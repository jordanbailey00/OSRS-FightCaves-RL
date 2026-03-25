# PR Plan

This plan assumes the current pass has already cleaned the workspace and established the planning docs. The PRs below are for the actual native rewrite after plan approval.

## Execution Status

- PR1
  - status: done
  - goal: freeze the contracts and check in deterministic goldens that the native runtime must match
  - dependency: none
  - note: completed as planned; generator/bootstrap fixes were required to make the retained legacy paths reproducible after cleanup
- PR2
  - status: done
  - goal: land the native project skeleton, local build target, Python-loadable probe surface, and frozen descriptor API only
  - dependency: PR1
  - note: completed as planned with an explicit descriptor-only guard so PR2 cannot silently absorb PR3 reset/step work
- PR3
  - status: done
  - goal: move deterministic reset and episode initialization into the native runtime
  - dependency: PR2
  - note: completed with deterministic reset/init, multi-slot reset outputs, reset-state flat projection, and explicit fixture-first handling of the reset rotation baseline mismatch
- PR4
  - status: done
  - goal: move action decoding, visibility ordering, and tick skeleton ownership into native code
  - dependency: PR3
  - note: completed with packed action decoding, visible-target ordering, target-index resolution, rejection control semantics, and a direct-test-only step skeleton while keeping native_preview out of the standard vecenv/train path
- PR5a
  - status: done
  - goal: move core combat, wave progression, and terminal ownership into the native runtime
  - dependency: PR4
  - note: completed with native generic combat/wave/terminal handling plus direct-test core-trace parity hooks, while keeping Jad/healer/Tz-Kek special mechanics out of scope
- PR5b
  - status: done
  - goal: move the highest-risk special mechanics parity into the native runtime
  - dependency: PR5a
  - note: completed with frozen special-trace parity coverage and direct-test-only snapshot plumbing, without opening PR6 output work or PR7 cutover work
- PR6
  - status: done
  - goal: move flat observation and reward-feature emission into the native hot path
  - dependency: PR5b
  - note: completed as planned; native flat observations and reward-feature rows now match the frozen contracts without opening the standard train path during PR6
- PR7
  - status: done
  - goal: cut Python `v2_fast` integration over to the native backend without changing the trainer contract
  - dependency: PR6
  - note: completed with explicit native_preview routing through vecenv/train/eval integration while preserving v1_bridge fallback and avoiding trainer redesign
- PR8a
  - status: done
  - goal: validate performance and determinism against the explicit native gates without flipping defaults
  - dependency: PR7
  - note: completed; env-only and multi-worker aggregate gates cleared on the current host, but the recorded end-to-end training result was still on a CPU-only Torch build and did not represent the intended CUDA learner path
- PR8a-followup
  - status: done
  - goal: enable the intended CUDA learner path, validate device placement, and rerun the PR8a benchmark suite without flipping defaults
  - dependency: PR8a
  - note: completed; the learner path now resolves to CUDA, but the rerun still missed the explicit end-to-end training floor, so PR8b remains deferred
- PR8a.2
  - status: done
  - goal: normalize the learner benchmark to steady-state throughput and attempt to remove the remaining CUDA advantage-path fallback
  - dependency: PR8a-followup
  - note: completed; the retained train benchmark was heavily distorted by startup, eval, and checkpoint overhead, but the normalized steady-state learner result still missed the explicit training gate and the PufferLib CUDA advantage path remains unresolved
- ARCH1
  - status: done
  - goal: split the active native code into training-env and demo-env components and review local Ocean native envs as layout references only
  - dependency: DV1, PR8a.2
  - note: completed as architecture hygiene only; the shared-backend rule stayed intact, active paths now follow the component split, and the Ocean review informed structure guidance without adding dependencies
- PR8a.3
  - status: done
  - goal: decompose the remaining learner-stack bottlenecks on the intended CUDA path and determine what still blocks the training gate
  - dependency: ARCH1
  - note: completed; the real native CUDA path is effectively back at the old `~16k` end-to-end baseline, but PR8b remains blocked because the explicit gate is still dominated by learner-side scalar sync and cleanup overhead rather than env speed
- PR8a.4
  - status: done
  - goal: reduce learner-loop scalar synchronization and cleanup overhead on the real native CUDA training path without redesigning the trainer
  - dependency: PR8a.3
  - note: completed; the targeted sync buckets were cut sharply and train-only SPS improved substantially, but PR8b remains blocked because the end-to-end bottleneck has now concentrated in rollout-side host/device boundaries and tiny-batch orchestration
- PR8a.5
  - status: done
  - goal: reduce rollout-boundary host/device transfer, action materialization, and tiny-batch orchestration overhead on the real native CUDA path without redesigning the trainer
  - dependency: PR8a.4
  - note: completed; the rollout-boundary patch slightly improved the retained benchmark and confirmed with one explicit larger-update probe that train-only throughput can climb much higher, but PR8b remains blocked because end-to-end throughput is still bounded near the old baseline by rollout-side transfer/materialization costs
- DV1
  - status: done
  - goal: land the native C Raylib viewer foundation as a shared-backend debug and replay harness
  - dependency: PR7
  - note: completed with a separate viewer executable, backend-owned scene/snapshot export, replay/snapshot inspection, and headless smoke coverage without taking on viewer-side gameplay logic
- DV2
  - status: done
  - goal: add playable manual controls and a stronger debug HUD on top of the same backend-used simulation
  - dependency: DV1
  - note: completed with backend-routed move, attack, item, and prayer controls plus stronger HUD/state feedback, while keeping the viewer as a render/input shell only
- DV3
  - status: done
  - goal: add agent attach, replay control, and deeper inspector overlays to the shared-backend viewer
  - dependency: DV2
  - note: completed with backend-driven replay loading, scrub/step controls, replay-pack inspection, and deeper inspector overlays while keeping the viewer mechanically passive
- DV4
  - status: done
  - goal: land the first native-owned Fight Caves theme/presentation slice and break the generic debug-scene look
  - dependency: DV3
  - note: completed only as an initial native-owned theme/presentation stub; it improved readability, but it does not yet reach acceptable Fight Caves scene parity because it still lacks the real cache-derived terrain/object/interface presentation path
- ARCH-VIEW1
  - status: done
  - goal: identify the real legacy Fight Caves render and asset path and define the bounded native export/parity strategy
  - dependency: DV4
  - note: completed; the legacy-headed env is only a control wrapper, the real presentation source is the RSPS void-client plus cache and RSPS data tables, and DV4b has now landed as the first cache-derived scene-parity slice
- DV4b
  - status: done
  - goal: build the first real native-owned Fight Caves export bundle and scene-parity slice from legacy source material
  - dependency: ARCH-VIEW1
  - note: completed with a validated cache-derived terrain/HUD export bundle from `reference/legacy-headless-env/data/cache`; object-archive props and richer model renderables remain explicitly blocked rather than faked
- DV5
  - status: next
  - goal: tighten native HUD, inventory, camera, and interaction presentation toward Fight Caves parity without changing backend gameplay truth
  - dependency: DV4b
  - note: now follows the landed DV4b cache-derived scene/UI slice and owns the remaining viewer-side readability/manual-play parity work
- DV6
  - status: upcoming
  - goal: validate manual play, debugging workflow, and demo readiness on the shared-backend native viewer stack
  - dependency: DV5
  - note: intended to close the viewer-completion sequence before more training/debugging passes resume
- ARCH-PERF2
  - status: done
  - goal: determine what broader architecture change would actually be required to move meaningfully beyond the current `~16k` end-to-end baseline toward the explicit training gate
  - dependency: PR8a.5, DV3
  - note: completed; the structural ceiling is now diagnosed as the Python/PufferLib rollout boundary plus CPU-owned host/device transfer model, so any future throughput work must be broader architecture redesign rather than another local `PR8a.x` pass
