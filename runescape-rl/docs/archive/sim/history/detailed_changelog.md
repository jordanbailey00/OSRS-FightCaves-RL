# Agent Changelog

Append-only log of implementation changes and decisions.

## 2026-03-09 21:15:00 -04:00 - Phase 2 Native-Linux Transport Promotion Gate

## 2026-03-10 02:35:00 -04:00 - Phase 2 Native-Linux Train Ceiling Workflow

### Decisions
1. Added a hosted native-Linux learner-ceiling workflow before any Phase 2 transport promotion decision is revisited.
2. Reused the existing hosted cache/bootstrap and results-publication path rather than inventing a separate manual benchmark process.
3. Treated the learner-ceiling benchmark as diagnostic only:
   - it should publish evidence
   - it should not enforce a hard gate on its own

### Changes Made
1. Added `.github/workflows/phase2_native_linux_train_ceiling.yml`.
2. Wired the workflow to:
   - build the canonical `:game:headlessDistZip`
   - run `RL/scripts/benchmark_train_ceiling.py`
   - summarize the `4 / 16 / 64 env` fake-env ceiling rows
   - publish results to `codex/phase2-results/phase2-train-ceiling-native-linux/latest`

### Outcome
1. The workspace now has a source-of-truth hosted path to measure whether the trainer loop itself is flattening throughput on native Linux.
2. No simulator semantics changed in this batch.

## 2026-03-09 21:15:00 -04:00 - Phase 2 Native-Linux Transport Promotion Gate

### Decisions
1. Kept `WC-P2-03` blocked until the low-copy transport proves itself on native Linux, not just on local WSL.
2. Treated the hosted native-Linux Phase 2 packet as the source-of-truth promotion gate for the subprocess transport swap.
3. Reused the existing cache/bootstrap and result-publication patterns from the earlier phase workflows instead of inventing a separate publication path.

### Changes Made
1. Added `.github/workflows/phase2_native_linux_packet.yml`.
2. Wired the workflow to:
   - build the canonical `:game:headlessDistZip`
   - run `RL/scripts/refresh_phase2_packet.py`
   - summarize the gate into JSON
   - publish artifacts to `codex/phase2-results`
   - fail if `wc_p2_03_unblocked` is false

### Verification
1. Local WSL Phase 2 packet completed successfully via the RL entrypoint.
2. Local WSL gate remained blocked, which matches the current source-of-truth policy that promotion decisions must come from native Linux.
3. Hosted native-Linux packet run `22882424149` completed packet generation, summary publication, and artifact upload successfully.

### Outcome
1. The native-Linux gate path for `WC-P2-03` is ready to run.
2. No simulator semantics changed in this batch.
3. After the first hosted run:
   - transport `64 env` cleared the promotion threshold at `1.3995x`
   - disabled train `64 env` did not clear the required `1.10x` threshold
   - shared-train `16 -> 64` scaling remained below `1.0x`
   - `WC-P2-03` remains blocked for real performance reasons, not workflow/plumbing reasons

## 2026-03-09 23:35:00 -04:00 - Phase 1 WC-P1-04 Equivalence Gate Design

### Decisions
1. Froze the raw-vs-flat certification gate as a sim-owned contract rather than leaving it implicit in later RL-side benchmark results.
2. Required Jad telegraph equivalence to remain a first-class part of the raw-vs-flat proof rather than treating it as incidental observation content.
3. Split responsibility cleanly:
   - sim-side projection and semantic equivalence in `fight-caves-RL`
   - consumer-side ingest equivalence and fail-fast checks in `RL`

### Changes Made
1. Added the new certification-gate source of truth:
   - `docs/raw_flat_equivalence_plan.md`

### Outcome
1. `WC-P1-04` is complete at the planning/design level.
2. Flat-path implementation can no longer claim success without explicit raw-vs-flat proof requirements.

## 2026-03-09 23:05:00 -04:00 - Phase 1 WC-P1-02 Flat Schema Design

### Decisions
1. Froze the first flat training schema as a conservative migration step that mirrors the already-shipped `134`-feature trainer tensor layout instead of redesigning the policy feature set at the same time.
2. Kept the flat schema sim-owned even though `v1` intentionally aligns to the existing RL-side trainer layout.
3. Carried `jad_telegraph_state` into the flat-schema equivalence set as protected semantic content rather than allowing it to become a trainer-only convenience field.

### Changes Made
1. Added the new design source of truth:
   - `docs/flat_training_observation_schema.md`
2. Updated `docs/raw_flat_observation_contract.md`:
   - linked the contract to the new flat-schema design and the RL-side ingestion design

### Outcome
1. `WC-P1-02` is complete at the design-contract level.
2. The next Phase 1 step can focus on raw-vs-flat equivalence gates against a specific flat schema instead of an abstract future layout.

## 2026-03-09 22:20:00 -04:00 - Phase 1 WC-P1-01 Contract Freeze

### Decisions
1. Froze the raw-vs-flat ownership boundary with the raw headless observation path remaining the semantic reference and any future flat training path remaining a sim-owned performance projection.
2. Classified `jad_telegraph_state` as protected raw semantic content for future flat-schema work rather than a disposable trainer-local field.
3. Kept this batch docs-first and contract-only; no emitter or transport implementation started.

### Changes Made
1. Added the new source-of-truth contract doc:
   - `docs/raw_flat_observation_contract.md`
2. Updated `docs/observation_schema.md`:
   - linked the raw schema to the future flat-path contract
   - explicitly protected visible-NPC ordering and `jad_telegraph_state` semantics in later flat-schema work

### Outcome
1. `WC-P1-01` is now complete at the sim-owned contract level.
2. The next Phase 1 work can proceed to flat-emitter design without ambiguity about ownership, versioning, or the Jad cue's protected meaning.

## 2026-03-09 21:30:00 -04:00 - Jad Telegraph JAD-05 and JAD-06

### Decisions
1. Preserved the discovered engine truth that Jad protection is sampled when the queued `hit(...)` is constructed after the `3`-tick windup, not at the later delayed visual landing tick.
2. Extended the authoritative Jad trace/state to retain last-attack timing and committed outcome fields after the active telegraph clears back to idle so replay/parity inspection can see the finished attack.
3. Scoped the final parity acceptance to Jad-specific trace/outcome assertions when unrelated oracle-side world activity would otherwise make whole-snapshot parity too noisy for this mini-rework.

### Changes Made
1. Updated `game/src/main/kotlin/content/area/karamja/tzhaar_city/JadTelegraph.kt`:
   - added `prayer_check_tick`
   - added `sampled_protection_prayer`
   - added `protected_at_prayer_check`
   - added `resolved_damage`
   - retained last telegraph start/resolve ticks for post-resolution trace inspection
2. Updated `game/src/main/kotlin/content/area/karamja/tzhaar_city/TzTokJad.kt`:
   - recorded Jad outcome metadata at the existing hit-construction point without changing combat timing
3. Added focused Jad outcome support/tests:
   - `game/src/test/kotlin/content/area/karamja/tzhaar_city/JadTelegraphTestSupport.kt`
   - `game/src/test/kotlin/content/area/karamja/tzhaar_city/JadTelegraphPrayerProtectionTest.kt`
   - `game/src/test/kotlin/headless/determinism/HeadlessReplayJadTelegraphTraceTest.kt`
   - `game/src/test/kotlin/headless/parity/ParityHarnessJadPrayerResolutionParityTest.kt`

### Outcome
1. The replay/parity surfaces can now inspect Jad telegraph onset, prayer-check timing, protection outcome, and resolved damage from the same authoritative combat event/state.
2. Regression coverage now proves that late prayer toggles after queued-hit construction are too late, while late toggles after sampling do not retroactively change the committed Jad outcome.

## 2026-03-09 16:10:00 -04:00 - Jad Telegraph JAD-01 and JAD-02

