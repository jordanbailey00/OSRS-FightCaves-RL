#include "fc_api.h"
#include "fc_combat.h"
#include "fc_prayer.h"
#include "fc_pathfinding.h"
#include <math.h>
#include <stdio.h>
#include "fc_npc.h"
#include "fc_wave.h"
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
    /* Two modes:
     * 1. Route-based (click-to-move from viewer): consume one step from route deque
     * 2. Directional (RL action head): immediate step in direction
     * Route takes priority. If route active, directional input is ignored. */
    if (p->route_idx < p->route_len) {
        /* Consume steps from the route: 1 if walking, 2 if running */
        int steps = p->is_running ? 2 : 1;
        for (int s = 0; s < steps && p->route_idx < p->route_len; s++) {
            int nx = p->route_x[p->route_idx];
            int ny = p->route_y[p->route_idx];
            if (fc_tile_walkable(nx, ny, state->walkable)) {
                /* Update facing based on movement direction */
                int dx = nx - p->x;
                int dy = ny - p->y;
                if (dx != 0 || dy != 0) {
                    /* atan2 of world X delta and negated world Y delta (for Raylib Z) */
                    p->facing_angle = atan2f((float)dx, (float)(-dy)) * (180.0f / 3.14159f);
                }
                p->x = nx;
                p->y = ny;
                state->movement_this_tick = 1;
            }
            p->route_idx++;
        }
    } else if (act_move == FC_MOVE_IDLE) {
        state->idle_this_tick = 1;
    } else if (act_move >= FC_MOVE_WALK_N && act_move <= FC_MOVE_RUN_NW) {
        /* Directional movement (for RL agents) */
        int dx = FC_MOVE_DX[act_move];
        int dy = FC_MOVE_DY[act_move];
        int max_steps = (act_move >= FC_MOVE_RUN_N) ? 2 : 1;
        int moved = fc_move_toward(&p->x, &p->y, dx, dy, max_steps, state->walkable);
        if (moved > 0) {
            state->movement_this_tick = 1;
            p->is_running = (moved >= 2) ? 1 : 0;
        }
    }

    /* ---- Attack target selection ---- */
    if (act_attack > FC_ATTACK_NONE) {
        int slot = act_attack - 1;
        int npc_idx = npc_slot_to_index(state, slot);
        if (npc_idx >= 0) p->attack_target_idx = npc_idx;
    }

    /* ---- Auto-attack current target ---- */
    /* Like Void CombatMovement: if target set, walk toward it until in range,
     * then attack on cooldown. Player stays still once in range. */
    if (p->attack_target_idx >= 0 && p->ammo_count > 0) {
        FcNpc* target = &state->npcs[p->attack_target_idx];
        if (!target->active || target->is_dead) {
            p->attack_target_idx = -1;  /* target died, clear */
            p->approach_target = 0;
        } else {
            int dist = fc_distance_to_npc(p->x, p->y, target);
            int weapon_range = 7;  /* rune crossbow */

            if (dist > weapon_range && p->approach_target && p->route_idx >= p->route_len) {
                /* Player explicitly clicked NPC — pathfind to a tile within weapon range.
                 * Find the closest walkable tile that's within range of the NPC footprint. */
                int best_x = -1, best_y = -1, best_d = 9999;
                int cx = target->x + target->size / 2;
                int cy = target->y + target->size / 2;
                /* Search tiles around the NPC within weapon_range */
                for (int dy = -weapon_range - 1; dy <= weapon_range + target->size; dy++) {
                    for (int dx = -weapon_range - 1; dx <= weapon_range + target->size; dx++) {
                        int tx = target->x + dx, ty = target->y + dy;
                        if (tx < 0 || tx >= FC_ARENA_WIDTH || ty < 0 || ty >= FC_ARENA_HEIGHT) continue;
                        if (!state->walkable[tx][ty]) continue;
                        /* Check this tile is within weapon range of NPC footprint */
                        int nx = (tx < target->x) ? target->x : (tx > target->x + target->size - 1) ? target->x + target->size - 1 : tx;
                        int ny = (ty < target->y) ? target->y : (ty > target->y + target->size - 1) ? target->y + target->size - 1 : ty;
                        int ndx = (tx > nx) ? tx - nx : nx - tx;
                        int ndy = (ty > ny) ? ty - ny : ny - ty;
                        int ndist = (ndx > ndy) ? ndx : ndy;
                        if (ndist > weapon_range) continue;
                        /* Pick tile closest to player */
                        int pdx = (p->x > tx) ? p->x - tx : tx - p->x;
                        int pdy = (p->y > ty) ? p->y - ty : ty - p->y;
                        int pdist = (pdx > pdy) ? pdx : pdy;
                        if (pdist < best_d) { best_d = pdist; best_x = tx; best_y = ty; }
                    }
                }
                if (best_x >= 0) {
                    p->route_len = fc_pathfind_bfs(p->x, p->y, best_x, best_y,
                                                    state->walkable, p->route_x, p->route_y, FC_MAX_ROUTE);
                    p->route_idx = 0;
                    fprintf(stderr, "APPROACH: player(%d,%d)→tile(%d,%d) dist=%d route=%d steps\n",
                            p->x, p->y, best_x, best_y, dist, p->route_len);
                } else {
                    fprintf(stderr, "APPROACH: no valid tile found! player(%d,%d) npc(%d,%d) size=%d dist=%d\n",
                            p->x, p->y, target->x, target->y, target->size, dist);
                }
            }

            /* Face the attack target */
            {
                float tx = (float)target->x + (float)target->size*0.5f;
                float ty = (float)target->y + (float)target->size*0.5f;
                float dx = tx - ((float)p->x + 0.5f);
                float dy = ty - ((float)p->y + 0.5f);
                if (dx != 0 || dy != 0) {
                    p->facing_angle = atan2f(dx, -dy) * (180.0f / 3.14159f);
                }
            }

            if (dist <= weapon_range && p->attack_timer <= 0) {
                /* In range — fire attack */
                int att_roll = fc_player_ranged_attack_roll(p);
                const FcNpcStats* tstats = fc_npc_get_stats(target->npc_type);
                int def_roll = fc_npc_def_roll(tstats->def_level, tstats->def_bonus);
                float chance = fc_hit_chance(att_roll, def_roll);

                int hit = (fc_rng_float(state) < chance) ? 1 : 0;
                int max_hit = fc_player_ranged_max_hit(p);
                int damage = hit ? fc_rng_int(state, max_hit + 1) : 0;

                int delay = fc_ranged_hit_delay(dist);
                fc_queue_pending_hit(target->pending_hits, &target->num_pending_hits,
                                     FC_MAX_PENDING_HITS,
                                     damage, delay, ATTACK_RANGED, -1, 0);

                p->attack_timer = 5;
                p->ammo_count--;
                p->hit_landed_this_tick = 1;  /* flag for viewer hitsplat */
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

/* ======================================================================== */
/* Jad healer auto-spawn                                                     */
/* ======================================================================== */

/*
 * Jad healer spawn (from TzhaarFightCave.kt npcLevelChanged handler):
 *   Trigger: Jad HP drops below 50% of max.
 *   Spawns up to 4 Yt-HurKot (fills missing slots: 4 - currently_alive).
 *   Respawn: Only after Jad is healed back above 50% and drops below again.
 *   The jad_healers_spawned flag tracks whether we're in a "healed" state.
 *   When Jad is healed above 50%, flag resets so next drop below 50% re-triggers.
 */
static void check_jad_healers(FcState* state) {
    if (state->current_wave != FC_NUM_WAVES) return;  /* only on wave 63 */

    /* Find Jad */
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        FcNpc* jad = &state->npcs[i];
        if (jad->npc_type != NPC_TZTOK_JAD || !jad->active || jad->is_dead) continue;

        int half_hp = jad->max_hp / 2;

        /* If Jad is above 50%, reset the spawn flag (allows re-trigger) */
        if (jad->current_hp >= half_hp) {
            state->jad_healers_spawned = 0;
            return;
        }

        /* Below 50% — spawn healers if not already spawned this cycle */
        if (state->jad_healers_spawned) return;

        /* Count currently alive healers */
        int alive_healers = 0;
        for (int h = 0; h < FC_MAX_NPCS; h++) {
            if (state->npcs[h].active && !state->npcs[h].is_dead &&
                state->npcs[h].npc_type == NPC_YT_HURKOT) {
                alive_healers++;
            }
        }

        /* Spawn up to 4 total (fill missing slots) */
        int to_spawn = FC_JAD_NUM_HEALERS - alive_healers;
        int offsets[4][2] = {{-2, 0}, {2, 0}, {0, -2}, {0, 2}};
        int spawned = 0;
        for (int h = 0; h < to_spawn; h++) {
            int hx = jad->x + offsets[(alive_healers + h) % 4][0];
            int hy = jad->y + offsets[(alive_healers + h) % 4][1];
            /* Clamp to arena */
            if (hx < 1) hx = 1;
            if (hy < 1) hy = 1;
            if (hx >= FC_ARENA_WIDTH - 1) hx = FC_ARENA_WIDTH - 2;
            if (hy >= FC_ARENA_HEIGHT - 1) hy = FC_ARENA_HEIGHT - 2;

            for (int slot = 0; slot < FC_MAX_NPCS; slot++) {
                if (!state->npcs[slot].active) {
                    fc_npc_spawn(&state->npcs[slot], NPC_YT_HURKOT, hx, hy,
                                 state->next_spawn_index++);
                    state->npcs_remaining++;
                    spawned++;
                    break;
                }
            }
        }
        state->jad_healers_spawned = 1;
        return;
    }
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

    /* Wave advancement (handles wave-clear and cave-complete) */
    fc_wave_check_advance(state);

    /* Jad healer spawn check */
    check_jad_healers(state);

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

    /* 4b. HP regen (1 HP = 10 tenths every FC_HP_REGEN_INTERVAL ticks) */
    if (state->player.current_hp > 0 && state->player.current_hp < state->player.max_hp) {
        state->player.hp_regen_counter++;
        if (state->player.hp_regen_counter >= FC_HP_REGEN_INTERVAL) {
            state->player.hp_regen_counter = 0;
            state->player.current_hp += 10;  /* 1 HP in tenths */
            if (state->player.current_hp > state->player.max_hp) {
                state->player.current_hp = state->player.max_hp;
            }
        }
    }

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
