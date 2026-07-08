package com.rfizzle.mercantile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 resource-contract guard for {@code en_us.json}. Pins the two suite
 * conventions this file has to satisfy so a future key can't quietly drift out
 * of them:
 *
 * <ul>
 *   <li>DESIGN-SYSTEM §10 — every key is namespaced by the <em>surface</em> it
 *       renders on. Bare {@code mercantile.<concept>.*} prefixes are banned.</li>
 *   <li>DESIGN-SYSTEM §3 — every chat / action-bar line the mod pushes carries
 *       the ✦ marker (⚠ for a blocked or destructive action).</li>
 * </ul>
 */
class LangKeyPrefixContractTest {

    private static final String RESOURCE = "/assets/mercantile/lang/en_us.json";
    private static final Path SOURCE =
            Path.of("src/main/resources/assets/mercantile/lang/en_us.json");

    /**
     * The surface prefixes DESIGN-SYSTEM §10 sanctions, plus the vanilla-mandated
     * {@code block.} / {@code item.} / {@code enchantment.} / {@code itemGroup.}
     * registry names the game itself resolves.
     */
    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "config.", "command.", "hud.", "gui.", "tooltip.", "message.",
            "notification.", "advancements.", "info.", "key.", "stat.",
            "block.", "item.", "enchantment.", "itemGroup.");

    /**
     * Continuation lines of the ✦-marked {@code message.mercantile.contract.details.header}
     * block: they print together as one right-click contract readout, so the marker
     * sits on the header alone rather than repeating down every line.
     */
    private static final Set<String> UNMARKED_CONTINUATION_LINES = Set.of(
            "message.mercantile.contract.details.request",
            "message.mercantile.contract.details.payment",
            "message.mercantile.contract.details.location",
            "message.mercantile.contract.details.remaining",
            "message.mercantile.contract.details.expired");

    private static JsonObject lang() {
        try (InputStream in = LangKeyPrefixContractTest.class.getResourceAsStream(RESOURCE)) {
            String json = in != null
                    ? new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(SOURCE, StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not load en_us.json", e);
        }
    }

    @Test
    void everyKeyUsesAnAllowedSurfacePrefix() {
        List<String> offenders = new ArrayList<>();
        for (String key : lang().keySet()) {
            if (ALLOWED_PREFIXES.stream().noneMatch(key::startsWith)) {
                offenders.add(key);
            }
        }
        assertTrue(offenders.isEmpty(),
                "Lang keys outside the DESIGN-SYSTEM §10 surface vocabulary: " + offenders);
    }

    @Test
    void noKeyUsesARetiredBareConceptPrefix() {
        List<String> offenders = new ArrayList<>();
        for (String key : lang().keySet()) {
            if (key.startsWith("mercantile.")) {
                offenders.add(key);
            }
        }
        assertTrue(offenders.isEmpty(),
                "Bare mercantile.<concept>.* keys must be reclassified onto a surface prefix: " + offenders);
    }

    @Test
    void everyPushedLineCarriesTheSuiteMarker() {
        JsonObject lang = lang();
        List<String> offenders = new ArrayList<>();
        for (String key : lang.keySet()) {
            if (!key.startsWith("message.") && !key.startsWith("notification.")) continue;
            if (UNMARKED_CONTINUATION_LINES.contains(key)) continue;
            String value = lang.get(key).getAsString();
            if (!value.startsWith("✦") && !value.startsWith("⚠")) {
                offenders.add(key);
            }
        }
        assertTrue(offenders.isEmpty(),
                "Pushed chat/action-bar lines missing the ✦ (or ⚠) suite marker: " + offenders);
    }
}
