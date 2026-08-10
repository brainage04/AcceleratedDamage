package io.github.brainage04.accelerateddamage.util;

public final class DamageAccelerationContext {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private DamageAccelerationContext() {
    }

    public static boolean enter() {
        boolean previous = ACTIVE.get();
        ACTIVE.set(true);
        return previous;
    }

    public static void exit(boolean previous) {
        if (previous) {
            ACTIVE.set(true);
        } else {
            ACTIVE.remove();
        }
    }

    public static boolean isActive() {
        return ACTIVE.get();
    }
}
