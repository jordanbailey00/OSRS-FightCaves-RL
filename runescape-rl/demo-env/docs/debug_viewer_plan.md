================================================================================
FIGHT CAVES DEBUG VIEWER — PLAN
================================================================================

STATUS: Terrain and placed objects rendering from b237 OSRS cache. All 8 NPC
types spawned as colored placeholder cubes. Player as blue cylinder. Black floor.
Cave wall/cliff terrain renders with 3,867 objects, 26 unique models, 424K tris.
Headless sim backend running with random actions.

NEXT MILESTONE: Implement the full authoritative Fight Caves backend as a
playable game in the debug viewer, porting systems from the Void RSPS reference.

================================================================================
ARCHITECTURE — SELF-CONTAINED DEMO-ENV
================================================================================

Everything needed to build and run the debug viewer lives in demo-env/:

  demo-env/
    src/
      viewer.c                  — Raylib viewer shell (input, rendering, UI)
      osrs_pvp_terrain.h        — terrain mesh loader
      osrs_pvp_objects.h        — objects mesh loader + atlas
      fc_types.h                — core types, enums, constants
      fc_api.h                  — public API (init, reset, step, render)
      fc_sim.c                  — tick loop, action processing, episode control
      fc_map.c                  — collision map, LOS, pathfinding, tile queries
      fc_combat.c               — targeting, attack rolls, damage resolution
      fc_npc.c                  — NPC definitions, AI, movement, spawning
      fc_player.c               — player movement, stats, consumables
      fc_prayer.c               — prayer activation, drain, protection checks
      fc_inventory.c            — inventory/equipment state, item definitions
      fc_projectile.c           — projectile flight, impact timing
      fc_wave.c                 — wave definitions, spawn tables, encounter script
      fc_obs.c                  — observation, action mask, reward extraction
      fc_rng.c                  — seeded XORshift32 RNG
      fc_debug.c                — state hash, replay trace, debug instrumentation
      fc_ids.h                  — canonical internal IDs (NOT cache IDs)
    assets/
      fightcaves.terrain        — b237 terrain mesh (401 KB)
      fightcaves.objects        — b237 objects mesh (30 MB)
      fightcaves.atlas          — b237 texture atlas (15 MB)
    docs/
      debug_viewer_plan.md      — this file
      fc_backend_systems.md     — full system coverage reference
      fc_systems_audit.md       — audit report with exact formulas/values
    tests/
      (scenario and parity tests)

BACKEND RULE: The viewer (viewer.c) NEVER decides gameplay outcomes. It only:
  - Sends actions to fc_step()
  - Reads state from FcState / FcRenderEntity
  - Draws the result

All gameplay authority lives in the fc_*.c backend files.

CACHE: Modern OSRS b237 from openrs2.org (flat file format)
  - Source: cache-oldschool-live-en-b237-2026-04-01-10-45-07-openrs2#2509.tar.gz
  - Location: /tmp/osrs_cache_modern/cache/ (re-extract after reboot)
  - Model IDs use int32 (opcodes 6/7), not uint16 (opcodes 1/5)

COORDINATE SYSTEM:
  - Valo_envs convention: X=east, Y=up, Z=-north (right-handed)
  - terrain_offset(tm, 2368, 5056) shifts world coords to local
  - Entity Z is negated: ey = -(entity.y)
  - FC arena origin: world (2368, 5056) = region (37,79)

================================================================================
PORTING REFERENCES
================================================================================

The backend logic is ported from the Void RSPS (Kotlin). Two reference copies:

  LEGACY RSPS (full server):
    /home/joe/projects/runescape-rl/claude/runescape-rl/reference/legacy-rsps/

    Key files for porting:
      game/.../content/area/karamja/tzhaar_city/TzhaarFightCave.kt    — wave controller
      game/.../content/area/karamja/tzhaar_city/TzhaarFightCaveWaves.kt — wave spawner
      engine/.../mode/combat/CombatApi.kt                              — combat event flow
      engine/.../data/config/CombatDefinition.kt                       — attack definitions
      game/.../content/skill/prayer/Prayer.kt                          — prayer system
      game/.../content/skill/prayer/active/PrayerDrain.kt              — drain rates
      engine/.../entity/character/mode/move/PathFinder.kt              — pathfinding
      engine/.../entity/character/npc/NPC.kt                           — NPC state
      engine/.../inv/Inventory.kt                                      — inventory system

    Data files (TOML configs):
      data/minigame/tzhaar_fight_cave/tzhaar_fight_cave_waves.toml     — 63 waves, 15 rotations
      data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.npcs.toml      — NPC stats
      data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.combat.toml    — attack patterns
      data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.anims.toml     — animation IDs
      data/skill/prayer/prayers.toml                                   — prayer definitions

  CURRENT FC DEMO (RL-integrated headed demo):
    /home/joe/projects/runescape-rl-reference/current_fightcaves_demo/

    Key files:
      game/.../tzhaar_city/FightCaveBackendActionAdapter.kt            — action types
      game/.../tzhaar_city/FightCaveHeadedObservationBuilder.kt        — observation schema
      game/.../tzhaar_city/FightCaveEpisodeInitializer.kt              — episode config

