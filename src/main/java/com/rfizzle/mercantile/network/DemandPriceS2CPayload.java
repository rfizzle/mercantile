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

    public static final int MAX_OFFERS = 32;

    public record PriceComponent(
            int basePrice,
            int demandAdjust,
            int reputationModifier,
            int moodModifier,
            int gossipModifier,
            int marketDayModifier,
            int otherAdjust,
            int finalPrice
    ) {
        // StreamCodec.composite tops out at 6 fields; this record has 8.
        public static final StreamCodec<ByteBuf, PriceComponent> STREAM_CODEC =
                StreamCodec.of(PriceComponent::encode, PriceComponent::decode);

        private static void encode(ByteBuf buf, PriceComponent c) {
            ByteBufCodecs.VAR_INT.encode(buf, c.basePrice);
            ByteBufCodecs.VAR_INT.encode(buf, c.demandAdjust);
            ByteBufCodecs.VAR_INT.encode(buf, c.reputationModifier);
            ByteBufCodecs.VAR_INT.encode(buf, c.moodModifier);
            ByteBufCodecs.VAR_INT.encode(buf, c.gossipModifier);
            ByteBufCodecs.VAR_INT.encode(buf, c.marketDayModifier);
            ByteBufCodecs.VAR_INT.encode(buf, c.otherAdjust);
            ByteBufCodecs.VAR_INT.encode(buf, c.finalPrice);
        }

        private static PriceComponent decode(ByteBuf buf) {
            return new PriceComponent(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf));
        }
    }

    public static final Type<DemandPriceS2CPayload> TYPE =
            new Type<>(Mercantile.id("demand_price_s2c"));

    public static final StreamCodec<ByteBuf, DemandPriceS2CPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DemandPriceS2CPayload::villagerEntityId,
                    ByteBufCodecs.collection(ArrayList::new, PriceComponent.STREAM_CODEC, MAX_OFFERS),
                    DemandPriceS2CPayload::components,
                    DemandPriceS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
