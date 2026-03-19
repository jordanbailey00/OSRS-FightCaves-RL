# Workspace Changelog

This is the historical changelog for the `/home/jordan/code` workspace.

It is intentionally higher-level than per-repo commit logs. Each entry records:
- when the change happened
- what type of change it was
- where the work landed
- why the change was made
- what worked
- what did not work or still matters for future reference

This file is synthesized from:
- `/home/jordan/code/RL/changelog.md`
- `/home/jordan/code/RSPS/changelog.md`
- `/home/jordan/code/fight-caves-RL/changelog.md`
- `/home/jordan/code/fight-caves-RL/history/detailed_changelog.md`
- `/home/jordan/code/optimization_history.md`
- `/home/jordan/code/pivot_plan.md`
- `/home/jordan/code/pivot_implementation_plan.md`
- `/home/jordan/code/fight-caves-demo-lite/session-logs/`
- `/home/jordan/code/.artifacts/void-client/headed-validation/`
- current local workspace state as of `2026-03-16`

## 2021-01-23 to 2026-01-24
- Type: `implementation`
- Paths affected: `/home/jordan/code/RSPS/engine`, `/home/jordan/code/RSPS/network`, `/home/jordan/code/RSPS/game`, `/home/jordan/code/RSPS/cache`, `/home/jordan/code/RSPS/data`
- What changed: the upstream Void/RSPS runtime matured into the mechanics and protocol base now reused by this workspace. Over this span it gained movement/pathfinding rewrites, ranged combat, networking, file-server support, collision and performance work, dynamic-zone fixes, audit logging, JDK 21 / Kotlin 2.2 / Gradle 9 upgrades, and on `2026-01-16` the upstream `TzHaar` city and Fight Caves content landed.
- Why: this gave the workspace a real RSPS server/runtime with the authentic dependencies a real client expects: login, JS5/file-server, dynamic region loading, NPC/player updates, combat, interfaces, containers, vars, and Fight Caves content scripts.
- What worked: this upstream foundation is the reason the later RSPS-backed headed demo is even viable. It already owned the in-game mechanics, cache/data model, and client protocol surface.
- What did not work / future reference: this runtime is far too broad and stateful for the RL hot path. It is a strong oracle/headed base, but it is not the right training kernel. Earlier docs in the workspace also initially under-described this repo's actual role because they still reflected inherited upstream identity.

## 2026-03-05 to 2026-03-06
- Type: `implementation`
- Paths affected: `/home/jordan/code/fight-caves-RL`, `/home/jordan/code/fight-caves-RL/game`, `/home/jordan/code/fight-caves-RL/docs`
- What changed: the extracted Fight Caves simulator/oracle repo was initialized and aligned. The repo/path migration was finalized, the headless parity implementation was landed, and the module was turned into the workspace-owned Fight Caves simulation/reference codebase rather than a generic RSPS tree.
- Why: the project needed a repo that could own Fight Caves-only headless and oracle/reference semantics without the full general-RSPS burden.
- What worked: this created a focused simulator repo with clearer Fight Caves ownership, easier headless execution, and a place to concentrate parity and performance work.
- What did not work / future reference: inherited docs and workflow assumptions still needed later cleanup. Release/build naming and some root docs were still partly inherited from upstream conventions and had to be corrected in later passes.

## 2026-03-07 to 2026-03-08
- Type: `implementation`
- Paths affected: `/home/jordan/code/RL`, `/home/jordan/code/RL/fight_caves_rl`, `/home/jordan/code/RL/configs`, `/home/jordan/code/RL/scripts`, `/home/jordan/code/RL/docs`
- What changed: the RL repo was bootstrapped as a real training/orchestration owner. The project adopted `pufferlib-core` as the baseline, froze the RL-to-sim integration contract, added the correctness-first bridge scaffold, landed determinism/parity canaries, added the first PufferLib smoke integration, defined W&B/logging contracts, then completed the first major RL slices for vectorized backend wiring, reward/curriculum scaffolding, replay/eval artifacts, performance hardening, parity validation, and the MVP acceptance gate.
- Why: this established a working RL stack before deeper optimization or architectural pivots. The team needed correctness, replay, parity, and trainer orchestration in place before pushing toward throughput.
- What worked: the RL repo became the clear owner for training, configs, reward registries, replay/eval tooling, acceptance gates, and PufferLib integration. It gave the workspace a coherent trainer-facing control plane instead of ad hoc scripts.
- What did not work / future reference: the initial training path was still correctness-first and expensive. The system was functional, but it was nowhere near the final throughput target. The later pivot work exists precisely because this initial stack proved the contracts and tooling, not because it solved performance.

## 2026-03-08
- Type: `bugfix`
- Paths affected: `/home/jordan/code/RL/fight_caves_rl/envs/subprocess_vector_env.py`, `/home/jordan/code/RL/fight_caves_rl/puffer/factory.py`, `/home/jordan/code/RL/scripts/train.py`, `/home/jordan/code/RL/fight_caves_rl/utils/config.py`, `/home/jordan/code/RL/tests`, `/home/jordan/code/RL/README.md`, `/home/jordan/code/RL/.env.example`
- What changed: a real reset-boundary training crash was reproduced and isolated. The failure only appeared after `PuffeRL.train()` shared a process with the embedded JPype/JVM runtime and the next reset crossed back into the episode-reset path. The shipped remediation made subprocess-isolated vecenv workers the default training backend while keeping embedded mode for correctness tooling and microbenchmarks. On the same day, a real W&B bootstrap bug was fixed so URL-style `WANDB_ENTITY` inputs were normalized instead of being passed through directly and rejected by W&B.
- Why: the workspace needed a stable end-to-end training path before more optimization or product work could be trusted.
- What worked: post-fix training completed across reset boundaries with W&B disabled and online. The default training path became robust enough to continue development. W&B config handling also became much more user-proof.
- What did not work / future reference: the stable subprocess path confirmed correctness, but it also made the performance gap impossible to ignore. Local measurements after stabilization were still roughly tens of SPS, far below the long-term target.

