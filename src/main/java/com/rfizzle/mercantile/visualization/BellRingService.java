package com.rfizzle.mercantile.visualization;

import com.rfizzle.mercantile.Mercantile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
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
        Vec3 center = Vec3.atCenterOf(bellPos);
        double d = RING_RADIUS;
        AABB region = new AABB(
                center.x - d, center.y - d, center.z - d,
                center.x + d, center.y + d, center.z + d);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, region);

        List<UUID> result = new ArrayList<>();
        for (Villager villager : villagers) {
            if (villager.isBaby()) continue;
            if (villager.position().distanceToSqr(center) > RING_RADIUS_SQR) continue;
            if (result.size() < MAX_VILLAGERS) {
                result.add(villager.getUUID());
            } else {
                Mercantile.LOGGER.warn("BellRingService: villager cap {} exceeded near {}", MAX_VILLAGERS, bellPos);
                break;
            }
        }
        return result;
    }
}
