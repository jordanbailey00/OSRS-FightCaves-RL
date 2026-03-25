#include "fight_caves_native_runtime.h"

#include "raylib.h"
#include "raymath.h"
#include "fight_caves_viewer_assets.generated.h"

#include <inttypes.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define FC_VIEWER_WIDTH 1440
#define FC_VIEWER_HEIGHT 960
#define FC_VIEWER_MAX_SCRIPTED_ACTIONS 64
#define FC_VIEWER_ACTION_LABEL_SIZE 64
#define FC_VIEWER_REPLAY_SOURCE_SIZE 128
#define FC_VIEWER_ASSET_PATH_SIZE 512

enum {
    FC_VIEWER_ACTION_WAIT = 0,
    FC_VIEWER_ACTION_WALK_TO_TILE = 1,
    FC_VIEWER_ACTION_ATTACK_VISIBLE_NPC = 2,
    FC_VIEWER_ACTION_TOGGLE_PROTECTION_PRAYER = 3,
    FC_VIEWER_ACTION_EAT_SHARK = 4,
    FC_VIEWER_ACTION_DRINK_PRAYER_POTION = 5,
    FC_VIEWER_ACTION_TOGGLE_RUN = 6,
};

enum {
    FC_VIEWER_REJECTION_NONE = 0,
    FC_VIEWER_REJECTION_ALREADY_ACTED_THIS_TICK = 1,
    FC_VIEWER_REJECTION_INVALID_TARGET_INDEX = 2,
    FC_VIEWER_REJECTION_TARGET_NOT_VISIBLE = 3,
    FC_VIEWER_REJECTION_PLAYER_BUSY = 4,
    FC_VIEWER_REJECTION_MISSING_CONSUMABLE = 5,
    FC_VIEWER_REJECTION_CONSUMPTION_LOCKED = 6,
    FC_VIEWER_REJECTION_PRAYER_POINTS_DEPLETED = 7,
    FC_VIEWER_REJECTION_INSUFFICIENT_RUN_ENERGY = 8,
    FC_VIEWER_REJECTION_NO_MOVEMENT_REQUIRED = 9,
    FC_VIEWER_REJECTION_UNSUPPORTED_ACTION_ID = 10,
    FC_VIEWER_REJECTION_INVALID_PRAYER_ID = 11,
};

enum {
    FC_VIEWER_PRAYER_MAGIC = 0,
    FC_VIEWER_PRAYER_MISSILES = 1,
    FC_VIEWER_PRAYER_MELEE = 2,
};

typedef struct fc_viewer_options {
    int width;
    int height;
    int dump_scene_json;
    int autoplay;
    int replay_frame;
    int step_count;
    int start_wave;
    int ammo;
    int prayer_potions;
    int sharks;
    int apply_action_count;
    int64_t seed;
    const char* replay_file;
    const char* trace_name;
    const char* apply_actions[FC_VIEWER_MAX_SCRIPTED_ACTIONS];
} fc_viewer_options;

typedef struct fc_hover_state {
    int kind;
    int npc_index;
    int visible_index;
} fc_hover_state;

typedef struct fc_ground_hover_state {
    int valid;
    int tile_x;
    int tile_y;
    int tile_level;
} fc_ground_hover_state;

typedef struct fc_viewer_action_feedback {
    int present;
    int action_id;
    int accepted;
    int rejection_code;
    int resolved_target_npc_index;
    int jad_telegraph_state;
    int jad_hit_resolve_outcome_code;
    int terminal_code;
    int terminated;
    int truncated;
    int episode_tick;
    int episode_step;
    char action_label[FC_VIEWER_ACTION_LABEL_SIZE];
} fc_viewer_action_feedback;

typedef struct fc_hud_layout {
    Rectangle panel;
    Rectangle shark_button;
    Rectangle potion_button;
    Rectangle prayer_magic_button;
    Rectangle prayer_missiles_button;
    Rectangle prayer_melee_button;
    Rectangle run_button;
    Rectangle replay_scrub_bar;
} fc_hud_layout;

typedef struct fc_replay_action {
    int32_t words[4];
    char label[FC_VIEWER_ACTION_LABEL_SIZE];
} fc_replay_action;

typedef struct fc_replay_frame {
    fc_runtime_slot_snapshot snapshot;
    fc_viewer_action_feedback feedback;
    int selected_npc_index;
} fc_replay_frame;

typedef struct fc_viewer_replay {
    int loaded;
    int action_count;
    int frame_count;
    int current_frame;
    int playing;
    fc_episode_config config;
    char source_kind[32];
    char source_ref[FC_VIEWER_REPLAY_SOURCE_SIZE];
    fc_replay_action* actions;
    fc_replay_frame* frames;
} fc_viewer_replay;

typedef struct fc_viewer_hud_textures {
    Texture2D panel_side_background;
    int panel_side_background_loaded;
    Texture2D pray_magic;
    int pray_magic_loaded;
    Texture2D pray_missiles;
    int pray_missiles_loaded;
    Texture2D pray_melee;
    int pray_melee_loaded;
    Texture2D inventory_slot;
    int inventory_slot_loaded;
    Texture2D inventory_slot_frame_a;
    int inventory_slot_frame_a_loaded;
    Texture2D inventory_slot_frame_b;
    int inventory_slot_frame_b_loaded;
    Texture2D wave_digits[10];
    int wave_digits_loaded[10];
} fc_viewer_hud_textures;

enum {
    FC_HOVER_NONE = 0,
    FC_HOVER_PLAYER = 1,
    FC_HOVER_NPC = 2,
};

static int fc_find_visible_target_index(
    const fc_runtime_slot_snapshot* snapshot,
    int npc_index
);

static const char* fc_scene_rect_label(int kind_code) {
    switch (kind_code) {
        case FC_NATIVE_SCENE_RECT_ARENA_BOUNDS: return "arena_bounds";
        case FC_NATIVE_SCENE_RECT_SPAWN_NORTH_WEST: return "spawn_north_west";
        case FC_NATIVE_SCENE_RECT_SPAWN_SOUTH_WEST: return "spawn_south_west";
        case FC_NATIVE_SCENE_RECT_SPAWN_SOUTH: return "spawn_south";
        case FC_NATIVE_SCENE_RECT_SPAWN_SOUTH_EAST: return "spawn_south_east";
        case FC_NATIVE_SCENE_RECT_PLAYER_DAIS: return "player_dais";
        default: return "unknown";
    }
}

static const char* fc_terminal_label(int code) {
    switch (code) {
        case 0: return "none";
        case 1: return "player_death";
        case 2: return "cave_complete";
        case 3: return "tick_cap";
        case 4: return "invalid_state";
        default: return "unknown";
    }
}

static const char* fc_jad_outcome_label(int code) {
    switch (code) {
        case 0: return "none";
        case 1: return "pending";
        case 2: return "protected";
        case 3: return "hit";
        default: return "unknown";
    }
}

static const char* fc_rejection_label(int code) {
    switch (code) {
        case FC_VIEWER_REJECTION_NONE: return "none";
        case FC_VIEWER_REJECTION_ALREADY_ACTED_THIS_TICK: return "already_acted_this_tick";
        case FC_VIEWER_REJECTION_INVALID_TARGET_INDEX: return "invalid_target_index";
        case FC_VIEWER_REJECTION_TARGET_NOT_VISIBLE: return "target_not_visible";
        case FC_VIEWER_REJECTION_PLAYER_BUSY: return "player_busy";
        case FC_VIEWER_REJECTION_MISSING_CONSUMABLE: return "missing_consumable";
        case FC_VIEWER_REJECTION_CONSUMPTION_LOCKED: return "consumption_locked";
        case FC_VIEWER_REJECTION_PRAYER_POINTS_DEPLETED: return "prayer_points_depleted";
        case FC_VIEWER_REJECTION_INSUFFICIENT_RUN_ENERGY: return "insufficient_run_energy";
        case FC_VIEWER_REJECTION_NO_MOVEMENT_REQUIRED: return "no_movement_required";
        case FC_VIEWER_REJECTION_UNSUPPORTED_ACTION_ID: return "unsupported_action_id";
        case FC_VIEWER_REJECTION_INVALID_PRAYER_ID: return "invalid_prayer_id";
        default: return "unknown";
    }
}

static const char* fc_prayer_label(int prayer_id) {
    switch (prayer_id) {
        case FC_VIEWER_PRAYER_MAGIC: return "magic";
        case FC_VIEWER_PRAYER_MISSILES: return "missiles";
        case FC_VIEWER_PRAYER_MELEE: return "melee";
        default: return "unknown";
    }
}

static int fc_trace_id_for_name(const char* name) {
    if (name == NULL) {
        return 0;
    }
    if (strcmp(name, "single_wave") == 0) {
        return 1;
    }
    if (strcmp(name, "full_run") == 0) {
        return 2;
    }
    if (strcmp(name, "jad_telegraph") == 0) {
        return 3;
    }
    if (strcmp(name, "jad_prayer_protected") == 0) {
        return 4;
    }
    if (strcmp(name, "jad_prayer_too_late") == 0) {
        return 5;
    }
    if (strcmp(name, "jad_healer") == 0) {
        return 6;
    }
    if (strcmp(name, "tzkek_split") == 0) {
        return 7;
    }
    return -1;
}

static Color fc_scene_rect_color(int kind_code) {
    switch (kind_code) {
        case FC_NATIVE_SCENE_RECT_ARENA_BOUNDS: return (Color){115, 75, 56, 255};
        case FC_NATIVE_SCENE_RECT_SPAWN_NORTH_WEST: return (Color){214, 125, 52, 255};
        case FC_NATIVE_SCENE_RECT_SPAWN_SOUTH_WEST: return (Color){189, 97, 41, 255};
        case FC_NATIVE_SCENE_RECT_SPAWN_SOUTH: return (Color){214, 147, 76, 255};
        case FC_NATIVE_SCENE_RECT_SPAWN_SOUTH_EAST: return (Color){235, 178, 92, 255};
        case FC_NATIVE_SCENE_RECT_PLAYER_DAIS: return (Color){84, 133, 132, 255};
        default: return GRAY;
    }
}

static Color fc_npc_color(int id_code, int jad_telegraph_state) {
    if (id_code == 11) {
        if (jad_telegraph_state > 0) {
            return FC_VIEWER_COLOR_NPC_JAD_TELEGRAPH;
        }
        return FC_VIEWER_COLOR_NPC_JAD;
    }
    if (id_code == 5) {
        return FC_VIEWER_COLOR_NPC_HEALER;
    }
    if (id_code == 2) {
        return FC_VIEWER_COLOR_NPC_RANGER;
    }
    if (id_code == 4) {
        return FC_VIEWER_COLOR_NPC_MAGE;
    }
    return FC_VIEWER_COLOR_NPC_MELEE;
}

static Vector3 fc_world_position(int tile_x, int tile_y, float elevation) {
    return (Vector3){(float)tile_x, elevation, -(float)tile_y};
}

static Color fc_color_lerp(Color a, Color b, float t) {
    if (t < 0.0f) {
        t = 0.0f;
    }
    if (t > 1.0f) {
        t = 1.0f;
    }
    return (Color){
        (unsigned char)lroundf((float)a.r + ((float)b.r - (float)a.r) * t),
        (unsigned char)lroundf((float)a.g + ((float)b.g - (float)a.g) * t),
        (unsigned char)lroundf((float)a.b + ((float)b.b - (float)a.b) * t),
        (unsigned char)lroundf((float)a.a + ((float)b.a - (float)a.a) * t),
    };
}

static const fc_scene_rect* fc_find_scene_rect(
    const fc_scene_rect* rects,
    int rect_count,
    int kind_code
) {
    for (int index = 0; index < rect_count; ++index) {
        if (rects[index].kind_code == kind_code) {
            return &rects[index];
        }
    }
    return NULL;
}

static void fc_append_asset_path(const char* argv0, const char* filename, char* out, size_t out_size) {
    const char* slash = NULL;
    size_t dir_length = 0;
    if (out_size == 0) {
        return;
    }
    if (argv0 == NULL || *argv0 == '\0') {
        snprintf(out, out_size, "%s", filename);
        return;
    }
    slash = strrchr(argv0, '/');
    if (slash == NULL) {
        snprintf(out, out_size, "%s", filename);
        return;
    }
    dir_length = (size_t)(slash - argv0);
    if (dir_length + 1 >= out_size) {
        snprintf(out, out_size, "%s", filename);
        return;
    }
    memcpy(out, argv0, dir_length);
    out[dir_length] = '/';
    out[dir_length + 1] = '\0';
    strncat(out, filename, out_size - dir_length - 2);
}

static const fc_viewer_sprite_asset* fc_find_hud_sprite_asset(const char* name) {
    for (int index = 0; index < FC_VIEWER_HUD_SPRITE_COUNT; ++index) {
        if (strcmp(FC_VIEWER_HUD_SPRITES[index].name, name) == 0) {
            return &FC_VIEWER_HUD_SPRITES[index];
        }
    }
    return NULL;
}

static int fc_load_texture_asset(
    const char* argv0,
    const char* filename,
    Texture2D* out_texture,
    int* out_loaded
) {
    char asset_path[FC_VIEWER_ASSET_PATH_SIZE];
    *out_loaded = 0;
    memset(out_texture, 0, sizeof(*out_texture));
    if (filename == NULL || *filename == '\0') {
        return 0;
    }
    fc_append_asset_path(argv0, filename, asset_path, sizeof(asset_path));
    if (FileExists(asset_path)) {
        *out_texture = LoadTexture(asset_path);
        *out_loaded = out_texture->id > 0;
        return *out_loaded;
    }
    if (FileExists(filename)) {
        *out_texture = LoadTexture(filename);
        *out_loaded = out_texture->id > 0;
        return *out_loaded;
    }
    return 0;
}

