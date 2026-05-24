package com.rfizzle.mercantile.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReputationHudOverlayTest {

    private static final int SCAN_INTERVAL_TICKS = 20;

    @Test
    void boxWidthForIncludesPaddingIconGapAndText() {
        // 4 (left pad) + 16 (icon) + 4 (gap) + textWidth + 4 (right pad)
        assertEquals(28 + 42, ReputationHudOverlay.boxWidthFor(42));
        assertEquals(28, ReputationHudOverlay.boxWidthFor(0));
        assertEquals(28 + 100, ReputationHudOverlay.boxWidthFor(100));
    }

    @Test
    void boxWidthForGrowsMonotonicallyWithText() {
        int shorter = ReputationHudOverlay.boxWidthFor(20);
        int longer = ReputationHudOverlay.boxWidthFor(60);
        assertTrue(longer > shorter, "longer label must produce a wider box");
        assertEquals(40, longer - shorter, "width difference must equal textWidth difference");
    }

    @Test
    void yOffsetForWithoutTribulationUsesBaseY() {
        assertEquals(2, ReputationHudOverlay.yOffsetFor(false));
    }

    @Test
    void yOffsetForWithTribulationReservesSpaceAbove() {
        int withTrib = ReputationHudOverlay.yOffsetFor(true);
        int withoutTrib = ReputationHudOverlay.yOffsetFor(false);
        assertTrue(withTrib > withoutTrib, "Tribulation must push our HUD down");
        // base 2 + reserved 22
        assertEquals(24, withTrib);
    }

    @Test
    void shouldRescanFiresOnFirstFrameAfterInit() {
        // Regression for B-085: lastScanTick=Long.MIN_VALUE overflowed and the HUD never rendered.
        // With the fixed initializer (-SCAN_INTERVAL_TICKS), the first frame at now=0 must scan.
        assertTrue(ReputationHudOverlay.shouldRescan(0, -SCAN_INTERVAL_TICKS));
    }

    @Test
    void shouldRescanIsFalseWithinInterval() {
        assertFalse(ReputationHudOverlay.shouldRescan(10, 0));
    }

    @Test
    void shouldRescanIsTrueAtInterval() {
        assertTrue(ReputationHudOverlay.shouldRescan(20, 0));
    }

    @Test
    void shouldRescanIsTrueWhenGameTimeRunsBackward() {
        // World reload / fresh-join after a long session — static field persists; recover by rescanning.
        assertTrue(ReputationHudOverlay.shouldRescan(5, 1000));
    }
}
