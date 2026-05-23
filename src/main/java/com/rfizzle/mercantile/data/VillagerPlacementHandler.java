package com.rfizzle.mercantile.data;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.config.MercantileConfig;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class VillagerPlacementHandler {

    private VillagerPlacementHandler() {}

    public static void init() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof PlayerHeadItem)) return InteractionResult.PASS;
            if (!stack.is(Items.PLAYER_HEAD)) return InteractionResult.PASS;

            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData == null) return InteractionResult.PASS;
            if (!customData.contains("MercantileDataVersion")) return InteractionResult.PASS;

            if (!MercantileConfig.get().enableVillagerPickup) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;

            CompoundTag nbt = customData.copyTag();
            int dataVersion = nbt.getInt("MercantileDataVersion");
            if (dataVersion > VillagerPickupHelper.CURRENT_DATA_VERSION) {
                ((ServerPlayer) player).displayClientMessage(
                        Component.translatable("mercantile.placement.newer_version")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.FAIL;
            }

            ServerLevel serverLevel = (ServerLevel) level;
            BlockPos targetPos = hit.getBlockPos().relative(hit.getDirection());
            Vec3 spawnVec = Vec3.atBottomCenterOf(targetPos);

            Villager villager;
            try {
                villager = EntityType.VILLAGER.create(level);
                if (villager == null) throw new IllegalStateException("Failed to create villager entity");
                villager.load(nbt);
            } catch (Exception e) {
                Mercantile.LOGGER.warn("Failed to deserialize villager from pickup item; keeping item", e);
                ((ServerPlayer) player).displayClientMessage(
                        Component.translatable("mercantile.placement.malformed_nbt")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.FAIL;
            }

            double dx = player.getX() - spawnVec.x;
            double dz = player.getZ() - spawnVec.z;
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;

            villager.moveTo(spawnVec.x, spawnVec.y, spawnVec.z, yaw, 0);
            villager.setYHeadRot(yaw);
            villager.setYBodyRot(yaw);
            villager.setDeltaMovement(Vec3.ZERO);
            villager.fallDistance = 0;

            stack.shrink(1);

            if (!serverLevel.addFreshEntity(villager)) {
                stack.grow(1);
                return InteractionResult.FAIL;
            }

            BlockState belowState = level.getBlockState(targetPos.below());
            if (!belowState.isAir()) {
                serverLevel.sendParticles(
                        new BlockParticleOption(ParticleTypes.BLOCK, belowState),
                        spawnVec.x, spawnVec.y + 0.1, spawnVec.z,
                        30, 0.3, 0.1, 0.3, 0.05);
            }

            level.playSound(null, spawnVec.x, spawnVec.y, spawnVec.z,
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.0f);

            return InteractionResult.SUCCESS;
        });
    }
}
