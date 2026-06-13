package com.rfizzle.mercantile.client.hud;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.config.MercantileConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Slot-2 HUD badge in the shared Concord layout: an icon-only element
 * matching Tribulation's slot-1 badge — a 16×16 glyph over a 2px progress
 * bar, state conveyed by color rather than text. The glyph is the vanilla
 * emerald item (asset philosophy: vanilla-first, no custom textures); the
 * bar is tinted by reputation tier and fills with progress toward the next
 * tier.
 */
public final class ReputationHudOverlay {

    private static final ItemStack GLYPH = new ItemStack(Items.EMERALD);

    private static final int PROXIMITY_RADIUS = 32;
    private static final int SCAN_INTERVAL_TICKS = 20;

    private static final int ICON_SIZE = 16;
    private static final int BAR_HEIGHT = 2;
    private static final int BAR_GAP = 1;
    private static final int BAR_BG_COLOR = 0xC0202020;

    /**
     * This element's height contribution for sibling HUD coordination
     * (HUD-STANDARD §3/§6): the standard 20px slot element plus the 2px
     * stacking gap. The badge renders 19px tall (16px glyph + 1px gap +
     * 2px bar) and rounds into the 20px box exactly as Tribulation's does.
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

        int score = ClientMercantileData.getReputationScore();
        ReputationTier tier = ReputationTier.fromScore(score);
        int color = tierColor(tier);
        float fraction = progressFraction(score, tier);

        MercantileConfig config = MercantileConfig.get();
        MercantileConfig.Anchor anchor = config.hudAnchor != null ? config.hudAnchor : MercantileConfig.Anchor.TOP_LEFT;
        int badgeH = ICON_SIZE + BAR_GAP + BAR_HEIGHT;
        int stackOffset = stackOffsetFor(anchor, TribulationOffset.current());
        int x = computeOriginX(anchor, graphics.guiWidth(), config.hudOffsetX, ICON_SIZE);
        int y = computeOriginY(anchor, graphics.guiHeight(), config.hudOffsetY, badgeH, stackOffset);

        // The emerald is an item render, not a tintable grayscale sprite, so
        // tier state lives in the bar color. When the pixel-art 16×16 glyph
        // master lands (deferred asset), it can be tinted like Tribulation's.
        graphics.renderItem(GLYPH, x, y);

        int barY = y + ICON_SIZE + BAR_GAP;
        graphics.fill(x, barY, x + ICON_SIZE, barY + BAR_HEIGHT, BAR_BG_COLOR);
        int filledW = Math.round(ICON_SIZE * fraction);
        if (filledW > 0) {
            graphics.fill(x, barY, x + filledW, barY + BAR_HEIGHT, color);
        }
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

    /**
     * Bar tint per tier. Positive tiers ramp through the mod's emerald
     * accents; NEUTRAL is white and the negative tiers use the shared
     * orange/red state ramp (HUD-STANDARD §3 — state tinting is the
     * element's only decoration).
     */
    static int tierColor(ReputationTier tier) {
        return switch (tier) {
            case HONORED -> 0xFF6DDB94;    // Emerald Bright (accent 2)
            case TRUSTED -> 0xFF50C878;    // Emerald (accent 1)
            case LIKED -> 0xFFA9D9B5;      // pale emerald
            case NEUTRAL -> 0xFFFFFFFF;    // white
            case DISTRUSTED -> 0xFFFF8C00; // orange (shared state ramp)
            case REVILED -> 0xFFFF4040;    // red (shared state ramp)
        };
    }

    /**
     * Fraction of the way from this tier's floor to the next tier's floor;
     * 1.0 at the top tier. Tiers are declared in descending minScore order,
     * so the next tier up is the previous enum constant.
     */
    static float progressFraction(int score, ReputationTier tier) {
        ReputationTier next = nextTierAbove(tier);
        if (next == null) return 1.0f;
        int span = next.minScore() - tier.minScore();
        if (span <= 0) return 1.0f;
        float fraction = (score - tier.minScore()) / (float) span;
        return Math.max(0.0f, Math.min(1.0f, fraction));
    }

    static ReputationTier nextTierAbove(ReputationTier tier) {
        int idx = tier.ordinal();
        return idx == 0 ? null : ReputationTier.values()[idx - 1];
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
     * Origin = top-left corner of the badge. Offsets are measured inward from
     * the anchored edges, so the element keeps its distance from its corner
     * regardless of screen size (HUD-STANDARD §4).
     */
    static int computeOriginX(MercantileConfig.Anchor anchor, int screenW, int offsetX, int badgeW) {
        return switch (anchor) {
            case TOP_LEFT, BOTTOM_LEFT -> offsetX;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenW - offsetX - badgeW;
        };
    }

    /**
     * The stacking offset shifts inward from the anchored vertical edge: down
     * from a top anchor, up from a bottom anchor.
     */
    static int computeOriginY(MercantileConfig.Anchor anchor, int screenH, int offsetY, int badgeH, int stackOffset) {
        return switch (anchor) {
            case TOP_LEFT, TOP_RIGHT -> offsetY + stackOffset;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenH - offsetY - badgeH - stackOffset;
        };
    }
}
