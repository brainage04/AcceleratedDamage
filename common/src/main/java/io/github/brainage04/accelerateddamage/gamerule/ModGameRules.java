package io.github.brainage04.accelerateddamage.gamerule;

import io.github.brainage04.accelerateddamage.platform.AcceleratedDamagePlatform;
import io.github.brainage04.accelerateddamage.util.ServerContext;
import net.minecraft.world.level.gamerules.GameRule;

import java.util.List;

public final class ModGameRules {


    public static GameRule<Boolean> DISABLE_IFRAMES;
    public static GameRule<Boolean> FASTER_EFFECT_TICKING;
    public static GameRule<Boolean> DISABLE_ATTACK_COOLDOWN;
    public static GameRule<Boolean> INSTANT_SHOOT;

    public static List<GameRule<Boolean>> REGISTERED;

    private ModGameRules() {
    }

    public static void initialize(AcceleratedDamagePlatform platform) {
        DISABLE_IFRAMES = platform.registerBooleanGameRule("disable_i_frames");
        FASTER_EFFECT_TICKING = platform.registerBooleanGameRule("faster_effect_ticking");
        DISABLE_ATTACK_COOLDOWN = platform.registerBooleanGameRule("disable_attack_cooldown");
        INSTANT_SHOOT = platform.registerBooleanGameRule("instant_shoot");
        REGISTERED = List.of(DISABLE_IFRAMES, FASTER_EFFECT_TICKING, DISABLE_ATTACK_COOLDOWN, INSTANT_SHOOT);

        platform.registerServerStarted(ServerContext::set);
        platform.registerServerStopped(ServerContext::clear);
    }

}
