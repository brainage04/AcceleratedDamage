package io.github.brainage04.accelerateddamage.neoforge;

import io.github.brainage04.accelerateddamage.AcceleratedDamage;
import io.github.brainage04.accelerateddamage.gametest.AcceleratedDamageGameTestScenarios;
import io.github.brainage04.accelerateddamage.gametest.AcceleratedDamageKnockbackScenarios;
import io.github.brainage04.accelerateddamage.gametest.AcceleratedDamageTimerScenarios;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = AcceleratedDamage.MOD_ID)
public final class NeoForgeAcceleratedDamageGameTests {
    private NeoForgeAcceleratedDamageGameTests() {}

    @SubscribeEvent
    public static void registerTestFunctions(RegisterEvent event) {
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                id("damage_rules_and_source_boundaries"),
                () -> AcceleratedDamageGameTestScenarios::damageRulesAndSourceBoundaries);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                id("fire_and_freeze_use_single_compressed_timeline"),
                () -> AcceleratedDamageGameTestScenarios::fireAndFreezeUseSingleCompressedTimeline);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                id("effect_cadence_duration_and_transitions"),
                () -> AcceleratedDamageGameTestScenarios::effectCadenceDurationAndTransitions);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                id("mob_attacks_use_compressed_timeline"),
                () -> AcceleratedDamageGameTestScenarios::mobAttacksUseCompressedTimeline);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                id("attack_cooldown_is_immediate_for_existing_and_joining_players"),
                () ->
                        AcceleratedDamageGameTestScenarios
                                ::attackCooldownIsImmediateForExistingAndJoiningPlayers);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                id("instant_shoot_fires_bow_crossbow_and_trident"),
                () -> AcceleratedDamageGameTestScenarios::instantShootFiresBowCrossbowAndTrident);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                id("air_drying_healing_and_mob_weapons"),
                () -> AcceleratedDamageTimerScenarios::airDryingHealingAndMobWeapons);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                id("hunger_and_stinging_and_creeper_fuses"),
                () -> AcceleratedDamageTimerScenarios::hungerAndStingingAndCreeperFuses);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                id("lingering_clouds_expire_on_the_selected_timeline"),
                () -> AcceleratedDamageTimerScenarios::lingeringCloudsExpireOnTheSelectedTimeline);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                id("healing_melee_and_ram_cooldowns"),
                () -> AcceleratedDamageTimerScenarios::healingMeleeAndRamCooldowns);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                id("beacon_and_conduit_pulses"),
                () -> AcceleratedDamageTimerScenarios::beaconAndConduitPulses);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                id("every_hit_knocks_back_while_i_frames_are_disabled"),
                () ->
                        AcceleratedDamageKnockbackScenarios
                                ::everyHitKnocksBackWhileIFramesAreDisabled);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                id("fully_blocked_hits_never_knock_back"),
                () -> AcceleratedDamageKnockbackScenarios::fullyBlockedHitsNeverKnockBack);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                id("crossbow_firework_rockets_knock_back_on_every_hit"),
                () ->
                        AcceleratedDamageKnockbackScenarios
                                ::crossbowFireworkRocketsKnockBackOnEveryHit);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                id("multishot_crossbows_knock_back_once_per_projectile"),
                () ->
                        AcceleratedDamageKnockbackScenarios
                                ::multishotCrossbowsKnockBackOncePerProjectile);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                id("wind_charges_damage_on_every_hit"),
                () -> AcceleratedDamageKnockbackScenarios::windChargesDamageOnEveryHit);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                id("snowballs_knock_back_without_damage"),
                () -> AcceleratedDamageKnockbackScenarios::snowballsKnockBackWithoutDamage);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(AcceleratedDamage.MOD_ID, path);
    }
}
