# Rewrite Plan

## Current Program State

- PR1 is complete.
- PR2 is complete.
- PR3 is complete.
- PR4 is complete.
- PR5a is complete.
- PR5b is complete.
- PR6 is complete.
- Native implementation now includes the PR2 scaffold/load/query/close surface plus deterministic reset, episode initialization, reset-state projection, packed action decoding, visible-target ordering, rejection handling, the PR4 per-step tick/control skeleton, the PR5a native core combat/wave/terminal path, the PR5b special-mechanics parity surface, and the PR6 native flat-observation and reward-feature emission path.
- The current approved next step is PR7.
- The native rewrite boundary remains unchanged: the native runtime will eventually own the headless hot path, while legacy headless Kotlin and RSPS stay as oracle/reference validation infrastructure.
- The active ownership model remains unchanged: long-term active ownership is limited to the native headless training environment, headed demo environment, and RL/PufferLib connectivity code.

Important assumptions and fixes that affected PR1 artifact generation and therefore matter for PR2+:

- The retained headless build now relies on a checked-in script registry path rather than the removed legacy metadata generation path.
- The Python launcher/runtime bootstrap path now creates its extract-root parent deterministically before launch.
- The Python debug bridge was aligned to current `sim.*` class names and getter lookups used by the retained headless build.
- The parity harness now exposes the named parity scenarios required by the frozen Jad/healer/Tz-Kek fixtures.
- A narrow Jad-telegraph guard bug in the retained headless runtime was fixed so the frozen PR1 oracle artifacts represent the intended Fight Caves behavior.

## Scope of this workspace

This repo is now the planning and implementation workspace for the C/C++ Fight Caves rewrite. The current Kotlin/Java/Python systems remain in-tree only as reference implementations, parity oracles, and integration harnesses.

## Decision / Change Rules

- This plan is intended to be followed unless reality forces an adjustment.
- Changes are allowed, but they must be recorded before or at the time they are made.
- Each recorded change must identify what shifted, why it shifted, and which downstream PRs are affected.
- Later-PR work must not silently bleed into earlier PRs.
- Frozen contracts and checked-in goldens are the canonical acceptance target.
- Retained Kotlin and RSPS behavior is reference/oracle infrastructure only.
- If retained legacy behavior disagrees with frozen artifacts, do not silently re-baseline anything; record the mismatch explicitly and require an intentional decision.

## Known Recorded Mismatches

- PR3 reset baseline mismatch:
  - The checked-in PR1 reset fixture rotation for `seed=11001,start_wave=1` does not match the local retained Kotlin `Random(seed)` result observed during PR3.
  - Decision taken in PR3: the native reset scaffold is anchored to the frozen reset artifacts first, with retained Kotlin behavior treated as reference/oracle infrastructure rather than automatic authority when the two disagree.
  - Follow-up expectation: if this mismatch is investigated later, the outcome must be recorded explicitly and any re-baselining must be an intentional decision rather than an incidental side effect of native implementation work.

## Long-Term Active Ownership Model

The repo should stay lean. The intended long-term active code surface is:

- native headless training environment,
- headed demo environment,
- RL and PufferLib connectivity code.

Reference-only surfaces that remain in-tree but are not first-class rewrite targets in the initial phases:

- `runescape-rl/src/rsps`
- `pufferlib`

In practical terms:

- RSPS remains an oracle/reference implementation, not an early rewrite target.
- PufferLib remains trainer/runtime reference material and connectivity dependency context, not an early rewrite target.

## Current module inventory

### Essential reference implementations

- `runescape-rl/src/headless-env`
  - Pruned Kotlin simulator for Fight Caves.
  - Owns the current headless runtime interface: episode reset, action application, structured observation building, flat observation building, parity harnesses, and the existing fast batch kernel wrapper.
  - This is the highest-value reference for the native rewrite.

- `runescape-rl/src/rsps`
  - Full headed RSPS/server fork.
  - Owns the broadest gameplay behavior and the strongest oracle/reference implementation for mechanics parity.
  - Still important for validation, but too large to be the first rewrite boundary.