static void fc_load_generated_hud_textures(const char* argv0, fc_viewer_hud_textures* out) {
    const fc_viewer_sprite_asset* sprite = NULL;
    memset(out, 0, sizeof(*out));

    sprite = fc_find_hud_sprite_asset("panel_side_background");
    if (sprite != NULL) {
        fc_load_texture_asset(argv0, sprite->filename, &out->panel_side_background, &out->panel_side_background_loaded);
    }
    sprite = fc_find_hud_sprite_asset("pray_magic");
    if (sprite != NULL) {
        fc_load_texture_asset(argv0, sprite->filename, &out->pray_magic, &out->pray_magic_loaded);
    }
    sprite = fc_find_hud_sprite_asset("pray_missiles");
    if (sprite != NULL) {
        fc_load_texture_asset(argv0, sprite->filename, &out->pray_missiles, &out->pray_missiles_loaded);
    }
    sprite = fc_find_hud_sprite_asset("pray_melee");
    if (sprite != NULL) {
        fc_load_texture_asset(argv0, sprite->filename, &out->pray_melee, &out->pray_melee_loaded);
    }
    sprite = fc_find_hud_sprite_asset("inventory_slot");
    if (sprite != NULL) {
        fc_load_texture_asset(argv0, sprite->filename, &out->inventory_slot, &out->inventory_slot_loaded);
    }
    sprite = fc_find_hud_sprite_asset("inventory_slot_frame_a");
    if (sprite != NULL) {
        fc_load_texture_asset(argv0, sprite->filename, &out->inventory_slot_frame_a, &out->inventory_slot_frame_a_loaded);
    }
    sprite = fc_find_hud_sprite_asset("inventory_slot_frame_b");
    if (sprite != NULL) {
        fc_load_texture_asset(argv0, sprite->filename, &out->inventory_slot_frame_b, &out->inventory_slot_frame_b_loaded);
    }
    for (int digit = 0; digit < 10; ++digit) {
        char sprite_name[32];
        snprintf(sprite_name, sizeof(sprite_name), "wave_digit_%d", digit);
        sprite = fc_find_hud_sprite_asset(sprite_name);
        if (sprite != NULL) {
            fc_load_texture_asset(argv0, sprite->filename, &out->wave_digits[digit], &out->wave_digits_loaded[digit]);
        }
    }
}

static void fc_unload_generated_hud_textures(fc_viewer_hud_textures* textures) {
    if (textures->panel_side_background_loaded) {
        UnloadTexture(textures->panel_side_background);
    }
    if (textures->pray_magic_loaded) {
        UnloadTexture(textures->pray_magic);
    }
    if (textures->pray_missiles_loaded) {
        UnloadTexture(textures->pray_missiles);
    }
    if (textures->pray_melee_loaded) {
        UnloadTexture(textures->pray_melee);
    }
    if (textures->inventory_slot_loaded) {
        UnloadTexture(textures->inventory_slot);
    }
    if (textures->inventory_slot_frame_a_loaded) {
        UnloadTexture(textures->inventory_slot_frame_a);
    }
    if (textures->inventory_slot_frame_b_loaded) {
        UnloadTexture(textures->inventory_slot_frame_b);
    }
    for (int digit = 0; digit < 10; ++digit) {
        if (textures->wave_digits_loaded[digit]) {
            UnloadTexture(textures->wave_digits[digit]);
        }
    }
    memset(textures, 0, sizeof(*textures));
}

static void fc_draw_texture_fit(const Texture2D* texture, int loaded, Rectangle dest, Color tint) {
    Rectangle source;
    if (!loaded || texture == NULL || texture->id <= 0) {
        return;
    }
    source = (Rectangle){0.0f, 0.0f, (float)texture->width, (float)texture->height};
    DrawTexturePro(*texture, source, dest, (Vector2){0.0f, 0.0f}, 0.0f, tint);
}

static const fc_viewer_terrain_tile_asset* fc_find_terrain_tile(int tile_x, int tile_y) {
    for (int index = 0; index < FC_VIEWER_TERRAIN_TILE_COUNT; ++index) {
        if (FC_VIEWER_TERRAIN_TILES[index].tile_x == tile_x && FC_VIEWER_TERRAIN_TILES[index].tile_y == tile_y) {
            return &FC_VIEWER_TERRAIN_TILES[index];
        }
    }
    return NULL;
}

static int fc_tile_kind_is_floor(int kind_code) {
    return kind_code == FC_VIEWER_TILE_FLOOR;
}

static int fc_tile_kind_is_scene_gap(int kind_code) {
    return kind_code == FC_VIEWER_TILE_VOID || kind_code == FC_VIEWER_TILE_LAVA;
}

static int fc_tile_has_floor_neighbor(int tile_x, int tile_y) {
    static const int offsets[4][2] = {
        {-1, 0},
        {1, 0},
        {0, -1},
        {0, 1},
    };
    for (int index = 0; index < 4; ++index) {
        const fc_viewer_terrain_tile_asset* neighbor = fc_find_terrain_tile(
            tile_x + offsets[index][0],
            tile_y + offsets[index][1]
        );
        if (neighbor != NULL && fc_tile_kind_is_floor(neighbor->kind_code)) {
            return 1;
        }
    }
    return 0;
}

static Color fc_cache_floor_color(const fc_viewer_terrain_tile_asset* tile) {
    float variation = 0.08f + 0.04f * sinf(((float)tile->tile_x * 0.37f) + ((float)tile->tile_y * 0.23f));
    return fc_color_lerp(tile->color, FC_VIEWER_COLOR_BASALT_FLOOR_HIGHLIGHT, variation);
}

static Color fc_cache_lava_color(const fc_viewer_terrain_tile_asset* tile, float time_seconds) {
    float pulse = 0.5f + 0.5f * sinf(time_seconds * 2.1f + ((float)tile->tile_x * 0.17f) + ((float)tile->tile_y * 0.11f));
    return fc_color_lerp(FC_VIEWER_COLOR_LAVA_BASE, FC_VIEWER_COLOR_LAVA_HOT, pulse * 0.58f);
}

static void fc_draw_generated_scene_slice(float time_seconds) {
    for (int index = 0; index < FC_VIEWER_TERRAIN_TILE_COUNT; ++index) {
        const fc_viewer_terrain_tile_asset* tile = &FC_VIEWER_TERRAIN_TILES[index];
        Vector3 position = fc_world_position(tile->tile_x, tile->tile_y, 0.0f);
        if (fc_tile_kind_is_floor(tile->kind_code)) {
            DrawCubeV(
                (Vector3){position.x, 0.05f, position.z},
                (Vector3){1.0f, 0.14f, 1.0f},
                fc_cache_floor_color(tile)
            );
            if (((tile->tile_x + tile->tile_y) & 3) == 0) {
                DrawCubeV(
                    (Vector3){position.x, 0.115f, position.z},
                    (Vector3){0.82f, 0.02f, 0.82f},
                    Fade(FC_VIEWER_COLOR_BASALT_WALL_EDGE, 0.28f)
                );
            }
        } else if (fc_tile_kind_is_scene_gap(tile->kind_code)) {
            Color lava = fc_tile_has_floor_neighbor(tile->tile_x, tile->tile_y)
                ? fc_cache_lava_color(tile, time_seconds)
                : Fade(FC_VIEWER_COLOR_LAVA_RING_DIM, 0.92f);
            DrawCubeV(
                (Vector3){position.x, -0.11f, position.z},
                (Vector3){1.0f, 0.12f, 1.0f},
                lava
            );
        }
    }

    for (int index = 0; index < FC_VIEWER_TERRAIN_TILE_COUNT; ++index) {
        const fc_viewer_terrain_tile_asset* tile = &FC_VIEWER_TERRAIN_TILES[index];
        Vector3 position = fc_world_position(tile->tile_x, tile->tile_y, 0.0f);
        if (!fc_tile_kind_is_floor(tile->kind_code) || !fc_tile_has_floor_neighbor(tile->tile_x, tile->tile_y)) {
            continue;
        }
        DrawCubeV(
            (Vector3){position.x, 1.42f, position.z},
            (Vector3){1.02f, 2.76f, 1.02f},
            Fade(FC_VIEWER_COLOR_BASALT_WALL, 0.96f)
        );
        DrawCubeV(
            (Vector3){position.x, 2.76f, position.z},
            (Vector3){0.86f, 0.36f, 0.86f},
            FC_VIEWER_COLOR_BASALT_WALL_EDGE
        );
    }
}

static void fc_draw_ellipse_ring(
    float center_x,
    float center_z,
    float radius_x,
    float radius_z,
    float y,
    Color color
) {
    const int segment_count = 36;
    for (int index = 0; index < segment_count; ++index) {
        float angle_a = ((float)index / (float)segment_count) * PI * 2.0f;
        float angle_b = ((float)(index + 1) / (float)segment_count) * PI * 2.0f;
        Vector3 a = {
            center_x + cosf(angle_a) * radius_x,
            y,
            center_z + sinf(angle_a) * radius_z,
        };
        Vector3 b = {
            center_x + cosf(angle_b) * radius_x,
            y,
            center_z + sinf(angle_b) * radius_z,
        };
        DrawLine3D(a, b, color);
    }
}

static void fc_draw_lava_surface(const fc_scene_rect* arena_bounds, float time_seconds) {
    float width = (float)(arena_bounds->max_tile_x - arena_bounds->min_tile_x + 1) + 28.0f;
    float depth = (float)(arena_bounds->max_tile_y - arena_bounds->min_tile_y + 1) + 28.0f;
    float center_x = ((float)arena_bounds->min_tile_x + (float)arena_bounds->max_tile_x) * 0.5f;
    float center_z = -(((float)arena_bounds->min_tile_y + (float)arena_bounds->max_tile_y) * 0.5f);
    Color lava = fc_color_lerp(
        FC_VIEWER_COLOR_LAVA_BASE,
        FC_VIEWER_COLOR_LAVA_MID,
        0.5f + 0.5f * sinf(time_seconds * 0.33f)
    );
    DrawCubeV((Vector3){center_x, -0.12f, center_z}, (Vector3){width, 0.24f, depth}, lava);
    for (int index = 0; index < FC_VIEWER_LAVA_RING_COUNT; ++index) {
        const fc_viewer_lava_ring_asset* ring = &FC_VIEWER_LAVA_RINGS[index];
        float pulse = 0.5f + 0.5f * sinf(time_seconds * ring->speed * 8.0f + ring->phase * PI * 2.0f);
        Color color = fc_color_lerp(FC_VIEWER_COLOR_LAVA_RING_DIM, FC_VIEWER_COLOR_LAVA_RING, pulse);
        fc_draw_ellipse_ring(ring->tile_x, -ring->tile_y, ring->radius_x, ring->radius_z, -0.01f + pulse * 0.03f, color);
    }
}

static void fc_draw_floor_cracks(const fc_scene_rect* arena_bounds) {
    float min_x = (float)arena_bounds->min_tile_x + 4.0f;
    float max_x = (float)arena_bounds->max_tile_x - 4.0f;
    float min_z = -(float)arena_bounds->max_tile_y + 4.0f;
    float max_z = -(float)arena_bounds->min_tile_y - 10.0f;
    Color crack = Fade(FC_VIEWER_COLOR_DAIS_GLOW, 0.42f);
    DrawLine3D((Vector3){min_x, 0.19f, max_z - 8.0f}, (Vector3){max_x - 6.0f, 0.19f, min_z + 18.0f}, crack);
    DrawLine3D((Vector3){min_x + 8.0f, 0.19f, max_z - 14.0f}, (Vector3){min_x + 18.0f, 0.19f, min_z + 10.0f}, crack);
    DrawLine3D((Vector3){max_x - 10.0f, 0.19f, max_z - 20.0f}, (Vector3){max_x - 2.0f, 0.19f, min_z + 8.0f}, crack);
    DrawLine3D((Vector3){min_x + 4.0f, 0.19f, -6.0f}, (Vector3){max_x - 8.0f, 0.19f, -2.0f}, Fade(FC_VIEWER_COLOR_BASALT_WALL_EDGE, 0.38f));
}

static void fc_draw_arena_shell(const fc_scene_rect* arena_bounds) {
    float width = (float)(arena_bounds->max_tile_x - arena_bounds->min_tile_x + 1);
    float depth = (float)(arena_bounds->max_tile_y - arena_bounds->min_tile_y + 1) - 20.0f;
    float center_x = ((float)arena_bounds->min_tile_x + (float)arena_bounds->max_tile_x) * 0.5f;
    float center_z = -(((float)arena_bounds->min_tile_y + (float)arena_bounds->max_tile_y - 20.0f) * 0.5f);
    DrawCubeV((Vector3){center_x, 0.11f, center_z}, (Vector3){width - 6.0f, 0.22f, depth}, FC_VIEWER_COLOR_BASALT_FLOOR);
    DrawCubeV((Vector3){center_x, 0.18f, center_z}, (Vector3){width - 11.0f, 0.08f, depth - 7.0f}, FC_VIEWER_COLOR_BASALT_FLOOR_HIGHLIGHT);

    DrawCubeV((Vector3){center_x, 2.3f, center_z - depth * 0.5f}, (Vector3){width - 2.0f, 4.6f, 2.6f}, FC_VIEWER_COLOR_BASALT_WALL);
    DrawCubeV((Vector3){center_x, 2.3f, center_z + depth * 0.5f}, (Vector3){width - 8.0f, 4.6f, 2.6f}, FC_VIEWER_COLOR_BASALT_WALL);
    DrawCubeV((Vector3){center_x - width * 0.5f + 1.3f, 2.3f, center_z}, (Vector3){2.6f, 4.6f, depth - 3.0f}, FC_VIEWER_COLOR_BASALT_WALL);
    DrawCubeV((Vector3){center_x + width * 0.5f - 1.3f, 2.3f, center_z}, (Vector3){2.6f, 4.6f, depth - 3.0f}, FC_VIEWER_COLOR_BASALT_WALL);

    DrawCubeWiresV((Vector3){center_x, 0.11f, center_z}, (Vector3){width - 6.0f, 0.22f, depth}, Fade(FC_VIEWER_COLOR_BASALT_WALL_EDGE, 0.65f));
    fc_draw_floor_cracks(arena_bounds);
}