- PR8b
  - status: deferred
  - goal: make the native backend the default fast path only if the learner-performance track clears the gates
  - dependency: explicit post-ARCH-PERF2 architecture decision
  - note: blocked; the current host remains near the old `~16k` end-to-end baseline on the current overall approach, so cutover is not approved unless the gate is relaxed or a broader architecture redesign succeeds

## PR Scope Discipline

- PR2 must not absorb PR3+ mechanics work.
- PR3 must not absorb PR5 mechanics parity work.
- PR4 must not absorb PR5 combat/mechanics work.
- PR5a must not absorb PR5b Jad, healer, or Tz-Kek special mechanics work.
- PR5a must not absorb PR6 output-hot-path work.
- PR7 must not become trainer redesign.
- PR8a must not silently absorb PR8b default-backend cutover work.
- PR8a-followup must not silently absorb PR8b default-backend cutover work.
- PR8a.2 must not silently absorb PR8b default-backend cutover work.
- ARCH1 must stay architecture hygiene and reference study only.
- PR8a.3 must stay focused on learner-path bottlenecks and must not silently absorb PR8b cutover work.
- PR8a.4 must stay focused on learner-loop desynchronization reduction and must not silently absorb PR8b cutover work or trainer redesign.
- PR8a.5 must stay focused on rollout-boundary and tiny-batch throughput reduction and must not silently absorb PR8b cutover work or trainer redesign.
- DV1 through DV3 must remain shared-backend viewer work; no viewer-only gameplay logic is allowed.
- DV1 must not absorb PR8a.3 learner optimization work.
- ARCH1 must not absorb PR8a.3 learner optimization work, DV2 manual controls, or gameplay redesign.
- DV4b must establish the native-owned export/parity slice and must not silently absorb DV5 HUD/camera parity work, TRAIN3 behavior work, or PR8b cutover work.
- DV5 must stay on HUD, inventory, camera, and interaction parity using the exported native-owned assets and must not fall back to runtime reads from `reference/legacy-*`.
- If an unblocker forces scope movement, record it explicitly in this file.

## Repo Boundary Note

- Active native work now lives entirely under `runescape-rl`.
- Legacy Java/Kotlin code now lives only under `reference/legacy-headless-env`, `reference/legacy-headed-env`, and `reference/legacy-rsps`.
- No active runtime dependency on legacy Java/Kotlin is allowed; those reference-only trees may be used only for logic study, oracle validation, fallback comparison, and asset sourcing.
- The viewer must remain a shared-backend harness for the native runtime, not a separate gameplay implementation.

## Performance-Track Pause

- PR8a.3 through PR8a.5 exhausted the narrow learner-loop and rollout-boundary optimization path on the current architecture for this benchmark host.
- The current architecture on this host remains near the old `~16k` end-to-end baseline even after those narrow passes.
- Future performance work, if resumed, must be framed as broader architecture investigation or redesign rather than another small local `PR8a.x` hot-path pass.
- PR8b stays deferred until that broader architecture question is resolved explicitly.
- The immediate implementation priority is no longer another performance step; it is viewer completion through the legacy-asset-derived path identified by ARCH-VIEW1.
- `docs/training_performance.md` is the active training-performance source of truth for current numbers, bottleneck interpretation, and history.
- `docs/agent_performance.md` is reserved for future agent-side performance tracking and is not the active training-performance source of truth.
- TRAIN3 and later behavior-focused training work are paused until the viewer-completion sequence reaches a minimum usable/readable/manual-debug bar.

## Viewer-Track Priority

- The immediate next viewer-side value is no longer deeper inspection alone; it is viewer completion to a minimum usable/readable/manual-debug standard before more training/debugging passes resume.
- DV3 completed on the shared-backend path with replay controls and backend-driven inspector overlays.
- DV4 is now complete only as an initial native-owned theme/presentation stub; it does not yet deliver acceptable Fight Caves scene parity.
- ARCH-VIEW1 completed the missing investigation step and established that the real visual source is the RSPS client-plus-cache path, not the headed Kotlin wrapper and not the current hand-authored theme bundle.
- DV4b is complete as the first real cache-derived scene-parity slice, and DV5 is now the next approved implementation step followed by DV6 before training/debugging passes resume.
- Viewer asset/presentation work remains separate from PR8b and does not imply default-backend cutover.

## Architecture-Performance Direction

- ARCH-PERF2 established that the native backend is no longer the primary blocker to end-to-end throughput on this host.
- The dominant remaining ceiling is the Python/PufferLib rollout contract and the CPU-owned host/device transfer model around `recv()`, Torch conversion/copies, `action.cpu().numpy()`, and subprocess transition/control-plane flow.
- No further narrow `PR8a.x` step is approved.
- If performance work resumes, it must be framed as broader rollout/learner-boundary redesign work rather than local hot-path cleanup.

## PR3 Completion Note

- Completed:
  - deterministic native episode config handling and reset/init scaffolding,
  - multi-slot reset output with terminal defaults, zero reward-feature rows, and reset-state flat observations,
  - direct native test APIs for reset validation without vecenv/train cutover.
- Intentionally not done:
  - no native step semantics,
  - no action execution surface,
  - no trainer or vecenv cutover,
  - no combat/mechanics work.
- Recorded reset-baseline mismatch decision:
  - the frozen PR1 reset fixture for `seed=11001,start_wave=1` disagrees with the locally observed retained Kotlin seeded rotation result,
  - PR3 treated the frozen artifact as canonical and recorded the mismatch explicitly instead of silently re-baselining.

## PR4 Completion Note

- Completed:
  - native ownership of the seven packed action types and action decoding,
  - visible-target ordering and target-index resolution on the native side,
  - explicit rejection semantics for invalid targets, busy states, lockouts, missing consumables, and no-op movement,
  - a per-step tick/control skeleton with terminal plumbing only as needed for PR4 validation.
- Intentionally not done:
  - no full combat progression,
  - no broader Fight Caves mechanics parity work,
  - no reward-feature parity implementation,
  - no flat-observation parity work beyond what PR4 tests needed,
  - no trainer or vecenv/native cutover.
- Scope boundary held:
  - PR4 kept `native_preview` on the direct-test-only path and did not absorb PR5 mechanics work.

## PR5 Split Note

- PR5 was split into PR5a and PR5b to reduce parity risk and avoid scope drift in the highest-risk mechanics phase.
- PR5a owns the core combat, wave progression, episode progression, and terminal surface needed to make the native step loop substantively real.
- PR5b owns the remaining special-mechanics parity work: Jad telegraph state, Jad prayer-resolution timing, healer behavior, and Tz-Kek split behavior.

## PR5a Completion Note

- Completed:
  - native core combat resolution, wave progression, NPC lifecycle handling, episode progression, and terminal-state emission for death, completion, invalid state, and tick cap,
  - retained direct-test-only parity coverage for the frozen single-wave and full-run core traces,
  - continued enforcement that `native_preview` is not train-runnable through the standard vecenv path.
- Intentionally not done:
  - no Jad telegraph work,
  - no Jad prayer-resolution timing work,
  - no healer behavior work,
  - no Tz-Kek split behavior work,
  - no PR6 reward-feature or broad flat-observation parity work,
  - no trainer or vecenv/native cutover.
- Scope boundary held:
  - PR5a used frozen core-trace fixtures for single-wave/full-run acceptance and kept the special-mechanics parity surface deferred to PR5b instead of silently absorbing it.

## PR5b Completion Note

