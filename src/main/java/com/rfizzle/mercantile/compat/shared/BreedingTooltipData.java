package com.rfizzle.mercantile.compat.shared;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BreedingTooltipData {

    public static final String KEY_WILLING = "mercantile:breedWilling";
    public static final String KEY_COOLDOWN = "mercantile:breedCooldown";
    public static final String KEY_BABY_AGE = "mercantile:breedBabyAge";
    public static final String KEY_IS_BABY = "mercantile:breedIsBaby";
    public static final String KEY_HAS_BED = "mercantile:breedHasBed";
    public static final String KEY_FOOD_POINTS = "mercantile:breedFoodPoints";
    public static final String KEY_FOOD_COUNTS = "mercantile:breedFoodCounts";
    public static final String KEY_NOT_WILLING_REASON = "mercantile:breedNotWillingReason";
    public static final String KEY_PRESENT = "mercantile:breedPresent";

    public static final String REASON_COOLDOWN = "on_cooldown";
    public static final String REASON_NO_BED = "no_bed";
    public static final String REASON_NOT_ENOUGH_FOOD = "not_enough_food";

    public static final int WILLING_FOOD_THRESHOLD = 12;

    private static final Map<Item, Integer> FOOD_POINTS;
    static {
        Map<Item, Integer> m = new LinkedHashMap<>();
        m.put(Items.BREAD, 4);
        m.put(Items.CARROT, 1);
        m.put(Items.POTATO, 1);
        m.put(Items.BEETROOT, 1);
        FOOD_POINTS = m;
    }

    private BreedingTooltipData() {}

    public static Map<Item, Integer> foodPoints() {
        return FOOD_POINTS;
    }

    public static void write(CompoundTag tag, Villager villager) {
        tag.putBoolean(KEY_PRESENT, true);

        boolean baby = villager.isBaby();
        tag.putBoolean(KEY_IS_BABY, baby);

        int age = villager.getAge();
        if (baby) {
            tag.putInt(KEY_BABY_AGE, Math.max(0, -age));
            return;
        }

        int cooldown = Math.max(0, age);
        boolean hasBed = villager.getBrain().hasMemoryValue(MemoryModuleType.HOME);
        int foodPoints = computeFoodPoints(villager);
        boolean willing = cooldown == 0 && hasBed && foodPoints >= WILLING_FOOD_THRESHOLD;

        tag.putInt(KEY_COOLDOWN, cooldown);
        tag.putBoolean(KEY_HAS_BED, hasBed);
        tag.putInt(KEY_FOOD_POINTS, foodPoints);
        tag.putBoolean(KEY_WILLING, willing);

        CompoundTag counts = new CompoundTag();
        SimpleContainer inv = villager.getInventory();
        for (Item food : FOOD_POINTS.keySet()) {
            int total = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.is(food)) {
                    total += stack.getCount();
                }
            }
            if (total > 0) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(food);
                counts.putInt(id.toString(), total);
            }
        }
        tag.put(KEY_FOOD_COUNTS, counts);

        if (!willing) {
            String reason;
            if (cooldown > 0) {
                reason = REASON_COOLDOWN;
            } else if (!hasBed) {
                reason = REASON_NO_BED;
            } else {
                reason = REASON_NOT_ENOUGH_FOOD;
            }
            tag.putString(KEY_NOT_WILLING_REASON, reason);
        }
    }

    public static int computeFoodPoints(Villager villager) {
        int total = 0;
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            Integer pts = FOOD_POINTS.get(stack.getItem());
            if (pts != null) {
                total += pts * stack.getCount();
            }
        }
        return total;
    }
}
