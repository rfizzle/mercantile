package com.rfizzle.mercantile.memorial;

import com.rfizzle.mercantile.particle.MercantileParticles;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Brief cosmetic mourning after a villager death: witnesses within range stop walking,
 * face the death spot, and shed grief tears for a couple of seconds. Purely visual —
 * no brain/activity surgery — and session-only state, so nothing is persisted.
 */
public final class MourningManager {

    /** Villagers within this range of a death mourn it (matches the mood witness range). */
    public static final double MOURNING_RANGE = 16.0;
    /** How long the mourning pose lasts (3 seconds). */
    public static final int MOURNING_DURATION_TICKS = 60;

    private static final int PARTICLE_INTERVAL_TICKS = 10;

    private record Session(ResourceKey<Level> dimension, List<UUID> mourners, Vec3 deathPos, long endGameTime) {
    }

    private static final List<Session> SESSIONS = new ArrayList<>();

    private MourningManager() {
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> SESSIONS.clear());
        ServerTickEvents.END_SERVER_TICK.register(MourningManager::tick);
    }

    static void startMourning(ServerLevel level, Villager dead) {
        AABB range = dead.getBoundingBox().inflate(MOURNING_RANGE);
        List<UUID> mourners = new ArrayList<>();
        for (Villager witness : level.getEntitiesOfClass(Villager.class, range, v -> v != dead && v.isAlive())) {
            mourners.add(witness.getUUID());
        }
        if (mourners.isEmpty()) return;
        SESSIONS.add(new Session(level.dimension(), mourners, dead.position(),
                level.getGameTime() + MOURNING_DURATION_TICKS));
    }

    /** Whether this villager is currently part of an active mourning session. */
    public static boolean isMourning(UUID villagerId) {
        for (Session session : SESSIONS) {
            if (session.mourners().contains(villagerId)) return true;
        }
        return false;
    }

    /** Number of active mourning sessions; lets tests assert on the delta of a single death. */
    public static int sessionCount() {
        return SESSIONS.size();
    }

    private static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        Iterator<Session> it = SESSIONS.iterator();
        while (it.hasNext()) {
            Session session = it.next();
            ServerLevel level = server.getLevel(session.dimension());
            if (level == null || level.getGameTime() >= session.endGameTime()) {
                it.remove();
                continue;
            }
            boolean emitTears = level.getGameTime() % PARTICLE_INTERVAL_TICKS == 0;
            for (UUID id : session.mourners()) {
                if (!(level.getEntity(id) instanceof Villager mourner) || !mourner.isAlive()) continue;
                mourner.getNavigation().stop();
                mourner.getLookControl().setLookAt(
                        session.deathPos().x, session.deathPos().y, session.deathPos().z);
                if (emitTears) {
                    level.sendParticles(MercantileParticles.GRIEF_TEAR,
                            mourner.getX(), mourner.getEyeY(), mourner.getZ(),
                            2, 0.2, 0.1, 0.2, 0.0);
                }
            }
        }
    }
}
