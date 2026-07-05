package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the entity id of the villager holding the requested delivery contract, or
 * {@link #NONE} when no nearby villager matches (wrong village, picked up, expired). The client
 * glows the matched villager for the holding player only (issue #86).
 */
public record ContractTargetS2CPayload(int villagerEntityId) implements CustomPacketPayload {

    public static final int NONE = -1;

    public static final Type<ContractTargetS2CPayload> TYPE =
            new Type<>(Mercantile.id("contract_target_s2c"));

    public static final StreamCodec<ByteBuf, ContractTargetS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ContractTargetS2CPayload::villagerEntityId,
                    ContractTargetS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
