# Agent Handoff Document

## 1. What this project is

**Fight Caves RL** — a reinforcement learning environment that simulates the Fight Caves minigame from RuneScape. Three components:

1. **C simulation backend** (`training-env/`) — deterministic headless Fight Caves sim. All 63 waves, 8 NPC types, combat, prayer, food/potions. Complete and working.
2. **Python RL binding** (`RL/`) — ctypes wrapper, vectorized env, training pipeline. Complete and working.
3. **C/Raylib debug viewer** (`demo-env/`) — 3D visual viewer rendering the sim state with OSRS cache assets. Under active development.

## 2. Current state (as of 2026-04-02)

The debug viewer renders:
- **Terrain floor**: heightmapped mesh from b237 OSRS cache (black, no lava texture yet)
- **Cave walls/cliffs**: 3,867 placed objects, 26 unique models, 424K triangles with texture atlas
- **All 8 NPC types**: colored placeholder cubes (correct sizes, distinct colors)
- **Player**: blue cylinder
- **Full UI**: HP/prayer bars, wave display, inventory counts, debug overlay
- **Camera**: orbit, zoom, aerial/gameplay presets

## 3. Next steps

**Phase A (Priority): Playable game** — wire keyboard input to the headless sim backend so the viewer becomes a fully playable Fight Caves with wave progression, combat, prayer switching, food/potions.

**Phase B: NPC models** — replace placeholder cubes with actual NPC meshes from cache.

**Phase C: Floor texture** — replace black floor with lava crack pattern.

See `demo-env/docs/debug_viewer_plan.md` for full details.

## 4. Key architecture decisions

- **Backend**: the headless C sim (`training-env/`) IS the game logic. No separate implementation.
- **Graphics cache**: b237 modern OSRS from openrs2.org (NOT the Void 634 cache).
- **Export pipeline**: Python scripts (from valo_envs) decode cache → binary assets → C/Raylib loads.
- **b237 opcode fix**: opcodes 6/7 read int32 model IDs (not uint16). Reference: RuneLite commit c2e1eb3.

## 5. Repository layout

```
runescape-rl/
├── training-env/          # C headless sim (the backend)
├── demo-env/              # C/Raylib debug viewer
│   ├── src/viewer.c       # Main viewer
│   ├── src/osrs_pvp_terrain.h  # Terrain loader (from valo_envs)
│   ├── src/osrs_pvp_objects.h  # Objects loader (from valo_envs)
│   ├── assets/            # Exported binary assets
│   └── docs/debug_viewer_plan.md  # Detailed plan
├── RL/                    # Python RL environment
└── docs/                  # Project docs
```

External:
- `/home/joe/projects/runescape-rl-reference/valo_envs/ocean/osrs/` — export scripts
- `/home/joe/projects/runescape-rl-reference/runelite/` — RuneLite (b237 opcode reference)
- `/home/joe/projects/runescape-rl-reference/current_fightcaves_demo/` — oracle (validation only)
- `~/Downloads/cache-oldschool-live-en-b237-*.tar.gz` — cache download

## 6. Build and run

```bash
# Extract cache (needed after reboot)
mkdir -p /tmp/osrs_cache_modern && cd /tmp/osrs_cache_modern && \
tar xzf ~/Downloads/cache-oldschool-live-en-b237-*.tar.gz

# Build viewer
cd /home/joe/projects/runescape-rl/claude/runescape-rl
cmake -B build -DCMAKE_BUILD_TYPE=Release \
  -DRAYLIB_ROOT=/home/joe/projects/runescape-rl/claude/pufferlib/raylib-5.5_linux_amd64
cmake --build build -j$(nproc)

# Run
cd build && ./demo-env/fc_viewer
```

## 7. Documents to read

1. `demo-env/docs/debug_viewer_plan.md` — current plan, architecture, next steps
2. `training-env/include/fc_types.h` — sim types, NPC definitions
3. `training-env/include/fc_api.h` — sim public API
4. `demo-env/src/viewer.c` — the viewer (~340 lines)
