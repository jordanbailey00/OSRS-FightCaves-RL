# Phase 9 PR 9.1 Baseline

Date: 2026-03-16

## Scope

This note records the first post-pivot Phase 9.1 baseline packet for the default V2 path using the canonical benchmark methodology (`4,16,64` env counts, `3` repeats).

## Command

From `/home/jordan/code/RL`:

```bash
source /home/jordan/code/.workspace-env.sh
./.venv/bin/python scripts/run_phase9_baseline_packet.py \
  --output-dir /home/jordan/code/RL/artifacts/post_pivot/phase9_pr91_baseline_20260316T180000Z
```

## Artifact

- packet:
  - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr91_baseline_20260316T180000Z/phase9_pr91_baseline_packet.json`
- raw env rows:
  - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr91_baseline_20260316T180000Z/raw/env/`
- raw train rows:
  - `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr91_baseline_20260316T180000Z/raw/train/`

## Headline Results

Targets:

- env-only target: `1,000,000` SPS
- training target (`production_env_steps_per_second`): `500,000` SPS

Measured peak medians from this packet:

- env-only peak median SPS: `25,092.86` (`2.51%` of target)
- training peak median SPS (disabled logging): `73.43` (`0.0147%` of target)

Measured medians by env count:

- env-only:
  - `4 env`: `3,828.83`
  - `16 env`: `10,820.75`
  - `64 env`: `25,092.86`
- training (`disabled` logging):
  - `4 env`: `46.91`
  - `16 env`: `73.43`
  - `64 env`: `69.76`

## Benchmark Context Snapshot

- host class: `wsl2`
- performance source-of-truth flag: `true`
- RL commit SHA: `b45d110331648ac3ed073a94a834302bd5c4c94f`
- sim commit SHA: `16c3bc66604813da293b46185d866d4711ca158f`
- RSPS commit SHA: `65f04890d368d2108cf1a3c16364369d3943e2ca`
- benchmark profile id/version: `official_profile_v0` / `0`

## Decision

PR 9.1 baseline capture is complete.

Next step is PR 9.2: produce a ranked decomposition from these rows (env/runtime vs trainer/update dominance) and identify the top bottlenecks blocking progress toward `~500,000` training SPS.
