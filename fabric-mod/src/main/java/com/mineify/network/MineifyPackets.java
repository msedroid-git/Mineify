package com.mineify.network;

import com.mineify.Mineify;
import com.mineify.network.packets.AddToPlaylistPacket;
import com.mineify.network.packets.RemoveFromPlaylistPacket;
import com.mineify.network.packets.SearchRequestPacket;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class MineifyPackets {
    public static void registerServerPackets() {
        // Register C2S (client-to-server) packet types
        PayloadTypeRegistry.playC2S().register(SearchRequestPacket.ID, SearchRequestPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(AddToPlaylistPacket.ID, AddToPlaylistPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RemoveFromPlaylistPacket.ID, RemoveFromPlaylistPacket.CODEC);

        // Register S2C (server-to-client) packet types
        PayloadTypeRegistry.playS2C().register(
                com.mineify.network.packets.SearchResultsPacket.ID,
                com.mineify.network.packets.SearchResultsPacket.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                com.mineify.network.packets.PlaylistSyncPacket.ID,
                com.mineify.network.packets.PlaylistSyncPacket.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                com.mineify.network.packets.NowPlayingPacket.ID,
                com.mineify.network.packets.NowPlayingPacket.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                com.mineify.network.packets.PlayAudioPacket.ID,
                com.mineify.network.packets.PlayAudioPacket.CODEC
        );

        // Register server-side handlers
        ServerPlayNetworking.registerGlobalReceiver(SearchRequestPacket.ID, (payload, context) -> {
            context.server().execute(() -> {
                var manager = Mineify.getPlaylistManager();
                if (manager != null) {
                    manager.handleSearch(context.player(), payload.query());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AddToPlaylistPacket.ID, (payload, context) -> {
            context.server().execute(() -> {
                var manager = Mineify.getPlaylistManager();
                if (manager != null) {
                    manager.handleAddToPlaylist(context.player(), payload.videoId(), payload.title(), payload.duration());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RemoveFromPlaylistPacket.ID, (payload, context) -> {
            context.server().execute(() -> {
                var manager = Mineify.getPlaylistManager();
                if (manager != null) {
                    manager.handleRemoveFromPlaylist(context.player(), payload.videoId());
                }
            });
        });
    }

    public static void registerClientPackets() {
        // Client-side packet handlers are registered in MineifyClient
    }
}
