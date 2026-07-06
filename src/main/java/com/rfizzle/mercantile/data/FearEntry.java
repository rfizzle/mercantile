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
                            .forGetter(FearEntry::getFearStartGameTime),
                    Codec.BOOL.optionalFieldOf("notified", false)
                            .forGetter(FearEntry::isNotified)
            ).apply(instance, FearEntry::new)
    );

    private List<Long> recentKillTimes;
    private long fearStartGameTime;
    // Whether this player has already been told the village fears them, so the one-time
    // "prices have risen" notice fires only on the first feared trade for this village.
    private boolean notified;

    public FearEntry() {
        this(List.of(), -1L, false);
    }

    public FearEntry(List<Long> recentKillTimes, long fearStartGameTime, boolean notified) {
        this.recentKillTimes = new ArrayList<>(recentKillTimes);
        this.fearStartGameTime = fearStartGameTime;
        this.notified = notified;
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

    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
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
