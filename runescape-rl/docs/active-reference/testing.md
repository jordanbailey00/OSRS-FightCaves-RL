# Active Testing Reference

This is the single active testing and validation truth for the workspace.

## Required validation gates

## Gate G1: Workspace smoke gate (must pass first)

Required command:

```bash
cd /home/jordan/code/runescape-rl
./scripts/validate_smoke.sh
```

Required evidence:

- smoke logs under `/home/jordan/code/runescape-rl-runtime/reports/validation`
- successful completion of all three smoke stages

Failure blocks:

- any Phase 10+ run that depends on `training-env` or headed selector wiring

## 1) Root smoke validation (workspace wiring)

Command:

```bash
cd /home/jordan/code/runescape-rl
./scripts/validate_smoke.sh
```

What it proves:

- `demo-env` still resolves through the `training-env/sim` composite build.
- `training-env/sim` Gradle module graph is loadable.
- `training-env/rl` backend selector wiring is operational.
- Runtime outputs route to runtime root, not source root.

## Gate G2: RSPS headed trust gate (must stay green)

Required acceptance areas:

- direct Fight Caves entry
- canonical starter-state/reset behavior
- combat/prayer/inventory loop usability
- wave progression and recycle behavior
- no broader world access

Required evidence:

- `rsps/docs/fight_caves_demo_headed_trust_gate.md`
- latest headed session artifacts/manifests/checklists referenced from trust gate note

Failure blocks:

- any claim of headed transfer readiness (Phase 11) or demo-default trust

## 2) RSPS headed demo correctness

Primary areas (covered by Gate G2):

- Fight Caves starter-state initializer behavior.
- Fight Caves wave progression and completion behavior.
- Headed trust-gate checks (entry, reset, combat/prayer/inventory loop, wave progression, world gating).

Key references:

- `rsps/docs/fight_caves_demo_headed_trust_gate.md`
- `rsps/docs/fight_caves_demo_observability.md`
- `rsps/docs/fight_caves_demo_stock_client_runbook.md`

## Gate G3: RL correctness gate

Required suites:

- `training-env/rl/fight_caves_rl/tests/unit`
- `training-env/rl/fight_caves_rl/tests/train`
- required integration/smoke suites for active phase (`integration`, `parity`, `determinism`, `smoke`) when touching bridge/env/control behavior

Required evidence:

- passing pytest output in CI/local run logs
- no contract-breaking changes to action/observation/replay interfaces

Failure blocks:

- benchmark/learning claims if train/bootstrap correctness is broken

## 3) Training-env RL correctness and integration

Core suites:

- unit: `training-env/rl/fight_caves_rl/tests/unit`
- train bootstrap/tests: `training-env/rl/fight_caves_rl/tests/train`
- integration/parity/determinism/smoke: `training-env/rl/fight_caves_rl/tests/{integration,parity,determinism,smoke}`

Key scripts:

- `training-env/rl/scripts/train.py`
- `training-env/rl/scripts/replay_eval.py`
- `training-env/rl/scripts/run_demo_backend.py`
- `training-env/rl/scripts/run_acceptance_gate.py`

## Gate G4: Sim mechanics gate

Required areas:

- headless runtime reset/step correctness
- Fight Caves mechanic progression tests
- parity harness surfaces for transfer-critical semantics

Failure blocks:

- learning proof credibility when reset/progression semantics are unstable

## 4) Training-env sim correctness

Core areas:

- headless runtime reset/step behavior
- parity harness behavior
- Fight Caves mechanics tests in `training-env/sim/game`

## Gate G5: Performance truth gate

Required steps:

1. Baseline benchmark packet (`run_phase9_baseline_packet.py`) with canonical configs.
2. Decomposition update (`analyze_phase9_baseline_packet.py`) after any meaningful perf-impacting change.
3. Performance summary update in `docs/active-reference/performance.md`.

Failure blocks:

- optimization claims without packet/decomposition evidence

## 5) Performance and benchmark validation

Canonical benchmark scripts:

- `training-env/rl/scripts/benchmark_env.py`
- `training-env/rl/scripts/benchmark_train.py`
- `training-env/rl/scripts/run_phase9_baseline_packet.py`
- `training-env/rl/scripts/analyze_phase9_baseline_packet.py`

Current profiling reference:

- `docs/active-reference/performance.md`
- `docs/reports/perf_hotpath_attribution_20260316.md`

## Required smoke and acceptance sequence (operator checklist)

1. Run G1 workspace smoke.
2. Run required RL suites (G3) for touched areas.
3. Run required sim mechanics checks (G4) for touched areas.
4. If headed path touched, rerun headed trust acceptance checks (G2).
5. If perf-sensitive path touched, rerun benchmark/decomposition packet (G5).
6. Update:
   - `docs/active-reference/plan.md` (status/blockers)
   - `docs/active-reference/performance.md` (if perf data changed)
   - `docs/active-reference/changelog.md` (if major outcome/decision changed)

## Current blockers and MUST REVISIT items

- `MUST REVISIT`: training ceiling benchmark shape mismatch on `fast_train_v2` (Phase 9.2), `64x536` vs `134x128`.
- Until resolved, use decomposition + baseline packet evidence as the performance truth source for optimization prioritization.
