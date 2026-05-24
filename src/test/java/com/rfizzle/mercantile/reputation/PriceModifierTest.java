package com.rfizzle.mercantile.reputation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class PriceModifierTest {

    @Test
    void neutralHasNoModifier() {
        assertEquals(0, ReputationManager.getPriceModifier(0, 10));
        assertEquals(0, ReputationManager.getPriceModifier(0, 64));
    }

    @ParameterizedTest
    @CsvSource({
            "75,  10, -1",
            "200, 10, -1",
            "299, 10, -1",
            "75,  20, -1",
            "299, 20, -1"
    })
    void likedGivesFivePercentDiscount(int score, int basePrice, int expected) {
        assertEquals(expected, ReputationManager.getPriceModifier(score, basePrice));
    }

    @ParameterizedTest
    @CsvSource({
            "300, 10, -1",
            "700, 10, -1",
            "999, 10, -1",
            "300, 20, -2",
            "999, 20, -2"
    })
    void trustedGivesTenPercentDiscount(int score, int basePrice, int expected) {
        assertEquals(expected, ReputationManager.getPriceModifier(score, basePrice));
    }

    @ParameterizedTest
    @CsvSource({
            "1000, 10, -2",
            "1250, 10, -2",
            "1500, 10, -2",
            "1000, 20, -3",
            "1500, 20, -3"
    })
    void honoredGivesFifteenPercentDiscount(int score, int basePrice, int expected) {
        assertEquals(expected, ReputationManager.getPriceModifier(score, basePrice));
    }

    @Test
    void distrustedMarkupScalesLinearly() {
        int base = 100;
        int atMinus1 = ReputationManager.getPriceModifier(-1, base);
        int atMinus149 = ReputationManager.getPriceModifier(-149, base);

        assertEquals(10, atMinus1);
        assertEquals(25, atMinus149);
    }

    @Test
    void distrustedMarkupMidpoint() {
        int base = 100;
        int atMinus75 = ReputationManager.getPriceModifier(-75, base);
        assertTrue(atMinus75 > 10 && atMinus75 < 25,
                "Midpoint markup should be between 10 and 25, got " + atMinus75);
    }

    @Test
    void reviledReturnsZero() {
        assertEquals(0, ReputationManager.getPriceModifier(-150, 10));
        assertEquals(0, ReputationManager.getPriceModifier(-200, 10));
    }

    @Test
    void smallBasePriceDiscountCanBeZero() {
        assertEquals(0, ReputationManager.getPriceModifier(75, 1));
    }

    @Test
    void discountNeverExceedsBasePrice() {
        for (int score = 1; score <= 1500; score++) {
            for (int base = 1; base <= 64; base++) {
                int mod = ReputationManager.getPriceModifier(score, base);
                assertTrue(mod >= -base,
                        "Discount " + mod + " exceeds base " + base + " at score " + score);
            }
        }
    }
}
