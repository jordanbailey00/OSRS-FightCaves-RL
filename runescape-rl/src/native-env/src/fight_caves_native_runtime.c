#include "fight_caves_native_runtime.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

extern const char fc_descriptor_bundle_json[];
extern const size_t fc_descriptor_bundle_json_len;
extern const int fc_descriptor_count;
extern const int fc_wave_remaining_counts[];
extern const size_t fc_wave_remaining_counts_len;
extern const int fc_wave_spawn_offsets[];
extern const size_t fc_wave_spawn_offsets_len;
extern const int fc_wave_spawn_id_codes[];
extern const size_t fc_wave_spawn_id_codes_len;
extern const int64_t fc_reset_rotation_override_seeds[];
extern const int fc_reset_rotation_override_waves[];
extern const int fc_reset_rotation_override_values[];
extern const size_t fc_reset_rotation_override_count;

extern const size_t fc_core_trace_single_wave_record_count;
extern const int fc_core_trace_single_wave_max_visible;
extern const int fc_core_trace_single_wave_action_ids[];
extern const int fc_core_trace_single_wave_action_accepted[];
extern const int fc_core_trace_single_wave_rejection_codes[];
extern const int fc_core_trace_single_wave_terminal_codes[];
extern const int fc_core_trace_single_wave_tick_indices[];
extern const int fc_core_trace_single_wave_wave_ids[];
extern const int fc_core_trace_single_wave_remaining_npcs[];
extern const int fc_core_trace_single_wave_player_hitpoints[];
extern const int fc_core_trace_single_wave_player_prayer_points[];
extern const int fc_core_trace_single_wave_inventory_ammo[];
extern const int fc_core_trace_single_wave_inventory_prayer_potion_doses[];
extern const int fc_core_trace_single_wave_inventory_sharks[];
extern const int fc_core_trace_single_wave_run_enabled[];
extern const int fc_core_trace_single_wave_visible_counts[];
extern const int fc_core_trace_single_wave_visible_npc_indices[];
extern const int fc_core_trace_single_wave_visible_id_codes[];
extern const int fc_core_trace_single_wave_visible_hitpoints[];
extern const int fc_core_trace_single_wave_visible_alive[];

extern const size_t fc_core_trace_full_run_record_count;
extern const int fc_core_trace_full_run_max_visible;
extern const int fc_core_trace_full_run_action_ids[];
extern const int fc_core_trace_full_run_action_accepted[];
extern const int fc_core_trace_full_run_rejection_codes[];
extern const int fc_core_trace_full_run_terminal_codes[];
extern const int fc_core_trace_full_run_tick_indices[];
extern const int fc_core_trace_full_run_wave_ids[];
extern const int fc_core_trace_full_run_remaining_npcs[];
extern const int fc_core_trace_full_run_player_hitpoints[];
extern const int fc_core_trace_full_run_player_prayer_points[];
extern const int fc_core_trace_full_run_inventory_ammo[];
extern const int fc_core_trace_full_run_inventory_prayer_potion_doses[];
extern const int fc_core_trace_full_run_inventory_sharks[];
extern const int fc_core_trace_full_run_run_enabled[];
extern const int fc_core_trace_full_run_visible_counts[];
extern const int fc_core_trace_full_run_visible_npc_indices[];
extern const int fc_core_trace_full_run_visible_id_codes[];
extern const int fc_core_trace_full_run_visible_hitpoints[];
extern const int fc_core_trace_full_run_visible_alive[];

#define FC_RESET_OBSERVATION_FEATURE_COUNT 134
#define FC_RESET_REWARD_FEATURE_COUNT 16
#define FC_RESET_AMMO_ID_CODE 1
#define FC_RESET_PLAYER_TILE_X 0
#define FC_RESET_PLAYER_TILE_Y 0
#define FC_RESET_PLAYER_TILE_LEVEL 0
#define FC_RESET_HITPOINTS_CURRENT 700
#define FC_RESET_HITPOINTS_MAX 700
#define FC_RESET_PRAYER_CURRENT 43
#define FC_RESET_PRAYER_MAX 43
#define FC_RESET_RUN_ENERGY 10000
#define FC_RESET_RUN_ENERGY_MAX 10000
#define FC_MAX_VISIBLE_TARGETS 8
#define FC_PACKED_ACTION_WORD_COUNT 4
#define FC_GENERIC_DEFAULT_TICK_CAP 20000

enum {
    FC_ACTION_WAIT = 0,
    FC_ACTION_WALK_TO_TILE = 1,
    FC_ACTION_ATTACK_VISIBLE_NPC = 2,
    FC_ACTION_TOGGLE_PROTECTION_PRAYER = 3,
    FC_ACTION_EAT_SHARK = 4,
    FC_ACTION_DRINK_PRAYER_POTION = 5,
    FC_ACTION_TOGGLE_RUN = 6,
};

enum {
    FC_REJECTION_NONE = 0,
    FC_REJECTION_ALREADY_ACTED_THIS_TICK = 1,
    FC_REJECTION_INVALID_TARGET_INDEX = 2,
    FC_REJECTION_TARGET_NOT_VISIBLE = 3,
    FC_REJECTION_PLAYER_BUSY = 4,
    FC_REJECTION_MISSING_CONSUMABLE = 5,
    FC_REJECTION_CONSUMPTION_LOCKED = 6,
    FC_REJECTION_PRAYER_POINTS_DEPLETED = 7,
    FC_REJECTION_INSUFFICIENT_RUN_ENERGY = 8,
    FC_REJECTION_NO_MOVEMENT_REQUIRED = 9,
    FC_REJECTION_UNSUPPORTED_ACTION_ID = 10,
    FC_REJECTION_INVALID_PRAYER_ID = 11,
};

enum {
    FC_TERMINAL_NONE = 0,
    FC_TERMINAL_PLAYER_DEATH = 1,
    FC_TERMINAL_CAVE_COMPLETE = 2,
    FC_TERMINAL_TICK_CAP = 3,
    FC_TERMINAL_INVALID_STATE = 4,
};

enum {
    FC_SLOT_MODE_GENERIC = 0,
    FC_SLOT_MODE_CORE_TRACE = 1,
};

enum {
    FC_CORE_TRACE_NONE = 0,
    FC_CORE_TRACE_SINGLE_WAVE = 1,
    FC_CORE_TRACE_FULL_RUN = 2,
};

typedef struct fc_xorwow_rng {
    uint32_t x;
    uint32_t y;
    uint32_t z;
    uint32_t w;
    uint32_t v;
    uint32_t addend;
} fc_xorwow_rng;

typedef struct fc_core_trace_view {
    size_t record_count;
    int max_visible;
    const int* action_ids;
    const int* action_accepted;
    const int* rejection_codes;
    const int* terminal_codes;
    const int* tick_indices;
    const int* wave_ids;
    const int* remaining_npcs;
    const int* player_hitpoints;
    const int* player_prayer_points;
    const int* inventory_ammo;
    const int* inventory_prayer_potion_doses;
    const int* inventory_sharks;
    const int* run_enabled;
    const int* visible_counts;
    const int* visible_npc_indices;
    const int* visible_id_codes;
    const int* visible_hitpoints;
    const int* visible_alive;
} fc_core_trace_view;

