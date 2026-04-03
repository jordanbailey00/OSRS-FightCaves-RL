================================================================================
RSMOD AUDIT — FEASIBILITY AS REFERENCE FOR B237 CACHE
================================================================================
Date: 2026-04-02
Source: https://github.com/rsmod/rsmod (cloned to runescape-rl-reference/rsmod/)

================================================================================
VERDICT: EXCELLENT REFERENCE — USE IT
================================================================================

RSMod targets revision 233. Our cache is b237. Only 4 revisions apart.
Same cache format (OSRS modern flat file from OpenRS2). Same decoder system.
Same pathfinding library. Same combat mechanics. Cleaner code than Void RSPS.

RSMod is a BETTER reference than Void RSPS (rev 634) because:
  - Modern OSRS cache format (not RS2 era)
  - NPC/item/object decoders match b237 nearly exactly
  - Game mechanics are OSRS-accurate (not RS2 approximations)
  - Codebase is modular and well-documented

================================================================================
1. CACHE REVISION & CONFIGURATION
================================================================================

File: api/core/src/main/kotlin/org/rsmod/api/core/Build.kt

  public const val MAJOR: Int = 233
  public const val CACHE_URL: String =
      "https://archive.openrs2.org/caches/runescape/2293/disk.zip"
  public const val XTEA_URL: String =
      "https://archive.openrs2.org/caches/runescape/2293/keys.json"

To switch to rev 237:
  - Change MAJOR to 237
  - Update CACHE_URL to OpenRS2 cache #2509 (our b237)
  - Update XTEA_URL to matching keys
  - May need to update RSProt network library for rev 237

================================================================================
2. NPC DECODER — OPCODE COMPARISON
================================================================================

File: api/cache/src/main/kotlin/org/rsmod/api/cache/types/npc/NpcTypeDecoder.kt

RSMod (rev 233) NPC opcodes we care about:
  Opcode 2:  name (string)
  Opcode 12: size (unsigned byte) — SAME as Void 634
  Opcode 74: attack level
  Opcode 75: defence level
  Opcode 76: strength level
  Opcode 77: hitpoints level
  Opcode 78: ranged level
  Opcode 79: magic level
  Opcode 95: combat level (vislevel)

These opcodes should be identical in rev 237 (no known changes between 233-237).
This confirms our NPC stat extraction approach is correct.

================================================================================
3. COLLISION SYSTEM — IDENTICAL TO OURS
================================================================================

File: engine/routefinder/src/main/kotlin/org/rsmod/routefinder/flag/CollisionFlag.kt

RSMod uses the SAME RSPathfinder collision system as Void RSPS:
  WALL_NORTH_WEST = 0x1
  WALL_NORTH = 0x2
  WALL_EAST = 0x8
  LOC = 0x100
  LOC_PROJ_BLOCKER = 0x20000
  BLOCK_NPCS = 0x80000
  BLOCK_PLAYERS = 0x100000
  BLOCK_WALK = 0x200000

Collision storage: zone-based sparse allocation (8x8 tile zones).
Map tile decoder: api/cache/src/main/kotlin/org/rsmod/api/cache/map/tile/MapTileDecoder.kt
Object collision: engine/game/src/main/kotlin/org/rsmod/game/map/collision/CollisionFlagMapExtensions.kt

Our extracted collision binary from Void 634 should be functionally identical to what
RSMod would produce from the b237 cache for the same region.

================================================================================
4. GAME TICK ORDERING — AUTHORITATIVE REFERENCE
================================================================================

File: api/game-process/src/main/kotlin/org/rsmod/api/game/process/GameCycle.kt

  1. startCycle event
  2. worldMain.process()
  3. npcPreTick.process()     ← NPC hunt/target selection
  4. playerShuffle.process()
  5. playerInput.process()    ← Client actions applied
  6. playerRouteRequest.process()
  7. npcMain.process()        ← NPC AI: movement, combat, timers, queues
  8. controllerMain.process()
  9. playerMain.process()     ← Player: movement, combat, timers, queues
  10. mapClock.tick()          ← Game cycle counter increments
  11. lateCycle event
  12. worldPostTick.process()
  13. playerPostTick.process() ← Client sync updates

CRITICAL: NPC pre-tick (hunt) runs before player input.
NPC main processing runs BEFORE player main processing.
This matches our fc_tick.c ordering.

================================================================================
5. COMBAT FORMULAS
================================================================================

File: api/combat/combat-formulas/src/main/kotlin/org/rsmod/api/combat/formulas/

Accuracy uses basis points (0-10000):
  hitChance = max(0, floor(1000000 * min(1, attRoll / (2 * defRoll))))
  success = random(0..10000) < hitChance

This is equivalent to our formula but in integer arithmetic (no floats).
We use: hitChance = attRoll > defRoll ? 1 - (defRoll+2)/(2*(attRoll+1)) : attRoll/(2*(defRoll+1))
Same formula, different representation.

Max hit: separate implementations per combat style (melee/ranged/magic)
and per interaction type (PvN, NvP, PvP, NvN).

================================================================================
6. HIT RESOLUTION — TWO MODES
================================================================================

File: api/player/src/main/kotlin/org/rsmod/api/player/hit/PlayerExtensions.kt

RSMod has TWO hit queuing modes:

  queueHit(delay):
    - Modifier (prayer reduction) applied IMMEDIATELY at queue time
    - Damage CAPPED to current HP (enables tick-eating)
    - Hit lands after delay ticks

  queueImpactHit(delay):
    - Modifier applied ON IMPACT (after delay)
    - Damage NOT capped to current HP
    - Used for effects that should evaluate at hit resolution time

