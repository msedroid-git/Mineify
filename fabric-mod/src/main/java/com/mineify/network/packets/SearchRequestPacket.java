package com.mineify.network.packets;

import com.mineify.Mineify;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SearchRequestPacket(String query) implements CustomPayload {
    public static final CustomPayload.Id<SearchRequestPacket> ID =
            new CustomPayload.Id<>(Identifier.of(Mineify.MOD_ID, "search_request"));

    public static final PacketCodec<RegistryByteBuf, SearchRequestPacket> CODEC =
            PacketCodecs.STRING.xmap(SearchRequestPacket::new, SearchRequestPacket::query)
                    .cast();

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
