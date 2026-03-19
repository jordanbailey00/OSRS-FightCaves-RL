# FCplan.md - Void Fight Caves Headless Extraction and 1:1 Parity Execution Plan

## Documentation Status

- Status: archive candidate under `pivot_documentation_triage.md`.
- Current authority: `pivot_plan.md` and `pivot_implementation_plan.md`.
- Retention reason: kept temporarily for historical extraction-plan context; do not use as the active workspace execution plan.

This plan is implementation-facing and must be executed in order.

## Iteration Tracking

Use this section to track each execution iteration and reduce implementation drift.

### Iteration 01 - Repository and Naming Alignment (2026-03-04)

- [x] Renamed the existing fork repo to "void-rsps-v2-5-0" and retained it as an unchanged archive fork for future replay/demo use.
- [x] Created a new standalone public repo "fight-caves-RL" (non-fork).
- [x] Rewired local git remotes:
  - origin -> jordanbailey00/fight-caves-RL
  - void-fork -> jordanbailey00/void-rsps-v2-5-0
  - upstream -> GregHib/void
- [x] Local root directory now moved/renamed by owner to `C:\Users\jorda\dev\personal_projects\fight-caves-RL`; path migration complete.
- [x] Step 1 execution started and completed in later iterations.

### Iteration 02 - Step 1 Manifest Authoring (2026-03-04)

- [x] Created `docs/extraction_manifest.md` with explicit Fight Caves keep/cut closure and validation rules.
- [x] Created `config/headless_manifest.toml` as machine-readable manifest for headless extraction enforcement.
- [x] Mapped direct Fight Caves closure dependencies from scripts, data files, item loadout sources, and runtime stage requirements.
- [x] Implemented Step 1 automated tests (`HeadlessManifestContainsFightCaveClosureTest`, `HeadlessManifestRejectsExcludedSystemsTest`).
- [x] Step 1 marked complete.

### Iteration 03 - Step 1 Test Execution and Validation (2026-03-05)

- [x] Added `game/src/test/kotlin/headless/manifest/HeadlessManifestContainsFightCaveClosureTest.kt`.
- [x] Added `game/src/test/kotlin/headless/manifest/HeadlessManifestRejectsExcludedSystemsTest.kt`.
- [x] Added shared parser/utility support in `game/src/test/kotlin/headless/manifest/HeadlessManifestTestSupport.kt`.
- [x] Executed Step 1 tests successfully with JDK 21 runtime.
- [x] Local root directory now moved/renamed by owner to `C:\Users\jorda\dev\personal_projects\fight-caves-RL`; path migration complete.

### Iteration 04 - Stop Checkpoint Before Step 2 (2026-03-05)

- [x] Retried local folder rename from parent directory after stopping Gradle daemons.
- [x] Local folder rename explicitly deferred by owner; directory is open in VSCode and manual rename will be done later.
- [x] Updated `FCplan.md`, `FCspec.md`, and `history/detailed_changelog.md` to reflect completed vs remaining work at this stopping point.
- [x] Confirmed Step 2 is the next execution target for tomorrow.

### Iteration 05 - Step 2 Headless Bootstrap Path (2026-03-05)

- [x] Added dedicated headless runtime entrypoint in `game/src/main/kotlin/HeadlessMain.kt`.
- [x] Added headless Koin service wiring in `game/src/main/kotlin/HeadlessModules.kt` and shared runtime bootstrap wiring in `game/src/main/kotlin/RuntimeBootstrap.kt`.
- [x] Added minimal headless tick pipeline in `getHeadlessTickStages()` with deterministic stage-order introspection helpers.
- [x] Added explicit runtime mode flag (`runtime.mode`) with startup logging for both headed and headless entrypoints.
- [x] Implemented and passed Step 2 tests:
  - `HeadlessBootWithoutNetworkTest`
  - `HeadlessTickPipelineOrderTest`
- [x] Ran headed regression smoke test and passed:
  - `content.area.karamja.tzhaar_city.TzhaarFightCaveTest`
- [x] Step 2 marked complete.

### Iteration 06 - Step 3 Fight Caves-Only Data Loading (2026-03-05)

