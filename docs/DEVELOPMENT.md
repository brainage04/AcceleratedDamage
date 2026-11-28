# Development

Use Java 25. The project has common sources and Fabric and NeoForge loader modules. Gradle resolves the MinecraftModConventions version pinned in `gradle.properties`; pass `-PuseLocalConventions` to build against the sibling `../MinecraftModConventions` checkout instead.

## Build and tests

In Thomas's Minecraft workspace, check `heavy status` and run Gradle through `heavy`:

```shell
heavy status
heavy --mem 6G --label accelerateddamage-tests -- ./gradlew --no-daemon --no-parallel --max-workers=2 build runAllGameTests --continue
```

`runAllGameTests` includes Fabric and NeoForge server GameTests and the Fabric client GameTest. Shared server scenarios live in `common/src/gametest` and are registered by each loader. Client scenarios connect a vanilla client to a dedicated modded server, spawn player-shaped targets and act only through `ClientGameTestPlayerActions`: the player turns to the target, walks into vanilla reach and presses the real attack or use key, and the shared guard fails the test if the crosshair is not on the target, the target is out of reach, or an interaction-range attribute was changed.

## Client recordings

The conventions recorder needs Xvfb, xdpyinfo, pactl and ffmpeg with X11 capture and PulseAudio input support. It creates a private display and audio sink; do not use the desktop's default sink.

```shell
heavy status
GTR_RECORDING_PROFILE=showcase heavy --mem 6G --label accelerateddamage-recording -- ./gradlew --no-daemon :fabric:recordClientGameTest
```

Recordings and their JSON metadata are written to `build/recordings`. The showcase films each rule against player-shaped targets and prints the target's health in the caption after every hit. Every scenario runs twice in the same scene with the same inputs, first about two seconds with its rules off, then about two seconds with them on:

- a wooden sword, thirteen hits one every three ticks, with `disable_attack_cooldown` and `disable_i_frames` off, then on;
- a bow (ten arrows, one every four ticks), nine loaded crossbows and nine crossbows of firework rockets and nine tridents (one shot every four ticks each), under `instant_shoot`, with `disable_i_frames` off, then on;
- one effects stage with eight targets, one per family, with `faster_effect_ticking` and `disable_i_frames` off, then on: fire, lava, poison and wither in front; freezing, cactus and a fish drying out on land behind them; a player drowning in a water tank at the back, whose air shows on its below-name line;
- nine splash potions of harming thrown from a pillar three blocks up, one every four ticks, with `disable_i_frames` off, then on; the thrower must end each half unhurt;
- a mace swung every ten ticks for two seconds at the game's normal speed, with `faster_effect_ticking` off, then on, showing the attack indicator refill;
- a knockback pair for the sword (two hits), the bow, the crossbow and the trident (three shots each) at a free target on a floor of one-block white and black bands, with `disable_i_frames` off, then on. The caption shows how far the target was pushed, and the run fails unless the target was pushed with the rule off and at least 1.2 times as far with the rule on. A fake player's client never applies a hit's knockback, so the showcase applies the motion the server sends the dummy's connection at the start of the dummy's next tick. The rocket has no knockback pair: its burst pushes the target away from the burst point, off the line of fire, so the later rockets miss;
- starvation, a Regeneration II healer, a healer kept at full saturation and a lingering poison cloud, with `faster_effect_ticking` and `disable_i_frames` off, then on. The run fails unless the Regeneration II healer heals exactly as often as the effect's own `shouldApplyEffectTickThisTick` allows over the durations each half covered, covering one duration a tick with the rules off and ten with them on, and unless the saturated healer heals within one heal of ten times its rules-off rate. Each healer gets an `AD_TICKLOG ... heal` line.

The weapon targets stand pinned in place, so the player aims once per scenario and never walks or turns between hits; only the knockback stages leave the target free. The server logs the game tick of every attack and shot (`AD_TICKLOG` lines) and the target's health before and after every hit, and fails the run when two inputs are more than five ticks apart, when a hit with the rules on deals no damage, or when, with the rules off, a hit inside vanilla's damage cooldown lands in full. The effect halves log their health readings as `AD_CURVE` lines, and the knockback stages log `AD_KNOCK` lines with the distance the target was pushed. The bow, crossbow, rocket and trident stages are filmed from the recording player's own eyes. The frame-coverage check, which fails a stage when entities and particles cover more than a quarter of the frame, leaves out one thing in those stages: an arrow, trident or rocket within two blocks of the camera, the projectile the player has just fired, whose model fills the lens until it has flown clear. A target, any other entity and every particle still count, and a stage where they cover more than a quarter of the frame fails the run. The conventions' `withDedicatedServer` starts and stops the recording itself; the test never calls `startRecording` or `stopRecording`.

Every player-shaped dummy is created and every cell is built in the `beforeJoin` hook, while the client is not yet connected, so no join line and no building can appear in a recorded frame. Between scenarios a dummy waits 400 blocks away, out of the client's view, and its scenario teleports it into the shot; the client then builds it as a new entity facing the way the server turns it. An effects scenario places its targets, effects and cloud before its stage marker and keeps the targets invulnerable until the marker, and the test fails unless the client shows every target in place, each player-shaped one facing the camera, at the marker and at every reading.

A player-shaped target needs one extra call to tick: `ServerPlayer#tick` runs only the connection half of a player's tick, and the `Player`/`LivingEntity`/`Entity` half (fire, effects, freezing, air, hunger) runs from `doTick()`, which a real client's connection calls. The showcase registers `ServerTickEvents.START_SERVER_TICK` and calls `doTick()` on every showcase target, which is what makes effects tick on a fake player at all.
