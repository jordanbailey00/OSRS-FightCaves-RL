# System Architecture (Canonical)

This is the canonical architecture source of truth for the workspace.

## Canonical roots

- Source root: `/home/jordan/code/runescape-rl`
- Runtime root: `/home/jordan/code/runescape-rl-runtime`

Source root contains source-owned files only. Generated outputs, caches, checkpoints, logs, builds, and artifacts belong in runtime root.

## Top-level ownership boundaries

- `rsps/`
  - Active headed runtime owner.
  - Owns headed Fight Caves mechanics/runtime behavior and stock-client integration path.
  - Owns headed trust-gate behavior and headed observability path.
- `training-env/`
  - Headless training system owner.
  - `training-env/sim`: headless simulation runtime and mechanics surface for training.
  - `training-env/rl`: Python RL training/control/eval/replay orchestration.
- `demo-env/`
  - Frozen lite-demo fallback/reference module.
  - Not the active headed runtime owner.

## Headed vs headless split

- Headless (train path): `training-env/sim` + `training-env/rl`
- Headed (demo/replay path): `rsps` runtime + stock `void-client` surface

Training remains headless-first. Headed path is used for trusted demo/replay after training.

## Runtime/control flows (explicit)

## Flow A: Default train path (headless)

1. Entry: `training-env/rl/scripts/train.py` (default `v2_fast` path).
2. Control: RL trainer loop in `training-env/rl` issues batched actions.
3. Runtime: actions are applied in `training-env/sim` vecenv/sim runtime.
4. Return: observations/rewards/terminals are returned to RL trainer.
5. Output: checkpoints, manifests, and train artifacts are written to runtime root.

This path is the throughput-critical path and must not depend on headed RSPS runtime.

## Flow B: Replay/eval path (headless oracle/reference + selectors)

1. Entry: `training-env/rl/scripts/replay_eval.py` (or selector via `run_demo_backend.py` for mode selection).
2. Input: checkpoint + seed pack + replay/eval config.
3. Runtime: headless sim execution for deterministic evaluation packet generation.
4. Output: `eval_summary`, replay pack/index/manifests in runtime root.

This path is used for deterministic evaluation and replay evidence, not headed UI trust by itself.

## Flow C: Headed checkpoint/demo path (RSPS-backed)

1. Entry: `training-env/rl/scripts/run_headed_checkpoint_inference.py` (or selector `run_demo_backend.py` with headed backend).
2. Control boundary: backend-issued shared action schema (no manual UI clicking as control surface).
3. Runtime: RSPS demo profile executes headed Fight Caves runtime behavior.
4. Presentation: stock `void-client` renders headed UI/gameframe.
5. Output: headed session logs/manifests/checklists/validation artifacts in runtime root.

This path is the trusted headed demo/replay target after training and replay-first validation.

## Active defaults

- Training default backend: `v2_fast` (in `training-env/rl`)
- Demo/replay default backend: trusted RSPS-backed headed path
- Preserved fallbacks:
  - `demo-env`
  - V1 oracle/reference/debug flows in `training-env/rl`

## Flow-selection summary

- Use **Flow A** for training and performance work.
- Use **Flow B** for deterministic replay/eval and checkpoint ladder evidence.
- Use **Flow C** for headed trust demonstrations and train-to-headed transfer proof.

## Non-goals of this architecture

- No training hot path through full RSPS runtime.
- No duplication of game mechanics semantics in Python wrappers.
- No default reliance on frozen lite-demo module for headed trust claims.
