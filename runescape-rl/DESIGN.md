================================================================================
FIGHT CAVES RL — DESIGN DOCUMENT
================================================================================

Last updated: 2026-04-06. Update only on major architectural changes or
significant new lessons learned.

================================================================================
TABLE OF CONTENTS
================================================================================

  PART 1: CURRENT ARCHITECTURE
    1.1  System Overview
    1.2  Backend Simulation (fc_*.c/h)
    1.3  Frontend — Raylib Debug Viewer (demo-env/)
    1.4  Training Environment — PufferLib 4.0 (training-env/)
    1.5  Asset Pipeline
    1.6  Build System
    1.7  Observation / Action / Reward Contracts

  PART 2: HOW WE GOT HERE
    2.1  Architecture Decisions
    2.2  What Worked
    2.3  What Didn't Work
    2.4  References Used
    2.5  Training History (v1–v18)
    2.6  Technical Gotchas

  PART 3: REPRODUCTION GUIDE
    3.1  Porting a New Boss/Zone
    3.2  Asset Export Workflow
    3.3  PufferLib Integration Checklist

================================================================================
PART 1: CURRENT ARCHITECTURE
================================================================================

--------------------------------------------------------------------------------
1.1 System Overview
--------------------------------------------------------------------------------

Three components share one logical Fight Caves backend, but today it still
exists as mirrored source copies under `training-env/src` and `demo-env/src`:

  1. fc_*.c/h (backend)     — deterministic game simulation
  2. demo-env/  (viewer)    — Raylib 3D viewer for human play + debug, ~2100 LOC
  3. training-env/ (RL)     — PufferLib 4.0 headless wrapper, ~360 LOC

Data flow:

  [Python trainer] --actions--> [fight_caves.h adapter] --actions-->
  [fc_step()] --state--> [fc_write_obs()] --obs--> [Python trainer]

  [Human input] --clicks/keys--> [viewer.c] --actions-->
  [fc_step()] --state--> [fc_fill_render_entities()] --entities--> [viewer.c render]

The viewer NEVER decides gameplay outcomes. It sends actions, reads state,
draws state. All gameplay authority lives in the mirrored fc_*.c backend.

--------------------------------------------------------------------------------
1.2 Backend Simulation (fc_*.c/h)
--------------------------------------------------------------------------------

Gameplay files currently exist in both `demo-env/src/` and `training-env/src/`.
The two trees are intended to stay behaviorally identical, but they are not yet
physically shared.

FILE MAP (18 gameplay files mirrored between training and demo):

  fc_types.h — Core types and constants.
    Structs: FcState, FcPlayer, FcNpc, FcPendingHit, FcWaveEntry,
             FcRenderEntity
    Enums:   FcNpcType (8 types), FcAttackStyle, FcPrayer, FcTerminalCode,
             FcSpawnDir
    Constants: FC_ARENA_WIDTH=64, FC_ARENA_HEIGHT=64, FC_MAX_NPCS=16,
               FC_VISIBLE_NPCS=8, FC_MAX_PENDING_HITS=8, FC_NUM_WAVES=63,
               FC_NUM_ROTATIONS=15, FC_MAX_SHARKS=20, FC_MAX_PRAYER_DOSES=32,
               FC_MAX_ROUTE=64, FC_MAX_EPISODE_TICKS=200000,
               FC_FOOD_COOLDOWN_TICKS=3, FC_POTION_COOLDOWN_TICKS=2

  fc_contracts.h — Frozen observation/action/reward buffer layouts.
    FC_POLICY_OBS_SIZE=106, FC_REWARD_FEATURES=18, FC_ACTION_MASK_SIZE=166,
    FC_NUM_ACTION_HEADS=7, FC_PUFFER_OBS_SIZE=142, FC_PUFFER_NUM_ATNS=5
    Per-feature index constants (FC_OBS_PLAYER_HP, FC_RWD_DAMAGE_DEALT, etc.)
    Per-head dimensions and direction lookup tables (FC_MOVE_DX[], FC_MOVE_DY[])

  fc_api.h — Public API.
    fc_init(), fc_reset(seed), fc_step(actions[7]), fc_destroy()
    fc_write_obs(out[124]), fc_write_mask(out[166]),
    fc_write_reward_features(out[18])
    fc_is_terminal(), fc_terminal_code()
    fc_fill_render_entities(entities[], count)
    fc_rng_seed(), fc_rng_next(), fc_rng_int(max), fc_rng_float()
    fc_state_hash() [demo-env only]

  fc_state.c — State lifecycle, observation/mask/reward writers.
    setup_arena(): loads fightcaves.collision (4096 bytes, 64x64 walkability)
    init_player(): fixed loadout — Def 70, Rng 70, Prayer 43, Rune Crossbow,
      Black D'hide, 50,000 Adamant Bolts. ranged_attack_bonus=153,
      ranged_strength_bonus=100, prayer_bonus=0.
    fc_reset(): memset(0), seed RNG, random rotation 0-14, setup arena,
      init player, spawn wave 1.
    fc_write_obs(): player features (17) + 8 NPC slots sorted by distance (80)
      + meta (9) + reward features (18) = 124 floats.
    fc_write_mask(): 166 floats, 1.0=valid, masks invalid movement directions,
      unavailable NPC slots, food/potion cooldowns, HP/prayer caps.

  fc_tick.c — Main tick loop, 8-phase processing order.
    Phase ordering (matches RSPS GameTick.kt):
      1. Clear per-tick flags
      2. Process player actions (prayer, eat/drink, movement, attack)
      3. Decrement player timers
      4. Prayer drain tick
      5. NPC AI tick (all 16 slots)
      6. Resolve pending hits (NPC→player, player→NPC)
      7. Check terminal (death, wave clear, cave complete, tick cap)
      8. Increment tick

    process_player_actions(): ~195 LOC. Handles all 7 action heads
      simultaneously. Prayer is instant. Eat/drink check separate cooldown
      timers. Walk-to-tile via BFS (heads 5+6) or directional (head 0).
      Attack auto-approaches to weapon range (7 tiles) then fires on cooldown.

    npc_slot_to_index(): resolves visible slot 0-7 to FcNpc array index,
      using same distance sort as observation writer.

    check_jad_healers(): spawns 4 Yt-HurKot at cardinal offsets from Jad
      when Jad HP drops below 50%. Resets flag if Jad healed above 50%.

  fc_combat.c — OSRS combat formulas.
    fc_hit_chance(att_roll, def_roll):
      if att > def: 1 - (def+2) / (2*(att+1))
      else: att / (2*(def+1))

    fc_player_ranged_max_hit(): 5 + (eff_str * ranged_strength_bonus) / 64
      At Rng 70 + adamant bolts: 126 tenths (12.6 HP).

    fc_player_def_roll(): for magic attacks uses 0.3*def + 0.7*magic.

    fc_prayer_blocks_style(): 100% block in PvM (NOT PvP 60%).

    fc_distance_to_npc(): Chebyshev distance to closest tile of multi-tile
      NPC footprint.

    fc_ranged_hit_delay(distance): 5 * distance / 30 + 1 (game ticks).

    fc_npc_hit_delay(): per-NPC-type timing from Void 634 gfx.toml:
      Tok-Xil: 5*dist/30 + 1
      Ket-Zek: (8 + 8*dist)/30 + 1
      Jad magic: 8*dist/30 + 1
      Jad ranged: fixed 5 ticks (no projectile)

    Pending hit queue: fc_queue_pending_hit() adds to per-entity queue.
    fc_resolve_player_pending_hits(): decrements ticks_remaining, checks
      active prayer at RESOLVE time (not fire time), applies damage, handles
      Tz-Kih prayer drain, auto-retaliate.
    fc_resolve_npc_pending_hits(): handles death, Tz-Kek split, Jad kill,
      healer distraction.

  fc_npc.c — NPC stat table, AI, spawning.
    NPC_STATS[9] table: per-type max_hp, attack_style, attack_speed,
      attack_range, max_hit, size, movement_speed, prayer_drain, etc.
    Type sizes: Tz-Kih=1, Tz-Kek=2, Tz-Kek-sm=1, Tok-Xil=3,
      Yt-MejKot=4, Ket-Zek=5, Jad=5, Yt-HurKot=1.

    fc_npc_spawn(): init from stat table, set attack_timer to attack_speed.
    fc_npc_tz_kek_split(): spawns 2 small Tz-Kek at death location.
      Does NOT increment npcs_remaining (pre-counted).

    Jad no longer has a telegraph state machine in backend or policy obs.
      Jad chooses magic vs ranged when the attack is actually queued.
      The agent sees that committed attack only through the same pending-hit
      channels used for every other NPC (`pending_style` / `pending_ticks`).

    Yt-MejKot: heals nearby NPCs (≤8 tiles, HP < 50% max) 100 HP every
      4 ticks.
    Yt-HurKot: follows Jad, heals 50 HP every 4 ticks within 5 tiles.
      Player attack permanently distracts (stops healing).

    fc_npc_tick(): main AI dispatcher per NPC per tick. Dual-mode NPCs
      (Tok-Xil, Ket-Zek) switch between melee and ranged/magic based on
      distance to player.

  fc_pathfinding.c — Collision, LOS, BFS.
    fc_tile_walkable(), fc_footprint_walkable() — tile and multi-tile queries.
    fc_move_toward(): move size-1 entity with diagonal-first fallback.
    fc_npc_step_toward_sized(): move sized NPC one tile toward target,
      checking full footprint walkability.
    fc_has_line_of_sight(): Bresenham line, checks intermediate tiles.
    fc_has_los_to_npc(): LOS to closest tile of NPC footprint.
    fc_pathfind_bfs(): greedy walk first (try diagonal, then axes), fall
      back to full 8-directional BFS. Scratch arrays are stack-local.

  fc_prayer.c — Prayer drain and restoration.
    fc_prayer_drain_tick(): counter-based OSRS system:
      drain_counter += 12 per tick (all protect prayers)
      resistance = 60 + 2 * prayer_bonus (prayer_bonus=0 in FC loadout)
      while counter > resistance: drain 10 tenths, counter -= resistance
    fc_prayer_apply_action(): toggle prayer, auto-deactivate if depleted.
    fc_prayer_potion_restore(prayer_level): (level/4 + 7) * 10 tenths.
      At prayer 43: 170 tenths = 17 points.

  fc_wave.c — 63 waves, 15 rotations, wave spawning.
    WAVE_TABLE[63]: NPC types and count per wave.
    WAVE_ROTATIONS[63][15][6]: spawn directions per rotation.
    5 spawn positions: SOUTH(37,17), SOUTH_WEST(14,17), NORTH_WEST(13,49),
      SOUTH_EAST(51,27), CENTER(32,32).
    find_valid_spawn(): search expanding rings for walkable footprint.
    fc_wave_spawn(): iterate wave NPCs, look up rotation, spawn at position.
      Tz-Kek counts as 2 in remaining counter.
    fc_wave_check_advance(): if npcs_remaining == 0, advance wave or
      set TERMINAL_CAVE_COMPLETE after wave 63.

  fc_rng.c — XORshift32 deterministic RNG.
    fc_rng_seed(): guards against zero state.
    fc_rng_next(): x ^= x<<13; x ^= x>>17; x ^= x<<5.
    fc_rng_int(max): next() % max.
    fc_rng_float(): [0.0, 1.0) via 24-bit mantissa.

  fc_capi.c — Python ctypes FFI bridge.
    FcEnvCtx: FcState + obs[290] + actions[7] + reward + episode tracking.
    fc_capi_create/destroy/reset/step(): single-env lifecycle.
    fc_capi_batch_create/destroy/reset/step_flat(): vectorized N-env
      interface. Contiguous memory, single step call for all envs.
    Auto-reset on terminal (reseeds from rng_state).

  fc_debug.c (viewer-only) — FNV-1a state hash for determinism verification.
  fc_hash.c (viewer-only) — Excluded from training-env (FC_NO_HASH).

