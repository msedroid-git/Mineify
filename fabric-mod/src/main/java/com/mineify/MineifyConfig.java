package com.mineify;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MineifyConfig {
    private static int maxPlaylistSize = 50;
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

    public static String getDownloadDir() {
        return downloadDir;
    }
}
