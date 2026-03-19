# Runtime Pruning (Step 10)

Step 10 introduces packaging and startup guards for a Fight Caves-only headless runtime while preserving headed/oracle paths for parity.

## Scope

This step does not physically delete repository files.
It enforces headless runtime pruning behavior and produces a deletion candidate inventory for the later hard-delete phase.

## Headless Runtime Pruning

`HeadlessMain.bootstrap` now enforces strict startup guards (`headless.pruning.strict=true` by default):

- Excluded tick stages must not be present:
  - `ConnectionQueue`
  - `SaveQueue`
  - `SaveLogs`
  - `BotManager`
  - `Hunting`
  - `GrandExchange`
  - `FloorItems`
- Excluded script namespaces must not be loaded:
  - `content.social.*`
  - `content.activity.*`
  - `content.minigame.*`
  - `content.quest.*` except `content.quest.Cutscene`
- Required pruned settings must be active:
  - `bots.count=0`
  - `events.shootingStars.enabled=false`
  - `events.penguinHideAndSeek.enabled=false`
  - `storage.autoSave.minutes=0`
  - `spawns.npcs=tzhaar_city.npc-spawns.toml`
  - `spawns.items=tzhaar_city.items.toml`
  - `spawns.objects=tzhaar_city.objs.toml`

These defaults are applied automatically by `loadRuntimeSettings` in headless mode and can be overridden only if strict pruning is disabled.

## Headless Packaging

New build tasks:

- `:game:headlessShadowJar`
  - Builds `fight-caves-headless.jar` with `HeadlessMain` as entrypoint.
- `:game:generateHeadlessDeletionCandidates`
  - Generates `history/deletion_candidates.md` from `config/headless_manifest.toml`.
- `:game:packageHeadless`
  - Runs deletion-candidate generation and creates `headlessDistZip`.

New distribution:

- `headlessDistZip` (`distributionBaseName=fight-caves-headless`)
  - includes `fight-caves-headless.jar`
  - includes `config/headless_data_allowlist.toml`, `config/headless_manifest.toml`, `config/headless_scripts.txt`
  - includes allowlisted `data/*.toml` files from headless data allowlist
  - includes `run-headless.sh` and `run-headless.bat`
  - includes empty `data/cache/` and `data/saves/` directories

## Verification Gates

Step 10 adds:

- `HeadlessPackageStartsWithoutExcludedSystemsTest`
- `HeadedModeStillPassesBaselineFightCaveTests`
- `HeadlessDeletionCandidateInventoryTest`

And requires build validation of headless artifact creation (`:game:packageHeadless`).
