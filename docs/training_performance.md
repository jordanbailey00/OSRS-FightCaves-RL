# Training Performance

## Current Snapshot

- Status date: `2026-03-24`
- Current training-performance source of truth step: `TRAIN2`
- PR8b status: `blocked`
- Current bottleneck category: `Python/PufferLib rollout boundary plus CPU-owned host<->device transfer/materialization`
- Backend and device context:
  - backend: `native_preview`
  - env-only topology: `embedded_native_ctypes`
  - train and multi-worker topology: `subprocess shared_memory_v1`
  - learner device: `cuda`
  - Torch: `2.11.0+cu128`
  - CUDA: `12.8`
  - GPU: `NVIDIA GeForce RTX 5070 Ti`
- Current authoritative numbers:
  - env-only SPS: `407,855.93`
    - most recent comparable CUDA-path env-only rerun from `PR8a-followup`
  - best-known env-only SPS: `560,689.31`
    - earlier `PR8a` env-only run; not the current CUDA-path reference for the learner stack
  - normalized steady-state train SPS: `70,272.25`
    - current value from `PR8a.5`
  - normalized steady-state end-to-end SPS: `14,204.98`
    - current value from `PR8a.5`
  - retained train benchmark SPS: `394.12`
    - current value from `PR8a.5`
    - not comparable to steady-state throughput; dominated by startup/eval/checkpoint overhead
  - multi-worker aggregate SPS: `346,792.17`
    - most recent comparable CUDA-path rerun from `PR8a-followup`

Important comparability note:

- The current env-only and multi-worker numbers were last rerun in `PR8a-followup`.
- The current retained and normalized train numbers were last rerun in `PR8a.5`.
- These are the best current known measurements for each benchmark class, but they are not all from one identical benchmark invocation.
- TRAIN1 did not rerun the benchmark suite; it changed the interpretation of these numbers by proving that the current throughput class is already sufficient for real training/debugging work on the native stack, even though it is still insufficient for PR8b.
- TRAIN2 also did not rerun the benchmark suite, but it added a real-run sanity check confirming that the visible CPU-heavy periods still align with expected rollout/eval/orchestration ownership rather than an unexpected learner-device fallback.

## Current Bottleneck Interpretation

- The native Fight Caves backend is no longer the primary blocker:
  - env-only and multi-worker native throughput already clear their explicit floors.
- The end-to-end learner path remains near the old `~15k` to `16k` baseline because the active rollout/training architecture still does the following every cycle:
  - receives rollout data into CPU/NumPy ownership,
  - copies observations/rewards/dones onto CUDA,
  - materializes sampled actions back to CPU/NumPy,
  - crosses a Python vecenv/subprocess control boundary again.
- `PR8a.4` proved that narrow learner-loop desynchronization fixes can raise train-only throughput sharply without materially improving end-to-end throughput.
- `PR8a.5` then showed that rollout-side transfer/materialization remains the dominant end-to-end ceiling even after those learner-loop fixes.
- The missing PufferLib CUDA advantage kernel is still real, but it is not the dominant limiter on this host.
- Throughput status:
  - env-only: strong
  - train-only steady-state: improved substantially versus early normalized runs
  - end-to-end: effectively flat relative to the old baseline
- Current practical interpretation:
  - the present throughput class is already sufficient to train real checkpoints, evaluate them on the native backend, and inspect failures through the shared-backend viewer,
  - it is not sufficient to justify PR8b or to claim anything close to the explicit `250,000` SPS end-to-end target.
- TRAIN2 runtime sanity-check interpretation:
  - policy parameters and rollout buffers remain on `cuda:0`,
  - the noisy CPU-heavy periods still line up with expected `eval_env_recv`, `eval_policy_forward`, tensor-copy, and other orchestration buckets,
  - there is still no evidence that a surprise CPU learner fallback is the main reason the GPU appears quiet during ordinary runs.

## Current Recommendation

- Do not do `PR8b` on the current overall approach.
- If the explicit `250,000` end-to-end SPS target is still mandatory, the next performance move must be a broader rollout/learner-boundary redesign rather than another narrow `PR8a.x` pass.
- For the current project phase, accept the current throughput class as sufficient for useful training/debugging on the stable native backend and viewer stack.
- Treat the next active problem as agent behavior, not another local performance cleanup pass.
- Use performance investigation again only if project direction changes back toward PR8b or a broader trainer/rollout-boundary redesign.

## Performance History / Change Log

### 2026-03-24 — PR8a

- Benchmark shape:
  - retained benchmark suite
  - CPU-only learner build for the recorded train number
- Previous numbers:
  - none; first recorded active native performance snapshot
- New numbers:
  - env-only SPS: `560,689.31`
  - retained train SPS: `275.43`
  - multi-worker aggregate SPS: `307,600.36`
- Delta:
  - initial baseline
- Explanation:
  - first explicit gate pass
  - env-only and multi-worker floors passed
  - end-to-end train gate missed
  - train number was later found not to represent the intended CUDA learner path

### 2026-03-24 — PR8a-followup

- Benchmark shape:
  - same retained benchmark family
  - intended CUDA learner path
- Previous numbers:
  - env-only SPS: `560,689.31`
  - retained train SPS: `275.43`
  - multi-worker SPS: `307,600.36`
- New numbers:
  - env-only SPS: `407,855.93`
  - retained train SPS: `406.58`
  - multi-worker SPS: `346,792.17`
- Delta:
  - env-only: `-152,833.38` (`-27.3%`)
  - retained train: `+131.15` (`+47.6%`)
  - multi-worker: `+39,191.81` (`+12.7%`)
- Explanation:
  - learner path was moved onto CUDA
  - env-only rerun dropped versus the earlier best-known number, but still remained far above the floor
  - retained train improved, yet still remained far below target

