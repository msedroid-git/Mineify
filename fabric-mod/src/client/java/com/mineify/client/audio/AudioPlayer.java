package com.mineify.client.audio;

import com.mineify.MineifyClient;
import com.mineify.network.packets.AudioChunkPacket;
import com.mineify.network.packets.ReadyPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Environment(EnvType.CLIENT)
public class AudioPlayer {
    private static AudioPlayer instance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Mineify-Audio");
        t.setDaemon(true);
        return t;
    });

    private volatile Clip currentClip;
    private volatile String currentTitle = "";
    private volatile boolean playing = false;
    private volatile boolean awaitingResume = false;
    private volatile float volume = 1.0f;

    // Chunk buffers keyed by videoId
    private final Map<String, byte[][]> chunkBuffer = new ConcurrentHashMap<>();
    private final Map<String, Integer> chunksReceived = new ConcurrentHashMap<>();
    private final Map<String, Long> chunkArrivalTime = new ConcurrentHashMap<>();

    private AudioPlayer() {}

    public static AudioPlayer getInstance() {
        if (instance == null) {
            instance = new AudioPlayer();
        }
        return instance;
    }

    /**
     * Called for every incoming AudioChunkPacket. Buffers chunks by videoId and
     * triggers playback once the final chunk arrives.
     */
    public void receiveChunk(AudioChunkPacket packet) {
        String videoId = packet.videoId();

        // First chunk of a track: reset buffers and discard any in-flight previous track
        if (packet.chunkIndex() == 0) {
            chunkBuffer.keySet().removeIf(id -> !id.equals(videoId));
            chunksReceived.keySet().removeIf(id -> !id.equals(videoId));
            chunkArrivalTime.keySet().removeIf(id -> !id.equals(videoId));
            chunkBuffer.put(videoId, new byte[packet.totalChunks()][]);
            chunksReceived.put(videoId, 0);
            chunkArrivalTime.put(videoId, System.currentTimeMillis());
        }

        byte[][] chunks = chunkBuffer.get(videoId);
        if (chunks == null) {
            // Non-first chunk arrived before the first chunk (shouldn't happen over TCP, ignore)
            return;
        }

        chunks[packet.chunkIndex()] = packet.data();
        int received = chunksReceived.merge(videoId, 1, Integer::sum);

        if (received == packet.totalChunks()) {
            // All chunks received — assemble
            int totalSize = 0;
            for (byte[] chunk : chunks) {
                if (chunk != null) totalSize += chunk.length;
            }
            byte[] audioBytes = new byte[totalSize];
            int offset = 0;
            for (byte[] chunk : chunks) {
                if (chunk != null) {
                    System.arraycopy(chunk, 0, audioBytes, offset, chunk.length);
                    offset += chunk.length;
                }
            }

            chunkBuffer.remove(videoId);
            chunksReceived.remove(videoId);
            chunkArrivalTime.remove(videoId);

            if (awaitingResume) {
                // Late-joiner: load clip but don't start, then signal ready
                MineifyClient.LOGGER.info("All {} chunks received for '{}', loading clip (awaiting resume)",
                        packet.totalChunks(), packet.title());
                loadWithoutPlaying(audioBytes, packet.title());
                ClientPlayNetworking.send(new ReadyPacket(videoId));
            } else {
                // Normal playback (first play of a track for all clients)
                long transferTime = 0L;
                long adjustedOffsetMs = packet.startOffsetMs() + transferTime;
                MineifyClient.LOGGER.info("All {} chunks received for '{}', starting playback (offset={}ms)",
                        packet.totalChunks(), packet.title(), adjustedOffsetMs);
                playFromBytes(audioBytes, packet.title(), adjustedOffsetMs);
            }
        }
    }

    private void playFromBytes(byte[] audioBytes, String title, long startOffsetMs) {
        executor.submit(() -> {
            stopInternal();
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
                AudioInputStream ais = AudioSystem.getAudioInputStream(bais);

                // Convert to a format the system can play if needed
                AudioFormat baseFormat = ais.getFormat();
                AudioFormat playFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        baseFormat.getSampleRate(),
                        16,
                        baseFormat.getChannels(),
                        baseFormat.getChannels() * 2,
                        baseFormat.getSampleRate(),
                        false
                );

                if (!baseFormat.matches(playFormat)) {
                    ais = AudioSystem.getAudioInputStream(playFormat, ais);
                }

                Clip clip = AudioSystem.getClip();
                clip.open(ais);

                if (startOffsetMs > 0) {
                    long clipLengthMs = clip.getMicrosecondLength() / 1000L;
                    long seekMs = Math.min(startOffsetMs, clipLengthMs > 0 ? clipLengthMs - 1 : 0);
                    clip.setMicrosecondPosition(seekMs * 1000L);
                    MineifyClient.LOGGER.info("Seeking to {}ms for late-join", seekMs);
                }

                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP && playing) {
                        playing = false;
                        currentTitle = "";
                        MineifyClient.LOGGER.info("Audio playback finished");
                    }
                });

                currentClip = clip;
                currentTitle = title;
                playing = true;
                applyVolume(clip);
                clip.start();

                MineifyClient.LOGGER.info("Playing: {}", title);
            } catch (Exception e) {
                MineifyClient.LOGGER.error("Audio playback failed for: {}", title, e);
                playing = false;
                currentTitle = "";
            }
        });
    }

    /**
     * Load audio into a clip but do not start playback.
     * Used by late-joiners who must wait for a ResumePacket.
     */
    private void loadWithoutPlaying(byte[] audioBytes, String title) {
        executor.submit(() -> {
            stopInternal();
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
                AudioInputStream ais = AudioSystem.getAudioInputStream(bais);

                AudioFormat baseFormat = ais.getFormat();
                AudioFormat playFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        baseFormat.getSampleRate(),
                        16,
                        baseFormat.getChannels(),
                        baseFormat.getChannels() * 2,
                        baseFormat.getSampleRate(),
                        false
                );

                if (!baseFormat.matches(playFormat)) {
                    ais = AudioSystem.getAudioInputStream(playFormat, ais);
                }

                Clip clip = AudioSystem.getClip();
                clip.open(ais);

                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP && playing) {
                        playing = false;
                        currentTitle = "";
                        MineifyClient.LOGGER.info("Audio playback finished");
                    }
                });

                currentClip = clip;
                currentTitle = title;
                applyVolume(clip);
                MineifyClient.LOGGER.info("Clip loaded (not started) for: {}", title);
            } catch (Exception e) {
                MineifyClient.LOGGER.error("Failed to load audio clip for: {}", title, e);
            }
        });
    }

    /**
     * Pause the current clip at the given position. Called when a PausePacket arrives.
     */
    public void pause(long positionMs) {
        awaitingResume = true;
        executor.submit(() -> {
            Clip clip = currentClip;
            if (clip != null && clip.isOpen()) {
                clip.stop();
                playing = false;
                MineifyClient.LOGGER.info("Paused at {}ms", positionMs);
            }
        });
    }

    /**
     * Seek to the given position and start playback. Called when a ResumePacket arrives.
     */
    public void resume(long positionMs) {
        awaitingResume = false;
        executor.submit(() -> {
            Clip clip = currentClip;
            if (clip != null && clip.isOpen()) {
                long clipLengthMs = clip.getMicrosecondLength() / 1000L;
                long seekMs = Math.min(positionMs, clipLengthMs > 0 ? clipLengthMs - 1 : 0);
                clip.setMicrosecondPosition(seekMs * 1000L);
                playing = true;
                clip.start();
                MineifyClient.LOGGER.info("Resumed at {}ms", seekMs);
            }
        });
    }

    public void stop() {
        awaitingResume = false;
        chunkBuffer.clear();
        chunksReceived.clear();
        chunkArrivalTime.clear();
        executor.submit(this::stopInternal);
    }

    private void stopInternal() {
        playing = false;
        awaitingResume = false;
        currentTitle = "";
        Clip clip = currentClip;
        if (clip != null) {
            clip.stop();
            clip.close();
            currentClip = null;
        }
    }

    public boolean isPlaying() {
        return playing;
    }

    public String getCurrentTitle() {
        return currentTitle;
    }

    public float getProgress() {
        Clip clip = currentClip;
        if (clip != null && clip.getMicrosecondLength() > 0) {
            return (float) clip.getMicrosecondPosition() / clip.getMicrosecondLength();
        }
        return 0f;
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = volume;
        Clip clip = currentClip;
        if (clip != null && clip.isOpen()) {
            applyVolume(clip);
        }
    }

    private void applyVolume(Clip clip) {
        try {
            FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = (float) (20.0 * Math.log10(Math.max(volume, 0.0001)));
            dB = Math.max(dB, control.getMinimum());
            dB = Math.min(dB, control.getMaximum());
            control.setValue(dB);
        } catch (IllegalArgumentException e) {
            // Volume control not available
        }
    }

    public void shutdown() {
        chunkBuffer.clear();
        chunksReceived.clear();
        chunkArrivalTime.clear();
        stopInternal();
        executor.shutdownNow();
    }
}
