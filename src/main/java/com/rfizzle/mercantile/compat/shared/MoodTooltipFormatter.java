package com.rfizzle.mercantile.compat.shared;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Client-side formatter for the mood tooltip line, shared by the Jade and WTHIT component providers. */
public final class MoodTooltipFormatter {

    private MoodTooltipFormatter() {}

    public static List<Component> format(CompoundTag data) {
        List<Component> lines = new ArrayList<>();
        if (!data.getBoolean(MoodTooltipData.KEY_PRESENT)) return lines;

        String tierKey = data.getString(MoodTooltipData.KEY_TIER);
        if (tierKey.isEmpty()) return lines;

        Component tierName = Component.translatable(tierKey).withStyle(colorForTier(tierKey));
        lines.add(Component.translatable("tooltip.mercantile.mood.label", tierName)
                .withStyle(ChatFormatting.GRAY));
        return lines;
    }

    public static ChatFormatting colorForTier(String tierKey) {
        return switch (tierKey) {
            case "tooltip.mercantile.mood.happy" -> ChatFormatting.GREEN;
            case "tooltip.mercantile.mood.unhappy" -> ChatFormatting.YELLOW;
            case "tooltip.mercantile.mood.miserable" -> ChatFormatting.RED;
            default -> ChatFormatting.WHITE;
        };
    }
}
