# Jad Telegraph Plan

## Purpose

This document is the source of truth for the Jad telegraph mini-rework across the workspace.

The design rule is:

Any combat cue needed for decision-making must exist as an authoritative gameplay event in SimCore first. Headed presentation and headless observation are both derived from that same event with identical onset tick, duration, and consequence window.

## Scope

This rework is narrowly scoped to:

- Jad attack telegraph state in the shared combat/sim path
- headed cue wiring for Jad
- headless observation exposure for Jad
- replay/parity traceability for Jad telegraph timing
- regression tests that prove parity and prevent oracle leakage

This rework must not:

- expose `correct_prayer`
- expose direct answer fields such as `pray_magic_now` or `pray_ranged_now`
- expose countdown helpers such as `ticks_until_impact`
- change hit timing, prayer-check timing, or reaction window semantics
- create different combat timing for headed and headless

## Authoritative Ownership

- `fight-caves-RL` owns the authoritative Jad telegraph gameplay event/state and the parity contract for the headless/oracle runtime pair.
- `RL` only consumes the headless observation contract and must not redefine Jad combat semantics.
- `RSPS` remains outside the RL hot path, but this document lives here because the final headed demo path must preserve the same telegraph meaning and timing.

## Current Code Inspection Findings

These findings are the pre-implementation verification required before logic changes.

### 1. Current Jad attack lifecycle

- Generic NPC attack selection, animation, gfx, sounds, and attack dispatch happen in [Attack.kt](/home/jordan/code/fight-caves-RL/game/src/main/kotlin/content/entity/npc/combat/Attack.kt).
- Jad-specific magic and ranged hit scheduling overrides live in [TzTokJad.kt](/home/jordan/code/fight-caves-RL/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzTokJad.kt).
- Current headed animation onset happens inside the generic NPC swing path when `play(attack.anim)` is invoked.

### 2. Current headed cue trigger points

- Animation onset is driven by combat definitions in:
  - [tzhaar_fight_cave.combat.toml](/home/jordan/code/fight-caves-RL/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.combat.toml)
  - [tzhaar_fight_cave.anims.toml](/home/jordan/code/fight-caves-RL/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.anims.toml)
- Jad currently uses:
  - `tztok_jad_attack_magic`
  - `tztok_jad_attack_range`
- Current Jad attack cues are animation-driven with combat-configured gfx/sound side effects.
- There is no Jad-specific `say(...)` cue in the current attack definitions. Dialog/audio is therefore not the canonical cue source in the current code path.

### 3. Current hit resolution timing

Current Jad magic/ranged timing is:

1. Generic NPC swing begins and starts the headed animation on the current game tick.
2. Jad-specific override queues `hit_target` after `3` game ticks.
3. The queued `hit(...)` uses a `64` client-tick delay.
4. The hit helper converts that to `CLIENT_TICKS.toTicks(64) + 1 = 3` game ticks before `directHit(...)`.

So the current onset-to-resolution window is `6` game ticks total:

- `3` game ticks from the Jad override queue
- plus `3` game ticks from the existing `hit(...)` scheduling path

### 4. Current prayer-check timing

- Prayer/damage semantics remain in the existing hit/damage path:
  - [Hit.kt](/home/jordan/code/fight-caves-RL/game/src/main/kotlin/content/entity/combat/hit/Hit.kt)
  - [Damage.kt](/home/jordan/code/fight-caves-RL/game/src/main/kotlin/content/entity/combat/hit/Damage.kt)
- Jad-specific timing nuance confirmed by regression tests:
  - the telegraph-to-damage span is still `6` game ticks
  - but prayer protection is applied when the queued `hit(...)` is constructed after the Jad-specific `3`-tick windup
  - the later delayed `directHit(...)` landing tick does not reopen the prayer decision window
- Prayer protection must continue to be evaluated exactly where the current combat engine already evaluates it.
- This rework must not move prayer evaluation earlier or later.

### 5. Current headless observation construction

- Headless observations are built in [HeadlessObservationBuilder.kt](/home/jordan/code/fight-caves-RL/game/src/main/kotlin/HeadlessObservationBuilder.kt).
- The default observation now exposes the additive NPC field `jad_telegraph_state`.
- This field is a semantic rendering of the same authoritative Jad telegraph state that drives the headed cue.
- Downstream optimization implication: any future flat training schema must preserve this cue's meaning and timing exactly rather than replacing it with a prayer oracle or countdown.

### 6. Current replay/parity serialization

- Replay snapshots are built in [HeadlessReplayRunner.kt](/home/jordan/code/fight-caves-RL/game/src/main/kotlin/HeadlessReplayRunner.kt).
- Oracle-vs-headless parity snapshots are built in [ParityHarness.kt](/home/jordan/code/fight-caves-RL/game/src/main/kotlin/ParityHarness.kt).

## Resolved Parity Assumptions

The following are implementation rules.

### 1. Canonical telegraph onset

Use the current headed animation onset tick as the canonical Jad telegraph onset.

Interpretation:

- the authoritative telegraph state begins on the same tick the headed path begins the Jad attack animation
- animation onset is the parity anchor for both headed and headless representations

### 2. Dialog/audio treatment

Dialog/audio is not the canonical telegraph source.

Interpretation:

- if dialog/audio starts on the same tick as animation, that is acceptable
- if it starts on a different tick, telegraph timing does not move to match it
- dialog/audio remains a presentation side effect of the same underlying telegraph event

### 3. Prayer-check timing

Preserve the current combat engine prayer-check timing exactly as it exists today.

Interpretation:

