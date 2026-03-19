# Codebase Restructure Recommendations

Date: 2026-03-16  
Scope: inventory-driven recommendations only. No structure moves applied in this document.

## 1) Structural Problem List

| Path | Problem | Why it hurts discoverability | Recommended action |
|---|---|---|---|
| `training-env/env/.git` | Nested VCS root | Makes ownership ambiguous in a unified source root; tools may treat it as a separate project unexpectedly | **Delete nested git metadata** after archival check |
| `training-env/rl/.git` | Nested VCS root | Same issue; obscures true module boundary under `training-env/` | **Delete nested git metadata** after archival check |
| `demo-env/.git` | Nested VCS root | Same issue; hides fallback module status and complicates repo-wide operations | **Delete nested git metadata** after archival check |
| `training-env/env/` | Name is too generic | "env" does not communicate "headless sim runtime"; hard for new operators | **Rename** to `training-env/sim/` |
| `training-env/rl/` | Name is too generic | "rl" hides scope (trainer, replay, benchmark, policy tooling) | **Rename** to `training-env/trainer/` |
| `training-env/configs -> rl/configs` | Symlink indirection | Path ownership is hidden; readers cannot tell where real config files live | **Replace symlink** with explicit directory + import/index docs or move configs to true top-level shared location |
| `training-env/scripts -> rl/scripts` | Symlink indirection | Same ownership ambiguity; scripts look top-level but are trainer-owned | **Replace symlink** with explicit wrappers or move scripts intentionally |
| `training-env/tests -> rl/fight_caves_rl/tests` | Symlink indirection | "tests/" suggests umbrella tests, but actually points only to trainer tests | **Split** into `training-env/tests/sim` and `training-env/tests/trainer` (or keep explicit subpaths only) |
| `training-env/env/data/` | Huge inherited world/content tree | Training-focused ownership is hard to locate because Fight Caves closure is buried in broad RSPS data | **Keep short-term**, then **split** Fight Caves closure into explicit `sim/data_fight_caves/` with generated allowlist manifest |
| `training-env/env/archive`, `training-env/env/history`, `training-env/rl/history`, `.../docs/archive` | Historical docs mixed into active module roots | Active code navigation is noisy and misleading | **Move/merge** under root `docs/archive/` with pointers |
| `demo-env/configs`, `demo-env/scripts`, `demo-env/tests` (mostly placeholders) | Low-signal directory scaffolding | Adds navigational hops without meaningful content | **Keep or delete** based on future use; if retained, add concrete ownership notes and sample files |
| `training-env/rl/fight_caves_rl/__pycache__`, `training-env/rl/scripts/__pycache__` | Generated Python cache in source tree | Obscures source inventory and causes noisy searches | **Delete** and enforce ignore |
| `training-env/README.md` | Runtime path text drift (`/tmp/runescape-rl-runtime`) | Misleads operators after path normalization | **Update** to `/home/jordan/code/runescape-rl-runtime` |
| `training-env/env/settings.gradle.kts` includes `:fight-caves-demo-lite` from `../../demo-env` | Cross-tree coupling with frozen fallback module | Makes module graph non-obvious; trainer sim build unexpectedly includes frozen fallback | **Keep short-term**, then **split** fallback module out of default sim composite build path |

## 2) Target Architecture Proposal

Goal: keep build-friendly conventions, reduce accidental layering, and make ownership obvious.

### 2.1 Proposed `training-env/` target tree

```text
training-env/
├── README.md
├── docs/
│   ├── module_map.md
│   └── contracts/
├── sim/                                 # renamed from env/
│   ├── README.md
│   ├── settings.gradle.kts
│   ├── modules/
│   │   ├── game/
│   │   ├── engine/
│   │   ├── cache/
│   │   ├── network/
│   │   ├── types/
│   │   ├── config/
│   │   └── buffer/
│   ├── config/
│   ├── data_fight_caves/               # explicit closure target (later split)
│   ├── scripts/
│   └── tests/
├── trainer/                             # renamed from rl/
│   ├── README.md
│   ├── pyproject.toml
│   ├── configs/
│   ├── scripts/
│   ├── src/fight_caves_rl/             # optional package-root normalization
│   ├── tests/
│   └── docs/
├── fixtures/
└── analysis/
```

