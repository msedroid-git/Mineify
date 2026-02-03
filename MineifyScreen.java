package com.mineify.client;

import com.mineify.MineifyClient;
import com.mineify.network.MineifyPackets;
import com.mineify.network.packets.SearchRequestPacket;
import com.mineify.network.packets.AddToPlaylistPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Main GUI screen for Mineify.
 * Allows players to search for songs and manage the playlist.
 */
@Environment(EnvType.CLIENT)
public class MineifyScreen extends Screen {
    // UI dimensions
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 220;

    // Search components
    private TextFieldWidget searchField;
    private ButtonWidget searchButton;

    // Search results (populated from server response)
    private List<SearchResult> searchResults = new ArrayList<>();

    // Playlist (synced from server)
    private List<PlaylistEntry> playlist = new ArrayList<>();

    // Current view: 0 = search, 1 = playlist
    private int currentTab = 0;

    // Scroll offset for lists
    private int searchScrollOffset = 0;
    private int playlistScrollOffset = 0;

    // Currently playing track info
    private String nowPlaying = null;
    private float playbackProgress = 0f;

    public MineifyScreen() {
        super(Text.literal("Mineify - Music Player"));
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;
        int panelTop = centerY - PANEL_HEIGHT / 2;

        // Search field
        this.searchField = new TextFieldWidget(
                this.textRenderer,
                panelLeft + 10,
                panelTop + 30,
                PANEL_WIDTH - 80,
                20,
                Text.literal("Search YouTube...")
        );
        this.searchField.setMaxLength(100);
        this.searchField.setPlaceholder(Text.literal("Search for songs..."));
        this.addDrawableChild(this.searchField);

        // Search button
        this.searchButton = ButtonWidget.builder(Text.literal("Search"), button -> {
            performSearch();
        }).dimensions(panelLeft + PANEL_WIDTH - 60, panelTop + 30, 50, 20).build();
        this.addDrawableChild(this.searchButton);

        // Tab buttons
        ButtonWidget searchTabBtn = ButtonWidget.builder(Text.literal("Search"), button -> {
            this.currentTab = 0;
        }).dimensions(panelLeft + 10, panelTop + 5, 60, 20).build();
        this.addDrawableChild(searchTabBtn);

        ButtonWidget playlistTabBtn = ButtonWidget.builder(Text.literal("Playlist"), button -> {
            this.currentTab = 1;
        }).dimensions(panelLeft + 75, panelTop + 5, 60, 20).build();
        this.addDrawableChild(playlistTabBtn);

        // Request current playlist state from server
        requestPlaylistSync();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw background overlay
        this.renderBackground(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;
        int panelTop = centerY - PANEL_HEIGHT / 2;

        // Draw panel background
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xCC000000);
        context.drawBorder(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, 0xFF555555);

        // Draw title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, panelTop - 15, 0xFFFFFF);

        // Draw content based on current tab
        if (currentTab == 0) {
            renderSearchTab(context, panelLeft, panelTop, mouseX, mouseY);
        } else {
            renderPlaylistTab(context, panelLeft, panelTop, mouseX, mouseY);
        }

        // Draw now playing bar at bottom
        renderNowPlaying(context, panelLeft, panelTop + PANEL_HEIGHT - 30);

        // Render parent (widgets)
        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * Render the search tab with results.
     */
    private void renderSearchTab(DrawContext context, int panelLeft, int panelTop, int mouseX, int mouseY) {
        int listTop = panelTop + 55;
        int listHeight = PANEL_HEIGHT - 90;
        int itemHeight = 25;

        // Draw search results
        if (searchResults.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("Search for songs above"),
                    panelLeft + PANEL_WIDTH / 2,
                    listTop + 20,
                    0x888888
            );
        } else {
            int y = listTop;
            for (int i = searchScrollOffset; i < searchResults.size() && y < listTop + listHeight - itemHeight; i++) {
                SearchResult result = searchResults.get(i);
                boolean hovered = mouseX >= panelLeft + 10 && mouseX <= panelLeft + PANEL_WIDTH - 10
                        && mouseY >= y && mouseY < y + itemHeight;

                // Draw item background
                int bgColor = hovered ? 0x44FFFFFF : 0x22FFFFFF;
                context.fill(panelLeft + 10, y, panelLeft + PANEL_WIDTH - 10, y + itemHeight - 2, bgColor);

                // Draw title (truncated)
                String title = truncateText(result.title, PANEL_WIDTH - 80);
                context.drawTextWithShadow(this.textRenderer, Text.literal(title), panelLeft + 15, y + 4, 0xFFFFFF);

                // Draw duration
                context.drawTextWithShadow(this.textRenderer, Text.literal(result.duration), panelLeft + PANEL_WIDTH - 50, y + 4, 0xAAAAAA);

                // Draw channel
                String channel = truncateText(result.channel, PANEL_WIDTH - 40);
                context.drawTextWithShadow(this.textRenderer, Text.literal(channel), panelLeft + 15, y + 14, 0x888888);

                y += itemHeight;
            }
        }
    }

