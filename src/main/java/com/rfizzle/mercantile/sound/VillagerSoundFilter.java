package com.rfizzle.mercantile.sound;

import net.minecraft.resources.ResourceLocation;

public final class VillagerSoundFilter {
    private static final String VANILLA_NAMESPACE = "minecraft";
    private static final String VILLAGER_PATH_PREFIX = "entity.villager.";

    private VillagerSoundFilter() {}

    public static boolean isVillagerSound(ResourceLocation loc) {
        return loc != null
                && VANILLA_NAMESPACE.equals(loc.getNamespace())
                && loc.getPath().startsWith(VILLAGER_PATH_PREFIX);
    }

    public static float scaleVolume(float baseVolume, ResourceLocation loc, float configFactor) {
        return isVillagerSound(loc) ? baseVolume * configFactor : baseVolume;
    }
}
