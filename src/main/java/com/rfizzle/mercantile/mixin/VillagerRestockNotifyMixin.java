package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.trade.OfferIdentityHash;
import com.rfizzle.mercantile.trade.TradePinManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * Detects which offers a restock actually replenished: the set of out-of-stock offer hashes
 * is captured before {@code restock()} runs and handed to {@link TradePinManager} afterwards,
 * so pin notifications fire only for trades that were sold out and are buyable again — not on
 * every scheduled restock of a fully-stocked villager.
 */
@Mixin(Villager.class)
public abstract class VillagerRestockNotifyMixin {

    // Captured at HEAD, consumed at TAIL of the same server-thread call. Re-seeded on every
    // restock, so a value leaked by an exceptional exit cannot mislead the next capture.
    @Unique
    private Set<String> mercantile$outOfStockBeforeRestock;

    @Inject(method = "restock", at = @At("HEAD"))
    private void mercantile$captureOutOfStock(CallbackInfo ci) {
        mercantile$outOfStockBeforeRestock = null;
        if (!MercantileConfig.get().enableTradePinning) return;

        Villager self = (Villager) (Object) this;
        Set<String> outOfStock = new HashSet<>();
        for (MerchantOffer offer : self.getOffers()) {
            if (offer.isOutOfStock()) {
                outOfStock.add(OfferIdentityHash.compute(offer));
            }
        }
        mercantile$outOfStockBeforeRestock = outOfStock;
    }

    @Inject(method = "restock", at = @At("TAIL"))
    private void mercantile$notifyPinnedRestock(CallbackInfo ci) {
        Set<String> replenished = mercantile$outOfStockBeforeRestock;
        mercantile$outOfStockBeforeRestock = null;
        if (replenished == null || replenished.isEmpty()) return;
        TradePinManager.onVillagerRestocked((Villager) (Object) this, replenished);
    }
}
