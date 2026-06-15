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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class GiftMappingManager {
    private static final Gson GSON = new Gson();
    private static final String DATA_PATH = "gift_mappings";

    private static volatile Map<String, Set<Item>> PROFESSION_GIFTS = Map.of();

    private GiftMappingManager() {
    }

    public static void init() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return Mercantile.id("gift_mappings");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        loadMappings(manager);
                    }
                }
        );
    }

    public static boolean isValidGift(String profession, Item item) {
        Set<Item> validGifts = PROFESSION_GIFTS.get(profession);
        return validGifts != null && validGifts.contains(item);
    }

    public static void loadMappings(ResourceManager manager) {
        Map<String, Set<Item>> next = new HashMap<>();

        Map<ResourceLocation, List<Resource>> found = manager.listResourceStacks(
                DATA_PATH, id -> id.getPath().endsWith(".json"));

        for (var entry : found.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            String path = fileId.getPath();
            String professionName = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);

            Set<Item> items = next.computeIfAbsent(professionName, k -> new HashSet<>());

            for (Resource resource : entry.getValue()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json == null) continue;

                    if (json.has("replace") && json.get("replace").getAsBoolean()) {
                        items.clear();
                    }

                    if (json.has("items")) {
                        JsonArray itemsArray = json.getAsJsonArray("items");
                        for (JsonElement element : itemsArray) {
                            String itemIdStr = element.getAsString();
                            ResourceLocation itemId = ResourceLocation.tryParse(itemIdStr);
                            if (itemId != null) {
                                Item item = BuiltInRegistries.ITEM.get(itemId);
                                if (item != Items.AIR) {
                                    items.add(item);
                                } else {
                                    Mercantile.LOGGER.warn("Unknown item in gift mapping {}: {}", fileId, itemIdStr);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Mercantile.LOGGER.error("Failed to load gift mappings: {}", fileId, e);
                }
            }
        }

        Map<String, Set<Item>> immutable = new HashMap<>();
        for (var e : next.entrySet()) {
            immutable.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        PROFESSION_GIFTS = Map.copyOf(immutable);

        int profCount = PROFESSION_GIFTS.size();
        int itemCount = PROFESSION_GIFTS.values().stream().mapToInt(Set::size).sum();
        Mercantile.LOGGER.info("Loaded {} items across {} profession gift mappings", itemCount, profCount);
    }

    public static Set<Item> getGiftsForProfession(String profession) {
        return PROFESSION_GIFTS.getOrDefault(profession, Set.of());
    }
}
