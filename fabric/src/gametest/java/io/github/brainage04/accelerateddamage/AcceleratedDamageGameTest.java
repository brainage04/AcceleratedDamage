package io.github.brainage04.accelerateddamage;

import io.github.brainage04.accelerateddamage.gametest.AcceleratedDamageGameTestScenarios;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class AcceleratedDamageGameTest {
    @GameTest(environment = "accelerateddamage:damage_rules")
    public void damageRulesAndSourceBoundaries(GameTestHelper context) {
        AcceleratedDamageGameTestScenarios.damageRulesAndSourceBoundaries(context);
    }

    @GameTest(environment = "accelerateddamage:fire_and_freeze")
    public void fireAndFreezeUseSingleCompressedTimeline(GameTestHelper context) {
        AcceleratedDamageGameTestScenarios.fireAndFreezeUseSingleCompressedTimeline(context);
    }

    @GameTest(environment = "accelerateddamage:effect_cadence")
    public void effectCadenceDurationAndTransitions(GameTestHelper context) {
        AcceleratedDamageGameTestScenarios.effectCadenceDurationAndTransitions(context);
    }

    @GameTest(environment = "accelerateddamage:attack_cooldown")
    public void attackCooldownIsImmediateForExistingAndJoiningPlayers(GameTestHelper context) {
        AcceleratedDamageGameTestScenarios.attackCooldownIsImmediateForExistingAndJoiningPlayers(context);
    }

    @GameTest(environment = "accelerateddamage:instant_shoot", maxTicks = 100)
    public void instantShootFiresBowCrossbowAndTrident(GameTestHelper context) {
        AcceleratedDamageGameTestScenarios.instantShootFiresBowCrossbowAndTrident(context);
    }
}
