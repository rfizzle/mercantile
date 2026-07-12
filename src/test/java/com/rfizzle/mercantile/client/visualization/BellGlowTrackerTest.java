package com.rfizzle.mercantile.client.visualization;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 unit coverage for {@link BellGlowTracker}'s hold-to-glow behavior (issue #161). The
 * tracker is a plain map of UUID -> expiry with no Minecraft types, so it tests without a client.
 */
class BellGlowTrackerTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    @AfterEach
    void reset() {
        BellGlowTracker.clear();
    }

    @Test
    void holdGlow_glowsWithinShortWindow_thenExpires() {
        long now = 100L;
        BellGlowTracker.markHoldGlowing(ID, now);

        assertTrue(BellGlowTracker.isGlowing(ID, now), "should glow the tick it is marked");
        assertTrue(BellGlowTracker.isGlowing(ID, now + BellGlowTracker.HOLD_GLOW_DURATION_TICKS - 1),
                "should still glow just before the hold window closes");
        assertFalse(BellGlowTracker.isGlowing(ID, now + BellGlowTracker.HOLD_GLOW_DURATION_TICKS),
                "should stop glowing once the brief hold window elapses");
    }

    @Test
    void stow_clearsWithinACoupleTicks() {
        // Last hold refresh at t=100; the bell is then stowed (no further marks).
        long lastRefresh = 100L;
        BellGlowTracker.markHoldGlowing(ID, lastRefresh);

        // A couple of ticks later the per-tick cleanup evicts the entry entirely.
        BellGlowTracker.tick(lastRefresh + BellGlowTracker.HOLD_GLOW_DURATION_TICKS);
        assertEquals(0, BellGlowTracker.size(), "hold glow must be gone within a couple of ticks of stowing");
        assertFalse(BellGlowTracker.isGlowing(ID, lastRefresh + BellGlowTracker.HOLD_GLOW_DURATION_TICKS),
                "villager should no longer glow after stowing");
    }

    @Test
    void holdRefresh_neverShortensRunningRingPulse() {
        // A rung bell starts a 3-second (60-tick) pulse...
        long now = 100L;
        BellGlowTracker.markGlowing(ID, now);
        // ...and the player is also holding a bell, so a hold refresh lands the same tick.
        BellGlowTracker.markHoldGlowing(ID, now);

        // The max-merge must keep the longer ring expiry, not clamp it down to the 3-tick hold window.
        assertTrue(BellGlowTracker.isGlowing(ID, now + 30),
                "hold refresh must not shorten an in-flight ring pulse");
        assertTrue(BellGlowTracker.isGlowing(ID, now + BellGlowTracker.GLOW_DURATION_TICKS - 1),
                "ring pulse should run its full duration despite the hold refresh");
        assertFalse(BellGlowTracker.isGlowing(ID, now + BellGlowTracker.GLOW_DURATION_TICKS),
                "ring pulse still expires at its own deadline");
    }

    @Test
    void ringPulse_afterHold_extendsToFullDuration() {
        long now = 100L;
        BellGlowTracker.markHoldGlowing(ID, now);
        BellGlowTracker.markGlowing(ID, now);

        assertTrue(BellGlowTracker.isGlowing(ID, now + 30),
                "a ring landing over an active hold should extend the glow to the full pulse");
    }

    @Test
    void tick_retainsEntryUntilItsDeadline() {
        long now = 100L;
        BellGlowTracker.markHoldGlowing(ID, now);

        BellGlowTracker.tick(now + BellGlowTracker.HOLD_GLOW_DURATION_TICKS - 1);
        assertEquals(1, BellGlowTracker.size(), "entry must survive cleanup runs before its deadline");
    }
}
