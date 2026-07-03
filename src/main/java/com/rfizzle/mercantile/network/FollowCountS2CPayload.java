package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The receiving player's own follower count. {@link FollowStateS2CPayload} is a
 * level-wide broadcast keyed by villager entity id, so a client cannot derive
 * <em>its</em> count from it — this payload carries the server-authoritative
 * per-player figure, sent only to the owning player on join and on every
 * follow/unfollow that changes their count.
 */
public record FollowCountS2CPayload(int count) implements CustomPacketPayload {

    public static final Type<FollowCountS2CPayload> TYPE =
            new Type<>(Mercantile.id("follow_count_s2c"));

    public static final StreamCodec<ByteBuf, FollowCountS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FollowCountS2CPayload::count,
                    FollowCountS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
