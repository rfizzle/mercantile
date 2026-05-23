package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.reputation.ReputationTier;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.network.DemandPriceS2CPayload;
import com.rfizzle.mercantile.network.RestockTimerS2CPayload;
import com.rfizzle.mercantile.network.VillagerInfoPanelS2CPayload;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager;
import com.rfizzle.mercantile.reputation.ReputationManager;
import com.rfizzle.mercantile.trade.PriceBreakdownBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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
        if (!MercantileConfig.get().enableReputation) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.connection == null) return;

        PlayerData data = serverPlayer.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        int score = data.getScore();

        Villager self = (Villager) (Object) this;

        if (score != 0) {
            for (MerchantOffer offer : self.getOffers()) {
                int basePrice = offer.getBaseCostA().getCount();
                int modifier = ReputationManager.getPriceModifier(score, basePrice);
                if (modifier != 0) {
                    // Intentional absolute set: mod fully owns reputation pricing, superseding vanilla's Hero-of-the-Village discount.
                    offer.setSpecialPriceDiff(modifier);
                }
            }
        }

        ExclusiveTradesManager.injectOffers(self, score);
    }

    @Inject(method = "startTrading", at = @At("TAIL"))
    private void mercantile$sendInfoOnTradeOpen(Player player, CallbackInfo ci) {
        if (!MercantileConfig.get().enableInfoPanel) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.connection == null) return;

        Villager self = (Villager) (Object) this;
        net.minecraft.world.entity.npc.VillagerData vd = self.getVillagerData();
        var villagerData = self.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);

        var professionKey = BuiltInRegistries.VILLAGER_PROFESSION.getKey(vd.getProfession());
        String profession = professionKey == null ? "none" : professionKey.getPath();
        int level = vd.getLevel();
        int xp = self.getVillagerXp();
        int xpToNextLevel = net.minecraft.world.entity.npc.VillagerData.getMaxXpPerLevel(level);

        PlayerData playerData = serverPlayer.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        int reputation = playerData.getScore();
        String reputationTier = ReputationTier.fromScore(reputation).translationKey();

        int totalTrades = playerData.getTradesWithVillager(self.getUUID());
        boolean hasWorkstation = self.getBrain()
                .getMemory(MemoryModuleType.JOB_SITE).isPresent();

        ServerPlayNetworking.send(serverPlayer, new VillagerInfoPanelS2CPayload(
                self.getId(), profession, level, xp, xpToNextLevel,
                reputation, reputationTier, totalTrades, hasWorkstation,
                villagerData.isProfessionLocked()));
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

        ServerPlayNetworking.send(serverPlayer, new RestockTimerS2CPayload(
                self.getId(), lastRestockGameTime, restocksToday, hasWorkstation));
    }
}
