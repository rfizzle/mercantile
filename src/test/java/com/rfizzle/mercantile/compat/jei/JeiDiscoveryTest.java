package com.rfizzle.mercantile.compat.jei;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the {@code @JeiPlugin} annotation as cross-loader parity, not as Fabric discovery.
 * On Fabric, JEI resolves plugins solely through the {@code jei_mod_plugin} entrypoint —
 * {@code FabricPluginFinder} reads that key and nothing else, and JEI registers its own
 * built-in plugins the same way. That entrypoint is the load-bearing half, pinned by
 * {@link com.rfizzle.mercantile.GametestEntrypointTest} alongside the other viewers.
 *
 * <p>The annotation is what a Forge or NeoForge port would be discovered by, so it is kept and
 * guarded here: dropping it costs nothing today and silently breaks that port later.
 *
 * <p>The contract is asserted against the source text rather than by reflection: {@code jei} is
 * {@code modCompileOnly} and absent from the test runtime classpath, so loading the class would
 * fail on its supertype for the wrong reason. {@code GametestEntrypointTest} resolves the
 * manifest entrypoints against the source tree for the same reason.
 */
class JeiDiscoveryTest {

    private static final Path SOURCE =
            Path.of("src/client/java/com/rfizzle/mercantile/compat/jei/MercantileJeiPlugin.java");
    private static final String DECLARATION = "public class MercantileJeiPlugin";

    private static List<String> sourceLines() {
        assertTrue(Files.isRegularFile(SOURCE), "missing JEI plugin source: " + SOURCE.toAbsolutePath());
        try {
            return Files.readAllLines(SOURCE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("failed to read " + SOURCE, e);
        }
    }

    private static boolean isCommentLine(String trimmed) {
        return trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*");
    }

    private static String stripTrailingComment(String line) {
        int marker = line.indexOf("//");
        return (marker >= 0 ? line.substring(0, marker) : line).trim();
    }

    private static int declarationIndex(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!isCommentLine(line) && line.startsWith(DECLARATION)) {
                return i;
            }
        }
        throw new AssertionError("no '" + DECLARATION + "' declaration in " + SOURCE);
    }

    /**
     * Anchored to the annotation block immediately above the declaration rather than matched
     * anywhere in the file: a bare substring search passes on a commented-out annotation, which
     * is the regression this guard exists to catch. Comment lines are skipped rather than
     * treated as terminators, so a note placed above the class cannot false-fail the check.
     */
    @Test
    void pluginClassCarriesJeiPluginAnnotation() {
        List<String> lines = sourceLines();
        boolean annotated = false;

        for (int i = declarationIndex(lines) - 1; i >= 0; i--) {
            String line = stripTrailingComment(lines.get(i));
            if (line.isEmpty() || isCommentLine(line)) {
                continue;
            }
            if (!line.startsWith("@")) {
                break;
            }
            if (line.equals("@JeiPlugin") || line.equals("@mezz.jei.api.JeiPlugin")) {
                annotated = true;
                break;
            }
        }

        assertTrue(annotated,
                "MercantileJeiPlugin must carry @JeiPlugin on the class declaration — "
                        + "Fabric ignores it, but a Forge or NeoForge port is discovered by it");
    }
}
