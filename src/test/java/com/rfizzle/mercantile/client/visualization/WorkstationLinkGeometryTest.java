package com.rfizzle.mercantile.client.visualization;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 unit coverage for {@link WorkstationLinkGeometry} (issue #174). The geometry has no Minecraft
 * types, so it tests without a client — the seam that lets a JUnit test assert the link motes, the
 * unbound-villager pulse, and the status markers are <em>forced</em> (the fix) past the 32-block cull,
 * which no gametest or fabric-loader tier can observe because none can build a {@code ClientLevel}.
 */
class WorkstationLinkGeometryTest {

    private static final double BASE_STEP = 0.6;
    private static final double MAX_STEP = 2.5;
    private static final double RENDER_RANGE_SQR = 64.0 * 64.0;
    private static final double VIEW_DOT_THRESHOLD = -0.35;
    private static final double PULSE_PERIOD = 12.0;
    private static final double PULSE_MIN_AMPLITUDE = 0.6;

    /** Recorded emission from the sink. */
    private record Emit(boolean force, double x, double y, double z) {
    }

    private static double distance(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Perpendicular distance from point {@code (px,py,pz)} to the line through {@code from → to}. */
    private static double distanceToSegmentLine(double fromX, double fromY, double fromZ,
                                                double toX, double toY, double toZ,
                                                double px, double py, double pz) {
        double dx = toX - fromX, dy = toY - fromY, dz = toZ - fromZ;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double ux = dx / len, uy = dy / len, uz = dz / len;
        double wx = px - fromX, wy = py - fromY, wz = pz - fromZ;
        double t = wx * ux + wy * uy + wz * uz;
        double cx = fromX + ux * t, cy = fromY + uy * t, cz = fromZ + uz * t;
        return distance(px, py, pz, cx, cy, cz);
    }

    // --- moteLine -----------------------------------------------------------

    @Test
    void moteLine_forcesEveryParticle() {
        List<Emit> out = new ArrayList<>();
        int count = WorkstationLinkGeometry.moteLine(
                0, 64, 0, 10, 64, 0, 0, 66, 0,
                BASE_STEP, MAX_STEP,
                (force, x, y, z, dx, dy, dz) -> out.add(new Emit(force, x, y, z)));

        assertEquals(out.size(), count, "returned count must match emissions");
        assertFalse(out.isEmpty(), "a 10-block line should emit motes");
        for (Emit e : out) {
            assertTrue(e.force(), "every link mote must be forced past the 32-block cull");
        }
    }

    @Test
    void moteLine_placesPointsOnSegment() {
        List<Emit> out = new ArrayList<>();
        WorkstationLinkGeometry.moteLine(
                3, 64, -5, 3, 70, 12, 0, 66, 0,
                BASE_STEP, MAX_STEP,
                (force, x, y, z, dx, dy, dz) -> out.add(new Emit(force, x, y, z)));

        assertFalse(out.isEmpty(), "a diagonal line should emit motes");
        for (Emit e : out) {
            assertEquals(0.0, distanceToSegmentLine(3, 64, -5, 3, 70, 12, e.x, e.y, e.z), 1.0e-9,
                    "each mote must sit on the villager → workstation line");
        }
    }

    @Test
    void moteLine_lodWidensSpacingWithDistance() {
        List<Emit> near = new ArrayList<>();
        WorkstationLinkGeometry.moteLine(
                0, 64, 0, 40, 64, 0, 20, 64, 0,
                BASE_STEP, MAX_STEP,
                (force, x, y, z, dx, dy, dz) -> near.add(new Emit(force, x, y, z)));

        List<Emit> far = new ArrayList<>();
        WorkstationLinkGeometry.moteLine(
                0, 64, 0, 40, 64, 0, 20, 200, 0,
                BASE_STEP, MAX_STEP,
                (force, x, y, z, dx, dy, dz) -> far.add(new Emit(force, x, y, z)));

        assertTrue(far.size() < near.size(),
                "a farther camera widens LOD spacing, emitting fewer motes for the same line");
    }

    @Test
    void moteLine_emptyForZeroLength() {
        List<Emit> out = new ArrayList<>();
        int count = WorkstationLinkGeometry.moteLine(
                5, 64, 5, 5, 64, 5, 0, 66, 0,
                BASE_STEP, MAX_STEP,
                (force, x, y, z, dx, dy, dz) -> out.add(new Emit(force, x, y, z)));

        assertEquals(0, count, "coincident endpoints emit nothing");
        assertTrue(out.isEmpty(), "a zero-length segment must not emit");
    }

    // --- emitCulledPoint (unbound pulse + status markers) -------------------

    @Test
    void emitCulledPoint_forcesWhenVisible() {
        List<Emit> out = new ArrayList<>();
        // Point 10 blocks ahead of an eye looking toward +X — in range, in cone.
        boolean emitted = WorkstationLinkGeometry.emitCulledPoint(
                10, 64, 0, 0, 64, 0, 1, 0, 0,
                RENDER_RANGE_SQR, VIEW_DOT_THRESHOLD,
                (force, x, y, z, dx, dy, dz) -> out.add(new Emit(force, x, y, z)));

        assertTrue(emitted, "a visible in-range point must emit");
        assertEquals(1, out.size(), "exactly one point is spawned");
        assertTrue(out.get(0).force(), "the marker/pulse point must be forced past the 32-block cull");
    }

    @Test
    void emitCulledPoint_dropsBeyondRange() {
        List<Emit> out = new ArrayList<>();
        // 70 blocks out (> 64) — inside the view cone but past render range.
        boolean emitted = WorkstationLinkGeometry.emitCulledPoint(
                70, 64, 0, 0, 64, 0, 1, 0, 0,
                RENDER_RANGE_SQR, VIEW_DOT_THRESHOLD,
                (force, x, y, z, dx, dy, dz) -> out.add(new Emit(force, x, y, z)));

        assertFalse(emitted, "a point past RENDER_RANGE must not emit");
        assertTrue(out.isEmpty(), "nothing is spawned beyond range");
    }

    @Test
    void emitCulledPoint_dropsOutsideViewCone() {
        List<Emit> out = new ArrayList<>();
        // In range but directly behind the eye (looking +X, point at -X).
        boolean emitted = WorkstationLinkGeometry.emitCulledPoint(
                -10, 64, 0, 0, 64, 0, 1, 0, 0,
                RENDER_RANGE_SQR, VIEW_DOT_THRESHOLD,
                (force, x, y, z, dx, dy, dz) -> out.add(new Emit(force, x, y, z)));

        assertFalse(emitted, "a point behind the camera must not emit");
        assertTrue(out.isEmpty(), "nothing is spawned outside the view cone");
    }

    // --- pulseVisible -------------------------------------------------------

    @Test
    void pulseVisible_trueAtPeak_falseAtTrough() {
        // sin peaks at a quarter period (gameTime = period/2 = 6) and troughs at 3/4 (18).
        assertTrue(WorkstationLinkGeometry.pulseVisible(6, PULSE_PERIOD, PULSE_MIN_AMPLITUDE),
                "the bright half of the cycle blinks the puff on");
        assertFalse(WorkstationLinkGeometry.pulseVisible(18, PULSE_PERIOD, PULSE_MIN_AMPLITUDE),
                "the trough of the cycle leaves the puff off");
    }
}
