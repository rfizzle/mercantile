package com.rfizzle.mercantile.data;

import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

/**
 * Mercantile's crafting recipes ({@code design/SPEC.md}, Sentry Pylon): a carved pumpkin over a
 * bell braced by iron blocks, on a stone-brick footing.
 */
public class MercantileRecipeProvider extends FabricRecipeProvider {

    public MercantileRecipeProvider(FabricDataOutput output,
                                    CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    public void buildRecipes(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, MercantileRegistry.SENTRY_PYLON)
                .pattern(" P ")
                .pattern("IBI")
                .pattern("SIS")
                .define('P', Items.CARVED_PUMPKIN)
                .define('I', Items.IRON_BLOCK)
                .define('B', Items.BELL)
                .define('S', Items.STONE_BRICKS)
                .unlockedBy("has_iron_block", has(Items.IRON_BLOCK))
                .save(output);
    }
}
