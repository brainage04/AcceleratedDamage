package io.github.brainage04.accelerateddamage;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.accelerateddamage.util.EffectSyncTicker;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AcceleratedDamage implements ModInitializer {
    public static final String MOD_ID = "accelerateddamage";
    public static final String MOD_NAME = "Accelerated Damage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    @Override
    public void onInitialize() {
        LOGGER.info("{} initialising...", MOD_NAME);
        ModGameRules.initialize();
        EffectSyncTicker.initialize();
        LOGGER.info("{} initialised.", MOD_NAME);
    }
}