- Completed:
  - native special-mechanics parity coverage for the frozen Jad telegraph, Jad prayer-protected, Jad prayer-too-late, healer, and Tz-Kek split artifacts,
  - direct-test-only native snapshot plumbing so reset-state special traces can be validated without opening the standard vecenv/train path,
  - continued enforcement that `native_preview` remains outside the standard Python env factory cutover path.
- Intentionally not done:
  - no PR6 flat-observation hot-path work,
  - no PR6 reward-feature emission work,
  - no Python/native cutover,
  - no trainer or broader vecenv integration changes.
- Scope boundary held:
  - PR5b extended the trace-backed oracle validation surface only as far as needed to match the frozen special-mechanics artifacts and did not absorb PR6 output work or PR7 integration work.

## PR6 Completion Note

- Completed:
  - native flat-observation writing and reward-feature emission on the training-facing hot path,
  - contract-level output parity checks against the frozen flat-observation and reward-feature artifacts,
  - continued enforcement of append-only output schema/version guarantees.
- Intentionally not done:
  - no Python/native vecenv cutover,
  - no trainer changes,
  - no broader factory integration work,
  - no performance validation or default-backend cutover.
- Scope boundary held:
  - PR6 stayed local to native outputs and direct-test validation; `native_preview` remained outside the standard train path during PR6 and no PR7 integration work was absorbed.

## PR7 Completion Note

- Completed:
  - Python `v2_fast`-surface integration to the native backend through embedded vecenv, subprocess vecenv, and single-env eval wiring,
  - native backend selection through `env_backend='native_preview'` while preserving the existing fast policy encoding and Python-side reward weighting behavior,
  - smoke coverage for native vecenv, subprocess, train, and eval flows alongside retained `v1_bridge` fallback coverage.
- Intentionally not done:
  - no trainer redesign,
  - no PufferLib rewrite,
  - no default-backend cutover,
  - no performance validation beyond basic smoke viability.
- Scope boundary held:
  - PR7 kept the integration change local to backend routing and compatibility wrappers; it did not absorb PR8 performance/default-cutover work or broader trainer/vecenv redesign.

## PR8a Completion Note

- Completed:
  - native env-only, end-to-end training, and multi-worker aggregate benchmarks were run through the retained benchmark harnesses,
  - native long-run stability was checked through the retained long-run validator,
  - native eval determinism was checked through repeated same-checkpoint replay-eval runs.
- Outcome:
  - env-only throughput cleared the explicit floor,
  - multi-worker aggregate throughput cleared the explicit floor,
  - end-to-end training throughput did not clear the explicit training floor or stretch target on the current benchmark host,
  - the recorded PR8a training measurement still used a CPU-only Torch build and therefore did not represent the intended CUDA learner path,
  - determinism and stability checks passed for the exercised PR8a paths.
- Scope boundary held:
  - PR8a did not flip the default backend, remove fallbacks, or absorb trainer redesign work.

## PR8a-followup Completion Note

- Completed:
  - the RL project dependency config and lockfile now resolve a CUDA-capable Torch build on this host,
  - the learner path was validated directly: `train.device` resolves to CUDA, policy weights land on `cuda:0`, rollout buffers land on `cuda:0`, and the native subprocess training path runs on the intended CUDA path,
  - the retained PR8a env, train, and long-run validation commands were rerun on the intended CUDA path.
- Outcome:
  - env-only throughput still clears the explicit floor,
  - multi-worker aggregate throughput still clears the explicit floor,
  - end-to-end training throughput still misses the explicit training floor by a wide margin even on the intended CUDA path,
  - the remaining confirmed learner-path bottleneck is the PufferLib advantage path falling back through CPU because `nvcc` is not installed, while Adam update state stays on CUDA except for scalar `step` counters on CPU.
- Scope boundary held:
  - PR8a-followup did not flip the default backend, remove fallback behavior, or absorb trainer redesign work.

## PR8a.2 Completion Note

- Completed:
  - inspected the installed PufferLib learner path directly and confirmed how `ADVANTAGE_CUDA` is gated in this repo environment,
  - attempted repo-local CUDA tooling enablement for the advantage path and verified that the installed `compute_puff_advantage` operator remains CPU-only on this host,
  - added a normalized steady-state train benchmark with warmup and separate startup reporting so learner throughput can be measured without checkpoint/eval distortion,
  - reran both the retained PR8a train benchmark shape and the normalized steady-state learner benchmark on the intended CUDA training path.
- Outcome:
  - the retained PR8a train benchmark result was mostly a benchmark-shape artifact dominated by startup, eval, and checkpoint overhead,
  - the normalized steady-state learner result is much higher than the retained PR8a train result but still remains well below the explicit `250,000` SPS training floor,
  - the remaining missing CUDA learner-path piece is still the PufferLib advantage kernel path; repo-local package installation did not provide a real `nvcc` binary and the installed operator still cannot execute on CUDA,
  - a bounded TF32 fast-path check did not improve the normalized learner result and was not adopted as a standing change.
- Scope boundary held:
  - PR8a.2 did not flip the default backend, did not redesign the trainer, and did not rewrite PufferLib.

## PR8a.3 Completion Note

- Completed:
  - reran the retained and normalized benchmarks on the real subprocess native CUDA path with deeper learner-side instrumentation,
  - decomposed the major learner buckets and added explicit breakdown for rollout wait/write, loss metric scalar extraction, train cleanup, and known synchronization sources,
  - verified on the exercised CUDA path that policy parameters, rollout buffers, and optimizer state remain on CUDA except for the existing scalar `step` counters,
  - sampled GPU utilization during the normalized steady-state run to distinguish env limits from learner-path orchestration limits.
- Outcome:
  - the normalized steady-state result is effectively at the old `~16k` end-to-end baseline (`15.4k` end-to-end SPS, `24.5k` train-only SPS), so the remaining gap versus the old baseline is no longer the primary issue,
  - the retained PR8a benchmark shape still reports only `~388` SPS because startup, eval, and checkpoint overhead dominate that retained measurement,
  - the current dominant bottlenecks are learner-side scalar synchronization and cleanup work: `train_done_cleanup` is almost entirely explained-variance computation, `train_loss_compute` is dominated by repeated `.item()` metric extraction, and rollout write cost is heavily dominated by GPU scalar index extraction,
  - the missing PufferLib CUDA advantage path is still real (`ADVANTAGE_CUDA=false`, no system `nvcc`), but it is not a primary blocker on this host because its measured cost is tiny relative to the larger synchronization and cleanup buckets,
  - GPU utilization stays in the single digits during the normalized run, which is consistent with the current tiny batch/update shape (`64` envs, batch size `256`, one minibatch per update) and CPU-orchestrated scalar sync rather than an env-speed bottleneck.
- Scope boundary held:
  - PR8a.3 stayed on learner-path decomposition only, did not flip defaults, did not redesign the trainer, and did not absorb DV2 or PR8b work.

## PR8a.4 Completion Note

- Completed:
  - added narrow trainer-hot-path reductions for the specific sync points identified in PR8a.3,
  - removed repeated `.item()` loss-metric extraction from the logging-disabled benchmark path,
  - removed rollout-side GPU scalar index extraction by maintaining host mirrors for rollout indices and lengths,
  - stopped paying explained-variance cleanup cost on the logging-disabled hot path where that value is not consumed,
  - reran the retained and normalized benchmark shapes on the real subprocess native CUDA path for an apples-to-apples comparison.
- Outcome:
  - normalized train-only SPS improved from `24.5k` to `71.4k`,
  - the targeted learner-side sync buckets were reduced sharply: `train_loss_metric_items` to `0`, `train_done_cleanup_explained_variance` to `0`, and `eval_rollout_index_extract` from a major stall to near-zero,
  - retained disabled benchmark SPS improved slightly from `388.0` to `393.9`,
  - end-to-end normalized SPS did not improve and remained far below the explicit gate because visible wait shifted into rollout-side host/device boundaries and the current tiny update shape still underutilizes the GPU.
