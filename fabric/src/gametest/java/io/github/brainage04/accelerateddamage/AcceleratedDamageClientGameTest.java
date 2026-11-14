package io.github.brainage04.accelerateddamage;

import com.mojang.authlib.GameProfile;
import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.accelerateddamage.util.VirtualTime;
import io.github.brainage04.minecraftmodconventions.ClientGameTestCommands;
import io.github.brainage04.minecraftmodconventions.ClientGameTestPlayerActions;
import io.github.brainage04.minecraftmodconventions.ClientGameTestRecorder;
import io.github.brainage04.minecraftmodconventions.ClientGameTestServers;
import io.github.brainage04.minecraftmodconventions.ClientGameTestStages;
import io.github.brainage04.minecraftmodconventions.ClientGameTestWaits;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/// The showcase the owner asked for: every rule, filmed against player-shaped targets with the
/// target's health printed in the caption after every hit, so a hit landing is visible.
///
/// <p>Every scenario runs twice in the same scene with the same inputs: about two seconds with the
/// scenario's rules off, then about two seconds with them on. Weapons hit every two game ticks with
/// the real attack or use key, and the server writes down the game tick of every attack and shot
/// and the target's health before and after every hit. A half fails when two inputs are more than
/// five ticks apart, when a hit with the rules on deals no damage, or when, with the rules off, a
/// hit inside vanilla's damage cooldown lands in full.
///
/// <p>Player-shaped targets are {@link ServerPlayer}s, and a {@link ServerPlayer} only runs the
/// {@code Player}/{@code LivingEntity}/{@code Entity} half of its tick from {@code doTick()}, which
/// a real client's connection calls. A fake client's {@code EmbeddedChannel} never ticks that
/// connection, so a target would otherwise never tick its fire, freeze, effects, air supply or
/// hunger. {@link #tickTrackedTargets(MinecraftServer)} calls {@code doTick()} on every target each
/// server tick, which is the call vanilla makes for a real player.
///
/// <p>Every on-camera action goes through {@link ClientGameTestPlayerActions} and the real key
/// bindings. The camera faces each scenario's target from its first frame: the teleport into the
/// scene carries the view, and nothing turns the camera inside a scenario.
@SuppressWarnings("UnstableApiUsage")
public final class AcceleratedDamageClientGameTest implements FabricClientGameTest {
    private static final int ARENA_Y = 64;
    private static final double RANGE_X = -6.5D;
    private static final Vec3 MELEE_STAND = new Vec3(0.5, ARENA_Y, 1.5);
    private static final Vec3 MELEE_TARGET = new Vec3(0.5, ARENA_Y, 4.5);
    private static final Vec3 RANGE_STAND = new Vec3(RANGE_X, ARENA_Y, 0.5);
    private static final Vec3 RANGE_TARGET = new Vec3(RANGE_X, ARENA_Y, 4.5);
    private static final Vec3 FIREWORK_TARGET = new Vec3(RANGE_X, ARENA_Y, 8.5);
    private static final Vec3 DROWNING_STAND = new Vec3(9.5, ARENA_Y, -6.5);
    private static final Vec3 DROWNING_VIEW = new Vec3(9.5, ARENA_Y + 0.6, -3.2);
    private static final Vec3 EFFECTS_STAND = new Vec3(0.0, ARENA_Y, -2.5);
    private static final Vec3 HUNGER_STAND = new Vec3(0.0, ARENA_Y, -34.5);

    /// A splash reaches every living entity within four blocks of where it breaks, the thrower
    /// included (ThrownSplashPotion.java:46-55 in the 26.3 sources), and a potion thrown at 0.5
    /// blocks a tick (ThrowablePotionItem.java:26) under a gravity of 0.05 a tick
    /// (AbstractThrownPotion.java:45-47) breaks only about five blocks out. On the floor the
    /// thrower would stand at the edge of its own splash, so it throws from a pillar three blocks
    /// high, out of the splash's reach. The pillar stands east of the arena, behind or beside the
    /// camera of every scene filmed on dry land.
    private static final Vec3 SPLASH_STAND = new Vec3(14.5, ARENA_Y + 3, 0.5);

    private static final Vec3 SPLASH_TARGET = new Vec3(9.5, ARENA_Y, 0.5);

    /// A weapon target survives both halves of its scenario, so no death message lands in the film.
    private static final float WEAPON_TARGET_HEALTH = 200.0F;

    /// An effect target is healthy enough to keep showing numbers for the whole scenario.
    private static final float EFFECT_TARGET_HEALTH = 300.0F;

    /// Effect targets face +Z, towards the camera south of their row.
    private static final float EFFECT_TARGET_YAW = 0.0F;

    private static final int READING_TICKS = 14;

    /// Regeneration II, the regeneration of a strong potion of regeneration (Potions.java:87-88 in
    /// the 26.3 sources).
    private static final int REGENERATION_AMPLIFIER = 1;

    /// Long enough to outlast a half with the rules on, which burn ten ticks of it every tick.
    private static final int REGENERATION_TICKS = 1200;

    /// Rule 5 of the recording rules: a hit at least every five ticks, ideally every two.
    private static final int INPUT_CADENCE_TICKS = 2;

    /// A bow or trident is drawn on one tick and released on the next. The server's synced "using
    /// an item" flag for that draw reaches the client a tick after the release and starts the use
    /// again on the client (LocalPlayer.java:596-606 in the 26.3 sources), which swallows a use
    /// press in that tick. The next draw therefore starts a tick later, three ticks apart.
    private static final int DRAW_CADENCE_TICKS = 3;

    private static final int MAX_INPUT_GAP_TICKS = 5;

    /// Vanilla's damage cooldown: a full hit sets it to 20, and while it is above 10 a later hit
    /// only deals what exceeds the last one (LivingEntity.java:1219-1232 in the 26.3 sources).
    private static final int VANILLA_WINDOW_TICKS = 10;

    private static final int MELEE_HITS = 16;
    private static final int BOW_SHOTS = 14;
    private static final int HOTBAR_SHOTS = 9;

    /// A filmed half lasts about two seconds.
    private static final int HALF_TICKS = 40;

    /// Where an off-stage dummy waits: far outside the recording player's view distance, so a dummy
    /// placed in a scene is a new entity on the client, its body built facing the rotation sent
    /// with it (LivingEntity.recreateFromPacket, LivingEntity.java:4009 in the 26.3 sources; see
    /// createDummy). A dummy moved while in view keeps the body turn of that move instead
    /// (LivingEntity.java:2791-2812), so it would face away from the camera or stand sideways.
    private static final Vec3 PARKING = new Vec3(0.5, 200.0, 400.5);

    /// Ticks a scene's targets are in place before its marker, so flames, effect particles and the
    /// poison cloud are already drawn on the stage's first frame.
    private static final int SCENE_SETTLE_TICKS = 10;

    /// How far a player-shaped target's body and head may turn from its server yaw.
    private static final float YAW_TOLERANCE_DEGREES = 10.0F;

    /// How far a target's yaw may point from the camera: the outer targets of a row stand up to 42
    /// degrees off the camera's line of sight.
    private static final float CAMERA_TOLERANCE_DEGREES = 50.0F;

    /// Every dummy is created before the client joins, so no join line can ever reach a filmed
    /// frame. Each one always faces the same way, parked or shown: the weapon dummies face the
    /// player north of them, the splash dummy faces the thrower east of it, and the effect dummies
    /// face the camera south of them.
    private static final Map<String, Float> DUMMY_YAWS = dummyYaws();

    private static Map<String, Float> dummyYaws() {
        Map<String, Float> yaws = new LinkedHashMap<>();
        for (String name :
                List.of(
                        "MeleeDummy",
                        "BowDummy",
                        "CrossbowDummy",
                        "FireworkDummy",
                        "TridentDummy")) {
            yaws.put(name, 180.0F);
        }
        yaws.put("SplashDummy", -90.0F);
        for (String name :
                List.of(
                        "Fire",
                        "Lava",
                        "Poison",
                        "Wither",
                        "Freezing",
                        "Cactus",
                        "Starving",
                        "Regeneration",
                        "Saturated",
                        "PoisonCloud")) {
            yaws.put(name, EFFECT_TARGET_YAW);
        }
        return yaws;
    }

    private static final Map<String, Integer> DUMMIES = new LinkedHashMap<>();

    private static final Map<UUID, TrackedTarget> TRACKED = new LinkedHashMap<>();
    private static boolean listenersRegistered;

    /// The game tick the server is on, set at the start of every server tick.
    private static volatile int serverTick;

    /// The log of the half being filmed, or {@code null} between halves. Written on the server
    /// thread only.
    private static volatile HitLog hitLog;

    /// Targets whose food bar and saturation are topped up before every tick of theirs, so natural
    /// regeneration from saturation never runs dry inside a half. Server thread only.
    private static final Set<Integer> FED = new HashSet<>();

    /// The heal log of every healing target in the half being filmed, by entity id. Server thread
    /// only.
    private static final Map<Integer, HealLog> HEAL_LOGS = new LinkedHashMap<>();

