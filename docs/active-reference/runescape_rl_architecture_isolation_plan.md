# RuneScape-RL Architecture Isolation Experiment — Plan

## Status
**Created:** 2026-03-22
**Branch:** `jvm-architecture-improvement` (isolated from mainline `vClaude`)
**Depends on:** Ocean-env experiment (completed, showed trainer-side mods are fast)

## Goal
Isolate how much overhead comes from the runescape-rl subprocess/IPC architecture
itself versus the actual Fight Caves JVM game simulation.

Central question: **"If we keep the runescape-rl subprocess+shared-memory architecture
but replace the game logic with a trivial no-op env, how fast does it go?"**

## Approach

### What we built
A trivial no-op environment (`TrivialNoOpEnv`) that:
- Returns constant observations of the same shape as the real env: (134,) float32
- Accepts actions of the same shape: MultiDiscrete([7, 16384, 16384, 4, 8, 3])
- Returns zero rewards, truncates at configurable tick_cap
- Does NO game simulation whatsoever

### How we test it

Three configurations, each measuring a different layer:

| Config | What it measures |
|--------|------------------|
| **A: Direct** | TrivialNoOpEnv stepped in the parent process, no subprocess, no IPC. Pure Python loop ceiling. |
| **B: Subprocess + shared memory** | TrivialNoOpEnv inside subprocess workers, communicating via mmap-backed shared memory + Pipe signaling. Same IPC pattern as runescape-rl production path. |
| **Comparison** | B vs A = pure IPC overhead. B vs known real-env SPS = JVM + simulation fraction. |

### What is held constant
- Observation shape: (134,) float32
- Action shape: MultiDiscrete([7, 16384, 16384, 4, 8, 3])
- Shared memory transport: mmap-backed arrays, same layout as real env
- Subprocess protocol: multiprocessing.Process (spawn), Pipe for commands, mmap for data
- Worker loop structure: recv command → read actions from shm → step env → write results to shm → send "ok"

### What differs
- **No JVM**: No JPype, no Kotlin, no HeadlessRuntime
- **No game logic**: No tick(), no observeFlatBatch(), no reward snapshot
- **No observation encoding**: Constant observations, no encode_observation_for_policy()
- **No reward computation**: Zero reward always
- **No training loop**: Pure env stepping, no policy forward/backward

### Files created
```
src/rl/fight_caves_rl/experiments/
├── __init__.py
└── architecture_isolation/
    ├── __init__.py
    ├── trivial_env.py              # No-op env with correct obs/action shapes
    ├── trivial_subprocess_env.py   # Subprocess+shm wrapper (mirrors SubprocessHeadlessBatchVecEnv)
    └── benchmark.py                # Benchmark runner: direct vs subprocess
```

### Comparison baselines (from existing investigation)

| Configuration | SPS | Source |
|---|---|---|
| Mode 1: JVM tick only (64 envs) | 1,459,000 | performance_investigation_report.md |
| Mode 4: Env-only embedded (64 envs) | 88,352 | performance_investigation_report.md |
| Mode 6: Trainer ceiling, fake env CPU | ~618 | performance_investigation_report.md (post-T3.2) |
| Mode 7: Embedded training CPU | ~214 | performance_investigation_report.md (pre-T3.2) |
| Production subprocess training CPU | ~387 | project_sps_investigation.md |
| Production GPU training (RTX 5070 Ti) | 4,042 | project_sps_investigation.md |

## Constraints
- Does not modify mainline codebase
- Does not change existing benchmark scripts
- All new code is under `experiments/architecture_isolation/`
- No production features introduced
- Results are diagnostic only
