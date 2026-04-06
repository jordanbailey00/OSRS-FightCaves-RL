================================================================================
DAILY PLAN — 2026-04-04
================================================================================

STATUS: v6.1 agent survives 200K ticks but never dies and never clears waves.
The agent learned that survival (at -0.001/tick) dominates dying (at -20).
Today's focus: build observability tooling, then diagnose and fix the reward
structure so the agent actually fights.

--------------------------------------------------------------------------------
0. DESIGN.md — Architecture & Lessons-Learned Document
--------------------------------------------------------------------------------

Write a comprehensive DESIGN.md at the project root covering:

  A. Current Architecture (technical spec):
     - Frontend: Raylib debug viewer (viewer.c), how it consumes FcState/
       FcRenderEntity, camera system, UI panels, debug overlays
     - Backend: fc_*.c/h simulation (tick loop, combat, NPC AI, pathfinding,
       prayer, wave system, observation/reward extraction)
     - Training: PufferLib 4.0 integration (fight_caves.h wrapper, binding.c,
       build.sh, vecenv.h interface)
     - Cache pipeline: b237 OSRS cache → Python export scripts → binary assets
       (terrain, objects, atlas, NPC models) → C loader headers
     - File-by-file breakdown with purpose, key functions, data flow

  B. How We Got Here (decisions, references, lessons):
     - What worked: C single-backend architecture, Raylib for viewer, PufferLib
       4.0 ocean pattern, binary asset pipeline, Void RSPS as porting reference
     - What didn't work: Kotlin/JVM approach (too slow for RL throughput),
       runtime cache decoding in C (too complex — preprocess in Python instead),
       early collision map issues, NPC size/pathfinding bugs
     - References used: Void RSPS (Kotlin), RSMod rev 233, b237 OSRS cache from
       openrs2, RuneLite loaders, PufferLib 4.0 ocean examples (snake, convert,
       template)
     - Dependencies: Raylib 5.5, PufferLib 4.0, PyTorch, wandb, Python 3.11+

  C. Reproduction Guide:
     - How to set up the same architecture for a different boss/zone
     - Cache extraction and asset export pipeline steps
     - Backend porting workflow (RSPS reference → fc_*.c)
     - PufferLib integration checklist

  This is a reference document, not a living plan. Update only on major changes
  or significant new lessons. Be explicit — reference exact file names, function
  names, variable names, struct fields. No filler.

--------------------------------------------------------------------------------
1. Policy Replay in Debug Viewer
--------------------------------------------------------------------------------

Goal: Watch the trained v6.1 policy play in the full debug viewer with all
overlays, so we can diagnose what the agent is actually doing.

  PRELIMINARY: Audit PufferLib 4.0 for built-in replay/eval support.
  The creator has stated replay is part of the framework's functionality, but
  it's unclear what exactly our side needs to provide. Do a focused audit of:
    - pufferlib_4/pufferlib/pufferl.py eval path
    - pufferlib_4/src/vecenv.h c_render() hook
    - Any existing ocean env that implements visual eval
  Determine what work is actually needed vs what PufferLib handles natively.

  VIEWER CHANGES — Add --policy-pipe mode to viewer.c (~30 lines):
    - When flag is set, viewer reads 5 action ints from stdin each tick
    - Viewer writes observation floats (policy obs + action mask) to stdout
      each tick
    - Replaces build_human_actions() with piped actions
    - All existing rendering, overlays, event log, camera controls unchanged
    - Human can still pause (Space), toggle debug (O), inspect state

  EVAL SCRIPT — Write eval_viewer.py:
    - Loads PufferLib checkpoint (.bin), reconstructs policy network
    - Launches viewer subprocess: ./fc_viewer --policy-pipe
    - Each tick: read obs from viewer stdout → policy forward pass → write
      5 action ints to viewer stdin
    - Apply action mask before sampling
    - Usage: python eval_viewer.py --checkpoint <path_to_.bin>

  This is purely a viewing/piping layer — no backend changes, no fc_*.c
  modifications.

  DEPENDENCY: All subsequent items (2-5) require this to be working first.

--------------------------------------------------------------------------------
2. Observe Agent Behavior at Wave 30
--------------------------------------------------------------------------------

