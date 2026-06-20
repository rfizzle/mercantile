package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BellBlock.class)
public abstract class BellBlockMixin {

    // Volume that makes the variable-range bell carry 96 blocks (audible radius = 16 × volume),
    // matching BellRingBroadcaster's 96-block villager-glow broadcast so a distant player who can
    // *see* glowing villagers also *hears* the ring to turn toward it.
    private static final float MERCANTILE_GLOW_RANGE_VOLUME = 6.0f;

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

    // Extend the bell ring's own audible range to match the glow broadcast (any bell, any trigger),
    // so distant players hear it instead of seeing silent glowing villagers. Gated on the same
    // toggle as the glow visualization; when off, the bell keeps its vanilla ~32-block reach.
    @ModifyArg(
            method = "attemptToRing(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"),
            index = 4)
    private float mercantile$extendBellRingRange(float volume) {
        return MercantileConfig.get().enableBellRadiusVis ? MERCANTILE_GLOW_RANGE_VOLUME : volume;
    }
}
