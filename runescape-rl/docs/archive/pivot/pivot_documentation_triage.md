# Pivot Documentation Triage

## Scope
- This is a planning-only triage pass.
- No files were moved, renamed, deleted, archived, or edited.
- Classifications are proposed for review only.

## Pivot Context
- `pivot_plan.md` defines the current workspace pivot architecture.
- `pivot_implementation_plan.md` defines the current phased execution plan.
- The current stack is split into V1 oracle/reference, V1.5 `fight-caves-demo-lite` headed demo/replay, and V2 fast headless training.
- Mechanics parity, not full engine/runtime parity, is the governing boundary across oracle, demo, and trainer.
- `RL/` owns the RL/training path, `fight-caves-RL/` is simulator/oracle-adjacent, and `RSPS/` is oracle/reference only.
- `fight-caves-demo-lite` is part of the target architecture, but no documentation surface for it exists in the current workspace yet because the module is not present.

## Triage Method
I reviewed the current documentation surface in the workspace root, `RL/`, `RL/docs/`, `RL/history/`, `fight-caves-RL/`, `fight-caves-RL/docs/`, `fight-caves-RL/history/`, `RSPS/`, `RSPS/docs/`, and `RSPS/history/`. Each document was classified by its present role under the pivot rather than by age, file name, or prior prominence.

## Classification Rules
- `source of truth`: the current authoritative document for workspace or module direction under the pivot.
- `active reference`: implementation-facing reference material that still matters during pivot execution but is not the top authority.
- `historical`: prior plans, reports, changelogs, and audits that remain useful context but no longer govern active work.
- `archive`: a proposed archive candidate if approved later because its planning role is superseded or heavily overlapped.
- `operational/setup`: onboarding, setup, repo overview, config syntax, contribution, or policy docs.

## Proposed Documentation Status Map

