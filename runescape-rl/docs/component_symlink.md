# Component / Symlink Refactor Plan

Status: planning only. No code moves are performed by this document.

Objective:
- eliminate mirrored Fight Caves backend code between `training-env` and `demo-env`
- make parity structural instead of manual
- keep pure simulation code separate from trainer glue and viewer glue
- prevent the two failure modes called out by the user:
  - non-shared code left inside a shared file
  - shared code left trapped in a non-shared file

Decision:
- The final architecture should use a real shared source tree, not long-term pairwise symlinks between `training-env/src` and `demo-env/src`.
- Symlinks are acceptable only as a temporary compatibility aid after a file has been made 100% shared and its ownership is already clean.
- Do not symlink mixed files.


## 1. Current-State Audit

### 1.1 Current mirrored backend files

These files currently exist under both:
- `training-env/src`
- `demo-env/src`

Intended shared backend set:
- `fc_api.h`
- `fc_capi.c`
- `fc_capi.h`
- `fc_combat.c`
- `fc_combat.h`
- `fc_contracts.h`
- `fc_npc.c`
- `fc_npc.h`
- `fc_pathfinding.c`
- `fc_pathfinding.h`
- `fc_player_init.h`
- `fc_prayer.c`
- `fc_prayer.h`
- `fc_rng.c`
- `fc_state.c`
- `fc_tick.c`
- `fc_types.h`
- `fc_wave.c`
- `fc_wave.h`

These are the files that currently carry the core Fight Caves simulation contract and are the primary candidates for a single shared source of truth.

### 1.2 Training-only files currently outside the mirrored set

Under `training-env/`:
- `binding.c`
- `fight_caves.h`
- `fight_caves.c`
- `fc_render.h`
- `build.sh`
- `CMakeLists.txt`

These are not pure backend mechanics. They contain trainer integration, reward shaping, analytics aggregation, optional rendering glue, and build logic.

### 1.3 Demo-only files currently outside the mirrored set

Under `demo-env/src`:
- `viewer.c`
- `fc_anim_loader.h`
- `fc_debug.c`
- `fc_debug.h`
- `fc_debug_overlay.h`
- `fc_hash.c`
- `fc_npc_models.h`
- `fc_objects_loader.h`
- `fc_terrain_loader.h`

Under `demo-env/tests`:
- `test_headless.c`

Under `demo-env/`:
- `CMakeLists.txt`

These files are viewer, rendering, asset loading, debug UI, determinism tooling, and tests. They are not all the same kind of thing and should not all be lumped into one bucket during the refactor.

### 1.4 Eval-only code

Top-level:
- `eval_viewer.py`

This is not a simulation backend. It is a Python policy loader and viewer subprocess driver.

### 1.5 Build/docs mismatch already present

Important existing mismatch:
- `README.md` claims that `demo-env` already links against the same shared backend as training.
- The repository does not actually do that yet. `demo-env/CMakeLists.txt` still compiles its own `fc_*.c` list, and `training-env/CMakeLists.txt` still compiles its own separate `fc_*.c` list.

This means the repo already advertises an architecture it does not yet implement.

### 1.6 Operational duplication already causing drift

There are also non-core file duplications that are already causing local
maintenance errors and should be folded into the same refactor pass.

Current duplicated operational file:
- `runescape-rl/config/fight_caves.ini`
- `pufferlib_4/config/fight_caves.ini`

Current behavior:
- `runescape-rl/config/fight_caves.ini` is the file humans edit.
- `pufferlib_4/config/fight_caves.ini` is the file PufferLib actually reads.
- launch scripts copy the first into the second before training.

Why this is a problem:
- it creates silent config drift when one file is edited and the other is not
- it makes repo state misleading because both copies appear canonical
- it already caused the `v18.3` sweep config and `v18.3_control` config to
  diverge accidentally

Other operational duplication:
- launch scripts repeat the same environment/bootstrap block in multiple files:
  - `.venv` activation
  - `PUFFER_DIR`
  - `FC_COLLISION_PATH`
  - W&B directory exports
  - cuDNN/lib path setup
  - backend existence/build check

This is not a symlink target by itself, but it is still the same maintenance
failure mode: one logical runtime contract duplicated in multiple places.


## 2. Ownership Model To Implement

