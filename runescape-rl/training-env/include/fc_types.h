#ifndef FC_TYPES_H
#define FC_TYPES_H

#include <stdint.h>

/*
 * fc_types.h — Core data structures for Fight Caves simulation.
 *
 * Design rules (from PufferLib OSRS PvP reference):
 *   - All state is flat fields on structs. No nested pointers, no heap alloc per tick.
 *   - Enables cache locality, fast memset reset, linear observation generation.
 *   - All structs are zeroed on reset via memset; zero must be a safe default for every field.
 *
 * PR 2 combat note — PvM prayer semantics:
 *   Protection prayers in Fight Caves BLOCK 100% of the matching NPC attack style.
 *   This is standard OSRS PvM behavior, NOT the PvP 60% reduction from osrs_pvp.
 *   Exceptions (e.g. TzTok-Jad hits through wrong prayer) must be explicit per NPC/attack.
 *   See fc_combat.c (PR 2) for implementation.
 */

/* ======================================================================== */
/* Enums                                                                     */
/* ======================================================================== */

typedef enum {
    ENTITY_PLAYER = 0,
    ENTITY_NPC    = 1
} FcEntityType;

/* NPC type codes — map to OSRS Fight Caves monsters */
typedef enum {
    NPC_NONE       = 0,
    NPC_TZ_KIH     = 1,  /* Lv 22, melee, drains prayer on hit */
    NPC_TZ_KEK     = 2,  /* Lv 45, melee, splits into 2 small on death */
    NPC_TZ_KEK_SM  = 3,  /* Lv 22, melee, small Tz-Kek spawn (from split) */
    NPC_TOK_XIL    = 4,  /* Lv 90, ranged */
    NPC_YT_MEJKOT  = 5,  /* Lv 180, melee + heals nearby NPCs */
    NPC_KET_ZEK    = 6,  /* Lv 360, magic (primary) + melee */
    NPC_TZTOK_JAD  = 7,  /* Lv 702, magic + ranged, prayer switching */
    NPC_YT_HURKOT  = 8,  /* Jad healer, heals Jad if not distracted */
    NPC_TYPE_COUNT  = 9
} FcNpcType;

/* Attack styles */
typedef enum {
    ATTACK_NONE   = 0,
    ATTACK_MELEE  = 1,
    ATTACK_RANGED = 2,
    ATTACK_MAGIC  = 3
} FcAttackStyle;

/* Protection prayers */
typedef enum {
    PRAYER_NONE          = 0,
    PRAYER_PROTECT_MELEE = 1,
    PRAYER_PROTECT_RANGE = 2,
    PRAYER_PROTECT_MAGIC = 3
} FcPrayer;

/*
 * Jad telegraph state — visible wind-up before attack lands.
 * PR 2 note: Jad raises legs (ranged) or rears back (magic) for ~3 ticks
 * before the hit is queued. The agent must read this telegraph and switch
 * prayer before the hit resolves.
 */
typedef enum {
    JAD_TELEGRAPH_IDLE          = 0,
    JAD_TELEGRAPH_MAGIC_WINDUP  = 1,
    JAD_TELEGRAPH_RANGED_WINDUP = 2
} FcJadTelegraph;

/* Terminal state codes */
typedef enum {
    TERMINAL_NONE          = 0,
    TERMINAL_PLAYER_DEATH  = 1,
    TERMINAL_CAVE_COMPLETE = 2,
    TERMINAL_TICK_CAP      = 3
} FcTerminalCode;

/* NPC spawn direction for wave rotations */
typedef enum {
    SPAWN_SOUTH      = 0,
    SPAWN_SOUTH_WEST = 1,
    SPAWN_NORTH_WEST = 2,
    SPAWN_SOUTH_EAST = 3,
    SPAWN_CENTER     = 4
} FcSpawnDir;

/* ======================================================================== */
/* Constants                                                                 */
/* ======================================================================== */

/* Arena */
#define FC_ARENA_WIDTH   64
#define FC_ARENA_HEIGHT  64

/* Entity limits */
#define FC_MAX_NPCS      16   /* max simultaneous NPCs in the arena */
#define FC_VISIBLE_NPCS   8   /* max NPCs in observation (see fc_contracts.h) */
#define FC_MAX_PENDING_HITS 8 /* per entity pending hit queue */

/* Waves */
#define FC_NUM_WAVES     63
#define FC_NUM_ROTATIONS 15

