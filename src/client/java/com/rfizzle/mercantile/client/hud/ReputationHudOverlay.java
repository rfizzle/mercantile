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

    private static final int BASE_X = 2;
    private static final int BASE_Y = 2;
    private static final int BOX_PAD_X = 4;
    private static final int BOX_PAD_Y = 2;
    private static final int ICON_SIZE = 16;
    private static final int ICON_TEXT_GAP = 4;
    private static final int TEXT_HEIGHT = 9;
    private static final int BG_COLOR = 0x99000000;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    // Tribulation reserves the same top-left strip when present; bump our HUD down to clear it.
    private static final int TRIBULATION_RESERVED_HEIGHT = 22;
    private static final String TRIBULATION_MOD_ID = "tribulation";

    // Render-thread only — HudRenderCallback fires on the render thread.
    private static long lastScanTick = Long.MIN_VALUE;
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

        int boxW = boxWidthFor(textWidth);
        int boxH = BOX_PAD_Y + ICON_SIZE + BOX_PAD_Y;
        int x = BASE_X;
        int y = yOffsetFor(FabricLoader.getInstance().isModLoaded(TRIBULATION_MOD_ID));

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
        if (now - lastScanTick >= SCAN_INTERVAL_TICKS || now < lastScanTick) {
            nearbyCached = !level.getEntitiesOfClass(
                    Villager.class,
                    player.getBoundingBox().inflate(PROXIMITY_RADIUS),
                    e -> true).isEmpty();
            lastScanTick = now;
        }
        return nearbyCached;
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

    static int yOffsetFor(boolean tribulationLoaded) {
        // We can't introspect Tribulation's runtime HUD toggle without a shared interop API;
        // reservation is binary on isModLoaded for now.
        return tribulationLoaded ? BASE_Y + TRIBULATION_RESERVED_HEIGHT : BASE_Y;
    }
}