- `runescape-rl/src/rl`
  - Python training, replay, evaluation, benchmarking, subprocess orchestration, config loading, and current trainer integration.
  - Owns vecenv assembly, backend selection, reward weighting for `v2_fast`, and PufferLib integration.
  - This should remain the integration harness during the first native phases.

### Useful but not first-class rewrite targets

- `runescape-rl/src/headed-env`
  - Headed demo-lite, replay validation, and headed evidence tooling.
  - Useful as a fallback/reference path, but not part of the first native cut.

- `pufferlib`
  - Vendored trainer/runtime dependency.
  - Important because the current training loop assumes PufferLib-style vecenv behavior.
  - Not a good first rewrite target and not part of the initial native ownership surface.

### Likely not needed as long-term rewrite ownership

- Legacy report trees, benchmark packets, historical plans, run artifacts, and scattered module-local specs.
- Vendored/generated environments and caches.
- Old headed demo documentation and RSPS wiki content.

## Runtime boundaries today

### Game simulation and Fight Caves mechanics

- `runescape-rl/src/headless-env/game/FightCaveSimulationRuntime.kt`
- `runescape-rl/src/headless-env/game/HeadlessMain.kt`
- `runescape-rl/src/headless-env/game/OracleMain.kt`
- `runescape-rl/src/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/*`

Current ownership:

- Headless Kotlin runtime owns the training-facing simulator contract.
- RSPS owns the broader gameplay oracle and headed validation behavior.
- Headless and oracle both share Fight Caves content scripts and action semantics.

### Combat and parity-critical Fight Caves logic

- `TzTokJad`, `JadTelegraph`, `FightCaveEpisodeInitializer`, action adapters, and parity harness tests in `headless-env`.
- RSPS parity references in `runescape-rl/src/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/*`.

Current ownership:

- Headless Kotlin runtime provides the reference training-facing behavior.
- RSPS remains the strongest oracle for validating that behavior.

### Observation building

- Structured observation:
  - `runescape-rl/src/headless-env/game/HeadlessObservationBuilder.kt`
- Flat training observation:
  - `runescape-rl/src/headless-env/game/HeadlessFlatObservationBuilder.kt`
- Headed reference observation:
  - `runescape-rl/src/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveHeadedObservationBuilder.kt`

Current ownership:

- Kotlin owns both structured and flat observation schemas.
- Python consumes and projects them, but does not author the canonical schema.

### Reward logic

- V1 bridge path:
  - Python reward functions in `runescape-rl/src/rl/fight_caves_rl/rewards/*`
- V2 fast path:
  - Kotlin reward feature emission in `runescape-rl/src/headless-env/game/headless/fast/FastRewardFeatures.kt`
  - Python weighting in `runescape-rl/src/rl/fight_caves_rl/envs_fast/fast_reward_adapter.py`

Current ownership:

- Raw reward feature generation already belongs to the fast simulator path.
- Python still owns coefficient loading and final weighted reward computation.

### Training loop

- `runescape-rl/src/rl/fight_caves_rl/puffer/production_trainer.py`
- `runescape-rl/src/rl/fight_caves_rl/puffer/trainer.py`
- `runescape-rl/src/rl/scripts/train.py`

Current ownership:

- Python owns policy execution, rollout buffering, optimization, checkpointing, evaluation, and logging.

### Vectorization and env batching

- V1 bridge path:
  - `runescape-rl/src/rl/fight_caves_rl/bridge/batch_client.py`
  - `runescape-rl/src/rl/fight_caves_rl/envs/vector_env.py`
- V2 fast path:
  - `runescape-rl/src/rl/fight_caves_rl/envs_fast/fast_vector_env.py`
  - `runescape-rl/src/headless-env/game/FastFightCavesKernelRuntime.kt`
- Multi-process wrapper:
  - `runescape-rl/src/rl/fight_caves_rl/envs/subprocess_vector_env.py`

Current ownership:

- Python owns vecenv behavior, subprocess routing, and transport choices.
- Kotlin owns the actual fast batched simulator work for `v2_fast`.

### Replay and demo rendering

