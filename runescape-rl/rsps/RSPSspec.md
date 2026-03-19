# RSPSspec.md

> Legacy detailed spec retained for reference.
> Canonical module docs are `rsps/README.md`, `rsps/SPEC.md`, `rsps/ARCHITECTURE.md`, and `rsps/PARITY.md`.

## 0) Purpose

This repo is the **headed RSPS oracle/reference module** for the Fight Caves workspace.

Its job is to preserve and expose the headed runtime behavior that the other modules reference when:
- parity disputes arise
- manual headed validation is required
- a mechanic needs debugging in the full game/server context

## 1) In-Scope Role

`RSPS` owns:
- headed/runtime Fight Caves behavior in the full server environment
- oracle/reference behavior for parity investigations
- manual validation and debugging in headed mode
- rare mechanic confirmation when the headless sim or RL wrapper appears to drift

## 2) Non-Goals

`RSPS` must not become:
- part of the RL training hot path
- a Python dependency of `RL`
- a dependency of the headless sim packaging/runtime path
- the place where RL wrapper semantics, batching, or PufferLib behavior are defined
- a duplicate implementation plan for the headless simulator

## 3) Dependency Boundaries

Dependency direction for this workspace remains:
- `RL -> fight-caves-RL`
- `fight-caves-RL` may compare against `RSPS` for oracle/parity/debug purposes
- `RSPS` remains out-of-band for runtime training execution

Allowed cross-repo references:
- parity/debug workflows
- headed/manual validation
- manifest provenance fields such as RSPS commit SHA when relevant to a parity pack or headed validation run

## 4) Validation and Debug Responsibilities

`RSPS` is the place to:
- reproduce a Fight Caves behavior in headed mode
- confirm whether a parity mismatch is a headless-sim bug, an RL-wrapper bug, or expected engine behavior
- provide headed reference context for rare or ambiguous mechanics

`RSPS` is not required for:
- normal RL bootstrap
- normal RL per-PR CI
- RL vectorization or throughput work

## 5) Setup Assumptions

Current baseline assumptions:
- Java 21+ is required
- the headed cache is available under `data/cache`
- standard headed bring-up follows the repo README and Gradle tasks already present in this repo

This spec does not redefine the full headed setup flow; it only fixes the module's role in the multi-repo architecture.

## 6) Release Workflow Intentionality

Unlike `fight-caves-RL`, this repo still presents itself as a Void-derived headed server in its current README and release workflow.

For this documentation pass:
- treat the current release workflow as intentionally inherited
- do not treat it as an RL/headless contract surface
- do not pull RSPS release automation into RL or headless packaging decisions

## 7) Acceptance Criteria

`RSPS` is sufficiently documented for the current workspace phase when:
- its oracle/reference role is explicit
- its non-goals are explicit
- its dependency boundary is explicit
- its validation/debug responsibilities are explicit
- its setup assumptions are explicit enough that the other repos can reference it correctly
