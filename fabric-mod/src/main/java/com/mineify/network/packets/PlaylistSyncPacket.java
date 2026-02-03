package com.mineify.network.packets;

import com.mineify.Mineify;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PlaylistSyncPacket(List<Entry> entries) implements CustomPayload {
    public static final CustomPayload.Id<PlaylistSyncPacket> ID =
            new CustomPayload.Id<>(Identifier.of(Mineify.MOD_ID, "playlist_sync"));

    public static final PacketCodec<RegistryByteBuf, PlaylistSyncPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeVarInt(value.entries.size());
                        for (Entry e : value.entries) {
                            buf.writeString(e.videoId);
                            buf.writeString(e.title);
                            buf.writeString(e.duration);
                            buf.writeString(e.addedBy);
                        }
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        List<Entry> entries = new ArrayList<>();
                        for (int i = 0; i < size; i++) {
                            entries.add(new Entry(
                                    buf.readString(), buf.readString(), buf.readString(), buf.readString()
                            ));
                        }
                        return new PlaylistSyncPacket(entries);
                    }
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public record Entry(String videoId, String title, String duration, String addedBy) {}
}
