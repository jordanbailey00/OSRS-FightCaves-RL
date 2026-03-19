# Phase 9 PR 9.5 Compute-Cost Sweep

Date: 2026-03-19

## Scope

This report records the bounded PR 9.5 trainer/policy compute-cost sweep for the default V2 training path.

Fixed across all compared configs:

- env backend: `v2_fast`
- transport mode: `shared_memory_v1`
- info payload mode: `minimal`
- reward config: `reward_shaped_v2`
- curriculum config: `curriculum_wave_progression_v2`
- topology: `64` envs, `4` subprocess workers

## Compared configs

- Baseline benchmark config:
  - `/home/jordan/code/runescape-rl/training-env/rl/configs/benchmark/fast_train_v2.yaml`
- Candidate benchmark configs:
  - `/home/jordan/code/runescape-rl/training-env/rl/configs/benchmark/fast_train_v2_lstm64.yaml`
  - `/home/jordan/code/runescape-rl/training-env/rl/configs/benchmark/fast_train_v2_mlp128.yaml`
  - `/home/jordan/code/runescape-rl/training-env/rl/configs/benchmark/fast_train_v2_mlp64.yaml`

## Benchmark evidence

All benchmark runs used:

- `scripts/benchmark_train.py`
- `--env-count 64`
- `--total-timesteps 1024`
- `--logging-modes disabled`

Artifacts:

- baseline:
  - `/home/jordan/code/runescape-rl-runtime/reports/perf/pr95_fast_train_v2_baseline_1024.json`
- `lstm64` candidate:
  - `/home/jordan/code/runescape-rl-runtime/reports/perf/pr95_fast_train_v2_lstm64_1024.json`
- `mlp128` candidate:
  - `/home/jordan/code/runescape-rl-runtime/reports/perf/pr95_fast_train_v2_mlp128_1024.json`
- `mlp64` candidate:
  - `/home/jordan/code/runescape-rl-runtime/reports/perf/pr95_fast_train_v2_mlp64_1024.json`

Measured `production_env_steps_per_second`:

- baseline `fast_train_v2`: `61.51`
- candidate `fast_train_v2_lstm64`: `56.96`
- candidate `fast_train_v2_mlp128`: `57.28`
- candidate `fast_train_v2_mlp64`: `64.88`

Result:

- `lstm64` regressed versus baseline and was rejected.
- `mlp128` regressed versus baseline and was rejected.
- `mlp64` improved versus baseline by `+5.47%` (`64.88 / 61.51`).

## Attribution evidence

Both attribution runs used:

- `scripts/profile_hotpath_attribution.py`
- `--env-count 64`
- `--total-timesteps 2048`
- `--backend subprocess`

Artifacts:

- baseline:
  - `/home/jordan/code/runescape-rl-runtime/reports/perf/pr95_hotpath_fast_train_v2_2048/perf_hotpath_attribution.json`
- selected candidate:
  - `/home/jordan/code/runescape-rl-runtime/reports/perf/pr95_hotpath_fast_train_v2_mlp64_2048/perf_hotpath_attribution.json`

Measured throughput:

- baseline `fast_train_v2`: `83.09 SPS`
- selected `fast_train_v2_mlp64`: `89.67 SPS`
- improvement: `+7.91%`

Derived split movement:

- trainer policy forward:
  - baseline: `14.236s`
  - selected: `13.736s`
  - delta: `-3.51%`
- trainer update compute:
  - baseline: `7.504s`
  - selected: `6.778s`
  - delta: `-9.67%`
- trainer wait for env:
  - baseline: `5.346s`
  - selected: `4.486s`
  - delta: `-16.09%`

Ranked hotspot order:

- baseline top 4:
  - `eval_policy_forward`
  - `train_backward`
  - `eval_env_recv`
  - `subprocess_parent_recv_wait`
- selected top 4:
  - `eval_policy_forward`
  - `train_backward`
  - `eval_env_recv`
  - `subprocess_parent_recv_wait`

Result:

- the ranked hotspot order did not change
- the selected config reduced the dominant policy/update buckets enough to raise bounded throughput
- transport/env semantics still are not the first-order blocker

## Selected Phase 10 default

Selected train config:

- `/home/jordan/code/runescape-rl/training-env/rl/configs/train/train_fast_v2_mlp64.yaml`

Selected benchmark/attribution config:

- `/home/jordan/code/runescape-rl/training-env/rl/configs/benchmark/fast_train_v2_mlp64.yaml`

Reason for selection:

- it is the only tested candidate in this bounded sweep that improved both:
  - same-family benchmark throughput on the shipped subprocess trainer path
  - post-selection attribution throughput on the same packet family

## Contract note

PR 9.5 intentionally changes the selected Phase 10 policy family from the PR 9.3 default-path assumption:

- from: `lstm_v0` with `use_rnn=true`
- to: `mlp_v0` with `hidden_size=64` and `use_rnn=false`

This is an explicit selection, not a silent drift. The env backend, transport mode, reward/curriculum path, metric contract, and artifact contract remain unchanged.

If Phase 10 canaries stall or later-wave proof shows recurrence is required, revisit recurrent candidates before treating this as a durable post-Phase-10 default.
