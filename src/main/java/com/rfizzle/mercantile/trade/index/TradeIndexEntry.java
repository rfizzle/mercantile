package com.rfizzle.mercantile.trade.index;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalInt;

public record TradeIndexEntry(
        ResourceLocation profession,
        int level,
        Source source,
        ItemStack inputA,
        ItemStack inputB,
        ItemStack output,
        int maxUses,
        int xpGain,
        float priceMultiplier,
        OptionalInt minScore
) {
    public enum Source {
        VANILLA,
        EXCLUSIVE_PROFESSION,
        EXCLUSIVE_CROSS_PROFESSION
    }
}