The repo should be split into five ownership zones:

1. Shared core simulation
2. Shared support (cross-consumer utilities that are not simulation)
3. Training-only
4. Demo/viewer-only
5. Eval-only

This extra "shared support" zone is necessary. Without it, non-simulation helpers like render snapshots and determinism hashing either pollute core simulation files or get stranded in viewer-only code even though multiple consumers need them.

Operational rule to add:
- one source of truth for training config
- one shared shell helper for repeated launcher/bootstrap environment setup


## 3. Proposed Target Layout

Recommended target structure:

```text
runescape-rl/
  fc-core/
    include/
      fc_api.h
      fc_capi.h
      fc_combat.h
      fc_contracts.h
      fc_npc.h
      fc_pathfinding.h
      fc_player_init.h
      fc_prayer.h
      fc_types.h
      fc_wave.h
    src/
      fc_capi.c
      fc_combat.c
      fc_npc.c
      fc_pathfinding.c
      fc_prayer.c
      fc_rng.c
      fc_state.c
      fc_tick.c
      fc_wave.c
    assets/
      fightcaves.collision

  fc-support/
    include/
      fc_hash.h
      fc_render_bridge.h
      fc_trace.h
    src/
      fc_hash.c
      fc_render_bridge.c
      fc_trace.c

  training-env/
    binding.c
    CMakeLists.txt
    build.sh
    fc_launch_common.sh
    fight_caves_env.c
    fight_caves_env.h
    fc_reward_shaping.c
    fc_reward_shaping.h
    fc_train_metrics.c
    fc_train_metrics.h
    fc_render.h
    fight_caves.c

  demo-env/
    CMakeLists.txt
    src/
      viewer.c
      fc_debug_overlay.h
      fc_debug.c
      fc_debug.h
      fc_anim_loader.h
      fc_npc_models.h
      fc_objects_loader.h
      fc_terrain_loader.h
    tests/
      test_headless.c
    assets/
      ...

  eval_viewer.py
```

Notes:
- `fc-core` contains only deterministic gameplay state + mechanics + contracts + raw C API.
- `fc-support` contains optional shared helpers used by viewer/tests/training-render, but not by the core tick simulation itself.
- `training-env` contains all PufferLib-specific code, reward shaping, logging, curriculum, and trainer-facing wrappers.
- `demo-env` contains all UI, rendering, model loading, overlays, and manual/debug interaction.
- `runescape-rl/config/fight_caves.ini` remains the single human-edited config source.
- `pufferlib_4/config/fight_caves.ini` should become a symlink to that file or be removed from the runtime path entirely.


## 4. File-By-File Ownership Map

### 4.1 Shared core: move as shared source of truth

These files should become one shared copy under `fc-core`:

| Current File | Target Owner | Target Path | Action |
|---|---|---|---|
| `training-env/src/fc_rng.c` + `demo-env/src/fc_rng.c` | shared core | `fc-core/src/fc_rng.c` | move as-is |
| `training-env/src/fc_combat.c` + `demo-env/src/fc_combat.c` | shared core | `fc-core/src/fc_combat.c` | move as-is |
| `training-env/src/fc_combat.h` + `demo-env/src/fc_combat.h` | shared core | `fc-core/include/fc_combat.h` | move as-is |
| `training-env/src/fc_npc.c` + `demo-env/src/fc_npc.c` | shared core | `fc-core/src/fc_npc.c` | move as-is |
| `training-env/src/fc_npc.h` + `demo-env/src/fc_npc.h` | shared core | `fc-core/include/fc_npc.h` | move as-is |
| `training-env/src/fc_pathfinding.c` + `demo-env/src/fc_pathfinding.c` | shared core | `fc-core/src/fc_pathfinding.c` | move as-is |
| `training-env/src/fc_pathfinding.h` + `demo-env/src/fc_pathfinding.h` | shared core | `fc-core/include/fc_pathfinding.h` | move as-is |
| `training-env/src/fc_prayer.c` + `demo-env/src/fc_prayer.c` | shared core | `fc-core/src/fc_prayer.c` | move as-is |
| `training-env/src/fc_prayer.h` + `demo-env/src/fc_prayer.h` | shared core | `fc-core/include/fc_prayer.h` | move as-is |
| `training-env/src/fc_wave.c` + `demo-env/src/fc_wave.c` | shared core | `fc-core/src/fc_wave.c` | move as-is |
| `training-env/src/fc_wave.h` + `demo-env/src/fc_wave.h` | shared core | `fc-core/include/fc_wave.h` | move as-is |
| `training-env/src/fc_tick.c` + `demo-env/src/fc_tick.c` | shared core | `fc-core/src/fc_tick.c` | move as-is |
| `training-env/src/fc_contracts.h` + `demo-env/src/fc_contracts.h` | shared core | `fc-core/include/fc_contracts.h` | move as-is |
| `training-env/src/fc_player_init.h` + `demo-env/src/fc_player_init.h` | shared core | `fc-core/include/fc_player_init.h` | move as-is |

