package com.rfizzle.mercantile.client.visualization;

import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.visualization.BellRingService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BellRadiusRenderer {

    private static final int RADIUS = BellRingService.RING_RADIUS;
    private static final int PARTICLES_PER_TICK = 32;
    private static final int CIRCLE_SAMPLES = 256;
    private static final int BURST_PARTICLES = 64;
    private static final double VIEW_DOT_THRESHOLD = -0.35;
    private static final double MAX_Y_DELTA = 16.0;

    // Placed-bell discovery + rendering bounds. The sweep walks the whole render-distance square, but
    // only the nearest MAX_RENDERED_BELLS within PLACED_BELL_RENDER_RANGE draw a circle each tick, so
    // the per-tick particle cost stays bounded (<= (MAX_RENDERED_BELLS + 1) * PARTICLES_PER_TICK) no
    // matter how many bells a world contains. Bells past that cap are still discovered, just not drawn.
    private static final int CHUNKS_PER_TICK = 64;
    private static final int MAX_RENDERED_BELLS = 8;
    private static final double PLACED_BELL_RENDER_RANGE = 128.0;
    private static final double PLACED_BELL_RENDER_RANGE_SQR = PLACED_BELL_RENDER_RANGE * PLACED_BELL_RENDER_RANGE;

    // Placed bells keep the established gold dust (actual coverage); the player-centered scouting ring
    // is a dim white so a hypothetical placement is never confused with a real bell's coverage.
    private static final Vector3f GOLD = new Vector3f(1.0f, 0.85f, 0.2f);
    private static final Vector3f PREVIEW_WHITE = new Vector3f(0.9f, 0.9f, 0.95f);
    private static final DustParticleOptions DUST_RING = new DustParticleOptions(GOLD, 1.0f);
    private static final DustParticleOptions DUST_PREVIEW = new DustParticleOptions(PREVIEW_WHITE, 1.0f);
    private static final DustParticleOptions DUST_BURST = new DustParticleOptions(GOLD, 1.4f);

    // Tick-thread only state — ClientTickEvents fires on the render thread.
    private static int angleOffset = 0;
    private static final Deque<BlockPos> pendingBursts = new ArrayDeque<>();
    private static final BellSweepScheduler bellSweep = new BellSweepScheduler();

    private BellRadiusRenderer() {
    }

    public static void queueBoundaryBurst(BlockPos bellPos) {
        pendingBursts.add(bellPos);
    }

    /** Full client-side teardown — bursts and the placed-bell sweep. Called on disconnect/world unload. */
    public static void clearState() {
        pendingBursts.clear();
        bellSweep.reset();
    }

    public static void tick(Minecraft client) {
        if (client == null) return;
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) {
            clearState();
            return;
        }

        long now = level.getGameTime();
        BellGlowTracker.tick(now);

        if (!isEnabled()) {
            clearState();
            return;
        }

        drainPendingBursts(level, player);

        if (isHoldingBell(player)) {
            runBellSweep(client, level, player);
            spawnPlacedBellRings(level, player);
            spawnPreviewRing(level, player);
            angleOffset = (angleOffset + 1) % CIRCLE_SAMPLES;
            // Rescan for glow targets on alternating ticks only: the hold expiry
            // (HOLD_GLOW_DURATION_TICKS) exceeds this interval, so the outline stays continuous
            // while halving the per-tick entity scan and its allocation.
            if ((now & 1L) == 0L) {
                refreshHoldGlow(level, player, now);
            }
        } else {
            bellSweep.reset();
        }
    }

    private static boolean isEnabled() {
        MercantileConfig synced = ClientMercantileData.getServerConfig();
        if (synced != null) return synced.enableBellRadiusVis;
        return MercantileConfig.get().enableBellRadiusVis;
    }

    private static boolean isHoldingBell(LocalPlayer player) {
        return player.getMainHandItem().is(Items.BELL) || player.getOffhandItem().is(Items.BELL);
    }

    /**
     * Scan this tick's budgeted slice of loaded chunks around the player for placed bells, feeding
     * finds back to the scheduler. Uses {@code getChunkNow} (never forces a load) so an unloaded chunk
     * is simply skipped this pass and re-checked on the next.
     */
    private static void runBellSweep(Minecraft client, ClientLevel level, LocalPlayer player) {
        int radius = client.options.getEffectiveRenderDistance();
        ClientChunkCache chunkCache = level.getChunkSource();
        int playerChunkX = SectionPos.blockToSectionCoord(player.getBlockX());
        int playerChunkZ = SectionPos.blockToSectionCoord(player.getBlockZ());

        int[] indices = bellSweep.nextChunkIndices(radius, CHUNKS_PER_TICK);
        for (int index : indices) {
            int chunkX = playerChunkX + BellSweepScheduler.offsetX(index, radius);
            int chunkZ = playerChunkZ + BellSweepScheduler.offsetZ(index, radius);
            LevelChunk chunk = chunkCache.getChunkNow(chunkX, chunkZ);
            if (chunk == null) continue;
            for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                if (entry.getValue() instanceof BellBlockEntity) {
                    bellSweep.recordBell(entry.getKey().asLong());
                }
            }
        }
    }

    /** Draw a gold coverage circle around each nearby placed bell — nearest first, budget-capped. */
    private static void spawnPlacedBellRings(ClientLevel level, LocalPlayer player) {
        var published = bellSweep.publishedBells();
        if (published.isEmpty()) return;

        // Unpack the packed bell positions into primitive arrays once (bell x/z centered on the block),
        // then let the pure selector pick the nearest few in range — no per-comparison Vec3 allocation.
        int n = published.size();
        double[] xs = new double[n];
        double[] ys = new double[n];
        double[] zs = new double[n];
        int i = 0;
        for (long packed : published) {
            xs[i] = BlockPos.getX(packed) + 0.5;
            ys[i] = BlockPos.getY(packed);
            zs[i] = BlockPos.getZ(packed) + 0.5;
            i++;
        }

        Vec3 playerPos = player.position();
        int[] selected = BellRadiusGeometry.nearestWithinRange(
                xs, ys, zs, playerPos.x, playerPos.y, playerPos.z,
                PLACED_BELL_RENDER_RANGE_SQR, MAX_RENDERED_BELLS);
        if (selected.length == 0) return;

        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0f).normalize();
        for (int j : selected) {
            BellRadiusGeometry.ringArc(
                    xs[j], ys[j], zs[j],
                    eye.x, eye.y, eye.z, view.x, view.y, view.z,
                    RADIUS, CIRCLE_SAMPLES, PARTICLES_PER_TICK, angleOffset,
                    MAX_Y_DELTA, VIEW_DOT_THRESHOLD,
                    (wx, wz) -> level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz),
                    (force, x, y, z, dx, dy, dz) -> level.addParticle(DUST_RING, force, x, y, z, dx, dy, dz));
        }
    }

    /** The dim-white player-centered scouting preview — a hypothetical bell placed where you stand. */
    private static void spawnPreviewRing(ClientLevel level, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0f).normalize();
        BellRadiusGeometry.ringArc(
                player.getX(), player.getY(), player.getZ(),
                eye.x, eye.y, eye.z, view.x, view.y, view.z,
                RADIUS, CIRCLE_SAMPLES, PARTICLES_PER_TICK, angleOffset,
                MAX_Y_DELTA, VIEW_DOT_THRESHOLD,
                (wx, wz) -> level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz),
                (force, x, y, z, dx, dy, dz) -> level.addParticle(DUST_PREVIEW, force, x, y, z, dx, dy, dz));
    }

    // Outlines adult villagers within the gathering radius of the player while a bell is held,
    // reusing the server's exact selection (baby filter, distance, cap). The short hold expiry is
    // rewritten every tick, so the glow clears within a couple of ticks once the bell is stowed.
    private static void refreshHoldGlow(ClientLevel level, LocalPlayer player, long now) {
        List<UUID> villagerIds = BellRingService.villagersInRange(level, player.position());
        for (UUID id : villagerIds) {
            BellGlowTracker.markHoldGlowing(id, now);
        }
    }

    private static void drainPendingBursts(ClientLevel level, LocalPlayer player) {
        if (pendingBursts.isEmpty()) return;
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0f).normalize();
        BlockPos pos;
        while ((pos = pendingBursts.poll()) != null) {
            BellRadiusGeometry.boundaryBurst(
                    pos.getX() + 0.5, pos.getZ() + 0.5,
                    eye.x, eye.y, eye.z, view.x, view.y, view.z,
                    RADIUS, BURST_PARTICLES, VIEW_DOT_THRESHOLD,
                    (wx, wz) -> level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz),
                    (force, x, y, z, dx, dy, dz) -> level.addParticle(DUST_BURST, force, x, y, z, dx, dy, dz));
        }
    }
}
