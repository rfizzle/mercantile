package com.rfizzle.mercantile.api;

import com.rfizzle.mercantile.Mercantile;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * <p>A listener that throws is caught, logged once at {@code WARN} naming the
 * listener class, and skipped — it can never break the reputation flow or the
 * listeners registered after it (API-STANDARD §3.1).
 */
@Stable
public interface ReputationChangedCallback {

    /** One-shot gate so a listener that throws on every change logs its stack trace once. */
    AtomicBoolean LISTENER_FAILURE_LOGGED = new AtomicBoolean(false);

    Event<ReputationChangedCallback> EVENT = EventFactory.createArrayBacked(ReputationChangedCallback.class,
            listeners -> (player, oldScore, newScore) -> {
                for (ReputationChangedCallback listener : listeners) {
                    try {
                        listener.onReputationChanged(player, oldScore, newScore);
                    } catch (VirtualMachineError e) {
                        throw e; // OOME/SOE: the JVM is gone, not the guest
                    } catch (Throwable t) {
                        // Throwable, not Exception: a listener compiled against an older
                        // signature throws Error (AbstractMethodError, NoClassDefFoundError),
                        // which an Exception catch would let escape and kill the server tick.
                        if (LISTENER_FAILURE_LOGGED.compareAndSet(false, true)) {
                            Mercantile.LOGGER.warn("A ReputationChangedCallback listener {} threw; skipping",
                                    listener.getClass().getName(), t);
                        }
                    }
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
