package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.MercantileVillagerData;
import com.rfizzle.mercantile.mood.MoodManager;
import com.rfizzle.mercantile.mood.MoodMath;
import com.rfizzle.mercantile.mood.MoodTier;
import com.rfizzle.mercantile.network.DemandPriceS2CPayload;
import com.rfizzle.mercantile.trade.PriceBreakdownBuilder;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.List;
import java.util.UUID;

public class MoodGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void freshVillagerStartsContent(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        helper.assertTrue(MoodManager.getMood(villager) == MoodMath.DEFAULT_MOOD,
                "fresh villager mood should start at the neutral default; got " + MoodManager.getMood(villager));
        helper.assertTrue(MoodManager.tier(villager) == MoodTier.CONTENT,
                "fresh villager should be Content");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void goodConditionsReachTopTierOverTime(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerLevel level = helper.getLevel();
        giveGoodConditions(helper, villager);

        // Backdate the last evaluation far enough that the drift fully converges.
        MercantileVillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        data.setLastMoodUpdateTime(level.getGameTime() - 1_000_000L);

        helper.assertTrue(MoodManager.getMood(villager) == MoodMath.MAX_MOOD,
                "bed + workstation + sleep + food should converge to 100; got " + MoodManager.getMood(villager));
        helper.assertTrue(MoodManager.tier(villager) == MoodTier.HAPPY,
                "villager with all conditions met should be Happy");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void strippedConditionsDropTowardBottomTier(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerLevel level = helper.getLevel();

        // No bed/workstation/sleep/food target is 20 (Miserable) once fully drifted.
        MercantileVillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        data.setMood(MoodMath.MAX_MOOD);
        data.setLastMoodUpdateTime(level.getGameTime() - 1_000_000L);

        int mood = MoodManager.getMood(villager);
        helper.assertTrue(mood == 20,
                "with no conditions met mood should converge to 20; got " + mood);
        helper.assertTrue(MoodManager.tier(villager) == MoodTier.MISERABLE,
                "villager with nothing should end up Miserable");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void driftIsGradualNotInstant(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerLevel level = helper.getLevel();
        giveGoodConditions(helper, villager);

        MercantileVillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        int interval = MercantileConfig.get().moodRecalcIntervalTicks;
        data.setMood(MoodMath.MIN_MOOD);
        data.setLastMoodUpdateTime(level.getGameTime() - interval);

        int mood = MoodManager.getMood(villager);
        helper.assertTrue(mood == MoodMath.DRIFT_PER_RECALC,
                "one interval should move mood by exactly the drift step; got " + mood);

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void moodPriceModifierAppearsInBreakdown(GameTestHelper helper) {
        Villager villager = spawnTrader(helper, 20);
        ServerPlayer player = directPlayer(helper);
        player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA).setScore(0);

        MercantileVillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        data.setLastMoodUpdateTime(helper.getLevel().getGameTime());

        data.setMood(MoodMath.MAX_MOOD);
        List<DemandPriceS2CPayload.PriceComponent> happy = PriceBreakdownBuilder.buildFor(villager, player);
        helper.assertTrue(happy.get(0).moodModifier() < 0,
                "Happy villager should discount; got " + happy.get(0).moodModifier());

        data.setMood(MoodMath.MIN_MOOD);
        List<DemandPriceS2CPayload.PriceComponent> miserable = PriceBreakdownBuilder.buildFor(villager, player);
        helper.assertTrue(miserable.get(0).moodModifier() > 0,
                "Miserable villager should mark up; got " + miserable.get(0).moodModifier());

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void moodDisabledZeroesEffects(GameTestHelper helper) {
        Villager villager = spawnTrader(helper, 20);
        ServerPlayer player = directPlayer(helper);
        player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA).setScore(0);

        MercantileVillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        data.setMood(MoodMath.MAX_MOOD);
        data.setLastMoodUpdateTime(helper.getLevel().getGameTime());

        MercantileConfig config = MercantileConfig.get();
        boolean saved = config.enableMood;
        try {
            config.enableMood = false;
            DemandPriceS2CPayload.PriceComponent c = PriceBreakdownBuilder.buildFor(villager, player).get(0);
            helper.assertTrue(c.moodModifier() == 0,
                    "moodModifier should be 0 when mood is disabled; got " + c.moodModifier());
            long interval = MoodManager.restockIntervalTicks(villager, MoodMath.BASE_RESTOCK_INTERVAL_TICKS);
            helper.assertTrue(interval == MoodMath.BASE_RESTOCK_INTERVAL_TICKS,
                    "restock interval should be vanilla when mood is disabled; got " + interval);
        } finally {
            config.enableMood = saved;
        }

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void happyVillagerRestocksSooner(GameTestHelper helper) {
        Villager villager = spawnTrader(helper, 20);
        MercantileVillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        data.setLastMoodUpdateTime(helper.getLevel().getGameTime());

        data.setMood(MoodMath.MAX_MOOD);
        long happyInterval = MoodManager.restockIntervalTicks(villager, MoodMath.BASE_RESTOCK_INTERVAL_TICKS);
        data.setMood(MoodMath.MIN_MOOD);
        long miserableInterval = MoodManager.restockIntervalTicks(villager, MoodMath.BASE_RESTOCK_INTERVAL_TICKS);

        helper.assertTrue(happyInterval < MoodMath.BASE_RESTOCK_INTERVAL_TICKS,
                "Happy villager should restock sooner than vanilla; got " + happyInterval);
        helper.assertTrue(miserableInterval > MoodMath.BASE_RESTOCK_INTERVAL_TICKS,
                "Miserable villager should restock later than vanilla; got " + miserableInterval);

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void moodStatePersistsThroughNbt(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        MercantileVillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        data.setMood(87);
        data.setLastMoodUpdateTime(5_000L);
        data.setLastHurtGameTime(4_000L);
        data.setLastWitnessedDeathGameTime(3_000L);

        CompoundTag saved = new CompoundTag();
        villager.saveWithoutId(saved);
        villager.discard();

        Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(loaded != null, "villager entity should be created");
        loaded.load(saved);

        MercantileVillagerData loadedData = loaded.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        helper.assertTrue(loadedData.getMood() == 87,
                "mood should survive save/load; got " + loadedData.getMood());
        helper.assertTrue(loadedData.getLastMoodUpdateTime() == 5_000L,
                "lastMoodUpdateTime should survive save/load");
        helper.assertTrue(loadedData.getLastHurtGameTime() == 4_000L,
                "lastHurtGameTime should survive save/load");
        helper.assertTrue(loadedData.getLastWitnessedDeathGameTime() == 3_000L,
                "lastWitnessedDeathGameTime should survive save/load");

        loaded.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void witnessedDeathMarksNearbyVillagers(GameTestHelper helper) {
        Villager victim = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        Villager witness = helper.spawn(EntityType.VILLAGER, 2, 1, 2);

        victim.kill();

        MercantileVillagerData witnessData = witness.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        helper.assertTrue(witnessData.getLastWitnessedDeathGameTime() >= 0,
                "nearby villager should record the witnessed death");

        witness.discard();
        helper.succeed();
    }

    private static void giveGoodConditions(GameTestHelper helper, Villager villager) {
        ServerLevel level = helper.getLevel();
        long now = level.getGameTime();
        villager.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(1, 1, 1))));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(2, 1, 2))));
        villager.getBrain().setMemory(MemoryModuleType.LAST_SLEPT, now);
        villager.getInventory().addItem(new ItemStack(Items.BREAD, 3)); // 12 food points
    }

    private static Villager spawnTrader(GameTestHelper helper, int basePrice) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(
                new ItemCost(Items.EMERALD, basePrice), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f));
        // Villager.setOffers actually persists; AbstractVillager.overrideOffers is a vanilla no-op.
        villager.setOffers(offers);
        return villager;
    }

    // Direct ServerPlayer construction avoids PlayerList.placeNewPlayer, which schedules
    // server work that ticks the villager's brain and wipes overridden offers.
    private static ServerPlayer directPlayer(GameTestHelper helper) {
        return new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "TestPlayer"),
                ClientInformation.createDefault());
    }
}