KEY STATE STRUCTS:

  FcPlayer (~50 fields):
    Position: x, y (tile coords, 0-63)
    Vitals: current_hp, max_hp, current_prayer, max_prayer (tenths)
    Prayer: prayer (enum), prayer_drain_counter
    Consumables: sharks_remaining (0-20), prayer_doses_remaining (0-32)
    Timers: attack_timer, food_timer, potion_timer, combo_timer
    Route: route_x[64], route_y[64], route_len, route_idx
    Combat: attack_target_idx (-1=none), approach_target, facing_angle
    Pending hits: pending_hits[8], num_pending_hits
    Per-tick flags: damage_taken_this_tick, hit_landed_this_tick, etc.
    Equipment: ranged_attack_bonus=153, ranged_strength_bonus=100,
      defence_crush=110, defence_magic=91, prayer_bonus=0

  FcNpc (~30 fields):
    Identity: active, npc_type, spawn_index
    Position: x, y, size (1-5)
    Vitals: current_hp, max_hp, is_dead, death_timer
    Combat: attack_style, attack_timer, attack_speed, attack_range, max_hit
    AI: movement_speed
    Healing: heal_timer, heal_amount, healer_distracted, heal_target_idx
    Pending hits: pending_hits[8], num_pending_hits

  FcState:
    player (FcPlayer), npcs[16] (FcNpc)
    Wave: current_wave (1-63), rotation_id (0-14), npcs_remaining
    Tick: tick counter
    Terminal: terminal (FcTerminalCode)
    RNG: rng_state, rng_seed (XORshift32)
    Collision: walkable[64][64]
    Jad: jad_healers_spawned
    Per-tick aggregated flags for reward features
    Episode-level analytics for training logging

DETERMINISM:
  Episode = (seed, action_sequence). Fully reproducible.
  XORshift32 single RNG state seeded at reset.
  fc_state_hash() = FNV-1a over explicit fields (padding-independent).
  Golden traces verified: PR2 seed 777 → 0xf7c9fc45.

--------------------------------------------------------------------------------
1.3 Frontend — Raylib Debug Viewer (demo-env/)
--------------------------------------------------------------------------------

viewer.c is the main viewer. Header-only loaders handle assets.

MAIN LOOP:
  1. Init: create 1280x800 window, load terrain/objects/NPC models/animations/
     prayer icons/tab sprites, call fc_reset().
  2. Frame loop (60 FPS):
     a. Input: camera orbit (right-drag), zoom (scroll), keyboard shortcuts,
        tile clicks (raycast → BFS pathfind), NPC clicks (set attack target).
     b. Tick accumulation: tick_acc += frame_time * TPS. Fire fc_step() when
        tick_acc >= 1.0.
     c. Post-tick: save prev positions, detect hitsplats/projectiles from
        state deltas, sync animations, fill render entities.
     d. Render: draw_scene() (3D), draw_header() (top bar), draw_panel()
        (side panel with tabs), draw_debug() (debug overlay).

CAMERA:
  Spherical orbit. cam_yaw, cam_pitch (clamped 0.1–1.4 rad), cam_dist
  (clamped 5–300). Right-drag orbits. Scroll zooms.
  Presets: key 4 = overhead (pitch=1.35, dist=120), key 5 = tactical
  (pitch=0.6, dist=50). Default: pitch=0.8, dist=30.

SMOOTH INTERPOLATION:
  Store prev_player_x/y and prev_npc_x/y[] before each tick. Compute
  tick_frac (0.0 right after tick, 1.0 just before next). Blend:
  render_pos = prev + (cur - prev) * tick_frac. Smooths 2 TPS game ticks
  to 60 FPS rendering.

UI PANELS:
  Header (40px top): wave counter, seed, tick, TPS, HP, prayer.
  Side panel (220px right): HP bar, prayer bar, wave info, timers.
  3 tabs (Inventory / Combat / Prayer):
    Inventory: 28-slot grid showing prayer pots (doses), sharks, empties.
    Combat: attack speed, style, current target.
    Prayer: 3 protection prayer buttons, active prayer highlighted.
  NPC health bars: clickable list of active NPCs with HP bars.

