package com.rfizzle.mercantile.client.visualization;

import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.RequestWorkstationMapC2SPayload;
import com.rfizzle.mercantile.network.WorkstationMapS2CPayload;
import com.rfizzle.mercantile.visualization.ProfessionColors;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WorkstationLinkRenderer {

    private static final int REQUEST_INTERVAL_TICKS = 40; // 2 s; matches server REQUEST_QUERY_COOLDOWN_MS
    private static final double RENDER_RANGE = 64.0;
    private static final double RENDER_RANGE_SQR = RENDER_RANGE * RENDER_RANGE;
    private static final double BASE_STEP_BLOCKS = 0.6; // dust particle spacing at distance 0
    private static final double MAX_STEP_BLOCKS = 2.5;
    private static final int MAX_PARTICLES_PER_TICK = 256;
    // Cone-cull threshold: dot(viewDir, normalize(target - eye)) > threshold ⇒ in view.
    // 0.0 ≈ 180° cone (everything in front); negative widens further.
    private static final double VIEW_DOT_THRESHOLD = -0.35;
    private static final double UNBOUND_PULSE_PERIOD = 12.0;
    private static final double ORBIT_PERIOD_TICKS = 40.0;

    // Tick-thread only state — ClientTickEvents fires on the render thread.
    private static int ticksSinceRequest = REQUEST_INTERVAL_TICKS;
    private static boolean wasActive = false;

    private WorkstationLinkRenderer() {
    }

    public static void tick(Minecraft client) {
        if (client == null) return;
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) {
            resetState();
            return;
        }

        boolean configOn = isEnabled();
        boolean holdingBell = isHoldingBell(player);
        boolean active = configOn && holdingBell;

        if (!active) {
            if (wasActive) {
                ClientMercantileData.setWorkstationMap(null);
            }
            wasActive = false;
            ticksSinceRequest = REQUEST_INTERVAL_TICKS;
            return;
        }

        ticksSinceRequest++;
        if (!wasActive || ticksSinceRequest >= REQUEST_INTERVAL_TICKS) {
            ClientPlayNetworking.send(new RequestWorkstationMapC2SPayload());
            ticksSinceRequest = 0;
        }
        wasActive = true;

        WorkstationMapS2CPayload payload = ClientMercantileData.getWorkstationMap();
        if (payload == null) return;
        spawnParticles(client, level, player, payload);
    }

    private static boolean isEnabled() {
        MercantileConfig synced = ClientMercantileData.getServerConfig();
        if (synced != null) return synced.enableWorkstationVis;
        return MercantileConfig.get().enableWorkstationVis;
    }

    private static boolean isHoldingBell(LocalPlayer player) {
        return player.getMainHandItem().is(Items.BELL) || player.getOffhandItem().is(Items.BELL);
    }

    private static void spawnParticles(Minecraft client, ClientLevel level, LocalPlayer player,
                                       WorkstationMapS2CPayload payload) {
        int spawned = 0;
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0f).normalize();
        long gameTime = level.getGameTime();

        // Index villagers once.
        Map<UUID, Villager> villagerByUuid = indexVillagers(level);

        // Bound links — coloured dust segments.
        for (Map.Entry<UUID, BlockPos> entry : payload.bound().entrySet()) {
            if (spawned >= MAX_PARTICLES_PER_TICK) return;
            Villager villager = villagerByUuid.get(entry.getKey());
            if (villager == null) continue;
            BlockPos workstation = entry.getValue();
            Vec3 from = villager.position().add(0.0, villager.getBbHeight() * 0.5, 0.0);
            Vec3 to = Vec3.atCenterOf(workstation);
            if (!withinRange(from, eye) && !withinRange(to, eye)) continue;
            if (!inViewCone(from, eye, view) && !inViewCone(to, eye, view)) continue;
            Vector3f color = ProfessionColors.lookup(villager.getVillagerData().getProfession());
            spawned += spawnDustLine(level, from, to, eye, color);
        }

        // Unbound villagers — pulsing angry_villager puff.
        double pulse = Math.sin((gameTime % (long) (UNBOUND_PULSE_PERIOD * 2)) / UNBOUND_PULSE_PERIOD * Math.PI);
        if (pulse > 0.6) {
            for (UUID id : payload.unboundVillagers()) {
                if (spawned >= MAX_PARTICLES_PER_TICK) return;
                Villager villager = villagerByUuid.get(id);
                if (villager == null) continue;
                Vec3 pos = villager.position().add(0.0, villager.getBbHeight() + 0.4, 0.0);
                if (!withinRange(pos, eye)) continue;
                if (!inViewCone(pos, eye, view)) continue;
                level.addParticle(ParticleTypes.ANGRY_VILLAGER,
                        pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
                spawned++;
            }
        }

        // Unclaimed workstations — yellow orbit.
        DustParticleOptions orbitDust = new DustParticleOptions(new Vector3f(1.0f, 0.95f, 0.2f), 1.0f);
        double angle = (gameTime % (long) ORBIT_PERIOD_TICKS) / ORBIT_PERIOD_TICKS * Math.PI * 2.0;
        for (BlockPos pos : payload.unclaimedWorkstations()) {
            if (spawned >= MAX_PARTICLES_PER_TICK) return;
            Vec3 center = Vec3.atCenterOf(pos).add(0.0, 0.7, 0.0);
            if (!withinRange(center, eye)) continue;
            if (!inViewCone(center, eye, view)) continue;
            double radius = 0.6;
            for (int i = 0; i < 2; i++) {
                double a = angle + i * Math.PI;
                double x = center.x + Math.cos(a) * radius;
                double z = center.z + Math.sin(a) * radius;
                level.addParticle(orbitDust, x, center.y, z, 0.0, 0.0, 0.0);
                spawned++;
                if (spawned >= MAX_PARTICLES_PER_TICK) return;
            }
        }
    }

    private static int spawnDustLine(ClientLevel level, Vec3 from, Vec3 to, Vec3 eye, Vector3f color) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 1.0e-3) return 0;
        // LOD: scale step by distance from camera (midpoint).
        Vec3 mid = from.add(delta.scale(0.5));
        double camDist = Math.sqrt(mid.distanceToSqr(eye));
        double step = Math.min(MAX_STEP_BLOCKS, BASE_STEP_BLOCKS * (1.0 + camDist / 16.0));
        int count = Math.max(1, (int) Math.floor(length / step));
        DustParticleOptions opts = new DustParticleOptions(color, 1.0f);
        Vec3 unit = delta.scale(1.0 / length);
        int spawned = 0;
        for (int i = 1; i <= count; i++) {
            double t = i * step;
            if (t > length) break;
            double x = from.x + unit.x * t;
            double y = from.y + unit.y * t;
            double z = from.z + unit.z * t;
            level.addParticle(opts, x, y, z, 0.0, 0.0, 0.0);
            spawned++;
        }
        return spawned;
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

    private static Map<UUID, Villager> indexVillagers(ClientLevel level) {
        Map<UUID, Villager> map = new HashMap<>();
        for (net.minecraft.world.entity.Entity entity : level.entitiesForRendering()) {
            if (entity instanceof Villager villager) {
                map.put(villager.getUUID(), villager);
            }
        }
        return map;
    }

    private static void resetState() {
        ticksSinceRequest = REQUEST_INTERVAL_TICKS;
        wasActive = false;
    }
}
