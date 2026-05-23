package com.rfizzle.mercantile.compat.rei;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VillagerTradeDisplay extends BasicDisplay {

    public static final CategoryIdentifier<VillagerTradeDisplay> IDENTIFIER =
            CategoryIdentifier.of(Mercantile.id("villager_trades"));

    private final TradeIndexEntry entry;

    public VillagerTradeDisplay(TradeIndexEntry entry) {
        super(buildInputs(entry), buildOutputs(entry), Optional.empty());
        this.entry = entry;
    }

    public TradeIndexEntry entry() {
        return entry;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return IDENTIFIER;
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
