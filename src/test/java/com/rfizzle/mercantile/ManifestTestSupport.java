package com.rfizzle.mercantile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Shared lookup for Mercantile's own {@code fabric.mod.json} from a unit test.
 *
 * <p>The test classpath carries a {@code fabric.mod.json} for every Fabric API submodule —
 * {@code configurations.testRuntimeClasspath} excludes the {@code fabric-api} fat module but not
 * the submodules — so a plain {@code getResourceAsStream("/fabric.mod.json")} returns whichever
 * one happens to come first. The pick is arbitrary and shifts with classpath ordering, which makes
 * any assertion on the result unstable at best and silently vacuous at worst. Enumerate every
 * candidate and select the manifest by mod id instead.
 */
public final class ManifestTestSupport {

    private static final Gson GSON = new Gson();

    private ManifestTestSupport() {
    }

    /**
     * @return Mercantile's shipped manifest, never {@code null}
     * @throws AssertionError if the classpath carries no manifest with Mercantile's mod id
     */
    public static JsonObject readMercantileManifest() {
        Enumeration<URL> urls;
        try {
            urls = ManifestTestSupport.class.getClassLoader().getResources("fabric.mod.json");
        } catch (IOException e) {
            throw new AssertionError("failed to enumerate fabric.mod.json on the test classpath", e);
        }
        return selectMercantileManifest(urls);
    }

    /**
     * Scans candidate manifests for Mercantile's own. Split from the classpath lookup so the
     * selection behaviour is testable against a controlled candidate list.
     */
    static JsonObject selectMercantileManifest(Enumeration<URL> urls) {
        List<String> skipped = new ArrayList<>();
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            try (InputStream stream = url.openStream()) {
                JsonObject manifest = GSON.fromJson(
                        new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
                if (manifest != null && manifest.has("id")
                        && manifest.get("id").isJsonPrimitive()
                        && Mercantile.MOD_ID.equals(manifest.get("id").getAsString())) {
                    return manifest;
                }
            } catch (Exception e) {
                // A candidate we cannot read is another mod's problem, not ours — keep scanning so
                // an unparseable manifest earlier in the enumeration cannot mask Mercantile's own.
                skipped.add(url + " (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            }
        }

        throw new AssertionError("no fabric.mod.json with id '" + Mercantile.MOD_ID
                + "' on the test classpath; unreadable candidates skipped: " + skipped);
    }
}
