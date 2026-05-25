package com.rfizzle.mercantile.compat.emi;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexIcon;
import com.rfizzle.mercantile.compat.tradeindex.TradeIndexLabels;
import com.rfizzle.mercantile.trade.index.TradeIndexEntry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class VillagerTradeEmiRecipe implements EmiRecipe {

    private static final int WIDTH = 158;
    private static final int HEIGHT = 50;

    private final TradeIndexEntry entry;
    private final ResourceLocation id;
    private final List<EmiIngredient> inputs;
    private final List<EmiStack> outputs;

    public VillagerTradeEmiRecipe(TradeIndexEntry entry, int suffix) {
        this.entry = entry;
        this.id = Mercantile.id("trade/" + signature(entry, suffix));

        List<EmiIngredient> ins = new ArrayList<>(2);
        ins.add(EmiStack.of(entry.inputA()));
        if (!entry.inputB().isEmpty()) {
            ins.add(EmiStack.of(entry.inputB()));
        }
        this.inputs = List.copyOf(ins);
        this.outputs = List.of(EmiStack.of(entry.output()));
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return MercantileEmiCategories.VILLAGER_TRADES;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return inputs;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public int getDisplayWidth() {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        Component professionLabel = TradeIndexLabels.professionLabel(entry.profession());
        Component levelLabel = entry.level() > 0
                ? TradeIndexLabels.levelLabel(entry.level())
                : TradeIndexLabels.tierLabel(entry.minScore().orElse(0));

        int y = 6;
        int x = 0;

        widgets.addSlot(EmiStack.of(TradeIndexIcon.forProfession(entry.profession())), x, y)
                .drawBack(false)
                .catalyst(true)
                .appendTooltip(professionLabel);
        x += 20;

        ItemStack workstation = entry.workstation();
        if (!workstation.isEmpty()) {
            widgets.addSlot(EmiStack.of(workstation), x, y)
                    .drawBack(false)
                    .catalyst(true)
                    .appendTooltip(workstation.getHoverName());
            x += 20;
        }

        widgets.addSlot(inputs.get(0), x, y);
        x += 20;

        if (inputs.size() > 1) {
            widgets.addText(Component.literal("+"), x + 2, y + 5, 0xFFFFFF, true);
            x += 8;
            widgets.addSlot(inputs.get(1), x, y);
            x += 20;
        }

        widgets.addFillingArrow(x, y + 1, 1000);
        x += 24;

        widgets.addSlot(outputs.get(0), x, y).recipeContext(this);

        widgets.addText(levelLabel.getVisualOrderText(), 0, y + 22, 0x404040, false);

        if (entry.minScore().isPresent()) {
            Component repLabel = Component.translatable("mercantile.trade_index.requires_reputation",
                    TradeIndexLabels.tierLabel(entry.minScore().getAsInt()));
            widgets.addText(repLabel.getVisualOrderText(), 0, y + 33, 0x404040, false);
        }
    }

    private static String signature(TradeIndexEntry e, int suffix) {
        String inputA = idOf(e.inputA());
        String inputB = idOf(e.inputB());
        String output = idOf(e.output());
        return e.profession().getNamespace() + "_" + e.profession().getPath()
                + "_l" + e.level()
                + "_" + e.source().name().toLowerCase()
                + "_" + sanitize(inputA) + "x" + e.inputA().getCount()
                + "_" + sanitize(inputB) + "x" + e.inputB().getCount()
                + "_" + sanitize(output) + "x" + e.output().getCount()
                + "_s" + e.minScore().orElse(Integer.MIN_VALUE)
                + "_" + suffix;
    }

    private static String idOf(ItemStack stack) {
        if (stack.isEmpty()) return "empty";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String sanitize(String s) {
        return s.replace(':', '_').replace('/', '_');
    }
}
