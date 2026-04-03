/*
 * viewer.c — Fight Caves playable debug viewer.
 *
 * Phase 8: Human-playable Fight Caves with all backend systems connected.
 *
 * Controls:
 *   WASD        — move (N/W/S/E)            Space    — pause/resume
 *   1/2/3       — protect melee/range/magic  Right    — single-step tick
 *   F           — eat shark                  Up/Down  — tick speed
 *   P           — drink prayer potion        R        — reset episode
 *   Tab         — cycle attack target        A        — toggle auto/manual
 *   D           — toggle debug overlay       G        — grid  C — collision
 *   4/5         — camera presets             Q/Esc    — quit
 *   Scroll      — zoom                       Right-drag — orbit camera
 */

#include "raylib.h"
#include "rlgl.h"
#include "fc_types.h"
#include "fc_contracts.h"
#include "fc_api.h"
#include "fc_npc.h"
#include "fc_combat.h"
#include "fc_pathfinding.h"
#include "osrs_pvp_terrain.h"
#include "osrs_pvp_objects.h"
#include "fc_npc_models.h"
#include "osrs_pvp_anim.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#define TILE_SIZE       16
#define PANEL_WIDTH     220
#define HEADER_HEIGHT   40
#define WINDOW_W        (FC_ARENA_WIDTH * TILE_SIZE + PANEL_WIDTH)
#define WINDOW_H        (FC_ARENA_HEIGHT * TILE_SIZE + HEADER_HEIGHT)
#define MAX_TPS         30
#define MIN_TPS         1
#define MAX_HITSPLATS   32

/* Player animation sequence IDs (from osrs-dumps seq.sym) */
#define PLAYER_ANIM_IDLE   4591  /* xbows_human_ready */
#define PLAYER_ANIM_WALK   4226  /* xbows_human_walk_f */
#define PLAYER_ANIM_RUN    4228  /* xbows_human_run */
#define PLAYER_ANIM_ATTACK 4230  /* xbows_human_fire_and_reload */
#define PLAYER_ANIM_EAT    829   /* human_eat */
#define PLAYER_ANIM_DEATH  836   /* human_death */

/* Colors */
#define COL_BG          CLITERAL(Color){ 80, 80, 85, 255 }
#define COL_HEADER      CLITERAL(Color){ 30, 30, 40, 255 }
#define COL_PANEL       CLITERAL(Color){ 62, 53, 41, 255 }
#define COL_PANEL_BORDER CLITERAL(Color){ 42, 36, 28, 255 }
#define COL_TEXT_YELLOW CLITERAL(Color){ 255, 255,  0, 255 }
#define COL_TEXT_WHITE  CLITERAL(Color){ 255, 255, 255, 255 }
#define COL_TEXT_SHADOW CLITERAL(Color){   0,   0,   0, 255 }
#define COL_TEXT_DIM    CLITERAL(Color){ 130, 130, 140, 255 }
#define COL_TEXT_GREEN  CLITERAL(Color){ 100, 255, 100, 255 }
#define COL_HP_GREEN    CLITERAL(Color){  30, 255,  30, 255 }
#define COL_HP_RED      CLITERAL(Color){ 120,   0,   0, 255 }
#define COL_PRAY_BLUE   CLITERAL(Color){  50, 120, 210, 255 }
#define COL_PLAYER      CLITERAL(Color){  80, 140, 255, 255 }
#define COL_GRID        CLITERAL(Color){  30,  30,  30, 80  }
#define COL_BLOCKED     CLITERAL(Color){ 180,  30,  30, 60  }
#define COL_WALKABLE    CLITERAL(Color){  30, 120,  30, 30  }
#define COL_TARGET      CLITERAL(Color){ 255, 255,   0, 120 }
#define COL_HIT_RED     CLITERAL(Color){ 255,  50,  50, 255 }
#define COL_HIT_BLUE    CLITERAL(Color){  50, 100, 255, 255 }

/* Asset paths */
static const char* TERRAIN_PATHS[] = {
    "demo-env/assets/fightcaves.terrain", "../demo-env/assets/fightcaves.terrain",
    "assets/fightcaves.terrain", NULL };
static const char* OBJECTS_PATHS[] = {
    "demo-env/assets/fightcaves.objects", "../demo-env/assets/fightcaves.objects",
    "assets/fightcaves.objects", NULL };
#define FC_WORLD_ORIGIN_X 2368
#define FC_WORLD_ORIGIN_Y 5056

/* Hitsplat — OSRS-style damage splat rendered as 2D overlay */
typedef struct {
    int active;
    float world_x, world_y, world_z;  /* 3D position (entity center) */
    int damage;           /* damage in tenths (0 = miss) */
    int frames_left;      /* lifetime in render frames */
} Hitsplat;

/* Visual projectile — travels from source to destination over tick duration */
#define MAX_PROJECTILES 16
typedef struct {
    int active;
    float src_x, src_y, src_z;   /* 3D source position */
    float dst_x, dst_y, dst_z;   /* 3D destination position */
    float total_time;             /* total travel time in seconds */
    float elapsed;                /* elapsed time in seconds */
    Color color;
    float radius;
} VisualProjectile;

/* NPC colors by type */
static const Color NPC_COLORS[] = {
    {128,128,128,255}, /* 0: none */
    {180,160,60,255},  /* 1: Tz-Kih (yellow) */
    {100,180,60,255},  /* 2: Tz-Kek (green) */
    {80,150,50,255},   /* 3: Tz-Kek small */
    {60,60,200,255},   /* 4: Tok-Xil (blue) */
    {200,100,60,255},  /* 5: Yt-MejKot (orange) */
    {160,40,160,255},  /* 6: Ket-Zek (purple) */
    {200,40,40,255},   /* 7: TzTok-Jad (RED) */
    {60,200,200,255},  /* 8: Yt-HurKot (cyan) */
};

/* Viewer state */
typedef struct {
    FcState state;
    FcRenderEntity entities[FC_MAX_RENDER_ENTITIES];
    int entity_count;
    int paused, step_once, tps;
    float tick_acc;
    int show_debug, show_grid, show_collision;
    int auto_mode;       /* 1 = random actions, 0 = human control */
    Camera3D camera;
    float cam_yaw, cam_pitch, cam_dist;
    int actions[FC_NUM_ACTION_HEADS];
    uint32_t seed, last_hash;
    int episode_count;
    int attack_target;   /* NPC slot index for attack (-1 = none) */
    /* Terrain + Objects + NPC models */
    TerrainMesh* terrain;
    ObjectMesh* objects;
    NpcModelSet* npc_models;
    NpcModelSet* player_model;
    /* Player animation */
    AnimCache* player_anims;
    AnimModelState* player_anim_state;
    uint16_t player_anim_seq;     /* current animation sequence ID */
    int player_anim_frame;        /* current frame index within sequence */
    float player_anim_timer;      /* seconds remaining in current frame */
    /* Hitsplats */
    Hitsplat hitsplats[MAX_HITSPLATS];
    /* Buffered key inputs (captured every frame, consumed on tick) */
    int pending_prayer, pending_eat, pending_drink;
    /* Clickable NPC health bars in side panel (filled during draw, checked on click) */
    int panel_npc_slot[8];   /* NPC array index for each panel row */
    int panel_npc_y[8];      /* screen Y of each panel row */
    int panel_npc_count;     /* how many rows drawn */
    /* Visual projectiles in flight */
    VisualProjectile projectiles[MAX_PROJECTILES];
    /* Prayer overhead icon textures */
    Texture2D pray_melee_tex, pray_missiles_tex, pray_magic_tex;
    /* Debug toggles */
    int godmode;        /* 1 = player can't die */
    int debug_spawn;    /* NPC type to spawn (0 = off, 1-8 = type) */
    /* Smooth movement interpolation — stored by stable slot (player + NPC array index) */
    float prev_player_x, prev_player_y;
    float prev_npc_x[FC_MAX_NPCS];
    float prev_npc_y[FC_MAX_NPCS];
    int prev_npc_active[FC_MAX_NPCS];  /* was this NPC active last tick? */
    float tick_frac;
} ViewerState;

/* Helpers */
static void text_s(const char* t, int x, int y, int sz, Color c) {
    DrawText(t, x+1, y+1, sz, COL_TEXT_SHADOW);
    DrawText(t, x, y, sz, c);
}

