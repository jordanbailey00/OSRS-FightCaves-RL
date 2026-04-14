# Refactor Planning

This is the working plan for the isolated refactor worktree. The goal is to
shrink stale RL surface area without destabilizing the current training path.

Scope:
- config keys
- reward/shaping surface
- policy-adjacent contracts
- analytics/logging clutter

Non-goals:
- changing the active learning recipe in the main repo
- speculative architecture work before the dead surface is removed

## Audit Summary

The current live training setup is not bloated because of the active reward
recipe. Most of the clutter is coming from:

- dead compatibility config keys that are still parsed
- legacy reward-feature slots that are still emitted
- staging-era analytics that are still logged
- long-unused curriculum/debug knobs that still widen the surface area
- viewer-only route heads that are not part of the RL policy

Important constraint:
- the current `106`-float policy observation contract is mostly live
- there are no obvious dead policy-observation channels worth removing first
- the safest cleanup is around dead config keys and stale analytics, not core obs

## Prune Candidates

### Phase 1: Safe Immediate Prune

These are the best first deletions. They add clutter today and do not drive the
current reward path.

#### 1. Dead parsed reward-weight config keys

Candidate removal:
- `w_food_used`
- `w_food_used_well`
- `w_prayer_pot_used`
- `w_correct_jad_prayer`
- `w_wrong_jad_prayer`

Why they are pruneable:
- they are still parsed into `FightCaves` and documented in `rl_config.md`
- the shared reward path in `fc_reward.h` does not read any of them
- the live training recipe has not used them for a long time

What to update when removing:
- `runescape-rl/config/fight_caves.ini`
- `pufferlib_4/config/fight_caves.ini`
- `training-env/binding.c`
- `training-env/fight_caves.h`
- `docs/rl_config.md`

Expected impact:
- no intended behavior change if the removal is done correctly
- smaller config surface
- less confusion in run history and config audits

#### 2. Stale staging-era analytics

Candidate removal:
- `reached_wave_30`
- `cleared_wave_30`
- `reached_wave_31`

Why they are pruneable:
- they were useful during the wave-30/31 plateau era
- they are now weaker than `wave_reached`, `max_wave`, `reached_wave_63`, and `cave_complete_rate`
- they add logging clutter in W&B, viewer summaries, and docs
- run history already notes that they became trivial or misleading once later-wave progress existed

What to update when removing:
- `training-env/fight_caves.h`
- `training-env/binding.c`
- `fc-core/include/fc_types.h`
- `fc-core/src/fc_tick.c`
- `demo-env/src/viewer.c`
- `demo-env/src/fc_debug_overlay.h`
- `docs/rl_config.md`

Expected impact:
- no policy change
- cleaner analytics surface
- easier run comparison

### Phase 2: Good Prune Targets, But Contract-Touching

These are worth removing, but they require more care because they affect
internal buffer contracts, debug tools, or viewer paths.

#### 3. Legacy Jad reward-feature slots

Candidate removal:
- `FC_RWD_CORRECT_JAD_PRAY`
- `FC_RWD_WRONG_JAD_PRAY`
- `state->correct_jad_prayer`
- `state->wrong_jad_prayer`

Why they are pruneable:
- the live reward path ignores these features
- they only remain as a historical compatibility layer
- current training relies on generic resolved-hit prayer reward instead

Why this is not a phase-1 delete:
- they still occupy internal reward-feature slots
- removing them changes:
  - `FC_REWARD_FEATURES`
  - `FC_TOTAL_OBS`
  - full backend buffer size
- tests, docs, viewer reward tabs, and any Python-side shape assumptions must be updated together

Suggested direction:
- remove both together
- shrink the reward-feature contract in one explicit refactor step

#### 4. Movement / idle reward plumbing

Candidate removal:
- `w_movement`
- `w_idle`
- `FC_RWD_MOVEMENT`
- `FC_RWD_IDLE`
- associated reward breakdown fields

Why they are candidates:
- both weights have sat at `0.0` for a long time
- the team has not been steering behavior through literal movement/idle reward
- the real anti-stall logic is `shape_not_attacking_*`, `shape_wave_stall_*`, and `w_tick_penalty`

Why this is not phase 1:
- these are live, not dead
- removing them changes the reward-feature contract and some debug output
- they are still available for future ablations if desired

Current recommendation:
- do not remove first
- only prune after the dead config keys and stale analytics are gone

#### 5. Viewer-only route heads

Candidate removal:
- `MOVE_TARGET_X`
- `MOVE_TARGET_Y`
- their full-mask slices

