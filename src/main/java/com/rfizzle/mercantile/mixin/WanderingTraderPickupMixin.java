package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.VillagerPickupHelper;
import com.rfizzle.mercantile.particle.MercantileParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Intercepts {@link WanderingTrader#mobInteract} (HEAD, cancellable) to implement
 * wandering trader pickup via shift+right-click with an empty hand.
 *
 * <p>Targets {@link WanderingTrader} directly (the narrowest class that declares
 * {@code mobInteract}), keeping vanilla {@link net.minecraft.world.entity.npc.Villager}
 * pickup on the sibling {@link VillagerPickupMixin}.</p>
 *
 * <p>Uses default priority (1000), matching {@link VillagerPickupMixin}. Pickup discards
 * the entity, so its early-exit guards must evaluate before any sibling mixin that might
 * consume the interaction; future trader-follow or trader-trade mixins targeting
 * {@code mobInteract} on this class should use a higher priority value (e.g., 1100) so
 * this pickup runs first.</p>
 */
@Mixin(WanderingTrader.class)
public abstract class WanderingTraderPickupMixin {

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void mercantile$tryPickupWanderingTrader(Player player, InteractionHand hand,
                                                     CallbackInfoReturnable<InteractionResult> cir) {
        WanderingTrader wt = (WanderingTrader) (Object) this;

        if (!MercantileConfig.get().enableVillagerPickup) return;
        if (hand != InteractionHand.MAIN_HAND) return;
        if (!player.isShiftKeyDown()) return;
        if (!player.getMainHandItem().isEmpty()) return;

        if (wt.level().isClientSide) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        ServerLevel serverLevel = (ServerLevel) wt.level();
        MercantileConfig config = MercantileConfig.get();

        if (wt.getTradingPlayer() != null && wt.getTradingPlayer() != player) {
            serverPlayer.displayClientMessage(
                    Component.translatable("notification.mercantile.pickup.denied.trader_trading")
                            .withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        if (!player.getAbilities().instabuild && player.experienceLevel < config.pickupXpCost) {
            serverPlayer.displayClientMessage(
                    Component.translatable("notification.mercantile.pickup.not_enough_xp")
                            .withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        ItemStack headItem = VillagerPickupHelper.createHeadItem(wt);

        if (!player.getAbilities().instabuild) {
            player.giveExperienceLevels(-config.pickupXpCost);
        }

        double x = wt.getX(), y = wt.getY(), z = wt.getZ();
        double midY = y + wt.getBbHeight() * 0.5;

        player.setItemInHand(InteractionHand.MAIN_HAND, headItem);

        player.level().playSound(null, x, y, z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.0f);
        serverLevel.sendParticles(MercantileParticles.PICKUP_SPARKLE,
                x, midY, z, 18, 0.3, 0.5, 0.3, 0.03);

        serverPlayer.displayClientMessage(
                Component.translatable("notification.mercantile.pickup.success_trader")
                        .withStyle(ChatFormatting.GREEN), true);

        // Order matters: detach llamas before discard so getLeashHolder() can
        // still resolve the trader as their holder. After discard() the leash
        // holder reference would be gone and we'd miss every leashed llama.
        mercantile$detachLeashedLlamas(wt, serverLevel);

        wt.discard();

        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    @Unique
    private void mercantile$detachLeashedLlamas(WanderingTrader trader, ServerLevel level) {
        AABB scanBox = trader.getBoundingBox().inflate(12.0);
        List<TraderLlama> leashed = level.getEntitiesOfClass(
                TraderLlama.class, scanBox,
                llama -> llama.getLeashHolder() == trader);

        // TraderLlama tracks its parent trader via getLeashHolder() — there is no
        // brain-memory link to clear on MC 1.21.1. Dropping the leash is sufficient.
        for (TraderLlama llama : leashed) {
            llama.dropLeash(true, true);
        }
    }
}