================================================================================
ID TRANSLATION — VOID 634 vs OSRS b237
================================================================================

The Void RSPS backend uses cache revision 634 IDs. Our cache is OSRS b237.
IDs WILL differ for NPCs, items, animations, projectiles, and graphics.

STRATEGY: Use canonical internal enums (fc_ids.h), never raw cache IDs in sim.

  fc_ids.h defines:
    - FcNpcId     (NPC_TZ_KIH, NPC_TZ_KEK, etc.)
    - FcItemId    (ITEM_SHARK, ITEM_PRAYER_POTION_4, etc.)
    - FcAnimId    (ANIM_TZ_KIH_ATTACK, ANIM_JAD_MAGIC_WINDUP, etc.)
    - FcProjId    (PROJ_TOK_XIL_RANGED, PROJ_KET_ZEK_MAGIC, etc.)
    - FcGfxId     (GFX_HITSPLAT, GFX_PRAYER_DRAIN, etc.)

  When porting from RSPS reference, translate IDs like this:
    RSPS Void 634 NPC ID 2734 (tz_kih) → internal NPC_TZ_KIH enum
    RSPS animation IDs → look up equivalent in b237 NPC definitions
    RSPS projectile IDs → look up equivalent in b237 spotanim/projectile defs

  The viewer maps internal IDs → b237 cache IDs for rendering only.
  The sim core never touches cache IDs.

  REFERENCE for ID lookup:
    - Void 634 NPC IDs: data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.npcs.toml
    - b237 NPC defs: cache index 2, archive 9 (NPC definitions)
    - b237 item defs: cache index 2, archive 10 (item definitions)
    - RuneLite rev 237 loaders: /home/joe/projects/runescape-rl-reference/runelite/

  Void 634 → Internal mapping (NPCs, sizes from cache opcode 12):
    2734 → NPC_TZ_KIH      (Tz-Kih, lv22, size=1, melee, drains prayer)
    2736 → NPC_TZ_KEK      (Tz-Kek, lv45, size=2, melee, splits on death)
    2738 → NPC_TZ_KEK_SM   (Small Tz-Kek, lv22, size=1, split spawn)
    2739 → NPC_TOK_XIL     (Tok-Xil, lv90, size=3, ranged+melee)
    2741 → NPC_YT_MEJKOT   (Yt-MejKot, lv180, size=4, melee + heals nearby NPCs)
    2743 → NPC_KET_ZEK     (Ket-Zek, lv360, size=5, magic + melee)
    2745 → NPC_TZTOK_JAD   (TzTok-Jad, lv702, size=5, all styles, 8-tick attack)
    2746 → NPC_YT_HURKOT   (Yt-HurKot, lv108, size=1, heals Jad 50hp/4ticks)

  PLAYER FIXED LOADOUT (from FightCaveEpisodeInitializer.kt):
    Stats: Att=1, Str=1, Def=70, Con=700(70HP), Rng=70, Pray=43, Mag=1
    Equipment: Coif, Rune Crossbow, Black D'hide Body/Chaps/Vambraces, Snakeskin Boots
    Ammo: 1000 Adamant Bolts
    Inventory: 8 Prayer Potions (32 doses), 20 Sharks

================================================================================
WHAT WORKS NOW
================================================================================

1. Terrain floor: 8,192 triangles from b237 cache, heightmapped
2. Placed objects: 3,867 objects, 26 unique models, 424K triangles, texture atlas
3. Cave walls/cliffs: visible and mostly continuous around arena perimeter
4. All 8 NPC types: spawned as colored placeholder cubes with correct sizes
5. Player: blue cylinder at arena center
6. Camera: orbit (right-drag), zoom (scroll), presets (1=aerial, 2=gameplay)
7. UI: header bar, side panel (HP, prayer, wave, items), debug overlay
8. Grid: toggleable tile grid (G key)
9. Simulation: headless backend runs, random actions, wave progression

================================================================================
IMPLEMENTATION PHASES
================================================================================

See fc_backend_systems.md for the complete system coverage reference.
Systems are grouped by dependency order. Each phase produces a testable result.

--- PHASE 1: SELF-CONTAINED BACKEND CONSOLIDATION ---

Goal: Move all backend source into demo-env/src/ so demo-env builds standalone.

  1. Copy fc_types.h, fc_api.h, fc_contracts.h into demo-env/src/
  2. Copy all fc_*.c source files into demo-env/src/
  3. Update CMakeLists.txt to compile backend sources directly (remove fc_core dep)
  4. Verify: demo-env builds and runs identically to current linked version