DEBUG OVERLAYS (O key):
  8 flags in fc_debug_overlay.h (808 LOC):
    DBG_COLLISION (1<<0): green/red tile walkability
    DBG_LOS (1<<1): line-of-sight rays to NPCs
    DBG_PATH (1<<2): yellow BFS route
    DBG_RANGE (1<<3): blue attack range circle
    DBG_ENTITY_INFO (1<<4): detailed entity state on hover
    DBG_OBS (1<<5): raw observation tensor values
    DBG_MASK (1<<6): valid actions per head
    DBG_REWARD (1<<7): reward feature breakdown
  Toggle all: O. Cycle individual: Shift+O.
  Event log: 128-entry ring buffer, timestamped, color-coded.
  debug_overlay_3d(): called inside BeginMode3D (collision tiles).
  debug_overlay_screen(): called after EndMode3D (2D projected overlays).

INPUT → ACTION MAPPING:
  Click empty tile → BFS pathfind, fill player route.
  Click NPC → set attack_target_idx + approach_target.
  Keys 1/2/3 → buffer prayer toggle. F → eat. P → drink. X → run toggle.
  build_human_actions(): converts buffered inputs into action[7] array
    for fc_step(). Movement handled via route (not action head 0).

TICK CONTROL:
  Space = pause. Right arrow = single step. Up/Down = TPS (1-30).
  R = reset episode. F9 = godmode. T = agent test mode.

AGENT TEST MODE (T key):
  Array of AGENT_TESTS[] with hardcoded action sequences for verification.
  Tests movement (8 dirs, run), combat, prayer, consumables, combined.

ASSET LOADERS (header-only):

  fc_terrain_loader.h (186 LOC):
    TerrainMesh struct. terrain_load() reads TERR binary (magic 0x54455252).
    Vertices + vertex colors + heightmap for ground_y() queries.
    terrain_offset() shifts to world origin (2368, 5056).

  fc_objects_loader.h (227 LOC):
    ObjectMesh struct. Reads OBJS v1 (vertex colors) or OBJ2 v2
    (vertex colors + texcoords). objects_load_atlas() reads ATLS binary
    for texture atlas. 3,867 placed objects, 26 unique models, 424K tris.

  fc_npc_models.h (210 LOC):
    NpcModelSet, NpcModelEntry structs. Reads MDL2 binary (magic 0x4D444C32).
    Per-model: expanded vertices, colors, base_verts, vertex_skins,
    face_indices. Coordinate transform: OSRS → Raylib (negate Z, scale ÷128).
    fc_npc_type_to_model_id() maps internal enum to b237 model ID:
      Tz-Kih→3116, Tz-Kek→3118, Tz-Kek-sm→3120, Tok-Xil→3121,
      Yt-MejKot→3123, Ket-Zek→3125, Jad→3127, Yt-HurKot→3128.

  fc_anim_loader.h (681 LOC):
    OSRS vertex-group animation system. NOT skeletal — vertex labels 0-255,
    per-label transform slots (translate, rotate, scale).
    AnimCache: global store of AnimFrameBase + AnimSequence.
    AnimModelState: per-entity working copy of transformed vertices + group map.
    anim_apply_frame(): applies Euler Z-X-Y rotation (sine table), translation,
      scaling per transform slot per vertex group.
    anim_update_mesh(): re-expands base_verts → triangle vertices via
      face_indices, outputs for GPU upload.
    Animation selection (viewer.c): per entity state — idle/walk/attack/death
      sequences. Frame timer at 0.02s per frame. UpdateMeshBuffer() each frame.

    Player anims: idle=4591, death=836, eat=829, attack=4230, walk=4226, run=4228.
    NPC anims: per-type arrays (NPC_ANIM_IDLE[], NPC_ANIM_DEATH[], etc.)
      e.g., Jad: idle=2650, walk=2651, melee=2655, magic=2656, death=2654.

COORDINATE SYSTEM:
  Game logic: tile coords (int), X=east, Y=north.
  Raylib: X=east, Y=up, Z=-north (right-handed).
  Entity render: ey = -(entity.y). Scale: OSRS units ÷ 128 = tiles.
  FC arena origin: world (2368, 5056) = region (37,79).

--------------------------------------------------------------------------------
1.4 Training Environment — PufferLib 4.0 (training-env/)
--------------------------------------------------------------------------------

fight_caves.h — PufferLib adapter.

  FightCaves struct:
    Log log (PufferLib required)
    float* observations, actions, rewards, terminals
    16 reward weight floats (w_damage_dealt, w_npc_kill, etc.)
    Curriculum: curriculum_wave, curriculum_pct
    FcState state (embedded)
    uint32_t seed_counter

  c_reset(): increment seed, fc_reset(), optionally skip to curriculum wave,
    write initial obs via fc_puffer_write_obs().
  c_step(): cast float actions to int[5], fc_step(), compute shaped reward,
    write obs + mask, auto-reset on terminal.
  c_render(): no-op (viewer is separate process).
  c_close(): fc_destroy().

  fc_puffer_write_obs(): writes 142 floats = 106 policy obs + 36 action mask.
    Policy obs uses FC_POLICY_OBS_SIZE=106. Action mask is the first 36 mask
    floats for the 5 policy heads (move/attack/prayer/eat/drink).

  fc_puffer_compute_reward(): weighted sum of 18 reward features plus
    configurable shaping terms.
    Damage taken uses QUADRATIC scaling: dmg_frac^2 * 70 * weight.
    Food/pot waste, stall pressure, kiting, unnecessary prayer, and related
    shaping terms are configurable from the ini.

binding.c (78 LOC) — PufferLib macros.
  OBS_SIZE = 162, OBS_TYPE = FLOAT
  NUM_ATNS = 5, ACT_SIZES = {17, 9, 5, 3, 2}, ACT_TYPE = DOUBLE
  my_init(): parses config/fight_caves.ini into FightCaves struct.
  my_log(): exports score, episode_return, episode_length, wave_reached.

fight_caves.c (87 LOC) — Standalone test binary. Runs 100 episodes with
  random actions, prints wave_reached and SPS.

build.sh — PufferLib build integration.
  Modes: default (CUDA), --cpu, --local (standalone debug), --fast (optimized).
  CUDA: nvcc + cuDNN. CPU: gcc -fPIC -fopenmp.
  Output: pufferlib/_C.cpython-312-x86_64-linux-gnu.so (Python extension)
  or fight_caves (standalone binary).

config/fight_caves.ini — Current v18 baseline config:
  Reward weights: w_damage_dealt=1.0, w_damage_taken=-0.75 (quadratic),
    w_npc_kill=2.0, w_wave_clear=10.0, w_jad_damage=2.0,
    w_player_death=-20.0, w_cave_complete=100.0, w_tick_penalty=-0.001,
    w_correct_danger_prayer=1.0.
  Shaping: capped stall penalty, configurable food/pot waste penalties,
    kiting reward, unnecessary-prayer penalty, NPC melee pressure penalty.
  Curriculum: all buckets disabled in the clean `v18` scratch baseline.
  Training: 4096 agents, 1.5B steps, lr=0.001, gamma=0.999, horizon=256,
    clip_coef=0.2, ent_coef=0.02.
  Policy: hidden_size=256, num_layers=2.

PERFORMANCE:
  Training SPS: ~2.0M (v18 finished at ~2.02M SPS).
  Per-env throughput: ~500 steps/sec.
  FcState size: ~2KB. 4096 envs = ~8MB working set.

--------------------------------------------------------------------------------
1.5 Asset Pipeline
--------------------------------------------------------------------------------

Offline Python export → binary assets → C runtime loaders.

SOURCE: OSRS b237 cache from openrs2.org (flat-file format).
  Downloaded as cache-oldschool-live-en-b237-2026-04-01-10-45-07-openrs2#2509.tar.gz
  Extract to /tmp/osrs_cache_modern/cache/ (re-extract after reboot).

EXPORT SCRIPTS (Python, in reference repos):
  /home/joe/projects/runescape-rl-reference/valo_envs/ocean/osrs/scripts/
  These decode cache indices and write binary formats readable by C loaders.