- [x] Added allowlist-driven headless config loading in `game/src/main/kotlin/HeadlessDataLoading.kt`.
- [x] Wired `HeadlessMain.bootstrap` to load allowlist-scoped `ConfigFiles` instead of `dirs.txt` sweep.
- [x] Added `config/headless_data_allowlist.toml` and `docs/headless_data_loading.md` as Step 3 artifacts.
- [x] Restricted map/collision decode to headless allowlisted regions in `MapDefinitions`.
- [x] Added headless runtime gating for non-Fight-Caves `DiangoCodeDefinitions` eager load.
- [x] Added runtime state-file directory preparation before `configFiles.update()` for non-default modified/wildcard paths.
- [x] Added and passed Step 3 tests:
  - `HeadlessLoadsFightCaveDataOnlyTest`
  - `HeadlessFailsOnMissingFightCaveWaveDataTest`
  - `HeadlessCollisionRegionSubsetTest`
- [x] Added regression hardening in headless test overrides to isolate cache/modified paths from headed tests.
- [x] Ran headed regression smoke and passed:
  - `content.area.karamja.tzhaar_city.TzhaarFightCaveTest`
- [x] Step 3 marked complete.
- [x] Remaining: Step 4 script allowlist + script registry validation (completed in Iteration 07).

### Iteration 07 - Step 4 Script Allowlist and Registry Validation (2026-03-05)

- [x] Added headless script allowlist artifact `config/headless_scripts.txt`.
- [x] Added headless script loading/validation docs `docs/headless_scripts.md`.
- [x] Updated `ContentLoader` + `RuntimeBootstrap` + `HeadlessMain` to support headless-only script allowlist filtering while preserving headed/oracle full script loading.
- [x] Added startup script validation in `HeadlessScriptLoading.kt`:
  - required hooks registered
  - loaded script set equals allowlist
  - allowlist equals `config/headless_manifest.toml` `[scripts].required_classes`
- [x] Added runtime hook-introspection helpers in engine registries (`CombatApi`, `TimerApi`, `Skills`, `Moved`, `Spawn`, `Despawn`, `Death`) for explicit startup validation.
- [x] Added and passed Step 4 tests:
  - `HeadlessScriptRegistryContainsFightCaveHandlersTest`
  - `HeadlessScriptRegistryExcludesUnrelatedSystemsTest`
  - `HeadlessSingleWaveScriptSmokeTest`
- [x] Re-ran and passed headed regression smoke:
  - `content.area.karamja.tzhaar_city.TzhaarFightCaveTest`
- [x] Re-ran and passed Step 1 manifest tests after script manifest updates:
  - `headless.manifest.HeadlessManifestContainsFightCaveClosureTest`
  - `headless.manifest.HeadlessManifestRejectsExcludedSystemsTest`
- [x] Step 4 marked complete.
- [x] Remaining: Step 5 episode initialization contract implementation (completed in Iteration 08).

### Iteration 08 - Step 5 Episode Initialization Contract Implementation (2026-03-05)

- [x] Added `game/src/main/kotlin/FightCaveEpisodeInitializer.kt` implementing deterministic episode reset contract.
- [x] Updated `game/src/main/kotlin/HeadlessMain.kt` and `HeadlessRuntime` with `resetFightCaveEpisode(...)` API surface.
- [x] Added Step 5 artifact `docs/episode_init_contract.md`.
- [x] Added and passed Step 5 tests:
  - `EpisodeInitSetsFixedStatsTest`
  - `EpisodeInitSetsLoadoutAndConsumablesTest`
  - `EpisodeInitResetsWaveStateTest`
  - `EpisodeInitUsesProvidedSeedTest`
- [x] Updated headless data closure for episode loadout containers:
  - `config/headless_data_allowlist.toml`
  - `config/headless_manifest.toml`
  - added inventory/equipment `.invs.toml` dependencies and `definitions.inventories` setting key.
- [x] Installed Java 21 runtime locally (`EclipseAdoptium.Temurin.21.JDK`) to satisfy project test-bytecode runtime requirements.
- [x] Re-ran and passed regression gates after allowlist updates:
  - `headless.manifest.HeadlessManifestContainsFightCaveClosureTest`
  - `headless.manifest.HeadlessManifestRejectsExcludedSystemsTest`
  - `HeadlessLoadsFightCaveDataOnlyTest`
  - `HeadlessFailsOnMissingFightCaveWaveDataTest`
  - `HeadlessCollisionRegionSubsetTest`
  - `content.area.karamja.tzhaar_city.TzhaarFightCaveTest`
- [x] Step 5 marked complete.
- [x] Remaining: Step 6 action adapter implementation (completed in Iteration 09).

### Iteration 09 - Step 6 Action Adapter Implementation (2026-03-05)

