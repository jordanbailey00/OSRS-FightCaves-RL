================================================================================
FIGHT CAVES BACKEND SYSTEMS — AUDIT REPORT
================================================================================
Date: 2026-04-02
Sources: Void RSPS legacy-rsps, current_fightcaves_demo, Void 634 cache

This audit covers every authoritative number, formula, and timing needed to
port the Fight Caves backend from Kotlin/Void to C. All values are extracted
from source code and data files, not approximated.

================================================================================
1. TICK LOOP — EXACT PHASE ORDERING
================================================================================

Source: GameTick.kt (current_fightcaves_demo)

One game tick = 600ms. Phases execute sequentially per tick:

  Phase 1:  PlayerResetTask       — clear per-tick visual flags
  Phase 2:  NPCResetTask          — clear per-tick NPC visual flags
  Phase 3:  Hunting               — NPC aggro/target scanning
  Phase 4:  NPCs (iteration)      — trigger NPC zone updates
  Phase 5:  FloorItems            — floor item processing
  Phase 6:  BackendControl        — RL action application (FC demo only)
  Phase 7:  InstructionTask       — client input processing (up to 20/player/tick)
  Phase 8:  World                 — world-level timers
  Phase 9:  NPCTask               — per-NPC: delay check, regen, AI, queue, mode.tick()
  Phase 10: PlayerTask            — per-player: delay check, queue, timers, mode.tick()
  Phase 11: FloorItemTracking     — floor item state changes
  Phase 12: GameObjects.timers    — object timers
  Phase 13: DynamicZones          — instance management
  Phase 14: ZoneBatchUpdates      — zone update encoding
  Phase 15: CharacterUpdateTask   — send visual updates to clients

CRITICAL: NPCTask (phase 9) runs BEFORE PlayerTask (phase 10).
NPC movement and AI executes before player movement each tick.

For our C backend, simplify to:
  1. Clear per-tick event flags
  2. Process player action (from RL or human input)
  3. Process NPC AI (hunt, target, attack decisions)
  4. Execute NPC movement (one step per tick)
  5. Execute player movement (one step per tick, or two if running)
  6. Resolve pending hits landing this tick
  7. Process deaths / splits / despawns
  8. Prayer drain tick
  9. HP regen tick (every 100 ticks)
  10. Wave clear check → advance wave
  11. Terminal condition check
  12. Emit state / fill render entities

================================================================================
2. NPC DEFINITIONS — AUTHORITATIVE DATA (from Void 634 cache + TOML)
================================================================================

NPC sizes extracted from Void 634 cache via NPCDecoder (opcode 12):

  Type          Void ID  Size  CL   HP    Att  Str  Def  Mag  Rng   AtkSpd  AtkStyle     AtkRange  MaxHit
  Tz-Kih        2734     1     22   100   20   30   15   15   30    4       melee(stab)  1         40
  Tz-Kek        2736     2     45   200   40   60   30   30   60    4       melee(crush) 1         70
  Tz-Kek(sm)    2738     1     22   100   20   30   15   15   30    4       melee(crush) 1         40
  Tok-Xil       2739     3     90   400   80   120  60   60   120   4       melee+range  1/14      130/140
  Yt-MejKot     2741     4     180  800   160  240  120  120  240   4       melee+heal   1         250
  Ket-Zek       2743     5     360  1600  320  480  240  240  480   4       melee+magic  1/14      540/490
  TzTok-Jad     2745     5     702  2500  640  960  480  480  960   8       multi        1/14      970/950
  Yt-HurKot     2746     1     108  600   140  100  60   120  120   4       melee        1         140

KEY SIZE CORRECTIONS FROM OUR PREVIOUS PLAN:
  - Tz-Kek is size 2 (was listed as size 2, correct)
  - Tok-Xil is size 3 (was listed as size 2, WRONG)
  - Yt-MejKot is size 4 (was listed as size 2, WRONG)
  - Ket-Zek is size 5 (was listed as size 3, WRONG)
  - TzTok-Jad is size 5 (was listed as size 3, WRONG — Jad and Ket-Zek are same collision size!)

