package com.rfizzle.mercantile.compat.shared;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class StateIndicatorFormatter {

    public record IndicatorLine(ItemStack icon, Component label) {}

    private StateIndicatorFormatter() {}

    public static List<IndicatorLine> format(CompoundTag data) {
        List<IndicatorLine> lines = new ArrayList<>();
        if (!data.getBoolean(StateIndicatorData.KEY_PRESENT)) return lines;

        ListTag states = data.getList(StateIndicatorData.KEY_STATES, Tag.TAG_STRING);
        if (states.isEmpty()) return lines;

        for (int i = 0; i < states.size(); i++) {
            String state = states.getString(i);
            IndicatorLine line = lineFor(state, data);
            if (line != null) lines.add(line);
        }
        return lines;
    }

    private static IndicatorLine lineFor(String state, CompoundTag data) {
        return switch (state) {
            case StateIndicatorData.STATE_TRADING -> {
                String name = data.getString(StateIndicatorData.KEY_TRADING_PLAYER);
                Component label = Component.translatable(
                        "tooltip.mercantile.state.trading", name)
                        .withStyle(ChatFormatting.GOLD);
                yield new IndicatorLine(new ItemStack(Items.EMERALD), label);
            }
            case StateIndicatorData.STATE_PANICKING -> new IndicatorLine(
                    new ItemStack(Items.BELL),
                    Component.translatable("tooltip.mercantile.state.panicking")
                            .withStyle(ChatFormatting.RED));
            case StateIndicatorData.STATE_NEEDS_WORKSTATION -> {
                ItemStack icon = workstationIcon(data);
                yield new IndicatorLine(icon,
                        Component.translatable("tooltip.mercantile.state.needs_workstation")
                                .withStyle(ChatFormatting.YELLOW));
            }
            default -> null;
        };
    }

    private static ItemStack workstationIcon(CompoundTag data) {
        String id = data.getString(StateIndicatorData.KEY_WORKSTATION_ITEM);
        if (id.isEmpty()) return new ItemStack(Items.CRAFTING_TABLE);
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return new ItemStack(Items.CRAFTING_TABLE);
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == Items.AIR) return new ItemStack(Items.CRAFTING_TABLE);
        return new ItemStack(item);
    }
}
