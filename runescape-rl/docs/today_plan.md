# Tomorrow Plan — 2026-04-08

This file replaces the old stale daily plan. It reflects the next actual work to do after `v18`.

## 1. Eval Viewer Playback Speed Controls

Primary objective:
- add runtime playback-speed controls to `demo-env` / `eval_viewer` so policy replays can be watched faster without changing game logic
- support hotkeys for `1x`, `2x`, `4x`, and `10x` speed
- keep simulation behavior identical; only wall-clock playback speed should change

Desired behavior:
- default remains `1x`
- hotkeys switch between `1x`, `2x`, `4x`, and `10x`
- policy actions, backend logic, RNG, and tick ordering must remain identical to normal speed
- only the viewer/update cadence changes so the same tick stream is consumed faster

Terminology:
- use `playback speed`, `simulation speed multiplier`, or `time-scale control`
- avoid framing this as a gameplay/mechanics change; it is a viewer/evaluation throughput feature

Rules:
- do not change backend mechanics to make this work
- do not change action timing semantics
- do not change policy inference inputs/outputs
- keep manual debug viewer behavior usable at `1x`

Required validation:
- manual viewer still behaves the same at `1x`
- eval viewer replay at `1x` matches current behavior exactly
- faster modes only reduce wall-clock time, not alter deterministic policy behavior
- on-screen indicator shows current speed multiplier

Why this matters:
- current policy replay is too slow for late-wave debugging
- faster playback will make checkpoint inspection practical without compromising parity

Checkpoint-selection follow-up in the same workstream:
- once playback speed controls are working, add an automated checkpoint replay/eval path on top of `demo-env` / `eval_viewer`
- goal: select the best continuation checkpoint from a prior training run based on replayed performance, not just training-history intuition

Desired checkpoint-eval workflow:
- choose several candidate checkpoints from a prior run
- replay each checkpoint in `demo-env` / `eval_viewer` at accelerated playback speed
- keep backend mechanics, action timing, RNG, and policy behavior identical to normal replay
- collect the same performance analytics we already use to judge runs:
  - wave reached
  - max wave
  - episode return
  - correct_prayer
  - no_prayer_hits
  - prayer_switches
  - damage_blocked
  - dmg_taken_avg
  - and any other existing replay-safe analytics already exposed by the backend
- rank the candidate checkpoints on those replay metrics
- use the best checkpoint as the warm-start checkpoint for the next continuation run

Implementation goal:
- make checkpoint selection practical enough that it becomes part of the normal training loop
- this should remove the current situation where checkpoint choice is based mostly on training-log heuristics

Rules:
- do not create a separate “eval-only” backend with different mechanics
- do not change tick semantics to speed up replay
- do not add fake or approximate metrics just for checkpoint ranking
- reuse the existing backend analytics and replay path as much as possible

Required validation:
- replay metrics from automated checkpoint eval should match what we would see from the same checkpoint in normal-speed replay
- accelerated replay must preserve deterministic behavior for deterministic policy runs
- the selected “best” checkpoint should be traceable to logged replay metrics, not manual judgment alone

## 2. Shared Backend Refactor

Primary objective:
- remove duplicated Fight Caves backend files between `training-env` and `demo-env`
- make parity structural instead of procedural
- do this as a file/layout refactor first, not a behavior change
- also remove obvious copy/sync operational drift points that are already
  causing local maintenance problems

Execution document:
- [component_symlink.md](/home/joe/projects/runescape-rl/codex3/runescape-rl/docs/component_symlink.md)

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

## 3. RL Tuning Follow-Up After Refactor

Baseline to tune from:
- `v18` / W&B run `lxttb7uo`
- treat `v18` as the current clean scratch baseline
- best checkpoint window appears to be around `~1.21B-1.29B`

Primary recommendation:
- run a late-wave fine-tune from the best `v18` checkpoint rather than redesigning the obs/reward stack again

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
- keep the rest of the `v18` reward structure unchanged

Reason:
- `v18` already proved the current backend/obs can learn
- the next problem is breaking past the late-wave plateau, not rebuilding early competence

Secondary control if needed:
- rerun the exact `v18` recipe longer, around `2.5B` total steps, with no curriculum changes

Only-if-needed follow-up:
- if late Jad switching still looks like the real bottleneck after fine-tuning, try a very small Jad-specific reward adjustment rather than another broad reward rewrite

## 4. Guardrails

Do not do these tomorrow unless a new finding forces it:
- do not prune or expand observations again
- do not reintroduce Jad telegraph obs
- do not bring back the heavy `v17` resource penalties
- do not mix a large PPO change, large reward change, and curriculum change in one experiment
- do not treat the final checkpoint as automatically best; evaluate the best `v18` plateau checkpoint first