This is a file reorganization, not new logic. training-env/ keeps its copy for
headless RL training. demo-env/ becomes independently buildable.

--- PHASE 2: AUTHORITATIVE SIMULATION FOUNDATION (Group 1) ---

Goal: Solid tick loop with deterministic state, action processing, and reset.

  Systems from fc_backend_systems.md Section 1A:
    - Fixed tick loop with defined phase ordering
    - Global simulation clock
    - Per-entity timers/cooldowns
    - Action queue with validation
    - Reset/episode restart
    - Seeded RNG (already exists in fc_rng.c)
    - State hash for determinism verification (already exists in fc_hash.c)

  Tick phase ordering (freeze this — matches RSPS GameTick.kt):
    1. Clear per-tick event flags (reset damage_this_tick, etc.)
    2. Process player action (one action per tick — from RL or human input)
    3. NPC AI: hunt/aggro check (every 4 ticks), target acquisition
    4. NPC combat: attack decisions, projectile spawning
    5. NPC movement: one step per tick toward target (size-aware)
    6. Player movement: one step per tick (or two if running)
    7. Decrement timers (attack cooldowns, food_delay, drink_delay)
    8. Resolve pending hits landing this tick (check prayer at RESOLVE time)
    9. Process deaths / splits / despawns
    10. Prayer drain tick (counter-based: accumulate, threshold, drain)
    11. HP regen tick (1 HP per 100 ticks if applicable)
    12. Check wave clear → advance wave (remaining == 0)
    13. Check terminal conditions (death, cave complete, tick cap)
    14. Emit state / fill render entities

  CRITICAL: NPC processing runs BEFORE player processing (matching RSPS).
  One action per tick enforced (LAST_ACTION_TICK_KEY).

  Port from: existing fc_tick.c + RSPS TzhaarFightCave.kt tick ordering

--- PHASE 3: MAP / COLLISION / PATHFINDING (Group 2) ---

Goal: Authoritative tile-based collision map separate from visual mesh.

  Systems from fc_backend_systems.md Section 1B:
    - 64x64 tile grid with walkability flags
    - Movement-blocked tiles (from placed objects / cave walls)
    - Line-of-sight (LOS) checks (Bresenham or similar)
    - Reachability queries
    - Multi-tile entity footprint support (Jad = 3x3, Ket-Zek = 3x3)
    - Arena boundaries
    - A* or BFS pathfinding (already exists in fc_pathfinding.c)
    - Size-aware pathing for large NPCs

  IMPORTANT: The collision map is gameplay-authoritative. The rendered terrain
  mesh is visual only. Safespots emerge naturally from correct collision.

  Build collision from: b237 cache collision flags for region (37,79), OR
  manually define walkable area from the known FC arena layout.

  Port from: existing fc_pathfinding.c + RSPS PathFinder.kt + RSMod collision

--- PHASE 4: ENTITY STATE & PLAYER SYSTEMS (Groups 3-4) ---

Goal: Full player state model with movement, stats, inventory, equipment, prayer.

  A. Player movement (Section 1E):
    - Click-to-tile intent → pathfind → one-step-per-tick progression
    - Walking vs running
    - Movement while under attack / while consuming
    - Arena boundary enforcement
    - Blocked tile handling

  B. Stats system (Section 1G):
    - Base stats, current stats, boosted/drained
    - HP, prayer points (already in FcPlayer)
    - Regen rules (HP regen every 100 ticks, etc.)
    - Death when HP reaches 0

  C. Inventory system (Section 1H):
    - Fixed 28-slot container
    - Sharks (heal 200 HP), prayer potions (restore formula)
    - Slot indexing, empty slot tracking
    - Item definitions as data table (not scattered in code)

  D. Equipment system (Section 1I):
    - Fixed Fight Caves loadout (no swaps for v1)
    - Equipment bonuses → attack/defence rolls
    - Weapon speed, attack style, range
    - Can expand to gear swaps in v2

  E. Prayer system (Section 1J):
    - Protect from melee/ranged/magic (already exists in fc_prayer.c)
    - Exclusive prayer groups (group 6: only one protect active)
    - Prayer drain: COUNTER-BASED system (not simple rate!)
      Each tick: counter += sum(active prayer drains)
      Resistance = 60 + (prayerBonus * 2)
      While counter > resistance: drain 1 point, counter -= resistance
      All three protects have drain = 12
    - Prayer bonus from equipment reduces drain rate (adds to resistance)
    - Prayer disabled when points reach 0
    - Tz-Kih drains exactly 1 prayer point per hit impact (flat)
    - Jad prayer timing: protection checked at hit RESOLVE time (tick 6 from attack)
      NOT at fire time. This is the FC demo behavior, not legacy RSPS.

  F. Consumables (Section 1R):
    - Eat shark: 3-tick food_delay, heals exactly 200 HP (flat)
    - Drink prayer potion: 2-tick drink_delay (NOT 3!), restores 7 + floor(43*0.25) = 17 pts
    - Food and potions use SEPARATE delay clocks — can eat+drink same tick
    - Cannot eat while food_delay active, cannot drink while drink_delay active
    - Can eat/drink + prayer switch on same tick (no interaction restriction)
    - Potions: 4 doses per potion, 8 potions = 32 doses default
    - Item removal/replacement on consume (potion → lower dose or vial)

  Port from: existing fc_types.h structs + RSPS Inventory.kt, Prayer.kt,
  PrayerDrain.kt, PrayerBonus.kt + RSPS combat.toml stats

