#include "fc_api.h"
#include "fc_wave.h"
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

/*
 * fc_state.c — State allocation, initialization, reset, rendering.
 *
 * FcState is caller-allocated (stack or heap). These functions
 * initialize and reset it. memset to zero is the canonical reset
 * mechanism — all fields must have safe zero defaults.
 */

/* ======================================================================== */
/* Arena collision map (from Void 634 cache, region 37,79, level 0)          */
/* ======================================================================== */

/*
 * Binary collision extracted via DumpFcCollision.kt from the Void 634 cache.
 * Format: 64*64 bytes, row-major (y=0..63, x=0..63), 1=walkable, 0=blocked.
 * 2153 walkable tiles, 1943 blocked. The irregular cave shape with walls,
 * pillars, and narrow passages is critical for safespot mechanics.
 *
 * Loaded from assets/fightcaves.collision at runtime.
 * If the file is missing, falls back to an open arena with border walls.
 */
static void setup_arena(FcState* state) {
    /* Try to load collision from binary file (search multiple paths) */
    static const char* collision_paths[] = {
        "assets/fightcaves.collision",
        "../demo-env/assets/fightcaves.collision",
        "../training-env/assets/fightcaves.collision",
        NULL
    };
    FILE* f = NULL;
    const char* env_path = getenv("FC_COLLISION_PATH");
    if (env_path) f = fopen(env_path, "rb");
    for (int p = 0; !f && collision_paths[p]; p++) {
        f = fopen(collision_paths[p], "rb");
    }
    if (f) {
        uint8_t buf[FC_ARENA_WIDTH * FC_ARENA_HEIGHT];
        size_t n = fread(buf, 1, sizeof(buf), f);
        fclose(f);
        if (n == sizeof(buf)) {
            /* Binary is row-major [y][x], our array is [x][y] — transpose */
            for (int y = 0; y < FC_ARENA_HEIGHT; y++) {
                for (int x = 0; x < FC_ARENA_WIDTH; x++) {
                    state->walkable[x][y] = buf[y * FC_ARENA_WIDTH + x];
                }
            }
            return;
        }
    }

    /* Fallback: open arena with border walls */
    memset(state->walkable, 1, sizeof(state->walkable));
    for (int x = 0; x < FC_ARENA_WIDTH; x++) {
        state->walkable[x][0] = 0;
        state->walkable[x][FC_ARENA_HEIGHT - 1] = 0;
    }
    for (int y = 0; y < FC_ARENA_HEIGHT; y++) {
        state->walkable[0][y] = 0;
        state->walkable[FC_ARENA_WIDTH - 1][y] = 0;
    }
}

/* ======================================================================== */
/* Player initialization                                                     */
/* ======================================================================== */

static void init_player(FcPlayer* p) {
    /* Position: center of arena */
    p->x = FC_ARENA_WIDTH / 2;
    p->y = FC_ARENA_HEIGHT / 2;

    /* Vitals */
    p->current_hp = FC_PLAYER_MAX_HP;
    p->max_hp = FC_PLAYER_MAX_HP;
    p->current_prayer = FC_PLAYER_MAX_PRAYER;
    p->max_prayer = FC_PLAYER_MAX_PRAYER;

    /* Prayer off at start */
    p->prayer = PRAYER_NONE;

    /* Consumables */
    p->sharks_remaining = FC_MAX_SHARKS;
    p->prayer_doses_remaining = FC_MAX_PRAYER_DOSES;

    /* Timers: 0 = ready */
    p->attack_timer = 0;
    p->food_timer = 0;
    p->potion_timer = 0;
    p->combo_timer = 0;

    /* Run */
    p->run_energy = 10000;  /* 100% */
    p->is_running = 1;  /* FC players typically run */

    /* Stats (from FightCaveEpisodeInitializer.kt) */
    p->attack_level = FC_PLAYER_ATTACK_LVL;
    p->strength_level = FC_PLAYER_STRENGTH_LVL;
    p->defence_level = FC_PLAYER_DEFENCE_LVL;
    p->ranged_level = FC_PLAYER_RANGED_LVL;
    p->prayer_level = FC_PLAYER_PRAYER_LVL;
    p->magic_level = FC_PLAYER_MAGIC_LVL;

    /* Equipment bonuses — exact values from Void 634 cache item definitions:
     * Coif + Rune Crossbow + Black D'hide Body/Chaps/Vambraces + Snakeskin Boots + Adamant Bolts */
    p->ranged_attack_bonus = 153;   /* 2+90+30+17+11+3 */
    p->ranged_strength_bonus = 100; /* adamant bolts (param 643) */
    p->defence_stab = 97;           /* 4+0+55+31+6+1 */
    p->defence_slash = 84;          /* 6+0+47+25+5+1 */
    p->defence_crush = 110;         /* 8+0+60+33+7+2 */
    p->defence_magic = 91;          /* 4+0+50+28+8+1 */
    p->defence_ranged = 90;         /* 4+0+55+31+0+0 */
    p->prayer_bonus = 0;            /* no prayer bonus from this loadout */

    /* Ammo */
    p->ammo_count = 1000;

    /* HP regen counter */
    p->hp_regen_counter = 0;

    /* Route (click-to-move) — empty */
    p->route_len = 0;
    p->route_idx = 0;

    /* Attack target — none */
    p->attack_target_idx = -1;
    p->approach_target = 0;
}

