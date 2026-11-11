package io.github.brainage04.accelerateddamage.gametest;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/// Knockback comes only from a hit that takes the full-damage path of `LivingEntity#hurtServer`.
/// With `disable_i_frames` every hit takes that path, so every hit knocks its target back; vanilla
/// rejects repeat hits inside the ten-tick part of the twenty-tick cooldown and knocks back only
/// the first, and 26.3 also skips knockback for a hit that was fully blocked. Every case below
/// pairs the enabled rule with the same shot sequence while the rule is off.
public final class AcceleratedDamageKnockbackScenarios {
    private AcceleratedDamageKnockbackScenarios() {}

    public static void everyHitKnocksBackWhileIFramesAreDisabled(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();

        try {
            setRules(rules, server, false, false, false, false);
            Zombie attacker = spawnZombie(context, 3.5, 1.5);
            Zombie victim = spawnZombie(context, 4.5, 5.5);
            DamageSource melee = level.damageSources().mobAttack(attacker);

            assertTrue(strike(level, victim, melee), "Expected the first vanilla hit to apply");
            assertKnockedBack(victim, "Expected the first vanilla hit to knock its target back");

            assertFalse(
                    strike(level, victim, melee),
                    "Expected vanilla invincibility to reject the second hit");
            assertNotKnockedBack(
                    victim, "Expected vanilla invincibility to skip the second hit's knockback");

            victim.discard();
            rules.set(ModGameRules.DISABLE_IFRAMES, true, server);
            Zombie repeated = spawnZombie(context, 4.5, 5.5);
            for (int hit = 1; hit <= 3; hit++) {
                assertTrue(
                        strike(level, repeated, melee),
                        "Expected hit " + hit + " to apply with invincibility disabled");
                assertKnockedBack(repeated, "Expected hit " + hit + " to knock its target back");
                repeated.setDeltaMovement(Vec3.ZERO);
            }
            assertTrue(
                    repeated.getHealth() < 18.5F,
                    "Expected three accepted one-damage hits to stack, health was "
                            + repeated.getHealth());
        } finally {
            setRules(rules, server, false, false, false, false);
        }

        context.succeed();
    }

    public static void fullyBlockedHitsNeverKnockBack(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();

        try {
            setRules(rules, server, true, false, false, false);
            Zombie attacker = spawnZombie(context, 3.5, 1.5);
            Zombie victim = spawnZombie(context, 4.5, 5.5);
            victim.setYRot(180.0F);
            victim.setYHeadRot(180.0F);
            ItemStack shield = new ItemStack(Items.SHIELD);
            shield.set(DataComponents.BLOCKS_ATTACKS, blockingEveryHit());
            victim.setItemSlot(EquipmentSlot.MAINHAND, shield);
            victim.startUsingItem(InteractionHand.MAIN_HAND);
            assertTrue(victim.isBlocking(), "Expected the victim to hold its shield up");

            DamageSource melee = level.damageSources().mobAttack(attacker);
            float health = victim.getHealth();
            victim.setDeltaMovement(Vec3.ZERO);
            assertFalse(
                    victim.hurtServer(level, melee, 4.0F),
                    "Expected a fully blocked hit to report that no damage landed");
            assertEquals(
                    health, victim.getHealth(), "Expected a fully blocked hit to deal no damage");
            assertNotKnockedBack(victim, "Expected 26.3 to skip knockback for a fully blocked hit");

            victim.stopUsingItem();
            assertFalse(victim.isBlocking(), "Expected lowering the shield to end blocking");
            float unblockedHealth = victim.getHealth();
            assertTrue(
                    strike(level, victim, melee),
                    "Expected the same hit to land once the shield is down");
            assertTrue(
                    victim.getHealth() < unblockedHealth,
                    "Expected the unblocked hit to damage its target");
            assertKnockedBack(victim, "Expected the unblocked hit to knock its target back");
        } finally {
            setRules(rules, server, false, false, false, false);
        }

        context.succeed();
    }

