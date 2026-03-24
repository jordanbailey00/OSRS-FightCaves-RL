# Fight Caves RL

Reinforcement learning agent for Old School RuneScape Fight Caves.

## Architecture

- **training-env/** — Shared C backend: all Fight Caves mechanics, state, tick simulation. Also owns the headless batched training kernel and Python C extension.
- **demo-env/** — Raylib viewer shell: rendering, HUD, input, replay. Links against the same C backend — no separate gameplay logic.
- **RL/** — Python RL stack: PufferLib integration, policies, training scripts, evaluation, W&B logging.
- **docs/** — `plan.md` and `pr_plan.md` only.

## Build

```bash
# C backend + viewer
cmake -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build

# Python RL
cd RL && uv sync
```

## Key constraints

- Single gameplay/mechanics owner in C (training-env/fc_core)
- Headless training and viewer share the same backend
- Flat contiguous observation/action/reward/debug contracts
- Determinism and replay are first-class
