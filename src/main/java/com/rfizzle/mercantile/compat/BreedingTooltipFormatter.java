package com.rfizzle.mercantile.compat;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

public final class BreedingTooltipFormatter {

    private BreedingTooltipFormatter() {}

    public static List<Component> format(CompoundTag data) {
        List<Component> lines = new ArrayList<>();
        if (!data.getBoolean(BreedingTooltipData.KEY_PRESENT)) return lines;

        if (data.getBoolean(BreedingTooltipData.KEY_IS_BABY)) {
            int babyAge = data.getInt(BreedingTooltipData.KEY_BABY_AGE);
            lines.add(breedingLine(
                    Component.translatable("tooltip.mercantile.breeding.state.growing", formatTicks(babyAge))
                            .withStyle(ChatFormatting.AQUA)));
            return lines;
        }

        int cooldown = data.getInt(BreedingTooltipData.KEY_COOLDOWN);
        boolean hasBed = data.getBoolean(BreedingTooltipData.KEY_HAS_BED);
        int foodPoints = data.getInt(BreedingTooltipData.KEY_FOOD_POINTS);
        int threshold = BreedingTooltipData.WILLING_FOOD_THRESHOLD;

        if (cooldown > 0) {
            lines.add(breedingLine(
                    Component.translatable("tooltip.mercantile.breeding.state.cooldown", formatTicks(cooldown))
                            .withStyle(ChatFormatting.YELLOW)));
            return lines;
        }

        if (!hasBed) {
            lines.add(breedingLine(
                    Component.translatable("tooltip.mercantile.breeding.state.needs_bed")
                            .withStyle(ChatFormatting.RED)));
            return lines;
        }

        if (foodPoints < threshold) {
            lines.add(breedingLine(
                    Component.translatable("tooltip.mercantile.breeding.state.hungry")
                            .withStyle(ChatFormatting.RED)));
            ChatFormatting foodColor = foodPoints == 0 ? ChatFormatting.RED : ChatFormatting.YELLOW;
            lines.add(Component.translatable("tooltip.mercantile.breeding.food_progress", foodPoints, threshold)
                    .withStyle(foodColor));
            return lines;
        }

        lines.add(breedingLine(
                Component.translatable("tooltip.mercantile.breeding.state.ready")
                        .withStyle(ChatFormatting.GREEN)));
        return lines;
    }

    public static String formatTicks(int ticks) {
        int totalSeconds = Math.max(0, ticks) / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private static MutableComponent breedingLine(Component stateComponent) {
        return Component.translatable("tooltip.mercantile.breeding.label", stateComponent)
                .withStyle(ChatFormatting.GRAY);
    }
}
