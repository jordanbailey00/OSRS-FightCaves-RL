/*
 * fight_caves.h — PufferLib 4.0 environment wrapper for Fight Caves.
 *
 * Wraps the FC backend (fc_*.c) into PufferLib's c_reset/c_step/c_render
 * interface. All game logic lives in the fc_*.c files included below.
 * This file only handles the PufferLib adapter layer:
 *   - FightCaves struct with PufferLib-required fields
 *   - c_reset: init game state, compute initial obs
 *   - c_step: read actions, step game, compute reward+obs, handle terminal
 *   - c_render: launch Raylib viewer (eval mode only)
 *   - c_close: cleanup
 *
 * Single-agent environment (num_agents=1 always for Fight Caves).
 */

#include <stdlib.h>
#include <string.h>
#include <stdio.h>

/* Include all backend sources directly (compiled as one unit) */
#include "fc_types.h"
#include "fc_contracts.h"
#include "fc_api.h"
#include "fc_combat.h"
#include "fc_npc.h"
#include "fc_pathfinding.h"
#include "fc_prayer.h"
#include "fc_wave.h"

/* Backend .c files — included directly so fight_caves.h is self-contained.
 * PufferLib compiles binding.c which includes this header. */
#include "fc_rng.c"
#include "fc_pathfinding.c"
#include "fc_prayer.c"
#include "fc_combat.c"
#include "fc_npc.c"
#include "fc_wave.c"
#include "fc_tick.c"
#include "fc_state.c"

/* Raylib rendering for eval mode (Phase 11).
 * Only compiled when FC_RENDER is defined (build.sh --render). */
#ifdef FC_RENDER
#include "fc_render.h"
#endif

/* All-time max trackers (global, bypasses PufferLib Log averaging) */
float g_max_wave_ever = 0;
float g_most_npcs_ever = 0;

/* Per-logging-period analytics (accumulated across all envs, averaged in my_log) */
float g_sum_prayer_uptime = 0;
float g_sum_prayer_uptime_melee = 0;
float g_sum_prayer_uptime_range = 0;
float g_sum_prayer_uptime_magic = 0;
float g_sum_correct_blocks = 0;
float g_sum_wrong_prayer_hits = 0;
float g_sum_no_prayer_hits = 0;
float g_sum_prayer_switches = 0;
float g_sum_damage_blocked = 0;
float g_sum_damage_taken = 0;
float g_sum_attack_when_ready = 0;
float g_sum_pots_used = 0;
float g_sum_avg_prayer_on_pot = 0;
float g_sum_food_eaten = 0;
float g_sum_avg_hp_on_food = 0;
float g_sum_food_wasted = 0;
float g_sum_pots_wasted = 0;
float g_sum_tokxil_melee_ticks = 0;
float g_sum_ketzek_melee_ticks = 0;
float g_sum_reached_wave_30 = 0;
float g_sum_cleared_wave_30 = 0;
float g_sum_reached_wave_31 = 0;
float g_n_analytics = 0;

/* ======================================================================== */
/* PufferLib Log struct (required fields)                                    */
/* ======================================================================== */

typedef struct {
    float score;
    float episode_return;
    float episode_length;
    float wave_reached;
    float n;  /* must be last */
} Log;

/* ======================================================================== */
/* PufferLib Environment struct                                              */
/* ======================================================================== */

/* Puffer-facing observation size:
 *   policy obs + masks for heads 0-4 only (move/attack/prayer/eat/drink)
 *   = 106 + 36 = 142
 */
#define FC_PUFFER_OBS_SIZE (FC_POLICY_OBS_SIZE + FC_MOVE_DIM + FC_ATTACK_DIM + FC_PRAYER_DIM + FC_EAT_DIM + FC_DRINK_DIM)

/* Number of action heads for PufferLib (v1: 5 heads, no walk-to-tile) */
#define FC_PUFFER_NUM_ATNS 5

