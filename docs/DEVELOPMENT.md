# Development

Use Java 25. The project has common sources and Fabric and NeoForge loader modules. `settings.gradle` uses the sibling MinecraftModConventions checkout when present; otherwise Gradle resolves the version pinned in `gradle.properties`.

## Build and tests

In Thomas's Minecraft workspace, check `heavy status` and run Gradle through `heavy`:

```shell
heavy status
heavy --mem 6G --label accelerateddamage-tests -- ./gradlew --no-daemon --no-parallel --max-workers=2 build runAllGameTests --continue
```

`runAllGameTests` includes Fabric and NeoForge server GameTests and the Fabric client GameTest. Shared server scenarios live in `common/src/gametest` and are registered by each loader. Client scenarios connect a vanilla client to a dedicated modded server and use keyboard input for melee and projectile attacks.

`:fabric:test` runs the unit tests in `fabric/src/test`. `CompressedTicksTest` and `EffectTickCadenceTest` hold examples and jqwik properties that compare the compressed timeline in `CompressedTicks` and `EffectTickCadence` with vanilla's, one virtual tick per vanilla tick: brain timers fire on the server tick nearest vanilla's, fire hurts in the server tick of every vanilla fire damage tick, poison, wither and regeneration apply on the virtual tick of every vanilla application, and the hurt cooldown lets through the hits vanilla lets through when they all land on the same side of the entity's cooldown step. `:fabric:pitest` runs mutation tests on `CompressedTicks` against them; it is not part of `check`, and the report is written to `fabric/build/reports/pitest`.

## Client recordings

The conventions recorder needs Xvfb, xdpyinfo, pactl and ffmpeg with X11 capture and PulseAudio input support. It creates a private display and audio sink; do not use the desktop's default sink.

```shell
heavy status
GTR_RECORDING_PROFILE=showcase heavy --mem 6G --label accelerateddamage-recording -- ./gradlew --no-daemon :fabric:recordClientGameTest
```

Recordings and their JSON metadata are written to `build/recordings`. The showcase covers vanilla damage immunity, rapid melee attacks, accelerated environmental effects, healing and drying, and one-tick bow, crossbow and trident attacks.