- [x] Added `game/src/main/kotlin/HeadlessActionAdapter.kt` with stable headless action model and result schema.
- [x] Added stable action id mapping and supported action set: `Wait`, `WalkToTile`, `AttackVisibleNpc`, `ToggleProtectionPrayer`, `EatShark`, `DrinkPrayerPotion`, `ToggleRun`.
- [x] Implemented one-intent-per-tick gating via `headless_last_action_tick`.
- [x] Implemented deterministic visible NPC indexing and visible-index target mapping for attack actions.
- [x] Implemented standardized rejection reasons and mandatory `action_applied` metadata on every result.
- [x] Updated `game/src/main/kotlin/HeadlessMain.kt` and `HeadlessRuntime` with action-surface APIs:
  - `visibleFightCaveNpcTargets(...)`
  - `applyFightCaveAction(...)`
  - `applyFightCaveActionAndTick(...)`
- [x] Added and passed Step 6 tests:
  - `ActionWalkToUsesPathfinderTest`
  - `ActionAttackNpcDeterministicIndexMappingTest`
  - `ActionPrayerToggleParityTest`
  - `ActionEatDrinkLockoutRejectionTest`
  - `ActionInvalidTargetRejectionTest`
- [x] Step 6 marked complete.
- [x] Remaining: Step 7 observation builder contract implementation (completed in Iteration 10).


### Iteration 10 - Step 7 Observation Builder Contract (2026-03-05)

- [x] Added `game/src/main/kotlin/HeadlessObservationBuilder.kt` implementing `HeadlessObservationV1` schema.
- [x] Added strict schema/version contract constants:
  - `HEADLESS_OBSERVATION_SCHEMA_ID`
  - `HEADLESS_OBSERVATION_SCHEMA_VERSION`
  - `HEADLESS_OBSERVATION_COMPATIBILITY_POLICY`
  - `HEADLESS_OBSERVATION_V1_FIELD_ORDER`
- [x] Added deterministic ordered serialization via `toOrderedMap()` for stable top-level field ordering.
- [x] Wired observation APIs into `HeadlessRuntime` in `game/src/main/kotlin/HeadlessMain.kt`:
  - `observeFightCave(...)`
- [x] Exposed required player/wave/NPC/lockout state with deterministic NPC ordering aligned to Step 6 visible index mapping.
- [x] Added future-leakage opt-in debug field while omitting it by default.
- [x] Added Step 7 artifact `docs/observation_schema.md`.
- [x] Added and passed Step 7 tests:
  - `ObservationSchemaCompletenessTest`
  - `ObservationNpcOrderingDeterminismTest`
  - `ObservationNoFutureLeakageDefaultTest`
  - `ObservationVersioningContractTest`
- [x] Step 7 marked complete.
- [x] Remaining: Step 8 determinism hardening and RNG governance implementation (completed in Iteration 11).


### Iteration 11 - Step 8 Determinism and RNG Governance (2026-03-05)

- [x] Added tracked seeded RNG support in `types/src/main/kotlin/world/gregs/voidps/type/Random.kt`:
  - `setSeededRandom(seed, trackCalls = true)`
  - `randomDiagnostics()`
  - `randomCallCount()`
- [x] Updated `game/src/main/kotlin/FightCaveEpisodeInitializer.kt` to seed via shared tracked RNG path (no direct `kotlin.random.Random` import in closure code).
- [x] Added `game/src/main/kotlin/HeadlessReplayRunner.kt` implementing deterministic replay from `seed + action_trace`.
- [x] Added Step 8 artifact `docs/determinism.md`.
- [x] Added and passed Step 8 tests:
  - `DeterministicReplaySameSeedSameTraceTest`
  - `DeterministicReplayDifferentSeedDivergesTest`
  - `FightCaveClosureNoDirectRandomUsageTest`
  - `RngCounterMonotonicityTest`
- [x] Step 8 marked complete.
- [x] Remaining: Step 9 oracle vs headless differential parity harness implementation (completed in Iteration 12).

### Iteration 12 - Step 9 Oracle vs Headless Differential Parity Harness (2026-03-05)

- [x] Added shared runtime contract `game/src/main/kotlin/FightCaveSimulationRuntime.kt` so oracle and headless paths expose identical episode/action/observation APIs.
- [x] Added oracle bootstrap entrypoint `game/src/main/kotlin/OracleMain.kt` for full-runtime, no-network parity execution.
- [x] Added `game/src/main/kotlin/ParityHarness.kt`:
  - side-by-side runner for oracle and headless paths
  - per-step `ParitySnapshot` capture
  - fail-fast `ParityMismatch` reporting with field-path diffs
  - first-divergence artifact persistence under `temp/parity/first_divergence/`
