#include "fc_npc.h"
#include "fc_combat.h"
#include "fc_pathfinding.h"
#include "fc_api.h"

/*
 * fc_npc.c — NPC framework with stat table and type-specific AI dispatch.
 *
 * PR 5: All 8 NPC types have full AI.
 *
 * NPC AI per tick (generic):
 *   1. If dead or inactive, skip.
 *   2. Decrement attack timer.
 *   3. Type-specific behavior (Jad telegraph, Yt-MejKot heal, Yt-HurKot heal).
 *   4. If not in attack range, move toward player (greedy step).
 *   5. If in range and attack timer ready, roll attack and queue pending hit.
 *
 * Type-specific:
 *   Tz-Kih:     Melee + prayer drain on hit.
 *   Tz-Kek:     Melee. Splits into 2 small Tz-Kek on death.
 *   Tz-Kek-Sm:  Melee. (no special)
 *   Tok-Xil:    Ranged with projectile delay.
 *   Yt-MejKot:  Melee + heals nearby NPCs on timer.
 *   Ket-Zek:    Magic with projectile delay.
 *   TzTok-Jad:  Magic/ranged alternating with 3-tick telegraph wind-up.
 *   Yt-HurKot:  Heals Jad unless distracted by player attack.
 */

/* ======================================================================== */
/* NPC stat table                                                            */
/* ======================================================================== */

/*
 * Stats sourced from OSRS wiki + Kotlin archive.
 * HP in tenths (100 = 10.0 HP). Max hit in tenths.
 * Defence stats used by fc_npc_def_roll for player attack accuracy.
 *
 * Fields: max_hp, attack_style, attack_speed, attack_range, max_hit,
 *         att_level, att_bonus, def_level, def_bonus, size, movement_speed,
 *         prayer_drain, heal_amount, heal_interval, jad_ranged_max_hit
 */
static const FcNpcStats NPC_STATS[NPC_TYPE_COUNT] = {
    /* NPC_NONE */
    { 0, ATTACK_NONE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },

    /* NPC_TZ_KIH: Lv 22 melee bat. Drains 1 prayer point on hit.
     * OSRS: 10 HP, att 22, def 22, max hit 4, attack speed 4, size 1 */
    { 100, ATTACK_MELEE, 4, 1, 40, 22, 0, 22, 0, 1, 1, 10, 0, 0, 0 },

    /* NPC_TZ_KEK: Lv 45 melee blob. Splits into 2 small Tz-Kek on death.
     * OSRS: 20 HP, att 45, def 45, max hit 7, attack speed 4, size 1 */
    { 200, ATTACK_MELEE, 4, 1, 70, 45, 0, 45, 0, 1, 1, 0, 0, 0, 0 },

    /* NPC_TZ_KEK_SM: Lv 22 small blob (from split).
     * OSRS: 10 HP, att 22, def 22, max hit 4 */
    { 100, ATTACK_MELEE, 4, 1, 40, 22, 0, 22, 0, 1, 1, 0, 0, 0, 0 },

    /* NPC_TOK_XIL: Lv 90 ranged ranger.
     * OSRS: 40 HP, att 90, def 60, max hit 13, range 6, attack speed 4 */
    { 400, ATTACK_RANGED, 4, 6, 130, 90, 20, 60, 20, 1, 1, 0, 0, 0, 0 },

    /* NPC_YT_MEJKOT: Lv 180 melee. Heals nearby NPCs for 10 HP every 8 ticks.
     * OSRS: 80 HP, att 180, def 180, max hit 25, size 2 */
    { 800, ATTACK_MELEE, 4, 1, 250, 180, 30, 180, 30, 2, 1, 0, 100, 8, 0 },

    /* NPC_KET_ZEK: Lv 360 magic. Long range, high damage.
     * OSRS: 80 HP, att 360, def 300, max hit 49, range 8, attack speed 4, size 2 */
    { 800, ATTACK_MAGIC, 4, 8, 490, 360, 40, 300, 40, 2, 1, 0, 0, 0, 0 },

    /* NPC_TZTOK_JAD: Lv 702 magic+ranged. Telegraph wind-up.
     * OSRS: 250 HP, att 702, def 480, magic max 970, ranged max 950,
     * range 10, attack speed 8, size 3 */
    { 2500, ATTACK_MAGIC, 8, 10, 970, 702, 100, 480, 100, 3, 1, 0, 0, 0, 950 },

    /* NPC_YT_HURKOT: Jad healer. Heals Jad 10 HP every 4 ticks if not distracted.
     * OSRS: 30 HP, att 22, def 22, max hit 4 */
    { 300, ATTACK_MELEE, 4, 1, 40, 22, 0, 22, 0, 1, 1, 0, 100, 4, 0 },
};

