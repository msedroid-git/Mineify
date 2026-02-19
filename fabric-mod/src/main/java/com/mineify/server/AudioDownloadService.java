package com.mineify.server;

import com.mineify.Mineify;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Downloads audio from YouTube using yt-dlp as a subprocess and serves the
 * resulting WAV files via an embedded HTTP server. Replaces the Node.js
 * companion service (Express + yt-dlp).
 *
 * The {@code audioServerUrl} should be configured to the address that
 * Minecraft clients can reach (e.g. "http://192.168.1.10:3001"). It defaults
 * to "http://localhost:3001" which only works when every client runs on the
 * same machine as the server.
 */
public class AudioDownloadService {
    private final Path downloadDir;
    private final String audioServerUrl;
    private final HttpServer httpServer;
    private final ExecutorService downloadExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Mineify-Downloader");
        t.setDaemon(true);
        return t;
    });
    private boolean ytDlpAvailable = false;
    private boolean ffmpegAvailable = false;

    public AudioDownloadService(Path downloadDir, int port, String audioServerUrl) throws IOException {
        this.downloadDir = downloadDir;
        this.audioServerUrl = audioServerUrl;
        Files.createDirectories(downloadDir);

        // Check for required dependencies at startup
        checkDependencies();

        this.httpServer = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        this.httpServer.createContext("/audio", exchange -> {
            String requestPath = exchange.getRequestURI().getPath();
            String videoId = requestPath.substring(requestPath.lastIndexOf('/') + 1);
            Path filePath = downloadDir.resolve(videoId + ".wav");

            if (!Files.exists(filePath)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            byte[] data = Files.readAllBytes(filePath);
            exchange.getResponseHeaders().set("Content-Type", "audio/wav");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(data.length));
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
            exchange.close();
        });
        this.httpServer.setExecutor(Executors.newCachedThreadPool());
        this.httpServer.start();

        Mineify.LOGGER.info("Mineify: Audio server started on port {}", port);
    }

    /**
     * Downloads a YouTube video as WAV using yt-dlp.
     * Returns a CompletableFuture with the URL clients should fetch, or null on failure.
     */
    public CompletableFuture<String> download(String videoId) {
        return CompletableFuture.supplyAsync(() -> {
            Path filePath = downloadDir.resolve(videoId + ".wav");

            if (Files.exists(filePath)) {
                Mineify.LOGGER.info("Mineify: Using cached audio for {}", videoId);
                return buildUrl(videoId);
            }

            Mineify.LOGGER.info("Mineify: Downloading audio for {}", videoId);
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "yt-dlp",
                        "-x",
                        "--audio-format", "wav",
                        "--audio-quality", "0",
                        "-o", filePath.toString(),
                        "--no-playlist",
                        "https://www.youtube.com/watch?v=" + videoId
                );
                pb.redirectErrorStream(true);

                Process process = pb.start();
                String output = new String(process.getInputStream().readAllBytes());
                boolean finished = process.waitFor(2, TimeUnit.MINUTES);

                if (!finished || process.exitValue() != 0) {
                    Mineify.LOGGER.error("yt-dlp failed for {}: {}", videoId, output);
                    if (output.contains("ffprobe and ffmpeg not found") || output.contains("ffmpeg not found")) {
                        Mineify.LOGGER.error("Mineify: ffmpeg is not installed! Please install ffmpeg to enable audio downloads.");
                        Mineify.LOGGER.error("Mineify: On Windows: winget install ffmpeg OR choco install ffmpeg OR scoop install ffmpeg");
                        Mineify.LOGGER.error("Mineify: On Linux: sudo apt install ffmpeg OR sudo dnf install ffmpeg");
                        Mineify.LOGGER.error("Mineify: On macOS: brew install ffmpeg");
                    }
                    return null;
                }

                if (!Files.exists(filePath)) {
                    Mineify.LOGGER.error("yt-dlp produced no output file for {}", videoId);
                    return null;
                }

                return buildUrl(videoId);
            } catch (IOException e) {
                Mineify.LOGGER.error("yt-dlp not found or failed to start for {}. Is yt-dlp installed?", videoId, e);
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Mineify.LOGGER.error("Download interrupted for {}", videoId, e);
                return null;
            }
        }, downloadExecutor);
    }

    public void delete(String videoId) {
        Path filePath = downloadDir.resolve(videoId + ".wav");
        try {
            Files.deleteIfExists(filePath);
            Mineify.LOGGER.info("Mineify: Deleted audio for {}", videoId);
        } catch (IOException e) {
            Mineify.LOGGER.warn("Mineify: Failed to delete audio for {}", videoId, e);
        }
    }

    private String buildUrl(String videoId) {
        return audioServerUrl + "/audio/" + videoId;
    }

    private void checkDependencies() {
        // Check yt-dlp
        try {
            Process p = new ProcessBuilder("yt-dlp", "--version").start();
            String version = new String(p.getInputStream().readAllBytes()).trim();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (finished && p.exitValue() == 0) {
                ytDlpAvailable = true;
                Mineify.LOGGER.info("Mineify: Found yt-dlp version {}", version);
            } else {
                Mineify.LOGGER.error("Mineify: yt-dlp check failed. Audio downloads will not work!");
            }
        } catch (IOException e) {
            Mineify.LOGGER.error("Mineify: yt-dlp not found! Please install yt-dlp and ensure it's on the system PATH.");
            Mineify.LOGGER.error("Mineify: See https://github.com/yt-dlp/yt-dlp#installation for installation instructions.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Check ffmpeg
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (finished && p.exitValue() == 0) {
                ffmpegAvailable = true;
                Mineify.LOGGER.info("Mineify: Found ffmpeg");
            } else {
                Mineify.LOGGER.error("Mineify: ffmpeg check failed. Audio conversion will not work!");
            }
        } catch (IOException e) {
            Mineify.LOGGER.error("Mineify: ffmpeg not found! Please install ffmpeg and ensure it's on the system PATH.");
            Mineify.LOGGER.error("Mineify: ffmpeg is required by yt-dlp to convert audio to WAV format.");
            Mineify.LOGGER.error("Mineify: See https://ffmpeg.org/download.html for installation instructions.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!ytDlpAvailable || !ffmpegAvailable) {
            Mineify.LOGGER.warn("Mineify: ============================================================");
            Mineify.LOGGER.warn("Mineify: AUDIO DOWNLOADS WILL FAIL - Missing required dependencies!");
            Mineify.LOGGER.warn("Mineify: Please install both yt-dlp and ffmpeg on the server.");
            Mineify.LOGGER.warn("Mineify: ============================================================");
        }
    }

    public boolean isDependenciesAvailable() {
        return ytDlpAvailable && ffmpegAvailable;
    }

    public void shutdown() {
        httpServer.stop(1);
        downloadExecutor.shutdownNow();
        Mineify.LOGGER.info("Mineify: Audio download service shut down");
    }
}
