package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Toggles the sending player's pin on one offer of the villager they are trading with. */
public record PinTradeC2SPayload(int villagerEntityId, int offerIndex) implements CustomPacketPayload {

    public static final Type<PinTradeC2SPayload> TYPE =
            new Type<>(Mercantile.id("pin_trade_c2s"));

    public static final StreamCodec<ByteBuf, PinTradeC2SPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PinTradeC2SPayload::villagerEntityId,
                    ByteBufCodecs.VAR_INT, PinTradeC2SPayload::offerIndex,
                    PinTradeC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
