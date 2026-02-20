package com.mineify.network.packets;

import com.mineify.Mineify;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ReadyPacket(String videoId) implements CustomPayload {
    public static final CustomPayload.Id<ReadyPacket> ID =
            new CustomPayload.Id<>(Identifier.of(Mineify.MOD_ID, "ready"));

    public static final PacketCodec<RegistryByteBuf, ReadyPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> buf.writeString(value.videoId),
                    buf -> new ReadyPacket(buf.readString())
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
