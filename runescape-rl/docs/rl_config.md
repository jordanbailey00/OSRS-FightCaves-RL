# RL Configuration Reference

This file is the code-audited reference for the current Fight Caves RL surface.
Historical run-by-run configs and conclusions live in
[run_history.md](/home/joe/projects/runescape-rl/claude/runescape-rl/docs/run_history.md).

## Current Checked-In Config

The checked-in `.ini` files are:
- [runescape-rl/config/fight_caves.ini](/home/joe/projects/runescape-rl/claude/runescape-rl/config/fight_caves.ini)
- [pufferlib_4/config/fight_caves.ini](/home/joe/projects/runescape-rl/claude/pufferlib_4/config/fight_caves.ini)

Those files currently represent the live config surface. The scalar values
match the `v25.9` live recipe.

```ini
[base]
env_name = fight_caves
checkpoint_interval = 50

[env]
w_damage_dealt = 0.7
w_attack_attempt = 0.2
w_damage_taken = -0.6
w_npc_kill = 3.5
w_wave_clear = 15.0
w_jad_damage = 2.0
w_jad_kill = 150.0
w_player_death = -20.0
w_correct_jad_prayer = 2.0
w_correct_danger_prayer = 0.25
w_invalid_action = -0.1
w_tick_penalty = -0.005
shape_food_full_waste_penalty = -6.5
shape_food_waste_scale = -1.2
shape_food_no_threat_penalty = 0.0
shape_pot_full_waste_penalty = -6.5
shape_pot_waste_scale = -1.2
shape_pot_no_threat_penalty = 0.0
shape_wrong_prayer_penalty = -1.25
shape_npc_specific_prayer_bonus = 1.5
shape_npc_melee_penalty = -1.0
shape_wasted_attack_penalty = -0.1
shape_wave_stall_start = 500
shape_wave_stall_base_penalty = -0.5
shape_wave_stall_ramp_interval = 50
shape_wave_stall_cap = -2.0
shape_not_attacking_grace_ticks = 2
shape_not_attacking_penalty = -0.01
shape_kiting_reward = 1.0
shape_kiting_min_dist = 2
shape_kiting_max_dist = 10
shape_safespot_attack_reward = 1.5
shape_unnecessary_prayer_penalty = -0.2
shape_jad_heal_penalty = -0.1
shape_reach_wave_60_bonus = 0.0
shape_reach_wave_61_bonus = 0.0
shape_reach_wave_62_bonus = 0.0
shape_reach_wave_63_bonus = 0.0
shape_jad_kill_bonus = 0.0
shape_resource_threat_window = 2

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

[sweep]
metric = wave_reached
```

## Code-Only Live Behavior

Some behavior in the current recipe is not represented by `.ini` keys:

- Player ranged attacks require LOS to fire.
- Auto-approach now seeks tiles that are both in range and have LOS.
- Yt-HurKot aggro/distract flips on any landed player hit, including `0`.
- Jad healer procs feed `shape_jad_heal_penalty`.
- Positive prayer rewards are suppressed while the agent is attack-ready and
  idle for 1 full tick; they re-enable immediately on attack attempt.
- `w_correct_jad_prayer` is fully wired and pays on the resolve tick of a
  correctly blocked Jad hit.
- Wave number is already present in the observation metadata as
  `FC_OBS_META_WAVE`.

## Config Surface

### `[base]`

- `env_name`
  Environment selector. Must remain `fight_caves`.
- `checkpoint_interval`
  Trainer checkpoint cadence.

### `[env]` reward weights

- `w_damage_dealt`
  Dense reward for landed damage.
- `w_attack_attempt`
  Dense reward for launching a real attack cycle.
- `w_damage_taken`
  Dense penalty on damage taken. Current reward logic squares normalized damage
  before applying this weight.
- `w_npc_kill`
  Per-NPC kill reward.
- `w_wave_clear`
  Wave-clear reward. Current logic scales it by cleared wave number.
- `w_jad_damage`
  Extra dense reward for damaging Jad.
- `w_jad_kill`
  Combined Jad kill + cave complete reward. Jad death immediately ends the
  episode (healers are despawned). Replaces the old separate `w_jad_kill` +
  `w_cave_complete` channels.
- `w_player_death`
  Terminal death penalty.
- `w_correct_jad_prayer`
  Jad-specific positive reward on a correctly blocked Jad hit. Pays on resolve
  tick, not on tell tick or prayer-lock tick.
- `w_correct_danger_prayer`
  Generic positive reward on a correctly blocked ranged or magic hit.
- `w_invalid_action`
  Penalty for masked / invalid actions.
- `w_tick_penalty`
  Constant time-pressure penalty each tick.

### `[env]` shaping

- `shape_food_full_waste_penalty`
  Flat penalty for eating at full HP.
- `shape_food_waste_scale`
  Proportional overheal waste penalty.
