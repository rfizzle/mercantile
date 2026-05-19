package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.network.VillagerInfoPanelS2CPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
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

    private MerchantScreenMixin(MerchantMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Inject(method = "renderLabels", at = @At("TAIL"))
    private void mercantile$renderLockIcon(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        VillagerInfoPanelS2CPayload info = ClientMercantileData.getVillagerInfo();
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
    private void mercantile$renderLockTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        VillagerInfoPanelS2CPayload info = ClientMercantileData.getVillagerInfo();
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
