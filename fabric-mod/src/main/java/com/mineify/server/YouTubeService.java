package com.mineify.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mineify.Mineify;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Performs YouTube search using YouTube's internal InnerTube API.
 * Replaces the companion service's youtube-search-api npm dependency.
 */
public class YouTubeService {
    private static final String INNERTUBE_URL = "https://www.youtube.com/youtubei/v1/search";
    private static final String CLIENT_VERSION = "2.20231219.04.00";
    private static final int MAX_RESULTS = 10;

    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public YouTubeService() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public CompletableFuture<List<SearchResult>> search(String query) {
        JsonObject context = new JsonObject();
        JsonObject client = new JsonObject();
        client.addProperty("clientName", "WEB");
        client.addProperty("clientVersion", CLIENT_VERSION);
        context.add("client", client);

        JsonObject body = new JsonObject();
        body.add("context", context);
        body.addProperty("query", query);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(INNERTUBE_URL))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseResults(response.body()))
                .exceptionally(e -> {
                    Mineify.LOGGER.error("YouTube search failed for query: {}", query, e);
                    return new ArrayList<>();
                });
    }

    private List<SearchResult> parseResults(String responseBody) {
        List<SearchResult> results = new ArrayList<>();
        try {
            JsonObject root = gson.fromJson(responseBody, JsonObject.class);
            JsonArray sections = root
                    .getAsJsonObject("contents")
                    .getAsJsonObject("twoColumnSearchResultsRenderer")
                    .getAsJsonObject("primaryContents")
                    .getAsJsonObject("sectionListRenderer")
                    .getAsJsonArray("contents");

            for (JsonElement section : sections) {
                JsonObject sectionObj = section.getAsJsonObject();
                if (!sectionObj.has("itemSectionRenderer")) continue;

                JsonArray items = sectionObj.getAsJsonObject("itemSectionRenderer")
                        .getAsJsonArray("contents");

                for (JsonElement item : items) {
                    JsonObject itemObj = item.getAsJsonObject();
                    if (!itemObj.has("videoRenderer")) continue;

                    JsonObject video = itemObj.getAsJsonObject("videoRenderer");
                    String videoId = video.has("videoId") ? video.get("videoId").getAsString() : "";
                    if (videoId.isEmpty()) continue;

                    String title = extractText(video.getAsJsonObject("title"));
                    String channel = video.has("ownerText")
                            ? extractText(video.getAsJsonObject("ownerText"))
                            : video.has("longBylineText")
                                ? extractText(video.getAsJsonObject("longBylineText"))
                                : "";
                    String duration = video.has("lengthText")
                            ? extractText(video.getAsJsonObject("lengthText"))
                            : "";
                    String thumbnail = "";
                    if (video.has("thumbnail")) {
                        JsonArray thumbs = video.getAsJsonObject("thumbnail").getAsJsonArray("thumbnails");
                        if (!thumbs.isEmpty()) {
                            thumbnail = thumbs.get(thumbs.size() - 1).getAsJsonObject()
                                    .get("url").getAsString();
                        }
                    }

                    results.add(new SearchResult(videoId, title, channel, duration, thumbnail));
                    if (results.size() >= MAX_RESULTS) return results;
                }
                if (results.size() >= MAX_RESULTS) break;
            }
        } catch (Exception e) {
            Mineify.LOGGER.error("Failed to parse YouTube search response", e);
        }
        return results;
    }

    private String extractText(JsonObject textObj) {
        if (textObj == null) return "";
        if (textObj.has("simpleText")) {
            return textObj.get("simpleText").getAsString();
        }
        if (textObj.has("runs")) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement run : textObj.getAsJsonArray("runs")) {
                JsonObject runObj = run.getAsJsonObject();
                if (runObj.has("text")) sb.append(runObj.get("text").getAsString());
            }
            return sb.toString();
        }
        return "";
    }

    public record SearchResult(String videoId, String title, String channel, String duration, String thumbnail) {}
}
