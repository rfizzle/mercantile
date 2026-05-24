package com.rfizzle.mercantile.reputation;

import com.rfizzle.mercantile.data.PlayerData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tier 1: pure-JUnit migration tests. The S-040 score migration multiplies pre-S-040
 * scores by 10 exactly once, then sets {@link PlayerData#isReputationMigrated()} so
 * future calls are no-ops. No Fabric/Minecraft runtime touched.
 */
class ReputationMigrationTest {

    @Test
    void migrateScalesScoreByTen() {
        PlayerData data = new PlayerData();
        data.setScore(50);
        ReputationManager.migrateIfNeeded(data);
        assertEquals(500, data.getScore());
        assertTrue(data.isReputationMigrated());
    }

    @Test
    void migrateIsIdempotent() {
        PlayerData data = new PlayerData();
        data.setScore(50);
        ReputationManager.migrateIfNeeded(data);
        ReputationManager.migrateIfNeeded(data);
        assertEquals(500, data.getScore(), "second migrate must be a no-op");
        assertTrue(data.isReputationMigrated());
    }

    @Test
    void migrateClampsAtMax() {
        PlayerData data = new PlayerData();
        data.setScore(200);  // legacy max cap on the new MIN_SCORE..MAX_SCORE range
        ReputationManager.migrateIfNeeded(data);
        assertEquals(PlayerData.MAX_SCORE, data.getScore(),
                "200 * 10 = 2000 must clamp to MAX_SCORE (1500)");
    }

    @Test
    void migrateClampsAtMin() {
        // Negative legacy scores. -100 * 10 = -1000, but MIN_SCORE is -200, so it clamps.
        // The migration deliberately preserves the clamp rather than the relative tier — this
        // is the documented loss on the negative end.
        PlayerData data = new PlayerData();
        data.setScore(-100);
        ReputationManager.migrateIfNeeded(data);
        assertEquals(PlayerData.MIN_SCORE, data.getScore());
        assertTrue(data.isReputationMigrated());
    }

    @Test
    void migrateZeroScoreIsNoOp() {
        PlayerData data = new PlayerData();
        ReputationManager.migrateIfNeeded(data);
        assertEquals(0, data.getScore());
        assertTrue(data.isReputationMigrated(),
                "even zero-score data must flip the flag so we don't re-run forever");
    }

    @Test
    void migrateDoesNotTouchDailyCounters() {
        // Pre-populate S-040 fields and verify migration only touches score + flag.
        PlayerData data = PlayerData.builder()
                .score(30)
                .proximityTicks(5_000)
                .lastProximityDay(7L)
                .dailyReputationEarned(3)
                .lastCapResetDay(12L)
                .dailyTradeRep(2)
                .dailyCycleRep(1)
                .tradesSinceLastRepGain(4)
                .build();
        ReputationManager.migrateIfNeeded(data);
        assertEquals(300, data.getScore());
        assertEquals(5_000, data.getProximityTicks(), "proximityTicks must be untouched");
        assertEquals(7L, data.getLastProximityDay(), "lastProximityDay must be untouched");
        assertEquals(3, data.getDailyReputationEarned(), "dailyReputationEarned must be untouched");
        assertEquals(12L, data.getLastCapResetDay(), "lastCapResetDay must be untouched");
        assertEquals(2, data.getDailyTradeRep(), "dailyTradeRep must be untouched");
        assertEquals(1, data.getDailyCycleRep(), "dailyCycleRep must be untouched");
        assertEquals(4, data.getTradesSinceLastRepGain(), "tradesSinceLastRepGain must be untouched");
        assertTrue(data.isReputationMigrated());
    }

    @Test
    void alreadyMigratedDataIsLeftAlone() {
        PlayerData data = new PlayerData();
        data.setScore(305);
        data.setReputationMigrated(true);
        ReputationManager.migrateIfNeeded(data);
        assertEquals(305, data.getScore(),
                "migration must NOT scale a player already on the new scale");
    }
}
