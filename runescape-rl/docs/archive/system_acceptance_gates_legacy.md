# System Acceptance Gates

## Purpose

These are the post-pivot system gates.

They exist to keep performance proof, learning proof, headed demo proof, and cleanup authorization separate.

## Gate A - Benchmark Truth Gate

### What must be true

- the canonical benchmark methodology is explicit
- the current default V2 env-only throughput is measured
- the current default V2 end-to-end training throughput is measured
- the gap to `1,000,000 env-only SPS` and `500,000 training SPS` is quantified honestly

### Required evidence

- benchmark packet from:
  - `/home/jordan/code/RL/configs/benchmark/fast_env_v2.yaml`
  - `/home/jordan/code/RL/configs/benchmark/fast_train_v2.yaml`
- repeated benchmark runs on the approved Linux/WSL source-of-truth host
- recorded:
  - topology
  - stage buckets
  - trainer buckets
  - memory profile
  - repo SHAs

### What does not count

- a single ad hoc run
- env-only SPS used as a substitute for the training SPS gate
- cherry-picked benchmark rows without topology or stage evidence

### Current evidence snapshot (2026-03-16)

- status:
  - Gate A methodology/baseline capture: `satisfied`
- artifact packet:
  - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr91_baseline_20260316T180000Z/phase9_pr91_baseline_packet.json`
- benchmark note:
  - `/home/jordan/code/RL/docs/phase9_pr91_baseline.md`
- decomposition note:
  - `/home/jordan/code/RL/docs/phase9_pr92_decomposition.md`
- measured headline values:
  - env-only peak median SPS: `25092.86` (`2.51%` of `1,000,000`)
  - training peak median SPS (disabled logging): `73.43` (`0.0147%` of `500,000`)

## Gate B - Training Readiness Gate

### What must be true

- the default training config is explicit and usable
- reward/curriculum/checkpoint assumptions are explicit
- training metrics and artifacts are frozen for learning runs
- "learning is happening" has a defined meaning

### Required evidence

- readiness checklist
- short readiness run artifacts
- fixed training/eval metric definitions

### What does not count

- "the smoke run finished"
- a checkpoint file with no evaluation ladder
- loose dashboard screenshots without fixed metric definitions

### Current evidence snapshot (2026-03-16)

- status:
  - Gate B readiness contract freeze: `satisfied`
- readiness note:
  - `/home/jordan/code/RL/docs/phase9_pr93_training_readiness.md`
- readiness artifacts:
  - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr93_readiness_20260316T184000Z/train_summary.json`
  - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr93_readiness_20260316T184000Z/eval_summary.json`
  - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr93_readiness_20260316T184000Z/learning_eval_summary.json`
- manifest evidence:
  - `/home/jordan/code/RL/artifacts/train/smoke_fast_v2/runs/fc-rl-train-1773686282-84bcf56d94db/run_manifest.json`
  - `/home/jordan/code/RL/artifacts/eval/replay_eval_v0/fc-rl-eval-1773686304-9787d154419f/run_manifest.json`

## Gate C - Learning Proof Gate

### What must be true

- the default V2 path shows repeatable improvement beyond trivial/random behavior
- a fixed checkpoint evaluation ladder exists
- milestone progress is visible across:
  - early waves
  - later waves
  - Jad reach
  - eventual completion

### Required evidence

- bounded training-run packet
- checkpoint ladder table
- fixed-seed evaluation artifacts
- replay/eval artifacts for representative checkpoints

### What does not count

- a single cherry-picked run
- subjective "it looks smarter"
- only early-wave evidence with no promotion ladder

## Gate D - Headed Demo Transfer Gate

### What must be true

- a real trained checkpoint drives the trusted headed RSPS-backed path
- the headed session is controlled through the backend-control boundary, not UI clicking
- headed behavior is credible enough to trust as a real demo of the trained policy

### Required evidence

- trained checkpoint metadata
- headed live-inference summary
- headed session log
- headed screenshots/checklist or equivalent run artifacts

### What does not count

- scripted replay alone
- backend smoke alone
- human-driven UI play

## Gate E - Transfer-Critical Parity Gate

### What must be true

- the semantics that matter for train-to-demo trust are validated:
  - target ordering and target resolution
  - prayer behavior
  - consumables
  - movement/path timing
  - reset/state initialization
  - wave progression
  - Jad behavior when relevant

### Required evidence

- targeted parity packet
- side-by-side headed/headless findings
- mismatch ledger with severity and owner

### What does not count

- broad parity claims with no focus on transfer-critical semantics
- "Phase 8 already validated replay/inference"
- unexplained mismatch lists with no prioritization

## Gate F - Performance Hardening Gate

### What must be true

- the first optimization targets are chosen from measured bottlenecks
- each meaningful optimization has before/after evidence
- no learning-proof or transfer-critical regression is introduced
- the remaining gap to `~500,000 SPS` is updated after each meaningful hardening round

### Required evidence

- ranked bottleneck report
- before/after benchmark packets
- no-regression evidence on learning/demo-critical behavior

### What does not count

- blind optimization patches
- raw speed claims without stage-bucket evidence
- env-only improvements presented as full training-gate success

## Gate G - Cleanup Authorization Gate

### What must be true

- benchmark truth is established
- learning proof is established
- headed transfer proof is established
- at least the first meaningful performance-hardening round is complete
- fallback/reference retention has been reviewed explicitly

### Required evidence

- dead-path audit
- retention matrix
- references to the completed benchmark, learning, and transfer gates

### What does not count

- "the repo feels messy"
- speculative cleanup before the winning path is proven
- removing fallback/reference paths just because defaults changed

## Operational Definitions

### What `~500,000 SPS` means operationally

- this is end-to-end training throughput on the default V2 path
- headline metric:
  - `production_env_steps_per_second`
  - from the canonical training benchmark family
- it is not satisfied by:
  - env-only SPS
  - replay throughput
  - single-run anecdotal training speed

### What benchmark methodology is canonical

- env-only:
  - `/home/jordan/code/RL/configs/benchmark/fast_env_v2.yaml`
  - `scripts/benchmark_env.py`
- training:
  - `/home/jordan/code/RL/configs/benchmark/fast_train_v2.yaml`
  - `scripts/benchmark_train.py`
- run on the approved Linux/WSL source-of-truth host
- use repeated runs and keep topology/stage data

### What counts as proving the agent is actually learning

- fixed-seed evaluation shows repeatable improvement beyond random/trivial baselines
- checkpoint progression is visible on a defined ladder
- milestone evidence exists for early-wave, later-wave, Jad-reach, and eventual-completion goals

### What counts as proving train-to-demo transfer works

- a real trained checkpoint drives the headed RSPS-backed path without UI clicking
- the headed run looks credible and explainable through logs/artifacts
- transfer-critical semantics are validated rather than assumed

### When cleanup/refactor is allowed to begin

- only after the benchmark truth, learning proof, headed demo transfer, and first meaningful performance-hardening gates are satisfied

### What must remain preserved until then

- `fight-caves-demo-lite`
- V1 oracle/reference/debug replay path
- explicit V1 training and benchmark fallback configs
- current headed validation notes and operator runbooks
