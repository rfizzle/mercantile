package com.rfizzle.mercantile.compat.jei;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexIcon;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexLabels;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class VillagerTradeJeiCategory implements IRecipeCategory<TradeIndexEntry> {

    public static final RecipeType<TradeIndexEntry> TYPE = new RecipeType<>(
            Mercantile.id("villager_trades"), TradeIndexEntry.class);

    private static final int WIDTH = 140;
    private static final int HEIGHT = 40;

    private final IDrawable background;
    private final IDrawable icon;

    public VillagerTradeJeiCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(TradeIndexIcon.categoryIcon());
    }

    @Override
    public RecipeType<TradeIndexEntry> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("category.mercantile.villager_trades");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, TradeIndexEntry entry, IFocusGroup focuses) {
        int x = 0;
        int y = 6;

        builder.addSlot(RecipeIngredientRole.CATALYST, x, y)
                .addItemStack(TradeIndexIcon.forProfession(entry.profession()))
                .addRichTooltipCallback((slot, tooltip) ->
                        tooltip.add(TradeIndexLabels.professionLabel(entry.profession())));
        x += 20;

        builder.addSlot(RecipeIngredientRole.INPUT, x, y).addItemStack(entry.inputA());
        x += 18;
        if (!entry.inputB().isEmpty()) {
            x += 8;
            builder.addSlot(RecipeIngredientRole.INPUT, x, y).addItemStack(entry.inputB());
            x += 18;
        }
        x += 24;

        builder.addSlot(RecipeIngredientRole.OUTPUT, x, y).addItemStack(entry.output());
    }

    @Override
    public void draw(TradeIndexEntry entry, IRecipeSlotsView slotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;

        Component professionLabel = TradeIndexLabels.professionLabel(entry.profession());
        Component levelLabel = entry.level() > 0
                ? TradeIndexLabels.levelLabel(entry.level())
                : TradeIndexLabels.tierLabel(entry.minScore().orElse(0));

        int textY = HEIGHT - 22;
        graphics.drawString(font,
                Component.translatable("mercantile.trade_index.tooltip.sold_by", professionLabel),
                0, textY, 0x404040, false);

        if (!levelLabel.getString().isEmpty()) {
            graphics.drawString(font, levelLabel, 0, textY + 10, 0x404040, false);
        }

        Component badge = TradeIndexLabels.sourceBadge(entry.source(), entry.minScore());
        if (!badge.getString().isEmpty()) {
            int badgeWidth = font.width(badge);
            graphics.drawString(font, badge, WIDTH - badgeWidth, textY + 10, 0xAA0000, false);
        }

        int x = 38;
        if (!entry.inputB().isEmpty()) {
            graphics.drawString(font, "+", x, 9, 0xFFFFFF, true);
        }

        int arrowX = x + (entry.inputB().isEmpty() ? 0 : 26);
        graphics.drawString(font, "→", arrowX, 9, 0xFFFFFF, true);
    }
}
