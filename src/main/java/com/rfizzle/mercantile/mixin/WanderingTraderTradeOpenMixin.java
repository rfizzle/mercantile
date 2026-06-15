package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WanderingTrader.class, priority = 1100)
public abstract class WanderingTraderTradeOpenMixin {

    @Inject(method = "mobInteract", at = @At("HEAD"))
    private void mercantile$stripInjectedWanderingTraderOffers(Player player, InteractionHand hand,
                                                               CallbackInfoReturnable<InteractionResult> cir) {
        WanderingTrader self = (WanderingTrader) (Object) this;
        ExclusiveTradesManager.stripInjectedOffers(self);
    }

    @Inject(method = "mobInteract", at = @At("HEAD"))
    private void mercantile$applyWanderingTraderReputation(Player player, InteractionHand hand,
                                                           CallbackInfoReturnable<InteractionResult> cir) {
        if (!MercantileConfig.get().enableReputation) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.connection == null) return;

        PlayerData data = serverPlayer.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        int score = data.getScore();

        WanderingTrader self = (WanderingTrader) (Object) this;
        ExclusiveTradesManager.injectWanderingTraderOffer(self, score);
    }
}
