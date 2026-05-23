package com.rfizzle.mercantile.compat.emi;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexIcon;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;

public final class MercantileEmiCategories {

    public static final EmiRecipeCategory VILLAGER_TRADES = new EmiRecipeCategory(
            Mercantile.id("villager_trades"),
            EmiStack.of(TradeIndexIcon.categoryIcon())
    );

    private MercantileEmiCategories() {
    }
}
