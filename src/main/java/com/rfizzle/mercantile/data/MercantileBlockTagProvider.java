package com.rfizzle.mercantile.data;

import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.BlockTags;

import java.util.concurrent.CompletableFuture;

/**
 * Mercantile's block tags: the Sentry Pylon is pickaxe-mineable and, matching its
 * {@code requiresCorrectToolForDrops()} properties, needs an iron tool to drop.
 */
public class MercantileBlockTagProvider extends FabricTagProvider.BlockTagProvider {

    public MercantileBlockTagProvider(FabricDataOutput output,
                                      CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider wrapperLookup) {
        getOrCreateTagBuilder(BlockTags.MINEABLE_WITH_PICKAXE).add(MercantileRegistry.SENTRY_PYLON);
        getOrCreateTagBuilder(BlockTags.NEEDS_IRON_TOOL).add(MercantileRegistry.SENTRY_PYLON);
    }
}
