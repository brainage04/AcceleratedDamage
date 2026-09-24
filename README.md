# Accelerated Damage

A server-side Minecraft 26.2 mod for Fabric and NeoForge that exposes configurable accelerated combat and damage mechanics as gamerules. Clients do not need to install the mod.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer with Fabric API, or NeoForge 26.2.0.23-beta or newer
- Java 25 or newer

## Gamerules

All rules are disabled by default.

| Gamerule | Effect |
| --- | --- |
| `accelerateddamage:disable_i_frames` | Disables the repeated-damage invincibility window for every damage source. |
| `accelerateddamage:faster_effect_ticking` | Runs damage and healing over time ten times faster, as if each server tick were ten vanilla ticks: status effects, fire, freezing (including powder snow build-up), drowning and drying out, hunger-based and Peaceful regeneration, starvation, bees dying after they sting, lingering potion and dragon's breath clouds, beacon and conduit pulses (conduits also attack ten times as often), and the self-healing of allays, horses, happy ghasts, the wither and the ender dragon. Mob attacks use the same timeline: melee cooldowns, ranged attack intervals, bow draw and crossbow charge for mobs, and the wind-ups and cooldowns of guardian beams, blaze and ghast fireballs, shulker bullets, evoker fangs and vex summoning, creeper fuses, ravager roars, phantom swoops, wither skulls, ender dragon fireballs and breath, warden sonic booms, breeze wind charges, goat rams and frog tongues. Movement, pathfinding and targeting stay at normal speed, and so do spear charges, whose damage depends on speed. The damage invincibility window gets the same 10× compression and lasts one server tick. As a result, repeated contact damage (cactus, lava, fire, magma, suffocation, cramming, the world border) lands ten times as often, while the vanilla cap on how often damage can land is kept. The shorter window also applies to melee and projectile hits. |
| `accelerateddamage:disable_attack_cooldown` | Makes server-authoritative attacks fully charged immediately, independently of server tick rate. |
| `accelerateddamage:instant_shoot` | Fully charges bows and crossbows immediately and removes the trident's minimum throw time. |

Use vanilla's gamerule command to query or update a rule:

```text
/gamerule accelerateddamage:disable_i_frames
/gamerule accelerateddamage:disable_i_frames true
```

## Building and verification

```shell
./gradlew build
./gradlew runAllProductionGameTests
```

The production GameTests exercise consecutive melee damage and the bow/trident instant-shoot behavior on a dedicated server.

## Migrating from the Fabric-only release

Install exactly one matching release JAR: `accelerateddamage-<version>.jar` for Fabric (with Fabric API), or `accelerateddamage-neoforge-<version>.jar` for NeoForge. Remove the old Accelerated Damage JAR before switching loaders; do not place both loader JARs in the same `mods` directory.

The mod ID remains `accelerateddamage`, so existing gamerule names and world-level gamerule data remain the same. This is server-side on both loaders: install it on the server only, and vanilla clients can connect. Fabric requires Fabric API; the NeoForge JAR has no additional mod dependency. Root `./gradlew build` emits both loader artifacts under `build/libs`.

## License

Accelerated Damage is available under the MIT License.
