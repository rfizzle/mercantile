package com.rfizzle.mercantile.compat.jei;

import com.rfizzle.mercantile.compat.tradeindex.TradeIndexCategoryKey;
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

    private static final int WIDTH = 160;
    private static final int HEIGHT = 60;

    private final TradeIndexCategoryKey key;
    private final RecipeType<TradeIndexEntry> type;
    private final IDrawable background;
    private final IDrawable icon;

    public VillagerTradeJeiCategory(IGuiHelper guiHelper, TradeIndexCategoryKey key) {
        this.key = key;
        this.type = recipeType(key);
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(TradeIndexIcon.categoryIcon());
    }

    /** The JEI recipe type for a shared category key. {@code RecipeType} has value equality. */
    public static RecipeType<TradeIndexEntry> recipeType(TradeIndexCategoryKey key) {
        return new RecipeType<>(key.id(), TradeIndexEntry.class);
    }

    @Override
    public RecipeType<TradeIndexEntry> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return key.title();
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

        if (!entry.workstation().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, x, y)
                    .addItemStack(entry.workstation())
                    .addRichTooltipCallback((slot, tooltip) ->
                            tooltip.add(entry.workstation().getHoverName()));
            x += 20;
        }

        builder.addSlot(RecipeIngredientRole.INPUT, x, y).addItemStack(entry.inputA());
        x += 20;
        if (!entry.inputB().isEmpty()) {
            x += 8;
            builder.addSlot(RecipeIngredientRole.INPUT, x, y).addItemStack(entry.inputB());
            x += 20;
        }
        x += 24;

        builder.addSlot(RecipeIngredientRole.OUTPUT, x, y).addItemStack(entry.output());
    }

    @Override
    public void draw(TradeIndexEntry entry, IRecipeSlotsView slotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;

        Component levelLabel = entry.level() > 0
                ? TradeIndexLabels.levelLabel(entry.level())
                : TradeIndexLabels.tierLabel(entry.minScore().orElse(0));
        boolean hasWorkstation = !entry.workstation().isEmpty();

        graphics.drawString(font, levelLabel, 0, 28, 0x404040, false);

        if (entry.minScore().isPresent()) {
            Component repLabel = Component.translatable("mercantile.trade_index.requires_reputation",
                    TradeIndexLabels.tierLabel(entry.minScore().getAsInt()));
            graphics.drawString(font, repLabel, 0, 39, 0x404040, false);
        }

        int slotOffset = hasWorkstation ? 20 : 0;
        int x = 40 + slotOffset;
        if (!entry.inputB().isEmpty()) {
            graphics.drawString(font, "+", x, 9, 0xFFFFFF, true);
        }

        int arrowX = x + (entry.inputB().isEmpty() ? 0 : 28);
        graphics.drawString(font, "→", arrowX, 9, 0xFFFFFF, true);
    }
}
