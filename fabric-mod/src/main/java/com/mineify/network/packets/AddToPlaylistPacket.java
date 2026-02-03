package com.mineify.network.packets;

import com.mineify.Mineify;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AddToPlaylistPacket(String videoId, String title, String duration) implements CustomPayload {
    public static final CustomPayload.Id<AddToPlaylistPacket> ID =
            new CustomPayload.Id<>(Identifier.of(Mineify.MOD_ID, "add_to_playlist"));

    public static final PacketCodec<RegistryByteBuf, AddToPlaylistPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeString(value.videoId);
                        buf.writeString(value.title);
                        buf.writeString(value.duration);
                    },
                    buf -> new AddToPlaylistPacket(buf.readString(), buf.readString(), buf.readString())
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
