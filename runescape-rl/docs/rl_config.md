# RL Configuration Reference

This file is the code-audited reference for the current Fight Caves RL setup. Historical runs and comparisons now live in [run_history.md](/home/joe/projects/runescape-rl/codex3/runescape-rl/docs/run_history.md).

## Current Checked-In Config (`v21.3`)

This is the active repo config in:
- [runescape-rl/config/fight_caves.ini](/home/joe/projects/runescape-rl/codex3/runescape-rl/config/fight_caves.ini)
- [pufferlib_4/config/fight_caves.ini](/home/joe/projects/runescape-rl/codex3/pufferlib_4/config/fight_caves.ini)

This checked-in baseline is the planned `v21.3` run config.

Compared with the `v21.2` run config, it keeps the same PPO stack and reward weights,
keeps the corrected `Masori (f) + Twisted bow` combat model and the wider `7/10`
kiting band from `v22.1`, and adds only a narrow low-prayer rescue rule:
- `shape_low_prayer_pot_threshold: 0.0 -> 0.05`
- `shape_low_prayer_no_pot_penalty: -0.01`
- `shape_low_prayer_pot_reward: 0.0`
- no checkpoint warm-start

It still keeps the non-learning infrastructure improvements added around `v21.1`:
- reward math centralized through the shared `fc_reward.h` helper so viewer and training use the same breakdown
- explicit analytics:
  - `cave_complete_rate`
  - `reached_wave_63`
  - `jad_kill_rate`
- viewer / debug overlay parity fixes

```ini
[base]
env_name = fight_caves
checkpoint_interval = 50

[env]
w_damage_dealt = 0.5
w_attack_attempt = 0.1
w_damage_taken = -0.6
w_npc_kill = 3.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = 0.0
w_food_used_well = 0.0
w_prayer_pot_used = 0.0
w_correct_jad_prayer = 0.0
w_wrong_jad_prayer = 0.0
w_correct_danger_prayer = 0.25
w_wrong_danger_prayer = 0.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = 0.0
w_tick_penalty = -0.005
shape_food_full_waste_penalty = -6.5
shape_food_waste_scale = -1.2
shape_food_safe_hp_threshold = 1.0
shape_food_no_threat_penalty = 0.0
shape_pot_full_waste_penalty = -6.5
shape_pot_waste_scale = -1.2
shape_pot_safe_prayer_threshold = 1.0
shape_pot_no_threat_penalty = 0.0
shape_low_prayer_pot_threshold = 0.05
shape_low_prayer_no_pot_penalty = -0.01
shape_low_prayer_pot_reward = 0.0
shape_wrong_prayer_penalty = -1.25
shape_npc_specific_prayer_bonus = 1.5
shape_npc_melee_penalty = -0.3
shape_wasted_attack_penalty = -0.1
shape_wave_stall_start = 500
shape_wave_stall_base_penalty = -0.5
shape_wave_stall_ramp_interval = 50
shape_wave_stall_cap = -2.0
shape_not_attacking_grace_ticks = 2
shape_not_attacking_penalty = -0.01
shape_kiting_reward = 1.0
shape_kiting_min_dist = 7
shape_kiting_max_dist = 10
shape_unnecessary_prayer_penalty = -0.2
shape_resource_threat_window = 2
curriculum_wave = 0
curriculum_pct = 0.0
curriculum_wave_2 = 0
curriculum_pct_2 = 0.0
curriculum_wave_3 = 0
curriculum_pct_3 = 0.0
disable_movement = 0

[vec]
total_agents = 4096
num_buffers = 2

[train]
total_timesteps = 5_000_000_000
learning_rate = 0.0003
anneal_lr = 0
gamma = 0.999
gae_lambda = 0.95
clip_coef = 0.2
vf_coef = 0.5
ent_coef = 0.01
max_grad_norm = 0.5
horizon = 256
minibatch_size = 4096

[policy]
hidden_size = 256
num_layers = 2
```

## Reference Delta (`v21.3` vs `v21.2`)

`v21.3` starts from the `v21.2` config with these deltas:

- `shape_low_prayer_pot_threshold = 0.05`
- `shape_low_prayer_no_pot_penalty = -0.01`
- `shape_low_prayer_pot_reward = 0.0`
- keep the corrected `Masori (f) + Twisted bow` combat model from `v22.1`
- keep the `7/10` kiting band from `v22.1`