### Decisions
1. Treated the current headed Jad animation onset tick as the canonical telegraph onset.
2. Treated dialog/audio as presentation side effects, not the parity anchor.
3. Preserved the existing hit-resolution path and prayer-check timing exactly:
   - Jad still uses the custom `strongQueue("hit_target", 3)` windup
   - the scheduled hit still uses `delay = 64`
   - total onset-to-resolution window remains the current `6` game ticks
4. Kept the change narrowly scoped to Jad:
   - no wider combat-engine redesign
   - no headless-only oracle field
   - no change to observation payload in this step

### Changes Made
1. Added `game/src/main/kotlin/content/area/karamja/tzhaar_city/JadTelegraph.kt`:
   - `JadTelegraphState`
   - `JadCommittedAttackStyle`
   - explicit timing constants
   - per-NPC telegraph lifecycle state and scheduling helpers
2. Updated `game/src/main/kotlin/content/entity/npc/combat/Attack.kt`:
   - starts the Jad telegraph on the same tick the headed attack animation begins
3. Updated `game/src/main/kotlin/content/area/karamja/tzhaar_city/TzTokJad.kt`:
   - replaced the custom Jad timing literals with shared named constants
4. Added `game/src/test/kotlin/content/area/karamja/tzhaar_city/JadTelegraphStateTest.kt`:
   - verifies style mapping
   - verifies onset tick capture
   - verifies resolve tick capture
   - verifies telegraph lifecycle clearing after the preserved reaction window

### Inspection Findings Captured
1. Current headed animation onset occurs in the generic NPC swing path before the Jad-specific hit queue is scheduled.
2. Current Jad attack definitions use animation/gfx/sound cues; there is no Jad-specific `say(...)` cue in the attack definition path.
3. Prayer semantics remain downstream in the existing hit/damage path and were left unchanged.

## 2026-03-09 17:25:00 -04:00 - Jad Telegraph JAD-03 and JAD-04

### Decisions
1. Kept the raw simulator observation on additive `headless_observation_v1`; the Jad cue is an additive NPC field, not a new raw schema generation.
2. Exposed only the telegraph state to headless consumers:
   - no direct correct-prayer field
   - no countdown timer
   - no pre-telegraph future-style leakage
3. Reused the same authoritative telegraph state for replay/parity trace payloads instead of building a parallel trace-only inference path.

### Changes Made
1. Updated `game/src/main/kotlin/HeadlessObservationBuilder.kt`:
   - added `HeadlessObservationNpc.jadTelegraphState`
   - serialized field name `jad_telegraph_state`
2. Added repo-owned telegraph trace projection in `game/src/main/kotlin/content/area/karamja/tzhaar_city/JadTelegraph.kt`:
   - `JadTelegraphTrace`
   - stable serialized state/style names
3. Updated replay/parity snapshot paths:
   - `game/src/main/kotlin/HeadlessReplayRunner.kt`
   - `game/src/main/kotlin/ParityHarness.kt`
4. Added focused regression coverage:
   - `game/src/test/kotlin/headless/observation/ObservationJadTelegraphStateTest.kt`
   - `game/src/test/kotlin/headless/determinism/HeadlessReplayJadTelegraphTraceTest.kt`
   - `game/src/test/kotlin/headless/parity/ParityHarnessJadTelegraphParityTest.kt`

### Outcome
1. The headless observation now carries a learnable Jad cue with the same onset tick and meaning as the headed animation-driven cue.
2. Replay and oracle-vs-headless parity snapshots now expose the same authoritative Jad telegraph timing for inspection.

## 2026-03-09 03:45:00 -04:00 - Active Phase 0 Doc Cleanup

### Decisions
1. Pruned stale Phase 0 blocker wording from active sim-side profiling docs after the native-Linux gate passed.
2. Kept the changelog history intact; only the live profiler-position text was tightened.

### Changes Made
1. Updated `docs/sim_profiler_report.md`:
   - removed the old statement that the native-Linux rerun was still the remaining blocker before Phase 1
   - replaced it with the current state: profiler infrastructure is no longer a Phase 0 blocker

## 2026-03-09 03:30:00 -04:00 - Phase 0 Native-Linux Gate Passed

### Decisions
1. Accepted the hosted native-Linux packet as the approved Phase 0 source-of-truth fallback while the self-hosted Linux runner remains unavailable.
2. Treated the hosted packet as sufficient to unblock Phase 1 because it satisfied the approved hard-gate conditions:
   - `benchmark_host_class = linux_native`
   - `native_linux_source_of_truth = true`
   - `phase1_unblocked = true`
3. Preserved the self-hosted workflow as the preferred future path for repeat runs against the real workspace topology.

### Verification
1. Hosted workflow run `22842056526` completed successfully after the cache/bootstrap and artifact-prerequisite hardening.
2. Published results branch `codex/phase0-results` now contains:
   - `phase0-native-linux/latest/run_summary.json`
   - `phase0-native-linux/latest/gate_summary.json`
   - `phase0-native-linux/latest/phase0_packet.json`
3. Gate summary reported:
   - `sim_single_slot_ticks_per_second = 30532.58`
   - `sim_batched_env_steps_per_second = 404635.41`
   - `workers_needed_for_100k = 1`

### Outcome
- Phase 0 native-Linux measurement is no longer blocked.
- The remaining optimization work can now move to approved Phase 1 design work in `RL`.

## 2026-03-09 03:00:00 -04:00 - Phase 0 Native-Linux Artifact Gate Hardening

### Decisions
1. Treated the packaged headless distribution as an explicit prerequisite for the hosted and self-hosted Phase 0 packet paths.
2. Preserved `:game:headlessDistZip` as the canonical artifact task for the benchmark/bridge path.
3. Kept stdout/stderr publication on the hosted path so the next native-Linux rerun produces actionable failure evidence if the packet still fails.

### Changes Made
1. Updated `.github/workflows/phase0_native_linux_packet.yml`:
   - runs `./gradlew --no-daemon :game:headlessDistZip` before the RL packet refresh
   - uploads `phase0_packet_stdout.log`, `phase0_packet_stderr.log`, and `phase0_packet_meta.json` with the normal Phase 0 artifacts

### Why This Was Needed
1. The RL Phase 0 packet benchmarks consume the packaged headless distribution via the bridge/runtime discovery path.
2. The standalone sim report/profile tasks do not build that distribution as a side effect.
3. On a fresh hosted runner, this left packet generation vulnerable to failing before any bridge or train rows could run.

## 2026-03-09 02:30:20 -04:00 - Phase 0 Native-Linux Packet Host Attempt

### Decisions
1. Added a hosted native-Linux Phase 0 packet workflow in `fight-caves-RL` as a fallback when the `RL` self-hosted Linux runner path is unavailable.
2. Kept this work strictly inside Phase 0 measurement infrastructure; no simulator/runtime semantics changed.
3. Treated the failed hosted run as a useful gate result rather than masking it: native-Linux packet generation is still blocked until cache provisioning is reproducible on that host path.

### Changes Made
1. Added `.github/workflows/phase0_native_linux_packet.yml`:
   - checks out `fight-caves-RL`, `RL`, and `RSPS`
   - provisions Java/Python/uv on `ubuntu-latest`
   - attempts to restore or download the required game cache before running the standalone sim report/profile and the RL Phase 0 packet refresh

### Verification
1. Hosted native-Linux workflow run created successfully:
   - run `22841206737`
2. The run failed before packet generation:
   - `Verify cache files exist`: failed
   - `Download game cache files`: skipped
3. Resulting implication:
   - the hosted native-Linux path currently lacks a reproducible cache source in this repo context
   - no new native-Linux Phase 0 packet exists yet

### Current Status
- Phase 0 native-Linux measurement remains blocked by infrastructure:
  - `RL` self-hosted Linux runner path is queued/unavailable
  - hosted `ubuntu-latest` path cannot yet provision the required game cache
- Phase 1 remains blocked

## 2026-03-09 02:55:00 -04:00 - Phase 0 Hosted Linux Bootstrap Hardening

