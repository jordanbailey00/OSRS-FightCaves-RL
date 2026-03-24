#include "fc_api.h"
#include "fc_combat.h"
#include "fc_prayer.h"
#include "fc_pathfinding.h"
#include "fc_npc.h"
#include "fc_contracts.h"
#include "fc_debug.h"

/*
 * fc_tick.c — Main tick loop for Fight Caves simulation.
 *
 * Processing order (adapted from PufferLib PvP two-phase execution):
 *
 *   1. Clear per-tick event flags
 *   2. Process player actions:
 *      a. Prayer toggle (instant)
 *      b. Eat food / drink potion (if timer ready)
 *      c. Movement (walk/run via directional head)
 *      d. Attack initiation (if target valid and timer ready)
 *   3. Decrement player timers (attack, food, potion, combo)
 *   4. Prayer drain
 *   5. NPC AI tick (movement + attack) for all active NPCs
 *   6. Resolve pending hits (NPC → player, player → NPC)
 *   7. Check terminal conditions
 *   8. Increment tick
 */

/* ======================================================================== */
/* Clear per-tick flags                                                      */
/* ======================================================================== */

static void clear_per_tick_flags(FcState* state) {
    state->damage_dealt_this_tick = 0;
    state->damage_taken_this_tick = 0;
    state->npcs_killed_this_tick = 0;
    state->wave_just_cleared = 0;
    state->jad_damage_this_tick = 0;
    state->jad_killed = 0;
    state->correct_jad_prayer = 0;
    state->wrong_jad_prayer = 0;
    state->invalid_action_this_tick = 0;
    state->movement_this_tick = 0;
    state->idle_this_tick = 0;
    state->food_used_this_tick = 0;
    state->prayer_potion_used_this_tick = 0;

    FcPlayer* p = &state->player;
    p->damage_taken_this_tick = 0;
    p->hit_landed_this_tick = 0;
    p->food_eaten_this_tick = 0;
    p->potion_used_this_tick = 0;
    p->prayer_changed_this_tick = 0;

    for (int i = 0; i < FC_MAX_NPCS; i++) {
        state->npcs[i].damage_taken_this_tick = 0;
        state->npcs[i].died_this_tick = 0;
    }
}

/* ======================================================================== */
/* Resolve NPC visible-slot index to NPC array index                         */
/* ======================================================================== */

/* Same ordering as observation writer — must be identical for consistency */
static int npc_slot_to_index(const FcState* state, int slot) {
    int active_indices[FC_MAX_NPCS];
    int active_count = 0;
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        if (state->npcs[i].active && !state->npcs[i].is_dead) {
            active_indices[active_count++] = i;
        }
    }

    /* Sort by (distance, spawn_index) — same as obs writer */
    for (int i = 1; i < active_count; i++) {
        int key = active_indices[i];
        int dk = fc_distance_to_npc(state->player.x, state->player.y, &state->npcs[key]);
        int j = i - 1;
        while (j >= 0) {
            int dj = fc_distance_to_npc(state->player.x, state->player.y,
                                        &state->npcs[active_indices[j]]);
            if (dj < dk || (dj == dk &&
                state->npcs[active_indices[j]].spawn_index < state->npcs[key].spawn_index)) {
                break;
            }
            active_indices[j + 1] = active_indices[j];
            j--;
        }
        active_indices[j + 1] = key;
    }

    if (slot < 0 || slot >= active_count || slot >= FC_VISIBLE_NPCS) return -1;
    return active_indices[slot];
}

/* ======================================================================== */
/* Process player actions                                                    */
/* ======================================================================== */

