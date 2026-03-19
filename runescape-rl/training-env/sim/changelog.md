# changelog.md

## 2026-03-10

- Completed the hosted native-Linux Phase 2 prototype packet after fixing the branch-scoped packaging contract:
  - the workflow now validates the packaged headless distribution via `scripts/headless_distribution_contract.py`
  - the shared Gradle build version now sanitizes `GITHUB_REF_NAME` so branch refs no longer create nested distribution paths
  - the source-of-truth packet completed successfully on `ubuntu-latest` using:
    - `fight-caves-headless-codex-phase2-prototype-native-linux.zip`
- Reproduced the hosted headless-distribution artifact bug locally with a repo-owned contract harness:
  - added `scripts/headless_distribution_contract.py`
  - validated the exact downstream contract under `game/build/distributions/fight-caves-headless*.zip`
  - confirmed the workflow YAML locally with `act --validate` before returning to hosted reruns
- Fixed the root cause in shared Gradle versioning:
  - `buildSrc/src/main/kotlin/shared.gradle.kts` now sanitizes `GITHUB_REF_NAME` before assigning `project.version`
  - branch refs like `codex/phase2-prototype-native-linux` no longer turn `headlessDistZip` into a nested path under `game/build/distributions/`
- Hardened the branch-scoped `Phase 2 Native Linux Packet` workflow for the prototype rerun:
  - replaced the raw GitHub cache-bootstrap download with a checkout of the cache-bootstrap branch
  - made result-summary publishing skip cleanly when the packet is absent
  - switched the workflow to the repo-owned `headless_distribution_contract.py` harness instead of inline packaging logic

## 2026-03-09

- Added a hosted native-Linux train-ceiling workflow for the Phase 2 pivot:
  - `.github/workflows/phase2_native_linux_train_ceiling.yml`
- The new workflow:
  - reuses the existing hosted cache/bootstrap path
  - builds the canonical `:game:headlessDistZip`
  - runs the RL-owned fake-env learner-ceiling benchmark on native Linux
  - publishes results to `codex/phase2-results/phase2-train-ceiling-native-linux/latest`
- Purpose:
  - prove whether the current trainer loop is already the dominant blocker on the source-of-truth host
  - avoid continuing Phase 2 transport promotion work blindly if the train loop itself is now flattening throughput

- Completed `WC-P1-04` from the workspace optimization plan.
- Added the sim-owned certification-gate source of truth:
  - `docs/raw_flat_equivalence_plan.md`
- Froze the rule that the future flat path is trusted only after source-side equivalence, RL-side consumer equivalence, determinism/parity reruns, and Jad telegraph equivalence all remain green.

- Completed `WC-P1-02` from the workspace optimization plan.
- Added the sim-owned flat-path design source of truth:
  - `docs/flat_training_observation_schema.md`
- Froze the first flat schema as:
  - `schema_id = headless_training_flat_observation_v1`
  - fixed-size `float32`
  - row-major `[env_count, 134]`
  - semantically equivalent to the current raw contract
  - intentionally aligned to the already-shipped trainer tensor layout for the first migration step
- Locked the downstream constraint that `jad_telegraph_state` remains part of the flat-path equivalence set rather than being replaced with an easier oracle cue.

- Completed the sim-owned contract-definition portion of optimization `WC-P1-01`.
- Added the new source-of-truth contract doc:
  - `docs/raw_flat_observation_contract.md`
- Locked the current raw headless observation path as the semantic reference for future flat-schema work:
  - `docs/observation_schema.md` now explicitly states that future flat training schemas must preserve field meaning, visible-NPC ordering, and parity-sensitive combat cues such as `jad_telegraph_state`
- Recorded the Jad telegraph downstream optimization implication:
  - `jad_telegraph_state` is now protected raw semantic content for future flat training schema design, not a disposable trainer-local convenience field

- Completed the remaining Jad telegraph validation slices:
  - `JAD-05` regression gate
  - `JAD-06` replay/demo outcome acceptance
- Extended the authoritative Jad trace in:
  - `game/src/main/kotlin/content/area/karamja/tzhaar_city/JadTelegraph.kt`
  - `game/src/main/kotlin/content/area/karamja/tzhaar_city/TzTokJad.kt`
- Added persistent outcome/timing fields for replay/parity inspection:
  - `prayer_check_tick`
  - `sampled_protection_prayer`
  - `protected_at_prayer_check`
  - `resolved_damage`
  - retained last-attack telegraph timing even after the active telegraph clears back to idle
- Locked the current engine prayer timing with regression tests instead of changing it:
  - Jad protection is sampled when the queued `hit(...)` is constructed after the `3`-tick windup
  - the later delayed visual landing does not reopen the prayer decision window