typedef struct FightCaves {
    Log log;                    /* required by PufferLib */
    float* observations;        /* required: FC_PUFFER_OBS_SIZE per agent */
    float* actions;             /* required: NUM_ATNS per agent (vecenv uses float*) */
    float* rewards;             /* required: 1 per agent */
    float* terminals;           /* required: 1 per agent (vecenv uses float*) */
    int num_agents;             /* always 1 for Fight Caves */
    int rng;                    /* per-env RNG seed (set by vecenv.h) */

    /* Game state */
    FcState state;

    /* Reward shaping weights (from config) */
    float w_damage_dealt;
    float w_attack_attempt;
    float w_damage_taken;
    float w_npc_kill;
    float w_wave_clear;
    float w_jad_damage;
    float w_jad_kill;
    float w_player_death;
    float w_cave_complete;
    float w_food_used;
    float w_food_used_well;
    float w_prayer_pot_used;
    float w_correct_jad_prayer;
    float w_wrong_jad_prayer;
    float w_correct_danger_prayer;   /* correct prayer vs non-Jad NPCs */
    float w_wrong_danger_prayer;     /* wrong/no prayer vs dangerous NPCs */
    float w_invalid_action;
    float w_movement;
    float w_idle;
    float w_tick_penalty;

    /* Configurable shaping terms (kept separate from reward-feature weights) */
    float shape_food_full_waste_penalty;
    float shape_food_waste_scale;
    float shape_food_safe_hp_threshold;
    float shape_food_no_threat_penalty;
    float shape_pot_full_waste_penalty;
    float shape_pot_waste_scale;
    float shape_pot_safe_prayer_threshold;
    float shape_pot_no_threat_penalty;
    float shape_wrong_prayer_penalty;
    float shape_npc_specific_prayer_bonus;
    float shape_npc_melee_penalty;
    float shape_wasted_attack_penalty;
    float shape_wave_stall_base_penalty;
    float shape_wave_stall_cap;
    float shape_not_attacking_penalty;
    float shape_kiting_reward;
    float shape_unnecessary_prayer_penalty;
    int shape_resource_threat_window;
    int shape_kiting_min_dist;
    int shape_kiting_max_dist;
    int shape_wave_stall_start;
    int shape_wave_stall_ramp_interval;
    int shape_not_attacking_grace_ticks;

    /* Curriculum */
    int curriculum_wave;         /* 0 = always wave 1, >0 = X% of episodes start at this wave */
    float curriculum_pct;        /* fraction of episodes that start at curriculum_wave (0.0-1.0) */
    int curriculum_wave_2;       /* optional second late-wave curriculum bucket */
    float curriculum_pct_2;
    int curriculum_wave_3;       /* optional third late-wave curriculum bucket */
    float curriculum_pct_3;
    int disable_movement;        /* 1 = force idle movement, agent can't walk/run */
    int ticks_since_attack;      /* ticks since agent last dealt damage */
    int ticks_in_wave;           /* ticks since current wave started */

    /* Episode tracking */
    float ep_return;
    int ep_length;

    /* RNG seed counter (increments each episode for variety) */
    uint32_t seed_counter;
} FightCaves;

/* ======================================================================== */
/* Observation writer — policy obs + action mask into flat float buffer      */
/* ======================================================================== */

static void fc_puffer_write_obs(FightCaves* env) {
    float* obs = env->observations;

    /* Policy observations */
    fc_write_obs(&env->state, obs);

    /* Action mask: 36 floats (5 heads only, skip walk-to-tile heads) */
    float full_mask[FC_ACTION_MASK_SIZE];
    fc_write_mask(&env->state, full_mask);
    /* Copy only the first 36 floats (heads 0-4), skip heads 5-6 */
    int mask_size = FC_MOVE_DIM + FC_ATTACK_DIM + FC_PRAYER_DIM + FC_EAT_DIM + FC_DRINK_DIM;
    memcpy(obs + FC_POLICY_OBS_SIZE, full_mask, sizeof(float) * mask_size);
}

typedef struct {
    int melee_pressure_npcs;
    int any_threat;
    int imminent_threat;
    int tokxil_melee;
    int ketzek_melee;
} FcRewardContext;

static FcRewardContext fc_collect_reward_context(const FightCaves* env) {
    FcRewardContext ctx = {0};
    const FcState* state = &env->state;
    const FcPlayer* p = &state->player;
    int threat_window = (env->shape_resource_threat_window > 0)
        ? env->shape_resource_threat_window : 1;

    for (int i = 0; i < FC_MAX_NPCS; i++) {
        const FcNpc* n = &state->npcs[i];
        if (!n->active || n->is_dead) continue;

        int dist = fc_distance_to_npc(p->x, p->y, n);
        if (dist <= 1) {
            ctx.melee_pressure_npcs++;
            if (n->npc_type == NPC_TOK_XIL) ctx.tokxil_melee = 1;
            if (n->npc_type == NPC_KET_ZEK) ctx.ketzek_melee = 1;
        }

        if (dist <= n->attack_range) {
            ctx.any_threat = 1;
            if (n->attack_timer <= threat_window) {
                ctx.imminent_threat = 1;
            }
        }
    }

    for (int i = 0; i < p->num_pending_hits; i++) {
        const FcPendingHit* ph = &p->pending_hits[i];
        if (!ph->active) continue;
        ctx.any_threat = 1;
        if (ph->ticks_remaining <= threat_window) {
            ctx.imminent_threat = 1;
        }
    }

    return ctx;
}

