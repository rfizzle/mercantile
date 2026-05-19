package com.rfizzle.mercantile.mixin;

import com.rfizzle.mercantile.command.MercantileCommands;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.network.VillagerInfoPanelS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerTradeOpenMixin {

    @Inject(method = "startTrading", at = @At("TAIL"))
    private void mercantile$sendInfoOnTradeOpen(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        Villager self = (Villager) (Object) this;
        net.minecraft.world.entity.npc.VillagerData vd = self.getVillagerData();
        var data = self.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);

        String profession = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(vd.getProfession()).getPath();
        int level = vd.getLevel();
        int xp = self.getVillagerXp();
        int xpToNextLevel = net.minecraft.world.entity.npc.VillagerData.getMaxXpPerLevel(level);
        int reputation = self.getPlayerReputation(player);
        String reputationTier = MercantileCommands.getTierName(reputation);
        int totalTrades = self.getOffers().size();
        boolean hasWorkstation = self.getBrain()
                .getMemory(MemoryModuleType.JOB_SITE).isPresent();

        ServerPlayNetworking.send(serverPlayer, new VillagerInfoPanelS2CPayload(
                self.getId(), profession, level, xp, xpToNextLevel,
                reputation, reputationTier, totalTrades, hasWorkstation,
                data.isProfessionLocked()));
    }
}
