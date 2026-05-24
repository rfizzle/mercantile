package com.rfizzle.mercantile.reputation;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.PlayerData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-tests the pure cap-decision helpers in {@link ReputationManager}. Avoids any Fabric/Minecraft
 * runtime — fabric-api is not on the JUnit classpath, so this stays at Tier 1 (no Bootstrap, no server).
 */
class ReputationCapTest {

    private MercantileConfig config;

    @BeforeEach
    void setUp() {
        // Use a fresh config with spec defaults so test order can't leak state.
        config = new MercantileConfig();
        config.enableReputation = true;
        config.reputationTradeGain = 1;
        config.reputationCycleGain = 1;
        config.reputationCureGain = 5;
        config.reputationDailyCap = 5;
        config.reputationTradesPerGain = 5;
        config.reputationDailyMaxTradeRep = 2;
        config.reputationDailyMaxCycleRep = 1;
    }

    @Test
    void tradeRepAwardedOnlyOnEveryNthTrade() {
        PlayerData data = new PlayerData();
        long day = 10L;

        for (int i = 1; i < 5; i++) {
            assertEquals(ReputationManager.CapDecision.BELOW_TRADE_THRESHOLD,
                    ReputationManager.evaluateTradeGain(data, config, day),
                    "trade #" + i + " must be below threshold");
            assertEquals(0, data.getDailyReputationEarned());
        }

        assertEquals(ReputationManager.CapDecision.AWARDED,
                ReputationManager.evaluateTradeGain(data, config, day));
        assertEquals(1, data.getDailyReputationEarned());
        assertEquals(1, data.getDailyTradeRep());
        assertEquals(0, data.getTradesSinceLastRepGain(),
                "pulse counter must reset after a successful award");
    }

    @Test
    void tradeSubCapBlocksThirdAward() {
        PlayerData data = new PlayerData();
        long day = 1L;

        // Earn the first two daily trade rep (10 trades).
        for (int i = 0; i < 10; i++) {
            ReputationManager.evaluateTradeGain(data, config, day);
        }
        assertEquals(2, data.getDailyTradeRep());
        assertEquals(2, data.getDailyReputationEarned());

        // 11th..15th trades should accumulate, then the 5th hits the trade sub-cap.
        for (int i = 0; i < 4; i++) {
            assertEquals(ReputationManager.CapDecision.BELOW_TRADE_THRESHOLD,
                    ReputationManager.evaluateTradeGain(data, config, day));
        }
        assertEquals(ReputationManager.CapDecision.SUBCAP_HIT,
                ReputationManager.evaluateTradeGain(data, config, day));
        assertEquals(2, data.getDailyTradeRep(), "trade sub-cap must hold at max");
        assertEquals(2, data.getDailyReputationEarned());
    }

    @Test
    void cycleSubCapBlocksSecondAward() {
        PlayerData data = new PlayerData();
        long day = 1L;

        assertEquals(ReputationManager.CapDecision.AWARDED,
                ReputationManager.evaluateCycleGain(data, config, day));
        assertEquals(1, data.getDailyCycleRep());
        assertEquals(1, data.getDailyReputationEarned());

        assertEquals(ReputationManager.CapDecision.SUBCAP_HIT,
                ReputationManager.evaluateCycleGain(data, config, day));
        assertEquals(1, data.getDailyCycleRep(), "cycle sub-cap must hold at max");
    }

    @Test
    void totalCapBlocksOnceReachedEvenIfSubCapNotHit() {
        // Lower total cap to 1 while leaving the cycle sub-cap higher so the total wins.
        config.reputationDailyCap = 1;
        config.reputationDailyMaxCycleRep = 5;
        PlayerData data = new PlayerData();
        long day = 1L;

        assertEquals(ReputationManager.CapDecision.AWARDED,
                ReputationManager.evaluateCycleGain(data, config, day));
        assertEquals(1, data.getDailyReputationEarned());

        assertEquals(ReputationManager.CapDecision.TOTAL_CAP_HIT,
                ReputationManager.evaluateCycleGain(data, config, day),
                "total cap must block when reached, even with sub-cap budget remaining");
    }

