package io.github.brainage04.accelerateddamage;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestRecorder;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestServers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.Properties;

@SuppressWarnings("UnstableApiUsage")
public final class AcceleratedDamageClientGameTest implements FabricClientGameTest {
    private static final int ARENA_Y = 64;

    @Override
    public void runTest(ClientGameTestContext context) {
        Properties serverProperties = ClientGameTestServers.flatServerProperties();

        ClientGameTestServers.withDedicatedServer(context, serverProperties, "Accelerated Damage combat range GameTest", server -> { try {
            server.runOnServer(AcceleratedDamageClientGameTest::prepareRange);
            ClientGameTestServers.assertClientWorldAndPlayerAvailable(context);
            context.waitTicks(20);
        
            ClientGameTestRecorder.startRecording(context);
            ClientGameTestRecorder.showStep(
                    context,
                    "combat-range.ready",
                    "Accelerated Damage combat range",
                    "Two marked targets demonstrate no invincibility frames and instant bow charge"
            );
            context.waitTicks(30);
        
            ClientGameTestRecorder.showStep(
                    context,
                    "combat-range.consecutive-hits",
                    "Consecutive hits bypass invincibility frames",
                    "The red target takes two immediate melee hits; its health is asserted on the server"
            );
            server.runOnServer(AcceleratedDamageClientGameTest::demonstrateConsecutiveDamage);
            context.waitTicks(30);
        
            ClientGameTestRecorder.showStep(
                    context,
                    "combat-range.instant-bow",
                    "Instant bow charge",
                    "The player holds a bow and the server verifies a one-tick full charge"
            );
            server.runOnServer(AcceleratedDamageClientGameTest::demonstrateInstantBow);
            context.waitTicks(40);
        } finally {
            server.runOnServer(AcceleratedDamageClientGameTest::restoreRules);
            ;
        } });
    }

    private static void prepareRange(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        GameRules rules = level.getGameRules();
        rules.set(ModGameRules.DISABLE_IFRAMES, false, server);
        rules.set(ModGameRules.INSTANT_SHOOT, false, server);

        for (int x = -8; x <= 12; x++) {
            for (int z = -5; z <= 5; z++) {
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, ARENA_Y - 1, z), Blocks.STONE.defaultBlockState());
            }
        }
        for (int z = -5; z <= 5; z++) {
            level.setBlockAndUpdate(new net.minecraft.core.BlockPos(3, ARENA_Y - 1, z), Blocks.GLOWSTONE.defaultBlockState());
        }

        player.teleportTo(level, -6.5, ARENA_Y, 0.5, java.util.Set.of(), -90.0F, 0.0F, false);
        player.getInventory().clearContent();
        player.getInventory().setSelectedSlot(0);
        player.getInventory().setItem(0, new ItemStack(Items.BOW));
        player.getInventory().setItem(9, new ItemStack(Items.ARROW, 16));
        player.setCustomName(Component.literal("Range Archer"));

        Zombie attacker = createTarget(level, "Melee striker", -1.5, ARENA_Y, -1.5);
        attacker.setNoAi(true);
        Zombie victim = createTarget(level, "Two-hit target", 1.5, ARENA_Y, -1.5);
        victim.setNoAi(true);
        Zombie bowTarget = createTarget(level, "Instant bow target", 7.5, ARENA_Y, 0.5);
        bowTarget.setNoAi(true);
    }

    private static void demonstrateConsecutiveDamage(MinecraftServer server) {
        ServerLevel level = server.overworld();
        GameRules rules = level.getGameRules();
        Zombie attacker = findTarget(level, "Melee striker");
        Zombie victim = findTarget(level, "Two-hit target");
        float initialHealth = victim.getHealth();
        rules.set(ModGameRules.DISABLE_IFRAMES, true, server);
        try {
            if (!victim.hurtServer(level, victim.damageSources().mobAttack(attacker), 2.0F)) {
                throw new AssertionError("Expected the first displayed melee hit to deal damage.");
            }
            float healthAfterFirstHit = victim.getHealth();
            if (!victim.hurtServer(level, victim.damageSources().mobAttack(attacker), 2.0F)) {
                throw new AssertionError("Expected the second displayed melee hit to bypass invincibility frames.");
            }
            if (!(healthAfterFirstHit < initialHealth && victim.getHealth() < healthAfterFirstHit)) {
                throw new AssertionError("Expected both consecutive melee hits to lower the target's health.");
            }
        } finally {
            rules.set(ModGameRules.DISABLE_IFRAMES, false, server);
        }
    }

    private static void demonstrateInstantBow(MinecraftServer server) {
        ServerLevel level = server.overworld();
        GameRules rules = level.getGameRules();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        rules.set(ModGameRules.INSTANT_SHOOT, true, server);
        try {
            if (Float.compare(BowItem.getPowerForTime(1), 1.0F) != 0) {
                throw new AssertionError("Expected a one-tick bow draw to be fully charged.");
            }
            player.getInventory().setSelectedSlot(0);
            ItemStack bow = player.getInventory().getSelectedItem();
            if (!bow.is(Items.BOW)) {
                throw new AssertionError("Expected the visible player weapon to be a bow.");
            }
            player.startUsingItem(InteractionHand.MAIN_HAND);
            bow.getItem().releaseUsing(bow, level, player, bow.getUseDuration(player) - 1);
        } finally {
            rules.set(ModGameRules.INSTANT_SHOOT, false, server);
        }
    }

    private static Zombie createTarget(ServerLevel level, String label, double x, double y, double z) {
        Zombie target = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (target == null) {
            throw new AssertionError("Expected to create combat range target " + label + ".");
        }
        target.setPos(x, y, z);
        target.setCustomName(Component.literal(label));
        target.setCustomNameVisible(true);
        level.addFreshEntity(target);
        return target;
    }

    private static Zombie findTarget(ServerLevel level, String label) {
        return level.getEntitiesOfClass(Zombie.class, new net.minecraft.world.phys.AABB(-10, ARENA_Y - 2, -10, 15, ARENA_Y + 4, 10))
                .stream()
                .filter(target -> target.getCustomName() != null && target.getCustomName().getString().equals(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected combat range target " + label + "."));
    }

    private static void restoreRules(MinecraftServer server) {
        GameRules rules = server.overworld().getGameRules();
        rules.set(ModGameRules.DISABLE_IFRAMES, false, server);
        rules.set(ModGameRules.INSTANT_SHOOT, false, server);
    }
}
