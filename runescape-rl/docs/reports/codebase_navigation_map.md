# Codebase Navigation Map

Date: 2026-03-16

This document answers: "Where does X live?" for `training-env/` and `demo-env/`.

## 1) Training-Env Responsibility Map

### Simulation runtime
- `training-env/sim/game/HeadlessMain.kt`
- `training-env/sim/game/FightCaveSimulationRuntime.kt`
- `training-env/sim/game/FastFightCavesKernelRuntime.kt`
- `training-env/sim/game/HeadlessModules.kt`

### Env state
- `training-env/sim/game/headless/fast/FastFightCavesState.kt`
- `training-env/sim/game/headless/fast/FastFightCavesShared.kt`
- `training-env/sim/game/headless/fast/FastFightCavesContracts.kt`

### Observation logic
- `training-env/sim/game/HeadlessObservationBuilder.kt`
- `training-env/sim/game/HeadlessFlatObservationBuilder.kt`
- `training-env/rl/fight_caves_rl/envs/observation_mapping.py`
- `training-env/rl/fight_caves_rl/envs/observation_views.py`
- `training-env/rl/fight_caves_rl/envs_fast/fast_policy_encoding.py`

### Reward logic
- `training-env/sim/game/headless/fast/FastRewardFeatures.kt`
- `training-env/rl/fight_caves_rl/rewards/registry.py`
- `training-env/rl/fight_caves_rl/rewards/reward_shaped_v0.py`
- `training-env/rl/fight_caves_rl/rewards/reward_sparse_v0.py`
- `training-env/rl/fight_caves_rl/envs_fast/fast_reward_adapter.py`

### Action translation
- `training-env/sim/game/HeadlessActionAdapter.kt`
- `training-env/sim/game/headless/fast/FastActionCodec.kt`
- `training-env/rl/fight_caves_rl/envs/action_mapping.py`
- `training-env/rl/fight_caves_rl/envs_fast/fast_policy_encoding.py`

### Episode/reset/start logic
- `training-env/sim/game/FightCaveEpisodeInitializer.kt`
- `training-env/sim/game/headless/fast/FastEpisodeInitializer.kt`
- `training-env/sim/game/content/area/karamja/tzhaar_city/TzhaarFightCave.kt`
- `training-env/sim/game/content/area/karamja/tzhaar_city/TzhaarFightCaveWaves.kt`

### Trainer integration
- `training-env/rl/fight_caves_rl/puffer/factory.py`
- `training-env/rl/fight_caves_rl/puffer/trainer.py`
- `training-env/rl/fight_caves_rl/puffer/production_trainer.py`
- `training-env/rl/scripts/train.py`

### Evaluation harness
- `training-env/rl/scripts/eval.py`
- `training-env/rl/scripts/replay_eval.py`
- `training-env/rl/fight_caves_rl/replay/eval_runner.py`
- `training-env/rl/fight_caves_rl/replay/replay_export.py`
- `training-env/rl/scripts/run_headed_trace_replay.py`
- `training-env/rl/scripts/run_headed_checkpoint_inference.py`

### Benchmark/smoke validation code
- `training-env/rl/scripts/benchmark_env.py`
- `training-env/rl/scripts/benchmark_train.py`
- `training-env/rl/scripts/run_vecenv_smoke.py`
- `training-env/rl/scripts/run_acceptance_gate.py`
- `training-env/rl/fight_caves_rl/tests/smoke/`
- `training-env/rl/fight_caves_rl/tests/performance/`
- `training-env/sim/game/test/headless/performance/`

### Artifact/log writing
- `training-env/rl/fight_caves_rl/manifests/run_manifest.py`
- `training-env/rl/fight_caves_rl/logging/artifact_naming.py`
- `training-env/rl/fight_caves_rl/logging/metrics.py`
- `training-env/rl/fight_caves_rl/headed_backend_control.py`
- Runtime root routing shell entrypoints:
  - `scripts/workspace-env.sh`
  - `scripts/bootstrap.sh`

### Config/defaults
- `training-env/rl/fight_caves_rl/defaults.py`
- `training-env/rl/fight_caves_rl/utils/config.py`
- `training-env/rl/configs/train/`
- `training-env/rl/configs/eval/`
- `training-env/rl/configs/benchmark/`
- `training-env/sim/config/headless_manifest.toml`
- `training-env/sim/config/headless_data_allowlist.toml`
- `training-env/sim/config/headless_scripts.txt`

## 2) Demo-Env Responsibility Map

### Launch/runtime code
- `demo-env/src/main/kotlin/DemoLiteMain.kt`
- `demo-env/src/main/kotlin/DemoLiteRuntime.kt`
- `demo-env/src/main/kotlin/DemoLiteFightCavesSession.kt`

### Headed client integration
- Not owned by `demo-env` now.
- Active headed integration lives in RSPS path:
  - `rsps/scripts/run_fight_caves_demo_client.sh`
  - `rsps/docs/fight_caves_demo_stock_client_runbook.md`

### Login/bootstrap logic
- Minimal local fallback behavior only:
  - `demo-env/src/main/kotlin/DemoLiteEpisodeInitializer.kt`
  - `demo-env/src/main/kotlin/DemoLiteResetController.kt`
- Real headed login/bootstrap is RSPS-owned (not demo-env-owned).

### Replay/demo logic
- Fallback session/event logging and loop behavior:
  - `demo-env/src/main/kotlin/DemoLiteSessionLogger.kt`
  - `demo-env/src/main/kotlin/DemoLiteSessionEvent.kt`
  - `demo-env/src/main/kotlin/DemoLiteWaveLogic.kt`
  - `demo-env/src/main/kotlin/DemoLiteCombatLoop.kt`

### Config/defaults
- `demo-env/build.gradle.kts`
- `demo-env/configs/README.md` (placeholder-level, not a rich config module)

### Smoke validation
- `demo-env/src/test/kotlin/DemoLiteRuntimeLoggingValidationTest.kt`
- `demo-env/src/test/kotlin/DemoLiteStarterStateContractTest.kt`
- `demo-env/src/test/kotlin/DemoLiteHeadedValidationEvidenceTest.kt`
- Root smoke entry:
  - `scripts/validate_smoke.sh` (runs `:fight-caves-demo-lite:tasks`)

## 3) Root Docs/Scripts Navigation

### Source-of-truth planning docs
- Pivot history and closure: `docs/pivot/`
- Post-pivot roadmap: `docs/pivot/post_pivot_plan.md`, `docs/pivot/post_pivot_implementation_plan.md`

### Runtime/layout docs
- `docs/runtime_root_spec.md`
- `docs/repo_layout.md`
- `docs/architecture.md`

### Workspace operations
- `scripts/workspace-env.sh`: canonical env + runtime-root routing
- `scripts/bootstrap.sh`: initializes env vars and runtime directories
- `scripts/validate_smoke.sh`: bounded cross-module smoke pass