| Path | Label | Primary Function | Why This Label Fits Under The Pivot | Proposed Follow-up |
|------|-------|------------------|-------------------------------------|--------------------|
| `pivot_plan.md` | source of truth | Workspace pivot architecture | It defines the current V1/V1.5/V2 split, module roles, mechanics-parity boundary, and success criteria for the pivot. | keep as-is |
| `pivot_implementation_plan.md` | source of truth | Workspace execution plan | It is the current phased execution plan that operationalizes the pivot without replacing the architectural authority of `pivot_plan.md`. | keep as-is |
| `CODEBASE_INVENTORY.md` | active reference | Workspace structure inventory | It remains useful for navigation and repo-surface review during the pivot, but it does not define architecture or execution. | keep visible |
| `CODEBASE_INVENTORY_RSPS.md` | active reference | RSPS structure inventory | It remains useful for RSPS navigation and doc triage under the oracle-only module role. | keep visible |
| `optimization_history.md` | historical | Optimization program record | It records prior optimization work and decisions that explain how the workspace reached the pivot, but it no longer governs the active doc surface. | keep visible |
| `optimization_plan.md` | archive | Pre-pivot workspace plan | Its planning role is superseded by `pivot_plan.md` and `pivot_implementation_plan.md`, and its priorities predate the pivot architecture. | archive candidate |
| `optimization_work_chunks.md` | archive | Pre-pivot work tracker | It tracks an older optimization program that is no longer the active execution framework once the pivot plan exists. | archive candidate |
| `trainer_architecture_decision_memo.md` | historical | Pre-pivot architecture memo | It still captures useful decision context, but the pivot docs now own current architecture and execution direction. | keep visible |
| `RL/RLspec.md` | source of truth | RL module spec | It still defines the RL module boundary and responsibilities under the pivot, even though the workspace execution plan now lives at the root. | keep as-is |
| `RL/RLplan.md` | historical | RL implementation plan | It is a detailed module plan, but the workspace pivot plan now owns the active sequencing and architecture across repos. | mark as superseded |
| `RL/README.md` | operational/setup | RL repo overview and bootstrap | It explains module scope and setup, which is operational information rather than current architecture authority. | keep as-is |
| `RL/changelog.md` | historical | RL change log | It records prior RL implementation changes and benchmark outcomes but is not an active planning or contract document. | keep visible |
| `RL/docs/action_mapping.md` | active reference | RL action contract reference | It still defines the RL-side action interpretation for the shipped simulator path and remains directly relevant during the pivot. | keep visible |
| `RL/docs/benchmark_matrix.md` | historical | Optimization-era benchmark packet | It captures benchmark structure from the pre-pivot audit phase, which is still useful context but no longer primary planning guidance. | keep visible |
| `RL/docs/bridge_contract.md` | active reference | RL bridge contract | It still documents the bridge/runtime contract for the current simulator-backed path and remains relevant while V1 stays as oracle/interim infrastructure. | keep visible |
| `RL/docs/eval_and_replay.md` | active reference | RL replay/eval contract | Replay and evaluation remain active parts of the pivot, so this contract doc still matters during implementation. | keep visible |
| `RL/docs/flat_observation_ingestion.md` | historical | Phase 1 flat-ingestion design | It documents the old flat-path optimization work and remains useful context, but it is no longer the top active design surface under the pivot. | keep visible |
| `RL/docs/hotpath_map.md` | historical | Current V1 hot path map | It remains useful as baseline context for understanding the pre-pivot train path, but it is a historical diagnostic view rather than a current plan. | keep visible |
| `RL/docs/logging_overhead_report.md` | historical | Logging cost analysis | It records pre-pivot performance findings that may still inform implementation, but it is not an active contract or plan. | keep visible |
| `RL/docs/mvp_acceptance.md` | active reference | RL acceptance gate reference | The current RL acceptance gate still exists and is useful during the pivot, even though later pivot-specific acceptance will likely supersede it. | keep visible |
| `RL/docs/observation_action_cost_report.md` | historical | Action/observation cost report | It is a useful diagnostic artifact from the optimization phase, but the pivot docs now govern current direction. | keep visible |
| `RL/docs/observation_mapping.md` | active reference | RL observation contract reference | It still defines how RL interprets the simulator observation surface and remains implementation-relevant under the pivot. | keep visible |
| `RL/docs/parity_canaries.md` | active reference | Parity canary reference | Parity remains central to the pivot, so this doc remains important reference material even though parity scope has narrowed to mechanics parity. | keep visible |
| `RL/docs/parity_safe_optimization_rules.md` | historical | Optimization safety rules | Its invariants are still useful context, but it belongs to the earlier optimization program rather than the current pivot doc hierarchy. | keep visible |
| `RL/docs/performance_decomposition_report.md` | historical | Performance evidence report | It remains valuable evidence for why the pivot happened, but it is no longer an active planning document. | keep visible |
| `RL/docs/performance_plan.md` | archive | Pre-pivot performance plan | Its goals and staged plan are superseded by the pivot architecture and current performance gates. | archive candidate |
| `RL/docs/phase1_decision_gate.md` | archive | Old Phase 1 gate | It is an optimization-program gate tied to a superseded workstream rather than the current pivot execution path. | archive candidate |
| `RL/docs/phase2_blocker_diagnosis.md` | historical | Phase 2 blocker analysis | It remains useful diagnostic context from the pre-pivot transport/trainer effort, but it no longer defines active work. | keep visible |
| `RL/docs/phase2_transport_gate.md` | archive | Old transport promotion gate | It is tied to a pre-pivot transport-first decision path that no longer governs the workspace after the pivot. | archive candidate |
| `RL/docs/production_fast_trainer_benchmark.md` | historical | Prototype benchmark target | It captures useful benchmark context from the prior trainer redesign effort, but the pivot implementation plan now owns active execution. | keep visible |
| `RL/docs/production_trainer_prototype_scope.md` | historical | Prototype scope memo | It records a prior prototype boundary that may still inform implementation context but is not current authority. | keep visible |
| `RL/docs/python_profiler_report.md` | historical | Python profiling report | It remains useful evidence for prior bottleneck analysis but is no longer a current plan or contract. | keep visible |
| `RL/docs/reward_configs.md` | active reference | RL reward/curriculum contract | Reward config and curriculum ownership stay in RL under the pivot, so this doc remains active reference material. | keep visible |
| `RL/docs/rl_integration_contract.md` | active reference | RL/simulator integration contract | It still documents the RL-facing contract with the current simulator path, which remains relevant while V1 stays as oracle/interim support. | keep visible |
| `RL/docs/run_manifest.md` | active reference | Train/eval manifest reference | Run manifests remain an operational and implementation-facing contract during the pivot. | keep visible |
| `RL/docs/runtime_topology.md` | active reference | Current runtime shape reference | It still explains the shipped V1 runtime topology that the pivot is replacing and is useful reference during implementation. | keep visible |
| `RL/docs/transport_and_copy_ledger.md` | historical | Transport/copy diagnostic ledger | It is useful historical evidence from the transport optimization effort, but it is no longer active planning guidance. | keep visible |
| `RL/docs/wandb_logging_contract.md` | active reference | W&B integration contract | Logging/manifests remain part of the RL module contract during the pivot, so this stays relevant reference material. | keep visible |
| `RL/history/workspace_refactor_audit.md` | historical | Workspace refactor audit | It belongs in retained historical context and should not be confused with current architecture or planning docs. | keep visible |
| `fight-caves-RL/CODE_OF_CONDUCT.md` | operational/setup | Repository conduct policy | It is a standard repo policy document and not part of the active planning or architecture surface. | keep as-is |
| `fight-caves-RL/CONTRIBUTING.md` | operational/setup | Repository contribution guide | It is operational contributor guidance, not a current pivot architecture or contract document. | keep as-is |
| `fight-caves-RL/FCplan.md` | archive | Old simulator extraction plan | It is a stepwise pre-pivot execution plan whose planning role is superseded by the workspace pivot docs. | archive candidate |
| `fight-caves-RL/FCspec.md` | historical | Old simulator extraction spec | It still explains the earlier 1:1 extraction effort and simulator boundary, but the pivot supersedes it as workspace authority. | mark as superseded |
| `fight-caves-RL/README.md` | operational/setup | Repo overview and bootstrap | It is an onboarding/setup surface for the simulator repo rather than a current architecture authority. | keep as-is |
| `fight-caves-RL/changelog.md` | historical | Simulator change log | It records prior simulator-side changes and benchmark work but is not an active planning or spec surface. | keep visible |
| `fight-caves-RL/config/README.md` | operational/setup | Config syntax guide | It documents config language usage and is purely operational/setup material. | keep as-is |
| `fight-caves-RL/docs/determinism.md` | active reference | Determinism governance reference | Determinism and replay remain important for oracle/reference behavior and parity work during the pivot. | keep visible |
| `fight-caves-RL/docs/e2e_acceptance.md` | archive | Old end-to-end acceptance gate | It is explicitly anchored to `FCspec.md` and `FCplan.md` completion, so its gating role is superseded by the pivot. | archive candidate |
| `fight-caves-RL/docs/episode_init_contract.md` | active reference | Episode reset contract | Reset semantics remain core to oracle/reference behavior and to parity validation under the pivot. | keep visible |
| `fight-caves-RL/docs/extraction_manifest.md` | archive | Step-1 extraction manifest | It is tightly coupled to the old extraction-step program and no longer needs active prominence under the pivot. | archive candidate |
| `fight-caves-RL/docs/flat_training_observation_schema.md` | active reference | Flat observation contract | The current flat observation schema remains relevant reference material while V1 still exists and V2 contracts are being defined. | keep visible |
| `fight-caves-RL/docs/headless_data_loading.md` | active reference | Headless data-loading reference | The current simulator/oracle-adjacent runtime still uses this headless bootstrap/data-loading behavior, so it remains useful reference material. | keep visible |
| `fight-caves-RL/docs/headless_scripts.md` | active reference | Headless script-loading reference | It still describes current headless script-loading behavior in the simulator/oracle-adjacent path. | keep visible |
| `fight-caves-RL/docs/observation_schema.md` | active reference | Raw observation contract | The current headless observation contract remains important reference material for oracle behavior and parity work. | keep visible |
| `fight-caves-RL/docs/parity_harness.md` | active reference | Simulator parity harness reference | Parity remains central under the pivot, so the existing simulator parity harness doc still matters as reference. | keep visible |
| `fight-caves-RL/docs/raw_flat_equivalence_plan.md` | historical | Old raw-vs-flat gate plan | It documents an older certification gate for the previous flat-path effort and is no longer a top-level active planning document. | mark as superseded |
| `fight-caves-RL/docs/raw_flat_observation_contract.md` | active reference | Raw-vs-flat contract | The current raw/flat observation relationship still matters for the simulator reference path and related parity work. | keep visible |
| `fight-caves-RL/docs/runtime_pruning.md` | active reference | Headless runtime-pruning reference | The simulator/oracle-adjacent headless path still uses these pruning/runtime guard concepts, so the doc remains useful reference material. | keep visible |
| `fight-caves-RL/docs/sim_profiler_report.md` | historical | Simulator profiling report | It remains useful performance evidence from the earlier extraction/optimization effort but is not current planning authority. | keep visible |
| `fight-caves-RL/history/baseline_step0.md` | historical | Step-0 baseline report | It belongs to the retained implementation history for the older extraction plan. | keep visible |
| `fight-caves-RL/history/deletion_candidates.md` | historical | Step-10 deletion inventory | It is historical context from the extraction/pruning workflow and should not be treated as active planning. | keep visible |
| `fight-caves-RL/history/detailed_changelog.md` | historical | Historical implementation log | It remains useful retained history but is not part of the current pivot doc hierarchy. | keep visible |
| `fight-caves-RL/history/performance_report_step11.md` | historical | Step-11 performance report | It is historical extraction-era performance evidence rather than current pivot planning. | keep visible |
| `fight-caves-RL/history/release_candidate_step12.md` | historical | Step-12 release candidate report | It is part of the old step-based implementation history and no longer governs active work. | keep visible |
| `fight-caves-RL/history/repo_prune_report.md` | historical | Step-13 prune report | It remains useful historical context but is not part of the current architecture or plan surface. | keep visible |
| `RSPS/CODE_OF_CONDUCT.md` | operational/setup | Repository conduct policy | It is a standard repo policy document, not a current planning or architecture authority. | keep as-is |
| `RSPS/CONTRIBUTING.md` | operational/setup | Repository contribution guide | It is contributor/setup guidance rather than a current pivot architecture or plan document. | keep as-is |
| `RSPS/README.md` | operational/setup | RSPS repo overview and bootstrap | It is an onboarding/setup surface for the RSPS repo rather than a source-of-truth architecture doc under the pivot. | keep as-is |
| `RSPS/RSPSplan.md` | historical | RSPS module plan | It is a detailed repo plan derived from `RSPSspec.md`, but the pivot implementation plan now owns workspace execution sequencing. | mark as superseded |
| `RSPS/RSPSspec.md` | source of truth | RSPS module spec | It defines the current oracle/reference-only role of the RSPS module under the pivot. | keep as-is |
| `RSPS/changelog.md` | historical | RSPS change log | It records prior RSPS-side changes and doc updates but is not an active planning or contract surface. | keep visible |
| `RSPS/config/README.md` | operational/setup | Config syntax guide | It is operational config documentation and not a current architecture or planning authority. | keep as-is |
| `RSPS/docs/jad_telegraph_plan.md` | active reference | Jad telegraph mechanics reference | It remains useful mechanics-specific reference material for oracle behavior and parity semantics, but it is not the top module authority. | keep visible |

