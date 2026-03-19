# Observation Schema (HeadlessObservationV1)

This document defines the Step 7 observation contract for headless Fight Caves simulation.

## Schema Identity

- `schema_id`: `headless_observation_v1`
- `schema_version`: `1`
- `compatibility_policy`: `v1_additive_only`

Compatibility policy:
- Existing fields in v1 are immutable.
- New fields may only be added in an additive manner.
- Field removals/renames require a new schema id/version.

Future performance-path note:
- this raw schema remains the semantic reference for any future flat training schema
- any future flat training schema must preserve field meaning, visible-NPC ordering, and parity-sensitive combat cues such as `jad_telegraph_state`
- see [raw_flat_observation_contract.md](/home/jordan/code/fight-caves-RL/docs/raw_flat_observation_contract.md)

## Top-Level Field Order

The canonical ordered top-level keys are:

1. `schema_id`
2. `schema_version`
3. `compatibility_policy`
4. `tick`
5. `episode_seed`
6. `player`
7. `wave`
8. `npcs`

Optional debug key (only when explicitly enabled):
- `debug_future_leakage`

## Player Block

`player` includes:

- `tile`: `{ x, y, level }`
- `hitpoints_current`
- `hitpoints_max`
- `prayer_current`
- `prayer_max`
- `run_energy`
- `run_energy_max`
- `run_energy_percent`
- `running`
- `protection_prayers`:
  - `protect_from_magic`
  - `protect_from_missiles`
  - `protect_from_melee`
- `lockouts`:
  - `attack_locked` (`action_delay`)
  - `food_locked` (`food_delay`)
  - `drink_locked` (`drink_delay`)
  - `combo_locked` (`combo_delay`)
  - `busy_locked` (`delay` or `stunned`)
- `consumables`:
  - `shark_count`
  - `prayer_potion_dose_count` (sum of 4/3/2/1 dose items)
  - `ammo_id`
  - `ammo_count`

## Wave Block

`wave` includes:

- `wave` (`fight_cave_wave`)
- `rotation` (`fight_cave_rotation`)
- `remaining` (`fight_cave_remaining`)

## NPC Block

`npcs` is deterministically ordered and aligned to action targeting indices.
Each entry includes:

- `visible_index`
- `npc_index`
- `id`
- `tile`: `{ x, y, level }`
- `hitpoints_current`
- `hitpoints_max`
- `hidden`
- `dead`
- `under_attack`
- `jad_telegraph_state`

`jad_telegraph_state` meaning:

- `0` = `idle`
- `1` = `magic_windup`
- `2` = `ranged_windup`

Parity rule:
- this field is a semantic rendering of the same authoritative Jad telegraph state that drives the headed animation cue
- it is only non-zero during the real Jad telegraph window
- it does not expose the correct prayer, countdowns, or any pre-telegraph future attack info

Ordering source:
- Shared visible-NPC mapping used by the Step 6 action adapter.
- Comparator: level, x, y, id, npc_index.

## Future Leakage Policy

Default behavior:
- Future-leakage fields are omitted.

Opt-in debug mode:
- `debug_future_leakage` can be included only when explicitly requested by caller.
- This field is not part of the default training observation payload.
