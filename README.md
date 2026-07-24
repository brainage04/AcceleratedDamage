# Accelerated Damage

A server-side Fabric mod for Minecraft 26.2 that exposes configurable accelerated combat and damage mechanics as gamerules. Clients do not need to install the mod.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API
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
./gradlew runProductionServerGameTest
```

The production GameTests exercise consecutive melee damage and the bow/trident instant-shoot behavior on a dedicated server.

## License

Accelerated Damage is available under the MIT License.
