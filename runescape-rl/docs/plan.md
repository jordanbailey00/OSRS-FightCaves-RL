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

--- PHASE 8h: CLICKABLE INVENTORY / COMBAT / PRAYER TABS ---

Goal: Add tabbed side panel with clickable Inventory, Combat, and Prayer tabs.
This is purely a UI/viewer layer — no backend changes. The tabs provide a more
intuitive visual interface for human play, mirroring the OSRS game interface.

The existing side panel (HP bar, prayer bar, wave info, consumable counts, NPC
health bars) remains at the top. Below it, three clickable tabs appear:

  TAB 1: INVENTORY (default)
    - 28-slot inventory grid (4 columns × 7 rows), styled like OSRS inventory
    - Slots are filled based on the player's current loadout:
      Prayer potions: slots 0-7 (8 pots × 4 doses each)
        Display: "Ppot(4)" → "Ppot(3)" → ... → "Ppot(1)" → empty
        As doses are consumed, label decrements. When all 4 doses used,
        slot shows empty (or "Vial"). prayer_doses_remaining / 4 = full pots,
        prayer_doses_remaining % 4 = partial pot doses.
      Sharks: slots 8-27 (20 sharks)
        Display: "Shark" icon/label. As sharks are eaten, slots empty from end.
    - Click a prayer potion slot → drink one dose (same as pressing P)
    - Click a shark slot → eat shark (same as pressing F)
    - Empty slots are dark/inactive (no click action)

  TAB 2: COMBAT
    - Shows current attack style info:
      "Accurate" / "Rapid" / "Long range" radio buttons (Rapid is default for FC)
      For FC v1, attack style is fixed (Rapid — 1 tick faster, no bonus),
      but display shows it so we can verify/change if needed.
    - Auto retaliate toggle: checkbox/button, currently always ON in FC
      When ON: player auto-targets aggressor if no current target
    - Current weapon: "Rune crossbow"
    - Attack speed: "5 ticks (Rapid)"
    - Weapon range: "7 tiles"
    - Current target info (name, HP bar, distance)

  TAB 3: PRAYER
    - Grid of prayer icons (simplified: just the 3 protection prayers for v1)
    - Each prayer shown as a clickable button:
      "Protect from Melee"  — click to toggle (same as pressing 1)
      "Protect from Range"  — click to toggle (same as pressing 2)
      "Protect from Magic"  — click to toggle (same as pressing 3)
    - Active prayer highlighted (bright icon/border)
    - Prayer points display: "43/43" with drain rate info
    - If prayer points = 0, buttons grayed out

  Implementation:
    1. Add tab state to ViewerState: active_tab enum (TAB_INVENTORY=0, TAB_COMBAT=1, TAB_PRAYER=2)
    2. Draw 3 tab buttons at the separator line between existing panel info and new tab area
    3. Click tab button → switch active_tab
    4. Based on active_tab, draw the appropriate sub-panel content
    5. For inventory tab: compute slot contents from sharks_remaining + prayer_doses_remaining
    6. For combat tab: display static info (style, auto-retal toggle)
    7. For prayer tab: draw 3 prayer buttons with toggle-on-click
    8. All clicks in tab area translate to the SAME backend actions (pending_eat, pending_drink,
       pending_prayer) — no new backend code needed, purely viewer-side UI

  Reference: OSRS inventory/prayer/combat tab layout from Void RSPS
    Inventory.kt — slot layout
    Prayer.kt — prayer grid icons
    CombatDefinition.kt — combat style display

--- PHASE 9a: AGENT-VIEWER ACTION PARITY ---

Goal: Every action available to a human in the viewer must be expressible by an
RL agent through the action API, and produce identical results. The viewer is
the demo-env — it must serve both human play and machine control interchangeably.

Current gap:
  Human clicks tile (25,30) → BFS pathfind → route stored → consumed per tick
  Agent sends action[0]=FC_MOVE_WALK_NE → one directional step per tick
  These are NOT equivalent. The human gets intelligent pathfinding; the agent
  gets raw per-tick directional nudges.

Similarly:
  Human clicks NPC → sets attack_target_idx + approach_target
  Agent sends action[1]=3 → sets target via slot index, no approach

Fix: Unify by making ALL actions go through the same backend interface.

Option A — Elevate the agent API to match human capabilities:
  Add new action types to fc_step:
    FC_ACTION_WALK_TO_TILE(x, y) — pathfind + route, same as click-to-move
    FC_ACTION_ATTACK_NPC(npc_idx) — set target + approach, same as click NPC
  The existing directional actions (FC_MOVE_WALK_N etc.) remain for fine-grained
  RL control, but the agent CAN use the higher-level actions too.

Option B — Lower the human input to match the agent API:
  Human clicks translate to the same per-tick directional actions the agent uses.
  Problem: pathfinding disappears, movement becomes clunky for humans.

Recommendation: Option A. Add high-level action types that both human and agent
can use. The viewer translates clicks → high-level actions. The agent can send
either high-level (walk_to_tile) or low-level (directional) actions.

Implementation: Option A — elevated agent API with high-level actions.

  Added two new action heads (5 and 6) for BFS walk-to-tile:
    Head 5: FC_MOVE_TARGET_X (dim 65: 0=no-op, 1-64=tile X 0-63)
    Head 6: FC_MOVE_TARGET_Y (dim 65: 0=no-op, 1-64=tile Y 0-63)
  When both are non-zero, backend calls fc_pathfind_bfs() and sets the
  player route — identical to a human clicking a tile in the viewer.
  The route is consumed per tick. Directional actions (head 0) are ignored
  while a route is active. If target is unwalkable, BFS returns 0 steps (no-op).

  The agent can now use EITHER:
    - Low-level: directional per-tick actions (head 0) for fine control
    - High-level: walk-to-tile (heads 5+6) for BFS pathfinding

  FC_NUM_ACTION_HEADS: 5 → 7
  FC_ACTION_MASK_SIZE: 36 → 166 (added 65+65 for tile coordinates)
  FC_OBS_SIZE: 178 → 308

