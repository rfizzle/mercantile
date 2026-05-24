package com.rfizzle.mercantile.client.inventory;

import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.config.MercantileConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

public final class StackRefillHandler {

    private static final int OFFHAND_SWAP_BUTTON = 40;

    // Tick-thread only state — ClientTickEvents fires on the render thread.
    // Snapshots are full ItemStack copies so component-aware matching can fire on refill.
    private static ItemStack prevHeldSnapshot = ItemStack.EMPTY;
    private static ItemStack prevOffhandSnapshot = ItemStack.EMPTY;
    private static int prevSelectedSlot = -1;
    private static boolean prevScreenOpen = false;

    private StackRefillHandler() {
    }

    public static void tick(Minecraft mc) {
        if (mc == null) {
            resetState();
            return;
        }
        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || mc.level == null || gameMode == null) {
            resetState();
            return;
        }
        if (player.getAbilities().instabuild) {
            captureSnapshot(player.getInventory(), mc.screen != null);
            return;
        }
        if (!isEnabled()) {
            captureSnapshot(player.getInventory(), mc.screen != null);
            return;
        }

        boolean screenOpen = mc.screen != null;
        Inventory inv = player.getInventory();
        int selectedSlot = inv.selected;
        ItemStack heldStack = inv.getItem(selectedSlot);
        ItemStack offhandStack = inv.offhand.get(0);

        // Skip refill while a screen is open or just-closed/opened — avoids racing the inventory.
        boolean screenBlocked = screenOpen || prevScreenOpen;

        // Main-hand refill: previous tick held an item, current is empty, selected slot unchanged.
        // F-swap guard: if the prior held item now lives in the offhand, the player pressed F.
        // Q-drop is deliberately treated like "stack ran out" — dropping the last item triggers refill.
        if (!screenBlocked
                && !prevHeldSnapshot.isEmpty()
                && heldStack.isEmpty()
                && prevSelectedSlot == selectedSlot
                && !ItemStack.isSameItemSameComponents(offhandStack, prevHeldSnapshot)) {
            int sourceInventorySlot = findRefillSlot(inv, prevHeldSnapshot);
            if (sourceInventorySlot >= 0) {
                performSwap(mc, sourceInventorySlot, selectedSlot);
                heldStack = inv.getItem(selectedSlot);
            }
        }

        // Offhand refill: same detection. F-swap guard mirrors the main-hand branch.
        if (!screenBlocked
                && !prevOffhandSnapshot.isEmpty()
                && offhandStack.isEmpty()
                && !ItemStack.isSameItemSameComponents(heldStack, prevOffhandSnapshot)) {
            int sourceInventorySlot = findRefillSlot(inv, prevOffhandSnapshot);
            if (sourceInventorySlot >= 0) {
                performSwap(mc, sourceInventorySlot, OFFHAND_SWAP_BUTTON);
                offhandStack = inv.offhand.get(0);
            }
        }

        captureSnapshot(inv, screenOpen);
    }

    static int findRefillSlot(Inventory inv, ItemStack template) {
        if (template.isEmpty()) return -1;
        for (int i = 9; i <= 35; i++) {
            ItemStack candidate = inv.getItem(i);
            if (matchesForRefill(candidate, template)) return i;
        }
        return -1;
    }

    // Damageable items (tools, armor) match by item type alone so a damaged or enchanted
    // variant refills from any spare of the same type. Everything else requires identical
    // components so potions, named items, books, etc. don't substitute cross-variant.
    static boolean matchesForRefill(ItemStack candidate, ItemStack template) {
        if (candidate.isEmpty() || template.isEmpty()) return false;
        if (!candidate.is(template.getItem())) return false;
        if (template.isDamageableItem()) return true;
        return ItemStack.isSameItemSameComponents(candidate, template);
    }

    // SWAP click: slotId is the menu slot of the source; button is the hotbar index (0..8) or 40 for offhand.
    // Main inventory indices 9..35 map directly to InventoryMenu slots 9..35.
    private static void performSwap(Minecraft mc, int inventorySlot, int hotbarButton) {
        InventoryMenu menu = mc.player.inventoryMenu;
        mc.gameMode.handleInventoryMouseClick(
                menu.containerId,
                inventorySlot,
                hotbarButton,
                ClickType.SWAP,
                mc.player);
    }

    private static boolean isEnabled() {
        MercantileConfig synced = ClientMercantileData.getServerConfig();
        if (synced != null) return synced.enableStackRefill;
        return MercantileConfig.get().enableStackRefill;
    }

    private static void captureSnapshot(Inventory inv, boolean screenOpen) {
        ItemStack heldStack = inv.getItem(inv.selected);
        ItemStack offhandStack = inv.offhand.get(0);
        prevHeldSnapshot = heldStack.isEmpty() ? ItemStack.EMPTY : heldStack.copy();
        prevOffhandSnapshot = offhandStack.isEmpty() ? ItemStack.EMPTY : offhandStack.copy();
        prevSelectedSlot = inv.selected;
        prevScreenOpen = screenOpen;
    }

    private static void resetState() {
        prevHeldSnapshot = ItemStack.EMPTY;
        prevOffhandSnapshot = ItemStack.EMPTY;
        prevSelectedSlot = -1;
        prevScreenOpen = false;
    }
}