## 2026-03-08 to 2026-03-09
- Type: `validation`
- Paths affected: `/home/jordan/code/RL/docs/performance_decomposition_report.md`, `/home/jordan/code/RL/docs/hotpath_map.md`, `/home/jordan/code/RL/docs/benchmark_matrix.md`, `/home/jordan/code/RL/docs/python_profiler_report.md`, `/home/jordan/code/RL/docs/runtime_topology.md`, `/home/jordan/code/RL/scripts/refresh_phase0_packet.py`, `/home/jordan/code/fight-caves-RL/docs/sim_profiler_report.md`, `/home/jordan/code/fight-caves-RL/game/build.gradle.kts`, `/home/jordan/code/fight-caves-RL/.github/workflows/phase0_native_linux.yml`
- What changed: the workspace moved to a measurement-first optimization program. Clean benchmark and profiler entrypoints were added, a Phase 0 packet was defined, native Linux was frozen as the source of truth for throughput decisions, and both RL-side and sim-side performance reports were published. The simulator also gained standalone headless performance report/profile tasks. Hosted/self-hosted native-Linux packet workflows were introduced and Phase 0 was cleared.
- Why: the team needed hard evidence instead of guessing at bottlenecks. This was the point where optimization became a formal program rather than an intuition-driven cleanup effort.
- What worked: the simulator-only path showed much higher raw throughput than the end-to-end trainer path, which immediately narrowed the search. The native-Linux Phase 0 gate passed and unblocked deeper performance work.
- What did not work / future reference: direct profiling of the embedded JVM from the Python-launched benchmark path was unreliable. `jcmd` visibility and JFR dump behavior were not good enough to support confident fine-grained JVM hotspot claims, so the project had to rely on standalone sim profiling plus Python-side profiling for the mixed stack.

## 2026-03-09
- Type: `implementation`
- Paths affected: `/home/jordan/code/fight-caves-RL/game/src/main/kotlin/content/area/karamja/tzhaar_city`, `/home/jordan/code/fight-caves-RL/game/src/test/kotlin/content/area/karamja/tzhaar_city`, `/home/jordan/code/RL/fight_caves_rl/envs/schema.py`, `/home/jordan/code/RL/fight_caves_rl/envs/puffer_encoding.py`, `/home/jordan/code/RL/fight_caves_rl/replay/trace_packs.py`, `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city`, `/home/jordan/code/RSPS/docs/jad_telegraph_plan.md`
- What changed: the Jad telegraph parity rework landed across the simulator, RL observation contract, replay/parity traces, and headed RSPS code path. An authoritative shared Jad telegraph state was introduced, exposed in headless observations as additive `jad_telegraph_state`, and wired into replay/parity trace surfaces. Later slices added prayer-check timing and outcome fields such as `prayer_check_tick`, `sampled_protection_prayer`, `protected_at_prayer_check`, and `resolved_damage`.
- Why: the project needed a real, parity-safe Jad cue that the policy could learn from, without introducing a prayer oracle or changing underlying combat timing.
- What worked: the telegraph state became a shared semantic contract across headed, headless, and RL tooling. The work was additive, regression-tested, and explicitly kept the original combat timing intact.
- What did not work / future reference: this area later exposed a broader regression in live RSPS runtime when non-Jad NPC combat could incorrectly touch Jad telegraph state. That later regression is not a reason to undo the telegraph design, but it is now a tracked correctness issue that must be resolved before the final headed trust gate closes.

## 2026-03-09 to 2026-03-10
- Type: `performance`
- Paths affected: `/home/jordan/code/fight-caves-RL/game/src/main/kotlin/HeadlessFlatObservationBuilder.kt`, `/home/jordan/code/fight-caves-RL/game/src/main/kotlin/HeadlessMain.kt`, `/home/jordan/code/fight-caves-RL/game/src/main/kotlin/OracleMain.kt`, `/home/jordan/code/fight-caves-RL/game/src/test/kotlin/headless/observation/FlatObservationProjectionEqualityTest.kt`, `/home/jordan/code/RL/fight_caves_rl/envs/observation_views.py`, `/home/jordan/code/RL/fight_caves_rl/bridge`, `/home/jordan/code/RL/tests/integration/test_flat_observation_matches_raw_projection.py`, `/home/jordan/code/RL/docs/flat_observation_ingestion.md`
- What changed: the raw-vs-flat observation contract was frozen, the first flat observation schema was designed, the sim-owned flat observation emitter was implemented, and RL production paths were switched to consume sim-emitted flat observations while certification/reference paths stayed on the raw path. Hosted native-Linux Phase 1 gate workflows were added, an immutable pre-Phase-1 baseline was published, and the final native-Linux Phase 1 gate passed.
- Why: Python object construction and observation pythonization had been identified as major hot-path costs. The flat path was the first conservative performance redesign that kept semantics fixed while attacking those costs.
- What worked: the flat path made raw object conversion non-dominant on the source-of-truth host and materially improved bridge and vecenv throughput. The immutable-baseline workflow also fixed a real gate-validity problem when the mutable `latest` baseline became contaminated.
- What did not work / future reference: even after Phase 1, end-to-end training was still far from the final target. Phase 1 removed one large bottleneck, but it did not solve trainer throughput.

