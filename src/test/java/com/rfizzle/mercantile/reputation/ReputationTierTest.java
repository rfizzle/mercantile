package com.rfizzle.mercantile.reputation;

import com.rfizzle.mercantile.data.PlayerData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ReputationTierTest {

    @ParameterizedTest
    @CsvSource({
            "-100, REVILED",
            "-75,  REVILED",
            "-50,  REVILED",
            "-49,  DISTRUSTED",
            "-25,  DISTRUSTED",
            "-1,   DISTRUSTED",
            "0,    NEUTRAL",
            "1,    LIKED",
            "25,   LIKED",
            "49,   LIKED",
            "50,   TRUSTED",
            "75,   TRUSTED",
            "99,   TRUSTED",
            "100,  HONORED",
            "150,  HONORED",
            "200,  HONORED"
    })
    void tierBoundaries(int score, String expectedTier) {
        assertEquals(ReputationTier.valueOf(expectedTier), ReputationTier.fromScore(score));
    }

    @Test
    void fromNameCaseInsensitive() {
        assertEquals(ReputationTier.HONORED, ReputationTier.fromName("honored"));
        assertEquals(ReputationTier.HONORED, ReputationTier.fromName("Honored"));
        assertEquals(ReputationTier.HONORED, ReputationTier.fromName("HONORED"));
        assertEquals(ReputationTier.TRUSTED, ReputationTier.fromName("trusted"));
        assertEquals(ReputationTier.LIKED, ReputationTier.fromName("liked"));
        assertEquals(ReputationTier.NEUTRAL, ReputationTier.fromName("neutral"));
        assertEquals(ReputationTier.DISTRUSTED, ReputationTier.fromName("distrusted"));
        assertEquals(ReputationTier.REVILED, ReputationTier.fromName("reviled"));
    }

    @Test
    void fromNameUnknownDefaultsToTrusted() {
        // Intentional: changed from NEUTRAL (0) to TRUSTED (50) — typos in datapacks now gate trades higher.
        assertEquals(ReputationTier.TRUSTED, ReputationTier.fromName("unknown"));
        assertEquals(ReputationTier.TRUSTED, ReputationTier.fromName("foobar"));
    }

    @Test
    void allTiersHaveNonBlankTranslationKey() {
        for (ReputationTier tier : ReputationTier.values()) {
            assertFalse(tier.translationKey().isBlank(),
                    tier.name() + " has blank translation key");
            assertTrue(tier.translationKey().startsWith("mercantile.tier."),
                    tier.name() + " translation key should start with 'mercantile.tier.'");
        }
    }

    @Test
    void addScoreClampsAtMax() {
        PlayerData data = new PlayerData();
        data.setScore(190);
        data.addScore(20);
        assertEquals(PlayerData.MAX_SCORE, data.getScore());
    }

    @Test
    void addScoreClampsAtMin() {
        PlayerData data = new PlayerData();
        data.setScore(-90);
        data.addScore(-20);
        assertEquals(PlayerData.MIN_SCORE, data.getScore());
    }

    @Test
    void setScoreClampsAboveMax() {
        PlayerData data = new PlayerData();
        data.setScore(999);
        assertEquals(PlayerData.MAX_SCORE, data.getScore());
    }

    @Test
    void setScoreClampsBelowMin() {
        PlayerData data = new PlayerData();
        data.setScore(-999);
        assertEquals(PlayerData.MIN_SCORE, data.getScore());
    }

    @Test
    void addScorePositive() {
        PlayerData data = new PlayerData();
        data.addScore(10);
        assertEquals(10, data.getScore());
    }

    @Test
    void addScoreNegative() {
        PlayerData data = new PlayerData();
        data.addScore(-10);
        assertEquals(-10, data.getScore());
    }

    @Test
    void addScoreAccumulates() {
        PlayerData data = new PlayerData();
        data.addScore(5);
        data.addScore(5);
        data.addScore(-3);
        assertEquals(7, data.getScore());
    }

    @Test
    void isReviledBoundary() {
        assertTrue(ReputationManager.isReviled(-50));
        assertTrue(ReputationManager.isReviled(-100));
        assertFalse(ReputationManager.isReviled(-49));
        assertFalse(ReputationManager.isReviled(0));
    }
}
