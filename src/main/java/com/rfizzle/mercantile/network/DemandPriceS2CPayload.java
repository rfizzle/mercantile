package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.Mercantile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record DemandPriceS2CPayload(
        int villagerEntityId,
        List<PriceComponent> components
) implements CustomPacketPayload {

    public record PriceComponent(
            int basePrice,
            int demandAdjust,
            int reputationModifier,
            int gossipModifier,
            int finalPrice
    ) {
        public static final StreamCodec<ByteBuf, PriceComponent> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, PriceComponent::basePrice,
                        ByteBufCodecs.VAR_INT, PriceComponent::demandAdjust,
                        ByteBufCodecs.VAR_INT, PriceComponent::reputationModifier,
                        ByteBufCodecs.VAR_INT, PriceComponent::gossipModifier,
                        ByteBufCodecs.VAR_INT, PriceComponent::finalPrice,
                        PriceComponent::new);
    }

    public static final Type<DemandPriceS2CPayload> TYPE =
            new Type<>(Mercantile.id("demand_price_s2c"));

    public static final StreamCodec<ByteBuf, DemandPriceS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DemandPriceS2CPayload::villagerEntityId,
                    ByteBufCodecs.collection(ArrayList::new, PriceComponent.STREAM_CODEC),
                    DemandPriceS2CPayload::components,
                    DemandPriceS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
