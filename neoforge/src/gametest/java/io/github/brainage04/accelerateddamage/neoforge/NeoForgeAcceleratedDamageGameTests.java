package io.github.brainage04.accelerateddamage.neoforge;

import io.github.brainage04.accelerateddamage.AcceleratedDamage;
import io.github.brainage04.accelerateddamage.gametest.AcceleratedDamageGameTestScenarios;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = AcceleratedDamage.MOD_ID)
public final class NeoForgeAcceleratedDamageGameTests {
    private NeoForgeAcceleratedDamageGameTests() {
    }

    @SubscribeEvent
    public static void registerTestFunctions(RegisterEvent event) {
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), id("damage_rules_and_source_boundaries"),
                () -> AcceleratedDamageGameTestScenarios::damageRulesAndSourceBoundaries);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), id("fire_and_freeze_use_single_compressed_timeline"),
                () -> AcceleratedDamageGameTestScenarios::fireAndFreezeUseSingleCompressedTimeline);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), id("effect_cadence_duration_and_transitions"),
                () -> AcceleratedDamageGameTestScenarios::effectCadenceDurationAndTransitions);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), id("mob_attacks_use_compressed_timeline"),
                () -> AcceleratedDamageGameTestScenarios::mobAttacksUseCompressedTimeline);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), id("attack_cooldown_is_immediate_for_existing_and_joining_players"),
                () -> AcceleratedDamageGameTestScenarios::attackCooldownIsImmediateForExistingAndJoiningPlayers);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), id("instant_shoot_fires_bow_crossbow_and_trident"),
                () -> AcceleratedDamageGameTestScenarios::instantShootFiresBowCrossbowAndTrident);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(AcceleratedDamage.MOD_ID, path);
    }
}
