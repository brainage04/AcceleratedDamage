# Development

Use Java 25. The project has common sources and Fabric and NeoForge loader modules. `settings.gradle` uses the sibling MinecraftModConventions checkout when present; otherwise Gradle resolves the version pinned in `gradle.properties`.

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

Recordings and their JSON metadata are written to `build/recordings`. The showcase films each rule against player-shaped targets, and every target's health is printed above its head and in the caption after each hit:

- rapid melee, and drowning;
- a bow, five crossbows that are loaded before the stage so the stage holds only the shots, a crossbow of firework rockets, and five tridents;
- three rows of effects, one target per family: fire, lava, poison, wither; freezing, cactus, drying out; starvation, a lingering cloud and hunger-based regeneration;
- the damage effects again with the rule off, where the numbers barely move over the same time.

The negative cases (the rule-off weapon cases, the drowning baseline and the vanilla effect baseline) are the last stage of the same world and carry their own caption, so the recorder's final seconds read as a named comparison rather than a stage where nothing happens. The conventions' `withDedicatedServer` starts and stops the recording itself; the test never calls `startRecording` or `stopRecording`.

A player-shaped target needs one extra call to tick: `ServerPlayer#tick` runs only the connection half of a player's tick, and the `Player`/`LivingEntity`/`Entity` half (fire, effects, freezing, air, hunger) runs from `doTick()`, which a real client's connection calls. The showcase registers `ServerTickEvents.START_SERVER_TICK` and calls `doTick()` on every showcase target, which is what makes effects tick on a fake player at all.
