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
float g_sum_correct_blocks = 0;
float g_sum_damage_taken = 0;
float g_sum_pots_used = 0;
float g_sum_food_eaten = 0;
float g_sum_food_wasted = 0;
float g_sum_pots_wasted = 0;
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

    /* Curriculum */
    int curriculum_wave;         /* 0 = always wave 1, >0 = X% of episodes start at this wave */
    float curriculum_pct;        /* fraction of episodes that start at curriculum_wave (0.0-1.0) */
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
    /* Food — penalize wasting scarce resources. No reward for eating.
     * Full HP: -5.0 strong penalty. Otherwise: penalty scaled by waste fraction. */
    if (rwd[FC_RWD_FOOD_USED] > 0.0f) {
        int pre_hp = env->state.pre_eat_hp;
        int max_hp = env->state.player.max_hp;
        if (pre_hp >= max_hp) {
            reward += -5.0f;  /* eating at full HP — complete waste */
        } else {
            int shark_heal = 200;
            int could_heal = max_hp - pre_hp;  /* how much HP was actually missing */
            int wasted = shark_heal - could_heal;
            if (wasted < 0) wasted = 0;
            reward += -(float)wasted / (float)shark_heal;  /* 0 to -1.0 based on waste */
        }
    }
    /* Prayer pot — penalize wasting scarce resources. No reward for drinking.
     * Full prayer: -5.0 strong penalty. Otherwise: penalty scaled by waste fraction. */
    if (rwd[FC_RWD_PRAYER_POT_USED] > 0.0f) {
        int pre_prayer = env->state.pre_drink_prayer;
        int max_prayer = env->state.player.max_prayer;
        if (pre_prayer >= max_prayer) {
            reward += -5.0f;  /* drinking at full prayer — complete waste */
        } else {
            int pot_restore = 170;
            int could_restore = max_prayer - pre_prayer;
            int wasted = pot_restore - could_restore;
            if (wasted < 0) wasted = 0;
            reward += -(float)wasted / (float)pot_restore;  /* 0 to -1.0 based on waste */
        }
    }
    /* Prayer correctness — +1/-1 for all attack styles.
     * Reward correct prayer when any hit resolves (melee, ranged, magic).
     * Penalize wrong prayer (active but doesn't match attack style). */
    {
        int style = env->state.player.hit_style_this_tick;
        int prayer = env->state.player.prayer;
        if (style != ATTACK_NONE && prayer != PRAYER_NONE) {
            if (fc_prayer_blocks_style(prayer, style)) {
                reward += env->w_correct_danger_prayer;  /* +1.0 correct prayer */
            } else {
                reward += -1.0f;
            }
        }
    }
    /* NPC-specific prayer rewards — +2.0 when correct prayer blocks a hit
     * from a specific NPC type while agent has a target selected.
     * Teaches explicit NPC→prayer mapping for ALL combat NPCs. */
    {
        int style = env->state.player.hit_style_this_tick;
        int prayer = env->state.player.prayer;
        int src_type = env->state.player.hit_source_npc_type;
        if (style != ATTACK_NONE && prayer != PRAYER_NONE &&
            fc_prayer_blocks_style(prayer, style) &&
            env->state.player.attack_target_idx >= 0) {
            if (src_type == NPC_KET_ZEK && prayer == PRAYER_PROTECT_MAGIC)
                reward += 2.0f;
            else if (src_type == NPC_TOK_XIL && prayer == PRAYER_PROTECT_RANGE)
                reward += 2.0f;
            else if ((src_type == NPC_YT_MEJKOT || src_type == NPC_TZ_KIH ||
                      src_type == NPC_TZ_KEK || src_type == NPC_TZ_KEK_SM) &&
                     prayer == PRAYER_PROTECT_MELEE)
                reward += 2.0f;
        }
    }
    /* Penalize ANY NPC in melee range — all NPCs should be kited.
     * -0.5/tick per NPC at distance 1. */
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        FcNpc* n = &env->state.npcs[i];
        if (!n->active || n->is_dead) continue;
        int dist = fc_distance_to_npc(env->state.player.x, env->state.player.y, n);
        if (dist <= 1) {
            reward += -0.5f;
        }
    }
    /* Prayer flick reward — +0.5 ONLY when prayer was just toggled,
     * blocked an actual hit this tick, and no prayer point was drained.
     * Must be attacking something. Cannot be farmed by toggling. */
    {
        int style = env->state.player.hit_style_this_tick;
        int prayer = env->state.player.prayer;
        if (style != ATTACK_NONE && prayer != PRAYER_NONE &&
            fc_prayer_blocks_style(prayer, style) &&
            env->state.player.prayer_changed_this_tick &&
            env->state.player.attack_target_idx >= 0) {
            int drain_rate = 12;
            int resistance = 60 + 2 * env->state.player.prayer_bonus;
            if (env->state.player.prayer_drain_counter + drain_rate <= resistance) {
                reward += 0.5f;
            }
        }
    }
    /* Wasted attack penalty — -0.3 when attack timer is 0 (ready to fire)
     * but agent didn't deal damage this tick and NPCs are alive. */
    if (env->state.player.attack_timer <= 0 && rwd[FC_RWD_DAMAGE_DEALT] <= 0.0f &&
        env->state.npcs_remaining > 0 && env->state.player.attack_target_idx >= 0) {
        reward += -0.3f;
    }
    /* Wave stall penalty — escalating after 500 ticks in the same wave.
     * -0.75 base, increases by 0.75 every 50 ticks beyond 500.
     * 500 ticks: -0.75, 550: -1.50, 600: -2.25, etc. */
    if (env->state.npcs_remaining > 0) {
        env->ticks_in_wave++;
        if (env->ticks_in_wave > 500) {
            int over = env->ticks_in_wave - 500;
            float scale = 1.0f + (float)(over / 50);
            reward += -0.75f * scale;
        }
    }
    if (rwd[FC_RWD_WAVE_CLEAR] > 0.0f) {
        env->ticks_in_wave = 0;
    }
    reward += rwd[FC_RWD_INVALID_ACTION]   * env->w_invalid_action;
    reward += rwd[FC_RWD_MOVEMENT]         * env->w_movement;
    reward += rwd[FC_RWD_IDLE]             * env->w_idle;
    reward += rwd[FC_RWD_TICK_PENALTY]     * env->w_tick_penalty;

    /* Punish not attacking — if agent hasn't dealt damage in 2+ ticks
     * and there are NPCs alive, penalize. Forces combat engagement. */
    if (rwd[FC_RWD_DAMAGE_DEALT] > 0.0f) {
        env->ticks_since_attack = 0;
    } else if (env->state.npcs_remaining > 0) {
        env->ticks_since_attack++;
        if (env->ticks_since_attack >= 2) {
            reward += -0.5f;
        }
    }

    /* Kiting reward — +1.0 when agent attacks from near-max range (5-7 tiles).
     * Incentivizes fighting at distance so melee NPCs can't reach and
     * dual-mode NPCs (Tok-Xil, Ket-Zek) use ranged/magic attacks,
     * forcing the agent to learn correct prayer switching. */
    if (rwd[FC_RWD_DAMAGE_DEALT] > 0.0f && env->state.player.attack_target_idx >= 0) {
        FcNpc* target = &env->state.npcs[env->state.player.attack_target_idx];
        if (target->active && !target->is_dead) {
            int dist = fc_distance_to_npc(env->state.player.x, env->state.player.y, target);
            if (dist >= 5 && dist <= 7) {
                reward += 1.0f;
            }
        }
    }

    /* Unnecessary prayer penalty — -0.2/tick when prayer is active but
     * no NPC is threatening the player (none in attack range, no pending
     * hits in flight). Teaches the agent to only pray when needed,
     * conserving prayer points. */
    if (env->state.player.prayer != PRAYER_NONE) {
        int threatened = 0;
        /* Check if any NPC is within its attack range of the player */
        for (int i = 0; i < FC_MAX_NPCS; i++) {
            FcNpc* n = &env->state.npcs[i];
            if (!n->active || n->is_dead) continue;
            int dist = fc_distance_to_npc(env->state.player.x, env->state.player.y, n);
            if (dist <= n->attack_range) { threatened = 1; break; }
        }
        /* Check if any pending hits are in flight */
        if (!threatened) {
            for (int i = 0; i < env->state.player.num_pending_hits; i++) {
                if (env->state.player.pending_hits[i].active) { threatened = 1; break; }
            }
        }
        if (!threatened) {
            reward += -0.2f;
        }
    }

    return reward;
}