## 2026-03-09 to 2026-03-10
- Type: `performance`
- Paths affected: `/home/jordan/code/RL/fight_caves_rl/envs/shared_memory_transport.py`, `/home/jordan/code/RL/fight_caves_rl/benchmarks/subprocess_transport_bench.py`, `/home/jordan/code/RL/fight_caves_rl/benchmarks/train_ceiling_bench.py`, `/home/jordan/code/RL/fight_caves_rl/puffer/trainer.py`, `/home/jordan/code/RL/fight_caves_rl/puffer/production_trainer.py`, `/home/jordan/code/RL/scripts/refresh_phase2_packet.py`, `/home/jordan/code/fight-caves-RL/.github/workflows/phase2_native_linux_packet.yml`, `/home/jordan/code/fight-caves-RL/.github/workflows/phase2_native_linux_train_ceiling.yml`, `/home/jordan/code/fight-caves-RL/buildSrc/src/main/kotlin/shared.gradle.kts`
- What changed: Phase 2 began with a lower-copy subprocess transport prototype using `Pipe` as the control plane and file-backed `mmap` as the approved low-copy data path on the current host. Promotion-gate tooling and hosted native-Linux workflows were added. The workspace then added minimal-info payload mode, a learner-ceiling benchmark, trainer instrumentation, a benchmark-only core train runner, a synchronous prototype trainer, and finally a hosted prototype-packet rerun after fixing a branch-scoped packaged-distribution contract bug caused by unsanitized `GITHUB_REF_NAME`.
- Why: the project was chasing the long-term training throughput target and needed to know whether transport or trainer overhead was the true dominant blocker.
- What worked: the transport prototype improved raw transport throughput, sometimes materially. The hosted workflow hardening also exposed and fixed a real artifact-packaging bug. The later synchronous trainer prototype proved the project could move local production rows above older baselines.
- What did not work / future reference: the transport win did not survive the full end-to-end training gate. Native-Linux evidence showed the trainer path itself had become a dominant bottleneck after Phase 1. That is the key reason the workspace did not promote the transport prototype and instead pivoted deeper into trainer redesign.

## 2026-03-10
- Type: `plan-update`
- Paths affected: `/home/jordan/code/optimization_history.md`, `/home/jordan/code/RL/docs`, `/home/jordan/code/fight-caves-RL/history`, `/home/jordan/code/RSPS/history`
- What changed: historical documentation was consolidated and active-doc clutter was reduced. The root optimization history was added, module histories were moved under `history/` where appropriate, and active source-of-truth docs were simplified.
- Why: by this point the workspace had accumulated enough phases, gate reports, and exploratory notes that active docs were becoming hard to trust at a glance.
- What worked: it became much clearer which files were active specifications versus historical artifacts. This also made later Phase 7 replanning easier.
- What did not work / future reference: doc consolidation does not fix runtime drift on its own. It only reduces operator confusion and stale guidance.

## 2026-03-11
- Type: `validation`
- Paths affected: `/home/jordan/code/fight-caves-demo-lite/session-logs`, `/home/jordan/code/fight-caves-demo-lite/src/main/kotlin/DemoLiteSessionLogger.kt`, `/home/jordan/code/fight-caves-demo-lite/src/main/kotlin/DemoLiteStarterStateManifest.kt`, `/home/jordan/code/fight-caves-demo-lite/src/main/kotlin/DemoLiteValidationOverlay.kt`, `/home/jordan/code/fight-caves-demo-lite/src/test/kotlin/DemoLiteRuntimeLoggingValidationTest.kt`, `/home/jordan/code/fight-caves-demo-lite/src/test/kotlin/DemoLiteHeadedValidationEvidenceTest.kt`
- What changed: the frozen lite-demo path has real on-disk headed validation history from `2026-03-11`, not just tests and docs. Three preserved manual sessions under `/home/jordan/code/fight-caves-demo-lite/session-logs/` show repeated direct-boot/manual-restart validation with starter-state manifests emitted on every reset. The largest preserved session (`demo-lite-20260311T221220Z-0001`) recorded `4489` events, `1699` ticks, `7` episode starter-state manifests, `6` manual restarts, and session shutdown via `user_window_close`. The emitted manifests explicitly point back to `RL/RLspec.md#7.1.1 Episode-start state contract` and mark themselves as derived from the shared initializer path.
- Why: this was the historical proof pass for the old lite-demo validation/logging work before the project decided to freeze the module and move the primary headed path to RSPS plus stock `void-client`.
- What worked: the lite-demo path did provide real manual validation evidence, canonical starter-state artifact emission, structured session logging, and restart-loop observability. That history is worth preserving because it explains why the module remains useful as fallback/reference even after being frozen.
- What did not work / future reference: these artifacts also reinforce why the module was frozen rather than promoted. The lite-demo path proved useful for validation, but it remained a custom headed path rather than a near-in-game RSPS/OSRS presentation. That fidelity gap is exactly what the later RSPS-backed Phase 7 detour was intended to close.