    /**
     * Render the playlist tab.
     */
    private void renderPlaylistTab(DrawContext context, int panelLeft, int panelTop, int mouseX, int mouseY) {
        int listTop = panelTop + 55;
        int listHeight = PANEL_HEIGHT - 90;
        int itemHeight = 25;

        if (playlist.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("Playlist is empty"),
                    panelLeft + PANEL_WIDTH / 2,
                    listTop + 20,
                    0x888888
            );
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("Search and add songs!"),
                    panelLeft + PANEL_WIDTH / 2,
                    listTop + 35,
                    0x666666
            );
        } else {
            int y = listTop;
            for (int i = playlistScrollOffset; i < playlist.size() && y < listTop + listHeight - itemHeight; i++) {
                PlaylistEntry entry = playlist.get(i);
                boolean hovered = mouseX >= panelLeft + 10 && mouseX <= panelLeft + PANEL_WIDTH - 10
                        && mouseY >= y && mouseY < y + itemHeight;
                boolean isPlaying = i == 0 && nowPlaying != null;

                // Draw item background
                int bgColor = isPlaying ? 0x4400FF00 : (hovered ? 0x44FFFFFF : 0x22FFFFFF);
                context.fill(panelLeft + 10, y, panelLeft + PANEL_WIDTH - 10, y + itemHeight - 2, bgColor);

                // Draw position number
                String pos = (i + 1) + ".";
                context.drawTextWithShadow(this.textRenderer, Text.literal(pos), panelLeft + 15, y + 8, 0xAAAAAA);

                // Draw title
                String title = truncateText(entry.title, PANEL_WIDTH - 100);
                context.drawTextWithShadow(this.textRenderer, Text.literal(title), panelLeft + 35, y + 4, 0xFFFFFF);

                // Draw added by
                String addedBy = "by " + entry.addedBy;
                context.drawTextWithShadow(this.textRenderer, Text.literal(addedBy), panelLeft + 35, y + 14, 0x888888);

                y += itemHeight;
            }
        }
    }

    /**
     * Render the now playing bar.
     */
    private void renderNowPlaying(DrawContext context, int panelLeft, int y) {
        // Background
        context.fill(panelLeft, y, panelLeft + PANEL_WIDTH, y + 25, 0x88000000);

        if (nowPlaying != null) {
            // Now playing text
            String text = "♪ " + truncateText(nowPlaying, PANEL_WIDTH - 40);
            context.drawTextWithShadow(this.textRenderer, Text.literal(text), panelLeft + 10, y + 4, 0x55FF55);

            // Progress bar
            int barWidth = PANEL_WIDTH - 20;
            int barX = panelLeft + 10;
            int barY = y + 18;
            context.fill(barX, barY, barX + barWidth, barY + 3, 0x44FFFFFF);
            context.fill(barX, barY, barX + (int)(barWidth * playbackProgress), barY + 3, 0xFF55FF55);
        } else {
            context.drawTextWithShadow(this.textRenderer, Text.literal("Nothing playing"), panelLeft + 10, y + 8, 0x666666);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            int panelLeft = centerX - PANEL_WIDTH / 2;
            int panelTop = centerY - PANEL_HEIGHT / 2;

            int listTop = panelTop + 55;
            int itemHeight = 25;

            // Check for search result clicks
            if (currentTab == 0 && !searchResults.isEmpty()) {
                int y = listTop;
                for (int i = searchScrollOffset; i < searchResults.size(); i++) {
                    if (mouseX >= panelLeft + 10 && mouseX <= panelLeft + PANEL_WIDTH - 10
                            && mouseY >= y && mouseY < y + itemHeight) {
                        addToPlaylist(searchResults.get(i));
                        return true;
                    }
                    y += itemHeight;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (currentTab == 0) {
            searchScrollOffset = Math.max(0, Math.min(searchScrollOffset - (int) verticalAmount, Math.max(0, searchResults.size() - 5)));
        } else {
            playlistScrollOffset = Math.max(0, Math.min(playlistScrollOffset - (int) verticalAmount, Math.max(0, playlist.size() - 5)));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle Enter key in search field
        if (keyCode == 257 && this.searchField.isFocused()) { // Enter key
            performSearch();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Perform a YouTube search.
     */
    private void performSearch() {
        String query = this.searchField.getText().trim();
        if (query.isEmpty()) {
            return;
        }

        MineifyClient.LOGGER.info("Searching for: {}", query);

        // Send search request to server
        ClientPlayNetworking.send(new SearchRequestPacket(query));

        // Clear current results while waiting
        this.searchResults.clear();
        this.searchScrollOffset = 0;
    }

    /**
     * Add a search result to the playlist.
     */
    private void addToPlaylist(SearchResult result) {
        MineifyClient.LOGGER.info("Adding to playlist: {}", result.title);

        // Send add request to server
        ClientPlayNetworking.send(new AddToPlaylistPacket(result.videoId, result.title, result.duration));
    }

    /**
     * Request playlist sync from server.
     */
    private void requestPlaylistSync() {
        // This will be called when the screen opens to get current state
        // The server will respond with the current playlist
    }

    /**
     * Update search results (called from packet handler).
     */
    public void updateSearchResults(List<SearchResult> results) {
        this.searchResults = results;
        this.searchScrollOffset = 0;
    }

    /**
     * Update playlist (called from packet handler).
     */
    public void updatePlaylist(List<PlaylistEntry> entries) {
        this.playlist = entries;
    }

    /**
     * Update now playing info (called from packet handler).
     */
    public void updateNowPlaying(String title, float progress) {
        this.nowPlaying = title;
        this.playbackProgress = progress;
    }

    /**
     * Truncate text to fit within a width.
     */
    private String truncateText(String text, int maxWidth) {
        if (this.textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        while (this.textRenderer.getWidth(text + "...") > maxWidth && text.length() > 0) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }

    @Override
    public boolean shouldPause() {
        return false; // Don't pause the game when the screen is open
    }

    // Data classes for search results and playlist entries
    public static class SearchResult {
        public final String videoId;
        public final String title;
        public final String channel;
        public final String duration;
        public final String thumbnail;

        public SearchResult(String videoId, String title, String channel, String duration, String thumbnail) {
            this.videoId = videoId;
            this.title = title;
            this.channel = channel;
            this.duration = duration;
            this.thumbnail = thumbnail;
        }
    }

    public static class PlaylistEntry {
        public final String videoId;
        public final String title;
        public final String duration;
        public final String addedBy;

        public PlaylistEntry(String videoId, String title, String duration, String addedBy) {
            this.videoId = videoId;
            this.title = title;
            this.duration = duration;
            this.addedBy = addedBy;
        }
    }
}