### Decisions
1. Kept the hosted native-Linux Phase 0 path active as the fallback when the self-hosted Linux runner is unavailable.
2. Used repo-owned Git branches, not hidden credentials, to unblock hosted cache bootstrap and result visibility:
   - `codex/cache-bootstrap` hosts the cache archive parts and manifest
   - `codex/phase0-results` hosts published run summaries and packets
3. Kept all of this work in Phase 0 measurement infrastructure only; no simulator semantics changed.

### Changes Made
1. Added the temporary cache bootstrap artifact branch:
   - `codex/cache-bootstrap`
   - contains `.github/cache-bootstrap/manifest.json`
   - contains split archive parts for the validated local `data/cache/`
2. Added the Phase 0 results publication branch:
   - `codex/phase0-results`
   - initialized with `phase0-native-linux/README.md`
3. Hardened `.github/workflows/phase0_native_linux_packet.yml`:
   - shallow checkout for the main repo to avoid full-history cost from the artifact branches
   - bootstrap from `codex/cache-bootstrap` when restored cache is missing or empty
   - publish `run_summary.json`, `gate_summary.json`, `phase0_packet.json`, or `error_summary.json` to `codex/phase0-results`

### Verification
1. Confirmed the hosted workflow now clears the cache gate on the latest rerun before entering the standalone sim report:
   - `Bootstrap cache from artifact branch`: success
   - `Verify cache files exist`: success
   - `Sync RL dependencies`: success

### Current Status
- The hosted native-Linux path is no longer blocked on cache provisioning or result visibility.
- The remaining live gate is completion of the standalone sim report / full packet run on the hosted native-Linux runner.

## 2026-03-04 21:47:33 -05:00 - Step 0 Baseline Start

### Decisions
1. Proceeded with Step 0 (stabilization + baseline execution) before any extraction/refactor work.
2. Kept project Java target at 21 (as defined by repo), and ran Gradle via portable local JDK 21 because system JDK was 17.
3. Added missing runtime cache by downloading OpenRS2 revision 283 disk archive to unblock tests.

### Changes Made
1. Code fix:
   - `game/src/main/kotlin/content/entity/world/RegionLoading.kt`
   - Replaced `addFirst` call with `add(0, ...)` to resolve compile error while preserving ordering intent.
2. Environment/bootstrap artifacts:
   - Added local tooling directory `.tools/` with portable JDK 21 and downloaded cache archive extraction helper artifacts.
   - Populated `data/cache/` from OpenRS2 disk zip.
3. Documentation:
   - Added `docs/baseline.md` with command-level baseline results and blockers.

### Test Outcomes
1. `TzhaarFightCaveTest`: failed (missing `cave_entrance_fight_cave` object at expected tile).
2. `content.entity.combat.*`: failed (`CombatFlinchTest` behavior assertion mismatch).
3. `content.skill.prayer.*`: passed.
4. `content.skill.constitution.*`: passed.

### Current Status
- Step 0 remains open due 2 failing baseline targets.
- Primary blocker appears to be cache parity mismatch for Fight Caves object placement plus existing combat flinch regression.

## 2026-03-04 22:01:53 -05:00 - Step 0 Baseline Completed

### Decisions
1. Kept project JVM target at Java 21 (repo-default) and used a local portable JDK 21 instead of retargeting build files.
2. Used official Void cache from Mega (2025-06-12-void-634-cache.7z) after confirming OpenRS2 cache mismatch caused invalid baseline behavior.

### Changes Made
1. Compile fix in RegionLoading.kt:
   - addFirst replaced with add(0, ...).
2. Added /.tools/ to .gitignore to avoid tracking local JDK/cache tooling artifacts.
3. Updated docs/baseline.md to final Step 0 PASS state.

### Verification
1. TzhaarFightCaveTest: passed.
2. content.entity.combat.*: passed.
3. content.skill.prayer.*: passed.
4. content.skill.constitution.*: passed.

### Outcome
- Step 0 exit criteria satisfied.


## 2026-03-04 22:16:00 -05:00 - Iteration 01 Repo/Naming Housekeeping

### Decisions
1. Separated active development repository from the upstream Void fork to avoid fork-coupled workflow.
2. Preserved an unchanged fork copy for future replay/demo reference.
3. Committed only non-build/code artifacts in this iteration; gameplay/runtime code changes remain out-of-scope for this commit.

### Changes Made
1. GitHub repository changes:
   - Renamed fork "fight-caves-rl" -> "void-rsps-v2-5-0".
   - Updated fork description to "Void RSPS v2.5.0 (unchanged fork for replays/demos)".
   - Created new standalone public repository "fight-caves-rsps" (non-fork).
2. Local git remote changes:
   - origin now points to jordanbailey00/fight-caves-rsps.
   - Added/retained void-fork remote pointing to jordanbailey00/void-rsps-v2-5-0.
   - Kept upstream pointing to GregHib/void.
3. Planning/process updates:
   - Added iteration checklist + step snapshot tracking in plan.md.

### Status
- Repository split completed.
- Local directory rename pending and scheduled immediately after commit/push operations to avoid interrupting active path state.

## 2026-03-04 22:41:00 -05:00 - Step 1 Manifest Artifacts Created

### Decisions
1. Built Step 1 manifest as both human-readable (`docs/extraction_manifest.md`) and machine-readable (`config/headless_manifest.toml`) artifacts to reduce model/agent drift in later extraction work.
2. Kept Step 1 status as partial because required Step 1 tests are not implemented yet.
3. Used a conservative keep-set for first manifest pass to avoid accidental parity regressions before headless bootstrap wiring is introduced.

### Changes Made
1. Added `docs/extraction_manifest.md`:
   - documents explicit keep/cut boundaries for code, scripts, data, runtime stages, cache regions, and exclusion policy.
   - records required NPC/item/object/variable identifiers for Fight Caves closure.
2. Added `config/headless_manifest.toml`:
   - codifies modules, script allowlist, data allowlist, exclusion globs, settings keys, region scope, and validation requirements.
3. Updated `plan.md` iteration tracking:
   - added Iteration 02 checklist with completed/remaining Step 1 work.

### Verification
1. Parsed `config/headless_manifest.toml` successfully via Python `tomllib`.
2. No build/runtime tests executed in this iteration (artifact authoring only).

### Status
- Step 1 manifest artifacts completed.
- Step 1 test implementation and pass gate remain open.

## 2026-03-05 00:53:00 -05:00 - Step 1 Tests Implemented and Passing

### Decisions
1. Implemented Step 1 checks as static manifest-validation tests to enforce Fight Caves closure and exclusion rules before headless bootstrap work.
2. Reused `ConfigReader` for TOML parsing to keep tests aligned with project-native config parsing behavior.
3. Used a portable JDK 21 runtime for local test execution because the host default JVM was Java 17 and cannot execute Java 21 bytecode.

### Changes Made
1. Added `game/src/test/kotlin/headless/manifest/HeadlessManifestTestSupport.kt`:
   - repository root discovery
   - headless manifest TOML loading/parsing
   - helper checks for file/class/id resolution
2. Added `game/src/test/kotlin/headless/manifest/HeadlessManifestContainsFightCaveClosureTest.kt`:
   - validates scope metadata (minigame, wave/rotation counts)
   - validates required code/data path resolution
   - validates required script class source resolution
   - validates required NPC/item/object IDs resolve in data TOML files
3. Added `game/src/test/kotlin/headless/manifest/HeadlessManifestRejectsExcludedSystemsTest.kt`:
   - validates required script classes exclude blocked namespaces and oracle-only overlap
   - validates required code/data paths are not excluded unless explicitly excepted
   - validates core excluded namespaces are declared in manifest
4. Updated `plan.md`:
   - Step 1 tests marked complete
   - Step 1 status marked complete in step snapshot
   - Iteration 03 tracking entry added

