# Documentation Consolidation Report

Date: 2026-03-16  
Scope: canonical source-of-truth reset for `/home/jordan/code/runescape-rl`

## Canonical docs created/updated

## Root

- `README.md` (updated)
- `AGENTS.md` (created)
- `docs/active-reference/plan.md` (created)
- `docs/active-reference/performance.md` (created)
- `docs/active-reference/changelog.md` (created)
- `docs/active-reference/testing.md` (created)
- `docs/source-of-truth/architecture.md` (created)
- `docs/source-of-truth/contracts.md` (created)
- `docs/source-of-truth/project_tree.md` (created)

## Module canonicals

- `training-env/README.md` (updated)
- `training-env/SPEC.md` (created)
- `training-env/ARCHITECTURE.md` (created)
- `training-env/PARITY.md` (created)
- `demo-env/README.md` (updated)
- `demo-env/SPEC.md` (created)
- `demo-env/ARCHITECTURE.md` (created)
- `demo-env/PARITY.md` (created)
- `rsps/README.md` (replaced with workspace canonical entrypoint)
- `rsps/SPEC.md` (created)
- `rsps/ARCHITECTURE.md` (created)
- `rsps/PARITY.md` (created)

## Non-canonical notices added

- `training-env/rl/docs/README.md`
- `training-env/sim/docs/README.md`
- `rsps/docs/README.md`
- `demo-env/docs/README.md`
- `training-env/rl/RLspec.md` (legacy note added)
- `rsps/RSPSspec.md` (legacy note added)
- `demo-env/docs/DEMOspec.md` (replacement canonical links updated)

## Competing docs decommissioned/moved

Moved from competing root locations into `docs/reports/`:

- `docs/architecture.md` -> `docs/reports/architecture_legacy.md`
- `docs/repo_layout.md` -> `docs/reports/repo_layout_legacy.md`
- `docs/changelog.md` -> `docs/reports/changelog_full_history.md`
- `docs/perf_hotpath_attribution.md` -> `docs/reports/perf_hotpath_attribution_20260316.md`
- `docs/archive/pivot/system_acceptance_gates.md` -> `docs/reports/system_acceptance_gates_legacy.md`

## Truth migration summary

- Active plan truth migrated from post-pivot archived plan docs into `docs/active-reference/plan.md`.
- Latest benchmark/profiling truth migrated from Phase 9 and hot-path attribution docs into `docs/active-reference/performance.md`.
- Testing/gate/runbook truth migrated into `docs/active-reference/testing.md`.
- Architecture/source-root/runtime-root and module ownership truth migrated into `docs/source-of-truth/architecture.md`.
- Cross-module invariants and parity contracts migrated into `docs/source-of-truth/contracts.md`.
- Documentation routing/authority map formalized in `docs/source-of-truth/project_tree.md`.

## Unresolved conflicts / needs confirmation

- Some legacy deep docs in `training-env/rl/docs`, `training-env/sim/docs`, and `rsps/docs` still contain stale historical absolute paths and older wording.
  - Current disposition: retained as report/reference (non-canonical), not active truth.
- Phase 9.2 `benchmark_train_ceiling` mismatch remains a technical open issue in active plan/testing docs.

## Docs intentionally kept as reports

- Phase packets, decomposition notes, runbooks, headed validation notes, and inventories remain as report/reference artifacts.
- Archive folders remain for historical traceability and are explicitly non-canonical.

## Deletions

- No evidence-bearing report docs were deleted in this pass.
- Competing root authority docs were moved to reports instead of hard deletion.
