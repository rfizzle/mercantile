package com.rfizzle.mercantile.client.gui.merchant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Font-independent geometry math extracted from {@code MerchantScreenMixin}.
 * The values here pin the exact positions the mixin used before the refactor so
 * the split can't silently shift any icon, panel, or hit-box.
 */
class MerchantScreenLayoutTest {

    // The vanilla merchant window is 276px wide; a common centered leftPos.
    private static final int LEFT_POS = 100;
    private static final int IMAGE_WIDTH = 276;

    @Test
    void panelXHangsOffTheLeftEdge() {
        // leftPos - width(110) - margin(4)
        assertEquals(LEFT_POS - 110 - 4, MerchantScreenLayout.panelX(LEFT_POS));
    }

    @Test
    void panelFitsOnlyWhenTwoPixelsClearOfTheScreenEdge() {
        // panelX == 2 is the boundary (inclusive).
        int boundaryLeftPos = 2 + 110 + 4;
        assertTrue(MerchantScreenLayout.panelFits(boundaryLeftPos));
        assertEquals(2, MerchantScreenLayout.panelX(boundaryLeftPos));
        assertFalse(MerchantScreenLayout.panelFits(boundaryLeftPos - 1));
    }

    @Test
    void lockIconSitsJustRightOfTheCenteredTitle() {
        int titleWidth = 40;
        int titleX = 49 + IMAGE_WIDTH / 2 - titleWidth / 2;
        assertEquals(titleX + titleWidth + 3, MerchantScreenLayout.lockIconX(titleWidth, IMAGE_WIDTH));
    }

    @Test
    void infoIconShiftsPastTheLockIconOnlyWhenProfessionLockShows() {
        int titleWidth = 40;
        int lockX = MerchantScreenLayout.lockIconX(titleWidth, IMAGE_WIDTH);
        // No lock icon → info icon occupies the same slot.
        assertEquals(lockX, MerchantScreenLayout.infoIconX(titleWidth, IMAGE_WIDTH, false));
        // Lock icon shown → info icon shifts by one icon width plus a 2px gap.
        assertEquals(lockX + MerchantScreenLayout.ICON_SIZE + 2,
                MerchantScreenLayout.infoIconX(titleWidth, IMAGE_WIDTH, true));
    }

    @Test
    void overlayIsCenteredOnScreen() {
        int screenW = 854;
        int screenH = 480;
        assertEquals((screenW - MerchantScreenLayout.OVERLAY_WIDTH) / 2, MerchantScreenLayout.overlayX(screenW));
        assertEquals((screenH - MerchantScreenLayout.OVERLAY_HEIGHT) / 2, MerchantScreenLayout.overlayY(screenH));
    }

    @Test
    void closeButtonSitsInsideTheOverlayTopRight() {
        int screenW = 854;
        int screenH = 480;
        int expectedX = MerchantScreenLayout.overlayX(screenW) + MerchantScreenLayout.OVERLAY_WIDTH
                - MerchantScreenLayout.OVERLAY_PAD / 2 - MerchantScreenLayout.CLOSE_BUTTON_SIZE;
        int expectedY = MerchantScreenLayout.overlayY(screenH) + MerchantScreenLayout.OVERLAY_PAD / 2;
        assertEquals(expectedX, MerchantScreenLayout.closeButtonX(screenW));
        assertEquals(expectedY, MerchantScreenLayout.closeButtonY(screenH));
    }

    @Test
    void pointInIsHalfOpenOnBothAxes() {
        int x = 10;
        int y = 20;
        int w = 11;
        int h = 11;
        // Top-left corner is inside; bottom-right corner is outside (half-open).
        assertTrue(MerchantScreenLayout.pointIn(x, y, x, y, w, h));
        assertTrue(MerchantScreenLayout.pointIn(x + w - 1, y + h - 1, x, y, w, h));
        assertFalse(MerchantScreenLayout.pointIn(x + w, y, x, y, w, h));
        assertFalse(MerchantScreenLayout.pointIn(x, y + h, x, y, w, h));
        assertFalse(MerchantScreenLayout.pointIn(x - 1, y, x, y, w, h));
    }
}