### Verification
1. Initial local run failed under Java 17 with `UnsupportedClassVersionError` (class file version 65 vs runtime max 61).
2. Re-ran with Java 21 and passed:
   - `./gradlew :game:test --tests "headless.manifest.HeadlessManifestContainsFightCaveClosureTest" --tests "headless.manifest.HeadlessManifestRejectsExcludedSystemsTest"`

### Outcome
- Step 1 exit criteria test gate is now satisfied.

## 2026-03-05 01:18:00 -05:00 - Stop Checkpoint and Docs Sync Before Step 2

### Decisions
1. Treated this turn as a stopping checkpoint and deferred Step 2 implementation until next session.
2. Retried the pending local folder rename first, then documented the blocker and current project state.
3. Kept all tracking docs aligned so next iteration can start directly at Step 2.

### Actions Taken
1. Attempted local rename from parent directory:
   - `Rename-Item -Path "elvarg_RSPS" -NewName "fight caves RL"`
2. Stopped local Gradle daemons and retried rename.
3. Updated documentation state:
   - `plan.md` iteration tracking/status refreshed (including rename blocker and Step 2 as next work item).
   - `spec.md` appended with execution status checkpoint section.

### Result
1. Local folder rename remains blocked by Windows file lock (`Rename-Item` IOException: directory in use by another process).
2. Step 0 and Step 1 remain complete; Step 2 is queued for tomorrow.

### Remaining Blocker
- Close/release active IDE/terminal/process handles on `elvarg_RSPS`, then retry rename to `fight caves RL`.

## 2026-03-05 01:22:00 -05:00 - Folder Rename Retry (Still Blocked)

### Action
- Retried: `Rename-Item -Path "elvarg_RSPS" -NewName "fight caves RL"` from parent directory.

### Outcome
- Same Windows file lock IOException (directory still in use by another process).

## 2026-03-05 01:28:00 -05:00 - Owner Decision: Directory Rename Deferred

### Decision
- Owner explicitly deferred local directory rename because the folder is open in VSCode and will be renamed manually later.

### Documentation Updates
- `plan.md` updated to mark rename as deferred/non-blocking (no longer treated as an active blocker).
- `spec.md` execution checkpoint updated to reflect deferred manual rename status.

### Effect on Execution
- Step 2 remains the next implementation step for the next session.

## 2026-03-05 14:45:00 -05:00 - Step 2 Completed (Headless Bootstrap Path)

### Decisions
1. Introduced a dedicated headless entrypoint while preserving existing headed `Main` behavior.
2. Centralized shared bootstrap/preload/cache-module wiring to reduce drift between headed and headless startup paths.
3. Kept headless tick stages minimal and explicit, with canonical stage-name helpers for deterministic order assertions.

### Changes Made
1. Added `game/src/main/kotlin/RuntimeBootstrap.kt`:
   - runtime mode flag constants/types (`runtime.mode`, `headed`, `headless`)
   - shared settings loading (`loadRuntimeSettings`)
   - shared preload/Koin wiring (`preloadRuntime`)
   - shared cache/definition module wiring (`cacheModule`)
2. Updated `game/src/main/kotlin/Main.kt`:
   - now uses shared bootstrap utilities
   - logs startup mode (`mode=headed`)
3. Added `game/src/main/kotlin/HeadlessMain.kt`:
   - dedicated headless bootstrap (`bootstrap`) and entrypoint (`main`)
   - no GameServer/LoginServer startup path
   - explicit headless mode startup logging
4. Added `game/src/main/kotlin/HeadlessModules.kt`:
   - headless-specific Koin runtime config binding
5. Updated `game/src/main/kotlin/GameTick.kt`:
   - added `getHeadlessTickStages()`
   - added `headlessTickStageOrder`
   - added `describeTickStage(s)` helpers for testable canonical stage ordering
6. Added Step 2 tests:
   - `game/src/test/kotlin/headless/bootstrap/HeadlessBootstrapTestSupport.kt`
   - `game/src/test/kotlin/headless/bootstrap/HeadlessBootWithoutNetworkTest.kt`
   - `game/src/test/kotlin/headless/bootstrap/HeadlessTickPipelineOrderTest.kt`

### Verification
1. Passed Step 2 tests:
   - `./gradlew :game:test --tests "HeadlessBootWithoutNetworkTest" --tests "HeadlessTickPipelineOrderTest"`
2. Passed headed regression smoke:
   - `./gradlew :game:test --tests "content.area.karamja.tzhaar_city.TzhaarFightCaveTest"`

### Outcome
- Step 2 exit criteria satisfied.
- Next execution target is Step 3.

## 2026-03-05 20:10:00 -05:00 - Iteration 06 Step 3 Completed (Fight Caves-Only Data Loading)

### Decisions
1. Kept headless data loading on an explicit allowlist path to prevent accidental `dirs.txt` closure drift.
2. Restricted map/collision decode by headless region allowlist and validated region subset behavior via integration test.
3. Avoided unrelated headless eager-loading failures by skipping `DiangoCodeDefinitions` initialization in headless mode.
4. Hardened runtime bootstrap to create parent directories for modified/wildcard state files before update writes.
5. Isolated headless test cache/modified paths from headed test metadata to prevent cross-test cache contamination.

### Changes Made
1. Added `game/src/main/kotlin/HeadlessDataLoading.kt`:
   - `HeadlessDataAllowlist` model
   - allowlist TOML loader and resolver
   - required setting/file validation
   - headless `ConfigFiles` assembly from allowlisted files
2. Updated `game/src/main/kotlin/HeadlessMain.kt`:
   - bootstrap now uses allowlist loader path
   - runtime now exposes loaded allowlist metadata
3. Updated `engine/src/main/kotlin/world/gregs/voidps/engine/data/definition/MapDefinitions.kt`:
   - headless-aware region restriction via `headless.map.regions`
4. Updated `engine/src/main/kotlin/world/gregs/voidps/engine/EngineModules.kt`:
   - skip `DiangoCodeDefinitions` eager load in headless mode
5. Updated `game/src/main/kotlin/RuntimeBootstrap.kt`:
   - create parent dirs for `storage.data.modified` and `storage.wildcards` before `configFiles.update()`
   - preserve headed behavior
6. Added/updated Step 3 artifacts:
   - `config/headless_data_allowlist.toml`
   - `docs/headless_data_loading.md`
7. Added Step 3 tests:
   - `game/src/test/kotlin/headless/data/HeadlessLoadsFightCaveDataOnlyTest.kt`
   - `game/src/test/kotlin/headless/data/HeadlessFailsOnMissingFightCaveWaveDataTest.kt`
   - `game/src/test/kotlin/headless/data/HeadlessCollisionRegionSubsetTest.kt`
8. Updated headless test support:
   - `game/src/test/kotlin/headless/bootstrap/HeadlessBootstrapTestSupport.kt`
   - isolated modified/wildcards/cache test paths under `../temp/data/headless-test-cache/`
   - disabled map cache writing in headless tests (`storage.caching.active=false`)

### Verification
1. Passed Step 3 gate tests:
   - `./gradlew :game:test --tests "HeadlessLoadsFightCaveDataOnlyTest" --tests "HeadlessFailsOnMissingFightCaveWaveDataTest" --tests "HeadlessCollisionRegionSubsetTest" --no-daemon`
2. Passed headed regression smoke after Step 3 changes:
   - `./gradlew :game:test --tests "content.area.karamja.tzhaar_city.TzhaarFightCaveTest" --no-daemon`

### Outcome
- Step 3 exit criteria satisfied.
- Step 4 (script allowlist + script registry validation) is next.

## 2026-03-05 22:35:00 -05:00 - Iteration 07 Step 4 Completed (Script Allowlist and Content Loading)

### Decisions
1. Enforced headless script loading through an explicit allowlist file (`config/headless_scripts.txt`) while leaving headed/oracle full-script loading unchanged.
2. Added startup fail-fast validation for script drift and required Fight Caves hook registration to prevent silent closure regressions.
3. Kept the Step 4 allowlist focused on Fight Caves closure scripts that can be loaded under headless data scope without wildcard-resolution failures.