- Scope boundary held:
  - PR8a.4 stayed local to learner-loop desynchronization reduction only, did not redesign the trainer, did not touch env mechanics, and did not absorb DV2 or PR8b work.

## PR8a.5 Completion Note

- Completed:
  - added narrow rollout-boundary staging changes on the trainer hot path to avoid repeated per-iteration transfer/materialization allocation work,
  - reran the retained and normalized benchmark shapes on the real subprocess native CUDA path for an apples-to-apples comparison,
  - ran one explicit larger-update probe (`128` envs, batch `512`) to test whether tiny updates were still materially limiting GPU use without turning the pass into a sweep.
- Outcome:
  - retained disabled benchmark SPS improved slightly from `393.9` to `394.1`,
  - normalized train-only SPS stayed roughly flat relative to PR8a.4 (`71.4k` to `70.3k`) and normalized end-to-end SPS stayed near the old baseline (`14.4k` to `14.2k`),
  - the intended rollout-boundary bottlenecks did not improve materially on the benchmark shape: host-device transfer and action materialization still dominate the end-to-end path,
  - the explicit larger-update probe raised train-only SPS sharply to `169k`, but end-to-end SPS only reached `16.3k`, confirming that rollout-side host/device transfer and action materialization remain the dominant ceiling even after the learner-loop sync fixes.
- Scope boundary held:
  - PR8a.5 stayed local to rollout-boundary and tiny-batch throughput reduction only, did not redesign the trainer, did not touch env mechanics, and did not absorb DV2 or PR8b work.

## DV1 Completion Note

- Completed:
  - a native C Raylib viewer executable now builds against the same native runtime library used by training,
  - the backend now exposes viewer-facing slot snapshots and backend-owned scene metadata for the viewer to render directly,
  - the viewer renders native runtime state, supports backend trace/reset snapshot export, and supports pause plus single-step playback through backend wait actions,
  - headless smoke coverage now validates viewer build plus snapshot/replay export without requiring a live window in tests.
- Intentionally not done:
  - no manual click-to-move, click-to-attack, or item/prayer input controls,
  - no live trainer attach,
  - no reward/action overlays,
  - no headed-demo replacement,
  - no viewer-owned gameplay logic.
- Scope boundary held:
  - DV1 stayed on shared-backend state and did not absorb PR8a.3 learner-performance work or DV2 manual-control work.

## DV2 Completion Note

- Completed:
  - backend-routed manual controls for move, attack, item use, prayer toggles, and run toggles in the native viewer,
  - a stronger debug HUD with HP, prayer, inventory, wave, tick, selected target, hovered entity, last-action feedback, and terminal state visibility,
  - headless scripted-action viewer smoke coverage so backend-routed manual actions can be validated without a live window.
- Intentionally not done:
  - no live agent attach,
  - no replay overlays or richer inspector overlays,
  - no viewer-owned gameplay logic,
  - no PR8b cutover work,
  - no renewed learner-performance work.
- Scope boundary held:
  - DV2 kept the viewer on the same backend truth used by training and did not absorb DV3 overlay/attach work or restart the paused performance track.

## DV3 Completion Note

- Completed:
  - backend-driven replay loading in the native viewer from exported native viewer replay files,
  - replay scrub, play/pause, frame-step, home/end, and reload controls without adding mechanics to the viewer,
  - deeper inspector overlays for per-entity state, visible-index context, Jad telegraph/debug state, and backend step/terminal feedback,
  - replay-pack inspection support so exported agent/eval trajectories can be visualized through the same backend-facing viewer path.
- Intentionally not done:
  - no PR8b cutover work,
  - no renewed learner-performance work,
  - no trainer redesign,
  - no viewer-owned gameplay logic,
  - no broader asset or presentation polish step.
- Scope boundary held:
  - DV3 stayed mechanically passive, did not create a second gameplay path, did not add runtime dependency on `reference/legacy-*`, and did not restart the paused performance track.

## DV4 Completion Note

- Completed:
  - a native-owned viewer theme bundle now exists under the active `demo-env` tree and is baked into the viewer build as generated data rather than being read from `reference/legacy-*` at runtime,
  - a minimal native-owned header/banner image now ships with the viewer build output,
  - the viewer scene now renders Fight Caves-specific lava, basalt shell, dais treatment, spawn-pad treatment, and scene props instead of the earlier generic debug-grid presentation,
  - player and NPC rendering now uses more Fight Caves-specific silhouettes and color treatment while still consuming backend snapshots only,
  - headless viewer export now reports the active theme bundle so the asset pipeline is covered by smoke tests.
- Intentionally not done:
  - no HUD/inventory parity polish beyond what was needed to support the new scene presentation,
  - no broad manual-control redesign,
  - no live trainer attach or replay-overlay expansion,
  - no viewer-owned gameplay logic,
  - no PR8b cutover or renewed learner-performance work.
- Limitation recorded explicitly:
  - DV4 delivered only the first native-owned theme/presentation stub,
  - it did not reconstruct the real Fight Caves terrain/object/interface path,
  - it is not yet acceptable scene parity for the required debug/demo/manual-play bar.

## ARCH-VIEW1 Completion Note

- Completed:
  - established that `reference/legacy-headed-env` is only a control/debug shell and not the real Fight Caves renderer,
  - established that the real legacy presentation source is `reference/legacy-rsps/void-client` plus synced client-cache data and RSPS Fight Caves/TzHaar semantic tables,
  - identified the exact Fight Caves, TzHaar city, inventory, interface, NPC, animation, and gfx metadata files that define the semantic source set for export,
  - identified that the current checkout does not actually contain the full cache-derived visual assets because `reference/legacy-rsps/data/cache` is empty,
  - re-scoped the viewer track around a bounded native-owned export pipeline rather than more hand-authored scene dressing.
- Outcome:
  - DV4 is reclassified as an initial presentation stub rather than acceptable scene parity,
  - training/debugging passes remain paused,
  - DV4b became the next implementation step and is now complete.
- Scope boundary held:
  - DV4 stayed on native-owned presentation and asset-pipeline work only, did not create runtime dependency on `reference/legacy-*`, and did not resume TRAIN3 or the paused performance track.

## DV4b Completion Note

- Completed:
  - validated `reference/legacy-headless-env/data/cache` as the active usable client-cache input,
  - confirmed `reference/legacy-rsps/data/cache` is empty in this checkout and is not the viewer export source,
  - built the offline export pipeline into `runescape-rl/src/demo-env/assets/generated`,
  - exported a real native-owned Fight Caves scene slice with cache-derived terrain coverage for `m37_79` and `m37_80`,
  - exported real cache-derived HUD/interface sprite subsets for prayer icons, panel backgrounds, inventory slot art, click crosses, and wave digits,
  - integrated the generated bundle into the native viewer build so runtime loads only native-owned generated outputs.
- Intentionally not done:
  - no DV5 HUD/camera/manual-interaction parity work,
  - no TRAIN3 behavior work,
  - no PR8b work,
  - no runtime dependency on `reference/legacy-*`.
- Explicit remaining blockers:
  - object archives `l37_79` and `l37_80` are still unreadable without Fight Caves region XTEAs,
  - richer item-icon and player/NPC model renderables still need a committed offline model-render export path.
- Outcome:
  - DV4b materially clears the first scene-parity slice bar: the viewer now reads as Fight Caves because of real cache-derived terrain and HUD assets rather than a hand-authored theme stub,
  - but full viewer-completion parity remains DV5 and DV6 work.

