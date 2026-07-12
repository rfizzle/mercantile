package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.BellRingS2CPayload;
import com.rfizzle.mercantile.visualization.BellRingService;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

public class BellRadiusGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void villagersInRangeReturnsOnlyInside(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bellRel = new BlockPos(0, 1, 0);
        BlockPos bellAbs = helper.absolutePos(bellRel);

        Villager inside1 = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
        Villager inside2 = helper.spawn(EntityType.VILLAGER, -3, 1, 1);
        Villager outside = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        outside.teleportTo(bellAbs.getX() + BellRingService.RING_RADIUS + 5, bellAbs.getY(), bellAbs.getZ());

        try {
            List<UUID> result = BellRingService.villagersInRange(level, bellAbs);

            helper.assertTrue(result.contains(inside1.getUUID()),
                    "inside1 should be in result; got " + result);
            helper.assertTrue(result.contains(inside2.getUUID()),
                    "inside2 should be in result; got " + result);
            helper.assertFalse(result.contains(outside.getUUID()),
                    "outside villager should NOT be in result; got " + result);
            helper.assertTrue(result.size() == 2,
                    "expected exactly 2 villagers; got " + result.size());
        } finally {
            inside1.discard();
            inside2.discard();
            outside.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void babyVillagerSkipped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bellAbs = helper.absolutePos(new BlockPos(0, 1, 0));

        Villager baby = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        baby.setBaby(true);

        try {
            List<UUID> result = BellRingService.villagersInRange(level, bellAbs);
            helper.assertFalse(result.contains(baby.getUUID()),
                    "baby villager should not appear in result; got " + result);
        } finally {
            baby.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void villagerCapTruncates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bellAbs = helper.absolutePos(new BlockPos(0, 1, 0));

        int spawnCount = BellRingService.MAX_VILLAGERS + 5;
        Villager[] spawned = new Villager[spawnCount];
        for (int i = 0; i < spawnCount; i++) {
            spawned[i] = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        }

        try {
            List<UUID> result = BellRingService.villagersInRange(level, bellAbs);
            helper.assertTrue(result.size() == BellRingService.MAX_VILLAGERS,
                    "result should be capped to MAX_VILLAGERS=" + BellRingService.MAX_VILLAGERS
                            + "; got " + result.size());
        } finally {
            for (Villager v : spawned) v.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void villagersInRangeVec3OverloadSelectsPlayerCentered(GameTestHelper helper) {
        // The client hold-to-glow path (issue #161) selects villagers via the Level+Vec3 overload,
        // centered on the player rather than a bell. Assert it honors the same baby-filter, distance,
        // and cap semantics the server ring path relies on.
        ServerLevel level = helper.getLevel();
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 1, 0)));

        Villager inside = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
        Villager baby = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        baby.setBaby(true);
        Villager outside = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        outside.teleportTo(center.x + BellRingService.RING_RADIUS + 5, center.y, center.z);

        try {
            List<UUID> result = BellRingService.villagersInRange(level, center);
            helper.assertTrue(result.contains(inside.getUUID()),
                    "adult inside radius should be selected; got " + result);
            helper.assertFalse(result.contains(baby.getUUID()),
                    "baby should be skipped; got " + result);
            helper.assertFalse(result.contains(outside.getUUID()),
                    "villager beyond radius should be excluded; got " + result);
        } finally {
            inside.discard();
            baby.discard();
            outside.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void villagersInRangeVec3OverloadTruncatesAtCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 1, 0)));

        int spawnCount = BellRingService.MAX_VILLAGERS + 5;
        Villager[] spawned = new Villager[spawnCount];
        for (int i = 0; i < spawnCount; i++) {
            spawned[i] = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        }

        try {
            List<UUID> result = BellRingService.villagersInRange(level, center);
            helper.assertTrue(result.size() == BellRingService.MAX_VILLAGERS,
                    "Vec3 overload should cap at MAX_VILLAGERS=" + BellRingService.MAX_VILLAGERS
                            + "; got " + result.size());
        } finally {
            for (Villager v : spawned) v.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void capWarnFiresOnlyOnRealOverflow(GameTestHelper helper) {
        // The ring path should warn only when villagers are actually dropped, not when the result is
        // merely full: exactly MAX_VILLAGERS in range drops nothing, MAX_VILLAGERS + 1 drops one.
        ServerLevel level = helper.getLevel();
        BlockPos bellAbs = helper.absolutePos(new BlockPos(0, 1, 0));

        CapWarnCapture capture = CapWarnCapture.attach();
        Villager[] exact = new Villager[BellRingService.MAX_VILLAGERS];
        Villager[] overflow = new Villager[BellRingService.MAX_VILLAGERS + 1];
        try {
            for (int i = 0; i < exact.length; i++) {
                exact[i] = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
            }
            BellRingService.villagersInRange(level, bellAbs);
            helper.assertTrue(capture.capWarnings() == 0,
                    "exactly MAX_VILLAGERS in range must not warn (nothing truncated); got "
                            + capture.capWarnings());
            for (Villager v : exact) v.discard();

            capture.reset();
            for (int i = 0; i < overflow.length; i++) {
                overflow[i] = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
            }
            BellRingService.villagersInRange(level, bellAbs);
            helper.assertTrue(capture.capWarnings() >= 1,
                    "MAX_VILLAGERS + 1 in range must warn on real truncation; got " + capture.capWarnings());
        } finally {
            capture.detach();
            for (Villager v : exact) if (v != null) v.discard();
            for (Villager v : overflow) if (v != null) v.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void disabledByConfigSkipsBroadcast(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bellRel = new BlockPos(0, 2, 0);
        BlockPos bellAbs = helper.absolutePos(bellRel);
        helper.setBlock(bellRel, Blocks.BELL);

        Villager v = helper.spawn(EntityType.VILLAGER, 1, 1, 1);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(bellAbs.getX() + 0.5, bellAbs.getY() + 1, bellAbs.getZ() + 0.5);

        EmbeddedChannel channel = extractEmbeddedChannel(helper, player);
        channel.outboundMessages().clear();

        boolean saved = MercantileConfig.get().enableBellRadiusVis;
        try {
            BlockState state = level.getBlockState(bellAbs);
            BellBlock bell = (BellBlock) Blocks.BELL;
            BlockHitResult hit = new BlockHitResult(
                    new Vec3(bellAbs.getX() + 0.5, bellAbs.getY() + 0.5, bellAbs.getZ() + 0.5),
                    Direction.NORTH, bellAbs, false);

            MercantileConfig.get().enableBellRadiusVis = false;
            bell.onHit(level, state, hit, player, false);
            int countDisabled = countBellRingPackets(channel);
            helper.assertTrue(countDisabled == 0,
                    "No BellRing packet should be sent when disabled; got " + countDisabled);

            MercantileConfig.get().enableBellRadiusVis = true;
            bell.onHit(level, state, hit, player, false);
            int countEnabled = countBellRingPackets(channel);
            helper.assertTrue(countEnabled >= 1,
                    "Expected BellRing packet after enabling; got " + countEnabled);
        } finally {
            MercantileConfig.get().enableBellRadiusVis = saved;
            v.discard();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void bellRingMixinTriggersBroadcast(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bellRel = new BlockPos(0, 2, 0);
        BlockPos bellAbs = helper.absolutePos(bellRel);
        helper.setBlock(bellRel, Blocks.BELL);

        Villager near = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
        Villager far = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        far.teleportTo(bellAbs.getX() + BellRingService.RING_RADIUS + 5, bellAbs.getY(), bellAbs.getZ());

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(bellAbs.getX() + 0.5, bellAbs.getY() + 1, bellAbs.getZ() + 0.5);

        EmbeddedChannel channel = extractEmbeddedChannel(helper, player);
        channel.outboundMessages().clear();

        boolean saved = MercantileConfig.get().enableBellRadiusVis;
        try {
            MercantileConfig.get().enableBellRadiusVis = true;
            BlockState state = level.getBlockState(bellAbs);
            BellBlock bell = (BellBlock) Blocks.BELL;
            BlockHitResult hit = new BlockHitResult(
                    new Vec3(bellAbs.getX() + 0.5, bellAbs.getY() + 0.5, bellAbs.getZ() + 0.5),
                    Direction.NORTH, bellAbs, false);

            boolean rang = bell.onHit(level, state, hit, player, false);
            helper.assertTrue(rang, "bell onHit should return true with canRing=false");

            BellRingS2CPayload payload = findBellRingPayload(channel);
            helper.assertTrue(payload != null, "expected a BellRingS2CPayload on the channel");
            helper.assertTrue(payload.bellPos().equals(bellAbs),
                    "payload bellPos should match; got " + payload.bellPos() + " expected " + bellAbs);
            helper.assertTrue(payload.villagerIds().contains(near.getUUID()),
                    "payload should contain near villager; got " + payload.villagerIds());
            helper.assertFalse(payload.villagerIds().contains(far.getUUID()),
                    "payload should NOT contain far villager; got " + payload.villagerIds());
        } finally {
            MercantileConfig.get().enableBellRadiusVis = saved;
            near.discard();
            far.discard();
            player.discard();
        }
        helper.succeed();
    }

    private static int countBellRingPackets(EmbeddedChannel channel) {
        int count = 0;
        for (Object msg : channel.outboundMessages()) {
            if (isBellRingPacket(msg)) count++;
        }
        return count;
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

    private static boolean isBellRingPacket(Object msg) {
        return msg instanceof ClientboundCustomPayloadPacket cpp
                && cpp.payload() instanceof BellRingS2CPayload;
    }

    /**
     * Captures the "villager cap ... exceeded" WARN off the mercantile Log4j2 logger so a test can
     * assert the ring path warns on real truncation but not on an exactly-full result.
     */
    private static final class CapWarnCapture
            extends org.apache.logging.log4j.core.appender.AbstractAppender {
        private final java.util.concurrent.atomic.AtomicInteger capWarnings =
                new java.util.concurrent.atomic.AtomicInteger();

        private CapWarnCapture() {
            super("mercantile-cap-warn-capture", null, null, true,
                    org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY);
        }

        static CapWarnCapture attach() {
            CapWarnCapture appender = new CapWarnCapture();
            appender.start();
            logger().addAppender(appender);
            return appender;
        }

        void detach() {
            logger().removeAppender(this);
            stop();
        }

        void reset() {
            capWarnings.set(0);
        }

        int capWarnings() {
            return capWarnings.get();
        }

        @Override
        public void append(org.apache.logging.log4j.core.LogEvent event) {
            if (event.getLevel() == org.apache.logging.log4j.Level.WARN
                    && event.getMessage().getFormattedMessage().contains("villager cap")) {
                capWarnings.incrementAndGet();
            }
        }

        private static org.apache.logging.log4j.core.Logger logger() {
            return (org.apache.logging.log4j.core.Logger)
                    org.apache.logging.log4j.LogManager.getLogger(Mercantile.MOD_ID);
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
