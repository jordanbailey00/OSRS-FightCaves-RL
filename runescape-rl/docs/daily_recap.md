# Daily Recap — 2026-04-13

## What We Did Today

- Completed and analyzed `v25.5` (`sluy9lmm`).
- Confirmed the broad milestone ladder (`wave 60/61/62/63 + Jad kill`) changed behavior, but mostly improved generic late-game stability instead of true wave-63 / Jad frontier conversion.
- Renamed the narrowed redo from `v25.5.1` to `v25.5a`.
- Updated `run_history.md` with:
  - full `v25.5` results and analysis
  - planned `v25.5a` section with exact config and diff vs `v25.5`
- Updated live config/docs to `v25.5a`:
  - `shape_reach_wave_60_bonus = 0.0`
  - `shape_reach_wave_61_bonus = 0.0`
  - `shape_reach_wave_62_bonus = 0.0`
  - `shape_reach_wave_63_bonus = 100.0`
  - `shape_jad_kill_bonus = 500.0`
- Verified W&B tag for today’s completed run:
  - `sluy9lmm` tagged as `v25.5`

## Current State

- Live training recipe is `v25.5a`.
- `v25.5a` is identical to `v25.5` except the broad `wave 60-62` bonuses are removed and `wave 63` bonus is reduced.
- Warm-start for `v25.5a`:
  - `7qhjnxa2/0000001888485376.bin`
- Current priority roadmap:
  - `v25.5a`
  - `v26` cold-start on the `v25.4` / `v25.5a` recipe
  - `v26.1` warm-start from best `v26` checkpoint
- Lower-priority diagnostic still planned:
  - `v25.6`

## Plan For Tomorrow

- Finish and analyze `v25.5a`.
- If `v25.5a` improves wave-63 / Jad conversion, continue iterating on that branch.
- If `v25.5a` still does not improve the frontier, move to:
  - `v26` cold-start
  - then `v26.1` warm-start from the best `v26` checkpoint