- current code inspection and regression coverage confirm that Jad protection is sampled when the queued `hit(...)` is constructed after the `3`-tick windup
- the later delayed visual impact tick does not reopen the decision window
- do not move prayer evaluation earlier or later
- the telegraph state must not alter damage-resolution semantics

### 4. Authoritative contract

The Jad telegraph contract is:

- telegraph onset tick = current headed animation start tick
- telegraph meaning = committed Jad attack style
- telegraph duration = existing reaction window until current hit-resolution tick
- prayer-check timing = unchanged existing engine behavior
- headed animation/audio = downstream presentation of the telegraph state
- headless observation = symbolic/semantic encoding of that same telegraph state

### 5. Mismatch handling rule

If inspection reveals:

- animation and audio/dialog are offset, animation remains canonical
- prayer is checked at hit resolution, preserve it exactly
- the current code already contains a partial telegraph abstraction, extend that instead of creating a parallel one

## Target Design

The authoritative gameplay state for Jad should be equivalent to:

- `telegraph_state = idle | magic_windup | ranged_windup`
- `telegraph_start_tick`
- `hit_resolve_tick`
- `committed_attack_style`

The intended sequence is:

1. Jad commits to attack style.
2. Shared combat state enters the matching telegraph state.
3. Telegraph begins on the same tick the headed animation starts.
4. Existing combat-valid delay elapses.
5. Existing hit-resolution path runs and prayer is checked where it already is today.

## Delivery Order

### JAD-01 Current timing freeze and integration-point audit

Purpose:

- formalize the current Jad timing contract from existing code before changing behavior

Required output:

- code/documented timeline anchored to the existing headed animation tick
- exact identification of current hit-resolution timing and prayer-check location

### JAD-02 Shared telegraph state in the sim/combat core

Purpose:

- add the first-class Jad telegraph state with no semantic timing drift

Required output:

- shared authoritative state for Jad telegraph lifecycle
- telegraph onset anchored to the current headed animation tick
- hit resolve tick recorded without altering existing combat timing

### JAD-03 Headless observation exposure

Purpose:

- expose the telegraph signal, not the answer, to the policy

Required output:

- semantic headless encoding of idle / magic windup / ranged windup
- no direct correct-prayer oracle

### JAD-04 Headed/demo/replay wiring and traceability

Purpose:

- ensure headed presentation and replay tracing both derive from the same telegraph state

Required output:

- headed cue sourced from the shared telegraph event/state
- replay/parity traceability for onset, resolve, and outcome

### JAD-05 Regression gate

Purpose:

- prevent parity drift and oracle leakage

Required output:

- lifecycle tests
- prayer-protection timing tests
- headless observation tests
- oracle-vs-headless timing parity tests
- replay/demo trace assertions

### JAD-06 Replay/demo outcome acceptance

Purpose:

- prove that telegraphed Jad prayer outcomes and resolve timing stay aligned across oracle and headless

Required output:

- replay/parity tests that carry a telegraphed Jad attack through hit resolution
- assertions that the same resolve tick and prayer-dependent damage outcome hold in oracle and headless
- assertions that replay/parity traces retain the last Jad telegraph timing plus committed prayer-sampling outcome after the telegraph clears back to idle
- targeted Jad-trace assertions when unrelated oracle-side world activity would make whole-snapshot equality too noisy for this mini-rework
- active work-chunk status updated to reflect the mini-rework closeout state

## Active Work Chunks

### WC-JAD-01

- [x] freeze current Jad timing and cue integration points
- [x] codify the current onset-to-resolution timing constants
- [x] document the parity anchor explicitly

### WC-JAD-02

- [x] add authoritative Jad telegraph state
- [x] begin telegraph on the current headed animation tick
- [x] keep prayer-check timing unchanged
- [x] keep headed cue timing unchanged

### WC-JAD-03

- [x] expose Jad telegraph state to headless observation
- [x] update RL-side observation contract only after the sim-side telegraph contract is in place

### WC-JAD-04

- [x] extend parity/replay traces with Jad telegraph timing fields derived from the same authoritative state
- [x] prove oracle/headless trace alignment for a single telegraph event

### WC-JAD-05

- [x] add lifecycle, protection, observation, parity, and regression tests
- [x] prove that Jad prayer protection is sampled at queued-hit construction, not at the later delayed visual landing tick
- [x] prove that no direct correct-prayer oracle is exposed

### WC-JAD-06

- [x] add replay/parity outcome assertions through Jad hit resolution
- [x] retain last-attack telegraph timing and outcome fields in replay/parity traces after the active telegraph clears
- [x] close the mini-rework with an explicit acceptance-status update

## Acceptance Status

This mini-rework is complete when evaluated against the scoped Jad telegraph requirements.

Completed:

- authoritative Jad telegraph state/event exists in shared combat logic
- headed animation onset remains the canonical telegraph start tick
- headless observation now exposes the telegraph signal without exposing the answer
- replay/parity traces now carry:
  - telegraph onset tick
  - hit resolve tick
  - prayer check tick
  - sampled protection prayer state
  - protection outcome
  - resolved damage
- regression coverage now proves:
  - lifecycle correctness
  - observation correctness
  - protection semantics
  - oracle leakage guard
  - replay/parity outcome visibility

Important implementation note:

- current engine truth is that Jad prayer is sampled at queued-hit construction after the `3`-tick windup
- the later delayed hit landing remains the visual/resolve point, but it is not the point where the protection decision is made
- this mini-rework preserved that engine behavior exactly and made it explicit in the shared telegraph/outcome trace
