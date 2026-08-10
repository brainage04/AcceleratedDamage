package io.github.brainage04.accelerateddamage.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectTickCadenceTest {
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
