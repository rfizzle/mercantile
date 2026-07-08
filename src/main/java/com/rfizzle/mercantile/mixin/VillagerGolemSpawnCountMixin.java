package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.block.SentryGolemTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.GolemSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Filter Mercantile sentry golems out of the villager iron-golem "cap" check so an active
 * pylon does not block normal iron farms. Targets {@link GolemSensor#checkForNearbyGolem}
 * because that is where {@code GOLEM_DETECTED_RECENTLY} (the memory that gates
 * {@code Villager#wantsToSpawnGolem}) is set from nearby IronGolems.
 *
 * <p>Rather than reimplement and cancel the method, this redirects only the single
 * {@code anyMatch} that decides whether a golem is nearby, replacing it with an indexed
 * scan that answers "is any <em>non-sentry</em> iron golem present?" instead of vanilla's
 * "any iron golem". The rest of the vanilla body
 * — the memory read, the {@code golemDetected} call, the expiry — runs unchanged, so the
 * injector composes with future Mojang edits and fails loudly at load if the call site
 * moves. The check is an allocation-free indexed loop, not a fresh stream per run.
 */
@Mixin(GolemSensor.class)
public abstract class VillagerGolemSpawnCountMixin {

    @Redirect(
            method = "checkForNearbyGolem",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;anyMatch(Ljava/util/function/Predicate;)Z"))
    private static boolean mercantile$anyNonSentryGolem(
            Stream<LivingEntity> vanillaStream,
            Predicate<? super LivingEntity> vanillaPredicate,
            LivingEntity livingEntity) {
        List<LivingEntity> nearby = livingEntity.getBrain()
                .getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES)
                .orElse(List.of());
        for (int i = 0, n = nearby.size(); i < n; i++) {
            LivingEntity entity = nearby.get(i);
            if (entity.getType() == EntityType.IRON_GOLEM && !SentryGolemTag.isSentry(entity)) {
                return true;
            }
        }
        return false;
    }
}
