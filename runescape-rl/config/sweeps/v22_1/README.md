# v22.1 Manual Ablation Sweep

This is an 8-run manual 2x4 sweep around the successful `v22.1` branch.

Matrix:
- factor A: initialization
  - warm-start from `u58coupx/0000001311768576.bin`
  - cold-start from scratch
- factor B: reward config
  - control
  - reward retune OFF
  - low-prayer shaping OFF
  - both OFF

Purpose:
- isolate which `v22.1` config deltas matter
- separately test whether those gains depend on warm-start
- keep the corrected TBow model and all non-swept settings fixed

What this 8-run design answers:
- whether the reward retune matters
- whether the low-prayer shaping matters
- whether warm-start itself matters
- whether there is interaction between warm-start and the two reward changes

Suggested sweep budget:
- `3B` steps each
- same warm-start checkpoint for the 4 warm-started runs:
  - `/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/u58coupx/0000001311768576.bin`

Sequential runner:
- `bash /home/joe/projects/runescape-rl/codex3/runescape-rl/config/sweeps/v22_1/run_v22_1_sweep.sh`
- resume from a failed job:
  - `bash /home/joe/projects/runescape-rl/codex3/runescape-rl/config/sweeps/v22_1/run_v22_1_sweep.sh --from cold_no_low_prayer`
- preview commands only:
  - `bash /home/joe/projects/runescape-rl/codex3/runescape-rl/config/sweeps/v22_1/run_v22_1_sweep.sh --dry-run`

Config pairs:
- `fight_caves_v22_1_control.ini`
- `fight_caves_v22_1_control_cold.ini`
- `fight_caves_v22_1_no_reward_retune.ini`
- `fight_caves_v22_1_no_reward_retune_cold.ini`
- `fight_caves_v22_1_no_low_prayer.ini`
- `fight_caves_v22_1_no_low_prayer_cold.ini`
- `fight_caves_v22_1_no_reward_retune_no_low_prayer.ini`
- `fight_caves_v22_1_no_reward_retune_no_low_prayer_cold.ini`

Warm-start run commands:

```bash
cd /home/joe/projects/runescape-rl/codex3/runescape-rl
CONFIG_PATH=/home/joe/projects/runescape-rl/codex3/runescape-rl/config/sweeps/v22_1/fight_caves_v22_1_control.ini \
LOAD_MODEL_PATH=/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/u58coupx/0000001311768576.bin \
FORCE_BACKEND_REBUILD=1 bash train.sh
```

```bash
cd /home/joe/projects/runescape-rl/codex3/runescape-rl
CONFIG_PATH=/home/joe/projects/runescape-rl/codex3/runescape-rl/config/sweeps/v22_1/fight_caves_v22_1_no_reward_retune.ini \
LOAD_MODEL_PATH=/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/u58coupx/0000001311768576.bin \
FORCE_BACKEND_REBUILD=1 bash train.sh
```

```bash
cd /home/joe/projects/runescape-rl/codex3/runescape-rl
CONFIG_PATH=/home/joe/projects/runescape-rl/codex3/runescape-rl/config/sweeps/v22_1/fight_caves_v22_1_no_low_prayer.ini \
LOAD_MODEL_PATH=/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/u58coupx/0000001311768576.bin \
FORCE_BACKEND_REBUILD=1 bash train.sh
```

```bash
cd /home/joe/projects/runescape-rl/codex3/runescape-rl
CONFIG_PATH=/home/joe/projects/runescape-rl/codex3/runescape-rl/config/sweeps/v22_1/fight_caves_v22_1_no_reward_retune_no_low_prayer.ini \
LOAD_MODEL_PATH=/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/u58coupx/0000001311768576.bin \
FORCE_BACKEND_REBUILD=1 bash train.sh
```

Cold-start run commands:

```bash
cd /home/joe/projects/runescape-rl/codex3/runescape-rl
CONFIG_PATH=/home/joe/projects/runescape-rl/codex3/runescape-rl/config/sweeps/v22_1/fight_caves_v22_1_control_cold.ini \
FORCE_BACKEND_REBUILD=1 bash train.sh
```

```bash
cd /home/joe/projects/runescape-rl/codex3/runescape-rl
CONFIG_PATH=/home/joe/projects/runescape-rl/codex3/runescape-rl/config/sweeps/v22_1/fight_caves_v22_1_no_reward_retune_cold.ini \
FORCE_BACKEND_REBUILD=1 bash train.sh
```

```bash
cd /home/joe/projects/runescape-rl/codex3/runescape-rl
CONFIG_PATH=/home/joe/projects/runescape-rl/codex3/runescape-rl/config/sweeps/v22_1/fight_caves_v22_1_no_low_prayer_cold.ini \
FORCE_BACKEND_REBUILD=1 bash train.sh
```

```bash
cd /home/joe/projects/runescape-rl/codex3/runescape-rl
CONFIG_PATH=/home/joe/projects/runescape-rl/codex3/runescape-rl/config/sweeps/v22_1/fight_caves_v22_1_no_reward_retune_no_low_prayer_cold.ini \
FORCE_BACKEND_REBUILD=1 bash train.sh
```
