# Raw Vs Flat Observation Contract

Date: 2026-03-09

This document defines the Phase 1 ownership and versioning contract between the existing raw headless observation path and the flat training observation path.

## Purpose

The raw path remains the semantic reference.
The flat path is the performance path.

The flat path may change representation, layout, batching shape, and transport behavior, but it may not silently change gameplay meaning.

## Ownership

- `fight-caves-RL` owns the raw semantic observation contract.
- `fight-caves-RL` also owns the sim-emitted flat training schema and its version ids.
- `RL` owns flat-schema consumption, compatibility checks, manifests, and trainer-side ingestion.
- `RSPS` remains oracle/reference only and is not part of the training hot path.

## Raw Semantic Reference

The current raw semantic reference is:

- [observation_schema.md](/home/jordan/code/fight-caves-RL/docs/observation_schema.md)
- schema id: `headless_observation_v1`

Protected semantic content includes:

- episode-start-aligned player state
- wave state meaning
- visible-NPC ordering and target-index alignment
- all current player/NPC field meaning
- decision-critical combat cues already present in the raw contract, including `jad_telegraph_state`

`jad_telegraph_state` is therefore part of the protected raw semantic contract, not an optional trainer convenience field.

## Flat Training Path

The flat training path must be:

- sim-owned
- fixed-size
- typed
- batched
- semantically equivalent to the same sim-owned state that drives the raw path

The flat path may:

- reorder fields
- pack booleans/categories into numeric layouts
- split batch/control/debug metadata into separate buffers
- use layouts optimized for transport and ingestion

The flat path may not:

- remove required decision-making meaning
- change visible-NPC ordering semantics
- reinterpret `jad_telegraph_state` as an oracle or countdown
- expose attack-style information before the real telegraph window begins

## Versioning Rules

Raw semantic contract version bump required when:

- a raw field meaning changes
- target ordering semantics change
- reset/episode-start observation meaning changes
- a parity-sensitive cue meaning changes

Flat training schema version bump required when:

- flat field order changes
- dtype changes
- batching layout changes
- mask/control-plane layout changes
- a projected field is added or removed from the flat schema

No raw or flat version bump required when:

- transport changes only
- internal implementation changes preserve identical layout and meaning
- benchmark/profiler harnesses change only

## Change Matrix

If the raw semantic contract changes:

- update [observation_schema.md](/home/jordan/code/fight-caves-RL/docs/observation_schema.md)
- update this document
- update sim-side parity/determinism/equivalence tests
- update RL-side consumer assertions and manifests

If the flat schema changes but semantics do not:

- update this document
- update sim-side flat-schema constants/docs once they exist
- update RL-side ingestion/manifests/tests
- rerun raw-vs-flat equivalence gates

If transport changes only:

- update RL transport/worker docs and manifests
- do not bump the raw semantic schema
- do not bump the flat schema unless the actual flat layout or wire compatibility contract changes

## Certification Mode

Certification Mode proves the flat path stays anchored to the raw path.

At minimum it must verify, on the same tick and same sim-owned state:

- identical projected field meaning
- identical visible-NPC ordering alignment
- identical action-target alignment assumptions
- identical parity-sensitive combat cue meaning, including `jad_telegraph_state`

For Jad specifically, Certification Mode must prove:

- `jad_telegraph_state` is non-zero on the same ticks as the raw path
- the encoded value preserves `idle / magic_windup / ranged_windup`
- no pre-telegraph future cue is exposed

## Production Training Mode

Production Training Mode may consume the flat path directly and keep the raw path out of the hot loop.

That is allowed only after Certification Mode proves equivalence for the relevant raw-schema / flat-schema version pair.

## Current Phase 1 Status

This ownership contract is now active in the current Phase 1 implementation batch.

Implemented Phase 1 references:

- [flat_training_observation_schema.md](/home/jordan/code/fight-caves-RL/docs/flat_training_observation_schema.md)
- [flat_observation_ingestion.md](/home/jordan/code/RL/docs/flat_observation_ingestion.md)
- [raw_flat_equivalence_plan.md](/home/jordan/code/fight-caves-RL/docs/raw_flat_equivalence_plan.md)
