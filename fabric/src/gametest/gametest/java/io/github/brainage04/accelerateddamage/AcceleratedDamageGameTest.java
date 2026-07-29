package io.github.brainage04.accelerateddamage;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.gamerules.GameRules;

public final class AcceleratedDamageGameTest {
    @GameTest
    public void instantShootChangesBowAndTridentCharge(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();
        int vanillaTridentThreshold = TridentItem.THROW_THRESHOLD_TIME;

        try {
            rules.set(ModGameRules.INSTANT_SHOOT, false, server);
            assertTrue(BowItem.getPowerForTime(1) < 1.0F, "Expected vanilla bow charge before enabling instant shooting");

            rules.set(ModGameRules.INSTANT_SHOOT, true, server);
            assertTrue(
                    Float.compare(1.0F, BowItem.getPowerForTime(1)) == 0,
                    "Expected the bow to charge instantly"
            );
            assertEquals(0, TridentItem.THROW_THRESHOLD_TIME, "Expected the trident throw threshold to be zero");
        } finally {
            rules.set(ModGameRules.INSTANT_SHOOT, false, server);
        }

        assertEquals(
                vanillaTridentThreshold,
                TridentItem.THROW_THRESHOLD_TIME,
                "Expected the trident threshold to return to its vanilla value"
        );
        context.succeed();
    }

    @GameTest
    public void disableIFramesAllowsConsecutiveMeleeDamage(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();
        Zombie attacker = createZombie(level);
        Zombie victim = createZombie(level);
        float initialHealth = victim.getHealth();

        try {
            rules.set(ModGameRules.DISABLE_IFRAMES, true, server);
            assertTrue(
                    victim.hurtServer(level, victim.damageSources().mobAttack(attacker), 1.0F),
                    "Expected the first melee hit to deal damage"
            );
            float healthAfterFirstHit = victim.getHealth();
            assertTrue(healthAfterFirstHit < initialHealth, "Expected the first melee hit to reduce health");

            assertTrue(
                    victim.hurtServer(level, victim.damageSources().mobAttack(attacker), 1.0F),
                    "Expected the consecutive melee hit to bypass invincibility frames"
            );
            assertTrue(
                    victim.getHealth() < healthAfterFirstHit,
                    "Expected the consecutive melee hit to reduce health again"
            );
        } finally {
            rules.set(ModGameRules.DISABLE_IFRAMES, false, server);
        }

        context.succeed();
    }

    private static Zombie createZombie(ServerLevel level) {
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (zombie == null) {
            throw new AssertionError("Expected to create a zombie");
        }
        return zombie;
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }


    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", found " + actual);
        }
    }
}
