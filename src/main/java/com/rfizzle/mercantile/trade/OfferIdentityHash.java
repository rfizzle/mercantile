package com.rfizzle.mercantile.trade;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.List;

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
        appendEnchantmentFingerprint(sb, offer.getResult());
        return sb.toString();
    }

    /**
     * Appends a sorted enchantment fingerprint, but only when the result stack actually carries
     * enchantment components. Component-free offers therefore omit the enchantment segment
     * entirely, so an unenchanted offer's hash never depends on this method.
     */
    private static void appendEnchantmentFingerprint(StringBuilder sb, ItemStack result) {
        String enchanted = fingerprint(result.get(DataComponents.ENCHANTMENTS));
        String stored = fingerprint(result.get(DataComponents.STORED_ENCHANTMENTS));
        if (enchanted.isEmpty() && stored.isEmpty()) return;
        sb.append('|').append(enchanted).append('|').append(stored);
    }

    private static String fingerprint(ItemEnchantments enchantments) {
        if (enchantments == null || enchantments.isEmpty()) return "";
        List<String> parts = new ArrayList<>(enchantments.size());
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            String id = holder.unwrapKey().map(ResourceKey::location).map(Object::toString)
                    .orElseGet(holder::toString);
            parts.add(id + '@' + entry.getIntValue());
        }
        parts.sort(null);
        return String.join(",", parts);
    }
}
