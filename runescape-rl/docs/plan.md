# Fight Caves RL — Plan

## Status

Hard pivot from Kotlin/JVM to C. The Kotlin implementation is archived at branch `archive/kotlin-final` for historical reference only. No JVM code exists in this repo.

The `runescape-rl/` directory is fully owned by the parent repository (`claude/`, branch `v2Claude`). The nested `.git` from the Kotlin era has been removed; its history is preserved in `runescape-rl-nested-backup.bundle` at the repo root's parent directory. (Historical note: the branch was originally named `c-rewrite` and renamed to `v2Claude` at the PR1–PR4 checkpoint.)

## Goal

Train an RL agent to clear OSRS Fight Caves (Jad + all 63 waves) using:
- A high-throughput C backend for all Fight Caves mechanics
- A Raylib-based viewer for human play, policy playback, and replay/debug
- A Python RL stack (PufferLib + W&B) for training, evaluation, and logging

## Reference Intake

Before locking contracts, these local references were reviewed to extract patterns and avoid reinventing the wheel. Findings are folded directly into this plan.

### PufferLib OSRS PvP (`pufferlib/pufferlib/ocean/osrs_pvp/`)

Production C environment for OSRS PvP combat. Key patterns adopted:

- **Flat state structs**: All player/NPC state is flat fields on a single struct (~2KB). No nested pointers, no heap allocation per tick. Enables cache locality and fast memset reset.
- **XORshift32 RNG**: Single `uint32_t rng_state` seeded at reset. All randomness flows through it. Episode = (seed, action_sequence) → fully reproducible.
- **Multi-head action space**: 7 independent heads (loadout, combat, prayer, food, potion, karambwan, veng) with per-tick action masks. Total combinatorial space is large but most masked as invalid. Fight Caves needs fewer heads (no gear switching, no PvP opponent reads).
- **Pending hits with tick countdown**: Attacks queue damage with delay (models projectile flight time). Prayer checked at landing time, not fire time. Adopt this for ranged NPC attacks and Jad.
- **Encounter vtable** (`EncounterDef`): Standard interface (create, reset, step, write_obs, write_mask, get_reward, is_terminal, fill_render_entities). Allows hot-swapping encounters. Adopt for fc_core API.
- **binding.c pattern**: ~150-line wrapper that bridges PufferLib's `vecenv.h` (double actions, float terminals) to internal types. Type conversion only — no logic. Adopt directly.
- **Raylib viewer with rewind**: `RenderClient` snapshots full state per tick (~4.5KB × 2000 ticks = 9MB). Step forward/backward via snapshot restore. Hit splats, HP bars, prayer icons, projectile flight animations, debug overlays (collision, pathfinding, safe spots). Adopt the snapshot rewind pattern.
- **Human input → same action interface**: `HumanInput` struct queues pending actions (move, attack, prayer, consumable). Mouse click → raycast → tile → action command. Same interface as RL agent. Adopt directly.
- **Per-tick flags for event detection**: `hit_landed_this_tick`, `freeze_applied_this_tick`, etc. Read in observations after step returns. Adopt for hitsplat/damage/prayer events.
- **Observation normalization**: Divisors table maps each feature to its max value. All obs normalized to [0,1]. Adopt for our observation contract.
- **Shared combat math** (`osrs_combat_shared.h`): OSRS accuracy formula, damage rolls, prayer blocking, hit delays. Reusable across encounters. Adopt for our combat resolution.

### PufferLib OSRS Zulrah (`pufferlib/pufferlib/ocean/osrs_zulrah/`)

Boss PvE encounter using the same infrastructure. Key patterns:

- **Phase/rotation state machine**: Data-driven phase table defining (position, form, phase_ticks, action_sequence). Execution is a simple timer + action index walker. Fight Caves waves are analogous — adopt this for wave progression.
- **6-head action space**: Movement (25 dirs), attack (3), prayer (5), food (3), potion (3), special (2). Simpler than PvP. Fight Caves can use a similar 5-6 head layout.
- **81 observations**: Player (16) + boss (14) + hazards (21) + snakelings (16) + meta (8) + safe tiles (4) + mask. Compact and sufficient for policy learning. Our FC obs will be similarly structured.
- **Encounter as single header**: Full Zulrah implementation in one ~2300-line header. Lightweight, no build complexity. Consider this for fc_core if it stays manageable.

