package com.rfizzle.mercantile.client.gui.merchant;

/**
 * Pure geometry for the {@code MerchantScreen} trade-GUI additions — panel and
 * overlay bounds, icon slots, trade-row/pin columns, and the point-in-region
 * hit-tests they share.
 *
 * <p>Everything here is deterministic integer math with no Minecraft rendering
 * or state, so it can be reasoned about and unit-tested on its own. The mixin
 * supplies the screen-relative origins ({@code leftPos}/{@code topPos}) and the
 * measured title width; this class turns those into positions and answers
 * whether a point falls in a region.
 */
public final class MerchantScreenLayout {
    private MerchantScreenLayout() {
    }

    // Shared icon sizing (lock / info / close / pin glyphs are all 11px square).
    public static final int ICON_SIZE = 11;
    public static final int LOCK_ICON_Y = 4;
    public static final int INFO_ICON_SIZE = 11;
    public static final int CLOSE_BUTTON_SIZE = 11;

    public static final int CYCLE_BUTTON_HEIGHT = 20;

    // Inline info panel — hangs off the left edge of the merchant window.
    public static final int INFO_PANEL_WIDTH = 110;
    public static final int INFO_PANEL_HEIGHT = 160;
    public static final int INFO_PANEL_MARGIN = 4;
    public static final int INFO_PANEL_PAD = 6;

    // Centered modal overlay — the fallback when the inline panel won't fit.
    public static final int OVERLAY_WIDTH = 130;
    public static final int OVERLAY_HEIGHT = 180;
    public static final int OVERLAY_PAD = 8;

    // Trade rows sit at leftPos+5, topPos+18, 88x20 each, 7 visible (vanilla init()).
    // The pin column lives just right of the scrollbar track (leftPos+94..100), in the
    // blank band before the trade-slot area that starts at leftPos+136.
    public static final int PIN_ICON_X = 101;
    public static final int TRADE_ROW_X = 5;
    public static final int TRADE_ROW_Y = 18;
    public static final int TRADE_ROW_HEIGHT = 20;
    public static final int VISIBLE_TRADE_ROWS = 7;

    // ---- Inline info panel ----

    /**
     * The panel's left x in screen coords. Only meaningful when {@link
     * #panelFits(int)} is true; otherwise the panel would overlap the merchant
     * UI and must be hidden in favour of the overlay.
     */
    public static int panelX(int leftPos) {
        return leftPos - INFO_PANEL_WIDTH - INFO_PANEL_MARGIN;
    }

    public static boolean panelFits(int leftPos) {
        return panelX(leftPos) >= 2;
    }

    // ---- Title-row icons (GUI-local x, origin at leftPos) ----

    /**
     * GUI-local x of the lock icon. Single source of truth so the hover
     * hit-test stays aligned with the rendered icon. {@code titleWidth} is the
     * measured pixel width of the (possibly level-decorated) title component.
     */
    public static int lockIconX(int titleWidth, int imageWidth) {
        int titleX = 49 + imageWidth / 2 - titleWidth / 2;
        return titleX + titleWidth + 3;
    }

    /**
     * GUI-local x of the info icon. Sits to the right of the lock icon when the
     * lock icon is shown; otherwise occupies the same slot.
     */
    public static int infoIconX(int titleWidth, int imageWidth, boolean professionLockEnabled) {
        int base = lockIconX(titleWidth, imageWidth);
        return professionLockEnabled ? base + ICON_SIZE + 2 : base;
    }

    // ---- Centered overlay ----

    public static int overlayX(int screenWidth) {
        return (screenWidth - OVERLAY_WIDTH) / 2;
    }

    public static int overlayY(int screenHeight) {
        return (screenHeight - OVERLAY_HEIGHT) / 2;
    }

    public static int closeButtonX(int screenWidth) {
        return overlayX(screenWidth) + OVERLAY_WIDTH - OVERLAY_PAD / 2 - CLOSE_BUTTON_SIZE;
    }

    public static int closeButtonY(int screenHeight) {
        return overlayY(screenHeight) + OVERLAY_PAD / 2;
    }

    // ---- Hit-testing ----

    /** True when the point falls in the half-open rect [x, x+w) × [y, y+h). */
    public static boolean pointIn(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