- [x] Added Step 9 test support and scenario traces/hooks in `game/src/test/kotlin/headless/parity/ParityHarnessTestSupport.kt`.
- [x] Added Step 9 tests:
  - `ParityHarnessSingleWaveTraceTest`
  - `ParityHarnessFullRunTraceTest`
  - `ParityHarnessJadHealerScenarioTest`
  - `ParityHarnessTzKekSplitScenarioTest`
- [x] Added Step 9 artifact `docs/parity_harness.md`.
- [x] Added parity stability fixes discovered during differential debugging:
  - initialized `DropTables` in headless cache bootstrap to prevent NPC death/drop cleanup divergence
  - cleared `GameObjects` during parity harness runtime reset
  - allowed explicit test override of `headless.map.regions` without loader clobbering
- [x] Executed and passed Step 9 test gate under Java 21:
  - `./gradlew :game:test --tests "ParityHarnessSingleWaveTraceTest" --tests "ParityHarnessFullRunTraceTest" --tests "ParityHarnessJadHealerScenarioTest" --tests "ParityHarnessTzKekSplitScenarioTest" --no-daemon`
- [x] Step 9 marked complete.
- [x] Remaining: Step 10 runtime pruning and packaging (completed in Iteration 13).

### Iteration 13 - Step 10 Runtime Pruning and Packaging (2026-03-05)

- [x] Added headless pruning defaults in `loadRuntimeSettings(...)` for `runtime.mode=headless`:
  - `bots.count=0`
  - `events.shootingStars.enabled=false`
  - `events.penguinHideAndSeek.enabled=false`
  - `storage.autoSave.minutes=0`
  - `spawns.npcs=tzhaar_city.npc-spawns.toml`
  - `spawns.items=tzhaar_city.items.toml`
  - `spawns.objects=tzhaar_city.objs.toml`
- [x] Added fail-fast headless startup guards in `game/src/main/kotlin/HeadlessMain.kt`:
  - excluded runtime stages must remain disabled
  - excluded script namespaces must not be loaded
  - required pruned settings must match expected values
- [x] Added deletion candidate inventory generator:
  - `game/src/main/kotlin/HeadlessDeletionCandidates.kt`
  - `:game:generateHeadlessDeletionCandidates`
  - generated artifact `history/deletion_candidates.md`
- [x] Added headless packaging tasks/artifacts in `game/build.gradle.kts`:
  - `:game:headlessShadowJar` (`fight-caves-headless.jar`, `Main-Class=HeadlessMain`)
  - `headlessDistZip` distribution (`distributionBaseName=fight-caves-headless`)
  - `:game:packageHeadless` aggregator task (`generateHeadlessDeletionCandidates + headlessDistZip`)
- [x] Added Step 10 docs artifact `docs/runtime_pruning.md`.
- [x] Added and passed Step 10 tests:
  - `HeadlessPackageStartsWithoutExcludedSystemsTest`
  - `HeadedModeStillPassesBaselineFightCaveTests`
  - `HeadlessDeletionCandidateInventoryTest`
- [x] Executed and passed required build artifact validation:
  - `./gradlew :game:packageHeadless --no-daemon`
- [x] Re-ran and passed headed regression gate:
  - `./gradlew :game:test --tests "content.area.karamja.tzhaar_city.TzhaarFightCaveTest" --no-daemon`
- [x] Re-ran and passed Step 9 parity regression matrix post-Step-10 changes:
  - `./gradlew :game:test --tests "ParityHarnessSingleWaveTraceTest" --tests "ParityHarnessFullRunTraceTest" --tests "ParityHarnessJadHealerScenarioTest" --tests "ParityHarnessTzKekSplitScenarioTest" --no-daemon`
- [x] Step 10 marked complete.
- [x] Remaining: Step 11 performance validation (post-parity only).

### Iteration 14 - Step 11 Performance Validation (2026-03-05)

- [x] Added high-throughput batch stepping API in `game/src/main/kotlin/HeadlessBatchStepping.kt`:
  - `runFightCaveBatch(...)`
  - `HeadlessBatchStepSnapshot`
  - `HeadlessBatchRunResult`
- [x] Applied Step 11 hot-path optimizations without behavior changes:
  - single-pass prayer potion inventory counting in `HeadlessObservationBuilder.kt`
  - validated/optimized tick loop path in `HeadlessMain.kt`
- [x] Added Step 11 tests:
  - `HeadlessStepRateBenchmarkTest`
  - `HeadlessLongRunStabilityTest`
  - `HeadlessBatchSteppingParityTest`
  - `HeadlessPerformanceReportGenerationTest`
- [x] Generated benchmark artifact:
  - `history/performance_benchmark.log`
- [x] Added Step 11 docs artifact:
  - `docs/sim_profiler_report.md`
