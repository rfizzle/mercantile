package com.rfizzle.mercantile.compat.jei;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexCategoryKey;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexIcon;
import com.rfizzle.mercantile.trade.index.ProfessionWorkstations;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
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

@JeiPlugin
public class MercantileJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = Mercantile.id("trade_index");

    // The synced index and reputation captured at the start of a registration cycle
    // (registerCategories runs first). Every phase reuses this so the category set and the
    // recipe/catalyst sets are always computed from one consistent snapshot — a mid-cycle
    // TradeIndexS2CPayload can otherwise leave JEI with recipes for an unregistered category.
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
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        Snapshot current = Snapshot.capture();
        snapshot = current;
        for (TradeIndexCategoryKey key : current.keys()) {
            registration.addRecipeCategories(
                    new VillagerTradeJeiCategory(registration.getJeiHelpers().getGuiHelper(), key));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Snapshot current = snapshot();
        for (TradeIndexCategoryKey key : current.keys()) {
            // JEI does expose IRecipeManager#hideRecipes at runtime, but driving it live is
            // unsafe here: TradeIndexEntry is a record whose equality recurses into ItemStack,
            // which compares by identity in 1.21.1 — so hide/unhide matching would silently
            // break across an index re-sync. The available view is therefore baked from the
            // current reputation and refreshes on the next viewer reload; REI carries the
            // live filter via its per-display visibility predicate.
            List<TradeIndexEntry> recipes = new ArrayList<>();
            for (TradeIndexEntry entry : current.entries()) {
                if (key.accepts(entry, current.playerScore())) {
                    recipes.add(entry);
                }
            }
            registration.addRecipes(VillagerTradeJeiCategory.recipeType(key), recipes);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        Snapshot current = snapshot();
        List<TradeIndexEntry> entries = current.entries();
        List<RecipeType<TradeIndexEntry>> catalystTypes = new ArrayList<>();
        for (TradeIndexCategoryKey key : current.keys()) {
            // Catalysts point at the browseable listings; tier tabs are reached from the
            // category list rather than a workstation lookup.
            if (key.type() == TradeIndexCategoryKey.Type.ALL
                    || key.type() == TradeIndexCategoryKey.Type.AVAILABLE) {
                catalystTypes.add(VillagerTradeJeiCategory.recipeType(key));
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
        for (ResourceLocation profId : profIds) {
            for (RecipeType<TradeIndexEntry> type : catalystTypes) {
                registration.addRecipeCatalyst(TradeIndexIcon.forProfession(profId), type);
            }
        }
        for (Item item : workstationItems) {
            for (RecipeType<TradeIndexEntry> type : catalystTypes) {
                registration.addRecipeCatalyst(new ItemStack(item), type);
            }
        }
    }
}