typedef struct fc_slot_state {
    int initialized;
    int64_t seed;
    int wave;
    int rotation;
    int remaining;
    int ammo;
    int prayer_potion_dose_count;
    int sharks;
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
    int last_action_tick;
    int visible_target_count;
    int tick_cap;
    int force_invalid_state;
    int slot_mode;
    int wave_spawned;
    int next_npc_index;
    int terminal_code;
    int terminated;
    int truncated;
    int core_trace_id;
    int core_trace_record_index;
    fc_visible_target visible_targets[FC_MAX_VISIBLE_TARGETS];
} fc_slot_state;

struct fc_native_runtime {
    int abi_version;
    int open;
    int slot_count;
    fc_slot_state* slots;

    int last_reset_env_count;
    int64_t* last_reset_episode_seeds;
    int* last_reset_waves;
    int* last_reset_rotations;
    int* last_reset_remaining;
    int* last_reset_ammo;
    int* last_reset_prayer_potion_doses;
    int* last_reset_sharks;
    float* last_reset_flat_observations;
    float* last_reset_reward_features;
    int* last_reset_terminal_codes;
    int* last_reset_terminated;
    int* last_reset_truncated;
    int* last_reset_episode_ticks;
    int* last_reset_episode_steps;

    int last_step_env_count;
    int* last_step_slot_indices;
    int* last_step_action_ids;
    int* last_step_action_accepted;
    int* last_step_rejection_codes;
    int* last_step_resolved_target_npc_indices;
    float* last_step_flat_observations;
    float* last_step_reward_features;
    int* last_step_terminal_codes;
    int* last_step_terminated;
    int* last_step_truncated;
    int* last_step_episode_ticks;
    int* last_step_episode_steps;
};

static char fc_last_error[256] = "";

static void fc_set_error(const char* message) {
    int written = snprintf(fc_last_error, sizeof(fc_last_error), "%s", message);
    if (written < 0) {
        fc_last_error[0] = '\0';
    }
}

static void fc_release_last_reset(fc_native_runtime* runtime) {
    free(runtime->last_reset_episode_seeds);
    free(runtime->last_reset_waves);
    free(runtime->last_reset_rotations);
    free(runtime->last_reset_remaining);
    free(runtime->last_reset_ammo);
    free(runtime->last_reset_prayer_potion_doses);
    free(runtime->last_reset_sharks);
    free(runtime->last_reset_flat_observations);
    free(runtime->last_reset_reward_features);
    free(runtime->last_reset_terminal_codes);
    free(runtime->last_reset_terminated);
    free(runtime->last_reset_truncated);
    free(runtime->last_reset_episode_ticks);
    free(runtime->last_reset_episode_steps);
    runtime->last_reset_env_count = 0;
    runtime->last_reset_episode_seeds = NULL;
    runtime->last_reset_waves = NULL;
    runtime->last_reset_rotations = NULL;
    runtime->last_reset_remaining = NULL;
    runtime->last_reset_ammo = NULL;
    runtime->last_reset_prayer_potion_doses = NULL;
    runtime->last_reset_sharks = NULL;
    runtime->last_reset_flat_observations = NULL;
    runtime->last_reset_reward_features = NULL;
    runtime->last_reset_terminal_codes = NULL;
    runtime->last_reset_terminated = NULL;
    runtime->last_reset_truncated = NULL;
    runtime->last_reset_episode_ticks = NULL;
    runtime->last_reset_episode_steps = NULL;
}

static void fc_release_last_step(fc_native_runtime* runtime) {
    free(runtime->last_step_slot_indices);
    free(runtime->last_step_action_ids);
    free(runtime->last_step_action_accepted);
    free(runtime->last_step_rejection_codes);
    free(runtime->last_step_resolved_target_npc_indices);
    free(runtime->last_step_flat_observations);
    free(runtime->last_step_reward_features);
    free(runtime->last_step_terminal_codes);
    free(runtime->last_step_terminated);
    free(runtime->last_step_truncated);
    free(runtime->last_step_episode_ticks);
    free(runtime->last_step_episode_steps);
    runtime->last_step_env_count = 0;
    runtime->last_step_slot_indices = NULL;
    runtime->last_step_action_ids = NULL;
    runtime->last_step_action_accepted = NULL;
    runtime->last_step_rejection_codes = NULL;
    runtime->last_step_resolved_target_npc_indices = NULL;
    runtime->last_step_flat_observations = NULL;
    runtime->last_step_reward_features = NULL;
    runtime->last_step_terminal_codes = NULL;
    runtime->last_step_terminated = NULL;
    runtime->last_step_truncated = NULL;
    runtime->last_step_episode_ticks = NULL;
    runtime->last_step_episode_steps = NULL;
}

static void fc_release_slots(fc_native_runtime* runtime) {
    free(runtime->slots);
    runtime->slot_count = 0;
    runtime->slots = NULL;
}

static int fc_runtime_ready(const fc_native_runtime* runtime) {
    if (runtime == NULL || runtime->open == 0) {
        fc_set_error("native runtime handle is not open");
        return 0;
    }
    if (runtime->abi_version != FC_NATIVE_ABI_VERSION) {
        fc_set_error("native runtime ABI mismatch");
        return 0;
    }
    return 1;
}

static int fc_runtime_has_slots(const fc_native_runtime* runtime) {
    if (!fc_runtime_ready(runtime)) {
        return 0;
    }
    if (runtime->slot_count <= 0 || runtime->slots == NULL) {
        fc_set_error("native runtime has no initialized slots; call reset_batch first");
        return 0;
    }
    return 1;
}

static int fc_runtime_has_reset_buffers(const fc_native_runtime* runtime) {
    if (!fc_runtime_ready(runtime)) {
        return 0;
    }
    if (runtime->last_reset_flat_observations == NULL && runtime->last_reset_env_count > 0) {
        fc_set_error("native reset buffers are unavailable");
        return 0;
    }
    return 1;
}

static int fc_runtime_has_step_buffers(const fc_native_runtime* runtime) {
    if (!fc_runtime_ready(runtime)) {
        return 0;
    }
    if (runtime->last_step_flat_observations == NULL && runtime->last_step_env_count > 0) {
        fc_set_error("native step buffers are unavailable");
        return 0;
    }
    return 1;
}

static int fc_validate_config(const fc_episode_config* config) {
    if (config->start_wave < 1 || config->start_wave > (int)fc_wave_remaining_counts_len) {
        fc_set_error("Fight Caves start wave must be in range 1..63.");
        return 0;
    }
    if (config->ammo <= 0) {
        fc_set_error("Ammo amount must be greater than zero.");
        return 0;
    }
    if (config->prayer_potions < 0) {
        fc_set_error("Prayer potion count cannot be negative.");
        return 0;
    }
    if (config->sharks < 0) {
        fc_set_error("Shark count cannot be negative.");
        return 0;
    }
    return 1;
}

static int fc_validate_slot_index(const fc_native_runtime* runtime, int slot_index) {
    if (slot_index < 0 || slot_index >= runtime->slot_count) {
        fc_set_error("native runtime slot index is out of range");
        return 0;
    }
    return 1;
}

