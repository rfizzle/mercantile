package com.rfizzle.mercantile;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the manifest scan itself. The test classpath carries a {@code fabric.mod.json} for every
 * Fabric API submodule, so the scan must tolerate whatever those candidates contain and still find
 * Mercantile's own manifest wherever it falls in the enumeration.
 */
class ManifestTestSupportTest {

    @TempDir
    Path tempDir;

    private URL write(String name, String contents) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        return file.toUri().toURL();
    }

    private static Enumeration<URL> candidates(URL... urls) {
        return Collections.enumeration(List.of(urls));
    }

    @Test
    void findsMercantileManifestAmongOtherMods() throws IOException {
        URL other = write("other.json", "{\"id\":\"fabric-screen-handler-api-v1\"}");
        URL ours = write("ours.json", "{\"id\":\"mercantile\",\"name\":\"Mercantile\"}");

        JsonObject manifest = ManifestTestSupport.selectMercantileManifest(candidates(other, ours));

        assertEquals("mercantile", manifest.get("id").getAsString());
        assertEquals("Mercantile", manifest.get("name").getAsString());
    }

    @Test
    void malformedEarlierCandidateDoesNotMaskOurs() throws IOException {
        URL truncated = write("truncated.json", "{\"id\": ");
        URL ours = write("ours.json", "{\"id\":\"mercantile\"}");

        JsonObject manifest = ManifestTestSupport.selectMercantileManifest(candidates(truncated, ours));

        assertEquals("mercantile", manifest.get("id").getAsString(),
                "an unparseable candidate must be skipped, not abort the scan");
    }

    @Test
    void unreadableEarlierCandidateDoesNotMaskOurs() throws IOException {
        URL missing = tempDir.resolve("does-not-exist.json").toUri().toURL();
        URL ours = write("ours.json", "{\"id\":\"mercantile\"}");

        JsonObject manifest = ManifestTestSupport.selectMercantileManifest(candidates(missing, ours));

        assertEquals("mercantile", manifest.get("id").getAsString(),
                "a candidate that fails to open must be skipped, not abort the scan");
    }

    @Test
    void nonPrimitiveIdIsSkippedRatherThanThrowing() throws IOException {
        URL objectId = write("object-id.json", "{\"id\":{\"nested\":\"value\"}}");
        URL ours = write("ours.json", "{\"id\":\"mercantile\"}");

        JsonObject manifest = ManifestTestSupport.selectMercantileManifest(candidates(objectId, ours));

        assertEquals("mercantile", manifest.get("id").getAsString());
    }

    @Test
    void missingManifestFailsAndNamesTheSkippedCandidates() throws IOException {
        URL truncated = write("truncated.json", "{\"id\": ");
        URL other = write("other.json", "{\"id\":\"fabric-object-builder-api-v1\"}");

        AssertionError error = assertThrows(AssertionError.class,
                () -> ManifestTestSupport.selectMercantileManifest(candidates(truncated, other)));

        assertTrue(error.getMessage().contains("no fabric.mod.json with id 'mercantile'"),
                "failure must name the mod id it looked for: " + error.getMessage());
        assertTrue(error.getMessage().contains("truncated.json"),
                "failure must name the candidates it could not read: " + error.getMessage());
    }

    @Test
    void resolvesTheRealMercantileManifestFromTheClasspath() {
        JsonObject manifest = ManifestTestSupport.readMercantileManifest();

        assertEquals(Mercantile.MOD_ID, manifest.get("id").getAsString());
        assertTrue(manifest.has("entrypoints"), "the shipped manifest must declare entrypoints");
    }
}
