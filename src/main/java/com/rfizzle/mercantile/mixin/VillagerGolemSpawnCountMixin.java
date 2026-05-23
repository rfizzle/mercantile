package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.block.SentryGolemTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.GolemSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

/**
 * Filter Mercantile sentry golems out of the villager iron-golem "cap" check so an active
 * pylon does not block normal iron farms. Targets {@link GolemSensor#checkForNearbyGolem}
 * because that is where {@code GOLEM_DETECTED_RECENTLY} (the memory that gates
 * {@code Villager#wantsToSpawnGolem}) is set from nearby IronGolems.
 */
@Mixin(GolemSensor.class)
public abstract class VillagerGolemSpawnCountMixin {

    @Inject(method = "checkForNearbyGolem", at = @At("HEAD"), cancellable = true)
    private static void mercantile$ignoreSentriesForGolemDetection(LivingEntity livingEntity, CallbackInfo ci) {
        Optional<List<LivingEntity>> optional = livingEntity.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES);
        if (optional.isEmpty()) return;
        boolean foundNonSentry = optional.get().stream()
                .anyMatch(e -> e.getType().equals(EntityType.IRON_GOLEM) && !SentryGolemTag.isSentry(e));
        if (foundNonSentry) {
            GolemSensor.golemDetected(livingEntity);
        }
        ci.cancel();
    }
}
