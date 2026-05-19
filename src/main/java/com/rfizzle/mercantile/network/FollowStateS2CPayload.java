package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FollowStateS2CPayload(int villagerEntityId, boolean following) implements CustomPacketPayload {

    public static final Type<FollowStateS2CPayload> TYPE =
            new Type<>(Mercantile.id("follow_state_s2c"));

    public static final StreamCodec<ByteBuf, FollowStateS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FollowStateS2CPayload::villagerEntityId,
                    ByteBufCodecs.BOOL, FollowStateS2CPayload::following,
                    FollowStateS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
