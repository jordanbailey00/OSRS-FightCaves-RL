# Active Performance Reference

This is the single active performance truth reference.

## Metric definitions

- `env_steps_per_second`: environment stepping throughput from env benchmarks.
- `production_env_steps_per_second`: end-to-end training throughput headline for the `~500,000 SPS` goal.
- `wall_clock_env_steps_per_second`: observed wall-clock training throughput.

Operational rule: the `~500,000 SPS` target applies to **end-to-end training**, not env-only runs.

## Canonical baseline context

- Date: 2026-03-16
- Baseline packet: `training-env/rl` Phase 9.1
- Source-of-truth host: Linux/WSL
- Canonical benchmark configs:
  - `training-env/rl/configs/benchmark/fast_env_v2.yaml`
  - `training-env/rl/configs/benchmark/fast_train_v2.yaml`
- Sweep policy: env counts `4, 16, 64`, repeats `>=3`

## Canonical baseline headline numbers

- Env-only peak median SPS: `25,092.86`
- Training peak median SPS (disabled logging): `73.43`
- Gap to goals:
  - env-only goal `1,000,000`: currently `2.51%`
  - training goal `500,000`: currently `0.0147%`

## Latest PR 9.5 sweep outcome

- Date: 2026-03-19
- Selection report:
  - `docs/reports/phase9_pr95_compute_sweep.md`
- Selected Phase 10 benchmark config:
  - `training-env/rl/configs/benchmark/fast_train_v2_mlp64.yaml`
- Selected Phase 10 train config:
  - `training-env/rl/configs/train/train_fast_v2_mlp64.yaml`

Bounded benchmark comparison (`64` env, `1024` timesteps, disabled logging):

- baseline `fast_train_v2`: `61.51 SPS`
- rejected `fast_train_v2_lstm64`: `56.96 SPS`
- rejected `fast_train_v2_mlp128`: `57.28 SPS`
- selected `fast_train_v2_mlp64`: `64.88 SPS`

Paired subprocess attribution comparison (`64` env, `2048` timesteps):

- baseline `fast_train_v2`: `83.09 SPS`
- selected `fast_train_v2_mlp64`: `89.67 SPS`
- trainer policy-forward seconds: `14.236 -> 13.736`
- trainer update-compute seconds: `7.504 -> 6.778`
- trainer wait-for-env seconds: `5.346 -> 4.486`

Selection note:

- the selected config intentionally switches the Phase 10 canary path from `lstm_v0` / `use_rnn=true` to `mlp_v0` / `hidden_size=64` / `use_rnn=false`
- env backend, transport mode, reward/curriculum path, metric contract, and artifact contract remain fixed
- if Phase 10 canaries stall or later-wave proof shows recurrence is required, revisit recurrent candidates before Phase 10.2 / 10.3

## Latest attribution/profiling conclusions

From `docs/reports/perf_hotpath_attribution_20260316.md`:

- Default V2 subprocess packet measured `~105.06 SPS`.
- Top stage shares:
  - `eval_policy_forward`: `40.07%`
  - `train_backward`: `32.92%`
  - `train_policy_forward`: `19.24%`
  - `eval_env_recv`: `6.02%`
- Main slowdown origin: **trainer-side model compute (forward + backward/update)**.
- Secondary contributor: recv/wait barrier time.
- Not primary in current packet:
  - transport send/serialize overhead
  - observation flatten/pack
  - action translation/dispatch

Embedded comparison (`~112.70 SPS`) indicates transport boundary is not the first-order bottleneck.

## Current optimization priority order

1. Run Phase 10 canaries on `train_fast_v2_mlp64`.
2. Revisit recurrent policy surfaces only if the selected Phase 10 path fails to show bounded learning progress.
3. After learning proof, continue attacking `eval_policy_forward`, `train_backward`, and then barrier-tail effects.

## Active caveats

- Attribution packet is ranking-grade; instrumentation overhead exists.
- Outlier worker wait spikes occur occasionally and should be revisited after primary trainer compute optimizations.