BINARY FORMATS:

  TERR (fightcaves.terrain, 401 KB):
    Magic 0x54455252. vertex_count, region_count, origin, vertices[3*N],
    colors[4*N], heightmap samples.

  OBJ2 (fightcaves.objects, 30 MB):
    Magic 0x4F424A32. placement_count, origin, total_verts, vertices[3*N],
    texcoords[2*N], colors[4*N]. Companion .atlas file.

  ATLS (fightcaves.atlas, 15 MB):
    Magic 0x41544C53. width, height, raw RGBA pixels.

  MDL2 (fc_npcs.models, fc_player.models, fc_projectiles.models):
    Magic 0x4D444C32. model_count, offset table, per-model: model_id,
    expanded_verts, colors, base_verts (int16[3*N]), vertex_skins (uint8[N]),
    face_indices (uint16[3*F]), priorities.

  ANIM (fc_all.anims, 173 KB):
    Magic 0x414E494D. framebase_count, sequence_count, framebase definitions,
    sequences with inlined frame data and delay values.

  COLLISION (fightcaves.collision, 4 KB):
    Raw 64x64 uint8 array. 1=walkable, 0=blocked. Row-major [y][x],
    transposed to [x][y] at load time.

DEMO-ENV ASSETS (demo-env/assets/, 45 MB total):
  fightcaves.terrain, fightcaves.objects, fightcaves.atlas,
  fightcaves.collision, fc_npcs.models, fc_player.models,
  fc_projectiles.models, fc_all.anims, fc_player.anims,
  sprites/ (prayer icons, tab icons), pray_*.png.

TRAINING-ENV ASSETS (training-env/assets/, 4 KB):
  fightcaves.collision only. No rendering assets needed.

--------------------------------------------------------------------------------
1.6 Build System
--------------------------------------------------------------------------------

CMake 3.20+, C11 standard, Release by default.

Root CMakeLists.txt adds two subdirectories:
  demo-env/ → fc_viewer (Raylib executable) + test_headless
  training-env/ → libfc_capi.so (shared lib)

DEMO-ENV BUILD:
  Sources: mirrored gameplay `fc_*.c` files + viewer.c + fc_debug.c + fc_hash.c
  Links: libraylib.a (local at demo-env/raylib/), X11, OpenGL, pthread, math
  Skipped if Raylib not found.

TRAINING-ENV BUILD:
  Sources: fc_state.c, fc_tick.c, fc_combat.c, fc_prayer.c, fc_pathfinding.c,
    fc_npc.c, fc_wave.c, fc_rng.c, fc_capi.c
  Defines: FC_NO_HASH (no fc_hash.c)
  Links: math only. No Raylib dependency.

PUFFERLIB BUILD (training-env/build.sh):
  Compiles fight_caves.h + binding.c against PufferLib headers (vecenv.h).
  fight_caves.h still directly includes the backend `.c` files.
  Produces _C.so Python extension.
  Flags: -fPIC -fopenmp -O3 -DPLATFORM_DESKTOP

BUILD COMMANDS:
  cmake -B build -DCMAKE_BUILD_TYPE=Release && cmake --build build -j$(nproc)
  cd build && ./demo-env/fc_viewer          # viewer
  cd training-env && ./build.sh --local     # standalone test binary

DEPENDENCIES:
  C: math.h, stdio.h, stdlib.h, string.h, stdint.h
  Demo-env: Raylib 5.5 (prebuilt, included in repo)
  Training: PufferLib 4.0 headers (vecenv.h)
  Python: 3.12, torch, numpy, wandb, pufferlib

--------------------------------------------------------------------------------
1.7 Observation / Action / Reward Contracts
--------------------------------------------------------------------------------

These are frozen in fc_contracts.h. Both training-env and demo-env are
expected to use identical contracts.

OBSERVATION (106 policy floats + 36 RL action mask = 142 total for PufferLib;
             full backend buffer = 106 policy + 18 reward + 166 mask = 290):

  All values normalized to [0, 1]. Flat float array, no names — the agent
  learns what each position means through training.

  Player (17 floats, positions 0-16):
    [0]  player_hp_fraction         current_hp / max_hp
    [1]  player_prayer_fraction     current_prayer / max_prayer
    [2]  player_x                   x / 64
    [3]  player_y                   y / 64
    [4]  player_attack_timer        attack_timer / 5.0
    [5]  player_pray_melee_active   0 or 1
    [6]  player_pray_range_active   0 or 1
    [7]  player_pray_magic_active   0 or 1
    [8]  player_sharks_remaining    count / 20
    [9]  player_prayer_doses        count / 32
    [10] player_incoming_melee_1t   pending melee hits landing in 1 tick / 4
    [11] player_incoming_range_1t   pending ranged hits landing in 1 tick / 4
    [12] player_incoming_magic_1t   pending magic hits landing in 1 tick / 4
    [13] player_incoming_melee_2t   pending melee hits landing in 2 ticks / 4
    [14] player_incoming_range_2t   pending ranged hits landing in 2 ticks / 4
    [15] player_incoming_magic_2t   pending magic hits landing in 2 ticks / 4
    [16] player_attack_target       visible target slot / 8 (0=none)

  NPCs (8 slots × 10 floats = 80, positions 17-96):
    Sorted by Chebyshev distance (closest first), tiebreak by spawn_index.
    Slot 0 = closest NPC, slot 7 = 8th closest. Empty slots = all zeros.

    Per slot (10 floats):
    [0]  npcN_valid                 1=NPC in slot, 0=empty
    [1]  npcN_x                    x / 64
    [2]  npcN_y                    y / 64
    [3]  npcN_hp_fraction          current_hp / max_hp
    [4]  npcN_distance             chebyshev_distance / 64
    [5]  npcN_effective_style_now  style this NPC would use now at current distance / 3
    [6]  npcN_attack_timer         ticks_until_next_attack / attack_speed
    [7]  npcN_line_of_sight        1=clear LOS from player
    [8]  npcN_pending_attack_style 0=none in flight, 0.33/0.67/1.0=attack in flight
    [9]  npcN_pending_attack_ticks ticks_until_hit_resolves / 10 (0=none)

  Meta (9 floats, positions 97-105):
    [0]  current_wave               wave / 63
    [1]  rotation_id                rotation / 15
    [2]  npcs_remaining             count / 16
    [3]  prayer_drain_counter       counter / current drain resistance
    [4]  incoming_melee_3t          pending melee hits landing in 3 ticks / 4
    [5]  incoming_range_3t          pending ranged hits landing in 3 ticks / 4
    [6]  incoming_magic_3t          pending magic hits landing in 3 ticks / 4
    [7]  damage_taken_this_tick     damage / max_hp
    [8]  wave_just_cleared          0 or 1

  Disabled from the live policy contract:
    player food/potion/combo timers, run energy, is_running, ammo, defence,
    ranged level; NPC type, size, healer flag, Jad telegraph, aggro; meta
    total damage dealt, total damage taken, and kills_this_tick.

REWARD FEATURES (18 floats, emitted by C, shaped by Python):
    [0]  Damage dealt: damage / 1000
    [1]  Damage taken: damage / max_hp
    [2]  NPC kill: count
    [3]  Wave clear: binary
    [4]  Jad damage: damage / 1000
    [5]  Jad kill: binary
    [6]  Player death: binary
    [7]  Cave complete: binary
    [8]  Food used: binary
    [9]  Prayer pot used: binary
    [10] Correct Jad prayer: binary
    [11] Wrong Jad prayer: binary
    [12] Invalid action: binary
    [13] Movement: binary
    [14] Idle: binary
    [15] Tick penalty: always 1.0
    [16] Correct danger prayer: binary
    [17] Wrong danger prayer: binary

