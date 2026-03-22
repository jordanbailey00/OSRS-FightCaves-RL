# Runescape RL Workspace

This repository is the canonical source root for the Fight Caves RL system.

## What this project is

The workspace delivers:

- high-throughput **headless training** in `training-env/`
- trusted **headed demo/replay** in `rsps/`
- preserved **fallback headed reference** in `demo-env/`

Training and generated runtime outputs are intentionally separated:

- source root: `/home/jordan/code/runescape-rl`
- runtime root: `/home/jordan/code/runescape-rl-runtime`

## Top-level module map

- `rsps/`: active headed RSPS-backed demo/runtime owner
- `training-env/`: headless sim + RL trainer/eval/replay owner
- `demo-env/`: frozen lite-demo fallback/reference owner
- `docs/`: canonical project docs and non-canonical reports
- `scripts/`: root bootstrap/smoke helpers

## Canonical docs (read these first)

- current plan: `docs/active-reference/plan.md`
- current performance truth: `docs/active-reference/performance.md`
- current testing truth: `docs/active-reference/testing.md`
- rolling 14-day history: `docs/active-reference/changelog.md`
- architecture source of truth: `docs/source-of-truth/architecture.md`
- contract source of truth: `docs/source-of-truth/contracts.md`
- documentation routing map: `docs/source-of-truth/project_tree.md`

## Quick start

```bash
source /home/jordan/code/.workspace-env.sh
cd /home/jordan/code/runescape-rl
./scripts/bootstrap.sh
./scripts/validate_smoke.sh
```

## Documentation policy

Permanent truth belongs in canonical docs listed above.

One-off analysis, audits, and packets belong in `docs/reports/` and are not canonical by default.
