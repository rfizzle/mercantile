package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RestockTimerS2CPayload(
        int villagerEntityId,
        long lastRestockGameTime,
        int restockCountToday,
        boolean hasWorkstation,
        int restockIntervalTicks,
        int maxRestocksToday
) implements CustomPacketPayload {

    public static final Type<RestockTimerS2CPayload> TYPE =
            new Type<>(Mercantile.id("restock_timer_s2c"));

    public static final StreamCodec<ByteBuf, RestockTimerS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RestockTimerS2CPayload::villagerEntityId,
                    ByteBufCodecs.VAR_LONG, RestockTimerS2CPayload::lastRestockGameTime,
                    ByteBufCodecs.VAR_INT, RestockTimerS2CPayload::restockCountToday,
                    ByteBufCodecs.BOOL, RestockTimerS2CPayload::hasWorkstation,
                    ByteBufCodecs.VAR_INT, RestockTimerS2CPayload::restockIntervalTicks,
                    ByteBufCodecs.VAR_INT, RestockTimerS2CPayload::maxRestocksToday,
                    RestockTimerS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