Everything else remains at the `v21.2` reward/training values:

```ini
[env]
w_damage_dealt = 0.5
w_attack_attempt = 0.1
w_damage_taken = -0.6
w_npc_kill = 3.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = 0.0
w_food_used_well = 0.0
w_prayer_pot_used = 0.0
w_correct_jad_prayer = 0.0
w_wrong_jad_prayer = 0.0
w_correct_danger_prayer = 0.25
w_wrong_danger_prayer = 0.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = 0.0
w_tick_penalty = -0.005
shape_food_full_waste_penalty = -6.5
shape_food_waste_scale = -1.2
shape_food_safe_hp_threshold = 1.0
shape_food_no_threat_penalty = 0.0
shape_pot_full_waste_penalty = -6.5
shape_pot_waste_scale = -1.2
shape_pot_safe_prayer_threshold = 1.0
shape_pot_no_threat_penalty = 0.0
shape_low_prayer_pot_threshold = 0.05
shape_low_prayer_no_pot_penalty = -0.01
shape_low_prayer_pot_reward = 0.0
shape_wrong_prayer_penalty = -1.25
shape_npc_specific_prayer_bonus = 1.5
shape_npc_melee_penalty = -0.3
shape_wasted_attack_penalty = -0.1
shape_wave_stall_start = 500
shape_wave_stall_base_penalty = -0.5
shape_wave_stall_ramp_interval = 50
shape_wave_stall_cap = -2.0
shape_not_attacking_grace_ticks = 2
shape_not_attacking_penalty = -0.01
shape_kiting_reward = 1.0
shape_kiting_min_dist = 7
shape_kiting_max_dist = 10
shape_unnecessary_prayer_penalty = -0.2
shape_resource_threat_window = 2
curriculum_wave = 0
curriculum_pct = 0.0
curriculum_wave_2 = 0
curriculum_pct_2 = 0.0
curriculum_wave_3 = 0
curriculum_pct_3 = 0.0
disable_movement = 0

[vec]
total_agents = 4096
num_buffers = 2

[train]
total_timesteps = 5_000_000_000
learning_rate = 0.0003
anneal_lr = 0
gamma = 0.999
gae_lambda = 0.95
clip_coef = 0.2
vf_coef = 0.5
ent_coef = 0.01
max_grad_norm = 0.5
horizon = 256
minibatch_size = 4096

[policy]
hidden_size = 256
num_layers = 2
```

Relative to the previous checked-in `v22.1` config, `v21.3` removes:
- the `v22.1` reward retune
- the `v22.1` zero-prayer potion-teaching settings
- checkpoint warm-start

## Status Legend

- <span style="color:#16a34a">ACTIVE NOW</span> = live in code and materially enabled in the current checked-in config.
- <span style="color:#2563eb">LIVE / OFF</span> = live in code and available to tune, but currently set to `0`, disabled, or not selected in the current checked-in config.
- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> = parsed legacy surface with no current effect, or a historical surface removed from the current mainline contract.

## Config Surface

### `[base]`

- <span style="color:#16a34a">ACTIVE NOW</span> `env_name`
  Fight Caves environment entrypoint name. `train.sh` and PufferLib expect this to stay `fight_caves`.
- <span style="color:#16a34a">ACTIVE NOW</span> `checkpoint_interval`
  Trainer checkpoint cadence. Lower values write checkpoints more often; higher values reduce I/O.

### `[env] Reward Weights`

- <span style="color:#16a34a">ACTIVE NOW</span> `w_damage_dealt`
  Dense reward for landed damage to NPCs.
- <span style="color:#16a34a">ACTIVE NOW</span> `w_attack_attempt`
  Dense reward for launching a real attack cycle, even if the later hit misses or rolls `0`.
- <span style="color:#16a34a">ACTIVE NOW</span> `w_damage_taken`
  Dense penalty on player damage taken. Current reward logic squares normalized damage before applying this weight.
- <span style="color:#16a34a">ACTIVE NOW</span> `w_npc_kill`
  Sparse per-NPC kill reward.
- <span style="color:#16a34a">ACTIVE NOW</span> `w_wave_clear`
  Sparse wave-clear reward. Current logic multiplies it by the cleared wave number.
- <span style="color:#16a34a">ACTIVE NOW</span> `w_jad_damage`
  Extra dense reward for damage dealt to Jad.
