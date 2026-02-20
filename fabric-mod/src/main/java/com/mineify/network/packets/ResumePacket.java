package com.mineify.network.packets;

import com.mineify.Mineify;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ResumePacket(long positionMs) implements CustomPayload {
    public static final CustomPayload.Id<ResumePacket> ID =
            new CustomPayload.Id<>(Identifier.of(Mineify.MOD_ID, "resume"));

    public static final PacketCodec<RegistryByteBuf, ResumePacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> buf.writeLong(value.positionMs),
                    buf -> new ResumePacket(buf.readLong())
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
