# Flat Training Observation Schema

Date: 2026-03-09

This document defines the first sim-owned flat observation schema for the production training path.

## Schema Identity

- `schema_id`: `headless_training_flat_observation_v1`
- `schema_version`: `1`
- `owner`: `fight-caves-RL`
- `status`: implemented in the Phase 1 implementation batch

## Purpose

This schema is the future production training observation data plane.

It is designed to:

- remove nested map/list/string emission from the training hot path
- preserve the same semantic meaning as the raw headless observation contract
- be emitted directly by the sim in fixed-size typed batches
- be consumed by RL without `_pythonize`, dict reconstruction, or trainer-local recoding

The raw semantic reference remains:

- [observation_schema.md](/home/jordan/code/fight-caves-RL/docs/observation_schema.md)
- [raw_flat_observation_contract.md](/home/jordan/code/fight-caves-RL/docs/raw_flat_observation_contract.md)

## Design Decision For V1

`headless_training_flat_observation_v1` intentionally mirrors the current trainer-consumed feature layout frozen in:

- [observation_mapping.md](/home/jordan/code/RL/docs/observation_mapping.md)

Reason:

- it minimizes migration risk
- it removes the dominant Python object-conversion bottleneck without simultaneously redesigning the policy feature set
- it lets the first flat path be validated against an already-shipped policy input contract

This is a performance-path schema, not a semantic ownership transfer to RL.
The schema is sim-owned even though its first version intentionally matches the existing RL-local policy vector shape.

Current implementation note:

- the sim now emits this schema directly through the headless runtime surface
- Production Training Mode in RL consumes it directly in the batch step path
- the raw path remains available for Certification Mode and replay/parity tooling

## Batch Representation

### Observation payload

- dtype: `float32`
- layout: row-major
- shape: `[env_count, 134]`
- one contiguous row per env slot

`134` comes from:

- base field count: `30`
- NPC slot field count: `13`
- visible NPC cap: `8`
- total: `30 + (13 * 8) = 134`

### Control-plane separation

This schema covers only the observation payload.

It does not carry:

- rewards
- termination/truncation flags
- action masks
- reject reasons
- replay/debug snapshots
- transport-specific framing

Those remain separate bridge/control-plane concerns.

## Field Order

### Base prefix order

1. `schema_version`
2. `tick`
3. `episode_seed`
4. `player.tile.x`
5. `player.tile.y`
6. `player.tile.level`
7. `player.hitpoints_current`
8. `player.hitpoints_max`
9. `player.prayer_current`
10. `player.prayer_max`
11. `player.run_energy`
12. `player.run_energy_max`
13. `player.run_energy_percent`
14. `player.running`
15. `player.protection_prayers.protect_from_magic`
16. `player.protection_prayers.protect_from_missiles`
17. `player.protection_prayers.protect_from_melee`
18. `player.lockouts.attack_locked`
19. `player.lockouts.food_locked`
20. `player.lockouts.drink_locked`
21. `player.lockouts.combo_locked`
22. `player.lockouts.busy_locked`
23. `player.consumables.shark_count`
24. `player.consumables.prayer_potion_dose_count`
25. `player.consumables.ammo_id_code`
26. `player.consumables.ammo_count`
27. `wave.wave`
28. `wave.rotation`
29. `wave.remaining`
30. `npcs.visible_count`

### Per-NPC slot order

1. `present`
2. `visible_index`
3. `npc_index`
4. `id_code`
5. `tile.x`
6. `tile.y`
7. `tile.level`
8. `hitpoints_current`
9. `hitpoints_max`
10. `hidden`
11. `dead`
12. `under_attack`
13. `jad_telegraph_state`

## Fixed Cap And Padding

- `max_visible_npcs = 8`
- visible NPC ordering must remain identical to the raw path and action-target indexing contract
- fewer than `8` visible NPCs are padded with zero rows after the active visible list
- `present = 0` means the remaining slot fields must be zero-filled

If the sim needs more than `8` visible NPC slots in the future, that is a flat-schema version bump.

## Projection Rules

The flat path is a projection from raw semantic state.

Allowed projection transforms in `v1`:

- booleans become `0.0` or `1.0`
- categorical strings become stable integer codes represented as `float32`
- absent NPC slots become zero-padded rows

Projection transforms that are not allowed:

- reordering visible NPCs independently of the raw path
- changing the meaning or timing window of `jad_telegraph_state`
- converting the Jad telegraph into a prayer oracle or countdown
- introducing trainer-only future-leakage fields

## Categorical Dictionaries

### `ammo_id_code`

- `0` => `""`
- `1` => `adamant_bolts`

### `npc_id_code`

1. `tz_kih`
2. `tz_kih_spawn_point`
3. `tz_kek`
4. `tz_kek_spawn_point`
5. `tz_kek_spawn`
6. `tok_xil`
7. `tok_xil_spawn_point`
8. `yt_mej_kot`
9. `yt_mej_kot_spawn_point`
10. `ket_zek`
11. `ket_zek_spawn_point`
12. `tztok_jad`
13. `yt_hur_kot`

Changing these dictionaries is a flat-schema version bump.

## Fields Intentionally Not Emitted In The Flat Payload

The flat payload does not include:

- `schema_id`
- `compatibility_policy`
- raw nested maps/lists
- string item/NPC ids
- `debug_future_leakage`

Reason:

- `schema_id` and compatibility are validated in handshake/manifests rather than repeated in every row
- debug-only data must not contaminate the production training hot path

## Jad Cue Constraint

`jad_telegraph_state` is included in the flat schema because it is already part of the protected raw semantic contract.

The flat path must preserve:

- `0 = idle`
- `1 = magic_windup`
- `2 = ranged_windup`
- non-zero values only during the real telegraph window

No flat-schema version may replace this with:

- `correct_prayer`
- `pray_magic_now`
- `pray_ranged_now`
- countdown-to-impact
- any pre-telegraph future attack cue

## Certification Expectation

Before `headless_training_flat_observation_v1` is trusted in Production Training Mode, Certification Mode must prove:

- identical projected field meaning from the raw path
- identical visible-NPC slot alignment
- identical categorical code mapping
- identical Jad telegraph semantics and onset window

Current implementation status:

- the first source-side certification slice is now implemented in `FlatObservationProjectionEqualityTest`
- wider native-Linux decision-gate review remains part of `WC-P1-05`

## Downstream RL Consumption Expectation

The initial RL consumer target is zero-transform ingestion of this schema into the policy observation tensor.

That design is frozen separately in:

- [flat_observation_ingestion.md](/home/jordan/code/RL/docs/flat_observation_ingestion.md)
