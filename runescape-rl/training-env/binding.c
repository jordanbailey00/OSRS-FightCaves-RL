/*
 * binding.c — PufferLib 4.0 binding for Fight Caves environment.
 *
 * Defines the macros PufferLib needs, includes vecenv.h, and implements
 * the my_init/my_log hooks for config parsing and stat logging.
 */

#include "fight_caves.h"

#define OBS_SIZE FC_PUFFER_OBS_SIZE
#define OBS_TENSOR_T FloatTensor
#define NUM_ATNS FC_PUFFER_NUM_ATNS
#define ACT_SIZES {17, 9, 5, 3, 2}
#define OBS_TYPE FLOAT
#define ACT_TYPE DOUBLE

#define Env FightCaves
#include "vecenv.h"

void my_init(Env* env, Dict* kwargs) {
    env->num_agents = 1;  /* Fight Caves is single-agent */

    /* Reward shaping weights (from config/fight_caves.ini [env] section) */
    DictItem* item;
    item = dict_get_unsafe(kwargs, "w_damage_dealt");
    env->w_damage_dealt = item ? (float)item->value : 0.5f;
    item = dict_get_unsafe(kwargs, "w_attack_attempt");
    env->w_attack_attempt = item ? (float)item->value : 0.0f;
    item = dict_get_unsafe(kwargs, "w_damage_taken");
    env->w_damage_taken = item ? (float)item->value : -0.75f;
    item = dict_get_unsafe(kwargs, "w_npc_kill");
    env->w_npc_kill = item ? (float)item->value : 3.0f;
    item = dict_get_unsafe(kwargs, "w_wave_clear");
    env->w_wave_clear = item ? (float)item->value : 10.0f;
    item = dict_get_unsafe(kwargs, "w_jad_damage");
    env->w_jad_damage = item ? (float)item->value : 2.0f;
    item = dict_get_unsafe(kwargs, "w_jad_kill");
    env->w_jad_kill = item ? (float)item->value : 50.0f;
    item = dict_get_unsafe(kwargs, "w_player_death");
    env->w_player_death = item ? (float)item->value : -20.0f;
    item = dict_get_unsafe(kwargs, "w_cave_complete");
    env->w_cave_complete = item ? (float)item->value : 100.0f;
    item = dict_get_unsafe(kwargs, "w_food_used");
    env->w_food_used = item ? (float)item->value : -0.5f;
    item = dict_get_unsafe(kwargs, "w_food_used_well");
    env->w_food_used_well = item ? (float)item->value : 1.0f;
    item = dict_get_unsafe(kwargs, "w_prayer_pot_used");
    env->w_prayer_pot_used = item ? (float)item->value : -1.0f;
    item = dict_get_unsafe(kwargs, "w_correct_jad_prayer");
    env->w_correct_jad_prayer = item ? (float)item->value : 0.0f;
    item = dict_get_unsafe(kwargs, "w_wrong_jad_prayer");
    env->w_wrong_jad_prayer = item ? (float)item->value : 0.0f;
    item = dict_get_unsafe(kwargs, "w_correct_danger_prayer");
    env->w_correct_danger_prayer = item ? (float)item->value : 2.0f;
    item = dict_get_unsafe(kwargs, "w_wrong_danger_prayer");
    env->w_wrong_danger_prayer = item ? (float)item->value : 0.0f;
    item = dict_get_unsafe(kwargs, "w_invalid_action");
    env->w_invalid_action = item ? (float)item->value : -0.1f;
    item = dict_get_unsafe(kwargs, "w_movement");
    env->w_movement = item ? (float)item->value : 0.0f;
    item = dict_get_unsafe(kwargs, "w_idle");
    env->w_idle = item ? (float)item->value : 0.0f;
    item = dict_get_unsafe(kwargs, "w_tick_penalty");
    env->w_tick_penalty = item ? (float)item->value : -0.001f;

    /* Configurable shaping terms */
    item = dict_get_unsafe(kwargs, "shape_food_full_waste_penalty");
    env->shape_food_full_waste_penalty = item ? (float)item->value : -5.0f;
    item = dict_get_unsafe(kwargs, "shape_food_waste_scale");
    env->shape_food_waste_scale = item ? (float)item->value : -1.0f;
    item = dict_get_unsafe(kwargs, "shape_food_safe_hp_threshold");
    env->shape_food_safe_hp_threshold = item ? (float)item->value : 0.70f;
    item = dict_get_unsafe(kwargs, "shape_food_no_threat_penalty");
    env->shape_food_no_threat_penalty = item ? (float)item->value : -0.5f;
    item = dict_get_unsafe(kwargs, "shape_pot_full_waste_penalty");
    env->shape_pot_full_waste_penalty = item ? (float)item->value : -5.0f;
    item = dict_get_unsafe(kwargs, "shape_pot_waste_scale");
    env->shape_pot_waste_scale = item ? (float)item->value : -1.0f;
    item = dict_get_unsafe(kwargs, "shape_pot_safe_prayer_threshold");
    env->shape_pot_safe_prayer_threshold = item ? (float)item->value : 0.50f;
    item = dict_get_unsafe(kwargs, "shape_pot_no_threat_penalty");
    env->shape_pot_no_threat_penalty = item ? (float)item->value : -0.5f;
    item = dict_get_unsafe(kwargs, "shape_wrong_prayer_penalty");
    env->shape_wrong_prayer_penalty = item ? (float)item->value : -1.0f;
    item = dict_get_unsafe(kwargs, "shape_npc_specific_prayer_bonus");
    env->shape_npc_specific_prayer_bonus = item ? (float)item->value : 2.0f;
    item = dict_get_unsafe(kwargs, "shape_npc_melee_penalty");
    env->shape_npc_melee_penalty = item ? (float)item->value : -0.5f;
    item = dict_get_unsafe(kwargs, "shape_wasted_attack_penalty");
    env->shape_wasted_attack_penalty = item ? (float)item->value : -0.3f;
    item = dict_get_unsafe(kwargs, "shape_wave_stall_base_penalty");
    env->shape_wave_stall_base_penalty = item ? (float)item->value : -0.75f;
    item = dict_get_unsafe(kwargs, "shape_wave_stall_cap");
    env->shape_wave_stall_cap = item ? (float)item->value : -3.0f;
    item = dict_get_unsafe(kwargs, "shape_not_attacking_penalty");
    env->shape_not_attacking_penalty = item ? (float)item->value : -0.5f;
    item = dict_get_unsafe(kwargs, "shape_kiting_reward");
    env->shape_kiting_reward = item ? (float)item->value : 1.0f;
    item = dict_get_unsafe(kwargs, "shape_unnecessary_prayer_penalty");
    env->shape_unnecessary_prayer_penalty = item ? (float)item->value : -0.2f;
    item = dict_get_unsafe(kwargs, "shape_resource_threat_window");
    env->shape_resource_threat_window = item ? (int)item->value : 2;
    item = dict_get_unsafe(kwargs, "shape_kiting_min_dist");
    env->shape_kiting_min_dist = item ? (int)item->value : 5;
    item = dict_get_unsafe(kwargs, "shape_kiting_max_dist");
    env->shape_kiting_max_dist = item ? (int)item->value : 7;
    item = dict_get_unsafe(kwargs, "shape_wave_stall_start");
    env->shape_wave_stall_start = item ? (int)item->value : 500;
    item = dict_get_unsafe(kwargs, "shape_wave_stall_ramp_interval");
    env->shape_wave_stall_ramp_interval = item ? (int)item->value : 50;
    item = dict_get_unsafe(kwargs, "shape_not_attacking_grace_ticks");
    env->shape_not_attacking_grace_ticks = item ? (int)item->value : 2;

    /* Curriculum */
    item = dict_get_unsafe(kwargs, "curriculum_wave");
    env->curriculum_wave = item ? (int)item->value : 0;
    item = dict_get_unsafe(kwargs, "curriculum_pct");
    env->curriculum_pct = item ? (float)item->value : 0.0f;
    item = dict_get_unsafe(kwargs, "curriculum_wave_2");
    env->curriculum_wave_2 = item ? (int)item->value : 0;
    item = dict_get_unsafe(kwargs, "curriculum_pct_2");
    env->curriculum_pct_2 = item ? (float)item->value : 0.0f;
    item = dict_get_unsafe(kwargs, "curriculum_wave_3");
    env->curriculum_wave_3 = item ? (int)item->value : 0;
    item = dict_get_unsafe(kwargs, "curriculum_pct_3");
    env->curriculum_pct_3 = item ? (float)item->value : 0.0f;
    item = dict_get_unsafe(kwargs, "disable_movement");
    env->disable_movement = item ? (int)item->value : 0;

    /* Initialize game state */
    env->seed_counter = 0;
    fc_init(&env->state);
}

