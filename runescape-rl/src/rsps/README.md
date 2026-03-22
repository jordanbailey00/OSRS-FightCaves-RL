# rsps

Canonical owner of the active headed Fight Caves demo/runtime path.

## What this module owns

- RSPS runtime behavior for headed Fight Caves demo flow.
- Stock-client headed integration path and runbooks.
- Headed trust-gate validation and headed observability artifacts.
- Server-side starter-state/reset enforcement on the headed path.

## What this module does not own

- Headless RL training loop ownership (owned by `training-env`).
- Primary fallback headed ownership (owned by `demo-env`).
- PufferLib training orchestration (owned by `training-env/rl`).

## Canonical docs for this module

- `rsps/SPEC.md`
- `rsps/ARCHITECTURE.md`
- `rsps/PARITY.md`

Supporting runbooks and validation notes under `rsps/docs/` are treated as module reports/reference docs.

## Common commands

Server bring-up:

```bash
cd /home/jordan/code/runescape-rl/rsps
./gradlew --no-daemon :game:runFightCavesDemo
```

Convenience client launcher:

```bash
cd /home/jordan/code/runescape-rl/rsps
./scripts/run_fight_caves_demo_client.sh
```

## Prerequisite note

WorldTest-based RSPS game tests require cache at:

- `/home/jordan/code/runescape-rl/rsps/data/cache/main_file_cache.dat2`
