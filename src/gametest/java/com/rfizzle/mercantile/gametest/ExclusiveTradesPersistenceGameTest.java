package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.concord.gametest.MockPlayers;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager.ExclusiveTrade;
import com.rfizzle.mercantile.reputation.ReputationManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Reputation-gated (exclusive) offers are injected into the merchant's real offer list for the
 * duration of a trade session. They must never reach saved data and must not survive the session.
 */
public class ExclusiveTradesPersistenceGameTest implements FabricGameTest {

    private static final int TRUSTED = ReputationTier.TRUSTED.minScore();

    private static ExclusiveTrade gatedTrade() {
        return new ExclusiveTrade(new ItemCost(Items.EMERALD, 5), null,
                new ItemStack(Items.DIAMOND, 1), 8, 1, 0.05f, TRUSTED);
    }

    private static void withTrades(String profession, Runnable body) {
        boolean saved = MercantileConfig.get().enableReputation;
        MercantileConfig.get().enableReputation = true;
        ExclusiveTradesManager.setSnapshotsForTesting(Map.of(profession, List.of(gatedTrade())), List.of());
        try {
            body.run();
        } finally {
            MercantileConfig.get().enableReputation = saved;
            ExclusiveTradesManager.setSnapshotsForTesting(Map.of(), List.of());
        }
    }

