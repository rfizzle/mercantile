package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.mixin.VillagerRestockAccessor;
import com.rfizzle.mercantile.network.RestockTimerS2CPayload;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class RestockIndicatorGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void payloadRoundTripsCorrectFields(GameTestHelper helper) {
        RestockTimerS2CPayload original = new RestockTimerS2CPayload(
                42, 123456789012345L, 1, true, 2400);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RestockTimerS2CPayload.CODEC.encode(buf, original);
            RestockTimerS2CPayload decoded = RestockTimerS2CPayload.CODEC.decode(buf);
            helper.assertTrue(decoded.villagerEntityId() == 42,
                    "entityId should round-trip; got " + decoded.villagerEntityId());
            helper.assertTrue(decoded.lastRestockGameTime() == 123456789012345L,
                    "lastRestockGameTime should round-trip as long; got " + decoded.lastRestockGameTime());
            helper.assertTrue(decoded.restockCountToday() == 1,
                    "restockCountToday should round-trip; got " + decoded.restockCountToday());
            helper.assertTrue(decoded.hasWorkstation(),
                    "hasWorkstation should round-trip as true");
        } finally {
            buf.release();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void accessorReadsLastRestockGameTime(GameTestHelper helper) {
        Villager villager = spawnTrader(helper);
        VillagerRestockAccessor accessor = (VillagerRestockAccessor) villager;

        helper.assertTrue(accessor.mercantile$getLastRestockGameTime() == 0L,
                "default lastRestockGameTime should be 0; got " + accessor.mercantile$getLastRestockGameTime());

        villager.restock();
        long after = accessor.mercantile$getLastRestockGameTime();
        long levelTime = helper.getLevel().getGameTime();
        helper.assertTrue(after == levelTime,
                "lastRestockGameTime should equal current gametime " + levelTime + " after restock; got " + after);

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void accessorReadsRestocksToday(GameTestHelper helper) {
        Villager villager = spawnTrader(helper);
        VillagerRestockAccessor accessor = (VillagerRestockAccessor) villager;

        helper.assertTrue(accessor.mercantile$getNumberOfRestocksToday() == 0,
                "default numberOfRestocksToday should be 0");

        villager.restock();
        helper.assertTrue(accessor.mercantile$getNumberOfRestocksToday() == 1,
                "after one restock, count should be 1; got " + accessor.mercantile$getNumberOfRestocksToday());

        villager.restock();
        helper.assertTrue(accessor.mercantile$getNumberOfRestocksToday() == 2,
                "after two restocks, count should be 2; got " + accessor.mercantile$getNumberOfRestocksToday());

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void hasWorkstationTrueWhenJobSiteMemoryPresent(GameTestHelper helper) {
        Villager villager = spawnTrader(helper);
        ServerLevel level = helper.getLevel();

        helper.assertFalse(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent(),
                "spawned villager should have no JOB_SITE memory by default");

        BlockPos workstationAbs = helper.absolutePos(new BlockPos(2, 1, 2));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                GlobalPos.of(level.dimension(), workstationAbs));
        helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent(),
                "after setMemory, JOB_SITE should be present");

        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        helper.assertFalse(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent(),
                "after eraseMemory, JOB_SITE should be absent");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tradeOpenSendsPayloadWithCorrectData(GameTestHelper helper) {
        Villager villager = spawnTrader(helper);
        ServerLevel level = helper.getLevel();
        BlockPos workstationAbs = helper.absolutePos(new BlockPos(2, 1, 2));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                GlobalPos.of(level.dimension(), workstationAbs));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(villager.getX(), villager.getY(), villager.getZ());

        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        channel.outboundMessages().clear();

        try {
            invokeStartTrading(helper, villager, player);

            int count = GametestNetUtil.countPayloads(channel, RestockTimerS2CPayload.class);
            helper.assertTrue(count == 1,
                    "expected exactly one RestockTimerS2CPayload after startTrading; got " + count);

            RestockTimerS2CPayload payload = GametestNetUtil.findUniquePayload(
                    helper, channel, RestockTimerS2CPayload.class);
            helper.assertTrue(payload.villagerEntityId() == villager.getId(),
                    "payload entityId should match villager; got " + payload.villagerEntityId());
            helper.assertTrue(payload.hasWorkstation(),
                    "hasWorkstation should be true when JOB_SITE memory is set");
            helper.assertTrue(payload.restockCountToday() == 0,
                    "restockCountToday should be 0 by default; got " + payload.restockCountToday());
        } finally {
            player.closeContainer();
            player.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tradeOpenSendsHasWorkstationFalseWithoutJobSite(GameTestHelper helper) {
        Villager villager = spawnTrader(helper);
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(villager.getX(), villager.getY(), villager.getZ());

        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        channel.outboundMessages().clear();

        try {
            invokeStartTrading(helper, villager, player);

            RestockTimerS2CPayload payload = GametestNetUtil.findUniquePayload(
                    helper, channel, RestockTimerS2CPayload.class);
            helper.assertFalse(payload.hasWorkstation(),
                    "hasWorkstation should be false when no JOB_SITE memory");
        } finally {
            player.closeContainer();
            player.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void featureDisabledSendsNoPayload(GameTestHelper helper) {
        Villager villager = spawnTrader(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(villager.getX(), villager.getY(), villager.getZ());

        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        channel.outboundMessages().clear();

        MercantileConfig config = MercantileConfig.get();
        boolean saved = config.enableRestockIndicator;
        try {
            config.enableRestockIndicator = false;
            invokeStartTrading(helper, villager, player);

            int count = GametestNetUtil.countPayloads(channel, RestockTimerS2CPayload.class);
            helper.assertTrue(count == 0,
                    "no RestockTimerS2CPayload should be sent when feature disabled; got " + count);
        } finally {
            config.enableRestockIndicator = saved;
            player.closeContainer();
            player.discard();
            villager.discard();
        }
        helper.succeed();
    }

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

    private Villager spawnTrader(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(new VillagerData(
                VillagerType.PLAINS, VillagerProfession.FARMER, 1));
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f));
        villager.overrideOffers(offers);
        return villager;
    }
}
