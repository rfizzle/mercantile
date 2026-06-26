package com.rfizzle.mercantile.compat.emi;

import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexIcon;
import com.rfizzle.mercantile.trade.index.ProfessionWorkstations;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MercantileEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(MercantileEmiCategories.VILLAGER_TRADES);

        Set<ResourceLocation> seenProfessions = new HashSet<>();
        Set<Item> seenWorkstations = new LinkedHashSet<>();
        Map<String, Integer> dupCounts = new HashMap<>();

        List<TradeIndexEntry> entries = ClientMercantileData.getTradeIndex();
        for (TradeIndexEntry entry : entries) {
            String key = entry.profession() + "|" + entry.level() + "|" + entry.source();
            int suffix = dupCounts.merge(key, 0, (oldV, ignored) -> oldV + 1);
            registry.addRecipe(new VillagerTradeEmiRecipe(entry, suffix));
            seenProfessions.add(entry.profession());
            if (!entry.workstation().isEmpty()) {
                seenWorkstations.add(entry.workstation().getItem());
            }
        }

        for (ResourceLocation profId : seenProfessions) {
            registry.addWorkstation(MercantileEmiCategories.VILLAGER_TRADES,
                    EmiStack.of(TradeIndexIcon.forProfession(profId)));
        }

        // Cover registered professions with no trades so they still surface in the workstation grid.
        for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
            ResourceLocation profId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            if (profId == null) continue;
            if (seenProfessions.add(profId)) {
                registry.addWorkstation(MercantileEmiCategories.VILLAGER_TRADES,
                        EmiStack.of(TradeIndexIcon.forProfession(profId)));
            }
            Block workstation = ProfessionWorkstations.forProfession(profId);
            if (workstation != null) {
                ItemStack stack = new ItemStack(workstation);
                if (!stack.isEmpty()) {
                    seenWorkstations.add(stack.getItem());
                }
            }
        }

        // Register the unlocking workstation block for each profession so players can look it up by block.
        for (Item item : seenWorkstations) {
            registry.addWorkstation(MercantileEmiCategories.VILLAGER_TRADES,
                    EmiStack.of(new ItemStack(item)));
        }
    }
}