void my_log(Log* log, Dict* out) {
    dict_set(out, "score", log->score);
    dict_set(out, "episode_return", log->episode_return);
    dict_set(out, "episode_length", log->episode_length);
    dict_set(out, "wave_reached", log->wave_reached);
    /* Globals — bypass PufferLib Log struct, averaged here */
    extern float g_max_wave_ever;
    extern float g_most_npcs_ever;
    extern float g_sum_prayer_uptime, g_sum_prayer_uptime_melee;
    extern float g_sum_prayer_uptime_range, g_sum_prayer_uptime_magic;
    extern float g_sum_correct_blocks;
    extern float g_sum_wrong_prayer_hits, g_sum_no_prayer_hits;
    extern float g_sum_prayer_switches, g_sum_damage_blocked, g_sum_damage_taken;
    extern float g_sum_attack_when_ready;
    extern float g_sum_pots_used, g_sum_avg_prayer_on_pot;
    extern float g_sum_food_eaten, g_sum_avg_hp_on_food;
    extern float g_sum_food_wasted, g_sum_pots_wasted, g_n_analytics;
    extern float g_sum_tokxil_melee_ticks, g_sum_ketzek_melee_ticks;
    extern float g_sum_reached_wave_30, g_sum_cleared_wave_30, g_sum_reached_wave_31;
    dict_set(out, "max_wave", g_max_wave_ever);
    dict_set(out, "most_npcs_slayed", g_most_npcs_ever);
    if (g_n_analytics > 0) {
        dict_set(out, "prayer_uptime", g_sum_prayer_uptime / g_n_analytics);
        dict_set(out, "prayer_uptime_melee", g_sum_prayer_uptime_melee / g_n_analytics);
        dict_set(out, "prayer_uptime_range", g_sum_prayer_uptime_range / g_n_analytics);
        dict_set(out, "prayer_uptime_magic", g_sum_prayer_uptime_magic / g_n_analytics);
        dict_set(out, "correct_prayer", g_sum_correct_blocks / g_n_analytics);
        dict_set(out, "wrong_prayer_hits", g_sum_wrong_prayer_hits / g_n_analytics);
        dict_set(out, "no_prayer_hits", g_sum_no_prayer_hits / g_n_analytics);
        dict_set(out, "prayer_switches", g_sum_prayer_switches / g_n_analytics);
        dict_set(out, "damage_blocked", g_sum_damage_blocked / g_n_analytics);
        dict_set(out, "dmg_taken_avg", g_sum_damage_taken / g_n_analytics);
        dict_set(out, "attack_when_ready_rate", g_sum_attack_when_ready / g_n_analytics);
        dict_set(out, "pots_used", g_sum_pots_used / g_n_analytics);
        dict_set(out, "avg_prayer_on_pot", g_sum_avg_prayer_on_pot / g_n_analytics);
        dict_set(out, "food_eaten", g_sum_food_eaten / g_n_analytics);
        dict_set(out, "avg_hp_on_food", g_sum_avg_hp_on_food / g_n_analytics);
        dict_set(out, "food_wasted", g_sum_food_wasted / g_n_analytics);
        dict_set(out, "pots_wasted", g_sum_pots_wasted / g_n_analytics);
        dict_set(out, "tokxil_melee_ticks", g_sum_tokxil_melee_ticks / g_n_analytics);
        dict_set(out, "ketzek_melee_ticks", g_sum_ketzek_melee_ticks / g_n_analytics);
        dict_set(out, "reached_wave_30", g_sum_reached_wave_30 / g_n_analytics);
        dict_set(out, "cleared_wave_30", g_sum_cleared_wave_30 / g_n_analytics);
        dict_set(out, "reached_wave_31", g_sum_reached_wave_31 / g_n_analytics);
        g_sum_prayer_uptime = 0; g_sum_prayer_uptime_melee = 0;
        g_sum_prayer_uptime_range = 0; g_sum_prayer_uptime_magic = 0;
        g_sum_correct_blocks = 0;
        g_sum_wrong_prayer_hits = 0; g_sum_no_prayer_hits = 0;
        g_sum_prayer_switches = 0; g_sum_damage_blocked = 0;
        g_sum_damage_taken = 0; g_sum_attack_when_ready = 0;
        g_sum_pots_used = 0;
        g_sum_avg_prayer_on_pot = 0; g_sum_food_eaten = 0;
        g_sum_avg_hp_on_food = 0; g_sum_food_wasted = 0;
        g_sum_pots_wasted = 0; g_sum_tokxil_melee_ticks = 0;
        g_sum_ketzek_melee_ticks = 0; g_sum_reached_wave_30 = 0;
        g_sum_cleared_wave_30 = 0; g_sum_reached_wave_31 = 0;
        g_n_analytics = 0;
    }
}
