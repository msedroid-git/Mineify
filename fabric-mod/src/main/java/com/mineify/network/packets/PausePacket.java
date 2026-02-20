package com.mineify.network.packets;

import com.mineify.Mineify;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PausePacket(long positionMs) implements CustomPayload {
    public static final CustomPayload.Id<PausePacket> ID =
            new CustomPayload.Id<>(Identifier.of(Mineify.MOD_ID, "pause"));

    public static final PacketCodec<RegistryByteBuf, PausePacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> buf.writeLong(value.positionMs),
                    buf -> new PausePacket(buf.readLong())
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
