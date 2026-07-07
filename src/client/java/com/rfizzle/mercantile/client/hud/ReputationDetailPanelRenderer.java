package com.rfizzle.mercantile.client.hud;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.client.MercantileClient;
import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.VillagerHeadTextures;
import com.rfizzle.mercantile.market.MarketDayMath;
import com.rfizzle.mercantile.network.PinnedTradesSummaryS2CPayload;
import com.rfizzle.mercantile.reputation.ReputationPerks;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hold-to-peek reputation detail panel. While {@link
 * MercantileClient#KEY_REPUTATION_DETAIL} is held — and the HUD's normal
 * visibility rules pass — this overlays a framed panel with the player's
 * reputation score, standing, progress to the next standing, the perks that
 * standing grants, and the villager professions standing nearby.
 *
 * <p>The perk listing is read from {@link ReputationPerks}, which derives the
 * price figure and exclusive-trade thresholds from the same {@link
 * ReputationTier} the economy applies, so the panel can never claim a perk the
 * game isn't actually granting.
 *
 * <p>No {@code Screen} is opened: the panel never captures the mouse, pauses
 * the game, or blocks movement — it behaves like vanilla's hold-Tab player
 * list. Because a non-focused HUD layer can't scroll without capturing input,
 * the nearby list keeps a comfortable fixed size: everything that fits is shown
 * at once, and any overflow is paged with a cross-fade and page dots while the
 * header, progress, and perks stay static.
 */
public final class ReputationDetailPanelRenderer implements HudRenderCallback {
    private static final ResourceLocation PANEL = Mercantile.id("textures/gui/reputation_detail_panel.png");
    private static final ResourceLocation ICON = Mercantile.id("textures/gui/reputation_detail_icon.png");

    private static final int TEX_SIZE = 64;    // panel texture is 64x64
    private static final int SLICE = 12;       // 9-slice corner inset (holds the emerald accents)
    private static final int ICON_TEX = 32;    // scales icon texture is 32x32
    private static final int ICON_SIZE = 16;   // drawn header-icon size

    private static final int INSET = 14;       // content inset from the panel edge
    private static final int LINE_H = 10;
    private static final int SECTION_GAP = 5;
    private static final int ROW_INDENT = 6;
    private static final int BAR_H = 6;
    private static final int MIN_CONTENT_W = 188;
    private static final float MAX_SCREEN_FRACTION = 0.92f;

    /** A page of the nearby list is up to this many rows (also clamped by the screen-height budget). */
    private static final int PAGE_COMFORT_ROWS = 10;
    /** A page of the pinned-trades list is up to this many rows (three 2-row pins). */
    private static final int PINS_COMFORT_ROWS = 6;
    /** Time one page stays up, including its fade in/out (ms). */
    private static final long PAGE_HOLD_MS = 2600L;
    /** Cross-fade duration at each page boundary (ms). */
    private static final long FADE_MS = 350L;

    /** How often (in game ticks) the nearby-villager scan refreshes its cache. */
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final double SCAN_RANGE = 32.0;

    private static final int DOT_SIZE = 3;
    private static final int DOT_GAP = 3;

    private static final int COLOR_BONE = 0xFFE8E0D4;
    private static final int COLOR_ASH = 0xFFA89F93;
    private static final int COLOR_BAR_TRACK = 0xFF26241F;
    private static final int COLOR_DOT_OFF = 0xFF55504A;

    // Stock tints for pinned trades — in-stock leaf green, out-of-stock rust, unknown ash.
    private static final int COLOR_STOCK_IN = 0xFF89C07A;
    private static final int COLOR_STOCK_OUT = 0xFFB5654A;
    private static final int COLOR_STOCK_UNKNOWN = COLOR_ASH;

    private static final String BULLET = "› ";  // ›

    /**
     * Cached nearby villager counts keyed by profession id, refreshed at most
     * every {@link #SCAN_INTERVAL_TICKS} ticks and only while the panel is held
     * open. Insertion order is the display order (count desc, set in the scan).
     */
    private final Map<ResourceLocation, Integer> nearbyByProfession = new LinkedHashMap<>();
    private long lastScanTick = Long.MIN_VALUE;

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker delta) {
        if (!shouldRender()) return;
        if (MercantileClient.KEY_REPUTATION_DETAIL == null || !MercantileClient.KEY_REPUTATION_DETAIL.isDown()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int score = ClientMercantileData.getReputationScore();
        ReputationTier tier = ReputationTier.fromScore(score);
        int tierColor = ReputationHudOverlay.tierColor(tier);

        // ---- Perks (fixed block) ----
        List<Component> perks = ReputationPerks.activePerks(score);

        // ---- Nearby villager groups (the only paged section) ----
        refreshNearbyIfStale(mc);
        List<Group> groups = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> e : nearbyByProfession.entrySet()) {
            Component header = Component.translatable("hud.mercantile.rep_detail.group",
                    VillagerHeadTextures.getDisplayName(e.getKey()), e.getValue());
            Component status = ReputationPerks.exclusiveTradesUnlocked(score)
                    ? Component.translatable("hud.mercantile.rep_detail.status.unlocked")
                    : Component.translatable("hud.mercantile.rep_detail.status.locked");
            groups.add(new Group(header, status));
        }
        boolean hasNearby = !groups.isEmpty();
        Component nearbyHeading = Component.translatable("hud.mercantile.rep_detail.nearby_heading");
        Component noNearby = Component.translatable("hud.mercantile.rep_detail.no_villagers");
        Component perksHeading = Component.translatable("hud.mercantile.rep_detail.perks_heading");

        // ---- Header / progress strings ----
        Component title = Component.translatable("hud.mercantile.detail.title");
        Component standing = tier.displayName();
        Component scoreText = Component.translatable("hud.mercantile.rep_detail.score", score);

        ReputationTier next = ReputationHudOverlay.nextTierAbove(tier);
        Component nextText = next == null
                ? Component.translatable("hud.mercantile.rep_detail.max")
                : Component.translatable("hud.mercantile.rep_detail.next", next.displayName(), next.minScore());

        float fraction = ReputationHudOverlay.progressFraction(score, tier);
        int percent = Math.round(fraction * 100f);
        Component progressText = next == null
                ? Component.translatable("hud.mercantile.rep_detail.score_only", score)
                : Component.translatable("hud.mercantile.rep_detail.progress", score, next.minScore());
        Component percentText = Component.translatable("hud.mercantile.rep_detail.percent", percent);
        Component dailyText = Component.translatable("hud.mercantile.rep_detail.daily",
                ClientMercantileData.getReputationDailyEarned(), ClientMercantileData.getReputationDailyCap());

        // ---- Status rows: market day + follow count (both need the synced config) ----
        MercantileConfig synced = ClientMercantileData.getServerConfig();
        boolean marketActive = false;
        Component marketText = null;
        if (synced != null && synced.enableMarketDay && mc.level != null) {
            long dayTime = mc.level.getDayTime();
            marketActive = MarketDayMath.isMarketDay(dayTime, synced.marketDayIntervalDays);
            if (marketActive) {
                marketText = Component.translatable("hud.mercantile.rep_detail.market_day.active",
                        synced.marketDayDiscountPercent);
            } else {
                long days = MarketDayMath.daysUntilNextMarketDay(dayTime, synced.marketDayIntervalDays);
                marketText = days == 1
                        ? Component.translatable("hud.mercantile.rep_detail.market_day.tomorrow")
                        : Component.translatable("hud.mercantile.rep_detail.market_day.next", days);
            }
        }
        int followCount = ClientMercantileData.getFollowCount();
        Component followText = synced != null && followCount > 0
                ? Component.translatable("hud.mercantile.rep_detail.following",
                        followCount, synced.maxFollowingVillagers)
                : null;
        int statusRows = (marketText != null ? 1 : 0) + (followText != null ? 1 : 0);
        // Rows + gap plus the section's own trailing divider.
        int statusH = statusRows == 0 ? 0 : statusRows * LINE_H + SECTION_GAP + 1 + SECTION_GAP;

        // ---- Pinned trades (paged section above the nearby list) ----
        // Player-scoped and persistent (synced separately from the merchant screen). Hidden
        // entirely when pinning is off or the player has no pins.
        List<PinnedTradesSummaryS2CPayload.Entry> pinEntries =
                synced != null && synced.enableTradePinning
                        ? ClientMercantileData.getPinnedTradesSummary()
                        : List.of();
        List<PinGroup> pinGroups = new ArrayList<>();
        for (PinnedTradesSummaryS2CPayload.Entry entry : pinEntries) {
            PinnedTradesSummaryS2CPayload.Status status =
                    PinnedTradesSummaryS2CPayload.Status.fromOrdinal(entry.status());
            Component statusText = Component.translatable(switch (status) {
                case IN_STOCK -> "hud.mercantile.rep_detail.pin_status.in_stock";
                case OUT_OF_STOCK -> "hud.mercantile.rep_detail.pin_status.out_of_stock";
                case UNKNOWN -> "hud.mercantile.rep_detail.pin_status.unknown";
            });
            int stockColor = switch (status) {
                case IN_STOCK -> COLOR_STOCK_IN;
                case OUT_OF_STOCK -> COLOR_STOCK_OUT;
                case UNKNOWN -> COLOR_STOCK_UNKNOWN;
            };
            Component summary = Component.literal(entry.tradeSummary());
            Component detail = Component.translatable("hud.mercantile.rep_detail.pin_detail",
                    entry.villagerName(), statusText);
            pinGroups.add(new PinGroup(summary, detail, stockColor));
        }
        boolean hasPins = !pinGroups.isEmpty();
        Component pinsHeading = Component.translatable("hud.mercantile.rep_detail.pins_heading");

        List<List<PinGroup>> pinPages = List.of();
        int pinsBodyRows = 0;
        int pinsW = 0;
        if (hasPins) {
            int totalPinRows = pinGroups.size() * 2;
            int pinsRowsPerPage = Math.max(2, Math.min(PINS_COMFORT_ROWS, totalPinRows));
            pinPages = paginate(pinGroups, pinsRowsPerPage);
            for (List<PinGroup> page : pinPages) {
                pinsBodyRows = Math.max(pinsBodyRows, page.size() * 2);
                for (PinGroup g : page) {
                    pinsW = Math.max(pinsW, font.width(g.summary()));
                    pinsW = Math.max(pinsW, ROW_INDENT + font.width(bullet(g.detail())));
                }
            }
        }
        int numPinPages = pinPages.size();
        int pinsDotsW = numPinPages > 1 ? numPinPages * DOT_SIZE + (numPinPages - 1) * DOT_GAP : 0;
        int pinsHeadingRowW = font.width(pinsHeading) + (pinsDotsW > 0 ? 12 + pinsDotsW : 0);
        // Folded into chrome: heading + fixed body + trailing divider.
        int pinsH = hasPins ? LINE_H + pinsBodyRows * LINE_H + SECTION_GAP + 1 + SECTION_GAP : 0;

        // Fixed-height "chrome": everything above the paged nearby list.
        int perksH = LINE_H + perks.size() * LINE_H + SECTION_GAP;  // heading + lines
        int chromeH = 2 * INSET
                + ICON_SIZE + SECTION_GAP                                  // header
                + 1 + SECTION_GAP                                          // divider
                + LINE_H + 3 + BAR_H + 3 + LINE_H + SECTION_GAP            // score + bar + figures
                + 1 + SECTION_GAP                                          // divider
                + perksH                                                   // perks block
                + 1 + SECTION_GAP                                          // divider
                + statusH                                                  // market day + follow rows (if any)
                + pinsH                                                     // pinned trades block (if any)
                + LINE_H;                                                  // nearby heading

        // Paginate the nearby list to a comfortable, bounded height.
        int rowBudget = (int) (graphics.guiHeight() * MAX_SCREEN_FRACTION) - chromeH;
        int rowsPerPage = Math.max(1, Math.min(PAGE_COMFORT_ROWS, rowBudget / LINE_H));
        List<List<Group>> pages = paginate(groups, rowsPerPage);
        int numPages = pages.size();

        // Fixed nearby-body height across pages so cycling never resizes the panel.
        int bodyRows = hasNearby ? 0 : 1;
        int groupW = 0;
        for (List<Group> page : pages) {
            int rows = 0;
            for (Group g : page) {
                rows += g.rowCount();
                groupW = Math.max(groupW, font.width(g.header()));
                groupW = Math.max(groupW, ROW_INDENT + font.width(bullet(g.status())));
            }
            bodyRows = Math.max(bodyRows, rows);
        }
        int nearbyW = hasNearby ? groupW : font.width(noNearby);

        // ---- Content width ----
        int dotsW = numPages > 1 ? numPages * DOT_SIZE + (numPages - 1) * DOT_GAP : 0;
        int headingRowW = font.width(nearbyHeading) + (dotsW > 0 ? 12 + dotsW : 0);
        int headerW = ICON_SIZE + 4 + font.width(title) + 12 + font.width(standing);
        int scoreRowW = font.width(scoreText) + 12 + font.width(nextText);
        int figuresW = font.width(progressText) + 6 + font.width(percentText) + 12 + font.width(dailyText);
        int perksW = font.width(perksHeading);
        for (Component perk : perks) {
            perksW = Math.max(perksW, ROW_INDENT + font.width(bullet(perk)));
        }
        int statusW = 0;
        if (marketText != null) statusW = Math.max(statusW, font.width(marketText));
        if (followText != null) statusW = Math.max(statusW, font.width(followText));
        int contentW = max(MIN_CONTENT_W, headerW, scoreRowW, figuresW, perksW, statusW,
                pinsW, pinsHeadingRowW, nearbyW, headingRowW);

        int contentH = chromeH - 2 * INSET + bodyRows * LINE_H;
        int panelW = contentW + 2 * INSET;
        int panelH = contentH + 2 * INSET;
        int panelX = (graphics.guiWidth() - panelW) / 2;
        int panelY = (graphics.guiHeight() - panelH) / 2;

        // ---- Safety net: scale the whole panel down if it still wouldn't fit ----
        graphics.setColor(1f, 1f, 1f, 1f);
        float fitScale = Math.min(1f, Math.min(
                graphics.guiWidth() * MAX_SCREEN_FRACTION / panelW,
                graphics.guiHeight() * MAX_SCREEN_FRACTION / panelH));
        boolean scaled = fitScale < 1f;
        if (scaled) {
            float scx = graphics.guiWidth() / 2f;
            float scy = graphics.guiHeight() / 2f;
            graphics.pose().pushPose();
            graphics.pose().translate(scx, scy, 0f);
            graphics.pose().scale(fitScale, fitScale, 1f);
            graphics.pose().translate(-scx, -scy, 0f);
        }

        // ---- Frame + content ----
        drawNineSlice(graphics, panelX, panelY, panelW, panelH);

        int contentX = panelX + INSET;
        int y = panelY + INSET;

        // Header: tier-tinted scales + title, standing right-aligned in tier color.
        blitTinted(graphics, contentX, y, tierColor);
        graphics.drawString(font, title, contentX + ICON_SIZE + 4, y + 4, COLOR_BONE, true);
        graphics.drawString(font, standing, contentX + contentW - font.width(standing), y + 4, tierColor, true);
        y += ICON_SIZE + SECTION_GAP;

        y = divider(graphics, contentX, y, contentW, tierColor);

        // Score + next-standing row.
        graphics.drawString(font, scoreText, contentX, y, COLOR_BONE, true);
        graphics.drawString(font, nextText, contentX + contentW - font.width(nextText), y, COLOR_ASH, true);
        y += LINE_H + 3;

        // Progress bar: ash track, tier-colored fill.
        graphics.fill(contentX, y, contentX + contentW, y + BAR_H, COLOR_BAR_TRACK);
        int filledW = Math.round(contentW * Math.max(0f, Math.min(1f, fraction)));
        if (filledW > 0) {
            graphics.fill(contentX, y, contentX + filledW, y + BAR_H, tierColor);
        }
        y += BAR_H + 3;

        // Progress figures + daily-earned row.
        graphics.drawString(font, progressText, contentX, y, COLOR_ASH, true);
        graphics.drawString(font, percentText, contentX + font.width(progressText) + 6, y, COLOR_ASH, true);
        graphics.drawString(font, dailyText, contentX + contentW - font.width(dailyText), y, COLOR_ASH, true);
        y += LINE_H + SECTION_GAP;

        y = divider(graphics, contentX, y, contentW, tierColor);

        // Perks block (static).
        graphics.drawString(font, perksHeading, contentX, y, COLOR_ASH, true);
        y += LINE_H;
        for (Component perk : perks) {
            graphics.drawString(font, bullet(perk), contentX + ROW_INDENT, y, COLOR_BONE, true);
            y += LINE_H;
        }
        y += SECTION_GAP;

        y = divider(graphics, contentX, y, contentW, tierColor);

        // Status rows: market day (accented while active) + follow count.
        if (statusRows > 0) {
            if (marketText != null) {
                graphics.drawString(font, marketText, contentX, y, marketActive ? tierColor : COLOR_ASH, true);
                y += LINE_H;
            }
            if (followText != null) {
                graphics.drawString(font, followText, contentX, y, COLOR_BONE, true);
                y += LINE_H;
            }
            y += SECTION_GAP;
            y = divider(graphics, contentX, y, contentW, tierColor);
        }

        // ---- Pinned trades: static caption + dots, page cross-fading ----
        if (hasPins) {
            graphics.drawString(font, pinsHeading, contentX, y, COLOR_ASH, true);
            int pinPage = 0;
            float pinAlpha = 1f;
            if (numPinPages > 1) {
                long now = System.currentTimeMillis();
                pinPage = (int) ((now / PAGE_HOLD_MS) % numPinPages);
                pinAlpha = pageAlpha(now % PAGE_HOLD_MS);
                drawDots(graphics, contentX + contentW - pinsDotsW, y + 2, numPinPages, pinPage, tierColor);
            }
            y += LINE_H;

            int cy = y;
            for (PinGroup g : pinPages.get(pinPage)) {
                graphics.drawString(font, g.summary(), contentX, cy, fade(COLOR_BONE, pinAlpha), true);
                cy += LINE_H;
                graphics.drawString(font, bullet(g.detail()), contentX + ROW_INDENT, cy,
                        fade(g.color(), pinAlpha), true);
                cy += LINE_H;
            }
            // Advance by the fixed body height (not the current page's) so a short page
            // never shifts the nearby section up and resizes the panel between pages.
            y += pinsBodyRows * LINE_H + SECTION_GAP;
            y = divider(graphics, contentX, y, contentW, tierColor);
        }

        // ---- Nearby list: static caption + dots, page cross-fading ----
        if (!hasNearby) {
            graphics.drawString(font, nearbyHeading, contentX, y, COLOR_ASH, true);
            y += LINE_H;
            graphics.drawString(font, noNearby, contentX, y, COLOR_ASH, true);
        } else {
            graphics.drawString(font, nearbyHeading, contentX, y, COLOR_ASH, true);
            int page = 0;
            float bodyAlpha = 1f;
            if (numPages > 1) {
                long now = System.currentTimeMillis();
                page = (int) ((now / PAGE_HOLD_MS) % numPages);
                bodyAlpha = pageAlpha(now % PAGE_HOLD_MS);
                drawDots(graphics, contentX + contentW - dotsW, y + 2, numPages, page, tierColor);
            }
            y += LINE_H;

            int headerColor = fade(COLOR_BONE, bodyAlpha);
            int statusColor = fade(tierColor, bodyAlpha);
            int lockedColor = fade(COLOR_ASH, bodyAlpha);
            boolean unlocked = ReputationPerks.exclusiveTradesUnlocked(score);
            int cy = y;
            for (Group g : pages.get(page)) {
                graphics.drawString(font, g.header(), contentX, cy, headerColor, true);
                cy += LINE_H;
                graphics.drawString(font, bullet(g.status()), contentX + ROW_INDENT, cy,
                        unlocked ? statusColor : lockedColor, true);
                cy += LINE_H;
            }
        }

        if (scaled) {
            graphics.pose().popPose();
        }
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * The badge's shared visibility gate — {@link ReputationHudOverlay#hudGateVisible}
     * (HUD-STANDARD §5 rules, {@code enableReputationHud}, and the server's
     * reputation toggle). Unlike the badge, the panel has no villager-proximity
     * requirement: it shows a "no villagers nearby" state instead.
     */
    private static boolean shouldRender() {
        return ReputationHudOverlay.hudGateVisible(Minecraft.getInstance());
    }

    /**
     * Refresh {@link #nearbyByProfession} if the cache is older than {@link
     * #SCAN_INTERVAL_TICKS} ticks (or the world's game time jumped backward, a
     * world change). Villagers without a tradeable profession (unemployed,
     * nitwit) are skipped; the rest are counted by profession and ordered by
     * count descending so the busiest professions lead.
     */
    private void refreshNearbyIfStale(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) return;

        long now = level.getGameTime();
        boolean fresh = lastScanTick != Long.MIN_VALUE && now >= lastScanTick && now - lastScanTick < SCAN_INTERVAL_TICKS;
        if (fresh) return;
        lastScanTick = now;

        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        AABB box = player.getBoundingBox().inflate(SCAN_RANGE);
        for (Villager villager : level.getEntitiesOfClass(Villager.class, box, v -> true)) {
            VillagerProfession profession = villager.getVillagerData().getProfession();
            if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) continue;
            ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            if (id == null) continue;
            counts.merge(id, 1, Integer::sum);
        }

        nearbyByProfession.clear();
        counts.entrySet().stream()
                .sorted((a, b) -> {
                    int byCount = Integer.compare(b.getValue(), a.getValue());
                    return byCount != 0 ? byCount : a.getKey().compareTo(b.getKey());
                })
                .forEach(e -> nearbyByProfession.put(e.getKey(), e.getValue()));
    }

    /**
     * Partition groups into single-column pages of up to {@code rowsPerPage}
     * rows, never splitting a group. Always returns at least one (possibly
     * empty) page.
     */
    private static <T extends Paged> List<List<T>> paginate(List<T> groups, int rowsPerPage) {
        List<List<T>> pages = new ArrayList<>();
        List<T> page = new ArrayList<>();
        int rows = 0;
        for (T g : groups) {
            int gr = g.rowCount();
            if (rows > 0 && rows + gr > rowsPerPage) {
                pages.add(page);
                page = new ArrayList<>();
                rows = 0;
            }
            page.add(g);
            rows += gr;
        }
        pages.add(page);
        return pages;
    }

    private static Component bullet(Component body) {
        return Component.literal(BULLET).append(body);
    }

    /** Triangular fade: 0→1 over the first {@link #FADE_MS}, 1→0 over the last. */
    private static float pageAlpha(long within) {
        if (within < FADE_MS) return within / (float) FADE_MS;
        if (within > PAGE_HOLD_MS - FADE_MS) return (PAGE_HOLD_MS - within) / (float) FADE_MS;
        return 1f;
    }

    private static void drawDots(GuiGraphics g, int x, int y, int count, int active, int activeColor) {
        int dx = x;
        for (int i = 0; i < count; i++) {
            g.fill(dx, y, dx + DOT_SIZE, y + DOT_SIZE, i == active ? activeColor : COLOR_DOT_OFF);
            dx += DOT_SIZE + DOT_GAP;
        }
    }

    private int divider(GuiGraphics graphics, int x, int y, int width, int color) {
        graphics.fill(x, y, x + width, y + 1, withAlpha(color, 0xB0));
        return y + 1 + SECTION_GAP;
    }

    /** Draw the 64x64 panel texture as a 9-slice scaled to {@code w}x{@code h}. */
    private static void drawNineSlice(GuiGraphics g, int x, int y, int w, int h) {
        int s = SLICE;
        int center = TEX_SIZE - 2 * s;
        int innerW = w - 2 * s;
        int innerH = h - 2 * s;
        blit(g, x, y, s, s, 0, 0, s, s);
        blit(g, x + w - s, y, s, s, TEX_SIZE - s, 0, s, s);
        blit(g, x, y + h - s, s, s, 0, TEX_SIZE - s, s, s);
        blit(g, x + w - s, y + h - s, s, s, TEX_SIZE - s, TEX_SIZE - s, s, s);
        blit(g, x + s, y, innerW, s, s, 0, center, s);
        blit(g, x + s, y + h - s, innerW, s, s, TEX_SIZE - s, center, s);
        blit(g, x, y + s, s, innerH, 0, s, s, center);
        blit(g, x + w - s, y + s, s, innerH, TEX_SIZE - s, s, s, center);
        blit(g, x + s, y + s, innerW, innerH, s, s, center, center);
    }

    private static void blit(GuiGraphics g, int x, int y, int dw, int dh, int u, int v, int sw, int sh) {
        g.blit(PANEL, x, y, dw, dh, u, v, sw, sh, TEX_SIZE, TEX_SIZE);
    }

    private static void blitTinted(GuiGraphics g, int x, int y, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float gr = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        g.setColor(r, gr, b, 1f);
        g.blit(ICON, x, y, ICON_SIZE, ICON_SIZE, 0, 0, ICON_TEX, ICON_TEX, ICON_TEX, ICON_TEX);
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /** Scale a colour's existing alpha by {@code a} (0..1) — used for the body fade. */
    private static int fade(int color, float a) {
        int base = (color >>> 24) & 0xFF;
        int scaled = Math.round(base * Math.max(0f, Math.min(1f, a)));
        return (scaled << 24) | (color & 0xFFFFFF);
    }

    private static int max(int... values) {
        int m = Integer.MIN_VALUE;
        for (int v : values) {
            m = Math.max(m, v);
        }
        return m;
    }

    /** A block that occupies a whole number of rows when paginated. */
    private interface Paged {
        int rowCount();
    }

    /** A nearby profession group: a header line and a one-line exclusive-trade status. */
    private record Group(Component header, Component status) implements Paged {
        @Override
        public int rowCount() {
            return 2;
        }
    }

    /** A pinned trade: a trade-summary line and a villager + stock-status line, stock-tinted. */
    private record PinGroup(Component summary, Component detail, int color) implements Paged {
        @Override
        public int rowCount() {
            return 2;
        }
    }
}
