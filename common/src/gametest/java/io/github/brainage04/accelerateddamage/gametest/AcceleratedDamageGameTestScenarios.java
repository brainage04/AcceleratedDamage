package io.github.brainage04.accelerateddamage.gametest;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class AcceleratedDamageGameTestScenarios {
    private AcceleratedDamageGameTestScenarios() {
    }

    public static void damageRulesAndSourceBoundaries(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();


        try {
            setRules(rules, server, false, false, false, false);

            Zombie attacker = createZombie(level);
            Zombie victim = createZombie(level);
            assertTrue(victim.hurtServer(level, level.damageSources().mobAttack(attacker), 1.0F),
                    "Expected the first vanilla melee hit to apply");
            assertFalse(victim.hurtServer(level, level.damageSources().mobAttack(attacker), 1.0F),
                    "Expected vanilla invincibility to reject the second melee hit");

            rules.set(ModGameRules.DISABLE_IFRAMES, true, server);
            victim = createZombie(level);
            assertTrue(victim.hurtServer(level, level.damageSources().mobAttack(attacker), 1.0F),
                    "Expected the first melee hit to apply with invincibility disabled");
            assertTrue(victim.hurtServer(level, level.damageSources().mobAttack(attacker), 1.0F),
                    "Expected the second melee hit to bypass invincibility");
            assertTrue(victim.getHealth() < 19.0F, "Expected two accepted melee hits");

            Zombie shooter = createZombie(level);
            Arrow arrow = new Arrow(level, shooter, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            victim = createZombie(level);
            assertTrue(victim.hurtServer(level, level.damageSources().arrow(arrow, shooter), 1.0F),
                    "Expected the first projectile hit to apply");
            assertTrue(victim.hurtServer(level, level.damageSources().arrow(arrow, shooter), 1.0F),
                    "Expected projectile damage to bypass invincibility without source filtering");

            victim = createZombie(level);
            assertTrue(victim.hurtServer(level, level.damageSources().lava(), 1.0F),
                    "Expected the first environmental hit to apply");
            assertTrue(victim.hurtServer(level, level.damageSources().lava(), 1.0F),
                    "Expected environmental damage to bypass invincibility");

            rules.set(ModGameRules.DISABLE_IFRAMES, false, server);
            rules.set(ModGameRules.FASTER_EFFECT_TICKING, true, server);
            victim = createZombie(level);
            assertTrue(victim.hurtServer(level, level.damageSources().cactus(), 1.0F),
                    "Expected the first contact hit to apply");
            assertFalse(victim.hurtServer(level, level.damageSources().cactus(), 1.0F),
                    "Expected a second hit in the same server tick to stay inside the compressed invincibility window");
            victim.baseTick();
            assertTrue(victim.hurtServer(level, level.damageSources().cactus(), 1.0F),
                    "Expected the ten-tick invincibility window to end after one accelerated server tick");

            victim = createZombie(level);
            assertTrue(victim.hurtServer(level, level.damageSources().onFire(), 1.0F),
                    "Expected the first fire hit to apply");
            assertFalse(victim.hurtServer(level, level.damageSources().onFire(), 1.0F),
                    "Expected fire damage to respect the compressed invincibility window");

            rules.set(ModGameRules.FASTER_EFFECT_TICKING, false, server);
            victim = createZombie(level);
            assertTrue(victim.hurtServer(level, level.damageSources().cactus(), 1.0F),
                    "Expected the first vanilla contact hit to apply");
            victim.baseTick();
            assertFalse(victim.hurtServer(level, level.damageSources().cactus(), 1.0F),
                    "Expected vanilla invincibility to outlast one server tick");
            rules.set(ModGameRules.FASTER_EFFECT_TICKING, true, server);

            victim = createZombie(level);
            victim.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100));
            victim.invulnerableTime = 8;
            victim.hurtDuration = 5;
            victim.hurtTime = 5;
            assertFalse(victim.hurtServer(level, level.damageSources().onFire(), 1.0F),
                    "Expected fire resistance to reject fire damage");
            assertEquals(8, victim.invulnerableTime,
                    "Expected a rejected hit not to mutate invincibility time");
            assertEquals(5, victim.hurtDuration,
                    "Expected a rejected hit not to mutate hurt duration");
            assertEquals(5, victim.hurtTime,
                    "Expected a rejected hit not to mutate hurt animation time");
        } finally {
            setRules(rules, server, false, false, false, false);
        }

        context.succeed();
    }

    public static void fireAndFreezeUseSingleCompressedTimeline(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();

        try {
            setRules(rules, server, true, true, false, false);

            Zombie evenFire = createZombie(level);
            evenFire.setHealth(20.0F);
            evenFire.setRemainingFireTicks(100);
            tickBase(evenFire, 4);
            assertEquals(60, evenFire.getRemainingFireTicks(),
                    "Expected four accelerated ticks to consume forty fire ticks");
            assertEquals(18.0F, evenFire.getHealth(),
                    "Expected the compressed 100..61 window to cross two fire damage ticks");

            Zombie oddFire = createZombie(level);
            oddFire.setHealth(20.0F);
            oddFire.setRemainingFireTicks(95);
            tickBase(oddFire, 4);
            assertEquals(55, oddFire.getRemainingFireTicks(),
                    "Expected odd fire timers to preserve their original phase while advancing by ten");
            assertEquals(18.0F, oddFire.getHealth(),
                    "Expected the compressed 95..56 window to cross the 80 and 60 damage ticks");

            rules.set(ModGameRules.FASTER_EFFECT_TICKING, false, server);
            Zombie vanillaFire = createZombie(level);
            vanillaFire.setHealth(20.0F);
            vanillaFire.setRemainingFireTicks(100);
            tickBase(vanillaFire, 4);
            assertEquals(96, vanillaFire.getRemainingFireTicks(),
                    "Expected the disabled rule to retain vanilla fire duration");
            assertEquals(19.0F, vanillaFire.getHealth(),
                    "Expected one vanilla fire damage tick");

            Zombie vanillaFreeze = createZombie(level);
            vanillaFreeze.setHealth(20.0F);
            vanillaFreeze.setTicksFrozen(vanillaFreeze.getTicksRequiredToFreeze() + 200);
            tickFreeze(vanillaFreeze, 10);
            assertEquals(19.0F, vanillaFreeze.getHealth(),
                    "Expected one vanilla freeze damage tick across forty ticks");

            rules.set(ModGameRules.FASTER_EFFECT_TICKING, true, server);
            Zombie acceleratedFreeze = createZombie(level);
            acceleratedFreeze.setHealth(20.0F);
            acceleratedFreeze.setTicksFrozen(acceleratedFreeze.getTicksRequiredToFreeze() + 200);
            tickFreeze(acceleratedFreeze, 10);
            assertEquals(10.0F, acceleratedFreeze.getHealth(),
                    "Expected ten compressed freeze damage ticks across forty virtual ticks");

            Zombie acceleratedThaw = createZombie(level);
            acceleratedThaw.setTicksFrozen(acceleratedThaw.getTicksRequiredToFreeze() + 20);
            acceleratedThaw.tickCount = 1;
            acceleratedThaw.aiStep();
            assertEquals(
                    acceleratedThaw.getTicksRequiredToFreeze(),
                    acceleratedThaw.getTicksFrozen(),
                    "Expected thawing to advance ten times faster without underflow"
            );

            Zombie acceleratedBuildUp = createZombie(level);
            acceleratedBuildUp.setTicksFrozen(acceleratedBuildUp.getTicksRequiredToFreeze() - 15);
            InsideBlockEffectType.FREEZE.effect().accept(acceleratedBuildUp);
            assertEquals(acceleratedBuildUp.getTicksRequiredToFreeze() - 5, acceleratedBuildUp.getTicksFrozen(),
                    "Expected powder snow to build up freezing ten times faster");
            InsideBlockEffectType.FREEZE.effect().accept(acceleratedBuildUp);
            assertEquals(acceleratedBuildUp.getTicksRequiredToFreeze(), acceleratedBuildUp.getTicksFrozen(),
                    "Expected accelerated freeze build-up to stop at the fully frozen threshold");

            rules.set(ModGameRules.FASTER_EFFECT_TICKING, false, server);
            Zombie vanillaBuildUp = createZombie(level);
            InsideBlockEffectType.FREEZE.effect().accept(vanillaBuildUp);
            assertEquals(1, vanillaBuildUp.getTicksFrozen(), "Expected vanilla powder snow build-up");
        } finally {
            setRules(rules, server, false, false, false, false);
        }

        context.succeed();
    }

    public static void effectCadenceDurationAndTransitions(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();

        try {
            setRules(rules, server, false, true, false, false);
            Zombie entity = createZombie(level);

            MobEffectInstance speed = new MobEffectInstance(MobEffects.SPEED, 15);
            assertTrue(speed.tickServer(level, entity, () -> { }),
                    "Expected a non-damage effect to remain after one accelerated tick");
            assertEquals(5, speed.getDuration(),
                    "Expected every finite status effect duration to advance by ten");
            assertFalse(speed.tickServer(level, entity, () -> { }),
                    "Expected a short status effect to expire without overshooting below zero");
            assertEquals(0, speed.getDuration(), "Expected finite duration to clamp at zero");

            MobEffectInstance hidden = new MobEffectInstance(MobEffects.SPEED, 100, 0);
            MobEffectInstance visible = new MobEffectInstance(
                    MobEffects.SPEED, 5, 1, false, true, true, hidden
            );
            int[] transitions = {0};
            assertTrue(visible.tickServer(level, entity, () -> transitions[0]++),
                    "Expected expiration to promote the hidden effect");
            assertEquals(0, visible.getAmplifier(), "Expected the hidden amplifier to become active");
            assertEquals(90, visible.getDuration(),
                    "Expected the promoted hidden effect to advance on the same compressed timeline");
            assertEquals(1, transitions[0], "Expected one hidden-effect transition callback");

            entity.setHealth(10.0F);
            MobEffectInstance regeneration = new MobEffectInstance(MobEffects.REGENERATION, 57);
            regeneration.tickServer(level, entity, () -> { });
            assertEquals(11.0F, entity.getHealth(),
                    "Expected generic periodic effects to detect ticks crossed by the compressed window");
            assertEquals(47, regeneration.getDuration(),
                    "Expected non-multiple durations to retain their phase");

            entity = createZombie(level);
            entity.setHealth(20.0F);
            MobEffectInstance poison = new MobEffectInstance(MobEffects.POISON, 12, 2);
            poison.tickServer(level, entity, () -> { });
            assertEquals(19.0F, entity.getHealth(),
                    "Expected a poison tick six virtual ticks after the last hit to stay inside the invincibility window");

            entity = createZombie(level);
            entity.setHealth(20.0F);
            MobEffectInstance wither = new MobEffectInstance(MobEffects.WITHER, 10, 3);
            wither.tickServer(level, entity, () -> { });
            assertEquals(19.0F, entity.getHealth(),
                    "Expected a wither tick five virtual ticks after the last hit to stay inside the invincibility window");

            rules.set(ModGameRules.FASTER_EFFECT_TICKING, false, server);
            float vanillaPoisonDamage = poisonDamageOverServerTicks(level, 120);
            rules.set(ModGameRules.FASTER_EFFECT_TICKING, true, server);
            assertEquals(vanillaPoisonDamage, poisonDamageOverServerTicks(level, 12),
                    "Expected high-amplifier poison to deal the vanilla damage of 120 ticks in 12 accelerated ticks");

            rules.set(ModGameRules.FASTER_EFFECT_TICKING, false, server);
            float vanillaCloudDamage = cloudDamageOverServerTicks(context, 40);
            rules.set(ModGameRules.FASTER_EFFECT_TICKING, true, server);
            assertTrue(vanillaCloudDamage > 0.0F, "Expected a harming cloud to hurt a mob standing in it");
            assertEquals(vanillaCloudDamage, cloudDamageOverServerTicks(context, 4),
                    "Expected a harming cloud to deal the vanilla damage of 40 ticks in 4 accelerated ticks");

            entity = createZombie(level);
            entity.setHealth(1.5F);
            poison = new MobEffectInstance(MobEffects.POISON, 12, 2);
            poison.tickServer(level, entity, () -> { });
            assertEquals(0.5F, entity.getHealth(),
                    "Expected poison to retain its nonlethal final-health behavior");
            assertTrue(entity.isAlive(), "Expected poison not to kill its target");

            entity = createZombie(level);
            entity.setHealth(1.0F);
            wither = new MobEffectInstance(MobEffects.WITHER, 10, 3);
            wither.tickServer(level, entity, () -> { });
            assertFalse(entity.isAlive(), "Expected wither to retain lethal final-health behavior");

            entity = createZombie(level);
            entity.setHealth(10.0F);
            MobEffectInstance infinite = new MobEffectInstance(
                    MobEffects.REGENERATION, MobEffectInstance.INFINITE_DURATION
            );
            infinite.tickServer(level, entity, () -> { });
            infinite.tickServer(level, entity, () -> { });
            assertEquals(MobEffectInstance.INFINITE_DURATION, infinite.getDuration(),
                    "Expected infinite effects to remain infinite");
            assertEquals(11.0F, entity.getHealth(),
                    "Expected consecutive infinite windows not to count the same scheduled tick twice");
        } finally {
            setRules(rules, server, false, false, false, false);
        }

        context.succeed();
    }

    public static void mobAttacksUseCompressedTimeline(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();

        try {
            setRules(rules, server, false, false, false, false);
            assertEquals(8, serverTicksUntilFangsBite(context),
                    "Expected vanilla evoker fangs to bite on their eighth tick");

            rules.set(ModGameRules.FASTER_EFFECT_TICKING, true, server);
            assertEquals(1, serverTicksUntilFangsBite(context),
                    "Expected accelerated evoker fangs to bite within the server tick holding virtual tick eight");
        } finally {
            setRules(rules, server, false, false, false, false);
        }

        context.succeed();
    }

    public static void attackCooldownIsImmediateForExistingAndJoiningPlayers(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();
        ServerPlayer existingPlayer = null;
        ServerPlayer joiningPlayer = null;

        try {
            setRules(rules, server, false, false, false, false);
            existingPlayer = context.makeMockServerPlayerInLevel();
            existingPlayer.resetAttackStrengthTicker();
            assertTrue(existingPlayer.getAttackStrengthScale(0.0F) < 1.0F,
                    "Expected vanilla attack charge immediately after attacking");

            ItemStack gatedWeapon = new ItemStack(Items.IRON_SWORD);
            gatedWeapon.set(DataComponents.MINIMUM_ATTACK_CHARGE, 1.0F);
            assertTrue(existingPlayer.cannotAttackWithItem(gatedWeapon, 0),
                    "Expected vanilla minimum-charge enforcement");

            rules.set(ModGameRules.DISABLE_ATTACK_COOLDOWN, true, server);
            assertEquals(1.0F, existingPlayer.getAttackStrengthScale(0.0F),
                    "Expected an existing player to become fully charged immediately");
            assertFalse(existingPlayer.cannotAttackWithItem(gatedWeapon, 0),
                    "Expected the rule to bypass minimum-charge enforcement");

            joiningPlayer = context.makeMockServerPlayerInLevel();
            joiningPlayer.resetAttackStrengthTicker();
            assertEquals(1.0F, joiningPlayer.getAttackStrengthScale(0.0F),
                    "Expected a player joining while enabled to be fully charged immediately");

            rules.set(ModGameRules.DISABLE_ATTACK_COOLDOWN, false, server);
            existingPlayer.resetAttackStrengthTicker();
            joiningPlayer.resetAttackStrengthTicker();
            assertTrue(existingPlayer.getAttackStrengthScale(0.0F) < 1.0F,
                    "Expected disabling the rule to restore vanilla attack charge");
            assertTrue(joiningPlayer.getAttackStrengthScale(0.0F) < 1.0F,
                    "Expected vanilla charge restoration for players that joined while enabled");
        } finally {
            setRules(rules, server, false, false, false, false);
            removePlayer(server, existingPlayer);
            removePlayer(server, joiningPlayer);
        }

        context.succeed();
    }

    public static void instantShootFiresBowCrossbowAndTrident(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        IronGolem[] target = {null};

        setRules(rules, server, false, false, false, false);
        assertTrue(BowItem.getPowerForTime(1) < 1.0F,
                "Expected one tick not to fully charge a vanilla bow");
        setRules(rules, server, false, false, false, true);
        assertEquals(1.0F, BowItem.getPowerForTime(1),
                "Expected one tick to fully charge a bow when instant shooting is enabled");
        Vec3 playerPosition = context.absoluteVec(new Vec3(2.5, 2.0, 2.5));
        player.snapTo(playerPosition.x, playerPosition.y, playerPosition.z);
        player.forceSetRotation(0.0F, false, 0.0F, false);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 16));

        GameTestSequence sequence = context.startSequence();
        sequence.thenExecute(() -> {
            target[0] = createTarget(context);
            setRules(rules, server, false, false, false, true);
            aimForward(player);
            ItemStack bow = new ItemStack(Items.BOW);
            player.setItemInHand(InteractionHand.MAIN_HAND, bow);
            bow.use(level, player, InteractionHand.MAIN_HAND);
            boolean fired = ((BowItem) Items.BOW).releaseUsing(
                    bow, level, player, bow.getUseDuration(player) - 1
            );
            assertTrue(fired, "Expected the one-tick bow use to fire");
            assertFalse(
                    level.getEntitiesOfClass(
                            AbstractArrow.class,
                            player.getBoundingBox().inflate(20.0)
                    ).isEmpty(),
                    "Expected the one-tick bow use to create an arrow"
            );
            directProjectilesAt(level, player, target[0], "bow");
        });
        sequence.thenWaitUntil(() -> assertDamaged(target[0], "bow"));
        sequence.thenExecute(() -> {
            target[0].discard();
            discardProjectiles(level, player.getBoundingBox().inflate(20.0));
            target[0] = createTarget(context);
            setRules(rules, server, false, false, false, true);
            aimForward(player);
            ItemStack crossbow = new ItemStack(Items.CROSSBOW);
            player.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
            crossbow.use(level, player, InteractionHand.MAIN_HAND);
            assertEquals(0, CrossbowItem.getChargeDuration(crossbow, player),
                    "Expected the enabled rule to set crossbow charge duration to zero");
            assertFalse(player.getProjectile(crossbow).isEmpty(),
                    "Expected the test player to have crossbow ammunition");
            int remainingUseDuration = crossbow.getUseDuration(player) - 1;
            ((CrossbowItem) Items.CROSSBOW).onUseTick(
                    level, player, crossbow, remainingUseDuration
            );
            ((CrossbowItem) Items.CROSSBOW).releaseUsing(
                    crossbow, level, player, remainingUseDuration
            );
            assertTrue(CrossbowItem.isCharged(crossbow),
                    "Expected a crossbow to charge after one server tick");
            crossbow.use(level, player, InteractionHand.MAIN_HAND);
            directProjectilesAt(level, player, target[0], "crossbow");
        });
        sequence.thenWaitUntil(() -> assertDamaged(target[0], "crossbow"));
        sequence.thenExecute(() -> {
            target[0].discard();
            discardProjectiles(level, player.getBoundingBox().inflate(20.0));
            target[0] = createTarget(context);
            setRules(rules, server, false, false, false, true);
            aimForward(player);
            ItemStack trident = new ItemStack(Items.TRIDENT);
            player.setItemInHand(InteractionHand.MAIN_HAND, trident);
            trident.use(level, player, InteractionHand.MAIN_HAND);
            boolean thrown = ((TridentItem) Items.TRIDENT).releaseUsing(
                    trident, level, player, trident.getUseDuration(player) - 1
            );
            assertTrue(thrown, "Expected a trident to throw after one server tick");
            directProjectilesAt(level, player, target[0], "trident");
        });
        sequence.thenWaitUntil(() -> assertDamaged(target[0], "trident"));
        sequence.thenExecute(() -> {
            target[0].discard();
            discardProjectiles(level, player.getBoundingBox().inflate(20.0));
            setRules(rules, server, false, false, false, false);
            ItemStack trident = new ItemStack(Items.TRIDENT);
            player.setItemInHand(InteractionHand.MAIN_HAND, trident);
            trident.use(level, player, InteractionHand.MAIN_HAND);
            boolean thrown = ((TridentItem) Items.TRIDENT).releaseUsing(
                    trident, level, player, trident.getUseDuration(player) - 1
            );
            assertFalse(thrown, "Expected disabling instant shooting to restore the vanilla trident threshold");
            ItemStack crossbow = new ItemStack(Items.CROSSBOW);
            player.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
            crossbow.use(level, player, InteractionHand.MAIN_HAND);
            int remainingUseDuration = crossbow.getUseDuration(player) - 1;
            ((CrossbowItem) Items.CROSSBOW).onUseTick(
                    level, player, crossbow, remainingUseDuration
            );
            ((CrossbowItem) Items.CROSSBOW).releaseUsing(
                    crossbow, level, player, remainingUseDuration
            );
            assertFalse(CrossbowItem.isCharged(crossbow),
                    "Expected disabling instant shooting to restore vanilla crossbow charging");
            removePlayer(server, player);
        });
        sequence.thenSucceed();
    }

    private static int serverTicksUntilFangsBite(GameTestHelper context) {
        Zombie victim = context.spawn(EntityTypes.ZOMBIE, new Vec3(1.5, 2.0, 1.5));
        victim.setNoAi(true);
        float health = victim.getHealth();
        Vec3 position = victim.position();
        EvokerFangs fangs = new EvokerFangs(context.getLevel(), position.x, position.y, position.z, 0.0F, 0, null);
        try {
            for (int tick = 1; tick <= 30; tick++) {
                fangs.tick();
                if (victim.getHealth() < health) {
                    return tick;
                }
            }
            return -1;
        } finally {
            fangs.discard();
            victim.discard();
        }
    }

    private static Zombie createZombie(ServerLevel level) {
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (zombie == null) {
            throw new AssertionError("Expected to create a zombie");
        }
        return zombie;
    }

    private static IronGolem createTarget(GameTestHelper context) {
        IronGolem target = context.spawn(EntityTypes.IRON_GOLEM, new Vec3(2.5, 2.0, 6.0));
        target.setNoAi(true);
        target.setHealth(target.getMaxHealth());
        return target;
    }

    private static void tickBase(Zombie zombie, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            zombie.baseTick();
        }
    }

    private static void tickFreeze(Zombie zombie, int applications) {
        for (int application = 1; application <= applications; application++) {
            zombie.setTicksFrozen(zombie.getTicksRequiredToFreeze() + 20);
            zombie.tickCount = application * 4;
            zombie.baseTick();
            zombie.aiStep();
        }
    }

    private static float cloudDamageOverServerTicks(GameTestHelper context, int serverTicks) {
        Pig victim = context.spawn(EntityTypes.PIG, new Vec3(1.5, 2.0, 1.5));
        victim.setNoAi(true);
        float health = victim.getHealth();
        Vec3 position = victim.position();
        AreaEffectCloud cloud = new AreaEffectCloud(context.getLevel(), position.x, position.y, position.z);
        cloud.setRadius(3.0F);
        cloud.setWaitTime(0);
        cloud.setDuration(600);
        cloud.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE));
        try {
            for (int tick = 0; tick < serverTicks && !cloud.isRemoved(); tick++) {
                cloud.tickCount++;
                cloud.tick();
            }
            return health - victim.getHealth();
        } finally {
            cloud.discard();
            victim.discard();
        }
    }

    private static float poisonDamageOverServerTicks(ServerLevel level, int serverTicks) {
        Zombie entity = createZombie(level);
        entity.setHealth(20.0F);
        MobEffectInstance poison = new MobEffectInstance(MobEffects.POISON, 120, 2);
        for (int tick = 0; tick < serverTicks; tick++) {
            entity.baseTick();
            poison.tickServer(level, entity, () -> { });
        }
        return 20.0F - entity.getHealth();
    }

    private static void aimForward(ServerPlayer player) {
        player.forceSetRotation(0.0F, false, 0.0F, false);
    }

    private static void assertDamaged(IronGolem target, String weapon) {
        assertTrue(target != null && target.getHealth() < target.getMaxHealth(),
                "Expected the one-tick " + weapon + " shot to damage its target");
    }

    private static void directProjectilesAt(
            ServerLevel level,
            ServerPlayer player,
            IronGolem target,
            String weapon
    ) {
        List<AbstractArrow> projectiles = level.getEntitiesOfClass(
                AbstractArrow.class,
                player.getBoundingBox().inflate(20.0)
        );
        assertFalse(projectiles.isEmpty(),
                "Expected the one-tick " + weapon + " use to create a projectile");
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        for (AbstractArrow projectile : projectiles) {
            projectile.setDeltaMovement(
                    targetCenter.subtract(projectile.position()).normalize().scale(3.0)
            );
        }
        for (int tick = 0; tick < 3 && target.getHealth() == target.getMaxHealth(); tick++) {
            for (AbstractArrow projectile : projectiles) {
                if (!projectile.isRemoved()) {
                    projectile.tick();
                }
            }
        }
    }

    private static void discardProjectiles(ServerLevel level, AABB bounds) {
        for (AbstractArrow projectile : level.getEntitiesOfClass(AbstractArrow.class, bounds)) {
            projectile.discard();
        }
    }

    private static void removePlayer(MinecraftServer server, ServerPlayer player) {
        if (player != null && server.getPlayerList().getPlayer(player.getUUID()) != null) {
            server.getPlayerList().remove(player);
        }
    }

    private static void setRules(
            GameRules rules,
            MinecraftServer server,
            boolean disableIFrames,
            boolean fasterEffects,
            boolean disableAttackCooldown,
            boolean instantShoot
    ) {
        rules.set(ModGameRules.DISABLE_IFRAMES, disableIFrames, server);
        rules.set(ModGameRules.FASTER_EFFECT_TICKING, fasterEffects, server);
        rules.set(ModGameRules.DISABLE_ATTACK_COOLDOWN, disableAttackCooldown, server);
        rules.set(ModGameRules.INSTANT_SHOOT, instantShoot, server);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(Component.literal(message), 0);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
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