ACTION SPACE (7 heads, 5 used in PufferLib v1):

  Head 0 — MOVE (17 values):
    0=idle, 1-8=walk (N/NE/E/SE/S/SW/W/NW), 9-16=run
    Ignored when BFS route active. Agent chooses direction EVERY tick.

  Head 1 — ATTACK (9 values):
    0=no target, 1-8=visible NPC slot 0-7 (distance-sorted).
    Sets approach_target: auto-walks to weapon range (7 tiles), then
    auto-attacks on cooldown.

  Head 2 — PRAYER (5 values):
    0=no change, 1=off, 2=protect magic, 3=protect range, 4=protect melee.
    Instant. Exclusive (only one protect active).

  Head 3 — EAT (3 values):
    0=none, 1=shark (20 HP, 3-tick cooldown), 2=combo eat (18 HP, 1-tick).
    Masked when: no food, timer active, HP full.

  Head 4 — DRINK (2 values):
    0=none, 1=prayer potion (17 pts, 2-tick cooldown).
    Masked when: no doses, timer active, prayer full.

  Head 5 — MOVE_TARGET_X (65 values): [excluded from PufferLib v1]
  Head 6 — MOVE_TARGET_Y (65 values): [excluded from PufferLib v1]
    When both non-zero: BFS pathfind to (x-1, y-1). Route auto-consumed.

  All heads processed SIMULTANEOUSLY. No head blocks another.
  Can eat + drink + pray + move + attack on same tick.

ACTION MASK (36 floats for v1, 166 for full 7-head):
  1.0 = valid, 0.0 = masked. Included in observation buffer for policy.
  PufferLib v1: OBS = 106 policy + 36 mask = 142 total.

REWARD SHAPING WEIGHTS (configured in config/fight_caves.ini):

  All weights applied in fc_puffer_compute_reward() in fight_caves.h.
  Reward each tick = weighted sum of reward features + configurable shaping.

  COMBAT:
    w_damage_dealt      Per-tick damage dealt to NPCs (normalized /1000).
                        Incentivizes attacking. Higher = more aggressive.
    w_npc_kill          Fires when an NPC dies. Incentivizes finishing kills.
    w_wave_clear        Fires when all NPCs in a wave are dead. Wave progression.
    w_jad_damage        Extra damage bonus specifically for hitting Jad.
    w_jad_kill          Big reward for killing Jad (wave 63 boss).
    w_cave_complete     Ultimate reward for clearing all 63 waves.

  SURVIVAL:
    w_damage_taken      Per-tick damage taken. QUADRATIC scaling:
                        penalty = dmg_frac² × 70 × weight. Small hits
                        ignorable, big hits catastrophic.
    w_player_death      Terminal penalty when player HP reaches 0.

  CONSUMABLES:
    Food and potion timing/waste are now driven by the configurable
    `shape_food_*` and `shape_pot_*` terms. The live `v18` baseline disables
    the extra threat-aware penalties and keeps the direct waste penalties.

  PRAYER:
    w_correct_danger_prayer  Reward for correct prayer blocking a ranged/magic
                        hit at resolve time. Positive reinforcement only.
    w_wrong_danger_prayer    Penalty for wrong prayer when hit. Set to 0 in v9+
                        (damage_taken handles this implicitly).
    w_correct_jad_prayer     Jad-specific prayer reward (disabled since v5).
    w_wrong_jad_prayer       Jad-specific prayer penalty (disabled since v5).

  OTHER:
    w_invalid_action    Small penalty when agent picks a masked action.
    w_movement          Reward/penalty for walking/running. Usually 0.
    w_idle              Reward/penalty for choosing idle movement. Usually 0.
    w_tick_penalty      Fires every tick. Small time pressure to progress.

  CONFIGURABLE SHAPING (in fight_caves.h, parsed from ini):
    food/pot waste      shape_food_* / shape_pot_* terms
    wrong prayer        shape_wrong_prayer_penalty
    NPC-specific block  shape_npc_specific_prayer_bonus
    melee pressure      shape_npc_melee_penalty
    wasted attack       shape_wasted_attack_penalty
    wave stall          shape_wave_stall_*
    no-damage stall     shape_not_attacking_*
    kiting              shape_kiting_reward + distance band
    unnecessary prayer  shape_unnecessary_prayer_penalty
    Prayer flick reward remains intentionally disabled in the live code.

  TRAINING HYPERPARAMETERS:
    learning_rate       PPO learning rate. 0.001 default.
    anneal_lr           1=decay LR to 0 over training, 0=constant LR.
    gamma               Discount factor. 0.999 for long-horizon games.
    gae_lambda          GAE smoothing. 0.95 standard.
    clip_coef           PPO clip coefficient. 0.2 standard.
    vf_coef             Value loss weight. 0.5 standard.
    ent_coef            Entropy bonus. Higher = more exploration.
    max_grad_norm       Gradient clipping. 0.5 standard.
    horizon             Rollout length per update. 256.
    minibatch_size      Samples per gradient step. 4096.
    total_agents        Parallel environments. 4096.
    total_timesteps     Training budget.

================================================================================
PART 2: HOW WE GOT HERE
================================================================================

--------------------------------------------------------------------------------
2.1 Architecture Decisions
--------------------------------------------------------------------------------

C-FIRST PIVOT (2026-03-24):
  Original approach was Kotlin/JVM (forking Void RSPS). Abandoned because:
  - JVM overhead killed RL throughput (target: millions of steps/sec)
  - Kotlin server was 500K+ LOC, 99% irrelevant to Fight Caves
  - Cache decoding in Kotlin tied to JVM ecosystem
  Decision: rewrite FC simulation in C, use Python for RL, Raylib for viewer.
  Kotlin history preserved in archive/kotlin-final branch.

SINGLE MECHANICS CONTRACT:
  All gameplay rules are intended to live in the mirrored `fc_*.c` files.
  Viewer and training wrap that contract differently, but the backend itself
  should behave the same in both places. The current weakness is that the code
  is still duplicated rather than physically shared.

HYBRID OFFLINE-EXPORT + RUNTIME-SIM:
  Cache decoding is complex — Python wins (flexible, libraries available).
  Runtime sim must be fast — C wins (~2.0M SPS on the current stack).
  Rendering needs GPU — Raylib/OpenGL wins.
  The Void RSPS is an oracle for correctness, not a codebase to fork.

FLAT STRUCT STATE:
  FcState has no pointers, no heap per-tick. Enables memset reset, cache
  locality, trivial serialization. Follows PufferLib OSRS PvP pattern.

5-HEAD ACTION SPACE (v1):
  Started with 5 heads (move, attack, prayer, eat, drink = 36 actions).
  Walk-to-tile heads 5-6 (65+65 = 130 more actions) implemented in backend
  but excluded from PufferLib v1 to keep action space small. Directional
  movement + attack auto-approach covers all needed movement.

COUNTER-BASED PRAYER DRAIN:
  Not a simple per-tick rate. Accumulate drain each tick, drain 1 point when
  counter exceeds resistance. Matches OSRS exactly (verified against RSMod).

PRAYER CHECK AT RESOLVE TIME:
  Pending hits check the player's active prayer when the hit resolves (lands),
  not when the attack fires. This remains critical for Jad, but the agent now
  reacts through the same queued pending-hit signals used elsewhere rather than
  through a special Jad telegraph observation.

--------------------------------------------------------------------------------
2.2 What Worked
--------------------------------------------------------------------------------

C GAME BACKEND:
  ~2.0M training SPS with 4096 agents. FcState at ~2KB each means the entire
  working set fits in L2 cache. memset(0) reset is near-instant.

RAYLIB FOR VIEWER:
  Raylib 5.5 is a single .a file, no complex build deps. C API matches our
  C backend. Mesh upload via UploadMesh/UpdateMeshBuffer is straightforward.
  3D camera, text rendering, input handling all built in.

PUFFERLIB 4.0 OCEAN PATTERN:
  fight_caves.h + binding.c + build.sh. Clean interface: define OBS_SIZE,
  NUM_ATNS, ACT_SIZES, implement c_reset/c_step/c_close. PufferLib handles
  vectorization, GPU transfer, PPO, eval, wandb logging.

BINARY ASSET PIPELINE:
  Python decodes OSRS cache → writes binary (TERR, OBJ2, MDL2, ANIM, ATLS).
  C loads with simple fread(). No runtime cache parsing. Assets are ~45 MB
  total, load in <1 second.

