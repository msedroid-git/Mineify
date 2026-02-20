package com.mineify.server;

import com.mineify.Mineify;
import com.mineify.network.packets.AudioChunkPacket;
import com.mineify.network.packets.NowPlayingPacket;
import com.mineify.network.packets.PausePacket;
import com.mineify.network.packets.PlaylistSyncPacket;
import com.mineify.network.packets.ResumePacket;
import com.mineify.network.packets.SearchResultsPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PlaylistManager {
    private final MinecraftServer server;
    private final YouTubeService youTubeService;
    private final AudioDownloadService audioDownloadService;
    private final List<PlaylistSyncPacket.Entry> playlist = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Mineify-Scheduler");
        t.setDaemon(true);
        return t;
    });

    private int currentIndex = -1;
    private boolean isPlaying = false;
    private long playbackStartTime = 0;
    private long currentTrackDurationMs = 0;
    private String currentVideoId = null;
    private ScheduledFuture<?> advanceFuture;
    private ScheduledFuture<?> progressFuture;

    // Late-join sync state
    private boolean isSyncing = false;
    private final Set<UUID> pendingPlayers = new HashSet<>();
    private long pausedPositionMs = 0;
    private long pauseStartTime = 0;
    private ScheduledFuture<?> syncTimeoutFuture;

    public PlaylistManager(MinecraftServer server, YouTubeService youTubeService, AudioDownloadService audioDownloadService) {
        this.server = server;
        this.youTubeService = youTubeService;
        this.audioDownloadService = audioDownloadService;

        // Broadcast progress every second (skip while syncing)
        this.progressFuture = scheduler.scheduleAtFixedRate(() -> {
            if (isPlaying && !isSyncing && currentIndex >= 0 && currentIndex < playlist.size()) {
                PlaylistSyncPacket.Entry entry = playlist.get(currentIndex);
                long elapsed = System.currentTimeMillis() - playbackStartTime;
                float progress = currentTrackDurationMs > 0 ? (float) elapsed / currentTrackDurationMs : 0f;
                server.execute(() -> broadcastNowPlaying(entry.title(), Math.min(progress, 1f)));
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public void handleSearch(ServerPlayerEntity player, String query) {
        Mineify.LOGGER.info("Player {} searching for: {}", player.getName().getString(), query);

        youTubeService.search(query).thenAccept(results -> {
            server.execute(() -> {
                List<SearchResultsPacket.Entry> entries = new ArrayList<>();
                for (var r : results) {
                    entries.add(new SearchResultsPacket.Entry(
                            r.videoId(), r.title(), r.channel(), r.duration(), r.thumbnail()
                    ));
                }
                ServerPlayNetworking.send(player, new SearchResultsPacket(entries));
            });
        });
    }

    public void handleAddToPlaylist(ServerPlayerEntity player, String videoId, String title, String duration) {
        Mineify.LOGGER.info("Player {} adding to playlist: {}", player.getName().getString(), title);

        // Warn if dependencies aren't available
        if (!audioDownloadService.isDependenciesAvailable()) {
            player.sendMessage(Text.literal("[Mineify] ")
                    .formatted(Formatting.YELLOW)
                    .append(Text.literal("Warning: yt-dlp or ffmpeg may not be installed on the server. Downloads may fail.")
                            .formatted(Formatting.WHITE)), false);
        }

        PlaylistSyncPacket.Entry entry = new PlaylistSyncPacket.Entry(
                videoId, title, duration, player.getName().getString()
        );
        playlist.add(entry);
        syncToAll();

        if (!isPlaying) {
            playNext();
        }
    }

    public void handleRemoveFromPlaylist(ServerPlayerEntity player, String videoId) {
        String playerName = player.getName().getString();

        int removeIndex = -1;
        for (int i = 0; i < playlist.size(); i++) {
            PlaylistSyncPacket.Entry entry = playlist.get(i);
            if (entry.videoId().equals(videoId) && entry.addedBy().equals(playerName)) {
                removeIndex = i;
                break;
            }
        }

        if (removeIndex == -1) {
            Mineify.LOGGER.warn("{} tried to remove {} but doesn't own it or it doesn't exist", playerName, videoId);
            return;
        }

        playlist.remove(removeIndex);
        Mineify.LOGGER.info("{} removed {} from playlist", playerName, videoId);

        audioDownloadService.delete(videoId);

        if (currentIndex >= 0) {
            if (removeIndex < currentIndex) {
                currentIndex--;
            } else if (removeIndex == currentIndex) {
                if (advanceFuture != null) {
                    advanceFuture.cancel(false);
                }
                // Tell all clients to stop playing the removed song immediately
                broadcastNowPlaying("", 0f);
                currentIndex--;
                playNext();
            }
        }

        syncToAll();
    }

    public void syncToPlayer(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new PlaylistSyncPacket(new ArrayList<>(playlist)));
        if (isPlaying && currentIndex >= 0 && currentIndex < playlist.size()) {
            PlaylistSyncPacket.Entry entry = playlist.get(currentIndex);
            long elapsed = System.currentTimeMillis() - playbackStartTime;
            float progress = currentTrackDurationMs > 0 ? (float) elapsed / currentTrackDurationMs : 0f;
            ServerPlayNetworking.send(player, new NowPlayingPacket(entry.title(), Math.min(progress, 1f)));

            if (currentVideoId != null) {
                UUID playerUuid = player.getUuid();
                pendingPlayers.add(playerUuid);

                // First joiner triggers pause for all existing clients
                if (!isSyncing) {
                    isSyncing = true;
                    pausedPositionMs = elapsed;
                    pauseStartTime = System.currentTimeMillis();

                    // Cancel the advance timer while paused
                    if (advanceFuture != null) {
                        advanceFuture.cancel(false);
                    }

                    // Pause all existing clients
                    PausePacket pausePacket = new PausePacket(pausedPositionMs);
                    for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                        if (!p.getUuid().equals(playerUuid)) {
                            ServerPlayNetworking.send(p, pausePacket);
                        }
                    }
                    Mineify.LOGGER.info("Mineify: Paused all clients at {}ms for late-join sync", pausedPositionMs);
                }

                // Schedule 10s safety timeout
                if (syncTimeoutFuture != null) {
                    syncTimeoutFuture.cancel(false);
                }
                syncTimeoutFuture = scheduler.schedule(
                        () -> server.execute(() -> {
                            if (isSyncing) {
                                Mineify.LOGGER.warn("Mineify: Sync timeout, forcing resume with {} pending players", pendingPlayers.size());
                                pendingPlayers.clear();
                                doResume();
                            }
                        }),
                        10, TimeUnit.SECONDS
                );

                // Send audio chunks to the late joiner (offset=0 since ResumePacket controls position)
                final String videoId = currentVideoId;
                scheduler.submit(() -> {
                    Path filePath = audioDownloadService.getFilePath(videoId);
                    if (!Files.exists(filePath)) {
                        Mineify.LOGGER.warn("Mineify: Audio file not found for late-join resend ({})", videoId);
                        server.execute(() -> {
                            pendingPlayers.remove(playerUuid);
                            if (pendingPlayers.isEmpty() && isSyncing) {
                                doResume();
                            }
                        });
                        return;
                    }
                    try {
                        byte[] audioBytes = Files.readAllBytes(filePath);
                        server.execute(() -> sendAudioChunks(player, videoId, entry.title(), audioBytes, 0L));
                    } catch (IOException e) {
                        Mineify.LOGGER.error("Mineify: Failed to read audio file for late-join resend ({})", videoId, e);
                        server.execute(() -> {
                            pendingPlayers.remove(playerUuid);
                            if (pendingPlayers.isEmpty() && isSyncing) {
                                doResume();
                            }
                        });
                    }
                });
            }
        }
    }

    public void onPlayerReady(ServerPlayerEntity player, String videoId) {
        // Ignore stale readies for a different track
        if (!isSyncing || !videoId.equals(currentVideoId)) {
            return;
        }

        pendingPlayers.remove(player.getUuid());
        Mineify.LOGGER.info("Mineify: Player {} ready ({}), {} pending",
                player.getName().getString(), videoId, pendingPlayers.size());

        if (pendingPlayers.isEmpty()) {
            doResume();
        }
    }

    private void doResume() {
        if (!isSyncing) return;

        isSyncing = false;
        if (syncTimeoutFuture != null) {
            syncTimeoutFuture.cancel(false);
            syncTimeoutFuture = null;
        }

        // Adjust playback start time to account for the pause duration
        long pauseDuration = System.currentTimeMillis() - pauseStartTime;
        playbackStartTime += pauseDuration;

        // Resume all clients at the paused position
        ResumePacket resumePacket = new ResumePacket(pausedPositionMs);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, resumePacket);
        }

        Mineify.LOGGER.info("Mineify: Resuming all clients at {}ms (paused for {}ms)", pausedPositionMs, pauseDuration);

        // Re-schedule advance timer for remaining duration
        if (currentTrackDurationMs > 0) {
            long remaining = currentTrackDurationMs - pausedPositionMs + 2000;
            if (remaining > 0) {
                advanceFuture = scheduler.schedule(
                        () -> server.execute(this::playNext),
                        remaining,
                        TimeUnit.MILLISECONDS
                );
            }
        }
    }

    public void handleDisconnect(ServerPlayerEntity player) {
        if (isSyncing) {
            pendingPlayers.remove(player.getUuid());
            Mineify.LOGGER.info("Mineify: Syncing player {} disconnected, {} pending",
                    player.getName().getString(), pendingPlayers.size());
            if (pendingPlayers.isEmpty()) {
                doResume();
            }
        }
    }

    private void playNext() {
        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            audioDownloadService.delete(playlist.get(currentIndex).videoId());
        }

        currentIndex++;
        if (currentIndex >= playlist.size()) {
            isPlaying = false;
            currentIndex = -1;
            currentVideoId = null;
            broadcastNowPlaying("", 0f);
            return;
        }

        PlaylistSyncPacket.Entry entry = playlist.get(currentIndex);
        isPlaying = true;
        currentTrackDurationMs = parseDuration(entry.duration());

        Mineify.LOGGER.info("Requesting download for: {} ({})", entry.title(), entry.videoId());

        audioDownloadService.download(entry.videoId()).thenAccept(filePath -> {
            if (filePath == null) {
                Mineify.LOGGER.error("Download failed for: {}", entry.title());
                server.execute(() -> {
                    Text message = Text.literal("[Mineify] ")
                            .formatted(Formatting.RED)
                            .append(Text.literal("Failed to download: " + entry.title())
                                    .formatted(Formatting.WHITE))
                            .append(Text.literal(" - Check server logs for details")
                                    .formatted(Formatting.GRAY));
                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        player.sendMessage(message, false);
                    }
                    playNext();
                });
                return;
            }

            // Read audio bytes on the download thread, then send chunks on the server thread
            try {
                byte[] audioBytes = Files.readAllBytes(filePath);
                server.execute(() -> {
                    currentVideoId = entry.videoId();
                    playbackStartTime = System.currentTimeMillis();

                    Mineify.LOGGER.info("Mineify: Sending audio to all players: {}", entry.title());

                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        sendAudioChunks(player, entry.videoId(), entry.title(), audioBytes, 0L);
                    }

                    broadcastNowPlaying(entry.title(), 0f);

                    if (currentTrackDurationMs > 0) {
                        if (advanceFuture != null) {
                            advanceFuture.cancel(false);
                        }
                        advanceFuture = scheduler.schedule(
                                () -> server.execute(this::playNext),
                                currentTrackDurationMs + 2000,
                                TimeUnit.MILLISECONDS
                        );
                    }
                });
            } catch (IOException e) {
                Mineify.LOGGER.error("Mineify: Failed to read downloaded audio for {}", entry.videoId(), e);
                server.execute(this::playNext);
            }
        });
    }

    /**
     * Splits audioBytes into CHUNK_SIZE chunks and sends each as an AudioChunkPacket.
     * Must be called from the server thread.
     */
    private void sendAudioChunks(ServerPlayerEntity player, String videoId, String title,
                                  byte[] audioBytes, long startOffsetMs) {
        int chunkSize = AudioChunkPacket.CHUNK_SIZE;
        int totalChunks = (int) Math.ceil((double) audioBytes.length / chunkSize);

        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, audioBytes.length);
            byte[] chunk = Arrays.copyOfRange(audioBytes, start, end);
            ServerPlayNetworking.send(player, new AudioChunkPacket(videoId, title, i, totalChunks, startOffsetMs, chunk));
        }

        Mineify.LOGGER.info("Mineify: Sent {} chunks ({} KB) for '{}' to {}",
                totalChunks, audioBytes.length / 1024, title, player.getName().getString());
    }

    private void syncToAll() {
        PlaylistSyncPacket packet = new PlaylistSyncPacket(new ArrayList<>(playlist));
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    private void broadcastNowPlaying(String title, float progress) {
        NowPlayingPacket packet = new NowPlayingPacket(title, progress);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    /**
     * Parse duration string like "3:45" or "1:02:30" to milliseconds.
     */
    private long parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) {
            return 3 * 60 * 1000;
        }
        try {
            String[] parts = duration.split(":");
            long seconds = 0;
            for (String part : parts) {
                seconds = seconds * 60 + Long.parseLong(part.trim());
            }
            return seconds * 1000;
        } catch (NumberFormatException e) {
            return 3 * 60 * 1000;
        }
    }

    public void shutdown() {
        if (advanceFuture != null) advanceFuture.cancel(true);
        if (progressFuture != null) progressFuture.cancel(true);
        scheduler.shutdownNow();
        playlist.clear();
        Mineify.LOGGER.info("Mineify: Playlist manager shut down");
    }
}