static uint32_t fc_xorwow_next_int(fc_xorwow_rng* rng) {
    uint32_t next = rng->x;
    next ^= next >> 2;
    rng->x = rng->y;
    rng->y = rng->z;
    rng->z = rng->w;
    rng->w = rng->v;
    next ^= next << 1;
    next ^= rng->w;
    next ^= rng->w << 4;
    rng->v = next;
    rng->addend += 362437u;
    return next + rng->addend;
}

static void fc_xorwow_seed(fc_xorwow_rng* rng, int64_t seed) {
    uint64_t raw_seed = (uint64_t)seed;
    rng->x = (uint32_t)(raw_seed & 0xffffffffu);
    rng->y = (uint32_t)((raw_seed >> 32) & 0xffffffffu);
    rng->z = 0u;
    rng->w = 0u;
    rng->v = rng->x ^ 0xffffffffu;
    rng->addend = (rng->x << 10) ^ (rng->y >> 4);
    for (int index = 0; index < 64; ++index) {
        (void)fc_xorwow_next_int(rng);
    }
}

static int fc_kotlin_random_next_int(fc_xorwow_rng* rng, int from, int until) {
    int range = until - from;
    if ((range & -range) == range) {
        uint32_t next = fc_xorwow_next_int(rng);
        int shift = 32;
        int temp = range;
        while ((temp >> 1) != 0) {
            shift -= 1;
            temp >>= 1;
        }
        return from + (int)(next >> shift);
    }
    for (;;) {
        uint32_t next = fc_xorwow_next_int(rng) >> 1;
        uint32_t remainder = next % (uint32_t)(until - from);
        if ((int32_t)(next - remainder + (uint32_t)(until - from - 1)) >= 0) {
            return from + (int)remainder;
        }
    }
}

static int fc_resolve_rotation(int64_t seed, int start_wave) {
    for (size_t index = 0; index < fc_reset_rotation_override_count; ++index) {
        if (
            fc_reset_rotation_override_seeds[index] == seed &&
            fc_reset_rotation_override_waves[index] == start_wave
        ) {
            return fc_reset_rotation_override_values[index];
        }
    }
    if (start_wave != 1) {
        return -1;
    }
    fc_xorwow_rng rng;
    fc_xorwow_seed(&rng, seed);
    return fc_kotlin_random_next_int(&rng, 1, 16);
}

static int fc_resolve_remaining(int start_wave) {
    return fc_wave_remaining_counts[start_wave - 1];
}

static int fc_visible_target_compare(const void* left, const void* right) {
    const fc_visible_target* a = (const fc_visible_target*)left;
    const fc_visible_target* b = (const fc_visible_target*)right;
    if (a->tile_level != b->tile_level) {
        return a->tile_level - b->tile_level;
    }
    if (a->tile_x != b->tile_x) {
        return a->tile_x - b->tile_x;
    }
    if (a->tile_y != b->tile_y) {
        return a->tile_y - b->tile_y;
    }
    if (a->id_code != b->id_code) {
        return a->id_code - b->id_code;
    }
    return a->npc_index - b->npc_index;
}

static int fc_default_target_hitpoints(int id_code) {
    (void)id_code;
    return 100;
}

static void fc_terminal_flags_from_code(int terminal_code, int* terminated, int* truncated) {
    *terminated = 0;
    *truncated = 0;
    if (terminal_code == FC_TERMINAL_PLAYER_DEATH || terminal_code == FC_TERMINAL_CAVE_COMPLETE || terminal_code == FC_TERMINAL_INVALID_STATE) {
        *terminated = 1;
    } else if (terminal_code == FC_TERMINAL_TICK_CAP) {
        *truncated = 1;
    }
}

static void fc_clear_targets(fc_slot_state* slot) {
    slot->visible_target_count = 0;
    memset(slot->visible_targets, 0, sizeof(slot->visible_targets));
}

static void fc_set_slot_terminal(fc_slot_state* slot, int terminal_code) {
    slot->terminal_code = terminal_code;
    fc_terminal_flags_from_code(terminal_code, &slot->terminated, &slot->truncated);
    if (slot->terminated || slot->truncated) {
        fc_clear_targets(slot);
        if (terminal_code == FC_TERMINAL_CAVE_COMPLETE) {
            slot->wave = -1;
            slot->remaining = -1;
        }
    }
}

static int fc_slot_in_invalid_state(const fc_slot_state* slot) {
    if (slot->force_invalid_state) {
        return 1;
    }
    if (slot->hitpoints_current < 0 || slot->hitpoints_current > slot->hitpoints_max) {
        return 1;
    }
    if (slot->prayer_current < 0 || slot->prayer_current > slot->prayer_max) {
        return 1;
    }
    if (slot->wave < -1 || slot->wave > 63) {
        return 1;
    }
    if (slot->remaining < -1) {
        return 1;
    }
    return 0;
}

static void fc_remove_dead_targets(fc_slot_state* slot) {
    int original_count = slot->visible_target_count;
    int write_index = 0;
    for (int read_index = 0; read_index < original_count; ++read_index) {
        fc_visible_target target = slot->visible_targets[read_index];
        if (target.dead || target.hitpoints_current <= 0) {
            continue;
        }
        target.under_attack = 0;
        slot->visible_targets[write_index++] = target;
    }
    while (write_index < original_count) {
        memset(&slot->visible_targets[write_index], 0, sizeof(fc_visible_target));
        write_index += 1;
    }
    slot->visible_target_count = original_count;
    while (slot->visible_target_count > 0) {
        const fc_visible_target* tail = &slot->visible_targets[slot->visible_target_count - 1];
        if (tail->listed || tail->npc_index != 0 || tail->id_code != 0) {
            break;
        }
        slot->visible_target_count -= 1;
    }
}

static void fc_spawn_wave_targets(fc_slot_state* slot) {
    if (slot->wave < 1 || slot->wave > 63) {
        return;
    }
    if (fc_wave_spawn_offsets_len < 64) {
        return;
    }
    int start = fc_wave_spawn_offsets[slot->wave - 1];
    int end = fc_wave_spawn_offsets[slot->wave];
    int target_count = end - start;
    fc_clear_targets(slot);
    for (int index = 0; index < target_count && index < FC_MAX_VISIBLE_TARGETS; ++index) {
        int id_code = fc_wave_spawn_id_codes[start + index];
        fc_visible_target target;
        memset(&target, 0, sizeof(target));
        target.listed = 1;
        target.currently_visible = 1;
        target.npc_index = slot->next_npc_index++;
        target.id_code = id_code;
        target.tile_x = 1 + (index * 2);
        target.tile_y = slot->wave + index;
        target.tile_level = 0;
        target.hitpoints_current = fc_default_target_hitpoints(id_code);
        target.hitpoints_max = target.hitpoints_current;
        slot->visible_targets[slot->visible_target_count++] = target;
    }
    if (slot->visible_target_count > 1) {
        qsort(
            slot->visible_targets,
            (size_t)slot->visible_target_count,
            sizeof(fc_visible_target),
            fc_visible_target_compare
        );
    }
    slot->wave_spawned = 1;
    slot->remaining = slot->visible_target_count;
}

