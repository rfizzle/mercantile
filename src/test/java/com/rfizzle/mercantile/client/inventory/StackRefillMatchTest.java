package com.rfizzle.mercantile.client.inventory;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackRefillMatchTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void apple_matches_apple() {
        assertTrue(StackRefillHandler.matchesForRefill(
                new ItemStack(Items.APPLE),
                new ItemStack(Items.APPLE)));
    }

    @Test
    void apple_does_not_match_bread() {
        assertFalse(StackRefillHandler.matchesForRefill(
                new ItemStack(Items.BREAD),
                new ItemStack(Items.APPLE)));
    }

    @Test
    void damaged_pickaxe_matches_fresh_pickaxe() {
        ItemStack template = new ItemStack(Items.IRON_PICKAXE);
        template.setDamageValue(template.getMaxDamage() - 1);
        ItemStack candidate = new ItemStack(Items.IRON_PICKAXE);

        assertTrue(StackRefillHandler.matchesForRefill(candidate, template),
                "Damageable items match by type — durability must be ignored");
    }

    @Test
    void renamed_pickaxe_matches_unrenamed_pickaxe() {
        ItemStack template = new ItemStack(Items.IRON_PICKAXE);
        template.set(DataComponents.CUSTOM_NAME, Component.literal("Excalibur"));
        ItemStack candidate = new ItemStack(Items.IRON_PICKAXE);

        // Damageable items ignore ALL components — covers enchants, names, custom modifiers.
        assertTrue(StackRefillHandler.matchesForRefill(candidate, template),
                "Damageable items match by type — components (incl. enchants, names) ignored");
    }

    @Test
    void iron_pickaxe_does_not_match_diamond_pickaxe() {
        assertFalse(StackRefillHandler.matchesForRefill(
                new ItemStack(Items.DIAMOND_PICKAXE),
                new ItemStack(Items.IRON_PICKAXE)),
                "Different tool tiers are different items — must not match");
    }

    @Test
    void potion_healing_does_not_match_potion_swiftness() {
        ItemStack healing = new ItemStack(Items.POTION);
        healing.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));
        ItemStack swiftness = new ItemStack(Items.POTION);
        swiftness.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.SWIFTNESS));

        assertFalse(StackRefillHandler.matchesForRefill(swiftness, healing),
                "Potions with different POTION_CONTENTS must not cross-refill");
    }

    @Test
    void splash_potion_does_not_match_regular_potion() {
        ItemStack splash = new ItemStack(Items.SPLASH_POTION);
        splash.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));
        ItemStack regular = new ItemStack(Items.POTION);
        regular.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));

        assertFalse(StackRefillHandler.matchesForRefill(splash, regular),
                "Splash and regular potions are distinct Items — must not match");
    }

    @Test
    void named_paper_does_not_match_unnamed_paper() {
        ItemStack template = new ItemStack(Items.PAPER);
        template.set(DataComponents.CUSTOM_NAME, Component.literal("Important Note"));
        ItemStack candidate = new ItemStack(Items.PAPER);

        // Paper is non-damageable, so the strict component check applies.
        assertFalse(StackRefillHandler.matchesForRefill(candidate, template),
                "Non-damageable items must match by full components (custom name differs)");
    }

    @Test
    void empty_template_returns_false() {
        assertFalse(StackRefillHandler.matchesForRefill(
                new ItemStack(Items.APPLE), ItemStack.EMPTY));
    }

    @Test
    void empty_candidate_returns_false() {
        assertFalse(StackRefillHandler.matchesForRefill(
                ItemStack.EMPTY, new ItemStack(Items.APPLE)));
    }

    // Non-damageable items with a non-CUSTOM_NAME component must still diff via the
    // strict isSameItemSameComponents path. Uses REPAIR_COST (a simple Integer component)
    // to lock in that the strict check is not name-only.
    @Test
    void enchanted_book_with_different_repair_cost_does_not_match() {
        ItemStack template = new ItemStack(Items.ENCHANTED_BOOK);
        template.set(DataComponents.REPAIR_COST, 3);
        ItemStack candidate = new ItemStack(Items.ENCHANTED_BOOK);
        candidate.set(DataComponents.REPAIR_COST, 7);

        assertFalse(StackRefillHandler.matchesForRefill(candidate, template),
                "Non-damageable items must match by full components beyond CUSTOM_NAME");
    }

    @Test
    void stack_count_is_ignored_for_matching() {
        ItemStack template = new ItemStack(Items.APPLE, 1);
        ItemStack candidate = new ItemStack(Items.APPLE, 64);

        assertTrue(StackRefillHandler.matchesForRefill(candidate, template),
                "Match must ignore count — isSameItemSameComponents does not compare count");
    }

    @Test
    void writable_book_does_not_match_written_book() {
        assertFalse(StackRefillHandler.matchesForRefill(
                new ItemStack(Items.WRITTEN_BOOK),
                new ItemStack(Items.WRITABLE_BOOK)),
                "Different Item types must never match even if visually related");
    }

    // findRefillSlot — scans main inventory slots 9..35 only, skipping hotbar (0..8)
    // and offhand (40). Uses a real Inventory; constructor accepts a null Player since
    // get/setItem never dereference the player field (verified against vanilla source).

    @Test
    void findRefillSlot_returnsFirstMatch_inMainInventory() {
        Inventory inv = new Inventory(null);
        inv.setItem(14, new ItemStack(Items.APPLE));

        assertEquals(14, StackRefillHandler.findRefillSlot(inv, new ItemStack(Items.APPLE)));
    }

    @Test
    void findRefillSlot_skipsHotbar() {
        Inventory inv = new Inventory(null);
        inv.setItem(3, new ItemStack(Items.APPLE));

        // A match in hotbar slot 3 must not be returned — that would self-swap.
        assertEquals(-1, StackRefillHandler.findRefillSlot(inv, new ItemStack(Items.APPLE)));
    }

    @Test
    void findRefillSlot_skipsOffhand() {
        Inventory inv = new Inventory(null);
        inv.offhand.set(0, new ItemStack(Items.APPLE));

        // Offhand source is intentionally unsupported — scan is 9..35 only.
        assertEquals(-1, StackRefillHandler.findRefillSlot(inv, new ItemStack(Items.APPLE)));
    }

    @Test
    void findRefillSlot_returnsEarliestMatch_whenMultiple() {
        Inventory inv = new Inventory(null);
        inv.setItem(12, new ItemStack(Items.APPLE));
        inv.setItem(28, new ItemStack(Items.APPLE));

        assertEquals(12, StackRefillHandler.findRefillSlot(inv, new ItemStack(Items.APPLE)),
                "Lowest-index match wins — left-to-right priority");
    }

    @Test
    void findRefillSlot_emptyTemplate_returnsMinusOne() {
        Inventory inv = new Inventory(null);
        inv.setItem(14, new ItemStack(Items.APPLE));

        assertEquals(-1, StackRefillHandler.findRefillSlot(inv, ItemStack.EMPTY));
    }

    @Test
    void findRefillSlot_noMatch_returnsMinusOne() {
        Inventory inv = new Inventory(null);
        inv.setItem(10, new ItemStack(Items.BREAD));
        inv.setItem(20, new ItemStack(Items.DIAMOND));

        assertEquals(-1, StackRefillHandler.findRefillSlot(inv, new ItemStack(Items.APPLE)));
    }

    @Test
    void findRefillSlot_respectsDamageableMatching() {
        Inventory inv = new Inventory(null);
        ItemStack fresh = new ItemStack(Items.IRON_PICKAXE);
        inv.setItem(20, fresh);

        ItemStack damagedTemplate = new ItemStack(Items.IRON_PICKAXE);
        damagedTemplate.setDamageValue(damagedTemplate.getMaxDamage() - 1);

        assertEquals(20, StackRefillHandler.findRefillSlot(inv, damagedTemplate),
                "Damageable template must accept any same-item candidate regardless of damage");
    }
}
