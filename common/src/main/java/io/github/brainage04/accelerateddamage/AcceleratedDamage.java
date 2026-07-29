package io.github.brainage04.accelerateddamage;

import io.github.brainage04.accelerateddamage.platform.AcceleratedDamagePlatform;
import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.accelerateddamage.util.EffectSyncTicker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AcceleratedDamage {
    public static final String MOD_ID = "accelerateddamage";
    public static final String MOD_NAME = "Accelerated Damage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private AcceleratedDamage() {
    }

    public static void initialize(AcceleratedDamagePlatform platform) {
        LOGGER.info("{} initialising...", MOD_NAME);
        ModGameRules.initialize(platform);
        EffectSyncTicker.initialize(platform);
        LOGGER.info("{} initialised.", MOD_NAME);
    }
}
