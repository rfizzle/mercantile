package com.rfizzle.mercantile.trade;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.MercantileVillagerData;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager;
import com.rfizzle.mercantile.reputation.ReputationManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import com.rfizzle.mercantile.particle.MercantileParticles;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TradeCycleManager {

    private static final int CANDIDATE_REROLL_ATTEMPTS = 10;

    private TradeCycleManager() {
    }

    public static boolean canCycle(ServerPlayer player, Villager villager) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableTradeCycling) return false;

        MercantileVillagerData villagerData = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        MerchantOffers offers = villager.getOffers();
        villagerData.migrateLockedTrades(offers);

        int playerScore = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA).getScore();
        evictInaccessibleExclusiveLocks(villager, villagerData, playerScore);

        boolean hasUnlocked = false;
        for (MerchantOffer offer : offers) {
            if (!villagerData.isTradeLocked(OfferIdentityHash.compute(offer))) {
                hasUnlocked = true;
                break;
            }
        }
        if (!hasUnlocked) return false;

        if (!player.isCreative()) {
            return countEmeralds(player) >= config.tradeCycleEmeraldCost;
        }
        return true;
    }

    public static boolean cycle(ServerPlayer player, Villager villager) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableTradeCycling) return false;

        MercantileVillagerData villagerData = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        MerchantOffers offers = villager.getOffers();
        villagerData.migrateLockedTrades(offers);

        ExclusiveTradesManager.stripInjectedOffers(villager);

        int playerScore = player.getAttachedOrCreate(MercantileAttachments.PLAYER_DATA).getScore();
        evictInaccessibleExclusiveLocks(villager, villagerData, playerScore);

        List<MerchantOffer> locked = new ArrayList<>();
        Set<String> lockedHashes = new HashSet<>();
        for (MerchantOffer offer : offers) {
            String hash = OfferIdentityHash.compute(offer);
            if (villagerData.isTradeLocked(hash)) {
                locked.add(offer);
                lockedHashes.add(hash);
            }
        }

        if (locked.size() == offers.size()) {
            ExclusiveTradesManager.injectOffers(villager, playerScore);
            syncOffers(player, villager);
            return false;
        }

        if (!player.isCreative()) {
            int cost = config.tradeCycleEmeraldCost;
            if (countEmeralds(player) < cost) {
                ExclusiveTradesManager.injectOffers(villager, playerScore);
                syncOffers(player, villager);
                return false;
            }
            removeEmeralds(player, cost);
        }

        int originalSize = offers.size();
        offers.clear();
        offers.addAll(locked);

        Int2ObjectMap<VillagerTrades.ItemListing[]> tradeMap =
                VillagerTrades.TRADES.get(villager.getVillagerData().getProfession());

        if (tradeMap != null) {
            int level = villager.getVillagerData().getLevel();
            int slotsToFill = originalSize - locked.size();

            List<MerchantOffer> candidates = new ArrayList<>();
            Set<String> seenHashes = new HashSet<>(lockedHashes);

            for (int attempt = 0; attempt < CANDIDATE_REROLL_ATTEMPTS && candidates.size() < slotsToFill; attempt++) {
                for (int lvl = 1; lvl <= level; lvl++) {
                    VillagerTrades.ItemListing[] listings = tradeMap.get(lvl);
                    if (listings == null || listings.length == 0) continue;
                    for (VillagerTrades.ItemListing listing : listings) {
                        MerchantOffer offer = listing.getOffer(villager, villager.getRandom());
                        if (offer == null) continue;
                        String hash = OfferIdentityHash.compute(offer);
                        if (seenHashes.add(hash)) {
                            candidates.add(offer);
                        }
                    }
                }
            }

            while (candidates.size() > slotsToFill) {
                candidates.remove(villager.getRandom().nextInt(candidates.size()));
            }
            offers.addAll(candidates);
        }

        if (config.enableReputation) {
            ReputationManager.modifyScore(player, config.reputationCycleGain);
        }

        villager.playSound(SoundEvents.VILLAGER_YES, 1.0f, villager.getVoicePitch());

        if (villager.level() instanceof ServerLevel serverLevel) {
            double py = villager.getY() + villager.getBbHeight() * 0.5;
            serverLevel.sendParticles(MercantileParticles.CYCLE_GLINT,
                    villager.getX(), py, villager.getZ(),
                    15, 0.4, 0.6, 0.4, 0.02);
        }

        ExclusiveTradesManager.injectOffers(villager, playerScore);
        syncOffers(player, villager);

        return true;
    }

    private static void evictInaccessibleExclusiveLocks(Villager villager, MercantileVillagerData villagerData, int playerScore) {
        Set<String> inaccessible = ExclusiveTradesManager.getInaccessibleExclusiveHashes(villager, playerScore);
        if (inaccessible.isEmpty()) return;
        for (String hash : inaccessible) {
            villagerData.removeLockedTrade(hash);
        }
    }

    private static void syncOffers(ServerPlayer player, Villager villager) {
        player.connection.send(new ClientboundMerchantOffersPacket(
                player.containerMenu.containerId,
                villager.getOffers(),
                villager.getVillagerData().getLevel(),
                villager.getVillagerXp(),
                villager.showProgressBar(),
                villager.canRestock()
        ));
    }

    private static int countEmeralds(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.EMERALD)) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(Items.EMERALD)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void removeEmeralds(ServerPlayer player, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) break;
            if (stack.is(Items.EMERALD)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (remaining <= 0) break;
            if (stack.is(Items.EMERALD)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }
}
