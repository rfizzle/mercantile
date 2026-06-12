package com.rfizzle.mercantile.client.hud;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.config.MercantileConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

public final class ReputationHudOverlay {

    private static final ResourceLocation ICON = Mercantile.id("icon.png");

    private static final int PROXIMITY_RADIUS = 32;
    private static final int SCAN_INTERVAL_TICKS = 20;

    private static final int BOX_PAD_X = 3;
    private static final int BOX_PAD_Y = 2;
    private static final int ICON_SIZE = 12;
    private static final int ICON_TEXT_GAP = 2;
    private static final int TEXT_HEIGHT = 9;
    private static final int BG_COLOR = 0x99000000;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    // Tribulation reserves the same top-left strip when present; bump our HUD down to clear it.
    private static final int TRIBULATION_RESERVED_HEIGHT = 22;
    private static final String TRIBULATION_MOD_ID = "tribulation";

    // Render-thread only — HudRenderCallback fires on the render thread.
    // Initialized to -SCAN_INTERVAL_TICKS so the first frame (now >= 0) triggers a scan;
    // using Long.MIN_VALUE would overflow (now - Long.MIN_VALUE) and the HUD would never render.
    private static long lastScanTick = -SCAN_INTERVAL_TICKS;
    private static boolean nearbyCached = false;

    private ReputationHudOverlay() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(ReputationHudOverlay::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!shouldRender(mc)) return;

        Font font = mc.font;
        Component label = buildLabel();
        int textWidth = font.width(label);

        MercantileConfig config = MercantileConfig.get();
        MercantileConfig.Anchor anchor = config.hudAnchor != null ? config.hudAnchor : MercantileConfig.Anchor.TOP_LEFT;
        int boxW = boxWidthFor(textWidth);
        int boxH = BOX_PAD_Y + ICON_SIZE + BOX_PAD_Y;
        int stackOffset = stackOffsetFor(anchor, FabricLoader.getInstance().isModLoaded(TRIBULATION_MOD_ID));
        int x = computeOriginX(anchor, graphics.guiWidth(), config.hudOffsetX, boxW);
        int y = computeOriginY(anchor, graphics.guiHeight(), config.hudOffsetY, boxH, stackOffset);

        drawBox(graphics, x, y, boxW, boxH);
        graphics.blit(ICON, x + BOX_PAD_X, y + BOX_PAD_Y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        int textX = x + BOX_PAD_X + ICON_SIZE + ICON_TEXT_GAP;
        int textY = y + BOX_PAD_Y + (ICON_SIZE - TEXT_HEIGHT) / 2;
        graphics.drawString(font, label, textX, textY, TEXT_COLOR, true);
    }

    private static boolean shouldRender(Minecraft mc) {
        if (mc == null) return false;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return false;
        if (mc.options.hideGui) return false;
        if (mc.screen != null) return false;
        if (player.isDeadOrDying()) return false;
        if (!MercantileConfig.get().enableReputationHud) return false;
        MercantileConfig synced = ClientMercantileData.getServerConfig();
        if (synced != null && !synced.enableReputation) return false;
        return villagerNearby(level, player);
    }

    private static boolean villagerNearby(ClientLevel level, LocalPlayer player) {
        long now = level.getGameTime();
        if (shouldRescan(now, lastScanTick)) {
            nearbyCached = !level.getEntitiesOfClass(
                    Villager.class,
                    player.getBoundingBox().inflate(PROXIMITY_RADIUS),
                    e -> true).isEmpty();
            lastScanTick = now;
        }
        return nearbyCached;
    }

    static boolean shouldRescan(long now, long lastScanTick) {
        return now - lastScanTick >= SCAN_INTERVAL_TICKS || now < lastScanTick;
    }

    private static void drawBox(GuiGraphics g, int x, int y, int w, int h) {
        // Two overlapping rects produce a 1px corner inset — cheap rounded look without sprites.
        g.fill(x + 1, y, x + w - 1, y + h, BG_COLOR);
        g.fill(x, y + 1, x + w, y + h - 1, BG_COLOR);
    }

    static int boxWidthFor(int textWidth) {
        return BOX_PAD_X + ICON_SIZE + ICON_TEXT_GAP + textWidth + BOX_PAD_X;
    }

    static Component buildLabel() {
        return Component.translatable(ClientMercantileData.getReputationTier());
    }

    /**
     * Sibling stacking offset per HUD-STANDARD §4: stacking applies within an
     * anchor. Tribulation owns slot 1; its anchor is not queryable through the
     * coordination accessors (only visibility/height are), so the reservation
     * applies at our default TOP_LEFT anchor — the slot registry's canonical
     * position. A user who moves our element to another corner opts out of
     * stacking against the default-placed sibling.
     */
    static int stackOffsetFor(MercantileConfig.Anchor anchor, boolean tribulationLoaded) {
        if (anchor != MercantileConfig.Anchor.TOP_LEFT) return 0;
        // We can't introspect Tribulation's runtime HUD toggle without a shared interop API;
        // reservation is binary on isModLoaded for now.
        return tribulationLoaded ? TRIBULATION_RESERVED_HEIGHT : 0;
    }

    /**
     * Origin = top-left corner of the box. Offsets are measured inward from
     * the anchored edges, so the element keeps its distance from its corner
     * regardless of screen size or label width (HUD-STANDARD §4).
     */
    static int computeOriginX(MercantileConfig.Anchor anchor, int screenW, int offsetX, int boxW) {
        return switch (anchor) {
            case TOP_LEFT, BOTTOM_LEFT -> offsetX;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenW - offsetX - boxW;
        };
    }

    /**
     * The stacking offset shifts inward from the anchored vertical edge: down
     * from a top anchor, up from a bottom anchor.
     */
    static int computeOriginY(MercantileConfig.Anchor anchor, int screenH, int offsetY, int boxH, int stackOffset) {
        return switch (anchor) {
            case TOP_LEFT, TOP_RIGHT -> offsetY + stackOffset;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenH - offsetY - boxH - stackOffset;
        };
    }
}
