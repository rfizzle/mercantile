package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.api.TradeExecutedCallback;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.network.DemandPriceS2CPayload;
import com.rfizzle.mercantile.network.RestockTimerSync;
import com.rfizzle.mercantile.network.VillagerInfoPanelSync;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager;
import com.rfizzle.mercantile.reputation.ReputationManager;
import com.rfizzle.mercantile.trade.BulkTradeContext;
import com.rfizzle.mercantile.trade.OfferIdentityHash;
import com.rfizzle.mercantile.trade.PriceBreakdownBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerTradeMixin {

    @Unique
    private List<Map.Entry<Integer, MerchantOffer>> mercantile$detachedForSave = List.of();

    /** Trade session ended (screen closed, player left): drop the reputation-gated offers injected for it. */
    @Inject(method = "setTradingPlayer", at = @At("TAIL"))
    private void mercantile$stripInjectedOnSessionEnd(@Nullable Player player, CallbackInfo ci) {
        if (player != null) return;
        AbstractVillager self = (AbstractVillager) (Object) this;
        if (self.level().isClientSide()) return;
        ExclusiveTradesManager.stripInjectedOffers(self);
    }

    /** Save guard: a save landing mid-session must never write injected offers into the persisted offer list. */
    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    private void mercantile$detachInjectedForSave(CompoundTag tag, CallbackInfo ci) {
        mercantile$detachedForSave = ExclusiveTradesManager.detachInjected((AbstractVillager) (Object) this);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void mercantile$reattachInjectedAfterSave(CompoundTag tag, CallbackInfo ci) {
        ExclusiveTradesManager.reattachInjected((AbstractVillager) (Object) this, mercantile$detachedForSave);
        mercantile$detachedForSave = List.of();
    }

    @Inject(method = "notifyTrade", at = @At("TAIL"))
    private void mercantile$onTrade(MerchantOffer offer, CallbackInfo ci) {
        AbstractVillager self = (AbstractVillager) (Object) this;

        // TradeExecutedCallback (api): fires for villagers AND wandering
        // traders — both complete trades through notifyTrade. Server-side
        // only: the client menu trades against ClientSideMerchant, never an
        // AbstractVillager, but guard anyway for safety.
        if (!self.level().isClientSide()
                && self.getTradingPlayer() instanceof ServerPlayer tradingServerPlayer) {
            // Listener isolation lives inside the callback's invoker (API-STANDARD §3.1).
            TradeExecutedCallback.EVENT.invoker().onTradeExecuted(tradingServerPlayer, self, offer);
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
            // Resend both panel payloads together so their shared hasWorkstation flag stays
            // coherent — a job-site change between screen-open and this trade must not leave
            // the restock payload reporting a stale binding against a fresh info payload.
            VillagerInfoPanelSync.sendTo(tradingPlayer, villager);
            RestockTimerSync.sendTo(tradingPlayer, villager);
        }
    }
}