This matters enormously for pathfinding, safespots, and collision.

================================================================================
3. COMBAT FORMULAS — EXACT MATH
================================================================================

Source: Hit.kt, Damage.kt (both codebases identical)

ACCURACY ROLL:
  if (offensiveRating > defensiveRating):
    hitChance = 1.0 - (defensiveRating + 2.0) / (2.0 * (offensiveRating + 1.0))
  else:
    hitChance = offensiveRating / (2.0 * (defensiveRating + 1.0))

OFFENSIVE RATING (NPC attacking):
  effectiveLevel = npcAttackLevel + 9  (no prayer/style bonuses for NPCs)
  equipmentBonus = npcAttackBonus
  offensiveRating = effectiveLevel * (equipmentBonus + 64)

DEFENSIVE RATING (player defending):
  For melee/ranged:
    effectiveLevel = playerDefenceLevel + 9  (+ prayer bonus if applicable)
    equipmentBonus = player.defenceBonus[attackType]  (stab/slash/crush/range/magic)
    defensiveRating = effectiveLevel * (equipmentBonus + 64)
  For magic attacks:
    magicDefLevel = floor(playerDefenceLevel * 0.3 + playerMagicLevel * 0.7) + 9
    equipmentBonus = player.defenceMagic
    defensiveRating = magicDefLevel * (equipmentBonus + 64)

DAMAGE ROLL:
  If hit succeeds: uniform random [0, maxHit]
  If miss: 0

MAX HIT (for player attacks, melee/ranged):
  5 + (effectiveLevel * strengthBonus) / 64

DAMAGE CLAMPED: damage = min(damage, target.currentHP)
  Overkill is lost. No negative HP.

================================================================================
4. PROJECTILE TIMING — EXACT VALUES
================================================================================

Source: tzhaar_fight_cave.gfx.toml, ShootProjectile.kt, Ticks.kt

CONVERSION: CLIENT_TICKS.toTicks(n) = n / 30 (integer division, floor)

Projectile travel time formula:
  time = timeOffset + (distance * multiplier)
  If timeOffset/multiplier not set: distance * 5 (default multiplier)

Hit delay in hit():
  gameTicks = CLIENT_TICKS.toTicks(clientDelay) + 1
  For melee: 0 (instant)

Per-NPC projectile timing:

  Tok-Xil ranged:
    projectile: tok_xil_shoot (ID 1616)
    delay: 32 client ticks, height: 256, curve: 16
    Travel: default (5 * distance)
    Hit delay: CLIENT_TICKS.toTicks(travel_time) + 1

  Ket-Zek magic:
    projectile: ket_zek_travel (ID 1623)
    delay: 28 client ticks, height: 128, curve: 16
    Travel: time_offset=8, multiplier=8 → 8 + (distance * 8) client ticks
    Hit delay: CLIENT_TICKS.toTicks(8 + distance*8) + 1

  TzTok-Jad magic:
    projectile: tztok_jad_travel (ID 1627)
    delay: 86 client ticks, height: 50, curve: 16
    Travel: multiplier=8 → distance * 8 client ticks
    Hit delay: CLIENT_TICKS.toTicks(distance * 8) + 1

  TzTok-Jad ranged:
    projectile: none (empty id), delay: 120 client ticks
    No travel projectile, hit delay: CLIENT_TICKS.toTicks(120) + 1 = 4 + 1 = 5 game ticks

JAD ATTACK TIMELINE (from JadTelegraph.kt):
  Tick 0: Jad starts attack animation (telegraph visible)
  Tick 3: strongQueue fires, hit() called (JAD_HIT_TARGET_QUEUE_TICKS = 3)
  Tick 3 + CLIENT_TICKS.toTicks(64) + 1 = 3 + 2 + 1 = 6: Damage resolves
  
  JAD_HIT_RESOLVE_OFFSET_TICKS = 6 ticks from attack start
  Prayer is checked at tick 6 (RESOLVE time, not fire time)

