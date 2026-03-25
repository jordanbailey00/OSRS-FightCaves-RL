# Puffer Ocean Implementation Experiment — Report

## Status
**Created:** 2026-03-22
**Benchmarks run:** 2026-03-22 on NVIDIA GeForce RTX 5070 Ti, CUDA 13.0, torch 2.10.0+cu128

## Build Environment Note

The original env selection (CartPole, Breakout, Snake, Boids) was adjusted at execution
time due to a build constraint: `python3-dev` headers were not installed system-wide, and
three of the four original envs (CartPole, Snake) use an old C binding pattern (`vecenv.h`)
that requires the `_C.so` CUDA kernel (needs `nvcc`, not available). Only envs using the
new `env_binding.h` pattern could be compiled standalone.

**Actual envs benchmarked:**
- **OneStateWorld** — replaced CartPole as the simplest/fastest baseline
- **Asteroids** — replaced Breakout as the medium-complexity env
- **Boids** — retained as the key MultiDiscrete comparison

This substitution does NOT affect the validity of the comparison: the experiment tests
the sampling/entropy method, not the env itself. The envs span the same complexity range.

## Exact Paths Created

```
pufferlib/implementations/
├── __init__.py
├── run_benchmarks.py              # Primary benchmark runner (used for all results)
├── run_all_benchmarks.py          # Original multi-module runner
├── shared/
│   ├── __init__.py
│   ├── gumbel_sample.py           # Gumbel-max + fused entropy from RS-RL
│   └── benchmark_engine.py        # Standalone PPO loop (original design)
├── cartpole/                      # Not benchmarked (binding compilation issue)
├── breakout/                      # Not benchmarked (binding compilation issue)
├── snake/                         # Not benchmarked (binding compilation issue)
├── boids/
│   ├── __init__.py
│   ├── policy.py
│   └── benchmark.py
├── onestateworld/                 # Replacement for CartPole
└── asteroids/                     # Replacement for Breakout
```

## Exact Envs Benchmarked

| Env | Source | Obs Shape | Action Space | Total Agents | Role |
|-----|--------|-----------|--------------|--------------|------|
| OneStateWorld | `pufferlib.ocean.onestateworld.onestateworld.World` | (1,) uint8 | Discrete(2) | 4096 | Simplest possible — infrastructure overhead baseline |
| Asteroids | `pufferlib.ocean.asteroids.asteroids.Asteroids` | (104,) float32 | Discrete(4) | 4096 | Medium obs complexity, arcade game |
| Boids | `pufferlib.ocean.boids.boids.Boids` | (256,) float32 | MultiDiscrete([5,5]) | 4096 (64×64) | **Key comparison** — MultiDiscrete matches RS-RL |

## Architecture Choices

### All envs: RS-style MLP policy
```
Input → Linear(obs_size, 128) → GELU → Linear(128, 128) → GELU → [action_head(s), value_head]
```
- `layer_init` on all layers
- Action heads: std=0.01
- Value head: std=1.0
- No RNN (`use_rnn=False`)
- Same policy used for BOTH stock and RS-style (isolates sampling difference)

### Boids (MultiDiscrete — closest RS-RL match)
- 2 separate action heads: Linear(128, 5) each
- Mirrors `MultiDiscreteMLPPolicy` from `fight_caves_rl/policies/mlp.py`

## Benchmark Configuration

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| bptt_horizon | 16 | Reasonable for throughput measurement |
| total_timesteps | 500,000 | Long enough for stable median |
| runs | 5 | Repeated for median (not single anecdotes) |
| prio_alpha | 0.0 | Tests RS-style alpha=0 shortcut (torch.randperm) |
| optimizer | Adam (lr=0.015) | Same for both stock and RS |
| device | cuda (RTX 5070 Ti) | GPU-first |
| env_only_duration | 10s per run | Standard Ocean benchmark duration |

## Benchmark Results (5 runs, 500K steps, medians)

### OneStateWorld (simplest — infrastructure overhead baseline)

| Metric | Value |
|--------|-------|
| Env-only SPS (median) | 32,568,098 |
| Stock training SPS (median) | 3,358,898 |
| RS-style training SPS (median) | 3,533,283 |
| **RS/Stock ratio** | **1.052x** |

