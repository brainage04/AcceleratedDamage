package io.github.brainage04.accelerateddamage.gametest;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The player half of the owner's rule of 16 Nov 2026: a player attacks ten times as often. The mod
 * does it with an {@code ATTACK_SPEED} modifier, which is client-syncable ({@code
 * Attributes.java:19-21}, 26.2), so a vanilla client computes the same delay the server does.
 *
 * <p>Every assertion reads the attribute or the packet the server would send, never a copied
 * constant, and the vanilla case is measured with the rule off first.
 */
public final class AcceleratedDamagePlayerAttackSpeedScenarios {
    private AcceleratedDamagePlayerAttackSpeedScenarios() {}

    public static void playerAttackSpeedIsSyncedToTheClient(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();
        ServerPlayer player = null;

        try {
            setRule(rules, server, false);
            player = MockPlayers.spawn(context);
            prepareMockPlayer(player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_SPEAR));
            player.doTick();
            player.doTick();

            float vanillaAttackSpeed = (float) player.getAttributeValue(Attributes.ATTACK_SPEED);
            float vanillaDelay = player.getCurrentItemAttackStrengthDelay();
            assertTrue(
                    vanillaDelay > 2.0F,
                    "Expected a vanilla spear to need more than two ticks to charge");
            assertEquals(
                    vanillaDelay,
                    clientDelay(player),
                    "Expected a vanilla client to compute the server's vanilla delay");

            setRule(rules, server, true);
            player.doTick();

            assertEquals(
                    vanillaAttackSpeed * 10.0F,
                    (float) player.getAttributeValue(Attributes.ATTACK_SPEED),
                    "Expected ten times the vanilla attack speed once the rule is on");
            float acceleratedDelay = player.getCurrentItemAttackStrengthDelay();
            assertEquals(
                    vanillaDelay / 10.0F,
                    acceleratedDelay,
                    "Expected a tenth of the vanilla delay with the rule on");
            assertEquals(
                    acceleratedDelay,
                    clientDelay(player),
                    "Expected the client to compute the server's accelerated delay");

            // A spear carries MINIMUM_ATTACK_CHARGE 1.0 (Item.java:526), so its stab gate is the
            // one melee case where a client keeping the vanilla delay changes what the player can
            // do (Minecraft.java:1627).
            ItemStack spear = player.getMainHandItem();
            assertEquals(
                    1.0F,
                    spear.getOrDefault(DataComponents.MINIMUM_ATTACK_CHARGE, 0.0F),
                    "Expected the spear fixture to carry the minimum charge under test");
            player.resetAttackStrengthTicker();
            player.doTick();
            player.doTick();
            player.doTick();
            assertTrue(
                    !player.cannotAttackWithItem(spear, 0),
                    "Expected an accelerated spear to be stabable after three ticks");

            // The tooltip's attack-speed line is the item's own modifier on the base value
            // (ItemAttributeModifiers.java:169-173, 26.2), not the player's total, so the rule
            // must not change it.
            List<Component> acceleratedTooltip = tooltip(level, player);
            assertTrue(!acceleratedTooltip.isEmpty(), "Expected a non-empty weapon tooltip");

            setRule(rules, server, false);
            player.doTick();
            assertEquals(
                    vanillaAttackSpeed,
                    (float) player.getAttributeValue(Attributes.ATTACK_SPEED),
                    "Expected the vanilla attack speed back with the rule off");
            assertEquals(
                    vanillaDelay,
                    player.getCurrentItemAttackStrengthDelay(),
                    "Expected the vanilla delay back with the rule off");
            assertTrue(
                    tooltip(level, player).equals(acceleratedTooltip),
                    "Expected the item tooltip to stay the same with the rule on and off");

            player.resetAttackStrengthTicker();
            player.doTick();
            player.doTick();
            player.doTick();
            assertTrue(
                    player.cannotAttackWithItem(spear, 0),
                    "Expected a vanilla spear to refuse the stab after three ticks");
        } finally {
            setRule(rules, server, false);
            removePlayer(server, player);
        }

