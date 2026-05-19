package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SyncReputationS2CPayload(int score, String tierName) implements CustomPacketPayload {

    public static final Type<SyncReputationS2CPayload> TYPE =
            new Type<>(Mercantile.id("sync_reputation_s2c"));

    public static final StreamCodec<ByteBuf, SyncReputationS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncReputationS2CPayload::score,
                    ByteBufCodecs.STRING_UTF8, SyncReputationS2CPayload::tierName,
                    SyncReputationS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
