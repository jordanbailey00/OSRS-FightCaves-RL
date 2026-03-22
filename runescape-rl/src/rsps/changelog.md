# changelog.md

## 2026-03-10

- Added `history/` as the module history location for non-active RSPS historical documents.

## 2026-03-09

- Corrected the Jad telegraph source-of-truth doc so it reflects the current completed state rather than the pre-implementation starting point.
- Recorded the downstream optimization implication in `docs/jad_telegraph_plan.md`:
  - the new headless Jad cue is now part of the protected raw semantic contract and must remain semantically identical in any future flat training schema

- Updated the Jad telegraph source-of-truth document to close out the remaining mini-rework chunks:
  - `JAD-05` regression gate
  - `JAD-06` replay/demo outcome acceptance
- Mirrored the authoritative Jad telegraph outcome/timing state extensions into the headed RSPS code path:
  - `prayer_check_tick`
  - `sampled_protection_prayer`
  - `protected_at_prayer_check`
  - `resolved_damage`
  - retained last-attack telegraph timing for post-resolution trace inspection
- Preserved the same combat timing contract:
  - telegraph onset still anchors to headed animation start
  - prayer timing still remains at the existing hit-construction point
  - no oracle field or headless-only mechanic was introduced

- Updated the Jad telegraph source-of-truth document to reflect completed work:
  - `JAD-03` headless observation exposure
  - `JAD-04` replay/parity trace wiring
- The RSPS doc remains the planning/source-of-truth location for this mini-rework, while the actual headless observation and replay/parity implementation lives in `fight-caves-RL` and `RL`.

- Added the Jad telegraph mini-rework source-of-truth document in:
  - `docs/jad_telegraph_plan.md`
- Recorded the resolved parity assumptions for Jad telegraph implementation:
  - canonical onset tick is the current headed animation start tick
  - dialog/audio is not the timing anchor
  - prayer-check timing must remain unchanged
- Mirrored the first shared-code Jad telegraph changes from `fight-caves-RL` into the headed RSPS code path:
  - added `game/src/main/kotlin/content/area/karamja/tzhaar_city/JadTelegraph.kt`
  - updated `game/src/main/kotlin/content/entity/npc/combat/Attack.kt`
  - updated `game/src/main/kotlin/content/area/karamja/tzhaar_city/TzTokJad.kt`
- This step formalizes the authoritative Jad telegraph state in the headed code path without exposing any oracle field or changing combat timing.

## 2026-03-08

- Replaced the root RSPS placeholder docs with minimum viable source-of-truth documentation:
  - `RSPSspec.md`
  - `RSPSplan.md`
- Fixed the module role explicitly as:
  - headed/oracle reference
  - manual validation/debug path
  - parity dispute reference only
- Documented that `RSPS` remains outside the RL training hot path and is not the owner of headless or RL wrapper semantics.
- Recorded the current release-workflow stance as intentionally inherited for this repo during the current docs-alignment pass.

## 2026-03-07

- Added canonical RSPS root planning/spec placeholders:
  - `RSPSspec.md`
  - `RSPSplan.md`
- Added this root changelog file so RSPS decisions and completed work can be tracked with the same convention used by the other modules.
- No RSPS runtime or gameplay code changes were made in this step.