static void reset_ep(ViewerState* v) {
    v->seed = (uint32_t)GetRandomValue(1, 999999);
    fc_reset(&v->state, v->seed);
    fc_fill_render_entities(&v->state, v->entities, &v->entity_count);
    v->last_hash = fc_state_hash(&v->state);
    v->episode_count++;
    v->attack_target = -1;
    v->tick_frac = 1.0f;
    memset(v->actions, 0, sizeof(v->actions));
    memset(v->hitsplats, 0, sizeof(v->hitsplats));
    memset(v->projectiles, 0, sizeof(v->projectiles));
    v->pending_prayer = 0;
    v->pending_eat = 0;
    v->pending_drink = 0;
    /* Initialize prev positions */
    v->prev_player_x = (float)v->state.player.x;
    v->prev_player_y = (float)v->state.player.y;
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        v->prev_npc_x[i] = (float)v->state.npcs[i].x;
        v->prev_npc_y[i] = (float)v->state.npcs[i].y;
        v->prev_npc_active[i] = v->state.npcs[i].active;
    }
}

static void spawn_projectile(ViewerState* v,
                             float sx, float sy, float sz,
                             float dx, float dy, float dz,
                             float travel_secs, Color col, float radius) {
    for (int i = 0; i < MAX_PROJECTILES; i++) {
        if (!v->projectiles[i].active) {
            v->projectiles[i] = (VisualProjectile){
                1, sx, sy, sz, dx, dy, dz, travel_secs, 0.0f, col, radius};
            return;
        }
    }
}

static void spawn_hitsplat(ViewerState* v, float wx, float wy, float wz, int damage_tenths) {
    for (int i = 0; i < MAX_HITSPLATS; i++) {
        if (!v->hitsplats[i].active) {
            v->hitsplats[i].active = 1;
            v->hitsplats[i].world_x = wx;
            v->hitsplats[i].world_y = wy;
            v->hitsplats[i].world_z = wz;
            v->hitsplats[i].damage = damage_tenths;
            v->hitsplats[i].frames_left = 60;  /* 1 second at 60fps */
            return;
        }
    }
}

/* Terrain loader — the terrain mesh is the floor heightmap.
 * The red/black lava pattern comes from the objects mesh (fightcaves.objects),
 * not the terrain. The terrain is just the ground surface.
 * We keep original cache colors (dark base) without modification. */
static TerrainMesh* load_terrain(ViewerState* v) {
    (void)v;
    for (int i = 0; TERRAIN_PATHS[i]; i++) {
        if (!FileExists(TERRAIN_PATHS[i])) continue;
        TerrainMesh* tm = terrain_load(TERRAIN_PATHS[i]);
        if (tm && tm->loaded) {
            terrain_offset(tm, FC_WORLD_ORIGIN_X, FC_WORLD_ORIGIN_Y);
            return tm;
        }
    }
    return NULL;
}

/* Load collision map for use during object height compression */
static int load_collision_for_objects(uint8_t coll[64][64]) {
    const char* paths[] = {
        "demo-env/assets/fightcaves.collision",
        "../demo-env/assets/fightcaves.collision",
        "assets/fightcaves.collision", NULL };
    for (int i = 0; paths[i]; i++) {
        FILE* f = fopen(paths[i], "rb");
        if (!f) continue;
        uint8_t buf[64*64];
        size_t n = fread(buf, 1, sizeof(buf), f);
        fclose(f);
        if (n == sizeof(buf)) {
            for (int y = 0; y < 64; y++)
                for (int x = 0; x < 64; x++)
                    coll[x][y] = buf[y * 64 + x];
            return 1;
        }
    }
    return 0;
}

/* Objects loader — no modifications */
static ObjectMesh* load_objects_with_terrain(TerrainMesh* tm) {
    (void)tm;
    for (int i = 0; OBJECTS_PATHS[i]; i++) {
        if (!FileExists(OBJECTS_PATHS[i])) continue;
        ObjectMesh* om = objects_load(OBJECTS_PATHS[i]);
        if (om && om->loaded) {
            objects_offset(om, FC_WORLD_ORIGIN_X, FC_WORLD_ORIGIN_Y);

            /* No modifications to objects mesh — original cache data */

            return om;
        }
    }
    return NULL;
}

/* Forward declaration */
static float ground_y(ViewerState* v, int tile_x, int tile_y);

/* ======================================================================== */
/* Human input → action heads                                                */
/* ======================================================================== */

/* Raycast from mouse position to find the tile coordinate on the ground plane */
static int raycast_to_tile(ViewerState* v, int* out_x, int* out_y) {
    Ray ray = GetScreenToWorldRay(GetMousePosition(), v->camera);
    /* Intersect with Y = ground_y plane */
    float gy = ground_y(v, 32, 32);
    if (fabsf(ray.direction.y) < 0.001f) return 0;  /* ray parallel to ground */
    float t = (gy - ray.position.y) / ray.direction.y;
    if (t < 0) return 0;  /* behind camera */
    float wx = ray.position.x + ray.direction.x * t;
    float wz = ray.position.z + ray.direction.z * t;
    /* Convert to tile coords: X = world X, tile Y = -world Z */
    int tx = (int)floorf(wx);
    int ty = (int)floorf(-wz);
    if (tx < 0 || tx >= FC_ARENA_WIDTH || ty < 0 || ty >= FC_ARENA_HEIGHT) return 0;
    *out_x = tx;
    *out_y = ty;
    return 1;
}

/* Find NPC at clicked tile — checks LIVE state, not render snapshot.
 * Returns NPC array index (0..FC_MAX_NPCS-1) or -1 if no NPC there. */
static int find_clicked_npc_idx(ViewerState* v, int tile_x, int tile_y) {
    int best = -1;
    int best_dist = 999;
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        FcNpc* n = &v->state.npcs[i];
        if (!n->active || n->is_dead) continue;
        /* Check if tile is within the NPC's footprint (or 1 tile adjacent) */
        if (tile_x >= n->x - 1 && tile_x <= n->x + n->size &&
            tile_y >= n->y - 1 && tile_y <= n->y + n->size) {
            /* Prefer the closest NPC center */
            int cx = n->x + n->size/2;
            int cy = n->y + n->size/2;
            int d = abs(tile_x - cx) + abs(tile_y - cy);
            if (d < best_dist) { best_dist = d; best = i; }
        }
    }
    return best;
}

/* Called EVERY FRAME to capture clicks (which only fire once at 60fps).
 * Sets routes and targets on the player struct directly. */
static void process_human_clicks(ViewerState* v) {
    FcPlayer* p = &v->state.player;

    if (IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
        Vector2 mpos = GetMousePosition();
        if (mpos.x < FC_ARENA_WIDTH * TILE_SIZE) {
            int tx, ty;
            int rc = raycast_to_tile(v, &tx, &ty);
            fprintf(stderr, "CLICK mouse=(%.0f,%.0f) raycast=%d tile=(%d,%d) player=(%d,%d)",
                    mpos.x, mpos.y, rc, tx, ty, p->x, p->y);
            if (rc) {
                int npc_idx = find_clicked_npc_idx(v, tx, ty);
                if (npc_idx >= 0) {
                    /* Click on/near NPC → set as attack target + approach */
                    p->attack_target_idx = npc_idx;
                    p->approach_target = 1;
                    p->route_len = 0;
                    p->route_idx = 0;
                    fprintf(stderr, " → ATTACK npc_idx=%d\n", npc_idx);
                } else {
                    int walkable = (tx >= 0 && tx < FC_ARENA_WIDTH &&
                                    ty >= 0 && ty < FC_ARENA_HEIGHT) ?
                                   v->state.walkable[tx][ty] : 0;
                    fprintf(stderr, " walkable=%d", walkable);
                    if (walkable) {
                        int steps = fc_pathfind_bfs(
                            p->x, p->y, tx, ty, v->state.walkable,
                            p->route_x, p->route_y, FC_MAX_ROUTE);
                        p->route_len = steps;
                        p->route_idx = 0;
                        p->attack_target_idx = -1;
                        p->approach_target = 0;
                        fprintf(stderr, " → ROUTE %d steps\n", steps);
                    } else {
                        fprintf(stderr, " → BLOCKED\n");
                    }
                }
            } else {
                fprintf(stderr, " → MISS (raycast failed)\n");
            }
        } else {
            /* Click in side panel — check if clicking an NPC health bar */
            for (int pi = 0; pi < v->panel_npc_count; pi++) {
                if (mpos.y >= v->panel_npc_y[pi] && mpos.y < v->panel_npc_y[pi] + 12) {
                    int ni = v->panel_npc_slot[pi];
                    p->attack_target_idx = ni;
                    p->approach_target = 1;
                    p->route_len = 0;
                    p->route_idx = 0;
                    fprintf(stderr, "PANEL CLICK → ATTACK npc_idx=%d\n", ni);
                    break;
                }
            }
        }
    }
}

