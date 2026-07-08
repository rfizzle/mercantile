package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.advancement.MercantileCriteria;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.trade.EmeraldPayment;
import com.rfizzle.mercantile.workorder.WorkOrder;
import com.rfizzle.mercantile.workorder.WorkOrderService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Intercepts {@code Villager#mobInteract} (HEAD, cancellable) to issue work orders (issue #90):
 * sneak + right-clicking an unemployed adult villager with a profession workstation item sends it
 * to claim the nearest unclaimed workstation of that type for a configurable emerald fee (waived
 * in creative). The item identifies the job and is never consumed. The item-hand guards across
 * the mobInteract mixins are mutually exclusive: {@link VillagerPickupMixin} requires an empty
 * hand, {@link VillagerFollowMixin} requires an emerald, {@link VillagerBabyFeedMixin} requires a
 * villager breeding food, {@link VillagerNitwitRehabMixin} requires a golden apple,
 * {@link VillagerContractMixin} requires paper or the delivery-contract item, and this
 * mixin requires a workstation block item (an acquirable job-site POI block — never empty, an
 * emerald, food, a golden apple, paper, or a contract). Future authors must preserve this
 * invariant so no two mobInteract injections can fire for the same interaction.
 */
@Mixin(Villager.class)
public abstract class VillagerWorkOrderMixin extends AbstractVillager {

    protected VillagerWorkOrderMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void mercantile$tryPlaceWorkOrder(Player player, InteractionHand hand,
                                              CallbackInfoReturnable<InteractionResult> cir) {
        if (!MercantileConfig.get().enableWorkOrders) return;
        if (hand != InteractionHand.MAIN_HAND) return;
        if (!player.isShiftKeyDown()) return;

        Optional<Holder<PoiType>> poiType = WorkOrderService.resolveJobSitePoi(player.getMainHandItem());
        if (poiType.isEmpty()) return;

        Villager self = (Villager) (Object) this;
        // Employed villagers, nitwits (profession NITWIT, not NONE), and babies fall through to
        // vanilla untouched.
        if (!WorkOrder.isEligibleTarget(self.isBaby(),
                self.getVillagerData().getProfession() == VillagerProfession.NONE)) {
            return;
        }
        // This HEAD injection runs before vanilla's own isAlive/isSleeping gate — replicate it so
        // a dying or sleeping villager is never ordered around or charged for.
        if (!self.isAlive() || self.isSleeping()) return;

        if (self.level().isClientSide) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        MercantileConfig config = MercantileConfig.get();
        boolean creative = player.getAbilities().instabuild;

        if (!WorkOrder.canAfford(creative, EmeraldPayment.count(serverPlayer), config.workOrderEmeraldCost)) {
            self.playSound(SoundEvents.VILLAGER_NO, 1.0f, self.getVoicePitch());
            serverPlayer.displayClientMessage(
                    Component.translatable("notification.mercantile.workorder.denied.cost",
                            config.workOrderEmeraldCost)
                            .withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        ServerLevel serverLevel = (ServerLevel) self.level();
        Optional<BlockPos> target = WorkOrderService.placeOrder(serverLevel, self, poiType.get());
        if (target.isEmpty()) {
            self.playSound(SoundEvents.VILLAGER_NO, 1.0f, self.getVoicePitch());
            serverPlayer.displayClientMessage(
                    Component.translatable("notification.mercantile.workorder.denied.no_workstation",
                            player.getMainHandItem().getHoverName())
                            .withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        if (!creative) {
            EmeraldPayment.remove(serverPlayer, config.workOrderEmeraldCost);
        }
        self.playSound(SoundEvents.VILLAGER_YES, 1.0f, self.getVoicePitch());
        // Entity event 14 = vanilla green "happy villager" particles.
        serverLevel.broadcastEntityEvent(self, (byte) 14);
        serverPlayer.displayClientMessage(
                Component.translatable("notification.mercantile.workorder.accepted",
                        player.getMainHandItem().getHoverName())
                        .withStyle(ChatFormatting.GREEN), true);

        MercantileCriteria.WORK_ORDER_ASSIGNED.trigger(serverPlayer);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
