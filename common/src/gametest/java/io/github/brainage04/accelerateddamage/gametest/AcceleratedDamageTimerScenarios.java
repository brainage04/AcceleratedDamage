package io.github.brainage04.accelerateddamage.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.behavior.CountDownCooldownTicks;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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

    public static void lingeringCloudsExpireOnTheSelectedTimeline(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        boolean previous = level.getGameRules().get(ModGameRules.FASTER_EFFECT_TICKING);
        try {
            for (boolean enabled : new boolean[]{false, true}) {
                level.getGameRules().set(ModGameRules.FASTER_EFFECT_TICKING, enabled, level.getServer());
                Vec3 position = context.absoluteVec(new Vec3(3.5, 3.0, 3.5));
                AreaEffectCloud cloud = new AreaEffectCloud(level, position.x, position.y, position.z);
                cloud.setWaitTime(0);
                cloud.setDuration(30);
                try {
                    for (int tick = 0; tick < 3; tick++) {
                        // ServerLevel normally advances entity age before calling tick().
                        cloud.tickCount++;
                        cloud.tick();
                    }
                    context.assertTrue(cloud.isRemoved() == enabled,
                            "A thirty-tick lingering cloud must expire after three ticks only when accelerated");
                } finally {
                    cloud.discard();
                }
            }
        } finally {
            level.getGameRules().set(ModGameRules.FASTER_EFFECT_TICKING, previous, level.getServer());
        }
        context.succeed();
    }

    public static void healingMeleeAndRamCooldowns(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        boolean previous = level.getGameRules().get(ModGameRules.FASTER_EFFECT_TICKING);
        try {
            for (boolean enabled : new boolean[]{false, true}) {
                level.getGameRules().set(ModGameRules.FASTER_EFFECT_TICKING, enabled, level.getServer());
                HappyGhast ghast = create(context, EntityTypes.HAPPY_GHAST);
                ghast.setHealth(5.0F);
                ghast.tickCount = 60;
                ghast.aiStep();
                context.assertTrue(ghast.getHealth() == (enabled ? 6.0F : 5.0F),
                        "A dry happy ghast must heal on its compressed sixty-tick interval only when enabled");

                Zombie attacker = create(context, EntityTypes.ZOMBIE);
                LivingEntity victim = create(context, EntityTypes.IRON_GOLEM);
                victim.setPos(attacker.position().add(0.5, 0.0, 0.0));
                attacker.setTarget(victim);
                MeleeAttackGoal goal = new MeleeAttackGoal(attacker, 1.0, true);
                try {
                    goal.tick();
                    float firstHealth = victim.getHealth();
                    context.assertTrue(firstHealth < victim.getMaxHealth(),
                            "The melee fixture must land its initial attack");
                    for (int tick = 0; tick < 2; tick++) {
                        victim.invulnerableTime = 0;
                        goal.tick();
                    }
                    context.assertTrue((victim.getHealth() < firstHealth) == enabled,
                            "A twenty-tick melee cooldown must allow another hit after two accelerated ticks");
                } finally {
                    goal.stop();
                }

                Goat goat = create(context, EntityTypes.GOAT);
                goat.getBrain().setMemory(MemoryModuleType.RAM_COOLDOWN_TICKS, 20);
                CountDownCooldownTicks countdown = new CountDownCooldownTicks(MemoryModuleType.RAM_COOLDOWN_TICKS);
                context.assertTrue(countdown.tryStart(level, goat, 0L), "The ram cooldown behavior must start");
                for (int tick = 1; tick <= 3; tick++) {
                    countdown.tickOrStop(level, goat, tick);
                }
                context.assertTrue(goat.getBrain().getMemory(MemoryModuleType.RAM_COOLDOWN_TICKS).isEmpty() == enabled,
                        "Ram cooldown memory must expire on the compressed timeline only when enabled");
            }
        } finally {
            level.getGameRules().set(ModGameRules.FASTER_EFFECT_TICKING, previous, level.getServer());
        }
        context.succeed();
    }

    public static void beaconAndConduitPulses(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        context.startSequence()
                .thenWaitUntil(() -> context.assertTrue(level.getGameTime() % 80L == 0L,
                        "Waiting for a vanilla beacon pulse boundary"))
                .thenExecute(() -> assertBeaconAmbientOnVanillaPulse(context))
                .thenWaitUntil(() -> context.assertTrue(level.getGameTime() % 80L == 8L,
                        "Waiting for a non-vanilla beacon and conduit pulse boundary"))
                .thenExecute(() -> assertBlockPulses(context))
                .thenSucceed();
    }

    private static void assertBeaconAmbientOnVanillaPulse(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        boolean previous = level.getGameRules().get(ModGameRules.FASTER_EFFECT_TICKING);
        BlockPos center = context.absolutePos(new BlockPos(3, 3, 3));
        Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();
        SoundListener listener = SoundListener.join(level);
        try {
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 0, 1))) {
                originalBlocks.put(pos.immutable(), level.getBlockState(pos));
            }
            BeaconBlockEntity beacon = placeBeacon(context, center);
            listener.player().setPos(Vec3.atCenterOf(center.above()));
            level.getGameRules().set(ModGameRules.FASTER_EFFECT_TICKING, true, level.getServer());
            listener.drainSounds(SoundEvents.BEACON_AMBIENT);
            for (int tick = 0; tick < 3; tick++) {
                BeaconBlockEntity.tick(level, center, beacon.getBlockState(), beacon);
            }
            context.assertTrue(listener.drainSounds(SoundEvents.BEACON_AMBIENT) > 0,
                    "An accelerated beacon must still hum on vanilla's 80-tick pulse");
        } finally {
            listener.leave(level);
            for (Map.Entry<BlockPos, BlockState> entry : originalBlocks.entrySet()) {
                level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            }
            level.getGameRules().set(ModGameRules.FASTER_EFFECT_TICKING, previous, level.getServer());
        }
    }

    private static BeaconBlockEntity placeBeacon(GameTestHelper context, BlockPos center) {
        ServerLevel level = context.getLevel();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlockAndUpdate(center.offset(x, -1, z), Blocks.IRON_BLOCK.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(center, Blocks.BEACON.defaultBlockState());
        BeaconBlockEntity beacon = (BeaconBlockEntity) level.getBlockEntity(center);
        context.assertTrue(beacon != null, "The beacon fixture must create a block entity");
        try {
            Field primaryPower = BeaconBlockEntity.class.getDeclaredField("primaryPower");
            primaryPower.setAccessible(true);
            primaryPower.set(beacon, MobEffects.SPEED);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not configure the beacon's selected effect", exception);
        }
        return beacon;
    }

    /** A mock player whose outbound packets stay readable, so a test can see which sounds the server sent it. */
    private record SoundListener(ServerPlayer player, EmbeddedChannel channel) {
        static SoundListener join(ServerLevel level) {
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                    new GameProfile(UUID.randomUUID(), "test-listener"), false);
            ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            EmbeddedChannel channel = new EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            return new SoundListener(player, channel);
        }

        int drainSounds(SoundEvent sound) {
            int count = 0;
            for (Object message = channel.readOutbound(); message != null; message = channel.readOutbound()) {
                if (message instanceof ClientboundSoundPacket packet && packet.getSound().value() == sound) {
                    count++;
                }
            }
            return count;
        }

        void leave(ServerLevel level) {
            level.getServer().getPlayerList().remove(player);
            channel.releaseOutbound();
        }
    }

    private static void assertBlockPulses(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        boolean previous = level.getGameRules().get(ModGameRules.FASTER_EFFECT_TICKING);
        BlockPos center = context.absolutePos(new BlockPos(3, 3, 3));
        Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();
        SoundListener listener = SoundListener.join(level);
        ServerPlayer player = listener.player();
        try {
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
                originalBlocks.put(pos.immutable(), level.getBlockState(pos));
            }
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -2; z <= 2; z++) {
                        boolean frame = y == 0 && (Math.abs(x) == 2 || Math.abs(z) == 2);
                        level.setBlockAndUpdate(center.offset(x, y, z),
                                (frame ? Blocks.PRISMARINE : Blocks.WATER).defaultBlockState());
                    }
                }
            }
            player.setPos(Vec3.atCenterOf(center));
            player.baseTick();
            ConduitBlockEntity conduit = new ConduitBlockEntity(center, Blocks.CONDUIT.defaultBlockState());
            level.getGameRules().set(ModGameRules.FASTER_EFFECT_TICKING, false, level.getServer());
            ConduitBlockEntity.serverTick(level, center, conduit.getBlockState(), conduit);
            context.assertTrue(!conduit.isActive() && !player.hasEffect(MobEffects.CONDUIT_POWER),
                    "A vanilla conduit must not pulse on an eight-tick boundary");
            level.getGameRules().set(ModGameRules.FASTER_EFFECT_TICKING, true, level.getServer());
            ConduitBlockEntity.serverTick(level, center, conduit.getBlockState(), conduit);
            context.assertTrue(conduit.isActive() && player.hasEffect(MobEffects.CONDUIT_POWER),
                    "An accelerated conduit must activate and grant power on its four-tick pulse");
            player.removeEffect(MobEffects.CONDUIT_POWER);

            for (BlockPos pos : originalBlocks.keySet()) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            BeaconBlockEntity beacon = placeBeacon(context, center);
            player.setPos(Vec3.atCenterOf(center.above()));
            level.getGameRules().set(ModGameRules.FASTER_EFFECT_TICKING, false, level.getServer());
            for (int tick = 0; tick < 3; tick++) {
                BeaconBlockEntity.tick(level, center, beacon.getBlockState(), beacon);
            }
            context.assertTrue(!player.hasEffect(MobEffects.SPEED),
                    "A vanilla beacon must not pulse on an eight-tick boundary");
            level.getGameRules().set(ModGameRules.FASTER_EFFECT_TICKING, true, level.getServer());
            listener.drainSounds(SoundEvents.BEACON_AMBIENT);
            BeaconBlockEntity.tick(level, center, beacon.getBlockState(), beacon);
            context.assertTrue(player.hasEffect(MobEffects.SPEED),
                    "An accelerated beacon must grant its selected effect on an eight-tick pulse");
            context.assertTrue(listener.drainSounds(SoundEvents.BEACON_AMBIENT) == 0,
                    "An accelerated beacon must not hum on its eight-tick pulse");
        } finally {
            player.removeEffect(MobEffects.SPEED);
            listener.leave(level);
            for (Map.Entry<BlockPos, BlockState> entry : originalBlocks.entrySet()) {
                level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            }
            level.getGameRules().set(ModGameRules.FASTER_EFFECT_TICKING, previous, level.getServer());
        }
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