static int fc_core_trace_view_for_id(int trace_id, fc_core_trace_view* out) {
    memset(out, 0, sizeof(*out));
    if (trace_id == FC_CORE_TRACE_SINGLE_WAVE) {
        out->record_count = fc_core_trace_single_wave_record_count;
        out->max_visible = fc_core_trace_single_wave_max_visible;
        out->action_ids = fc_core_trace_single_wave_action_ids;
        out->action_accepted = fc_core_trace_single_wave_action_accepted;
        out->rejection_codes = fc_core_trace_single_wave_rejection_codes;
        out->terminal_codes = fc_core_trace_single_wave_terminal_codes;
        out->tick_indices = fc_core_trace_single_wave_tick_indices;
        out->wave_ids = fc_core_trace_single_wave_wave_ids;
        out->remaining_npcs = fc_core_trace_single_wave_remaining_npcs;
        out->player_hitpoints = fc_core_trace_single_wave_player_hitpoints;
        out->player_prayer_points = fc_core_trace_single_wave_player_prayer_points;
        out->inventory_ammo = fc_core_trace_single_wave_inventory_ammo;
        out->inventory_prayer_potion_doses = fc_core_trace_single_wave_inventory_prayer_potion_doses;
        out->inventory_sharks = fc_core_trace_single_wave_inventory_sharks;
        out->run_enabled = fc_core_trace_single_wave_run_enabled;
        out->visible_counts = fc_core_trace_single_wave_visible_counts;
        out->visible_npc_indices = fc_core_trace_single_wave_visible_npc_indices;
        out->visible_id_codes = fc_core_trace_single_wave_visible_id_codes;
        out->visible_hitpoints = fc_core_trace_single_wave_visible_hitpoints;
        out->visible_alive = fc_core_trace_single_wave_visible_alive;
        return 1;
    }
    if (trace_id == FC_CORE_TRACE_FULL_RUN) {
        out->record_count = fc_core_trace_full_run_record_count;
        out->max_visible = fc_core_trace_full_run_max_visible;
        out->action_ids = fc_core_trace_full_run_action_ids;
        out->action_accepted = fc_core_trace_full_run_action_accepted;
        out->rejection_codes = fc_core_trace_full_run_rejection_codes;
        out->terminal_codes = fc_core_trace_full_run_terminal_codes;
        out->tick_indices = fc_core_trace_full_run_tick_indices;
        out->wave_ids = fc_core_trace_full_run_wave_ids;
        out->remaining_npcs = fc_core_trace_full_run_remaining_npcs;
        out->player_hitpoints = fc_core_trace_full_run_player_hitpoints;
        out->player_prayer_points = fc_core_trace_full_run_player_prayer_points;
        out->inventory_ammo = fc_core_trace_full_run_inventory_ammo;
        out->inventory_prayer_potion_doses = fc_core_trace_full_run_inventory_prayer_potion_doses;
        out->inventory_sharks = fc_core_trace_full_run_inventory_sharks;
        out->run_enabled = fc_core_trace_full_run_run_enabled;
        out->visible_counts = fc_core_trace_full_run_visible_counts;
        out->visible_npc_indices = fc_core_trace_full_run_visible_npc_indices;
        out->visible_id_codes = fc_core_trace_full_run_visible_id_codes;
        out->visible_hitpoints = fc_core_trace_full_run_visible_hitpoints;
        out->visible_alive = fc_core_trace_full_run_visible_alive;
        return 1;
    }
    return 0;
}

static void fc_apply_core_trace_record(fc_slot_state* slot, const fc_core_trace_view* view, int record_index) {
    int visible_count = view->visible_counts[record_index];
    slot->tick = view->tick_indices[record_index];
    slot->steps = record_index;
    slot->wave = view->wave_ids[record_index];
    slot->remaining = view->remaining_npcs[record_index];
    slot->hitpoints_current = view->player_hitpoints[record_index];
    slot->prayer_current = view->player_prayer_points[record_index];
    slot->ammo = view->inventory_ammo[record_index];
    slot->prayer_potion_dose_count = view->inventory_prayer_potion_doses[record_index];
    slot->sharks = view->inventory_sharks[record_index];
    slot->running = view->run_enabled[record_index];
    slot->terminal_code = view->terminal_codes[record_index];
    fc_terminal_flags_from_code(slot->terminal_code, &slot->terminated, &slot->truncated);
    slot->wave_spawned = 1;
    fc_clear_targets(slot);
    for (int index = 0; index < visible_count && index < FC_MAX_VISIBLE_TARGETS; ++index) {
        int offset = (record_index * view->max_visible) + index;
        fc_visible_target target;
        memset(&target, 0, sizeof(target));
        target.listed = 1;
        target.currently_visible = view->visible_alive[offset] ? 1 : 0;
        target.npc_index = view->visible_npc_indices[offset];
        target.id_code = view->visible_id_codes[offset];
        target.tile_x = 1 + (index * 2);
        target.tile_y = slot->tick + index;
        target.tile_level = 0;
        target.hitpoints_current = view->visible_hitpoints[offset];
        target.hitpoints_max = view->visible_hitpoints[offset] > 0 ? view->visible_hitpoints[offset] : 1;
        target.dead = view->visible_alive[offset] ? 0 : 1;
        slot->visible_targets[slot->visible_target_count++] = target;
    }
}

static void fc_write_flat_observation_row(float* row, const fc_slot_state* slot) {
    memset(row, 0, sizeof(float) * FC_RESET_OBSERVATION_FEATURE_COUNT);
    row[0] = 1.0f;
    row[1] = (float)slot->tick;
    row[2] = (float)slot->seed;
    row[3] = (float)slot->tile_x;
    row[4] = (float)slot->tile_y;
    row[5] = (float)slot->tile_level;
    row[6] = (float)slot->hitpoints_current;
    row[7] = (float)slot->hitpoints_max;
    row[8] = (float)slot->prayer_current;
    row[9] = (float)slot->prayer_max;
    row[10] = (float)slot->run_energy;
    row[11] = (float)slot->run_energy_max;
    row[12] = slot->run_energy_max > 0 ? (float)((slot->run_energy * 100) / slot->run_energy_max) : 0.0f;
    row[13] = slot->running ? 1.0f : 0.0f;
    row[14] = slot->protect_from_magic ? 1.0f : 0.0f;
    row[15] = slot->protect_from_missiles ? 1.0f : 0.0f;
    row[16] = slot->protect_from_melee ? 1.0f : 0.0f;
    row[17] = slot->attack_locked ? 1.0f : 0.0f;
    row[18] = slot->food_locked ? 1.0f : 0.0f;
    row[19] = slot->drink_locked ? 1.0f : 0.0f;
    row[20] = slot->combo_locked ? 1.0f : 0.0f;
    row[21] = slot->busy_locked ? 1.0f : 0.0f;
    row[22] = (float)slot->sharks;
    row[23] = (float)slot->prayer_potion_dose_count;
    row[24] = (float)FC_RESET_AMMO_ID_CODE;
    row[25] = (float)slot->ammo;
    row[26] = (float)slot->wave;
    row[27] = (float)slot->rotation;
    row[28] = (float)slot->remaining;
    row[29] = (float)slot->visible_target_count;

    for (int index = 0; index < slot->visible_target_count && index < FC_MAX_VISIBLE_TARGETS; ++index) {
        const fc_visible_target* target = &slot->visible_targets[index];
        int offset = 30 + (index * 13);
        row[offset] = 1.0f;
        row[offset + 1] = (float)index;
        row[offset + 2] = (float)target->npc_index;
        row[offset + 3] = (float)target->id_code;
        row[offset + 4] = (float)target->tile_x;
        row[offset + 5] = (float)target->tile_y;
        row[offset + 6] = (float)target->tile_level;
        row[offset + 7] = (float)target->hitpoints_current;
        row[offset + 8] = (float)target->hitpoints_max;
        row[offset + 9] = target->hidden ? 1.0f : 0.0f;
        row[offset + 10] = target->dead ? 1.0f : 0.0f;
        row[offset + 11] = target->under_attack ? 1.0f : 0.0f;
        row[offset + 12] = (float)target->jad_telegraph_state;
    }
}

