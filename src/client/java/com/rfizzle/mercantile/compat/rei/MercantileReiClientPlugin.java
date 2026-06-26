package com.rfizzle.mercantile.compat.rei;

import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexIcon;
import com.rfizzle.mercantile.trade.index.ProfessionWorkstations;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MercantileReiClientPlugin implements REIClientPlugin {

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new VillagerTradeDisplayCategory());

        Set<ResourceLocation> profIds = new HashSet<>();
        Set<Item> workstationItems = new LinkedHashSet<>();
        List<TradeIndexEntry> entries = ClientMercantileData.getTradeIndex();
        for (TradeIndexEntry entry : entries) {
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

        List<EntryIngredient> workstations = new ArrayList<>(profIds.size() + workstationItems.size());
        for (ResourceLocation id : profIds) {
            workstations.add(EntryIngredient.of(EntryStacks.of(TradeIndexIcon.forProfession(id))));
        }
        for (Item item : workstationItems) {
            workstations.add(EntryIngredient.of(EntryStacks.of(new ItemStack(item))));
        }
        registry.addWorkstations(VillagerTradeDisplay.IDENTIFIER, workstations.toArray(new EntryIngredient[0]));
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        List<TradeIndexEntry> entries = ClientMercantileData.getTradeIndex();
        for (TradeIndexEntry entry : entries) {
            registry.add(new VillagerTradeDisplay(entry));
        }
    }
}
