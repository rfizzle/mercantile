package com.rfizzle.mercantile.pathfinding;

import com.google.common.collect.Sets;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.mixin.FenceGateBlockAccessor;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;

public class InteractWithFenceGate {
    private static final int COOLDOWN_BEFORE_RERUNNING_IN_SAME_NODE = 20;
    private static final double SKIP_CLOSING_IF_FURTHER_AWAY_THAN = 3.0;
    private static final double MAX_DISTANCE_TO_HOLD_OPEN_FOR_OTHER_MOBS = 2.0;

    public static BehaviorControl<LivingEntity> create() {
        MutableObject<Node> lastNode = new MutableObject<>(null);
        MutableInt cooldown = new MutableInt(0);
        Set<GlobalPos> gatesToClose = Sets.newHashSet();

        return BehaviorBuilder.create(
            instance -> instance.group(
                instance.present(MemoryModuleType.PATH),
                instance.registered(MemoryModuleType.NEAREST_LIVING_ENTITIES)
            ).apply(instance, (pathAccessor, nearbyAccessor) -> (serverLevel, livingEntity, tick) -> {
                if (!MercantileConfig.get().enablePathfindingFixes
                        || !MercantileConfig.get().enablePathfindingDoors) {
                    return false;
                }

                Path path = instance.get(pathAccessor);
                if (path.notStarted() || path.isDone()) {
                    closeGatesThatWeHavePassed(serverLevel, livingEntity, null, null, gatesToClose,
                            instance.tryGet(nearbyAccessor));
                    return false;
                }

                if (Objects.equals(lastNode.getValue(), path.getNextNode())) {
                    cooldown.setValue(COOLDOWN_BEFORE_RERUNNING_IN_SAME_NODE);
                } else if (cooldown.decrementAndGet() > 0) {
                    return false;
                }

                lastNode.setValue(path.getNextNode());
                Node prevNode = path.getPreviousNode();
                Node nextNode = path.getNextNode();

                openFenceGateIfPresent(serverLevel, livingEntity, prevNode.asBlockPos(), gatesToClose);
                openFenceGateIfPresent(serverLevel, livingEntity, nextNode.asBlockPos(), gatesToClose);

                closeGatesThatWeHavePassed(serverLevel, livingEntity, prevNode, nextNode, gatesToClose,
                        instance.tryGet(nearbyAccessor));
                return true;
            })
        );
    }

    private static void openFenceGateIfPresent(ServerLevel level, LivingEntity entity, BlockPos pos,
                                               Set<GlobalPos> gatesToClose) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FenceGateBlock gate)) return;
        if (state.getValue(FenceGateBlock.OPEN)) return;
        if (state.getValue(FenceGateBlock.POWERED)) return;

        level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, true), 10);
        var woodType = ((FenceGateBlockAccessor) gate).mercantile$getType();
        level.playSound(null, pos, woodType.fenceGateOpen(), SoundSource.BLOCKS,
                1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
        level.gameEvent(entity, GameEvent.BLOCK_OPEN, pos);
        gatesToClose.add(GlobalPos.of(level.dimension(), pos));
    }

    private static void closeGatesThatWeHavePassed(ServerLevel level, LivingEntity entity,
                                                   @Nullable Node prevNode, @Nullable Node nextNode,
                                                   Set<GlobalPos> gates,
                                                   Optional<List<LivingEntity>> nearby) {
        Iterator<GlobalPos> it = gates.iterator();
        while (it.hasNext()) {
            GlobalPos globalPos = it.next();
            BlockPos pos = globalPos.pos();

            if ((prevNode != null && prevNode.asBlockPos().equals(pos))
                    || (nextNode != null && nextNode.asBlockPos().equals(pos))) {
                continue;
            }

            if (isTooFarAway(level, entity, globalPos)) {
                it.remove();
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof FenceGateBlock gate)) {
                it.remove();
                continue;
            }

            if (!state.getValue(FenceGateBlock.OPEN)) {
                it.remove();
                continue;
            }

            if (areOtherMobsComingThrough(entity, pos, nearby)) {
                it.remove();
                continue;
            }

            level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, false), 10);
            var woodType = ((FenceGateBlockAccessor) gate).mercantile$getType();
            level.playSound(null, pos, woodType.fenceGateClose(), SoundSource.BLOCKS,
                    1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
            level.gameEvent(entity, GameEvent.BLOCK_CLOSE, pos);
            it.remove();
        }
    }

    private static boolean isTooFarAway(ServerLevel level, LivingEntity entity, GlobalPos globalPos) {
        return globalPos.dimension() != level.dimension()
                || !globalPos.pos().closerToCenterThan(entity.position(), SKIP_CLOSING_IF_FURTHER_AWAY_THAN);
    }

    private static boolean areOtherMobsComingThrough(LivingEntity entity, BlockPos pos,
                                                     Optional<List<LivingEntity>> nearby) {
        if (nearby.isEmpty()) return false;
        return nearby.get().stream()
                .filter(e -> e.getType() == entity.getType())
                .filter(e -> pos.closerToCenterThan(e.position(), MAX_DISTANCE_TO_HOLD_OPEN_FOR_OTHER_MOBS))
                .anyMatch(e -> isMobPathingThrough(e.getBrain(), pos));
    }

    private static boolean isMobPathingThrough(Brain<?> brain, BlockPos pos) {
        if (!brain.hasMemoryValue(MemoryModuleType.PATH)) return false;
        Path path = brain.getMemory(MemoryModuleType.PATH).get();
        if (path.isDone()) return false;
        Node prev = path.getPreviousNode();
        if (prev == null) return false;
        Node next = path.getNextNode();
        return pos.equals(prev.asBlockPos()) || pos.equals(next.asBlockPos());
    }
}
