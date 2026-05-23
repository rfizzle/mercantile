package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.compat.BreedingTooltipData;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BreedingTooltipGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void freshAdultIsNotWillingNoBed(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);

        CompoundTag tag = new CompoundTag();
        BreedingTooltipData.write(tag, villager);

        helper.assertTrue(tag.getBoolean(BreedingTooltipData.KEY_PRESENT), "data should be present");
        helper.assertFalse(tag.getBoolean(BreedingTooltipData.KEY_IS_BABY), "adult should not be baby");
        helper.assertFalse(tag.getBoolean(BreedingTooltipData.KEY_WILLING), "fresh villager should not be willing");
        helper.assertFalse(tag.getBoolean(BreedingTooltipData.KEY_HAS_BED), "no HOME memory should mean no bed");
        helper.assertValueEqual(tag.getInt(BreedingTooltipData.KEY_FOOD_POINTS), 0, "empty inventory should be 0 food points");
        helper.assertValueEqual(tag.getString(BreedingTooltipData.KEY_NOT_WILLING_REASON),
                BreedingTooltipData.REASON_NO_BED, "no-bed reason expected");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void withFoodButNoBedReasonIsNoBed(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);
        villager.getInventory().addItem(new ItemStack(Items.BREAD, 6));

        CompoundTag tag = new CompoundTag();
        BreedingTooltipData.write(tag, villager);

        helper.assertValueEqual(tag.getInt(BreedingTooltipData.KEY_FOOD_POINTS), 24,
                "6 bread = 24 food points");
        helper.assertValueEqual(tag.getString(BreedingTooltipData.KEY_NOT_WILLING_REASON),
                BreedingTooltipData.REASON_NO_BED, "no-bed reason has priority over food");

        CompoundTag counts = tag.getCompound(BreedingTooltipData.KEY_FOOD_COUNTS);
        helper.assertValueEqual(counts.getInt("minecraft:bread"), 6, "bread count = 6");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void withBedAndNoFoodReasonIsNotEnoughFood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bedAbs = helper.absolutePos(new BlockPos(1, 1, 1));
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bedAbs));

        CompoundTag tag = new CompoundTag();
        BreedingTooltipData.write(tag, villager);

        helper.assertTrue(tag.getBoolean(BreedingTooltipData.KEY_HAS_BED), "HOME memory should mean has bed");
        helper.assertValueEqual(tag.getString(BreedingTooltipData.KEY_NOT_WILLING_REASON),
                BreedingTooltipData.REASON_NOT_ENOUGH_FOOD, "should report not_enough_food");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void babyVillagerHasBabyAgeOnly(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setAge(-12000);

        CompoundTag tag = new CompoundTag();
        BreedingTooltipData.write(tag, villager);

        helper.assertTrue(tag.getBoolean(BreedingTooltipData.KEY_PRESENT), "data present");
        helper.assertTrue(tag.getBoolean(BreedingTooltipData.KEY_IS_BABY), "should be baby");
        helper.assertValueEqual(tag.getInt(BreedingTooltipData.KEY_BABY_AGE), 12000, "baby age = 12000 ticks");
        helper.assertFalse(tag.contains(BreedingTooltipData.KEY_WILLING), "no willing key for babies");
        helper.assertFalse(tag.contains(BreedingTooltipData.KEY_FOOD_COUNTS), "no food counts for babies");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void cooldownVillagerReportsCooldownReason(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bedAbs = helper.absolutePos(new BlockPos(1, 1, 1));
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bedAbs));
        villager.getInventory().addItem(new ItemStack(Items.BREAD, 12));
        villager.setAge(6000);

        CompoundTag tag = new CompoundTag();
        BreedingTooltipData.write(tag, villager);

        helper.assertValueEqual(tag.getInt(BreedingTooltipData.KEY_COOLDOWN), 6000, "cooldown ticks = age");
        helper.assertValueEqual(tag.getString(BreedingTooltipData.KEY_NOT_WILLING_REASON),
                BreedingTooltipData.REASON_COOLDOWN, "cooldown takes priority over food/bed");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void foodCountsAreItemized(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.getInventory().addItem(new ItemStack(Items.BREAD, 2));
        villager.getInventory().addItem(new ItemStack(Items.CARROT, 5));
        villager.getInventory().addItem(new ItemStack(Items.POTATO, 3));
        villager.getInventory().addItem(new ItemStack(Items.BEETROOT, 1));
        villager.getInventory().addItem(new ItemStack(Items.DIRT, 16));

        CompoundTag tag = new CompoundTag();
        BreedingTooltipData.write(tag, villager);

        int expectedPoints = 2 * 4 + 5 + 3 + 1;
        helper.assertValueEqual(tag.getInt(BreedingTooltipData.KEY_FOOD_POINTS), expectedPoints,
                "food points = bread*4 + carrot + potato + beetroot");

        CompoundTag counts = tag.getCompound(BreedingTooltipData.KEY_FOOD_COUNTS);
        helper.assertValueEqual(counts.getInt("minecraft:bread"), 2, "bread count");
        helper.assertValueEqual(counts.getInt("minecraft:carrot"), 5, "carrot count");
        helper.assertValueEqual(counts.getInt("minecraft:potato"), 3, "potato count");
        helper.assertValueEqual(counts.getInt("minecraft:beetroot"), 1, "beetroot count");
        helper.assertFalse(counts.contains("minecraft:dirt"), "non-food items not counted");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void emptyFoodCountsTagWhenNothing(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);

        CompoundTag tag = new CompoundTag();
        BreedingTooltipData.write(tag, villager);

        CompoundTag counts = tag.getCompound(BreedingTooltipData.KEY_FOOD_COUNTS);
        helper.assertTrue(counts.isEmpty(), "food counts should be empty when no food in inventory");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void dataSurvivesEntitySaveLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bedAbs = helper.absolutePos(new BlockPos(1, 1, 1));
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bedAbs));
        villager.getInventory().addItem(new ItemStack(Items.CARROT, 7));

        CompoundTag saved = new CompoundTag();
        villager.saveWithoutId(saved);
        villager.discard();

        Villager loaded = EntityType.VILLAGER.create(level);
        helper.assertTrue(loaded != null, "loaded villager non-null");
        loaded.load(saved);

        CompoundTag tag = new CompoundTag();
        BreedingTooltipData.write(tag, loaded);

        CompoundTag counts = tag.getCompound(BreedingTooltipData.KEY_FOOD_COUNTS);
        helper.assertValueEqual(counts.getInt("minecraft:carrot"), 7, "carrot count survives save/load");

        loaded.discard();
        helper.succeed();
    }
}
