package com.rfizzle.mercantile.client.visualization;

/**
 * Pure geometry for the bell-radius overlay (issue #162) — the point sampling, height lookup, and
 * culling for the held-bell ring and the boundary burst, with no {@code net.minecraft.*} types so it
 * unit-tests without a client. {@link BellRadiusRenderer} is the thin shell that feeds it the player's
 * position/eye/view and turns each emitted point into a real particle.
 *
 * <p>Every emission is forced ({@code emit(true, ...)}): the ring sits at the 48-block gathering
 * radius, beyond vanilla's 32-block ({@code distanceToSqr > 1024}) particle cull, so an unforced
 * spawn is silently dropped every tick. The forced flag ORs past that cull on the client.
 */
public final class BellRadiusGeometry {

    /** Sink for a generated particle point; the shell binds the particle options and forwards {@code force}. */
    @FunctionalInterface
    public interface ParticleSink {
        void emit(boolean force, double x, double y, double z, double dx, double dy, double dz);
    }

    /** World-surface height at a block column; the shell backs this with the level's heightmap. */
    @FunctionalInterface
    public interface HeightSampler {
        int surfaceY(int worldX, int worldZ);
    }

    private BellRadiusGeometry() {
    }

    /**
     * Emit this tick's window of forced ring particles: {@code particlesPerTick} points sampled around
     * the circle from {@code angleOffset}, each snapped to the terrain surface, dropped when their
     * height strays past {@code maxYDelta} of the player or when they fall outside the view cone.
     */
    public static void ringArc(
            double cx, double cy, double cz,
            double eyeX, double eyeY, double eyeZ,
            double viewX, double viewY, double viewZ,
            int radius, int circleSamples, int particlesPerTick, int angleOffset,
            double maxYDelta, double viewDotThreshold,
            HeightSampler heights, ParticleSink sink) {
        for (int i = 0; i < particlesPerTick; i++) {
            int idx = (angleOffset + i * (circleSamples / particlesPerTick)) % circleSamples;
            double angle = (idx / (double) circleSamples) * Math.PI * 2.0;
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            int surfaceY = heights.surfaceY((int) Math.floor(x), (int) Math.floor(z));
            double y = surfaceY + 0.2;
            if (Math.abs(y - cy) > maxYDelta) continue;
            if (!inViewCone(x, y, z, eyeX, eyeY, eyeZ, viewX, viewY, viewZ, viewDotThreshold)) continue;
            sink.emit(true, x, y, z, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Emit the forced boundary-burst ring around a rung bell: {@code burstParticles} points evenly
     * spaced around the circle centered on {@code (cx, cz)}, terrain-snapped, view-cone culled.
     */
    public static void boundaryBurst(
            double cx, double cz,
            double eyeX, double eyeY, double eyeZ,
            double viewX, double viewY, double viewZ,
            int radius, int burstParticles, double viewDotThreshold,
            HeightSampler heights, ParticleSink sink) {
        for (int i = 0; i < burstParticles; i++) {
            double angle = (i / (double) burstParticles) * Math.PI * 2.0;
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            int surfaceY = heights.surfaceY((int) Math.floor(x), (int) Math.floor(z));
            double y = surfaceY + 0.3;
            if (!inViewCone(x, y, z, eyeX, eyeY, eyeZ, viewX, viewY, viewZ, viewDotThreshold)) continue;
            sink.emit(true, x, y, z, 0.0, 0.05, 0.0);
        }
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
}
