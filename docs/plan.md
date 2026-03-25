# Rewrite Plan

## Current Program State

- PR1 is complete.
- PR2 is complete.
- PR3 is complete.
- PR4 is complete.
- PR5a is complete.
- PR5b is complete.
- PR6 is complete.
- PR7 is complete.
- PR8a is complete.
- PR8a-followup is complete.
- PR8a.2 is complete.
- PR8a.3 is complete.
- PR8a.4 is complete.
- PR8a.5 is complete.
- DV1 is complete.
- TRAIN1 is complete.
- TRAIN2 is complete.
- Native implementation now includes the PR2 scaffold/load/query/close surface plus deterministic reset, episode initialization, reset-state projection, packed action decoding, visible-target ordering, rejection handling, the PR4 per-step tick/control skeleton, the PR5a native core combat/wave/terminal path, the PR5b special-mechanics parity surface, the PR6 native flat-observation and reward-feature emission path, and the PR7 Python integration/cutover plumbing for native vecenv, subprocess, and eval usage.
- The native headless runtime is now functionally complete enough that the primary remaining blocker to default cutover is learner-stack/training-path performance; backend-driven native debugging and inspection tooling is now materially in place through DV1 to DV3.
- The initial PR8a validation pass measured native env-only throughput, multi-worker aggregate throughput, end-to-end training throughput, and native determinism/stability on the current benchmark host, but its end-to-end training result used a CPU-only Torch build and therefore did not represent the intended CUDA learner path.
- PR8a-followup switched the RL project to a CUDA-capable Torch build, verified that `train.device` resolves to CUDA on this host, and verified that the benchmarked learner path keeps policy weights and rollout buffers on CUDA.
- PR8a.2 verified that the retained PR8a train benchmark was heavily distorted by startup, eval, and checkpoint overhead, but the normalized steady-state learner result still remains well below the explicit end-to-end training gate.
- PR8a.2 also confirmed that the remaining PufferLib advantage fallback could not be removed with repo-local tooling in this pass: the installed `compute_puff_advantage` operator is still CPU-only for this environment and the repo-local CUDA package install still does not provide a real `nvcc` binary.
- PR8a.3 reran the real subprocess native CUDA training path with deeper learner-path instrumentation and confirmed that the normalized steady-state result is effectively at the old `~16k` end-to-end baseline (`15.4k` end-to-end SPS, `24.5k` train-only SPS), while the retained benchmark shape still reports only `~388` SPS because it is dominated by startup/eval/checkpoint overhead.
- PR8a.3 also established that the explicit `250,000` SPS training floor is currently blocked primarily by learner-side scalar synchronization and cleanup/metric bookkeeping on tiny update sizes, not by env throughput and not mainly by the missing PufferLib CUDA advantage kernel.
- PR8a.4 was the approved narrow learner-loop optimization pass targeting exactly those PR8a.3 bottlenecks: repeated `.item()` metric extraction, rollout scalar index extraction, explained-variance cleanup cost, and other learner-side orchestration sync.
- PR8a.4 completed with narrow trainer-hot-path changes only: it removed the measured scalar-sync bottlenecks, lifted normalized train-only throughput from `24.5k` SPS to `71.4k` SPS, and reduced measured learner-side synchronization buckets sharply, but end-to-end throughput still remained far below the explicit gate because visible wait shifted into rollout-side host/device boundaries and the current tiny update shape still leaves the GPU badly underutilized.
- PR8a.5 was the approved narrow rollout-boundary and tiny-batch throughput pass targeting exactly those remaining PR8a.4 bottlenecks: host-device transfer, action materialization back to CPU, and rollout orchestration on the real native CUDA path.
- PR8a.5 completed with narrow rollout/trainer-boundary changes only: it slightly improved the retained benchmark, kept train-only throughput roughly flat relative to PR8a.4 on the benchmark shape, and confirmed via one explicit `128 env / 512 batch` probe that larger updates lift train-only throughput sharply while leaving end-to-end throughput still bounded near the old baseline by rollout-side host/device transfer and action materialization.
- The remaining known PufferLib CPU fallback is still real (`ADVANTAGE_CUDA=false`, no system `nvcc` on PATH), but it is not currently the dominant throughput limiter on this host.
- DV1 established a native C Raylib viewer foundation that renders backend-owned scene metadata and runtime snapshots, supports replay/snapshot inspection, and stays separate from the training hot path.
- DV2 is complete: the native viewer now routes manual move, attack, item, and prayer inputs through the same backend action path used by training, and the HUD now exposes selected/hovered entity data plus last-action feedback from backend step results.
- DV3 is complete: the native viewer now supports backend-driven replay loading from native viewer replay files, replay scrub/step controls, replay-pack and trace-pack inspection, and deeper per-entity inspector overlays driven directly from backend snapshots and step feedback.
- DV4 is complete only as an initial native-owned theme and presentation stub: it proved that a native asset bundle can be baked into the viewer build, but it does not yet meet the intended visual/demo/debug bar and it still reads as a hand-authored approximation rather than the real Fight Caves.
- TRAIN1 established the first real native training and debugging baseline on the current stable path: a `train_fast_v2_mlp64`-derived native CUDA run completed for `1,048,576` timesteps, produced a checkpoint, evaluated cleanly on the supported native seed packs, and was inspected through the native replay and viewer tooling.
- TRAIN1 also established that the current stack is already usable for real training/debugging work even though it does not clear the PR8b throughput gate: the native backend, Python training path, eval path, replay export path, and shared-backend viewer all work together on a real checkpoint without backend instability.
- The first real agent-performance baseline is behaviorally weak: the evaluated checkpoint never clears wave 1 on the supported native seed packs and collapses into repeated `eat_shark` actions, so the next most important active problem is agent behavior rather than throughput.
- TRAIN2 completed the first narrow behavior-focused follow-up pass:
  - structured policy action handling now masks unavailable consumables, routes attack target selection through the currently visible target count, and zeroes irrelevant action heads for sampling and greedy eval,
  - `reward_shaped_v2` now penalizes `food_used`, `invalid_action`, and `idle_penalty_flag` more explicitly.
