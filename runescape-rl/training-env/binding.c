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
    item = dict_get_unsafe(kwargs, "w_damage_taken");
    env->w_damage_taken = item ? (float)item->value : -0.5f;
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
    env->w_food_used = item ? (float)item->value : -0.05f;
    item = dict_get_unsafe(kwargs, "w_prayer_pot_used");
    env->w_prayer_pot_used = item ? (float)item->value : -0.05f;
    item = dict_get_unsafe(kwargs, "w_correct_jad_prayer");
    env->w_correct_jad_prayer = item ? (float)item->value : 5.0f;
    item = dict_get_unsafe(kwargs, "w_wrong_jad_prayer");
    env->w_wrong_jad_prayer = item ? (float)item->value : -10.0f;
    item = dict_get_unsafe(kwargs, "w_correct_danger_prayer");
    env->w_correct_danger_prayer = item ? (float)item->value : 2.0f;
    item = dict_get_unsafe(kwargs, "w_wrong_danger_prayer");
    env->w_wrong_danger_prayer = item ? (float)item->value : -3.0f;
    item = dict_get_unsafe(kwargs, "w_invalid_action");
    env->w_invalid_action = item ? (float)item->value : -0.1f;
    item = dict_get_unsafe(kwargs, "w_movement");
    env->w_movement = item ? (float)item->value : 0.0f;
    item = dict_get_unsafe(kwargs, "w_idle");
    env->w_idle = item ? (float)item->value : -0.01f;
    item = dict_get_unsafe(kwargs, "w_tick_penalty");
    env->w_tick_penalty = item ? (float)item->value : -0.005f;

    /* Curriculum */
    item = dict_get_unsafe(kwargs, "curriculum_wave");
    env->curriculum_wave = item ? (int)item->value : 0;
    item = dict_get_unsafe(kwargs, "curriculum_pct");
    env->curriculum_pct = item ? (float)item->value : 0.0f;

    /* Initialize game state */
    env->seed_counter = 0;
    fc_init(&env->state);
}

void my_log(Log* log, Dict* out) {
    dict_set(out, "score", log->score);
    dict_set(out, "episode_return", log->episode_return);
    dict_set(out, "episode_length", log->episode_length);
    dict_set(out, "wave_reached", log->wave_reached);
}
