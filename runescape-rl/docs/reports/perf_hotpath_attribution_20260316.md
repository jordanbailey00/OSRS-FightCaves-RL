# Post-Flattening Hot-Path Attribution (Default V2 Path)

## Scope and setup
- Date: 2026-03-16
- Source root: `/home/jordan/code/runescape-rl`
- Runtime root: `/home/jordan/code/runescape-rl-runtime`
- Primary profile target: default V2 path (`env_backend=v2_fast`) with subprocess vecenv
- Config: `/home/jordan/code/runescape-rl/training-env/rl/configs/benchmark/fast_train_v2.yaml`
- Bounded run parameters:
  - `num_envs=64`
  - `subprocess_worker_count=4` (`16` envs/worker)
  - transport mode `shared_memory_v1`
  - info payload mode `minimal`
  - policy `lstm_v0` (`use_rnn=true`)
  - total timesteps `8192`
  - warmup iterations `0`

Comparison packet (transport isolation):
- Same config and timesteps, but `--backend embedded`.

## Artifacts
- Primary (subprocess/default path):
  - `/home/jordan/code/runescape-rl-runtime/reports/perf/hotpath_attribution_20260316T214936Z/perf_hotpath_attribution.json`
  - `/home/jordan/code/runescape-rl-runtime/reports/perf/hotpath_attribution_20260316T214936Z/hotspots_ranked.csv`
  - `/home/jordan/code/runescape-rl-runtime/reports/perf/hotpath_attribution_20260316T214936Z/worker_skew_stats.csv`
- Comparison (embedded vecenv):
  - `/home/jordan/code/runescape-rl-runtime/reports/perf/hotpath_attribution_20260316T215137Z/perf_hotpath_attribution.json`
  - `/home/jordan/code/runescape-rl-runtime/reports/perf/hotpath_attribution_20260316T215137Z/hotspots_ranked.csv`
  - `/home/jordan/code/runescape-rl-runtime/reports/perf/hotpath_attribution_20260316T215137Z/worker_skew_stats.csv`

## Throughput snapshot
- Subprocess/default: `8448` env-steps in `80.409s` => **`105.06 SPS`**
- Embedded comparison: `8448` env-steps in `74.958s` => **`112.70 SPS`** (`+7.27%`)

## Ranked hotspots (subprocess/default)
| Rank | Stage | Seconds | Share of wall time | Source |
|---:|---|---:|---:|---|
| 1 | `eval_policy_forward` | 32.219 | 40.07% | trainer |
| 2 | `train_backward` | 26.467 | 32.92% | trainer |
| 3 | `train_policy_forward` | 15.468 | 19.24% | trainer |
| 4 | `eval_env_recv` | 4.841 | 6.02% | trainer |
| 5 | `subprocess_parent_recv_wait` | 4.821 | 6.00% | transport boundary |
| 6 | `fast_vecenv_step_batch_call` | 2.328 | 2.89% | env worker |
| 7 | `fast_kernel_apply_actions` | 0.834 | 1.04% | env worker |
| 8 | `fast_kernel_tick` | 0.805 | 1.00% | env worker |
| 9 | `train_optimizer_step` | 0.580 | 0.72% | trainer |
| 10 | `fast_kernel_observe_flat` | 0.219 | 0.27% | env worker |
| 11 | `fast_kernel_projection` | 0.114 | 0.14% | env worker |
| 12 | `fast_vecenv_apply_step_buffers` | 0.092 | 0.11% | env worker |

## Attribution splits
### Trainer vs env vs transport
- Trainer policy compute (`eval_policy_forward + train_policy_forward`): `47.687s` (59.31%)
- Trainer update compute (`backward + optimizer + loss + minibatch prep`): `27.131s` (33.74%)
- Trainer waiting for env (`eval_env_recv`): `4.841s` (6.02%)
- Env fast-kernel core (`fast_kernel_total`): `1.972s` (2.45%)
- Parent transport wait (`subprocess_parent_recv_wait`): `4.821s` (6.00%)
- Action send/serialization overhead (`send_command + worker_send + worker_serialize`): `0.0898s` (0.11%)

