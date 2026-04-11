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
#include "fc_reward.h"
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
float g_sum_reached_wave_63 = 0;
float g_sum_jad_kill_rate = 0;
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
    float shape_low_prayer_pot_threshold;
    float shape_low_prayer_no_pot_penalty;
    float shape_low_prayer_pot_reward;
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

static FcRewardParams fc_reward_params_from_env(const FightCaves* env) {
    FcRewardParams params;
    memset(&params, 0, sizeof(params));

    params.w_damage_dealt = env->w_damage_dealt;
    params.w_attack_attempt = env->w_attack_attempt;
    params.w_damage_taken = env->w_damage_taken;
    params.w_npc_kill = env->w_npc_kill;
    params.w_wave_clear = env->w_wave_clear;
    params.w_jad_damage = env->w_jad_damage;
    params.w_jad_kill = env->w_jad_kill;
    params.w_player_death = env->w_player_death;
    params.w_cave_complete = env->w_cave_complete;
    params.w_correct_danger_prayer = env->w_correct_danger_prayer;
    params.w_wrong_danger_prayer = env->w_wrong_danger_prayer;
    params.w_invalid_action = env->w_invalid_action;
    params.w_movement = env->w_movement;
    params.w_idle = env->w_idle;
    params.w_tick_penalty = env->w_tick_penalty;

    params.shape_food_full_waste_penalty = env->shape_food_full_waste_penalty;
    params.shape_food_waste_scale = env->shape_food_waste_scale;
    params.shape_food_safe_hp_threshold = env->shape_food_safe_hp_threshold;
    params.shape_food_no_threat_penalty = env->shape_food_no_threat_penalty;
    params.shape_pot_full_waste_penalty = env->shape_pot_full_waste_penalty;
    params.shape_pot_waste_scale = env->shape_pot_waste_scale;
    params.shape_pot_safe_prayer_threshold = env->shape_pot_safe_prayer_threshold;
    params.shape_pot_no_threat_penalty = env->shape_pot_no_threat_penalty;
    params.shape_low_prayer_pot_threshold = env->shape_low_prayer_pot_threshold;
    params.shape_low_prayer_no_pot_penalty = env->shape_low_prayer_no_pot_penalty;
    params.shape_low_prayer_pot_reward = env->shape_low_prayer_pot_reward;
    params.shape_wrong_prayer_penalty = env->shape_wrong_prayer_penalty;
    params.shape_npc_specific_prayer_bonus = env->shape_npc_specific_prayer_bonus;
    params.shape_npc_melee_penalty = env->shape_npc_melee_penalty;
    params.shape_wasted_attack_penalty = env->shape_wasted_attack_penalty;
    params.shape_wave_stall_base_penalty = env->shape_wave_stall_base_penalty;
    params.shape_wave_stall_cap = env->shape_wave_stall_cap;
    params.shape_not_attacking_penalty = env->shape_not_attacking_penalty;
    params.shape_kiting_reward = env->shape_kiting_reward;
    params.shape_unnecessary_prayer_penalty = env->shape_unnecessary_prayer_penalty;

    params.shape_resource_threat_window = env->shape_resource_threat_window;
    params.shape_kiting_min_dist = env->shape_kiting_min_dist;
    params.shape_kiting_max_dist = env->shape_kiting_max_dist;
    params.shape_wave_stall_start = env->shape_wave_stall_start;
    params.shape_wave_stall_ramp_interval = env->shape_wave_stall_ramp_interval;
    params.shape_not_attacking_grace_ticks = env->shape_not_attacking_grace_ticks;

    return params;
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
    FcRewardParams params = fc_reward_params_from_env(env);
    FcRewardRuntime runtime = {
        env->ticks_since_attack,
        env->ticks_in_wave,
    };
    FcRewardBreakdown breakdown =
        fc_reward_compute_breakdown(&env->state, &params, &runtime);

    env->ticks_since_attack = runtime.ticks_since_attack;
    env->ticks_in_wave = runtime.ticks_in_wave;

    if (breakdown.threat_ctx.tokxil_melee) env->state.ep_tokxil_melee_ticks++;
    if (breakdown.threat_ctx.ketzek_melee) env->state.ep_ketzek_melee_ticks++;

    return breakdown.total;
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
        g_sum_reached_wave_63 += (float)env->state.ep_reached_wave_63;
        g_sum_jad_kill_rate += (float)env->state.ep_jad_killed;
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
