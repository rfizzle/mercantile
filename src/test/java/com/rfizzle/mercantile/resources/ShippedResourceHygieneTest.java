package com.rfizzle.mercantile.resources;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the boundary between what ships and what only ever serves a test (mc-mod-testing,
 * "Every mod ships this guard").
 *
 * <p>Gametest fixtures — structure templates and bespoke data files — resolve through the
 * merged {@code ResourceManager} by namespace, so they work from any loaded mod's resource
 * root. That means they belong in the gametest source set, whose manifest declares a separate
 * {@code mercantile-gametest} mod that never enters the jar. Mercantile's suites run on Fabric's
 * {@code EMPTY_STRUCTURE} and ship no fixtures of their own today, so the known-fixture
 * assertion the model guard carries has nothing to name here; the two sweeps below catch the
 * first fixture somebody adds to the wrong root.
 *
 * <p>These assertions read the test classpath rather than the source tree, because the
 * classpath is what the jar is built from — it is the shipped artifact under test.
 */
class ShippedResourceHygieneTest {

    /**
     * A shipped resource root, located by anchoring on a file known to live at its top level.
     *
     * @param anchor  classpath path of the anchor file
     * @param markers entries that must exist directly under the resolved root, so that an
     *                anchor which moves into a subdirectory fails loudly instead of silently
     *                narrowing the walk to that subdirectory
     */
    private record ShippedRoot(String anchor, List<String> markers) {
    }

    private static final List<ShippedRoot> SHIPPED_ROOTS = List.of(
            new ShippedRoot("/mercantile.mixins.json",
                    List.of("fabric.mod.json", "data/mercantile", "assets/mercantile")),
            new ShippedRoot("/mercantile.client.mixins.json",
                    List.of("mercantile.client.mixins.json")));

    /** Path segment that marks a file as existing only to serve the gametest suite. */
    private static final String TEST_ONLY_SEGMENT = "gametest";

    /**
     * Structure templates are a gametest-only format here — Mercantile ships no structures of
     * its own — so the extension is disqualifying wherever it appears, not just under a
     * {@code gametest/} directory.
     */
    private static final String TEMPLATE_EXTENSION = ".snbt";

    @Test
    void noGametestPathSegment_anywhereInShippedResources() {
        List<String> offenders = scanShippedRoots(
                relative -> hasSegment(relative, TEST_ONLY_SEGMENT));

        assertTrue(offenders.isEmpty(),
                "shipped resources must not contain gametest-only files, but found "
                        + offenders.size() + ": " + offenders
                        + " — move them to src/gametest/resources/, which is on the "
                        + "runGametest classpath but never enters the jar");
    }

    /** Catches a template parked outside a {@code gametest/} directory, which the sweep above misses. */
    @Test
    void noStructureTemplates_anywhereInShippedResources() {
        List<String> offenders = scanShippedRoots(
                relative -> relative.getFileName().toString().endsWith(TEMPLATE_EXTENSION));

        assertTrue(offenders.isEmpty(),
                "shipped resources must not contain structure templates, but found "
                        + offenders.size() + ": " + offenders
                        + " — templates serve the gametest suite only and belong in "
                        + "src/gametest/resources/");
    }

    /** Walks every shipped root, collecting root-relative paths of files matching the rule. */
    private static List<String> scanShippedRoots(java.util.function.Predicate<Path> offending) {
        List<String> offenders = new ArrayList<>();

        for (Path root : shippedResourceRoots()) {
            try (Stream<Path> tree = Files.walk(root)) {
                tree.filter(Files::isRegularFile)
                        .map(root::relativize)
                        .filter(offending)
                        .map(Path::toString)
                        .forEach(offenders::add);
            } catch (Exception e) {
                fail("could not walk shipped resource root " + root, e);
            }
        }

        offenders.sort(String::compareTo);
        return offenders;
    }

    private static boolean hasSegment(Path relative, String segment) {
        for (Path part : relative) {
            if (segment.equals(part.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves every directory the shipped resources are processed into. Both the main and the
     * client source set contribute to the jar, so a guard that walked only one would miss a
     * fixture dropped into the other. The datagen output under {@code src/main/generated} is a
     * second {@code main} resources srcDir and is processed into the same root as the anchor.
     */
    private static List<Path> shippedResourceRoots() {
        List<Path> roots = new ArrayList<>();

        for (ShippedRoot shipped : SHIPPED_ROOTS) {
            URL anchor = ShippedResourceHygieneTest.class.getResource(shipped.anchor());
            if (anchor == null) {
                fail("could not locate " + shipped.anchor() + " on the test classpath — the "
                        + "anchor this guard uses to find a shipped resource root has moved");
            }
            if (!"file".equals(anchor.getProtocol())) {
                fail("expected the shipped resource root to be a directory on the test "
                        + "classpath, but " + shipped.anchor() + " resolved to " + anchor);
            }

            Path root;
            try {
                root = Path.of(anchor.toURI()).getParent();
            } catch (Exception e) {
                return fail("could not resolve a filesystem path for " + anchor, e);
            }

            for (String marker : shipped.markers()) {
                assertTrue(Files.exists(root.resolve(marker)),
                        "resolved shipped resource root " + root + " is missing expected entry '"
                                + marker + "' — the anchor " + shipped.anchor() + " has most "
                                + "likely moved into a subdirectory, which would silently narrow "
                                + "this guard's coverage");
            }

            roots.add(root);
        }

        return roots;
    }
}
