package com.rfizzle.mercantile.api;

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
 * <p>A listener that throws is caught and logged by Mercantile; it cannot
 * corrupt the trade, but it may prevent listeners registered after it from
 * seeing that trade.
 */
@Stable
public interface TradeExecutedCallback {

    Event<TradeExecutedCallback> EVENT = EventFactory.createArrayBacked(TradeExecutedCallback.class,
            listeners -> (player, villager, offer) -> {
                for (TradeExecutedCallback listener : listeners) {
                    listener.onTradeExecuted(player, villager, offer);
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
