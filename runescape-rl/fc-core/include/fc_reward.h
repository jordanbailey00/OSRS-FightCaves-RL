#ifndef FC_REWARD_H
#define FC_REWARD_H

#include <string.h>

#include "fc_types.h"
#include "fc_contracts.h"
#include "fc_api.h"
#include "fc_combat.h"
#include "fc_npc.h"

typedef struct {
    float w_damage_dealt;
    float w_attack_attempt;
    float w_damage_taken;
    float w_npc_kill;
    float w_wave_clear;
    float w_jad_damage;
    float w_jad_kill;          /* combined Jad kill + cave complete reward */
    float w_player_death;
    float w_correct_jad_prayer;
    float w_correct_danger_prayer;
    float w_invalid_action;
    float w_tick_penalty;

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
} FcRewardParams;

typedef struct {
    int ticks_since_attack;
    int ticks_in_wave;
} FcRewardRuntime;

typedef struct {
    int melee_pressure_npcs;
    int any_threat;
    int imminent_threat;
    int tokxil_melee;
    int ketzek_melee;
} FcRewardThreatContext;

typedef struct {
    float raw[FC_REWARD_FEATURES];

    float damage_dealt;
    float attack_attempt;
    float damage_taken;
    float npc_kill;
    float wave_clear;
    float jad_damage;
    float jad_kill;
    float player_death;

    float food_waste;
    float food_safe;
    float pot_waste;
    float pot_safe;

    float correct_jad_prayer;
    float correct_danger_prayer;
    float wrong_danger_prayer_shape;
    float npc_specific_prayer;
    float melee_pressure;
    float wasted_attack;
    float wave_stall;
    float not_attacking;
    float kiting;
    float safespot_attack;
    float unnecessary_prayer;
    float jad_heal_penalty;
    float late_wave_bonus;
    float jad_kill_bonus;

    float invalid_action;
    float tick_penalty;

    float total;
    FcRewardThreatContext threat_ctx;
} FcRewardBreakdown;

static inline FcRewardParams fc_reward_default_params(void) {
    FcRewardParams params;
    memset(&params, 0, sizeof(params));

    params.w_damage_dealt = 0.5f;
    params.w_attack_attempt = 0.1f;
    params.w_damage_taken = -0.6f;
    params.w_npc_kill = 3.0f;
    params.w_wave_clear = 10.0f;
    params.w_jad_damage = 2.0f;
    params.w_jad_kill = 150.0f;
    params.w_player_death = -20.0f;
    params.w_correct_jad_prayer = 0.0f;
    params.w_correct_danger_prayer = 0.25f;
    params.w_invalid_action = -0.1f;
    params.w_tick_penalty = -0.005f;

    params.shape_food_full_waste_penalty = -6.5f;
    params.shape_food_waste_scale = -1.2f;
    params.shape_food_no_threat_penalty = 0.0f;
    params.shape_pot_full_waste_penalty = -6.5f;
    params.shape_pot_waste_scale = -1.2f;
    params.shape_pot_no_threat_penalty = 0.0f;
    params.shape_wrong_prayer_penalty = -1.25f;
    params.shape_npc_specific_prayer_bonus = 1.5f;
    params.shape_npc_melee_penalty = -0.3f;
    params.shape_wasted_attack_penalty = -0.1f;
    params.shape_wave_stall_base_penalty = -0.5f;
    params.shape_wave_stall_cap = -2.0f;
    params.shape_not_attacking_penalty = -0.01f;
    params.shape_kiting_reward = 1.0f;
    params.shape_unnecessary_prayer_penalty = -0.2f;
    params.shape_jad_heal_penalty = 0.0f;
    params.shape_safespot_attack_reward = 0.0f;
    params.shape_reach_wave_60_bonus = 0.0f;
    params.shape_reach_wave_61_bonus = 0.0f;
    params.shape_reach_wave_62_bonus = 0.0f;
    params.shape_reach_wave_63_bonus = 0.0f;
    params.shape_jad_kill_bonus = 0.0f;

    params.shape_resource_threat_window = 2;
    params.shape_kiting_min_dist = 5;
    params.shape_kiting_max_dist = 7;
    params.shape_wave_stall_start = 500;
    params.shape_wave_stall_ramp_interval = 50;
    params.shape_not_attacking_grace_ticks = 2;

    return params;
}

static inline void fc_reward_runtime_reset(FcRewardRuntime* runtime) {
    runtime->ticks_since_attack = 0;
    runtime->ticks_in_wave = 0;
}

