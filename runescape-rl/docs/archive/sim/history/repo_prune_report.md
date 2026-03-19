# Repository Prune Report (Step 13)

Generated on: 2026-03-06

## Approved Deletion List

Source candidate inventory:
- `docs/deletion_candidates.md`
- `config/headless_manifest.toml` (`[modules].removed_in_step13`)

Approved and executed hard deletes:
- `database/`
  - Reason: optional module, not required for headless Fight Caves runtime, deterministic replay, parity harness, or acceptance gates.
- `tools/`
  - Reason: explicitly excluded module from headless runtime closure; not needed for runtime, parity, or E2E validation.

## Build/Config Updates Required By Prune

- Updated `settings.gradle.kts`:
  - removed `include("database")`
  - removed `include("tools")`
- Updated `game/build.gradle.kts`:
  - removed conditional `includeDb` dependency wiring for `:database`
  - removed database-specific `shadowJar` branching
  - added `headlessDist` alias task (depends on `headlessDistZip`)
  - fixed `e2eTest` task to execute test classes (`testClassesDirs` + `classpath`)
  - hardened `e2eTest` worker settings (`maxHeapSize=2048m`, `maxParallelForks=1`, `forkEvery=1`)
- Updated `config/headless_manifest.toml`:
  - status -> `step13-physically-pruned`
  - module lists reflect removed modules
- Added `config/headless_prune_manifest.toml` for post-prune tree assertions.
  - version `2` includes `pruned_test_root` + `retained_test_files`

## Step 13 Validation Tests

- `ProjectTreeMatchesApprovedManifestTest` (PASS)
- `ForbiddenPathsAbsentTest` (PASS)
- `HeadlessDeletionCandidateInventoryTest` (PASS)

## Post-Prune Acceptance Re-Run (Step 12 Command Matrix)

Executed under Java 21 (`C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`):

1. `./gradlew clean --no-daemon` - PASS (~17s)
2. `./gradlew :game:test --no-daemon` - PASS (~5m45s)
3. `./gradlew :game:test --tests "*Headless*" --no-daemon` - PASS (~2m38s)
4. `./gradlew :game:test --tests "*Parity*" --no-daemon` - PASS (~2m44s)
5. `./gradlew :game:test --tests "content.area.karamja.tzhaar_city.TzhaarFightCaveTest" --no-daemon` - PASS (~40s)
6. `./gradlew :game:headlessDist --no-daemon` - PASS (~25s)
7. `./gradlew :game:e2eTest --no-daemon` - PASS (~2m26s)

## Recovery Points

Created before prune closeout validation:
- branch: `recovery/pre-step13-prune-2026-03-06`
- tag: `pre-step13-prune-2026-03-06`

## Outcome

- Step 13 prune closure criteria satisfied.
- Physical repository prune is complete and acceptance gates pass post-prune.
