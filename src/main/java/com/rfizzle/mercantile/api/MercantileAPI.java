package com.rfizzle.mercantile.api;

import com.rfizzle.mercantile.block.SentryGolemTag;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.MercantileVillagerData;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.trade.OfferIdentityHash;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Public, read-only API for Mercantile (Concord API Standard v1).
 *
 * <p>All methods are static accessors over Mercantile's server-side state.
 * Nothing here mutates that state; gameplay reads are authoritative on the
 * server only. The entity/player data attachments backing these accessors are
 * internal — consume this facade, never the attachments.
 *
 * <p>Safe to use as a soft dependency: compile with {@code modCompileOnly}
 * and guard call sites with
 * {@code FabricLoader.getInstance().isModLoaded("mercantile")}.
 */
public final class MercantileAPI {

    private MercantileAPI() {
    }

    /**
     * Get a player's current reputation score with the villager economy.
     * Authoritative, server-side only.
     *
     * <p>Scores are clamped to {@code [-200, 1500]}. Players with no
     * reputation history yet score {@code 0} (NEUTRAL). Saves from very old
     * Mercantile versions are rescaled lazily on their first reputation
     * change; until then this returns the score as persisted.
     *
     * @param player the player
     * @return the player's reputation score
     */
    public static int getReputation(ServerPlayer player) {
        PlayerData data = player.getAttached(MercantileAttachments.PLAYER_DATA);
        return data == null ? 0 : data.getScore();
    }

    /**
     * Get a player's current reputation tier, derived from
     * {@link #getReputation}. Authoritative, server-side only.
     *
     * @param player the player
     * @return the player's reputation tier; {@link ReputationTier#NEUTRAL}
     *         for players with no reputation history
     */
    public static ReputationTier getReputationTier(ServerPlayer player) {
        return ReputationTier.fromScore(getReputation(player));
    }

    /**
     * Check whether an entity is a sentry golem — an iron golem spawned and
     * maintained by a Sentry Pylon (temporary, pylon-bound, despawns when its
     * pylon runs out of fuel). Server-side only.
     *
     * @param entity the entity to check
     * @return true if the entity is a pylon-spawned sentry golem
     */
    public static boolean isSentryGolem(Entity entity) {
        return SentryGolemTag.isSentry(entity);
    }

    /**
     * Check whether a villager's profession is locked. Mercantile locks a
     * villager's profession permanently after its first trade with any player,
     * so the villager can no longer lose its trades by losing its workstation.
     * Server-side only.
     *
     * <p>This is the villager-level lock. Locking of <em>individual trades</em>
     * (which survive trade cycling) is per-offer — see
     * {@link #isTradeLocked(Villager, MerchantOffer)}.
     *
     * @param villager the villager
     * @return true if the villager's profession is locked
     */
    public static boolean isProfessionLocked(Villager villager) {
        MercantileVillagerData data = villager.getAttached(MercantileAttachments.VILLAGER_DATA);
        return data != null && data.isProfessionLocked();
    }

    /**
     * Check whether a specific offer on a villager is locked. Mercantile
     * tracks locked trades per offer, not per villager: an offer locks the
     * first time it is traded, and locked offers are preserved when the
     * villager's trade pool is cycled. Server-side only.
     *
     * <p>Offers are identified by their item/count identity, so an
     * equivalent offer object (same costs, same result) matches the stored
     * lock even if it is not the same instance. Locks written by very old
     * Mercantile versions used a count-less identity; those are matched here
     * too until the villager's stored locks are migrated.
     *
     * @param villager the villager owning the offer
     * @param offer    the offer to check
     * @return true if this offer is locked (survives trade cycling)
     */
    public static boolean isTradeLocked(Villager villager, MerchantOffer offer) {
        MercantileVillagerData data = villager.getAttached(MercantileAttachments.VILLAGER_DATA);
        if (data == null) return false;
        if (data.isTradeLocked(OfferIdentityHash.compute(offer))) return true;
        return !data.isTradesMigrated() && data.isTradeLocked(OfferIdentityHash.computeLegacy(offer));
    }

    // Render-thread only — resolved once on the first ENV=CLIENT call.
    private static boolean hudHandlesResolved;
    private static MethodHandle isHudVisibleHandle;
    private static MethodHandle getHudHeightHandle;

    private static void resolveHudHandles() {
        if (hudHandlesResolved) return;
        hudHandlesResolved = true;
        try {
            Class<?> overlay = Class.forName("com.rfizzle.mercantile.client.hud.ReputationHudOverlay");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            isHudVisibleHandle = lookup.findStatic(overlay, "isHudVisible", MethodType.methodType(boolean.class));
            getHudHeightHandle = lookup.findStatic(overlay, "getHudHeight", MethodType.methodType(int.class));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            isHudVisibleHandle = null;
            getHudHeightHandle = null;
        }
    }

    /**
     * HUD coordination accessor (Concord HUD-STANDARD §6): whether
     * Mercantile's reputation HUD element is currently being drawn. Safe to
     * call unconditionally from common code on either side.
     *
     * <p>Reflection-backed into the client overlay so this class never
     * references client-only code. Documented sentinel: {@code false} on a
     * dedicated server, when the HUD is config-disabled, or when it is
     * currently hidden (F1, open screen, spectator, death screen, no villager
     * in range). Rendering coordination only — never use for gameplay logic.
     *
     * @return true if the reputation HUD element is currently visible
     */
    public static boolean isHudVisible() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            resolveHudHandles();
            if (isHudVisibleHandle == null) return false;
            try {
                return (boolean) isHudVisibleHandle.invokeExact();
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    /**
     * HUD coordination accessor (Concord HUD-STANDARD §6): this element's
     * current height contribution in pixels (element + stacking gap), for
     * lower-priority HUD slots to offset past. Safe to call unconditionally
     * from common code on either side.
     *
     * <p>Reflection-backed into the client overlay. Documented sentinel:
     * {@code 0} on a dedicated server or whenever {@link #isHudVisible} is
     * false; {@code 22} (20px standard element + 2px gap) when visible.
     *
     * @return the element's height contribution in px, or 0 if not visible
     */
    public static int getHudHeight() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            resolveHudHandles();
            if (getHudHeightHandle == null) return 0;
            try {
                return (int) getHudHeightHandle.invokeExact();
            } catch (Throwable t) {
                return 0;
            }
        }
        return 0;
    }
}