/* Consumables (standard Fight Caves loadout) */
#define FC_MAX_SHARKS            20
#define FC_MAX_PRAYER_DOSES      32  /* 8 potions × 4 doses */

/* Tick timing */
#define FC_FOOD_COOLDOWN_TICKS    3
#define FC_POTION_COOLDOWN_TICKS  3
#define FC_COMBO_EAT_TICKS        1  /* karambwan combo delay after food */
#define FC_MAX_EPISODE_TICKS   30000 /* ~5 hours at 0.6s/tick */

/* Player base stats (standard Fight Caves setup) */
#define FC_PLAYER_MAX_HP        700  /* 70 HP in tenths */
#define FC_PLAYER_MAX_PRAYER    430  /* 43 prayer points in tenths */
#define FC_PLAYER_DEFENCE_LVL    70
#define FC_PLAYER_RANGED_LVL     70
#define FC_PLAYER_PRAYER_LVL     43

/* ======================================================================== */
/* Pending Hit (projectile in flight or delayed melee)                       */
/* ======================================================================== */

/*
 * Attacks queue a PendingHit with a tick delay before damage applies.
 * At resolve time, the ACTIVE prayer is checked — not the prayer at fire time.
 * This models OSRS projectile flight:
 *   Melee:  0 tick delay (instant)
 *   Ranged: 1 + floor((3 + distance) / 6) ticks
 *   Magic:  1 + floor((1 + distance) / 3) ticks
 *
 * PR 2 note: Protection prayer blocks 100% damage if correct style match.
 * Unlike PvP (60% reduction), PvM prayer fully blocks the hit.
 * Exception: Jad attacks always deal damage if WRONG prayer is active.
 */
typedef struct {
    int active;           /* 1 if this slot is in use */
    int damage;           /* pre-prayer damage roll (0 = miss) */
    int ticks_remaining;  /* ticks until hit resolves */
    int attack_style;     /* FcAttackStyle of the incoming hit */
    int source_npc_idx;   /* index into FcState.npcs[] of the attacker */
    int prayer_drain;     /* prayer points drained on hit (Tz-Kih) */
} FcPendingHit;

/* ======================================================================== */
/* Player                                                                    */
/* ======================================================================== */

typedef struct {
    /* Position */
    int x, y;

    /* Vitals (in tenths for precision: 700 = 70.0 HP) */
    int current_hp, max_hp;
    int current_prayer, max_prayer;

    /* Active prayer */
    int prayer;  /* FcPrayer enum */

    /* Consumables */
    int sharks_remaining;
    int prayer_doses_remaining;

    /* Timers (tick countdown, 0 = ready) */
    int attack_timer;
    int food_timer;
    int potion_timer;
    int combo_timer;    /* karambwan combo eat delay */

    /* Run */
    int run_energy;     /* 0-10000 (100.00%) */
    int is_running;     /* 1 if run mode active */

    /* Combat stats */
    int defence_level;
    int ranged_level;

    /* Equipment bonuses (simplified for Fight Caves fixed loadout) */
    int ranged_attack_bonus;
    int ranged_strength_bonus;
    int defence_stab, defence_slash, defence_crush;
    int defence_magic, defence_ranged;
    int prayer_bonus;

    /* Ammo */
    int ammo_count;

    /* Pending hits (from NPC attacks in flight) */
    FcPendingHit pending_hits[FC_MAX_PENDING_HITS];
    int num_pending_hits;

    /* Per-tick event flags (cleared each tick, used for obs/reward/hitsplats) */
    int damage_taken_this_tick;
    int hit_landed_this_tick;
    int food_eaten_this_tick;
    int potion_used_this_tick;
    int prayer_changed_this_tick;

    /* Cumulative stats (for reward/logging) */
    int total_damage_dealt;
    int total_damage_taken;
    int total_food_eaten;
    int total_potions_used;
} FcPlayer;

/* ======================================================================== */
/* NPC                                                                       */
/* ======================================================================== */

