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
    void invariantToInputCount() {
        MerchantOffer one = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        MerchantOffer ten = new MerchantOffer(
                new ItemCost(Items.EMERALD, 10), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        assertEquals(OfferIdentityHash.compute(one), OfferIdentityHash.compute(ten));
    }

    @Test
    void invariantToOutputCount() {
        MerchantOffer one = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
        MerchantOffer stack = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 16), 16, 1, 0.0f);
        assertEquals(OfferIdentityHash.compute(one), OfferIdentityHash.compute(stack));
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
    void secondInputCountInvariant() {
        MerchantOffer one = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), Optional.of(new ItemCost(Items.BOOK, 1)),
                new ItemStack(Items.ENCHANTED_BOOK, 1), 16, 1, 0.0f);
        MerchantOffer three = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), Optional.of(new ItemCost(Items.BOOK, 3)),
                new ItemStack(Items.ENCHANTED_BOOK, 1), 16, 1, 0.0f);
        assertEquals(OfferIdentityHash.compute(one), OfferIdentityHash.compute(three));
    }
}
