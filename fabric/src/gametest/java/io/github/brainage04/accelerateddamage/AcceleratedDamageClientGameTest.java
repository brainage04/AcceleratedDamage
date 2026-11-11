package io.github.brainage04.accelerateddamage;

import com.mojang.authlib.GameProfile;
import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.minecraftmodconventions.ClientGameTestCommands;
import io.github.brainage04.minecraftmodconventions.ClientGameTestPlayerActions;
import io.github.brainage04.minecraftmodconventions.ClientGameTestRecorder;
import io.github.brainage04.minecraftmodconventions.ClientGameTestServers;
import io.github.brainage04.minecraftmodconventions.ClientGameTestStages;
import io.github.brainage04.minecraftmodconventions.ClientGameTestWaits;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/// The showcase the owner asked for: every rule, filmed against player-shaped targets with the
/// target's health printed in the caption and above its head after every hit, so a hit landing is
/// visible twice.
///
/// <p>Player-shaped targets are {@link ServerPlayer}s, and a {@link ServerPlayer} only runs the
/// {@code Player}/{@code LivingEntity}/{@code Entity} half of its tick from {@code doTick()}, which
/// a real client's connection calls. A fake client's {@code EmbeddedChannel} never ticks that
/// connection, so a target would otherwise never tick its fire, freeze, effects, air supply or
/// hunger. {@link #tickTrackedTargets(MinecraftServer)} calls {@code doTick()} on every target each
/// server tick, which is the call vanilla makes for a real player.
///
/// <p>Every on-camera action goes through {@link ClientGameTestPlayerActions} and the real key
/// bindings: the player turns to a target, stands inside vanilla reach and presses the attack or
/// use key on the crosshair target. Only the camera moves directly. Chat lines from the fake
/// targets joining and leaving are cleared before each stage, and no target dies on camera, so the
/// film has no chat clutter.
@SuppressWarnings("UnstableApiUsage")
public final class AcceleratedDamageClientGameTest implements FabricClientGameTest {
    private static final int ARENA_Y = 64;
    private static final double MELEE_DISTANCE = 2.6D;
    private static final double RANGE_X = -6.5D;
    private static final double FIREWORK_RANGE_Z = 8.5D;

    /// A weapon target survives every hit of its stage, so no death message lands in the film.
    private static final float WEAPON_TARGET_HEALTH = 60.0F;

    /// An effect target is healthy enough to keep showing numbers for the whole stage.
    private static final float EFFECT_TARGET_HEALTH = 300.0F;

    /// Effect targets that only have to survive a short rule-off comparison.
    private static final float COMPARISON_TARGET_HEALTH = 20.0F;

    private static final int READING_TICKS = 14;

    private static final Map<UUID, TrackedTarget> TRACKED = new LinkedHashMap<>();
    private static boolean tickListenerRegistered;

    /// A fake player the showcase keeps ticking, with the position to hold it at (or {@code null}
    /// to let knockback move it) and the text its name tag shows.
    private record TrackedTarget(UUID id, String name, Vec3 anchor) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        registerTickListener();
        Properties serverProperties = ClientGameTestServers.flatServerProperties();

