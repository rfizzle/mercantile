package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerTradeMixin {

    @Inject(method = "notifyTrade", at = @At("TAIL"))
    private void mercantile$lockProfessionOnTrade(MerchantOffer offer, CallbackInfo ci) {
        if (!MercantileConfig.get().enableProfessionLock) return;
        if (!((Object) this instanceof Villager villager)) return;

        var data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        if (!data.isProfessionLocked()) {
            data.setProfessionLocked(true);
        }
    }
}
