package io.github.brainage04.accelerateddamage.gametest;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class AcceleratedDamageTimerScenarios {
    private AcceleratedDamageTimerScenarios() {
    }

    public static void airDryingHealingAndMobWeapons(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        boolean previous = level.getGameRules().get(ModGameRules.FASTER_EFFECT_TICKING);
        try {
            for (boolean enabled : new boolean[]{false, true}) {
                level.getGameRules().set(ModGameRules.FASTER_EFFECT_TICKING, enabled, level.getServer());
                int elapsed = enabled ? 10 : 1;
                assertAirLoss(context, EntityTypes.COD, elapsed);
                assertAirLoss(context, EntityTypes.SALMON, elapsed);
                assertAirLoss(context, EntityTypes.AXOLOTL, elapsed);
                assertAirLoss(context, EntityTypes.NAUTILUS, elapsed);

                Allay allay = create(context, EntityTypes.ALLAY);
                allay.setHealth(5.0F);
                allay.tickCount = 1;
                allay.aiStep();
                context.assertTrue(allay.getHealth() == (enabled ? 6.0F : 5.0F),
                        "Allay self-healing must follow the selected timeline");

                Mob archer = create(context, EntityTypes.SKELETON);
                archer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
                archer.startUsingItem(InteractionHand.MAIN_HAND);
                archer.tick();
                context.assertTrue(archer.getTicksUsingItem() == elapsed,
                        "Mob bow draw must advance once per virtual tick");
                archer.stopUsingItem();

                Mob eater = create(context, EntityTypes.ZOMBIE);
                eater.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
                eater.startUsingItem(InteractionHand.MAIN_HAND);
                eater.tick();
                context.assertTrue(eater.getTicksUsingItem() == 1,
                        "Non-weapon item use must keep vanilla speed");
            }
        } finally {
            level.getGameRules().set(ModGameRules.FASTER_EFFECT_TICKING, previous, level.getServer());
        }
        context.succeed();
    }

    public static void hungerAndStingingAndCreeperFuses(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        boolean previous = level.getGameRules().get(ModGameRules.FASTER_EFFECT_TICKING);
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        try {
            player.setGameMode(GameType.SURVIVAL);
            for (boolean enabled : new boolean[]{false, true}) {
                level.getGameRules().set(ModGameRules.FASTER_EFFECT_TICKING, enabled, level.getServer());
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(5.0F);
                player.setHealth(10.0F);
                for (int tick = 0; tick < 2; tick++) {
                    player.doTick();
                }
                context.assertTrue(enabled ? player.getHealth() > 10.0F : player.getHealth() == 10.0F,
                        "Saturated hunger healing must run ten times faster only when enabled");

                Creeper creeper = create(context, EntityTypes.CREEPER);
                creeper.ignite();
                for (int tick = 0; tick < 3; tick++) {
                    creeper.tick();
                }
                context.assertTrue(creeper.isRemoved() == enabled,
                        "An ignited creeper must finish its thirty-tick fuse in three accelerated ticks");

                Bee bee = create(context, EntityTypes.BEE);
                bee.setNoAi(false);
                bee.getRandom().setSeed(0L);
                context.assertTrue(bee.doHurtTarget(level, create(context, EntityTypes.COW)),
                        "The bee must sting a real target before its lifetime is measured");
                for (int tick = 0; tick < 120 && bee.isAlive(); tick++) {
                    bee.tick();
                }
                context.assertTrue(bee.isAlive() != enabled,
                        "A stung bee must exhaust its lifetime within 120 accelerated ticks, not 120 vanilla ticks");
            }
        } finally {
            level.getGameRules().set(ModGameRules.FASTER_EFFECT_TICKING, previous, level.getServer());
            level.getServer().getPlayerList().remove(player);
        }
        context.succeed();
    }

    private static <T extends LivingEntity> void assertAirLoss(GameTestHelper context, EntityType<T> type, int elapsed) {
        T animal = create(context, type);
        if (animal instanceof Mob mob) {
            mob.setNoAi(false);
        }
        animal.setAirSupply(100);
        animal.baseTick();
        context.assertTrue(animal.getAirSupply() == 100 - elapsed,
                type.toShortString() + " must dry out on the selected timeline");
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
}
