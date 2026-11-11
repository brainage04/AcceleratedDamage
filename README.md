# Accelerated Damage

Accelerated Damage is a server-side Minecraft 26.3 mod for Fabric and NeoForge that exposes configurable accelerated combat and damage mechanics as gamerules. Clients do not need to install the mod.

All rules are disabled by default.

## Features

### Damage invincibility

Disables the repeated-damage invincibility window for every damage source. Vanilla applies knockback, the hurt flash and the hurt sound only to a hit that takes the full-damage path, so with this rule every hit knocks its target back as well; a hit that a shield blocks completely still does not.

Minecraft 26.3 separated this window from a new temporary invulnerability that, for a fixed time, rejects all damage except `/kill` and falling into the void. Neither `disable_i_frames` nor `faster_effect_ticking` changes it. Vanilla gives it to the horses and skeletons of a skeleton trap (60 ticks), and a mob that converts into another mob keeps what is left of it. In 26.2 those trap mobs used the damage invincibility window instead, so both rules applied to them.

### Faster effect ticking

Runs damage and healing over time ten times faster, as if each server tick were ten vanilla ticks: status effects, fire, freezing (including powder snow build-up), drowning and drying out, hunger-based and Peaceful regeneration, starvation, bees dying after they sting, lingering potion and dragon's breath clouds, beacon and conduit pulses (conduits also attack ten times as often), and the self-healing of allays, horses, happy ghasts, the wither and the ender dragon.

Mob attacks use the same timeline: melee cooldowns, ranged attack intervals, bow draw and crossbow charge for mobs, and the wind-ups and cooldowns of guardian beams, blaze and ghast fireballs, shulker bullets, evoker fangs and vex summoning, creeper fuses, ravager roars, phantom swoops, wither skulls, ender dragon fireballs and breath, warden sonic booms, breeze wind charges, goat rams and frog tongues. Movement, pathfinding and targeting stay at normal speed, and so do spear charges, whose damage depends on speed.

The damage invincibility window gets the same 10× compression and lasts one server tick. As a result, repeated contact damage (cactus, lava, fire, magma, suffocation, cramming, the world border) lands ten times as often, while the vanilla cap on how often damage can land is kept. The shorter window also applies to melee and projectile hits.

### Attack cooldown

Makes server-authoritative attacks fully charged immediately, independently of server tick rate.

### Instant shooting

Fully charges bows and crossbows immediately and removes the trident's minimum throw time.

## Getting started

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer with Fabric API, or NeoForge 26.3.0.99 or newer
- Java 25 or newer
- Install on the server.

## Commands

Use vanilla's gamerule command to query or update a rule:

```text
/gamerule accelerateddamage:disable_i_frames
/gamerule accelerateddamage:disable_i_frames true
```

## Configuration

| Gamerule | Effect |
| --- | --- |
| `accelerateddamage:disable_i_frames` | Disables damage invincibility. |
| `accelerateddamage:faster_effect_ticking` | Runs damage, healing and mob attack timers ten times faster. |
| `accelerateddamage:disable_attack_cooldown` | Makes attacks fully charged immediately. |
| `accelerateddamage:instant_shoot` | Removes bow, crossbow and trident charge time. |

## Compatibility

No known incompatibilities.

## Documentation

- [Development](docs/DEVELOPMENT.md) - building, tests and client recordings.
- [Release automation](docs/RELEASE.md)
- [Optional Modrinth publishing](docs/MODRINTH.md)

Accelerated Damage is available under the MIT License.
