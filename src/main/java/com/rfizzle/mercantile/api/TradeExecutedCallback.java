package com.rfizzle.mercantile.api;

import com.rfizzle.mercantile.Mercantile;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Callback fired when a player completes a trade with a villager or a
 * wandering trader. Part of Mercantile's stable API surface (Concord API
 * Standard v1).
 *
 * <p>Fired <strong>server-side only</strong>, after the merchant has been
 * notified of the trade ({@code AbstractVillager#notifyTrade}) — i.e. after
 * the offer's use count, villager XP, and Mercantile's own bookkeeping
 * (profession lock, trade lock, reputation pulse) have been applied. Both
 * regular villagers and wandering traders complete trades through this path,
 * and a bulk trade (shift-click) fires once per executed trade.
 *
 * <p>A listener that throws is caught, logged once at {@code WARN} naming the
 * listener class, and skipped — it can never break the trade or the listeners
 * registered after it (API-STANDARD §3.1).
 */
@Stable
public interface TradeExecutedCallback {

    /** One-shot gate so a listener that throws on every trade logs its stack trace once. */
    AtomicBoolean LISTENER_FAILURE_LOGGED = new AtomicBoolean(false);

    Event<TradeExecutedCallback> EVENT = EventFactory.createArrayBacked(TradeExecutedCallback.class,
            listeners -> (player, villager, offer) -> {
                for (TradeExecutedCallback listener : listeners) {
                    try {
                        listener.onTradeExecuted(player, villager, offer);
                    } catch (VirtualMachineError e) {
                        throw e; // OOME/SOE: the JVM is gone, not the guest
                    } catch (Throwable t) {
                        // Throwable, not Exception: a listener compiled against an older
                        // signature throws Error (AbstractMethodError, NoClassDefFoundError),
                        // which an Exception catch would let escape and kill the server tick.
                        if (LISTENER_FAILURE_LOGGED.compareAndSet(false, true)) {
                            Mercantile.LOGGER.warn("A TradeExecutedCallback listener {} threw; skipping",
                                    listener.getClass().getName(), t);
                        }
                    }
                }
            });

    /**
     * Called after a trade has completed.
     *
     * @param player   the trading player
     * @param villager the merchant — a {@code Villager} or {@code WanderingTrader}
     * @param offer    the offer that was executed
     */
    void onTradeExecuted(ServerPlayer player, AbstractVillager villager, MerchantOffer offer);
}
