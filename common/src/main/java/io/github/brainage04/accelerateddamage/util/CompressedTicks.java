package io.github.brainage04.accelerateddamage.util;

/** Tick arithmetic of the compressed timeline described in {@link VirtualTime}. */
public final class CompressedTicks {
    // Entity.baseTick hard-codes the 20-tick fire damage interval (Entity.java:558 in 26.3); nothing exposes it.
    private static final int FIRE_DAMAGE_INTERVAL = 20;

    private CompressedTicks() {
    }

    /**
     * Scales a vanilla brain timer whose event fires {@code vanillaTicks + 1} ticks after it is set (memory
     * expiry and {@code Behavior} timeouts both behave this way) to the server tick nearest a tenth of that.
     */
    public static long brainTimer(long vanillaTicks) {
        return Math.max(0L, (vanillaTicks + 1 + VirtualTime.ACCELERATION / 2) / VirtualTime.ACCELERATION - 1);
    }

    /** As {@link #brainTimer}, but the event never fires later than vanilla's once that is a server tick away. */
    public static long brainTimerNoLater(long vanillaTicks) {
        return Math.max(0L, (vanillaTicks + 1) / VirtualTime.ACCELERATION - 1);
    }

    /** A countdown that loses one tick per virtual tick, after a whole server tick. */
    public static int countDown(int ticks) {
        return Math.max(0, ticks - VirtualTime.ACCELERATION);
    }

    /** A countdown that vanilla has already stepped once this server tick, after the other virtual ticks. */
    public static int remainingAfterServerTick(int vanillaValue) {
        return Math.max(0, vanillaValue - VirtualTime.LAST_OFFSET);
    }

    /** The hurt cooldown at the running virtual tick, from its value as of the end of the server tick. */
    public static int cooldownSeenAt(int storedCooldown, int remainingInTick) {
        return storedCooldown + remainingInTick;
    }

    /** The hurt cooldown as of the end of the server tick, from the value set at the running virtual tick. */
    public static int cooldownStoredAfterHit(int cooldown, int remainingInTick) {
        return Math.max(0, cooldown - remainingInTick);
    }

    /**
     * The fire ticks vanilla's damage check should see, so that it hurts once this server tick if any of the
     * virtual ticks it covers is a fire damage tick.
     */
    public static int fireTicksForDamageCheck(int remainingFireTicks) {
        if (EffectTickCadence.crossesScheduledTick(
                remainingFireTicks, FIRE_DAMAGE_INTERVAL, VirtualTime.ACCELERATION)) {
            return remainingFireTicks - Math.floorMod(remainingFireTicks, FIRE_DAMAGE_INTERVAL);
        }
        return remainingFireTicks;
    }
}
