package com.rfizzle.mercantile.reputation;

import com.rfizzle.mercantile.command.MercantileCommands;
import com.rfizzle.mercantile.data.PlayerData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ReputationTierTest {

    @ParameterizedTest
    @CsvSource({
            "-100, Reviled",
            "-75,  Reviled",
            "-50,  Reviled",
            "-49,  Distrusted",
            "-25,  Distrusted",
            "-1,   Distrusted",
            "0,    Neutral",
            "1,    Liked",
            "25,   Liked",
            "49,   Liked",
            "50,   Trusted",
            "75,   Trusted",
            "99,   Trusted",
            "100,  Honored",
            "150,  Honored",
            "200,  Honored"
    })
    void tierBoundaries(int score, String expectedTier) {
        assertEquals(expectedTier, MercantileCommands.getTierName(score));
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
