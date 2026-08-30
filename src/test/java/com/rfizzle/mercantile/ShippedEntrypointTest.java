package com.rfizzle.mercantile;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the shipped manifest's runtime entrypoints — {@code main}, {@code client}, and every
 * compat plugin discovered through {@code fabric.mod.json}. The gametest half of the old
 * combined guard lives in {@link GametestRegistrationTest}.
 */
class ShippedEntrypointTest {

    /**
     * Every entrypoint the shipped manifest declares, mapped to the class it must name.
     * The compat entries are as load-bearing as {@code main} and {@code client}: Jade,
     * Mod Menu, EMI, REI, and JEI are all discovered <em>through</em> this manifest, so dropping
     * an entry disables that integration without a crash, a warning, or a red test. (WTHIT
     * is the exception — it is discovered through {@code waila_plugins.json} at the resource
     * root, guarded separately by {@code WthitDiscoveryTest}.)
     *
     * <p>JEI is discovered by {@code jei_mod_plugin} on Fabric, not by the {@code @JeiPlugin}
     * annotation, which is load-bearing on Forge/NeoForge only — {@code FabricPluginFinder}
     * resolves plugins solely through this entrypoint key.
     *
     * <p>{@code fabric-datagen} is the first of the four datagen anchors (mc-datagen): without it
     * {@code runDatagen} generates nothing and {@code verifyDatagenIdempotent} passes vacuously.
     */
    private static final Map<String, String> RUNTIME_ENTRYPOINTS = Map.of(
            "main", "com.rfizzle.mercantile.Mercantile",
            "client", "com.rfizzle.mercantile.client.MercantileClient",
            "modmenu", "com.rfizzle.mercantile.compat.modmenu.ModMenuIntegration",
            "emi", "com.rfizzle.mercantile.compat.emi.MercantileEmiPlugin",
            "rei_client", "com.rfizzle.mercantile.compat.rei.MercantileReiClientPlugin",
            "jei_mod_plugin", "com.rfizzle.mercantile.compat.jei.MercantileJeiPlugin",
            "jade", "com.rfizzle.mercantile.compat.jade.MercantileJadePlugin",
            "fabric-datagen", "com.rfizzle.mercantile.data.MercantileDataGenerator");

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
