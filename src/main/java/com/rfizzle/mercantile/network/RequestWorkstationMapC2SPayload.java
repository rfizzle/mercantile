package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RequestWorkstationMapC2SPayload() implements CustomPacketPayload {

    public static final Type<RequestWorkstationMapC2SPayload> TYPE =
            new Type<>(Mercantile.id("request_workstation_map_c2s"));

    public static final StreamCodec<ByteBuf, RequestWorkstationMapC2SPayload> CODEC =
            StreamCodec.unit(new RequestWorkstationMapC2SPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
