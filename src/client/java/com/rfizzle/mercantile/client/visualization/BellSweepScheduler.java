package com.rfizzle.mercantile.client.visualization;

import java.util.HashSet;
import java.util.Set;

/**
 * Pure budgeted scheduler for the client-side placed-bell discovery sweep (issue #163). Holds no
 * {@code net.minecraft.*} types, so it unit-tests without a client — {@link BellRadiusRenderer} is the
 * thin shell that turns each emitted chunk offset into a {@code ClientChunkCache.getChunkNow} lookup and
 * feeds discovered bell positions back via {@link #recordBell}.
 *
 * <p>A full pass walks every chunk in the {@code (2r+1)²} square centered on the player's chunk, at most
 * {@code chunkBudget} chunks per tick, in row-major order. Discovered bells accumulate into a private
 * set; the accumulator is promoted <em>wholesale</em> to {@link #publishedBells()} at the start of the
 * next pass, so a bell that was broken between passes disappears and a newly placed one appears within
 * one full pass (a few seconds at normal render distance). Publishing the whole pass at once — rather
 * than incrementally — is what makes removals correct.
 */
public final class BellSweepScheduler {

    private int cursor;
    private int passRadius = -1;
    private boolean sawFirstPass;
    private Set<Long> accumulating = new HashSet<>();
    private Set<Long> published = new HashSet<>();

    /** Row-major chunk count along one edge of the sweep square for radius {@code r}. */
    public static int side(int radius) {
        return 2 * radius + 1;
    }

    /** Total chunks in the sweep square for radius {@code r}. */
    public static int total(int radius) {
        return side(radius) * side(radius);
    }

    /** Chunk-X offset (relative to the player's chunk, in {@code [-r, r]}) for a row-major index. */
    public static int offsetX(int index, int radius) {
        return index % side(radius) - radius;
    }

    /** Chunk-Z offset (relative to the player's chunk, in {@code [-r, r]}) for a row-major index. */
    public static int offsetZ(int index, int radius) {
        return index / side(radius) - radius;
    }

    /**
     * Advance the sweep by one tick and return this tick's row-major chunk indices to scan (at most
     * {@code chunkBudget}). On the first call of a fresh pass, the previous pass's accumulated finds are
     * promoted to {@link #publishedBells()} and the accumulator is cleared. A change in {@code radius}
     * restarts the sweep (cursor and accumulator reset), keeping the last published set until the new
     * pass completes.
     */
    public int[] nextChunkIndices(int radius, int chunkBudget) {
        if (radius != passRadius) {
            passRadius = radius;
            cursor = 0;
            accumulating.clear();
            sawFirstPass = false;
        }
        int total = total(radius);
        if (cursor == 0) {
            if (sawFirstPass) {
                published = accumulating;
                accumulating = new HashSet<>();
            }
            sawFirstPass = true;
        }
        int count = Math.min(Math.max(chunkBudget, 0), total - cursor);
        int[] indices = new int[count];
        for (int i = 0; i < count; i++) {
            indices[i] = cursor + i;
        }
        cursor += count;
        if (cursor >= total) {
            cursor = 0;
        }
        return indices;
    }

    /** Record a bell discovered during the current pass (the shell passes {@code BlockPos.asLong()}). */
    public void recordBell(long packedPos) {
        accumulating.add(packedPos);
    }

    /**
     * The bells from the most recently completed pass — empty until the first pass finishes. Returns the
     * live internal set (no copy, so this stays allocation-free in the per-tick render path); it is
     * tick-thread-only state and callers must not mutate it or retain it across ticks.
     */
    public Set<Long> publishedBells() {
        return published;
    }

    /**
     * Drop all sweep state (called every tick the feature is enabled but no bell is held, and on
     * disconnect/world unload), clearing the sets in place rather than reallocating so the common idle
     * tick allocates nothing.
     */
    public void reset() {
        cursor = 0;
        passRadius = -1;
        sawFirstPass = false;
        accumulating.clear();
        published.clear();
    }
}
