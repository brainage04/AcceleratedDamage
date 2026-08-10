package io.github.brainage04.accelerateddamage;

import io.github.brainage04.accelerateddamage.gametest.AcceleratedDamageGameTestScenarios;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class AcceleratedDamageGameTest {
    @GameTest
    public void damageRulesAndSourceBoundaries(GameTestHelper context) {
        AcceleratedDamageGameTestScenarios.damageRulesAndSourceBoundaries(context);
    }

    @GameTest
    public void fireAndFreezeUseSingleCompressedTimeline(GameTestHelper context) {
        AcceleratedDamageGameTestScenarios.fireAndFreezeUseSingleCompressedTimeline(context);
    }

    @GameTest
    public void effectCadenceDurationAndTransitions(GameTestHelper context) {
        AcceleratedDamageGameTestScenarios.effectCadenceDurationAndTransitions(context);
    }

    @GameTest
    public void attackCooldownIsImmediateForExistingAndJoiningPlayers(GameTestHelper context) {
        AcceleratedDamageGameTestScenarios.attackCooldownIsImmediateForExistingAndJoiningPlayers(context);
    }

    @GameTest(maxTicks = 100)
    public void instantShootFiresBowCrossbowAndTrident(GameTestHelper context) {
        AcceleratedDamageGameTestScenarios.instantShootFiresBowCrossbowAndTrident(context);
    }
}