These are already pure backend mechanics or contracts and should be centralized first.

### 4.2 Shared core: move after removing mixed content

These files belong in shared core, but only after code is extracted out of them.

| Current File | Target Owner | Target Path | Required Split |
|---|---|---|---|
| `training-env/src/fc_types.h` + `demo-env/src/fc_types.h` | shared core | `fc-core/include/fc_types.h` | remove render snapshot types |
| `training-env/src/fc_api.h` + `demo-env/src/fc_api.h` | shared core | `fc-core/include/fc_api.h` | remove render/hash declarations |
| `training-env/src/fc_state.c` + `demo-env/src/fc_state.c` | shared core | `fc-core/src/fc_state.c` | remove render snapshot function; possibly separate collision asset lookup |
| `training-env/src/fc_capi.h` + `demo-env/src/fc_capi.h` | shared core | `fc-core/include/fc_capi.h` | remove stale/incorrect batch declarations and define a raw shared API only |
| `training-env/src/fc_capi.c` + `demo-env/src/fc_capi.c` | shared core | `fc-core/src/fc_capi.c` | remove trainer-specific reward and auto-reset semantics |

Details for each mixed file are in Section 5.

### 4.3 Shared support: non-simulation helpers shared across multiple consumers

These do not belong in core simulation, but they are also not strictly demo-only if the repo wants clean layering.

| Current File | Target Owner | Target Path | Reason |
|---|---|---|---|
| `demo-env/src/fc_hash.c` | shared support | `fc-support/src/fc_hash.c` | deterministic state hashing is not simulation logic, but it is reusable across viewer/tests/training diagnostics |
| `demo-env/src/fc_debug.h` | shared support | `fc-support/include/fc_trace.h` and optionally `fc-support/include/fc_debug.h` | action trace / debug log types are utilities, not gameplay |
| `demo-env/src/fc_debug.c` | shared support | `fc-support/src/fc_trace.c` and optionally `fc-support/src/fc_debug.c` | action trace implementation is reusable; circular UI log can remain demo-only if not reused |
| `FcRenderEntity` currently inside `fc_types.h` | shared support | `fc-support/include/fc_render_bridge.h` | render snapshots are derived presentation data, not simulation state |
| `fc_fill_render_entities()` currently inside `fc_state.c` | shared support | `fc-support/src/fc_render_bridge.c` | viewer/training-render snapshot bridge, not core tick logic |

Important distinction:
- `fc_hash.c` and trace helpers are cross-consumer utilities.
- They should not live in `fc-core`.
- They also should not remain hidden inside `demo-env` if other tooling needs them.

### 4.4 Training-only files

| Current File | Target Owner | Action |
|---|---|---|
| `training-env/binding.c` | training-only | keep; update includes to training wrapper only |
| `training-env/fight_caves.h` | training-only but split | replace with real `.c/.h` wrapper pair or keep name but remove embedded core compilation |
| `training-env/fight_caves.c` | training-only | keep as standalone harness or move to `training-env/tools/` |
| `training-env/fc_render.h` | training-only or retire | keep only if `FC_RENDER` mode remains supported; do not place in shared core |
| `training-env/build.sh` | training-only | keep; update to build/link shared core/support libraries |
| `training-env/CMakeLists.txt` | training-only | keep; switch from compiling mirrored sources to linking `fc-core` and optional support libs |

### 4.5 Demo-only files

