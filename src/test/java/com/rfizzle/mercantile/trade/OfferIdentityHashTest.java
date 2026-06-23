package com.rfizzle.mercantile.trade;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OfferIdentityHashTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void deterministic() {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        String hash1 = OfferIdentityHash.compute(offer);
        String hash2 = OfferIdentityHash.compute(offer);
        assertEquals(hash1, hash2);
    }

    @Test
    void differentInputCountProducesDifferentHash() {
        MerchantOffer one = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        MerchantOffer ten = new MerchantOffer(
                new ItemCost(Items.EMERALD, 10), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        assertNotEquals(OfferIdentityHash.compute(one), OfferIdentityHash.compute(ten));
    }

    @Test
    void differentOutputCountProducesDifferentHash() {
        MerchantOffer one = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        MerchantOffer stack = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 16), 16, 1, 0.0f);
        assertNotEquals(OfferIdentityHash.compute(one), OfferIdentityHash.compute(stack));
    }

    @Test
    void invariantToPriceMultiplier() {
        MerchantOffer low = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        MerchantOffer high = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.5f);
        assertEquals(OfferIdentityHash.compute(low), OfferIdentityHash.compute(high));
    }

    @Test
    void invariantToMaxUsesAndXp() {
        MerchantOffer a = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        MerchantOffer b = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 64, 10, 0.0f);
        assertEquals(OfferIdentityHash.compute(a), OfferIdentityHash.compute(b));
    }

    @Test
    void differentInputItemProducesDifferentHash() {
        MerchantOffer emerald = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        MerchantOffer diamond = new MerchantOffer(
                new ItemCost(Items.DIAMOND, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        assertNotEquals(OfferIdentityHash.compute(emerald), OfferIdentityHash.compute(diamond));
    }

    @Test
    void differentOutputItemProducesDifferentHash() {
        MerchantOffer apple = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        MerchantOffer bread = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD, 1), 16, 1, 0.0f);
        assertNotEquals(OfferIdentityHash.compute(apple), OfferIdentityHash.compute(bread));
    }

    @Test
    void withSecondInputDiffersFromWithout() {
        MerchantOffer single = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        MerchantOffer dual = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), Optional.of(new ItemCost(Items.BOOK, 1)),
                new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        assertNotEquals(OfferIdentityHash.compute(single), OfferIdentityHash.compute(dual));
    }

    @Test
    void differentSecondInputProducesDifferentHash() {
        MerchantOffer book = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), Optional.of(new ItemCost(Items.BOOK, 1)),
                new ItemStack(Items.ENCHANTED_BOOK, 1), 16, 1, 0.0f);
        MerchantOffer paper = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), Optional.of(new ItemCost(Items.PAPER, 1)),
                new ItemStack(Items.ENCHANTED_BOOK, 1), 16, 1, 0.0f);
        assertNotEquals(OfferIdentityHash.compute(book), OfferIdentityHash.compute(paper));
    }

    @Test
    void differentSecondInputCountProducesDifferentHash() {
        MerchantOffer one = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), Optional.of(new ItemCost(Items.BOOK, 1)),
                new ItemStack(Items.ENCHANTED_BOOK, 1), 16, 1, 0.0f);
        MerchantOffer three = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), Optional.of(new ItemCost(Items.BOOK, 3)),
                new ItemStack(Items.ENCHANTED_BOOK, 1), 16, 1, 0.0f);
        assertNotEquals(OfferIdentityHash.compute(one), OfferIdentityHash.compute(three));
    }

    @Test
    void hashAgreesAcrossCallsites() {
        MerchantOffer offer1 = new MerchantOffer(
                new ItemCost(Items.EMERALD, 5), new ItemStack(Items.BREAD, 6), 16, 1, 0.05f);
        MerchantOffer offer2 = new MerchantOffer(
                new ItemCost(Items.EMERALD, 5), new ItemStack(Items.BREAD, 6), 16, 1, 0.05f);
        assertEquals(OfferIdentityHash.compute(offer1), OfferIdentityHash.compute(offer2));
    }

    @Test
    void farmerLevel1BreadVsLevel2BreadAreDistinct() {
        MerchantOffer level1 = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD, 6), 16, 1, 0.05f);
        MerchantOffer level2 = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD, 12), 16, 1, 0.05f);
        assertNotEquals(OfferIdentityHash.compute(level1), OfferIdentityHash.compute(level2));
    }

    @Test
    void costACountChangeProducesDifferentHash() {
        MerchantOffer cheap = new MerchantOffer(
                new ItemCost(Items.WHEAT, 10), new ItemStack(Items.EMERALD, 1), 16, 1, 0.05f);
        MerchantOffer expensive = new MerchantOffer(
                new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD, 1), 16, 1, 0.05f);
        assertNotEquals(OfferIdentityHash.compute(cheap), OfferIdentityHash.compute(expensive));
    }

    @Test
    void costBCountChangeProducesDifferentHash() {
        MerchantOffer one = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), Optional.of(new ItemCost(Items.PAPER, 1)),
                new ItemStack(Items.ENCHANTED_BOOK, 1), 16, 1, 0.0f);
        MerchantOffer five = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), Optional.of(new ItemCost(Items.PAPER, 5)),
                new ItemStack(Items.ENCHANTED_BOOK, 1), 16, 1, 0.0f);
        assertNotEquals(OfferIdentityHash.compute(one), OfferIdentityHash.compute(five));
    }

    @Test
    void emptyCostBHashStable() {
        MerchantOffer noCostB1 = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 3), 16, 1, 0.0f);
        MerchantOffer noCostB2 = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 3), 16, 1, 0.0f);
        MerchantOffer noCostBDifferentResult = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 6), 16, 1, 0.0f);
        // empty costB segment is stable: same offer hashes identically
        assertEquals(OfferIdentityHash.compute(noCostB1), OfferIdentityHash.compute(noCostB2));
        // and different result counts still diverge even with no costB
        assertNotEquals(OfferIdentityHash.compute(noCostB1), OfferIdentityHash.compute(noCostBDifferentResult));
        // format sanity: no-costB hash contains || (empty middle segment)
        String hash = OfferIdentityHash.compute(noCostB1);
        assertTrue(hash.contains("||"), "Empty costB segment should produce || delimiter pair, got: " + hash);
    }

    @Test
    void componentFreeHashFormatUnchanged() {
        // Regression guard: an unenchanted offer must hash byte-identically to the pre-enchantment
        // format (item x count | costB | item x count) with NO trailing enchantment segment, so
        // stored lock-hash data stays valid with no migration.
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 5), Optional.of(new ItemCost(Items.DIAMOND, 4)),
                new ItemStack(Items.DIAMOND_SWORD, 1), 1, 1, 0.05f);
        assertEquals("minecraft:emeraldx5|minecraft:diamondx4|minecraft:diamond_swordx1",
                OfferIdentityHash.compute(offer));
    }
}
