# demo-env

Frozen lite-demo fallback/reference module.

## What this module owns

- historical lite-demo headed fallback behavior and references
- compatibility surface needed to keep fallback path runnable

## What this module does not own

- active headed runtime/demo ownership (owned by `rsps/`)
- primary train-to-headed proof path

## Canonical docs for this module

- `demo-env/SPEC.md`
- `demo-env/ARCHITECTURE.md`
- `demo-env/PARITY.md`

Legacy module docs under `demo-env/docs/` are report/historical context and are not the active workspace source of truth.

## Runtime/output routing

Generated outputs must remain outside source root:

- `/home/jordan/code/runescape-rl-runtime`