| Current File | Target Owner | Action |
|---|---|---|
| `demo-env/src/viewer.c` | demo-only | keep; it is the viewer shell |
| `demo-env/src/fc_debug_overlay.h` | demo-only | keep; pure overlay/UI logic |
| `demo-env/src/fc_anim_loader.h` | demo-only | keep; raylib animation loader |
| `demo-env/src/fc_npc_models.h` | demo-only | keep; raylib model loader |
| `demo-env/src/fc_objects_loader.h` | demo-only | keep; raylib object loader |
| `demo-env/src/fc_terrain_loader.h` | demo-only | keep; raylib terrain loader |
| `demo-env/CMakeLists.txt` | demo-only | keep; change to link shared core instead of compiling duplicated backend |
| `demo-env/tests/test_headless.c` | demo-only test | keep; change to link shared core and shared support |

### 4.6 Eval-only files

| Current File | Target Owner | Action |
|---|---|---|
| `eval_viewer.py` | eval-only | keep; update header paths/binary lookup after refactor |

### 4.7 Shared / single-source operational files

These are not simulation files, but they should still stop drifting.

| Current File(s) | Target Owner | Action |
|---|---|---|
| `runescape-rl/config/fight_caves.ini` + `pufferlib_4/config/fight_caves.ini` | single-source shared config | keep `runescape-rl/config/fight_caves.ini` as canonical; make `pufferlib_4/config/fight_caves.ini` a symlink or remove the duplicate runtime read path |
| repeated launcher env setup in `train.sh`, `sweep_v18_3.sh`, and future launchers | shared training support | extract one shared shell helper such as `training-env/fc_launch_common.sh`; source it from all launchers |
| `training-env/assets/fightcaves.collision` and `demo-env/assets/fightcaves.collision` | single-source asset | keep one asset file only; move to shared location and stop probing multiple wrapper paths |

### 4.8 Assets and third-party code

Shared simulation asset:
- `training-env/assets/fightcaves.collision`
- `demo-env/assets/fightcaves.collision`

Plan:
- reduce to one shared copy under `fc-core/assets/fightcaves.collision`

Demo-only render assets remain demo-only:
- `.terrain`
- `.objects`
- `.atlas`
- `.models`
- `.anims`
- sprites
- prayer icons

Third-party code that should remain where it is:
- `demo-env/raylib/**`


## 5. Exact Mixed-File Extraction Plan

This section is the critical part. It lists the exact code that must move so no mixed ownership remains in a shared file.

### 5.1 `fc_types.h`

Current mixed ownership:
- Simulation state types
- Viewer render snapshot type

Keep in shared core:
- enums: `FcEntityType`, `FcNpcType`, `FcAttackStyle`, `FcPrayer`, `FcTerminalCode`, `FcSpawnDir`
- constants: arena, entity limits, wave counts, consumables, timers
- `FcPendingHit`
- `FcPlayer`
- `FcNpc`
- `FcWaveEntry`
- `FcState`

Move out of shared core:
- `FcRenderEntity`
- `FC_MAX_RENDER_ENTITIES`

Target destination:
- `fc-support/include/fc_render_bridge.h`

Reason:
- render snapshots are a presentation adapter
- they are derived from `FcState`
- they are not part of simulation state

### 5.2 `fc_api.h`

Current mixed ownership:
- core lifecycle and obs/mask API
- optional hash declaration
- render snapshot declaration

Keep in shared core:
- `fc_init`
- `fc_reset`
- `fc_step`
- `fc_tick`
- `fc_destroy`
- `fc_write_obs`
- `fc_write_mask`
- `fc_write_reward_features`
- `fc_is_terminal`
- `fc_terminal_code`
- `fc_rng_seed`
- `fc_rng_next`
- `fc_rng_int`
- `fc_rng_float`

Move out of shared core:
- `fc_state_hash` declaration
- `fc_fill_render_entities` declaration

Target destinations:
- `fc-support/include/fc_hash.h`
- `fc-support/include/fc_render_bridge.h`

Reason:
- hash and render-snapshot APIs are support utilities, not gameplay API

### 5.3 `fc_state.c`

Current mixed ownership:
- shared reset/state/obs/mask/reward logic
- render snapshot generation
- environment-specific collision file path search

