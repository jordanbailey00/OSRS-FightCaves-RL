#ifndef FIGHT_CAVES_NATIVE_RUNTIME_H
#define FIGHT_CAVES_NATIVE_RUNTIME_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define FC_NATIVE_ABI_VERSION 1
#define FC_NATIVE_MAX_VISIBLE_TARGETS 8

#define FC_NATIVE_SCENE_RECT_ARENA_BOUNDS 1
#define FC_NATIVE_SCENE_RECT_SPAWN_NORTH_WEST 2
#define FC_NATIVE_SCENE_RECT_SPAWN_SOUTH_WEST 3
#define FC_NATIVE_SCENE_RECT_SPAWN_SOUTH 4
#define FC_NATIVE_SCENE_RECT_SPAWN_SOUTH_EAST 5
#define FC_NATIVE_SCENE_RECT_PLAYER_DAIS 6

typedef struct fc_episode_config {
    int64_t seed;
    int start_wave;
    int ammo;
    int prayer_potions;
    int sharks;
} fc_episode_config;

typedef struct fc_slot_debug_state {
    int tile_x;
    int tile_y;
    int tile_level;
    int hitpoints_current;
    int prayer_current;
    int run_energy;
    int running;
    int protect_from_magic;
    int protect_from_missiles;
    int protect_from_melee;
    int attack_locked;
    int food_locked;
    int drink_locked;
    int combo_locked;
    int busy_locked;
    int sharks;
    int prayer_potion_dose_count;
    int ammo;
} fc_slot_debug_state;

typedef struct fc_visible_target {
    int listed;
    int currently_visible;
    int npc_index;
    int id_code;
    int tile_x;
    int tile_y;
    int tile_level;
    int hitpoints_current;
    int hitpoints_max;
    int hidden;
    int dead;
    int under_attack;
    int jad_telegraph_state;
} fc_visible_target;

typedef struct fc_scene_rect {
    int kind_code;
    int min_tile_x;
    int max_tile_x;
    int min_tile_y;
    int max_tile_y;
    int tile_level;
} fc_scene_rect;

typedef struct fc_runtime_slot_snapshot {
    int slot_index;
    int initialized;
    int64_t seed;
    int wave;
    int rotation;
    int remaining;
    int tick;
    int steps;
    int tile_x;
    int tile_y;
    int tile_level;
    int hitpoints_current;
    int hitpoints_max;
    int prayer_current;
    int prayer_max;
    int run_energy;
    int run_energy_max;
    int running;
    int protect_from_magic;
    int protect_from_missiles;
    int protect_from_melee;
    int attack_locked;
    int food_locked;
    int drink_locked;
    int combo_locked;
    int busy_locked;
    int sharks;
    int prayer_potion_dose_count;
    int ammo;
    int tick_cap;
    int force_invalid_state;
    int slot_mode;
    int core_trace_id;
    int core_trace_record_index;
    int terminal_code;
    int terminated;
    int truncated;
    int jad_hit_resolve_outcome_code;
    int visible_target_count;
    fc_visible_target visible_targets[FC_NATIVE_MAX_VISIBLE_TARGETS];
} fc_runtime_slot_snapshot;

typedef struct fc_native_runtime fc_native_runtime;

int fc_native_abi_version(void);
const char* fc_native_runtime_version_string(void);
fc_native_runtime* fc_native_runtime_open(void);
void fc_native_runtime_close(fc_native_runtime* runtime);
const char* fc_native_runtime_last_error(void);
int fc_native_runtime_descriptor_count(const fc_native_runtime* runtime);
const char* fc_native_runtime_descriptor_bundle_json(const fc_native_runtime* runtime);
size_t fc_native_runtime_descriptor_bundle_json_size(const fc_native_runtime* runtime);
int fc_native_runtime_scene_rect_count(void);
const fc_scene_rect* fc_native_runtime_scene_rects(void);
int fc_native_runtime_reset_batch(
    fc_native_runtime* runtime,
    const fc_episode_config* configs,
    int env_count
);
int fc_native_runtime_last_reset_env_count(const fc_native_runtime* runtime);
const int64_t* fc_native_runtime_last_reset_episode_seeds(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_reset_waves(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_reset_rotations(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_reset_remaining(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_reset_ammo(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_reset_prayer_potion_doses(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_reset_sharks(const fc_native_runtime* runtime);
const float* fc_native_runtime_last_reset_flat_observations(const fc_native_runtime* runtime);
const float* fc_native_runtime_last_reset_reward_features(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_reset_terminal_codes(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_reset_terminated(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_reset_truncated(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_reset_episode_ticks(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_reset_episode_steps(const fc_native_runtime* runtime);
int fc_native_runtime_debug_configure_slot_state(
    fc_native_runtime* runtime,
    int slot_index,
    const fc_slot_debug_state* state
);
int fc_native_runtime_debug_set_slot_visible_targets(
    fc_native_runtime* runtime,
    int slot_index,
    const fc_visible_target* targets,
    int target_count
);
int fc_native_runtime_debug_set_slot_control(
    fc_native_runtime* runtime,
    int slot_index,
    int tick_cap,
    int force_invalid_state
);
int fc_native_runtime_debug_load_core_trace(
    fc_native_runtime* runtime,
    int slot_index,
    int trace_id
);
int fc_native_runtime_debug_snapshot_slots(
    fc_native_runtime* runtime,
    const int* slot_indices,
    int env_count
);
int fc_native_runtime_export_slot_snapshot(
    const fc_native_runtime* runtime,
    int slot_index,
    fc_runtime_slot_snapshot* out
);
int fc_native_runtime_step_batch(
    fc_native_runtime* runtime,
    const int* slot_indices,
    const int32_t* packed_actions,
    int env_count
);
int fc_native_runtime_last_step_env_count(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_step_slot_indices(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_step_action_ids(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_step_action_accepted(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_step_rejection_codes(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_step_resolved_target_npc_indices(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_step_jad_telegraph_states(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_step_jad_hit_resolve_outcome_codes(const fc_native_runtime* runtime);
const float* fc_native_runtime_last_step_flat_observations(const fc_native_runtime* runtime);
const float* fc_native_runtime_last_step_reward_features(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_step_terminal_codes(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_step_terminated(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_step_truncated(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_step_episode_ticks(const fc_native_runtime* runtime);
const int* fc_native_runtime_last_step_episode_steps(const fc_native_runtime* runtime);

#ifdef __cplusplus
}
#endif

#endif
