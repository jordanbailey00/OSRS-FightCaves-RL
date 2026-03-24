#include "fc_npc.h"
#include "fc_combat.h"
#include "fc_pathfinding.h"
#include "fc_api.h"

/*
 * fc_npc.c — NPC framework with stat table and AI dispatch.
 *
 * PR 2 implements Tz-Kih only. The framework is generic: add a row to
 * NPC_STATS and extend fc_npc_tick for NPC-specific behavior.
 *
 * NPC AI per tick:
 *   1. If dead or inactive, skip.
 *   2. Decrement attack timer.
 *   3. If not in attack range, move toward player (greedy step).
 *   4. If in range and attack timer ready, roll attack and queue pending hit.
 */

/* ======================================================================== */
/* NPC stat table                                                            */
/* ======================================================================== */

/*
 * Stats sourced from OSRS wiki + Kotlin archive.
 * HP is in tenths (e.g. 100 = 10.0 HP). Max hit is in tenths.
 * PR 5 will fill in the remaining NPC types.
 */
static const FcNpcStats NPC_STATS[NPC_TYPE_COUNT] = {
    /* NPC_NONE */
    { 0, ATTACK_NONE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },

    /* NPC_TZ_KIH: Lv 22 melee bat. Drains 1 prayer point on hit. */
    { 100, ATTACK_MELEE, 4, 1, 40, 22, 0, 22, 0, 1, 1, 10 },
    /* max_hit=4 damage, prayer_drain=10 tenths (1 point) */

    /* NPC_TZ_KEK: Lv 45 melee blob. Splits on death. */
    { 200, ATTACK_MELEE, 4, 1, 70, 45, 0, 45, 0, 1, 1, 0 },

    /* NPC_TZ_KEK_SM: Lv 22 small blob (from split). */
    { 100, ATTACK_MELEE, 4, 1, 40, 22, 0, 22, 0, 1, 1, 0 },

    /* NPC_TOK_XIL: Lv 90 ranged. */
    { 400, ATTACK_RANGED, 4, 6, 130, 90, 20, 0, 0, 1, 1, 0 },

    /* NPC_YT_MEJKOT: Lv 180 melee. Heals nearby NPCs. */
    { 800, ATTACK_MELEE, 4, 1, 250, 180, 30, 180, 30, 2, 1, 0 },

    /* NPC_KET_ZEK: Lv 360 magic. */
    { 800, ATTACK_MAGIC, 4, 8, 490, 360, 40, 0, 0, 2, 1, 0 },

    /* NPC_TZTOK_JAD: Lv 702 magic+ranged. */
    { 2500, ATTACK_MAGIC, 8, 10, 970, 702, 100, 0, 0, 3, 1, 0 },

    /* NPC_YT_HURKOT: Jad healer. */
    { 300, ATTACK_MELEE, 4, 1, 40, 22, 0, 22, 0, 1, 1, 0 },
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
    npc->heal_timer = 0;
    npc->heal_amount = 0;
    npc->is_healer = (npc_type == NPC_YT_HURKOT) ? 1 : 0;
    npc->healer_distracted = 0;
    npc->heal_target_idx = -1;
    npc->damage_taken_this_tick = 0;
    npc->died_this_tick = 0;
    npc->num_pending_hits = 0;
}

/* ======================================================================== */
/* NPC AI tick                                                               */
/* ======================================================================== */

void fc_npc_tick(FcState* state, int npc_idx) {
    FcNpc* npc = &state->npcs[npc_idx];
    if (!npc->active || npc->is_dead) return;

    const FcNpcStats* stats = fc_npc_get_stats(npc->npc_type);
    FcPlayer* p = &state->player;

    /* Decrement attack timer */
    if (npc->attack_timer > 0) npc->attack_timer--;

    /* Distance to player */
    int dist = fc_distance_to_npc(p->x, p->y, npc);

    /* Movement: if not in attack range, walk toward player */
    if (dist > npc->attack_range) {
        for (int step = 0; step < npc->movement_speed; step++) {
            fc_npc_step_toward(&npc->x, &npc->y, p->x, p->y, state->walkable);
        }
        dist = fc_distance_to_npc(p->x, p->y, npc);
    }

    /* Attack: if in range and timer ready */
    if (dist <= npc->attack_range && npc->attack_timer <= 0) {
        /* Roll attack */
        int att_roll = fc_npc_attack_roll(stats->att_level, stats->att_bonus);
        int def_roll = fc_player_def_roll(p, npc->attack_style);
        float chance = fc_hit_chance(att_roll, def_roll);

        int hit = (fc_rng_float(state) < chance) ? 1 : 0;
        int damage = hit ? fc_rng_int(state, npc->max_hit + 1) : 0;

        /* Queue pending hit with appropriate delay */
        int delay;
        if (npc->attack_style == ATTACK_MELEE) {
            delay = fc_melee_hit_delay();
        } else {
            delay = fc_ranged_hit_delay(dist);
        }

        fc_queue_pending_hit(p->pending_hits, &p->num_pending_hits,
                             FC_MAX_PENDING_HITS,
                             damage, delay, npc->attack_style, npc_idx,
                             stats->prayer_drain);

        /* Reset attack timer */
        npc->attack_timer = npc->attack_speed;
    }
}
