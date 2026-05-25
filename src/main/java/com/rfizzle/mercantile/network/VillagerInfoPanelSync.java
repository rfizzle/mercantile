package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.reputation.ReputationTier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;

public final class VillagerInfoPanelSync {

    private VillagerInfoPanelSync() {}

    public static void sendTo(ServerPlayer player, Villager villager) {
        if (!MercantileConfig.get().enableInfoPanel) return;
        if (player.connection == null) return;

        VillagerData vd = villager.getVillagerData();
        var villagerData = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);

        var professionKey = BuiltInRegistries.VILLAGER_PROFESSION.getKey(vd.getProfession());
        String profession = professionKey == null ? "none" : professionKey.getPath();
        int level = vd.getLevel();
        int xp = villager.getVillagerXp();
        int xpToNextLevel = VillagerData.getMaxXpPerLevel(level);

        PlayerData playerData = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        int reputation = playerData.getScore();
        String reputationTier = ReputationTier.fromScore(reputation).translationKey();

        int totalTrades = playerData.getTradesWithVillager(villager.getUUID());
        boolean hasWorkstation = villager.getBrain()
                .getMemory(MemoryModuleType.JOB_SITE).isPresent();

        ServerPlayNetworking.send(player, new VillagerInfoPanelS2CPayload(
                villager.getId(), profession, level, xp, xpToNextLevel,
                reputation, reputationTier, totalTrades, hasWorkstation,
                villagerData.isProfessionLocked()));
    }
}