## ARCH-PERF2 Completion Note

- Completed:
  - reviewed the active native training path, subprocess/embedded vecenv boundaries, and the PufferLib trainer/runtime contract as a system rather than another hot-path tweak target,
  - reviewed local Ocean native env references and the current PufferLib rollout path for native/Python boundary patterns,
  - documented the current architectural ceiling and the credible future option set in `docs/plan.md` and `docs/training_performance.md`.
- Outcome:
  - the native env is already fast enough that it is no longer the primary end-to-end blocker,
  - the current Python/PufferLib rollout contract plus CPU-owned action/observation materialization is the structural limiter on this host,
  - the explicit `250,000` SPS target does not look realistic on the current overall approach without broader architecture redesign,
  - PR8b remains blocked.
- Scope boundary held:
  - ARCH-PERF2 did not restart narrow optimization work, did not do PR8b cutover, and did not redesign gameplay, trainer behavior, or the viewer track.

## ARCH1 Completion Note

- Completed:
  - the active native codebase is now split into `runescape-rl/src/training-env` and `runescape-rl/src/demo-env`,
  - active build helpers, smoke tests, and docs now point at the split component layout,
  - the shared-backend rule remains intact: the viewer shell still links against and renders the same native backend used by training,
  - local PufferLib Ocean envs were reviewed as implementation references only.
- Intentionally not done:
  - no gameplay redesign,
  - no learner optimization,
  - no DV2 manual controls,
  - no PR8b cutover work.
- Scope boundary held:
  - ARCH1 stayed at repo structure, build hygiene, and reference study only.

## Ownership and non-goals

The initial rewrite ownership surface is:

- native headless training runtime,
- native debug/playable viewer-demo harness,
- RL and PufferLib connectivity and training code.

The following are explicitly not first-class rewrite targets in the early phases:

- legacy headless-env,
- legacy headed-env,
- RSPS,
- PufferLib internals,
- trainer redesign,
- action-space redesign,
- broad RL redesign.

## PR1: Contract Freeze And Golden Fixtures

Purpose:
Freeze the behavior and buffer contracts the native runtime must match.

Exact scope:

- Finalize the authoritative action, flat-observation, reward-feature, terminal-code, and episode-start contracts.
- Add or refresh deterministic golden traces and reset fixtures sourced from the current legacy runtime.
- Lock append-only schema/versioning rules into tests and contract registries.
- Make the Python-side tests point at the new root planning docs and contract fixtures, not legacy phase docs.

Out of scope:

- Any native simulator implementation.
- Trainer changes beyond fixture/test wiring.

Acceptance criteria:

- Contract descriptors are explicit and versioned.
- Golden traces exist for reset, single-wave, full-run, Jad prayer, healer, and Tz-Kek split scenarios.
- Existing parity tests can consume the frozen fixtures.
- The parity model is explicit: native-vs-legacy comparison, not shared live runtime logic.

Validation and parity expectations:

- Same seed produces identical legacy trace fixtures on repeated generation.
- Fixture generation clearly identifies legacy runtime version/source.

Dependency ordering:

- Must land before any native runtime PR.

## PR2: Native Build Scaffold And Python Load Path

Purpose:
Introduce a native build target and a Python-loadable surface without taking ownership of mechanics yet.

Exact scope:

- Add the native project skeleton.
- Expose a descriptor API for schema ids, versions, counts, and action metadata.
- Add Python runtime discovery/loading for the native backend behind an explicit feature flag or backend selection.

Out of scope:

- Real Fight Caves stepping logic.
- Trainer optimization or cutoff from the JVM fast path.

Acceptance criteria:

- The native library builds locally.
- Python can import/load it.
- Descriptor output matches the frozen contracts from PR1.
- No trainer rewrite or PufferLib rewrite is introduced.

Validation and parity expectations:

- Smoke test proves Python can query native descriptors and close the runtime cleanly.

Dependency ordering:

- Depends on PR1.

## PR3: Native Reset Path And Deterministic Episode Scaffold

Purpose:
Own deterministic episode initialization natively.

Exact scope:

- Implement native episode config handling.
- Implement seed handling, start-wave selection, fixed stats, starting loadout, consumables, and player spawn.
- Implement batched reset output with terminal defaults, reward-feature zeros, and flat observation rows for reset state.

Out of scope:

- Real combat progression.
- Headed demo or replay tooling.

Acceptance criteria:

- Native reset output matches legacy reset semantics for seed, wave, loadout, consumables, and observation layout.
- Batch reset returns correctly shaped buffers for multiple envs.

Validation and parity expectations:

- Reset parity tests against legacy headless runtime pass.
- Same seed and reset options produce identical native reset outputs.

Dependency ordering:

- Depends on PR2.

## PR4: Native Action Contract, Visibility Ordering, And Tick Skeleton

Purpose:
Own the training-facing control surface natively before full combat parity.

Exact scope:

- Implement the seven action types and packed action decoding.
- Implement visible-target ordering and target-index resolution.
- Implement rejection codes for invalid targets, busy states, lockouts, missing consumables, and no-op movement.
- Implement per-step tick advancement skeleton and terminal plumbing.

Out of scope:

- Full Fight Caves combat parity.
- Reward shaping beyond contract emission.

Acceptance criteria:

- Native actions accept and reject inputs exactly according to the shared schema.
- Stable visible-target ordering is preserved across reset/step scenarios covered by fixtures.

Validation and parity expectations:

- Native-vs-legacy action rejection tests pass.
- Deterministic target-index mapping tests pass.

Dependency ordering:

- Depends on PR3.

## PR5a: Native Core Combat, Wave Progression, And Terminal Parity

Purpose:
Move the native runtime from a control skeleton to a real core episode loop.

Exact scope:

- Implement wave progression, NPC lifecycle, combat resolution, and episode progression.
- Implement terminal-state emission for death, completion, invalid state, and tick cap.
- Add parity checks for single-wave and full-run core flow where the frozen artifacts are authoritative for PR5a.

Out of scope:

- Jad telegraph state and prayer-resolution behavior.
- Healer behavior.
- Tz-Kek split behavior.
- Reward-feature parity work.
- Broad flat-observation parity work beyond what PR5a tests strictly need.
- Python trainer changes beyond test hooks.
- Headed demo path replacement.

Acceptance criteria:

- Native core-flow behavior covers wave progression, NPC lifecycle, combat resolution, and terminal emission in native code.
- Native step traces match the frozen single-wave/full-run core-flow expectations used for PR5a.
- Terminal codes and episode counters match the frozen contract.
- Native-vs-legacy parity tests are the acceptance mechanism; legacy runtimes remain oracle infrastructure only.

Validation and parity expectations:

- Single-wave and full-run core-flow parity checks pass.
- Death, completion, invalid-state, and tick-cap terminal checks pass.

Dependency ordering:

- Depends on PR4.

## PR5b: Native Special Mechanics Parity

Purpose:
Land the remaining high-risk Fight Caves mechanics that are intentionally deferred out of PR5a.

Exact scope:

- Implement Jad telegraph state.
- Implement Jad prayer-resolution timing.
- Implement healer behavior.
- Implement Tz-Kek split behavior.

Out of scope:

- Trainer redesign or vecenv/native cutover.
- PR6 reward-feature or full output-hot-path work.

Acceptance criteria:

- Native traces match the frozen Jad telegraph, Jad prayer, healer, and Tz-Kek parity artifacts.
- Special-mechanics parity is proven through native-vs-legacy comparison rather than shared live logic.

Validation and parity expectations:

- Jad telegraph, Jad prayer, healer, and Tz-Kek scenario parity checks pass.