    /// A fake player the showcase keeps ticking, with the position to hold it at (or {@code null}
    /// to let knockback move it) and the text its name tag shows.
    private record TrackedTarget(UUID id, String name, Vec3 anchor) {}

    /// The server's record of one healing target in one filmed half: every tick on which the
    /// target's own tick raised its health, and the health it gained in all.
    private static final class HealLog {
        private final int openTick;
        private final List<Integer> healTicks = new ArrayList<>();
        private float healed;
        private int closeTick;

        private HealLog(int openTick) {
            this.openTick = openTick;
        }

        private int ticks() {
            return closeTick - openTick;
        }

        private float healPerStep() {
            return healTicks.isEmpty() ? 0.0F : healed / healTicks.size();
        }

        /// Heals per tick, from the ticks between the half's first and last heal.
        private double stepsPerTick() {
            if (healTicks.size() < 2) {
                throw new AssertionError(
                        "Expected at least two heals to measure a cadence: " + this);
            }
            return (healTicks.size() - 1) / (double) (healTicks.getLast() - healTicks.getFirst());
        }

        @Override
        public String toString() {
            return String.format(
                    Locale.ROOT,
                    "healed=%.1f ticks=%d healTicks=%s",
                    healed,
                    ticks(),
                    healTicks.stream().map(tick -> tick - openTick).toList());
        }
    }

    /// One hit that reached the target: the game tick, and the target's health before the hit and
    /// before whatever reached it next.
    private static final class Hit {
        private final int tick;
        private final float before;
        private float after;

        private Hit(int tick, float before) {
            this.tick = tick;
            this.before = before;
            this.after = before;
        }

        private float damage() {
            return before - after;
        }
    }

    /// The server's record of one filmed half: the game tick of every attack and every projectile
    /// of the recorded player, and every hit on the target.
    private static final class HitLog {
        private final String stage;
        private final int playerId;
        private final int targetId;
        private final int openTick;
        private final List<Integer> inputTicks = new ArrayList<>();
        private final List<Hit> hits = new ArrayList<>();
        private int closeTick;

        private HitLog(String stage, int playerId, int targetId, int openTick) {
            this.stage = stage;
            this.playerId = playerId;
            this.targetId = targetId;
            this.openTick = openTick;
        }

        private void hit(int tick, float health) {
            finishLastHit(health);
            hits.add(new Hit(tick, health));
        }

        private void close(int tick, float health) {
            finishLastHit(health);
            closeTick = tick;
        }

        private void finishLastHit(float health) {
            if (!hits.isEmpty()) {
                hits.getLast().after = health;
            }
        }

        private int minGap() {
            int min = Integer.MAX_VALUE;
            for (int index = 1; index < inputTicks.size(); index++) {
                min = Math.min(min, inputTicks.get(index) - inputTicks.get(index - 1));
            }
            return min;
        }

        private int maxGap() {
            int max = 0;
            for (int index = 1; index < inputTicks.size(); index++) {
                max = Math.max(max, inputTicks.get(index) - inputTicks.get(index - 1));
            }
            return max;
        }

        private int landed() {
            return (int) hits.stream().filter(hit -> hit.damage() > 0.0F).count();
        }

        /// One line the take's log keeps, for the cadence table of the report.
        private String describe() {
            StringBuilder line = new StringBuilder("AD_TICKLOG ");
            line.append(stage)
                    .append(" inputs=")
                    .append(inputTicks.size())
                    .append(" minGap=")
                    .append(minGap())
                    .append(" maxGap=")
                    .append(maxGap())
                    .append(" hits=")
                    .append(hits.size())
                    .append(" landed=")
                    .append(landed())
                    .append(" inputTicks=")
                    .append(inputTicks)
                    .append(" hitTicks=[");
            for (int index = 0; index < hits.size(); index++) {
                Hit hit = hits.get(index);
                if (index > 0) {
                    line.append(", ");
                }
                line.append(hit.tick)
                        .append(':')
                        .append(health(hit.before))
                        .append('>')
                        .append(health(hit.after));
            }
            return line.append(']').toString();
        }
    }

    /// Puts one effect scenario's targets in place for one half, with fresh health and effects.
    /// {@code previous} holds the ids of the first half's targets, or is empty in the first half.
    @FunctionalInterface
    private interface EffectSetup {
        List<Integer> place(MinecraftServer server, List<Integer> previous);
    }

    /// What a scene starts at its marker: the timers its curve is measured from.
    @FunctionalInterface
    private interface EffectStart {
        void begin(MinecraftServer server, List<Integer> targets);
    }

    /// One effect scenario: its targets' names in caption order, how many of those names the
    /// caption's first line holds in place of the subject (0 when one line holds every name), which
    /// of them heal instead of taking damage, with each healer's vanilla heals per tick read from
    /// its rules-off half, and which of them vanilla may hurt by at most four health in a half.
    private record EffectScene(
            String id,
            String subject,
            Vec3 stand,
            Vec3 view,
            List<String> names,
            int titleNames,
            Map<String, ToDoubleFunction<HealLog>> healers,
            Set<String> slowInVanilla,
            EffectSetup setup,
            EffectStart start) {}