    @Test
    void cureAndNegativeBypassesDoNotConsumeCap() {
        // We don't call modifyScore here (that needs a ServerPlayer), but we can
        // assert that the helper paths don't touch dailyReputationEarned.
        PlayerData data = new PlayerData();
        long day = 1L;
        ReputationManager.rolloverIfNewDay(data, day);

        // Simulate other systems mutating score directly — daily counter must remain 0.
        data.addScore(-15); // attack penalty
        data.addScore(5);   // cure gain (bypass)

        assertEquals(0, data.getDailyReputationEarned(),
                "bypassed gains/losses must not consume daily cap budget");
    }

    @Test
    void dayRolloverZeroesCountersWhenCurrentDayIsNewer() {
        PlayerData data = new PlayerData();
        ReputationManager.rolloverIfNewDay(data, 5L);

        // Saturate the daily totals on day 5.
        for (int i = 0; i < 25; i++) {
            ReputationManager.evaluateTradeGain(data, config, 5L);
        }
        for (int i = 0; i < 3; i++) {
            ReputationManager.evaluateCycleGain(data, config, 5L);
        }
        int earnedBeforeRollover = data.getDailyReputationEarned();
        assertTrue(earnedBeforeRollover > 0);

        // New day — first call rolls over and re-evaluates against fresh counters.
        ReputationManager.CapDecision firstOfNewDay = ReputationManager.evaluateCycleGain(data, config, 6L);
        assertEquals(ReputationManager.CapDecision.AWARDED, firstOfNewDay);
        assertEquals(1, data.getDailyReputationEarned(),
                "counters must reset across a day boundary");
        assertEquals(6L, data.getLastCapResetDay());
    }

    @Test
    void sameDayRepeatedCallDoesNotZeroCounters() {
        PlayerData data = new PlayerData();
        long day = 3L;
        ReputationManager.rolloverIfNewDay(data, day);
        ReputationManager.evaluateCycleGain(data, config, day);
        assertEquals(1, data.getDailyReputationEarned());

        ReputationManager.rolloverIfNewDay(data, day);
        assertEquals(1, data.getDailyReputationEarned(),
                "rollover on the same day must not zero counters");
    }

    @Test
    void firstEverCallInitializesResetDayWithoutLosingCounters() {
        PlayerData data = new PlayerData();
        assertEquals(-1L, data.getLastCapResetDay());
        // First-ever interaction on day 0 (game just started).
        ReputationManager.rolloverIfNewDay(data, 0L);
        assertEquals(0L, data.getLastCapResetDay());
        assertEquals(0, data.getDailyReputationEarned());
    }

    @Test
    void belowTradeThresholdAdvancesPulseCounter() {
        PlayerData data = new PlayerData();
        long day = 1L;

        assertEquals(ReputationManager.CapDecision.BELOW_TRADE_THRESHOLD,
                ReputationManager.evaluateTradeGain(data, config, day));
        assertEquals(1, data.getTradesSinceLastRepGain());

        assertEquals(ReputationManager.CapDecision.BELOW_TRADE_THRESHOLD,
                ReputationManager.evaluateTradeGain(data, config, day));
        assertEquals(2, data.getTradesSinceLastRepGain());
    }

    @Test
    void pulseCounterResetsWhenCapBlocksAward() {
        // Once trade sub-cap is full, every 5th trade should hit SUBCAP_HIT and reset the
        // pulse counter so the next 5-trade window starts cleanly (still capped, but no carryover).
        PlayerData data = new PlayerData();
        long day = 1L;

        // Saturate trade sub-cap (2 successful awards = 10 trades).
        for (int i = 0; i < 10; i++) {
            ReputationManager.evaluateTradeGain(data, config, day);
        }
        assertEquals(2, data.getDailyTradeRep());

        // Trades 11–14 advance pulse, trade 15 hits sub-cap and resets pulse.
        for (int i = 0; i < 4; i++) {
            ReputationManager.evaluateTradeGain(data, config, day);
        }
        assertEquals(4, data.getTradesSinceLastRepGain());
        assertEquals(ReputationManager.CapDecision.SUBCAP_HIT,
                ReputationManager.evaluateTradeGain(data, config, day));
        assertEquals(0, data.getTradesSinceLastRepGain(),
                "pulse counter must reset on SUBCAP_HIT so the next window starts fresh");
    }

