package com.rfizzle.mercantile.compat.tradeindex;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.data.VillagerHeadTextures;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TradeIndexIcon {

    public static final ResourceLocation CROSS_PROFESSION_ID = Mercantile.id("exclusive");

    private static final Map<ResourceLocation, ItemStack> CACHE = new ConcurrentHashMap<>();

    private TradeIndexIcon() {
    }

    public static ItemStack forProfession(ResourceLocation professionId) {
        return CACHE.computeIfAbsent(professionId, TradeIndexIcon::build).copy();
    }

    public static ItemStack categoryIcon() {
        return forProfession(VillagerHeadTextures.FALLBACK_ID);
    }

    private static ItemStack build(ResourceLocation professionId) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.PROFILE, VillagerHeadTextures.getProfile(professionId));
        stack.set(DataComponents.CUSTOM_NAME,
                VillagerHeadTextures.getDisplayName(professionId)
                        .copy().withStyle(Style.EMPTY.withItalic(false)));
        return stack;
    }
}
