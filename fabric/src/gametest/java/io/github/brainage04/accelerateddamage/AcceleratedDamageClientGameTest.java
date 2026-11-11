package io.github.brainage04.accelerateddamage;

import com.mojang.authlib.GameProfile;
import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.minecraftmodconventions.ClientGameTestPlayerActions;
import io.github.brainage04.minecraftmodconventions.ClientGameTestRecorder;
import io.github.brainage04.minecraftmodconventions.ClientGameTestServers;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/// The showcase the owner asked for: every effect against player-shaped targets, with the target's health printed in
/// the caption panel after each hit, so a hit landing is visible twice. Every positive stage is followed by the same
/// actions with the rule off, where the repeat hits do nothing. All player actions go through
/// `ClientGameTestPlayerActions`, so the player only ever turns to a target, walks into vanilla reach and presses the
/// real attack or use key on the crosshair target.
@SuppressWarnings("UnstableApiUsage")
public final class AcceleratedDamageClientGameTest implements FabricClientGameTest {
    private static final int ARENA_Y = 64;
    private static final double MELEE_DISTANCE = 2.6D;

    @Override
    public void runTest(ClientGameTestContext context) {
        Properties serverProperties = ClientGameTestServers.flatServerProperties();

        ClientGameTestServers.withDedicatedServer(
                context,
                serverProperties,
                "Accelerated Damage complete showcase GameTest",
                server -> {
                    int playerId;
                    try {
                        playerId = server.computeOnServer(current -> {
                            prepareArena(current);
                            return current.getPlayerList().getPlayers().getFirst().getId();
                        });
                        ClientGameTestServers.assertClientWorldAndPlayerAvailable(context);
                        context.waitTicks(20);

                        ClientGameTestRecorder.startRecording(context);
                        ClientGameTestRecorder.showStep(
                                context,
                                "showcase.ready",
                                "Accelerated Damage",
                                "Every gamerule against player targets, with their health after each hit"
                        );
                        context.waitTicks(20);

                        recordMelee(context, server, playerId);
                        recordDrowning(context, server, playerId);
                        recordBow(context, server, playerId);
                        recordCrossbow(context, server, playerId);
                        recordTrident(context, server, playerId);
                        recordFireworkRocket(context, server, playerId);
                        recordEffects(context, server, playerId);

                        ClientGameTestRecorder.showStep(
                                context,
                                "showcase.done",
                                "Accelerated Damage",
                                "Rules off: the same hits land once per cooldown window"
                        );
                        context.waitTicks(20);
                        ClientGameTestRecorder.stopRecording(context);

                        assertEffectsOff(context, server, playerId);
                    } finally {
                        server.runOnServer(AcceleratedDamageClientGameTest::restoreState);
                    }
                }
        );
    }

