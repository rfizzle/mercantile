package com.rfizzle.mercantile.client.hud;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.client.MercantileClient;
import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.VillagerHeadTextures;
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
        Component title = Component.translatable("hud.mercantile.rep_detail.title");
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

        // Fixed-height "chrome": everything above the paged nearby list.
        int perksH = LINE_H + perks.size() * LINE_H + SECTION_GAP;  // heading + lines
        int chromeH = 2 * INSET
                + ICON_SIZE + SECTION_GAP                                  // header
                + 1 + SECTION_GAP                                          // divider
                + LINE_H + 3 + BAR_H + 3 + LINE_H + SECTION_GAP            // score + bar + figures
                + 1 + SECTION_GAP                                          // divider
                + perksH                                                   // perks block
                + 1 + SECTION_GAP                                          // divider
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
        int contentW = max(MIN_CONTENT_W, headerW, scoreRowW, figuresW, perksW, nearbyW, headingRowW);

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

    /** The HUD-STANDARD §5 visibility rules plus the server's reputation toggle. */
    private static boolean shouldRender() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return false;
        if (mc.options.hideGui) return false;
        if (mc.screen != null) return false;
        if (player.isSpectator()) return false;
        if (player.isDeadOrDying()) return false;
        MercantileConfig synced = ClientMercantileData.getServerConfig();
        return synced == null || synced.enableReputation;
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
    private static List<List<Group>> paginate(List<Group> groups, int rowsPerPage) {
        List<List<Group>> pages = new ArrayList<>();
        List<Group> page = new ArrayList<>();
        int rows = 0;
        for (Group g : groups) {
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

    /** A nearby profession group: a header line and a one-line exclusive-trade status. */
    private record Group(Component header, Component status) {
        int rowCount() {
            return 2;
        }
    }
}
