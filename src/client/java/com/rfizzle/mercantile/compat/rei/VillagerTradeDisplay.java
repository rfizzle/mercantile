package com.rfizzle.mercantile.compat.rei;

import com.rfizzle.mercantile.compat.tradeindex.TradeIndexCategoryKey;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VillagerTradeDisplay extends BasicDisplay {

    private final TradeIndexEntry entry;
    private final TradeIndexCategoryKey categoryKey;
    private final CategoryIdentifier<VillagerTradeDisplay> categoryId;

    public VillagerTradeDisplay(TradeIndexCategoryKey categoryKey, TradeIndexEntry entry) {
        super(buildInputs(entry), buildOutputs(entry), Optional.empty());
        this.categoryKey = categoryKey;
        this.entry = entry;
        this.categoryId = categoryIdentifier(categoryKey);
    }

    /** The REI category identifier for a shared category key. */
    public static CategoryIdentifier<VillagerTradeDisplay> categoryIdentifier(TradeIndexCategoryKey key) {
        return CategoryIdentifier.of(key.id());
    }

    public TradeIndexEntry entry() {
        return entry;
    }

    public TradeIndexCategoryKey categoryKey() {
        return categoryKey;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return categoryId;
    }

    private static List<EntryIngredient> buildInputs(TradeIndexEntry entry) {
        List<EntryIngredient> list = new ArrayList<>(2);
        list.add(EntryIngredient.of(EntryStacks.of(entry.inputA())));
        if (!entry.inputB().isEmpty()) {
            list.add(EntryIngredient.of(EntryStacks.of(entry.inputB())));
        }
        return list;
    }

    private static List<EntryIngredient> buildOutputs(TradeIndexEntry entry) {
        return List.of(EntryIngredient.of(EntryStacks.of(entry.output())));
    }
}