    private static void prepareArena(MinecraftServer server) {
        ServerLevel level = server.overworld();
        restoreRules(server);
        // Every target is a player, and a server whose PvP game rule is off rejects all player-versus-player damage in
        // ServerPlayer#hurtServer, so the showcase world runs with the rule on.
        level.getGameRules().set(GameRules.PVP, true, server);
        for (int x = -8; x <= 12; x++) {
            for (int z = -10; z <= 10; z++) {
                level.setBlockAndUpdate(
                        new BlockPos(x, ARENA_Y - 1, z),
                        (z & 1) == 0 ? Blocks.STONE.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState()
                );
            }
        }
        for (int x = -8; x <= 12; x++) {
            level.setBlockAndUpdate(new BlockPos(x, ARENA_Y - 1, 0), Blocks.GLOWSTONE.defaultBlockState());
        }
        // The melee target's back is against this wall, so a knockback that a player could not chase inside the
        // ten-tick window still leaves the target in reach for the next hit.
        for (int x = -4; x <= 4; x++) {
            for (int y = ARENA_Y; y <= ARENA_Y + 2; y++) {
                level.setBlockAndUpdate(new BlockPos(x, y, 5), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        for (int y = ARENA_Y; y <= ARENA_Y + 3; y++) {
            for (int x = 6; x <= 12; x++) {
                for (int z = -10; z <= -3; z++) {
                    boolean wall = x == 6 || x == 12 || z == -10 || z == -3 || y == ARENA_Y + 3;
                    level.setBlockAndUpdate(
                            new BlockPos(x, y, z),
                            wall ? Blocks.GLASS.defaultBlockState() : Blocks.WATER.defaultBlockState()
                    );
                }
            }
        }

        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        player.teleportTo(level, 0.5, ARENA_Y, 0.5, Set.of(), 0.0F, 0.0F, false);
        player.setHealth(player.getMaxHealth());
        player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);
    }

    private static void recordMelee(ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        ClientGameTestRecorder.showStep(
                context,
                "showcase.melee",
                "Rapid melee: every hit lands",
                "Five sword hits inside one cooldown window, and every one knocks the target back"
        );
        int targetId = server.computeOnServer(current -> {
            ServerLevel level = current.overworld();
            GameRules rules = level.getGameRules();
            rules.set(ModGameRules.DISABLE_IFRAMES, true, current);
            rules.set(ModGameRules.DISABLE_ATTACK_COOLDOWN, true, current);
            ServerPlayer player = player(current, playerId);
            giveSword(player);
            player.teleportTo(level, 0.5, ARENA_Y, 1.5, Set.of(), 0.0F, 0.0F, false);
            return spawnTarget(current, "MeleeDummy", new Vec3(0.5, ARENA_Y, 4.5));
        });
        context.waitTicks(4);
        strikeRepeatedly(context, server, playerId, targetId, 5, true, true);
        server.runOnServer(current -> removeTarget(current, targetId));

        ClientGameTestRecorder.showStep(
                context,
                "showcase.melee-off",
                "The same hits with the rule off",
                "Vanilla rejects every repeat hit inside the ten-tick part of the cooldown"
        );
        int vanillaTargetId = server.computeOnServer(current -> {
            GameRules rules = current.overworld().getGameRules();
            rules.set(ModGameRules.DISABLE_IFRAMES, false, current);
            rules.set(ModGameRules.DISABLE_ATTACK_COOLDOWN, false, current);
            ServerPlayer player = player(current, playerId);
            player.teleportTo(current.overworld(), 0.5, ARENA_Y, 1.5, Set.of(), 0.0F, 0.0F, false);
            return spawnTarget(current, "MeleeDummyOff", new Vec3(0.5, ARENA_Y, 4.5));
        });
        context.waitTicks(4);
        strikeRepeatedly(context, server, playerId, vanillaTargetId, 2, false, false);
        server.runOnServer(current -> removeTarget(current, vanillaTargetId));
    }

    /// Attacks the target with the real attack key until it dies, prints its health after every hit and checks the
    /// pattern the rule promises: every hit lands when it is on, only the first one of the window when it is off.
    private static void strikeRepeatedly(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            int playerId,
            int targetId,
            int hits,
            boolean accelerated,
            boolean chase
    ) {
        float previous = server.computeOnServer(current -> living(current, targetId).getHealth());
        for (int hit = 1; hit <= hits; hit++) {
            if (chase || hit == 1) {
                Vec3 position = server.computeOnServer(current -> entity(current, targetId).position());
                // A sword's minimum attack range is two blocks, so the player stands 2.6 blocks out and still inside
                // vanilla entity reach.
                ClientGameTestPlayerActions.walkTo(context, position, MELEE_DISTANCE);
            }
            ClientGameTestPlayerActions.lookAt(context, chest(server, targetId));
            long actionTimeBefore = server.computeOnServer(current -> player(current, playerId).getLastActionTime());
            ClientGameTestPlayerActions.attackEntity(context, targetId);
            String aimAtPress = context.computeOnClient(client -> String.valueOf(client.hitResult));
            context.waitTicks(3);
            long actionTimeAfter = server.computeOnServer(current -> player(current, playerId).getLastActionTime());
            float health = server.computeOnServer(current -> living(current, targetId).getHealth());
            ClientGameTestRecorder.log(context, "hit " + hit + "  target health " + health);
            if (accelerated && health >= previous) {
                String clientState = context.computeOnClient(client -> "hitResult=" + client.hitResult
                        + " handsBusy=" + client.player.isHandsBusy()
                        + " usingItem=" + client.player.isUsingItem()
                        + " attackScale=" + client.player.getAttackStrengthScale(0.0F)
                        + " held=" + client.player.getMainHandItem()
                        + " xRot=" + client.player.getXRot() + " yRot=" + client.player.getYRot())
                        + " | aimAtPress=" + aimAtPress
                        + " actionTime " + actionTimeBefore + " -> " + actionTimeAfter;
                throw new AssertionError(
                        "Expected hit " + hit + " to damage the target; health stayed at " + health + ": "
                                + server.computeOnServer(current -> describeTarget(current, targetId, playerId))
                                + " | " + clientState
                );
            }
            if (!accelerated && hit > 1 && health != previous) {
                throw new AssertionError(
                        "Expected vanilla invincibility to reject hit " + hit + "; health fell from " + previous + " to " + health
                );
            }
            previous = health;
            if (health <= 0.0F) {
                return;
            }
        }
    }

    private static void recordDrowning(ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        ClientGameTestRecorder.showStep(
                context,
                "showcase.drowning",
                "Drowning in two seconds",
                "The air bar empties ten times faster and drowning damage starts"
        );
        server.runOnServer(current -> {
            GameRules rules = current.overworld().getGameRules();
            rules.set(ModGameRules.FASTER_EFFECT_TICKING, true, current);
            ServerPlayer player = player(current, playerId);
            player.teleportTo(current.overworld(), 9.5, ARENA_Y, -6.5, Set.of(), 0.0F, 0.0F, false);
            player.setAirSupply(player.getMaxAirSupply());
            player.setHealth(player.getMaxHealth());
        });
        context.waitTicks(4);
        reportAir(context, server, playerId, "start");
        context.waitTicks(10);
        reportAir(context, server, playerId, "10 ticks");
        context.waitTicks(10);
        reportAir(context, server, playerId, "20 ticks");
        context.waitTicks(20);
        int[] drowned = reportAir(context, server, playerId, "40 ticks");
        if (drowned[0] > 0 || drowned[1] >= 20) {
            throw new AssertionError(
                    "Expected the air to run out and drowning damage to start, air was " + drowned[0]
                            + " and health " + drowned[1]
            );
        }

        ClientGameTestRecorder.showStep(
                context,
                "showcase.drowning-off",
                "The same two seconds with the rule off",
                "Vanilla air lasts fifteen seconds, so the bar has barely moved"
        );
        server.runOnServer(current -> {
            current.overworld().getGameRules().set(ModGameRules.FASTER_EFFECT_TICKING, false, current);
            ServerPlayer player = player(current, playerId);
            player.setAirSupply(player.getMaxAirSupply());
            player.setHealth(player.getMaxHealth());
        });
        context.waitTicks(4);
        reportAir(context, server, playerId, "start");
        context.waitTicks(40);
        int[] vanillaAir = reportAir(context, server, playerId, "40 ticks");
        if (vanillaAir[0] < 200 || vanillaAir[1] != 20) {
            throw new AssertionError(
                    "Expected vanilla air to last, air was " + vanillaAir[0] + " and health " + vanillaAir[1]
            );
        }
        server.runOnServer(current -> player(current, playerId).setAirSupply(player(current, playerId).getMaxAirSupply()));
    }

    private static int[] reportAir(ClientGameTestContext context, TestDedicatedServerContext server, int playerId, String label) {
        int[] state = server.computeOnServer(current -> {
            ServerPlayer player = player(current, playerId);
            return new int[]{player.getAirSupply(), Math.round(player.getHealth())};
        });
        ClientGameTestRecorder.log(context, label + ": air " + state[0] + " health " + state[1]);
        return state;
    }

    private static void recordBow(ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        ClientGameTestRecorder.showStep(
                context,
                "showcase.bow",
                "Bow: five arrows in one second",
                "Each one-tick draw fires a full-power arrow at the player target"
        );
        int targetId = shootStage(context, server, playerId, "BowDummy", Items.BOW, Items.ARROW, 5);
        server.runOnServer(current -> removeTarget(current, targetId));

        ClientGameTestRecorder.showStep(
                context,
                "showcase.bow-off",
                "The same shots with the rule off",
                "Only the first arrow inside the cooldown window lands"
        );
        int vanillaTargetId = shootStage(context, server, playerId, "BowDummyOff", Items.BOW, Items.ARROW, 2, false);
        server.runOnServer(current -> removeTarget(current, vanillaTargetId));
    }

    private static void recordCrossbow(ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        ClientGameTestRecorder.showStep(
                context,
                "showcase.crossbow",
                "Crossbow: five shots in under two seconds",
                "Loading takes one tick and every bolt inside the window lands"
        );
        int targetId = shootStage(context, server, playerId, "CrossbowDummy", Items.CROSSBOW, Items.ARROW, 5);
        server.runOnServer(current -> removeTarget(current, targetId));

        ClientGameTestRecorder.showStep(
                context,
                "showcase.crossbow-off",
                "The same shots with the rule off",
                "Only the first bolt inside the cooldown window lands"
        );
        int vanillaTargetId = shootStage(context, server, playerId, "CrossbowDummyOff", Items.CROSSBOW, Items.ARROW, 2, false);
        server.runOnServer(current -> removeTarget(current, vanillaTargetId));
    }

    private static void recordTrident(ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        ClientGameTestRecorder.showStep(
                context,
                "showcase.trident",
                "Trident: five throws in under two seconds",
                "The vanilla ten-tick wind-up is gone, so every trident flies straight away"
        );
        int targetId = shootStage(context, server, playerId, "TridentDummy", Items.TRIDENT, Items.TRIDENT, 5);
        server.runOnServer(current -> removeTarget(current, targetId));

        ClientGameTestRecorder.showStep(
                context,
                "showcase.trident-off",
                "The same throws with the rule off",
                "Only the first trident inside the cooldown window lands"
        );
        int vanillaTargetId = shootStage(
                context,
                server,
                playerId,
                "TridentDummyOff",
                Items.TRIDENT,
                Items.TRIDENT,
                2,
                false
        );
        server.runOnServer(current -> removeTarget(current, vanillaTargetId));
    }

    private static void recordFireworkRocket(ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        ClientGameTestRecorder.showStep(
                context,
                "showcase.firework",
                "Crossbow with firework rockets",
                "Each rocket explodes on the target and its damage lands inside the cooldown window"
        );
        int targetId = shootStage(
                context,
                server,
                playerId,
                "FireworkDummy",
                Items.CROSSBOW,
                Items.FIREWORK_ROCKET,
                4
        );
        server.runOnServer(current -> removeTarget(current, targetId));

        ClientGameTestRecorder.showStep(
                context,
                "showcase.firework-off",
                "The same rockets with the rule off",
                "Only the first rocket inside the cooldown window lands"
        );
        int vanillaTargetId = shootStage(
                context,
                server,
                playerId,
                "FireworkDummyOff",
                Items.CROSSBOW,
                Items.FIREWORK_ROCKET,
                2,
                false
        );
        server.runOnServer(current -> removeTarget(current, vanillaTargetId));
    }

    /// Equips the weapon and its ammunition, spawns a player target in front of the shooter and fires with the real use
    /// key until the target dies or the shot budget runs out, printing the target's health after every shot.
    private static int shootStage(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            int playerId,
            String targetName,
            Item weapon,
            Item ammunition,
            int shots
    ) {
        return shootStage(context, server, playerId, targetName, weapon, ammunition, shots, true);
    }

    private static int shootStage(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            int playerId,
            String targetName,
            Item weapon,
            Item ammunition,
            int shots,
            boolean accelerated
    ) {
        int targetId = server.computeOnServer(current -> {
            GameRules rules = current.overworld().getGameRules();
            rules.set(ModGameRules.INSTANT_SHOOT, true, current);
            rules.set(ModGameRules.DISABLE_IFRAMES, accelerated, current);
            ServerPlayer player = player(current, playerId);
            player.teleportTo(current.overworld(), 0.5, ARENA_Y, 0.5, Set.of(), 0.0F, 0.0F, false);
            player.setHealth(player.getMaxHealth());
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            if (weapon == Items.TRIDENT) {
                for (int slot = 0; slot < shots; slot++) {
                    player.getInventory().setItem(slot, new ItemStack(Items.TRIDENT));
                }
            } else {
                player.getInventory().setItem(0, new ItemStack(weapon));
            }
            if (ammunition == Items.FIREWORK_ROCKET) {
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.FIREWORK_ROCKET, 32));
            } else {
                player.getInventory().setItem(8, new ItemStack(ammunition, 32));
            }
            player.getInventory().setSelectedSlot(0);
            return spawnTarget(current, targetName, new Vec3(0.5, ARENA_Y, 4.5));
        });
        context.waitTicks(4);
        ClientGameTestPlayerActions.requireVanillaInteractionRanges(context);
        ClientGameTestPlayerActions.lookAt(context, chest(server, targetId));
        float[] previous = {server.computeOnServer(current -> living(current, targetId).getHealth())};
        float[] firstDrop = {0.0F};

        for (int shot = 1; shot <= shots; shot++) {
            int hotbarSlot = shot - 1;
            if (weapon == Items.TRIDENT) {
                context.getInput().pressKey(options -> options.keyHotbarSlots[hotbarSlot]);
                context.waitTicks(2);
            }
            if (weapon == Items.CROSSBOW) {
                context.getInput().holdKeyFor(options -> options.keyUse, 1);
                context.waitTicks(2);
                context.getInput().pressKey(options -> options.keyUse);
            } else {
                context.getInput().holdKeyFor(options -> options.keyUse, 1);
            }
            context.waitTicks(3);
            float health = server.computeOnServer(current -> living(current, targetId).getHealth());
            float drop = previous[0] - health;
            ClientGameTestRecorder.log(context, "shot " + shot + "  target health " + health);
            if (accelerated) {
                if (drop <= 0.0F) {
                    throw new AssertionError("Expected shot " + shot + " to damage the target; health stayed at " + health);
                }
            } else if (shot == 1) {
                if (drop <= 0.0F) {
                    throw new AssertionError("Expected the first vanilla shot to damage the target; health stayed at " + health);
                }
                firstDrop[0] = drop;
                // Vanilla still passes on the part of a later hit that exceeds the window's largest hit, so the
                // negative case shows that no repeat hit lands in full rather than that nothing happens at all.
            } else if (drop >= firstDrop[0]) {
                throw new AssertionError(
                        "Expected vanilla invincibility to keep shot " + shot + " from landing in full; it took "
                                + drop + " of the first hit's " + firstDrop[0]
                );
            }
            previous[0] = health;
            if (health <= 0.0F) {
                return targetId;
            }
        }
        return targetId;
    }