- Python replay/eval scripts and trace pack tooling under `runescape-rl/src/rl/fight_caves_rl/replay` and `runescape-rl/src/rl/scripts`.
- Headed demo-lite code in `runescape-rl/src/headed-env`.
- RSPS headed backend control in `runescape-rl/src/rsps`.

Current ownership:

- Demo/render/replay remain mixed Python and Kotlin/Java tooling.
- This is not the first rewrite boundary.

### Hardest boundaries to preserve

- Visible NPC ordering and `visible_index` stability.
- Action IDs and argument semantics for the seven supported actions.
- Episode reset semantics: seed, start wave, loadout, consumables, and rotation resolution.
- Flat observation feature ordering and append-only compatibility rules.
- Reward feature ordering and append-only compatibility rules.
- Jad telegraph state, prayer check timing, healer behavior, and Tz-Kek split behavior.
- Tick-stage order and clock/lockout behavior.

## Authoritative systems and contracts to preserve

### Parity strategy

Parity is enforced through validation infrastructure, not shared live logic across runtimes.

The native runtime should own the hot path.

Legacy headless Kotlin and RSPS paths should serve as oracle/reference validation infrastructure through:

- frozen contracts,
- golden reset fixtures,
- golden step traces,
- native-vs-legacy parity tests,
- append-only schema and versioning rules.

The goal is explicit comparison between native and legacy outputs. The goal is not to keep the runtimes coupled through shared live execution logic.

### Action contract

The shared action surface is already explicit and small:

- Action IDs `0..6` in `HeadlessActionType` and `FIGHT_CAVES_FAST_ACTION_SCHEMA`.
- Wait
- WalkToTile
- AttackVisibleNpc
- ToggleProtectionPrayer
- EatShark
- DrinkPrayerPotion
- ToggleRun

This contract should remain append-only.

### Structured observation contract

`HeadlessObservationBuilder.kt` is the current source of truth for:

- tick
- episode seed
- player stats and lockouts
- consumable counts
- wave state
- visible NPC list
- Jad telegraph field

This is authoritative for semantic validation and debugging, even if the native runtime eventually stops using structured observations on the hot path.

### Flat observation contract

`HeadlessFlatObservationBuilder.kt` and `FastFightCavesContracts.kt` define the current training contract:

- schema id: `headless_training_flat_observation_v1`
- dtype: `float32`
- base fields: `30`
- NPC stride: `13`
- max visible NPCs: `8`
- total feature count: `134`

The flat schema is parity-critical and hot-path critical.

### Reward feature contract

`reward_feature_schema.py` and `FastRewardFeatures.kt` define:

- contract id: `fight_caves_v2_reward_features_v1`
- append-only feature policy
- 16 emitted per-step reward features

This contract is already shaped for a native kernel boundary and should be preserved directly.

### Terminal code contract

`terminal_codes.py` defines the current shared terminal contract:

- `NONE`
- `PLAYER_DEATH`
- `CAVE_COMPLETE`
- `TICK_CAP`
- `INVALID_STATE`
- `ORACLE_ABORT`

### Episode-start and reset contract

Episode initialization must preserve:

- explicit seed-driven reset behavior,
- fixed levels and consumables,
- explicit start wave,
- deterministic wave rotation resolution,
- stable player spawn and inventory state.

### Parity traces and oracle comparison

The codebase already has parity-focused tests around:

- full-run traces,
- single-wave traces,
- Jad telegraph parity,
- Jad prayer resolution,
- healer scenarios,
- Tz-Kek split scenarios,
- deterministic replay same-seed/same-trace behavior.

Those tests define the behavioral floor for the native rewrite.

## Parity-critical mechanics that must survive

- Fight Caves episode initialization from seed and wave.
- Stable visible-target ordering and deterministic target indexing.
- Action rejection semantics for invalid, busy, or locked states.
- Walk/pathing behavior used by `WalkToTile`.
- Attack behavior used by `AttackVisibleNpc`.
- Prayer toggle semantics and mutual exclusion of protection prayers.
- Food/drink consumption and clock-based lockouts.
- Jad telegraph state exposure in both semantic and flat observations.
- Jad prayer-resolution timing.
- Jad healer behavior.
- Tz-Kek split behavior.
- Terminal behavior for death, completion, tick-cap truncation, and invalid states.
- No-future-leakage defaults in training-facing observations and rewards.