- Added focused Jad protection/outcome coverage in:
  - `game/src/test/kotlin/content/area/karamja/tzhaar_city/JadTelegraphPrayerProtectionTest.kt`
  - `game/src/test/kotlin/content/area/karamja/tzhaar_city/JadTelegraphTestSupport.kt`
  - `game/src/test/kotlin/headless/determinism/HeadlessReplayJadTelegraphTraceTest.kt`
  - `game/src/test/kotlin/headless/parity/ParityHarnessJadPrayerResolutionParityTest.kt`
- Scoped the final parity acceptance to Jad-specific trace/outcome assertions when unrelated oracle-side wave activity would add whole-snapshot noise outside this mini-rework.

- Completed the next Jad telegraph parity slices:
  - `JAD-03` headless observation exposure
  - `JAD-04` replay/parity trace wiring
- Exposed the authoritative Jad telegraph as an additive raw observation field:
  - `HeadlessObservationNpc.jadTelegraphState`
  - serialized as `jad_telegraph_state`
  - values:
    - `0 = idle`
    - `1 = magic_windup`
    - `2 = ranged_windup`
- Kept the raw sim schema on additive `headless_observation_v1` instead of creating a new sim schema version.
- Added replay/parity trace views sourced from the same authoritative Jad telegraph state:
  - `JadTelegraphTrace`
  - `HeadlessReplayTickSnapshot.jadTelegraph`
  - `ParitySnapshot.jadTelegraph`
- Added focused Jad telegraph coverage for:
  - headless observation exposure
  - replay trace capture
  - oracle-vs-headless parity trace alignment

- Started the Jad telegraph parity rework and completed the first two scoped chunks:
  - `JAD-01` current timing freeze and integration-point audit
  - `JAD-02` authoritative Jad telegraph state in the shared combat path
- Codified the existing Jad timing contract without changing combat semantics:
  - `TzTokJad` still queues the custom Jad hit after `3` game ticks
  - the Jad hit still resolves through the existing `hit(... delay = 64)` path
  - total telegraph-to-resolution window remains the current `6` game ticks
- Added repo-owned Jad telegraph state in:
  - `game/src/main/kotlin/content/area/karamja/tzhaar_city/JadTelegraph.kt`
- Anchored telegraph onset to the current headed animation tick by starting the Jad telegraph at the generic NPC swing point in:
  - `game/src/main/kotlin/content/entity/npc/combat/Attack.kt`
- Kept the existing Jad-specific attack override in:
  - `game/src/main/kotlin/content/area/karamja/tzhaar_city/TzTokJad.kt`
  but replaced raw timing literals with named shared constants so the parity contract is explicit.
- Added initial Jad telegraph regression coverage in:
  - `game/src/test/kotlin/content/area/karamja/tzhaar_city/JadTelegraphStateTest.kt`
- No headless observation or RL-side schema exposure was added in this step yet; that remains later Jad work.

- Started Phase 0 optimization implementation work for the headless sim measurement gate.
- Added clean standalone sim measurement entrypoints in `game/build.gradle.kts`:
  - `:game:headlessPerformanceReport`
  - `:game:headlessPerformanceProfile`
- Expanded the sim-side performance harness in:
  - `game/src/test/kotlin/headless/performance/HeadlessPerformanceReportGenerator.kt`
  - the harness now writes both `history/performance_benchmark.log` and `history/performance_benchmark.json`
  - the harness now records runtime metadata and a batched headless throughput row
  - the harness now publishes a per-worker ceiling estimate
- Updated performance harness regression coverage in:
  - `game/src/test/kotlin/headless/performance/HeadlessPerformanceReportGenerationTest.kt`
- Executed the standalone report and profile tasks successfully on the current WSL host:
  - `./gradlew --no-daemon :game:headlessPerformanceReport`
  - `./gradlew --no-daemon :game:headlessPerformanceProfile`
- Recorded the current-host standalone sim results:
  - single-slot throughput about `30.5k` ticks/sec
  - batched headless throughput (`16 envs`) about `473.6k` env steps/sec
  - clean JFR artifact emitted at `game/build/reports/headless-performance/headless_performance_profile.jfr`
- Recorded one important Phase 0 interpretation change:
  - the older Step 11 `8.9k` artifact is now historical context, not the active per-worker ceiling estimate
  - the remaining blocker before Phase 1 is the native-Linux source-of-truth rerun, not missing profiler infrastructure
- Updated sim-side docs to reflect the new standalone Phase 0 path:
  - `docs/sim_profiler_report.md`
  - `docs/sim_profiler_report.md`

- Added a sim-side performance audit report in:
  - `docs/sim_profiler_report.md`
- Recorded the current audit outcome for JVM profiling:
  - the repo still has a real direct-JVM throughput artifact in `history/performance_report_step11.md` at about `8.9k` ticks/sec
  - the current WSL host passes `HeadlessStepRateBenchmarkTest`
  - embedded-JVM JFR dump attempts launched from the RL Python benchmark did not dump cleanly on exit
  - Gradle/JUnit JFR capture succeeded but was dominated by Gradle / JUnit / JaCoCo harness noise and did not yield clean headless-symbol CPU samples
  - `jcmd -l` did not expose the embedded JPype JVM as an attachable target during the RL bridge benchmark
