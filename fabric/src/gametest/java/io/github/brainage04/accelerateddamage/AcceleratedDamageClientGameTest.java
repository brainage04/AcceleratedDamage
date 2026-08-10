package io.github.brainage04.accelerateddamage;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestRecorder;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestServers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.Properties;
import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
public final class AcceleratedDamageClientGameTest implements FabricClientGameTest {
    private static final int ARENA_Y = 64;

    @Override
    public void runTest(ClientGameTestContext context) {
        Properties serverProperties = ClientGameTestServers.flatServerProperties();

        ClientGameTestServers.withDedicatedServer(
                context,
                serverProperties,
                "Accelerated Damage complete showcase GameTest",
                server -> {
                    try {
                        server.runOnServer(AcceleratedDamageClientGameTest::prepareRange);
                        ClientGameTestServers.assertClientWorldAndPlayerAvailable(context);
                        context.waitTicks(20);

                        ClientGameTestRecorder.startRecording(context);
                        ClientGameTestRecorder.showStep(
                                context,
                                "showcase.ready",
                                "Accelerated Damage showcase",
                                "Every gamerule is exercised against live entities on a dedicated server"
                        );
                        context.waitTicks(30);

                        ClientGameTestRecorder.showStep(
                                context,
                                "showcase.rapid-melee",
                                "Immediate attacks and no invincibility frames",
                                "The player performs two real sword attacks; both must damage the target"
                        );
                        server.runOnServer(AcceleratedDamageClientGameTest::performFirstRapidAttack);
                        context.waitTicks(5);
                        server.runOnServer(AcceleratedDamageClientGameTest::performSecondRapidAttack);
                        context.waitTicks(25);

                        ClientGameTestRecorder.showStep(
                                context,
                                "showcase.effects",
                                "Ten-times-faster environmental and status effects",
                                "Fire, freezing, poison, and wither damage accelerate; the speed icon demonstrates client duration sync"
                        );
                        server.runOnServer(AcceleratedDamageClientGameTest::startEffectGallery);
                        context.waitTicks(20);
                        server.runOnServer(AcceleratedDamageClientGameTest::assertEffectGallery);
                        assertClientEffectDurationSynchronized(context, server);
                        context.waitTicks(20);

                        ClientGameTestRecorder.showStep(
                                context,
                                "showcase.instant-bow",
                                "One-tick bow shot",
                                "A real one-tick bow draw fires at and damages the marked target"
                        );
                        server.runOnServer(AcceleratedDamageClientGameTest::fireInstantBow);
                        context.waitTicks(15);
                        server.runOnServer(current -> assertTargetDamaged(current, "Bow target"));

                        ClientGameTestRecorder.showStep(
                                context,
                                "showcase.instant-crossbow",
                                "One-tick crossbow shot",
                                "A crossbow loads, fires, and damages its marked target after one tick"
                        );
                        server.runOnServer(AcceleratedDamageClientGameTest::fireInstantCrossbow);
                        context.waitTicks(15);
                        server.runOnServer(current -> assertTargetDamaged(current, "Crossbow target"));

                        ClientGameTestRecorder.showStep(
                                context,
                                "showcase.instant-trident",
                                "One-tick trident throw",
                                "A trident clears its vanilla minimum use time and damages its marked target"
                        );
                        server.runOnServer(AcceleratedDamageClientGameTest::throwInstantTrident);
                        context.waitTicks(20);
                        server.runOnServer(current -> assertTargetDamaged(current, "Trident target"));
                        context.waitTicks(20);
                    } finally {
                        server.runOnServer(AcceleratedDamageClientGameTest::restoreState);
                    }
                }
        );
    }

