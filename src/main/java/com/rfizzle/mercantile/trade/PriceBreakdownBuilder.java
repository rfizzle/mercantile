package com.rfizzle.mercantile.trade;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.market.MarketDayManager;
import com.rfizzle.mercantile.memorial.FearManager;
import com.rfizzle.mercantile.memorial.FearMath;
import com.rfizzle.mercantile.mixin.MerchantOfferDemandAccessor;
import com.rfizzle.mercantile.mood.MoodManager;
import com.rfizzle.mercantile.network.DemandPriceS2CPayload;
import com.rfizzle.mercantile.reputation.ReputationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.List;

public final class PriceBreakdownBuilder {

    private PriceBreakdownBuilder() {
    }

    public static List<DemandPriceS2CPayload.PriceComponent> buildFor(Villager villager, ServerPlayer player) {
        MercantileConfig config = MercantileConfig.get();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        int score = data.getScore();
        int gossipReputation = villager.getPlayerReputation(player);

        List<MerchantOffer> offers = villager.getOffers();
        List<DemandPriceS2CPayload.PriceComponent> components = new ArrayList<>(offers.size());
        double fearFraction = FearManager.fearFraction(villager, player, config);
        for (MerchantOffer offer : offers) {
            int basePrice = offer.getBaseCostA().getCount();
            int demand = ((MerchantOfferDemandAccessor) offer).mercantile$getDemand();
            float priceMultiplier = offer.getPriceMultiplier();

            int demandAdjust = Math.max(0, Mth.floor(basePrice * demand * priceMultiplier));

            int reputationModifier = (score != 0 && config.enableReputation)
                    ? ReputationManager.getPriceModifier(score, basePrice)
                    : 0;

            int moodModifier = MoodManager.priceModifier(villager, basePrice, config);
            int marketDayModifier = MarketDayManager.priceModifier(villager, basePrice, config);

            // Mirrors VillagerTradeOpenMixin: an absolute setSpecialPriceDiff from
            // reputation supersedes vanilla's gossip special price; mood stacks on top
            // of whichever base (gossip or reputation) is active.
            int gossipModifier = (reputationModifier != 0)
                    ? 0
                    : -Mth.floor(gossipReputation * priceMultiplier);

            // Same headroom cap as the mixin: fear past the vanilla max-stack clamp is
            // never charged, so it must not be reported either.
            int rawFear = FearManager.priceModifier(basePrice, fearFraction, config);
            int fearModifier = FearMath.capToHeadroom(rawFear, offer.getBaseCostA().getMaxStackSize(),
                    basePrice, demandAdjust,
                    reputationModifier + gossipModifier + moodModifier + marketDayModifier);

            int finalPrice = offer.getCostA().getCount();
            int otherAdjust = finalPrice - basePrice - demandAdjust - reputationModifier - moodModifier
                    - gossipModifier - marketDayModifier - fearModifier;

            components.add(new DemandPriceS2CPayload.PriceComponent(
                    basePrice, demandAdjust, reputationModifier, moodModifier, gossipModifier,
                    marketDayModifier, fearModifier, otherAdjust, finalPrice));
        }
        return components;
    }
}
