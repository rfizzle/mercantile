package com.rfizzle.mercantile.client.hud;

import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.config.MercantileConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReputationHudOverlayTest {

    private static final int SCAN_INTERVAL_TICKS = 20;

    @Test
    void nextTierAboveWalksUpTheLadderAndStopsAtHonored() {
        assertEquals(ReputationTier.DISTRUSTED, ReputationHudOverlay.nextTierAbove(ReputationTier.REVILED));
        assertEquals(ReputationTier.NEUTRAL, ReputationHudOverlay.nextTierAbove(ReputationTier.DISTRUSTED));
        assertEquals(ReputationTier.LIKED, ReputationHudOverlay.nextTierAbove(ReputationTier.NEUTRAL));
        assertEquals(ReputationTier.TRUSTED, ReputationHudOverlay.nextTierAbove(ReputationTier.LIKED));
        assertEquals(ReputationTier.HONORED, ReputationHudOverlay.nextTierAbove(ReputationTier.TRUSTED));
        assertNull(ReputationHudOverlay.nextTierAbove(ReputationTier.HONORED));
    }

    @Test
    void progressFractionIsZeroAtTierFloorAndApproachesOneBelowCeiling() {
        // NEUTRAL spans 0..74; LIKED starts at 75.
        assertEquals(0.0f, ReputationHudOverlay.progressFraction(0, ReputationTier.NEUTRAL));
        assertEquals(0.5f, ReputationHudOverlay.progressFraction(37, ReputationTier.NEUTRAL), 0.01f);
        assertTrue(ReputationHudOverlay.progressFraction(74, ReputationTier.NEUTRAL) < 1.0f);
    }

    @Test
    void progressFractionIsFullAtTopTier() {
        assertEquals(1.0f, ReputationHudOverlay.progressFraction(1000, ReputationTier.HONORED));
        assertEquals(1.0f, ReputationHudOverlay.progressFraction(1500, ReputationTier.HONORED));
    }

    @Test
    void progressFractionIsClampedAgainstMismatchedScores() {
        // Defensive: a score outside the tier's range must not under/overflow the bar.
        assertEquals(0.0f, ReputationHudOverlay.progressFraction(-500, ReputationTier.NEUTRAL));
        assertEquals(1.0f, ReputationHudOverlay.progressFraction(5000, ReputationTier.NEUTRAL));
    }

    @Test
    void tierColorIsDistinctPerTier() {
        // The bar tint is the badge's only state signal — every tier must read differently.
        long distinct = java.util.Arrays.stream(ReputationTier.values())
                .mapToInt(ReputationHudOverlay::tierColor)
                .distinct()
                .count();
        assertEquals(ReputationTier.values().length, distinct);
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
