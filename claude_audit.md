# codex3 Audit

Date: 2026-04-06

Audit root: `/home/joe/projects/runescape-rl/codex3`

Project root: `/home/joe/projects/runescape-rl/codex3/runescape-rl`

Scope: deep audit of the copied Fight Caves RL repo as it now exists in `codex3`, with emphasis on architecture, current live contracts, doc accuracy, stale files, path coupling, self-containment risks, and safe next steps.

This document treats `codex3` as the working baseline. It does not assume git `HEAD` is the true state of the project, because the copied checkout is not clean.

## Executive Summary

This repo is a Fight Caves reinforcement-learning project built around a deterministic C simulator, a Raylib debug/eval viewer, and a PufferLib 4 training wrapper. The core design is strong:

- single-tick deterministic simulation in C
- explicit observation/action/reward contracts
- real viewer over the same simulation
- reward history documented unusually well
- local assets already included for the encounter itself

The current `codex3` checkout is also clearly a copied working tree, not a clean source checkout. Important consequences:

- the repo is not clean relative to git `HEAD`
- the copied `build/` directory is stale and still points at the old `/claude` source path
- some files required by the current working setup are untracked local files
- several scripts still mutate or depend on paths outside `codex3`
- the "single mechanics owner" idea has drifted into two slightly different backend copies

Short version:

- The live code is more current than the docs.
- `docs/rl_config.md` is the best doc, but still mixes current and old assumptions.
- `DESIGN.md` is good for principles and history, not for exact current contract numbers.
- `docs/history.md` is archival only.
- `codex3` can build some local artifacts, but the actual PufferLib training flow is still not self-contained.
- The highest-risk technical issue is not raw correctness; it is maintenance drift and hidden coupling.

## What This Repo Actually Is

At the code level, this is really four surfaces, not three:

1. `demo-env/`
   Viewer shell plus one copy of the Fight Caves simulation.

2. `training-env/`
   Another copy of the same simulation, plus the PufferLib adapter and a plain C API.

3. `config/`
   Current training config in `fight_caves.ini`.

4. `docs/`
   A mix of current design notes, current-ish reward/config notes, and historical planning/archeology.

The architecture intends to have one mechanics owner, but today it is more accurate to say:

- there are two backend copies
- they are mostly aligned
- they are not perfectly aligned anymore

That distinction matters before any refactor.

## Current Working Tree Reality

This copied checkout is large and heavily local-state driven:

- total files under `runescape-rl/`: `32141`
- git-tracked files: `100`
- local `.venv`: `7.1G`
- local `build/`: `3.9M`
- committed `demo-env/assets/`: `45M`
- committed `demo-env/raylib/`: `5.3M`

The git tree is not clean. At audit time, `git status --short` showed:

- many modified tracked files in `demo-env/`, `training-env/`, `DESIGN.md`, `docs/rl_config.md`, `config/fight_caves.ini`, and `eval_viewer.py`
- untracked helper scripts: `train.sh`, `analyze_run.sh`
- untracked `claude_audit.md`
- untracked `fc_player_init.h`
- untracked top-level `training-env/*` symlink mirrors

Implication:

- the working copy is likely the real baseline, not git `HEAD`
- if future work starts from git `HEAD` instead of this worktree, behavior may regress

## Trust Ranking

For understanding the repo, the safest trust order is:

1. Live C/Python code in `demo-env/src`, `training-env/src`, `training-env/fight_caves.h`, `binding.c`, and `eval_viewer.py`
2. `config/fight_caves.ini`
3. `docs/rl_config.md`
4. `DESIGN.md`
5. `README.md`
6. `docs/history.md`, `docs/plan.md`, `docs/today_plan.md`
7. copied `build/` artifacts and `.claude/settings.local.json`

Why:

- code is current behavior
- config is current training intent
- `rl_config.md` mostly matches live reward/obs behavior
- `DESIGN.md` still contains older contract numbers, loadout assumptions, and external-path guidance
- `history.md` is useful background but conflicts with current architecture in multiple places
- copied build files still hard-reference the old `/claude` path

## Live Architecture

### Backend

The backend is a deterministic Fight Caves simulation in C:

- state lifecycle: `fc_init`, `fc_reset`, `fc_step`
- arena collision loading and player init in `fc_state.c`
- main tick order in `fc_tick.c`
- combat formulas and pending-hit resolution in `fc_combat.c`
- NPC definitions and AI in `fc_npc.c`
- pathfinding and LOS in `fc_pathfinding.c`
- prayer drain and toggles in `fc_prayer.c`
- wave tables and rotations in `fc_wave.c`
- RNG in `fc_rng.c`