    private static Villager spawnMason(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.MASON, 1));
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f));
        villager.setOffers(offers);
        return villager;
    }

    private static void startTrading(Villager villager, ServerPlayer player) {
        try {
            Method m = Villager.class.getDeclaredMethod("startTrading", Player.class);
            m.setAccessible(true);
            m.invoke(villager, player);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static int diamondOffers(MerchantOffers offers) {
        int n = 0;
        for (MerchantOffer o : offers) if (o.getResult().is(Items.DIAMOND)) n++;
        return n;
    }

    /** Result-item count among the offers an entity would persist, read from real NBT. */
    private static int persistedDiamondOffers(GameTestHelper helper, AbstractVillager merchant) {
        CompoundTag tag = new CompoundTag();
        merchant.saveWithoutId(tag);
        return persistedDiamondOffers(tag);
    }

    private static int persistedDiamondOffers(CompoundTag tag) {
        int n = 0;
        for (var e : tag.getCompound("Offers").getList("Recipes", 10)) {
            if (((CompoundTag) e).getCompound("sell").getString("id").equals("minecraft:diamond")) n++;
        }
        return n;
    }

    /** Full save then load into the same entity, as a chunk unload/reload would round-trip it. */
    private static void reload(AbstractVillager merchant) {
        CompoundTag tag = new CompoundTag();
        merchant.saveWithoutId(tag);
        merchant.load(tag);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void villagerCloseThenReloadLeavesNoExclusiveOffers(GameTestHelper helper) {
        withTrades("mason", () -> {
            Villager villager = spawnMason(helper);
            ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
            ReputationManager.setScore(player, TRUSTED);
            try {
                startTrading(villager, player);
                helper.assertTrue(diamondOffers(villager.getOffers()) == 1,
                        "trusted player should be offered the gated trade during the session");
                player.closeContainer();
                helper.assertTrue(diamondOffers(villager.getOffers()) == 0,
                        "closing the trade screen must remove the injected offer");
                helper.assertTrue(persistedDiamondOffers(helper, villager) == 0,
                        "persisted offers must not contain the exclusive trade");
                reload(villager);
                helper.assertTrue(diamondOffers(villager.getOffers()) == 0,
                        "reloaded villager must not carry the exclusive trade");
                helper.assertTrue(villager.getOffers().size() == 1, "vanilla offer must survive");
            } finally {
                MockPlayers.retire(player);
                villager.discard();
            }
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void villagerMidSessionSaveExcludesInjectedAndKeepsLiveOffers(GameTestHelper helper) {
        withTrades("mason", () -> {
            Villager villager = spawnMason(helper);
            ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
            ReputationManager.setScore(player, TRUSTED);
            try {
                startTrading(villager, player);
                helper.assertTrue(persistedDiamondOffers(helper, villager) == 0,
                        "a mid-session save must not write the injected offer");
                helper.assertTrue(diamondOffers(villager.getOffers()) == 1,
                        "the live session must keep its injected offer after the save");
                helper.assertTrue(villager.getOffers().get(1).getResult().is(Items.DIAMOND),
                        "the injected offer must be restored at its original position");
                player.closeContainer();
                helper.assertTrue(diamondOffers(villager.getOffers()) == 0, "close still strips after a save");
            } finally {
                MockPlayers.retire(player);
                villager.discard();
            }
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void villagerRepeatedOpenReloadCyclesDoNotDuplicate(GameTestHelper helper) {
        withTrades("mason", () -> {
            Villager villager = spawnMason(helper);
            ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
            ReputationManager.setScore(player, TRUSTED);
            try {
                for (int i = 0; i < 3; i++) {
                    startTrading(villager, player);
                    helper.assertTrue(diamondOffers(villager.getOffers()) == 1,
                            "cycle " + i + ": exactly one gated offer during the session, got "
                                    + diamondOffers(villager.getOffers()));
                    reload(villager);   // save lands mid-session
                    player.closeContainer();
                    reload(villager);   // and again after close
                    helper.assertTrue(diamondOffers(villager.getOffers()) == 0,
                            "cycle " + i + ": no gated offer between sessions");
                }
                helper.assertTrue(villager.getOffers().size() == 1, "only the vanilla offer remains");
            } finally {
                MockPlayers.retire(player);
                villager.discard();
            }
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void lowReputationPlayerNeverSeesGatedOfferAfterReload(GameTestHelper helper) {
        withTrades("mason", () -> {
            Villager villager = spawnMason(helper);
            ServerPlayer trusted = MockPlayers.serverPlayerInLevel(helper);
            ServerPlayer neutral = MockPlayers.serverPlayerInLevel(helper);
            ReputationManager.setScore(trusted, TRUSTED);
            ReputationManager.setScore(neutral, 0);
            try {
                startTrading(villager, trusted);
                trusted.closeContainer();
                reload(villager);
                startTrading(villager, neutral);
                helper.assertTrue(diamondOffers(villager.getOffers()) == 0,
                        "a neutral player must not see the gated offer after another player's session + reload");
                neutral.closeContainer();
            } finally {
                MockPlayers.retire(trusted);
                MockPlayers.retire(neutral);
                villager.discard();
            }
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void legacyLeakedExclusiveOffersArePurgedOnOpen(GameTestHelper helper) {
        withTrades("mason", () -> {
            Villager villager = spawnMason(helper);
            // A 1.1.0 world: the gated offer leaked into the saved offers, then duplicated.
            villager.getOffers().add(gatedTrade().createOffer());
            villager.getOffers().add(gatedTrade().createOffer());
            ServerPlayer neutral = MockPlayers.serverPlayerInLevel(helper);
            ReputationManager.setScore(neutral, 0);
            try {
                startTrading(villager, neutral);
                helper.assertTrue(diamondOffers(villager.getOffers()) == 0,
                        "leaked exclusive offers must be removed for a low-reputation player");
                helper.assertTrue(villager.getOffers().size() == 1, "vanilla offer must survive the purge");
                neutral.closeContainer();
            } finally {
                MockPlayers.retire(neutral);
                villager.discard();
            }
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void wanderingTraderCloseAndMidSessionSaveLeaveNoExclusiveOffer(GameTestHelper helper) {
        withTrades("wandering_trader", () -> {
            WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
            ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
            ReputationManager.setScore(player, TRUSTED);
            int vanilla = trader.getOffers().size();
            try {
                for (int i = 0; i < 3; i++) {
                    trader.interact(player, InteractionHand.MAIN_HAND);
                    helper.assertTrue(diamondOffers(trader.getOffers()) == 1,
                            "cycle " + i + ": trusted player sees the gated offer during the session");
                    helper.assertTrue(persistedDiamondOffers(helper, trader) == 0,
                            "cycle " + i + ": mid-session save must not persist it");
                    helper.assertTrue(diamondOffers(trader.getOffers()) == 1,
                            "cycle " + i + ": live offer restored after save");
                    player.closeContainer();
                    helper.assertTrue(diamondOffers(trader.getOffers()) == 0,
                            "cycle " + i + ": close strips the gated offer");
                    reload(trader);
                    helper.assertTrue(trader.getOffers().size() == vanilla,
                            "cycle " + i + ": only vanilla offers after reload");
                }
            } finally {
                MockPlayers.retire(player);
                trader.discard();
            }
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void wanderingTraderLegacyLeakedOfferIsPurgedForLowReputation(GameTestHelper helper) {
        withTrades("wandering_trader", () -> {
            WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, 0, 1, 0);
            int vanilla = trader.getOffers().size();
            trader.getOffers().add(gatedTrade().createOffer());
            ServerPlayer neutral = MockPlayers.serverPlayerInLevel(helper);
            ReputationManager.setScore(neutral, 0);
            try {
                trader.interact(neutral, InteractionHand.MAIN_HAND);
                helper.assertTrue(diamondOffers(trader.getOffers()) == 0, "leaked offer purged");
                helper.assertTrue(trader.getOffers().size() == vanilla, "vanilla offers intact");
                neutral.closeContainer();
            } finally {
                MockPlayers.retire(neutral);
                trader.discard();
            }
        });
        helper.succeed();
    }

}
