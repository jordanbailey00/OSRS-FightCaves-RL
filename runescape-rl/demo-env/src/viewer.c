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
#include "fc_terrain_loader.h"
#include "fc_objects_loader.h"
#include "fc_npc_models.h"
#include "fc_anim_loader.h"
#include "fc_debug_overlay.h"
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

/* NPC animation sequence IDs (from osrs-dumps seq.sym) */
/* NPC animation IDs — Jad uses lordmagmus anims (same model as FC Jad in cache) */
static const uint16_t NPC_ANIM_IDLE[] = {
    0, 2618, 2624, 2624, 2631, 2636, 2642, 2650, 2636  /* indexed by NPC type 0-8 */
};
static const uint16_t NPC_ANIM_WALK[] = {
    0, 2619, 2623, 2623, 2632, 2634, 2643, 2651, 2634
};
static const uint16_t NPC_ANIM_ATTACK[] = {
    0, 2621, 2625, 2625, 2628, 2637, 2644, 2655, 2637
};
static const uint16_t NPC_ANIM_DEATH[] = {
    0, 2620, 2627, 2627, 2630, 2638, 2646, 2654, 2638
};

/* Projectile spotanim IDs for model lookup */
#define PROJ_CROSSBOW_BOLT  27
#define PROJ_TOK_XIL_SPINE  443
#define PROJ_KET_ZEK_FIRE   445
#define PROJ_JAD_RANGED     440
#define PROJ_JAD_MAGIC      439

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
    float src_x, src_y, src_z;
    float dst_x, dst_y, dst_z;
    float total_time;
    float elapsed;
    Color color;
    float radius;
    uint32_t spot_id;            /* spotanim ID for model lookup (0 = use sphere) */
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
    NpcModelSet* projectile_models;
    /* Animation cache (shared by player + all NPCs) */
    AnimCache* anim_cache;
    /* Player animation state */
    AnimModelState* player_anim_state;
    uint16_t player_anim_seq;
    int player_anim_frame;
    float player_anim_timer;
    /* Per-NPC animation state */
    AnimModelState* npc_anim_states[FC_MAX_NPCS];
    uint16_t npc_anim_seq[FC_MAX_NPCS];
    int npc_anim_frame[FC_MAX_NPCS];
    float npc_anim_timer[FC_MAX_NPCS];
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
    /* Side panel tabs (Phase 8h) */
    int active_tab;     /* 0=inventory, 1=combat, 2=prayer */
    int tab_area_y;     /* screen Y where tab buttons start (set during draw) */
    int combat_style;   /* 0=accurate, 1=rapid, 2=long range */
    /* Item / tab sprites */
    Texture2D tex_ppot, tex_shark;
    Texture2D tex_pray_melee_on, tex_pray_melee_off;
    Texture2D tex_pray_range_on, tex_pray_range_off;
    Texture2D tex_pray_magic_on, tex_pray_magic_off;
    Texture2D tex_tab_inv, tex_tab_combat, tex_tab_prayer;
    /* Agent action test mode (Phase 9a verification) */
    int test_mode;       /* 1 = running scripted agent tests */
    int test_id;         /* current test index */
    int test_tick;       /* ticks elapsed in current test */
    /* Debug overlay (Phase 9c) — toggled with O key */
    int dbg_flags;       /* bitmask of DBG_* flags from fc_debug_overlay.h */
    int dbg_tab;         /* 0=player, 1=obs, 2=mask, 3=reward (side panel debug tab) */
    int dbg_tab_y;       /* screen Y where debug tab buttons start (set during draw) */
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

/* Forward declarations */
static int process_tab_click(ViewerState* v, float mx, float my);

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
    dbg_log_clear();
    /* Initialize prev positions */
    v->prev_player_x = (float)v->state.player.x;
    v->prev_player_y = (float)v->state.player.y;
    /* Reset NPC animation states */
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        v->prev_npc_x[i] = (float)v->state.npcs[i].x;
        v->prev_npc_y[i] = (float)v->state.npcs[i].y;
        v->prev_npc_active[i] = v->state.npcs[i].active;
        if (v->npc_anim_states[i]) {
            anim_model_state_free(v->npc_anim_states[i]);
            v->npc_anim_states[i] = NULL;
        }
        v->npc_anim_seq[i] = 0;
        v->npc_anim_frame[i] = 0;
        v->npc_anim_timer[i] = 0;
    }
}

