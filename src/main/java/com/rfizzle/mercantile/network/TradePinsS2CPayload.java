package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The receiving player's pin state for the open villager's offers, index-aligned with the
 * merchant screen's offer list (mirrors {@link DemandPriceS2CPayload}). Sent on trade open
 * and after every pin toggle.
 */
public record TradePinsS2CPayload(
        int villagerEntityId,
        List<Boolean> pinnedByIndex
) implements CustomPacketPayload {

    public static final int MAX_OFFERS = 32;

    public static final Type<TradePinsS2CPayload> TYPE =
            new Type<>(Mercantile.id("trade_pins_s2c"));

    public static final StreamCodec<ByteBuf, TradePinsS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TradePinsS2CPayload::villagerEntityId,
                    ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.BOOL, MAX_OFFERS),
                    TradePinsS2CPayload::pinnedByIndex,
                    TradePinsS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
