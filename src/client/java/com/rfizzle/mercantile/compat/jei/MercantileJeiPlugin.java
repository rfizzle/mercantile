package com.rfizzle.mercantile.compat.jei;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexIcon;
import com.rfizzle.mercantile.trade.index.ProfessionWorkstations;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.HashSet;
import java.util.LinkedHashSet;
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
        Set<Item> workstationItems = new LinkedHashSet<>();
        for (TradeIndexEntry entry : TradeIndexDataSource.snapshot()) {
            profIds.add(entry.profession());
            if (!entry.workstation().isEmpty()) {
                workstationItems.add(entry.workstation().getItem());
            }
        }
        for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
            ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            if (id == null) continue;
            profIds.add(id);
            Block workstation = ProfessionWorkstations.forProfession(id);
            if (workstation != null) {
                ItemStack stack = new ItemStack(workstation);
                if (!stack.isEmpty()) {
                    workstationItems.add(stack.getItem());
                }
            }
        }
        for (ResourceLocation profId : profIds) {
            registration.addRecipeCatalyst(TradeIndexIcon.forProfession(profId),
                    VillagerTradeJeiCategory.TYPE);
        }
        for (Item item : workstationItems) {
            registration.addRecipeCatalyst(new ItemStack(item), VillagerTradeJeiCategory.TYPE);
        }
    }
}
