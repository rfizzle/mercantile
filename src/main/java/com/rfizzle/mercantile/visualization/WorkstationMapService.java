package com.rfizzle.mercantile.visualization;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.network.WorkstationMapS2CPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class WorkstationMapService {

    public static final int QUERY_RADIUS = 64;
    public static final int MAX_ENTRIES = WorkstationMapS2CPayload.MAX_ENTRIES;
    public static final int MAX_UNBOUND = WorkstationMapS2CPayload.MAX_UNBOUND;
    public static final int MAX_UNCLAIMED = WorkstationMapS2CPayload.MAX_UNCLAIMED;

    private WorkstationMapService() {
    }

    public static WorkstationMapS2CPayload build(ServerLevel level, BlockPos origin) {
        Vec3 center = Vec3.atCenterOf(origin);
        double d = QUERY_RADIUS;
        AABB region = new AABB(
                center.x - d, center.y - d, center.z - d,
                center.x + d, center.y + d, center.z + d);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, region);

        Map<UUID, BlockPos> bound = new LinkedHashMap<>();
        List<UUID> unbound = new ArrayList<>();
        Set<BlockPos> boundPositions = new HashSet<>();
        boolean boundCapWarned = false;
        boolean unboundCapWarned = false;

        for (Villager villager : villagers) {
            if (villager.isBaby()) continue;
            UUID uuid = villager.getUUID();
            Optional<GlobalPos> jobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            if (jobSite.isPresent() && jobSite.get().dimension().equals(level.dimension())) {
                BlockPos pos = jobSite.get().pos();
                if (bound.size() < MAX_ENTRIES) {
                    bound.put(uuid, pos);
                    boundPositions.add(pos);
                } else if (!boundCapWarned) {
                    Mercantile.LOGGER.warn("WorkstationMapService: bound cap {} exceeded near {}", MAX_ENTRIES, origin);
                    boundCapWarned = true;
                }
            } else {
                if (unbound.size() < MAX_UNBOUND) {
                    unbound.add(uuid);
                } else if (!unboundCapWarned) {
                    Mercantile.LOGGER.warn("WorkstationMapService: unbound cap {} exceeded near {}", MAX_UNBOUND, origin);
                    unboundCapWarned = true;
                }
            }
        }

        PoiManager poiManager = level.getPoiManager();
        List<BlockPos> unclaimed = new ArrayList<>();
        Set<BlockPos> seenUnclaimed = new LinkedHashSet<>();
        boolean unclaimedCapWarned = false;
        for (PoiRecord record : (Iterable<PoiRecord>) poiManager.getInRange(
                holder -> holder.is(PoiTypeTags.ACQUIRABLE_JOB_SITE),
                origin,
                QUERY_RADIUS,
                PoiManager.Occupancy.HAS_SPACE)::iterator) {
            BlockPos pos = record.getPos();
            if (boundPositions.contains(pos)) continue;
            if (!seenUnclaimed.add(pos)) continue;
            if (unclaimed.size() < MAX_UNCLAIMED) {
                unclaimed.add(pos);
            } else if (!unclaimedCapWarned) {
                Mercantile.LOGGER.warn("WorkstationMapService: unclaimed cap {} exceeded near {}", MAX_UNCLAIMED, origin);
                unclaimedCapWarned = true;
                break;
            }
        }

        return new WorkstationMapS2CPayload(new HashMap<>(bound), unbound, unclaimed);
    }
}
