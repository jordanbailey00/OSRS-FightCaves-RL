# RL Configuration History

Tracks every training config change with results and reasoning.
Current config is at the top. Older runs below.

---

## CURRENT: v3 (2026-04-03)

Changes from v2:
- MAJOR: Added general prayer protection reward for ALL NPCs (not just Jad).
  Agent now gets rewarded for correct prayer vs Ket-Zek magic, Tok-Xil ranged,
  etc. This is the key fix — agent had zero incentive to pray before wave 63.
  New config fields: w_correct_danger_prayer=2.0, w_wrong_danger_prayer=-3.0
- w_damage_taken: -0.5 → -0.75 (slightly harsher to encourage prayer use)
- w_food_used: -0.05 → -0.01 (waste scaling handles heavy penalty already)
- w_prayer_pot_used: -0.05 → -0.01 (same reasoning)
- w_idle: -0.01 → 0.0 (don't punish idle, agent should choose when to move)
- w_tick_penalty: -0.005 → -0.001 (less time pressure, let agent be strategic)
- ent_coef: 0.05 → 0.02 (v2 entropy was fine at 6.9, don't over-explore)
- horizon: 256 → 512 (longer rollouts for strategic planning across waves)

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

Results (2.5B steps, ~19min):
- SPS: 2.2M
- Wave reached: 28.2 avg (barely improved from v1's 27.6)
- Episode length: 3101 ticks
- Episode return: 561.2
- Score: 0 (no cave completions)
- Entropy: 6.9 (healthier than v1's 5.8)
- Clipfrac: 0.000 (STILL zero — policy not updating meaningfully)

Diagnosis: Agent stuck at wave 28 (Ket-Zek). 5x more training didn't help.
Root cause: no prayer reward for non-Jad NPCs. Agent has zero incentive
to use protection prayers before Jad, so it dies to Ket-Zek magic.

Changes from v1:
- total_timesteps: 500M → 2.5B (need more training to reach Jad)
- learning_rate: 0.0003 → 0.001 (clipfrac=0 in v1, policy updates too conservative)
- ent_coef: 0.01 → 0.05 (entropy dropped from 8.3→5.8 in v1, keep exploration alive)
- w_correct_jad_prayer: 5.0 → 10.0 (stronger signal for correct prayer switching)
- w_wrong_jad_prayer: -10.0 → -20.0 (harsher penalty for wrong prayer)
- Food/pot waste penalty: scales by actual overheal, not HP percentage.
  Shark heals 20 HP — penalty = base × (wasted HP / 20 heal).
  Prayer pot restores 17 pts — penalty = base × (wasted pts / 17 restore).
  Examples:
    Eat at 50/70 HP: heals full 20, 0 waste → base penalty (-0.05)
    Eat at 60/70 HP: heals 10, wastes 10 → -0.05 × 5.5 = -0.275
    Eat at 68/70 HP: heals 2, wastes 18 → -0.05 × 9.1 = -0.455
    Drink at 30/43 pray: restores 13, 0 waste → base penalty (-0.05)
    Drink at 40/43 pray: restores 3, wastes 14 → heavy penalty
  Prevents agent from wasting resources on minor damage/drain.

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
