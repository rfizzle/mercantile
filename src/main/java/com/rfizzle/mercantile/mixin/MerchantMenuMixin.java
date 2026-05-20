package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin extends AbstractContainerMenu {

    @Shadow
    @Final
    private MerchantContainer tradeContainer;

    @Shadow
    private void playTradeSound() {}

    protected MerchantMenuMixin(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void mercantile$bulkQuickMove(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        if (!MercantileConfig.get().enableBulkTrading) return;
        if (slotIndex != 2) return;

        Slot resultSlot = this.slots.get(2);
        if (!resultSlot.hasItem()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        MerchantOffer offer = this.tradeContainer.getActiveOffer();
        if (offer == null || offer.isOutOfStock()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        int lockedPrice = offer.getSpecialPriceDiff();
        ItemStack firstResult = ItemStack.EMPTY;
        int tradeCount = 0;

        for (int t = 0; t < 64; t++) {
            offer.setSpecialPriceDiff(lockedPrice);

            MerchantOffer currentOffer = this.tradeContainer.getActiveOffer();
            if (currentOffer != offer || currentOffer.isOutOfStock()) break;
            if (!resultSlot.hasItem()) break;

            ItemStack resultItem = resultSlot.getItem();
            ItemStack resultCopy = resultItem.copy();

            if (!this.moveItemStackTo(resultItem, 3, 39, true)) break;

            resultSlot.onQuickCraft(resultItem, resultCopy);

            if (resultItem.isEmpty()) {
                resultSlot.setByPlayer(ItemStack.EMPTY);
            } else {
                resultSlot.setChanged();
            }

            resultSlot.onTake(player, resultItem);

            if (firstResult.isEmpty()) {
                firstResult = resultCopy;
            }
            tradeCount++;

            mercantile$refillPaymentSlots(offer);
        }

        if (tradeCount > 0) {
            this.playTradeSound();
        }

        if (tradeCount > 1) {
            player.displayClientMessage(
                    Component.translatable("gui.mercantile.bulk_trade.feedback", tradeCount, firstResult.getHoverName()),
                    true
            );
        }

        cir.setReturnValue(firstResult);
    }

    @Unique
    private void mercantile$refillPaymentSlots(MerchantOffer offer) {
        mercantile$refillSlot(0, offer.getItemCostA());
        offer.getItemCostB().ifPresent(cost -> mercantile$refillSlot(1, cost));
    }

    @Unique
    private void mercantile$refillSlot(int paymentSlotIndex, ItemCost cost) {
        ItemStack current = this.tradeContainer.getItem(paymentSlotIndex);
        int maxStackSize = cost.itemStack().getMaxStackSize();
        boolean changed = false;

        for (int i = 3; i < 39; i++) {
            if (current.getCount() >= maxStackSize) break;

            ItemStack invItem = this.slots.get(i).getItem();
            if (invItem.isEmpty() || !cost.test(invItem)) continue;

            if (current.isEmpty()) {
                int toMove = Math.min(invItem.getCount(), maxStackSize);
                current = invItem.copyWithCount(toMove);
                invItem.shrink(toMove);
                changed = true;
            } else if (ItemStack.isSameItemSameComponents(current, invItem)) {
                int toMove = Math.min(invItem.getCount(), maxStackSize - current.getCount());
                current.grow(toMove);
                invItem.shrink(toMove);
                changed = true;
            }

            if (invItem.isEmpty()) {
                this.slots.get(i).setByPlayer(ItemStack.EMPTY);
            }
        }

        if (changed) {
            this.tradeContainer.setItem(paymentSlotIndex, current);
        }
    }
}