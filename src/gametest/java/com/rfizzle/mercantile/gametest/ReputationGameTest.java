package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager;
import com.rfizzle.mercantile.reputation.ReputationManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

public class ReputationGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void tradeGainsReputation(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = spawnTrader(helper, offer);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);

        villager.setTradingPlayer(player);
        for (int i = 0; i < 5; i++) {
            villager.notifyTrade(offer);
        }

        int expectedGain = 5 * MercantileConfig.get().reputationTradeGain;
        helper.assertTrue(data.getScore() == expectedGain,
                "Expected score " + expectedGain + " after 5 trades, got " + data.getScore());

        helper.assertTrue(data.getTradesWithVillager(villager.getUUID()) == 5,
                "Expected 5 trade stats, got " + data.getTradesWithVillager(villager.getUUID()));
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void attackLosesReputation(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);

        DamageSource source = player.damageSources().playerAttack(player);
        villager.hurt(source, 1.0f);

        int expected = -MercantileConfig.get().reputationAttackLoss;
        helper.assertTrue(data.getScore() == expected,
                "Expected score " + expected + " after attack, got " + data.getScore());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void killLosesReputation(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);

        DamageSource source = player.damageSources().playerAttack(player);
        villager.hurt(source, 1000.0f);

        int expected = -MercantileConfig.get().reputationKillLoss;
        helper.assertTrue(data.getScore() == expected,
                "Expected score " + expected + " after kill, got " + data.getScore());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void priceModifierCalculation(GameTestHelper helper) {
        int base = 20;
        int honored = ReputationManager.getPriceModifier(100, base);
        int trusted = ReputationManager.getPriceModifier(50, base);
        int liked = ReputationManager.getPriceModifier(1, base);
        int neutral = ReputationManager.getPriceModifier(0, base);
        int distrusted = ReputationManager.getPriceModifier(-25, base);
        int reviled = ReputationManager.getPriceModifier(-100, base);

        helper.assertTrue(honored < 0, "Honored should discount, got " + honored);
        helper.assertTrue(trusted < 0, "Trusted should discount, got " + trusted);
        helper.assertTrue(liked < 0, "Liked should discount, got " + liked);
        helper.assertTrue(neutral == 0, "Neutral should be 0, got " + neutral);
        helper.assertTrue(distrusted > 0, "Distrusted should markup, got " + distrusted);
        helper.assertTrue(reviled == 0, "Reviled returns 0 (trades refused), got " + reviled);

        helper.assertTrue(honored < trusted, "Honored discount > trusted discount");
        helper.assertTrue(trusted < liked, "Trusted discount > liked discount");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void reviledIsDetected(GameTestHelper helper) {
        helper.assertTrue(ReputationManager.isReviled(-50), "-50 should be reviled");
        helper.assertTrue(ReputationManager.isReviled(-100), "-100 should be reviled");
        helper.assertFalse(ReputationManager.isReviled(-49), "-49 should not be reviled");
        helper.assertFalse(ReputationManager.isReviled(0), "0 should not be reviled");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void exclusiveTradesInjectedByScore(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = spawnTrader(helper, offer);

        int baseCount = villager.getOffers().size();

        ExclusiveTradesManager.injectOffers(villager, 100);
        int honoredCount = villager.getOffers().size();

        ExclusiveTradesManager.stripInjectedOffers(villager);
        int afterStripCount = villager.getOffers().size();

        helper.assertTrue(honoredCount >= baseCount,
                "Honored tier should have >= base offers: base=" + baseCount + " honored=" + honoredCount);
        helper.assertTrue(afterStripCount == baseCount,
                "After strip should equal base: base=" + baseCount + " after=" + afterStripCount);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void exclusiveTradesDoNotAccumulate(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = spawnTrader(helper, offer);

        ExclusiveTradesManager.injectOffers(villager, 100);
        int firstCount = villager.getOffers().size();

        ExclusiveTradesManager.stripInjectedOffers(villager);
        ExclusiveTradesManager.injectOffers(villager, 100);
        int secondCount = villager.getOffers().size();

        helper.assertTrue(firstCount == secondCount,
                "Exclusive trades should not accumulate: first=" + firstCount + " second=" + secondCount);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void priceModifierDoesNotStackOnReopen(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 20), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = spawnTrader(helper, offer);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(100); // HONORED tier — produces negative modifier (discount)

        int basePrice = offer.getBaseCostA().getCount();
        int modifier = ReputationManager.getPriceModifier(data.getScore(), basePrice);
        helper.assertTrue(modifier != 0, "HONORED tier should produce non-zero modifier for base price 20");

        // Trigger VillagerTradeOpenMixin via the private startTrading method (the actual mixin target).
        // mobInteract can't re-enter while isTrading()=true, so we call it via reflection.
        // Between the two calls: closeContainer() properly tears down the MerchantMenu (avoiding
        // the spurious resetSpecialPrices() that openTradingScreen triggers when closing the old
        // menu), then we pre-set the diff to simulate the state left by the first open.
        // Absolute set → value stays at modifier; additive → value doubles to 2*modifier.
        java.lang.reflect.Method startTrading;
        try {
            startTrading = Villager.class.getDeclaredMethod("startTrading", Player.class);
            startTrading.setAccessible(true);
        } catch (NoSuchMethodException e) {
            helper.fail("Villager.startTrading(Player) not found — mixin target changed? " + e);
            return;
        }
        try {
            startTrading.invoke(villager, player);           // first open — applies modifier
            player.closeContainer();                         // tear down menu, resets diff to 0
            offer.setSpecialPriceDiff(modifier);             // pre-set: simulate first-open state
            startTrading.invoke(villager, player);           // second open — must not accumulate
        } catch (java.lang.reflect.InvocationTargetException e) {
            helper.fail("startTrading threw: " + e.getCause());
            return;
        } catch (IllegalAccessException e) {
            helper.fail("Could not invoke startTrading: " + e);
            return;
        }

        helper.assertTrue(offer.getSpecialPriceDiff() == modifier,
                "Price diff after two opens: expected=" + modifier
                        + " got=" + offer.getSpecialPriceDiff()
                        + " (stacked would be " + (modifier * 2) + ")");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void killingVillagerAppliesOnlyKillLoss(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);

        DamageSource source = player.damageSources().playerAttack(player);
        villager.hurt(source, 1000.0f); // lethal hit

        int expected = -MercantileConfig.get().reputationKillLoss;
        helper.assertTrue(data.getScore() == expected,
                "Kill should apply only kill loss: expected=" + expected
                        + " got=" + data.getScore()
                        + " (double-penalty would be " + -(MercantileConfig.get().reputationKillLoss + MercantileConfig.get().reputationAttackLoss) + ")");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void nonLethalAttackStillAppliesAttackLoss(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);

        DamageSource source = player.damageSources().playerAttack(player);
        villager.hurt(source, 1.0f); // non-lethal hit (villager has 20 HP base)

        helper.assertTrue(villager.isAlive(), "Villager should survive a 1 HP hit");
        int expected = -MercantileConfig.get().reputationAttackLoss;
        helper.assertTrue(data.getScore() == expected,
                "Non-lethal attack should still apply attack loss: expected=" + expected + " got=" + data.getScore());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void exclusiveTradesNotInjectedWhenReputationDisabled(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = spawnTrader(helper, offer);

        int baseCount = villager.getOffers().size();

        boolean saved = MercantileConfig.get().enableReputation;
        try {
            MercantileConfig.get().enableReputation = false;
            ExclusiveTradesManager.injectOffers(villager, 100);
            helper.assertTrue(villager.getOffers().size() == baseCount,
                    "No exclusive trades should be injected when enableReputation=false: "
                            + "base=" + baseCount + " after=" + villager.getOffers().size());
        } finally {
            MercantileConfig.get().enableReputation = saved;
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void proximityDayCapUsesGameTimeNotDayTime(GameTestHelper helper) {
        // Constants must match ReputationManager private fields
        final int CHECK_INTERVAL = 20;
        final int THRESHOLD = 12_000;

        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // Teleport player to the villager so it falls within PROXIMITY_RANGE (16 blocks)
        player.teleportTo(villager.getX(), villager.getY(), villager.getZ());

        ServerLevel level = helper.getLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setScore(0);

        // Mark "already awarded today" using the getGameTime()-based day (correct implementation)
        long todayByGameTime = level.getGameTime() / 24_000L;
        data.setLastProximityDay(todayByGameTime);
        data.setProximityTicks(THRESHOLD - CHECK_INTERVAL); // one interval away from threshold

        // Advance getDayTime() by 2 full days without touching getGameTime().
        // This mirrors a /time add or /time set that jumps the clock forward.
        // getDayTime()/24000 now returns 2, but getGameTime()/24000 is still todayByGameTime.
        level.setDayTime(2L * 24_000L + 1L);

        java.lang.reflect.Method tickProximity;
        try {
            tickProximity = ReputationManager.class.getDeclaredMethod("tickProximity", ServerPlayer.class);
            tickProximity.setAccessible(true);
        } catch (NoSuchMethodException e) {
            helper.fail("ReputationManager.tickProximity(ServerPlayer) not found — signature changed? " + e);
            return;
        }
        try {
            tickProximity.invoke(null, player);
        } catch (java.lang.reflect.InvocationTargetException e) {
            helper.fail("tickProximity threw: " + e.getCause());
            return;
        } catch (IllegalAccessException e) {
            helper.fail("Could not invoke tickProximity: " + e);
            return;
        }

        // With getGameTime(): currentDay == todayByGameTime → guard (lastProximityDay >= currentDay) holds → score unchanged (correct)
        // With getDayTime(): currentDay = 2 > todayByGameTime → guard fails → score increments (bug)
        helper.assertTrue(data.getScore() == 0,
                "Day cap must use getGameTime(), not getDayTime(): score should stay 0 but got "
                        + data.getScore()
                        + " (getDayTime()-based code awards again because getDayTime()/24000="
                        + (level.getDayTime() / 24_000L) + " > lastProximityDay=" + todayByGameTime + ")");

        helper.succeed();
    }

    private Villager spawnTrader(GameTestHelper helper, MerchantOffer offer) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.FARMER, 1));
        MerchantOffers offers = new MerchantOffers();
        offers.add(offer);
        villager.overrideOffers(offers);
        return villager;
    }
}