- TRAIN2 removed the specific `eat_shark` collapse from TRAIN1, but it did not improve wave progression:
  - the new checkpoint still never clears wave 1 on the supported native seed packs,
  - it now collapses into repeated `attack_visible_npc` at `tick=0`,
  - those attacks are rejected because the reset-state policy observation has no visible targets yet, so the agent never advances into the productive `wait -> attack` pattern.
- The current highest-value problem remains agent behavior, but the diagnosis is now sharper:
  - the main issue is no longer wasteful consumable spam,
  - it is now reset-step action sequencing and training around a no-target initial observation, combined with action-space/behavior-learning instability.
- ARCH-PERF2 is complete: the current end-to-end throughput ceiling is now diagnosed as a broader architecture constraint centered on the Python trainer/orchestration path, the vecenv/subprocess rollout boundary, and the CPU-owned host/device transfer model rather than the native Fight Caves backend itself.
- ARCH1 is complete: the active native codebase is now split into `runescape-rl/src/training-env` and `runescape-rl/src/demo-env`, with the shared-backend rule unchanged.
- Local PufferLib Ocean C envs (`osrs_pvp`, `osrs_zulrah`, and sampled mature native envs) were reviewed as implementation references only, not as active dependencies.
- Repo-boundary cleanup is complete: legacy JVM code now lives only under `reference/legacy-headless-env`, `reference/legacy-headed-env`, and `reference/legacy-rsps`.
- The active `runescape-rl` tree now contains only native C and Python code; no `.kt` or `.java` sources remain inside the active tree.
- PR8b default-fast-path cutover is not approved yet because neither the retained benchmark nor the normalized steady-state learner result clears the explicit end-to-end training gate.
- PR8b remains blocked/deferred; no default cutover work is approved until either the remaining learner-path bottlenecks are reduced substantially or an explicit decision is made to accept lower-than-target training throughput.
- PR8a.4 was the approved narrow learner-loop optimization pass focused on scalar synchronization and cleanup overhead.
- PR8a.5 was the approved narrow rollout-boundary and tiny-batch throughput pass focused on host-device transfer, action materialization, and rollout orchestration overhead.
- After PR8a.5, the narrow performance track is paused. PR8a.3 through PR8a.5 exhausted the small local learner-loop and rollout-boundary optimization path on the current architecture for this host.
- Any future attempt to chase the explicit `250,000` end-to-end SPS gate should be treated as a broader architecture investigation or redesign effort, not another narrow `PR8a.x` hot-path pass.
- PR8b remains blocked/deferred while that broader architecture question is unresolved.
- The narrow performance track remains paused after PR8a.5, and PR8b remains blocked/deferred.
- `docs/training_performance.md` is now the active training-performance source of truth for current numbers, bottleneck interpretation, and history; `docs/agent_performance.md` is now the active source of truth for real checkpoint behavior, progression, and replay/viewer-derived agent diagnosis.
- The current system remains in real training/debugging use on the native backend and shared-backend viewer stack, but training work is paused while the viewer is brought up to the required readable/manual-debug bar.
- Primitive rendering is no longer considered sufficient for the native viewer. The current DV4 result does not yet meet the intended Fight Caves visual/demo/debug bar.
- ARCH-VIEW1 is complete: `reference/legacy-headed-env` is a control/debug wrapper, while the real legacy Fight Caves presentation path is `reference/legacy-rsps/void-client` plus synced client-cache data and RSPS Fight Caves/TzHaar data tables.
- DV4b is complete:
  - the validated cache input is `reference/legacy-headless-env/data/cache`,
  - `reference/legacy-rsps/data/cache` is empty in this checkout and is not the active export input,
  - the native viewer now builds and ships a first real cache-derived native-owned Fight Caves scene slice under `runescape-rl/src/demo-env/assets/generated`,
  - that slice includes real terrain coverage for `m37_79` and `m37_80`, real cache-derived HUD/prayer/inventory-slot sprites, and real item/NPC metadata exported into native-owned bundles.