    private static void prepareRange(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        restoreRules(server);

        for (int x = -8; x <= 12; x++) {
            for (int z = -7; z <= 8; z++) {
                level.setBlockAndUpdate(
                        new net.minecraft.core.BlockPos(x, ARENA_Y - 1, z),
                        (z & 1) == 0 ? Blocks.STONE.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState()
                );
            }
        }
        for (int x = -8; x <= 12; x++) {
            level.setBlockAndUpdate(
                    new net.minecraft.core.BlockPos(x, ARENA_Y - 1, 0),
                    Blocks.GLOWSTONE.defaultBlockState()
            );
        }

        player.teleportTo(level, -6.5, ARENA_Y, 0.5, Set.of(), -90.0F, 0.0F, false);
        player.getInventory().clearContent();
        player.getInventory().setSelectedSlot(0);
        player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        player.getInventory().setItem(1, new ItemStack(Items.BOW));
        player.getInventory().setItem(2, new ItemStack(Items.CROSSBOW));
        player.getInventory().setItem(3, new ItemStack(Items.TRIDENT));
        player.getInventory().setItem(9, new ItemStack(Items.ARROW, 32));
        player.setCustomName(Component.literal("Accelerated fighter"));

        createTarget(level, "Rapid melee target", -1.0, ARENA_Y, -5.0);
        createTarget(level, "Fire target", 0.0, ARENA_Y, -2.0);
        createTarget(level, "Freeze target", 3.0, ARENA_Y, -2.0);
        createTarget(level, "Poison target", 6.0, ARENA_Y, -2.0);
        createTarget(level, "Wither target", 9.0, ARENA_Y, -2.0);
        createTarget(level, "Bow target", 2.0, ARENA_Y, 2.0);
        createTarget(level, "Crossbow target", 2.0, ARENA_Y, 4.0);
        createTarget(level, "Trident target", 2.0, ARENA_Y, 6.0);
    }

    private static void performFirstRapidAttack(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        Cow target = findTarget(level, "Rapid melee target");
        GameRules rules = level.getGameRules();
        rules.set(ModGameRules.DISABLE_IFRAMES, true, server);
        rules.set(ModGameRules.DISABLE_ATTACK_COOLDOWN, true, server);

        player.getInventory().setSelectedSlot(0);
        player.teleportTo(level, -3.0, ARENA_Y, -5.0, Set.of(), -90.0F, 0.0F, false);
        aimAt(player, target);
        player.resetAttackStrengthTicker();
        if (Float.compare(player.getAttackStrengthScale(0.0F), 1.0F) != 0) {
            throw new AssertionError("Expected the first displayed attack to be fully charged.");
        }
        player.swing(InteractionHand.MAIN_HAND);
        player.attack(target);
        if (!(target.getHealth() < target.getMaxHealth())) {
            throw new AssertionError("Expected the first displayed sword attack to deal damage.");
        }
    }

    private static void performSecondRapidAttack(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        Cow target = findTarget(level, "Rapid melee target");
        float healthBefore = target.getHealth();

        player.resetAttackStrengthTicker();
        if (Float.compare(player.getAttackStrengthScale(0.0F), 1.0F) != 0) {
            throw new AssertionError("Expected the consecutive displayed attack to be fully charged.");
        }
        player.swing(InteractionHand.MAIN_HAND);
        player.attack(target);
        if (!(target.getHealth() < healthBefore)) {
            throw new AssertionError("Expected the consecutive real sword attack to bypass invincibility.");
        }
    }