static void fc_draw_spawn_pad(const fc_scene_rect* rect) {
    float width = (float)(rect->max_tile_x - rect->min_tile_x + 1);
    float depth = (float)(rect->max_tile_y - rect->min_tile_y + 1);
    float center_x = ((float)rect->min_tile_x + (float)rect->max_tile_x) * 0.5f;
    float center_z = -(((float)rect->min_tile_y + (float)rect->max_tile_y) * 0.5f);
    DrawCubeV((Vector3){center_x, 0.21f, center_z}, (Vector3){width, 0.08f, depth}, Fade(FC_VIEWER_COLOR_SPAWN_PAD, 0.55f));
    fc_draw_ellipse_ring(center_x, center_z, width * 0.6f, depth * 0.45f, 0.27f, Fade(FC_VIEWER_COLOR_EMBER, 0.72f));
}

static void fc_draw_prop_asset(const fc_viewer_prop_asset* prop, float time_seconds) {
    Vector3 center = fc_world_position((int)lroundf(prop->tile_x), (int)lroundf(prop->tile_y), prop->base_y);
    center.x = prop->tile_x;
    center.z = -prop->tile_y;
    if (prop->kind_code == FC_VIEWER_PROP_ROCK_SPIRE) {
        DrawCubeV((Vector3){center.x, prop->size_y * 0.35f, center.z}, (Vector3){prop->size_x, prop->size_y * 0.7f, prop->size_z}, FC_VIEWER_COLOR_BASALT_WALL);
        DrawCubeV((Vector3){center.x - 0.3f, prop->size_y * 0.75f, center.z + 0.2f}, (Vector3){prop->size_x * 0.68f, prop->size_y * 0.45f, prop->size_z * 0.68f}, FC_VIEWER_COLOR_BASALT_WALL_EDGE);
        DrawCubeV((Vector3){center.x + 0.15f, prop->size_y * 1.02f, center.z - 0.15f}, (Vector3){prop->size_x * 0.34f, prop->size_y * 0.28f, prop->size_z * 0.34f}, FC_VIEWER_COLOR_EMBER);
    } else if (prop->kind_code == FC_VIEWER_PROP_LAVA_FORGE) {
        float pulse = 0.5f + 0.5f * sinf(time_seconds * 2.4f + prop->tile_x * 0.13f);
        DrawCubeV((Vector3){center.x, 0.45f, center.z}, (Vector3){prop->size_x, 0.9f, prop->size_z}, FC_VIEWER_COLOR_BASALT_WALL);
        DrawCubeV((Vector3){center.x, 0.85f, center.z}, (Vector3){prop->size_x * 0.72f, 0.5f, prop->size_z * 0.72f}, fc_color_lerp(FC_VIEWER_COLOR_LAVA_MID, FC_VIEWER_COLOR_LAVA_HOT, pulse));
        fc_draw_ellipse_ring(center.x, center.z, prop->size_x * 0.38f, prop->size_z * 0.38f, 1.12f, Fade(FC_VIEWER_COLOR_LAVA_RING, 0.92f));
    } else if (prop->kind_code == FC_VIEWER_PROP_BASALT_COLUMN) {
        DrawCubeV((Vector3){center.x, prop->size_y * 0.5f, center.z}, (Vector3){prop->size_x, prop->size_y, prop->size_z}, FC_VIEWER_COLOR_BASALT_WALL_EDGE);
        DrawCubeV((Vector3){center.x, prop->size_y * 0.92f, center.z}, (Vector3){prop->size_x * 1.2f, prop->size_y * 0.14f, prop->size_z * 1.2f}, FC_VIEWER_COLOR_BASALT_FLOOR_HIGHLIGHT);
    } else if (prop->kind_code == FC_VIEWER_PROP_ROCK_ARCH) {
        DrawCubeV((Vector3){center.x - prop->size_x * 0.32f, prop->size_y * 0.45f, center.z}, (Vector3){prop->size_x * 0.22f, prop->size_y * 0.9f, prop->size_z}, FC_VIEWER_COLOR_BASALT_WALL);
        DrawCubeV((Vector3){center.x + prop->size_x * 0.32f, prop->size_y * 0.45f, center.z}, (Vector3){prop->size_x * 0.22f, prop->size_y * 0.9f, prop->size_z}, FC_VIEWER_COLOR_BASALT_WALL);
        DrawCubeV((Vector3){center.x, prop->size_y * 0.82f, center.z}, (Vector3){prop->size_x, prop->size_y * 0.18f, prop->size_z * 0.9f}, FC_VIEWER_COLOR_BASALT_WALL_EDGE);
    }
}

static void fc_compute_scene_bounds(
    const fc_scene_rect* rects,
    int rect_count,
    const fc_runtime_slot_snapshot* snapshot,
    float* out_center_x,
    float* out_center_z
) {
    int min_x = snapshot->tile_x;
    int max_x = snapshot->tile_x;
    int min_y = snapshot->tile_y;
    int max_y = snapshot->tile_y;
    for (int index = 0; index < rect_count; ++index) {
        if (rects[index].min_tile_x < min_x) {
            min_x = rects[index].min_tile_x;
        }
        if (rects[index].max_tile_x > max_x) {
            max_x = rects[index].max_tile_x;
        }
        if (rects[index].min_tile_y < min_y) {
            min_y = rects[index].min_tile_y;
        }
        if (rects[index].max_tile_y > max_y) {
            max_y = rects[index].max_tile_y;
        }
    }
    for (int index = 0; index < snapshot->visible_target_count; ++index) {
        const fc_visible_target* target = &snapshot->visible_targets[index];
        if (target->tile_x < min_x) {
            min_x = target->tile_x;
        }
        if (target->tile_x > max_x) {
            max_x = target->tile_x;
        }
        if (target->tile_y < min_y) {
            min_y = target->tile_y;
        }
        if (target->tile_y > max_y) {
            max_y = target->tile_y;
        }
    }
    *out_center_x = ((float)min_x + (float)max_x) * 0.5f;
    *out_center_z = -(((float)min_y + (float)max_y) * 0.5f);
}

static int fc_parse_int(const char* text, int* out) {
    char* end = NULL;
    long value = strtol(text, &end, 10);
    if (text == NULL || *text == '\0' || end == NULL || *end != '\0') {
        return 0;
    }
    *out = (int)value;
    return 1;
}

static int fc_parse_int64(const char* text, int64_t* out) {
    char* end = NULL;
    long long value = strtoll(text, &end, 10);
    if (text == NULL || *text == '\0' || end == NULL || *end != '\0') {
        return 0;
    }
    *out = (int64_t)value;
    return 1;
}

static void fc_clear_action_feedback(fc_viewer_action_feedback* out) {
    memset(out, 0, sizeof(*out));
    out->resolved_target_npc_index = -1;
    snprintf(out->action_label, sizeof(out->action_label), "none");
}

static void fc_update_action_feedback(
    const fc_native_runtime* runtime,
    int fallback_action_id,
    const char* action_label,
    fc_viewer_action_feedback* out
) {
    int env_count = fc_native_runtime_last_step_env_count(runtime);
    const int* action_ids;
    const int* accepted;
    const int* rejections;
    const int* resolved_targets;
    const int* jad_states;
    const int* jad_outcomes;
    const int* terminal_codes;
    const int* terminated;
    const int* truncated;
    const int* episode_ticks;
    const int* episode_steps;

    fc_clear_action_feedback(out);
    out->present = 1;
    out->action_id = fallback_action_id;
    snprintf(out->action_label, sizeof(out->action_label), "%s", action_label);
    if (env_count <= 0) {
        return;
    }

    action_ids = fc_native_runtime_last_step_action_ids(runtime);
    accepted = fc_native_runtime_last_step_action_accepted(runtime);
    rejections = fc_native_runtime_last_step_rejection_codes(runtime);
    resolved_targets = fc_native_runtime_last_step_resolved_target_npc_indices(runtime);
    jad_states = fc_native_runtime_last_step_jad_telegraph_states(runtime);
    jad_outcomes = fc_native_runtime_last_step_jad_hit_resolve_outcome_codes(runtime);
    terminal_codes = fc_native_runtime_last_step_terminal_codes(runtime);
    terminated = fc_native_runtime_last_step_terminated(runtime);
    truncated = fc_native_runtime_last_step_truncated(runtime);
    episode_ticks = fc_native_runtime_last_step_episode_ticks(runtime);
    episode_steps = fc_native_runtime_last_step_episode_steps(runtime);

    if (action_ids != NULL) {
        out->action_id = action_ids[0];
    }
    if (accepted != NULL) {
        out->accepted = accepted[0];
    }
    if (rejections != NULL) {
        out->rejection_code = rejections[0];
    }
    if (resolved_targets != NULL) {
        out->resolved_target_npc_index = resolved_targets[0];
    }
    if (jad_states != NULL) {
        out->jad_telegraph_state = jad_states[0];
    }
    if (jad_outcomes != NULL) {
        out->jad_hit_resolve_outcome_code = jad_outcomes[0];
    }
    if (terminal_codes != NULL) {
        out->terminal_code = terminal_codes[0];
    }
    if (terminated != NULL) {
        out->terminated = terminated[0];
    }
    if (truncated != NULL) {
        out->truncated = truncated[0];
    }
    if (episode_ticks != NULL) {
        out->episode_tick = episode_ticks[0];
    }
    if (episode_steps != NULL) {
        out->episode_step = episode_steps[0];
    }
}

static int fc_parse_prayer_name(const char* text, int* out_prayer_id) {
    if (strcmp(text, "magic") == 0) {
        *out_prayer_id = FC_VIEWER_PRAYER_MAGIC;
        return 1;
    }
    if (strcmp(text, "missiles") == 0) {
        *out_prayer_id = FC_VIEWER_PRAYER_MISSILES;
        return 1;
    }
    if (strcmp(text, "melee") == 0) {
        *out_prayer_id = FC_VIEWER_PRAYER_MELEE;
        return 1;
    }
    return 0;
}

static const char* fc_action_label_for_packed_words(const int32_t words[4]) {
    switch (words[0]) {
        case FC_VIEWER_ACTION_WAIT: return "wait";
        case FC_VIEWER_ACTION_WALK_TO_TILE: return "walk_to_tile";
        case FC_VIEWER_ACTION_ATTACK_VISIBLE_NPC: return "attack_visible_npc";
        case FC_VIEWER_ACTION_TOGGLE_PROTECTION_PRAYER:
            switch (words[1]) {
                case FC_VIEWER_PRAYER_MAGIC: return "toggle_prayer_magic";
                case FC_VIEWER_PRAYER_MISSILES: return "toggle_prayer_missiles";
                case FC_VIEWER_PRAYER_MELEE: return "toggle_prayer_melee";
                default: return "toggle_prayer_unknown";
            }
        case FC_VIEWER_ACTION_EAT_SHARK: return "eat_shark";
        case FC_VIEWER_ACTION_DRINK_PRAYER_POTION: return "drink_prayer_potion";
        case FC_VIEWER_ACTION_TOGGLE_RUN: return "toggle_run";
        default: return "unknown";
    }
}

static void fc_clear_replay(fc_viewer_replay* replay) {
    free(replay->actions);
    free(replay->frames);
    memset(replay, 0, sizeof(*replay));
    replay->current_frame = 0;
}

static int fc_clamp_replay_frame(const fc_viewer_replay* replay, int frame_index) {
    if (replay->frame_count <= 0) {
        return 0;
    }
    if (frame_index < 0) {
        return 0;
    }
    if (frame_index >= replay->frame_count) {
        return replay->frame_count - 1;
    }
    return frame_index;
}

