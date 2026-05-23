package com.rfizzle.mercantile.client.visualization;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class BellGlowTracker {

    public static final int GLOW_DURATION_TICKS = 60; // 3 s @ 20 tps

    // Client-thread only. Receivers route through context.client().execute(),
    // so all writes/reads happen on the render thread.
    private static final Map<UUID, Long> expiryByUuid = new HashMap<>();

    private BellGlowTracker() {
    }

    public static void markGlowing(UUID id, long nowTicks) {
        expiryByUuid.put(id, nowTicks + GLOW_DURATION_TICKS);
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