/* ======================================================================== */
/* PufferLib interface: c_reset, c_step, c_render, c_close                   */
/* ======================================================================== */

void c_reset(FightCaves* env) {
    env->seed_counter++;
    fc_reset(&env->state, env->seed_counter);

    /* Curriculum: skip to a later wave for a percentage of episodes.
     * Directly sets wave number and spawns NPCs — no fc_step loop. */
    if (env->curriculum_wave > 1 && env->curriculum_pct > 0.0f) {
        float roll = (float)(env->seed_counter % 1000) / 1000.0f;
        if (roll < env->curriculum_pct) {
            int target = env->curriculum_wave;
            if (target > FC_NUM_WAVES) target = FC_NUM_WAVES;
            /* Clear any wave 1 NPCs that fc_reset spawned */
            for (int i = 0; i < FC_MAX_NPCS; i++) {
                env->state.npcs[i].active = 0;
                env->state.npcs[i].is_dead = 0;
            }
            env->state.npcs_remaining = 0;
            /* Set wave and spawn */
            env->state.current_wave = target;
            fc_wave_spawn(&env->state, target);
            /* Restore player to full */
            env->state.player.current_hp = env->state.player.max_hp;
            env->state.player.current_prayer = env->state.player.max_prayer;
        }
    }

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
        g_sum_correct_blocks += (float)env->state.ep_correct_blocks;
        g_sum_damage_taken += (float)env->state.player.total_damage_taken;
        g_sum_pots_used += (float)env->state.ep_pots_used;
        g_sum_food_eaten += (float)env->state.ep_food_eaten;
        g_sum_food_wasted += (float)env->state.ep_food_overhealed;
        g_sum_pots_wasted += (float)env->state.ep_pots_overrestored;
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