/* Called EVERY FRAME for key presses. Buffers actions for next tick. */
static void process_human_keys(ViewerState* v) {
    FcPlayer* p = &v->state.player;
    if (IsKeyPressed(KEY_ONE))   v->pending_prayer = (p->prayer == PRAYER_PROTECT_MELEE) ? FC_PRAYER_OFF : FC_PRAYER_MELEE;
    if (IsKeyPressed(KEY_TWO))   v->pending_prayer = (p->prayer == PRAYER_PROTECT_RANGE) ? FC_PRAYER_OFF : FC_PRAYER_RANGE;
    if (IsKeyPressed(KEY_THREE)) v->pending_prayer = (p->prayer == PRAYER_PROTECT_MAGIC) ? FC_PRAYER_OFF : FC_PRAYER_MAGIC;
    if (IsKeyPressed(KEY_F))     v->pending_eat = FC_EAT_SHARK;
    if (IsKeyPressed(KEY_P))     v->pending_drink = FC_DRINK_PRAYER_POT;
    if (IsKeyPressed(KEY_X))     p->is_running = !p->is_running;

    /* --- Debug toggles (testing only) --- */
    /* F9: toggle godmode (player can't die) */
    if (IsKeyPressed(KEY_F9)) {
        v->godmode = !v->godmode;
        fprintf(stderr, "GODMODE: %s\n", v->godmode ? "ON" : "OFF");
    }
    /* F1-F8: spawn NPC type 1-8 near the player */
    for (int fk = 0; fk < 8; fk++) {
        if (IsKeyPressed(KEY_F1 + fk)) {
            int npc_type = fk + 1;
            /* Find free NPC slot */
            for (int si = 0; si < FC_MAX_NPCS; si++) {
                if (!v->state.npcs[si].active) {
                    int sx = p->x + 5, sy = p->y;
                    const FcNpcStats* stats = fc_npc_get_stats(npc_type);
                    /* Find nearby walkable tile */
                    for (int r = 0; r < 10; r++) {
                        for (int dx = -r; dx <= r; dx++) {
                            int ty = p->y + r, tx = p->x + dx + 3;
                            if (tx >= 0 && tx < FC_ARENA_WIDTH && ty >= 0 && ty < FC_ARENA_HEIGHT &&
                                fc_footprint_walkable(tx, ty, stats->size, v->state.walkable)) {
                                sx = tx; sy = ty; r = 99; break;
                            }
                        }
                    }
                    fc_npc_spawn(&v->state.npcs[si], npc_type, sx, sy,
                                 v->state.next_spawn_index++);
                    /* Don't increment npcs_remaining — debug spawns shouldn't
                     * affect wave progression. Wave clear checks npcs_remaining. */
                    fprintf(stderr, "DEBUG SPAWN: NPC type %d at (%d,%d)\n", npc_type, sx, sy);
                    break;
                }
            }
        }
    }
}

/* Called on TICK frames: build action array from buffered inputs. */
static void build_human_actions(ViewerState* v) {
    memset(v->actions, 0, sizeof(v->actions));
    /* Movement comes from route deque in fc_tick.c, not action head */
    v->actions[0] = FC_MOVE_IDLE;
    /* Attack comes from attack_target_idx in fc_tick.c, not action head */
    v->actions[1] = FC_ATTACK_NONE;
    /* Buffered prayer/eat/drink */
    v->actions[2] = v->pending_prayer;
    v->actions[3] = v->pending_eat;
    v->actions[4] = v->pending_drink;
    /* Clear buffers */
    v->pending_prayer = 0;
    v->pending_eat = 0;
    v->pending_drink = 0;
}

/* Entity ground Y — slightly above terrain so entities stand on the flattened cracks */
static float ground_y(ViewerState* v, int tile_x, int tile_y) {
    if (v->terrain && v->terrain->loaded) {
        return terrain_height_at(v->terrain, tile_x, tile_y) + 0.1f;
    }
    return 0.0f;
}

/* ======================================================================== */
/* Scene drawing                                                             */
/* ======================================================================== */

