# RL Configuration History

Tracks every training config change with results and reasoning.
Current config is at the top. Older runs below.

---

## CURRENT: v6 (2026-04-04)

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

Results (2.5B/5B steps, stopped early — wave 30 wall):
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

Next steps: implement policy replay in demo-env viewer to visually observe
what the agent is actually doing at wave 30.

---

## v5 (2026-04-03)

Results (5B steps, ~37min):
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

Results (1.7B/5B steps, stopped early — broken):
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

Results (2.5B steps, ~19min):
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

Results (500M steps, ~3m43s):
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