### Changes Made
1. Added script loading/validation path:
   - `game/src/main/kotlin/HeadlessScriptLoading.kt`
   - `config/headless_scripts.txt`
   - `docs/headless_scripts.md`
2. Updated runtime/script loader wiring:
   - `game/src/main/kotlin/ContentLoader.kt`
   - `game/src/main/kotlin/RuntimeBootstrap.kt`
   - `game/src/main/kotlin/HeadlessMain.kt`
3. Added registry introspection helpers used by startup validation:
   - `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/mode/combat/CombatApi.kt`
   - `engine/src/main/kotlin/world/gregs/voidps/engine/timer/TimerApi.kt`
   - `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/skill/Skills.kt`
   - `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/mode/move/Moved.kt`
   - `engine/src/main/kotlin/world/gregs/voidps/engine/entity/Spawn.kt`
   - `engine/src/main/kotlin/world/gregs/voidps/engine/entity/Despawn.kt`
   - `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/Death.kt`
4. Added Step 4 tests:
   - `game/src/test/kotlin/headless/scripts/HeadlessScriptRegistryContainsFightCaveHandlersTest.kt`
   - `game/src/test/kotlin/headless/scripts/HeadlessScriptRegistryExcludesUnrelatedSystemsTest.kt`
   - `game/src/test/kotlin/headless/scripts/HeadlessSingleWaveScriptSmokeTest.kt`
   - support: `game/src/test/kotlin/headless/scripts/HeadlessScriptTestSupport.kt`
5. Updated manifest/docs tracking for script closure parity:
   - `config/headless_manifest.toml`
   - `docs/extraction_manifest.md`
   - `plan.md`
   - `spec.md`

### Verification
1. Passed Step 4 tests:
   - `./gradlew :game:test --tests "HeadlessScriptRegistryContainsFightCaveHandlersTest" --tests "HeadlessScriptRegistryExcludesUnrelatedSystemsTest" --tests "HeadlessSingleWaveScriptSmokeTest" --no-daemon`
2. Passed headed regression smoke:
   - `./gradlew :game:test --tests "content.area.karamja.tzhaar_city.TzhaarFightCaveTest" --no-daemon`
3. Passed manifest regression checks:
   - `./gradlew :game:test --tests "headless.manifest.HeadlessManifestContainsFightCaveClosureTest" --tests "headless.manifest.HeadlessManifestRejectsExcludedSystemsTest" --no-daemon`

### Outcome
- Step 4 exit criteria satisfied.
- Next target is Step 5 (episode initialization contract implementation).

## 2026-03-05 17:04:47 -05:00 - Iteration 08 Step 5 Completed (Episode Initialization Contract)

### Decisions
1. Implemented episode reset as a first-class headless runtime API so future action/observation work can assume a stable deterministic start state.
2. Preserved Void-native semantics for wave boot, dynamic instance creation, prayer/run state, inventory/equipment containers, and RNG path.
3. Expanded headless data closure to include missing inventory definition files required for equipment/inventory containers in headless mode.
4. Standardized local verification on Java 21 because project/test bytecode target is classfile 65.

### Changes Made
1. Added `game/src/main/kotlin/FightCaveEpisodeInitializer.kt`:
   - `FightCaveEpisodeConfig`
   - `FightCaveEpisodeState`
   - deterministic `reset(player, config)` implementation
2. Updated `game/src/main/kotlin/HeadlessMain.kt`:
   - runtime now captures `TzhaarFightCave` script instance at bootstrap
   - `HeadlessRuntime` now exposes `resetFightCaveEpisode(...)` helpers
3. Added Step 5 tests:
   - `game/src/test/kotlin/headless/episode/EpisodeInitSetsFixedStatsTest.kt`
   - `game/src/test/kotlin/headless/episode/EpisodeInitSetsLoadoutAndConsumablesTest.kt`
   - `game/src/test/kotlin/headless/episode/EpisodeInitResetsWaveStateTest.kt`
   - `game/src/test/kotlin/headless/episode/EpisodeInitUsesProvidedSeedTest.kt`
4. Added Step 5 artifact:
   - `docs/episode_init_contract.md`
5. Updated data/manifests for loadout container closure:
   - `config/headless_data_allowlist.toml`
   - `config/headless_manifest.toml`
   - `docs/extraction_manifest.md`
   - added:
     - `data/entity/player/inventory/inventory.invs.toml`
     - `data/entity/player/equipment/worn_equipment.invs.toml`
     - `definitions.inventories` required setting key
6. Updated tracking docs:
   - `plan.md` (Iteration 08 + Step 5 complete)
   - `spec.md` execution checkpoint (Step 5 complete)
7. Installed Java 21 runtime locally for verification:
   - `winget install -e --id EclipseAdoptium.Temurin.21.JDK --scope machine`

### Verification
1. Passed Step 5 test gate:
   - `./gradlew :game:test --tests "EpisodeInit*" --rerun-tasks --no-daemon`
2. Passed regression gates after allowlist updates:
   - `./gradlew :game:test --tests "headless.manifest.HeadlessManifestContainsFightCaveClosureTest" --tests "headless.manifest.HeadlessManifestRejectsExcludedSystemsTest" --tests "HeadlessLoadsFightCaveDataOnlyTest" --tests "HeadlessFailsOnMissingFightCaveWaveDataTest" --tests "HeadlessCollisionRegionSubsetTest" --tests "content.area.karamja.tzhaar_city.TzhaarFightCaveTest" --no-daemon`

### Outcome
- Step 5 exit criteria satisfied.
- Next target is Step 6 (action adapter).

## 2026-03-05 17:32:15 -05:00 - Iteration 09 Step 6 Completed (Action Adapter)

### Decisions
1. Implemented Step 6 action ingestion as a dedicated adapter (`HeadlessActionAdapter`) to keep action semantics explicit and testable.
2. Enforced one-intent-per-tick at adapter boundary (`headless_last_action_tick`) to satisfy the API contract and prevent multi-action drift.
3. Used deterministic visible-NPC ordering (tile/id/index comparator) plus visible-index mapping so policy targets remain stable across runs.

### Changes Made
1. Added `game/src/main/kotlin/HeadlessActionAdapter.kt`:
   - stable action enum/id mapping (`HeadlessActionType`)
   - action payloads (`HeadlessAction`)
   - result contract (`HeadlessActionResult`) with mandatory `action_applied` metadata
   - standardized rejection reasons (`HeadlessActionRejectReason`)
   - canonical handlers for walk/attack/prayer/eat/drink/run/wait actions
2. Updated `game/src/main/kotlin/HeadlessMain.kt` / `HeadlessRuntime`:
   - bootstrap wiring for `HeadlessActionAdapter`
   - exposed headless action APIs: `visibleFightCaveNpcTargets`, `applyFightCaveAction`, `applyFightCaveActionAndTick`
3. Added Step 6 tests:
   - `game/src/test/kotlin/headless/action/ActionWalkToUsesPathfinderTest.kt`
   - `game/src/test/kotlin/headless/action/ActionAttackNpcDeterministicIndexMappingTest.kt`
   - `game/src/test/kotlin/headless/action/ActionPrayerToggleParityTest.kt`
   - `game/src/test/kotlin/headless/action/ActionEatDrinkLockoutRejectionTest.kt`
   - `game/src/test/kotlin/headless/action/ActionInvalidTargetRejectionTest.kt`
4. Fixed Step 6 compile blockers during implementation:
   - added missing `running` extension import
   - replaced unsupported viewport NPC conversion path with explicit primitive iterator mapping

### Verification
1. Compile gate passed:
   - `./gradlew :game:compileKotlin --no-daemon --rerun-tasks`
