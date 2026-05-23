package com.rfizzle.mercantile.compat;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

public final class BreedingTooltipFormatter {

    private BreedingTooltipFormatter() {}

    public static List<Component> format(CompoundTag data) {
        List<Component> lines = new ArrayList<>();
        if (!data.getBoolean(BreedingTooltipData.KEY_PRESENT)) return lines;

        if (data.getBoolean(BreedingTooltipData.KEY_IS_BABY)) {
            int babyAge = data.getInt(BreedingTooltipData.KEY_BABY_AGE);
            lines.add(Component.translatable(
                    "tooltip.mercantile.breeding.baby_growup",
                    formatTicks(babyAge)).withStyle(ChatFormatting.AQUA));
            return lines;
        }

        boolean willing = data.getBoolean(BreedingTooltipData.KEY_WILLING);
        int cooldown = data.getInt(BreedingTooltipData.KEY_COOLDOWN);

        if (willing) {
            lines.add(Component.translatable("tooltip.mercantile.breeding.willing")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            lines.add(Component.translatable("tooltip.mercantile.breeding.not_willing")
                    .withStyle(ChatFormatting.GRAY));
            String reason = data.getString(BreedingTooltipData.KEY_NOT_WILLING_REASON);
            if (!reason.isEmpty()) {
                lines.add(Component.translatable("tooltip.mercantile.breeding.reason." + reason)
                        .withStyle(ChatFormatting.RED));
            }
        }

        if (cooldown > 0) {
            lines.add(Component.translatable(
                    "tooltip.mercantile.breeding.cooldown",
                    formatTicks(cooldown)).withStyle(ChatFormatting.YELLOW));
        }

        CompoundTag counts = data.getCompound(BreedingTooltipData.KEY_FOOD_COUNTS);
        lines.add(Component.translatable("tooltip.mercantile.breeding.food_header"));
        if (counts.isEmpty()) {
            lines.add(Component.translatable("tooltip.mercantile.breeding.food_none")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            for (String key : counts.getAllKeys()) {
                int count = counts.getInt(key);
                Component itemName = itemDisplayName(key);
                lines.add(Component.translatable(
                        "tooltip.mercantile.breeding.food_line", count, itemName));
            }
        }

        return lines;
    }

    public static String formatTicks(int ticks) {
        int totalSeconds = Math.max(0, ticks) / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private static Component itemDisplayName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return Component.literal(id);
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        return Component.translatable(item.getDescriptionId());
    }
}
