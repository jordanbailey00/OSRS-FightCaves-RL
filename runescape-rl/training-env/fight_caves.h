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

/* Observation size: 126 policy features + 36 action mask = 162 */
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
    float w_damage_taken;
    float w_npc_kill;
    float w_wave_clear;
    float w_jad_damage;
    float w_jad_kill;
    float w_player_death;
    float w_cave_complete;
    float w_food_used;
    float w_prayer_pot_used;
    float w_correct_jad_prayer;
    float w_wrong_jad_prayer;
    float w_correct_danger_prayer;   /* correct prayer vs non-Jad NPCs */
    float w_wrong_danger_prayer;     /* wrong/no prayer vs dangerous NPCs */
    float w_invalid_action;
    float w_movement;
    float w_idle;
    float w_tick_penalty;

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

    /* Policy observations: 126 floats */
    fc_write_obs(&env->state, obs);

    /* Action mask: 36 floats (5 heads only, skip walk-to-tile heads) */
    float full_mask[FC_ACTION_MASK_SIZE];
    fc_write_mask(&env->state, full_mask);
    /* Copy only the first 36 floats (heads 0-4), skip heads 5-6 */
    int mask_size = FC_MOVE_DIM + FC_ATTACK_DIM + FC_PRAYER_DIM + FC_EAT_DIM + FC_DRINK_DIM;
    memcpy(obs + FC_POLICY_OBS_SIZE, full_mask, sizeof(float) * mask_size);
}

/* ======================================================================== */
/* Reward computation from reward features                                   */
/* ======================================================================== */

static float fc_puffer_compute_reward(FightCaves* env) {
    /* Extract reward features from obs buffer */
    float obs_buf[FC_OBS_SIZE];
    fc_write_obs(&env->state, obs_buf);
    float* rwd = obs_buf + FC_REWARD_START;

    float reward = 0.0f;
    reward += rwd[FC_RWD_DAMAGE_DEALT]     * env->w_damage_dealt;
    reward += rwd[FC_RWD_DAMAGE_TAKEN]     * env->w_damage_taken;
    reward += rwd[FC_RWD_NPC_KILL]         * env->w_npc_kill;
    reward += rwd[FC_RWD_WAVE_CLEAR]       * env->w_wave_clear;
    reward += rwd[FC_RWD_JAD_DAMAGE]       * env->w_jad_damage;
    reward += rwd[FC_RWD_JAD_KILL]         * env->w_jad_kill;
    reward += rwd[FC_RWD_PLAYER_DEATH]     * env->w_player_death;
    reward += rwd[FC_RWD_CAVE_COMPLETE]    * env->w_cave_complete;
    /* Food/pot penalty scales by how much of the heal was wasted.
     * Shark heals 200 tenths (20 HP). If current_hp + 200 > max_hp,
     * the overheal is waste. Penalty = base * (wasted / total_heal).
     * Eating at 50/70 HP: heals to 70, 0 waste → 0 penalty.
     * Eating at 68/70 HP: heals 2, wastes 18 → penalty * 0.9.
     * Same logic for prayer pots (restore 170 tenths = 17 points). */
    if (rwd[FC_RWD_FOOD_USED] > 0.0f) {
        int shark_heal = 200;  /* tenths */
        int overheal = (env->state.player.current_hp + shark_heal) - env->state.player.max_hp;
        if (overheal < 0) overheal = 0;
        float waste_frac = (float)overheal / (float)shark_heal;  /* 0 = no waste, 1 = total waste */
        reward += rwd[FC_RWD_FOOD_USED] * env->w_food_used * (1.0f + waste_frac * 9.0f);
    }
    if (rwd[FC_RWD_PRAYER_POT_USED] > 0.0f) {
        int pot_restore = 170;  /* tenths (17 prayer points) */
        int overrestore = (env->state.player.current_prayer + pot_restore) - env->state.player.max_prayer;
        if (overrestore < 0) overrestore = 0;
        float waste_frac = (float)overrestore / (float)pot_restore;
        reward += rwd[FC_RWD_PRAYER_POT_USED] * env->w_prayer_pot_used * (1.0f + waste_frac * 9.0f);
    }
    /* Prayer rewards disabled in v5. Agent should discover prayer's value
     * organically through the damage_taken penalty — correct prayer = 0
     * damage = no penalty. Wrong/no prayer = full damage = penalty.
     * The reward signal is already implicit in w_damage_taken. */
    /* reward += rwd[FC_RWD_CORRECT_JAD_PRAY] * env->w_correct_jad_prayer; */
    /* reward += rwd[FC_RWD_WRONG_JAD_PRAY]   * env->w_wrong_jad_prayer;   */
    reward += rwd[FC_RWD_INVALID_ACTION]   * env->w_invalid_action;
    reward += rwd[FC_RWD_MOVEMENT]         * env->w_movement;
    reward += rwd[FC_RWD_IDLE]             * env->w_idle;
    reward += rwd[FC_RWD_TICK_PENALTY]     * env->w_tick_penalty;

    return reward;
}

/* ======================================================================== */
/* PufferLib interface: c_reset, c_step, c_render, c_close                   */
/* ======================================================================== */

void c_reset(FightCaves* env) {
    env->seed_counter++;
    fc_reset(&env->state, env->seed_counter);

    env->ep_return = 0.0f;
    env->ep_length = 0;

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
        env->log.n += 1.0f;

        /* Auto-reset for next episode. Obs already written above
         * reflecting the terminal state. Next c_step will see the
         * reset state's obs after stepping. */
        c_reset(env);
    }
}

void c_render(FightCaves* env) {
    /* TODO: Connect to Raylib viewer for eval mode.
     * For now, no-op. Will reuse demo-env viewer code. */
    (void)env;
}

void c_close(FightCaves* env) {
    fc_destroy(&env->state);
}
