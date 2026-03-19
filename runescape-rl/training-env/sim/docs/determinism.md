# Determinism and RNG Governance

This document defines Step 8 deterministic replay and RNG governance for the headless Fight Caves runtime.

## RNG Source of Truth

- Fight Caves runtime randomness uses `sim.types.random`.
- Episode reset seeds RNG through `setSeededRandom(seed, trackCalls = true)`.
- All replay runs for parity/debug are expected to use seeded RNG.

## RNG Diagnostics

RNG diagnostics are exposed via:

- `randomDiagnostics()` -> `RandomDiagnostics`
- `randomCallCount()` -> current call count for tracked RNG

`RandomDiagnostics` fields:

- `trackingEnabled`: whether call tracking is active
- `callCount`: total RNG calls observed by tracked RNG wrapper
- `seed`: seeded value when seeded tracking is active

## Deterministic Replay Runner

`HeadlessReplayRunner` executes:

1. Episode reset with provided seed/start wave.
2. Per-step action application from a fixed action trace.
3. Tick progression by `ticksAfter` per step.
4. Observation capture (`HeadlessObservationV1`) and RNG call counter snapshots.

Replay output:

- `HeadlessReplayResult` (seed, startWave, snapshots, rngDiagnostics)
- per-step `HeadlessReplayTickSnapshot` containing observation + RNG counter

## Governance Rules

- Fight Caves closure code must not directly import/use `kotlin.random.Random`.
- Direct randomness in closure is forbidden outside shared RNG utilities.
- CI test gate enforces this policy with a static source scan.

## Debug/Parity Use

For oracle-vs-headless parity diagnostics, include:

- replay step index
- tick
- action and action result
- observation payload
- RNG call counter at snapshot
- final RNG diagnostics

This provides deterministic first-divergence context for Step 9 parity harness.
