# codex2

`codex2` is the cleanup and planning workspace for the RuneScape Fight Caves C/C++ rewrite.

This pass does not start the rewrite. It keeps the legacy Kotlin/Java/Python code only as reference material, removes stale documentation/report clutter, and establishes a small set of source-of-truth planning docs for the native rewrite.

## Goal

The project goal is to replace the current high-frequency Fight Caves simulation path with a native C/C++ implementation while preserving:

- deterministic episode reset and stepping behavior,
- parity-critical Fight Caves mechanics,
- flat observation and reward-feature contracts used by training,
- Python training integration until the native core is stable.

## Long-Term Ownership

The intended long-term active code surface in this repo is:

- native headless training environment,
- headed demo environment,
- RL and PufferLib connectivity code.

The following stay in the repo as reference-only infrastructure during the rewrite:

- `runescape-rl/src/rsps` as oracle/reference behavior,
- `pufferlib` as trainer/runtime reference material.

RSPS and PufferLib are not first-class rewrite targets in the initial phases.

## Why rewrite in C/C++

The current fast path still crosses Python and JVM boundaries during training. The codebase already shows that the performance-sensitive surface is a batched simulator kernel that emits flat float buffers and reward features. Rewriting that surface in C/C++ is the clearest path to lower overhead without rewriting the entire trainer stack first.

## Major workspace components

- `runescape-rl/src/headless-env`: Kotlin reference implementation of the current headless simulator and fast-kernel boundary.
- `runescape-rl/src/rsps`: headed RSPS oracle/reference implementation.
- `runescape-rl/src/headed-env`: headed demo-lite and validation path.
- `runescape-rl/src/rl`: Python RL, evaluation, replay, benchmarking, vectorization, and trainer integration code.
- `pufferlib`: vendored PufferLib reference implementation used by the current Python training stack.

## Intended architecture

The target architecture is:

- native Fight Caves simulator core in C/C++,
- native batched reset/step API that emits flat observations, reward features, and terminal codes,
- thin Python integration layer for training and evaluation,
- legacy RSPS/headless Kotlin code retained temporarily as parity oracle and migration reference.

The rewrite should start at the simulator kernel boundary, not at the trainer UI, demo tooling, or full RSPS server boundary.

## Parity And Performance

The native runtime will own the hot path. Legacy headless and RSPS code will serve as oracle/reference validation infrastructure only.

Parity will be enforced through:

- frozen contracts,
- golden reset fixtures,
- golden step traces,
- native-vs-legacy parity tests,
- append-only schema and versioning rules.

The initial planning gates are:

- native env-only throughput floor: `200,000` SPS,
- full end-to-end training throughput stretch target: `500,000` SPS,
- multi-worker throughput is tracked separately from single-runtime env-only and full-training numbers.

These are distinct measurements and must not be conflated.

## Early Non-Goals

Early rewrite phases do not include:

- trainer rewrite,
- PufferLib rewrite,
- full RSPS rewrite,
- headed demo rewrite beyond minimal parity-support changes,
- action-space redesign,
- broad RL redesign.

## How to use this repo now

- Treat [`docs/plan.md`](docs/plan.md) as the rewrite source of truth.
- Treat [`docs/pr_plan.md`](docs/pr_plan.md) as the implementation slicing plan.
- Use legacy code under `runescape-rl/` and `pufferlib/` as reference implementations, not as planning authorities.
- Do not add new legacy-style planning docs elsewhere in the tree.

Legacy doc sprawl was intentionally removed so this workspace can become the clean source of truth for the rewrite.
