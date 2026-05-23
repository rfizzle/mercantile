package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.visualization.BellRingService;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record BellRingS2CPayload(
        BlockPos bellPos,
        List<UUID> villagerIds
) implements CustomPacketPayload {

    public static final int MAX_VILLAGERS = BellRingService.MAX_VILLAGERS;

    public static final Type<BellRingS2CPayload> TYPE =
            new Type<>(Mercantile.id("bell_ring_s2c"));

    public static final StreamCodec<FriendlyByteBuf, BellRingS2CPayload> CODEC =
            StreamCodec.of(BellRingS2CPayload::encode, BellRingS2CPayload::decode);

    private static void encode(FriendlyByteBuf buf, BellRingS2CPayload payload) {
        if (payload.villagerIds.size() > MAX_VILLAGERS) {
            throw new EncoderException("BellRingS2CPayload exceeds villager cap: "
                    + payload.villagerIds.size() + " > " + MAX_VILLAGERS);
        }
        buf.writeBlockPos(payload.bellPos);
        buf.writeVarInt(payload.villagerIds.size());
        for (UUID id : payload.villagerIds) {
            buf.writeUUID(id);
        }
    }

    private static BellRingS2CPayload decode(FriendlyByteBuf buf) {
        BlockPos bellPos = buf.readBlockPos();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_VILLAGERS) {
            throw new DecoderException("BellRingS2CPayload villager count out of bounds: " + size);
        }
        List<UUID> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(buf.readUUID());
        }
        return new BellRingS2CPayload(bellPos, ids);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