    public static void crossbowFireworkRocketsKnockBackOnEveryHit(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();
        ServerPlayer shooter = MockPlayers.spawn(context);

        try {
            setRules(rules, server, true, false, false, false);
            Zombie victim = spawnZombie(context, 4.5, 5.5);

            float first = landFireworkRocket(level, shooter, victim);
            assertTrue(
                    first > 0.0F,
                    "Expected the first crossbow firework rocket to damage its target");
            assertKnockedBack(
                    victim, "Expected the first crossbow firework rocket to knock its target back");

            reset(victim);
            float second = landFireworkRocket(level, shooter, victim);
            assertTrue(
                    second > 0.0F,
                    "Expected a second firework rocket inside the same cooldown window to deal its damage");
            assertKnockedBack(
                    victim,
                    "Expected the second crossbow firework rocket to knock its target back too");

            rules.set(ModGameRules.DISABLE_IFRAMES, false, server);
            reset(victim);
            assertTrue(
                    landFireworkRocket(level, shooter, victim) > 0.0F,
                    "Expected the first vanilla firework rocket to damage its target");
            resetDelta(victim);
            assertEquals(
                    0.0F,
                    landFireworkRocket(level, shooter, victim),
                    "Expected vanilla invincibility to discard a second firework rocket in the same window");
            assertNotKnockedBack(
                    victim,
                    "Expected vanilla invincibility to skip the discarded rocket's knockback");
        } finally {
            setRules(rules, server, false, false, false, false);
            removePlayer(server, shooter);
        }

        context.succeed();
    }

    public static void multishotCrossbowsKnockBackOncePerProjectile(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();
        ServerPlayer shooter = MockPlayers.spawn(context);

        try {
            setRules(rules, server, true, false, false, false);
            Zombie victim = spawnZombie(context, 4.5, 5.5);
            assertEquals(
                    3,
                    fireMultishot(level, shooter, victim),
                    "Expected a multishot crossbow to launch three projectiles");
            assertEquals(
                    3,
                    landEveryProjectile(level, victim),
                    "Expected every multishot projectile to land while invincibility is disabled");

            rules.set(ModGameRules.DISABLE_IFRAMES, false, server);
            victim.discard();
            victim = spawnZombie(context, 4.5, 5.5);
            assertEquals(
                    3,
                    fireMultishot(level, shooter, victim),
                    "Expected a multishot crossbow to launch three projectiles with the rule off");
            assertEquals(
                    1,
                    landEveryProjectile(level, victim),
                    "Expected vanilla invincibility to keep only the first multishot projectile");
        } finally {
            setRules(rules, server, false, false, false, false);
            removePlayer(server, shooter);
        }

        context.succeed();
    }

    public static void windChargesDamageOnEveryHit(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();

        try {
            setRules(rules, server, true, false, false, false);
            Zombie victim = spawnZombie(context, 4.5, 5.5);
            for (int charge = 1; charge <= 3; charge++) {
                float before = victim.getHealth();
                burstWindCharge(level, victim);
                assertTrue(
                        victim.getHealth() < before,
                        "Expected wind charge " + charge + " to damage its target");
            }

            rules.set(ModGameRules.DISABLE_IFRAMES, false, server);
            reset(victim);
            float before = victim.getHealth();
            burstWindCharge(level, victim);
            assertTrue(
                    victim.getHealth() < before,
                    "Expected the first vanilla wind charge to damage its target");
            for (int charge = 2; charge <= 3; charge++) {
                float health = victim.getHealth();
                burstWindCharge(level, victim);
                assertEquals(
                        health,
                        victim.getHealth(),
                        "Expected vanilla invincibility to discard wind charge " + charge);
            }
        } finally {
            setRules(rules, server, false, false, false, false);
        }

        context.succeed();
    }

    public static void snowballsKnockBackWithoutDamage(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();

        try {
            setRules(rules, server, true, false, false, false);
            Zombie victim = spawnZombie(context, 4.5, 5.5);
            for (int snowball = 1; snowball <= 3; snowball++) {
                resetDelta(victim);
                landSnowball(level, victim);
                assertEquals(
                        victim.getMaxHealth(),
                        victim.getHealth(),
                        "Expected snowball " + snowball + " to deal no damage");
                assertKnockedBack(
                        victim, "Expected snowball " + snowball + " to knock its target back");
            }

            rules.set(ModGameRules.DISABLE_IFRAMES, false, server);
            reset(victim);
            landSnowball(level, victim);
            assertKnockedBack(
                    victim, "Expected the first vanilla snowball to knock its target back");

            resetDelta(victim);
            landSnowball(level, victim);
            assertNotKnockedBack(
                    victim,
                    "Expected vanilla invincibility to skip the second snowball's knockback");
        } finally {
            setRules(rules, server, false, false, false, false);
        }

        context.succeed();
    }

