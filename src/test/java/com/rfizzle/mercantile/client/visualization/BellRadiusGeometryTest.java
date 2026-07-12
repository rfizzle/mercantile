package com.rfizzle.mercantile.client.visualization;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 unit coverage for {@link BellRadiusGeometry} (issue #162). The geometry has no Minecraft
 * types, so it tests without a client — the seam that lets a JUnit test assert the ring/burst
 * particles are <em>forced</em> (the fix) and correctly placed, which no gametest or fabric-loader
 * tier can observe because none can build a {@code ClientLevel}.
 */
class BellRadiusGeometryTest {

    private static final int RADIUS = 48;
    private static final int CIRCLE_SAMPLES = 256;
    private static final int PARTICLES_PER_TICK = 32;
    private static final int BURST_PARTICLES = 64;
    private static final double MAX_Y_DELTA = 16.0;
    private static final double VIEW_DOT_THRESHOLD = -0.35;

    /** Recorded emission from the sink. */
    private record Emit(boolean force, double x, double y, double z) {
    }

    /** Flat terrain at a fixed height, so the height cull is out of the way unless a test wants it. */
    private static BellRadiusGeometry.HeightSampler flat(int y) {
        return (wx, wz) -> y;
    }

    private static double radiusFrom(double cx, double cz, Emit e) {
        double dx = e.x - cx;
        double dz = e.z - cz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Recover the circle sample index a ring point was generated from, inverting the angle math. */
    private static int sampleIndex(double cx, double cz, Emit e) {
        double angle = Math.atan2(e.z - cz, e.x - cx);
        if (angle < 0) angle += Math.PI * 2.0;
        return (int) Math.round(angle / (Math.PI * 2.0) * CIRCLE_SAMPLES) % CIRCLE_SAMPLES;
    }

    // --- ringArc ------------------------------------------------------------

    @Test
    void ringArc_forcesEveryParticle() {
        List<Emit> out = new ArrayList<>();
        // Player at origin, looking straight down so the whole ring is inside the wide front cone.
        BellRadiusGeometry.ringArc(
                0, 64, 0, 0, 65.6, 0, 0, -1, 0,
                RADIUS, CIRCLE_SAMPLES, PARTICLES_PER_TICK, 0,
                MAX_Y_DELTA, VIEW_DOT_THRESHOLD,
                flat(64), (force, x, y, z, dx, dy, dz) -> out.add(new Emit(force, x, y, z)));

        assertFalse(out.isEmpty(), "looking down should emit ring particles");
        for (Emit e : out) {
            assertTrue(e.force(), "every ring particle must be forced past the 32-block cull");
        }
    }

    @Test
    void ringArc_placesPointsOnRadius() {
        List<Emit> out = new ArrayList<>();
        BellRadiusGeometry.ringArc(
                10, 64, -20, 10, 65.6, -20, 0, -1, 0,
                RADIUS, CIRCLE_SAMPLES, PARTICLES_PER_TICK, 0,
                MAX_Y_DELTA, VIEW_DOT_THRESHOLD,
                flat(64), (force, x, y, z, dx, dy, dz) -> out.add(new Emit(force, x, y, z)));

        for (Emit e : out) {
            assertEquals(RADIUS, radiusFrom(10, -20, e), 1.0e-9, "ring point must sit on the gathering radius");
        }
    }

    @Test
    void ringArc_emitsBudgetedWindowAtOffset() {
        // Looking straight down over flat terrain, nothing is culled, so the tick emits exactly the
        // budget — and the emitted points must land on the angleOffset-driven sample indices, which
        // fails if the index/budget math is ignored rather than merely capped by the loop bound.
        int offset = 3;
        List<Emit> out = new ArrayList<>();
        BellRadiusGeometry.ringArc(
                0, 64, 0, 0, 65.6, 0, 0, -1, 0,
                RADIUS, CIRCLE_SAMPLES, PARTICLES_PER_TICK, offset,
                MAX_Y_DELTA, VIEW_DOT_THRESHOLD,
                flat(64), (force, x, y, z, dx, dy, dz) -> out.add(new Emit(force, x, y, z)));

        assertEquals(PARTICLES_PER_TICK, out.size(),
                "an unculled tick emits exactly the per-tick budget");

        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < PARTICLES_PER_TICK; i++) {
            expected.add((offset + i * (CIRCLE_SAMPLES / PARTICLES_PER_TICK)) % CIRCLE_SAMPLES);
        }
        List<Integer> actual = new ArrayList<>();
        for (Emit e : out) {
            actual.add(sampleIndex(0, 0, e));
        }
        expected.sort(null);
        actual.sort(null);
        assertEquals(expected, actual,
                "emitted points must sit on the budgeted sample indices from angleOffset");
    }

    @Test
    void ringArc_cullsPointsOutsideViewCone() {
        List<Emit> straightDown = new ArrayList<>();
        BellRadiusGeometry.ringArc(
                0, 64, 0, 0, 65.6, 0, 0, -1, 0,
                RADIUS, CIRCLE_SAMPLES, PARTICLES_PER_TICK, 0,
                MAX_Y_DELTA, VIEW_DOT_THRESHOLD,
                flat(64), (force, x, y, z, dx, dy, dz) -> straightDown.add(new Emit(force, x, y, z)));

        // Looking level toward +X drops the ring behind the player, so fewer points survive the cone.
        List<Emit> level = new ArrayList<>();
        BellRadiusGeometry.ringArc(
                0, 64, 0, 0, 65.6, 0, 1, 0, 0,
                RADIUS, CIRCLE_SAMPLES, PARTICLES_PER_TICK, 0,
                MAX_Y_DELTA, VIEW_DOT_THRESHOLD,
                flat(64), (force, x, y, z, dx, dy, dz) -> level.add(new Emit(force, x, y, z)));

        assertTrue(level.size() < straightDown.size(),
                "a level gaze should cull the ring points behind the player");
    }

    @Test
    void ringArc_cullsPointsBeyondMaxYDelta() {
        List<Emit> out = new ArrayList<>();
        // Terrain sampled far below the player (delta 100 > MAX_Y_DELTA) drops every point.
        BellRadiusGeometry.ringArc(
                0, 164, 0, 0, 165.6, 0, 0, -1, 0,
                RADIUS, CIRCLE_SAMPLES, PARTICLES_PER_TICK, 0,
                MAX_Y_DELTA, VIEW_DOT_THRESHOLD,
                flat(64), (force, x, y, z, dx, dy, dz) -> out.add(new Emit(force, x, y, z)));

        assertTrue(out.isEmpty(), "points whose surface strays past MAX_Y_DELTA are dropped");
    }

    // --- boundaryBurst ------------------------------------------------------

    @Test
    void boundaryBurst_forcesEveryParticleOnRadius() {
        List<Emit> out = new ArrayList<>();
        BellRadiusGeometry.boundaryBurst(
                5, 5, 5, 65.6, 5, 0, -1, 0,
                RADIUS, BURST_PARTICLES, VIEW_DOT_THRESHOLD,
                flat(64), (force, x, y, z, dx, dy, dz) -> out.add(new Emit(force, x, y, z)));

        assertFalse(out.isEmpty(), "burst directly below the viewer should emit");
        assertTrue(out.size() <= BURST_PARTICLES, "burst must not exceed its particle count");
        for (Emit e : out) {
            assertTrue(e.force(), "every burst particle must be forced past the 32-block cull");
            assertEquals(RADIUS, radiusFrom(5, 5, e), 1.0e-9, "burst point must sit on the gathering radius");
        }
    }
}
