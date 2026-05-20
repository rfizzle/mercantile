package com.rfizzle.mercantile.pathfinding;

import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class ClimbLadder {
    private static final double CLIMB_SPEED = 0.2;

    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(
            instance -> instance.group(
                instance.present(MemoryModuleType.PATH)
            ).apply(instance, pathAccessor -> (serverLevel, entity, tick) -> {
                if (!MercantileConfig.get().enablePathfindingFixes
                        || !MercantileConfig.get().enablePathfindingLadders) {
                    return false;
                }

                if (!entity.onClimbable()) return false;

                Path path = instance.get(pathAccessor);
                if (path.notStarted() || path.isDone()) return false;

                Node nextNode = path.getNextNode();
                int mobX = entity.getBlockX();
                int mobY = entity.getBlockY();
                int mobZ = entity.getBlockZ();

                if (nextNode.x != mobX || nextNode.z != mobZ) return false;

                Vec3 delta = entity.getDeltaMovement();
                if (nextNode.y > mobY) {
                    entity.setDeltaMovement(delta.x, CLIMB_SPEED, delta.z);
                    return true;
                } else if (nextNode.y < mobY) {
                    entity.setDeltaMovement(delta.x, -CLIMB_SPEED, delta.z);
                    return true;
                }

                return false;
            })
        );
    }
}