Dependency ordering:

- Depends on PR5a.

## PR6: Native Flat Observation And Reward-Feature Emission

Purpose:
Finish the hot-path native output surface used by training.

Exact scope:

- Implement native flat observation writing.
- Implement native reward-feature emission.
- Preserve no-future-leakage defaults.
- Optionally keep structured observation generation in validation mode only if needed.

Out of scope:

- Trainer redesign.
- Logging/report format changes.

Acceptance criteria:

- Flat observation schema id, counts, ordering, and values match the frozen contract.
- Reward-feature vectors match the frozen contract and legacy semantics.
- Append-only schema/versioning guarantees remain enforced.

Validation and parity expectations:

- Native flat observations match legacy flat projections on the fixture set.
- Native reward features match legacy reward-feature rows on the fixture set.

Dependency ordering:

- Depends on PR5b.

## PR7: Python `v2_fast` Integration Cutover

Purpose:
Replace the JPype/JVM fast backend with the native backend while keeping the trainer contract stable.

Exact scope:

- Wire the Python fast vecenv to the native backend.
- Preserve existing `v2_fast` config semantics.
- Keep `v1_bridge` fallback intact.
- Keep subprocess worker support working against the native backend.

Out of scope:

- Rewriting the trainer loop.
- Removing legacy JVM parity tooling.

Acceptance criteria:

- Existing smoke train, eval, vecenv, and subprocess flows run against the native backend.
- Python-side reward weighting and policy encoding continue to work unchanged.

Validation and parity expectations:

- `v2_fast` smoke tests pass with the native backend selected.
- Native backend matches legacy parity fixtures before default cutover.

Dependency ordering:

- Depends on PR6.

## PR8a: Performance Validation And Determinism

Purpose:
Measure the native backend honestly against the explicit gates before any default flip is considered.

Exact scope:

- Run env-only, train-loop, and multi-worker performance validation.
- Run long-run determinism and stability checks.
- Report measured results against the explicit gates.

Out of scope:

- Default-backend cutover.
- Fallback removal.
- Trainer redesign.
- PufferLib rewrite.
- Broad architecture changes.

Acceptance criteria:

- Native backend clears all explicit gates:
  - env-only floor: `200,000` SPS,
  - end-to-end training floor: `250,000` SPS,
  - end-to-end training stretch target: `500,000` SPS,
  - multi-worker aggregate floor: `300,000` SPS.
- Long-run determinism and stability checks pass.

Validation and parity expectations:

- Native performance is measured against the same benchmark harness shape used for the legacy fast path.
- No parity regressions appear during long-run validation.
- Env-only, end-to-end training, and multi-worker SPS are reported separately.

Dependency ordering:

- Depends on PR7.

## PR8a-followup: GPU Enablement And Learner-Path Validation

Purpose:
Validate the intended CUDA learner path honestly before any default cutover is reconsidered.

Exact scope:

- Verify that the RL project resolves a CUDA-capable Torch install on the benchmark host.
- Verify that `train.device` resolves to CUDA in the exercised native training path.
- Verify that policy weights, rollout buffers, and the update path land on CUDA where intended.
- Verify whether PufferLib accelerated kernels are available on the benchmark host.
- Rerun the retained PR8a env, train, and long-run validation commands on the intended CUDA path.
- Report concrete learner-path bottlenecks if the end-to-end training gate still fails.

Out of scope:

- Default-backend cutover.
- Fallback removal.
- Trainer redesign.
- PufferLib rewrite.
- Broad architecture changes.

Acceptance criteria:

- CUDA-capable Torch is installed and reported through the repo training environment.
- The exercised native training path resolves `train.device` to CUDA and keeps the model on CUDA.
- The retained PR8a validation commands are rerun on the intended CUDA path.
- Any remaining gate failure is reported honestly with concrete bottleneck evidence.

Validation and parity expectations:

- This step does not redefine the benchmarks to manufacture a passing result.
- Device-placement checks are run on the same native subprocess training path used by the benchmark harness.
- PR8b remains blocked unless the explicit training gate is cleared on the intended CUDA path.

Dependency ordering:

- Depends on PR8a.

## PR8a.2: Learner-Path Optimization And Benchmark Normalization

Purpose:
Normalize the learner benchmark to steady-state behavior and determine whether the remaining training-gap is primarily setup distortion, missing CUDA learner kernels, or a real residual bottleneck.

Exact scope:

- Verify exactly how PufferLib enables its CUDA advantage path in this repo environment.
- Install or configure any missing repo-local CUDA tooling that could enable the accelerated advantage path on this host, if feasible.
- Rebuild or reinstall the minimum required pieces to determine whether the accelerated advantage path can actually run on CUDA.
- Add or adapt a benchmark mode that includes warmup, excludes startup, checkpoint, and eval overhead from the measured training window, and reports startup separately from steady-state learner throughput.
- Report learner-path time buckets for rollout collection, advantage/GAE, policy forward, loss compute, backward, optimizer step, and major host-to-device or device-to-host transfers.
- Run bounded PyTorch fast-path checks only where they can be evaluated honestly without redesigning the trainer.

Out of scope:

- Default-backend cutover.
- Trainer redesign.
- PufferLib rewrite.
- Broad hyperparameter sweeps.
- Architecture changes.

Acceptance criteria:

- The repo reports before-and-after learner-path findings for `ADVANTAGE_CUDA`, `nvcc` availability, and actual CUDA placement of the exercised learner path.
- The retained PR8a train benchmark result and the normalized steady-state learner result are both recorded honestly.
- Any remaining gate failure is classified explicitly as benchmark-shape artifact, missing CUDA learner kernel support, remaining real bottleneck, or some combination of those factors.
- PR8b remains blocked unless the explicit training gate is actually cleared.

Validation and parity expectations:

- The normalized benchmark must use the existing training path rather than a synthetic learner microbenchmark.
- Startup and steady-state measurements must be reported separately rather than conflated.
- Bounded fast-path checks must be reported explicitly and must not be left enabled if they do not help.

Dependency ordering:

- Depends on PR8a-followup.

## ARCH1: Component Split And Native Reference-Env Review

Purpose:
Reshape the active native codebase into explicit backend and viewer components, and review local Ocean native envs as implementation references only.

Exact scope:

- Split the active native code under `runescape-rl/src` into `training-env`, `demo-env`, and `rl`.
- Keep the backend as the single gameplay truth and the viewer as rendering/input/debugging on top of that same backend.
- Patch active build helpers, smoke tests, and docs to follow the new component layout.
- Review local PufferLib Ocean native envs for useful structure, build, and boundary patterns.

Out of scope:

- Gameplay or mechanics redesign.
- Learner optimization.
- DV2 manual controls.
- PR8b default cutover.

Acceptance criteria:

- Active native code no longer lives under a generic `native-env` bucket.
- `training-env` owns the backend and native-owned active data.
- `demo-env` owns the viewer shell and still links against the same backend library used by training.
- The Ocean review is recorded only as reference guidance, not as a new dependency.

Validation and parity expectations:

- Targeted boundary and smoke tests pass after the split.
- No viewer-only gameplay logic is introduced.

Dependency ordering:

- Depends on DV1 and PR8a.2.

## PR8a.3: Learner-Stack Bottleneck Decomposition

Purpose:
Identify the remaining learner-path bottlenecks that still block the explicit training gate on the intended CUDA path.

Exact scope:

- Keep the retained and normalized benchmark shapes honest and comparable.
- Decompose the remaining learner-path cost centers with concrete evidence from the exercised CUDA training path.
- Distinguish benchmark-shape effects, missing CUDA learner kernels, host-device transfer costs, optimizer/update costs, and other residual bottlenecks.
- Record the smallest defensible follow-up changes needed to close the gate if it is still missed.

