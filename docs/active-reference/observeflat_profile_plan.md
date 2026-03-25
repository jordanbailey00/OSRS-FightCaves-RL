# ObserveFlat Profile — Plan

## Status
**Created:** 2026-03-22
**Branch:** `jvm-architecture-improvement`
**Depends on:** Architecture isolation experiment (showed IPC is not the bottleneck;
the 16.5x gap between JVM tick-only and full env-only is the largest single-layer cost)

## Goal
Identify the highest-cost methods inside the JVM observation/reward extraction path,
at method-level granularity, to determine where to focus optimization.

## Profile Scope

### Target path
The `stepBatch()` method in `FastFightCavesKernelRuntime.kt` already instruments
four phases with `measureNanoTime`:

| Phase | What it measures | Variable |
|-------|------------------|----------|
| Apply actions | Action decode + apply to game state | `applyActionsNanos` |
| Tick | GameLoop.tick() — all 12 game stages | `tickNanos` |
| **ObserveFlat** | Flat observation packing | `observeFlatNanos` |
| **Projection** | Reward snapshot + terminal eval + reward encoding | `projectionNanos` |

This profile focuses on **observeFlat** and **projection** — the two phases that make up
the observation/reward extraction layer.

### Methods in scope

**ObserveFlat phase** (`runtime.observeFlatBatch(players)`):
- `HeadlessMain.observeFlatBatch()` → maps `observeFightCaveFlat(player)` per player
- `HeadlessFlatObservationBuilder.build(player)` — the core method
  - Base fields (30 floats): varbit lookups, skill lookups, inventory scans
  - NPC observation (8×13 floats): `visibleNpcTargets(player)` → NPC resolution + sort
- `HeadlessActionAdapter.visibleNpcTargets(player)` → `resolveVisibleNpcs(player)`
  - `sourceNpcsFromViewport(player)` — IntSet iteration, NPCs.indexed() lookups
  - `sourceNpcsFromInstanceOrRegion(player)` — fallback, 4× NPCs.at() region lookups
  - Filter: `npc.index != -1 && !npc.hide && !npc.contains("dead") && npc.tile.within()`
  - Sort: `compareBy(level, x, y, id, index)` — O(n log n) per player
- `packFlatObservationBatch()` — array allocation + copy

**Projection phase** (`projectionNanos`):
- `buildPostTickCache(player)` per env:
  - `fightCaveNpcs(player)` — region NPC iteration + HashSet filter
  - `player.inventory.count("shark")` — linear inventory scan
  - `fightCavePrayerPotionDoseCount(player)` — linear inventory scan with HashMap
- `captureRewardSnapshotCached(slot, cache)` — NPC sumOf/count, skill lookups
- `FastTerminalStateEvaluator.infer()` — 3 scalar comparisons (O(1))
- `FastRewardFeatureWriter.encodeTransition()` — 16 float scalars (O(1))
- `currentJadTraceCached(cache.npcs)` — linear NPC scan for Jad

## Methodology

### Approach: static code analysis + complexity estimation

Since JFR/async-profiler require a running JVM with the game engine loaded (not available
in this environment), this analysis uses:

1. **Complete source code reading** of every method in the observation/reward path
2. **Algorithmic complexity analysis** per method
3. **Data structure cost classification** (HashMap lookup vs linear scan vs allocation)
4. **Redundancy detection** (methods called multiple times per step)
5. **Cross-reference with existing timing instrumentation** (`FastStepMetrics` already
   reports applyActionsNanos, tickNanos, observeFlatNanos, projectionNanos)

### What this approach can determine
- Which methods are algorithmically expensive (O(n) vs O(1))
- Which methods create unnecessary allocations
- Which methods are called redundantly
- Which methods do string-based lookups vs int-based lookups
- The relative cost structure within each phase

### What this approach cannot determine
- Exact nanosecond timings per method (requires JFR/async-profiler)
- JIT warmup effects
- Cache miss rates
- GC pressure attribution to specific methods
- Exact % share per method (approximated from complexity analysis)