Keep in shared core:
- collision cache data structure if asset loading remains internal
- `load_collision_once` or its replacement
- `setup_arena`
- `init_player`
- `fc_init`
- `fc_reset`
- `fc_step`
- `fc_destroy`
- NPC slot ordering helpers
- incoming-hit normalization helpers
- `npc_effective_style_now`
- `fc_write_obs`
- `fc_write_reward_features`
- `fc_write_mask`
- `fc_is_terminal`
- `fc_terminal_code`

Move out of shared core:
- `fc_fill_render_entities`

Recommended additional cleanup:
- move collision file path discovery into a small dedicated shared asset helper or reduce it to one shared asset location

Reason:
- render snapshot generation is not part of state transition or observation generation
- current path search is coupled to `training-env` and `demo-env` directory names

### 5.4 `fc_capi.h` / `fc_capi.c`

Current mixed ownership:
- raw C API for lifecycle/obs/mask
- hardcoded scalar reward shaping
- auto-reset behavior on terminal
- batch helpers
- header/implementation mismatch

Current problems:
- `fc_capi_step()` computes a scalar reward using hardcoded weights
- `fc_capi_step()` auto-resets on terminal
- header declares `fc_capi_batch_step(FcEnvCtx** ...)`
- implementation actually defines:
  - `fc_capi_batch_create`
  - `fc_capi_batch_destroy`
  - `fc_capi_batch_reset`
  - `fc_capi_batch_step_flat`
  - `fc_capi_batch_get_obs`

This is already an API mismatch and should be fixed during the refactor.

Keep in shared core:
- exported constant getters
- raw context create/destroy/reset/step
- raw obs access
- raw terminal access
- raw batch stepping only if it is generic and header-consistent

Move out of shared core:
- hardcoded scalar reward calculation from reward features
- hardcoded reward weights
- auto-reset policy
- any training-host assumptions about episode lifecycle

Recommended new contract:
- `fc_capi_step()` should perform one raw step only
- terminal state should remain terminal until caller resets
- scalar reward assembly should happen in training-only code
- if batch stepping remains in shared core, it must be truly raw and fully declared in the header

Reason:
- reward shaping is training policy, not simulation
- auto-reset is host behavior, not game mechanics

### 5.5 `training-env/fight_caves.h`

Current mixed ownership:
- trainer wrapper
- reward shaping
- curriculum logic
- analytics aggregation
- direct compilation of shared backend `.c` files inside a header

Current functions inside this file:
- `fc_puffer_write_obs`
- `fc_collect_reward_context`
- `fc_cap_stall_penalty`
- `fc_pick_curriculum_wave`
- `fc_apply_curriculum_wave`
- `fc_puffer_compute_reward`
- `c_reset`
- `c_step`
- `c_render`
- `c_close`

Keep as training-only:
- `FightCaves` env struct
- reward weights / shaping config fields
- analytics globals and aggregation
- curriculum selection/apply helpers
- Puffer observation packing for 5-head mask
- scalar reward computation
- `c_reset`, `c_step`, `c_close`

Do not keep:
- direct `#include "fc_rng.c"` / `fc_pathfinding.c` / etc inside this header

Refactor target:
- either split into:
  - `fight_caves_env.h`
  - `fight_caves_env.c`
  - `fc_reward_shaping.h`
  - `fc_reward_shaping.c`
  - `fc_train_metrics.h`
  - `fc_train_metrics.c`
- or keep a single training wrapper file pair, but stop using a header as a compilation unit

Reason:
- this file is training glue, not shared mechanics
- embedding backend `.c` files prevents a clean library boundary

### 5.6 `training-env/fc_render.h`

Current mixed ownership:
- training-side optional render callback
- direct dependence on demo-env render asset loaders and demo assets

Do not move into shared core.

Options:
1. Keep it training-only as an optional render adapter that links shared core + demo render support.
2. Deprecate/remove it if `eval_viewer.py + fc_viewer --policy-pipe` is the only supported evaluation path.

Important:
- whatever happens, this file should not force demo render dependencies into core simulation

### 5.7 `demo-env/src/viewer.c`

Current responsibilities all remain demo-only:
- episode reset wrapper
- projectile/hitsplat visuals
- terrain/object/model loading orchestration
- human keyboard/mouse input
- action synthesis
- policy-pipe stdin/stdout protocol
- 3D scene drawing
- 2D HUD tabs
- debug UI
- main loop