- Documented the profiler limitation explicitly instead of claiming function-level JVM hotspots without clean samples.
- Expanded the sim profiler report with the exact JFR commands, runtime/JVM details, and benchmark-scenario metadata used in this audit pass.

## 2026-03-08

- Tightened the root `FCspec.md` episode-start contract so the primary module spec now matches the already-documented headless reset contract:
  - fixed run toggle wording to unconditional `ON`
  - added explicit reset input defaults for `startWave`, `ammo`, `prayerPotions`, and `sharks`
  - added the missing `all other skills: 1` line
  - clarified that no neck item is part of the canonical loadout
- Added an explicit root-spec note that the current `create_release.yml` remains inherited from the upstream Void fork and is not the source of truth for headless artifact naming or packaging contracts.
- Updated the root README and end-to-end checklist to use the canonical headless artifact wording:
  - default task `:game:headlessDistZip`
  - fallback/validation task `:game:packageHeadless`

## 2026-03-07

- Adopted canonical Fight Caves module root filenames:
  - `FCspec.md`
  - `FCplan.md`
- Renamed the prior root files from `spec.md` and `plan.md`.
- Updated active functional references so the rename does not break:
  - repo-root detection in Kotlin sources/tests
  - headless manifest references
  - current README guidance
  - current end-to-end checklist docs
- Existing detailed historical execution notes remain in `history/detailed_changelog.md`.
## 2026-03-10

- Pruned and reorganized repo documentation to reduce active-doc clutter:
  - moved historical extraction/prune/performance docs under `history/`
  - kept `docs/sim_profiler_report.md` as the canonical current sim-performance document
  - renamed the active end-to-end acceptance checklist to `docs/e2e_acceptance.md`
  - kept `changelog.md` as the canonical module changelog
  - moved the prior detailed execution log to `history/detailed_changelog.md`

## 2026-03-09

- Added the hosted native-Linux Phase 2 packet workflow:
  - `.github/workflows/phase2_native_linux_packet.yml`
- Phase 2 native-Linux workflow intent:
  - build the canonical `:game:headlessDistZip`
  - refresh the RL-owned Phase 2 transport packet on native Linux
  - publish `phase2_packet.json` and gate summaries to `codex/phase2-results`
  - enforce the `WC-P2-03` promotion gate before any production transport swap proceeds
- Executed the first hosted native-Linux Phase 2 packet run:
  - workflow completed packet generation and publication successfully
  - final workflow failure came only from the gate enforcement step
  - published gate result:
    - transport `64 env`: `1.3995x`
    - disabled train `64 env`: `1.0048x`
    - shared-train scaling `64 vs 16`: `0.9534x`
  - `WC-P2-03` remains blocked because the end-to-end training signal is too weak

- Added a dedicated immutable native-Linux pre-Phase-1 baseline workflow:
  - `.github/workflows/phase0_native_linux_pre_phase1_baseline.yml`
- Published the immutable pre-Phase-1 native-Linux baseline packet to:
  - `codex/phase0-results/phase0-native-linux/immutable/pre-phase1/rl-3e557474f3c6b4e44842da82a971c8f97d521b10__sim-216c1fd2ac31f450f8c599f9ec9454330a4e6b3a`
- Repointed the hosted Phase 1 gate to that immutable baseline:
  - `.github/workflows/phase1_native_linux_packet.yml`
- The final hosted native-Linux Phase 1 gate now passes with:
  - bridge `64 env = 9148.80` env/s, ratio `6.6397`
  - vecenv `64 env = 10961.11` env/s, ratio `8.0101`
  - `phase2_unblocked = true`

- Implemented the sim-owned Phase 1 flat observation emitter:
  - added `game/src/main/kotlin/HeadlessFlatObservationBuilder.kt`
  - extended the headless runtime surface to expose direct flat observations
  - wired `HeadlessMain` and `OracleMain` to the new flat observation builder
- Added the first source-side raw-vs-flat certification slice:
  - `game/src/test/kotlin/headless/observation/FlatObservationProjectionEqualityTest.kt`
  - coverage includes representative projection equality and explicit Jad telegraph preservation
- Added the hosted native-Linux Phase 1 gate workflow:
  - `.github/workflows/phase1_native_linux_packet.yml`
- Phase 1 implementation is now ready for source-of-truth native-Linux gate review; no combat semantics changed in this batch.
- Hardened the hosted native-Linux workflow path:
  - Phase 0 results publication now publishes the full baseline packet directory, not just the top-level summaries
  - Phase 1 results publication now captures packet stdout/stderr/meta on failure
  - Phase 1 results publication now creates `codex/phase1-results` robustly on first publish
- Executed the hosted native-Linux Phase 1 packet end to end:
  - bridge and vecenv rows are published
  - steady-state Python profile is published
  - the remaining blocker is a contaminated `phase0-results/latest` comparison baseline, not a failing Phase 1 packet implementation
