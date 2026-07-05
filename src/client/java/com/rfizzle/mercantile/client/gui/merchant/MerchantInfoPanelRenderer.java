package com.rfizzle.mercantile.client.gui.merchant;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.compat.MoodTooltipFormatter;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.RestockTimerS2CPayload;
import com.rfizzle.mercantile.network.VillagerInfoPanelS2CPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the villager info panel — the framed block of profession, level, XP,
 * reputation, mood, trade count, workstation status and the restock subsection.
 *
 * <p>The same {@link #drawContents} routine backs both the inline panel (shown
 * beside the merchant window when it fits) and the centered modal overlay, so
 * the two never drift apart. It also owns the small sprite glyphs the title row
 * and overlay chrome use (lock, info, close). Layout constants and positions
 * come from {@link MerchantScreenLayout}; state comes in as arguments, so this
 * class holds none of its own.
 */
public final class MerchantInfoPanelRenderer {
    private MerchantInfoPanelRenderer() {
    }

    private static final ResourceLocation LOCKED_SPRITE = Mercantile.id("locked_button");
    private static final ResourceLocation UNLOCKED_SPRITE = Mercantile.id("unlocked_button");
    private static final ResourceLocation INFO_BUTTON_SPRITE = Mercantile.id("info_button");
    private static final ResourceLocation CLOSE_BUTTON_SPRITE = Mercantile.id("close_button");

    public static final int INFO_PANEL_BG_COLOR = 0xC0101010;
    public static final int OVERLAY_PANEL_BG_COLOR = 0xFF101010;
    private static final int INFO_PANEL_BORDER_COLOR = 0xFF555555;
    private static final int INFO_PANEL_TEXT_COLOR = 0xFFFFFFFF;
    private static final int INFO_PANEL_DIM_COLOR = 0xFFA0A0A0;
    private static final int XP_BAR_BG_COLOR = 0xFF404040;
    private static final int XP_BAR_FG_COLOR = 0xFF4FC74F;

    private static final int INFO_ICON_BG_COLOR = 0xC0303030;
    private static final int INFO_ICON_BORDER_COLOR = 0xFF888888;
    private static final int CLOSE_BUTTON_HOVER_BG_COLOR = 0xC0A03030;

    private static final int PAD = MerchantScreenLayout.INFO_PANEL_PAD;

    // ---- Title-row + overlay chrome glyphs ----

    public static void drawLockIcon(GuiGraphics g, boolean locked, int x, int y) {
        g.blitSprite(locked ? LOCKED_SPRITE : UNLOCKED_SPRITE, x, y,
                MerchantScreenLayout.ICON_SIZE, MerchantScreenLayout.ICON_SIZE);
    }

    public static void drawInfoIcon(GuiGraphics g, int x, int y) {
        g.blitSprite(INFO_BUTTON_SPRITE, x, y,
                MerchantScreenLayout.INFO_ICON_SIZE, MerchantScreenLayout.INFO_ICON_SIZE);
    }

    /** The overlay's top-right close button, with a hover-tinted background. */
    public static void drawCloseButton(GuiGraphics g, int cx, int cy, boolean hovered) {
        int size = MerchantScreenLayout.CLOSE_BUTTON_SIZE;
        int bg = hovered ? CLOSE_BUTTON_HOVER_BG_COLOR : INFO_ICON_BG_COLOR;
        g.fill(cx, cy, cx + size, cy + size, bg);
        g.fill(cx, cy, cx + size, cy + 1, INFO_ICON_BORDER_COLOR);
        g.fill(cx, cy + size - 1, cx + size, cy + size, INFO_ICON_BORDER_COLOR);
        g.fill(cx, cy, cx + 1, cy + size, INFO_ICON_BORDER_COLOR);
        g.fill(cx + size - 1, cy, cx + size, cy + size, INFO_ICON_BORDER_COLOR);
        g.blitSprite(CLOSE_BUTTON_SPRITE, cx, cy, size, size);
    }