### Viewer

`demo-env/` builds:

- `fc_viewer`
- `test_headless`

The viewer is more than a toy shell. It supports:

- human control
- policy-pipe mode for model replay
- runtime loadout switching via dropdown
- wave jump dropdown
- debug overlay families
- start-wave flag
- screenshots

### Training

There are two separate training-facing surfaces:

1. `training-env/fight_caves.h` + `binding.c`
   The actual PufferLib wrapper path.

2. `training-env/src/fc_capi.c`
   A plain C shared library API for ctypes/cffi.

These are not equivalent.

That is important:

- the PufferLib wrapper exposes one observation contract and one reward function
- the plain C API exposes a different observation buffer shape and a simpler older reward function

### Intentional vs actual ownership

The docs repeatedly say `demo-env/src` is authoritative and `training-env/src` is a copy. That is directionally true, but not fully true today, because there is already behavior drift between them.

## Current Live Contracts

### Core dimensions

The current live macros imply:

- player obs: `22`
- per-NPC stride: `15`
- visible NPC slots: `8`
- NPC obs total: `120`
- meta obs: `10`
- policy obs: `152`
- reward features: `18`
- full pre-mask obs: `170`
- full action mask: `166`
- full C buffer: `336`

So the true full engine-side contract is:

- `FC_POLICY_OBS_SIZE = 152`
- `FC_REWARD_FEATURES = 18`
- `FC_TOTAL_OBS = 170`
- `FC_ACTION_MASK_SIZE = 166`
- `FC_OBS_SIZE = 336`

### Five-head RL vs seven-head engine

The engine defines seven action heads:

- head 0: move
- head 1: attack
- head 2: prayer
- head 3: eat
- head 4: drink
- head 5: move target x
- head 6: move target y

The RL path only uses the first five heads.

So there are really two action contracts:

- engine contract: 7 heads
- PufferLib policy contract: 5 heads

### The most important observation nuance

`fc_write_obs()` writes:

- 152 policy floats
- 18 reward-feature floats

That produces `170` floats.

`fc_write_mask()` writes:

- 166 mask floats

The plain C API (`fc_capi.c`) returns the full `336`-float buffer:

- `170` obs/reward floats
- `166` mask floats

The PufferLib wrapper does not.

In `training-env/fight_caves.h`, `fc_puffer_write_obs()`:

- writes the `170`-float obs/reward block
- then copies only the first `36` mask floats into `obs + FC_POLICY_OBS_SIZE`

That means the RL-visible buffer is:

- `152` policy obs
- `36` mask floats for heads 0-4
- total `188`

The 18 reward features are overwritten in the PufferLib observation buffer. They still exist conceptually and are recomputed for reward/logging, but they are not part of the policy input.

This is the single most important contract detail in the repo.

Any future change to observation plumbing must preserve or intentionally migrate all three surfaces:

- full C API buffer: `336`
- engine obs+reward block: `170`
- PufferLib policy buffer: `188`

## Current Loadout, Config, and Training Intent

### Active loadout

The live active compile-time loadout is not the older mid-level loadout.

`fc_player_init.h` currently sets:

- `FC_ACTIVE_LOADOUT = 1`

That means training is on Loadout B:

- Masori (f) + Twisted Bow
- 99 HP
- 99 Prayer
- 99 Ranged
- 99 Defence

This is a major source of doc drift, because some docs still describe the older 70/43 Rune Crossbow setup in feature descriptions.

### Current config

`config/fight_caves.ini` is current-ish and matches the top of `docs/rl_config.md`:

- total timesteps: `20_000_000_000`
- learning rate: `0.001`
- `anneal_lr = 1`
- `total_agents = 4096`
- `horizon = 256`
- `hidden_size = 256`
- `num_layers = 2`

The file itself still has a stale comment:

- `# Reward shaping weights (v14)`

but the actual train section reflects the later `v16.2` doc state.

## Reward and Logging Audit

The reward path is concentrated in `training-env/fight_caves.h`.

Important split:

- reward features are emitted by simulation state
- scalar reward is assembled in the wrapper
- config only controls part of that scalar reward
- several important penalties/rewards are hardcoded

### Live reward status by category

