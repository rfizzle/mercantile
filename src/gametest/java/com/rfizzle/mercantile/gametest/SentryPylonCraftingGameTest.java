package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;
import java.util.Optional;

public class SentryPylonCraftingGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void recipeExistsAndProducesOnePylon(GameTestHelper helper) {
        Optional<RecipeHolder<?>> holder = helper.getLevel().getRecipeManager()
                .byKey(Mercantile.id("sentry_pylon"));
        helper.assertTrue(holder.isPresent(), "sentry_pylon recipe should exist");

        if (!(holder.get().value() instanceof CraftingRecipe recipe)) {
            helper.fail("sentry_pylon recipe should be a CraftingRecipe");
            return;
        }
        ItemStack result = recipe.getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(MercantileRegistry.SENTRY_PYLON.asItem()),
                "recipe result should be sentry_pylon (got " + result + ")");
        helper.assertTrue(result.getCount() == 1,
                "recipe result count should be 1 (got " + result.getCount() + ")");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void recipeAssemblesPylonFromIngredients(GameTestHelper helper) {
        Optional<RecipeHolder<?>> holder = helper.getLevel().getRecipeManager()
                .byKey(Mercantile.id("sentry_pylon"));
        helper.assertTrue(holder.isPresent(), "sentry_pylon recipe should exist");
        if (!(holder.get().value() instanceof CraftingRecipe recipe)) {
            helper.fail("sentry_pylon recipe should be a CraftingRecipe");
            return;
        }

        // Layout matches data/mercantile/recipe/sentry_pylon.json:
        //  P
        // IBI
        // SIS
        ItemStack P = new ItemStack(Items.CARVED_PUMPKIN);
        ItemStack I = new ItemStack(Items.IRON_BLOCK);
        ItemStack B = new ItemStack(Items.BELL);
        ItemStack S = new ItemStack(Items.STONE_BRICKS);
        ItemStack empty = ItemStack.EMPTY;

        CraftingInput input = CraftingInput.of(3, 3, List.of(
                empty, P, empty,
                I, B, I,
                S, I, S
        ));

        helper.assertTrue(recipe.matches(input, helper.getLevel()),
                "recipe should match the expected 3x3 layout");

        ItemStack assembled = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(assembled.is(MercantileRegistry.SENTRY_PYLON.asItem()),
                "assembled result should be sentry_pylon (got " + assembled + ")");
        helper.assertTrue(assembled.getCount() == 1,
                "assembled count should be 1 (got " + assembled.getCount() + ")");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void recipeIsCraftingType(GameTestHelper helper) {
        long count = helper.getLevel().getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(h -> h.id().equals(Mercantile.id("sentry_pylon")))
                .count();
        helper.assertTrue(count == 1,
                "exactly one CRAFTING recipe should match sentry_pylon (got " + count + ")");
        helper.succeed();
    }
}
