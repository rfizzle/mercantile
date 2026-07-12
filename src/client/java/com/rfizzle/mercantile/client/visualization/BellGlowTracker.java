package com.rfizzle.mercantile.client.visualization;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class BellGlowTracker {

    public static final int GLOW_DURATION_TICKS = 60; // 3 s @ 20 tps — the ring pulse

    // Brief expiry for the hold-to-glow refresh: rewritten every client tick while a bell is
    // held, so stowing the bell clears the outline within a couple of ticks (issue #161).
    public static final int HOLD_GLOW_DURATION_TICKS = 3;

    // Client-thread only, so a plain HashMap is safe: the ring writer (markGlowing) runs via the
    // payload receiver's context.client().execute(), and the hold writer (markHoldGlowing) runs via
    // ClientTickEvents.END_CLIENT_TICK — both dispatch on the render thread.
    private static final Map<UUID, Long> expiryByUuid = new HashMap<>();

    private BellGlowTracker() {
    }

    public static void markGlowing(UUID id, long nowTicks) {
        expiryByUuid.put(id, nowTicks + GLOW_DURATION_TICKS);
    }

    /**
     * Refreshes a villager's glow for the brief hold-to-glow window. Uses max-merge so a per-tick
     * hold refresh can only ever raise an expiry, never shorten a longer ring pulse ({@link
     * #markGlowing}) that is still running on the same villager.
     */
    public static void markHoldGlowing(UUID id, long nowTicks) {
        expiryByUuid.merge(id, nowTicks + HOLD_GLOW_DURATION_TICKS, Long::max);
    }

    public static boolean isGlowing(UUID id, long nowTicks) {
        Long expiry = expiryByUuid.get(id);
        if (expiry == null) return false;
        return nowTicks < expiry;
    }

    public static void tick(long nowTicks) {
        Iterator<Map.Entry<UUID, Long>> it = expiryByUuid.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (nowTicks >= entry.getValue()) {
                it.remove();
            }
        }
    }

    public static void clear() {
        expiryByUuid.clear();
    }

    public static int size() {
        return expiryByUuid.size();
    }
}