Action parity checklist:
  [x] Movement (low-level): directional actions per tick (head 0)
  [x] Movement (high-level): BFS walk-to-tile via heads 5+6
  [x] Attack: agent targets NPC by slot index with auto-approach (head 1)
  [x] Prayer: agent toggles via action head 2
  [x] Eat/drink: agent consumes via action heads 3/4
  [ ] Run toggle: human has X key. No action head. Low priority for FC.
  [x] Wait/idle: FC_MOVE_IDLE (head 0 = 0)

COMPLETE ACTION REFERENCE (7 heads, 166 discrete values):

  All 7 heads are processed SIMULTANEOUSLY each tick. The agent submits one
  value per head per tick. Actions don't block each other — the agent can
  move + eat + pray + attack all on the same tick.

  Head 0: MOVE — directional per-tick movement (17 values)
    0      idle (stand still)
    1-8    walk 1 tile: N, NE, E, SE, S, SW, W, NW
    9-16   run 2 tiles: N, NE, E, SE, S, SW, W, NW
    Note: ignored when a BFS route is active (from heads 5+6 or viewer click).
    Agent must choose direction EVERY tick. For 20 tiles: 10 ticks of run.
    But agent can eat/drink/pray/attack on each of those ticks too.

  Head 1: ATTACK — target NPC by visible slot index (9 values)
    0      no target
    1-8    target NPC in slot 0-7 (sorted by distance to player)
    Sets approach_target=1: auto-walks into weapon range (7 tiles for
    crossbow), then auto-attacks on cooldown. Identical to human click-NPC.
    Attack clears when target dies → agent must retarget.

  Head 2: PRAYER — toggle protection prayer (5 values)
    0      no change
    1      prayer off (deactivate current prayer)
    2      protect from magic
    3      protect from range (missiles)
    4      protect from melee
    Instant — processed before movement/combat. Toggling the already-active
    prayer is a no-op (not an error). Grayed out when prayer points = 0.

  Head 3: EAT — consume food (3 values)
    0      don't eat
    1      eat shark (heals 20 HP, 3-tick food cooldown)
    2      combo eat karambwan (heals 18 HP, 1-tick combo cooldown)
    Masked when: no sharks left, food timer > 0, HP already full.
    Food and potion cooldowns are SEPARATE — can eat + drink same tick.

  Head 4: DRINK — consume potion (2 values)
    0      don't drink
    1      drink prayer potion dose (restores 17 pray pts, 2-tick cooldown)
    Masked when: no doses left, potion timer > 0, prayer already full.
    8 potions × 4 doses = 32 total doses.

  Head 5: MOVE_TARGET_X — BFS pathfind target X (65 values)
    0      no pathfind target (use directional head 0 instead)
    1-64   tile X coordinate 0-63
    Must be paired with head 6. If either is 0, pathfinding is skipped.

  Head 6: MOVE_TARGET_Y — BFS pathfind target Y (65 values)
    0      no pathfind target
    1-64   tile Y coordinate 0-63
    When both heads 5+6 are non-zero: BFS pathfind to tile (x-1, y-1).
    Route is set once, then auto-consumed 1-2 tiles per tick. While the
    route plays out, the agent still submits all heads each tick and can
    eat/drink/pray/attack normally. If target is unwalkable or unreachable,
    BFS returns 0 steps → no-op. Setting a new walk-to-tile cancels any
    previous route. Directional actions (head 0) are ignored while route
    is active.

  INTERACTION RULES:
    - All heads are independent — no head blocks another
    - Can eat + drink on same tick (separate cooldown timers)
    - Can pray + eat + drink on same tick (prayer is instant)
    - Movement + combat: setting attack target auto-walks to range
    - Walk-to-tile cancels attack approach (and vice versa)
    - Directional movement ignored while BFS route is active

LIVE VERIFICATION TEST PLAN:

  Run the viewer with scripted agent actions, observe in debug viewer:

  Movement tests:
    1. Directional walk — each of 8 directions (head 0 = 1-8)
    2. Directional run — at least 2 directions (head 0 = 9-16)
    3. Walk-to-tile — BFS to a specific walkable tile (heads 5+6)
    4. Walk-to-tile blocked — target unwalkable tile (should no-op)
    5. Walk-to-tile then directional — directional should be ignored
       while route active, then resume working after route completes
    6. Walk-to-tile cancel — new walk-to-tile replaces old route

  Combat tests:
    7. Attack NPC — target slot 1 (closest), verify approach + auto-attack
    8. Switch target — retarget different NPC mid-combat
    9. Target dies — verify target auto-clears, agent can retarget

  Prayer tests:
    10. Activate each protection prayer (head 2 = 2, 3, 4)
    11. Deactivate prayer (head 2 = 1)
    12. Prayer at 0 points — should be rejected

  Consumable tests:
    13. Eat shark — verify HP heals, 3-tick cooldown blocks re-eat
    14. Drink prayer pot — verify prayer restores, 2-tick cooldown
    15. Eat + drink same tick — both should work (separate cooldowns)
    16. Eat at full HP — should be masked/rejected
    17. Drink at full prayer — should be masked/rejected

  Combined tests:
    18. Run north + eat shark + switch prayer — all on same tick
    19. Walk-to-tile + attack NPC — walk-to-tile should cancel approach
    20. Attack NPC while walking route — approach should cancel route

--- PHASE 9b: BACKEND AGENT PLAYABILITY VERIFICATION ---

