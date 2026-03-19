# Project Documentation Tree

## Legend
- `source of truth`
- `active reference`
- `historical`
- `archive`
- `operational/setup`

## Workspace Root
- `archive/CODEBASE_INVENTORY.md` — `archive` — archived workspace structure inventory
- `archive/CODEBASE_INVENTORY_RSPS.md` — `archive` — archived RSPS structure inventory
- `archive/codebase_inventory.csv` — `archive` — archived workspace inventory artifact
- `archive/codebase_inventory_rsps.csv` — `archive` — archived RSPS inventory artifact
- `archive/optimization_plan.md` — `archive` — archived pre-pivot workspace plan
- `archive/optimization_work_chunks.md` — `archive` — archived pre-pivot work tracker
- `archive/trainer_architecture_decision_memo.md` — `archive` — archived pre-pivot architecture memo
- `optimization_history.md` — `historical` — optimization program history log
- `pivot_documentation_triage.md` — `active reference` — approved documentation triage record
- `pivot_implementation_plan.md` — `source of truth` — current phased pivot execution plan
- `pivot_plan.md` — `source of truth` — current pivot architecture and role split
- `project_tree.md` — `active reference` — current documentation layout map

## RL
- `RL/README.md` — `operational/setup` — RL repo overview and bootstrap guide
- `RL/RLplan.md` — `historical` — older RL module execution plan
- `RL/RLspec.md` — `source of truth` — RL module boundary and ownership spec
- `RL/changelog.md` — `historical` — RL change log

## RL/docs
- `RL/docs/action_mapping.md` — `active reference` — RL action contract reference
- `RL/docs/benchmark_matrix.md` — `historical` — optimization-era benchmark packet
- `RL/docs/bridge_contract.md` — `active reference` — RL bridge/runtime contract
- `RL/docs/eval_and_replay.md` — `active reference` — replay and eval contract
- `RL/docs/flat_observation_ingestion.md` — `historical` — old flat-ingestion design
- `RL/docs/hotpath_map.md` — `historical` — current V1 hot path map
- `RL/docs/logging_overhead_report.md` — `historical` — logging cost report
- `RL/docs/mvp_acceptance.md` — `active reference` — current RL acceptance-gate reference
- `RL/docs/observation_action_cost_report.md` — `historical` — action and observation cost report
- `RL/docs/observation_mapping.md` — `active reference` — RL observation contract reference
- `RL/docs/parity_canaries.md` — `active reference` — parity canary reference
- `RL/docs/parity_safe_optimization_rules.md` — `historical` — optimization safety rules
- `RL/docs/performance_decomposition_report.md` — `historical` — performance evidence report
- `RL/docs/phase2_blocker_diagnosis.md` — `historical` — Phase 2 blocker analysis
- `RL/docs/production_fast_trainer_benchmark.md` — `historical` — prototype benchmark target
- `RL/docs/production_trainer_prototype_scope.md` — `historical` — prototype scope memo
- `RL/docs/python_profiler_report.md` — `historical` — Python profiling report
- `RL/docs/reward_configs.md` — `active reference` — RL reward and curriculum contract
- `RL/docs/rl_integration_contract.md` — `active reference` — RL and simulator integration contract
- `RL/docs/run_manifest.md` — `active reference` — run-manifest reference
- `RL/docs/runtime_topology.md` — `active reference` — current runtime topology reference
- `RL/docs/transport_and_copy_ledger.md` — `historical` — transport and copy ledger
- `RL/docs/wandb_logging_contract.md` — `active reference` — W&B logging contract

## RL/docs/archive
- `RL/docs/archive/performance_plan.md` — `archive` — archived superseded performance plan
- `RL/docs/archive/phase1_decision_gate.md` — `archive` — archived superseded Phase 1 gate
- `RL/docs/archive/phase2_transport_gate.md` — `archive` — archived superseded transport gate

## RL/history
- `RL/history/workspace_refactor_audit.md` — `historical` — workspace refactor audit record