## Required Section: Source-of-Truth Docs
- `pivot_plan.md`: current workspace architecture source of truth for the pivot.
- `pivot_implementation_plan.md`: current workspace execution source of truth for phased implementation.
- `RL/RLspec.md`: current RL module boundary source of truth.
- `RSPS/RSPSspec.md`: current RSPS oracle/reference module boundary source of truth.

These four docs define the current active hierarchy most cleanly: the two root pivot docs own workspace architecture and execution, while the two module specs own module-local scope boundaries.

## Required Section: Active Reference Docs
- Workspace inventories: `CODEBASE_INVENTORY.md`, `CODEBASE_INVENTORY_RSPS.md`.
- RL contract/reference docs: `RL/docs/action_mapping.md`, `bridge_contract.md`, `observation_mapping.md`, `eval_and_replay.md`, `parity_canaries.md`, `run_manifest.md`, `runtime_topology.md`, `rl_integration_contract.md`, `reward_configs.md`, `wandb_logging_contract.md`, `mvp_acceptance.md`.
- Simulator/oracle-adjacent reference docs: `fight-caves-RL/docs/determinism.md`, `episode_init_contract.md`, `flat_training_observation_schema.md`, `headless_data_loading.md`, `headless_scripts.md`, `observation_schema.md`, `parity_harness.md`, `raw_flat_observation_contract.md`, `runtime_pruning.md`.
- RSPS mechanic reference doc: `RSPS/docs/jad_telegraph_plan.md`.

