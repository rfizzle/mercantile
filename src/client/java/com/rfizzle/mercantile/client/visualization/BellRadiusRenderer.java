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
        double cx = player.getX();
        double cz = player.getZ();
        double cy = player.getY();

        for (int i = 0; i < PARTICLES_PER_TICK; i++) {
            int idx = (angleOffset + i * (CIRCLE_SAMPLES / PARTICLES_PER_TICK)) % CIRCLE_SAMPLES;
            double angle = (idx / (double) CIRCLE_SAMPLES) * Math.PI * 2.0;
            double x = cx + Math.cos(angle) * RADIUS;
            double z = cz + Math.sin(angle) * RADIUS;
            int worldX = (int) Math.floor(x);
            int worldZ = (int) Math.floor(z);
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
            double y = surfaceY + 0.2;
            if (Math.abs(y - cy) > MAX_Y_DELTA) continue;
            Vec3 point = new Vec3(x, y, z);
            if (!inViewCone(point, eye, view)) continue;
            level.addParticle(DUST_RING, x, y, z, 0.0, 0.0, 0.0);
        }

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
            double cx = pos.getX() + 0.5;
            double cz = pos.getZ() + 0.5;
            for (int i = 0; i < BURST_PARTICLES; i++) {
                double angle = (i / (double) BURST_PARTICLES) * Math.PI * 2.0;
                double x = cx + Math.cos(angle) * RADIUS;
                double z = cz + Math.sin(angle) * RADIUS;
                int worldX = (int) Math.floor(x);
                int worldZ = (int) Math.floor(z);
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
                double y = surfaceY + 0.3;
                Vec3 point = new Vec3(x, y, z);
                if (!inViewCone(point, eye, view)) continue;
                level.addParticle(DUST_BURST, x, y, z, 0.0, 0.05, 0.0);
            }
        }
    }

    private static boolean inViewCone(Vec3 point, Vec3 eye, Vec3 view) {
        Vec3 to = point.subtract(eye);
        double len = to.length();
        if (len < 1.0e-3) return true;
        double dot = (to.x * view.x + to.y * view.y + to.z * view.z) / len;
        return dot > VIEW_DOT_THRESHOLD;
    }
}