## 2026-03-12
- Type: `implementation`
- Paths affected: `/home/jordan/code/RL/fight_caves_rl/envs_fast`, `/home/jordan/code/RL/fight_caves_rl/contracts`, `/home/jordan/code/RL/fight_caves_rl/replay/mechanics_parity.py`, `/home/jordan/code/RL/fight_caves_rl/policies/lstm.py`, `/home/jordan/code/RL/fight_caves_rl/policies/registry.py`, `/home/jordan/code/RL/configs/benchmark/fast_env_v2.yaml`, `/home/jordan/code/RL/configs/benchmark/fast_train_v2.yaml`, `/home/jordan/code/RL/configs/train/smoke_fast_v2.yaml`, `/home/jordan/code/RL/configs/train/train_fast_v2.yaml`, `/home/jordan/code/fight-caves-RL/game/src/main/kotlin/FastFightCavesKernelRuntime.kt`, `/home/jordan/code/fight-caves-RL/game/src/main/kotlin/headless`, `/home/jordan/code/fight-caves-RL/game/src/test/kotlin/headless/fast`, `/home/jordan/code/fight-caves-RL/game/src/main/kotlin/ParityHarness.kt`
- What changed: the current local workspace also contains an in-flight V2 fast-training implementation stream beyond what the repo-local changelogs already recorded. The RL worktree shows new fast-env modules, contract/trace surfaces, fast benchmark and train configs, policy-registry/LSTM additions, parity/mechanics-comparison helpers, and bridge/vector-env updates. The simulator worktree shows matching fast-runtime and parity-harness support plus cleanup of older root docs into archive/history areas.
- Why: this is the direct continuation of the earlier pivot plan for a V2 fast headless training path that targets much higher throughput than the correctness-first bridge stack can deliver.
- What worked: this entry makes the historical record honest about the current local workspace state instead of pretending the repo-local changelogs are the whole story.
- What did not work / future reference: this stream is still active local work rather than a closed historical batch. It should be read as “currently being pushed” rather than “fully accepted and stabilized.” Detailed ownership and acceptance still belong in the active pivot docs and in the eventual module-local changelog updates once the work is finalized.

## 2026-03-12
- Type: `decision`
- Paths affected: `/home/jordan/code/pivot_plan.md`, `/home/jordan/code/pivot_implementation_plan.md`, `/home/jordan/code/fight_caves_demo_rsps_spec.md`, `/home/jordan/code/rsps_fight_caves_trim_audit.md`, `/home/jordan/code/void_client_inventory.md`, `/home/jordan/code/rsps_void_client_integration_plan.md`
- What changed: the workspace made a major headed-demo decision. The old `fight-caves-demo-lite` continuation stopped being the primary Phase 7 path. The new headed strategy became an RSPS-backed Fight Caves-only demo built by trimming and reusing `RSPS` plus stock `void-client`, while the headless RL environment remained the training path and parity reference for the training contract. The current Phase 7/8 structure was rewritten around small PRs, explicit ownership, and a required trust gate before any replay/default-switchover assumptions.
- Why: the audit showed that the RSPS runtime already owns the real mechanics and server-side seams that matter, while `void-client` already owns the near-in-game UI/assets/rendering. Recreating a lightweight headed UI was the wrong primary investment for fidelity.
- What worked: the docs now have a clear source-of-truth split. The headed path, headless path, and RL orchestration path are no longer conceptually blurred.
- What did not work / future reference: this was a real detour. It intentionally delayed the older Phase 7/8 lite-demo continuation, and that delay should be considered part of the historical record rather than hidden.

## 2026-03-12
- Type: `freeze`
- Paths affected: `/home/jordan/code/fight-caves-demo-lite/README.md`, `/home/jordan/code/fight-caves-demo-lite/DEMOspec.md`, `/home/jordan/code/pivot_plan.md`, `/home/jordan/code/pivot_implementation_plan.md`
- What changed: `fight-caves-demo-lite` was formally frozen as fallback/reference only. The earlier lite-demo work, including old PR 7.1 validation, was preserved for historical reference, but old PR 7.2 replay/live-inference continuation was explicitly superseded.
- Why: the workspace needed to remove ambiguity before starting the RSPS-backed headed implementation program.
- What worked: docs and trackers now consistently treat lite-demo as historical fallback rather than the active target.
- What did not work / future reference: freezing the module does not remove it from the workspace, so future readers still need the docs to be explicit about its non-primary status. That is why the superseded wording had to be removed from active pivot docs as well.

## 2026-03-12
- Type: `implementation`
- Paths affected: `/home/jordan/code/RSPS/game/src/main/kotlin/Main.kt`, `/home/jordan/code/RSPS/game/src/main/kotlin/FightCavesDemoMain.kt`, `/home/jordan/code/RSPS/game/src/main/kotlin/GameProfiles.kt`, `/home/jordan/code/RSPS/game/src/main/kotlin/GameTick.kt`, `/home/jordan/code/RSPS/game/build.gradle.kts`, `/home/jordan/code/RSPS/game/src/main/resources/fight-caves-demo.properties`, `/home/jordan/code/RSPS/game/src/test/kotlin/FightCavesDemoSettingsTest.kt`
- What changed: PR 7A.2 added the RSPS demo-specific bootstrap/profile. The server can now start in a Fight Caves demo mode that layers demo properties on top of default settings, isolates saves/logs/errors under `data/fight_caves_demo`, disables generic character creation, sets `bots.count=0`, skips bot ticking, and marks demo-profile logins with server-side entry flags.
- Why: the project needed a first real RSPS-side trim that preserved login, JS5/file-server, and stock-client protocol assumptions without requiring a client fork.
- What worked: the demo server boot path stayed compatible with the stock client expectations and gave later PRs a clean post-login/server-owned seam for Fight Caves routing.
- What did not work / future reference: this slice deliberately did not yet enforce starter state or direct-to-cave landing. It created the runtime profile and the seam, not the full episode behavior.

