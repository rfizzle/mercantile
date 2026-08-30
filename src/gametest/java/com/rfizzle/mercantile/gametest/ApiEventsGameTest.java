package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.api.ReputationChangedCallback;
import com.rfizzle.mercantile.api.TradeExecutedCallback;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.reputation.ReputationManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Verifies the api package's Fabric events: listener registration works and
 * each event fires from its documented trigger. Listeners are global (Fabric
 * events cannot be unregistered), so captures are filtered by player UUID —
 * gametests may run concurrently.
 */
public class ApiEventsGameTest implements FabricGameTest {

    private record ReputationChange(UUID player, int oldScore, int newScore) {
    }

    private record TradeExecution(UUID player, UUID merchant, MerchantOffer offer) {
    }

    private static final List<ReputationChange> REP_EVENTS = new CopyOnWriteArrayList<>();
    private static final List<TradeExecution> TRADE_EVENTS = new CopyOnWriteArrayList<>();

    static {
        // Registered ahead of the recorders: every capture below is therefore also proof that
        // a throwing listener is isolated inside the invoker and the listeners after it still
        // fire (API-STANDARD §3.1) — a fire-site wrap would abandon the recorders.
        ReputationChangedCallback.EVENT.register((player, oldScore, newScore) -> {
            throw new IllegalStateException("gametest: misbehaving ReputationChangedCallback listener");
        });
        TradeExecutedCallback.EVENT.register((player, merchant, offer) -> {
            throw new IllegalStateException("gametest: misbehaving TradeExecutedCallback listener");
        });
        ReputationChangedCallback.EVENT.register((player, oldScore, newScore) ->
                REP_EVENTS.add(new ReputationChange(player.getUUID(), oldScore, newScore)));
        TradeExecutedCallback.EVENT.register((player, merchant, offer) ->
                TRADE_EVENTS.add(new TradeExecution(player.getUUID(), merchant.getUUID(), offer)));
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void throwingListenerIsSkippedAndLoggedOnce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setReputationMigrated(true);
        data.setScore(0);

        // The thrower registered first must neither escape here nor starve the recorder.
        ReputationManager.modifyScore(player, 3);

        helper.assertTrue(repEventsFor(player).size() == 1,
                "the listener registered after the thrower must still see the change");
        helper.assertTrue(ReputationChangedCallback.LISTENER_FAILURE_LOGGED.get(),
                "the invoker must record the first listener failure so it logs exactly once");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void reputationChangedFiresOnManagerChange(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setReputationMigrated(true);
        data.setScore(0);

        ReputationManager.modifyScore(player, 5);

        List<ReputationChange> mine = repEventsFor(player);
        helper.assertTrue(mine.size() == 1,
                "expected exactly 1 ReputationChangedCallback, saw " + mine.size());
        helper.assertTrue(mine.get(0).oldScore() == 0 && mine.get(0).newScore() == 5,
                "expected change 0 -> 5, saw " + mine.get(0).oldScore() + " -> " + mine.get(0).newScore());

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void reputationChangedFiresOnCommandPath(GameTestHelper helper) {
        // /mercantile reputation set|add delegate to ReputationManager.setScore/addScore.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setReputationMigrated(true);
        data.setScore(0);

        ReputationManager.setScore(player, 250);
        ReputationManager.addScore(player, -50);

        List<ReputationChange> mine = repEventsFor(player);
        helper.assertTrue(mine.size() == 2,
                "expected 2 ReputationChangedCallbacks, saw " + mine.size());
        helper.assertTrue(mine.get(0).oldScore() == 0 && mine.get(0).newScore() == 250,
                "set: expected 0 -> 250, saw " + mine.get(0).oldScore() + " -> " + mine.get(0).newScore());
        helper.assertTrue(mine.get(1).oldScore() == 250 && mine.get(1).newScore() == 200,
                "add: expected 250 -> 200, saw " + mine.get(1).oldScore() + " -> " + mine.get(1).newScore());

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void reputationChangedNotFiredWhenClampAbsorbsChange(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        data.setReputationMigrated(true);
        data.setScore(PlayerData.MAX_SCORE);

        ReputationManager.modifyScore(player, 10); // clamped: score stays MAX_SCORE

        List<ReputationChange> mine = repEventsFor(player);
        helper.assertTrue(mine.isEmpty(),
                "no event expected when clamping absorbs the change, saw " + mine.size());

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tradeExecutedFiresForVillager(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.FARMER, 1));
        MerchantOffers offers = new MerchantOffers();
        offers.add(offer);
        villager.overrideOffers(offers);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        villager.setTradingPlayer(player);
        villager.notifyTrade(offer);
        villager.setTradingPlayer(null);

        List<TradeExecution> mine = tradeEventsFor(player);
        helper.assertTrue(mine.size() == 1,
                "expected exactly 1 TradeExecutedCallback, saw " + mine.size());
        helper.assertTrue(mine.get(0).merchant().equals(villager.getUUID()),
                "callback should carry the trading villager");
        helper.assertTrue(mine.get(0).offer() == offer,
                "callback should carry the executed offer");

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tradeExecutedFiresForWanderingTrader(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BLUE_DYE, 3), 16, 1, 0.0f);
        WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
        MerchantOffers offers = new MerchantOffers();
        offers.add(offer);
        trader.overrideOffers(offers);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        trader.setTradingPlayer(player);
        trader.notifyTrade(offer);
        trader.setTradingPlayer(null);

        List<TradeExecution> mine = tradeEventsFor(player);
        helper.assertTrue(mine.size() == 1,
                "expected exactly 1 TradeExecutedCallback for the wandering trader, saw " + mine.size());
        helper.assertTrue(mine.get(0).merchant().equals(trader.getUUID()),
                "callback should carry the trading wandering trader");

        player.discard();
        helper.succeed();
    }

    private static List<ReputationChange> repEventsFor(ServerPlayer player) {
        UUID id = player.getUUID();
        return REP_EVENTS.stream().filter(e -> e.player().equals(id)).toList();
    }

    private static List<TradeExecution> tradeEventsFor(ServerPlayer player) {
        UUID id = player.getUUID();
        return TRADE_EVENTS.stream().filter(e -> e.player().equals(id)).toList();
    }
}
