package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.reputation.ReputationManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.ZombieVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ZombieVillager.class)
public abstract class ZombieVillagerCureMixin {

    @Shadow
    private UUID conversionStarter;

    @Inject(method = "finishConversion", at = @At("TAIL"))
    private void mercantile$onCure(ServerLevel level, CallbackInfo ci) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableReputation) return;
        if (conversionStarter == null) return;

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(conversionStarter);
        if (player == null) return;

        ZombieVillager self = (ZombieVillager) (Object) this;
        UUID villagerUuid = self.getUUID();

        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        if (data.addCuredVillager(villagerUuid)) {
            ReputationManager.gainCureRep(player);
        }
    }
}
