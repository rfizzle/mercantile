package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerProfessionLockMixin {

    @Shadow
    public abstract net.minecraft.world.entity.npc.VillagerData getVillagerData();

    @Inject(method = "setVillagerData", at = @At("HEAD"), cancellable = true)
    private void mercantile$preventProfessionClearing(net.minecraft.world.entity.npc.VillagerData villagerData, CallbackInfo ci) {
        if (!MercantileConfig.get().enableProfessionLock) return;

        net.minecraft.world.entity.npc.VillagerData current = this.getVillagerData();
        if (current.getProfession() != VillagerProfession.NONE
                && villagerData.getProfession() == VillagerProfession.NONE) {
            Villager self = (Villager) (Object) this;
            var data = self.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
            if (data.isProfessionLocked()) {
                ci.cancel();
            }
        }
    }
}
