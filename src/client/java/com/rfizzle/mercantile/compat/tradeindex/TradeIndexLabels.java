package com.rfizzle.mercantile.compat.tradeindex;

import com.rfizzle.mercantile.data.VillagerHeadTextures;
import com.rfizzle.mercantile.reputation.ReputationTier;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.OptionalInt;

public final class TradeIndexLabels {

    private TradeIndexLabels() {
    }

    public static Component levelLabel(int level) {
        if (level < 1 || level > 5) return Component.empty();
        return Component.translatable("mercantile.trade_index.level." + level);
    }

    public static Component tierLabel(int minScore) {
        ReputationTier tier = ReputationTier.fromScore(minScore);
        return tier.displayName();
    }

    public static Component professionLabel(ResourceLocation professionId) {
        if (TradeIndexIcon.CROSS_PROFESSION_ID.equals(professionId)) {
            return Component.translatable("mercantile.trade_index.exclusive");
        }
        return VillagerHeadTextures.getDisplayName(professionId);
    }

    public static Component sourceBadge(TradeIndexEntry.Source source, OptionalInt minScore) {
        if (source == TradeIndexEntry.Source.VANILLA || minScore.isEmpty()) {
            return Component.empty();
        }
        return Component.translatable("mercantile.trade_index.exclusive_tooltip",
                tierLabel(minScore.getAsInt()));
    }
}