static int fc_parse_replay_file(const char* path, fc_viewer_replay* out) {
    FILE* file = fopen(path, "r");
    char line[512];
    int saw_schema = 0;

    if (file == NULL) {
        fprintf(stderr, "Could not open replay file: %s\n", path);
        return 0;
    }

    fc_clear_replay(out);
    out->config.seed = 11001;
    out->config.start_wave = 1;
    out->config.ammo = 1000;
    out->config.prayer_potions = 8;
    out->config.sharks = 20;

    while (fgets(line, sizeof(line), file) != NULL) {
        char key[64];
        if (line[0] == '#' || line[0] == '\n' || line[0] == '\0') {
            continue;
        }
        if (sscanf(line, "%63s", key) != 1) {
            continue;
        }
        if (strcmp(key, "schema_id") == 0) {
            char schema_id[128];
            if (sscanf(line, "schema_id %127s", schema_id) != 1) {
                fclose(file);
                fprintf(stderr, "Invalid schema_id line in replay file.\n");
                return 0;
            }
            if (strcmp(schema_id, "fc_native_viewer_replay_v1") != 0) {
                fclose(file);
                fprintf(stderr, "Unsupported replay schema_id: %s\n", schema_id);
                return 0;
            }
            saw_schema = 1;
        } else if (strcmp(key, "schema_version") == 0) {
            int version = 0;
            if (sscanf(line, "schema_version %d", &version) != 1 || version != 1) {
                fclose(file);
                fprintf(stderr, "Unsupported replay schema_version.\n");
                return 0;
            }
        } else if (strcmp(key, "source_kind") == 0) {
            if (sscanf(line, "source_kind %31s", out->source_kind) != 1) {
                fclose(file);
                fprintf(stderr, "Invalid source_kind line in replay file.\n");
                return 0;
            }
        } else if (strcmp(key, "source_ref") == 0) {
            if (sscanf(line, "source_ref %127s", out->source_ref) != 1) {
                fclose(file);
                fprintf(stderr, "Invalid source_ref line in replay file.\n");
                return 0;
            }
        } else if (strcmp(key, "seed") == 0) {
            long long seed = 0;
            if (sscanf(line, "seed %lld", &seed) != 1) {
                fclose(file);
                fprintf(stderr, "Invalid seed line in replay file.\n");
                return 0;
            }
            out->config.seed = (int64_t)seed;
        } else if (strcmp(key, "start_wave") == 0) {
            if (sscanf(line, "start_wave %d", &out->config.start_wave) != 1) {
                fclose(file);
                fprintf(stderr, "Invalid start_wave line in replay file.\n");
                return 0;
            }
        } else if (strcmp(key, "ammo") == 0) {
            if (sscanf(line, "ammo %d", &out->config.ammo) != 1) {
                fclose(file);
                fprintf(stderr, "Invalid ammo line in replay file.\n");
                return 0;
            }
        } else if (strcmp(key, "prayer_potions") == 0) {
            if (sscanf(line, "prayer_potions %d", &out->config.prayer_potions) != 1) {
                fclose(file);
                fprintf(stderr, "Invalid prayer_potions line in replay file.\n");
                return 0;
            }
        } else if (strcmp(key, "sharks") == 0) {
            if (sscanf(line, "sharks %d", &out->config.sharks) != 1) {
                fclose(file);
                fprintf(stderr, "Invalid sharks line in replay file.\n");
                return 0;
            }
        } else if (strcmp(key, "action") == 0) {
            fc_replay_action action;
            fc_replay_action* resized_actions;
            int parsed = sscanf(
                line,
                "action %d %d %d %d %63s",
                &action.words[0],
                &action.words[1],
                &action.words[2],
                &action.words[3],
                action.label
            );
            if (parsed < 4) {
                fclose(file);
                fprintf(stderr, "Invalid action line in replay file.\n");
                return 0;
            }
            if (parsed == 4) {
                snprintf(action.label, sizeof(action.label), "%s", fc_action_label_for_packed_words(action.words));
            }
            resized_actions = (fc_replay_action*)realloc(
                out->actions,
                sizeof(fc_replay_action) * (size_t)(out->action_count + 1)
            );
            if (resized_actions == NULL) {
                fclose(file);
                fprintf(stderr, "Out of memory while loading replay actions.\n");
                return 0;
            }
            out->actions = resized_actions;
            out->actions[out->action_count++] = action;
        }
    }
    fclose(file);

    if (!saw_schema) {
        fprintf(stderr, "Replay file is missing schema_id.\n");
        return 0;
    }
    out->loaded = 1;
    out->frame_count = out->action_count + 1;
    out->current_frame = 0;
    return 1;
}

static int fc_parse_args(int argc, char** argv, fc_viewer_options* out) {
    out->width = FC_VIEWER_WIDTH;
    out->height = FC_VIEWER_HEIGHT;
    out->dump_scene_json = 0;
    out->autoplay = 0;
    out->replay_frame = 0;
    out->step_count = 0;
    out->start_wave = 1;
    out->ammo = 1000;
    out->prayer_potions = 8;
    out->sharks = 20;
    out->seed = 11001;
    out->replay_file = NULL;
    out->trace_name = NULL;
    out->apply_action_count = 0;

    for (int index = 1; index < argc; ++index) {
        const char* arg = argv[index];
        if (strcmp(arg, "--dump-scene-json") == 0) {
            out->dump_scene_json = 1;
            continue;
        }
        if (strcmp(arg, "--autoplay") == 0) {
            out->autoplay = 1;
            continue;
        }
        if (strcmp(arg, "--trace") == 0 && index + 1 < argc) {
            out->trace_name = argv[++index];
            continue;
        }
        if (strcmp(arg, "--replay-file") == 0 && index + 1 < argc) {
            out->replay_file = argv[++index];
            continue;
        }
        if (strcmp(arg, "--replay-frame") == 0 && index + 1 < argc) {
            if (!fc_parse_int(argv[++index], &out->replay_frame)) {
                fprintf(stderr, "Invalid --replay-frame value.\n");
                return 0;
            }
            continue;
        }
        if (strcmp(arg, "--apply-action") == 0 && index + 1 < argc) {
            if (out->apply_action_count >= FC_VIEWER_MAX_SCRIPTED_ACTIONS) {
                fprintf(stderr, "Too many --apply-action values.\n");
                return 0;
            }
            out->apply_actions[out->apply_action_count++] = argv[++index];
            continue;
        }
        if (strcmp(arg, "--step-count") == 0 && index + 1 < argc) {
            if (!fc_parse_int(argv[++index], &out->step_count)) {
                fprintf(stderr, "Invalid --step-count value.\n");
                return 0;
            }
            continue;
        }
        if (strcmp(arg, "--start-wave") == 0 && index + 1 < argc) {
            if (!fc_parse_int(argv[++index], &out->start_wave)) {
                fprintf(stderr, "Invalid --start-wave value.\n");
                return 0;
            }
            continue;
        }
        if (strcmp(arg, "--seed") == 0 && index + 1 < argc) {
            if (!fc_parse_int64(argv[++index], &out->seed)) {
                fprintf(stderr, "Invalid --seed value.\n");
                return 0;
            }
            continue;
        }
        if (strcmp(arg, "--ammo") == 0 && index + 1 < argc) {
            if (!fc_parse_int(argv[++index], &out->ammo)) {
                fprintf(stderr, "Invalid --ammo value.\n");
                return 0;
            }
            continue;
        }
        if (strcmp(arg, "--prayer-potions") == 0 && index + 1 < argc) {
            if (!fc_parse_int(argv[++index], &out->prayer_potions)) {
                fprintf(stderr, "Invalid --prayer-potions value.\n");
                return 0;
            }
            continue;
        }
        if (strcmp(arg, "--sharks") == 0 && index + 1 < argc) {
            if (!fc_parse_int(argv[++index], &out->sharks)) {
                fprintf(stderr, "Invalid --sharks value.\n");
                return 0;
            }
            continue;
        }
        if (strcmp(arg, "--width") == 0 && index + 1 < argc) {
            if (!fc_parse_int(argv[++index], &out->width)) {
                fprintf(stderr, "Invalid --width value.\n");
                return 0;
            }
            continue;
        }
        if (strcmp(arg, "--height") == 0 && index + 1 < argc) {
            if (!fc_parse_int(argv[++index], &out->height)) {
                fprintf(stderr, "Invalid --height value.\n");
                return 0;
            }
            continue;
        }
        if (strcmp(arg, "--help") == 0 || strcmp(arg, "-h") == 0) {
            printf(
                "Fight Caves native debug viewer\n"
                "  --dump-scene-json        export the current backend scene and snapshot as JSON\n"
                "  --replay-file PATH       load a backend-driven replay file exported for the viewer\n"
                "  --replay-frame N         select replay frame N for dump/export or initial window state\n"
                "  --trace NAME             load one of: single_wave, full_run, jad_telegraph,\n"
                "                           jad_prayer_protected, jad_prayer_too_late, jad_healer, tzkek_split\n"
                "  --step-count N           apply N backend wait-steps before rendering/export\n"
                "  --apply-action SPEC      apply one scripted backend action before rendering/export;\n"
                "                           SPEC is one of wait, eat, drink, run, prayer:magic,\n"
                "                           prayer:missiles, prayer:melee, attack-visible:N,\n"
                "                           move:X:Y or move:X:Y:L\n"
                "  --start-wave N           generic reset start wave (default 1)\n"
                "  --seed N                 episode seed (default 11001)\n"
                "  --ammo N                 reset ammo count (default 1000)\n"
                "  --prayer-potions N       reset prayer potion count (default 8)\n"
                "  --sharks N               reset shark count (default 20)\n"
                "  --autoplay               start the windowed viewer unpaused\n"
                "  --width N                window width (default 1440)\n"
                "  --height N               window height (default 960)\n"
            );
            return 2;
        }
        fprintf(stderr, "Unknown argument: %s\n", arg);
        return 0;
    }
    if (out->start_wave < 1 || out->start_wave > 63) {
        fprintf(stderr, "--start-wave must be in range 1..63\n");
        return 0;
    }
    if (out->trace_name != NULL && fc_trace_id_for_name(out->trace_name) <= 0) {
        fprintf(stderr, "Unsupported --trace value: %s\n", out->trace_name);
        return 0;
    }
    if (out->replay_file != NULL) {
        if (out->trace_name != NULL) {
            fprintf(stderr, "--replay-file cannot be combined with --trace.\n");
            return 0;
        }
        if (out->apply_action_count > 0) {
            fprintf(stderr, "--replay-file cannot be combined with --apply-action.\n");
            return 0;
        }
        if (out->step_count > 0) {
            fprintf(stderr, "--replay-file cannot be combined with --step-count.\n");
            return 0;
        }
    }
    return 1;
}

static int fc_prepare_runtime(fc_native_runtime* runtime, const fc_viewer_options* options) {
    fc_episode_config config;
    memset(&config, 0, sizeof(config));
    config.seed = options->seed;
    config.start_wave = options->start_wave;
    config.ammo = options->ammo;
    config.prayer_potions = options->prayer_potions;
    config.sharks = options->sharks;
    if (!fc_native_runtime_reset_batch(runtime, &config, 1)) {
        fprintf(stderr, "%s\n", fc_native_runtime_last_error());
        return 0;
    }
    if (options->trace_name != NULL) {
        int trace_id = fc_trace_id_for_name(options->trace_name);
        if (!fc_native_runtime_debug_load_core_trace(runtime, 0, trace_id)) {
            fprintf(stderr, "%s\n", fc_native_runtime_last_error());
            return 0;
        }
    }
    return 1;
}

static int fc_prepare_runtime_from_config(
    fc_native_runtime* runtime,
    const fc_episode_config* config
) {
    if (!fc_native_runtime_reset_batch(runtime, config, 1)) {
        fprintf(stderr, "%s\n", fc_native_runtime_last_error());
        return 0;
    }
    return 1;
}

static int fc_export_snapshot(fc_native_runtime* runtime, fc_runtime_slot_snapshot* out) {
    if (!fc_native_runtime_export_slot_snapshot(runtime, 0, out)) {
        fprintf(stderr, "%s\n", fc_native_runtime_last_error());
        return 0;
    }
    return 1;
}

static int fc_apply_packed_action(
    fc_native_runtime* runtime,
    const int32_t packed_action[4],
    int action_id,
    const char* action_label,
    fc_runtime_slot_snapshot* snapshot,
    fc_viewer_action_feedback* feedback
) {
    const int slot_index = 0;
    if (!fc_native_runtime_step_batch(runtime, &slot_index, packed_action, 1)) {
        fprintf(stderr, "%s\n", fc_native_runtime_last_error());
        return 0;
    }
    fc_update_action_feedback(runtime, action_id, action_label, feedback);
    if (!fc_export_snapshot(runtime, snapshot)) {
        return 0;
    }
    return 1;
}

static int fc_apply_wait_steps(
    fc_native_runtime* runtime,
    int step_count,
    fc_runtime_slot_snapshot* snapshot,
    fc_viewer_action_feedback* feedback
) {
    const int32_t packed_action[4] = {FC_VIEWER_ACTION_WAIT, 0, 0, 0};
    for (int index = 0; index < step_count; ++index) {
        if (!fc_apply_packed_action(
                runtime,
                packed_action,
                FC_VIEWER_ACTION_WAIT,
                "wait",
                snapshot,
                feedback
            )) {
            return 0;
        }
    }
    return 1;
}

static int fc_apply_move_action(
    fc_native_runtime* runtime,
    int tile_x,
    int tile_y,
    int tile_level,
    fc_runtime_slot_snapshot* snapshot,
    fc_viewer_action_feedback* feedback
) {
    const int32_t packed_action[4] = {
        FC_VIEWER_ACTION_WALK_TO_TILE,
        tile_x,
        tile_y,
        tile_level,
    };
    return fc_apply_packed_action(
        runtime,
        packed_action,
        FC_VIEWER_ACTION_WALK_TO_TILE,
        "walk_to_tile",
        snapshot,
        feedback
    );
}

static int fc_apply_attack_action(
    fc_native_runtime* runtime,
    int visible_index,
    fc_runtime_slot_snapshot* snapshot,
    fc_viewer_action_feedback* feedback
) {
    const int32_t packed_action[4] = {
        FC_VIEWER_ACTION_ATTACK_VISIBLE_NPC,
        visible_index,
        0,
        0,
    };
    return fc_apply_packed_action(
        runtime,
        packed_action,
        FC_VIEWER_ACTION_ATTACK_VISIBLE_NPC,
        "attack_visible_npc",
        snapshot,
        feedback
    );
}

static int fc_apply_prayer_action(
    fc_native_runtime* runtime,
    int prayer_id,
    fc_runtime_slot_snapshot* snapshot,
    fc_viewer_action_feedback* feedback
) {
    int32_t packed_action[4] = {
        FC_VIEWER_ACTION_TOGGLE_PROTECTION_PRAYER,
        prayer_id,
        0,
        0,
    };
    char action_label[FC_VIEWER_ACTION_LABEL_SIZE];
    snprintf(action_label, sizeof(action_label), "toggle_prayer_%s", fc_prayer_label(prayer_id));
    return fc_apply_packed_action(
        runtime,
        packed_action,
        FC_VIEWER_ACTION_TOGGLE_PROTECTION_PRAYER,
        action_label,
        snapshot,
        feedback
    );
}

static int fc_apply_simple_action(
    fc_native_runtime* runtime,
    int action_id,
    const char* action_label,
    fc_runtime_slot_snapshot* snapshot,
    fc_viewer_action_feedback* feedback
) {
    const int32_t packed_action[4] = {action_id, 0, 0, 0};
    return fc_apply_packed_action(runtime, packed_action, action_id, action_label, snapshot, feedback);
}

