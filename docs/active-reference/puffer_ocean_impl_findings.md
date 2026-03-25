# Puffer Ocean Implementation Experiment — Findings

## Status
**Completed:** 2026-03-22
**Hardware:** NVIDIA GeForce RTX 5070 Ti, CUDA 13.0, torch 2.10.0+cu128
**Method:** 5 runs per benchmark, 500K steps each, medians reported

## Central Result

| Env | RS/Stock Ratio | Interpretation |
|-----|---------------|----------------|
| OneStateWorld (trivial) | 1.052x | RS is ~5% faster (Gumbel avoids CUDA warmup on multinomial) |
| Asteroids (medium) | 0.999x | Identical — env stepping dominates, sampling irrelevant |
| Boids (MultiDiscrete) | 1.015x | RS is ~1.5% faster — within noise, effectively neutral |

**Answer to the central question: Yes, the RS-style trainer modifications (Gumbel-max
sampling + fused entropy) remain fast on known-fast Ocean envs. They introduce zero
measurable overhead and show a slight (~1-5%) advantage in some cases.**

## Detailed Findings

### 1. Do the RS-style trainer modifications remain fast on known-fast Ocean envs?

**Yes.** Across all 3 envs, the RS/Stock ratio ranges from 0.999x to 1.052x. The RS-style
modifications never slow things down. The slight advantage on OneStateWorld (1.052x) is
from Gumbel-max avoiding `torch.multinomial`'s first-use CUDA kernel compilation cost.

### 2. If not fast, where does the overhead appear?

**N/A — no overhead detected.** The eval-phase and train-phase timings are nearly identical
between stock and RS-style across all envs and all runs.

Specifically:
- **Eval phase**: RS eval times are equal or slightly lower (Gumbel argmax is cheaper than
  multinomial's CDF scan, but both are tiny relative to env stepping)
- **Train phase**: Identical. The fused entropy saves one softmax call but for small action
  spaces (2-5 actions) this is negligible

### 3. Does this make the remaining runescape-rl bottleneck look trainer-side or env-specific?

**Env-specific.** The RS training architecture adds no overhead on fast envs. Therefore,
any SPS gap in runescape-rl is coming from:

1. **JVM subprocess IPC** — the send/recv/materialize cycle across process boundaries.
   Ocean envs use direct C bindings with zero IPC. RS-RL uses subprocess workers with
   shared memory or pipe transport.

2. **Observation encoding** — `encode_observation_for_policy()` converts complex dict
   observations to flat tensors. Ocean envs provide native tensor observations directly.

3. **JVM step latency** — the Java Fight Caves simulation is inherently slower per step
   than a C-compiled Ocean env. This is a fundamental env-side cost.

4. **Reward shaping / curriculum overhead** — per-step reward computation and curriculum
   logic add cycles that Ocean envs don't have.

The training architecture itself (Gumbel-max, fused entropy, PPO loop, policy forward/backward)
is NOT the bottleneck.

### 4. Which env is the most informative comparison to runescape-rl?

**Boids.** It is the only benchmarked env with MultiDiscrete action spaces, which is
the action space type used by runescape-rl. The RS/Stock ratio of 1.015x on Boids confirms
that the MultiDiscrete action head architecture + Gumbel-max sampling does not introduce
overhead even on the most architecturally similar env.

Asteroids is useful as a secondary reference because its obs size (104) is closer to
runescape-rl's observation size than OneStateWorld's trivial 1-dim obs.

### 5. What are the top 3 implications for further runescape-rl optimization?

**1. Stop looking at the trainer for SPS gains.**
The Gumbel-max and fused entropy optimizations (T1.1 and T2.1) are correct and efficient.
They produce RS/Stock >= 1.0 on all tested envs. Further trainer-side micro-optimizations
will yield diminishing returns. The bottleneck is elsewhere.

**2. Focus on subprocess IPC and env step latency.**
The gap between env-only SPS and training SPS on Ocean envs is 2-10x (e.g., Boids:
4.8M env-only → 1.8M training). This 2.7x gap is the overhead of the PPO training loop
itself (rollout buffer management, policy forward, backward, optimizer). On runescape-rl,
the gap is much larger because JVM subprocess IPC adds another layer of latency on top
of this. Reducing IPC overhead is the highest-leverage optimization target.

**3. The Gumbel-max warmup advantage is real but minor.**
On OneStateWorld, stock run 1 shows 1.1M SPS vs RS run 1 at 3.4M SPS — a 3x difference.
This is because `torch.multinomial` triggers CUDA kernel compilation on first use. After
warmup, both converge. In production training (millions of steps), this warmup cost is
amortized to near-zero. It is not worth optimizing for.

## What This Experiment Does and Does Not Compare

### Does compare
- Gumbel-max vs torch.multinomial sampling throughput — **measured, ~equal**
- Fused vs unfused entropy computation — **measured, ~equal**
- torch.randperm vs torch.multinomial for uniform minibatch selection — **measured, ~equal**
- RS-style MultiDiscrete action heads vs single concat head — **measured via Boids, ~equal**

### Does NOT compare
- JVM subprocess IPC overhead (Ocean envs are in-process C extensions)
- Observation encoding/decoding (Ocean envs produce native tensors)
- Reward shaping computation (not applicable)
- Curriculum reset logic (not applicable)
- LSTM vs MLP (both benchmarks use MLP only)
- Shared memory vs pipe transport (not applicable)
- Dashboard/logging/checkpoint I/O (excluded)

## Stability Notes

- OneStateWorld stock run 1 shows a consistent CUDA warmup penalty (~1.1M vs ~3.4M SPS).
  This happens because `torch.multinomial` triggers first-use CUDA kernel compilation.
  RS-style's Gumbel sampling uses basic torch ops (rand, log, argmax) that are already
  compiled. This is a real but one-time cost, not a sustained overhead.

- All other runs across all envs show <5% run-to-run variance, confirming measurements
  are stable.

- No crashes, OOMs, or numerical instabilities observed in any run.

## Recommended Follow-Up Experiments

1. **Profile runescape-rl subprocess IPC** — measure send/recv/materialize time per step
   to quantify the actual IPC bottleneck. Compare shared_memory_v1 vs pipe_pickle.

2. **Benchmark runescape-rl with a no-op JVM env** — replace the Fight Caves simulation
   with a trivial JVM env that returns constant observations. This isolates JVM startup,
   IPC, and observation encoding overhead from the actual game simulation cost.

3. **Test LSTM overhead separately** — this experiment used MLP only. A follow-up should
   measure the LSTM cell (eval) vs nn.LSTM (train) dual-path overhead, which is an
   RS-specific architecture choice not tested here.
