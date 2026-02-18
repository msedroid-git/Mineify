package com.mineify;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MineifyConfig {
    private static int maxPlaylistSize = 50;
    private static int audioServerPort = 3001;
    // URL that Minecraft clients use to fetch audio. Must be reachable from
    // every client — change to the server's public IP when running online.
    private static String audioServerUrl = "http://localhost:3001";
    // Directory where downloaded WAV files are stored
    private static String downloadDir = "./mineify-downloads";

    static {
        load();
    }

    private static void load() {
        Path configPath = Path.of("config", "mineify.json");
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                Gson gson = new Gson();
                JsonObject obj = gson.fromJson(json, JsonObject.class);
                if (obj.has("maxPlaylistSize")) {
                    maxPlaylistSize = obj.get("maxPlaylistSize").getAsInt();
                }
                if (obj.has("audioServerPort")) {
                    audioServerPort = obj.get("audioServerPort").getAsInt();
                }
                if (obj.has("audioServerUrl")) {
                    audioServerUrl = obj.get("audioServerUrl").getAsString();
                }
                if (obj.has("downloadDir")) {
                    downloadDir = obj.get("downloadDir").getAsString();
                }
            } catch (IOException e) {
                Mineify.LOGGER.warn("Failed to load mineify config, using defaults", e);
            }
        }
    }

    public static int getMaxPlaylistSize() {
        return maxPlaylistSize;
    }

    public static int getAudioServerPort() {
        return audioServerPort;
    }

    public static String getAudioServerUrl() {
        return audioServerUrl;
    }

    public static String getDownloadDir() {
        return downloadDir;
    }
}