These docs should remain easy to find because they still help implementation work even though they are not the top planning authority.

## Required Section: Historical Docs
- Root historical context: `optimization_history.md`, `trainer_architecture_decision_memo.md`.
- RL historical plans/reports: `RL/RLplan.md`, `RL/changelog.md`, `RL/docs/benchmark_matrix.md`, `flat_observation_ingestion.md`, `hotpath_map.md`, `logging_overhead_report.md`, `observation_action_cost_report.md`, `parity_safe_optimization_rules.md`, `performance_decomposition_report.md`, `phase2_blocker_diagnosis.md`, `production_fast_trainer_benchmark.md`, `production_trainer_prototype_scope.md`, `python_profiler_report.md`, `transport_and_copy_ledger.md`, `RL/history/workspace_refactor_audit.md`.
- fight-caves-RL historical context: `fight-caves-RL/FCspec.md`, `fight-caves-RL/changelog.md`, `fight-caves-RL/docs/raw_flat_equivalence_plan.md`, `fight-caves-RL/docs/sim_profiler_report.md`, and all current docs under `fight-caves-RL/history/`.
- RSPS historical docs: `RSPS/RSPSplan.md`, `RSPS/changelog.md`.

These docs remain worth keeping because they preserve prior decisions, prior benchmark evidence, and implementation history that still explains the present workspace shape.

