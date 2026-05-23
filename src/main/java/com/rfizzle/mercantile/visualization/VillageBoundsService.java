package com.rfizzle.mercantile.visualization;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.network.VillageBoundsS2CPayload;
import com.rfizzle.mercantile.network.VillageBoundsS2CPayload.PoiEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class VillageBoundsService {

    public static final int QUERY_RADIUS = 64;
    public static final int BOUNDS_PADDING = 10;
    public static final int MAX_POIS = VillageBoundsS2CPayload.MAX_POIS;

    public static final String TYPE_BED = "bed";
    public static final String TYPE_WORKSTATION = "workstation";
    public static final String TYPE_BELL = "bell";

    private VillageBoundsService() {
    }

    public static VillageBoundsS2CPayload build(ServerLevel level, BlockPos origin) {
        PoiManager poiManager = level.getPoiManager();

        // Collect once into a list — we want POIs of any occupancy (claimed or free).
        List<PoiRecord> records = poiManager.getInRange(
                holder -> holder.is(PoiTypeTags.VILLAGE),
                origin,
                QUERY_RADIUS,
                PoiManager.Occupancy.ANY).toList();

        if (records.isEmpty()) {
            return new VillageBoundsS2CPayload(origin, origin, origin, List.of());
        }

        // Pre-fetch villagers within QUERY_RADIUS so we can back-link occupied POIs to their villager.
        Vec3 originCenter = Vec3.atCenterOf(origin);
        double d = QUERY_RADIUS;
        AABB region = new AABB(
                originCenter.x - d, originCenter.y - d, originCenter.z - d,
                originCenter.x + d, originCenter.y + d, originCenter.z + d);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, region);

        List<PoiEntry> pois = new ArrayList<>(Math.min(records.size(), MAX_POIS));
        long sumX = 0;
        long sumY = 0;
        long sumZ = 0;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean capWarned = false;
        int counted = 0;

        for (PoiRecord record : records) {
            BlockPos pos = record.getPos();
            String type = classify(record.getPoiType());
            if (type == null) continue;

            // Centroid + AABB are taken over ALL classified POIs in range, even if we drop
            // some from the payload for the cap. That keeps the bounding box stable when at
            // the cap (truncating only the per-POI markers, not the box).
            sumX += pos.getX();
            sumY += pos.getY();
            sumZ += pos.getZ();
            if (pos.getX() < minX) minX = pos.getX();
            if (pos.getY() < minY) minY = pos.getY();
            if (pos.getZ() < minZ) minZ = pos.getZ();
            if (pos.getX() > maxX) maxX = pos.getX();
            if (pos.getY() > maxY) maxY = pos.getY();
            if (pos.getZ() > maxZ) maxZ = pos.getZ();
            counted++;

            if (pois.size() >= MAX_POIS) {
                if (!capWarned) {
                    Mercantile.LOGGER.warn("VillageBoundsService: POI cap {} exceeded near {}", MAX_POIS, origin);
                    capWarned = true;
                }
                continue;
            }

            Optional<BlockPos> villagerPos = record.isOccupied()
                    ? findVillagerForPoi(villagers, level, pos, type)
                    : Optional.empty();

            pois.add(new PoiEntry(pos, type, villagerPos));
        }

        if (counted == 0) {
            return new VillageBoundsS2CPayload(origin, origin, origin, List.of());
        }

        BlockPos center = new BlockPos(
                (int) Math.round(sumX / (double) counted),
                (int) Math.round(sumY / (double) counted),
                (int) Math.round(sumZ / (double) counted));
        BlockPos boundsMin = new BlockPos(minX - BOUNDS_PADDING, minY - BOUNDS_PADDING, minZ - BOUNDS_PADDING);
        BlockPos boundsMax = new BlockPos(maxX + BOUNDS_PADDING, maxY + BOUNDS_PADDING, maxZ + BOUNDS_PADDING);

        return new VillageBoundsS2CPayload(center, boundsMin, boundsMax, pois);
    }

    private static String classify(Holder<PoiType> poiType) {
        if (poiType.is(PoiTypes.HOME)) return TYPE_BED;
        if (poiType.is(PoiTypes.MEETING)) return TYPE_BELL;
        if (poiType.is(PoiTypeTags.ACQUIRABLE_JOB_SITE)) return TYPE_WORKSTATION;
        return null;
    }

    private static Optional<BlockPos> findVillagerForPoi(List<Villager> villagers, ServerLevel level,
                                                        BlockPos poiPos, String type) {
        MemoryModuleType<GlobalPos> memory = switch (type) {
            case TYPE_BED -> MemoryModuleType.HOME;
            case TYPE_WORKSTATION -> MemoryModuleType.JOB_SITE;
            case TYPE_BELL -> MemoryModuleType.MEETING_POINT;
            default -> null;
        };
        if (memory == null) return Optional.empty();
        for (Villager villager : villagers) {
            if (villager.isBaby()) continue;
            Optional<GlobalPos> mem = villager.getBrain().getMemory(memory);
            if (mem.isEmpty()) continue;
            GlobalPos global = mem.get();
            if (!global.dimension().equals(level.dimension())) continue;
            if (!global.pos().equals(poiPos)) continue;
            return Optional.of(villager.blockPosition());
        }
        return Optional.empty();
    }
}
