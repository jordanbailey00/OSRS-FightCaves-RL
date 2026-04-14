# Daily Recap — 2026-04-13

## What We Did Today

- Completed and analyzed `v25.5` (`sluy9lmm`).
- Completed and analyzed `v25.5a` (`wiee1ezs`).
- Confirmed the broad milestone ladder (`wave 60/61/62/63 + Jad kill`) changed
  behavior, but mostly improved generic late-game stability instead of true
  wave-63 / Jad frontier conversion.
- Confirmed the narrowed `v25.5a` ladder (`wave 63 + Jad kill` only) did not
  change behavior in any observable way versus `v25.5`.
- Updated `run_history.md` with:
  - full `v25.5` results and analysis
  - full `v25.5a` results and analysis
  - refreshed roadmap notes for `v26`, `v26.1`, and `v25.6`
- Re-checked the strongest `v25.4` frontier windows from fuller W&B history
  and confirmed the best practical checkpoint candidate is:
  - `7qhjnxa2/0000000368050176.bin`
- Synced W&B tags through the full late-`v25` line:
  - `v25`
  - `v25.1`
  - `v25.2`
  - `v25.3`
  - `v25.4`
  - `v25.5`
  - `v25.5a`

## Current State

- Live training recipe is `v25.5a`.
- `v25.5a` is identical to `v25.5` except the broad `wave 60-62` bonuses are
  removed and the `wave 63` bonus is reduced.
- Warm-start for `v25.5a`:
  - `7qhjnxa2/0000001888485376.bin`
- Best modern checkpoint source identified today:
  - `7qhjnxa2/0000000368050176.bin`
- Current priority roadmap:
  - `v26` cold-start on the `v25.4` / `v25.5a` recipe
  - `v26.1` warm-start from best `v26` checkpoint
- Lower-priority diagnostic still planned:
  - `v25.6`

## Plan For Tomorrow

- Stop iterating on milestone-ladder variants for now.
- Launch `v26` as a cold-start on the `v25.4` / `v25.5a` recipe.
- If `v26` produces a good frontier checkpoint, launch `v26.1` warm-start from
  that checkpoint.
- Keep `v25.6` as a lower-priority checkpoint diagnostic only if `v26` does
  not clarify the stale-checkpoint question.