- <span style="color:#16a34a">ACTIVE NOW</span> `w_jad_kill`
  Sparse reward when Jad dies.
- <span style="color:#16a34a">ACTIVE NOW</span> `w_player_death`
  Terminal death penalty.
- <span style="color:#16a34a">ACTIVE NOW</span> `w_cave_complete`
  Terminal reward for clearing all 63 waves.
- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> `w_food_used`
  Parsed from config but not consumed by the shared reward breakdown. Food timing is handled by shaping terms instead.
- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> `w_food_used_well`
  Historical positive food-use weight. Still parsed, not used by the live reward path.
- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> `w_prayer_pot_used`
  Historical potion-use weight. Still parsed, not used by the live reward path.
- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> `w_correct_jad_prayer`
  Parsed legacy Jad-specific key. The current reward path does not read it.
- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> `w_wrong_jad_prayer`
  Parsed legacy Jad-specific key. The current reward path does not read it.
- <span style="color:#16a34a">ACTIVE NOW</span> `w_correct_danger_prayer`
  Reward for correctly blocked resolved ranged or magic attacks using the backend prayer snapshot that actually governed the hit.
- <span style="color:#2563eb">LIVE / OFF</span> `w_wrong_danger_prayer`
  Penalty multiplier for resolved ranged or magic hits taken with wrong or no prayer. Live path, currently set to `0.0`.
- <span style="color:#16a34a">ACTIVE NOW</span> `w_invalid_action`
  Penalty when the policy attempts an invalid RL-facing masked action.
- <span style="color:#2563eb">LIVE / OFF</span> `w_movement`
  Optional weight on the movement reward feature. Live path, currently `0.0`.
- <span style="color:#2563eb">LIVE / OFF</span> `w_idle`
  Optional weight on the literal idle feature. Live path, currently `0.0`.
- <span style="color:#16a34a">ACTIVE NOW</span> `w_tick_penalty`
  Per-tick living/time-cost penalty.

### `[env] Shaping Terms`

- <span style="color:#16a34a">ACTIVE NOW</span> `shape_food_full_waste_penalty`
  Flat penalty when food is used at full HP.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_food_waste_scale`
  Scaled penalty for overhealing with food.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_food_safe_hp_threshold`
  HP fraction threshold for the safe-food logic.
- <span style="color:#2563eb">LIVE / OFF</span> `shape_food_no_threat_penalty`
  Extra penalty for eating above the safe threshold when there is no imminent threat. Live path, currently `0.0`.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_pot_full_waste_penalty`
  Flat penalty when a prayer dose is used at full prayer.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_pot_waste_scale`
  Scaled penalty for over-restoring prayer.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_pot_safe_prayer_threshold`
  Prayer fraction threshold for the safe-potion logic.
- <span style="color:#2563eb">LIVE / OFF</span> `shape_pot_no_threat_penalty`
  Extra penalty for drinking above the safe threshold with no threat active. Live path, currently `0.0`.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_low_prayer_pot_threshold`
  Low-prayer teaching threshold, expressed as a fraction of max prayer and floored to whole prayer points before use. Current value `0.0` means the signal only fires at exact zero prayer.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_low_prayer_no_pot_penalty`
  Small penalty when the player is at or below the low-prayer threshold, can drink, still has doses, and does not drink.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_low_prayer_pot_reward`
  Small reward when a prayer potion dose is consumed at or below the low-prayer threshold.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_wrong_prayer_penalty`
  Extra shaping penalty layered on top of the resolved wrong-danger prayer event.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_npc_specific_prayer_bonus`
  Extra reward for correct mapped prayer blocks on key NPC classes: Ket-Zek magic, Tok-Xil range, and melee-only threats.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_npc_melee_penalty`
  Per-tick penalty for allowing NPCs into melee range.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_wasted_attack_penalty`
  Penalty when the player is attack-ready, has a target, and still deals no damage.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_wave_stall_base_penalty`
  Base penalty applied once a wave exceeds the stall threshold.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_wave_stall_cap`
  Cap on the escalating stall penalty.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_not_attacking_penalty`
  Penalty applied after the non-attacking grace window while NPCs are alive and the player remains attack-ready.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_kiting_reward`
  Reward for dealing damage while keeping distance within the configured kiting band.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_unnecessary_prayer_penalty`
  Per-tick penalty for leaving prayer on when no threat exists.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_resource_threat_window`
  Lookahead window, in ticks, used by threat-aware food/pot shaping.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_kiting_min_dist`
  Lower bound of the rewarded kiting band. Current value is `7`.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_kiting_max_dist`
  Upper bound of the rewarded kiting band. Current value is `10`.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_wave_stall_start`
  Wave tick count where stall penalties begin.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_wave_stall_ramp_interval`
  Number of ticks between stall-penalty ramp increases.
