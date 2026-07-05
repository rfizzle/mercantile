package com.rfizzle.mercantile.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceKey;
import com.rfizzle.mercantile.Mercantile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class VillagerNameManagerTest {

    private static final Gson GSON = new Gson();
    private static final String[] ALL_CATEGORIES = {
            "plains", "desert", "taiga", "jungle", "savanna", "swamp", "badlands", "fallback"
    };

    static Stream<Arguments> biomeCategoryMappings() {
        return Stream.of(
                Arguments.of(Biomes.PLAINS, "plains"),
                Arguments.of(Biomes.SUNFLOWER_PLAINS, "plains"),
                Arguments.of(Biomes.FOREST, "plains"),
                Arguments.of(Biomes.FLOWER_FOREST, "plains"),
                Arguments.of(Biomes.BIRCH_FOREST, "plains"),
                Arguments.of(Biomes.OLD_GROWTH_BIRCH_FOREST, "plains"),
                Arguments.of(Biomes.DARK_FOREST, "plains"),
                Arguments.of(Biomes.MEADOW, "plains"),
                Arguments.of(Biomes.CHERRY_GROVE, "plains"),
                Arguments.of(Biomes.WINDSWEPT_HILLS, "plains"),
                Arguments.of(Biomes.WINDSWEPT_GRAVELLY_HILLS, "plains"),
                Arguments.of(Biomes.WINDSWEPT_FOREST, "plains"),
                Arguments.of(Biomes.DESERT, "desert"),
                Arguments.of(Biomes.TAIGA, "taiga"),
                Arguments.of(Biomes.SNOWY_TAIGA, "taiga"),
                Arguments.of(Biomes.OLD_GROWTH_PINE_TAIGA, "taiga"),
                Arguments.of(Biomes.OLD_GROWTH_SPRUCE_TAIGA, "taiga"),
                Arguments.of(Biomes.SNOWY_PLAINS, "taiga"),
                Arguments.of(Biomes.ICE_SPIKES, "taiga"),
                Arguments.of(Biomes.SNOWY_BEACH, "taiga"),
                Arguments.of(Biomes.FROZEN_RIVER, "taiga"),
                Arguments.of(Biomes.FROZEN_OCEAN, "taiga"),
                Arguments.of(Biomes.DEEP_FROZEN_OCEAN, "taiga"),
                Arguments.of(Biomes.GROVE, "taiga"),
                Arguments.of(Biomes.FROZEN_PEAKS, "taiga"),
                Arguments.of(Biomes.JAGGED_PEAKS, "taiga"),
                Arguments.of(Biomes.SNOWY_SLOPES, "taiga"),
                Arguments.of(Biomes.STONY_PEAKS, "taiga"),
                Arguments.of(Biomes.JUNGLE, "jungle"),
                Arguments.of(Biomes.SPARSE_JUNGLE, "jungle"),
                Arguments.of(Biomes.BAMBOO_JUNGLE, "jungle"),
                Arguments.of(Biomes.SAVANNA, "savanna"),
                Arguments.of(Biomes.SAVANNA_PLATEAU, "savanna"),
                Arguments.of(Biomes.WINDSWEPT_SAVANNA, "savanna"),
                Arguments.of(Biomes.SWAMP, "swamp"),
                Arguments.of(Biomes.MANGROVE_SWAMP, "swamp"),
                Arguments.of(Biomes.BADLANDS, "badlands"),
                Arguments.of(Biomes.ERODED_BADLANDS, "badlands"),
                Arguments.of(Biomes.WOODED_BADLANDS, "badlands")
        );
    }

    @ParameterizedTest
    @MethodSource("biomeCategoryMappings")
    void biomeMapsToCategoryCorrectly(ResourceKey<Biome> biome, String expectedCategory) {
        assertEquals(expectedCategory, VillagerNameManager.getCategory(biome));
    }

    @Test
    void unmappedBiomeFallsBackToFallback() {
        assertEquals("fallback", VillagerNameManager.getCategory(Biomes.MUSHROOM_FIELDS));
        assertEquals("fallback", VillagerNameManager.getCategory(Biomes.THE_END));
        assertEquals("fallback", VillagerNameManager.getCategory(Biomes.NETHER_WASTES));
        assertEquals("fallback", VillagerNameManager.getCategory(Biomes.DEEP_DARK));
        assertEquals("fallback", VillagerNameManager.getCategory(Biomes.OCEAN));
        assertEquals("fallback", VillagerNameManager.getCategory(Biomes.LUSH_CAVES));
    }

    @Test
    void allBuiltInPoolFilesAreValidAndSized() {
        for (String category : ALL_CATEGORIES) {
            String path = "/data/mercantile/villager_names/" + category + ".json";
            try (InputStream stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, "Missing built-in name pool: " + path);

                JsonObject json = GSON.fromJson(
                        new String(stream.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
                assertNotNull(json, category + ".json should be valid JSON");
                assertTrue(json.has("replace"), category + ".json missing 'replace' field");
                assertTrue(json.has("names"), category + ".json missing 'names' field");
                assertFalse(json.get("replace").getAsBoolean(), "Built-in pools should have replace=false");

                JsonArray names = json.getAsJsonArray("names");
                assertTrue(names.size() >= 40,
                        category + " pool should have >= 40 names, got " + names.size());
                assertTrue(names.size() <= 60,
                        category + " pool should have <= 60 names, got " + names.size());

                Set<String> seen = new HashSet<>();
                for (var element : names) {
                    String name = element.getAsString();
                    assertFalse(name.isBlank(), category + " pool has a blank name");
                    assertTrue(seen.add(name), category + " pool has duplicate: " + name);
                }
            } catch (Exception e) {
                fail("Error reading " + category + ".json: " + e.getMessage());
            }
        }
    }

    @Test
    void loadNamePoolsPopulatesAllCategories() {
        VillagerNameManager.loadNamePools(createManagerFromClasspath());

        for (String category : ALL_CATEGORIES) {
            List<String> pool = VillagerNameManager.getNamePool(category);
            assertFalse(pool.isEmpty(), category + " pool should not be empty after loading");
            assertTrue(pool.size() >= 40, category + " pool should have >= 40 names");
        }
    }

    @Test
    void replaceFlagClearsPreviousPool() {
        Map<ResourceLocation, List<String>> resources = new LinkedHashMap<>();
        ResourceLocation id = poolId("plains");
        resources.put(id, List.of(
                "{\"replace\": false, \"names\": [\"Alice\", \"Bob\"]}",
                "{\"replace\": true, \"names\": [\"Zara\"]}"
        ));
        fillEmptyPools(resources, "plains");

        VillagerNameManager.loadNamePools(createManager(resources));

        assertEquals(List.of("Zara"), VillagerNameManager.getNamePool("plains"));
    }

    @Test
    void appendModeAddsToPreviousPool() {
        Map<ResourceLocation, List<String>> resources = new LinkedHashMap<>();
        ResourceLocation id = poolId("plains");
        resources.put(id, List.of(
                "{\"replace\": false, \"names\": [\"Alice\", \"Bob\"]}",
                "{\"replace\": false, \"names\": [\"Charlie\"]}"
        ));
        fillEmptyPools(resources, "plains");

        VillagerNameManager.loadNamePools(createManager(resources));

        assertEquals(List.of("Alice", "Bob", "Charlie"), VillagerNameManager.getNamePool("plains"));
    }

    @Test
    void duplicateNamesAreIgnored() {
        Map<ResourceLocation, List<String>> resources = new LinkedHashMap<>();
        ResourceLocation id = poolId("plains");
        resources.put(id, List.of("{\"replace\": false, \"names\": [\"Alice\", \"Bob\", \"Alice\"]}"));
        fillEmptyPools(resources, "plains");

        VillagerNameManager.loadNamePools(createManager(resources));

        assertEquals(List.of("Alice", "Bob"), VillagerNameManager.getNamePool("plains"));
    }

    @Test
    void duplicatesAcrossPacksAreIgnored() {
        Map<ResourceLocation, List<String>> resources = new LinkedHashMap<>();
        ResourceLocation id = poolId("plains");
        resources.put(id, List.of(
                "{\"replace\": false, \"names\": [\"Alice\", \"Bob\"]}",
                "{\"replace\": false, \"names\": [\"Bob\", \"Charlie\"]}"
        ));
        fillEmptyPools(resources, "plains");

        VillagerNameManager.loadNamePools(createManager(resources));

        assertEquals(List.of("Alice", "Bob", "Charlie"), VillagerNameManager.getNamePool("plains"));
    }

    @Test
    void getRandomNameReturnsNameFromCorrectPool() {
        Map<ResourceLocation, List<String>> resources = new LinkedHashMap<>();
        resources.put(poolId("plains"), List.of("{\"replace\":false,\"names\":[\"PlainsOnly\"]}"));
        resources.put(poolId("desert"), List.of("{\"replace\":false,\"names\":[\"DesertOnly\"]}"));
        fillEmptyPools(resources, "plains", "desert");

        VillagerNameManager.loadNamePools(createManager(resources));

        RandomSource random = RandomSource.create(42);
        assertEquals("PlainsOnly", VillagerNameManager.getRandomName(Biomes.PLAINS, random));
        assertEquals("DesertOnly", VillagerNameManager.getRandomName(Biomes.DESERT, random));
    }

    @Test
    void getRandomNameFallsBackForUnmappedBiome() {
        Map<ResourceLocation, List<String>> resources = new LinkedHashMap<>();
        resources.put(poolId("fallback"), List.of("{\"replace\":false,\"names\":[\"FallbackName\"]}"));
        fillEmptyPools(resources, "fallback");

        VillagerNameManager.loadNamePools(createManager(resources));

        assertEquals("FallbackName",
                VillagerNameManager.getRandomName(Biomes.MUSHROOM_FIELDS, RandomSource.create(42)));
    }

    @Test
    void getRandomNameWithNullBiomeUsesFallback() {
        Map<ResourceLocation, List<String>> resources = new LinkedHashMap<>();
        resources.put(poolId("fallback"), List.of("{\"replace\":false,\"names\":[\"NullFallback\"]}"));
        fillEmptyPools(resources, "fallback");

        VillagerNameManager.loadNamePools(createManager(resources));

        assertEquals("NullFallback",
                VillagerNameManager.getRandomName(null, RandomSource.create(42)));
    }

    @Test
    void getRandomNameReturnsVillagerWhenAllPoolsEmpty() {
        VillagerNameManager.loadNamePools(createManager(Map.of()));

        assertEquals("Villager",
                VillagerNameManager.getRandomName(Biomes.PLAINS, RandomSource.create(42)));
    }

    @Test
    void emptyMappedPoolFallsBackToFallbackPool() {
        Map<ResourceLocation, List<String>> resources = new LinkedHashMap<>();
        resources.put(poolId("fallback"), List.of("{\"replace\":false,\"names\":[\"OnlyFallback\"]}"));
        fillEmptyPools(resources, "fallback");

        VillagerNameManager.loadNamePools(createManager(resources));

        assertEquals("OnlyFallback",
                VillagerNameManager.getRandomName(Biomes.PLAINS, RandomSource.create(42)));
    }

    @Test
    void dedupPicksAvailableNameWhenSomeTaken() {
        Map<ResourceLocation, List<String>> resources = new LinkedHashMap<>();
        resources.put(poolId("plains"),
                List.of("{\"replace\":false,\"names\":[\"A\",\"B\",\"C\",\"D\",\"E\"]}"));
        fillEmptyPools(resources, "plains");
        VillagerNameManager.loadNamePools(createManager(resources));

        Set<String> taken = Set.of("A", "B", "C");
        Set<String> seen = new HashSet<>();
        for (int seed = 0; seed < 50; seed++) {
            String picked = VillagerNameManager.getRandomNameAvoiding(
                    Biomes.PLAINS, RandomSource.create(seed), taken);
            seen.add(picked);
            assertFalse(taken.contains(picked),
                    "Dedup should never return a taken name; got " + picked);
        }
        assertTrue(seen.contains("D") || seen.contains("E"),
                "Should pick from available subset");
    }

    @Test
    void dedupFallsBackToRandomWhenAllTaken() {
        Map<ResourceLocation, List<String>> resources = new LinkedHashMap<>();
        resources.put(poolId("plains"),
                List.of("{\"replace\":false,\"names\":[\"A\",\"B\",\"C\"]}"));
        fillEmptyPools(resources, "plains");
        VillagerNameManager.loadNamePools(createManager(resources));

        Set<String> taken = Set.of("A", "B", "C");
        String picked = VillagerNameManager.getRandomNameAvoiding(
                Biomes.PLAINS, RandomSource.create(42), taken);
        assertTrue(taken.contains(picked),
                "When all names taken, falls back to random pool pick; got " + picked);
    }

    @Test
    void dedupEmptyTakenBehavesLikeRandomPick() {
        Map<ResourceLocation, List<String>> resources = new LinkedHashMap<>();
        resources.put(poolId("plains"),
                List.of("{\"replace\":false,\"names\":[\"A\",\"B\",\"C\",\"D\",\"E\"]}"));
        fillEmptyPools(resources, "plains");
        VillagerNameManager.loadNamePools(createManager(resources));

        for (int seed = 0; seed < 10; seed++) {
            String fromRandom = VillagerNameManager.getRandomName(
                    Biomes.PLAINS, RandomSource.create(seed));
            String fromDedup = VillagerNameManager.getRandomNameAvoiding(
                    Biomes.PLAINS, RandomSource.create(seed), Set.of());
            assertEquals(fromRandom, fromDedup,
                    "Empty taken set should produce same pick as getRandomName for seed " + seed);
        }
    }

    @Test
    void namePoolsAreImmutable() {
        Map<ResourceLocation, List<String>> resources = new LinkedHashMap<>();
        resources.put(poolId("plains"), List.of("{\"replace\":false,\"names\":[\"Alice\"]}"));
        fillEmptyPools(resources, "plains");

        VillagerNameManager.loadNamePools(createManager(resources));

        List<String> pool = VillagerNameManager.getNamePool("plains");
        assertThrows(UnsupportedOperationException.class, () -> pool.add("Hacker"));
    }

    // --- helpers ---

    private static ResourceLocation poolId(String category) {
        return Mercantile.id("villager_names/" + category + ".json");
    }

    private void fillEmptyPools(Map<ResourceLocation, List<String>> resources, String... loaded) {
        Set<String> skip = Set.of(loaded);
        for (String cat : ALL_CATEGORIES) {
            if (!skip.contains(cat)) {
                resources.putIfAbsent(poolId(cat), List.of());
            }
        }
    }

    private ResourceManager createManagerFromClasspath() {
        Map<ResourceLocation, List<String>> resources = new LinkedHashMap<>();
        for (String category : ALL_CATEGORIES) {
            String path = "/data/mercantile/villager_names/" + category + ".json";
            try (InputStream stream = getClass().getResourceAsStream(path)) {
                if (stream != null) {
                    resources.put(poolId(category),
                            List.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
                }
            } catch (Exception e) {
                fail("Failed to read " + path + ": " + e.getMessage());
            }
        }
        return createManager(resources);
    }

    private ResourceManager createManager(Map<ResourceLocation, List<String>> jsonByLocation) {
        return new ResourceManager() {
            @Override
            public Set<String> getNamespaces() {
                return Set.of("mercantile");
            }

            @Override
            public List<Resource> getResourceStack(ResourceLocation id) {
                return jsonByLocation.getOrDefault(id, List.of()).stream()
                        .map(json -> new Resource(
                                (PackResources) null,
                                () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))))
                        .toList();
            }

            @Override
            public Optional<Resource> getResource(ResourceLocation id) {
                var stack = getResourceStack(id);
                return stack.isEmpty() ? Optional.empty() : Optional.of(stack.getLast());
            }

            @Override
            public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
                return Map.of();
            }

            @Override
            public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) {
                return Map.of();
            }

            @Override
            public Stream<PackResources> listPacks() {
                return Stream.empty();
            }
        };
    }
}
