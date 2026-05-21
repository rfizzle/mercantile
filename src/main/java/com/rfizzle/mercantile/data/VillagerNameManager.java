package com.rfizzle.mercantile.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.config.MercantileConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class VillagerNameManager {
    private static final Gson GSON = new Gson();
    private static final String DATA_PATH = "villager_names";

    private static final List<String> CATEGORIES = List.of(
            "plains", "desert", "taiga", "jungle", "savanna", "swamp", "badlands", "fallback"
    );

    private static final Map<ResourceKey<Biome>, String> BIOME_TO_CATEGORY = new HashMap<>();
    private static volatile Map<String, List<String>> NAME_POOLS = Map.of();

    static {
        mapBiome(Biomes.PLAINS, "plains");
        mapBiome(Biomes.SUNFLOWER_PLAINS, "plains");
        mapBiome(Biomes.FOREST, "plains");
        mapBiome(Biomes.FLOWER_FOREST, "plains");
        mapBiome(Biomes.BIRCH_FOREST, "plains");
        mapBiome(Biomes.OLD_GROWTH_BIRCH_FOREST, "plains");
        mapBiome(Biomes.DARK_FOREST, "plains");
        mapBiome(Biomes.MEADOW, "plains");
        mapBiome(Biomes.CHERRY_GROVE, "plains");
        mapBiome(Biomes.WINDSWEPT_HILLS, "plains");
        mapBiome(Biomes.WINDSWEPT_GRAVELLY_HILLS, "plains");
        mapBiome(Biomes.WINDSWEPT_FOREST, "plains");

        mapBiome(Biomes.DESERT, "desert");

        mapBiome(Biomes.TAIGA, "taiga");
        mapBiome(Biomes.SNOWY_TAIGA, "taiga");
        mapBiome(Biomes.OLD_GROWTH_PINE_TAIGA, "taiga");
        mapBiome(Biomes.OLD_GROWTH_SPRUCE_TAIGA, "taiga");
        mapBiome(Biomes.SNOWY_PLAINS, "taiga");
        mapBiome(Biomes.ICE_SPIKES, "taiga");
        mapBiome(Biomes.SNOWY_BEACH, "taiga");
        mapBiome(Biomes.FROZEN_RIVER, "taiga");
        mapBiome(Biomes.FROZEN_OCEAN, "taiga");
        mapBiome(Biomes.DEEP_FROZEN_OCEAN, "taiga");
        mapBiome(Biomes.GROVE, "taiga");
        mapBiome(Biomes.FROZEN_PEAKS, "taiga");
        mapBiome(Biomes.JAGGED_PEAKS, "taiga");
        mapBiome(Biomes.SNOWY_SLOPES, "taiga");
        mapBiome(Biomes.STONY_PEAKS, "taiga");

        mapBiome(Biomes.JUNGLE, "jungle");
        mapBiome(Biomes.SPARSE_JUNGLE, "jungle");
        mapBiome(Biomes.BAMBOO_JUNGLE, "jungle");

        mapBiome(Biomes.SAVANNA, "savanna");
        mapBiome(Biomes.SAVANNA_PLATEAU, "savanna");
        mapBiome(Biomes.WINDSWEPT_SAVANNA, "savanna");

        mapBiome(Biomes.SWAMP, "swamp");
        mapBiome(Biomes.MANGROVE_SWAMP, "swamp");

        mapBiome(Biomes.BADLANDS, "badlands");
        mapBiome(Biomes.ERODED_BADLANDS, "badlands");
        mapBiome(Biomes.WOODED_BADLANDS, "badlands");
    }

    private static void mapBiome(ResourceKey<Biome> biome, String category) {
        BIOME_TO_CATEGORY.put(biome, category);
    }

    public static String getCategory(ResourceKey<Biome> biome) {
        return BIOME_TO_CATEGORY.getOrDefault(biome, "fallback");
    }

    public static String getRandomName(ResourceKey<Biome> biome, RandomSource random) {
        String category = biome != null ? getCategory(biome) : "fallback";
        List<String> pool = NAME_POOLS.get(category);
        if (pool == null || pool.isEmpty()) {
            pool = NAME_POOLS.get("fallback");
        }
        if (pool == null || pool.isEmpty()) {
            return "Villager";
        }
        return pool.get(random.nextInt(pool.size()));
    }

    public static List<String> getNamePool(String category) {
        return NAME_POOLS.getOrDefault(category, List.of());
    }

    public static void init() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return Mercantile.id("villager_names");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        loadNamePools(manager);
                    }
                }
        );

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof Villager villager)) return;
            if (!MercantileConfig.get().enableNames) return;
            assignName(villager, world);
        });
    }

    private static void assignName(Villager villager, ServerLevel world) {
        VillagerData data = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);

        if (data.isNameAssigned()) {
            if (villager.hasCustomName()) {
                villager.setCustomNameVisible(true);
            }
            return;
        }

        data.setNameAssigned(true);
        villager.setAttached(MercantileAttachments.VILLAGER_DATA, data);

        if (villager.hasCustomName()) {
            return;
        }

        Holder<Biome> biomeHolder = world.getBiome(villager.blockPosition());
        Optional<ResourceKey<Biome>> biomeKey = biomeHolder.unwrapKey();
        String name = getRandomName(biomeKey.orElse(null), villager.getRandom());

        villager.setCustomName(Component.literal(name));
        villager.setCustomNameVisible(true);
    }

    public static void loadNamePools(ResourceManager manager) {
        Map<String, List<String>> next = new HashMap<>();

        for (String category : CATEGORIES) {
            ResourceLocation id = Mercantile.id(DATA_PATH + "/" + category + ".json");
            List<String> merged = new ArrayList<>();

            List<Resource> resources = manager.getResourceStack(id);
            for (Resource resource : resources) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json == null) continue;

                    boolean replace = json.has("replace") && json.get("replace").getAsBoolean();
                    if (replace) {
                        merged.clear();
                    }

                    if (json.has("names")) {
                        Set<String> existing = new HashSet<>(merged);
                        for (JsonElement element : json.getAsJsonArray("names")) {
                            String name = element.getAsString();
                            if (existing.add(name)) {
                                merged.add(name);
                            }
                        }
                    }
                } catch (Exception e) {
                    Mercantile.LOGGER.error("Failed to load villager name pool: {}", id, e);
                }
            }

            if (!merged.isEmpty()) {
                next.put(category, List.copyOf(merged));
            }
        }

        int total = next.values().stream().mapToInt(List::size).sum();
        int poolCount = next.size();
        NAME_POOLS = Map.copyOf(next);

        Mercantile.LOGGER.info("Loaded {} villager names across {} pools", total, poolCount);
    }
}
