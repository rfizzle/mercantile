package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Client → server: "which nearby villager holds the accepted delivery contract with this id?"
 * Sent on a throttle while the player holds a written contract; answered with
 * {@link ContractTargetS2CPayload} so the client can glow the payee (issue #86).
 */
public record RequestContractTargetC2SPayload(UUID contractId) implements CustomPacketPayload {

    public static final Type<RequestContractTargetC2SPayload> TYPE =
            new Type<>(Mercantile.id("request_contract_target_c2s"));

    public static final StreamCodec<ByteBuf, RequestContractTargetC2SPayload> CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, RequestContractTargetC2SPayload::contractId,
                    RequestContractTargetC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
