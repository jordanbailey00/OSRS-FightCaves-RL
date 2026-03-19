# Cross-Module Contracts (Canonical)

This is the canonical source of truth for cross-module contracts and invariants.

## Contract 1: Source/runtime separation

- Source-owned content stays in source root.
- Generated/regenerable runtime outputs stay in runtime root.
- Scripts and defaults must route writes to runtime root.

## Contract 2: Mechanics ownership

- Simulator/runtime semantics are owned by `training-env/sim` (headless path) and `rsps` (headed path).
- `training-env/rl` consumes those semantics; it does not redefine mechanics.

## Contract 3: Starter-state/reset ownership

- Canonical episode start-state enforcement is server/runtime owned (not client UI owned).
- For headed RSPS-backed demo flow, starter-state enforcement is in the Fight Caves runtime path.
- RL wrappers may pass reset configuration but must not override runtime semantics.

## Contract 4: Shared action/observation boundary

- Headless training and headed backend-control paths must stay aligned on shared action semantics.
- Target selection/indexing semantics must remain stable enough for checkpoint transfer.
- Any schema change requires explicit contract update and compatibility notes.

## Contract 5: Parity scope

Parity is transfer-critical mechanics parity, especially:

- target ordering/target resolution
- prayer behavior
- consumables behavior
- movement/path timing behavior
- reset/state initialization
- wave progression
- Jad behavior when relevant

## Contract 6: Benchmark/performance truth

- `~500,000 SPS` refers to end-to-end training throughput.
- Env-only throughput is a separate supporting metric.
- Performance claims require benchmark packet evidence and decomposition support.

## Contract 7: Headed integration boundary

- RSPS-backed headed path is the trusted default for demo/replay.
- Backend-issued action control is the control boundary for headed replay/live inference.
- UI-clicking is not a substitute for backend-control integration proof.

## Contract 8: Fallback preservation

The following remain selectable until explicitly removed by a later approved plan:

- `demo-env` frozen fallback/reference path
- V1 oracle/reference/debug flows in `training-env/rl`

## Contract 9: Documentation authority

Canonical documentation authority lives in:

- `docs/active-reference/*`
- `docs/source-of-truth/*`
- module-level canonical docs (`README.md`, `SPEC.md`, `ARCHITECTURE.md`, `PARITY.md`)

Reports are non-canonical by default and must be merged into canonical docs when they change durable truth.