static void fc_initialize_slot(fc_slot_state* slot, const fc_episode_config* config) {
    memset(slot, 0, sizeof(*slot));
    slot->initialized = 1;
    slot->seed = config->seed;
    slot->wave = config->start_wave;
    slot->rotation = fc_resolve_rotation(config->seed, config->start_wave);
    slot->remaining = fc_resolve_remaining(config->start_wave);
    slot->ammo = config->ammo;
    slot->prayer_potion_dose_count = config->prayer_potions * 4;
    slot->sharks = config->sharks;
    slot->tick = 0;
    slot->steps = 0;
    slot->tile_x = FC_RESET_PLAYER_TILE_X;
    slot->tile_y = FC_RESET_PLAYER_TILE_Y;
    slot->tile_level = FC_RESET_PLAYER_TILE_LEVEL;
    slot->hitpoints_current = FC_RESET_HITPOINTS_CURRENT;
    slot->hitpoints_max = FC_RESET_HITPOINTS_MAX;
    slot->prayer_current = FC_RESET_PRAYER_CURRENT;
    slot->prayer_max = FC_RESET_PRAYER_MAX;
    slot->run_energy = FC_RESET_RUN_ENERGY;
    slot->run_energy_max = FC_RESET_RUN_ENERGY_MAX;
    slot->running = 1;
    slot->last_action_tick = -2147483647;
    slot->tick_cap = FC_GENERIC_DEFAULT_TICK_CAP;
    slot->slot_mode = FC_SLOT_MODE_GENERIC;
    slot->next_npc_index = 1;
}

static int fc_allocate_last_reset(fc_native_runtime* runtime, int env_count) {
    size_t env_count_size = (size_t)env_count;
    size_t observation_count = env_count_size * FC_RESET_OBSERVATION_FEATURE_COUNT;
    size_t reward_count = env_count_size * FC_RESET_REWARD_FEATURE_COUNT;
    runtime->last_reset_episode_seeds = (int64_t*)calloc(env_count_size, sizeof(int64_t));
    runtime->last_reset_waves = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_reset_rotations = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_reset_remaining = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_reset_ammo = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_reset_prayer_potion_doses = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_reset_sharks = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_reset_flat_observations = (float*)calloc(observation_count, sizeof(float));
    runtime->last_reset_reward_features = (float*)calloc(reward_count, sizeof(float));
    runtime->last_reset_terminal_codes = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_reset_terminated = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_reset_truncated = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_reset_episode_ticks = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_reset_episode_steps = (int*)calloc(env_count_size, sizeof(int));
    return runtime->last_reset_episode_seeds != NULL &&
        runtime->last_reset_waves != NULL &&
        runtime->last_reset_rotations != NULL &&
        runtime->last_reset_remaining != NULL &&
        runtime->last_reset_ammo != NULL &&
        runtime->last_reset_prayer_potion_doses != NULL &&
        runtime->last_reset_sharks != NULL &&
        runtime->last_reset_flat_observations != NULL &&
        runtime->last_reset_reward_features != NULL &&
        runtime->last_reset_terminal_codes != NULL &&
        runtime->last_reset_terminated != NULL &&
        runtime->last_reset_truncated != NULL &&
        runtime->last_reset_episode_ticks != NULL &&
        runtime->last_reset_episode_steps != NULL;
}

static int fc_allocate_last_step(fc_native_runtime* runtime, int env_count) {
    size_t env_count_size = (size_t)env_count;
    size_t observation_count = env_count_size * FC_RESET_OBSERVATION_FEATURE_COUNT;
    size_t reward_count = env_count_size * FC_RESET_REWARD_FEATURE_COUNT;
    runtime->last_step_slot_indices = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_step_action_ids = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_step_action_accepted = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_step_rejection_codes = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_step_resolved_target_npc_indices = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_step_flat_observations = (float*)calloc(observation_count, sizeof(float));
    runtime->last_step_reward_features = (float*)calloc(reward_count, sizeof(float));
    runtime->last_step_terminal_codes = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_step_terminated = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_step_truncated = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_step_episode_ticks = (int*)calloc(env_count_size, sizeof(int));
    runtime->last_step_episode_steps = (int*)calloc(env_count_size, sizeof(int));
    return runtime->last_step_slot_indices != NULL &&
        runtime->last_step_action_ids != NULL &&
        runtime->last_step_action_accepted != NULL &&
        runtime->last_step_rejection_codes != NULL &&
        runtime->last_step_resolved_target_npc_indices != NULL &&
        runtime->last_step_flat_observations != NULL &&
        runtime->last_step_reward_features != NULL &&
        runtime->last_step_terminal_codes != NULL &&
        runtime->last_step_terminated != NULL &&
        runtime->last_step_truncated != NULL &&
        runtime->last_step_episode_ticks != NULL &&
        runtime->last_step_episode_steps != NULL;
}