- <span style="color:#16a34a">ACTIVE NOW</span> `shape_not_attacking_grace_ticks`
  Number of consecutive attack-ready ticks allowed before the non-attacking penalty begins.

### `[env] Curriculum / Runtime`

- <span style="color:#2563eb">LIVE / OFF</span> `curriculum_wave`
  First optional curriculum bucket. `0` disables it.
- <span style="color:#2563eb">LIVE / OFF</span> `curriculum_pct`
  Fraction of episodes assigned to `curriculum_wave`.
- <span style="color:#2563eb">LIVE / OFF</span> `curriculum_wave_2`
  Second optional curriculum bucket.
- <span style="color:#2563eb">LIVE / OFF</span> `curriculum_pct_2`
  Fraction of episodes assigned to `curriculum_wave_2`.
- <span style="color:#2563eb">LIVE / OFF</span> `curriculum_wave_3`
  Third optional curriculum bucket.
- <span style="color:#2563eb">LIVE / OFF</span> `curriculum_pct_3`
  Fraction of episodes assigned to `curriculum_wave_3`.
- <span style="color:#2563eb">LIVE / OFF</span> `disable_movement`
  Forces the movement head to idle. Used only for ablations/debug; current config leaves movement enabled.

### `[vec]`

- <span style="color:#16a34a">ACTIVE NOW</span> `total_agents`
  Number of parallel Fight Caves environments stepped by the trainer.
- <span style="color:#16a34a">ACTIVE NOW</span> `num_buffers`
  Number of rollout buffers. `2` enables overlapped rollout/training.

### `[train]`

- <span style="color:#16a34a">ACTIVE NOW</span> `total_timesteps`
  Total environment steps before training stops.
- <span style="color:#16a34a">ACTIVE NOW</span> `learning_rate`
  Adam learning rate.
- <span style="color:#16a34a">ACTIVE NOW</span> `anneal_lr`
  `0` keeps the learning rate constant; `1` linearly decays it to zero over the run.
- <span style="color:#16a34a">ACTIVE NOW</span> `gamma`
  Discount factor for future rewards.
- <span style="color:#16a34a">ACTIVE NOW</span> `gae_lambda`
  GAE bias/variance tradeoff parameter.
- <span style="color:#16a34a">ACTIVE NOW</span> `clip_coef`
  PPO clip ratio.
- <span style="color:#16a34a">ACTIVE NOW</span> `vf_coef`
  Value-function loss weight.
- <span style="color:#16a34a">ACTIVE NOW</span> `ent_coef`
  Entropy bonus weight.
- <span style="color:#16a34a">ACTIVE NOW</span> `max_grad_norm`
  Gradient clipping norm.
- <span style="color:#16a34a">ACTIVE NOW</span> `horizon`
  Rollout length before PPO updates.
- <span style="color:#16a34a">ACTIVE NOW</span> `minibatch_size`
  Samples per PPO minibatch.

### `[policy]`

- <span style="color:#16a34a">ACTIVE NOW</span> `hidden_size`
  Width of each hidden layer in the policy/value MLP.
- <span style="color:#16a34a">ACTIVE NOW</span> `num_layers`
  Number of hidden layers.

### Launcher / Runtime Overrides (not INI keys)

- <span style="color:#16a34a">ACTIVE NOW</span> `CONFIG_PATH`
  Optional override for which INI file `train.sh` syncs into PufferLib before launch.
- <span style="color:#2563eb">LIVE / OFF</span> `PUFFER_DIR`
  Optional override for which local PufferLib checkout the launcher targets.
- <span style="color:#2563eb">LIVE / OFF</span> `FORCE_BACKEND_REBUILD`
  Forces `train.sh` to rebuild the native backend before launch.
- <span style="color:#16a34a">ACTIVE NOW</span> `LOAD_MODEL_PATH`
  Optional warm-start checkpoint path, or `latest`. Planned `v22.1` launch uses `/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/u58coupx/0000001311768576.bin`.
