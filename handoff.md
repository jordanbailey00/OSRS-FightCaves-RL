# Codex3 Handoff

This file is the single handoff document for a fresh Codex session.
Start the new session in:

`/home/joe/projects/runescape-rl/codex3`

## Repo State

- Repo root: `/home/joe/projects/runescape-rl/codex3`
- Branch: `v2codex`
- Last pushed commit: `df07aa8da5e1b800986e84eb19ad248f0f83dff1`
- Remote: `git@github.com:jordanbailey00/runescape-rl.git`
- Branch pushed: `origin/v2codex`

Current local working tree state:
- modified: `changelog.md`
- modified: `runescape-rl/docs/rl_config.md`
- untracked: `handoff.md`

Meaning:
- the `q3ald8bc` analysis and this handoff are updated locally
- those doc updates are not yet included in the last pushed commit

Git note:
- `pufferlib_4/` is intentionally ignored at the root repo level
- `pufferlib_4` is local runtime/build state and is not part of the push

## Read First

These are the only docs a fresh session should treat as primary:

1. [runescape-rl/docs/rl_config.md](/home/joe/projects/runescape-rl/codex3/runescape-rl/docs/rl_config.md)
2. [runescape-rl/config/fight_caves.ini](/home/joe/projects/runescape-rl/codex3/runescape-rl/config/fight_caves.ini)
3. [changelog.md](/home/joe/projects/runescape-rl/codex3/changelog.md)
4. [runescape-rl/DESIGN.md](/home/joe/projects/runescape-rl/codex3/runescape-rl/DESIGN.md)

Do not rely on `today_plan.md` or `claude_audit.md` as the main handoff.

## Current Training

No active training run should be assumed from this handoff.

Latest completed run:
- Run id: `q3ald8bc`
- Purpose: `v17.1` control run
- Status: completed
- Launch mode: from scratch, no warm-start, no curriculum

Launch command:

```bash
cd /home/joe/projects/runescape-rl/codex3/runescape-rl && bash train.sh
```

Important:
- Do not set `LOAD_MODEL_PATH` for `v17.1`
- `train.sh` handles:
  - syncing `runescape-rl/config/fight_caves.ini` into `pufferlib_4/config/fight_caves.ini`
  - local output directories
  - local `pufferlib_4` backend build when `_C` is missing
  - local W&B/log/checkpoint paths

Runtime output locations:
- Checkpoints: `/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves`
- Logs: `/home/joe/projects/runescape-rl/codex3/pufferlib_4/logs/fight_caves`
- Wandb: `/home/joe/projects/runescape-rl/codex3/pufferlib_4/wandb`

Current latest completed log file:
- `/home/joe/projects/runescape-rl/codex3/pufferlib_4/logs/fight_caves/q3ald8bc.json`

## Current Live Config

The live config is `v17.1`, not the older `v17` warm-start run.

`v17.1` definition:
- no curriculum
- no warm-start
- `1.5B` steps
- keep the current `v17` obs/reward/PPO package otherwise

Core live values:
- `total_timesteps = 1_500_000_000`
- `learning_rate = 0.00025`
- `clip_coef = 0.12`
- `ent_coef = 0.03`
- `curriculum_wave = 0`
- `curriculum_pct = 0.0`
- `curriculum_wave_2 = 0`
- `curriculum_pct_2 = 0.0`
- `curriculum_wave_3 = 0`
- `curriculum_pct_3 = 0.0`

This config is already mirrored into:
- [runescape-rl/config/fight_caves.ini](/home/joe/projects/runescape-rl/codex3/runescape-rl/config/fight_caves.ini)
- `/home/joe/projects/runescape-rl/codex3/pufferlib_4/config/fight_caves.ini`

## What Changed In Codex3

High-level changes already implemented in `codex3`:

- Isolated local `pufferlib_4` checkout under repo root
- Root repo ignores `pufferlib_4`
- Training/build/eval scripts localized to `codex3`
- Reward-path SPS optimization landed
- Observation contract updated for better threat timing
- `prayer_drain_counter` added to obs
- Prayer flick reward disabled
- Reward shaping moved into config-backed `shape_*` knobs
- Threat-aware food/pot shaping added
- Mixed curriculum support added
- Extra analytics added:
  - per-style prayer uptime
  - average prayer before pot
  - average HP before food
  - Tok-Xil / Ket-Zek melee ticks
  - reached/cleared late-wave milestones

## Key Run History

### v16.2a

Run:
- `ge5sma5y`

Meaning:
- `v16.2-style` bridge run on the current `codex3` backend
- scratch
- no curriculum
- `1.5B` steps

Why it matters:
- This is the strongest clean baseline on current `codex3`
- It reached the wave-29 frontier and is the main comparison point

Key result:
- final wave reached: `28.82`
- max wave: `31`

Best warm-start checkpoint from that run:
- `/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/ge5sma5y/0000001364197376.bin`

### v17

Run:
- `mv0snohb`

Meaning:
- warm-start from `ge5sma5y`
- mixed curriculum restored
- `5B` steps

Main conclusion:
- more stable than old long-run `v16.2`
- did not preserve the `v16.2a` wave-29 frontier

Key result:
- final wave reached: `25.01`
- max wave: `32`

Interpretation:
- likely improved discipline and some threat semantics
- regressed too much on prayer switching / late-wave conversion
- too many simultaneous changes were applied to a warm-started policy

### v17.1

Completed control run:
- `q3ald8bc`

Purpose:
- answer whether the current `v17` package is actually better than
  `v16.2a` on its own
- remove warm-start and curriculum as confounders

Result:
- final wave reached: `17.17`
- max wave: `24`

Conclusion:
- the current `v17` package does not bootstrap from scratch well enough
- it is materially worse than `v16.2a`

## Current Working Hypothesis

The current hypothesis is:
- the observation/threat updates are likely useful
- the current `v17` reward / PPO recipe is too conservative for scratch
  learning and likely overcomplicates resource shaping
- the next run should preserve the new observation contract but move the
  optimization and dense reward recipe back toward `ge5sma5y`

Recommended next run: `v17.2`
- keep current obs improvements
- keep prayer flick reward disabled
- keep capped stall penalty
- revert closer to `ge5sma5y` on the learning recipe:
  - `learning_rate = 0.001`
  - `clip_coef = 0.2`
  - `ent_coef = 0.02`
  - `w_damage_dealt = 1.0`
  - `w_npc_kill = 2.0`
  - `w_correct_danger_prayer = 1.0`
  - disable the extra threat-aware food/pot penalties
- no curriculum
- no warm-start
- `1.5B` steps

## What To Do Next

1. Treat `q3ald8bc` as completed and analyzed
2. Use [runescape-rl/docs/rl_config.md](/home/joe/projects/runescape-rl/codex3/runescape-rl/docs/rl_config.md) as the source of truth for the `v17.1` postmortem
3. Prepare the next run as `v17.2` using the recommendation above
4. Only revisit curriculum or warm-start after the scratch recipe works again

Useful command after completion:

```bash
cd /home/joe/projects/runescape-rl/codex3/runescape-rl
bash analyze_run.sh q3ald8bc
```

## Fresh Session Instruction

If a new Codex session starts, give it this message:

`Continue the codex3 Fight Caves work from /home/joe/projects/runescape-rl/codex3 on branch v2codex. Read handoff.md, runescape-rl/docs/rl_config.md, runescape-rl/config/fight_caves.ini, and changelog.md first. The latest completed run is q3ald8bc, which was the v17.1 control run. It underperformed v16.2a badly. The current recommended next step is a v17.2 scratch run that keeps the new observation contract but reverts the learning and dense reward recipe closer to ge5sma5y.`
