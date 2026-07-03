package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.breeding.BabyFeeding;
import com.rfizzle.mercantile.compat.BreedingTooltipData;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.MercantileVillagerData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts {@code Villager#mobInteract} (HEAD, cancellable) to feed baby villagers: a villager
 * food item in the main hand is consumed and removes a food-value-weighted percentage of the
 * remaining growth time, up to a per-baby cumulative cap (see {@link BabyFeeding}). The item-hand
 * guards across the mobInteract mixins are mutually exclusive: {@link VillagerPickupMixin}
 * requires an empty hand, {@link VillagerFollowMixin} requires an emerald, and this mixin
 * requires a baby villager and a villager food item in the main hand. Future authors must
 * preserve this invariant so no two mobInteract injections can fire for the same interaction.
 */
@Mixin(Villager.class)
public abstract class VillagerBabyFeedMixin extends AbstractVillager {

    protected VillagerBabyFeedMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void mercantile$tryFeedBaby(Player player, InteractionHand hand,
                                        CallbackInfoReturnable<InteractionResult> cir) {
        if (!MercantileConfig.get().enableBabyFeeding) return;
        if (hand != InteractionHand.MAIN_HAND) return;

        Villager self = (Villager) (Object) this;
        if (!self.isBaby()) return;

        ItemStack stack = player.getMainHandItem();
        Integer foodPoints = BreedingTooltipData.foodPoints().get(stack.getItem());
        if (foodPoints == null) return;

        if (self.level().isClientSide) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        MercantileConfig config = MercantileConfig.get();
        MercantileVillagerData data = self.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);

        int remaining = Math.max(0, -self.getAge());
        int budget = BabyFeeding.remainingBudget(data.getFedGrowthTicks(), config.babyFeedMaxReductionPercent);
        int reduction = Math.min(
                BabyFeeding.computeReduction(remaining, foodPoints, config.babyFeedPercentPerFeed),
                budget);

        if (reduction <= 0) {
            self.playSound(SoundEvents.VILLAGER_NO, 1.0f, self.getVoicePitch());
            player.displayClientMessage(
                    Component.translatable("mercantile.feeding.capped")
                            .withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        self.setAge(Math.min(0, self.getAge() + reduction));
        data.setFedGrowthTicks(data.getFedGrowthTicks() + reduction);
        self.setAttached(MercantileAttachments.VILLAGER_DATA, data);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        self.playSound(this.getEatingSound(stack), 1.0f, self.getVoicePitch());
        // Entity event 14 = vanilla green "happy villager" particles.
        self.level().broadcastEntityEvent(self, (byte) 14);

        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