VOID RSPS AS PORTING ORACLE:
  Kotlin source provided exact formulas, tick ordering, NPC stats, wave tables,
  projectile timing. We translated to C, not reverse-engineered. RSMod (rev 233)
  was better for generic OSRS mechanics (cleaner code, modern revision).

DEBUG OVERLAY SYSTEM:
  8-flag overlay system (collision, LOS, path, range, entity info, obs, mask,
  reward) proved essential for diagnosing NPC pathfinding bugs, combat timing
  issues, and reward function problems.

SMOOTH INTERPOLATION:
  Storing prev positions and blending with tick_frac gives 60 FPS visual
  fluidity from 2 TPS game ticks. Simple to implement, big visual payoff.

EVENT-DRIVEN HITSPLATS/PROJECTILES:
  Detecting state deltas after fc_step() (damage_taken_this_tick, pending hit
  count changes) to spawn visual effects. No render-side game logic.

OSRS b237 CACHE FOR ASSETS:
  Modern cache from openrs2.org has all Fight Caves models, terrain, animations.
  Flat-file format is easier to parse than older sector-chain .dat2. Model IDs
  use int32 (opcodes 6/7) — this differs from older revisions (uint16).

VERTEX-GROUP ANIMATION:
  OSRS animation system maps vertex labels (0-255) to transform slots, not
  bones. Simpler to implement than skeletal animation. anim_apply_frame()
  handles translate, rotate (Euler Z-X-Y), scale per vertex group.

CURRICULUM LEARNING:
  Light targeted curriculum remains useful for late-wave fine-tuning, but the
  later run history showed that heavy scratch curriculum can trap the policy.
  It should be used sparingly and only after a healthy scratch recipe exists.

QUADRATIC DAMAGE PENALTY:
  Linear damage penalty treated a 1 HP poke the same as a 50 HP Jad hit.
  Quadratic scaling (dmg_frac^2 * 70 * weight) makes big hits disproportionately
  punishing, teaching the agent that praying against Ket-Zek/Jad matters
  far more than avoiding Tz-Kih pokes.

--------------------------------------------------------------------------------
2.3 What Didn't Work
--------------------------------------------------------------------------------

KOTLIN/JVM APPROACH:
  500K+ LOC Void RSPS server was 99% irrelevant. JVM GC pauses destroyed
  deterministic timing. ctypes bridge to JVM was brittle. Throughput was
  orders of magnitude too low for RL.

RUNTIME CACHE DECODING IN C:
  Attempted to parse OSRS cache directly in C. Cache format is complex
  (GZIP-compressed archives, opcode-driven binary, multiple index files).
  Python handles this in 100 LOC; C would need 1000+. Abandoned in favor
  of offline Python export.

OBJECT DEFINITION PARSER MISSING OPCODES 21/22:
  contouredGround and delayShading are zero-byte flag opcodes. Missing them
  caused the parser to lose sync and silently discard 95.7% of objects
  (11,598 of 12,113 definitions). After fix: 205 → 4,382 object instances.

INITIAL NPC SIZES WRONG:
  Used guesses for NPC sizes. Actual sizes from Void 634 cache opcode 12:
  Tok-Xil=3 (not 2), Yt-MejKot=4 (not 2), Ket-Zek=5 (not 3), Jad=5 (not 3).
  Wrong sizes broke pathfinding, safespots, and collision for all large NPCs.

CAMERA PITCH 1.1 RAD:
  Initial camera was nearly top-down (63 degrees). OSRS default is 35 degrees
  (0.6 rad from horizontal). Required oracle comparison to discover.

TERRAIN HEIGHT FORMULA h * -1/128:
  Should be h * -1/16 (8x larger). -1/128 made terrain nearly flat with no
  visible relief. Discovered by comparing to oracle screenshots.

RAYLIB DEFAULT ALPHA BLENDING:
  DrawMesh does NOT call glEnable(GL_BLEND). Vertex alpha values have no
  effect. Required explicit two-pass rendering (opaque first, then blended
  with depth mask disabled).

EXPLICIT PRAYER REWARDS (v3):
  Added per-NPC prayer correctness rewards. Bug: reward fired every tick an
  NPC existed, not per-hit. Caused massive inflation (episode return 561 → 3682).
  Agent learned to idle with prayer on. v5 removed all explicit prayer rewards —
  let the quadratic damage_taken penalty teach prayer organically.

HORIZON 512 (v3):
  Doubled horizon from 256 to 512 hoping to capture longer temporal structure.
  SPS dropped from 2.2M to 1.5M (32% slower). No improvement in wave reached.
  Reverted to 256.

TICK CAP 30K HIDING DEGENERATE STRATEGY:
  v6 agent hit 30K tick cap and we assumed it was still fighting. Raising cap
  to 200K (v6.1) revealed agent survives indefinitely without clearing waves.
  It learned that surviving at -0.001/tick is better than dying at -20.

DEATH PENALTY TOO HIGH (-20):
  Makes the agent overly conservative. Any strategy that avoids death
  dominates, even if it means never clearing waves. Need to reduce death
  penalty or add wave stall penalty.

STATIC ARRAYS IN MULTITHREADED CODE:
  bfs_walk() in fc_pathfinding.c used static arrays for BFS scratch space
  (visited, queue, parent directions). PufferLib runs c_step() for 4096
  envs in parallel via OpenMP. All threads wrote to the same arrays —
  data race causing segfault. The bug was latent for months because the
  collision file was never loaded (see below), so BFS was never called.
  Once real collision was added, BFS ran constantly and threads clobbered
  each other. Fix: make arrays local (stack). Rule: NEVER use static
  mutable state in any function called from c_step/c_reset.

COLLISION FILE SEARCH PATH:
  fc_state.c searched for fightcaves.collision at relative paths like
  "assets/fightcaves.collision" and "../demo-env/assets/...". PufferLib
  runs from its own directory (pufferlib_4/), where none of these paths
  exist. setup_arena() silently fell back to an open arena — all tiles
  walkable, no walls. v1-v6.1 all trained on a flat open arena. The agent
  walked through visual terrain because walls literally didn't exist in
  the training simulation. Current code caches the collision globally and
  probes multiple repo-relative paths plus FC_COLLISION_PATH, but this
  should still be simplified during the shared-core refactor.

--------------------------------------------------------------------------------
2.4 References Used
--------------------------------------------------------------------------------

VOID RSPS (rev 634, Kotlin):
  Location: runescape-rl/reference/legacy-rsps/
  Used for: FC encounter scripting (TzhaarFightCave.kt), wave definitions
    (tzhaar_fight_cave_waves.toml), NPC stats (tzhaar_fight_cave.npcs.toml),
    attack patterns (tzhaar_fight_cave.combat.toml), Jad timing/style logic,
    healer behavior, prayer drain (PrayerDrain.kt), combat formulas
    (CombatApi.kt), pathfinding (PathFinder.kt), inventory/equipment.
  Authoritative for FC-specific mechanics.

RSMOD (rev 233):
  Location: rsmod/ (referenced in memory)
  Used for: b237 cache format, combat formulas (independent verification),
    tick ordering (GameCycle.kt), collision flags, prayer drain (confirmed
    counter-based system matches Void). Better than Void for generic OSRS
    mechanics — cleaner code, modern revision.

OSRS b237 CACHE:
  Source: openrs2.org archive #2509 (2026-04-01 snapshot)
  Used for: terrain mesh, placed objects, NPC models, animation sequences,
    texture atlas. Flat-file format (not sector-chain .dat2).
  Critical fix: opcodes 6/7 read int32 model IDs (RuneLite commit c2e1eb3).

PUFFERLIB 4.0:
  Location: /home/joe/projects/runescape-rl-reference/pufferlib_4/
  Used for: training framework (PPO, vectorized env, wandb logging),
    ocean pattern (fight_caves.h + binding.c structure), vecenv.h interface.
  Key examples: ocean/template/ (minimal), ocean/convert/ (multi-head),
    ocean/snake/ (complex game logic).

PUFFERLIB OSRS PvP:
  Used for: flat state struct pattern, XORshift32 RNG, multi-head action space,
    Raylib viewer integration approach, binding.c conventions.

