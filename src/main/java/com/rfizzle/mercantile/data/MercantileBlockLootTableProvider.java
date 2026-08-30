package com.rfizzle.mercantile.data;

import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;

import java.util.concurrent.CompletableFuture;

/**
 * Mercantile's block loot tables: the Sentry Pylon drops itself when broken. Its fuel is
 * consumed, not stored, so nothing else is owed by the table.
 */
public class MercantileBlockLootTableProvider extends FabricBlockLootTableProvider {

    public MercantileBlockLootTableProvider(FabricDataOutput output,
                                            CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    public void generate() {
        dropSelfWithSequence(MercantileRegistry.SENTRY_PYLON);
    }

    /**
     * {@link #dropSelf(Block)} with the table's random sequence restored.
     *
     * <p>Vanilla's own {@code LootTableProvider} stamps every table with
     * {@code random_sequence = <its own id>} before setting the param set;
     * {@code FabricLootTableProviderImpl.run} only sets the param set, so a bare
     * {@code dropSelf} silently omits the key. It selects the per-table RNG stream —
     * seeded off the world seed and persisted in the level's {@code random_sequences}
     * data — that the {@code survives_explosion} condition rolls against, so a table
     * without it sits outside the sequence state vanilla puts every table into.
     * See the {@code mc-datagen} skill.
     */
    private void dropSelfWithSequence(Block block) {
        add(block, createSingleItemTable(block).setRandomSequence(block.getLootTable().location()));
    }
}
