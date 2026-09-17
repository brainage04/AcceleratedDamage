# AcceleratedDamage icon

## What this is

`docs/icon/icon.png` — the mod's icon: 36x36 PNG, 8-bit RGBA, non-interlaced, 569 bytes,
sha256 `8e633c5db6034e3563cd73f2f45b71aab265e82570f26d4a273eca37d632a36b`.

## How it was made

**Generated pixel art derived from real vanilla HUD/effect textures** — no renderer, no ML,
no antialiasing. `provenance/pixel2/render.py` (Pillow 12.3.0) composes it deterministically
with integer/nearest-neighbour operations only:

1. The real Speed status-effect sprite (18x18) is scaled **2x with NEAREST** as the
   background, filling the 36x36 canvas.
2. The hardcore heart is assembled at its **native 9x9** size
   (`container_hardcore` outline + `hardcore_half` fill, alpha-composited), scaled 2x to
   18x18, and pasted at (2, 10) — i.e. moved **1 px down and 7 px left** of its centred
   position.
3. Native HUD proportions are preserved exactly: effect icon 18x18 and heart 9x9, both drawn
   at 2x.

Source textures (vanilla Java **1.21.4** client jar; no shader pack, no in-game capture, no
camera):

| texture | jar member | sha256 |
|---|---|---|
| `provenance/pixel2/sources/speed.png` | `assets/minecraft/textures/mob_effect/speed.png` | `ba388036b839b85b04f300aaf81cbf73c452f43ecc4becdfb7296535962086a9` |
| `provenance/pixel2/sources/container_hardcore.png` | `assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore.png` | `5dad1a14187bf035d1cd56a5477f162e8f3baadeccadcc20e11f58db5a26ac0f` |
| `provenance/pixel2/sources/hardcore_half.png` | `assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half.png` | `2c8e12d1b2389b4f82fcadbe1bfabcbe354f33bd763737606124a1c4d236c1ae` |

The hashes were re-verified against the actual jar members. Re-extract with:

```
unzip -p client-1.21.4.jar assets/minecraft/textures/mob_effect/speed.png                            > speed.png
unzip -p client-1.21.4.jar assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore.png    > container_hardcore.png
unzip -p client-1.21.4.jar assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half.png         > hardcore_half.png
```

## Provenance files

| file | what it is |
|---|---|
| `provenance/pixel2/render.py` | the author script that generated this icon (and the other round-3 pixel icons) |
| `provenance/pixel2/metadata.json` | this icon's entry extracted from `provenance/from-round3/pixel2/manifest.json`: label, method, source line, notes |
| `provenance/pixel2/sources/*.png` | the three client-jar sprites this icon is built from (the other two siblings belong to SimpleTwitchChat/BrainageMinigames/TwitchPlaysMinecraft) |
| `provenance/pixel/sources/*.png` | the remaining textures the shared script reads for its other sections, kept so the script runs unmodified |
| `provenance/pixel/source-provenance.json` | jar member / sha256 / origin per source file, and which files this icon uses |

## How to regenerate

From `docs/icon/provenance` (Pillow 12.3.0):

```
python3 pixel2/render.py
```

This rewrites all six `accelerated-damage-*.png` variants (the other five offsets are not
shipped) and the other round-3 pixel icons; compare
`pixel2/accelerated-damage-down1-left7.png` with the sha256 above.

## Notes

- The chosen variant of the six generated offsets is **down 1 px, left 7 px**. The other five
  (`down1-left3/5`, `down3-left3/5/7`) are deliberately not copied; the offsets differ only
  in the heart's placement, and the script parameterises them.
- The heart outline and fill are both genuine vanilla HUD sprites, so the icon uses the real
  hardcore-heart pixel art rather than a redrawn heart.
- Everything is scaled with NEAREST at integer factors, so no antialiasing is expected and
  none is present.
- Not copied: the other candidate variants, the retired `get-enchant-info.png`, and the 28 MB
  `pixel2/jar/client-1.21.4.jar` (over the 5 MB single-file limit; the shipped textures are
  enough to regenerate this icon).

## Working-tree note

The round-3 working tree that produced this icon was cleaned up after integration. Every file needed to regenerate the icon was copied into `provenance/`; the copies live under `provenance/from-round3/` when they came from the working tree. Any remaining `round3/...` mention records where something came from, not a path that still exists.