Goal: Verify the backend is fully playable by an agent using ONLY the action
API — no viewer, no human input, no GUI. This is the critical gate before
copying the backend to training-env.

  Steps:
    1. Write a simple test harness (C or Python via ctypes) that:
       a. Calls fc_reset() to start an episode
       b. Each tick: reads observation, computes action mask, selects a valid
          random action per head, calls fc_tick() with those actions
       c. Runs for multiple full episodes (wave 1 through death or completion)
       d. Logs: waves reached, ticks survived, damage dealt, prayer usage
    2. Verify all action heads work correctly without viewer:
       - Directional movement: agent can navigate the arena
       - NPC targeting: agent can select and attack NPCs by slot index
       - Prayer switching: agent can toggle prayers via action head
       - Eating/drinking: agent consumes items via action head
       - Action mask: masked actions are correctly rejected
    3. Verify combat parity:
       - Same damage rolls, hit delays, prayer checks as viewer play
       - NPC AI behaves identically
       - Wave progression works (all 63 waves, Jad healers, splits)
       - Terminal conditions fire correctly
    4. Verify observation correctness:
       - All 126 policy obs features are populated and normalized to [0,1]
       - 16 reward features fire at correct times
       - Action mask reflects valid actions each tick
    5. Verify determinism:
       - Same seed + same actions → identical state hash at every tick
       - Run twice with recorded actions, compare hash traces

--- PHASE 9c: DEBUG TOOLING & OVERLAYS ---

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

--- PHASE 9d: TRAINING-ENV CODE AUDIT ---

Goal: Determine exactly which files and code from demo-env/ belong in the
headless training-env, and what must be excluded. The rule: keep everything
that affects game logic, mechanics, timing, stats, or simulation. Exclude
everything that only exists for rendering, sprites, UI, or human interaction.

  AUDIT RESULT (completed, updated with training performance focus):

  BACKEND — COPY TO TRAINING-ENV (16 files):
  Every file here is essential for the game simulation. Nothing can be
  removed without breaking gameplay mechanics, stats, or the RL interface.

    fc_types.h          — All game structs (FcState, FcPlayer, FcNpc, FcPendingHit, enums)
    fc_contracts.h      — Obs/action/reward/mask buffer layouts, head dimensions, normalization
    fc_api.h            — Public sim API: init, reset, step, write_obs, write_mask, write_reward
    fc_capi.h           — Python ctypes FFI interface declarations (PufferLib bridge)
    fc_capi.c           — FFI implementation: env create/destroy/reset/step, batch interface
    fc_state.c          — State init, reset, walkability map, obs/mask/reward writers
    fc_tick.c           — Main tick loop: action processing, timers, hit resolution, death timers
    fc_combat.c         — OSRS combat formulas: accuracy rolls, max hit, prayer blocking, hit queues
    fc_combat.h         — Combat function declarations
    fc_npc.c            — NPC stat table (8 types), AI dispatch, aggro, movement, Tz-Kek split
    fc_npc.h            — NPC function declarations
    fc_wave.c           — All 63 waves × 15 rotations spawn data + wave advancement logic (40KB)
    fc_wave.h           — Wave function declarations
    fc_prayer.c         — Prayer drain counter (OSRS formula), potion restore calculation
    fc_prayer.h         — Prayer function declarations
    fc_pathfinding.c    — Tile walkability, footprint checks, Bresenham LOS, BFS pathfinding
    fc_pathfinding.h    — Pathfinding function declarations
    fc_rng.c            — XORshift32 deterministic RNG (all randomness flows through this)

  DO NOT COPY — DEAD WEIGHT FOR TRAINING (8 files):

    fc_debug.h          — Debug event log structs, trace buffer, FC_DBG_LOG macros.
                           fc_tick.c and fc_combat.c include it but NEVER call any
                           of its functions (verified: zero FC_DBG_LOG calls). The
                           include is dead. Training-env should not carry this.
    fc_debug.c          — Debug log + action trace buffer implementation.
                           Only useful for viewer-side diagnostics. Not called by
                           any core game code. Dead weight.
    fc_hash.c           — FNV-1a state hash for determinism verification.
                           Only called from viewer.c (debug overlay). fc_capi.c
                           never calls it. Useful for testing but not training.
                           fc_api.h declares fc_state_hash() but no training code
                           calls it, so no linker error.
    fc_debug_overlay.h  — Phase 9c debug visualization (Raylib overlays, event log
                           ring buffer, panel tabs). Viewer-only.
    viewer.c            — 2000+ LOC Raylib 3D viewer, UI, input handling
    fc_npc_models.h     — Raylib MDL2 model loader (depends on raylib.h)
    osrs_pvp_terrain.h  — Raylib terrain mesh loader (depends on raylib.h)
    osrs_pvp_objects.h  — Raylib placed objects + atlas loader (depends on raylib.h)
    osrs_pvp_anim.h     — OSRS vertex-group animation runtime (graphics-focused)

  CLEANUP NEEDED BEFORE COPY:
    - Remove dead #include "fc_debug.h" from fc_tick.c and fc_combat.c
      (they include it but never use it — verified zero calls)
    - fc_api.h declares fc_state_hash() which lives in fc_hash.c. This
      declaration is harmless (no training code calls it, so no linker
      error), but could be wrapped in #ifdef FC_INCLUDE_HASH for cleanliness.

  THINGS THAT MUST NOT BE STRIPPED:
    - Pathfinding (BFS) — needed for walk-to-tile action heads 5+6
    - fc_fill_render_entities() in fc_state.c — it's a pure data serializer,
      harmless to include even if training never calls it
    - death_timer / died_this_tick — simulation timing that affects wave
      progression and observation writer, not cosmetic
    - All combat formulas, NPC stats, prayer drain rates, wave data
    - Action mask computation (used by RL policy)
    - Reward feature extraction (used by RL trainer)

--- PHASE 9e: REPO CLEANUP & REORG ---