Conclusion: wall time is dominated by **trainer-side model forward + backward**, not env core compute and not transport serialization overhead.

### Policy-forward vs everything else
- Policy forward total: `47.687s` (59.31%)
- Backward/update path: `27.131s` (33.74%)
- Everything else combined: remainder (~6.95%)

Conclusion: policy forward is the single largest contributor; backward/update is the second.

### Observation path cost
- `fast_kernel_observe_flat`: `0.219s` (0.27%)
- `fast_kernel_projection`: `0.114s` (0.14%)
- Combined obs/flatten/projection seen in fast-kernel buckets: `~0.333s` (0.41%)

Conclusion: observation build/flatten/pack is **not** a primary blocker in this packet.

### Action path cost
- Env action pack: `0.0266s` (0.03%)
- Trainer send actions: `0.0427s` (0.05%)
- Worker receive shared-memory actions: `0.0009s` (negligible)

Conclusion: action translation/dispatch is **not** a primary blocker.

### Worker skew / stragglers
From `worker_skew_stats.csv` (subprocess):
- Worker step means are close (`0.00464s` to `0.00527s`).
- Step p95 across workers stays tight (`~0.0103s` to `0.0116s`).
- One worker has a large recv-wait max (`3.924s`) indicating an outlier stall (startup/one-off), not persistent slow compute.
- Round wait span stats: median `0.00321s`, p95 `0.01237s`, p99 `0.06672s`, max `3.902s`.

Conclusion: there is occasional barrier outlier behavior, but sustained per-step skew is not the dominant driver.

## Explicit answers to required questions
1. **Most expensive stage:** `eval_policy_forward` (`32.219s`, 40.07% wall), followed by `train_backward` and `train_policy_forward`.
2. **Trainer mostly computing or waiting?** Mostly computing. Trainer wait is `6.02%`; trainer policy+update compute is `~93.05%`.
3. **Is env recv/send overhead primary?** No. Parent recv-wait is visible (`~6%`), but send/serialize overhead is tiny (`~0.11%`).
4. **Is policy forward primary or secondary?** Primary. It is the largest single contributor (`59.31%` combined eval/train forward).
5. **Is env worker hot path too expensive?** Not primary in this profile. `fast_kernel_total` is `2.45%` of wall.
6. **Is observation building/packing a major hotspot?** No (`~0.41%` combined observed obs/projection buckets).
7. **Is action translation/dispatch a major hotspot?** No (well under `0.2%` combined measured send/pack/receive).
8. **Are stragglers causing synchronization loss?** Minor/episodic, not dominant. One large outlier exists, but steady-state worker timing is tight.
9. **Top 1-3 hotspots to attack first for SPS gains:**
   1. Policy forward path (`eval_policy_forward` + `train_policy_forward`)
   2. Backward/update path (`train_backward`, then optimizer step)
   3. Secondary: reduce env wait/barrier tail (`subprocess_parent_recv_wait`) after model path improvements
10. **Architecture fundamentally too broad, or still optimizable?** Still optimizable within current design. Evidence indicates model/trainer compute dominates; the current architecture is not yet disproven by transport/env overhead.

## Smallest high-confidence next actions (evidence-tied)
1. **Reduce policy-forward cost first** (largest hotspot):
   - Measure smaller hidden size / cheaper policy variant under same packet.
   - Evaluate `torch.compile` modes for forward path only with rollback guard.
2. **Reduce backward/update cost second**:
   - Run minibatch/bptt sweep at fixed env topology to reduce `train_backward` share.
   - Keep acceptance tied to measured SPS and unchanged behavioral metrics.
3. **Then trim subprocess barrier tail** (secondary):
   - Investigate the outlier wait spikes with per-iteration correlation to resets/world events.
   - Keep transport changes bounded; current send/serialize path is not the first-order issue.

## Measurement notes / limitations
- Instrumentation adds some overhead; focus is attribution ranking, not absolute peak SPS.
- Primary conclusions are from subprocess/default path (the one used operationally). Embedded run is used only to isolate transport contribution.
- Outlier wait spikes exist; this packet indicates they are not the dominant steady-state cost driver.
