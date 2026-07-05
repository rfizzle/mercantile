package com.rfizzle.mercantile.client.visualization;

import com.rfizzle.mercantile.client.network.ClientMercantileData;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.contract.ContractService;
import com.rfizzle.mercantile.network.ContractTargetS2CPayload;
import com.rfizzle.mercantile.network.RequestContractTargetC2SPayload;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * Glows the payee villager while the local player holds its written delivery contract
 * (issue #86). The client cannot see villager attachments, so it asks the server which nearby
 * villager holds the held contract's id on a 40-tick throttle (the workstation-map pattern) and
 * outlines the answer via {@code EntityIsCurrentlyGlowingMixin} — client-side only, so the glow
 * is visible to the holding player alone, and matched by contract id so it survives the villager
 * pickup/place cycle. All state is tick/render-thread confined.
 */
public final class ContractGlowTracker {

    private static final int REQUEST_INTERVAL_TICKS = 40; // 2 s; matches server CONTRACT_TARGET_COOLDOWN_MS
    /** A target older than this without a refreshed reply stops glowing (server went quiet). */
    private static final long TARGET_FRESH_TICKS = 100;

    private static int targetEntityId = ContractTargetS2CPayload.NONE;
    private static long targetResponseTime = Long.MIN_VALUE;
    private static UUID heldContractId;
    private static int ticksSinceRequest = REQUEST_INTERVAL_TICKS;

    private ContractGlowTracker() {
    }

    public static void tick(Minecraft client) {
        if (client == null) return;
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            clear();
            return;
        }

        UUID holding = isEnabled() ? heldContractId(player) : null;
        if (holding == null) {
            clear();
            return;
        }

        ticksSinceRequest++;
        if (!holding.equals(heldContractId) || ticksSinceRequest >= REQUEST_INTERVAL_TICKS) {
            // A different contract in hand re-requests immediately; the old target is stale.
            if (!holding.equals(heldContractId)) {
                targetEntityId = ContractTargetS2CPayload.NONE;
            }
            heldContractId = holding;
            ticksSinceRequest = 0;
            ClientPlayNetworking.send(new RequestContractTargetC2SPayload(holding));
        }
    }

    /** Network receiver entry point (render thread via {@code context.client().execute}). */
    public static void setTarget(int entityId, long nowTicks) {
        targetEntityId = entityId;
        targetResponseTime = nowTicks;
    }

    /** Whether this villager is the held contract's payee — the glow predicate for the mixin. */
    public static boolean isTarget(Villager villager, long nowTicks) {
        return heldContractId != null
                && targetEntityId != ContractTargetS2CPayload.NONE
                && villager.getId() == targetEntityId
                && nowTicks - targetResponseTime <= TARGET_FRESH_TICKS;
    }

    /** Cheap early-out for the glow mixin's hot path. */
    public static boolean isActive() {
        return heldContractId != null && targetEntityId != ContractTargetS2CPayload.NONE;
    }

    public static void clear() {
        targetEntityId = ContractTargetS2CPayload.NONE;
        targetResponseTime = Long.MIN_VALUE;
        heldContractId = null;
        ticksSinceRequest = REQUEST_INTERVAL_TICKS;
    }

    private static boolean isEnabled() {
        MercantileConfig config = ClientMercantileData.getServerConfig();
        if (config == null) config = MercantileConfig.get();
        return config.enableContracts && config.enableReputation;
    }

    private static UUID heldContractId(LocalPlayer player) {
        UUID main = contractId(player.getMainHandItem());
        return main != null ? main : contractId(player.getOffhandItem());
    }

    private static UUID contractId(ItemStack stack) {
        if (!stack.is(MercantileRegistry.DELIVERY_CONTRACT)) return null;
        Optional<UUID> id = ContractService.readContractId(stack);
        return id.orElse(null);
    }
}