typedef struct {
    /* Identity */
    int active;       /* 1 if alive and in the arena */
    int npc_type;     /* FcNpcType */
    int spawn_index;  /* unique index across the episode, stable for NPC slot ordering */

    /* Position */
    int x, y;
    int size;         /* tile size: 1 for most, 2+ for larger NPCs */

    /* Vitals */
    int current_hp, max_hp;
    int is_dead;

    /* Combat */
    int attack_style;       /* FcAttackStyle: what this NPC attacks with */
    int attack_timer;       /* tick countdown to next attack */
    int attack_speed;       /* ticks between attacks */
    int attack_range;       /* tile distance for ranged/magic, 1 for melee */
    int max_hit;            /* max damage this NPC can roll */

    /* AI */
    int aggro_target;       /* -1 = no target, 0 = player (always 0 in FC) */
    int movement_speed;     /* 1 = walk, 2 = run */

    /* Jad-specific */
    int jad_telegraph;      /* FcJadTelegraph: current wind-up state */
    int jad_next_style;     /* FcAttackStyle: the queued attack style */

    /* Yt-MejKot healing */
    int heal_timer;         /* ticks until next heal attempt */
    int heal_amount;        /* HP healed per proc */

    /* Yt-HurKot (Jad healer) */
    int is_healer;
    int healer_distracted;  /* 1 if player has attacked this healer */
    int heal_target_idx;    /* NPC index of the entity being healed (Jad) */

    /* Per-tick event flags */
    int damage_taken_this_tick;
    int died_this_tick;

    /* Pending hits (player attacks in flight toward this NPC) */
    FcPendingHit pending_hits[FC_MAX_PENDING_HITS];
    int num_pending_hits;
} FcNpc;

/* ======================================================================== */
/* Wave entry (spawn table row)                                              */
/* ======================================================================== */

#define FC_MAX_SPAWNS_PER_WAVE 6

typedef struct {
    int npc_types[FC_MAX_SPAWNS_PER_WAVE];  /* FcNpcType per spawn */
    int num_spawns;
} FcWaveEntry;

/* ======================================================================== */
/* Render entity (value type for viewer — filled by fc_fill_render_entities) */
/* ======================================================================== */

/*
 * The viewer never reads FcPlayer/FcNpc directly. It receives an array of
 * these render entities via the fc_fill_render_entities callback.
 * This decouples rendering from simulation internals.
 */
typedef struct {
    int entity_type;    /* FcEntityType */
    int npc_type;       /* FcNpcType (0 for player) */
    int x, y;
    int size;           /* tile size */
    int current_hp, max_hp;
    int attack_style;   /* FcAttackStyle: current/last attack style */
    int prayer;         /* FcPrayer: active prayer (player only) */
    int jad_telegraph;  /* FcJadTelegraph (NPCs only) */
    int is_dead;
    int aggro_target;   /* entity index of aggro target (-1 = none) */

    /* Per-tick events for hitsplat/animation rendering */
    int damage_taken_this_tick;
    int hit_landed_this_tick;
    int died_this_tick;
} FcRenderEntity;

#define FC_MAX_RENDER_ENTITIES (1 + FC_MAX_NPCS)  /* player + NPCs */

/* ======================================================================== */
/* Top-level simulation state                                                */
/* ======================================================================== */

typedef struct {
    FcPlayer player;
    FcNpc npcs[FC_MAX_NPCS];

    /* Wave progression */
    int current_wave;       /* 1-indexed: 1..63. 0 = not started */
    int rotation_id;        /* 0..14, selected at episode start */
    int npcs_remaining;     /* count of active (alive) NPCs in current wave */
    int total_npcs_killed;
    int next_spawn_index;   /* monotonic counter for NPC spawn ordering */

    /* Tick */
    int tick;

    /* Terminal */
    int terminal;           /* FcTerminalCode */

    /* RNG — XORshift32, single state, seeded at reset */
    uint32_t rng_state;
    uint32_t rng_seed;      /* saved for replay */

    /* Arena walkability (1 = walkable, 0 = obstacle) */
    uint8_t walkable[FC_ARENA_WIDTH][FC_ARENA_HEIGHT];

    /* Jad healer state */
    int jad_healers_spawned;  /* 1 if Yt-HurKot healers already spawned this wave */

    /* Per-tick aggregated event flags (for reward features) */
    int damage_dealt_this_tick;
    int damage_taken_this_tick;
    int npcs_killed_this_tick;
    int wave_just_cleared;
    int jad_damage_this_tick;
    int jad_killed;
    int correct_jad_prayer;
    int wrong_jad_prayer;
    int invalid_action_this_tick;
    int movement_this_tick;
    int idle_this_tick;
    int food_used_this_tick;
    int prayer_potion_used_this_tick;
} FcState;

#endif /* FC_TYPES_H */
