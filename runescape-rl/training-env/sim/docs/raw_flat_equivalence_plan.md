# Raw Vs Flat Equivalence Plan

## Documentation Status

- Status: superseded for active planning under `pivot_documentation_triage.md`.
- Current authority: `pivot_plan.md`, `pivot_implementation_plan.md`, and `raw_flat_observation_contract.md`.
- Retention reason: kept for historical flat-path certification context; do not treat it as the active planning surface.

Date: 2026-03-09

This document defines the certification gate for proving that the flat training observation path remains semantically anchored to the raw headless observation path.

This is the source-of-truth deliverable for optimization `WC-P1-04`.

## Purpose

The flat training path is allowed to change representation, layout, batching, and transport behavior.
It is not allowed to change gameplay meaning.

Certification Mode exists to prove that the flat path is a semantically equivalent projection of the same sim-owned state that drives the raw path.

## Ownership

- primary owner: `fight-caves-RL`
- downstream consumer-equivalence owner: `RL`
- oracle/reference spot-check role: `RSPS`

The sim owns the raw semantic contract and the emitted flat schema.
RL owns the consumer-side proof that it can ingest the flat schema without reintroducing semantic drift.

## Reference Inputs

Raw semantic references:

- [observation_schema.md](/home/jordan/code/fight-caves-RL/docs/observation_schema.md)
- [raw_flat_observation_contract.md](/home/jordan/code/fight-caves-RL/docs/raw_flat_observation_contract.md)
- [flat_training_observation_schema.md](/home/jordan/code/fight-caves-RL/docs/flat_training_observation_schema.md)

RL consumer references:

- [observation_mapping.md](/home/jordan/code/RL/docs/observation_mapping.md)
- [flat_observation_ingestion.md](/home/jordan/code/RL/docs/flat_observation_ingestion.md)

## Equivalence Questions To Prove

Certification Mode must prove all of the following for the same tick and same env slot:

1. raw and flat reflect the same player state meaning
2. raw and flat reflect the same wave state meaning
3. raw and flat reflect the same visible-NPC ordering and target-index alignment
4. raw and flat reflect the same categorical identity mapping
5. raw and flat reflect the same parity-sensitive combat cue meaning, including `jad_telegraph_state`
6. raw and flat do not introduce future leakage not present in the raw path

## Required Test Buckets

### 1. Source-Side Projection Equality

Owner:

- `fight-caves-RL`

Goal:

- prove that the flat emitter projects the same meaning as the raw observation builder from the same sim-owned state

Required cases:

- single-slot state projection equality
- batched multi-slot projection equality
- representative snapshots from early, middle, and late waves
- snapshots with zero visible NPCs, partial NPC sets, and full padded NPC sets

Expected test shape:

- collect raw observation
- collect flat observation from the same tick/state
- compare against a shared reference projection contract

### 2. Visible-NPC Ordering And Padding

Owner:

- `fight-caves-RL`

Goal:

- prove that visible-NPC ordering and target alignment remain identical

Required assertions:

- `visible_index` alignment is preserved
- padding begins only after the last real visible NPC
- padded NPC rows are zero-filled with `present = 0`
- no real NPC is dropped or reordered inside the visible cap

### 3. Categorical Dictionary Stability

Owner:

- `fight-caves-RL`
- mirrored decode/consume checks in `RL`

Goal:

- prove that categorical code tables are stable and explicit

Required assertions:

- `ammo_id_code` dictionary matches the frozen contract
- `npc_id_code` dictionary matches the frozen contract
- any unknown categorical value fails fast rather than silently remapping

### 4. Jad Telegraph Equivalence

Owner:

- `fight-caves-RL`
- mirrored downstream checks in `RL`

Goal:

- prove that the flat path preserves Jad cue semantics exactly

Required assertions:

- `jad_telegraph_state` is non-zero on the same ticks in raw and flat paths
- value mapping remains `idle / magic_windup / ranged_windup`
- only the real Jad NPC may emit non-zero values
- no pre-telegraph future cue is exposed
- the cue window still matches the raw parity contract

### 5. No-Leakage Guard

Owner:

- `fight-caves-RL`
- `RL` for consumer-side enforcement

Goal:

- prove the flat path does not introduce helper signals absent from the raw path

Disallowed examples:

- direct correct-prayer fields
- countdown-to-impact fields
- future attack-style fields before telegraph onset
- hidden target-order shortcuts

### 6. Determinism And Replay Revalidation

Owner:

- `fight-caves-RL`
- `RL`

Goal:

- prove that the flat path does not break the existing determinism/parity expectations

Required reruns after implementation:

`fight-caves-RL`

- headless observation determinism/versioning tests
- Jad telegraph observation/parity tests
- deterministic replay tests
- headless parity harness slices that touch observation meaning

`RL`

- integration
- determinism
- parity
- replay-eval on a known checkpoint
- any new flat-path consumer equivalence tests

## Implemented And Planned Test Surfaces

### Sim-Side

Likely test areas:

- `game/test/headless/observation`
- `game/test/headless/determinism`
- `game/test/headless/parity`

Currently implemented categories:

- raw-vs-flat observation projection equality
- Jad telegraph raw-vs-flat equivalence

Still required as the Phase 1 gate broadens:

- visible-NPC padding/order equivalence
- categorical dictionary stability
- no-leakage flat-path regression

### RL-Side

Likely test areas:

- `fight_caves_rl/tests/unit`
- `fight_caves_rl/tests/integration`
- `fight_caves_rl/tests/determinism`
- `fight_caves_rl/tests/parity`

Currently implemented categories:

- flat buffer handshake/version fail-fast
- raw reference encoder vs flat row equality

Still required as the Phase 1 gate broadens:

- direct-ingestion batch-shape/dtype validation
- flat-path parity canary reruns

## Acceptance Rule

The flat path is trusted only if:

1. source-side projection equality is green
2. RL-side ingest equality is green
3. determinism/parity/replay reruns stay green
4. Jad telegraph semantics remain identical
5. no leakage regression is detected

If any of those fail, the flat path is not ready for Production Training Mode.

## Output Of WC-P1-04

`WC-P1-04` is complete when this certification gate is frozen clearly enough that:

- emitter implementation cannot claim success without satisfying it
- RL ingestion implementation cannot bypass it
- later benchmark wins cannot hide semantic drift