- [x] Re-ran and passed mandatory full Step 9 parity regression matrix post-optimization:
  - `ParityHarnessSingleWaveTraceTest`
  - `ParityHarnessFullRunTraceTest`
  - `ParityHarnessJadHealerScenarioTest`
  - `ParityHarnessTzKekSplitScenarioTest`
- [x] Recorded and resolved a verification environment issue:
  - initial combined run failed under Java 17 (`UnsupportedClassVersionError`, class file 65.0)
  - reran under Java 21 and all Step 11 + parity tests passed
- [x] Step 11 marked complete.
- [x] Remaining: Step 12 final acceptance and release gate.

### Iteration 15 - Step 12 Final Acceptance Re-Run With Test Speedups (2026-03-06)

- [x] Applied acceptance-safe test speedups:
  - physically pruned unrelated legacy tests under `game/src/test/kotlin/content/**`
  - retained required headed regression test `content/area/karamja/tzhaar_city/TzhaarFightCaveTest.kt`
  - hardened `e2eTest` worker config in `game/build.gradle.kts` (`maxHeapSize=2048m`, `maxParallelForks=1`, `forkEvery=1`)
- [x] Re-ran Step 12 mandatory acceptance sequence under Java 21:
  - `./gradlew clean --no-daemon` (PASS, ~17s)
  - `./gradlew :game:test --no-daemon` (PASS, ~5m45s)
  - `./gradlew :game:test --tests "*Headless*" --no-daemon` (PASS, ~2m38s)
  - `./gradlew :game:test --tests "*Parity*" --no-daemon` (PASS, ~2m44s)
  - `./gradlew :game:test --tests "content.area.karamja.tzhaar_city.TzhaarFightCaveTest" --no-daemon` (PASS, ~40s)
  - `./gradlew :game:headlessDist --no-daemon` (PASS, ~25s)
  - `./gradlew :game:e2eTest --no-daemon` (PASS, ~2m26s)
- [x] Published Step 12 release gate artifact:
  - `history/release_candidate_step12.md`
- [x] Step 12 marked complete.
- [x] Remaining: Step 13 post-prune validation closeout (completed in Iteration 16).

### Iteration 16 - Step 13 Post-Prune Validation Closeout (2026-03-06)

- [x] Confirmed Step 13 physical prune state remains intact:
  - deleted modules: `database/`, `tools/`
  - `settings.gradle.kts` no longer includes removed modules
- [x] Created recovery refs prior to closeout validation:
  - branch: `recovery/pre-step13-prune-2026-03-06`
  - tag: `pre-step13-prune-2026-03-06`
- [x] Re-ran Step 13 prune verification tests and passed:
  - `ProjectTreeMatchesApprovedManifestTest`
  - `ForbiddenPathsAbsentTest`
  - `HeadlessDeletionCandidateInventoryTest`
- [x] Regenerated deletion-candidate inventory:
  - `./gradlew :game:generateHeadlessDeletionCandidates --no-daemon`
  - output `history/deletion_candidates.md` (`modules=0`, `codeFiles=872`, `dataFiles=3012`)
- [x] Updated prune manifest/test contract:
  - `config/headless_prune_manifest.toml` version `2`
  - added `pruned_test_root` + `retained_test_files`
  - updated prune support/assertion tests to enforce retained test-file closure
- [x] Updated Step 13 report artifact:
  - `history/repo_prune_report.md`
- [x] Step 13 marked complete.
- [x] Remaining: none.

### Iteration 17 - Textual Parity Alignment Hardening (2026-03-06)

- [x] Aligned docs + manifests + runtime guards on excluded headless stage set by explicitly including `FloorItems`.
- [x] Updated headless startup/pruning tests to assert `FloorItems` is excluded from headless stage pipeline.
- [x] Corrected episode-init docs to match implementation (`fightCave.startWave(..., start = false)`).
- [x] Clarified HP/Constitution value scale in spec (`70 HP` display corresponds to internal value `700`).
- [x] Remaining: none.

### Step Status Snapshot

- [x] Step 0 - Baseline Stabilization and Freeze
- [x] Step 1 - Define and Lock Extraction Manifest
- [x] Step 2 - Headless Bootstrap Path (No Networking)
- [x] Step 3 - Fight Caves-Only Data Loading
- [x] Step 4 - Script Allowlist and Content Loading
- [x] Step 5 - Episode Initialization Contract Implementation
- [x] Step 6 - Action Adapter (One Intent Per Tick)
- [x] Step 7 - Observation Builder Contract
- [x] Step 8 - Determinism Hardening and RNG Governance
- [x] Step 9 - Oracle vs Headless Differential Parity Harness
- [x] Step 10 - Runtime Pruning and Packaging
- [x] Step 11 - Performance Validation (Post-Parity Only)
- [x] Step 12 - Final Acceptance and Release Gate
- [x] Step 13 - Physical Repository Prune (Hard Delete Pass)

