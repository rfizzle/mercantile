package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ConfigSyncS2CPayload(String configJson) implements CustomPacketPayload {

    public static final Type<ConfigSyncS2CPayload> TYPE =
            new Type<>(Mercantile.id("config_sync_s2c"));

    public static final StreamCodec<ByteBuf, ConfigSyncS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ConfigSyncS2CPayload::configJson,
                    ConfigSyncS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
