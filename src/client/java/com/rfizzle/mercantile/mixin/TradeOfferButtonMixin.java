package com.rfizzle.mercantile.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.network.DemandPriceS2CPayload;
import com.rfizzle.mercantile.network.VillagerInfoPanelS2CPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "net.minecraft.client.gui.screens.inventory.MerchantScreen$TradeOfferButton")
public abstract class TradeOfferButtonMixin {

    @Shadow
    @Final
    int index;

    // Wraps vanilla's costA item tooltip (first renderTooltip(Font, ItemStack, int, int)
    // in TradeOfferButton.renderToolTip — ordinal=0). When demand transparency is on
    // and a matching DemandPriceS2CPayload is available, append the breakdown lines
    // to the item tooltip so they share a single tooltip box instead of overlapping.
    @WrapOperation(
            method = "renderToolTip",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/gui/GuiGraphics;renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V"))
    private void mercantile$mergePriceBreakdown(GuiGraphics g, Font font, ItemStack stack,
                                                int mx, int my, Operation<Void> original) {
        MercantileConfig config = ClientMercantileData.getServerConfig();
        if (config == null) config = MercantileConfig.get();
        if (!config.enableDemandTransparency) {
            original.call(g, font, stack, mx, my);
            return;
        }

        DemandPriceS2CPayload price = ClientMercantileData.getDemandPrice();
        if (price == null) {
            original.call(g, font, stack, mx, my);
            return;
        }

        VillagerInfoPanelS2CPayload info = ClientMercantileData.getVillagerInfo();
        if (info == null || info.villagerEntityId() != price.villagerEntityId()) {
            original.call(g, font, stack, mx, my);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof MerchantScreen ms)) {
            original.call(g, font, stack, mx, my);
            return;
        }

        int offerIndex = this.index + ((MerchantScreenAccessor) ms).mercantile$getScrollOff();
        List<DemandPriceS2CPayload.PriceComponent> components = price.components();
        if (offerIndex < 0 || offerIndex >= components.size()) {
            original.call(g, font, stack, mx, my);
            return;
        }

        List<Component> merged = new ArrayList<>(Screen.getTooltipFromItem(mc, stack));
        merged.add(Component.empty());
        merged.addAll(mercantile$buildBreakdownLines(components.get(offerIndex)));
        g.renderTooltip(font, merged, stack.getTooltipImage(), mx, my);
    }

    @Unique
    private static List<Component> mercantile$buildBreakdownLines(DemandPriceS2CPayload.PriceComponent c) {
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
        return lines;
    }
}