static int fc_apply_scripted_action(
    fc_native_runtime* runtime,
    const char* spec,
    fc_runtime_slot_snapshot* snapshot,
    fc_viewer_action_feedback* feedback,
    int* selected_npc_index
) {
    if (strcmp(spec, "wait") == 0) {
        return fc_apply_simple_action(runtime, FC_VIEWER_ACTION_WAIT, "wait", snapshot, feedback);
    }
    if (strcmp(spec, "eat") == 0) {
        return fc_apply_simple_action(runtime, FC_VIEWER_ACTION_EAT_SHARK, "eat_shark", snapshot, feedback);
    }
    if (strcmp(spec, "drink") == 0) {
        return fc_apply_simple_action(
            runtime,
            FC_VIEWER_ACTION_DRINK_PRAYER_POTION,
            "drink_prayer_potion",
            snapshot,
            feedback
        );
    }
    if (strcmp(spec, "run") == 0) {
        return fc_apply_simple_action(runtime, FC_VIEWER_ACTION_TOGGLE_RUN, "toggle_run", snapshot, feedback);
    }
    if (strncmp(spec, "prayer:", 7) == 0) {
        int prayer_id = 0;
        if (!fc_parse_prayer_name(spec + 7, &prayer_id)) {
            fprintf(stderr, "Unsupported scripted prayer action: %s\n", spec);
            return 0;
        }
        return fc_apply_prayer_action(runtime, prayer_id, snapshot, feedback);
    }
    if (strncmp(spec, "attack-visible:", 15) == 0) {
        int visible_index = 0;
        if (!fc_parse_int(spec + 15, &visible_index)) {
            fprintf(stderr, "Invalid attack-visible action: %s\n", spec);
            return 0;
        }
        if (visible_index >= 0 && visible_index < snapshot->visible_target_count) {
            *selected_npc_index = snapshot->visible_targets[visible_index].npc_index;
        }
        return fc_apply_attack_action(runtime, visible_index, snapshot, feedback);
    }
    if (strncmp(spec, "move:", 5) == 0) {
        int tile_x = 0;
        int tile_y = 0;
        int tile_level = snapshot->tile_level;
        int parsed = sscanf(spec + 5, "%d:%d:%d", &tile_x, &tile_y, &tile_level);
        if (parsed == 2 || parsed == 3) {
            return fc_apply_move_action(runtime, tile_x, tile_y, tile_level, snapshot, feedback);
        }
        fprintf(stderr, "Invalid move action: %s\n", spec);
        return 0;
    }
    fprintf(stderr, "Unsupported scripted action: %s\n", spec);
    return 0;
}

static int fc_apply_scripted_actions(
    fc_native_runtime* runtime,
    const fc_viewer_options* options,
    fc_runtime_slot_snapshot* snapshot,
    fc_viewer_action_feedback* feedback,
    int* selected_npc_index
) {
    for (int index = 0; index < options->apply_action_count; ++index) {
        if (!fc_apply_scripted_action(
                runtime,
                options->apply_actions[index],
                snapshot,
                feedback,
                selected_npc_index
            )) {
            return 0;
        }
    }
    return 1;
}

static int fc_build_replay_frames(
    fc_native_runtime* runtime,
    fc_viewer_replay* replay
) {
    fc_runtime_slot_snapshot snapshot;
    fc_viewer_action_feedback feedback;
    int selected_npc_index = -1;

    free(replay->frames);
    replay->frames = NULL;
    replay->frame_count = replay->action_count + 1;
    replay->frames = (fc_replay_frame*)calloc((size_t)replay->frame_count, sizeof(fc_replay_frame));
    if (replay->frames == NULL) {
        fprintf(stderr, "Out of memory while building replay frames.\n");
        return 0;
    }

    fc_clear_action_feedback(&feedback);
    if (!fc_prepare_runtime_from_config(runtime, &replay->config)) {
        return 0;
    }
    if (!fc_export_snapshot(runtime, &snapshot)) {
        return 0;
    }
    replay->frames[0].snapshot = snapshot;
    replay->frames[0].feedback = feedback;
    replay->frames[0].selected_npc_index = -1;

    for (int index = 0; index < replay->action_count; ++index) {
        const fc_replay_action* action = &replay->actions[index];
        if (!fc_apply_packed_action(
                runtime,
                action->words,
                action->words[0],
                action->label,
                &snapshot,
                &feedback
            )) {
            return 0;
        }
        if (feedback.resolved_target_npc_index >= 0) {
            selected_npc_index = feedback.resolved_target_npc_index;
        }
        if (selected_npc_index >= 0 && fc_find_visible_target_index(&snapshot, selected_npc_index) < 0) {
            selected_npc_index = -1;
        }
        replay->frames[index + 1].snapshot = snapshot;
        replay->frames[index + 1].feedback = feedback;
        replay->frames[index + 1].selected_npc_index = selected_npc_index;
    }
    replay->current_frame = fc_clamp_replay_frame(replay, replay->current_frame);
    return 1;
}

static void fc_load_replay_frame(
    const fc_viewer_replay* replay,
    fc_runtime_slot_snapshot* snapshot,
    fc_viewer_action_feedback* feedback,
    int* selected_npc_index
) {
    int frame_index = fc_clamp_replay_frame(replay, replay->current_frame);
    *snapshot = replay->frames[frame_index].snapshot;
    *feedback = replay->frames[frame_index].feedback;
    *selected_npc_index = replay->frames[frame_index].selected_npc_index;
}

static int fc_find_visible_target_index(
    const fc_runtime_slot_snapshot* snapshot,
    int npc_index
) {
    for (int index = 0; index < snapshot->visible_target_count; ++index) {
        if (snapshot->visible_targets[index].npc_index == npc_index) {
            return index;
        }
    }
    return -1;
}

static void fc_print_scene_json(
    const fc_viewer_options* options,
    const fc_runtime_slot_snapshot* snapshot,
    const fc_viewer_action_feedback* feedback,
    int selected_npc_index,
    const fc_viewer_replay* replay
) {
    const fc_scene_rect* rects = fc_native_runtime_scene_rects();
    int rect_count = fc_native_runtime_scene_rect_count();
    printf("{\n");
    printf("  \"schema_id\": \"fc_native_debug_viewer_scene_v1\",\n");
    if (options->trace_name != NULL) {
        printf("  \"trace_id\": \"%s\",\n", options->trace_name);
    } else {
        printf("  \"trace_id\": null,\n");
    }
    printf("  \"scene\": {\n");
    printf("    \"rect_count\": %d,\n", rect_count);
    printf("    \"rects\": [\n");
    for (int index = 0; index < rect_count; ++index) {
        const fc_scene_rect* rect = &rects[index];
        printf(
            "      {\"kind_code\": %d, \"label\": \"%s\", \"min_tile_x\": %d, \"max_tile_x\": %d, \"min_tile_y\": %d, \"max_tile_y\": %d, \"tile_level\": %d}%s\n",
            rect->kind_code,
            fc_scene_rect_label(rect->kind_code),
            rect->min_tile_x,
            rect->max_tile_x,
            rect->min_tile_y,
            rect->max_tile_y,
            rect->tile_level,
            index + 1 == rect_count ? "" : ","
        );
    }
    printf("    ]\n");
    printf("  },\n");
    printf("  \"snapshot\": {\n");
    printf("    \"slot_index\": %d,\n", snapshot->slot_index);
    printf("    \"seed\": %" PRId64 ",\n", snapshot->seed);
    printf("    \"wave\": %d,\n", snapshot->wave);
    printf("    \"rotation\": %d,\n", snapshot->rotation);
    printf("    \"remaining\": %d,\n", snapshot->remaining);
    printf("    \"tick\": %d,\n", snapshot->tick);
    printf("    \"steps\": %d,\n", snapshot->steps);
    printf("    \"terminal_code\": %d,\n", snapshot->terminal_code);
    printf("    \"terminal_label\": \"%s\",\n", fc_terminal_label(snapshot->terminal_code));
    printf("    \"terminated\": %d,\n", snapshot->terminated);
    printf("    \"truncated\": %d,\n", snapshot->truncated);
    printf("    \"player\": {\"tile_x\": %d, \"tile_y\": %d, \"tile_level\": %d, \"hitpoints_current\": %d, \"hitpoints_max\": %d, \"prayer_current\": %d, \"prayer_max\": %d, \"run_energy\": %d, \"run_energy_max\": %d, \"running\": %d, \"protect_from_magic\": %d, \"protect_from_missiles\": %d, \"protect_from_melee\": %d},\n",
        snapshot->tile_x,
        snapshot->tile_y,
        snapshot->tile_level,
        snapshot->hitpoints_current,
        snapshot->hitpoints_max,
        snapshot->prayer_current,
        snapshot->prayer_max,
        snapshot->run_energy,
        snapshot->run_energy_max,
        snapshot->running,
        snapshot->protect_from_magic,
        snapshot->protect_from_missiles,
        snapshot->protect_from_melee
    );
    printf("    \"inventory\": {\"ammo\": %d, \"prayer_potion_dose_count\": %d, \"sharks\": %d},\n",
        snapshot->ammo,
        snapshot->prayer_potion_dose_count,
        snapshot->sharks
    );
    printf("    \"jad_hit_resolve_outcome\": \"%s\",\n", fc_jad_outcome_label(snapshot->jad_hit_resolve_outcome_code));
    printf("    \"visible_target_count\": %d,\n", snapshot->visible_target_count);
    printf("    \"npcs\": [\n");
    for (int index = 0; index < snapshot->visible_target_count; ++index) {
        const fc_visible_target* target = &snapshot->visible_targets[index];
        printf(
            "      {\"visible_index\": %d, \"npc_index\": %d, \"id_code\": %d, \"tile_x\": %d, \"tile_y\": %d, \"tile_level\": %d, \"hitpoints_current\": %d, \"hitpoints_max\": %d, \"currently_visible\": %d, \"hidden\": %d, \"dead\": %d, \"under_attack\": %d, \"jad_telegraph_state\": %d}%s\n",
            index,
            target->npc_index,
            target->id_code,
            target->tile_x,
            target->tile_y,
            target->tile_level,
            target->hitpoints_current,
            target->hitpoints_max,
            target->currently_visible,
            target->hidden,
            target->dead,
            target->under_attack,
            target->jad_telegraph_state,
            index + 1 == snapshot->visible_target_count ? "" : ","
        );
    }
    printf("    ]\n");
    printf("  },\n");
    printf("  \"replay\": {\n");
    printf("    \"loaded\": %d,\n", replay->loaded);
    printf("    \"source_kind\": \"%s\",\n", replay->loaded ? replay->source_kind : "none");
    printf("    \"source_ref\": \"%s\",\n", replay->loaded ? replay->source_ref : "none");
    printf("    \"frame_index\": %d,\n", replay->loaded ? replay->current_frame : 0);
    printf("    \"frame_count\": %d\n", replay->loaded ? replay->frame_count : 0);
    printf("  },\n");
    printf("  \"assets\": {\n");
    printf("    \"asset_id\": \"%s\",\n", FC_VIEWER_ASSET_BUNDLE_ID);
    printf("    \"source_ref_count\": %d,\n", FC_VIEWER_ASSET_SOURCE_REF_COUNT);
    printf("    \"terrain_tile_count\": %d,\n", FC_VIEWER_TERRAIN_TILE_COUNT);
    printf("    \"hud_sprite_count\": %d,\n", FC_VIEWER_HUD_SPRITE_COUNT);
    printf("    \"blocked_category_count\": %d,\n", FC_VIEWER_BLOCKED_CATEGORY_COUNT);
    printf("    \"map_archive_accessible\": %d,\n", FC_VIEWER_MAP_ARCHIVE_ACCESSIBLE);
    printf("    \"object_archive_accessible\": %d\n", FC_VIEWER_OBJECT_ARCHIVE_ACCESSIBLE);
    printf("  },\n");
    printf("  \"viewer\": {\n");
    printf("    \"selected_npc_index\": %d,\n", selected_npc_index);
    printf("    \"selected_visible_index\": %d,\n", fc_find_visible_target_index(snapshot, selected_npc_index));
    printf("    \"last_action\": {\n");
    printf("      \"present\": %d,\n", feedback->present);
    printf("      \"action_id\": %d,\n", feedback->action_id);
    printf("      \"action_label\": \"%s\",\n", feedback->action_label);
    printf("      \"accepted\": %d,\n", feedback->accepted);
    printf("      \"rejection_code\": %d,\n", feedback->rejection_code);
    printf("      \"rejection_label\": \"%s\",\n", fc_rejection_label(feedback->rejection_code));
    printf("      \"resolved_target_npc_index\": %d,\n", feedback->resolved_target_npc_index);
    printf("      \"jad_telegraph_state\": %d,\n", feedback->jad_telegraph_state);
    printf("      \"jad_hit_resolve_outcome\": \"%s\",\n", fc_jad_outcome_label(feedback->jad_hit_resolve_outcome_code));
    printf("      \"terminal_label\": \"%s\",\n", fc_terminal_label(feedback->terminal_code));
    printf("      \"terminated\": %d,\n", feedback->terminated);
    printf("      \"truncated\": %d,\n", feedback->truncated);
    printf("      \"tick\": %d,\n", feedback->episode_tick);
    printf("      \"step\": %d\n", feedback->episode_step);
    printf("    }\n");
    printf("  }\n");
    printf("}\n");
}

