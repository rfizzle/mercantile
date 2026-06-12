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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

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

    /**
     * This element's height contribution for sibling HUD coordination
     * (HUD-STANDARD §3/§6): the standard 20px slot element plus the 2px
     * stacking gap. The box currently renders 16px tall (12px glyph + 2px
     * padding); the slot still reserves the standard 22px so siblings don't
     * shift when the visual is brought up to the 20px spec.
     */
    private static final int HUD_HEIGHT_CONTRIBUTION = 22;

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
        int stackOffset = stackOffsetFor(anchor, TribulationOffset.current());
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
        // The four HUD-STANDARD §5 visibility rules: F1, open screen,
        // spectator mode, death screen.
        if (mc.options.hideGui) return false;
        if (mc.screen != null) return false;
        if (player.isSpectator()) return false;
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
    static int stackOffsetFor(MercantileConfig.Anchor anchor, int siblingHeight) {
        return anchor == MercantileConfig.Anchor.TOP_LEFT ? siblingHeight : 0;
    }

    // --- HUD coordination accessors (HUD-STANDARD §6) ---
    // Reflection targets of MercantileAPI.isHudVisible()/getHudHeight(); keep
    // names and (static, no-arg) signatures in sync with the api facade.

    /**
     * Whether the reputation HUD element is currently being drawn — config
     * enabled, none of the §5 visibility rules hiding it, and a villager in
     * range. Client render state; call on the client only.
     */
    public static boolean isHudVisible() {
        return shouldRender(Minecraft.getInstance());
    }

    /**
     * This element's current height contribution in px (element + gap) for
     * sibling offset computation; 0 when not visible.
     */
    public static int getHudHeight() {
        return isHudVisible() ? HUD_HEIGHT_CONTRIBUTION : 0;
    }

    /**
     * Tribulation's slot-1 height, read through its HUD coordination
     * accessors per HUD-STANDARD §6 — hardcoded sibling heights go stale the
     * moment the user disables or moves the sibling's HUD.
     *
     * <p>Graceful degradation: {@code TribulationAPI.isHudVisible()} /
     * {@code getHudHeight()} are being added to Tribulation in a parallel
     * change and do not exist in its current releases. When the class or
     * methods are absent (older Tribulation), we fall back to the legacy
     * fixed 22px reservation so behavior with current releases is unchanged.
     * When Tribulation is absent entirely the offset is 0.
     */
    static final class TribulationOffset {
        private static final String TRIBULATION_MOD_ID = "tribulation";
        private static final String TRIBULATION_API_CLASS = "com.rfizzle.tribulation.api.TribulationAPI";
        /** Pre-accessor behavior: reserve a fixed strip whenever Tribulation is loaded. */
        private static final int LEGACY_FIXED_OFFSET = 22;

        // Render-thread only, resolved once on first render pass.
        private static boolean resolveAttempted;
        private static MethodHandle isHudVisibleHandle;
        private static MethodHandle getHudHeightHandle;

        private TribulationOffset() {
        }

        /** Tribulation's current height contribution; queried per render pass (cheap client reads). */
        static int current() {
            if (!FabricLoader.getInstance().isModLoaded(TRIBULATION_MOD_ID)) return 0;
            resolveOnce();
            if (isHudVisibleHandle == null || getHudHeightHandle == null) {
                return LEGACY_FIXED_OFFSET;
            }
            try {
                if (!(boolean) isHudVisibleHandle.invokeExact()) return 0;
                return Math.max(0, (int) getHudHeightHandle.invokeExact());
            } catch (Throwable t) {
                // Accessor misbehaving — degrade to the legacy reservation
                // rather than overlapping slot 1.
                return LEGACY_FIXED_OFFSET;
            }
        }

        private static void resolveOnce() {
            if (resolveAttempted) return;
            resolveAttempted = true;
            try {
                Class<?> api = Class.forName(TRIBULATION_API_CLASS);
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                isHudVisibleHandle = lookup.findStatic(api, "isHudVisible", MethodType.methodType(boolean.class));
                getHudHeightHandle = lookup.findStatic(api, "getHudHeight", MethodType.methodType(int.class));
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
                // Older Tribulation without the coordination accessors.
                isHudVisibleHandle = null;
                getHudHeightHandle = null;
                Mercantile.LOGGER.info(
                        "Tribulation present without HUD accessors; using the legacy fixed {}px HUD offset",
                        LEGACY_FIXED_OFFSET);
            }
        }
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
