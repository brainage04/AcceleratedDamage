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

Recordings and their JSON metadata are written to `build/recordings`. The showcase covers rapid melee against a player target with its health printed after each hit, drowning, bow, crossbow, trident and crossbow firework rockets fired several times inside one cooldown window, and accelerated fire and poison; every stage is followed by the same actions with the rule off, where only the first hit of each window lands.