Goal: Clean up legacy files, dead code, and stale artifacts across the repo
so the codebase is well-organized and easy to navigate BEFORE we copy the
backend to training-env. We don't want to carry cruft into the training build.

  Steps:
    1. Audit runescape-rl/ top-level directory structure:
       - demo-env/        — keep (viewer + backend, actively developed)
       - training-env/    — keep (headless RL env, about to be refreshed)
       - reference/       — keep (legacy RSPS source for porting, read-only)
       - docs/            — review: remove stale writeups that are no longer
         relevant, consolidate useful ones. Keep agent_handoff, pr_plan,
         debug_viewer_analysis. Remove anything from pre-C-pivot era that
         references the old Kotlin/JVM architecture.
       - pufferlib/       — review: is this a submodule or a copy? Remove
         any stale forks or unused code. Should only contain what's needed
         for the training pipeline.

    2. Clean up demo-env/src/:
       - Remove any dead code, commented-out blocks, or TODO stubs that
         will never be implemented
       - Ensure all fc_*.c/h files have accurate header comments describing
         what they do (no stale descriptions from early phases)
       - Remove any debug fprintf statements that spam stderr during normal
         play (keep only ones gated by FC_DEBUG or show_debug)

    3. Clean up demo-env/docs/:
       - debug_viewer_plan.md — mark completed phases as DONE, remove
         outdated problem descriptions that have been fixed
       - Remove any one-off investigation docs that served their purpose

    4. Clean up training-env/:
       - The stale 2026-03-24 copies will be replaced in Phase 9f, but
         remove any other cruft (old test files, unused scripts, stale
         build artifacts)
       - Verify CMakeLists.txt is ready for the refreshed backend

    5. Repository root:
       - Remove empty directories, stale build outputs, temp files
       - Ensure .gitignore covers build/, *.o, __pycache__, etc.
       - Verify the directory structure is clean and self-explanatory

  Rule: If you're not sure whether to delete something, check git log to
  see when it was last meaningfully touched. If it hasn't been relevant
  since the C pivot (2026-03-24), it's probably safe to remove.

--- PHASE 9f: COPY BACKEND TO TRAINING-ENV ---

Goal: training-env/ has a complete, independently buildable copy of the FC
backend — identical game logic, no rendering, ready for PufferLib integration.

  Current state: training-env/ has STALE copies of fc_*.c/h from 2026-03-24.
  The demo-env backend has evolved significantly since then (click-to-move,
  combat fixes, animation state, NPC models, prayer/combat improvements).

  Steps:
    1. Snapshot the demo-env backend files to copy:
       - All fc_*.c and fc_*.h from demo-env/src/
       - EXCLUDE viewer-only files: viewer.c, osrs_pvp_terrain.h,
         osrs_pvp_objects.h, fc_npc_models.h, osrs_pvp_anim.h
       - EXCLUDE assets/ directory (terrain, objects, atlas, models, anims)
    2. Copy fc_*.c/h into training-env/src/ and training-env/include/,
       replacing the stale versions
    3. Verify training-env CMakeLists.txt compiles all backend sources
       (no Raylib dependency — headless build)
    4. Verify training-env/src/fc_capi.c (the PufferLib C API bridge) still
       compiles and links against the updated backend
    5. Run the agent playability test from Phase 9b against the training-env
       build to confirm identical behavior
    6. Run determinism check: same seed + actions in demo-env and training-env
       must produce identical state hashes at every tick

  CRITICAL: After this step, both demo-env and training-env share the same
  backend logic. Future backend changes must be applied to BOTH, or we must
  establish a shared-source build system. For v1, manual sync is acceptable.
  Phase 10+ may introduce a shared fc_core/ directory with symlinks or
  CMake add_subdirectory() to eliminate drift.

--- PHASE 10: RL TRAINING INFRASTRUCTURE (PufferLib 4.0) ---

