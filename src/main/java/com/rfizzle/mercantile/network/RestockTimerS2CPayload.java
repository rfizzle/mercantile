package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RestockTimerS2CPayload(
        int villagerEntityId,
        int lastRestockTick,
        int restockCountToday,
        boolean hasWorkstation
) implements CustomPacketPayload {

    public static final Type<RestockTimerS2CPayload> TYPE =
            new Type<>(Mercantile.id("restock_timer_s2c"));

    public static final StreamCodec<ByteBuf, RestockTimerS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RestockTimerS2CPayload::villagerEntityId,
                    ByteBufCodecs.VAR_INT, RestockTimerS2CPayload::lastRestockTick,
                    ByteBufCodecs.VAR_INT, RestockTimerS2CPayload::restockCountToday,
                    ByteBufCodecs.BOOL, RestockTimerS2CPayload::hasWorkstation,
                    RestockTimerS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