Configurable and actively used:

- `w_damage_dealt`
- `w_damage_taken`
- `w_npc_kill`
- `w_wave_clear`
- `w_jad_damage`
- `w_jad_kill`
- `w_player_death`
- `w_cave_complete`
- `w_correct_danger_prayer`
- `w_invalid_action`
- `w_movement`
- `w_idle`
- `w_tick_penalty`

Parsed from config but not used by the current scalar reward:

- `w_food_used`
- `w_food_used_well`
- `w_prayer_pot_used`
- `w_correct_jad_prayer`
- `w_wrong_jad_prayer`
- `w_wrong_danger_prayer`

Features/signals emitted by state but ignored by the scalar reward:

- `FC_RWD_CORRECT_JAD_PRAY`
- `FC_RWD_WRONG_JAD_PRAY`
- `FC_RWD_WRONG_DANGER_PRAY`

Signal wired into reward but apparently never set:

- `invalid_action_this_tick`

Hardcoded shaping that bypasses config weights:

- food waste penalty
- prayer-pot waste penalty
- wrong prayer penalty (`-1.0`)
- NPC-specific correct-prayer bonus (`+2.0`)
- prayer flick reward (`+0.5`)
- melee-range penalty (`-0.5` per nearby NPC)
- wasted attack penalty (`-0.3`)
- wave stall penalty
- combat idle penalty (`-0.5`)
- unnecessary prayer penalty (`-0.2`)
- kiting reward (`+1.0`)

### Logging drift

There is naming drift around prayer analytics:

- simulation counters use `ep_correct_blocks`
- wrapper logs it under `correct_prayer`
- `analyze_run.sh` looks for `env/correct_blocks`
- older docs also use `correct_blocks`

This is small but important, because it will silently break dashboards and analysis scripts if not normalized.

## Documentation Audit

## `README.md`

This file is stale enough that it should not be used for setup or architecture decisions.

Problems:

- it says there is an `RL/` directory; there is not
- it says docs are only `plan.md` and `pr_plan.md`
- it tells you to `cd RL && uv sync`
- it describes `training-env/` as the shared backend owner in a way that no longer matches the repo's actual duplicated layout

Verdict:

- good only as a one-line project label
- not safe as an onboarding guide

## `DESIGN.md`

This is valuable, but only if read with caution.

What is still useful:

- architectural principles
- component boundaries
- reference-oracle history
- why the project pivoted to C
- asset/export background

What is stale:

- contract numbers
- observation sizes in comments and examples
- older fixed loadout assumptions
- build/output descriptions
- external path guidance
- training history "current state" section

Examples of stale specifics:

- it still describes older observation sizes like `135`, `151`, `162`, `166`, `178`
- it still describes the older Def 70 / Ranged 70 / Prayer 43 / Rune Crossbow loadout in current-architecture sections
- it still describes training history as if the repo is around `v6.1`, while `rl_config.md` continues through `v16.2`
- it contains many absolute references to `runescape-rl-reference` and `pufferlib_4`

Verdict:

- strong design-history doc
- not safe as a source for exact live dimensions or current runtime paths

## `docs/rl_config.md`

This is the most accurate doc in the repo and the best written source for live training behavior.

What it gets right:

- `152` policy obs
- `36` five-head mask
- `188` policy-visible RL buffer
- reward-shaping philosophy
- current high-level reward structure
- late training history through `v16.2`

Where it still drifts:

- early feature descriptions still assume the old 70 HP / 43 Prayer / RCB loadout
- history sections mix old and new metric names
- some sections still mention the historical `pufferlib_4/assets/` collision-copy workaround
- there is at least one duplicate/awkward history heading (`v10`)

Verdict:

- best current doc
- still needs a pass to fully align with Loadout B and the current wrapper behavior

## `docs/history.md`

This is useful only as archaeology.

What it is good for:

- project chronology
- Kotlin-to-C pivot context
- old reference sources
- asset/export lessons

What is outdated:

- observation size
- directory names
- old `RL/` stack references
- tick-order statements that do not match the current code/docs framing

Verdict:

- reference only
- not architecture truth

## Path Coupling and Self-Containment Problems

The user's stated goal is important:

- using `codex3` should not require modifying or updating anything outside `codex3`

The repo is not there yet.

### Direct old-path references inside `codex3`

Current direct references to the old `claude` location still exist in:

