package com.rfizzle.mercantile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

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

    /**
     * The test classpath carries a {@code fabric.mod.json} for every Fabric API submodule,
     * so a plain {@code getResourceAsStream} returns an arbitrary one. Enumerate them all and
     * select Mercantile's own manifest by mod id.
     */
    private static JsonObject readMercantileManifest() {
        try {
            Enumeration<URL> urls = GametestEntrypointTest.class.getClassLoader()
                    .getResources("fabric.mod.json");
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (InputStream stream = url.openStream()) {
                    JsonObject manifest = GSON.fromJson(
                            new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
                    if (manifest != null && manifest.has("id")
                            && Mercantile.MOD_ID.equals(manifest.get("id").getAsString())) {
                        return manifest;
                    }
                }
            }
        } catch (Exception e) {
            throw new AssertionError("failed to scan the classpath for fabric.mod.json", e);
        }
        throw new AssertionError("no fabric.mod.json with id '" + Mercantile.MOD_ID + "' on the test classpath");
    }

    @Test
    void shippedManifestDeclaresNoGametestEntrypoints() {
        JsonObject manifest = readMercantileManifest();
        JsonObject entrypoints = manifest.getAsJsonObject("entrypoints");
        assertNotNull(entrypoints, "fabric.mod.json must declare entrypoints");
        assertFalse(entrypoints.has("fabric-gametest"),
                "the shipped manifest must not declare fabric-gametest entrypoints — "
                        + "they belong to the mercantile-gametest manifest in src/gametest/resources");
    }

    @Test
    void shippedManifestKeepsItsRuntimeEntrypoints() {
        JsonObject entrypoints = readMercantileManifest().getAsJsonObject("entrypoints");
        assertEquals("com.rfizzle.mercantile.Mercantile",
                entrypoints.getAsJsonArray("main").get(0).getAsString());
        assertEquals("com.rfizzle.mercantile.client.MercantileClient",
                entrypoints.getAsJsonArray("client").get(0).getAsString());
    }
}
