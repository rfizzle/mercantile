package com.rfizzle.mercantile.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.rfizzle.mercantile.pathfinding.ClimbLadder;
import com.rfizzle.mercantile.pathfinding.InteractWithFenceGate;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerGoalPackages.class)
public abstract class VillagerGoalPackagesMixin {

    @Inject(method = "getCorePackage", at = @At("RETURN"), cancellable = true)
    private static void mercantile$addFenceGateBehavior(
            VillagerProfession profession, float speed,
            CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
        var original = cir.getReturnValue();
        cir.setReturnValue(
                ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>builder()
                        .addAll(original)
                        .add(Pair.of(0, InteractWithFenceGate.create()))
                        .add(Pair.of(0, ClimbLadder.create()))
                        .build()
        );
    }
}
