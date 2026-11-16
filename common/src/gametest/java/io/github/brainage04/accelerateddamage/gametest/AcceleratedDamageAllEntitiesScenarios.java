package io.github.brainage04.accelerateddamage.gametest;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * The owner's rule of 16 Nov 2026: every entity attacks ten times as often and takes damage ten times as
 * soon while {@code faster_effect_ticking} is on, players included. Each case measures the vanilla interval
 * with the rule off and the same interval with it on, so the assertion is the ratio, not a copied constant.
 */
public final class AcceleratedDamageAllEntitiesScenarios {
    private AcceleratedDamageAllEntitiesScenarios() {
    }

    public static void everyEntityAttacksAndTakesDamageTenTimesFaster(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();
        ServerPlayer player = null;

        try {
            setRule(rules, server, false);
            player = context.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));

            float vanillaDelay = player.getCurrentItemAttackStrengthDelay();
            assertTrue(vanillaDelay > 2.0F, "Expected a vanilla sword to need more than two ticks to charge");
            player.resetAttackStrengthTicker();
            player.doTick();
            player.doTick();
            assertTrue(player.getAttackStrengthScale(0.0F) < 1.0F,
                    "Expected a vanilla sword not to be charged after two ticks");

            int vanillaMelee = meleeTicksBetweenHits(context);
            int vanillaRanged = rangedTicksBetweenShots(context);
            int vanillaMobIntake = mobTicksUntilSecondHit(context);
            int vanillaPlayerIntake = playerTicksUntilSecondHit(level, player);

            setRule(rules, server, true);

            assertEquals(vanillaDelay / 10.0F, player.getCurrentItemAttackStrengthDelay(),
                    "Expected the attack delay to be a tenth of vanilla");
            player.resetAttackStrengthTicker();
            player.doTick();
            player.doTick();
            assertEquals(1.0F, player.getAttackStrengthScale(0.0F),
                    "Expected an accelerated sword to be fully charged after two ticks");

            assertEquals(vanillaMelee / 10, meleeTicksBetweenHits(context),
                    "Expected a melee mob to attack ten times as often");
            assertEquals(vanillaRanged / 10, rangedTicksBetweenShots(context),
                    "Expected a ranged mob to shoot ten times as often");
            assertEquals(vanillaMobIntake / 10, mobTicksUntilSecondHit(context),
                    "Expected a mob to take a second hit ten times sooner");
            assertEquals(vanillaPlayerIntake / 10, playerTicksUntilSecondHit(level, player),
                    "Expected a player to take a second hit ten times sooner");
        } finally {
            setRule(rules, server, false);
            if (player != null && server.getPlayerList().getPlayer(player.getUUID()) != null) {
                server.getPlayerList().remove(player);
            }
        }

        context.succeed();
    }

    /** Ticks from a melee mob's first landed hit to its second, with the damage window held open. */
    private static int meleeTicksBetweenHits(GameTestHelper context) {
        Zombie attacker = create(context, EntityTypes.ZOMBIE);
        IronGolem victim = create(context, EntityTypes.IRON_GOLEM);
        victim.setPos(attacker.position().add(0.5, 0.0, 0.0));
        attacker.setTarget(victim);
        MeleeAttackGoal goal = new MeleeAttackGoal(attacker, 1.0, true);
        try {
            int firstHit = -1;
            for (int tick = 1; tick <= 60; tick++) {
                victim.invulnerableTime = 0;
                float before = victim.getHealth();
                goal.tick();
                if (victim.getHealth() < before) {
                    if (firstHit < 0) {
                        firstHit = tick;
                    } else {
                        return tick - firstHit;
                    }
                }
            }
            return -1;
        } finally {
            goal.stop();
            attacker.discard();
            victim.discard();
        }
    }

    /** Ticks from a ranged mob's first shot to its second, counted by the projectiles it spawns. */
    private static int rangedTicksBetweenShots(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        SnowGolem shooter = create(context, EntityTypes.SNOW_GOLEM);
        Zombie target = create(context, EntityTypes.ZOMBIE);
        target.setPos(shooter.position().add(0.0, 0.0, 3.0));
        shooter.setTarget(target);
        RangedAttackGoal goal = new RangedAttackGoal(shooter, 1.0, 20, 10.0F);
        Set<Integer> seen = new HashSet<>();
        try {
            assertTrue(goal.canUse(), "Expected the ranged fixture to acquire its target");
            int firstShot = -1;
            for (int tick = 1; tick <= 200; tick++) {
                goal.tick();
                int shots = 0;
                for (Snowball snowball : level.getEntitiesOfClass(
                        Snowball.class, shooter.getBoundingBox().inflate(20.0))) {
                    if (seen.add(snowball.getId())) {
                        shots++;
                    }
                }
                if (shots > 0) {
                    if (firstShot < 0) {
                        firstShot = tick;
                    } else {
                        return tick - firstShot;
                    }
                }
            }
            return -1;
        } finally {
            goal.stop();
            shooter.discard();
            target.discard();
        }
    }

    /** Server ticks until a mob accepts a second hit of the same size from the same source. */
    private static int mobTicksUntilSecondHit(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        Zombie victim = create(context, EntityTypes.ZOMBIE);
        victim.setHealth(20.0F);
        try {
            assertTrue(victim.hurtServer(level, level.damageSources().cactus(), 1.0F),
                    "Expected the first contact hit to apply");
            for (int tick = 1; tick <= 30; tick++) {
                victim.baseTick();
                if (victim.hurtServer(level, level.damageSources().cactus(), 1.0F)) {
                    return tick;
                }
            }
            return -1;
        } finally {
            victim.discard();
        }
    }

    /** Server ticks until a player accepts a second hit of the same size from the same source. */
    private static int playerTicksUntilSecondHit(ServerLevel level, ServerPlayer player) {
        player.setHealth(20.0F);
        assertTrue(player.hurtServer(level, level.damageSources().cactus(), 1.0F),
                "Expected the first contact hit to apply to the player");
        for (int tick = 1; tick <= 30; tick++) {
            player.tick();
            if (player.hurtServer(level, level.damageSources().cactus(), 1.0F)) {
                return tick;
            }
        }
        return -1;
    }

    private static <T extends LivingEntity> T create(GameTestHelper context, EntityType<T> type) {
        T entity = type.create(context.getLevel(), EntitySpawnReason.COMMAND);
        if (entity == null) {
            throw new AssertionError("Could not create " + type);
        }
        Vec3 position = context.absoluteVec(new Vec3(3.5, 3.0, 3.5));
        entity.setPos(position);
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
        return entity;
    }

    private static void setRule(GameRules rules, MinecraftServer server, boolean fasterEffectTicking) {
        rules.set(ModGameRules.FASTER_EFFECT_TICKING, fasterEffectTicking, server);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(Component.literal(message), 0);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new GameTestAssertException(
                    Component.literal(message + ": expected " + expected + ", found " + actual),
                    0
            );
        }
    }

    private static void assertEquals(float expected, float actual, String message) {
        if (Float.compare(expected, actual) != 0) {
            throw new GameTestAssertException(
                    Component.literal(message + ": expected " + expected + ", found " + actual),
                    0
            );
        }
    }
}
