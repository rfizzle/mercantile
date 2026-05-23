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
    private static final WidgetSprites CYCLE_SPRITES = new WidgetSprites(
            Mercantile.id("widget/cycle_trades"),
            Mercantile.id("widget/cycle_trades_disabled"));

    @Unique
    private static final int CYCLE_BUTTON_SIZE = 18;

    @Unique
    private static final int INFO_PANEL_WIDTH = 110;
    @Unique
    private static final int INFO_PANEL_HEIGHT = 134;
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

    @Unique
    private ImageButton mercantile$cycleButton;

    @Shadow
    private int scrollOff;

    private MerchantScreenMixin(MerchantMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mercantile$addCycleButton(CallbackInfo ci) {
        MercantileConfig config = ClientMercantileData.getServerConfig();
        if (config == null) config = MercantileConfig.get();
        if (!config.enableTradeCycling) return;

        int buttonX = this.leftPos + 5;
        int buttonY = this.topPos + this.imageHeight + 2;

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

        VillagerInfoPanelS2CPayload info = mercantile$validInfo();
        MercantileConfig config = ClientMercantileData.getServerConfig();
        if (config == null) config = MercantileConfig.get();

        boolean enabled = true;

        if (info == null) {
            enabled = false;
        } else if (!config.enableTradeCycling) {
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

    @Inject(method = "renderLabels", at = @At("TAIL"))
    private void mercantile$renderRestockInfo(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        RestockTimerS2CPayload timer = ClientMercantileData.getRestockTimer();
        if (timer == null) return;
        MercantileConfig config = ClientMercantileData.getServerConfig();
        if (config == null) config = MercantileConfig.get();
        if (!config.enableRestockIndicator) return;

        int x = 5;
        int y = 4 + this.font.lineHeight + 2;

        if (!timer.hasWorkstation()) {
            guiGraphics.drawString(this.font,
                    Component.translatable("gui.mercantile.restock.no_workstation")
                            .withStyle(ChatFormatting.RED),
                    x, y, 0xFFFFFF, false);
            y += this.font.lineHeight + 1;
            guiGraphics.drawString(this.font,
                    Component.translatable("gui.mercantile.restock.count",
                            timer.restockCountToday(), 2),
                    x, y, 0x404040, false);
            return;
        }

        boolean fullyStocked = mercantile$allOffersFresh();
        if (fullyStocked) {
            guiGraphics.drawString(this.font,
                    Component.translatable("gui.mercantile.restock.fully_stocked")
                            .withStyle(ChatFormatting.GREEN),
                    x, y, 0xFFFFFF, false);
            y += this.font.lineHeight + 1;
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
                    x, y, 0x404040, false);
            y += this.font.lineHeight + 1;
        }

        guiGraphics.drawString(this.font,
                Component.translatable("gui.mercantile.restock.count",
                        timer.restockCountToday(), 2),
                x, y, 0x404040, false);
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

    @Inject(method = "removed", at = @At("HEAD"))
    private void mercantile$clearOnClose(CallbackInfo ci) {
        ClientMercantileData.clearMerchantScreenData();
    }

    @Inject(method = "renderLabels", at = @At("TAIL"))
    private void mercantile$renderLockIcon(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        VillagerInfoPanelS2CPayload info = mercantile$validInfo();
        if (info == null) return;

        Component titleComponent = mercantile$getTitleComponent();
        int titleWidth = this.font.width(titleComponent);
        int titleX = 49 + this.imageWidth / 2 - titleWidth / 2;
        int iconX = titleX + titleWidth + 3;
        int iconY = 4;

        ResourceLocation sprite = info.professionLocked() ? LOCKED_SPRITE : UNLOCKED_SPRITE;
        guiGraphics.blitSprite(sprite, iconX, iconY, ICON_SIZE, ICON_SIZE);
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

        int panelX = this.leftPos + this.imageWidth + INFO_PANEL_MARGIN;
        if (panelX + INFO_PANEL_WIDTH + 2 > this.width) {
            // Right edge would clip — clamp inside the screen.
            panelX = Math.max(0, this.width - INFO_PANEL_WIDTH - 2);
        }
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

        // Profession + level.
        String profession = info.profession();
        Component professionLine;
        if (profession == null || profession.isEmpty() || "none".equals(profession)) {
            professionLine = Component.translatable("gui.mercantile.info.unemployed");
        } else {
            Component professionName = Component.translatable("entity.minecraft.villager." + profession);
            Component levelName = Component.translatable("merchant.level." + info.level());
            professionLine = Component.empty().append(professionName).append(" — ").append(levelName);
        }
        guiGraphics.drawString(this.font, professionLine, contentX, y, INFO_PANEL_TEXT_COLOR, false);
        y += this.font.lineHeight + 4;

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

        Component titleComponent = mercantile$getTitleComponent();
        int titleWidth = this.font.width(titleComponent);
        int titleX = this.leftPos + 49 + this.imageWidth / 2 - titleWidth / 2;
        int iconX = titleX + titleWidth + 3;
        int iconY = this.topPos + 4;

        if (mouseX >= iconX && mouseX < iconX + ICON_SIZE
                && mouseY >= iconY && mouseY < iconY + ICON_SIZE) {
            String key = info.professionLocked()
                    ? "gui.mercantile.profession.locked"
                    : "gui.mercantile.profession.unlocked";
            guiGraphics.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
        }

        if (mercantile$cycleButton != null && mercantile$cycleButton.isHovered()) {
            MercantileConfig config = ClientMercantileData.getServerConfig();
            if (config == null) config = MercantileConfig.get();
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

        DemandPriceS2CPayload price = ClientMercantileData.getDemandPrice();
        if (price == null) return;
        VillagerInfoPanelS2CPayload info = ClientMercantileData.getVillagerInfo();
        if (info == null || info.villagerEntityId() != price.villagerEntityId()) return;

        // Cost A icon is rendered at (leftPos + 10, topPos + 19) with 20px row spacing
        // (see MerchantScreen.render: n = k+5+5, p = (l+16+1)+2).
        int slotX = this.leftPos + 10;
        int slotY = this.topPos + 19;
        if (mouseX < slotX || mouseX >= slotX + 16) return;

        List<DemandPriceS2CPayload.PriceComponent> components = price.components();
        for (int i = 0; i < VISIBLE_TRADE_ROWS; i++) {
            int rowY = slotY + i * 20;
            int componentIndex = this.scrollOff + i;
            if (componentIndex < 0 || componentIndex >= components.size()) continue;
            if (mouseY < rowY || mouseY >= rowY + 16) continue;

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
