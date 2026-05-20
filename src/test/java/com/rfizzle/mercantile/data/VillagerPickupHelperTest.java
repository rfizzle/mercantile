package com.rfizzle.mercantile.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VillagerPickupHelperTest {

    @Test
    void currentDataVersionIsOne() {
        assertEquals(1, VillagerPickupHelper.CURRENT_DATA_VERSION,
                "Initial data version should be 1");
    }
}
