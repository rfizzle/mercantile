package com.rfizzle.mercantile.reputation;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rfizzle.mercantile.Mercantile;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ExclusiveTradesManager {

    private static final Gson GSON = new Gson();
    private static final String DATA_PATH = "exclusive_trades";
    private static final String CROSS_PROFESSION_KEY = "_mercantile";

    private static final Map<String, List<ExclusiveTrade>> PROFESSION_TRADES = new HashMap<>();
    private static final List<ExclusiveTrade> CROSS_PROFESSION_TRADES = new ArrayList<>();

    private static final WeakHashMap<Villager, List<MerchantOffer>> INJECTED_OFFERS = new WeakHashMap<>();

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
        String profession = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession()).getPath();

        List<MerchantOffer> toInject = new ArrayList<>();

        List<ExclusiveTrade> professionTrades = PROFESSION_TRADES.get(profession);
        if (professionTrades != null) {
            for (ExclusiveTrade trade : professionTrades) {
                if (playerScore >= trade.minScore()) {
                    toInject.add(trade.createOffer());
                }
            }
        }

        for (ExclusiveTrade trade : CROSS_PROFESSION_TRADES) {
            if (playerScore >= trade.minScore()) {
                toInject.add(trade.createOffer());
            }
        }

        if (toInject.isEmpty()) return;

        villager.getOffers().addAll(toInject);
        INJECTED_OFFERS.put(villager, toInject);
    }

    static int getMinScoreForTier(String tierName) {
        return switch (tierName.toLowerCase(Locale.ROOT)) {
            case "honored" -> 100;
            case "trusted" -> 50;
            case "liked" -> 1;
            case "neutral" -> 0;
            case "distrusted" -> -49;
            case "reviled" -> -100;
            default -> 0;
        };
    }

    static void loadTrades(ResourceManager manager) {
        PROFESSION_TRADES.clear();
        CROSS_PROFESSION_TRADES.clear();

        Map<ResourceLocation, List<Resource>> found = manager.listResourceStacks(
                DATA_PATH, id -> id.getPath().endsWith(".json"));

        for (var entry : found.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            String path = fileId.getPath();
            String professionName = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);

            boolean isCrossProfession = CROSS_PROFESSION_KEY.equals(professionName);

            List<ExclusiveTrade> merged = isCrossProfession
                    ? CROSS_PROFESSION_TRADES
                    : PROFESSION_TRADES.computeIfAbsent(professionName, k -> new ArrayList<>());

            for (Resource resource : entry.getValue()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json == null) continue;

                    if (json.has("replace") && json.get("replace").getAsBoolean()) {
                        merged.clear();
                    }

                    int defaultMinScore = getMinScoreForTier(
                            json.has("min_tier") ? json.get("min_tier").getAsString() : "trusted");

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
                ? getMinScoreForTier(json.get("min_tier_override").getAsString())
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

    record ExclusiveTrade(ItemCost input1, ItemCost input2, ItemStack output,
                          int maxUses, int xpGain, float priceMultiplier, int minScore) {
        MerchantOffer createOffer() {
            if (input2 != null) {
                return new MerchantOffer(input1, Optional.of(input2), output.copy(), maxUses, xpGain, priceMultiplier);
            }
            return new MerchantOffer(input1, output.copy(), maxUses, xpGain, priceMultiplier);
        }
    }
}
