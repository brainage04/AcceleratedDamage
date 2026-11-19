package io.github.brainage04.accelerateddamage.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EffectSyncTickerTest {
    @Test
    void resendsEveryTenServerTicksWhileTheRuleIsOn() {
        assertTrue(EffectSyncTicker.shouldSync(0, true));
        assertTrue(EffectSyncTicker.shouldSync(10, true));
        assertTrue(EffectSyncTicker.shouldSync(1230, true));
        assertFalse(EffectSyncTicker.shouldSync(1, true));
        assertFalse(EffectSyncTicker.shouldSync(9, true));
        assertFalse(EffectSyncTicker.shouldSync(11, true));
    }

    @Test
    void sendsNothingWhileTheRuleIsOff() {
        assertFalse(EffectSyncTicker.shouldSync(0, false));
        assertFalse(EffectSyncTicker.shouldSync(10, false));
    }
}
