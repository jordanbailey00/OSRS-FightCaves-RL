# training-env SPEC

## Purpose

Provide the default headless training path for Fight Caves RL:

- deterministic headless simulation runtime
- RL training/eval/replay control plane
- benchmark and profiling surfaces for throughput and learning proof

## Required behavior

- Must support headless training on the default V2 path.
- Must support checkpoint eval/replay flows.
- Must expose benchmark and profiling entrypoints used by active plan/performance docs.
- Must preserve fallback V1 oracle/reference/debug paths until explicitly removed.

## Inputs

- training configs (`training-env/rl/configs`)
- sim/runtime configuration and assets (`training-env/sim`)
- runtime-root output location (`FIGHT_CAVES_RUNTIME_ROOT`)

## Outputs

- checkpoints
- eval summaries/replay artifacts
- benchmark/profiling artifacts
- run manifests/logs

All generated outputs go to runtime root, not source directories.

## Invariants

- Headless training semantics are runtime-owned, not reinterpreted in Python.
- Action/observation semantics must stay aligned with shared contracts used for headed transfer.
- Benchmark claims must use canonical methodology and recorded evidence.
- Training default remains `v2_fast` unless changed by approved plan.