================================================================================
5. PRAYER SYSTEM — EXACT MECHANICS
================================================================================

Source: PrayerDrain.kt, prayers.toml

PROTECTION PRAYER DRAIN RATES (from prayers.toml):
  protect_from_magic:    drain = 12, group = 6, level = 37
  protect_from_missiles: drain = 12, group = 6, level = 40
  protect_from_melee:    drain = 12, group = 6, level = 43

DRAIN FORMULA (counter-based, NOT simple rate):
  Each tick:
    prayerDrainCounter += sum of all active prayer drain values
    prayerDrainResistance = 60 + (prayerBonus * 2)
    while (prayerDrainCounter > prayerDrainResistance):
      drain 1 prayer point
      prayerDrainCounter -= prayerDrainResistance

  Example with protect_from_melee (drain=12) and prayerBonus=0:
    resistance = 60 + 0 = 60
    Each tick: counter += 12
    After 5 ticks: counter = 60 → drain 1 point (counter resets to 0)
    = 1 prayer point per 5 ticks = 1 point per 3 seconds

  With prayerBonus=5 (from equipment):
    resistance = 60 + 10 = 70
    Each tick: counter += 12
    After 6 ticks: counter = 72 → drain 1 point (counter = 2)
    ≈ 1 prayer point per 5.8 ticks

PRAYER GROUPS (mutually exclusive):
  Group 6: protect_from_melee, protect_from_missiles, protect_from_magic
  Only one protect prayer active at a time.

PRAYER AT 0 POINTS:
  Timer stops, all active prayers deactivated, message shown.

PVM PROTECTION:
  Against NPCs: Protection prayer blocks 100% of matching attack style.
  Against players: 60% reduction (not relevant for Fight Caves).

TZ-KIH PRAYER DRAIN:
  On hit impact: flat -1 prayer point (not scaled by anything).
  Defined in combat.toml: impact_drain = { skill = "prayer", amount = 1 }

================================================================================
6. CONSUMABLE TIMING — EXACT VALUES
================================================================================

Source: Eating.kt, Potions.kt

FOOD (SHARKS):
  Heal: 200 HP (flat, not a range — heals field = 200)
  Delay: 3 game ticks (food_delay clock)
  Animation: "eat_drink"
  No lockout on movement, prayer, or attack while eating

PRAYER POTIONS:
  Restore: 7 + floor(prayerLevel * 0.25) prayer points
  With holy wrench/prayer cape: 7 + floor(prayerLevel * 0.27)
  For our setup (prayer 43, no holy item): 7 + floor(43 * 0.25) = 7 + 10 = 17 points
  Delay: 2 game ticks (drink_delay clock) — NOT 3 like food
  Doses: 4 per potion, decrements on use (_4 → _3 → _2 → _1 → vial)
  Default: 8 potions = 32 doses total

COMBO EATING RULES:
  Food and potions use SEPARATE delay clocks:
    food_delay (3 ticks) — blocks next food
    drink_delay (2 ticks) — blocks next potion
  Can eat food + drink potion ON THE SAME TICK
  Can eat/drink + prayer switch on same tick (no interaction restriction)

DELAY CHECKS:
  If player.hasClock("food_delay") → cannot eat
  If player.hasClock("drink_delay") → cannot drink

================================================================================
7. PLAYER CONFIGURATION — FIXED LOADOUT
================================================================================

Source: FightCaveEpisodeInitializer.kt

SKILL LEVELS:
  Attack: 1, Strength: 1, Defence: 70, Constitution: 700 (70 HP)
  Ranged: 70, Prayer: 43, Magic: 1

EQUIPMENT:
  Slot 0  (head):   Coif
  Slot 3  (weapon): Rune Crossbow
  Slot 4  (chest):  Black Dragonhide Body
  Slot 7  (legs):   Black Dragonhide Chaps
  Slot 9  (hands):  Black Dragonhide Vambraces
  Slot 10 (feet):   Snakeskin Boots
  Slot 13 (ammo):   Adamant Bolts (1000)