/* ======================================================================== */
/* Lifecycle                                                                 */
/* ======================================================================== */

void fc_init(FcState* state) {
    memset(state, 0, sizeof(FcState));
}

void fc_reset(FcState* state, uint32_t seed) {
    /* Zero everything first — ensures no stale state, padding is clean */
    memset(state, 0, sizeof(FcState));

    /* Seed RNG */
    fc_rng_seed(state, seed);

    /* Select random rotation */
    state->rotation_id = fc_rng_int(state, FC_NUM_ROTATIONS);

    /* Setup arena */
    setup_arena(state);

    /* Initialize player */
    init_player(&state->player);

    /* Spawn wave 1 NPCs */
    state->current_wave = 1;
    state->next_spawn_index = 0;
    fc_wave_spawn(state, 1);
}

void fc_step(FcState* state, const int actions[FC_NUM_ACTION_HEADS]) {
    if (state->terminal != TERMINAL_NONE) return;  /* episode over */
    fc_tick(state, actions);
}

void fc_destroy(FcState* state) {
    /* Currently no heap allocations. Zero for safety. */
    memset(state, 0, sizeof(FcState));
}

/* ======================================================================== */
/* Observation / Mask / Reward                                               */
/* ======================================================================== */

/*
 * NPC slot selection: sort active NPCs by (distance, spawn_index),
 * take first FC_VISIBLE_NPCS.
 */
static int npc_distance(const FcPlayer* p, const FcNpc* n) {
    int dx = (p->x > n->x) ? (p->x - n->x) : (n->x - p->x);
    int dy = (p->y > n->y) ? (p->y - n->y) : (n->y - p->y);
    return (dx > dy) ? dx : dy;  /* Chebyshev distance */
}

/* Sort helper: indices of active NPCs, sorted by (distance, spawn_index) */
static int compare_npc_slots(const void* a, const void* b, const FcState* state) {
    int ia = *(const int*)a;
    int ib = *(const int*)b;
    int da = npc_distance(&state->player, &state->npcs[ia]);
    int db = npc_distance(&state->player, &state->npcs[ib]);
    if (da != db) return da - db;
    return state->npcs[ia].spawn_index - state->npcs[ib].spawn_index;
}

/* Simple insertion sort for small arrays (max 16 elements) */
static void sort_npc_indices(int* indices, int count, const FcState* state) {
    for (int i = 1; i < count; i++) {
        int key = indices[i];
        int j = i - 1;
        while (j >= 0 && compare_npc_slots(&key, &indices[j], state) < 0) {
            indices[j + 1] = indices[j];
            j--;
        }
        indices[j + 1] = key;
    }
}

