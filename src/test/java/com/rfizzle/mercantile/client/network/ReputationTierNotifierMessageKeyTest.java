package com.rfizzle.mercantile.client.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the tier-change hint keys that {@code ReputationTierNotifier} builds
 * dynamically. The notifier picks a base key ({@code message.mercantile.tier_up}
 * or {@code message.mercantile.tier_down}) and translates either that key or the
 * same key with a {@code .hint} suffix appended, so the swap is invisible to the
 * compiler — a rename that orphaned any of the four keys would ship a raw
 * translation key to players. This test fails loudly if that happens.
 */
class ReputationTierNotifierMessageKeyTest {

    private static JsonObject lang;

    @BeforeAll
    static void loadLang() throws Exception {
        try (Reader reader = new InputStreamReader(
                ReputationTierNotifierMessageKeyTest.class.getResourceAsStream(
                        "/assets/mercantile/lang/en_us.json"),
                StandardCharsets.UTF_8)) {
            lang = new Gson().fromJson(reader, JsonObject.class);
        }
        assertNotNull(lang, "en_us.json must be on the test classpath");
    }

    @Test
    void tierBaseKeysExistWithSingleArg() {
        // Component.translatable(baseKey, newTier.displayName()) — one %s.
        for (String base : new String[]{
                "message.mercantile.tier_up", "message.mercantile.tier_down"}) {
            assertTrue(lang.has(base), "missing lang key: " + base);
            assertEquals(1, countFormatArgs(lang.get(base).getAsString()),
                    base + " must carry exactly one %s (the tier name)");
        }
    }

    @Test
    void tierHintKeysExistWithTwoArgs() {
        // Component.translatable(baseKey + ".hint", displayName, keyName) — two %s.
        for (String hint : new String[]{
                "message.mercantile.tier_up.hint", "message.mercantile.tier_down.hint"}) {
            assertTrue(lang.has(hint), "missing lang key: " + hint);
            assertEquals(2, countFormatArgs(lang.get(hint).getAsString()),
                    hint + " must carry two %s (tier name, then keybind)");
        }
    }

    private static int countFormatArgs(String value) {
        return value.split("%s", -1).length - 1;
    }
}
