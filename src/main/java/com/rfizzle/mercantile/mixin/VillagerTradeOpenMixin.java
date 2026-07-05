package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.market.MarketDayManager;
import com.rfizzle.mercantile.memorial.FearManager;
import com.rfizzle.mercantile.memorial.FearMath;
import com.rfizzle.mercantile.mood.MoodManager;
import com.rfizzle.mercantile.mood.MoodMath;
import com.rfizzle.mercantile.network.DemandPriceS2CPayload;
import com.rfizzle.mercantile.network.RestockTimerS2CPayload;
import com.rfizzle.mercantile.network.VillagerInfoPanelSync;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager;
import com.rfizzle.mercantile.reputation.ReputationManager;
import com.rfizzle.mercantile.trade.PriceBreakdownBuilder;
import com.rfizzle.mercantile.trade.TradePinManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerTradeOpenMixin {

    @Inject(method = "startTrading", at = @At("HEAD"), cancellable = true)
    private void mercantile$denyReviledTrading(Player player, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        ExclusiveTradesManager.stripInjectedOffers(self);

        if (!MercantileConfig.get().enableReputation) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.connection == null) return;

        PlayerData data = serverPlayer.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        if (!ReputationManager.isReviled(data.getScore())) return;

        serverPlayer.displayClientMessage(
                Component.translatable("mercantile.trade.denied.reviled")
                        .withStyle(ChatFormatting.RED), true);

        if (self.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    self.getX(), self.getEyeY() + 0.5, self.getZ(),
                    3, 0.3, 0.3, 0.3, 0.0);
        }

        self.playSound(SoundEvents.VILLAGER_NO, 1.0f, self.getVoicePitch());
        ci.cancel();
    }

    @Inject(method = "startTrading",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/npc/Villager;openTradingScreen(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/network/chat/Component;I)V"))
    private void mercantile$applyReputationEffects(Player player, CallbackInfo ci) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableReputation && !config.enableMood && !config.enableMarketDay
                && !config.enableFearMarkup) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.connection == null) return;

        Villager self = (Villager) (Object) this;

        int score = 0;
        if (config.enableReputation) {
            PlayerData data = serverPlayer.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            score = data.getScore();
        }

        double fearFraction = FearManager.fearFraction(self, serverPlayer, config);
        for (MerchantOffer offer : self.getOffers()) {
            int basePrice = offer.getBaseCostA().getCount();
            int reputationModifier = score != 0 ? ReputationManager.getPriceModifier(score, basePrice) : 0;
            int moodModifier = MoodManager.priceModifier(self, basePrice, config);
            int marketDayModifier = MarketDayManager.priceModifier(self, basePrice, config);
            int rawFear = FearManager.priceModifier(basePrice, fearFraction, config);
            // Vanilla clamps the charged cost at the item's max stack size; cap fear at the
            // remaining headroom so the breakdown never reports fear that isn't charged.
            int demandAdjust = Math.max(0, Mth.floor(basePrice
                    * ((MerchantOfferDemandAccessor) offer).mercantile$getDemand()
                    * offer.getPriceMultiplier()));
            if (reputationModifier != 0) {
                // Intentional absolute set: mod fully owns reputation pricing, superseding
                // vanilla's gossip and Hero-of-the-Village discounts. Mood, the market-day
                // discount, and the fear markup stack on top.
                int fearModifier = FearMath.capToHeadroom(rawFear, offer.getBaseCostA().getMaxStackSize(),
                        basePrice, demandAdjust, reputationModifier + moodModifier + marketDayModifier);
                offer.setSpecialPriceDiff(reputationModifier + moodModifier + marketDayModifier + fearModifier);
            } else {
                // No reputation modifier: mood, market day, and fear stack on the vanilla
                // gossip/HotV special price.
                int vanillaDiff = offer.getSpecialPriceDiff();
                int fearModifier = FearMath.capToHeadroom(rawFear, offer.getBaseCostA().getMaxStackSize(),
                        basePrice, demandAdjust, vanillaDiff + moodModifier + marketDayModifier);
                if (moodModifier != 0 || marketDayModifier != 0 || fearModifier != 0) {
                    offer.setSpecialPriceDiff(vanillaDiff + moodModifier + marketDayModifier + fearModifier);
                }
            }
        }

        if (config.enableReputation) {
            ExclusiveTradesManager.injectOffers(self, score);
        }
    }

    @Inject(method = "startTrading", at = @At("TAIL"))
    private void mercantile$sendInfoOnTradeOpen(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        VillagerInfoPanelSync.sendTo(serverPlayer, (Villager) (Object) this);
    }

    @Inject(method = "startTrading", at = @At("TAIL"))
    private void mercantile$sendDemandPriceOnTradeOpen(Player player, CallbackInfo ci) {
        if (!MercantileConfig.get().enableDemandTransparency) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.connection == null) return;

        Villager self = (Villager) (Object) this;
        ServerPlayNetworking.send(serverPlayer, new DemandPriceS2CPayload(
                self.getId(), PriceBreakdownBuilder.buildFor(self, serverPlayer)));
    }

    @Inject(method = "startTrading", at = @At("TAIL"))
    private void mercantile$sendTradePinsOnTradeOpen(Player player, CallbackInfo ci) {
        if (!MercantileConfig.get().enableTradePinning) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.connection == null) return;

        TradePinManager.sendPinsTo(serverPlayer, (Villager) (Object) this);
    }

    @Inject(method = "startTrading", at = @At("TAIL"))
    private void mercantile$sendRestockOnTradeOpen(Player player, CallbackInfo ci) {
        if (!MercantileConfig.get().enableRestockIndicator) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.connection == null) return;

        Villager self = (Villager) (Object) this;
        VillagerRestockAccessor accessor = (VillagerRestockAccessor) self;
        long lastRestockGameTime = accessor.mercantile$getLastRestockGameTime();
        int restocksToday = accessor.mercantile$getNumberOfRestocksToday();
        boolean hasWorkstation = self.getBrain()
                .getMemory(MemoryModuleType.JOB_SITE).isPresent();

        int restockIntervalTicks = (int) MoodManager.restockIntervalTicks(
                self, MoodMath.BASE_RESTOCK_INTERVAL_TICKS);

        ServerPlayNetworking.send(serverPlayer, new RestockTimerS2CPayload(
                self.getId(), lastRestockGameTime, restocksToday, hasWorkstation, restockIntervalTicks,
                MarketDayManager.maxRestocksToday(self)));
    }
}
