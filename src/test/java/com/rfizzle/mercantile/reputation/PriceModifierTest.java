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
            "1,   10, -1",
            "25,  10, -1",
            "49,  10, -1",
            "1,   20, -1",
            "49,  20, -1"
    })
    void likedGivesFivePercentDiscount(int score, int basePrice, int expected) {
        assertEquals(expected, ReputationManager.getPriceModifier(score, basePrice));
    }

    @ParameterizedTest
    @CsvSource({
            "50,  10, -1",
            "75,  10, -1",
            "99,  10, -1",
            "50,  20, -2",
            "99,  20, -2"
    })
    void trustedGivesTenPercentDiscount(int score, int basePrice, int expected) {
        assertEquals(expected, ReputationManager.getPriceModifier(score, basePrice));
    }

    @ParameterizedTest
    @CsvSource({
            "100, 10, -2",
            "150, 10, -2",
            "200, 10, -2",
            "100, 20, -3",
            "200, 20, -3"
    })
    void honoredGivesFifteenPercentDiscount(int score, int basePrice, int expected) {
        assertEquals(expected, ReputationManager.getPriceModifier(score, basePrice));
    }

    @Test
    void distrustedMarkupScalesLinearly() {
        int base = 100;
        int atMinus1 = ReputationManager.getPriceModifier(-1, base);
        int atMinus49 = ReputationManager.getPriceModifier(-49, base);

        assertEquals(10, atMinus1);
        assertEquals(25, atMinus49);
    }

    @Test
    void distrustedMarkupMidpoint() {
        int base = 100;
        int atMinus25 = ReputationManager.getPriceModifier(-25, base);
        assertTrue(atMinus25 > 10 && atMinus25 < 25,
                "Midpoint markup should be between 10 and 25, got " + atMinus25);
    }

    @Test
    void reviledReturnsZero() {
        assertEquals(0, ReputationManager.getPriceModifier(-50, 10));
        assertEquals(0, ReputationManager.getPriceModifier(-100, 10));
    }

    @Test
    void smallBasePriceDiscountCanBeZero() {
        assertEquals(0, ReputationManager.getPriceModifier(1, 1));
    }

    @Test
    void discountNeverExceedsBasePrice() {
        for (int score = 1; score <= 200; score++) {
            for (int base = 1; base <= 64; base++) {
                int mod = ReputationManager.getPriceModifier(score, base);
                assertTrue(mod >= -base,
                        "Discount " + mod + " exceeds base " + base + " at score " + score);
            }
        }
    }
}