- DV4b also exposed the remaining explicit viewer-parity blockers:
  - Fight Caves object archives `l37_79` and `l37_80` are not readable from the validated cache input without region XTEAs,
  - richer item-icon and player/NPC model renderables are still blocked by the absence of a committed offline model-render export path in this checkout.
- ARCH-VIEW2 is investigation-complete but not sufficient as the main implementation path:
  - the offline export path now emits native-owned object-definition bundles plus item, NPC, and default-player model/render inputs under `runescape-rl/src/demo-env/assets/generated/models`,
  - the local XTEA fixtures present in `reference/legacy-rsps/tools/src/test/resources/xteas` are only test samples and do not contain Fight Caves regions `9551` and `9552`,
  - real Fight Caves object-placement export therefore remains blocked on an external XTEA source,
  - final raster item icons are still not exported in this checkout even though the item model/render recipes are now committed.
- The pre-DV4c viewer result from DV4 and DV4b was still not acceptable as a Fight Caves visual/demo/debug surface:
  - DV4 delivered only a native-authored theme stub,
  - DV4b proved that the cache-derived path works,
  - but the resulting viewer still does not yet read closely enough like the real Fight Caves client path.
- DV4c is complete:
  - the viewer now uses a real `.dat2/.idx*` cache-reader-based export path against `reference/legacy-headless-env/data/cache`,
  - the native-owned bundle now includes terrain exported from cache index `5` across Fight Caves regions `(37,79)`, `(37,80)`, `(38,80)`, and `(39,80)`,
  - floor definitions now come from cache index `2`, core Fight Caves NPC definitions come from cache index `18` with model bytes from index `7`, and the viewer HUD sprite subset comes from cache index `8`,
  - the native viewer now loads TERR, MDL2, and PNG outputs from native-owned generated bundles instead of relying on fake primitive scene dressing as the primary presentation path.
- DV4c materially improves the viewer beyond the old fake-scene result, but it does not yet finish scene parity:
  - real terrain, NPC model data, and cache-derived sprite assets are now in place,
  - Fight Caves object-archive props still remain blocked on missing XTEAs for regions `9551` and `9552`,
  - richer item/player render export is still incomplete.
