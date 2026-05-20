package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.VillagerPickupHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class VillagerPlacementMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void mercantile$tryPlaceVillager(UseOnContext context,
                                             CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = context.getItemInHand();
        if (!stack.is(Items.PLAYER_HEAD)) return;

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return;

        CompoundTag nbt = customData.copyTag();
        if (!nbt.contains("MercantileDataVersion")) return;

        Level level = context.getLevel();
        if (level.isClientSide) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        Player player = context.getPlayer();
        if (player == null) return;

        if (!MercantileConfig.get().enableVillagerPickup) return;

        int dataVersion = nbt.getInt("MercantileDataVersion");
        if (dataVersion > VillagerPickupHelper.CURRENT_DATA_VERSION) {
            ((ServerPlayer) player).displayClientMessage(
                    Component.translatable("mercantile.placement.newer_version")
                            .withStyle(ChatFormatting.RED),
                    true);
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        BlockPos targetPos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 spawnVec = Vec3.atBottomCenterOf(targetPos);

        Villager villager;
        try {
            villager = EntityType.VILLAGER.create(level);
            if (villager == null) throw new IllegalStateException("Failed to create villager entity");
            villager.load(nbt);
        } catch (Exception e) {
            Mercantile.LOGGER.warn("Failed to deserialize villager from pickup item, spawning default", e);
            villager = EntityType.VILLAGER.create(level);
            if (villager == null) {
                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }
        }

        double dx = player.getX() - spawnVec.x;
        double dz = player.getZ() - spawnVec.z;
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;

        villager.moveTo(spawnVec.x, spawnVec.y, spawnVec.z, yaw, 0);
        villager.setYHeadRot(yaw);
        villager.setYBodyRot(yaw);
        villager.setDeltaMovement(Vec3.ZERO);
        villager.fallDistance = 0;

        serverLevel.addFreshEntity(villager);

        stack.shrink(1);

        BlockState belowState = level.getBlockState(targetPos.below());
        if (!belowState.isAir()) {
            serverLevel.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, belowState),
                    spawnVec.x, spawnVec.y + 0.1, spawnVec.z,
                    30, 0.3, 0.1, 0.3, 0.05);
        }

        level.playSound(null, spawnVec.x, spawnVec.y, spawnVec.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.0f);

        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
