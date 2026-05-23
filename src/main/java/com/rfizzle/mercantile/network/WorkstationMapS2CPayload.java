package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WorkstationMapS2CPayload(
        Map<UUID, BlockPos> bound,
        List<UUID> unboundVillagers,
        List<BlockPos> unclaimedWorkstations
) implements CustomPacketPayload {

    public static final int MAX_ENTRIES = 1024;
    public static final int MAX_UNBOUND = 1024;
    public static final int MAX_UNCLAIMED = 1024;

    public static final Type<WorkstationMapS2CPayload> TYPE =
            new Type<>(Mercantile.id("workstation_map_s2c"));

    public static final StreamCodec<FriendlyByteBuf, WorkstationMapS2CPayload> CODEC =
            StreamCodec.of(WorkstationMapS2CPayload::encode, WorkstationMapS2CPayload::decode);

    private static void encode(FriendlyByteBuf buf, WorkstationMapS2CPayload payload) {
        if (payload.bound.size() > MAX_ENTRIES) {
            throw new EncoderException("WorkstationMapS2CPayload exceeds bound cap: " + payload.bound.size() + " > " + MAX_ENTRIES);
        }
        if (payload.unboundVillagers.size() > MAX_UNBOUND) {
            throw new EncoderException("WorkstationMapS2CPayload exceeds unbound cap: " + payload.unboundVillagers.size() + " > " + MAX_UNBOUND);
        }
        if (payload.unclaimedWorkstations.size() > MAX_UNCLAIMED) {
            throw new EncoderException("WorkstationMapS2CPayload exceeds unclaimed cap: " + payload.unclaimedWorkstations.size() + " > " + MAX_UNCLAIMED);
        }
        buf.writeVarInt(payload.bound.size());
        for (var entry : payload.bound.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeBlockPos(entry.getValue());
        }
        buf.writeVarInt(payload.unboundVillagers.size());
        for (UUID id : payload.unboundVillagers) {
            buf.writeUUID(id);
        }
        buf.writeVarInt(payload.unclaimedWorkstations.size());
        for (BlockPos pos : payload.unclaimedWorkstations) {
            buf.writeBlockPos(pos);
        }
    }

    private static WorkstationMapS2CPayload decode(FriendlyByteBuf buf) {
        int boundSize = buf.readVarInt();
        if (boundSize < 0 || boundSize > MAX_ENTRIES) {
            throw new DecoderException("WorkstationMapS2CPayload bound count out of bounds: " + boundSize);
        }
        Map<UUID, BlockPos> bound = new HashMap<>(boundSize);
        for (int i = 0; i < boundSize; i++) {
            bound.put(buf.readUUID(), buf.readBlockPos());
        }
        int unboundSize = buf.readVarInt();
        if (unboundSize < 0 || unboundSize > MAX_UNBOUND) {
            throw new DecoderException("WorkstationMapS2CPayload unbound count out of bounds: " + unboundSize);
        }
        List<UUID> unbound = new ArrayList<>(unboundSize);
        for (int i = 0; i < unboundSize; i++) {
            unbound.add(buf.readUUID());
        }
        int unclaimedSize = buf.readVarInt();
        if (unclaimedSize < 0 || unclaimedSize > MAX_UNCLAIMED) {
            throw new DecoderException("WorkstationMapS2CPayload unclaimed count out of bounds: " + unclaimedSize);
        }
        List<BlockPos> unclaimed = new ArrayList<>(unclaimedSize);
        for (int i = 0; i < unclaimedSize; i++) {
            unclaimed.add(buf.readBlockPos());
        }
        return new WorkstationMapS2CPayload(bound, unbound, unclaimed);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
