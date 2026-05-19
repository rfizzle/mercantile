package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RequestVillageBoundsC2SPayload() implements CustomPacketPayload {

    public static final Type<RequestVillageBoundsC2SPayload> TYPE =
            new Type<>(Mercantile.id("request_village_bounds_c2s"));

    public static final StreamCodec<ByteBuf, RequestVillageBoundsC2SPayload> CODEC =
            StreamCodec.unit(new RequestVillageBoundsC2SPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
