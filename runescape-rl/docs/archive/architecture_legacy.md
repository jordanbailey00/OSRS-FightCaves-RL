# Workspace Architecture (Post-Prune Reset)

## Source Root vs Runtime Root

Source root (`/home/jordan/code/runescape-rl`) is source-only:

- `/home/jordan/code/runescape-rl/rsps`
- `/home/jordan/code/runescape-rl/demo-env`
- `/home/jordan/code/runescape-rl/training-env`
- `/home/jordan/code/runescape-rl/docs`
- `/home/jordan/code/runescape-rl/scripts`

Runtime root is externalized:

- `/home/jordan/code/runescape-rl-runtime`

Parent directory layout:

- `/home/jordan/code/runescape-rl`
- `/home/jordan/code/runescape-rl-runtime`

## Ownership Split

- `rsps/`: RSPS server/runtime + `void-client` source (protected internal content).
- `demo-env/`: frozen lite-demo fallback/reference source only.
- `training-env/sim/`: headless simulator/runtime source (from `fight-caves-RL`).
- `training-env/rl/`: RL/training/control/eval/replay source (from `RL`).

Headed ownership note:

- active headed client/demo integration is RSPS-owned (`rsps/`)
- `demo-env/` is fallback/reference only

## Runtime Routing

Generated/regenerable outputs are routed to runtime root:

- caches: `/home/jordan/code/runescape-rl-runtime/cache`
- tool state: `/home/jordan/code/runescape-rl-runtime/tool-state`
- builds: `/home/jordan/code/runescape-rl-runtime/builds`
- logs: `/home/jordan/code/runescape-rl-runtime/logs`
- checkpoints/artifacts/eval/replays: `/home/jordan/code/runescape-rl-runtime/{checkpoints,artifacts,eval,replays}`

## Guardrail Policy

- No root-level hidden operational dirs in source root.
- No training/eval outputs under `training-env/`.
- No demo logs/replays/build outputs under `demo-env/`.
- Runtime state must be relocatable via `FIGHT_CAVES_RUNTIME_ROOT`.