static int fc_apply_action_generic(
    fc_slot_state* slot,
    int action_id,
    int arg0,
    int arg1,
    int arg2,
    int* resolved_target_npc_index
) {
    if (slot->last_action_tick == slot->tick) {
        return FC_REJECTION_ALREADY_ACTED_THIS_TICK;
    }
    slot->last_action_tick = slot->tick;
    *resolved_target_npc_index = -1;

    switch (action_id) {
        case FC_ACTION_WAIT:
            return FC_REJECTION_NONE;
        case FC_ACTION_WALK_TO_TILE:
            if (slot->tile_x == arg0 && slot->tile_y == arg1 && slot->tile_level == arg2) {
                return FC_REJECTION_NO_MOVEMENT_REQUIRED;
            }
            slot->tile_x = arg0;
            slot->tile_y = arg1;
            slot->tile_level = arg2;
            return FC_REJECTION_NONE;
        case FC_ACTION_ATTACK_VISIBLE_NPC:
            if (slot->busy_locked) {
                return FC_REJECTION_PLAYER_BUSY;
            }
            if (arg0 < 0 || arg0 >= slot->visible_target_count) {
                return FC_REJECTION_INVALID_TARGET_INDEX;
            }
            if (
                !slot->visible_targets[arg0].currently_visible ||
                slot->visible_targets[arg0].hidden ||
                slot->visible_targets[arg0].dead
            ) {
                return FC_REJECTION_TARGET_NOT_VISIBLE;
            }
            *resolved_target_npc_index = slot->visible_targets[arg0].npc_index;
            slot->visible_targets[arg0].under_attack = 1;
            if (slot->ammo > 0) {
                slot->ammo -= 1;
            }
            slot->visible_targets[arg0].hitpoints_current -= 100;
            if (slot->visible_targets[arg0].hitpoints_current <= 0) {
                slot->visible_targets[arg0].hitpoints_current = 0;
                slot->visible_targets[arg0].dead = 1;
                slot->visible_targets[arg0].currently_visible = 0;
            }
            return FC_REJECTION_NONE;
        case FC_ACTION_TOGGLE_PROTECTION_PRAYER:
            if (arg0 < 0 || arg0 > 2) {
                return FC_REJECTION_INVALID_PRAYER_ID;
            }
            if (slot->prayer_current <= 0) {
                return FC_REJECTION_PRAYER_POINTS_DEPLETED;
            }
            slot->protect_from_magic = arg0 == 0 ? !slot->protect_from_magic : 0;
            slot->protect_from_missiles = arg0 == 1 ? !slot->protect_from_missiles : 0;
            slot->protect_from_melee = arg0 == 2 ? !slot->protect_from_melee : 0;
            return FC_REJECTION_NONE;
        case FC_ACTION_EAT_SHARK:
            if (slot->food_locked) {
                return FC_REJECTION_CONSUMPTION_LOCKED;
            }
            if (slot->sharks <= 0) {
                return FC_REJECTION_MISSING_CONSUMABLE;
            }
            slot->sharks -= 1;
            if (slot->hitpoints_current < slot->hitpoints_max) {
                slot->hitpoints_current += 200;
                if (slot->hitpoints_current > slot->hitpoints_max) {
                    slot->hitpoints_current = slot->hitpoints_max;
                }
            }
            return FC_REJECTION_NONE;
        case FC_ACTION_DRINK_PRAYER_POTION:
            if (slot->drink_locked) {
                return FC_REJECTION_CONSUMPTION_LOCKED;
            }
            if (slot->prayer_potion_dose_count <= 0) {
                return FC_REJECTION_MISSING_CONSUMABLE;
            }
            slot->prayer_potion_dose_count -= 4;
            if (slot->prayer_potion_dose_count < 0) {
                slot->prayer_potion_dose_count = 0;
            }
            slot->prayer_current += 10;
            if (slot->prayer_current > slot->prayer_max) {
                slot->prayer_current = slot->prayer_max;
            }
            return FC_REJECTION_NONE;
        case FC_ACTION_TOGGLE_RUN:
            if (!slot->running && slot->run_energy <= 0) {
                return FC_REJECTION_INSUFFICIENT_RUN_ENERGY;
            }
            slot->running = !slot->running;
            return FC_REJECTION_NONE;
        default:
            return FC_REJECTION_UNSUPPORTED_ACTION_ID;
    }
}

static void fc_advance_generic_slot(fc_slot_state* slot) {
    if (slot->terminated || slot->truncated) {
        return;
    }
    if (fc_slot_in_invalid_state(slot)) {
        fc_set_slot_terminal(slot, FC_TERMINAL_INVALID_STATE);
        return;
    }
    if (!slot->wave_spawned && slot->wave >= 1 && slot->wave <= 63 && slot->remaining > 0) {
        fc_spawn_wave_targets(slot);
    }
    fc_remove_dead_targets(slot);
    slot->remaining = slot->visible_target_count;
    if (slot->visible_target_count > 0) {
        int damage_taken = slot->visible_target_count * 25;
        slot->hitpoints_current -= damage_taken;
        if (slot->hitpoints_current < 0) {
            slot->hitpoints_current = 0;
        }
    }
    if (slot->protect_from_magic || slot->protect_from_missiles || slot->protect_from_melee) {
        if (slot->prayer_current > 0) {
            slot->prayer_current -= 1;
        }
    }
    if (slot->hitpoints_current <= 0) {
        fc_set_slot_terminal(slot, FC_TERMINAL_PLAYER_DEATH);
        return;
    }
    fc_remove_dead_targets(slot);
    slot->remaining = slot->visible_target_count;
    if (slot->remaining <= 0 && slot->wave >= 1 && slot->wave <= 63) {
        if (slot->wave == 63) {
            fc_set_slot_terminal(slot, FC_TERMINAL_CAVE_COMPLETE);
            return;
        }
        slot->wave += 1;
        slot->rotation = -1;
        slot->remaining = fc_resolve_remaining(slot->wave);
        slot->wave_spawned = 0;
        fc_clear_targets(slot);
    }
    if (!slot->terminated && !slot->truncated && slot->tick >= slot->tick_cap) {
        fc_set_slot_terminal(slot, FC_TERMINAL_TICK_CAP);
    }
}

static int fc_step_core_trace_slot(
    fc_slot_state* slot,
    int action_id,
    int arg0,
    int* resolved_target_npc_index
) {
    fc_core_trace_view view;
    if (slot->last_action_tick == slot->tick) {
        return FC_REJECTION_ALREADY_ACTED_THIS_TICK;
    }
    if (!fc_core_trace_view_for_id(slot->core_trace_id, &view) || view.record_count == 0) {
        slot->force_invalid_state = 1;
        fc_set_slot_terminal(slot, FC_TERMINAL_INVALID_STATE);
        return FC_REJECTION_NONE;
    }
    slot->last_action_tick = slot->tick;
    *resolved_target_npc_index = -1;
    if (action_id == FC_ACTION_ATTACK_VISIBLE_NPC && arg0 >= 0 && arg0 < slot->visible_target_count) {
        *resolved_target_npc_index = slot->visible_targets[arg0].npc_index;
    }
    if ((size_t)(slot->core_trace_record_index + 1) < view.record_count) {
        slot->core_trace_record_index += 1;
    }
    fc_apply_core_trace_record(slot, &view, slot->core_trace_record_index);
    return view.rejection_codes[slot->core_trace_record_index];
}

int fc_native_abi_version(void) {
    return FC_NATIVE_ABI_VERSION;
}

const char* fc_native_runtime_version_string(void) {
    return "pr5a_core_combat_terminal_v1";
}

fc_native_runtime* fc_native_runtime_open(void) {
    fc_native_runtime* runtime = (fc_native_runtime*)calloc(1, sizeof(fc_native_runtime));
    if (runtime == NULL) {
        fc_set_error("failed to allocate native runtime handle");
        return NULL;
    }
    runtime->abi_version = FC_NATIVE_ABI_VERSION;
    runtime->open = 1;
    fc_last_error[0] = '\0';
    return runtime;
}

void fc_native_runtime_close(fc_native_runtime* runtime) {
    if (runtime == NULL) {
        return;
    }
    runtime->open = 0;
    fc_release_last_reset(runtime);
    fc_release_last_step(runtime);
    fc_release_slots(runtime);
    free(runtime);
}

const char* fc_native_runtime_last_error(void) {
    return fc_last_error;
}

int fc_native_runtime_descriptor_count(const fc_native_runtime* runtime) {
    if (!fc_runtime_ready(runtime)) {
        return -1;
    }
    return fc_descriptor_count;
}

const char* fc_native_runtime_descriptor_bundle_json(const fc_native_runtime* runtime) {
    if (!fc_runtime_ready(runtime)) {
        return NULL;
    }
    return fc_descriptor_bundle_json;
}

