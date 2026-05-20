package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.reputation.ReputationManager;
import com.rfizzle.mercantile.trade.OfferIdentityHash;
import net.minecraft.server.level.ServerPlayer;
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
    private void mercantile$onTrade(MerchantOffer offer, CallbackInfo ci) {
        if (!((Object) this instanceof Villager villager)) return;

        MercantileConfig config = MercantileConfig.get();

        var villagerData = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);

        if (config.enableProfessionLock && !villagerData.isProfessionLocked()) {
            villagerData.setProfessionLocked(true);
        }

        if (config.enableTradeCycling) {
            villagerData.addLockedTrade(OfferIdentityHash.compute(offer));
        }

        if (config.enableReputation && villager.getTradingPlayer() instanceof ServerPlayer serverPlayer) {
            PlayerData playerData = serverPlayer.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            playerData.incrementTradesWithVillager(villager.getUUID());
            ReputationManager.modifyScore(serverPlayer, config.reputationTradeGain);
        }
    }
}
