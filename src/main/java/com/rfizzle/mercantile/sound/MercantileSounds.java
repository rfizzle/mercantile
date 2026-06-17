package com.rfizzle.mercantile.sound;

import com.rfizzle.mercantile.Mercantile;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public final class MercantileSounds {

    // Cold synthetic klaxon emitted at the pylon when it rings a bell, so the
    // villager glow broadcast (96-block range) has an audible companion a distant
    // player can localize — the vanilla bell only carries ~16-32 blocks.
    public static final SoundEvent SENTRY_PYLON_ALARM = register("block.sentry_pylon.alarm");

    public static void init() {
        // Referencing this class triggers the static initializers above, which
        // register the events. Called from Mercantile#onInitialize.
    }

    private static SoundEvent register(String path) {
        ResourceLocation id = Mercantile.id(path);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    private MercantileSounds() {
    }
}
