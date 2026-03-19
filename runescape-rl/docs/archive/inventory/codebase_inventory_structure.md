# Codebase Inventory: Structure

Date: 2026-03-16  
Scope: `training-env/`, `demo-env/`, and root source-owned support dirs (`docs/`, `scripts/`)

This is a structural inventory for discoverability and ownership clarity, not a disk-size audit.

## 1) Source Tree Inventory

### 1.1 `training-env/` (current)

```text
training-env/
├── README.md
├── .gitignore
├── analysis/
│   └── README.md
├── docs/
│   └── README.md
├── fixtures/
│   └── README.md
├── sim/                                   (Kotlin/Gradle headless sim + inherited RSPS structure)
│   ├── settings.gradle.kts
│   ├── gradlew / gradlew.bat
│   ├── build-logic/ buildSrc/
│   ├── game/ engine/ cache/ config/ network/ types/ buffer/
│   │   └── src/main/kotlin + src/test/kotlin
│   ├── data/                               (large inherited world/content tree)
│   ├── scripts/
│   ├── docs/ + docs/archive/
│   ├── history/
│   ├── archive/
│   └── .git                                (nested VCS root)
└── rl/                                    (Python RL package + scripts/configs/docs)
    ├── pyproject.toml
    ├── uv.lock
    ├── configs/
    ├── scripts/
    ├── docs/ + docs/archive/
    ├── history/
    ├── resources/
    ├── fight_caves_rl/
    │   ├── bridge/ contracts/ envs/ envs_fast/ replay/ rewards/
    │   ├── puffer/ policies/ curriculum/ benchmarks/
    │   ├── logging/ manifests/ utils/
    │   └── tests/{unit,smoke,integration,parity,determinism,performance,train}
    └── .git                                (nested VCS root)
```

### 1.2 `demo-env/` (current)

```text
demo-env/
├── README.md
├── .gitignore
├── build.gradle.kts
├── assets/
├── configs/
│   └── README.md
├── docs/
│   └── DEMOspec.md
├── scripts/
│   └── README.md
├── tests/
│   └── README.md
├── src/
│   ├── main/kotlin/
│   │   ├── DemoLiteMain.kt
│   │   ├── DemoLiteRuntime.kt
│   │   ├── DemoLiteEpisodeInitializer.kt
│   │   ├── DemoLiteCombatLoop.kt
│   │   ├── DemoLiteWaveLogic.kt
│   │   ├── DemoLiteSessionLogger.kt
│   │   └── ...
│   └── test/kotlin/
│       ├── DemoLiteStarterStateContractTest.kt
│       ├── DemoLiteRuntimeLoggingValidationTest.kt
│       └── ...
└── .git                                    (nested VCS root)
```

### 1.3 Root support dirs (relevant)

```text
runescape-rl/
├── docs/
│   ├── pivot/...
│   ├── inventory/...
│   ├── architecture.md
│   ├── repo_layout.md
│   └── runtime_root_spec.md
└── scripts/
    ├── workspace-env.sh
    ├── bootstrap.sh
    └── validate_smoke.sh
```

## 2) Module Map (Intentional vs Accidental)

| Module | Path | Purpose | Intentional? | Key source roots | Key entrypoints |
|---|---|---|---|---|---|
| Training umbrella | `training-env/` | Aggregates sim + RL | Intentional | top-level symlinks/readmes | n/a |
| Headless sim composite | `training-env/sim/` | Kotlin runtime used by training and parity | Intentional, flattened for module-first navigation | `game/`, `game/test/`, `engine/`, `network/`, `types/`, `buffer/`, `cache/`, `config/`, `data/` | `HeadlessMain.kt`, `Main.kt`, `OracleMain.kt` |
| RL runtime | `training-env/rl/` | Python training/eval/replay/orchestration | Intentional | `fight_caves_rl/`, `scripts/`, `configs/` | `scripts/train.py`, `scripts/eval.py`, `scripts/replay_eval.py` |
| RL package | `training-env/rl/fight_caves_rl/` | Core Python package | Intentional | `envs*`, `bridge`, `puffer`, `rewards`, `replay` | `puffer/trainer.py`, `puffer/factory.py` |
| Demo fallback module | `demo-env/` | Frozen fallback/reference headed-lite module | Intentional (frozen) | `src/main/kotlin`, `src/test/kotlin` | `DemoLiteMain.kt` |
| Root scripts | `scripts/` | Workspace bootstrap + smoke checks | Intentional | `workspace-env.sh`, `bootstrap.sh`, `validate_smoke.sh` | shell entrypoints |
| Nested Git roots | _removed in cleanup pass_ | Historical standalone repos | Accidental in canonical mono-source layout | n/a | n/a |

## 3) Discoverability Notes (Quick)

- `training-env/sim/` now uses module-root source layouts for major modules (`game`, `engine`, `network`, `buffer`, `cache`, `types`, `config`).
- Module-first navigation is now the default shape: opening a module exposes Kotlin sources directly.
- `training-env/sim/data/` remains inherited and intentionally deferred for a dedicated data-scope cleanup pass.