    private static Zombie spawnZombie(GameTestHelper context, double x, double z) {
        Zombie zombie = context.spawn(EntityTypes.ZOMBIE, new Vec3(x, 3.0, z));
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        return zombie;
    }

    private static boolean strike(ServerLevel level, LivingEntity victim, DamageSource source) {
        victim.setDeltaMovement(Vec3.ZERO);
        return victim.hurtServer(level, source, 1.0F);
    }

    private static BlocksAttacks blockingEveryHit() {
        return new BlocksAttacks(
                0.0F,
                1.0F,
                List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
                BlocksAttacks.ItemDamageFunction.DEFAULT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static float landFireworkRocket(
            ServerLevel level, ServerPlayer shooter, LivingEntity victim) {
        shooter.snapTo(victim.getX(), victim.getY(), victim.getZ() - 2.5);
        aimAt(shooter, victim);

        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(FireworkExplosion.DEFAULT)));
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.set(
                DataComponents.CHARGED_PROJECTILES,
                ChargedProjectiles.of(ItemStackTemplate.fromNonEmptyStack(rocket)));
        shooter.setItemInHand(InteractionHand.MAIN_HAND, crossbow);

        float before = victim.getHealth();
        victim.setDeltaMovement(Vec3.ZERO);
        crossbow.use(level, shooter, InteractionHand.MAIN_HAND);

        List<FireworkRocketEntity> launched =
                level.getEntitiesOfClass(
                        FireworkRocketEntity.class, victim.getBoundingBox().inflate(16.0));
        assertEquals(1, launched.size(), "Expected the crossbow to launch one firework rocket");
        FireworkRocketEntity firework = launched.getFirst();
        for (int tick = 0; tick < 10 && !firework.isRemoved(); tick++) {
            firework.setDeltaMovement(
                    victim.getBoundingBox()
                            .getCenter()
                            .subtract(firework.position())
                            .normalize()
                            .scale(3.0));
            firework.tick();
        }
        assertTrue(firework.isRemoved(), "Expected the firework rocket to explode on its target");
        return before - victim.getHealth();
    }

    private static int fireMultishot(ServerLevel level, ServerPlayer shooter, LivingEntity victim) {
        for (AbstractArrow arrow :
                level.getEntitiesOfClass(
                        AbstractArrow.class, victim.getBoundingBox().inflate(16.0))) {
            arrow.discard();
        }

        Holder<Enchantment> multishot =
                level.holderLookup(Registries.ENCHANTMENT)
                        .get(Enchantments.MULTISHOT)
                        .orElseThrow(
                                () -> new AssertionError("Expected the multishot enchantment"));
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.enchant(multishot, 1);
        crossbow.set(
                DataComponents.CHARGED_PROJECTILES,
                ChargedProjectiles.ofNonEmpty(
                        List.of(
                                new ItemStack(Items.ARROW),
                                new ItemStack(Items.ARROW),
                                new ItemStack(Items.ARROW))));
        shooter.snapTo(victim.getX(), victim.getY(), victim.getZ() - 2.5);
        aimAt(shooter, victim);
        shooter.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
        crossbow.use(level, shooter, InteractionHand.MAIN_HAND);

        return level.getEntitiesOfClass(AbstractArrow.class, victim.getBoundingBox().inflate(16.0))
                .size();
    }