This file does not belong in shared core.

Optional future cleanup:
- split internally into:
  - `viewer_input.c`
  - `viewer_render_3d.c`
  - `viewer_hud.c`
  - `viewer_policy_pipe.c`
  - `viewer_debug_ui.c`

That split is not required to achieve backend sharing, but it will make the viewer easier to maintain.


## 6. Exact “What Gets Shared” Summary

### 6.1 Shared core simulation

These domains must have exactly one source of truth:
- RNG
- flat state/types
- obs contract
- core lifecycle API
- raw C API
- combat resolution
- NPC behavior
- tick loop
- pathfinding
- prayer logic
- wave spawning
- player init/loadout constants
- observation writing
- reward-feature writing
- action-mask writing

### 6.2 Shared support, not core

These are shared, but they are not core simulation:
- determinism hashing
- action trace / replay utilities
- render snapshot bridge (`FcRenderEntity`, `fc_fill_render_entities`)

### 6.3 Must remain separate

Training-only:
- PufferLib integration
- scalar reward assembly
- config parsing for shaping weights
- analytics aggregation/export
- curriculum
- trainer lifecycle

Demo-only:
- windowing
- rendering
- terrain/object/model/animation loaders
- debug overlay
- manual controls
- policy-pipe process I/O
- headless demo tests

Eval-only:
- checkpoint loading
- model instantiation/inference
- viewer subprocess driver


## 7. Safe-To-Symlink List

These files are safe to symlink only after the mixed-content cleanup above is complete:

- `fc_rng.c`
- `fc_combat.c`
- `fc_combat.h`
- `fc_npc.c`
- `fc_npc.h`
- `fc_pathfinding.c`
- `fc_pathfinding.h`
- `fc_prayer.c`
- `fc_prayer.h`
- `fc_wave.c`
- `fc_wave.h`
- `fc_tick.c`
- `fc_contracts.h`
- `fc_player_init.h`
- `pufferlib_4/config/fight_caves.ini` -> `runescape-rl/config/fight_caves.ini`

These files are safe to symlink only after the specific extractions listed in Section 5 are complete:

- `fc_types.h`
- `fc_api.h`
- `fc_state.c`
- `fc_capi.h`
- `fc_capi.c`

These files must not be symlinked as shared core:

- `training-env/binding.c`
- `training-env/fight_caves.h`
- `training-env/fight_caves.c`
- `training-env/fc_render.h`
- `training-env/build.sh`
- `training-env/CMakeLists.txt`
- `train.sh`
- `sweep_v18_3.sh`
- `demo-env/src/viewer.c`
- `demo-env/src/fc_debug_overlay.h`
- `demo-env/src/fc_anim_loader.h`
- `demo-env/src/fc_npc_models.h`
- `demo-env/src/fc_objects_loader.h`
- `demo-env/src/fc_terrain_loader.h`
- `demo-env/CMakeLists.txt`
- `demo-env/tests/test_headless.c`
- `eval_viewer.py`

Launcher note:
- `train.sh`, `sweep_v18_3.sh`, and other run helpers should not become
  symlinks to each other
- they should instead source one shared shell helper for the repeated
  runtime setup block


## 8. Build-System Refactor Plan

### 8.1 Top-level CMake

Current:
- builds `demo-env`
- builds `training-env`
- both compile their own source copies

Target:
- add `fc-core` target first
- add optional `fc-support` target(s)
- make both `demo-env` and `training-env` link those targets

### 8.2 `training-env/CMakeLists.txt`

Current:
- directly compiles mirrored backend source list into `fc_capi`

Target:
- link `fc-core`
- optionally link `fc-support` if training-side render mode or debug support needs it
- compile only training wrapper sources

### 8.3 `demo-env/CMakeLists.txt`

Current:
- defines `FC_BACKEND_SOURCES`
- compiles mirrored backend directly into `fc_viewer` and `test_headless`

Target:
- stop compiling `fc_*.c` locally
- link `fc-core`
- link `fc-support` for hash/render snapshot/trace as needed
- keep demo-only source compilation local

### 8.4 `training-env/build.sh`

Current:
- compiles `binding.c` which transitively includes backend `.c` files

Target:
- build/link against `fc-core` archive or object library
- build/link against optional `fc-support`
- remove the need for `fight_caves.h` to embed backend source files