static void fc_draw_scene_rects(const fc_scene_rect* rects, int rect_count, float time_seconds) {
    const fc_scene_rect* player_dais = fc_find_scene_rect(rects, rect_count, FC_NATIVE_SCENE_RECT_PLAYER_DAIS);
    fc_draw_generated_scene_slice(time_seconds);
    for (int index = 0; index < rect_count; ++index) {
        const fc_scene_rect* rect = &rects[index];
        if (rect->kind_code == FC_NATIVE_SCENE_RECT_ARENA_BOUNDS) {
            continue;
        }
        if (rect->kind_code == FC_NATIVE_SCENE_RECT_PLAYER_DAIS) {
            float width = (float)(rect->max_tile_x - rect->min_tile_x + 1);
            float depth = (float)(rect->max_tile_y - rect->min_tile_y + 1);
            float center_x = ((float)rect->min_tile_x + (float)rect->max_tile_x) * 0.5f;
            float center_z = -(((float)rect->min_tile_y + (float)rect->max_tile_y) * 0.5f);
            DrawCubeV((Vector3){center_x, 0.52f, center_z}, (Vector3){width + 1.0f, 0.68f, depth + 1.0f}, FC_VIEWER_COLOR_DAIS);
            fc_draw_ellipse_ring(center_x, center_z, width * 0.72f, depth * 0.72f, 0.92f, Fade(FC_VIEWER_COLOR_DAIS_GLOW, 0.95f));
            DrawCubeWiresV((Vector3){center_x, 0.54f, center_z}, (Vector3){width + 1.0f, 0.68f, depth + 1.0f}, FC_VIEWER_COLOR_BASALT_WALL_EDGE);
            continue;
        }
        fc_draw_spawn_pad(rect);
    }
    if (player_dais != NULL) {
        float center_x = ((float)player_dais->min_tile_x + (float)player_dais->max_tile_x) * 0.5f;
        float center_z = -(((float)player_dais->min_tile_y + (float)player_dais->max_tile_y) * 0.5f);
        fc_draw_ellipse_ring(center_x, center_z, 6.2f, 6.2f, 0.44f, Fade(FC_VIEWER_COLOR_LAVA_RING_DIM, 0.50f));
    }
}

static void fc_draw_snapshot_entities(
    const fc_runtime_slot_snapshot* snapshot,
    fc_hover_state hovered,
    int selected_npc_index,
    fc_ground_hover_state hovered_ground
) {
    Vector3 player_position = fc_world_position(snapshot->tile_x, snapshot->tile_y, 0.74f);
    DrawCubeV((Vector3){player_position.x, player_position.y, player_position.z}, (Vector3){0.95f, 1.25f, 0.85f}, FC_VIEWER_COLOR_PLAYER_ACCENT);
    DrawCubeV((Vector3){player_position.x, player_position.y + 0.18f, player_position.z}, (Vector3){0.62f, 1.28f, 0.54f}, FC_VIEWER_COLOR_PLAYER_BASE);
    DrawSphere((Vector3){player_position.x, player_position.y + 0.92f, player_position.z}, 0.34f, FC_VIEWER_COLOR_PLAYER_BASE);
    DrawCubeWiresV((Vector3){player_position.x, player_position.y, player_position.z}, (Vector3){0.95f, 1.25f, 0.85f}, Fade(BLACK, 0.7f));
    fc_draw_ellipse_ring(player_position.x, player_position.z, 0.78f, 0.58f, 0.16f, Fade(FC_VIEWER_COLOR_PLAYER_ACCENT, 0.92f));
    if (hovered.kind == FC_HOVER_PLAYER) {
        fc_draw_ellipse_ring(player_position.x, player_position.z, 1.08f, 0.88f, 0.18f, FC_VIEWER_COLOR_HOVER);
    }

    for (int index = 0; index < snapshot->visible_target_count; ++index) {
        const fc_visible_target* target = &snapshot->visible_targets[index];
        Color color = fc_npc_color(target->id_code, target->jad_telegraph_state);
        Vector3 npc_position = fc_world_position(target->tile_x, target->tile_y, target->id_code == 11 ? 1.55f : 0.72f);
        float scale = 1.0f;
        if (target->id_code == 11) {
            scale = 3.0f;
        } else if (target->id_code == 5) {
            scale = 0.82f;
        } else if (target->id_code == 1) {
            scale = 0.68f;
        } else if (target->id_code == 3) {
            scale = 1.35f;
        }
        DrawCubeV(npc_position, (Vector3){0.9f * scale, 1.1f * scale, 0.9f * scale}, Fade(color, target->currently_visible ? 0.94f : 0.38f));
        DrawSphere((Vector3){npc_position.x, npc_position.y + 0.55f * scale, npc_position.z}, 0.28f * scale, fc_color_lerp(color, FC_VIEWER_COLOR_LAVA_HOT, 0.25f));
        DrawCubeWiresV(npc_position, (Vector3){0.9f * scale, 1.1f * scale, 0.9f * scale}, Fade(BLACK, 0.82f));
        if (target->id_code == 11) {
            DrawCubeV((Vector3){npc_position.x - 0.95f, npc_position.y - 0.75f, npc_position.z + 0.35f}, (Vector3){0.45f, 1.25f, 0.45f}, color);
            DrawCubeV((Vector3){npc_position.x + 0.95f, npc_position.y - 0.75f, npc_position.z - 0.35f}, (Vector3){0.45f, 1.25f, 0.45f}, color);
            if (target->jad_telegraph_state > 0) {
                fc_draw_ellipse_ring(npc_position.x, npc_position.z, 2.3f, 1.6f, 0.22f, Fade(FC_VIEWER_COLOR_NPC_JAD_TELEGRAPH, 0.96f));
            }
        }
        if (target->under_attack) {
            fc_draw_ellipse_ring(npc_position.x, npc_position.z, 0.95f * scale, 0.75f * scale, 0.18f, GOLD);
        }
        if (hovered.kind == FC_HOVER_NPC && hovered.visible_index == index) {
            fc_draw_ellipse_ring(npc_position.x, npc_position.z, 1.18f * scale, 0.98f * scale, 0.2f, FC_VIEWER_COLOR_HOVER);
        }
        if (selected_npc_index >= 0 && target->npc_index == selected_npc_index) {
            fc_draw_ellipse_ring(npc_position.x, npc_position.z, 1.42f * scale, 1.14f * scale, 0.22f, FC_VIEWER_COLOR_SELECTION);
        }
    }

    if (hovered_ground.valid) {
        Vector3 tile_position = fc_world_position(hovered_ground.tile_x, hovered_ground.tile_y, 0.02f);
        DrawCubeWiresV(tile_position, (Vector3){1.0f, 0.05f, 1.0f}, FC_VIEWER_COLOR_HOVER);
    }
}

static void fc_draw_entity_inspector_overlays(
    const Camera3D* camera,
    const fc_runtime_slot_snapshot* snapshot,
    int selected_npc_index,
    int overlays_enabled
) {
    char line[160];
    Vector2 player_screen;

    if (!overlays_enabled) {
        return;
    }

    player_screen = GetWorldToScreen(
        fc_world_position(snapshot->tile_x, snapshot->tile_y, 1.4f),
        *camera
    );
    snprintf(
        line,
        sizeof(line),
        "player hp=%d/%d pray=%d/%d run=%d action_lock=%d",
        snapshot->hitpoints_current,
        snapshot->hitpoints_max,
        snapshot->prayer_current,
        snapshot->prayer_max,
        snapshot->run_energy,
        snapshot->attack_locked
    );
    DrawText(line, (int)player_screen.x - 90, (int)player_screen.y - 26, 14, SKYBLUE);

    for (int index = 0; index < snapshot->visible_target_count; ++index) {
        const fc_visible_target* target = &snapshot->visible_targets[index];
        Vector2 screen = GetWorldToScreen(
            fc_world_position(target->tile_x, target->tile_y, 1.3f),
            *camera
        );
        Color text_color = selected_npc_index == target->npc_index ? GREEN : RAYWHITE;
        snprintf(
            line,
            sizeof(line),
            "v%d npc#%d id=%d hp=%d/%d tgt=%d vis=%d dead=%d tele=%d",
            index,
            target->npc_index,
            target->id_code,
            target->hitpoints_current,
            target->hitpoints_max,
            target->currently_visible && !target->hidden && !target->dead,
            target->currently_visible,
            target->dead,
            target->jad_telegraph_state
        );
        DrawText(line, (int)screen.x - 96, (int)screen.y - 24, 13, text_color);
    }
}

static fc_hover_state fc_pick_hovered_entity(
    const Camera3D* camera,
    const fc_runtime_slot_snapshot* snapshot
) {
    fc_hover_state hovered;
    Vector2 mouse;
    float best_distance = 28.0f;
    hovered.kind = FC_HOVER_NONE;
    hovered.npc_index = -1;
    hovered.visible_index = -1;
    mouse = GetMousePosition();

    Vector2 player_screen = GetWorldToScreen(fc_world_position(snapshot->tile_x, snapshot->tile_y, 0.75f), *camera);
    float player_distance = Vector2Distance(mouse, player_screen);
    if (player_distance < best_distance) {
        best_distance = player_distance;
        hovered.kind = FC_HOVER_PLAYER;
    }

    for (int index = 0; index < snapshot->visible_target_count; ++index) {
        Vector2 npc_screen = GetWorldToScreen(
            fc_world_position(snapshot->visible_targets[index].tile_x, snapshot->visible_targets[index].tile_y, 0.55f),
            *camera
        );
        float distance = Vector2Distance(mouse, npc_screen);
        if (distance < best_distance) {
            best_distance = distance;
            hovered.kind = FC_HOVER_NPC;
            hovered.npc_index = snapshot->visible_targets[index].npc_index;
            hovered.visible_index = index;
        }
    }
    return hovered;
}

static fc_ground_hover_state fc_pick_ground_tile(
    const Camera3D* camera,
    const fc_runtime_slot_snapshot* snapshot
) {
    fc_ground_hover_state ground;
    Ray ray = GetMouseRay(GetMousePosition(), *camera);
    float distance = 0.0f;
    Vector3 hit;

    memset(&ground, 0, sizeof(ground));
    if (fabsf(ray.direction.y) < 0.0001f) {
        return ground;
    }
    distance = -ray.position.y / ray.direction.y;
    if (distance <= 0.0f) {
        return ground;
    }
    hit = Vector3Add(ray.position, Vector3Scale(ray.direction, distance));
    ground.valid = 1;
    ground.tile_x = (int)lroundf(hit.x);
    ground.tile_y = (int)lroundf(-hit.z);
    ground.tile_level = snapshot->tile_level;
    return ground;
}

static fc_hud_layout fc_make_hud_layout(void) {
    fc_hud_layout layout;
    layout.panel = (Rectangle){16.0f, 16.0f, 470.0f, 556.0f};
    layout.shark_button = (Rectangle){32.0f, 404.0f, 94.0f, 34.0f};
    layout.potion_button = (Rectangle){138.0f, 404.0f, 128.0f, 34.0f};
    layout.prayer_magic_button = (Rectangle){32.0f, 446.0f, 102.0f, 34.0f};
    layout.prayer_missiles_button = (Rectangle){146.0f, 446.0f, 102.0f, 34.0f};
    layout.prayer_melee_button = (Rectangle){260.0f, 446.0f, 102.0f, 34.0f};
    layout.run_button = (Rectangle){374.0f, 446.0f, 94.0f, 34.0f};
    layout.replay_scrub_bar = (Rectangle){32.0f, 494.0f, 436.0f, 20.0f};
    return layout;
}

static void fc_draw_button(
    Rectangle rect,
    const char* label,
    const char* value,
    Color fill,
    int active,
    int hovered
) {
    Color outline = active ? GOLD : Fade(RAYWHITE, 0.55f);
    Color background = hovered ? Fade(fill, 0.98f) : Fade(fill, 0.86f);
    DrawRectangleRounded(rect, 0.15f, 8, background);
    DrawRectangleRoundedLinesEx(rect, 0.15f, 8, 2.0f, outline);
    DrawText(label, (int)rect.x + 10, (int)rect.y + 6, 18, RAYWHITE);
    DrawText(value, (int)rect.x + 10, (int)rect.y + 19, 13, Fade(RAYWHITE, 0.82f));
}

static int fc_try_handle_hud_click(
    Vector2 mouse,
    const fc_hud_layout* layout,
    fc_native_runtime* runtime,
    fc_runtime_slot_snapshot* snapshot,
    fc_viewer_action_feedback* feedback
) {
    if (!IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
        return 0;
    }
    if (!CheckCollisionPointRec(mouse, layout->panel)) {
        return 0;
    }
    if (CheckCollisionPointRec(mouse, layout->shark_button)) {
        return fc_apply_simple_action(runtime, FC_VIEWER_ACTION_EAT_SHARK, "eat_shark", snapshot, feedback) ? 1 : -1;
    }
    if (CheckCollisionPointRec(mouse, layout->potion_button)) {
        return fc_apply_simple_action(
            runtime,
            FC_VIEWER_ACTION_DRINK_PRAYER_POTION,
            "drink_prayer_potion",
            snapshot,
            feedback
        ) ? 1 : -1;
    }
    if (CheckCollisionPointRec(mouse, layout->prayer_magic_button)) {
        return fc_apply_prayer_action(runtime, FC_VIEWER_PRAYER_MAGIC, snapshot, feedback) ? 1 : -1;
    }
    if (CheckCollisionPointRec(mouse, layout->prayer_missiles_button)) {
        return fc_apply_prayer_action(runtime, FC_VIEWER_PRAYER_MISSILES, snapshot, feedback) ? 1 : -1;
    }
    if (CheckCollisionPointRec(mouse, layout->prayer_melee_button)) {
        return fc_apply_prayer_action(runtime, FC_VIEWER_PRAYER_MELEE, snapshot, feedback) ? 1 : -1;
    }
    if (CheckCollisionPointRec(mouse, layout->run_button)) {
        return fc_apply_simple_action(runtime, FC_VIEWER_ACTION_TOGGLE_RUN, "toggle_run", snapshot, feedback) ? 1 : -1;
    }
    return 1;
}

