# Tomorrow Plan — 2026-04-08

This file replaces the old stale daily plan. It reflects the next actual work to do after `v19.3`.

## 1. Shared Backend Refactor

Primary objective:
- remove duplicated Fight Caves backend files between `training-env` and `demo-env`
- make parity structural instead of procedural
- do this as a file/layout refactor first, not a behavior change
- also remove obvious copy/sync operational drift points that are already
  causing local maintenance problems

Execution document:
- [component_symlink.md](/home/joe/projects/runescape-rl/codex3/runescape-rl/docs/component_symlink.md)

Running cleanup candidates for later follow-up:
- `training-env/fc_render.h`
  - legacy in-wrapper Raylib render path; appears unused in the current headless training and external eval-viewer workflow, so it is a likely remove-or-relocate candidate

Rules:
- do not change gameplay logic intentionally
- do not change the obs contract intentionally
- do not move trainer-only logic into shared core
- do not move viewer-only logic into shared core
- do not leave copy-synced config files in place once a safe single source
  of truth exists
- do not leave duplicated launch/bootstrap shell logic in multiple scripts if
  it can be sourced from one shared helper
- validate after each phase, not only at the end

Explicit non-code drift items to fold into the same work:
- unify the active training config path:
  - `runescape-rl/config/fight_caves.ini`
  - `pufferlib_4/config/fight_caves.ini`
  - goal: one source of truth, not copy/sync before launch
- after config unification, remove the `cp ... fight_caves.ini` sync step from:
  - `train.sh`
  - `sweep_v18_3.sh`
  - any other train/sweep helper that still copies config into `pufferlib_4`
- centralize duplicated shell runtime setup shared by train/sweep launchers:
  - `.venv` activation
  - `PUFFER_DIR`
  - `FC_COLLISION_PATH`
  - W&B dirs
  - cuDNN/lib path exports
  - backend existence/build check
  - goal: one shared shell helper, not repeated blocks across multiple launch scripts
- keep the collision asset single-sourced:
  - `training-env/assets/fightcaves.collision` today
  - or `fc-core/assets/fightcaves.collision` after refactor
  - do not create parallel copies
- keep detailed ownership/file mapping in `component_symlink.md`
  and keep `today_plan.md` as the short execution pointer only

Required validation:
- `cmake --build build`
- `training-env` build path still works
- `demo-env` viewer still works
- `test_headless` still works
- `eval_viewer.py` still resolves the contract correctly
- same seed + same action sequence still produces matching behavior/hash

## 2. Eval Viewer Playback Speed Controls

Primary objective:
- make policy replay faster to inspect without changing the actual simulation
- keep `1x` identical to current replay behavior
- add simple preset playback speeds: `1x`, `2x`, `4x`, `10x`

Current repo state:
- `demo-env/src/viewer.c` already drives replay speed through `v.tps`
- `eval_viewer.py` just launches `fc_viewer --policy-pipe` and feeds actions
- so this should be a small viewer-side change, not a new replay system

Implementation plan:
- treat the current default policy replay rate as `1x`
- map fixed replay presets onto the existing viewer tick-rate control
- add hotkeys for `1x`, `2x`, `4x`, and `10x` in `--policy-pipe` replay mode
- keep the existing manual viewer controls usable; do not break prayer keys for human play
- show the active playback multiplier on screen
- optionally add an `eval_viewer.py` arg to set the initial replay speed when launching

Rules:
- do not change backend mechanics
- do not change action timing semantics
- do not change policy inference inputs/outputs
- only change wall-clock replay speed
- keep manual debug viewer behavior unchanged at normal speed

Checkpoint-selection follow-up:
- after playback presets work, add a small replay-metrics export path
- current replay path does not yet emit a clean terminal summary for checkpoint ranking
- export existing backend-safe metrics at episode end, then use that output to compare candidate checkpoints

Desired checkpoint-eval workflow:
- choose several candidate checkpoints from a prior run
- replay each checkpoint at accelerated playback speed
- collect real replay metrics already backed by the existing environment state
- rank checkpoints from replay results instead of training-log intuition

Required validation:
- manual viewer still behaves the same at normal speed
- eval replay at `1x` matches current behavior exactly
- faster presets only reduce wall-clock time
- deterministic replay stays deterministic at every speed
- on-screen speed indicator is visible and correct

## 3. Training-Env Performance Refactor

Primary objective:
- refactor `training-env` specifically for performance improvements

## 4. RL Tuning Follow-Up After Refactor

Baseline to tune from:
- `v19` through `v19.3`
- treat the current `v19` line as the active continuation baseline
- select the best continuation checkpoint from `v19` through `v19.3` before the next run

Primary recommendation:
- run a late-wave fine-tune from the best `v19` / `v19.3` checkpoint rather than redesigning the obs/reward stack again

Recommended first config to try:
- `total_timesteps = 1_000_000_000`
- `learning_rate = 0.0003`
- `clip_coef = 0.15`
- `ent_coef = 0.01`
- `checkpoint_interval = 50`
- `curriculum_wave = 30`
- `curriculum_pct = 0.05`
- `curriculum_wave_2 = 31`
- `curriculum_pct_2 = 0.02`
- `curriculum_wave_3 = 0`
- `curriculum_pct_3 = 0.0`
- keep the rest of the current `v19.3` reward structure unchanged

Reason:
- the current `v19` / `v19.3` line already proved the current backend/obs can learn
- the next problem is breaking past the late-wave plateau, not rebuilding early competence

Secondary control if needed:
- rerun the exact `v19.3` recipe longer, around `2.5B` total steps, with no curriculum changes

Only-if-needed follow-up:
- if late Jad switching still looks like the real bottleneck after fine-tuning, try a very small Jad-specific reward adjustment rather than another broad reward rewrite

## 5. Guardrails

Do not do these tomorrow unless a new finding forces it:
- do not prune or expand observations again
- do not reintroduce Jad telegraph obs
- do not bring back the heavy `v17` resource penalties
- do not mix a large PPO change, large reward change, and curriculum change in one experiment
- do not treat the final checkpoint as automatically best; evaluate the best `v19` / `v19.3` plateau checkpoint first
