package com.rfizzle.mercantile.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;

public final class SentryPylonScanner {
    private SentryPylonScanner() {
    }

    @Nullable
    public static LivingEntity findNearestHostile(ServerLevel level, BlockPos center, int radius) {
        AABB box = new AABB(center).inflate(radius);
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        double cx = center.getX() + 0.5;
        double cy = center.getY() + 0.5;
        double cz = center.getZ() + 0.5;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, SentryPylonScanner::isHostile)) {
            double dx = e.getX() - cx;
            double dy = e.getY() - cy;
            double dz = e.getZ() - cz;
            double dsq = dx * dx + dy * dy + dz * dz;
            if (dsq < bestDistSq) {
                bestDistSq = dsq;
                best = e;
            }
        }
        return best;
    }

    static boolean isHostile(LivingEntity e) {
        return isHostileClassification(
                e.getType().getCategory(),
                e.isAlive(),
                e instanceof Enemy,
                e instanceof IronGolem,
                SentryGolemTag.isSentry(e));
    }

    static boolean isHostileClassification(MobCategory category, boolean isAlive,
                                           boolean isEnemy, boolean isIronGolem, boolean isSentry) {
        if (!isAlive) return false;
        if (isIronGolem) return false;
        if (isSentry) return false;
        if (isEnemy) return true;
        return category == MobCategory.MONSTER;
    }

    @Nullable
    public static BlockPos findSpawnPos(ServerLevel level, BlockPos near, BlockPos pylon, int radius) {
        RandomSource random = level.getRandom();
        long maxDistSq = (long) radius * (long) radius;
        for (int attempt = 0; attempt < 10; attempt++) {
            int ox = near.getX() + random.nextInt(9) - 4;
            int oz = near.getZ() + random.nextInt(9) - 4;
            int topY = near.getY() + 3;
            int bottomY = near.getY() - 3;
            for (int y = topY; y >= bottomY; y--) {
                BlockPos candidate = new BlockPos(ox, y, oz);
                if (!isWithinRadius(candidate, pylon, maxDistSq)) continue;
                if (isValidSpawn(level, candidate) && hasLineOfSight(level, pylon, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    static boolean isWithinRadius(BlockPos candidate, BlockPos pylon, long maxDistSq) {
        long dx = candidate.getX() - pylon.getX();
        long dy = candidate.getY() - pylon.getY();
        long dz = candidate.getZ() - pylon.getZ();
        return dx * dx + dy * dy + dz * dz <= maxDistSq;
    }

    /**
     * Require an unobstructed line from the pylon to the spawn position. A sentry can only
     * materialize where the pylon can "see" it — so a threat in a sealed cave below, or behind
     * a wall, won't conjure a golem buried underground.
     *
     * <p>The ray is cast from the candidate (always air — verified by {@link #isValidSpawn}) toward
     * the pylon, never the reverse: starting inside the pylon's own collision shape would make the
     * clip self-hit immediately. A clear path either misses every collider ({@link HitResult.Type#MISS})
     * or its first hit is the pylon block itself — both mean nothing solid stands in between.
     */
    public static boolean hasLineOfSight(ServerLevel level, BlockPos pylon, BlockPos candidate) {
        Vec3 from = Vec3.atCenterOf(candidate);
        Vec3 to = Vec3.atCenterOf(pylon);
        BlockHitResult hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pylon);
    }

    private static boolean isValidSpawn(ServerLevel level, BlockPos candidate) {
        BlockPos below = candidate.below();
        BlockState belowState = level.getBlockState(below);
        if (!belowState.isFaceSturdy(level, below, Direction.UP)) return false;
        BlockState atFeet = level.getBlockState(candidate);
        if (!atFeet.getCollisionShape(level, candidate).isEmpty()) return false;
        BlockPos head = candidate.above();
        BlockState atHead = level.getBlockState(head);
        return atHead.getCollisionShape(level, head).isEmpty();
    }

    public static Optional<BlockPos> findNearestBell(ServerLevel level, BlockPos center, int radius) {
        return level.getPoiManager().getInRange(holder ->
                        holder.value().matchingStates().stream().anyMatch(s -> s.is(Blocks.BELL)),
                center, radius, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
                .min(Comparator.comparingDouble(record -> record.getPos().distSqr(center)))
                .map(net.minecraft.world.entity.ai.village.poi.PoiRecord::getPos);
    }
}