static void spawn_projectile(ViewerState* v,
                             float sx, float sy, float sz,
                             float dx, float dy, float dz,
                             float travel_secs, Color col, float radius,
                             uint32_t spot_id) {
    for (int i = 0; i < MAX_PROJECTILES; i++) {
        if (!v->projectiles[i].active) {
            VisualProjectile* vp = &v->projectiles[i];
            vp->active = 1;
            vp->src_x = sx; vp->src_y = sy; vp->src_z = sz;
            vp->dst_x = dx; vp->dst_y = dy; vp->dst_z = dz;
            vp->total_time = travel_secs; vp->elapsed = 0;
            vp->color = col; vp->radius = radius;
            vp->spot_id = spot_id;
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
            /* Click in side panel — try tabs first, then NPC health bars */
            if (!process_tab_click(v, mpos.x, mpos.y)) {
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
}

/* Agent test data (defined here so key handler can reference it) */
typedef struct {
    const char* name;
    const char* desc;
    int duration;
    int actions[7];
} AgentTest;

static const AgentTest AGENT_TESTS[] = {
    /* --- Movement tests (PASSED) ---
    { "Walk North",     "Head 0 = 1 (walk N, 3 ticks)",        3,  {1, 0,0,0,0, 0,0} },
    { "Walk East",      "Head 0 = 3 (walk E, 3 ticks)",        3,  {3, 0,0,0,0, 0,0} },
    { "Walk South",     "Head 0 = 5 (walk S, 3 ticks)",        3,  {5, 0,0,0,0, 0,0} },
    { "Walk West",      "Head 0 = 7 (walk W, 3 ticks)",        3,  {7, 0,0,0,0, 0,0} },
    { "Walk NE",        "Head 0 = 2 (walk NE, 3 ticks)",       3,  {2, 0,0,0,0, 0,0} },
    { "Walk SE",        "Head 0 = 4 (walk SE, 3 ticks)",       3,  {4, 0,0,0,0, 0,0} },
    { "Walk SW",        "Head 0 = 6 (walk SW, 3 ticks)",       3,  {6, 0,0,0,0, 0,0} },
    { "Walk NW",        "Head 0 = 8 (walk NW, 3 ticks)",       3,  {8, 0,0,0,0, 0,0} },
    { "Run North",      "Head 0 = 9 (run N, 3 ticks = 6 tiles)",  3,  {9, 0,0,0,0, 0,0} },
    { "Run SE",         "Head 0 = 12 (run SE, 3 ticks = 6 tiles)", 3,  {12,0,0,0,0, 0,0} },
    { "Walk-to-tile",   "Heads 5+6 = (26,31) -> tile (25,30)",  10, {0, 0,0,0,0, 26,31} },
    { "Walk-to-tile 2", "Heads 5+6 = (36,36) -> tile (35,35)",  10, {0, 0,0,0,0, 36,36} },
    */

    /* --- Combat tests (PASSED) ---
    { "Attack slot 1",  "Head 1=1: target closest NPC, auto-approach+attack", 15, {0, 1,0,0,0, 0,0} },
    { "Switch to slot 2","Head 1=2: retarget to 2nd closest NPC",             10, {0, 2,0,0,0, 0,0} },
    { "Attack + walk",  "Head 1=1 + walk S: attack while moving south",       10, {5, 1,0,0,0, 0,0} },
    */

    /* --- Prayer tests (PASSED) ---
    { "Prot Magic",     "Head 2=2: activate Protect from Magic",               3, {0, 0,2,0,0, 0,0} },
    { "Prot Range",     "Head 2=3: switch to Protect from Range",              3, {0, 0,3,0,0, 0,0} },
    { "Prot Melee",     "Head 2=4: switch to Protect from Melee",              3, {0, 0,4,0,0, 0,0} },
    { "Prayer off",     "Head 2=1: deactivate prayer",                         3, {0, 0,1,0,0, 0,0} },
    */

    /* --- Consumable tests (PASSED) ---
    { "Eat shark",      "Head 3=1: eat shark, watch HP heal +20",              5, {0, 0,0,1,0, 0,0} },
    { "Eat cooldown",   "Head 3=1: try eat again (3-tick cooldown, may fail)", 2, {0, 0,0,1,0, 0,0} },
    { "Drink ppot",     "Head 4=1: drink prayer pot, watch prayer restore",    5, {0, 0,0,0,1, 0,0} },
    { "Drink cooldown", "Head 4=1: try drink again (2-tick cooldown)",         2, {0, 0,0,0,1, 0,0} },
    { "Eat + drink",    "Head 3=1 + 4=1: both same tick (separate cooldowns)", 5, {0, 0,0,1,1, 0,0} },
    */

    /* --- Combined tests (PASSED) ---
    { "Run+eat+pray",   "Run N + eat shark + prot magic (all same tick)",      5, {9, 0,2,1,0, 0,0} },
    { "Attack+pray+pot","Attack slot 1 + prot range + drink ppot",            10, {0, 1,3,0,1, 0,0} },
    { "WalkTile+attack","Walk to (30,30) + attack slot 1 (walk cancels)",      8, {0, 1,0,0,0, 31,31} },
    */

    /* --- Debug overlay tests (9c-A: Collision/LOS/Path/Range) --- */
    /* Press O to toggle overlays ON before starting these tests */
    { "Collision",      "Press O first! Green=walkable, red=blocked tiles",     5, {0, 0,0,0,0, 0,0} },
    { "LOS rays",       "Green lines=LOS clear, red=blocked. Walk near NPCs",  8, {5, 0,0,0,0, 0,0} },
    { "Path viz",       "Walk to tile (30,25) — yellow path shows route",       10,{0, 0,0,0,0, 31,26} },
    { "Attack range",   "Blue ring = 7-tile crossbow range around player",      5, {0, 0,0,0,0, 0,0} },
};
#define NUM_AGENT_TESTS (int)(sizeof(AGENT_TESTS)/sizeof(AGENT_TESTS[0]))

/* Called EVERY FRAME for key presses. Buffers actions for next tick. */
static void process_human_keys(ViewerState* v) {
    FcPlayer* p = &v->state.player;
    if (IsKeyPressed(KEY_ONE))   v->pending_prayer = (p->prayer == PRAYER_PROTECT_MELEE) ? FC_PRAYER_OFF : FC_PRAYER_MELEE;
    if (IsKeyPressed(KEY_TWO))   v->pending_prayer = (p->prayer == PRAYER_PROTECT_RANGE) ? FC_PRAYER_OFF : FC_PRAYER_RANGE;
    if (IsKeyPressed(KEY_THREE)) v->pending_prayer = (p->prayer == PRAYER_PROTECT_MAGIC) ? FC_PRAYER_OFF : FC_PRAYER_MAGIC;
    if (IsKeyPressed(KEY_F))     v->pending_eat = FC_EAT_SHARK;
    if (IsKeyPressed(KEY_P))     v->pending_drink = FC_DRINK_PRAYER_POT;
    if (IsKeyPressed(KEY_X))     p->is_running = !p->is_running;

    /* T: agent action test mode — start or advance to next test */
    if (IsKeyPressed(KEY_T)) {
        if (!v->test_mode) {
            /* Start test mode from current test_id */
            v->test_mode = 1;
            v->test_tick = 0;
            v->paused = 0;
            fprintf(stderr, "TEST MODE: starting test %d/%d\n", v->test_id + 1, NUM_AGENT_TESTS);
        } else if (v->test_tick >= AGENT_TESTS[v->test_id].duration) {
            /* Current test done — advance to next */
            v->test_id++;
            v->test_tick = 0;
            if (v->test_id >= NUM_AGENT_TESTS) {
                v->test_mode = 0;
                fprintf(stderr, "TEST MODE: all tests complete\n");
            } else {
                v->paused = 0;
                fprintf(stderr, "TEST MODE: starting test %d/%d\n", v->test_id + 1, NUM_AGENT_TESTS);
            }
        }
    }

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

/* ======================================================================== */
/* Agent action test mode (Phase 9a verification)                           */
/* ======================================================================== */

/* Build actions for current test. Returns 1 if test is active, 0 if done. */
static int build_test_actions(ViewerState* v) {
    if (!v->test_mode) return 0;
    if (v->test_id >= NUM_AGENT_TESTS) {
        v->test_mode = 0;
        return 0;
    }

    const AgentTest* t = &AGENT_TESTS[v->test_id];

    if (v->test_tick >= t->duration) {
        /* Test finished — pause and wait for user to advance */
        v->paused = 1;
        return 0;
    }

    /* Send the test actions */
    memset(v->actions, 0, sizeof(v->actions));
    for (int i = 0; i < 7; i++) {
        v->actions[i] = t->actions[i];
    }

    /* For walk-to-tile tests, only send the coordinates on the first tick
     * (the route persists, subsequent ticks just let it play out) */
    if (v->test_tick > 0 && (t->actions[5] > 0 || t->actions[6] > 0)) {
        v->actions[5] = 0;
        v->actions[6] = 0;
    }

    v->test_tick++;
    return 1;
}

/* Draw test overlay showing what's being tested */
static void draw_test_overlay(ViewerState* v) {
    if (!v->test_mode && v->test_id == 0) return;
    if (v->test_id >= NUM_AGENT_TESTS) return;

    const AgentTest* t = &AGENT_TESTS[v->test_id];

    int bx = 10, bw = 460, bh = 60;
    int arena_bottom = HEADER_HEIGHT + FC_ARENA_HEIGHT * TILE_SIZE;
    int by = arena_bottom - bh - 10;
    DrawRectangle(bx, by, bw, bh, CLITERAL(Color){0,0,0,200});
    DrawRectangleLinesEx((Rectangle){(float)bx,(float)by,(float)bw,(float)bh}, 2,
                         COL_TEXT_YELLOW);

    char buf[128];
    snprintf(buf, sizeof(buf), "TEST %d/%d: %s", v->test_id + 1, NUM_AGENT_TESTS, t->name);
    DrawText(buf, bx + 8, by + 6, 16, COL_TEXT_YELLOW);
    DrawText(t->desc, bx + 8, by + 26, 12, COL_TEXT_WHITE);

    if (v->paused && v->test_tick >= t->duration) {
        snprintf(buf, sizeof(buf), "DONE — press T for next test   (tick %d/%d)",
                 v->test_tick, t->duration);
        DrawText(buf, bx + 8, by + 42, 11, COL_TEXT_GREEN);
    } else {
        snprintf(buf, sizeof(buf), "Running tick %d/%d   Player: (%d,%d)",
                 v->test_tick, t->duration,
                 v->state.player.x, v->state.player.y);
        DrawText(buf, bx + 8, by + 42, 11, COL_TEXT_DIM);
    }
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

    /* Draw active visual projectiles — use 3D model if available, sphere fallback */
    for (int pi = 0; pi < MAX_PROJECTILES; pi++) {
        VisualProjectile* vp = &v->projectiles[pi];
        if (!vp->active) continue;
        float t = (vp->total_time > 0) ? vp->elapsed / vp->total_time : 1.0f;
        if (t > 1.0f) t = 1.0f;
        float px = vp->src_x + (vp->dst_x - vp->src_x) * t;
        float py = vp->src_y + (vp->dst_y - vp->src_y) * t + sinf(t * 3.14159f) * 1.5f;
        float pz = vp->src_z + (vp->dst_z - vp->src_z) * t;

        /* Try to render actual projectile model */
        NpcModelEntry* pm = (vp->spot_id > 0 && v->projectile_models)
            ? fc_npc_model_find(v->projectile_models, vp->spot_id) : NULL;
        if (pm && pm->loaded) {
            /* Rotate projectile to face travel direction */
            float ddx = vp->dst_x - vp->src_x;
            float ddz = vp->dst_z - vp->src_z;
            float angle = atan2f(ddx, ddz) * (180.0f / 3.14159f);
            rlDisableBackfaceCulling();
            DrawModelEx(pm->model, (Vector3){px, py, pz},
                        (Vector3){0,1,0}, angle, (Vector3){1,1,1}, WHITE);
            rlEnableBackfaceCulling();
        } else {
            DrawSphere((Vector3){px, py, pz}, vp->radius, vp->color);
        }
    }

    /* Debug overlays — 3D collision tiles (before EndMode3D) */
    if (v->dbg_flags) debug_overlay_3d(&v->state, v->dbg_flags);

    EndMode3D();

    /* Debug overlays — 2D screen-space (LOS, path, range — after EndMode3D) */
    if (v->dbg_flags) debug_overlay_screen(&v->state, v->camera, v->dbg_flags);

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

/* ======================================================================== */
/* Side panel tab contents (Phase 8h)                                       */
/* ======================================================================== */

/* Tab button colors */
#define COL_TAB_ACTIVE   CLITERAL(Color){ 82, 73, 61, 255 }
#define COL_TAB_INACTIVE CLITERAL(Color){ 52, 45, 35, 255 }
#define COL_TAB_HOVER    CLITERAL(Color){ 72, 63, 51, 255 }
#define COL_SLOT_BG      CLITERAL(Color){ 40, 35, 28, 255 }
#define COL_SLOT_HOVER   CLITERAL(Color){ 60, 52, 40, 255 }
#define COL_SLOT_EMPTY   CLITERAL(Color){ 30, 26, 20, 255 }
#define COL_PRAY_ACTIVE  CLITERAL(Color){ 60, 120, 200, 200 }
#define COL_PRAY_BUTTON  CLITERAL(Color){ 50, 44, 36, 255 }
#define COL_COMBAT_BTN   CLITERAL(Color){ 50, 44, 36, 255 }

/* Inventory slot dimensions */
#define INV_COLS   4
#define INV_ROWS   7
#define INV_SLOTS  28

/* Helper: draw a texture scaled to fit a rectangle, centered */
static void draw_tex_fit(Texture2D tex, int dx, int dy, int dw, int dh, Color tint) {
    if (tex.id == 0) return;
    Rectangle src = { 0, 0, (float)tex.width, (float)tex.height };
    /* Fit within dw x dh preserving aspect ratio */
    float sx = (float)dw / (float)tex.width;
    float sy = (float)dh / (float)tex.height;
    float s = (sx < sy) ? sx : sy;
    int rw = (int)(tex.width * s);
    int rh = (int)(tex.height * s);
    Rectangle dst = { (float)(dx + (dw - rw) / 2), (float)(dy + (dh - rh) / 2), (float)rw, (float)rh };
    DrawTexturePro(tex, src, dst, (Vector2){0,0}, 0, tint);
}

/* Inventory slot layout:
 *   Slots 0-7:   prayer potions (8 pots, 4 doses each)
 *   Slots 8-27:  sharks (20 sharks)
 * Consumed items empty from the end of their group. */
static int draw_inventory_tab(ViewerState* v, int px, int x, int by) {
    FcPlayer* p = &v->state.player;
    char b[32];

    int doses = p->prayer_doses_remaining;  /* 0..32 */
    int sharks = p->sharks_remaining;       /* 0..20 */

    int slot_w = (PANEL_WIDTH - 20) / INV_COLS;
    int slot_h = 32;
    int icon_sz = 20;  /* sprite draw size within slot */

    for (int row = 0; row < INV_ROWS; row++) {
        for (int col = 0; col < INV_COLS; col++) {
            int slot = row * INV_COLS + col;
            int sx = x + col * slot_w;
            int sy = by + row * (slot_h + 2);
            Rectangle sr = { (float)sx, (float)sy, (float)slot_w - 2, (float)slot_h };

            int has_item = 0;
            int is_ppot = 0;
            int ppot_dose = 0;  /* 1-4, or 0 = vial */
            int is_shark = 0;

            if (slot < 8) {
                /* Prayer potion slots (0-7) */
                int full_pots = doses / 4;
                int partial = doses % 4;
                int consumed_pots = 8 - full_pots - (partial > 0 ? 1 : 0);
                if (slot < full_pots) {
                    has_item = 1; is_ppot = 1; ppot_dose = 4;
                } else if (slot == full_pots && partial > 0) {
                    has_item = 1; is_ppot = 1; ppot_dose = partial;
                } else if (slot < 8 && slot >= full_pots + (partial > 0 ? 1 : 0)) {
                    /* Consumed pot → vial */
                    has_item = 1; is_ppot = 1; ppot_dose = 0;
                }
            } else {
                int shark_idx = slot - 8;
                if (shark_idx < sharks) {
                    has_item = 1; is_shark = 1;
                }
            }

            /* Draw slot background */
            int hovered = CheckCollisionPointRec(GetMousePosition(), sr);
            Color bg;
            if (is_ppot && ppot_dose == 0) {
                bg = COL_SLOT_EMPTY;  /* vial = dark */
            } else if (has_item) {
                bg = hovered ? COL_SLOT_HOVER : COL_SLOT_BG;
            } else {
                bg = COL_SLOT_EMPTY;
            }
            DrawRectangleRec(sr, bg);
            DrawRectangleLinesEx(sr, 1, COL_PANEL_BORDER);

            if (is_ppot && ppot_dose > 0) {
                /* Prayer potion with sprite + dose label */
                Color tint = WHITE;
                if (ppot_dose == 3) tint = CLITERAL(Color){220,220,255,255};
                if (ppot_dose == 2) tint = CLITERAL(Color){200,200,240,255};
                if (ppot_dose == 1) tint = CLITERAL(Color){180,180,220,255};
                draw_tex_fit(v->tex_ppot, sx + 1, sy + 1, icon_sz, slot_h - 6, tint);
                snprintf(b, sizeof(b), "(%d)", ppot_dose);
                int tw = MeasureText(b, 9);
                text_s(b, sx + slot_w - 2 - tw - 2, sy + slot_h - 12, 9, COL_PRAY_BLUE);
            } else if (is_ppot && ppot_dose == 0) {
                /* Empty vial */
                text_s("Vial", sx + 6, sy + 10, 8, COL_TEXT_DIM);
            } else if (is_shark) {
                draw_tex_fit(v->tex_shark, sx + 2, sy + 2, slot_w - 6, slot_h - 4, WHITE);
            }
        }
    }
    return by + INV_ROWS * (slot_h + 2);
}

static int draw_combat_tab(ViewerState* v, int px, int x, int by) {
    FcPlayer* p = &v->state.player;
    char b[128];

    /* Weapon */
    text_s("Rune crossbow", x, by, 10, COL_TEXT_YELLOW); by += 16;
    DrawLine(px+4, by-2, px+PANEL_WIDTH-4, by-2, COL_PANEL_BORDER);

    /* Attack styles — read from v->combat_style */
    text_s("Attack style:", x, by, 9, COL_TEXT_DIM); by += 14;
    static const char* styles[] = { "Accurate", "Rapid", "Long range" };
    static const char* style_desc[] = { "+3 Ranged", "1 tick faster", "+2 Defence" };
    for (int i = 0; i < 3; i++) {
        int btn_y = by;
        int btn_w = PANEL_WIDTH - 20;
        int btn_h = 28;
        Rectangle br = { (float)x, (float)btn_y, (float)btn_w, (float)btn_h };
        int hovered = CheckCollisionPointRec(GetMousePosition(), br);
        int selected = (i == v->combat_style);
        Color bg = selected ? COL_TAB_ACTIVE : (hovered ? COL_TAB_HOVER : COL_COMBAT_BTN);
        DrawRectangleRec(br, bg);
        Color border = selected ? COL_TEXT_YELLOW : COL_PANEL_BORDER;
        DrawRectangleLinesEx(br, selected ? 2 : 1, border);
        Color tc = selected ? COL_TEXT_YELLOW : COL_TEXT_WHITE;
        text_s(styles[i], x + 8, btn_y + 4, 10, tc);
        text_s(style_desc[i], x + 8, btn_y + 16, 8, selected ? COL_TEXT_DIM : CLITERAL(Color){100,90,80,255});
        by += btn_h + 3;
    }
    by += 6;

    /* Auto retaliate */
    text_s("Auto retaliate:", x, by, 9, COL_TEXT_DIM);
    text_s("ON", x + 96, by, 9, COL_TEXT_GREEN);
    by += 18;

    /* Weapon stats */
    DrawLine(px+4, by-2, px+PANEL_WIDTH-4, by-2, COL_PANEL_BORDER);
    int spd = (v->combat_style == 1) ? 5 : 6;  /* Rapid = 5 ticks, others = 6 */
    snprintf(b, sizeof(b), "Speed: %d ticks (%s)", spd, styles[v->combat_style]);
    text_s(b, x, by, 9, COL_TEXT_DIM); by += 12;
    snprintf(b, sizeof(b), "Range: 7 tiles");
    text_s(b, x, by, 9, COL_TEXT_DIM); by += 12;
    snprintf(b, sizeof(b), "Ammo: %d bolts", p->ammo_count);
    text_s(b, x, by, 9, COL_TEXT_WHITE); by += 16;

    /* Current target */
    DrawLine(px+4, by-2, px+PANEL_WIDTH-4, by-2, COL_PANEL_BORDER);
    if (p->attack_target_idx >= 0) {
        static const char* NPC_NAMES[] = {"?","Tz-Kih","Tz-Kek","Kek-Sm","Tok-Xil",
            "MejKot","Ket-Zek","Jad","HurKot"};
        FcNpc* tgt = &v->state.npcs[p->attack_target_idx];
        const char* name = (tgt->npc_type > 0 && tgt->npc_type < 9) ? NPC_NAMES[tgt->npc_type] : "?";
        snprintf(b, sizeof(b), "Target: %s", name);
        text_s(b, x, by, 10, COL_TEXT_YELLOW); by += 14;
        int bar_w = PANEL_WIDTH - 20;
        float hp_frac = (tgt->max_hp > 0) ? (float)tgt->current_hp / (float)tgt->max_hp : 0;
        DrawRectangle(x, by, bar_w, 10, COL_HP_RED);
        DrawRectangle(x, by, (int)(bar_w * hp_frac), 10, COL_HP_GREEN);
        snprintf(b, sizeof(b), "%d/%d", tgt->current_hp / 10, tgt->max_hp / 10);
        text_s(b, x + bar_w / 2 - MeasureText(b, 9) / 2, by + 1, 9, COL_TEXT_WHITE);
        by += 14;
        int dist = fc_distance_to_npc(p->x, p->y, tgt);
        snprintf(b, sizeof(b), "Distance: %d tiles", dist);
        text_s(b, x, by, 9, COL_TEXT_DIM); by += 12;
    } else {
        text_s("Target: none [Tab]", x, by, 10, COL_TEXT_DIM); by += 14;
    }
    return by;
}

static int draw_prayer_tab(ViewerState* v, int px, int x, int by) {
    FcPlayer* p = &v->state.player;
    char b[64];

    /* Prayer points */
    snprintf(b, sizeof(b), "Prayer: %d / %d", p->current_prayer / 10, p->max_prayer / 10);
    text_s(b, x, by, 10, COL_PRAY_BLUE); by += 16;

    /* Drain info */
    if (p->prayer != PRAYER_NONE) {
        int resistance = 60 + 2 * p->prayer_bonus;
        snprintf(b, sizeof(b), "Drain rate: 12 / %d resist", resistance);
        text_s(b, x, by, 8, COL_TEXT_DIM);
    } else {
        text_s("No prayer active", x, by, 8, COL_TEXT_DIM);
    }
    by += 14;

    DrawLine(px+4, by-2, px+PANEL_WIDTH-4, by-2, COL_PANEL_BORDER);
    by += 4;

    /* Prayer buttons — 3 clickable buttons with icon sprites */
    static const char* pray_names[] = { "Prot. Melee", "Prot. Range", "Prot. Magic" };
    static const int pray_vals[] = { PRAYER_PROTECT_MELEE, PRAYER_PROTECT_RANGE, PRAYER_PROTECT_MAGIC };
    Color pray_colors[] = { COL_TEXT_YELLOW, COL_TEXT_GREEN, COL_PRAY_BLUE };

    /* On/off texture pairs indexed by prayer [melee=0, range=1, magic=2] */
    Texture2D tex_on[] = { v->tex_pray_melee_on, v->tex_pray_range_on, v->tex_pray_magic_on };
    Texture2D tex_off[] = { v->tex_pray_melee_off, v->tex_pray_range_off, v->tex_pray_magic_off };

    int btn_w = PANEL_WIDTH - 20;
    int btn_h = 34;
    int no_points = (p->current_prayer <= 0);

    for (int i = 0; i < 3; i++) {
        int btn_y = by;
        Rectangle br = { (float)x, (float)btn_y, (float)btn_w, (float)btn_h };
        int is_active = (p->prayer == pray_vals[i]);
        int hovered = CheckCollisionPointRec(GetMousePosition(), br);

        Color bg;
        if (no_points) {
            bg = COL_SLOT_EMPTY;
        } else if (is_active) {
            bg = COL_PRAY_ACTIVE;
        } else if (hovered) {
            bg = COL_TAB_HOVER;
        } else {
            bg = COL_PRAY_BUTTON;
        }
        DrawRectangleRec(br, bg);
        DrawRectangleLinesEx(br, is_active ? 2 : 1, is_active ? pray_colors[i] : COL_PANEL_BORDER);

        /* Prayer icon sprite */
        Texture2D icon = is_active ? tex_on[i] : tex_off[i];
        Color icon_tint = no_points ? CLITERAL(Color){80,80,80,255} : WHITE;
        draw_tex_fit(icon, x + 4, btn_y + 2, 30, 30, icon_tint);

        /* Label */
        Color tc = no_points ? COL_TEXT_DIM : (is_active ? COL_TEXT_WHITE : pray_colors[i]);
        text_s(pray_names[i], x + 38, btn_y + 7, 10, tc);

        /* Hotkey + status */
        snprintf(b, sizeof(b), "[%d]", i + 1);
        text_s(b, x + 38, btn_y + 21, 8, COL_TEXT_DIM);
        if (is_active) {
            text_s("ACTIVE", x + btn_w - 44, btn_y + 21, 8, COL_TEXT_WHITE);
        }

        by += btn_h + 3;
    }

    by += 6;
    snprintf(b, sizeof(b), "Prayer bonus: +%d", p->prayer_bonus);
    text_s(b, x, by, 8, COL_TEXT_DIM); by += 12;
    return by;
}

/* Handle clicks in the tab area. Returns 1 if click was consumed. */
static int process_tab_click(ViewerState* v, float mx, float my) {
    int px = FC_ARENA_WIDTH * TILE_SIZE;
    int x = px + 8;
    int tab_y = v->tab_area_y;
    if (tab_y <= 0) return 0;  /* tabs not drawn yet */
    FcPlayer* p = &v->state.player;

    /* Tab buttons: 3 equal-width buttons at tab_y */
    int tab_w = (PANEL_WIDTH - 12) / 3;
    int tab_h = 18;
    for (int t = 0; t < 3; t++) {
        int tx = px + 4 + t * tab_w;
        if (mx >= tx && mx < tx + tab_w && my >= tab_y && my < tab_y + tab_h) {
            v->active_tab = t;
            return 1;
        }
    }

    /* Content area starts below tab buttons */
    int content_y = tab_y + tab_h + 4;
    if (my < content_y) return 0;

    if (v->active_tab == 0) {
        /* Inventory tab clicks */
        int slot_w = (PANEL_WIDTH - 20) / INV_COLS;
        int slot_h = 32;
        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                int sx = x + col * slot_w;
                int sy = content_y + row * (slot_h + 2);
                if (mx >= sx && mx < sx + slot_w - 2 && my >= sy && my < sy + slot_h) {
                    int slot = row * INV_COLS + col;
                    if (slot < 8) {
                        /* Prayer potion slot — check if it has doses */
                        int full_pots = p->prayer_doses_remaining / 4;
                        int partial = p->prayer_doses_remaining % 4;
                        if (slot < full_pots || (slot == full_pots && partial > 0)) {
                            v->pending_drink = FC_DRINK_PRAYER_POT;
                            return 1;
                        }
                    } else {
                        /* Shark slot */
                        int shark_idx = slot - 8;
                        if (shark_idx < p->sharks_remaining) {
                            v->pending_eat = FC_EAT_SHARK;
                            return 1;
                        }
                    }
                }
            }
        }
    } else if (v->active_tab == 1) {
        /* Combat tab clicks — attack style buttons */
        /* Skip weapon label (16px) + separator + "Attack style:" (14px) = 30px offset */
        int btn_w = PANEL_WIDTH - 20;
        int btn_h = 28;
        int btn_start = content_y + 30;
        for (int i = 0; i < 3; i++) {
            int btn_y = btn_start + i * (btn_h + 3);
            if (mx >= x && mx < x + btn_w && my >= btn_y && my < btn_y + btn_h) {
                v->combat_style = i;
                return 1;
            }
        }
    } else if (v->active_tab == 2) {
        /* Prayer tab clicks — check prayer buttons */
        int btn_w = PANEL_WIDTH - 20;
        int btn_h = 34;
        /* Skip past prayer points text + drain info + separator (approx 34px) */
        int btn_start = content_y + 34;
        for (int i = 0; i < 3; i++) {
            int btn_y = btn_start + i * (btn_h + 3);
            if (mx >= x && mx < x + btn_w && my >= btn_y && my < btn_y + btn_h) {
                if (p->current_prayer > 0) {
                    static const int pray_enum[] = { PRAYER_PROTECT_MELEE, PRAYER_PROTECT_RANGE, PRAYER_PROTECT_MAGIC };
                    static const int pray_action[] = { FC_PRAYER_MELEE, FC_PRAYER_RANGE, FC_PRAYER_MAGIC };
                    if (p->prayer == pray_enum[i]) {
                        v->pending_prayer = FC_PRAYER_OFF;
                    } else {
                        v->pending_prayer = pray_action[i];
                    }
                    return 1;
                }
            }
        }
    }

    /* Debug panel tab clicks */
    if (v->dbg_flags && v->dbg_tab_y > 0) {
        int dtab_w = (PANEL_WIDTH - 12) / 5;
        int dtab_h = 16;
        int dtab_btn_y = v->dbg_tab_y + 2;
        for (int t = 0; t < 5; t++) {
            int tx = px + 4 + t * dtab_w;
            if (mx >= tx && mx < tx + dtab_w && my >= dtab_btn_y && my < dtab_btn_y + dtab_h) {
                v->dbg_tab = t;
                return 1;
            }
        }
    }

    return 0;
}

/* ======================================================================== */
/* Main panel draw                                                           */
/* ======================================================================== */

/* Draw NPC health bars (clickable to target). Returns Y position after bars. */
static int draw_npc_bars(ViewerState* v, int px, int x, int by) {
    char b[128];
    DrawLine(px+4, by, px+PANEL_WIDTH-4, by, COL_PANEL_BORDER); by += 4;
    v->panel_npc_count = 0;
    static const char* NPC_SHORT[] = {"?","Tz-Kih","Tz-Kek","Kek-Sm","Tok-Xil",
        "MejKot","Ket-Zek","Jad","HurKot"};
    int shown = 0;
    for (int ni = 0; ni < FC_MAX_NPCS && shown < 8; ni++) {
        FcNpc* n = &v->state.npcs[ni];
        if (!n->active || n->is_dead) continue;
        if (v->panel_npc_count < 8) {
            v->panel_npc_slot[v->panel_npc_count] = ni;
            v->panel_npc_y[v->panel_npc_count] = by;
            v->panel_npc_count++;
        }
        const char* nname = (n->npc_type > 0 && n->npc_type < 9) ? NPC_SHORT[n->npc_type] : "?";
        int is_target = (ni == v->state.player.attack_target_idx);
        if (is_target) text_s(">", x, by, 10, COL_TEXT_YELLOW);
        Color name_col = is_target ? COL_TEXT_YELLOW : COL_TEXT_WHITE;
        text_s(nname, x + 10, by, 9, name_col);
        int bar_x = x + 65;
        int bar_w = PANEL_WIDTH - 82;
        float hp_frac = (n->max_hp > 0) ? (float)n->current_hp / (float)n->max_hp : 0;
        DrawRectangle(bar_x, by + 1, bar_w, 8, COL_HP_RED);
        DrawRectangle(bar_x, by + 1, (int)(bar_w * hp_frac), 8, COL_HP_GREEN);
        snprintf(b, sizeof(b), "%d", n->current_hp / 10);
        DrawText(b, bar_x + bar_w + 2, by, 9, COL_TEXT_DIM);
        by += 12;
        shown++;
    }
    if (shown == 0) {
        text_s("No NPCs alive", x, by, 9, COL_TEXT_DIM);
        by += 12;
    }
    return by + 4;
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

    /* Wave info + timers */
    snprintf(b,sizeof(b),"Wave %d/%d  NPCs:%d",v->state.current_wave,FC_NUM_WAVES,v->state.npcs_remaining);
    text_s(b,x,by,10,COL_TEXT_YELLOW); by+=14;
    snprintf(b,sizeof(b),"AtkTmr:%d Food:%d Pot:%d",
        p->attack_timer, p->food_timer, p->potion_timer);
    text_s(b,x,by,10,COL_TEXT_DIM); by+=14;

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
        text_s("[R] to restart", x, by, 10, COL_TEXT_DIM); by += 16;
    }

    /* ---- Tab buttons ---- */
    DrawLine(px+4, by, px+PANEL_WIDTH-4, by, COL_PANEL_BORDER); by += 2;
    v->tab_area_y = by;

    int tab_w = (PANEL_WIDTH - 12) / 3;
    int tab_h = 18;
    static const char* tab_labels[] = { "Inven", "Combat", "Prayer" };
    Texture2D tab_icons[] = { v->tex_tab_inv, v->tex_tab_combat, v->tex_tab_prayer };
    for (int t = 0; t < 3; t++) {
        int tx = px + 4 + t * tab_w;
        Rectangle tr = { (float)tx, (float)by, (float)tab_w, (float)tab_h };
        int hovered = CheckCollisionPointRec(GetMousePosition(), tr);
        Color bg = (t == v->active_tab) ? COL_TAB_ACTIVE : (hovered ? COL_TAB_HOVER : COL_TAB_INACTIVE);
        DrawRectangleRec(tr, bg);
        Color tc = (t == v->active_tab) ? COL_TEXT_YELLOW : COL_TEXT_DIM;
        if (tab_icons[t].id > 0) {
            Color icon_tint = (t == v->active_tab) ? WHITE : CLITERAL(Color){160,160,160,255};
            draw_tex_fit(tab_icons[t], tx + 2, by + 1, 16, 16, icon_tint);
            text_s(tab_labels[t], tx + 20, by + 5, 8, tc);
        } else {
            int tw = MeasureText(tab_labels[t], 9);
            text_s(tab_labels[t], tx + (tab_w - tw) / 2, by + 5, 9, tc);
        }
        if (t == v->active_tab) {
            DrawLine(tx + 2, by + tab_h - 1, tx + tab_w - 2, by + tab_h - 1, COL_TEXT_YELLOW);
        }
    }
    by += tab_h + 4;

    /* ---- Tab content ---- */
    int tab_end_y = by;
    switch (v->active_tab) {
        case 0: tab_end_y = draw_inventory_tab(v, px, x, by); break;
        case 1: tab_end_y = draw_combat_tab(v, px, x, by); break;
        case 2: tab_end_y = draw_prayer_tab(v, px, x, by); break;
    }

    /* ---- NPC health bars (always visible, below tab content) ---- */
    int npc_end_y = draw_npc_bars(v, px, x, tab_end_y);

    /* ---- Debug info tabs (Phase 9c, shown when O is toggled on) ---- */
    if (v->dbg_flags) {
        v->dbg_tab_y = npc_end_y;
        dbg_draw_panel_tabs(&v->state, px, x, npc_end_y, PANEL_WIDTH, v->dbg_tab);
    } else {
        v->dbg_tab_y = 0;
    }
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

    /* Load animations (combined NPC + player) */
    {
        const char* anim_paths[] = {
            "demo-env/assets/fc_all.anims",
            "../demo-env/assets/fc_all.anims",
            "assets/fc_all.anims", NULL };
        for (int i = 0; anim_paths[i]; i++) {
            if (FileExists(anim_paths[i])) {
                v.anim_cache = anim_cache_load(anim_paths[i]);
                break;
            }
        }
        /* Create player animation state */
        if (v.anim_cache && v.player_model && v.player_model->count > 0) {
            NpcModelEntry* pm = &v.player_model->entries[0];
            if (pm->loaded && pm->vertex_skins) {
                v.player_anim_state = anim_model_state_create(
                    pm->vertex_skins, pm->base_vert_count);
                v.player_anim_seq = PLAYER_ANIM_IDLE;
                fprintf(stderr, "Player animation state created (%d base verts)\n",
                        pm->base_vert_count);
            }
        }
        /* Create NPC animation states */
        if (v.anim_cache && v.npc_models) {
            for (int i = 0; i < v.npc_models->count; i++) {
                NpcModelEntry* nm = &v.npc_models->entries[i];
                if (nm->loaded && nm->vertex_skins) {
                    /* Find which NPC type this model corresponds to */
                    for (int t = 1; t <= 8; t++) {
                        if (fc_npc_type_to_model_id(t) == nm->model_id) {
                            /* Store anim state indexed by model entry, not NPC type */
                            break;
                        }
                    }
                }
            }
        }
    }

    /* Load projectile models */
    {
        const char* proj_paths[] = {
            "demo-env/assets/fc_projectiles.models",
            "../demo-env/assets/fc_projectiles.models",
            "assets/fc_projectiles.models", NULL };
        for (int i = 0; proj_paths[i]; i++) {
            if (FileExists(proj_paths[i])) {
                v.projectile_models = fc_npc_models_load(proj_paths[i]);
                break;
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

    /* Load tab/inventory sprites (Phase 8h) */
    {
        const char* spr_dirs[] = {"demo-env/assets/sprites/", "../demo-env/assets/sprites/", "assets/sprites/", NULL};
        for (int i = 0; spr_dirs[i]; i++) {
            char path[256];
            snprintf(path, sizeof(path), "%sprayer_potion.png", spr_dirs[i]);
            if (FileExists(path)) {
                v.tex_ppot = LoadTexture(path);
                snprintf(path, sizeof(path), "%sshark.png", spr_dirs[i]);
                v.tex_shark = LoadTexture(path);
                snprintf(path, sizeof(path), "%sprotect_melee_on.png", spr_dirs[i]);
                v.tex_pray_melee_on = LoadTexture(path);
                snprintf(path, sizeof(path), "%sprotect_melee_off.png", spr_dirs[i]);
                v.tex_pray_melee_off = LoadTexture(path);
                snprintf(path, sizeof(path), "%sprotect_missiles_on.png", spr_dirs[i]);
                v.tex_pray_range_on = LoadTexture(path);
                snprintf(path, sizeof(path), "%sprotect_missiles_off.png", spr_dirs[i]);
                v.tex_pray_range_off = LoadTexture(path);
                snprintf(path, sizeof(path), "%sprotect_magic_on.png", spr_dirs[i]);
                v.tex_pray_magic_on = LoadTexture(path);
                snprintf(path, sizeof(path), "%sprotect_magic_off.png", spr_dirs[i]);
                v.tex_pray_magic_off = LoadTexture(path);
                snprintf(path, sizeof(path), "%stab_inventory.png", spr_dirs[i]);
                v.tex_tab_inv = LoadTexture(path);
                snprintf(path, sizeof(path), "%stab_combat.png", spr_dirs[i]);
                v.tex_tab_combat = LoadTexture(path);
                snprintf(path, sizeof(path), "%stab_prayer.png", spr_dirs[i]);
                v.tex_tab_prayer = LoadTexture(path);
                fprintf(stderr, "Tab sprites loaded from %s\n", spr_dirs[i]);
                break;
            }
        }
    }

    v.combat_style = 1;  /* Rapid default */

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
        /* O: cycle debug overlay modes. O=all on/off, Shift+O=cycle sub-modes */
        if (IsKeyPressed(KEY_O)) {
            if (IsKeyDown(KEY_LEFT_SHIFT) || IsKeyDown(KEY_RIGHT_SHIFT)) {
                /* Cycle through individual modes */
                if (v.dbg_flags == 0) v.dbg_flags = DBG_COLLISION;
                else if (v.dbg_flags == DBG_COLLISION) v.dbg_flags = DBG_LOS;
                else if (v.dbg_flags == DBG_LOS) v.dbg_flags = DBG_PATH | DBG_RANGE;
                else if (v.dbg_flags == (DBG_PATH | DBG_RANGE)) v.dbg_flags = DBG_ENTITY_INFO;
                else if (v.dbg_flags == DBG_ENTITY_INFO) v.dbg_flags = DBG_OBS | DBG_MASK | DBG_REWARD;
                else v.dbg_flags = 0;
            } else {
                /* Toggle all on/off */
                v.dbg_flags = v.dbg_flags ? 0 : DBG_ALL;
            }
        }
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
            if (v.test_mode && build_test_actions(&v)) {
                /* Test mode provided actions */
            } else if (v.auto_mode) {
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

            /* Debug event log — record events from this tick */
            if (v.dbg_flags) dbg_log_tick(&v.state);

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
                 * Detect: attack_timer just reset to 5 (player fired this tick). */
                if (v.state.player.attack_target_idx >= 0 && v.state.player.attack_timer == 5) {
                    int ti = v.state.player.attack_target_idx;
                    FcNpc* tn = &v.state.npcs[ti];
                    if (tn->active) {
                        float n3x = (float)tn->x + (float)tn->size*0.5f;
                        float n3y = ground_y(&v, tn->x, tn->y) + 1.0f + (float)tn->size*0.3f;
                        float n3z = -((float)tn->y + (float)tn->size*0.5f);
                        int pdist = fc_distance_to_npc(v.state.player.x, v.state.player.y, tn);
                        float travel = (float)fc_ranged_hit_delay(pdist) * tick_sec;
                        if (travel < 0.1f) travel = 0.1f;
                        spawn_projectile(&v, p3x, p3y, p3z, n3x, n3y, n3z,
                                         travel,
                                         CLITERAL(Color){200, 200, 50, 255}, 0.12f,
                                         PROJ_CROSSBOW_BOLT);
                    }
                }

                /* NPC damage taken — hitsplats */
                for (int i = 0; i < FC_MAX_NPCS; i++) {
                    FcNpc* n = &v.state.npcs[i];
                    if (!n->active && !n->died_this_tick && n->damage_taken_this_tick == 0) continue;
                    int hit_resolved = n->damage_taken_this_tick > 0 || n->died_this_tick ||
                                       (prev_npc_hits[i] > n->num_pending_hits);
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
                    /* Pick projectile spot_id based on NPC type and attack style */
                    uint32_t spot = 0;
                    if (src->npc_type == NPC_TOK_XIL) spot = PROJ_TOK_XIL_SPINE;
                    else if (src->npc_type == NPC_KET_ZEK) spot = PROJ_KET_ZEK_FIRE;
                    else if (src->npc_type == NPC_TZTOK_JAD && ph->attack_style == ATTACK_MAGIC)
                        spot = PROJ_JAD_MAGIC;
                    else if (src->npc_type == NPC_TZTOK_JAD && ph->attack_style == ATTACK_RANGED)
                        spot = PROJ_JAD_RANGED;
                    spawn_projectile(&v, s3x, s3y, s3z, p3x, p3y, p3z,
                                     travel, pc, rad, spot);
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
        if (v.player_anim_state && v.anim_cache && v.player_model &&
            v.player_model->count > 0) {
            NpcModelEntry* pm = &v.player_model->entries[0];
            float dt = GetFrameTime();

            /* Select animation based on player state.
             * Attack anim plays while attack_timer > 2 (first 3 ticks of the 5-tick cooldown).
             * This gives the animation time to play before returning to idle. */
            uint16_t desired_seq = PLAYER_ANIM_IDLE;
            if (v.state.terminal == TERMINAL_PLAYER_DEATH) {
                desired_seq = PLAYER_ANIM_DEATH;
            } else if (v.state.player.food_eaten_this_tick) {
                desired_seq = PLAYER_ANIM_EAT;
            } else if (v.state.player.attack_timer > 2) {
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
            AnimSequence* seq = anim_get_sequence(v.anim_cache, v.player_anim_seq);
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
                AnimFrameBase* fb = anim_get_framebase(v.anim_cache, frame_data->framebase_id);
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

        /* Update NPC animations */
        if (v.anim_cache && v.npc_models) {
            float dt = GetFrameTime();
            for (int ni = 0; ni < FC_MAX_NPCS; ni++) {
                FcNpc* n = &v.state.npcs[ni];
                if (!n->active && !n->died_this_tick) {
                    /* NPC gone — free anim state */
                    if (v.npc_anim_states[ni]) {
                        anim_model_state_free(v.npc_anim_states[ni]);
                        v.npc_anim_states[ni] = NULL;
                    }
                    continue;
                }

                /* Find model entry for this NPC type */
                uint32_t mid = fc_npc_type_to_model_id(n->npc_type);
                NpcModelEntry* nme = fc_npc_model_find(v.npc_models, mid);
                if (!nme || !nme->loaded || !nme->vertex_skins) continue;

                /* Lazy-create anim state on first use */
                if (!v.npc_anim_states[ni]) {
                    v.npc_anim_states[ni] = anim_model_state_create(
                        nme->vertex_skins, nme->base_vert_count);
                    v.npc_anim_seq[ni] = (n->npc_type > 0 && n->npc_type < 9)
                        ? NPC_ANIM_IDLE[n->npc_type] : 0;
                    v.npc_anim_frame[ni] = 0;
                    v.npc_anim_timer[ni] = 0;
                }

                /* Select animation based on NPC state */
                uint16_t desired = (n->npc_type > 0 && n->npc_type < 9)
                    ? NPC_ANIM_IDLE[n->npc_type] : 0;
                if (n->is_dead || n->died_this_tick) {
                    desired = (n->npc_type > 0 && n->npc_type < 9)
                        ? NPC_ANIM_DEATH[n->npc_type] : 0;
                } else if (n->damage_taken_this_tick > 0) {
                    /* NPC just got hit — brief defend/flinch, handled by staying on current */
                } else if (n->attack_timer == n->attack_speed) {
                    /* NPC just attacked */
                    desired = (n->npc_type > 0 && n->npc_type < 9)
                        ? NPC_ANIM_ATTACK[n->npc_type] : 0;
                } else if (v.state.movement_this_tick) {
                    /* Check if this NPC moved (compare prev position) */
                    if (v.prev_npc_x[ni] != (float)n->x || v.prev_npc_y[ni] != (float)n->y) {
                        desired = (n->npc_type > 0 && n->npc_type < 9)
                            ? NPC_ANIM_WALK[n->npc_type] : 0;
                    }
                }

                if (desired != v.npc_anim_seq[ni] && desired != 0) {
                    v.npc_anim_seq[ni] = desired;
                    v.npc_anim_frame[ni] = 0;
                    v.npc_anim_timer[ni] = 0;
                }

                /* Advance frame and apply animation */
                AnimSequence* seq = anim_get_sequence(v.anim_cache, v.npc_anim_seq[ni]);
                if (seq && seq->frame_count > 0) {
                    v.npc_anim_timer[ni] -= dt;
                    if (v.npc_anim_timer[ni] <= 0) {
                        v.npc_anim_frame[ni] = (v.npc_anim_frame[ni] + 1) % seq->frame_count;
                        float frame_delay = (float)seq->frames[v.npc_anim_frame[ni]].delay * 0.02f;
                        if (frame_delay < 0.016f) frame_delay = 0.016f;
                        v.npc_anim_timer[ni] = frame_delay;
                    }

                    AnimFrameData* fd = &seq->frames[v.npc_anim_frame[ni]].frame;
                    AnimFrameBase* fb = anim_get_framebase(v.anim_cache, fd->framebase_id);
                    if (fb) {
                        anim_apply_frame(v.npc_anim_states[ni], nme->base_verts, fd, fb);
                        float* mv = nme->model.meshes[0].vertices;
                        anim_update_mesh(mv, v.npc_anim_states[ni],
                                         nme->face_indices, nme->face_count);
                        int evc = nme->face_count * 3;
                        for (int vi = 0; vi < evc; vi++) {
                            mv[vi*3+0] /=  128.0f;
                            mv[vi*3+1] /=  128.0f;
                            mv[vi*3+2] /= -128.0f;
                        }
                        UpdateMeshBuffer(nme->model.meshes[0], 0, mv,
                                         evc * 3 * sizeof(float), 0);
                    }
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
        draw_test_overlay(&v);

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
    /* Phase 8h sprites */
    if (v.tex_ppot.id > 0) UnloadTexture(v.tex_ppot);
    if (v.tex_shark.id > 0) UnloadTexture(v.tex_shark);
    if (v.tex_pray_melee_on.id > 0) UnloadTexture(v.tex_pray_melee_on);
    if (v.tex_pray_melee_off.id > 0) UnloadTexture(v.tex_pray_melee_off);
    if (v.tex_pray_range_on.id > 0) UnloadTexture(v.tex_pray_range_on);
    if (v.tex_pray_range_off.id > 0) UnloadTexture(v.tex_pray_range_off);
    if (v.tex_pray_magic_on.id > 0) UnloadTexture(v.tex_pray_magic_on);
    if (v.tex_pray_magic_off.id > 0) UnloadTexture(v.tex_pray_magic_off);
    if (v.tex_tab_inv.id > 0) UnloadTexture(v.tex_tab_inv);
    if (v.tex_tab_combat.id > 0) UnloadTexture(v.tex_tab_combat);
    if (v.tex_tab_prayer.id > 0) UnloadTexture(v.tex_tab_prayer);
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        if (v.npc_anim_states[i]) anim_model_state_free(v.npc_anim_states[i]);
    }
    if (v.player_anim_state) anim_model_state_free(v.player_anim_state);
    if (v.anim_cache) anim_cache_free(v.anim_cache);
    if (v.projectile_models) fc_npc_models_unload(v.projectile_models);
    if (v.player_model) fc_npc_models_unload(v.player_model);
    if (v.npc_models) fc_npc_models_unload(v.npc_models);
    if (v.objects && v.objects->loaded) { UnloadModel(v.objects->model); free(v.objects); }
    if (v.terrain && v.terrain->loaded) { UnloadModel(v.terrain->model); free(v.terrain->heightmap); free(v.terrain); }
    CloseWindow();
    return 0;
}
