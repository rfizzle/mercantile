package com.rfizzle.mercantile.data;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Mercantile's {@code fabric-datagen} entrypoint — the first of the four datagen anchors
 * (the loom {@code datagen} run, the {@code make run-datagen} target, and
 * {@code verifyDatagenIdempotent} are the other three, and they only mean anything as a set;
 * see the {@code mc-datagen} skill).
 *
 * <p>Everything registered here writes into {@code src/main/generated}, which {@code build.gradle}
 * declares as a {@code main} resources source dir — so the output ships in the jar and lands on
 * the test classpath exactly the way {@code src/main/resources} does.
 *
 * <p>Only the Sentry Pylon's vanilla-shaped data is generated: its crafting recipe (and the recipe
 * advancement the recipe provider emits beside it), its block loot table, and its two vanilla
 * block-tag entries. Everything else under {@code data/mercantile} — contracts, exclusive trades,
 * gift mappings, gratitude gifts, villager names, and the hand-designed advancement tree with its
 * custom criteria — is Mercantile's own datapack format with no vanilla provider, and stays
 * hand-authored under {@code src/main/resources}.
 */
public class MercantileDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        pack.addProvider(MercantileRecipeProvider::new);
        pack.addProvider(MercantileBlockLootTableProvider::new);
        pack.addProvider(MercantileBlockTagProvider::new);
    }
}