--- PHASE 5: NPC AI & MOVEMENT (Group 2 continued) ---

Goal: Authentic NPC behavior — aggro, chase, attack, patrol, size-aware pathing.

  Systems from fc_backend_systems.md Section 1F:
    - Spawn at wave-defined positions
    - Aggro acquisition (aggressive_intolerant hunt mode — all players in LOS)
    - Chase logic: path toward player each tick
    - Size-aware pathing (Jad 3x3, Ket-Zek 3x3 cannot fit through narrow gaps)
    - Movement stop distance = attack range
    - NPC collision: NPCs CAN walk through each other (no NPC-NPC collision!)
    - Only terrain/object collision blocks NPCs
    - Target loss / reacquisition
    - Death cleanup / corpse timing

  Fight Caves specific:
    - Tz-Kek split: spawns 2 small Tz-Kek at death tile
    - Yt-MejKot: heals nearby NPCs with HP < 50% of max, 100 HP per heal
    - Yt-HurKot: follows Jad, heals 50 HP every 4 ticks when within 5 tiles
      Distracted when player attacks (switches to combat mode, stops healing)
    - Jad: weighted random style selection (magic/ranged), telegraph visible
      Attack speed: 8 ticks. Hit resolves 6 ticks from attack start.
      Healer spawn: 4 healers when Jad HP drops below 50%
      Respawn: only after Jad healed back above 50% and drops below again
    - Safespot behavior: emergent from correct collision + LOS + size-aware pathing
      (5x5 Ket-Zek and Jad cannot fit through gaps that smaller NPCs can)

  NPC stat table (from Void 634 cache + tzhaar_fight_cave.npcs.toml):
    Type          HP    Atk   Str   Def   Mag   Rng   Style    AtkSpd  Size  AtkRange  MaxHit
    Tz-Kih        100   20    30    15    15    30    melee    4       1     1         40
    Tz-Kek        200   40    60    30    30    60    melee    4       2     1         70
    Tz-Kek(sm)    100   20    30    15    15    30    melee    4       1     1         40
    Tok-Xil       400   80    120   60    60    120   rng+mel  4       3     1/14      130/140
    Yt-MejKot     800   160   240   120   120   240   mel+heal 4       4     1         250
    Ket-Zek       1600  320   480   240   240   480   mag+mel  4       5     1/14      540/490
    TzTok-Jad     2500  640   960   480   480   960   all      8       5     1/14      970/950
    Yt-HurKot     600   140   100   60    120   120   melee    4       1     1         140

  CRITICAL: Sizes from Void 634 cache (opcode 12, verified):
    Tz-Kih=1, Tz-Kek=2, Tok-Xil=3, Yt-MejKot=4, Ket-Zek=5, Jad=5, Yt-HurKot=1
    These large sizes (Ket-Zek and Jad are 5x5!) are critical for pathfinding/safespots.

  Port from: existing fc_npc.c + RSPS NPC.kt, hunt_modes.toml,
  tzhaar_fight_cave.combat.toml

--- PHASE 6: COMBAT FOUNDATION (Group 3) ---

