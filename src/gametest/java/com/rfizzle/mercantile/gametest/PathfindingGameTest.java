package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.Path;

public class PathfindingGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void fenceGatePassable(GameTestHelper helper) {
        buildFloor(helper);
        for (int x = 0; x < 5; x++) {
            if (x != 2) {
                helper.setBlock(x, 1, 2, Blocks.STONE.defaultBlockState());
                helper.setBlock(x, 2, 2, Blocks.STONE.defaultBlockState());
            }
        }
        helper.setBlock(2, 1, 2, Blocks.OAK_FENCE_GATE.defaultBlockState());

        Villager villager = helper.spawn(EntityType.VILLAGER, 2, 1, 0);
        helper.runAfterDelay(2, () -> {
            Path path = villager.getNavigation().createPath(
                    helper.absolutePos(new BlockPos(2, 1, 4)), 0);
            helper.assertTrue(path != null, "Villager should find a path");
            helper.assertTrue(pathContains(path, helper.absolutePos(new BlockPos(2, 1, 2))),
                    "Path should go through fence gate");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void staircaseClimb(GameTestHelper helper) {
        buildFloor(helper);
        for (int x = 0; x < 5; x++) {
            helper.setBlock(x, 1, 3, Blocks.STONE.defaultBlockState());
            helper.setBlock(x, 1, 4, Blocks.STONE.defaultBlockState());
        }
        helper.setBlock(2, 1, 2, Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.NORTH)
                .setValue(StairBlock.HALF, Half.BOTTOM));

        Villager villager = helper.spawn(EntityType.VILLAGER, 2, 1, 0);
        helper.runAfterDelay(2, () -> {
            Path path = villager.getNavigation().createPath(
                    helper.absolutePos(new BlockPos(2, 2, 4)), 0);
            helper.assertTrue(path != null, "Villager should path up staircase");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void ladderClimb(GameTestHelper helper) {
        buildFloor(helper);
        helper.setBlock(3, 1, 2, Blocks.STONE.defaultBlockState());
        helper.setBlock(3, 2, 2, Blocks.STONE.defaultBlockState());
        helper.setBlock(2, 1, 2, Blocks.LADDER.defaultBlockState()
                .setValue(LadderBlock.FACING, Direction.WEST));
        helper.setBlock(2, 2, 2, Blocks.LADDER.defaultBlockState()
                .setValue(LadderBlock.FACING, Direction.WEST));
        helper.setBlock(2, 1, 3, Blocks.STONE.defaultBlockState());
        helper.setBlock(2, 1, 4, Blocks.STONE.defaultBlockState());

        Villager villager = helper.spawn(EntityType.VILLAGER, 2, 1, 0);
        helper.runAfterDelay(2, () -> {
            Path path = villager.getNavigation().createPath(
                    helper.absolutePos(new BlockPos(2, 2, 4)), 0);
            helper.assertTrue(path != null, "Villager should path up ladder");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void waterAvoidancePath(GameTestHelper helper) {
        buildFloor(helper);
        helper.setBlock(2, 1, 2, Blocks.WATER.defaultBlockState());

        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        helper.runAfterDelay(2, () -> {
            Path path = villager.getNavigation().createPath(
                    helper.absolutePos(new BlockPos(4, 1, 4)), 0);
            helper.assertTrue(path != null, "Path should exist around water");

            BlockPos waterAbs = helper.absolutePos(new BlockPos(2, 1, 2));
            helper.assertFalse(pathContains(path, waterAbs),
                    "Path should not go through water");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "pathfindingConfig")
    public void configDisableRevertsToVanilla(GameTestHelper helper) {
        boolean orig = MercantileConfig.get().enablePathfindingFixes;
        MercantileConfig.get().enablePathfindingFixes = false;

        buildFloor(helper);
        for (int x = 0; x < 5; x++) {
            if (x != 2) {
                helper.setBlock(x, 1, 2, Blocks.STONE.defaultBlockState());
                helper.setBlock(x, 2, 2, Blocks.STONE.defaultBlockState());
            }
        }
        helper.setBlock(2, 1, 2, Blocks.OAK_FENCE_GATE.defaultBlockState());

        Villager villager = helper.spawn(EntityType.VILLAGER, 2, 1, 0);
        helper.runAfterDelay(2, () -> {
            try {
                Path path = villager.getNavigation().createPath(
                        helper.absolutePos(new BlockPos(2, 1, 4)), 0);
                helper.assertTrue(path != null, "Path should still exist (around the gate)");
                helper.assertFalse(pathContains(path, helper.absolutePos(new BlockPos(2, 1, 2))),
                        "With config disabled, path should not go through fence gate");
                helper.succeed();
            } finally {
                MercantileConfig.get().enablePathfindingFixes = orig;
            }
        });
    }

    private void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < 5; x++)
            for (int z = 0; z < 5; z++)
                helper.setBlock(x, 0, z, Blocks.STONE.defaultBlockState());
    }

    private static boolean pathContains(Path path, BlockPos pos) {
        for (int i = 0; i < path.getNodeCount(); i++) {
            if (path.getNode(i).asBlockPos().equals(pos)) {
                return true;
            }
        }
        return false;
    }
}
