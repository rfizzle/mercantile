package com.rfizzle.mercantile.trade.index;

import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public final class TradeIndexDataSource {

    private static final ResourceLocation CROSS_PROFESSION_ID = Mercantile.id("exclusive");
    private static final long DETERMINISTIC_SEED = 0L;

    private static volatile List<TradeIndexEntry> SNAPSHOT = List.of();

    private TradeIndexDataSource() {
    }

    public static void init() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return Mercantile.id("trade_index");
                    }

                    @Override
                    public Collection<ResourceLocation> getFabricDependencies() {
                        return List.of(Mercantile.id("exclusive_trades"));
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        rebuild();
                    }
                }
        );
    }

    public static List<TradeIndexEntry> snapshot() {
        return SNAPSHOT;
    }

    public static int size() {
        return SNAPSHOT.size();
    }

    // @VisibleForTesting
    public static void rebuild() {
        List<TradeIndexEntry> next = new ArrayList<>();
        RandomSource random = new SingleThreadedRandomSource(DETERMINISTIC_SEED);

        for (Map.Entry<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> profEntry
                : VillagerTrades.TRADES.entrySet()) {
            ResourceLocation profId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profEntry.getKey());
            if (profId == null) continue;
            Int2ObjectMap<VillagerTrades.ItemListing[]> levels = profEntry.getValue();
            for (int level = 1; level <= 5; level++) {
                VillagerTrades.ItemListing[] listings = levels.get(level);
                if (listings == null) continue;
                for (VillagerTrades.ItemListing listing : listings) {
                    try {
                        MerchantOffer offer = listing.getOffer(null, random);
                        if (offer == null) continue;
                        next.add(toEntry(profId, level, TradeIndexEntry.Source.VANILLA, offer, OptionalInt.empty()));
                    } catch (Exception e) {
                        Mercantile.LOGGER.debug("Skipping listing {} for {} level {}: {}",
                                listing.getClass().getSimpleName(), profId, level, e.getMessage());
                    }
                }
            }
        }

        Map<String, List<ExclusiveTradesManager.ExclusiveTrade>> profExclusive =
                ExclusiveTradesManager.professionTradesSnapshot();
        for (Map.Entry<String, List<ExclusiveTradesManager.ExclusiveTrade>> entry : profExclusive.entrySet()) {
            ResourceLocation profId = ResourceLocation.fromNamespaceAndPath("minecraft", entry.getKey());
            for (ExclusiveTradesManager.ExclusiveTrade trade : entry.getValue()) {
                MerchantOffer offer = trade.createOffer();
                next.add(toEntry(profId, 0, TradeIndexEntry.Source.EXCLUSIVE_PROFESSION, offer,
                        OptionalInt.of(trade.minScore())));
            }
        }

        List<ExclusiveTradesManager.ExclusiveTrade> crossExclusive =
                ExclusiveTradesManager.crossProfessionTradesSnapshot();
        for (ExclusiveTradesManager.ExclusiveTrade trade : crossExclusive) {
            MerchantOffer offer = trade.createOffer();
            next.add(toEntry(CROSS_PROFESSION_ID, 0, TradeIndexEntry.Source.EXCLUSIVE_CROSS_PROFESSION, offer,
                    OptionalInt.of(trade.minScore())));
        }

        List<TradeIndexEntry> published = List.copyOf(next);
        SNAPSHOT = published;
        Mercantile.LOGGER.info("Built trade index with {} entries", published.size());
    }

    private static TradeIndexEntry toEntry(ResourceLocation profession, int level,
                                           TradeIndexEntry.Source source, MerchantOffer offer,
                                           OptionalInt minScore) {
        ItemStack inputA = offer.getBaseCostA().copy();
        ItemStack inputB = offer.getItemCostB()
                .map(c -> c.itemStack().copy())
                .orElse(ItemStack.EMPTY);
        ItemStack output = offer.getResult().copy();
        return new TradeIndexEntry(
                profession,
                level,
                source,
                inputA,
                inputB,
                output,
                offer.getMaxUses(),
                offer.getXp(),
                offer.getPriceMultiplier(),
                minScore
        );
    }
}
