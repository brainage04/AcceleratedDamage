package io.github.brainage04.accelerateddamage.fabric;

import io.github.brainage04.accelerateddamage.AcceleratedDamage;
import io.github.brainage04.accelerateddamage.platform.AcceleratedDamagePlatform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

import java.util.function.Consumer;

public final class AcceleratedDamageFabric implements ModInitializer, AcceleratedDamagePlatform {
    @Override
    public void onInitialize() {
        AcceleratedDamage.initialize(this);
    }

    @Override
    public GameRule<Boolean> registerBooleanGameRule(String path) {
        return GameRuleBuilder.forBoolean(false).category(GameRuleCategory.PLAYER)
                .buildAndRegister(Identifier.fromNamespaceAndPath(AcceleratedDamage.MOD_ID, path));
    }


    @Override public void registerServerStarted(Consumer<MinecraftServer> callback) { ServerLifecycleEvents.SERVER_STARTED.register(callback::accept); }
    @Override public void registerServerStopped(Consumer<MinecraftServer> callback) { ServerLifecycleEvents.SERVER_STOPPED.register(callback::accept); }
    @Override public void registerEndServerTick(Consumer<MinecraftServer> callback) { ServerTickEvents.END_SERVER_TICK.register(callback::accept); }
}