## Training stack shape

### Current trainer/runtime stack

- Python package: `fight-caves-rl-training`
- Python dependencies:
  - `jpype1`
  - `numpy`
  - `torch`
  - `orjson`
  - `msgpack`
  - `pyarrow`
  - `PyYAML`
  - `wandb`
- PufferLib is used for vecenv semantics and training integration.

### Backend split

- `v1_bridge`
  - Python `HeadlessBatchClient`
  - structured/raw observation path still available
  - more Python-side object conversion

- `v2_fast`
  - Kotlin fast runtime wrapped by `FastFightCavesKernelRuntime`
  - Python `FastKernelVecEnv`
  - minimal info mode only
  - flat observations and reward features are emitted in batch form

### What still depends on Python

- trainer loop and rollout buffering,
- config loading,
- reward coefficient loading and weighting for `v2_fast`,
- subprocess orchestration,
- policy encoding/decoding,
- logging and artifact generation,
- replay and evaluation scripts.

### What depends on PufferLib

- vecenv interface semantics,
- policy/trainer wiring,
- joint observation/action space handling,
- rollout send/recv loop expectations.

### Assumptions that matter for the rewrite

- The fast path is already buffer-oriented and batch-oriented.
- Python expects joint batch spaces and `reset/send/recv` vecenv behavior.
- Minimal-info mode is preferred for throughput.
- Training currently assumes it can keep the env side as a lower-level backend while Python retains orchestration.

## Performance-sensitive paths

Based on code structure, the most important hot paths are:

- batched episode reset and step in `FastFightCavesKernelRuntime.kt`,
- action decode and per-slot action application,
- `runtime.tick(1)` and the tick-stage pipeline,
- flat observation packing and copying,
- reward feature emission,
- terminal-state evaluation,
- Python vecenv `send/recv`,
- Python tensor copies and rollout buffering in the trainer,
- subprocess transport when multi-worker mode is used.

### Best candidates for native ownership first

- simulator state and per-tick mechanics,
- batched reset/step execution,
- flat observation writing,
- reward feature emission,
- terminal-code emission,
- parity-trace emission if needed for validation.

### Better left higher-level at first

- trainer and optimizer logic,
- PufferLib integration layer,
- experiment logging,
- replay/report packaging,
- headed demo tooling.

## Performance gates

Performance gates are explicit and separate by measurement type.

### Env-only SPS

- Definition: simulator-only reset/step throughput without learner/update cost.
- Floor for native fast path: `200,000` SPS.
- This is the first hard performance gate for default-path viability.

### End-to-end training SPS

- Definition: full training throughput including vecenv integration, rollout handling, policy execution, and learner/update overhead.
- Practical initial floor: `250,000` SPS.
- Stretch target: `500,000` SPS.

### Multi-worker aggregate SPS

- Definition: aggregate throughput across subprocess/native workers, measured separately from single-runtime env-only and end-to-end single-process runs.
- Initial floor: `300,000` SPS aggregate.
- Stretch target: `500,000` SPS aggregate while preserving parity and determinism.

These numbers must be tracked separately. An env-only win does not satisfy the end-to-end training target, and multi-worker scaling must not be used to hide a weak single-runtime hot path.

## Recommended rewrite boundary

Rewrite the Fight Caves simulator core plus:

- batched reset/step API,
- flat observation emission,
- reward feature emission,
- terminal-code emission.

Do not rewrite the Python trainer or headed RSPS path first.

### Why this is the best first boundary

- The current `v2_fast` path already isolates a kernel-like surface with typed batch buffers.
- Flat observations and reward features are part of the hot path; leaving them outside the native core would preserve unnecessary copying and reconstruction overhead.
- Python training can remain usable while the native runtime is validated against the Kotlin/RSPS references.
- RSPS/headed code stays available as oracle infrastructure instead of becoming a migration blocker.

### Why narrower boundaries are weaker

- Pure simulator core only:
  - Leaves flat observation and reward-feature work outside the native boundary.
  - Misses major hot-path costs and preserves awkward handoff points.

