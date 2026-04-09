# Today Plan — 2026-04-09

This file reflects the current next work. The shared-backend refactor and replay-speed controls are done.

## 1. Revisit Prayer Timing Changes

Primary objective:
- revisit the prayer-lock timing change before deciding whether to keep it
- compare the prayer-only snapshot directly against the pre-prayer baseline

What changed:
- non-Jad incoming hits no longer check live prayer on impact
- non-Jad hits snapshot prayer on the attack tick
- Jad melee snapshots on the attack tick
- Jad ranged / magic snapshots shortly after the tell (`N+1`)
- pending hits carry the locked prayer state used later at resolve time

Where that code lives now:
- active worktree:
  - `/home/joe/projects/runescape-rl/codex3/run_post_prayer_pre_obs`
- saved snapshot:
  - branch: `snapshot/post-prayer-pre-obs-2026-04-08`
  - tag: `post-prayer-pre-obs-2026-04-08`
  - commit: `c232f09b`
- main core files:
  - `fc-core/include/fc_types.h`
  - `fc-core/src/fc_combat.c`
  - `fc-core/src/fc_npc.c`

Questions to resolve:
- keep or revert the prayer timing change?
- if we keep it, is the Jad timing exactly right?
- is `v_tmp2` (`fkhhysfd`) clearly better than `v_tmp3` (`fg029tll`) once we weigh both progress and stability?

## 2. Revisit Obs / Reward Follow-Up Changes

Primary objective:
- revisit the post-prayer obs / reward follow-up that regressed `v_tmp1`
- separate the obs change from the reward-path change instead of bundling them

What changed:
- added `npc_type` back into policy obs
- policy / puffer contract changed from `106 / 142` to `114 / 150`
- resolved-hit prayer rewards started using the locked prayer snapshot instead of live prayer at landing
- debug overlay / docs were updated to match

Where that code lives now:
- saved snapshot:
  - branch: `snapshot/post-obs-post-prayer-2026-04-08`
  - tag: `post-obs-post-prayer-2026-04-08`
  - commit: `58768c5b8fd4bd4a3a16c97a7f8db5c752bfa8ee`
- no active worktree right now
- main files:
  - `fc-core/include/fc_contracts.h`
  - `fc-core/include/fc_types.h`
  - `fc-core/src/fc_state.c`
  - `fc-core/src/fc_tick.c`
  - `fc-core/src/fc_combat.c`
  - `training-env/fight_caves.h`
  - `demo-env/src/fc_debug_overlay.h`

Questions to resolve:
- did `npc_type` obs itself hurt training?
- did the resolved-hit reward-path change hurt training?
- reintroduce changes one at a time, not together

## 3. Training-Env Performance Refactor

Primary objective:
- refactor `training-env` specifically for performance improvements
- use gprof
- use bash build.sh ENV_NAME --profile

## 4. RL Tuning Follow-Up After Refactor

Baseline to tune from:
- `v19` through `v19.3`
- treat the current `v19` line as the active continuation baseline
- select the best continuation checkpoint from `v19` through `v19.3` before the next run

Primary recommendation:
- run a late-wave fine-tune from the best `v19` / `v19.3` checkpoint rather than redesigning the obs/reward stack again

Recommended first config to try:
- `total_timesteps = 1_000_000_000`
- `learning_rate = 0.0003`
- `clip_coef = 0.15`
- `ent_coef = 0.01`
- `checkpoint_interval = 50`
- `curriculum_wave = 30`
- `curriculum_pct = 0.05`
- `curriculum_wave_2 = 31`
- `curriculum_pct_2 = 0.02`
- `curriculum_wave_3 = 0`
- `curriculum_pct_3 = 0.0`
- keep the rest of the current `v19.3` reward structure unchanged

Reason:
- the current `v19` / `v19.3` line already proved the current backend/obs can learn
- the next problem is breaking past the late-wave plateau, not rebuilding early competence

Secondary control if needed:
- rerun the exact `v19.3` recipe longer, around `2.5B` total steps, with no curriculum changes

Only-if-needed follow-up:
- if late Jad switching still looks like the real bottleneck after fine-tuning, try a very small Jad-specific reward adjustment rather than another broad reward rewrite

## 5. Guardrails

Do not do these tomorrow unless a new finding forces it:
- do not prune or expand observations again
- do not reintroduce Jad telegraph obs
- do not bring back the heavy `v17` resource penalties
- do not mix a large PPO change, large reward change, and curriculum change in one experiment
- do not treat the final checkpoint as automatically best; evaluate the best `v19` / `v19.3` plateau checkpoint first