2. Passed Step 6 tests:
   - `./gradlew :game:test --tests "ActionWalkToUsesPathfinderTest" --tests "ActionAttackNpcDeterministicIndexMappingTest" --tests "ActionPrayerToggleParityTest" --tests "ActionEatDrinkLockoutRejectionTest" --tests "ActionInvalidTargetRejectionTest" --no-daemon --rerun-tasks`

### Outcome
- Step 6 exit criteria satisfied.
- Next target is Step 7 (observation builder contract).

## 2026-03-05 17:46:58 -05:00 - Iteration 10 Step 7 Completed (Observation Builder Contract)

### Decisions
1. Implemented Step 7 as a dedicated observation builder (`HeadlessObservationBuilder`) so schema/versioning and ordering are centralized.
2. Reused Step 6 visible-NPC mapping to guarantee deterministic NPC ordering parity between action targets and observation indices.
3. Kept future-leakage fields disabled by default and exposed only through explicit debug opt-in.

### Changes Made
1. Added `game/src/main/kotlin/HeadlessObservationBuilder.kt`:
   - `HeadlessObservationV1` schema model
   - strict versioning constants and top-level field order contract
   - ordered serialization helper (`toOrderedMap()`)
   - deterministic player/wave/NPC/lockout extraction
2. Updated `game/src/main/kotlin/HeadlessMain.kt` / `HeadlessRuntime`:
   - added observation wiring and runtime API `observeFightCave(...)`
3. Added Step 7 tests:
   - `game/src/test/kotlin/headless/observation/ObservationSchemaCompletenessTest.kt`
   - `game/src/test/kotlin/headless/observation/ObservationNpcOrderingDeterminismTest.kt`
   - `game/src/test/kotlin/headless/observation/ObservationNoFutureLeakageDefaultTest.kt`
   - `game/src/test/kotlin/headless/observation/ObservationVersioningContractTest.kt`
4. Added Step 7 artifact:
   - `docs/observation_schema.md`

### Verification
1. Passed Step 7 tests:
   - `./gradlew :game:test --tests "ObservationSchemaCompletenessTest" --tests "ObservationNpcOrderingDeterminismTest" --tests "ObservationNoFutureLeakageDefaultTest" --tests "ObservationVersioningContractTest" --no-daemon --rerun-tasks`

### Outcome
- Step 7 exit criteria satisfied.
- Next target is Step 8 (determinism hardening and RNG governance).

## 2026-03-05 18:02:37 -05:00 - Iteration 11 Step 8 Completed (Determinism and RNG Governance)

### Decisions
1. Kept Fight Caves RNG on the shared `world.gregs.voidps.type.random` path and moved episode seeding to tracked seeded RNG utilities.
2. Implemented replay determinism around `seed + action_trace` using a dedicated headless replay runner.
3. Added static governance checks to prevent direct `kotlin.random.Random` imports in Fight Caves closure code.

### Changes Made
1. Updated `types/src/main/kotlin/world/gregs/voidps/type/Random.kt`:
   - added `setSeededRandom(seed, trackCalls = true)`
   - added `RandomDiagnostics`, `randomDiagnostics()`, and `randomCallCount()`
2. Updated `game/src/main/kotlin/FightCaveEpisodeInitializer.kt`:
   - now seeds RNG via `setSeededRandom(config.seed, trackCalls = true)`
   - removed direct `kotlin.random.Random` usage from closure reset path
3. Added `game/src/main/kotlin/HeadlessReplayRunner.kt`:
   - `HeadlessReplayStep`
   - `HeadlessReplayTickSnapshot`
   - `HeadlessReplayResult`
   - deterministic replay execution over action traces
4. Added Step 8 tests:
   - `game/src/test/kotlin/headless/determinism/DeterministicReplaySameSeedSameTraceTest.kt`
   - `game/src/test/kotlin/headless/determinism/DeterministicReplayDifferentSeedDivergesTest.kt`
   - `game/src/test/kotlin/headless/determinism/FightCaveClosureNoDirectRandomUsageTest.kt`
   - `game/src/test/kotlin/headless/determinism/RngCounterMonotonicityTest.kt`
5. Added Step 8 artifact:
   - `docs/determinism.md`

### Verification
1. Passed Step 8 tests:
   - `./gradlew :game:test --tests "DeterministicReplaySameSeedSameTraceTest" --tests "DeterministicReplayDifferentSeedDivergesTest" --tests "FightCaveClosureNoDirectRandomUsageTest" --tests "RngCounterMonotonicityTest" --no-daemon`

### Outcome
- Step 8 exit criteria satisfied.
- Next target is Step 9 (oracle vs headless differential parity harness).


## 2026-03-05 19:59:49 -05:00 - Iteration 12 Step 9 Completed (Oracle vs Headless Differential Parity Harness)

### Decisions
1. Implemented Step 9 as a reusable differential harness that runs both oracle and headless through the same runtime API to reduce comparison drift.
2. Preserved fail-fast comparison semantics and persisted first-divergence artifacts for deterministic mismatch debugging.
3. Hardened parity runtime reset/bootstrap after diagnosing divergence caused by uninitialized drop tables during NPC death cleanup.

### Changes Made
1. Added shared runtime contract and oracle bootstrap path:
   - game/src/main/kotlin/FightCaveSimulationRuntime.kt
   - game/src/main/kotlin/OracleMain.kt
2. Added Step 9 harness core:
   - game/src/main/kotlin/ParityHarness.kt
   - includes ParitySnapshot, ParityMismatch, and first-divergence artifact writing to `temp/parity/first_divergence/`.
3. Added Step 9 test matrix and support:
   - game/src/test/kotlin/headless/parity/ParityHarnessSingleWaveTraceTest.kt
   - game/src/test/kotlin/headless/parity/ParityHarnessFullRunTraceTest.kt
   - game/src/test/kotlin/headless/parity/ParityHarnessJadHealerScenarioTest.kt
   - game/src/test/kotlin/headless/parity/ParityHarnessTzKekSplitScenarioTest.kt
   - game/src/test/kotlin/headless/parity/ParityHarnessTestSupport.kt
4. Added Step 9 docs artifact:
   - docs/parity_harness.md
5. Applied parity stability fixes discovered during implementation/debugging:
   - game/src/main/kotlin/RuntimeBootstrap.kt: initialize DropTables in headless cache bootstrap.
   - game/src/main/kotlin/ParityHarness.kt: clear GameObjects during runtime reset.
   - game/src/main/kotlin/HeadlessDataLoading.kt: preserve explicit headless.map.regions overrides.
   - game/src/test/kotlin/headless/parity/ParityHarnessTestSupport.kt: deterministic split scenario hook hardening.

### Verification
1. Initial Step 9 run under Java 17 failed before class execution due to Java 21 bytecode target mismatch.
2. Re-ran with Java 21 and passed Step 9 gate:
   - ./gradlew :game:test --tests "ParityHarnessSingleWaveTraceTest" --tests "ParityHarnessFullRunTraceTest" --tests "ParityHarnessJadHealerScenarioTest" --tests "ParityHarnessTzKekSplitScenarioTest" --no-daemon

### Outcome
- Step 9 exit criteria satisfied.
- Next target is Step 10 (runtime pruning and packaging).

## 2026-03-05 20:21:04 -05:00 - Iteration 13 Step 10 Completed (Runtime Pruning and Packaging)

### Decisions
1. Kept headed/oracle runtime behavior intact while enforcing stricter default pruning semantics only for `runtime.mode=headless`.
2. Added fail-fast startup guards so accidental activation of excluded systems in headless mode stops boot immediately.
3. Implemented packaging and deletion-inventory generation as build tasks to make Step 10 repeatable and testable.

### Changes Made
1. Updated runtime settings bootstrap in game/src/main/kotlin/RuntimeBootstrap.kt:
   - added headless pruned defaults for bots/events/autosave/spawn file bindings.
2. Updated headless startup in game/src/main/kotlin/HeadlessMain.kt:
   - added strict pruning guard checks for excluded stages, excluded script namespaces, and required pruned settings.