    // ---- Panel body ----

    public static void drawFrame(GuiGraphics g, int x, int y, int w, int h, int bgColor) {
        g.fill(x, y, x + w, y + h, bgColor);
        g.fill(x, y, x + w, y + 1, INFO_PANEL_BORDER_COLOR);
        g.fill(x, y + h - 1, x + w, y + h, INFO_PANEL_BORDER_COLOR);
        g.fill(x, y, x + 1, y + h, INFO_PANEL_BORDER_COLOR);
        g.fill(x + w - 1, y, x + w, y + h, INFO_PANEL_BORDER_COLOR);
    }

    /**
     * Render the info-panel body at ({@code panelX}, {@code panelY}). The
     * restock subsection is appended when {@code timer} is present and {@link
     * MercantileConfig#enableRestockIndicator} is on; {@code allOffersFresh} and
     * {@code gameTime} feed its "fully stocked" / countdown lines.
     */
    public static void drawContents(GuiGraphics guiGraphics, Font font, MercantileConfig config,
                                    VillagerInfoPanelS2CPayload info, Component screenTitle,
                                    RestockTimerS2CPayload timer, boolean allOffersFresh, long gameTime,
                                    int panelX, int panelY, int panelWidth) {
        int contentX = panelX + PAD;
        int contentWidth = panelWidth - 2 * PAD;
        int y = panelY + PAD;

        // Title (villager display name), bold + centered.
        Component title = screenTitle.copy().withStyle(ChatFormatting.BOLD);
        int titleX = panelX + (panelWidth - font.width(title)) / 2;
        guiGraphics.drawString(font, title, titleX, y, INFO_PANEL_TEXT_COLOR, false);
        y += font.lineHeight + 4;

        // Profession on its own line; level on a second dim line. Split avoids
        // overflow of the 98px content area on long localized names.
        String profession = info.profession();
        if (profession == null || profession.isEmpty() || "none".equals(profession)) {
            guiGraphics.drawString(font,
                    Component.translatable("gui.mercantile.info.unemployed"),
                    contentX, y, INFO_PANEL_TEXT_COLOR, false);
            y += font.lineHeight + 4;
        } else {
            Component professionName = Component.translatable("entity.minecraft.villager." + profession);
            Component levelName = Component.translatable("merchant.level." + info.level());
            guiGraphics.drawString(font, professionName, contentX, y, INFO_PANEL_TEXT_COLOR, false);
            y += font.lineHeight + 4;
            guiGraphics.drawString(font, levelName, contentX, y, INFO_PANEL_DIM_COLOR, false);
            y += font.lineHeight + 4;
        }

        // XP bar — omitted at max level, where the level label already reads "Master".
        if (info.level() < 5) {
            int barWidth = contentWidth;
            int barHeight = 5;
            int filled = info.xpToNextLevel() > 0
                    ? Math.min(barWidth, (int) ((long) info.xp() * barWidth / info.xpToNextLevel()))
                    : 0;
            guiGraphics.fill(contentX, y, contentX + barWidth, y + barHeight, XP_BAR_BG_COLOR);
            if (filled > 0) {
                guiGraphics.fill(contentX, y, contentX + filled, y + barHeight, XP_BAR_FG_COLOR);
            }
            y += barHeight + 2;
            Component xpText = Component.translatable("gui.mercantile.info.xp", info.xp(), info.xpToNextLevel());
            guiGraphics.drawString(font, xpText, contentX, y, INFO_PANEL_DIM_COLOR, false);
            y += font.lineHeight + 4;
        }

        // Reputation tier + score.
        ChatFormatting tierColor = colorForTier(info.reputationTier());
        Component tier = Component.translatable(info.reputationTier()).withStyle(tierColor);
        Component repLine = Component.translatable("gui.mercantile.info.reputation", tier, info.reputation());
        guiGraphics.drawString(font, repLine, contentX, y, INFO_PANEL_TEXT_COLOR, false);
        y += font.lineHeight + 4;

        // Mood tier — empty key means the mood system is disabled server-side.
        if (info.moodTier() != null && !info.moodTier().isEmpty()) {
            Component moodName = Component.translatable(info.moodTier())
                    .withStyle(MoodTooltipFormatter.colorForTier(info.moodTier()));
            Component moodLine = Component.translatable("gui.mercantile.info.mood", moodName);
            guiGraphics.drawString(font, moodLine, contentX, y, INFO_PANEL_TEXT_COLOR, false);
            y += font.lineHeight + 4;
        }

        // Trade count.
        Component trades = Component.translatable("gui.mercantile.info.trades", info.totalTrades());
        guiGraphics.drawString(font, trades, contentX, y, INFO_PANEL_TEXT_COLOR, false);
        y += font.lineHeight + 4;

        // Workstation status.
        String wsKey = info.hasWorkstation()
                ? "gui.mercantile.info.workstation.bound"
                : "gui.mercantile.info.workstation.missing";
        ChatFormatting wsColor = info.hasWorkstation() ? ChatFormatting.GREEN : ChatFormatting.RED;
        Component workstation = Component.translatable(wsKey).withStyle(wsColor);
        guiGraphics.drawString(font, workstation, contentX, y, INFO_PANEL_TEXT_COLOR, false);
        y += font.lineHeight + 4;

        // Restock subsection.
        if (timer == null || !config.enableRestockIndicator) return;

        if (!timer.hasWorkstation()) {
            guiGraphics.drawString(font,
                    Component.translatable("gui.mercantile.restock.no_workstation")
                            .withStyle(ChatFormatting.RED),
                    contentX, y, INFO_PANEL_TEXT_COLOR, false);
            y += font.lineHeight + 4;
        } else if (allOffersFresh) {
            guiGraphics.drawString(font,
                    Component.translatable("gui.mercantile.restock.fully_stocked")
                            .withStyle(ChatFormatting.GREEN),
                    contentX, y, INFO_PANEL_TEXT_COLOR, false);
            y += font.lineHeight + 4;
        } else if (timer.restockCountToday() < timer.maxRestocksToday()) {
            long nextTick = timer.lastRestockGameTime() + timer.restockIntervalTicks();
            long remaining = Math.max(0L, nextTick - gameTime);
            long totalSeconds = remaining / 20L;
            long minutes = totalSeconds / 60L;
            long seconds = totalSeconds % 60L;
            String timeStr = String.format("%d:%02d", minutes, seconds);
            guiGraphics.drawString(font,
                    Component.translatable("gui.mercantile.restock.timer", timeStr),
                    contentX, y, INFO_PANEL_DIM_COLOR, false);
            y += font.lineHeight + 4;
        }

        // A cap above the vanilla two restocks per day means market day is in effect.
        String countKey = timer.maxRestocksToday() > 2
                ? "gui.mercantile.restock.count.market_day"
                : "gui.mercantile.restock.count";
        guiGraphics.drawString(font,
                Component.translatable(countKey,
                        timer.restockCountToday(), timer.maxRestocksToday()),
                contentX, y, INFO_PANEL_DIM_COLOR, false);
    }

    private static ChatFormatting colorForTier(String tierKey) {
        if (tierKey == null) return ChatFormatting.WHITE;
        return switch (tierKey) {
            case "mercantile.tier.honored" -> ChatFormatting.GOLD;
            case "mercantile.tier.trusted" -> ChatFormatting.GREEN;
            case "mercantile.tier.liked" -> ChatFormatting.DARK_GREEN;
            case "mercantile.tier.distrusted" -> ChatFormatting.YELLOW;
            case "mercantile.tier.reviled" -> ChatFormatting.RED;
            default -> ChatFormatting.WHITE;
        };
    }
}