static int fc_try_handle_replay_scrub_click(
    Vector2 mouse,
    const fc_hud_layout* layout,
    fc_viewer_replay* replay
) {
    float normalized = 0.0f;
    if (!replay->loaded || replay->frame_count <= 0) {
        return 0;
    }
    if (!IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
        return 0;
    }
    if (!CheckCollisionPointRec(mouse, layout->replay_scrub_bar)) {
        return 0;
    }
    normalized = (mouse.x - layout->replay_scrub_bar.x) / layout->replay_scrub_bar.width;
    if (normalized < 0.0f) {
        normalized = 0.0f;
    }
    if (normalized > 1.0f) {
        normalized = 1.0f;
    }
    replay->current_frame = fc_clamp_replay_frame(
        replay,
        (int)lroundf(normalized * (float)(replay->frame_count - 1))
    );
    replay->playing = 0;
    return 1;
}

static void fc_draw_wave_digits(
    int wave,
    int x,
    int y,
    const fc_viewer_hud_textures* textures
) {
    char buffer[16];
    int length = snprintf(buffer, sizeof(buffer), "%d", wave);
    int draw_x = x;
    if (length < 1) {
        return;
    }
    for (int index = 0; index < length; ++index) {
        int digit = buffer[index] - '0';
        if (digit >= 0 && digit <= 9 && textures->wave_digits_loaded[digit]) {
            float scale = 1.2f;
            DrawTextureEx(
                textures->wave_digits[digit],
                (Vector2){(float)draw_x, (float)y},
                0.0f,
                scale,
                WHITE
            );
            draw_x += (int)lroundf((float)textures->wave_digits[digit].width * scale) + 2;
        } else {
            DrawText(TextFormat("%c", buffer[index]), draw_x, y, 30, GOLD);
            draw_x += 18;
        }
    }
}

static void fc_draw_inventory_slot_widget(
    Rectangle rect,
    const char* short_label,
    const char* detail_label,
    int count,
    const fc_viewer_hud_textures* textures
) {
    Rectangle icon_rect = {rect.x, rect.y, 42.0f, 42.0f};
    Color text_color = RAYWHITE;
    if (textures->inventory_slot_loaded) {
        fc_draw_texture_fit(&textures->inventory_slot, 1, icon_rect, WHITE);
    } else {
        DrawRectangleRounded(icon_rect, 0.18f, 8, Fade((Color){56, 42, 44, 255}, 0.95f));
    }
    if (textures->inventory_slot_frame_a_loaded) {
        fc_draw_texture_fit(&textures->inventory_slot_frame_a, 1, icon_rect, WHITE);
    }
    if (textures->inventory_slot_frame_b_loaded) {
        fc_draw_texture_fit(&textures->inventory_slot_frame_b, 1, icon_rect, Fade(GOLD, 0.18f));
    }
    DrawText(short_label, (int)icon_rect.x + 8, (int)icon_rect.y + 11, 16, text_color);
    DrawText(detail_label, (int)rect.x + 52, (int)rect.y + 2, 16, text_color);
    DrawText(TextFormat("%d", count), (int)rect.x + 52, (int)rect.y + 20, 22, GOLD);
}

static void fc_draw_hud(
    const fc_viewer_options* options,
    const fc_runtime_slot_snapshot* snapshot,
    fc_hover_state hovered,
    fc_ground_hover_state hovered_ground,
    int selected_npc_index,
    int paused,
    const fc_viewer_action_feedback* feedback,
    const fc_hud_layout* layout,
    const fc_viewer_replay* replay,
    int inspector_overlays_enabled,
    const fc_viewer_hud_textures* hud_textures,
    const Texture2D* header_texture,
    int header_texture_loaded
) {
    int panel_x = 16;
    int panel_y = 16;
    int line_y = panel_y + 18;
    int selected_visible_index = fc_find_visible_target_index(snapshot, selected_npc_index);
    Vector2 mouse = GetMousePosition();
    char line[256];

    DrawRectangleRounded(layout->panel, 0.12f, 12, Fade((Color){14, 10, 12, 255}, 0.94f));
    if (hud_textures != NULL && hud_textures->panel_side_background_loaded) {
        fc_draw_texture_fit(
            &hud_textures->panel_side_background,
            1,
            (Rectangle){layout->panel.x + 4.0f, layout->panel.y + 4.0f, layout->panel.width - 8.0f, layout->panel.height - 8.0f},
            Fade(WHITE, 0.96f)
        );
    }
    DrawRectangleRoundedLinesEx(layout->panel, 0.12f, 12, 2.0f, Fade(FC_VIEWER_COLOR_LAVA_RING, 0.62f));
    if (header_texture_loaded && header_texture != NULL && header_texture->id > 0) {
        float scale = 438.0f / (float)header_texture->width;
        DrawTextureEx(*header_texture, (Vector2){panel_x + 16.0f, panel_y + 10.0f}, 0.0f, scale, WHITE);
        line_y = panel_y + (int)lroundf((float)header_texture->height * scale) + 22;
    } else {
        DrawText("Fight Caves Native Debug Viewer", panel_x + 14, line_y, 24, RAYWHITE);
        line_y += 34;
    }

    if (replay->loaded) {
        snprintf(
            line,
            sizeof(line),
            "mode: replay  source=%s  frame=%d/%d  overlays=%d",
            replay->source_ref,
            replay->current_frame,
            replay->frame_count > 0 ? replay->frame_count - 1 : 0,
            inspector_overlays_enabled
        );
    } else {
        snprintf(line, sizeof(line), "mode: %s", options->trace_name != NULL ? options->trace_name : "generic_reset");
    }
    DrawText(line, panel_x + 14, line_y, 18, LIGHTGRAY);
    line_y += 22;

    snprintf(line, sizeof(line), "state: %s  wave=%d  tick=%d  steps=%d", paused ? "paused" : "running", snapshot->wave, snapshot->tick, snapshot->steps);
    DrawText(line, panel_x + 14, line_y, 18, GOLD);
    if (hud_textures != NULL) {
        fc_draw_wave_digits(snapshot->wave, panel_x + 332, line_y - 6, hud_textures);
    }
    line_y += 22;

    snprintf(line, sizeof(line), "terminal: %s  remaining=%d", fc_terminal_label(snapshot->terminal_code), snapshot->remaining);
    DrawText(line, panel_x + 14, line_y, 18, LIGHTGRAY);
    line_y += 24;

    snprintf(line, sizeof(line), "hp %d/%d   prayer %d/%d   run %d/%d", snapshot->hitpoints_current, snapshot->hitpoints_max, snapshot->prayer_current, snapshot->prayer_max, snapshot->run_energy, snapshot->run_energy_max);
    DrawText(line, panel_x + 14, line_y, 18, RAYWHITE);
    line_y += 22;

    snprintf(line, sizeof(line), "inventory: sharks=%d  ppot_doses=%d  ammo=%d", snapshot->sharks, snapshot->prayer_potion_dose_count, snapshot->ammo);
    DrawText(line, panel_x + 14, line_y, 18, RAYWHITE);
    line_y += 22;

    snprintf(line, sizeof(line), "prayers: magic=%d missiles=%d melee=%d running=%d", snapshot->protect_from_magic, snapshot->protect_from_missiles, snapshot->protect_from_melee, snapshot->running);
    DrawText(line, panel_x + 14, line_y, 18, LIGHTGRAY);
    line_y += 22;

    snprintf(line, sizeof(line), "last_action: %s  accepted=%d  reject=%s", feedback->action_label, feedback->accepted, fc_rejection_label(feedback->rejection_code));
    DrawText(line, panel_x + 14, line_y, 18, LIGHTGRAY);
    line_y += 22;

    snprintf(line, sizeof(line), "action_target=%d  jad_outcome=%s", feedback->resolved_target_npc_index, fc_jad_outcome_label(feedback->jad_hit_resolve_outcome_code));
    DrawText(line, panel_x + 14, line_y, 18, LIGHTGRAY);
    line_y += 24;

    if (selected_visible_index >= 0) {
        const fc_visible_target* target = &snapshot->visible_targets[selected_visible_index];
        snprintf(
            line,
            sizeof(line),
            "selected: npc#%d id=%d hp=%d/%d tile=(%d,%d,%d)",
            target->npc_index,
            target->id_code,
            target->hitpoints_current,
            target->hitpoints_max,
            target->tile_x,
            target->tile_y,
            target->tile_level
        );
        DrawText(line, panel_x + 14, line_y, 18, GREEN);
        line_y += 22;
    } else {
        DrawText("selected: none", panel_x + 14, line_y, 18, LIGHTGRAY);
        line_y += 22;
    }

    if (hovered.kind == FC_HOVER_PLAYER) {
        snprintf(line, sizeof(line), "hover: player tile=(%d,%d,%d)", snapshot->tile_x, snapshot->tile_y, snapshot->tile_level);
        DrawText(line, panel_x + 14, line_y, 18, FC_VIEWER_COLOR_PLAYER_ACCENT);
        line_y += 22;
    } else if (hovered.kind == FC_HOVER_NPC && hovered.visible_index >= 0 && hovered.visible_index < snapshot->visible_target_count) {
        const fc_visible_target* target = &snapshot->visible_targets[hovered.visible_index];
        snprintf(
            line,
            sizeof(line),
            "hover: npc#%d id=%d hp=%d/%d telegraph=%d",
            target->npc_index,
            target->id_code,
            target->hitpoints_current,
            target->hitpoints_max,
            target->jad_telegraph_state
        );
        DrawText(line, panel_x + 14, line_y, 18, fc_npc_color(target->id_code, target->jad_telegraph_state));
        line_y += 22;
    } else if (hovered_ground.valid) {
        snprintf(line, sizeof(line), "hover: ground tile=(%d,%d,%d)", hovered_ground.tile_x, hovered_ground.tile_y, hovered_ground.tile_level);
        DrawText(line, panel_x + 14, line_y, 18, FC_VIEWER_COLOR_HOVER);
        line_y += 22;
    } else {
        DrawText("hover: none", panel_x + 14, line_y, 18, LIGHTGRAY);
        line_y += 22;
    }

    if (!replay->loaded) {
        fc_draw_inventory_slot_widget(
            (Rectangle){panel_x + 18.0f, panel_y + 350.0f, 132.0f, 42.0f},
            "Shk",
            "Sharks",
            snapshot->sharks,
            hud_textures
        );
        fc_draw_inventory_slot_widget(
            (Rectangle){panel_x + 162.0f, panel_y + 350.0f, 132.0f, 42.0f},
            "P4",
            "Prayer",
            snapshot->prayer_potion_dose_count / 4,
            hud_textures
        );
        fc_draw_inventory_slot_widget(
            (Rectangle){panel_x + 306.0f, panel_y + 350.0f, 132.0f, 42.0f},
            "Bol",
            "Ammo",
            snapshot->ammo,
            hud_textures
        );
        fc_draw_button(
            layout->shark_button,
            "click: eat",
            TextFormat("sharks=%d  key=F", snapshot->sharks),
            (Color){145, 76, 76, 255},
            0,
            CheckCollisionPointRec(mouse, layout->shark_button)
        );
        fc_draw_button(
            layout->potion_button,
            "click: drink",
            TextFormat("doses=%d  key=G", snapshot->prayer_potion_dose_count),
            (Color){67, 98, 151, 255},
            0,
            CheckCollisionPointRec(mouse, layout->potion_button)
        );
        fc_draw_button(
            layout->prayer_magic_button,
            "magic",
            "click or key=1",
            (Color){55, 76, 161, 255},
            snapshot->protect_from_magic,
            CheckCollisionPointRec(mouse, layout->prayer_magic_button)
        );
        if (hud_textures != NULL && hud_textures->pray_magic_loaded) {
            fc_draw_texture_fit(
                &hud_textures->pray_magic,
                1,
                (Rectangle){layout->prayer_magic_button.x + 72.0f, layout->prayer_magic_button.y + 5.0f, 24.0f, 24.0f},
                WHITE
            );
        }
        fc_draw_button(
            layout->prayer_missiles_button,
            "missiles",
            "click or key=2",
            (Color){60, 112, 66, 255},
            snapshot->protect_from_missiles,
            CheckCollisionPointRec(mouse, layout->prayer_missiles_button)
        );
        if (hud_textures != NULL && hud_textures->pray_missiles_loaded) {
            fc_draw_texture_fit(
                &hud_textures->pray_missiles,
                1,
                (Rectangle){layout->prayer_missiles_button.x + 72.0f, layout->prayer_missiles_button.y + 5.0f, 24.0f, 24.0f},
                WHITE
            );
        }
        fc_draw_button(
            layout->prayer_melee_button,
            "melee",
            "click or key=3",
            (Color){137, 83, 48, 255},
            snapshot->protect_from_melee,
            CheckCollisionPointRec(mouse, layout->prayer_melee_button)
        );
        if (hud_textures != NULL && hud_textures->pray_melee_loaded) {
            fc_draw_texture_fit(
                &hud_textures->pray_melee,
                1,
                (Rectangle){layout->prayer_melee_button.x + 72.0f, layout->prayer_melee_button.y + 5.0f, 24.0f, 24.0f},
                WHITE
            );
        }
        fc_draw_button(
            layout->run_button,
            "run",
            "click or key=T",
            (Color){88, 132, 121, 255},
            snapshot->running,
            CheckCollisionPointRec(mouse, layout->run_button)
        );
        DrawText(
            "controls: left-click npc attack, left-click ground move, SPACE pause, N step, R reset, F eat, G drink, 1/2/3 prayers, T run",
            panel_x + 14,
            panel_y + 528,
            16,
            Fade(RAYWHITE, 0.82f)
        );
    } else {
        float progress = replay->frame_count > 1
            ? (float)replay->current_frame / (float)(replay->frame_count - 1)
            : 0.0f;
        Rectangle handle = {
            layout->replay_scrub_bar.x + progress * layout->replay_scrub_bar.width - 6.0f,
            layout->replay_scrub_bar.y - 4.0f,
            12.0f,
            layout->replay_scrub_bar.height + 8.0f,
        };
        DrawText(
            "replay controls: SPACE play/pause, [ prev, ] next, HOME start, END end, click scrub bar",
            panel_x + 14,
            panel_y + 524,
            16,
            Fade(RAYWHITE, 0.82f)
        );
        DrawRectangleRounded(layout->replay_scrub_bar, 0.2f, 8, Fade((Color){48, 62, 74, 255}, 0.95f));
        DrawRectangleRounded(
            (Rectangle){
                layout->replay_scrub_bar.x,
                layout->replay_scrub_bar.y,
                layout->replay_scrub_bar.width * progress,
                layout->replay_scrub_bar.height,
            },
            0.2f,
            8,
            Fade((Color){91, 183, 208, 255}, 0.92f)
        );
        DrawRectangleRounded(handle, 0.2f, 8, GOLD);
    }
}

