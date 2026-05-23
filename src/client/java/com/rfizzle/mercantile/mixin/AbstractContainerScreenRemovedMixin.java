package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.client.network.ClientMercantileData;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenRemovedMixin {

    @Inject(method = "removed", at = @At("HEAD"))
    private void mercantile$clearOnClose(CallbackInfo ci) {
        if ((Object) this instanceof MerchantScreen) {
            ClientMercantileData.clearMerchantScreenData();
        }
    }
}