### 2026-03-24 — PR8a.2

- Benchmark shape:
  - retained benchmark plus new normalized steady-state benchmark
- Previous numbers:
  - retained train SPS: `406.58`
  - normalized numbers: not yet tracked
- New numbers:
  - retained train SPS: `391.03`
  - normalized steady-state train SPS: `24,451.17`
  - normalized steady-state end-to-end SPS: `15,310.01`
- Delta:
  - retained train: `-15.55` (`-3.8%`)
  - normalized train/end-to-end: newly introduced
- Explanation:
  - startup/eval/checkpoint overhead was separated from steady-state throughput
  - revealed that the retained benchmark shape dramatically understates steady-state learner throughput
  - not apples-to-apples to compare retained train SPS directly with normalized SPS

### 2026-03-24 — PR8a.3

- Benchmark shape:
  - same normalized steady-state benchmark on the real subprocess native CUDA path
- Previous numbers:
  - normalized train SPS: `24,451.17`
  - normalized end-to-end SPS: `15,310.01`
- New numbers:
  - normalized train SPS: `24,537.00`
  - normalized end-to-end SPS: `15,364.81`
- Delta:
  - normalized train: `+85.83` (`+0.35%`)
  - normalized end-to-end: `+54.80` (`+0.36%`)
- Explanation:
  - effectively flat
  - the important change was diagnosis, not performance
  - PR8a.3 showed that the current ceiling was dominated by learner-loop synchronization and cleanup overhead rather than env speed

### 2026-03-24 — PR8a.4

- Benchmark shape:
  - same retained and normalized benchmark family
- Previous numbers:
  - retained train SPS: `388.01`
  - normalized train SPS: `24,537.00`
  - normalized end-to-end SPS: `15,364.81`
- New numbers:
  - retained train SPS: `393.90`
  - normalized train SPS: `71,409.83`
  - normalized end-to-end SPS: `14,438.38`
- Delta:
  - retained train: `+5.89` (`+1.5%`)
  - normalized train: `+46,872.83` (`+191.0%`)
  - normalized end-to-end: `-926.43` (`-6.0%`)
- Explanation:
  - major train-only improvement from removing `.item()` sync, rollout scalar extraction, and explained-variance cleanup cost
  - end-to-end throughput regressed because the visible wait shifted into rollout-side transfer/materialization
  - regression was expected from the new bottleneck surfacing, not from env slowdown

### 2026-03-24 — PR8a.5

- Benchmark shape:
  - same retained and normalized benchmark family
  - one extra larger-update probe for diagnosis only
- Previous numbers:
  - retained train SPS: `393.90`
  - normalized train SPS: `71,409.83`
  - normalized end-to-end SPS: `14,438.38`
- New numbers:
  - retained train SPS: `394.12`
  - normalized train SPS: `70,272.25`
  - normalized end-to-end SPS: `14,204.98`
  - larger-update diagnostic probe: `169,293.63` train-only SPS and `16,331.09` end-to-end SPS
- Delta:
  - retained train: `+0.22` (`+0.06%`)
  - normalized train: `-1,137.58` (`-1.6%`)
  - normalized end-to-end: `-233.40` (`-1.6%`)
- Explanation:
  - effectively flat to slightly regressed on the retained benchmark shape
  - larger updates improved train-only throughput materially, but not end-to-end throughput enough to matter for the explicit gate
  - confirms that rollout-side transfer/materialization remains the structural ceiling
  - the larger-update probe is not apples-to-apples with the retained base shape and should be treated as diagnostic evidence only

### 2026-03-24 — ARCH-PERF2

- Benchmark shape:
  - no new benchmark run
  - architecture investigation only
- Previous numbers:
  - env-only SPS: `407,855.93`
  - normalized train SPS: `70,272.25`
  - normalized end-to-end SPS: `14,204.98`
  - multi-worker SPS: `346,792.17`
- New numbers:
  - unchanged
- Delta:
  - flat
- Explanation:
  - no new measurement was taken
  - the interpretation changed: the current overall Python/PufferLib/send-recv/CPU-buffer approach is now the documented architectural ceiling, and the explicit `250,000` SPS target is not realistic on this approach without broader redesign

### 2026-03-24 — TRAIN1

- Benchmark shape:
  - no new performance benchmark run
  - first real native training, checkpoint evaluation, replay export, and viewer inspection pass
- Previous numbers:
  - env-only SPS: `407,855.93`
  - normalized steady-state train SPS: `70,272.25`
  - normalized steady-state end-to-end SPS: `14,204.98`
  - multi-worker aggregate SPS: `346,792.17`
- New numbers:
  - unchanged
- Delta:
  - flat
- Explanation:
  - TRAIN1 did not materially change the measured throughput numbers
  - the interpretation changed materially: the current throughput class is now proven sufficient for real training/debugging work, even though it remains far below the PR8b cutover target

### 2026-03-24 — TRAIN2

- Benchmark shape:
  - no new retained or normalized benchmark run
  - real native training rerun plus lightweight learner-path sanity check
- Previous numbers:
  - env-only SPS: `407,855.93`
  - normalized steady-state train SPS: `70,272.25`
  - normalized steady-state end-to-end SPS: `14,204.98`
  - multi-worker aggregate SPS: `346,792.17`
- New numbers:
  - unchanged
- Delta:
  - flat
- Explanation:
  - TRAIN2 did not materially change the known throughput numbers
  - it did materially confirm the phase interpretation: during real native training, policy parameters and rollout buffers remain on CUDA, while the visibly CPU-heavy periods still align with expected rollout/eval/orchestration buckets rather than a new unexpected fallback
