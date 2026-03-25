# Training Performance — Living Doc

Last updated: 2026-03-24 | Branch: v2Claude | Post-PR7

## Current Performance Snapshot

| Metric | Value | Context |
|--------|-------|---------|
| Python↔C SPS (64 envs, batched) | **3,353,676** | Batched C path, `fc_capi_batch_step_flat`. `python3 RL/src/fc_env.py` |
| Python↔C SPS (1024 envs, batched) | **4,617,173** | Peak throughput at high env count |
| Python↔C SPS (1 env, batched) | **167,956** | Single env baseline |

### SPS Scaling Table (PR7 Benchmark)

| Envs | Total SPS | Per-Env SPS | Config |
|------|-----------|-------------|--------|
| 1 | 167,956 | 167,956 | batch path, 5.0s wall |
| 16 | 1,714,272 | 107,142 | batch path, 5.0s wall |
| 64 | 3,353,676 | 52,401 | batch path, 5.0s wall |
| 256 | 4,351,384 | 16,998 | batch path, 5.0s wall |
| 1024 | 4,617,173 | 4,509 | batch path, 5.0s wall |

### PR7 Improvement vs PR3 Baseline

| Metric | PR3 (per-env ctypes) | PR7 (batched C) | Improvement |
|--------|---------------------|-----------------|-------------|
| SPS @ 64 envs | 335,800 | 3,353,676 | **+3,017,876 (+899%)** |

**Cause**: PR3 called ctypes per-env per-step (64 Python→C round-trips per tick). PR7 uses a single `fc_capi_batch_step_flat` C call that steps all envs and copies results into flat numpy arrays — eliminating per-env ctypes overhead.

### Measurement Details

**PR7 batched benchmark**:
- Command: `python3 RL/src/fc_env.py` (benchmark_scaling section)
- Config: 1/16/64/256/1024 envs, 5.0s wall each
- Viewer: off
- Type: accepted benchmark
- Machine: Linux x86_64 (user workstation)
- Episodes run full 63-wave Fight Caves with all NPC types (PR6 wave system active)

### Current Bottleneck

At high env counts (256+), per-env SPS drops significantly (52K→5K per-env at 1024). This is likely L1/L2 cache pressure: each FcEnvCtx is ~2KB+ (FcState), 1024 envs = ~2MB+ working set. The C step loop is sequential — no threading.

Possible further improvements:
- SIMD batching (step multiple envs in parallel via data parallelism)
- Thread pool for env stepping (divide envs across cores)
- PufferLib `env_binding.h` C extension (if python3-dev becomes available)

### Viewer Overhead

Not yet measured. The viewer runs as a separate process and does not affect training SPS.

## Regression Log

No regressions. PR7 is a pure improvement over PR3 baseline.

## History

| Date | PR | Change | Impact |
|------|-----|--------|--------|
| 2026-03-24 | PR3 | Initial SPS baseline | 335,800 SPS (64 envs, per-env ctypes) |
| 2026-03-24 | PR5 | NPC stat table restructure | No SPS change (C-only) |
| 2026-03-24 | PR6 | Wave system (63 waves + Jad) | No SPS change (C-only) |
| 2026-03-24 | PR7 | Batched C path | **3,353,676 SPS @ 64 envs (+899%)**, peak 4.6M @ 1024 envs |
