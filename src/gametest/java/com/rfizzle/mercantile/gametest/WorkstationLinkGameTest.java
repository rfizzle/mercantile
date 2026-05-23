package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.MercantileNetworking;
import com.rfizzle.mercantile.network.WorkstationMapS2CPayload;
import com.rfizzle.mercantile.visualization.WorkstationMapService;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class WorkstationLinkGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void boundVillagerProducesEntry(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));

        BlockPos workstationRel = new BlockPos(2, 1, 2);
        BlockPos workstationAbs = helper.absolutePos(workstationRel);
        helper.setBlock(workstationRel, Blocks.COMPOSTER);
        ServerLevel level = helper.getLevel();
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                GlobalPos.of(level.dimension(), workstationAbs));

        WorkstationMapS2CPayload payload = WorkstationMapService.build(level, villager.blockPosition());

        helper.assertTrue(payload.bound().containsKey(villager.getUUID()),
                "bound map should contain villager UUID; got bound=" + payload.bound());
        helper.assertTrue(workstationAbs.equals(payload.bound().get(villager.getUUID())),
                "bound entry should point to workstation pos; got " + payload.bound().get(villager.getUUID()));
        helper.assertFalse(payload.unboundVillagers().contains(villager.getUUID()),
                "bound villager should not appear in unboundVillagers");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unboundVillagerFlaggedInPayload(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);

        WorkstationMapS2CPayload payload = WorkstationMapService.build(
                helper.getLevel(), villager.blockPosition());

        helper.assertTrue(payload.unboundVillagers().contains(villager.getUUID()),
                "unbound villager should appear in unboundVillagers");
        helper.assertFalse(payload.bound().containsKey(villager.getUUID()),
                "unbound villager should not appear in bound map");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unclaimedWorkstationDetected(GameTestHelper helper) {
        BlockPos workstationRel = new BlockPos(2, 1, 2);
        BlockPos workstationAbs = helper.absolutePos(workstationRel);
        helper.setBlock(workstationRel, Blocks.COMPOSTER);

        WorkstationMapS2CPayload payload = WorkstationMapService.build(
                helper.getLevel(), workstationAbs);

        helper.assertTrue(payload.unclaimedWorkstations().contains(workstationAbs),
                "unclaimed composter should appear in unclaimedWorkstations; got "
                        + payload.unclaimedWorkstations());
        helper.assertTrue(payload.bound().isEmpty(),
                "no villagers were spawned — bound should be empty");

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void boundWorkstationExcludedFromUnclaimed(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));

        BlockPos workstationRel = new BlockPos(2, 1, 2);
        BlockPos workstationAbs = helper.absolutePos(workstationRel);
        helper.setBlock(workstationRel, Blocks.COMPOSTER);
        ServerLevel level = helper.getLevel();
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                GlobalPos.of(level.dimension(), workstationAbs));

        WorkstationMapS2CPayload payload = WorkstationMapService.build(level, villager.blockPosition());

        helper.assertTrue(payload.bound().containsKey(villager.getUUID()),
                "villager should be in bound map");
        helper.assertFalse(payload.unclaimedWorkstations().contains(workstationAbs),
                "bound workstation must not also appear as unclaimed; got unclaimed="
                        + payload.unclaimedWorkstations());

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void outOfRangeVillagerExcluded(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        BlockPos farOrigin = villager.blockPosition().offset(
                WorkstationMapService.QUERY_RADIUS * 4, 0, 0);

        WorkstationMapS2CPayload payload = WorkstationMapService.build(helper.getLevel(), farOrigin);

        helper.assertFalse(payload.bound().containsKey(villager.getUUID()),
                "villager beyond QUERY_RADIUS should not appear in bound");
        helper.assertFalse(payload.unboundVillagers().contains(villager.getUUID()),
                "villager beyond QUERY_RADIUS should not appear in unboundVillagers");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void crossDimensionalJobSiteTreatedAsUnbound(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        ServerLevel level = helper.getLevel();
        ResourceKey<Level> otherDim = level.dimension() == Level.OVERWORLD ? Level.NETHER : Level.OVERWORLD;
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                GlobalPos.of(otherDim, new BlockPos(0, 64, 0)));

        WorkstationMapS2CPayload payload = WorkstationMapService.build(level, villager.blockPosition());

        helper.assertTrue(payload.unboundVillagers().contains(villager.getUUID()),
                "villager with cross-dimensional JOB_SITE should be treated as unbound");
        helper.assertFalse(payload.bound().containsKey(villager.getUUID()),
                "villager with cross-dimensional JOB_SITE should not appear in bound map");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void babyVillagerSkipped(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.setBaby(true);

        WorkstationMapS2CPayload payload = WorkstationMapService.build(
                helper.getLevel(), villager.blockPosition());

        helper.assertFalse(payload.bound().containsKey(villager.getUUID()),
                "baby villager should not appear in bound map");
        helper.assertFalse(payload.unboundVillagers().contains(villager.getUUID()),
                "baby villager should not appear in unboundVillagers");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void disabledByConfigReturnsEarly(GameTestHelper helper) {
        // Spawn a villager so the service WOULD produce a non-empty payload if invoked.
        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(villager.getX(), villager.getY(), villager.getZ());

        EmbeddedChannel channel = extractEmbeddedChannel(helper, player);
        // Drain anything that may have queued during placeNewPlayer.
        channel.outboundMessages().clear();

        Method handler = locateHandler(helper);

        boolean saved = MercantileConfig.get().enableWorkstationVis;
        try {
            MercantileConfig.get().enableWorkstationVis = false;
            invokeHandler(helper, handler, player);
            int afterDisabled = channel.outboundMessages().size();
            helper.assertTrue(afterDisabled == 0,
                    "No packet should be sent when enableWorkstationVis=false; queued=" + afterDisabled);

            MercantileConfig.get().enableWorkstationVis = true;
            invokeHandler(helper, handler, player);
            int afterEnabled = channel.outboundMessages().size();
            helper.assertTrue(afterEnabled >= 1,
                    "A packet should be queued when enableWorkstationVis=true; queued=" + afterEnabled);
        } finally {
            MercantileConfig.get().enableWorkstationVis = saved;
        }

        player.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void mapCapTruncatesNotThrows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));

        int spawnCount = WorkstationMapService.MAX_ENTRIES + 50;
        Villager[] spawned = new Villager[spawnCount];
        for (int i = 0; i < spawnCount; i++) {
            Villager v = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
            v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER));
            v.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                    GlobalPos.of(level.dimension(), origin));
            spawned[i] = v;
        }

        WorkstationMapS2CPayload payload;
        try {
            payload = WorkstationMapService.build(level, origin);
        } finally {
            for (Villager v : spawned) v.discard();
        }

        helper.assertTrue(payload.bound().size() == WorkstationMapService.MAX_ENTRIES,
                "bound size should be capped to MAX_ENTRIES=" + WorkstationMapService.MAX_ENTRIES
                        + "; got " + payload.bound().size());

        helper.succeed();
    }

    private static Method locateHandler(GameTestHelper helper) {
        try {
            Method m = MercantileNetworking.class.getDeclaredMethod("handleRequestWorkstationMap", ServerPlayer.class);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            helper.fail("MercantileNetworking.handleRequestWorkstationMap not found — signature changed? " + e);
            throw new AssertionError(e);
        }
    }

    private static void invokeHandler(GameTestHelper helper, Method handler, ServerPlayer player) {
        try {
            handler.invoke(null, player);
        } catch (InvocationTargetException e) {
            helper.fail("handleRequestWorkstationMap threw: " + e.getCause());
            throw new AssertionError(e.getCause());
        } catch (IllegalAccessException e) {
            helper.fail("Could not invoke handleRequestWorkstationMap: " + e);
            throw new AssertionError(e);
        }
    }

    private static EmbeddedChannel extractEmbeddedChannel(GameTestHelper helper, ServerPlayer player) {
        Connection connection;
        try {
            Field connField = net.minecraft.server.network.ServerCommonPacketListenerImpl.class
                    .getDeclaredField("connection");
            connField.setAccessible(true);
            connection = (Connection) connField.get(player.connection);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            helper.fail("ServerCommonPacketListenerImpl.connection field not accessible — mapping changed? " + e);
            throw new AssertionError(e);
        }
        Field channelField;
        try {
            channelField = Connection.class.getDeclaredField("channel");
            channelField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            helper.fail("Connection.channel field not found — mapping or field renamed? " + e);
            throw new AssertionError(e);
        }
        try {
            Object channel = channelField.get(connection);
            if (!(channel instanceof EmbeddedChannel embedded)) {
                helper.fail("Mock player connection channel is not EmbeddedChannel; got "
                        + (channel == null ? "null" : channel.getClass().getName()));
                throw new AssertionError("not embedded");
            }
            return embedded;
        } catch (IllegalAccessException e) {
            helper.fail("Could not access Connection.channel: " + e);
            throw new AssertionError(e);
        }
    }
}
