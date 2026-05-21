package com.rfizzle.mercantile.concurrency;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.data.VillagerHeadTextures;
import com.rfizzle.mercantile.data.VillagerNameManager;
import com.rfizzle.mercantile.follow.FollowManager;
import com.rfizzle.mercantile.healing.VillagerHealingContext;
import com.rfizzle.mercantile.reputation.ExclusiveTradesManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biomes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ThreadSafetyTest {

    private static final int THREADS = 8;
    private static final int ITERATIONS = 200;

    @AfterEach
    @SuppressWarnings("unchecked")
    void cleanup() throws Exception {
        FollowManager.clearAll();

        Field instanceField = MercantileConfig.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        // VillagerHeadTextures: remove test-only entries; clear the profile cache
        Field texturesField = VillagerHeadTextures.class.getDeclaredField("TEXTURES");
        texturesField.setAccessible(true);
        Map<ResourceLocation, ?> textures = (Map<ResourceLocation, ?>) texturesField.get(null);
        textures.keySet().removeIf(k -> "conctest".equals(k.getNamespace()));
        Field profileCacheField = VillagerHeadTextures.class.getDeclaredField("PROFILE_CACHE");
        profileCacheField.setAccessible(true);
        ((Map<?, ?>) profileCacheField.get(null)).clear();

        // VillagerNameManager: reset volatile name-pool reference
        Field namePoolsField = VillagerNameManager.class.getDeclaredField("NAME_POOLS");
        namePoolsField.setAccessible(true);
        namePoolsField.set(null, Map.of());

        // ExclusiveTradesManager: reset all three volatile/final fields
        Field profTradesField = ExclusiveTradesManager.class.getDeclaredField("PROFESSION_TRADES");
        profTradesField.setAccessible(true);
        profTradesField.set(null, Map.of());
        Field crossTradesField = ExclusiveTradesManager.class.getDeclaredField("CROSS_PROFESSION_TRADES");
        crossTradesField.setAccessible(true);
        crossTradesField.set(null, List.of());
        Field injectedField = ExclusiveTradesManager.class.getDeclaredField("INJECTED_OFFERS");
        injectedField.setAccessible(true);
        ((Map<?, ?>) injectedField.get(null)).clear();

        // VillagerHealingContext: remove ThreadLocal entry for the test runner thread
        VillagerHealingContext.exit();
    }

    // --- 1. VillagerHealingContext — ThreadLocal semantics ---

    @Test
    void healingContextIsThreadLocal() throws Exception {
        int n = THREADS;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        CyclicBarrier barrier = new CyclicBarrier(n);
        AtomicReference<Throwable> error = new AtomicReference<>();

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < n; t++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    barrier.await();
                    for (int i = 0; i < ITERATIONS; i++) {
                        assertFalse(VillagerHealingContext.isActive(),
                                "Should start inactive on each thread");
                        VillagerHealingContext.enter();
                        assertTrue(VillagerHealingContext.isActive(),
                                "Should be active after enter()");
                        VillagerHealingContext.exit();
                        assertFalse(VillagerHealingContext.isActive(),
                                "Should be inactive after exit()");
                    }
                } catch (Throwable e) {
                    error.compareAndSet(null, e);
                }
                return null;
            }));
        }
        start.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            fail("Thread pool did not terminate — worker task may have hung");
        }

        assertNull(error.get(), () -> "Thread saw unexpected state: " + error.get());
    }

    @Test
    void healingContextThreadsAreIndependent() throws Exception {
        CountDownLatch threadAEntered = new CountDownLatch(1);
        CountDownLatch threadBChecked = new CountDownLatch(1);
        AtomicReference<Boolean> threadBSawActive = new AtomicReference<>();

        Thread a = new Thread(() -> {
            VillagerHealingContext.enter();
            threadAEntered.countDown();
            try { threadBChecked.await(); } catch (InterruptedException ignored) {}
            VillagerHealingContext.exit();
        });
        Thread b = new Thread(() -> {
            try { threadAEntered.await(); } catch (InterruptedException ignored) {}
            threadBSawActive.set(VillagerHealingContext.isActive());
            threadBChecked.countDown();
        });

        a.start(); b.start();
        a.join(); b.join();

        assertFalse(threadBSawActive.get(),
                "Thread B should not see Thread A's enter() — ThreadLocal must isolate state");
    }

    // --- 2. VillagerHeadTextures — ConcurrentHashMap ---

    @Test
    void villagerHeadTexturesConcurrentAccess() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            final int tid = t;
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < ITERATIONS; i++) {
                        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                                "conctest", "prof_" + tid + "_" + (i % 5));
                        VillagerHeadTextures.register(id, "eyJ0ZXN0IjoiY29uY3VycmVuY3kifQ==");
                        // getTextureValue is a pure read
                        String val = VillagerHeadTextures.getTextureValue(id);
                        assertNotNull(val);
                        // getProfile triggers computeIfAbsent on PROFILE_CACHE
                        var profile = VillagerHeadTextures.getProfile(id);
                        assertNotNull(profile);
                    }
                } catch (Throwable e) {
                    error.compareAndSet(null, e);
                }
                return null;
            }));
        }
        start.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            fail("Thread pool did not terminate — worker task may have hung");
        }

        assertNull(error.get(), () -> "Concurrent access threw: " + error.get());
    }

    // --- 3. VillagerNameManager — volatile reference-swap ---

    @Test
    void villagerNameManagerConcurrentLoadAndRead() throws Exception {
        ResourceManager mgr = buildNameManager(
                "plains", "[\"Alice\",\"Bob\",\"Carol\",\"Dave\",\"Eve\"]"
        );
        // pre-load so readers see a valid snapshot
        VillagerNameManager.loadNamePools(mgr);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        List<Future<?>> futures = new ArrayList<>();
        // Half threads reload, half read
        for (int t = 0; t < THREADS; t++) {
            final boolean isWriter = (t % 2 == 0);
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < ITERATIONS; i++) {
                        if (isWriter) {
                            VillagerNameManager.loadNamePools(mgr);
                        } else {
                            List<String> pool2 = VillagerNameManager.getNamePool("plains");
                            // Must be empty (no pool loaded for category) or a consistent list
                            // We only care that no CME / corrupt state
                            if (!pool2.isEmpty()) {
                                RandomSource rng = RandomSource.create(i);
                                String name = VillagerNameManager.getRandomName(Biomes.PLAINS, rng);
                                assertNotNull(name);
                                assertTrue(pool2.contains(name) || "Villager".equals(name));
                            }
                        }
                    }
                } catch (Throwable e) {
                    error.compareAndSet(null, e);
                }
                return null;
            }));
        }
        start.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            fail("Thread pool did not terminate — worker task may have hung");
        }

        assertNull(error.get(), () -> "Concurrent load/read threw: " + error.get());
    }

    // --- 4. ExclusiveTradesManager — volatile swap + synchronizedMap(WeakHashMap) ---

    @Test
    void exclusiveTradesConcurrentLoad() throws Exception {
        ResourceManager empty = emptyResourceManager();
        // Pre-load to establish baseline
        invokeLoadTrades(empty);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < ITERATIONS; i++) {
                        // Concurrent reloads must not throw ConcurrentModificationException
                        invokeLoadTrades(empty);
                    }
                } catch (Throwable e) {
                    error.compareAndSet(null, e);
                }
                return null;
            }));
        }
        start.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            fail("Thread pool did not terminate — worker task may have hung");
        }

        assertNull(error.get(), () -> "Concurrent loadTrades threw: " + error.get());

        // After all loads from an empty manager, both collections must be empty immutable snapshots
        Field profField = ExclusiveTradesManager.class.getDeclaredField("PROFESSION_TRADES");
        profField.setAccessible(true);
        Field crossField = ExclusiveTradesManager.class.getDeclaredField("CROSS_PROFESSION_TRADES");
        crossField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, List<?>> prof = (Map<String, List<?>>) profField.get(null);
        @SuppressWarnings("unchecked")
        List<?> cross = (List<?>) crossField.get(null);

        assertEquals(0, prof.size());
        assertEquals(0, cross.size());
    }

    // --- 5. FollowManager — ConcurrentHashMap + compute fix ---

    @Test
    void followManagerConcurrentRegisterStop() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        UUID[] villagers = new UUID[THREADS * 4];
        UUID[] players = new UUID[THREADS];
        for (int i = 0; i < villagers.length; i++) villagers[i] = UUID.randomUUID();
        for (int i = 0; i < players.length; i++) players[i] = UUID.randomUUID();

        Method tryRegister = FollowManager.class.getDeclaredMethod(
                "tryRegister", UUID.class, UUID.class, int.class);
        tryRegister.setAccessible(true);

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            final int tid = t;
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    UUID player = players[tid];
                    for (int i = 0; i < ITERATIONS; i++) {
                        UUID villager = villagers[(tid * 4 + i % 4) % villagers.length];
                        tryRegister.invoke(null, villager, player, 1000);
                        FollowManager.getFollowerCount(player);
                        FollowManager.getFollowers(player);
                        FollowManager.stopFollowing(villager);
                        FollowManager.isFollowing(villager);
                    }
                } catch (Throwable e) {
                    error.compareAndSet(null, e);
                }
                return null;
            }));
        }
        start.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            fail("Thread pool did not terminate — worker task may have hung");
        }

        assertNull(error.get(), () -> "Concurrent follow ops threw: " + error.get());

        // Verify cross-map invariant: every villager in a player's follower set must be
        // actively following (villagerToPlayer must agree with playerToVillagers).
        for (UUID player : players) {
            for (UUID v : FollowManager.getFollowers(player)) {
                assertTrue(FollowManager.isFollowing(v),
                        "Two-map invariant violated: getFollowers contains " + v
                                + " but isFollowing returns false");
            }
        }
    }

    @Test
    void followManagerTwoMapInvariant() throws Exception {
        Method tryRegister = FollowManager.class.getDeclaredMethod(
                "tryRegister", UUID.class, UUID.class, int.class);
        tryRegister.setAccessible(true);

        UUID player = UUID.randomUUID();
        UUID[] villagers = new UUID[20];
        for (int i = 0; i < villagers.length; i++) villagers[i] = UUID.randomUUID();

        // Register all, then stop some
        for (UUID v : villagers) {
            tryRegister.invoke(null, v, player, 1000);
        }
        // Stop every other one
        for (int i = 0; i < villagers.length; i += 2) {
            FollowManager.stopFollowing(villagers[i]);
        }

        int followerCount = FollowManager.getFollowerCount(player);
        Set<UUID> followers = FollowManager.getFollowers(player);

        assertEquals(10, followerCount);
        assertEquals(10, followers.size());
        // followers is a snapshot — must not throw on iteration
        for (UUID f : followers) {
            assertTrue(FollowManager.isFollowing(f));
        }
    }

    @Test
    void getFollowersReturnsSnapshot() throws Exception {
        Method tryRegister = FollowManager.class.getDeclaredMethod(
                "tryRegister", UUID.class, UUID.class, int.class);
        tryRegister.setAccessible(true);

        UUID player = UUID.randomUUID();
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();
        tryRegister.invoke(null, v1, player, 1000);
        tryRegister.invoke(null, v2, player, 1000);

        Set<UUID> snapshot = FollowManager.getFollowers(player);
        // Mutating the source after snapshot was taken must not affect the snapshot
        FollowManager.stopFollowing(v1);
        assertEquals(2, snapshot.size(), "Snapshot must not reflect post-snapshot mutations");
    }

    // --- 6. MercantileConfig — volatile + DCL ---

    @Test
    void configConcurrentReadsReturnSameInstance() throws Exception {
        Field instanceField = MercantileConfig.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);

        MercantileConfig expected = new MercantileConfig();
        instanceField.set(null, expected);

        int n = 16;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MercantileConfig>> futures = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return MercantileConfig.get();
            }));
        }
        start.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            fail("Thread pool did not terminate — worker task may have hung");
        }

        for (Future<MercantileConfig> f : futures) {
            assertSame(expected, f.get(), "All threads must see the same published instance");
        }
    }

    @Test
    void configVolatilePublishIsVisible() throws Exception {
        Field instanceField = MercantileConfig.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        // Pre-set a valid instance directly (bypasses load() which needs FabricLoader)
        MercantileConfig config = new MercantileConfig();
        instanceField.set(null, config);

        // Concurrent readers must all see the published instance
        int n = 16;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Future<MercantileConfig>> futures = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> { start.await(); return MercantileConfig.get(); }));
        }
        start.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            fail("Thread pool did not terminate — worker task may have hung");
        }

        for (var f : futures) {
            assertSame(config, f.get());
        }
    }

    // --- helpers ---

    private static void invokeLoadTrades(ResourceManager mgr) {
        ExclusiveTradesManager.loadTrades(mgr);
    }

    private static ResourceManager emptyResourceManager() {
        return new ResourceManager() {
            @Override public Set<String> getNamespaces() { return Set.of(); }
            @Override public Optional<Resource> getResource(ResourceLocation id) { return Optional.empty(); }
            @Override public List<Resource> getResourceStack(ResourceLocation id) { return List.of(); }
            @Override public Map<ResourceLocation, Resource> listResources(String p, Predicate<ResourceLocation> f) { return Map.of(); }
            @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(String p, Predicate<ResourceLocation> f) { return Map.of(); }
            @Override public Stream<PackResources> listPacks() { return Stream.empty(); }
        };
    }

    private static ResourceManager buildNameManager(String category, String namesJson) {
        String fullJson = "{\"replace\":false,\"names\":" + namesJson + "}";
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "mercantile", "villager_names/" + category + ".json");
        return new ResourceManager() {
            @Override public Set<String> getNamespaces() { return Set.of("mercantile"); }
            @Override public Optional<Resource> getResource(ResourceLocation loc) {
                var stack = getResourceStack(loc);
                return stack.isEmpty() ? Optional.empty() : Optional.of(stack.getFirst());
            }
            @Override public List<Resource> getResourceStack(ResourceLocation loc) {
                if (!loc.equals(id)) return List.of();
                return List.of(new Resource((PackResources) null,
                        () -> new ByteArrayInputStream(fullJson.getBytes(StandardCharsets.UTF_8))));
            }
            @Override public Map<ResourceLocation, Resource> listResources(String p, Predicate<ResourceLocation> f) { return Map.of(); }
            @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(String p, Predicate<ResourceLocation> f) { return Map.of(); }
            @Override public Stream<PackResources> listPacks() { return Stream.empty(); }
        };
    }
}
