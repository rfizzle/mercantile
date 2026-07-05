package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.trade.EmeraldPayment;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

/**
 * Work-order gametests (issue #90). Gametest structures run concurrently only a few blocks
 * apart while {@code WorkOrder.SEARCH_RADIUS} is 48, so any test whose outcome depends on which
 * free workstations exist holds a workstation type no other gametest places (stonecutter, loom,
 * cartography table, smoker — the suite otherwise only places lecterns and composters); tests
 * that only assert pass-through are immune and share the lectern.
 */
public class WorkOrderGameTest implements FabricGameTest {

    /** Generous headroom for the villager brain to convert POTENTIAL_JOB_SITE into a profession. */
    private static final int ASSIGNMENT_TIMEOUT_TICKS = 200;

    private static final BlockPos VILLAGER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos WORKSTATION_POS = new BlockPos(2, 1, 1);

    /**
     * EMPTY_STRUCTURE is 8x8x8 of pure air — without a floor the reachability check in
     * {@code placeOrder} correctly finds no walkable ground and every order is denied. Spawns the
     * villager on a small stone floor, settled so pathfinding treats it like a real villager
     * (a freshly spawned entity has not ticked its onGround flag yet, and GroundPathNavigation
     * refuses to path for an airborne mob).
     */
    private static Villager spawnUnemployed(GameTestHelper helper) {
        for (int x = 0; x <= 3; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER_POS);
        villager.setOnGround(true);
        return villager;
    }