## 9. Asset Refactor Plan

### 9.1 Collision asset

Current problem:
- `fightcaves.collision` exists in both `training-env/assets` and `demo-env/assets`
- `fc_state.c` path lookup explicitly probes both directory names

Target:
- one shared copy under `fc-core/assets/fightcaves.collision`

Core code should not know about both wrappers.

### 9.2 Demo render assets

Remain demo-only:
- terrain
- objects
- atlas
- NPC/player/projectile models
- animation packs
- sprites
- tab textures
- prayer icons

These should stay under `demo-env/assets`.


## 10. Migration Order

This order is designed to minimize risk.

### Phase 0: prepare structure only
- create `fc-core`
- create `fc-support`
- add targets to CMake
- identify the canonical config path and canonical collision asset path
- add a shared launcher helper plan before touching shell scripts
- do not delete old files yet

### Phase 1: move pure shared files first
- move the already-pure shared backend files from Section 4.1 into `fc-core`
- point both wrappers at the new include directories / library
- keep mixed files duplicated for now

### Phase 2: split mixed shared files
- extract render snapshot bridge from `fc_types.h`, `fc_api.h`, `fc_state.c`
- extract hash/trace declarations out of `fc_api.h`
- split `fc_capi` into raw shared API vs training behavior

### Phase 3: split training wrapper
- convert `fight_caves.h` into normal training wrapper compilation units
- remove backend `.c` includes from the header
- make `binding.c` depend only on the training wrapper + shared core

### Phase 4: rewire demo
- remove local backend source compilation from `demo-env/CMakeLists.txt`
- link `fc-core` + `fc-support`
- keep viewer/render files local

### Phase 5: collapse duplicate assets
- move collision asset to one shared location
- remove duplicate collision copies
- simplify collision path lookup

### Phase 5.5: collapse duplicate operational files
- make `runescape-rl/config/fight_caves.ini` the single active config source
- replace `pufferlib_4/config/fight_caves.ini` with a symlink or remove the duplicate runtime read path
- extract repeated launcher environment setup into one shared shell helper
- update `train.sh` / sweep scripts to source the helper instead of repeating the block

### Phase 6: delete temporary compatibility layer
- only after both wrappers build and tests pass
- remove duplicated legacy copies
- remove any temporary symlinks if real shared paths are already in use


## 11. Validation Checklist After Refactor

The refactor is not complete unless all of these pass.

### 11.1 Build validation
- `cmake --build build` succeeds
- training build path succeeds
- demo viewer builds
- headless demo test builds
- eval viewer still resolves the contract correctly

### 11.2 Behavioral parity validation
- same seed + same action sequence => same hash sequence
- viewer behavior matches training backend because they now link the same core
- obs sizes and mask sizes match training expectations
- no duplicate backend source lists remain in `demo-env/CMakeLists.txt` or `training-env/CMakeLists.txt`

### 11.3 Structural validation
- no mirrored copies of shared core files remain under both wrappers
- shared core files contain no viewer-specific structs/functions
- shared core files contain no trainer-specific reward/curriculum/logging code
- demo-only files do not contain gameplay logic
- training-only files do not contain gameplay logic


## 12. Things That Must Not Happen During Implementation

- Do not symlink `fight_caves.h` or `viewer.c`.
- Do not move scalar reward computation into shared core.
- Do not leave `FcRenderEntity` in `fc_types.h`.
- Do not leave `fc_fill_render_entities()` in `fc_state.c`.
- Do not leave `fc_state_hash()` declared in `fc_api.h`.
- Do not leave hardcoded trainer reward weights inside shared `fc_capi.c`.
- Do not let shared core path-search `training-env/...` and `demo-env/...` forever.
- Do not keep duplicate collision assets once the shared asset path exists.
- Do not rely on README claims until build targets actually reflect the new structure.


## 13. Recommended Minimal End State

If the goal is the smallest change that still fixes the parity problem, the minimum acceptable end state is:

- one shared `fc-core` tree for gameplay files
- one shared collision asset
- one training wrapper layer
- one demo viewer layer
- one optional shared support layer for hash/render snapshot/trace
- zero mirrored backend files between `training-env` and `demo-env`

That is the smallest architecture that makes parity structural instead of procedural.