- Simulator plus env API bridge only:
  - Still leaves observation and reward projection split across languages.

### Why wider boundaries are riskier

- Rewriting trainer or PufferLib integration immediately adds unnecessary integration risk.
- Rewriting the full RSPS or headed demo path first explodes scope without improving the training hot path soon enough.

## Proposed target architecture

### Native side

- Fight Caves-only native runtime.
- Deterministic episode state and RNG.
- Stable action schema shared with existing Python code.
- Batched `reset` and `step` surfaces returning flat buffers.
- Optional parity-trace output for validation mode.

### Python side

- Existing config, curriculum, trainer, replay, and logging layers.
- Existing vecenv and subprocess orchestration semantics.
- Reward weighting stays in Python initially unless later profiling shows it should move down.

### Oracle side

- Keep `headless-env` and `rsps` available as parity references until native validation is complete.

## Early non-goals

The first rewrite phases do not include:

- trainer rewrite,
- PufferLib rewrite,
- full RSPS rewrite,
- headed demo rewrite beyond minimal parity-support changes,
- action-space redesign,
- broad RL redesign,
- broad observation or reward redesign beyond contract-preserving implementation work.

## Build and tooling implications

### What exists today

- Gradle Kotlin multi-project builds in `headless-env` and `rsps`.
- Python packaging in `runescape-rl/src/rl`.
- Vendored Python package/build setup in `pufferlib`.

### What will complicate the rewrite

- Current fast runtime bootstraps through JVM discovery and JPype.
- Current headless runtime still depends on Kotlin-side bootstrapping, data allowlists, and script metadata generation.
- The oracle/parity harnesses currently assume Kotlin runtime availability.
- Current data ownership is still embedded in the legacy code/data layout.

### Integration points the native rewrite will need

- Python-loadable native library or extension module.
- Stable descriptor API for action, observation, reward-feature, and terminal contracts.
- Compatibility layer in the Python RL package so `v2_fast` can switch from JPype/JVM runtime to native runtime without changing trainer code.
- Continued access to oracle validation against legacy headless and/or RSPS traces.

## Risk map

### Biggest technical risks

- Reproducing deterministic tick behavior exactly.
- Preserving visible-target ordering and action resolution semantics.
- Deciding how native code will source Fight Caves data without dragging in the full legacy runtime.

### Biggest parity risks

- Jad telegraph timing and prayer resolution.
- Tz-Kek split logic.
- Healer behavior and wave transition edge cases.
- Lockout clocks, delay semantics, and pathing side effects.

### Biggest integration risks

- Replacing JPype/JVM runtime loading without breaking Python vecenv contracts.
- Preserving batch buffer layouts exactly.
- Preserving subprocess worker behavior once a native backend is introduced.

### Biggest scope-creep risks

- Expanding from Fight Caves kernel rewrite into full RSPS rewrite.
- Pulling headed demo tooling into the first native milestone.
- Rewriting the trainer stack before the simulator boundary is stable.

## Recommended staged implementation approach

1. Freeze the contracts and golden fixtures that the native runtime must match.
2. Add a native scaffold that can expose the same batch-oriented surface as the current fast kernel.
3. Implement deterministic reset, action semantics, and core wave/combat progression.
4. Reach parity on flat observations, terminal codes, and reward-feature emission.
5. Swap the Python `v2_fast` backend to the native runtime behind the existing vecenv interface.
6. Run parity validation and long-run determinism checks against the legacy references.
7. Clear the explicit env-only, end-to-end, and multi-worker performance gates.
8. Only then optimize further or revisit whether more logic should move out of Python.

## Open questions

- Should the native runtime read existing Fight Caves data/config directly, or should the repo generate a smaller exported dataset for it?
- Should reward weighting remain in Python permanently, or move native once parity and profiling are stable?
- What binding approach should expose the native runtime to Python while keeping multi-worker startup simple?
- Do we keep structured observations as a validation-only surface, or require the native runtime to emit them in production too?
- How much of the existing parity harness can be reused directly versus replaced with native-vs-legacy trace comparison tooling?
- Whether structured observations should remain generated in native production mode or be retained only as a validation/debug surface after parity is proven.
