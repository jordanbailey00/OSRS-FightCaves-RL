# RL Configuration History

Tracks every training config change with results and reasoning.
Current config is at the top. Older runs below.

---

## CURRENT: v2 (2026-04-03)

Changes from v1:
- total_timesteps: 500M → 2.5B (need more training to reach Jad)
- learning_rate: 0.0003 → 0.001 (clipfrac=0 in v1, policy updates too conservative)
- ent_coef: 0.01 → 0.05 (entropy dropped from 8.3→5.8 in v1, keep exploration alive)
- w_correct_jad_prayer: 5.0 → 10.0 (stronger signal for correct prayer switching)
- w_wrong_jad_prayer: -10.0 → -20.0 (harsher penalty for wrong prayer)

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

Results (500M steps, ~3m43s):
- SPS: 2.2M
- Wave reached: 27.6 avg
- Episode length: 3110 ticks avg
- Episode return: 554.3
- Score (cave completions): 0
- Episodes completed: 10,174
- Entropy: 8.3 → 5.8
- Clipfrac: 0.000

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
