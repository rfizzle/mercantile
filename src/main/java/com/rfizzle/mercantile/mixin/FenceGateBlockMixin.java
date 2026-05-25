package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FenceGateBlock.class)
public abstract class FenceGateBlockMixin {

    @Inject(method = "useWithoutItem", at = @At("TAIL"))
    private void mercantile$handleDoubleGate(BlockState state, Level level, BlockPos pos, Player player,
                                             BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (player.isSpectator()) return;
        if (!MercantileConfig.get().enableDoubleDoorSync) return;

        BlockState updatedState = level.getBlockState(pos);
        if (!(updatedState.getBlock() instanceof FenceGateBlock)) return;

        boolean open = updatedState.getValue(FenceGateBlock.OPEN);
        Direction facing = updatedState.getValue(FenceGateBlock.FACING);

        Direction[] partnerDirs = { facing.getClockWise(), facing.getCounterClockWise(), Direction.UP, Direction.DOWN };
        for (Direction partnerDir : partnerDirs) {
            BlockPos partnerPos = pos.relative(partnerDir);
            BlockState partnerState = level.getBlockState(partnerPos);

            if (!partnerState.is(updatedState.getBlock())) continue;
            // Use axis comparison rather than exact direction: vanilla may flip the clicked gate's
            // FACING (NORTH→SOUTH) based on approach direction, so NORTH and SOUTH must still match.
            if (partnerState.getValue(FenceGateBlock.FACING).getAxis() != facing.getAxis()) continue;
            if (partnerState.getValue(FenceGateBlock.POWERED)) continue;
            if (partnerState.getValue(FenceGateBlock.OPEN) == open) continue;

            BlockState newPartnerState = partnerState.setValue(FenceGateBlock.OPEN, open);
            level.setBlock(partnerPos, newPartnerState, 10);

            WoodType type = ((FenceGateBlockAccessor) (Object) this).mercantile$getType();
            SoundEvent sound = open ? type.fenceGateOpen() : type.fenceGateClose();
            level.playSound(null, partnerPos, sound, SoundSource.BLOCKS,
                    1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
            level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, partnerPos);
            break;
        }
    }
}
