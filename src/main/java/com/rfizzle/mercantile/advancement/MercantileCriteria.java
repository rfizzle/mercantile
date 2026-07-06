package com.rfizzle.mercantile.advancement;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.api.ReputationChangedCallback;
import com.rfizzle.mercantile.api.ReputationTier;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Custom advancement triggers that power the "Mercantile" tutorial tree. Each
 * gesture the mod teaches has a plain {@link PlayerTrigger} instance registered
 * under a mod id; the matching advancement JSON in
 * {@code data/mercantile/advancement/mercantile/} references that id, and the
 * gesture's existing server-side success path calls {@code trigger(player)}.
 *
 * <p>Because every grant lives inside an already feature-gated success branch,
 * a disabled feature simply never fires its trigger — no extra toggle checks
 * are needed here.
 */
public final class MercantileCriteria {

    /** Sneak + right-click a villager with an empty hand to pick it up. */
    public static final PlayerTrigger PICKUP_VILLAGER = register("pickup_villager");
    /** Right-click a villager with an emerald to make it follow. */
    public static final PlayerTrigger FOLLOW_VILLAGER = register("follow_villager");
    /** Have a villager accept a tossed gift. */
    public static final PlayerTrigger GIFT_ACCEPTED = register("gift_accepted");
    /** Sneak + right-click a villager with paper to accept a contract. */
    public static final PlayerTrigger CONTRACT_ACCEPTED = register("contract_accepted");
    /** Right-click a Sentry Pylon with an iron block to fuel it. */
    public static final PlayerTrigger PYLON_FUELED = register("pylon_fueled");
    /** Sneak + right-click an unemployed villager holding a workstation block. */
    public static final PlayerTrigger WORK_ORDER_ASSIGNED = register("work_order_assigned");
    /** Feed a golden apple to a nitwit to begin rehabilitation. */
    public static final PlayerTrigger NITWIT_REHAB_STARTED = register("nitwit_rehab_started");
    /** Reach the Trusted reputation tier (score >= 300). */
    public static final PlayerTrigger TRUSTED_TIER = register("trusted_tier");

    private MercantileCriteria() {
    }

    /**
     * Force class-load (so the trigger fields register) and wire the Trusted
     * advancement to the reputation score choke point.
     */
    public static void init() {
        ReputationChangedCallback.EVENT.register((player, oldScore, newScore) -> {
            int threshold = ReputationTier.TRUSTED.minScore();
            if (oldScore < threshold && newScore >= threshold) {
                TRUSTED_TIER.trigger(player);
            }
        });
    }

    private static PlayerTrigger register(String path) {
        return Registry.register(BuiltInRegistries.TRIGGER_TYPES, Mercantile.id(path), new PlayerTrigger());
    }
}
