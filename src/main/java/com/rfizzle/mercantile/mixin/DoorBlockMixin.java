package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DoorBlock.class)
public abstract class DoorBlockMixin {

    @Unique
    private static final ThreadLocal<Boolean> mercantile$handlingDoubleDoor =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "setOpen", at = @At("TAIL"))
    private void mercantile$handleDoubleDoor(@Nullable Entity entity, Level level, BlockState state,
                                             BlockPos pos, boolean open, CallbackInfo ci) {
        if (!(entity instanceof Villager)) return;
        if (!MercantileConfig.get().enablePathfindingFixes) return;
        if (!MercantileConfig.get().enablePathfindingDoors) return;

        if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) return;

        if (mercantile$handlingDoubleDoor.get()) return;

        Direction facing = state.getValue(DoorBlock.FACING);
        DoorHingeSide hinge = state.getValue(DoorBlock.HINGE);
        Direction partnerDir = hinge == DoorHingeSide.LEFT
                ? facing.getClockWise()
                : facing.getCounterClockWise();
        BlockPos partnerPos = pos.relative(partnerDir);
        BlockState partnerState = level.getBlockState(partnerPos);

        if (partnerState.getBlock() instanceof DoorBlock partnerDoor
                && partnerState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && partnerState.getValue(DoorBlock.FACING) == facing
                && partnerState.getValue(DoorBlock.HINGE) != hinge
                && partnerState.getValue(DoorBlock.OPEN) != open) {
            mercantile$handlingDoubleDoor.set(Boolean.TRUE);
            try {
                partnerDoor.setOpen(entity, level, partnerState, partnerPos, open);
            } finally {
                mercantile$handlingDoubleDoor.remove();
            }
        }
    }
}