### Kotlin Archive (`archive/kotlin-final`)

Domain knowledge from the abandoned Kotlin Fight Caves implementation:

- **63 waves with 15 spawn rotations each**: Wave table defines NPC composition per wave. Rotations vary spawn directions (north_west, south_east, south, south_west) to prevent memorization.
- **6 NPC types**: Tz-Kih (melee), Tz-Kek (melee, spawns 2 adds on death), Tok-Xil (ranged), Yt-MejKot (magic), Ket-Zek (high melee), TzTok-Jad (magic+ranged, prayer switching). Yt-HurKot healers spawn during Jad.
- **Jad mechanics**: 3-tick attack queue delay + client animation delay. Prayer telegraph states: Idle, MagicWindup, RangedWindup. Prayer checked at hit resolution tick. Max hits: magic 950, ranged 970 (in tenths).
- **Observation layout**: 134 floats — 30 base (player state, wave info, consumables) + 13 features × 8 visible NPCs (type, position, HP, aggro, Jad telegraph). Visible NPC cap of 8.
- **Action space**: 7 discrete actions — wait, walk_to_tile(x,y), attack_npc(idx), toggle_prayer(id), eat_shark, drink_prayer_potion, toggle_run.
- **16 reward features**: damage_dealt, damage_taken, npc_kill, wave_clear, jad_damage_dealt, jad_kill, player_death, cave_complete, food_used, prayer_potion_used, correct_jad_prayer, wrong_jad_prayer, invalid_action, movement_progress, idle_penalty, tick_penalty.
- **Equipment**: Fixed loadout (rune crossbow, black d'hide, adamant bolts). 20 sharks + 8 prayer potions (32 doses).

### Asset Sources for Viewer

- **PufferLib PvP render** (`osrs_pvp_render.h`, ~4000 lines): Complete Raylib viewer with 3D models exported from OSRS cache via Python scripts (`scripts/export_models.py`, `scripts/export_terrain.py`). Models stored as C header arrays or MDL2/TERR binaries. Animation data similarly exported. Adopt render patterns directly.
- **PufferLib collision maps**: Exported from OSRS cache. 104×104 local grid format with per-tile traversability flags.
- **RSPS/Kotlin data** (`archive/kotlin-final`): NPC definitions (IDs 2734-2746), animation IDs (9230-9300), GFX/projectile IDs, combat definitions, arena coordinates, wave data. All in TOML format under `headless-env/data/minigame/tzhaar_fight_cave/`.
- **OSRS binary cache** (`reference/legacy-headless-env/data/cache/`): Full modern OSRS cache (183MB .dat2 + 37 index files). Source for terrain meshes, 3D models, animations, sprites. PufferLib export scripts read this with a `.dat`→`.dat2` adapter.

## Architecture

### Core Principle: Single Mechanics Owner

All Fight Caves gameplay logic lives in one place: `training-env/fc_core`. This is a pure C library with no rendering, no Python, no I/O. Both the headless training kernel and the demo-env viewer are thin shells over this shared backend.

```
training-env/
├── fc_core        (static lib)  — state, tick, NPC AI, combat, prayer, pathfinding, waves
├── libfc_capi.so  (shared lib)  — plain C API + FcBatchCtx batched stepping (PR7)

demo-env/
├── fc_viewer      (executable)  — Raylib shell: render, HUD, input, replay
└── assets/        — exported terrain, models, sprites from OSRS cache

RL/
└── fc_env.py      — ctypes wrapper over libfc_capi.so, batched vectorized env
```

### What Each Layer Owns

| Component | Owns | Does NOT own |
|-----------|------|-------------|
| `fc_core` | All Fight Caves mechanics: state transitions, NPC behavior, combat math, prayer, pathfinding, wave spawning, hit resolution, deterministic RNG, debug/trace hooks | Rendering, I/O, Python bindings, batching |
| `libfc_capi.so` | Plain C shared library API: single-env and batched (FcBatchCtx) create/destroy/reset/step/getters, contract constant exports. Wraps fc_core for any FFI consumer. | Mechanics (delegates to fc_core), Python-specific logic |
| `fc_viewer` | Raylib rendering, HUD, input handling, replay playback, rewind snapshots, debug overlays | Mechanics (calls fc_core for all state/tick) |
| `RL/fc_env.py` | ctypes bridge to libfc_capi.so, vectorized env, obs splitting, SPS measurement | Environment mechanics, rendering |

**Future optimization**: If python3-dev becomes available, a PufferLib `env_binding.h` C extension (`binding.c`) can be added alongside the ctypes path for lower per-step overhead. The ctypes path is the current production integration.

### Canonical Action Contract

A single action interface is shared across all consumers: headless RL, human playable viewer, replay playback, and policy playback. No consumer bypasses this interface.

**Multi-head command-style actions** (informed by PufferLib PvP/Zulrah patterns):

| Head | Dim | Values | Notes |
|------|-----|--------|-------|
| `MOVE` | ~17 | 0=idle, 1-8=walk cardinal+diagonal, 9-16=run cardinal+diagonal | Tile-relative movement. Walk=1 tile/tick, run=2 tiles/tick. |
| `ATTACK` | 9 | 0=none, 1-8=attack visible NPC by slot index | Visible NPC cap of 8. Attack queues via fc_core combat resolution. |
| `PRAYER` | 5 | 0=no change, 1=off, 2=protect magic, 3=protect missiles, 4=protect melee | Toggle active protection prayer. |
| `EAT` | 3 | 0=none, 1=shark, 2=combo eat (karambwan if available) | Subject to food timer lockout. |
| `DRINK` | 2 | 0=none, 1=prayer potion | Subject to potion timer lockout. |

Total: 5 heads, ~17×9×5×3×2 = 4,590 combinations (most masked).

**Action mask**: Per-tick binary mask per head value. Prevents invalid actions (eat with no food, attack nonexistent NPC, run with 0 energy). Mask packed as float array appended to observations (PufferLib convention).

**Human input mapping**: Mouse click on tile → MOVE head value. Mouse click on NPC → ATTACK head value. Click prayer icon → PRAYER head value. Click inventory item → EAT/DRINK head value. All go through the same action buffer — the viewer never calls fc_core directly for state mutation.

**Replay**: Records action buffer per tick. Playback feeds recorded actions through the same step interface.

### Observation Contract

Flat float array packed by fc_core's observation writer. Python reads this directly — it never redefines the layout.

**Finalized layout** (locked in PR 1, defined in `training-env/include/fc_contracts.h`):

```
policy_obs (126 floats):
  Player state (20):     HP, prayer, position(x,y), attack_timer, food_timer, potion_timer,
                         combo_timer, run_energy, is_running, active_prayer(3),
                         sharks_remaining, prayer_doses, ammo_count, defence_level,
                         ranged_level, damage_taken_this_tick, stunned(reserved)
  Per-NPC state (96):    8 visible NPC slots × 12 features each
                         (valid, npc_type, x, y, hp, distance, atk_style, atk_timer,
                          size, is_healer, jad_telegraph, aggro)
  Wave/meta (10):        wave_id, rotation_id, remaining_npcs, tick, total_damage_dealt,
                         total_damage_taken, damage_dealt_tick, damage_taken_tick,
                         npcs_killed_tick, wave_just_cleared

reward_features (16 floats):
  Emitted by C, transported with the obs buffer, but NOT part of the default policy input.
  Trainer reads reward features for shaping/logging; policy sees only policy_obs.
  Features: damage_dealt, damage_taken, npc_kill, wave_clear, jad_damage,
            jad_kill, player_death, cave_complete, food_used, prayer_pot_used,
            correct_jad_prayer, wrong_jad_prayer, invalid_action, movement,
            idle, tick_penalty

action_mask (36 floats):
  5 heads: MOVE(17) + ATTACK(9) + PRAYER(5) + EAT(3) + DRINK(2)
  1.0 = valid, 0.0 = invalid. Appended after reward features.
```

**Total transport per environment: 178 floats** (126 + 16 + 36).

PufferLib sees `OBS_SIZE = 178`. Python trainer slices:
- `policy_obs = obs[:126]` — fed to the policy network
- `reward_features = obs[126:142]` — for shaping/logging, NOT policy input
- `action_mask = obs[142:178]` — for masked action sampling

All values normalized to [0,1] using divisor tables. Contracts in `fc_contracts.h` are the single source of truth.

### Reward Contract

16 per-tick reward features are computed in C and packed into the observation buffer (after policy_obs, before action_mask). Python applies configurable shaping weights to produce the scalar reward — C does not hardcode the final reward.

Reward features (from Kotlin archive, validated as sufficient):

| # | Feature | Trigger |
|---|---------|---------|
| 0 | damage_dealt | NPC HP reduced this tick |
| 1 | damage_taken | Player HP reduced this tick |
| 2 | npc_kill | NPC death count this tick |
| 3 | wave_clear | All wave NPCs dead |
| 4 | jad_damage_dealt | Jad HP reduced this tick |
| 5 | jad_kill | Jad defeated |
| 6 | player_death | Player HP ≤ 0 |
| 7 | cave_complete | All 63 waves cleared |
| 8 | food_used | Shark consumed this tick |
| 9 | prayer_potion_used | Potion consumed this tick |
| 10 | correct_jad_prayer | Prayer matched Jad attack at hit resolution |
| 11 | wrong_jad_prayer | Prayer did not match Jad attack |
| 12 | invalid_action | Rejected/masked action attempted |
| 13 | movement_progress | Walk/run action executed |
| 14 | idle_penalty | Wait/idle action |
| 15 | tick_penalty | Every tick (time discount) |

### Determinism, Replay, and Debug

Determinism and replay are first-class from PR 1, not deferred.

**Determinism guarantee**: The C backend is fully deterministic given (seed, action_sequence). XORshift32 single RNG state seeded at reset. All randomness (combat rolls, NPC targeting, spawn rotations) flows through this RNG.

**Determinism verification** (built into fc_core from PR 1):
- `fc_state_hash(state)` — FNV-1a hash over explicit field values (not raw struct bytes). Padding-independent. Called per tick to verify that two runs with the same (seed, actions) produce identical state hashes at every tick.
- Determinism unit test: run episode twice with same seed+actions, assert per-tick state hash equality.

**Replay recording** (infrastructure in place, file I/O deferred to PR 7/12):
- Format: `{seed: uint32, actions: int32[num_ticks][num_heads]}` — binary, compact.
- `FcActionTrace` (fc_debug.h) records per-tick actions + state hashes in memory. This is available from PR 1 for in-process determinism verification and test canaries.
- Binary file I/O for persistent replays is deferred to PR 7 (headless kernel can write replay files during batch runs) or PR 12 (viewer replay mode loads replay files). PR 6 adds the wave system but does NOT add file I/O — it uses the existing in-memory `FcActionTrace` for determinism tests.
- Replay playback reconstructs episode entirely from determinism — no state serialization needed.

**Debug/trace hooks** (built into fc_core from PR 2):
- `fc_debug_log` — optional per-tick event log (hit events, prayer toggles, NPC spawns/deaths, wave transitions). Disabled by default (zero cost). Enabled via compile flag or runtime toggle.
- `fc_trace_actions` — records (tick, action_heads[]) for seed+action replay compatibility.
- These hooks are sufficient to diagnose simulation drift, incorrect combat resolution, and policy misbehavior without the viewer.

### Viewer: Asset and Render Requirements

**Priority**: Viewer completion (asset-backed rendering + human-playable mode + debug usability) is a **pre-training requirement**. Training does not begin until the viewer looks and plays similar to actual RuneScape Fight Caves. Policy playback depends on a trained checkpoint and can remain after training begins.

**Asset provenance** (required path, not optional polish):
- **Cache-exported / RSPS-derived assets**: OSRS cache sprites/models exported via Python scripts (pattern from PufferLib PvP `scripts/export_models.py`, `scripts/export_terrain.py`). Stored as C header arrays or binary blobs in `demo-env/assets/`. NPC models, player equipment, terrain tiles, prayer icons, hitsplat sprites, inventory icons.
- **Kotlin demo-env reference**: UI layout, asset loading patterns, combat visual presentation from the archived Kotlin Fight Caves demo.
- **PufferLib OSRS PvP viewer reference**: 3D render pipeline with Raylib, model/terrain export scripts, HUD layout, hit splats, prayer icons, debug overlays. Adopt render patterns directly where applicable.
- **OSRS binary cache available**: Full modern OSRS cache at `/home/joe/projects/runescape-rl/reference/legacy-headless-env/data/cache/` (183MB `.dat2` + 37 index files). PufferLib export scripts can read this with a `.dat` → `.dat2` filename adapter. This unblocks terrain, model, animation, and sprite extraction for Fight Caves.
- **Placeholders only where extraction is genuinely blocked**: Each remaining placeholder must be explicitly documented with the reason it could not be asset-backed.

**Tile/world-to-screen coordinate ownership**:
- fc_core owns the game-world coordinate system: integer tile grid (x, y). The Fight Caves arena is approximately 64×64 tiles.
- The viewer owns the screen projection: tile (x, y) → screen pixel (px, py). Top-down or isometric, configurable. Camera zoom, pan, center-on-player.
- fc_core exposes arena bounds (min_x, min_y, max_x, max_y) and per-tile flags (walkable, obstacle, safe spot) for the viewer to render.

**Data fc_core must expose for rendering** (via `fill_render_entities` callback, PufferLib pattern):
- Player: position, HP, prayer, equipment slots, active prayer icon, attack animation state, pending hit events (for hitsplats)
- Per NPC: position, type, HP, max HP, size (1×1 or 2×2+), aggro target, attack animation state, death state, Jad telegraph state
- Arena: walkable tile mask, obstacle positions, spawn points
- Wave: current wave number, NPCs remaining
- Combat events this tick: hitsplat list (entity_id, damage, style), prayer activation events, NPC spawn/death events
- Debug data (optional): pathfinding grid, aggro ranges, reward signal breakdown, safe spot analysis

**HUD data fc_core must expose**:
- Player HP / max HP, prayer / max prayer, run energy
- Inventory: shark count, prayer potion doses
- Ammo count
- Active prayer
- Wave number, total kill count

### Viewer Modes

The viewer is a Raylib presentation/input/replay shell. It does not own separate mechanics. All gameplay calls go through fc_core.

**1. Human Playable**
- Click-to-move, click-to-attack, click-to-use-item, toggle prayers
- All input goes through the canonical multi-head action interface
- Manual controls do not bypass game rules
- Real-time tick rate (~0.6s/tick OSRS standard, or configurable)

**2. Policy Playback**
- Load a trained policy checkpoint (PyTorch)
- Viewer steps fc_core, packs observations, feeds to policy, applies returned actions
- Real-time or configurable speed
- Overlay: agent's chosen action per head, value estimate, action probabilities

**3. Replay/Debug**
- Load a recorded (seed, action_sequence) replay file
- Step forward/backward via deterministic re-execution (forward) or snapshot rewind (backward)
- Pause, single-step, slow-motion, fast-forward
- Debug overlays (individually toggleable):
  - Pathfinding visualization (BFS expansion)
  - NPC aggro ranges and target selection
  - Per-tick reward signal breakdown
  - Safe spot analysis (tiles not reachable by current NPCs)
  - State inspection panel (hover/click entity for internal values)
  - Hit event log (scrolling combat text)
  - Per-tick state hash (for determinism verification)

## RL Stack

### Framework

- **PufferLib** for vectorized environment management and training loops
- **PyTorch** for policy networks
- **W&B** for experiment tracking and logging

### Environment Integration

Python calls into the C backend via `libfc_capi.so` loaded through ctypes (`RL/src/fc_env.py`). The shared library exposes create/reset/step/getters. All contract constants (obs size, action dims, mask size) are exported from C and read at Python import time — no magic numbers in Python.

`FightCavesVecEnv` wraps N independent C env contexts, steps them per-tick, and copies obs/reward/terminal into numpy arrays. It never interprets observation fields or reconstructs game state. `split_obs()` provides the trainer with separated (policy_obs, reward_features, action_mask) slices.

### Policies

Start simple (MLP), graduate to more complex architectures as needed. Policy architecture is independent of the C backend — it only sees the flat observation+mask contract.

Multi-head action output: one softmax per action head, sampled independently. Action masks applied before sampling (masked logits → -inf).

### Training Metrics (tracked continuously)

Performance metrics:
- Steps per second (SPS)
- Episodes per second
- GPU utilization, memory usage

Agent metrics:
- Wave reached (mean, max, distribution)
- Jad kills, Jad prayer accuracy
- Deaths by cause (HP depletion, which wave)
- DPS efficiency, prayer potion efficiency
- Episode length

These will be maintained in `docs/training_performance.md` and `docs/agent_performance.md` as living documents once training begins.

## Current Implemented State

**PR 1 — Complete.** Core state, contracts, determinism infrastructure.
- `fc_types.h`: FcState, FcPlayer, FcNpc, FcPendingHit, FcRenderEntity, all enums.
- `fc_contracts.h`: 178-float transport (126 policy_obs + 16 reward_features + 36 action_mask). 5 action heads (MOVE/ATTACK/PRAYER/EAT/DRINK). Single source of truth.
- `fc_api.h`: init, reset, step, destroy, write_obs, write_mask, write_reward_features, is_terminal, state_hash, fill_render_entities, RNG.
- `fc_debug.h`: Debug log (compile-time FC_DEBUG toggle), action trace for replay.
- `fc_state.c`, `fc_rng.c` (XORshift32), `fc_hash.c` (FNV-1a over explicit fields, padding-independent), `fc_debug.c`.
- 368 test assertions passing.

**PR 2 — Complete.** Minimal deterministic combat slice (player vs 1 Tz-Kih).
- `fc_tick.c`: Full tick loop — clear flags → actions → timers → prayer drain → NPC AI → resolve hits → check terminal → tick++.
- `fc_combat.c`: OSRS accuracy formula, NPC attack roll/max hit, player ranged attack, PvM prayer block (100% on correct match), pending hit queue with tick delay, hit resolution.
- `fc_prayer.c`: 3 protection prayers, per-tick drain, prayer potion restore.
- `fc_pathfinding.c`: Greedy diagonal-first walk/run, NPC step-toward.
- `fc_npc.c`: NPC stat table pre-populated for all 9 types (NPC_NONE through NPC_YT_HURKOT). Spawn, AI tick (move + attack). Tz-Kih fully functional; other types have stats, AI extensions deferred to PR 5.
- Golden trace artifact: seed 777, 20 ticks, final hash `0x89d019cd`. Catches simulation drift.
- 64 test assertions passing (432 total with PR 1).

**PR 3 — Complete.** Python binding and smoke tests.
- `fc_capi.h` / `fc_capi.c`: Plain C shared library API (libfc_capi.so). create/destroy/reset/step/getters, batch interface, contract constant exports. No Python.h dependency.
- `RL/src/fc_env.py`: ctypes wrapper — `FightCavesVecEnv` with split_obs(), smoke test, vectorized test, SPS benchmark.
- All contract constants read from C at import time (no duplicated magic numbers).
- Reward features separable: `split_obs()` returns (policy_obs[126], reward_features[16], action_mask[36]).
- Auto-reset on terminal: C side resets with RNG-derived seed and writes fresh obs.
- **SPS baseline: 335,800** (64 envs, per-env ctypes loop). PufferLib env_binding.h path deferred (python3-dev unavailable).
- Smoke test: 4 envs × 100 steps, shapes/ranges/masks verified. Vectorized test: 16 envs diverge correctly.

**PR 4 — Complete.** Minimal Raylib viewer shell.
- `demo-env/src/viewer.c` (~435 lines): single-file viewer combining render, HUD, debug overlay, and controls.
- Placeholder top-down tile grid rendering: player = blue circle, NPCs = type-colored rectangles (red=Tz-Kih, green=Tok-Xil, cyan=Yt-MejKot, purple=Ket-Zek, orange=Jad, yellow=Yt-HurKot), walkable tiles = dark gray, obstacles = darker.
- Shared-backend rendering via `fc_fill_render_entities` — viewer never accesses FcState fields directly.
- HP bars above all entities, prayer indicator above player (M/R/P text), Jad telegraph indicator (MAG/RNG text), hitsplat damage numbers on hit.
- HUD overlay (toggleable with H): HP, prayer, active prayer name, sharks/doses, ammo, wave/kills/remaining, tick/episode, player position, timers, cumulative damage, pause/speed status.
- Debug overlay (toggleable with D): state hash, seed/rotation, action head values, terminal/damage-this-tick, zoom level.
- Controls: Space (pause/resume), Right (single-step while paused), Up/Down (tick speed ±, range 1–30 tps), R (reset episode), H (toggle HUD), D (toggle debug), Q/Esc (quit), scroll wheel (zoom 0.3x–5.0x), middle-mouse drag (camera pan).
- Demo mode: random actions each tick with occasional idle for readability. Auto-pause on terminal.
- Linked against `fc_core` static lib + Raylib 5.5 pre-built distribution.
- Current limitations:
  - No human-playable input (click-to-move, click-to-attack deferred to PR 10).
  - No policy playback (deferred to PR 11).
  - No replay file I/O or rewind (deferred to PR 12).
  - No step-backward (forward only; snapshot rewind is a PR 12 feature).

**PR 5 — Complete.** All NPC Types — AI and Special Mechanics.
- All 8 NPC types have full AI: type-specific attack, movement, and special behavior.
- Real NPC defence stats (`def_level`, `def_bonus`): player accuracy now varies by target (>80% vs Tz-Kih, <30% vs Ket-Zek).
- `fc_magic_hit_delay(distance)`: 1 + floor((1+dist)/3).
- Tz-Kek split-on-death: spawns 2 NPC_TZ_KEK_SM at death position, reuses dead NPC's slot.
- Tok-Xil: ranged (range 6), projectile delay, blocked by protect range.
- Yt-MejKot: melee + heals nearby NPCs (≤3 tiles) for 10 HP every 8 ticks.
- Ket-Zek: magic (range 8), projectile delay, blocked by protect magic.
- Jad: 3-state telegraph (IDLE → MAGIC_WINDUP/RANGED_WINDUP → fire), 3-tick windup, RNG style selection, magic max 970 / ranged max 950 tenths.
- Yt-HurKot: walks to Jad and heals 10 HP/4 ticks; player attack distracts permanently.
- 72 test assertions passing (504 total). PR5 golden trace: seed 55555, hash `0x8baf02d4`.
- Outstanding: Yt-HurKot auto-spawn at Jad low HP is a wave-system feature (PR 6).

**PR 6 — Complete.** Wave System (All 63 Waves + Jad).
- `src/fc_wave.c` (~1250 lines): 63-wave spawn table + 15 rotations/wave. Wave data sourced from Kotlin archive TOML and embedded as static C arrays.
- `fc_reset` auto-spawns wave 1. Wave-clear triggers `fc_wave_check_advance` → next wave spawn. All 63 waves → TERMINAL_CAVE_COMPLETE.
- Wave-clear correctly accounts for Tz-Kek split spawns (splits increment `npcs_remaining`).
- Jad (wave 63): spawns via wave table. When HP drops below 50%, 4 Yt-HurKot auto-spawn around Jad.
- Spawn directions: SOUTH (32,5), SOUTH_WEST (8,8), NORTH_WEST (8,55), SOUTH_EAST (55,8), CENTER (32,32). Per-wave rotation selected at reset via RNG.
- Full episode: reset → 63 waves of combat → Jad + healers → cave complete or player death. Deterministic given (seed, action_sequence).
- 57 test assertions passing (560 total). PR6 golden trace: seed 66666, hash `0x7c0d19cd`.

**PR 8 — Foundation complete, parity NOT yet complete (PR8a+PR8b+PR8c).**
- **PR8a**: Identified all FC asset sources. Found OSRS binary cache at `reference/legacy-headless-env/data/cache/`. Decoded VoidPS NPC definitions to extract model IDs.
- **PR8b**: Built asset export pipeline. Exported 8 NPC 3D models (686KB MDL2), 14 UI sprites (prayer icons, slots, tabs).
- **PR8c**: Integrated NPC 3D models via MDL2 loader. Prayer icon sprites as Raylib textures. Dual 3D/2D mode.
- **Not yet done** (required for viewer parity):
  - Cache/RSPS-derived Fight Caves terrain and object rendering (procedural fallback not accepted as final).
  - Actual player rendering with the fixed FC gear/loadout (blue cylinder not accepted).
  - RS-like tabbed UI with inventory/prayer/combat panels and real item sprites.
  - Clickable inventory items and prayer toggles in the UI.
  - Human-playable controls through canonical actions.
- Training (PR11) remains blocked until viewer parity is accepted (PR9a + PR9b).

**PR 7 — Complete.** Batched Headless Kernel and Benchmarks.
- `FcBatchCtx` in `fc_capi.c`: contiguous array of N `FcEnvCtx` structs. `fc_capi_batch_step_flat` steps all envs in one C call with flat numpy-compatible arrays.
- `FightCavesVecEnv` rewritten to use batched C path — zero per-env Python loop in the hot path.
- **SPS @ 64 envs: 3,353,676** (up from 335,800, +899%). Peak: 4,617,173 @ 1024 envs.
- Batch independence verified: batch envs produce identical results to individually-run single envs.

**Accepted tradeoffs and outstanding items:**
- **8-visible-NPC overflow**: Observation slots are limited to the 8 closest active NPCs (Chebyshev distance, spawn_index tiebreak). Overflow NPCs (9+) are simulated but invisible to the policy and untargetable via ATTACK head. This covers the vast majority of waves; `FC_VISIBLE_NPCS` can be increased if testing reveals a need.
- **Observation slot-to-attack alignment**: ATTACK head value N targets the NPC in observation slot N-1. The slot ordering (distance, spawn_index) is identical in the obs writer and action resolver.

## Required Viewer Features (pre-training)

The viewer must reach the following bar before training begins:

- **Terrain**: Cache/RSPS-derived Fight Caves arena, not procedural colored tiles.
- **Player**: Actual player representation with the fixed FC gear (rune crossbow, black d'hide, etc.), not a primitive shape.
- **NPCs**: 3D models from cache (done in PR8c).
- **Tabbed UI**: RS-like right-side panel with switchable tabs (inventory, prayer, combat/stats). Not just a flat text panel.
- **Inventory**: Visible item sprites for sharks, prayer potions, adamant bolts in inventory slots. Clickable to use.
- **Prayer panel**: Prayer icons (on/off states) for the 3 protection prayers. Clickable to toggle.
- **Human-playable controls**: Click-to-move, click-to-attack, click inventory item, click prayer toggle — all routed through canonical actions.
- **Hitsplats, HP bars, overhead prayer**: Already partially done; must work in the final integrated view.

## Non-Goals

- Pixel-perfect OSRS UI replication (close and functional, not pixel-identical)
- Full OSRS game implementation (Fight Caves only)
- Java/Kotlin/JVM code in any form
- Separate mechanics implementations for training vs. viewing
- Python rebuilding environment semantics
- Full OSRS client feature set (we need RS-like Fight Caves play/debug, not a general client)

## Docs Maintenance Rules

- **plan.md** changes only when architecture, contracts, accepted tradeoffs, or current implemented truth changes. It is the source of truth for what the system IS, not what it will be.
- **pr_plan.md** changes after every PR to record: status, delivered scope, acceptance evidence, deviations from plan, downstream implications, and the exact next PR. It is the execution log.
- No important implementation decision may live only in chat. If it affects contracts, architecture, or future PRs, it must be in one of these two docs.
