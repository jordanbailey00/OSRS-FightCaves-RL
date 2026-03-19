# Runtime Root Spec

## Canonical Runtime Root

- Path: `/home/jordan/code/runescape-rl-runtime`

This path is outside source root and writable in the current environment.

## Runtime Layout

```text
/home/jordan/code/runescape-rl-runtime/
├── artifacts/
│   ├── benchmark/
│   ├── training-readiness/
│   ├── learning-canaries/
│   └── replay-packets/
├── cache/
│   ├── python/
│   ├── pip/
│   ├── uv/
│   ├── gradle/
│   ├── model-cache/
│   └── general/
├── checkpoints/
│   ├── canary/
│   ├── promoted/
│   └── archived/
├── eval/
│   ├── summaries/
│   ├── packets/
│   └── comparisons/
├── logs/
│   ├── train/
│   ├── eval/
│   ├── demo/
│   └── scripts/
├── replays/
│   ├── headed/
│   ├── headless/
│   └── video/
├── tmp/
│   ├── run/
│   ├── build/
│   └── extraction/
├── builds/
│   ├── demo-env/
│   └── training-env/
├── scratch/
│   ├── local-analysis/
│   └── adhoc/
├── tool-state/
│   ├── workspace-tools/
│   ├── grade/
│   ├── agent/
│   └── python/
└── reports/
    ├── prune/
    └── validation/
```

## Environment Variables / Routing

Set by `scripts/workspace-env.sh` and `scripts/bootstrap.sh`:

- `FIGHT_CAVES_RUNTIME_ROOT=/home/jordan/code/runescape-rl-runtime`
- `JAVA_HOME=$FIGHT_CAVES_RUNTIME_ROOT/tool-state/workspace-tools/jdk-21`
- `GRADLE_USER_HOME=$FIGHT_CAVES_RUNTIME_ROOT/cache/gradle`
- `PIP_CACHE_DIR=$FIGHT_CAVES_RUNTIME_ROOT/cache/pip`
- `UV_CACHE_DIR=$FIGHT_CAVES_RUNTIME_ROOT/cache/uv`
- `XDG_CACHE_HOME=$FIGHT_CAVES_RUNTIME_ROOT/cache/general`
- `TMPDIR=$FIGHT_CAVES_RUNTIME_ROOT/tmp/run`
- `WANDB_DIR/WANDB_DATA_DIR/WANDB_CACHE_DIR` routed under `$FIGHT_CAVES_RUNTIME_ROOT/artifacts/training-env/rl`

## Path Update Summary

Training RL defaults were updated to runtime-root outputs for:

- train output dir (`fight_caves_rl/puffer/factory.py`)
- replay/eval output dir (`fight_caves_rl/replay/eval_runner.py`)
- headed backend smoke/replay/live inference script outputs
- benchmark/acceptance default output dirs
- bootstrap config artifact roots and default repo paths (`rsps`, `training-env/sim`)
