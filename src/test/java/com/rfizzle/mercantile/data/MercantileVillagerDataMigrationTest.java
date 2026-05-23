package com.rfizzle.mercantile.data;

import com.rfizzle.mercantile.trade.OfferIdentityHash;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MercantileVillagerDataMigrationTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static MerchantOffer emeraldForApple() {
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1), 16, 1, 0.0f);
    }

    private static MerchantOffer wheatForEmerald() {
        return new MerchantOffer(
                new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD, 1), 16, 1, 0.0f);
    }

    @Test
    void legacyHashGetsRewrittenToNewFormat() {
        MerchantOffer offer = emeraldForApple();
        String legacy = OfferIdentityHash.computeLegacy(offer);
        String current = OfferIdentityHash.compute(offer);
        assertNotEquals(legacy, current, "Test premise: legacy and current hashes must differ");

        MercantileVillagerData data = new MercantileVillagerData(false, Set.of(legacy), false, false);
        data.migrateLockedTrades(List.of(offer));

        assertFalse(data.getLockedTrades().contains(legacy), "Legacy hash should be removed");
        assertTrue(data.getLockedTrades().contains(current), "Current hash should be present");
        assertEquals(1, data.getLockedTrades().size(), "Set size unchanged");
        assertTrue(data.isTradesMigrated());
    }

    @Test
    void isIdempotent() {
        MerchantOffer offer = emeraldForApple();
        String legacy = OfferIdentityHash.computeLegacy(offer);

        MercantileVillagerData data = new MercantileVillagerData(false, Set.of(legacy), false, false);
        data.migrateLockedTrades(List.of(offer));
        Set<String> afterFirst = new HashSet<>(data.getLockedTrades());
        assertTrue(data.isTradesMigrated(), "Flag must flip after first call");

        data.migrateLockedTrades(List.of(offer));
        assertEquals(afterFirst, data.getLockedTrades(), "Second migration must be a no-op");
    }

    @Test
    void noOpOnNewFormatHashes() {
        MerchantOffer offer = emeraldForApple();
        String current = OfferIdentityHash.compute(offer);

        MercantileVillagerData data = new MercantileVillagerData(false, Set.of(current), false, false);
        data.migrateLockedTrades(List.of(offer));

        assertTrue(data.getLockedTrades().contains(current));
        assertEquals(1, data.getLockedTrades().size(), "No extra entries should be created");
        assertTrue(data.isTradesMigrated());
    }

    @Test
    void mixedLegacyAndNewMigratesOnlyLegacy() {
        MerchantOffer a = emeraldForApple();
        MerchantOffer b = wheatForEmerald();
        String legacyA = OfferIdentityHash.computeLegacy(a);
        String currentA = OfferIdentityHash.compute(a);
        String currentB = OfferIdentityHash.compute(b);

        Set<String> seed = new HashSet<>();
        seed.add(legacyA);
        seed.add(currentB);
        MercantileVillagerData data = new MercantileVillagerData(false, seed, false, false);
        data.migrateLockedTrades(List.of(a, b));

        assertFalse(data.getLockedTrades().contains(legacyA), "Legacy hash must be converted");
        assertTrue(data.getLockedTrades().contains(currentA), "Converted current hash present");
        assertTrue(data.getLockedTrades().contains(currentB), "Already-new hash untouched");
        assertEquals(2, data.getLockedTrades().size(), "Total count preserved");
    }

    @Test
    void legacyHashNotMatchingCurrentOffersIsLeftAlone() {
        MerchantOffer stored = emeraldForApple();
        MerchantOffer different = wheatForEmerald();
        String legacyStored = OfferIdentityHash.computeLegacy(stored);

        // Migrate with an offer list that does NOT contain the stored offer.
        // Migration has no way to know what the new-format hash should be, so the entry
        // must remain as-is (safe default — preserve user data).
        MercantileVillagerData data = new MercantileVillagerData(false, Set.of(legacyStored), false, false);
        data.migrateLockedTrades(List.of(different));

        assertTrue(data.getLockedTrades().contains(legacyStored),
                "Unmatched legacy hash must be preserved");
        assertEquals(1, data.getLockedTrades().size());
        assertTrue(data.isTradesMigrated(), "Flag still flipped — migration ran");
    }

    @Test
    void alreadyMigratedFlagSkipsWork() {
        MerchantOffer offer = emeraldForApple();
        String legacy = OfferIdentityHash.computeLegacy(offer);

        // Pre-flag as migrated even though legacy hash is present.
        MercantileVillagerData data = new MercantileVillagerData(false, Set.of(legacy), false, true);
        data.migrateLockedTrades(List.of(offer));

        assertTrue(data.getLockedTrades().contains(legacy),
                "Flag short-circuits — legacy hash untouched");
        assertEquals(1, data.getLockedTrades().size());
    }

    @Test
    void emptyLockedTradesIsNoOp() {
        MerchantOffer offer = emeraldForApple();

        MercantileVillagerData data = new MercantileVillagerData(false, Set.of(), false, false);
        data.migrateLockedTrades(List.of(offer));

        assertTrue(data.getLockedTrades().isEmpty(), "lockedTrades remains empty");
        assertTrue(data.isTradesMigrated(), "Flag flipped — migration ran against a real offer list");
    }

    @Test
    void emptyOffersDoesNotFlipFlag() {
        MerchantOffer offer = emeraldForApple();
        String legacy = OfferIdentityHash.computeLegacy(offer);

        MercantileVillagerData data = new MercantileVillagerData(false, Set.of(legacy), false, false);
        data.migrateLockedTrades(List.of());

        assertFalse(data.isTradesMigrated(), "Empty-offer call must not consume the one-shot");
        assertTrue(data.getLockedTrades().contains(legacy), "Legacy hash preserved for next attempt");

        // A subsequent real call must still migrate.
        data.migrateLockedTrades(List.of(offer));
        assertTrue(data.isTradesMigrated());
        assertFalse(data.getLockedTrades().contains(legacy));
        assertTrue(data.getLockedTrades().contains(OfferIdentityHash.compute(offer)));
    }
}