- <span style="color:#2563eb">LIVE / OFF</span> `--no-wandb`
  Launcher flag that disables W&B logging for that run.

## Observation, Reward, and Action Contracts

### Policy Observation Contract

- <span style="color:#16a34a">ACTIVE NOW</span> `FC_POLICY_OBS_SIZE = 106`
  Current policy-visible contract.
- <span style="color:#16a34a">ACTIVE NOW</span> Visible-NPC ordering
  NPC slots are ordered by Chebyshev distance to the player, then `spawn_index` as a tiebreaker.

#### Active Player Observation Channels (`17`)

- <span style="color:#16a34a">ACTIVE NOW</span> `player_hp`
  Normalized current HP.
- <span style="color:#16a34a">ACTIVE NOW</span> `player_prayer`
  Normalized current prayer points.
- <span style="color:#16a34a">ACTIVE NOW</span> `player_x`
  Normalized player X tile coordinate.
- <span style="color:#16a34a">ACTIVE NOW</span> `player_y`
  Normalized player Y tile coordinate.
- <span style="color:#16a34a">ACTIVE NOW</span> `player_atk_timer`
  Normalized attack cooldown. `0` means attack-ready.
- <span style="color:#16a34a">ACTIVE NOW</span> `player_pray_melee`
  One-hot bit for Protect from Melee.
- <span style="color:#16a34a">ACTIVE NOW</span> `player_pray_range`
  One-hot bit for Protect from Missiles.
- <span style="color:#16a34a">ACTIVE NOW</span> `player_pray_magic`
  One-hot bit for Protect from Magic.
- <span style="color:#16a34a">ACTIVE NOW</span> `player_sharks`
  Normalized sharks remaining.
- <span style="color:#16a34a">ACTIVE NOW</span> `player_doses`
  Normalized prayer doses remaining.
- <span style="color:#16a34a">ACTIVE NOW</span> `player_incoming_melee_1t`
  Normalized count of melee hits landing in `1` tick.
- <span style="color:#16a34a">ACTIVE NOW</span> `player_incoming_range_1t`
  Normalized count of ranged hits landing in `1` tick.
- <span style="color:#16a34a">ACTIVE NOW</span> `player_incoming_magic_1t`
  Normalized count of magic hits landing in `1` tick.
- <span style="color:#16a34a">ACTIVE NOW</span> `player_incoming_melee_2t`
  Normalized count of melee hits landing in `2` ticks.
- <span style="color:#16a34a">ACTIVE NOW</span> `player_incoming_range_2t`
  Normalized count of ranged hits landing in `2` ticks.
- <span style="color:#16a34a">ACTIVE NOW</span> `player_incoming_magic_2t`
  Normalized count of magic hits landing in `2` ticks.
- <span style="color:#16a34a">ACTIVE NOW</span> `player_target`
  Current visible target slot, normalized so `0` means no target and `(slot + 1) / 8` means visible slot `0-7`.

#### Active Per-NPC Observation Channels (`10` per visible slot, `8` slots)

- <span style="color:#16a34a">ACTIVE NOW</span> `valid`
  `1` if the slot is occupied by an active NPC.
- <span style="color:#16a34a">ACTIVE NOW</span> `npc_x`
  Normalized NPC X tile coordinate.
- <span style="color:#16a34a">ACTIVE NOW</span> `npc_y`
  Normalized NPC Y tile coordinate.
- <span style="color:#16a34a">ACTIVE NOW</span> `npc_hp`
  Normalized NPC HP.
- <span style="color:#16a34a">ACTIVE NOW</span> `npc_distance`
  Normalized Chebyshev distance from player to NPC.
- <span style="color:#16a34a">ACTIVE NOW</span> `npc_effective_style_now`
  Attack style the NPC would use right now at current distance and LOS.
- <span style="color:#16a34a">ACTIVE NOW</span> `npc_atk_timer`
  Normalized NPC attack cooldown.
- <span style="color:#16a34a">ACTIVE NOW</span> `npc_los`
  `1` if the player has LOS to the NPC center tile.
- <span style="color:#16a34a">ACTIVE NOW</span> `npc_pending_style`
  Incoming pending-hit style on the player from this NPC, if any.