const FcNpcStats* fc_npc_get_stats(int npc_type) {
    if (npc_type < 0 || npc_type >= NPC_TYPE_COUNT) return &NPC_STATS[0];
    return &NPC_STATS[npc_type];
}

/* ======================================================================== */
/* Spawn                                                                     */
/* ======================================================================== */

void fc_npc_spawn(FcNpc* npc, int npc_type, int x, int y, int spawn_index) {
    const FcNpcStats* stats = fc_npc_get_stats(npc_type);

    npc->active = 1;
    npc->npc_type = npc_type;
    npc->spawn_index = spawn_index;
    npc->x = x;
    npc->y = y;
    npc->size = stats->size;
    npc->current_hp = stats->max_hp;
    npc->max_hp = stats->max_hp;
    npc->is_dead = 0;
    npc->attack_style = stats->attack_style;
    npc->attack_timer = stats->attack_speed;  /* first attack after full cooldown */
    npc->attack_speed = stats->attack_speed;
    npc->attack_range = stats->attack_range;
    npc->max_hit = stats->max_hit;
    npc->aggro_target = 0;  /* always target player in Fight Caves */
    npc->movement_speed = stats->movement_speed;
    npc->jad_telegraph = JAD_TELEGRAPH_IDLE;
    npc->jad_next_style = ATTACK_NONE;
    npc->heal_timer = stats->heal_interval;  /* start at full cooldown */
    npc->heal_amount = stats->heal_amount;
    npc->is_healer = (npc_type == NPC_YT_HURKOT) ? 1 : 0;
    npc->healer_distracted = 0;
    npc->heal_target_idx = -1;
    npc->damage_taken_this_tick = 0;
    npc->died_this_tick = 0;
    npc->num_pending_hits = 0;
}

/* ======================================================================== */
/* Tz-Kek: split on death — spawn 2 small Tz-Kek                            */
/* ======================================================================== */

void fc_npc_tz_kek_split(FcState* state, int dead_x, int dead_y) {
    /* Spawn 2 NPC_TZ_KEK_SM at/near the death position */
    int spawned = 0;
    for (int i = 0; i < FC_MAX_NPCS && spawned < 2; i++) {
        if (!state->npcs[i].active) {
            int sx = dead_x + (spawned == 0 ? 0 : 1);
            int sy = dead_y;
            /* Clamp to arena */
            if (sx >= FC_ARENA_WIDTH - 1) sx = dead_x - 1;
            if (sx < 1) sx = 1;

            fc_npc_spawn(&state->npcs[i], NPC_TZ_KEK_SM, sx, sy,
                         state->next_spawn_index++);
            state->npcs_remaining++;
            spawned++;
        }
    }
}

/* ======================================================================== */
/* Jad telegraph state machine                                               */
/* ======================================================================== */

/*
 * Jad attack cycle:
 *   IDLE → (choose style via RNG) → set telegraph to MAGIC_WINDUP or RANGED_WINDUP
 *   → hold telegraph for 3 ticks (attack_speed / 2 approximately)
 *   → fire attack (queue pending hit) → reset attack timer → IDLE
 *
 * The agent must read the telegraph and switch prayer before the hit resolves.
 * Telegraph is visible in observations as FC_NPC_JAD_TELEGRAPH.
 */