Rules:
- Do not skip steps.
- Do not mark a step complete unless all listed tests pass.
- If a test fails, fix root cause before continuing.
- If code behavior conflicts with `FCspec.md`, update code to match spec or update spec with evidence from current authoritative Void behavior.

Traceability rule:
- Every PR/change set must reference the relevant `FCspec.md` sections by number.

---

## Step 0 - Baseline Stabilization and Freeze

Spec linkage:
- `FCspec.md` sections 1, 2, 10

Required action items:
1. Fix current compile blockers in the existing repo without changing gameplay semantics.
2. Run baseline Fight Caves tests and core combat/movement/prayer tests in current full-runtime mode.
3. Capture baseline test report and create a baseline state document with commit hash, environment details, and passing tests.
4. Record known technical debt and known flaky tests (if any) before extraction starts.

Required tests:
1. `./gradlew :game:test --tests "content.area.karamja.tzhaar_city.TzhaarFightCaveTest"`
2. `./gradlew :game:test --tests "content.entity.combat.*"`
3. `./gradlew :game:test --tests "content.skill.prayer.*"`
4. `./gradlew :game:test --tests "content.skill.constitution.*"`

Step artifacts:
1. `history/baseline_step0.md` with commit id and pass/fail snapshot.
2. CI log artifacts or stored local logs.

Exit criteria:
- Baseline compile passes and listed tests pass on unextracted runtime.

---

## Step 1 - Define and Lock Extraction Manifest

Spec linkage:
- `FCspec.md` sections 3, 4, 6

Required action items:
1. Create an explicit keep/cut manifest covering code modules, scripts, and data files.
2. Enumerate all Fight Caves closure dependencies transitively from:
   - `TzhaarFightCave.kt`
   - movement/pathing
   - combat/hits
   - prayer
   - food/potions
   - inventory/equipment/ammo
3. Define explicit exclusion list for non-runtime systems.
4. Add a static check to ensure new files outside manifest are not accidentally loaded in headless mode.

Required tests:
1. New unit test: `HeadlessManifestContainsFightCaveClosureTest`.
2. New unit test: `HeadlessManifestRejectsExcludedSystemsTest`.

Step artifacts:
1. `docs/extraction_manifest.md`.
2. `config/headless_manifest.toml` (or equivalent machine-readable manifest).

Exit criteria:
- Manifest is complete, reviewed, and test-validated.

---

## Step 2 - Headless Bootstrap Path (No Networking)

Spec linkage:
- `FCspec.md` sections 2.2, 6, 10

Required action items:
1. Add dedicated headless entrypoint and service wiring (Koin/module setup).
2. Ensure headless path does not start login/game servers or packet decoding loops.
3. Build a minimal stage pipeline preserving required tick order semantics for retained systems.
4. Add explicit runtime mode flag (`headed` vs `headless`) and log startup mode.

Required tests:
1. New integration test: `HeadlessBootWithoutNetworkTest`.
2. New integration test: `HeadlessTickPipelineOrderTest`.
3. Regression test: existing headed mode still boots and tests pass.

Step artifacts:
1. `HeadlessMain` (or equivalent).
2. `HeadlessModules` configuration.

Exit criteria:
- Headless runtime boots and ticks without network stack.

---

## Step 3 - Fight Caves-Only Data Loading

Spec linkage:
- `FCspec.md` sections 3, 4, 6

Required action items:
1. Implement headless data loader using an explicit allowlist (no full `dirs.txt` sweep).
2. Include Fight Caves-specific config files and all required shared definitions.
3. Limit map/collision loading to Fight Caves source regions plus required dynamic-instance support.
4. Add assertions that missing allowlisted data causes startup failure with clear errors.

Required tests:
1. New integration test: `HeadlessLoadsFightCaveDataOnlyTest`.
2. New integration test: `HeadlessFailsOnMissingFightCaveWaveDataTest`.
3. New integration test: `HeadlessCollisionRegionSubsetTest`.

Step artifacts:
1. `config/headless_data_allowlist.toml`.
2. Loader docs in `docs/headless_data_loading.md`.

Exit criteria:
- Headless runtime loads only allowlisted data and still runs Fight Caves.

---

## Step 4 - Script Allowlist and Content Loading