Out of scope:

- Default-backend cutover.
- Trainer redesign.
- PufferLib rewrite.
- Broad architecture changes.

Acceptance criteria:

- The remaining learner bottlenecks are recorded with concrete evidence from the exercised native training path.
- The plan is explicit about what still blocks the `250,000` SPS training floor.
- PR8b remains blocked unless the explicit training gate is actually cleared.

Validation and parity expectations:

- The learner-path analysis stays on the real native training path, not a synthetic microbenchmark.
- Any benchmark-shape change is recorded explicitly.

Dependency ordering:

- Depends on ARCH1.

## PR8a.4: Learner-Loop Desynchronization Reduction

Purpose:
Reduce the specific learner-loop synchronization and cleanup costs that PR8a.3 proved were dominating the current native CUDA training path.

Exact scope:

- Keep the retained and normalized benchmark shapes unchanged so results remain comparable to PR8a.3.
- Reduce repeated `.item()` metric extraction on the logging-disabled hot path.
- Reduce rollout-side GPU scalar index extraction where the current trainer loop only needs host scalars.
- Reduce or skip explained-variance and related cleanup work on the logging-disabled hot path when those values are not consumed.
- Keep changes narrow and local to the learner/trainer hot path.

Out of scope:

- Default-backend cutover.
- Trainer redesign.
- PufferLib rewrite.
- Env or gameplay changes.
- Broad hyperparameter changes.

Acceptance criteria:

- The targeted learner-side synchronization buckets are reduced measurably on the real native CUDA path.
- Retained and normalized benchmark shapes remain directly comparable with PR8a.3.
- PR8b remains blocked unless the explicit end-to-end training gate is actually cleared.

Validation and parity expectations:

- Validation stays on the exercised native subprocess CUDA training path.
- Any observed improvement or regression is reported honestly even if the gate is still missed.

Dependency ordering:

- Depends on PR8a.3.

## PR8a.5: Rollout Boundary And Tiny-Batch Throughput Reduction

Purpose:
Reduce the rollout-side host/device transfer, action materialization, and tiny-update orchestration costs that remained dominant after PR8a.4.

Exact scope:

- Keep the retained and normalized benchmark shapes unchanged so results remain comparable to PR8a.4.
- Reduce rollout/eval boundary overhead where that can be done locally in the trainer hot path.
- Reduce action materialization and CPU conversion overhead where that can be done locally in the trainer hot path.
- Evaluate one minimal, explicit larger-update probe to test whether tiny update shape is still a meaningful GPU-utilization limiter without turning the pass into a broad sweep.

Out of scope:

- Default-backend cutover.
- Trainer redesign.
- PufferLib rewrite.
- Env or gameplay changes.
- Broad hyperparameter changes.

Acceptance criteria:

- The rollout-boundary bottlenecks are remeasured honestly on the real native CUDA path after the narrow local changes.
- The retained and normalized benchmark shapes remain directly comparable with PR8a.4.
- PR8b remains blocked unless the explicit end-to-end gate is actually cleared.

Validation and parity expectations:

- Validation stays on the exercised native subprocess CUDA training path.
- Any explicit larger-update probe is recorded as a probe, not silently substituted for the retained benchmark shape.

Dependency ordering:

- Depends on PR8a.4.

## DV1: Native Debug Viewer Foundation

Purpose:
Create the first native debug and replay viewer around the same backend used by training.

Exact scope:

- Add a native C viewer executable built with Raylib.
- Add viewer-facing backend exports for runtime snapshots and backend-owned scene metadata.
- Render arena bounds, backend scene props, player state, and visible NPC state directly from native runtime snapshots.
- Provide a debug HUD for HP, prayer, inventory, current wave, current tick, and hovered entity information when available.
- Support snapshot and replay rendering from native runtime state.
- Support pause and single-step viewing through backend wait actions.

Out of scope:

- Full manual controls.
- Click-to-move, click-to-attack, or item/prayer click input.
- Live trainer attach.
- Reward/action overlays.
- Headed-demo replacement.
- Viewer-owned gameplay logic.

Acceptance criteria:

- The viewer builds locally as a native executable.
- The viewer renders backend-owned scene metadata and runtime snapshots without implementing a separate gameplay path.
- Snapshot and replay export are testable headlessly.
- The viewer remains separate from the training hot path except through the shared backend interface.

Validation and parity expectations:

- The viewer consumes native backend state and scene metadata directly.
- Pause and single-step operate by sending actions into the backend, not by simulating mechanics inside the viewer.

Dependency ordering:

- Depends on PR7.

## DV2: Playable Manual Controls And HUD

Purpose:
Turn the native viewer foundation into a manual-debug harness for the same backend the agent trains on.

Exact scope:

- Add backend-routed manual movement, attack, item-use, and prayer-toggle controls.
- Strengthen the HUD around player state, targets, and debug context.
- Keep the viewer on the same shared backend truth.

Out of scope:

- Trainer redesign.
- Viewer-owned gameplay logic.
- Headed-demo replacement.

Acceptance criteria:

- Manual inputs route into backend actions instead of viewer-side mechanics.
- The viewer remains usable as a manual-debug shell around the training backend.

Validation and parity expectations:

- Any gameplay-relevant interaction visible in the viewer must exercise backend logic directly.

Dependency ordering:

- Depends on ARCH1.

## DV3: Agent Attach, Replay, And Inspector Overlays

Purpose:
Add deeper inspection and replay control to the shared-backend viewer once the manual-debug shell exists.

Exact scope:

- Attach agent or replay streams to the viewer.
- Add inspector overlays, replay controls, and deeper per-entity/state inspection.
- Preserve the viewer as presentation plus input around the same backend used elsewhere.

Out of scope:

- Default-backend cutover.
- Trainer redesign.
- Viewer-owned gameplay logic.

Acceptance criteria:

- Agent or replay attachment works without creating a separate gameplay path.
- Inspector overlays reflect backend state directly.

Validation and parity expectations:

- Overlay and replay features must remain backend-driven and mechanically passive.

Dependency ordering:

- Depends on DV2.

## DV4: Native Asset Pipeline And Arena Presentation Parity

Purpose:
Land the first native-owned Fight Caves theme/presentation slice and break the generic debug-scene look while keeping the shared-backend rule intact.

Exact scope:

- Create a minimal native-owned asset subset for the viewer sourced from the legacy reference trees.
- Bake that asset subset into the active `demo-env` build/output path so there is no runtime dependency on `reference/legacy-*`.
- Replace the generic debug-grid arena look with Fight Caves-specific lava, walls, props, floor treatment, and more readable player/NPC presentation.
- Use local Ocean references only for render/input-structure guidance, not as direct dependencies.

Out of scope:

- Broad HUD/inventory/camera parity polish.
- Broad manual-control redesign.
- Live trainer attach or replay-overlay expansion.
- Viewer-owned gameplay logic.
- PR8b cutover or renewed performance work.

Acceptance criteria:

- The viewer no longer reads as a generic debug scene.
- The active build ships only a minimal native-owned asset subset.
- The viewer remains mechanically passive and fully backend-driven.

Validation and parity expectations:

- The viewer still consumes backend state and backend-facing scene metadata only.
- Headless viewer export and build smoke coverage prove the native-owned asset bundle is present in the build output.

Dependency ordering:

- Depends on DV3.

## ARCH-VIEW1: Legacy Fight Caves Render/Asset Excavation

Purpose:
Identify the real legacy Fight Caves render path, asset source set, and bounded native export strategy before more viewer-parity implementation work.

Exact scope:

- Inspect `reference/legacy-headed-env` to distinguish control/debug shell responsibilities from actual scene rendering.
- Inspect `reference/legacy-rsps` to identify the real Fight Caves client, cache, interface, area, object, NPC, animation, and gfx source paths.
- Review local Ocean viewer envs only for render/input/packaging patterns worth adopting or avoiding.
- Produce a concrete native-owned export strategy and re-scope DV4b, DV5, and DV6 accordingly.

Out of scope:

- Viewer implementation work.
- Training behavior work.
- Performance work.
- PR8b cutover.

Acceptance criteria:

- The plan names the real legacy render path rather than hand-waving around “better assets”.
- The bounded native export surface is explicit: what to export, what to transform, what to own natively, and what not to copy wholesale.
- The next viewer implementation step is concrete and implementation-ready.

Validation and parity expectations:

- The investigation must preserve the shared-backend rule and must not create runtime dependency on `reference/legacy-*`.

Dependency ordering:

- Depends on DV4.

## DV4b: Native Asset Export Pipeline And Scene-Parity Slice

Purpose:
Build the first real native-owned Fight Caves export bundle and scene-parity slice from the legacy render/asset source path identified by ARCH-VIEW1.

Exact scope:

- Establish an offline export path from explicit cache input plus RSPS semantic tables into native-owned viewer bundles.
- Export the smallest credible Fight Caves scene subset:
  - terrain mesh and height metadata,
  - object placements and textured atlas subset,
  - Fight Caves overlay and essential HUD sprite subset,
  - minimal NPC/player visual subset for the default debug/manual-play path.
- Replace the remaining hand-authored arena shell/styling with exported scene data where available.
- Keep the viewer runtime reading only native-owned exported bundles.

Out of scope:

- Full HUD/inventory/camera parity.
- Manual-play validation closure.
- Training behavior work.
- PR8b cutover.

Acceptance criteria:

- The native viewer scene reads as Fight Caves because of exported scene/interface data, not just approximate primitive dressing.
- The runtime has no dependency on `reference/legacy-*` at load time.
- The exported bundle scope remains bounded and does not copy the full client/cache wholesale.

Validation and parity expectations:

- Exported assets are derived offline from legacy/client/cache inputs and checked into or generated into native-owned locations only.
- Backend gameplay truth remains unchanged.

Dependency ordering:

- Depends on ARCH-VIEW1.

## DV5: Native HUD, Inventory, Camera, And Interaction Parity

Purpose:
Tighten the native viewer shell so manual debugging and play feel much closer to the real Fight Caves interaction model.

Exact scope:

- Build on the exported scene bundle from DV4b.
- Improve Fight Caves HUD and inventory/prayer presentation toward real client readability.
- Improve camera framing, projection, and interaction readability around the real cave footprint.
- Tighten manual-control presentation without changing backend gameplay truth.

Out of scope:

- Backend gameplay redesign.
- Live trainer attach.
- PR8b cutover.

Acceptance criteria:

- The viewer is comfortable enough for manual debugging without relying on the earlier debug-only panel layout.
- Interaction and HUD presentation no longer obscure core Fight Caves state during manual play.
- The viewer presentation is no longer blocked by missing scene or interface assets from DV4b.

Validation and parity expectations:

- All gameplay-relevant interaction still routes into the same backend used by training.

Dependency ordering:

- Depends on DV4b.

## DV6: Manual-Play Debug Validation And Demo Readiness

Purpose:
Prove that the shared-backend viewer is usable for real manual debugging and external demo/readability needs before training work resumes.

Exact scope:

- Run manual-play validation and debugging workflow checks on the native viewer.
- Validate that the viewer supports the intended pause/step/replay/manual-input debugging loop cleanly.
- Close the minimum usable/readable/manual-debug bar for resuming training-side work.

Out of scope:

- PR8b cutover.
- Viewer-owned gameplay logic.
- Broader trainer redesign.

Acceptance criteria:

- The native viewer is usable as a real shared-backend manual-debug harness.
- Remaining viewer gaps, if any, are clearly narrowed to polish rather than core readability/debuggability.

Validation and parity expectations:

- The backend remains the single gameplay truth and the viewer remains presentation plus input plus debugging only.

Dependency ordering:

- Depends on DV5.

## ARCH-PERF2: End-to-End Throughput Architecture Investigation

Purpose:
Determine what broader architecture change would actually be required to move meaningfully beyond the current `~15k` to `16k` end-to-end class toward the explicit `250,000` SPS target.

Exact scope:

- Analyze the active native training path as a full system problem rather than another hot-path cleanup target.
- Review the trainer/orchestration boundary, subprocess and vecenv boundary, host/device transfer model, and the role of the current PufferLib contract.
- Review local Ocean native envs and current PufferLib runtime code as reference material only.
- Produce a small set of credible future architecture options and a recommendation.

Out of scope:

- PR8b default cutover.
- Another narrow `PR8a.x` optimization pass.
- Gameplay redesign.
- Trainer implementation redesign in the same pass.
- New viewer features or asset-pipeline work.

Acceptance criteria:

- The current structural ceiling is documented clearly enough that future work does not confuse system-level limits with another local hot-path issue.
- A small set of credible future architecture options exists.
- `docs/training_performance.md` is updated as the active performance source of truth.
- PR8b direction is clearer after the investigation.

Validation and parity expectations:

- No gameplay behavior changes are introduced.
- The native backend remains the single gameplay truth and the shared viewer model remains intact in all recommended options.

Dependency ordering:

- Depends on PR8a.5 and DV3.

## PR8b: Default Fast-Path Cutover

Purpose:
Flip the default fast path only after the intended CUDA learner path has proven the native backend is ready.

Exact scope:

- Make the native backend the default fast path only if the learner-performance track confirms the gates are cleared on the intended CUDA learner path.
- Preserve explicit fallback behavior.
- Document operational expectations and fallback paths.

Out of scope:

- Re-benchmarking to manufacture a passing result.
- Trainer redesign.
- PufferLib rewrite.

Acceptance criteria:

- Default fast backend selection points to the native implementation.
- Fallback paths remain explicit and functional.
- Operational expectations and fallback behavior are recorded in the existing plan docs.
- PR8b only proceeds after the learner-performance track actually clears all explicit gates on the intended CUDA learner path.

Validation and parity expectations:

- Default selection changes only after the CUDA-path rerun results are recorded.
- No parity regressions appear when the default fast path flips.

Dependency ordering:

- Depends on an explicit post-ARCH-PERF2 architecture-performance decision.

## Sequencing summary

Recommended order:

1. PR1
2. PR2
3. PR3
4. PR4
5. PR5a
6. PR5b
7. PR6
8. PR7
9. PR8a
10. PR8a-followup
11. PR8a.2
12. DV1
13. ARCH1
14. PR8a.3
15. PR8a.4
16. PR8a.5
17. DV2
18. DV3
19. ARCH-PERF2
20. DV4
21. ARCH-VIEW1
22. DV4b
23. DV5
24. DV6
25. PR8b

After ARCH1, the learner-performance track and the viewer track diverged cleanly. The narrow performance track remains paused after PR8a.5, ARCH-PERF2 has documented the broader architecture ceiling, DV4 is now understood as an initial presentation stub, ARCH-VIEW1 documented the real legacy render/asset source path, DV4b has now landed the first real cache-derived scene slice, DV5 is next, TRAIN3 is paused until the viewer-completion sequence clears the minimum usable/readable/manual-debug bar, and PR8b is still gated by an explicit architecture-performance decision rather than viewer work.

Do not combine PR5 through PR8 into one migration batch. The highest-risk failure mode in this project is losing parity while changing runtime, outputs, trainer integration, and viewer behavior at the same time.
