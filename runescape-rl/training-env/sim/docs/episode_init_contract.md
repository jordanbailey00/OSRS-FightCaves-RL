# Episode Initialization Contract (Step 5)

This document defines the deterministic episode reset contract for headless Fight Caves runs.

## Entry Point

- Runtime API: `HeadlessRuntime.resetFightCaveEpisode(...)`
- Implementation: `FightCaveEpisodeInitializer.reset(player, config)`

## Inputs

`FightCaveEpisodeConfig`
- `seed: Long` (required)
- `startWave: Int` (default `1`, valid `1..63`)
- `ammo: Int` (default `1000`, must be `> 0`)
- `prayerPotions: Int` (default `8`, must be `>= 0`)
- `sharks: Int` (default `20`, must be `>= 0`)

## Reset Guarantees

### 1) Deterministic RNG seed
- Global shared RNG is seeded from `config.seed`.
- `player["episode_seed"]` is set to the same seed.
- Wave 1 rotation sampling uses this seeded RNG path.

### 2) Transient state cleanup
- Action queues cleared.
- Soft timers and timers stopped.
- Character mode reset to `EmptyMode`.
- Steps/facing/watch/animation/graphics reset.
- Known lockout clocks cleared:
  - `delay`
  - `movement_delay`
  - `food_delay`
  - `drink_delay`
  - `combo_delay`
  - `fight_cave_cooldown`

### 3) Fight Caves + prayer variable reset
- Fight Caves variables reset before re-init:
  - `fight_cave_wave`
  - `fight_cave_rotation`
  - `fight_cave_remaining`
  - `fight_cave_start_time`
  - `healed`
- Logout warning reset:
  - `fight_caves_logout_warning = false`
- Prayer state reset:
  - `PrayerConfigs.PRAYERS = ""`
  - `PrayerConfigs.USING_QUICK_PRAYERS = false`
  - `PrayerConfigs.SELECTING_QUICK_PRAYERS = false`
  - clear active/quick prayer and curse collections
  - clear `prayer_drain_counter`

### 4) Previous instance cleanup
- If the player already has a dynamic instance:
  - NPCs are cleared from all region levels in that instance.
  - `NPCs.run()` is invoked to flush despawns.
  - Previous instance binding is removed.

### 5) Fixed episode stats/resources
- Skills are set to fixed levels (current + max via XP):
  - Attack: `1`
  - Strength: `1`
  - Defence: `70`
  - Constitution: `700` (represents 70 HP in Void scale)
  - Ranged: `70`
  - Prayer: `43`
  - Magic: `1`
  - all other skills: `1`
- XP gain is blocked for all skills.
- Run state:
  - run energy: `MAX_RUN_ENERGY` (100%)
  - run toggle: ON (`movement = "run"`)

### 6) Fixed loadout and consumables
Equipment:
- `coif`
- `rune_crossbow`
- `black_dragonhide_body`
- `black_dragonhide_chaps`
- `black_dragonhide_vambraces`
- `snakeskin_boots`
- `adamant_bolts x ammo`

Inventory:
- `prayer_potion_4 x prayerPotions`
- `shark x sharks`

### 7) New Fight Caves instance and wave boot
- A new small dynamic instance is created for the player.
- Player is teleported and walked to Fight Caves start positions with the new instance offset.
- `fightCave.startWave(player, startWave, start = false)` is called.
- `start = false` is intentional for headless episodes to avoid headed dialogue/cutscene gating while preserving wave state initialization semantics.

## Output

`FightCaveEpisodeState`
- `seed`
- `wave`
- `rotation`
- `remaining`
- `instanceId`
- `playerTile`

## Verification Tests

- `EpisodeInitSetsFixedStatsTest`
- `EpisodeInitSetsLoadoutAndConsumablesTest`
- `EpisodeInitResetsWaveStateTest`
- `EpisodeInitUsesProvidedSeedTest`


