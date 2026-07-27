package io.github.brainage04.accelerateddamage.gamerule;

import io.github.brainage04.accelerateddamage.platform.AcceleratedDamagePlatform;
import io.github.brainage04.accelerateddamage.util.ServerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.gamerules.GameRule;

import java.util.List;

public final class ModGameRules {
    private static final int VANILLA_TRIDENT_THROW_THRESHOLD = TridentItem.THROW_THRESHOLD_TIME;

    public static GameRule<Boolean> DISABLE_IFRAMES;
    public static GameRule<Boolean> FASTER_EFFECT_DAMAGE_TICKING;
    public static GameRule<Boolean> DISABLE_ATTACK_COOLDOWN;
    public static GameRule<Boolean> INSTANT_SHOOT;

    public static List<GameRule<Boolean>> REGISTERED;

    private ModGameRules() {
    }

    public static void initialize(AcceleratedDamagePlatform platform) {
        DISABLE_IFRAMES = platform.registerBooleanGameRule("disable_i_frames");
        FASTER_EFFECT_DAMAGE_TICKING = platform.registerBooleanGameRule("faster_effect_damage_ticking");
        DISABLE_ATTACK_COOLDOWN = platform.registerBooleanGameRule("disable_attack_cooldown");
        INSTANT_SHOOT = platform.registerBooleanGameRule("instant_shoot");
        REGISTERED = List.of(DISABLE_IFRAMES, FASTER_EFFECT_DAMAGE_TICKING, DISABLE_ATTACK_COOLDOWN, INSTANT_SHOOT);

        platform.registerGameRuleChange(DISABLE_ATTACK_COOLDOWN,
                (disabled, server) -> updateAttackCooldowns(server, disabled));
        platform.registerGameRuleChange(INSTANT_SHOOT, (enabled, server) -> updateTridentThreshold(enabled));
        platform.registerPlayerJoin((player, server) ->
                updateAttackCooldown(player, server.getGameRules().get(DISABLE_ATTACK_COOLDOWN)));
        platform.registerServerStarted(server -> {
            ServerContext.set(server);
            updateTridentThreshold(server.getGameRules().get(INSTANT_SHOOT));
        });
        platform.registerServerStopped(server -> {
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