static void draw_scene(ViewerState* v) {
    /* Camera follows player with same interpolation */
    float cx = FC_ARENA_WIDTH*0.5f, cy = -(FC_ARENA_HEIGHT*0.5f);
    if (v->entity_count > 0) {
        float t = v->tick_frac;
        float pcx = v->prev_player_x + 0.5f;
        float pcy = v->prev_player_y + 0.5f;
        float ccx = (float)v->entities[0].x + 0.5f;
        float ccy = (float)v->entities[0].y + 0.5f;
        cx = pcx + (ccx - pcx) * t;
        cy = -(pcy + (ccy - pcy) * t);
    }
    v->camera.target = (Vector3){cx, 0.5f, cy};
    v->camera.position = (Vector3){
        cx + v->cam_dist*cosf(v->cam_pitch)*sinf(v->cam_yaw),
        v->cam_dist*sinf(v->cam_pitch),
        cy + v->cam_dist*cosf(v->cam_pitch)*cosf(v->cam_yaw) };
    BeginMode3D(v->camera);

    /* Terrain + objects */
    if (v->terrain && v->terrain->loaded) {
        rlDisableBackfaceCulling();
        DrawModel(v->terrain->model, (Vector3){0,0,0}, 1.0f, WHITE);
        rlEnableBackfaceCulling();
    }
    if (v->objects && v->objects->loaded) {
        rlDisableBackfaceCulling();
        DrawModel(v->objects->model, (Vector3){0,0,0}, 1.0f, WHITE);
        rlEnableBackfaceCulling();
    }

    /* Grid overlay */
    if (v->show_grid) {
        for (int x = 0; x <= FC_ARENA_WIDTH; x++)
            DrawLine3D((Vector3){(float)x,0.01f,0}, (Vector3){(float)x,0.01f,-(float)FC_ARENA_HEIGHT}, COL_GRID);
        for (int z = 0; z <= FC_ARENA_HEIGHT; z++)
            DrawLine3D((Vector3){0,0.01f,-(float)z}, (Vector3){(float)FC_ARENA_WIDTH,0.01f,-(float)z}, COL_GRID);
    }

    /* Collision overlay */
    if (v->show_collision) {
        for (int tx = 0; tx < FC_ARENA_WIDTH; tx++) {
            for (int ty = 0; ty < FC_ARENA_HEIGHT; ty++) {
                Color c = v->state.walkable[tx][ty] ? COL_WALKABLE : COL_BLOCKED;
                DrawCube((Vector3){tx+0.5f, 0.02f, -(ty+0.5f)}, 0.9f, 0.02f, 0.9f, c);
            }
        }
    }

    /* Entities */
    int target_entity_idx = -1;  /* for highlight ring */
    for (int i = 0; i < v->entity_count; i++) {
        FcRenderEntity* e = &v->entities[i];

        /* Interpolate position using stable slot-based prev positions */
        float cur_x = (float)e->x + (float)e->size*0.5f;
        float cur_y = (float)e->y + (float)e->size*0.5f;
        float prv_x, prv_y;
        if (e->entity_type == ENTITY_PLAYER) {
            prv_x = v->prev_player_x + (float)e->size*0.5f;
            prv_y = v->prev_player_y + (float)e->size*0.5f;
        } else {
            prv_x = v->prev_npc_x[e->npc_slot] + (float)e->size*0.5f;
            prv_y = v->prev_npc_y[e->npc_slot] + (float)e->size*0.5f;
        }
        float t = v->tick_frac;
        float ex = prv_x + (cur_x - prv_x) * t;
        float ey = -(prv_y + (cur_y - prv_y) * t);

        /* Sample terrain height at entity position */
        float gy = ground_y(v, e->x, e->y);

        if (e->entity_type == ENTITY_PLAYER) {
            /* Player model or fallback cylinder */
            NpcModelEntry* pm = (v->player_model && v->player_model->count > 0)
                ? &v->player_model->entries[0] : NULL;
            if (pm && pm->loaded) {
                Vector3 pos = {ex, gy, ey};
                /* Use the backend-computed facing angle (set per movement step or when targeting) */
                float face_angle = v->state.player.facing_angle;
                rlDisableBackfaceCulling();
                DrawModelEx(pm->model, pos, (Vector3){0,1,0}, face_angle, (Vector3){1,1,1}, WHITE);
                rlEnableBackfaceCulling();
            } else {
                DrawCylinder((Vector3){ex, gy, ey}, 0.4f, 0.4f, 2.0f, 8, COL_PLAYER);
                DrawCylinderWires((Vector3){ex, gy, ey}, 0.4f, 0.4f, 2.0f, 8, WHITE);
            }

            /* Prayer icon above player — rendered as 2D text after EndMode3D */
            /* (handled below in the 2D overlay section) */
        } else if (!e->is_dead) {
            /* NPC: try to render actual model, fallback to colored cube */
            uint32_t mid = fc_npc_type_to_model_id(e->npc_type);
            NpcModelEntry* nme = v->npc_models ? fc_npc_model_find(v->npc_models, mid) : NULL;

            if (nme) {
                /* Render NPC model facing toward player */
                Vector3 pos = {ex, gy, ey};
                float player_ex = (float)v->entities[0].x + 0.5f;
                float player_ey = -((float)v->entities[0].y + 0.5f);
                float face_angle = atan2f(player_ex - ex, player_ey - ey) * (180.0f / 3.14159f);
                rlDisableBackfaceCulling();
                DrawModelEx(nme->model, pos, (Vector3){0,1,0}, face_angle, (Vector3){1,1,1}, WHITE);
                rlEnableBackfaceCulling();
            } else {
                /* Fallback: colored cube */
                float s = (float)e->size * 0.45f;
                float h = 1.0f + (float)e->size * 0.5f;
                Color col = (e->npc_type > 0 && e->npc_type < 9) ? NPC_COLORS[e->npc_type] : GRAY;
                if (e->died_this_tick) { h *= 0.3f; col.a = 100; }
                DrawCube((Vector3){ex, gy + h*0.5f, ey}, s*2, h, s*2, col);
                DrawCubeWires((Vector3){ex, gy + h*0.5f, ey}, s*2, h, s*2, WHITE);
            }

            /* Track attack target for highlight */
            if (i > 0 && (i-1) == v->attack_target) target_entity_idx = i;
        }

        /* Blue circle indicator under NPC for visibility */
        if (e->entity_type == ENTITY_NPC && !e->is_dead) {
            float cr = (float)e->size * 0.5f;
            if (cr < 0.4f) cr = 0.4f;
            DrawCircle3D((Vector3){ex, gy + 0.05f, ey}, cr,
                         (Vector3){1,0,0}, 90.0f, CLITERAL(Color){80, 180, 255, 255});
        }

        /* HP bar — floating above entity */
        if (e->max_hp > 0 && !e->is_dead) {
            float hf = (float)e->current_hp/(float)e->max_hp;
            float bar_h = (e->entity_type==ENTITY_PLAYER) ? 1.0f + (float)e->size*0.5f : 1.0f + (float)e->size*0.5f;
            float by = gy + bar_h + 0.8f;
            float bw = (float)e->size*0.8f;
            if (bw < 0.6f) bw = 0.6f;
            DrawCube((Vector3){ex,by,ey}, bw,0.08f,0.08f, COL_HP_RED);
            DrawCube((Vector3){ex-bw*(1.0f-hf)*0.5f,by,ey}, bw*hf,0.08f,0.08f, COL_HP_GREEN);
        }
    }

    /* Attack target highlight ring */
    if (target_entity_idx >= 0) {
        FcRenderEntity* e = &v->entities[target_entity_idx];
        float ex = (float)e->x + (float)e->size*0.5f;
        float ey = -((float)e->y + (float)e->size*0.5f);
        float r = (float)e->size * 0.6f;
        DrawCircle3D((Vector3){ex, 0.05f, ey}, r, (Vector3){1,0,0}, 90.0f, COL_TARGET);
    }

    /* Draw active visual projectiles as colored spheres */
    for (int pi = 0; pi < MAX_PROJECTILES; pi++) {
        VisualProjectile* vp = &v->projectiles[pi];
        if (!vp->active) continue;
        float t = (vp->total_time > 0) ? vp->elapsed / vp->total_time : 1.0f;
        if (t > 1.0f) t = 1.0f;
        /* Lerp position with slight arc (parabolic Y offset for visual polish) */
        float px = vp->src_x + (vp->dst_x - vp->src_x) * t;
        float py = vp->src_y + (vp->dst_y - vp->src_y) * t + sinf(t * 3.14159f) * 1.5f;
        float pz = vp->src_z + (vp->dst_z - vp->src_z) * t;
        DrawSphere((Vector3){px, py, pz}, vp->radius, vp->color);
    }

    EndMode3D();

    /* Hitsplats — 2D screen-space damage numbers projected from 3D.
     * Drawn AFTER EndMode3D so they render on top of everything.
     * Red circle + white number for damage, blue circle + "0" for miss. */
    for (int i = 0; i < MAX_HITSPLATS; i++) {
        Hitsplat* h = &v->hitsplats[i];
        if (!h->active) continue;

        /* Float upward over lifetime */
        float rise = (float)(60 - h->frames_left) * 0.02f;
        float alpha = (float)h->frames_left / 60.0f;

        /* Project 3D position to 2D screen coordinates */
        Vector3 world_pos = {h->world_x, h->world_y + rise, h->world_z};
        Vector2 screen = GetWorldToScreen(world_pos, v->camera);

        /* Skip if off-screen */
        if (screen.x < -50 || screen.x > WINDOW_W + 50 ||
            screen.y < -50 || screen.y > WINDOW_H + 50) continue;

        int dmg = h->damage / 10;  /* convert tenths to whole HP */
        int sx = (int)screen.x;
        int sy = (int)screen.y;

        /* Draw OSRS-style splat: colored circle background + damage number */
        Color bg_col, text_col;
        if (h->damage > 0) {
            bg_col = (Color){187, 0, 0, (unsigned char)(alpha * 230)};      /* dark red */
            text_col = (Color){255, 255, 255, (unsigned char)(alpha * 255)}; /* white */
        } else {
            bg_col = (Color){0, 100, 200, (unsigned char)(alpha * 230)};     /* blue */
            text_col = (Color){255, 255, 255, (unsigned char)(alpha * 255)};
        }

        /* Circle background */
        DrawCircle(sx, sy, 12, bg_col);
        DrawCircleLines(sx, sy, 12, (Color){0, 0, 0, (unsigned char)(alpha * 200)});

        /* Damage number centered in circle */
        char dmg_str[8];
        snprintf(dmg_str, sizeof(dmg_str), "%d", dmg);
        int tw = MeasureText(dmg_str, 14);
        DrawText(dmg_str, sx - tw/2, sy - 7, 14, text_col);
    }

    /* Prayer overhead icon — 2D projected from player head position */
    if (v->entities[0].prayer != PRAYER_NONE && v->entity_count > 0) {
        float t = v->tick_frac;
        float p_cx = v->prev_player_x + 0.5f + ((float)v->entities[0].x - v->prev_player_x) * t;
        float p_cy = v->prev_player_y + 0.5f + ((float)v->entities[0].y - v->prev_player_y) * t;
        float p_gy = ground_y(v, v->entities[0].x, v->entities[0].y);
        Vector3 head_pos = {p_cx, p_gy + 3.0f, -p_cy};
        Vector2 scr = GetWorldToScreen(head_pos, v->camera);
        int px = (int)scr.x, py = (int)scr.y;

        /* Draw actual prayer sprite texture */
        Texture2D tex = {0};
        if (v->entities[0].prayer == PRAYER_PROTECT_MELEE && v->pray_melee_tex.id > 0)
            tex = v->pray_melee_tex;
        else if (v->entities[0].prayer == PRAYER_PROTECT_RANGE && v->pray_missiles_tex.id > 0)
            tex = v->pray_missiles_tex;
        else if (v->entities[0].prayer == PRAYER_PROTECT_MAGIC && v->pray_magic_tex.id > 0)
            tex = v->pray_magic_tex;

        if (tex.id > 0) {
            /* Scale sprite to ~28x28 pixels and center on projected position */
            float scale = 28.0f / (float)tex.width;
            int dw = (int)(tex.width * scale);
            int dh = (int)(tex.height * scale);
            DrawTextureEx(tex, (Vector2){(float)(px - dw/2), (float)(py - dh/2)},
                          0.0f, scale, WHITE);
        } else {
            /* Fallback: letter if textures not loaded */
            const char* icon_txt = "?";
            if (v->entities[0].prayer == PRAYER_PROTECT_MELEE) icon_txt = "M";
            else if (v->entities[0].prayer == PRAYER_PROTECT_RANGE) icon_txt = "R";
            else icon_txt = "W";
            DrawCircle(px, py, 14, (Color){255,255,255,220});
            int itw = MeasureText(icon_txt, 18);
            DrawText(icon_txt, px - itw/2, py - 9, 18, (Color){0,0,0,255});
        }
    }
}