Goal: Full attack pipeline — targeting, rolls, projectiles, damage, death.

  A. Target/engagement system (Section 1K):
    - Target selection (click NPC or auto-retaliate)
    - Target validity (alive, in range, LOS)
    - Attack initiation: approach if out of range, then attack
    - Attack cooldown (weapon speed in ticks)
    - Retargeting on target death

  B. Player combat (Section 1L):
    - OSRS accuracy formula (already in fc_combat.c):
      hit_chance = att_roll > def_roll
        ? 1 - (def_roll+2)/(2*(att_roll+1))
        : att_roll/(2*(def_roll+1))
    - Damage roll: uniform [0, max_hit]
    - Equipment/stat/prayer modifiers on att/def rolls
    - Ranged projectile spawn with travel delay
    - Hit delay by distance: 1 + floor((3 + dist) / 6) for ranged

  C. NPC combat (Section 1M):
    - Per-type attack style and damage from stat table
    - Melee: instant hit (0 delay)
    - Ranged (Tok-Xil): projectile, delayed hit
    - Magic (Ket-Zek): projectile, delayed hit
    - Jad: style selection (alternating magic/ranged), telegraph, delayed hit
    - Prayer interaction: correct protect blocks 100% in PvM

  D. Projectile system (Section 1N):
    - Spawn projectile at source position
    - Travel time: CLIENT_TICKS.toTicks(offset + distance * multiplier) + 1
      Where toTicks(n) = n / 30 (integer floor division)
    - Per-projectile timing (from gfx.toml):
      tok_xil_shoot:   delay=32, default multiplier=5
      ket_zek_travel:  delay=28, offset=8, multiplier=8
      tztok_jad_travel: delay=86, multiplier=8
      Jad ranged: no projectile, fixed delay=120 → 5 game ticks
    - Prayer check at IMPACT tick (not launch tick)
    - Despawn on impact
    - Target tracking (projectile follows moving target)

  JAD ATTACK TIMELINE (critical for RL):
    Tick 0: Attack animation starts (telegraph visible)
    Tick 3: hit() called (damage rolled, projectile spawned)
    Tick 6: Damage resolves, prayer checked (3 + toTicks(64) + 1 = 3+2+1 = 6)
    Total: 6 game ticks from telegraph to hit = 3.6 seconds

  E. Damage / hit resolution (Section 1O):
    - Pending hit queue (already in FcPendingHit struct)
    - Resolve at ticks_remaining == 0
    - Check active prayer at resolve time
    - Multiple hits can land same tick
    - HP floor = 0 → death trigger
    - Damage splat generation for viewer

  F. Death / despawn (Section 1P):
    - NPC death → remove after death delay (2-3 ticks)
    - Tz-Kek death → spawn 2 small Tz-Kek
    - Player death → terminal state
    - Cleanup pending hits/projectiles referencing dead entities
    - Wave clear detection: npcs_remaining == 0

  Port from: existing fc_combat.c + RSPS CombatApi.kt, CombatDefinition.kt,
  CombatAttack.kt, tzhaar_fight_cave.combat.toml

--- PHASE 7: WAVE / ENCOUNTER SCRIPTING (Group 5) ---

Goal: All 63 waves with 15 rotations, spawned from data tables.

  Systems from fc_backend_systems.md Section 1S:
    - Wave index 1..63
    - Spawn list per wave (from tzhaar_fight_cave_waves.toml)
    - 15 spawn rotations with directional positions
    - Spawn timing (immediate on wave start)
    - Wave complete detection (all NPCs dead, counting Tz-Kek splits)
    - Transition to next wave
    - Wave 63: TzTok-Jad spawn
    - Jad half-HP: spawn 4 Yt-HurKot healers
    - Terminal: cave complete after wave 63 cleared

  EXISTING: fc_wave.c already has wave data. Verify against RSPS TOML:
    data/minigame/tzhaar_fight_cave/tzhaar_fight_cave_waves.toml (50,967 bytes)
    Contains all 63 waves with exact NPC counts and 15 rotation spawn positions.

  Port from: existing fc_wave.c + RSPS TzhaarFightCaveWaves.kt + waves TOML

--- PHASE 8: VIEWER INTEGRATION — PLAYABLE GAME (Group 7) ---

Goal: Human-playable Fight Caves in the debug viewer.
Status: PARTIALLY DONE — sub-phases below track remaining work.

--- PHASE 8a: FIX MOVEMENT — CLICK-TO-MOVE ---

Problems identified:
  - WASD directional movement is unnatural. OSRS uses click-to-move.
  - Player keeps moving when no input (if auto-mode accidentally toggled).
  - No way to click a tile and have the player walk there.
  - Movement should STOP when player reaches destination or enters combat.

How Void RSPS does it (FightCaveBackendActionAdapter.kt):
  - Wait action (0) = complete no-op, player stands still
  - WalkToTile action (1) = pathfind to (x,y) once, walk one step per tick
  - Movement mode clears when destination reached → player stops

How RSMod does it (PlayerMovementProcessor.kt):
  - Click creates RouteRequest → pathfinder computes route once
  - Each tick: consume one step from route deque
  - When deque empty: player stops. No persistent movement.

Fix plan:
  1. Add route deque to FcPlayer (destination + path steps)
  2. Click tile in viewer → set player route destination
  3. Each tick: if route has steps, consume one step (walk toward destination)
  4. If route empty or destination reached: player stands still
  5. Remove WASD directional movement (or keep as secondary option)
  6. New action: clicking on the 3D terrain → raycast to tile → set destination
  7. Player stops moving when entering combat range of target

--- PHASE 8b: FIX COMBAT BEHAVIOR ---

Problems identified:
  - NPCs and player both keep moving during combat instead of being stationary
  - NPCs should stop moving once in attack range and attack on cooldown
  - Player should face target and auto-attack, not wander around
  - Hitsplats are 0.12-radius spheres — invisible at normal camera distance
  - No damage NUMBER text, just tiny colored dots
  - No attack animation indication