static float fc_cap_stall_penalty(float penalty, float cap) {
    if (cap == 0.0f) return penalty;
    if (penalty < 0.0f && penalty < cap) return cap;
    if (penalty > 0.0f && penalty > cap) return cap;
    return penalty;
}

static int fc_pick_curriculum_wave(FightCaves* env) {
    const int waves[3] = {
        env->curriculum_wave,
        env->curriculum_wave_2,
        env->curriculum_wave_3,
    };
    const float pcts[3] = {
        env->curriculum_pct,
        env->curriculum_pct_2,
        env->curriculum_pct_3,
    };

    float roll = fc_rng_float(&env->state);
    float cumulative = 0.0f;
    for (int i = 0; i < 3; i++) {
        if (waves[i] <= 1 || pcts[i] <= 0.0f) continue;
        cumulative += pcts[i];
        if (cumulative > 1.0f) cumulative = 1.0f;
        if (roll < cumulative) {
            return waves[i];
        }
    }

    return 1;
}

static void fc_apply_curriculum_wave(FightCaves* env, int target_wave) {
    if (target_wave <= 1) return;
    if (target_wave > FC_NUM_WAVES) target_wave = FC_NUM_WAVES;

    for (int i = 0; i < FC_MAX_NPCS; i++) {
        env->state.npcs[i].active = 0;
        env->state.npcs[i].is_dead = 0;
    }
    env->state.npcs_remaining = 0;
    env->state.current_wave = target_wave;
    fc_wave_spawn(&env->state, target_wave);
    env->state.player.current_hp = env->state.player.max_hp;
    env->state.player.current_prayer = env->state.player.max_prayer;
}

/* ======================================================================== */
/* Reward computation from reward features                                   */
/* ======================================================================== */

