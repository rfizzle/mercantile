package com.rfizzle.mercantile.block;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SentryPylonScanCadenceTest {

    // --- scanIntervalTicks: cadence stretches with radius, floored at the baseline ---

    @Test
    void intervalAtBaseRadiusIsBaseline() {
        assertEquals(40, SentryPylonBlockEntity.scanIntervalTicks(32),
                "radius 32 (the reference) keeps the 40-tick baseline");
    }

    @Test
    void intervalBelowBaseRadiusFloorsAtBaseline() {
        assertEquals(40, SentryPylonBlockEntity.scanIntervalTicks(4),
                "a small-radius pylon still scans no faster than the baseline");
        assertEquals(40, SentryPylonBlockEntity.scanIntervalTicks(16),
                "below the reference radius the interval never dips under the baseline");
    }

    @Test
    void intervalScalesLinearlyAboveBaseRadius() {
        assertEquals(80, SentryPylonBlockEntity.scanIntervalTicks(64),
                "double the radius doubles the interval");
        assertEquals(160, SentryPylonBlockEntity.scanIntervalTicks(128),
                "the max radius (128) scans a quarter as often as the baseline");
    }

    @Test
    void intervalIsMonotonicNonDecreasing() {
        int prev = SentryPylonBlockEntity.scanIntervalTicks(4);
        for (int radius = 5; radius <= 128; radius++) {
            int current = SentryPylonBlockEntity.scanIntervalTicks(radius);
            assertTrue(current >= prev,
                    "interval must never shrink as radius grows (radius=" + radius + ")");
            prev = current;
        }
    }

    // --- idleHostileCheckIntervalTicks: recheck cadence stretches with radius like the main scan ---

    @Test
    void idleIntervalAtBaseRadiusIsBaseline() {
        assertEquals(10, SentryPylonBlockEntity.idleHostileCheckIntervalTicks(32),
                "radius 32 (the reference) keeps the 10-tick recheck baseline");
    }

    @Test
    void idleIntervalBelowBaseRadiusFloorsAtBaseline() {
        assertEquals(10, SentryPylonBlockEntity.idleHostileCheckIntervalTicks(4),
                "a small-radius pylon still rechecks no faster than the baseline");
        assertEquals(10, SentryPylonBlockEntity.idleHostileCheckIntervalTicks(16),
                "below the reference radius the recheck interval never dips under the baseline");
    }

    @Test
    void idleIntervalScalesLinearlyAboveBaseRadius() {
        assertEquals(20, SentryPylonBlockEntity.idleHostileCheckIntervalTicks(64),
                "double the radius doubles the recheck interval");
        assertEquals(40, SentryPylonBlockEntity.idleHostileCheckIntervalTicks(128),
                "the max radius (128) rechecks a quarter as often as the baseline");
    }

    @Test
    void idleIntervalIsMonotonicNonDecreasing() {
        int prev = SentryPylonBlockEntity.idleHostileCheckIntervalTicks(4);
        for (int radius = 5; radius <= 128; radius++) {
            int current = SentryPylonBlockEntity.idleHostileCheckIntervalTicks(radius);
            assertTrue(current >= prev,
                    "recheck interval must never shrink as radius grows (radius=" + radius + ")");
            prev = current;
        }
    }

    // --- scanPhaseOffset: deterministic, in range, and well-spread ---

    @Test
    void phaseOffsetIsWithinInterval() {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                int offset = SentryPylonBlockEntity.scanPhaseOffset(new BlockPos(x, 64, z), 40);
                assertTrue(offset >= 0 && offset < 40,
                        "phase offset must land in [0, interval) at (" + x + "," + z + ") -> " + offset);
            }
        }
    }

    @Test
    void phaseOffsetIsDeterministic() {
        BlockPos pos = new BlockPos(123, 70, -456);
        assertEquals(SentryPylonBlockEntity.scanPhaseOffset(pos, 40),
                SentryPylonBlockEntity.scanPhaseOffset(pos, 40),
                "same position must always yield the same phase");
    }

    @Test
    void phaseOffsetSpreadsAdjacentPositions() {
        // A row of adjacent pylons (the worst case for low-bit packing) must not collapse onto a
        // single tick — the whole point of the stagger. Expect a healthy spread of distinct phases.
        Set<Integer> offsets = new HashSet<>();
        for (int x = 0; x < 40; x++) {
            offsets.add(SentryPylonBlockEntity.scanPhaseOffset(new BlockPos(x, 64, 0), 40));
        }
        assertTrue(offsets.size() >= 20,
                "40 adjacent pylons should scatter across many ticks, got " + offsets.size()
                        + " distinct phases");
    }
}
