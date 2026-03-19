# Fight Caves Demo Live Checkpoint Validation

This note records the live-checkpoint-inference slice inside PR 8.1 for the trusted RSPS-backed headed Fight Caves demo.

It exists to prove that a real trained checkpoint can drive the headed demo through the same backend-control boundary already proven by the backend smoke and replay slices, without UI clicking and without inventing a headed-only action schema.

## Validation run

- Date: 2026-03-12
- Account: `fcdemo01`
- Runtime topology:
  - RSPS demo server in WSL/Linux
  - convenience headed client on Windows
  - working machine-specific path: `172.25.183.199:43594`
  - `127.0.0.1:43594` still does not work for the stock/convenience client path on this machine

## Checkpoint and control boundary

The live inference driver is:

- `/home/jordan/code/RL/scripts/run_headed_checkpoint_inference.py`

The real trained checkpoint used for both live validation passes was:

- `/home/jordan/code/RL/artifacts/acceptance/pr52_wsl_acceptance_20260311/train_run/fc-rl-train-1773247469-b81341f34265.pt`

Checkpoint/config assumptions captured in the output artifact:

- `policy_id=lstm_v0`
- `policy_use_rnn=true`
- `train_config_id=smoke_fast_v2`

The live headed control boundary remains the shared schema:

- `schema_id=headless_action_v1`
- `schema_version=1`

Each live policy action was:

1. decoded from the checkpoint policy logits against the shared action schema
2. normalized through the same action mapping used by backend smoke and replay
3. written into the headed backend-control inbox at:
   - `/home/jordan/code/RSPS/data/fight_caves_demo/backend_control/inbox/`
4. applied by the RSPS headed backend-control stage
5. observed through per-request result JSON plus `backend_action_processed` session-log events

The per-request result artifacts now also include:

- `observation_before`
- `observation_after`

using the shared headed export format:

- `schema_id=headless_observation_v1`

## Primary artifacts

- Greedy live inference summary:
  - `/home/jordan/code/RL/artifacts/headed_live_inference/fcdemo01-20260312T204803310840.json`
- Sampled live inference summary:
  - `/home/jordan/code/RL/artifacts/headed_live_inference/fcdemo01-20260312T204858268296.json`
- Headed session log with policy-driven backend actions:
  - `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/session_logs/fcdemo01-2026-03-12T20-47-37.612.jsonl`
- Representative per-step result artifact:
  - `/home/jordan/code/RSPS/data/fight_caves_demo/backend_control/results/fcdemo01-attack_visible_npc-20260312T204915740110.json`

## Result summary

The live-checkpoint slice passed for the current PR 8.1 step.

Confirmed:

- a real trained checkpoint drove the live headed session without manual UI clicking
- the live policy path stayed on the same shared `headless_action_v1` boundary already proven by backend smoke and replay
- the headed runtime surfaced policy-issued actions, visible targets, processed ticks, rejection reasons, and result metadata in artifact-backed form

The greedy pass was useful for clean timing and multi-target visibility evidence, but the sampled pass was needed to exercise a broader live action family set from the same checkpoint.

Sampled live action coverage:

- `walk_to_tile`
- `attack_visible_npc`
- `eat_shark`
- `drink_prayer_potion`
- `toggle_protection_prayer`
- `toggle_run`
- `wait`

Applied sampled live actions:

- `walk_to_tile=4`
- `attack_visible_npc=1`
- `eat_shark=4`
- `drink_prayer_potion=4`
- `toggle_protection_prayer=8`
- `toggle_run=5`
- `wait=2`

Rejected sampled live actions were still surfaced coherently instead of failing silently:

- `attack_visible_npc` with invalid visible-target indices produced `InvalidTargetIndex`
- `drink_prayer_potion` during a lockout produced `ConsumptionLocked`

## Target-ordering and target-resolution findings

Live checkpoint inference explicitly validated headed target ordering and resolution instead of inheriting replay assumptions.

For the sampled live run:

- `multi_target_observed=true`
- `max_visible_target_count=2`
- three policy-issued attack requests were observed
- one attack request applied successfully
- all applied attack steps resolved the expected headed target

For the successful live attack:

- selected visible index: `1`
- visible target before attack:
  - id: `tz_kek_spawn`
  - npc index: `20352`
- resolved target metadata after application:
  - `target_npc_id=tz_kek_spawn`
  - `target_npc_index=20352`

That is enough to show that:

- live headed visible-target collection remained explicit under policy control
- selected visible indices continued to resolve through the shared contract during live inference
- invalid target indices were surfaced explicitly rather than being silently remapped

This is still a bounded multi-target validation, not a broad multi-wave stress pass.

## Timing and cadence findings

Live inference artifacts now carry enough detail to reason about headed timing:

- per-step processed tick
- per-step latency
- per-step rejection reason where present
- per-step visible targets before/after
- per-step `observation_before` and `observation_after`

For the sampled live run:

- processed ticks advanced coherently from `194` through `225`
- every step advanced on a forward headed tick
- `tick_deltas` stayed at `1`
- per-step result latency stayed in the `500-750ms` range
- rejected steps were explicit and attributable

## Conclusion for PR 8.1

The live-checkpoint slice is now real in code and validated with artifact-backed evidence.

That means PR 8.1 now has all three required sub-slices:

- backend-control smoke
- replay-first validation
- live checkpoint inference

This note does not claim any default switch or Phase 8.2 readiness by itself.

## Still open after this slice

Not yet claimed here:

- default/backend switching
- removal of fallback paths
- a broad multi-wave live stress pass across many target-ordering situations

Those are outside this PR 8.1 implementation slice.

## Closure review outcome

PR 8.1 is complete.

Closure rationale:

- backend-control smoke is complete with artifact-backed headed action evidence
- replay-first validation is complete with artifact-backed target-resolution and timing evidence
- live checkpoint inference is complete with artifact-backed shared-schema, target-ordering, and cadence evidence
- no concrete acceptance blocker remains inside PR 8.1

Next step after this note:

- PR 8.2 completed later and performed the conditional default switch without removing fallback paths
- fallback paths remain explicitly available