- The next approved implementation step is DV4d: Object-Archive/XTEA Unblock And Richer Render Export.
- TRAIN3 and later behavior-focused training/debugging passes remain paused until the viewer asset/export pipeline is sufficiently unblocked for DV5 through DV6.
- The next unresolved training-side problem remains agent behavior, but it is not the immediate implementation priority. After the viewer-completion sequence, the next training problem is still reset-step sequencing (`wait` before attack) rather than raw throughput.
- Throughput remains an architecture-level issue only in the context of PR8b and the explicit `250,000` SPS target. Any future attempt to chase that gate should still be treated as a broader architecture redesign question, not another narrow optimization pass.
- Viewer asset and presentation polish are no longer deferred. The main implementation path now runs through DV4d before DV5 to DV6 resume the viewer-completion sequence.
- The native rewrite boundary remains unchanged: the native runtime will eventually own the headless hot path, while `reference/legacy-headless-env` and `reference/legacy-rsps` stay as oracle/reference validation infrastructure.
- The active ownership model now includes the native debug/playable viewer-demo harness alongside the native headless training environment and RL/PufferLib connectivity and training code.

Important assumptions and fixes that affected PR1 artifact generation and therefore matter for PR2+:

- The retained headless build now relies on a checked-in script registry path rather than the removed legacy metadata generation path.
- The Python launcher/runtime bootstrap path now creates its extract-root parent deterministically before launch.
- The Python debug bridge was aligned to current `sim.*` class names and getter lookups used by the retained headless build.
- The parity harness now exposes the named parity scenarios required by the frozen Jad/healer/Tz-Kek fixtures.
- A narrow Jad-telegraph guard bug in the retained headless runtime was fixed so the frozen PR1 oracle artifacts represent the intended Fight Caves behavior.

## Scope of this workspace

This repo is now the planning and implementation workspace for the C/C++ Fight Caves rewrite. The active `runescape-rl` tree is reserved for native C and Python ownership only. Legacy JVM reference surfaces now live under the root-level `reference/` directory and are retained only for logic study, oracle validation, fallback comparison, and asset sourcing.

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
- native debug viewer / manual debug harness,
- RL and PufferLib connectivity and training code.

Reference-only surfaces that now live outside `runescape-rl` and are not first-class rewrite targets in the initial phases:

- `reference/legacy-headless-env`
- `reference/legacy-headed-env`
- `reference/legacy-rsps`
- `pufferlib`

In practical terms:

- Legacy headless-env, legacy headed-env, and legacy RSPS may be used only for logic/system study, oracle validation, fallback comparison, and asset sourcing.
- No active runtime dependency on legacy Java/Kotlin code is allowed.
- RSPS remains an oracle/reference implementation, not an early rewrite target.
- Legacy headless-env remains a reference simulator/oracle, not part of the active owned tree.
- Legacy headed-env remains a reference presentation/fallback surface, not an active owned destination.
- PufferLib remains trainer/runtime reference material and connectivity dependency context, not an early rewrite target.
- The native viewer is part of the active native ownership surface, not a throwaway demo path.

## Viewer Philosophy

- The viewer is a native C debug and play tool built around the same backend the trainer uses.
- The viewer renders native runtime state and backend-owned scene metadata directly.
- The viewer does not own gameplay mechanics, combat rules, wave flow, item logic, or prayer logic.
- The existing headed demo remains a reference and presentation path, not the primary native debug tool.

## Shared-Backend Viewer Rules

- The viewer is a shared-backend manual-debug harness, not a separate simulation.
- Viewer code may render state, inspect state, replay state, and send actions into the backend, but it must not implement viewer-only gameplay logic.
- If the viewer needs new gameplay-relevant state or interaction capability, that capability must be added to the backend or backend-facing interface first.
- Backend changes that affect mechanics, state, action handling, items, stats, NPC behavior, wave flow, or gameplay-relevant observations must remain reflected in the viewer integration path immediately.

## Current module inventory

### Active implementation surfaces

- `runescape-rl/src/training-env`
  - Native C headless runtime and the minimal native-owned data subset required by active builds.
  - This is the single gameplay-truth backend and the primary implementation target.

- `runescape-rl/src/demo-env`
  - Native C debug/playable viewer harness layered on the same backend used by training.
  - Owns rendering, inspection, input shell concerns, and a native-owned exported asset bundle only; it must not own gameplay logic.

- `runescape-rl/src/rl`
  - Python training, replay, evaluation, benchmarking, subprocess orchestration, config loading, native loading, and trainer integration.
  - Owns vecenv assembly, backend selection, reward weighting for `v2_fast`, and PufferLib connectivity.
  - This remains the integration harness during the native phases.

### Reference-only legacy surfaces

- `reference/legacy-headless-env`
  - Pruned Kotlin simulator for Fight Caves.
  - Highest-value legacy simulator reference for parity study, oracle validation, and fallback comparison.

