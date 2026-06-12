package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.api.TradeExecutedCallback;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.network.DemandPriceS2CPayload;
import com.rfizzle.mercantile.network.VillagerInfoPanelSync;
import com.rfizzle.mercantile.reputation.ReputationManager;
import com.rfizzle.mercantile.trade.BulkTradeContext;
import com.rfizzle.mercantile.trade.OfferIdentityHash;
import com.rfizzle.mercantile.trade.PriceBreakdownBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
        AbstractVillager self = (AbstractVillager) (Object) this;

        // TradeExecutedCallback (api): fires for villagers AND wandering
        // traders — both complete trades through notifyTrade. Server-side
        // only: the client menu trades against ClientSideMerchant, never an
        // AbstractVillager, but guard anyway for safety.
        if (!self.level().isClientSide()
                && self.getTradingPlayer() instanceof ServerPlayer tradingServerPlayer) {
            try {
                TradeExecutedCallback.EVENT.invoker().onTradeExecuted(tradingServerPlayer, self, offer);
            } catch (Exception e) {
                // Error isolation per Concord API-STANDARD §3: a misbehaving
                // listener must never corrupt the trade.
                Mercantile.LOGGER.warn("TradeExecutedCallback listener threw", e);
            }
        }

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
            if (!BulkTradeContext.isActive()) {
                ReputationManager.tryGainTradeRep(serverPlayer);
            }
        }

        if (config.enableDemandTransparency
                && !BulkTradeContext.isActive()
                && villager.getTradingPlayer() instanceof ServerPlayer tradingPlayer
                && tradingPlayer.connection != null) {
            ServerPlayNetworking.send(tradingPlayer, new DemandPriceS2CPayload(
                    villager.getId(), PriceBreakdownBuilder.buildFor(villager, tradingPlayer)));
        }

        if (!BulkTradeContext.isActive()
                && villager.getTradingPlayer() instanceof ServerPlayer tradingPlayer) {
            VillagerInfoPanelSync.sendTo(tradingPlayer, villager);
        }
    }
}
