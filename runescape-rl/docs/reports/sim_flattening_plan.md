# Sim Flattening Plan and Execution Map

Date: 2026-03-16  
Scope: `training-env/sim` only  
Goal: navigation-first flattening while preserving multi-module Gradle boundaries.

## 1) Module-by-Module Source/Test Flattening Map

### `game`

| Surface | Before | After | Package declaration change |
|---|---|---|---|
| Main Kotlin root | `game/src/main/kotlin/**` | `game/**` | No forced global rename for `content.*` / `headless.*` packages in this pass |
| Main resources | `game/src/main/resources/**` | `game/resources/**` | n/a |
| Test Kotlin root | `game/src/test/kotlin/**` | `game/test/**` | Existing test package names preserved |
| Test resources | `game/src/test/resources/**` | `game/test-resources/**` | n/a |

Key moved runtime files now directly visible under `game/`:
- `FightCaveSimulationRuntime.kt`
- `FastFightCavesKernelRuntime.kt`
- `FightCaveEpisodeInitializer.kt`
- `HeadlessActionAdapter.kt`
- `HeadlessObservationBuilder.kt`
- `HeadlessFlatObservationBuilder.kt`
- `HeadlessMain.kt`
- `ParityHarness.kt`
- `RuntimeBootstrap.kt`

### `engine`

| Surface | Before | After | Package declaration change |
|---|---|---|---|
| Main Kotlin root | `engine/src/main/kotlin/world/gregs/voidps/engine/**` | `engine/**` | `world.gregs.voidps.engine.*` -> `sim.engine.*` |
| Main resources | `engine/src/main/resources/**` | `engine/resources/**` | n/a |
| Test Kotlin root | `engine/src/test/kotlin/world/gregs/voidps/engine/**` | `engine/test/**` | `world.gregs.voidps.engine.*` -> `sim.engine.*` |
| Test resources | `engine/src/test/resources/**` | `engine/test-resources/**` | n/a |

### `network`

| Surface | Before | After | Package declaration change |
|---|---|---|---|
| Main Kotlin root | `network/src/main/kotlin/world/gregs/voidps/network/**` | `network/**` | `world.gregs.voidps.network.*` -> `sim.network.*` |
| Main resources | `network/src/main/resources/**` | `network/resources/**` | n/a |
| Test Kotlin root | `network/src/test/kotlin/world/gregs/voidps/network/**` | `network/test/**` | `world.gregs.voidps.network.*` -> `sim.network.*` |
| Test resources | `network/src/test/resources/**` | `network/test-resources/**` | n/a |

### `buffer`

| Surface | Before | After | Package declaration change |
|---|---|---|---|
| Main Kotlin root | `buffer/src/main/kotlin/world/gregs/voidps/buffer/**` | `buffer/**` | `world.gregs.voidps.buffer.*` -> `sim.buffer.*` |
| Main resources | `buffer/src/main/resources/**` | `buffer/resources/**` | n/a |
| Test Kotlin root | `buffer/src/test/kotlin/world/gregs/voidps/buffer/**` | `buffer/test/**` | `world.gregs.voidps.buffer.*` -> `sim.buffer.*` |
| Test resources | `buffer/src/test/resources/**` | `buffer/test-resources/**` | n/a |

### `cache`

| Surface | Before | After | Package declaration change |
|---|---|---|---|
| Main Kotlin root | `cache/src/main/kotlin/world/gregs/voidps/cache/**` | `cache/**` | `world.gregs.voidps.cache.*` -> `sim.cache.*` |
| Main resources | `cache/src/main/resources/**` | `cache/resources/**` | n/a |
| Test Kotlin root | `cache/src/test/kotlin/world/gregs/voidps/cache/**` | `cache/test/**` | `world.gregs.voidps.cache.*` -> `sim.cache.*` |
| Test resources | `cache/src/test/resources/**` | `cache/test-resources/**` | n/a |

### `types`

| Surface | Before | After | Package declaration change |
|---|---|---|---|
| Main Kotlin root | `types/src/main/kotlin/world/gregs/voidps/type/**` | `types/**` | `world.gregs.voidps.type.*` -> `sim.types.*` |
| Main resources | `types/src/main/resources/**` | `types/resources/**` | n/a |
| Test Kotlin root | `types/src/test/kotlin/world/gregs/voidps/type/**` | `types/test/**` | `world.gregs.voidps.type.*` -> `sim.types.*` |
| Test resources | `types/src/test/resources/**` | `types/test-resources/**` | n/a |

### `config`

| Surface | Before | After | Package declaration change |
|---|---|---|---|
| Main Kotlin root | `config/src/main/kotlin/world/gregs/config/**` | `config/**` | `world.gregs.config.*` -> `sim.config.*` |
| Main resources | `config/src/main/resources/**` | `config/resources/**` | n/a |
| Test Kotlin root | `config/src/test/kotlin/world/gregs/config/**` | `config/test/**` | `world.gregs.config.*` -> `sim.config.*` |
| Test resources | `config/src/test/resources/**` | `config/test-resources/**` | n/a |

## 2) Controlled Package Prefix Migration

Applied Kotlin package/import migration prefixes:

- `world.gregs.voidps.engine` -> `sim.engine`
- `world.gregs.voidps.network` -> `sim.network`
- `world.gregs.voidps.buffer` -> `sim.buffer`
- `world.gregs.voidps.cache` -> `sim.cache`
- `world.gregs.voidps.type` -> `sim.types`
- `world.gregs.config` -> `sim.config`

Notes:
- This pass intentionally did not force global `content.*` -> `sim.game.content.*` package renames.
- The legacy `Class.forName("world.gregs.voidps.storage.DatabaseStorage")` reflection string in `game/GameModules.kt` was left unchanged because `database` is not a flattened sim submodule in this pass.

## 3) Gradle Source-Set Reconfiguration Plan

For each flattened module (`game`, `engine`, `network`, `buffer`, `cache`, `types`, `config`):

- main Kotlin source: module root (`.`)
- main resources: `resources/`
- test Kotlin source: `test/`
- test resources: `test-resources/`
- main Kotlin excludes: `test/**`, `resources/**`, `test-resources/**`, `build/**`, `.gradle/**`

Game-specific task path updates:

- `scriptMetadata.inputDirectory`: `src/main/kotlin/content` -> `content`
- resource path references: `src/main/resources` -> `resources`
- shadow minimize exclude path updated for migrated engine logging package.

## 4) Data Scope Boundary (Deferred by Design)

Intentionally not flattened in this pass:

- `sim/data/**` large inherited content tree

Reason:

- high-risk content-boundary surgery was explicitly deferred
- this pass is navigation-first source/test flattening + build correctness
