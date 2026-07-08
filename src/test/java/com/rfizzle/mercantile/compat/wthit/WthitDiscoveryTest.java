package com.rfizzle.mercantile.compat.wthit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the WTHIT discovery wiring: Mercantile must advertise its probe-tooltip
 * plugin through the suite-standard {@code waila_plugins.json} sided split, not the
 * legacy {@code custom.wthit:plugins} key in {@code fabric.mod.json}.
 */
class WthitDiscoveryTest {

    private static final Gson GSON = new Gson();
    private static final String PKG = "com.rfizzle.mercantile.compat.wthit.";

    private static JsonObject readResource(String path) {
        try (InputStream stream = WthitDiscoveryTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "missing resource: " + path);
            return GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
        } catch (Exception e) {
            throw new AssertionError("failed to read " + path, e);
        }
    }

    @Test
    void manifestDeclaresSidedSplit() {
        JsonObject manifest = readResource("/waila_plugins.json");
        assertTrue(manifest.has("mercantile:wthit"), "manifest must key on mercantile:wthit");

        JsonObject plugin = manifest.getAsJsonObject("mercantile:wthit");
        JsonObject entrypoints = plugin.getAsJsonObject("entrypoints");
        assertEquals(PKG + "WthitCommonPlugin", entrypoints.get("common").getAsString());
        assertEquals(PKG + "WthitClientPlugin", entrypoints.get("client").getAsString());

        assertTrue(plugin.has("side"), "manifest must declare a side");
        assertTrue(plugin.getAsJsonObject("required").has("wthit"),
                "manifest must gate loading on the wthit requirement");
    }

    @Test
    void fabricModJsonHasNoLegacyWthitCustomKey() {
        JsonObject fabricModJson = readResource("/fabric.mod.json");
        if (fabricModJson.has("custom")) {
            assertFalse(fabricModJson.getAsJsonObject("custom").has("wthit:plugins"),
                    "fabric.mod.json must not discover WTHIT via the legacy custom.wthit:plugins key");
        }
    }
}
