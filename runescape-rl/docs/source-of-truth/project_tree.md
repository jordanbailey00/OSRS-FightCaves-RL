# Documentation Routing and Project Tree (Canonical)

This file defines where documentation truth lives and how docs are routed.

## Source tree (high level)

```text
/home/jordan/code/runescape-rl/
├── README.md
├── AGENTS.md
├── rsps/
├── demo-env/
├── training-env/
├── docs/
│   ├── active-reference/
│   ├── source-of-truth/
│   ├── reports/
│   └── archive/
└── scripts/
```

## Canonical root docs and ownership

- `README.md`: workspace entrypoint and navigation.
- `AGENTS.md`: agent behavior/workflow and documentation rules.
- `docs/active-reference/plan.md`: current execution plan and blockers.
- `docs/active-reference/performance.md`: latest benchmark and profiling truth.
- `docs/active-reference/changelog.md`: rolling 14-day change history.
- `docs/active-reference/testing.md`: current testing truth and gates.
- `docs/source-of-truth/architecture.md`: canonical architecture boundaries.
- `docs/source-of-truth/contracts.md`: cross-module contracts/invariants.
- `docs/source-of-truth/project_tree.md`: this routing map.

## Canonical module docs and ownership

For each major module:

- `README.md`: module entrypoint and ownership summary.
- `SPEC.md`: required behaviors/invariants/inputs/outputs.
- `ARCHITECTURE.md`: internal structure and runtime flows.
- `PARITY.md`: parity and compatibility guarantees/risks.

Required modules:

- `rsps/`
- `demo-env/`
- `training-env/`

## Reports vs canonical docs

- `docs/reports/`: temporary or one-off outputs (audits, inventories, packets, writeups).
- Reports are not source-of-truth by default.
- If a report changes durable project truth, update canonical docs directly.

## Archive policy

- `docs/archive/` contains superseded/historical material.
- Archived docs are never the active source of truth.
- Legacy docs may remain for traceability but must be treated as non-canonical.

## Update policy

- Do not create new permanent plan/performance/architecture docs outside canonical locations.
- Update canonical docs in place when truth changes.
- Keep authority singular: one active plan, one active performance reference, one active testing reference, one active architecture/contracts reference.