## 2026-03-12
- Type: `implementation`
- Paths affected: `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveEpisodeInitializer.kt`, `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt`, `/home/jordan/code/RSPS/game/src/test/kotlin/content/area/karamja/tzhaar_city/FightCaveEpisodeInitializerTest.kt`, `/home/jordan/code/RSPS/docs/fight_caves_demo_preflight.md`, `/home/jordan/code/RSPS/README.md`, `/home/jordan/code/RSPS/data/cache`
- What changed: PR 7A.3 added the canonical Fight Caves episode initializer on the server side. It overwrites persisted-save drift, enforces the canonical starter state from `RLspec.md`, creates a fresh Fight Caves instance, seeds the wave rotation, boots the first wave immediately, and suppresses intro/Jad warning dialogues only for the demo/headless episode path. A cache preflight note was added because the behavioral tests require the RSPS cache at `RSPS/data/cache`.
- Why: starter-state enforcement is the core parity seam between headed demo behavior and the headless contract, so it had to be server-owned and authoritative.
- What worked: once the cache precondition was restored, the real `WorldTest` behavioral suite verified canonical starter state, persisted drift overwrite, fresh instance creation, seeded wave rotation, immediate wave boot, and demo-only warning suppression.
- What did not work / future reference: PR 7A.3 exposed a broader Fight Caves correctness regression outside its own acceptance scope. `TzhaarFightCaveTest > Complete all waves to earn fire cape()` still fails because generic NPC combat can incorrectly trigger Jad telegraph handling for non-Jad attacks. This remains a tracked `MUST REVISIT` item for later Phase 7 work.

## 2026-03-12
- Type: `implementation`
- Paths affected: `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt`, `/home/jordan/code/RSPS/game/src/test/kotlin/content/area/karamja/tzhaar_city/FightCaveEpisodeInitializerTest.kt`, `/home/jordan/code/pivot_implementation_plan.md`
- What changed: PR 7A.4 gated the runtime to Fight Caves-only without deep first-pass pruning. Demo-profile exit-object use, leave, death, completion, and area exit were all recycled back into a fresh Fight Caves episode. Broader-world escape was treated as a violation, logged, and reset rather than allowed.
- Why: the demo had to behave like a Fight Caves-only headed environment while still keeping the generic RSPS plumbing the stock client needs.
- What worked: targeted `WorldTest` coverage proved teleport-out, leave, death, and broader-world violation handling. The generic runtime dependencies remained alive, which kept the path conservative and low-risk.
- What did not work / future reference: this PR intentionally avoided aggressive deletion of data/cache/interfaces. The first pass is gating-first because broad pruning is risky and likely to cause hidden client/server breakage.

## 2026-03-12
- Type: `integration`
- Paths affected: `/home/jordan/code/RSPS/void-client`, `/home/jordan/code/RSPS/docs/fight_caves_demo_stock_client_runbook.md`, `/home/jordan/code/RSPS/docs/fight_caves_demo_stock_client_validation.md`, `/home/jordan/code/RSPS/docs/fight_caves_demo_login_differential_debug.md`, `/home/jordan/code/RSPS/engine/src/main/kotlin/world/gregs/voidps/engine/client/PlayerAccountLoader.kt`, `/home/jordan/code/RSPS/engine/src/main/kotlin/world/gregs/voidps/engine/GameLoop.kt`, `/home/jordan/code/RSPS/network/src/main/kotlin/world/gregs/voidps/network/login/PasswordManager.kt`, `/home/jordan/code/RSPS/engine/src/test/kotlin/world/gregs/voidps/engine/client/PlayerAccountLoaderTest.kt`, `/home/jordan/code/RSPS/engine/src/test/kotlin/world/gregs/voidps/engine/GameLoopStageExceptionTest.kt`
- What changed: PR 7A.5 cloned and audited `void-client`, standardized first-pass use of stock `client.jar`, documented the native-Windows client plus WSL/Linux server topology, and then debugged real stock-client login. A bounded differential pass compared the full normal RSPS path against the demo profile path and showed the `ACCOUNT_DISABLED(4)` result was a broader server-side first-login/account-persistence problem, not a demo-only one. The fix persisted newly created first-login accounts before queue wait and prevented unrelated stage exceptions from killing later queue-completion stages.
- Why: the new headed path had to prove stock-client compatibility before any client fork or client convenience work could be justified.
- What worked: the real stock client reached update sync, login, and finally in-game entry. Full RSPS profile login worked, Fight Caves demo-profile login worked, and the demo path landed in the real Fight Caves runtime with live gameframe/tab interaction. On this machine, the working transport path is the WSL IP fallback, not `127.0.0.1`.
- What did not work / future reference: `127.0.0.1` does not work for this Windows stock-client path on this machine and fails during JS5/update sync. The checked-out `void-client` source tree also does not compile cleanly under the workspace JDK 21 CLI path because it still references older/internal Java APIs. First pass should continue to prefer the stock release `client.jar`.

