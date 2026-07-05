package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.rehab.NitwitRehab;
import com.rfizzle.mercantile.rehab.NitwitRehabManager;
import com.rfizzle.mercantile.reputation.ReputationManager;
import com.rfizzle.mercantile.trade.EmeraldPayment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts {@code Villager#mobInteract} (HEAD, cancellable) to rehabilitate nitwits: a Trusted+
 * player using a golden apple on an adult nitwit pays the apple plus a configurable emerald fee,
 * and after a short delay the nitwit converts to an unemployed villager (see
 * {@link NitwitRehabManager}). The item-hand guards across the mobInteract mixins are mutually
 * exclusive: {@link VillagerPickupMixin} requires an empty hand, {@link VillagerFollowMixin}
 * requires an emerald, {@link VillagerBabyFeedMixin} requires a villager breeding food
 * (bread/carrot/potato/beetroot — never a golden apple), {@link VillagerWorkOrderMixin} requires
 * a workstation block item, and this mixin requires a golden apple in the main hand targeting a
 * nitwit. Future authors must preserve this invariant so no two mobInteract injections can fire
 * for the same interaction.
 */
@Mixin(Villager.class)
public abstract class VillagerNitwitRehabMixin extends AbstractVillager {

    protected VillagerNitwitRehabMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void mercantile$tryRehabNitwit(Player player, InteractionHand hand,
                                           CallbackInfoReturnable<InteractionResult> cir) {
        if (!MercantileConfig.get().enableNitwitRehab) return;
        if (hand != InteractionHand.MAIN_HAND) return;
        if (!player.getMainHandItem().is(Items.GOLDEN_APPLE)) return;

        Villager self = (Villager) (Object) this;
        if (self.getVillagerData().getProfession() != VillagerProfession.NITWIT) return;
        // This HEAD injection runs before vanilla's own isAlive/isSleeping gate — replicate it so
        // a dying or sleeping nitwit is never charged.
        if (!self.isAlive() || self.isSleeping()) return;

        if (self.level().isClientSide) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        MercantileConfig config = MercantileConfig.get();

        // Already paid for and mid-delay — swallow the interaction so a second apple isn't burned.
        if (NitwitRehabManager.isPending(self.getUUID())) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        if (self.isBaby()) {
            self.playSound(SoundEvents.VILLAGER_NO, 1.0f, self.getVoicePitch());
            serverPlayer.displayClientMessage(
                    Component.translatable("mercantile.rehab.denied.baby")
                            .withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        if (config.enableReputation) {
            PlayerData data = serverPlayer.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            ReputationManager.migrateIfNeeded(data);
            if (!NitwitRehab.meetsReputationRequirement(true, data.getScore())) {
                self.playSound(SoundEvents.VILLAGER_NO, 1.0f, self.getVoicePitch());
                serverPlayer.displayClientMessage(
                        Component.translatable("mercantile.rehab.denied.reputation",
                                NitwitRehab.REQUIRED_TIER.displayName())
                                .withStyle(ChatFormatting.RED), true);
                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }
        }

        boolean creative = player.getAbilities().instabuild;
        if (!NitwitRehab.canAfford(creative, EmeraldPayment.count(serverPlayer), config.nitwitRehabEmeraldCost)) {
            self.playSound(SoundEvents.VILLAGER_NO, 1.0f, self.getVoicePitch());
            serverPlayer.displayClientMessage(
                    Component.translatable("mercantile.rehab.denied.cost",
                            config.nitwitRehabEmeraldCost)
                            .withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        ItemStack apple = player.getMainHandItem();
        SoundEvent eatingSound = this.getEatingSound(apple);
        if (!creative) {
            apple.shrink(1);
            EmeraldPayment.remove(serverPlayer, config.nitwitRehabEmeraldCost);
        }

        NitwitRehabManager.schedule(self, serverPlayer);
        self.playSound(eatingSound, 1.0f, self.getVoicePitch());
        serverPlayer.displayClientMessage(
                Component.translatable("mercantile.rehab.start")
                        .withStyle(ChatFormatting.GREEN), true);

        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
