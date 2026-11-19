# Accelerated Damage

Accelerated Damage is a server-side Minecraft 26.3 mod for Fabric and NeoForge that exposes configurable accelerated combat and damage mechanics as gamerules. Clients do not need to install the mod.

All rules are disabled by default.

## Features

### Damage invincibility

Disables the repeated-damage invincibility window for every damage source. Vanilla applies knockback, the hurt flash and the hurt sound only to a hit that takes the full-damage path, so with this rule every hit knocks its target back as well; a hit that a shield blocks completely still does not.

Minecraft 26.3 separated this window from a new temporary invulnerability that, for a fixed time, rejects all damage except `/kill` and falling into the void. Neither `disable_i_frames` nor `faster_effect_ticking` changes it. Vanilla gives it to the horses and skeletons of a skeleton trap (60 ticks), and a mob that converts into another mob keeps what is left of it. In 26.2 those trap mobs used the damage invincibility window instead, so both rules applied to them.

### Faster effect ticking

Runs damage and healing over time ten times faster, as if each server tick were ten vanilla ticks: status effects, fire, freezing (including powder snow build-up), drowning and drying out, hunger-based and Peaceful regeneration, starvation, bees dying after they sting, lingering potion and dragon's breath clouds, beacon and conduit pulses (conduits also attack ten times as often), and the self-healing of allays, horses, happy ghasts, the wither and the ender dragon.

Mob and player attacks use the same timeline: melee cooldowns, ranged attack intervals, bow draw and crossbow charge, and the wind-ups and cooldowns of guardian beams, blaze and ghast fireballs, shulker bullets, evoker fangs and vex summoning, creeper fuses, ravager roars, phantom swoops, wither skulls, ender dragon fireballs and breath, warden sonic booms, breeze wind charges, goat rams and frog tongues. A player's attack cooldown is ten times shorter too, and the client sees it: the mod raises the player's attack speed attribute, which the server syncs, so the attack indicator, the hand-swap animation and a spear's stab gate all run on the same shorter delay. Movement, pathfinding and targeting stay at normal speed, and so do spear charges, whose damage depends on speed.

The damage invincibility window gets the same 10× compression and lasts one server tick. As a result, repeated contact damage (cactus, lava, fire, magma, suffocation, cramming, the world border) lands ten times as often, while the vanilla cap on how often damage can land is kept. The shorter window also applies to melee and projectile hits.

### Attack cooldown

Makes server-authoritative attacks fully charged immediately, independently of server tick rate.

The client keeps vanilla's cooldown for this rule, because the rule is applied on the server only. The attack indicator therefore still fills at vanilla speed after each attack, and a spear stab is still held back until the spear is fully charged.

### Instant shooting

Fully charges bows and crossbows immediately and removes the trident's minimum throw time. Riptide keeps its vanilla charge time, because only the player's client can launch the player.

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
| `accelerateddamage:faster_effect_ticking` | Runs damage, healing and mob attack timers ten times faster, and gives players ten times their attack speed. |
| `accelerateddamage:disable_attack_cooldown` | Makes attacks fully charged immediately on the server. |
| `accelerateddamage:instant_shoot` | Removes bow, crossbow and trident charge time. |

## Compatibility

No known incompatibilities.

## Documentation

- [Development](docs/DEVELOPMENT.md) - building, tests and client recordings.
- [Release automation](docs/RELEASE.md)
- [Optional Modrinth publishing](docs/MODRINTH.md)

Accelerated Damage is available under the MIT License.