void fc_write_obs(const FcState* state, float* out) {
    memset(out, 0, sizeof(float) * FC_TOTAL_OBS);

    const FcPlayer* p = &state->player;

    /* Player features */
    float* player = out + FC_OBS_PLAYER_START;
    player[FC_OBS_PLAYER_HP]        = (p->max_hp > 0) ? (float)p->current_hp / (float)p->max_hp : 0.0f;
    player[FC_OBS_PLAYER_PRAYER]    = (p->max_prayer > 0) ? (float)p->current_prayer / (float)p->max_prayer : 0.0f;
    player[FC_OBS_PLAYER_X]         = (float)p->x / (float)FC_ARENA_WIDTH;
    player[FC_OBS_PLAYER_Y]         = (float)p->y / (float)FC_ARENA_HEIGHT;
    player[FC_OBS_PLAYER_ATK_TIMER] = (float)p->attack_timer / 5.0f;  /* typical max attack speed */
    player[FC_OBS_PLAYER_FOOD_TMR]  = (float)p->food_timer / (float)FC_FOOD_COOLDOWN_TICKS;
    player[FC_OBS_PLAYER_POT_TMR]   = (float)p->potion_timer / (float)FC_POTION_COOLDOWN_TICKS;
    player[FC_OBS_PLAYER_COMBO_TMR] = (float)p->combo_timer / (float)FC_COMBO_EAT_TICKS;
    player[FC_OBS_PLAYER_RUN_NRG]   = (float)p->run_energy / 10000.0f;
    player[FC_OBS_PLAYER_IS_RUN]    = (float)p->is_running;
    player[FC_OBS_PLAYER_PRAY_MEL]  = (p->prayer == PRAYER_PROTECT_MELEE) ? 1.0f : 0.0f;
    player[FC_OBS_PLAYER_PRAY_RNG]  = (p->prayer == PRAYER_PROTECT_RANGE) ? 1.0f : 0.0f;
    player[FC_OBS_PLAYER_PRAY_MAG]  = (p->prayer == PRAYER_PROTECT_MAGIC) ? 1.0f : 0.0f;
    player[FC_OBS_PLAYER_SHARKS]    = (float)p->sharks_remaining / (float)FC_MAX_SHARKS;
    player[FC_OBS_PLAYER_DOSES]     = (float)p->prayer_doses_remaining / (float)FC_MAX_PRAYER_DOSES;
    player[FC_OBS_PLAYER_AMMO]      = (float)p->ammo_count / 1000.0f;
    player[FC_OBS_PLAYER_DEF_LVL]   = (float)p->defence_level / 99.0f;
    player[FC_OBS_PLAYER_RNG_LVL]   = (float)p->ranged_level / 99.0f;
    player[FC_OBS_PLAYER_DMG_TICK]  = (p->max_hp > 0) ? (float)p->damage_taken_this_tick / (float)p->max_hp : 0.0f;
    player[FC_OBS_PLAYER_STUNNED]   = 0.0f;  /* reserved */

    /* NPC slot selection: gather active NPCs, sort, take first 8 */
    int active_indices[FC_MAX_NPCS];
    int active_count = 0;
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        if (state->npcs[i].active && !state->npcs[i].is_dead) {
            active_indices[active_count++] = i;
        }
    }
    sort_npc_indices(active_indices, active_count, state);

    int visible = (active_count < FC_VISIBLE_NPCS) ? active_count : FC_VISIBLE_NPCS;
    for (int slot = 0; slot < visible; slot++) {
        const FcNpc* n = &state->npcs[active_indices[slot]];
        float* npc_out = out + FC_OBS_NPC_START + slot * FC_OBS_NPC_STRIDE;

        npc_out[FC_NPC_VALID]         = 1.0f;
        npc_out[FC_NPC_TYPE]          = (float)n->npc_type / (float)NPC_TYPE_COUNT;
        npc_out[FC_NPC_X]             = (float)n->x / (float)FC_ARENA_WIDTH;
        npc_out[FC_NPC_Y]             = (float)n->y / (float)FC_ARENA_HEIGHT;
        npc_out[FC_NPC_HP]            = (n->max_hp > 0) ? (float)n->current_hp / (float)n->max_hp : 0.0f;
        npc_out[FC_NPC_DISTANCE]      = (float)npc_distance(p, n) / (float)FC_ARENA_WIDTH;
        npc_out[FC_NPC_ATK_STYLE]     = (float)n->attack_style / 3.0f;
        npc_out[FC_NPC_ATK_TIMER]     = (n->attack_speed > 0) ? (float)n->attack_timer / (float)n->attack_speed : 0.0f;
        npc_out[FC_NPC_SIZE]          = (float)n->size / 5.0f;  /* max NPC size is 5 (Ket-Zek, Jad) */
        npc_out[FC_NPC_IS_HEALER]     = (float)n->is_healer;
        npc_out[FC_NPC_JAD_TELEGRAPH] = (float)n->jad_telegraph / 2.0f;
        npc_out[FC_NPC_AGGRO]         = (n->aggro_target >= 0) ? 1.0f : 0.0f;
    }
    /* Remaining NPC slots already zeroed by memset */

    /* Wave/meta features */
    float* meta = out + FC_OBS_META_START;
    meta[FC_OBS_META_WAVE]       = (float)state->current_wave / (float)FC_NUM_WAVES;
    meta[FC_OBS_META_ROTATION]   = (float)state->rotation_id / (float)FC_NUM_ROTATIONS;
    meta[FC_OBS_META_REMAINING]  = (float)state->npcs_remaining / (float)FC_MAX_NPCS;
    meta[FC_OBS_META_TICK]       = (float)state->tick / (float)FC_MAX_EPISODE_TICKS;
    meta[FC_OBS_META_TOT_DMG_D]  = (float)state->player.total_damage_dealt / 10000.0f;
    meta[FC_OBS_META_TOT_DMG_T]  = (p->max_hp > 0) ? (float)state->player.total_damage_taken / (float)p->max_hp : 0.0f;
    meta[FC_OBS_META_DMG_D_TICK] = (float)state->damage_dealt_this_tick / 1000.0f;
    meta[FC_OBS_META_DMG_T_TICK] = (p->max_hp > 0) ? (float)state->damage_taken_this_tick / (float)p->max_hp : 0.0f;
    meta[FC_OBS_META_KILLS_TICK] = (float)state->npcs_killed_this_tick / (float)FC_MAX_NPCS;
    meta[FC_OBS_META_WAVE_CLR]   = (float)state->wave_just_cleared;

    /* Reward features (at offset FC_REWARD_START) — written by fc_write_reward_features */
    fc_write_reward_features(state, out + FC_REWARD_START);
}

