# Phase 9 PR 9.3 Training Readiness

Date: 2026-03-16

## Scope

This document freezes Phase 9.3 readiness for the default V2 training path:

- default config assumptions
- reward/curriculum assumptions
- canonical training/eval metric set
- dashboard expectations
- required train/eval artifact contract
- fixed definition of "learning is happening" for Phase 10

## PR 9.5 Supersession Note (2026-03-19)

PR 9.5 kept the env backend, transport mode, reward/curriculum path, metric contract, and artifact contract from this note intact, but it explicitly changed the selected Phase 10 canary policy surface:

- selected train config:
  - `/home/jordan/code/runescape-rl/training-env/rl/configs/train/train_fast_v2_mlp64.yaml`
- selected benchmark config:
  - `/home/jordan/code/runescape-rl/training-env/rl/configs/benchmark/fast_train_v2_mlp64.yaml`
- policy-family change:
  - from `lstm_v0` with `use_rnn=true`
  - to `mlp_v0` with `hidden_size=64` and `use_rnn=false`

This was an explicit Phase 10 selection decision, not a silent contract drift. If later-wave proof shows recurrence is required, revisit recurrent policy candidates before treating the PR 9.5 selection as durable beyond the early canary phase.

## Readiness Evidence

Readiness run packet:

- `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr93_readiness_20260316T184000Z/train_summary.json`
- `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr93_readiness_20260316T184000Z/eval_summary.json`
- `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr93_readiness_20260316T184000Z/learning_eval_summary.json`

Manifest evidence:

- train run manifest:
  - `/home/jordan/code/RL/artifacts/train/smoke_fast_v2/runs/fc-rl-train-1773686282-84bcf56d94db/run_manifest.json`
- eval run manifest:
  - `/home/jordan/code/RL/artifacts/eval/replay_eval_v0/fc-rl-eval-1773686304-9787d154419f/run_manifest.json`

## Default Path Assumptions (Frozen)

### Training defaults

- default train entrypoint:
  - `/home/jordan/code/RL/scripts/train.py`
- default config:
  - `/home/jordan/code/RL/configs/train/smoke_fast_v2.yaml`
- train-scale config for bounded learning runs:
  - `/home/jordan/code/RL/configs/train/train_fast_v2.yaml`

### Required default train config shape

- `env.env_backend = v2_fast`
- `env.info_payload_mode = minimal`
- subprocess transport active:
  - `env.subprocess_transport_mode = shared_memory_v1`
- default policy:
  - `policy.id = lstm_v0`
- reward config:
  - `reward_shaped_v2`
- curriculum config:
  - `curriculum_wave_progression_v2`

### Reward/curriculum ownership note

- V2 reward configs (`reward_sparse_v2`, `reward_shaped_v2`) are kernel-feature weighting configs and must not be routed through the V1 observation-reward function path.
- `curriculum_wave_progression_v2` is a reset-override schedule only; it does not alter simulator mechanics.

## Canonical Metrics Contract

### Training metrics (from `train_summary.json` + `puffer_logs`)

Required:

- throughput and progress:
  - `train/SPS`
  - `train/agent_steps`
  - `train/epoch`
- optimization health:
  - `train/losses/policy_loss`
  - `train/losses/value_loss`
  - `train/losses/entropy`
  - `train/losses/approx_kl`
  - `train/losses/clipfrac`
  - `train/losses/explained_variance`
- runtime timing:
  - `runner_stage_seconds.*` from train summary
  - `train/performance/*` from puffer logs

### Eval/learning metrics (from `learning_eval_summary.json`)

Required:

- `episode_count`
- `mean_steps`
- `median_steps`
- `terminated_rate`
- `truncated_rate`
- `player_death_rate`
- `cave_complete_rate`
- `mean_final_wave`
- `max_final_wave`
- `mean_max_wave_seen`
- `max_wave_seen`

### Benchmark metrics (carry-over from PR 9.1/9.2)

- `production_env_steps_per_second`
- `wall_clock_env_steps_per_second`
- `runner_stage_seconds`
- `trainer_bucket_totals`
- `vecenv_topology`

## Dashboard Contract

W&B (or equivalent offline export) should expose three fixed views:

- Train health:
  - `train/SPS`, `train/agent_steps`, `train/epoch`
- Loss/optimization:
  - `train/losses/*`
- Runtime performance:
  - `train/performance/*`
  - run-level `runner_stage_seconds` attached from summaries/manifests

For learning ladders, each checkpoint-eval summary must include the fixed eval metrics from `learning_eval_summary_v1`.

## Required Artifact Contract

Every bounded learning run must emit:

- train side:
  - `train_summary.json` (script output)
  - checkpoint `.pt`
  - checkpoint metadata `.metadata.json`
  - `run_manifest.json`
- eval side (for promoted checkpoints):
  - `eval_summary.json`
  - `replay_pack.json`
  - `replay_index.json`
  - `run_manifest.json`
  - `learning_eval_summary.json` (`learning_eval_summary_v1`)

Runs without these artifacts are not promotable for Phase 10 decision-making.

## Fixed Definition: "Learning Is Happening"

Phase 10 should treat learning as happening only when all are true on a fixed checkpoint ladder:

- fixed evaluation surface is used (seed pack + config + policy mode recorded)
- at least 4 checkpoints are evaluated (including a near-initial baseline checkpoint)
- at least 2 learning metrics improve versus the baseline checkpoint and the improvement persists across at least 2 consecutive later checkpoints
  - eligible metrics:
    - `mean_steps`
    - `mean_max_wave_seen`
    - `player_death_rate` (decrease)
    - `cave_complete_rate` (increase)
- results are artifact-backed (`eval_summary`, replay pack/index, run manifest, learning eval summary)

Single cherry-picked replays without ladder evidence do not count.

## MUST REVISIT Carry-Forward

- `scripts/benchmark_train_ceiling.py` fails for `configs/benchmark/fast_train_v2.yaml` with shape mismatch (`64x536` vs `134x128`):
  - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr92_train_ceiling_fast_train_v2_failure_20260316T183200Z.log`
- this remains open for later hardening diagnostics, but it is not a blocker for PR 9.3 readiness closure.
