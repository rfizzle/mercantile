package com.rfizzle.mercantile.compat;

import com.rfizzle.mercantile.compat.shared.MoodTooltipData;
import com.rfizzle.mercantile.compat.shared.MoodTooltipFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MoodTooltipFormatterTest {

    private static CompoundTag tagFor(String tierKey) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MoodTooltipData.KEY_PRESENT, true);
        tag.putString(MoodTooltipData.KEY_TIER, tierKey);
        return tag;
    }

    @Test
    void emptyTagProducesNoLines() {
        assertTrue(MoodTooltipFormatter.format(new CompoundTag()).isEmpty());
    }

    @Test
    void missingTierProducesNoLines() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MoodTooltipData.KEY_PRESENT, true);
        assertTrue(MoodTooltipFormatter.format(tag).isEmpty());
    }

    @Test
    void producesSingleMoodLine() {
        List<Component> lines = MoodTooltipFormatter.format(tagFor("mercantile.mood.happy"));
        assertEquals(1, lines.size());
        TranslatableContents contents = (TranslatableContents) lines.get(0).getContents();
        assertEquals("tooltip.mercantile.mood.label", contents.getKey());
    }

    @Test
    void tierNameCarriesTierColor() {
        List<Component> lines = MoodTooltipFormatter.format(tagFor("mercantile.mood.miserable"));
        TranslatableContents contents = (TranslatableContents) lines.get(0).getContents();
        Component tierName = (Component) contents.getArgs()[0];
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), tierName.getStyle().getColor());
    }

    @Test
    void colorMapping() {
        assertEquals(ChatFormatting.GREEN, MoodTooltipFormatter.colorForTier("mercantile.mood.happy"));
        assertEquals(ChatFormatting.WHITE, MoodTooltipFormatter.colorForTier("mercantile.mood.content"));
        assertEquals(ChatFormatting.YELLOW, MoodTooltipFormatter.colorForTier("mercantile.mood.unhappy"));
        assertEquals(ChatFormatting.RED, MoodTooltipFormatter.colorForTier("mercantile.mood.miserable"));
        assertEquals(ChatFormatting.WHITE, MoodTooltipFormatter.colorForTier("unknown"));
    }
}