Why they are candidates:
- they are not part of the 5-head RL policy
- they exist mainly to support viewer click-to-move / BFS route input
- they inflate the full backend action-mask contract from the RL-facing `36` to `166`

Why this is not phase 1:
- they are useful for human debugging in the viewer
- removing them is a viewer/input design decision, not just stale-config cleanup

Current recommendation:
- keep for now unless the viewer input model is explicitly being redesigned

### Phase 3: Simplification Candidates

These are not dead, but they are larger-surface features that may not justify
their maintenance cost anymore.

#### 6. Multi-bucket curriculum

Candidate simplification:
- reduce from three buckets to either:
  - one bucket: `curriculum_wave`, `curriculum_pct`
  - or no curriculum surface at all

Current knobs:
- `curriculum_wave`
- `curriculum_pct`
- `curriculum_wave_2`
- `curriculum_pct_2`
- `curriculum_wave_3`
- `curriculum_pct_3`

Why they are candidates:
- curriculum has been off for a long time
- the second and third buckets add config, parser, and runtime complexity
- if curriculum ever returns, one bucket may be enough

Current recommendation:
- keep until the team decides whether curriculum experiments are dead for good
- if curriculum is considered abandoned, remove all six together
- if curriculum is still wanted, collapse to one bucket only

#### 7. Debug / ablation-only movement disable

Candidate removal:
- `disable_movement`

Why it is a candidate:
- it came from early movement-off ablations
- it is not part of the live training recipe
- it adds special cases in training and viewer code

Why it is not obviously dead:
- it is still useful for debugging or ablation work

Current recommendation:
- prune if the team no longer wants movement-off debugging
- otherwise keep but clearly mark it as debug-only

#### 8. Threat-aware safe food / safe pot extras

Candidate removal:
- `shape_food_no_threat_penalty`
- `shape_pot_no_threat_penalty`

Why they are candidates:
- both have been `0.0` for a long time
- the core waste logic is carried by:
  - `shape_food_full_waste_penalty`
  - `shape_food_waste_scale`
  - `shape_pot_full_waste_penalty`
  - `shape_pot_waste_scale`
- these extra knobs widen the surface for a behavior that has not been actively tuned

Why this is lower priority:
- they are still live
- removing them does not reduce much code compared with the dead-key cleanup

Current recommendation:
- only prune after phase 1 and phase 2 are done

## Keep For Now

These are not good prune targets right now.

### Policy observation channels

Keep:
- all `17` player obs
- all `10 x 8` visible-NPC obs
- all `9` meta obs

Reason:
- the current `106`-float policy contract is already relatively tight
- there are no obvious dead live channels in the current mainline obs
- changing obs is invasive and likely to invalidate checkpoints

### Live reward shaping surface

Keep:
- `shape_wrong_prayer_penalty`
- `shape_npc_specific_prayer_bonus`
- `shape_npc_melee_penalty`
- `shape_wasted_attack_penalty`
- `shape_wave_stall_*`
- `shape_not_attacking_*`
- `shape_kiting_*`
- `shape_unnecessary_prayer_penalty`
- `shape_resource_threat_window`
- `shape_low_prayer_*`

Reason:
- these are active and materially shape current training behavior
- even when a value is modest, these are part of the current recipe rather than legacy clutter

### High-value analytics

Keep:
- `wave_reached`
- `max_wave`
- `most_npcs_slayed`
- `reached_wave_63`
- `jad_kill_rate`
- `cave_complete_rate`
- resource / prayer usage analytics
- melee-pressure analytics

Reason:
- these still support run diagnosis directly

## Recommended Cleanup Order

1. Remove dead parsed reward-weight keys.
2. Remove stale stage-era analytics (`wave_30/31`).
3. Re-audit docs after the live surface shrinks.
4. Then do one explicit contract cleanup for dead Jad reward-feature slots.
5. Only after that, decide whether to simplify curriculum/debug/viewer-only surfaces.

## First Refactor Worktree Goals

The first isolated refactor worktree should target only:

- dead parsed reward-weight keys
- stale `wave_30/31` analytics
- documentation cleanup that follows from those removals

It should explicitly avoid, in the first pass:

- policy observation changes
- trainer hyperparameter changes
- reward recipe retuning
- viewer input redesign
- combat model changes

## Open Questions

- Do we want to keep any curriculum surface at all, or treat curriculum as archived?
- Do we still want `disable_movement` for debugging, or is that era over?
- Is viewer click-to-move important enough to justify keeping `MOVE_TARGET_X/Y`?
- After phase 1 cleanup, do we want to make a clean contract bump and remove all dead reward-feature slots in one pass?
