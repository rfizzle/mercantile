package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.market.MarketDayManager;
import com.rfizzle.mercantile.mixin.VillagerRestockAccessor;
import com.rfizzle.mercantile.mood.MoodManager;
import com.rfizzle.mercantile.mood.MoodMath;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

/**
 * Builds and sends the {@link RestockTimerS2CPayload} for a villager to one
 * player. Kept as a sibling of {@link VillagerInfoPanelSync} so both panel
 * payloads are constructed the same way and, crucially, resent together on
 * every trigger: their shared {@code hasWorkstation} flag is read from the same
 * {@code JOB_SITE} memory in the same tick, so the two can never disagree on the
 * client (which picks its workstation readout from whichever payload a given
 * render path holds).
 */
public final class RestockTimerSync {

    private RestockTimerSync() {}

    public static void sendTo(ServerPlayer player, Villager villager) {
        if (!MercantileConfig.get().enableRestockIndicator) return;
        if (player.connection == null) return;

        VillagerRestockAccessor accessor = (VillagerRestockAccessor) villager;
        long lastRestockGameTime = accessor.mercantile$getLastRestockGameTime();
        int restocksToday = accessor.mercantile$getNumberOfRestocksToday();
        boolean hasWorkstation = villager.getBrain()
                .getMemory(MemoryModuleType.JOB_SITE).isPresent();
        int restockIntervalTicks = (int) MoodManager.restockIntervalTicks(
                villager, MoodMath.BASE_RESTOCK_INTERVAL_TICKS);

        ServerPlayNetworking.send(player, new RestockTimerS2CPayload(
                villager.getId(), lastRestockGameTime, restocksToday, hasWorkstation,
                restockIntervalTicks, MarketDayManager.maxRestocksToday(villager)));
    }
}