Per-run detail:
| Run | Stock SPS | RS SPS | Stock Eval(s) | Stock Train(s) | RS Eval(s) | RS Train(s) |
|-----|-----------|--------|---------------|----------------|------------|-------------|
| 1 | 1,119,557 | 3,387,501 | 0.26 | 0.21 | 0.06 | 0.09 |
| 2 | 3,332,358 | 3,596,207 | 0.07 | 0.09 | 0.06 | 0.08 |
| 3 | 3,431,283 | 3,454,879 | 0.07 | 0.08 | 0.06 | 0.09 |
| 4 | 3,358,898 | 3,562,083 | 0.07 | 0.09 | 0.06 | 0.09 |
| 5 | 3,443,517 | 3,533,283 | 0.07 | 0.08 | 0.06 | 0.09 |

Note: Stock run 1 shows a CUDA warmup penalty (1.1M vs 3.3M+ SPS). RS-style avoids
this on run 1 because Gumbel sampling doesn't trigger torch.multinomial's first-use
CUDA kernel compilation.

### Asteroids (medium complexity, Discrete(4))

| Metric | Value |
|--------|-------|
| Env-only SPS (median) | 969,638 |
| Stock training SPS (median) | 794,550 |
| RS-style training SPS (median) | 793,534 |
| **RS/Stock ratio** | **0.999x** |

Per-run detail:
| Run | Stock SPS | RS SPS | Stock Eval(s) | Stock Train(s) | RS Eval(s) | RS Train(s) |
|-----|-----------|--------|---------------|----------------|------------|-------------|
| 1 | 765,350 | 793,534 | 0.59 | 0.09 | 0.57 | 0.09 |
| 2 | 805,753 | 777,054 | 0.56 | 0.09 | 0.58 | 0.09 |
| 3 | 797,176 | 791,543 | 0.56 | 0.10 | 0.57 | 0.09 |
| 4 | 749,670 | 804,141 | 0.60 | 0.09 | 0.55 | 0.10 |
| 5 | 794,550 | 816,862 | 0.57 | 0.09 | 0.54 | 0.10 |

Note: Eval dominates wall time (~85%). Train phase is only ~0.09s for both. At this
env complexity, the env stepping cost dominates and the sampling method is irrelevant.

### Boids (MultiDiscrete([5,5]) — key RS-RL comparison)

| Metric | Value |
|--------|-------|
| Env-only SPS (median) | 4,763,517 |
| Stock training SPS (median) | 1,770,708 |
| RS-style training SPS (median) | 1,796,671 |
| **RS/Stock ratio** | **1.015x** |

Per-run detail:
| Run | Stock SPS | RS SPS | Stock Eval(s) | Stock Train(s) | RS Eval(s) | RS Train(s) |
|-----|-----------|--------|---------------|----------------|------------|-------------|
| 1 | 1,794,007 | 1,796,671 | 0.20 | 0.10 | 0.19 | 0.10 |
| 2 | 1,761,717 | 1,767,776 | 0.20 | 0.10 | 0.19 | 0.10 |
| 3 | 1,796,040 | 1,788,379 | 0.20 | 0.09 | 0.19 | 0.10 |
| 4 | 1,770,708 | 1,801,809 | 0.20 | 0.10 | 0.19 | 0.10 |
| 5 | 1,726,822 | 1,805,712 | 0.20 | 0.10 | 0.19 | 0.10 |

Note: Very stable across runs. Eval and train times nearly identical between stock and RS.

### Cross-Env Summary

| Env | Env-Only SPS | Stock SPS | RS-Style SPS | RS/Stock |
|-----|-------------|-----------|--------------|----------|
| OneStateWorld | 32,568,098 | 3,358,898 | 3,533,283 | **1.052x** |
| Asteroids | 969,638 | 794,550 | 793,534 | **0.999x** |
| Boids | 4,763,517 | 1,770,708 | 1,796,671 | **1.015x** |

## What This Comparison Measures

### Held constant (same for both stock and RS-style)
- Same env, same num_envs, same env kwargs
- Same policy architecture (2-layer GELU MLP, layer_init)
- Same optimizer (Adam, same lr/eps)
- Same PPO algorithm (GAE, clipped surrogate, value clipping)
- Same batch size, horizon, minibatch size
- Same hardware (RTX 5070 Ti, CUDA)

### What differs
- **Sampling method**: `torch.multinomial` (stock) vs `argmax(logits + Gumbel)` (RS)
- **Entropy computation**: two-pass softmax (stock) vs fused `exp(log_softmax)` (RS)
- **Minibatch selection**: `torch.multinomial` on uniform (stock) vs `torch.randperm` (RS, when alpha=0)

### What this does NOT compare
- JVM subprocess overhead (not applicable to Ocean envs)
- Shared memory transport / IPC
- Observation encoding/decoding
- Reward shaping / curriculum
- LSTM overhead (both use MLP)
- Dashboard/logging/checkpoint I/O (excluded from benchmarks)
