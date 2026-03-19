# Sim Flattening Execution Report

Date: 2026-03-16  
Scope: `training-env/sim` navigation-first flattening only

## 1) Before/After Tree (Module-Level)

### Before (wrapper-heavy)

```text
training-env/sim/
├── game/src/main/kotlin/...
├── game/src/test/kotlin/...
├── engine/src/main/kotlin/world/gregs/voidps/engine/...
├── network/src/main/kotlin/world/gregs/voidps/network/...
├── buffer/src/main/kotlin/world/gregs/voidps/buffer/...
├── cache/src/main/kotlin/world/gregs/voidps/cache/...
├── types/src/main/kotlin/world/gregs/voidps/type/...
└── config/src/main/kotlin/world/gregs/config/...
```

### After (module-first navigation)

```text
training-env/sim/
├── game/
│   ├── HeadlessMain.kt
│   ├── FightCaveSimulationRuntime.kt
│   ├── HeadlessActionAdapter.kt
│   ├── content/...
│   ├── headless/fast/...
│   ├── resources/...
│   ├── test/...
│   └── test-resources/...
├── engine/
│   ├── Contexts.kt
│   ├── EngineModules.kt
│   ├── GameLoop.kt
│   ├── client/...
│   ├── data/...
│   ├── entity/...
│   └── test/...
├── network/
│   ├── GameServer.kt
│   ├── LoginServer.kt
│   ├── client/...
│   ├── file/...
│   ├── login/...
│   └── test/...
├── buffer/
│   ├── Unicode.kt
│   ├── read/...
│   ├── write/...
│   └── test/...
├── cache/
│   ├── Cache.kt
│   ├── compress/...
│   ├── config/...
│   ├── definition/...
│   └── test/...
├── types/
│   ├── Area.kt
│   ├── Tile.kt
│   ├── area/...
│   └── test/...
├── config/
│   ├── Config.kt
│   ├── headless_manifest.toml
│   ├── headless_data_allowlist.toml
│   └── test/...
└── data/...
```

## 2) Move Summary

- Source/test/resource moves executed from `/tmp/sim_flatten_move_map.tsv`
- Total moved files: `1982`
- Flattened modules: `game`, `engine`, `network`, `buffer`, `cache`, `types`, `config`
- Removed module-local `src/main/kotlin` and `src/test/kotlin` wrapper depth in flattened modules.

## 3) Gradle Reconfiguration Report

Updated module build files:

- `training-env/sim/game/build.gradle.kts`
- `training-env/sim/engine/build.gradle.kts`
- `training-env/sim/network/build.gradle.kts`
- `training-env/sim/buffer/build.gradle.kts`
- `training-env/sim/cache/build.gradle.kts`
- `training-env/sim/types/build.gradle.kts`
- `training-env/sim/config/build.gradle.kts`

Applied source-set model (all flattened modules):

- main Kotlin: module root (`.`)
- test Kotlin: `test/`
- main resources: `resources/`
- test resources: `test-resources/`
- main excludes: `test/**`, `resources/**`, `test-resources/**`, `build/**`, `.gradle/**`

Game-specific path adjustments:

- `scriptMetadata.inputDirectory`: `content/`
- resource references moved from `src/main/resources` to `resources`
- shadow minimize package exclude updated to `sim/engine/log/**`

## 4) Package Migration Report

Prefix migrations applied in Kotlin sources under flattened modules:

- `world.gregs.voidps.engine` -> `sim.engine`
- `world.gregs.voidps.network` -> `sim.network`
- `world.gregs.voidps.buffer` -> `sim.buffer`
- `world.gregs.voidps.cache` -> `sim.cache`
- `world.gregs.voidps.type` -> `sim.types`
- `world.gregs.config` -> `sim.config`

Intentional hold:

- `game/GameModules.kt` keeps `Class.forName("world.gregs.voidps.storage.DatabaseStorage")` because `storage` is not part of this flattening scope.

## 5) Documentation/Path Updates

Updated for flattened sim layout:

- `docs/sim_flattening_plan.md`
- `docs/codebase_navigation_map.md`
- `docs/codebase_inventory_structure.md`
- `training-env/README.md`
- `training-env/sim/README.md`
- `training-env/sim/docs/determinism.md`
- `training-env/sim/docs/raw_flat_equivalence_plan.md`
- `training-env/sim/docs/sim_profiler_report.md`

## 6) Deferred Scope (Explicit)

Not flattened/restructured in this pass:

- `training-env/sim/data/**`
- `training-env/rl/**`

Reason: this pass is strictly module navigation flattening + Gradle/package correctness.

## 7) Validation Results

Executed module task checks:

- `./gradlew --no-daemon :game:tasks`
- `./gradlew --no-daemon :engine:tasks`
- `./gradlew --no-daemon :network:tasks`
- `./gradlew --no-daemon :types:tasks`
- `./gradlew --no-daemon :buffer:tasks`
- `./gradlew --no-daemon :cache:tasks`
- `./gradlew --no-daemon :config:tasks`

Compile/test-source checks:

- `./gradlew --no-daemon :game:compileKotlin :engine:compileKotlin :network:compileKotlin :types:compileKotlin :buffer:compileKotlin :cache:compileKotlin :config:compileKotlin`
- `./gradlew --no-daemon :game:testClasses :engine:testClasses :network:testClasses :types:testClasses :buffer:testClasses :cache:testClasses :config:testClasses`
- `./gradlew --no-daemon :game:test --tests HeadlessBootWithoutNetworkTest --tests HeadlessTickPipelineOrderTest`

Cross-workspace smoke:

- `./scripts/validate_smoke.sh`
- Result: passed.
- Runtime-root validation logs: `/home/jordan/code/runescape-rl-runtime/reports/validation`