- `reference/legacy-headed-env`
  - Legacy headed demo-lite and headed validation tooling.
  - Useful for presentation fallback, logic study, and asset sourcing, but not an active owned destination.

- `reference/legacy-rsps`
  - Full headed RSPS/server fork.
  - Broadest oracle/reference implementation for mechanics parity and backend-control fallback comparison.

- `pufferlib`
  - Vendored trainer/runtime dependency context.
  - Important because the current training loop assumes PufferLib-style vecenv behavior, but not a first rewrite target.

### Not active long-term ownership

- Legacy report trees, benchmark packets, historical plans, run artifacts, and scattered module-local specs.
- Vendored/generated environments and caches.
- Old headed demo documentation and RSPS wiki content.

## Runtime boundaries today

### Game simulation and Fight Caves mechanics

- `reference/legacy-headless-env/game/FightCaveSimulationRuntime.kt`
- `reference/legacy-headless-env/game/HeadlessMain.kt`
- `reference/legacy-headless-env/game/OracleMain.kt`
- `reference/legacy-rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/*`

Current ownership:

- Headless Kotlin runtime owns the training-facing simulator contract.
- RSPS owns the broader gameplay oracle and headed validation behavior.
- Headless and oracle both share Fight Caves content scripts and action semantics.

### Combat and parity-critical Fight Caves logic

- `TzTokJad`, `JadTelegraph`, `FightCaveEpisodeInitializer`, action adapters, and parity harness tests in `reference/legacy-headless-env`.
- RSPS parity references in `reference/legacy-rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/*`.

Current ownership:

- Headless Kotlin runtime provides the reference training-facing behavior.
- RSPS remains the strongest oracle for validating that behavior.

### Observation building

- Structured observation:
  - `reference/legacy-headless-env/game/HeadlessObservationBuilder.kt`
- Flat training observation:
  - `reference/legacy-headless-env/game/HeadlessFlatObservationBuilder.kt`
- Headed reference observation:
  - `reference/legacy-rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveHeadedObservationBuilder.kt`

Current ownership:

- Kotlin owns both structured and flat observation schemas.
- Python consumes and projects them, but does not author the canonical schema.

### Reward logic

- V1 bridge path:
  - Python reward functions in `runescape-rl/src/rl/fight_caves_rl/rewards/*`
- V2 fast path:
  - Kotlin reward feature emission in `reference/legacy-headless-env/game/headless/fast/FastRewardFeatures.kt`
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
  - `reference/legacy-headless-env/game/FastFightCavesKernelRuntime.kt`
- Multi-process wrapper:
  - `runescape-rl/src/rl/fight_caves_rl/envs/subprocess_vector_env.py`

Current ownership:

- Python owns vecenv behavior, subprocess routing, and transport choices.
- Kotlin owns the actual fast batched simulator work for `v2_fast`.

### Replay and demo rendering

- Python replay/eval scripts and trace pack tooling under `runescape-rl/src/rl/fight_caves_rl/replay` and `runescape-rl/src/rl/scripts`.
- Legacy headed demo-lite code in `reference/legacy-headed-env`.
- RSPS headed backend control in `reference/legacy-rsps`.

Current ownership:

- Python still owns replay/eval packaging and export helpers.
- `reference/legacy-headed-env` is only a control/debug wrapper and validation shell, not the real 3D Fight Caves renderer.
- The real legacy Fight Caves presentation path is the RSPS `void-client` plus synced client-cache data and RSPS Fight Caves/TzHaar semantic tables.
- The current native viewer DV4 presentation is still a placeholder/stub and not yet scene-parity sufficient.

## Legacy Fight Caves Render And Asset Source

- The actual headed Fight Caves scene was not built in `reference/legacy-headed-env`.
- [DemoLiteUi.kt](/home/joe/projects/runescape-rl/codex2/reference/legacy-headed-env/src/main/kotlin/DemoLiteUi.kt) is a Swing action panel and validation overlay. It exposes actions, wave/state text, and target buttons, but it does not render the real cave scene.
- The real legacy visual path is the RSPS client:
  - `reference/legacy-rsps/void-client/client/src`
  - `reference/legacy-rsps/void-client/client/resources`
  - `reference/legacy-rsps/scripts/build_fight_caves_demo_client.sh`
  - `reference/legacy-rsps/scripts/run_fight_caves_demo_client.sh`
  - `reference/legacy-rsps/scripts/launch_fight_caves_demo_client.ps1`