INVENTORY:
  Prayer Potion (4): 8 potions (32 doses)
  Shark: 20

EQUIPMENT BONUSES (must be calculated from item definitions):
  Need to look up: ranged_attack, ranged_strength, defence bonuses for each slot.

================================================================================
8. NPC AI & MOVEMENT
================================================================================

Source: Hunting.kt, Movement.kt, hunt_modes.toml, Follow.kt

AGGRO MODE: aggressive_intolerant (all FC NPCs)
  - Requires line of sight
  - Hunt range: 64 tiles
  - No 10-minute tolerance timeout
  - Re-checks every 4 ticks (hunt rate)

MOVEMENT:
  Walk: 1 tile per tick (all NPCs)
  NPCs do NOT run in Fight Caves.
  
  Step algorithm:
    1. Try diagonal toward target (dx, dy)
    2. If blocked, try horizontal (dx, 0)
    3. If blocked, try vertical (0, dy)
    4. If all blocked, stop

NPC COLLISION:
  NPCs CAN walk through each other (no NPC-to-NPC collision).
  Only collision is with terrain/objects.
  Size-aware pathfinding: large NPCs check all tiles in footprint.

PATHFINDING:
  Algorithm: RSMod A* (external library)
  Recalculates when: steps queue empty OR target moved
  Size-aware: passes character.size to pathfinder

NPC HEALTH REGEN:
  Every 25 ticks, if NPC has no target, restore 1 HP.
  (Not significant in FC since NPCs always have a target)

================================================================================
9. PER-NPC SPECIAL BEHAVIORS
================================================================================

TZ-KIH (lv22, melee):
  - Chase and melee. Drains 1 prayer point on hit impact.
  - No other special behavior.

TZ-KEK (lv45, melee):
  - Chase and melee.
  - SPLIT ON DEATH: spawns 2 "tz_kek_spawn" (small, lv22) at death tile.
  - Small Tz-Kek has half stats but max hit 40 (crush).
  - Wave "remaining" counter: Tz-Kek counts as 2 (for the split).
    Code: ids.sumOf { if (it == "tz_kek" || it == "tz_kek_spawn_point") 2 else 1 }

TOK-XIL (lv90, ranged+melee):
  - Dual attack mode: melee (crush, max 130) at range 1, ranged (max 140) at range 14.
  - No explicit range maintenance — attacks opportunistically based on distance.

YT-MEJKOT (lv180, melee+heal):
  - Melee: crush, max 250, range 1.
  - HEAL: Condition "weakened_nearby_monsters" — heals nearby NPCs with HP < 50% of max.
  - Heal amount: 100 HP per heal action.
  - Heals NPCs within adjacent zones (roughly 8-tile radius).
  - Single heal per attack cycle.

KET-ZEK (lv360, magic+melee):
  - Magic: max 490, range 14, projectile ket_zek_travel.
  - Melee: stab, max 540, range 1.
  - No explicit mode switch logic — combat system picks based on range/chance.

TZTOK-JAD (lv702, all styles):
  - Melee: stab, max 970, range 1.
  - Ranged: max 970, range 14, no visible projectile, hit delay 5 ticks.
  - Magic: max 950, range 14, projectile tztok_jad_travel.
  - Attack speed: 8 ticks (double other NPCs).
  - TELEGRAPH: visible wind-up animation before attack.
  - Attack selection: weighted random from valid attacks.
  - Does not move during attack animations.

  HEALER SPAWN:
    Trigger: Jad HP drops below 50% of max (1250 HP).
    Count: 4 healers max. Fills missing slots (4 - current_alive).
    Respawn: Only if Jad was healed back to >50% and drops below again.
    Code: npcLevelChanged checks: half !in to..<from

YT-HURKOT (lv108, healer):
  - Spawned by Jad healer trigger, not by wave table.
  - Movement: Follow mode targeting Jad.
  - Heal: 50 HP to Jad every 4 ticks when within 5 tiles.
  - DISTRACTION: If player attacks healer, it retargets player (stops healing).
  - Melee: crush, max 140, range 1.