With policy replay working, watch the v6.1 agent play with debug overlays
(O key). Identify what the agent is doing (or not doing):

  - Is it safespotting? (LOS overlay will show)
  - Is it praying? (prayer icon overhead, prayer panel)
  - Is it attacking? (event log will show combat rolls)
  - Is it moving? (path overlay, position changes)
  - Is it eating/drinking? (inventory tab, consumable counts)
  - Are NPCs stuck? (possible pathfinding bug if NPCs don't reach player)
  - Is it just idling? (no actions, standing still)

Document findings before making any changes.

--------------------------------------------------------------------------------
3. Diagnose and Fix Based on Observations
--------------------------------------------------------------------------------

Depending on what we observe in step 2:

  - If safespotting indefinitely: impressive emergent behavior, but need to
    incentivize actually killing NPCs (wave stall penalty)
  - If idle with prayer on: reward structure lets the agent stall forever —
    fix the tick/idle penalty balance
  - If NPCs stuck behind walls: game logic bug, fix pathfinding or spawn
    positions
  - If agent can't figure out prayer switching on Jad: may need observation
    changes (clearer telegraph signal) or curriculum (start at wave 62)
  - If agent attacks but dies immediately: reward may be too punishing for
    taking damage, making it overly passive in earlier training

--------------------------------------------------------------------------------
4. Fix Reward Structure for v7
--------------------------------------------------------------------------------

The core problem: surviving at -0.001/tick is a better strategy than risking
death at -20. The agent maximizes reward by doing nothing.

Options to fix:

  A. Wave stall penalty: if wave doesn't advance within N ticks, apply a heavy
     per-tick penalty (e.g., -0.1/tick after 500 ticks on same wave). Forces
     the agent to engage.

  B. Reduce death penalty: -5 instead of -20 so the agent takes more risks.
     Combined with positive kill/wave rewards, fighting becomes dominant.

  C. Per-wave timeout: instead of a global tick cap, each wave gets a timeout
     (e.g., 500 ticks). Failing to clear the wave = terminal. Directly
     punishes stalling without touching the reward function.

  D. Revert tick cap to 30K: once rewards incentivize fighting, the agent
     shouldn't survive 200K ticks doing nothing. Shorter episodes = faster
     training iterations.

  Likely approach: combine A + B + D. Wave stall penalty forces engagement,
  lower death penalty reduces passivity, shorter tick cap speeds training.

--------------------------------------------------------------------------------
5. Train v7 and Iterate
--------------------------------------------------------------------------------

Run training with the fixed reward structure. After ~500M steps:

  - Use policy replay to observe v7 agent behavior
  - Check wandb metrics: wave_reached distribution, episode_length, reward
  - If agent pushes past wave 30: success, continue training
  - If new degenerate behavior emerges: diagnose with step 2-3, fix, retrain
  - Tune reward weights based on observed behavior

Goal: agent that actively fights, progresses through waves, and learns
prayer switching on Jad.

--------------------------------------------------------------------------------
6. V17 Run Recommendation — 2026-04-06
--------------------------------------------------------------------------------

Reviewed for this recommendation:
  - runescape-rl/docs/rl_config.md (full run history through v16.2)
  - runescape-rl/config/fight_caves.ini (live config)
  - runescape-rl/training-env/* and src/* (live reward/obs/mask/combat code)
  - pufferlib_4/ocean reference envs, especially g2048 scaffolding curriculum

Performance constraint for all v17 implementation work:

  Current throughput is roughly 1.8M-2.3M SPS. Do not trade that away for
  denser observation logic or more expensive shaping. Prefer constant-time or
  re-used per-tick calculations, avoid extra full-NPC scans when existing loops
  can be extended, and flag any change that looks likely to reduce SPS before
  merging it into the training path.

  Current perf watchlist:
    - reward computation still calls fc_write_obs() again inside
      fight_caves.h::fc_puffer_compute_reward(), and c_step() then calls
      fc_puffer_write_obs() afterward. That likely means duplicate obs-writing
      work on every step. Do not change this casually, but revisit it as a
      possible SPS win once v17 behavior changes are stable.

Core conclusion:

  The main blocker is probably NOT "we need even more prayer reward."
  The bigger blockers look like:

  1. The policy still gets a misleading threat representation for dual-mode NPCs.
     Tok-Xil and Ket-Zek expose their PRIMARY style in obs
     (FC_NPC_ATK_STYLE in fc_state.c), but in live combat they switch to
     melee at distance 1 (npc_generic_attack in fc_npc.c). This means the
     agent must infer a hidden "style changes at melee range" rule from
     distance + NPC type + delayed consequences. That is learnable, but much
     harder than it needs to be.

  2. v16.1 reward design was basically clean and productive. v16.2 showed the
     agent CAN reach wave ~29 with those signals, but the optimizer kept
     updating too aggressively after the breakthrough and destroyed the policy.

  3. Resource overconsumption is probably downstream of weak threat
     classification. If the agent cannot cleanly predict which prayer is needed
     and when, it will over-pray, over-drink, and panic-eat.

  4. The current env still hides several important shaping knobs in hardcoded
     logic inside fight_caves.h, which slows tuning and makes reward iteration
     noisy.

V17 recommendation, in priority order:

  A. Highest-priority code changes before the run

    1. Replace the current per-NPC primary attack-style observation with an
       "effective style now" signal.
       Recommendation:
         - remove the misleading primary-style cue for dual-mode NPCs
         - expose the style the NPC would use RIGHT NOW at current distance/LOS
         - keep distance in obs so the policy can still learn the range rule
       For Tok-Xil and Ket-Zek this should flip to melee at dist<=1.
       This is cleaner than exposing a static primary style that is often wrong
       at the moment the threat actually matters.

    2. Keep attack timing explicit.
       Each visible NPC should expose:
         - effective_style_now
         - attack_timer
         - pending_style
         - pending_ticks
       This gives the policy both "what this NPC wants to do next" and
       "what projectile/hit is already committed and landing soon."

    3. Add player-level incoming-hit timeline summaries instead of absolute
       episode tick numbers.
       Recommendation: add compact counters/flags for incoming hits by style
       over the next few ticks, e.g.:
         - incoming_melee_in_1t / 2t / 3t
         - incoming_ranged_in_1t / 2t / 3t
         - incoming_magic_in_1t / 2t / 3t
         - optional same_tick_style_conflict flag
       Do NOT expose absolute "lands on tick 436" episode clocks. Relative
       countdowns generalize better and are exactly what the policy needs for
       proactive prayer.

    4. Move the hardcoded shaping constants in fight_caves.h into config.
       Especially:
         - kiting reward
         - unnecessary prayer penalty
         - NPC melee-range penalty
         - NPC-specific prayer bonus
         - wasted attack penalty
         - wave stall penalty start/base/ramp/cap
         - prayer flick reward
         - full-waste food/pot penalties
       For v17, tuning these from ini is much more valuable than adding more
       one-off hardcoded logic.

    5. Mask redundant prayer no-ops.
       Right now all prayer actions are always valid. I would mask:
         - prayer off when already off
         - selecting the already-active prayer
       This shrinks the prayer action space and removes useless exploration.

    6. Improve analytics before the run.
       Add explicit logs for:
         - correct_blocks_total
         - wrong_prayer_hits
         - no_prayer_hits
         - melee/ranged/magic prayer uptime
         - average prayer level when pot is used
         - average HP when food is used
         - Tok-Xil/Ket-Zek time spent at melee distance
         - reached_wave_30 / cleared_wave_30 / reached_wave_31
       The current logs are helpful, but not enough to isolate the prayer and
       resource problem quickly.

  B. Reward changes I would make for v17

    1. Keep the v16.1 core shaping philosophy.
       That is the first clean setup that showed a late real breakthrough.
       Do NOT go back to large dense prayer rewards or large food/pot bonuses.

    2. Remove or disable the prayer flick reward for v17 unless we also expose
       prayer_drain_counter in observation.
       Right now the env rewards a timing-sensitive mechanic the policy does not
       directly observe. For "get a fire cape," correct prayer timing matters
       more than advanced flick optimization.

    3. Keep kiting reward and unnecessary-prayer penalty.
       v15.3 is still one of the strongest signals in the history. It is the
       first run where the agent clearly experienced non-melee threat patterns.

    4. Cap the escalating wave stall penalty.
       v16.2 analysis strongly suggests the uncapped stall penalty amplified the
       collapse once the policy regressed. I would keep stall punishment, but
       cap it. Example:
         - start after 500 ticks
         - base -0.75/tick
         - ramp every 50 ticks
         - hard cap total stall penalty at -3.0 or -4.0 per tick
       The penalty should force engagement, not create unrecoverable gradients.

    5. Make resource waste more threat-aware, not more positive.
       I would NOT reintroduce big bonuses for eating/drinking.
       I WOULD add extra penalties when:
         - food is used with no pending hit and HP is still high
         - prayer pot is used with no meaningful threat and prayer is still high
       Small negative shaping is safer than positive "good timing" bonuses.

    6. Optional: add a small hard-wave resource-efficiency bonus.
       If we want one new conservation incentive, make it sparse and only on
       wave clear for harder waves, e.g. wave >= 30. That avoids early-wave
       farming. Keep it small.

  C. Curriculum recommendation

    Do NOT use 100% wave-30 starts again.
    v15.2 showed that leads to camping melee prayer and surviving until
    supplies run out.

    Preferred v17 curriculum is a mixed scaffolding curriculum, modeled after
    the g2048 scaffolding pattern in pufferlib_4/ocean/g2048/g2048.h:

      - 60% normal episodes from wave 1
      - 25% episodes starting at wave 30
      - 10% episodes starting at wave 31
      - 5% episodes starting at wave 32 or Jad-adjacent content later

    Why:
      - wave 1 starts preserve full-run kiting/resource behavior
      - wave 30 starts repeatedly expose Tok-Xil + Yt-MejKot
      - wave 31 starts expose Ket-Zek magic
      - mixed starts avoid overfitting to one late-wave snapshot

    The current env only supports one curriculum_wave/curriculum_pct pair.
    I would upgrade that to multi-bucket scaffolding before v17 if possible.

  D. Hyperparameter recommendation

    Preferred path: warm-start from the best v16.2 checkpoint around 4.7B.
    PufferLib already supports loading a pretrained checkpoint via
    --load-model-path. That is a better starting point than another 20B
    from-scratch run because the best policy already exists.

    Warm-start v17 proposal:
      - load_model_path = best checkpoint near 4.7B
      - total_timesteps = 1.5B to 2.5B additional steps
      - learning_rate = 0.0002 to 0.0003
      - anneal_lr = 1
      - ent_coef = 0.03
      - clip_coef = 0.10 to 0.15
      - keep gamma = 0.999, gae_lambda = 0.95, horizon = 256
      - keep total_agents = 4096 unless memory becomes a bottleneck
      - add checkpoint_interval = 50 or 100

    Why:
      - v16.2 collapse was optimizer stability, not lack of ability
      - lower LR + tighter clipping should protect the good policy
      - slightly higher entropy should help avoid deterministic collapse
      - shorter run + more checkpoints gives safer early stopping

    If warm-start is not used, fallback from-scratch v17:
      - keep v16.1-style rewards
      - lower learning_rate to 0.0005
      - keep anneal_lr = 1
      - ent_coef = 0.03
      - use mixed scaffolding curriculum
      - run 2.5B to 5B, not 20B

  E. Things I would NOT change for v17

    - Do not disable movement. History says that makes the task easier to
      learn short-term but hurts the real objective. Kiting is core.
    - Do not add large dense correct/wrong prayer rewards again.
      That repeatedly caused reward domination or instability.
    - Do not increase network size yet. Current failures look like signal and
      optimizer issues, not model-capacity issues.
    - Do not run another ultra-long 20B job at lr=0.001.
      That already found a good policy and then destroyed it.

  F. My strongest v17 recommendation

    If we only do three things before the next run, I would do these:

      1. Add effective-threat observation features for dual-mode NPCs.
      2. Run a mixed scaffolding curriculum instead of 0% or 100% late starts.
      3. Warm-start from the best v16.2 checkpoint with much lower LR and more
         frequent checkpoints.

    That is the highest-probability path to getting past the wave-30/31 wall
    without reintroducing reward hacks.
