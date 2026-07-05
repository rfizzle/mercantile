package com.rfizzle.mercantile.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One player's fear state toward a single village: the recent kill timestamps inside the
 * activation window and, once the threshold was reached, the game time the markup started
 * (refreshed by every further kill). Pure data holder — the bookkeeping lives in
 * {@link com.rfizzle.mercantile.memorial.FearMath}.
 */
public class FearEntry {

    public static final Codec<FearEntry> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.LONG.listOf().optionalFieldOf("recentKillTimes", List.of())
                            .forGetter(FearEntry::getRecentKillTimes),
                    Codec.LONG.optionalFieldOf("fearStartGameTime", -1L)
                            .forGetter(FearEntry::getFearStartGameTime)
            ).apply(instance, FearEntry::new)
    );

    private List<Long> recentKillTimes;
    private long fearStartGameTime;

    public FearEntry() {
        this(List.of(), -1L);
    }

    public FearEntry(List<Long> recentKillTimes, long fearStartGameTime) {
        this.recentKillTimes = new ArrayList<>(recentKillTimes);
        this.fearStartGameTime = fearStartGameTime;
    }

    public List<Long> getRecentKillTimes() {
        return Collections.unmodifiableList(recentKillTimes);
    }

    public void setRecentKillTimes(List<Long> recentKillTimes) {
        this.recentKillTimes = new ArrayList<>(recentKillTimes);
    }

    public long getFearStartGameTime() {
        return fearStartGameTime;
    }

    public void setFearStartGameTime(long fearStartGameTime) {
        this.fearStartGameTime = fearStartGameTime;
    }

    /**
     * Most recent game time this entry saw activity (a kill or a markup activation);
     * {@code -1} for a blank entry. Eviction picks the least-recently-active victim, so an
     * active markup can't be laundered out of the map by touching many other villages —
     * and unlike map insertion order, this survives a codec round-trip.
     */
    public long lastActivityGameTime() {
        long last = fearStartGameTime;
        for (long time : recentKillTimes) {
            last = Math.max(last, time);
        }
        return last;
    }

    /** Whether the entry carries no live state: no active markup and no kill newer than the window. */
    public boolean isStale(long now, long windowTicks, long durationTicks) {
        boolean markupOver = fearStartGameTime < 0 || now - fearStartGameTime >= durationTicks;
        boolean killsStale = recentKillTimes.stream().allMatch(t -> t > now || now - t >= windowTicks);
        return markupOver && killsStale;
    }
}
