package com.rfizzle.mercantile.compat.rei;

import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexCategoryKey;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexFilter;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexIcon;
import com.rfizzle.mercantile.trade.index.ProfessionWorkstations;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import dev.architectury.event.EventResult;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
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

    // Captured when registerCategories runs (REI's first phase) and reused by registerDisplays,
    // so the category set and the display set always come from one consistent snapshot — a
    // mid-cycle index sync can otherwise leave a display pointing at an unregistered category.
    private Snapshot snapshot;

    private record Snapshot(List<TradeIndexEntry> entries, int playerScore,
                            List<TradeIndexCategoryKey> keys) {
        static Snapshot capture() {
            List<TradeIndexEntry> entries = ClientMercantileData.getTradeIndex();
            return new Snapshot(entries, ClientMercantileData.getReputationScore(),
                    TradeIndexCategoryKey.forSnapshot(entries));
        }
    }

    private Snapshot snapshot() {
        Snapshot current = snapshot;
        return current != null ? current : Snapshot.capture();
    }

    @Override
    public void registerCategories(CategoryRegistry registry) {
        Snapshot current = Snapshot.capture();
        snapshot = current;
        List<TradeIndexEntry> entries = current.entries();
        List<TradeIndexCategoryKey> keys = current.keys();

        List<CategoryIdentifier<VillagerTradeDisplay>> workstationCategories = new ArrayList<>();
        for (TradeIndexCategoryKey key : keys) {
            registry.add(new VillagerTradeDisplayCategory(key));
            if (key.type() == TradeIndexCategoryKey.Type.ALL
                    || key.type() == TradeIndexCategoryKey.Type.AVAILABLE) {
                workstationCategories.add(VillagerTradeDisplay.categoryIdentifier(key));
            }
        }

        Set<ResourceLocation> profIds = new HashSet<>();
        Set<Item> workstationItems = new LinkedHashSet<>();
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
        EntryIngredient[] workstationArray = workstations.toArray(new EntryIngredient[0]);
        for (CategoryIdentifier<VillagerTradeDisplay> category : workstationCategories) {
            registry.addWorkstations(category, workstationArray);
        }
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        Snapshot current = snapshot();
        List<TradeIndexEntry> entries = current.entries();
        int playerScore = current.playerScore();
        List<TradeIndexCategoryKey> keys = current.keys();

        for (TradeIndexEntry entry : entries) {
            for (TradeIndexCategoryKey key : keys) {
                // The available view registers every trade and lets the visibility predicate
                // below hide locked ones — so a trade appears the moment reputation unlocks it,
                // without re-registering. Other categories filter their membership up front.
                boolean register = key.type() == TradeIndexCategoryKey.Type.AVAILABLE
                        || key.accepts(entry, playerScore);
                if (register) {
                    registry.add(new VillagerTradeDisplay(key, entry));
                }
            }
        }

        // Live filter: hide trades in the available view whose gate the player no longer meets.
        // REI re-evaluates this each time the display list is built, so it tracks the synced
        // reputation as it changes.
        registry.registerVisibilityPredicate((category, display) -> {
            if (display instanceof VillagerTradeDisplay trade
                    && trade.categoryKey().type() == TradeIndexCategoryKey.Type.AVAILABLE
                    && !TradeIndexFilter.isUnlocked(trade.entry(), ClientMercantileData.getReputationScore())) {
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });
    }
}
