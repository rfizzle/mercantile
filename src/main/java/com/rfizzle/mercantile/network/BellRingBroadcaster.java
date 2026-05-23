package com.rfizzle.mercantile.network;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.visualization.BellRingService;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public final class BellRingBroadcaster {

    // Slightly above the 48-block ring radius so viewers just outside still see the boundary burst.
    private static final double BROADCAST_RADIUS = 96.0;

    private BellRingBroadcaster() {
    }

    public static void broadcast(ServerLevel level, BlockPos bellPos) {
        if (!MercantileConfig.get().enableBellRadiusVis) return;

        List<UUID> villagerIds = BellRingService.villagersInRange(level, bellPos);
        BellRingS2CPayload payload = new BellRingS2CPayload(bellPos, villagerIds);

        Vec3 center = Vec3.atCenterOf(bellPos);
        for (ServerPlayer player : PlayerLookup.around(level, center, BROADCAST_RADIUS)) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
