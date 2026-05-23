package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Villager.class)
public abstract class VillagerProfessionLockMixin {

    @Shadow
    public abstract VillagerData getVillagerData();

    @ModifyVariable(method = "setVillagerData", at = @At("HEAD"), argsOnly = true)
    private VillagerData mercantile$preserveLockedProfession(VillagerData incoming) {
        if (!MercantileConfig.get().enableProfessionLock) return incoming;

        VillagerData current = this.getVillagerData();
        if (current.getProfession() != VillagerProfession.NONE
                && incoming.getProfession() == VillagerProfession.NONE) {
            Villager self = (Villager) (Object) this;
            var data = self.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
            if (data.isProfessionLocked()) {
                return incoming.setProfession(current.getProfession());
            }
        }
        return incoming;
    }
}
