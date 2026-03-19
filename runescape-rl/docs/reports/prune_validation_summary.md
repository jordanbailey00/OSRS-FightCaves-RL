# Post-Prune Validation Summary

Date: 2026-03-16

## Before/After Size

- source root before: `51G`
- source root after: `876M`
- runtime root after: `2.6G`
- source-root reduction: `~50.1G`

## Final Root Checks

Verified source root now contains only:

- `rsps/`
- `demo-env/`
- `training-env/`
- `docs/`
- `scripts/`
- `README.md`
- `.gitignore`

Removed from source root:

- `.cache`, `.config`, `.gradle`, `.tmp`, `.tmp-build`, `.workspace-tools`, `.artifacts`, `archive`

## Smoke Validation

Executed:

- `/home/jordan/code/runescape-rl/scripts/validate_smoke.sh`

Results:

- demo-env module load through training-env composite build: pass
  - `/home/jordan/code/runescape-rl-runtime/reports/validation/demo-env.tasks.stdout.log`
- training-env env module load: pass
  - `/home/jordan/code/runescape-rl-runtime/reports/validation/training-env.sim.test.stdout.log`
- training-env rl backend selector dry-run: pass
  - `/home/jordan/code/runescape-rl-runtime/reports/validation/training-env.rl.backend_dry_run.stdout.log`

All smoke logs are under:

- `/home/jordan/code/runescape-rl-runtime/reports/validation/`

## Output Routing Verification

Validated runtime routing changes in RL path constructors/defaults:

- train outputs -> runtime root
- eval/replay outputs -> runtime root
- headed smoke/replay/live inference outputs -> runtime root
- acceptance/benchmark defaults -> runtime root
- bootstrap/env scripts route caches/temp/gradle to runtime root

## Remaining Caveats

- `demo-env` remains a frozen fallback module loaded via `training-env/sim` composite Gradle settings.
- A user-space JDK is now hosted at:
  - `/home/jordan/code/runescape-rl-runtime/tool-state/workspace-tools/jdk-21`
- `rsps/` internal build/cache/tooling directories were intentionally not pruned in this pass to honor the RSPS-protected-content constraint.
