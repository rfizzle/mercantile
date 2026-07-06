package com.rfizzle.mercantile.compat.emi;

import com.rfizzle.mercantile.compat.tradeindex.TradeIndexCategoryKey;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexIcon;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;

public final class MercantileEmiCategories {

    private MercantileEmiCategories() {
    }

    /**
     * Builds the EMI category for a shared {@link TradeIndexCategoryKey}. EMI derives a
     * category's display name from an {@code emi.category.<id>} translation key by default;
     * overriding {@link EmiRecipeCategory#getName()} lets the tier categories reuse the one
     * formatted {@code category.mercantile.villager_trades.tier} key instead of needing a
     * distinct EMI key per tier.
     */
    public static EmiRecipeCategory create(TradeIndexCategoryKey key) {
        return new TitledCategory(key);
    }

    private static final class TitledCategory extends EmiRecipeCategory {
        private final TradeIndexCategoryKey key;

        private TitledCategory(TradeIndexCategoryKey key) {
            super(key.id(), EmiStack.of(TradeIndexIcon.categoryIcon()));
            this.key = key;
        }

        @Override
        public Component getName() {
            return key.title();
        }
    }
}
