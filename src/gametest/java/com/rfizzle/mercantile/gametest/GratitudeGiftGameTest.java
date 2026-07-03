package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.GratitudeGiftTables;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.mixin.ItemEntityAccessor;
import com.rfizzle.mercantile.reputation.GratitudeGiftManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GratitudeGiftGameTest implements FabricGameTest {

    private static final int HONORED_SCORE = 1000;
    private static final int TRUSTED_SCORE = 500;

    @GameTest(template = EMPTY_STRUCTURE)
    public void honoredPlayerReceivesGiftFromProfessionTable(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, VillagerProfession.FARMER);
        ServerPlayer player = makePreparedPlayer(helper, HONORED_SCORE);

        boolean given = GratitudeGiftManager.tryGiveGratitudeGift(player, List.of(villager));

        helper.assertTrue(given, "Honored player must receive a gratitude gift");
        List<ItemEntity> items = thrownItems(helper, villager);
        helper.assertFalse(items.isEmpty(), "A thrown gift item entity must exist");
        Set<Item> farmerItems = GratitudeGiftTables.getTableForProfession("farmer").stream()
                .map(GratitudeGiftTables.GiftEntry::item)
                .collect(Collectors.toSet());
        Item gifted = items.get(0).getItem().getItem();
        helper.assertTrue(farmerItems.contains(gifted),
                "Gift must come from the farmer table, got " + gifted);
        helper.assertTrue(player.getUUID().equals(((ItemEntityAccessor) items.get(0)).getTarget()),
                "Gift pickup must be locked to the receiving player");

        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        helper.assertTrue(data.getDailyGratitudeGifts() == 1,
                "Daily gift counter must be 1, got " + data.getDailyGratitudeGifts());

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void trustedPlayerNeverReceivesGift(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, VillagerProfession.FARMER);
        ServerPlayer player = makePreparedPlayer(helper, TRUSTED_SCORE);

        boolean given = GratitudeGiftManager.tryGiveGratitudeGift(player, List.of(villager));

        helper.assertFalse(given, "A Trusted-and-below player must never receive a gratitude gift");
        helper.assertTrue(thrownItems(helper, villager).isEmpty(), "No gift item must be thrown");
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        helper.assertTrue(data.getDailyGratitudeGifts() == 0,
                "Daily gift counter must stay 0, got " + data.getDailyGratitudeGifts());

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void perDayCapIsEnforced(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, VillagerProfession.FARMER);
        ServerPlayer player = makePreparedPlayer(helper, HONORED_SCORE);
        int cap = MercantileConfig.get().gratitudeGiftsPerDay;

        int given = 0;
        for (int i = 0; i < cap + 3; i++) {
            if (GratitudeGiftManager.tryGiveGratitudeGift(player, List.of(villager))) {
                given++;
            }
        }

        helper.assertTrue(given == cap, "Exactly " + cap + " gifts must be given, got " + given);
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        helper.assertTrue(data.getDailyGratitudeGifts() == cap,
                "Daily gift counter must equal the cap, got " + data.getDailyGratitudeGifts());

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unmappedProfessionFallsBackToGenericPool(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, VillagerProfession.NITWIT);
        ServerPlayer player = makePreparedPlayer(helper, HONORED_SCORE);

        boolean given = GratitudeGiftManager.tryGiveGratitudeGift(player, List.of(villager));

        helper.assertTrue(given, "Fallback pool must still produce a gift");
        List<ItemEntity> items = thrownItems(helper, villager);
        helper.assertFalse(items.isEmpty(), "A thrown gift item entity must exist");
        Set<Item> fallback = GratitudeGiftTables.fallbackPool().stream()
                .map(GratitudeGiftTables.GiftEntry::item)
                .collect(Collectors.toSet());
        Item gifted = items.get(0).getItem().getItem();
        helper.assertTrue(fallback.contains(gifted),
                "Gift must come from the fallback pool, got " + gifted);

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void disabledToggleGivesNothing(GameTestHelper helper) {
        boolean prev = MercantileConfig.get().enableGratitudeGifts;
        MercantileConfig.get().enableGratitudeGifts = false;
        try {
            Villager villager = spawnVillager(helper, VillagerProfession.FARMER);
            ServerPlayer player = makePreparedPlayer(helper, HONORED_SCORE);

            boolean given = GratitudeGiftManager.tryGiveGratitudeGift(player, List.of(villager));

            helper.assertFalse(given, "Disabled feature must never give a gift");
            player.discard();
            helper.succeed();
        } finally {
            MercantileConfig.get().enableGratitudeGifts = prev;
        }
    }

    private static Villager spawnVillager(GameTestHelper helper, VillagerProfession profession) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 1, 2, 1);
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, profession, 1));
        return villager;
    }

    private static ServerPlayer makePreparedPlayer(GameTestHelper helper, int score) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setReputationMigrated(true);
        data.setScore(score);
        long currentDay = player.serverLevel().getGameTime() / 24_000L;
        data.resetDailyCounters(currentDay);
        return player;
    }

    private static List<ItemEntity> thrownItems(GameTestHelper helper, Villager villager) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                villager.getBoundingBox().inflate(8.0));
    }
}