/* ======================================================================== */
/* UI drawing                                                                */
/* ======================================================================== */

static void draw_header(ViewerState* v) {
    DrawRectangle(0,0,WINDOW_W,HEADER_HEIGHT,COL_HEADER);
    char b[128];
    snprintf(b,sizeof(b),"Fight Caves — Wave %d/%d  [%s]",
        v->state.current_wave, FC_NUM_WAVES, v->auto_mode ? "AUTO" : "PLAY");
    text_s(b,10,4,16,COL_TEXT_YELLOW);
    snprintf(b,sizeof(b),"Ep:%d Seed:%u Tick:%d TPS:%d",
        v->episode_count, v->seed, v->state.tick, v->tps);
    text_s(b,10,22,10,COL_TEXT_DIM);
    FcPlayer* p=&v->state.player;
    snprintf(b,sizeof(b),"HP %d/%d  Pray %d/%d",
        p->current_hp/10, p->max_hp/10, p->current_prayer/10, p->max_prayer/10);
    text_s(b,WINDOW_W-MeasureText(b,14)-10,8,14,COL_TEXT_WHITE);
}

static void draw_panel(ViewerState* v) {
    int px=FC_ARENA_WIDTH*TILE_SIZE, py=HEADER_HEIGHT;
    DrawRectangle(px,py,PANEL_WIDTH,WINDOW_H-py,COL_PANEL);
    DrawLine(px,py,px,WINDOW_H,COL_PANEL_BORDER);
    FcPlayer* p=&v->state.player; int x=px+8; char b[128];
    int by=py+6;

    /* HP bar */
    float hf=(p->max_hp>0)?(float)p->current_hp/(float)p->max_hp:0;
    DrawRectangle(x,by,PANEL_WIDTH-16,12,COL_HP_RED);
    DrawRectangle(x,by,(int)((PANEL_WIDTH-16)*hf),12,COL_HP_GREEN);
    snprintf(b,sizeof(b),"HP %d/%d",p->current_hp/10,p->max_hp/10);
    text_s(b,x+(PANEL_WIDTH-16)/2-MeasureText(b,10)/2,by+1,10,COL_TEXT_WHITE); by+=16;

    /* Prayer bar */
    float pf=(p->max_prayer>0)?(float)p->current_prayer/(float)p->max_prayer:0;
    DrawRectangle(x,by,PANEL_WIDTH-16,12,CLITERAL(Color){40,40,80,255});
    DrawRectangle(x,by,(int)((PANEL_WIDTH-16)*pf),12,COL_PRAY_BLUE);
    snprintf(b,sizeof(b),"Pray %d/%d",p->current_prayer/10,p->max_prayer/10);
    text_s(b,x+(PANEL_WIDTH-16)/2-MeasureText(b,10)/2,by+1,10,COL_TEXT_WHITE); by+=18;

    /* Wave info */
    snprintf(b,sizeof(b),"Wave %d/%d  NPCs:%d",v->state.current_wave,FC_NUM_WAVES,v->state.npcs_remaining);
    text_s(b,x,by,10,COL_TEXT_YELLOW); by+=16;

    /* Consumables */
    snprintf(b,sizeof(b),"Sharks: %d  Ppots: %d",p->sharks_remaining,p->prayer_doses_remaining);
    text_s(b,x,by,10,COL_TEXT_WHITE); by+=14;
    snprintf(b,sizeof(b),"Ammo: %d",p->ammo_count);
    text_s(b,x,by,10,COL_TEXT_WHITE); by+=18;

    /* Active prayer (highlighted) */
    text_s("Prayer:",x,by,10,COL_TEXT_DIM); by+=14;
    Color c_mel = (p->prayer==PRAYER_PROTECT_MELEE) ? COL_TEXT_YELLOW : COL_TEXT_DIM;
    Color c_rng = (p->prayer==PRAYER_PROTECT_RANGE) ? COL_TEXT_GREEN : COL_TEXT_DIM;
    Color c_mag = (p->prayer==PRAYER_PROTECT_MAGIC) ? COL_PRAY_BLUE : COL_TEXT_DIM;
    text_s("[1] Melee",x,by,10,c_mel);
    text_s("[2] Range",x+70,by,10,c_rng); by+=14;
    text_s("[3] Magic",x,by,10,c_mag); by+=18;

    /* Attack target */
    if (v->attack_target >= 0 && v->attack_target < v->entity_count - 1) {
        FcRenderEntity* tgt = &v->entities[v->attack_target + 1];
        static const char* NPC_NAMES[] = {"?","Tz-Kih","Tz-Kek","Kek-Sm","Tok-Xil",
            "MejKot","Ket-Zek","Jad","HurKot"};
        const char* name = (tgt->npc_type > 0 && tgt->npc_type < 9) ? NPC_NAMES[tgt->npc_type] : "?";
        snprintf(b,sizeof(b),"Target: %s (%d%%)", name,
            tgt->max_hp > 0 ? tgt->current_hp * 100 / tgt->max_hp : 0);
        text_s(b,x,by,10,COL_TEXT_YELLOW);
    } else {
        text_s("Target: none [Tab]",x,by,10,COL_TEXT_DIM);
    }
    by += 18;

    /* Timers */
    snprintf(b,sizeof(b),"AtkTmr:%d Food:%d Pot:%d",
        p->attack_timer, p->food_timer, p->potion_timer);
    text_s(b,x,by,10,COL_TEXT_DIM); by+=14;

    /* Live NPC health bars (clickable to target) */
    DrawLine(px+4, by, px+PANEL_WIDTH-4, by, COL_PANEL_BORDER); by += 4;
    v->panel_npc_count = 0;
    {
        static const char* NPC_SHORT[] = {"?","Tz-Kih","Tz-Kek","Kek-Sm","Tok-Xil",
            "MejKot","Ket-Zek","Jad","HurKot"};
        int shown = 0;
        for (int ni = 0; ni < FC_MAX_NPCS && shown < 8; ni++) {
            FcNpc* n = &v->state.npcs[ni];
            if (!n->active || n->is_dead) continue;
            /* Store for click detection */
            if (v->panel_npc_count < 8) {
                v->panel_npc_slot[v->panel_npc_count] = ni;
                v->panel_npc_y[v->panel_npc_count] = by;
                v->panel_npc_count++;
            }
            const char* nname = (n->npc_type > 0 && n->npc_type < 9) ? NPC_SHORT[n->npc_type] : "?";
            int is_target = (ni == v->state.player.attack_target_idx);

            /* Sword icon for current target */
            if (is_target) {
                text_s(">", x, by, 10, COL_TEXT_YELLOW);
            }

            /* NPC name */
            snprintf(b, sizeof(b), "%s", nname);
            Color name_col = is_target ? COL_TEXT_YELLOW : COL_TEXT_WHITE;
            text_s(b, x + 10, by, 9, name_col);

            /* HP bar */
            int bar_x = x + 65;
            int bar_w = PANEL_WIDTH - 82;
            float hp_frac = (n->max_hp > 0) ? (float)n->current_hp / (float)n->max_hp : 0;
            DrawRectangle(bar_x, by + 1, bar_w, 8, COL_HP_RED);
            DrawRectangle(bar_x, by + 1, (int)(bar_w * hp_frac), 8, COL_HP_GREEN);

            /* HP text */
            snprintf(b, sizeof(b), "%d", n->current_hp / 10);
            DrawText(b, bar_x + bar_w + 2, by, 9, COL_TEXT_DIM);

            by += 12;
            shown++;
        }
        if (shown == 0) {
            text_s("No NPCs alive", x, by, 9, COL_TEXT_DIM);
            by += 12;
        }
    }
    by += 4;

    /* Terminal state */
    if (v->state.terminal != TERMINAL_NONE) {
        const char* t = "???";
        Color tc = COL_HP_RED;
        switch (v->state.terminal) {
            case TERMINAL_CAVE_COMPLETE: t="VICTORY!"; tc=COL_TEXT_GREEN; break;
            case TERMINAL_PLAYER_DEATH:  t="DEATH"; break;
            case TERMINAL_TICK_CAP:      t="TICK CAP"; break;
        }
        text_s(t, x, by, 16, tc); by += 20;
        text_s("[R] to restart", x, by, 10, COL_TEXT_DIM);
    }

    /* Controls help at bottom of panel */
    int help_y = WINDOW_H - 130;
    if (help_y < by + 10) help_y = by + 10;
    DrawLine(px+4, help_y-4, px+PANEL_WIDTH-4, help_y-4, COL_PANEL_BORDER);
    text_s("--- Controls ---", x, help_y, 10, COL_TEXT_DIM); help_y += 14;
    text_s("WASD  Move", x, help_y, 9, COL_TEXT_DIM); help_y += 12;
    text_s("1/2/3 Prayer", x, help_y, 9, COL_TEXT_DIM); help_y += 12;
    text_s("F Eat  P Drink", x, help_y, 9, COL_TEXT_DIM); help_y += 12;
    text_s("Tab Target  A Auto", x, help_y, 9, COL_TEXT_DIM); help_y += 12;
    text_s("Space Pause  R Reset", x, help_y, 9, COL_TEXT_DIM); help_y += 12;
    text_s("D Debug  G Grid  C Coll", x, help_y, 9, COL_TEXT_DIM); help_y += 12;
    text_s("4 Aerial  5 Game cam", x, help_y, 9, COL_TEXT_DIM);
}