## Required Section: Archive Candidates
- `optimization_plan.md`
- `optimization_work_chunks.md`
- `RL/docs/performance_plan.md`
- `RL/docs/phase1_decision_gate.md`
- `RL/docs/phase2_transport_gate.md`
- `fight-caves-RL/FCplan.md`
- `fight-caves-RL/docs/e2e_acceptance.md`
- `fight-caves-RL/docs/extraction_manifest.md`

These are the strongest archive candidates because their planning or gating role is directly superseded by the pivot and they no longer need equal prominence in the active documentation hierarchy.

## Required Section: Operational / Setup Docs
- `RL/README.md`
- `fight-caves-RL/README.md`
- `fight-caves-RL/config/README.md`
- `fight-caves-RL/CONTRIBUTING.md`
- `fight-caves-RL/CODE_OF_CONDUCT.md`
- `RSPS/README.md`
- `RSPS/config/README.md`
- `RSPS/CONTRIBUTING.md`
- `RSPS/CODE_OF_CONDUCT.md`

These docs should remain available for onboarding, repo usage, contribution flow, and config syntax, but they should not be confused with planning or architecture sources of truth.

## Required Section: Overlap / Supersession Notes
- Pre-pivot optimization docs such as `optimization_plan.md`, `optimization_work_chunks.md`, `RL/docs/performance_plan.md`, and `RL/docs/phase2_transport_gate.md` overlap with the newer pivot docs but are no longer equal planning authorities.
- `RL/RLplan.md` overlaps with `pivot_implementation_plan.md`; the RL plan remains useful context, but the root pivot implementation plan now owns current sequencing across repos.
- `fight-caves-RL/FCplan.md`, `fight-caves-RL/FCspec.md`, and extraction-era simulator docs overlap with the newer pivot architecture; they still explain prior simulator work, but they no longer define the workspace-level plan.
- `RSPS/RSPSplan.md` overlaps with `RSPS/RSPSspec.md`; the spec remains the active module boundary authority while the plan becomes retained historical context.
- `fight-caves-RL/FCplan.md` and `fight-caves-RL/FCspec.md` are not equivalent: the plan is the stronger archive candidate, while the spec still has historical value because it explains the earlier extraction model.
- `RSPS/docs/jad_telegraph_plan.md` still contains source-of-truth language inside the doc, but under the pivot it fits best as mechanics-specific active reference rather than top-level module authority.
- `fight-caves-RL/docs/e2e_acceptance.md` and related simulator acceptance/extraction gates overlap with the pivot’s newer mechanics-parity and phased implementation model.