static inline FcRewardThreatContext fc_reward_collect_threat_context(
        const FcState* state, const FcRewardParams* params) {
    FcRewardThreatContext ctx;
    const FcPlayer* p = &state->player;
    int threat_window = (params->shape_resource_threat_window > 0)
        ? params->shape_resource_threat_window : 1;

    memset(&ctx, 0, sizeof(ctx));

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

static inline float fc_reward_cap_stall_penalty(float penalty, float cap) {
    if (cap == 0.0f) return penalty;
    if (penalty < 0.0f && penalty < cap) return cap;
    if (penalty > 0.0f && penalty > cap) return cap;
    return penalty;
}

static inline FcRewardBreakdown fc_reward_compute_breakdown(
        const FcState* state, const FcRewardParams* params, FcRewardRuntime* runtime) {
    FcRewardBreakdown out;
    const FcPlayer* p = &state->player;
    int prayer_reward_idle;

    memset(&out, 0, sizeof(out));
    fc_write_reward_features(state, out.raw);
    out.threat_ctx = fc_reward_collect_threat_context(state, params);
    prayer_reward_idle =
        (runtime->ticks_since_attack >= 1 && out.raw[FC_RWD_ATTACK_ATTEMPT] <= 0.0f);

    out.damage_dealt = out.raw[FC_RWD_DAMAGE_DEALT] * params->w_damage_dealt;
    out.attack_attempt = out.raw[FC_RWD_ATTACK_ATTEMPT] * params->w_attack_attempt;

    {
        float dmg_frac = out.raw[FC_RWD_DAMAGE_TAKEN];
        out.damage_taken = dmg_frac * dmg_frac * 70.0f * params->w_damage_taken;
    }

    out.npc_kill = out.raw[FC_RWD_NPC_KILL] * params->w_npc_kill;

    if (out.raw[FC_RWD_WAVE_CLEAR] > 0.0f) {
        int cleared_wave = state->current_wave - 1;
        if (cleared_wave < 1) cleared_wave = 1;
        out.wave_clear = params->w_wave_clear * (float)cleared_wave;
    }

    out.jad_damage = out.raw[FC_RWD_JAD_DAMAGE] * params->w_jad_damage;
    out.jad_kill = out.raw[FC_RWD_JAD_KILL] * params->w_jad_kill;
    out.player_death = out.raw[FC_RWD_PLAYER_DEATH] * params->w_player_death;

    if (out.raw[FC_RWD_FOOD_USED] > 0.0f) {
        int pre_hp = state->pre_eat_hp;
        int max_hp = p->max_hp;
        if (pre_hp >= max_hp) {
            out.food_waste += params->shape_food_full_waste_penalty;
        } else {
            int shark_heal = 200;
            int could_heal = max_hp - pre_hp;
            int wasted = shark_heal - could_heal;
            if (wasted < 0) wasted = 0;
            out.food_waste += params->shape_food_waste_scale *
                ((float)wasted / (float)shark_heal);

            if (!out.threat_ctx.imminent_threat) {
                out.food_safe += params->shape_food_no_threat_penalty;
            }
        }
    }

    if (out.raw[FC_RWD_PRAYER_POT_USED] > 0.0f) {
        int pre_prayer = state->pre_drink_prayer;
        int max_prayer = p->max_prayer;
        if (pre_prayer >= max_prayer) {
            out.pot_waste += params->shape_pot_full_waste_penalty;
        } else {
            int pot_restore = 170;
            int could_restore = max_prayer - pre_prayer;
            int wasted = pot_restore - could_restore;
            if (wasted < 0) wasted = 0;
            out.pot_waste += params->shape_pot_waste_scale *
                ((float)wasted / (float)pot_restore);

            if (!out.threat_ctx.any_threat) {
                out.pot_safe += params->shape_pot_no_threat_penalty;
            }
        }
    }

    if (!prayer_reward_idle) {
        out.correct_jad_prayer =
            out.raw[FC_RWD_CORRECT_JAD_PRAY] * params->w_correct_jad_prayer;
        out.correct_danger_prayer =
            out.raw[FC_RWD_CORRECT_DANGER_PRAY] * params->w_correct_danger_prayer;
    }
    out.wrong_danger_prayer_shape =
        out.raw[FC_RWD_WRONG_DANGER_PRAY] * params->shape_wrong_prayer_penalty;

    {
        int prayer = p->hit_locked_prayer_this_tick;
        int src_type = p->hit_source_npc_type;
        if (!prayer_reward_idle &&
            p->hit_landed_this_tick && p->hit_blocked_this_tick &&
            prayer != PRAYER_NONE) {
            if (src_type == NPC_KET_ZEK && prayer == PRAYER_PROTECT_MAGIC) {
                out.npc_specific_prayer = params->shape_npc_specific_prayer_bonus;
            } else if (src_type == NPC_TOK_XIL &&
                       prayer == PRAYER_PROTECT_RANGE) {
                out.npc_specific_prayer = params->shape_npc_specific_prayer_bonus;
            } else if ((src_type == NPC_YT_MEJKOT || src_type == NPC_TZ_KIH ||
                        src_type == NPC_TZ_KEK || src_type == NPC_TZ_KEK_SM) &&
                       prayer == PRAYER_PROTECT_MELEE) {
                out.npc_specific_prayer = params->shape_npc_specific_prayer_bonus;
            }
        }
    }

    if (out.threat_ctx.melee_pressure_npcs > 0) {
        out.melee_pressure = params->shape_npc_melee_penalty *
            (float)out.threat_ctx.melee_pressure_npcs;
    }

    if (p->attack_timer <= 0 && out.raw[FC_RWD_DAMAGE_DEALT] <= 0.0f &&
        state->npcs_remaining > 0 && p->attack_target_idx >= 0) {
        out.wasted_attack = params->shape_wasted_attack_penalty;
    }

    if (state->npcs_remaining > 0) {
        runtime->ticks_in_wave++;
        if (params->shape_wave_stall_base_penalty != 0.0f &&
            runtime->ticks_in_wave > params->shape_wave_stall_start) {
            int over = runtime->ticks_in_wave - params->shape_wave_stall_start;
            int ramps = 0;
            if (params->shape_wave_stall_ramp_interval > 0) {
                ramps = over / params->shape_wave_stall_ramp_interval;
            }
            out.wave_stall = params->shape_wave_stall_base_penalty *
                (1.0f + (float)ramps);
            out.wave_stall = fc_reward_cap_stall_penalty(
                out.wave_stall, params->shape_wave_stall_cap);
        }
    }
    if (out.raw[FC_RWD_WAVE_CLEAR] > 0.0f) {
        runtime->ticks_in_wave = 0;
    }

    out.invalid_action = out.raw[FC_RWD_INVALID_ACTION] * params->w_invalid_action;
    out.tick_penalty = out.raw[FC_RWD_TICK_PENALTY] * params->w_tick_penalty;

    if (out.raw[FC_RWD_ATTACK_ATTEMPT] > 0.0f) {
        runtime->ticks_since_attack = 0;
    } else if (state->npcs_remaining > 0 && p->attack_timer <= 0) {
        runtime->ticks_since_attack++;
        if (runtime->ticks_since_attack >= params->shape_not_attacking_grace_ticks) {
            out.not_attacking = params->shape_not_attacking_penalty;
        }
    } else if (state->npcs_remaining <= 0) {
        runtime->ticks_since_attack = 0;
    }

    if (out.raw[FC_RWD_DAMAGE_DEALT] > 0.0f && p->attack_target_idx >= 0) {
        const FcNpc* target = &state->npcs[p->attack_target_idx];
        if (target->active && !target->is_dead) {
            int dist = fc_distance_to_npc(p->x, p->y, target);
            if (dist >= params->shape_kiting_min_dist &&
                dist <= params->shape_kiting_max_dist) {
                out.kiting = params->shape_kiting_reward;
            }
        }
    }

    if (state->safespot_attack_this_tick) {
        out.safespot_attack = params->shape_safespot_attack_reward;
    }

    if (p->prayer != PRAYER_NONE && !out.threat_ctx.any_threat) {
        out.unnecessary_prayer = params->shape_unnecessary_prayer_penalty;
    }

    if (state->jad_heal_procs_this_tick > 0) {
        out.jad_heal_penalty =
            params->shape_jad_heal_penalty * (float)state->jad_heal_procs_this_tick;
    }

    if (out.raw[FC_RWD_WAVE_CLEAR] > 0.0f) {
        switch (state->current_wave) {
            case 60:
                out.late_wave_bonus += params->shape_reach_wave_60_bonus;
                break;
            case 61:
                out.late_wave_bonus += params->shape_reach_wave_61_bonus;
                break;
            case 62:
                out.late_wave_bonus += params->shape_reach_wave_62_bonus;
                break;
            case 63:
                out.late_wave_bonus += params->shape_reach_wave_63_bonus;
                break;
            default:
                break;
        }
    }

    if (out.raw[FC_RWD_JAD_KILL] > 0.0f) {
        out.jad_kill_bonus = params->shape_jad_kill_bonus;
    }

    out.total =
        out.damage_dealt +
        out.attack_attempt +
        out.damage_taken +
        out.npc_kill +
        out.wave_clear +
        out.jad_damage +
        out.jad_kill +
        out.player_death +
        out.food_waste +
        out.food_safe +
        out.pot_waste +
        out.pot_safe +
        out.correct_jad_prayer +
        out.correct_danger_prayer +
        out.wrong_danger_prayer_shape +
        out.npc_specific_prayer +
        out.melee_pressure +
        out.wasted_attack +
        out.wave_stall +
        out.not_attacking +
        out.kiting +
        out.safespot_attack +
        out.unnecessary_prayer +
        out.jad_heal_penalty +
        out.late_wave_bonus +
        out.jad_kill_bonus +
        out.invalid_action +
        out.tick_penalty;

    return out;
}

#endif /* FC_REWARD_H */
