package com.rfizzle.mercantile.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/**
 * Callback fired whenever a player's reputation score actually changes
 * (listeners are not invoked when a change is absorbed by score clamping).
 * Part of Mercantile's stable API surface (Concord API Standard v1).
 *
 * <p>Fired <strong>server-side only</strong>, from the single internal choke
 * point through which every score change flows. Triggers:
 * <ul>
 *   <li>trade reputation gain (every Nth trade pulse, daily caps permitting)</li>
 *   <li>trade-cycle reputation gain</li>
 *   <li>curing a zombie villager</li>
 *   <li>the daily villager-proximity gain</li>
 *   <li>attacking or killing a villager (loss)</li>
 *   <li>bulk-trade reputation gain</li>
 *   <li>{@code /mercantile reputation set|add}</li>
 * </ul>
 *
 * <p>Not fired by the one-shot legacy save-format score migration (a 10x
 * rescale of pre-release saves, not a gameplay change).
 *
 * <p>A listener that throws is caught and logged by Mercantile; it cannot
 * corrupt the reputation flow, but it may prevent listeners registered after
 * it from seeing that change.
 */
@Stable
public interface ReputationChangedCallback {

    Event<ReputationChangedCallback> EVENT = EventFactory.createArrayBacked(ReputationChangedCallback.class,
            listeners -> (player, oldScore, newScore) -> {
                for (ReputationChangedCallback listener : listeners) {
                    listener.onReputationChanged(player, oldScore, newScore);
                }
            });

    /**
     * Called after a player's reputation score has changed.
     *
     * @param player   the player whose reputation changed
     * @param oldScore the score before the change
     * @param newScore the score after the change (clamped; never equal to
     *                 {@code oldScore})
     */
    void onReputationChanged(ServerPlayer player, int oldScore, int newScore);
}