void fc_write_reward_features(const FcState* state, float* out) {
    memset(out, 0, sizeof(float) * FC_REWARD_FEATURES);

    out[FC_RWD_DAMAGE_DEALT]     = (float)state->damage_dealt_this_tick / 1000.0f;
    out[FC_RWD_DAMAGE_TAKEN]     = (state->player.max_hp > 0) ?
                                   (float)state->damage_taken_this_tick / (float)state->player.max_hp : 0.0f;
    out[FC_RWD_NPC_KILL]         = (float)state->npcs_killed_this_tick;
    out[FC_RWD_WAVE_CLEAR]       = (float)state->wave_just_cleared;
    out[FC_RWD_JAD_DAMAGE]       = (float)state->jad_damage_this_tick / 1000.0f;
    out[FC_RWD_JAD_KILL]         = (float)state->jad_killed;
    out[FC_RWD_PLAYER_DEATH]     = (state->terminal == TERMINAL_PLAYER_DEATH) ? 1.0f : 0.0f;
    out[FC_RWD_CAVE_COMPLETE]    = (state->terminal == TERMINAL_CAVE_COMPLETE) ? 1.0f : 0.0f;
    out[FC_RWD_FOOD_USED]        = (float)state->food_used_this_tick;
    out[FC_RWD_PRAYER_POT_USED]  = (float)state->prayer_potion_used_this_tick;
    out[FC_RWD_CORRECT_JAD_PRAY] = (float)state->correct_jad_prayer;
    out[FC_RWD_WRONG_JAD_PRAY]   = (float)state->wrong_jad_prayer;
    out[FC_RWD_INVALID_ACTION]   = (float)state->invalid_action_this_tick;
    out[FC_RWD_MOVEMENT]         = (float)state->movement_this_tick;
    out[FC_RWD_IDLE]             = (float)state->idle_this_tick;
    out[FC_RWD_TICK_PENALTY]     = 1.0f;  /* always fires */
}

