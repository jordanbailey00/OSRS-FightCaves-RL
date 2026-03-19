# training-env

Canonical owner of the headless training system.

## What this module owns

- `sim/`: headless simulation runtime/mechanics surface used by training.
- `rl/`: Python RL control plane (training, evaluation, replay, benchmarking, checkpoint orchestration).
- training-focused configs/scripts/tests and analysis helpers.

## What this module does not own

- Active headed runtime/demo ownership (owned by `rsps/`).
- Primary headed fallback module ownership (owned by `demo-env/`).

## Canonical docs for this module

- `training-env/SPEC.md`
- `training-env/ARCHITECTURE.md`
- `training-env/PARITY.md`

Supporting detailed docs may exist under submodule report folders, but these files above are canonical.

## Runtime/output routing

Generated outputs route to runtime root:

- `/home/jordan/code/runescape-rl-runtime`
