package io.github.brainage04.accelerateddamage.neoforge;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.accelerateddamage.AcceleratedDamage;
import io.github.brainage04.accelerateddamage.platform.AcceleratedDamagePlatform;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Mod(AcceleratedDamage.MOD_ID)
public final class AcceleratedDamageNeoForge implements AcceleratedDamagePlatform {
    private final Map<Identifier, GameRule<Boolean>> gameRules = new LinkedHashMap<>();

    public AcceleratedDamageNeoForge(IEventBus modBus) {
        // Game rules are created now and registered when NeoForge opens the game rule registry.
        AcceleratedDamage.initialize(this);
        modBus.addListener((RegisterEvent event) -> event.register(Registries.GAME_RULE, helper -> gameRules.forEach(helper::register)));
    }

    @Override
    public GameRule<Boolean> registerBooleanGameRule(String path) {
        GameRule<Boolean> rule = new GameRule<>(
                GameRuleCategory.PLAYER, GameRuleType.BOOL, BoolArgumentType.bool(),
                (visitor, gameRule) -> visitor.visitBoolean(gameRule), Codec.BOOL,
                value -> value ? 1 : 0, false, FeatureFlagSet.of()
        );
        gameRules.put(Identifier.fromNamespaceAndPath(AcceleratedDamage.MOD_ID, path), rule);
        return rule;
    }

    @Override public void registerServerStarted(Consumer<MinecraftServer> callback) { NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> callback.accept(event.getServer())); }
    @Override public void registerServerStopped(Consumer<MinecraftServer> callback) { NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> callback.accept(event.getServer())); }
    @Override public void registerEndServerTick(Consumer<MinecraftServer> callback) { NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> callback.accept(event.getServer())); }
}