- `shape_food_no_threat_penalty`
  Additional food-use penalty when there is no imminent threat.
- `shape_pot_full_waste_penalty`
  Flat penalty for drinking at full prayer.
- `shape_pot_waste_scale`
  Proportional overrestore waste penalty.
- `shape_pot_no_threat_penalty`
  Additional potion-use penalty when there is no active threat.
- `shape_wrong_prayer_penalty`
  Shape penalty for wrong/no-prayer resolved hits.
- `shape_npc_specific_prayer_bonus`
  Extra block reward for key NPCs:
  Tok-Xil, Ket-Zek, and melee cave NPCs in the special set.
- `shape_npc_melee_penalty`
  Pressure penalty while melee threats are actively pressuring the player.
- `shape_wasted_attack_penalty`
  Penalty for sitting attack-ready and failing to use the cycle.
- `shape_wave_stall_start`
  Tick threshold before stall penalties begin.
- `shape_wave_stall_base_penalty`
  Base stall penalty after `shape_wave_stall_start`.
- `shape_wave_stall_ramp_interval`
  Extra stall ramp every N ticks after the start threshold.
- `shape_wave_stall_cap`
  Cap on stall penalty magnitude.
- `shape_not_attacking_grace_ticks`
  Number of attack-ready idle ticks tolerated before `shape_not_attacking_penalty`
  begins.
- `shape_not_attacking_penalty`
  Penalty applied after the grace window while attack-ready and idle.
- `shape_kiting_reward`
  Reward for dealing damage while inside the configured kiting band.
- `shape_kiting_min_dist`
  Lower bound of the rewarded kiting band.
- `shape_kiting_max_dist`
  Upper bound of the rewarded kiting band.
- `shape_safespot_attack_reward`
  Reward for attacking when no NPC is adjacent (distance <= 1). Fires on any
  attack where the player has no NPC in melee range. Rewards both safespotting
  behind terrain and kiting at range.
- `shape_unnecessary_prayer_penalty`
  Penalty for keeping protection prayer on with no active threat.
- `shape_jad_heal_penalty`
  Penalty per healer proc that successfully heals Jad.
- `shape_reach_wave_60_bonus`
  Extra reward on the wave-transition tick when the next active wave becomes 60.
  Current live value is `0.0`.
- `shape_reach_wave_61_bonus`
  Extra reward on the wave-transition tick when the next active wave becomes 61.
  Current live value is `0.0`.
- `shape_reach_wave_62_bonus`
  Extra reward on the wave-transition tick when the next active wave becomes 62.
  Current live value is `0.0`.
- `shape_reach_wave_63_bonus`
  Extra reward on the wave-transition tick when the next active wave becomes 63.
- `shape_jad_kill_bonus`
  Extra shaping reward on the tick Jad dies, in addition to `w_jad_kill`.
- `shape_resource_threat_window`
  Lookahead window used by food/potion threat checks.

### `[vec]`

- `total_agents`
  Number of simultaneous environments.
- `num_buffers`
  Number of rollout buffers.

### `[train]`

- `total_timesteps`
  Full training budget.
- `learning_rate`
  PPO optimizer learning rate.
- `anneal_lr`
  Whether to decay LR over time.
- `gamma`
  Discount factor.
- `gae_lambda`
  GAE smoothing factor.
- `clip_coef`
  PPO clip coefficient.
- `vf_coef`
  Value-loss weight.
- `ent_coef`
  Entropy bonus.
- `max_grad_norm`
  Gradient clip.
- `horizon`
  Rollout length.
- `minibatch_size`
  PPO minibatch size.

### `[policy]`

- `hidden_size`
  Width of each hidden layer.
- `num_layers`
  Number of hidden layers.

### `[sweep]`

- `metric`
  The W&B / sweep target metric used by sweep tooling. Current value:
  `wave_reached`.

## Live Analytics Surface

These are the Fight Caves metrics still emitted by the training binding and
viewer episode summary:

- `episode_length`
- `wave_reached`
- `max_wave`
- `most_npcs_slayed`
- `prayer_uptime_melee`
- `prayer_uptime_range`
- `prayer_uptime_magic`
- `correct_prayer`
- `wrong_prayer_hits`
- `no_prayer_hits`
- `prayer_switches`
- `damage_blocked`
- `dmg_taken_avg`
- `attack_when_ready_rate`
- `pots_used`
- `avg_prayer_on_pot`
- `food_eaten`
- `avg_hp_on_food`
- `food_wasted`
- `pots_wasted`
- `tokxil_melee_ticks`
- `ketzek_melee_ticks`
- `max_wave_ticks`
- `max_wave_ticks_wave`
- `reached_wave_63`
- `jad_kill_rate`

Historical runs in `run_history.md` may still mention older knobs and metrics
where needed to preserve what those runs actually used or reported.