- <span style="color:#16a34a">ACTIVE NOW</span> `npc_pending_ticks`
  Normalized ticks until that pending hit resolves.

#### Active Meta Observation Channels (`9`)

- <span style="color:#16a34a">ACTIVE NOW</span> `wave`
  Current wave number, normalized by `63`.
- <span style="color:#16a34a">ACTIVE NOW</span> `rotation`
  Current spawn rotation, normalized by `15`.
- <span style="color:#16a34a">ACTIVE NOW</span> `npcs_remaining`
  Normalized remaining active NPC count.
- <span style="color:#16a34a">ACTIVE NOW</span> `prayer_drain_counter`
  Normalized OSRS-style prayer drain counter state.
- <span style="color:#16a34a">ACTIVE NOW</span> `incoming_melee_3t`
  Normalized count of melee hits landing in `3` ticks.
- <span style="color:#16a34a">ACTIVE NOW</span> `incoming_range_3t`
  Normalized count of ranged hits landing in `3` ticks.
- <span style="color:#16a34a">ACTIVE NOW</span> `incoming_magic_3t`
  Normalized count of magic hits landing in `3` ticks.
- <span style="color:#16a34a">ACTIVE NOW</span> `damage_taken_this_tick`
  Normalized damage the player took this tick.
- <span style="color:#16a34a">ACTIVE NOW</span> `wave_just_cleared`
  `1` on the tick where the last NPC in the wave dies.

### Internal Reward Feature Contract

- <span style="color:#16a34a">ACTIVE NOW</span> `FC_REWARD_FEATURES = 19`
  Internal features written behind policy observations. The policy does not consume them by default.
- <span style="color:#16a34a">ACTIVE NOW</span> `damage_dealt`
  Normalized NPC damage dealt this tick. Directly weighted by `w_damage_dealt`.
- <span style="color:#16a34a">ACTIVE NOW</span> `damage_taken`
  Normalized player damage taken this tick. Directly weighted by `w_damage_taken` after quadratic scaling.
- <span style="color:#16a34a">ACTIVE NOW</span> `npc_kill`
  NPC deaths this tick. Directly weighted by `w_npc_kill`.
- <span style="color:#16a34a">ACTIVE NOW</span> `wave_clear`
  Wave-clear event. Directly weighted by `w_wave_clear` with wave-number scaling.
- <span style="color:#16a34a">ACTIVE NOW</span> `jad_damage`
  Jad damage dealt this tick. Directly weighted by `w_jad_damage`.
- <span style="color:#16a34a">ACTIVE NOW</span> `jad_kill`
  Jad death event. Directly weighted by `w_jad_kill`.
- <span style="color:#16a34a">ACTIVE NOW</span> `player_death`
  Terminal death event. Directly weighted by `w_player_death`.
- <span style="color:#16a34a">ACTIVE NOW</span> `cave_complete`
  Full-cave completion event. Directly weighted by `w_cave_complete`.
- <span style="color:#16a34a">ACTIVE NOW</span> `food_used`
  Food-use event. Consumed by live food waste/safety shaping.
- <span style="color:#16a34a">ACTIVE NOW</span> `prayer_pot_used`
  Prayer-dose use event. Consumed by live potion waste/safety shaping.
- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> `correct_jad_pray`
  Still emitted in the enum, but ignored by the live reward breakdown.
- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> `wrong_jad_pray`
  Still emitted in the enum, but ignored by the live reward breakdown.
- <span style="color:#16a34a">ACTIVE NOW</span> `invalid_action`
  Invalid-action event. Directly weighted by `w_invalid_action`.
- <span style="color:#2563eb">LIVE / OFF</span> `movement`
  Movement event. Still wired into the reward breakdown, current weight `0.0`.
- <span style="color:#2563eb">LIVE / OFF</span> `idle`
  Literal idle event. Still wired into the reward breakdown, current weight `0.0`.
- <span style="color:#16a34a">ACTIVE NOW</span> `tick_penalty`
  Fires every tick. Directly weighted by `w_tick_penalty`.
- <span style="color:#16a34a">ACTIVE NOW</span> `correct_danger_prayer`
  Correctly blocked resolved ranged/magic hit. Directly weighted by `w_correct_danger_prayer`.
- <span style="color:#16a34a">ACTIVE NOW</span> `wrong_danger_prayer`
  Wrong/no-prayer resolved ranged/magic hit. Used by both `w_wrong_danger_prayer` and `shape_wrong_prayer_penalty`.