    /// Ticks every projectile into the victim in turn and reports how many of them took the
    /// full-damage path, which a hit shows by knocking its target back. A projectile the cooldown
    /// rejects can still pass on the damage that exceeds the window's largest hit, so the health
    /// alone does not tell a landed hit from a rejected one.
    private static int landEveryProjectile(ServerLevel level, LivingEntity victim) {
        List<AbstractArrow> projectiles =
                level.getEntitiesOfClass(
                        AbstractArrow.class, victim.getBoundingBox().inflate(16.0));
        int landed = 0;
        for (AbstractArrow projectile : projectiles) {
            if (projectile.isRemoved()) {
                continue;
            }
            victim.setDeltaMovement(Vec3.ZERO);
            float before = victim.getHealth();
            double peak = 0.0D;
            for (int tick = 0; tick < 10 && !projectile.isRemoved(); tick++) {
                projectile.setDeltaMovement(
                        victim.getBoundingBox()
                                .getCenter()
                                .subtract(projectile.position())
                                .normalize()
                                .scale(3.0));
                projectile.tick();
                peak = Math.max(peak, victim.getDeltaMovement().horizontalDistanceSqr());
            }
            if (peak >= 0.09D) {
                landed++;
                assertTrue(
                        victim.getHealth() < before,
                        "Expected a knocked-back multishot projectile to damage its target too");
            }
            projectile.discard();
        }
        return landed;
    }

    private static void burstWindCharge(ServerLevel level, LivingEntity victim) {
        WindCharge charge = new WindCharge(EntityTypes.WIND_CHARGE, level);
        charge.setPos(victim.getX(), victim.getY() + 0.8, victim.getZ() - 1.5);
        level.addFreshEntity(charge);
        for (int tick = 0; tick < 10 && !charge.isRemoved(); tick++) {
            charge.setDeltaMovement(0.0, 0.0, 1.5);
            charge.tick();
        }
        assertTrue(charge.isRemoved(), "Expected the wind charge to burst on its target");
        charge.discard();
    }

    private static void landSnowball(ServerLevel level, LivingEntity victim) {
        Snowball snowball =
                new Snowball(
                        level,
                        victim.getX(),
                        victim.getY() + 1.0,
                        victim.getZ() - 1.5,
                        new ItemStack(Items.SNOWBALL));
        level.addFreshEntity(snowball);
        for (int tick = 0; tick < 10 && !snowball.isRemoved(); tick++) {
            snowball.setDeltaMovement(0.0, 0.0, 1.5);
            snowball.tick();
        }
        assertTrue(snowball.isRemoved(), "Expected the snowball to hit its target");
    }

    private static void aimAt(LivingEntity shooter, LivingEntity target) {
        Vec3 centre = target.getBoundingBox().getCenter();
        double xd = centre.x - shooter.getX();
        double yd = centre.y - shooter.getEyeY();
        double zd = centre.z - shooter.getZ();
        double horizontal = Math.sqrt(xd * xd + zd * zd);
        float yRot = (float) (Math.toDegrees(Math.atan2(zd, xd))) - 90.0F;
        float xRot = (float) -Math.toDegrees(Math.atan2(yd, horizontal));
        shooter.forceSetRotation(yRot, false, xRot, false);
        shooter.setYHeadRot(yRot);
    }

    private static void reset(LivingEntity victim) {
        victim.setHealth(victim.getMaxHealth());
        victim.damageCooldownTime = 0;
        victim.hurtDuration = 0;
        victim.hurtTime = 0;
        resetDelta(victim);
    }

    private static void resetDelta(LivingEntity victim) {
        victim.setDeltaMovement(Vec3.ZERO);
    }

    private static void assertKnockedBack(LivingEntity victim, String message) {
        if (victim.getDeltaMovement().horizontalDistanceSqr() < 0.09) {
            throw new GameTestAssertException(
                    Component.literal(message + ": knockback was " + victim.getDeltaMovement()), 0);
        }
    }

    private static void assertNotKnockedBack(LivingEntity victim, String message) {
        if (victim.getDeltaMovement().horizontalDistanceSqr() > 1.0E-6) {
            throw new GameTestAssertException(
                    Component.literal(message + ": knockback was " + victim.getDeltaMovement()), 0);
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
            boolean instantShoot) {
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
                    Component.literal(message + ": expected " + expected + ", found " + actual), 0);
        }
    }

    private static void assertEquals(float expected, float actual, String message) {
        if (Float.compare(expected, actual) != 0) {
            throw new GameTestAssertException(
                    Component.literal(message + ": expected " + expected + ", found " + actual), 0);
        }
    }
}