- That launcher syncs cache files into the client cache before launch. In other words, the real arena floor, lava, walls, props, interface art, item sprites, and model presentation come from the client cache and the client renderer, not from the Kotlin demo wrapper.
- The RSPS semantic/source tables that identify the relevant Fight Caves content are:
  - `reference/legacy-rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.areas.toml`
  - `reference/legacy-rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.ifaces.toml`
  - `reference/legacy-rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.npcs.toml`
  - `reference/legacy-rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.anims.toml`
  - `reference/legacy-rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.gfx.toml`
  - `reference/legacy-rsps/data/area/karamja/tzhaar_city/tzhaar_city.objs.toml`
  - `reference/legacy-rsps/data/area/karamja/tzhaar_city/tzhaar_city.npcs.toml`
  - `reference/legacy-rsps/data/area/karamja/tzhaar_city/tzhaar_city.anims.toml`
  - `reference/legacy-rsps/data/area/karamja/tzhaar_city/tzhaar_city.gfx.toml`
  - `reference/legacy-rsps/data/entity/player/inventory/inventory.ifaces.toml`
  - `reference/legacy-rsps/data/entity/player/inventory/inventory_side.ifaces.toml`
  - `reference/legacy-rsps/data/entity/player/modal/icon.items.toml`
- Those TOML files are necessary semantic metadata, but they are not the final visual assets:
  - `tzhaar_fight_cave.areas.toml` gives cave bounds and named subregions,
  - `tzhaar_city.objs.toml` gives interactive object ids like cave entrances and lava forge ids,
  - `tzhaar_fight_cave.ifaces.toml` gives the Fight Caves overlay interface id,
  - `*.npcs.toml`, `*.anims.toml`, and `*.gfx.toml` identify the NPC ids, animation ids, and combat-visual effect ids.
- The actual floor tiles, lava treatment, walls, rock meshes, object meshes, interface sprites, and item sprites are cache-derived client assets.
- `reference/legacy-rsps/data/cache` is currently empty in this checkout. That means the repo does not currently contain the full client-side presentation assets needed for native scene parity, and any future viewer-parity work must make its cache input explicit rather than pretending the remaining gap is just hand-authored polish.

## Recommended Native Viewer Asset Export Strategy

- Inputs to the offline export/adaptation pipeline:
  - RSPS semantic tables under `reference/legacy-rsps/data/minigame/tzhaar_fight_cave/*`
  - RSPS TzHaar city tables under `reference/legacy-rsps/data/area/karamja/tzhaar_city/*`
  - player UI tables under `reference/legacy-rsps/data/entity/player/inventory/*`
  - modal/icon tables under `reference/legacy-rsps/data/entity/player/modal/icon.items.toml`
  - the RSPS `void-client` source/resources and an explicit cache input path
  - optional carry-over legacy header art such as `reference/legacy-headless-env/assets/fight-caves-header.png`
- Native-owned outputs that should be produced and loaded by the viewer:
  - Fight Caves terrain mesh plus height and camera-anchor metadata
  - Fight Caves object-placement bundle plus textured atlas subset for the cave shell, lava-edge props, rocks, and key scene dressing
  - minimal NPC/player visual bundle for Fight Caves entities and the default player loadout
  - minimal HUD/interface sprite bundle:
    - Fight Caves wave overlay
    - inventory and side-tab frames
    - prayer icons
    - shark, prayer-potion, and ammo item sprites
    - click-cross and hitsplat sprites if used
  - scene/layout metadata that maps backend tiles to the exported scene and UI anchors
- Those exported assets should live under `runescape-rl/src/demo-env/assets` as native-owned generated bundles. The viewer may read only those generated/native-owned outputs at runtime.
- The pipeline should never copy wholesale:
  - the full RSPS client,
  - the full client cache,
  - the full item/interface/model archives,
  - large unrelated world regions.
- The rule stays unchanged:
  - backend = gameplay truth,
  - viewer = rendering + input + debugging only,
  - export pipeline = offline adaptation only, never a second gameplay implementation.

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