Spec linkage:
- `FCspec.md` sections 3, 6

Required action items:
1. Add headless script manifest (Fight Caves closure scripts only).
2. Keep full script loading behavior unchanged for oracle/headed mode.
3. Add startup validation that required script hooks are registered.
4. Add guard against accidental script drift (new script dependencies not in manifest).

Required tests:
1. New integration test: `HeadlessScriptRegistryContainsFightCaveHandlersTest`.
2. New integration test: `HeadlessScriptRegistryExcludesUnrelatedSystemsTest`.
3. Differential smoke: single wave run executes with expected script hooks.

Step artifacts:
1. `config/headless_scripts.txt`.
2. `docs/headless_scripts.md`.

Exit criteria:
- Headless script registry is minimal and sufficient.

---

## Step 5 - Episode Initialization Contract Implementation

Spec linkage:
- `FCspec.md` sections 3, 4, 5.3

Required action items:
1. Implement deterministic episode reset function for headless API.
2. Initialize fixed stats, resources, equipment, and inventory exactly as defined.
3. Ensure Fight Caves wave state and instance state are reset correctly.
4. Ensure run/prayer toggles and timers start from contract-defined values.

Required tests:
1. New test: `EpisodeInitSetsFixedStatsTest`.
2. New test: `EpisodeInitSetsLoadoutAndConsumablesTest`.
3. New test: `EpisodeInitResetsWaveStateTest`.
4. New test: `EpisodeInitUsesProvidedSeedTest`.

Step artifacts:
1. `FightCaveEpisodeInitializer` (or equivalent).
2. `docs/episode_init_contract.md`.

Exit criteria:
- Reset state is reproducible and contract-compliant.

---

## Step 6 - Action Adapter (One Intent Per Tick)

Spec linkage:
- `FCspec.md` section 5.1

Required action items:
1. Define stable action enum/id mapping.
2. Implement per-tick action ingestion and intent application to native Void systems.
3. Implement deterministic visible-NPC indexing and target mapping rules.
4. Implement standardized rejected-action reason enums and `action_applied` metadata.
5. Ensure movement actions use canonical pathfinder and walking queue behavior.

Required tests:
1. New test: `ActionWalkToUsesPathfinderTest`.
2. New test: `ActionAttackNpcDeterministicIndexMappingTest`.
3. New test: `ActionPrayerToggleParityTest`.
4. New test: `ActionEatDrinkLockoutRejectionTest`.
5. New test: `ActionInvalidTargetRejectionTest`.

Step artifacts:
1. `HeadlessActionAdapter`.
2. `HeadlessActionResult` schema.

Exit criteria:
- Every action is deterministic, validated, and correctly reported.

---

## Step 7 - Observation Builder Contract

Spec linkage:
- `FCspec.md` section 5.2

Required action items:
1. Build observation schema with strict field definitions and stable ordering.
2. Ensure deterministic NPC ordering for fixed indexing.
3. Expose required player/wave/NPC state and lockout fields.
4. Omit future-leakage fields by default.
5. Version the observation schema and add backward compatibility policy.

Required tests:
1. New test: `ObservationSchemaCompletenessTest`.
2. New test: `ObservationNpcOrderingDeterminismTest`.
3. New test: `ObservationNoFutureLeakageDefaultTest`.
4. New test: `ObservationVersioningContractTest`.

Step artifacts:
1. `HeadlessObservationV1`.
2. `docs/observation_schema.md`.

Exit criteria:
- Observation output is deterministic and policy-ready.

---

## Step 8 - Determinism Hardening and RNG Governance

Spec linkage:
- `FCspec.md` sections 2.1, 7

Required action items:
1. Ensure all Fight Caves closure randomness uses shared seeded RNG path.
2. Add deterministic replay runner that accepts `seed + action_trace`.
3. Add optional RNG call counter/state tracking for diff diagnostics.
4. Add lint/static checks preventing direct `kotlin.random.Random` use in closure.

Required tests:
1. New test: `DeterministicReplaySameSeedSameTraceTest`.
2. New test: `DeterministicReplayDifferentSeedDivergesTest`.
3. New test: `FightCaveClosureNoDirectRandomUsageTest`.
4. New test: `RngCounterMonotonicityTest`.

Step artifacts:
1. `HeadlessReplayRunner`.
2. RNG governance docs in `docs/determinism.md`.

Exit criteria:
- Replay determinism is enforced and verifiable.

---

## Step 9 - Oracle vs Headless Differential Parity Harness

Spec linkage:
- `FCspec.md` section 8

