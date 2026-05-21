package com.rfizzle.mercantile.trade;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.trading.MerchantOffer;

public final class OfferIdentityHash {

    private OfferIdentityHash() {
    }

    public static String compute(MerchantOffer offer) {
        StringBuilder sb = new StringBuilder();
        sb.append(BuiltInRegistries.ITEM.getKey(offer.getBaseCostA().getItem()));
        sb.append('x').append(offer.getBaseCostA().getCount());
        sb.append('|');
        offer.getItemCostB().ifPresent(cost -> {
            sb.append(BuiltInRegistries.ITEM.getKey(cost.item().value()));
            sb.append('x').append(cost.count());
        });
        sb.append('|');
        sb.append(BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()));
        sb.append('x').append(offer.getResult().getCount());
        return sb.toString();
    }

    /**
     * Legacy hash format (no item counts) used before BUG-003 fix.
     * Used only for migrating old world-save data — do not use for new hashes.
     */
    public static String computeLegacy(MerchantOffer offer) {
        StringBuilder sb = new StringBuilder();
        sb.append(BuiltInRegistries.ITEM.getKey(offer.getBaseCostA().getItem()));
        sb.append('|');
        offer.getItemCostB().ifPresent(cost ->
                sb.append(BuiltInRegistries.ITEM.getKey(cost.item().value())));
        sb.append('|');
        sb.append(BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()));
        return sb.toString();
    }
}
