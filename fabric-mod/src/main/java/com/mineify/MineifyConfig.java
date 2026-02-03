package com.mineify;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MineifyConfig {
    private static String companionUrl = "http://localhost:3001";
    private static int maxPlaylistSize = 50;
    private static String audioSessionFolder = "./mineify-sessions";

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
                if (obj.has("companionServiceUrl")) {
                    companionUrl = obj.get("companionServiceUrl").getAsString();
                }
                if (obj.has("maxPlaylistSize")) {
                    maxPlaylistSize = obj.get("maxPlaylistSize").getAsInt();
                }
                if (obj.has("audioSessionFolder")) {
                    audioSessionFolder = obj.get("audioSessionFolder").getAsString();
                }
            } catch (IOException e) {
                Mineify.LOGGER.warn("Failed to load mineify config, using defaults", e);
            }
        }
    }

    public static String getCompanionUrl() {
        return companionUrl;
    }

    public static int getMaxPlaylistSize() {
        return maxPlaylistSize;
    }

    public static String getAudioSessionFolder() {
        return audioSessionFolder;
    }
}
