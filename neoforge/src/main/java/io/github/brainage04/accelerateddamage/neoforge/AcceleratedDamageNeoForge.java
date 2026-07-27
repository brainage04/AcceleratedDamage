package io.github.brainage04.accelerateddamage.neoforge;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.accelerateddamage.AcceleratedDamage;
import io.github.brainage04.accelerateddamage.platform.AcceleratedDamagePlatform;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.GameRuleChangedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Mod(AcceleratedDamage.MOD_ID)
public final class AcceleratedDamageNeoForge implements AcceleratedDamagePlatform {
    private static final DeferredRegister<GameRule<?>> GAME_RULES = DeferredRegister.create(Registries.GAME_RULE, AcceleratedDamage.MOD_ID);
    private static final DeferredHolder<GameRule<?>, GameRule<Boolean>> DISABLE_IFRAMES = registerBoolean("disable_i_frames");
    private static final DeferredHolder<GameRule<?>, GameRule<Boolean>> FASTER_EFFECT_DAMAGE_TICKING = registerBoolean("faster_effect_damage_ticking");
    private static final DeferredHolder<GameRule<?>, GameRule<Boolean>> DISABLE_ATTACK_COOLDOWN = registerBoolean("disable_attack_cooldown");
    private static final DeferredHolder<GameRule<?>, GameRule<Boolean>> INSTANT_SHOOT = registerBoolean("instant_shoot");

    public AcceleratedDamageNeoForge(IEventBus modBus) {
        GAME_RULES.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> AcceleratedDamage.initialize(this));
    }

    private static DeferredHolder<GameRule<?>, GameRule<Boolean>> registerBoolean(String path) {
        return GAME_RULES.register(path, () -> new GameRule<>(
                GameRuleCategory.PLAYER, GameRuleType.BOOL, BoolArgumentType.bool(),
                (visitor, rule) -> visitor.visitBoolean(rule), Codec.BOOL,
                value -> value ? 1 : 0, false, FeatureFlagSet.of()
        ));
    }

    @Override
    public GameRule<Boolean> registerBooleanGameRule(String path) {
        return switch (path) {
            case "disable_i_frames" -> DISABLE_IFRAMES.get();
            case "faster_effect_damage_ticking" -> FASTER_EFFECT_DAMAGE_TICKING.get();
            case "disable_attack_cooldown" -> DISABLE_ATTACK_COOLDOWN.get();
            case "instant_shoot" -> INSTANT_SHOOT.get();
            default -> throw new IllegalArgumentException("Unknown Accelerated Damage gamerule: " + path);
        };
    }

    @Override
    public void registerGameRuleChange(GameRule<Boolean> rule, BiConsumer<Boolean, MinecraftServer> callback) {
        NeoForge.EVENT_BUS.addListener((GameRuleChangedEvent event) ->
                event.runIfMatching(rule, value -> callback.accept(value, event.getServer())));
    }

    @Override
    public void registerPlayerJoin(BiConsumer<ServerPlayer, MinecraftServer> callback) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) callback.accept(player, player.level().getServer());
        });
    }

    @Override public void registerServerStarted(Consumer<MinecraftServer> callback) { NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> callback.accept(event.getServer())); }
    @Override public void registerServerStopped(Consumer<MinecraftServer> callback) { NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> callback.accept(event.getServer())); }
    @Override public void registerEndServerTick(Consumer<MinecraftServer> callback) { NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> callback.accept(event.getServer())); }
}