static void draw_debug(ViewerState* v) {
    if (!v->show_debug) return;
    int x=10, y=HEADER_HEIGHT+6; char b[256];
    DrawRectangle(5,y-2,500,70,CLITERAL(Color){0,0,0,200});
    snprintf(b,sizeof(b),"%s | TPS:%d | CAM p=%.2f d=%.1f",
        v->paused ? "PAUSED" : "RUNNING", v->tps, v->cam_pitch, v->cam_dist);
    text_s(b,x,y,10,COL_TEXT_GREEN); y+=14;
    snprintf(b,sizeof(b),"Hash:0x%08x Player:(%d,%d) Entities:%d",
        v->last_hash, v->state.player.x, v->state.player.y, v->entity_count);
    text_s(b,x,y,10,COL_TEXT_DIM); y+=14;
    snprintf(b,sizeof(b),"Terrain:%s Objects:%s | Mode:%s",
        (v->terrain && v->terrain->loaded)?"ON":"off",
        (v->objects && v->objects->loaded)?"ON":"off",
        v->auto_mode?"AUTO":"HUMAN");
    text_s(b,x,y,10,COL_TEXT_DIM); y+=14;
    snprintf(b,sizeof(b),"DmgDealt:%d DmgTaken:%d Kills:%d",
        v->state.damage_dealt_this_tick, v->state.damage_taken_this_tick,
        v->state.npcs_killed_this_tick);
    text_s(b,x,y,10,COL_TEXT_DIM); y+=14;
    if (v->godmode) text_s("GODMODE ON (F9)", x, y, 10, COL_TEXT_YELLOW);
    text_s("F1-F8: spawn NPC | F9: godmode", x, y + (v->godmode ? 14 : 0), 9, COL_TEXT_DIM);
}

/* ======================================================================== */
/* Main                                                                      */
/* ======================================================================== */

