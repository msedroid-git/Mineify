package com.mineify.network.packets;

import com.mineify.Mineify;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Carries a chunk of raw WAV audio data from the server to clients.
 * The server splits a downloaded WAV file into fixed-size chunks and sends
 * them in order. Clients buffer all chunks and begin playback once the final
 * chunk (chunkIndex == totalChunks - 1) is received.
 */
public record AudioChunkPacket(
        String videoId,
        String title,
        int chunkIndex,
        int totalChunks,
        long startOffsetMs,
        byte[] data
) implements CustomPayload {

    public static final CustomPayload.Id<AudioChunkPacket> ID =
            new CustomPayload.Id<>(Identifier.of(Mineify.MOD_ID, "audio_chunk"));

    // Each chunk is at most 30 KB, safely under Short.MAX_VALUE (32 767)
    public static final int CHUNK_SIZE = 30 * 1024;

    public static final PacketCodec<RegistryByteBuf, AudioChunkPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeString(value.videoId);
                        buf.writeString(value.title);
                        buf.writeInt(value.chunkIndex);
                        buf.writeInt(value.totalChunks);
                        buf.writeLong(value.startOffsetMs);
                        buf.writeByteArray(value.data);
                    },
                    buf -> new AudioChunkPacket(
                            buf.readString(),
                            buf.readString(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readLong(),
                            buf.readByteArray(Short.MAX_VALUE)
                    )
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
