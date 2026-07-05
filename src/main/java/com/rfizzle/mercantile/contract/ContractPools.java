package com.rfizzle.mercantile.contract;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.data.GratitudeGiftTables;
import com.rfizzle.mercantile.registry.MercantileRegistry;
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
 * Data-driven per-profession delivery-contract request pools, loaded from
 * {@code data/mercantile/contracts/<profession>.json} (issue #86). Entries are weighted and carry
 * a count range plus an emerald payment range. Professions without a pool never roll contract
 * offers — there is deliberately no fallback pool, so pack makers control exactly who asks for
 * deliveries. Follows the same skeleton (filename keying, root-level {@code "replace"}, manual
 * GSON, warn-and-skip validation) as the mod's other loaders.
 */
public final class ContractPools {
    private static final Gson GSON = new Gson();
    private static final String DATA_PATH = "contracts";

    public record ContractEntry(Item item, int weight, int minCount, int maxCount,
                                int minPayment, int maxPayment) {
    }

    private static volatile Map<String, List<ContractEntry>> PROFESSION_POOLS = Map.of();

    private ContractPools() {
    }

    public static void init() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return Mercantile.id("contracts");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        loadPools(manager);
                    }
                }
        );
    }

    /** The pool for a profession key (path only, e.g. {@code "farmer"}); empty if none. */
    public static List<ContractEntry> getPool(String profession) {
        return PROFESSION_POOLS.getOrDefault(profession, List.of());
    }

    /** Rolls a weighted entry from the profession's pool, or {@code null} if the pool is empty. */
    public static ContractEntry roll(String profession, RandomSource random) {
        List<ContractEntry> pool = getPool(profession);
        if (pool.isEmpty()) return null;
        int[] weights = new int[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            weights[i] = pool.get(i).weight();
        }
        int total = GratitudeGiftTables.totalWeight(weights);
        return pool.get(GratitudeGiftTables.pickIndex(weights, random.nextInt(total)));
    }

    /** Uniform pick within an inclusive [min, max] range. */
    public static int rollRange(int min, int max, RandomSource random) {
        return min >= max ? min : min + random.nextInt(max - min + 1);
    }

    public static void loadPools(ResourceManager manager) {
        Map<String, List<ContractEntry>> next = new HashMap<>();

        Map<ResourceLocation, List<Resource>> found = manager.listResourceStacks(
                DATA_PATH, id -> id.getPath().endsWith(".json"));

        for (var fileEntry : found.entrySet()) {
            ResourceLocation fileId = fileEntry.getKey();
            String path = fileId.getPath();
            String professionName = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);

            List<ContractEntry> entries = next.computeIfAbsent(professionName, k -> new ArrayList<>());

            for (Resource resource : fileEntry.getValue()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json == null) continue;

                    if (json.has("replace") && json.get("replace").getAsBoolean()) {
                        entries.clear();
                    }

                    if (json.has("requests")) {
                        JsonArray requests = json.getAsJsonArray("requests");
                        for (JsonElement element : requests) {
                            ContractEntry parsed = parseEntry(element, fileId);
                            if (parsed != null) {
                                entries.add(parsed);
                            }
                        }
                    }
                } catch (Exception e) {
                    Mercantile.LOGGER.error("Failed to load contract pool: {}", fileId, e);
                }
            }
        }

        Map<String, List<ContractEntry>> immutable = new HashMap<>();
        for (var e : next.entrySet()) {
            immutable.put(e.getKey(), List.copyOf(e.getValue()));
        }
        PROFESSION_POOLS = Map.copyOf(immutable);

        int profCount = PROFESSION_POOLS.size();
        int entryCount = PROFESSION_POOLS.values().stream().mapToInt(List::size).sum();
        Mercantile.LOGGER.info("Loaded {} requests across {} profession contract pools", entryCount, profCount);
    }

    private static ContractEntry parseEntry(JsonElement element, ResourceLocation fileId) {
        if (!element.isJsonObject()) {
            Mercantile.LOGGER.warn("Non-object entry in contract pool {}: {}", fileId, element);
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        String itemIdStr = obj.has("item") ? obj.get("item").getAsString() : null;
        ResourceLocation itemId = itemIdStr == null ? null : ResourceLocation.tryParse(itemIdStr);
        if (itemId == null) {
            Mercantile.LOGGER.warn("Missing or invalid item id in contract pool {}: {}", fileId, element);
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            Mercantile.LOGGER.warn("Unknown item in contract pool {}: {}", fileId, itemIdStr);
            return null;
        }
        // Emeralds are the payment currency; a contract asking for them would be a money pump.
        if (item == Items.EMERALD || item == Items.EMERALD_BLOCK) {
            Mercantile.LOGGER.warn("Emerald requests are not allowed in contract pool {}: {}", fileId, itemIdStr);
            return null;
        }
        // Delivery matches by item type only, so a damageable request could be settled with a
        // 1-durability husk; and requesting the contract item itself would double-consume it.
        if (item == MercantileRegistry.DELIVERY_CONTRACT) {
            Mercantile.LOGGER.warn("The contract item cannot be requested in contract pool {}: {}", fileId, itemIdStr);
            return null;
        }
        if (new ItemStack(item).isDamageableItem()) {
            Mercantile.LOGGER.warn("Damageable items are not allowed in contract pool {}: {}", fileId, itemIdStr);
            return null;
        }
        int weight = obj.has("weight") ? Math.max(1, obj.get("weight").getAsInt()) : 1;
        int[] count = parseRange(obj.get("count"), 1, DeliveryContract.MAX_COUNT, 1);
        int[] payment = parseRange(obj.get("payment"), 0, DeliveryContract.MAX_PAYMENT, 1);
        return new ContractEntry(item, weight, count[0], count[1], payment[0], payment[1]);
    }

    /** An int or a {@code [min, max]} array, clamped to [floor, ceiling]; null yields the default. */
    private static int[] parseRange(JsonElement element, int floor, int ceiling, int defaultValue) {
        int min = defaultValue;
        int max = defaultValue;
        if (element != null) {
            if (element.isJsonArray()) {
                JsonArray range = element.getAsJsonArray();
                min = range.get(0).getAsInt();
                max = range.get(range.size() - 1).getAsInt();
            } else {
                min = element.getAsInt();
                max = min;
            }
        }
        min = Math.clamp(min, floor, ceiling);
        max = Math.clamp(max, min, ceiling);
        return new int[]{min, max};
    }
}