int main(int argc, char** argv) {
    int screenshot_mode = 0;
    const char* screenshot_path = NULL;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--screenshot") == 0 && i+1 < argc) {
            screenshot_mode = 1;
            screenshot_path = argv[++i];
        }
    }
    fprintf(stderr,"=== Fight Caves Viewer (Phase 8 — Playable) ===\n");
    SetConfigFlags(FLAG_WINDOW_RESIZABLE|FLAG_MSAA_4X_HINT);
    InitWindow(WINDOW_W, WINDOW_H, "Fight Caves RL — Playable Viewer");
    SetTargetFPS(60);

    ViewerState v; memset(&v, 0, sizeof(v));
    fc_init(&v.state);
    v.paused = 1; v.tps = 2; v.show_debug = 1; v.auto_mode = 0;
    v.attack_target = -1;
    v.cam_yaw = 0; v.cam_pitch = 0.8f; v.cam_dist = 30;
    v.camera.up = (Vector3){0,1,0}; v.camera.fovy = 32;
    v.camera.projection = CAMERA_PERSPECTIVE;

    v.terrain = load_terrain(&v);
    v.objects = load_objects_with_terrain(v.terrain);
    if (!v.terrain || !v.terrain->loaded) v.show_grid = 1;

    /* Load NPC models */
    {
        const char* npc_paths[] = {
            "demo-env/assets/fc_npcs.models",
            "../demo-env/assets/fc_npcs.models",
            "assets/fc_npcs.models", NULL };
        for (int i = 0; npc_paths[i]; i++) {
            if (FileExists(npc_paths[i])) {
                v.npc_models = fc_npc_models_load(npc_paths[i]);
                break;
            }
        }
        if (!v.npc_models) fprintf(stderr, "warning: NPC models not found\n");
    }

    /* Load player model */
    {
        const char* pm_paths[] = {
            "demo-env/assets/fc_player.models",
            "../demo-env/assets/fc_player.models",
            "assets/fc_player.models", NULL };
        for (int i = 0; pm_paths[i]; i++) {
            if (FileExists(pm_paths[i])) {
                v.player_model = fc_npc_models_load(pm_paths[i]);
                break;
            }
        }
    }

    /* Load player animations */
    {
        const char* anim_paths[] = {
            "demo-env/assets/fc_player.anims",
            "../demo-env/assets/fc_player.anims",
            "assets/fc_player.anims", NULL };
        for (int i = 0; anim_paths[i]; i++) {
            if (FileExists(anim_paths[i])) {
                v.player_anims = anim_cache_load(anim_paths[i]);
                break;
            }
        }
        /* Create animation model state from player model's vertex skins */
        if (v.player_anims && v.player_model && v.player_model->count > 0) {
            NpcModelEntry* pm = &v.player_model->entries[0];
            if (pm->loaded && pm->vertex_skins) {
                v.player_anim_state = anim_model_state_create(
                    pm->vertex_skins, pm->base_vert_count);
                v.player_anim_seq = PLAYER_ANIM_IDLE;
                v.player_anim_frame = 0;
                v.player_anim_timer = 0;
                fprintf(stderr, "Player animation state created (%d base verts)\n",
                        pm->base_vert_count);
            }
        }
    }

    /* Load prayer overhead icon textures */
    {
        const char* pray_dirs[] = {"demo-env/assets/", "../demo-env/assets/", "assets/", NULL};
        for (int i = 0; pray_dirs[i]; i++) {
            char path[256];
            snprintf(path, sizeof(path), "%spray_melee.png", pray_dirs[i]);
            if (FileExists(path)) {
                v.pray_melee_tex = LoadTexture(path);
                snprintf(path, sizeof(path), "%spray_missiles.png", pray_dirs[i]);
                v.pray_missiles_tex = LoadTexture(path);
                snprintf(path, sizeof(path), "%spray_magic.png", pray_dirs[i]);
                v.pray_magic_tex = LoadTexture(path);
                fprintf(stderr, "Prayer icons loaded from %s\n", pray_dirs[i]);
                break;
            }
        }
    }

    reset_ep(&v);
    int frame_count = 0;

    while (!WindowShouldClose()) {
        /* Screenshot mode */
        if (screenshot_mode && frame_count == 5) {
            TakeScreenshot(screenshot_path);
            fprintf(stderr, "Screenshot saved to %s\n", screenshot_path);
            break;
        }
        frame_count++;

        /* Global keys (always active) */
        if (IsKeyPressed(KEY_Q)) break;
        if (IsKeyPressed(KEY_SPACE)) v.paused = !v.paused;
        if (IsKeyPressed(KEY_RIGHT)) v.step_once = 1;
        if (IsKeyPressed(KEY_UP) && v.tps < MAX_TPS) v.tps++;
        if (IsKeyPressed(KEY_DOWN) && v.tps > MIN_TPS) v.tps--;
        if (IsKeyPressed(KEY_R)) reset_ep(&v);
        if (IsKeyPressed(KEY_KP_ADD) || (IsKeyDown(KEY_LEFT_SHIFT) && IsKeyPressed(KEY_EQUAL))) {
            if (v.tps < MAX_TPS) v.tps++;
        }

        /* Toggle keys */
        /* Auto mode removed — was too easy to accidentally toggle with 'A' key.
         * Use --auto command line flag if needed for testing. */
        if (IsKeyPressed(KEY_G)) v.show_grid = !v.show_grid;
        if (IsKeyPressed(KEY_C)) v.show_collision = !v.show_collision;
        /* D: only toggle debug when not pressing WASD */
        if (IsKeyPressed(KEY_D) && !IsKeyDown(KEY_W) && !IsKeyDown(KEY_A) && !IsKeyDown(KEY_S)) {
            /* D is also used for East movement — only toggle debug if no movement keys held */
        }
        if (IsKeyPressed(KEY_GRAVE)) v.show_debug = !v.show_debug;

        /* Camera presets */
        if (IsKeyPressed(KEY_FOUR))  { v.cam_yaw=0; v.cam_pitch=1.35f; v.cam_dist=120; }
        if (IsKeyPressed(KEY_FIVE))  { v.cam_yaw=0; v.cam_pitch=0.6f; v.cam_dist=50; }

        /* Camera orbit + zoom */
        if (IsMouseButtonDown(MOUSE_BUTTON_RIGHT)) {
            Vector2 d = GetMouseDelta();
            v.cam_yaw += d.x*0.005f; v.cam_pitch -= d.y*0.005f;
            if (v.cam_pitch < 0.1f) v.cam_pitch = 0.1f;
            if (v.cam_pitch > 1.4f) v.cam_pitch = 1.4f;
        }
        float wh = GetMouseWheelMove();
        if (wh != 0) {
            v.cam_dist *= (wh > 0) ? (1.0f/1.15f) : 1.15f;
            if (v.cam_dist < 5) v.cam_dist = 5;
            if (v.cam_dist > 300) v.cam_dist = 300;
        }

        /* Tick processing */
        int tick = 0;
        if (!v.paused) {
            v.tick_acc += GetFrameTime() * (float)v.tps;
            if (v.tick_acc >= 1.0f) { v.tick_acc -= 1.0f; tick = 1; }
            /* tick_frac: 0.0 right after tick, approaches 1.0 just before next tick */
            v.tick_frac = v.tick_acc;
            if (v.tick_frac > 1.0f) v.tick_frac = 1.0f;
        }
        if (v.step_once) { tick = 1; v.step_once = 0; }

        /* Capture clicks and key presses EVERY frame (60fps).
         * These set routes/targets/buffers on the player struct.
         * The tick loop reads them when the next tick fires. */
        if (!v.auto_mode && v.state.terminal == TERMINAL_NONE) {
            process_human_clicks(&v);
            process_human_keys(&v);
        }

        if (tick && v.state.terminal == TERMINAL_NONE) {
            /* Build action array for this tick */
            if (v.auto_mode) {
                for (int h = 0; h < FC_NUM_ACTION_HEADS; h++)
                    v.actions[h] = GetRandomValue(0, FC_ACTION_DIMS[h]-1);
                if (GetRandomValue(0,2) == 0) v.actions[0] = 0;
            } else {
                build_human_actions(&v);
            }

            /* Save previous positions by stable NPC array index */
            v.prev_player_x = (float)v.state.player.x;
            v.prev_player_y = (float)v.state.player.y;
            for (int ni = 0; ni < FC_MAX_NPCS; ni++) {
                v.prev_npc_x[ni] = (float)v.state.npcs[ni].x;
                v.prev_npc_y[ni] = (float)v.state.npcs[ni].y;
                v.prev_npc_active[ni] = v.state.npcs[ni].active;
            }

            /* Snapshot pending hit counts BEFORE tick (to detect new projectiles) */
            int prev_player_hits = v.state.player.num_pending_hits;
            int prev_npc_hits[FC_MAX_NPCS];
            for (int ni = 0; ni < FC_MAX_NPCS; ni++)
                prev_npc_hits[ni] = v.state.npcs[ni].num_pending_hits;

            /* Step simulation */
            fc_step(&v.state, v.actions);
            v.tick_frac = 0.0f;

            /* Debug: godmode — prevent player death */
            if (v.godmode && v.state.player.current_hp <= 0) {
                v.state.player.current_hp = v.state.player.max_hp;
                v.state.terminal = TERMINAL_NONE;
            }

            /* Snap prev positions for newly spawned NPCs so they don't fly.
             * An NPC that wasn't active last tick but is now = new spawn. */
            for (int ni = 0; ni < FC_MAX_NPCS; ni++) {
                if (v.state.npcs[ni].active && !v.prev_npc_active[ni]) {
                    v.prev_npc_x[ni] = (float)v.state.npcs[ni].x;
                    v.prev_npc_y[ni] = (float)v.state.npcs[ni].y;
                }
            }

            fc_fill_render_entities(&v.state, v.entities, &v.entity_count);
            v.last_hash = fc_state_hash(&v.state);

            /* Generate hitsplats and projectiles from per-tick events */
            {
                float gy_p = ground_y(&v, v.state.player.x, v.state.player.y);
                float p3x = (float)v.state.player.x + 0.5f;
                float p3y = gy_p + 1.5f;
                float p3z = -((float)v.state.player.y + 0.5f);
                float tick_sec = (v.tps > 0) ? (1.0f / (float)v.tps) : 0.5f;

                /* Player damage taken — hitsplat */
                if (v.state.player.damage_taken_this_tick > 0 || v.state.player.hit_landed_this_tick) {
                    spawn_hitsplat(&v, p3x, gy_p + 2.5f, p3z,
                                   v.state.player.damage_taken_this_tick);
                }

                /* Player fired ranged attack → ONE crossbow bolt projectile.
                 * Detect: new pending hits appeared on target NPC this tick. */
                if (v.state.player.attack_target_idx >= 0) {
                    int ti = v.state.player.attack_target_idx;
                    FcNpc* tn = &v.state.npcs[ti];
                    if (tn->active && tn->num_pending_hits > prev_npc_hits[ti]) {
                        /* New hit was queued on target NPC → player just attacked */
                        FcPendingHit* newest = &tn->pending_hits[tn->num_pending_hits - 1];
                        float n3x = (float)tn->x + (float)tn->size*0.5f;
                        float n3y = ground_y(&v, tn->x, tn->y) + 1.0f + (float)tn->size*0.3f;
                        float n3z = -((float)tn->y + (float)tn->size*0.5f);
                        float travel = (float)newest->ticks_remaining * tick_sec;
                        if (travel < 0.1f) travel = 0.1f;
                        spawn_projectile(&v, p3x, p3y, p3z, n3x, n3y, n3z,
                                         travel,
                                         CLITERAL(Color){200, 200, 50, 255}, 0.12f);
                    }
                }

                /* NPC damage taken — hitsplats (including 0-damage misses) */
                for (int i = 0; i < FC_MAX_NPCS; i++) {
                    FcNpc* n = &v.state.npcs[i];
                    /* Show splat if: took damage, died, OR had pending hits resolve
                     * (detect by: fewer pending hits than before tick) */
                    int hit_resolved = (prev_npc_hits[i] > n->num_pending_hits) ||
                                       n->damage_taken_this_tick > 0 || n->died_this_tick;
                    if (hit_resolved) {
                        float gy_n = ground_y(&v, n->x, n->y);
                        float nx = (float)n->x + (float)n->size*0.5f;
                        float nz = -((float)n->y + (float)n->size*0.5f);
                        float nh = gy_n + 1.0f + (float)n->size*0.5f;
                        spawn_hitsplat(&v, nx, nh, nz, n->damage_taken_this_tick);
                    }
                }

                /* NPC ranged/magic fired → ONE projectile per new pending hit on player.
                 * Compare player pending hit count before/after tick. */
                for (int hi = prev_player_hits; hi < v.state.player.num_pending_hits; hi++) {
                    FcPendingHit* ph = &v.state.player.pending_hits[hi];
                    if (!ph->active || ph->source_npc_idx < 0) continue;
                    if (ph->attack_style == ATTACK_MELEE) continue;  /* no projectile for melee */
                    FcNpc* src = &v.state.npcs[ph->source_npc_idx];
                    if (!src->active) continue;
                    float s3x = (float)src->x + (float)src->size*0.5f;
                    float s3y = ground_y(&v, src->x, src->y) + 1.0f + (float)src->size*0.3f;
                    float s3z = -((float)src->y + (float)src->size*0.5f);
                    float travel = (float)ph->ticks_remaining * tick_sec;
                    if (travel < 0.1f) travel = 0.1f;
                    Color pc = (ph->attack_style == ATTACK_MAGIC)
                        ? CLITERAL(Color){80, 80, 255, 255}
                        : CLITERAL(Color){80, 200, 80, 255};
                    float rad = (src->npc_type == NPC_TZTOK_JAD) ? 0.3f : 0.15f;
                    spawn_projectile(&v, s3x, s3y, s3z, p3x, p3y, p3z,
                                     travel, pc, rad);
                }
            }

            /* Sync viewer attack_target with player's backend target */
            v.attack_target = v.state.player.attack_target_idx;
            /* Auto-clear if target NPC died */
            if (v.state.player.attack_target_idx >= 0) {
                FcNpc* tn = &v.state.npcs[v.state.player.attack_target_idx];
                if (!tn->active || tn->is_dead) {
                    v.state.player.attack_target_idx = -1;
                    v.attack_target = -1;
                }
            }

            if (v.state.terminal != TERMINAL_NONE) v.paused = 1;
        }

        /* Update hitsplat lifetimes */
        for (int i = 0; i < MAX_HITSPLATS; i++) {
            if (v.hitsplats[i].active) {
                v.hitsplats[i].frames_left--;
                if (v.hitsplats[i].frames_left <= 0) v.hitsplats[i].active = 0;
            }
        }

        /* Update visual projectiles */
        {
            float dt = GetFrameTime();
            for (int i = 0; i < MAX_PROJECTILES; i++) {
                if (v.projectiles[i].active) {
                    v.projectiles[i].elapsed += dt;
                    if (v.projectiles[i].elapsed >= v.projectiles[i].total_time) {
                        v.projectiles[i].active = 0;
                    }
                }
            }
        }

        /* Update player animation */
        if (v.player_anim_state && v.player_anims && v.player_model &&
            v.player_model->count > 0) {
            NpcModelEntry* pm = &v.player_model->entries[0];
            float dt = GetFrameTime();

            /* Select animation based on player state.
             * attack_timer == 5 means player JUST fired (crossbow speed = 5 ticks).
             * hit_landed_this_tick fires for both player attacks AND incoming hits,
             * so we don't use it for the attack animation. */
            uint16_t desired_seq = PLAYER_ANIM_IDLE;
            if (v.state.terminal == TERMINAL_PLAYER_DEATH) {
                desired_seq = PLAYER_ANIM_DEATH;
            } else if (v.state.player.food_eaten_this_tick) {
                desired_seq = PLAYER_ANIM_EAT;
            } else if (v.state.player.attack_timer == 5) {
                desired_seq = PLAYER_ANIM_ATTACK;
            } else if (v.state.movement_this_tick) {
                desired_seq = v.state.player.is_running ? PLAYER_ANIM_RUN : PLAYER_ANIM_WALK;
            }

            /* Switch animation if needed */
            if (desired_seq != v.player_anim_seq) {
                v.player_anim_seq = desired_seq;
                v.player_anim_frame = 0;
                v.player_anim_timer = 0;
            }

            /* Advance frame timer */
            AnimSequence* seq = anim_get_sequence(v.player_anims, v.player_anim_seq);
            if (seq && seq->frame_count > 0) {
                v.player_anim_timer -= dt;
                if (v.player_anim_timer <= 0) {
                    v.player_anim_frame = (v.player_anim_frame + 1) % seq->frame_count;
                    /* Delay in game ticks → seconds: delay * 0.6 / game_speed
                     * At TPS=2, each game tick = 0.5s; at TPS=1 = 1s. Use 0.02s per frame for smooth playback. */
                    float frame_delay = (float)seq->frames[v.player_anim_frame].delay * 0.02f;
                    if (frame_delay < 0.016f) frame_delay = 0.016f;
                    v.player_anim_timer = frame_delay;
                }

                /* Apply animation frame to model */
                AnimFrameData* frame_data = &seq->frames[v.player_anim_frame].frame;
                AnimFrameBase* fb = anim_get_framebase(v.player_anims, frame_data->framebase_id);
                if (fb) {
                    anim_apply_frame(v.player_anim_state, pm->base_verts, frame_data, fb);

                    /* Re-expand into mesh vertices and scale to tile units */
                    float* mesh_verts = pm->model.meshes[0].vertices;
                    anim_update_mesh(mesh_verts, v.player_anim_state,
                                     pm->face_indices, pm->face_count);

                    /* Scale from OSRS units to tile units (same as initial load) */
                    int evc = pm->face_count * 3;
                    for (int vi = 0; vi < evc; vi++) {
                        mesh_verts[vi*3+0] /=  128.0f;
                        mesh_verts[vi*3+1] /=  128.0f;
                        mesh_verts[vi*3+2] /= -128.0f;
                    }

                    UpdateMeshBuffer(pm->model.meshes[0], 0, mesh_verts,
                                     evc * 3 * sizeof(float), 0);
                }
            }
        }

        /* Draw */
        BeginDrawing();
        ClearBackground(COL_BG);
        draw_scene(&v);
        draw_header(&v);
        draw_panel(&v);
        draw_debug(&v);

        /* Status bar */
        const char* status = v.paused ? "PAUSED — [Space] resume, [Right] step" :
                             v.auto_mode ? "AUTO mode — [A] switch to manual" :
                             "Left-click:move  Click NPC:attack  1/2/3:pray  F:eat  P:pot";
        DrawText(status, 10, WINDOW_H-14, 10, CLITERAL(Color){80,80,90,255});

        /* Big centered pause prompt */
        if (v.paused && v.state.terminal == TERMINAL_NONE) {
            const char* msg = "Press SPACE to start";
            int mw = MeasureText(msg, 30);
            int mx = (FC_ARENA_WIDTH * TILE_SIZE - mw) / 2;
            int my = WINDOW_H / 2 - 15;
            DrawRectangle(mx - 10, my - 5, mw + 20, 40, CLITERAL(Color){0,0,0,180});
            DrawText(msg, mx+1, my+1, 30, COL_TEXT_SHADOW);
            DrawText(msg, mx, my, 30, COL_TEXT_YELLOW);
        }

        EndDrawing();
    }

    if (v.pray_melee_tex.id > 0) UnloadTexture(v.pray_melee_tex);
    if (v.pray_missiles_tex.id > 0) UnloadTexture(v.pray_missiles_tex);
    if (v.pray_magic_tex.id > 0) UnloadTexture(v.pray_magic_tex);
    if (v.player_anim_state) anim_model_state_free(v.player_anim_state);
    if (v.player_anims) anim_cache_free(v.player_anims);
    if (v.player_model) fc_npc_models_unload(v.player_model);
    if (v.npc_models) fc_npc_models_unload(v.npc_models);
    if (v.objects && v.objects->loaded) { UnloadModel(v.objects->model); free(v.objects); }
    if (v.terrain && v.terrain->loaded) { UnloadModel(v.terrain->model); free(v.terrain->heightmap); free(v.terrain); }
    CloseWindow();
    return 0;
}
