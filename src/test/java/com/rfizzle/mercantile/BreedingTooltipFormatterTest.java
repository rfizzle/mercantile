package com.rfizzle.mercantile;

import com.rfizzle.mercantile.compat.BreedingTooltipData;
import com.rfizzle.mercantile.compat.BreedingTooltipFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BreedingTooltipFormatterTest {

    @Test
    void emptyTagProducesNoLines() {
        assertEquals(0, BreedingTooltipFormatter.format(new CompoundTag()).size());
    }

    @Test
    void babyProducesSingleLineWithGrowingStateAqua() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(BreedingTooltipData.KEY_PRESENT, true);
        tag.putBoolean(BreedingTooltipData.KEY_IS_BABY, true);
        tag.putInt(BreedingTooltipData.KEY_BABY_AGE, 12000);

        List<Component> lines = BreedingTooltipFormatter.format(tag);

        assertEquals(1, lines.size(), "baby: single breeding line, no food line");
        Component state = stateArg(lines.get(0));
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), state.getStyle().getColor());
        TranslatableContents stateContents = (TranslatableContents) state.getContents();
        assertEquals("tooltip.mercantile.breeding.state.growing", stateContents.getKey());
        assertEquals("10:00", stateContents.getArgs()[0]);
    }

    @Test
    void cooldownProducesSingleYellowLineNoFoodLine() {
        List<Component> lines = BreedingTooltipFormatter.format(adultTag(6000, true, 12));

        assertEquals(1, lines.size(), "cooldown: single breeding line, no food line");
        Component state = stateArg(lines.get(0));
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.YELLOW), state.getStyle().getColor());
        TranslatableContents stateContents = (TranslatableContents) state.getContents();
        assertEquals("tooltip.mercantile.breeding.state.cooldown", stateContents.getKey());
    }

    @Test
    void noBedProducesSingleRedLineNoFoodLine() {
        List<Component> lines = BreedingTooltipFormatter.format(adultTag(0, false, 0));

        assertEquals(1, lines.size(), "no bed: single breeding line, no food line");
        Component state = stateArg(lines.get(0));
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), state.getStyle().getColor());
        TranslatableContents stateContents = (TranslatableContents) state.getContents();
        assertEquals("tooltip.mercantile.breeding.state.needs_bed", stateContents.getKey());
    }

    @Test
    void hungryZeroFoodProducesTwoLinesWithRedFood() {
        List<Component> lines = BreedingTooltipFormatter.format(adultTag(0, true, 0));

        assertEquals(2, lines.size(), "hungry: breeding line + food progress line");
        Component state = stateArg(lines.get(0));
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), state.getStyle().getColor());
        TranslatableContents stateContents = (TranslatableContents) state.getContents();
        assertEquals("tooltip.mercantile.breeding.state.hungry", stateContents.getKey());
        // food line is red when 0 food
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), lines.get(1).getStyle().getColor());
        TranslatableContents foodContents = (TranslatableContents) lines.get(1).getContents();
        assertEquals("tooltip.mercantile.breeding.food_progress", foodContents.getKey());
    }

    @Test
    void hungryPartialFoodProducesTwoLinesWithYellowFood() {
        List<Component> lines = BreedingTooltipFormatter.format(adultTag(0, true, 4));

        assertEquals(2, lines.size());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.YELLOW), lines.get(1).getStyle().getColor());
    }

    @Test
    void readyProducesSingleGreenLine() {
        List<Component> lines = BreedingTooltipFormatter.format(adultTag(0, true, 12));

        assertEquals(1, lines.size(), "ready: single breeding line, no food line");
        Component state = stateArg(lines.get(0));
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), state.getStyle().getColor());
        TranslatableContents stateContents = (TranslatableContents) state.getContents();
        assertEquals("tooltip.mercantile.breeding.state.ready", stateContents.getKey());
    }

    @Test
    void breedingLineLabelIsGray() {
        List<Component> lines = BreedingTooltipFormatter.format(adultTag(0, true, 12));

        assertEquals(1, lines.size());
        Component line = lines.get(0);
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY), line.getStyle().getColor());
        TranslatableContents contents = (TranslatableContents) line.getContents();
        assertEquals("tooltip.mercantile.breeding.label", contents.getKey());
    }

    @Test
    void cooldownTakesPrecedenceOverNoBed() {
        List<Component> lines = BreedingTooltipFormatter.format(adultTag(1000, false, 0));

        assertEquals(1, lines.size());
        Component state = stateArg(lines.get(0));
        TranslatableContents stateContents = (TranslatableContents) state.getContents();
        assertEquals("tooltip.mercantile.breeding.state.cooldown", stateContents.getKey());
    }

    // --- helpers ---

    private static CompoundTag adultTag(int cooldown, boolean hasBed, int foodPoints) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(BreedingTooltipData.KEY_PRESENT, true);
        tag.putBoolean(BreedingTooltipData.KEY_IS_BABY, false);
        tag.putInt(BreedingTooltipData.KEY_COOLDOWN, cooldown);
        tag.putBoolean(BreedingTooltipData.KEY_HAS_BED, hasBed);
        tag.putInt(BreedingTooltipData.KEY_FOOD_POINTS, foodPoints);
        tag.putBoolean(BreedingTooltipData.KEY_WILLING,
                cooldown == 0 && hasBed && foodPoints >= BreedingTooltipData.WILLING_FOOD_THRESHOLD);
        return tag;
    }

    private static Component stateArg(Component breedingLine) {
        TranslatableContents contents = (TranslatableContents) breedingLine.getContents();
        assertEquals("tooltip.mercantile.breeding.label", contents.getKey(),
                "outer component must be the breeding label");
        return (Component) contents.getArgs()[0];
    }
}