================================================================================
10. WAVE SYSTEM
================================================================================

Source: tzhaar_fight_cave_waves.toml, TzhaarFightCaveWaves.kt

63 waves, 15 rotations. Rotation chosen randomly at wave 1.

SPAWN AREAS (tile ranges, pre-instance-offset):
  NORTH_WEST:  x=[2378, 2385], y=[5102, 5109]  (8x8)
  SOUTH_WEST:  x=[2379, 2385], y=[5070, 5076]  (7x7)
  SOUTH:       x=[2402, 2408], y=[5070, 5076]  (7x7)
  SOUTH_EAST:  x=[2416, 2422], y=[5080, 5086]  (7x7)
  CENTER:      x=[2397, 2403], y=[5085, 5091]  (7x7)

PLAYER START: Tile(2400, 5088) + instance offset

WAVE CLEAR DETECTION:
  Counter: fight_cave_remaining (decremented on npcDespawn)
  When counter reaches 0: next wave starts immediately (no delay)
  Wave 63 Jad kill: episode terminal (cave complete)

REMAINING COUNT FORMULA:
  For each NPC in spawn list:
    if (npc == "tz_kek" || npc == "tz_kek_spawn_point"): count += 2
    else: count += 1

MAX CONCURRENT NPCs: 6 (wave 58)

================================================================================
11. ACTION SYSTEM
================================================================================

Source: FightCaveBackendActionAdapter.kt

ONE ACTION PER TICK (enforced via LAST_ACTION_TICK_KEY)

ACTION TYPES:
  0: Wait            — no-op
  1: WalkToTile      — absolute tile (x, y, level)
  2: AttackVisibleNpc — NPC index in sorted visible list
  3: ToggleProtectionPrayer — prayer enum (magic/missiles/melee)
  4: EatShark         — consume shark from inventory
  5: DrinkPrayerPotion — consume prayer potion dose
  6: ToggleRun        — toggle running

REJECTION REASONS:
  AlreadyActedThisTick, InvalidTargetIndex, TargetNotVisible,
  PlayerBusy, MissingConsumable, ConsumptionLocked,
  PrayerPointsDepleted, InsufficientRunEnergy, NoMovementRequired

NPC SORTING (for AttackVisibleNpc index):
  Sorted by: level, x, y, id, index (ascending)

================================================================================
12. OBSERVATION SCHEMA
================================================================================

Source: FightCaveHeadedObservationBuilder.kt

Schema ID: "headless_observation_v1"

Player state:
  tile (x, y, level), hitpoints_current/max, prayer_current/max,
  run_energy/max/percent, running,
  protection_prayers (magic, missiles, melee — booleans),
  lockouts (attack_locked, food_locked, drink_locked, combo_locked, busy_locked),
  consumables (shark_count, prayer_potion_dose_count, ammo_id, ammo_count)

Wave state:
  wave, rotation, remaining

NPC array (sorted by level/x/y/id/index):
  visible_index, npc_index, id, tile, hitpoints_current/max,
  hidden, dead, under_attack,
  jad_telegraph_state (0=Idle, 1=MagicWindup, 2=RangedWindup)

================================================================================
13. TERMINAL CONDITIONS
================================================================================

  1. Player death (HP reaches 0)           → TERMINAL_PLAYER_DEATH
  2. Cave complete (wave 63 Jad killed)    → TERMINAL_CAVE_COMPLETE
  3. Tick cap (30,000 ticks in our C env)  → TERMINAL_TICK_CAP
     (Note: RSPS has no tick cap — our C backend adds this for RL safety)

No explicit delay between wave clear and next wave start.
Pending projectiles resolve normally during wave transitions.

================================================================================
14. ANIMATION IDS (Void 634)
================================================================================

