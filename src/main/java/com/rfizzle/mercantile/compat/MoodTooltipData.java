package com.rfizzle.mercantile.compat;

import com.rfizzle.mercantile.mood.MoodManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.Villager;

/** Server-side writer for the mood tooltip line, shared by the Jade and WTHIT data providers. */
public final class MoodTooltipData {

    public static final String KEY_PRESENT = "mercantile:moodPresent";
    public static final String KEY_TIER = "mercantile:moodTier";

    private MoodTooltipData() {}

    public static void write(CompoundTag tag, Villager villager) {
        tag.putBoolean(KEY_PRESENT, true);
        tag.putString(KEY_TIER, MoodManager.tier(villager).translationKey());
    }
}
