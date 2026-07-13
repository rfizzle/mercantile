package com.rfizzle.mercantile.client.visualization;

/**
 * Pure geometry for the workstation-link overlay (issue #174) — the link-mote LOD line sampling, the
 * pulse timing, and the range/view-cone culling, with no {@code net.minecraft.*} types so it
 * unit-tests without a client. {@link WorkstationLinkRenderer} is the thin shell that feeds it the
 * player's eye/view and the anchor coordinates and turns each emitted point into a real particle.
 *
 * <p>Every emission is forced ({@code emit(true, ...)}): the overlay renders anchors out to
 * {@code RENDER_RANGE} (64 blocks), beyond vanilla's 32-block ({@code distanceToSqr > 1024})
 * particle cull, so an unforced spawn is silently dropped every tick for any anchor between 32 and
 * 64 blocks. The forced flag ORs past that cull on the client. This mirrors the bell-radius seam
 * ({@link BellRadiusGeometry}) — the same accepted side effect applies: forcing also bypasses the
 * "Minimal" particle graphics setting for these opt-in, bell-gated overlays.
 */
public final class WorkstationLinkGeometry {

    /** Sink for a generated particle point; the shell binds the particle options and forwards {@code force}. */
    @FunctionalInterface
    public interface ParticleSink {
        void emit(boolean force, double x, double y, double z, double dx, double dy, double dz);
    }

    private WorkstationLinkGeometry() {
    }

    /**
     * Sample forced link motes along the {@code from → to} segment and emit them through {@code sink},
     * returning the count spawned. LOD: the spacing widens with the camera's distance to the segment
     * midpoint (a far line isn't oversampled), scaled from {@code baseStep} up to {@code maxStep}.
     * Pure: the shell binds the profession-coloured {@code LinkMoteParticleOptions} in the sink, so the
     * force flag and LOD spacing unit-test without a client. A near-zero-length segment emits nothing.
     */
    public static int moteLine(
            double fromX, double fromY, double fromZ,
            double toX, double toY, double toZ,
            double eyeX, double eyeY, double eyeZ,
            double baseStep, double maxStep,
            ParticleSink sink) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-3) return 0;
        // LOD: scale step by distance from camera (segment midpoint).
        double midX = fromX + dx * 0.5;
        double midY = fromY + dy * 0.5;
        double midZ = fromZ + dz * 0.5;
        double camDist = distance(midX, midY, midZ, eyeX, eyeY, eyeZ);
        double step = Math.min(maxStep, baseStep * (1.0 + camDist / 16.0));
        int count = Math.max(1, (int) Math.floor(length / step));
        double invLen = 1.0 / length;
        double ux = dx * invLen;
        double uy = dy * invLen;
        double uz = dz * invLen;
        int spawned = 0;
        for (int i = 1; i <= count; i++) {
            double t = i * step;
            if (t > length) break;
            sink.emit(true, fromX + ux * t, fromY + uy * t, fromZ + uz * t, 0.0, 0.0, 0.0);
            spawned++;
        }
        return spawned;
    }

    /**
     * Emit one forced particle at {@code (x, y, z)} when it is both within {@code rangeSqr} of the eye
     * and inside the view cone, returning whether it was emitted. The single choke point for the
     * unbound-villager pulse and the workstation status markers: both cull identically, then spawn one
     * forced point. An off-screen anchor emits nothing (the marker path re-checks it next tick).
     */
    public static boolean emitCulledPoint(
            double x, double y, double z,
            double eyeX, double eyeY, double eyeZ,
            double viewX, double viewY, double viewZ,
            double rangeSqr, double viewDotThreshold,
            ParticleSink sink) {
        if (!withinRange(x, y, z, eyeX, eyeY, eyeZ, rangeSqr)) return false;
        if (!inViewCone(x, y, z, eyeX, eyeY, eyeZ, viewX, viewY, viewZ, viewDotThreshold)) return false;
        sink.emit(true, x, y, z, 0.0, 0.0, 0.0);
        return true;
    }

    /**
     * The unbound-villager pulse gate: true on the bright half of a sine cycle of {@code period} ticks
     * whose amplitude exceeds {@code minAmplitude}, so the puff blinks rather than streams every tick.
     */
    public static boolean pulseVisible(long gameTime, double period, double minAmplitude) {
        double phase = (gameTime % (long) (period * 2)) / period * Math.PI;
        return Math.sin(phase) > minAmplitude;
    }

    /** True when {@code (px, py, pz)} lies within {@code rangeSqr} of the eye. */
    static boolean withinRange(double px, double py, double pz,
                               double eyeX, double eyeY, double eyeZ,
                               double rangeSqr) {
        double dx = px - eyeX;
        double dy = py - eyeY;
        double dz = pz - eyeZ;
        return dx * dx + dy * dy + dz * dz <= rangeSqr;
    }

    /** True when the point lies within the front view cone (normalized eye→point dot with view &gt; threshold). */
    static boolean inViewCone(double px, double py, double pz,
                              double eyeX, double eyeY, double eyeZ,
                              double viewX, double viewY, double viewZ,
                              double threshold) {
        double tx = px - eyeX;
        double ty = py - eyeY;
        double tz = pz - eyeZ;
        double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (len < 1.0e-3) return true;
        double dot = (tx * viewX + ty * viewY + tz * viewZ) / len;
        return dot > threshold;
    }

    private static double distance(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
