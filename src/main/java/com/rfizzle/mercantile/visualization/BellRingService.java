package com.rfizzle.mercantile.visualization;

import com.rfizzle.mercantile.Mercantile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BellRingService {

    // Matches vanilla BellBlockEntity.SEARCH_RADIUS (48 blocks).
    public static final int RING_RADIUS = 48;
    public static final int MAX_VILLAGERS = 64;

    private static final double RING_RADIUS_SQR = (double) RING_RADIUS * RING_RADIUS;

    private BellRingService() {
    }

    public static List<UUID> villagersInRange(ServerLevel level, BlockPos bellPos) {
        Selection selection = select(level, Vec3.atCenterOf(bellPos));
        if (selection.truncated()) {
            Mercantile.LOGGER.warn("BellRingService: villager cap {} exceeded near {}", MAX_VILLAGERS, bellPos);
        }
        return selection.ids();
    }

    /**
     * Adult villagers within {@link #RING_RADIUS} of {@code center}, capped at {@link #MAX_VILLAGERS}.
     * Shared by the server-side bell ring (bell-centered) and the client-side hold-to-glow refresh
     * (player-centered) so both use identical baby-filter, distance, and cap semantics. Silent by
     * design: the client path calls this every tick and must not log.
     */
    public static List<UUID> villagersInRange(Level level, Vec3 center) {
        return select(level, center).ids();
    }

    private record Selection(List<UUID> ids, boolean truncated) {
    }

    /**
     * Single-scan selection core. {@code truncated} is true only when a qualifying villager beyond
     * the cap was encountered — i.e. entries were genuinely dropped — so the server ring path can
     * warn on real overflow without firing at an exactly-full, nothing-dropped result. The cap only
     * bounds the result list, not the underlying {@code getEntitiesOfClass} scan.
     */
    private static Selection select(Level level, Vec3 center) {
        double d = RING_RADIUS;
        AABB region = new AABB(
                center.x - d, center.y - d, center.z - d,
                center.x + d, center.y + d, center.z + d);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, region);

        List<UUID> result = new ArrayList<>();
        for (Villager villager : villagers) {
            if (villager.isBaby()) continue;
            if (villager.position().distanceToSqr(center) > RING_RADIUS_SQR) continue;
            if (result.size() >= MAX_VILLAGERS) {
                return new Selection(result, true);
            }
            result.add(villager.getUUID());
        }
        return new Selection(result, false);
    }
}
