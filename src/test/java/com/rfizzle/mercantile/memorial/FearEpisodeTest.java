package com.rfizzle.mercantile.memorial;

import com.rfizzle.mercantile.data.FearEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The one-time "villagers fear you" notice arms once per fear episode. These exercise
 * {@link FearManager#applyKillToEntry} — the per-village update that decides when a kill
 * (re)opens an episode — without needing a live world.
 */
class FearEpisodeTest {

    private static final int THRESHOLD = 3;
    private static final long DURATION = 72_000L; // 3 in-game days

    /** A kill list at or past the threshold, all inside the window. */
    private static List<Long> spree(long now) {
        return List.of(now - 2, now - 1, now);
    }

    @Test
    void freshSpreeStartsMarkupButDoesNotForceNotified() {
        FearEntry entry = new FearEntry();
        FearManager.applyKillToEntry(entry, spree(1_000L), 1_000L, THRESHOLD, DURATION);

        assertEquals(1_000L, entry.getFearStartGameTime(), "threshold spree starts the decay clock");
        assertFalse(entry.isNotified(), "a brand-new entry has not been notified yet");
    }

    @Test
    void refreshingActiveFearKeepsNotifiedFlag() {
        FearEntry entry = new FearEntry(spree(1_000L), 1_000L, true);

        // Another kill 100 ticks later while the markup is still active.
        FearManager.applyKillToEntry(entry, spree(1_100L), 1_100L, THRESHOLD, DURATION);

        assertEquals(1_100L, entry.getFearStartGameTime(), "the clock is refreshed");
        assertTrue(entry.isNotified(), "refreshing an active episode must not re-arm the notice");
    }

    @Test
    void reactivatingAfterDecayReArmsNotice() {
        // Episode ran and was notified; the markup has since fully decayed (start is far in the past).
        long start = 1_000L;
        FearEntry entry = new FearEntry(spree(start), start, true);
        long now = start + DURATION + 5_000L; // well past the duration -> fraction 0

        FearManager.applyKillToEntry(entry, spree(now), now, THRESHOLD, DURATION);

        assertEquals(now, entry.getFearStartGameTime(), "a fresh spree restarts the clock");
        assertFalse(entry.isNotified(), "reactivating from a decayed state re-arms the notice");
    }

    @Test
    void belowThresholdDoesNotOpenEpisodeOrTouchNotified() {
        FearEntry entry = new FearEntry();
        entry.setNotified(true); // ensure it is left untouched

        FearManager.applyKillToEntry(entry, List.of(500L), 500L, THRESHOLD, DURATION);

        assertEquals(-1L, entry.getFearStartGameTime(), "sub-threshold kills do not start markup");
        assertTrue(entry.isNotified(), "sub-threshold kills leave the notified flag as-is");
        assertEquals(List.of(500L), entry.getRecentKillTimes(), "the kill is still recorded");
    }
}