static float fc_puffer_compute_reward(FightCaves* env) {
    /* Extract reward features directly.
     * c_step writes the policy observation immediately after reward
     * computation, so avoid rebuilding the full observation buffer here. */
    float rwd[FC_REWARD_FEATURES];
    fc_write_reward_features(&env->state, rwd);
    FcRewardContext ctx = fc_collect_reward_context(env);

    if (ctx.tokxil_melee) env->state.ep_tokxil_melee_ticks++;
    if (ctx.ketzek_melee) env->state.ep_ketzek_melee_ticks++;

    float reward = 0.0f;
    reward += rwd[FC_RWD_DAMAGE_DEALT]     * env->w_damage_dealt;
    reward += rwd[FC_RWD_ATTACK_ATTEMPT]   * env->w_attack_attempt;
    /* Damage taken penalty scales quadratically — big hits hurt way more.
     * 1 HP (0.014): -0.75 × 0.014² × 70 = -0.00015 (tiny)
     * 10 HP (0.143): -0.75 × 0.143² × 70 = -1.07 (moderate)
     * 20 HP (0.286): -0.75 × 0.286² × 70 = -4.28 (harsh)
     * 50 HP (0.714): -0.75 × 0.714² × 70 = -26.8 (devastating — pray!) */
    {
        float dmg_frac = rwd[FC_RWD_DAMAGE_TAKEN];
        reward += dmg_frac * dmg_frac * 70.0f * env->w_damage_taken;
    }
    reward += rwd[FC_RWD_NPC_KILL]         * env->w_npc_kill;
    /* Wave clear scales with wave number — higher waves = bigger reward.
     * current_wave has already advanced when WAVE_CLEAR fires, so the
     * wave just cleared = current_wave - 1.
     * Wave 1: 10 * 1 = 10. Wave 15: 10 * 15 = 150. Wave 30: 10 * 30 = 300.
     * Makes pushing to higher waves always more valuable than farming
     * easy waves for per-tick rewards. */
    if (rwd[FC_RWD_WAVE_CLEAR] > 0.0f) {
        int cleared_wave = env->state.current_wave - 1;
        if (cleared_wave < 1) cleared_wave = 1;
        reward += env->w_wave_clear * (float)cleared_wave;
    }
    reward += rwd[FC_RWD_JAD_DAMAGE]       * env->w_jad_damage;
    reward += rwd[FC_RWD_JAD_KILL]         * env->w_jad_kill;
    reward += rwd[FC_RWD_PLAYER_DEATH]     * env->w_player_death;
    reward += rwd[FC_RWD_CAVE_COMPLETE]    * env->w_cave_complete;

    /* Food — penalize waste and additional panic-eating when there is no
     * imminent threat. The thresholds and magnitudes are config-driven. */
    if (rwd[FC_RWD_FOOD_USED] > 0.0f) {
        int pre_hp = env->state.pre_eat_hp;
        int max_hp = env->state.player.max_hp;
        if (pre_hp >= max_hp) {
            reward += env->shape_food_full_waste_penalty;
        } else {
            int shark_heal = 200;
            int could_heal = max_hp - pre_hp;  /* how much HP was actually missing */
            int wasted = shark_heal - could_heal;
            if (wasted < 0) wasted = 0;
            reward += env->shape_food_waste_scale * ((float)wasted / (float)shark_heal);

            if (max_hp > 0) {
                float hp_frac = (float)pre_hp / (float)max_hp;
                if (!ctx.imminent_threat && hp_frac >= env->shape_food_safe_hp_threshold) {
                    reward += env->shape_food_no_threat_penalty;
                }
            }
        }
    }

    /* Prayer pots — penalize waste and additional over-drinking when there is
     * no meaningful threat. */
    if (rwd[FC_RWD_PRAYER_POT_USED] > 0.0f) {
        int pre_prayer = env->state.pre_drink_prayer;
        int max_prayer = env->state.player.max_prayer;
        if (pre_prayer >= max_prayer) {
            reward += env->shape_pot_full_waste_penalty;
        } else {
            int pot_restore = 170;
            int could_restore = max_prayer - pre_prayer;
            int wasted = pot_restore - could_restore;
            if (wasted < 0) wasted = 0;
            reward += env->shape_pot_waste_scale * ((float)wasted / (float)pot_restore);

            if (max_prayer > 0) {
                float prayer_frac = (float)pre_prayer / (float)max_prayer;
                if (!ctx.any_threat && prayer_frac >= env->shape_pot_safe_prayer_threshold) {
                    reward += env->shape_pot_no_threat_penalty;
                }
            }
        }
    }

    /* Prayer correctness for resolved ranged/magic hits.
     * These backend flags already reflect the locked prayer snapshot used by
     * combat resolution, so reward stays aligned with queued-hit semantics. */
    reward += rwd[FC_RWD_CORRECT_DANGER_PRAY] * env->w_correct_danger_prayer;
    reward += rwd[FC_RWD_WRONG_DANGER_PRAY]   * env->w_wrong_danger_prayer;
    reward += rwd[FC_RWD_WRONG_DANGER_PRAY]   * env->shape_wrong_prayer_penalty;

    /* NPC-specific prayer mapping bonus.
     * Uses the prayer snapshot that actually blocked the resolved hit. */
    {
        int prayer = env->state.player.hit_locked_prayer_this_tick;
        int src_type = env->state.player.hit_source_npc_type;
        if (env->state.player.hit_landed_this_tick &&
            env->state.player.hit_blocked_this_tick &&
            prayer != PRAYER_NONE) {
            if (src_type == NPC_KET_ZEK && prayer == PRAYER_PROTECT_MAGIC)
                reward += env->shape_npc_specific_prayer_bonus;
            else if (src_type == NPC_TOK_XIL && prayer == PRAYER_PROTECT_RANGE)
                reward += env->shape_npc_specific_prayer_bonus;
            else if ((src_type == NPC_YT_MEJKOT || src_type == NPC_TZ_KIH ||
                      src_type == NPC_TZ_KEK || src_type == NPC_TZ_KEK_SM) &&
                     prayer == PRAYER_PROTECT_MELEE)
                reward += env->shape_npc_specific_prayer_bonus;
        }
    }

    if (ctx.melee_pressure_npcs > 0) {
        reward += env->shape_npc_melee_penalty * (float)ctx.melee_pressure_npcs;
    }

    /* Prayer flick reward intentionally disabled for v17.
     * We now expose prayer_drain_counter in observation, but the shaping stays
     * off until we explicitly choose to revisit flick optimization. */

    /* Wasted attack penalty — ready to fire, has a target, but did not deal
     * damage this tick. */
    if (env->state.player.attack_timer <= 0 && rwd[FC_RWD_DAMAGE_DEALT] <= 0.0f &&
        env->state.npcs_remaining > 0 && env->state.player.attack_target_idx >= 0) {
        reward += env->shape_wasted_attack_penalty;
    }

    /* Wave stall penalty — escalating with a hard cap. */
    if (env->state.npcs_remaining > 0) {
        env->ticks_in_wave++;
        if (env->shape_wave_stall_base_penalty != 0.0f &&
            env->ticks_in_wave > env->shape_wave_stall_start) {
            int over = env->ticks_in_wave - env->shape_wave_stall_start;
            int ramps = 0;
            if (env->shape_wave_stall_ramp_interval > 0) {
                ramps = over / env->shape_wave_stall_ramp_interval;
            }
            {
                float penalty = env->shape_wave_stall_base_penalty * (1.0f + (float)ramps);
                reward += fc_cap_stall_penalty(penalty, env->shape_wave_stall_cap);
            }
        }
    }
    if (rwd[FC_RWD_WAVE_CLEAR] > 0.0f) {
        env->ticks_in_wave = 0;
    }
    reward += rwd[FC_RWD_INVALID_ACTION]   * env->w_invalid_action;
    reward += rwd[FC_RWD_MOVEMENT]         * env->w_movement;
    reward += rwd[FC_RWD_IDLE]             * env->w_idle;
    reward += rwd[FC_RWD_TICK_PENALTY]     * env->w_tick_penalty;

    /* Punish not attacking — only count ticks where the weapon cooldown is
     * ready, so time spent waiting between attacks does not look like
     * inactivity. A real attack attempt resets the timer even if it later
     * misses or rolls 0 damage. */
    if (rwd[FC_RWD_ATTACK_ATTEMPT] > 0.0f) {
        env->ticks_since_attack = 0;
    } else if (env->state.npcs_remaining > 0 && env->state.player.attack_timer <= 0) {
        env->ticks_since_attack++;
        if (env->ticks_since_attack >= env->shape_not_attacking_grace_ticks) {
            reward += env->shape_not_attacking_penalty;
        }
    } else if (env->state.npcs_remaining <= 0) {
        env->ticks_since_attack = 0;
    }

    /* Kiting reward — attack from preferred distance band. */
    if (rwd[FC_RWD_DAMAGE_DEALT] > 0.0f && env->state.player.attack_target_idx >= 0) {
        FcNpc* target = &env->state.npcs[env->state.player.attack_target_idx];
        if (target->active && !target->is_dead) {
            int dist = fc_distance_to_npc(env->state.player.x, env->state.player.y, target);
            if (dist >= env->shape_kiting_min_dist && dist <= env->shape_kiting_max_dist) {
                reward += env->shape_kiting_reward;
            }
        }
    }

    if (env->state.player.prayer != PRAYER_NONE && !ctx.any_threat) {
        reward += env->shape_unnecessary_prayer_penalty;
    }

    return reward;
}

