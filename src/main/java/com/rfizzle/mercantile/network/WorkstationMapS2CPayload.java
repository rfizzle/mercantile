package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record WorkstationMapS2CPayload(Map<UUID, BlockPos> entries) implements CustomPacketPayload {

    public static final int MAX_ENTRIES = 1024;

    public static final Type<WorkstationMapS2CPayload> TYPE =
            new Type<>(Mercantile.id("workstation_map_s2c"));

    public static final StreamCodec<FriendlyByteBuf, WorkstationMapS2CPayload> CODEC =
            StreamCodec.of(WorkstationMapS2CPayload::encode, WorkstationMapS2CPayload::decode);

    private static void encode(FriendlyByteBuf buf, WorkstationMapS2CPayload payload) {
        if (payload.entries.size() > MAX_ENTRIES) {
            throw new EncoderException("WorkstationMapS2CPayload exceeds entry cap: " + payload.entries.size() + " > " + MAX_ENTRIES);
        }
        buf.writeVarInt(payload.entries.size());
        for (var entry : payload.entries.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeBlockPos(entry.getValue());
        }
    }

    private static WorkstationMapS2CPayload decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new DecoderException("WorkstationMapS2CPayload entry count out of bounds: " + size);
        }
        Map<UUID, BlockPos> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            map.put(buf.readUUID(), buf.readBlockPos());
        }
        return new WorkstationMapS2CPayload(map);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
