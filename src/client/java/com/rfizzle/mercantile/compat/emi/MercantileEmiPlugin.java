package com.rfizzle.mercantile.compat.emi;

import com.rfizzle.mercantile.compat.tradeindex.TradeIndexIcon;
import com.rfizzle.mercantile.trade.index.TradeIndexDataSource;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MercantileEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(MercantileEmiCategories.VILLAGER_TRADES);

        Set<ResourceLocation> seenProfessions = new HashSet<>();
        Map<String, Integer> dupCounts = new HashMap<>();

        for (TradeIndexEntry entry : TradeIndexDataSource.snapshot()) {
            String key = entry.profession() + "|" + entry.level() + "|" + entry.source();
            int suffix = dupCounts.merge(key, 0, (oldV, ignored) -> oldV + 1);
            registry.addRecipe(new VillagerTradeEmiRecipe(entry, suffix));
            seenProfessions.add(entry.profession());
        }

        for (ResourceLocation profId : seenProfessions) {
            registry.addWorkstation(MercantileEmiCategories.VILLAGER_TRADES,
                    EmiStack.of(TradeIndexIcon.forProfession(profId)));
        }

        // Cover registered professions with no trades so they still surface in the workstation grid.
        for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
            ResourceLocation profId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            if (profId != null && seenProfessions.add(profId)) {
                registry.addWorkstation(MercantileEmiCategories.VILLAGER_TRADES,
                        EmiStack.of(TradeIndexIcon.forProfession(profId)));
            }
        }
    }
}
