package io.github.brainage04.accelerateddamage.util;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.accelerateddamage.platform.AcceleratedDamagePlatform;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

public final class EffectSyncTicker {
    private EffectSyncTicker() {}

    public static void initialize(AcceleratedDamagePlatform platform) {
        platform.registerEndServerTick(EffectSyncTicker::syncEffects);
    }

    /// The client counts an effect down once per tick, so a resend every ten server ticks keeps its
    /// copy within one accelerated window of the server's.
    static boolean shouldSync(int tickCount, boolean fasterEffectTicking) {
        return tickCount % 10 == 0 && fasterEffectTicking;
    }

    private static void syncEffects(MinecraftServer server) {
        if (!shouldSync(
                server.getTickCount(),
                server.getGameRules().get(ModGameRules.FASTER_EFFECT_TICKING))) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (MobEffectInstance effect : player.getActiveEffects()) {
                player.connection.send(
                        new ClientboundUpdateMobEffectPacket(player.getId(), effect, true));
            }
        }
    }
}