## Required Section: Proposed Later Actions For Approval

### keep prominent
- Keep `pivot_plan.md`, `pivot_implementation_plan.md`, `RL/RLspec.md`, and `RSPS/RSPSspec.md` as the clearest top-level authorities.
- Keep the main RL contract/reference docs visible during implementation.
- Keep the current simulator/oracle contract docs visible where they still describe active reference behavior.

### keep but de-emphasize
- Keep historical optimization reports, profiler reports, transport ledgers, inventories, and changelogs available but not equally prominent with the pivot docs.
- Keep `fight-caves-RL/FCspec.md` visible as historical context without treating it as current workspace authority.
- Keep `RSPS/RSPSplan.md` and `RL/RLplan.md` accessible as older module plans, but not as active execution docs.

### mark superseded
- Mark `RL/RLplan.md` as superseded by `pivot_implementation_plan.md`.
- Mark `RSPS/RSPSplan.md` as superseded by `RSPS/RSPSspec.md` plus the root pivot docs.
- Mark `fight-caves-RL/FCspec.md` and `fight-caves-RL/docs/raw_flat_equivalence_plan.md` as superseded for active planning purposes.

### archive later if approved
- `optimization_plan.md`
- `optimization_work_chunks.md`
- `RL/docs/performance_plan.md`
- `RL/docs/phase1_decision_gate.md`
- `RL/docs/phase2_transport_gate.md`
- `fight-caves-RL/FCplan.md`
- `fight-caves-RL/docs/e2e_acceptance.md`
- `fight-caves-RL/docs/extraction_manifest.md`

### review further before deciding
- Whether `fight-caves-RL/FCspec.md` should remain historical or become active reference once V2 fast-kernel docs land.
- Whether `RL/docs/mvp_acceptance.md` should stay active reference until a pivot-native acceptance doc replaces it.
- Whether any simulator raw-vs-flat docs should stay active reference after the V2 mechanics contract is frozen and implemented.
- How the future `fight-caves-demo-lite` doc surface should be introduced once the module exists.

## Open Questions
- `fight-caves-demo-lite` is part of the pivot architecture, but there is currently no module or documentation surface for it in the workspace. A future doc triage pass will need to place its README/spec/contract docs once they exist.
- `fight-caves-RL/FCspec.md` still contains useful simulator boundary context, but its original 1:1 extraction framing no longer matches the workspace-level pivot authority.
- `RSPS/docs/jad_telegraph_plan.md` still uses source-of-truth language internally even though it is best triaged as active reference under the broader pivot hierarchy.
- `RL/docs/mvp_acceptance.md` is still implementation-relevant today, but its long-term status depends on how the pivot-specific acceptance gate is documented later.