int main(int argc, char** argv) {
    fc_viewer_options options;
    fc_native_runtime* runtime = NULL;
    fc_runtime_slot_snapshot snapshot;
    fc_viewer_action_feedback feedback;
    fc_viewer_replay replay;
    fc_hud_layout hud_layout;
    fc_viewer_hud_textures hud_textures;
    Texture2D header_texture = {0};
    int header_texture_loaded = 0;
    const fc_scene_rect* scene_rects;
    int scene_rect_count;
    int parse_result;
    int selected_npc_index = -1;

    parse_result = fc_parse_args(argc, argv, &options);
    if (parse_result == 2) {
        return 0;
    }
    if (!parse_result) {
        return 1;
    }

    memset(&replay, 0, sizeof(replay));
    fc_clear_action_feedback(&feedback);
    runtime = fc_native_runtime_open();
    if (runtime == NULL) {
        fprintf(stderr, "%s\n", fc_native_runtime_last_error());
        return 1;
    }
    if (options.replay_file != NULL) {
        if (!fc_parse_replay_file(options.replay_file, &replay)) {
            fc_native_runtime_close(runtime);
            return 1;
        }
        replay.current_frame = options.replay_frame;
        if (!fc_build_replay_frames(runtime, &replay)) {
            fc_clear_replay(&replay);
            fc_native_runtime_close(runtime);
            return 1;
        }
        fc_load_replay_frame(&replay, &snapshot, &feedback, &selected_npc_index);
    } else {
        if (!fc_prepare_runtime(runtime, &options)) {
            fc_native_runtime_close(runtime);
            return 1;
        }
        if (!fc_export_snapshot(runtime, &snapshot)) {
            fc_native_runtime_close(runtime);
            return 1;
        }
        if (options.step_count > 0 && !fc_apply_wait_steps(runtime, options.step_count, &snapshot, &feedback)) {
            fc_native_runtime_close(runtime);
            return 1;
        }
        if (!fc_apply_scripted_actions(runtime, &options, &snapshot, &feedback, &selected_npc_index)) {
            fc_native_runtime_close(runtime);
            return 1;
        }
    }

    scene_rects = fc_native_runtime_scene_rects();
    scene_rect_count = fc_native_runtime_scene_rect_count();

    if (options.dump_scene_json) {
        fc_print_scene_json(&options, &snapshot, &feedback, selected_npc_index, &replay);
        fc_clear_replay(&replay);
        fc_native_runtime_close(runtime);
        return 0;
    }

    SetConfigFlags(FLAG_WINDOW_RESIZABLE | FLAG_MSAA_4X_HINT);
    InitWindow(options.width, options.height, "Fight Caves Native Debug Viewer");
    SetTargetFPS(60);
    memset(&hud_textures, 0, sizeof(hud_textures));
    {
        char asset_path[FC_VIEWER_ASSET_PATH_SIZE];
        fc_append_asset_path(argv[0], FC_VIEWER_HEADER_IMAGE_FILENAME, asset_path, sizeof(asset_path));
        if (FileExists(asset_path)) {
            header_texture = LoadTexture(asset_path);
            header_texture_loaded = header_texture.id > 0;
        } else if (FileExists(FC_VIEWER_HEADER_IMAGE_FILENAME)) {
            header_texture = LoadTexture(FC_VIEWER_HEADER_IMAGE_FILENAME);
            header_texture_loaded = header_texture.id > 0;
        }
    }
    fc_load_generated_hud_textures(argv[0], &hud_textures);

    {
        float base_focus_x = 0.0f;
        float base_focus_z = 0.0f;
        float pan_x = 0.0f;
        float pan_z = 0.0f;
        float camera_yaw = -0.95f;
        float camera_radius = 36.0f;
        float camera_height = 34.0f;
        int inspector_overlays_enabled = 1;
        int paused = options.autoplay ? 0 : 1;

        hud_layout = fc_make_hud_layout();

        while (!WindowShouldClose()) {
            Camera3D camera;
            fc_hover_state hovered;
            fc_ground_hover_state hovered_ground;
            Vector2 mouse = GetMousePosition();
            int hud_click_result = 0;
            int stepped_this_frame = 0;

            if (IsKeyPressed(KEY_SPACE)) {
                if (replay.loaded) {
                    replay.playing = !replay.playing;
                } else {
                    paused = !paused;
                }
            }
            if (IsKeyPressed(KEY_R)) {
                selected_npc_index = -1;
                fc_clear_action_feedback(&feedback);
                if (replay.loaded) {
                    replay.current_frame = 0;
                    replay.playing = 0;
                    if (!fc_build_replay_frames(runtime, &replay)) {
                        break;
                    }
                    fc_load_replay_frame(&replay, &snapshot, &feedback, &selected_npc_index);
                } else {
                    if (!fc_prepare_runtime(runtime, &options) || !fc_export_snapshot(runtime, &snapshot)) {
                        break;
                    }
                    paused = 1;
                }
            }
            if (IsKeyPressed(KEY_I)) {
                inspector_overlays_enabled = !inspector_overlays_enabled;
            }
            if (IsKeyDown(KEY_A)) {
                camera_yaw -= 0.025f;
            }
            if (IsKeyDown(KEY_D)) {
                camera_yaw += 0.025f;
            }
            if (IsKeyDown(KEY_W)) {
                camera_height += 0.45f;
            }
            if (IsKeyDown(KEY_S)) {
                camera_height -= 0.45f;
            }
            if (IsKeyDown(KEY_LEFT)) {
                pan_x -= 0.35f;
            }
            if (IsKeyDown(KEY_RIGHT)) {
                pan_x += 0.35f;
            }
            if (IsKeyDown(KEY_UP)) {
                pan_z -= 0.35f;
            }
            if (IsKeyDown(KEY_DOWN)) {
                pan_z += 0.35f;
            }
            camera_radius -= GetMouseWheelMove() * 2.0f;
            if (camera_radius < 10.0f) {
                camera_radius = 10.0f;
            }
            if (camera_radius > 96.0f) {
                camera_radius = 96.0f;
            }
            if (camera_height < 10.0f) {
                camera_height = 10.0f;
            }
            if (camera_height > 96.0f) {
                camera_height = 96.0f;
            }

            fc_compute_scene_bounds(scene_rects, scene_rect_count, &snapshot, &base_focus_x, &base_focus_z);
            camera.position = (Vector3){
                base_focus_x + pan_x + cosf(camera_yaw) * camera_radius,
                camera_height,
                base_focus_z + pan_z + sinf(camera_yaw) * camera_radius,
            };
            camera.target = (Vector3){base_focus_x + pan_x, 0.0f, base_focus_z + pan_z};
            camera.up = (Vector3){0.0f, 1.0f, 0.0f};
            camera.fovy = 42.0f;
            camera.projection = CAMERA_PERSPECTIVE;

            hovered = fc_pick_hovered_entity(&camera, &snapshot);
            hovered_ground = fc_pick_ground_tile(&camera, &snapshot);

            if (replay.loaded) {
                if (fc_try_handle_replay_scrub_click(mouse, &hud_layout, &replay)) {
                    fc_load_replay_frame(&replay, &snapshot, &feedback, &selected_npc_index);
                } else if (IsKeyPressed(KEY_HOME)) {
                    replay.current_frame = 0;
                    replay.playing = 0;
                    fc_load_replay_frame(&replay, &snapshot, &feedback, &selected_npc_index);
                } else if (IsKeyPressed(KEY_END)) {
                    replay.current_frame = fc_clamp_replay_frame(&replay, replay.frame_count - 1);
                    replay.playing = 0;
                    fc_load_replay_frame(&replay, &snapshot, &feedback, &selected_npc_index);
                } else if (IsKeyPressed(KEY_LEFT_BRACKET) || IsKeyPressed(KEY_B)) {
                    replay.current_frame = fc_clamp_replay_frame(&replay, replay.current_frame - 1);
                    replay.playing = 0;
                    fc_load_replay_frame(&replay, &snapshot, &feedback, &selected_npc_index);
                } else if (IsKeyPressed(KEY_RIGHT_BRACKET) || IsKeyPressed(KEY_N)) {
                    replay.current_frame = fc_clamp_replay_frame(&replay, replay.current_frame + 1);
                    replay.playing = 0;
                    fc_load_replay_frame(&replay, &snapshot, &feedback, &selected_npc_index);
                } else if (replay.playing) {
                    replay.current_frame = fc_clamp_replay_frame(&replay, replay.current_frame + 1);
                    if (replay.current_frame >= replay.frame_count - 1) {
                        replay.current_frame = replay.frame_count - 1;
                        replay.playing = 0;
                    }
                    fc_load_replay_frame(&replay, &snapshot, &feedback, &selected_npc_index);
                }
            } else {
                hud_click_result = fc_try_handle_hud_click(mouse, &hud_layout, runtime, &snapshot, &feedback);
                if (hud_click_result < 0) {
                    break;
                }
                if (hud_click_result > 0) {
                    stepped_this_frame = 1;
                }

                if (!stepped_this_frame) {
                    if (IsKeyPressed(KEY_F)) {
                        if (!fc_apply_simple_action(runtime, FC_VIEWER_ACTION_EAT_SHARK, "eat_shark", &snapshot, &feedback)) {
                            break;
                        }
                        stepped_this_frame = 1;
                    } else if (IsKeyPressed(KEY_G)) {
                        if (!fc_apply_simple_action(runtime, FC_VIEWER_ACTION_DRINK_PRAYER_POTION, "drink_prayer_potion", &snapshot, &feedback)) {
                            break;
                        }
                        stepped_this_frame = 1;
                    } else if (IsKeyPressed(KEY_ONE)) {
                        if (!fc_apply_prayer_action(runtime, FC_VIEWER_PRAYER_MAGIC, &snapshot, &feedback)) {
                            break;
                        }
                        stepped_this_frame = 1;
                    } else if (IsKeyPressed(KEY_TWO)) {
                        if (!fc_apply_prayer_action(runtime, FC_VIEWER_PRAYER_MISSILES, &snapshot, &feedback)) {
                            break;
                        }
                        stepped_this_frame = 1;
                    } else if (IsKeyPressed(KEY_THREE)) {
                        if (!fc_apply_prayer_action(runtime, FC_VIEWER_PRAYER_MELEE, &snapshot, &feedback)) {
                            break;
                        }
                        stepped_this_frame = 1;
                    } else if (IsKeyPressed(KEY_T)) {
                        if (!fc_apply_simple_action(runtime, FC_VIEWER_ACTION_TOGGLE_RUN, "toggle_run", &snapshot, &feedback)) {
                            break;
                        }
                        stepped_this_frame = 1;
                    } else if (IsMouseButtonPressed(MOUSE_BUTTON_LEFT) && !CheckCollisionPointRec(mouse, hud_layout.panel)) {
                        if (hovered.kind == FC_HOVER_NPC && hovered.visible_index >= 0) {
                            selected_npc_index = hovered.npc_index;
                            if (!fc_apply_attack_action(runtime, hovered.visible_index, &snapshot, &feedback)) {
                                break;
                            }
                            stepped_this_frame = 1;
                        } else if (hovered_ground.valid) {
                            if (!fc_apply_move_action(runtime, hovered_ground.tile_x, hovered_ground.tile_y, hovered_ground.tile_level, &snapshot, &feedback)) {
                                break;
                            }
                            stepped_this_frame = 1;
                        }
                    }
                }

                if (!stepped_this_frame && (IsKeyPressed(KEY_N) || !paused)) {
                    if (!fc_apply_simple_action(runtime, FC_VIEWER_ACTION_WAIT, "wait", &snapshot, &feedback)) {
                        break;
                    }
                    stepped_this_frame = 1;
                }

                if (feedback.resolved_target_npc_index >= 0) {
                    selected_npc_index = feedback.resolved_target_npc_index;
                }
                if (selected_npc_index >= 0 && fc_find_visible_target_index(&snapshot, selected_npc_index) < 0) {
                    selected_npc_index = -1;
                }
                if ((stepped_this_frame || !paused) && (snapshot.terminated || snapshot.truncated)) {
                    paused = 1;
                }
            }

            BeginDrawing();
            ClearBackground(FC_VIEWER_COLOR_LAVA_BASE);

            BeginMode3D(camera);
            fc_draw_scene_rects(scene_rects, scene_rect_count, (float)GetTime());
            fc_draw_snapshot_entities(&snapshot, hovered, selected_npc_index, hovered_ground);
            EndMode3D();
            fc_draw_entity_inspector_overlays(&camera, &snapshot, selected_npc_index, inspector_overlays_enabled);

            fc_draw_hud(
                &options,
                &snapshot,
                hovered,
                hovered_ground,
                selected_npc_index,
                replay.loaded ? !replay.playing : paused,
                &feedback,
                &hud_layout,
                &replay,
                inspector_overlays_enabled,
                &hud_textures,
                &header_texture,
                header_texture_loaded
            );
            EndDrawing();
        }
    }

    if (header_texture_loaded) {
        UnloadTexture(header_texture);
    }
    fc_unload_generated_hud_textures(&hud_textures);
    CloseWindow();
    fc_clear_replay(&replay);
    fc_native_runtime_close(runtime);
    return 0;
}
