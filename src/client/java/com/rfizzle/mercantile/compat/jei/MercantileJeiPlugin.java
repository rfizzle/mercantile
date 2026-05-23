package com.rfizzle.mercantile.compat.jei;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexIcon;
import com.rfizzle.mercantile.trade.index.TradeIndexDataSource;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JeiPlugin
public class MercantileJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = Mercantile.id("trade_index");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new VillagerTradeJeiCategory(
                registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(VillagerTradeJeiCategory.TYPE, List.copyOf(TradeIndexDataSource.snapshot()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        Set<ResourceLocation> profIds = new HashSet<>();
        for (TradeIndexEntry entry : TradeIndexDataSource.snapshot()) {
            profIds.add(entry.profession());
        }
        for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
            ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            if (id != null) profIds.add(id);
        }
        for (ResourceLocation profId : profIds) {
            registration.addRecipeCatalyst(TradeIndexIcon.forProfession(profId),
                    VillagerTradeJeiCategory.TYPE);
        }
    }
}