Required action items:
1. Build side-by-side runner for oracle (full runtime path) and headless path.
2. Apply identical initial state, seed, and action trace to both.
3. Compare per tick with fail-fast mismatch reporting.
4. Persist first-divergence diagnostic artifacts for reproducibility.

Required tests:
1. New integration test: `ParityHarnessSingleWaveTraceTest`.
2. New integration test: `ParityHarnessFullRunTraceTest`.
3. New integration test: `ParityHarnessJadHealerScenarioTest`.
4. New integration test: `ParityHarnessTzKekSplitScenarioTest`.

Step artifacts:
1. `ParityHarness` runner.
2. `ParitySnapshot` schema.
3. `docs/parity_harness.md`.

Exit criteria:
- Differential parity tests pass for required scenario matrix.

---

## Step 10 - Runtime Pruning and Packaging

Spec linkage:
- `FCspec.md` section 6

Required action items:
1. Remove excluded runtime systems from headless package path.
2. Keep oracle/headed mode intact for future differential validation.
3. Add build tasks for headless artifact creation.
4. Add startup assertions to fail if excluded systems are accidentally active.
5. Produce a deletion-candidate inventory from `config/headless_manifest.toml` for all files/directories not required by headless runtime.

Required tests:
1. New integration test: `HeadlessPackageStartsWithoutExcludedSystemsTest`.
2. New integration test: `HeadedModeStillPassesBaselineFightCaveTests`.
3. Build test: headless artifact generation task succeeds.
4. New test: `HeadlessDeletionCandidateInventoryTest`.

Step artifacts:
1. headless distribution/build target.
2. `docs/runtime_pruning.md`.
3. `history/deletion_candidates.md`.

Exit criteria:
- Headless runtime is minimal and functionally complete.

---

## Step 11 - Performance Validation (Post-Parity Only)

Spec linkage:
- `FCspec.md` section 9

Required action items:
1. Profile hot paths (tick loop, combat, pathing, observation serialization).
2. Optimize allocations and reduce logging in hot path.
3. Add batch stepping support only if parity remains unchanged.
4. Re-run complete parity suite after each optimization batch.

Required tests:
1. New performance test: `HeadlessStepRateBenchmarkTest`.
2. New soak test: `HeadlessLongRunStabilityTest`.
3. Mandatory full parity regression rerun.

Step artifacts:
1. `docs/sim_profiler_report.md`.
2. Benchmark output logs.

Exit criteria:
- Throughput target met with no parity regressions.

---

## Step 12 - Final Acceptance and Release Gate

Spec linkage:
- `FCspec.md` section 10

Required action items:
1. Run full end-to-end suite defined in `docs/e2e_acceptance.md`.
2. Verify all mandatory pass criteria and artifact generation.
3. Freeze release candidate commit.
4. Publish final implementation notes and known limitations (if any).

Required tests:
1. Entire `docs/e2e_acceptance.md` suite.

Step artifacts:
1. `history/release_candidate_step12.md`.
2. test logs and parity artifacts.

Exit criteria:
- All acceptance criteria satisfied and signed off.

---

## Step 13 - Physical Repository Prune (Hard Delete Pass)

Spec linkage:
- `FCspec.md` sections 6, 10

Required action items:
1. Freeze a recovery point before deletion (`git tag` + branch) so removed files can be recovered if needed.
2. Convert `history/deletion_candidates.md` into an approved deletion list.
3. Physically delete all non-approved files/directories/code/data not required for:
   - headless Fight Caves runtime
   - deterministic replay
   - parity harness and required tests
   - project build and CI metadata
4. Remove unused Gradle modules/dependencies and update `settings.gradle.kts` if modules are deleted.
5. Update docs to reflect post-prune project structure.
6. Re-run full E2E suite after deletion.

Required tests:
1. New test: `ProjectTreeMatchesApprovedManifestTest`.
2. New test: `ForbiddenPathsAbsentTest`.
3. Full `docs/e2e_acceptance.md` suite (must pass post-prune).

Step artifacts:
1. `history/repo_prune_report.md` (deleted paths + reason + owner approval).
2. Updated `config/headless_manifest.toml`.
3. Updated `docs/extraction_manifest.md`.

Exit criteria:
- Unnecessary files/directories are physically removed from the repository.
- Build, parity, and E2E validation still pass after pruning.

---
## Post-Spec Future Work (Do Not Start Before Step 12 Passes)

1. RL environment wrappers (Gymnasium-style).
2. Vectorized rollouts and distributed training.
3. Reward experiments and curriculum.
4. Policy replay into headed RSPS demonstrations.

These tasks are blocked until full 1:1 parity is confirmed.