    private static void recordEffects(ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        ClientGameTestRecorder.showStep(
                context,
                "showcase.effects",
                "Fire and poison ten times faster",
                "Two player targets burn and wither while their health falls every couple of ticks"
        );
        List<Integer> targets = server.computeOnServer(current -> {
            ServerLevel level = current.overworld();
            GameRules rules = level.getGameRules();
            rules.set(ModGameRules.FASTER_EFFECT_TICKING, true, current);
            rules.set(ModGameRules.DISABLE_IFRAMES, false, current);
            ServerPlayer player = player(current, playerId);
            player.teleportTo(level, 0.5, ARENA_Y, -1.5, Set.of(), 0.0F, 10.0F, false);
            player.setHealth(player.getMaxHealth());
            player.getInventory().clearContent();
            int burning = spawnTarget(current, "BurningDummy", new Vec3(-1.5, ARENA_Y, 3.5));
            int poisoned = spawnTarget(current, "PoisonedDummy", new Vec3(2.5, ARENA_Y, 3.5));
            living(current, burning).setRemainingFireTicks(400);
            living(current, poisoned).addEffect(new MobEffectInstance(MobEffects.POISON, 400, 2));
            return List.of(burning, poisoned);
        });
        for (int tick = 1; tick <= 4; tick++) {
            context.waitTicks(5);
            ClientGameTestRecorder.log(context, server.computeOnServer(current -> effectsLabel(current, targets)));
        }
        server.runOnServer(current -> {
            for (int target : targets) {
                if (living(current, target).getHealth() > 14.0F) {
                    throw new AssertionError(
                            "Expected accelerated fire and poison to take more than six health in twenty ticks, "
                                    + "health was " + living(current, target).getHealth()
                    );
                }
                removeTarget(current, target);
            }
        });
    }

