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
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
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
            // The mock player is CREATIVE (GameType.java:62-66, 26.2), which makes it invulnerable, and its
            // connection still waits for the client to load (ServerPlayer.java:1279-1280, 26.2), which makes it
            // immune to every source until the 60-tick timeout runs out (ServerGamePacketListenerImpl.java:2214-2216).
            player.getAbilities().invulnerable = false;
            player.setInvulnerable(false);
            for (int tick = 0; tick < 60; tick++) {
                player.connection.tickClientLoadTimeout();
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));

            // The held item's ATTACK_SPEED modifier reaches the attribute on the player's next tick.
            player.resetAttackStrengthTicker();
            player.doTick();
            player.doTick();
            float vanillaDelay = player.getCurrentItemAttackStrengthDelay();
            assertTrue(vanillaDelay > 2.0F, "Expected a vanilla sword to need more than two ticks to charge");
            assertTrue(player.getAttackStrengthScale(0.0F) < 1.0F,
                    "Expected a vanilla sword not to be charged after two ticks");

            int vanillaDraw = playerTicksToDrawBow(player);
            int vanillaThrow = playerTicksToThrowTrident(level, player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            player.doTick();
            int vanillaMelee = meleeTicksBetweenHits(context);
            int vanillaRanged = rangedTicksBetweenShots(context);
            int vanillaMobIntake = mobTicksUntilSecondHit(context);
            int vanillaPlayerIntake = playerTicksUntilSecondHit(level, player);
            assertTrue(vanillaDraw > 0 && vanillaThrow > 0 && vanillaMelee > 0 && vanillaRanged > 0
                            && vanillaMobIntake > 0 && vanillaPlayerIntake > 0,
                    "Expected every vanilla interval to be measurable: draw=" + vanillaDraw
                            + ", throw=" + vanillaThrow + ", melee=" + vanillaMelee + ", ranged=" + vanillaRanged
                            + ", mob intake=" + vanillaMobIntake + ", player intake=" + vanillaPlayerIntake);

            setRule(rules, server, true);

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            player.doTick();
            float acceleratedDelay = player.getCurrentItemAttackStrengthDelay();
            player.resetAttackStrengthTicker();
            player.doTick();
            player.doTick();
            float acceleratedCharge = player.getAttackStrengthScale(0.0F);
            int acceleratedDraw = playerTicksToDrawBow(player);
            int acceleratedThrow = playerTicksToThrowTrident(level, player);
            int acceleratedMelee = meleeTicksBetweenHits(context);
            int acceleratedRanged = rangedTicksBetweenShots(context);
            int acceleratedMobIntake = mobTicksUntilSecondHit(context);
            int acceleratedPlayerIntake = playerTicksUntilSecondHit(level, player);

            // Every interval is reported in one failure, so a run shows all of them at once.
            StringBuilder mismatches = new StringBuilder();
            expectTenth(mismatches, "player attack delay", vanillaDelay, acceleratedDelay);
            expectTenth(mismatches, "player bow draw", vanillaDraw, acceleratedDraw);
            expectTenth(mismatches, "player trident throw", vanillaThrow, acceleratedThrow);
            expectTenth(mismatches, "melee mob hit spacing", vanillaMelee, acceleratedMelee);
            expectTenth(mismatches, "ranged mob shot spacing", vanillaRanged, acceleratedRanged);
            expectTenth(mismatches, "mob damage window", vanillaMobIntake, acceleratedMobIntake);
            expectTenth(mismatches, "player damage window", vanillaPlayerIntake, acceleratedPlayerIntake);
            if (Float.compare(acceleratedCharge, 1.0F) != 0) {
                mismatches.append("; a sword must be fully charged after two accelerated ticks, found ")
                        .append(acceleratedCharge);
            }
            if (!mismatches.isEmpty()) {
                throw new GameTestAssertException(Component.literal(mismatches.substring(2)), 0);
            }
        } finally {
            setRule(rules, server, false);
            if (player != null && server.getPlayerList().getPlayer(player.getUUID()) != null) {
                server.getPlayerList().remove(player);
            }
        }

        context.succeed();
    }

    /** Ticks a player needs to reach a full-power bow draw ({@code BowItem.getPowerForTime} 1.0). */
    private static int playerTicksToDrawBow(ServerPlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        player.getInventory().setItem(9, new ItemStack(Items.ARROW, 16));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        try {
            for (int tick = 1; tick <= 40; tick++) {
                // ServerPlayer.tick runs only the connection half; the item-use countdown is in doTick.
                player.doTick();
                if (BowItem.getPowerForTime(player.getTicksUsingItem()) >= 1.0F) {
                    return tick;
                }
            }
            return -1;
        } finally {
            player.stopUsingItem();
        }
    }

    /**
     * Ticks a player must hold a trident before {@code TridentItem.releaseUsing} throws it, measured by the
     * vanilla 10-tick minimum ({@code TridentItem.java:65}, 26.2) through the item's own release path.
     */
    private static int playerTicksToThrowTrident(ServerLevel level, ServerPlayer player) {
        for (int held = 1; held <= 20; held++) {
            ItemStack trident = new ItemStack(Items.TRIDENT);
            player.setItemInHand(InteractionHand.MAIN_HAND, trident);
            trident.use(level, player, InteractionHand.MAIN_HAND);
            if (((TridentItem) Items.TRIDENT).releaseUsing(
                    trident, level, player, trident.getUseDuration(player) - held)) {
                player.stopUsingItem();
                return held;
            }
            player.stopUsingItem();
        }
        return -1;
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
        player.invulnerableTime = 0;
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

    private static void expectTenth(
            StringBuilder mismatches,
            String interval,
            float vanilla,
            float accelerated
    ) {
        if (Float.compare(vanilla / 10.0F, accelerated) != 0) {
            mismatches.append("; ").append(interval).append(" must be a tenth of vanilla ")
                    .append(vanilla).append(", found ").append(accelerated);
        }
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
