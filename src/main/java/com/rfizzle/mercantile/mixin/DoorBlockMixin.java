package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DoorBlock.class)
public abstract class DoorBlockMixin {

    @Unique
    private static final ThreadLocal<Boolean> mercantile$handlingDoubleDoor =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "setOpen", at = @At("TAIL"))
    private void mercantile$handleDoubleDoor(@Nullable Entity entity, Level level, BlockState state,
                                             BlockPos pos, boolean open, CallbackInfo ci) {
        MercantileConfig config = MercantileConfig.get();
        if (entity instanceof Villager) {
            if (!config.enablePathfindingFixes) return;
            if (!config.enablePathfindingDoors) return;
        } else if (entity instanceof Player player) {
            if (player.isSpectator()) return;
            if (!config.enableDoubleDoorSync) return;
        } else {
            return;
        }

        if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) return;

        if (mercantile$handlingDoubleDoor.get()) return;

        Direction facing = state.getValue(DoorBlock.FACING);
        DoorHingeSide hinge = state.getValue(DoorBlock.HINGE);
        Direction partnerDir = hinge == DoorHingeSide.LEFT
                ? facing.getClockWise()
                : facing.getCounterClockWise();
        BlockPos partnerPos = pos.relative(partnerDir);
        BlockState partnerState = level.getBlockState(partnerPos);

        if (partnerState.is(state.getBlock())
                && partnerState.getBlock() instanceof DoorBlock partnerDoor
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

    @Inject(method = "useWithoutItem", at = @At("TAIL"))
    private void mercantile$handleDoubleDoorOnUse(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (player.isSpectator()) return;
        if (!MercantileConfig.get().enableDoubleDoorSync) return;

        BlockState updatedState = level.getBlockState(pos);
        if (!(updatedState.getBlock() instanceof DoorBlock)) return;

        // Resolve to lower half regardless of which half was clicked
        BlockPos lowerPos;
        if (updatedState.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            lowerPos = pos.below();
            updatedState = level.getBlockState(lowerPos);
            if (!(updatedState.getBlock() instanceof DoorBlock)) return;
            if (updatedState.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) return;
        } else {
            lowerPos = pos;
        }

        boolean open = updatedState.getValue(DoorBlock.OPEN);
        Direction facing = updatedState.getValue(DoorBlock.FACING);
        DoorHingeSide hinge = updatedState.getValue(DoorBlock.HINGE);

        Direction partnerDir = hinge == DoorHingeSide.LEFT
                ? facing.getClockWise()
                : facing.getCounterClockWise();
        BlockPos partnerPos = lowerPos.relative(partnerDir);
        BlockState partnerState = level.getBlockState(partnerPos);

        if (partnerState.is(updatedState.getBlock())
                && partnerState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && partnerState.getValue(DoorBlock.FACING) == facing
                && partnerState.getValue(DoorBlock.HINGE) != hinge
                && partnerState.getValue(DoorBlock.OPEN) != open) {
            // setBlock with flag 10 triggers updateNeighbourShapes, which propagates OPEN to partner UPPER via DoorBlock.updateShape
            level.setBlock(partnerPos, partnerState.cycle(DoorBlock.OPEN), 10);
            BlockSetType blockSetType = ((DoorBlockAccessor) (Object) this).mercantile$getType();
            SoundEvent sound = open ? blockSetType.doorOpen() : blockSetType.doorClose();
            level.playSound(null, partnerPos, sound, SoundSource.BLOCKS,
                    1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
            level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, partnerPos);
        }
    }
}
