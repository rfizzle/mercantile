package com.rfizzle.mercantile.trade;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Counts and deducts emeralds across the player's main inventory and offhand. Callers are
 * responsible for the creative ({@code instabuild}) gate — creative players should skip both the
 * affordability check and the deduction.
 */
public final class EmeraldPayment {

    private EmeraldPayment() {
    }

    public static int count(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.EMERALD)) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(Items.EMERALD)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static void remove(ServerPlayer player, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) break;
            if (stack.is(Items.EMERALD)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (remaining <= 0) break;
            if (stack.is(Items.EMERALD)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }
}
