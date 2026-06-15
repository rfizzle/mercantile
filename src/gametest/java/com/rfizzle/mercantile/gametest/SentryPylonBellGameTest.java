package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.block.SentryPylonBlockEntity;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.BellRingS2CPayload;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

public class SentryPylonBellGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void pylonRingsBellOnActivation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pylonRel = new BlockPos(1, 2, 1);
        BlockPos pylonAbs = helper.absolutePos(pylonRel);
        BlockPos bellRel = new BlockPos(2, 2, 1);
        BlockPos bellAbs = helper.absolutePos(bellRel);

        helper.setBlock(pylonRel, MercantileRegistry.SENTRY_PYLON);
        helper.setBlock(bellRel, Blocks.BELL);

        SentryPylonBlockEntity pylon = (SentryPylonBlockEntity) helper.getBlockEntity(pylonRel);
        pylon.setFuel(pylon.getMaxFuel());

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(pylonAbs.getX() + 0.5, pylonAbs.getY() + 1, pylonAbs.getZ() + 0.5);
        EmbeddedChannel channel = extractEmbeddedChannel(helper, player);
        channel.outboundMessages().clear();

        boolean savedVis = MercantileConfig.get().enableBellRadiusVis;
        boolean savedAlarm = MercantileConfig.get().enablePylonBellAlarm;
        try {
            MercantileConfig.get().enableBellRadiusVis = true;
            MercantileConfig.get().enablePylonBellAlarm = true;

            // Spawn a threat nearby to trigger scan and activation
            helper.spawn(EntityType.ZOMBIE, 3, 2, 1);
            pylon.setScanCooldownForTesting(0);
            pylon.setScanCooldownForTesting(0);

            helper.runAfterDelay(5, () -> {
                pylon.tickServerCommon();
                BellRingS2CPayload payload = findBellRingPayload(channel);
                helper.assertTrue(payload != null, "Expected a BellRingS2CPayload when pylon activates near a bell");
                helper.assertTrue(payload.bellPos().equals(bellAbs), "Payload bellPos should match the bell's position");
                helper.succeed();
            });
        } finally {
            MercantileConfig.get().enableBellRadiusVis = savedVis;
            MercantileConfig.get().enablePylonBellAlarm = savedAlarm;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void pylonRespectsBellConfig(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pylonRel = new BlockPos(1, 2, 1);
        BlockPos bellRel = new BlockPos(2, 2, 1);

        helper.setBlock(pylonRel, MercantileRegistry.SENTRY_PYLON);
        helper.setBlock(bellRel, Blocks.BELL);

        SentryPylonBlockEntity pylon = (SentryPylonBlockEntity) helper.getBlockEntity(pylonRel);
        pylon.setFuel(pylon.getMaxFuel());

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        EmbeddedChannel channel = extractEmbeddedChannel(helper, player);
        channel.outboundMessages().clear();

        boolean savedAlarm = MercantileConfig.get().enablePylonBellAlarm;
        try {
            MercantileConfig.get().enablePylonBellAlarm = false;

            helper.spawn(EntityType.ZOMBIE, 3, 2, 1);

            helper.runAfterDelay(5, () -> {
                pylon.tickServerCommon();
                BellRingS2CPayload payload = findBellRingPayload(channel);
                helper.assertTrue(payload == null, "Bell should NOT ring when enablePylonBellAlarm is false");
                helper.succeed();
            });
        } finally {
            MercantileConfig.get().enablePylonBellAlarm = savedAlarm;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void pylonHandlesNoBellCase(GameTestHelper helper) {
        BlockPos pylonRel = new BlockPos(1, 2, 1);
        helper.setBlock(pylonRel, MercantileRegistry.SENTRY_PYLON);

        SentryPylonBlockEntity pylon = (SentryPylonBlockEntity) helper.getBlockEntity(pylonRel);
        pylon.setFuel(pylon.getMaxFuel());

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        EmbeddedChannel channel = extractEmbeddedChannel(helper, player);
        channel.outboundMessages().clear();

        helper.spawn(EntityType.ZOMBIE, 3, 2, 1);
        pylon.setScanCooldownForTesting(0);

        helper.runAfterDelay(5, () -> {
            try {
                pylon.tickServerCommon();
                BellRingS2CPayload payload = findBellRingPayload(channel);
                helper.assertTrue(payload == null, "Payload should be null when no bell is nearby");
                helper.succeed();
            } catch (Exception e) {
                helper.fail("Pylon should handle no-bell case without errors: " + e.getMessage());
            }
        });
    }

    private static BellRingS2CPayload findBellRingPayload(EmbeddedChannel channel) {
        for (Object msg : channel.outboundMessages()) {
            if (msg instanceof Packet<?> packet
                    && packet instanceof ClientboundCustomPayloadPacket cpp
                    && cpp.payload() instanceof BellRingS2CPayload payload) {
                return payload;
            }
        }
        return null;
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