- `runescape-rl/src/training-env`: Fight Caves-only native runtime.
- Deterministic episode state and RNG.
- Stable action schema shared with existing Python code.
- Batched `reset` and `step` surfaces returning flat buffers.
- Optional parity-trace output for validation mode.
- `runescape-rl/src/demo-env`: native viewer/debug shell that renders and drives the same backend through backend-facing interfaces rather than separate mechanics.
- `runescape-rl/src/demo-env` also owns a native-owned exported asset bundle generated offline from legacy/client/cache inputs; it must not read from `reference/legacy-*` at runtime.

### Python side

- Existing config, curriculum, trainer, replay, and logging layers.
- Existing vecenv and subprocess orchestration semantics.
- Reward weighting stays in Python initially unless later profiling shows it should move down.

### Oracle side

- Keep `reference/legacy-headless-env` and `reference/legacy-rsps` available as parity references until native validation is complete.

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

- Native C build targets in `runescape-rl/src/training-env` and `runescape-rl/src/demo-env`.
- Gradle Kotlin multi-project builds in `reference/legacy-headless-env` and `reference/legacy-rsps`.
- Python packaging in `runescape-rl/src/rl`.
- Vendored Python package/build setup in `pufferlib`.

PufferLib Ocean note:

- Local Ocean C envs were reviewed as implementation references for component layout, binding shape, build organization, and debug/viewer patterns.
- They are not active dependencies of this repo and do not change the shared-backend ownership rules here.

### What will complicate the rewrite

- Current fast runtime bootstraps through JVM discovery and JPype.
- Current headless runtime still depends on Kotlin-side bootstrapping, data allowlists, and script metadata generation.
- The oracle/parity harnesses currently assume Kotlin runtime availability.
- Current data ownership is still embedded in the legacy code/data layout.
- Viewer scene parity now requires an explicit offline asset/export pipeline from legacy/client/cache inputs into native-owned viewer bundles.
- The current checkout does not contain a usable RSPS-side cache input (`reference/legacy-rsps/data/cache` is empty).
- DV4b validated and now uses `reference/legacy-headless-env/data/cache` as the explicit cache input for the native viewer export pipeline.
- The current generated scene slice therefore depends on:
  - RSPS semantic tables,
  - RSPS void-client render/interface references,
  - the validated cache input under `reference/legacy-headless-env/data/cache`.
- The remaining blocker on cache-derived prop placement is not cache absence; it is missing Fight Caves region XTEAs for `l37_79` and `l37_80`.

### Integration points the native rewrite will need

- Python-loadable native library or extension module.
- Stable descriptor API for action, observation, reward-feature, and terminal contracts.
- Compatibility layer in the Python RL package so `v2_fast` can switch from JPype/JVM runtime to native runtime without changing trainer code.
- Continued access to oracle validation against legacy headless and/or RSPS traces.
- Offline asset exporters/adapters that turn bounded legacy/client/cache inputs into native-owned viewer bundles with no runtime dependency on `reference/legacy-*`.

## End-to-End Throughput Ceiling Investigation

### Current ceiling diagnosis

- The native backend is no longer the dominant limit:
  - env-only SPS on the native path is already above `400k`,
  - multi-worker aggregate SPS on the native path is already above `300k`,
  - the end-to-end path remains near `14k` to `16k` SPS.
- The exercised training path is structurally CPU-owned at the rollout boundary:
  - `NativeKernelVecEnv.recv()` returns NumPy/CPU buffers,
  - the trainer then converts observations, rewards, and dones into Torch tensors and copies them onto CUDA,
  - the trainer then converts sampled actions back to CPU/NumPy before sending them to the vecenv.
- The subprocess path adds a second architectural boundary:
  - the worker loop receives commands over a control pipe,
  - runs `vecenv.send()` and `vecenv.recv()` inside the worker,
  - serializes the transition and sends it back to the parent after every step batch.
- PR8a.4 proved that local learner-loop synchronization was not the whole story:
  - train-only SPS jumped from about `24.5k` to about `71.4k`,
  - end-to-end SPS still stayed near the old `~16k` baseline.
