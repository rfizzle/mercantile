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
    void v4FileGainsGratitudeGiftFieldsAtDefaults() {
        JsonObject raw = parse("""
                {
                  "configVersion": 4,
                  "enableMarketDay": false
                }
                """);

        boolean migrated = ConfigMigrator.migrate(raw);
        MercantileConfig defaults = new MercantileConfig();

        assertTrue(migrated, "a v4 file must migrate to v5");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
        assertEquals(defaults.enableGratitudeGifts, raw.get("enableGratitudeGifts").getAsBoolean());
        assertEquals(defaults.gratitudeGiftsPerDay, raw.get("gratitudeGiftsPerDay").getAsInt());
        // Existing fields are carried forward untouched.
        assertFalse(raw.get("enableMarketDay").getAsBoolean());
    }

    @Test
    void v5FileGainsNitwitRehabFieldsAtDefaults() {
        JsonObject raw = parse("""
                {
                  "configVersion": 5,
                  "enableGratitudeGifts": false
                }
                """);

        boolean migrated = ConfigMigrator.migrate(raw);
        MercantileConfig defaults = new MercantileConfig();

        assertTrue(migrated, "a v5 file must migrate to v6");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
        assertEquals(defaults.enableNitwitRehab, raw.get("enableNitwitRehab").getAsBoolean());
        assertEquals(defaults.nitwitRehabEmeraldCost, raw.get("nitwitRehabEmeraldCost").getAsInt());
        // Existing fields are carried forward untouched.
        assertFalse(raw.get("enableGratitudeGifts").getAsBoolean());
    }

    @Test
    void v6FileGainsMemorialFearFieldsAtDefaults() {
        JsonObject raw = parse("""
                {
                  "configVersion": 6,
                  "enableNitwitRehab": false
                }
                """);

        boolean migrated = ConfigMigrator.migrate(raw);
        MercantileConfig defaults = new MercantileConfig();

        assertTrue(migrated, "a v6 file must migrate to v7");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
        assertEquals(defaults.enableMemorials, raw.get("enableMemorials").getAsBoolean());
        assertEquals(defaults.enableMourning, raw.get("enableMourning").getAsBoolean());
        assertEquals(defaults.enableFearMarkup, raw.get("enableFearMarkup").getAsBoolean());
        assertEquals(defaults.fearKillThreshold, raw.get("fearKillThreshold").getAsInt());
        assertEquals(defaults.fearKillWindowMinutes, raw.get("fearKillWindowMinutes").getAsInt());
        assertEquals(defaults.fearMarkupPercent, raw.get("fearMarkupPercent").getAsInt());
        assertEquals(defaults.fearMarkupDurationDays, raw.get("fearMarkupDurationDays").getAsInt());
        // Existing fields are carried forward untouched.
        assertFalse(raw.get("enableNitwitRehab").getAsBoolean());
    }

    @Test
    void v7FileGainsTradePinningFieldsAtDefaults() {
        JsonObject raw = parse("""
                {
                  "configVersion": 7,
                  "enableMemorials": false
                }
                """);

        boolean migrated = ConfigMigrator.migrate(raw);
        MercantileConfig defaults = new MercantileConfig();

        assertTrue(migrated, "a v7 file must migrate to v8");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
        assertEquals(defaults.enableTradePinning, raw.get("enableTradePinning").getAsBoolean());
        assertEquals(defaults.maxPinnedTradesPerPlayer, raw.get("maxPinnedTradesPerPlayer").getAsInt());
        assertEquals(defaults.pinRestockNotifyRange, raw.get("pinRestockNotifyRange").getAsInt());
        // Existing fields are carried forward untouched.
        assertFalse(raw.get("enableMemorials").getAsBoolean());
    }

    @Test
    void v8FileGainsWorkOrderFieldsAtDefaults() {
        JsonObject raw = parse("""
                {
                  "configVersion": 8,
                  "enableTradePinning": false
                }
                """);

        boolean migrated = ConfigMigrator.migrate(raw);
        MercantileConfig defaults = new MercantileConfig();

        assertTrue(migrated, "a v8 file must migrate to v9");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
        assertEquals(defaults.enableWorkOrders, raw.get("enableWorkOrders").getAsBoolean());
        assertEquals(defaults.workOrderEmeraldCost, raw.get("workOrderEmeraldCost").getAsInt());
        // Existing fields are carried forward untouched.
        assertFalse(raw.get("enableTradePinning").getAsBoolean());
    }

    @Test
    void v9FileGainsContractFieldsAtDefaults() {
        JsonObject raw = parse("""
                {
                  "configVersion": 9,
                  "enableWorkOrders": false
                }
                """);

        boolean migrated = ConfigMigrator.migrate(raw);
        MercantileConfig defaults = new MercantileConfig();

        assertTrue(migrated, "a v9 file must migrate to v10");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
        assertEquals(defaults.enableContracts, raw.get("enableContracts").getAsBoolean());
        assertEquals(defaults.contractOfferChance, raw.get("contractOfferChance").getAsInt());
        assertEquals(defaults.contractPaymentScale, raw.get("contractPaymentScale").getAsInt());
        assertEquals(defaults.contractRepGain, raw.get("contractRepGain").getAsInt());
        assertEquals(defaults.contractRepPerDay, raw.get("contractRepPerDay").getAsInt());
        assertEquals(defaults.contractDeadlineDays, raw.get("contractDeadlineDays").getAsInt());
        // Existing fields are carried forward untouched.
        assertFalse(raw.get("enableWorkOrders").getAsBoolean());
    }

    @Test
    void v10FileGainsTierChangeMessageFieldAtDefault() {
        JsonObject raw = parse("""
                {
                  "configVersion": 10,
                  "enableReputationHud": false
                }
                """);

        boolean migrated = ConfigMigrator.migrate(raw);
        MercantileConfig defaults = new MercantileConfig();

        assertTrue(migrated, "a v10 file must migrate to v11");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
        assertEquals(defaults.enableTierChangeMessages, raw.get("enableTierChangeMessages").getAsBoolean());
        // Existing fields are carried forward untouched.
        assertFalse(raw.get("enableReputationHud").getAsBoolean());
    }

    @Test
    void tierChangeMigrationPreservesExplicitValue() {
        JsonObject raw = parse("""
                {
                  "configVersion": 10,
                  "enableTierChangeMessages": false
                }
                """);

        ConfigMigrator.migrate(raw);

        assertFalse(raw.get("enableTierChangeMessages").getAsBoolean(),
                "an explicitly set enableTierChangeMessages must not be overwritten by the migration");
    }

    @Test
    void contractMigrationPreservesExplicitValues() {
        JsonObject raw = parse("""
                {
                  "configVersion": 9,
                  "enableContracts": false,
                  "contractRepGain": 7
                }
                """);

        ConfigMigrator.migrate(raw);

        assertFalse(raw.get("enableContracts").getAsBoolean(),
                "an explicitly set enableContracts must not be overwritten by the migration");
        assertEquals(7, raw.get("contractRepGain").getAsInt());
        assertEquals(new MercantileConfig().contractOfferChance, raw.get("contractOfferChance").getAsInt(),
                "the absent field must still be seeded at its default alongside preserved keys");
    }

    @Test
    void workOrderMigrationPreservesExplicitValues() {
        JsonObject raw = parse("""
                {
                  "configVersion": 8,
                  "enableWorkOrders": false
                }
                """);

        ConfigMigrator.migrate(raw);

        assertFalse(raw.get("enableWorkOrders").getAsBoolean(),
                "an explicitly set enableWorkOrders must not be overwritten by the migration");
        assertEquals(new MercantileConfig().workOrderEmeraldCost, raw.get("workOrderEmeraldCost").getAsInt(),
                "the absent field must still be seeded at its default alongside preserved keys");
    }

    @Test
    void tradePinningMigrationPreservesExplicitValues() {
        JsonObject raw = parse("""
                {
                  "configVersion": 7,
                  "enableTradePinning": false,
                  "maxPinnedTradesPerPlayer": 5
                }
                """);

        ConfigMigrator.migrate(raw);

        assertFalse(raw.get("enableTradePinning").getAsBoolean(),
                "an explicitly set enableTradePinning must not be overwritten by the migration");
        assertEquals(5, raw.get("maxPinnedTradesPerPlayer").getAsInt());
        assertEquals(new MercantileConfig().pinRestockNotifyRange, raw.get("pinRestockNotifyRange").getAsInt(),
                "the absent field must still be seeded at its default alongside preserved keys");
    }

    @Test
    void memorialFearMigrationPreservesExplicitValues() {
        JsonObject raw = parse("""
                {
                  "configVersion": 6,
                  "enableMemorials": false,
                  "fearMarkupPercent": 60
                }
                """);

        ConfigMigrator.migrate(raw);

        assertFalse(raw.get("enableMemorials").getAsBoolean(),
                "an explicitly set enableMemorials must not be overwritten by the migration");
        assertEquals(60, raw.get("fearMarkupPercent").getAsInt());
    }

    @Test
    void nitwitRehabMigrationPreservesExplicitValues() {
        JsonObject raw = parse("""
                {
                  "configVersion": 5,
                  "enableNitwitRehab": false,
                  "nitwitRehabEmeraldCost": 32
                }
                """);

        ConfigMigrator.migrate(raw);

        assertFalse(raw.get("enableNitwitRehab").getAsBoolean(),
                "an explicitly set enableNitwitRehab must not be overwritten by the migration");
        assertEquals(32, raw.get("nitwitRehabEmeraldCost").getAsInt());
    }

    @Test
    void gratitudeGiftMigrationPreservesExplicitValues() {
        JsonObject raw = parse("""
                {
                  "configVersion": 4,
                  "enableGratitudeGifts": false,
                  "gratitudeGiftsPerDay": 3
                }
                """);

        ConfigMigrator.migrate(raw);

        assertFalse(raw.get("enableGratitudeGifts").getAsBoolean(),
                "an explicitly set enableGratitudeGifts must not be overwritten by the migration");
        assertEquals(3, raw.get("gratitudeGiftsPerDay").getAsInt());
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
