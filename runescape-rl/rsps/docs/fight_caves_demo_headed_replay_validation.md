# Fight Caves Demo Headed Replay Validation

This note records the replay-first slice inside PR 8.1 for the trusted RSPS-backed headed Fight Caves demo.

It exists to prove that recorded/shared action traces can drive the headed demo through the same backend-control boundary already proven by the backend smoke path, without UI clicking and without inventing a headed-only action schema.

## Validation run

- Date: 2026-03-12
- Account: `fcdemo01`
- Runtime topology:
  - RSPS demo server in WSL/Linux
  - convenience headed client on Windows
  - working machine-specific path: `172.25.183.199:43594`
  - `127.0.0.1:43594` still does not work for the stock/convenience client path on this machine

## Replay boundary

The replay driver is:

- `/home/jordan/code/RL/scripts/run_headed_trace_replay.py`

Accepted replay inputs:

- a built-in trace pack via `--trace-pack`
- an explicit JSON trace via `--trace-json`
- a session-valid materialized trace when neither is provided

The accepted action boundary remains the shared schema:

- `schema_id=headless_action_v1`
- `schema_version=1`

The headed replay path does not introduce a replay-only or headed-only action schema. Every replayed step is normalized through the same shared action mapping used by the backend smoke path and then delivered into the live headed session through:

- `/home/jordan/code/RSPS/data/fight_caves_demo/backend_control/inbox/`
- `/home/jordan/code/RSPS/data/fight_caves_demo/backend_control/results/`

For this first validation pass, movement was materialized from the current headed session state because Fight Caves runs in a dynamic instance. That changes the source of the input trace, not the dispatched action schema.

## Primary artifacts

- Replay summary:
  - `/home/jordan/code/RL/artifacts/headed_replay/fcdemo01-20260312T202236944935.json`
- Replay input trace used for the run:
  - `/home/jordan/code/RL/artifacts/headed_replay/fcdemo01-20260312T202236944935-input-trace.json`
- Headed session log with replay-driven action events:
  - `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/session_logs/fcdemo01-2026-03-12T20-21-02.168.jsonl`

## Result summary

The replay slice passed for the current PR 8.1 step.

Confirmed:

- replay drove the live headed session without manual UI clicking
- the replay input boundary stayed on `headless_action_v1`
- all replayed actions applied successfully:
  - `walk_to_tile`
  - `toggle_protection_prayer`
  - `attack_visible_npc`
  - `eat_shark`
  - `drink_prayer_potion`
- no replay step was rejected
- replayed actions advanced on coherent headed ticks:
  - `340, 341, 342, 343, 344, 345, 346, 347, 348`
- replay cadence remained forward-only with:
  - `tick_deltas=[1,1,1,1,1,1,1,1]`

## Target-ordering and target-resolution findings

Replay explicitly validated headed visible-target mapping during attack selection.

For the replayed `attack_visible_npc` step:

- selected visible index: `0`
- visible target before attack:
  - id: `tz_kek`
  - npc index: `20356`
- resolved target metadata after application:
  - `target_npc_id=tz_kek`
  - `target_npc_index=20356`

That first replay validation confirms:

- visible-target collection was surfaced in the replay artifact and session log
- the selected headed visible-target index remained meaningful during replay
- attack target-resolution semantics stayed aligned with the shared contract for this run

## Timing and cadence findings

Replay artifacts now carry enough detail to reason about headed timing:

- per-step processed tick
- per-step request context
- per-step visible targets before/after
- per-step application result metadata
- per-step latency

For this run:

- all replayed actions were processed on forward ticks
- no deferred or rejected replay steps were observed
- per-step result latency stayed in the `500-750ms` range for this headed session

## Later PR 8.1 status

At the time of this replay slice, live checkpoint inference remained later inside PR 8.1.

That later slice is now complete and recorded in:

- [fight_caves_demo_live_checkpoint_validation.md](./fight_caves_demo_live_checkpoint_validation.md)
