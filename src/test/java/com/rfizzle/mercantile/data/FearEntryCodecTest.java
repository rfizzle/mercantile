package com.rfizzle.mercantile.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FearEntryCodecTest {

    @Test
    void defaultsAreNotNotified() {
        FearEntry entry = new FearEntry();
        assertFalse(entry.isNotified());
        assertEquals(-1L, entry.getFearStartGameTime());
        assertTrue(entry.getRecentKillTimes().isEmpty());
    }

    @Test
    void notifiedFlagRoundTrips() {
        FearEntry original = new FearEntry(List.of(100L, 200L), 500L, true);

        JsonElement encoded = FearEntry.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        FearEntry decoded = FearEntry.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertTrue(decoded.isNotified());
        assertEquals(500L, decoded.getFearStartGameTime());
        assertEquals(List.of(100L, 200L), decoded.getRecentKillTimes());
    }

    @Test
    void legacyDataWithoutNotifiedDefaultsFalse() {
        // A save written before the notice feature has no "notified" field; it must load clean
        // and default to un-notified so the one-time message can still fire once.
        JsonObject json = new JsonObject();
        json.addProperty("fearStartGameTime", 42L);

        FearEntry decoded = FearEntry.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertFalse(decoded.isNotified());
        assertEquals(42L, decoded.getFearStartGameTime());
    }
}
