# OSRS PvP Environment

C implementation of Old School RuneScape NH PvP for reinforcement learning.

## Build

```bash
python setup.py build_osrs_pvp --inplace --force
```

Zulrah encounter is a separate env that shares headers with osrs_pvp:
```bash
python setup.py build_osrs_zulrah --inplace --force
```

## Visual mode

Standalone headed viewer with raylib. supports human play, AI observation, and encounter selection.

```bash
cd pufferlib/ocean/osrs_pvp
make visual
./osrs_pvp_visual --visual                      # PvP (watch AI vs scripted opponent)
./osrs_pvp_visual --visual --encounter zulrah   # Zulrah
./osrs_pvp_visual --visual --human              # human control (click to move/attack)
```

requires raylib 5.5 — download from https://github.com/raysan5/raylib/releases and extract to `raylib-5.5_macos/` (or adjust `RAYLIB_DIR` in Makefile for linux).

## Data assets

Not in git. Exported from the OSRS game cache:

1. Download a modern cache from https://archive.openrs2.org/ ("flat file" export)
2. `cd pufferlib/ocean/osrs_pvp && ./scripts/export_all.sh /path/to/cache`

Pure Python, no deps.

## Spaces

**Obs:** 373 = 334 features + 39 action mask, normalized in C.

**Actions:** MultiDiscrete `[9, 13, 6, 2, 5, 2, 2]` — loadout, combat, prayer, food, potion, karambwan, veng.

**Timing:** tick N actions apply at tick N+1 (OSRS-accurate async).

## Opponents

28 scripted policies from trivial (`true_random`) to boss (`nightmare_nh` — onetick + 50% action reading). Curriculum mixes and PFSP supported.

## Encounters

Vtable interface (`osrs_encounter.h`). Current: NH PvP, Zulrah (81 obs, 6 heads, 3 forms, venom, clouds, collision).

## Files

Core env: `osrs_pvp_types/items/gear/combat/collision/pathfinding/movement/observations/actions/opponents/api.h`

Visual: `osrs_pvp_render/gui/anim/models/terrain/objects/effects/human_input.h`

Encounters: `encounters/encounter_nh_pvp.h`, `encounters/encounter_zulrah.h`

Data: `data/` (gitignored binaries + C model headers), `scripts/` (cache exporters)
