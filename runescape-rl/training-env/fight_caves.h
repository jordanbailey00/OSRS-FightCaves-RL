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
float g_sum_max_wave_ticks = 0;
float g_sum_max_wave_ticks_wave = 0;
float g_sum_reached_wave_63 = 0;
float g_sum_jad_kill_rate = 0;
float g_n_analytics = 0;

/* ======================================================================== */
/* PufferLib Log struct (required fields)                                    */
/* ======================================================================== */

typedef struct {
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
    float w_correct_jad_prayer;
    float w_correct_danger_prayer;   /* correct prayer vs ranged/magic danger hits */
    float w_invalid_action;
    float w_tick_penalty;

    /* Configurable shaping terms (kept separate from reward-feature weights) */
    float shape_food_full_waste_penalty;
    float shape_food_waste_scale;
    float shape_food_no_threat_penalty;
    float shape_pot_full_waste_penalty;
    float shape_pot_waste_scale;
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
    float shape_jad_heal_penalty;
    float shape_safespot_attack_reward;
    float shape_reach_wave_60_bonus;
    float shape_reach_wave_61_bonus;
    float shape_reach_wave_62_bonus;
    float shape_reach_wave_63_bonus;
    float shape_jad_kill_bonus;
    int shape_resource_threat_window;
    int shape_kiting_min_dist;
    int shape_kiting_max_dist;
    int shape_wave_stall_start;
    int shape_wave_stall_ramp_interval;
    int shape_not_attacking_grace_ticks;
    int ticks_since_attack;      /* ticks since agent last dealt damage */
    int ticks_in_wave;           /* ticks since current wave started */

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
    params.w_correct_jad_prayer = env->w_correct_jad_prayer;
    params.w_correct_danger_prayer = env->w_correct_danger_prayer;
    params.w_invalid_action = env->w_invalid_action;
    params.w_tick_penalty = env->w_tick_penalty;

    params.shape_food_full_waste_penalty = env->shape_food_full_waste_penalty;
    params.shape_food_waste_scale = env->shape_food_waste_scale;
    params.shape_food_no_threat_penalty = env->shape_food_no_threat_penalty;
    params.shape_pot_full_waste_penalty = env->shape_pot_full_waste_penalty;
    params.shape_pot_waste_scale = env->shape_pot_waste_scale;
    params.shape_pot_no_threat_penalty = env->shape_pot_no_threat_penalty;
    params.shape_wrong_prayer_penalty = env->shape_wrong_prayer_penalty;
    params.shape_npc_specific_prayer_bonus = env->shape_npc_specific_prayer_bonus;
    params.shape_npc_melee_penalty = env->shape_npc_melee_penalty;
    params.shape_wasted_attack_penalty = env->shape_wasted_attack_penalty;
    params.shape_wave_stall_base_penalty = env->shape_wave_stall_base_penalty;
    params.shape_wave_stall_cap = env->shape_wave_stall_cap;
    params.shape_not_attacking_penalty = env->shape_not_attacking_penalty;
    params.shape_kiting_reward = env->shape_kiting_reward;
    params.shape_unnecessary_prayer_penalty = env->shape_unnecessary_prayer_penalty;
    params.shape_jad_heal_penalty = env->shape_jad_heal_penalty;
    params.shape_safespot_attack_reward = env->shape_safespot_attack_reward;
    params.shape_reach_wave_60_bonus = env->shape_reach_wave_60_bonus;
    params.shape_reach_wave_61_bonus = env->shape_reach_wave_61_bonus;
    params.shape_reach_wave_62_bonus = env->shape_reach_wave_62_bonus;
    params.shape_reach_wave_63_bonus = env->shape_reach_wave_63_bonus;
    params.shape_jad_kill_bonus = env->shape_jad_kill_bonus;

    params.shape_resource_threat_window = env->shape_resource_threat_window;
    params.shape_kiting_min_dist = env->shape_kiting_min_dist;
    params.shape_kiting_max_dist = env->shape_kiting_max_dist;
    params.shape_wave_stall_start = env->shape_wave_stall_start;
    params.shape_wave_stall_ramp_interval = env->shape_wave_stall_ramp_interval;
    params.shape_not_attacking_grace_ticks = env->shape_not_attacking_grace_ticks;

    return params;
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

    /* Step the game simulation */
    fc_step(&env->state, actions);

    /* Compute reward */
    float reward = fc_puffer_compute_reward(env);
    env->rewards[0] = reward;
    env->ep_length++;

    /* Write observations BEFORE checking terminal — agent must see
     * the terminal state observation, not the post-reset observation. */
    fc_puffer_write_obs(env);

    /* Check terminal */
    if (fc_is_terminal(&env->state)) {
        env->terminals[0] = 1.0f;
        env->log.episode_length += (float)env->ep_length;
        env->log.wave_reached += (float)env->state.current_wave;
        /* Analytics globals (averaged in my_log) */
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
        g_sum_max_wave_ticks += (float)env->state.ep_max_wave_ticks;
        g_sum_max_wave_ticks_wave += (float)env->state.ep_max_wave_ticks_wave;
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
     * See demo-env/eval_viewer.py for the eval pipeline. */
    (void)env;
}

void c_close(FightCaves* env) {
    fc_destroy(&env->state);
}