How Void RSPS does combat:
  - Player clicks NPC → enters CombatMovement mode
  - CombatMovement: approach target until in attack range, then STOP
  - Once in range: attack on cooldown, remain stationary
  - Player only moves if explicitly clicking a new tile
  - Hitsplats: show damage number above entity head

Fix plan:
  1. NPC behavior: once in attack range AND attack timer ready, STOP moving.
     Only resume chasing if player moves out of range.
  2. Player attack: when target selected (Tab or click NPC), player walks
     toward NPC until in weapon range (7 tiles for crossbow), then STOPS
     and auto-attacks on cooldown.
  3. Hitsplats: render as 2D text overlay (DrawText in screen space projected
     from 3D position), not 3D spheres. Show damage number in red/blue.
     Make them large enough to read (font size 16+).
  4. Attack indication: brief flash or pulse on attacker when they swing.

--- PHASE 8e: VISUAL PROJECTILES WITH ACTUAL MODELS ---

Projectile model IDs (from osrs-dumps dump.spot):
  Crossbow bolt:           model 3135 (crossbowbolt_travel, spotanim 27)
  Tok-Xil spine:           model 9340 (tzhaar_spine_attack, spotanim 443)
  Ket-Zek fire launch:     model 9335 (tzhaar_fire_launch_travel, spotanim 445)
  Jad ranged fire:         model 6434 (tzhaar_ranged_fire_attack, spotanim 440)
  Jad magic fireball:      model 9334 (tzhaar_firebreath_attack, spotanim 439)
  Yt-MejKot heal:          model 9341 (tzhaar_heal, spotanim 444)

Implementation:
  1. Export projectile models from b237 cache (same pipeline as NPC models)
  2. Load as separate model set in viewer
  3. Replace colored sphere projectiles with actual 3D model instances
  4. Rotate projectile to face travel direction
  5. Scale per spotanim definition (resizeh/resizev)

--- PHASE 8e-old: VISUAL PROJECTILES ---

Requirements:
  - Ranged/magic attacks show an actual projectile moving from attacker to target
  - Projectile travels at the correct speed per NPC/weapon type (from gfx.toml timing)
  - Player crossbow bolt: colored sphere moving from player to NPC
  - Tok-Xil ranged: projectile from NPC to player
  - Ket-Zek magic: blue projectile from NPC to player
  - Jad magic/ranged: large projectile from Jad to player
  - Melee attacks: no projectile (just hitsplat on impact)
  - Projectile is a small colored sphere that lerps from source to dest over the hit delay ticks
  - Multiple projectiles can be in flight simultaneously

Implementation:
  - Add VisualProjectile struct: source xyz, dest xyz, color, total_ticks, elapsed_ticks
  - When fc_tick queues a pending hit with delay > 1, viewer spawns a VisualProjectile
  - Each frame: lerp projectile position from source to dest based on elapsed/total
  - Draw as colored sphere in 3D
  - Remove when elapsed >= total (hit has landed)

--- PHASE 8g: ANIMATIONS (NPC + PLAYER) ---

Requirements:
  - NPCs and player should animate: idle, walk, attack, death
  - Animations should match the correct game tick timing
  - Jad attack telegraph should have visible wind-up animation

How OSRS animations work:
  - NOT skeletal — uses vertex group labels (0-255 per vertex)
  - FrameBase: defines transform slots (translate, rotate, scale) and which vertex labels they affect
  - FrameDef: per-frame dx/dy/dz values for each transform slot
  - SequenceDef: chains frames with tick delays + body part interleave

Existing infrastructure (valo_envs):
  - export_animations.py: full decoder for sequences, frames, framebases from cache
  - export_inferno_npcs.py: already exports NPC animations to .anims binary
  - osrs_pvp_anim.h: PRODUCTION C code for animation playback in Raylib!
    - anim_apply_frame(): applies frame transforms to vertices
    - anim_update_mesh(): updates Raylib mesh vertices from animated state
    - Complete load/decode/playback pipeline

FC NPC animation IDs (from osrs-dumps seq.sym, verified):
  Tz-Kih (firebat):     idle=2618 walk=2619 attack=2621 defend=2622 death=2620
  Tz-Kek (lavabeast):   idle=2624 walk=2623 attack=2625 defend=2626 death=2627
  Tok-Xil (magmaquris): idle=2631 walk=2632 melee=2628 ranged=2633 defend=2629 death=2630
  Yt-MejKot (lizard_cleric): idle=2636 walk=2634 attack=2637 defend=2635 death=2638 heal=2639
  Ket-Zek (igniferum):  idle=2642 walk=2643 melee=2644 magic=2647 defend=2645 death=2646
  TzTok-Jad (lordmagmus): idle=2650 walk=2651 melee=2655 magic=2656 defend=2653 death=2654
  Jad (jaltokjad):      idle=7589 walk=7588 melee=7590 magic=7592 ranged=7593 defend=7591 death=7594
  Yt-HurKot (lizard_cleric): idle=2636 walk=2634 attack=2637 defend=2635 death=2638 heal=2639

  Note: lordmagmus (seq 2650-2656) and jaltokjad (seq 7588-7594) are DIFFERENT animation sets.
  The NPC dump says Jad (3127) uses lordmagmus anims. Verify which set the b237 cache actually uses.

