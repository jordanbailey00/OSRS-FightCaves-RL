# Phase 9 PR 9.2 Performance Decomposition

Date: 2026-03-16

## Scope

This note ranks the current default V2 bottlenecks using the completed PR 9.1 baseline packet and a PR 9.2 decomposition artifact.

## Inputs

- PR 9.1 packet:
  - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr91_baseline_20260316T180000Z/phase9_pr91_baseline_packet.json`
- PR 9.2 decomposition artifact:
  - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr92_decomposition_20260316T182300Z.json`
- fallback learner-ceiling diagnostic (non-RNN benchmark profile):
  - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr92_train_ceiling_train1024envv0_20260316T181800Z.json`
- default-profile learner-ceiling failure log:
  - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr92_train_ceiling_fast_train_v2_failure_20260316T183200Z.log`

## Headline Throughput Gap

- env-only peak median SPS: `25,092.86`
- training peak median SPS (`disabled` logging): `73.43`
- training-to-env peak ratio: `0.00293`
- env-to-training slowdown factor: `341.73x`

Interpretation:

- the default V2 path is not currently runtime-throughput limited first.
- end-to-end training throughput is dominated by trainer/evaluation path costs.

## Ranked Bottlenecks

### 1) Trainer-side evaluation path dominates training cycles

Disabled rows (`env_count=16,64`) mean runner-stage shares:

- `evaluate_seconds`: `47.23%`
- `train_seconds`: `34.47%`
- `final_evaluate_seconds`: `5.72%`

Trainer bucket ranking (`env_count=16,64` aggregate):

- `eval_total`: `49.02%`
- `eval_policy_forward`: `25.71%`
- `eval_env_recv`: `23.09%`
- `trainer_save_checkpoint`: `1.98%`

### 2) Runtime transport is visible in env-only hot path, but not the training gate leader

Env-only (`env_count=64`) hot-path ranking:

- `fast_vecenv_send_total`: `51.64%`
- `fast_vecenv_step_batch_call`: `39.32%`
- `fast_vecenv_apply_step_buffers`: `7.33%`
- `fast_vecenv_action_pack`: `1.58%`
- `fast_vecenv_recv_total`: `0.12%`

Interpretation:

- runtime send/step overhead is still meaningful in env-only mode.
- despite that, the training gate remains primarily constrained by trainer/eval cost composition.

### 3) Fallback learner-ceiling diagnostic still shows trainer-bound behavior

Fallback ceiling results (`train_1024env_v0` diagnostic):

- `4 env`: `149.07` diagnostic SPS
- `16 env`: `145.82` diagnostic SPS
- `64 env`: `146.04` diagnostic SPS

Interpretation:

- trainer-side throughput remains largely env-count invariant in this diagnostic.
- this supports the trainer-bound conclusion from the PR 9.1 training packet.

## MUST REVISIT

- `benchmark_train_ceiling.py` fails against the default `fast_train_v2` benchmark config with:
  - `RuntimeError: mat1 and mat2 shapes cannot be multiplied (64x536 and 134x128)`
- reproduced evidence:
  - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr92_train_ceiling_fast_train_v2_failure_20260316T183200Z.log`
- this blocks default-profile learner-ceiling decomposition and should be fixed in a dedicated follow-up before later hardening rounds.
- this did not block PR 9.2 ranking because PR 9.1 training packet plus runner/trainer bucket evidence was complete and sufficient.

## Decision

PR 9.2 is complete:

- dominant bottlenecks are ranked by measured cost share.
- trainer/evaluation path is identified as the primary gate to `~500,000` training SPS.
- next step is PR 9.3 training-readiness/metrics/artifact contract, with the ceiling mismatch tracked as `MUST REVISIT`.
