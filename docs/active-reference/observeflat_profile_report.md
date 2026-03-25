# ObserveFlat Profile — Report

## Status
**Completed:** 2026-03-22
**Branch:** `jvm-architecture-improvement`
**Method:** Static code analysis of complete call graph + algorithmic cost estimation

## 1. Top 10 Hottest Methods in the Observation/Reward Layer

Ranked by estimated per-step-per-env cost, based on algorithmic complexity and data
structure access patterns. The observation/reward layer runs after `tick()` and before
data is sent back to Python.

| Rank | Method | Phase | Per-Env Complexity | Primary Cost Driver |
|------|--------|-------|-------------------|---------------------|
| **1** | `resolveVisibleNpcs(player)` | ObserveFlat | O(region_npcs) | Iterates ALL NPCs in viewport/region, filters by alive/visible, builds ArrayList |
| **2** | `fightCaveNpcs(player)` | Projection | O(region_npcs) | Iterates ALL NPCs across 4 instance levels, filters by HashSet membership |
| **3** | `visibleNpcTargets(player)` sort | ObserveFlat | O(n log n) | Sorts visible NPCs by 5-field comparator (level, x, y, id, index) |
| **4** | `HeadlessFlatObservationBuilder.build()` NPC loop | ObserveFlat | O(visible_npcs × 13) | Per-NPC: NPCs.indexed() lookup + 2× skill lookups + contains("dead") + hasClock + jadState |
| **5** | `captureRewardSnapshotCached()` NPC aggregation | Projection | O(cache_npcs) | `sumOf { npc.levels.get() }` + `count { !contains("dead") }` over all Fight Cave NPCs |
| **6** | `inventory.count("shark")` | Both | O(inventory_size) | Linear scan of ~28 inventory slots, string comparison per slot |
| **7** | `fightCavePrayerPotionDoseCount()` | Both | O(inventory_size) | Linear scan + HashMap lookup per slot |
| **8** | `packFlatObservationBatch()` | ObserveFlat | O(envs × 134) | Allocates FloatArray(envs × 134), copies per-env arrays |
| **9** | `FastRewardFeatureWriter.packRows()` | Projection | O(envs × 16) | Allocates FloatArray(envs × 16), copies per-env arrays |
| **10** | `FastRewardFeatureWriter.encodeTransition()` | Projection | O(1) | 16 scalar arithmetic operations — cheap |

### Approximate share by group

| Group | Est. % of Obs/Reward Layer | Methods |
|-------|---------------------------|---------|
| **NPC resolution + filtering** | ~40-50% | `resolveVisibleNpcs`, `fightCaveNpcs`, viewport/region iteration |
| **NPC data extraction** | ~15-20% | Per-NPC skill lookups, contains("dead"), hasClock, jadState |
| **NPC sorting** | ~5-10% | 5-field comparator sort on visible NPCs |
| **Inventory scans** | ~10-15% | `inventory.count("shark")`, `fightCavePrayerPotionDoseCount` |
| **Array allocation + copy** | ~5-10% | `packFlatObservationBatch`, `packRows`, FloatArray creation |
| **Scalar arithmetic** | ~2-5% | `encodeTransition`, `infer`, base player fields |

## 2. Cost Category Breakdown

### A. NPC Resolution / Filtering (~40-50% of layer)

**What happens:** Every step, for every env, the code must determine which NPCs are visible.
This happens in TWO independent code paths:

1. **ObserveFlat:** `resolveVisibleNpcs(player)` at `HeadlessActionAdapter.kt:277`
   - Tries viewport first (`sourceNpcsFromViewport`) — iterates IntSet of NPC indices
   - Falls back to region scan (`sourceNpcsFromInstanceOrRegion`) — iterates ALL NPCs in 4 levels
   - Filters each NPC: `index != -1 && !hide && !contains("dead") && within(tile, radius)`
   - The `contains("dead")` check is string-based collection lookup per NPC

2. **Projection:** `fightCaveNpcs(player)` at `FastFightCavesKernelRuntime.kt:507`
   - Iterates ALL NPCs in instance (4 levels) or region
   - Filters: `it.id in FIGHT_CAVE_NPC_IDS` (HashSet, O(1) per NPC)
   - Returns `List<NPC>` — allocates new list every call

