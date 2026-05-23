package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.MercantileVillagerData;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.network.VillagerInfoPanelS2CPayload;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class InfoPanelGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void hasWorkstationMirrorsJobSiteMemory(GameTestHelper helper) {
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
    public void professionLockedMirrorsAttachment(GameTestHelper helper) {
        Villager villager = spawnTrader(helper);

        MercantileVillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        helper.assertFalse(data.isProfessionLocked(),
                "freshly attached villager data should not be locked");

        data.setProfessionLocked(true);
        helper.assertTrue(
                villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA).isProfessionLocked(),
                "setProfessionLocked(true) should be reflected on subsequent reads");

        data.setProfessionLocked(false);
        helper.assertFalse(
                villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA).isProfessionLocked(),
                "setProfessionLocked(false) should be reflected on subsequent reads");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void totalTradesReflectsPlayerVillagerHistory(GameTestHelper helper) {
        Villager villager = spawnTrader(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(villager.getX(), villager.getY(), villager.getZ());

        try {
            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            helper.assertTrue(data.getTradesWithVillager(villager.getUUID()) == 0,
                    "fresh player data should have 0 trades with new villager; got "
                            + data.getTradesWithVillager(villager.getUUID()));

            data.incrementTradesWithVillager(villager.getUUID());
            data.incrementTradesWithVillager(villager.getUUID());
            data.incrementTradesWithVillager(villager.getUUID());

            int count = data.getTradesWithVillager(villager.getUUID());
            helper.assertTrue(count == 3,
                    "after 3 increments, count should be 3; got " + count);
        } finally {
            player.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void xpToNextLevelMatchesVanilla(GameTestHelper helper) {
        // Confirms the payload would carry the vanilla xp threshold. If Mojang changes
        // the thresholds, the panel field stays wired to the right value.
        int[] expected = {10, 70, 150, 250};
        for (int level = 1; level <= 4; level++) {
            int got = VillagerData.getMaxXpPerLevel(level);
            helper.assertTrue(got == expected[level - 1],
                    "level " + level + " xp threshold should be " + expected[level - 1] + "; got " + got);
        }
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

        // Seed history so we can assert that totalTrades reads PlayerData, not getOffers().size().
        PlayerData playerData = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        playerData.incrementTradesWithVillager(villager.getUUID());
        playerData.incrementTradesWithVillager(villager.getUUID());

        // Lock the profession so we can assert the lock bit propagates.
        villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA).setProfessionLocked(true);

        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        channel.outboundMessages().clear();

        try {
            invokeStartTrading(helper, villager, player);

            VillagerInfoPanelS2CPayload payload = GametestNetUtil.findUniquePayload(
                    helper, channel, VillagerInfoPanelS2CPayload.class);
            helper.assertTrue(payload.villagerEntityId() == villager.getId(),
                    "payload entityId should match villager; got " + payload.villagerEntityId());
            helper.assertTrue("farmer".equals(payload.profession()),
                    "profession should be 'farmer'; got '" + payload.profession() + "'");
            helper.assertTrue(payload.level() == 1,
                    "level should be 1; got " + payload.level());
            helper.assertTrue(payload.xpToNextLevel() == VillagerData.getMaxXpPerLevel(1),
                    "xpToNextLevel should match VillagerData.getMaxXpPerLevel(1); got "
                            + payload.xpToNextLevel());
            helper.assertTrue(payload.hasWorkstation(),
                    "hasWorkstation should be true when JOB_SITE memory is set");
            helper.assertTrue(payload.professionLocked(),
                    "professionLocked should be true after setProfessionLocked(true)");
            helper.assertTrue(payload.totalTrades() == 2,
                    "totalTrades should reflect PlayerData history (2); got " + payload.totalTrades());
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
        boolean saved = config.enableInfoPanel;
        try {
            config.enableInfoPanel = false;
            invokeStartTrading(helper, villager, player);

            int count = GametestNetUtil.countPayloads(channel, VillagerInfoPanelS2CPayload.class);
            helper.assertTrue(count == 0,
                    "no VillagerInfoPanelS2CPayload should be sent when feature disabled; got " + count);
        } finally {
            config.enableInfoPanel = saved;
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
