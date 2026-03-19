# post_pivot_implementation_plan.md

## Post-Pivot Execution Summary

The pivot is complete. This implementation plan covers the next execution period:

- benchmark truth
- training readiness
- trainer/policy compute-cost attribution and bounded pre-learning enablement
- learning proof
- train-to-headed-demo proof
- targeted performance hardening
- cleanup/refactor only after proof

The system should now be treated as stable enough to measure and prove, not as architecture that still needs broad convergence work.

## Execution Rules

- only completed work should be marked completed
- benchmark truth must come before performance optimization
- bounded trainer/policy compute-cost enablement is allowed before learning proof only when benchmark truth shows trainer/model compute is the dominant blocker on the default path
- learning proof must come before cleanup/refactor
- headed demo proof must use real trained checkpoints, not human UI play or scripted traces alone
- fallback/reference paths stay preserved until the cleanup authorization gate is passed
- targeted parity audits should only cover transfer-critical semantics

## Canonical Benchmark Methodology

Until explicitly revised in a later approved PR, the canonical post-pivot benchmark starting point is:

- env-only benchmark profile:
  - `/home/jordan/code/RL/configs/benchmark/fast_env_v2.yaml`
- training benchmark profile:
  - `/home/jordan/code/RL/configs/benchmark/fast_train_v2.yaml`

Canonical methodology:

- source-of-truth environment: approved Linux/WSL host
- env-count sweep: at minimum `4,16,64`
- repeat count: at least `3` runs per important row before making claims
- required recorded fields:
  - `env_steps_per_second`
  - `production_env_steps_per_second`
  - `wall_clock_env_steps_per_second`
  - `vecenv_topology`
  - `runner_stage_seconds`
  - `trainer_bucket_totals`
  - `memory_profile`
  - repo SHAs and config ids

Operational interpretation of `~500,000 SPS`:

- this means end-to-end training throughput on the default V2 training path
- use `production_env_steps_per_second` from the canonical training benchmark family as the headline number
- env-only SPS is a separate supporting gate, not a substitute for the training gate

## Phase 9 - Training Readiness, Benchmark Truth, And Pre-Learning Compute-Cost Enablement
- Execution status: `completed`
- Phase objective: measure the real current V2 system, define the canonical measurement methodology, ensure the default training path is instrumented well enough for learning proof, and remove clearly measured trainer/policy compute waste before Phase 10 canaries begin.

### PR 9.1 - Freeze Post-Pivot Benchmark Methodology And Produce The Current Baseline Packet
- Status: `completed` (2026-03-16)
- Objective: establish the canonical benchmark profiles, commands, packet schema, and first post-pivot baseline packet for the default V2 path.
- Why it belongs here: no later performance claim or optimization should happen without a frozen measurement contract.
- Exact files/directories/modules likely touched:
  - `/home/jordan/code/RL/scripts/benchmark_env.py`
  - `/home/jordan/code/RL/scripts/benchmark_train.py`
  - `/home/jordan/code/RL/scripts/run_phase9_baseline_packet.py`
  - `/home/jordan/code/RL/configs/benchmark/fast_env_v2.yaml`
  - `/home/jordan/code/RL/configs/benchmark/fast_train_v2.yaml`
  - `/home/jordan/code/RL/docs/phase9_pr91_baseline.md`
  - `/home/jordan/code/system_acceptance_gates.md`
- Work items:
  - freeze the canonical env-only and training benchmark methodology
  - define the approved env-count sweep and replicate policy
  - run the current default V2 benchmark packet
  - record the exact gap to `1,000,000 env-only SPS` and `500,000 training SPS`
- Deliverables:
  - post-pivot baseline packet
  - benchmark methodology note/update
  - measured gap summary
- Evidence required:
  - artifact packet with env-only and training results
  - benchmark metadata including topology, host class, SHAs, and config ids
- Acceptance criteria:
  - canonical benchmark methodology is explicit and reviewable
  - current default V2 env-only and training throughput are measured honestly
  - the gap to the long-term throughput targets is quantified
