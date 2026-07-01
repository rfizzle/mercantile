package com.rfizzle.mercantile.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VillagerPickupHelperTest {

    @Test
    void currentVersionIsReadable() {
        assertTrue(VillagerPickupHelper.isReadable(VillagerPickupHelper.CURRENT_DATA_VERSION),
                "a head from the current schema must be restorable");
    }

    @Test
    void olderVersionsAreReadable() {
        assertTrue(VillagerPickupHelper.isReadable(VillagerPickupHelper.CURRENT_DATA_VERSION - 1),
                "a head from an older schema must still be restorable");
        assertTrue(VillagerPickupHelper.isReadable(0),
                "a pre-versioning head (version 0) must still be restorable");
    }

    @Test
    void futureVersionsAreRefused() {
        assertFalse(VillagerPickupHelper.isReadable(VillagerPickupHelper.CURRENT_DATA_VERSION + 1),
                "a head from a newer schema must be refused, not loaded blind");
        assertFalse(VillagerPickupHelper.isReadable(99),
                "a head from a far-future schema must be refused");
    }
}
