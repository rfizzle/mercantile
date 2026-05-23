package com.rfizzle.mercantile.compat.rei;

import com.rfizzle.mercantile.compat.tradeindex.TradeIndexIcon;
import com.rfizzle.mercantile.trade.index.TradeIndexDataSource;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MercantileReiClientPlugin implements REIClientPlugin {

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new VillagerTradeDisplayCategory());

        Set<ResourceLocation> profIds = new HashSet<>();
        for (TradeIndexEntry entry : TradeIndexDataSource.snapshot()) {
            profIds.add(entry.profession());
        }
        for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
            ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            if (id != null) profIds.add(id);
        }

        List<EntryIngredient> workstations = new ArrayList<>(profIds.size());
        for (ResourceLocation id : profIds) {
            workstations.add(EntryIngredient.of(EntryStacks.of(TradeIndexIcon.forProfession(id))));
        }
        registry.addWorkstations(VillagerTradeDisplay.IDENTIFIER, workstations.toArray(new EntryIngredient[0]));
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        for (TradeIndexEntry entry : TradeIndexDataSource.snapshot()) {
            registry.add(new VillagerTradeDisplay(entry));
        }
    }
}