static void process_player_actions(FcState* state, const int actions[FC_NUM_ACTION_HEADS]) {
    FcPlayer* p = &state->player;

    int act_move   = actions[0];
    int act_attack = actions[1];
    int act_prayer = actions[2];
    int act_eat    = actions[3];
    int act_drink  = actions[4];

    /* ---- Prayer (instant, processed first) ---- */
    if (act_prayer > 0) {
        int old_prayer = p->prayer;
        fc_prayer_apply_action(p, act_prayer);
        if (p->prayer != old_prayer) {
            p->prayer_changed_this_tick = 1;
        }
    }

    /* ---- Eat food ---- */
    if (act_eat == FC_EAT_SHARK && p->food_timer <= 0 &&
        p->sharks_remaining > 0 && p->current_hp < p->max_hp) {
        int heal = 200;  /* shark heals 20 HP = 200 tenths */
        p->current_hp += heal;
        if (p->current_hp > p->max_hp) p->current_hp = p->max_hp;
        p->sharks_remaining--;
        p->food_timer = FC_FOOD_COOLDOWN_TICKS;
        p->food_eaten_this_tick = 1;
        state->food_used_this_tick = 1;
    }
    /* Combo eat: simplified — treat as another shark for now */
    if (act_eat == FC_EAT_COMBO && p->combo_timer <= 0 &&
        p->sharks_remaining > 0 && p->current_hp < p->max_hp) {
        int heal = 180;  /* karambwan heals 18 HP = 180 tenths */
        p->current_hp += heal;
        if (p->current_hp > p->max_hp) p->current_hp = p->max_hp;
        p->sharks_remaining--;
        p->combo_timer = FC_COMBO_EAT_TICKS;
        p->food_eaten_this_tick = 1;
        state->food_used_this_tick = 1;
    }

    /* ---- Drink prayer potion ---- */
    if (act_drink == FC_DRINK_PRAYER_POT && p->potion_timer <= 0 &&
        p->prayer_doses_remaining > 0 && p->current_prayer < p->max_prayer) {
        int restore = fc_prayer_potion_restore(FC_PLAYER_PRAYER_LVL);
        p->current_prayer += restore;
        if (p->current_prayer > p->max_prayer) p->current_prayer = p->max_prayer;
        p->prayer_doses_remaining--;
        p->potion_timer = FC_POTION_COOLDOWN_TICKS;
        p->potion_used_this_tick = 1;
        state->prayer_potion_used_this_tick = 1;
    }

    /* ---- Movement ---- */
    if (act_move == FC_MOVE_IDLE) {
        state->idle_this_tick = 1;
    } else if (act_move >= FC_MOVE_WALK_N && act_move <= FC_MOVE_RUN_NW) {
        int dx = FC_MOVE_DX[act_move];
        int dy = FC_MOVE_DY[act_move];
        int max_steps = (act_move >= FC_MOVE_RUN_N) ? 2 : 1;
        int moved = fc_move_toward(&p->x, &p->y, dx, dy, max_steps, state->walkable);
        if (moved > 0) {
            state->movement_this_tick = 1;
            p->is_running = (moved >= 2) ? 1 : 0;
        }
    }

    /* ---- Attack ---- */
    if (act_attack > FC_ATTACK_NONE && p->attack_timer <= 0 && p->ammo_count > 0) {
        int slot = act_attack - 1;  /* 1-indexed → 0-indexed */
        int npc_idx = npc_slot_to_index(state, slot);
        if (npc_idx >= 0) {
            FcNpc* target = &state->npcs[npc_idx];
            int dist = fc_distance_to_npc(p->x, p->y, target);

            /* Ranged crossbow: range of ~7 tiles */
            int weapon_range = 7;
            if (dist <= weapon_range) {
                int att_roll = fc_player_ranged_attack_roll(p);
                int def_roll = 1;  /* NPCs have minimal ranged defence for now */
                float chance = fc_hit_chance(att_roll, def_roll);

                int hit = (fc_rng_float(state) < chance) ? 1 : 0;
                int max_hit = fc_player_ranged_max_hit(p);
                int damage = hit ? fc_rng_int(state, max_hit + 1) : 0;

                int delay = fc_ranged_hit_delay(dist);
                fc_queue_pending_hit(target->pending_hits, &target->num_pending_hits,
                                     FC_MAX_PENDING_HITS,
                                     damage, delay, ATTACK_RANGED, -1, 0);

                p->attack_timer = 5;  /* crossbow attack speed */
                p->ammo_count--;
            }
        }
    }
}

/* ======================================================================== */
/* Decrement player timers                                                   */
/* ======================================================================== */

static void decrement_player_timers(FcPlayer* p) {
    if (p->attack_timer > 0) p->attack_timer--;
    if (p->food_timer > 0) p->food_timer--;
    if (p->potion_timer > 0) p->potion_timer--;
    if (p->combo_timer > 0) p->combo_timer--;
}

/* ======================================================================== */
/* Check terminal conditions                                                 */
/* ======================================================================== */

static void check_terminal(FcState* state) {
    if (state->terminal != TERMINAL_NONE) return;

    /* Player death */
    if (state->player.current_hp <= 0) {
        state->terminal = TERMINAL_PLAYER_DEATH;
        return;
    }

    /* Wave clear: all NPCs dead and current_wave was active */
    if (state->npcs_remaining <= 0 && state->current_wave > 0) {
        state->wave_just_cleared = 1;

        if (state->current_wave >= FC_NUM_WAVES) {
            /* All 63 waves + Jad cleared */
            state->terminal = TERMINAL_CAVE_COMPLETE;
        }
        /* Otherwise PR 6 handles wave transition */
    }

    /* Tick cap */
    if (state->tick >= FC_MAX_EPISODE_TICKS) {
        state->terminal = TERMINAL_TICK_CAP;
    }
}

/* ======================================================================== */
/* Main tick entry point                                                     */
/* ======================================================================== */

void fc_tick(FcState* state, const int actions[FC_NUM_ACTION_HEADS]) {
    /* 1. Clear per-tick flags */
    clear_per_tick_flags(state);

    /* 2. Process player actions */
    process_player_actions(state, actions);

    /* 3. Decrement player timers */
    decrement_player_timers(&state->player);

    /* 4. Prayer drain */
    fc_prayer_drain_tick(&state->player);

    /* 5. NPC AI tick */
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        fc_npc_tick(state, i);
    }

    /* 6. Resolve pending hits */
    fc_resolve_player_pending_hits(state);
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        if (state->npcs[i].active) {
            fc_resolve_npc_pending_hits(state, i);
        }
    }

    /* 7. Check terminal */
    check_terminal(state);

    /* 8. Increment tick */
    state->tick++;
}