        context.succeed();
    }

    /**
     * A player that joins, respawns or changes dimension keeps the accelerated attack speed,
     * because the mod re-adds it on the player's first tick in that state.
     */
    public static void playerAttackSpeedSurvivesJoinRespawnAndDimensionChange(
            GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();
        ServerPlayer player = null;
        ServerPlayer respawned = null;

        try {
            setRule(rules, server, false);
            player = MockPlayers.spawn(context);
            prepareMockPlayer(player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            player.doTick();
            player.doTick();
            float vanillaDelay = player.getCurrentItemAttackStrengthDelay();
            float vanillaAttackSpeed = (float) player.getAttributeValue(Attributes.ATTACK_SPEED);

            // Joining while the rule is on: a player's own attributes reach its client through
            // sendDirtyEntityData (ServerEntity.java:345-355, 26.2), which runs once per tick.
            setRule(rules, server, true);
            player.doTick();
            assertAccelerated(
                    player,
                    vanillaAttackSpeed,
                    vanillaDelay,
                    "Expected a player joining with the rule on to get the accelerated delay");

            // Respawn builds a new ServerPlayer and copies only base values and permanent modifiers
            // (ServerPlayer.java:1606-1608), so the transient modifier is not carried over.
            respawned = server.getPlayerList().respawn(player, false, RemovalReason.KILLED);
            assertTrue(respawned != null, "Expected the respawn to return a player");
            // restoreFrom copies base values and permanent modifiers only, so the transient
            // modifier is gone until the first tick adds it back; the attribute reads the
            // respawned player's own base value, not the accelerated one.
            assertTrue(
                    Float.compare(
                                    (float) respawned.getAttributeValue(Attributes.ATTACK_SPEED),
                                    vanillaAttackSpeed * 10.0F)
                            != 0,
                    "Expected the transient attack speed not to survive the respawn copy");
            respawned.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            respawned.doTick();
            respawned.doTick();
            assertAccelerated(
                    respawned,
                    vanillaAttackSpeed,
                    vanillaDelay,
                    "Expected the accelerated delay back on the first tick after a respawn");

            // A dimension change runs setServerLevel (ServerPlayer.java:1132, :2037-2040) and keeps
            // the player's attribute map, so the modifier survives; the rule is read from the new
            // level, and gamerules belong to the server anyway (ServerLevel.java:1839-1841).
            ServerLevel nether = server.getLevel(Level.NETHER);
            assertTrue(nether != null, "Expected the test server to have a Nether level");
            respawned.setServerLevel(nether);
            respawned.doTick();
            assertAccelerated(
                    respawned,
                    vanillaAttackSpeed,
                    vanillaDelay,
                    "Expected the accelerated delay to survive a dimension change");
        } finally {
            setRule(rules, server, false);
            removePlayer(server, respawned);
            removePlayer(server, player);
        }

        context.succeed();
    }

    private static void prepareMockPlayer(ServerPlayer player) {
        // The mock player is CREATIVE (GameType.java:62-66, 26.2), which makes it invulnerable, and
        // its connection still waits for the client to load (ServerPlayer.java:1279-1280), which
        // makes it immune to every source until the 60-tick timeout runs out
        // (ServerGamePacketListenerImpl.java:2214-2216).
        player.getAbilities().invulnerable = false;
        player.setInvulnerable(false);
        for (int tick = 0; tick < 60; tick++) {
            player.connection.tickClientLoadTimeout();
        }
        player.setGameMode(GameType.SURVIVAL);
    }

    private static List<Component> tooltip(ServerLevel level, Player player) {
        return new ItemStack(Items.IRON_SWORD)
                .getTooltipLines(Item.TooltipContext.of(level), player, TooltipFlag.NORMAL);
    }

    /**
     * The delay a vanilla client computes after it applies the attribute snapshots the server sends
     * for the player ({@code ClientPacketListener.java:2350-2363}, 26.2). The stand-in is a mock
     * {@link Player} whose attribute map starts from the same supplier a client's own player does,
     * so the only thing that can make it differ from the server is the packet's contents.
     */
    private static float clientDelay(ServerPlayer serverPlayer) {
        Player standIn =
                new Player(serverPlayer.level(), serverPlayer.getGameProfile()) {
                    @Override
                    public GameType gameMode() {
                        return GameType.SURVIVAL;
                    }
                };
        ClientboundUpdateAttributesPacket packet =
                new ClientboundUpdateAttributesPacket(
                        serverPlayer.getId(), serverPlayer.getAttributes().getSyncableAttributes());
        AttributeMap attributes = standIn.getAttributes();
        for (ClientboundUpdateAttributesPacket.AttributeSnapshot snapshot : packet.getValues()) {
            AttributeInstance instance = attributes.getInstance(snapshot.attribute());
            instance.setBaseValue(snapshot.base());
            instance.removeModifiers();
            for (AttributeModifier modifier : snapshot.modifiers()) {
                instance.addTransientModifier(modifier);
            }
        }
        return standIn.getCurrentItemAttackStrengthDelay();
    }

    private static void assertAccelerated(
            ServerPlayer player, float vanillaAttackSpeed, float vanillaDelay, String message) {
        assertTrue(
                Float.compare(
                                (float) player.getAttributeValue(Attributes.ATTACK_SPEED),
                                vanillaAttackSpeed * 10.0F)
                        == 0,
                message + ": the attack speed attribute is not ten times its vanilla value");
        assertEquals(vanillaDelay / 10.0F, clientDelay(player), message);
    }

    private static void setRule(GameRules rules, MinecraftServer server, boolean enabled) {
        rules.set(ModGameRules.FASTER_EFFECT_TICKING, enabled, server);
    }

    private static void removePlayer(MinecraftServer server, ServerPlayer player) {
        if (player != null && server.getPlayerList().getPlayer(player.getUUID()) != null) {
            server.getPlayerList().remove(player);
        }
    }

    private static void assertEquals(float expected, float actual, String message) {
        if (Float.compare(expected, actual) != 0) {
            throw new GameTestAssertException(
                    Component.literal(message + ": expected " + expected + ", found " + actual), 0);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(Component.literal(message), 0);
        }
    }
}
