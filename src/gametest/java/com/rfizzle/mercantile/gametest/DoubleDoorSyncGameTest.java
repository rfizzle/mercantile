package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class DoubleDoorSyncGameTest implements FabricGameTest {

    private static final BlockPos LEFT = new BlockPos(2, 1, 2);
    private static final BlockPos LEFT_UPPER = new BlockPos(2, 2, 2);
    private static final BlockPos RIGHT = new BlockPos(3, 1, 2);
    private static final BlockPos RIGHT_UPPER = new BlockPos(3, 2, 2);

    private static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < 5; x++)
            for (int z = 0; z < 5; z++)
                helper.setBlock(x, 0, z, Blocks.STONE.defaultBlockState());
    }

    private static void placeDoorPair(GameTestHelper helper, boolean open) {
        BlockState leftLower = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.OPEN, open);
        BlockState leftUpper = leftLower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        BlockState rightLower = leftLower.setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT);
        BlockState rightUpper = leftUpper.setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT);
        helper.setBlock(LEFT, leftLower);
        helper.setBlock(LEFT_UPPER, leftUpper);
        helper.setBlock(RIGHT, rightLower);
        helper.setBlock(RIGHT_UPPER, rightUpper);
    }

    private static void placeFenceGatePair(GameTestHelper helper, boolean open) {
        BlockState gate = Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(FenceGateBlock.FACING, Direction.NORTH)
                .setValue(FenceGateBlock.OPEN, open)
                .setValue(FenceGateBlock.POWERED, false);
        helper.setBlock(LEFT, gate);
        helper.setBlock(RIGHT, gate);
    }

    private static BlockHitResult hitAt(GameTestHelper helper, BlockPos relative) {
        BlockPos abs = helper.absolutePos(relative);
        return new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
    }

    private static ServerPlayer spawnPlayerAt(GameTestHelper helper, BlockPos relative) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos abs = helper.absolutePos(relative);
        player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        return player;
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void doorPairOpensTogether(GameTestHelper helper) {
        buildFloor(helper);
        placeDoorPair(helper, false);

        ServerPlayer player = spawnPlayerAt(helper, LEFT);
        DoorBlock door = (DoorBlock) Blocks.OAK_DOOR;
        door.setOpen(player, helper.getLevel(), helper.getBlockState(LEFT),
                helper.absolutePos(LEFT), true);

        helper.assertBlockProperty(LEFT, DoorBlock.OPEN, true);
        helper.assertBlockProperty(RIGHT, DoorBlock.OPEN, true);

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void doorPairClosesTogether(GameTestHelper helper) {
        buildFloor(helper);
        placeDoorPair(helper, true);

        ServerPlayer player = spawnPlayerAt(helper, RIGHT);
        DoorBlock door = (DoorBlock) Blocks.OAK_DOOR;
        door.setOpen(player, helper.getLevel(), helper.getBlockState(RIGHT),
                helper.absolutePos(RIGHT), false);

        helper.assertBlockProperty(LEFT, DoorBlock.OPEN, false);
        helper.assertBlockProperty(RIGHT, DoorBlock.OPEN, false);

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void singleDoorUnaffected(GameTestHelper helper) {
        buildFloor(helper);

        BlockState leftLower = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.OPEN, false);
        BlockState leftUpper = leftLower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        helper.setBlock(LEFT, leftLower);
        helper.setBlock(LEFT_UPPER, leftUpper);

        ServerPlayer player = spawnPlayerAt(helper, LEFT);
        DoorBlock door = (DoorBlock) Blocks.OAK_DOOR;
        door.setOpen(player, helper.getLevel(), helper.getBlockState(LEFT),
                helper.absolutePos(LEFT), true);

        helper.assertBlockProperty(LEFT, DoorBlock.OPEN, true);
        helper.assertTrue(helper.getBlockState(RIGHT).isAir(),
                "Right slot should remain air (no ghost door placed)");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void mismatchedHingeDoesNotSync(GameTestHelper helper) {
        buildFloor(helper);

        BlockState lower = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.OPEN, false);
        BlockState upper = lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        helper.setBlock(LEFT, lower);
        helper.setBlock(LEFT_UPPER, upper);
        helper.setBlock(RIGHT, lower);
        helper.setBlock(RIGHT_UPPER, upper);

        ServerPlayer player = spawnPlayerAt(helper, LEFT);
        DoorBlock door = (DoorBlock) Blocks.OAK_DOOR;
        door.setOpen(player, helper.getLevel(), helper.getBlockState(LEFT),
                helper.absolutePos(LEFT), true);

        helper.assertBlockProperty(LEFT, DoorBlock.OPEN, true);
        helper.assertBlockProperty(RIGHT, DoorBlock.OPEN, false);

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fenceGatePairOpensTogether(GameTestHelper helper) {
        buildFloor(helper);
        placeFenceGatePair(helper, false);

        ServerPlayer player = spawnPlayerAt(helper, LEFT);
        BlockState state = helper.getBlockState(LEFT);
        state.useWithoutItem(helper.getLevel(), player, hitAt(helper, LEFT));

        helper.assertBlockProperty(LEFT, FenceGateBlock.OPEN, true);
        helper.assertBlockProperty(RIGHT, FenceGateBlock.OPEN, true);

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fenceGatePairClosesTogether(GameTestHelper helper) {
        buildFloor(helper);
        placeFenceGatePair(helper, true);

        ServerPlayer player = spawnPlayerAt(helper, RIGHT);
        BlockState state = helper.getBlockState(RIGHT);
        state.useWithoutItem(helper.getLevel(), player, hitAt(helper, RIGHT));

        helper.assertBlockProperty(LEFT, FenceGateBlock.OPEN, false);
        helper.assertBlockProperty(RIGHT, FenceGateBlock.OPEN, false);

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fenceGateOppositeFacingPartnerPreservesFacing(GameTestHelper helper) {
        buildFloor(helper);
        BlockState north = Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(FenceGateBlock.FACING, Direction.NORTH)
                .setValue(FenceGateBlock.OPEN, false)
                .setValue(FenceGateBlock.POWERED, false);
        BlockState south = north.setValue(FenceGateBlock.FACING, Direction.SOUTH);
        helper.setBlock(LEFT, north);
        helper.setBlock(RIGHT, south);

        ServerPlayer player = spawnPlayerAt(helper, LEFT);
        // Face north so vanilla doesn't flip LEFT's FACING in useWithoutItem.
        player.setYRot(180f);
        BlockState state = helper.getBlockState(LEFT);
        state.useWithoutItem(helper.getLevel(), player, hitAt(helper, LEFT));

        helper.assertBlockProperty(LEFT, FenceGateBlock.OPEN, true);
        helper.assertBlockProperty(LEFT, FenceGateBlock.FACING, Direction.NORTH);
        helper.assertBlockProperty(RIGHT, FenceGateBlock.OPEN, true);
        helper.assertBlockProperty(RIGHT, FenceGateBlock.FACING, Direction.SOUTH);

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fenceGateRedstonePartnerNoSync(GameTestHelper helper) {
        buildFloor(helper);
        BlockState unpowered = Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(FenceGateBlock.FACING, Direction.NORTH)
                .setValue(FenceGateBlock.OPEN, false)
                .setValue(FenceGateBlock.POWERED, false);
        BlockState powered = unpowered.setValue(FenceGateBlock.POWERED, true);
        helper.setBlock(LEFT, unpowered);
        helper.setBlock(RIGHT, powered);

        ServerPlayer player = spawnPlayerAt(helper, LEFT);
        BlockState state = helper.getBlockState(LEFT);
        state.useWithoutItem(helper.getLevel(), player, hitAt(helper, LEFT));

        helper.assertBlockProperty(LEFT, FenceGateBlock.OPEN, true);
        helper.assertBlockProperty(RIGHT, FenceGateBlock.OPEN, false);
        helper.assertBlockProperty(RIGHT, FenceGateBlock.POWERED, true);

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void configDisabledDoorNoSync(GameTestHelper helper) {
        boolean saved = MercantileConfig.get().enableDoubleDoorSync;
        try {
            MercantileConfig.get().enableDoubleDoorSync = false;
            buildFloor(helper);
            placeDoorPair(helper, false);

            ServerPlayer player = spawnPlayerAt(helper, LEFT);
            DoorBlock door = (DoorBlock) Blocks.OAK_DOOR;
            door.setOpen(player, helper.getLevel(), helper.getBlockState(LEFT),
                    helper.absolutePos(LEFT), true);

            helper.assertBlockProperty(LEFT, DoorBlock.OPEN, true);
            helper.assertBlockProperty(RIGHT, DoorBlock.OPEN, false);

            player.discard();
            helper.succeed();
        } finally {
            MercantileConfig.get().enableDoubleDoorSync = saved;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void configDisabledFenceGateNoSync(GameTestHelper helper) {
        boolean saved = MercantileConfig.get().enableDoubleDoorSync;
        try {
            MercantileConfig.get().enableDoubleDoorSync = false;
            buildFloor(helper);
            placeFenceGatePair(helper, false);

            ServerPlayer player = spawnPlayerAt(helper, LEFT);
            BlockState state = helper.getBlockState(LEFT);
            state.useWithoutItem(helper.getLevel(), player, hitAt(helper, LEFT));

            helper.assertBlockProperty(LEFT, FenceGateBlock.OPEN, true);
            helper.assertBlockProperty(RIGHT, FenceGateBlock.OPEN, false);

            player.discard();
            helper.succeed();
        } finally {
            MercantileConfig.get().enableDoubleDoorSync = saved;
        }
    }
}
