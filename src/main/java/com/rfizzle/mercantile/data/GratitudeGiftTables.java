package com.rfizzle.mercantile.data;

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
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven per-profession gratitude gift tables, loaded from
 * {@code data/mercantile/gratitude_gifts/<profession>.json}. Entries are weighted and carry a
 * count range; professions without a table fall back to {@link #fallbackPool()}.
 */
public final class GratitudeGiftTables {
    private static final Gson GSON = new Gson();
    private static final String DATA_PATH = "gratitude_gifts";

    public record GiftEntry(Item item, int weight, int minCount, int maxCount) {
    }

    private static volatile Map<String, List<GiftEntry>> PROFESSION_TABLES = Map.of();

    private GratitudeGiftTables() {
    }

    public static void init() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return Mercantile.id("gratitude_gifts");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        loadTables(manager);
                    }
                }
        );
    }

    /** Generic pool for professions without a table (issue #93: bread and seeds — flavor only). */
    public static List<GiftEntry> fallbackPool() {
        return List.of(
                new GiftEntry(Items.BREAD, 3, 1, 2),
                new GiftEntry(Items.WHEAT_SEEDS, 2, 2, 5)
        );
    }

    public static List<GiftEntry> getTableForProfession(String profession) {
        List<GiftEntry> table = PROFESSION_TABLES.get(profession);
        return table == null || table.isEmpty() ? fallbackPool() : table;
    }

    /** Rolls a weighted gift stack for the profession; never empty (the fallback pool backs it). */
    public static ItemStack rollGift(String profession, RandomSource random) {
        List<GiftEntry> table = getTableForProfession(profession);
        int[] weights = new int[table.size()];
        for (int i = 0; i < table.size(); i++) {
            weights[i] = table.get(i).weight();
        }
        GiftEntry entry = table.get(pickIndex(weights, random.nextInt(totalWeight(weights))));
        int count = entry.minCount() >= entry.maxCount()
                ? entry.minCount()
                : entry.minCount() + random.nextInt(entry.maxCount() - entry.minCount() + 1);
        return new ItemStack(entry.item(), count);
    }

    public static int totalWeight(int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        return total;
    }

    /**
     * Pure weighted pick: returns the index whose cumulative weight bracket contains {@code roll}
     * ({@code 0 <= roll < totalWeight}).
     */
    public static int pickIndex(int[] weights, int roll) {
        int cumulative = 0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                return i;
            }
        }
        return weights.length - 1;
    }

    public static void loadTables(ResourceManager manager) {
        Map<String, List<GiftEntry>> next = new HashMap<>();

        Map<ResourceLocation, List<Resource>> found = manager.listResourceStacks(
                DATA_PATH, id -> id.getPath().endsWith(".json"));

        for (var fileEntry : found.entrySet()) {
            ResourceLocation fileId = fileEntry.getKey();
            String path = fileId.getPath();
            String professionName = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);

            List<GiftEntry> entries = next.computeIfAbsent(professionName, k -> new ArrayList<>());

            for (Resource resource : fileEntry.getValue()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json == null) continue;

                    if (json.has("replace") && json.get("replace").getAsBoolean()) {
                        entries.clear();
                    }

                    if (json.has("items")) {
                        JsonArray itemsArray = json.getAsJsonArray("items");
                        for (JsonElement element : itemsArray) {
                            GiftEntry parsed = parseEntry(element, fileId);
                            if (parsed != null) {
                                entries.add(parsed);
                            }
                        }
                    }
                } catch (Exception e) {
                    Mercantile.LOGGER.error("Failed to load gratitude gift table: {}", fileId, e);
                }
            }
        }

        Map<String, List<GiftEntry>> immutable = new HashMap<>();
        for (var e : next.entrySet()) {
            immutable.put(e.getKey(), List.copyOf(e.getValue()));
        }
        PROFESSION_TABLES = Map.copyOf(immutable);

        int profCount = PROFESSION_TABLES.size();
        int entryCount = PROFESSION_TABLES.values().stream().mapToInt(List::size).sum();
        Mercantile.LOGGER.info("Loaded {} entries across {} profession gratitude gift tables", entryCount, profCount);
    }

    private static GiftEntry parseEntry(JsonElement element, ResourceLocation fileId) {
        if (!element.isJsonObject()) {
            Mercantile.LOGGER.warn("Non-object entry in gratitude gift table {}: {}", fileId, element);
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        String itemIdStr = obj.has("item") ? obj.get("item").getAsString() : null;
        ResourceLocation itemId = itemIdStr == null ? null : ResourceLocation.tryParse(itemIdStr);
        if (itemId == null) {
            Mercantile.LOGGER.warn("Missing or invalid item id in gratitude gift table {}: {}", fileId, element);
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            Mercantile.LOGGER.warn("Unknown item in gratitude gift table {}: {}", fileId, itemIdStr);
            return null;
        }
        int weight = obj.has("weight") ? Math.max(1, obj.get("weight").getAsInt()) : 1;
        int minCount = 1;
        int maxCount = 1;
        if (obj.has("count")) {
            JsonElement count = obj.get("count");
            if (count.isJsonArray()) {
                JsonArray range = count.getAsJsonArray();
                minCount = Math.max(1, range.get(0).getAsInt());
                maxCount = Math.max(minCount, range.get(range.size() - 1).getAsInt());
            } else {
                minCount = Math.max(1, count.getAsInt());
                maxCount = minCount;
            }
        }
        return new GiftEntry(item, weight, minCount, maxCount);
    }
}
