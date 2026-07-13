package com.rfizzle.mercantile.block;

import com.mojang.serialization.MapCodec;
import com.rfizzle.mercantile.advancement.MercantileCriteria;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.particle.MercantileParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SentryPylonBlock extends BaseEntityBlock {
    public static final EnumProperty<PylonStateProperty> STATE =
            EnumProperty.create("state", PylonStateProperty.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public static final MapCodec<SentryPylonBlock> CODEC = simpleCodec(SentryPylonBlock::new);

    public SentryPylonBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(STATE, PylonStateProperty.EMPTY)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SentryPylonBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof SentryPylonBlockEntity pylon) {
                pylon.tickServerCommon();
            }
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        PylonStateProperty pylonState = state.getValue(STATE);
        if (pylonState == PylonStateProperty.EMPTY) return;
        if (pylonState == PylonStateProperty.IDLE) {
            if (random.nextInt(4) != 0) return;
            double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
            double y = pos.getY() + 1.0 + random.nextDouble() * 0.1;
            double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
            level.addParticle(MercantileParticles.PYLON_MOTE, x, y, z, 0.0, 0.0, 0.0);
        } else if (pylonState == PylonStateProperty.ACTIVE) {
            if (random.nextInt(2) != 0) return;
            double x = pos.getX() + 0.25 + random.nextDouble() * 0.5;
            double y = pos.getY() + 0.95 + random.nextDouble() * 0.1;
            double z = pos.getZ() + 0.25 + random.nextDouble() * 0.5;
            level.addParticle(MercantileParticles.PYLON_SPARK, x, y, z, 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!MercantileConfig.get().enableSentryPylon) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!stack.is(Items.IRON_BLOCK)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof SentryPylonBlockEntity pylon)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (pylon.getFuel() >= pylon.getMaxFuel()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            pylon.addFuel(1);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.4f);
            if (player instanceof ServerPlayer serverPlayer) {
                MercantileCriteria.PYLON_FUELED.trigger(serverPlayer);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) return;
        boolean signal = level.hasNeighborSignal(pos);
        if (signal != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, signal), Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(pos) instanceof SentryPylonBlockEntity pylon) {
                pylon.updateVisualState();
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        // Fire only on genuine removal/replacement — mining, explosion, /setblock — not on a
        // POWERED/STATE property flip (same block) and never on chunk unload (which doesn't route
        // through onRemove at all). Dismiss the pylon's sentries before super removes the block entity,
        // so temporary summons don't outlive their pylon (issue #166).
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof SentryPylonBlockEntity pylon) {
            pylon.onPylonRemoved(server);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SentryPylonBlockEntity pylon)) {
            return 0;
        }
        int max = pylon.getMaxFuel();
        if (max <= 0) return 0;
        int fuel = pylon.getFuel();
        if (fuel <= 0) return 0;
        if (fuel >= max) return 15;
        return Mth.clamp(Mth.ceil((fuel / (float) max) * 15.0f), 1, 15);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STATE, POWERED);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.mercantile.sentry_pylon.fuel")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.mercantile.sentry_pylon.sentries")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.mercantile.sentry_pylon.bell")
                .withStyle(ChatFormatting.GRAY));
    }
}
