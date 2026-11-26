package io.github.brainage04.accelerateddamage;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.accelerateddamage.recording.ShowcaseFramePacer;
import io.github.brainage04.accelerateddamage.util.VirtualTime;
import io.github.brainage04.minecraftmodconventions.ClientGameTestCommands;
import io.github.brainage04.minecraftmodconventions.ClientGameTestPlayerActions;
import io.github.brainage04.minecraftmodconventions.ClientGameTestRecorder;
import io.github.brainage04.minecraftmodconventions.ClientGameTestRecordingHud;
import io.github.brainage04.minecraftmodconventions.ClientGameTestServers;
import io.github.brainage04.minecraftmodconventions.ClientGameTestStages;
import io.github.brainage04.minecraftmodconventions.ClientGameTestWaits;
import io.github.brainage04.minecraftmodconventions.PerformanceOverlayPlacement;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.HashMap;
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
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ThrownTridentRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.FluidTags;
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
import net.minecraft.world.entity.decoration.ArmorStand;
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
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

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
    /// The camera's near plane, in blocks: {@code Camera.setupPerspective} uses 0.05 ({@code
    /// Camera.java:106} in the 26.3 sources).
    private static final double NEAR_PLANE = 0.05;

    /// The most of the frame the entities and particles of one frame may cover together. A
    /// player-shaped target at its own distance covers a few per cent of it; a body across the lens
    /// covers all of it. A take measured at 0.5 had three frames of the dummy at 45.8% and four at
    /// about 30%, so the bound is a quarter.
    private static final double MAX_ENTITY_SCREEN_COVERAGE = 0.25;

    /// How far outside a particle quad the inverse bilinear lookup may land and still count the
    /// cell. The raster samples cell centres, so a small margin absorbs rounding at the edges.
    private static final double QUAD_UV_MARGIN = 0.01;

    /// Newton steps for the inverse bilinear lookup. Three iterations already land far inside a
    /// cell for the near-planar quads the renderer draws; the extra steps are cheap and keep the
    /// lookup inside the margin at the lens.
    private static final int BILINEAR_ITERATIONS = 6;

    /// Residual and Jacobian bounds that stop the Newton iteration and reject a singular quad.
    private static final double BILINEAR_EPSILON = 1.0E-12;

    /// UV tolerance when matching a quad's atlas rectangle to one sprite, and when deciding whether
    /// the sprite's UV span is usable.
    private static final double SPRITE_UV_TOLERANCE = 1.0E-4;

    /// Server ticks allowed for every input of a half to reach the target before the check fails.
    private static final int INPUT_WAIT_TICKS = 60;

    /// The frame's coverage is rasterised onto this grid, in cells across and down: a quarter of
    /// the frame is thirteen and a half rows of one or twenty-four columns of the other, far finer
    /// than the bound needs to be.
    private static final int COVERAGE_GRID_WIDTH = 96;

    private static final int COVERAGE_GRID_HEIGHT = 54;

    /// The frame's coverage cells, marked with the frame's epoch rather than cleared: the raster
    /// runs on the render thread once per drawn frame, so one scratch grid and one epoch serve
    /// every frame.
    private static final int[] COVERAGE_GRID = new int[COVERAGE_GRID_WIDTH * COVERAGE_GRID_HEIGHT];

    private static int coverageEpoch;

    /// The last coverage over {@link #MAX_ENTITY_SCREEN_COVERAGE} since the last frame check, or
    /// null when no frame's entities and particles covered more than that.
    private static volatile String LAST_FRAME_VIOLATION;

    /// The models {@link #modelFaces} measures. They are baked once per model set rather than per
    /// frame, and re-baked when the client swaps its model set at a resource reload ({@code
    /// ModelManager}).
    private static final Map<ModelLayerLocation, ModelPart> MODELS = new HashMap<>();

    private static Object modelsOwner;

    /// Scratch vertices for the coverage raster, render thread only: a polygon clipped at the near
    /// plane gains at most one vertex per clipped edge, and the quads and model faces the raster
    /// reads have four.
    private static final int MAX_RASTER_VERTICES = 8;

    private static final double[] RWX = new double[MAX_RASTER_VERTICES];
    private static final double[] RWY = new double[MAX_RASTER_VERTICES];
    private static final double[] RWZ = new double[MAX_RASTER_VERTICES];
    private static final double[] RNX = new double[MAX_RASTER_VERTICES];
    private static final double[] RNY = new double[MAX_RASTER_VERTICES];
    private static final double[] RIW = new double[MAX_RASTER_VERTICES];
    private static final double[] RPA = new double[MAX_RASTER_VERTICES];
    private static final double[] RPB = new double[MAX_RASTER_VERTICES];

    /// The four unclipped projected corners of one particle quad, kept for the UV lookup: clipping
    /// changes the raster polygon but must not change which sprite pixel a cell samples.
    private static final double[] QSX = new double[4];

    private static final double[] QSY = new double[4];

    /// Scratch matrix, projection and bilinear result, reused every frame and every cell.
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();

    private static final Vector4f PROJECTED = new Vector4f();
    private static final double[] UV_RESULT = new double[2];

    /// Render-thread scratch for the model and box faces the check rasterises. The lists are
    /// cleared per item; the immutable Vec3 snapshots still have to be rebuilt because Vec3 has no
    /// setter.
    private static final PoseStack POSE = new PoseStack();

    private static final Vector3f TRANSFORMED = new Vector3f();
    private static final List<Vec3[]> MODEL_FACES = new ArrayList<>();
    private static final List<Vec3[]> BOX_FACES = new ArrayList<>();

    private static final double[] UV_SPAN = new double[4];
    private static final QuadShape QUAD_SHAPE = new QuadShape();

    /// One particle quad's corners and UV parameters as {@code buildLayer} emits them, render
    /// thread only.
    private static final double[] QX = new double[4];

    private static final double[] QY = new double[4];
    private static final double[] QZ = new double[4];
    private static final float[] QU = new float[4];
    private static final float[] QV = new float[4];

    /// The reflected sprite list of each particle atlas, keyed by the atlas object so a reload
    /// binds a fresh list. Null when the lookup failed once: the sprites are then treated opaque.
    private static final Map<
                    net.minecraft.client.renderer.texture.TextureAtlas,
                    List<net.minecraft.client.renderer.texture.TextureAtlasSprite>>
            ATLAS_SPRITES = new HashMap<>();

    private static boolean atlasSpritesBroken;

    /// The reflected image of each sprite the raster samples, keyed by the sprite contents.
    private static final Map<
                    net.minecraft.client.renderer.texture.SpriteContents,
                    com.mojang.blaze3d.platform.NativeImage>
            SPRITE_IMAGES = new HashMap<>();

    private static boolean spriteImagesBroken;

    /// The health the current half's last caption showed, for {@link #requireLastCaptionShown}.
    private static volatile Float LAST_CAPTION_HEALTH;

    /// The server tick the current half's last caption was pushed on, for {@link
    /// #requireLastCaptionShown}.
    private static volatile int LAST_CAPTION_TICK;

    /// The recorder's drop count when the current stage began, -1 outside a stage.
    private static int stageStartDrops = -1;

    private static final int ARENA_Y = 64;
    private static final double RANGE_X = -6.5D;
    private static final Vec3 MELEE_STAND = new Vec3(0.5, ARENA_Y, 1.5);
    private static final Vec3 MELEE_TARGET = new Vec3(0.5, ARENA_Y, 4.5);
    private static final Vec3 RANGE_STAND = new Vec3(RANGE_X, ARENA_Y, 0.5);
    private static final Vec3 RANGE_TARGET = new Vec3(RANGE_X, ARENA_Y, 4.5);
    private static final Vec3 FIREWORK_TARGET = new Vec3(RANGE_X, ARENA_Y, 8.5);
    private static final Vec3 EFFECTS_STAND = new Vec3(0.0, ARENA_Y, -2.5);
    private static final Vec3 HUNGER_STAND = new Vec3(0.0, ARENA_Y, -34.5);

    /// A potion thrown at 0.5 blocks a tick (ThrowablePotionItem.java:26 in the 26.3 sources) under
    /// a gravity of 0.05 a tick (AbstractThrownPotion.java:45-47) breaks only about five blocks
    /// out, and its splash scales Harming by how close each entity's box comes to it, the thrower's
    /// included (ThrownSplashPotion.java:45-56). From this pillar three blocks up the thrower still
    /// sits just inside the four-block reach, but at a scale under 1/12, so the harm rounds to 0
    /// (HealOrHarmMobEffect.java:40) and Player#hurtServer drops it without a hurt
    /// (Player.java:701). Each splash half asserts the thrower ends it unhurt. The pillar stands
    /// east of the arena, behind or beside the camera of every other scene.
    private static final Vec3 SPLASH_STAND = new Vec3(14.5, ARENA_Y + 3, 0.5);

    private static final Vec3 SPLASH_TARGET = new Vec3(9.5, ARENA_Y, 0.5);

    /// A weapon target survives both halves of its scenario, so no death message lands in the film.
    private static final float WEAPON_TARGET_HEALTH = 200.0F;

    /// An effect target is healthy enough to keep showing numbers for the whole scenario.
    private static final float EFFECT_TARGET_HEALTH = 300.0F;

    /// Effect targets face +Z, towards the camera south of their row.
    private static final float EFFECT_TARGET_YAW = 0.0F;

    private static final int READING_TICKS = 14;

    /// Ticks between the mace hit of a cooldown stage and the reading of the client's charge: the
    /// mace's vanilla delay is 33.3 ticks, so the indicator is an eighth full, and a tenth of that
    /// fills it.
    private static final int COOLDOWN_TICKS = 4;

    /// Ticks a cooldown stage holds its picture. At {@link #COOLDOWN_TICK_RATE} ticks a second that
    /// is six seconds: nine refills of the accelerated 3.3-tick delay, and most of one of the
    /// vanilla 33.3-tick delay.
    private static final int COOLDOWN_HOLD_TICKS = 30;

    /// Ticks between the mace hits of a cooldown stage: longer than the accelerated delay, so the
    /// indicator fills between hits with the rule on, and far shorter than the vanilla one. At
    /// {@link #COOLDOWN_TICK_RATE} ticks a second a hit lands every second, so the indicator is
    /// drawn for the whole stage: empty, filling, full, then empty again.
    private static final int COOLDOWN_CADENCE_TICKS = 5;

    /// Ticks a second the game runs at for the two cooldown stages. The accelerated refill is 3.3
    /// ticks, a sixth of a second at the vanilla 20, which a 30 fps capture shows as a flicker. At
    /// 5 ticks a second it lasts two thirds of a second, and the vanilla refill 6.7 seconds.
    private static final float COOLDOWN_TICK_RATE = 5.0F;

    /// Ticks the title card is on screen: 0.8 s at the 20 ticks a second the test has set at that
    /// point ({@code TickRateManager.java:9} is the default). A card is a still, and a still longer
    /// than 1.3 s reads as a stall; the cut adds the stage's own hold to this, so the card is
    /// shorter than the bound by that hold.
    private static final int TITLE_CARD_TICKS = 16;

    /// Ticks the client holds a spear before its own stab gate is read. The accelerated delay is
    /// 2.3 ticks, so the gate accepts the stab well inside this. The vanilla 23-tick delay would
    /// still refuse it.
    private static final int SPEAR_GATE_TICKS = 8;

    /// How many ticks the caption's health numbers may lag the server's: a number that changes on a
    /// tick must be on the client's HUD within this many ticks.
    private static final int CAPTION_LAG_TICKS = 2;

    /// How far a client's effect duration may sit from the server's. The check gives the client a
    /// duration 200 ticks above the server's, which is ten times this tolerance and twice the 100
    /// ticks the client burns over the ten-tick wait, so only a resend can bring it back. The read
    /// is taken one tick after a resend, when the client has just applied it.
    private static final int EFFECT_SYNC_TOLERANCE_TICKS = 20;

    private static final String AIR_OBJECTIVE = "accelerateddamage_air";

    /// Regeneration II, the regeneration of a strong potion of regeneration (Potions.java:87-88 in
    /// the 26.3 sources).
    private static final int REGENERATION_AMPLIFIER = 1;

    /// Long enough to outlast a half with the rules on, which burn ten ticks of it every tick. The
    /// speed effect the client-duration check reads uses the same length.
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

    /// Ticks the last caption of a half stays on screen before its end marker. The cut keeps a
    /// stage up to its marker, so a caption pushed on the marker's own tick is never drawn: the bow
    /// half fires 14 shots at 3 ticks, 42 ticks, which is already past {@link #HALF_TICKS}.
    private static final int MIN_LAST_CAPTION_TICKS = 4;

    /// Client ticks allowed for the recorder to finish a copy and count a late drop: four buffers can
    /// still be in flight after a present (MinecraftModConventions 3.5.0, GameCapture.java:37,44-48).
    private static final int CAPTURE_SETTLE_TICKS = 4;

    /// Client ticks a scene's targets may take to reach the client's world. Packets are processed at
    /// the start of a client tick ({@code Minecraft.java:1156-1161} in the 26.3 sources) and a filmed
    /// frame parks the render thread after its present, so a target can arrive after the server's
    /// reading. The wait ends as soon as the scene is in place.
    private static final int SHOT_WAIT_TICKS = 200;

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
                        "Drowning",
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

    /// The anchors of the targets in {@link #TRACKED}, published for the client thread, which must
    /// not read the map while the server thread changes it.
    private static volatile Map<UUID, Vec3> TRACKED_ANCHORS = Map.of();

    private static boolean listenersRegistered;

    /// The game tick the server is on, set at the start of every server tick.
    private static volatile int serverTick;

    /// The armor stand the projectile stages' camera sits on, or null while the camera is the
    /// recording player.
    private static volatile UUID rangeCameraStand;

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
    /// target's own tick raised its health, the health it gained in all, and, for a healer that
    /// heals from an effect, the effect's remaining duration when the half opened and closed.
    private static final class HealLog {
        private final int openTick;
        private final List<Integer> healTicks = new ArrayList<>();
        private float healed;
        private int closeTick;
        private int durationOpen = -1;
        private int durationClose = -1;
        private int amplifier;

        private HealLog(int openTick) {
            this.openTick = openTick;
        }

        private int ticks() {
            return closeTick - openTick;
        }

        private float healPerStep() {
            return healTicks.isEmpty() ? 0.0F : healed / healTicks.size();
        }

        /// Heals per tick, from the ticks between the half's first and last heal. Needs two heals.
        private double stepsPerTick() {
            return (healTicks.size() - 1) / (double) (healTicks.getLast() - healTicks.getFirst());
        }

        @Override
        public String toString() {
            return String.format(
                    Locale.ROOT,
                    "healed=%.1f ticks=%d duration=%d>%d healTicks=%s",
                    healed,
                    ticks(),
                    durationOpen,
                    durationClose,
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
        private float finalHealth;

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
            finalHealth = health;
        }

        /// The target's health when the half closed, which is what its last caption must show.
        private float finalHealth() {
            return finalHealth;
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
    /// of them heal instead of taking damage (with the effect they heal from, or empty for healing
    /// from saturation), which of them vanilla may hurt by at most four health in a half, and which
    /// of them stand under water.
    private record EffectScene(
            String id,
            String subject,
            Vec3 stand,
            Vec3 view,
            List<String> names,
            int titleNames,
            Map<String, Optional<Holder<MobEffect>>> healers,
            Set<String> slowInVanilla,
            Set<String> underWater,
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
                    Throwable original = null;
                    try {
                        ShowcaseFramePacer.enableIfCaptured();
                        playerId =
                                server.computeOnServer(
                                        current -> {
                                            preparePlayer(current);
                                            return recordingPlayerId(current);
                                        });
                        ClientGameTestServers.assertClientWorldAndPlayerAvailable(context);
                        // The performance panel (fps, TPS, heap) is a diagnostic, not part of the
                        // film: the recording keeps its numbers in its metadata.
                        ClientGameTestRecorder.placePerformanceOverlay(
                                context, PerformanceOverlayPlacement.HIDDEN);
                        requireKeysOverlay(context);
                        String spriteAlpha =
                                context.computeOnClient(
                                        AcceleratedDamageClientGameTest::spriteAlphaMappingFailure);
                        if (spriteAlpha != null) {
                            throw new AssertionError(
                                    "Expected the sprite alpha path to count an opaque particle quad: "
                                            + spriteAlpha);
                        }
                        // The film is cut to its stage markers, so the first marker has to be the
                        // first frame worth showing. The tour that loads every viewpoint runs
                        // before any marker, and the title card goes up on the melee scene, which
                        // is the first thing the film shows.
                        ClientGameTestWaits.waitForChunkWindow(context, 4, 200);
                        ClientGameTestWaits.waitForRenderSettled(context, 4, 200);
                        preloadArena(context, server, playerId);

                        recordMelee(context, server, playerId);
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
                        if (rangeCameraStand != null) {
                            leaveRangeCamera(context, server, rangeCameraStand);
                            rangeCameraStand = null;
                        }
                        requireSpearGate(context, server, playerId);
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
                    } catch (Throwable failure) {
                        original = failure;
                        throw failure;
                    } finally {
                        ShowcaseFramePacer.disable();
                        AssertionError finalViolation = null;
                        try {
                            if (rangeCameraStand != null) {
                                UUID stand = rangeCameraStand;
                                rangeCameraStand = null;
                                context.runOnClient(
                                        client -> client.setCameraEntity(client.player));
                                server.runOnServer(
                                        current -> {
                                            Entity entity = current.overworld().getEntity(stand);
                                            if (entity != null) {
                                                entity.discard();
                                            }
                                        });
                            }
                            try {
                                requireNoEntityFillsTheFrame("showcase.final");
                            } catch (AssertionError violation) {
                                finalViolation = violation;
                            }
                        } finally {
                            TRACKED.clear();
                            publishTrackedIds();
                            server.runOnServer(AcceleratedDamageClientGameTest::restoreState);
                        }
                        if (finalViolation != null) {
                            if (original != null) {
                                original.addSuppressed(finalViolation);
                            } else {
                                throw finalViolation;
                            }
                        }
                    }
                });
    }

    /// Visits every viewpoint before the film starts, so a scenario never opens on a chunk the
    /// client has not built yet. The recorder keeps running from the first frame, so a teleport
    /// later costs only a few ticks.
    private static void preloadArena(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        List<Vec3> viewpoints = List.of(EFFECTS_STAND, SPLASH_STAND, HUNGER_STAND, RANGE_STAND);
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
        // Both ends of the client tick: a target moved by a packet before the tick is put back
        // before the tick, and the frame is drawn after the tick ends.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK
                .register(AcceleratedDamageClientGameTest::pinTargetsToTheirAnchors);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
                AcceleratedDamageClientGameTest::pinTargetsToTheirAnchors);
        LevelExtractionEvents.END_EXTRACTION.register(
                AcceleratedDamageClientGameTest::recordFrameCoverage);
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

    /// Puts every target's client copy back on its anchor.
    ///
    /// <p>A target's model is drawn at the position lerped from its old position ({@code
    /// EntityRenderer.java:163-165} in the 26.3 sources). The client's copy of a target that was
    /// teleported into the scene interpolates from where it was, and the line it is drawn along
    /// passes through the camera, so the body fills the frame. Cancelling the interpolation alone
    /// is not enough: it freezes the copy wherever the interpolation had already moved it, which
    /// can be at the camera. The copy is put back on the anchor the server pins it to, and its old
    /// position with it, so the lerp is a point. The server's target is untouched.
    private static void pinTargetsToTheirAnchors(Minecraft client) {
        if (client.level == null) {
            return;
        }
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof net.minecraft.world.entity.player.Player player)) {
                continue;
            }
            Vec3 anchor = TRACKED_ANCHORS.get(player.getUUID());
            if (anchor == null) {
                continue;
            }
            player.getInterpolation().cancel();
            player.setPos(anchor.x, anchor.y, anchor.z);
            player.setOldPosAndRot();
        }
    }

    /// The faces of an arrow's or a trident's model as its renderer draws them, relative to the
    /// camera, or null for any other entity.
    private static List<Vec3[]> modelFaces(Minecraft client, EntityRenderState state, Vec3 eye) {
        PoseStack pose = POSE;
        pose.setIdentity();
        pose.translate(state.x - eye.x, state.y - eye.y, state.z - eye.z);
        ModelLayerLocation layer;
        if (state instanceof ArrowRenderState arrow) {
            // ArrowRenderer.submit's own pose (ArrowRenderer.java:23-25).
            pose.rotateDegrees(Axis.YP, arrow.yRot - 90.0F);
            pose.rotateDegrees(Axis.ZP, arrow.xRot);
            layer = ModelLayers.ARROW;
        } else if (state instanceof ThrownTridentRenderState trident) {
            // ThrownTridentRenderer.submit's own pose (ThrownTridentRenderer.java:30-31).
            pose.rotateDegrees(Axis.YP, trident.yRot - 90.0F);
            pose.rotateDegrees(Axis.ZP, trident.xRot + 90.0F);
            layer = ModelLayers.TRIDENT;
        } else {
            return null;
        }
        if (modelsOwner != client.getEntityModels()) {
            MODELS.clear();
            modelsOwner = client.getEntityModels();
        }
        List<Vec3[]> faces = MODEL_FACES;
        faces.clear();
        MODELS.computeIfAbsent(layer, client.getEntityModels()::bakeLayer)
                .visit(
                        pose,
                        (part, _, _, cube) -> {
                            for (ModelPart.Polygon polygon : cube.polygons) {
                                Vec3[] face = new Vec3[polygon.vertices().length];
                                for (int index = 0; index < face.length; index++) {
                                    ModelPart.Vertex vertex = polygon.vertices()[index];
                                    Vector3f point =
                                            part.pose()
                                                    .transformPosition(
                                                            vertex.worldX(),
                                                            vertex.worldY(),
                                                            vertex.worldZ(),
                                                            TRANSFORMED);
                                    face[index] = new Vec3(point.x, point.y, point.z);
                                }
                                faces.add(face);
                            }
                        });
        return faces;
    }

    /// Fails a stage whose frame an entity fills more than a quarter of.
    ///
    /// <p>{@link #recordFrameCoverage} measures the render states of every frame the client draws.
    /// The check runs at every stage marker and after every shot, so a body across the lens fails
    /// the run instead of reaching the film.
    private static void requireNoEntityFillsTheFrame(String stage) {
        String violation = LAST_FRAME_VIOLATION;
        LAST_FRAME_VIOLATION = null;
        if (violation != null) {
            throw new AssertionError(
                    "Expected no entity to fill the frame in " + stage + ": " + violation);
        }
    }

    /// Fails unless the recording's keys display is on: the film is watched to see what the player
    /// did, and a take whose client loaded a conventions jar without the overlay has no caps in any
    /// frame. The overlay draws only while a recording session is enabled, so this also proves the
    /// take is a recording.
    private static void requireKeysOverlay(ClientGameTestContext context) {
        // Reflected, not imported: the gate builds against the published conventions jar, which
        // has no InputOverlay, and a direct reference would not compile there. The session class is
        // in every conventions jar this test can build against, so its absence is a broken build,
        // not a plain run: a plain run has the class and reports the session off, and the check
        // then passes, because the overlay draws only in a recording.
        boolean recording =
                context.computeOnClient(
                        _ -> {
                            try {
                                Class<?> session =
                                        Class.forName(
                                                "io.github.brainage04.minecraftmodconventions"
                                                        + ".ClientGameTestRecordingSession");
                                return (Boolean) session.getMethod("isEnabled").invoke(null);
                            } catch (ClassNotFoundException absent) {
                                throw new AssertionError(
                                        "Expected the client's conventions jar to have"
                                                + " ClientGameTestRecordingSession",
                                        absent);
                            } catch (ReflectiveOperationException broken) {
                                throw new AssertionError(
                                        "Expected ClientGameTestRecordingSession.isEnabled to be"
                                                + " readable",
                                        broken);
                            }
                        });
        if (!recording) {
            return;
        }
        boolean on =
                context.computeOnClient(
                        _ -> {
                            try {
                                Class<?> overlay =
                                        Class.forName(
                                                "io.github.brainage04.minecraftmodconventions"
                                                        + ".InputOverlay");
                                return (Boolean) overlay.getMethod("isEnabled").invoke(null);
                            } catch (ReflectiveOperationException absent) {
                                return false;
                            }
                        });
        if (!on) {
            throw new AssertionError(
                    "Expected the recording's keys display to be on: the client's conventions jar has"
                            + " no InputOverlay, or the recording session is not enabled");
        }
    }

    /// Proves the alpha path counts an opaque textured particle quad. It rasterises one
    /// screen-filling quad whose atlas UV is pinned to an opaque sprite pixel. The atlas coordinate
    /// itself points at a different, transparent pixel, so the old atlas-UV readout counts no cell
    /// while the sprite-local mapping counts the whole quad. Returns a violation, or null when the
    /// path works.
    @SuppressWarnings("unchecked")
    private static String spriteAlphaMappingFailure(Minecraft client) {
        try {
            net.minecraft.client.renderer.texture.AbstractTexture texture =
                    client.getTextureManager()
                            .getTexture(
                                    SingleQuadParticle.Layer.TRANSLUCENT.textureAtlasLocation());
            if (!(texture instanceof net.minecraft.client.renderer.texture.TextureAtlas atlas)) {
                return "no particle texture atlas";
            }
            java.lang.reflect.Field field =
                    net.minecraft.client.renderer.texture.TextureAtlas.class.getDeclaredField(
                            "sprites");
            field.setAccessible(true);
            List<net.minecraft.client.renderer.texture.TextureAtlasSprite> sprites =
                    (List<net.minecraft.client.renderer.texture.TextureAtlasSprite>)
                            field.get(atlas);
            for (net.minecraft.client.renderer.texture.TextureAtlasSprite sprite : sprites) {
                net.minecraft.client.renderer.texture.SpriteContents contents = sprite.contents();
                com.mojang.blaze3d.platform.NativeImage image = spriteImage(contents);
                if (image == null || contents.width() < 2 || contents.height() < 2) {
                    continue;
                }
                int px = contents.width() / 2;
                int py = contents.height() / 2;
                if (net.minecraft.util.ARGB.alpha(image.getPixel(px, py)) != 255) {
                    continue;
                }
                double u =
                        sprite.getU0()
                                + (px + 0.5) / contents.width() * (sprite.getU1() - sprite.getU0());
                double v =
                        sprite.getV0()
                                + (py + 0.5)
                                        / contents.height()
                                        * (sprite.getV1() - sprite.getV0());
                int oldPx =
                        Math.min(
                                contents.width() - 1,
                                Math.max(0, (int) Math.round(u * contents.width() - 0.5)));
                int oldPy =
                        Math.min(
                                contents.height() - 1,
                                Math.max(0, (int) Math.round(v * contents.height() - 0.5)));
                if (net.minecraft.util.ARGB.alpha(image.getPixel(oldPx, oldPy)) == 255) {
                    continue;
                }
                QSX[0] = -0.8;
                QSY[0] = -0.8;
                QSX[1] = 0.8;
                QSY[1] = -0.8;
                QSX[2] = 0.8;
                QSY[2] = 0.8;
                QSX[3] = -0.8;
                QSY[3] = 0.8;
                for (int corner = 0; corner < 4; corner++) {
                    RNX[corner] = QSX[corner];
                    RNY[corner] = QSY[corner];
                }
                UV_SPAN[0] = u;
                UV_SPAN[1] = u;
                UV_SPAN[2] = v;
                UV_SPAN[3] = v;
                coverageEpoch++;
                int cells = rasterize(4, QUAD_SHAPE, sprite, UV_SPAN);
                coverageEpoch++;
                if (cells == 0) {
                    return "the opaque pixel at "
                            + px
                            + ","
                            + py
                            + " counted no cell; the atlas pixel "
                            + oldPx
                            + ","
                            + oldPy
                            + " is transparent";
                }
                return null;
            }
            return "no particle sprite has an opaque centre and a transparent atlas-mapped pixel";
        } catch (ReflectiveOperationException failure) {
            return "the particle atlas sprites could not be read: " + failure;
        }
    }

    /// Records when the entities and particles of a drawn frame cover more than the threshold of
    /// the frame between them, for {@link #requireNoEntityFillsTheFrame}.
    ///
    /// <p>Measures the render states the frame draws, after the frame's entities and particles are
    /// extracted with the camera already aligned for that frame ({@code GameRenderer.update} before
    /// {@code GameRenderer.extract}, {@code Minecraft.java:1277-1282} in the 26.3 sources;
    /// particles at {@code LevelExtractor.java:214}). Frames are drawn between ticks too: the
    /// client handles its packets every frame and ticks only when a tick is due ({@code
    /// Minecraft.java:1156-1180}), so a projectile added or moved by a packet is drawn before any
    /// tick hook can see it.
    ///
    /// <p>Every item is rasterised onto a coarse grid and what fails is their union, not any one of
    /// them: the defect this check exists for was many overlapping crit squares, no one of which
    /// would have failed alone. A particle is only counted where its sprite is drawn: the quad of a
    /// sword's sweep, two blocks wide a block ahead of the player ({@code Player.java:1178-1180},
    /// {@code AttackSweepParticle.java:18}), covers half the frame while its thin arc draws almost
    /// none of it. A particle whose sprite cannot be read counts as opaque. {@code
    /// ElderGuardianParticleGroup} and {@code ItemPickupParticleGroup}, the other {@code
    /// ParticleGroupRenderState} types, are not measured; neither is in the film. An invisible
    /// entity draws nothing but a worn item ({@code ArmorStandRenderer}), so it is not measured.
    private static void recordFrameCoverage(LevelExtractionContext context) {
        Minecraft client = Minecraft.getInstance();
        Camera camera = context.camera();
        Matrix4f viewProjection = camera.getViewRotationProjectionMatrix(VIEW_PROJECTION);
        Vec3 eye = camera.position();
        Vector3fc forwardVector = camera.forwardVector();
        Vec3 ahead = new Vec3(forwardVector.x(), forwardVector.y(), forwardVector.z());
        coverageEpoch++;
        int largest = 0;
        String largestItem = null;
        for (EntityRenderState state : context.levelState().entityRenderStates) {
            if (state.isInvisible) {
                continue;
            }
            int cells = 0;
            for (Vec3[] face : drawnFaces(client, state, eye)) {
                int clipped = clipFace(face, null, null, ahead, viewProjection);
                cells += rasterize(clipped, null, null, null);
            }
            if (cells > largest) {
                largest = cells;
                largestItem =
                        String.format(
                                Locale.ROOT,
                                "%s (age %.2f ticks) at %s",
                                state.entityType,
                                state.ageInTicks,
                                new Vec3(state.x, state.y, state.z).subtract(eye));
            }
        }
        for (ParticleGroupRenderState group : context.levelState().particlesRenderState.particles) {
            if (!(group instanceof QuadParticleRenderState quads)) {
                continue;
            }
            for (SingleQuadParticle.Layer layer : quads.layers()) {
                QuadCoverage coverage = new QuadCoverage(client, layer, ahead, viewProjection);
                quads.buildLayer(layer, coverage);
                if (coverage.largest > largest) {
                    largest = coverage.largest;
                    largestItem = coverage.largestItem;
                }
            }
        }
        double covered = largestCoverage() / (double) (COVERAGE_GRID_WIDTH * COVERAGE_GRID_HEIGHT);
        if (covered > MAX_ENTITY_SCREEN_COVERAGE) {
            LAST_FRAME_VIOLATION =
                    String.format(
                            Locale.ROOT,
                            "entities and particles cover %.0f%% of the frame from the camera at"
                                    + " %s, largest item %s",
                            covered * 100.0,
                            eye,
                            largestItem);
        }
    }

    /// The number of grid cells the frame has covered so far.
    private static int largestCoverage() {
        int cells = 0;
        for (int cell : COVERAGE_GRID) {
            if (cell == coverageEpoch) {
                cells++;
            }
        }
        return cells;
    }

    /// Clips a polygon at the camera's near plane and projects the vertices it keeps into the
    /// raster scratch arrays. The input positions are relative to the camera; {@code paramA} and
    /// {@code paramB} are the quad parameters of a particle's corners, or null for an opaque item.
    /// Returns the clipped vertex count, zero when the polygon never reaches past the near plane.
    ///
    /// <p>The renderer draws each face clipped at the near plane, so a face that straddles the
    /// plane is drawn from the plane outwards: a body a third of a block in front of the camera
    /// fills the frame. Clipping first is what makes that measurable; projecting only the corners
    /// in front would miss it, and treating every straddling face as full would fail a body beside
    /// the lens.
    private static int clipFace(
            Vec3[] points, double[] paramA, double[] paramB, Vec3 ahead, Matrix4f viewProjection) {
        int count = 0;
        for (int index = 0; index < points.length && count < MAX_RASTER_VERTICES; index++) {
            Vec3 a = points[index];
            Vec3 b = points[(index + 1) % points.length];
            double depthA = a.dot(ahead) - NEAR_PLANE;
            double depthB = b.dot(ahead) - NEAR_PLANE;
            if (depthA >= 0.0) {
                projectVertex(
                        a.x,
                        a.y,
                        a.z,
                        paramA == null ? 0.0 : paramA[index],
                        paramA == null ? 0.0 : paramB[index],
                        count);
                count++;
            }
            if ((depthA >= 0.0) != (depthB >= 0.0) && count < MAX_RASTER_VERTICES) {
                double t = depthA / (depthA - depthB);
                double pa = 0.0;
                double pb = 0.0;
                if (paramA != null) {
                    pa = paramA[index] + t * (paramA[(index + 1) % points.length] - paramA[index]);
                    pb = paramB[index] + t * (paramB[(index + 1) % points.length] - paramB[index]);
                }
                projectVertex(
                        a.x + (b.x - a.x) * t,
                        a.y + (b.y - a.y) * t,
                        a.z + (b.z - a.z) * t,
                        pa,
                        pb,
                        count);
                count++;
            }
        }
        if (count < 3) {
            return 0;
        }
        for (int vertex = 0; vertex < count; vertex++) {
            PROJECTED.set((float) RWX[vertex], (float) RWY[vertex], (float) RWZ[vertex], 1.0F);
            viewProjection.transform(PROJECTED);
            if (PROJECTED.w <= 0.0F) {
                return 0;
            }
            RIW[vertex] = 1.0F / PROJECTED.w;
            RNX[vertex] = PROJECTED.x * RIW[vertex];
            RNY[vertex] = PROJECTED.y * RIW[vertex];
        }
        return count;
    }

    private static void projectVertex(
            double x, double y, double z, double paramA, double paramB, int vertex) {
        RWX[vertex] = x;
        RWY[vertex] = y;
        RWZ[vertex] = z;
        RPA[vertex] = paramA;
        RPB[vertex] = paramB;
    }

    /// Projects the four unclipped corners into the UV scratch arrays. A corner behind the camera
    /// has no screen position, so the caller then leaves the quad fully opaque rather than guess
    /// its sprite pixels.
    private static boolean projectQuadCorners(Vec3[] corners, Matrix4f viewProjection) {
        for (int vertex = 0; vertex < 4; vertex++) {
            Vec3 corner = corners[vertex];
            PROJECTED.set((float) corner.x, (float) corner.y, (float) corner.z, 1.0F);
            viewProjection.transform(PROJECTED);
            if (PROJECTED.w <= 0.0F) {
                return false;
            }
            float inverse = 1.0F / PROJECTED.w;
            QSX[vertex] = PROJECTED.x * inverse;
            QSY[vertex] = PROJECTED.y * inverse;
        }
        return true;
    }

    /// Whether the sprite's pixel at (x, y) is opaque. A particle is measured for what it hides: a
    /// translucent pixel tints what is behind it and the scene shows through, so only a fully
    /// opaque pixel hides the frame. {@code SpriteContents.isTransparent} answers only for a fully
    /// transparent pixel and keeps the image private, so the alpha is read through the same
    /// reflection as the atlas's sprite list, at frame zero ({@code SpriteContents.java:212-221} in
    /// the 26.3 sources; the film's animated sprites do not change their coverage between frames).
    /// A pixel whose image cannot be read counts as opaque.
    private static boolean spriteOpaque(
            net.minecraft.client.renderer.texture.SpriteContents sprite, int x, int y) {
        com.mojang.blaze3d.platform.NativeImage image = spriteImage(sprite);
        if (image == null) {
            return true;
        }
        int colour = image.getPixel(x, y);
        return net.minecraft.util.ARGB.alpha(colour) == 255;
    }

    /// The sprite's image, reflected once per sprite. Vanilla reads it the same way in {@code
    /// SpriteContents.isTransparent}.
    private static com.mojang.blaze3d.platform.NativeImage spriteImage(
            net.minecraft.client.renderer.texture.SpriteContents sprite) {
        if (SPRITE_IMAGES.containsKey(sprite)) {
            return SPRITE_IMAGES.get(sprite);
        }
        com.mojang.blaze3d.platform.NativeImage image = null;
        if (!spriteImagesBroken) {
            try {
                java.lang.reflect.Field field =
                        net.minecraft.client.renderer.texture.SpriteContents.class.getDeclaredField(
                                "originalImage");
                field.setAccessible(true);
                image = (com.mojang.blaze3d.platform.NativeImage) field.get(sprite);
            } catch (ReflectiveOperationException | ClassCastException failure) {
                spriteImagesBroken = true;
            }
        }
        SPRITE_IMAGES.put(sprite, image);
        return image;
    }

    /// Marks the grid cells a clipped, projected convex polygon covers. A convex polygon meets a
    /// scanline in one span, so each row is filled between the edge crossings of its centre. Cells
    /// outside the screen fall away by clamping. For a particle, {@code sprite} is its sprite (or
    /// null to count it opaque) and a cell is only marked where the sprite's pixel at the cell is
    /// drawn, read at the quad's UV interpolated into the cell. Returns the cells this item newly
    /// covered.
    private static int rasterize(
            int count,
            QuadShape quad,
            net.minecraft.client.renderer.texture.TextureAtlasSprite atlasSprite,
            double[] uvSpan) {
        if (count < 3) {
            return 0;
        }
        int marked = 0;
        for (int row = 0; row < COVERAGE_GRID_HEIGHT; row++) {
            double y = (row + 0.5) * 2.0 / COVERAGE_GRID_HEIGHT - 1.0;
            double xMin = Double.MAX_VALUE;
            double xMax = -Double.MAX_VALUE;
            for (int vertex = 0; vertex < count; vertex++) {
                int next = (vertex + 1) % count;
                double y0 = RNY[vertex];
                double y1 = RNY[next];
                if ((y0 <= y && y1 > y) || (y1 <= y && y0 > y)) {
                    double t = (y - y0) / (y1 - y0);
                    double x = RNX[vertex] + t * (RNX[next] - RNX[vertex]);
                    xMin = Math.min(xMin, x);
                    xMax = Math.max(xMax, x);
                }
            }
            if (xMin >= xMax) {
                continue;
            }
            int first =
                    Math.max(
                            0,
                            (int) Math.floor((xMin + 1.0) * 0.5 * COVERAGE_GRID_WIDTH - 0.5 + 1.0));
            int last =
                    Math.min(
                            COVERAGE_GRID_WIDTH - 1,
                            (int) Math.ceil((xMax + 1.0) * 0.5 * COVERAGE_GRID_WIDTH - 0.5 - 1.0));
            for (int col = first; col <= last; col++) {
                double x = (col + 0.5) * 2.0 / COVERAGE_GRID_WIDTH - 1.0;
                if (x < xMin || x > xMax) {
                    continue;
                }
                if (quad != null && atlasSprite != null) {
                    if (!quad.inverseBilinear(x, y)) {
                        continue;
                    }
                    double u = uvSpan[0] + UV_RESULT[0] * (uvSpan[1] - uvSpan[0]);
                    double v = uvSpan[3] + UV_RESULT[1] * (uvSpan[2] - uvSpan[3]);
                    double uSpan = atlasSprite.getU1() - atlasSprite.getU0();
                    double vSpan = atlasSprite.getV1() - atlasSprite.getV0();
                    if (uSpan > SPRITE_UV_TOLERANCE && vSpan > SPRITE_UV_TOLERANCE) {
                        // The renderer's UVs are atlas coordinates; the image is indexed from the
                        // sprite's own corner (TextureAtlasSprite.java:30-33, 26.3).
                        double localU = (u - atlasSprite.getU0()) / uSpan;
                        double localV = (v - atlasSprite.getV0()) / vSpan;
                        net.minecraft.client.renderer.texture.SpriteContents sprite =
                                atlasSprite.contents();
                        int px =
                                Math.min(
                                        sprite.width() - 1,
                                        Math.max(0, (int) Math.floor(localU * sprite.width())));
                        int py =
                                Math.min(
                                        sprite.height() - 1,
                                        Math.max(0, (int) Math.floor(localV * sprite.height())));
                        if (!spriteOpaque(sprite, px, py)) {
                            continue;
                        }
                    }
                }
                int cell = row * COVERAGE_GRID_WIDTH + col;
                if (COVERAGE_GRID[cell] != coverageEpoch) {
                    COVERAGE_GRID[cell] = coverageEpoch;
                    marked++;
                }
            }
        }
        return marked;
    }

    /// One particle quad's projected corners, for the inverse bilinear lookup that reads a cell's
    /// UV: corner 0 sits at parameters (1, 0), corner 1 at (1, 1), corner 2 at (0, 1) and corner 3
    /// at (0, 0), the vertex order {@code QuadParticleRenderState.renderRotatedQuad} emits ({@code
    /// QuadParticleRenderState.java:76-81} in the 26.3 sources). The bilinear inversion is Newton's
    /// from the centre; a quad's projection is close to planar, so three iterations are far inside
    /// a cell.
    private static final class QuadShape {
        /// The (a, b) parameters of the screen point (x, y) in normalised device coordinates are
        /// written to {@link #UV_RESULT}, or false when the point is outside the quad or the
        /// inversion does not converge. The corners are the unclipped projected corners in {@link
        /// #QSX}/{@link #QSY}, so clipping cannot change which sprite pixel a cell samples.
        private boolean inverseBilinear(double x, double y) {
            double a0 = QSX[3];
            double bx = QSX[2] - QSX[3];
            double cx = QSX[0] - QSX[3];
            double dx = QSX[1] - QSX[2] - QSX[0] + QSX[3];
            double e0 = QSY[3];
            double fy = QSY[2] - QSY[3];
            double gx = QSY[0] - QSY[3];
            double hx = QSY[1] - QSY[2] - QSY[0] + QSY[3];
            double a = 0.5;
            double b = 0.5;
            for (int iteration = 0; iteration < BILINEAR_ITERATIONS; iteration++) {
                double px = a0 + bx * b + cx * a + dx * a * b - x;
                double py = e0 + fy * b + gx * a + hx * a * b - y;
                if (px * px + py * py < BILINEAR_EPSILON) {
                    break;
                }
                double dpa = cx + dx * b;
                double dpb = bx + dx * a;
                double dqa = gx + hx * b;
                double dqb = fy + hx * a;
                double determinant = dpa * dqb - dpb * dqa;
                if (Math.abs(determinant) < BILINEAR_EPSILON) {
                    return false;
                }
                double da = (px * dqb - py * dpb) / determinant;
                double db = (py * dpa - px * dqa) / determinant;
                a -= da;
                b -= db;
            }
            if (!(a >= -QUAD_UV_MARGIN
                    && a <= 1.0 + QUAD_UV_MARGIN
                    && b >= -QUAD_UV_MARGIN
                    && b <= 1.0 + QUAD_UV_MARGIN)) {
                return false;
            }
            UV_RESULT[0] = Math.min(1.0, Math.max(0.0, a));
            UV_RESULT[1] = Math.min(1.0, Math.max(0.0, b));
            return true;
        }
    }

    /// Collects the particle quads {@code QuadParticleRenderState.buildLayer} emits ({@code
    /// QuadParticleRenderState.java:85-108} in the 26.3 sources), four corners to a quad, and
    /// rasterises each as it completes: positions relative to the camera ({@code
    /// SingleQuadParticle.java:66-69}), with the UV the renderer interpolates over the quad.
    private static final class QuadCoverage implements VertexConsumer {
        private final Minecraft client;
        private final SingleQuadParticle.Layer layer;
        private final Vec3 ahead;
        private final Matrix4f viewProjection;
        private final Map<String, net.minecraft.client.renderer.texture.TextureAtlasSprite>
                sprites = new HashMap<>();
        private int vertex;
        private int largest;
        private String largestItem;

        private QuadCoverage(
                Minecraft client,
                SingleQuadParticle.Layer layer,
                Vec3 ahead,
                Matrix4f viewProjection) {
            this.client = client;
            this.layer = layer;
            this.ahead = ahead;
            this.viewProjection = viewProjection;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            QX[vertex] = x;
            QY[vertex] = y;
            QZ[vertex] = z;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            QU[vertex] = u;
            QV[vertex] = v;
            vertex++;
            if (vertex == 4) {
                vertex = 0;
                rasterizeQuad();
            }
            return this;
        }

        private void rasterizeQuad() {
            Vec3[] corners = new Vec3[4];
            double[] paramA = new double[4];
            double[] paramB = new double[4];
            for (int index = 0; index < 4; index++) {
                corners[index] = new Vec3(QX[index], QY[index], QZ[index]);
            }
            paramA[0] = 1.0;
            paramA[1] = 1.0;
            paramA[2] = 0.0;
            paramA[3] = 0.0;
            paramB[0] = 0.0;
            paramB[1] = 1.0;
            paramB[2] = 1.0;
            paramB[3] = 0.0;
            // The UV lookup needs the four original projected corners: clipping changes the raster
            // polygon, not which sprite pixel a screen point samples.
            boolean projected = projectQuadCorners(corners, viewProjection);
            int clipped = clipFace(corners, paramA, paramB, ahead, viewProjection);
            if (clipped < 3) {
                return;
            }
            double u0 = Math.min(Math.min(QU[0], QU[1]), Math.min(QU[2], QU[3]));
            double u1 = Math.max(Math.max(QU[0], QU[1]), Math.max(QU[2], QU[3]));
            double v0 = Math.min(Math.min(QV[0], QV[1]), Math.min(QV[2], QV[3]));
            double v1 = Math.max(Math.max(QV[0], QV[1]), Math.max(QV[2], QV[3]));
            UV_SPAN[0] = u0;
            UV_SPAN[1] = u1;
            UV_SPAN[2] = v0;
            UV_SPAN[3] = v1;
            QuadShape shape = projected ? QUAD_SHAPE : null;
            net.minecraft.client.renderer.texture.TextureAtlasSprite atlasSprite =
                    spriteFor(u0, u1, v0, v1);
            int cells = rasterize(clipped, shape, atlasSprite, shape == null ? null : UV_SPAN);
            if (cells > largest) {
                largest = cells;
                largestItem =
                        String.format(
                                Locale.ROOT,
                                "a particle at (%.2f, %.2f, %.2f)",
                                (QX[0] + QX[1] + QX[2] + QX[3]) / 4.0,
                                (QY[0] + QY[1] + QY[2] + QY[3]) / 4.0,
                                (QZ[0] + QZ[1] + QZ[2] + QZ[3]) / 4.0);
            }
        }

        /// The sprite of the quad's layer that holds the quad's UV rectangle, or null when the
        /// sprite cannot be read: the quad then counts as opaque. The atlas keeps its sprites
        /// private, so the list is read once per atlas through reflection.
        private net.minecraft.client.renderer.texture.TextureAtlasSprite spriteFor(
                double u0, double u1, double v0, double v1) {
            String key =
                    String.format(Locale.ROOT, "%.5f,%.5f,%.5f,%.5f", u0, v0, u1 - u0, v1 - v0);
            if (sprites.containsKey(key)) {
                return sprites.get(key);
            }
            net.minecraft.client.renderer.texture.TextureAtlasSprite found = null;
            for (net.minecraft.client.renderer.texture.TextureAtlasSprite sprite : atlasSprites()) {
                if (u0 >= sprite.getU0() - SPRITE_UV_TOLERANCE
                        && u1 <= sprite.getU1() + SPRITE_UV_TOLERANCE
                        && v0 >= sprite.getV0() - SPRITE_UV_TOLERANCE
                        && v1 <= sprite.getV1() + SPRITE_UV_TOLERANCE) {
                    found = sprite;
                    break;
                }
            }
            sprites.put(key, found);
            return found;
        }

        @SuppressWarnings("unchecked")
        private List<net.minecraft.client.renderer.texture.TextureAtlasSprite> atlasSprites() {
            net.minecraft.client.renderer.texture.AbstractTexture texture =
                    client.getTextureManager().getTexture(layer.textureAtlasLocation());
            if (!(texture instanceof net.minecraft.client.renderer.texture.TextureAtlas atlas)) {
                return List.of();
            }
            List<net.minecraft.client.renderer.texture.TextureAtlasSprite> cached =
                    ATLAS_SPRITES.get(atlas);
            if (cached != null) {
                return cached;
            }
            List<net.minecraft.client.renderer.texture.TextureAtlasSprite> list = List.of();
            if (!atlasSpritesBroken) {
                try {
                    java.lang.reflect.Field field =
                            net.minecraft.client.renderer.texture.TextureAtlas.class
                                    .getDeclaredField("sprites");
                    field.setAccessible(true);
                    list =
                            List.copyOf(
                                    (List<net.minecraft.client.renderer.texture.TextureAtlasSprite>)
                                            field.get(atlas));
                } catch (ReflectiveOperationException | ClassCastException failure) {
                    atlasSpritesBroken = true;
                    list = List.of();
                }
            }
            ATLAS_SPRITES.put(atlas, list);
            return list;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }

    /// The faces the renderer draws for a render state, relative to the camera. An arrow or a
    /// trident is measured by its own model, which reaches far behind its position (an arrow 0.68
    /// of a block, {@code ArrowModel.java:23-31}; a trident 1.69, its 27-pixel pole, {@code
    /// TridentModel.java:24}), so its box misses the tail drawn at the lens. Any other entity is
    /// measured by its box.
    private static List<Vec3[]> drawnFaces(Minecraft client, EntityRenderState state, Vec3 eye) {
        List<Vec3[]> model = modelFaces(client, state, eye);
        if (model != null) {
            return model;
        }
        Vec3 offset = new Vec3(state.x, state.y, state.z).subtract(eye);
        List<Vec3[]> faces = BOX_FACES;
        faces.clear();
        // The entity's box, from the size EntityRenderer copies into the state
        // (EntityRenderer.java:168-169), standing on its position and centred on x and z.
        double halfWidth = state.boundingBoxWidth / 2.0;
        double[] xs = {offset.x - halfWidth, offset.x + halfWidth};
        double[] ys = {offset.y, offset.y + state.boundingBoxHeight};
        double[] zs = {offset.z - halfWidth, offset.z + halfWidth};
        for (int side = 0; side < 2; side++) {
            faces.add(
                    new Vec3[] {
                        new Vec3(xs[side], ys[0], zs[0]), new Vec3(xs[side], ys[1], zs[0]),
                        new Vec3(xs[side], ys[1], zs[1]), new Vec3(xs[side], ys[0], zs[1])
                    });
            faces.add(
                    new Vec3[] {
                        new Vec3(xs[0], ys[side], zs[0]), new Vec3(xs[1], ys[side], zs[0]),
                        new Vec3(xs[1], ys[side], zs[1]), new Vec3(xs[0], ys[side], zs[1])
                    });
            faces.add(
                    new Vec3[] {
                        new Vec3(xs[0], ys[0], zs[side]), new Vec3(xs[1], ys[0], zs[side]),
                        new Vec3(xs[1], ys[1], zs[side]), new Vec3(xs[0], ys[1], zs[side])
                    });
        }
        return faces;
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
                publishTrackedIds();
                continue;
            }
            if (FED.contains(target.getId())) {
                target.getFoodData().setFoodLevel(20);
                target.getFoodData().setSaturation(20.0F);
            }
            float before = target.getHealth();
            target.doTick();
            boolean underWater = target.isEyeInFluid(FluidTags.WATER);
            // Targets are invulnerable until their marker; holding the air full until then keeps
            // the shown air from jumping back to full at the marker.
            if (underWater && target.isInvulnerable()) {
                target.setAirSupply(target.getMaxAirSupply());
            }
            showAir(server, target, underWater);
            HealLog heals = HEAL_LOGS.get(target.getId());
            if (heals != null && target.getHealth() > before) {
                heals.healTicks.add(serverTick);
                heals.healed += target.getHealth() - before;
            }
            if (tracked.anchor() != null) {
                // A pinned target takes no knockback at all: a hit that moved it and was snapped
                // back in the same tick still reaches the client as two positions, and the client
                // interpolates the target through the camera between them.
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(tracked.anchor().x, tracked.anchor().y, tracked.anchor().z);
            }
            target.setCustomName(
                    Component.literal(
                            tracked.name() + " " + Math.round(Math.max(0.0F, target.getHealth()))));
        }
    }

    /// Puts a target's air on its below-name line while its eyes are under water, and takes the
    /// line away otherwise. The client draws a player-shaped target's profile name as its name tag,
    /// never its custom name (client Player.java:1633-1635, 1682-1684 and
    /// EntityRenderer.java:140-141 in the 26.3 sources), so a reading can only show on the
    /// below-name scoreboard line (client Entity.java:3422-3430).
    private static void showAir(MinecraftServer server, ServerPlayer target, boolean underWater) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective air = scoreboard.getObjective(AIR_OBJECTIVE);
        if (air == null) {
            return;
        }
        if (underWater) {
            scoreboard.getOrCreatePlayerScore(target, air).set(Math.max(0, target.getAirSupply()));
        } else {
            scoreboard.resetSinglePlayerScore(target, air);
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
        Scoreboard scoreboard = server.getScoreboard();
        scoreboard.setDisplayObjective(
                DisplaySlot.BELOW_NAME,
                scoreboard.addObjective(
                        AIR_OBJECTIVE,
                        ObjectiveCriteria.DUMMY,
                        Component.literal("air"),
                        ObjectiveCriteria.RenderType.INTEGER,
                        false,
                        null));
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
        // The drowning target's tank: two blocks of water, so its eyes are under the surface, in
        // a glass ring that holds the water in.
        for (int y = ARENA_Y; y <= ARENA_Y + 1; y++) {
            setBlock(level, -2, y, -18, Blocks.WATER);
            setBlock(level, -3, y, -18, Blocks.GLASS);
            setBlock(level, -1, y, -18, Blocks.GLASS);
            setBlock(level, -2, y, -17, Blocks.GLASS);
            setBlock(level, -2, y, -19, Blocks.GLASS);
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
        stageStartDrops = ShowcaseFramePacer.droppedFrames();
        System.out.println("CAPTURE_DROPS " + id + " start " + stageStartDrops);
        ClientGameTestRecorder.showStep(context, id, title, subtitle);
        requireNoEntityFillsTheFrame(id);
    }

    private static void closeStage(ClientGameTestContext context, String id, int holdTicks) {
        ClientGameTestStages.endStage(context, id, holdTicks);
        // Frames after the last caption push still belong to this stage; read the sticky violation
        // here so a cooldown frame is not named after the next stage, and so the tail is judged.
        requireNoEntityFillsTheFrame(id);
        context.waitTicks(CAPTURE_SETTLE_TICKS);
        int endDrops = ShowcaseFramePacer.droppedFrames();
        System.out.println("CAPTURE_DROPS " + id + " end " + endDrops);
        if (stageStartDrops >= 0 && endDrops > stageStartDrops) {
            throw new AssertionError(
                    "Recorder dropped " + (endDrops - stageStartDrops) + " frames in " + id);
        }
        stageStartDrops = -1;
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

    /// Spawns the invisible armor stand the bow, crossbow, firework and trident stages are filmed
    /// through. It stands 1.8 blocks beside the shooter and a block behind {@link #RANGE_STAND},
    /// looking at a point halfway between the shooter and the target: blocks away from the line of
    /// fire, so a crit arrow's trail, which drifts back along that line ({@code
    /// AbstractArrow.java:226-238} and {@code CritParticle.java:20-28} in the 26.3 sources), passes
    /// blocks from the lens. First person cannot keep that trail off the lens: the arrow spawns at
    /// the eye and each tick's four crits spawn on its path and drift back along it for about four
    /// blocks. The stand is invisible, so it draws nothing itself, and it is not a projectile's
    /// target: it never takes a hit.
    private static UUID spawnRangeCameraStand(MinecraftServer server, Vec3 target) {
        ServerLevel level = server.overworld();
        ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
        if (stand == null) {
            throw new AssertionError("Expected to create the camera armor stand");
        }
        Vec3 position = RANGE_STAND.add(1.8, 0.0, -1.0);
        stand.setPos(position.x, position.y, position.z);
        Vec3 midpoint = RANGE_STAND.add(target).scale(0.5).add(0.0, 0.9, 0.0);
        stand.lookAt(EntityAnchorArgument.Anchor.EYES, midpoint);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setPermanentlyInvulnerable(true);
        level.addFreshEntity(stand);
        return stand.getUUID();
    }

    /// Switches the client camera to the armor stand beside the shooter, so the stage is filmed
    /// from beside it. The camera is a viewpoint, not game behaviour: {@code
    /// Minecraft.setCameraEntity} is the vanilla client's own switch, and every input still goes
    /// through the recording player's real keys. The {@code Gui} draws the hotbar and hearts only
    /// for a Player camera ({@code Hud.java:728} in the 26.3 sources), so these stages show
    /// neither; the captions carry the shot count and the target's health instead. Vanilla also
    /// never draws the local player while the camera is not it ({@code LevelExtractor.java:269-270}
    /// in the 26.3 sources), so the shooter is invisible; its first-person hands and bow are drawn,
    /// as vanilla draws them for a non-player camera ({@code GameRenderer.java:377-380}).
    private static void enterRangeCamera(
            ClientGameTestContext context, TestDedicatedServerContext server, UUID standId) {
        context.waitFor(
                client -> {
                    for (Entity entity : client.level.entitiesForRendering()) {
                        if (entity.getUUID().equals(standId)) {
                            return true;
                        }
                    }
                    return false;
                },
                200);
        context.runOnClient(
                client -> {
                    for (Entity entity : client.level.entitiesForRendering()) {
                        if (entity.getUUID().equals(standId)) {
                            client.setCameraEntity(entity);
                            return;
                        }
                    }
                    throw new AssertionError("Expected the camera armor stand on the client");
                });
        // The camera's eye height lerps to the stand's over a few ticks (Camera.java:82-83 in the
        // 26.3 sources); the frames of that slide are before the next marker, so the cut drops
        // them.
        context.waitTicks(6);
        requireKeysOverlay(context);
    }

    /// Puts the camera back on the recording player and removes the armor stand.
    private static void leaveRangeCamera(
            ClientGameTestContext context, TestDedicatedServerContext server, UUID standId) {
        context.runOnClient(client -> client.setCameraEntity(client.player));
        server.runOnServer(
                current -> {
                    Entity stand = current.overworld().getEntity(standId);
                    if (stand != null) {
                        stand.discard();
                    }
                });
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
        openStage(
                context,
                "showcase.ready",
                "Accelerated Damage",
                "Four rules, every hit and every effect");
        // The marker pair brackets the hold, so the title card is on screen for it.
        context.waitTicks(TITLE_CARD_TICKS);
        closeStage(context, "showcase.ready", 4);
        meleeHalf(context, server, playerId, targetId, false);
        meleeHalf(context, server, playerId, targetId, true);
        cooldownHalf(context, server, playerId, targetId, false);
        cooldownHalf(context, server, playerId, targetId, true);
        server.runOnServer(
                current -> {
                    current.tickRateManager().setTickRate(20.0F);
                    current.overworld()
                            .getGameRules()
                            .set(ModGameRules.FASTER_EFFECT_TICKING, false, current);
                    parkTarget(current, targetId);
                });
    }

    /// One mace hit, then the crosshair's attack indicator refilling: the client's own charge
    /// scale, which the synced attack speed shortens tenfold. The cooldown rule is off in both
    /// halves, because with it on the server's own {@code getAttackStrengthScale} returns 1.0
    /// ({@code PlayerMixin.acceleratedDamage$fullyChargeAttacks}) and the client, which never sees
    /// that override, is left with its own scale. The `melee-on` stage has
    /// `disable_attack_cooldown` on and the effect rule off: the client restarts its own attack
    /// timer on every hit (`MultiPlayerGameMode.java:437` in the 26.3 sources), so the bar stays
    /// low while every sword hit lands. A-1409 is shown by these two cooldown halves, where the
    /// effect rule alone changes the synced attack speed.
    ///
    /// <p>The game runs at {@link #COOLDOWN_TICK_RATE} ticks a second for both halves. The
    /// accelerated refill is 3.3 ticks, a sixth of a second at the vanilla 20, which a 30 fps
    /// capture shows as a flicker. At 5 ticks a second it lasts two thirds of a second and the
    /// vanilla one 6.7 seconds, so both are refills a viewer can follow.
    private static void cooldownHalf(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            int playerId,
            int targetId,
            boolean rulesOn) {
        String stage = "showcase.cooldown-" + (rulesOn ? "on" : "off");
        String title = "Attack indicator, effect rule " + (rulesOn ? "on" : "off");
        openStage(
                context,
                stage,
                title,
                "a mace hit every "
                        + COOLDOWN_CADENCE_TICKS
                        + " ticks, the game at "
                        + (int) COOLDOWN_TICK_RATE
                        + " ticks a second");
        server.runOnServer(
                current -> {
                    setDamageRules(current, false, false);
                    current.overworld()
                            .getGameRules()
                            .set(ModGameRules.FASTER_EFFECT_TICKING, rulesOn, current);
                    current.tickRateManager().setTickRate(COOLDOWN_TICK_RATE);
                    ServerPlayer player = player(current, playerId);
                    player.getInventory().setItem(0, new ItemStack(Items.MACE));
                    player.getInventory().setSelectedSlot(0);
                    resetTarget(current, targetId, WEAPON_TARGET_HEALTH);
                });
        // The client's own hand must hold the mace before the attack key sends anything.
        context.waitFor(client -> client.player.getMainHandItem().is(Items.MACE));
        // The indicator is drawn nine GUI pixels below the crosshair (Hud.java:448-465), so aiming
        // at the neck puts it on the dummy's chest, where the red hurt flash hides it. Aiming at
        // the shins keeps the crosshair inside the hitbox and puts the bar on the floor below it.
        ClientGameTestPlayerActions.lookAt(
                context,
                server.computeOnServer(
                        current -> entity(current, targetId).position().add(0.0, 0.2, 0.0)));
        // A hit every five ticks: the client resets its own attack-strength ticker on the attack
        // packet, so the indicator empties and refills between hits. With the effect rule off the
        // mace's 33.3-tick delay leaves it crawling. With the rule on the synced 3.3-tick delay
        // fills it before the next hit.
        float charge = -1.0F;
        float delay = -1.0F;
        for (int tick = 1; tick <= COOLDOWN_HOLD_TICKS; tick++) {
            if ((tick - 1) % COOLDOWN_CADENCE_TICKS == 0) {
                ClientGameTestPlayerActions.attackEntity(context, targetId);
            }
            context.waitTick();
            float[] reading =
                    context.computeOnClient(
                            client ->
                                    client.player == null
                                            ? new float[] {-1.0F, -1.0F}
                                            : new float[] {
                                                client.player.getAttackStrengthScale(0.0F),
                                                client.player.getCurrentItemAttackStrengthDelay()
                                            });
            if ((tick - 1) % COOLDOWN_CADENCE_TICKS == COOLDOWN_TICKS) {
                charge = reading[0];
                delay = reading[1];
            }
            System.out.println(
                    "AD_COOLDOWN "
                            + stage
                            + " tick="
                            + tick
                            + " charge="
                            + reading[0]
                            + " delay="
                            + reading[1]);
        }
        // The mace's attack speed is 4.0 - 3.4 = 0.6 (MaceItem.java:42, 26.3), so its vanilla
        // delay is 20 / 0.6 = 33.3 ticks and four ticks leave the indicator an eighth full; the
        // synced attack speed makes the delay 3.3 ticks, so the same four ticks fill it.
        if (rulesOn && charge < 1.0F) {
            throw new AssertionError(
                    "Expected the client's attack indicator to be full "
                            + COOLDOWN_TICKS
                            + " ticks after a hit with the effect rule on, its charge is "
                            + charge
                            + " and its delay "
                            + delay);
        }
        if (!rulesOn && charge >= 0.5F) {
            throw new AssertionError(
                    "Expected the client's attack indicator to be under half full "
                            + COOLDOWN_TICKS
                            + " ticks after a hit with the effect rule off, its charge is "
                            + charge
                            + " and its delay "
                            + delay);
        }
        if (rulesOn && delay > 4.0F) {
            throw new AssertionError(
                    "Expected the client's attack delay to be a tenth of the mace's vanilla 33.3"
                            + " ticks with the effect rule on, it is "
                            + delay);
        }
        closeStage(context, stage, 4);
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
        finishHalf(context, server, stage, title, "hit " + MELEE_HITS, MELEE_HITS, rulesOn);
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
        rangeCameraStand =
                server.computeOnServer(current -> spawnRangeCameraStand(current, RANGE_TARGET));
        enterRangeCamera(context, server, rangeCameraStand);
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
            finishHalf(context, server, stage, title, "shot " + BOW_SHOTS, BOW_SHOTS, rulesOn);
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
            finishHalf(
                    context, server, stage, title, "shot " + HOTBAR_SHOTS, HOTBAR_SHOTS, rulesOn);
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

    /// The vanilla client's own stab gate, which is what a spear waits on: {@code
    /// Minecraft#startAttack} refuses the swing while {@code Player#cannotAttackWithItem} says the
    /// charge is short ({@code Minecraft.java:1647} in the 26.3 sources), and a spear's {@code
    /// MINIMUM_ATTACK_CHARGE} is 1.0 ({@code Item.java:552}). The client reads the attack speed the
    /// server synced, so a few ticks after the spear arrives it must accept the stab. With the
    /// vanilla delay it would refuse for 23 ticks.
    ///
    /// <p>This is a check, not a stage: a filmed spear stage showed the spear held in the air and
    /// never the swing landing, so the gate is read here and the film does not show it. The take
    /// that filmed it threw {@code IllegalArgumentException: Can't find id for 'minecraft:spear'}
    /// while encoding {@code clientbound/minecraft:damage_event}; why the id is missing is not
    /// established.
    private static void requireSpearGate(
            ClientGameTestContext context, TestDedicatedServerContext server, int playerId) {
        String stage = "showcase.spear";
        server.runOnServer(
                current -> {
                    current.overworld()
                            .getGameRules()
                            .set(ModGameRules.FASTER_EFFECT_TICKING, true, current);
                    ServerPlayer player = player(current, playerId);
                    player.getInventory().clearContent();
                    player.getInventory().setItem(0, new ItemStack(Items.NETHERITE_SPEAR));
                    player.getInventory().setSelectedSlot(0);
                    player.connection.send(new ClientboundSetHeldSlotPacket(0));
                    player.resetAttackStrengthTicker();
                });
        // The client resets its own attack-strength ticker when the held item changes, so it needs
        // a few ticks after the spear arrives. A spear's vanilla delay is 23 ticks, far more.
        context.waitTicks(SPEAR_GATE_TICKS);
        String violation =
                context.computeOnClient(
                        client -> {
                            if (client.player == null) {
                                return "the client player is not available";
                            }
                            ItemStack held = client.player.getMainHandItem();
                            if (!held.is(Items.NETHERITE_SPEAR)) {
                                return "the client holds " + held + ", not the spear";
                            }
                            if (client.player.cannotAttackWithItem(held, 0)) {
                                return "the vanilla client still refuses a spear stab "
                                        + SPEAR_GATE_TICKS
                                        + " ticks after the spear arrived: attack speed "
                                        + client.player.getAttributeValue(Attributes.ATTACK_SPEED)
                                        + ", charge "
                                        + client.player.getAttackStrengthScale(0.0F)
                                        + ", delay "
                                        + client.player.getCurrentItemAttackStrengthDelay();
                            }
                            return null;
                        });
        if (violation != null) {
            throw new AssertionError(stage + ": " + violation);
        }
        System.out.println(
                "AD_SPEAR "
                        + stage
                        + " accepted after "
                        + SPEAR_GATE_TICKS
                        + " ticks, delay "
                        + context.computeOnClient(
                                client -> client.player.getCurrentItemAttackStrengthDelay()));
        server.runOnServer(
                current -> {
                    current.overworld()
                            .getGameRules()
                            .set(ModGameRules.FASTER_EFFECT_TICKING, false, current);
                    player(current, playerId).getInventory().clearContent();
                });
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
        // Cleared so a half that never pushes a caption cannot be checked against the last half's.
        LAST_CAPTION_HEALTH = null;
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
        LAST_CAPTION_HEALTH = health;
        LAST_CAPTION_TICK = serverTick;
        requireNoEntityFillsTheFrame(stage);
    }

    /// Fails unless the half's last caption shows the last hit's health. The cut keeps a stage up
    /// to its end marker, so a caption pushed on the marker's own tick never reaches the film: the
    /// bow half's 14 shots at 3 ticks run 42 ticks, past {@link #HALF_TICKS}, and its last caption
    /// was cut from the take.
    private static void requireLastCaptionShown(String stage, HitLog log) {
        Float caption = LAST_CAPTION_HEALTH;
        if (caption == null) {
            throw new AssertionError("Expected " + stage + " to push a caption");
        }
        if (Math.abs(caption - log.finalHealth()) > 0.05F) {
            throw new AssertionError(
                    "Expected the last caption of "
                            + stage
                            + " to show the last hit's health "
                            + log.finalHealth()
                            + ", it showed "
                            + caption);
        }
        // The cut keeps a stage up to its end marker, so a caption pushed on the marker's own tick
        // is never drawn. This is the gap from the last push to the marker: with the hold in
        // finishHalf it is the hold, and with the hold removed it is 0 for a half that ran past
        // HALF_TICKS, which is the bow half.
        int shown = serverTick - LAST_CAPTION_TICK;
        if (shown < MIN_LAST_CAPTION_TICKS) {
            throw new AssertionError(
                    "Expected the last caption of "
                            + stage
                            + " to stay on screen for "
                            + MIN_LAST_CAPTION_TICKS
                            + " ticks before its end marker, it had "
                            + shown);
        }
    }

    /// Waits until every projectile of the half has reached the target, pushing the caption on
    /// every tick of the wait so the last hits' numbers reach the screen, closes the server's log,
    /// holds the picture until the half has run about two seconds, and checks the log.
    private static void finishHalf(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            String stage,
            String title,
            String lastLabel,
            int expectedInputs,
            boolean rulesOn) {
        int targetId = server.computeOnServer(current -> hitLog.targetId);
        for (int waited = 0; ; waited++) {
            boolean landed =
                    server.computeOnServer(
                            current -> {
                                HitLog log = hitLog;
                                return log.inputTicks.size() >= expectedInputs
                                        && log.hits.size() >= log.inputTicks.size();
                            });
            if (landed) {
                break;
            }
            if (waited >= INPUT_WAIT_TICKS) {
                throw new AssertionError(
                        "Expected every attack or shot of "
                                + stage
                                + " to reach the target: "
                                + server.computeOnServer(current -> hitLog.describe()));
            }
            context.waitTick();
            // The last projectiles land after the last input, so the caption is pushed again on
            // every tick of the wait: a number that changes on a tick is on screen on the next
            // frame, and the hold that follows shows the target's final health.
            showHealth(context, server, targetId, stage, title, lastLabel);
        }
        // The loop breaks on the tick the last hit landed, before pushing that tick's caption, so
        // the health the last hit left is pushed here.
        showHealth(context, server, targetId, stage, title, lastLabel);
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
        // the marker until it has run about two seconds, and never less than the last caption
        // needs to be drawn.
        context.waitTicks(
                Math.max(MIN_LAST_CAPTION_TICKS, HALF_TICKS - (log.closeTick - log.openTick)));
        requireLastCaptionShown(stage, log);
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

    private static void setEffectRules(MinecraftServer server, boolean on) {
        GameRules rules = server.overworld().getGameRules();
        rules.set(ModGameRules.FASTER_EFFECT_TICKING, on, server);
        rules.set(ModGameRules.DISABLE_IFRAMES, on, server);
        rules.set(ModGameRules.INSTANT_SHOOT, false, server);
        rules.set(ModGameRules.DISABLE_ATTACK_COOLDOWN, false, server);
    }

    /// Fire, lava, poison and wither in the front row; powder snow, cactus and a fish on land in
    /// the back row, each back-row target in a gap of the front row; a player in a water tank
    /// further back, in the gap left of the cactus, so the camera sees all eight. The caption panel
    /// cuts a line at 360 GUI pixels (MinecraftModConventions 3.4.0,
    /// ClientGameTestRecordingHud.java:24), short of eight figures and the rules, so the title
    /// carries the first five figures and the subtitle the other three and the rules.
    private static EffectScene effectsScene() {
        return new EffectScene(
                "effects",
                null,
                EFFECTS_STAND,
                new Vec3(0.0, ARENA_Y + 0.9, -10.0),
                List.of(
                        "Fire",
                        "Lava",
                        "Poison",
                        "Wither",
                        "Freezing",
                        "Cactus",
                        "Drying",
                        "Drowning"),
                5,
                Map.of(),
                Set.of("Fire", "Poison", "Wither"),
                Set.of("Drowning"),
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
                    int drowning = placeTarget(current, "Drowning", new Vec3(-1.5, ARENA_Y, -17.5));
                    // The camera stands about 15 blocks from the tank, past the default 10 within
                    // which the client draws the below-name line (Attributes.java:22-24).
                    living(current, drowning)
                            .getAttribute(Attributes.BELOW_NAME_DISTANCE)
                            .setBaseValue(32.0);
                    LivingEntity fish = living(current, drying);
                    fish.getAttribute(Attributes.MAX_HEALTH).setBaseValue(EFFECT_TARGET_HEALTH);
                    fish.setHealth(EFFECT_TARGET_HEALTH);
                    fish.setAirSupply(0);
                    List<Integer> targets =
                            List.of(fire, lava, poison, wither, freezing, cactus, drying, drowning);
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
                        Optional.of(MobEffects.REGENERATION),
                        "Saturated",
                        Optional.empty()),
                Set.of(),
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

    /// One effect scenario: about two seconds with faster_effect_ticking and disable_i_frames off,
    /// then the same targets with fresh health and the same effects with both rules on. Every
    /// target loses more health (a healer gains more) with the rules on than with them off, a
    /// healer heals ten times as fast with them on, and a target under water keeps its air and
    /// health with them off and runs out of air and drowns with them on.
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
        int[][] air = new int[2][count];
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
                                if (rulesOn) {
                                    // The recording player's own effect is what the client can be
                                    // compared against: EffectSyncTicker resends an effect to its
                                    // holder's connection, and the only connection a real client
                                    // holds is its own player's. Water breathing carries no
                                    // attribute modifier (MobEffects.java:57), so it leaves the
                                    // field of view alone, and the icon is off so the HUD is
                                    // unchanged.
                                    player(current, playerId)
                                            .addEffect(
                                                    new MobEffectInstance(
                                                            MobEffects.WATER_BREATHING,
                                                            REGENERATION_TICKS,
                                                            0,
                                                            false,
                                                            false,
                                                            false));
                                }
                                for (Map.Entry<String, Optional<Holder<MobEffect>>> healer :
                                        scene.healers().entrySet()) {
                                    LivingEntity target =
                                            healerOf(current, shown, scene, healer.getKey());
                                    HealLog log = new HealLog(serverTick);
                                    MobEffectInstance instance =
                                            healer.getValue().map(target::getEffect).orElse(null);
                                    if (instance != null) {
                                        log.durationOpen = instance.getDuration();
                                        log.amplifier = instance.getAmplifier();
                                    }
                                    HEAL_LOGS.put(target.getId(), log);
                                }
                                return healthsOf(current, shown);
                            });
            String startingAir = server.computeOnServer(current -> airLine(current, shown, scene));
            System.out.println(
                    "AD_CURVE "
                            + stage
                            + " 0 ticks: "
                            + String.join(" | ", starting)
                            + startingAir);
            int pushedTick = serverTick;
            for (int tick = 1; tick <= 3 * READING_TICKS; tick++) {
                context.waitTick();
                String[] label =
                        server.computeOnServer(current -> caption(current, shown, scene, rulesOn));
                int now = serverTick;
                requireCaptionIsFresh(stage, now, pushedTick);
                // The caption carries the health numbers, so it is pushed on every tick: a number
                // that changes on a tick is on screen on the next frame.
                context.runOnClient(
                        _ -> ClientGameTestRecordingHud.showStep(stage, label[0], label[1]));
                pushedTick = now;
                if (tick % READING_TICKS == 0) {
                    String airLabel =
                            server.computeOnServer(current -> airLine(current, shown, scene));
                    System.out.println(
                            "AD_CURVE "
                                    + stage
                                    + " "
                                    + tick
                                    + " ticks: "
                                    + String.join(" | ", label)
                                    + airLabel);
                    requireShot(context, stage, "after " + tick + " ticks", shot);
                    if (rulesOn && tick == READING_TICKS) {
                        requireClientEffectDurationSynchronized(context, server, playerId, stage);
                        // The check waits for a resend tick, so the caption it left on screen is
                        // older than the freshness bound allows; push it again from here.
                        String[] refreshed =
                                server.computeOnServer(
                                        current -> caption(current, shown, scene, rulesOn));
                        context.runOnClient(
                                _ ->
                                        ClientGameTestRecordingHud.showStep(
                                                stage, refreshed[0], refreshed[1]));
                        pushedTick = serverTick;
                    }
                }
            }
            Map<String, HealLog> halfHeals = new LinkedHashMap<>();
            int[] halfAir = air[half];
            float[] end =
                    server.computeOnServer(
                            current -> {
                                for (Map.Entry<String, Optional<Holder<MobEffect>>> healer :
                                        scene.healers().entrySet()) {
                                    LivingEntity target =
                                            healerOf(current, shown, scene, healer.getKey());
                                    HealLog log = HEAL_LOGS.remove(target.getId());
                                    log.closeTick = serverTick;
                                    if (healer.getValue().isPresent()) {
                                        MobEffectInstance instance =
                                                target.getEffect(healer.getValue().get());
                                        log.durationClose =
                                                instance == null ? 0 : instance.getDuration();
                                    }
                                    halfHeals.put(healer.getKey(), log);
                                }
                                for (int index = 0; index < count; index++) {
                                    halfAir[index] =
                                            living(current, shown.get(index)).getAirSupply();
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
        assertDrowning(scene, change, air);
        assertEffectsAccelerated(scene, change);
    }

    /// Fails unless a resend from {@code EffectSyncTicker} reaches the vanilla client. The server
    /// burns ten durations a tick with the rule on ({@code MobEffectInstanceMixin}), and the ticker
    /// resends every effect to its own holder's connection every ten ticks.
    ///
    /// <p>The check first gives the client a duration the server does not hold, then requires the
    /// next resend to put it back. Comparing the two durations without that step proves nothing:
    /// the client runs the same {@code MobEffectInstanceMixin}, and {@code VirtualTime.isEnabled()}
    /// reads the server's game rule through {@code ServerContext}, which a client GameTest shares
    /// with its dedicated server, so the client burns ten durations a tick too and tracks the
    /// server with no resend at all.
    ///
    /// <p>The subject is the recording player, not an effect target. A target is a {@link
    /// ServerPlayer} whose connection is a fake {@code EmbeddedChannel}, so the ticker sends its
    /// effects there and the recording client never receives them: a target's effect is absent on
    /// the client, not merely stale.
    private static void requireClientEffectDurationSynchronized(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            int playerId,
            String stage) {
        while (server.computeOnServer(_ -> serverTick) % 10 != 1) {
            context.waitTick();
        }
        int serverDuration =
                server.computeOnServer(
                        current -> {
                            MobEffectInstance effect =
                                    player(current, playerId).getEffect(MobEffects.WATER_BREATHING);
                            if (effect == null) {
                                throw new AssertionError(
                                        "Expected the recording player to hold the water breathing effect in "
                                                + stage);
                            }
                            return effect.getDuration();
                        });
        int given = serverDuration + EFFECT_SYNC_TOLERANCE_TICKS * 10;
        context.runOnClient(
                client -> {
                    client.player.removeEffect(MobEffects.WATER_BREATHING);
                    client.player.addEffect(
                            new MobEffectInstance(
                                    MobEffects.WATER_BREATHING, given, 0, false, false, false));
                });
        // The read is already on a resend tick, so the next one is ten ticks out: wait for the
        // tick count to pass a whole resend period before reading again.
        int perturbed = server.computeOnServer(_ -> serverTick);
        while (server.computeOnServer(_ -> serverTick) - perturbed < 10
                || server.computeOnServer(_ -> serverTick) % 10 != 1) {
            context.waitTick();
        }
        int[] read = new int[2];
        read[0] =
                server.computeOnServer(
                        current -> {
                            MobEffectInstance effect =
                                    player(current, playerId).getEffect(MobEffects.WATER_BREATHING);
                            if (effect == null) {
                                throw new AssertionError(
                                        "Expected the recording player to hold the water breathing effect in "
                                                + stage);
                            }
                            return effect.getDuration();
                        });
        read[1] =
                context.computeOnClient(
                        client -> {
                            MobEffectInstance effect =
                                    client.player.getEffect(MobEffects.WATER_BREATHING);
                            if (effect == null) {
                                throw new AssertionError(
                                        "Expected the vanilla client to receive the water breathing effect in "
                                                + stage);
                            }
                            return effect.getDuration();
                        });
        if (Math.abs(read[0] - read[1]) > EFFECT_SYNC_TOLERANCE_TICKS) {
            throw new AssertionError(
                    "Expected the accelerated effect duration to reach the vanilla client in "
                            + stage
                            + ": the client was given "
                            + given
                            + ", the server holds "
                            + read[0]
                            + " and the client "
                            + read[1]);
        }
        System.out.println(
                "AD_EFFECT_SYNC "
                        + stage
                        + " given="
                        + given
                        + " server="
                        + read[0]
                        + " client="
                        + read[1]);
    }

    private static LivingEntity healerOf(
            MinecraftServer server, List<Integer> shown, EffectScene scene, String healer) {
        return living(server, shown.get(scene.names().indexOf(healer)));
    }

    /// The air of every target under water, for the take's log; the below-name line shows it on
    /// screen.
    private static String airLine(MinecraftServer server, List<Integer> shown, EffectScene scene) {
        StringBuilder line = new StringBuilder();
        for (String name : scene.underWater()) {
            line.append(" | ")
                    .append(name)
                    .append(" air ")
                    .append(living(server, shown.get(scene.names().indexOf(name))).getAirSupply());
        }
        return line.toString();
    }

    /// The checks of the drowning stage this scene took over: with the rules off a target under
    /// water keeps at least 200 of its air and all of its health, and with them on its air runs out
    /// and drowning damage starts.
    private static void assertDrowning(EffectScene scene, float[][] change, int[][] air) {
        for (String name : scene.underWater()) {
            int index = scene.names().indexOf(name);
            if (air[0][index] < 200 || change[0][index] != 0.0F) {
                throw new AssertionError(
                        "Expected vanilla air to last for "
                                + name
                                + ", air was "
                                + air[0][index]
                                + " and health changed by "
                                + change[0][index]);
            }
            if (air[1][index] > 0 || change[1][index] >= 0.0F) {
                throw new AssertionError(
                        "Expected "
                                + name
                                + "'s air to run out and drowning damage to start, air was "
                                + air[1][index]
                                + " and health changed by "
                                + change[1][index]);
            }
        }
    }

    /// The tick log of every healer and the checks that it heals ten times as fast with the rules
    /// on. A healer that heals from an effect is checked exactly against the effect itself: each
    /// half heals once for every remaining duration the half covered at which the effect applies
    /// ({@code shouldApplyEffectTickThisTick}), and the half covers one duration a tick with the
    /// rules off and ten with them on. Regeneration II applies at most once in 25 durations
    /// (RegenerationMobEffect.java:21-24 in the 26.3 sources), so one heal tick is one application.
    /// A healer that heals from saturation has no vanilla API for its cadence (the 10-tick timer is
    /// FoodData.java:47), so its vanilla rate is measured from the gaps between its heals with the
    /// rules off (so its rules-off check only catches an uneven cadence), and each half must heal
    /// within one heal of that rate, times ten with the rules on.
    private static void assertHealingAccelerated(
            EffectScene scene, List<Map<String, HealLog>> heals) {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, Optional<Holder<MobEffect>>> healer : scene.healers().entrySet()) {
            String name = healer.getKey();
            HealLog off = heals.get(0).get(name);
            HealLog on = heals.get(1).get(name);
            if (off.healTicks.size() < 2 || on.healTicks.isEmpty()) {
                failures.add(
                        String.format(
                                Locale.ROOT,
                                "Expected %s in showcase.%s to heal at least twice with the rules"
                                        + " off and at least once with them on: off[%s] on[%s]",
                                name,
                                scene.id(),
                                off,
                                on));
                continue;
            }
            double offPerTick = off.healed / (double) off.ticks();
            double onPerTick = on.healed / (double) on.ticks();
            String line =
                    String.format(
                            Locale.ROOT,
                            "AD_TICKLOG showcase.%s heal %s off[%s] on[%s] offPerTick=%.4f"
                                    + " onPerTick=%.4f onOverOff=%.2f",
                            scene.id(),
                            name,
                            off,
                            on,
                            offPerTick,
                            onPerTick,
                            onPerTick / offPerTick);
            System.out.println(line);
            if (healer.getValue().isPresent()) {
                MobEffect effect = healer.getValue().get().value();
                checkEffectHeals(name, "off", effect, off, 1, line, failures);
                checkEffectHeals(name, "on", effect, on, VirtualTime.ACCELERATION, line, failures);
                continue;
            }
            float step = off.healPerStep();
            double vanillaPerTick = step * off.stepsPerTick();
            double expectedOn = VirtualTime.ACCELERATION * vanillaPerTick * on.ticks();
            if (Math.abs(off.healed - vanillaPerTick * off.ticks()) > step) {
                failures.add(
                        String.format(
                                Locale.ROOT,
                                "Expected %s to heal at an even rate with the rules off: %s",
                                name,
                                line));
            }
            if (Math.abs(on.healed - expectedOn) > step) {
                failures.add(
                        String.format(
                                Locale.ROOT,
                                "Expected %s to heal %.1f, %d times its rules-off rate, with the"
                                        + " rules on: %s",
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

    private static void checkEffectHeals(
            String name,
            String half,
            MobEffect effect,
            HealLog log,
            int durationsPerTick,
            String line,
            List<String> failures) {
        int covered = log.durationOpen - log.durationClose;
        int expectedCovered = durationsPerTick * log.ticks();
        int expectedHeals = 0;
        for (int duration = log.durationOpen; duration > log.durationClose; duration--) {
            if (effect.shouldApplyEffectTickThisTick(duration, log.amplifier)) {
                expectedHeals++;
            }
        }
        if (covered != expectedCovered || log.healTicks.size() != expectedHeals) {
            failures.add(
                    String.format(
                            Locale.ROOT,
                            "Expected %s with the rules %s to cover %d durations in %d ticks and"
                                    + " heal %d times, it covered %d and healed %d times: %s",
                            name,
                            half,
                            expectedCovered,
                            log.ticks(),
                            expectedHeals,
                            covered,
                            log.healTicks.size(),
                            line));
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
            context.waitFor(client -> shotViolation(client, shot) == null, SHOT_WAIT_TICKS);
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
        try {
            context.waitFor(client -> shotViolation(client, shot) == null, SHOT_WAIT_TICKS);
        } catch (AssertionError timeout) {
            throw new AssertionError(
                    stage
                            + " "
                            + when
                            + ": "
                            + context.computeOnClient(client -> shotViolation(client, shot)),
                    timeout);
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

    /// Fails when the caption was pushed more than {@link #CAPTION_LAG_TICKS} ticks ago. This
    /// measures the push cadence, not the screen: {@code runOnClient} blocks until the client
    /// thread has applied the push, and the HUD renders the step it holds every frame, so the
    /// caption on screen is at most one tick older than the last push.
    private static void requireCaptionIsFresh(String stage, int now, int pushedTick) {
        if (now - pushedTick > CAPTION_LAG_TICKS) {
            throw new AssertionError(
                    "Expected the caption of "
                            + stage
                            + " to be at most "
                            + CAPTION_LAG_TICKS
                            + " ticks old, it was pushed "
                            + (now - pushedTick)
                            + " ticks ago");
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
        // A projectile knocks its target along the line it came in on (LivingEntity.java:1293-1305
        // and Projectile.java:444-448 in the 26.3 sources), which is towards the camera, and nine
        // crossbow shots in a row walked the dummy into the lens. Full resistance zeroes the
        // knockback before it moves anything (LivingEntity.java:1647-1649).
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        target.setCustomName(Component.literal(name));
        target.setCustomNameVisible(true);
        DUMMIES.put(name, target.getId());
        TRACKED.put(target.getUUID(), new TrackedTarget(target.getUUID(), name, PARKING));
        publishTrackedIds();
    }

    /// Publishes the tracked ids for the client thread, which must not read {@link #TRACKED} while
    /// the server thread changes it.
    private static void publishTrackedIds() {
        Map<UUID, Vec3> anchors = new LinkedHashMap<>();
        TRACKED.forEach(
                (id, tracked) -> {
                    if (tracked.anchor() != null) {
                        anchors.put(id, tracked.anchor());
                    }
                });
        TRACKED_ANCHORS = Map.copyOf(anchors);
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
        publishTrackedIds();
        return id;
    }

    /// Gives a dummy a fresh health pool and clears what the last half left on it: fire, frost,
    /// effects, stuck arrows, spent air and the feeding.
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
        target.setAirSupply(target.getMaxAirSupply());
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
            publishTrackedIds();
        } else if (target != null) {
            target.discard();
        }
    }

    private static void restoreState(MinecraftServer server) {
        restoreRules(server);
        TRACKED.clear();
        publishTrackedIds();
        hitLog = null;
        FED.clear();
        Objective air = server.getScoreboard().getObjective(AIR_OBJECTIVE);
        if (air != null) {
            server.getScoreboard().removeObjective(air);
        }
        HEAL_LOGS.clear();
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (player.getCustomName() != null) {
                server.getPlayerList().remove(player);
            }
        }
        if (!server.getPlayerList().getPlayers().isEmpty()) {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.removeEffect(MobEffects.WATER_BREATHING);
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
