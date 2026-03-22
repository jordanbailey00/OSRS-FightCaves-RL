# demo-env ARCHITECTURE

## Internal structure

- `src/`: lite-demo code path
- `assets/`: source-owned assets used by fallback module
- `docs/`: legacy module notes and historical references

## Runtime role

- Optional fallback headed module only.
- Not part of the default headed runtime path.

## Relationship to sibling modules

- `rsps/` supersedes this module for active headed runtime ownership.
- `training-env/` remains the training/control owner.

## Design constraints

- Keep maintenance bounded to compatibility and clarity.
- Avoid expanding frozen module scope.