**Key insight:** These are TWO SEPARATE NPC iterations per env per step. `resolveVisibleNpcs`
gets the visible-and-alive NPCs for the observation. `fightCaveNpcs` gets all Fight Cave NPCs
(including dead) for the reward snapshot. They iterate the same underlying NPC collection but
with different filters.

**E1.1 optimization (already applied):** `buildPostTickCache` caches `fightCaveNpcs` result
for the projection phase, eliminating 4 redundant calls. But `resolveVisibleNpcs` (in
ObserveFlat) is NOT cached — it runs independently.

### B. NPC Data Extraction (~15-20%)

For each visible NPC (max 8), the observation builder does:
```kotlin
NPCs.indexed(target.npcIndex)          // Global array lookup
npc.levels.get(Skill.Constitution)     // Skill level lookup (×2: current + max)
npc.contains("dead")                    // String-based collection membership check
npc.hasClock("under_attack")            // String-based clock lookup
npc.jadTelegraphState                   // Property access (Jad only)
```
And constructs a `FlatObservedNpc` data class per NPC — object allocation.

**Cost driver:** `contains("dead")` and `hasClock("under_attack")` use string keys.
The NPC's internal data structure determines whether this is O(1) HashMap or O(n) list scan.

### C. NPC Sorting (~5-10%)

`visibleNpcTargets()` sorts the resolved NPCs by `compareBy(level, x, y, id, index)`.
For 8 visible NPCs, this is O(8 log 8) ≈ 24 comparisons × 5 fields each.

**Cost driver:** The comparator creates lambda-based `Comparator` chain. Each comparison
is a virtual call through the `compareBy` chain. Small but measurable with 64 envs × 8 NPCs.

### D. Inventory Scans (~10-15%)

Two linear scans of the ~28-slot inventory per env per step:
1. `player.inventory.count("shark")` — string comparison per slot
2. `fightCavePrayerPotionDoseCount(player)` — HashMap lookup per slot

These run in BOTH the ObserveFlat phase (inside `build()`) AND the Projection phase
(inside `buildPostTickCache()`). E1.1 caches the projection-phase result, but the
ObserveFlat phase does its own scan.

### E. Array Allocation + Copy (~5-10%)

Per step (across all envs):
- `FloatArray(envs × 134)` for packed observations
- `FloatArray(envs × 16)` for packed reward features
- `FloatArray(134)` per env for individual observations
- `ArrayList<NPC>` per env for visible NPC lists
- `List<HeadlessVisibleNpcTarget>` per env from sort
- `FlatObservedNpc` data class per visible NPC

**Cost driver:** Short-lived object churn triggers GC pressure. At 64 envs × 8 NPCs,
that's ~512 `FlatObservedNpc` objects per step, plus ~64 `FloatArray(134)` allocations.

### F. Scalar Arithmetic (~2-5%)

- `FastRewardFeatureWriter.encodeTransition()` — 16 floats of simple arithmetic (O(1))
- `FastTerminalStateEvaluator.infer()` — 3 integer comparisons (O(1))
- Base player fields — 30 simple property/varbit lookups (O(1) each)

These are cheap. Not optimization targets.

## 3. Top 3 Most Promising Optimization Targets

### Target 1: Unified NPC resolution (deduplicate the two NPC iteration paths)
**Risk: Low-Medium**

**Problem:** `resolveVisibleNpcs` (ObserveFlat) and `fightCaveNpcs` (Projection) both
iterate over the same NPC collection with different filters. This is two full NPC
scans per env per step.

**Fix:** Perform a single NPC iteration per env per step that produces both:
- The visible-and-alive NPC list (for observation)
- The all-Fight-Cave-NPC list (for reward)

Cache both results in `PostTickEnvCache` or equivalent. Move the `visibleNpcTargets`
call before the observation builder so the observation can use the cached result.

**Estimated impact:** Eliminates ~50% of NPC iteration cost.
**Risk assessment:** LOW. The filter criteria are well-defined. The main risk is ensuring
the cached list is valid at the point the observation is built (i.e., after the tick).

### Target 2: Eliminate per-env object allocation in the observation path
**Risk: Low**

**Problem:** Per env per step, the code allocates:
- `FloatArray(134)` for individual observation → then copies into packed batch array
- `ArrayList<NPC>` for viewport NPC lookup
- `List<HeadlessVisibleNpcTarget>` from sort
- `FlatObservedNpc` data class per visible NPC

