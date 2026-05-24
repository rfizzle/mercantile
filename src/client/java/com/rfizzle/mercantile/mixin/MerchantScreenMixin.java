package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.CycleTradesC2SPayload;
import com.rfizzle.mercantile.network.DemandPriceS2CPayload;
import com.rfizzle.mercantile.network.RestockTimerS2CPayload;
import com.rfizzle.mercantile.network.VillagerInfoPanelS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {

    @Unique
    private static final ResourceLocation LOCKED_SPRITE =
            ResourceLocation.withDefaultNamespace("widget/locked_button");
    @Unique
    private static final ResourceLocation UNLOCKED_SPRITE =
            ResourceLocation.withDefaultNamespace("widget/unlocked_button");
    @Unique
    private static final int ICON_SIZE = 11;
    @Unique
    private static final int LOCK_ICON_Y = 4;

    @Unique
    private static final WidgetSprites CYCLE_SPRITES = new WidgetSprites(
            Mercantile.id("widget/cycle_trades"),
            Mercantile.id("widget/cycle_trades_disabled"));

    @Unique
    private static final int CYCLE_BUTTON_SIZE = 18;

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
    private static final int INFO_PANEL_BORDER_COLOR = 0xFF555555;
    @Unique
    private static final int INFO_PANEL_TEXT_COLOR = 0xFFFFFFFF;
    @Unique
    private static final int INFO_PANEL_DIM_COLOR = 0xFFA0A0A0;
    @Unique
    private static final int XP_BAR_BG_COLOR = 0xFF404040;
    @Unique
    private static final int XP_BAR_FG_COLOR = 0xFF4FC74F;

    // Mirrors vanilla MerchantScreen.NUMBER_OF_OFFER_BUTTONS (private). If vanilla
    // expands the visible offer count, update this constant.
    @Unique
    private static final int VISIBLE_TRADE_ROWS = 7;

    // Offer-row geometry, derived from MerchantScreen.render (n = k+5+5,
    // p = (l+16+1)+2). Single source of truth — the hover hit-test and the
    // tooltip exclusion bounds both derive from these.
    @Unique
    private static final int OFFER_SLOT_X_OFFSET = 10;
    @Unique
    private static final int OFFER_SLOT_Y_OFFSET = 19;
    @Unique
    private static final int OFFER_SLOT_SIZE = 16;
    @Unique
    private static final int OFFER_ROW_SPACING = 20;

    @Unique
    private ImageButton mercantile$cycleButton;

    // Screen.init() runs on every window resize, not just on screen open. Guard
    // the one-shot clear so resizing mid-trade does not wipe villagerInfo/restockTimer/
    // demandPrice and blank the info panel until the server's next periodic resend.
    @Unique
    private boolean mercantile$initialized;

    @Shadow
    private int scrollOff;

    private MerchantScreenMixin(MerchantMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void mercantile$clearStaleDataOnOpen(CallbackInfo ci) {
        if (mercantile$initialized) return;
        mercantile$initialized = true;
        ClientMercantileData.clearMerchantScreenData();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mercantile$addCycleButton(CallbackInfo ci) {
        MercantileConfig config = ClientMercantileData.getServerConfig();
        if (config == null) config = MercantileConfig.get();
        if (!config.enableTradeCycling) return;
        if (!mercantile$panelFits()) return;

        int panelX = mercantile$panelX();
        int panelY = this.topPos;
        int buttonX = panelX + (INFO_PANEL_WIDTH - CYCLE_BUTTON_SIZE) / 2;
        int buttonY = panelY + INFO_PANEL_HEIGHT - INFO_PANEL_PAD - CYCLE_BUTTON_SIZE;

        mercantile$cycleButton = new ImageButton(
                buttonX, buttonY,
                CYCLE_BUTTON_SIZE, CYCLE_BUTTON_SIZE,
                CYCLE_SPRITES,
                btn -> mercantile$onCycleClick(),
                Component.translatable("gui.mercantile.cycle_trades"));
        this.addRenderableWidget(mercantile$cycleButton);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void mercantile$updateCycleButtonState(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (mercantile$cycleButton == null) return;

        MercantileConfig config = ClientMercantileData.getServerConfig();
        if (config == null) config = MercantileConfig.get();
        VillagerInfoPanelS2CPayload info = mercantile$validInfo();

        boolean panelVisible = config.enableInfoPanel && info != null && mercantile$panelFits();
        mercantile$cycleButton.visible = panelVisible;
        if (!panelVisible) return;

        boolean enabled = true;

        if (!config.enableTradeCycling) {
            enabled = false;
        } else if (!this.minecraft.player.isCreative()) {
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
        if (!config.enableProfessionLock) return;

        ResourceLocation sprite = info.professionLocked() ? LOCKED_SPRITE : UNLOCKED_SPRITE;
        guiGraphics.blitSprite(sprite, mercantile$lockIconX(), LOCK_ICON_Y, ICON_SIZE, ICON_SIZE);
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

    @Inject(method = "render", at = @At("TAIL"))
    private void mercantile$renderInfoPanelInject(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        mercantile$renderInfoPanel(guiGraphics);
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

        // Background + 1-px border.
        guiGraphics.fill(panelX, panelY, panelX + INFO_PANEL_WIDTH, panelY + INFO_PANEL_HEIGHT, INFO_PANEL_BG_COLOR);
        guiGraphics.fill(panelX, panelY, panelX + INFO_PANEL_WIDTH, panelY + 1, INFO_PANEL_BORDER_COLOR);
        guiGraphics.fill(panelX, panelY + INFO_PANEL_HEIGHT - 1, panelX + INFO_PANEL_WIDTH, panelY + INFO_PANEL_HEIGHT, INFO_PANEL_BORDER_COLOR);
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + INFO_PANEL_HEIGHT, INFO_PANEL_BORDER_COLOR);
        guiGraphics.fill(panelX + INFO_PANEL_WIDTH - 1, panelY, panelX + INFO_PANEL_WIDTH, panelY + INFO_PANEL_HEIGHT, INFO_PANEL_BORDER_COLOR);

        int contentX = panelX + INFO_PANEL_PAD;
        int contentWidth = INFO_PANEL_WIDTH - 2 * INFO_PANEL_PAD;
        int y = panelY + INFO_PANEL_PAD;

        // Title (villager display name), bold + centered.
        Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
        int titleX = panelX + (INFO_PANEL_WIDTH - this.font.width(title)) / 2;
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

        // XP bar (or "Master" at level 5).
        if (info.level() >= 5) {
            Component master = Component.translatable("gui.mercantile.info.master")
                    .withStyle(ChatFormatting.GOLD);
            guiGraphics.drawString(this.font, master, contentX, y, INFO_PANEL_TEXT_COLOR, false);
            y += this.font.lineHeight + 4;
        } else {
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
        } else if (timer.restockCountToday() < 2) {
            long now = this.minecraft.level == null ? 0L : this.minecraft.level.getGameTime();
            long nextTick = timer.lastRestockGameTime() + 2400L;
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

        guiGraphics.drawString(this.font,
                Component.translatable("gui.mercantile.restock.count",
                        timer.restockCountToday(), 2),
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

        if (mercantile$cycleButton != null && mercantile$cycleButton.isHovered()) {
            guiGraphics.renderTooltip(this.font,
                    Component.translatable("gui.mercantile.cycle_trades.tooltip", config.tradeCycleEmeraldCost),
                    mouseX, mouseY);
        }

        mercantile$renderPriceBreakdownTooltip(guiGraphics, mouseX, mouseY);
    }

    @Unique
    private void mercantile$renderPriceBreakdownTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        MercantileConfig config = ClientMercantileData.getServerConfig();
        if (config == null) config = MercantileConfig.get();
        if (!config.enableDemandTransparency) return;

        int slotX = this.leftPos + OFFER_SLOT_X_OFFSET;
        int slotY = this.topPos + OFFER_SLOT_Y_OFFSET;

        // Belt-and-suspenders: no overlap with header band, info panel, or cycle button tooltip.
        if (mouseY < slotY - 1) return;
        if (mouseX < slotX - 1) return;
        if (mercantile$cycleButton != null && mercantile$cycleButton.isHovered()) return;

        DemandPriceS2CPayload price = ClientMercantileData.getDemandPrice();
        if (price == null) return;
        VillagerInfoPanelS2CPayload info = ClientMercantileData.getVillagerInfo();
        if (info == null || info.villagerEntityId() != price.villagerEntityId()) return;

        if (mouseX < slotX || mouseX >= slotX + OFFER_SLOT_SIZE) return;

        List<DemandPriceS2CPayload.PriceComponent> components = price.components();
        for (int i = 0; i < VISIBLE_TRADE_ROWS; i++) {
            int rowY = slotY + i * OFFER_ROW_SPACING;
            int componentIndex = this.scrollOff + i;
            if (componentIndex < 0 || componentIndex >= components.size()) continue;
            if (mouseY < rowY || mouseY >= rowY + OFFER_SLOT_SIZE) continue;

            DemandPriceS2CPayload.PriceComponent c = components.get(componentIndex);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.mercantile.price.base", c.basePrice()));
            if (c.demandAdjust() > 0) {
                lines.add(Component.translatable("gui.mercantile.price.demand", c.demandAdjust())
                        .withStyle(ChatFormatting.RED));
            }
            if (c.reputationModifier() != 0) {
                ChatFormatting color = c.reputationModifier() < 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
                String value = (c.reputationModifier() > 0 ? "+" : "") + c.reputationModifier();
                lines.add(Component.translatable("gui.mercantile.price.reputation", value)
                        .withStyle(color));
            }
            if (c.gossipModifier() != 0) {
                ChatFormatting color = c.gossipModifier() < 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
                String value = (c.gossipModifier() > 0 ? "+" : "") + c.gossipModifier();
                lines.add(Component.translatable("gui.mercantile.price.gossip", value)
                        .withStyle(color));
            }
            if (c.otherAdjust() != 0) {
                ChatFormatting color = c.otherAdjust() < 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
                String value = (c.otherAdjust() > 0 ? "+" : "") + c.otherAdjust();
                lines.add(Component.translatable("gui.mercantile.price.other", value)
                        .withStyle(color));
            }
            lines.add(Component.empty());
            lines.add(Component.translatable("gui.mercantile.price.final", c.finalPrice()));

            guiGraphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            return;
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
}
