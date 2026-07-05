package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.memorial.FearManager;
import com.rfizzle.mercantile.network.DemandPriceS2CPayload;
import com.rfizzle.mercantile.trade.PriceBreakdownBuilder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public class FearMarkupGameTest implements FabricGameTest {

    // POI registration for the bell can lag block placement by a tick or two.
    private static final int FEAR_TIMEOUT_TICKS = 100;

    private static void placeBell(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.BELL);
    }

    private static void killVillagers(GameTestHelper helper, ServerPlayer player, int count) {
        for (int i = 0; i < count; i++) {
            Villager victim = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            victim.hurt(helper.getLevel().damageSources().playerAttack(player), 1_000.0f);
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = FEAR_TIMEOUT_TICKS)
    public void killingSpreeTriggersFearMarkup(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        placeBell(helper);
        Villager survivor = helper.spawn(EntityType.VILLAGER, 3, 1, 3);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        killVillagers(helper, player, config.fearKillThreshold);

        helper.succeedWhen(() -> {
            double fraction = FearManager.fearFraction(survivor, player, config);
            helper.assertTrue(fraction > 0.99,
                    "A fresh killing spree should put fear at full strength, got " + fraction);
            helper.assertTrue(FearManager.priceModifier(64, fraction, config) > 0,
                    "An active fear fraction should produce a positive price markup");
            player.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = FEAR_TIMEOUT_TICKS)
    public void killsBelowThresholdCauseNoFear(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        placeBell(helper);
        Villager survivor = helper.spawn(EntityType.VILLAGER, 3, 1, 3);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        killVillagers(helper, player, config.fearKillThreshold - 1);

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(FearManager.fearFraction(survivor, player, config) == 0.0,
                    "Kills below the threshold should not trigger fear");
            player.discard();
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = FEAR_TIMEOUT_TICKS)
    public void fearAppliesOnlyToTheKiller(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        placeBell(helper);
        Villager survivor = helper.spawn(EntityType.VILLAGER, 3, 1, 3);
        ServerPlayer killer = helper.makeMockServerPlayerInLevel();
        ServerPlayer bystander = helper.makeMockServerPlayerInLevel();

        killVillagers(helper, killer, config.fearKillThreshold);

        helper.succeedWhen(() -> {
            helper.assertTrue(FearManager.fearFraction(survivor, killer, config) > 0.99,
                    "The killer should face the fear markup");
            helper.assertTrue(FearManager.fearFraction(survivor, bystander, config) == 0.0,
                    "A player who killed nobody should face no fear markup");
            killer.discard();
            bystander.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = FEAR_TIMEOUT_TICKS)
    public void chargedFearMatchesBreakdownAndRespectsStackClamp(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        placeBell(helper);
        Villager trader = helper.spawn(EntityType.VILLAGER, 3, 1, 3);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(trader.getX() + 1, trader.getY(), trader.getZ());

        killVillagers(helper, player, config.fearKillThreshold);

        // An unemployed villager generates no trades, so the offer list is exactly these two.
        MerchantOffer cheap = new MerchantOffer(
                new ItemCost(Items.EMERALD, 8), new ItemStack(Items.APPLE), 12, 2, 0.05f);
        MerchantOffer expensive = new MerchantOffer(
                new ItemCost(Items.EMERALD, 60), new ItemStack(Items.DIAMOND), 12, 2, 0.05f);
        trader.getOffers().add(cheap);
        trader.getOffers().add(expensive);

        // Isolate fear from the other price systems so the lock-step invariant is exact.
        boolean savedMood = config.enableMood;
        boolean savedMarket = config.enableMarketDay;
        boolean savedRep = config.enableReputation;
        config.enableMood = false;
        config.enableMarketDay = false;
        config.enableReputation = false;
        try {
            double fraction = FearManager.fearFraction(trader, player, config);
            helper.assertTrue(fraction > 0.99,
                    "A fresh spree should put fear at full strength, got " + fraction);

            invokeStartTrading(helper, trader, player);

            List<DemandPriceS2CPayload.PriceComponent> components =
                    PriceBreakdownBuilder.buildFor(trader, player);
            DemandPriceS2CPayload.PriceComponent cheapComp = components.get(0);
            DemandPriceS2CPayload.PriceComponent expensiveComp = components.get(1);

            // The charged price must rise by exactly what the breakdown reports as fear,
            // with nothing leaking into the "Other" residual.
            helper.assertTrue(cheapComp.fearModifier() > 0,
                    "The cheap offer should carry a fear markup");
            helper.assertTrue(cheap.getCostA().getCount() == 8 + cheapComp.fearModifier(),
                    "Cheap offer: charged " + cheap.getCostA().getCount()
                            + " but breakdown reports base 8 + fear " + cheapComp.fearModifier());
            helper.assertTrue(cheapComp.otherAdjust() == 0,
                    "Cheap offer: fear must not leak into Other, got " + cheapComp.otherAdjust());

            helper.assertTrue(expensive.getCostA().getCount() <= 64,
                    "The charged cost can never exceed the emerald stack size");
            helper.assertTrue(expensive.getCostA().getCount() == 60 + expensiveComp.fearModifier(),
                    "Expensive offer: charged " + expensive.getCostA().getCount()
                            + " but breakdown reports base 60 + fear " + expensiveComp.fearModifier());
            helper.assertTrue(expensiveComp.otherAdjust() == 0,
                    "Expensive offer: capped fear must not leak into Other, got "
                            + expensiveComp.otherAdjust());
        } finally {
            config.enableMood = savedMood;
            config.enableMarketDay = savedMarket;
            config.enableReputation = savedRep;
            player.closeContainer();
            player.discard();
            trader.discard();
        }
        helper.succeed();
    }

    // startTrading is protected; reflection here mirrors InfoPanelGameTest.
    private static void invokeStartTrading(GameTestHelper helper, Villager villager, ServerPlayer player) {
        Method method;
        try {
            method = Villager.class.getDeclaredMethod("startTrading", Player.class);
            method.setAccessible(true);
        } catch (NoSuchMethodException e) {
            helper.fail("Villager.startTrading not found — signature changed? " + e);
            throw new AssertionError(e);
        }
        try {
            method.invoke(villager, player);
        } catch (InvocationTargetException e) {
            helper.fail("Villager.startTrading threw: " + e.getCause());
            throw new AssertionError(e.getCause());
        } catch (IllegalAccessException e) {
            helper.fail("Could not invoke Villager.startTrading: " + e);
            throw new AssertionError(e);
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = FEAR_TIMEOUT_TICKS)
    public void fearDisabledRecordsNothing(GameTestHelper helper) {
        MercantileConfig config = MercantileConfig.get();
        placeBell(helper);
        Villager survivor = helper.spawn(EntityType.VILLAGER, 3, 1, 3);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        boolean saved = config.enableFearMarkup;
        config.enableFearMarkup = false;
        try {
            killVillagers(helper, player, config.fearKillThreshold);
        } finally {
            config.enableFearMarkup = saved;
        }

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(FearManager.fearFraction(survivor, player, config) == 0.0,
                    "Kills made while the feature was disabled should not be counted");
            player.discard();
            helper.succeed();
        });
    }
}
