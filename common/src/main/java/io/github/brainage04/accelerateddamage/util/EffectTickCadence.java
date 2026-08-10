package io.github.brainage04.accelerateddamage.util;

public final class EffectTickCadence {
    private EffectTickCadence() {
    }
    public static boolean crossesScheduledTick(int duration, int interval, int ticksAdvanced) {
        if (duration <= 0 || ticksAdvanced <= 0) {
            return false;
        }
        int activeTicksAdvanced = Math.min(ticksAdvanced, duration);
        return countScheduledTicks(duration, interval, activeTicksAdvanced, false) > 0;
    }


    public static int scheduledTick(int currentTick, int offset, boolean ascending) {
        return ascending ? currentTick + offset : currentTick - offset;
    }

    public static int countScheduledTicks(
            int currentTick,
            int interval,
            int ticksAdvanced,
            boolean ascending
    ) {
        int applications = 0;
        int safeInterval = Math.max(interval, 1);
        for (int offset = 0; offset < ticksAdvanced; offset++) {
            int tick = scheduledTick(currentTick, offset, ascending);
            if (Math.floorMod(tick, safeInterval) == 0) {
                applications++;
            }
        }
        return applications;
    }
}
