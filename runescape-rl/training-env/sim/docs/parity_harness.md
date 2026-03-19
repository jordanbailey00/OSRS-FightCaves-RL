# Parity Harness

Step 9 introduces a deterministic oracle-vs-headless differential harness for Fight Caves.

## Purpose

The harness validates 1:1 behavior by replaying the same:
- initial episode state
- seed
- per-step action trace

across two runtime paths:
- `oracle`: full headed runtime logic path without networking (`OracleMain.bootstrap`)
- `headless`: extracted headless runtime path (`HeadlessMain.bootstrap`)

## Core Artifacts

- `ParityHarness`
- `ParitySnapshot`
- `ParityComparisonResult`
- `ParityMismatch`

## Snapshot Contract

Each `ParitySnapshot` captures:
- replay step index and game tick
- action + action result
- ordered observation payload (`HeadlessObservationV1.toOrderedMap()`)
- combat timing state (`action_delay`, `food_delay`, `drink_delay`, `combo_delay`, `stunned`, `delay`)
- player and visible-NPC hitsplat counts
- RNG call counter (diagnostic only)

## Comparison Behavior

- Fail-fast: first mismatch stops comparison.
- Mismatch output includes:
  - snapshot index
  - step index
  - tick
  - field path
  - oracle value
  - headless value
  - last action
  - oracle/headless RNG counters
- On mismatch, first-divergence artifact is written to:
  - `temp/parity/first_divergence/parity_seed_<seed>_wave_<wave>_<timestamp>.txt`

## Usage

Use `ParityHarness.runAndCompare(...)` with:
- `seed`
- `startWave`
- `actionTrace`
- optional runtime `settingsOverrides`
- optional deterministic `configurePlayer` hook
- optional deterministic `stepHook` for scenario fixture actions

The result is considered passing when `result.mismatch == null`.

## Step 9 Test Matrix

Implemented integration tests:
- `ParityHarnessSingleWaveTraceTest`
- `ParityHarnessFullRunTraceTest`
- `ParityHarnessJadHealerScenarioTest`
- `ParityHarnessTzKekSplitScenarioTest`