- <span style="color:#16a34a">ACTIVE NOW</span> `attack_attempt`
  Valid attack cycle launched this tick. Directly weighted by `w_attack_attempt` and used by the non-attacking stall logic.

### Action Heads and Masks

- <span style="color:#16a34a">ACTIVE NOW</span> RL action heads
  `MOVE(17)`, `ATTACK(9)`, `PRAYER(5)`, `EAT(3)`, `DRINK(2)`.
- <span style="color:#16a34a">ACTIVE NOW</span> RL mask size
  `36` floats appended to the policy obs for PufferLib/eval.
- <span style="color:#2563eb">LIVE / OFF</span> Viewer-only path heads
  `MOVE_TARGET_X(65)` and `MOVE_TARGET_Y(65)` still exist in the backend/viewer contract, but are not part of the 5-head RL policy.
- <span style="color:#16a34a">ACTIVE NOW</span> Full backend mask size
  `166` floats when the viewer-only heads are included.
- <span style="color:#16a34a">ACTIVE NOW</span> Full backend buffer size
  `106` policy obs + `19` reward features + `166` action-mask floats = `291` floats total.

## Analytics Surface

### Core Episode Metrics

- <span style="color:#16a34a">ACTIVE NOW</span> `score`
  Fraction of completed episodes that ended in full cave completion.
- <span style="color:#16a34a">ACTIVE NOW</span> `cave_complete_rate`
  Explicit alias of `score`.
- <span style="color:#16a34a">ACTIVE NOW</span> `episode_return`
  Average shaped return per completed episode.
- <span style="color:#16a34a">ACTIVE NOW</span> `episode_length`
  Average completed-episode length in ticks.
- <span style="color:#16a34a">ACTIVE NOW</span> `wave_reached`
  Average terminal wave reached.
- <span style="color:#16a34a">ACTIVE NOW</span> `max_wave`
  All-time highest terminal wave reached in the run.
- <span style="color:#16a34a">ACTIVE NOW</span> `most_npcs_slayed`
  All-time max NPC kills in a single episode.

### Fight Caves Analytics

- <span style="color:#16a34a">ACTIVE NOW</span> Aggregation rule
  `max_wave` and `most_npcs_slayed` are all-time maxima; the other Fight Caves analytics are windowed averages over completed episodes since the last flush.
- <span style="color:#16a34a">ACTIVE NOW</span> `prayer_uptime`
  Fraction of episode ticks with any protection prayer active.
- <span style="color:#16a34a">ACTIVE NOW</span> `prayer_uptime_melee`
  Fraction of episode ticks with Protect from Melee active.
- <span style="color:#16a34a">ACTIVE NOW</span> `prayer_uptime_range`
  Fraction of episode ticks with Protect from Missiles active.
- <span style="color:#16a34a">ACTIVE NOW</span> `prayer_uptime_magic`
  Fraction of episode ticks with Protect from Magic active.
- <span style="color:#16a34a">ACTIVE NOW</span> `correct_prayer`
  Average count of resolved hits correctly blocked by the active prayer.
- <span style="color:#16a34a">ACTIVE NOW</span> `wrong_prayer_hits`
  Average count of resolved hits taken with the wrong prayer active.
- <span style="color:#16a34a">ACTIVE NOW</span> `no_prayer_hits`
  Average count of resolved hits taken with no protection prayer active.
- <span style="color:#16a34a">ACTIVE NOW</span> `prayer_switches`
  Average number of prayer changes per episode.
- <span style="color:#16a34a">ACTIVE NOW</span> `damage_blocked`
  Average total pre-prayer damage prevented by correct prayer blocks.
- <span style="color:#16a34a">ACTIVE NOW</span> `dmg_taken_avg`
  Average total player damage taken per episode.
- <span style="color:#16a34a">ACTIVE NOW</span> `attack_when_ready_rate`
  Fraction of attack-ready ticks where a real attack was launched.
- <span style="color:#16a34a">ACTIVE NOW</span> `pots_used`
  Average prayer doses consumed per episode.
- <span style="color:#16a34a">ACTIVE NOW</span> `avg_prayer_on_pot`
  Average prayer-bar fraction immediately before each potion dose.