## 2026-03-12
- Type: `validation`
- Paths affected: `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveDemoObservability.kt`, `/home/jordan/code/RSPS/game/src/main/resources/fight-caves-demo.properties`, `/home/jordan/code/RSPS/game/src/main/kotlin/GameProfiles.kt`, `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt`, `/home/jordan/code/RSPS/game/src/test/kotlin/content/area/karamja/tzhaar_city/FightCaveEpisodeInitializerTest.kt`, `/home/jordan/code/RSPS/docs/fight_caves_demo_observability.md`, `/home/jordan/code/RSPS/README.md`, `/home/jordan/code/fight_caves_demo_rsps_spec.md`
- What changed: PR 7A.6 added headed observability for the RSPS-backed demo. The demo path now emits structured session logs, starter-state manifests, and per-session validation checklists. Entry, reset, leave, and world-access violation events are all recorded at the existing demo seams, and artifact locations are documented explicitly.
- Why: the headed path needs evidence that is stronger than UI inspection alone. The project needs to be able to prove starter-state correctness, debug gating failures, and compare headed behavior to the headless contract.
- What worked: every validation run now has a structured artifact surface. Tests cover session-log creation, starter-state manifest emission, checklist emission, and violation logging. The artifact root is explicit and documented.
- What did not work / future reference: this does not close the final trust gate by itself. Manual headed validation is still pending, and the broader non-Jad telegraph regression remains a `MUST REVISIT` item before PR 7A.7 can close.

## 2026-03-12
- Type: `plan-update`
- Paths affected: `/home/jordan/code/CHANGELOG.md`
- What changed: a root-level workspace changelog was added to preserve cross-repo project history in one place rather than forcing readers to reconstruct it from separate repo changelogs, git logs, and planning docs.
- Why: the workspace now spans multiple repos, an inherited upstream dependency, a major Phase 7 direction shift, and several current local RSPS demo slices that are easier to understand in one historical narrative.
- What worked: this file makes the project story legible from inception to current local state.
- What did not work / future reference: this file is intentionally historical and synthesized. Repo-local changelogs, specs, and implementation trackers remain the source of truth for detailed module ownership and future execution.

## 2026-03-12 to 2026-03-13
- Type: `bugfix`
- Paths affected: `/home/jordan/code/RSPS/game/src/main/kotlin/content/entity/npc/combat/Attack.kt`, `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/JadTelegraph.kt`, `/home/jordan/code/RSPS/game/src/test/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCaveTest.kt`, `/home/jordan/code/pivot_implementation_plan.md`
- What changed: PR `7A.6A` was inserted between observability and trust-gate closure to resolve the broader Fight Caves regression where non-Jad combat could incorrectly enter Jad telegraph state. The failing full-wave regression (`Complete all waves to earn fire cape`) was restored to passing.
- Why: manual trust validation was not credible while a known full-wave mechanics blocker remained open.
- What worked: non-Jad attacks no longer touch Jad-only telegraph state, and the broader Fight Caves regression suite returned to passing.
- What did not work / future reference: this was a targeted correctness pass only; it did not replace the manual headed trust gate and had to remain separate from PR `7A.7`.

## 2026-03-13 to 2026-03-14
- Type: `validation`
- Paths affected: `/home/jordan/code/RSPS/docs/fight_caves_demo_headed_trust_gate.md`, `/home/jordan/code/RSPS/docs/fight_caves_demo_stock_client_runbook.md`, `/home/jordan/code/pivot_implementation_plan.md`, `/home/jordan/code/RSPS/data/fight_caves_demo`
- What changed: PR `7A.7` went through reopen-and-close trust-gate cycles based on real manual headed sessions. Tracker optimism was corrected when live runs contradicted closure claims, then updated back to closed only after a fresh live rerun confirmed normal prayers, stable recycle-with-spawns, playable combat/prayer/inventory loop, and plausible wave progression.
- Why: trust-gate closure needed to be evidence-first and reversible, not “closed once and assumed stable.”
- What worked: the final live rerun closed the practical headed trust blockers and preserved machine-specific runbook truth (`WSL IP works`, `127.0.0.1` does not on this machine for stock-client bring-up).
- What did not work / future reference: manual trust validation is sensitive to environment drift and requires strict artifact logging discipline; tracker status must always follow latest observed evidence.

## 2026-03-14
- Type: `ops-setup`
- Paths affected: `/home/jordan/code/RSPS/scripts/run_fight_caves_demo_client.sh`, `/home/jordan/code/RSPS/docs/fight_caves_demo_stock_client_runbook.md`, `/home/jordan/code/RSPS/docs/fight_caves_demo_bootstrap_debug.md`, `/home/jordan/code/RSPS/README.md`
- What changed: PR `7B.1` and PR `7B.2` convenience/reliability work landed. The headed convenience path now bootstraps directly into Fight Caves with no manual login clicks, keeps session continuity through death/recycle, and shows an immediate dedicated loading window with an active progress bar before game entry.
- Why: post-trust-gate demo usability still had unacceptable friction (long opaque startup, login/setup interaction, recycle disconnect risk).
- What worked: stock-client demo startup became deterministic and operator-friendly: visible loading feedback, direct entry, and in-session recycle behavior.
- What did not work / future reference: startup wall-clock remains materially non-trivial; convenience improved the user-facing path but did not solve core runtime startup cost.

