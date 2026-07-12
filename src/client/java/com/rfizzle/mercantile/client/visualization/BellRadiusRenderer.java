package com.rfizzle.mercantile.client.visualization;

import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.visualization.BellRingService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public final class BellRadiusRenderer {

    private static final int RADIUS = BellRingService.RING_RADIUS;
    private static final int PARTICLES_PER_TICK = 32;
    private static final int CIRCLE_SAMPLES = 256;
    private static final int BURST_PARTICLES = 64;
    private static final double VIEW_DOT_THRESHOLD = -0.35;
    private static final double MAX_Y_DELTA = 16.0;
    private static final Vector3f GOLD = new Vector3f(1.0f, 0.85f, 0.2f);
    private static final DustParticleOptions DUST_RING = new DustParticleOptions(GOLD, 1.0f);
    private static final DustParticleOptions DUST_BURST = new DustParticleOptions(GOLD, 1.4f);

    // Tick-thread only state — ClientTickEvents fires on the render thread.
    private static int angleOffset = 0;
    private static final Deque<BlockPos> pendingBursts = new ArrayDeque<>();

    private BellRadiusRenderer() {
    }

    public static void queueBoundaryBurst(BlockPos bellPos) {
        pendingBursts.add(bellPos);
    }

    public static void clearPending() {
        pendingBursts.clear();
    }

    public static void tick(Minecraft client) {
        if (client == null) return;
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) {
            pendingBursts.clear();
            return;
        }

        long now = level.getGameTime();
        BellGlowTracker.tick(now);

        if (!isEnabled()) {
            pendingBursts.clear();
            return;
        }

        drainPendingBursts(level, player);

        if (isHoldingBell(player)) {
            spawnRingArc(level, player);
            // Rescan for glow targets on alternating ticks only: the hold expiry
            // (HOLD_GLOW_DURATION_TICKS) exceeds this interval, so the outline stays continuous
            // while halving the per-tick entity scan and its allocation.
            if ((now & 1L) == 0L) {
                refreshHoldGlow(level, player, now);
            }
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

    private static void spawnRingArc(ClientLevel level, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0f).normalize();
        BellRadiusGeometry.ringArc(
                player.getX(), player.getY(), player.getZ(),
                eye.x, eye.y, eye.z, view.x, view.y, view.z,
                RADIUS, CIRCLE_SAMPLES, PARTICLES_PER_TICK, angleOffset,
                MAX_Y_DELTA, VIEW_DOT_THRESHOLD,
                (wx, wz) -> level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz),
                (force, x, y, z, dx, dy, dz) -> level.addParticle(DUST_RING, force, x, y, z, dx, dy, dz));

        angleOffset = (angleOffset + 1) % CIRCLE_SAMPLES;
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
