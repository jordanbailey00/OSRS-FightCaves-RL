# Shared Backend Source Of Truth

Status:
- implemented as of 2026-04-08
- no symlink-based sharing is used
- Fight Caves backend parity is now structural: both `demo-env` and `training-env` consume the same `fc-core` files

Objective:
- keep one canonical copy of the Fight Caves backend
- make shared vs non-shared ownership explicit
- avoid behavior changes while consolidating code layout

Non-goals:
- no gameplay/mechanics changes
- no observation contract changes
- no reward/curriculum changes
- no viewer UX changes


## 1. Current Layout

Shared backend root:
- `fc-core/`

Shared backend files:
- `fc-core/include/fc_api.h`
- `fc-core/include/fc_capi.h`
- `fc-core/include/fc_combat.h`
- `fc-core/include/fc_contracts.h`
- `fc-core/include/fc_npc.h`
- `fc-core/include/fc_pathfinding.h`
- `fc-core/include/fc_player_init.h`
- `fc-core/include/fc_prayer.h`
- `fc-core/include/fc_types.h`
- `fc-core/include/fc_wave.h`
- `fc-core/src/fc_capi.c`
- `fc-core/src/fc_combat.c`
- `fc-core/src/fc_npc.c`
- `fc-core/src/fc_pathfinding.c`
- `fc-core/src/fc_prayer.c`
- `fc-core/src/fc_rng.c`
- `fc-core/src/fc_state.c`
- `fc-core/src/fc_tick.c`
- `fc-core/src/fc_wave.c`
- `fc-core/assets/fightcaves.collision`

Training-only files:
- `training-env/binding.c`
- `training-env/fight_caves.h`
- `training-env/fight_caves.c`
- `training-env/fc_render.h`
- `training-env/build.sh`
- `training-env/CMakeLists.txt`

Demo-only files:
- `demo-env/src/viewer.c`
- `demo-env/src/fc_anim_loader.h`
- `demo-env/src/fc_debug.c`
- `demo-env/src/fc_debug.h`
- `demo-env/src/fc_debug_overlay.h`
- `demo-env/src/fc_hash.c`
- `demo-env/src/fc_npc_models.h`
- `demo-env/src/fc_objects_loader.h`
- `demo-env/src/fc_terrain_loader.h`
- `demo-env/tests/test_headless.c`
- `demo-env/CMakeLists.txt`
- demo-only assets under `demo-env/assets/` other than the old duplicate collision map

Tooling / launcher files that depend on the shared backend path:
- `eval_viewer.py`
- `train.sh`
- `sweep_v18_2.sh`
- `sweep_v18_3.sh`
- top-level `CMakeLists.txt`


## 2. Consumer Wiring

Top-level CMake:
- `CMakeLists.txt` adds `fc-core` first, then `demo-env`, then `training-env`

Demo build path:
- `demo-env/CMakeLists.txt` builds `fc_viewer` from local viewer/debug sources
- `fc_viewer` links against the shared `fc_core` static library
- `test_headless` also links against `fc_core`

Training CMake path:
- `training-env/CMakeLists.txt` builds `fc_capi` from `fc-core/src/fc_capi.c`
- it includes `fc-core/include`
- it links against `fc_core`

Training Puffer build path:
- `training-env/build.sh` is the canonical Fight Caves backend build script
- it now includes `fc-core/include` and `fc-core/src`
- `binding.c` still includes `fight_caves.h`
- `fight_caves.h` still directly includes backend `.c` files, but those files now resolve from `fc-core`, not from duplicated local copies

Launcher/build integration:
- `train.sh` rebuilds through `training-env/build.sh`
- `sweep_v18_2.sh` rebuilds through `training-env/build.sh`
- `sweep_v18_3.sh` rebuilds through `training-env/build.sh`
- those scripts also verify that the compiled extension exposes the trainer API before deciding the backend is reusable

Eval/tooling integration:
- `eval_viewer.py` reads contracts from `fc-core/include/fc_contracts.h`
- `eval_viewer.py` prefers the local `pufferlib_4` checkout on `sys.path`

Shared asset path:
- `FC_COLLISION_PATH` now points at `fc-core/assets/fightcaves.collision`
- viewer/runtime collision probing includes the shared `fc-core/assets/...` path


## 3. What Was Removed

Removed duplicate backend trees:
- `demo-env/src/fc_*.c/h`
- `training-env/src/fc_*.c/h`
- `training-env/fc_*.c/h` compatibility duplicates

Removed duplicate shared asset:
- `demo-env/assets/fightcaves.collision`
- `training-env/assets/fightcaves.collision`

Important result:
- there is now one committed backend source of truth
- there is now one committed shared collision asset


## 4. Remaining Intentional Caveats

These are shared today by design, even though they are not "pure mechanics only":

- `fc-core/include/fc_types.h`
  - still contains render-bridge structs such as `FcRenderEntity`

- `fc-core/include/fc_api.h`
  - still exposes render-support APIs such as `fc_fill_render_entities()`
  - still conditionally exposes `fc_state_hash()`

- `fc-core/src/fc_state.c`
  - still implements `fc_fill_render_entities()`
  - still contains collision-path probing logic

- `fc-core/include/fc_capi.h`
- `fc-core/src/fc_capi.c`
  - still mix raw shared state stepping with host-facing behavior such as scalar reward output and auto-reset semantics

These are acceptable for the current refactor because:
- both demo and training still need the same behavior
- the user explicitly asked for consolidation, not behavior redesign
- extracting a cleaner support layer would be a follow-up refactor, not part of this move


## 5. Validation Status

Validated after the refactor:
- `cmake --build build` succeeds
- `build/demo-env/test_headless` passes
- `build/demo-env/fc_viewer` launches
- `eval_viewer.py --ckpt latest --deterministic` loads a real policy and launches the viewer
- `train.sh` can run an isolated smoke-training job and emit a checkpoint
- the fresh smoke checkpoint replays in `eval_viewer.py`

Smoke-test checkpoint used for end-to-end validation:
- `/tmp/fc_shared_refactor_smoke_ckpts/fight_caves/1775676164356/0000000000065536.bin`


## 6. Rules Going Forward

If a file changes Fight Caves gameplay state, contracts, or shared runtime assets:
- change it in `fc-core`

If a file changes Puffer integration, reward shaping, training analytics, or training build logic:
- change it in `training-env`

If a file changes viewer UI, debug overlays, render helpers, viewer tests, or viewer-only assets:
- change it in `demo-env`

Do not reintroduce mirrored backend copies under `demo-env/src` or `training-env/src`.