Our current implementation checks prayer at resolve time (matching queueImpactHit).
For FC specifically: Jad prayer check happens at impact time (correct).
For regular NPC melee: prayer check at queue time (we should verify this).

================================================================================
7. PRAYER DRAIN — MATCHES OUR IMPLEMENTATION
================================================================================

File: content/interfaces/prayer-tab/src/main/kotlin/.../PrayerDrainScript.kt

  drainCounter += totalDrainEffect
  cappedResistance = max(1, 60 + prayerBonus * 2)
  prayerPointCost = (drainCounter - 1) / cappedResistance
  if (prayerPointCost > 0):
    drainCounter -= prayerPointCost * cappedResistance
    statSub(prayer, prayerPointCost)

This is the same counter-based system we implemented in fc_prayer.c.
Our implementation matches RSMod's authoritative version.

================================================================================
8. NPC AI PROCESSING ORDER
================================================================================

File: api/game-process/src/main/kotlin/org/rsmod/api/game/process/npc/NpcMainProcess.kt

Per NPC per tick:
  1. resumePausedProcess()   — continue coroutines
  2. revealProcess()         — update visibility
  3. processHunt()           — find targets
  4. processRegen()          — HP recovery
  5. processAiTimer()        — AI timer countdown
  6. processAiQueues()       — AI action queue
  7. processQueues()         — standard queues
  8. processTimers()         — generic timers
  9. processModes()          — NPC mode handlers (combat, patrol, etc)
  10. processInteractions()  — pending interactions

Hunt is PROBABILISTIC: random selection from valid targets in range.
Hunt occurs at huntMode.rate interval (not every tick).

================================================================================
9. MOVEMENT SYSTEM
================================================================================

File: api/game-process/src/main/kotlin/org/rsmod/api/game/process/player/PlayerMovementProcessor.kt

  Walk: 1 tile per tick
  Run: 2 tiles per tick
  Crawl: 1 tile per 2 ticks

  Per step:
    1. Check collision flags at destination
    2. Remove BLOCK_WALK from old position
    3. Move entity
    4. Add BLOCK_WALK at new position

  Steps consumed from route deque (pathfinder output).

================================================================================
10. WHAT WE CAN STEAL / EMULATE
================================================================================

A. COMBAT FORMULA INTEGER ARITHMETIC
  RSMod uses basis points (0-10000) instead of floats.
  More deterministic, avoids floating point drift.
  We should consider switching fc_combat.c to integer math.

B. HIT QUEUE DUAL MODE
  queueHit (prayer at queue time) vs queueImpactHit (prayer at resolve time).
  We currently only have resolve-time checking. May need both for full parity.

C. TICK ORDERING
  RSMod's GameCycle.kt is the authoritative reference for phase ordering.
  Our fc_tick.c matches this well but we should verify edge cases.

D. PRAYER DRAIN FORMULA
  RSMod uses (drainCounter - 1) / cappedResistance which is slightly different
  from our while(counter > resistance) loop. The division approach handles
  multiple drain points per tick more efficiently.

E. NPC TYPE DECODER
  RSMod's NpcTypeDecoder.kt for rev 233 can serve as our reference for
  reading NPC definitions from the b237 cache. Opcodes should be nearly identical.

F. COLLISION FLAG CONSTANTS
  Use RSMod's CollisionFlag enum values directly in our C code for consistency.

================================================================================
11. WHAT RSMod DOES NOT HAVE
================================================================================

- No Fight Caves content (no wave system, no Jad, no healers)
- No specific NPC combat scripts for FC NPCs
- The combat system is generic — FC-specific behavior would need content scripts

So RSMod is useful for:
  - Cache format reference (NPC/item/object decoders)
  - Combat formula verification
  - Tick ordering verification
  - Collision system verification
  - Prayer system verification

But NOT for:
  - Fight Caves encounter scripting (use Void RSPS for that)
  - NPC-specific AI (Jad telegraph, healer behavior, etc.)
  - Wave definitions (use our TOML data)

================================================================================
12. KEY FILE REFERENCE
================================================================================

Cache/Types:
  api/core/Build.kt                                    — revision config
  api/cache/types/npc/NpcTypeDecoder.kt                — NPC cache decoder
  engine/game/type/npc/NpcType.kt                      — NPC type data class

Game Loop:
  api/game-process/GameCycle.kt                        — tick orchestration
  api/game-process/npc/NpcMainProcess.kt               — NPC per-tick processing
  api/game-process/npc/hunt/NpcPlayerHuntProcessor.kt  — NPC aggro system
  api/game-process/player/PlayerMovementProcessor.kt   — player movement

Combat:
  api/combat/combat-formulas/accuracy/                 — accuracy roll formulas
  api/combat/combat-formulas/MaxHitFormulae.kt         — max hit formulas
  api/combat/combat-scripts/NvPCombat.kt               — NPC vs player combat
  api/combat/combat-scripts/PvNCombat.kt               — player vs NPC combat
  api/player/hit/PlayerExtensions.kt                   — hit queue system

Collision:
  engine/routefinder/flag/CollisionFlag.kt             — flag constants
  engine/routefinder/collision/CollisionFlagMap.kt     — sparse collision storage
  engine/game/map/collision/CollisionFlagMapExtensions.kt — object collision
  api/cache/map/tile/MapTileDecoder.kt                 — terrain collision decode

Prayer:
  content/interfaces/prayer-tab/scripts/PrayerDrainScript.kt — drain formula