VOID RSPS HEADED CLIENT:
  Location: /home/joe/projects/runescape-rl-reference/
  Used for: visual oracle comparison. Server + client can be launched locally
  for screenshot comparison of terrain, objects, camera, lighting.

RUNELITE (rev 237):
  Used for: cache loader reference, opcode documentation. Confirmed int32
  model IDs for b237 format.

--------------------------------------------------------------------------------
2.5 Training History (v1–v18)
--------------------------------------------------------------------------------

All configs and detailed results in docs/rl_config.md.

  v1 (500M steps, 3m43s): Wave 27.6 avg. Clipfrac=0, LR too low. Agent
    plateaus at Ket-Zek (magic + melee). No prayer reward for non-Jad NPCs.

  v2 (2.5B steps, 19min): Wave 28.2. LR 3x higher, ent_coef 5x higher.
    Still clipfrac=0. Added food waste penalty scaling. Wave ceiling unmoved.
    Root cause: no incentive to pray against Ket-Zek.

  v3 (1.7B steps, stopped): Wave 15.2 (CRASHED). Prayer reward bug: fired
    every tick per NPC, not per-hit. Episode return inflated 561→3682.
    Agent idled with prayer on. Horizon 512 slowed SPS to 1.5M.

  v4 (not trained, superseded by v5): Fixed prayer reward to per-hit only.
    Reduced prayer weights. Reverted horizon to 256.

  v5 (5B steps, 37min): Wave 30.0. Removed ALL explicit prayer rewards.
    Let damage_taken penalty teach prayer organically. Episode length doubled
    (3101→5056 ticks). But clipfrac still ~0. Agent converged to local optimum.

  v6 (2.5B/5B steps, 19min): Wave 30.0 (same wall). Added hit_style, LOS,
    target observations. Quadratic damage penalty. Curriculum at wave 28.
    Episode length hit 30K tick cap. Clipfrac finally non-zero (0.051).
    MinGRU network (PufferLib default) gives recurrent state.

  v6.1 (2B steps, 15min): Wave 30.0. Raised tick cap to 200K. Agent survives
    199,939 ticks without dying — degenerate strategy confirmed. Surviving
    at -0.001/tick dominates dying at -20. Episode return negative (-135).
    Value loss near 0 — agent perfectly predicts its expected return.

  v15.2: Heavy late-wave scratch curriculum trapped the policy and hurt
    general learning.

  v16.2a (`ge5sma5y`): First strong clean frontier baseline on this code line.
    Reached wave 28.8 avg, max wave 31, with real prayer switching and damage
    blocking competence.

  v17 (`mv0snohb`): Warm-start run on the new backend package. Held onto some
    inherited competence but underperformed the best scratch baseline.

  v17.1 (`q3ald8bc`): Scratch control for the `v17` recipe. Flatlined badly
    around wave 17 and proved the regression was in the training recipe.

  v18 (`lxttb7uo`): Clean scratch recovery run on the current pruned backend /
    pruned observation contract. Reached wave 29.2 avg, max wave 31, and
    established the current `codex3` baseline.

PROGRESSION: early 27-30 wave runs → reward bug crash → stall-detection era →
  strong wave-29 frontier on current codebase.
CURRENT BLOCKER: the project is no longer stuck on survival-vs-progress.
  The current blocker is breaking through the late-wave plateau around the
  wave 29-31 region without destabilizing the recovered baseline.

--------------------------------------------------------------------------------
2.6 Technical Gotchas
--------------------------------------------------------------------------------

  1. NPC sizes were wrong. Tok-Xil=3, Yt-MejKot=4, Ket-Zek=5, Jad=5.
     Not 2/2/3/3. Critical for pathfinding/safespots.
  2. Potion delay is 2 ticks, not 3. Food is 3. Separate clocks.
  3. Prayer drain is counter-based (accumulate → threshold → drain 1).
  4. Camera pitch 1.1 rad was wrong. Oracle default: 0.6 rad (35 degrees).
  5. Object def parser missing opcodes 21/22 → 95.7% objects discarded.
  6. Raylib DrawMesh doesn't enable GL_BLEND. Need explicit two-pass.
  7. Object 9383 is a ceiling (Y=7.5 tiles up), not a floor tile.
  8. Terrain height h * -1/128 wrong. Correct: h * -1/16 (8x larger).
  9. Terrain crop at 63 was off-by-one. Arena is 64 tiles (0-63 inclusive).
  10. VoidPS cache uses non-standard floor opcodes that standard decoders miss.
  11. b237 model opcodes 6/7 read int32 (not uint16 as older revisions).
  12. NPCs walk through each other (no NPC-NPC collision in FC).
  13. Jad prayer is still checked at RESOLVE time, but Jad telegraph state
      and telegraph observations have been removed. The policy now learns from
      queued pending-hit timing/style instead of a Jad-only wind-up channel.
  14. Tz-Kek counts as 2 in wave remaining counter (for split spawns).
  15. Healer respawn requires Jad healed back above 50% first.
  16. Prayer reward per-tick-per-NPC bug crashed training (v3). Must be per-hit.
  17. anneal_lr=1 (PufferLib default) decays LR toward 0. May stall late
      training. Consider anneal_lr=0 if agent stops improving.
  18. BFS pathfinding used static arrays (vis, qx, qy, pdx, pdy) shared
      across OpenMP threads. With open arena BFS rarely triggered so the
      data race was latent. Loading real collision caused constant BFS
      calls → segfault from concurrent writes. Fix: local stack arrays.
  19. Collision file search path was a real failure mode. Earlier training
      silently fell back to an open arena when the asset could not be found.
      Current code caches collision globally and probes several repo-relative
      locations plus `FC_COLLISION_PATH`, but the shared-core refactor should
      still collapse this to one unambiguous asset location.
  20. PufferLib config lives at pufferlib_4/config/fight_caves.ini, NOT
      our repo's config/fight_caves.ini. Edits to the repo copy have no
      effect unless synced to the PufferLib directory.

================================================================================
PART 3: REPRODUCTION GUIDE
================================================================================

How to replicate this architecture for a different boss/zone (e.g., Zulrah,
Inferno, Gauntlet).

--------------------------------------------------------------------------------
3.1 Porting a New Boss/Zone
--------------------------------------------------------------------------------

  1. IDENTIFY THE ENCOUNTER:
     Pick the boss/zone. Find it in the Void RSPS reference:
       runescape-rl/reference/legacy-rsps/game/.../content/area/...
     Find the TOML data files:
       data/minigame/<name>/ or data/boss/<name>/
     Note: Void is rev 634. NPC IDs, item IDs, animation IDs will differ
     from modern OSRS cache.

  2. DEFINE THE STATE (fc_types.h equivalent):
     Enumerate NPC types, attack styles, phases.
     Define player struct (stats, equipment, consumables for that encounter).
     Define NPC struct (type-specific fields: phase, telegraph, special).
     Define state struct (entities, wave/phase tracking, terminal conditions).
     Use flat structs with no pointers. Size < 4KB for cache efficiency.

  3. PORT THE GAME LOGIC (fc_tick.c, fc_combat.c, fc_npc.c equivalents):
     Translate Kotlin → C. Don't fork the RSPS — it's an oracle, not a base.
     Key systems to port:
       - Tick loop ordering (from GameTick.kt / GameCycle.kt)
       - Combat formulas (from CombatApi.kt, Hit.kt)
       - NPC AI (from encounter-specific Kotlin files)
       - Prayer/prayer drain (from PrayerDrain.kt)
       - Consumables (item-specific timing and effects)
       - Phase/wave progression
       - Terminal conditions
     Cross-reference with RSMod for generic mechanics.

  4. DEFINE THE RL INTERFACE (fc_contracts.h equivalent):
     Observation: player features + entity features + meta. Normalize [0,1].
     Action heads: what can the player do? Keep heads independent.
     Reward features: per-tick signals the Python trainer can weight.
     Action mask: which actions are legal this tick.
     Start with fewer heads and a smaller obs space. Expand if needed.

  5. EXTRACT NPC STATS:
     From Void 634 TOML configs: HP, attack speed, max hit, size, style.
     VERIFY sizes against cache opcode 12 — don't guess.
     Map Void 634 IDs → internal enums. Never use raw cache IDs in sim.

  6. BUILD COLLISION MAP:
     From b237 cache collision flags for the boss arena region, or manually
     define walkable area. Export as binary (64x64 uint8 or whatever size).

  7. ADD DETERMINISTIC RNG:
     Single XORshift32 state. Seed at reset. All randomness flows through it.
     This enables reproducible episodes.

  8. WRITE TESTS:
     Determinism: same seed + actions → identical state hash.
     Parity: compare key formulas against RSPS reference values.
     Coverage: all NPC types, all phases, terminal conditions.

