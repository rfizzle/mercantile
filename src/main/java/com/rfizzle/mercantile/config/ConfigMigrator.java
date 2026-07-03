package com.rfizzle.mercantile.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.rfizzle.mercantile.Mercantile;

/**
 * Upgrades a config's raw JSON to the current schema before Gson deserializes it, so a renamed or
 * restructured field is carried forward rather than silently dropped by a lenient parse.
 *
 * <p>Migrations run only on the file-load path. A config built from {@code toJson}/{@code fromJson}
 * for the ModMenu working copy or the server&rarr;client sync is already current and must not be
 * re-migrated.
 */
final class ConfigMigrator {

    static final int CURRENT_VERSION = 4;

    @FunctionalInterface
    interface Migration {
        void apply(JsonObject json);
    }

    // Index i applies the v(i) -> v(i+1) transition. Append only; never reorder.
    private static final Migration[] MIGRATIONS = {
        // v0 -> v1: baseline. Files written before config versioning existed carry no configVersion,
        // read as v0, and are stamped configVersion=1 as-is (their fields already match the v1 schema).
        json -> {},
        // v1 -> v2: Sentry Pylon × Tribulation scaling fields, seeded at their defaults.
        json -> {
            MercantileConfig defaults = new MercantileConfig();
            addIfAbsent(json, "pylonTribulationGolemBonusPerTier", defaults.pylonTribulationGolemBonusPerTier);
            addIfAbsent(json, "pylonTribulationRadiusBonusPerTier", defaults.pylonTribulationRadiusBonusPerTier);
            addIfAbsent(json, "pylonTribulationMaxGolems", defaults.pylonTribulationMaxGolems);
        },
        // v2 -> v3: villager mood system fields, seeded at their defaults.
        json -> {
            MercantileConfig defaults = new MercantileConfig();
            addIfAbsent(json, "enableMood", defaults.enableMood);
            addIfAbsent(json, "moodPriceModifierPercent", defaults.moodPriceModifierPercent);
            addIfAbsent(json, "moodRestockSpeedPercent", defaults.moodRestockSpeedPercent);
            addIfAbsent(json, "moodRecalcIntervalTicks", defaults.moodRecalcIntervalTicks);
            addIfAbsent(json, "moodAmbientParticles", defaults.moodAmbientParticles);
        },
        // v3 -> v4: market day fields, seeded at their defaults.
        json -> {
            MercantileConfig defaults = new MercantileConfig();
            addIfAbsent(json, "enableMarketDay", defaults.enableMarketDay);
            addIfAbsent(json, "marketDayIntervalDays", defaults.marketDayIntervalDays);
            addIfAbsent(json, "marketDayDiscountPercent", defaults.marketDayDiscountPercent);
        },
    };

    private static void addIfAbsent(JsonObject json, String key, boolean value) {
        if (!json.has(key)) {
            json.addProperty(key, value);
        }
    }

    private static void addIfAbsent(JsonObject json, String key, int value) {
        if (!json.has(key)) {
            json.addProperty(key, value);
        }
    }

    /**
     * Applies every migration from the file's version up to {@link #CURRENT_VERSION} in place.
     *
     * @return {@code true} if the JSON was upgraded (and should be persisted back), {@code false} if
     *         it was already current.
     */
    static boolean migrate(JsonObject json) {
        int version = readVersion(json);
        if (version >= CURRENT_VERSION) {
            return false;
        }
        boolean changed = false;
        for (int i = version; i < CURRENT_VERSION && i < MIGRATIONS.length; i++) {
            try {
                MIGRATIONS[i].apply(json);
                Mercantile.LOGGER.info("Migrated config from v{} to v{}", i, i + 1);
                changed = true;
            } catch (Exception e) {
                Mercantile.LOGGER.warn("Config migration v{} to v{} failed; skipping: {}", i, i + 1, e.getMessage());
            }
        }
        if (changed) {
            json.addProperty("configVersion", CURRENT_VERSION);
        }
        return changed;
    }

    // Missing or non-numeric configVersion is treated as v0 (pre-versioning).
    private static int readVersion(JsonObject json) {
        JsonElement element = json.get("configVersion");
        if (element != null && element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                return primitive.getAsInt();
            }
        }
        return 0;
    }

    private ConfigMigrator() {
    }
}
