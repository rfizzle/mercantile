package com.rfizzle.mercantile.block;

import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.registry.MercantileRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class SentryPylonFuelClampTest {

    private static int savedMaxFuel;

    @BeforeAll
    static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // MercantileRegistry.register() depends on FabricItemGroup (creative tab),
        // which is excluded from testRuntimeClasspath. Skip the full register and
        // populate just the BE type field we need.
        if (MercantileRegistry.SENTRY_PYLON_BE == null) {
            BlockEntityType<SentryPylonBlockEntity> type = BlockEntityType.Builder
                    .of(SentryPylonBlockEntity::new, MercantileRegistry.SENTRY_PYLON)
                    .build(null);
            Field f = MercantileRegistry.class.getDeclaredField("SENTRY_PYLON_BE");
            f.setAccessible(true);
            f.set(null, type);
        }
    }

    @BeforeEach
    void snapshotConfig() {
        savedMaxFuel = MercantileConfig.get().pylonMaxFuel;
    }

    @AfterEach
    void restoreConfig() {
        MercantileConfig.get().pylonMaxFuel = savedMaxFuel;
    }

    private static SentryPylonBlockEntity newPylon() {
        return new SentryPylonBlockEntity(BlockPos.ZERO,
                MercantileRegistry.SENTRY_PYLON.defaultBlockState());
    }

    // --- setFuel clamps ---

    @Test
    void setFuelNegativeClampsToZero() {
        SentryPylonBlockEntity be = newPylon();
        be.setFuel(-5);
        assertEquals(0, be.getFuel(), "negative fuel should clamp to 0");
    }

    @Test
    void setFuelAboveMaxClampsToMax() {
        SentryPylonBlockEntity be = newPylon();
        int max = be.getMaxFuel();
        be.setFuel(max + 100);
        assertEquals(max, be.getFuel(), "over-max fuel should clamp to max");
    }

    @Test
    void setFuelZeroLeavesAtZero() {
        SentryPylonBlockEntity be = newPylon();
        be.setFuel(0);
        assertEquals(0, be.getFuel(), "setting to 0 should leave at 0");
    }

    @Test
    void setFuelRespectsConfigMaxChange() {
        SentryPylonBlockEntity be = newPylon();
        MercantileConfig.get().pylonMaxFuel = 4;
        be.setFuel(10);
        assertEquals(4, be.getFuel(),
                "setFuel(10) with config max=4 should clamp to 4");
    }

    // --- addFuel ---

    @Test
    void addFuelZeroReturnsFalse() {
        SentryPylonBlockEntity be = newPylon();
        assertFalse(be.addFuel(0), "addFuel(0) should return false");
        assertEquals(0, be.getFuel(), "fuel should remain 0");
    }

    @Test
    void addFuelNegativeReturnsFalse() {
        SentryPylonBlockEntity be = newPylon();
        assertFalse(be.addFuel(-1), "addFuel(-1) should return false");
        assertEquals(0, be.getFuel(), "fuel should remain 0");
    }

    @Test
    void addFuelWhenFullReturnsFalse() {
        SentryPylonBlockEntity be = newPylon();
        int max = be.getMaxFuel();
        be.setFuel(max);
        assertFalse(be.addFuel(1), "addFuel(1) at max should return false");
        assertEquals(max, be.getFuel(), "fuel should remain at max");
    }

    @Test
    void addFuelOneFromZeroSucceeds() {
        SentryPylonBlockEntity be = newPylon();
        assertTrue(be.addFuel(1), "addFuel(1) from 0 should return true");
        assertEquals(1, be.getFuel(), "fuel should become 1");
    }

    @Test
    void addFuelOverflowClampsToMax() {
        SentryPylonBlockEntity be = newPylon();
        int max = be.getMaxFuel();
        assertTrue(be.addFuel(max + 1000), "addFuel(very large) should return true while not at max");
        assertEquals(max, be.getFuel(), "fuel should clamp to max via setFuel");
    }

    // --- consumeFuel ---

    @Test
    void consumeFuelInsufficientReturnsFalse() {
        SentryPylonBlockEntity be = newPylon();
        be.setFuel(1);
        assertFalse(be.consumeFuel(2), "consumeFuel(2) with fuel=1 should return false");
        assertEquals(1, be.getFuel(), "fuel should be unchanged on failed consume");
    }

    @Test
    void consumeFuelZeroIsNoOpReturningTrue() {
        SentryPylonBlockEntity be = newPylon();
        be.setFuel(0);
        assertTrue(be.consumeFuel(0), "consumeFuel(0) should early-return true");
        assertEquals(0, be.getFuel());
    }

    @Test
    void consumeFuelExactSucceeds() {
        SentryPylonBlockEntity be = newPylon();
        be.setFuel(1);
        assertTrue(be.consumeFuel(1), "consumeFuel(1) with fuel=1 should return true");
        assertEquals(0, be.getFuel(), "fuel should become 0");
    }

    @Test
    void consumeFuelNegativeAmountIsNoOpReturningTrue() {
        SentryPylonBlockEntity be = newPylon();
        be.setFuel(5);
        assertTrue(be.consumeFuel(-3), "consumeFuel(-3) should early-return true");
        assertEquals(5, be.getFuel(), "fuel should be unchanged");
    }

    // --- getMaxFuel reflects current config ---

    @Test
    void getMaxFuelReflectsConfig() {
        SentryPylonBlockEntity be = newPylon();
        int original = MercantileConfig.get().pylonMaxFuel;
        try {
            MercantileConfig.get().pylonMaxFuel = 12;
            assertEquals(12, be.getMaxFuel(), "max fuel should follow config");
        } finally {
            MercantileConfig.get().pylonMaxFuel = original;
        }
    }
}
