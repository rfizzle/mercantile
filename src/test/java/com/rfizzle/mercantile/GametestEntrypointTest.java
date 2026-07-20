package com.rfizzle.mercantile;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the gametest entrypoint split: the gametest classes live only in the
 * {@code gametest} source set, so the shipped manifest must not declare them.
 * {@code fabric-gametest-api-v1} is a dev-only Fabric module whose initializer is
 * ungated — it instantiates every declared {@code fabric-gametest} entrypoint on
 * any dev launch, so a stale entry in the shipped manifest crashes {@code runServer},
 * whose run set does not carry the gametest source set.
 */
class GametestEntrypointTest {

    private static final Gson GSON = new Gson();
    private static final String GAMETEST_PKG = "com.rfizzle.mercantile.gametest.";

    @Test
    void shippedManifestDeclaresNoGametestEntrypoints() {
        JsonObject manifest = ManifestTestSupport.readMercantileManifest();
        JsonObject entrypoints = manifest.getAsJsonObject("entrypoints");
        assertNotNull(entrypoints, "fabric.mod.json must declare entrypoints");
        assertFalse(entrypoints.has("fabric-gametest"),
                "the shipped manifest must not declare fabric-gametest entrypoints — "
                        + "they belong to the mercantile-gametest manifest in src/gametest/resources");
    }

    /**
     * The gametest manifest is not on any classpath {@code ./gradlew build} reads, so an
     * unregistered suite is silent — it simply never runs. Compare the declared entrypoints
     * against the classes on disk so a new suite cannot rot unnoticed.
     */
    @Test
    void everyGametestSuiteIsRegistered() throws IOException {
        Path sources = Path.of("src/gametest/java/com/rfizzle/mercantile/gametest");
        assertTrue(Files.isDirectory(sources), "missing gametest source dir: " + sources.toAbsolutePath());

        Set<String> onDisk;
        try (Stream<Path> files = Files.list(sources)) {
            onDisk = files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith("GameTest.java"))
                    .map(n -> GAMETEST_PKG + n.substring(0, n.length() - ".java".length()))
                    .collect(Collectors.toCollection(TreeSet::new));
        }

        Path manifestPath = Path.of("src/gametest/resources/fabric.mod.json");
        assertTrue(Files.isRegularFile(manifestPath), "missing gametest manifest: " + manifestPath.toAbsolutePath());

        JsonObject manifest;
        try (Reader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
            manifest = GSON.fromJson(reader, JsonObject.class);
        }

        Set<String> declared = new TreeSet<>();
        manifest.getAsJsonObject("entrypoints").getAsJsonArray("fabric-gametest")
                .forEach(e -> declared.add(e.getAsString()));

        Set<String> unregistered = new TreeSet<>(onDisk);
        unregistered.removeAll(declared);
        assertTrue(unregistered.isEmpty(),
                "gametest classes exist but are not registered, so their tests never run: " + unregistered);

        Set<String> dangling = new TreeSet<>(declared);
        dangling.removeAll(onDisk);
        assertTrue(dangling.isEmpty(),
                "the gametest manifest names classes that no longer exist: " + dangling);
    }

    /**
     * Every entrypoint the shipped manifest declares, mapped to the class it must name.
     * The compat entries are as load-bearing as {@code main} and {@code client}: Jade,
     * Mod Menu, EMI, and REI are all discovered <em>through</em> this manifest, so dropping
     * an entry disables that integration without a crash, a warning, or a red test. (WTHIT
     * is the exception — it is discovered through {@code waila_plugins.json} at the resource
     * root, guarded separately by {@code WthitDiscoveryTest}.)
     */
    private static final Map<String, String> RUNTIME_ENTRYPOINTS = Map.of(
            "main", "com.rfizzle.mercantile.Mercantile",
            "client", "com.rfizzle.mercantile.client.MercantileClient",
            "modmenu", "com.rfizzle.mercantile.compat.modmenu.ModMenuIntegration",
            "emi", "com.rfizzle.mercantile.compat.emi.MercantileEmiPlugin",
            "rei_client", "com.rfizzle.mercantile.compat.rei.MercantileReiClientPlugin",
            "jade", "com.rfizzle.mercantile.compat.jade.MercantileJadePlugin");

    @Test
    void shippedManifestKeepsItsRuntimeEntrypoints() {
        JsonObject entrypoints = ManifestTestSupport.readMercantileManifest().getAsJsonObject("entrypoints");

        assertEquals(new TreeSet<>(RUNTIME_ENTRYPOINTS.keySet()), new TreeSet<>(entrypoints.keySet()),
                "the shipped manifest declares an entrypoint this guard does not pin — add it to "
                        + "RUNTIME_ENTRYPOINTS so the new integration cannot be dropped silently");

        List<String> drift = new ArrayList<>();
        RUNTIME_ENTRYPOINTS.forEach((key, fqn) -> {
            JsonArray declared = entrypoints.getAsJsonArray(key);
            if (declared == null) {
                drift.add(key + ": no longer declared");
            } else if (declared.size() != 1) {
                drift.add(key + ": expected exactly one class, found " + declared.size());
            } else if (!fqn.equals(declared.get(0).getAsString())) {
                drift.add(key + ": names " + declared.get(0).getAsString() + ", expected " + fqn);
            }
        });
        assertTrue(drift.isEmpty(), "shipped manifest entrypoint drift: " + drift);
    }

    /**
     * The manifest names its entrypoints as strings, so a class that is deleted or moved
     * leaves the entry dangling and the integration silently dead. Resolve each one against
     * the source tree rather than the classpath: the compat plugins implement interfaces from
     * {@code modCompileOnly} dependencies that are absent at test runtime, so loading the
     * class would fail for the wrong reason.
     */
    @Test
    void everyDeclaredEntrypointClassExists() {
        Path mainRoot = Path.of("src/main/java");
        Path clientRoot = Path.of("src/client/java");
        assertTrue(Files.isDirectory(mainRoot), "missing source root: " + mainRoot.toAbsolutePath());
        assertTrue(Files.isDirectory(clientRoot), "missing source root: " + clientRoot.toAbsolutePath());

        List<String> missing = new ArrayList<>();
        RUNTIME_ENTRYPOINTS.values().forEach(fqn -> {
            String relative = fqn.replace('.', '/') + ".java";
            if (!Files.isRegularFile(mainRoot.resolve(relative))
                    && !Files.isRegularFile(clientRoot.resolve(relative))) {
                missing.add(fqn);
            }
        });
        assertTrue(missing.isEmpty(),
                "the shipped manifest names entrypoint classes that no longer exist on disk: " + missing);
    }
}
