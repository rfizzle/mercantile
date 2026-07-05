package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.compat.MoodTooltipFormatter;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.CycleTradesC2SPayload;
import com.rfizzle.mercantile.network.PinTradeC2SPayload;
import com.rfizzle.mercantile.network.RestockTimerS2CPayload;
import com.rfizzle.mercantile.network.VillagerInfoPanelS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {

    @Unique
    private static final ResourceLocation LOCKED_SPRITE = Mercantile.id("locked_button");
    @Unique
    private static final ResourceLocation UNLOCKED_SPRITE = Mercantile.id("unlocked_button");
    @Unique
    private static final ResourceLocation INFO_BUTTON_SPRITE = Mercantile.id("info_button");
    @Unique
    private static final ResourceLocation CLOSE_BUTTON_SPRITE = Mercantile.id("close_button");
    @Unique
    private static final int ICON_SIZE = 11;
    @Unique
    private static final int LOCK_ICON_Y = 4;

    @Unique
    private static final int CYCLE_BUTTON_HEIGHT = 20;

    @Unique
    private static final ResourceLocation PIN_SPRITE = Mercantile.id("pin_button");
    @Unique
    private static final ResourceLocation PIN_OFF_SPRITE = Mercantile.id("pin_button_off");
    // Trade rows sit at leftPos+5, topPos+18, 88x20 each, 7 visible (vanilla init()).
    // The pin column lives just right of the scrollbar track (leftPos+94..100), in the
    // blank band before the trade-slot area that starts at leftPos+136.
    @Unique
    private static final int PIN_ICON_X = 101;
    @Unique
    private static final int TRADE_ROW_X = 5;
    @Unique
    private static final int TRADE_ROW_Y = 18;
    @Unique
    private static final int TRADE_ROW_HEIGHT = 20;
    @Unique
    private static final int VISIBLE_TRADE_ROWS = 7;

    @Unique
    private static final int INFO_PANEL_WIDTH = 110;
    @Unique
    private static final int INFO_PANEL_HEIGHT = 160;
    @Unique
    private static final int INFO_PANEL_MARGIN = 4;
    @Unique
    private static final int INFO_PANEL_PAD = 6;
    @Unique
    private static final int INFO_PANEL_BG_COLOR = 0xC0101010;
    @Unique
    private static final int OVERLAY_PANEL_BG_COLOR = 0xFF101010;
    @Unique
    private static final int INFO_PANEL_BORDER_COLOR = 0xFF555555;
    @Unique
    private static final int INFO_PANEL_TEXT_COLOR = 0xFFFFFFFF;
    @Unique
    private static final int INFO_PANEL_DIM_COLOR = 0xFFA0A0A0;
    @Unique
    private static final int XP_BAR_BG_COLOR = 0xFF404040;
    @Unique
    private static final int XP_BAR_FG_COLOR = 0xFF4FC74F;

    @Unique
    private static final int OVERLAY_WIDTH = 130;
    @Unique
    private static final int OVERLAY_HEIGHT = 180;
    @Unique
    private static final int OVERLAY_PAD = 8;
    @Unique
    private static final int OVERLAY_DIM_COLOR = 0xE0000000;
    @Unique
    private static final int CLOSE_BUTTON_SIZE = 11;
    @Unique
    private static final int INFO_ICON_SIZE = 11;
    @Unique
    private static final int INFO_ICON_BG_COLOR = 0xC0303030;
    @Unique
    private static final int INFO_ICON_BORDER_COLOR = 0xFF888888;
    @Unique
    private static final int CLOSE_BUTTON_HOVER_BG_COLOR = 0xC0A03030;

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
        MercantileConfig config = ClientMercantileData.getServerConfig();
        if (config == null) config = MercantileConfig.get();
        if (!config.enableTradeCycling) return;

        int panelX = mercantile$panelX();
        int panelY = this.topPos;
        int buttonX = panelX + INFO_PANEL_PAD;
        int buttonY = panelY + INFO_PANEL_HEIGHT - INFO_PANEL_PAD - CYCLE_BUTTON_HEIGHT;
        int buttonW = INFO_PANEL_WIDTH - 2 * INFO_PANEL_PAD;

        mercantile$cycleButton = Button.builder(
                        Component.translatable("gui.mercantile.reroll_trades"),
                        btn -> mercantile$onCycleClick())
                .bounds(buttonX, buttonY, buttonW, CYCLE_BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(mercantile$cycleButton);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void mercantile$updateCycleButtonState(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (mercantile$cycleButton == null) return;

        MercantileConfig config = ClientMercantileData.getServerConfig();
        if (config == null) config = MercantileConfig.get();
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
            int ox = mercantile$overlayX();
            int oy = mercantile$overlayY();
            int buttonW = OVERLAY_WIDTH - 2 * OVERLAY_PAD;
            mercantile$cycleButton.setX(ox + OVERLAY_PAD);
            mercantile$cycleButton.setY(oy + OVERLAY_HEIGHT - OVERLAY_PAD - CYCLE_BUTTON_HEIGHT);
            mercantile$cycleButton.setWidth(buttonW);
        } else {
            int panelX = mercantile$panelX();
            int panelY = this.topPos;
            int buttonW = INFO_PANEL_WIDTH - 2 * INFO_PANEL_PAD;
            mercantile$cycleButton.setX(panelX + INFO_PANEL_PAD);
            mercantile$cycleButton.setY(panelY + INFO_PANEL_HEIGHT - INFO_PANEL_PAD - CYCLE_BUTTON_HEIGHT);
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

    @Unique
    private boolean mercantile$allOffersFresh() {
        var offers = this.menu.getOffers();
        if (offers == null || offers.isEmpty()) return false;
        for (MerchantOffer offer : offers) {
            if (offer.getUses() != 0) return false;
        }
        return true;
    }

    @Inject(method = "renderLabels", at = @At("TAIL"))
    private void mercantile$renderLockIcon(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        VillagerInfoPanelS2CPayload info = mercantile$validInfo();
        if (info == null) return;
        MercantileConfig config = ClientMercantileData.getServerConfig();
        if (config == null) config = MercantileConfig.get();

        if (config.enableProfessionLock) {
            ResourceLocation sprite = info.professionLocked() ? LOCKED_SPRITE : UNLOCKED_SPRITE;
            guiGraphics.blitSprite(sprite, mercantile$lockIconX(), LOCK_ICON_Y, ICON_SIZE, ICON_SIZE);
        }

        if (mercantile$infoIconVisible()) {
            mercantile$drawInfoIcon(guiGraphics, mercantile$infoIconX(), LOCK_ICON_Y);
        }
    }

    // Returns the lock icon's GUI-local x (origin at leftPos). Y is the constant
    // LOCK_ICON_Y. Single source of truth so the hover hit-test stays aligned
    // with the rendered icon.
    @Unique
    private int mercantile$lockIconX() {
        Component titleComponent = mercantile$getTitleComponent();
        int titleWidth = this.font.width(titleComponent);
        int titleX = 49 + this.imageWidth / 2 - titleWidth / 2;
        return titleX + titleWidth + 3;
    }

    // GUI-local x for the info icon. Sits to the right of the lock icon when the
    // lock icon is shown; otherwise occupies the same slot.
    @Unique
    private int mercantile$infoIconX() {
        MercantileConfig config = ClientMercantileData.getServerConfig();
        if (config == null) config = MercantileConfig.get();
        int base = mercantile$lockIconX();
        if (config.enableProfessionLock) {
            return base + ICON_SIZE + 2;
        }
        return base;
    }

    @Unique
    private boolean mercantile$infoIconVisible() {
        MercantileConfig config = ClientMercantileData.getServerConfig();
        if (config == null) config = MercantileConfig.get();
        return config.enableInfoPanel
                && mercantile$validInfo() != null
                && !mercantile$panelFits()
                && !mercantile$overlayOpen;
    }

    @Unique
    private boolean mercantile$isPointInInfoIcon(double mouseX, double mouseY) {
        int sx = this.leftPos + mercantile$infoIconX();
        int sy = this.topPos + LOCK_ICON_Y;
        return mouseX >= sx && mouseX < sx + INFO_ICON_SIZE
                && mouseY >= sy && mouseY < sy + INFO_ICON_SIZE;
    }

    @Unique
    private int mercantile$overlayX() {
        return (this.width - OVERLAY_WIDTH) / 2;
    }

    @Unique
    private int mercantile$overlayY() {
        return (this.height - OVERLAY_HEIGHT) / 2;
    }

    @Unique
    private int mercantile$closeButtonX() {
        return mercantile$overlayX() + OVERLAY_WIDTH - OVERLAY_PAD / 2 - CLOSE_BUTTON_SIZE;
    }

    @Unique
    private int mercantile$closeButtonY() {
        return mercantile$overlayY() + OVERLAY_PAD / 2;
    }

    @Unique
    private boolean mercantile$isPointInCloseButton(double mouseX, double mouseY) {
        int cx = mercantile$closeButtonX();
        int cy = mercantile$closeButtonY();
        return mouseX >= cx && mouseX < cx + CLOSE_BUTTON_SIZE
                && mouseY >= cy && mouseY < cy + CLOSE_BUTTON_SIZE;
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
        MercantileConfig config = ClientMercantileData.getServerConfig();
        if (config == null) config = MercantileConfig.get();
        return config.enableTradePinning
                && !mercantile$overlayOpen
                && ClientMercantileData.getTradePins() != null;
    }

    @Unique
    private void mercantile$renderTradePins(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!mercantile$pinsActive()) return;
        var pins = ClientMercantileData.getTradePins();
        var offers = this.menu.getOffers();
        if (offers == null || offers.isEmpty()) return;

        int iconX = this.leftPos + PIN_ICON_X;
        int rows = Math.min(VISIBLE_TRADE_ROWS, offers.size() - scrollOff);
        for (int row = 0; row < rows; row++) {
            int offerIndex = row + scrollOff;
            if (offerIndex >= pins.pinnedByIndex().size()) break;
            boolean pinned = pins.pinnedByIndex().get(offerIndex);
            int rowY = this.topPos + TRADE_ROW_Y + row * TRADE_ROW_HEIGHT;
            int iconY = rowY + (TRADE_ROW_HEIGHT - ICON_SIZE) / 2;

            // The unpinned affordance only appears while the pointer is on the row (or the
            // pin column beside it), so seven hollow pins don't clutter an untouched screen.
            boolean rowHovered = mouseX >= this.leftPos + TRADE_ROW_X
                    && mouseX < iconX + ICON_SIZE
                    && mouseY >= rowY && mouseY < rowY + TRADE_ROW_HEIGHT;
            if (pinned) {
                guiGraphics.blitSprite(PIN_SPRITE, iconX, iconY, ICON_SIZE, ICON_SIZE);
            } else if (rowHovered) {
                guiGraphics.blitSprite(PIN_OFF_SPRITE, iconX, iconY, ICON_SIZE, ICON_SIZE);
            }

            if (mouseX >= iconX && mouseX < iconX + ICON_SIZE
                    && mouseY >= iconY && mouseY < iconY + ICON_SIZE) {
                String key = pinned
                        ? "gui.mercantile.pin.tooltip.pinned"
                        : "gui.mercantile.pin.tooltip.unpinned";
                guiGraphics.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
            }
        }
    }

    /** Returns the absolute offer index of the pin icon under the cursor, or -1. */
    @Unique
    private int mercantile$pinIndexAt(double mouseX, double mouseY) {
        if (!mercantile$pinsActive()) return -1;
        var pins = ClientMercantileData.getTradePins();
        var offers = this.menu.getOffers();
        if (offers == null || offers.isEmpty()) return -1;

        int iconX = this.leftPos + PIN_ICON_X;
        if (mouseX < iconX || mouseX >= iconX + ICON_SIZE) return -1;
        int rows = Math.min(VISIBLE_TRADE_ROWS, offers.size() - scrollOff);
        for (int row = 0; row < rows; row++) {
            int rowY = this.topPos + TRADE_ROW_Y + row * TRADE_ROW_HEIGHT;
            int iconY = rowY + (TRADE_ROW_HEIGHT - ICON_SIZE) / 2;
            if (mouseY >= iconY && mouseY < iconY + ICON_SIZE) {
                int offerIndex = row + scrollOff;
                return offerIndex < pins.pinnedByIndex().size() ? offerIndex : -1;
            }
        }
        return -1;
    }

    @Unique
    private void mercantile$renderInfoPanel(GuiGraphics guiGraphics) {
        MercantileConfig config = ClientMercantileData.getServerConfig();
        if (config == null) config = MercantileConfig.get();
        if (!config.enableInfoPanel) return;

        VillagerInfoPanelS2CPayload info = ClientMercantileData.getVillagerInfo();
        if (info == null) return;
        if (!mercantile$panelFits()) return;

        int panelX = mercantile$panelX();
        int panelY = this.topPos;
        mercantile$drawPanelFrame(guiGraphics, panelX, panelY, INFO_PANEL_WIDTH, INFO_PANEL_HEIGHT, INFO_PANEL_BG_COLOR);
        mercantile$drawInfoPanelContents(guiGraphics, config, info, panelX, panelY, INFO_PANEL_WIDTH);
    }

    @Unique
    private void mercantile$renderOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!mercantile$overlayOpen) return;

        MercantileConfig config = ClientMercantileData.getServerConfig();
        if (config == null) config = MercantileConfig.get();
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

        int ox = mercantile$overlayX();
        int oy = mercantile$overlayY();
        mercantile$drawPanelFrame(guiGraphics, ox, oy, OVERLAY_WIDTH, OVERLAY_HEIGHT, OVERLAY_PANEL_BG_COLOR);
        mercantile$drawInfoPanelContents(guiGraphics, config, info, ox, oy, OVERLAY_WIDTH);

        // Re-render the cycle button on top of the dim — vanilla widget pass drew it
        // BEFORE the TAIL injection, so without this it would be visually buried.
        if (mercantile$cycleButton != null && mercantile$cycleButton.visible) {
            mercantile$cycleButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        // Close button (top-right).
        int cx = mercantile$closeButtonX();
        int cy = mercantile$closeButtonY();
        boolean hovered = mercantile$isPointInCloseButton(mouseX, mouseY);
        int bg = hovered ? CLOSE_BUTTON_HOVER_BG_COLOR : INFO_ICON_BG_COLOR;
        guiGraphics.fill(cx, cy, cx + CLOSE_BUTTON_SIZE, cy + CLOSE_BUTTON_SIZE, bg);
        guiGraphics.fill(cx, cy, cx + CLOSE_BUTTON_SIZE, cy + 1, INFO_ICON_BORDER_COLOR);
        guiGraphics.fill(cx, cy + CLOSE_BUTTON_SIZE - 1, cx + CLOSE_BUTTON_SIZE, cy + CLOSE_BUTTON_SIZE, INFO_ICON_BORDER_COLOR);
        guiGraphics.fill(cx, cy, cx + 1, cy + CLOSE_BUTTON_SIZE, INFO_ICON_BORDER_COLOR);
        guiGraphics.fill(cx + CLOSE_BUTTON_SIZE - 1, cy, cx + CLOSE_BUTTON_SIZE, cy + CLOSE_BUTTON_SIZE, INFO_ICON_BORDER_COLOR);
        guiGraphics.blitSprite(CLOSE_BUTTON_SPRITE, cx, cy, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE);

        guiGraphics.pose().popPose();
    }

    @Unique
    private void mercantile$drawInfoIcon(GuiGraphics g, int x, int y) {
        g.blitSprite(INFO_BUTTON_SPRITE, x, y, INFO_ICON_SIZE, INFO_ICON_SIZE);
    }

    @Unique
    private void mercantile$drawPanelFrame(GuiGraphics g, int x, int y, int w, int h, int bgColor) {
        g.fill(x, y, x + w, y + h, bgColor);
        g.fill(x, y, x + w, y + 1, INFO_PANEL_BORDER_COLOR);
        g.fill(x, y + h - 1, x + w, y + h, INFO_PANEL_BORDER_COLOR);
        g.fill(x, y, x + 1, y + h, INFO_PANEL_BORDER_COLOR);
        g.fill(x + w - 1, y, x + w, y + h, INFO_PANEL_BORDER_COLOR);
    }

    @Unique
    private void mercantile$drawInfoPanelContents(GuiGraphics guiGraphics, MercantileConfig config,
                                                  VillagerInfoPanelS2CPayload info,
                                                  int panelX, int panelY, int panelWidth) {
        int contentX = panelX + INFO_PANEL_PAD;
        int contentWidth = panelWidth - 2 * INFO_PANEL_PAD;
        int y = panelY + INFO_PANEL_PAD;

        // Title (villager display name), bold + centered.
        Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
        int titleX = panelX + (panelWidth - this.font.width(title)) / 2;
        guiGraphics.drawString(this.font, title, titleX, y, INFO_PANEL_TEXT_COLOR, false);
        y += this.font.lineHeight + 4;

        // Profession on its own line; level on a second dim line. Split avoids
        // overflow of the 98px content area on long localized names.
        String profession = info.profession();
        if (profession == null || profession.isEmpty() || "none".equals(profession)) {
            guiGraphics.drawString(this.font,
                    Component.translatable("gui.mercantile.info.unemployed"),
                    contentX, y, INFO_PANEL_TEXT_COLOR, false);
            y += this.font.lineHeight + 4;
        } else {
            Component professionName = Component.translatable("entity.minecraft.villager." + profession);
            Component levelName = Component.translatable("merchant.level." + info.level());
            guiGraphics.drawString(this.font, professionName, contentX, y, INFO_PANEL_TEXT_COLOR, false);
            y += this.font.lineHeight + 4;
            guiGraphics.drawString(this.font, levelName, contentX, y, INFO_PANEL_DIM_COLOR, false);
            y += this.font.lineHeight + 4;
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
            guiGraphics.drawString(this.font, xpText, contentX, y, INFO_PANEL_DIM_COLOR, false);
            y += this.font.lineHeight + 4;
        }

        // Reputation tier + score.
        ChatFormatting tierColor = mercantile$colorForTier(info.reputationTier());
        Component tier = Component.translatable(info.reputationTier()).withStyle(tierColor);
        Component repLine = Component.translatable("gui.mercantile.info.reputation", tier, info.reputation());
        guiGraphics.drawString(this.font, repLine, contentX, y, INFO_PANEL_TEXT_COLOR, false);
        y += this.font.lineHeight + 4;

        // Mood tier — empty key means the mood system is disabled server-side.
        if (info.moodTier() != null && !info.moodTier().isEmpty()) {
            Component moodName = Component.translatable(info.moodTier())
                    .withStyle(MoodTooltipFormatter.colorForTier(info.moodTier()));
            Component moodLine = Component.translatable("gui.mercantile.info.mood", moodName);
            guiGraphics.drawString(this.font, moodLine, contentX, y, INFO_PANEL_TEXT_COLOR, false);
            y += this.font.lineHeight + 4;
        }

        // Trade count.
        Component trades = Component.translatable("gui.mercantile.info.trades", info.totalTrades());
        guiGraphics.drawString(this.font, trades, contentX, y, INFO_PANEL_TEXT_COLOR, false);
        y += this.font.lineHeight + 4;

        // Workstation status.
        String wsKey = info.hasWorkstation()
                ? "gui.mercantile.info.workstation.bound"
                : "gui.mercantile.info.workstation.missing";
        ChatFormatting wsColor = info.hasWorkstation() ? ChatFormatting.GREEN : ChatFormatting.RED;
        Component workstation = Component.translatable(wsKey).withStyle(wsColor);
        guiGraphics.drawString(this.font, workstation, contentX, y, INFO_PANEL_TEXT_COLOR, false);
        y += this.font.lineHeight + 4;

        // Restock subsection.
        RestockTimerS2CPayload timer = ClientMercantileData.getRestockTimer();
        if (timer == null || !config.enableRestockIndicator) return;

        if (!timer.hasWorkstation()) {
            guiGraphics.drawString(this.font,
                    Component.translatable("gui.mercantile.restock.no_workstation")
                            .withStyle(ChatFormatting.RED),
                    contentX, y, INFO_PANEL_TEXT_COLOR, false);
            y += this.font.lineHeight + 4;
        } else if (mercantile$allOffersFresh()) {
            guiGraphics.drawString(this.font,
                    Component.translatable("gui.mercantile.restock.fully_stocked")
                            .withStyle(ChatFormatting.GREEN),
                    contentX, y, INFO_PANEL_TEXT_COLOR, false);
            y += this.font.lineHeight + 4;
        } else if (timer.restockCountToday() < timer.maxRestocksToday()) {
            long now = this.minecraft.level == null ? 0L : this.minecraft.level.getGameTime();
            long nextTick = timer.lastRestockGameTime() + timer.restockIntervalTicks();
            long remaining = Math.max(0L, nextTick - now);
            long totalSeconds = remaining / 20L;
            long minutes = totalSeconds / 60L;
            long seconds = totalSeconds % 60L;
            String timeStr = String.format("%d:%02d", minutes, seconds);
            guiGraphics.drawString(this.font,
                    Component.translatable("gui.mercantile.restock.timer", timeStr),
                    contentX, y, INFO_PANEL_DIM_COLOR, false);
            y += this.font.lineHeight + 4;
        }

        // A cap above the vanilla two restocks per day means market day is in effect.
        String countKey = timer.maxRestocksToday() > 2
                ? "gui.mercantile.restock.count.market_day"
                : "gui.mercantile.restock.count";
        guiGraphics.drawString(this.font,
                Component.translatable(countKey,
                        timer.restockCountToday(), timer.maxRestocksToday()),
                contentX, y, INFO_PANEL_DIM_COLOR, false);
    }

    @Unique
    private ChatFormatting mercantile$colorForTier(String tierKey) {
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

    @Inject(method = "render", at = @At("TAIL"))
    private void mercantile$renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        VillagerInfoPanelS2CPayload info = mercantile$validInfo();
        if (info == null) return;

        MercantileConfig config = ClientMercantileData.getServerConfig();
        if (config == null) config = MercantileConfig.get();

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
            int iconY = this.topPos + LOCK_ICON_Y;
            if (mouseX >= iconX && mouseX < iconX + ICON_SIZE
                    && mouseY >= iconY && mouseY < iconY + ICON_SIZE) {
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

    // Returns the panel's left x in screen coords. Only valid when mercantile$panelFits()
    // is true; otherwise the panel would overlap the merchant UI and must be hidden.
    @Unique
    private int mercantile$panelX() {
        return this.leftPos - INFO_PANEL_WIDTH - INFO_PANEL_MARGIN;
    }

    @Unique
    private boolean mercantile$panelFits() {
        return mercantile$panelX() >= 2;
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
                var pins = ClientMercantileData.getTradePins();
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
