package com.rfizzle.mercantile.client.visualization;

import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.RequestVillageBoundsC2SPayload;
import com.rfizzle.mercantile.network.VillageBoundsS2CPayload;
import com.rfizzle.mercantile.network.VillageBoundsS2CPayload.PoiEntry;
import com.rfizzle.mercantile.visualization.VillageBoundsService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class VillageBoundsRenderer {

    private static final int REQUEST_INTERVAL_TICKS = 40; // 2 s; matches server REQUEST_QUERY_COOLDOWN_MS
    private static final double RENDER_RANGE = 128.0;
    private static final double RENDER_RANGE_SQR = RENDER_RANGE * RENDER_RANGE;
    private static final double BASE_STEP_BLOCKS = 0.5;
    private static final double MAX_STEP_BLOCKS = 2.5;
    private static final int MAX_PARTICLES_PER_TICK = 256;
    private static final double VIEW_DOT_THRESHOLD = -0.35;
    private static final double UNCLAIMED_PULSE_PERIOD = 12.0;
    private static final int TOGGLE_DURATION_TICKS = 60 * 20; // 60 seconds
    private static final int CENTROID_COLUMN_HEIGHT = 6;
    private static final int CENTROID_PARTICLES_PER_TICK = 2;

    private static final DustParticleOptions WHITE_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), 1.0f);
    private static final DustParticleOptions BED_DUST =
            new DustParticleOptions(new Vector3f(0.3f, 0.5f, 1.0f), 1.0f);
    private static final DustParticleOptions WORKSTATION_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 0.95f, 0.2f), 1.0f);
    private static final DustParticleOptions BELL_DUST =
            new DustParticleOptions(new Vector3f(0.3f, 1.0f, 0.4f), 1.0f);

    // Tick-thread only state — ClientTickEvents fires on the render thread.
    private static int ticksSinceRequest = REQUEST_INTERVAL_TICKS;
    private static boolean wasActive = false;
    private static long toggledUntilTick = 0L;
    private static boolean pendingFreshPacket = false;

    private VillageBoundsRenderer() {
    }

    public static void notePacketArrived() {
        // Seeding the toggle window here would read the *old* level's game time during a
        // dimension transition (or 0 if client.level is null). Defer the seed to the next
        // tick that observes a live level so the window aligns with the renderer's clock.
        pendingFreshPacket = true;
    }

    public static void clear() {
        ticksSinceRequest = REQUEST_INTERVAL_TICKS;
        wasActive = false;
        toggledUntilTick = 0L;
        pendingFreshPacket = false;
    }

    public static void tick(Minecraft client) {
        if (client == null) return;
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) {
            clear();
            return;
        }

        boolean configOn = isEnabled();
        boolean holdingBell = isHoldingBell(player);
        long gameTime = level.getGameTime();
        if (pendingFreshPacket) {
            toggledUntilTick = gameTime + TOGGLE_DURATION_TICKS;
            pendingFreshPacket = false;
        }
        boolean toggled = gameTime < toggledUntilTick;
        boolean active = configOn && (holdingBell || toggled);

        if (!active) {
            if (wasActive) {
                ClientMercantileData.setVillageBounds(null);
            }
            wasActive = false;
            ticksSinceRequest = REQUEST_INTERVAL_TICKS;
            return;
        }

        ticksSinceRequest++;
        // Bell-holding players auto-refresh; command-triggered toggle relies on the original
        // snapshot until the player walks back into the command-triggered behaviour or holds a bell.
        if (holdingBell && (!wasActive || ticksSinceRequest >= REQUEST_INTERVAL_TICKS)) {
            ClientPlayNetworking.send(new RequestVillageBoundsC2SPayload());
            ticksSinceRequest = 0;
        }
        wasActive = true;

        VillageBoundsS2CPayload payload = ClientMercantileData.getVillageBounds();
        if (payload == null || payload.pois().isEmpty()) return;
        spawnParticles(level, player, payload, gameTime);
    }

    private static boolean isEnabled() {
        MercantileConfig synced = ClientMercantileData.getServerConfig();
        if (synced != null) return synced.enableVillageBoundaryVis;
        return MercantileConfig.get().enableVillageBoundaryVis;
    }

    private static boolean isHoldingBell(LocalPlayer player) {
        return player.getMainHandItem().is(Items.BELL) || player.getOffhandItem().is(Items.BELL);
    }

    private static void spawnParticles(ClientLevel level, LocalPlayer player,
                                       VillageBoundsS2CPayload payload, long gameTime) {
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0f).normalize();
        int spawned = 0;

        spawned += spawnCentroidColumn(level, payload.center(), eye, view);
        if (spawned >= MAX_PARTICLES_PER_TICK) return;

        spawned += spawnBoundsBox(level, payload.boundsMin(), payload.boundsMax(), eye, view,
                MAX_PARTICLES_PER_TICK - spawned);
        if (spawned >= MAX_PARTICLES_PER_TICK) return;

        double pulse = Math.sin((gameTime % (long) (UNCLAIMED_PULSE_PERIOD * 2))
                / UNCLAIMED_PULSE_PERIOD * Math.PI);
        boolean unclaimedBurst = pulse > 0.6;

        for (PoiEntry entry : payload.pois()) {
            if (spawned >= MAX_PARTICLES_PER_TICK) return;
            boolean occupied = entry.villagerPos().isPresent();
            if (!occupied && !unclaimedBurst) continue;
            Vec3 marker = Vec3.atCenterOf(entry.pos()).add(0.0, 0.6, 0.0);
            if (!withinRange(marker, eye)) continue;
            if (!inViewCone(marker, eye, view)) continue;
            DustParticleOptions opts = colorFor(entry.type());
            level.addParticle(opts, marker.x, marker.y, marker.z, 0.0, 0.0, 0.0);
            spawned++;
        }
    }

    private static int spawnCentroidColumn(ClientLevel level, BlockPos center, Vec3 eye, Vec3 view) {
        Vec3 base = Vec3.atCenterOf(center);
        if (!withinRange(base, eye)) return 0;
        if (!inViewCone(base, eye, view)) return 0;
        int spawned = 0;
        for (int i = 0; i < CENTROID_PARTICLES_PER_TICK; i++) {
            double t = level.random.nextDouble() * CENTROID_COLUMN_HEIGHT;
            level.addParticle(ParticleTypes.END_ROD,
                    base.x, base.y + t, base.z,
                    0.0, 0.02, 0.0);
            spawned++;
        }
        return spawned;
    }

    private static int spawnBoundsBox(ClientLevel level, BlockPos min, BlockPos max,
                                      Vec3 eye, Vec3 view, int budget) {
        if (budget <= 0) return 0;
        // Vanilla AABB on integer block positions — draw edges from min.x..max.x+1 (inclusive).
        double x0 = min.getX();
        double y0 = min.getY();
        double z0 = min.getZ();
        double x1 = max.getX() + 1.0;
        double y1 = max.getY() + 1.0;
        double z1 = max.getZ() + 1.0;

        // 12 edges of the AABB.
        int spawned = 0;
        spawned += drawEdge(level, x0, y0, z0, x1, y0, z0, eye, view, budget - spawned);
        if (spawned >= budget) return spawned;
        spawned += drawEdge(level, x0, y0, z1, x1, y0, z1, eye, view, budget - spawned);
        if (spawned >= budget) return spawned;
        spawned += drawEdge(level, x0, y1, z0, x1, y1, z0, eye, view, budget - spawned);
        if (spawned >= budget) return spawned;
        spawned += drawEdge(level, x0, y1, z1, x1, y1, z1, eye, view, budget - spawned);
        if (spawned >= budget) return spawned;

        spawned += drawEdge(level, x0, y0, z0, x0, y1, z0, eye, view, budget - spawned);
        if (spawned >= budget) return spawned;
        spawned += drawEdge(level, x1, y0, z0, x1, y1, z0, eye, view, budget - spawned);
        if (spawned >= budget) return spawned;
        spawned += drawEdge(level, x0, y0, z1, x0, y1, z1, eye, view, budget - spawned);
        if (spawned >= budget) return spawned;
        spawned += drawEdge(level, x1, y0, z1, x1, y1, z1, eye, view, budget - spawned);
        if (spawned >= budget) return spawned;

        spawned += drawEdge(level, x0, y0, z0, x0, y0, z1, eye, view, budget - spawned);
        if (spawned >= budget) return spawned;
        spawned += drawEdge(level, x1, y0, z0, x1, y0, z1, eye, view, budget - spawned);
        if (spawned >= budget) return spawned;
        spawned += drawEdge(level, x0, y1, z0, x0, y1, z1, eye, view, budget - spawned);
        if (spawned >= budget) return spawned;
        spawned += drawEdge(level, x1, y1, z0, x1, y1, z1, eye, view, budget - spawned);
        return spawned;
    }

    private static int drawEdge(ClientLevel level, double ax, double ay, double az,
                                double bx, double by, double bz,
                                Vec3 eye, Vec3 view, int budget) {
        if (budget <= 0) return 0;
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-3) return 0;
        Vec3 mid = new Vec3(ax + dx * 0.5, ay + dy * 0.5, az + dz * 0.5);
        if (!withinRange(mid, eye)) return 0;
        double camDist = Math.sqrt(mid.distanceToSqr(eye));
        double step = Math.min(MAX_STEP_BLOCKS, BASE_STEP_BLOCKS * (1.0 + camDist / 16.0));
        int count = Math.max(1, (int) Math.floor(length / step));
        double unitX = dx / length, unitY = dy / length, unitZ = dz / length;
        int spawned = 0;
        for (int i = 0; i <= count && spawned < budget; i++) {
            double t = i * step;
            if (t > length) break;
            double x = ax + unitX * t;
            double y = ay + unitY * t;
            double z = az + unitZ * t;
            Vec3 point = new Vec3(x, y, z);
            if (!inViewCone(point, eye, view)) continue;
            level.addParticle(WHITE_DUST, x, y, z, 0.0, 0.0, 0.0);
            spawned++;
        }
        return spawned;
    }

    private static DustParticleOptions colorFor(String type) {
        return switch (type) {
            case VillageBoundsService.TYPE_BED -> BED_DUST;
            case VillageBoundsService.TYPE_WORKSTATION -> WORKSTATION_DUST;
            case VillageBoundsService.TYPE_BELL -> BELL_DUST;
            default -> WHITE_DUST;
        };
    }

    private static boolean withinRange(Vec3 point, Vec3 eye) {
        return point.distanceToSqr(eye) <= RENDER_RANGE_SQR;
    }

    private static boolean inViewCone(Vec3 point, Vec3 eye, Vec3 view) {
        Vec3 to = point.subtract(eye);
        double len = to.length();
        if (len < 1.0e-3) return true;
        double dot = (to.x * view.x + to.y * view.y + to.z * view.z) / len;
        return dot > VIEW_DOT_THRESHOLD;
    }
}