void fc_write_mask(const FcState* state, float* out) {
    /* Set all to valid, then mask invalid */
    for (int i = 0; i < FC_ACTION_MASK_SIZE; i++) {
        out[i] = 1.0f;
    }

    const FcPlayer* p = &state->player;

    /* MOVE: idle always valid. Walk/run directions masked if destination not walkable */
    for (int m = 1; m < FC_MOVE_DIM; m++) {
        int dest_x = p->x + FC_MOVE_DX[m];
        int dest_y = p->y + FC_MOVE_DY[m];
        /* For run (m >= 9), check both intermediate and final tile */
        if (dest_x < 0 || dest_x >= FC_ARENA_WIDTH ||
            dest_y < 0 || dest_y >= FC_ARENA_HEIGHT ||
            !state->walkable[dest_x][dest_y]) {
            out[FC_MASK_MOVE_START + m] = 0.0f;
        }
        /* Run requires energy */
        if (m >= 9 && p->run_energy <= 0) {
            out[FC_MASK_MOVE_START + m] = 0.0f;
        }
    }

    /* ATTACK: slot 0 (none) always valid. Slots 1-8 masked if no NPC in that slot */
    /* NPC slot availability determined by same ordering as observation */
    int active_indices[FC_MAX_NPCS];
    int active_count = 0;
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        if (state->npcs[i].active && !state->npcs[i].is_dead) {
            active_indices[active_count++] = i;
        }
    }
    sort_npc_indices(active_indices, active_count, state);
    int visible = (active_count < FC_VISIBLE_NPCS) ? active_count : FC_VISIBLE_NPCS;
    for (int slot = visible; slot < FC_VISIBLE_NPCS; slot++) {
        out[FC_MASK_ATTACK_START + 1 + slot] = 0.0f;  /* no NPC in this slot */
    }

    /* PRAYER: always valid (toggling off when already off is a no-op) */

    /* EAT */
    if (p->sharks_remaining <= 0 || p->food_timer > 0 || p->current_hp >= p->max_hp) {
        out[FC_MASK_EAT_START + FC_EAT_SHARK] = 0.0f;
    }
    /* Combo eat: also need food timer check (separate timer path in PR 2) */
    if (p->sharks_remaining <= 0 || p->combo_timer > 0 || p->current_hp >= p->max_hp) {
        out[FC_MASK_EAT_START + FC_EAT_COMBO] = 0.0f;
    }

    /* DRINK */
    if (p->prayer_doses_remaining <= 0 || p->potion_timer > 0 ||
        p->current_prayer >= p->max_prayer) {
        out[FC_MASK_DRINK_START + FC_DRINK_PRAYER_POT] = 0.0f;
    }
}

int fc_is_terminal(const FcState* state) {
    return state->terminal != TERMINAL_NONE;
}

int fc_terminal_code(const FcState* state) {
    return state->terminal;
}

/* ======================================================================== */
/* Render entities                                                           */
/* ======================================================================== */

void fc_fill_render_entities(const FcState* state, FcRenderEntity* entities, int* count) {
    int idx = 0;

    /* Entity 0: player */
    FcRenderEntity* pe = &entities[idx++];
    memset(pe, 0, sizeof(FcRenderEntity));
    pe->entity_type = ENTITY_PLAYER;
    pe->x = state->player.x;
    pe->y = state->player.y;
    pe->size = 1;
    pe->current_hp = state->player.current_hp;
    pe->max_hp = state->player.max_hp;
    pe->prayer = state->player.prayer;
    pe->damage_taken_this_tick = state->player.damage_taken_this_tick;
    pe->hit_landed_this_tick = state->player.hit_landed_this_tick;

    /* Active NPCs + NPCs that just died this tick (for death hitsplat visibility) */
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        const FcNpc* n = &state->npcs[i];
        if (!n->active && !n->died_this_tick) continue;

        FcRenderEntity* ne = &entities[idx++];
        memset(ne, 0, sizeof(FcRenderEntity));
        ne->entity_type = ENTITY_NPC;
        ne->npc_type = n->npc_type;
        ne->x = n->x;
        ne->y = n->y;
        ne->size = n->size;
        ne->current_hp = n->current_hp;
        ne->max_hp = n->max_hp;
        ne->attack_style = n->attack_style;
        ne->jad_telegraph = n->jad_telegraph;
        ne->is_dead = n->is_dead;
        ne->aggro_target = n->aggro_target;
        ne->damage_taken_this_tick = n->damage_taken_this_tick;
        ne->died_this_tick = n->died_this_tick;
        ne->npc_slot = i;
    }

    *count = idx;
}