    /// A target the client must show in place: player-shaped ones also face the camera.
    private record ShotTarget(int id, String label, Vec3 position, float yaw, boolean faces) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        registerListeners();
        Properties serverProperties = ClientGameTestServers.flatServerProperties();
        // Fabric's test server allows one player (DedicatedServerImplUtil sets max-players to 1),
        // and every dummy joins before the recorded client, which the full server would refuse.
        serverProperties.setProperty("max-players", "40");
        // ClientGameTestServers.withDedicatedServer starts the recording before the world exists
        // and stops it after the world is gone, so this test never starts or stops it itself. The
        // arena is built in its beforeJoin hook, so no frame shows the world being built.
        ClientGameTestServers.withDedicatedServer(
                context,
                serverProperties,
                "Accelerated Damage complete showcase GameTest",
                server -> server.runOnServer(AcceleratedDamageClientGameTest::buildArena),
                server -> {
                    int playerId;
                    try {
                        playerId =
                                server.computeOnServer(
                                        current -> {
                                            preparePlayer(current);
                                            return recordingPlayerId(current);
                                        });
                        ClientGameTestServers.assertClientWorldAndPlayerAvailable(context);
                        context.waitTicks(20);
                        // The title card covers the tour that loads every viewpoint, so the first
                        // scenario never opens on an unloaded chunk.
                        ClientGameTestRecorder.showStep(
                                context,
                                "showcase.ready",
                                "Accelerated Damage",
                                "Four rules, every hit and every effect");
                        preloadArena(context, server, playerId);
                        context.waitTicks(10);

                        recordMelee(context, server, playerId);
                        recordDrowning(context, server, playerId);
                        recordBow(context, server, playerId);
                        recordLoadedHotbar(
                                context,
                                server,
                                playerId,
                                "crossbow",
                                "Nine loaded crossbows",
                                "CrossbowDummy",
                                RANGE_TARGET,
                                RANGE_STAND,
                                AcceleratedDamageClientGameTest::chest,
                                slot -> loadedCrossbow(new ItemStack(Items.ARROW)),
                                INPUT_CADENCE_TICKS);
                        recordLoadedHotbar(
                                context,
                                server,
                                playerId,
                                "firework",
                                "Nine crossbows of rockets",
                                "FireworkDummy",
                                FIREWORK_TARGET,
                                RANGE_STAND,
                                AcceleratedDamageClientGameTest::chest,
                                slot -> loadedCrossbow(fireworkRocket()),
                                INPUT_CADENCE_TICKS);
                        recordLoadedHotbar(
                                context,
                                server,
                                playerId,
                                "trident",
                                "A trident every 3 ticks",
                                "TridentDummy",
                                RANGE_TARGET,
                                RANGE_STAND,
                                AcceleratedDamageClientGameTest::chest,
                                slot -> new ItemStack(Items.TRIDENT),
                                DRAW_CADENCE_TICKS);
                        recordEffectScene(context, server, playerId, effectsScene());
                        // Aimed at the target's eyes, the throw's lift of 20 degrees above the
                        // view (ThrowablePotionItem.java:26) brings the potion down on its body.
                        recordLoadedHotbar(
                                context,
                                server,
                                playerId,
                                "splash",
                                "Nine splash potions of harming",
                                "SplashDummy",
                                SPLASH_TARGET,
                                SPLASH_STAND,
                                Entity::getEyePosition,
                                slot ->
                                        PotionContents.createItemStack(
                                                Items.SPLASH_POTION, Potions.HARMING),
                                INPUT_CADENCE_TICKS);
                        recordEffectScene(context, server, playerId, hungerEffectsScene());
                        ClientGameTestPlayerActions.requireVanillaInteractionRanges(context);
                        ClientGameTestCommands.clearChat(context);
                        context.waitTicks(4);
                    } finally {
                        TRACKED.clear();
                        server.runOnServer(AcceleratedDamageClientGameTest::restoreState);
                    }
                });
    }

    /// Visits every viewpoint before the film starts, so a scenario never opens on a chunk the
    /// client has not built yet. The recorder keeps running from the first frame, so a teleport
    /// later costs only a few ticks.
    private static void preloadArena(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        List<Vec3> viewpoints =
                List.of(EFFECTS_STAND, SPLASH_STAND, HUNGER_STAND, DROWNING_STAND, RANGE_STAND);
        for (Vec3 viewpoint : viewpoints) {
            server.runOnServer(
                    current ->
                            player(current, playerId)
                                    .teleportTo(
                                            current.overworld(),
                                            viewpoint.x,
                                            viewpoint.y,
                                            viewpoint.z,
                                            Set.of(),
                                            180.0F,
                                            0.0F,
                                            false));
            context.waitTicks(6);
            ClientGameTestWaits.waitForChunkWindow(context, 3, 600);
            ClientGameTestWaits.waitForRenderSettled(context, 4, 600);
        }
    }

    private static void registerListeners() {
        if (listenersRegistered) {
            return;
        }
        ServerTickEvents.START_SERVER_TICK.register(
                AcceleratedDamageClientGameTest::tickTrackedTargets);
        AttackEntityCallback.EVENT.register(
                (player, level, hand, entity, hitResult) -> {
                    HitLog log = hitLog;
                    if (log != null
                            && !level.isClientSide()
                            && player.getId() == log.playerId
                            && entity.getId() == log.targetId) {
                        log.inputTicks.add(serverTick);
                    }
                    return InteractionResult.PASS;
                });
        ServerEntityEvents.ENTITY_LOAD.register(
                (entity, level) -> {
                    HitLog log = hitLog;
                    if (log != null
                            && entity instanceof Projectile projectile
                            && projectile.getOwner() != null
                            && projectile.getOwner().getId() == log.playerId) {
                        log.inputTicks.add(serverTick);
                    }
                });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) -> {
                    HitLog log = hitLog;
                    if (log != null && entity.getId() == log.targetId) {
                        log.hit(serverTick, entity.getHealth());
                    }
                    return true;
                });
        listenersRegistered = true;
    }

    /// Runs the half of a player's tick that a real client's connection runs for it.
    private static void tickTrackedTargets(MinecraftServer server) {
        serverTick = server.getTickCount();
        if (TRACKED.isEmpty()) {
            return;
        }
        for (TrackedTarget tracked : List.copyOf(TRACKED.values())) {
            ServerPlayer target = server.getPlayerList().getPlayer(tracked.id());
            if (target == null || target.isRemoved()) {
                TRACKED.remove(tracked.id());
                continue;
            }
            if (FED.contains(target.getId())) {
                target.getFoodData().setFoodLevel(20);
                target.getFoodData().setSaturation(20.0F);
            }
            float before = target.getHealth();
            target.doTick();
            HealLog heals = HEAL_LOGS.get(target.getId());
            if (heals != null && target.getHealth() > before) {
                heals.healTicks.add(serverTick);
                heals.healed += target.getHealth() - before;
            }
            if (tracked.anchor() != null) {
                target.setPos(tracked.anchor().x, tracked.anchor().y, tracked.anchor().z);
            }
            target.setCustomName(
                    Component.literal(
                            tracked.name() + " " + Math.round(Math.max(0.0F, target.getHealth()))));
        }
    }

    /// Builds the whole showcase arena, every scenario's cells included. It runs before the client
    /// joins, so the film starts on a finished set and no scenario builds anything on camera.
    private static void buildArena(MinecraftServer server) {
        ServerLevel level = server.overworld();
        restoreRules(server);
        // Every target is a player, and a server whose PvP game rule is off rejects all
        // player-versus-player damage in ServerPlayer#hurtServer, so the showcase world runs with
        // the rule on.
        level.getGameRules().set(GameRules.PVP, true, server);
        for (int x = -10; x <= 16; x++) {
            for (int z = -46; z <= 10; z++) {
                level.setBlockAndUpdate(
                        new BlockPos(x, ARENA_Y - 1, z),
                        (z & 1) == 0
                                ? Blocks.STONE.defaultBlockState()
                                : Blocks.DEEPSLATE.defaultBlockState());
            }
        }
        for (int x = -10; x <= 16; x++) {
            level.setBlockAndUpdate(
                    new BlockPos(x, ARENA_Y - 1, 0), Blocks.GLOWSTONE.defaultBlockState());
        }
        // A wall behind the melee target, so the scene has a backdrop.
        for (int x = -4; x <= 4; x++) {
            for (int y = ARENA_Y; y <= ARENA_Y + 2; y++) {
                level.setBlockAndUpdate(
                        new BlockPos(x, y, 5), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        // The splash thrower's pillar.
        for (int y = ARENA_Y; y < SPLASH_STAND.y; y++) {
            setBlock(
                    level,
                    Mth.floor(SPLASH_STAND.x),
                    y,
                    Mth.floor(SPLASH_STAND.z),
                    Blocks.STONE_BRICKS);
        }
        // The drowning box: a glass wall around one column of water, so the film shows the water
        // the player is in.
        for (int y = ARENA_Y; y <= ARENA_Y + 3; y++) {
            for (int x = 6; x <= 12; x++) {
                for (int z = -10; z <= -3; z++) {
                    boolean wall = x == 6 || x == 12 || z == -10 || z == -3 || y == ARENA_Y + 3;
                    level.setBlockAndUpdate(
                            new BlockPos(x, y, z),
                            wall
                                    ? Blocks.GLASS.defaultBlockState()
                                    : Blocks.WATER.defaultBlockState());
                }
            }
        }

        // The flat world has air under the arena floor, so every sand block gets stone under it:
        // unsupported sand falls, and a cactus on falling sand breaks.
        for (int x = 7; x <= 11; x++) {
            for (int z = -9; z <= -4; z++) {
                setBlock(level, x, ARENA_Y - 2, z, Blocks.STONE);
                setBlock(level, x, ARENA_Y - 1, z, Blocks.SAND);
            }
        }
        for (int[] spot : new int[][] {{9, -8}, {8, -5}, {10, -4}, {7, -7}}) {
            level.setBlockAndUpdate(
                    new BlockPos(spot[0], ARENA_Y, spot[1]), Blocks.SEAGRASS.defaultBlockState());
        }

        plainCell(level, -5, -8);
        lavaCell(level, -2, -8);
        plainCell(level, 1, -8);
        plainCell(level, 4, -8);
        snowCell(level, -7, -13);
        cactusCell(level, 0, -13);
        plainCell(level, 6, -13);
        plainCell(level, -5, -40);
        plainCell(level, -2, -40);
        plainCell(level, 1, -40);
        plainCell(level, 4, -40);

        // Every dummy joins here, before the client connects: a join line that is broadcast while
        // nobody is connected can never appear in a recorded frame.
        for (String name : DUMMY_YAWS.keySet()) {
            createDummy(server, name);
        }
    }

    /// The recorded client: every dummy carries a custom name, the real player does not.
    private static int recordingPlayerId(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream()
                .filter(candidate -> candidate.getCustomName() == null)
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("Expected the recorded player in the player list"))
                .getId();
    }

    /// Puts the joined player at the arena centre, healed and empty-handed.
    private static void preparePlayer(MinecraftServer server) {
        ServerPlayer player = player(server, recordingPlayerId(server));
        int playerId = player.getId();
        healPlayer(server, playerId);
        player.teleportTo(server.overworld(), 0.5, ARENA_Y, 0.5, Set.of(), 0.0F, 0.0F, false);
        player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);
    }

    /// Puts the recording player back to full health, full air and unlit, so a scenario never
    /// starts wounded, drowned or on fire. A death screen would cover every later scenario.
    private static void healPlayer(MinecraftServer server, int playerId) {
        ServerPlayer player = player(server, playerId);
        player.setHealth(player.getMaxHealth());
        player.setAirSupply(player.getMaxAirSupply());
        player.setRemainingFireTicks(0);
        player.setTicksFrozen(0);
        player.removeAllEffects();
    }

    /// Clears the chat panel and shows the half's caption. The scene is already in place, so the
    /// first frame with this caption shows the scene it names.
    private static void openStage(
            ClientGameTestContext context, String id, String title, String subtitle) {
        ClientGameTestCommands.clearChat(context);
        ClientGameTestStages.markStage(id);
        ClientGameTestRecorder.showStep(context, id, title, subtitle);
    }

    private static void closeStage(ClientGameTestContext context, String id, int holdTicks) {
        ClientGameTestStages.endStage(context, id, holdTicks);
    }

    /// Teleports the player into a scenario's scene, already facing the point it looks at, and
    /// waits until the scene is built on the client. The caption of the previous scenario is still
    /// on screen, so the cut drops these frames.
    private static void enterScene(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            int playerId,
            Vec3 stand,
            Vec3 view) {
        server.runOnServer(
                current -> {
                    healPlayer(current, playerId);
                    ServerPlayer player = player(current, playerId);
                    player.teleportTo(
                            current.overworld(),
                            stand.x,
                            stand.y,
                            stand.z,
                            Set.of(),
                            player.getYRot(),
                            player.getXRot(),
                            false);
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, view);
                });
        context.waitTicks(6);
        ClientGameTestWaits.waitForChunkWindow(context, 4, 200);
        ClientGameTestWaits.waitForRenderSettled(context, 4, 200);
        // The view is already on the point, so this aim settles the client's own rotation without
        // a visible turn.
        ClientGameTestPlayerActions.lookAt(context, view);
    }

    private static void setDamageRules(
            MinecraftServer server, boolean disableIFrames, boolean disableAttackCooldown) {
        GameRules rules = server.overworld().getGameRules();
        rules.set(ModGameRules.DISABLE_IFRAMES, disableIFrames, server);
        rules.set(ModGameRules.DISABLE_ATTACK_COOLDOWN, disableAttackCooldown, server);
    }

    private static void recordMelee(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        int targetId =
                server.computeOnServer(
                        current -> {
                            giveSword(player(current, playerId));
                            return placeTarget(
                                    current, "MeleeDummy", MELEE_TARGET, WEAPON_TARGET_HEALTH);
                        });
        // Aim at the neck rather than the middle of the body: the name tag above the head then
        // sits near the middle of the frame instead of under the caption panel.
        Vec3 neck = chest(server, targetId).add(0.0, 0.45, 0.0);
        enterScene(context, server, playerId, MELEE_STAND, neck);
        meleeHalf(context, server, playerId, targetId, false);
        meleeHalf(context, server, playerId, targetId, true);
        server.runOnServer(current -> parkTarget(current, targetId));
    }

    /// Sixteen sword hits, one every two ticks, with the real attack key on a target pinned in
    /// place, so the player never walks or turns between hits.
    private static void meleeHalf(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            int playerId,
            int targetId,
            boolean rulesOn) {
        String stage = "showcase.melee-" + (rulesOn ? "on" : "off");
        String title =
                "Sword every 2 ticks, cooldown and i-frame rules " + (rulesOn ? "on" : "off");
        openStage(context, stage, title, "health " + health(WEAPON_TARGET_HEALTH));
        openHitLog(
                server,
                stage,
                playerId,
                targetId,
                current -> {
                    setDamageRules(current, rulesOn, rulesOn);
                    resetTarget(current, targetId, WEAPON_TARGET_HEALTH);
                });
        for (int hit = 1; hit <= MELEE_HITS; hit++) {
            ClientGameTestPlayerActions.attackEntity(context, targetId);
            context.waitTicks(INPUT_CADENCE_TICKS - 1);
            showHealth(context, server, targetId, stage, title, "hit " + hit);
        }
        finishHalf(context, server, stage, MELEE_HITS, rulesOn);
    }

    private static void recordBow(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        int targetId =
                server.computeOnServer(
                        current -> {
                            ServerPlayer player = player(current, playerId);
                            player.getInventory().clearContent();
                            player.getInventory().setItem(0, new ItemStack(Items.BOW));
                            player.getInventory().setItem(8, new ItemStack(Items.ARROW, 64));
                            player.getInventory().setSelectedSlot(0);
                            current.overworld()
                                    .getGameRules()
                                    .set(ModGameRules.INSTANT_SHOOT, true, current);
                            return placeTarget(
                                    current, "BowDummy", RANGE_TARGET, WEAPON_TARGET_HEALTH);
                        });
        enterScene(context, server, playerId, RANGE_STAND, chest(server, targetId));
        ClientGameTestPlayerActions.requireVanillaInteractionRanges(context);
        // The client's own hand must hold the bow: MultiPlayerGameMode#useItem sends no packet
        // while the client's carried item is empty, whatever the server's main hand holds.
        context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        for (boolean rulesOn : new boolean[] {false, true}) {
            String stage = "showcase.bow-" + (rulesOn ? "on" : "off");
            String title = "Bow, an arrow every 3 ticks, i-frame rule " + (rulesOn ? "on" : "off");
            openStage(context, stage, title, "health " + health(WEAPON_TARGET_HEALTH));
            openHitLog(
                    server,
                    stage,
                    playerId,
                    targetId,
                    current -> {
                        setDamageRules(current, rulesOn, false);
                        resetTarget(current, targetId, WEAPON_TARGET_HEALTH);
                    });
            for (int shot = 1; shot <= BOW_SHOTS; shot++) {
                // One tick of draw, then the release on the next tick fires a full-power arrow
                // under instant_shoot.
                context.getInput().holdKeyFor(options -> options.keyUse, 1);
                context.waitTicks(DRAW_CADENCE_TICKS - 1);
                showHealth(context, server, targetId, stage, title, "shot " + shot);
            }
            finishHalf(context, server, stage, BOW_SHOTS, rulesOn);
        }
        server.runOnServer(current -> parkTarget(current, targetId));
    }

    /// Nine weapons in the hotbar, one per slot, fired one after another from {@code stand} at the
    /// point {@code aim} picks on the target: the hotbar key and the use key go down in the same
    /// tick. A loaded crossbow fires and a splash potion is thrown on the press, and a trident is
    /// thrown on the release a tick later. Nothing of the half may hurt the recording player.
    private static void recordLoadedHotbar(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            int playerId,
            String name,
            String subject,
            String dummy,
            Vec3 targetPosition,
            Vec3 stand,
            Function<Entity, Vec3> aim,
            IntFunction<ItemStack> weapon,
            int cadenceTicks) {
        int targetId =
                server.computeOnServer(
                        current -> {
                            current.overworld()
                                    .getGameRules()
                                    .set(ModGameRules.INSTANT_SHOOT, true, current);
                            fillHotbar(player(current, playerId), weapon);
                            return placeTarget(
                                    current, dummy, targetPosition, WEAPON_TARGET_HEALTH);
                        });
        Vec3 view = server.computeOnServer(current -> aim.apply(entity(current, targetId)));
        enterScene(context, server, playerId, stand, view);
        ClientGameTestPlayerActions.requireVanillaInteractionRanges(context);
        for (boolean rulesOn : new boolean[] {false, true}) {
            String stage = "showcase." + name + "-" + (rulesOn ? "on" : "off");
            String title = subject + ", i-frame rule " + (rulesOn ? "on" : "off");
            server.runOnServer(current -> fillHotbar(player(current, playerId), weapon));
            waitForClientHotbar(context, weapon);
            openStage(context, stage, title, "health " + health(WEAPON_TARGET_HEALTH));
            openHitLog(
                    server,
                    stage,
                    playerId,
                    targetId,
                    current -> {
                        setDamageRules(current, rulesOn, false);
                        resetTarget(current, targetId, WEAPON_TARGET_HEALTH);
                    });
            for (int shot = 1; shot <= HOTBAR_SHOTS; shot++) {
                int slot = shot - 1;
                Function<Options, KeyMapping> hotbarKey = options -> options.keyHotbarSlots[slot];
                context.getInput().holdKey(hotbarKey);
                context.getInput().holdKey(options -> options.keyUse);
                context.waitTick();
                context.getInput().releaseKey(options -> options.keyUse);
                context.getInput().releaseKey(hotbarKey);
                context.waitTicks(cadenceTicks - 1);
                showHealth(context, server, targetId, stage, title, "shot " + shot);
            }
            finishHalf(context, server, stage, HOTBAR_SHOTS, rulesOn);
            float[] playerHealth =
                    server.computeOnServer(
                            current -> {
                                ServerPlayer player = player(current, playerId);
                                return new float[] {player.getHealth(), player.getMaxHealth()};
                            });
            if (playerHealth[0] < playerHealth[1]) {
                throw new AssertionError(
                        "Expected "
                                + stage
                                + " to leave the recording player unhurt, its health is "
                                + playerHealth[0]);
            }
        }
        server.runOnServer(current -> parkTarget(current, targetId));
    }

    private static void fillHotbar(ServerPlayer player, IntFunction<ItemStack> weapon) {
        player.getInventory().clearContent();
        for (int slot = 0; slot < HOTBAR_SHOTS; slot++) {
            player.getInventory().setItem(slot, weapon.apply(slot));
        }
    }

    /// Waits until the client's own hotbar holds every weapon: the client sends no use packet for a
    /// slot it still sees empty, so a press before the inventory update arrives would be lost.
    private static void waitForClientHotbar(
            ClientGameTestContext context, IntFunction<ItemStack> weapon) {
        context.waitFor(
                client -> {
                    for (int slot = 0; slot < HOTBAR_SHOTS; slot++) {
                        ItemStack held = client.player.getInventory().getItem(slot);
                        if (!ItemStack.isSameItemSameComponents(held, weapon.apply(slot))) {
                            return false;
                        }
                    }
                    return true;
                });
    }

    /// Starts the server's log of one half, after the server has set the half's rules and target.
    private static void openHitLog(
            TestDedicatedServerContext server,
            String stage,
            int playerId,
            int targetId,
            Consumer<MinecraftServer> prepare) {
        server.runOnServer(
                current -> {
                    prepare.accept(current);
                    hitLog = new HitLog(stage, playerId, targetId, serverTick);
                });
    }

    private static void showHealth(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            int targetId,
            String stage,
            String title,
            String label) {
        float health = server.computeOnServer(current -> living(current, targetId).getHealth());
        ClientGameTestRecorder.showStep(
                context, stage, title, label + ", health " + health(health));
    }

    /// Waits until every projectile of the half has reached the target, closes the server's log,
    /// holds the picture until the half has run about two seconds, and checks the log.
    private static void finishHalf(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            String stage,
            int expectedInputs,
            boolean rulesOn) {
        try {
            ClientGameTestWaits.waitForServer(
                    context,
                    server,
                    current -> {
                        HitLog log = hitLog;
                        return log.inputTicks.size() >= expectedInputs
                                && log.hits.size() >= log.inputTicks.size();
                    },
                    60,
                    "every attack or shot of " + stage + " to reach the target");
        } catch (AssertionError timeout) {
            throw new AssertionError(
                    timeout.getMessage()
                            + ": "
                            + server.computeOnServer(current -> hitLog.describe()),
                    timeout);
        }
        HitLog log =
                server.computeOnServer(
                        current -> {
                            HitLog closing = hitLog;
                            hitLog = null;
                            closing.close(
                                    serverTick, living(current, closing.targetId).getHealth());
                            return closing;
                        });
        System.out.println(log.describe());
        // The cut keeps a stage up to its end marker, so the half holds its last picture before
        // the marker until it has run about two seconds.
        context.waitTicks(Math.max(0, HALF_TICKS - (log.closeTick - log.openTick)));
        closeStage(context, stage, 4);
        assertCadence(log, expectedInputs);
        if (rulesOn) {
            assertEveryHitLands(log);
        } else {
            assertVanillaWindows(log);
        }
    }

    /// Every attack or shot reached the server, and no two of them are more than five ticks apart.
    private static void assertCadence(HitLog log, int expectedInputs) {
        if (log.inputTicks.size() != expectedInputs) {
            throw new AssertionError(
                    "Expected "
                            + expectedInputs
                            + " attacks or shots in "
                            + log.stage
                            + ", the server saw "
                            + log.inputTicks.size()
                            + ": "
                            + log.describe());
        }
        if (log.maxGap() > MAX_INPUT_GAP_TICKS) {
            throw new AssertionError(
                    "Expected an attack or shot at least every "
                            + MAX_INPUT_GAP_TICKS
                            + " ticks in "
                            + log.stage
                            + ", the longest gap was "
                            + log.maxGap()
                            + ": "
                            + log.describe());
        }
    }

    /// With the rules on, every hit that reaches the target takes health off it.
    private static void assertEveryHitLands(HitLog log) {
        for (Hit hit : log.hits) {
            if (hit.damage() <= 0.0F) {
                throw new AssertionError(
                        "Expected every hit in "
                                + log.stage
                                + " to damage the target, the hit at tick "
                                + hit.tick
                                + " left it at "
                                + hit.after
                                + ": "
                                + log.describe());
            }
        }
    }

    /// With the rules off, only the first hit of each vanilla damage-cooldown window lands in full:
    /// a later hit inside the next ten ticks deals at most what exceeds the window's first hit, so
    /// it always deals less than that hit.
    private static void assertVanillaWindows(HitLog log) {
        Hit opener = null;
        int openers = 0;
        for (Hit hit : log.hits) {
            boolean windowOver = opener == null || hit.tick - opener.tick >= VANILLA_WINDOW_TICKS;
            if (windowOver && hit.damage() > 0.0F) {
                opener = hit;
                openers++;
            } else if (opener == null) {
                throw new AssertionError(
                        "Expected the first hit of "
                                + log.stage
                                + " to damage the target: "
                                + log.describe());
            } else if (!windowOver && hit.damage() >= opener.damage()) {
                throw new AssertionError(
                        "Expected vanilla's damage cooldown to keep the hit at tick "
                                + hit.tick
                                + " in "
                                + log.stage
                                + " from landing in full: "
                                + log.describe());
            }
        }
        if (openers >= log.hits.size()) {
            throw new AssertionError(
                    "Expected vanilla's damage cooldown to reject some hits in "
                            + log.stage
                            + ": "
                            + log.describe());
        }
    }

    private static void recordDrowning(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        enterScene(context, server, playerId, DROWNING_STAND, DROWNING_VIEW);
        String offTitle = "Under water, effect and i-frame rules off";
        openStage(context, "showcase.drowning-off", offTitle, "air 300, health 20");
        server.runOnServer(
                current -> {
                    setEffectRules(current, false);
                    healPlayer(current, playerId);
                });
        int[] slow = new int[2];
        for (int reading = 0; reading <= 3; reading++) {
            if (reading > 0) {
                context.waitTicks(10);
            }
            slow = reportAir(context, server, playerId, "showcase.drowning-off", offTitle, reading);
        }
        if (slow[0] < 200 || slow[1] != 20) {
            throw new AssertionError(
                    "Expected vanilla air to last, air was " + slow[0] + " and health " + slow[1]);
        }
        closeStage(context, "showcase.drowning-off", 4);

        String onTitle = "Under water, effect and i-frame rules on";
        openStage(context, "showcase.drowning-on", onTitle, "air 300, health 20");
        server.runOnServer(
                current -> {
                    setEffectRules(current, true);
                    healPlayer(current, playerId);
                });
        int[] drowned = new int[2];
        for (int reading = 0; reading <= 4; reading++) {
            if (reading > 0) {
                context.waitTicks(10);
            }
            drowned =
                    reportAir(context, server, playerId, "showcase.drowning-on", onTitle, reading);
        }
        if (drowned[0] > 0 || drowned[1] >= 20) {
            throw new AssertionError(
                    "Expected the air to run out and drowning damage to start, air was "
                            + drowned[0]
                            + " and health "
                            + drowned[1]);
        }
        // Out of the water before the next hit would kill: a death screen would cover every later
        // scenario.
        server.runOnServer(
                current -> {
                    setEffectRules(current, false);
                    healPlayer(current, playerId);
                });
        closeStage(context, "showcase.drowning-on", 4);
    }

    private static int[] reportAir(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            int playerId,
            String stepId,
            String stepTitle,
            int reading) {
        int[] state =
                server.computeOnServer(
                        current -> {
                            ServerPlayer player = player(current, playerId);
                            return new int[] {
                                player.getAirSupply(), Math.round(player.getHealth())
                            };
                        });
        String line = (reading * 10) + " ticks: air " + state[0] + ", health " + state[1];
        ClientGameTestRecorder.showStep(context, stepId, stepTitle, line);
        System.out.println("AD_CURVE " + stepId + " " + line);
        return state;
    }

    private static void setEffectRules(MinecraftServer server, boolean on) {
        GameRules rules = server.overworld().getGameRules();
        rules.set(ModGameRules.FASTER_EFFECT_TICKING, on, server);
        rules.set(ModGameRules.DISABLE_IFRAMES, on, server);
        rules.set(ModGameRules.INSTANT_SHOOT, false, server);
        rules.set(ModGameRules.DISABLE_ATTACK_COOLDOWN, false, server);
    }

    /// Fire, lava, poison and wither in the front row; powder snow, cactus and a fish on land in
    /// the back row, each back-row target in a gap of the front row, so the camera sees all seven.
    /// The caption panel cuts a line at 360 GUI pixels (MinecraftModConventions 3.4.0,
    /// ClientGameTestRecordingHud.java:24), short of seven figures and the rules, so the title
    /// carries the first five figures and the subtitle the other two and the rules.
    private static EffectScene effectsScene() {
        return new EffectScene(
                "effects",
                null,
                EFFECTS_STAND,
                new Vec3(0.0, ARENA_Y + 0.9, -10.0),
                List.of("Fire", "Lava", "Poison", "Wither", "Freezing", "Cactus", "Drying"),
                5,
                Map.of(),
                Set.of("Fire", "Poison", "Wither"),
                (current, previous) -> {
                    int fire = placeTarget(current, "Fire", new Vec3(-4.5, ARENA_Y, -7.5));
                    int lava = placeTarget(current, "Lava", new Vec3(-1.5, ARENA_Y, -7.5));
                    int poison = placeTarget(current, "Poison", new Vec3(1.5, ARENA_Y, -7.5));
                    int wither = placeTarget(current, "Wither", new Vec3(4.5, ARENA_Y, -7.5));
                    int freezing = placeTarget(current, "Freezing", new Vec3(-6.5, ARENA_Y, -12.5));
                    int cactus = placeTarget(current, "Cactus", new Vec3(0.85, ARENA_Y, -12.5));
                    int drying =
                            previous.isEmpty()
                                    ? spawnMob(
                                            current,
                                            EntityTypes.COD,
                                            "Drying",
                                            new Vec3(6.5, ARENA_Y, -12.5))
                                    : previous.get(6);
                    LivingEntity fish = living(current, drying);
                    fish.getAttribute(Attributes.MAX_HEALTH).setBaseValue(EFFECT_TARGET_HEALTH);
                    fish.setHealth(EFFECT_TARGET_HEALTH);
                    fish.setAirSupply(0);
                    List<Integer> targets =
                            List.of(fire, lava, poison, wither, freezing, cactus, drying);
                    setDamageEffects(current, targets);
                    return targets;
                },
                (current, targets) -> {
                    setDamageEffects(current, targets);
                    living(current, targets.get(4)).setTicksFrozen(0);
                    living(current, targets.get(6)).setAirSupply(0);
                });
    }

    /// Sets fire, poison and wither on the fire, poison and wither targets with full durations.
    private static void setDamageEffects(MinecraftServer server, List<Integer> targets) {
        LivingEntity fire = living(server, targets.get(0));
        LivingEntity poison = living(server, targets.get(2));
        LivingEntity wither = living(server, targets.get(3));
        fire.setRemainingFireTicks(400);
        poison.addEffect(new MobEffectInstance(MobEffects.POISON, 320, 0));
        wither.addEffect(new MobEffectInstance(MobEffects.WITHER, 400, 0));
        if (fire.getRemainingFireTicks() <= 0
                || !poison.hasEffect(MobEffects.POISON)
                || !wither.hasEffect(MobEffects.WITHER)) {
            throw new AssertionError(
                    "Expected to set fire, poison and wither on the effect targets");
        }
    }

    /// Starving, two kinds of healing and a poison cloud. One healer has Regeneration II; the other
    /// keeps a full food bar and full saturation for the whole half, so it heals from saturation
    /// without running dry.
    private static EffectScene hungerEffectsScene() {
        return new EffectScene(
                "effects-hunger",
                "Starving, healing, poison cloud",
                HUNGER_STAND,
                new Vec3(0.0, ARENA_Y + 0.9, -39.5),
                List.of("Starving", "Regeneration", "Saturated", "PoisonCloud"),
                0,
                Map.of(
                        "Regeneration",
                        off ->
                                applicationsPerTick(
                                        MobEffects.REGENERATION,
                                        REGENERATION_AMPLIFIER,
                                        REGENERATION_TICKS),
                        "Saturated",
                        HealLog::stepsPerTick),
                Set.of(),
                (current, previous) -> {
                    ServerLevel level = current.overworld();
                    int starving = placeTarget(current, "Starving", new Vec3(-4.5, ARENA_Y, -39.5));
                    int regeneration =
                            placeTarget(current, "Regeneration", new Vec3(-1.5, ARENA_Y, -39.5));
                    int saturated =
                            placeTarget(current, "Saturated", new Vec3(1.5, ARENA_Y, -39.5));
                    int clouded =
                            placeTarget(current, "PoisonCloud", new Vec3(4.5, ARENA_Y, -39.5));
                    // Saturation, hunger and regeneration start at the marker; until then the
                    // targets keep the dummies' 17 food, at which neither healing nor starving runs
                    // (FoodData.java:45-60 in the 26.3 sources).
                    living(current, regeneration).setHealth(EFFECT_TARGET_HEALTH / 2.0F);
                    living(current, saturated).setHealth(EFFECT_TARGET_HEALTH / 2.0F);

                    if (previous.size() > 4) {
                        Entity oldCloud = level.getEntity(previous.get(4));
                        if (oldCloud != null) {
                            oldCloud.discard();
                        }
                    }
                    AreaEffectCloud cloud = new AreaEffectCloud(level, 4.5, ARENA_Y, -39.5);
                    cloud.setPotionContents(
                            new PotionContents(
                                    Optional.of(Potions.POISON),
                                    Optional.empty(),
                                    List.of(),
                                    Optional.empty()));
                    cloud.setRadius(2.5F);
                    cloud.setDuration(600);
                    cloud.setWaitTime(0);
                    level.addFreshEntity(cloud);
                    return List.of(starving, regeneration, saturated, clouded, cloud.getId());
                },
                (current, targets) -> {
                    ServerPlayer starving = player(current, targets.get(0));
                    starving.getFoodData().setFoodLevel(0);
                    starving.getFoodData().setSaturation(0.0F);
                    player(current, targets.get(1))
                            .addEffect(
                                    new MobEffectInstance(
                                            MobEffects.REGENERATION,
                                            REGENERATION_TICKS,
                                            REGENERATION_AMPLIFIER));
                    FED.add(targets.get(2));
                    // The cloud ends once its age passes its duration (AreaEffectCloud.java:191),
                    // so it gets its full duration again from the marker.
                    Entity cloud = current.overworld().getEntity(targets.get(4));
                    if (!(cloud instanceof AreaEffectCloud poisonCloud)) {
                        throw new AssertionError("Expected the poison cloud to exist");
                    }
                    poisonCloud.setDuration(poisonCloud.tickCount + 600);
                });
    }

    /// How many times per tick vanilla applies an effect at an amplifier, asked of the effect
    /// itself over {@code ticks} ticks of its duration.
    private static double applicationsPerTick(Holder<MobEffect> effect, int amplifier, int ticks) {
        int applications = 0;
        for (int tick = 1; tick <= ticks; tick++) {
            if (effect.value().shouldApplyEffectTickThisTick(tick, amplifier)) {
                applications++;
            }
        }
        return applications / (double) ticks;
    }

    /// One effect scenario: about two seconds with faster_effect_ticking and disable_i_frames off,
    /// then the same targets with fresh health and the same effects with both rules on. Every
    /// target loses more health (a healer gains more) with the rules on than with them off, and a
    /// healer heals ten times as fast as vanilla with them on.
    ///
    /// Each half's targets, effects and cloud are in place, shown by the client and facing the
    /// camera before the half's marker. Until the marker the targets are invulnerable, so every
    /// curve still starts from full health at the marker.
    private static void recordEffectScene(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            int playerId,
            EffectScene scene) {
        enterScene(context, server, playerId, scene.stand(), scene.view());
        server.runOnServer(current -> player(current, playerId).getInventory().clearContent());
        int count = scene.names().size();
        float[][] change = new float[2][count];
        List<Map<String, HealLog>> heals = new ArrayList<>();
        List<Integer> targets = List.of();
        for (int half = 0; half < 2; half++) {
            boolean rulesOn = half == 1;
            String stage = "showcase." + scene.id() + "-" + (rulesOn ? "on" : "off");
            List<Integer> previous = targets;
            targets =
                    server.computeOnServer(
                            current -> {
                                setEffectRules(current, rulesOn);
                                List<Integer> ids = scene.setup().place(current, previous);
                                ids.forEach(
                                        id -> entity(current, id).setPermanentlyInvulnerable(true));
                                return ids;
                            });
            List<Integer> placed = targets;
            List<Integer> shown = targets.subList(0, count);
            List<ShotTarget> shot = shotTargets(server, placed);
            waitForShot(context, stage, shot);
            context.waitTicks(SCENE_SETTLE_TICKS);
            requireShot(context, stage, "at its marker", shot);
            String[] starting =
                    server.computeOnServer(current -> caption(current, shown, scene, rulesOn));
            openStage(context, stage, starting[0], starting[1]);
            float[] start =
                    server.computeOnServer(
                            current -> {
                                placed.forEach(
                                        id ->
                                                entity(current, id)
                                                        .setPermanentlyInvulnerable(false));
                                scene.start().begin(current, placed);
                                for (String healer : scene.healers().keySet()) {
                                    HEAL_LOGS.put(
                                            shown.get(scene.names().indexOf(healer)),
                                            new HealLog(serverTick));
                                }
                                return healthsOf(current, shown);
                            });
            System.out.println("AD_CURVE " + stage + " 0 ticks: " + String.join(" | ", starting));
            for (int reading = 1; reading <= 3; reading++) {
                context.waitTicks(READING_TICKS);
                String[] label =
                        server.computeOnServer(current -> caption(current, shown, scene, rulesOn));
                ClientGameTestRecorder.showStep(context, stage, label[0], label[1]);
                System.out.println(
                        "AD_CURVE "
                                + stage
                                + " "
                                + reading * READING_TICKS
                                + " ticks: "
                                + String.join(" | ", label));
                requireShot(context, stage, "after " + reading * READING_TICKS + " ticks", shot);
            }
            Map<String, HealLog> halfHeals = new LinkedHashMap<>();
            float[] end =
                    server.computeOnServer(
                            current -> {
                                for (String healer : scene.healers().keySet()) {
                                    HealLog log =
                                            HEAL_LOGS.remove(
                                                    shown.get(scene.names().indexOf(healer)));
                                    log.closeTick = serverTick;
                                    halfHeals.put(healer, log);
                                }
                                return healthsOf(current, shown);
                            });
            heals.add(halfHeals);
            for (int index = 0; index < count; index++) {
                change[half][index] = end[index] - start[index];
            }
            closeStage(context, stage, 4);
        }
        List<Integer> finished = targets;
        server.runOnServer(
                current -> {
                    setEffectRules(current, false);
                    finished.forEach(target -> parkTarget(current, target));
                });
        assertHealingAccelerated(scene, heals);
        assertEffectsAccelerated(scene, change);
    }

    /// With the rules off a healer heals at vanilla's rate, and with them on at ten times that
    /// rate, each within one heal. Vanilla's rate is one heal of the rules-off half times the
    /// healer's vanilla heals per tick. The take's log keeps one line per healer for the report.
    private static void assertHealingAccelerated(
            EffectScene scene, List<Map<String, HealLog>> heals) {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, ToDoubleFunction<HealLog>> healer : scene.healers().entrySet()) {
            String name = healer.getKey();
            HealLog off = heals.get(0).get(name);
            HealLog on = heals.get(1).get(name);
            float step = off.healPerStep();
            double vanillaPerTick = step * healer.getValue().applyAsDouble(off);
            double onPerTick = on.healed / (double) on.ticks();
            String line =
                    String.format(
                            Locale.ROOT,
                            "AD_TICKLOG showcase.%s heal %s off[%s] on[%s] vanillaPerTick=%.4f"
                                    + " onPerTick=%.4f onOverVanilla=%.2f",
                            scene.id(),
                            name,
                            off,
                            on,
                            vanillaPerTick,
                            onPerTick,
                            onPerTick / vanillaPerTick);
            System.out.println(line);
            if (step <= 0.0F) {
                failures.add("Expected " + name + " to heal with the rules off: " + line);
                continue;
            }
            double expectedOff = vanillaPerTick * off.ticks();
            double expectedOn = VirtualTime.ACCELERATION * vanillaPerTick * on.ticks();
            if (Math.abs(off.healed - expectedOff) > step) {
                failures.add(
                        String.format(
                                Locale.ROOT,
                                "Expected %s to heal %.1f at vanilla's rate with the rules off: %s",
                                name,
                                expectedOff,
                                line));
            }
            if (Math.abs(on.healed - expectedOn) > step) {
                failures.add(
                        String.format(
                                Locale.ROOT,
                                "Expected %s to heal %.1f, %d times vanilla's rate, with the rules"
                                        + " on: %s",
                                name,
                                expectedOn,
                                VirtualTime.ACCELERATION,
                                line));
            }
        }
        if (!failures.isEmpty()) {
            throw new AssertionError(String.join("\n", failures));
        }
    }

    /// The caption of one effect half. The title names the scene and its rules and the subtitle
    /// holds every target's health; a scene whose figures do not fit one line puts its first {@code
    /// titleNames} figures in the title and the rules after the rest.
    private static String[] caption(
            MinecraftServer server, List<Integer> targets, EffectScene scene, boolean rulesOn) {
        String rules = "effect and i-frame rules " + (rulesOn ? "on" : "off");
        int split = scene.titleNames();
        if (split == 0) {
            return new String[] {
                scene.subject() + ", " + rules,
                healthLine(server, targets, scene, 0, targets.size())
            };
        }
        return new String[] {
            healthLine(server, targets, scene, 0, split),
            healthLine(server, targets, scene, split, targets.size()) + ", " + rules
        };
    }

    private static float[] healthsOf(MinecraftServer server, List<Integer> targets) {
        float[] values = new float[targets.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = living(server, targets.get(index)).getHealth();
        }
        return values;
    }

    /// Where the server holds each of a scene's targets, and the way it turns them.
    private static List<ShotTarget> shotTargets(
            TestDedicatedServerContext server, List<Integer> targets) {
        return server.computeOnServer(
                current -> {
                    List<ShotTarget> shot = new ArrayList<>();
                    for (int id : targets) {
                        Entity entity = entity(current, id);
                        shot.add(
                                new ShotTarget(
                                        id,
                                        entity.getName().getString(),
                                        entity.position(),
                                        entity.getYRot(),
                                        entity instanceof ServerPlayer));
                    }
                    return shot;
                });
    }

    private static void waitForShot(
            ClientGameTestContext context, String stage, List<ShotTarget> shot) {
        try {
            context.waitFor(client -> shotViolation(client, shot) == null, 200);
        } catch (AssertionError timeout) {
            throw new AssertionError(
                    stage
                            + ": the client never showed the scene in place: "
                            + context.computeOnClient(client -> shotViolation(client, shot)),
                    timeout);
        }
    }

    /// Fails unless the client shows every target of the scene where the server holds it, with
    /// every player-shaped target facing the camera.
    private static void requireShot(
            ClientGameTestContext context, String stage, String when, List<ShotTarget> shot) {
        String violation = context.computeOnClient(client -> shotViolation(client, shot));
        if (violation != null) {
            throw new AssertionError(stage + " " + when + ": " + violation);
        }
    }

    private static String shotViolation(Minecraft client, List<ShotTarget> shot) {
        Vec3 camera = client.player.getEyePosition();
        for (ShotTarget target : shot) {
            Entity entity = client.level.getEntity(target.id());
            if (entity == null) {
                return target.label() + " is not in the client's world";
            }
            double dx = entity.getX() - target.position().x;
            double dz = entity.getZ() - target.position().z;
            // A fish on land flops about its cell; a pinned target and the cloud stay put.
            double reach = entity instanceof Mob ? 2.0 : 0.1;
            if (dx * dx + dz * dz > reach * reach) {
                return target.label() + " is not at its place";
            }
            if (!target.faces() || !(entity instanceof LivingEntity living)) {
                continue;
            }
            float body = Mth.wrapDegrees(living.yBodyRot - target.yaw());
            float head = Mth.wrapDegrees(living.getYHeadRot() - target.yaw());
            if (Math.abs(body) > YAW_TOLERANCE_DEGREES || Math.abs(head) > YAW_TOLERANCE_DEGREES) {
                return String.format(
                        Locale.ROOT,
                        "%s is turned %.0f degrees (body) and %.0f degrees (head) from its yaw %.0f"
                                + " (client yaw %.0f, %d ticks on the client)",
                        target.label(),
                        body,
                        head,
                        target.yaw(),
                        living.getYRot(),
                        living.tickCount);
            }
            // Yaw 0 faces +Z, so a yaw points along (-sin, cos).
            Vec3 toCamera = camera.subtract(entity.position());
            float towardCamera = (float) Math.toDegrees(Math.atan2(-toCamera.x, toCamera.z));
            float offCamera = Mth.wrapDegrees(towardCamera - target.yaw());
            if (Math.abs(offCamera) > CAMERA_TOLERANCE_DEGREES) {
                return String.format(
                        Locale.ROOT,
                        "%s faces %.0f degrees away from the camera",
                        target.label(),
                        offCamera);
            }
        }
        return null;
    }

    private static void assertEffectsAccelerated(EffectScene scene, float[][] change) {
        for (int index = 0; index < scene.names().size(); index++) {
            String name = scene.names().get(index);
            boolean heals = scene.healers().containsKey(name);
            float off = heals ? change[0][index] : -change[0][index];
            float on = heals ? change[1][index] : -change[1][index];
            if (on <= 0.0F || on <= off) {
                throw new AssertionError(
                        "Expected "
                                + name
                                + " to "
                                + (heals ? "heal" : "lose health")
                                + " faster with the rules on: "
                                + off
                                + " with them off, "
                                + on
                                + " with them on");
            }
            if (scene.slowInVanilla().contains(name) && off > 4.0F) {
                throw new AssertionError(
                        "Expected vanilla effect ticking to take at most four health from "
                                + name
                                + " in three readings, it took "
                                + off);
            }
        }
    }

    /// One no-AI mob, for a fish out of water.
    private static int spawnMob(
            MinecraftServer server, EntityType<? extends Mob> type, String name, Vec3 position) {
        ServerLevel level = server.overworld();
        Mob target = type.create(level, EntitySpawnReason.COMMAND);
        if (target == null) {
            throw new AssertionError("Expected to create " + name);
        }
        target.setPos(position.x, position.y, position.z);
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.setCustomName(Component.literal(name));
        target.setCustomNameVisible(true);
        level.addFreshEntity(target);
        return target.getId();
    }

    /// One caption line holding the health of the scenario's targets {@code from} up to {@code to},
    /// in the order the scenario names them.
    private static String healthLine(
            MinecraftServer server, List<Integer> targets, EffectScene scene, int from, int to) {
        StringBuilder line = new StringBuilder();
        for (int index = from; index < to; index++) {
            LivingEntity target = living(server, targets.get(index));
            if (line.length() > 0) {
                line.append("   ");
            }
            line.append(scene.names().get(index))
                    .append(' ')
                    .append(Math.round(Math.max(0.0F, target.getHealth())));
        }
        return line.toString();
    }

    private static void plainCell(ServerLevel level, int x, int z) {
        setBlock(level, x, ARENA_Y - 1, z, Blocks.STONE);
        setBlock(level, x, ARENA_Y, z, Blocks.AIR);
        setBlock(level, x + 1, ARENA_Y, z, Blocks.AIR);
        setBlock(level, x - 1, ARENA_Y, z, Blocks.AIR);
        setBlock(level, x, ARENA_Y, z + 1, Blocks.AIR);
        setBlock(level, x, ARENA_Y, z - 1, Blocks.AIR);
    }

    private static void lavaCell(ServerLevel level, int x, int z) {
        setBlock(level, x, ARENA_Y - 1, z, Blocks.STONE);
        setBlock(level, x, ARENA_Y, z, Blocks.LAVA);
        setBlock(level, x + 1, ARENA_Y, z, Blocks.GLASS);
        setBlock(level, x - 1, ARENA_Y, z, Blocks.GLASS);
        setBlock(level, x, ARENA_Y, z + 1, Blocks.GLASS);
        setBlock(level, x, ARENA_Y, z - 1, Blocks.GLASS);
    }

    private static void snowCell(ServerLevel level, int x, int z) {
        setBlock(level, x, ARENA_Y - 1, z, Blocks.STONE);
        setBlock(level, x, ARENA_Y, z, Blocks.POWDER_SNOW);
    }

    private static void cactusCell(ServerLevel level, int x, int z) {
        setBlock(level, x, ARENA_Y - 1, z, Blocks.STONE);
        setBlock(level, x + 1, ARENA_Y - 2, z, Blocks.STONE);
        setBlock(level, x + 1, ARENA_Y - 1, z, Blocks.SAND);
        setBlock(level, x + 1, ARENA_Y, z, Blocks.CACTUS);
    }

    private static void setBlock(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlockAndUpdate(new BlockPos(x, y, z), block.defaultBlockState());
    }

    private static ItemStack loadedCrossbow(ItemStack projectile) {
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.set(
                DataComponents.CHARGED_PROJECTILES,
                ChargedProjectiles.ofNonEmpty(List.of(projectile)));
        return crossbow;
    }

    /// A firework rocket with one small-ball explosion, which is what makes
    /// FireworkRocketEntity#dealExplosionDamage deal damage at all: a rocket item with no {@code
    /// FIREWORKS} component has no explosion to apply.
    private static ItemStack fireworkRocket() {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        FireworkExplosion explosion =
                new FireworkExplosion(
                        FireworkExplosion.Shape.SMALL_BALL,
                        IntList.of(0xFF5555),
                        IntList.of(0xFFFFFF),
                        true,
                        true);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(explosion)));
        return rocket;
    }

    private static void giveSword(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getInventory().setSelectedSlot(0);
        player.getInventory().setItem(0, new ItemStack(Items.WOODEN_SWORD));
    }

    /// One decimal for every health figure the caption prints, so a rocket's fractional damage
    /// reads like the other scenarios' numbers.
    private static String health(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static Vec3 chest(TestDedicatedServerContext server, int entityId) {
        return server.computeOnServer(current -> chest(entity(current, entityId)));
    }

    private static Vec3 chest(Entity entity) {
        return entity.getBoundingBox().getCenter();
    }

    private static Entity entity(MinecraftServer server, int entityId) {
        Entity entity = server.overworld().getEntity(entityId);
        if (entity == null) {
            throw new AssertionError("Expected entity " + entityId + " in the showcase arena");
        }
        return entity;
    }

    private static LivingEntity living(MinecraftServer server, int entityId) {
        Entity entity = entity(server, entityId);
        if (!(entity instanceof LivingEntity living)) {
            throw new AssertionError("Expected a living entity at " + entityId);
        }
        return living;
    }

    private static ServerPlayer player(MinecraftServer server, int playerId) {
        Entity entity = server.overworld().getEntity(playerId);
        if (!(entity instanceof ServerPlayer player)) {
            throw new AssertionError("Expected the showcase player at entity id " + playerId);
        }
        return player;
    }

    /// Connects a player-shaped, survival target the way a vanilla GameTest does, so the vanilla
    /// client renders it as a player the mod's damage applies to. Its food level sits below the
    /// regeneration threshold and above starvation, so its health only changes when something hits
    /// it. Creates a dummy once, parked far from the arena, and remembers its id.
    ///
    /// A dummy entering the client's view is spawned with the last rotation its ServerEntity sent
    /// (ClientboundAddEntityPacket.java:45-49, ServerEntity.java:93 and 201-205 in the 26.3
    /// sources), and the client builds its body from that, so the dummy holds its shown yaw from
    /// before it joins.
    private static void createDummy(MinecraftServer server, String name) {
        ServerLevel level = server.overworld();
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer target =
                new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        float yaw = DUMMY_YAWS.get(name);
        target.setYRot(yaw);
        target.setYHeadRot(yaw);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, target, cookie);
        // ServerPlayer#isInvulnerableTo rejects every damage source until the client acknowledges
        // the world load, which a real client does with this packet after its 60-tick load timer;
        // the fake client is already loaded.
        target.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        target.setGameMode(GameType.SURVIVAL);
        target.teleportTo(level, PARKING.x, PARKING.y, PARKING.z, Set.of(), yaw, 0.0F, false);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(EFFECT_TARGET_HEALTH);
        target.setHealth(EFFECT_TARGET_HEALTH);
        target.getFoodData().setFoodLevel(17);
        target.getFoodData().setSaturation(0.0F);
        target.setCustomName(Component.literal(name));
        target.setCustomNameVisible(true);
        DUMMIES.put(name, target.getId());
        TRACKED.put(target.getUUID(), new TrackedTarget(target.getUUID(), name, PARKING));
    }

    /// An effect target: pinned in its cell with full effect-target health, facing the camera.
    private static int placeTarget(MinecraftServer server, String name, Vec3 position) {
        return placeTarget(server, name, position, EFFECT_TARGET_HEALTH);
    }

    /// Moves a pre-created dummy to its scenario position with a fresh health pool, facing its yaw,
    /// and pins it there so knockback cannot move it out of the shot.
    private static int placeTarget(
            MinecraftServer server, String name, Vec3 position, float health) {
        Integer id = DUMMIES.get(name);
        if (id == null) {
            throw new AssertionError("Expected the dummy " + name + " to exist");
        }
        resetTarget(server, id, health);
        ServerPlayer target = player(server, id);
        target.teleportTo(
                server.overworld(),
                position.x,
                position.y,
                position.z,
                Set.of(),
                DUMMY_YAWS.get(name),
                0.0F,
                false);
        TRACKED.put(target.getUUID(), new TrackedTarget(target.getUUID(), name, position));
        return id;
    }

    /// Gives a dummy a fresh health pool and clears what the last half left on it: fire, frost,
    /// effects, stuck arrows and the feeding.
    private static void resetTarget(MinecraftServer server, int targetId, float health) {
        FED.remove(targetId);
        ServerPlayer target = player(server, targetId);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        target.setHealth(health);
        target.getFoodData().setFoodLevel(17);
        target.getFoodData().setSaturation(0.0F);
        target.setRemainingFireTicks(0);
        target.setTicksFrozen(0);
        target.setArrowCount(0);
        target.removeAllEffects();
    }

    /// Sends a dummy back to the parking spot so the next scenario's shot is clean; any other
    /// entity is discarded.
    private static void parkTarget(MinecraftServer server, int entityId) {
        FED.remove(entityId);
        Entity target = server.overworld().getEntity(entityId);
        if (target instanceof ServerPlayer player) {
            String name =
                    DUMMIES.entrySet().stream()
                            .filter(entry -> entry.getValue() == entityId)
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .orElseThrow();
            player.removeAllEffects();
            player.setRemainingFireTicks(0);
            player.setHealth(player.getMaxHealth());
            player.teleportTo(
                    server.overworld(),
                    PARKING.x,
                    PARKING.y,
                    PARKING.z,
                    Set.of(),
                    DUMMY_YAWS.get(name),
                    0.0F,
                    false);
            TRACKED.put(player.getUUID(), new TrackedTarget(player.getUUID(), name, PARKING));
        } else if (target != null) {
            target.discard();
        }
    }

    private static void restoreState(MinecraftServer server) {
        restoreRules(server);
        TRACKED.clear();
        hitLog = null;
        FED.clear();
        HEAL_LOGS.clear();
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (player.getCustomName() != null) {
                server.getPlayerList().remove(player);
            }
        }
        if (!server.getPlayerList().getPlayers().isEmpty()) {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.removeEffect(MobEffects.SPEED);
            player.setAirSupply(player.getMaxAirSupply());
            player.getInventory().clearContent();
        }
    }

    private static void restoreRules(MinecraftServer server) {
        GameRules rules = server.overworld().getGameRules();
        rules.set(ModGameRules.DISABLE_IFRAMES, false, server);
        rules.set(ModGameRules.FASTER_EFFECT_TICKING, false, server);
        rules.set(ModGameRules.DISABLE_ATTACK_COOLDOWN, false, server);
        rules.set(ModGameRules.INSTANT_SHOOT, false, server);
    }
}