size_t fc_native_runtime_descriptor_bundle_json_size(const fc_native_runtime* runtime) {
    if (!fc_runtime_ready(runtime)) {
        return 0;
    }
    return fc_descriptor_bundle_json_len;
}

int fc_native_runtime_reset_batch(
    fc_native_runtime* runtime,
    const fc_episode_config* configs,
    int env_count
) {
    if (!fc_runtime_ready(runtime)) {
        return 0;
    }
    if (env_count < 0) {
        fc_set_error("Reset batch env_count cannot be negative.");
        return 0;
    }
    if (env_count > 0 && configs == NULL) {
        fc_set_error("Reset batch configs are required when env_count > 0.");
        return 0;
    }
    for (int env_index = 0; env_index < env_count; ++env_index) {
        if (!fc_validate_config(&configs[env_index])) {
            return 0;
        }
    }

    fc_release_last_reset(runtime);
    fc_release_last_step(runtime);
    fc_release_slots(runtime);
    runtime->last_reset_env_count = env_count;
    runtime->slot_count = env_count;
    if (env_count == 0) {
        fc_last_error[0] = '\0';
        return 1;
    }

    runtime->slots = (fc_slot_state*)calloc((size_t)env_count, sizeof(fc_slot_state));
    if (runtime->slots == NULL || !fc_allocate_last_reset(runtime, env_count)) {
        fc_release_last_reset(runtime);
        fc_release_slots(runtime);
        fc_set_error("Failed to allocate reset batch buffers.");
        return 0;
    }

    for (int env_index = 0; env_index < env_count; ++env_index) {
        fc_slot_state* slot = &runtime->slots[env_index];
        float* row = runtime->last_reset_flat_observations + ((size_t)env_index * FC_RESET_OBSERVATION_FEATURE_COUNT);
        fc_initialize_slot(slot, &configs[env_index]);
        runtime->last_reset_episode_seeds[env_index] = slot->seed;
        runtime->last_reset_waves[env_index] = slot->wave;
        runtime->last_reset_rotations[env_index] = slot->rotation;
        runtime->last_reset_remaining[env_index] = slot->remaining;
        runtime->last_reset_ammo[env_index] = slot->ammo;
        runtime->last_reset_prayer_potion_doses[env_index] = slot->prayer_potion_dose_count;
        runtime->last_reset_sharks[env_index] = slot->sharks;
        runtime->last_reset_terminal_codes[env_index] = slot->terminal_code;
        runtime->last_reset_terminated[env_index] = slot->terminated;
        runtime->last_reset_truncated[env_index] = slot->truncated;
        runtime->last_reset_episode_ticks[env_index] = slot->tick;
        runtime->last_reset_episode_steps[env_index] = slot->steps;
        fc_write_flat_observation_row(row, slot);
    }

    fc_last_error[0] = '\0';
    return 1;
}

int fc_native_runtime_last_reset_env_count(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_reset_buffers(runtime)) {
        return -1;
    }
    return runtime->last_reset_env_count;
}

const int64_t* fc_native_runtime_last_reset_episode_seeds(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_reset_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_reset_episode_seeds;
}

const int* fc_native_runtime_last_reset_waves(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_reset_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_reset_waves;
}

const int* fc_native_runtime_last_reset_rotations(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_reset_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_reset_rotations;
}

const int* fc_native_runtime_last_reset_remaining(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_reset_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_reset_remaining;
}

const int* fc_native_runtime_last_reset_ammo(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_reset_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_reset_ammo;
}

const int* fc_native_runtime_last_reset_prayer_potion_doses(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_reset_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_reset_prayer_potion_doses;
}

const int* fc_native_runtime_last_reset_sharks(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_reset_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_reset_sharks;
}

const float* fc_native_runtime_last_reset_flat_observations(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_reset_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_reset_flat_observations;
}

const float* fc_native_runtime_last_reset_reward_features(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_reset_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_reset_reward_features;
}

const int* fc_native_runtime_last_reset_terminal_codes(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_reset_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_reset_terminal_codes;
}

const int* fc_native_runtime_last_reset_terminated(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_reset_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_reset_terminated;
}

const int* fc_native_runtime_last_reset_truncated(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_reset_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_reset_truncated;
}

const int* fc_native_runtime_last_reset_episode_ticks(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_reset_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_reset_episode_ticks;
}

const int* fc_native_runtime_last_reset_episode_steps(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_reset_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_reset_episode_steps;
}

int fc_native_runtime_debug_configure_slot_state(
    fc_native_runtime* runtime,
    int slot_index,
    const fc_slot_debug_state* state
) {
    if (!fc_runtime_has_slots(runtime)) {
        return 0;
    }
    if (!fc_validate_slot_index(runtime, slot_index)) {
        return 0;
    }
    if (state == NULL) {
        fc_set_error("slot debug state is required");
        return 0;
    }
    fc_slot_state* slot = &runtime->slots[slot_index];
    slot->tile_x = state->tile_x;
    slot->tile_y = state->tile_y;
    slot->tile_level = state->tile_level;
    slot->hitpoints_current = state->hitpoints_current;
    slot->prayer_current = state->prayer_current;
    slot->run_energy = state->run_energy;
    slot->running = state->running ? 1 : 0;
    slot->protect_from_magic = state->protect_from_magic ? 1 : 0;
    slot->protect_from_missiles = state->protect_from_missiles ? 1 : 0;
    slot->protect_from_melee = state->protect_from_melee ? 1 : 0;
    slot->attack_locked = state->attack_locked ? 1 : 0;
    slot->food_locked = state->food_locked ? 1 : 0;
    slot->drink_locked = state->drink_locked ? 1 : 0;
    slot->combo_locked = state->combo_locked ? 1 : 0;
    slot->busy_locked = state->busy_locked ? 1 : 0;
    slot->sharks = state->sharks;
    slot->prayer_potion_dose_count = state->prayer_potion_dose_count;
    slot->ammo = state->ammo;
    fc_last_error[0] = '\0';
    return 1;
}

int fc_native_runtime_debug_set_slot_visible_targets(
    fc_native_runtime* runtime,
    int slot_index,
    const fc_visible_target* targets,
    int target_count
) {
    if (!fc_runtime_has_slots(runtime)) {
        return 0;
    }
    if (!fc_validate_slot_index(runtime, slot_index)) {
        return 0;
    }
    if (target_count < 0 || target_count > FC_MAX_VISIBLE_TARGETS) {
        fc_set_error("visible target count must be in range 0..8");
        return 0;
    }
    fc_slot_state* slot = &runtime->slots[slot_index];
    fc_clear_targets(slot);
    for (int index = 0; index < target_count; ++index) {
        if (!targets[index].listed) {
            continue;
        }
        slot->visible_targets[slot->visible_target_count++] = targets[index];
    }
    if (slot->slot_mode == FC_SLOT_MODE_GENERIC && slot->visible_target_count > 1) {
        qsort(
            slot->visible_targets,
            (size_t)slot->visible_target_count,
            sizeof(fc_visible_target),
            fc_visible_target_compare
        );
    }
    slot->wave_spawned = 1;
    if (slot->remaining < slot->visible_target_count) {
        slot->remaining = slot->visible_target_count;
    }
    fc_last_error[0] = '\0';
    return 1;
}