    @Test
    void negativePathDoesNotConsumeCap() {
        // Symmetry test: the daily cap budgets POSITIVE gain only. Negative score deltas
        // (attack/kill penalties) must still apply even after the cap is saturated, and
        // must NOT touch dailyReputationEarned.
        PlayerData data = new PlayerData();
        long day = 7L;
        data.setScore(100); // mid-range so the -15 penalty has room to register

        // Saturate the cap via cycles (1 cycle * cap=5 awards over multiple days normally,
        // but evaluateCycleGain saturates at the sub-cap=1 in 1 call → exercise both paths).
        config.reputationDailyMaxCycleRep = 5;
        for (int i = 0; i < 5; i++) {
            ReputationManager.evaluateCycleGain(data, config, day);
        }
        assertEquals(5, data.getDailyReputationEarned(), "cap should be saturated");
        int scoreBeforePenalty = data.getScore();
        int earnedBeforePenalty = data.getDailyReputationEarned();

        // Apply an attack-equivalent penalty directly (production flow: modifyScore → addScore).
        data.addScore(-config.reputationAttackLoss);

        assertEquals(scoreBeforePenalty - config.reputationAttackLoss, data.getScore(),
                "negative delta must reduce score even when daily cap is saturated");
        assertEquals(earnedBeforePenalty, data.getDailyReputationEarned(),
                "negative delta must NOT consume daily cap budget");
    }

    @Test
    void cureGainPathDoesNotConsumeCap() {
        // The cure flow (ReputationManager.gainCureRep) skips evaluate*Gain entirely and calls
        // addScore directly. After cap saturation, a cure must still increase score AND must
        // not consume the cap.
        PlayerData data = new PlayerData();
        long day = 3L;
        data.setScore(50);
        config.reputationDailyMaxCycleRep = 5;
        for (int i = 0; i < 5; i++) {
            ReputationManager.evaluateCycleGain(data, config, day);
        }
        assertEquals(5, data.getDailyReputationEarned(), "cap should be saturated");
        int scoreBeforeCure = data.getScore();
        int earnedBeforeCure = data.getDailyReputationEarned();

        // Production cure path: rollover, then addScore(+reputationCureGain). No evaluate call.
        ReputationManager.rolloverIfNewDay(data, day);
        data.addScore(config.reputationCureGain);

        assertEquals(scoreBeforeCure + config.reputationCureGain, data.getScore(),
                "cure must increase score past the daily cap budget");
        assertEquals(earnedBeforeCure, data.getDailyReputationEarned(),
                "cure must NOT consume daily cap budget");
    }

    @Test
    void subCapPrecedenceWhenSubCapExceedsTotalCap() {
        // Operator misconfiguration: dailyMaxTradeRep > reputationDailyCap. The total cap
        // must still be the binding constraint — sub-cap budget remaining is irrelevant when
        // the total cap is already exhausted.
        config.reputationDailyCap = 2;
        config.reputationDailyMaxTradeRep = 10; // larger than total cap
        PlayerData data = new PlayerData();
        long day = 1L;

        // 2 successful trade awards = 10 trades. After this, total cap is saturated but
        // sub-cap is only at 2/10.
        for (int i = 0; i < 10; i++) {
            ReputationManager.evaluateTradeGain(data, config, day);
        }
        assertEquals(2, data.getDailyReputationEarned());
        assertEquals(2, data.getDailyTradeRep());

        // Next 5-trade window: 4 below-threshold then TOTAL_CAP_HIT, not SUBCAP_HIT.
        for (int i = 0; i < 4; i++) {
            assertEquals(ReputationManager.CapDecision.BELOW_TRADE_THRESHOLD,
                    ReputationManager.evaluateTradeGain(data, config, day));
        }
        assertEquals(ReputationManager.CapDecision.TOTAL_CAP_HIT,
                ReputationManager.evaluateTradeGain(data, config, day),
                "total cap must win even when sub-cap budget remains");
        assertEquals(2, data.getDailyTradeRep(), "sub-cap should not advance past total cap");
        assertEquals(2, data.getDailyReputationEarned());
    }

    @Test
    void configReloadLoweringCapImmediatelyBlocks() {
        PlayerData data = new PlayerData();
        long day = 1L;
        // Earn 1 cycle rep with cap=5.
        ReputationManager.evaluateCycleGain(data, config, day);
        assertEquals(1, data.getDailyReputationEarned());

        // Operator lowers daily cap mid-day.
        config.reputationDailyCap = 1;

        // Lift the sub-cap so it can't be the blocker — total cap must be the one to fire.
        config.reputationDailyMaxCycleRep = 5;

        assertEquals(ReputationManager.CapDecision.TOTAL_CAP_HIT,
                ReputationManager.evaluateCycleGain(data, config, day));
    }
}
