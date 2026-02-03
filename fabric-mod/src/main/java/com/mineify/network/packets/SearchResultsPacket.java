package com.mineify.network.packets;

import com.mineify.Mineify;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record SearchResultsPacket(List<Entry> results) implements CustomPayload {
    public static final CustomPayload.Id<SearchResultsPacket> ID =
            new CustomPayload.Id<>(Identifier.of(Mineify.MOD_ID, "search_results"));

    public static final PacketCodec<RegistryByteBuf, SearchResultsPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeVarInt(value.results.size());
                        for (Entry e : value.results) {
                            buf.writeString(e.videoId);
                            buf.writeString(e.title);
                            buf.writeString(e.channel);
                            buf.writeString(e.duration);
                            buf.writeString(e.thumbnail);
                        }
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        List<Entry> results = new ArrayList<>();
                        for (int i = 0; i < size; i++) {
                            results.add(new Entry(
                                    buf.readString(), buf.readString(), buf.readString(),
                                    buf.readString(), buf.readString()
                            ));
                        }
                        return new SearchResultsPacket(results);
                    }
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public record Entry(String videoId, String title, String channel, String duration, String thumbnail) {}
}
