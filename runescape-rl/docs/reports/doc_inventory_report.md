# Documentation Inventory Report

Date: 2026-03-16  
Scope: workspace-level and major module-level docs in `/home/jordan/code/runescape-rl`

Classification buckets used:

- canonical truth worth migrating
- active reference worth migrating
- temporary report
- historical/archive
- obsolete/redundant/conflicting

## Inventory and disposition

| Path | Current role before consolidation | Valid truth present | Target canonical destination(s) | Final action |
|---|---|---|---|---|
| `README.md` | root entrypoint | yes | `README.md` | merge/update |
| `AGENTS.md` (missing) | agent policy | n/a | `AGENTS.md` | create |
| `docs/architecture.md` | competing architecture doc | yes | `docs/source-of-truth/architecture.md` | merge then move to report (`docs/reports/architecture_legacy.md`) |
| `docs/repo_layout.md` | competing tree/layout doc | yes | `docs/source-of-truth/project_tree.md` | merge then move to report (`docs/reports/repo_layout_legacy.md`) |
| `docs/changelog.md` | long historical changelog | yes | `docs/active-reference/changelog.md` | merge current 14-day truth; move full history to report (`docs/reports/changelog_full_history.md`) |
| `docs/perf_hotpath_attribution.md` | one-off perf report | yes | `docs/active-reference/performance.md` | keep as report (`docs/reports/perf_hotpath_attribution_20260316.md`) |
| `docs/archive/pivot/post_pivot_plan.md` | historical plan source | yes | `docs/active-reference/plan.md`, `docs/source-of-truth/architecture.md` | merge; keep in archive |
| `docs/archive/pivot/post_pivot_implementation_plan.md` | detailed execution tracker | yes | `docs/active-reference/plan.md`, `docs/active-reference/testing.md` | merge; keep in archive |
| `docs/archive/pivot/pivot_closure_report.md` | closure evidence | yes | `docs/active-reference/changelog.md`, `docs/source-of-truth/architecture.md` | merge; keep in archive |
| `docs/archive/pivot/system_acceptance_gates.md` | gate definitions | yes | `docs/active-reference/testing.md`, `docs/active-reference/plan.md` | merge; move to report (`docs/reports/system_acceptance_gates_legacy.md`) |
| `docs/archive/pivot/pivot_plan.md` | superseded plan | partial historical | `docs/active-reference/changelog.md` | keep in archive |
| `docs/archive/pivot/pivot_implementation_plan.md` | superseded tracker | partial historical | `docs/active-reference/changelog.md` | keep in archive |
| `docs/archive/pivot/fight_caves_demo_rsps_spec.md` | superseded Phase 7 spec | partial historical | `docs/source-of-truth/contracts.md` | keep in archive |
| `docs/archive/inventory/*` | one-off audits/inventories | yes (report-level) | none (report-only) | keep in archive/reports (non-canonical) |
| `training-env/README.md` | module entrypoint | yes | `training-env/README.md` | merge/update |
| `training-env/rl/RLspec.md` | deep RL module spec | yes but overlapping | `training-env/SPEC.md`, `docs/source-of-truth/contracts.md` | merge; keep as legacy detailed spec with non-canonical note |
| `training-env/rl/docs/default_backend_selection.md` | backend defaults note | yes | `docs/source-of-truth/architecture.md`, `docs/source-of-truth/contracts.md`, `docs/active-reference/plan.md` | merge; keep as report/reference |
| `training-env/rl/docs/eval_and_replay.md` | replay/eval contract | yes | `docs/source-of-truth/contracts.md`, `training-env/PARITY.md` | merge; keep as report/reference |
| `training-env/rl/docs/rl_integration_contract.md` | RL/sim contract detail | yes | `docs/source-of-truth/contracts.md`, `training-env/SPEC.md` | merge; keep as report/reference |
| `training-env/rl/docs/performance_decomposition_report.md` | perf analysis | yes | `docs/active-reference/performance.md` | keep as report/reference |
| `training-env/rl/docs/phase9_pr91_baseline.md` | baseline packet report | yes | `docs/active-reference/performance.md` | keep as report |
| `training-env/rl/docs/phase9_pr92_decomposition.md` | decomposition report | yes | `docs/active-reference/performance.md` | keep as report |
| `training-env/rl/docs/phase9_pr93_training_readiness.md` | readiness report | yes | `docs/active-reference/plan.md`, `docs/active-reference/testing.md` | keep as report |
| `training-env/rl/docs/*` (other) | detailed technical notes | mixed | canonical docs where applicable | keep as reports with non-canonical folder notice |
| `training-env/sim/README.md` | sim submodule entrypoint | yes (submodule scope) | `training-env/ARCHITECTURE.md`, `training-env/SPEC.md` | merge; retain as submodule doc |
| `training-env/sim/docs/*` | detailed sim docs | yes (detailed) | `docs/source-of-truth/contracts.md`, `training-env/PARITY.md` | keep as reports/reference with non-canonical notice |
| `demo-env/README.md` | module entrypoint | yes | `demo-env/README.md` | merge/update |
| `demo-env/docs/DEMOspec.md` | frozen lite-demo note | yes | `demo-env/SPEC.md`, `docs/source-of-truth/architecture.md` | merge; keep as legacy note |
| `rsps/README.md` | inherited upstream mixed doc | yes but conflicting authority | `rsps/README.md` | replace with workspace-canonical module entrypoint |
| `rsps/RSPSspec.md` | detailed RSPS role spec | yes | `rsps/SPEC.md`, `docs/source-of-truth/contracts.md` | merge; keep as legacy detailed spec with non-canonical note |
| `rsps/docs/fight_caves_demo_*` | runbooks and validation reports | yes | `docs/active-reference/testing.md`, `rsps/PARITY.md` | keep as reports/reference with non-canonical folder notice |
| `rsps/docs/jad_telegraph_plan.md` | targeted mechanic note | yes (historical/debug) | `docs/active-reference/changelog.md`, `training-env/PARITY.md` | keep as report/reference |
| `docs/archive/rl/*`, `docs/archive/sim/*`, `docs/archive/demo-env/*` | historical/archive docs | historical | none | keep archived, non-canonical |

## Net result

- Canonical truth consolidated into:
  - `docs/active-reference/*`
  - `docs/source-of-truth/*`
  - module canonical docs (`README/SPEC/ARCHITECTURE/PARITY`).
- Competing root docs were moved into `docs/reports/`.
- Legacy deep specs/runbooks retained, but explicitly marked non-canonical.