    private static String effectsLabel(MinecraftServer server, List<Integer> targets) {
        StringBuilder label = new StringBuilder("health now");
        for (int target : targets) {
            Entity entity = server.overworld().getEntity(target);
            if (entity instanceof LivingEntity living) {
                label.append("  ").append(living.getHealth());
            }
        }
        return label.toString();
    }

    private static void assertEffectsOff(ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        int[] slow = server.computeOnServer(current -> {
            ServerLevel level = current.overworld();
            GameRules rules = level.getGameRules();
            rules.set(ModGameRules.FASTER_EFFECT_TICKING, false, current);
            int target = spawnTarget(current, "SlowDummy", new Vec3(0.5, ARENA_Y, 3.5));
            LivingEntity living = living(current, target);
            living.setHealth(living.getMaxHealth());
            living.setRemainingFireTicks(400);
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 2));
            return new int[]{target};
        });
        context.waitTicks(20);
        float health = server.computeOnServer(current -> living(current, slow[0]).getHealth());
        server.runOnServer(current -> {
            removeTarget(current, slow[0]);
            restoreRules(current);
        });
        if (health < 17.0F) {
            throw new AssertionError("Expected vanilla fire and poison to deal little damage in twenty ticks, health was " + health);
        }
        ClientGameTestRecorder.log(context, "rule off: health after twenty ticks " + health);
        ClientGameTestPlayerActions.requireVanillaInteractionRanges(context);
    }

    private static String describeTarget(MinecraftServer server, int targetId, int playerId) {
        LivingEntity target = living(server, targetId);
        ServerPlayer player = player(server, playerId);
        StringBuilder state = new StringBuilder();
        state.append("target=").append(target.getType())
                .append(" health=").append(target.getHealth())
                .append(" invulnerable=").append(target.isInvulnerable())
                .append(" cooldown=").append(target.damageCooldownTime)
                .append(" hurtTime=").append(target.hurtTime)
                .append(" lastSource=").append(target.getLastDamageSource())
                .append(" mode=").append(target instanceof ServerPlayer targetPlayer ? targetPlayer.gameMode() : null)
                .append(" targetAbilitiesInvulnerable=")
                .append(target instanceof net.minecraft.world.entity.player.Player targetPlayer
                        ? targetPlayer.getAbilities().invulnerable
                        : null)
                .append(" playerMode=").append(player.gameMode())
                .append(" playerAbilitiesInvulnerable=").append(player.getAbilities().invulnerable)
                .append(" attackScale=").append(player.getAttackStrengthScale(0.5F))
                .append(" distance=").append(Math.sqrt(target.getBoundingBox().distanceToSqr(player.getEyePosition())))
                .append(" attackDamage=").append(player.getAttributeValue(Attributes.ATTACK_DAMAGE))
                .append(" withinAttackRange=")
                .append(player.isWithinAttackRange(player.getMainHandItem(), target.getBoundingBox(), 3.0))
                .append(" cannotAttackWithItem=")
                .append(player.cannotAttackWithItem(player.getMainHandItem(), 5))
                .append(" held=").append(player.getMainHandItem())
                .append(" probeInvulnerableTo=")
                .append(target.isInvulnerableTo(
                        server.overworld(),
                        server.overworld().damageSources().playerAttack(player)
                ))
                .append(" isRemoved=").append(target.isRemoved())
                .append(" removalReason=").append(target.getRemovalReason())
                .append(" temporarilyInvulnerable=").append(target.isTemporarilyInvulnerable())
                .append(" pvpRule=").append(server.overworld().getGameRules().get(GameRules.PVP))
                .append(" canHarmPlayer=")
                .append(target instanceof ServerPlayer targetPlayer && targetPlayer.canHarmPlayer(player));
        return state.toString();
    }

    private static void giveSword(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getInventory().setSelectedSlot(0);
        player.getInventory().setItem(0, new ItemStack(Items.WOODEN_SWORD));
    }

    private static Vec3 chest(TestDedicatedServerContext server, int entityId) {
        return server.computeOnServer(current -> entity(current, entityId).getBoundingBox().getCenter());
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

    /// Connects a player-shaped, survival target the way a vanilla GameTest does, so the vanilla client renders it as
    /// a player the mod's damage applies to.
    private static int spawnTarget(MinecraftServer server, String name, Vec3 position) {
        ServerLevel level = server.overworld();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer target = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, target, cookie);
        // ServerPlayer#isInvulnerableTo rejects every damage source until the client acknowledges the world load, which
        // a real client does with this packet after its 60-tick load timer; the fake client is already loaded.
        target.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        target.setGameMode(GameType.SURVIVAL);
        target.teleportTo(level, position.x, position.y, position.z, Set.of(), 180.0F, 0.0F, false);
        target.setHealth(target.getMaxHealth());
        target.setCustomName(Component.literal(name));
        target.setCustomNameVisible(true);
        return target.getId();
    }

    private static void removeTarget(MinecraftServer server, int entityId) {
        if (server.overworld().getEntity(entityId) instanceof ServerPlayer target) {
            server.getPlayerList().remove(target);
        }
    }

    private static void restoreState(MinecraftServer server) {
        restoreRules(server);
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (player.getCustomName() != null) {
                server.getPlayerList().remove(player);
            }
        }
        if (!server.getPlayerList().getPlayers().isEmpty()) {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.removeEffect(MobEffects.SPEED);
            player.setAirSupply(player.getMaxAirSupply());
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
