package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FollowVillagerC2SPayload(int villagerEntityId) implements CustomPacketPayload {

    public static final Type<FollowVillagerC2SPayload> TYPE =
            new Type<>(Mercantile.id("follow_villager_c2s"));

    public static final StreamCodec<ByteBuf, FollowVillagerC2SPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FollowVillagerC2SPayload::villagerEntityId,
                    FollowVillagerC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
