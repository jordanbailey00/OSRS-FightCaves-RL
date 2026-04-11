# RL Configuration History

Tracks every training config change with results and reasoning.
Current config is at the top. Older runs below.

Formatting note:
- recent sections now include `Exact active config` snapshots
- these list all non-zero training-shaping values plus semantically important
  zero-valued keys when the zero itself materially changes behavior
  (for example `shape_low_prayer_pot_threshold = 0.0` or `load_model_path = null`)

---

## v21.3 (2026-04-10, planned)

Actual run:
- TBD

Goal:
- return to the `v21.2` learning recipe as the planning baseline
- keep `v21.2` as the clean reference point while adding only a very narrow
  prayer-potion rescue signal
- use this as the fallback branch if the `v22.x` line does not recover
- keep the corrected `Masori (f) + TBow` combat model and `7/10` kiting band
  from `v22.1`

Config versus `v21.2`:
- same baseline except for one narrow low-prayer potion rule
- same PPO/train recipe
- same `5B` budget
- same backend structure
- same reward weights and shaping terms
- same vectorization / minibatching
- same no-curriculum setup
- same intended learning behavior aside from emergency prayer-pot use
- keep the `v22.1` combat model and kiting distance

Exact starting config:
- reward weights:
  - `w_damage_dealt = 0.5`
  - `w_attack_attempt = 0.1`
  - `w_damage_taken = -0.6`
  - `w_npc_kill = 3.0`
  - `w_wave_clear = 10.0`
  - `w_jad_damage = 2.0`
  - `w_jad_kill = 50.0`
  - `w_player_death = -20.0`
  - `w_cave_complete = 100.0`
  - `w_correct_danger_prayer = 0.25`
  - `w_wrong_danger_prayer = 0.0`
  - `w_invalid_action = -0.1`
  - `w_movement = 0.0`
  - `w_idle = 0.0`
  - `w_tick_penalty = -0.005`
- shaping terms:
  - `shape_food_full_waste_penalty = -6.5`
  - `shape_food_waste_scale = -1.2`
  - `shape_food_safe_hp_threshold = 1.0`
  - `shape_food_no_threat_penalty = 0.0`
  - `shape_pot_full_waste_penalty = -6.5`
  - `shape_pot_waste_scale = -1.2`
  - `shape_pot_safe_prayer_threshold = 1.0`
  - `shape_pot_no_threat_penalty = 0.0`
  - `shape_low_prayer_pot_threshold = 0.05`
  - `shape_low_prayer_no_pot_penalty = -0.01`
  - `shape_low_prayer_pot_reward = 0.0`
  - `shape_wrong_prayer_penalty = -1.25`
  - `shape_npc_specific_prayer_bonus = 1.5`
  - `shape_npc_melee_penalty = -0.3`
  - `shape_wasted_attack_penalty = -0.1`
  - `shape_wave_stall_start = 500`
  - `shape_wave_stall_base_penalty = -0.5`
  - `shape_wave_stall_ramp_interval = 50`
  - `shape_wave_stall_cap = -2.0`
  - `shape_not_attacking_grace_ticks = 2`
  - `shape_not_attacking_penalty = -0.01`
  - `shape_kiting_reward = 1.0`
  - `shape_kiting_min_dist = 7`
  - `shape_kiting_max_dist = 10`
  - `shape_unnecessary_prayer_penalty = -0.2`
  - `shape_resource_threat_window = 2`
- runtime:
  - `total_agents = 4096`
  - `num_buffers = 2`
  - `total_timesteps = 5_000_000_000`
  - `learning_rate = 0.0003`
  - `anneal_lr = 0`
  - `gamma = 0.999`
  - `gae_lambda = 0.95`
  - `clip_coef = 0.2`
  - `vf_coef = 0.5`
  - `ent_coef = 0.01`
  - `max_grad_norm = 0.5`
  - `horizon = 256`
  - `minibatch_size = 4096`
  - `hidden_size = 256`
  - `num_layers = 2`

Current planning stance:
- selected tweak set is intentionally tiny:
  - `shape_low_prayer_pot_threshold = 0.05`
  - `shape_low_prayer_no_pot_penalty = -0.01`
  - `shape_low_prayer_pot_reward = 0.0`
- no checkpoint warm-start
- keep the `v22.1` combat model and `7/10` kiting band
- do not fold in the `v22` / `v22.1` reward retune or obs changes

---

## v22.1 (2026-04-11, completed)

Actual run:
- `721zk2cg`
- local run log:
  - [721zk2cg.json](/home/joe/projects/runescape-rl/codex3/pufferlib_4/logs/fight_caves/721zk2cg.json)

Status:
- completed to the full `5B` budget

Goal:
- recover from the `v22` collapse without giving up the corrected `Masori (f) +
  TBow` combat model
- remove the added per-NPC identity observation from `v22`
- keep the `v22` low-prayer shaping path, but retune it to exact zero prayer
- increase combat-tempo / conversion rewards
- widen the rewarded kiting band for the corrected TBow range
- warm-start from a strong `v21.2` checkpoint

Config versus `v22`:
- same PPO/train recipe
- same `5B` budget
- same backend structure
- same viewer / reward-parity / analytics infrastructure
- same corrected `Masori (f) + TBow` player combat model
- intended learning-behavior changes were:
  - remove the added per-NPC `npc_type` observation channel
  - move the low-prayer shaping threshold from `59` prayer to exact `0`
  - keep `shape_low_prayer_no_pot_penalty = -0.01`
  - keep `shape_low_prayer_pot_reward = 0.15`
  - increase:
    - `w_damage_dealt: 0.5 -> 0.7`
    - `w_attack_attempt: 0.1 -> 0.2`
    - `w_npc_kill: 3.0 -> 3.5`
    - `w_wave_clear: 10.0 -> 15.0`
  - widen the kiting band:
    - `shape_kiting_min_dist: 5 -> 7`
    - `shape_kiting_max_dist: 7 -> 10`
  - warm-start from:
    - `/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/u58coupx/0000001311768576.bin`

Exact active config:
- run setup: `load_model_path=/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/u58coupx/0000001311768576.bin`, corrected `Masori (f) + TBow` combat model, `policy_obs=106`, `puffer_obs=142`
- reward weights: `w_damage_dealt=0.7`, `w_attack_attempt=0.2`, `w_damage_taken=-0.6`, `w_npc_kill=3.5`, `w_wave_clear=15.0`, `w_jad_damage=2.0`, `w_jad_kill=50.0`, `w_player_death=-20.0`, `w_cave_complete=100.0`, `w_correct_danger_prayer=0.25`, `w_invalid_action=-0.1`, `w_tick_penalty=-0.005`
- shaping: `shape_food_full_waste_penalty=-6.5`, `shape_food_waste_scale=-1.2`, `shape_food_safe_hp_threshold=1.0`, `shape_pot_full_waste_penalty=-6.5`, `shape_pot_waste_scale=-1.2`, `shape_pot_safe_prayer_threshold=1.0`, `shape_low_prayer_pot_threshold=0.0`, `shape_low_prayer_no_pot_penalty=-0.01`, `shape_low_prayer_pot_reward=0.15`, `shape_wrong_prayer_penalty=-1.25`, `shape_npc_specific_prayer_bonus=1.5`, `shape_npc_melee_penalty=-0.3`, `shape_wasted_attack_penalty=-0.1`, `shape_wave_stall_start=500`, `shape_wave_stall_base_penalty=-0.5`, `shape_wave_stall_ramp_interval=50`, `shape_wave_stall_cap=-2.0`, `shape_not_attacking_grace_ticks=2`, `shape_not_attacking_penalty=-0.01`, `shape_kiting_reward=1.0`, `shape_kiting_min_dist=7`, `shape_kiting_max_dist=10`, `shape_unnecessary_prayer_penalty=-0.2`, `shape_resource_threat_window=2`
- runtime: `total_agents=4096`, `num_buffers=2`, `total_timesteps=5_000_000_000`, `learning_rate=0.0003`, `gamma=0.999`, `gae_lambda=0.95`, `clip_coef=0.2`, `vf_coef=0.5`, `ent_coef=0.01`, `max_grad_norm=0.5`, `horizon=256`, `minibatch_size=4096`, `hidden_size=256`, `num_layers=2`

Results (`721zk2cg`):
- completed normally
- final logged trainer step:
  - `4,999,610,368 / 5,000,000,000`
- runtime:
  - `2634s`
- throughput:
  - `1.83M SPS`

Final metrics:
- `score = 0.0217`
- `cave_complete_rate = 0.0217`
- `wave_reached = 61.97`
- `max_wave = 63`
- `most_npcs_slayed = 325`
- `episode_return = 32277.45`
- `episode_length = 8581.45`
- `reached_wave_30 = 1.0`
- `cleared_wave_30 = 1.0`
- `reached_wave_31 = 1.0`
- `reached_wave_63 = 0.3582`
- `jad_kill_rate = 0.0149`
- `prayer_uptime = 0.7345`
- `prayer_uptime_melee = 0.0322`
- `prayer_uptime_range = 0.1727`
- `prayer_uptime_magic = 0.5296`
- `correct_prayer = 1550.93`
- `wrong_prayer_hits = 296.24`
- `no_prayer_hits = 29.76`
- `damage_blocked = 187217.02`
- `dmg_taken_avg = 6445.69`
- `prayer_switches = 651.28`
- `attack_when_ready_rate = 0.7500`
- `pots_used = 31.36`
- `avg_prayer_on_pot = 0.3760`
- `food_eaten = 8.01`
- `avg_hp_on_food = 0.5725`
- `food_wasted = 1.33`
- `pots_wasted = 3.07`
- `tokxil_melee_ticks = 3.85`
- `ketzek_melee_ticks = 5.36`

Key progression points:
- early climb was solid but not special through `~1.5B`:
  - `1.248B`: `wave_reached = 33.5`, `episode_return = 9219.3`
  - `1.516B`: `wave_reached = 35.0`, `episode_return = 10056.0`
- the major jump happened between `1.52B` and `1.82B`:
  - `1.818B`: `wave_reached = 54.2`, `episode_return = 24812.8`
- frontier competence arrived soon after:
  - `2.072B`: `wave_reached = 59.6`, `episode_return = 28984.9`
  - `2.284B`: `wave_reached = 61.4`, `episode_return = 30993.2`
- unlike `v22` and `v21.2`, there was no catastrophic late collapse
- the run stabilized in a `61-62.5` wave regime for the rest of training
- best sampled window was late:
  - `4.127B`: `wave_reached = 62.3`, `episode_return = 32340.6`
  - `4.627B`: `wave_reached = 62.5`, `episode_return = 32827.5`
- nearest eval checkpoint to the best sampled window:
  - `/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/721zk2cg/0000004614782976.bin`

Comparison to `v22` (`7vuw9jy8`):
- `score: 0.0217 vs 0.0`
- `cave_complete_rate: 0.0217 vs 0.0`
- `wave_reached: 61.97 vs 27.15`
- `max_wave: 63 vs 33`
- `most_npcs_slayed: 325 vs 123`
- `episode_return: 32277.4 vs 4338.1`
- `episode_length: 8581 vs 4386`
- `reached_wave_30: 1.0 vs 0.0648`
- `cleared_wave_30: 1.0 vs 0.0`
- `reached_wave_31: 1.0 vs 0.0`
- `reached_wave_63: 0.3582 vs 0.0`
- `jad_kill_rate: 0.0149 vs 0.0`
- `prayer_uptime: 0.7345 vs 0.9758`
- `prayer_uptime_magic: 0.5296 vs 0.0`
- `attack_when_ready_rate: 0.7500 vs 0.4557`
- `pots_used: 31.36 vs 31.96`
- `avg_prayer_on_pot: 0.3760 vs 0.6929`
- `food_eaten: 8.01 vs 20.0`
- `food_wasted: 1.33 vs 17.09`

Comparison to `v21.2` (`u58coupx`):
- `score: 0.0217 vs 0.0`
- `cave_complete_rate: 0.0217 vs 0.0`
- `wave_reached: 61.97 vs 48.65`
- `max_wave: 63 vs 63`
- `most_npcs_slayed: 325 vs 272`
- `episode_return: 32277.4 vs 14307.1`
- `episode_length: 8581 vs 6493`
- `reached_wave_30: 1.0 vs 0.9733`
- `cleared_wave_30: 1.0 vs 0.9733`
- `reached_wave_31: 1.0 vs 0.9733`
- `reached_wave_63: 0.3582 vs 0.0067`
- `jad_kill_rate: 0.0149 vs 0.0`
- `prayer_uptime: 0.7345 vs 0.2638`
- `prayer_uptime_magic: 0.5296 vs 0.1228`
- `correct_prayer: 1550.9 vs 1143.9`
- `wrong_prayer_hits: 296.2 vs 98.4`
- `no_prayer_hits: 29.8 vs 155.6`
- `damage_blocked: 187217 vs 139493`
- `dmg_taken_avg: 6445.7 vs 6818.0`
- `prayer_switches: 651.3 vs 2284.7`
- `attack_when_ready_rate: 0.7500 vs 0.6329`
- `pots_used: 31.36 vs 0.02`
- `food_eaten: 8.01 vs 15.89`
- `food_wasted: 1.33 vs 4.33`

Comparison to `v21` (`v4ekkk3z`):
- `score: 0.0217 vs 0.0`
- `wave_reached: 61.97 vs 61.40`
- `max_wave: 63 vs 63`
- `most_npcs_slayed: 325 vs 273`
- `episode_return: 32277.4 vs 22940.3`
- `episode_length: 8581 vs 10027`
- `prayer_uptime: 0.7345 vs 0.7345`
- `prayer_uptime_magic: 0.5296 vs 0.5068`
- `correct_prayer: 1550.9 vs 2173.5`
- `wrong_prayer_hits: 296.2 vs 281.5`
- `no_prayer_hits: 29.8 vs 37.5`
- `damage_blocked: 187217 vs 277395`
- `dmg_taken_avg: 6445.7 vs 7028.8`
- `prayer_switches: 651.3 vs 2725.4`
- `attack_when_ready_rate: 0.7500 vs 0.6226`
- `pots_used: 31.36 vs 32.0`
- `avg_prayer_on_pot: 0.3760 vs 0.5754`
- `food_eaten: 8.01 vs 16.94`
- `food_wasted: 1.33 vs 9.02`
- `tokxil_melee_ticks: 3.85 vs 10.76`
- `ketzek_melee_ticks: 5.36 vs 18.08`

Interpretation:
- `v22.1` is a decisive recovery from `v22`
- it does not look like the `v22` prayer-camping collapse at all
- it also clearly beats `v21.2`
- and on the logged metrics that matter most, it likely becomes the new best
  branch so far:
  - first non-zero `cave_complete_rate`
  - first non-zero `jad_kill_rate`
  - sustained `61-62.5` wave regime
  - full wave-30+ stability
- the potion issue is effectively solved in the narrow sense that the agent now
  absolutely knows how to consume prayer pots
- however, it did not learn conservative potion timing:
  - `pots_used = 31.36`
  - `avg_prayer_on_pot = 0.376`
- the food side is much cleaner:
  - only `8.0` food eaten on average
  - much lower waste than both `v22` and `v21`
- the prayer profile is notable:
  - total prayer uptime is almost identical to `v21`
  - magic prayer dominates the uptime mix
  - prayer switches are far lower than `v21`
- that suggests a different high-level policy style than `v21`:
  - more tempo / damage conversion
  - fewer switches
  - fewer panic resources
  - still enough prayer correctness to reach and sometimes finish Jad

Important observation:
- there is a small analytics inconsistency:
  - `cave_complete_rate = 0.0217`
  - `jad_kill_rate = 0.0149`
- logically, cave completion should imply Jad kill
- so `jad_kill_rate` appears to be undercounting relative to terminal cave
  completions in this run
- the run result itself is still clear because `score` / `cave_complete_rate`
  is authoritative for clears, but the new `jad_kill_rate` metric should be
  audited before relying on it as ground truth

Most important new insight:
- the `v22` recovery cannot be attributed cleanly to one change
- `v22.1` changed five things at once relative to `v22`:
  - removed `npc_type` obs
  - changed low-prayer threshold to zero
  - increased combat-conversion rewards
  - widened kiting distance
  - warm-started from `v21.2`
- so this is not a clean ablation
- still, the result is strong enough that the branch itself is worth keeping
- the biggest practical conclusion is:
  - do not abandon the `v22.x` line yet

Recommendations:
- keep `v22.1` as the new working branch
- do not jump straight to `v21.3` as the mainline follow-up
- if the next goal is optimization rather than fallback validation, run a small
  ablation sweep around `v22.1` first
- if the next goal is simplicity / safety, only then use `v21.3` as the
  fallback control branch

Bottom line:
- `v22.1` is a considerable success
- it fully recovers the `v22` collapse
- it beats `v21.2`
- and it likely beats `v21` as the best overall run family so far, because it
  is the first branch with non-zero cave completion

---

## v22 (2026-04-10, completed)

Actual run:
- `7vuw9jy8`
- W&B run name:
  - `good-planet-129`
- local run log:
  - [7vuw9jy8.json](/home/joe/projects/runescape-rl/codex3/pufferlib_4/logs/fight_caves/7vuw9jy8.json)

Status:
- completed to the full `5B` budget

Goal:
- keep the `v21` / `v21.2` PPO stack and overall reward recipe
- fix the player-side combat-model mismatch for the active `Masori (f) + TBow`
  loadout
- give the policy explicit NPC identity in obs so late-wave prayer decisions are
  less slot-order-dependent
- make prayer-potion timing directly teachable without adding heavy or invasive
  shaping

Config versus `v21.2`:
- same PPO/train recipe
- same `5B` budget
- same backend structure
- same viewer / reward-parity / analytics infrastructure
- intended learning-behavior changes were:
  - correct the active player weapon/loadout model
  - add one per-NPC `npc_type` observation channel
  - add a lightweight low-prayer potion teaching signal

Exact differences versus `v21.2`:
- player weapon model:
  - `weapon_kind`: generic ranged -> `Twisted bow`
  - `weapon_range`: `7 -> 10`
  - `weapon_speed`: unchanged at `5` on rapid
  - player ranged attack roll and max-hit path now use a lightweight TBow
    target-magic scaling instead of the old generic ranged formula
  - TBow scaling is simplified and localized:
    - uses target NPC magic level only
    - caps target magic level at `250`
    - does not add a deeper full target-magic-accuracy model
- active loadout-B hardcoded player stats corrected:
  - `def_stab: 115 -> 116`
  - `def_slash: 104 -> 106`
  - `def_crush: 129 -> 129` (unchanged)
  - `def_magic: 148 -> 150`
  - `def_ranged: 111 -> 121`
  - `prayer_bonus: 9 -> 6`
  - offensive totals remain:
    - `ranged_atk = 215`
    - `ranged_str = 99`
- policy observation contract:
  - add explicit `npc_type` per visible NPC slot
  - `FC_OBS_NPC_STRIDE: 10 -> 11`
  - `FC_POLICY_OBS_SIZE: 106 -> 114`
  - Puffer/eval policy input:
    - `142 -> 150` floats (`policy obs + mask5`)
  - full backend buffer:
    - `291 -> 299` floats
- reward shaping:
  - keep all existing `v21.2` weights and shaping terms
  - add three new live shaping keys:
    - `shape_low_prayer_pot_threshold = 0.60`
    - `shape_low_prayer_no_pot_penalty = -0.01`
    - `shape_low_prayer_pot_reward = 0.15`

Exact active config:
- run setup: `load_model_path=null`, corrected `Masori (f) + TBow` combat model, per-NPC `npc_type` obs enabled, `policy_obs=114`, `puffer_obs=150`
- reward weights: `w_damage_dealt=0.5`, `w_attack_attempt=0.1`, `w_damage_taken=-0.6`, `w_npc_kill=3.0`, `w_wave_clear=10.0`, `w_jad_damage=2.0`, `w_jad_kill=50.0`, `w_player_death=-20.0`, `w_cave_complete=100.0`, `w_correct_danger_prayer=0.25`, `w_invalid_action=-0.1`, `w_tick_penalty=-0.005`
- shaping: `shape_food_full_waste_penalty=-6.5`, `shape_food_waste_scale=-1.2`, `shape_food_safe_hp_threshold=1.0`, `shape_pot_full_waste_penalty=-6.5`, `shape_pot_waste_scale=-1.2`, `shape_pot_safe_prayer_threshold=1.0`, `shape_low_prayer_pot_threshold=0.60`, `shape_low_prayer_no_pot_penalty=-0.01`, `shape_low_prayer_pot_reward=0.15`, `shape_wrong_prayer_penalty=-1.25`, `shape_npc_specific_prayer_bonus=1.5`, `shape_npc_melee_penalty=-0.3`, `shape_wasted_attack_penalty=-0.1`, `shape_wave_stall_start=500`, `shape_wave_stall_base_penalty=-0.5`, `shape_wave_stall_ramp_interval=50`, `shape_wave_stall_cap=-2.0`, `shape_not_attacking_grace_ticks=2`, `shape_not_attacking_penalty=-0.01`, `shape_kiting_reward=1.0`, `shape_kiting_min_dist=5`, `shape_kiting_max_dist=7`, `shape_unnecessary_prayer_penalty=-0.2`, `shape_resource_threat_window=2`
- runtime: `total_agents=4096`, `num_buffers=2`, `total_timesteps=5_000_000_000`, `learning_rate=0.0003`, `gamma=0.999`, `gae_lambda=0.95`, `clip_coef=0.2`, `vf_coef=0.5`, `ent_coef=0.01`, `max_grad_norm=0.5`, `horizon=256`, `minibatch_size=4096`, `hidden_size=256`, `num_layers=2`

Results (`7vuw9jy8`):
- completed normally
- final logged trainer step:
  - `4,991,221,760 / 5,000,000,000`
- runtime:
  - `2446s`
- throughput:
  - `1.95M SPS`

Final metrics:
- `score = 0.0`
- `cave_complete_rate = 0.0`
- `wave_reached = 27.15`
- `max_wave = 33`
- `most_npcs_slayed = 123`
- `episode_return = 4338.14`
- `episode_length = 4385.76`
- `reached_wave_30 = 0.0648`
- `cleared_wave_30 = 0.0`
- `reached_wave_31 = 0.0`
- `reached_wave_63 = 0.0`
- `jad_kill_rate = 0.0`
- `prayer_uptime = 0.9758`
- `prayer_uptime_melee = 0.4382`
- `prayer_uptime_range = 0.5376`
- `prayer_uptime_magic = 0.0`
- `correct_prayer = 1400.37`
- `wrong_prayer_hits = 179.28`
- `no_prayer_hits = 22.09`
- `damage_blocked = 25648.89`
- `dmg_taken_avg = 3090.27`
- `prayer_switches = 616.13`
- `attack_when_ready_rate = 0.4557`
- `pots_used = 31.96`
- `avg_prayer_on_pot = 0.6929`
- `pots_wasted = 14.27`
- `food_eaten = 20.0`
- `avg_hp_on_food = 0.9068`
- `food_wasted = 17.09`
- `tokxil_melee_ticks = 19.89`
- `ketzek_melee_ticks = 0.0`

Key progression points:
- sampled `wave_reached >= 20` by `218M`
- sampled `wave_reached >= 25` by `218M`
- sampled `wave_reached >= 28` by `218M`
- sampled `wave_reached >= 29` by `455M`
- never sampled `wave_reached >= 30`
- strongest window was roughly `1.01B-1.49B`:
  - `1.013B`: `wave_reached = 29.9`, `episode_return = 5507.6`
  - `1.491B`: `wave_reached = 29.2`, `episode_return = 5514.9`
- after `~3.0B`, the run decayed
- the sharp collapse arrived around `4.11B`:
  - sampled `wave_reached = 25.6`
  - sampled `episode_return = 3793.0`
- nearest eval checkpoint to the best sampled average-wave window:
  - `/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/7vuw9jy8/0000001012924416.bin`

Comparison to `v21.2` (`u58coupx`):
- `score: 0.0 vs 0.0`
- `cave_complete_rate: 0.0 vs 0.0`
- `wave_reached: 27.15 vs 48.65`
- `max_wave: 33 vs 63`
- `most_npcs_slayed: 123 vs 272`
- `episode_return: 4338.1 vs 14307.1`
- `episode_length: 4386 vs 6493`
- `reached_wave_30: 0.0648 vs 0.9733`
- `cleared_wave_30: 0.0 vs 0.9733`
- `reached_wave_31: 0.0 vs 0.9733`
- `reached_wave_63: 0.0 vs 0.0067`
- `jad_kill_rate: 0.0 vs 0.0`
- `prayer_uptime: 0.976 vs 0.264`
- `prayer_uptime_melee: 0.438 vs 0.033`
- `prayer_uptime_range: 0.538 vs 0.108`
- `prayer_uptime_magic: 0.000 vs 0.123`
- `correct_prayer: 1400.4 vs 1143.9`
- `wrong_prayer_hits: 179.3 vs 98.4`
- `no_prayer_hits: 22.1 vs 155.6`
- `damage_blocked: 25649 vs 139493`
- `dmg_taken_avg: 3090 vs 6818`
- `prayer_switches: 616.1 vs 2284.7`
- `attack_when_ready_rate: 0.456 vs 0.633`
- `pots_used: 32.0 vs 0.02`
- `avg_prayer_on_pot: 0.693 vs 0.0018`
- `pots_wasted: 14.27 vs 0.0`
- `food_eaten: 20.0 vs 15.9`
- `food_wasted: 17.09 vs 4.33`
- `tokxil_melee_ticks: 19.89 vs 4.79`
- `ketzek_melee_ticks: 0.0 vs 10.28`

Comparison to `v21` (`v4ekkk3z`):
- `score: 0.0 vs 0.0`
- `wave_reached: 27.15 vs 61.40`
- `max_wave: 33 vs 63`
- `most_npcs_slayed: 123 vs 273`
- `episode_return: 4338.1 vs 22940.3`
- `episode_length: 4386 vs 10027`
- `prayer_uptime: 0.976 vs 0.734`
- `prayer_uptime_melee: 0.438 vs 0.037`
- `prayer_uptime_range: 0.538 vs 0.191`
- `prayer_uptime_magic: 0.000 vs 0.507`
- `correct_prayer: 1400.4 vs 2173.5`
- `wrong_prayer_hits: 179.3 vs 281.5`
- `no_prayer_hits: 22.1 vs 37.5`
- `damage_blocked: 25649 vs 277395`
- `dmg_taken_avg: 3090 vs 7029`
- `prayer_switches: 616.1 vs 2725.4`
- `attack_when_ready_rate: 0.456 vs 0.623`
- `pots_used: 32.0 vs 32.0`
- `avg_prayer_on_pot: 0.693 vs 0.575`
- `pots_wasted: 14.27 vs 9.42`
- `food_eaten: 20.0 vs 16.94`
- `food_wasted: 17.09 vs 9.02`
- `tokxil_melee_ticks: 19.89 vs 10.76`
- `ketzek_melee_ticks: 0.0 vs 18.08`

Interpretation:
- this is a severe regression, not a minor frontier wobble
- `v22` did not preserve the `v21.2` Jad-facing ceiling at all
- instead it fell into a high-prayer, high-consumption, low-offense regime:
  - prayer almost always on
  - all prayer doses consumed
  - all food consumed
  - low prayer-switch count
  - low attack-ready rate
  - no durable post-wave-30 competence
- the behavioral signature looks much closer to the old prayer-camping
  regressions than to the `v21.2` no-pot-collapse regime
- especially important:
  - `prayer_uptime = 0.976`
  - `prayer_uptime_magic = 0.0`
  - `prayer_uptime_range = 0.538`
  - `reached_wave_30 = 0.0648`
  - `attack_when_ready_rate = 0.456`
- this is not a late-wave resource-collapse policy
- it is an early/mid-wave over-defensive policy that never builds enough tempo
  or switching competence to reach the old frontier

Most important new insight:
- the `v22` delta bundle did not fail in a neutral way
- it appears to have pushed the policy into a heavily defensive prayer-potion
  regime very early
- there are three plausible causes in the `v22` delta set:
  - corrected player combat model
  - added `npc_type` obs / larger policy input
  - low-prayer potion teaching signal
- of those, the strongest *behavioral* signature points more toward the new
  low-prayer shaping than toward the combat-model correction:
  - potion use went from near-zero in `v21.2` to essentially full-inventory
  - prayer uptime exploded upward instead of collapsing
- however, because `v22` also changed the policy-input contract from `106` to
  `114`, the added `npc_type` channel is still a valid rollback target

Recommendations:
- revert the added `npc_type` observation as `v22.1`
- keep the corrected player combat model for now
- keep the low-prayer shaping for one more rollback test only if the goal is
  to isolate the obs-contract change first
- if `v22.1` is still strongly regressed, the next primary suspect is the
  low-prayer potion shaping, not the player combat-model correction
- use the early `v22` checkpoint window for replay if needed, not the final
  checkpoint

Bottom line:
- `v22` is a considerable regression from both `v21.2` and `v21`
- it never reached wave 31 in a stable way
- it never reached Jad
- its failure mode is a high-prayer, full-resource-burn, low-tempo regime
- `v22.1` should be the clean rollback of the added `npc_type` observation
  while leaving the other `v22` changes intact

---

## v_tmp2.1 (2026-04-09, completed)

Actual run id:
- `j63y66ed`
- W&B run name:
  - `fallen-water-102`
- local run log:
  - [j63y66ed.json](/home/joe/projects/runescape-rl/codex3/v_tmp2/pufferlib_4/logs/fight_caves/j63y66ed.json)

Mainline direction:
- keep the prayer/combat backend with locked-prayer hit resolution
- keep reward-path alignment to those backend semantics
- no curriculum
- no PPO retune
- only reduce positive prayer shaping and slightly increase wrong-prayer pressure

Exact changes versus the original `v_tmp2` run (`fkhhysfd`):
- `w_correct_danger_prayer: 0.5 -> 0.25`
- `shape_wrong_prayer_penalty: -1.0 -> -1.25`
- `shape_npc_specific_prayer_bonus: 2.5 -> 1.5`

What stayed the same:
- no curriculum buckets
- same PPO/train recipe
- same damage, kill, wave-clear, Jad, melee-pressure, kiting, and stall terms
- same `4096` agents and `2.5B` step budget

Why this run mattered:
- the first `v_tmp2` run was promising on depth and stability, but clearly bad on
  prayer correctness
  - `wrong_prayer_hits = 464.25`
  - `dmg_taken_avg = 7470.13`
  - `prayer_uptime = 0.947`
- `v_tmp2.1` was the first clean test after reward alignment to locked-prayer
  backend semantics

Backend / config provenance:
- this run used the `v_tmp2` backend path, not the old pre-prayer baseline
- evidence:
  - the local log artifact lives under the `v_tmp2` PufferLib tree:
    [j63y66ed.json](/home/joe/projects/runescape-rl/codex3/v_tmp2/pufferlib_4/logs/fight_caves/j63y66ed.json)
  - that artifact records the exact unique `v_tmp2.1` values:
    - `w_correct_danger_prayer = 0.25`
    - `shape_wrong_prayer_penalty = -1.25`
    - `shape_npc_specific_prayer_bonus = 1.5`
    - `curriculum_wave = 0`
    - `curriculum_pct = 0.0`
  - the run was launched with `FORCE_BACKEND_REBUILD=1`, so the trainer backend
    was rebuilt from the `v_tmp2` source tree before training

Exact active config:
- run setup: `load_model_path=null`, `policy_obs=106`, `puffer_obs=142`
- reward weights: `w_damage_dealt=0.5`, `w_attack_attempt=0.1`, `w_damage_taken=-0.6`, `w_npc_kill=3.0`, `w_wave_clear=10.0`, `w_jad_damage=2.0`, `w_jad_kill=50.0`, `w_player_death=-20.0`, `w_cave_complete=100.0`, `w_correct_danger_prayer=0.25`, `w_invalid_action=-0.1`, `w_tick_penalty=-0.005`
- shaping: `shape_food_full_waste_penalty=-6.5`, `shape_food_waste_scale=-1.2`, `shape_food_safe_hp_threshold=1.0`, `shape_pot_full_waste_penalty=-6.5`, `shape_pot_waste_scale=-1.2`, `shape_pot_safe_prayer_threshold=1.0`, `shape_wrong_prayer_penalty=-1.25`, `shape_npc_specific_prayer_bonus=1.5`, `shape_npc_melee_penalty=-0.3`, `shape_wasted_attack_penalty=-0.1`, `shape_wave_stall_start=500`, `shape_wave_stall_base_penalty=-0.5`, `shape_wave_stall_ramp_interval=50`, `shape_wave_stall_cap=-2.0`, `shape_not_attacking_grace_ticks=2`, `shape_not_attacking_penalty=-0.01`, `shape_kiting_reward=1.0`, `shape_kiting_min_dist=5`, `shape_kiting_max_dist=7`, `shape_unnecessary_prayer_penalty=-0.2`, `shape_resource_threat_window=2`
- runtime: `total_agents=4096`, `num_buffers=2`, `total_timesteps=2_500_000_000`, `learning_rate=0.0003`, `gamma=0.999`, `gae_lambda=0.95`, `clip_coef=0.2`, `vf_coef=0.5`, `ent_coef=0.01`, `max_grad_norm=0.5`, `horizon=256`, `minibatch_size=4096`, `hidden_size=256`, `num_layers=2`

Results:
- completed normally
- final trainer step:
  - `2,499,805,184 / 2,500,000,000`
- runtime:
  - `1262.9s`
- throughput:
  - `1.98M SPS`

Final metrics:
- `episode_return = 12739.18`
- `episode_length = 6066.53`
- `wave_reached = 46.12`
- `max_wave = 62`
- `most_npcs_slayed = 270`
- `prayer_uptime = 0.665`
- `prayer_uptime_melee = 0.115`
- `prayer_uptime_range = 0.272`
- `prayer_uptime_magic = 0.278`
- `correct_prayer = 1339.86`
- `wrong_prayer_hits = 191.61`
- `no_prayer_hits = 26.04`
- `damage_blocked = 122207.55`
- `dmg_taken_avg = 5132.12`
- `attack_when_ready_rate = 0.579`
- `prayer_switches = 1212.56`
- `pots_used = 31.94`
- `avg_prayer_on_pot = 0.768`
- `pots_wasted = 23.47`
- `food_eaten = 18.83`
- `avg_hp_on_food = 0.790`
- `food_wasted = 10.65`
- `tokxil_melee_ticks = 7.04`
- `ketzek_melee_ticks = 9.39`

Key progression points:
- `reached_wave_30 >= 0.90` by `490.7M`
- `reached_wave_31 >= 0.90` by `499.1M`
- first sampled `wave_reached >= 40` by `1.74B`
- strongest sustained window was around `2.26B-2.36B`
  - sampled `wave_reached = 52.3`
  - sampled `episode_return = 16430.2`

Comparison to original `v_tmp2` (`fkhhysfd`):
- `wave_reached: 46.12 vs 44.72`
- `max_wave: 62 vs 53`
- `most_npcs_slayed: 270 vs 222`
- `reached_wave_31: 0.981 vs 0.965`
- `first reached_wave_31 >= 0.90: 499M vs 1.01B`
- `prayer_uptime: 0.665 vs 0.947`
- `wrong_prayer_hits: 191.6 vs 464.3`
- `dmg_taken_avg: 5132 vs 7470`
- `tokxil_melee_ticks: 7.04 vs 12.63`
- `attack_when_ready_rate: 0.579 vs 0.656`
- `prayer_switches: 1213 vs 1073`
- `ketzek_melee_ticks: 9.39 vs 4.71`

Interpretation versus original `v_tmp2`:
- this broke the old high-prayer, low-correctness local optimum
- the backend-aligned reward path plus smaller positive prayer rewards materially
  improved the exact metrics that had regressed
- the remaining cost is more specific:
  - higher Ket-Zek melee exposure
  - earlier prayer-potion use and much more potion waste

Comparison to `v_tmp3` (`fg029tll`):
- `wave_reached: 46.12 vs 41.17`
- `max_wave: 62 vs 60`
- `most_npcs_slayed: 270 vs 263`
- `reached_wave_31: 0.981 vs 0.944`
- `attack_when_ready_rate: 0.579 vs 0.349`
- `prayer_switches: 1213 vs 2238`
- `tokxil_melee_ticks: 7.04 vs 17.81`
- `food_wasted: 10.65 vs 18.07`
- `wrong_prayer_hits: 191.6 vs 155.1`
- `dmg_taken_avg: 5132 vs 3727`
- `first reached_wave_31 >= 0.90: 499M vs 218M`

Interpretation versus `v_tmp3`:
- `v_tmp3` still learns deep play faster and remains cleaner on prayer/damage
- `v_tmp2.1` now wins on final depth, late-run stability, and rare-event tail
- this is the first version of the locked-prayer backend that looks strictly
  viable as the forward path rather than an interesting regression branch

Most important new insight:
- the old `v_tmp2` failure mode was mostly reward-shaping around prayer, not an
  observation-space problem
- after aligning reward to the backend snapshot semantics and reducing generic
  prayer incentive, the same backend improved on:
  - `wrong_prayer_hits`
  - `dmg_taken_avg`
  - late-wave milestone timing
  - max-wave / tail depth

Bottom line:
- `v_tmp2.1` is the run that justified merging the locked-prayer backend into
  mainline code
- follow-up tuning should now focus narrowly on:
  - Ket-Zek positioning / melee leakage
  - prayer-potion timing
  - late-run stability

---

## v21.2 (2026-04-10, completed)

Actual run:
- `u58coupx`
- W&B run name:
  - `light-sky-128`
- local run log:
  - [u58coupx.json](/home/joe/projects/runescape-rl/codex3/pufferlib_4/logs/fight_caves/u58coupx.json)

Status:
- completed normally

Goal:
- restore the exact `v21` reward / PPO recipe after the `v21.1` anti-stall
  regression
- keep the non-learning tooling improvements added around `v21.1`
- test whether removing the `shape_not_attacking_*` retune restores the `v21`
  frontier

Config versus `v21.1`:
- revert the only intended reward changes:
  - `shape_not_attacking_grace_ticks: 6 -> 2`
  - `shape_not_attacking_penalty: -0.03 -> -0.01`

Config versus `v21`:
- no intended learning-behavior delta
- keep these non-learning changes:
  - reward math centralized through the shared `fc_reward.h` helper so viewer
    and training use the same breakdown
  - explicit analytics:
    - `cave_complete_rate`
    - `reached_wave_63`
    - `jad_kill_rate`
  - viewer / debug overlay parity fixes so replay inspection matches training
    reward terms and current observation labels

Exact active config:
- run setup: `load_model_path=null`, `policy_obs=106`, `puffer_obs=142`
- reward weights: `w_damage_dealt=0.5`, `w_attack_attempt=0.1`, `w_damage_taken=-0.6`, `w_npc_kill=3.0`, `w_wave_clear=10.0`, `w_jad_damage=2.0`, `w_jad_kill=50.0`, `w_player_death=-20.0`, `w_cave_complete=100.0`, `w_correct_danger_prayer=0.25`, `w_invalid_action=-0.1`, `w_tick_penalty=-0.005`
- shaping: `shape_food_full_waste_penalty=-6.5`, `shape_food_waste_scale=-1.2`, `shape_food_safe_hp_threshold=1.0`, `shape_pot_full_waste_penalty=-6.5`, `shape_pot_waste_scale=-1.2`, `shape_pot_safe_prayer_threshold=1.0`, `shape_wrong_prayer_penalty=-1.25`, `shape_npc_specific_prayer_bonus=1.5`, `shape_npc_melee_penalty=-0.3`, `shape_wasted_attack_penalty=-0.1`, `shape_wave_stall_start=500`, `shape_wave_stall_base_penalty=-0.5`, `shape_wave_stall_ramp_interval=50`, `shape_wave_stall_cap=-2.0`, `shape_not_attacking_grace_ticks=2`, `shape_not_attacking_penalty=-0.01`, `shape_kiting_reward=1.0`, `shape_kiting_min_dist=5`, `shape_kiting_max_dist=7`, `shape_unnecessary_prayer_penalty=-0.2`, `shape_resource_threat_window=2`
- runtime: `total_agents=4096`, `num_buffers=2`, `total_timesteps=5_000_000_000`, `learning_rate=0.0003`, `gamma=0.999`, `gae_lambda=0.95`, `clip_coef=0.2`, `vf_coef=0.5`, `ent_coef=0.01`, `max_grad_norm=0.5`, `horizon=256`, `minibatch_size=4096`, `hidden_size=256`, `num_layers=2`

Results (`u58coupx`):
- completed normally
- final logged trainer step:
  - `4,999,610,368 / 5,000,000,000`
- runtime:
  - `2371.9s`
- throughput:
  - `2.01M SPS`

Final metrics:
- `score = 0.0`
- `cave_complete_rate = 0.0`
- `wave_reached = 48.65`
- `max_wave = 63`
- `most_npcs_slayed = 272`
- `episode_return = 14307.14`
- `episode_length = 6492.74`
- `reached_wave_30 = 0.9733`
- `cleared_wave_30 = 0.9733`
- `reached_wave_31 = 0.9733`
- `reached_wave_63 = 0.0067`
- `jad_kill_rate = 0.0`
- `prayer_uptime = 0.2638`
- `prayer_uptime_melee = 0.0331`
- `prayer_uptime_range = 0.1079`
- `prayer_uptime_magic = 0.1228`
- `correct_prayer = 1143.94`
- `wrong_prayer_hits = 98.37`
- `no_prayer_hits = 155.63`
- `damage_blocked = 139492.77`
- `dmg_taken_avg = 6817.96`
- `prayer_switches = 2284.72`
- `attack_when_ready_rate = 0.6329`
- `pots_used = 0.02`
- `avg_prayer_on_pot = 0.0018`
- `pots_wasted = 0.0`
- `food_eaten = 15.89`
- `avg_hp_on_food = 0.6149`
- `food_wasted = 4.33`
- `tokxil_melee_ticks = 4.79`
- `ketzek_melee_ticks = 10.28`

Key progression points:
- sampled `wave_reached >= 30` by `222.3M`
- sampled `wave_reached >= 35` by `459.3M`
- sampled `wave_reached >= 40` by `559.9M`
- sampled `wave_reached >= 45` by `616.6M`
- sampled `wave_reached >= 50` by `690.0M`
- sampled `wave_reached >= 55` by `887.1M`
- sampled `wave_reached >= 60` by `992.0M`
- sampled `max_wave = 63` by `780.1M`
- best sampled window was around `1.013B`:
  - `wave_reached = 60.73`
  - `episode_return = 21935.6`
  - `episode_length = 9092`
- after that peak, the run decayed into the mid-to-high `40s` / low `50s` and
  finished well below `v21`
- nearest eval checkpoint to the peak window:
  - `/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/u58coupx/0000000997195776.bin`

Comparison to `v21` (`v4ekkk3z`):
- `score: 0.0 vs 0.0`
- `wave_reached: 48.65 vs 61.40`
- `max_wave: 63 vs 63`
- `most_npcs_slayed: 272 vs 273`
- `episode_return: 14307.1 vs 22940.3`
- `episode_length: 6493 vs 10027`
- `prayer_uptime: 0.264 vs 0.734`
- `prayer_uptime_melee: 0.033 vs 0.037`
- `prayer_uptime_range: 0.108 vs 0.191`
- `prayer_uptime_magic: 0.123 vs 0.507`
- `correct_prayer: 1143.9 vs 2173.5`
- `wrong_prayer_hits: 98.4 vs 281.5`
- `no_prayer_hits: 155.6 vs 37.5`
- `damage_blocked: 139493 vs 277395`
- `dmg_taken_avg: 6818 vs 7029`
- `prayer_switches: 2284.7 vs 2725.4`
- `attack_when_ready_rate: 0.633 vs 0.623`
- `pots_used: 0.02 vs 32.0`
- `avg_prayer_on_pot: 0.0018 vs 0.5754`
- `pots_wasted: 0.0 vs 9.42`
- `food_eaten: 15.9 vs 16.9`
- `food_wasted: 4.33 vs 9.02`
- `tokxil_melee_ticks: 4.79 vs 10.76`
- `ketzek_melee_ticks: 10.28 vs 18.08`
- `reached_wave_63: 0.0067 vs not logged in v21`
- `jad_kill_rate: 0.0 vs not logged in v21`
- `SPS: 2.01M vs 2.01M`
- `entropy: 0.658 vs 0.782`
- `clipfrac: 0.157 vs 0.164`
- `value_loss: 21.506 vs 8.213`
- `kl: 0.0225 vs 0.0197`

Comparison to `v21.1` (`nxj1iw0b`):
- `score: 0.0 vs 0.0`
- `cave_complete_rate: 0.0 vs 0.0`
- `wave_reached: 48.65 vs 31.10`
- `max_wave: 63 vs 33`
- `most_npcs_slayed: 272 vs 124`
- `episode_return: 14307.1 vs 6157.9`
- `episode_length: 6493 vs 3448`
- `prayer_uptime: 0.264 vs 0.697`
- `prayer_uptime_melee: 0.033 vs 0.071`
- `prayer_uptime_range: 0.108 vs 0.626`
- `prayer_uptime_magic: 0.123 vs 0.000`
- `correct_prayer: 1143.9 vs 829.0`
- `wrong_prayer_hits: 98.4 vs 39.1`
- `no_prayer_hits: 155.6 vs 17.2`
- `damage_blocked: 139493 vs 11791`
- `dmg_taken_avg: 6818 vs 1930`
- `prayer_switches: 2284.7 vs 220.2`
- `attack_when_ready_rate: 0.633 vs 0.336`
- `pots_used: 0.02 vs 12.2`
- `food_eaten: 15.9 vs 6.7`
- `tokxil_melee_ticks: 4.79 vs 1.51`
- `ketzek_melee_ticks: 10.28 vs 1.03`
- `reached_wave_63: 0.0067 vs 0.0`
- `jad_kill_rate: 0.0 vs 0.0`

Jad / completion audit:
- `reached_wave_63 > 0` confirms real Jad-wave episodes
- `jad_kill_rate = 0.0` confirms no Jad kills
- `cave_complete_rate = 0.0` confirms no clears
- `most_npcs_slayed = 272` is consistent with reaching Jad while killing no
  Jad healers

Interpretation:
- reverting the `v21.1` anti-stall retune clearly fixed the wave-31 collapse
- offensive tempo recovered almost exactly to `v21`:
  - `attack_when_ready_rate: 0.633 vs 0.623`
- but `v21.2` did **not** restore the `v21` sustained late-wave regime
- instead it learned a different failure mode:
  - very low prayer uptime
  - near-zero potion usage
  - high no-prayer hit volume
  - decent offensive tempo, but poor prayer/resource sustain
- the run still has real ceiling:
  - it reached `max_wave = 63`
  - it hit `wave_reached = 60.73` around `1.013B`
- but it could not hold that regime and drifted down for the final `~4B` steps
- even after recovering from `v21.1`, the final `v21.2` policy still finished
  below `v20.2` on average depth (`48.65` vs `55.83`)

Most important new insight:
- reverting the `shape_not_attacking_*` retune is necessary but not sufficient
  to recover `v21`
- `v21.2` regained Jad-wave reach and removed the `v21.1` low-offense shelf
- the remaining failure mode is different from `v21.1`:
  - not attack passivity
  - but prayer/resource collapse
- because `v21.2` nominally restores the `v21` learning config, the remaining
  gap is now either:
  - trainer variance / instability
  - or a non-neutral effect from the retained shared reward-helper refactor

Recommendations:
- use the early-peak `v21.2` checkpoint window for eval, not the final checkpoint
- keep the explicit Jad analytics and viewer parity fixes
- do not reintroduce the `v21.1` `shape_not_attacking_*` retune
- before further reward tuning, run a numeric parity audit between the shared
  `fc_reward.h` path and the pre-refactor `v21` reward computation on identical
  recorded states
- if `v21.3` is needed, debug the prayer/resource regression first, not stall
  shaping

Bottom line:
- `v21.2` substantially recovered from the `v21.1` collapse
- `v21.2` did not reproduce `v21`
- it reached Jad wave but recorded zero Jad kills and zero cave clears
- the next debugging target is training equivalence of the retained shared
  reward path, not another anti-stall retune

---

## v21.1 (2026-04-10, completed)

Actual run:
- `nxj1iw0b`
- W&B run name:
  - `fanciful-disco-127`
- local run log:
  - [nxj1iw0b.json](/home/joe/projects/runescape-rl/codex3/pufferlib_4/logs/fight_caves/nxj1iw0b.json)

Status:
- completed normally

Goal:
- keep the `v21` reward/backend stack and `5B` run budget
- increase pressure against deterministic “attack-ready but not attacking”
  stalls seen in eval viewer
- make viewer reward/debug output match the current training-env/core reward
  path exactly so replay assessment is trustworthy

Config versus `v21`:
- same PPO recipe
- same backend
- same loadout
- same prayer / resource / melee-pressure shaping terms
- only intended learning-behavior change:
  - `shape_not_attacking_grace_ticks: 2 -> 6`
  - `shape_not_attacking_penalty: -0.01 -> -0.03`

Tooling / analytics changes kept in this run:
- `viewer.c` loads reward params from `config/fight_caves.ini`
- training-env reward and viewer reward both route through the shared
  `fc_reward.h` breakdown code
- the viewer reward tab now reflects the current training breakdown instead of
  stale viewer-only math
- explicit run analytics now include:
  - `cave_complete_rate`
  - `reached_wave_63`
  - `jad_kill_rate`

Exact active config:
- run setup: `load_model_path=null`, `policy_obs=106`, `puffer_obs=142`
- reward weights: `w_damage_dealt=0.5`, `w_attack_attempt=0.1`, `w_damage_taken=-0.6`, `w_npc_kill=3.0`, `w_wave_clear=10.0`, `w_jad_damage=2.0`, `w_jad_kill=50.0`, `w_player_death=-20.0`, `w_cave_complete=100.0`, `w_correct_danger_prayer=0.25`, `w_invalid_action=-0.1`, `w_tick_penalty=-0.005`
- shaping: `shape_food_full_waste_penalty=-6.5`, `shape_food_waste_scale=-1.2`, `shape_food_safe_hp_threshold=1.0`, `shape_pot_full_waste_penalty=-6.5`, `shape_pot_waste_scale=-1.2`, `shape_pot_safe_prayer_threshold=1.0`, `shape_wrong_prayer_penalty=-1.25`, `shape_npc_specific_prayer_bonus=1.5`, `shape_npc_melee_penalty=-0.3`, `shape_wasted_attack_penalty=-0.1`, `shape_wave_stall_start=500`, `shape_wave_stall_base_penalty=-0.5`, `shape_wave_stall_ramp_interval=50`, `shape_wave_stall_cap=-2.0`, `shape_not_attacking_grace_ticks=6`, `shape_not_attacking_penalty=-0.03`, `shape_kiting_reward=1.0`, `shape_kiting_min_dist=5`, `shape_kiting_max_dist=7`, `shape_unnecessary_prayer_penalty=-0.2`, `shape_resource_threat_window=2`
- runtime: `total_agents=4096`, `num_buffers=2`, `total_timesteps=5_000_000_000`, `learning_rate=0.0003`, `gamma=0.999`, `gae_lambda=0.95`, `clip_coef=0.2`, `vf_coef=0.5`, `ent_coef=0.01`, `max_grad_norm=0.5`, `horizon=256`, `minibatch_size=4096`, `hidden_size=256`, `num_layers=2`

Results (`nxj1iw0b`):
- completed normally
- final logged trainer step:
  - `4,999,610,368 / 5,000,000,000`
- runtime:
  - `2669.2s`
- throughput:
  - `1.75M SPS`

Final metrics:
- `score = 0.0`
- `cave_complete_rate = 0.0`
- `wave_reached = 31.10`
- `max_wave = 33`
- `most_npcs_slayed = 124`
- `episode_return = 6157.90`
- `episode_length = 3447.77`
- `reached_wave_30 = 1.0`
- `cleared_wave_30 = 1.0`
- `reached_wave_31 = 1.0`
- `reached_wave_63 = 0.0`
- `jad_kill_rate = 0.0`
- `prayer_uptime = 0.6972`
- `prayer_uptime_melee = 0.0711`
- `prayer_uptime_range = 0.6261`
- `prayer_uptime_magic = 0.0`
- `correct_prayer = 829.04`
- `wrong_prayer_hits = 39.10`
- `no_prayer_hits = 17.24`
- `damage_blocked = 11790.64`
- `dmg_taken_avg = 1929.56`
- `prayer_switches = 220.16`
- `attack_when_ready_rate = 0.3364`
- `pots_used = 12.22`
- `avg_prayer_on_pot = 0.4226`
- `pots_wasted = 0.90`
- `food_eaten = 6.74`
- `avg_hp_on_food = 0.8967`
- `food_wasted = 5.60`
- `tokxil_melee_ticks = 1.51`
- `ketzek_melee_ticks = 1.03`

Key progression points:
- sampled `wave_reached >= 28` by `230.7M`
- sampled `wave_reached >= 30` by `478.2M`
- sampled `wave_reached >= 31` by `1.309B`
- never sampled `wave_reached >= 32`
- never sampled `wave_reached >= 35`
- from roughly `1.3B` onward, the run sat on a narrow `30.9-31.2` shelf for the
  rest of training
- best sampled window was the final one:
  - `wave_reached = 31.2`
  - `episode_return = 6193.6`
  - `episode_length = 3446`

Comparison to `v21` (`v4ekkk3z`):
- `score: 0.0 vs 0.0`
- `wave_reached: 31.10 vs 61.40`
- `max_wave: 33 vs 63`
- `most_npcs_slayed: 124 vs 273`
- `episode_return: 6157.9 vs 22940.3`
- `episode_length: 3448 vs 10027`
- `prayer_uptime: 0.697 vs 0.734`
- `prayer_uptime_melee: 0.071 vs 0.037`
- `prayer_uptime_range: 0.626 vs 0.191`
- `prayer_uptime_magic: 0.000 vs 0.507`
- `correct_prayer: 829.0 vs 2173.5`
- `wrong_prayer_hits: 39.1 vs 281.5`
- `no_prayer_hits: 17.2 vs 37.5`
- `damage_blocked: 11791 vs 277395`
- `dmg_taken_avg: 1930 vs 7029`
- `prayer_switches: 220.2 vs 2725.4`
- `attack_when_ready_rate: 0.336 vs 0.623`
- `pots_used: 12.2 vs 32.0`
- `food_eaten: 6.7 vs 16.9`
- `tokxil_melee_ticks: 1.51 vs 10.76`
- `ketzek_melee_ticks: 1.03 vs 18.08`
- `reached_wave_63: 0.0 vs Jad-wave territory in v21`
- `jad_kill_rate: 0.0 vs not logged in v21`
- `SPS: 1.75M vs 2.01M`
- `entropy: 1.742 vs 0.782`
- `clipfrac: 0.171 vs 0.164`
- `value_loss: 8.317 vs 8.213`
- `kl: 0.0145 vs 0.0197`

Interpretation:
- this was not a “same-depth but cleaner” regression
- it was a complete frontier collapse from a Jad-facing run into a stable
  wave-31 shelf
- the lower damage taken, lower resource use, and lower melee leakage are a
  byproduct of dying much earlier, not evidence of better late-wave play
- the strongest behavioral signatures are:
  - `attack_when_ready_rate` collapsing from `0.623` to `0.336`
  - `prayer_uptime_magic` collapsing from `0.507` to `0.0`
  - `prayer_uptime_range` dominating at `0.626`
  - `prayer_switches` collapsing from `2725` to `220`
- that points to a conservative, low-offense, range-prayer-heavy policy that
  consistently clears wave 30 but fails to convert into deeper late-wave play

Most important new insight:
- the `v21.1` anti-stall retune was clearly harmful on this stack
- the only intended learning-behavior delta from `v21` was the
  `shape_not_attacking_*` change
- the viewer / parity / analytics changes are much less likely to explain a
  collapse this large because they were intended to preserve training behavior
- the most plausible read is that delaying the non-attacking penalty out to `6`
  ready ticks materially weakened offensive pressure around the wave-30/31
  frontier

Recommendations:
- revert `shape_not_attacking_grace_ticks` to `2`
- revert `shape_not_attacking_penalty` to `-0.01`
- keep the shared reward helper, explicit Jad analytics, and viewer parity work
- use the reverted parity baseline as `v21.2`
- if future anti-stall tuning is revisited, do it from the `v21`/`v21.2` base
  with much smaller deltas or shorter diagnostic budgets

Bottom line:
- `v21.1` was a severe regression from `v21`
- it never reached Jad wave
- it plateaued around wave `31` for almost the entire back half of training
- the clean next move is to revert the `shape_not_attacking_*` change and keep
  only the non-learning tooling improvements

---

## v21 (2026-04-09, completed)

Actual run:
- `v4ekkk3z`
- W&B run name:
  - `logical-wave-125`

Status:
- completed normally

Goal:
- run the `v20.2` baseline longer without changing the reward recipe or backend
  behavior
- test whether the strong `v20.2` trajectory continues to improve when given a
  `5B` budget instead of `2.5B`

Config:
- exact `v20.2` PPO recipe
- exact `v20.2` env / reward recipe
- no curriculum
- same loadout
- only intended config change versus `v20.2`:
  - `total_timesteps: 2.5B -> 5.0B`

Backend changes versus `v20.2`:
- none
- keep:
  - locked-prayer combat backend
  - 1-tick prayer flick drain fix
  - resolve-time generic danger-prayer reward
  - mild `v_tmp2.1` prayer reward magnitudes

Exact active config:
- run setup: `load_model_path=null`, `policy_obs=106`, `puffer_obs=142`
- reward weights: `w_damage_dealt=0.5`, `w_attack_attempt=0.1`, `w_damage_taken=-0.6`, `w_npc_kill=3.0`, `w_wave_clear=10.0`, `w_jad_damage=2.0`, `w_jad_kill=50.0`, `w_player_death=-20.0`, `w_cave_complete=100.0`, `w_correct_danger_prayer=0.25`, `w_invalid_action=-0.1`, `w_tick_penalty=-0.005`
- shaping: `shape_food_full_waste_penalty=-6.5`, `shape_food_waste_scale=-1.2`, `shape_food_safe_hp_threshold=1.0`, `shape_pot_full_waste_penalty=-6.5`, `shape_pot_waste_scale=-1.2`, `shape_pot_safe_prayer_threshold=1.0`, `shape_wrong_prayer_penalty=-1.25`, `shape_npc_specific_prayer_bonus=1.5`, `shape_npc_melee_penalty=-0.3`, `shape_wasted_attack_penalty=-0.1`, `shape_wave_stall_start=500`, `shape_wave_stall_base_penalty=-0.5`, `shape_wave_stall_ramp_interval=50`, `shape_wave_stall_cap=-2.0`, `shape_not_attacking_grace_ticks=2`, `shape_not_attacking_penalty=-0.01`, `shape_kiting_reward=1.0`, `shape_kiting_min_dist=5`, `shape_kiting_max_dist=7`, `shape_unnecessary_prayer_penalty=-0.2`, `shape_resource_threat_window=2`
- runtime: `total_agents=4096`, `num_buffers=2`, `total_timesteps=5_000_000_000`, `learning_rate=0.0003`, `gamma=0.999`, `gae_lambda=0.95`, `clip_coef=0.2`, `vf_coef=0.5`, `ent_coef=0.01`, `max_grad_norm=0.5`, `horizon=256`, `minibatch_size=4096`, `hidden_size=256`, `num_layers=2`

Important caveat:
- this was not a clean “same run, longer” control in trainer internals
- `total_timesteps` also lengthened the prioritized-replay beta anneal horizon
  in the current PufferLib implementation
- so `v21` changed both:
  - the total budget
  - the replay-anneal schedule

Results (`v4ekkk3z`):
- completed normally
- final trainer step: `4,999,610,368 / 5,000,000,000`
- runtime: `2418s`
- throughput: `2.01M SPS`

Final metrics:
- `score = 0.0`
- `wave_reached = 61.40`
- `max_wave = 63`
- `most_npcs_slayed = 273`
- `episode_return = 22940.31`
- `episode_length = 10027.00`
- `reached_wave_30 = 1.0`
- `reached_wave_31 = 1.0`
- `prayer_uptime = 0.7345`
- `prayer_uptime_melee = 0.0371`
- `prayer_uptime_range = 0.1906`
- `prayer_uptime_magic = 0.5068`
- `correct_prayer = 2173.52`
- `wrong_prayer_hits = 281.50`
- `no_prayer_hits = 37.47`
- `damage_blocked = 277395.06`
- `dmg_taken_avg = 7028.79`
- `prayer_switches = 2725.36`
- `attack_when_ready_rate = 0.6226`
- `pots_used = 32.0`
- `avg_prayer_on_pot = 0.5754`
- `pots_wasted = 9.42`
- `food_eaten = 16.94`
- `avg_hp_on_food = 0.7751`
- `food_wasted = 9.02`
- `tokxil_melee_ticks = 10.76`
- `ketzek_melee_ticks = 18.08`

Key progression points:
- sampled `wave_reached >= 30` by `218M`
- sampled `wave_reached >= 35` by `455M`
- sampled `wave_reached >= 40` by `696M`
- sampled `wave_reached >= 45` by `696M`
- sampled `wave_reached >= 50` by `696M`
- sampled `wave_reached >= 55` by `1.013B`
- sampled `wave_reached >= 60` by `1.013B`
- sampled `max_wave = 63` by `1.876B`
- best sampled window was around `3.246B`:
  - `wave_reached = 61.8`
  - `episode_return = 23283.6`
  - `episode_length = 10191`
- after that peak, the run drifted back into the upper-50 / low-60 band for
  the rest of training rather than converting into full clears

Comparison to `v20.2` (`4o8gv87z`):
- `score: 0.0 vs 0.0`
- `wave_reached: 61.40 vs 55.83`
- `max_wave: 63 vs 60`
- `most_npcs_slayed: 273 vs 264`
- `episode_return: 22940.31 vs 19112.53`
- `episode_length: 10027.00 vs 8246.68`
- `sampled wave_reached >= 30: 218M vs 489M`
- `sampled wave_reached >= 40: 696M vs 973M`
- `sampled wave_reached >= 50: 696M vs 1.260B`
- `sampled wave_reached >= 55: 1.013B vs 1.369B`
- `prayer_uptime: 0.734 vs 0.824`
- `correct_prayer: 2173.52 vs 1837.12`
- `wrong_prayer_hits: 281.50 vs 155.01`
- `no_prayer_hits: 37.47 vs 23.55`
- `damage_blocked: 277395 vs 204649`
- `dmg_taken_avg: 7029 vs 5241`
- `prayer_switches: 2725.4 vs 485.9`
- `attack_when_ready_rate: 0.623 vs 0.657`
- `tokxil_melee_ticks: 10.76 vs 2.14`
- `ketzek_melee_ticks: 18.08 vs 4.21`
- `food_wasted: 9.02 vs 7.57`
- `pots_wasted: 9.42 vs 3.99`

Jad / completion audit:
- `score = 0` is correct for this run under the current backend
- in Fight Caves, `score` only increments when terminal code is
  `TERMINAL_CAVE_COMPLETE`
- reaching wave 63 does **not** imply a score increment
- so `max_wave = 63` with `score = 0` means:
  - the run reached Jad-wave episodes
  - no episode actually cleared the cave
- before this audit patch, the backend did **not** preserve a per-episode
  “Jad died at any point” latch in logged analytics
- therefore the old logs cannot prove whether `v21` ever killed Jad in an
  episode and later died to healers
- however, `most_npcs_slayed = 273` is still informative:
  - a full cave without Jad healers is `272` kills total
  - `273` proves the policy reached Jad healer-phase territory in at least one
    episode
  - but it does **not** prove Jad kill, because `273` could be:
    - Jad + 1 healer
    - or 2 healers with Jad still alive
- forward fix applied here:
  - added explicit `reached_wave_63`, `jad_kill_rate`, and
    `cave_complete_rate` analytics so future runs cannot blur those cases

Interpretation:
- `v21` is deeper than `v20.2` on every coarse depth metric that matters:
  average wave, max wave, kill-count ceiling, return, and episode length
- but it is also much sloppier in late-wave execution:
  - far more wrong-prayer hits
  - more no-prayer hits
  - much more prayer thrash
  - much worse Tok-Xil / Ket-Zek melee leakage
  - more potion waste
- this is not the old `v20` / `v20.1` prayer-camping failure mode
- instead it looks like a high-ceiling, low-conversion regime:
  - it gets to Jad territory early and often
  - but it leaks too much damage and resource efficiency to finish
- because the anneal horizon changed with the longer budget, this cannot be
  cleanly interpreted as “5B is better than 2.5B” without qualification

Most important new insight:
- longer training on the `v20.2` recipe does unlock much more frequent Jad-wave
  reach and a higher peak frontier on this stack
- but the current trainer schedule does not turn that extra depth into actual
  cave clears
- the run is good evidence that the recipe has more ceiling left
- it is not good evidence yet that the final `5B` policy is the right control
  for future reward/backend comparisons

Recommendations:
- keep `v20.2` as the clean baseline for config comparisons
- treat `v21` as a promising but schedule-confounded long-run result
- decouple replay beta anneal horizon from `total_timesteps` before the next
  long control (`v21.1` / `v22`)
- use the new explicit `reached_wave_63`, `jad_kill_rate`, and
  `cave_complete_rate` metrics for all future Jad-facing runs
- evaluate the best `v21` checkpoint window, not just the final checkpoint:
  - `/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/v4ekkk3z/0000003251634176.bin`
- if follow-up tuning is needed after the anneal decouple, focus on:
  - late-wave damage leakage
  - Tok-Xil / Ket-Zek melee exposure
  - resource waste during Jad/healer phase
  - not on broad prayer-reward redesign first

Bottom line:
- `v21` was a real frontier extension over `v20.2` in raw depth
- `v21` did **not** produce a cave clear
- old logging could not tell us whether Jad ever died during a failed wave-63
  episode
- the backend audit found that ambiguity and the patch here fixes it for future
  runs

---

## v20 Family Comparison Summary (2026-04-09)

This four-run block cleanly separated three moving pieces:
- the 1-tick prayer drain fix
- snapshot-timed generic prayer reward
- aggressive prayer reward magnitudes

Run matrix:
- `v20`
  - 1-tick flick fix
  - snapshot-timed generic prayer reward
  - aggressive prayer magnitudes
  - NPC-specific bonus disabled
- `v20.1`
  - 1-tick flick fix
  - snapshot-timed generic prayer reward
  - mild `v_tmp2.1` prayer magnitudes
  - NPC-specific bonus restored
- `v20.2`
  - 1-tick flick fix
  - original resolve-time generic prayer reward
  - mild `v_tmp2.1` prayer magnitudes
  - NPC-specific bonus restored
- `v20.3`
  - 1-tick flick fix
  - original resolve-time generic prayer reward
  - aggressive prayer magnitudes
  - NPC-specific bonus disabled

Final ranking by actual cave performance:
- `v20.2` (`4o8gv87z`) — clear winner
- `v_tmp2.1` / `ro7h07qm` — still strong, but now second-best
- `v20` (`t4eudsav`) and `v20.1` (`rp7a0y2e`) — both meaningful regressions
- `v20.3` (`77yha5y7`) — worst of the `v20` family

What the matrix says:
- the 1-tick flick fix itself is strongly positive
  - `v20.2` is the direct evidence
- snapshot-timed generic prayer reward is harmful in this form
  - both `v20` and `v20.1` regressed while keeping that timing change
- aggressive prayer reward magnitudes are also harmful
  - `v20.3` regressed even after reward timing was restored to resolve-time
- the best current recipe is:
  - keep the 1-tick flick fix
  - keep resolve-time generic prayer reward
  - keep the milder `v_tmp2.1` prayer magnitudes
  - keep the NPC-specific bonus enabled at the old value

Key observations across the four runs:
- snapshot timing consistently pushed the policy toward prayer camping
  - `v20`: `prayer_uptime = 0.966`, `prayer_switches = 682`
  - `v20.1`: `prayer_uptime = 0.980`, `prayer_switches = 402`
  - both were much less switchy than `v_tmp2.1` while not getting deeper
- aggressive magnitudes without snapshot timing still overconstrained the policy
  - `v20.3`: `prayer_switches = 57.6`, `no_prayer_hits = 58.9`
  - this was not just “camp more prayer”; it also lost offensive tempo and
    stalled around the low-30 shelf for most of the run
- `v20.2` is qualitatively different from the others
  - it broke into the 40+ range by `948M`
  - hit `45+` by `1.028B`
  - hit `50+` by `1.193B`
  - hit `55+` by `1.351B`
  - no other run in this family came close

Best-to-worst comparison at a glance:
- `wave_reached`
  - `v20.2: 55.83`
  - `v_tmp2.1: 46.12`
  - `v20: 44.66`
  - `v20.1: 44.11`
  - `v20.3: 38.32`
- `max_wave`
  - `v20.2: 60`
  - `v_tmp2.1: 62`
  - `v20: 51`
  - `v20.1: 50`
  - `v20.3: 49`
- `attack_when_ready_rate`
  - `v20.2: 0.657`
  - `v_tmp2.1: 0.579`
  - `v20: 0.537`
  - `v20.1: 0.535`
  - `v20.3: 0.465`
- `tokxil_melee_ticks`
  - `v20.2: 2.14`
  - `v_tmp2.1: 7.04`
  - `v20.1: 12.78`
  - `v20.3: 11.20`
  - `v20: 18.16`
- `ketzek_melee_ticks`
  - `v20.2: 4.21`
  - `v_tmp2.1: 9.39`
  - `v20.3: 8.12`
  - `v20.1: 13.78`
  - `v20: 20.83`

Interpretation:
- the old hypothesis that “maybe the new prayer timing reward just needs softer
  magnitudes” did not hold up
  - `v20.1` disproved that
- the old hypothesis that “maybe the new reward magnitudes were the whole
  problem” also did not hold up by itself
  - `v20.3` disproved that
- the current strongest explanation is:
  - snapshot-timed generic reward creates bad credit assignment pressure
  - aggressive prayer magnitudes also create bad optimization pressure
  - the 1-tick flick fix is independently good and should be kept

Recommendations:
- promote `v20.2` as the new working baseline
- do not train further on the snapshot-timed generic prayer reward path in its
  current form
- do not use the aggressive prayer reward magnitudes from `v20` / `v20.3`
- if prayer reward timing is revisited later, test it only as a much smaller
  auxiliary signal instead of replacing the resolve-time signal
- if further ablations are needed, the clean next one would be:
  - `v20.2` base
  - keep resolve-time reward
  - vary only NPC-specific bonus on/off
  - leave generic prayer reward at `0.25`

Bottom line:
- `v20.2` answered the main question
- the valuable backend change is the 1-tick flick fix
- the valuable training recipe is still the mild `v_tmp2.1` prayer reward band
- the new forward baseline should be `v20.2`, not `v20`, `v20.1`, or `v20.3`

---

## v20.3 (2026-04-09, completed)

Actual run:
- `77yha5y7`
- W&B run name:
  - `bright-firebrand-110`

Status:
- completed normally

Goal:
- test the aggressive prayer reward magnitudes from `v20` without the new
  snapshot-timed prayer reward
- separate “reward strength” from “reward timing”

Config:
- same PPO recipe as `v_tmp2.1`
- no curriculum
- `2.5B` budget
- same loadout and same non-prayer shaping terms as `v_tmp2.1`

Backend changes versus `v_tmp2.1` / `ro7h07qm`:
- keep the 1-tick prayer drain fix
- revert generic danger-prayer reward timing to the original resolve-time path

Config changes versus `v20.2`:
- disable `shape_npc_specific_prayer_bonus`
- raise `w_correct_danger_prayer` from `0.25` to `1.75`

Exact active config:
- run setup: `load_model_path=null`, `policy_obs=106`, `puffer_obs=142`
- reward weights: `w_damage_dealt=0.5`, `w_attack_attempt=0.1`, `w_damage_taken=-0.6`, `w_npc_kill=3.0`, `w_wave_clear=10.0`, `w_jad_damage=2.0`, `w_jad_kill=50.0`, `w_player_death=-20.0`, `w_cave_complete=100.0`, `w_correct_danger_prayer=1.75`, `w_invalid_action=-0.1`, `w_tick_penalty=-0.005`
- shaping: `shape_food_full_waste_penalty=-6.5`, `shape_food_waste_scale=-1.2`, `shape_food_safe_hp_threshold=1.0`, `shape_pot_full_waste_penalty=-6.5`, `shape_pot_waste_scale=-1.2`, `shape_pot_safe_prayer_threshold=1.0`, `shape_wrong_prayer_penalty=-1.25`, `shape_npc_melee_penalty=-0.3`, `shape_wasted_attack_penalty=-0.1`, `shape_wave_stall_start=500`, `shape_wave_stall_base_penalty=-0.5`, `shape_wave_stall_ramp_interval=50`, `shape_wave_stall_cap=-2.0`, `shape_not_attacking_grace_ticks=2`, `shape_not_attacking_penalty=-0.01`, `shape_kiting_reward=1.0`, `shape_kiting_min_dist=5`, `shape_kiting_max_dist=7`, `shape_unnecessary_prayer_penalty=-0.2`, `shape_resource_threat_window=2`
- runtime: `total_agents=4096`, `num_buffers=2`, `total_timesteps=2_500_000_000`, `learning_rate=0.0003`, `gamma=0.999`, `gae_lambda=0.95`, `clip_coef=0.2`, `vf_coef=0.5`, `ent_coef=0.01`, `max_grad_norm=0.5`, `horizon=256`, `minibatch_size=4096`, `hidden_size=256`, `num_layers=2`

Results (`77yha5y7`):
- completed normally
- final trainer step: `2,496,659,456 / 2,500,000,000`
- runtime: `1252s`
- throughput: `1.81M SPS`

Final metrics:
- `wave_reached = 38.32`
- `max_wave = 49`
- `most_npcs_slayed = 195`
- `episode_return = 8819.71`
- `episode_length = 4650.34`
- `reached_wave_30 = 0.9708`
- `reached_wave_31 = 0.9417`
- `prayer_uptime = 0.7789`
- `prayer_uptime_melee = 0.000043`
- `prayer_uptime_range = 0.4839`
- `prayer_uptime_magic = 0.2949`
- `correct_prayer = 1004.14`
- `wrong_prayer_hits = 176.15`
- `no_prayer_hits = 58.86`
- `damage_blocked = 71645.82`
- `dmg_taken_avg = 4080.46`
- `prayer_switches = 57.56`
- `attack_when_ready_rate = 0.4651`
- `pots_used = 24.10`
- `avg_prayer_on_pot = 0.5947`
- `pots_wasted = 9.38`
- `food_eaten = 20.0`
- `avg_hp_on_food = 0.8899`
- `food_wasted = 15.87`
- `tokxil_melee_ticks = 11.20`
- `ketzek_melee_ticks = 8.12`

Key progression points:
- `reached_wave_30 >= 0.90` by `811.6M`
- `reached_wave_31 >= 0.90` by `895.5M`
- never sampled `wave_reached >= 40`
- never sampled `wave_reached >= 45`
- never sampled `wave_reached >= 50`
- never sampled `wave_reached >= 55`

Comparison to `v20.2` (`4o8gv87z`):
- `wave_reached: 38.32 vs 55.83`
- `max_wave: 49 vs 60`
- `most_npcs_slayed: 195 vs 264`
- `episode_return: 8819.71 vs 19112.53`
- `episode_length: 4650.34 vs 8246.68`
- `reached_wave_30: 0.9708 vs 1.0`
- `reached_wave_31: 0.9417 vs 1.0`
- `first reached_wave_30 >= 0.90: 812M vs 436M`
- `first reached_wave_31 >= 0.90: 895M vs 451M`
- `never reached sampled wave_reached >= 40` vs `948M`
- `prayer_uptime: 0.779 vs 0.824`
- `correct_prayer: 1004.14 vs 1837.12`
- `wrong_prayer_hits: 176.15 vs 155.01`
- `no_prayer_hits: 58.86 vs 23.55`
- `damage_blocked: 71646 vs 204649`
- `dmg_taken_avg: 4080 vs 5241`
- `prayer_switches: 57.6 vs 485.9`
- `attack_when_ready_rate: 0.465 vs 0.657`
- `tokxil_melee_ticks: 11.20 vs 2.14`
- `ketzek_melee_ticks: 8.12 vs 4.21`
- `food_eaten: 20.0 vs 15.5`
- `food_wasted: 15.87 vs 7.57`
- `pots_wasted: 9.38 vs 3.99`

Comparison to `v20.1` (`rp7a0y2e`):
- `wave_reached: 38.32 vs 44.11`
- `max_wave: 49 vs 50`
- `most_npcs_slayed: 195 vs 204`
- `episode_return: 8819.71 vs 11739.62`
- `episode_length: 4650.34 vs 5771.71`
- `reached_wave_31: 0.9417 vs 0.9948`
- `first reached_wave_30 >= 0.90: 812M vs 655M`
- `first reached_wave_31 >= 0.90: 895M vs 666M`
- `never reached sampled wave_reached >= 40` vs `1.840B`
- `prayer_uptime: 0.779 vs 0.980`
- `correct_prayer: 1004.14 vs 1681.56`
- `wrong_prayer_hits: 176.15 vs 247.08`
- `no_prayer_hits: 58.86 vs 8.49`
- `damage_blocked: 71646 vs 127795`
- `dmg_taken_avg: 4080 vs 4117`
- `prayer_switches: 57.6 vs 401.6`
- `attack_when_ready_rate: 0.465 vs 0.535`
- `tokxil_melee_ticks: 11.20 vs 12.78`
- `ketzek_melee_ticks: 8.12 vs 13.78`
- `food_wasted: 15.87 vs 16.60`
- `pots_wasted: 9.38 vs 6.26`

Comparison to `v20` (`t4eudsav`):
- `wave_reached: 38.32 vs 44.66`
- `max_wave: 49 vs 51`
- `most_npcs_slayed: 195 vs 211`
- `episode_return: 8819.71 vs 12149.99`
- `episode_length: 4650.34 vs 5904.67`
- `reached_wave_31: 0.9417 vs 0.9946`
- `first reached_wave_30 >= 0.90: 812M vs 302M`
- `first reached_wave_31 >= 0.90: 895M vs 1.009B`
- `never reached sampled wave_reached >= 40` vs `2.141B`
- `prayer_uptime: 0.779 vs 0.966`
- `correct_prayer: 1004.14 vs 1997.74`
- `wrong_prayer_hits: 176.15 vs 231.66`
- `no_prayer_hits: 58.86 vs 7.79`
- `damage_blocked: 71646 vs 131147`
- `dmg_taken_avg: 4080 vs 4093`
- `prayer_switches: 57.6 vs 682.1`
- `attack_when_ready_rate: 0.465 vs 0.537`
- `tokxil_melee_ticks: 11.20 vs 18.16`
- `ketzek_melee_ticks: 8.12 vs 20.83`
- `food_wasted: 15.87 vs 16.83`
- `pots_wasted: 9.38 vs 8.85`

Comparison to `v_tmp2.1` / `ro7h07qm`:
- `wave_reached: 38.32 vs 46.12`
- `max_wave: 49 vs 62`
- `most_npcs_slayed: 195 vs 270`
- `episode_return: 8819.71 vs 12739.18`
- `episode_length: 4650.34 vs 6066.53`
- `reached_wave_30: 0.9708 vs 1.0`
- `reached_wave_31: 0.9417 vs 0.9815`
- `first reached_wave_30 >= 0.90: 812M vs 499M`
- `first reached_wave_31 >= 0.90: 895M vs 499M`
- `never reached sampled wave_reached >= 40` vs `1.743B`
- `prayer_uptime: 0.779 vs 0.665`
- `correct_prayer: 1004.14 vs 1339.86`
- `wrong_prayer_hits: 176.15 vs 191.61`
- `no_prayer_hits: 58.86 vs 26.04`
- `damage_blocked: 71646 vs 122208`
- `dmg_taken_avg: 4080 vs 5132`
- `prayer_switches: 57.6 vs 1212.6`
- `attack_when_ready_rate: 0.465 vs 0.579`
- `tokxil_melee_ticks: 11.20 vs 7.04`
- `ketzek_melee_ticks: 8.12 vs 9.39`
- `food_wasted: 15.87 vs 10.65`
- `pots_wasted: 9.38 vs 23.47`

Interpretation:
- `v20.3` answers the reward-magnitude question directly:
  - the aggressive prayer reward settings are bad even when snapshot timing is
    removed
- this run did not become the same kind of high-prayer camper as `v20` /
  `v20.1`; instead it became a much lower-switch, lower-tempo, range-biased
  policy that stalled near the low-30 shelf
- the strongest failure signatures are:
  - `prayer_switches = 57.6`
  - `attack_when_ready_rate = 0.465`
  - `no_prayer_hits = 58.86`
  - never reaching sampled `wave_reached >= 40`
- compared with `v20.2`, this is not a small degradation; it is a completely
  different and much weaker policy regime

Most likely takeaway:
- aggressive generic prayer reward magnitude is independently harmful
- the `v20` regression was not only about snapshot timing
- the strongest recipe remains the mild `v_tmp2.1` prayer reward band paired
  with resolve-time reward and the 1-tick flick fix

---

## v20.2 (2026-04-09, completed)

Actual run:
- `4o8gv87z`
- W&B run name:
  - `valiant-spaceship-109`

Status:
- completed normally

Goal:
- isolate the 1-tick prayer drain fix by itself
- remove the new snapshot-timed prayer reward change from the experiment
- keep the successful `v_tmp2.1` / `ro7h07qm` reward magnitudes

Config:
- same PPO recipe as `v_tmp2.1`
- no curriculum
- `2.5B` budget
- same loadout and same non-prayer shaping terms as `v_tmp2.1`

Backend changes versus `v_tmp2.1` / `ro7h07qm`:
- keep the 1-tick prayer drain fix:
  - prayer drain only accrues if prayer was active at the start of the tick and
    is still active after that tick's action processing
  - perfect 1-tick flicking is therefore free
- revert generic danger-prayer reward timing to the original state:
  - prayer correctness reward fires on resolve, not on queue / lock
- generic danger-prayer coverage remains on the original path:
  - reward is driven by the old resolve-time danger-prayer signal

Exact active config:
- run setup: `load_model_path=null`, `policy_obs=106`, `puffer_obs=142`
- reward weights: `w_damage_dealt=0.5`, `w_attack_attempt=0.1`, `w_damage_taken=-0.6`, `w_npc_kill=3.0`, `w_wave_clear=10.0`, `w_jad_damage=2.0`, `w_jad_kill=50.0`, `w_player_death=-20.0`, `w_cave_complete=100.0`, `w_correct_danger_prayer=0.25`, `w_invalid_action=-0.1`, `w_tick_penalty=-0.005`
- shaping: `shape_food_full_waste_penalty=-6.5`, `shape_food_waste_scale=-1.2`, `shape_food_safe_hp_threshold=1.0`, `shape_pot_full_waste_penalty=-6.5`, `shape_pot_waste_scale=-1.2`, `shape_pot_safe_prayer_threshold=1.0`, `shape_wrong_prayer_penalty=-1.25`, `shape_npc_specific_prayer_bonus=1.5`, `shape_npc_melee_penalty=-0.3`, `shape_wasted_attack_penalty=-0.1`, `shape_wave_stall_start=500`, `shape_wave_stall_base_penalty=-0.5`, `shape_wave_stall_ramp_interval=50`, `shape_wave_stall_cap=-2.0`, `shape_not_attacking_grace_ticks=2`, `shape_not_attacking_penalty=-0.01`, `shape_kiting_reward=1.0`, `shape_kiting_min_dist=5`, `shape_kiting_max_dist=7`, `shape_unnecessary_prayer_penalty=-0.2`, `shape_resource_threat_window=2`
- runtime: `total_agents=4096`, `num_buffers=2`, `total_timesteps=2_500_000_000`, `learning_rate=0.0003`, `gamma=0.999`, `gae_lambda=0.95`, `clip_coef=0.2`, `vf_coef=0.5`, `ent_coef=0.01`, `max_grad_norm=0.5`, `horizon=256`, `minibatch_size=4096`, `hidden_size=256`, `num_layers=2`

Results (`4o8gv87z`):
- completed normally
- final trainer step: `2,495,610,880 / 2,500,000,000`
- runtime: `1223s`
- throughput: `2.08M SPS`

Final metrics:
- `wave_reached = 55.83`
- `max_wave = 60`
- `most_npcs_slayed = 264`
- `episode_return = 19112.53`
- `episode_length = 8246.68`
- `reached_wave_30 = 1.0`
- `reached_wave_31 = 1.0`
- `prayer_uptime = 0.8238`
- `prayer_uptime_melee = 0.0417`
- `prayer_uptime_range = 0.2483`
- `prayer_uptime_magic = 0.5338`
- `correct_prayer = 1837.12`
- `wrong_prayer_hits = 155.01`
- `no_prayer_hits = 23.55`
- `damage_blocked = 204648.86`
- `dmg_taken_avg = 5241.05`
- `prayer_switches = 485.93`
- `attack_when_ready_rate = 0.6571`
- `pots_used = 31.66`
- `avg_prayer_on_pot = 0.5217`
- `pots_wasted = 3.99`
- `food_eaten = 15.5`
- `avg_hp_on_food = 0.7686`
- `food_wasted = 7.57`
- `tokxil_melee_ticks = 2.14`
- `ketzek_melee_ticks = 4.21`

Key progression points:
- `reached_wave_30 >= 0.90` by `436.2M`
- `reached_wave_31 >= 0.90` by `450.9M`
- first sampled `wave_reached >= 40` by `947.9M`
- first sampled `wave_reached >= 45` by `1.028B`
- first sampled `wave_reached >= 50` by `1.193B`
- first sampled `wave_reached >= 55` by `1.351B`

Comparison to `v_tmp2.1` / `ro7h07qm`:
- `wave_reached: 55.83 vs 46.12`
- `max_wave: 60 vs 62`
- `most_npcs_slayed: 264 vs 270`
- `episode_return: 19112.53 vs 12739.18`
- `episode_length: 8246.68 vs 6066.53`
- `reached_wave_30: 1.0 vs 1.0`
- `reached_wave_31: 1.0 vs 0.9815`
- `first reached_wave_30 >= 0.90: 436M vs 491M`
- `first reached_wave_31 >= 0.90: 451M vs 499M`
- `first wave_reached >= 40: 948M vs 1.743B`
- `first wave_reached >= 45: 1.028B vs 2.009B`
- `first wave_reached >= 50: 1.193B vs never sampled`
- `prayer_uptime: 0.824 vs 0.665`
- `prayer_switches: 486 vs 1213`
- `attack_when_ready_rate: 0.657 vs 0.579`
- `correct_prayer: 1837.12 vs 1339.86`
- `wrong_prayer_hits: 155.01 vs 191.61`
- `no_prayer_hits: 23.55 vs 26.04`
- `damage_blocked: 204649 vs 122208`
- `dmg_taken_avg: 5241 vs 5132`
- `tokxil_melee_ticks: 2.14 vs 7.04`
- `ketzek_melee_ticks: 4.21 vs 9.39`
- `food_eaten: 15.5 vs 18.83`
- `food_wasted: 7.57 vs 10.65`
- `pots_wasted: 3.99 vs 23.47`

Comparison to `v20.1` (`rp7a0y2e`):
- `wave_reached: 55.83 vs 44.11`
- `max_wave: 60 vs 50`
- `most_npcs_slayed: 264 vs 204`
- `episode_return: 19112.53 vs 11739.62`
- `episode_length: 8246.68 vs 5771.71`
- `reached_wave_31: 1.0 vs 0.9948`
- `first reached_wave_30 >= 0.90: 436M vs 655M`
- `first reached_wave_31 >= 0.90: 451M vs 666M`
- `first wave_reached >= 40: 948M vs 1.840B`
- `first wave_reached >= 45: 1.028B vs never sampled`
- `prayer_uptime: 0.824 vs 0.980`
- `correct_prayer: 1837.12 vs 1681.56`
- `wrong_prayer_hits: 155.01 vs 247.08`
- `no_prayer_hits: 23.55 vs 8.49`
- `damage_blocked: 204649 vs 127795`
- `dmg_taken_avg: 5241 vs 4117`
- `prayer_switches: 486 vs 402`
- `attack_when_ready_rate: 0.657 vs 0.535`
- `tokxil_melee_ticks: 2.14 vs 12.78`
- `ketzek_melee_ticks: 4.21 vs 13.78`
- `food_eaten: 15.5 vs 20.0`
- `food_wasted: 7.57 vs 16.60`
- `pots_wasted: 3.99 vs 6.26`

Comparison to `v20` (`t4eudsav`):
- `wave_reached: 55.83 vs 44.66`
- `max_wave: 60 vs 51`
- `most_npcs_slayed: 264 vs 211`
- `episode_return: 19112.53 vs 12149.99`
- `episode_length: 8246.68 vs 5904.67`
- `reached_wave_31: 1.0 vs 0.9946`
- `first reached_wave_30 >= 0.90: 436M vs 302M`
- `first reached_wave_31 >= 0.90: 451M vs 1.009B`
- `first wave_reached >= 40: 948M vs 2.141B`
- `first wave_reached >= 45: 1.028B vs 2.489B`
- `prayer_uptime: 0.824 vs 0.966`
- `correct_prayer: 1837.12 vs 1997.74`
- `wrong_prayer_hits: 155.01 vs 231.66`
- `no_prayer_hits: 23.55 vs 7.79`
- `damage_blocked: 204649 vs 131147`
- `dmg_taken_avg: 5241 vs 4093`
- `prayer_switches: 486 vs 682`
- `attack_when_ready_rate: 0.657 vs 0.537`
- `tokxil_melee_ticks: 2.14 vs 18.16`
- `ketzek_melee_ticks: 4.21 vs 20.83`
- `food_eaten: 15.5 vs 20.0`
- `food_wasted: 7.57 vs 16.83`
- `pots_wasted: 3.99 vs 8.85`

Interpretation:
- `v20.2` is not just a recovery from `v20` / `v20.1`; it is a major step
  forward over the whole `v_tmp2.1` line on average depth and late-wave
  stability
- the 1-tick flick fix appears strongly positive when paired with the old
  resolve-time reward timing
- compared with `v_tmp2.1`, the policy became:
  - deeper on average
  - much faster at breaking into the 40+ and 45+ ranges
  - much more attack-ready
  - far cleaner on Tok-Xil / Ket-Zek melee exposure
  - much less wasteful on food and pot usage
- the cost is narrow and understandable:
  - slightly higher total damage taken
  - somewhat higher prayer uptime than the old baseline
  - lower prayer-switch count, but without losing prayer correctness

Most likely takeaway:
- the snapshot-timed prayer reward was the actual regression source in the
  `v20` / `v20.1` line
- the 1-tick prayer drain fix itself is highly likely to be a real positive
  change
- this run argues for keeping:
  - the 1-tick flick fix
  - the older resolve-time prayer reward path
  - the milder `v_tmp2.1` prayer reward magnitudes
- `v20.3` is still useful as a final control for aggressive reward magnitude,
  but `v20.2` is already strong evidence that reward timing, not flick logic,
  caused the earlier prayer-camping regressions

---

## v20.1 (2026-04-09, completed)

Actual run:
- `rp7a0y2e`

Status:
- completed normally

Goal:
- keep the backend-side prayer timing improvements from `v20`
  - 1-tick prayer flick drain fix
  - snapshot-timed prayer reward
  - melee / ranged / magic coverage in the generic danger-prayer reward
- revert the config knobs back to the successful `v_tmp2.1` / `ro7h07qm`
  prayer-reward magnitudes

Config:
- same PPO recipe as `v_tmp2.1`
- no curriculum
- `2.5B` budget
- same loadout and same non-prayer shaping terms as `v_tmp2.1`

Backend changes versus `v_tmp2.1` / `ro7h07qm`:
- 1-tick prayer drain fix:
  - prayer drain only accrues if prayer was active at the start of the tick and
    is still active after that tick's action processing
  - perfect 1-tick flicking is therefore free
- generic danger-prayer reward timing:
  - immediate-snapshot attacks reward on the queue tick
  - delayed-snapshot attacks reward on the lock tick
  - reward no longer waits for the later damage-resolve tick
- generic danger-prayer coverage:
  - now covers melee, ranged, and magic
  - not just ranged / magic

Config changes versus `v20`:
- restore `w_correct_danger_prayer` from `1.75` back to `0.25`
- restore `shape_npc_specific_prayer_bonus` from `0.0` back to `1.5`

Exact active config:
- run setup: `load_model_path=null`, snapshot-timed generic prayer reward, `policy_obs=106`, `puffer_obs=142`
- reward weights: `w_damage_dealt=0.5`, `w_attack_attempt=0.1`, `w_damage_taken=-0.6`, `w_npc_kill=3.0`, `w_wave_clear=10.0`, `w_jad_damage=2.0`, `w_jad_kill=50.0`, `w_player_death=-20.0`, `w_cave_complete=100.0`, `w_correct_danger_prayer=0.25`, `w_invalid_action=-0.1`, `w_tick_penalty=-0.005`
- shaping: `shape_food_full_waste_penalty=-6.5`, `shape_food_waste_scale=-1.2`, `shape_food_safe_hp_threshold=1.0`, `shape_pot_full_waste_penalty=-6.5`, `shape_pot_waste_scale=-1.2`, `shape_pot_safe_prayer_threshold=1.0`, `shape_wrong_prayer_penalty=-1.25`, `shape_npc_specific_prayer_bonus=1.5`, `shape_npc_melee_penalty=-0.3`, `shape_wasted_attack_penalty=-0.1`, `shape_wave_stall_start=500`, `shape_wave_stall_base_penalty=-0.5`, `shape_wave_stall_ramp_interval=50`, `shape_wave_stall_cap=-2.0`, `shape_not_attacking_grace_ticks=2`, `shape_not_attacking_penalty=-0.01`, `shape_kiting_reward=1.0`, `shape_kiting_min_dist=5`, `shape_kiting_max_dist=7`, `shape_unnecessary_prayer_penalty=-0.2`, `shape_resource_threat_window=2`
- runtime: `total_agents=4096`, `num_buffers=2`, `total_timesteps=2_500_000_000`, `learning_rate=0.0003`, `gamma=0.999`, `gae_lambda=0.95`, `clip_coef=0.2`, `vf_coef=0.5`, `ent_coef=0.01`, `max_grad_norm=0.5`, `horizon=256`, `minibatch_size=4096`, `hidden_size=256`, `num_layers=2`

Results (`rp7a0y2e`):
- completed normally
- final trainer step: `2,499,805,184 / 2,500,000,000`
- runtime: `1225s`
- throughput: `2.01M SPS`

Final metrics:
- `wave_reached = 44.11`
- `max_wave = 50`
- `most_npcs_slayed = 204`
- `episode_return = 11739.62`
- `episode_length = 5771.71`
- `reached_wave_30 = 0.9948`
- `reached_wave_31 = 0.9948`
- `prayer_uptime = 0.9796`
- `wrong_prayer_hits = 247.08`
- `no_prayer_hits = 8.49`
- `dmg_taken_avg = 4117.20`
- `prayer_switches = 401.55`
- `attack_when_ready_rate = 0.5352`
- `tokxil_melee_ticks = 12.78`
- `ketzek_melee_ticks = 13.78`
- `pots_used = 30.91`
- `food_eaten = 20.0`
- `food_wasted = 16.60`
- `pots_wasted = 6.26`

Key progression points:
- `reached_wave_30 >= 0.90` by `655.4M`
- `reached_wave_31 >= 0.90` by `665.8M`
- first sampled `wave_reached >= 40` by `1.840B`
- never sampled `wave_reached >= 45`

Comparison to `v_tmp2.1` / `ro7h07qm`:
- `wave_reached: 44.11 vs 46.12`
- `max_wave: 50 vs 62`
- `most_npcs_slayed: 204 vs 270`
- `episode_return: 11739.62 vs 12739.18`
- `reached_wave_30: 0.9948 vs 1.0`
- `reached_wave_31: 0.9948 vs 0.9815`
- `first reached_wave_30 >= 0.90: 666M vs 499M`
- `first reached_wave_31 >= 0.90: 666M vs 499M`
- `first wave_reached >= 40: 1.840B vs 1.743B`
- `never reached sampled wave_reached >= 45` vs `2.009B`
- `prayer_uptime: 0.980 vs 0.665`
- `wrong_prayer_hits: 247.08 vs 191.61`
- `no_prayer_hits: 8.49 vs 26.04`
- `dmg_taken_avg: 4117 vs 5132`
- `prayer_switches: 402 vs 1213`
- `attack_when_ready_rate: 0.535 vs 0.579`
- `tokxil_melee_ticks: 12.78 vs 7.04`
- `ketzek_melee_ticks: 13.78 vs 9.39`
- `food_wasted: 16.60 vs 10.65`
- `pots_wasted: 6.26 vs 23.47`

Comparison to `v20` (`t4eudsav`):
- `wave_reached: 44.11 vs 44.66`
- `max_wave: 50 vs 51`
- `most_npcs_slayed: 204 vs 211`
- `episode_return: 11739.62 vs 12149.99`
- `prayer_uptime: 0.980 vs 0.966`
- `wrong_prayer_hits: 247.08 vs 231.66`
- `dmg_taken_avg: 4117 vs 4093`
- `prayer_switches: 402 vs 682`
- `tokxil_melee_ticks: 12.78 vs 18.16`
- `ketzek_melee_ticks: 13.78 vs 20.83`
- `first reached_wave_31 >= 0.90: 666M vs 1.009B`
- `first wave_reached >= 40: 1.840B vs 2.141B`

Interpretation:
- `v20.1` is clearly healthier than `v20`, but it still regressed from the
  strong `v_tmp2.1` / `ro7h07qm` line
- the snapshot-timed reward with the mild old reward magnitudes still pushed
  the policy toward heavy prayer camping:
  - very high prayer uptime
  - very low no-prayer exposure
  - much lower prayer switching than the good baseline
- compared with `v20`, restoring the NPC-specific bonus and lowering generic
  prayer reward did help:
  - much better Tok-Xil / Ket-Zek melee exposure
  - much earlier wave-31 stability
  - earlier climb into the 40+ range
- but it still did not recover late-cave ceiling or switching tempo

Most likely takeaway:
- the main regression source is the snapshot-timed prayer reward itself, not
  just the stronger `v20` reward magnitudes
- `v20.2` is the right next control:
  - keep the 1-tick flick fix
  - remove snapshot-timed reward
  - keep the known-good `v_tmp2.1` reward magnitudes

Re-implementing snapshot-timed reward later:
- the reverted implementation was small and localized
- to restore it:
  - add a helper like `fc_note_prayer_snapshot(...)` in `fc_combat.[ch]`
  - call it in [fc_npc.c](/home/joe/projects/runescape-rl/codex3/runescape-rl/fc-core/src/fc_npc.c) immediately after `queued->prayer_snapshot = p->prayer` for immediate-snapshot attacks
  - call it in [fc_combat.c](/home/joe/projects/runescape-rl/codex3/runescape-rl/fc-core/src/fc_combat.c) when delayed snapshots fill at `state->tick >= h->prayer_lock_tick`
  - remove the resolve-time `correct_danger_prayer` / `wrong_danger_prayer` flag writes from the later hit-resolution block
  - if desired, broaden the generic signal to include melee there as `v20` / `v20.1` did

---

## v20 (2026-04-09, completed)

Actual run:
- `t4eudsav`

Status:
- completed normally

Goal:
- push prayer learning earlier in the credit-assignment chain
- reward the policy on the tick the backend decides which prayer snapshot will
  govern the incoming hit, rather than waiting for the later resolve tick
- remove the NPC-specific prayer bonus from this trial so the policy learns the
  generic timing rule instead of leaning on monster-specific shaping

Config:
- same PPO recipe as `v_tmp2.1`
- no curriculum
- `2.5B` budget
- same loadout and all non-prayer shaping terms as `v_tmp2.1`

Backend changes versus `v_tmp2.1` / `ro7h07qm`:
- 1-tick prayer drain fix:
  - prayer drain only accrues if prayer was active at the start of the tick and
    is still active after that tick's action processing
  - perfect 1-tick flicking is therefore free
- generic danger-prayer reward timing:
  - immediate-snapshot attacks reward on the queue tick
  - delayed-snapshot attacks reward on the lock tick
  - reward no longer waits for the later damage-resolve tick
- generic danger-prayer coverage:
  - now covers melee, ranged, and magic
  - not just ranged / magic
- NPC-specific prayer bonus:
  - backend path kept
  - config disabled for this run

Exact active config:
- run setup: `load_model_path=null`, snapshot-timed generic prayer reward, `policy_obs=106`, `puffer_obs=142`
- reward weights: `w_damage_dealt=0.5`, `w_attack_attempt=0.1`, `w_damage_taken=-0.6`, `w_npc_kill=3.0`, `w_wave_clear=10.0`, `w_jad_damage=2.0`, `w_jad_kill=50.0`, `w_player_death=-20.0`, `w_cave_complete=100.0`, `w_correct_danger_prayer=1.75`, `w_invalid_action=-0.1`, `w_tick_penalty=-0.005`
- shaping: `shape_food_full_waste_penalty=-6.5`, `shape_food_waste_scale=-1.2`, `shape_food_safe_hp_threshold=1.0`, `shape_pot_full_waste_penalty=-6.5`, `shape_pot_waste_scale=-1.2`, `shape_pot_safe_prayer_threshold=1.0`, `shape_wrong_prayer_penalty=-1.25`, `shape_npc_melee_penalty=-0.3`, `shape_wasted_attack_penalty=-0.1`, `shape_wave_stall_start=500`, `shape_wave_stall_base_penalty=-0.5`, `shape_wave_stall_ramp_interval=50`, `shape_wave_stall_cap=-2.0`, `shape_not_attacking_grace_ticks=2`, `shape_not_attacking_penalty=-0.01`, `shape_kiting_reward=1.0`, `shape_kiting_min_dist=5`, `shape_kiting_max_dist=7`, `shape_unnecessary_prayer_penalty=-0.2`, `shape_resource_threat_window=2`
- runtime: `total_agents=4096`, `num_buffers=2`, `total_timesteps=2_500_000_000`, `learning_rate=0.0003`, `gamma=0.999`, `gae_lambda=0.95`, `clip_coef=0.2`, `vf_coef=0.5`, `ent_coef=0.01`, `max_grad_norm=0.5`, `horizon=256`, `minibatch_size=4096`, `hidden_size=256`, `num_layers=2`

Results (`t4eudsav`):
- completed normally
- final trainer step: `2,495,610,880 / 2,500,000,000`
- runtime: `1226s`
- throughput: `2.03M SPS`

Final metrics:
- `wave_reached = 44.66`
- `max_wave = 51`
- `most_npcs_slayed = 211`
- `episode_return = 12149.99`
- `episode_length = 5904.67`
- `reached_wave_30 = 0.9946`
- `reached_wave_31 = 0.9946`
- `prayer_uptime = 0.9659`
- `wrong_prayer_hits = 231.66`
- `no_prayer_hits = 7.79`
- `dmg_taken_avg = 4093.19`
- `prayer_switches = 682.05`
- `attack_when_ready_rate = 0.5372`
- `tokxil_melee_ticks = 18.16`
- `ketzek_melee_ticks = 20.83`
- `pots_used = 31.50`
- `food_eaten = 20.0`
- `food_wasted = 16.83`
- `pots_wasted = 8.85`

Key progression points:
- `reached_wave_30 >= 0.90` by `302.0M`
- `reached_wave_31 >= 0.90` by `1.009B`
- first sampled `wave_reached >= 40` by `2.141B`
- first sampled `wave_reached >= 45` by `2.489B`

Comparison to `v_tmp2.1` / `ro7h07qm`:
- `wave_reached: 44.66 vs 46.12`
- `max_wave: 51 vs 62`
- `most_npcs_slayed: 211 vs 270`
- `episode_return: 12149.99 vs 12739.18`
- `reached_wave_30: 0.9946 vs 1.0`
- `reached_wave_31: 0.9946 vs 0.9815`
- `first reached_wave_30 >= 0.90: 302M vs 491M`
- `first reached_wave_31 >= 0.90: 1.009B vs 499M`
- `first wave_reached >= 40: 2.141B vs 1.743B`
- `prayer_uptime: 0.966 vs 0.665`
- `wrong_prayer_hits: 231.66 vs 191.61`
- `no_prayer_hits: 7.79 vs 26.04`
- `dmg_taken_avg: 4093 vs 5132`
- `prayer_switches: 682 vs 1213`
- `attack_when_ready_rate: 0.537 vs 0.579`
- `tokxil_melee_ticks: 18.16 vs 7.04`
- `ketzek_melee_ticks: 20.83 vs 9.39`
- `food_wasted: 16.83 vs 10.65`
- `pots_wasted: 8.85 vs 23.47`

Interpretation:
- `v20` did not improve late-cave performance; it regressed from the strong
  `v_tmp2.1` / `ro7h07qm` line
- the most obvious shift is toward very conservative prayer camping:
  - much higher prayer uptime
  - far fewer switches
  - far fewer no-prayer hits
- that conservative behavior helped early wave-30 consistency, but it did not
  convert into deeper cave performance
- the later-wave handling got worse, not better:
  - lower final depth
  - much lower ceiling
  - far worse Tok-Xil / Ket-Zek melee exposure
  - slower climb from wave 31 into the 40+ range

Most likely takeaway:
- the backend-side timing changes themselves still look promising
- the aggressive reward change in `v20` was the problem:
  - `w_correct_danger_prayer = 1.75` plus disabling the NPC-specific bonus
    appears to have over-incentivized “always keep something protective on”
    rather than fast switching between threats
- this run argues for keeping the new backend timing semantics but restoring the
  milder `v_tmp2.1` reward magnitudes, which is exactly what `v20.1` does

---

## Temporary Comparison Summary (2026-04-08)

These three scratch runs were designed to isolate the impact of the prayer timing fix and the later obs / reward follow-up changes, while holding the training recipe constant.

Final ranking by real late-wave progress:
- `v_tmp2` (`fkhhysfd`, prayer fix only) — strongest final performer
- `v_tmp3` (`fg029tll`, pre-prayer baseline) — also very strong, but less stable / less disciplined than `v_tmp2`
- `v_tmp1` (`qyekq4z3`, prayer fix + obs / reward follow-up) — clear regression

What changed:
- `v_tmp1 -> v_tmp2`:
  - removing the later obs / reward follow-up changes while keeping the prayer timing fix produced a massive recovery
  - this is the strongest evidence in the set that the regression came from the obs / reward follow-up work, not from the prayer timing change itself
- `v_tmp3 -> v_tmp2`:
  - adding the prayer timing fix improved stability and discipline without sacrificing late-wave performance
  - `v_tmp2` took far less damage, switched prayers much less, and had lower Tok-Xil melee exposure than `v_tmp3`
  - `v_tmp3` still showed some upside in ceiling metrics (`max_wave = 60` vs `53`, `most_npcs_slayed = 263` vs `222`), but its overall behavior was noisier and more wasteful
- versus `v19.3`:
  - both `v_tmp2` and `v_tmp3` converted wave-30 entries into wave-31+ much more consistently than `v19.3`
  - `v19.3` remained much cleaner on damage taken, wrong-prayer hits, switching rate, and resource use

Current interpretation:
- the prayer timing fix looks directionally correct and beneficial
- the later `npc_type` obs addition and/or resolved-hit prayer reward follow-up in `v_tmp1` likely caused the collapse
- the next clean path is to treat `v_tmp2` as the strongest current branch and reintroduce follow-up changes one at a time instead of bundling them

---

## v_tmp1 (2026-04-08, completed)

Temporary comparison run:
- actual run id:
  - `qyekq4z3`
- code snapshot:
  - `post-obs-post-prayer-2026-04-08`
  - commit `58768c5b8fd4bd4a3a16c97a7f8db5c752bfa8ee`

Config:
- scratch run
- no checkpoint load
- no curriculum
- `2.5B` budget
- same PPO / reward recipe as the `v19.3` family
- same current end-game loadout

Exact active config:
- run setup: `load_model_path=null`, early prayer-lock timing enabled, per-NPC `npc_type` obs enabled, locked-prayer resolved-hit reward follow-up enabled, `policy_obs=114`, `puffer_obs=150`
- reward weights: `w_damage_dealt=0.5`, `w_attack_attempt=0.1`, `w_damage_taken=-0.6`, `w_npc_kill=3.0`, `w_wave_clear=10.0`, `w_jad_damage=2.0`, `w_jad_kill=50.0`, `w_player_death=-20.0`, `w_cave_complete=100.0`, `w_correct_danger_prayer=0.5`, `w_invalid_action=-0.1`, `w_tick_penalty=-0.005`
- shaping: `shape_food_full_waste_penalty=-6.5`, `shape_food_waste_scale=-1.2`, `shape_food_safe_hp_threshold=1.0`, `shape_pot_full_waste_penalty=-6.5`, `shape_pot_waste_scale=-1.2`, `shape_pot_safe_prayer_threshold=1.0`, `shape_wrong_prayer_penalty=-1.0`, `shape_npc_specific_prayer_bonus=2.5`, `shape_npc_melee_penalty=-0.3`, `shape_wasted_attack_penalty=-0.1`, `shape_wave_stall_start=500`, `shape_wave_stall_base_penalty=-0.5`, `shape_wave_stall_ramp_interval=50`, `shape_wave_stall_cap=-2.0`, `shape_not_attacking_grace_ticks=2`, `shape_not_attacking_penalty=-0.01`, `shape_kiting_reward=1.0`, `shape_kiting_min_dist=5`, `shape_kiting_max_dist=7`, `shape_unnecessary_prayer_penalty=-0.2`, `shape_resource_threat_window=2`
- runtime: `total_agents=4096`, `num_buffers=2`, `total_timesteps=2_500_000_000`, `learning_rate=0.0003`, `gamma=0.999`, `gae_lambda=0.95`, `clip_coef=0.2`, `vf_coef=0.5`, `ent_coef=0.01`, `max_grad_norm=0.5`, `horizon=256`, `minibatch_size=4096`, `hidden_size=256`, `num_layers=2`

Backend differences:
- versus `v_tmp2`:
  - adds early prayer-lock timing
  - adds `npc_type` back into policy obs
  - policy contract changes from `106 / 142` to `114 / 150`
  - resolved-hit prayer rewards use the locked prayer snapshot
- versus `v_tmp3`:
  - includes the prayer timing fix
  - includes the obs / reward follow-up changes above

Results (`qyekq4z3`):
- completed normally
- final trainer step: `2,499,805,184 / 2,500,000,000`
- runtime: `1175.7s`
- throughput: `2.13M SPS`

Final metrics:
- `episode_return = 7205.14`
- `episode_length = 5601.45`
- `wave_reached = 28.94`
- `max_wave = 34`
- `most_npcs_slayed = 125`
- `score = 0.0`
- `prayer_uptime = 0.982`
- `prayer_uptime_melee = 0.503`
- `prayer_uptime_range = 0.473`
- `prayer_uptime_magic = 0.006386`
- `correct_prayer = 2819.28`
- `wrong_prayer_hits = 152.87`
- `no_prayer_hits = 37.81`
- `prayer_switches = 1854.04`
- `damage_blocked = 54995.54`
- `dmg_taken_avg = 3395.16`
- `attack_when_ready_rate = 0.315`
- `pots_used = 27.20`
- `avg_prayer_on_pot = 0.514`
- `food_eaten = 20.00`
- `avg_hp_on_food = 0.923`
- `food_wasted = 19.35`
- `pots_wasted = 4.17`
- `tokxil_melee_ticks = 25.32`
- `ketzek_melee_ticks = 0.0`
- `reached_wave_30 = 0.0457`
- `cleared_wave_30 = 0.0051`
- `reached_wave_31 = 0.0051`

Analysis:
- this run underperformed `v19.3` badly on actual cave progress
- final `wave_reached` dropped from `30.66` to `28.94`
- `reached_wave_30` fell from `99.15%` to `4.57%`
- `cleared_wave_30 / reached_wave_31` fell from `54.04%` to `0.51%`
- the run looked more defensive than competent:
  - much higher prayer switching
  - much more blocked damage
  - but also much higher damage taken and more food use
- it looked best around the mid-run window, then regressed hard late instead of converting wave-30 reaches into stable clears

---

## v_tmp2 (2026-04-08, completed)

Temporary comparison run:
- actual run id:
  - `fkhhysfd`
- code snapshot:
  - `post-prayer-pre-obs-2026-04-08`
  - commit `c232f09b`

Config:
- scratch run
- no checkpoint load
- no curriculum
- `2.5B` budget
- same PPO / reward recipe as the `v19.3` family
- same current end-game loadout

Exact active config:
- run setup: `load_model_path=null`, prayer timing fix only, no `npc_type` obs follow-up, `policy_obs=106`, `puffer_obs=142`
- reward weights: `w_damage_dealt=0.5`, `w_attack_attempt=0.1`, `w_damage_taken=-0.6`, `w_npc_kill=3.0`, `w_wave_clear=10.0`, `w_jad_damage=2.0`, `w_jad_kill=50.0`, `w_player_death=-20.0`, `w_cave_complete=100.0`, `w_correct_danger_prayer=0.5`, `w_invalid_action=-0.1`, `w_tick_penalty=-0.005`
- shaping: `shape_food_full_waste_penalty=-6.5`, `shape_food_waste_scale=-1.2`, `shape_food_safe_hp_threshold=1.0`, `shape_pot_full_waste_penalty=-6.5`, `shape_pot_waste_scale=-1.2`, `shape_pot_safe_prayer_threshold=1.0`, `shape_wrong_prayer_penalty=-1.0`, `shape_npc_specific_prayer_bonus=2.5`, `shape_npc_melee_penalty=-0.3`, `shape_wasted_attack_penalty=-0.1`, `shape_wave_stall_start=500`, `shape_wave_stall_base_penalty=-0.5`, `shape_wave_stall_ramp_interval=50`, `shape_wave_stall_cap=-2.0`, `shape_not_attacking_grace_ticks=2`, `shape_not_attacking_penalty=-0.01`, `shape_kiting_reward=1.0`, `shape_kiting_min_dist=5`, `shape_kiting_max_dist=7`, `shape_unnecessary_prayer_penalty=-0.2`, `shape_resource_threat_window=2`
- runtime: `total_agents=4096`, `num_buffers=2`, `total_timesteps=2_500_000_000`, `learning_rate=0.0003`, `gamma=0.999`, `gae_lambda=0.95`, `clip_coef=0.2`, `vf_coef=0.5`, `ent_coef=0.01`, `max_grad_norm=0.5`, `horizon=256`, `minibatch_size=4096`, `hidden_size=256`, `num_layers=2`

Backend differences:
- versus `v_tmp1`:
  - keeps the prayer timing fix
  - removes the `npc_type` obs addition
  - restores the old `106 / 142` policy contract
  - restores the pre-follow-up resolved-hit prayer reward path
- versus `v_tmp3`:
  - adds the prayer timing fix only

Results:
- completed normally
- final trainer step: `2,499,805,184 / 2,500,000,000`
- runtime: `1231.0s`
- throughput: `1.95M SPS`

Final metrics:
- `episode_return = 14745.91`
- `episode_length = 5678.44`
- `wave_reached = 44.72`
- `max_wave = 53`
- `most_npcs_slayed = 222`
- `score = 0.0`
- `prayer_uptime = 0.947`
- `prayer_uptime_melee = 0.264`
- `prayer_uptime_range = 0.238`
- `prayer_uptime_magic = 0.445`
- `correct_prayer = 1861.91`
- `wrong_prayer_hits = 464.25`
- `no_prayer_hits = 18.26`
- `prayer_switches = 1073.48`
- `damage_blocked = 132200.91`
- `dmg_taken_avg = 7470.13`
- `attack_when_ready_rate = 0.656`
- `pots_used = 30.32`
- `avg_prayer_on_pot = 0.607`
- `food_eaten = 19.36`
- `avg_hp_on_food = 0.650`
- `food_wasted = 4.32`
- `pots_wasted = 10.09`
- `tokxil_melee_ticks = 12.63`
- `ketzek_melee_ticks = 4.71`
- `reached_wave_30 = 0.9653`
- `cleared_wave_30 = 0.9653`
- `reached_wave_31 = 0.9653`

Analysis:
- this run was massively stronger than `v_tmp1` on real cave progress
- final `wave_reached` jumped from `28.94` to `44.72`
- `reached_wave_30` jumped from `4.57%` to `96.53%`
- `cleared_wave_30 / reached_wave_31` jumped from `0.51%` to `96.53%`
- `max_wave` jumped from `34` to `53`
- compared with `v19.3`, this run still reached wave 30 slightly less often (`96.53%` vs `99.15%`), but once it got there it converted vastly better (`96.53%` wave 31 vs `54.04%`)
- the tradeoff is that it was much sloppier and more brute-force:
  - much higher `wrong_prayer_hits`
  - much higher `dmg_taken_avg`
  - much higher prayer switching
  - heavier potion usage
- it also attacked far more consistently than both `v_tmp1` and `v19.3`
- Tok-Xil melee exposure improved sharply versus `v_tmp1` (`12.63` vs `25.32`), which points to the prayer-only snapshot retaining much stronger handling of mid/late cave spacing
- unlike `v_tmp1`, this run improved hard after the midpoint and held the gains through the finish instead of regressing late
- provisional takeaway:
  - the prayer timing change itself looks strongly positive
  - the later obs / reward follow-up changes in `v_tmp1` likely caused the regression
  - this snapshot is currently the strongest late-wave performer of the temporary comparison set, even though it is much less clean in damage and resource discipline than `v19.3`

---

## v_tmp3 (2026-04-08, completed)

Temporary comparison run:
- actual run id:
  - `fg029tll`
- code snapshot:
  - `pre-prayer-fix-2026-04-08`
  - commit `7c77cf93`

Config:
- scratch run
- no checkpoint load
- no curriculum
- `2.5B` budget
- same PPO / reward recipe as the `v19.3` family
- same current end-game loadout

Exact active config:
- run setup: `load_model_path=null`, pre-prayer-fix baseline, no `npc_type` obs follow-up, `policy_obs=106`, `puffer_obs=142`
- reward weights: `w_damage_dealt=0.5`, `w_attack_attempt=0.1`, `w_damage_taken=-0.6`, `w_npc_kill=3.0`, `w_wave_clear=10.0`, `w_jad_damage=2.0`, `w_jad_kill=50.0`, `w_player_death=-20.0`, `w_cave_complete=100.0`, `w_correct_danger_prayer=0.5`, `w_invalid_action=-0.1`, `w_tick_penalty=-0.005`
- shaping: `shape_food_full_waste_penalty=-6.5`, `shape_food_waste_scale=-1.2`, `shape_food_safe_hp_threshold=1.0`, `shape_pot_full_waste_penalty=-6.5`, `shape_pot_waste_scale=-1.2`, `shape_pot_safe_prayer_threshold=1.0`, `shape_wrong_prayer_penalty=-1.0`, `shape_npc_specific_prayer_bonus=2.5`, `shape_npc_melee_penalty=-0.3`, `shape_wasted_attack_penalty=-0.1`, `shape_wave_stall_start=500`, `shape_wave_stall_base_penalty=-0.5`, `shape_wave_stall_ramp_interval=50`, `shape_wave_stall_cap=-2.0`, `shape_not_attacking_grace_ticks=2`, `shape_not_attacking_penalty=-0.01`, `shape_kiting_reward=1.0`, `shape_kiting_min_dist=5`, `shape_kiting_max_dist=7`, `shape_unnecessary_prayer_penalty=-0.2`, `shape_resource_threat_window=2`
- runtime: `total_agents=4096`, `num_buffers=2`, `total_timesteps=2_500_000_000`, `learning_rate=0.0003`, `gamma=0.999`, `gae_lambda=0.95`, `clip_coef=0.2`, `vf_coef=0.5`, `ent_coef=0.01`, `max_grad_norm=0.5`, `horizon=256`, `minibatch_size=4096`, `hidden_size=256`, `num_layers=2`

Backend differences:
- versus `v_tmp2`:
  - removes the prayer timing fix
  - keeps the older pre-follow-up obs / reward path
- versus `v_tmp1`:
  - removes both the prayer timing fix and the later obs / reward follow-up changes
  - represents the last committed pre-prayer baseline for this comparison set

Results:
- completed normally
- final trainer step: `2,499,805,184 / 2,500,000,000`
- runtime: `1206.6s`
- throughput: `2.05M SPS`

Final metrics:
- `episode_return = 12958.40`
- `episode_length = 6141.61`
- `wave_reached = 41.17`
- `max_wave = 60`
- `most_npcs_slayed = 263`
- `score = 0.0`
- `prayer_uptime = 0.794`
- `prayer_uptime_melee = 0.401`
- `prayer_uptime_range = 0.259`
- `prayer_uptime_magic = 0.133`
- `correct_prayer = 2706.46`
- `wrong_prayer_hits = 155.09`
- `no_prayer_hits = 32.17`
- `prayer_switches = 2238.11`
- `damage_blocked = 118229.65`
- `dmg_taken_avg = 3726.61`
- `attack_when_ready_rate = 0.349`
- `pots_used = 31.42`
- `avg_prayer_on_pot = 0.625`
- `food_eaten = 20.0`
- `avg_hp_on_food = 0.913`
- `food_wasted = 18.07`
- `pots_wasted = 11.53`
- `tokxil_melee_ticks = 17.81`
- `ketzek_melee_ticks = 6.52`
- `reached_wave_30 = 0.9641`
- `cleared_wave_30 = 0.9436`
- `reached_wave_31 = 0.9436`

Analysis:
- this pre-prayer baseline was far stronger than `v_tmp1` and only modestly weaker than `v_tmp2`
- versus `v_tmp1`:
  - `wave_reached` jumped from `28.94` to `41.17`
  - `reached_wave_30` jumped from `4.57%` to `96.41%`
  - `reached_wave_31` jumped from `0.51%` to `94.36%`
  - `max_wave` jumped from `34` to `60`
- versus `v_tmp2`:
  - slightly worse final late-wave conversion (`94.36%` wave 31 vs `96.53%`)
  - lower final `wave_reached` (`41.17` vs `44.72`)
  - much higher prayer switching (`2238` vs `1073`)
  - much higher Tok-Xil melee exposure (`17.81` vs `12.63`)
  - lower damage taken (`3726.61`) than `v_tmp2` (`7470.13`), but still higher than `v19.3`
- the run shape was strong early and peaked very high mid-run:
  - by ~`0.94B` it was already at `99.72%` wave-30 reach and `99.55%` wave-31 reach
  - it peaked near `49.87` wave average around ~`1.56B`
  - then regressed somewhat by the finish, but still ended as a very strong late-wave policy
- interpretation:
  - the pre-prayer baseline already had excellent ceiling and late-wave competence
  - the prayer timing fix in `v_tmp2` appears to have traded some ceiling for much better stability and much cleaner tactical behavior
  - the collapse in `v_tmp1` was therefore not caused by removing the old pre-prayer behavior alone; it points much more strongly at the later obs / reward follow-up changes

---

## v19.3 (2026-04-08, completed)

Naming note:
- this run used the exact wave-0 checkpoint-transfer recipe that had been
  staged immediately before launch
- to keep the run-history numbering aligned with the latest discussion, the
  executed run is recorded here as `v19.3`
- actual run id:
  - `hl0yb7qa`
- warm-start source run id:
  - `8u6flr5y` (`v19.1`)
- warm-start checkpoint used:
  - [0000000263192576.bin](/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/8u6flr5y/0000000263192576.bin)

Mainline direction:
- checkpoint transfer run
- warm-start from the best early `v19.1` checkpoint window
- no sweep
- no curriculum
- same backend / same current pruned obs / same no-Jad-telegraph logic

Why `v19.3` exists:
- `v19.1` proved that direct wave-31 exposure can teach real late-wave
  mechanics very quickly
- the next question was whether that competence would transfer back to
  unbiased full-cave play from wave 1
- `v19.3` is the clean test of that transfer:
  - keep the exact `v19` reward / PPO recipe
  - remove curriculum completely
  - warm-start from the best `v19.1` checkpoint before the curriculum run
    regressed

Budget:
- `v19.1`: `2.5B`
- `v19.3`: `2.5B`

Run / trainer shape:
- continuation run, not scratch
- no `[sweep]` section
- no curriculum buckets
- same PPO / reward recipe as `v19`
- warm-start from:
  - `8u6flr5y/0000000263192576.bin`

What stays the same as `v19`:
- `learning_rate = 0.0003`
- `anneal_lr = 0`
- `clip_coef = 0.2`
- `ent_coef = 0.01`
- `num_buffers = 2`
- same reward weights and shaping
- same `w_attack_attempt = 0.1`
- same `2.5B` budget used in `v19.1`

What changes versus `v19.1`:
- `load_model_path: None -> 8u6flr5y/0000000263192576.bin`
- `curriculum_wave: 31 -> 0`
- `curriculum_pct: 1.0 -> 0.0`
- all curriculum buckets disabled

Exact active config:
- run setup: `load_model_path=/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/8u6flr5y/0000000263192576.bin`, no curriculum, `policy_obs=106`, `puffer_obs=142`
- reward weights: `w_damage_dealt=0.5`, `w_attack_attempt=0.1`, `w_damage_taken=-0.6`, `w_npc_kill=3.0`, `w_wave_clear=10.0`, `w_jad_damage=2.0`, `w_jad_kill=50.0`, `w_player_death=-20.0`, `w_cave_complete=100.0`, `w_correct_danger_prayer=0.5`, `w_invalid_action=-0.1`, `w_tick_penalty=-0.005`
- shaping: `shape_food_full_waste_penalty=-6.5`, `shape_food_waste_scale=-1.2`, `shape_food_safe_hp_threshold=1.0`, `shape_pot_full_waste_penalty=-6.5`, `shape_pot_waste_scale=-1.2`, `shape_pot_safe_prayer_threshold=1.0`, `shape_wrong_prayer_penalty=-1.0`, `shape_npc_specific_prayer_bonus=2.5`, `shape_npc_melee_penalty=-0.3`, `shape_wasted_attack_penalty=-0.1`, `shape_wave_stall_start=500`, `shape_wave_stall_base_penalty=-0.5`, `shape_wave_stall_ramp_interval=50`, `shape_wave_stall_cap=-2.0`, `shape_not_attacking_grace_ticks=2`, `shape_not_attacking_penalty=-0.01`, `shape_kiting_reward=1.0`, `shape_kiting_min_dist=5`, `shape_kiting_max_dist=7`, `shape_unnecessary_prayer_penalty=-0.2`, `shape_resource_threat_window=2`
- runtime: `total_agents=4096`, `num_buffers=2`, `total_timesteps=2_500_000_000`, `learning_rate=0.0003`, `gamma=0.999`, `gae_lambda=0.95`, `clip_coef=0.2`, `vf_coef=0.5`, `ent_coef=0.01`, `max_grad_norm=0.5`, `horizon=256`, `minibatch_size=4096`, `hidden_size=256`, `num_layers=2`

Results (`hl0yb7qa`):
- completed normally
- continuation run confirmed:
  - `load_model_path = 8u6flr5y/0000000263192576.bin`
  - no sweep
  - no curriculum
- final trainer step: `2,499,805,184 / 2,500,000,000`
- runtime: `1154.2s`
- throughput: `2.18M SPS`

Final metrics:
- `episode_return = 5539.23`
- `episode_length = 4869.51`
- `wave_reached = 30.66`
- `max_wave = 33`
- `most_npcs_slayed = 124`
- `score = 0.0`
- `prayer_uptime = 0.896`
- `prayer_uptime_melee = 0.356`
- `prayer_uptime_range = 0.540`
- `prayer_uptime_magic = 0.000001`
- `correct_prayer = 1785.32`
- `wrong_prayer_hits = 70.83`
- `no_prayer_hits = 35.02`
- `prayer_switches = 600.17`
- `damage_blocked = 25106.45`
- `dmg_taken_avg = 2502.86`
- `attack_when_ready_rate = 0.301`
- `pots_used = 23.80`
- `avg_prayer_on_pot = 0.562`
- `food_eaten = 13.63`
- `avg_hp_on_food = 0.905`
- `food_wasted = 12.23`
- `pots_wasted = 4.57`
- `tokxil_melee_ticks = 11.99`
- `ketzek_melee_ticks = 0.56`
- `reached_wave_30 = 0.991`
- `cleared_wave_30 = 0.540`
- `reached_wave_31 = 0.540`

Trajectory:
- `126M` steps:
  - `wave_reached = 24.1`
  - `episode_return = 3225.1`
- `239M` steps:
  - `wave_reached = 28.1`
  - `episode_return = 4533.9`
- `369M` steps:
  - `wave_reached = 29.8`
  - `episode_return = 5085.4`
- `973M` steps:
  - `wave_reached = 30.0`
  - `episode_return = 5358.5`
- `1.26B` steps:
  - `wave_reached = 30.0`
  - `episode_return = 5890.7`
- `1.89B` steps:
  - `wave_reached = 30.0`
  - `episode_return = 7203.7`
- `2.26B` steps:
  - `wave_reached = 30.5`
  - `episode_return = 7413.9`
  - this was the strongest window
- final:
  - `wave_reached = 30.66`
  - `episode_return = 5539.23`

What went right:
- this is the best **unbiased full-cave average-wave run so far**
  on the current stack
- it is the first run to combine:
  - `wave_reached > 30.5`
  - `cleared_wave_30 > 0.5`
  - much lower food/pot use
  - much lower no-prayer exposure
- transfer from the `v19.1` curriculum checkpoint was clearly real

Comparison to `v19` (`12gtkmfc`):
- `wave_reached: 30.66 vs 29.29`
- `max_wave: 33 vs 33`
- `episode_return: 5539 vs 5462`
- `reached_wave_30: 0.991 vs 0.594`
- `cleared_wave_30: 0.540 vs 0.0`
- `reached_wave_31: 0.540 vs 0.0`
- `dmg_taken_avg: 2503 vs 3016`
- `pots_used: 23.8 vs 30.4`
- `food_eaten: 13.6 vs 20.0`
- `avg_prayer_on_pot: 0.562 vs 0.762`
- `pots_wasted: 4.57 vs 20.38`
- `food_wasted: 12.23 vs 19.33`
- `no_prayer_hits: 35.0 vs 119.6`
- `wrong_prayer_hits: 70.8 vs 84.0`
- `damage_blocked: 25106 vs 16176`

Interpretation versus `v19`:
- the checkpoint transfer worked
- the biggest gains were not raw late-wave mechanics by themselves
- the biggest gains were:
  - much better resource conservation
  - much lower unprotected damage exposure
  - much stronger conversion of wave-30 entries into actual wave-31 starts
- this strongly supports the view that the old `29-30` wall was not just
  “missing Ket-Zek reps”; it was also a resource / tempo wall

Comparison to `v19.1` (`8u6flr5y`):
- direct raw depth is not comparable because `v19.1` used forced wave-31
  curriculum
- useful side-metric comparisons:
  - `dmg_taken_avg: 2503 vs 4162`
  - `pots_used: 23.8 vs 29.2`
  - `food_eaten: 13.6 vs 19.9`
  - `avg_prayer_on_pot: 0.562 vs 0.434`
  - `pots_wasted: 4.57 vs 4.63`
  - `food_wasted: 12.23 vs 14.83`
  - `attack_when_ready_rate: 0.301 vs 0.138`
  - `prayer_uptime_magic: ~0.000 vs 0.483`

Interpretation versus `v19.1`:
- the curriculum run successfully taught something transferable, but it was
  **not** mainly durable magic-prayer behavior
- what transferred best was:
  - better offensive tempo
  - better potion/food discipline
  - lower damage intake
  - better overall conversion through the `29-30` wall
- what did **not** transfer cleanly was late-wave magic-prayer usage
- `prayer_uptime_magic` collapsing back to ~0 is one of the strongest signs
  that this run did not preserve the full Ket-Zek-specific behavior from
  `v19.1`

Comparison to `v18.1` (`xm6i52ta`):
- `wave_reached: 30.66 vs 29.24`
- `max_wave: 33 vs 34`
- `episode_return: 5539 vs 5535`
- `reached_wave_30: 0.991 vs 0.306`
- `cleared_wave_30: 0.540 vs 0.0`
- `reached_wave_31: 0.540 vs 0.0`
- `dmg_taken_avg: 2503 vs 3580`
- `pots_used: 23.8 vs 31.5`
- `food_eaten: 13.6 vs 20.0`
- `avg_prayer_on_pot: 0.562 vs 0.690`
- `pots_wasted: 4.57 vs 13.3`
- `food_wasted: 12.23 vs 19.4`
- `no_prayer_hits: 35.0 vs 77.5`
- `wrong_prayer_hits: 70.8 vs 143.4`
- `damage_blocked: 25106 vs 34386`

Interpretation versus `v18.1`:
- `v19.3` is much more efficient and much more practical on whole-cave metrics
- it does not match `v18.1` in raw prayer/blocking volume, but it no longer
  needs to; it is getting deeper while spending far fewer resources
- the trade is:
  - less overall blocking activity
  - much better efficiency and much better milestone conversion

Comparison to `v1_retro` (`yjxqnott`):
- `wave_reached: 30.66 vs 30.10`
- `max_wave: 33 vs 34`
- `episode_return: 5539 vs 4662`
- `reached_wave_30: 0.991 vs 1.0`
- `cleared_wave_30: 0.540 vs 0.500`
- `reached_wave_31: 0.540 vs 0.500`
- `dmg_taken_avg: 2503 vs 4286`
- `wrong_prayer_hits: 70.8 vs 210.5`
- `no_prayer_hits: 35.0 vs 89.5`
- `pots_used: 23.8 vs 22.5`
- `food_eaten: 13.6 vs 20.0`
- `food_wasted: 12.23 vs 18.5`
- `pots_wasted: 4.57 vs 7.5`

Interpretation versus `v1_retro`:
- `v19.3` keeps the best part of `v1_retro`:
  - fast, aggressive progress through the cave
- but removes much of the chaos:
  - far lower damage taken
  - far fewer prayer mistakes
  - far less waste
- this is a clear sign that the modern shaping stack can work when seeded from
  the right policy state

Most important new insight:
- the `v19.1` checkpoint did **not** mainly transfer “Ket-Zek skill”
- it transferred a stronger **resource-efficient wave-29/30 policy**
- evidence:
  - dramatic improvement in food/pot use and waste
  - dramatic drop in no-prayer hits
  - large improvement in wave-30 -> wave-31 conversion
  - near-zero magic-prayer uptime in the transferred run
- that means the old bottleneck was more about:
  - resource preservation
  - ready-to-attack tempo
  - range/melee defensive discipline before and around the `29-30` wall
  than about pure Ket-Zek mechanics

Attack readiness signal:
- `attack_when_ready_rate = 0.301`
- this is far better than the late `v19.1` value of `0.138`
- the run still becomes somewhat more passive over time, but it does **not**
  collapse into the same severe passivity seen in the late curriculum run
- this is likely one of the main reasons resource use fell so much

What did not fully transfer:
- magic prayer
- explicit late-wave boss handling
- rare-event tail depth
  - `max_wave` did not exceed the best rare tails from `v18.1` / `v19.1`
- so `v19.3` is best understood as:
  - a very strong full-cave progression run
  - not yet a full late-game/Jad mastery run

Bottom line:
- `v19.3` is the strongest whole-cave run yet on average progress and
  milestone conversion
- the checkpoint transfer idea is validated
- the main benefit of the `v19.1` curriculum checkpoint was not direct
  Ket-Zek mastery; it was improved resource/tempo policy
- the next improvements should focus on preserving these gains while
  reintroducing durable magic-prayer / post-wave-30 competence

Best checkpoint window:
- strongest region appears around `~1.89B-2.26B`
- if replaying or reusing a checkpoint from this run, start in that window,
  not from the final checkpoint

## v19.2 (2026-04-08, staged)

Actual run id:
- none
- `v19.2` was staged only and was not launched under its own label

Mainline direction:
- checkpoint transfer run
- warm-start from `v19.1`
- no sweep
- no curriculum
- same backend / same current pruned obs / same no-Jad-telegraph logic

Why `v19.2` exists:
- `v19.1` proved the agent can learn late-wave mechanics quickly when exposed:
  - real magic-prayer usage
  - real mixed-threat switching
  - much better potion timing
  - repeated deep late-wave progress up to `max_wave = 63`
- that makes `v19.2` the clean transfer test:
  - keep the same `v19` reward/optimizer recipe
  - remove curriculum entirely
  - start from a strong `v19.1` checkpoint
  - see whether the learned late-wave competence transfers back to full-cave
    play from wave 1

Budget:
- `v19.1`: `2.5B`
- `v19.2`: `2.5B`

Checkpoint choice:
- warm-start source run id:
  - `8u6flr5y` (`v19.1`)
- warm-start from:
  - [0000000263192576.bin](/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/8u6flr5y/0000000263192576.bin)
- rationale:
  - this sits in the first strong `v19.1` peak window (`~237M-367M`)
  - it captures the early late-wave competence before the long-run regression
  - it is earlier and likely cleaner than later checkpoints from the same run

Run / trainer shape:
- continuation run, not scratch
- no `[sweep]` section
- no curriculum buckets
- same PPO / reward recipe as `v19`
- default launch path:
  - `cd /home/joe/projects/runescape-rl/codex3/runescape-rl && FORCE_BACKEND_REBUILD=1 LOAD_MODEL_PATH=/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/8u6flr5y/0000000263192576.bin bash train.sh`

What stays the same as `v19`:
- `learning_rate = 0.0003`
- `anneal_lr = 0`
- `clip_coef = 0.2`
- `ent_coef = 0.01`
- `num_buffers = 2`
- same reward weights and shaping
- same `w_attack_attempt = 0.1`
- same `2.5B` late-run budget as `v19.1`

What changes versus `v19.1`:
- `load_model_path: None -> 8u6flr5y/0000000263192576.bin`
- `curriculum_wave: 31 -> 0`
- `curriculum_pct: 1.0 -> 0.0`
- all late-wave curriculum buckets disabled

Results:
- not launched under the `v19.2` label
- this staged recipe was executed unchanged as `v19.3`
- executed run id:
  - `hl0yb7qa`
- warm-start source run id stayed the same:
  - `8u6flr5y` (`v19.1`)
- executed warm-start checkpoint stayed the same:
  - `8u6flr5y/0000000263192576.bin`
- for actual rollout metrics and trajectory, see the `v19.3` section above

Final metrics:
- none recorded under `v19.2` because no run completed under that label
- canonical results for this recipe live under `v19.3`

Why this is the right next test:
- if `v19.2` improves full-cave results from wave 1, then late-wave skill
  transfer is real and the curriculum run taught something reusable
- if `v19.2` still collapses around the old `29-30` shelf, then the problem is
  not just missing late-wave competence; it is likely deeper resource/tempo
  policy quality across the whole cave

What to watch:
- unbiased full-cave metrics again:
  - `wave_reached`
  - `reached_wave_30`
  - `cleared_wave_30`
  - `reached_wave_31`
- whether the `v19.1` improvements transfer:
  - `prayer_uptime_magic`
  - `no_prayer_hits`
  - `avg_prayer_on_pot`
  - `pots_wasted`
  - `attack_when_ready_rate`
- whether the agent arrives late with more resources rather than merely having
  learned late-wave switching in isolation

Live `v19.2` config block:

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
w_correct_danger_prayer = 0.5
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
shape_wrong_prayer_penalty = -1.0
shape_npc_specific_prayer_bonus = 2.5
shape_npc_melee_penalty = -0.3
shape_wasted_attack_penalty = -0.1
shape_wave_stall_start = 500
shape_wave_stall_base_penalty = -0.5
shape_wave_stall_ramp_interval = 50
shape_wave_stall_cap = -2.0
shape_not_attacking_grace_ticks = 2
shape_not_attacking_penalty = -0.01
shape_kiting_reward = 1.0
shape_kiting_min_dist = 5
shape_kiting_max_dist = 7
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
total_timesteps = 2_500_000_000
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

## v19.1 (2026-04-08, completed)

Actual run id:
- `8u6flr5y`

Mainline direction:
- scratch run
- no checkpoint
- no sweep
- forced curriculum
- same backend / same current pruned obs / same no-Jad-telegraph logic

Why `v19.1` exists:
- `v19` validated the `v1_retro` optimizer base and reached the `~29-30`
  shelf very quickly
- but `v19` still failed to convert that shelf into stable wave-31+ progress,
  and `prayer_uptime_magic = 0.0` strongly suggests weak Ket-Zek / late-wave
  magic handling
- `v19.1` is a direct diagnostic run:
  - always start at wave 31
  - keep the exact `v19` optimizer and reward recipe
  - force late-wave Ket-Zek / boss exposure
  - determine whether late-game mechanics are the wall, or whether the real
    wall is arriving there in poor shape from earlier waves

Budget:
- `v19`: `10B`
- `v19.1`: `2.5B`

Run / trainer shape:
- scratch run, not continuation
- no `[sweep]` section
- curriculum always starts at wave 31:
  - `curriculum_wave = 31`
  - `curriculum_pct = 1.0`
  - all other curriculum buckets disabled
- default launch path:
  - `cd /home/joe/projects/runescape-rl/codex3/runescape-rl && bash train.sh`

Key differences from `v19`:
- same PPO settings
- same vectorization
- same policy architecture
- same reward shaping
- same `w_attack_attempt = 0.1`
- only two intentional changes:
  - `total_timesteps: 10B -> 2.5B`
  - curriculum forced to wave 31 for 100% of episodes

Why this is worth testing:
- if `v19.1` quickly learns meaningful magic-prayer uptime, Ket-Zek handling,
  and late-wave conversion, then the late-game mechanics themselves are likely
  the missing experience
- if `v19.1` still fails despite constant wave-31 exposure, then the real wall
  is probably broader policy quality:
  - bad early-wave tempo
  - poor resource state arriving late
  - insufficient general combat quality rather than insufficient Ket-Zek reps

Analytic expectation:
- `attack_when_ready_rate` should appear in this run
- the local launch path now rebuilds the backend automatically when
  `training-env` sources are newer than the compiled extension, so the next
  local `train.sh` launch should pick up the new reward/analytic path instead
  of reusing the stale backend that masked it in `v19`

Live `v19.1` config block:

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
w_correct_danger_prayer = 0.5
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
shape_wrong_prayer_penalty = -1.0
shape_npc_specific_prayer_bonus = 2.5
shape_npc_melee_penalty = -0.3
shape_wasted_attack_penalty = -0.1
shape_wave_stall_start = 500
shape_wave_stall_base_penalty = -0.5
shape_wave_stall_ramp_interval = 50
shape_wave_stall_cap = -2.0
shape_not_attacking_grace_ticks = 2
shape_not_attacking_penalty = -0.01
shape_kiting_reward = 1.0
shape_kiting_min_dist = 5
shape_kiting_max_dist = 7
shape_unnecessary_prayer_penalty = -0.2
shape_resource_threat_window = 2
curriculum_wave = 31
curriculum_pct = 1.0
curriculum_wave_2 = 0
curriculum_pct_2 = 0.0
curriculum_wave_3 = 0
curriculum_pct_3 = 0.0
disable_movement = 0

[vec]
total_agents = 4096
num_buffers = 2

[train]
total_timesteps = 2_500_000_000
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

Results (`8u6flr5y`):
- completed normally
- scratch run confirmed:
  - `load_model_path = None`
  - no sweep
  - curriculum fixed at wave 31 for all episodes
- final trainer step: `2,499,805,184 / 2,500,000,000`
- runtime: `1166.3s`
- throughput: `2.11M SPS`

Final metrics:
- `episode_return = 8175.82`
- `episode_length = 6547.15`
- `wave_reached = 44.10`
- `max_wave = 63`
- `most_npcs_slayed = 154`
- `score = 0.0`
- `prayer_uptime = 0.934`
- `prayer_uptime_melee = 0.164`
- `prayer_uptime_range = 0.287`
- `prayer_uptime_magic = 0.483`
- `correct_prayer = 3588.02`
- `wrong_prayer_hits = 186.33`
- `no_prayer_hits = 19.19`
- `prayer_switches = 3795.27`
- `damage_blocked = 294652.41`
- `dmg_taken_avg = 4161.77`
- `attack_when_ready_rate = 0.138`
- `pots_used = 29.22`
- `avg_prayer_on_pot = 0.434`
- `food_eaten = 19.91`
- `avg_hp_on_food = 0.858`
- `food_wasted = 14.83`
- `pots_wasted = 4.63`
- `tokxil_melee_ticks = 7.31`
- `ketzek_melee_ticks = 14.95`
- `reached_wave_30 = 1.0`
- `cleared_wave_30 = 1.0`
- `reached_wave_31 = 1.0`

Critical caveat:
- raw wave milestones are heavily contaminated by curriculum here
- `reached_wave_30`, `cleared_wave_30`, and `reached_wave_31` are trivial
  because every episode starts at wave 31
- `wave_reached = 44.10` is still meaningful as a late-wave survival / progress
  measure, but it is **not** comparable to scratch wave-1 averages from
  `v19`, `v18.1`, or `v1_retro`

Trajectory:
- `2.1M` steps:
  - `wave_reached = 31.2`
  - `episode_return = -31.8`
  - `attack_when_ready_rate = 0.789`
- `123.7M` steps:
  - `wave_reached = 53.6`
  - `episode_return = 13066.5`
- `237.0M` steps:
  - `wave_reached = 57.9`
  - `episode_return = 17175.7`
  - this was the strongest late-wave checkpoint window
- `367.0M` steps:
  - `wave_reached = 57.4`
  - `episode_return = 17213.0`
- `971.0M` steps:
  - `wave_reached = 54.7`
  - `episode_return = 15909.1`
- `1.63B` steps:
  - `wave_reached = 45.0`
  - `episode_return = 8406.8`
- `2.50B` steps:
  - `wave_reached = 44.1`
  - `episode_return = 8175.8`
  - `attack_when_ready_rate = 0.138`

What the run proves:
- direct wave-31 exposure works
- the agent very quickly learned real late-wave mechanics:
  - non-trivial `prayer_uptime_magic = 0.483`
  - very high `correct_prayer`
  - very low `no_prayer_hits`
  - `max_wave = 63`
- this strongly argues that Ket-Zek itself is **not** the main hard wall
- if Ket-Zek were the main blocker, the run would not rapidly climb into the
  mid/upper-50s and repeatedly reach Jad-wave territory

What improved versus `v19`:
- much better late-wave prayer handling:
  - `prayer_uptime_magic: 0.483 vs 0.000`
  - `correct_prayer: 3588 vs 1233`
  - `damage_blocked: 294652 vs 16176`
  - `no_prayer_hits: 19.2 vs 119.6`
  - `prayer_switches: 3795 vs 613`
- much better potion timing:
  - `avg_prayer_on_pot: 0.434 vs 0.762`
  - `pots_wasted: 4.63 vs 20.38`
- somewhat better food timing:
  - `avg_hp_on_food: 0.858 vs 0.929`
  - `food_wasted: 14.83 vs 19.33`

What did not improve enough:
- total resource consumption is still extremely high:
  - `pots_used = 29.22`
  - `food_eaten = 19.91`
- the agent still burns essentially the whole inventory
- `dmg_taken_avg = 4161.77` is worse than `v19` and worse than `v18.1`
- `wrong_prayer_hits = 186.33` is still high in absolute terms
- `score = 0.0` even though `max_wave = 63`

Most important new insight:
- the remaining issue is not “the agent never learned magic prayer”
- the remaining issue looks more like:
  - high-pressure mixed-threat execution is still too sloppy
  - the agent survives long enough to see the late game, but takes too much
    damage and spends too many resources doing it
  - it reaches Jad territory, but not in a strong enough state to convert

Attack readiness signal:
- this was the first run where `attack_when_ready_rate` was successfully logged
- it is extremely informative
- the metric collapses over training:
  - `0.789 -> 0.401 -> 0.230 -> 0.181 -> 0.138`
- interpretation:
  - the policy becomes increasingly passive on ticks where its weapon is ready
  - it over-shifts toward survival / prayer maintenance and gives up offense
  - that likely contributes directly to:
    - longer episodes
    - more incoming attacks
    - more prayer drain
    - more food use
    - more resource depletion before a clear is secured

Comparison to `v19`:
- `v19.1` is **not** directly comparable on raw depth because of curriculum
- but on late-wave side metrics it is decisively more competent:
  - it learned magic prayer
  - it learned switching under dense threat
  - it learned much better potion timing
- that means the absence of late-wave reps in full scratch runs was a real
  weakness
- however, `v19.1` also shows that late-wave reps alone are not sufficient to
  solve the cave from wave 1

Comparison to `v18.1`:
- `v18.1` remained a stronger whole-cave policy in terms of unbiased wave-1
  average
- but `v19.1` teaches a different lesson:
  - the agent can absolutely learn the late-wave mechanics when exposed
  - the late-game wall is not purely a missing-signal problem anymore

Bottom line:
- your current read is partly right, but with one important refinement
- it is **not** mainly “bad potion timing” anymore
- `v19.1` learned potion timing surprisingly well
- the bigger remaining problem is:
  - the agent still takes too much damage in late-wave mixed-threat combat
  - it still spends nearly the full inventory
  - it becomes too passive about attacking when ready
- so the wall looks more like **late-wave tempo + mixed-threat execution +
  resource burn under pressure**, not a simple inability to use magic prayer or
  handle Ket-Zek in isolation

Best checkpoint window:
- if you want replay targets from this run, use the `~237M-367M` region
- the final checkpoint is clearly worse than the early/mid-run peak

## v19 (2026-04-08, completed)

Mainline direction:
- scratch run
- no checkpoint
- no sweep
- no curriculum
- same backend / same current pruned obs / same no-Jad-telegraph logic

Why `v19` exists:
- `v18.1` eventually recovered the wave-29 shelf, but only after a very long
  budget
- `v1_retro` reached comparable depth dramatically faster on the modern stack
- `v19` is the intended hybrid:
  - keep the strong `v1_retro` optimizer pressure and tempo
  - keep a trimmed subset of the modern shaping instead of zeroing nearly
    everything
  - stay fully scratch so we do not inherit the continuation failures seen in
    `v18.2` / `v18.3`

Budget:
- `v1_retro`: `500M`
- `v18.1`: `10B`
- `v19`: `10B`

Run / trainer shape:
- scratch run, not continuation
- no `[sweep]` section
- no curriculum buckets
- default launch path:
  - `cd /home/joe/projects/runescape-rl/codex3/runescape-rl && bash train.sh`

Key PPO / vectorization choices:
- copy the `v1_retro` base:
  - `learning_rate = 0.0003`
  - `anneal_lr = 0`
  - `clip_coef = 0.2`
  - `ent_coef = 0.01`
  - `num_buffers = 2`
- keep the stable shared core:
  - `gamma = 0.999`
  - `gae_lambda = 0.95`
  - `vf_coef = 0.5`
  - `max_grad_norm = 0.5`
  - `horizon = 256`
  - `minibatch_size = 4096`
  - `total_agents = 4096`
  - `hidden_size = 256`
  - `num_layers = 2`

Core reward intent:
- keep `v1_retro` offensive pressure:
  - `w_damage_dealt = 0.5`
  - `w_npc_kill = 3.0`
  - `w_tick_penalty = -0.005`
  - new `w_attack_attempt = 0.1`
- soften damage punishment relative to `v18.1`, but not all the way back to
  `v1_retro`:
  - `w_damage_taken = -0.6`
- keep only moderate prayer guidance:
  - `w_correct_danger_prayer = 0.5`
  - `shape_wrong_prayer_penalty = -1.0`
  - `shape_npc_specific_prayer_bonus = 2.5`
- keep modern anti-waste shaping, but stronger:
  - food/pot waste penalties increased beyond `v18.1`
- keep tempo shaping, but reduce overconstraint:
  - `shape_wasted_attack_penalty = -0.1`
  - `shape_wave_stall_base_penalty = -0.5`
  - `shape_wave_stall_cap = -2.0`
  - `shape_not_attacking_penalty = -0.01`

New reward term:
- `w_attack_attempt = 0.1`
- this rewards a real attack cycle being launched, even if the later hit
  misses or rolls zero
- it does **not** replace `w_damage_dealt`
- paired backend fix already landed:
  - “not attacking” now only counts when the weapon cooldown is ready, not
    during time spent waiting between attacks

Config deltas versus `v18.1`:
- `learning_rate: 0.001 -> 0.0003`
- `anneal_lr: 1 -> 0`
- `ent_coef: 0.02 -> 0.01`
- `num_buffers: 1 -> 2`
- `w_damage_dealt: 1.0 -> 0.5`
- `w_attack_attempt: new -> 0.1`
- `w_damage_taken: -0.75 -> -0.6`
- `w_npc_kill: 2.0 -> 3.0`
- `w_tick_penalty: -0.001 -> -0.005`
- `w_food_used: -0.5 -> 0.0`
- `w_food_used_well: 1.0 -> 0.0`
- `w_prayer_pot_used: -1.0 -> 0.0`
- `w_correct_danger_prayer: 1.0 -> 0.5`
- `shape_food_full_waste_penalty: -5.0 -> -6.5`
- `shape_food_waste_scale: -1.0 -> -1.2`
- `shape_pot_full_waste_penalty: -5.0 -> -6.5`
- `shape_pot_waste_scale: -1.0 -> -1.2`
- `shape_npc_specific_prayer_bonus: 2.0 -> 2.5`
- `shape_npc_melee_penalty: -0.5 -> -0.3`
- `shape_wasted_attack_penalty: -0.3 -> -0.1`
- `shape_wave_stall_base_penalty: -0.75 -> -0.5`
- `shape_wave_stall_cap: -3.0 -> -2.0`
- `shape_not_attacking_penalty: -0.5 -> -0.01`

Config deltas versus `v1_retro`:
- same low-pressure PPO settings
- same offensive pressure:
  - `w_damage_dealt = 0.5`
  - `w_npc_kill = 3.0`
  - `w_tick_penalty = -0.005`
- but reintroduce a trimmed set of modern shaping instead of zeroing nearly
  all of it
- add the new attack reward:
  - `w_attack_attempt = 0.1`
- increase waste penalties substantially above `v1_retro`
- keep moderate prayer shaping instead of none

Live `v19` config block:

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
w_correct_danger_prayer = 0.5
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
shape_wrong_prayer_penalty = -1.0
shape_npc_specific_prayer_bonus = 2.5
shape_npc_melee_penalty = -0.3
shape_wasted_attack_penalty = -0.1
shape_wave_stall_start = 500
shape_wave_stall_base_penalty = -0.5
shape_wave_stall_ramp_interval = 50
shape_wave_stall_cap = -2.0
shape_not_attacking_grace_ticks = 2
shape_not_attacking_penalty = -0.01
shape_kiting_reward = 1.0
shape_kiting_min_dist = 5
shape_kiting_max_dist = 7
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
total_timesteps = 10_000_000_000
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

Backend / obs:
- same backend
- same current pruned obs
- same no-Jad-telegraph logic
- same ammo `50,000`
- policy obs remains **106**
- reward features now **19** because of `attack_attempt`
- RL-facing puffer obs remains **142**

Pre-run expectation:
- should retain the fast depth-building behavior seen in `v1_retro`
- should be less sloppy with food/pot usage than `v1_retro`
- should avoid the continuation failures seen in `v18.2` / `v18.3`
- key thing to watch:
  - whether the added shaping keeps the speed while reducing the messiness,
    or whether it overconstrains the policy again

Results (`12gtkmfc`):
- completed normally
- scratch run confirmed:
  - `load_model_path = None`
  - no sweep
  - no curriculum
- final trainer step: `9,999,220,736 / 10,000,000,000`
- runtime: `4962.0s`
- throughput: `1.95M SPS`

Final metrics:
- `episode_return = 5462.44`
- `wave_reached = 29.29`
- `max_wave = 33`
- `reached_wave_30 = 0.594`
- `cleared_wave_30 = 0.0`
- `reached_wave_31 = 0.0`
- `correct_prayer = 1233.13`
- `wrong_prayer_hits = 84.02`
- `no_prayer_hits = 119.59`
- `prayer_switches = 612.80`
- `damage_blocked = 16176.20`
- `dmg_taken_avg = 3015.86`
- `pots_used = 30.42`
- `pots_wasted = 20.38`
- `food_eaten = 20.0`
- `food_wasted = 19.33`
- `avg_prayer_on_pot = 0.762`
- `avg_hp_on_food = 0.929`
- `prayer_uptime = 0.732`
- `prayer_uptime_range = 0.262`
- `prayer_uptime_magic = 0.0`
- `tokxil_melee_ticks = 3.30`
- `ketzek_melee_ticks = 0.0`

Trajectory:
- `1.252B` steps:
  - `wave_reached = 29.18`
  - `episode_return = 5467.62`
  - `reached_wave_30 = 0.783`
- `3.751B` steps:
  - `wave_reached = 30.0009`
  - `episode_return = 6389.86`
  - `reached_wave_30 = 0.977`
  - this was the best window of the run
- `6.251B` steps:
  - `wave_reached = 29.92`
  - `episode_return = 5870.51`
  - `reached_wave_30 = 0.893`
- `8.750B` steps:
  - `wave_reached = 29.58`
  - `episode_return = 5704.41`
  - `reached_wave_30 = 0.600`
- `10.000B` steps:
  - `wave_reached = 29.29`
  - `episode_return = 5462.44`
  - `reached_wave_30 = 0.594`

Comparison to `v1_retro` (`yjxqnott`, 500M):
- `v1_retro` was still stronger on depth and conversion:
  - `wave_reached: 30.10 vs 29.29`
  - `max_wave: 34 vs 33`
  - `reached_wave_30: 1.00 vs 0.594`
  - `cleared_wave_30: 0.50 vs 0.0`
  - `reached_wave_31: 0.50 vs 0.0`
- `v19` was much cleaner defensively:
  - `wrong_prayer_hits: 84.0 vs 210.5`
  - `dmg_taken_avg: 3015.9 vs 4286.5`
  - `tokxil_melee_ticks: 3.30 vs 62.0`
- but `v19` also blocked far less damage and switched far less:
  - `damage_blocked: 16176 vs 29157`
  - `prayer_switches: 613 vs 2286`
- interpretation:
  - `v19` traded away the messy high-ceiling aggression of `v1_retro`
  - it became calmer and safer, but that safety did not translate into
    better late-wave conversion

Comparison to `v18.1` (`xm6i52ta`, 10B):
- final performance was very similar, with `v19` only marginally better on
  average depth:
  - `wave_reached: 29.29 vs 29.24`
  - `episode_return: 5462 vs 5535`
  - `max_wave: 33 vs 34`
- the real improvement was sample efficiency:
  - `v19` was already at `29.18` by `1.25B`
  - `v18.1` was only at `26.28` at the same point
  - `v19` peaked by `3.75B`, while `v18.1` did not peak until `~6.26B`
- defensive profile versus `v18.1` was mixed:
  - better:
    - `wrong_prayer_hits: 84.0 vs 143.4`
    - `dmg_taken_avg: 3015.9 vs 3580.0`
  - worse:
    - `no_prayer_hits: 119.6 vs 77.5`
    - `damage_blocked: 16176 vs 34386`
    - `prayer_uptime: 0.732 vs 0.835`
    - `prayer_uptime_magic: 0.0 vs 0.041`
- interpretation:
  - `v19` reached the old shelf much faster
  - but it underused prayer, especially magic prayer, and never turned that
    faster climb into stronger deep-wave conversion

What worked:
- the `v1_retro` PPO base was validated again:
  - `learning_rate = 0.0003`
  - `anneal_lr = 0`
  - `ent_coef = 0.01`
  - `num_buffers = 2`
- `v19` shows that this base reaches the `~29-30` shelf far faster than
  `v18.1`
- the run was operationally stable for the full `10B` budget

What did not work:
- stronger food/pot waste shaping did **not** produce better resource
  behavior:
  - `pots_wasted` stayed high at `20.38`
  - `avg_prayer_on_pot = 0.762` was worse than both `v1_retro` and `v18.1`
  - `food_wasted` stayed essentially unchanged
- the moderate prayer shaping appears too weak and too static:
  - very low switching relative to prior strong runs
  - almost no magic-prayer uptime
  - much lower blocked damage
- the run peaked around `3.75B` and then steadily regressed

Most important caveat:
- `env/attack_when_ready_rate` is absent from the stored metrics for
  `12gtkmfc`
- that metric should have been present if the rebuilt backend/logging path was
  actually loaded
- so this run likely started from a stale compiled extension before the
  automatic rebuild fix landed
- because the same patchset added `w_attack_attempt`, treat the effect of
  `w_attack_attempt = 0.1` in `v19` as **unverified**

Bottom line:
- `v19` was a success relative to `v18.1` on sample efficiency
- `v19` was not a success relative to `v1_retro` on actual deep-wave
  conversion
- the reintroduced shaping likely overconstrained the policy again, while not
  actually improving the resource metrics it was supposed to improve

Best checkpoint window:
- if you want to replay or inspect the best point from this run, use the
  `~3.75B` region, not the final checkpoint
- that was the strongest combination of:
  - `wave_reached`
  - `episode_return`
  - `reached_wave_30`

## v1_retro (2026-04-08, completed)

Changes from the current `v18.3` reference setup:

This is a **fun retro scratch run**, not the next mainline experiment.

Core intent:
- revisit the original `v1` optimizer / reward recipe that first reached
  the wave-27 shelf
- run it on the **current** backend and **current** pruned obs contract
- keep it isolated from the mainline plan so it does not overwrite the live
  config

What `v1_retro` is:
- scratch run
- no checkpoint
- no sweep
- no curriculum
- `v1`-style PPO settings
- `v1`-style exposed reward weights as closely as the current code allows
- current backend / current obs / current trainer stack

What `v1_retro` is **not**:
- not an exact historical replay of the original `v1`
- not a true reproduction of the 2026-04-03 codebase
- not a replacement for the mainline `v18.3` / `v19` plan

Why it cannot be exact:
- the current obs contract is now the pruned **106-float** contract, not
  the early `v1` obs layout
- the current backend no longer has the old Jad telegraph behavior
- ammo is now effectively unconstrained at **50,000**
- some early `v1` weights are legacy/inactive in the modern reward code:
  - `w_food_used`
  - `w_prayer_pot_used`
  - `w_correct_jad_prayer`
  - `w_wrong_jad_prayer`
  - `w_idle`
- modern shaping terms exist that did not exist in `v1`, so `v1_retro`
  explicitly zeros them to approximate the earlier recipe

Why this run still has value:
- it answers a fun and useful question:
  - how does the original `v1` optimizer / reward style behave on the
    modern pruned backend?
- it gives a clean reference point for how much of the old early success
  came from:
  - the old reward mix
  - the old trainer settings
  - versus the old backend / old obs contract

Config / training changes from the then-current `v18.3` reference:
- change run type:
  - corrected continuation sweep -> isolated scratch train
- remove warm-start:
  - `load_model_path: xm6i52ta/... -> null`
- remove sweep:
  - no `[sweep]` section used
  - run as a normal `train`, not a `sweep`
- change timestep budget:
  - `400M -> 500M`
- restore the documented `v1` PPO settings:
  - `learning_rate: 0.0002 -> 0.0003`
  - `clip_coef: 0.10 -> 0.20`
  - `ent_coef: 0.005 -> 0.01`
  - `num_buffers: 1 -> 2`
- keep the rest of the core trainer shape the same as `v1`:
  - `gamma = 0.999`
  - `gae_lambda = 0.95`
  - `vf_coef = 0.5`
  - `max_grad_norm = 0.5`
  - `horizon = 256`
  - `minibatch_size = 4096`
  - `total_agents = 4096`
  - `hidden_size = 256`
  - `num_layers = 2`
- restore `v1`-style exposed reward weights where they still exist:
  - `w_damage_dealt = 0.5`
  - `w_damage_taken = -0.5`
  - `w_npc_kill = 3.0`
  - `w_wave_clear = 10.0`
  - `w_jad_damage = 2.0`
  - `w_jad_kill = 50.0`
  - `w_player_death = -20.0`
  - `w_cave_complete = 100.0`
  - `w_invalid_action = -0.1`
  - `w_tick_penalty = -0.005`
- explicitly disable most later-added shaping to make the run behave more
  like an early sparse-reward recipe:
  - no food/pot waste shaping
  - no general correct/wrong prayer shaping
  - no NPC-specific prayer bonus
  - no melee-range penalty
  - no wasted-attack penalty
  - no stall penalty
  - no not-attacking penalty
  - no kiting reward
  - no unnecessary-prayer penalty
- keep current backend / obs unchanged:
  - policy obs remains **106**
  - reward features remain **19**
  - RL-facing obs remains **142**
  - current no-telegraph Jad logic remains
  - current invalid-action signal remains live

`v1_retro` config block:

```ini
[base]
env_name = fight_caves
checkpoint_interval = 50

[env]
w_damage_dealt = 0.5
w_damage_taken = -0.5
w_npc_kill = 3.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.05
w_food_used_well = 0.0
w_prayer_pot_used = -0.05
w_correct_jad_prayer = 5.0
w_wrong_jad_prayer = -10.0
w_correct_danger_prayer = 0.0
w_wrong_danger_prayer = 0.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = -0.01
w_tick_penalty = -0.005
shape_food_full_waste_penalty = 0.0
shape_food_waste_scale = 0.0
shape_food_safe_hp_threshold = 1.0
shape_food_no_threat_penalty = 0.0
shape_pot_full_waste_penalty = 0.0
shape_pot_waste_scale = 0.0
shape_pot_safe_prayer_threshold = 1.0
shape_pot_no_threat_penalty = 0.0
shape_wrong_prayer_penalty = 0.0
shape_npc_specific_prayer_bonus = 0.0
shape_npc_melee_penalty = 0.0
shape_wasted_attack_penalty = 0.0
shape_wave_stall_start = 500
shape_wave_stall_base_penalty = 0.0
shape_wave_stall_ramp_interval = 50
shape_wave_stall_cap = 0.0
shape_not_attacking_grace_ticks = 2
shape_not_attacking_penalty = 0.0
shape_kiting_reward = 0.0
shape_kiting_min_dist = 5
shape_kiting_max_dist = 7
shape_unnecessary_prayer_penalty = 0.0
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
total_timesteps = 500_000_000
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

Hyperparameters:
- fixed:
  - `total_timesteps = 500M`
  - `learning_rate = 0.0003`
  - `anneal_lr = 0`
  - `gamma = 0.999`
  - `gae_lambda = 0.95`
  - `clip_coef = 0.2`
  - `vf_coef = 0.5`
  - `ent_coef = 0.01`
  - `max_grad_norm = 0.5`
  - `horizon = 256`
  - `minibatch_size = 4096`
  - `total_agents = 4096`
  - `num_buffers = 2`
  - `hidden_size = 256`
  - `num_layers = 2`
  - no checkpoint
  - no curriculum
  - no sweep

What `v1_retro` is actually testing:
- whether the original `v1` training recipe still climbs quickly on the
  modern stack
- whether the old wave-27 shelf was mostly:
  - optimizer / reward recipe,
  - or old backend / old obs specific

What `v1_retro` is **not** testing:
- not a pure historical reproduction
- no old obs contract
- no old Jad telegraph backend
- no exact old reward semantics for legacy/inactive weights

Success criteria:
- reproduce the old “fast early climb” flavor on the current stack
- see whether the run still naturally finds the old `~27` shelf
- use the result as a fun sanity reference, not as the main branch for
  future tuning

Operational notes:
- this run was isolated from the then-live `v18.3` sweep setup
- the current active config is now `v19` in `config/fight_caves.ini`

Pre-run expectation:
- likely outcome is not a full return to the exact old `v1` behavior,
  because the backend and obs are materially different now
- the most informative result would be:
  - whether the old low-pressure reward recipe still climbs fast and then
    stalls
  - or whether the modern stack changes that trajectory completely

Results (`yjxqnott`, W&B `snowy-paper-94`):
- completed normally
- state: `finished`
- final trainer step: `499,122,176 / 500,000,000`
- runtime: `253.34s`
- throughput: `2.26M SPS`

Final metrics:
- `episode_return = 4662.17`
- `wave_reached = 30.10`
- `max_wave = 34`
- `correct_prayer = 1144.0`
- `wrong_prayer_hits = 210.5`
- `no_prayer_hits = 89.5`
- `prayer_switches = 2286.5`
- `damage_blocked = 29157.0`
- `dmg_taken_avg = 4286.5`
- `tokxil_melee_ticks = 62.0`
- `reached_wave_30 = 1.0`
- `cleared_wave_30 = 0.5`

Trajectory:
- `65M` steps:
  - `wave_reached = 25.15`
  - `max_wave = 27.83`
  - `reached_wave_30 = 0.434`
  - `cleared_wave_30 = 0.152`
- `189M` steps:
  - `wave_reached = 30.10`
  - `max_wave = 32`
  - `reached_wave_30 = 0.996`
- `313M` steps:
  - `wave_reached = 29.96`
  - `max_wave = 32`
- `438M` steps:
  - `wave_reached = 30.02`
  - `max_wave = 32`
- `499M` steps:
  - `wave_reached = 30.10`
  - `max_wave = 34`
  - `cleared_wave_30 = 0.5`

Comparison to the documented original `v1`:
- original `v1` (doc only, no local JSON retained):
  - `wave_reached = 27.6`
  - `episode_return = 554.3`
  - `max shelf = ~27-28`
- `v1_retro` on the modern stack:
  - `wave_reached = 30.10`
  - `episode_return = 4662.17`
  - `max_wave = 34`

This means:
- the old recipe did **not** merely survive on the modern stack
- it got materially stronger on the modern stack
- so the current backend / obs improvements are not the problem
- the mainline regression is coming from the newer training recipe, not
  from the current environment itself

Comparison to the strong scratch baselines:
- vs `v18` at final (`lxttb7uo`, 1.5B):
  - `wave_reached: 30.10 vs 29.20`
  - `max_wave: 34 vs 31`
  - `damage_blocked: 29157 vs 24912`
  - `reached_wave_30: 1.00 vs 0.35`
  - `episode_return: 4662 vs 4956`
- vs `v18.1` at final (`xm6i52ta`, 10B):
  - `wave_reached: 30.10 vs 29.24`
  - `max_wave: 34 vs 34`
  - `damage_blocked: 29157 vs 34385`
  - `episode_return: 4662 vs 5535`

The striking part is not just the final value. It is the speed:
- `v1_retro` reached `wave_reached ~30` by **~189M** steps
- `v18` did not reach `wave_reached ~27` until **~940M** steps
- `v18` did not reach its `~29` plateau until **~1.31B-1.50B**

Interpretation:
- `v1_retro` is dramatically faster at converting early competence into
  late-wave depth
- but it does so with a much sloppier, higher-risk policy

What is clearly working in `v1_retro`:

1. Lower-pressure PPO settings
- `learning_rate = 0.0003` and `ent_coef = 0.01` appear to be much better
  for fast depth-building on the current stack than the `v18` scratch
  settings of `0.001 / 0.02`
- clipfrac stayed in a healthy learning band:
  - `0.028 -> 0.058`
- unlike the continuation family, this run learned aggressively without
  collapsing or freezing

2. Sparse, objective-heavy reward mix
- the recipe shifts emphasis away from “clean combat” shaping and toward
  actual progress:
  - lower `w_damage_dealt`
  - higher `w_npc_kill`
  - much harsher `w_tick_penalty`
  - most extra shaping disabled
- this appears to force the policy to prioritize:
  - killing things
  - clearing waves
  - moving forward
  over looking tidy on auxiliary metrics

3. Reduced punishment for risky progress
- `w_damage_taken = -0.5` is materially softer than the current `-0.75`
- `v1_retro` clearly accepts more damage in exchange for more progress
- that trade appears to be favorable for reaching late waves quickly

4. Removing prayer-side over-optimization
- `w_correct_danger_prayer = 0.0`
- no wrong-prayer shaping
- no NPC-specific prayer bonus
- the result is not “better prayer discipline”
- the result is a policy that is willing to keep moving and attacking,
  while switching prayers a huge amount
- this looks ugly, but it appears to unlock depth much sooner

5. Stronger time pressure
- `w_tick_penalty = -0.005` vs the modern `-0.001`
- this is a big difference
- even with no explicit stall penalty, the harsher living cost strongly
  rewards making progress quickly

6. `num_buffers = 2`
- this is not proven to be the main driver, but it is one of the few
  structural differences from the later successful scratch runs
- it may be helping with rollout freshness/diversity early in training

What `v1_retro` is missing / where it is clearly worse:

1. It is much sloppier defensively
- `wrong_prayer_hits = 210.5`
- `no_prayer_hits = 89.5`
- `dmg_taken_avg = 4286.5`
- `tokxil_melee_ticks = 62.0`
- compared to `v18`, this is a much messier policy

2. It seems to brute-force depth rather than stabilize it
- prayer switches exploded:
  - `532.9 -> 2286.5`
- blocked damage is high, but so are mistakes and total damage taken
- this looks like hyperactive adaptation, not calm mastery

3. It likely sacrifices long-run cleanliness for short-run progress
- `v1_retro` reaches deep waves much faster
- but the policy looks more chaotic and may be harder to extend cleanly to
  Jad without some later refinement

Main conclusion:
- the modern stack is capable of fast late-wave learning
- the **current mainline reward / PPO recipe is probably over-constrained**
- the biggest thing missing from the current configs is not more state
  information; it is **permission to play more aggressively and optimize the
  real objective sooner**

What `v1_retro` suggests the current configs are doing wrong:
- too much emphasis on clean prayer-side behavior early
- too much punishment for taking damage while progressing
- too many auxiliary shaping terms competing with the real goal
- too little time pressure
- possibly too much entropy / too much learning-rate noise for this
  particular modern stack

Best synthesis for future runs:
- do **not** adopt `v1_retro` wholesale as the new mainline
- it is too sloppy defensively
- but the next mainline scratch run should borrow from it

Most plausible ingredients to steal for `v19`:
- `learning_rate = 0.0003`
- `clip_coef = 0.2`
- `ent_coef = 0.01`
- consider `num_buffers = 2`
- reduce `w_damage_taken` from `-0.75` toward `-0.5` or `-0.6`
- increase objective pressure:
  - `w_npc_kill` toward `3.0`
  - stronger tick pressure than `-0.001`
- drastically simplify or reduce the extra shaping stack instead of adding
  more

Most important takeaway:
- the fast progress in `v1_retro` strongly argues that the current mainline
  recipe is teaching the agent to be **clean before competent**
- `v1_retro` succeeds because it does the opposite:
  - it becomes competent first
  - cleanliness comes later, if at all

---

## v18.3 (2026-04-08, first completed trial `tae450qe`)

Changes from `v18.2`:

This is a **corrected continuation sweep**.

Core intent:
- fix the `v18.2` sweep infrastructure bug so the search space actually
  varies
- remove the small late-wave curriculum so sweep metrics are directly
  comparable to the scratch baselines again
- make the default continuation recipe gentler than `v18.2`
- retry the continuation idea from a stronger `v18.1` checkpoint

Code changes from `v18.2`:
- fix nested `sweep_only` handling in
  `pufferlib_4/pufferlib/sweep.py`
  - `sweep_only` now matches against the full nested key path instead of
    only the leaf name
  - both full paths like `train/learning_rate` and leaf names like
    `learning_rate` now work
- add a fail-fast guard in `Hyperparameters`
  - if the sweep search space resolves to zero parameters, the run now
    errors immediately instead of silently launching repeated defaults
    and failing later in the GP phase

Why these code changes were necessary:
- `v18.2` did not execute a real sweep
- the parser bug filtered out all requested sweep parameters
- that produced **12 identical trials** and then a GP crash on the
  zero-dimensional search space
- `v18.3` has to fix that before any continuation conclusions are
  meaningful

Chosen warm-start checkpoint:
- default `LOAD_MODEL_PATH` is
  `/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/xm6i52ta/0000005977931776.bin`
- this is the `~5.98B` `v18.1` checkpoint
- rationale:
  - it sits in one of the strongest average-wave / return windows from
    `v18.1`
  - it is a better candidate than the early `~2.20B` checkpoint used in
    `v18.2`
- caveat:
  - this is still a training-log choice, not a fixed-eval winner
  - once fixed eval exists, checkpoint selection should move there

Config / training changes from `v18.2`:
- keep run type the same:
  - continuation sweep -> continuation sweep
- change warm-start checkpoint:
  - `xm6i52ta/0000002203058176.bin -> xm6i52ta/0000005977931776.bin`
- remove curriculum entirely:
  - `curriculum_wave: 30 -> 0`
  - `curriculum_pct: 0.05 -> 0.0`
  - `curriculum_wave_2: 31 -> 0`
  - `curriculum_pct_2: 0.02 -> 0.0`
- make the continuation recipe gentler:
  - `learning_rate: 0.0003 -> 0.0002`
  - `clip_coef: 0.15 -> 0.10`
  - `ent_coef: 0.01 -> 0.005`
- reduce per-trial budget:
  - `total_timesteps: 600M -> 400M`
- reduce total sweep budget for the first corrected run:
  - `max_runs: 24 -> 16`
- narrow the sweep space to the continuation knobs only:
  - keep `sweep_only = train/learning_rate, train/clip_coef, train/ent_coef`
  - remove curriculum knobs from the sweep
- keep rewards unchanged from `v18/v18.1/v18.2`:
  - `w_damage_dealt = 1.0`
  - `w_npc_kill = 2.0`
  - `w_correct_danger_prayer = 1.0`
  - no extra threat-aware food/pot penalties
  - capped stall penalty stays on
- keep current backend / obs unchanged:
  - policy obs remains **106**
  - reward features remain **18**
  - RL-facing obs remains **142**
  - Jad telegraph remains removed from both obs and backend
  - ammo remains **50,000**

Why these config changes were necessary:
- `v18.2` showed two separate issues:
  - the sweep never varied
  - the default continuation recipe itself looked like active unlearning
- the next clean test should therefore:
  - eliminate the sweep bug
  - eliminate curriculum contamination
  - move to a softer continuation band
- `v18.3` is meant to answer:
  - whether a **corrected**, **no-curriculum**, **gentler** continuation
    can preserve and improve a strong `v18.1` checkpoint

Live `fight_caves.ini`:

```ini
[base]
env_name = fight_caves
checkpoint_interval = 50

[env]
w_damage_dealt = 1.0
w_damage_taken = -0.75
w_npc_kill = 2.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.5
w_food_used_well = 1.0
w_prayer_pot_used = -1.0
w_correct_jad_prayer = 0.0
w_wrong_jad_prayer = 0.0
w_correct_danger_prayer = 1.0
w_wrong_danger_prayer = 0.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = 0.0
w_tick_penalty = -0.001
shape_food_full_waste_penalty = -5.0
shape_food_waste_scale = -1.0
shape_food_safe_hp_threshold = 1.0
shape_food_no_threat_penalty = 0.0
shape_pot_full_waste_penalty = -5.0
shape_pot_waste_scale = -1.0
shape_pot_safe_prayer_threshold = 1.0
shape_pot_no_threat_penalty = 0.0
shape_wrong_prayer_penalty = -1.0
shape_npc_specific_prayer_bonus = 2.0
shape_npc_melee_penalty = -0.5
shape_wasted_attack_penalty = -0.3
shape_wave_stall_start = 500
shape_wave_stall_base_penalty = -0.75
shape_wave_stall_ramp_interval = 50
shape_wave_stall_cap = -3.0
shape_not_attacking_grace_ticks = 2
shape_not_attacking_penalty = -0.5
shape_kiting_reward = 1.0
shape_kiting_min_dist = 5
shape_kiting_max_dist = 7
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
num_buffers = 1

[train]
total_timesteps = 400_000_000
learning_rate = 0.0002
anneal_lr = 0
gamma = 0.999
gae_lambda = 0.95
clip_coef = 0.10
vf_coef = 0.5
ent_coef = 0.005
max_grad_norm = 0.5
horizon = 256
minibatch_size = 4096

[policy]
hidden_size = 256
num_layers = 2

[sweep]
method = Protein
metric = wave_reached
metric_distribution = linear
goal = maximize
max_suggestion_cost = 1200
max_runs = 16
gpus = 1
downsample = 5
use_gpu = True
prune_pareto = True
early_stop_quantile = 0.25
sweep_only = train/learning_rate, train/clip_coef, train/ent_coef

[sweep.train.learning_rate]
distribution = log_normal
min = 0.0001
max = 0.0003
scale = auto

[sweep.train.clip_coef]
distribution = uniform
min = 0.08
max = 0.12
scale = auto

[sweep.train.ent_coef]
distribution = log_normal
min = 0.003
max = 0.008
scale = auto
```

Hyperparameters:
- fixed:
  - gamma=`0.999`
  - gae_lambda=`0.95`
  - vf_coef=`0.5`
  - max_grad_norm=`0.5`
  - horizon=`256`
  - minibatch_size=`4096`
  - total_agents=`4096`
  - hidden_size=`256`
  - num_layers=`2`
  - total_timesteps=`400M`
  - anneal_lr=`0`
  - checkpoint_interval=`50`
  - no curriculum
- sweep space:
  - `learning_rate` in `[0.0001, 0.0003]`
  - `clip_coef` in `[0.08, 0.12]`
  - `ent_coef` in `[0.003, 0.008]`

What `v18.3` is actually testing:
- whether a strong `v18.1` checkpoint can be improved with:
  - smaller continuation steps
  - lower clip
  - lower entropy
  - no curriculum confounds
- whether the late-wave plateau is better attacked by a **gentle
  continuation** rather than a heavier continuation like `v18.2`

What `v18.3` is **not** testing:
- no new observations
- no backend mechanics changes
- no reward rewrite
- no curriculum
- no broad PPO sweep

Success criteria:
- preserve frontier competence from the warm-start checkpoint
- avoid the severe regression seen in `v18.2`
- find at least one continuation setting that is competitive with, or
  better than, the `v18/v18.1` frontier on clean no-curriculum metrics
- keep prayer/combat competence healthy:
  - high `correct_prayer`
  - high `damage_blocked`
  - high `prayer_switches`
  - low `no_prayer_hits`

Operational notes:
- the active launch helper is `sweep_v18_3.sh`
- default local launch:
  - `cd /home/joe/projects/runescape-rl/codex3/runescape-rl && bash sweep_v18_3.sh`
- override for cloud or multi-GPU sweep fanout:
  - `SWEEP_GPUS=<n> TRAIN_GPUS=<m> bash sweep_v18_3.sh`
- override checkpoint if a better `v18.1` candidate is chosen:
  - `LOAD_MODEL_PATH=/path/to/checkpoint.bin bash sweep_v18_3.sh`

Pre-run expectation:
- if `v18.3` works, the win should show up as:
  - much less regression than `v18.2`
  - preserved prayer/blocking competence
  - at least some runs staying near the `v18/v18.1` frontier instead of
    collapsing to the low-20s
- if `v18.3` still regresses badly, the likely next step is:
  - checkpoint selection by fixed eval before more continuation tuning,
    or
  - a return to scratch-based improvements instead of continuation

First completed trial:
- W&B run `tae450qe` (`wild-puddle-93`)
- state: `finished`
- this trial completed normally to budget; it did **not** crash and did
  **not** early-stop
- final trainer step: `399,507,456 / 400,000,000`
- sampled hyperparameters for this trial:
  - `learning_rate = 0.0002626368403335551`
  - `clip_coef = 0.11023490528350335`
  - `ent_coef = 0.004868108593398205`

Results:
- `wave_reached = 17.92`
- `max_wave = 24`
- `episode_return = 1305.30`
- `correct_prayer = 252.58`
- `wrong_prayer_hits = 34.35`
- `no_prayer_hits = 127.45`
- `prayer_switches = 105.56`
- `damage_blocked = 1712.60`
- `dmg_taken_avg = 2796.51`
- `tokxil_melee_ticks = 7.12`
- `reached_wave_30 = 0.0`
- `cleared_wave_30 = 0.0`
- throughput remained normal:
  - `SPS = 1.99M`
  - `GPU util = 66%` final, with most of the run in the mid-80s
  - `VRAM = ~2.13 GB`
  - `runtime = 200.29s`

Learning dynamics:
- entropy fell from `7.62 -> 6.35`
- clipfrac rose from `0.076 -> 0.179`
- return improved early then faded:
  - `1183.9 -> 1330.6 -> 1429.4 -> 1334.9 -> 1305.3`
- wave reached peaked mid-run then regressed:
  - `18.77 -> 18.64 -> 18.94 -> 18.33 -> 17.92`
- max wave never moved beyond `24`
- prayer-switch frequency collapsed over training:
  - `250.6 -> 161.9 -> 171.9 -> 109.6 -> 105.6`
- prayer correctness improved while switching decreased:
  - `correct_prayer: 179.1 -> 252.6`
  - `wrong_prayer_hits: 76.9 -> 34.3`
  - `damage_blocked: 1533.0 -> 1712.6`
- damage taken did **not** improve meaningfully:
  - `2777.3 -> 2796.5`

Interpretation:
- The **sweep fix worked**. Unlike `v18.2`, this was a real varied trial,
  not one of many duplicated defaults.
- Operationally, `v18.3` is healthy:
  - no sweep parser failure
  - no GP crash
  - no NaNs
  - no throughput anomaly
- Behaviorally, this trial still **regressed hard** relative to the
  `v18/v18.1` frontier and did **not** preserve the strength of the
  `xm6i52ta` warm-start checkpoint.

What improved relative to the `v18.3_control` sanity run `wh0ef384`:
- `episode_return: 1260.75 -> 1305.30`
- `correct_prayer: 212.67 -> 252.58`
- `wrong_prayer_hits: 65.76 -> 34.35`
- `damage_blocked: 1058.92 -> 1712.60`
- `tokxil_melee_ticks: 7.36 -> 7.12`

What did **not** improve:
- `wave_reached: 18.31 -> 17.92`
- `max_wave: 25 -> 24`
- `no_prayer_hits: 98.19 -> 127.45`
- `dmg_taken_avg: 2747.68 -> 2796.51`
- `prayer_switches: 133.26 -> 105.56`

This is the key pattern:
- the sampled `v18.3` point produced a policy that was **cleaner on paper**
  in prayer correctness and blocked damage
- but it did **not** convert that into deeper progression
- instead it looks like a more conservative / less active continuation that
  gives up too much offensive tempo or late-wave adaptability

Comparison to the continuation families:
- vs `v18.2` representative trial `bbqcayi3`
  - `tae450qe` is cleaner and more stable operationally
  - it avoids curriculum contamination
  - it has much better prayer correctness and higher return
  - but that does **not** rescue continuation as a strategy; it is still far
    below the scratch frontier
- vs `v18.3_control` `wh0ef384`
  - `tae450qe` is not a disaster relative to the control
  - it is roughly the same weak continuation regime, just with slightly
    better blocking / prayer hygiene and slightly worse depth

Comparison to the scratch frontier:
- vs `v18` `lxttb7uo`
  - `wave_reached: 17.92 vs 29.20`
  - `episode_return: 1305.30 vs 4955.67`
  - `correct_prayer: 252.58 vs 1720.20`
  - `damage_blocked: 1712.60 vs 24912.17`
- vs `v18.1` `xm6i52ta`
  - `wave_reached: 17.92 vs 29.24`
  - `max_wave: 24 vs 34`
  - `correct_prayer: 252.58 vs 2407.14`
  - `damage_blocked: 1712.60 vs 34385.50`

The gap is too large to explain away as normal continuation noise.
The main conclusion is:
- **continuation is still failing to preserve frontier competence**
- the gentler `v18.3` band is safer than `v18.2`, but it is still not a
  good path to progress

Why this likely happened:
- the checkpoint was chosen from training history, not fixed eval
- the continuation recipe still pushes the policy away from the strong
  frontier behavior instead of consolidating it
- lower entropy / lower clip / lower LR improved defensive neatness but seem
  to have reduced the aggressive switching / tempo needed to actually
  progress deeper
- the current reward mix still appears more effective for **scratch
  learning** than for **checkpoint continuation**

Current `v18.3` verdict:
- sweep infrastructure: **fixed**
- continuation strategy: **still not validated**
- first completed trial: **regression**
- recommendation: **do not promote continuation as the main path forward**

What this means for the next run:
- The evidence now favors returning to a **scratch** run family rather than
  spending more time on checkpoint continuation.
- If continuation is revisited later, it should be after:
  - accelerated replay / checkpoint ranking exists
  - fixed-eval checkpoint selection exists
  - the continuation objective is better aligned with preserving frontier
    competence

Recommended next direction after this result:
- make `v19` a **no-sweep, no-checkpoint scratch run**
- start from the `v18` / `v18.1` recipe, not the continuation recipe
- keep the current backend / obs contract unchanged
- test only small, explicit late-wave changes rather than another broad
  search

Best current `v19` shape:
- scratch run
- no curriculum for the first pass
- no checkpoint warm-start
- keep the strong `v18` PPO base:
  - `learning_rate = 0.001`
  - `clip_coef = 0.2`
  - `ent_coef = 0.02`
- modify only a **small** number of shaping terms aimed at late-wave tempo
  rather than more prayer-side continuation tuning

Most likely useful `v19` experiment:
- preserve the `v18` reward backbone
- make the agent slightly more assertive about actually clearing waves:
  - slightly stronger `shape_wasted_attack_penalty`
  - slightly stronger `shape_not_attacking_penalty`
  - optionally a small increase to `w_npc_kill` or `w_wave_clear`, but not
    both aggressively
- do **not** make another large prayer-shaping jump like `v17`

Reason for this `v19` recommendation:
- the scratch family already learns the task
- the plateau appears more like a late-wave execution / tempo bottleneck
  than a missing prayer-signal bottleneck
- `tae450qe` specifically showed that making the policy “cleaner” on prayer
  alone did **not** buy more depth
- the next controlled change should therefore bias toward turning competent
  defense into faster, cleaner clears

---

## v18.2 (2026-04-07)

Changes from `v18.1`:

This is a **narrow checkpoint fine-tune sweep**, not another scratch run.

Core intent:
- warm-start from a strong `v18.1` checkpoint
- keep the current pruned backend / obs contract unchanged
- keep the `v18/v18.1` reward structure unchanged
- switch from a long scratch budget to a short continuation budget
- sweep only a few continuation knobs around the late-wave frontier

Chosen warm-start checkpoint:
- default `LOAD_MODEL_PATH` is
  `/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/xm6i52ta/0000002203058176.bin`
- this is the `~2.20B` `v18.1` checkpoint, chosen because it sits in the
  early strong plateau window and aligns with one of the best sampled
  `v18.1` return regions
- this is the **default starting point**, not a claim that it is already
  the globally best checkpoint by fixed eval; that should still be
  validated after replay/eval inspection

Config / training changes from `v18.1`:
- change run type:
  - from scratch control -> continuation sweep
- add warm-start:
  - `load_model_path: null -> xm6i52ta/0000002203058176.bin`
- shorten budget:
  - `total_timesteps: 10B -> 600M`
- disable LR annealing for the continuation:
  - `anneal_lr: 1 -> 0`
- change default continuation recipe to the smaller-noise settings:
  - `learning_rate: 0.001 -> 0.0003`
  - `clip_coef: 0.20 -> 0.15`
  - `ent_coef: 0.02 -> 0.01`
- reduce checkpoint spacing:
  - `checkpoint_interval: 100 -> 50`
- introduce a tiny fixed late-wave curriculum:
  - `curriculum_wave: 0 -> 30`
  - `curriculum_pct: 0.0 -> 0.05`
  - `curriculum_wave_2: 0 -> 31`
  - `curriculum_pct_2: 0.0 -> 0.02`
  - `curriculum_wave_3` remains `0`
  - `curriculum_pct_3` remains `0.0`
- keep rewards unchanged from `v18/v18.1`:
  - `w_damage_dealt = 1.0`
  - `w_npc_kill = 2.0`
  - `w_correct_danger_prayer = 1.0`
  - extra threat-aware food/pot penalties stay disabled
  - capped stall penalty stays on
- keep current backend / obs unchanged:
  - policy obs remains **106**
  - reward features remain **18**
  - RL-facing obs remains **142**
  - Jad telegraph remains removed from both obs and backend
  - ammo remains **50,000**

Why this run exists:
- `v18.1` showed that the current stack can occasionally push deeper
  (`max_wave 34`), but the average frontier stayed pinned near the same
  `~29-30` shelf as `v18`
- another long scratch rerun of the same recipe is therefore low-value
- the next useful test is whether a **warm-started, lower-noise
  continuation** can convert those rare deep penetrations into more
  stable late-wave progress
- this is also the right place to introduce sweeps, because the search
  space can now be kept narrow and local to the known-good regime

Live `fight_caves.ini`:

```ini
[base]
env_name = fight_caves
checkpoint_interval = 50

[env]
w_damage_dealt = 1.0
w_damage_taken = -0.75
w_npc_kill = 2.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.5
w_food_used_well = 1.0
w_prayer_pot_used = -1.0
w_correct_jad_prayer = 0.0
w_wrong_jad_prayer = 0.0
w_correct_danger_prayer = 1.0
w_wrong_danger_prayer = 0.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = 0.0
w_tick_penalty = -0.001
shape_food_full_waste_penalty = -5.0
shape_food_waste_scale = -1.0
shape_food_safe_hp_threshold = 1.0
shape_food_no_threat_penalty = 0.0
shape_pot_full_waste_penalty = -5.0
shape_pot_waste_scale = -1.0
shape_pot_safe_prayer_threshold = 1.0
shape_pot_no_threat_penalty = 0.0
shape_wrong_prayer_penalty = -1.0
shape_npc_specific_prayer_bonus = 2.0
shape_npc_melee_penalty = -0.5
shape_wasted_attack_penalty = -0.3
shape_wave_stall_start = 500
shape_wave_stall_base_penalty = -0.75
shape_wave_stall_ramp_interval = 50
shape_wave_stall_cap = -3.0
shape_not_attacking_grace_ticks = 2
shape_not_attacking_penalty = -0.5
shape_kiting_reward = 1.0
shape_kiting_min_dist = 5
shape_kiting_max_dist = 7
shape_unnecessary_prayer_penalty = -0.2
shape_resource_threat_window = 2
curriculum_wave = 30
curriculum_pct = 0.05
curriculum_wave_2 = 31
curriculum_pct_2 = 0.02
curriculum_wave_3 = 0
curriculum_pct_3 = 0.0
disable_movement = 0

[vec]
total_agents = 4096
num_buffers = 1

[train]
total_timesteps = 600_000_000
learning_rate = 0.0003
anneal_lr = 0
gamma = 0.999
gae_lambda = 0.95
clip_coef = 0.15
vf_coef = 0.5
ent_coef = 0.01
max_grad_norm = 0.5
horizon = 256
minibatch_size = 4096

[policy]
hidden_size = 256
num_layers = 2

[sweep]
method = Protein
metric = wave_reached
metric_distribution = linear
goal = maximize
max_suggestion_cost = 1200
max_runs = 24
gpus = 1
downsample = 5
use_gpu = True
prune_pareto = True
early_stop_quantile = 0.25
sweep_only = train/learning_rate, train/clip_coef, train/ent_coef, env/curriculum_pct, env/curriculum_pct_2

[sweep.train.learning_rate]
distribution = log_normal
min = 0.00015
max = 0.0005
scale = auto

[sweep.train.clip_coef]
distribution = uniform
min = 0.10
max = 0.20
scale = auto

[sweep.train.ent_coef]
distribution = log_normal
min = 0.005
max = 0.015
scale = auto

[sweep.env.curriculum_pct]
distribution = uniform
min = 0.03
max = 0.08
scale = auto

[sweep.env.curriculum_pct_2]
distribution = uniform
min = 0.0
max = 0.02
scale = auto
```

Hyperparameters:
- fixed:
  - gamma=`0.999`
  - gae_lambda=`0.95`
  - vf_coef=`0.5`
  - max_grad_norm=`0.5`
  - horizon=`256`
  - minibatch_size=`4096`
  - total_agents=`4096`
  - hidden_size=`256`
  - num_layers=`2`
  - total_timesteps=`600M`
  - anneal_lr=`0`
  - checkpoint_interval=`50`
- sweep space:
  - `learning_rate` in `[0.00015, 0.0005]`
  - `clip_coef` in `[0.10, 0.20]`
  - `ent_coef` in `[0.005, 0.015]`
  - `curriculum_pct` in `[0.03, 0.08]`
  - `curriculum_pct_2` in `[0.0, 0.02]`

What the sweep was intended to test:
- whether the current late-wave plateau is best solved by:
  - gentler continuation updates
  - slightly lower exploration
  - a small amount of targeted wave `30/31` rehearsal
- without reintroducing any of the broader `v17` confounds

What the sweep is **not** testing:
- no new observations
- no backend mechanics changes
- no reward rewrite
- no large curriculum
- no broad hyperparameter search across the whole PPO stack

Success criteria:
- materially improve average `wave_reached` beyond the `v18/v18.1`
  `~29.2-29.3` shelf
- improve late-wave milestone conversion:
  - `reached_wave_30`
  - `cleared_wave_30`
  - `reached_wave_31`
- preserve the current strong competence signals:
  - high `correct_prayer`
  - high `damage_blocked`
  - low `tokxil_melee_ticks`
- avoid regressing into the `v17.1` flatline regime

Operational notes:
- the active launch helper is `sweep_v18_2.sh`
- default local launch:
  - `cd /home/joe/projects/runescape-rl/codex3/runescape-rl && bash sweep_v18_2.sh`
- override for cloud or multi-GPU sweep fanout:
  - `SWEEP_GPUS=<n> TRAIN_GPUS=<m> bash sweep_v18_2.sh`
- override checkpoint if a better `v18.1` candidate is chosen after
  fixed eval:
  - `LOAD_MODEL_PATH=/path/to/checkpoint.bin bash sweep_v18_2.sh`

Results (attempted sweep, 12 completed trials before failure,
wandb group `v18_2_sweep`):
- completed trial ids:
  - `bbqcayi3`
  - `dtx7o43t`
  - `q2tnsyj9`
  - `7986v0xu`
  - `j98edhkh`
  - `dulc6rym`
  - `mnamq7yv`
  - `4umps8gz`
  - `5vw45smy`
  - `jxnofnjl`
  - `gy73cihv`
  - `6ej6mifr`
- total completed work before failure:
  - **12** successful trials
  - about **7.20B** total agent steps
  - about **56.3 min** of successful trial uptime
- all 12 completed trials used the **same default continuation
  config**, not different sweep suggestions:
  - `learning_rate = 0.0003`
  - `clip_coef = 0.15`
  - `ent_coef = 0.01`
  - `curriculum_pct = 0.05`
  - `curriculum_pct_2 = 0.02`

Representative final trial summary
(`bbqcayi3`; the others are effectively identical):
- SPS: **2.11M**
- uptime: **282.0s**
- wave reached: **20.96**
- episode return: **904.9**
- max wave: **32**
- most NPCs slayed: **82**
- reached_wave_30: **0.533**
- cleared_wave_30: **0.0**
- reached_wave_31: **0.0**
- correct_prayer: **129.9**
- no_prayer_hits: **87.7**
- prayer_switches: **96.9**
- damage_blocked: **1414.6**
- dmg_taken_avg: **3024.5**
- entropy: **6.792**
- clipfrac: **0.127**

Important operational outcome:
- `v18.2` did **not** execute a valid hyperparameter sweep.
- It launched **12 identical continuation trials** and then failed before
  trial 13.
- This means there is **no legitimate sweep winner** from `v18.2`.
- The run is still informative, but it should be read as:
  - a repeated default continuation control, and
  - a sweep infrastructure failure, not a completed sweep experiment

Why the sweep failed:
- The `sweep_only` filter used path-style keys:
  - `train/learning_rate`
  - `train/clip_coef`
  - `train/ent_coef`
  - `env/curriculum_pct`
  - `env/curriculum_pct_2`
- But the recursive sweep parser filters on the **leaf field name**
  after descending into nested dicts.
- That means the parser compared:
  - `learning_rate`
  - `clip_coef`
  - `ent_coef`
  - `curriculum_pct`
  - `curriculum_pct_2`
  against the full path strings above, and filtered them all out.
- Net result:
  - the sweep search space collapsed to **zero dimensions**
  - `Hyperparameters.num = 0`
  - every launched run used the same default config
- The launch count of **12** is also explained by the broken sweep path:
  - `pufferlib.pufferl.sweep()` runs the first **2** experiments without
    calling `suggest()`
  - `Protein` then emits **10** zero-dimensional random/default
    suggestions
  - after those **12** completed trials, the optimizer tries to switch
    into the GP model path and crashes on the empty hyperparameter space
- The failure is reproducible locally from the stored config and lands in
  the GP training path with a shape mismatch on zero-dimensional inputs.

What went well:
- Warm-start loading worked:
  - every completed trial loaded the intended `xm6i52ta` checkpoint
- Local sweep orchestration worked up to the failure point:
  - per-trial W&B runs were created
  - per-trial JSON logs were saved
  - completed trials were isolated cleanly by run id
- Runtime stability was good:
  - no NaNs
  - no early-stop failures
  - stable throughput around **2.11M SPS**
  - stable GPU/VRAM usage:
    - GPU util averaged about **92%**
    - VRAM averaged about **1.99 GB**
- The repeated identical runs also acted as an accidental determinism
  check:
  - outcomes were nearly identical across all 12 trials
  - that is evidence the checkpoint loading + current backend path are
    highly repeatable under fixed settings

What went wrong:
- The most important problem is the sweep bug above: `v18.2` did not
  search the intended hyperparameter space at all.
- The second important problem is that the **default continuation recipe
  itself underperformed badly**, even before the sweep crashed.
- Compared to the source run family (`v18` / `v18.1`), the default
  `v18.2` continuation is a major regression.

Representative trial progression (`bbqcayi3`):
- `~76.5M` steps:
  - wave `22.44`
  - return `326.2`
  - correct_prayer `175.8`
  - prayer_switches `196.1`
  - damage_blocked `2438.9`
  - entropy `7.654`
  - clipfrac `0.070`
- `~226.5M` steps:
  - wave `20.65`
  - return `771.6`
  - correct_prayer `154.7`
  - prayer_switches `99.5`
  - damage_blocked `893.6`
- `~376.4M` steps:
  - wave `20.12`
  - return `903.9`
  - correct_prayer `178.9`
  - no_prayer_hits `102.0`
  - damage_blocked `1205.2`
- `~526.4M` steps:
  - wave `20.22`
  - return `901.5`
  - correct_prayer `172.1`
  - prayer_switches `88.5`
  - damage_blocked `1095.7`
- final `~599.8M` steps:
  - wave `20.96`
  - return `904.9`
  - correct_prayer `129.9`
  - prayer_switches `96.9`
  - damage_blocked `1414.6`
  - entropy `6.792`
  - clipfrac `0.127`

Interpretation of that progression:
- This is **not** a stagnant run that simply failed to update.
- The policy kept moving:
  - entropy fell from `7.65 -> 6.79`
  - clipfrac rose from `0.070 -> 0.127`
- But the movement was not beneficial:
  - average wave fell below the opening continuation window
  - prayer switching volume collapsed
  - blocked damage collapsed
- So the default `v18.2` continuation looks like **active unlearning**,
  not optimizer starvation.

Important metric caveat:
- `v18.2` introduced a small wave `30/31` curriculum:
  - `curriculum_pct = 0.05`
  - `curriculum_pct_2 = 0.02`
- Because of that, several late-wave metrics are **partially confounded**
  and should not be read as directly comparable to scratch runs:
  - `reached_wave_30`
  - `max_wave`
  - `most_npcs_slayed`
- Inference:
  - the raw `wave_reached = 20.96` is also mildly inflated by those
    curriculum starts
  - if you subtract only the minimum wave-floor injected by the
    curriculum (`0.05 * 30 + 0.02 * 31 = 2.12`), the implied
    non-curriculum average would be closer to about **18.84**
  - that is only an approximation, but it shows the continuation was
    almost certainly even weaker than the raw headline number suggests

Comparison vs `v18.1` (`xm6i52ta`):

Top-line:
- `v18.2` default continuation:
  - wave `20.96`
  - return `904.9`
  - max wave `32`
  - correct_prayer `129.9`
  - prayer_switches `96.9`
  - damage_blocked `1414.6`
- `v18.1` final run:
  - wave `29.24`
  - return `5534.5`
  - max wave `34`
  - correct_prayer `2407.1`
  - prayer_switches `2107.0`
  - damage_blocked `34.4k`

What this says:
- The default `v18.2` continuation is dramatically weaker than the
  source recipe:
  - wave down by **8.28**
  - return down by **4629.7**
  - correct_prayer down by about **2277**
  - prayer_switches down by about **2010**
  - damage_blocked down by about **32.97k**
- Because `v18.2` uses curriculum and `v18.1` does not, its raw
  `reached_wave_30` is **not** a valid headline comparison
- The reliable reading is simple:
  - `v18.2` did not consolidate late-wave performance
  - it regressed far below the frontier recovered by `v18.1`

Comparison vs `v18` (`lxttb7uo`):
- `v18` final wave: **29.20**
- `v18.2` default continuation wave: **20.96**
- `v18` correct_prayer: **1720.2**
- `v18.2` correct_prayer: **129.9**
- `v18` damage_blocked: **24.9k**
- `v18.2` damage_blocked: **1.4k**

Interpretation:
- `v18.2` did not preserve the competence that `v18` re-established on
  the pruned backend / obs contract
- So this is not a case where the current checkpoint fine-tune just
  held steady while the sweep infrastructure misbehaved
- The continuation recipe itself is poor in its current form

Comparison vs `v17.1` (`q3ald8bc`):
- `v17.1` wave: **17.17**
- `v18.2` default continuation wave: **20.96**

Interpretation:
- Even the broken `v18.2` continuation remains above the failed
  `v17.1` scratch control
- That is consistent with a checkpoint carrying some competence forward
- But it is still nowhere close to the frontier already proven by
  `v18` / `v18.1`

Main conclusion:
- `v18.2` is a **negative result** in two different ways:
  - the first sweep implementation was not valid
  - the default continuation recipe performed poorly even before the
    sweep infrastructure failed
- There is no basis to choose a hyperparameter winner from this run.
- The only safe conclusions are:
  - the sweep path needs to be fixed before the next sweep
  - the exact default `v18.2` continuation recipe should **not** be
    promoted as the next baseline

Most likely root causes of poor agent performance in this attempt:
- sweep infrastructure bug:
  - the intended hyperparameter search never happened
- checkpoint continuation recipe is too disruptive:
  - the continuation actively moved the policy, but in a harmful
    direction
- late-wave curriculum polluted the evaluation signals:
  - milestone metrics from this run are not cleanly comparable to the
    scratch baselines
- checkpoint selection was not fixed-eval-backed:
  - the chosen `~2.20B` checkpoint was a plausible candidate, but not a
    validated best checkpoint

Recommendation for next run:
- Do **not** treat `v18.2` as a successful sweep.
- Before the next sweep:
  - fix the `sweep_only` / nested-parameter path handling
  - verify that the sweep object actually sees a nonzero search space
  - dry-run one local suggestion and print the sampled hyperparameters
    before launching full experiments
- For the next continuation attempt:
  - choose the warm-start checkpoint by fixed eval, not by intuition
  - remove curriculum for the first corrected sweep so the metrics are
    cleanly comparable
  - test the small continuation recipe again only after the sweep path
    is known-good
- If a single non-sweep continuation control is desired before fixing
  sweeps:
  - run one warm-start continuation with:
    - `anneal_lr = 0`
    - `learning_rate = 0.00015` or `0.0002`
    - `clip_coef = 0.10`
    - `ent_coef = 0.005`
    - **no curriculum**
  - that will tell us whether the poor result was mostly:
    - bad checkpoint + curriculum, or
    - bad continuation dynamics more broadly

---

## v18.1 (2026-04-07)

Changes from `v18`:

This run was intended as a long-budget control on the `v18` recipe.

Nominal config delta:
- `total_timesteps: 1.5B -> 10B`

Important caveat:
- because `anneal_lr = 1`, this was **not** a perfect "more time only"
  control
- extending `total_timesteps` from `1.5B` to `10B` also stretched the
  LR decay by `6.67x`
- at `1.5B` steps, `v18` had annealed essentially to `0`
- at `1.5B` steps in `v18.1`, the effective LR was still about
  `0.00085`
- so `v18.1` tested **more budget plus a much slower decay schedule**,
  not just more wall-clock compute

Everything else stayed identical to `v18`:
- same current pruned obs/backend:
  - policy obs remains **106**
  - reward features remain **18**
  - RL-facing obs remains **142**
  - Jad telegraph stays removed from both obs and backend
  - ammo remains **50,000**
- same PPO recipe:
  - `learning_rate = 0.001`
  - `clip_coef = 0.2`
  - `ent_coef = 0.02`
- same reward/shaping recipe:
  - `w_damage_dealt = 1.0`
  - `w_npc_kill = 2.0`
  - `w_correct_danger_prayer = 1.0`
  - extra threat-aware food/pot penalties remain disabled
  - capped stall penalty remains on
- same no-warm-start / no-curriculum setup

Why this run existed:
- `v18` already proved that the current pruned backend / obs contract is
  healthy and can recover to the wave-29 frontier.
- The main unanswered question is whether the same exact recipe keeps
  climbing with a much larger budget, or whether it simply plateaus
  around the current `~29-31` region.
- `v18.1` is meant to answer that without introducing any new confounds.

Live `fight_caves.ini`:

```ini
[base]
env_name = fight_caves
checkpoint_interval = 100

[env]
w_damage_dealt = 1.0
w_damage_taken = -0.75
w_npc_kill = 2.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.5
w_food_used_well = 1.0
w_prayer_pot_used = -1.0
w_correct_jad_prayer = 0.0
w_wrong_jad_prayer = 0.0
w_correct_danger_prayer = 1.0
w_wrong_danger_prayer = 0.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = 0.0
w_tick_penalty = -0.001
shape_food_full_waste_penalty = -5.0
shape_food_waste_scale = -1.0
shape_food_safe_hp_threshold = 1.0
shape_food_no_threat_penalty = 0.0
shape_pot_full_waste_penalty = -5.0
shape_pot_waste_scale = -1.0
shape_pot_safe_prayer_threshold = 1.0
shape_pot_no_threat_penalty = 0.0
shape_wrong_prayer_penalty = -1.0
shape_npc_specific_prayer_bonus = 2.0
shape_npc_melee_penalty = -0.5
shape_wasted_attack_penalty = -0.3
shape_wave_stall_start = 500
shape_wave_stall_base_penalty = -0.75
shape_wave_stall_ramp_interval = 50
shape_wave_stall_cap = -3.0
shape_not_attacking_grace_ticks = 2
shape_not_attacking_penalty = -0.5
shape_kiting_reward = 1.0
shape_kiting_min_dist = 5
shape_kiting_max_dist = 7
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
num_buffers = 1

[train]
total_timesteps = 10_000_000_000
learning_rate = 0.001
anneal_lr = 1
gamma = 0.999
gae_lambda = 0.95
clip_coef = 0.2
vf_coef = 0.5
ent_coef = 0.02
max_grad_norm = 0.5
horizon = 256
minibatch_size = 4096

[policy]
hidden_size = 256
num_layers = 2
```

Hyperparameters: gamma=0.999, gae_lambda=0.95, clip_coef=0.2,
vf_coef=0.5, ent_coef=0.02, max_grad_norm=0.5, horizon=256,
minibatch_size=4096, total_agents=4096, hidden_size=256,
num_layers=2. total_timesteps=10B, lr=0.001, anneal_lr=1.

Success criteria were:
- determine whether the exact `v18` recipe can break past the current
  wave-29 frontier with more time alone
- preserve the recovered scratch-learning behavior from `v18`
- identify whether the best checkpoint window moves materially beyond
  the `~1.21B-1.29B` region from `v18`
- avoid introducing any new confounds before the shared-backend refactor

If this run still plateaus in the same region:
- that is strong evidence the next step should be targeted fine-tuning
  or a small late-wave curriculum intervention, not another large-budget
  scratch rerun of the same recipe

Results (10B steps, ~87.5min, wandb run `xm6i52ta`):
- SPS: **1.84M**
- Wave reached: **29.24** avg
- Max wave: **34**
- Most NPCs slayed: **128**
- Episode return: **5534.5**
- Episode length: **4402**
- Score: 0
- Epochs: 9536
- Entropy: **5.495**
- Clipfrac: **0.0935**
- Value loss: **40.82**
- Policy loss: `-0.0551`
- KL: `0.00728`

Analytics (final summary window):
- prayer_uptime: **0.835**
- prayer_uptime_melee: **0.564**
- prayer_uptime_range: **0.230**
- prayer_uptime_magic: **0.041**
- correct_prayer: **2407.1**
- wrong_prayer_hits: **143.4**
- no_prayer_hits: **77.5**
- prayer_switches: **2107.0**
- damage_blocked: **34,385.5**
- dmg_taken_avg: **3580.0**
- pots_used: **31.5**
- avg_prayer_on_pot: **0.690**
- food_eaten: **20.0**
- avg_hp_on_food: **0.926**
- food_wasted: **19.4**
- pots_wasted: **13.3**
- tokxil_melee_ticks: **12.8**
- ketzek_melee_ticks: **0.0**
- reached_wave_30: **0.306**
- cleared_wave_30: **0.0**
- reached_wave_31: **0.0**

Progression:
- `v18.1` recovered to the frontier earlier than `v18`:
  - `~512M`: **wave 20.0**
  - `~1.13B`: **wave 29.2**
- After that, the run spent almost the entire remaining budget on the
  same broad plateau:
  - `1.66B`: **wave 29.4**, return **5743.5**
  - `2.17B`: **wave 29.4**, return **5935.9**
  - `4.64B`: **wave 29.5**, return **5340.2**
  - `5.99B`: **wave 29.6**, return **5555.0**
  - `9.60B`: **wave 29.3**, return **5667.1**
  - `9.99B`: **wave 29.3**, return **5594.6**
- The strongest result of the extra budget was not a new average-wave
  breakout. It was a stronger rare-event tail:
  - `max_wave` increased to **34**
  - `most_npcs_slayed` increased to **128**
- Important nuance:
  - the final summary window shows `cleared_wave_30 = 0.0` and
    `reached_wave_31 = 0.0`
  - that does **not** mean the run never broke deeper
  - it means those deep conversions remained rare and did not
    consolidate into the final logging window

Primary interpretation:
- `v18.1` answers the main control question: **more time alone did not
  materially move the average frontier beyond the same `~29-30` shelf**
- The extra budget **did** improve tail performance, total return, and
  episode length
- But it did **not** turn those rare deeper runs into stable average
  progress
- This is the behavior of a policy that can occasionally penetrate the
  frontier, but still lacks a reliable late-wave conversion strategy

Comparison vs `v18` (`lxttb7uo`, same recipe, 1.5B):

Top-line:
- `xm6i52ta`: wave `29.24`, max wave `34`, return `5534.5`
- `lxttb7uo`: wave `29.20`, max wave `31`, return `4955.7`

What improved:
- Better rare-event tail:
  - `max_wave 34` vs `31`
  - `most_npcs_slayed 128` vs `119`
- Higher return and longer-lived episodes:
  - `episode_return 5534.5` vs `4955.7`
  - `episode_length 4402` vs `3451`
- Stronger prayer/combat volume:
  - `correct_prayer 2407.1` vs `1720.2`
  - `prayer_switches 2107.0` vs `1386.6`
  - `damage_blocked 34.4k` vs `24.9k`
- Better movement/threat handling on at least one important proxy:
  - `tokxil_melee_ticks 12.8` vs `23.2`
- Better potion timing:
  - `avg_prayer_on_pot 0.690` vs `0.724`
  - `pots_wasted 13.3` vs `17.4`

What did not improve:
- Average frontier barely changed:
  - `wave 29.24` vs `29.20`
- Final-window late-wave conversion did not improve:
  - `reached_wave_30 0.306` vs `0.348`
  - `cleared_wave_30 0.0` vs `0.0`
- Defensive cleanliness got worse:
  - `no_prayer_hits 77.5` vs `25.8`
  - `dmg_taken_avg 3580.0` vs `3071.9`
  - `prayer_uptime 0.835` vs `0.919`

Interpretation:
- `v18.1` became **deeper and more productive**, but not **cleaner**
- The policy appears to have traded some defensive coverage for longer,
  more aggressive episodes
- That trade improved return and tail depth, but not stable frontier
  advancement

Most important optimizer observation:
- `v18` had essentially annealed to zero by the end:
  - final `clipfrac 0.0053`
  - final `KL 0.00124`
- `v18.1` was still updating materially even at the end:
  - final `clipfrac 0.0935`
  - final `KL 0.00728`
- So this was **not** a case where the optimizer simply froze too early
- The plateau held even while the policy was still moving, which points
  more toward a late-wave exposure / credit-assignment / conversion
  problem than a basic optimization-starvation problem

Comparison vs `v16.2a` (`ge5sma5y`, old strong scratch baseline):

Top-line:
- `xm6i52ta`: wave `29.24`, max wave `34`, return `5534.5`
- `ge5sma5y`: wave `28.82`, max wave `31`, return `4593.7`

What improved:
- Better average wave, return, and episode length
- Better rare tail:
  - `max_wave 34` vs `31`
  - `most_npcs_slayed 128` vs `119`
- Stronger block/switch volume:
  - `correct_prayer 2407.1` vs `1604.2`
  - `prayer_switches 2107.0` vs `1343.2`
  - `damage_blocked 34.4k` vs `23.2k`
- Better Tok-Xil spacing:
  - `tokxil_melee_ticks 12.8` vs `28.4`

What is still not clearly better:
- `no_prayer_hits` is much worse (`77.5` vs `28.3`)
- `dmg_taken_avg` is also worse (`3580.0` vs `3334.6`)
- So `v18.1` is not simply a cleaner version of `ge5sma5y`
- It is a stronger high-ceiling run with more occasional late-wave
  penetration, but still messy in defensive coverage

Comparison vs `v17.1` (`q3ald8bc`, failed scratch control):

Top-line:
- `xm6i52ta`: wave `29.24`, max wave `34`, return `5534.5`
- `q3ald8bc`: wave `17.17`, max wave `24`, return `1315.7`

What this confirms:
- The current pruned backend / obs contract is not the issue
- The major failure in `v17.1` was the weakened PPO + reward recipe
- `v18.1` massively outperforms `v17.1` on every meaningful frontier and
  prayer metric

Comparison vs `v17` warm-start (`mv0snohb`):

Top-line:
- `xm6i52ta`: wave `29.24`, max wave `34`, return `5534.5`
- `mv0snohb`: wave `25.01`, max wave `32`, return `291.8`

What this confirms:
- the strong current scratch recipe is better than the old warm-start +
  curriculum package
- carrying old competence forward was not enough; the better recipe
  matters more than the old transfer scaffolding

Main conclusion:
- `v18.1` is a success in one narrow sense:
  it proves the current stack can keep producing rare deeper runs over a
  very long budget without collapsing
- But it is also a clear negative result:
  **another long scratch rerun of the same recipe is unlikely to solve
  the plateau**
- The average frontier stayed effectively flat after `~1.1B-1.7B`
  despite another `8B+` steps of training

Recommendation for next run:
- Do **not** run another 10B scratch repetition of this exact recipe
- The best next step is a targeted late-wave fine-tune from a strong
  `v18.1` checkpoint, not another full scratch restart

Recommended `v19` direction:
- load from a strong `v18.1` checkpoint chosen by fixed eval, not by
  final-step training average alone
- first checkpoints worth evaluating:
  - `~1.66B`
  - `~2.17B`
  - `~4.90B-6.00B`
  - `~9.18B-9.60B`
- use a gentler fine-tune recipe:
  - `learning_rate = 0.0003`
  - `clip_coef = 0.15`
  - `ent_coef = 0.01`
  - `checkpoint_interval = 50`
- add only a **small** late-wave curriculum:
  - `curriculum_wave = 30`, `curriculum_pct = 0.05`
  - `curriculum_wave_2 = 31`, `curriculum_pct_2 = 0.02`
  - keep `curriculum_wave_3 = 0`
- keep the current `v18/v18.1` reward structure unchanged for the first
  fine-tune

Why this is the right next move:
- `v18.1` shows the agent can occasionally break deeper
- the missing ingredient is not basic competence anymore
- the missing ingredient is **consistent conversion** once it reaches
  the late-wave frontier
- that is exactly the situation where a small late-wave curriculum and a
  lower-noise fine-tune are justified

Secondary recommendation:
- if you want another "time only" control in the future, do not change
  `total_timesteps` with `anneal_lr = 1` and assume the recipe stayed
  identical
- future continuation-style comparisons should either:
  - continue training from the best checkpoint
  - or explicitly control the LR schedule when changing total budget

---

## v18 (2026-04-06)

Changes from `v17.1`:

This is the scratch recovery run that kept the current `codex3` pruned
backend/obs contract, but restored the stronger scratch-learning recipe.

Config / training changes from `v17.1`:
- keep **no warm-start**
- keep **no curriculum**
- keep the current pruned obs/backend:
  - policy obs stays at **106**
  - Jad telegraph remains removed from both obs and backend logic
  - ammo remains **50,000**
- restore the learning recipe toward the working `ge5sma5y` regime:
  - `learning_rate: 0.00025 -> 0.001`
  - `clip_coef: 0.12 -> 0.2`
  - `ent_coef: 0.03 -> 0.02`
  - `w_damage_dealt: 0.5 -> 1.0`
  - `w_npc_kill: 3.0 -> 2.0`
  - `w_correct_danger_prayer: 2.0 -> 1.0`
- disable the extra threat-aware food/pot penalties:
  - `shape_food_safe_hp_threshold: 0.70 -> 1.0`
  - `shape_food_no_threat_penalty: -0.50 -> 0.0`
  - `shape_pot_safe_prayer_threshold: 0.50 -> 1.0`
  - `shape_pot_no_threat_penalty: -0.50 -> 0.0`
- keep the current stability fixes:
  - prayer flick reward still disabled
  - capped stall penalty still on
  - incoming-hit timeline summaries and `prayer_drain_counter` still on

Why this run existed:
- `v17.1` answered the control question clearly: the current `v17`
  package was materially worse than `v16.2a` from scratch.
- The unresolved question was whether the failure came from the new
  backend / observation contract, or from the weakened PPO + reward
  recipe around it.
- `v18` isolates that question:
  keep the new backend / pruned obs, but restore the proven scratch
  learning recipe.

Live `fight_caves.ini`:

```ini
[base]
env_name = fight_caves
checkpoint_interval = 100

[env]
w_damage_dealt = 1.0
w_damage_taken = -0.75
w_npc_kill = 2.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.5
w_food_used_well = 1.0
w_prayer_pot_used = -1.0
w_correct_jad_prayer = 0.0
w_wrong_jad_prayer = 0.0
w_correct_danger_prayer = 1.0
w_wrong_danger_prayer = 0.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = 0.0
w_tick_penalty = -0.001
shape_food_full_waste_penalty = -5.0
shape_food_waste_scale = -1.0
shape_food_safe_hp_threshold = 1.0
shape_food_no_threat_penalty = 0.0
shape_pot_full_waste_penalty = -5.0
shape_pot_waste_scale = -1.0
shape_pot_safe_prayer_threshold = 1.0
shape_pot_no_threat_penalty = 0.0
shape_wrong_prayer_penalty = -1.0
shape_npc_specific_prayer_bonus = 2.0
shape_npc_melee_penalty = -0.5
shape_wasted_attack_penalty = -0.3
shape_wave_stall_start = 500
shape_wave_stall_base_penalty = -0.75
shape_wave_stall_ramp_interval = 50
shape_wave_stall_cap = -3.0
shape_not_attacking_grace_ticks = 2
shape_not_attacking_penalty = -0.5
shape_kiting_reward = 1.0
shape_kiting_min_dist = 5
shape_kiting_max_dist = 7
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
num_buffers = 1

[train]
total_timesteps = 1_500_000_000
learning_rate = 0.001
anneal_lr = 1
gamma = 0.999
gae_lambda = 0.95
clip_coef = 0.2
vf_coef = 0.5
ent_coef = 0.02
max_grad_norm = 0.5
horizon = 256
minibatch_size = 4096

[policy]
hidden_size = 256
num_layers = 2
```

Hyperparameters: gamma=0.999, gae_lambda=0.95, clip_coef=0.2,
vf_coef=0.5, ent_coef=0.02, max_grad_norm=0.5, horizon=256,
minibatch_size=4096, total_agents=4096, hidden_size=256,
num_layers=2. total_timesteps=1.5B, lr=0.001, anneal_lr=1.

Success criteria:
- recover a real scratch breakout instead of the `v17.1` flatline
- beat `wave 20` materially earlier than `v17.1`
- rebuild prayer-switching and damage-blocking metrics toward `ge5sma5y`
- preserve the improved stability from the current backend/stall-cap changes

Results (1.5B steps, ~12.4min, wandb run `lxttb7uo`):
- SPS: **2.02M**
- Wave reached: **29.20** avg
- Max wave: **31**
- Most NPCs slayed: **119**
- Episode return: **4955.7**
- Episode length: **3451**
- Score: 0
- Epochs: 1430
- Entropy: **5.938**
- Clipfrac: **0.0053**
- Value loss: **48.49**
- Policy loss: `-0.0300`
- KL: `0.00124`

Analytics (final):
- prayer_uptime: **0.919**
- prayer_uptime_melee: **0.544**
- prayer_uptime_range: **0.312**
- prayer_uptime_magic: **0.063**
- correct_prayer: **1720.2**
- wrong_prayer_hits: **152.7**
- no_prayer_hits: **25.8**
- prayer_switches: **1386.6**
- damage_blocked: **24,912.2**
- dmg_taken_avg: **3071.9**
- pots_used: **28.7**
- avg_prayer_on_pot: **0.724**
- food_eaten: **20.0**
- avg_hp_on_food: **0.922**
- food_wasted: **18.9**
- pots_wasted: **17.4**
- tokxil_melee_ticks: **23.2**
- ketzek_melee_ticks: **0.0**
- reached_wave_30: **0.348**
- cleared_wave_30: **0.0**
- reached_wave_31: **0.0**

Progression:
- `v18` did **not** look healthy immediately. For the first `~550M`
  steps it still looked like a middling run, mostly living in the
  `18-19` range.
- The decisive breakout started around `~620M-700M`:
  - `616M`: **wave 20.2**
  - `696M`: **wave 22.6**
  - `780M`: **wave 24.0**
  - `853M`: **wave 26.9**
  - `935M`: **wave 28.2**
- The run then established a real frontier plateau:
  - `1.084B`: **wave 28.8**
  - `1.156B`: **wave 29.2**
  - `1.214B`: **wave 29.4**
  - `1.290B`: **wave 29.3**
- Best observed checkpoint window was around `~1.21B-1.29B`, not the
  final step:
  - peak average return: **5079.5** at `1.289B`
  - final average return: **4955.7**
- This is the same broad learning shape as `ge5sma5y`: a long slow
  setup phase, then a hard mid-run breakout into the high-20s.
- Important implication: stopping this recipe around `400M-600M` would
  have produced a false negative.

Comparison vs `v17.1` (`q3ald8bc`, scratch, no curriculum, 1.5B):

Top-line:
- `lxttb7uo`: wave `29.20`, max wave `31`, return `4955.7`
- `q3ald8bc`: wave `17.17`, max wave `24`, return `1315.7`

What improved dramatically:
- Breakout came back:
  - `v17.1` never reached wave 20
  - `v18` reached wave 20 by `~616M`, wave 24 by `~780M`, and wave 29 by `~1.15B`
- Prayer learning recovered:
  - `correct_prayer 1720.2` vs `224.9`
  - `prayer_switches 1386.6` vs `339.4`
  - `damage_blocked 24.9k` vs `1.2k`
  - `no_prayer_hits 25.8` vs `96.5`
- Resource timing improved instead of collapsing:
  - `pots_used 28.7` vs `32.0`
  - `avg_prayer_on_pot 0.724` vs `0.987`
  - `pots_wasted 17.4` vs `32.0`
  - `avg_hp_on_food 0.922` vs `0.926`
  - `food_wasted 18.9` vs `19.5`
- Episode depth recovered:
  - `episode_length 3451` vs `997`

What worsened on paper but is not actually a regression:
- `wrong_prayer_hits` increased (`152.7` vs `62.4`)
- `dmg_taken_avg` increased (`3071.9` vs `2683.5`)
- `tokxil_melee_ticks` increased (`23.2` vs `12.1`)

Interpretation:
- Those raw totals rose because `v18` survives far longer, reaches much
  later waves, and faces many more dangerous encounters per episode.
- The more meaningful signal is that `no_prayer_hits` collapsed while
  `correct_prayer`, `prayer_switches`, and `damage_blocked` exploded
  upward. That is real competence, not just longer suffering.

Comparison vs `v16.2a` (`ge5sma5y`, scratch, no curriculum, 1.5B):

Top-line:
- `lxttb7uo`: wave `29.20`, max wave `31`, return `4955.7`
- `ge5sma5y`: wave `28.82`, max wave `31`, return `4593.7`

What improved:
- Slightly higher frontier:
  - wave `29.20` vs `28.82`
  - return `4955.7` vs `4593.7`
  - episode_length `3451` vs `3281`
- Slightly better combat conversion:
  - `correct_prayer 1720.2` vs `1604.2`
  - `prayer_switches 1386.6` vs `1343.2`
  - `damage_blocked 24.9k` vs `23.2k`
  - `no_prayer_hits 25.8` vs `28.3`
- Better resource discipline:
  - `pots_used 28.7` vs `30.5`
  - `avg_prayer_on_pot 0.724` vs `0.767`
  - `pots_wasted 17.4` vs `19.8`
  - `food_wasted 18.9` vs `19.0`
- Better overall safety totals:
  - `dmg_taken_avg 3071.9` vs `3334.6`
  - `tokxil_melee_ticks 23.2` vs `28.4`
- Slightly better SPS: `2.02M` vs `1.95M`

What changed but is not clearly better or worse:
- `prayer_uptime` rose slightly (`0.919` vs `0.903`)
- melee prayer time fell slightly while range prayer time rose
- value loss was lower in `ge5sma5y`, but that did not translate into a
  stronger frontier than `v18`

Most important comparison:
- At matched checkpoints, `v18` tracked `ge5sma5y` very closely:
  - `~190M`: wave `18.44` vs `18.37`
  - `~564M`: wave `20.34` vs `21.28`
  - `~940M`: wave `27.36` vs `27.37`
  - `~1.31B`: wave `29.29` vs `29.00`
- That is strong evidence that the pruned observation contract did **not**
  destroy learning capacity.

Comparison vs `v17` warm-start (`mv0snohb`):

Top-line:
- `lxttb7uo`: wave `29.20`, max wave `31`, return `4955.7`
- `mv0snohb`: wave `25.01`, max wave `32`, return `291.8`

What this means:
- `v18` from scratch clearly outperformed the warm-started `v17` package.
- The `v17` failure was not that scratch training was too hard for the
  new backend. It was that the `v17` recipe itself was weak enough to
  need inherited competence.
- `v18` removed that ambiguity.

Observations:

1. **The main cause of the `v17.1` collapse was the recipe, not the
   pruned backend / obs contract.**
   - `v18` kept the pruned 106-float observation contract
   - kept Jad telegraph removed
   - kept the capped stall penalty
   - kept the current backend / parity fixes
   - and still recovered to the wave-29 frontier once the PPO/reward
     recipe was restored

2. **The strong scratch recipe still needs patience.**
   - `v18` looked ordinary for hundreds of millions of steps
   - the breakout window again arrived around `~600M-900M`
   - future ablations should not be declared dead too early unless they
     are clearly flat well past that region

3. **The capped stall penalty appears safe.**
   - `ge5sma5y` used the same broad recipe but with `shape_wave_stall_cap = 0.0`
   - `v18` used `-3.0` and still matched or slightly beat the frontier
   - so the cap does not appear to be the source of previous regressions

4. **Resource thrift came back as a consequence of competence.**
   - removing the extra threat-aware resource penalties did not make the
     agent waste resources more
   - it actually improved pot timing substantially
   - this supports the idea that the added `v17` resource penalties were
     distracting scratch learning more than helping it

5. **Rare late-wave milestone metrics are still not ideal as final
   summary indicators.**
   - final `reached_wave_31` and `cleared_wave_30` read as `0.0`
   - but `max_wave = 31` proves the run did reach wave 31 at least once
   - these counters are averaged over the last logging window, not a
     whole-run cumulative truth

Key takeaway:
- `v18` is the successful answer to the `v17.1` control failure.
- The backend / pruned obs changes are compatible with frontier
  learning.
- The real regression source in `v17` / `v17.1` was the weakened PPO
  profile and reward mix, not the observation contract itself.
- `v18` should now replace `ge5sma5y` as the clean scratch baseline on
  the current `codex3` codebase.

Recommendation for next step:
- treat `lxttb7uo` as the new baseline run
- use a best checkpoint from the `~1.21B-1.29B` plateau for eval /
  future warm-start experiments rather than blindly using the final step
- do **not** add more reward complexity right away
- if the next question is about backend architecture or parity, that can
  now proceed without the training recipe itself being in doubt

---

## v17.1 (2026-04-06)

Changes from v17:

This is the clean control run for the current `codex3` v17 package.
It keeps the new observation surface, reward weights, threat-aware
resource shaping, capped stall penalty, and PPO hyperparameters from
`v17`, but removes the two confounders that made `mv0snohb` hard to
interpret:
- **no warm-start**
- **no curriculum**

Why this run exists:
- `v17` warm-start + mixed curriculum was more stable than old `v16.2`,
  but it did not preserve the `v16.2a` wave-29 frontier.
- That run changed too many things at once:
  checkpoint initialization, curriculum distribution, reward weights,
  and PPO settings.
- `v17.1` isolates the actual question:
  are the current `v17` obs/reward/hyperparameter changes themselves
  better than `v16.2a` when trained from scratch on the same backend?

Config / training changes from `v17`:
- removed warm-start (`LOAD_MODEL_PATH` unset)
- disabled all curriculum buckets:
  - `curriculum_wave=0`, `curriculum_pct=0.0`
  - `curriculum_wave_2=0`, `curriculum_pct_2=0.0`
  - `curriculum_wave_3=0`, `curriculum_pct_3=0.0`
- shortened run length:
  - `total_timesteps: 5B -> 1.5B`

Everything else stays at the current `v17` values:
- `learning_rate = 0.00025`
- `clip_coef = 0.12`
- `ent_coef = 0.03`
- `w_damage_dealt = 0.5`
- `w_npc_kill = 3.0`
- `w_correct_danger_prayer = 2.0`
- threat-aware food/pot shaping kept on
- capped stall penalty kept on

Live `fight_caves.ini`:

```ini
[base]
env_name = fight_caves
checkpoint_interval = 100

[env]
w_damage_dealt = 0.5
w_damage_taken = -0.75
w_npc_kill = 3.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.5
w_food_used_well = 1.0
w_prayer_pot_used = -1.0
w_correct_jad_prayer = 0.0
w_wrong_jad_prayer = 0.0
w_correct_danger_prayer = 2.0
w_wrong_danger_prayer = 0.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = 0.0
w_tick_penalty = -0.001
shape_food_full_waste_penalty = -5.0
shape_food_waste_scale = -1.0
shape_food_safe_hp_threshold = 0.70
shape_food_no_threat_penalty = -0.50
shape_pot_full_waste_penalty = -5.0
shape_pot_waste_scale = -1.0
shape_pot_safe_prayer_threshold = 0.50
shape_pot_no_threat_penalty = -0.50
shape_wrong_prayer_penalty = -1.0
shape_npc_specific_prayer_bonus = 2.0
shape_npc_melee_penalty = -0.5
shape_wasted_attack_penalty = -0.3
shape_wave_stall_start = 500
shape_wave_stall_base_penalty = -0.75
shape_wave_stall_ramp_interval = 50
shape_wave_stall_cap = -3.0
shape_not_attacking_grace_ticks = 2
shape_not_attacking_penalty = -0.5
shape_kiting_reward = 1.0
shape_kiting_min_dist = 5
shape_kiting_max_dist = 7
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
num_buffers = 1

[train]
total_timesteps = 1_500_000_000
learning_rate = 0.00025
anneal_lr = 1
gamma = 0.999
gae_lambda = 0.95
clip_coef = 0.12
vf_coef = 0.5
ent_coef = 0.03
max_grad_norm = 0.5
horizon = 256
minibatch_size = 4096

[policy]
hidden_size = 256
num_layers = 2
```

Hyperparameters: gamma=0.999, gae_lambda=0.95, clip_coef=0.12,
vf_coef=0.5, ent_coef=0.03, max_grad_norm=0.5, horizon=256,
minibatch_size=4096, total_agents=4096, hidden_size=256,
num_layers=2. total_timesteps=1.5B, lr=0.00025, anneal_lr=1.

Success criteria:
- fair comparison against `v16.2a`
- no checkpoint transfer effects
- no curriculum contamination of average-wave metrics
- answer whether the current `v17` package actually improves prayer
  learning and late-wave competence on its own

Results (1.5B steps, ~12.5min, wandb run `q3ald8bc`):
- SPS: **2.02M**
- Wave reached: **17.17** avg
- Max wave: **24**
- Most NPCs slayed: **86**
- Episode return: **1315.7**
- Episode length: **997**
- Score: 0
- Epochs: 1430
- Entropy: **8.044**
- Clipfrac: **0.0001**
- Value loss: **20.69**
- Policy loss: `+0.0154`

Analytics (final):
- prayer_uptime: **0.647**
- prayer_uptime_melee: **0.386**
- prayer_uptime_range: **0.189**
- prayer_uptime_magic: **0.072**
- correct_prayer: **224.9**
- wrong_prayer_hits: **62.4**
- no_prayer_hits: **96.5**
- prayer_switches: **339.4**
- damage_blocked: **1,220.5**
- dmg_taken_avg: **2683.5**
- pots_used: **32.0**
- avg_prayer_on_pot: **0.987**
- food_eaten: **20.0**
- avg_hp_on_food: **0.926**
- food_wasted: **19.5**
- pots_wasted: **32.0**
- tokxil_melee_ticks: **12.1**
- ketzek_melee_ticks: **0.0**
- reached_wave_30: **0.0**
- cleared_wave_30: **0.0**
- reached_wave_31: **0.0**

Progression:
- This run never had a real breakout phase.
- It briefly touched **wave 18.0** by `~84M` steps, then spent the
  rest of the full `1.5B` run oscillating in a very tight **16.6-17.8**
  band.
- It never reached wave 20, never formed a late inflection, and never
  showed the kind of entropy drop or return expansion that marked the
  `ge5sma5y` breakthrough.
- Unlike `v16.2a`, which built foundations slowly and then broke out
  hard after `~550M-700M`, `v17.1` was flat from start to finish.

Comparison vs `v16.2a` (`ge5sma5y`, scratch, no curriculum, 1.5B):

Top-line:
- `q3ald8bc`: wave `17.17`, max wave `24`, return `1315.7`
- `ge5sma5y`: wave `28.82`, max wave `31`, return `4593.7`

What improved:
- Slightly better SPS: `2.02M` vs `1.95M`
- Lower average damage taken: `2683.5` vs `3334.6`
- Lower Tok-Xil melee exposure than `ge5sma5y`: `12.1` vs `28.4`

What regressed badly:
- Wave progression collapsed:
  - `v17.1` never reached wave 20
  - `v16.2a` reached wave 20 around `~550M`, wave 25 around `~730M`,
    wave 28 around `~963M`, and wave 29 around `~1.17B`
- Combat conversion was dramatically worse:
  - `correct_prayer 224.9` vs `1604.2`
  - `damage_blocked 1.2k` vs `23.2k`
  - `prayer_switches 339.4` vs `1343.2`
  - `episode_length 997` vs `3281`
- Resource behavior was worse, not better:
  - `pots_used 32.0` vs `30.5`
  - `avg_prayer_on_pot 0.987` vs `0.767`
  - `avg_hp_on_food 0.926` vs `0.923`
  - `pots_wasted 32.0` vs `19.8`

Interpretation:
- `v17.1` was safer in a narrow sense, but it did not learn the actual
  Fight Caves task nearly well enough.
- The current `v17` package from scratch is nowhere near the `v16.2a`
  frontier.

Comparison vs `v17` warm-start (`mv0snohb`):

Top-line:
- `q3ald8bc`: wave `17.17`, max wave `24`, return `1315.7`
- `mv0snohb`: wave `25.01`, max wave `32`, return `291.8`

What this means:
- Warm-start + curriculum was clearly carrying a large fraction of the
  `v17` result.
- The current `v17` package does **not** bootstrap well from scratch.
- So the failure mode in `mv0snohb` was not "the package can learn but
  warm-start hurt it a little." The package itself is weak enough that
  it needed inherited competence just to reach the mid-20s.

Comparison vs older `v16+` baselines:
- `v17.1` is only slightly better than the broken `v16` run on average
  wave (`17.17` vs `16.19`) and still far below:
  - `v16.1`: `25.16`
  - `v15.3`: `28.36`
  - `v15.1`: `28.65`
- That is a strong signal that the current `v17` package is not just
  "not yet optimized"; it is materially worse than older working
  scratch recipes.

Diagnosis:

1. **The current `v17` optimizer profile is too conservative for
   scratch training.**
   - `learning_rate=0.00025` and `clip_coef=0.12` appear appropriate
     for fine-tuning or warm-start stabilization, not for learning
     Fight Caves from random weights.
   - Evidence: entropy stayed extremely high (`~8.0`) for the entire
     run, and clipfrac decayed quickly to essentially zero without any
     learning inflection.
   - The policy barely specialized at all.

2. **The current `v17` reward mix is likely under-driving combat
   progress while over-complicating resource behavior.**
   - `w_damage_dealt` was cut to `0.5` from the stronger scratch recipe.
   - threat-aware food/pot penalties were added
   - danger-prayer reward was increased
   - but the resulting policy still wasted nearly every potion dose and
     most food value while failing to learn late-wave combat
   - the extra shaping complexity is not buying the intended behavior

3. **Observation improvements alone did not rescue prayer learning.**
   - `correct_prayer` is non-zero, so the policy is not totally blind
   - but the scale is nowhere near `v16.2a`
   - this suggests the new threat obs are probably useful, but they
     still need a stronger optimization / reward recipe around them

4. **Current resource penalties are not teaching thrift from scratch.**
   - `avg_prayer_on_pot = 0.987`
   - `pots_wasted = 32.0`
   - `avg_hp_on_food = 0.926`
   - `food_wasted = 19.5`
   - This is nearly worst-case usage despite the threat-aware penalties.
   - So those penalties are either too weak, too hard to learn through,
     or simply distracting at this stage.

Key takeaway:
- `v17.1` answered the control question clearly:
  the current `v17` package is **not** a better scratch recipe than
  `v16.2a`.
- The best path forward is not a sweep of the current package.
- The best path forward is to keep the new observation contract and
  stability fixes, but restore a proven learning recipe around them.

Recommendation for `v17.2`:

Run a focused ablation:
- keep the current observation improvements:
  - `effective_style_now`
  - incoming-hit timeline summaries
  - `prayer_drain_counter`
- keep prayer flick reward disabled
- keep the capped stall penalty

But revert the training / reward recipe closer to `ge5sma5y`:
- `learning_rate: 0.00025 -> 0.001`
- `clip_coef: 0.12 -> 0.2`
- `ent_coef: 0.03 -> 0.02`
- `w_damage_dealt: 0.5 -> 1.0`
- `w_npc_kill: 3.0 -> 2.0`
- `w_correct_danger_prayer: 2.0 -> 1.0`
- disable the extra threat-aware food/pot penalties:
  - `shape_food_safe_hp_threshold = 1.0`
  - `shape_food_no_threat_penalty = 0.0`
  - `shape_pot_safe_prayer_threshold = 1.0`
  - `shape_pot_no_threat_penalty = 0.0`
- keep no curriculum
- keep no warm-start
- run `1.5B` again

Why this is the best next step:
- it isolates whether the failure came from the current `v17` reward /
  PPO recipe rather than the new observation contract
- it preserves the likely-good `codex3` threat representation work
- it restores the only scratch recipe on current `codex3` that has
  actually proven it can reach the wave-29 frontier

What not to do next:
- do not run another long warm-start + curriculum job yet
- do not sweep the current `v17.1` package yet
- do not add more shaping complexity until the baseline learns again

---

## v17 (2026-04-06)

Changes from v16.2a:

Switch back to the staged v17 config on the current `codex3` codebase
and warm-start from the best `v16.2a` bridge checkpoint.

Warm-start source:
- Run: `ge5sma5y`
- Recommended checkpoint:
  `/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/ge5sma5y/0000001364197376.bin`

Config / training changes:
- total_timesteps: `1.5B` bridge run → **5B**
- learning_rate: `0.001` → **0.00025**
- clip_coef: `0.2` → **0.12**
- ent_coef: `0.02` → **0.03**
- mixed curriculum restored:
  - `curriculum_wave=30`, `curriculum_pct=0.25`
  - `curriculum_wave_2=31`, `curriculum_pct_2=0.10`
  - `curriculum_wave_3=32`, `curriculum_pct_3=0.05`
- reward weights restored to the staged v17 values:
  - `w_damage_dealt: 1.0 → 0.5`
  - `w_npc_kill: 2.0 → 3.0`
  - `w_correct_danger_prayer: 1.0 → 2.0`
- threat-aware resource penalties restored:
  - `shape_food_safe_hp_threshold = 0.70`
  - `shape_food_no_threat_penalty = -0.50`
  - `shape_pot_safe_prayer_threshold = 0.50`
  - `shape_pot_no_threat_penalty = -0.50`
- wave stall cap restored:
  - `shape_wave_stall_cap = -3.0`

Live `fight_caves.ini`:

```ini
[base]
env_name = fight_caves
checkpoint_interval = 100

[env]
w_damage_dealt = 0.5
w_damage_taken = -0.75
w_npc_kill = 3.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.5
w_food_used_well = 1.0
w_prayer_pot_used = -1.0
w_correct_jad_prayer = 0.0
w_wrong_jad_prayer = 0.0
w_correct_danger_prayer = 2.0
w_wrong_danger_prayer = 0.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = 0.0
w_tick_penalty = -0.001
shape_food_full_waste_penalty = -5.0
shape_food_waste_scale = -1.0
shape_food_safe_hp_threshold = 0.70
shape_food_no_threat_penalty = -0.50
shape_pot_full_waste_penalty = -5.0
shape_pot_waste_scale = -1.0
shape_pot_safe_prayer_threshold = 0.50
shape_pot_no_threat_penalty = -0.50
shape_wrong_prayer_penalty = -1.0
shape_npc_specific_prayer_bonus = 2.0
shape_npc_melee_penalty = -0.5
shape_wasted_attack_penalty = -0.3
shape_wave_stall_start = 500
shape_wave_stall_base_penalty = -0.75
shape_wave_stall_ramp_interval = 50
shape_wave_stall_cap = -3.0
shape_not_attacking_grace_ticks = 2
shape_not_attacking_penalty = -0.5
shape_kiting_reward = 1.0
shape_kiting_min_dist = 5
shape_kiting_max_dist = 7
shape_unnecessary_prayer_penalty = -0.2
shape_resource_threat_window = 2
curriculum_wave = 30
curriculum_pct = 0.25
curriculum_wave_2 = 31
curriculum_pct_2 = 0.10
curriculum_wave_3 = 32
curriculum_pct_3 = 0.05
disable_movement = 0

[vec]
total_agents = 4096
num_buffers = 1

[train]
total_timesteps = 5_000_000_000
learning_rate = 0.00025
anneal_lr = 1
gamma = 0.999
gae_lambda = 0.95
clip_coef = 0.12
vf_coef = 0.5
ent_coef = 0.03
max_grad_norm = 0.5
horizon = 256
minibatch_size = 4096

[policy]
hidden_size = 256
num_layers = 2
```

Hyperparameters: gamma=0.999, gae_lambda=0.95, clip_coef=0.12,
vf_coef=0.5, ent_coef=0.03, max_grad_norm=0.5, horizon=256,
minibatch_size=4096, total_agents=4096, hidden_size=256,
num_layers=2. total_timesteps=5B, lr=0.00025, anneal_lr=1.

Why this setup:
- `v16.2a` on `codex3` proved the stack is working and reached the
  wave 29 frontier cleanly by `~1.2B`.
- The best saved `v16.2a` checkpoint sits on the `29.2` plateau,
  before the slight fade in the final bridge-run segment.
- Lower LR + tighter clipping should preserve the plateau instead of
  repeating the large-update instability seen in the old 20B `v16.2`.
- Higher entropy and mixed `30/31/32` scaffolding should force more
  late-wave prayer decisions without restarting the policy from scratch.

Results (5B steps, ~42.0min, wandb run `mv0snohb`):
- Warm-started from:
  `/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/ge5sma5y/0000001364197376.bin`
- SPS: **2.03M**
- Wave reached: **25.01** avg
- Max wave: **32**
- Most NPCs slayed: **82**
- Episode return: **291.8**
- Episode length: **824**
- Score: 0
- Epochs: 4768
- Entropy: **7.635**
- Clipfrac: **0.0007**
- Value loss: **19.60**
- Policy loss: `-0.0159`

Analytics (final):
- prayer_uptime: **0.775**
- prayer_uptime_melee: **0.542**
- prayer_uptime_range: **0.141**
- prayer_uptime_magic: **0.091**
- correct_prayer: **230.7**
- wrong_prayer_hits: **58.3**
- no_prayer_hits: **64.2**
- prayer_switches: **228.6**
- damage_blocked: **5,223**
- dmg_taken_avg: **3,049**
- pots_used: **24.8**
- avg_prayer_on_pot: **0.953**
- food_eaten: **20.0**
- avg_hp_on_food: **0.898**
- food_wasted: **16.5**
- pots_wasted: **23.7**
- tokxil_melee_ticks: **4.09**
- ketzek_melee_ticks: **0.0**

Progression:
- The run opened with inherited late-wave competence from the warm
  checkpoint plus mixed curriculum. The first sampled point was
  already wave `30.0` at `2.1M` steps.
- Early adaptation was unstable. After a brief spike to **wave 27.6**
  at `453M`, the run spent most of `0.7B-1.5B` back in the `17-18`
  range.
- Mid-run rebuilt some competence but never recovered the `v16.2a`
  frontier. From `1.8B-4.1B` it climbed into the low-mid 20s, with
  the best later sampled window at **wave 26.5** around `4.11B`.
- Final `~900M` steps were effectively annealed out. Clipfrac fell to
  near zero, entropy stayed high, and the run finished at **25.0**
  without another late-wave breakthrough.

Comparison vs `v16.2a` (`ge5sma5y`, no curriculum, scratch, 1.5B):

What improved:
- Slightly better throughput: `2.03M` vs `1.95M` SPS.
- Much better Tok-Xil spacing: `tokxil_melee_ticks 4.1` vs `28.4`.
- Lower overall damage taken: `3049` vs `3335`.
- Better resource discipline:
  - `pots_used 24.8` vs `30.5`
  - `avg_prayer_on_pot 0.953` vs `0.767`
  - `avg_hp_on_food 0.898` vs `0.923`
  - lower food and pot waste totals

What regressed:
- Wave progression and overall combat conversion dropped sharply:
  - final wave `25.0` vs `28.8`
  - return `291.8` vs `4593.7`
  - episode length `824` vs `3281`
- Prayer performance regressed hard:
  - `correct_prayer 230.7` vs `1604.2`
  - `no_prayer_hits 64.2` vs `28.3`
  - `prayer_switches 228.6` vs `1343.2`
  - `damage_blocked 5.2k` vs `23.2k`
- Even allowing for curriculum/warm-start contamination of the top-line
  metrics, the sampled `v17` peak (`27.6`) never recovered the
  `29.1-29.2` plateau reached by `v16.2a`.

Comparison vs older `v16+` runs:
- `v17` is **more stable** than the old 20B `v16.2`. There was no
  entropy collapse, no catastrophic stall-penalty death spiral, and
  value loss stayed moderate.
- `v17` is **not stronger** than the best frontier runs:
  - `v16.2a`: wave `28.8` final, `29.2` plateau
  - `v16.2`: wave `29.1` peak
  - `v15.3`: wave `28.4` final
  - `v15.1`: wave `28.6` final
- So the current `v17` package improved discipline and stability, but
  reduced the ceiling and lost too much late-wave competence.

Diagnosis:

1. **The observation fixes likely helped, but the full package was too
   conservative.**
   - Lower Tok-Xil melee ticks and lower damage taken suggest the new
     threat representation is doing something useful.
   - But the policy became much less willing to prayer-switch and much
     less able to convert safe play into actual wave clears.

2. **Warm-start + mixed curriculum was not a clean fit.**
   - `ge5sma5y` was a strong no-curriculum frontier policy.
   - `v17` immediately changed reward weights, raised entropy, lowered
     LR, tightened clipping, and injected `30/31/32` starts.
   - That is too many simultaneous shifts for a warm policy. The run
     appears to have unlearned a good portion of the inherited frontier
     behavior before it could stabilize.

3. **The optimizer may have been too gentle to re-form the frontier.**
   - `learning_rate=0.00025` and `clip_coef=0.12` gave a very safe run.
   - Safety was achieved, but the run never made a second decisive move
     back toward wave 29.
   - By late training, clipfrac was nearly zero while entropy was still
     very high. That is a bad combination for a warm-start refinement:
     lots of randomness, very little useful policy movement.

4. **The stronger resource-threat shaping may be over-weighting thrift
   relative to late-wave aggression.**
   - The run drank later, ate later, blocked less, and used less prayer.
   - Those are not bad properties by themselves.
   - But in aggregate they look like the policy learned to be
     "disciplined" faster than it learned to be "decisive."

Key takeaway:
- `v17` did **not** prove that the new package is stronger than
  `v16.2a`.
- It **did** prove that the new package is much less collapse-prone and
  likely has better threat semantics.
- The next step should be a clean control run, not another mixed
  curriculum warm-start.

Recommendation for `v17.1`:

Run a strict baseline/control:
- **No curriculum**
- **No warm-start**
- **1.5B steps**
- Keep the current `v17` observation/reward/hyperparameter package
  otherwise unchanged

Why:
- This isolates the real question: are the `v17` obs/reward changes
  themselves good, independent of curriculum and checkpoint transfer?
- It gives a fair apples-to-apples comparison against `v16.2a`.
- If `v17.1` still underperforms `v16.2a`, the problem is in the
  current `v17` package itself, not in warm-start mechanics.

If `v17.1` is still weak after that, the first two follow-up knobs I
would test are:
- `ent_coef: 0.03 -> 0.02`
- temporarily disabling curriculum even for future warm-start runs
  until the baseline package proves it can naturally rebuild the
  frontier

---

## v16.2a (2026-04-06)

Changes from v16.2:

This was a `v16.2-style` bridge run on the current `codex3` codebase,
used for two purposes:
- validate that the isolated `codex3` training stack runs end-to-end
- produce a fresh warm-start checkpoint compatible with the new backend

Important caveat:
- This is not a pure legacy reproduction. PPO hyperparameters and the
  dense reward intent stayed `v16.2-style`, but the runtime surface was
  the current `codex3` implementation.

`codex3` backend / env changes relative to old `rqvxfqmq`:
- `effective_style_now` observation replaced the old misleading
  per-NPC base attack-style feature
- incoming-hit timeline summaries were added to policy observation
- `prayer_drain_counter` was exposed in observation
- prayer flick reward was disabled
- reward shaping moved out of hardcoded logic into config-backed
  `shape_*` controls
- local PufferLib backend build/runtime paths were isolated inside
  `codex3`, and the env-core reward path was optimized

Bridge-run config changes:
- total_timesteps: `20B` → **1.5B**
- checkpoint_interval: `200` → **100**
- kept no curriculum
- kept `v16.2-style` PPO settings:
  - `learning_rate = 0.001`
  - `clip_coef = 0.2`
  - `ent_coef = 0.02`
- set the new `shape_*` knobs to emulate documented `v16.1/v16.2`
  behavior on the current code:
  - resource no-threat penalties disabled
  - safe food/prayer thresholds set to `1.0`
  - wave stall cap disabled (`shape_wave_stall_cap = 0.0`)

```ini
[train]
total_timesteps = 1_500_000_000
learning_rate = 0.001
anneal_lr = 1
clip_coef = 0.2
ent_coef = 0.02

[env]
curriculum_wave = 0
curriculum_pct = 0.0
curriculum_wave_2 = 0
curriculum_pct_2 = 0.0
curriculum_wave_3 = 0
curriculum_pct_3 = 0.0
shape_food_safe_hp_threshold = 1.0
shape_food_no_threat_penalty = 0.0
shape_pot_safe_prayer_threshold = 1.0
shape_pot_no_threat_penalty = 0.0
shape_wave_stall_cap = 0.0
```

Hyperparameters: gamma=0.999, gae_lambda=0.95, clip_coef=0.2,
vf_coef=0.5, ent_coef=0.02, max_grad_norm=0.5, horizon=256,
minibatch_size=4096, total_agents=4096, hidden_size=256,
num_layers=2. total_timesteps=1.5B, lr=0.001, anneal_lr=1.

Results (1.5B steps, ~12.7min, wandb run ge5sma5y):
- SPS: 1.95M
- Wave reached: **28.82** avg
- Max wave: **31**
- Most NPCs slayed: **119**
- Episode return: **4593.7**
- Episode length: **3281**
- Score: 0
- Epochs: 1430
- Entropy: **6.225**
- Clipfrac: **0.0004**
- Value loss: 66.27

Analytics (final):
- prayer_uptime: 0.903
- prayer_uptime_melee: 0.559
- prayer_uptime_range: 0.275
- prayer_uptime_magic: 0.069
- correct_prayer: 1604.2
- wrong_prayer_hits: 174.7
- no_prayer_hits: 28.3
- prayer_switches: 1343.2
- damage_blocked: 23,200
- dmg_taken_avg: 3334.6
- pots_used: 30.5
- avg_prayer_on_pot: 0.767
- food_eaten: 20.0
- avg_hp_on_food: 0.923
- food_wasted: 19.0
- pots_wasted: 19.8
- tokxil_melee_ticks: 28.4
- reached_wave_30: 0.230
- cleared_wave_30: 0.000
- reached_wave_31: 0.000

Progression:
- 0-550M: Similar early-game build-up to old runs, then accelerated.
- **Wave 20 at 551.6M** steps.
- **Wave 25 at 729.8M** steps.
- **Wave 28 at 962.6M** steps.
- **Wave 29 at 1.168B** steps.
- Peak plateau: `1.21B-1.36B`, wave `29.1-29.2`, return `4779-4896`.
- Final segment (`1.36B-1.50B`) faded slightly to wave `28.8`, but
  never collapsed and still ended near the frontier.

Matched comparison vs old `v16.2` (`rqvxfqmq`) near `1.5B`:
- `ge5sma5y @ 1.499B`: wave `28.82`, return `4593.7`, entropy `6.225`,
  clipfrac `0.0004`, value loss `66.3`
- `rqvxfqmq @ 1.487B`: wave `28.85`, return `4693.2`, entropy `5.665`,
  clipfrac `0.307`, value loss `87.1`

What improved:
- Faster learning curve on `codex3`:
  - wave `20`: `551.6M` vs `780.1M`
  - wave `25`: `729.8M` vs `992.0M`
  - wave `28`: `962.6M` vs `992.0M`
  - wave `29`: `1.168B` vs `2.930B`
- Lower value loss than old `v16.2` at the matched `~1.5B` window
- No sign of the destructive collapse pattern within the bridge-run span

What regressed or stayed weaker:
- Final matched wave/return at `~1.5B` were basically tied, not better
- Fewer correct prayer blocks than old `v16.2` at the matched window
- Slightly shorter episodes and no max-wave `32` spike

Diagnosis:
- The `codex3` stack is healthy enough to use for the real v17 run.
- The bridge run did not prove a giant average-wave improvement over
  old `v16.2` by `1.5B`, but it did show a much earlier climb to the
  frontier and a cleaner, less destructive optimization profile.
- Best warm-start checkpoint is on the `29.2` plateau:
  `/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/ge5sma5y/0000001364197376.bin`

---

## v16.2 (2026-04-06)

Changes from v16.1:

No reward or signal changes. Same config as v16.1.
- total_timesteps: 1.25B → **20B**. v16.1 showed a late breakthrough
  at 890M steps (wave 20→25 in 200M steps) that was still climbing
  when LR annealed to ~0. 20B gives the agent time for the
  breakthrough phase to fully play out.

```ini
[train]
total_timesteps = 20_000_000_000
learning_rate = 0.001
anneal_lr = 1
```

All other config, loadout, rewards identical to v16.1.

Hyperparameters: gamma=0.999, gae_lambda=0.95, clip_coef=0.2,
vf_coef=0.5, ent_coef=0.02, max_grad_norm=0.5, horizon=256,
minibatch_size=4096, total_agents=4096, hidden_size=256,
num_layers=2. total_timesteps=20B, lr=0.001, anneal_lr=1.

Results (20B steps, ~191min, wandb run rqvxfqmq):
- SPS: 1.67M
- Wave reached: 12.73 avg (final — collapsed), **29.1 peak** (at ~4.7B)
- Max wave: **32**
- Most NPCs slayed: **121**
- Episode return: 512.6 (final), 5,235 peak
- Episode length: 837 (final), 3,610 peak
- Score: 0
- Epochs: 19,073
- Entropy: **0.025** (final — completely collapsed)
- Clipfrac: 0.003 (final)
- Value loss: 43.5

Analytics (final):
- prayer_uptime: 0.988
- correct_prayer: 0.0
- dmg_taken_avg: 1,395
- pots_used: 7.6
- food_eaten: 0.0

Progression — THREE DISTINCT PHASES:

Phase 1 — Growth (0-4.7B, LR 0.001→0.00077):
- Wave climbed 18.7 → 29.1 over 4.7B steps. Best wave performance
  of any run. Episode return grew from 1,283 to 5,235.
- Agent learned kiting, combat engagement, and basic prayer.
- Entropy declined steadily 8.0→4.8 as policy committed to strategy.
- Clipfrac elevated at 0.4-0.5 — large policy updates happening.
  This is healthy during the growth phase but foreshadows instability.
- Value loss volatile (64-120) — the value function struggled to
  keep up with rapidly changing policy. Normal during fast learning.
- **Key checkpoint: ~4.7B steps = best policy of the entire run.**

Phase 2 — Destabilization (4.7B-9.8B, LR 0.00077→0.00051):
- Wave regressed 29.1 → 27.0 over 5B steps. Slow bleed, not sudden.
- Entropy continued dropping 4.8→1.9 — policy over-committing.
  The agent was specializing into a narrow strategy that worked at
  wave 27-29 but couldn't improve further.
- Clipfrac still 0.3-0.5 — LR was still high enough for destructive
  updates. The policy was near-converged but gradients kept pushing.
- Value loss remained extreme (89-120) — value function diverged
  from actual returns, causing bad advantage estimates, which caused
  bad policy updates, creating a feedback loop.
- Return dropped 5,235→3,498 despite wave only dropping 29→27.
  The agent earned less reward per wave because its strategy was
  degrading (worse kiting, worse combat efficiency).

Phase 3 — Entropy Collapse (9.8B-20B, LR 0.00051→0):
- **Catastrophic entropy collapse at ~9.8B.** Entropy crashed from
  1.9 to 0.027 in one logging interval. Wave dropped 27→13 instantly.
- The policy became deterministic — entropy 0.025 means the agent
  picks the same action in every state with >99.9% probability.
  It cannot explore or adapt. Effectively a fixed program.
- Episode return hit **-269,923** at 14.2B — the escalating wave
  stall penalty generated massive negative reward on a single bad
  episode (1052 ticks at wave 13 = ~550 ticks over limit, scale
  ~11x, penalty ~-8.25/tick for 550 ticks = -4,537 per episode,
  but accumulated over multiple waves within the episode).
- Policy could not recover because entropy=0 means no exploration.
  LR was still 0.0005 but every gradient update just reinforced the
  same frozen action distribution.
- food_eaten=0.0 — agent stopped eating entirely. pots_used=7.6 —
  barely drinking. correct_prayer=0.0 — praying 99% of ticks but
  never the correct type. The agent is a zombie — alive but not
  functioning.
- **The last 10B steps (50% of training) were completely wasted.**

Root cause analysis:

1. **LR annealing too slow for 20B.** Linear anneal from 0.001 over
   20B means LR was still 0.00075 at 5B when the policy was already
   near-converged. Previous 1.25B runs annealed fast enough that LR
   hit ~0 before destabilization could occur. With 20B the dangerous
   LR zone (0.001-0.0005) lasted for ~10B steps.

2. **Clipfrac at 0.4-0.5 for too long.** PPO's clip ratio is designed
   to limit policy updates. Clipfrac >0.3 means >30% of samples are
   being clipped — the policy wants to change faster than clipping
   allows. This sustained pressure eventually finds a way through
   (a bad batch that aligns with the clip direction), causing a
   destructive update that triggers the entropy collapse.

3. **Escalating wave stall penalty amplified the collapse.** Once the
   agent dropped to wave 13 and couldn't recover, the stall penalty
   generated -4K+ per episode. These extreme negative rewards created
   enormous gradients that pushed the policy further into the frozen
   state. The penalty designed to prevent stalling actually made
   recovery impossible.

4. **No entropy floor.** PPO's entropy coefficient (ent_coef=0.02)
   provides a small incentive to maintain entropy, but it was
   overwhelmed by the much larger reward signals. A minimum entropy
   constraint or adaptive entropy would have prevented the 0.025
   collapse.

Key takeaways:

- The v16.1 reward signals ARE WORKING. The agent reached wave 29.1
  with clean, non-exploited returns. The problem is training stability
  over long runs, not reward design.
- **The best policy exists at ~4.7B steps.** The checkpoint at that
  point should be the starting point for future work.
- Long runs need either: lower LR, faster annealing schedule,
  entropy floor, or checkpoint-based early stopping.
- The escalating wave stall penalty needs a cap to prevent
  catastrophic gradient amplification during collapse.

---

## v16.1 (2026-04-05)

Changes from v16:

Fixes for v16 prayer flick exploit + new signals:

Prayer flick reward fixed:
- Now requires ALL of: prayer just toggled, blocked an actual hit,
  no prayer point drained, AND agent has a target selected. Cannot
  be farmed by toggling prayer without engaging in combat.

NPC melee range penalty expanded:
- Now penalizes ALL NPCs at melee range (distance 1), not just
  Ket-Zek/Tok-Xil/MejKot. Includes Tz-Kih, Tz-Kek, Tz-Kek-Sm.
  Agent should kite everything.

NPC-specific prayer bonus expanded:
- Added Tz-Kih, Tz-Kek, Tz-Kek-Sm → protect melee (+2.0).
  Now all combat NPCs have explicit prayer mappings.

Wasted attack penalty (-0.3):
- Fires when attack timer is 0 (ready to fire), agent has a target
  but didn't deal damage this tick. Encourages attack-while-kiting.

Wave stall penalty (escalating after 500 ticks):
- -0.75/tick base after 500 ticks, scales +0.75 every 50 ticks.
  500=-0.75, 550=-1.50, 600=-2.25, etc. Resets on wave clear.

New analytics:
- food_wasted: sharks that overhealed (lower is better)
- pots_wasted: doses that over-restored (lower is better)

All other config unchanged from v16.

Hyperparameters: gamma=0.999, gae_lambda=0.95, clip_coef=0.2,
vf_coef=0.5, ent_coef=0.02, max_grad_norm=0.5, horizon=256,
minibatch_size=4096, total_agents=4096, hidden_size=256,
num_layers=2. total_timesteps=1.25B, lr=0.001, anneal_lr=1.

Results (1.25B steps, ~10.5min, wandb run wcfgtykg):
- SPS: 1.93M
- Wave reached: 25.16 avg (final), max wave 28
- Most NPCs slayed: 112
- Episode return: 3,144 (healthy, not inflated)
- Episode length: 2,231 (short — wave stall working)
- Score: 0
- Epochs: 1192
- Entropy: 6.565 (very healthy, still exploring)
- Clipfrac: 0.001 (LR annealed to ~0)
- Value loss: 20.8

Analytics:
- prayer_uptime: 0.800
- dmg_taken_avg: 2,730
- pots_used: 32.0
- food_eaten: 20.0

Progression — LATE BREAKTHROUGH PATTERN:
- 0-890M: Slow climb wave 17 → 20.8. Spent 890M steps building
  foundational skills (kiting, attacking, basic prayer). Much slower
  than previous runs which climbed to 21 in 100M steps.
- **890M: BREAKOUT.** Wave jumped 20.8→22→23.5→25.2 in ~200M steps.
  Entropy dropped 7.4→6.1 (policy committed to a strategy). Value
  loss spiked to 41 (world model updating dramatically). Policy loss
  hit -0.14 (massive policy update). Something clicked.
- 1010M-1250M: Stabilized at 25.0-25.3. Still climbing when LR
  annealed to ~0 and policy stopped updating.

Diagnosis — reward signals working, needs more steps:

First run with NO reward exploit. Episode return 3,144 is healthy
(not inflated). Episode length 2,231 is short (wave stall penalty
working). No prayer farming. The late breakthrough at 890M suggests
the agent needed ~900M steps to learn foundational behaviors before
the "real" learning could begin.

The breakthrough was STILL HAPPENING when training ended. The agent
was climbing from 23 to 25 in the final 200M steps. LR had already
annealed to ~0 (clipfrac=0.001), cutting off further learning. A
longer run would let the agent continue past wave 25.

Recommendation: same config, 2.5B steps. The agent needs ~1B to
build foundations, then ~1.5B at useful LR for the breakthrough
phase. No reward changes — v16.1 signals are clean.

---

## v16 (2026-04-05)

Changes from v15.3:

Major reward shaping overhaul focused on teaching prayer mechanics
and punishing resource waste through domain-specific signals.

New reward signals:
- **NPC-specific prayer bonus (+2.0):** Extra reward when correct
  prayer blocks a hit from a specific dangerous NPC while attacking:
  Ket-Zek→protect magic, Tok-Xil→protect missiles, MejKot→protect
  melee. Stacks with the generic +1.0 correct prayer reward. Explicit
  hint that shortcuts the NPC-prayer mapping the agent needs to learn.
- **Dangerous NPC melee range penalty (-0.5/tick per NPC):** Penalizes
  having Ket-Zek, Tok-Xil, or Yt-MejKot at distance 1. These NPCs
  should be kited. At melee range, dual-mode NPCs use melee attacks,
  making the agent's ranged prayer useless.
- **Prayer flick reward (+0.5):** Fires when prayer was just toggled
  ON this tick AND the drain counter hasn't exceeded resistance
  (meaning no prayer point was drained). Teaches the OSRS prayer
  flicking mechanic: activate prayer briefly to block a hit, then
  deactivate before drain fires. Conserves prayer points.

Changed reward signals:
- **Food/pot rewards removed entirely.** No +1 for good timing. Only
  waste-based penalty remains:
  - Full HP/prayer: -5.0 (complete waste)
  - Otherwise: -(wasted / max_heal_or_restore), 0 to -1.0 scaled
  - Philosophy: consuming scarce resources is never rewarded. The
    agent eats/drinks for survival, not for reward.

Removed legacy signals:
- All old food/pot timing rewards (+1/-1 at thresholds) removed
- Config weights w_food_used, w_food_used_well, w_prayer_pot_used
  are still parsed but completely unused

Analytics changes:
- Replaced `wrong_prayer` with `dmg_taken_avg` (total damage taken
  per episode). More useful for understanding overall survivability.
- Renamed `correct_blocks` to `correct_prayer` — tracks how many
  hits per episode were correctly blocked by matching prayer.

All other config unchanged from v15.3 (no curriculum, Loadout B,
anneal_lr=1, 1.25B steps, kiting +1.0, prayer waste -0.2).

Hardcoded in fight_caves.h:
- NPC-specific prayer: +2.0 (Ket-Zek/magic, Tok-Xil/ranged, MejKot/melee) — requires agent to have a target selected
- Dangerous NPC melee range: -0.5/tick per NPC at distance 1
- Prayer flick: +0.5 when prayer on without drain
- Food waste: -5.0 at full HP, else -(wasted/200)
- Pot waste: -5.0 at full prayer, else -(wasted/170)
- Kiting: +1.0 at range 5-7 while attacking (from v15.3)
- Prayer waste: -0.2/tick praying with no threat (from v15.3)
- Wrong prayer: -1.0 per hit (unchanged)
- Combat idle: -0.5/tick after 2 ticks (unchanged)

Hyperparameters: gamma=0.999, gae_lambda=0.95, clip_coef=0.2,
vf_coef=0.5, ent_coef=0.02, max_grad_norm=0.5, horizon=256,
minibatch_size=4096, total_agents=4096, hidden_size=256,
num_layers=2. total_timesteps=1.25B, lr=0.001, anneal_lr=1.

Results (1.25B steps, ~10.2min, wandb run qg2ovzqp):
- SPS: 2.06M
- Wave reached: 16.19 avg (massive regression from v15.3's 28.36)
- Max wave: 31
- Most NPCs slayed: 119
- Episode return: 5,834 (inflated — reward hacking)
- Episode length: 6,479 (stalling)
- Score: 0
- Epochs: 1192
- Entropy: 5.90
- Value loss: 47.3 (unstable)

Analytics:
- prayer_uptime: 0.917 (92% praying)
- correct_prayer: 0.0 (NEVER blocks a hit correctly)
- dmg_taken_avg: 2,604
- pots_used: 31.9
- food_eaten: 20.0

Progression:
- 0-70M: Climbed to wave 19, then immediately collapsed to 15-16.
- 70M-1.25B: Stuck at wave 15-17 for entire run. Episode return
  inflated to 5800-8500 despite low wave — reward hacking.

Diagnosis — PRAYER FLICK FARMING:

The agent exploited the prayer flick reward (+0.5 per tick when
prayer toggled on without drain). It toggles prayer every tick to
farm +0.5 per tick while the prayer_waste penalty is only -0.2.
Net: +0.3/tick for doing nothing useful. Over 6000 ticks this
generates ~+1800 free reward, dominating wave_clear entirely.

correct_prayer=0.0 confirms the agent NEVER actually blocks a hit.
It just toggles prayer for the flick reward without matching the
correct style. The NPC-specific prayer bonus (+2.0) doesn't fire
because prayer never matches the attack style.

The prayer flick reward is fundamentally exploitable — any per-tick
reward for toggling prayer can be farmed without engaging in combat.
It must be removed. Prayer conservation should be taught through
prayer_waste penalty (-0.2) alone. Correct prayer usage through
the on-hit correct_prayer reward (+1.0).

The dangerous NPC melee range penalty (-0.5) and NPC-specific
prayer bonus (+2.0) were not the issue — they couldn't fire because
the agent wasn't engaging correctly to begin with.

---

## v15.3 (2026-04-05)

Changes from v15.2:

Two new reward signals to teach kiting and prayer discipline:

Kiting reward (+1.0 hardcoded):
- Fires when agent deals damage while target NPC is at distance 5-7
  (near max weapon range of 7). Only fires when attacking — no
  reward for just standing at range without engaging.
- Incentivizes ranged combat at distance. Melee NPCs can't reach the
  agent at range 5+, so the agent dodges melee damage entirely.
  Dual-mode NPCs (Tok-Xil, Ket-Zek) use their ranged/magic attacks
  at distance, forcing the agent to learn correct prayer switching
  because only ranged/magic hits land.

Unnecessary prayer penalty (-0.2/tick hardcoded):
- Fires when prayer is active but no NPC is within its attack range
  of the player AND no pending hits are in flight.
- Teaches the agent to turn prayer OFF when nothing is threatening,
  conserving prayer points. Combined with kiting, the agent learns:
  stay at range → melee NPCs out of range → turn prayer off → only
  pray when ranged/magic attack incoming → pray the correct type.

No curriculum — starts at wave 1. Kiting is most impactful in early
waves where all NPCs are melee. Loadout B, anneal_lr=1, 1.25B steps.

Hardcoded in fight_caves.h (new):
- Kiting reward: +1.0 when attacking from range 5-7
- Prayer waste: -0.2/tick when praying with no threat in range

Hyperparameters: gamma=0.999, gae_lambda=0.95, clip_coef=0.2,
vf_coef=0.5, ent_coef=0.02, max_grad_norm=0.5, horizon=256,
minibatch_size=4096, total_agents=4096, hidden_size=256,
num_layers=2. total_timesteps=1.25B, lr=0.001, anneal_lr=1.

Results (1.25B steps, ~10.6min, wandb run fjss9fzb):
- SPS: 1.88M
- Wave reached: 28.36 avg
- Max wave: 32
- Most NPCs slayed: 121
- Episode return: 5,491
- Episode length: 4,716
- Entropy: 5.87
- Value loss: 21.9

Analytics:
- prayer_uptime: 0.902
- correct_blocks: 2,922
- wrong_prayer_hits: 110.9 (first time non-zero — kiting working)
- pots_used: 32.0
- food_eaten: 20.0

Progression:
- 0-200M: Climb to wave 28.5.
- 200M-1.25B: Stable 27.9-28.8. No collapse.

Diagnosis: Kiting reward caused wrong_prayer to jump from 0 to 110.
Agent now faces ranged attacks at distance. Hasn't learned to switch
prayer yet but is experiencing the training signal for the first time.

---

## v15.2 (2026-04-05)

Changes from v15.1:

Curriculum only — same loadout and reward weights as v15.1.
- curriculum_wave: 0 → **30**
- curriculum_pct: 0 → **1.0** (100% of episodes start at wave 30)

ALL episodes start at wave 30. Skips 29 waves of melee-only content
the agent already handles perfectly. Forces immediate exposure to
Tok-Xil (ranged), Ket-Zek (magic), and mixed combat requiring prayer
switching — the exact content the agent has never learned.

```ini
[env]
# All weights identical to v15/v15.1
curriculum_wave = 30
curriculum_pct = 1.0

[train]
total_timesteps = 1_250_000_000
learning_rate = 0.001
anneal_lr = 1
```

Loadout: B (Masori (f) + Twisted Bow, 99/99/99/99)
Curriculum: 100% of episodes start at wave 30

Hyperparameters: gamma=0.999, gae_lambda=0.95, clip_coef=0.2,
vf_coef=0.5, ent_coef=0.02, max_grad_norm=0.5, horizon=256,
minibatch_size=4096, total_agents=4096, hidden_size=256,
num_layers=2. total_timesteps=1.25B, lr=0.001, anneal_lr=1.

Results (1.25B steps, ~9.7min, wandb run bsf4ecjl):
- SPS: 2.06M
- Wave reached: 30.00 avg (never clears wave 30)
- Max wave: 33 (occasionally breaks through)
- Most NPCs slayed: 7 (per episode avg — barely denting wave 30)
- Episode return: 756
- Episode length: 6,194 (very long — surviving but not killing)
- Score: 0
- Epochs: 1192
- Entropy: 6.14 (healthy)
- Clipfrac: 0.006 (LR annealed)
- Value loss: 14.3

Analytics:
- prayer_uptime: 0.968 (97%! praying almost every tick)
- correct_blocks: 3,203 per episode
- wrong_prayer_hits: **0.0** (still zero across entire run)
- pots_used: 29.2
- food_eaten: **0.0** (never eats — doesn't need to with 99 HP + prayer)

Progression:
- Plateau at wave 30.00 for entire run. Episode return stable 750-890.
- Agent survives ~6200 ticks at wave 30 then runs out of prayer and dies.
- Never learns to clear wave 30 consistently.

Diagnosis — agent camps melee prayer and tanks everything:

wrong_prayer_hits=0.0 across every single run (v13-v15.2) reveals
the fundamental problem: the agent has NEVER been hit while praying
the wrong style. It camps protect melee and everything that reaches
melee range attacks with melee — including Tok-Xil, which is a
dual-mode NPC that switches from ranged to melee at distance 1.

The agent's strategy: stand still, pray melee, let all NPCs walk
into melee range (where they all use melee), block everything, wait
until prayer runs out, die. It works for 6000+ ticks but can't
kill Yt-MejKot fast enough (800 HP + heals nearby NPCs).

Wave 30 spawns: 2x Yt-MejKot (melee, 800 HP, heals) + Tok-Xil
(ranged/melee dual). The agent should kite Tok-Xil at range and
pray ranged, switch to melee for MejKot. Instead it tanks all three
with melee prayer and slowly chips away.

Root cause — misleading observation for dual-mode NPCs:

The `npc_atk_style` observation shows the NPC's BASE style (ranged
for Tok-Xil), not what it's CURRENTLY attacking with (melee when at
range 1). The agent sees "ranged NPC" but gets hit with melee. This
contradicts the prayer matching logic — the observation says "pray
ranged" but melee prayer blocks the actual hit. The agent learns to
ignore `npc_atk_style` and just camp melee because it always works.

---

## v15.1 (2026-04-05)

Changes from v15:

Loadout change only — no reward or training parameter changes.
Switched from Loadout A (mid-level) to Loadout B (end-game) to test
how much player power affects wave progression.

Player stats changed:
- HP: 70 → **99** (990 tenths)
- Prayer: 43 → **99** (990 tenths)
- Defence: 70 → **99**
- Ranged: 70 → **99**

Equipment changed:
- Weapon: Rune Crossbow → **Twisted Bow**
- Armour: Black D'hide → **Masori (f) set**
- Ammo: Adamant Bolts → **Dragon Arrows**
- Added: Ava's assembler, Necklace of anguish, Zaryte vambraces,
  Pegasian boots, Venator ring
- Prayer bonus: 0 → **9** (prayer drains ~30% slower)

```ini
[env]
curriculum_wave = 0
curriculum_pct = 0.0

[train]
total_timesteps = 1_250_000_000
learning_rate = 0.001
anneal_lr = 1
```

Loadout: B (Masori (f) + Twisted Bow, 99/99/99/99)

Hyperparameters: gamma=0.999, gae_lambda=0.95, clip_coef=0.2,
vf_coef=0.5, ent_coef=0.02, max_grad_norm=0.5, horizon=256,
minibatch_size=4096, total_agents=4096, hidden_size=256,
num_layers=2. total_timesteps=1.25B, lr=0.001, anneal_lr=1.

Results (1.25B steps, ~10.2min, wandb run wj3p2wj9):
- SPS: 2.08M
- Wave reached: 28.65 avg, 29.2 peak
- Max wave: **32** (first time breaking wave 30)
- Most NPCs slayed: **121**
- Episode return: 5,003
- Episode length: 4,717
- Entropy: 6.07
- Value loss: 34.96

Analytics:
- prayer_uptime: 0.911
- correct_blocks: 2,721
- wrong_prayer_hits: 0.0
- pots_used: 31.9
- food_eaten: 20.0

Progression:
- 0-200M: Fast climb wave 17 → 28.9.
- 200M-1.25B: Stable plateau at 28.6-29.2. No collapse.

Diagnosis: End-game gear broke the wave 22 ceiling. Wave 21.7→29.2
(+7 waves) with zero reward changes. wrong_prayer_hits=0 — agent
still only prays melee, hasn't learned prayer switching.

---

## v15 (2026-04-05)

Changes from v14:

Anti-reward-hacking — reduce all dense signal magnitudes:
- v14's +20 correct prayer reward caused the agent to farm prayer
  blocks at easy melee waves (+43K/episode) rather than progress.
  All dense signals reduced to ±1.0 so wave_clear is the dominant signal.
- w_correct_danger_prayer: 20.0 → **1.0**
- w_damage_dealt: 2.0 → **1.0**
- w_npc_kill: 5.0 → **2.0**
- Wrong prayer: -5.0 → **-1.0** hardcoded
- Food timing: +50/-30 → **+1/-1** hardcoded (threshold unchanged at 70% HP)
- Pot timing: +50/-30 → **+1/-1** hardcoded, threshold changed from
  20% → **70%** prayer. Agent rewarded for drinking at or below 70%
  prayer, penalized above.
- All other weights unchanged from v14.

Principle: wave_clear (10 × wave_num) should be the loudest signal.
At wave 22: wave_clear = +2530 total. Prayer blocks at +1 each =
~+2000. Kills at +2 each = ~+100. No single dense signal dominates.

Training: 1.25B steps (shorter run for faster iteration).

```ini
[env]
w_damage_dealt = 1.0
w_damage_taken = -0.75
w_npc_kill = 2.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_correct_danger_prayer = 1.0
w_invalid_action = -0.1
w_tick_penalty = -0.001
curriculum_wave = 0
curriculum_pct = 0.0
disable_movement = 0

[train]
total_timesteps = 1_250_000_000
learning_rate = 0.001
anneal_lr = 1
```

Hardcoded in fight_caves.h:
- Wrong prayer: -1.0 per hit with wrong prayer active
- Wave clear: w_wave_clear × cleared_wave_number
- Combat idle: -0.5/tick after 2 ticks no damage dealt
- Food at/below 70% HP (pre-eat): +1.0, above 70%: -1.0
- Prayer pot at/below 70% prayer (pre-drink): +1.0, above 70%: -1.0

Hyperparameters: gamma=0.999, gae_lambda=0.95, clip_coef=0.2,
vf_coef=0.5, ent_coef=0.02, max_grad_norm=0.5, horizon=256,
minibatch_size=4096, total_agents=4096, hidden_size=256,
num_layers=2. total_timesteps=1.25B, lr=0.001, anneal_lr=1.

Results (1.25B steps, ~10.6min, wandb run f6z3abj1):
- SPS: 1.94M
- Wave reached: 21.65 avg (final), 21.8 peak
- Max wave: 26 (all-time best single episode)
- Most NPCs slayed: 94
- Episode return: 2,680 (healthy, not inflated)
- Episode length: 2,730
- Score: 0
- Epochs: 1192
- Entropy: 5.984 (healthy, slightly rising at end)
- Clipfrac: 0.006 (naturally decayed via LR annealing)
- Value loss: 3.52 (stable 3-4 entire run — best stability ever)
- Policy loss: -0.025

Analytics:
- prayer_uptime: 0.707 (71% of ticks praying)
- correct_blocks: 1,368 per episode
- wrong_prayer_hits: 0.0 (perfect prayer against melee waves)
- pots_used: 32.0 (all doses consumed)
- food_eaten: 20.0 (all sharks consumed)

Progression:
- 0-130M: Fast climb wave 9 → 20.6. Return 163 → 2224.
- 130M-1.25B: Gradual plateau at wave 21.4-21.8. Return slowly
  climbed from 2224 to 2680. Value loss stable at 3-4.6.
- **NO COLLAPSE.** First run without late-training instability.
  LR annealing worked — clipfrac naturally decayed from 0.036 to
  0.006 as LR approached 0. Entropy stayed healthy at 5.8-6.0.

Compared to v14: reward hacking eliminated. Episode return 2,680
vs v14's inflated 42,121. No wave regression — v14 collapsed from
21.8 to 13, v15 held at 21.7 for the entire run.

Compared to v13 (best prior peak): identical wave ceiling (~21.8)
but v15 is stable where v13 collapsed to 16. The reduced signal
magnitudes successfully prevented both reward hacking and late
instability.

Diagnosis — wave 22 ceiling:
The agent plateaus at wave 21-22 in every run (v13, v14, v15).
Wave 22 introduces Tok-Xil (ranged, lv90) alongside melee NPCs.
The agent has never learned to prayer-switch between melee and
ranged because it rarely survives past wave 21 to get exposure.
wrong_prayer_hits = 0 confirms the agent only faces melee content
and prays melee perfectly — it has zero experience with mixed
combat styles.

Food/pot consumption: still 32/20 per episode. With +1/-1 rewards,
the signal is too weak to change behavior. But consumption is now
at the correct thresholds (HP <=70%, prayer <=70%) — the agent
isn't wasting resources, it's just using them as fast as prayer
drains and combat damages HP. This is a resource economy issue,
not a reward shaping issue.

Key takeaway: reward shaping is now clean and stable. The wave 22
wall is a content exposure problem — the agent needs to see Tok-Xil
and Ket-Zek to learn prayer switching. Options for v16: curriculum
(expose agent to wave 22+ content), or longer training at 5B+ to
give more episodes to randomly break through.

---

## v14 (2026-04-05)

Changes from v13:

Prayer reward/penalty overhaul:
- Correct prayer: 1.0 → **20.0**. Massive positive reinforcement when
  the agent has the correct protection prayer active and a hit of that
  style resolves. Only fires when prayer is active at resolution time —
  toggling too late gives nothing. Uses config weight w_correct_danger_prayer.
- Wrong prayer: **NEW, -5.0 hardcoded**. When a hit resolves and the agent
  has a prayer active that does NOT match the attack style, -5.0 penalty.
  Fires even on 0-damage hits (misses). Previously there was no penalty
  for wrong prayer — the agent could pray missiles against melee-only
  NPCs with zero signal that anything was wrong. Now the agent gets
  explicit negative feedback for having the wrong prayer on.
- The +20/-5 asymmetry is intentional: correct prayer should be strongly
  rewarded to encourage the agent to learn which prayer matches which NPC,
  while wrong prayer gets a moderate penalty. The quadratic damage_taken
  penalty already heavily punishes unblocked hits on top of the -5.

Food and prayer pot — flat reward/penalty with pre-consumption check:
- Replaced old waste-scaled system with flat values. Reward function
  checks HP/prayer BEFORE the heal/restore is applied (using
  pre_eat_hp / pre_drink_prayer saved in fc_tick.c). Previous versions
  checked post-consumption values which made the pot reward impossible
  to trigger (pot restore always pushed prayer above 20% threshold).
- **Food at or below 70% HP (pre-eat): +50.0 reward.**
- **Food above 70% HP (pre-eat): -30.0 penalty.**
- **Prayer pot at or below 20% prayer (pre-drink): +50.0 reward.**
- **Prayer pot above 20% prayer (pre-drink): -30.0 penalty.**
- Note: PufferLib 4 CUDA kernel does NOT enforce action masks at the
  sampling level. Masks are observation features the policy learns
  from, not hard constraints. Standard masks (no food, on cooldown,
  full HP/prayer) are still set but are advisory only.

Hit source observation:
- NEW: player_hit_source (obs index 20). When an NPC hits the player,
  the agent now sees which NPC type dealt the damage. Normalized as
  npc_type / NPC_TYPE_COUNT. Lets the agent learn "Ket-Zek = magic,
  Tok-Xil = ranged" directly rather than inferring from pending attacks.

Training stability:
- anneal_lr: 0 → **1**. Linear LR decay from 0.001 to 0 over training.
  Every run with constant lr=0.001 has collapsed late (v10 at ~1B,
  v11 at ~1.2B, v13 at ~1.4B). Annealing gives aggressive early
  learning with stable late fine-tuning.
- total_timesteps: 1.5B → **5B**. Much longer run to take advantage of
  LR annealing and give the new prayer signals time to shape behavior.

No curriculum. No movement changes. Wave-scaled clear reward unchanged.

```ini
[env]
w_damage_dealt = 2.0
w_damage_taken = -0.75
w_npc_kill = 5.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.5
w_food_used_well = 1.0
w_prayer_pot_used = -1.0
w_correct_danger_prayer = 20.0
w_wrong_danger_prayer = 0.0
w_invalid_action = -0.1
w_tick_penalty = -0.001
curriculum_wave = 0
curriculum_pct = 0.0
disable_movement = 0

[train]
total_timesteps = 5_000_000_000
learning_rate = 0.001
anneal_lr = 1
```

Hardcoded in fight_caves.h:
- Wrong prayer: -5.0 per hit with wrong prayer active
- Wave clear: w_wave_clear × cleared_wave_number
- Combat idle: -0.5/tick after 2 ticks no damage dealt
- Food at/below 70% HP (pre-eat): +50.0, above 70%: -30.0
- Prayer pot at/below 20% prayer (pre-drink): +50.0, above 20%: -30.0

Hyperparameters: gamma=0.999, gae_lambda=0.95, clip_coef=0.2,
vf_coef=0.5, ent_coef=0.02, max_grad_norm=0.5, horizon=256,
minibatch_size=4096, total_agents=4096, hidden_size=256,
num_layers=2. total_timesteps=5B, lr=0.001, anneal_lr=1.

Results (5B steps, ~41.5min, wandb run tutxtn7x):
- SPS: 1.94M
- Wave reached: 13.03 avg (final), 21.8 peak (at ~1B steps)
- Max wave: 26 (all-time best single episode)
- Most NPCs slayed: 94
- Episode return: 42,121 (final — massively inflated)
- Episode length: 3,436 (stable, no stalling)
- Score: 0
- Epochs: 4768
- Entropy: 5.287 (healthy)
- Clipfrac: 0.097 (collapsed to near-zero by end — LR annealed out)
- Value loss: 48.4 (unstable at end)
- Policy loss: -0.009

Analytics:
- prayer_uptime: 0.762 (praying 76% of ticks)
- correct_blocks: 2,167 per episode
- wrong_prayer_hits: 0.0 (never has wrong prayer when hit)
- pots_used: 32.0 (all doses consumed every episode)
- food_eaten: 20.0 (all sharks consumed every episode)

Progression:
- 0-1B: Climbed wave 9 → 21.8. Return 26K-30K.
- ~1.2B: Collapsed wave 21.8 → 13.0. Return INCREASED to 41K-43K.
- 1.5B-5B: Stuck at wave 13.0 for 3.5B steps. Never recovered.

Diagnosis — REWARD HACKING via prayer blocks:

The agent earns MORE reward at wave 13 than at wave 22. The +20.0
correct prayer reward completely dominates all other signals:
- correct_blocks per episode: 2,167 × +20.0 = **+43,340**
- wave_clear rewards (waves 1-13): 10+20+...+130 = **+910**
- Prayer blocks = **47x** more valuable than wave progression

Waves 1-13 are all melee NPCs. The agent prays protect melee, blocks
every hit (+20 each), and has zero incentive to push to wave 14+ where
Tok-Xil (ranged) would require risky prayer switching. Farming melee
prayer blocks at easy waves is overwhelmingly more profitable than
progressing.

The +50 food/pot rewards also contribute to inflation (eating 20
sharks at +50 each = +1000, drinking 32 doses at +50 each = +1600)
but prayer blocks are the dominant factor.

LR annealing (0.001 → 0) did NOT prevent the collapse — it happened
at ~1.2B when LR was still 0.00075. Annealing did prevent the late
value-loss explosion seen in v13 (value loss stable at 5-11 for most
of training vs v13's 3→11 spike). But by the end (LR≈0), clipfrac
dropped to 0.097 and the policy stopped updating entirely.

Lesson: any dense per-hit reward that fires thousands of times per
episode will dominate sparse event rewards (wave clear, kills). The
reward magnitude must be proportional to rarity: frequent signals
need small weights, rare milestones need large weights.

---

## v13 (2026-04-05)

Changes from v12:

Wave clear reward scaling:
- Wave clear now scales with wave number: reward = w_wave_clear × wave.
  Wave 1 clear = 10. Wave 15 = 150. Wave 30 = 300. Wave 63 = 630.
  Previously a flat 10.0 regardless of wave. The flat reward meant the
  agent earned the same +10 clearing wave 1 as wave 30, so farming easy
  waves (fewer per-tick penalties, simpler combat) was equally profitable.
  Scaling makes pushing to higher waves overwhelmingly more valuable than
  any per-tick exploit.

  v12 analysis showed the agent earned ~600 episode_return at both wave 22
  (before collapse) and wave 13 (after collapse). Per-tick rewards (combat
  idle, prayer, damage) accumulated over longer episodes at easy waves and
  equaled the wave-clear bonuses from harder waves. Scaling breaks this:
  clearing waves 1-22 gives 10+20+...+220 = 2530 total. Clearing waves
  1-13 gives only 10+20+...+130 = 910 total. The agent can't earn more
  by farming easy waves.

All other config unchanged from v12. Movement enabled, no curriculum,
anneal_lr=0, combat idle -0.5 after 2 ticks.

```ini
[env]
# unchanged from v12, wave_clear scaling is in code not config
w_wave_clear = 10.0

[train]
total_timesteps = 1_500_000_000
```

Hardcoded in fight_caves.h:
- Wave clear: w_wave_clear × cleared_wave_number (scales with progression)
- Combat idle: -0.5/tick after 2 ticks no damage dealt
- Food timing: +1.0 for eating with 20+ HP missing
- Prayer pot below 20%: +2.0, above 80%: -3.0

Hyperparameters: gamma=0.999, gae_lambda=0.95, clip_coef=0.2,
vf_coef=0.5, ent_coef=0.02, max_grad_norm=0.5, horizon=256,
minibatch_size=4096, total_agents=4096, hidden_size=256,
num_layers=2. total_timesteps=1.5B, lr=0.001, anneal_lr=0.

Results (1.5B steps, ~12.5min, wandb run 0pwza2dq):
- SPS: 1.94M
- Wave reached: 16.01 avg (final), 21.8 peak (at ~1428M steps)
- Max wave: 26 (all-time best single episode)
- Most NPCs slayed: 94
- Episode return: 2040.5 (final), 2846 peak (at ~1428M)
- Episode length: 2737 (stable throughout, no stalling)
- Score: 0
- Epochs: 1430
- Entropy: 4.915 (final — dropped from ~5.8 plateau)
- Clipfrac: 0.275 (elevated at end)
- Value loss: 11.26 (spiked at end — was ~3 for most of training)
- Policy loss: 0.023
- KL: 0.022

Progression:
- 0-80M: Fast climb, wave 9 → 19.5. Agent learns basic combat quickly.
- 80M-1430M: Plateaued at wave 21-22 for the entire training run.
  Wave oscillated 21.1-21.8, return slowly climbed 2224 → 2846.
  Agent never broke past wave 22 consistently. Entropy stable at
  5.7-5.8, value loss stable at 3-4, clipfrac ~0.22. Training was
  healthy but the agent could not progress.
- 1430M-1500M: Late collapse. Wave dropped 21.8 → 16.0. Value loss
  spiked 3.0 → 11.3. Entropy dropped 5.7 → 4.9. Clipfrac rose to
  0.27. This is a catastrophic policy update — the value function
  diverged, bad advantages were computed, and a large destructive
  policy update destroyed learned behavior.

Compared to v11 (best prior run, an75g72f):
- Wave: 21.8 peak → **29.23** (v13 is 8 waves worse at peak)
- Return: 2846 peak → 2045 (comparable)
- Entropy: 5.7-5.8 → 5.56 (similar)
- Value loss: 3-4 → 28.1 (v13 much healthier until collapse)
- Episode length: 2700-2900 → 2973 (similar, no stalling in either)

Diagnosis — why v13 regressed 8 waves from v11:

1. **No curriculum is the primary cause.** v11 used curriculum_wave=28,
   curriculum_pct=0.5 — half of all episodes started at wave 28, so the
   agent regularly faced Tok-Xil+Yt-MejKot combos and occasionally saw
   Ket-Zek (wave 31+). In v13, the agent starts wave 1 every episode and
   must grind through 21 waves of easy melee-only content before seeing
   anything challenging. It never gets enough exposure to hard content
   to learn how to handle it.

2. **Movement adds complexity.** v11 disabled movement entirely. v13
   re-enabled it. With 17 move actions available every tick, the agent
   has much more to explore. The combination of movement + no curriculum
   means the agent has a harder problem (larger action space) and less
   exposure to the hard content that would teach it to use prayer
   switching and kiting effectively.

3. **Wave-scaled rewards may be underweighting early progress.** With
   scaling, clearing waves 1-10 gives 10+20+...+100 = 550 total. With
   flat 10.0 (v11), waves 1-10 gave 100 total. The scaled reward
   makes early waves worth MORE, but the agent can't distinguish "I
   should push harder" from "I'm already getting good reward." The
   flat reward in v11 was simpler and the agent found it equally
   worth pushing to wave 29 as wave 10 — the only difference was
   duration. This interacts with curriculum: with curriculum, the
   agent saw waves 28-31 often enough that the high wave-clear
   rewards there (~300-310) dominated. Without curriculum, the agent
   never sees those rewards.

4. **Late collapse from constant LR.** anneal_lr=0 with lr=0.001 is
   too aggressive for late training. Same failure mode as v10 and v11
   late instability. At 1430M steps the policy was near-converged at
   wave 21.8, and a single bad batch caused value loss to spike 3→11
   and wave to drop to 16. LR decay or a lower constant LR (0.0003)
   would reduce this risk.

Recommendations for v14:
- Re-enable curriculum: curriculum_wave=30, curriculum_pct=0.5. This
  was the single biggest factor in v11's success. The agent needs
  regular exposure to Ket-Zek (magic, wave 31+) and Tok-Xil+MejKot
  combos to learn prayer switching and target priority.
- Consider anneal_lr=1 or reducing LR to 0.0003. Every run with
  constant 0.001 eventually collapses (v10, v11, v13). The late
  instability is a recurring pattern.
- Keep movement enabled. v11 hit wave 29 without movement but couldn't
  push past it. Movement is needed for kiting Yt-MejKot and dodging
  multi-NPC combos in waves 30+. The solution to movement complexity
  is curriculum exposure, not disabling movement.
- Keep wave-scaled rewards. The concept is sound (prevent farming easy
  waves) but it needs curriculum to expose the agent to the high-value
  wave clears that make scaling worthwhile.

---

## v12 (2026-04-04)

Changes from v11:

Movement:
- RE-ENABLED. disable_movement=0. Full action space restored. v10-v11
  disabled movement to isolate combat learning, but without movement the
  agent couldn't get past wave 14-15 (Yt-MejKot with 800HP + healing).
  Movement is part of how the agent survives (kiting, repositioning).

Curriculum:
- Disabled (wave 0). Agent starts wave 1 every episode.

Pre-impact prayer window:
- REMOVED. The +0.5 per pending hit per tick within 3-tick window was
  being EXPLOITED by the agent. With multiple NPCs attacking, correct
  prayer generated +1.5-2.0/tick of free reward — 10x more than combat
  rewards (damage_dealt ≈ +0.05/tick). The agent learned to just turn
  prayer on and stop fighting, collecting prayer reward passively.
  This caused the deterministic collapse at ~40M steps seen in 3
  consecutive runs: agent rapidly reaches wave 20+, discovers the prayer
  exploit, commits to it, stops killing NPCs, waves stall, dies.
  The correct-prayer-at-resolve reward (+1.0 per hit) remains and is
  sufficient — it fires once per hit, not per tick, so can't be farmed.

Combat idle punishment:
- NEW. -0.5 per tick when agent hasn't dealt damage in 2+ ticks and
  NPCs are alive. Forces the agent to engage in combat instead of
  standing idle with prayer on. Counter resets when damage is dealt.
  Tracked via ticks_since_attack in FightCaves struct.

New metrics:
- max_wave: highest wave any single episode reached (all-time max).
- most_npcs_slayed: highest NPC kill count in any single episode.
  Both tracked as globals, visible in wandb User Stats.

```ini
[env]
w_damage_dealt = 2.0
w_damage_taken = -0.75
w_npc_kill = 5.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.5
w_food_used_well = 1.0
w_prayer_pot_used = -1.0
w_correct_danger_prayer = 1.0
w_wrong_danger_prayer = 0.0
w_invalid_action = -0.1
w_tick_penalty = -0.001
curriculum_wave = 0
curriculum_pct = 0.0
disable_movement = 0

[train]
total_timesteps = 1_500_000_000
learning_rate = 0.001
anneal_lr = 0
```

Hardcoded rewards in fight_caves.h (not in config):
- food_used_well: +1.0 for eating with 20+ HP missing
- prayer_pot below 20%: +2.0 for drinking when low
- prayer_pot above 80%: -3.0 extra for wasteful drinking
- combat idle: -0.5/tick after 2 ticks of no damage dealt

Hyperparameters: gamma=0.999, gae_lambda=0.95, clip_coef=0.2,
vf_coef=0.5, ent_coef=0.02, max_grad_norm=0.5, horizon=256,
minibatch_size=4096, total_agents=4096, hidden_size=256,
num_layers=2. total_timesteps=1.5B, lr=0.001, anneal_lr=0.

Results: not run — v12 config was superseded by v13 before training.

---

## v11 (2026-04-04)

Changes from v10 (based on policy replay of v10 peak + collapse):

Prayer correctness:
- Correct prayer reward now fires for ALL attack styles (melee, ranged,
  magic), not just ranged/magic. In v9/v10 the agent had zero signal for
  melee prayer in waves 1-28 (all melee). It randomly switched between
  protect ranged and magic because those were the only styles that gave
  reward. Now protect melee is rewarded in early waves.
- Pre-impact window (+0.5) also fires for ALL styles, not just ranged/magic.

Prayer drain cost:
- REMOVED entirely. The -0.01/tick cost from v9/v10 taught the agent to
  never pray (v9: 67K tick stalling, v10: contributed to value loss
  explosion). Prayer conservation now handled by potion timing reward.

Prayer potion timing:
- Below 20% prayer: +2.0 reward for drinking (was +1.0 in v10)
- Above 80% prayer: -3.0 extra penalty for drinking (on top of existing
  waste scaling). Agent punished for wasteful chugging.
- Between 20-80%: only the waste scaling applies (base -1.0)

No wrong-prayer penalty: kept at 0 (removed since v9). damage_taken
quadratic penalty handles this implicitly — wrong prayer = full damage =
harsh penalty proportional to hit size.

Movement: still disabled (disable_movement=1).
anneal_lr: still 0 (constant LR).
All config weights unchanged from v10.

Code changes (fight_caves.h):
- fc_puffer_compute_reward: prayer correctness checks ATTACK_NONE instead
  of ATTACK_RANGED||ATTACK_MAGIC. Pre-impact window same change.
- Removed prayer drain cost block.
- Prayer potion timing: +2.0 below 20%, -3.0 above 80%.

```ini
[base]
env_name = fight_caves

[env]
w_damage_dealt = 2.0
w_damage_taken = -0.75
w_npc_kill = 5.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.5
w_food_used_well = 1.0
w_prayer_pot_used = -1.0
w_correct_jad_prayer = 0.0
w_wrong_jad_prayer = 0.0
w_correct_danger_prayer = 1.0
w_wrong_danger_prayer = 0.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = 0.0
w_tick_penalty = -0.001
curriculum_wave = 28
curriculum_pct = 0.5
disable_movement = 1

[train]
total_timesteps = 1_250_000_000
learning_rate = 0.001
anneal_lr = 0
```

Hyperparameters: gamma=0.999, gae_lambda=0.95, clip_coef=0.2,
vf_coef=0.5, ent_coef=0.02, max_grad_norm=0.5, horizon=256,
minibatch_size=4096, total_agents=4096, hidden_size=256,
num_layers=2. total_timesteps=1.25B, lr=0.001, anneal_lr=0.

Results (1.25B steps, ~10.1min, wandb run an75g72f):
- SPS: 2.0M
- Wave reached: 29.23 avg
- Max wave: N/A (metric added after this run started)
- Most NPCs slayed: N/A (metric added after this run started)
- Episode length: 2973 (stable, not stalling)
- Episode return: 2045 (up from v10's 993)
- Score: 0
- Epochs: 1192
- Entropy: 5.56 (healthy — not collapsing, not random)
- Clipfrac: 0.310 (high but stable — not diverging like v10)
- Value loss: 28.1 (healthy, similar to v7's 31.7)
- Policy loss: 0.124
- KL: 0.029

Progression notes: Agent hit wave 30.0 by 67M steps and stayed near 30
for most of training. Episode return climbed steadily from 747 to 2658
at 889M steps. Then a late dip at 1187M (value_loss spiked to 154,
return dropped to 497) before recovering to 2045. The late instability
may be from anneal_lr=0 keeping LR too high (0.001) when policy should
be fine-tuning.

Compared to v10:
- Wave: 28.74 → 29.23 (improved, though not 30.0)
- Episode return: 993 → 2045 (2x improvement)
- Value loss: 325 → 28 (12x better — stable training)
- Entropy: 3.13 → 5.56 (much healthier, not over-converging)
- Episode length: 1732 → 2973 (surviving longer)

Key improvements from v11 changes:
- Removing prayer drain cost eliminated the v9/v10 death spiral.
- Correct prayer reward for ALL styles (including melee) gave the agent
  a learning signal in early waves. Previously had zero signal for the
  first 28 waves of melee.
- Prayer potion timing (+2 below 20%, -3 above 80%) should help with
  pot conservation, though replay needed to verify.

Still stuck at wave 30 wall. Agent dies during wave 30 (Yt-MejKot +
Tok-Xil + 2× Tz-Kek = 6+ simultaneous NPCs). Has never seen Ket-Zek
(wave 31+). Consider curriculum at wave 30 or 31 to expose agent to
harder content.

Late training instability (1187M spike) suggests anneal_lr=0 with
lr=0.001 is too aggressive for late fine-tuning. Consider anneal_lr=1
with lower starting LR, or reducing LR to 0.0003.

---

## v10 (2026-04-04)

Changes from v8 (based on v8 regression analysis + policy replay):

Philosophy shift: stop punishing wrong prayer explicitly. Let damage_taken
handle it implicitly. Use positive reinforcement and natural costs instead.

Reward changes:
- w_correct_danger_prayer: 2.0 → 1.0, now RANGED/MAGIC ONLY. Positive
  reinforcement when correct prayer blocks a dangerous hit. Melee excluded.
- w_wrong_danger_prayer: -5.0 → 0.0. Removed entirely. The quadratic
  damage_taken penalty already punishes wrong prayer (wrong prayer = full
  damage = big penalty). Explicit punishment created too much noise in v8.
- Prayer drain cost: NEW, hardcoded -0.01/tick when any prayer is active.
  Incentivizes turning prayer OFF when not needed. Over 100 ticks of
  leaving prayer on = -1.0. Pushes toward prayer flicking (on before hit,
  off after). Teaches prayer conservation without prayer potions.
- Prayer potion timing: NEW, +1.0 for drinking when prayer below 20%
  (86/430 tenths). Combined with existing waste scaling, agent learns to
  drink only when actually low.

Code changes:
- fight_caves.h: prayer correctness check restricted to ranged/magic only,
  removed wrong-prayer branch, added per-tick prayer drain cost, added
  prayer potion timing bonus.

All other weights unchanged from v8:
- w_damage_dealt=2.0, w_npc_kill=5.0, w_food_used=-0.5, w_prayer_pot=-1.0
- w_damage_taken=-0.75 (quadratic), w_player_death=-20.0

```ini
[base]
env_name = fight_caves

[env]
w_damage_dealt = 2.0
w_damage_taken = -0.75
w_npc_kill = 5.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.5
w_food_used_well = 1.0
w_prayer_pot_used = -1.0
w_correct_jad_prayer = 0.0
w_wrong_jad_prayer = 0.0
w_correct_danger_prayer = 1.0
w_wrong_danger_prayer = 0.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = 0.0
w_tick_penalty = -0.001
curriculum_wave = 28
curriculum_pct = 0.5

[vec]
total_agents = 4096
num_buffers = 1

[train]
total_timesteps = 1_250_000_000
learning_rate = 0.001
gamma = 0.999
gae_lambda = 0.95
clip_coef = 0.2
vf_coef = 0.5
ent_coef = 0.02
max_grad_norm = 0.5
horizon = 256
minibatch_size = 4096

[policy]
hidden_size = 256
num_layers = 2
```

Results (1.25B steps, ~10.6min, wandb run njxrz8l2):
- SPS: 1.9M
- Wave reached: 29.81 avg (recovered from v8's 28.05, close to v7's 29.96)
- Episode length: 67505 (STALLING AGAIN — back to v6.1 behavior)
- Episode return: 1158
- Score: 0
- Epochs: 1192
- Entropy: 8.16 (shot UP — policy became near-random)
- Clipfrac: 0.0002 (policy stopped updating entirely)
- Value loss: 20.8 (healthy, reward signal stable)
- Policy loss: -0.017
- KL: 0.0004

Diagnosis: Removing wrong-prayer penalty fixed the instability from v8
(value loss back to healthy 20 vs 222). But the -0.01/tick prayer drain
cost taught the agent to turn prayer OFF entirely. No prayer = no drain
cost. Agent found a strategy to avoid damage without prayer (safespotting/
kiting) and converged to it. Clipfrac dropped to 0 — policy stopped
learning. Entropy rose to 8.16 (near-random) because the policy gave up
on combat actions. Episode length 67505 = stalling again like v6.1.

Lesson: per-tick prayer cost incentivizes no-prayer-ever, not prayer
flicking. Need positive incentives for correct prayer usage, not costs
for having prayer on.

---

## v10 (2026-04-04)

Changes from v9:

Observation improvements:
- Added 2 new per-NPC features (NPC_STRIDE 13→15, OBS_SIZE 171→187):
  - npcN_pending_attack_style: attack style of projectile in flight from
    this NPC (0=none, 0.33=melee, 0.67=ranged, 1.0=magic)
  - npcN_pending_attack_ticks: ticks until that hit resolves (normalized /10)
  Agent now sees "Ket-Zek magic attack landing in 2 ticks" and can switch
  prayer preemptively.

Reward changes:
- Pre-impact prayer window: NEW, +0.5 per tick when correct prayer is
  active within 3 ticks of a ranged/magic hit resolving. Rewards having
  correct prayer up BEFORE impact.
- Prayer drain cost: kept at -0.01/tick (from v9).
- All other weights unchanged from v9.

Training changes:
- anneal_lr: 1 → 0. Constant learning rate (0.001) throughout training.
  Prevents late-training stalling from LR decay.
- disable_movement: NEW flag = 1. Agent's movement action forced to idle.
  Cannot walk or run. NPCs come to the agent. Forces learning of combat
  actions (prayer, attack, eat, drink) without movement as a crutch.

```ini
[env]
w_damage_dealt = 2.0
w_damage_taken = -0.75
w_npc_kill = 5.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.5
w_food_used_well = 1.0
w_prayer_pot_used = -1.0
w_correct_danger_prayer = 1.0
w_wrong_danger_prayer = 0.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = 0.0
w_tick_penalty = -0.001
curriculum_wave = 28
curriculum_pct = 0.5
disable_movement = 1

[train]
anneal_lr = 0
total_timesteps = 1_250_000_000
```

Results (1.25B steps, ~10.8min, wandb run m8nr3tnt):
- SPS: 1.8M
- Wave reached: 28.74 avg (down from v9's 29.81)
- Episode length: 1732 (SHORT — agent dying fast without movement)
- Episode return: 993
- Score: 0
- Epochs: 1192
- Entropy: 3.13 (very low — agent converged hard)
- Clipfrac: 0.414 (VERY HIGH — policy thrashing)
- Value loss: 325 (CATASTROPHIC — worse than v8's 222)
- Policy loss: 0.009
- KL: 0.158 (extremely high — policy diverging)

Progression notes: Agent peaked at wave 30 and ep_return ~2400 around
500M-700M steps. Then value_loss exploded (11→325) and the agent
collapsed. Wave dropped 30→28.7, episode length 3000→1732. The policy
destabilized in the second half of training.

Diagnosis: Two problems compounded:
1. Without movement, the agent takes more damage (can't kite/dodge).
   This amplifies the quadratic damage_taken penalty, making rewards
   more volatile → value_loss explosion.
2. The -0.01/tick prayer drain cost is STILL causing problems. With no
   movement, prayer is more important (can't avoid hits). But the drain
   cost pushes the agent to turn prayer off, causing more damage, which
   causes more penalty, creating a death spiral.
3. anneal_lr=0 kept the LR at 0.001 throughout, which may have caused
   the late-training instability (policy kept making large updates even
   as it should have been fine-tuning).

---

## v9 (2026-04-04)

Changes from v7 (based on policy replay observations):

Prayer fix:
- Prayer correctness now fires for ALL attack types including melee
  (v7 only checked ranged/magic). Fires on zero-damage hits too.
- No prayer active when hit = same punishment as wrong prayer.
- w_correct_danger_prayer: 2.0 (unchanged)
- w_wrong_danger_prayer: 0.0 → -5.0 (harsh punishment for wrong prayer)

Combat incentive:
- w_damage_dealt: 0.5 → 2.0 (4x stronger incentive to attack)
- w_npc_kill: 3.0 → 5.0 (bigger kill reward)

```ini
[env]
w_damage_dealt = 2.0
w_damage_taken = -0.75
w_npc_kill = 5.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.5
w_food_used_well = 1.0
w_prayer_pot_used = -1.0
w_correct_danger_prayer = 2.0
w_wrong_danger_prayer = -5.0
w_invalid_action = -0.1
w_idle = 0.0
w_tick_penalty = -0.001
curriculum_wave = 28
curriculum_pct = 0.5
```

Hyperparameters: gamma=0.999, gae_lambda=0.95, clip_coef=0.2,
vf_coef=0.5, ent_coef=0.02, max_grad_norm=0.5, horizon=256,
minibatch_size=4096, total_agents=4096, hidden_size=256,
num_layers=2. total_timesteps=2.5B, lr=0.001, anneal_lr=1.

Results (2.5B steps, ~23min, wandb run w8nsir07):
- SPS: 1.7M (down from v7's 2.0M)
- Wave reached: 28.05 avg (REGRESSED from v7's 29.96 — lost 2 waves)
- Episode length: 2615 (down from v7's 5438 — dying 2x faster)
- Episode return: 2564 (down from v7's 3421)
- Score: 0
- Epochs: 2384
- Entropy: 4.56 (lower than v7's 5.3)
- Clipfrac: 0.280 (4.5x v7's 0.061 — UNSTABLE, policy thrashing)
- Value loss: 222.6 (7x v7's 31.7 — CATASTROPHIC)
- Policy loss: -0.053
- KL: 0.032

Diagnosis: w_wrong_danger_prayer=-5.0 destroyed training. In waves 1-28,
melee NPCs attack every 4 ticks. Each hit with wrong prayer = -5.0.
Over a 3000-tick episode that's ~3750 in prayer penalties, drowning out
all other rewards. Value loss exploded 7x, clipfrac hit 0.28 (28% of
updates clipped). Agent regressed 2 waves and learned nothing useful.

Lesson: don't apply equal punishment for all prayer mismatches. Melee
attacks are frequent and low-damage. Punishing them at -5.0 per hit
overwhelms the reward signal. Let damage_taken handle it implicitly.

---

## v7 (2026-04-04)

Changes from v6/v6.1 (based on policy replay observations):

Critical bug fix:
- Collision map was never loaded during training. PufferLib runs from
  pufferlib_4/ directory where none of the search paths in fc_state.c
  resolved, so setup_arena() fell back to an open arena (all tiles
  walkable, border walls only). ALL v1-v6.1 runs trained without cave
  walls. Agent walked through terrain because walls didn't exist in
  training. Fixed by copying fightcaves.collision to pufferlib_4/assets/.

Reward shaping changes:
- w_food_used: -0.01 → -0.5. Waste scaling (overheal fraction × 9)
  now produces meaningful punishment. Eating at 1 HP missing = -4.8.
  Eating at 20+ HP missing = -0.5 (acceptable base cost).
- w_food_used_well: NEW, +1.0. Positive reward for eating when 20+ HP
  missing (200+ tenths). Net reward for well-timed eat = +0.5.
  Agent learns to eat when actually hurt, not for chip damage.
- w_prayer_pot_used: -0.01 → -1.0. Same waste scaling logic. Drinking
  at 1 prayer missing = -9.5. Drinking at 17+ missing = -1.0.
- w_damage_taken: -0.75 (unchanged). Quadratic scaling kept — agent must
  learn that big hits are catastrophic. This is damage taken, not HP
  missing — directly punishes taking hits from hard-hitting NPCs.
- w_correct_danger_prayer: 0.0 → 2.0. NEW reward feature. Fires per-hit
  when correct prayer blocks a ranged/magic attack from ANY NPC.
- w_wrong_danger_prayer: 0.0 → -2.0. NEW reward feature. Fires per-hit
  when wrong/no prayer active and hit by ranged/magic. Symmetrical with
  correct prayer (+2/-2). Agent needs this signal to learn prayer switching.

Backend changes:
- Added correct_danger_prayer / wrong_danger_prayer flags to FcState
- Tracking in fc_combat.c: fires when ranged/magic hit resolves, checks
  if active prayer matches attack style. Per-hit, not per-tick.
- FC_RWD_CORRECT_DANGER_PRAY (16), FC_RWD_WRONG_DANGER_PRAY (17)
- FC_REWARD_FEATURES: 16 → 18
- w_food_used_well field added to FightCaves struct + binding.c

```ini
[base]
env_name = fight_caves

[env]
w_damage_dealt = 0.5
w_damage_taken = -0.75
w_npc_kill = 3.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.5
w_food_used_well = 1.0
w_prayer_pot_used = -1.0
w_correct_jad_prayer = 0.0
w_wrong_jad_prayer = 0.0
w_correct_danger_prayer = 2.0
w_wrong_danger_prayer = -2.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = 0.0
w_tick_penalty = -0.001
curriculum_wave = 28
curriculum_pct = 0.5

[vec]
total_agents = 4096
num_buffers = 2

[train]
total_timesteps = 2_500_000_000
learning_rate = 0.001
gamma = 0.999
gae_lambda = 0.95
clip_coef = 0.2
vf_coef = 0.5
ent_coef = 0.02
max_grad_norm = 0.5
horizon = 256
minibatch_size = 4096

[policy]
hidden_size = 256
num_layers = 2
```

Results (2.5B steps, ~21min, wandb run er56r0lu):
- SPS: 2.0M (down from v6's 2.3M — collision-enabled pathfinding overhead)
- Wave reached: 29.96 avg (same wave 30 wall as v6)
- Episode length: 5438 ticks (down from v6.1's 200K — agent dying, not stalling)
- Episode return: 3421 (up from v6's 37.7 — new rewards providing dense signal)
- Score: 0 (no cave completions)
- Epochs: 2384
- Entropy: 8.3 → 5.3 (converging, possibly too fast)
- Clipfrac: 0.061 (healthy — policy updating)
- Value loss: 31.7 (very high — reward variance too large for value network)
- Policy loss: -0.028
- KL: 0.005

Key observations:
- Agent no longer stalls indefinitely. Episode length 5438 means it engages
  and dies, unlike v6.1's 200K-tick survival strategy. The consumable waste
  penalties are working — agent isn't chugging pots/food mindlessly.
- Collision is loaded and working for the first time. No more open arena.
- Wave 30 wall persists. Reaches ~29.8 within first 200M steps, then flat.
  Ket-Zek (magic+melee, size 5) still the blocker. Agent may not be
  learning to prayer switch despite the new prayer rewards.
- Value loss 31.7 is concerning — means reward signal has high variance.
  The value network can't predict expected returns, which hurts GAE
  advantage estimation and slows learning.
- Entropy dropped to 5.3 from 8.3 — agent may be converging prematurely.

Next: replay v7 policy to observe prayer switching behavior, consumable
usage, and movement with real collision. Compare to v6 replay.

---

## v6.1 (2026-04-04) — Tick cap experiment (archived)

Same config as v6 except FC_MAX_EPISODE_TICKS: 30000 → 200000.
See v6.1 section below for full results.

---

## v6 (2026-04-04) (archived)

Changes from v5:

Observation improvements (agent gets better info to make decisions):
- Player slot 19: hit_style_this_tick — WHAT attack style hit the agent
  this tick (0=none, 0.33=melee, 0.67=ranged, 1.0=magic). Previously
  the agent only knew damage amount, not type. Now it can directly
  correlate "hit by magic → should pray magic."
- Player slot 20: attack_target — which NPC slot the agent is currently
  targeting (0=none, 0.125-1.0=slot 0-7). Agent can now track its own
  target for decision-making about switching targets.
- NPC slot 12: LOS — line of sight from player to NPC (1=clear, 0=blocked).
  Critical for safespotting strategies. Agent can learn to break LOS
  behind terrain to avoid ranged/magic attacks.
- FC_OBS_PLAYER_SIZE: 20 → 21. NPC_STRIDE: 12 → 13.
  FC_POLICY_OBS_SIZE: 126 → 135. FC_PUFFER_OBS_SIZE: 162 → 171.

Reward improvements:
- Damage taken penalty now scales quadratically — big hits hurt
  disproportionately more than small ones:
    1 HP: penalty ≈ -0.0002 (barely noticeable, chip damage is ok)
    10 HP: penalty ≈ -1.1 (moderate, should eat soon)
    20 HP: penalty ≈ -4.3 (harsh, should have been praying)
    50 HP: penalty ≈ -26.8 (catastrophic, Jad without prayer)
  This teaches the agent that praying against big hits matters far
  more than avoiding small melee pokes.
- All explicit prayer rewards remain at 0 (organic learning).

Curriculum learning:
- 50% of episodes start at wave 28 (where agent was dying in v5).
  Gives the agent more practice on Ket-Zek+ waves instead of
  spending most training time on easy waves 1-20.
- Remaining 50% start wave 1 to maintain full-game learning.
- Player restored to full HP/prayer at curriculum start wave.
- New config: curriculum_wave=28, curriculum_pct=0.5.

```ini
[base]
env_name = fight_caves

[env]
w_damage_dealt = 0.5
w_damage_taken = -0.75
w_npc_kill = 3.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.01
w_prayer_pot_used = -0.01
w_correct_jad_prayer = 0.0
w_wrong_jad_prayer = 0.0
w_correct_danger_prayer = 0.0
w_wrong_danger_prayer = 0.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = 0.0
w_tick_penalty = -0.001
curriculum_wave = 28
curriculum_pct = 0.5

[vec]
total_agents = 4096
num_buffers = 2

[train]
total_timesteps = 5_000_000_000
learning_rate = 0.001
gamma = 0.999
gae_lambda = 0.95
clip_coef = 0.2
vf_coef = 0.5
ent_coef = 0.02
max_grad_norm = 0.5
horizon = 256
minibatch_size = 4096

[policy]
hidden_size = 256
num_layers = 2
```

Results (2.5B/5B steps, stopped early — wave 30 wall, wandb run cz6dyc3p):
- SPS: 2.3M
- Wave reached: 30.0 avg (same wall as v5, reached immediately)
- Episode length: 30001 ticks (hit tick cap — agent surviving but stuck)
- Episode return: 37.7 (dropped massively from v5's 636 — quadratic penalty)
- Score: 0 (no cave completions)
- Params: 446.5K (slightly more from new obs features)
- Epochs: 2374
- Entropy: 7.15 (higher than v5's 6.57 — more exploration)
- Clipfrac: 0.051 (finally non-zero! policy actually updating)
- Value loss: 0.001 (very stable)
- Policy loss: -0.008
- KL: 0.005
- Old KL: 0.006
- Uptime: 18m 42s

Diagnosis: New observations (hit style, LOS, target) and curriculum at
wave 28 didn't break the wave 30 wall. Agent reaches wave 30 very quickly
(within first few epochs) but can't progress further. Episode length hit
30001 (tick cap) meaning agent survives indefinitely but can't clear the
wave — possibly stuck idle or praying without attacking. Episode return
dropped from 636→38 due to quadratic damage penalty and tick penalty
accumulating over 30K ticks. Clipfrac finally non-zero (0.051) which is
healthy — policy is actually updating now unlike v1-v5.

Key insight: episode_length = 30001 = FC_MAX_EPISODE_TICKS + 1. The agent
is hitting the tick cap, meaning it's surviving but not clearing waves.
It may have learned to just stand still and pray, avoiding damage but not
fighting. Need to visually observe agent behavior.

Notable from wandb config dump:
- Network: MinGRU (PufferLib default, not plain MLP — has recurrent state)
- Encoder/Decoder: DefaultEncoder/DefaultDecoder
- anneal_lr: 1 (LR annealing is ON — LR decreases over training)
- replay_ratio: 1.0, vtrace enabled (V-trace importance sampling active)
- cudagraphs: 10 (CUDA graph optimization enabled)
- eval_episodes: 10000 (large eval batch)
- checkpoint_interval: 200 epochs

Interesting observations:
- MinGRU gives the agent memory across ticks — it can remember what
  happened several ticks ago (e.g., "Ket-Zek attacked with magic 3 ticks
  ago, I should still be praying magic"). This is better than a stateless
  MLP for prayer switching.
- anneal_lr=1 means the LR of 0.001 decreases toward 0 over the 5B steps.
  By 2.5B steps it's already at 0.0005. This could explain why the agent
  stops improving — LR becomes too small. Consider setting anneal_lr=0.
- Episode length hitting 30001 (tick cap) is suspicious. With curriculum
  starting 50% of episodes at wave 28, the agent may have learned to just
  survive at wave 28-30 without progressing (no deaths = no tick penalty
  accumulation worth worrying about at -0.001/tick).

Next steps: implement policy replay in demo-env viewer to visually observe
what the agent is actually doing at wave 30.

---

## v6.1 (2026-04-04) — Tick cap experiment

Same config as v6 except:
- FC_MAX_EPISODE_TICKS: 30000 → 200000 (force prayer pot drain)
- total_timesteps: 5B → 2B (quick test)
- Hypothesis: agent is hiding behind prayer, hitting 30K tick cap.
  200K ticks should drain all 32 prayer pot doses and expose whether
  the agent actually learned to fight.

Results (2.0B steps, ~14m38s, wandb run ss966rf9):
- SPS: 2.3M
- Wave reached: 30.0 avg (still wave 30 wall — unchanged)
- Episode length: 199,939 ticks (HITTING 200K CAP! agent still not dying!)
- Episode return: -135.1 (NEGATIVE — tick penalty accumulated over 200K ticks)
- Score: 0 (no cave completions)
- Episodes completed: 4,239 (far fewer eps — each one is 6.7x longer)
- Entropy: 6.90
- Clipfrac: 0.001 (barely updating)
- Value loss: 0.0002 (extremely stable — agent found a fixed strategy)
- Policy loss: -0.006
- KL: 0.0007
- VRAM: 3.1/15G (higher than v6's 1.9G — longer episodes need more memory)

KEY FINDINGS:
1. Agent survives 200K ticks (33+ hours of game time) without dying.
   Even after all prayer pots are drained, it still doesn't die. This means
   the agent learned something beyond just "pray and idle":
   - It may be safespotting (breaking LOS behind terrain so NPCs can't attack)
   - It may be kiting (running away from melee NPCs, staying out of range)
   - It may be exploiting a game mechanic (NPC deaggro? stuck pathfinding?)
   
2. Episode return is NEGATIVE (-135) because tick_penalty × 200K ticks = -200.
   The agent's reward from damage/kills doesn't outweigh the tick penalty over
   such a long episode. This is actually a problem — the agent learned that
   surviving (even at negative reward) is better than dying quickly.

3. Wave 30 is the hard ceiling. Even with infinite time, the agent can't
   clear it. This isn't a resource issue — it's a strategy/capability issue.

4. Value loss near 0 (0.0002) means the agent has perfectly predicted its
   expected return — it knows it will survive indefinitely at wave 30 with
   a slightly negative reward. It has fully converged to this strategy.

5. Compared to v6 (episode_length 30001 → 199939): the agent was already
   surviving indefinitely in v6, we just couldn't see it because of the cap.
   The 30K cap was hiding the true behavior.

IMPLICATIONS FOR v7:
- The agent has found a degenerate strategy (survive forever, never clear).
  The current reward structure actually rewards this: dying is -20, but
  surviving indefinitely at wave 30 only costs -0.001/tick = -200 over 200K
  ticks, which is much better than dying (-20 death + losing all wave rewards).
- MUST fix the reward to make clearing waves more valuable than surviving.
  Options:
    a. Increase tick penalty significantly (but risks making agent rush/die)
    b. Add a "wave stall" penalty — if wave doesn't advance for N ticks,
       heavy penalty. This directly targets the degenerate strategy.
    c. Cap episodes at death or wave clear — no tick cap at all. If the agent
       doesn't clear the wave within some timeout, it's a death equivalent.
    d. Make death less punishing (-5 instead of -20) so the agent is less
       afraid to try aggressive strategies that might fail.
- The agent MUST be visually observed to confirm what it's actually doing.
  If it's safespotting, that's actually impressive and we should build on it.
  If it's exploiting a bug, we need to fix the game logic.

---

## v5 (2026-04-03)

Results (5B steps, ~37min, wandb run plpi9rgf):
- SPS: 2.3M (restored from v3's 1.5M)
- Wave reached: 30.0 avg (up from v2's 28.2)
- Episode length: 5056 ticks (up from v2's 3101 — surviving 63% longer)
- Episode return: 636.5
- Score: 0 (no cave completions)
- Episodes completed: 10,006
- Entropy: 6.57
- Clipfrac: 0.000005 (basically zero — still barely updating)
- Value loss: 0.014 (very stable)
- Policy loss: 0.002
- KL: 0.0001

Diagnosis: Removing explicit prayer rewards improved wave 28→30.
Agent learned to use prayer organically through damage_taken signal.
Episode length nearly doubled — agent surviving much longer. But still
stuck at wave 30. Clipfrac ~0 means policy converged to local optimum.
Agent doesn't know WHAT style hit it (only that damage occurred), making
prayer switching harder to learn. Added hit_style observation in v6.

Changes from v4:
- Removed ALL explicit prayer rewards and penalties (Jad and non-Jad).
  All prayer weights set to 0. Prayer reward logic commented out.
- Reasoning: the agent should discover prayer's value organically.
  Correct prayer → blocks 100% damage → w_damage_taken never fires.
  Wrong/no prayer → full damage → w_damage_taken penalty (-0.75).
  The damage signal already encodes whether prayer was correct.
  Explicit prayer rewards caused reward inflation (v3) and added
  complexity without improving wave reached (v4 untested due to v3 crash).
- Cleaner reward function: only 11 active weights, no prayer-specific
  code running per tick. Should restore v1/v2 SPS levels (~2.2M).

```ini
[base]
env_name = fight_caves

[env]
w_damage_dealt = 0.5
w_damage_taken = -0.75
w_npc_kill = 3.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.01
w_prayer_pot_used = -0.01
w_correct_jad_prayer = 0.0
w_wrong_jad_prayer = 0.0
w_correct_danger_prayer = 0.0
w_wrong_danger_prayer = 0.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = 0.0
w_tick_penalty = -0.001

[vec]
total_agents = 4096
num_buffers = 2

[train]
total_timesteps = 5_000_000_000
learning_rate = 0.001
gamma = 0.999
gae_lambda = 0.95
clip_coef = 0.2
vf_coef = 0.5
ent_coef = 0.02
max_grad_norm = 0.5
horizon = 256
minibatch_size = 4096

[policy]
hidden_size = 256
num_layers = 2
```

---

## v4 (2026-04-03) — not trained, superseded by v5

Changes from v3:
- CRITICAL FIX: Prayer reward was firing every tick an NPC existed, not
  when hits actually landed. Caused massive reward inflation (561→3682
  episode return) and the agent learned to idle with prayer on.
- Prayer reward now only fires when: (a) player takes damage and had
  wrong prayer, or (b) NPC attacked and correct prayer blocked it.
  Max one prayer reward per tick to prevent spam.
- w_correct_danger_prayer: 2.0 → 0.5 (was too dominant vs other rewards)
- w_wrong_danger_prayer: -3.0 → -1.0 (proportional reduction)
- horizon: 512 → 256 (512 slowed SPS from 2.2M to 1.5M)

```ini
[base]
env_name = fight_caves

[env]
w_damage_dealt = 0.5
w_damage_taken = -0.75
w_npc_kill = 3.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.01
w_prayer_pot_used = -0.01
w_correct_jad_prayer = 5.0
w_wrong_jad_prayer = -10.0
w_correct_danger_prayer = 0.5
w_wrong_danger_prayer = -1.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = 0.0
w_tick_penalty = -0.001

[vec]
total_agents = 4096
num_buffers = 2

[train]
total_timesteps = 5_000_000_000
learning_rate = 0.001
gamma = 0.999
gae_lambda = 0.95
clip_coef = 0.2
vf_coef = 0.5
ent_coef = 0.02
max_grad_norm = 0.5
horizon = 256
minibatch_size = 4096

[policy]
hidden_size = 256
num_layers = 2
```

---

## v3 (2026-04-03)

Results (1.7B/5B steps, stopped early — broken, wandb run crb1jwbv):
- SPS: 1.5M (down from 2.2M — horizon 512 too slow)
- Wave reached: 15.2 avg (CRASHED from v2's 28.2)
- Episode length: 4115 ticks (longer but worse — agent idling)
- Episode return: 3682.5 (massively inflated by prayer reward spam)
- Score: 0 (no cave completions)
- Episodes completed: ~10K
- Entropy: 5.7
- Clipfrac: 0.262 (now clipping — updates too aggressive)
- Value loss: 1.9 (30x higher than v2's 0.06 — unstable)
- Policy loss: 0.018
- KL: 0.019

Diagnosis: Prayer reward fired every tick per NPC, not per hit. Agent
learned to turn prayer on and idle, collecting +4-8 reward/tick from
multiple NPCs. Reward inflation destabilized training completely.
Value network couldn't predict highly variable rewards. Clipfrac spiked
to 0.26 (from 0.00) indicating policy updates too large. Wave reached
dropped from 28→15 — agent forgot how to fight, just stood still praying.

Changes from v2:
- MAJOR: Added general prayer protection reward for ALL NPCs (not just Jad).
  New config fields: w_correct_danger_prayer=2.0, w_wrong_danger_prayer=-3.0
  BUG: Reward fired every tick, not per-hit. Fixed in v4.
- w_damage_taken: -0.5 → -0.75
- w_food_used: -0.05 → -0.01 (waste scaling handles heavy penalty)
- w_prayer_pot_used: -0.05 → -0.01
- w_idle: -0.01 → 0.0
- w_tick_penalty: -0.005 → -0.001
- ent_coef: 0.05 → 0.02
- horizon: 256 → 512 (caused SPS drop, reverted in v4)

```ini
[base]
env_name = fight_caves

[env]
w_damage_dealt = 0.5
w_damage_taken = -0.75
w_npc_kill = 3.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.01
w_prayer_pot_used = -0.01
w_correct_jad_prayer = 5.0
w_wrong_jad_prayer = -10.0
w_correct_danger_prayer = 2.0
w_wrong_danger_prayer = -3.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = 0.0
w_tick_penalty = -0.001

[vec]
total_agents = 4096
num_buffers = 2

[train]
total_timesteps = 5_000_000_000
learning_rate = 0.001
gamma = 0.999
gae_lambda = 0.95
clip_coef = 0.2
vf_coef = 0.5
ent_coef = 0.02
max_grad_norm = 0.5
horizon = 512
minibatch_size = 4096

[policy]
hidden_size = 256
num_layers = 2
```

---

## v2 (2026-04-03)

Results (2.5B steps, ~19min, wandb run lsw5jibn):
- SPS: 2.2M
- Wave reached: 28.2 avg (barely improved from v1's 27.6)
- Episode length: 3101 ticks
- Episode return: 561.2
- Score: 0 (no cave completions)
- Episodes completed: 10,221
- Entropy: 6.9 (healthier than v1's 5.8)
- Clipfrac: 0.000 (STILL zero — policy not updating meaningfully)
- Value loss: 0.057
- Policy loss: -0.007
- KL: 0.00007

Diagnosis: Agent stuck at wave 28 (Ket-Zek). 5x more training and 3x
higher LR didn't help. Clipfrac still 0 — policy barely changing.
Root cause: no prayer reward for non-Jad NPCs. Agent has zero incentive
to use protection prayers before Jad, so it dies to Ket-Zek magic.
Higher entropy coef (0.05) kept exploration alive (6.9 vs 5.8) but
without the right reward signal, exploration doesn't help.

Changes from v1:
- total_timesteps: 500M → 2.5B
- learning_rate: 0.0003 → 0.001 (clipfrac=0 in v1)
- ent_coef: 0.01 → 0.05 (prevent premature convergence)
- w_correct_jad_prayer: 5.0 → 10.0
- w_wrong_jad_prayer: -10.0 → -20.0
- Food/pot waste penalty: scales by actual overheal amount.
  Shark heals 20 HP — penalty = base × (1 + wasted/heal × 9).
  Eat at 50/70: 0 waste → base only. Eat at 68/70: 90% waste → 10x.

```ini
[base]
env_name = fight_caves

[env]
w_damage_dealt = 0.5
w_damage_taken = -0.5
w_npc_kill = 3.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.05
w_prayer_pot_used = -0.05
w_correct_jad_prayer = 10.0
w_wrong_jad_prayer = -20.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = -0.01
w_tick_penalty = -0.005

[vec]
total_agents = 4096
num_buffers = 2

[train]
total_timesteps = 2_500_000_000
learning_rate = 0.001
gamma = 0.999
gae_lambda = 0.95
clip_coef = 0.2
vf_coef = 0.5
ent_coef = 0.05
max_grad_norm = 0.5
horizon = 256
minibatch_size = 4096

[policy]
hidden_size = 256
num_layers = 2
```

---

## v1 (2026-04-03) — First training run

Results (500M steps, ~3m43s, wandb run xgsb170g):
- SPS: 2.2M
- Wave reached: 27.6 avg
- Episode length: 3110 ticks avg
- Episode return: 554.3
- Score (cave completions): 0
- Episodes completed: 10,174
- Entropy: 8.3 → 5.8
- Clipfrac: 0.000
- Value loss: 0.057
- Policy loss: -0.007
- KL: 0.00007

Observations:
- Agent plateaus around wave 27-28 (Ket-Zek appears with magic+melee)
- Never reached Jad (wave 63)
- Clipfrac=0 means learning rate too conservative
- Entropy dropped ~30%, agent specializing but possibly too early
- Strong early learning (wave 1→27 quickly) but no late-game progress

```ini
[base]
env_name = fight_caves

[env]
w_damage_dealt = 0.5
w_damage_taken = -0.5
w_npc_kill = 3.0
w_wave_clear = 10.0
w_jad_damage = 2.0
w_jad_kill = 50.0
w_player_death = -20.0
w_cave_complete = 100.0
w_food_used = -0.05
w_prayer_pot_used = -0.05
w_correct_jad_prayer = 5.0
w_wrong_jad_prayer = -10.0
w_invalid_action = -0.1
w_movement = 0.0
w_idle = -0.01
w_tick_penalty = -0.005

[vec]
total_agents = 4096
num_buffers = 2

[train]
total_timesteps = 500_000_000
learning_rate = 0.0003
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