Notes:
- Keep `src/main/kotlin` and `src/test/kotlin` inside Gradle modules.
- Keep Python package structure stable during rename; package move to `src/` can be deferred if risk is high.

### 2.2 Proposed `demo-env/` target tree

```text
demo-env/
├── README.md
├── build.gradle.kts
├── src/
│   ├── main/kotlin/
│   └── test/kotlin/
├── docs/
│   ├── module_status.md
│   └── fallback_usage.md
├── assets/
└── configs/                            # only if real config exists; otherwise remove
```

Notes:
- `demo-env` remains frozen fallback/reference; avoid adding new integration ownership here.
- If `scripts/` and `tests/` stay placeholder-only, remove and document from README instead.

## 3) Migration Plan (Path-by-Path)

| Current path | Target path | Reason | Likely updates needed | Risk |
|---|---|---|---|---|
| `training-env/env` | `training-env/sim` | Improve ownership discoverability | all docs/runbooks, Gradle paths, script env vars, CI paths | Medium |
| `training-env/rl` | `training-env/trainer` | Make trainer ownership explicit | Python scripts, docs, shell scripts, imports in tooling wrappers | Medium |
| `training-env/configs` symlink | `training-env/configs/` real dir (or remove) | Remove hidden ownership | README, script references, docs | Low |
| `training-env/scripts` symlink | `training-env/scripts/` real wrappers | Remove hidden ownership and clarify call sites | runner docs, path assumptions in automation | Low |
| `training-env/tests` symlink | explicit test dirs or remove | Avoid false "umbrella tests" signal | docs, smoke scripts | Low |
| `training-env/env/data` Fight Caves closure | `training-env/sim/data_fight_caves` | Reduce non-domain noise for training runtime | headless manifests, allowlists, loaders, tests | High |
| `training-env/env/archive`, `training-env/env/history`, `training-env/rl/history`, `*/docs/archive` | `docs/archive/{sim,trainer}` | Separate historical material from active modules | cross-links in READMEs/docs | Low |
| `demo-env/scripts` placeholder | remove or fill with real wrappers | Avoid no-op scaffolding | demo-env README, smoke script references | Low |
| `demo-env/tests` placeholder dir | remove (tests remain under `src/test/kotlin`) | Reduce duplicated test locations | README | Low |
| nested `.git` dirs in submodules | remove nested metadata | Restore mono-source ownership clarity | none if subtree history not needed locally | Medium |
| `training-env/env/settings.gradle.kts` cross-include of `demo-env` | isolate fallback include behind optional profile | decouple active sim graph from frozen fallback module | Gradle settings, smoke script task names | Medium |
| `training-env/README.md` runtime path | corrected runtime root path | avoid operator confusion | README only | Low |

## 4) Recommended Execution Order

1. Documentation-first renames and boundary labels (`env -> sim`, `rl -> trainer`) in docs only.  
2. Remove discoverability hazards with low risk:
   - stale `__pycache__`
   - placeholder directory cleanup
   - runtime path text drift.
3. Replace symlink indirection with explicit directories/wrappers.
4. Rename directories (`env`, `rl`) and patch scripts/docs/CI.
5. Decouple `demo-env` from default composite build path.
6. Higher-risk content boundary split (`data` closure extraction) with dedicated validation PR.

## 5) Legitimate vs Pointless Nesting Summary

Legitimate:
- Gradle/Kotlin source sets under `src/main/kotlin` and `src/test/kotlin`.
- Python package subdivision in `fight_caves_rl/*` by domain.

Pointless or currently harmful:
- Nested `.git` repositories under canonical source root.
- Symlinked top-level `configs/scripts/tests` that hide true ownership.
- Placeholder directories that imply active ownership but contain only README stubs.
- Historical `archive/history` material mixed into active module roots.