Implementation plan:
  1. Write export_fc_anims.py (adapt export_inferno_npcs.py for FC NPC + player anims)
     - Export all FC NPC idle/walk/attack/death sequence IDs
     - Export player attack/walk animations
     - Output fc_anims.anims binary
  2. Copy osrs_pvp_anim.h into demo-env/src/ (it's already production C code)
  3. Load .anims binary at viewer startup alongside .models
  4. Per entity per tick: select animation based on state (idle/walk/attack/death)
  5. Per render frame: interpolate between animation frames, apply transforms, update mesh
  6. Upload updated mesh to GPU each frame (UploadMesh or UpdateMeshBuffer)

Animation state per entity (add to viewer):
  - current_seq_id (which animation is playing)
  - frame_index (current frame within sequence)
  - frame_timer (ticks remaining in current frame)
  - base_verts (copy of original un-animated vertices for reset each frame)

--- PHASE 8f: PLAYER CHARACTER MODEL ---

Requirements:
  - Replace blue cylinder with actual player model wearing FC equipment
  - Player model is a composite of base body kit + equipment worn models
  - Each equipment slot has a "worn model" ID in the item definition

Challenge:
  - b237 live cache has obfuscated item names (all blank)
  - Item IDs 1169, 9185, 2503, 2497, 2491, 6328 exist but names are stripped
  - Worn model IDs (opcode 23/25) may still be valid even without names
  - Need to extract worn models from item defs at the known IDs
  - Player body is composed of: base body + head + torso + arms + legs + feet + weapon
  - Base body kit model IDs are in the player kit definitions (index 2, group 3)

Options:
  1. Extract worn models from Void 634 cache (has correct item data)
  2. Try b237 item IDs directly (opcode 23 may have model IDs despite blank names)
  3. Use a pre-made generic player model as placeholder
  4. Export from the Void 634 cache using Kotlin tools

--- PHASE 8c: FIX NPC SPAWN POSITIONS ---

Problems identified:
  - Wave 4 Tz-Kek sometimes spawns behind a cliff (unreachable)
  - find_valid_spawn fallback uses original position even if invalid
  - NPC stuck behind cliff: can't reach player, player can't reach NPC
  - This blocks wave progression entirely

Fix plan:
  1. Increase find_valid_spawn search radius from 5 to 10
  2. If still no valid position found, spawn at CENTER (32,32) as last resort
  3. Verify all 5 spawn positions work for all NPC sizes (1-5)
  4. Add debug logging when spawn fallback triggers

--- PHASE 8d: NPC MODEL RENDERING (from Phase 10) ---

Problems identified:
  - All NPCs render as identical colored cubes
  - Hard to tell NPC types apart at distance
  - Need actual NPC meshes from b237 cache

This is Phase 10 work pulled forward because it's blocking usability.
  1. Export NPC models for each FC NPC type from b237 cache
  2. Load as mesh assets in viewer
  3. Render at entity position with correct size/rotation
  4. Fallback to colored cubes if model not available
    - Prayer icon next to player HP bar
    - Attack animation indicators (flash/pulse on attack tick)

  C. UI panels (reflecting backend state only):
    - Inventory panel: shark count, potion doses
    - Prayer panel: active prayer highlighted, prayer points
    - Combat panel: current target, attack timer
    - Wave display: current wave, NPCs remaining
    - Stats: HP, prayer, total damage, kills

  D. Tick controls:
    - Configurable TPS (1-60, default: game speed ~1.67 TPS)
    - Pause/unpause (space)
    - Single-step (right arrow while paused)
    - Fast-forward (hold shift + space)

--- PHASE 9: DEBUG TOOLING & OVERLAYS (Group 8) ---

Goal: Make the viewer a powerful debugging tool for RL development.

  A. Collision/LOS overlays:
    - Show walkable tiles (green) vs blocked (red)
    - Show LOS rays from player to NPCs
    - Show pathfinding result (yellow path tiles)
    - Show attack range circles

  B. Entity state inspection:
    - Click entity to show detailed state panel
    - NPC: type, HP, attack timer, target, AI state, telegraph
    - Player: all stats, timers, pending hits
    - Pending hit visualization (incoming damage indicators)

  C. RL observation overlay:
    - Show action mask (which actions are legal this tick)
    - Show reward feature values
    - Show observation vector summary
    - Toggle between human control and agent control

  D. Replay / trace:
    - Record action trace per episode
    - Deterministic replay from seed + actions
    - Per-tick state dump (optional)
    - State hash display for determinism verification

  E. Debug event log:
    - "why move failed" / "why attack failed" annotations
    - Combat roll details (att_roll, def_roll, damage)
    - Prayer check results
    - NPC AI decisions

--- PHASE 10: NPC MODELS (Visual Polish) ---

Goal: Replace colored placeholder cubes with actual NPC meshes from b237 cache.

  1. Look up b237 NPC definition IDs for each FC NPC type
     (These will differ from Void 634 IDs — use cache reader to find by name)
  2. Export NPC models via export_models.py with recoloring
  3. Load as MDL2 binary meshes in viewer
  4. Render at entity positions with correct size/rotation
  5. Stretch goal: idle/attack/death animation playback

--- PHASE 11: FLOOR TEXTURE (Visual Polish) ---

Goal: Replace black floor with lava crack texture.

  Options:
    1. Extract from older OSRS revision where lava texture is static
    2. Generate procedurally in shader
    3. Hand-crafted lava texture asset
    4. Accept current vertex-color height shading

================================================================================
WHAT NOT TO BUILD
================================================================================

This is Fight Caves only. Do NOT implement:
  - Banking, shops, trading
  - Chat, social systems
  - Quests, skilling
  - World persistence, networking
  - Login/account systems
  - General object interaction beyond FC needs
  - Full RuneScape combat (only FC-relevant subset)

================================================================================
KEY PARITY CONTRACTS (FREEZE THESE)
================================================================================

These must be identical between headless RL env and debug viewer backend:

  - Tick phase ordering (Phase 2 list above)
  - Action schema (5 heads: move, attack, prayer, eat, drink)
  - Observation schema (126 floats: player 20 + NPC 96 + meta 10)
  - Reward features (16 floats)
  - Action mask (36 floats)
  - Combat timing (attack speed, projectile delay, prayer check timing)
  - Pathfinding / collision semantics
  - NPC AI target selection
  - Wave progression rules
  - Terminal conditions

================================================================================
KEY FILES
================================================================================

Viewer:
  demo-env/src/viewer.c              — Raylib viewer shell (~375 lines)
  demo-env/src/osrs_pvp_terrain.h    — terrain loader
  demo-env/src/osrs_pvp_objects.h    — objects loader + atlas
  demo-env/assets/fightcaves.*       — terrain, objects, atlas from b237

Backend (to be consolidated into demo-env/src/):
  fc_types.h / fc_api.h              — types, API
  fc_sim.c                           — tick loop, episode control
  fc_map.c                           — collision, LOS, pathfinding
  fc_combat.c                        — targeting, rolls, damage
  fc_npc.c                           — NPC defs, AI, movement
  fc_player.c                        — player movement, stats
  fc_prayer.c                        — prayer system
  fc_inventory.c                     — inventory, equipment, items
  fc_projectile.c                    — projectile flight/impact
  fc_wave.c                          — wave data, encounter script
  fc_obs.c                           — observation, mask, reward
  fc_rng.c                           — deterministic RNG
  fc_debug.c                         — hash, replay, instrumentation
  fc_ids.h                           — canonical internal ID enums

Reference (for porting, NOT for linking):
  reference/legacy-rsps/             — full Void RSPS in Kotlin
  /home/joe/projects/runescape-rl-reference/current_fightcaves_demo/

Export scripts:
  /home/joe/projects/runescape-rl-reference/valo_envs/ocean/osrs/scripts/

Cache:
  Download: ~/Downloads/cache-oldschool-live-en-b237-*.tar.gz
  Extract to: /tmp/osrs_cache_modern/cache/

================================================================================
BUILD & RUN
================================================================================

# Extract cache (after reboot)
mkdir -p /tmp/osrs_cache_modern && cd /tmp/osrs_cache_modern && \
tar xzf ~/Downloads/cache-oldschool-live-en-b237-*.tar.gz

# Build
cd /home/joe/projects/runescape-rl/claude/runescape-rl && \
cmake --build build -j$(nproc)

# Run
cd build && ./demo-env/fc_viewer

================================================================================
DO NOT
================================================================================

- Use the Void 634 cache for graphics (use b237 OSRS cache instead)
- Decode cache data in C at runtime (use Python export → binary assets)
- Let raw cache IDs leak into sim core (use fc_ids.h canonical enums)
- Let the viewer decide gameplay outcomes (viewer reads state, never writes it)
- Assume Void 634 IDs work in b237 (translate everything through fc_ids.h)
- Build systems that aren't needed for Fight Caves
- Implement separate game logic for viewer vs headless (one backend, two frontends)
