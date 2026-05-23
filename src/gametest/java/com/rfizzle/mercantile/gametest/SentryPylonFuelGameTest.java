package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.block.SentryPylonBlock;
import com.rfizzle.mercantile.block.SentryPylonBlockEntity;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class SentryPylonFuelGameTest implements FabricGameTest {

    private static BlockHitResult hitAt(GameTestHelper helper, BlockPos relative) {
        BlockPos abs = helper.absolutePos(relative);
        return new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
    }

    private static ItemInteractionResult useIronBlock(GameTestHelper helper, ServerPlayer player,
                                                      BlockPos pylonPos) {
        BlockPos abs = helper.absolutePos(pylonPos);
        BlockState state = helper.getLevel().getBlockState(abs);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_BLOCK, 4));
        BlockHitResult hit = hitAt(helper, pylonPos);
        return state.useItemOn(player.getItemInHand(InteractionHand.MAIN_HAND), helper.getLevel(),
                player, InteractionHand.MAIN_HAND, hit);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void rightClickWithIronBlockAddsFuel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity be = (SentryPylonBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "block entity should exist");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        BlockPos abs = helper.absolutePos(pos);
        player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);

        ItemInteractionResult result = useIronBlock(helper, player, pos);

        helper.assertTrue(result.consumesAction(),
                "useItemOn should consume action (got " + result + ")");
        helper.assertTrue(be.getFuel() == 1,
                "fuel should be 1 after one iron block (got " + be.getFuel() + ")");
        helper.assertTrue(player.getMainHandItem().getCount() == 3,
                "stack should shrink by 1 (got " + player.getMainHandItem().getCount() + ")");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void rightClickWithIronBlockWhenFullDoesNotConsume(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity be = (SentryPylonBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "block entity should exist");

        int max = be.getMaxFuel();
        be.setFuel(max);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        BlockPos abs = helper.absolutePos(pos);
        player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);

        ItemInteractionResult result = useIronBlock(helper, player, pos);

        helper.assertTrue(result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                "useItemOn should pass when full (got " + result + ")");
        helper.assertTrue(be.getFuel() == max,
                "fuel should remain at max (got " + be.getFuel() + ")");
        helper.assertTrue(player.getMainHandItem().getCount() == 4,
                "stack should be unchanged (got " + player.getMainHandItem().getCount() + ")");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void creativePlayerDoesNotConsumeIronBlock(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity be = (SentryPylonBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "block entity should exist");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = true;
        BlockPos abs = helper.absolutePos(pos);
        player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);

        useIronBlock(helper, player, pos);

        helper.assertTrue(be.getFuel() == 1,
                "fuel should still be 1 in creative (got " + be.getFuel() + ")");
        helper.assertTrue(player.getMainHandItem().getCount() == 4,
                "stack should not shrink in creative (got " + player.getMainHandItem().getCount() + ")");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void rightClickWithNonIronDoesNothing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity be = (SentryPylonBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "block entity should exist");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        BlockPos abs = helper.absolutePos(pos);
        player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 4));

        BlockHitResult hit = hitAt(helper, pos);
        BlockState state = helper.getLevel().getBlockState(abs);
        ItemInteractionResult result = state.useItemOn(player.getItemInHand(InteractionHand.MAIN_HAND),
                helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);

        helper.assertTrue(result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                "useItemOn should pass for non-iron item");
        helper.assertTrue(be.getFuel() == 0, "fuel should remain 0");
        helper.assertTrue(player.getMainHandItem().getCount() == 4, "dirt stack should be unchanged");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void redstoneSignalSetsPoweredBlockState(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity be = (SentryPylonBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "block entity should exist");
        be.setFuel(4);

        BlockState before = helper.getBlockState(pos);
        helper.assertFalse(before.getValue(SentryPylonBlock.POWERED),
                "POWERED should be false before signal");

        helper.setBlock(new BlockPos(2, 2, 1), Blocks.REDSTONE_BLOCK);

        helper.succeedWhen(() -> {
            BlockState after = helper.getBlockState(pos);
            helper.assertTrue(after.getValue(SentryPylonBlock.POWERED),
                    "POWERED should be true with adjacent redstone block");
            helper.assertTrue(after.getValue(SentryPylonBlock.STATE)
                            == com.rfizzle.mercantile.block.PylonStateProperty.EMPTY,
                    "visual STATE should be EMPTY when powered");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void comparatorOutputZeroAtEmpty(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity be = (SentryPylonBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "block entity should exist");

        BlockPos abs = helper.absolutePos(pos);
        int signal = helper.getBlockState(pos).getAnalogOutputSignal(helper.getLevel(), abs);
        helper.assertTrue(signal == 0, "comparator output should be 0 at fuel=0 (got " + signal + ")");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void comparatorOutputMaxAtFull(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity be = (SentryPylonBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "block entity should exist");
        be.setFuel(be.getMaxFuel());

        BlockPos abs = helper.absolutePos(pos);
        int signal = helper.getBlockState(pos).getAnalogOutputSignal(helper.getLevel(), abs);
        helper.assertTrue(signal == 15,
                "comparator output should be 15 at max fuel (got " + signal + ")");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void comparatorOutputScalesProportionally(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity be = (SentryPylonBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "block entity should exist");

        int max = be.getMaxFuel();
        int half = max / 2;
        be.setFuel(half);

        BlockPos abs = helper.absolutePos(pos);
        int signal = helper.getBlockState(pos).getAnalogOutputSignal(helper.getLevel(), abs);
        int expected = Math.max(1, (int) Math.ceil((half / (float) max) * 15.0f));
        helper.assertTrue(signal == expected,
                "comparator output at half fuel should be " + expected + " (got " + signal + ")");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void outOfFuelAlertFiresThenCoolsDown(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MercantileRegistry.SENTRY_PYLON);
        SentryPylonBlockEntity be = (SentryPylonBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "block entity should exist");

        helper.assertTrue(be.getOutOfFuelCooldown() == 0, "cooldown should start at 0");
        be.tryAlertOutOfFuel();
        helper.assertTrue(be.getOutOfFuelCooldown() > 0,
                "cooldown should be set after first alert (got " + be.getOutOfFuelCooldown() + ")");

        int afterFirst = be.getOutOfFuelCooldown();
        be.tryAlertOutOfFuel();
        helper.assertTrue(be.getOutOfFuelCooldown() == afterFirst,
                "cooldown should not be re-set by second call during cooldown");

        helper.succeed();
    }
}
