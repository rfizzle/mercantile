package com.rfizzle.mercantile.compat.tradeindex;

import com.rfizzle.mercantile.data.VillagerHeadTextures;
import com.rfizzle.mercantile.api.ReputationTier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class TradeIndexLabels {

    private TradeIndexLabels() {
    }

    public static Component levelLabel(int level) {
        if (level < 1 || level > 5) return Component.empty();
        return Component.translatable("gui.mercantile.trade_index.level." + level);
    }

    public static Component tierLabel(int minScore) {
        ReputationTier tier = ReputationTier.fromScore(minScore);
        return tier.displayName();
    }

    public static Component professionLabel(ResourceLocation professionId) {
        if (TradeIndexIcon.CROSS_PROFESSION_ID.equals(professionId)) {
            return Component.translatable("tooltip.mercantile.trade_index.exclusive");
        }
        return VillagerHeadTextures.getDisplayName(professionId);
    }
}
