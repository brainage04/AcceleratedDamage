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
| `accelerateddamage:disable_i_frames` | Lets consecutive melee attacks deal damage without vanilla invincibility frames. Environmental damage retains vanilla invincibility unless accelerated effect damage is also enabled. |
| `accelerateddamage:faster_effect_damage_ticking` | Runs fire, freezing, poison, and wither damage ten times faster while shortening those effects at the same rate. |
| `accelerateddamage:disable_attack_cooldown` | Raises connected players' base attack speed and applies the same change when players join. |
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