- `runescape-rl/analyze_run.sh`
- `runescape-rl/train.sh`
- `runescape-rl/docs/plan.md`
- copied `claude_audit.md` before this rewrite

### External repo dependencies

Current external path dependencies still exist in:

- `runescape-rl/train.sh`
- `runescape-rl/training-env/build.sh`
- `runescape-rl/eval_viewer.py`
- `runescape-rl/DESIGN.md`
- `runescape-rl/docs/history.md`
- `runescape-rl/docs/plan.md`
- `runescape-rl/docs/rl_config.md`
- `.claude/settings.local.json`

### The biggest self-containment blockers

#### `train.sh`

This script is currently not safe for isolated `codex3` use.

It does all of the following:

- hardcodes `PUFFER_DIR="/home/joe/projects/runescape-rl-reference/pufferlib_4"`
- copies config out of the repo into that external tree
- changes directory into that external tree
- hardcodes `LD_LIBRARY_PATH` to the old `/claude` repo's `.venv`

This is an external-mutating launcher, not a self-contained repo launcher.

#### `training-env/build.sh`

This is the actual biggest structural blocker.

It currently:

- defaults `PUFFERLIB_DIR` to `/home/joe/projects/runescape-rl-reference/pufferlib_4`
- requires external `vecenv.h`
- requires external `src/bindings_cpu.cpp`
- requires external `src/bindings.cu`
- changes directory into the external PufferLib tree
- writes `_C.so` into that external tree
- downloads Raylib on demand

So even though the Fight Caves code lives in `codex3`, the current training build system still assumes an external PufferLib source checkout as the real build root.

#### `eval_viewer.py`

This is closer to local, but still not fully local by default.

It:

- defaults latest-checkpoint search to `/home/joe/projects/runescape-rl-reference/pufferlib_4`
- assumes PufferLib is importable from the current Python environment
- has a stale docstring using `--checkpoint` while argparse actually defines `--ckpt`

#### `analyze_run.sh`

This is an old-path analysis helper, not a reliable repo-local script.

It:

- sources the old `/claude` venv
- hardcodes a W&B entity/project path
- expects `env/correct_blocks`
- prints `correct_blocks` twice

### Collision assets: partly fixed, still path fragile

The good news:

- `demo-env/assets/fightcaves.collision` is tracked
- `training-env/assets/fightcaves.collision` is tracked

The bad news:

- `fc_state.c` still resolves collision by relative paths and optional `FC_COLLISION_PATH`
- the historical external PufferLib flow changes cwd into `pufferlib_4/`
- once cwd moves outside the repo, the repo-relative search list no longer reliably finds the in-repo asset

So the asset is in the repo, but the current training workflow still escapes the repo before loading it.

## Viewer/Trainer Drift

This is the most important live code drift I found.

### Action mask mismatch

`demo-env/src/fc_state.c` and `training-env/src/fc_state.c` no longer mask consumables the same way.

Viewer side:

- food masked above `70%` HP
- prayer pot masked above `20%` prayer

Training side:

- food only masked at full HP
- prayer pot only masked at full prayer

Implication:

- training allows more aggressive eating/drinking
- viewer policy replay uses stricter consumable availability
- a checkpoint replayed in the viewer is not being evaluated under exactly the same mask semantics it trained under

That is a real parity bug, not just stale commentary.

### Training-side analytics-only additions

`training-env/src/fc_tick.c` contains episode analytics not present in `demo-env/src/fc_tick.c`, such as:

- `ep_food_overhealed`
- `ep_pots_overrestored`

These are not a mechanics problem by themselves, but they confirm the two copies are drifting in practice.

### "Single owner" is currently aspirational

The project still wants one shared gameplay owner, but in reality it now has:

- one backend copy for the viewer
- one backend copy for training
- at least one known behavioral divergence

Before any cleanup refactor, parity should be restored or the split should be made explicit.

## Plain C API vs PufferLib Wrapper Drift

This is easy to miss and important.

`training-env/src/fc_capi.c` is not just a transport wrapper. It also computes its own scalar reward from the reward-feature buffer, using a much simpler and older weighting scheme:

- damage dealt `* 1.0`
- damage taken `* -1.0`
- npc kill `* 5.0`
- wave clear `* 10.0`
- player death `* -20.0`
- cave complete `* 100.0`
- tick penalty `* -0.01`

That is not the same as the current PufferLib reward in `fight_caves.h`.

Implication:

- `libfc_capi.so` is not a drop-in behavioral match for the PufferLib training wrapper
- if anyone uses the plain C API for evaluation or experimentation, they may believe they are using "the current reward" when they are not

This should either be unified later or documented very explicitly.

## Build Surface Audit

There are multiple build surfaces, and they are not equally trustworthy.

### Local CMake build

Tracked CMake files define:

- top-level `add_subdirectory(demo-env)`
- top-level `add_subdirectory(training-env)`
- `demo-env` builds `fc_viewer` and `test_headless`
- `training-env` builds `libfc_capi.so`

This is the cleanest local build surface.

### Copied build directory is stale

The copied `runescape-rl/build/` directory should not be trusted.

Evidence:

- generated `CTestTestfile.cmake` files still name source/build directories under `/home/joe/projects/runescape-rl/claude/runescape-rl`
- copied build artifacts include historical binaries not explained by current tracked CMake:
  - `build/training-env/libfc_core.a`
  - `build/training-env/test_pr1`
  - `build/training-env/test_pr2`
  - `build/training-env/test_pr5`
  - `build/training-env/test_pr6`

Implication:

- any future build work in `codex3` should start from a clean reconfigure
- copied `build/` is reference debris, not a trustworthy current output tree

### Raylib tracking problem

`demo-env/CMakeLists.txt` links against:

- `demo-env/raylib/lib/libraylib.a`

But git tracks only:

- `libraylib.so.5.5.0`
- `libraylib.so.550`

The static library currently exists locally in `codex3`, but it is untracked.

Implication:

- the current viewer build works in this copied checkout
- a clean source-only clone may fail because the exact static library CMake expects is not part of tracked source

### Test registration gap

`test_headless` is built, but not registered with CTest.

Evidence:

- `enable_testing()` exists
- no `add_test(...)` exists in tracked CMake
- generated `CTestTestfile.cmake` files are effectively empty

Implication:

- there is useful test code
- there is not yet a first-class repeatable test harness in the build system

## What the Existing Test Actually Covers

`demo-env/tests/test_headless.c` is good and worth keeping.

It checks:

- observation values normalized to `[0,1]`
- some reward-feature firing
- action-mask behavior
- determinism via `fc_state_hash()`
- multi-episode stability

This is not exhaustive, but it is a strong start. The issue is discoverability and automation, not the absence of useful test code.

## Stale or Odd Local Files

### `fc_player_init.h` is currently untracked

Both of these are untracked:

- `runescape-rl/demo-env/src/fc_player_init.h`
- `runescape-rl/training-env/src/fc_player_init.h`

But the code includes and depends on them.

This is a major portability hazard:

- the current working checkout needs them
- git does not guarantee they exist

### Top-level `training-env/*` symlink mirrors are untracked

The copied checkout has untracked symlink mirrors such as:

- `training-env/fc_api.h -> src/fc_api.h`
- `training-env/fc_rng.c -> src/fc_rng.c`
- etc.

These look like convenience include/build mirrors for the external wrapper flow.

Important nuance:

- CMake uses `src/*` directly
- `build.sh` includes both root and `src`
- these symlinks may not be strictly required, but they are present in the current working setup and should not be deleted blindly

### `train.sh` and `analyze_run.sh` are untracked

That means the current convenience scripts are part of the copied working state, not committed source truth.

### `.claude/settings.local.json` is copied local tooling state

This file is ignored source-control-wise but exists in the copied tree. It contains a very large amount of stale historical local automation state, including many old `claude` path references and even references to older architectures that no longer exist in this repo.

This file should not be treated as documentation or repo truth.

## Specific Weird Observations

These are the kinds of details that tend to bite later.

### `fc_contracts.h` comments are badly stale

The macros compute current values, but many inline comments still describe older dimensions:

- player features comment says `20`, actual is `22`
- NPC total comment says `104`, actual is `120`
- policy obs comment says `135`, actual is `152`
- total obs comment says `142`, actual is `170`

The code is right. The comments are wrong.

### `training-env/fight_caves.h` comments are also stale

It still says things like:

- `126 policy features + 36 action mask = 162`
- `Policy observations: 126 floats`

That no longer matches live code.

### Viewer controls comment is stale

At the top of `viewer.c`, the comment says:

- `D` toggles debug overlay

In current code:

