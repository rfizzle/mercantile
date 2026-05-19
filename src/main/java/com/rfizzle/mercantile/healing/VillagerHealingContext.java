package com.rfizzle.mercantile.healing;

public final class VillagerHealingContext {
    private static boolean active;

    private VillagerHealingContext() {
    }

    public static void enter() {
        active = true;
    }

    public static void exit() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }
}
