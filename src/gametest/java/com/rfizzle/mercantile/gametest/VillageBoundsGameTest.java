package com.rfizzle.mercantile.gametest;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.MercantileNetworking;
import com.rfizzle.mercantile.network.VillageBoundsS2CPayload;
import com.rfizzle.mercantile.network.VillageBoundsS2CPayload.PoiEntry;
import com.rfizzle.mercantile.visualization.VillageBoundsService;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Gametests for VillageBoundsService and the /mercantile village handler path.
 *
 * Note: tests must tolerate background POIs that may exist in the gametest world from
 * world-gen villages. Assertions check for presence/absence of POIs we explicitly added,
 * not exact equality of the full payload.
 */
public class VillageBoundsGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void payloadIncludesBedWorkstationBell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos originAbs = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos bedAbs = helper.absolutePos(new BlockPos(1, 1, 0));
        BlockPos workAbs = helper.absolutePos(new BlockPos(2, 1, 0));
        BlockPos bellAbs = helper.absolutePos(new BlockPos(3, 1, 0));
        addPoi(level, bedAbs, PoiTypes.HOME);
        addPoi(level, workAbs, PoiTypes.FARMER);
        addPoi(level, bellAbs, PoiTypes.MEETING);

        VillageBoundsS2CPayload payload = VillageBoundsService.build(level, originAbs);

        helper.assertTrue(hasPoi(payload, bedAbs, VillageBoundsService.TYPE_BED),
                "bed missing from payload");
        helper.assertTrue(hasPoi(payload, workAbs, VillageBoundsService.TYPE_WORKSTATION),
                "workstation missing from payload");
        helper.assertTrue(hasPoi(payload, bellAbs, VillageBoundsService.TYPE_BELL),
                "bell missing from payload");

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void occupiedHomeBacklinksVillager(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bedAbs = helper.absolutePos(new BlockPos(1, 1, 0));
        addPoi(level, bedAbs, PoiTypes.HOME);
        // Consume the ticket so the POI reads as occupied.
        level.getPoiManager().take(
                holder -> holder.is(PoiTypes.HOME),
                (holder, pos) -> pos.equals(bedAbs),
                bedAbs, 1);

        Villager villager = helper.spawn(EntityType.VILLAGER, 0, 1, 0);
        villager.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(level.dimension(), bedAbs));

        VillageBoundsS2CPayload payload = VillageBoundsService.build(level, villager.blockPosition());

        PoiEntry bed = findPoi(payload, bedAbs);
        helper.assertTrue(bed != null, "bed POI missing");
        helper.assertTrue(bed.villagerPos().isPresent(),
                "occupied bed should back-link to villager");
        helper.assertTrue(bed.villagerPos().get().equals(villager.blockPosition()),
                "villagerPos mismatch");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unclaimedHomeHasNoVillagerPos(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bedAbs = helper.absolutePos(new BlockPos(1, 1, 0));
        addPoi(level, bedAbs, PoiTypes.HOME);

        VillageBoundsS2CPayload payload = VillageBoundsService.build(level, bedAbs);

        PoiEntry bed = findPoi(payload, bedAbs);
        helper.assertTrue(bed != null, "bed POI missing");
        helper.assertFalse(bed.villagerPos().isPresent(),
                "unclaimed bed should have no villagerPos");

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void boundsBoxContainsPaddedPoi(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bedAbs = helper.absolutePos(new BlockPos(0, 1, 0));
        addPoi(level, bedAbs, PoiTypes.HOME);

        VillageBoundsS2CPayload payload = VillageBoundsService.build(level, bedAbs);

        int pad = VillageBoundsService.BOUNDS_PADDING;
        // Background POIs may extend the bounds further than ours — assert containment, not equality.
        helper.assertTrue(payload.boundsMin().getX() <= bedAbs.getX() - pad, "minX too tight");
        helper.assertTrue(payload.boundsMin().getY() <= bedAbs.getY() - pad, "minY too tight");
        helper.assertTrue(payload.boundsMin().getZ() <= bedAbs.getZ() - pad, "minZ too tight");
        helper.assertTrue(payload.boundsMax().getX() >= bedAbs.getX() + pad, "maxX too tight");
        helper.assertTrue(payload.boundsMax().getY() >= bedAbs.getY() + pad, "maxY too tight");
        helper.assertTrue(payload.boundsMax().getZ() >= bedAbs.getZ() + pad, "maxZ too tight");

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void outOfRangePoiNotIncluded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos originAbs = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos farAbs = originAbs.offset(VillageBoundsService.QUERY_RADIUS * 4, 0, 0);
        addPoi(level, farAbs, PoiTypes.HOME);

        VillageBoundsS2CPayload payload = VillageBoundsService.build(level, originAbs);

        boolean containsFar = false;
        for (PoiEntry e : payload.pois()) {
            if (e.pos().equals(farAbs)) {
                containsFar = true;
                break;
            }
        }
        helper.assertFalse(containsFar, "far POI should not be in payload");

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void disabledByConfigReturnsEarly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bedAbs = helper.absolutePos(new BlockPos(1, 1, 0));
        addPoi(level, bedAbs, PoiTypes.HOME);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(bedAbs.getX() + 0.5, bedAbs.getY(), bedAbs.getZ() + 0.5);

        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        channel.outboundMessages().clear();

        Method handler = locateHandler(helper);

        boolean saved = MercantileConfig.get().enableVillageBoundaryVis;
        try {
            MercantileConfig.get().enableVillageBoundaryVis = false;
            invokeHandler(helper, handler, player);
            int afterDisabled = GametestNetUtil.countPayloads(channel, VillageBoundsS2CPayload.class);
            helper.assertTrue(afterDisabled == 0,
                    "no packet when disabled; got " + afterDisabled);

            MercantileConfig.get().enableVillageBoundaryVis = true;
            invokeHandler(helper, handler, player);
            int afterEnabled = GametestNetUtil.countPayloads(channel, VillageBoundsS2CPayload.class);
            helper.assertTrue(afterEnabled >= 1,
                    "expected packet when enabled; got " + afterEnabled);
        } finally {
            MercantileConfig.get().enableVillageBoundaryVis = saved;
        }

        player.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void roundTripDeliversPoisToClient(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bedAbs = helper.absolutePos(new BlockPos(1, 1, 0));
        BlockPos workAbs = helper.absolutePos(new BlockPos(2, 1, 0));
        BlockPos bellAbs = helper.absolutePos(new BlockPos(3, 1, 0));
        addPoi(level, bedAbs, PoiTypes.HOME);
        addPoi(level, workAbs, PoiTypes.FARMER);
        addPoi(level, bellAbs, PoiTypes.MEETING);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(bedAbs.getX() + 0.5, bedAbs.getY(), bedAbs.getZ() + 0.5);

        EmbeddedChannel channel = GametestNetUtil.extractEmbeddedChannel(helper, player);
        channel.outboundMessages().clear();

        boolean saved = MercantileConfig.get().enableVillageBoundaryVis;
        try {
            MercantileConfig.get().enableVillageBoundaryVis = true;
            invokeHandler(helper, locateHandler(helper), player);
        } finally {
            MercantileConfig.get().enableVillageBoundaryVis = saved;
        }

        VillageBoundsS2CPayload decoded = GametestNetUtil.findUniquePayload(
                helper, channel, VillageBoundsS2CPayload.class);
        helper.assertTrue(decoded != null, "no payload decoded");
        helper.assertTrue(hasPoi(decoded, bedAbs, VillageBoundsService.TYPE_BED),
                "decoded missing bed");
        helper.assertTrue(hasPoi(decoded, workAbs, VillageBoundsService.TYPE_WORKSTATION),
                "decoded missing workstation");
        helper.assertTrue(hasPoi(decoded, bellAbs, VillageBoundsService.TYPE_BELL),
                "decoded missing bell");

        player.discard();
        helper.succeed();
    }

    // --- helpers ---

    private static void addPoi(ServerLevel level, BlockPos pos, ResourceKey<PoiType> key) {
        Holder<PoiType> holder = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolderOrThrow(key);
        PoiManager pm = level.getPoiManager();
        pm.getType(pos).ifPresent(existing -> pm.remove(pos));
        pm.add(pos, holder);
    }

    private static boolean hasPoi(VillageBoundsS2CPayload payload, BlockPos pos, String type) {
        for (PoiEntry e : payload.pois()) {
            if (e.pos().equals(pos) && e.type().equals(type)) return true;
        }
        return false;
    }

    private static PoiEntry findPoi(VillageBoundsS2CPayload payload, BlockPos pos) {
        for (PoiEntry e : payload.pois()) {
            if (e.pos().equals(pos)) return e;
        }
        return null;
    }

    private static Method locateHandler(GameTestHelper helper) {
        try {
            Method m = MercantileNetworking.class.getDeclaredMethod(
                    "handleRequestVillageBounds", ServerPlayer.class);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            helper.fail("handleRequestVillageBounds not found");
            throw new AssertionError(e);
        }
    }

    private static void invokeHandler(GameTestHelper helper, Method handler, ServerPlayer player) {
        try {
            handler.invoke(null, player);
        } catch (InvocationTargetException e) {
            helper.fail("handler threw: " + e.getCause());
            throw new AssertionError(e.getCause());
        } catch (IllegalAccessException e) {
            helper.fail("could not invoke handler");
            throw new AssertionError(e);
        }
    }
}
