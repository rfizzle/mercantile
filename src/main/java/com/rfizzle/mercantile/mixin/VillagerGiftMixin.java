package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.advancement.MercantileCriteria;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.GiftMappingManager;
import com.rfizzle.mercantile.reputation.ReputationManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerGiftMixin {

    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void mercantile$wantsToPickUpGift(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!MercantileConfig.get().enableGifting) return;

        Villager self = (Villager) (Object) this;
        ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(self.getVillagerData().getProfession());
        if (key == null) return;
        String profession = key.getPath();

        if (GiftMappingManager.isValidGift(profession, stack.getItem())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "pickUpItem", at = @At("HEAD"))
    private void mercantile$pickUpGift(ItemEntity itemEntity, CallbackInfo ci) {
        if (!MercantileConfig.get().enableGifting) return;

        Villager self = (Villager) (Object) this;
        if (self.level().isClientSide) return;

        ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(self.getVillagerData().getProfession());
        if (key == null) return;
        String profession = key.getPath();
        ItemStack stack = itemEntity.getItem();

        if (GiftMappingManager.isValidGift(profession, stack.getItem())) {
            if (((ItemEntityAccessor) itemEntity).getTarget() != null) {
                ServerPlayer player = (ServerPlayer) self.level().getPlayerByUUID(((ItemEntityAccessor) itemEntity).getTarget());
                if (player != null) {
                    ReputationManager.CapDecision decision = ReputationManager.tryGainGiftRep(player);
                    // Confirm the gift landed only when rep was actually awarded; a cap hit already
                    // sent its own daily-cap action-bar line, so a second message would compete.
                    if (decision == ReputationManager.CapDecision.AWARDED && player.connection != null) {
                        player.displayClientMessage(Component.translatable(
                                "message.mercantile.gift_accepted", self.getDisplayName()), true);
                    }
                    // The villager accepted the gift regardless of the daily rep cap.
                    MercantileCriteria.GIFT_ACCEPTED.trigger(player);
                }
            }
            // Emit happy particles
            self.level().broadcastEntityEvent(self, (byte) 14);
        }
    }
}