        ClientGameTestServers.withDedicatedServer(
                context,
                serverProperties,
                "Accelerated Damage complete showcase GameTest",
                server -> {
                    int playerId;
                    try {
                        playerId =
                                server.computeOnServer(
                                        current -> {
                                            prepareArena(current);
                                            return current.getPlayerList()
                                                    .getPlayers()
                                                    .getFirst()
                                                    .getId();
                                        });
                        ClientGameTestServers.assertClientWorldAndPlayerAvailable(context);
                        context.waitTicks(20);
                        // The title card covers the tour that loads every viewpoint, so the first
                        // stage never opens on
                        // an unloaded chunk.
                        ClientGameTestRecorder.showStep(
                                context,
                                "showcase.ready",
                                "Accelerated Damage",
                                "Four rules, every hit and every effect");
                        preloadArena(context, server, playerId);
                        ClientGameTestRecorder.startRecording(context);
                        context.waitTicks(10);

                        recordMelee(context, server, playerId);
                        recordDrowning(context, server, playerId);
                        recordBow(context, server, playerId);
                        recordCrossbow(context, server, playerId);
                        recordFirework(context, server, playerId);
                        recordTrident(context, server, playerId);
                        recordDamageEffects(context, server, playerId);
                        recordFreezingAndContact(context, server, playerId);
                        recordHungerCloudAndHealing(context, server, playerId);
                        recordEffectsOff(context, server, playerId);

                        // The last targets left the world a moment ago; their leave lines must not
                        // reach the last
                        // frame.
                        ClientGameTestCommands.clearChat(context);
                        context.waitTicks(4);
                        ClientGameTestRecorder.stopRecording(context);

                        assertNegativeCases(context, server, playerId);
                    } finally {
                        TRACKED.clear();
                        server.runOnServer(AcceleratedDamageClientGameTest::restoreState);
                    }
                });
    }

    /// Visits every viewpoint before the film starts, so a stage never opens on a chunk the client
    /// has not built yet. The recorder keeps running from the first frame, so a teleport later
    /// costs only a few ticks.
    private static void preloadArena(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        double[][] viewpoints = {
            {0.5, -3.5},
            {0.5, -19.5},
            {0.5, -35.5},
            {9.5, -6.5},
            {RANGE_X, 0.5}
        };
        for (double[] viewpoint : viewpoints) {
            server.runOnServer(
                    current ->
                            player(current, playerId)
                                    .teleportTo(
                                            current.overworld(),
                                            viewpoint[0],
                                            ARENA_Y,
                                            viewpoint[1],
                                            Set.of(),
                                            180.0F,
                                            0.0F,
                                            false));
            context.waitTicks(6);
            ClientGameTestWaits.waitForChunkWindow(context, 3, 600);
            ClientGameTestWaits.waitForRenderSettled(context, 4, 600);
        }
        server.runOnServer(
                current ->
                        player(current, playerId)
                                .teleportTo(
                                        current.overworld(),
                                        0.5,
                                        ARENA_Y,
                                        0.5,
                                        Set.of(),
                                        0.0F,
                                        0.0F,
                                        false));
        context.waitTicks(6);
        ClientGameTestWaits.waitForRenderSettled(context, 4, 600);
    }

    private static void registerTickListener() {
        if (tickListenerRegistered) {
            return;
        }
        ServerTickEvents.START_SERVER_TICK.register(
                AcceleratedDamageClientGameTest::tickTrackedTargets);
        tickListenerRegistered = true;
    }

    /// Runs the half of a player's tick that a real client's connection runs for it.
    private static void tickTrackedTargets(MinecraftServer server) {
        if (TRACKED.isEmpty()) {
            return;
        }
        for (TrackedTarget tracked : List.copyOf(TRACKED.values())) {
            ServerPlayer target = server.getPlayerList().getPlayer(tracked.id());
            if (target == null || target.isRemoved()) {
                TRACKED.remove(tracked.id());
                continue;
            }
            target.doTick();
            if (tracked.anchor() != null) {
                target.setPos(tracked.anchor().x, tracked.anchor().y, tracked.anchor().z);
            }
            target.setCustomName(
                    Component.literal(
                            tracked.name() + " " + Math.round(Math.max(0.0F, target.getHealth()))));
        }
    }

    private static void prepareArena(MinecraftServer server) {
        ServerLevel level = server.overworld();
        restoreRules(server);
        // Every target is a player, and a server whose PvP game rule is off rejects all
        // player-versus-player damage in
        // ServerPlayer#hurtServer, so the showcase world runs with the rule on.
        level.getGameRules().set(GameRules.PVP, true, server);
        for (int x = -10; x <= 16; x++) {
            for (int z = -46; z <= 10; z++) {
                level.setBlockAndUpdate(
                        new BlockPos(x, ARENA_Y - 1, z),
                        (z & 1) == 0
                                ? Blocks.STONE.defaultBlockState()
                                : Blocks.DEEPSLATE.defaultBlockState());
            }
        }
        for (int x = -10; x <= 16; x++) {
            level.setBlockAndUpdate(
                    new BlockPos(x, ARENA_Y - 1, 0), Blocks.GLOWSTONE.defaultBlockState());
        }
        // The melee target's back is against this wall, so a knockback that a player could not
        // chase inside the
        // ten-tick window still leaves the target in reach for the next hit.
        for (int x = -4; x <= 4; x++) {
            for (int y = ARENA_Y; y <= ARENA_Y + 2; y++) {
                level.setBlockAndUpdate(
                        new BlockPos(x, y, 5), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        // The drowning box: a glass wall around one column of water, so the film shows the water
        // the player is in.
        for (int y = ARENA_Y; y <= ARENA_Y + 3; y++) {
            for (int x = 6; x <= 12; x++) {
                for (int z = -10; z <= -3; z++) {
                    boolean wall = x == 6 || x == 12 || z == -10 || z == -3 || y == ARENA_Y + 3;
                    level.setBlockAndUpdate(
                            new BlockPos(x, y, z),
                            wall
                                    ? Blocks.GLASS.defaultBlockState()
                                    : Blocks.WATER.defaultBlockState());
                }
            }
        }

        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        player.teleportTo(level, 0.5, ARENA_Y, 0.5, Set.of(), 0.0F, 0.0F, false);
        player.setHealth(player.getMaxHealth());
        player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);
    }

    /// Clears the chat lines the fake targets add when they join or leave, then opens the stage.
    /// Spawn a target before calling this, so its join line is wiped before the first frame of the
    /// stage.
    private static void openStage(
            ClientGameTestContext context, String id, String title, String subtitle) {
        ClientGameTestCommands.clearChat(context);
        ClientGameTestStages.markStage(id);
        ClientGameTestRecorder.showStep(context, id, title, subtitle);
    }

    private static void closeStage(ClientGameTestContext context, String id, int holdTicks) {
        ClientGameTestStages.endStage(context, id, holdTicks);
    }

    private static void standAt(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            int playerId,
            double x,
            double z,
            float yaw) {
        server.runOnServer(
                current ->
                        player(current, playerId)
                                .teleportTo(
                                        current.overworld(),
                                        x,
                                        ARENA_Y,
                                        z,
                                        Set.of(),
                                        yaw,
                                        0.0F,
                                        false));
        context.waitTicks(6);
        ClientGameTestWaits.waitForChunkWindow(context, 4, 200);
        ClientGameTestWaits.waitForRenderSettled(context, 4, 200);
    }

    private static void recordMelee(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        int targetId =
                server.computeOnServer(
                        current -> {
                            ServerLevel level = current.overworld();
                            GameRules rules = level.getGameRules();
                            rules.set(ModGameRules.DISABLE_IFRAMES, true, current);
                            rules.set(ModGameRules.DISABLE_ATTACK_COOLDOWN, true, current);
                            ServerPlayer player = player(current, playerId);
                            giveSword(player);
                            player.teleportTo(
                                    level, 0.5, ARENA_Y, 1.5, Set.of(), 0.0F, 0.0F, false);
                            return spawnTarget(
                                    current,
                                    "MeleeDummy",
                                    new Vec3(0.5, ARENA_Y, 4.5),
                                    WEAPON_TARGET_HEALTH,
                                    false);
                        });
        context.waitTicks(6);
        openStage(
                context,
                "showcase.melee",
                "Rapid melee: every hit lands",
                "Five sword hits, one per tick of the window");
        strikeRepeatedly(
                context,
                server,
                playerId,
                targetId,
                5,
                true,
                true,
                true,
                "showcase.melee",
                "Rapid melee: every hit lands");
        closeStage(context, "showcase.melee", 20);
        server.runOnServer(current -> removeTarget(current, targetId));
    }

    /// Attacks the target with the real attack key until the shot budget runs out, prints its
    /// health after every hit and checks the pattern the rule promises: every hit lands when it is
    /// on, only the first one of the window when it is off.
    private static void strikeRepeatedly(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            int playerId,
            int targetId,
            int hits,
            boolean accelerated,
            boolean chase,
            boolean filmed,
            String stepId,
            String stepTitle) {
        float previous = server.computeOnServer(current -> living(current, targetId).getHealth());
        for (int hit = 1; hit <= hits; hit++) {
            if (chase || hit == 1) {
                Vec3 position =
                        server.computeOnServer(current -> entity(current, targetId).position());
                // A sword's minimum attack range is two blocks, so the player stands 2.6 blocks out
                // and still inside
                // vanilla entity reach.
                ClientGameTestPlayerActions.walkTo(context, position, MELEE_DISTANCE);
            }
            ClientGameTestPlayerActions.lookAt(context, chest(server, targetId));
            ClientGameTestPlayerActions.attackEntity(context, targetId);
            context.waitTicks(5);
            float health = server.computeOnServer(current -> living(current, targetId).getHealth());
            if (filmed) {
                ClientGameTestRecorder.showStep(
                        context, stepId, stepTitle, "hit " + hit + ", health " + health);
            }
            ClientGameTestRecorder.log(context, "hit " + hit + "  target health " + health);
            if (accelerated && health >= previous) {
                throw new AssertionError(
                        "Expected hit "
                                + hit
                                + " to damage the target; health stayed at "
                                + health
                                + ": "
                                + server.computeOnServer(
                                        current -> describeTarget(current, targetId, playerId)));
            }
            if (!accelerated && hit > 1 && health != previous) {
                throw new AssertionError(
                        "Expected vanilla invincibility to reject hit "
                                + hit
                                + "; health fell from "
                                + previous
                                + " to "
                                + health);
            }
            previous = health;
            if (health <= 0.0F) {
                return;
            }
        }
    }

    private static void recordDrowning(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        server.runOnServer(
                current -> {
                    GameRules rules = current.overworld().getGameRules();
                    rules.set(ModGameRules.FASTER_EFFECT_TICKING, true, current);
                    ServerPlayer player = player(current, playerId);
                    player.teleportTo(
                            current.overworld(), 9.5, ARENA_Y, -6.5, Set.of(), 0.0F, 30.0F, false);
                    player.setAirSupply(player.getMaxAirSupply());
                    player.setHealth(player.getMaxHealth());
                });
        context.waitTicks(6);
        openStage(
                context,
                "showcase.drowning",
                "Drowning in two seconds",
                "Air empties ten times faster");
        ClientGameTestPlayerActions.lookAt(context, new Vec3(9.5, ARENA_Y - 0.4, -6.5));
        reportAir(
                context, server, playerId, "showcase.drowning", "Drowning in two seconds", "start");
        context.waitTicks(10);
        reportAir(
                context,
                server,
                playerId,
                "showcase.drowning",
                "Drowning in two seconds",
                "10 ticks");
        context.waitTicks(10);
        reportAir(
                context,
                server,
                playerId,
                "showcase.drowning",
                "Drowning in two seconds",
                "20 ticks");
        context.waitTicks(20);
        int[] drowned =
                reportAir(
                        context,
                        server,
                        playerId,
                        "showcase.drowning",
                        "Drowning in two seconds",
                        "40 ticks");
        if (drowned[0] > 0 || drowned[1] >= 20) {
            throw new AssertionError(
                    "Expected the air to run out and drowning damage to start, air was "
                            + drowned[0]
                            + " and health "
                            + drowned[1]);
        }
        closeStage(context, "showcase.drowning", 10);
        server.runOnServer(
                current ->
                        player(current, playerId)
                                .setAirSupply(player(current, playerId).getMaxAirSupply()));
    }

    private static int[] reportAir(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            int playerId,
            String stepId,
            String stepTitle,
            String label) {
        int[] state =
                server.computeOnServer(
                        current -> {
                            ServerPlayer player = player(current, playerId);
                            return new int[] {
                                player.getAirSupply(), Math.round(player.getHealth())
                            };
                        });
        String reading = label + ": air " + state[0] + ", health " + state[1];
        ClientGameTestRecorder.showStep(context, stepId, stepTitle, reading);
        ClientGameTestRecorder.log(context, reading);
        return state;
    }

    private static void recordBow(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        int targetId =
                server.computeOnServer(
                        current -> {
                            prepareShooter(current, playerId);
                            ServerPlayer player = player(current, playerId);
                            player.getInventory().setItem(0, new ItemStack(Items.BOW));
                            player.getInventory().setItem(8, new ItemStack(Items.ARROW, 32));
                            player.getInventory().setSelectedSlot(0);
                            return spawnTarget(
                                    current,
                                    "BowDummy",
                                    new Vec3(RANGE_X, ARENA_Y, 4.5),
                                    WEAPON_TARGET_HEALTH,
                                    false);
                        });
        context.waitTicks(6);
        openStage(
                context,
                "showcase.bow",
                "Bow: five arrows in one second",
                "One tick of draw, a full-power arrow");
        shootRepeatedly(
                context,
                server,
                playerId,
                targetId,
                5,
                true,
                true,
                "showcase.bow",
                "Bow: five arrows in one second");
        closeStage(context, "showcase.bow", 20);
        server.runOnServer(current -> removeTarget(current, targetId));
    }

    /// Five crossbows loaded with one bolt each, so the five bolts take only the shots themselves:
    /// the stage beats the bow's one arrow per draw.
    private static void recordCrossbow(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        int targetId =
                server.computeOnServer(
                        current -> {
                            prepareShooter(current, playerId);
                            ServerPlayer player = player(current, playerId);
                            for (int slot = 0; slot < 5; slot++) {
                                player.getInventory()
                                        .setItem(slot, loadedCrossbow(new ItemStack(Items.ARROW)));
                            }
                            player.getInventory().setSelectedSlot(0);
                            return spawnTarget(
                                    current,
                                    "CrossbowDummy",
                                    new Vec3(RANGE_X, ARENA_Y, 4.5),
                                    WEAPON_TARGET_HEALTH,
                                    false);
                        });
        context.waitTicks(6);
        openStage(
                context,
                "showcase.crossbow",
                "Crossbow: five loaded crossbows",
                "No loading, five bolts in three quarters of a second");
        shootLoadedCrossbows(
                context,
                server,
                playerId,
                targetId,
                5,
                4,
                false,
                "showcase.crossbow",
                "Crossbow: five loaded crossbows");
        closeStage(context, "showcase.crossbow", 20);
        server.runOnServer(current -> removeTarget(current, targetId));
    }

    /// A crossbow of firework rockets, fired at a target eight blocks away so the blast reaches the
    /// target and not the shooter. The rocket carries a firework explosion: a rocket item without a
    /// {@code FIREWORKS} component has no explosion, and FireworkRocketEntity#dealExplosionDamage
    /// then deals no damage at all.
    private static void recordFirework(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        int targetId =
                server.computeOnServer(
                        current -> {
                            prepareShooter(current, playerId);
                            ServerPlayer player = player(current, playerId);
                            for (int slot = 0; slot < 2; slot++) {
                                player.getInventory()
                                        .setItem(slot, loadedCrossbow(fireworkRocket()));
                            }
                            player.getInventory().setSelectedSlot(0);
                            return spawnTarget(
                                    current,
                                    "FireworkDummy",
                                    new Vec3(RANGE_X, ARENA_Y, FIREWORK_RANGE_Z),
                                    WEAPON_TARGET_HEALTH,
                                    false);
                        });
        context.waitTicks(6);
        openStage(
                context,
                "showcase.firework",
                "Crossbow of fireworks",
                "Each rocket explodes on the target");
        shootLoadedCrossbows(
                context,
                server,
                playerId,
                targetId,
                2,
                14,
                false,
                "showcase.firework",
                "Crossbow of fireworks");
        closeStage(context, "showcase.firework", 24);
        server.runOnServer(current -> removeTarget(current, targetId));
    }

    private static void recordTrident(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        int targetId =
                server.computeOnServer(
                        current -> {
                            prepareShooter(current, playerId);
                            ServerPlayer player = player(current, playerId);
                            for (int slot = 0; slot < 5; slot++) {
                                player.getInventory().setItem(slot, new ItemStack(Items.TRIDENT));
                            }
                            player.getInventory().setSelectedSlot(0);
                            return spawnTarget(
                                    current,
                                    "TridentDummy",
                                    new Vec3(RANGE_X, ARENA_Y, 4.5),
                                    WEAPON_TARGET_HEALTH,
                                    false);
                        });
        context.waitTicks(6);
        openStage(
                context,
                "showcase.trident",
                "Trident: five throws",
                "The ten-tick wind-up is gone");
        shootLoadedCrossbows(
                context,
                server,
                playerId,
                targetId,
                5,
                4,
                true,
                "showcase.trident",
                "Trident: five throws");
        closeStage(context, "showcase.trident", 20);
        server.runOnServer(current -> removeTarget(current, targetId));
    }

    private static void prepareShooter(MinecraftServer server, int playerId) {
        GameRules rules = server.overworld().getGameRules();
        rules.set(ModGameRules.INSTANT_SHOOT, true, server);
        rules.set(ModGameRules.DISABLE_IFRAMES, true, server);
        ServerPlayer player = player(server, playerId);
        // The ranged lane runs beside the melee wall, so a rocket that overshoots explodes in open
        // air.
        player.teleportTo(server.overworld(), RANGE_X, ARENA_Y, 0.5, Set.of(), 0.0F, 0.0F, false);
        player.setHealth(player.getMaxHealth());
        player.getInventory().clearContent();
    }

    /// Turns to the target, checks the crosshair is on it inside vanilla reach, and presses the use
    /// key once per weapon in the hotbar's first slots. Each shot must take health off the target.
    private static void shootLoadedCrossbows(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            int playerId,
            int targetId,
            int shots,
            int settleTicks,
            boolean holdUse,
            String stepId,
            String stepTitle) {
        ClientGameTestPlayerActions.requireVanillaInteractionRanges(context);
        float previous = server.computeOnServer(current -> living(current, targetId).getHealth());
        for (int shot = 1; shot <= shots; shot++) {
            ClientGameTestPlayerActions.lookAt(context, chest(server, targetId));
            ClientGameTestPlayerActions.requireLookingAtEntity(context, targetId);
            int hotbarSlot = shot - 1;
            context.getInput().pressKey(options -> options.keyHotbarSlots[hotbarSlot]);
            context.waitTicks(2);
            ClientGameTestPlayerActions.lookAt(context, chest(server, targetId));
            ClientGameTestPlayerActions.requireLookingAtEntity(context, targetId);
            if (holdUse) {
                context.getInput().holdKeyFor(options -> options.keyUse, 1);
            } else {
                context.getInput().pressKey(options -> options.keyUse);
            }
            context.waitTicks(settleTicks);
            float health = server.computeOnServer(current -> living(current, targetId).getHealth());
            ClientGameTestRecorder.showStep(
                    context, stepId, stepTitle, "shot " + shot + ", health " + health);
            ClientGameTestRecorder.log(context, "shot " + shot + "  target health " + health);
            if (health >= previous) {
                throw new AssertionError(
                        "Expected shot "
                                + shot
                                + " to damage the target; health stayed at "
                                + health
                                + ": "
                                + server.computeOnServer(
                                        current -> describeTarget(current, targetId, playerId)));
            }
            previous = health;
            if (health <= 0.0F) {
                return;
            }
        }
    }

    /// Fires the bow with the real use key until the shot budget runs out, and prints the target's
    /// health after every shot.
    private static void shootRepeatedly(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            int playerId,
            int targetId,
            int shots,
            boolean accelerated,
            boolean filmed,
            String stepId,
            String stepTitle) {
        ClientGameTestPlayerActions.requireVanillaInteractionRanges(context);
        float previous = server.computeOnServer(current -> living(current, targetId).getHealth());
        float firstDrop = 0.0F;
        for (int shot = 1; shot <= shots; shot++) {
            ClientGameTestPlayerActions.lookAt(context, chest(server, targetId));
            context.getInput().holdKeyFor(options -> options.keyUse, 1);
            context.waitTicks(4);
            float health = server.computeOnServer(current -> living(current, targetId).getHealth());
            float drop = previous - health;
            if (filmed) {
                ClientGameTestRecorder.showStep(
                        context, stepId, stepTitle, "shot " + shot + ", health " + health);
            }
            ClientGameTestRecorder.log(context, "shot " + shot + "  target health " + health);
            if (accelerated) {
                if (drop <= 0.0F) {
                    throw new AssertionError(
                            "Expected shot "
                                    + shot
                                    + " to damage the target; health stayed at "
                                    + health
                                    + ": "
                                    + server.computeOnServer(
                                            current ->
                                                    describeTarget(current, targetId, playerId)));
                }
            } else if (shot == 1) {
                if (drop <= 0.0F) {
                    throw new AssertionError(
                            "Expected the first vanilla shot to damage the target; health stayed at "
                                    + health);
                }
                firstDrop = drop;
                // Vanilla still passes on the part of a later hit that exceeds the window's largest
                // hit, so the
                // negative case shows that no repeat hit lands in full rather than that nothing
                // happens at all.
            } else if (drop >= firstDrop) {
                throw new AssertionError(
                        "Expected vanilla invincibility to keep shot "
                                + shot
                                + " from landing in full; it took "
                                + drop
                                + " of the first hit's "
                                + firstDrop);
            }
            previous = health;
            if (health <= 0.0F) {
                return;
            }
        }
    }

    private static void recordDamageEffects(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        standAt(context, server, playerId, 0.5, -3.5, 180.0F);
        List<Integer> targets =
                server.computeOnServer(
                        current -> {
                            ServerLevel level = current.overworld();
                            GameRules rules = level.getGameRules();
                            rules.set(ModGameRules.FASTER_EFFECT_TICKING, true, current);
                            rules.set(ModGameRules.DISABLE_IFRAMES, false, current);
                            rules.set(ModGameRules.INSTANT_SHOOT, false, current);
                            rules.set(ModGameRules.DISABLE_ATTACK_COOLDOWN, false, current);
                            player(current, playerId).getInventory().clearContent();

                            plainCell(level, -5, -8);
                            plainCell(level, 4, -8);
                            plainCell(level, 1, -8);
                            lavaCell(level, -2, -8);
                            int fire =
                                    spawnTarget(
                                            current,
                                            "Fire",
                                            new Vec3(-4.5, ARENA_Y, -7.5),
                                            EFFECT_TARGET_HEALTH,
                                            true);
                            int lava =
                                    spawnTarget(
                                            current,
                                            "Lava",
                                            new Vec3(-1.5, ARENA_Y, -7.5),
                                            EFFECT_TARGET_HEALTH,
                                            true);
                            int poison =
                                    spawnTarget(
                                            current,
                                            "Poison",
                                            new Vec3(1.5, ARENA_Y, -7.5),
                                            EFFECT_TARGET_HEALTH,
                                            true);
                            int wither =
                                    spawnTarget(
                                            current,
                                            "Wither",
                                            new Vec3(4.5, ARENA_Y, -7.5),
                                            EFFECT_TARGET_HEALTH,
                                            true);
                            living(current, fire).setRemainingFireTicks(400);
                            living(current, poison)
                                    .addEffect(new MobEffectInstance(MobEffects.POISON, 320, 0));
                            living(current, wither)
                                    .addEffect(new MobEffectInstance(MobEffects.WITHER, 400, 0));
                            if (living(current, fire).getRemainingFireTicks() <= 0
                                    || !living(current, poison).hasEffect(MobEffects.POISON)
                                    || !living(current, wither).hasEffect(MobEffects.WITHER)) {
                                throw new AssertionError(
                                        "Expected to set fire, poison and wither on the effect targets");
                            }
                            return List.of(fire, lava, poison, wither);
                        });
        context.waitTicks(4);
        ClientGameTestPlayerActions.lookAt(context, new Vec3(0.5, ARENA_Y + 0.9, -7.5));
        openStage(
                context,
                "showcase.effects-damage",
                "Fire, lava, poison, wither",
                "Every effect ticks ten times faster");
        for (int reading = 0; reading < 3; reading++) {
            context.waitTicks(READING_TICKS);
            String label =
                    server.computeOnServer(
                            current ->
                                    healthLine(
                                            current, targets, "Fire", "Lava", "Poison", "Wither"));
            ClientGameTestRecorder.showStep(
                    context, "showcase.effects-damage", "Fire, lava, poison, wither", label);
            ClientGameTestRecorder.log(context, label);
        }
        assertHealthFell(server, targets, EFFECT_TARGET_HEALTH, "fire, lava, poison and wither");
        closeStage(context, "showcase.effects-damage", 10);
        server.runOnServer(current -> targets.forEach(target -> removeTarget(current, target)));
    }

    private static void recordFreezingAndContact(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        standAt(context, server, playerId, 0.5, -19.5, 180.0F);
        List<Integer> targets =
                server.computeOnServer(
                        current -> {
                            ServerLevel level = current.overworld();
                            snowCell(level, -5, -24);
                            cactusCell(level, 0, -24);
                            plainCell(level, 5, -24);
                            int freezing =
                                    spawnTarget(
                                            current,
                                            "Freezing",
                                            new Vec3(-4.5, ARENA_Y, -23.5),
                                            EFFECT_TARGET_HEALTH,
                                            true);
                            int cactus =
                                    spawnTarget(
                                            current,
                                            "Cactus",
                                            new Vec3(0.85, ARENA_Y, -23.5),
                                            EFFECT_TARGET_HEALTH,
                                            true);
                            int drying =
                                    spawnMob(
                                            current,
                                            EntityTypes.COD,
                                            "Drying",
                                            new Vec3(5.5, ARENA_Y, -23.5));
                            living(current, drying)
                                    .getAttribute(Attributes.MAX_HEALTH)
                                    .setBaseValue(EFFECT_TARGET_HEALTH);
                            living(current, drying).setHealth(EFFECT_TARGET_HEALTH);
                            living(current, drying).setAirSupply(0);
                            return List.of(freezing, cactus, drying);
                        });
        context.waitTicks(4);
        ClientGameTestPlayerActions.lookAt(context, new Vec3(0.5, ARENA_Y + 0.9, -23.5));
        openStage(
                context,
                "showcase.effects-contact",
                "Freezing, cactus, drying",
                "Powder snow, contact damage, a fish on land");
        for (int reading = 0; reading < 3; reading++) {
            context.waitTicks(READING_TICKS);
            String label =
                    server.computeOnServer(
                            current ->
                                    healthLine(current, targets, "Freezing", "Cactus", "Drying"));
            ClientGameTestRecorder.showStep(
                    context, "showcase.effects-contact", "Freezing, cactus, drying", label);
            ClientGameTestRecorder.log(context, label);
        }
        assertHealthFell(server, targets, EFFECT_TARGET_HEALTH, "freezing, cactus and drying");
        closeStage(context, "showcase.effects-contact", 10);
        server.runOnServer(current -> targets.forEach(target -> removeTarget(current, target)));
    }

    private static void recordHungerCloudAndHealing(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        standAt(context, server, playerId, 0.5, -35.5, 180.0F);
        List<Integer> targets =
                server.computeOnServer(
                        current -> {
                            ServerLevel level = current.overworld();
                            plainCell(level, -5, -40);
                            plainCell(level, 1, -40);
                            plainCell(level, 5, -40);
                            int starving =
                                    spawnTarget(
                                            current,
                                            "Starving",
                                            new Vec3(-4.5, ARENA_Y, -39.5),
                                            EFFECT_TARGET_HEALTH,
                                            true);
                            int regenerating =
                                    spawnTarget(
                                            current,
                                            "Regenerating",
                                            new Vec3(1.5, ARENA_Y, -39.5),
                                            EFFECT_TARGET_HEALTH,
                                            true);
                            int clouded =
                                    spawnTarget(
                                            current,
                                            "Cloud",
                                            new Vec3(5.5, ARENA_Y, -39.5),
                                            EFFECT_TARGET_HEALTH,
                                            true);
                            ServerPlayer starvingPlayer = (ServerPlayer) living(current, starving);
                            starvingPlayer.getFoodData().setFoodLevel(0);
                            starvingPlayer.getFoodData().setSaturation(0.0F);
                            ServerPlayer regeneratingPlayer =
                                    (ServerPlayer) living(current, regenerating);
                            regeneratingPlayer.setHealth(EFFECT_TARGET_HEALTH / 2.0F);
                            regeneratingPlayer.getFoodData().setFoodLevel(20);
                            regeneratingPlayer.getFoodData().setSaturation(0.0F);

                            AreaEffectCloud cloud = new AreaEffectCloud(level, 5.5, ARENA_Y, -39.5);
                            cloud.setPotionContents(
                                    new PotionContents(
                                            Optional.of(Potions.POISON),
                                            Optional.empty(),
                                            List.of(),
                                            Optional.empty()));
                            cloud.setRadius(2.5F);
                            cloud.setDuration(600);
                            cloud.setWaitTime(0);
                            level.addFreshEntity(cloud);
                            return List.of(starving, regenerating, clouded);
                        });
        context.waitTicks(4);
        ClientGameTestPlayerActions.lookAt(context, new Vec3(0.5, ARENA_Y + 0.9, -39.5));
        openStage(
                context,
                "showcase.effects-hunger",
                "Hunger, a cloud, and healing",
                "Starvation, a lingering cloud, self-healing");
        float startingHealth =
                server.computeOnServer(current -> living(current, targets.get(1)).getHealth());
        for (int reading = 0; reading < 3; reading++) {
            context.waitTicks(READING_TICKS);
            String label =
                    server.computeOnServer(
                            current ->
                                    healthLine(
                                            current, targets, "Starving", "Regenerating", "Cloud"));
            ClientGameTestRecorder.showStep(
                    context, "showcase.effects-hunger", "Hunger, a cloud, and healing", label);
            ClientGameTestRecorder.log(context, label);
        }
        float healing =
                server.computeOnServer(current -> living(current, targets.get(1)).getHealth());
        if (healing <= startingHealth) {
            throw new AssertionError(
                    "Expected hunger-based regeneration to heal the wounded target; health went from "
                            + startingHealth
                            + " to "
                            + healing);
        }
        closeStage(context, "showcase.effects-hunger", 10);
        server.runOnServer(current -> targets.forEach(target -> removeTarget(current, target)));
    }

    /// The same three damage effects with the rule off, filmed because the numbers barely move: the
    /// comparison is what shows "ten times faster".
    private static void recordEffectsOff(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        standAt(context, server, playerId, 0.5, -3.5, 180.0F);
        List<Integer> targets =
                server.computeOnServer(
                        current -> {
                            ServerLevel level = current.overworld();
                            level.getGameRules()
                                    .set(ModGameRules.FASTER_EFFECT_TICKING, false, current);
                            plainCell(level, -5, -8);
                            plainCell(level, -2, -8);
                            plainCell(level, 1, -8);
                            int fire =
                                    spawnTarget(
                                            current,
                                            "FireOff",
                                            new Vec3(-4.5, ARENA_Y, -7.5),
                                            COMPARISON_TARGET_HEALTH,
                                            true);
                            int poison =
                                    spawnTarget(
                                            current,
                                            "PoisonOff",
                                            new Vec3(-1.5, ARENA_Y, -7.5),
                                            COMPARISON_TARGET_HEALTH,
                                            true);
                            int wither =
                                    spawnTarget(
                                            current,
                                            "WitherOff",
                                            new Vec3(1.5, ARENA_Y, -7.5),
                                            COMPARISON_TARGET_HEALTH,
                                            true);
                            living(current, fire).setRemainingFireTicks(400);
                            living(current, poison)
                                    .addEffect(new MobEffectInstance(MobEffects.POISON, 320, 0));
                            living(current, wither)
                                    .addEffect(new MobEffectInstance(MobEffects.WITHER, 400, 0));
                            return List.of(fire, poison, wither);
                        });
        context.waitTicks(4);
        ClientGameTestPlayerActions.lookAt(context, new Vec3(-1.5, ARENA_Y + 0.9, -7.5));
        openStage(
                context,
                "showcase.effects-off",
                "The same effects, rule off",
                "Vanilla: barely a scratch in two seconds");
        float[] start =
                server.computeOnServer(
                        current ->
                                new float[] {
                                    living(current, targets.get(0)).getHealth(),
                                    living(current, targets.get(1)).getHealth(),
                                    living(current, targets.get(2)).getHealth()
                                });
        for (int reading = 0; reading < 3; reading++) {
            context.waitTicks(READING_TICKS);
            String label =
                    server.computeOnServer(
                            current ->
                                    healthLine(
                                            current, targets, "FireOff", "PoisonOff", "WitherOff"));
            ClientGameTestRecorder.showStep(
                    context, "showcase.effects-off", "The same effects, rule off", label);
            ClientGameTestRecorder.log(context, label);
        }
        float[] end =
                server.computeOnServer(
                        current ->
                                new float[] {
                                    living(current, targets.get(0)).getHealth(),
                                    living(current, targets.get(1)).getHealth(),
                                    living(current, targets.get(2)).getHealth()
                                });
        for (int index = 0; index < start.length; index++) {
            if (start[index] - end[index] > 4.0F) {
                throw new AssertionError(
                        "Expected vanilla effect ticking to take at most four health in three readings; target "
                                + index
                                + " fell from "
                                + start[index]
                                + " to "
                                + end[index]);
            }
        }
        closeStage(context, "showcase.effects-off", 10);
        server.runOnServer(current -> targets.forEach(target -> removeTarget(current, target)));
    }

    /// The negative cases the owner asked for, run after the film ends: every positive stage above
    /// has the same actions with the rule off, where vanilla rejects the repeat hit.
    private static void assertNegativeCases(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        int meleeTarget =
                server.computeOnServer(
                        current -> {
                            ServerLevel level = current.overworld();
                            GameRules rules = level.getGameRules();
                            rules.set(ModGameRules.DISABLE_IFRAMES, false, current);
                            rules.set(ModGameRules.DISABLE_ATTACK_COOLDOWN, false, current);
                            rules.set(ModGameRules.FASTER_EFFECT_TICKING, false, current);
                            ServerPlayer player = player(current, playerId);
                            giveSword(player);
                            player.teleportTo(
                                    level, 0.5, ARENA_Y, 1.5, Set.of(), 0.0F, 0.0F, false);
                            return spawnTarget(
                                    current,
                                    "MeleeDummyOff",
                                    new Vec3(0.5, ARENA_Y, 4.5),
                                    WEAPON_TARGET_HEALTH,
                                    false);
                        });
        context.waitTicks(4);
        strikeRepeatedly(
                context,
                server,
                playerId,
                meleeTarget,
                2,
                false,
                false,
                false,
                "showcase.melee-off",
                "The same hits, rule off");
        server.runOnServer(current -> removeTarget(current, meleeTarget));

        int bowTarget =
                server.computeOnServer(
                        current -> {
                            prepareShooter(current, playerId);
                            ServerPlayer player = player(current, playerId);
                            player.getInventory().setItem(0, new ItemStack(Items.BOW));
                            player.getInventory().setItem(8, new ItemStack(Items.ARROW, 32));
                            player.getInventory().setSelectedSlot(0);
                            return spawnTarget(
                                    current,
                                    "BowDummyOff",
                                    new Vec3(RANGE_X, ARENA_Y, 4.5),
                                    WEAPON_TARGET_HEALTH,
                                    false);
                        });
        context.waitTicks(4);
        shootRepeatedly(
                context,
                server,
                playerId,
                bowTarget,
                2,
                false,
                false,
                "showcase.bow-off",
                "The same shots, rule off");
        server.runOnServer(current -> removeTarget(current, bowTarget));

        int crossbowTarget =
                server.computeOnServer(
                        current -> {
                            prepareShooter(current, playerId);
                            ServerPlayer player = player(current, playerId);
                            for (int slot = 0; slot < 2; slot++) {
                                player.getInventory()
                                        .setItem(slot, loadedCrossbow(new ItemStack(Items.ARROW)));
                            }
                            player.getInventory().setSelectedSlot(0);
                            return spawnTarget(
                                    current,
                                    "CrossbowDummyOff",
                                    new Vec3(RANGE_X, ARENA_Y, 4.5),
                                    WEAPON_TARGET_HEALTH,
                                    false);
                        });
        context.waitTicks(4);
        shootLoadedCrossbowsVanilla(context, server, playerId, crossbowTarget, 2, false);
        server.runOnServer(current -> removeTarget(current, crossbowTarget));

        int tridentTarget =
                server.computeOnServer(
                        current -> {
                            prepareShooter(current, playerId);
                            ServerPlayer player = player(current, playerId);
                            for (int slot = 0; slot < 2; slot++) {
                                player.getInventory().setItem(slot, new ItemStack(Items.TRIDENT));
                            }
                            player.getInventory().setSelectedSlot(0);
                            return spawnTarget(
                                    current,
                                    "TridentDummyOff",
                                    new Vec3(RANGE_X, ARENA_Y, 4.5),
                                    WEAPON_TARGET_HEALTH,
                                    false);
                        });
        context.waitTicks(4);
        shootLoadedCrossbowsVanilla(context, server, playerId, tridentTarget, 2, true);
        server.runOnServer(current -> removeTarget(current, tridentTarget));

        assertVanillaDrowningSlow(context, server, playerId);
        assertVanillaEffectsSlow(context, server);
        ClientGameTestPlayerActions.requireVanillaInteractionRanges(context);
    }

    /// The drowning negative: the same two seconds in the water with the rule off leaves the air
    /// (and the health) almost untouched.
    private static void assertVanillaDrowningSlow(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        server.runOnServer(
                current -> {
                    current.overworld()
                            .getGameRules()
                            .set(ModGameRules.FASTER_EFFECT_TICKING, false, current);
                    ServerPlayer player = player(current, playerId);
                    player.teleportTo(
                            current.overworld(), 9.5, ARENA_Y, -6.5, Set.of(), 0.0F, 30.0F, false);
                    player.setAirSupply(player.getMaxAirSupply());
                    player.setHealth(player.getMaxHealth());
                });
        context.waitTicks(40);
        int[] vanillaAir =
                server.computeOnServer(
                        current -> {
                            ServerPlayer player = player(current, playerId);
                            return new int[] {
                                player.getAirSupply(), Math.round(player.getHealth())
                            };
                        });
        server.runOnServer(
                current ->
                        player(current, playerId)
                                .setAirSupply(player(current, playerId).getMaxAirSupply()));
        if (vanillaAir[0] < 200 || vanillaAir[1] != 20) {
            throw new AssertionError(
                    "Expected vanilla air to last, air was "
                            + vanillaAir[0]
                            + " and health "
                            + vanillaAir[1]);
        }
        ClientGameTestRecorder.log(
                context, "rule off drowning: air " + vanillaAir[0] + ", health " + vanillaAir[1]);
    }

    /// Two shots with the rule off: the first lands, and the second lands at most what the first
    /// did, never in full.
    private static void shootLoadedCrossbowsVanilla(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            int playerId,
            int targetId,
            int shots,
            boolean holdUse) {
        ClientGameTestPlayerActions.requireVanillaInteractionRanges(context);
        float firstDrop = 0.0F;
        float previous = server.computeOnServer(current -> living(current, targetId).getHealth());
        for (int shot = 1; shot <= shots; shot++) {
            ClientGameTestPlayerActions.lookAt(context, chest(server, targetId));
            ClientGameTestPlayerActions.requireLookingAtEntity(context, targetId);
            int hotbarSlot = shot - 1;
            context.getInput().pressKey(options -> options.keyHotbarSlots[hotbarSlot]);
            context.waitTicks(2);
            ClientGameTestPlayerActions.lookAt(context, chest(server, targetId));
            ClientGameTestPlayerActions.requireLookingAtEntity(context, targetId);
            if (holdUse) {
                context.getInput().holdKeyFor(options -> options.keyUse, 1);
            } else {
                context.getInput().pressKey(options -> options.keyUse);
            }
            context.waitTicks(4);
            float health = server.computeOnServer(current -> living(current, targetId).getHealth());
            float drop = previous - health;
            ClientGameTestRecorder.log(
                    context, "rule off shot " + shot + "  target health " + health);
            if (shot == 1) {
                if (drop <= 0.0F) {
                    throw new AssertionError(
                            "Expected the first vanilla shot to damage the target; health stayed at "
                                    + health);
                }
                firstDrop = drop;
            } else if (drop >= firstDrop) {
                throw new AssertionError(
                        "Expected vanilla invincibility to keep shot "
                                + shot
                                + " from landing in full; it took "
                                + drop
                                + " of the first hit's "
                                + firstDrop);
            }
            previous = health;
        }
    }

    private static void assertVanillaEffectsSlow(
            ClientGameTestContext context, TestDedicatedServerContext server) {
        int target =
                server.computeOnServer(
                        current -> {
                            ServerLevel level = current.overworld();
                            level.getGameRules()
                                    .set(ModGameRules.FASTER_EFFECT_TICKING, false, current);
                            plainCell(level, 0, 4);
                            int slow =
                                    spawnMob(
                                            current,
                                            EntityTypes.ZOMBIE,
                                            "SlowDummy",
                                            new Vec3(0.5, ARENA_Y, 3.5));
                            LivingEntity living = living(current, slow);
                            living.setHealth(living.getMaxHealth());
                            living.setRemainingFireTicks(400);
                            living.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 2));
                            return slow;
                        });
        context.waitTicks(20);
        float health = server.computeOnServer(current -> living(current, target).getHealth());
        server.runOnServer(
                current -> {
                    removeTarget(current, target);
                    restoreRules(current);
                });
        if (health < 17.0F) {
            throw new AssertionError(
                    "Expected vanilla fire and poison to deal little damage in twenty ticks, health was "
                            + health);
        }
        ClientGameTestRecorder.log(context, "rule off: health after twenty ticks " + health);
    }

    /// One no-AI mob, for a fish out of water and for the vanilla comparison.
    private static int spawnMob(
            MinecraftServer server, EntityType<? extends Mob> type, String name, Vec3 position) {
        ServerLevel level = server.overworld();
        Mob target = type.create(level, EntitySpawnReason.COMMAND);
        if (target == null) {
            throw new AssertionError("Expected to create " + name);
        }
        target.setPos(position.x, position.y, position.z);
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.setCustomName(Component.literal(name));
        target.setCustomNameVisible(true);
        level.addFreshEntity(target);
        return target.getId();
    }

    /// One caption line holding the health of every target of the stage, in the order the stage
    /// names them.
    private static String healthLine(
            MinecraftServer server, List<Integer> targets, String... names) {
        StringBuilder line = new StringBuilder();
        for (int index = 0; index < targets.size(); index++) {
            LivingEntity target = living(server, targets.get(index));
            if (line.length() > 0) {
                line.append("   ");
            }
            line.append(names[index])
                    .append(' ')
                    .append(Math.round(Math.max(0.0F, target.getHealth())));
        }
        return line.toString();
    }

    /// Every target of an accelerated stage loses health: each effect family has to be seen
    /// working.
    private static void assertHealthFell(
            TestDedicatedServerContext server, List<Integer> targets, float start, String family) {
        for (int index = 0; index < targets.size(); index++) {
            int target = targets.get(index);
            float health = server.computeOnServer(current -> living(current, target).getHealth());
            if (health >= start) {
                throw new AssertionError(
                        "Expected accelerated damage from "
                                + family
                                + " to take health off target "
                                + index
                                + "; health stayed at "
                                + health);
            }
        }
    }

    private static void plainCell(ServerLevel level, int x, int z) {
        setBlock(level, x, ARENA_Y - 1, z, Blocks.STONE);
        setBlock(level, x, ARENA_Y, z, Blocks.AIR);
        setBlock(level, x + 1, ARENA_Y, z, Blocks.AIR);
        setBlock(level, x - 1, ARENA_Y, z, Blocks.AIR);
        setBlock(level, x, ARENA_Y, z + 1, Blocks.AIR);
        setBlock(level, x, ARENA_Y, z - 1, Blocks.AIR);
    }

    private static void lavaCell(ServerLevel level, int x, int z) {
        setBlock(level, x, ARENA_Y - 1, z, Blocks.STONE);
        setBlock(level, x, ARENA_Y, z, Blocks.LAVA);
        setBlock(level, x + 1, ARENA_Y, z, Blocks.GLASS);
        setBlock(level, x - 1, ARENA_Y, z, Blocks.GLASS);
        setBlock(level, x, ARENA_Y, z + 1, Blocks.GLASS);
        setBlock(level, x, ARENA_Y, z - 1, Blocks.GLASS);
    }

    private static void snowCell(ServerLevel level, int x, int z) {
        setBlock(level, x, ARENA_Y - 1, z, Blocks.STONE);
        setBlock(level, x, ARENA_Y, z, Blocks.POWDER_SNOW);
    }

    private static void cactusCell(ServerLevel level, int x, int z) {
        setBlock(level, x, ARENA_Y - 1, z, Blocks.STONE);
        setBlock(level, x + 1, ARENA_Y - 1, z, Blocks.SAND);
        setBlock(level, x + 1, ARENA_Y, z, Blocks.CACTUS);
    }

    private static void setBlock(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlockAndUpdate(new BlockPos(x, y, z), block.defaultBlockState());
    }

    private static ItemStack loadedCrossbow(ItemStack projectile) {
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.set(
                DataComponents.CHARGED_PROJECTILES,
                ChargedProjectiles.ofNonEmpty(List.of(projectile)));
        return crossbow;
    }

    /// A firework rocket with one small-ball explosion, which is what makes
    /// FireworkRocketEntity#dealExplosionDamage deal damage at all: a rocket item with no {@code
    /// FIREWORKS} component has no explosion to apply.
    private static ItemStack fireworkRocket() {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        FireworkExplosion explosion =
                new FireworkExplosion(
                        FireworkExplosion.Shape.SMALL_BALL,
                        IntList.of(0xFF5555),
                        IntList.of(0xFFFFFF),
                        true,
                        true);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(explosion)));
        return rocket;
    }

    private static String describeTarget(MinecraftServer server, int targetId, int playerId) {
        LivingEntity target = living(server, targetId);
        ServerPlayer player = player(server, playerId);
        StringBuilder state = new StringBuilder();
        state.append("target=")
                .append(target.getType())
                .append(" health=")
                .append(target.getHealth())
                .append(" invulnerable=")
                .append(target.isInvulnerable())
                .append(" cooldown=")
                .append(target.damageCooldownTime)
                .append(" hurtTime=")
                .append(target.hurtTime)
                .append(" lastSource=")
                .append(target.getLastDamageSource())
                .append(" mode=")
                .append(
                        target instanceof ServerPlayer targetPlayer
                                ? targetPlayer.gameMode()
                                : null)
                .append(" targetAbilitiesInvulnerable=")
                .append(
                        target instanceof net.minecraft.world.entity.player.Player targetPlayer
                                ? targetPlayer.getAbilities().invulnerable
                                : null)
                .append(" playerMode=")
                .append(player.gameMode())
                .append(" playerAbilitiesInvulnerable=")
                .append(player.getAbilities().invulnerable)
                .append(" attackScale=")
                .append(player.getAttackStrengthScale(0.5F))
                .append(" distance=")
                .append(Math.sqrt(target.getBoundingBox().distanceToSqr(player.getEyePosition())))
                .append(" attackDamage=")
                .append(player.getAttributeValue(Attributes.ATTACK_DAMAGE))
                .append(" withinAttackRange=")
                .append(
                        player.isWithinAttackRange(
                                player.getMainHandItem(), target.getBoundingBox(), 3.0))
                .append(" cannotAttackWithItem=")
                .append(player.cannotAttackWithItem(player.getMainHandItem(), 5))
                .append(" held=")
                .append(player.getMainHandItem())
                .append(" probeInvulnerableTo=")
                .append(
                        target.isInvulnerableTo(
                                server.overworld(),
                                server.overworld().damageSources().playerAttack(player)))
                .append(" isRemoved=")
                .append(target.isRemoved())
                .append(" removalReason=")
                .append(target.getRemovalReason())
                .append(" temporarilyInvulnerable=")
                .append(target.isTemporarilyInvulnerable())
                .append(" pvpRule=")
                .append(server.overworld().getGameRules().get(GameRules.PVP))
                .append(" canHarmPlayer=")
                .append(
                        target instanceof ServerPlayer targetPlayer
                                && targetPlayer.canHarmPlayer(player));
        return state.toString();
    }

    private static void giveSword(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getInventory().setSelectedSlot(0);
        player.getInventory().setItem(0, new ItemStack(Items.WOODEN_SWORD));
    }

    private static Vec3 chest(TestDedicatedServerContext server, int entityId) {
        return server.computeOnServer(
                current -> entity(current, entityId).getBoundingBox().getCenter());
    }

    private static Entity entity(MinecraftServer server, int entityId) {
        Entity entity = server.overworld().getEntity(entityId);
        if (entity == null) {
            throw new AssertionError("Expected entity " + entityId + " in the showcase arena");
        }
        return entity;
    }

    private static LivingEntity living(MinecraftServer server, int entityId) {
        Entity entity = entity(server, entityId);
        if (!(entity instanceof LivingEntity living)) {
            throw new AssertionError("Expected a living entity at " + entityId);
        }
        return living;
    }

    private static ServerPlayer player(MinecraftServer server, int playerId) {
        Entity entity = server.overworld().getEntity(playerId);
        if (!(entity instanceof ServerPlayer player)) {
            throw new AssertionError("Expected the showcase player at entity id " + playerId);
        }
        return player;
    }

    /// Connects a player-shaped, survival target the way a vanilla GameTest does, so the vanilla
    /// client renders it as a player the mod's damage applies to. A weapon target keeps enough
    /// health to outlive its stage; its food level sits below the regeneration threshold and above
    /// starvation, so its health only changes when something hits it.
    private static int spawnTarget(
            MinecraftServer server, String name, Vec3 position, float health, boolean pinned) {
        ServerLevel level = server.overworld();
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer target =
                new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, target, cookie);
        // ServerPlayer#isInvulnerableTo rejects every damage source until the client acknowledges
        // the world load, which
        // a real client does with this packet after its 60-tick load timer; the fake client is
        // already loaded.
        target.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        target.setGameMode(GameType.SURVIVAL);
        target.teleportTo(level, position.x, position.y, position.z, Set.of(), 180.0F, 0.0F, false);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        target.setHealth(health);
        target.getFoodData().setFoodLevel(17);
        target.getFoodData().setSaturation(0.0F);
        target.setCustomName(Component.literal(name));
        target.setCustomNameVisible(true);
        TRACKED.put(
                target.getUUID(),
                new TrackedTarget(target.getUUID(), name, pinned ? position : null));
        return target.getId();
    }

    private static void removeTarget(MinecraftServer server, int entityId) {
        Entity target = server.overworld().getEntity(entityId);
        if (target instanceof ServerPlayer player) {
            TRACKED.remove(player.getUUID());
            server.getPlayerList().remove(player);
        } else if (target != null) {
            target.discard();
        }
    }

    private static void restoreState(MinecraftServer server) {
        restoreRules(server);
        TRACKED.clear();
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (player.getCustomName() != null) {
                server.getPlayerList().remove(player);
            }
        }
        if (!server.getPlayerList().getPlayers().isEmpty()) {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.removeEffect(MobEffects.SPEED);
            player.setAirSupply(player.getMaxAirSupply());
            player.getInventory().clearContent();
        }
    }

    private static void restoreRules(MinecraftServer server) {
        GameRules rules = server.overworld().getGameRules();
        rules.set(ModGameRules.DISABLE_IFRAMES, false, server);
        rules.set(ModGameRules.FASTER_EFFECT_TICKING, false, server);
        rules.set(ModGameRules.DISABLE_ATTACK_COOLDOWN, false, server);
        rules.set(ModGameRules.INSTANT_SHOOT, false, server);
    }
}