Source: tzhaar_fight_cave.anims.toml

  tz_kih_attack: 9232    tz_kih_defend: 9231    tz_kih_death: 9230
  tz_kek_attack: 9233    tz_kek_defend: 9235    tz_kek_death: 9234
  tok_xil_attack: 9245   tok_xil_defend: 9242   tok_xil_death: 9239   tok_xil_shoot: 9243
  yt_mej_kot_attack: 9246 yt_mej_kot_defend: 9248 yt_mej_kot_death: 9247 yt_mej_kot_heal: 9249
  ket_zek_attack: 9265   ket_zek_attack_magic: 9266 ket_zek_defend: 9268 ket_zek_death: 9269
  tztok_jad_attack_melee: 9277 tztok_jad_attack_range: 9276 tztok_jad_attack_magic: 9300
  tztok_jad_defend: 9278 tztok_jad_death: 9279
  yt_hur_kot_attack: 9252 yt_hur_kot_defend: 9253 yt_hur_kot_death: 9257 yt_hur_kot_heal: 9254

These are Void 634 animation IDs. Must be mapped to b237 equivalents for viewer.

================================================================================
15. GRAPHICS / PROJECTILE IDS (Void 634)
================================================================================

Source: tzhaar_fight_cave.gfx.toml

  tzhaar_heal: 444           (healer heal effect)
  ket_zek_attack: 1622       (Ket-Zek source gfx)
  ket_zek_travel: 1623       (projectile, delay=28, h=128, offset=8, mult=8, curve=16)
  ket_zek_impact: 1624       (impact gfx)
  tok_xil_shoot: 1616        (projectile, delay=32, h=256, curve=16)
  tztok_jad_stomp: 1625      (Jad ranged source gfx)
  tztok_jad_magic: 1626      (Jad magic source gfx)
  tztok_jad_travel: 1627     (projectile, delay=86, h=50, mult=8, curve=16)
  tztok_jad_range_impact: 451 (Jad ranged impact gfx)

================================================================================
16. GAPS / CORRECTIONS TO PREVIOUS PLAN
================================================================================

CORRECTIONS:
  1. NPC sizes were wrong for Tok-Xil (3 not 2), Yt-MejKot (4 not 2),
     Ket-Zek (5 not 3), Jad (5 not 3). These are critical for pathfinding.
  2. Potion delay is 2 ticks, not 3. Food is 3 ticks. Different clocks.
  3. Prayer drain is counter-based (accumulate → threshold → drain 1), not a simple rate.
  4. Shark heals exactly 200 (flat), not a random range.
  5. Jad hit resolves at tick 6 from attack start (not 7).
     CLIENT_TICKS.toTicks(64) = 64/30 = 2 (floor division), total = 3+2+1 = 6.
  6. Prayer is checked at RESOLVE time in FC demo (not fire time).
  7. NPCs can walk through each other (no NPC-NPC collision).
  8. NPC AI re-checks targets every 4 ticks (hunt rate), not every tick.

SYSTEMS IN PLAN THAT NEED CLARIFICATION:
  - Equipment bonuses: Need exact numbers from item definitions for the fixed loadout.
    Must calculate: ranged_attack, ranged_strength, defence_stab/slash/crush/magic/ranged,
    prayer bonus — from Coif, Rune Crossbow, Black D'hide set, Snakeskin Boots.
  - Player attack speed: depends on Rune Crossbow (5 ticks for crossbow).
  - Player attack range: Rune Crossbow = 7 tiles.
  - Ranged max hit: 5 + (effectiveRangedLevel * rangedStrengthBonus) / 64

SYSTEMS NOT IN ORIGINAL PLAN:
  - Run energy system (toggle run, drain/restore — relevant if running enabled)
  - NPC health regen (1 HP per 25 ticks if no target — minor)
  - Action rejection/validation with specific reason codes
  - NPC spawn point variants (2735, 2737, 2740, 2742, 2744 — different NPCs with
    same stats but different spawning behavior in wave tables)
  - Yt-MejKot heal condition: heals NPCs with HP < 50% of max, not just any damaged NPC
  - Yt-HurKot heal distance: 5 tiles from Jad to start healing
  - Healer respawn requires Jad to be healed back above 50% first
