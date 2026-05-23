package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.network.BellRingBroadcaster;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BellBlock.class)
public abstract class BellBlockMixin {

    @Inject(
            method = "onHit(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/entity/player/Player;Z)Z",
            at = @At("RETURN"))
    private void mercantile$onBellRung(Level level, BlockState state, BlockHitResult hitResult,
                                       @Nullable Player player, boolean canRingFrom,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        BellRingBroadcaster.broadcast(serverLevel, hitResult.getBlockPos());
    }
}
