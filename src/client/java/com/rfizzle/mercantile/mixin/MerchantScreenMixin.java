package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.client.gui.merchant.MerchantInfoPanelRenderer;
import com.rfizzle.mercantile.client.gui.merchant.MerchantScreenLayout;
import com.rfizzle.mercantile.client.gui.merchant.TradePinRenderer;
import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.CycleTradesC2SPayload;
import com.rfizzle.mercantile.network.PinTradeC2SPayload;
import com.rfizzle.mercantile.network.TradePinsS2CPayload;
import com.rfizzle.mercantile.network.VillagerInfoPanelS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wires the Mercantile trade-GUI features into vanilla's {@link MerchantScreen}:
 * the info panel and its centered-overlay fallback, per-trade pins, the
 * profession-lock and info-icon title glyphs, and the re-roll (cycle) button.
 *
 * <p>The mixin is deliberately thin — it owns only the screen-coupled state
 * (overlay open flag, one-shot init guard, the cycle {@link Button} widget) and
 * forwards each injection point to a feature class that holds the actual layout
 * and drawing: {@link MerchantScreenLayout} for geometry, {@link
 * MerchantInfoPanelRenderer} for the panel/overlay body and chrome glyphs, and
 * {@link TradePinRenderer} for the pin column. This mirrors the reputation-HUD
 * split, where rendering lives outside any mixin.
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {

    @Unique
    private static final int OVERLAY_DIM_COLOR = 0xE0000000;

    @Shadow
    private int scrollOff;

    @Unique
    private Button mercantile$cycleButton;

    // Screen.init() runs on every window resize, not just on screen open. Guard
    // the one-shot clear so resizing mid-trade does not wipe villagerInfo/restockTimer/
    // demandPrice and blank the info panel until the server's next periodic resend.
    @Unique
    private boolean mercantile$initialized;

    @Unique
    private boolean mercantile$overlayOpen;

    private MerchantScreenMixin(MerchantMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void mercantile$clearStaleDataOnOpen(CallbackInfo ci) {
        // init() also fires on resize — close any open overlay so its bounds get
        // recomputed against the new this.width/this.height, and so that a resize
        // back to a wide window (where the inline panel fits) doesn't leave a
        // stale overlay covering the trade UI.
        mercantile$overlayOpen = false;
        if (mercantile$initialized) return;
        mercantile$initialized = true;
        ClientMercantileData.clearMerchantScreenData();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mercantile$addCycleButton(CallbackInfo ci) {
        MercantileConfig config = mercantile$config();
        if (!config.enableTradeCycling) return;

        int panelX = mercantile$panelX();
        int panelY = this.topPos;
        int buttonX = panelX + MerchantScreenLayout.INFO_PANEL_PAD;
        int buttonY = panelY + MerchantScreenLayout.INFO_PANEL_HEIGHT
                - MerchantScreenLayout.INFO_PANEL_PAD - MerchantScreenLayout.CYCLE_BUTTON_HEIGHT;
        int buttonW = MerchantScreenLayout.INFO_PANEL_WIDTH - 2 * MerchantScreenLayout.INFO_PANEL_PAD;

        mercantile$cycleButton = Button.builder(
                        Component.translatable("gui.mercantile.reroll_trades"),
                        btn -> mercantile$onCycleClick())
                .bounds(buttonX, buttonY, buttonW, MerchantScreenLayout.CYCLE_BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(mercantile$cycleButton);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void mercantile$updateCycleButtonState(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (mercantile$cycleButton == null) return;

        MercantileConfig config = mercantile$config();
        VillagerInfoPanelS2CPayload info = mercantile$validInfo();

        boolean panelVisible = config.enableInfoPanel && info != null && mercantile$panelFits();
        boolean overlayVisible = mercantile$overlayOpen && config.enableInfoPanel && info != null;
        // Sprint S-049 / B-091: hide the re-roll button while the profession is locked.
        // Re-rolling is technically independent of profession-lock (it re-rolls within the
        // current profession), but the UX intent is that a "locked" villager looks frozen
        // to the player — exposing a re-roll affordance would muddle that signal.
        boolean lockedHidden = config.enableProfessionLock && info != null && info.professionLocked();
        boolean visible = config.enableTradeCycling && !lockedHidden && (panelVisible || overlayVisible);
        mercantile$cycleButton.visible = visible;
        if (!visible) return;

        // Reposition per frame so the button follows the active container (in-panel vs overlay).
        if (overlayVisible) {
            int ox = MerchantScreenLayout.overlayX(this.width);
            int oy = MerchantScreenLayout.overlayY(this.height);
            int buttonW = MerchantScreenLayout.OVERLAY_WIDTH - 2 * MerchantScreenLayout.OVERLAY_PAD;
            mercantile$cycleButton.setX(ox + MerchantScreenLayout.OVERLAY_PAD);
            mercantile$cycleButton.setY(oy + MerchantScreenLayout.OVERLAY_HEIGHT
                    - MerchantScreenLayout.OVERLAY_PAD - MerchantScreenLayout.CYCLE_BUTTON_HEIGHT);
            mercantile$cycleButton.setWidth(buttonW);
        } else {
            int panelX = mercantile$panelX();
            int panelY = this.topPos;
            int buttonW = MerchantScreenLayout.INFO_PANEL_WIDTH - 2 * MerchantScreenLayout.INFO_PANEL_PAD;
            mercantile$cycleButton.setX(panelX + MerchantScreenLayout.INFO_PANEL_PAD);
            mercantile$cycleButton.setY(panelY + MerchantScreenLayout.INFO_PANEL_HEIGHT
                    - MerchantScreenLayout.INFO_PANEL_PAD - MerchantScreenLayout.CYCLE_BUTTON_HEIGHT);
            mercantile$cycleButton.setWidth(buttonW);
        }

        boolean enabled = true;
        if (!this.minecraft.player.isCreative()) {
            int emeraldCount = 0;
            for (var stack : this.minecraft.player.getInventory().items) {
                if (stack.is(Items.EMERALD)) emeraldCount += stack.getCount();
            }
            if (emeraldCount < config.tradeCycleEmeraldCost) {
                enabled = false;
            }
        }

        mercantile$cycleButton.active = enabled;
    }

    @Inject(method = "renderLabels", at = @At("TAIL"))
    private void mercantile$renderLockIcon(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        VillagerInfoPanelS2CPayload info = mercantile$validInfo();
        if (info == null) return;
        MercantileConfig config = mercantile$config();

        if (config.enableProfessionLock) {
            MerchantInfoPanelRenderer.drawLockIcon(guiGraphics, info.professionLocked(),
                    mercantile$lockIconX(), MerchantScreenLayout.LOCK_ICON_Y);
        }

        if (mercantile$infoIconVisible()) {
            MerchantInfoPanelRenderer.drawInfoIcon(guiGraphics, mercantile$infoIconX(), MerchantScreenLayout.LOCK_ICON_Y);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mercantile$renderInfoPanelInject(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        mercantile$renderTradePins(guiGraphics, mouseX, mouseY);
        mercantile$renderInfoPanel(guiGraphics);
        mercantile$renderOverlay(guiGraphics, mouseX, mouseY, partialTick);
    }

    // ---- Trade pins ----

    // Deliberately independent of the info-panel payload: TradePinsS2CPayload carries its
    // own villagerEntityId, so pinning keeps working when enableInfoPanel is off.
    @Unique
    private boolean mercantile$pinsActive() {
        MercantileConfig config = mercantile$config();
        return config.enableTradePinning
                && !mercantile$overlayOpen
                && ClientMercantileData.getTradePins() != null;
    }

    @Unique
    private void mercantile$renderTradePins(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!mercantile$pinsActive()) return;
        TradePinRenderer.render(guiGraphics, this.font, ClientMercantileData.getTradePins(),
                this.menu.getOffers(), this.leftPos, this.topPos, scrollOff, mouseX, mouseY);
    }

    /** Returns the absolute offer index of the pin icon under the cursor, or -1. */
    @Unique
    private int mercantile$pinIndexAt(double mouseX, double mouseY) {
        if (!mercantile$pinsActive()) return -1;
        return TradePinRenderer.indexAt(ClientMercantileData.getTradePins(), this.menu.getOffers(),
                this.leftPos, this.topPos, scrollOff, mouseX, mouseY);
    }

    // ---- Info panel + overlay ----

    @Unique
    private void mercantile$renderInfoPanel(GuiGraphics guiGraphics) {
        MercantileConfig config = mercantile$config();
        if (!config.enableInfoPanel) return;

        VillagerInfoPanelS2CPayload info = ClientMercantileData.getVillagerInfo();
        if (info == null) return;
        if (!mercantile$panelFits()) return;

        int panelX = mercantile$panelX();
        int panelY = this.topPos;
        MerchantInfoPanelRenderer.drawFrame(guiGraphics, panelX, panelY,
                MerchantScreenLayout.INFO_PANEL_WIDTH, MerchantScreenLayout.INFO_PANEL_HEIGHT,
                MerchantInfoPanelRenderer.INFO_PANEL_BG_COLOR);
        mercantile$drawPanelContents(guiGraphics, config, info, panelX, panelY, MerchantScreenLayout.INFO_PANEL_WIDTH);
    }

    @Unique
    private void mercantile$renderOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!mercantile$overlayOpen) return;

        MercantileConfig config = mercantile$config();
        VillagerInfoPanelS2CPayload info = ClientMercantileData.getVillagerInfo();
        // Auto-close if the data we are showing went away mid-frame (e.g. villager despawn).
        if (!config.enableInfoPanel || info == null) {
            mercantile$overlayOpen = false;
            return;
        }

        // Push above vanilla's Z=100 slot-item pass so inventory/hotbar icons don't
        // bleed through the opaque panel. Stays below Z=400 so tooltips still win.
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0f, 0.0f, 300.0f);

        // Dim the trade screen.
        guiGraphics.fill(0, 0, this.width, this.height, OVERLAY_DIM_COLOR);

        int ox = MerchantScreenLayout.overlayX(this.width);
        int oy = MerchantScreenLayout.overlayY(this.height);
        MerchantInfoPanelRenderer.drawFrame(guiGraphics, ox, oy,
                MerchantScreenLayout.OVERLAY_WIDTH, MerchantScreenLayout.OVERLAY_HEIGHT,
                MerchantInfoPanelRenderer.OVERLAY_PANEL_BG_COLOR);
        mercantile$drawPanelContents(guiGraphics, config, info, ox, oy, MerchantScreenLayout.OVERLAY_WIDTH);

        // Re-render the cycle button on top of the dim — vanilla widget pass drew it
        // BEFORE the TAIL injection, so without this it would be visually buried.
        if (mercantile$cycleButton != null && mercantile$cycleButton.visible) {
            mercantile$cycleButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        // Close button (top-right).
        MerchantInfoPanelRenderer.drawCloseButton(guiGraphics,
                MerchantScreenLayout.closeButtonX(this.width), MerchantScreenLayout.closeButtonY(this.height),
                mercantile$isPointInCloseButton(mouseX, mouseY));

        guiGraphics.pose().popPose();
    }

    // Shared by the inline panel and the overlay so their bodies never diverge.
    @Unique
    private void mercantile$drawPanelContents(GuiGraphics guiGraphics, MercantileConfig config,
                                              VillagerInfoPanelS2CPayload info,
                                              int panelX, int panelY, int panelWidth) {
        long gameTime = this.minecraft.level == null ? 0L : this.minecraft.level.getGameTime();
        MerchantInfoPanelRenderer.drawContents(guiGraphics, this.font, config, info, this.title,
                ClientMercantileData.getRestockTimer(), mercantile$allOffersFresh(), gameTime,
                panelX, panelY, panelWidth);
    }

    @Unique
    private boolean mercantile$allOffersFresh() {
        var offers = this.menu.getOffers();
        if (offers == null || offers.isEmpty()) return false;
        for (MerchantOffer offer : offers) {
            if (offer.getUses() != 0) return false;
        }
        return true;
    }

    // ---- Tooltips ----

    @Inject(method = "render", at = @At("TAIL"))
    private void mercantile$renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        VillagerInfoPanelS2CPayload info = mercantile$validInfo();
        if (info == null) return;

        MercantileConfig config = mercantile$config();

        if (mercantile$overlayOpen) {
            // Suppress underlying lock-icon tooltip while the overlay is open; the lock
            // icon is covered by the dim and the user's attention is on the overlay.
            if (mercantile$isPointInCloseButton(mouseX, mouseY)) {
                guiGraphics.renderTooltip(this.font,
                        Component.translatable("gui.mercantile.info.overlay.close"),
                        mouseX, mouseY);
            } else if (mercantile$cycleButton != null && mercantile$cycleButton.visible
                    && mercantile$cycleButton.isHovered()) {
                guiGraphics.renderTooltip(this.font,
                        Component.translatable("gui.mercantile.reroll_trades.tooltip", config.tradeCycleEmeraldCost),
                        mouseX, mouseY);
            }
            return;
        }

        if (config.enableProfessionLock) {
            int iconX = this.leftPos + mercantile$lockIconX();
            int iconY = this.topPos + MerchantScreenLayout.LOCK_ICON_Y;
            if (MerchantScreenLayout.pointIn(mouseX, mouseY, iconX, iconY,
                    MerchantScreenLayout.ICON_SIZE, MerchantScreenLayout.ICON_SIZE)) {
                String key = info.professionLocked()
                        ? "gui.mercantile.profession.locked"
                        : "gui.mercantile.profession.unlocked";
                guiGraphics.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
            }
        }

        if (mercantile$infoIconVisible() && mercantile$isPointInInfoIcon(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font,
                    Component.translatable("gui.mercantile.info.button.tooltip"),
                    mouseX, mouseY);
        }

        if (mercantile$cycleButton != null && mercantile$cycleButton.visible && mercantile$cycleButton.isHovered()) {
            guiGraphics.renderTooltip(this.font,
                    Component.translatable("gui.mercantile.reroll_trades.tooltip", config.tradeCycleEmeraldCost),
                    mouseX, mouseY);
        }
    }

    // ---- Geometry helpers (delegate to MerchantScreenLayout) ----

    @Unique
    private int mercantile$lockIconX() {
        return MerchantScreenLayout.lockIconX(this.font.width(mercantile$getTitleComponent()), this.imageWidth);
    }

    @Unique
    private int mercantile$infoIconX() {
        return MerchantScreenLayout.infoIconX(this.font.width(mercantile$getTitleComponent()),
                this.imageWidth, mercantile$config().enableProfessionLock);
    }

    @Unique
    private boolean mercantile$infoIconVisible() {
        return mercantile$config().enableInfoPanel
                && mercantile$validInfo() != null
                && !mercantile$panelFits()
                && !mercantile$overlayOpen;
    }

    @Unique
    private boolean mercantile$isPointInInfoIcon(double mouseX, double mouseY) {
        return MerchantScreenLayout.pointIn(mouseX, mouseY,
                this.leftPos + mercantile$infoIconX(), this.topPos + MerchantScreenLayout.LOCK_ICON_Y,
                MerchantScreenLayout.INFO_ICON_SIZE, MerchantScreenLayout.INFO_ICON_SIZE);
    }

    @Unique
    private boolean mercantile$isPointInCloseButton(double mouseX, double mouseY) {
        return MerchantScreenLayout.pointIn(mouseX, mouseY,
                MerchantScreenLayout.closeButtonX(this.width), MerchantScreenLayout.closeButtonY(this.height),
                MerchantScreenLayout.CLOSE_BUTTON_SIZE, MerchantScreenLayout.CLOSE_BUTTON_SIZE);
    }

    @Unique
    private int mercantile$panelX() {
        return MerchantScreenLayout.panelX(this.leftPos);
    }

    @Unique
    private boolean mercantile$panelFits() {
        return MerchantScreenLayout.panelFits(this.leftPos);
    }

    @Unique
    private Component mercantile$getTitleComponent() {
        int level = this.menu.getTraderLevel();
        if (level > 0 && level <= 5 && this.menu.showProgressBar()) {
            return Component.translatable("merchant.title", this.title,
                    Component.translatable("merchant.level." + level));
        }
        return this.title;
    }

    @Unique
    private MercantileConfig mercantile$config() {
        MercantileConfig config = ClientMercantileData.getServerConfig();
        return config == null ? MercantileConfig.get() : config;
    }

    @Unique
    private void mercantile$onCycleClick() {
        VillagerInfoPanelS2CPayload info = mercantile$validInfo();
        if (info == null) return;
        ClientPlayNetworking.send(new CycleTradesC2SPayload(info.villagerEntityId()));
    }

    @Unique
    @Nullable
    private VillagerInfoPanelS2CPayload mercantile$validInfo() {
        return ClientMercantileData.getVillagerInfo();
    }

    // ---- Input handling for the overlay ----
    // Vanilla MerchantScreen overrides mouseClicked/mouseDragged/mouseScrolled itself,
    // so @Override would collide. Use @Inject(HEAD, cancellable) for those three.
    // mouseReleased/keyPressed/charTyped are inherited from Screen and not overridden
    // in vanilla MerchantScreen, so @Override merges them in as fresh overrides.

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mercantile$onMouseClicked(double mouseX, double mouseY, int button,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (mercantile$overlayOpen) {
            if (button == 0 && mercantile$isPointInCloseButton(mouseX, mouseY)) {
                mercantile$overlayOpen = false;
                cir.setReturnValue(true);
                return;
            }
            if (mercantile$cycleButton != null && mercantile$cycleButton.visible
                    && mercantile$cycleButton.active
                    && mercantile$cycleButton.isMouseOver(mouseX, mouseY)) {
                // Dispatch only to the cycle button so the dimmed trade widgets behind
                // the overlay do not also receive the click. The boolean return is
                // intentionally ignored — overlay modality means we always consume.
                mercantile$cycleButton.mouseClicked(mouseX, mouseY, button);
                cir.setReturnValue(true);
                return;
            }
            // Consume any other click so the underlying trade UI does not react.
            cir.setReturnValue(true);
            return;
        }

        if (button == 0 && mercantile$infoIconVisible() && mercantile$isPointInInfoIcon(mouseX, mouseY)) {
            mercantile$overlayOpen = true;
            cir.setReturnValue(true);
            return;
        }

        if (button == 0) {
            int pinIndex = mercantile$pinIndexAt(mouseX, mouseY);
            if (pinIndex >= 0) {
                TradePinsS2CPayload pins = ClientMercantileData.getTradePins();
                if (pins != null) {
                    ClientPlayNetworking.send(new PinTradeC2SPayload(pins.villagerEntityId(), pinIndex));
                }
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void mercantile$onMouseDragged(double mouseX, double mouseY, int button,
                                           double dragX, double dragY,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (mercantile$overlayOpen) cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void mercantile$onMouseScrolled(double mouseX, double mouseY,
                                            double scrollX, double scrollY,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (mercantile$overlayOpen) cir.setReturnValue(true);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (mercantile$overlayOpen) {
            if (mercantile$cycleButton != null && mercantile$cycleButton.visible
                    && mercantile$cycleButton.isMouseOver(mouseX, mouseY)) {
                return super.mouseReleased(mouseX, mouseY, button);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (mercantile$overlayOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                mercantile$overlayOpen = false;
                return true;
            }
            // Swallow other keys (hotbar slot swap, drop, etc.) while the overlay is open.
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (mercantile$overlayOpen) return true;
        return super.charTyped(codePoint, modifiers);
    }
}
