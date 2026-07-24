package io.github.brainage04.accelerateddamage.gamerule;

import io.github.brainage04.accelerateddamage.AcceleratedDamage;
import io.github.brainage04.accelerateddamage.util.ServerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

import java.util.List;

public final class ModGameRules {
    private static final int VANILLA_TRIDENT_THROW_THRESHOLD = TridentItem.THROW_THRESHOLD_TIME;

    public static final GameRule<Boolean> DISABLE_IFRAMES = register("disable_i_frames");
    public static final GameRule<Boolean> FASTER_EFFECT_DAMAGE_TICKING = register("faster_effect_damage_ticking");
    public static final GameRule<Boolean> DISABLE_ATTACK_COOLDOWN = register("disable_attack_cooldown");
    public static final GameRule<Boolean> INSTANT_SHOOT = register("instant_shoot");

    public static final List<GameRule<Boolean>> REGISTERED = List.of(
            DISABLE_IFRAMES,
            FASTER_EFFECT_DAMAGE_TICKING,
            DISABLE_ATTACK_COOLDOWN,
            INSTANT_SHOOT
    );

    private ModGameRules() {
    }

    private static GameRule<Boolean> register(String path) {
        return GameRuleBuilder.forBoolean(false)
                .category(GameRuleCategory.PLAYER)
                .buildAndRegister(Identifier.fromNamespaceAndPath(AcceleratedDamage.MOD_ID, path));
    }

    public static void initialize() {
        GameRuleEvents.changeCallback(DISABLE_ATTACK_COOLDOWN).register((enabled, server) ->
                updateAttackCooldowns(server, enabled));
        GameRuleEvents.changeCallback(INSTANT_SHOOT).register((enabled, server) ->
                updateTridentThreshold(enabled));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                updateAttackCooldown(
                        handler.player,
                        server.getGameRules().get(DISABLE_ATTACK_COOLDOWN)
                ));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerContext.set(server);
            updateTridentThreshold(server.getGameRules().get(INSTANT_SHOOT));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            updateTridentThreshold(false);
            ServerContext.clear(server);
        });
    }

    private static void updateAttackCooldowns(MinecraftServer server, boolean disabled) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            updateAttackCooldown(player, disabled);
        }
    }

    private static void updateAttackCooldown(ServerPlayer player, boolean disabled) {
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed == null) {
            return;
        }
        if (disabled) {
            attackSpeed.setBaseValue(24.0);
        } else {
            player.getAttributes().resetBaseValue(Attributes.ATTACK_SPEED);
        }
    }

    private static void updateTridentThreshold(boolean instant) {
        TridentItem.THROW_THRESHOLD_TIME = instant ? 0 : VANILLA_TRIDENT_THROW_THRESHOLD;
    }
}