## 2026-03-14 to 2026-03-15
- Type: `integration`
- Paths affected: `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveBackendActionAdapter.kt`, `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveDemoBackendControl.kt`, `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveDemoObservability.kt`, `/home/jordan/code/RSPS/game/src/main/resources/fight-caves-demo.properties`, `/home/jordan/code/RL/scripts/run_headed_backend_smoke.py`, `/home/jordan/code/RL/scripts/run_headed_trace_replay.py`, `/home/jordan/code/RL/scripts/run_headed_checkpoint_inference.py`, `/home/jordan/code/pivot_implementation_plan.md`
- What changed: Phase 8 PR `8.1` completed in sequence: backend-control smoke, replay-first integration, then live checkpoint inference on the trusted RSPS-backed headed path. All slices reused the shared action schema boundary and emitted artifact-backed headed evidence (action processing logs, replay/live summaries, session logs).
- Why: the workspace needed proof that trained-policy control works through backend-issued actions, not only by UI clicking.
- What worked: the headed RSPS path accepted backend-issued movement/attack/consumable/prayer actions, replay traces drove headed behavior on the same boundary, and real checkpoint inference completed with evidence.
- What did not work / future reference: `8.1` deliberately did not claim default switching or broad stress/showcase coverage; those remained the `8.2` concern.

## 2026-03-15
- Type: `decision`
- Paths affected: `/home/jordan/code/RL/fight_caves_rl/puffer/factory.py`, `/home/jordan/code/RL/fight_caves_rl/puffer/trainer.py`, `/home/jordan/code/RL/docs/default_backend_selection.md`, `/home/jordan/code/RL/docs/eval_and_replay.md`, `/home/jordan/code/RL/docs/rl_integration_contract.md`, `/home/jordan/code/README.md`, `/home/jordan/code/pivot_implementation_plan.md`
- What changed: PR `8.2` completed the conditional default switch: training default stayed/was locked on `v2_fast`, and headed demo/replay default switched to the trusted RSPS-backed path. Explicit fallback selection remained preserved for `fight-caves-demo-lite` and V1 oracle/reference/debug paths.
- Why: defaults were updated only after `8.1` integration proof and trust evidence existed.
- What worked: docs/scripts/configs aligned on new defaults while preserving reversible fallback controls.
- What did not work / future reference: fallback paths are intentionally retained; default switch is not removal.

## 2026-03-15
- Type: `plan-update`
- Paths affected: `/home/jordan/code/pivot_plan.md`, `/home/jordan/code/pivot_implementation_plan.md`, `/home/jordan/code/pivot_closure_report.md`, `/home/jordan/code/post_pivot_plan.md`, `/home/jordan/code/post_pivot_implementation_plan.md`, `/home/jordan/code/system_acceptance_gates.md`
- What changed: pivot closure was finalized and a post-pivot roadmap replaced pivot execution as the active plan. New phase structure now starts at Phase 9 with explicit gates for benchmark truth, learning proof, transfer proof, hardening, then cleanup/refactor.
- Why: architecture convergence was complete; the next unknowns are performance truth and learning efficacy.
- What worked: planning moved from migration mechanics to measurable post-pivot proof gates with explicit dependencies and acceptance criteria.
- What did not work / future reference: post-pivot completion is now evidence-gated; cleanup/refactor remains intentionally deferred until training/transfer proof exists.

## 2026-03-16
- Type: `validation`
- Paths affected: `/home/jordan/code/RL/scripts/run_phase9_baseline_packet.py`, `/home/jordan/code/RL/docs/phase9_pr91_baseline.md`, `/home/jordan/code/post_pivot_implementation_plan.md`, `/home/jordan/code/system_acceptance_gates.md`, `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr91_baseline_20260316T180000Z`
- What changed: Phase 9 PR `9.1` baseline packet was executed and recorded using canonical methodology (`4,16,64` env counts with `3` repeats) on the WSL/Linux source-of-truth environment.
- Why: no performance or optimization claim should proceed without a frozen benchmark truth packet.
- What worked: baseline packet captured complete env/train rows with topology/context metadata and quantified the current gap (`25,092.86` env-only peak median vs `73.43` training peak median).
- What did not work / future reference: the measured gap to long-term targets is still large, confirming that PR `9.2` decomposition and later hardening are mandatory.

## 2026-03-16
- Type: `validation`
- Paths affected: `/home/jordan/code/RL/scripts/analyze_phase9_baseline_packet.py`, `/home/jordan/code/RL/docs/phase9_pr92_decomposition.md`, `/home/jordan/code/RL/docs/performance_decomposition_report.md`, `/home/jordan/code/post_pivot_implementation_plan.md`, `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr92_decomposition_20260316T182300Z.json`
- What changed: Phase 9 PR `9.2` ranked decomposition was completed from the PR `9.1` packet and supporting diagnostics. The dominant cost shares were identified (`evaluate_seconds`, `eval_policy_forward`, `eval_env_recv`) and documented as the current training-gate bottlenecks.
- Why: optimization sequencing needed evidence-backed ranking instead of speculative refactors.
- What worked: decomposition now separates runtime env hot path vs trainer/eval bottlenecks and makes the next optimization direction explicit.
- What did not work / future reference: `benchmark_train_ceiling.py` currently fails against `fast_train_v2` with an observation-shape mismatch (`64x536` vs `134x128`); this is now tracked as `MUST REVISIT` and should be fixed before later hardening rounds.
  - reproduced failure log: `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr92_train_ceiling_fast_train_v2_failure_20260316T183200Z.log`