## fight-caves-demo-lite
- `fight-caves-demo-lite/DEMOspec.md` — `active reference` — placeholder demo-lite module spec scaffold
- `fight-caves-demo-lite/README.md` — `operational/setup` — placeholder demo-lite repo overview scaffold

## fight-caves-RL
- `fight-caves-RL/README.md` — `operational/setup` — simulator repo overview and bootstrap
- `fight-caves-RL/changelog.md` — `historical` — simulator change log
- `fight-caves-RL/config/README.md` — `operational/setup` — config syntax guide

## fight-caves-RL/archive
- `fight-caves-RL/archive/CODE_OF_CONDUCT.md` — `archive` — archived repository conduct policy
- `fight-caves-RL/archive/CONTRIBUTING.md` — `archive` — archived repository contribution guide
- `fight-caves-RL/archive/FCplan.md` — `archive` — archived superseded simulator extraction plan
- `fight-caves-RL/archive/FCspec.md` — `archive` — archived superseded simulator extraction spec

## fight-caves-RL/docs
- `fight-caves-RL/docs/determinism.md` — `active reference` — determinism and RNG reference
- `fight-caves-RL/docs/episode_init_contract.md` — `active reference` — episode reset contract
- `fight-caves-RL/docs/flat_training_observation_schema.md` — `active reference` — flat observation schema reference
- `fight-caves-RL/docs/headless_data_loading.md` — `active reference` — headless data-loading reference
- `fight-caves-RL/docs/headless_scripts.md` — `active reference` — headless script-loading reference
- `fight-caves-RL/docs/observation_schema.md` — `active reference` — raw observation schema reference
- `fight-caves-RL/docs/parity_harness.md` — `active reference` — simulator parity harness reference
- `fight-caves-RL/docs/raw_flat_equivalence_plan.md` — `historical` — old raw-vs-flat gate plan
- `fight-caves-RL/docs/raw_flat_observation_contract.md` — `active reference` — raw-vs-flat observation contract
- `fight-caves-RL/docs/runtime_pruning.md` — `active reference` — headless runtime-pruning reference
- `fight-caves-RL/docs/sim_profiler_report.md` — `historical` — simulator profiling report

## fight-caves-RL/docs/archive
- `fight-caves-RL/docs/archive/e2e_acceptance.md` — `archive` — archived superseded end-to-end acceptance gate
- `fight-caves-RL/docs/archive/extraction_manifest.md` — `archive` — archived superseded extraction manifest

## fight-caves-RL/history
- `fight-caves-RL/history/baseline_step0.md` — `historical` — Step-0 baseline report
- `fight-caves-RL/history/deletion_candidates.md` — `historical` — Step-10 deletion candidate inventory
- `fight-caves-RL/history/detailed_changelog.md` — `historical` — detailed historical change log
- `fight-caves-RL/history/performance_report_step11.md` — `historical` — Step-11 performance report
- `fight-caves-RL/history/release_candidate_step12.md` — `historical` — Step-12 release candidate report
- `fight-caves-RL/history/repo_prune_report.md` — `historical` — Step-13 prune report

## RSPS
- `RSPS/README.md` — `operational/setup` — RSPS repo overview and bootstrap
- `RSPS/RSPSspec.md` — `source of truth` — RSPS oracle/reference module spec
- `RSPS/changelog.md` — `historical` — RSPS change log
- `RSPS/config/README.md` — `operational/setup` — config syntax guide

## RSPS/archive
- `RSPS/archive/CODE_OF_CONDUCT.md` — `archive` — archived repository conduct policy
- `RSPS/archive/CONTRIBUTING.md` — `archive` — archived repository contribution guide
- `RSPS/archive/RSPSplan.md` — `archive` — archived superseded RSPS module plan

## RSPS/docs
- `RSPS/docs/jad_telegraph_plan.md` — `active reference` — Jad telegraph mechanics reference

## RSPS/history

## Summary
- `source of truth`: 4
- `active reference`: 24
- `historical`: 26
- `archive`: 19
- `operational/setup`: 6