    private static void startEffectGallery(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        GameRules rules = level.getGameRules();
        rules.set(ModGameRules.DISABLE_IFRAMES, false, server);
        rules.set(ModGameRules.DISABLE_ATTACK_COOLDOWN, false, server);
        rules.set(ModGameRules.FASTER_EFFECT_TICKING, true, server);

        Cow fire = findTarget(level, "Fire target");
        fire.setRemainingFireTicks(240);

        Cow freeze = findTarget(level, "Freeze target");
        freeze.setTicksFrozen(freeze.getTicksRequiredToFreeze() + 400);

        findTarget(level, "Poison target").addEffect(new MobEffectInstance(MobEffects.POISON, 240, 1));
        findTarget(level, "Wither target").addEffect(new MobEffectInstance(MobEffects.WITHER, 240, 1));
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 400));
        player.teleportTo(level, -6.5, ARENA_Y, 0.5, Set.of(), -90.0F, 0.0F, false);
    }

    private static void assertEffectGallery(MinecraftServer server) {
        ServerLevel level = server.overworld();
        assertTargetDamaged(server, "Fire target");
        assertTargetDamaged(server, "Freeze target");
        assertTargetDamaged(server, "Poison target");
        assertTargetDamaged(server, "Wither target");

        MobEffectInstance effect = server.getPlayerList().getPlayers().getFirst().getEffect(MobEffects.SPEED);
        if (effect == null || effect.getDuration() >= 300) {
            throw new AssertionError("Expected the server-side speed duration to advance ten times faster.");
        }
    }

    private static void assertClientEffectDurationSynchronized(
            ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext server
    ) {
        int serverDuration = server.computeOnServer(current -> {
            MobEffectInstance effect = current.getPlayerList().getPlayers().getFirst().getEffect(MobEffects.SPEED);
            if (effect == null) {
                throw new AssertionError("Expected the server player to retain the speed effect.");
            }
            return effect.getDuration();
        });
        int clientDuration = context.computeOnClient(client -> {
            if (client.player == null) {
                throw new AssertionError("Expected the client player to be available.");
            }
            MobEffectInstance effect = client.player.getEffect(MobEffects.SPEED);
            if (effect == null) {
                throw new AssertionError("Expected the vanilla client to receive the speed effect.");
            }
            return effect.getDuration();
        });
        if (Math.abs(serverDuration - clientDuration) > 20) {
            throw new AssertionError(
                    "Expected accelerated effect duration packets to keep the vanilla client synchronized: server "
                            + serverDuration + ", client " + clientDuration
            );
        }
    }

    private static void fireInstantBow(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = prepareShooter(server, "Bow target", 1);
        ItemStack bow = player.getMainHandItem();
        bow.use(level, player, InteractionHand.MAIN_HAND);
        boolean fired = ((BowItem) Items.BOW).releaseUsing(
                bow, level, player, bow.getUseDuration(player) - 1
        );
        if (!fired) {
            throw new AssertionError("Expected the displayed one-tick bow shot to fire.");
        }
    }

    private static void fireInstantCrossbow(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = prepareShooter(server, "Crossbow target", 2);
        ItemStack crossbow = player.getMainHandItem();
        crossbow.use(level, player, InteractionHand.MAIN_HAND);
        int remainingUseDuration = crossbow.getUseDuration(player) - 1;
        ((CrossbowItem) Items.CROSSBOW).onUseTick(level, player, crossbow, remainingUseDuration);
        ((CrossbowItem) Items.CROSSBOW).releaseUsing(crossbow, level, player, remainingUseDuration);
        if (!CrossbowItem.isCharged(crossbow)) {
            throw new AssertionError("Expected the displayed crossbow to charge after one tick.");
        }
        crossbow.use(level, player, InteractionHand.MAIN_HAND);
    }

    private static void throwInstantTrident(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = prepareShooter(server, "Trident target", 3);
        ItemStack trident = player.getMainHandItem();
        trident.use(level, player, InteractionHand.MAIN_HAND);
        boolean thrown = ((TridentItem) Items.TRIDENT).releaseUsing(
                trident, level, player, trident.getUseDuration(player) - 1
        );
        if (!thrown) {
            throw new AssertionError("Expected the displayed trident to throw after one tick.");
        }
    }

    private static ServerPlayer prepareShooter(MinecraftServer server, String targetName, int selectedSlot) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        Cow target = findTarget(level, targetName);
        level.getGameRules().set(ModGameRules.INSTANT_SHOOT, true, server);
        player.getInventory().setSelectedSlot(selectedSlot);
        player.connection.send(new ClientboundSetHeldSlotPacket(selectedSlot));
        player.teleportTo(level, -3.0, ARENA_Y, target.getZ(), Set.of(), -90.0F, 0.0F, false);
        aimAt(player, target);
        return player;
    }

    private static Cow createTarget(ServerLevel level, String label, double x, double y, double z) {
        Cow target = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
        if (target == null) {
            throw new AssertionError("Expected to create showcase target " + label + ".");
        }
        target.setPos(x, y, z);
        target.setNoAi(true);
        target.setCustomName(Component.literal(label));
        target.setCustomNameVisible(true);
        level.addFreshEntity(target);
        return target;
    }

    private static Cow findTarget(ServerLevel level, String label) {
        return level.getEntitiesOfClass(
                        Cow.class,
                        new net.minecraft.world.phys.AABB(-10, ARENA_Y - 2, -10, 15, ARENA_Y + 4, 10)
                ).stream()
                .filter(target -> target.getCustomName() != null && target.getCustomName().getString().equals(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected showcase target " + label + "."));
    }

    private static void aimAt(ServerPlayer player, Cow target) {
        player.lookAt(
                EntityAnchorArgument.Anchor.EYES,
                target,
                EntityAnchorArgument.Anchor.EYES
        );
    }

    private static void assertTargetDamaged(MinecraftServer server, String label) {
        Cow target = findTarget(server.overworld(), label);
        if (!(target.getHealth() < target.getMaxHealth())) {
            throw new AssertionError("Expected the displayed weapon or effect to damage " + label + ".");
        }
    }

    private static void restoreState(MinecraftServer server) {
        restoreRules(server);
        if (!server.getPlayerList().getPlayers().isEmpty()) {
            server.getPlayerList().getPlayers().getFirst().removeEffect(MobEffects.SPEED);
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
