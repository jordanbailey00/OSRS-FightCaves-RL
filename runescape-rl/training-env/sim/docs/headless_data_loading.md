# Headless Data Loading (Step 3)

## Purpose

Headless runtime data loading is now explicit and allowlist-driven.
No `dirs.txt` directory sweep is used on the headless bootstrap path.

## Source of Truth

- `config/headless_data_allowlist.toml`

The allowlist defines:
- required data files
- optional data files
- required source map regions for collision/object decode
- required settings keys that must resolve to allowlisted files

## Runtime Path

Headless bootstrap now uses:
1. `loadHeadlessDataAllowlist()`
2. `loadHeadlessConfigFiles()`
3. `preloadRuntime(...)`

`loadHeadlessConfigFiles()` enforces:
- fail-fast on missing required allowlisted file
- fail-fast if required settings keys do not resolve to allowlisted `.toml` files
- setting `headless.map.regions` from allowlist region scope

## Collision/Map Scope

`MapDefinitions` now respects restricted regions in headless mode:
- if `runtime.mode=headless` and `headless.map.regions` is set, map/collision decode is limited to those region ids
- current allowlist sets required source region to Fight Caves source region `9551`

## Tests (Step 3)

- `HeadlessLoadsFightCaveDataOnlyTest`
- `HeadlessFailsOnMissingFightCaveWaveDataTest`
- `HeadlessCollisionRegionSubsetTest`

These validate that:
- only allowlisted files are loaded
- missing Fight Caves wave data fails startup with clear error
- loaded collision data stays within the allowlisted region subset

## Non-Closure Eager Loads

To keep headless startup aligned with Fight Caves closure, unrelated eager definition loads are gated off in headless mode:
- `DiangoCodeDefinitions` is loaded only in headed/oracle mode.

## Test Isolation Note

Headless Step 3 tests run with isolated state-file/cache paths (`../temp/data/headless-test-cache/`) so they do not mutate headed test cache metadata in `../data/.temp/`.