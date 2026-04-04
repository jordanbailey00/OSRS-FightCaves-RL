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