## 2026-03-16
- Type: `validation`
- Paths affected: `/home/jordan/code/RL/docs/phase9_pr93_training_readiness.md`, `/home/jordan/code/RL/scripts/summarize_learning_eval.py`, `/home/jordan/code/post_pivot_implementation_plan.md`, `/home/jordan/code/system_acceptance_gates.md`, `/home/jordan/code/RL/artifacts/post_pivot/phase9_pr93_readiness_20260316T184000Z`
- What changed: Phase 9 PR `9.3` closed with a frozen readiness contract for default V2 training. The workspace now has an explicit training/eval artifact contract, fixed metric payload (`learning_eval_summary_v1`), and a concrete definition of "learning is happening" for the Phase 10 checkpoint ladder.
- Why: Phase 10 learning claims needed fixed metrics and mandatory artifacts before running canaries, not ad hoc run interpretation.
- What worked: a short train + replay-eval readiness run generated real manifests and artifacts, and the new learning-summary script now normalizes replay-eval outputs into a stable metric payload for ladder comparisons.
- What did not work / future reference: this does not prove learning yet. It freezes the measurement and artifact contract so learning proof can begin in Phase 10 with less ambiguity.

## 2026-03-16
- Type: `refactor`
- Paths affected: `/home/jordan/code/rsps`, `/home/jordan/code/demo-env`, `/home/jordan/code/training-env`, `/home/jordan/code/docs`, `/home/jordan/code/scripts`, `/tmp/runescape-rl-runtime`
- What changed: a full workspace prune/reorganization reset landed. Source root was reduced to source-owned directories (`rsps`, `demo-env`, `training-env`, docs/scripts), and generated runtime state was externalized to `/tmp/runescape-rl-runtime`. Legacy bloated root dirs (`.cache`, `.workspace-tools`, `.tmp`, `.gradle`, `.artifacts`, etc.) were removed from source root.
- Why: the workspace had become operationally unsafe and difficult to navigate due heavy generated bloat in source root.
- What worked: source root dropped from `51G` to `876M`; runtime root is isolated at `1.3G`; bounded smoke validation passed with logs under `/tmp/runescape-rl-runtime/reports/validation`.
- What did not work / future reference: `demo-env` is still a frozen fallback module resolved through the `training-env/env` composite build (not a fully independent Gradle build yet). Also, runtime-root default uses `/tmp` in this environment due writable-root constraints.

## 2026-03-16
- Type: `refactor`
- Paths affected: `/home/jordan/code/runescape-rl`, `/home/jordan/code/runescape-rl-runtime`, `/home/jordan/code/runescape-rl/scripts/workspace-env.sh`, `/home/jordan/code/runescape-rl/scripts/bootstrap.sh`, `/home/jordan/code/runescape-rl/training-env/rl/fight_caves_rl/utils/paths.py`
- What changed: follow-up path normalization moved the workspace to sibling roots under `/home/jordan/code/`: source root at `/home/jordan/code/runescape-rl` and runtime root at `/home/jordan/code/runescape-rl-runtime`. Script/runtime defaults were updated to use the sibling runtime root instead of `/tmp`.
- Why: enforce permanent root separation with predictable canonical paths.
- What worked: smoke validation still passed after the move, and runtime logs now write under `/home/jordan/code/runescape-rl-runtime/reports/validation`.
- What did not work / future reference: historical docs still contain legacy absolute paths by design; they remain historical references rather than active runbook defaults.

## 2026-03-16
- Type: `validation`
- Paths affected: `/home/jordan/code/runescape-rl/training-env/rl/scripts/profile_hotpath_attribution.py`, `/home/jordan/code/runescape-rl/training-env/rl/fight_caves_rl/envs/subprocess_vector_env.py`, `/home/jordan/code/runescape-rl/docs/perf_hotpath_attribution.md`, `/home/jordan/code/runescape-rl-runtime/reports/perf/hotpath_attribution_20260316T214936Z`, `/home/jordan/code/runescape-rl-runtime/reports/perf/hotpath_attribution_20260316T215137Z`
- What changed: post-flattening hot-path attribution was executed with stage/function-level timing across trainer, env worker, transport boundary, and worker skew. A profiling race/path issue was corrected in the attribution runner by avoiding unsafe mid-flight worker control resets and by draining the pending vecenv response before control-plane snapshot commands. The pass emitted machine-readable JSON/CSV artifacts plus a consolidated report with ranked hotspots and explicit root-cause answers.
- Why: PR 9 performance work required exact attribution of where wall-clock time is spent before optimization decisions.
- What worked: the measured default subprocess V2 path clearly attributed dominant cost to trainer-side policy forward/backward (`~93%` combined policy+update compute), with env core and transport serialization overhead as secondary/minor contributors. A bounded embedded comparison confirmed transport is not the primary blocker.
- What did not work / future reference: worker wait has occasional high outliers (startup/barrier spikes) that should be investigated after the primary trainer-compute hotspots. The packet is attribution-grade, not a final peak-SPS benchmark.