3. Added deletion inventory generator in game/src/main/kotlin/HeadlessDeletionCandidates.kt:
   - generates docs/deletion_candidates.md from config/headless_manifest.toml closure rules.
4. Updated game/build.gradle.kts with Step 10 packaging tasks:
   - headlessShadowJar (outputs `fight-caves-headless.jar`, Main-Class=HeadlessMain)
   - generateHeadlessDeletionCandidates
   - packageHeadless
   - headless distribution (headlessDistZip) containing headless jar + headless config artifacts + allowlisted data + run scripts.
5. Added Step 10 docs artifacts:
   - docs/runtime_pruning.md
   - regenerated docs/deletion_candidates.md
6. Added Step 10 tests:
   - game/src/test/kotlin/headless/pruning/HeadlessPackageStartsWithoutExcludedSystemsTest.kt
   - game/src/test/kotlin/headless/pruning/HeadedModeStillPassesBaselineFightCaveTests.kt
   - game/src/test/kotlin/headless/pruning/HeadlessDeletionCandidateInventoryTest.kt

### Verification
1. Compile gates passed:
   - ./gradlew :game:compileKotlin :game:compileTestKotlin --no-daemon
2. Step 10 tests passed:
   - ./gradlew :game:test --tests "HeadlessPackageStartsWithoutExcludedSystemsTest" --tests "HeadedModeStillPassesBaselineFightCaveTests" --tests "HeadlessDeletionCandidateInventoryTest" --no-daemon
3. Headless packaging build gate passed:
   - ./gradlew :game:packageHeadless --no-daemon
4. Headed regression gate passed:
   - ./gradlew :game:test --tests "content.area.karamja.tzhaar_city.TzhaarFightCaveTest" --no-daemon
5. Parity regression matrix passed after Step 10 changes:
   - ./gradlew :game:test --tests "ParityHarnessSingleWaveTraceTest" --tests "ParityHarnessFullRunTraceTest" --tests "ParityHarnessJadHealerScenarioTest" --tests "ParityHarnessTzKekSplitScenarioTest" --no-daemon

### Outcome
- Step 10 exit criteria satisfied.
- Next target is Step 11 (performance validation, post-parity only).

## 2026-03-05 20:22:14 -05:00 - Iteration 13 Entry Correction

The previous Step 10 entry contains formatting artifacts from shell escaping. Canonical text for the affected lines:

1. Decision 1 should read: kept headed/oracle behavior intact while enforcing stricter defaults only for `runtime.mode=headless`.
2. Step 10 packaging artifact name should read: `fight-caves-headless.jar`.
3. Step 9 artifact directory reference should read: `temp/parity/first_divergence/`.

## 2026-03-05 21:01:10 -05:00 - Iteration 14 Step 11 Completed (Performance Validation)

### Decisions
1. Added batch stepping as a first-class runtime API to support high-throughput simulation runs while preserving one-action-per-step semantics.
2. Kept Step 11 optimizations behavior-neutral and required a full Step 9 parity rerun after the optimization batch.
3. Standardized Step 11 reporting on generated benchmark artifacts (`docs/performance_benchmark.log`) plus a human-readable summary (`docs/performance_report.md`).

### Changes Made
1. Added `game/src/main/kotlin/HeadlessBatchStepping.kt`:
   - `runFightCaveBatch(...)`
   - `HeadlessBatchStepSnapshot`
   - `HeadlessBatchRunResult`
2. Optimized hot paths without gameplay-semantic changes:
   - single-pass `prayerPotionDoseCount(...)` inventory scan in `game/src/main/kotlin/HeadlessObservationBuilder.kt`
   - validated/optimized `HeadlessRuntime.tick(times)` loop in `game/src/main/kotlin/HeadlessMain.kt`
3. Added Step 11 performance test suite:
   - `game/src/test/kotlin/headless/performance/HeadlessStepRateBenchmarkTest.kt`
   - `game/src/test/kotlin/headless/performance/HeadlessLongRunStabilityTest.kt`
   - `game/src/test/kotlin/headless/performance/HeadlessBatchSteppingParityTest.kt`
   - `game/src/test/kotlin/headless/performance/HeadlessPerformanceReportGenerationTest.kt`
4. Added/updated Step 11 artifacts:
   - `docs/performance_benchmark.log`
   - `docs/performance_report.md`

### Verification
1. Initial combined Step 11 + parity run failed under Java 17 with:
   - `UnsupportedClassVersionError` (class file version 65.0)
2. Re-ran under Java 21 and passed:
   - `./gradlew :game:test --tests "HeadlessStepRateBenchmarkTest" --tests "HeadlessLongRunStabilityTest" --tests "HeadlessBatchSteppingParityTest" --tests "HeadlessPerformanceReportGenerationTest" --tests "ParityHarnessSingleWaveTraceTest" --tests "ParityHarnessFullRunTraceTest" --tests "ParityHarnessJadHealerScenarioTest" --tests "ParityHarnessTzKekSplitScenarioTest" --no-daemon`
3. Benchmark artifact values (latest):
   - `throughput.ticks_per_second=8891.93`
   - `soak.ticks_per_second=9186.05`

### Outcome
- Step 11 exit criteria satisfied.
- Step status advanced: Step 11 complete, Step 12 next.


## 2026-03-06 10:42:00 -05:00 - Iteration 15 Step 12 Re-Run Completed (Acceptance Gate with Speedups)

### Decisions
1. Re-ran the full Step 12 acceptance matrix after applying test-runtime speedups, without reducing Fight Caves/headless/parity acceptance scope.
2. Standardized acceptance verification on Java 21 to match project bytecode target and prevent cross-JDK drift.
3. Captured release-gate output in a dedicated release-candidate artifact for sign-off traceability.

### Changes Made
1. Confirmed acceptance-safe speedups in the active tree:
   - unrelated legacy tests under `game/src/test/kotlin/content/**` physically pruned
   - required headed regression test retained: `content/area/karamja/tzhaar_city/TzhaarFightCaveTest.kt`
   - `e2eTest` worker hardening in `game/build.gradle.kts` (`maxHeapSize=2048m`, `maxParallelForks=1`, `forkEvery=1`)
2. Generated/updated Step 12 artifact:
   - `docs/release_candidate.md`

### Verification
1. `./gradlew clean --no-daemon` - PASS (~17s)
2. `./gradlew :game:test --no-daemon` - PASS (~5m45s)
3. `./gradlew :game:test --tests "*Headless*" --no-daemon` - PASS (~2m38s)
4. `./gradlew :game:test --tests "*Parity*" --no-daemon` - PASS (~2m44s)
5. `./gradlew :game:test --tests "content.area.karamja.tzhaar_city.TzhaarFightCaveTest" --no-daemon` - PASS (~40s)
6. `./gradlew :game:headlessDist --no-daemon` - PASS (~25s)
7. `./gradlew :game:e2eTest --no-daemon` - PASS (~2m26s)

### Outcome
- Step 12 exit criteria satisfied on speedup-hardened test/runtime configuration.

## 2026-03-06 10:58:00 -05:00 - Iteration 16 Step 13 Closeout Completed (Post-Prune Validation)

### Decisions
1. Closed Step 13 with explicit post-prune structural assertions and prune-manifest retention checks for the pruned content test root.
2. Regenerated deletion-candidate inventory after speedup changes to preserve current-state deletion accounting.
3. Preserved recovery refs (branch + tag) for low-risk rollback of prune state if needed.

### Changes Made
1. Updated prune report and status tracking docs:
   - `docs/repo_prune_report.md`
   - `plan.md`
   - `spec.md`
2. Updated prune manifest contract and test support:
   - `config/headless_prune_manifest.toml` (version `2`, `pruned_test_root`, `retained_test_files`)
   - pruning test support/assertions enforce retained file closure under pruned root
3. Regenerated prune inventory artifact:
   - `docs/deletion_candidates.md`

