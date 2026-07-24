package io.github.brainage04.fabricmoddingtemplate;

import io.github.brainage04.fabricmoddingtemplate.command.core.ClientModCommands;
import net.fabricmc.api.ClientModInitializer;

public class FabricModdingTemplateClient implements ClientModInitializer {
    private static volatile boolean initialized;

    @Override
    public void onInitializeClient() {
        ClientModCommands.initialize();
        initialized = true;

        FabricModdingTemplate.LOGGER.info("{} client initialised.", FabricModdingTemplate.MOD_NAME);
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
