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
  - status: not_started
  - goal: move flat observation and reward-feature emission into the native hot path
  - dependency: PR5b
  - note: next
- PR7
  - status: not_started
  - goal: cut Python `v2_fast` integration over to the native backend without changing the trainer contract
  - dependency: PR6
  - note: upcoming
- PR8
  - status: not_started
  - goal: clear the explicit performance gates and make the native backend the default fast path
  - dependency: PR7
  - note: queued

## PR Scope Discipline

- PR2 must not absorb PR3+ mechanics work.
- PR3 must not absorb PR5 mechanics parity work.
- PR4 must not absorb PR5 combat/mechanics work.
- PR5a must not absorb PR5b Jad, healer, or Tz-Kek special mechanics work.
- PR5a must not absorb PR6 output-hot-path work.
- PR7 must not become trainer redesign.
- If an unblocker forces scope movement, record it explicitly in this file.

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

## Ownership and non-goals

The initial rewrite ownership surface is:

- native headless training runtime,
- headed demo environment only where minimal parity-support changes are required,
- RL and PufferLib connectivity code.

The following are explicitly not first-class rewrite targets in the early phases:

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
- Single-runtime env-only performance reaches at least `200,000` SPS on the authoritative benchmark host.

Validation and parity expectations:

- `v2_fast` smoke tests pass with the native backend selected.
- Native backend matches legacy parity fixtures before default cutover.

Dependency ordering:

- Depends on PR6.

## PR8: Performance Validation And Default Cutover

Purpose:
Prove the rewrite is worth keeping and make it the default fast path.

Exact scope:

- Run env-only, train-loop, and multi-worker performance validation.
- Remove or demote the JVM fast backend from default selection once the native backend clears parity and performance bars.
- Document residual fallback paths and operational expectations.

Out of scope:

- Full RSPS rewrite.
- Headed demo rewrite.

Acceptance criteria:

- Native backend clears all explicit gates:
  - env-only floor: `200,000` SPS,
  - end-to-end training floor: `250,000` SPS,
  - end-to-end training stretch target: `500,000` SPS,
  - multi-worker aggregate floor: `300,000` SPS.
- Long-run determinism and stability checks pass.
- Default fast backend selection points to the native implementation.

Validation and parity expectations:

- Native performance is measured against the same benchmark harness shape used for the legacy fast path.
- No parity regressions appear during long-run validation.
- Env-only, end-to-end training, and multi-worker SPS are reported separately.

Dependency ordering:

- Depends on PR7.

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
9. PR8

Do not combine PR5 through PR8 into one migration batch. The highest-risk failure mode in this project is losing parity while changing runtime, outputs, and trainer integration at the same time.
