package com.rfizzle.mercantile.trade;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.PinnedTrade;
import com.rfizzle.mercantile.data.PlayerData;
import com.rfizzle.mercantile.data.VillagerHeadTextures;
import com.rfizzle.mercantile.network.TradePinsS2CPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-player trade pins: a pin marks one offer of one villager (by
 * {@link OfferIdentityHash}) so the player is told, via an action-bar note, when that offer
 * comes back in stock. Pins live in {@link PlayerData}; this manager owns the toggle
 * validation, the restock fan-out, and the pruning of pins whose villager died or lost the
 * offer.
 */
public final class TradePinManager {

    private TradePinManager() {
    }

    public static void init() {
        // Prune every online player's pins on a villager the moment it dies. Pins held by
        // offline players can't be reached here (their attachment data isn't loaded) and a
        // dead villager never resolves again, so those become "unknown" entries in
        // /mercantile pins — the player clears them with `pins remove <n>` or `pins clear`.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof Villager villager)) return;
            if (villager.level().isClientSide()) return;
            for (ServerPlayer player : villager.getServer().getPlayerList().getPlayers()) {
                PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
                data.removePinnedTradesFor(villager.getUUID());
            }
        });
    }

    /**
     * Toggles the player's pin on the given offer of the villager they are trading with.
     * Caller has already validated the villager resolve/range/trading-player invariants.
     */
    public static void togglePin(ServerPlayer player, Villager villager, int offerIndex) {
        MerchantOffers offers = villager.getOffers();
        if (offerIndex < 0 || offerIndex >= offers.size()) return;

        MerchantOffer offer = offers.get(offerIndex);
        String hash = OfferIdentityHash.compute(offer);
        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);

        if (data.isTradePinned(villager.getUUID(), hash)) {
            data.removePinnedTrade(villager.getUUID(), hash);
        } else {
            int cap = MercantileConfig.get().maxPinnedTradesPerPlayer;
            if (data.getPinnedTrades().size() >= cap) {
                if (player.connection != null) {
                    player.displayClientMessage(Component.translatable(
                            "mercantile.message.pin_cap", cap).withStyle(ChatFormatting.RED), true);
                }
                return;
            }
            data.addPinnedTrade(new PinnedTrade(villager.getUUID(), hash,
                    describeVillager(villager).getString(), summarize(offer).getString()));
        }
        sendPinsTo(player, villager);
    }

    /**
     * Syncs the player's pin state for this villager's offers (index-aligned booleans), and
     * lazily prunes the player's pins that reference offers this villager no longer sells
     * (e.g. removed by trade cycling).
     */
    public static void sendPinsTo(ServerPlayer player, Villager villager) {
        if (player.connection == null) return;

        PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
        MerchantOffers offers = villager.getOffers();

        // Hash ALL offers for the prune check; only the index-aligned payload is capped.
        // Pruning against a capped set would silently delete legitimate pins on offers
        // past MAX_OFFERS (possible with modded professions / injected trades).
        Set<String> currentHashes = new HashSet<>();
        List<Boolean> pinnedByIndex = new ArrayList<>(Math.min(offers.size(), TradePinsS2CPayload.MAX_OFFERS));
        for (int i = 0; i < offers.size(); i++) {
            String hash = OfferIdentityHash.compute(offers.get(i));
            currentHashes.add(hash);
            if (i < TradePinsS2CPayload.MAX_OFFERS) {
                pinnedByIndex.add(data.isTradePinned(villager.getUUID(), hash));
            }
        }

        for (PinnedTrade pin : List.copyOf(data.getPinnedTrades())) {
            if (pin.villagerUuid().equals(villager.getUUID()) && !currentHashes.contains(pin.offerHash())) {
                data.removePinnedTrade(pin.villagerUuid(), pin.offerHash());
            }
        }

        ServerPlayNetworking.send(player, new TradePinsS2CPayload(villager.getId(), pinnedByIndex));
    }

    /**
     * Called after a villager restocked, with the identity hashes of the offers that were out
     * of stock beforehand (i.e. actually replenished). Notifies every online player in the
     * villager's dimension within the configured range whose pin matches a replenished offer.
     */
    public static void onVillagerRestocked(Villager villager, Set<String> replenishedHashes) {
        if (replenishedHashes.isEmpty()) return;
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableTradePinning) return;
        if (villager.getServer() == null) return;

        // Hash once, not per player. Note: two content-identical offers share one hash, so a
        // pin on one marks both — the notification below then fires once per matching row.
        List<MerchantOffer> replenishedOffers = new ArrayList<>();
        List<String> replenishedOfferHashes = new ArrayList<>();
        for (MerchantOffer offer : villager.getOffers()) {
            String hash = OfferIdentityHash.compute(offer);
            if (replenishedHashes.contains(hash)) {
                replenishedOffers.add(offer);
                replenishedOfferHashes.add(hash);
            }
        }
        if (replenishedOffers.isEmpty()) return;

        double rangeSqr = (double) config.pinRestockNotifyRange * config.pinRestockNotifyRange;
        for (ServerPlayer player : villager.getServer().getPlayerList().getPlayers()) {
            if (player.connection == null) continue;
            if (player.level() != villager.level()) continue;
            if (player.distanceToSqr(villager) > rangeSqr) continue;

            PlayerData data = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA);
            for (int i = 0; i < replenishedOffers.size(); i++) {
                if (!data.isTradePinned(villager.getUUID(), replenishedOfferHashes.get(i))) continue;
                player.displayClientMessage(Component.translatable("mercantile.message.trade_restocked",
                                describeVillager(villager), summarize(replenishedOffers.get(i)))
                        .withStyle(ChatFormatting.GREEN), true);
            }
        }
    }

    /** The villager's custom name, or its profession display name when unnamed. */
    public static Component describeVillager(Villager villager) {
        Component custom = villager.getCustomName();
        if (custom != null) return custom;
        return VillagerHeadTextures.getDisplayName(
                BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()));
    }

    /** A compact one-line trade description: "Emerald x3 + Book → Mending I". */
    public static Component summarize(MerchantOffer offer) {
        MutableComponent summary = Component.empty();
        appendStack(summary, offer.getBaseCostA());
        ItemStack costB = offer.getCostB();
        if (!costB.isEmpty()) {
            summary.append(" + ");
            appendStack(summary, costB);
        }
        summary.append(" → ");

        ItemStack result = offer.getResult();
        ItemEnchantments stored = result.is(Items.ENCHANTED_BOOK)
                ? result.get(DataComponents.STORED_ENCHANTMENTS) : null;
        if (stored != null && !stored.isEmpty()) {
            var entry = stored.entrySet().iterator().next();
            summary.append(Enchantment.getFullname(entry.getKey(), entry.getIntValue()));
        } else {
            appendStack(summary, result);
        }
        return summary;
    }

    private static void appendStack(MutableComponent target, ItemStack stack) {
        target.append(stack.getHoverName());
        if (stack.getCount() > 1) {
            target.append(" x" + stack.getCount());
        }
    }
}
