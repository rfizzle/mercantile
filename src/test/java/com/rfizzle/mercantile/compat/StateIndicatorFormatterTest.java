package com.rfizzle.mercantile.compat;

import com.rfizzle.mercantile.compat.shared.StateIndicatorData;
import com.rfizzle.mercantile.compat.shared.StateIndicatorFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StateIndicatorFormatterTest {

    private static CompoundTag tagFor(String... states) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(StateIndicatorData.KEY_PRESENT, true);
        ListTag list = new ListTag();
        for (String state : states) {
            list.add(StringTag.valueOf(state));
        }
        tag.put(StateIndicatorData.KEY_STATES, list);
        return tag;
    }

    @Test
    void absentTagProducesNoLines() {
        assertTrue(StateIndicatorFormatter.format(new CompoundTag()).isEmpty());
    }

    @Test
    void emptyStateListProducesNoLines() {
        assertTrue(StateIndicatorFormatter.format(tagFor()).isEmpty());
    }

    @Test
    void unknownStateIsSkipped() {
        assertTrue(StateIndicatorFormatter.format(tagFor("not_a_real_state")).isEmpty());
    }

    @Test
    void professionLockedProducesGrayIronLine() {
        List<StateIndicatorFormatter.IndicatorLine> lines =
                StateIndicatorFormatter.format(tagFor(StateIndicatorData.STATE_PROFESSION_LOCKED));

        assertEquals(1, lines.size());
        StateIndicatorFormatter.IndicatorLine line = lines.get(0);
        assertTrue(line.icon().is(Items.IRON_INGOT));

        TranslatableContents contents = (TranslatableContents) line.label().getContents();
        assertEquals("tooltip.mercantile.state.profession_locked", contents.getKey());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY), line.label().getStyle().getColor());
    }

    @Test
    void statesRenderInListOrder() {
        List<StateIndicatorFormatter.IndicatorLine> lines = StateIndicatorFormatter.format(
                tagFor(StateIndicatorData.STATE_PANICKING, StateIndicatorData.STATE_PROFESSION_LOCKED));

        assertEquals(2, lines.size());
        TranslatableContents first = (TranslatableContents) lines.get(0).label().getContents();
        TranslatableContents second = (TranslatableContents) lines.get(1).label().getContents();
        assertEquals("tooltip.mercantile.state.panicking", first.getKey());
        assertEquals("tooltip.mercantile.state.profession_locked", second.getKey());
    }
}
