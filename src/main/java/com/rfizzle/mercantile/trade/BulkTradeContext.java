package com.rfizzle.mercantile.trade;

public final class BulkTradeContext {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private BulkTradeContext() {
    }

    public static void enter() {
        ACTIVE.set(true);
    }

    public static void exit() {
        ACTIVE.remove();
    }

    public static boolean isActive() {
        return ACTIVE.get();
    }
}
