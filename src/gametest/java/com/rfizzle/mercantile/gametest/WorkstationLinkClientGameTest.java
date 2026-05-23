package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.network.MercantileNetworking;
import com.rfizzle.mercantile.network.WorkstationMapS2CPayload;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * End-to-end packet pipeline gametests for the workstation-link visualization.
 *
 * Pure client rendering (particle spawning, frustum culling, dust colour selection) cannot be
 * exercised in a Fabric gametest — gametests run on a headless server with no rendering context.
 * The renderer's correctness is validated manually via runClient (see SPEC §11). What we *can*
 * validate here is that triggering the server-side handler produces a single outbound
 * WorkstationMapS2CPayload carrying bound, unbound, and unclaimed entries that match the world.
 */
public class WorkstationLinkClientGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void requestPayloadRoundTripDeliversBoundUnboundUnclaimed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // Bound villager + composter
        Villager bound = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        bound.setVillagerData(bound.getVillagerData().setProfession(VillagerProfession.FARMER));
        BlockPos workstationRel = new BlockPos(2, 1, 2);
        BlockPos workstationAbs = helper.absolutePos(workstationRel);
        helper.setBlock(workstationRel, Blocks.COMPOSTER);
        bound.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                GlobalPos.of(level.dimension(), workstationAbs));

        // Unbound villager (separate entity, no JOB_SITE)
        Villager unbound = helper.spawn(EntityType.VILLAGER, 4, 1, 0);
        unbound.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);

        // Unclaimed workstation (no villager claims it — bound villager already has its composter)
        BlockPos unclaimedRel = new BlockPos(5, 1, 5);
        BlockPos unclaimedAbs = helper.absolutePos(unclaimedRel);
        helper.setBlock(unclaimedRel, Blocks.COMPOSTER);

        // Mock player, teleported into the test region so the service queries the right area.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos centerAbs = helper.absolutePos(new BlockPos(2, 1, 2));
        player.teleportTo(centerAbs.getX() + 0.5, centerAbs.getY(), centerAbs.getZ() + 0.5);

        EmbeddedChannel channel = extractEmbeddedChannel(helper, player);
        channel.outboundMessages().clear();

        invokeHandler(helper, locateHandler(helper), player);

        WorkstationMapS2CPayload decoded = decodeWorkstationPayload(helper, channel);
        helper.assertTrue(decoded != null,
                "handler should produce exactly one WorkstationMapS2CPayload; channel="
                        + channel.outboundMessages());

        helper.assertTrue(decoded.bound().containsKey(bound.getUUID()),
                "decoded bound map should contain bound villager UUID; got " + decoded.bound());
        helper.assertTrue(workstationAbs.equals(decoded.bound().get(bound.getUUID())),
                "bound entry should point to composter pos; got " + decoded.bound().get(bound.getUUID()));
        helper.assertTrue(decoded.unboundVillagers().contains(unbound.getUUID()),
                "decoded unbound list should contain unbound villager; got " + decoded.unboundVillagers());
        helper.assertTrue(decoded.unclaimedWorkstations().contains(unclaimedAbs),
                "decoded unclaimed list should contain stray composter; got " + decoded.unclaimedWorkstations());
        helper.assertFalse(decoded.unclaimedWorkstations().contains(workstationAbs),
                "bound composter must not appear in unclaimed list");

        player.discard();
        bound.discard();
        unbound.discard();
        helper.succeed();
    }

    // --- helpers (mirror those in WorkstationLinkGameTest; duplicated to avoid coupling) ---

    private static Method locateHandler(GameTestHelper helper) {
        try {
            Method m = MercantileNetworking.class.getDeclaredMethod(
                    "handleRequestWorkstationMap", ServerPlayer.class);
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

    private static WorkstationMapS2CPayload decodeWorkstationPayload(GameTestHelper helper, EmbeddedChannel channel) {
        WorkstationMapS2CPayload found = null;
        int matches = 0;
        for (Object msg : channel.outboundMessages()) {
            if (msg instanceof Packet<?> packet
                    && packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof WorkstationMapS2CPayload payload) {
                found = payload;
                matches++;
            }
        }
        if (matches != 1) {
            helper.fail("Expected exactly one WorkstationMapS2CPayload on channel; saw " + matches
                    + " (outbound=" + channel.outboundMessages() + ")");
            return null;
        }
        return found;
    }
}