### Verification
1. `./gradlew :game:test --tests "ProjectTreeMatchesApprovedManifestTest" --tests "ForbiddenPathsAbsentTest" --tests "HeadlessDeletionCandidateInventoryTest" --no-daemon` - PASS
2. `./gradlew :game:generateHeadlessDeletionCandidates --no-daemon` - PASS
   - result: `modules=0`, `codeFiles=872`, `dataFiles=3012`

### Outcome
- Step 13 exit criteria satisfied after post-prune validation rerun.

## 2026-03-06 11:36:00 -05:00 - Iteration 17 Textual Parity Alignment Hardening

### Decisions
1. Treated documentation/manifests/guard-lists as contract surfaces and aligned them to the implemented runtime to avoid future drift.
2. Standardized `FloorItems` as explicitly excluded in headless mode across spec, manifest, runtime guard list, and pruning tests.
3. Corrected episode init docs to match implemented wave boot semantics (`start = false`) and clarified HP scale wording.

### Changes Made
1. Updated `spec.md`:
   - explicit current headless excluded stage set now includes `FloorItems`
   - fixed skill/resources wording clarifies `70 HP` display vs internal `700`
2. Updated `docs/episode_init_contract.md`:
   - corrected wave boot call to `fightCave.startWave(..., start = false)`
   - added rationale for `start = false` in headless episodes
3. Updated `config/headless_manifest.toml`:
   - added `FloorItems` to `[tick_pipeline].headless_candidate_excluded`
4. Updated `docs/runtime_pruning.md` and `docs/extraction_manifest.md`:
   - aligned excluded-stage and stage notes with headless implementation
5. Updated runtime/test guards:
   - `game/src/main/kotlin/HeadlessMain.kt`
   - `game/src/test/kotlin/headless/pruning/HeadlessPackageStartsWithoutExcludedSystemsTest.kt`
   - `game/src/test/kotlin/headless/bootstrap/HeadlessBootWithoutNetworkTest.kt`
6. Updated execution tracking docs:
   - `plan.md` (Iteration 17 entry)

### Outcome
- Textual parity is now aligned with runtime behavior for the identified non-blocking gaps.

## 2026-03-06 12:08:00 -05:00 - Iteration 18 - Local Path Migration and Repo Rename

### Decisions
1. Adopted `C:\Users\jorda\dev\personal_projects\fight-caves-RL` as the canonical local repository path.
2. Renamed GitHub repository to `fight-caves-RL` to match local project naming.
3. Updated active documentation references that would otherwise point to stale local paths or old repo naming.

### Changes Made
1. Updated `README.md` clone/badge references to `fight-caves-RL`.
2. Updated active plan/spec/release notes for completed local path migration:
   - `plan.md`
   - `spec.md`
   - `docs/release_candidate.md`
3. Removed stale absolute OneDrive path reference in `docs/baseline.md`.
4. Renamed GitHub repository via CLI:
   - `gh repo rename fight-caves-RL --repo jordanbailey00/fight-caves-rsps --yes`

### Outcome
- Naming/path references now align with the current local and GitHub repository identity.
## 2026-03-09

### Phase 1 Flat Observation Implementation

#### Decisions
1. The first flat training schema moved from design-only to implemented sim-owned runtime surface.
2. `jad_telegraph_state` remains part of the protected raw-to-flat equivalence set and is now covered by source-side flat projection tests.
3. Native-Linux remains the final Phase 1 continue-vs-pivot source of truth even though the local WSL preview already clears the numeric planning thresholds.

#### Changes Made
1. Added the flat observation emitter/runtime plumbing:
   - `game/src/main/kotlin/HeadlessFlatObservationBuilder.kt`
   - `game/src/main/kotlin/FightCaveSimulationRuntime.kt`
   - `game/src/main/kotlin/HeadlessMain.kt`
   - `game/src/main/kotlin/OracleMain.kt`
2. Added the first flat certification slice:
   - `game/src/test/kotlin/headless/observation/FlatObservationProjectionEqualityTest.kt`
3. Added the hosted native-Linux Phase 1 packet workflow:
   - `.github/workflows/phase1_native_linux_packet.yml`
4. Updated the active flat-path docs:
   - `docs/flat_training_observation_schema.md`
   - `docs/raw_flat_observation_contract.md`
   - `docs/raw_flat_equivalence_plan.md`

#### Verification
1. `./gradlew --no-daemon :game:compileKotlin :game:test --tests 'FlatObservationProjectionEqualityTest' :game:headlessDistZip` - PASS
2. Targeted Jad/observation/parity regression slice - PASS

#### Outcome
- Phase 1 sim-side flat emitter implementation is in place and certification coverage has started.

### Phase 1 Native-Linux Gate Execution And Workflow Hardening

#### Decisions
1. Treated the first hosted Phase 1 failures as workflow-path diagnostics problems first, not as evidence that the flat-path implementation itself was wrong.
2. Published the full Phase 0 packet directory into `codex/phase0-results` because the Phase 1 gate needs the individual baseline row files, not only `gate_summary.json`.
3. Preserved the hosted Phase 1 packet stderr/stdout/meta on failure by publishing them to `codex/phase1-results`.
4. Treated the current native-Linux ratio gate as invalid because the active `phase0-results/latest` baseline was republished after the Phase 1 code landed.

#### Changes Made
1. Hardened `.github/workflows/phase0_native_linux_packet.yml` to publish the full baseline packet contents.
2. Hardened `.github/workflows/phase1_native_linux_packet.yml` to:
   - fetch the full Phase 0 baseline set
   - preserve packet failure diagnostics
   - create the `codex/phase1-results` branch reliably on first publish
3. Used the published Phase 1 diagnostics to identify the hosted-only RL packet bug in `scripts/refresh_phase1_packet.py` and confirm the later end-to-end packet success.

#### Verification
1. Hosted Phase 0 baseline republish completed successfully.
2. Hosted Phase 1 reruns eventually completed the packet and published:
   - `bridge_*.json`
   - `vecenv_*.json`
   - `phase1_packet.json`
   - `gate_summary.json`
   - `python_vec16_top.txt`
3. The final hosted Phase 1 gate failed only at the enforcement step, with packet generation and publication succeeding.

#### Outcome
- The native-Linux Phase 1 implementation path is now observable and reproducible.
- That blocker is now resolved by the immutable pre-Phase-1 baseline workflow and the clean rerun against that baseline.

### Immutable Pre-Phase-1 Baseline Publication And Final Gate Decision

#### Decisions
1. Locked the clean Phase 1 comparison to an immutable pre-Phase-1 native-Linux baseline path instead of any mutable `latest` alias.
2. Treated the clean rerun against that immutable baseline as the only valid continue-versus-pivot decision for Phase 1.
3. Approved continuation to Phase 2 because the clean hosted native-Linux gate cleared the defined bridge, vecenv, and profiler thresholds.

#### Changes Made
1. Added `.github/workflows/phase0_native_linux_pre_phase1_baseline.yml`.
2. Published the immutable baseline packet under:
   - `phase0-native-linux/immutable/pre-phase1/rl-3e557474f3c6b4e44842da82a971c8f97d521b10__sim-216c1fd2ac31f450f8c599f9ec9454330a4e6b3a`
3. Updated `.github/workflows/phase1_native_linux_packet.yml` to fetch that immutable baseline path instead of `phase0-native-linux/latest`.

#### Verification
1. Immutable pre-Phase-1 native-Linux baseline workflow completed successfully.
2. Final hosted native-Linux Phase 1 packet completed successfully against the immutable baseline.
3. Final gate summary reports:
   - bridge `64 env = 9148.80` env/s, ratio `6.6397`
   - vecenv `64 env = 10961.11` env/s, ratio `8.0101`
   - `raw_object_conversion_still_dominant = false`
   - `phase2_unblocked = true`

#### Outcome
- Phase 1 is now closed with a clean source-of-truth comparison.
- Phase 2 is unblocked.
