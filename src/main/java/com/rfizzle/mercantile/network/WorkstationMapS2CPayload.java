package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record WorkstationMapS2CPayload(Map<UUID, BlockPos> entries) implements CustomPacketPayload {

    public static final Type<WorkstationMapS2CPayload> TYPE =
            new Type<>(Mercantile.id("workstation_map_s2c"));

    public static final StreamCodec<FriendlyByteBuf, WorkstationMapS2CPayload> CODEC =
            StreamCodec.of(WorkstationMapS2CPayload::encode, WorkstationMapS2CPayload::decode);

    private static void encode(FriendlyByteBuf buf, WorkstationMapS2CPayload payload) {
        buf.writeVarInt(payload.entries.size());
        for (var entry : payload.entries.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeBlockPos(entry.getValue());
        }
    }

    private static WorkstationMapS2CPayload decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
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
