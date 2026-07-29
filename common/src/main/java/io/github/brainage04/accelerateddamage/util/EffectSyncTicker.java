package io.github.brainage04.accelerateddamage.util;

import io.github.brainage04.accelerateddamage.platform.AcceleratedDamagePlatform;
import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

public final class EffectSyncTicker {
    private EffectSyncTicker() {
    }

    public static void initialize(AcceleratedDamagePlatform platform) {
        platform.registerEndServerTick(EffectSyncTicker::syncEffects);
    }

    private static void syncEffects(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0
                || !server.getGameRules().get(ModGameRules.FASTER_EFFECT_DAMAGE_TICKING)) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (MobEffectInstance effect : player.getActiveEffects()) {
                player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), effect, true));
            }
        }
    }
}
