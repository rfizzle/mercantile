package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CycleTradesC2SPayload(int villagerEntityId) implements CustomPacketPayload {

    public static final Type<CycleTradesC2SPayload> TYPE =
            new Type<>(Mercantile.id("cycle_trades_c2s"));

    public static final StreamCodec<ByteBuf, CycleTradesC2SPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CycleTradesC2SPayload::villagerEntityId,
                    CycleTradesC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
