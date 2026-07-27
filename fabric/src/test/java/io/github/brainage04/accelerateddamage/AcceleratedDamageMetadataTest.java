package io.github.brainage04.accelerateddamage;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcceleratedDamageMetadataTest {
    @Test
    void fabricLoaderBootsInServerModeForTests() {
        assertEquals(EnvType.SERVER, FabricLoader.getInstance().getEnvironmentType());
    }

    @Test
    void fabricLoaderCanResolveModMetadata() {
        ModContainer mod = FabricLoader.getInstance()
                .getModContainer(AcceleratedDamage.MOD_ID)
                .orElseThrow(() -> new AssertionError("Expected Accelerated Damage to be loaded for tests."));
        ModMetadata metadata = mod.getMetadata();

        assertAll(
                () -> assertEquals(AcceleratedDamage.MOD_ID, metadata.getId()),
                () -> assertEquals(AcceleratedDamage.MOD_NAME, metadata.getName()),
                () -> assertTrue(metadata.getLicense().contains("MIT")),
                () -> assertTrue(mod.findPath("fabric.mod.json").isPresent())
        );
    }
}