Goal: Integrate Fight Caves as a PufferLib 4.0 ocean environment and begin
training. PufferLib 4.0 expects ALL game logic in a single C header file
with standardized function signatures. The framework handles vectorization,
GPU transfer, PPO training, and evaluation automatically.

  ARCHITECTURE CHANGE: PufferLib 4.0 does NOT use ctypes/.so loading.
  Instead, it compiles the game C code directly into the training binary
  via build.sh. Our fc_capi.c bridge is NOT needed — PufferLib's vecenv.h
  provides its own vectorized step/reset/render hooks.

  The training-env/src/ files become the source for a PufferLib ocean env.
  We adapt them into the PufferLib pattern (fight_caves.h + binding.c).

  Reference repo: /home/joe/projects/runescape-rl-reference/pufferlib_4/

  A. Create PufferLib-compatible environment (in our repo):

    File structure (inside runescape-rl/training-env/):
      fight_caves.h     — Main header: FightCaves struct, c_reset, c_step,
                           c_close, c_render. Includes/inlines all fc_*.c
                           backend code (or #includes the .c files directly).
      binding.c          — PufferLib binding: defines OBS_SIZE, NUM_ATNS,
                           ACT_SIZES, OBS_TYPE. Implements my_init() to
                           parse config dict and my_log() to extract stats.

    The code lives in OUR repo, not in pufferlib_4/ocean/. We follow the
    same interface pattern so PufferLib's vecenv.h and build system can
    compile it. We point the build at our directory or write our own build
    script using the same flags. Only move to ocean/ if upstreaming to
    PufferLib as a contribution.

    Steps:
      1. Write fight_caves.h in training-env/ that wraps our FcState into the PufferLib
         environment struct pattern:
           - Log log (required: perf, score, episode_return, episode_length, n)
           - float* observations (FC_POLICY_OBS_SIZE per agent)
           - double* actions (NUM_ATNS per agent, from network output)
           - float* rewards, float* terminals
           - FcState state (our game state, embedded directly)
      3. Implement c_reset(): call fc_reset(), compute initial observations
      4. Implement c_step(): read actions from double* buffer, convert to
         int action heads, call fc_step(), compute reward from features,
         write observations, handle terminal + auto-reset
      5. Implement c_close(): call fc_destroy()
      6. Implement c_render(): launch our Raylib viewer (optional, for eval)
      7. Write binding.c with macros and my_init/my_log hooks

    Macros for binding.c (v1 — 5 heads, no walk-to-tile):
      OBS_SIZE = 162 (126 policy obs + 36 action mask)
      NUM_ATNS = 5
      ACT_SIZES = {17, 9, 5, 3, 2}
      OBS_TYPE = FLOAT
      ACT_TYPE = DOUBLE

    Action mapping in c_step():
      int actions[7];
      for (int h = 0; h < 7; h++)
          actions[h] = (int)env->actions[agent_idx * 7 + h];
      fc_step(&env->state, actions);

    Reward computation in c_step():
      Read reward features from fc_write_reward_features()
      Apply shaping weights (configurable via config dict):
        reward += rwd[DAMAGE_DEALT] * w_damage_dealt;
        reward += rwd[DAMAGE_TAKEN] * w_damage_taken;
        reward += rwd[NPC_KILL] * w_npc_kill;
        ... etc for all 16 features ...
      env->rewards[agent_idx] = reward;

    Observation in c_step():
      fc_write_obs(&env->state, env->observations + agent_idx * OBS_SIZE);
      (Only policy obs, NOT reward features or action mask — PufferLib
       handles action masking separately if needed)

  B. Action masking integration:

    PufferLib 4.0 action masking approach:
      Option 1: Include mask in observations (append mask floats to obs).
                Policy network reads obs[:126] for features, obs[126:292]
                for mask. OBS_SIZE = 126 + 166 = 292.
      Option 2: Embed mask logic in the policy network (mask invalid logits
                before sampling). Requires custom network in models.cu.
      Option 3: No masking — let the agent learn which actions are valid
                via negative reward for invalid actions. Simplest but slower.

    Recommendation: Option 1 for v1. Include action mask in the observation
    buffer. The policy network applies the mask before softmax on each head.
    This is how several ocean envs handle it (see nmmo3 pattern).

  C. Configuration (config/fight_caves.ini):

    [base]
    env_name = fight_caves

    [env]
    # Reward shaping weights (from D1 analysis)
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
    # Curriculum
    start_wave = 1
    # Collision file path
    collision_path = assets/fightcaves.collision

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

  D. RL DESIGN DECISIONS:

    These must be decided before implementation. Each directly affects
    whether the agent learns to get a fire cape.

    --- D1. REWARD WEIGHTS ---

    We have 16 reward features (from fc_contracts.h). The scalar reward
    each tick = weighted sum of these features. The weights determine what
    behavior the agent optimizes for.

    Best practice for long-horizon survival games:
      - Start with DENSE rewards (reward many small things) so the agent
        gets learning signal early. Sparse rewards (only reward cave
        complete) make it nearly impossible to learn from scratch.
      - Avoid rewards that create degenerate behavior. E.g., if damage
        dealt is rewarded too heavily, the agent may ignore prayer/food
        and just attack until it dies.
      - Negative rewards should be mild so the agent isn't paralyzed by
        fear of punishment. Exception: wrong Jad prayer should be harsh
        because it's the key skill to learn.

    Proposed v1 weights (will tune after initial training runs):

      POSITIVE (encourage):
        w_damage_dealt      =  0.5    # small reward per damage, keeps agent engaged
        w_npc_kill           =  3.0    # meaningful reward for finishing off NPCs
        w_wave_clear         = 10.0    # major milestone, agent should prioritize
        w_jad_damage         =  2.0    # extra incentive to attack Jad (not just hide)
        w_jad_kill           = 50.0    # huge reward — this is the goal
        w_cave_complete      = 100.0   # ultimate reward
        w_correct_jad_prayer =  5.0    # reinforce correct prayer switching

      NEGATIVE (discourage):
        w_damage_taken       = -0.5    # mild punishment, don't want agent to be reckless
        w_player_death       = -20.0   # significant but not so harsh agent becomes passive
        w_wrong_jad_prayer   = -10.0   # harsh — this is the #1 mistake to avoid
        w_invalid_action     = -0.1    # gentle nudge away from masked actions
        w_food_used          = -0.05   # very slight — food is meant to be used, but conserve
        w_prayer_pot_used    = -0.05   # same — pots are resources to manage, not hoard

      NEUTRAL/ZERO:
        w_movement           =  0.0    # don't reward or penalize movement itself
        w_idle               = -0.01   # tiny penalty to discourage standing still forever
        w_tick_penalty        = -0.005  # tiny time pressure to prevent infinite stalling

    Key principles:
      - Wave clear (10.0) > NPC kill (3.0) > damage dealt (0.5)
        Hierarchy ensures agent prioritizes wave completion over farming damage.
      - Cave complete (100.0) >> everything else
        The agent should sacrifice short-term reward for long-term survival.
      - Jad prayer rewards/penalties are the largest per-tick signals
        This teaches the agent that prayer switching is critical.

    --- D2. ACTION SPACE ---

    Current: 7 heads, 166 total actions.
    The walk-to-tile heads (5+6) add 65+65 = 130 actions on top of the
    base 36. This is a large action space that may slow learning.

    Best practice:
      - Smaller action spaces learn faster. Most successful RL game agents
        use <50 discrete actions.
      - Multi-head discrete is fine if heads are independent (ours are).
      - Large coordinate spaces (65x65 tiles) are hard for PPO to explore
        effectively — the agent needs to discover that specific (x,y)
        pairs are useful, which is exponentially harder than 8 directions.

    Options:
      A. Full 7 heads (166 actions) — keep walk-to-tile for pathfinding
      B. 5 heads only (36 actions) — drop walk-to-tile, directional only
      C. 5 heads + coarse walk-to-tile (e.g., 8x8 grid = 64+64 = 164)
      D. Start with 5 heads (B), add walk-to-tile later if needed

    Recommendation: Option D. Start simple with the 5 original heads
    (move:17, attack:9, prayer:5, eat:3, drink:2 = 36 actions). The
    directional movement + attack auto-approach covers all needed movement.
    If the agent struggles with pathfinding around obstacles, add walk-to-
    tile in a later iteration.

    For PufferLib binding:
      NUM_ATNS = 5
      ACT_SIZES = {17, 9, 5, 3, 2}

    --- D3. OBSERVATION SPACE ---

    Current: 126 policy features (player:20 + NPC:96 + meta:10).
    Plus 166 action mask floats = 292 total if mask is in obs buffer.

    Best practice:
      - Include action mask in observations. The policy network needs to
        know which actions are valid to avoid wasting exploration on
        impossible actions. This is standard for masked action PPO.
      - Normalize all features to [0,1] (already done in fc_write_obs).
      - 126 features is reasonable — most game RL envs use 50-500.
      - Don't over-observe: the agent should learn from the same info
        a human player has (HP, prayer, NPC positions, wave number).

    Decision: Include action mask in the observation buffer.
      OBS_SIZE = FC_POLICY_OBS_SIZE + FC_ACTION_MASK_SIZE
      With 5 heads: OBS_SIZE = 126 + 36 = 162
      With 7 heads: OBS_SIZE = 126 + 166 = 292

      Policy reads obs[:126] for game state, obs[126:] for mask.
      Apply mask to logits before softmax (standard masked PPO).

    --- D4. HYPERPARAMETERS ---

    Fight Caves is a long-horizon game (~3000+ ticks for a full run).
    Best practices for long-horizon games:

      gamma = 0.999         # High discount — agent must value future rewards
                             # (wave 63 is thousands of ticks away from wave 1)
      gae_lambda = 0.95      # Standard GAE smoothing
      horizon = 256          # Longer rollouts capture more temporal structure
                             # (a full wave might take 50-200 ticks)
      learning_rate = 0.0003 # Conservative LR for stability (PPO standard)
      clip_coef = 0.2        # Standard PPO clip
      vf_coef = 0.5          # Standard value loss weight
      ent_coef = 0.01        # Moderate entropy for exploration
                             # Increase to 0.05 if agent converges too early
      max_grad_norm = 0.5    # Standard gradient clipping
      minibatch_size = 4096  # Large batches for stable gradients
      total_agents = 4096    # Many parallel envs for throughput

    Network:
      hidden_size = 256      # Moderate — FC has complex strategy but
                             # manageable state space
      num_layers = 2         # Shallow is usually better for PPO
                             # (deep networks + PPO = instability)

    These are starting points. Key tuning knobs after initial runs:
      - If agent doesn't explore: increase ent_coef
      - If training is unstable: decrease learning_rate
      - If agent is short-sighted: increase gamma toward 0.9995
      - If agent plateaus early: increase horizon

    --- D5. CURRICULUM ---

    Options:
      A. Always start wave 1 — simplest, agent learns full game
      B. Start at random wave (1-30) — faster exposure to harder content
      C. Progressive: start wave 1, unlock later start waves as agent
         improves (e.g., once agent reliably clears wave 20, allow
         starting from wave 15-20)

    Best practice:
      - Start with Option A. The agent needs to learn basic combat
        (waves 1-10) before it can handle Ket-Zek or Jad.
      - If training stalls (agent can't get past wave 30 after days
        of training), switch to Option C to practice harder waves.
      - Option B risks the agent never learning early-wave basics.

    Recommendation: Option A for v1. Always wave 1. Revisit if needed.

    Implementation: start_wave config parameter in fight_caves.ini.
    When start_wave > 1, fc_reset skips to that wave and spawns
    appropriate NPCs. Player starts with proportionally reduced
    consumables (fewer sharks/pots for later waves).

  E. Build and verify:

    DONE:
    [x] Build script (training-env/build.sh) — compiles against PufferLib
        headers, supports CUDA/CPU/standalone modes
    [x] Standalone test (fight_caves.c) — 100 episodes, ~1M steps/sec
    [x] Static lib compiles against vecenv.h (GCC, verified)
    [x] Collision asset in training-env/assets/fightcaves.collision
    [x] Struct fields match vecenv.h expectations (float* actions/terminals,
        int rng field)
    [x] Auto-reset observation timing fix — obs written BEFORE reset so
        agent sees terminal state, not post-reset state (verified against
        ocean/convert and ocean/snake patterns)
    [x] Action mask included in observation buffer (162 = 126 + 36)

    REMAINING — PufferLib integration test:
    1. Install PufferLib 4.0 Python package (pip install or dev mode)
    2. Copy config/fight_caves.ini to pufferlib_4/config/
    3. Run build: cd training-env && ./build.sh (or ./build.sh --cpu)
    4. Verify _C.so is produced in pufferlib_4/pufferlib/
    5. Run: cd pufferlib_4 && puffer train fight_caves
       - Check: no crashes on startup
       - Check: obs shape = (total_agents, 162)
       - Check: action shape = (total_agents, 5)
       - Check: rewards are non-zero (reward features firing)
       - Check: episodes terminate and auto-reset
       - Check: log shows score, episode_return, wave_reached
    6. Let it train for ~1M steps, verify wandb metrics
    7. Run: puffer eval fight_caves --load-model-path <checkpoint>
       - Verify eval mode works (even with random/untrained policy)

  F. Milestone tracking:

    PufferLib logs via my_log() hook. We export:
      - score (cumulative reward)
      - episode_return, episode_length
      - Custom fields: wave_reached, terminal_reason, jad_encountered
    These go to wandb automatically via puffer train --wandb.
    Save checkpoints every N training updates (configurable).

  G. Key reference files in PufferLib 4.0:

    Pattern to follow (all in runescape-rl-reference/pufferlib_4/):
      ocean/template/         — Minimal working example (start here)
      ocean/convert/          — Multi-head discrete actions {9,5}
      ocean/snake/snake.h     — Complex game logic (300+ lines)
      ocean/whisker_racer/    — Raylib rendering in c_render()

    Training internals (in runescape-rl-reference/pufferlib_4/):
      pufferlib/pufferl.py    — CLI: train/eval/sweep entry points
      src/vecenv.h            — Vectorized env interface (study lines 108-131)
      src/pufferlib.cu        — PPO training kernels
      config/default.ini      — Base hyperparameters (copy + override)
      build.sh                — Build script (study lines 119-240)

    Our repo structure after Phase 10:
      runescape-rl/
        training-env/
          src/                — 18 backend files (game logic)
          fight_caves.h       — PufferLib wrapper (c_reset/c_step/c_render)
          binding.c           — PufferLib macros + my_init/my_log
          build.sh            — Build script (compiles with PufferLib headers)
        config/
          fight_caves.ini     — Hyperparameters + reward weights
        demo-env/             — Raylib viewer (unchanged)

--- PHASE 11: POLICY REPLAY IN DEBUG VIEWER ---

Goal: Watch a trained policy play Fight Caves in the full debug viewer with
all overlays, NPC models, animations, hitsplats — everything we built in
Phases 8-9. No backend changes. No fc_*.c modifications.

  STATUS: Audit complete. Implementation plan below.

  AUDIT FINDINGS — PufferLib 4.0 eval system:

    PufferLib has a built-in eval() function (pufferl.py:401-428):
      while True:
          backend.render(pufferl, 0)   # calls c_render(&envs[0])
          backend.rollouts(pufferl)    # one policy step + env step

    - Eval sets horizon=1 (single-step inference) and reset_state=False
      (preserves MinGRU hidden state across episodes).
    - c_render() is called BEFORE each rollout step. It gets full access
      to the env struct (FightCaves*, which contains FcState).
    - Checkpoints are raw .bin files (float32 weight arrays). Loaded via
      backend.load_weights(pufferl, path). Network architecture reconstructed
      from config (hidden_size=256, num_layers=2, MinGRU encoder/decoder).
    - Ocean envs (whisker_racer, snake) render Raylib INLINE in c_render().
      They initialize the window on first call, draw one frame per call.
    - Eval runs ALL vectorized envs (4096 by default). Only env 0 renders.

  AUDIT FINDINGS — viewer.c action input path:

    - Actions flow through int actions[FC_NUM_ACTION_HEADS] in ViewerState.
    - Three existing non-human action sources:
      1. build_human_actions() (line 582): fills from buffered keyboard/mouse
      2. auto_mode (line 1742): random actions per head
      3. test_mode (line 603): hardcoded AGENT_TESTS[] sequences
    - Action selection at line 1738-1748 (once per tick):
        if (test_mode) build_test_actions()
        else if (auto_mode) random_actions()
        else build_human_actions()
    - fc_step(&v.state, v.actions) at line 1766.
    - NO fc_write_obs() call exists in viewer.c — obs not computed.
    - --screenshot flag (line 1493) is the argv parsing pattern to follow.

  APPROACH ANALYSIS:

    Option A — PufferLib native c_render() (PufferLib-driven):
      Implement c_render() in fight_caves.h with full Raylib rendering.
      Run via: puffer eval fight_caves --load-model-path checkpoint.bin
      Pro: PufferLib handles all ML (checkpoint loading, inference, action
           sampling, action masking). Zero custom Python code.
      Con: Our viewer is 2100 LOC with complex state (camera orbit, smooth
           interpolation, hitsplats, animations, tab UI, debug overlays).
           Extracting this into a callable c_render() function is a major
           refactor. Would also need Raylib linked into the training build.
           Not how the project is currently structured (training = headless,
           demo = visual).

    Option B — Viewer-driven with stdin/stdout pipe:
      Add --policy-pipe mode to viewer.c. Viewer reads actions from stdin,
      writes observations to stdout. Python eval script loads checkpoint,
      runs inference, pipes actions.
      Pro: Viewer changes are ~40 lines. All existing features work unchanged
           (camera, overlays, animations, hitsplats, pause/step, UI).
           No training-env changes. Clean separation maintained.
      Con: Need Python eval script that loads PufferLib checkpoint and runs
           policy inference outside PufferLib's eval loop.

  RECOMMENDATION: Option B (viewer-driven pipe).

    Reason: Our viewer is a standalone application with its own main loop,
    camera system, animation state, and 45 MB of assets. Refactoring it
    into a callable c_render() subroutine would be a multi-day effort and
    would couple the training build to Raylib. The pipe approach adds ~40
    lines to viewer.c and keeps the existing architecture intact.

    For checkpoint inference in the Python script, use PufferLib's own
    Python API: create a minimal pufferl instance with total_agents=1,
    load the checkpoint, and use its inference path. This avoids manually
    reconstructing the MinGRU network architecture.

  IMPLEMENTATION:

    Step 1 — viewer.c changes (~40 lines):

      A. Add --policy-pipe flag parsing (after --screenshot pattern):
         int policy_pipe = 0;
         for (int i = 1; i < argc; i++) {
             if (strcmp(argv[i], "--policy-pipe") == 0) policy_pipe = 1;
         }

      B. Add read_policy_actions() function:
         Reads one line from stdin: "a0 a1 a2 a3 a4\n" (5 space-separated ints)
         Fills v->actions[0..4], sets actions[5]=actions[6]=0.
         Returns 1 on success, 0 on EOF/error.
         Uses text format for debuggability (tick rate is 2 TPS — no perf concern).
         stdin must be set to line-buffered or unbuffered.

      C. Add write_obs_to_pipe() function:
         After fc_step(), computes obs + mask via:
           float obs[FC_POLICY_OBS_SIZE];  // 135 floats
           float mask[36];                 // 5-head mask
           fc_write_obs(&v.state, obs_buf);   // fills 151 floats (135 policy + 16 reward)
           fc_write_mask(&v.state, mask_buf); // fills 166 floats (full 7-head)
         Writes to stdout: 171 floats as space-separated text + newline.
           First 135 = policy obs, next 36 = 5-head action mask.
         Calls fflush(stdout) after each line.

      D. Integrate into tick loop at line 1738-1748:
         if (test_mode) build_test_actions()
         else if (policy_pipe) read_policy_actions()   // NEW
         else if (auto_mode) random_actions()
         else build_human_actions()

         After fc_step() (line 1766):
         if (policy_pipe) write_obs_to_pipe()           // NEW

      E. When policy_pipe is active:
         - Skip process_human_clicks() and process_human_keys() for gameplay
           (camera and debug overlay keys still work)
         - On terminal state, write obs (so Python sees terminal) and wait
           for Python to send a "reset" signal or just pipe actions for the
           new episode after fc_reset()
         - Initial obs written once at startup (before first action read)

      F. Disable printf/fprintf that would pollute stdout.
         Any existing debug prints to stdout must go to stderr instead
         when policy_pipe is active.

    Step 2 — eval_viewer.py (~100-120 lines):

      A. Checkpoint loading via PufferLib:
         Use PufferLib's Python API to reconstruct the policy network and
         load checkpoint weights. This avoids manually reimplementing the
         MinGRU architecture.

         Approach: import pufferlib, create a minimal eval instance with
         total_agents=1, load weights. Extract the policy forward pass
         function for standalone use.

         Fallback: If PufferLib's API doesn't support standalone inference
         easily, reconstruct the network in PyTorch:
           - Encoder: Linear(171 → 256) + ReLU
           - MinGRU: 2 layers, hidden_size=256
           - Actor heads: 5 × Linear(256 → dim)
           - Load raw .bin weights in order
         This requires matching PufferLib's exact weight layout.

      B. Subprocess management:
         Launch: subprocess.Popen(
           ['./build/demo-env/fc_viewer', '--policy-pipe'],
           stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=None
         )
         stderr=None lets viewer debug output pass through to terminal.

      C. Main loop:
         1. Read initial obs from viewer stdout (171 floats, text line)
         2. Parse obs into policy_obs[135] and action_mask[36]
         3. Run policy forward pass → get logits for 5 heads
         4. Apply action mask: set logits to -inf where mask == 0
         5. Sample actions (argmax for deterministic, or categorical)
         6. Write actions to viewer stdin: "a0 a1 a2 a3 a4\n"
         7. Read next obs from viewer stdout
         8. Repeat until viewer exits or user interrupts

      D. MinGRU hidden state:
         The policy has recurrent state (MinGRU). Must maintain hidden state
         across ticks. Reset hidden state on episode boundaries (when terminal
         is detected in obs, or when wave resets to 1).

      E. Usage:
         python eval_viewer.py --checkpoint checkpoints/fight_caves/.../model.bin
         Optional: --deterministic (argmax instead of sampling)
         Optional: --start-wave N (curriculum start wave)

    Step 3 — Testing:

      A. Verify pipe protocol:
         Run viewer with --policy-pipe, manually type "0 0 0 0 0\n" on stdin.
         Confirm obs appears on stdout. Confirm viewer renders idle agent.

      B. Verify with random policy:
         Write a trivial Python script that reads obs, sends random valid
         actions (respecting mask). Confirm viewer plays normally.

      C. Verify with trained checkpoint:
         Load v6.1 checkpoint, observe agent behavior at wave 30.
         This is the whole point — see what the agent is actually doing.

  FILES TO CREATE:
    eval_viewer.py                    — Python eval script (in runescape-rl/)

  FILES TO MODIFY:
    demo-env/src/viewer.c             — Add --policy-pipe mode (~40 lines)

  FILES NOT MODIFIED:
    All fc_*.c/h backend files        — no changes
    training-env/fight_caves.h        — no changes
    training-env/binding.c            — no changes

  PROTOCOL SUMMARY:

    Viewer → Python (stdout): 171 space-separated floats per line
      [0..134]   policy observation (135 floats, normalized 0-1)
      [135..170] action mask for 5 heads (36 floats, 0.0 or 1.0)
      Emitted: once at startup, then once after each fc_step()

    Python → Viewer (stdin): 5 space-separated ints per line
      [0] MOVE (0-16)
      [1] ATTACK (0-8)
      [2] PRAYER (0-4)
      [3] EAT (0-2)
      [4] DRINK (0-1)
      Consumed: once per game tick

    Text format chosen for debuggability. At 2 TPS, bandwidth is ~1 KB/sec.
    Each line is newline-terminated. fflush() after each write.

--- FUTURE WORK: VISUAL POLISH ---

  NPC MODELS:
    Replace colored placeholder cubes with actual NPC meshes from b237 cache.
    1. Look up b237 NPC definition IDs for each FC NPC type
    2. Export NPC models via export_models.py with recoloring
    3. Load as MDL2 binary meshes in viewer
    4. Render at entity positions with correct size/rotation
    5. Stretch goal: idle/attack/death animation playback

  FLOOR TEXTURE:
    Replace black floor with lava crack texture.
    Options: extract from older OSRS revision, procedural shader,
    hand-crafted asset, or accept current vertex-color height shading.

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
