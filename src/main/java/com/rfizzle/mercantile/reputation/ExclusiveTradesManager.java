package com.rfizzle.mercantile.reputation;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.trade.OfferIdentityHash;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ExclusiveTradesManager {

    private static final Gson GSON = new Gson();
    private static final String DATA_PATH = "exclusive_trades";
    private static final String CROSS_PROFESSION_KEY = "_mercantile";

    private static volatile Map<String, List<ExclusiveTrade>> PROFESSION_TRADES = Map.of();
    private static volatile List<ExclusiveTrade> CROSS_PROFESSION_TRADES = List.of();

    private static final Map<Villager, List<MerchantOffer>> INJECTED_OFFERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ExclusiveTradesManager() {
    }

    public static void init() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return Mercantile.id("exclusive_trades");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        loadTrades(manager);
                    }
                }
        );
    }

    public static void stripInjectedOffers(Villager villager) {
        List<MerchantOffer> previouslyInjected = INJECTED_OFFERS.remove(villager);
        if (previouslyInjected == null || previouslyInjected.isEmpty()) return;

        var offers = villager.getOffers();
        for (MerchantOffer injected : previouslyInjected) {
            offers.remove(injected);
        }
    }

    public static void injectOffers(Villager villager, int playerScore) {
        if (!MercantileConfig.get().enableReputation) return;

        Map<String, List<ExclusiveTrade>> profTrades = PROFESSION_TRADES;
        List<ExclusiveTrade> crossTrades = CROSS_PROFESSION_TRADES;

        String profession = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession()).getPath();

        List<MerchantOffer> toInject = new ArrayList<>();

        List<ExclusiveTrade> professionTrades = profTrades.get(profession);
        if (professionTrades != null) {
            for (ExclusiveTrade trade : professionTrades) {
                if (playerScore >= trade.minScore()) {
                    toInject.add(trade.createOffer());
                }
            }
        }

        for (ExclusiveTrade trade : crossTrades) {
            if (playerScore >= trade.minScore()) {
                toInject.add(trade.createOffer());
            }
        }

        if (toInject.isEmpty()) return;

        villager.getOffers().addAll(toInject);
        INJECTED_OFFERS.put(villager, toInject);
    }

    /**
     * Computes the set of identity hashes for every exclusive trade available to {@code villager}'s
     * profession plus all cross-profession trades that the player CANNOT currently access
     * ({@code playerScore < trade.minScore()}). Used to evict stale lock-hash entries when the
     * player's reputation drops below an exclusive trade's threshold.
     */
    public static Set<String> getInaccessibleExclusiveHashes(Villager villager, int playerScore) {
        Map<String, List<ExclusiveTrade>> profTrades = PROFESSION_TRADES;
        List<ExclusiveTrade> crossTrades = CROSS_PROFESSION_TRADES;

        String profession = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession()).getPath();

        Set<String> inaccessible = new HashSet<>();

        List<ExclusiveTrade> professionTrades = profTrades.get(profession);
        if (professionTrades != null) {
            for (ExclusiveTrade trade : professionTrades) {
                if (playerScore < trade.minScore()) {
                    inaccessible.add(OfferIdentityHash.compute(trade.createOffer()));
                }
            }
        }

        for (ExclusiveTrade trade : crossTrades) {
            if (playerScore < trade.minScore()) {
                inaccessible.add(OfferIdentityHash.compute(trade.createOffer()));
            }
        }

        return inaccessible;
    }

    // @VisibleForTesting
    public static void loadTrades(ResourceManager manager) {
        Map<String, List<ExclusiveTrade>> nextProf = new HashMap<>();
        List<ExclusiveTrade> nextCross = new ArrayList<>();

        Map<ResourceLocation, List<Resource>> found = manager.listResourceStacks(
                DATA_PATH, id -> id.getPath().endsWith(".json"));

        for (var entry : found.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            String path = fileId.getPath();
            String professionName = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);

            boolean isCrossProfession = CROSS_PROFESSION_KEY.equals(professionName);

            List<ExclusiveTrade> merged = isCrossProfession
                    ? nextCross
                    : nextProf.computeIfAbsent(professionName, k -> new ArrayList<>());

            for (Resource resource : entry.getValue()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json == null) continue;

                    if (json.has("replace") && json.get("replace").getAsBoolean()) {
                        merged.clear();
                    }

                    int defaultMinScore = ReputationTier.fromName(
                            json.has("min_tier") ? json.get("min_tier").getAsString() : "trusted").minScore();

                    if (!json.has("trades")) continue;

                    JsonArray trades = json.getAsJsonArray("trades");
                    for (JsonElement tradeElem : trades) {
                        try {
                            ExclusiveTrade trade = parseTrade(tradeElem.getAsJsonObject(), defaultMinScore);
                            if (trade != null) {
                                merged.add(trade);
                            }
                        } catch (Exception e) {
                            Mercantile.LOGGER.warn("Skipping malformed exclusive trade in {}: {}", fileId, e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    Mercantile.LOGGER.error("Failed to load exclusive trades: {}", fileId, e);
                }
            }
        }

        // Deep-copy list values before publishing immutable snapshot
        Map<String, List<ExclusiveTrade>> immutableProf = new HashMap<>();
        for (var e : nextProf.entrySet()) {
            immutableProf.put(e.getKey(), List.copyOf(e.getValue()));
        }
        PROFESSION_TRADES = Map.copyOf(immutableProf);
        CROSS_PROFESSION_TRADES = List.copyOf(nextCross);

        int profCount = PROFESSION_TRADES.values().stream().mapToInt(List::size).sum();
        int crossCount = CROSS_PROFESSION_TRADES.size();
        Mercantile.LOGGER.info("Loaded {} exclusive trades ({} profession, {} cross-profession)",
                profCount + crossCount, profCount, crossCount);
    }

    private static ExclusiveTrade parseTrade(JsonObject json, int defaultMinScore) {
        ItemCost input1 = parseItemCost(json.getAsJsonObject("input_1"));
        if (input1 == null) return null;

        ItemCost input2 = json.has("input_2") ? parseItemCost(json.getAsJsonObject("input_2")) : null;

        ItemStack output = parseItemStack(json.getAsJsonObject("output"));
        if (output == null || output.isEmpty()) return null;

        int maxUses = json.has("max_uses") ? json.get("max_uses").getAsInt() : 12;
        int xpGain = json.has("xp_gain") ? json.get("xp_gain").getAsInt() : 1;
        float priceMultiplier = json.has("price_multiplier") ? json.get("price_multiplier").getAsFloat() : 0.05f;

        int minScore = json.has("min_tier_override")
                ? ReputationTier.fromName(json.get("min_tier_override").getAsString()).minScore()
                : defaultMinScore;

        return new ExclusiveTrade(input1, input2, output, maxUses, xpGain, priceMultiplier, minScore);
    }

    private static ItemCost parseItemCost(JsonObject json) {
        if (json == null) return null;
        Item item = parseItem(json.get("item").getAsString());
        if (item == null) return null;
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        return new ItemCost(item, count);
    }

    private static ItemStack parseItemStack(JsonObject json) {
        if (json == null) return null;
        Item item = parseItem(json.get("item").getAsString());
        if (item == null) return null;
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        return new ItemStack(item, count);
    }

    private static Item parseItem(String id) {
        ResourceLocation itemId = ResourceLocation.tryParse(id);
        if (itemId == null) {
            Mercantile.LOGGER.warn("Invalid item ID in exclusive trade: {}", id);
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            Mercantile.LOGGER.warn("Unknown item in exclusive trade: {}", id);
            return null;
        }
        return item;
    }

    public static Map<String, List<ExclusiveTrade>> professionTradesSnapshot() {
        return Collections.unmodifiableMap(PROFESSION_TRADES);
    }

    public static List<ExclusiveTrade> crossProfessionTradesSnapshot() {
        return Collections.unmodifiableList(CROSS_PROFESSION_TRADES);
    }

    @VisibleForTesting
    public static void setSnapshotsForTesting(Map<String, List<ExclusiveTrade>> profession,
                                              List<ExclusiveTrade> crossProfession) {
        PROFESSION_TRADES = Map.copyOf(profession);
        CROSS_PROFESSION_TRADES = List.copyOf(crossProfession);
    }

    public record ExclusiveTrade(ItemCost input1, ItemCost input2, ItemStack output,
                                 int maxUses, int xpGain, float priceMultiplier, int minScore) {
        @Override
        public ItemStack output() {
            return output.copy();
        }

        public MerchantOffer createOffer() {
            if (input2 != null) {
                return new MerchantOffer(input1, Optional.of(input2), output.copy(), maxUses, xpGain, priceMultiplier);
            }
            return new MerchantOffer(input1, output.copy(), maxUses, xpGain, priceMultiplier);
        }
    }
}
