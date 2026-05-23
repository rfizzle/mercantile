package com.rfizzle.mercantile.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReputationHudOverlayTest {

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
}
