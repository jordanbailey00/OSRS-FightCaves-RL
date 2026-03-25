# Puffer Ocean Implementation Experiment — Plan

## Status
**Created:** 2026-03-22
**Completed:** 2026-03-22
**Scope:** Isolated experiment under `claude/pufferlib/` — NOT mainline runescape-rl work.
**Env substitution:** CartPole/Breakout/Snake replaced by OneStateWorld/Asteroids at execution
time due to C binding compilation constraint (missing python3-dev). Boids retained. See report.

## Goal
Determine whether the runescape-rl training/integration architecture remains fast
when applied to known-fast Puffer Ocean environments, vs stock/idiomatic Puffer usage.

Central question: **"If we take known-fast Ocean envs and implement/train them using
the same or similar methods used in runescape-rl, do they still stay fast?"**

## Renamed Path Confirmation
- **From:** `/home/joe/projects/runescape-rl/claude/OSRS-env-review`
- **To:** `/home/joe/projects/runescape-rl/claude/pufferlib`
- **PDF:** `/home/joe/projects/runescape-rl/claude/pufferlib/PufferLib_Docs.pdf`

## Selected Environments

| # | Env | Obs Space | Action Space | Complexity | Selection Rationale |
|---|-----|-----------|--------------|------------|---------------------|
| 1 | **CartPole** | (4,) float32 | Discrete(2) | Simple | Fastest Ocean env — pure infrastructure overhead baseline. Any SPS delta is from the training architecture, not the env. |
| 2 | **Breakout** | (118,) float32 | Discrete(3) | Medium | 30x larger observation than CartPole. Tests encoder throughput. Variable episode lengths test rollout buffer management. |
| 3 | **Snake** | (11,11) int8 | Discrete(4) | Medium | Multi-agent: 16 envs × 256 agents = 4096 agents. Tests batch processing at high agent count, matching runescape-rl's scale. |
| 4 | **Boids** | (num_boids×4,) float32 | **MultiDiscrete([5,5])** | Medium | **Key comparison**: only selected env with MultiDiscrete actions, matching runescape-rl's action space style. Tests the separate-action-heads architecture. |

### Why these 4

- **CartPole** = infrastructure noise floor (trivial env, all overhead is training architecture)
- **Breakout** = medium obs complexity, game-like episode structure
- **Snake** = scale test with 4096+ concurrent agents
- **Boids** = MultiDiscrete action space, architecturally closest to runescape-rl

## Implementation Structure

```
pufferlib/implementations/
├── __init__.py
├── run_all_benchmarks.py        # Top-level runner
├── shared/                      # DOCUMENTED EXCEPTION: shared helpers
│   ├── __init__.py
│   ├── gumbel_sample.py         # Gumbel-max sampling + fused entropy (from RS)
│   └── benchmark_engine.py      # Standalone PPO training loop for benchmarking
├── cartpole/
│   ├── __init__.py
│   ├── policy.py                # RS-style MLP policy
│   └── benchmark.py             # Env-only + stock + RS-style benchmarks
├── breakout/
│   ├── (same structure)
├── snake/
│   ├── (same structure)
└── boids/
    ├── (same structure)
```

**Documented exception:** `shared/` contains two modules used by all envs:
- `gumbel_sample.py` — the core RS-style sampling optimization being tested
- `benchmark_engine.py` — standalone PPO loop for fair comparison

These are shared because they ARE the RS-style architecture under test. Duplicating
them per-env would add maintenance burden with no scientific benefit.

## Benchmark Methodology

### Three measurements per env:

1. **Env-only SPS** — raw env stepping with no training. Ceiling measurement.
   - Duration: 10 seconds
   - Action cache: 1024 pre-generated random actions
   - Reports: total agent-steps per second

2. **Stock training SPS** — full PPO training using `pufferlib.pytorch.sample_logits`
   - Uses `torch.multinomial` for categorical sampling
   - Uses PufferLib's default two-pass entropy computation
   - Same policy architecture as RS-style (to isolate sampling effect)

3. **RS-style training SPS** — full PPO training using `sample_logits_gumbel`
   - Uses Gumbel-max sampling (argmax on logits + Gumbel noise)
   - Uses fused single-pass entropy (avoids redundant softmax)
   - Same policy architecture as stock (same encoder, same heads)

### What is held constant between stock and RS-style:
- Same env, same num_envs, same env kwargs
- Same policy architecture and initialization
- Same batch size, bptt_horizon, minibatch_size
- Same optimizer (Muon or Adam, whichever PufferLib provides)
- Same hyperparameters (lr, gamma, clip_coef, etc.)
- Same hardware (device, GPU/CPU)

### What differs:
- **Sampling method**: torch.multinomial vs Gumbel-max (argmax)
- **Entropy computation**: two-pass softmax vs fused single-pass log_softmax
- **Priority shortcut**: RS-style uses `torch.randperm` when prio_alpha=0 (vs always using multinomial)

### Reproducing results
```bash
cd /home/joe/projects/runescape-rl/claude/pufferlib

# All envs:
python -m implementations.run_all_benchmarks --device cuda --timesteps 200000

# Single env:
python -m implementations.cartpole.benchmark --device cuda --timesteps 200000

# Time-based (more stable):
python -m implementations.cartpole.benchmark --device cuda --duration 30

# CPU-only:
python -m implementations.run_all_benchmarks --device cpu --timesteps 100000
```

## Comparison Methodology

For each env, report:
- Env-only SPS (ceiling)
- Stock training SPS
- RS-style training SPS
- RS/Stock ratio (>1.0 = RS is faster, <1.0 = RS is slower)
- Eval-phase SPS and Train-phase SPS (to isolate where overhead enters)

If the RS/Stock ratio is near 1.0 across all envs, the RS architecture adds
no meaningful overhead — the remaining runescape-rl bottlenecks are env-specific.

If the RS/Stock ratio deviates significantly, the RS training modifications
(Gumbel-max, fused entropy) have a measurable effect on these known-fast envs.

## What parts of runescape-rl are replicated

| RS-RL Component | Replicated? | Notes |
|-----------------|-------------|-------|
| 2-layer GELU MLP encoder | Yes | All policies use `layer_init + Linear + GELU + Linear + GELU` |
| Separate action heads per dimension | Yes (Boids) | Boids policy has per-dim heads matching `MultiDiscreteMLPPolicy` |
| `layer_init` with std=0.01/1.0 | Yes | Action heads std=0.01, value head std=1.0 |
| Gumbel-max sampling (T1.1) | Yes | `sample_logits_gumbel` copied directly from RS |
| Fused entropy (T2.1) | Yes | `_fused_entropy` copied directly from RS |
| prio_alpha=0 shortcut | Yes | `torch.randperm` instead of multinomial when alpha=0 |
| ConfigurablePuffeRL wrapper | Partial | Instrumentation not replicated (not relevant to SPS comparison) |
| Subprocess/shared-memory transport | No | JVM-specific, not applicable to Ocean envs |
| JVM integration (JPype) | No | RuneScape-specific |
| Reward shaping / curriculum | No | Fight Caves-specific |
| LSTM policy | No | MLP-only comparison (LSTM overhead would be equal in both) |

## What could NOT be replicated (and why)

- **Subprocess worker model**: Ocean envs are single-process C extensions, not JVM subprocesses
- **Shared memory transport**: No IPC needed for Ocean envs (direct memory access)
- **FastKernelVecEnv**: JVM-specific embedded kernel execution
- **Reward/curriculum system**: Fight Caves domain logic
- **Checkpoint/manifest/wandb integration**: Operational infrastructure, not architecture