**Fix:** Pre-allocate reusable buffers:
- Write observation directly into the packed batch `FloatArray` at the correct offset
  (skip the per-env intermediate `FloatArray(134)`)
- Pre-allocate NPC list and visible-target array, clear and reuse per env
- Use struct-of-arrays instead of `FlatObservedNpc` data classes

**Estimated impact:** Reduces GC pressure, eliminates ~512+ object allocations per step.
**Risk assessment:** LOW. This is a mechanical refactor — same data, fewer allocations.

### Target 3: Replace string-based NPC lookups with int-based lookups
**Risk: Low-Medium**

**Problem:** Multiple hot-path operations use string keys:
- `npc.contains("dead")` — checked per visible NPC per env (up to 64×8 = 512 calls/step)
- `npc.hasClock("under_attack")` — same frequency
- `inventory.count("shark")` — string comparison per inventory slot
- `npc.id in FIGHT_CAVE_NPC_IDS` — string HashSet lookup per NPC in region

**Fix:** Use integer flags or pre-computed boolean fields:
- Add `npc.isDead: Boolean` cached property (set during tick)
- Add `npc.isUnderAttack: Boolean` cached property
- Use item ID integers instead of string names for inventory scans
- Pre-compute NPC type flags during spawn (avoid per-step HashSet lookup)

**Estimated impact:** Eliminates string hashing/comparison in the tightest loops.
**Risk assessment:** LOW-MEDIUM. Requires changes to NPC data model, but the changes
are additive (new cached fields) not destructive.

## 4. Risk/Difficulty Assessment

| Target | Difficulty | Risk | Estimated SPS Impact |
|--------|-----------|------|---------------------|
| 1: Unified NPC resolution | Medium | Low | High — eliminates redundant scan |
| 2: Pre-allocated buffers | Low | Low | Medium — reduces GC pressure |
| 3: Int-based lookups | Medium | Low-Medium | Medium — faster per-NPC checks |
| Bonus: Skip NPC sort | Low | Low | Low-Medium — O(n log n) → O(n) |
| Bonus: Reuse observation buffer | Low | Low | Low — saves allocation |

## 5. Recommended Next Step

**A small env-side optimization PR** that combines Targets 1 and 2:

1. **Unified NPC cache** — single NPC iteration per env per step, producing both the
   visible NPC list (for observation) and the all-NPCs list (for reward). Store in
   `PostTickEnvCache` or a new per-step cache.

2. **Direct-write observation** — write observation floats directly into the packed batch
   `FloatArray` instead of allocating a per-env intermediate array. The
   `observationBuffer` parameter already exists in `stepBatch()` but isn't used for
   the per-env write path.

This PR should:
- Be a single focused change (~100-200 lines modified)
- Not change observation values or reward values (pure performance refactor)
- Include before/after timing from `FastStepMetrics` (observeFlatNanos + projectionNanos)
- Be verifiable via parity tests (same observation output, faster)

**Do NOT** pursue a structural refactor (e.g., changing the NPC data model to use int
flags) until after the unified cache + direct-write PR has been measured. The simpler
change may be sufficient.

## 6. What This Profile Does NOT Cover

- **Tick phase cost** (`tickNanos`) — the game engine loop. This profile focused only on
  the observation/reward layer after the tick.
- **JPype/JVM boundary cost** — the cost of crossing from Python to JVM. This is measured
  by the architecture isolation experiment.
- **Python-side observation encoding** — `encode_observation_for_policy()`. The fast path
  (v2_fast) uses JVM-side flat observations directly, bypassing this.
- **Exact nanosecond timing per method** — requires JFR/async-profiler on a running JVM.
  This analysis provides algorithmic cost ranking, not measured wall-clock attribution.

## Summary

| Question | Answer |
|----------|--------|
| **Hottest methods** | `resolveVisibleNpcs` and `fightCaveNpcs` — NPC iteration/filtering |
| **Strongest single target** | Unified NPC resolution: deduplicate the two NPC scans |
| **Primary cost category** | NPC iteration + filtering (~40-50%), not copying or arithmetic |
| **What follow-up PR should do** | Unified NPC cache + direct-write observation buffer |
| **Risk level** | Low — additive changes, verifiable via parity |
