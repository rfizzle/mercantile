package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.CycleTradesC2SPayload;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
    private ImageButton mercantile$cycleButton;

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
    private void mercantile$clearRestockOnClose(CallbackInfo ci) {
        ClientMercantileData.setRestockTimer(null);
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