static void jad_telegraph_tick(FcState* state, FcNpc* npc, int npc_idx) {
    const FcNpcStats* stats = fc_npc_get_stats(NPC_TZTOK_JAD);
    FcPlayer* p = &state->player;
    int dist = fc_distance_to_npc(p->x, p->y, npc);

    if (npc->jad_telegraph == JAD_TELEGRAPH_IDLE) {
        /* Start new attack cycle when timer is ready */
        if (npc->attack_timer <= 0 && dist <= npc->attack_range) {
            /* Choose attack style via RNG */
            int style = (fc_rng_int(state, 2) == 0) ? ATTACK_MAGIC : ATTACK_RANGED;
            npc->jad_next_style = style;
            npc->jad_telegraph = (style == ATTACK_MAGIC)
                ? JAD_TELEGRAPH_MAGIC_WINDUP
                : JAD_TELEGRAPH_RANGED_WINDUP;
            /* Telegraph holds for 3 ticks before the hit fires */
            npc->attack_timer = 3;
        }
    } else {
        /* Wind-up in progress — count down, then fire */
        if (npc->attack_timer <= 0) {
            /* Fire the attack */
            int att_roll = fc_npc_attack_roll(stats->att_level, stats->att_bonus);
            int def_roll = fc_player_def_roll(p, npc->jad_next_style);
            float chance = fc_hit_chance(att_roll, def_roll);

            int max_hit = (npc->jad_next_style == ATTACK_MAGIC)
                ? stats->max_hit : stats->jad_ranged_max_hit;

            int hit = (fc_rng_float(state) < chance) ? 1 : 0;
            int damage = hit ? fc_rng_int(state, max_hit + 1) : 0;

            int delay = (npc->jad_next_style == ATTACK_MAGIC)
                ? fc_magic_hit_delay(dist) : fc_ranged_hit_delay(dist);

            fc_queue_pending_hit(p->pending_hits, &p->num_pending_hits,
                                 FC_MAX_PENDING_HITS,
                                 damage, delay, npc->jad_next_style, npc_idx, 0);

            /* Reset to idle, full attack cooldown */
            npc->jad_telegraph = JAD_TELEGRAPH_IDLE;
            npc->jad_next_style = ATTACK_NONE;
            npc->attack_timer = npc->attack_speed;
        }
    }
}

/* ======================================================================== */
/* Yt-MejKot: heal nearby NPCs                                              */
/* ======================================================================== */

static void yt_mejkot_heal_tick(FcState* state, FcNpc* npc) {
    if (npc->heal_timer > 0) {
        npc->heal_timer--;
        return;
    }

    const FcNpcStats* stats = fc_npc_get_stats(npc->npc_type);
    npc->heal_timer = stats->heal_interval;

    /* Heal nearby NPCs (within 3 tiles) that are damaged */
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        FcNpc* target = &state->npcs[i];
        if (!target->active || target->is_dead) continue;
        if (target == npc) continue;  /* don't self-heal */
        if (target->current_hp >= target->max_hp) continue;  /* full HP */

        /* Chebyshev distance between NPCs */
        int dx = (npc->x > target->x) ? npc->x - target->x : target->x - npc->x;
        int dy = (npc->y > target->y) ? npc->y - target->y : target->y - npc->y;
        int dist = (dx > dy) ? dx : dy;

        if (dist <= 3) {
            target->current_hp += npc->heal_amount;
            if (target->current_hp > target->max_hp) {
                target->current_hp = target->max_hp;
            }
        }
    }
}

/* ======================================================================== */
/* Yt-HurKot: heal Jad unless distracted                                     */
/* ======================================================================== */

static void yt_hurkot_tick(FcState* state, FcNpc* npc) {
    if (npc->healer_distracted) return;  /* player attacked us, stop healing */

    if (npc->heal_timer > 0) {
        npc->heal_timer--;
        return;
    }

    const FcNpcStats* stats = fc_npc_get_stats(npc->npc_type);
    npc->heal_timer = stats->heal_interval;

    /* Find Jad and heal him */
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        FcNpc* jad = &state->npcs[i];
        if (jad->npc_type == NPC_TZTOK_JAD && jad->active && !jad->is_dead) {
            npc->heal_target_idx = i;

            /* Move toward Jad if not adjacent */
            int dx = (npc->x > jad->x) ? npc->x - jad->x : jad->x - npc->x;
            int dy = (npc->y > jad->y) ? npc->y - jad->y : jad->y - npc->y;
            int dist = (dx > dy) ? dx : dy;

            if (dist <= 1) {
                jad->current_hp += npc->heal_amount;
                if (jad->current_hp > jad->max_hp) {
                    jad->current_hp = jad->max_hp;
                }
            } else {
                /* Walk toward Jad instead of player */
                fc_npc_step_toward(&npc->x, &npc->y, jad->x, jad->y,
                                   state->walkable);
            }
            return;
        }
    }
}