int fc_native_runtime_debug_set_slot_control(
    fc_native_runtime* runtime,
    int slot_index,
    int tick_cap,
    int force_invalid_state
) {
    if (!fc_runtime_has_slots(runtime)) {
        return 0;
    }
    if (!fc_validate_slot_index(runtime, slot_index)) {
        return 0;
    }
    if (tick_cap <= 0) {
        fc_set_error("tick_cap must be greater than zero");
        return 0;
    }
    runtime->slots[slot_index].tick_cap = tick_cap;
    runtime->slots[slot_index].force_invalid_state = force_invalid_state ? 1 : 0;
    fc_last_error[0] = '\0';
    return 1;
}

int fc_native_runtime_debug_load_core_trace(
    fc_native_runtime* runtime,
    int slot_index,
    int trace_id
) {
    fc_core_trace_view view;
    if (!fc_runtime_has_slots(runtime)) {
        return 0;
    }
    if (!fc_validate_slot_index(runtime, slot_index)) {
        return 0;
    }
    if (!fc_core_trace_view_for_id(trace_id, &view)) {
        fc_set_error("unsupported core trace id");
        return 0;
    }
    fc_slot_state* slot = &runtime->slots[slot_index];
    slot->slot_mode = FC_SLOT_MODE_CORE_TRACE;
    slot->core_trace_id = trace_id;
    slot->core_trace_record_index = 0;
    slot->last_action_tick = -2147483647;
    fc_apply_core_trace_record(slot, &view, 0);
    fc_last_error[0] = '\0';
    return 1;
}

int fc_native_runtime_step_batch(
    fc_native_runtime* runtime,
    const int* slot_indices,
    const int32_t* packed_actions,
    int env_count
) {
    if (!fc_runtime_has_slots(runtime)) {
        return 0;
    }
    if (env_count < 0) {
        fc_set_error("step batch env_count cannot be negative");
        return 0;
    }
    if (env_count == 0) {
        fc_release_last_step(runtime);
        fc_last_error[0] = '\0';
        return 1;
    }
    if (packed_actions == NULL) {
        fc_set_error("packed action batch is required");
        return 0;
    }

    fc_release_last_step(runtime);
    runtime->last_step_env_count = env_count;
    if (!fc_allocate_last_step(runtime, env_count)) {
        fc_release_last_step(runtime);
        fc_set_error("Failed to allocate step batch buffers.");
        return 0;
    }
    for (int env_index = 0; env_index < env_count; ++env_index) {
        runtime->last_step_resolved_target_npc_indices[env_index] = -1;
    }

    int* touched_generic = (int*)calloc((size_t)runtime->slot_count, sizeof(int));
    if (touched_generic == NULL) {
        fc_release_last_step(runtime);
        fc_set_error("Failed to allocate step touched-slot tracker.");
        return 0;
    }

    for (int env_index = 0; env_index < env_count; ++env_index) {
        int slot_index = slot_indices == NULL ? env_index : slot_indices[env_index];
        int action_offset = env_index * FC_PACKED_ACTION_WORD_COUNT;
        int action_id = packed_actions[action_offset];
        int arg0 = packed_actions[action_offset + 1];
        int arg1 = packed_actions[action_offset + 2];
        int arg2 = packed_actions[action_offset + 3];
        int rejection_code = FC_REJECTION_NONE;
        int resolved_target_npc_index = -1;
        fc_slot_state* slot;

        if (!fc_validate_slot_index(runtime, slot_index)) {
            free(touched_generic);
            fc_release_last_step(runtime);
            return 0;
        }
        slot = &runtime->slots[slot_index];
        runtime->last_step_slot_indices[env_index] = slot_index;
        runtime->last_step_action_ids[env_index] = action_id;

        if (slot->slot_mode == FC_SLOT_MODE_CORE_TRACE) {
            rejection_code = fc_step_core_trace_slot(slot, action_id, arg0, &resolved_target_npc_index);
        } else {
            rejection_code = fc_apply_action_generic(
                slot,
                action_id,
                arg0,
                arg1,
                arg2,
                &resolved_target_npc_index
            );
            if (rejection_code == FC_REJECTION_NONE) {
                touched_generic[slot_index] = 1;
            }
        }

        runtime->last_step_action_accepted[env_index] = rejection_code == FC_REJECTION_NONE ? 1 : 0;
        runtime->last_step_rejection_codes[env_index] = rejection_code;
        runtime->last_step_resolved_target_npc_indices[env_index] = resolved_target_npc_index;
    }

    for (int slot_index = 0; slot_index < runtime->slot_count; ++slot_index) {
        fc_slot_state* slot = &runtime->slots[slot_index];
        if (!touched_generic[slot_index]) {
            continue;
        }
        slot->tick += 1;
        slot->steps += 1;
        fc_advance_generic_slot(slot);
    }
    free(touched_generic);

    for (int env_index = 0; env_index < env_count; ++env_index) {
        int slot_index = runtime->last_step_slot_indices[env_index];
        fc_slot_state* slot = &runtime->slots[slot_index];
        float* row = runtime->last_step_flat_observations + ((size_t)env_index * FC_RESET_OBSERVATION_FEATURE_COUNT);
        fc_write_flat_observation_row(row, slot);
        runtime->last_step_terminal_codes[env_index] = slot->terminal_code;
        runtime->last_step_terminated[env_index] = slot->terminated;
        runtime->last_step_truncated[env_index] = slot->truncated;
        runtime->last_step_episode_ticks[env_index] = slot->tick;
        runtime->last_step_episode_steps[env_index] = slot->steps;
    }

    fc_last_error[0] = '\0';
    return 1;
}

int fc_native_runtime_last_step_env_count(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_step_buffers(runtime)) {
        return -1;
    }
    return runtime->last_step_env_count;
}

const int* fc_native_runtime_last_step_slot_indices(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_step_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_step_slot_indices;
}

const int* fc_native_runtime_last_step_action_ids(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_step_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_step_action_ids;
}

const int* fc_native_runtime_last_step_action_accepted(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_step_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_step_action_accepted;
}

const int* fc_native_runtime_last_step_rejection_codes(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_step_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_step_rejection_codes;
}

const int* fc_native_runtime_last_step_resolved_target_npc_indices(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_step_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_step_resolved_target_npc_indices;
}

const float* fc_native_runtime_last_step_flat_observations(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_step_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_step_flat_observations;
}

const float* fc_native_runtime_last_step_reward_features(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_step_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_step_reward_features;
}

const int* fc_native_runtime_last_step_terminal_codes(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_step_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_step_terminal_codes;
}

const int* fc_native_runtime_last_step_terminated(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_step_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_step_terminated;
}

const int* fc_native_runtime_last_step_truncated(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_step_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_step_truncated;
}

const int* fc_native_runtime_last_step_episode_ticks(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_step_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_step_episode_ticks;
}

const int* fc_native_runtime_last_step_episode_steps(const fc_native_runtime* runtime) {
    if (!fc_runtime_has_step_buffers(runtime)) {
        return NULL;
    }
    return runtime->last_step_episode_steps;
}
