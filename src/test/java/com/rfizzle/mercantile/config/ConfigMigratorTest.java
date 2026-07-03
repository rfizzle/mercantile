package com.rfizzle.mercantile.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigMigratorTest {

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void preVersioningFileMigratesToCurrent() {
        JsonObject raw = parse("""
                {
                  "enableVillagerPickup": false,
                  "pickupXpCost": 7
                }
                """);

        boolean migrated = ConfigMigrator.migrate(raw);

        assertTrue(migrated, "a file with no configVersion is treated as v0 and must migrate");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
        // Existing fields are carried forward untouched.
        assertFalse(raw.get("enableVillagerPickup").getAsBoolean());
        assertEquals(7, raw.get("pickupXpCost").getAsInt());
    }

    @Test
    void currentVersionFileIsUntouched() {
        JsonObject raw = parse("""
                {
                  "configVersion": %d,
                  "pickupXpCost": 5
                }
                """.formatted(ConfigMigrator.CURRENT_VERSION));

        boolean migrated = ConfigMigrator.migrate(raw);

        assertFalse(migrated, "an already-current file must not be migrated");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
        assertEquals(5, raw.get("pickupXpCost").getAsInt());
    }

    @Test
    void futureVersionFileIsNotDowngraded() {
        JsonObject raw = parse("""
                {
                  "configVersion": %d
                }
                """.formatted(ConfigMigrator.CURRENT_VERSION + 5));

        boolean migrated = ConfigMigrator.migrate(raw);

        assertFalse(migrated, "a file from a newer build must be left as-is, never downgraded");
        assertEquals(ConfigMigrator.CURRENT_VERSION + 5, raw.get("configVersion").getAsInt());
    }

    @Test
    void migrationIsIdempotent() {
        JsonObject raw = parse("{ \"pickupXpCost\": 3 }");

        assertTrue(ConfigMigrator.migrate(raw), "first pass upgrades the pre-versioning file");
        assertFalse(ConfigMigrator.migrate(raw), "second pass is a no-op — the file is now current");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
        assertEquals(3, raw.get("pickupXpCost").getAsInt());
    }

    @Test
    void v1FileGainsTribulationFieldsAtDefaults() {
        JsonObject raw = parse("""
                {
                  "configVersion": 1,
                  "pylonMaxGolems": 4
                }
                """);

        boolean migrated = ConfigMigrator.migrate(raw);
        MercantileConfig defaults = new MercantileConfig();

        assertTrue(migrated, "a v1 file must migrate to v2");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
        assertEquals(defaults.pylonTribulationGolemBonusPerTier, raw.get("pylonTribulationGolemBonusPerTier").getAsInt());
        assertEquals(defaults.pylonTribulationRadiusBonusPerTier, raw.get("pylonTribulationRadiusBonusPerTier").getAsInt());
        assertEquals(defaults.pylonTribulationMaxGolems, raw.get("pylonTribulationMaxGolems").getAsInt());
        // Existing fields are carried forward untouched.
        assertEquals(4, raw.get("pylonMaxGolems").getAsInt());
    }

    @Test
    void v2FileGainsMoodFieldsAtDefaults() {
        JsonObject raw = parse("""
                {
                  "configVersion": 2,
                  "enableReputation": false
                }
                """);

        boolean migrated = ConfigMigrator.migrate(raw);
        MercantileConfig defaults = new MercantileConfig();

        assertTrue(migrated, "a v2 file must migrate to v3");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
        assertEquals(defaults.enableMood, raw.get("enableMood").getAsBoolean());
        assertEquals(defaults.moodPriceModifierPercent, raw.get("moodPriceModifierPercent").getAsInt());
        assertEquals(defaults.moodRestockSpeedPercent, raw.get("moodRestockSpeedPercent").getAsInt());
        assertEquals(defaults.moodRecalcIntervalTicks, raw.get("moodRecalcIntervalTicks").getAsInt());
        assertEquals(defaults.moodAmbientParticles, raw.get("moodAmbientParticles").getAsBoolean());
        // Existing fields are carried forward untouched.
        assertFalse(raw.get("enableReputation").getAsBoolean());
    }

    @Test
    void v3FileGainsMarketDayFieldsAtDefaults() {
        JsonObject raw = parse("""
                {
                  "configVersion": 3,
                  "enableMood": false
                }
                """);

        boolean migrated = ConfigMigrator.migrate(raw);
        MercantileConfig defaults = new MercantileConfig();

        assertTrue(migrated, "a v3 file must migrate to v4");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
        assertEquals(defaults.enableMarketDay, raw.get("enableMarketDay").getAsBoolean());
        assertEquals(defaults.marketDayIntervalDays, raw.get("marketDayIntervalDays").getAsInt());
        assertEquals(defaults.marketDayDiscountPercent, raw.get("marketDayDiscountPercent").getAsInt());
        // Existing fields are carried forward untouched.
        assertFalse(raw.get("enableMood").getAsBoolean());
    }

    @Test
    void marketDayMigrationPreservesExplicitValues() {
        JsonObject raw = parse("""
                {
                  "configVersion": 3,
                  "enableMarketDay": false,
                  "marketDayIntervalDays": 3
                }
                """);

        ConfigMigrator.migrate(raw);

        assertFalse(raw.get("enableMarketDay").getAsBoolean(),
                "an explicitly set enableMarketDay must not be overwritten by the migration");
        assertEquals(3, raw.get("marketDayIntervalDays").getAsInt());
    }

    @Test
    void moodMigrationPreservesExplicitValues() {
        JsonObject raw = parse("""
                {
                  "configVersion": 2,
                  "enableMood": false,
                  "moodPriceModifierPercent": 12
                }
                """);

        ConfigMigrator.migrate(raw);

        assertFalse(raw.get("enableMood").getAsBoolean(),
                "an explicitly set enableMood must not be overwritten by the migration");
        assertEquals(12, raw.get("moodPriceModifierPercent").getAsInt());
    }

    @Test
    void nonNumericVersionTreatedAsPreVersioning() {
        JsonObject raw = parse("{ \"configVersion\": \"garbage\" }");

        boolean migrated = ConfigMigrator.migrate(raw);

        assertTrue(migrated, "a non-numeric configVersion is treated as v0 and must migrate");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
    }
}