    /** Survival mock player sneaking with the given workstation item, standing next to the villager. */
    private static ServerPlayer orderingPlayer(GameTestHelper helper, Villager villager, Item held, int emeralds) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // Mock server players default to creative, where instabuild waives the fee; force
        // survival so the emeralds are actually charged.
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(held));
        if (emeralds > 0) {
            player.getInventory().add(new ItemStack(Items.EMERALD, emeralds));
        }
        player.moveTo(villager.position().add(1, 0, 0));
        player.setShiftKeyDown(true);
        return player;
    }

    private static Optional<GlobalPos> potentialJobSite(Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = ASSIGNMENT_TIMEOUT_TICKS)
    public void orderSendsVillagerToWorkstationAndAssignsProfession(GameTestHelper helper) {
        int cost = MercantileConfig.get().workOrderEmeraldCost;
        BlockPos stationAbs = helper.absolutePos(WORKSTATION_POS);
        helper.setBlock(WORKSTATION_POS, Blocks.SMITHING_TABLE);

        Villager villager = spawnUnemployed(helper);
        ServerPlayer player = orderingPlayer(helper, villager, Items.SMITHING_TABLE, cost + 3);

        var result = villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(result.consumesAction(),
                "An accepted work order should consume the interaction (got " + result + ")");
        helper.assertTrue(player.getMainHandItem().is(Items.SMITHING_TABLE) && player.getMainHandItem().getCount() == 1,
                "The workstation item must not be consumed");
        helper.assertTrue(EmeraldPayment.count(player) == 3,
                "The emerald fee should be deducted, " + EmeraldPayment.count(player) + " emeralds left");
        Optional<GlobalPos> memory = potentialJobSite(villager);
        helper.assertTrue(memory.isPresent() && memory.get().pos().equals(stationAbs),
                "POTENTIAL_JOB_SITE should point at the smithing table; got " + memory);
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(stationAbs) == 0,
                "The order should claim the site's POI ticket");

        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.TOOLSMITH,
                    "The villager should take the toolsmith profession from the claimed smithing table");
            player.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void deniedWithoutWorkstationAndNoFeeTaken(GameTestHelper helper) {
        int cost = MercantileConfig.get().workOrderEmeraldCost;
        Villager villager = spawnUnemployed(helper);
        // No stonecutter exists anywhere in the gametest world; the 48-block search must come up
        // empty regardless of what neighboring structures place.
        ServerPlayer player = orderingPlayer(helper, villager, Items.STONECUTTER, cost + 3);

        var result = villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertFalse(result.consumesAction(),
                "A denied order should not consume the interaction (got " + result + ")");
        helper.assertTrue(EmeraldPayment.count(player) == cost + 3,
                "No fee may be taken on a denied order, got " + EmeraldPayment.count(player));
        helper.assertTrue(potentialJobSite(villager).isEmpty(),
                "No POTENTIAL_JOB_SITE should be set when no workstation is in range");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void claimedWorkstationIsRefused(GameTestHelper helper) {
        int cost = MercantileConfig.get().workOrderEmeraldCost;
        BlockPos loomAbs = helper.absolutePos(WORKSTATION_POS);
        helper.setBlock(WORKSTATION_POS, Blocks.LOOM);
        // Claim the loom's only ticket, as another villager's pending claim would.
        Optional<BlockPos> taken = helper.getLevel().getPoiManager()
                .take(holder -> true, (holder, pos) -> pos.equals(loomAbs), loomAbs, 1);
        helper.assertTrue(taken.isPresent(), "Test setup: the loom's POI ticket should be takeable");

        Villager villager = spawnUnemployed(helper);
        ServerPlayer player = orderingPlayer(helper, villager, Items.LOOM, cost + 3);

        var result = villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertFalse(result.consumesAction(),
                "An order for a fully claimed workstation must be denied (got " + result + ")");
        helper.assertTrue(EmeraldPayment.count(player) == cost + 3,
                "No fee may be taken when the only workstation is already claimed");
        helper.assertTrue(potentialJobSite(villager).isEmpty(),
                "No POTENTIAL_JOB_SITE should be set for a claimed workstation");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void reorderReleasesPriorTicket(GameTestHelper helper) {
        int cost = MercantileConfig.get().workOrderEmeraldCost;
        BlockPos cartographyAbs = helper.absolutePos(WORKSTATION_POS);
        BlockPos smokerAbs = helper.absolutePos(new BlockPos(1, 1, 2));
        helper.setBlock(WORKSTATION_POS, Blocks.CARTOGRAPHY_TABLE);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.SMOKER);

        Villager villager = spawnUnemployed(helper);
        ServerPlayer player = orderingPlayer(helper, villager, Items.CARTOGRAPHY_TABLE, cost * 2 + 3);

        villager.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(cartographyAbs) == 0,
                "The first order should claim the cartography table's ticket");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SMOKER));
        villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(cartographyAbs) == 1,
                "Re-ordering must release the previously claimed ticket");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(smokerAbs) == 0,
                "Re-ordering must claim the new site's ticket");
        Optional<GlobalPos> memory = potentialJobSite(villager);
        helper.assertTrue(memory.isPresent() && memory.get().pos().equals(smokerAbs),
                "POTENTIAL_JOB_SITE should point at the smoker after the re-order; got " + memory);
        helper.assertTrue(EmeraldPayment.count(player) == 3,
                "Both accepted orders should charge the fee, got " + EmeraldPayment.count(player));

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void creativePlayerPaysNothing(GameTestHelper helper) {
        BlockPos stationAbs = helper.absolutePos(WORKSTATION_POS);
        helper.setBlock(WORKSTATION_POS, Blocks.FLETCHING_TABLE);

        Villager villager = spawnUnemployed(helper);
        ServerPlayer player = orderingPlayer(helper, villager, Items.FLETCHING_TABLE, 0);
        player.getAbilities().instabuild = true;

        var result = villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(result.consumesAction(),
                "A creative order with zero emeralds should still be accepted (got " + result + ")");
        Optional<GlobalPos> memory = potentialJobSite(villager);
        helper.assertTrue(memory.isPresent() && memory.get().pos().equals(stationAbs),
                "POTENTIAL_JOB_SITE should point at the fletching table; got " + memory);

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void featureInertWhenDisabled(GameTestHelper helper) {
        int cost = MercantileConfig.get().workOrderEmeraldCost;
        helper.setBlock(WORKSTATION_POS, Blocks.LECTERN);
        Villager villager = spawnUnemployed(helper);
        ServerPlayer player = orderingPlayer(helper, villager, Items.LECTERN, cost + 3);

        boolean saved = MercantileConfig.get().enableWorkOrders;
        try {
            MercantileConfig.get().enableWorkOrders = false;
            villager.interact(player, InteractionHand.MAIN_HAND);
        } finally {
            MercantileConfig.get().enableWorkOrders = saved;
        }

        helper.assertTrue(EmeraldPayment.count(player) == cost + 3,
                "No fee may be taken while work orders are disabled");
        helper.assertTrue(potentialJobSite(villager).isEmpty(),
                "No POTENTIAL_JOB_SITE should be set while work orders are disabled");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void employedVillagerUnaffected(GameTestHelper helper) {
        int cost = MercantileConfig.get().workOrderEmeraldCost;
        helper.setBlock(WORKSTATION_POS, Blocks.LECTERN);
        Villager villager = spawnUnemployed(helper);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        ServerPlayer player = orderingPlayer(helper, villager, Items.LECTERN, cost + 3);

        villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(EmeraldPayment.count(player) == cost + 3,
                "An employed villager must not accept (or charge for) a work order");
        helper.assertTrue(potentialJobSite(villager).isEmpty(),
                "An employed villager's POTENTIAL_JOB_SITE must stay untouched");
        helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.FARMER,
                "The profession must be unchanged");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void sleepingVillagerUnaffected(GameTestHelper helper) {
        int cost = MercantileConfig.get().workOrderEmeraldCost;
        helper.setBlock(WORKSTATION_POS, Blocks.LECTERN);
        Villager villager = spawnUnemployed(helper);
        villager.startSleeping(villager.blockPosition());
        ServerPlayer player = orderingPlayer(helper, villager, Items.LECTERN, cost + 3);

        villager.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(villager.isSleeping(), "Test setup: the villager should be sleeping");
        helper.assertTrue(EmeraldPayment.count(player) == cost + 3,
                "A sleeping villager must not accept (or charge for) a work order");
        helper.assertTrue(potentialJobSite(villager).isEmpty(),
                "A sleeping villager's POTENTIAL_JOB_SITE must stay untouched");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void nitwitsAndBabiesUnaffected(GameTestHelper helper) {
        int cost = MercantileConfig.get().workOrderEmeraldCost;
        helper.setBlock(WORKSTATION_POS, Blocks.LECTERN);

        Villager nitwit = spawnUnemployed(helper);
        nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
        ServerPlayer player = orderingPlayer(helper, nitwit, Items.LECTERN, cost + 3);

        nitwit.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(potentialJobSite(nitwit).isEmpty(),
                "A nitwit must not accept a work order");
        helper.assertTrue(nitwit.getVillagerData().getProfession() == VillagerProfession.NITWIT,
                "The nitwit must keep its profession");

        Villager baby = spawnUnemployed(helper);
        baby.setBaby(true);
        baby.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(potentialJobSite(baby).isEmpty(),
                "A baby must not accept a work order");

        helper.assertTrue(EmeraldPayment.count(player) == cost + 3,
                "Neither ineligible interaction may charge a fee");

        player.discard();
        helper.succeed();
    }
}
