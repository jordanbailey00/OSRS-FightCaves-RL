# Optimization History

Append-only record of optimization updates, decisions, benchmarks, and pivots across the workspace.

This file is the historical companion to:
- [optimization_plan.md](/home/jordan/code/optimization_plan.md)
- [optimization_work_chunks.md](/home/jordan/code/optimization_work_chunks.md)

## 2026-03-10

### Established optimization program
- Created the workspace-level optimization plan and work-chunk tracker.
- Locked the design-center target as `>= 100,000` env steps/sec.
- Kept `>= 1,000,000` env steps/sec as a stretch goal only.

### Completed Phase 0
- Hardened the Phase 0 measurement gate.
- Established native Linux as the performance source of truth.
- Added clean standalone sim benchmark and profiler entrypoints.
- Published native-Linux Phase 0 gate artifacts and unblocked Phase 1.

### Completed Phase 1
- Defined the raw-vs-flat contract and ownership model.
- Added the first flat training observation schema design.
- Implemented the first sim-owned flat observation path.
- Closed the native-Linux Phase 1 gate with a successful bridge/vecenv improvement result.

### Phase 2 pivot
- Prototyped a lower-copy subprocess transport path.
- Confirmed that transport-only gains did not survive the native-Linux end-to-end training gate.
- Added a learner-ceiling benchmark and confirmed the trainer path is the active bottleneck after Phase 1.
- Approved a trainer-redesign review batch instead of continuing small transport suppressions.
- Corrected the benchmark contract:
  - production train throughput now excludes `final_evaluate`
  - learner-ceiling remains diagnostic-only
  - future Phase 2 gate reruns must use the corrected production metric rather than the older mixed train row
- Added benchmark-safe trainer instrumentation:
  - current local dominant buckets are now named explicitly
  - `eval_policy_forward` and `eval_env_recv` lead the evaluate path
  - `train_backward` and `train_policy_forward` lead the PPO update path
- Froze the production fast-trainer benchmark definition:
  - native-Linux `16 env` and `64 env` disabled rows are the canonical Phase 2 target
  - learner-ceiling rows remain diagnostic companions only
  - certification-only responsibilities are now explicitly separated from the benchmark hot path

### Jad telegraph parity rework
- Formalized Jad telegraph as authoritative shared combat state in the sim/oracle code paths.
- Exposed the Jad telegraph cue to headless observations without introducing an oracle field.
- Added replay/parity coverage so the cue remains aligned across headed and headless representations.

### Documentation prune and consolidation
- Added this root historical optimization log as the append-only optimization history surface.
- Moved module historical docs into `history/` directories so active source-of-truth docs stay focused.
- Consolidated `fight-caves-RL` active sim-performance references around:
  - `/home/jordan/code/fight-caves-RL/docs/sim_profiler_report.md`
- Relocated generated sim benchmark artifacts and historical extraction/prune docs out of `fight-caves-RL/docs/`.
- Renamed the active Fight Caves end-to-end acceptance checklist to:
  - `/home/jordan/code/fight-caves-RL/docs/e2e_acceptance.md`
- Retired the placeholder RL sweep config:
  - `/home/jordan/code/RL/configs/sweep/ppo_sweep_v0.yaml`
