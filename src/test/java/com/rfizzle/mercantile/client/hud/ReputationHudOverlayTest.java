package com.rfizzle.mercantile.client.hud;

import com.rfizzle.mercantile.config.MercantileConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReputationHudOverlayTest {

    private static final int SCAN_INTERVAL_TICKS = 20;

    @Test
    void boxWidthForIncludesPaddingIconGapAndText() {
        // 3 (left pad) + 12 (icon) + 2 (gap) + textWidth + 3 (right pad)
        assertEquals(20 + 42, ReputationHudOverlay.boxWidthFor(42));
        assertEquals(20, ReputationHudOverlay.boxWidthFor(0));
        assertEquals(20 + 100, ReputationHudOverlay.boxWidthFor(100));
    }

    @Test
    void boxWidthForGrowsMonotonicallyWithText() {
        int shorter = ReputationHudOverlay.boxWidthFor(20);
        int longer = ReputationHudOverlay.boxWidthFor(60);
        assertTrue(longer > shorter, "longer label must produce a wider box");
        assertEquals(40, longer - shorter, "width difference must equal textWidth difference");
    }

    @Test
    void stackOffsetWithoutSiblingIsZero() {
        assertEquals(0, ReputationHudOverlay.stackOffsetFor(MercantileConfig.Anchor.TOP_LEFT, 0));
    }

    @Test
    void stackOffsetPassesSiblingHeightThroughAtTopLeft() {
        int withTrib = ReputationHudOverlay.stackOffsetFor(MercantileConfig.Anchor.TOP_LEFT, 22);
        assertTrue(withTrib > 0, "a visible sibling must push our HUD down at the shared default anchor");
        assertEquals(22, withTrib);
    }

    @Test
    void stackOffsetOnlyAppliesAtTopLeftAnchor() {
        // Tribulation's slot-1 element canonically sits top-left; other anchors don't stack against it.
        assertEquals(0, ReputationHudOverlay.stackOffsetFor(MercantileConfig.Anchor.TOP_RIGHT, 22));
        assertEquals(0, ReputationHudOverlay.stackOffsetFor(MercantileConfig.Anchor.BOTTOM_LEFT, 22));
        assertEquals(0, ReputationHudOverlay.stackOffsetFor(MercantileConfig.Anchor.BOTTOM_RIGHT, 22));
    }

    @Test
    void computeOriginXAnchorsLeftAndRightEdges() {
        // screen 400 wide, offset 4, box 50 wide
        assertEquals(4, ReputationHudOverlay.computeOriginX(MercantileConfig.Anchor.TOP_LEFT, 400, 4, 50));
        assertEquals(4, ReputationHudOverlay.computeOriginX(MercantileConfig.Anchor.BOTTOM_LEFT, 400, 4, 50));
        assertEquals(400 - 4 - 50, ReputationHudOverlay.computeOriginX(MercantileConfig.Anchor.TOP_RIGHT, 400, 4, 50));
        assertEquals(400 - 4 - 50, ReputationHudOverlay.computeOriginX(MercantileConfig.Anchor.BOTTOM_RIGHT, 400, 4, 50));
    }

    @Test
    void computeOriginYAnchorsTopAndBottomEdgesAndStacksInward() {
        // screen 300 tall, offset 4, box 16 tall, stack 22
        assertEquals(4 + 22, ReputationHudOverlay.computeOriginY(MercantileConfig.Anchor.TOP_LEFT, 300, 4, 16, 22));
        assertEquals(4 + 22, ReputationHudOverlay.computeOriginY(MercantileConfig.Anchor.TOP_RIGHT, 300, 4, 16, 22));
        assertEquals(300 - 4 - 16 - 22, ReputationHudOverlay.computeOriginY(MercantileConfig.Anchor.BOTTOM_LEFT, 300, 4, 16, 22));
        assertEquals(300 - 4 - 16, ReputationHudOverlay.computeOriginY(MercantileConfig.Anchor.BOTTOM_RIGHT, 300, 4, 16, 0));
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
