package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PylonStateS2CPayload(
        BlockPos pylonPos,
        int fuelLevel,
        int maxFuel,
        boolean powered,
        boolean active
) implements CustomPacketPayload {

    public static final Type<PylonStateS2CPayload> TYPE =
            new Type<>(Mercantile.id("pylon_state_s2c"));

    public static final StreamCodec<ByteBuf, PylonStateS2CPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PylonStateS2CPayload::pylonPos,
                    ByteBufCodecs.VAR_INT, PylonStateS2CPayload::fuelLevel,
                    ByteBufCodecs.VAR_INT, PylonStateS2CPayload::maxFuel,
                    ByteBufCodecs.BOOL, PylonStateS2CPayload::powered,
                    ByteBufCodecs.BOOL, PylonStateS2CPayload::active,
                    PylonStateS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
