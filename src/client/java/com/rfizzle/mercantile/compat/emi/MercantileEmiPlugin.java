package com.rfizzle.mercantile.compat.emi;

import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexCategoryKey;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexIcon;
import com.rfizzle.mercantile.trade.index.ProfessionWorkstations;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MercantileEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        List<TradeIndexEntry> entries = ClientMercantileData.getTradeIndex();
        int playerScore = ClientMercantileData.getReputationScore();
        List<TradeIndexCategoryKey> keys = TradeIndexCategoryKey.forSnapshot(entries);

        Map<TradeIndexCategoryKey, EmiRecipeCategory> categories = new LinkedHashMap<>();
        // Workstation catalysts go on the browseable listings (comprehensive + available);
        // the tier tabs are reached from the category strip, not from a bench lookup.
        List<EmiRecipeCategory> workstationCategories = new ArrayList<>();
        for (TradeIndexCategoryKey key : keys) {
            EmiRecipeCategory category = MercantileEmiCategories.create(key);
            categories.put(key, category);
            registry.addCategory(category);
            if (key.type() == TradeIndexCategoryKey.Type.ALL
                    || key.type() == TradeIndexCategoryKey.Type.AVAILABLE) {
                workstationCategories.add(category);
            }
        }

        Set<ResourceLocation> seenProfessions = new HashSet<>();
        Set<Item> seenWorkstations = new LinkedHashSet<>();
        Map<String, Integer> dupCounts = new HashMap<>();

        for (TradeIndexEntry entry : entries) {
            for (Map.Entry<TradeIndexCategoryKey, EmiRecipeCategory> category : categories.entrySet()) {
                // EMI has no runtime recipe-visibility hook, so the available view is baked
                // from the current reputation here and refreshes on the next viewer reload.
                if (!category.getKey().accepts(entry, playerScore)) {
                    continue;
                }
                String dupKey = category.getKey().path() + "|" + entry.profession()
                        + "|" + entry.level() + "|" + entry.source();
                int suffix = dupCounts.merge(dupKey, 0, (oldV, ignored) -> oldV + 1);
                registry.addRecipe(new VillagerTradeEmiRecipe(category.getValue(), entry, suffix));
            }
            seenProfessions.add(entry.profession());
            if (!entry.workstation().isEmpty()) {
                seenWorkstations.add(entry.workstation().getItem());
            }
        }

        for (ResourceLocation profId : seenProfessions) {
            addWorkstation(registry, workstationCategories,
                    EmiStack.of(TradeIndexIcon.forProfession(profId)));
        }

        // Cover registered professions with no trades so they still surface in the workstation grid.
        for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
            ResourceLocation profId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            if (profId == null) continue;
            if (seenProfessions.add(profId)) {
                addWorkstation(registry, workstationCategories,
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
            addWorkstation(registry, workstationCategories, EmiStack.of(new ItemStack(item)));
        }
    }

    private static void addWorkstation(EmiRegistry registry, List<EmiRecipeCategory> categories,
                                       EmiStack stack) {
        for (EmiRecipeCategory category : categories) {
            registry.addWorkstation(category, stack);
        }
    }
}