/* ======================================================================== */
/* PufferLib interface: c_reset, c_step, c_render, c_close                   */
/* ======================================================================== */

void c_reset(FightCaves* env) {
    env->seed_counter++;
    fc_reset(&env->state, env->seed_counter);

    /* Curriculum: optionally start at a later wave from one of several
     * scaffold buckets. Any leftover probability starts from wave 1. */
    fc_apply_curriculum_wave(env, fc_pick_curriculum_wave(env));

    env->ep_return = 0.0f;
    env->ep_length = 0;
    env->ticks_since_attack = 0;
    env->ticks_in_wave = 0;

    /* Compute initial observations */
    fc_puffer_write_obs(env);
}

void c_step(FightCaves* env) {
    env->rewards[0] = 0.0f;
    env->terminals[0] = 0.0f;

    /* Convert float actions from network to int action heads.
     * PufferLib sends actions as floats in a flat array.
     * We only use 5 heads for v1 (skip walk-to-tile). */
    int actions[FC_NUM_ACTION_HEADS];
    memset(actions, 0, sizeof(actions));
    for (int h = 0; h < FC_PUFFER_NUM_ATNS; h++) {
        actions[h] = (int)env->actions[h];
    }
    /* Heads 5+6 (walk-to-tile) always 0 — not used in v1 */

    /* Force idle movement if disabled */
    if (env->disable_movement)
        actions[0] = 0;  /* FC_MOVE_IDLE */

    /* Step the game simulation */
    fc_step(&env->state, actions);

    /* Compute reward */
    float reward = fc_puffer_compute_reward(env);
    env->rewards[0] = reward;
    env->ep_return += reward;
    env->ep_length++;

    /* Write observations BEFORE checking terminal — agent must see
     * the terminal state observation, not the post-reset observation. */
    fc_puffer_write_obs(env);

    /* Check terminal */
    if (fc_is_terminal(&env->state)) {
        env->terminals[0] = 1.0f;
        env->log.score += (env->state.terminal == TERMINAL_CAVE_COMPLETE) ? 1.0f : 0.0f;
        env->log.episode_return += env->ep_return;
        env->log.episode_length += (float)env->ep_length;
        env->log.wave_reached += (float)env->state.current_wave;
        /* Analytics globals (averaged in my_log) */
        g_sum_prayer_uptime += (env->ep_length > 0)
            ? (float)env->state.ep_ticks_praying / (float)env->ep_length : 0.0f;
        g_sum_prayer_uptime_melee += (env->ep_length > 0)
            ? (float)env->state.ep_ticks_pray_melee / (float)env->ep_length : 0.0f;
        g_sum_prayer_uptime_range += (env->ep_length > 0)
            ? (float)env->state.ep_ticks_pray_range / (float)env->ep_length : 0.0f;
        g_sum_prayer_uptime_magic += (env->ep_length > 0)
            ? (float)env->state.ep_ticks_pray_magic / (float)env->ep_length : 0.0f;
        g_sum_correct_blocks += (float)env->state.ep_correct_blocks;
        g_sum_wrong_prayer_hits += (float)env->state.ep_wrong_prayer_hits;
        g_sum_no_prayer_hits += (float)env->state.ep_no_prayer_hits;
        g_sum_prayer_switches += (float)env->state.ep_prayer_switches;
        g_sum_damage_blocked += (float)env->state.ep_damage_blocked;
        g_sum_damage_taken += (float)env->state.player.total_damage_taken;
        g_sum_attack_when_ready += (env->state.ep_attack_ready_ticks > 0)
            ? (float)env->state.ep_attack_attempt_ticks / (float)env->state.ep_attack_ready_ticks
            : 0.0f;
        g_sum_pots_used += (float)env->state.ep_pots_used;
        g_sum_avg_prayer_on_pot += (env->state.ep_pots_used > 0 && env->state.player.max_prayer > 0)
            ? ((float)env->state.ep_pot_pre_prayer_sum /
               ((float)env->state.ep_pots_used * (float)env->state.player.max_prayer)) : 0.0f;
        g_sum_food_eaten += (float)env->state.ep_food_eaten;
        g_sum_avg_hp_on_food += (env->state.ep_food_eaten > 0 && env->state.player.max_hp > 0)
            ? ((float)env->state.ep_food_pre_hp_sum /
               ((float)env->state.ep_food_eaten * (float)env->state.player.max_hp)) : 0.0f;
        g_sum_food_wasted += (float)env->state.ep_food_overhealed;
        g_sum_pots_wasted += (float)env->state.ep_pots_overrestored;
        g_sum_tokxil_melee_ticks += (float)env->state.ep_tokxil_melee_ticks;
        g_sum_ketzek_melee_ticks += (float)env->state.ep_ketzek_melee_ticks;
        g_sum_reached_wave_30 += (float)env->state.ep_reached_wave_30;
        g_sum_cleared_wave_30 += (float)env->state.ep_cleared_wave_30;
        g_sum_reached_wave_31 += (float)env->state.ep_reached_wave_31;
        g_n_analytics += 1.0f;
        if ((float)env->state.current_wave > g_max_wave_ever)
            g_max_wave_ever = (float)env->state.current_wave;
        if ((float)env->state.total_npcs_killed > g_most_npcs_ever)
            g_most_npcs_ever = (float)env->state.total_npcs_killed;
        env->log.n += 1.0f;

        /* Auto-reset for next episode. Obs already written above
         * reflecting the terminal state. Next c_step will see the
         * reset state's obs after stepping. */
        c_reset(env);
    }
}

void c_render(FightCaves* env) {
    /* Rendering handled by external viewer via --policy-pipe mode.
     * See eval_viewer.py for the eval pipeline. */
    (void)env;
}

void c_close(FightCaves* env) {
    fc_destroy(&env->state);
}