/* ======================================================================== */
/* Generic NPC attack (melee/ranged/magic, non-Jad)                          */
/* ======================================================================== */

static void npc_generic_attack(FcState* state, FcNpc* npc, int npc_idx) {
    const FcNpcStats* stats = fc_npc_get_stats(npc->npc_type);
    FcPlayer* p = &state->player;
    int dist = fc_distance_to_npc(p->x, p->y, npc);

    if (dist <= npc->attack_range && npc->attack_timer <= 0) {
        int att_roll = fc_npc_attack_roll(stats->att_level, stats->att_bonus);
        int def_roll = fc_player_def_roll(p, npc->attack_style);
        float chance = fc_hit_chance(att_roll, def_roll);

        int hit = (fc_rng_float(state) < chance) ? 1 : 0;
        int damage = hit ? fc_rng_int(state, npc->max_hit + 1) : 0;

        int delay;
        switch (npc->attack_style) {
            case ATTACK_MELEE:  delay = fc_melee_hit_delay(); break;
            case ATTACK_RANGED: delay = fc_ranged_hit_delay(dist); break;
            case ATTACK_MAGIC:  delay = fc_magic_hit_delay(dist); break;
            default:            delay = 1; break;
        }

        fc_queue_pending_hit(p->pending_hits, &p->num_pending_hits,
                             FC_MAX_PENDING_HITS,
                             damage, delay, npc->attack_style, npc_idx,
                             stats->prayer_drain);

        npc->attack_timer = npc->attack_speed;
    }
}

/* ======================================================================== */
/* NPC AI tick — type dispatch                                               */
/* ======================================================================== */

void fc_npc_tick(FcState* state, int npc_idx) {
    FcNpc* npc = &state->npcs[npc_idx];
    if (!npc->active || npc->is_dead) return;

    FcPlayer* p = &state->player;

    /* Decrement attack timer */
    if (npc->attack_timer > 0) npc->attack_timer--;

    /* --- Type-specific pre-attack behavior --- */

    /* Yt-HurKot: heal Jad instead of normal AI (unless distracted) */
    if (npc->npc_type == NPC_YT_HURKOT && !npc->healer_distracted) {
        yt_hurkot_tick(state, npc);
        return;  /* healers don't attack when healing Jad */
    }

    /* Yt-MejKot: heal nearby NPCs on timer */
    if (npc->npc_type == NPC_YT_MEJKOT) {
        yt_mejkot_heal_tick(state, npc);
    }

    /* Jad: telegraph state machine handles everything */
    if (npc->npc_type == NPC_TZTOK_JAD) {
        /* Jad movement: if not in range, walk toward player */
        int dist = fc_distance_to_npc(p->x, p->y, npc);
        if (dist > npc->attack_range) {
            for (int step = 0; step < npc->movement_speed; step++) {
                fc_npc_step_toward(&npc->x, &npc->y, p->x, p->y, state->walkable);
            }
        }
        jad_telegraph_tick(state, npc, npc_idx);
        return;
    }

    /* --- Generic movement + attack for all other types --- */

    int dist = fc_distance_to_npc(p->x, p->y, npc);

    /* Movement: if not in attack range, walk toward player */
    if (dist > npc->attack_range) {
        for (int step = 0; step < npc->movement_speed; step++) {
            fc_npc_step_toward(&npc->x, &npc->y, p->x, p->y, state->walkable);
        }
    }

    /* Attack */
    npc_generic_attack(state, npc, npc_idx);
}