- Not allowed to defer:
  - separating env-only from end-to-end training throughput
  - recording topology and stage-bucket evidence
- Optional/later:
  - deeper profiler integrations
- Dependencies:
  - pivot completion through PR 8.2
- Completion evidence (2026-03-16):
  - baseline packet:
    - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr91_baseline_20260316T180000Z/phase9_pr91_baseline_packet.json`
  - baseline note:
    - `/home/jordan/code/RL/docs/phase9_pr91_baseline.md`
  - measured headline gap:
    - env-only peak median SPS: `25092.86` vs target `1000000.00`
    - training peak median SPS (disabled logging): `73.43` vs target `500000.00`
  - execution note:
    - canonical `4,16,64` x `3` repeats completed on approved Linux/WSL host using the frozen V2 benchmark configs

### PR 9.2 - Produce Ranked Default-V2 Performance Decomposition
- Status: `completed` (2026-03-16)
- Objective: identify the actual dominant bottlenecks on the current default V2 path.
- Why it belongs here: optimization work must be ranked, not speculative.
- Exact files/directories/modules likely touched:
  - `/home/jordan/code/RL/docs/performance_decomposition_report.md`
  - `/home/jordan/code/RL/fight_caves_rl/puffer/trainer.py`
  - `/home/jordan/code/RL/fight_caves_rl/benchmarks/*`
  - any profiler helper scripts needed for reproducible attribution
- Work items:
  - separate env/runtime cost from trainer/update cost
  - compare benchmark runs against learner-ceiling or fake-env diagnostics where useful
  - rank bottlenecks by measured cost share
  - identify which bottlenecks are blocking `~500,000 SPS`
- Deliverables:
  - ranked bottleneck report
  - updated decomposition artifacts
- Evidence required:
  - stage-bucket comparison tables
  - hotspot ranking tied to real benchmark rows
- Acceptance criteria:
  - dominant bottlenecks are identified concretely
  - future optimization work can point to a ranked cost list instead of intuition
- Not allowed to defer:
  - explicit ranking of the top bottlenecks
  - explicit statement of whether the trainer loop, runtime, or transport dominates
- Optional/later:
  - native-kernel escalation discussion
- Dependencies:
  - PR 9.1
- Completion evidence (2026-03-16):
  - actual touched files:
    - `/home/jordan/code/RL/scripts/analyze_phase9_baseline_packet.py`
    - `/home/jordan/code/RL/docs/phase9_pr92_decomposition.md`
    - `/home/jordan/code/RL/docs/performance_decomposition_report.md`
    - `/home/jordan/code/system_acceptance_gates.md`
  - decomposition note:
    - `/home/jordan/code/RL/docs/phase9_pr92_decomposition.md`
  - decomposition artifact:
    - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr92_decomposition_20260316T182300Z.json`
  - ranked outcome:
    - runner stage dominance is `evaluate_seconds` then `train_seconds` on disabled rows
    - trainer bucket dominance is `eval_total`, `eval_policy_forward`, and `eval_env_recv`
    - env-only hot path still shows send/step costs, but trainer/eval remains the primary training-gate blocker
  - MUST REVISIT:
    - `scripts/benchmark_train_ceiling.py` currently fails on `configs/benchmark/fast_train_v2.yaml` with an observation-shape mismatch (`64x536` vs `134x128`)
    - reproduced failure log:
      - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr92_train_ceiling_fast_train_v2_failure_20260316T183200Z.log`
    - fallback ceiling diagnostics were run with `configs/benchmark/train_1024env_v0.yaml` for non-blocking supporting context

### PR 9.3 - Lock Training Readiness, Metrics, And Artifact Contract
- Status: `completed` (2026-03-16)
- Objective: make the default V2 training path operationally ready for bounded learning runs.
- Why it belongs here: learning runs are hard to interpret unless config, eval, and logging surfaces are explicit.
- Exact files/directories/modules likely touched:
  - `/home/jordan/code/RL/configs/train/smoke_fast_v2.yaml`
  - `/home/jordan/code/RL/configs/train/train_fast_v2.yaml`
  - `/home/jordan/code/RL/fight_caves_rl/rewards/*`
  - `/home/jordan/code/RL/fight_caves_rl/curriculum/*`
  - `/home/jordan/code/RL/docs/default_backend_selection.md`
  - `/home/jordan/code/system_acceptance_gates.md`
- Work items:
  - verify default reward/curriculum/checkpoint assumptions
  - define canonical training metrics and dashboards
  - define what artifacts every learning run must emit
  - define what counts as "learning is happening" at the next phase
- Deliverables:
  - training readiness checklist
  - metrics/dashboards definition
  - run artifact contract for learning runs
- Evidence required:
  - short readiness run manifests
  - doc updates for metrics and artifacts
- Acceptance criteria:
  - the default V2 train path is ready for bounded learning runs
  - training metrics and checkpoint promotion signals are explicit
- Not allowed to defer:
  - checkpoint/eval artifact expectations
  - fixed learning metrics for the next phase
- Optional/later:
  - richer dashboard polish
- Dependencies:
  - PR 9.1
  - PR 9.2
- Completion evidence (2026-03-16):
  - readiness note:
    - `/home/jordan/code/RL/docs/phase9_pr93_training_readiness.md`
  - readiness artifacts:
    - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr93_readiness_20260316T184000Z/train_summary.json`
    - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr93_readiness_20260316T184000Z/eval_summary.json`
    - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr93_readiness_20260316T184000Z/learning_eval_summary.json`
  - readiness manifests:
    - `/home/jordan/code/RL/artifacts/train/smoke_fast_v2/runs/fc-rl-train-1773686282-84bcf56d94db/run_manifest.json`
    - `/home/jordan/code/RL/artifacts/eval/replay_eval_v0/fc-rl-eval-1773686304-9787d154419f/run_manifest.json`
  - frozen Phase 10 readiness contract:
    - fixed learning metric payload: `learning_eval_summary_v1`
    - fixed "learning is happening" ladder rule captured in the readiness note
  - MUST REVISIT carried forward:
    - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr92_train_ceiling_fast_train_v2_failure_20260316T183200Z.log`

### PR 9.4 - Freeze Post-Flattening Hot-Path Attribution For The Default V2 Path
- Status: `completed` (2026-03-16)
- Objective: identify whether the current default V2 training bottleneck is trainer/model compute, env worker hot path, or transport/boundary overhead after the structural flattening pass.
- Why it belongs here: PR 9.2 proved that trainer/eval dominated the training gate at a ranked-bucket level, but the next decision needed a tighter post-flattening attribution packet before any bounded pre-learning optimization could be justified.
- Exact files/directories/modules likely touched:
  - `/home/jordan/code/runescape-rl/training-env/rl/profile_hotpath_attribution.py`
  - `/home/jordan/code/runescape-rl/docs/perf_hotpath_attribution.md`
  - `/home/jordan/code/runescape-rl-runtime/reports/perf/*`
  - any trainer-side timing hooks required for reproducible stage attribution
- Work items:
  - instrument trainer-side forward, backward, wait, and send stages
  - instrument env worker hot-path buckets on the default V2 path
  - compare subprocess/default against embedded topology to isolate transport effects
  - produce explicit answers for whether trainer compute, env core, or transport is the dominant blocker
- Deliverables:
  - post-flattening hot-path attribution report
  - machine-readable attribution packet(s)
  - ranked hotspot table and worker-skew packet
- Evidence required:
  - ranked hotspot report tied to a bounded reproducible packet
  - trainer vs env vs transport split
  - policy-forward vs backward/update split
  - worker skew and barrier-tail analysis
- Acceptance criteria:
  - the dominant training-path bottleneck is identified concretely
  - the report explicitly states whether the trainer is mostly computing or mostly waiting
  - future bounded optimization work can point to measured hotspot shares rather than theory
- Not allowed to defer:
  - explicit answer on whether policy forward is a primary blocker
  - explicit answer on whether env recv/send is a primary blocker
  - explicit answer on whether env hot-path compute is primary or secondary
- Optional/later:
  - deeper per-function profiler work inside the chosen trainer/policy hotspot
- Dependencies:
  - PR 9.1
  - PR 9.2
  - PR 9.3
- Completion evidence (2026-03-16):
  - attribution report:
    - `/home/jordan/code/runescape-rl/docs/perf_hotpath_attribution.md`
  - attribution packets:
    - `/home/jordan/code/runescape-rl-runtime/reports/perf/hotpath_attribution_20260316T214936Z/perf_hotpath_attribution.json`
    - `/home/jordan/code/runescape-rl-runtime/reports/perf/hotpath_attribution_20260316T215137Z/perf_hotpath_attribution.json`
  - ranked outcome:
    - `eval_policy_forward` is the single largest hotspot
    - `train_backward` is the second-largest hotspot
    - `train_policy_forward` is the third-largest hotspot
    - trainer policy + update compute dominates wall time
    - env worker hot path and send/serialize overhead are secondary
  - embedded comparison outcome:
    - embedded vs subprocess only improved throughput modestly, confirming transport is not the first-order blocker

### PR 9.5 - Trainer/Policy Compute-Cost Enablement Sweep
- Status: `completed` (2026-03-19)
- Objective: reduce trainer/policy compute cost enough that Phase 10 learning canaries run on the cheapest viable default V2 training configuration rather than an already-measured uneconomic default.
- Why it belongs here: PR 9.4 shows the current default V2 path is dominated by trainer-side model forward/backward compute, not env core or transport. A bounded pre-learning enablement sweep is therefore justified before Phase 10 so learning proof is not attempted on a knowingly suboptimal trainer/policy surface.
- Exact files/directories/modules likely touched:
  - `/home/jordan/code/runescape-rl/training-env/rl/configs/train/*`
  - `/home/jordan/code/runescape-rl/training-env/rl/configs/benchmark/fast_train_v2.yaml`
  - `/home/jordan/code/runescape-rl/training-env/rl/fight_caves_rl/policies/*`
  - `/home/jordan/code/runescape-rl/training-env/rl/fight_caves_rl/puffer/trainer.py`
  - `/home/jordan/code/runescape-rl/training-env/rl/fight_caves_rl/puffer/production_trainer.py`
  - `/home/jordan/code/runescape-rl/training-env/rl/fight_caves_rl/policies/registry.py`
  - `/home/jordan/code/runescape-rl/docs/perf_hotpath_attribution.md`
  - any new bounded sweep notes/artifacts needed for the selected default config
- Work items:
  - compare the current default policy against at least one cheaper viable candidate under the same bounded attribution packet
  - sweep trainer/update-economics knobs at fixed topology, including minibatch/BPTT/update cadence where relevant
  - keep env backend, transport mode, info payload mode, reward path, and evaluation surface fixed while reducing trainer/model cost
  - choose the cheapest viable default config for Phase 10 canaries
  - rerun the attribution packet on the selected config and record before/after movement
- Deliverables:
  - trainer/policy compute-cost sweep report
  - selected Phase 10 default training config
  - before/after attribution packet and hotspot comparison
- Evidence required:
  - side-by-side packet comparison against the PR 9.4 baseline
  - measured training SPS change on the same bounded profile family
  - hotspot-share comparison showing whether policy/update cost was reduced materially
  - explicit statement of the selected default config for Phase 10
- Acceptance criteria:
  - a Phase 10 default config is selected explicitly rather than implicitly inherited
  - the selected config materially reduces the dominant trainer/model compute share and/or improves bounded training throughput
  - no training-readiness contract from PR 9.3 is silently broken
- Not allowed to defer:
  - explicit config-selection decision for Phase 10
  - before/after evidence tied to the same attribution packet family
  - explicit statement of whether the sweep changed the ranked hotspot order
- Optional/later:
  - deeper architecture changes
  - broader throughput hardening beyond the bounded trainer/policy surface
- Dependencies:
  - PR 9.4
- Completion evidence (2026-03-19):
  - sweep report:
    - `/home/jordan/code/runescape-rl/docs/reports/phase9_pr95_compute_sweep.md`
  - bounded benchmark artifacts:
    - `/home/jordan/code/runescape-rl-runtime/reports/perf/pr95_fast_train_v2_baseline_1024.json`
    - `/home/jordan/code/runescape-rl-runtime/reports/perf/pr95_fast_train_v2_lstm64_1024.json`
    - `/home/jordan/code/runescape-rl-runtime/reports/perf/pr95_fast_train_v2_mlp128_1024.json`
    - `/home/jordan/code/runescape-rl-runtime/reports/perf/pr95_fast_train_v2_mlp64_1024.json`
  - before/after attribution packets:
    - `/home/jordan/code/runescape-rl-runtime/reports/perf/pr95_hotpath_fast_train_v2_2048/perf_hotpath_attribution.json`
    - `/home/jordan/code/runescape-rl-runtime/reports/perf/pr95_hotpath_fast_train_v2_mlp64_2048/perf_hotpath_attribution.json`
  - selected Phase 10 default:
    - train config: `/home/jordan/code/runescape-rl/training-env/rl/configs/train/train_fast_v2_mlp64.yaml`
    - benchmark config: `/home/jordan/code/runescape-rl/training-env/rl/configs/benchmark/fast_train_v2_mlp64.yaml`
  - rejected candidates:
    - `fast_train_v2_lstm64` regressed vs baseline (`56.96 SPS` vs `61.51 SPS`)
    - `fast_train_v2_mlp128` regressed vs baseline (`57.28 SPS` vs `61.51 SPS`)
  - selected-candidate outcome:
    - `fast_train_v2_mlp64` improved bounded train SPS to `64.88` vs baseline `61.51` (`+5.47%`)
    - paired attribution throughput improved to `89.67 SPS` vs baseline `83.09 SPS` (`+7.91%`)
    - trainer policy-forward seconds dropped from `14.236s` to `13.736s`
    - trainer update-compute seconds dropped from `7.504s` to `6.778s`
    - hotspot order stayed the same: `eval_policy_forward`, `train_backward`, `eval_env_recv`, `subprocess_parent_recv_wait`
  - contract note:
    - PR 9.3 env/reward/curriculum/metric/artifact assumptions remain intact
    - the selected Phase 10 config explicitly changes the policy family from `lstm_v0`/`use_rnn=true` to `mlp_v0`/`use_rnn=false`

## Phase 10 - Learning Proof
- Execution status: `planned`
- Phase objective: prove that the default V2 system can learn meaningful Fight Caves behavior on the selected cheapest viable training configuration established at the end of Phase 9.

### PR 10.1 - Early-Wave Learning Canary Runs
- Status: `planned`
- Objective: prove the agent improves beyond trivial or random opening behavior.
- Why it belongs here: this is the first proof that training signal and policy updates are actually useful, and it should be run on the explicitly selected Phase 9 default rather than a superseded trainer/policy surface.
- Exact files/directories/modules likely touched:
  - training configs
  - eval/replay configs
  - W&B/reporting docs
  - learning-run artifact directories
- Work items:
  - run bounded training canaries on the selected post-PR-9.5 default V2 path
  - compare against a random or no-op baseline where useful
  - evaluate checkpoint ladders on a fixed seed pack
- Deliverables:
  - early-learning run packet
  - checkpoint ladder table
  - failure mode notes if learning stalls
- Evidence required:
  - train manifests
  - eval summaries
  - replay artifacts for representative checkpoints
- Acceptance criteria:
  - the agent beats trivial/random behavior on the fixed evaluation surface
  - early-wave progression is repeatable, not a one-off cherry-picked run
- Not allowed to defer:
  - a fixed eval surface
  - explicit baseline comparison
- Optional/later:
  - long-run tuning
- Dependencies:
  - Phase 9 completion
  - explicit Phase 10 default-config selection from PR 9.5

### PR 10.2 - Mid-Wave And Later-Wave Learning Proof Ladder
- Status: `planned`
- Objective: move beyond early-wave proof into materially later-wave behavior.
- Why it belongs here: the system needs a promotable checkpoint before headed-demo proof has value.
- Work items:
  - define the checkpoint promotion ladder
  - run longer bounded training runs
  - measure progress on mid-wave and later-wave milestones
- Deliverables:
  - checkpoint promotion rules
  - mid/late-wave evaluation packet
- Evidence required:
  - fixed-seed evaluation ladder
  - reproducible checkpoint table with wave metrics
- Acceptance criteria:
  - later-wave progress is visible on the fixed evaluation surface
  - a promotable checkpoint exists for headed transfer proof
- Not allowed to defer:
  - explicit checkpoint promotion thresholds
  - later-wave evidence beyond the opening waves
- Optional/later:
  - full completion push
- Dependencies:
  - PR 10.1

### PR 10.3 - Jad Reach And First Completion Push
- Status: `planned`
- Objective: push from later-wave competence toward Jad reach and first completion.
- Why it belongs here: completion remains the real task goal, but it should build on already-proven learning rather than replace it.
- Work items:
  - run longer training experiments with promoted configs/checkpoints
  - track Jad reach and first completion separately
  - classify failure modes near Jad/completion
- Deliverables:
  - Jad-reach packet
  - first-completion evidence if achieved
  - ranked late-game failure list if not yet complete
- Evidence required:
  - held-out evaluation summaries
  - replay artifacts for Jad/completion attempts
- Acceptance criteria:
  - at minimum, Jad reach is demonstrated on the evaluation surface
  - if first completion is achieved, it is recorded with full artifacts
- Not allowed to defer:
  - explicit late-game failure analysis if completion is not yet achieved
- Optional/later:
  - completion-rate hardening
- Dependencies:
  - PR 10.2

## Phase 11 - End-To-End Headed Demo Proof
- Execution status: `planned`
- Phase objective: prove that a real trained checkpoint transfers credibly into the trusted headed RSPS-backed demo path.

### PR 11.1 - Checkpoint-To-Headed Demo Harness And Artifact Packet
- Status: `planned`
- Objective: run the best current trained checkpoint in the headed RSPS-backed path as a real end-to-end demo.
- Why it belongs here: this is the actual train -> headed-demo proof, not replay or scripted smoke.
- Exact files/directories/modules likely touched:
  - `/home/jordan/code/RL/scripts/run_demo_backend.py`
  - `/home/jordan/code/RL/scripts/run_headed_checkpoint_inference.py`
  - `/home/jordan/code/RSPS/docs/fight_caves_demo_stock_client_runbook.md`
  - headed validation note(s)
- Work items:
  - load a promoted training checkpoint
  - run it on the trusted headed path without UI clicking
  - collect headed artifacts, logs, and screenshots/checklists
- Deliverables:
  - train-to-headed demo artifact packet
  - headed validation note for the run
- Evidence required:
  - checkpoint metadata
  - headed live inference summary
  - headed session log
- Acceptance criteria:
  - a real trained checkpoint drives the headed demo path
  - the run is artifact-backed and reproducible enough for review
- Not allowed to defer:
  - use of a real trained checkpoint
  - headed artifact capture
- Optional/later:
  - longer showcase sessions
- Dependencies:
  - Phase 10 promotable checkpoint

### PR 11.2 - Transfer-Critical Parity Audit Packet
- Status: `planned`
- Objective: validate only the semantics that matter most for train-to-demo trust.
- Why it belongs here: transfer proof is only trustworthy if the meaningful live semantics line up.
- Work items:
  - audit visible-target ordering and target resolution
  - audit prayer behavior
  - audit consumables
  - audit movement/path timing
  - audit starter-state/reset behavior
  - audit wave progression
  - audit Jad behavior when relevant evidence exists
- Deliverables:
  - targeted parity packet
  - mismatch ledger with severity and owner
- Evidence required:
  - headed session artifacts
  - headless eval artifacts
  - explicit side-by-side findings for the targeted semantics
- Acceptance criteria:
  - transfer-critical semantics are either aligned or explained with bounded known differences
  - no unexplained headed mismatch remains on a trusted checkpoint demo path
- Not allowed to defer:
  - explicit target-ordering and prayer/consumable checks
- Optional/later:
  - broader parity exploration outside transfer-critical semantics
- Dependencies:
  - PR 11.1

### PR 11.3 - Headed Demo Proof Closure
- Status: `planned`
- Objective: close the end-to-end train -> headed-demo proof gate honestly.
- Why it belongs here: the project needs a clear "transfer works" decision before cleanup or major performance-hardening claims.
- Work items:
  - review the headed checkpoint run and targeted parity packet together
  - decide whether train-to-demo transfer is proven
  - document any remaining non-blocking caveats
- Deliverables:
  - headed demo proof closure note
  - updated operator guidance if transfer is proven
- Evidence required:
  - promoted checkpoint artifact
  - headed demo packet
  - targeted parity packet
- Acceptance criteria:
  - a trained checkpoint shows credible Fight Caves behavior in the headed RSPS-backed path
  - the behavior is explainable and trustworthy enough for demo use
- Not allowed to defer:
  - closure decision based on evidence rather than optimism
- Optional/later:
  - showcase/demo polish
- Dependencies:
  - PR 11.2

## Phase 12 - Performance Hardening
- Execution status: `planned`
- Phase objective: improve throughput only after measurement and learning/transfer proof exist, using the ranked bottleneck history from Phase 9 and any bounded pre-learning trainer/policy cost-reduction evidence already captured there.

### PR 12.1 - Freeze Ranked Hot-Path Fix Order
- Status: `planned`
- Objective: select the first post-proof optimization targets strictly from the measured bottleneck ranking and the bounded trainer/policy compute-cost evidence already captured before learning proof.
- Why it belongs here: no speculative broad refactors should enter the hot path, and any later hardening round should start from the already-proven cheapest viable learning configuration rather than re-open pre-learning configuration selection.
- Work items:
  - choose the top ranked bottleneck(s)
  - define the expected measurement movement
  - define the regression checks that must remain green
  - distinguish between trainer/policy work already addressed in PR 9.5 and broader post-proof hardening work still worth pursuing
- Deliverables:
  - ranked fix order
  - before/after measurement plan
- Evidence required:
  - reference to the Phase 9 decomposition packet
  - reference to the PR 9.4 hot-path attribution packet
  - reference to the PR 9.5 selected-default and before/after compute-cost packet
- Acceptance criteria:
  - the first optimization target is evidence-backed
  - the first post-proof hardening target is not redundant with already-completed pre-learning trainer/policy enablement work
- Not allowed to defer:
  - explicit expected measurement delta
- Optional/later:
  - lower-ranked optimization backlog
- Dependencies:
  - Phase 9
  - Phase 10
  - Phase 11

### PR 12.2 - Bottleneck Fix Round 1 And Remeasure
- Status: `planned`
- Objective: implement the first ranked performance fix and remeasure the full benchmark packet.
- Work items:
  - apply the bounded hot-path change
  - rerun the canonical benchmark methodology
  - confirm no learning/transfer-critical regression
- Deliverables:
  - before/after benchmark packet
  - regression-check note
- Evidence required:
  - benchmark diff
  - no-regression eval/demo evidence where required
- Acceptance criteria:
  - the change moves the intended bottleneck in the expected direction
  - no transfer-critical regression is introduced
- Not allowed to defer:
  - before/after measurement
  - regression check on learning/demo-critical behavior
- Optional/later:
  - deeper cleanup of related code
- Dependencies:
  - PR 12.1

### PR 12.3 - Continue Hardening Or Escalate Based On Evidence
- Status: `planned`
- Objective: decide whether to continue ranked hardening rounds or open a deeper kernel/runtime escalation path.
- Why it belongs here: the system should not drift into open-ended optimization without a decision rule.
- Work items:
  - review the remaining gap to `~500,000 SPS`
  - decide whether another ranked round is justified
  - if the gap remains too large, write the bounded escalation note for a deeper runtime/kernel change
- Deliverables:
  - performance decision note
  - next ranked round or bounded escalation proposal
- Evidence required:
  - cumulative benchmark history
  - remaining-gap analysis
- Acceptance criteria:
  - the next performance move is justified by evidence
- Not allowed to defer:
  - explicit decision on whether current hardening is enough
- Optional/later:
  - native-kernel exploratory roadmap
- Dependencies:
  - PR 12.2

## Phase 13 - Cleanup / Prune / Refactor
- Execution status: `planned`
- Phase objective: clean up only after the winning path is proven.

### PR 13.1 - Dead-Path Audit And Preservation Review
- Status: `planned`
- Objective: identify what is actually dead, stale, redundant, or still necessary as fallback/reference.
- Why it belongs here: cleanup should be guided by proven usage, not aesthetics.
- Work items:
  - audit dead files and redundant configs/scripts/docs
  - decide what must remain preserved for fallback/reference/debug
  - mark superseded paths for archive or removal
- Deliverables:
  - dead-path audit
  - preservation/retention matrix
- Evidence required:
  - usage and dependency review
  - explicit fallback retention decision
- Acceptance criteria:
  - cleanup targets are concrete and justified
- Not allowed to defer:
  - explicit retention decision for fallback/reference paths
- Optional/later:
  - broad folder reshaping
- Dependencies:
  - Phase 11 closure
  - at least the first meaningful Phase 12 round

### PR 13.2 - Docs/Scripts/Config Cleanup
- Status: `planned`
- Objective: remove or archive stale docs/scripts/configs that are no longer needed once the winning path is proven.
- Work items:
  - archive superseded docs
  - remove stale script/config entrypoints where safe
  - simplify operator docs around the winning default path
- Deliverables:
  - cleanup patch
  - archive/supersede notes
- Evidence required:
  - dead-path audit references
- Acceptance criteria:
  - cleanup is deliberate and does not remove needed fallback/reference paths prematurely
- Not allowed to defer:
  - explicit archive/supersede notes for removed materials
- Optional/later:
  - cosmetic doc polishing
- Dependencies:
  - PR 13.1

### PR 13.3 - Winning-Path Refactor Only Where Justified
- Status: `planned`
- Objective: refactor only where repeated measurement and usage show a real maintenance or performance need.
- Why it belongs here: refactor should follow proof, not precede it.
- Work items:
  - identify high-friction modules on the proven path
  - limit refactor scope to the winning/default path
  - preserve behavior and operator contracts
- Deliverables:
  - bounded refactor patch set
  - no-regression evidence
- Evidence required:
  - maintenance or performance justification
  - no-regression validation
- Acceptance criteria:
  - refactor reduces real pain on the proven path without reopening architecture uncertainty
- Not allowed to defer:
  - explicit no-regression evidence
- Optional/later:
  - broader repository reorganization
- Dependencies:
  - PR 13.2

## Preservation Rule Until Proof Is Complete

These remain preserved until the relevant post-pivot gates are passed:

- `/home/jordan/code/fight-caves-demo-lite`
- V1 oracle/reference/debug replay path
- explicit V1 training and benchmark configs
- current headed validation notes and runbooks

No cleanup/refactor PR should weaken those preservation guarantees before Phase 13 authorization.