- PR8a.5 then showed that even after the learner-loop sync fixes, the dominant wall time is still rollout-side host/device transfer and action materialization.
- The missing PufferLib CUDA advantage kernel remains real, but it is not the primary blocker on this host.
- Embedded native topology is somewhat better than subprocess for pure env throughput, but the current NumPy/CPU rollout contract still exists in both modes, so embedded alone is unlikely to close a `14k` to `250k` gap.
- The current tiny synchronous rollout/update shape is also part of the problem:
  - larger updates improved train-only throughput materially,
  - they did not break the end-to-end ceiling because the Python/CPU rollout boundary stayed dominant.

### What the Ocean references imply

- The Ocean C envs are useful for one thing here: they keep env stepping and env buffer ownership inside native code, with Python acting as a thin integration layer.
- They do not solve the learner-side GPU boundary automatically.
- The useful pattern to adopt is the thin native/Python boundary with native-owned env buffers and minimal Python per-step orchestration.
- The pattern to avoid is assuming that faster native env stepping alone will deliver large end-to-end gains once the learner path is dominated by Python control flow and CPU/GPU round trips.

### Credible architecture options

#### Option A: Accept the current throughput class

- Expected upside:
  - lowest risk and fastest path to productive training/debugging work,
  - preserves the current shared-backend truth and viewer model unchanged.
- Expected risk:
  - explicit `250,000` SPS target is effectively retired for this architecture,
  - PR8b stays blocked unless the cutover gate is explicitly relaxed.
- Implementation scope:
  - no major architecture work,
  - continue with training/debugging and viewer usage on the current path.
- Likely effect on parity/debuggability:
  - best of all options; no additional moving parts.
- Shared-backend truth:
  - preserved completely.

#### Option B: Moderate redesign inside the current Python/PufferLib integration model

- Expected upside:
  - could reduce some boundary cost by pushing harder toward embedded native vecenv, pinned host buffers, fewer info/control-plane round trips, and larger default update shapes,
  - keeps the existing Python trainer contract mostly recognizable.
- Expected risk:
  - moderate integration work with uncertain upside,
  - likely improves tens-of-thousands-class throughput rather than making `250,000` SPS credible.
- Implementation scope:
  - medium,
  - touches vecenv, trainer, native loader/buffer plumbing, and benchmark shapes,
  - still avoids a full trainer rewrite.
- Likely effect on parity/debuggability:
  - good, because the native backend remains the single gameplay truth.
- Shared-backend truth:
  - preserved.

#### Option C: Broader rollout/learner-boundary redesign

- Expected upside:
  - this is the first option that makes the explicit `250,000` SPS target even plausibly reachable,
  - removes the per-iteration CPU/NumPy ownership model and reduces Python orchestration from the hot path.
- Expected risk:
  - high,
  - effectively becomes a trainer/runtime architecture project rather than a local optimization pass.
- Implementation scope:
  - large,
  - examples include a C/C++/CUDA rollout runner, Torch-extension-backed batch buffers, GPU-friendly action packing/materialization, and a much thinner Python control loop.
- Likely effect on parity/debuggability:
  - acceptable if done carefully,
  - parity/debuggability remain strong only if the same native backend stays authoritative and the viewer continues to consume backend state rather than reimplementing mechanics.
- Shared-backend truth:
  - preserved if the redesign changes only the rollout/learner boundary and not gameplay ownership.

#### Option D: Full trainer-stack redesign or alternate training runtime

- Expected upside:
  - maximum freedom to optimize throughput.
- Expected risk:
  - highest risk and largest scope,
  - destabilizes training behavior, integration assumptions, and maintenance burden all at once.
- Implementation scope:
  - very large,
  - effectively a new training stack project.
- Likely effect on parity/debuggability:
  - weakest unless carefully controlled,
  - easy to lose the clean separation between gameplay truth and training infrastructure.
- Shared-backend truth:
  - can be preserved, but only with significant discipline.

### Recommendation

- If the explicit `250,000` end-to-end SPS target is still a hard requirement, the current overall approach is not sufficient.
- On the current Python/PufferLib/send-recv/CPU-buffer architecture, the target does not look realistic.
- The recommended future performance move is Option C, not another narrow local pass:
  - redesign the rollout/learner boundary so the training path is no longer dominated by Python orchestration and CPU-owned action/observation materialization.
- If the project does not actually require that target anymore, Option A is the pragmatic choice:
  - accept the current throughput class, keep PR8b blocked, and proceed with training/debugging on the current stable shared-backend system.

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
