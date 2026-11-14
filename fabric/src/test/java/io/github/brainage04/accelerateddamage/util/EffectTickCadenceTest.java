package io.github.brainage04.accelerateddamage.util;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The properties compare the compressed timeline with vanilla's effect ticking, cited from the 26.3 decompilation
 * (~/agent-work/port263-diff/dec/server-26.3).
 */
class EffectTickCadenceTest {
    private static final int ACCELERATION = VirtualTime.ACCELERATION;

    /**
     * Poison, wither and regeneration apply when the tick is a multiple of {@code base >> amplifier}, or on every
     * tick once that is 0 (PoisonMobEffect.java:23-26 with 25, WitherMobEffect.java:20-23 with 40,
     * RegenerationMobEffect.java:21-24 with 50).
     */
    private static boolean vanillaApplies(int base, int amplifier, int tick) {
        int interval = base >> amplifier;
        return interval > 0 ? tick % interval == 0 : true;
    }

    private static int vanillaApplications(int base, int amplifier, int firstTick, int ticks, boolean ascending) {
        int applications = 0;
        for (int offset = 0; offset < ticks; offset++) {
            if (vanillaApplies(base, amplifier, EffectTickCadence.scheduledTick(firstTick, offset, ascending))) {
                applications++;
            }
        }
        return applications;
    }

    @Property
    void countsTheTicksVanillaAppliesOn(
            @ForAll("bases") int base,
            @ForAll @IntRange(max = 255) int amplifier,
            @ForAll @IntRange(min = 1, max = 1_000_000) int currentTick,
            @ForAll @IntRange(max = 40) int ticksAdvanced,
            @ForAll boolean ascending) {
        if (!ascending) {
            ticksAdvanced = Math.min(ticksAdvanced, currentTick);
        }
        assertEquals(
                vanillaApplications(base, amplifier, currentTick, ticksAdvanced, ascending),
                EffectTickCadence.countScheduledTicks(currentTick, base >> amplifier, ticksAdvanced, ascending));
    }

    @Provide
    Arbitrary<Integer> bases() {
        return Arbitraries.of(25, 40, 50);
    }

    /**
     * A finite effect: vanilla applies it on its remaining duration and then counts down by one while the duration is
     * above 0 (MobEffectInstance.java:223-238,255-261). The compressed timeline covers the ten durations from the
     * current one down in one server tick, then counts down with {@link CompressedTicks#countDown}.
     */
    @Property
    void finiteEffectAppliesInTheServerTickOfEveryVanillaApplication(
            @ForAll("bases") int base,
            @ForAll @IntRange(max = 8) int amplifier,
            @ForAll @IntRange(min = 1, max = 20_000) int duration) {
        int serverTick = 0;
        for (int remaining = duration; remaining > 0; remaining = CompressedTicks.countDown(remaining)) {
            int ticksAdvanced = Math.min(ACCELERATION, remaining);
            int vanilla = 0;
            for (int virtual = serverTick * ACCELERATION; virtual < (serverTick + 1) * ACCELERATION; virtual++) {
                int vanillaRemaining = duration - virtual;
                if (vanillaRemaining > 0 && vanillaApplies(base, amplifier, vanillaRemaining)) {
                    vanilla++;
                }
            }
            assertEquals(vanilla, EffectTickCadence.countScheduledTicks(remaining, base >> amplifier, ticksAdvanced, false),
                    "server tick " + serverTick);
            serverTick++;
        }
        assertEquals((duration + ACCELERATION - 1) / ACCELERATION, serverTick);
    }

    /**
     * An infinite effect: vanilla applies it on the entity's tick count, one per tick (MobEffectInstance.java:228).
     * The compressed timeline covers the next ten tick counts in each server tick.
     */
    @Property
    void infiniteEffectAppliesInTheServerTickOfEveryVanillaApplication(
            @ForAll("bases") int base,
            @ForAll @IntRange(max = 8) int amplifier,
            @ForAll @IntRange(max = 1_000_000) int firstTickCount,
            @ForAll @IntRange(min = 1, max = 200) int serverTicks) {
        for (int serverTick = 0; serverTick < serverTicks; serverTick++) {
            int vanilla = 0;
            for (int offset = 0; offset < ACCELERATION; offset++) {
                if (vanillaApplies(base, amplifier, firstTickCount + serverTick * ACCELERATION + offset)) {
                    vanilla++;
                }
            }
            assertEquals(vanilla, EffectTickCadence.countScheduledTicks(
                    firstTickCount + serverTick * ACCELERATION, base >> amplifier, ACCELERATION, true));
        }
    }

    @Property
    void crossesAScheduledTickWhenADurationItCoversIsAMultipleOfTheInterval(
            @ForAll @IntRange(min = -100, max = 100_000) int duration,
            @ForAll @IntRange(min = -5, max = 100) int interval,
            @ForAll @IntRange(min = -5, max = 40) int ticksAdvanced) {
        boolean expected = false;
        for (int tick = duration; tick > 0 && tick > duration - ticksAdvanced; tick--) {
            expected |= tick % Math.max(interval, 1) == 0;
        }
        assertEquals(expected, EffectTickCadence.crossesScheduledTick(duration, interval, ticksAdvanced));
    }

    @Test
    void detectsScheduledTicksAcrossTheAcceleratedDurationWindow() {
        assertTrue(EffectTickCadence.crossesScheduledTick(25, 25, 10));
        assertTrue(EffectTickCadence.crossesScheduledTick(30, 25, 10));
        assertFalse(EffectTickCadence.crossesScheduledTick(24, 25, 10));
        assertFalse(EffectTickCadence.crossesScheduledTick(20, 25, 10));
    }

    @Test
    void excludesZeroWhenTheEffectExpires() {
        assertFalse(EffectTickCadence.crossesScheduledTick(5, 25, 10));
        assertFalse(EffectTickCadence.crossesScheduledTick(1, 25, 10));
        assertFalse(EffectTickCadence.crossesScheduledTick(0, 1, 10));
    }

    @Test
    void clampsCollapsedAmplifierIntervalsToEveryTick() {
        assertTrue(EffectTickCadence.crossesScheduledTick(7, 0, 10));
    }


    @Test
    void countsEveryScheduledTickInDescendingFiniteWindows() {
        assertEquals(2, EffectTickCadence.countScheduledTicks(10, 5, 10, false));
        assertEquals(1, EffectTickCadence.countScheduledTicks(7, 5, 3, false));
    }

    @Test
    void countsNonOverlappingAscendingInfiniteWindows() {
        assertEquals(2, EffectTickCadence.countScheduledTicks(1, 5, 10, true));
        assertEquals(2, EffectTickCadence.countScheduledTicks(11, 5, 10, true));
    }
}
