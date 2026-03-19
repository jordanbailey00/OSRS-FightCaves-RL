# Source Repo Layout

## Final Source Root

```text
/home/jordan/code/runescape-rl/
├── rsps/
├── demo-env/
├── training-env/
├── docs/
├── scripts/
├── README.md
└── .gitignore
```

Workspace parent layout:

```text
/home/jordan/code/
├── runescape-rl/
└── runescape-rl-runtime/
```

## Directory Purpose

- `rsps/`
  - Protected RSPS server/runtime and client source tree.
  - Internal contents intentionally left functionally unchanged.

- `demo-env/`
  - Renamed `fight-caves-demo-lite`.
  - Frozen fallback/reference source only.
  - Runtime outputs removed.

- `training-env/`
  - Consolidated training source root.
  - `sim/` from `fight-caves-RL`.
  - `rl/` from `RL`.
  - No top-level symlink facade for `configs/scripts/tests`; navigate directly to owning locations.
  - `analysis/`, `fixtures/`, and `docs/` added for source-only organization.

- `docs/`
  - Root source-of-truth planning/layout/runtime docs and prune reports.

- `scripts/`
  - Root bootstrap/runtime env script.
  - Root smoke-validation script.

## Notes

- Runtime material is externalized to `/home/jordan/code/runescape-rl-runtime`.
- Historical pivot/post-pivot docs were moved under `docs/pivot/`.
- Inventory snapshots moved under `docs/inventory/`.