- `O` manages overlay modes
- `` ` `` / grave toggles `show_debug`
- the `KEY_D` branch is effectively empty

So the controls comment is no longer trustworthy.

### `eval_viewer.py` usage comment is stale

Docstring usage says:

- `--checkpoint`

Real argparse flag is:

- `--ckpt`

### `fight_caves.ini` version comment is stale

The config file still labels reward weights as `v14`, while the live training doc continues through `v16.2`.

### `fc_state.c` silently falls back to an open arena

If collision loading fails, the code builds:

- an open arena with border walls

There is no loud failure.

That fallback is convenient for development, but dangerous for RL correctness because it can silently change encounter geometry and safespot behavior.

### The repo really contains duplicate `fc_capi` code

There is `fc_capi.c/h` in both:

- `training-env/src`
- `demo-env/src`

But the viewer build does not use the demo copy.

This looks like mirror duplication rather than a consciously supported dual surface.

## Doc-to-Code Mismatch Summary

If I had to summarize the docs in one sentence:

- `rl_config.md` understands the current reward system best
- `DESIGN.md` understands the original design intent best
- the code understands the actual repo best

Specific doc mismatches to remember:

- current loadout is end-game, not the older mid-level one described in many prose sections
- current policy obs is `152`, not the older values still shown in comments/docs
- PufferLib-visible obs is `188`, not the full engine buffer
- the actual build/training story is more path-coupled than the architecture docs suggest
- viewer/trainer backend parity is no longer exact

## What Must Not Change Accidentally

If the goal is to improve this repo without breaking the currently working behavior, these are the highest-value invariants to preserve until intentionally migrated:

- observation feature ordering
- visible NPC slot ordering: distance first, `spawn_index` tiebreak
- action head ordering and dimensions
- current five-head RL mask layout (`36` floats)
- current policy buffer size (`188`) for PufferLib/eval_viewer compatibility
- current full C API buffer size (`336`) if the plain C API remains supported
- hit-resolution semantics, especially prayer checked at resolve time
- RNG determinism from `(seed, action sequence)`
- wave table and rotation behavior
- collision asset semantics
- current active loadout, unless training is intentionally restarted under a new loadout
- `eval_viewer.py` checkpoint weight mapping order, unless checkpoint format and policy architecture are migrated together

## Recommended Work Order

If the next phase is "make `codex3` the repo we actively improve", the safest order is:

1. Make `codex3` self-contained before feature work.
   Remove any script behavior that copies config, outputs, or libraries into external repos.

2. Clean up path coupling.
   Replace absolute `claude` and `runescape-rl-reference` paths with repo-local paths or explicit user-supplied env vars.

3. Decide what the real training build surface is.
   Either vendor the required PufferLib interface bits into this repo, or formalize a submodule/dependency strategy. Right now the training build still assumes an external source tree.

4. Regenerate local build state from scratch.
   The copied `build/` directory is old-path debris and should not be used as a trusted baseline.

5. Track or eliminate required untracked files.
   `fc_player_init.h` and the Raylib static library issue need to be resolved before the repo is truly portable.

6. Restore viewer/training parity.
   The mask divergence should be fixed or explicitly justified before using viewer replay as a trustworthy eval path.

7. Unify docs after code/path cleanup.
   Do not "polish docs" first. First decide the actual self-contained runtime/build model, then update `README.md`, `DESIGN.md`, and `rl_config.md` together.

8. Only then consider deduplicating backend source trees.
   The duplication is real technical debt, but deduplicating before parity is restored makes it harder to reason about what changed.

## Minimal "Do Not Break" Migration Rules

When converting `codex3` into a self-contained repo, the safest rules are:

- do not change observation shapes without migrating all consumers together
- do not change reward semantics and path semantics in the same step
- do not delete untracked local files until it is proven they are unnecessary
- do not trust copied build outputs as evidence of current source behavior
- do not assume docs override code
- do not assume viewer behavior equals training behavior until parity is explicitly restored

## Bottom Line

This repo is in a good place to improve, but not yet in a good place to "treat as a clean standalone source repo".

Its strengths are real:

- strong deterministic C core
- good reward iteration history
- real viewer
- clear Fight Caves encounter modeling

Its current liabilities are also clear:

- copied working-tree state instead of clean source state
- partial backend drift
- stale docs/comments
- required untracked files
- old-path coupling
- external PufferLib build assumptions

The next milestone should not be a gameplay change. It should be:

- make `codex3` self-contained
- make the runtime/build paths local
- restore parity between the two backend copies
- then start intentional improvements from that stable baseline
