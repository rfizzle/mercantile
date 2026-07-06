package com.rfizzle.mercantile.client.gui.merchant;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.network.TradePinsS2CPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.List;

/**
 * Renders the per-trade pin column and answers which pin the cursor is over.
 *
 * <p>Deliberately independent of the info-panel payload: {@link
 * TradePinsS2CPayload} carries its own villager entity id, so pinning keeps
 * working when the info panel is disabled. The mixin decides <em>whether</em>
 * pins are active (config toggle, overlay not open, payload present) and hands
 * this class the resolved pins and offers; the drawing and hit-testing live
 * here.
 */
public final class TradePinRenderer {
    private TradePinRenderer() {
    }

    private static final ResourceLocation PIN_SPRITE = Mercantile.id("pin_button");
    private static final ResourceLocation PIN_OFF_SPRITE = Mercantile.id("pin_button_off");

    private static final int ICON_SIZE = MerchantScreenLayout.ICON_SIZE;

    // Alpha for the hollow pin on an un-hovered, unpinned row: present but understated,
    // so the whole column is discoverable without seven full-opacity pins competing.
    private static final float UNPINNED_DIM_ALPHA = 0.35f;

    public static void render(GuiGraphics guiGraphics, Font font, TradePinsS2CPayload pins,
                              List<MerchantOffer> offers, int leftPos, int topPos, int scrollOff,
                              int mouseX, int mouseY) {
        if (offers == null || offers.isEmpty()) return;

        int iconX = leftPos + MerchantScreenLayout.PIN_ICON_X;
        int rows = Math.min(MerchantScreenLayout.VISIBLE_TRADE_ROWS, offers.size() - scrollOff);
        for (int row = 0; row < rows; row++) {
            int offerIndex = row + scrollOff;
            if (offerIndex >= pins.pinnedByIndex().size()) break;
            boolean pinned = pins.pinnedByIndex().get(offerIndex);
            int rowY = topPos + MerchantScreenLayout.TRADE_ROW_Y + row * MerchantScreenLayout.TRADE_ROW_HEIGHT;
            int iconY = rowY + (MerchantScreenLayout.TRADE_ROW_HEIGHT - ICON_SIZE) / 2;

            // The pin column is always drawn so the affordance is discoverable. The hollow
            // unpinned sprite renders dimmed on an untouched row and at full opacity while the
            // pointer is on the row (or the pin column beside it), so it reads without cluttering.
            boolean rowHovered = mouseX >= leftPos + MerchantScreenLayout.TRADE_ROW_X
                    && mouseX < iconX + ICON_SIZE
                    && mouseY >= rowY && mouseY < rowY + MerchantScreenLayout.TRADE_ROW_HEIGHT;
            if (pinned) {
                guiGraphics.blitSprite(PIN_SPRITE, iconX, iconY, ICON_SIZE, ICON_SIZE);
            } else if (rowHovered) {
                guiGraphics.blitSprite(PIN_OFF_SPRITE, iconX, iconY, ICON_SIZE, ICON_SIZE);
            } else {
                guiGraphics.setColor(1.0f, 1.0f, 1.0f, UNPINNED_DIM_ALPHA);
                guiGraphics.blitSprite(PIN_OFF_SPRITE, iconX, iconY, ICON_SIZE, ICON_SIZE);
                guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            }

            if (mouseX >= iconX && mouseX < iconX + ICON_SIZE
                    && mouseY >= iconY && mouseY < iconY + ICON_SIZE) {
                String key = pinned
                        ? "gui.mercantile.pin.tooltip.pinned"
                        : "gui.mercantile.pin.tooltip.unpinned";
                guiGraphics.renderTooltip(font, Component.translatable(key), mouseX, mouseY);
            }
        }
    }

    /** Returns the absolute offer index of the pin icon under the cursor, or -1. */
    public static int indexAt(TradePinsS2CPayload pins, List<MerchantOffer> offers,
                              int leftPos, int topPos, int scrollOff, double mouseX, double mouseY) {
        if (offers == null || offers.isEmpty()) return -1;

        int iconX = leftPos + MerchantScreenLayout.PIN_ICON_X;
        if (mouseX < iconX || mouseX >= iconX + ICON_SIZE) return -1;
        int rows = Math.min(MerchantScreenLayout.VISIBLE_TRADE_ROWS, offers.size() - scrollOff);
        for (int row = 0; row < rows; row++) {
            int rowY = topPos + MerchantScreenLayout.TRADE_ROW_Y + row * MerchantScreenLayout.TRADE_ROW_HEIGHT;
            int iconY = rowY + (MerchantScreenLayout.TRADE_ROW_HEIGHT - ICON_SIZE) / 2;
            if (mouseY >= iconY && mouseY < iconY + ICON_SIZE) {
                int offerIndex = row + scrollOff;
                return offerIndex < pins.pinnedByIndex().size() ? offerIndex : -1;
            }
        }
        return -1;
    }
}
