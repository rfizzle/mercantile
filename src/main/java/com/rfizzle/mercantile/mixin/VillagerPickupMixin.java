package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.VillagerPickupHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerPickupMixin {

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void mercantile$tryPickupVillager(Player player, InteractionHand hand,
                                              CallbackInfoReturnable<InteractionResult> cir) {
        if (!MercantileConfig.get().enableVillagerPickup) return;
        if (hand != InteractionHand.MAIN_HAND) return;
        if (!player.isShiftKeyDown()) return;
        if (!player.getMainHandItem().isEmpty()) return;

        Villager self = (Villager) (Object) this;

        if (self.level().isClientSide) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        ServerLevel serverLevel = (ServerLevel) self.level();
        MercantileConfig config = MercantileConfig.get();

        Raid raid = serverLevel.getRaidAt(self.blockPosition());
        if (raid != null && raid.isActive()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("mercantile.pickup.denied.raid")
                            .withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        if (self.getTradingPlayer() != null && self.getTradingPlayer() != player) {
            serverPlayer.displayClientMessage(
                    Component.translatable("mercantile.pickup.denied.trading")
                            .withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        // Follow mode denial will be added when S-018 is implemented

        if (!player.getAbilities().instabuild && player.experienceLevel < config.pickupXpCost) {
            serverPlayer.displayClientMessage(
                    Component.translatable("mercantile.pickup.not_enough_xp")
                            .withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        ItemStack headItem = VillagerPickupHelper.createHeadItem(self);

        if (!player.getAbilities().instabuild) {
            player.giveExperienceLevels(-config.pickupXpCost);
        }

        double x = self.getX(), y = self.getY(), z = self.getZ();
        self.discard();

        player.setItemInHand(InteractionHand.MAIN_HAND, headItem);

        player.level().playSound(null, x, y, z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.0f);

        serverPlayer.displayClientMessage(
                Component.translatable("mercantile.pickup.success")
                        .withStyle(ChatFormatting.GREEN), true);

        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
