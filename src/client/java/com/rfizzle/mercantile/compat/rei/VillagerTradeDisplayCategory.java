package com.rfizzle.mercantile.compat.rei;

import com.rfizzle.mercantile.compat.tradeindex.TradeIndexIcon;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexLabels;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class VillagerTradeDisplayCategory implements DisplayCategory<VillagerTradeDisplay> {

    @Override
    public CategoryIdentifier<? extends VillagerTradeDisplay> getCategoryIdentifier() {
        return VillagerTradeDisplay.IDENTIFIER;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("category.mercantile.villager_trades");
    }

    @Override
    public Renderer getIcon() {
        return EntryStacks.of(TradeIndexIcon.categoryIcon());
    }

    @Override
    public int getDisplayHeight() {
        return 60;
    }

    @Override
    public List<Widget> setupDisplay(VillagerTradeDisplay display, Rectangle bounds) {
        TradeIndexEntry entry = display.entry();
        ItemStack workstation = entry.workstation();
        boolean hasWorkstation = !workstation.isEmpty();
        Component professionLabel = TradeIndexLabels.professionLabel(entry.profession());
        Component levelLabel = entry.level() > 0
                ? TradeIndexLabels.levelLabel(entry.level())
                : TradeIndexLabels.tierLabel(entry.minScore().orElse(0));
        Component badge = TradeIndexLabels.sourceBadge(entry.source(), entry.minScore());

        int startOffset = hasWorkstation ? 70 : 60;
        Point start = new Point(bounds.getCenterX() - startOffset, bounds.getCenterY() - 18);
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));

        int x = start.x;
        int y = start.y;

        widgets.add(Widgets.withTooltip(
                Widgets.createSlot(new Point(x, y))
                        .entry(EntryStacks.of(TradeIndexIcon.forProfession(entry.profession())))
                        .disableBackground()
                        .notInteractable(),
                professionLabel));
        x += 22;

        if (hasWorkstation) {
            widgets.add(Widgets.withTooltip(
                    Widgets.createSlot(new Point(x, y))
                            .entry(EntryStacks.of(workstation))
                            .disableBackground()
                            .notInteractable(),
                    workstation.getHoverName()));
            x += 20;
        }

        widgets.add(Widgets.createSlot(new Point(x, y))
                .entries(display.getInputEntries().get(0))
                .markInput());
        x += 20;

        if (display.getInputEntries().size() > 1) {
            widgets.add(Widgets.createLabel(new Point(x + 3, y + 5), Component.literal("+")));
            x += 10;
            widgets.add(Widgets.createSlot(new Point(x, y))
                    .entries(display.getInputEntries().get(1))
                    .markInput());
            x += 20;
        }

        widgets.add(Widgets.createArrow(new Point(x, y)));
        x += 24;

        widgets.add(Widgets.createSlot(new Point(x, y))
                .entries(display.getOutputEntries().get(0))
                .markOutput());
        x += 22;

        widgets.add(Widgets.createLabel(new Point(x, y + 5), levelLabel).leftAligned());

        Component header = Component.translatable(
                "mercantile.trade_index.tooltip.sold_by", professionLabel);
        widgets.add(Widgets.createLabel(
                new Point(bounds.getCenterX(), bounds.getMaxY() - 12), header).centered());

        if (!badge.getString().isEmpty()) {
            widgets.add(Widgets.createLabel(
                    new Point(bounds.getCenterX(), bounds.getMaxY() - 22), badge).centered());
        }

        if (hasWorkstation) {
            Component workstationLine = Component.translatable(
                    "mercantile.trade_index.tooltip.workstation", workstation.getHoverName());
            widgets.add(Widgets.createLabel(
                    new Point(bounds.getCenterX(), bounds.getMaxY() - 32), workstationLine).centered());
        }

        return widgets;
    }
}