- <span style="color:#16a34a">ACTIVE NOW</span> `pots_wasted`
  Average count of potion doses that over-restored prayer.
- <span style="color:#16a34a">ACTIVE NOW</span> `food_eaten`
  Average food uses per episode.
- <span style="color:#16a34a">ACTIVE NOW</span> `avg_hp_on_food`
  Average HP-bar fraction immediately before each food use.
- <span style="color:#16a34a">ACTIVE NOW</span> `food_wasted`
  Average count of food uses that overhealed.
- <span style="color:#16a34a">ACTIVE NOW</span> `tokxil_melee_ticks`
  Average count of ticks with at least one Tok-Xil in melee range.
- <span style="color:#16a34a">ACTIVE NOW</span> `ketzek_melee_ticks`
  Average count of ticks with at least one Ket-Zek in melee range.
- <span style="color:#16a34a">ACTIVE NOW</span> `reached_wave_30`
  Fraction of completed episodes in the log window that reached wave `30+`.
- <span style="color:#16a34a">ACTIVE NOW</span> `cleared_wave_30`
  Fraction of completed episodes in the log window that actually cleared wave `30`.
- <span style="color:#16a34a">ACTIVE NOW</span> `reached_wave_31`
  Fraction of completed episodes in the log window that reached wave `31+`.
- <span style="color:#16a34a">ACTIVE NOW</span> `reached_wave_63`
  Fraction of completed episodes in the log window that reached Jad wave.
- <span style="color:#16a34a">ACTIVE NOW</span> `jad_kill_rate`
  Fraction of completed episodes in the log window where Jad died at any point.

### Trainer / PPO Diagnostics

- <span style="color:#16a34a">ACTIVE NOW</span> `SPS`
  Steps per second.
- <span style="color:#16a34a">ACTIVE NOW</span> `entropy`
  Policy entropy.
- <span style="color:#16a34a">ACTIVE NOW</span> `clipfrac`
  Fraction of PPO updates that hit the clipping boundary.
- <span style="color:#16a34a">ACTIVE NOW</span> `value_loss`
  Critic loss.
- <span style="color:#16a34a">ACTIVE NOW</span> `policy_loss`
  Actor loss.
- <span style="color:#16a34a">ACTIVE NOW</span> `KL`
  Policy KL divergence between old and new policies during PPO updates.
- <span style="color:#16a34a">ACTIVE NOW</span> `epochs`
  Total PPO update count so far.
- <span style="color:#16a34a">ACTIVE NOW</span> `uptime`
  Wall-clock runtime.

## Removed

### Base / Vec / Train / Policy Keys

- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> None removed
  No audited INI keys in `[base]`, `[vec]`, `[train]`, or `[policy]` have been fully deleted from the current codebase.

### Environment Reward / Logic Paths

- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> `prayer_flick` shaping path
  Historical exploit-prone reward path removed from the live reward code and no longer configurable.
- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> Snapshot-timed generic danger-prayer reward timing
  Historical backend variant from the `v20` / `v20.1` line. No current config key selects it.
- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> Generic melee-inclusive danger-prayer reward coverage
  Historical backend variant. The current generic danger-prayer path is resolved-hit ranged/magic only; melee guidance now comes from other live shaping terms.

### Removed Observation Channels

- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> Removed player channels
  `player_food_timer`, `player_pot_timer`, `player_combo_timer`, `player_run_energy`, `player_is_running`, `player_ammo`, `player_def_level`, `player_rng_level`.
- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> Removed NPC channels
  `npc_size`, `npc_is_healer`, `npc_jad_telegraph`, `npc_aggro`.
- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> Removed meta channels
  `total_damage_dealt`, `total_damage_taken`, `kills_tick`.
- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> Jad telegraph backend hint path
  The agent no longer gets a dedicated Jad-only telegraph feature or logic path. Jad prayer learning now comes only through the normal pending-hit channels.

### Removed Analytics

- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> None removed
  The current logger surface has no retired metric names still emitted in parallel. Historical discussion may reference older labels like `correct_blocks`, but the live metric name is `correct_prayer`.

### Removed Run History Content

- <span style="color:#dc2626">LEGACY / NO-OP / REMOVED</span> Historical run sections moved out of this file
  All prior run narratives, comparisons, and staged-run notes now live in [run_history.md](/home/joe/projects/runescape-rl/codex3/runescape-rl/docs/run_history.md).