--------------------------------------------------------------------------------
3.2 Asset Export Workflow
--------------------------------------------------------------------------------

  1. OBTAIN CACHE:
     Download from openrs2.org. Pick the most recent OSRS live cache.
     Extract to /tmp/osrs_cache_modern/cache/.

  2. IDENTIFY THE REGION:
     Boss arenas have specific region IDs. FC is region (37,79).
     Use RuneScape wiki or OSRS map tools to find region coords.

  3. EXPORT TERRAIN:
     Python script reads cache index 5 (maps), decodes heightmap and overlay
     colors for the region. Outputs TERR binary.

  4. EXPORT OBJECTS:
     Script reads cache index 5 (location data) + index 2 (object defs) +
     index 7 (models). Resolves object placements, loads models, applies
     recoloring (opcode 40), writes OBJ2 binary + ATLS texture atlas.
     WATCH: opcodes 21/22 are zero-byte flags. Missing them drops 95%+ objects.

  5. EXPORT NPC MODELS:
     Look up NPC definition IDs in b237 cache (index 2, archive 9).
     Map to model IDs. Export models as MDL2 binary with base_verts and
     vertex_skins for animation support.
     Scale: OSRS units ÷ 128 = tile units. Negate Z for right-handed coords.

  6. EXPORT ANIMATIONS:
     Script reads cache index 0 (framebases) + index 2 (sequences).
     Exports as ANIM binary. Verify sequence IDs against RSPS anim TOML.

  7. EXPORT COLLISION:
     From cache collision flags or from manual walkability definition.
     Output as raw binary (width*height uint8 array).

  8. VERIFY:
     Load assets in Raylib viewer. Compare against headed RSPS oracle.
     Check: terrain shape, object placement, NPC model appearance,
     animation playback, collision accuracy.

--------------------------------------------------------------------------------
3.3 PufferLib Integration Checklist
--------------------------------------------------------------------------------

  1. Create <encounter>.h:
     Define Env struct (embed your state struct).
     Include Log log, float* observations/actions/rewards/terminals.
     Include reward weight fields, curriculum config.
     Implement c_reset(), c_step(), c_close(), c_render() (no-op for headless).

  2. Create binding.c:
     #define OBS_SIZE, OBS_TENSOR_T, NUM_ATNS, ACT_SIZES, OBS_TYPE, ACT_TYPE
     #define Env <YourStruct>
     #include "vecenv.h"
     Implement my_init() (parse config), my_log() (export metrics).

  3. Create config/<encounter>.ini:
     [base] env_name, [env] reward weights + curriculum,
     [vec] total_agents + num_buffers,
     [train] timesteps + hyperparameters,
     [policy] hidden_size + num_layers.

  4. Create build.sh:
     Compile your .h + binding.c + backend sources against PufferLib headers.
     Output _C.so Python extension.

  5. Test standalone:
     Write <encounter>.c that runs N episodes with random actions.
     Verify: episodes terminate, observations populated, rewards non-zero.

  6. Train:
     cd pufferlib_4 && puffer train <encounter>
     Check: obs shape, action shape, rewards non-zero, episodes auto-reset.
     Monitor wandb: score, episode_return, wave_reached (or equivalent).

  7. Start with dense rewards:
     Reward many small things (damage dealt, kills) so agent gets learning
     signal early. Sparse rewards (only terminal) make learning near-impossible
     for long-horizon games.

  8. Watch for degenerate strategies:
     If agent survives forever without progressing: add wave stall penalty
     or reduce death penalty. If agent idles with prayer: check reward
     function for per-tick inflation bugs.

  9. Iterate reward weights:
     Train → observe → diagnose → adjust. Document every config change
     and result. The reward function is the hardest part to get right.

--------------------------------------------------------------------------------
3.4 Dependencies & Tooling
--------------------------------------------------------------------------------

Everything required to build and run this project from scratch.

  SYSTEM:
    Linux (Ubuntu 22.04+ / 24.04 tested)
    GCC 12+ (C11 support required, tested with GCC 13)
    CMake 3.20+
    make or ninja

  C BACKEND (no external deps beyond system libs):
    libc (stdio, stdlib, string, stdint, math)
    libm (-lm, linked by CMake)
    libpthread (-lpthread, for Raylib)

  RAYLIB 5.5 (demo-env viewer only):
    Prebuilt libraylib.a included in repo at demo-env/raylib/
    Headers at demo-env/raylib/include/
    Runtime deps: X11, OpenGL (Mesa), libdl
    Packages: libx11-dev, libxrandr-dev, libxinerama-dev, libxcursor-dev,
              libxi-dev, libgl-dev (or mesa-common-dev)
    NOT required for headless training — only for the viewer.

  PYTHON 3.11+ (3.12 used):
    virtualenv or venv for isolated environment
    pip for package management

  PYTHON PACKAGES:
    torch (PyTorch 2.x, CUDA or CPU)
    numpy
    pufferlib==4.0.0 (PufferLib RL framework)
    wandb (Weights & Biases for experiment tracking)
    rich (terminal formatting, used by PufferLib)

  CUDA (GPU training only):
    CUDA Toolkit 12.x (nvcc compiler)
    cuDNN 8.x or 9.x
    NVIDIA GPU with compute capability 7.0+ (RTX 2000+ series)
    nvidia-driver package
    NOT required for CPU-only training (use build.sh --cpu)

  PUFFERLIB 4.0:
    Source: /home/joe/projects/runescape-rl-reference/pufferlib_4/
    Headers needed: src/vecenv.h (included at build time via build.sh)
    Install: pip install -e . (dev mode from pufferlib_4 repo)
    Or: pip install pufferlib==4.0.0

  OSRS CACHE (asset export only):
    Download from openrs2.org (OSRS live, b237 or newer)
    ~200 MB compressed, ~500 MB extracted
    Extract to /tmp/osrs_cache_modern/cache/
    Only needed when re-exporting assets. Pre-exported binaries are
    committed in demo-env/assets/ and training-env/assets/.

  ASSET EXPORT SCRIPTS (Python, for re-exporting from cache):
    Located at: runescape-rl-reference/valo_envs/ocean/osrs/scripts/
    Python deps for export: struct, gzip, zlib, PIL/Pillow (for atlas)
    Only needed if modifying assets or porting to a new zone.

  VOID RSPS REFERENCE (porting oracle, read-only):
    Located at: runescape-rl/reference/legacy-rsps/
    Kotlin source, not compiled or executed during normal development.
    JDK 21 required only if running the headed oracle for visual comparison:
      openjdk-21-jdk, gradlew (included in RSPS repo)

  HEADED ORACLE (optional, visual comparison only):
    JDK 21 (for Void RSPS server)
    JDK 8 compatibility (client compiled with --release 8)
    X11 display (for Swing client window)
    Only used for screenshot comparison during development.

  WANDB (experiment tracking):
    Account at wandb.ai
    wandb login (API key, one-time setup)
    Logs training metrics: SPS, wave_reached, episode_return, etc.

  VERSION SUMMARY:
    GCC:        13.x
    CMake:      3.20+
    Raylib:     5.5 (prebuilt, in repo)
    Python:     3.12
    PyTorch:    2.x
    PufferLib:  4.0.0
    CUDA:       12.x (optional)
    cuDNN:      8.x/9.x (optional)
    OSRS cache: b237 (openrs2 archive #2509)
