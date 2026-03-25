/*
 * viewer.c — Fight Caves Raylib viewer (PR8c: asset-integrated rendering).
 *
 * Renders fc_core state via fc_fill_render_entities. No gameplay logic.
 *
 * PR8c integration:
 *   - NPC 3D models loaded from fight_caves_npcs.models (MDL2 binary)
 *   - Prayer icons loaded from exported PNG sprites
 *   - Procedural arena terrain (TzHaar cave palette) as documented fallback
 *     (terrain export blocked by VoidPS floor definition format mismatch)
 *   - OSRS-style HUD/GUI from PufferLib patterns
 *
 * Controls:
 *   Space     — pause/resume
 *   Right     — single-step (while paused)
 *   Up/Down   — increase/decrease tick speed
 *   R         — reset episode
 *   D         — toggle debug overlay
 *   T         — toggle 3D/2D NPC rendering
 *   Q / Esc   — quit
 *   Scroll    — zoom in/out
 *   Mid-drag  — pan camera
 *   Right-drag — orbit camera (3D mode)
 */

#include "raylib.h"
#include "raymath.h"
#include "rlgl.h"
#include "fc_types.h"
#include "fc_contracts.h"
#include "fc_api.h"
#include "fc_model_loader.h"
#include "fc_terrain_loader.h"
#include <stdio.h>
#include <string.h>
#include <math.h>

/* ======================================================================== */
/* Layout                                                                    */
/* ======================================================================== */

#define TILE_SIZE           16
#define PANEL_WIDTH         220
#define HEADER_HEIGHT       40
#define WINDOW_W            (FC_ARENA_WIDTH * TILE_SIZE + PANEL_WIDTH)
#define WINDOW_H            (FC_ARENA_HEIGHT * TILE_SIZE + HEADER_HEIGHT)
#define MAX_TICKS_PER_SEC   30
#define MIN_TICKS_PER_SEC   1

/* OSRS model coordinate scale: 128 units = 1 tile */
#define OSRS_SCALE          (1.0f / 128.0f)

/* ======================================================================== */
/* OSRS color palette                                                        */
/* ======================================================================== */

#define COL_FLOOR_1     CLITERAL(Color){ 120, 90, 50, 255 }
#define COL_FLOOR_2     CLITERAL(Color){ 105, 78, 42, 255 }
#define COL_LAVA        CLITERAL(Color){ 210, 60, 15, 255 }
#define COL_LAVA_LIGHT  CLITERAL(Color){ 240, 100, 20, 255 }
#define COL_ROCK        CLITERAL(Color){ 45, 35, 25, 255 }
#define COL_ROCK_EDGE   CLITERAL(Color){ 65, 50, 35, 255 }
#define COL_BORDER      CLITERAL(Color){ 30, 20, 15, 255 }
#define COL_BG_DARK     CLITERAL(Color){ 62, 53, 41, 255 }
#define COL_BG_SLOT     CLITERAL(Color){ 56, 48, 38, 255 }
#define COL_GUI_BORDER  CLITERAL(Color){ 42, 36, 28, 255 }
#define COL_GUI_BORDER_LT CLITERAL(Color){ 100, 90, 70, 255 }
#define COL_TEXT_YELLOW CLITERAL(Color){ 255, 255, 0, 255 }
#define COL_TEXT_ORANGE CLITERAL(Color){ 255, 152, 31, 255 }
#define COL_TEXT_WHITE  CLITERAL(Color){ 255, 255, 255, 255 }
#define COL_TEXT_GREEN  CLITERAL(Color){ 0, 255, 0, 255 }
#define COL_TEXT_RED    CLITERAL(Color){ 255, 0, 0, 255 }
#define COL_TEXT_CYAN   CLITERAL(Color){ 0, 255, 255, 255 }
#define COL_TEXT_SHADOW CLITERAL(Color){ 0, 0, 0, 255 }
#define COL_HP_GREEN    CLITERAL(Color){ 0, 146, 0, 255 }
#define COL_HP_RED      CLITERAL(Color){ 160, 0, 0, 255 }
#define COL_PRAYER_BLUE CLITERAL(Color){ 50, 120, 210, 255 }
#define COL_HEADER_BG   CLITERAL(Color){ 30, 30, 40, 255 }
#define COL_PLAYER      CLITERAL(Color){ 80, 140, 255, 255 }
#define COL_SPLAT_RED   CLITERAL(Color){ 200, 20, 20, 255 }
#define COL_SPLAT_BLUE  CLITERAL(Color){ 50, 80, 200, 255 }

/* NPC type → OSRS NPC ID mapping (for model lookup) */
static const uint16_t NPC_TYPE_TO_OSRS_ID[] = {
    0,     /* NPC_NONE */
    2734,  /* NPC_TZ_KIH */
    2736,  /* NPC_TZ_KEK */
    2738,  /* NPC_TZ_KEK_SM */
    2739,  /* NPC_TOK_XIL */
    2741,  /* NPC_YT_MEJKOT */
    2743,  /* NPC_KET_ZEK */
    2745,  /* NPC_TZTOK_JAD */
    2746,  /* NPC_YT_HURKOT */
};

/* NPC type → model ID (from fc_npc_models.h) */
static const uint32_t NPC_TYPE_TO_MODEL_ID[] = {
    0,      /* NPC_NONE */
    34252,  /* NPC_TZ_KIH */
    34251,  /* NPC_TZ_KEK */
    34250,  /* NPC_TZ_KEK_SM */
    34133,  /* NPC_TOK_XIL */
    34135,  /* NPC_YT_MEJKOT */
    34137,  /* NPC_KET_ZEK */
    34131,  /* NPC_TZTOK_JAD */
    34136,  /* NPC_YT_HURKOT */
};

/* NPC fallback colors (when model not found) */
static Color npc_color(int t) {
    switch (t) {
        case NPC_TZ_KIH:    return CLITERAL(Color){180,80,40,255};
        case NPC_TZ_KEK:    return CLITERAL(Color){140,110,60,255};
        case NPC_TZ_KEK_SM: return CLITERAL(Color){160,130,70,255};
        case NPC_TOK_XIL:   return CLITERAL(Color){90,160,60,255};
        case NPC_YT_MEJKOT: return CLITERAL(Color){60,130,180,255};
        case NPC_KET_ZEK:   return CLITERAL(Color){130,50,170,255};
        case NPC_TZTOK_JAD: return CLITERAL(Color){220,150,30,255};
        case NPC_YT_HURKOT: return CLITERAL(Color){220,200,60,255};
        default: return GRAY;
    }
}

static const char* npc_name(int t) {
    switch (t) {
        case NPC_TZ_KIH: return "Tz-Kih"; case NPC_TZ_KEK: return "Tz-Kek";
        case NPC_TZ_KEK_SM: return "Tz-Kek"; case NPC_TOK_XIL: return "Tok-Xil";
        case NPC_YT_MEJKOT: return "Yt-MejKot"; case NPC_KET_ZEK: return "Ket-Zek";
        case NPC_TZTOK_JAD: return "TzTok-Jad"; case NPC_YT_HURKOT: return "Yt-HurKot";
        default: return "NPC";
    }
}

/* ======================================================================== */
/* Hit splat animation                                                       */
/* ======================================================================== */

typedef struct { int active, damage, ticks_remaining, entity_idx; float y_offset; } SplatAnim;

/* ======================================================================== */
/* Viewer state                                                              */
/* ======================================================================== */

typedef struct {
    FcState state;
    FcRenderEntity entities[FC_MAX_RENDER_ENTITIES];
    int entity_count;

    int paused, step_once, ticks_per_sec;
    float tick_accumulator;
    int show_debug, use_3d_models;

    Camera2D camera2d;
    Camera3D camera3d;
    float cam_yaw, cam_pitch, cam_dist;

    int actions[FC_NUM_ACTION_HEADS];
    uint32_t seed, last_hash;
    int episode_count;

    SplatAnim splats[FC_MAX_RENDER_ENTITIES * 4];
    int num_splats;

    /* Assets */
    FcModelCache* model_cache;
    FcTerrain terrain;
    Texture2D pray_melee_tex, pray_range_tex, pray_magic_tex;
    Texture2D pray_melee_off_tex, pray_range_off_tex, pray_magic_off_tex;
    Texture2D tab_inv_tex, tab_pray_tex, tab_combat_tex;
    int sprites_loaded;

    /* Tabbed UI */
    int active_tab;  /* 0=inventory, 1=prayer, 2=combat */

    /* Pending human action (from UI clicks) */
    int pending_prayer_action;  /* 0=none, FC_PRAYER_* value */
    int pending_eat_action;     /* 0=none, FC_EAT_* value */
    int pending_drink_action;   /* 0=none, FC_DRINK_* value */
} ViewerState;

/* ======================================================================== */
/* Text with shadow                                                          */
/* ======================================================================== */

static void text_s(const char* t, int x, int y, int sz, Color c) {
    DrawText(t, x+1, y+1, sz, COL_TEXT_SHADOW);
    DrawText(t, x, y, sz, c);
}

/* ======================================================================== */
/* World coordinate helpers                                                  */
/* ======================================================================== */

static Vector2 tile_scr(int tx, int ty) {
    return (Vector2){(float)(tx*TILE_SIZE),(float)((FC_ARENA_HEIGHT-1-ty)*TILE_SIZE+HEADER_HEIGHT)};
}

/* ======================================================================== */
/* Draw arena (2D procedural)                                                */
/* ======================================================================== */

static void draw_arena(ViewerState* v) {
    for (int x = 0; x < FC_ARENA_WIDTH; x++) {
        for (int y = 0; y < FC_ARENA_HEIGHT; y++) {
            Vector2 sp = tile_scr(x, y);
            if (v->state.walkable[x][y]) {
                DrawRectangle((int)sp.x,(int)sp.y,TILE_SIZE,TILE_SIZE,((x+y)%2==0)?COL_FLOOR_1:COL_FLOOR_2);
            } else {
                int adj=0;
                for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) {
                    int nx=x+dx,ny=y+dy;
                    if (nx>=0&&nx<FC_ARENA_WIDTH&&ny>=0&&ny<FC_ARENA_HEIGHT&&v->state.walkable[nx][ny]) adj=1;
                }
                if (adj) DrawRectangle((int)sp.x,(int)sp.y,TILE_SIZE,TILE_SIZE,((x+y+(v->state.tick/3))%3==0)?COL_LAVA_LIGHT:COL_LAVA);
                else DrawRectangle((int)sp.x,(int)sp.y,TILE_SIZE,TILE_SIZE,((x+y)%2==0)?COL_ROCK:COL_ROCK_EDGE);
            }
        }
    }
}

/* ======================================================================== */
/* Draw 3D NPC models                                                        */
/* ======================================================================== */

static void draw_entities_3d(ViewerState* v) {
    /* Set up 3D camera looking at the arena */
    float cx = (float)(FC_ARENA_WIDTH / 2);
    float cy = (float)(FC_ARENA_HEIGHT / 2);

    /* Follow player */
    if (v->entity_count > 0) {
        cx = (float)v->entities[0].x;
        cy = (float)v->entities[0].y;
    }

    v->camera3d.target = (Vector3){ cx, 0.0f, cy };
    v->camera3d.position = (Vector3){
        cx + v->cam_dist * cosf(v->cam_pitch) * sinf(v->cam_yaw),
        v->cam_dist * sinf(v->cam_pitch),
        cy + v->cam_dist * cosf(v->cam_pitch) * cosf(v->cam_yaw)
    };

    BeginMode3D(v->camera3d);

    /* Terrain mesh or procedural fallback */
    if (v->terrain.loaded) {
        DrawModel(v->terrain.model, (Vector3){0, 0, 0}, 1.0f, WHITE);
    } else {
        /* Procedural ground plane fallback */
        for (int x = 0; x < FC_ARENA_WIDTH; x++) {
            for (int y = 0; y < FC_ARENA_HEIGHT; y++) {
                Color c;
                if (v->state.walkable[x][y]) {
                    c = ((x+y)%2==0) ? COL_FLOOR_1 : COL_FLOOR_2;
                } else {
                    int adj=0;
                    for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) {
                        int nx=x+dx,ny=y+dy;
                        if (nx>=0&&nx<FC_ARENA_WIDTH&&ny>=0&&ny<FC_ARENA_HEIGHT&&v->state.walkable[nx][ny]) adj=1;
                    }
                    if (adj) c = ((x+y+(v->state.tick/3))%3==0) ? COL_LAVA_LIGHT : COL_LAVA;
                    else c = ((x+y)%2==0) ? COL_ROCK : COL_ROCK_EDGE;
                }
                DrawCube((Vector3){(float)x+0.5f, -0.05f, (float)y+0.5f}, 1.0f, 0.1f, 1.0f, c);
            }
        }
    }

    /* Entities */
    for (int i = 0; i < v->entity_count; i++) {
        FcRenderEntity* e = &v->entities[i];
        float ex = (float)e->x + (float)e->size * 0.5f;
        float ey = (float)e->y + (float)e->size * 0.5f;

        if (e->entity_type == ENTITY_PLAYER) {
            DrawCylinder((Vector3){ex, 0, ey}, 0.3f, 0.3f, 1.2f, 8, COL_PLAYER);
        } else if (!e->is_dead) {
            /* Try to render 3D model */
            uint32_t model_id = (e->npc_type > 0 && e->npc_type < NPC_TYPE_COUNT)
                ? NPC_TYPE_TO_MODEL_ID[e->npc_type] : 0;
            FcModel* m = fc_model_cache_get(v->model_cache, model_id);
            if (m) {
                float scale = OSRS_SCALE * (float)e->size;
                DrawModelEx(m->model, (Vector3){ex, 0, ey},
                            (Vector3){0,1,0}, 0.0f,
                            (Vector3){scale, scale, scale}, WHITE);
            } else {
                /* Fallback: colored cube */
                float sz = (float)e->size * 0.8f;
                DrawCube((Vector3){ex, sz*0.5f, ey}, sz, sz, sz, npc_color(e->npc_type));
            }
        }

        /* HP bar (billboard) */
        if (e->max_hp > 0 && !e->is_dead) {
            float hp_frac = (float)e->current_hp / (float)e->max_hp;
            float bar_y = (e->entity_type == ENTITY_PLAYER) ? 1.4f : (float)e->size * 0.8f + 0.3f;
            float bar_w = (float)e->size * 0.8f;
            DrawCube((Vector3){ex, bar_y, ey}, bar_w, 0.08f, 0.08f, COL_HP_RED);
            DrawCube((Vector3){ex - bar_w*(1.0f-hp_frac)*0.5f, bar_y, ey},
                     bar_w * hp_frac, 0.08f, 0.08f, COL_HP_GREEN);
        }
    }

    EndMode3D();
}

/* ======================================================================== */
/* Draw 2D entities (fallback)                                               */
/* ======================================================================== */

static void draw_entities_2d(ViewerState* v) {
    for (int i = 0; i < v->entity_count; i++) {
        FcRenderEntity* e = &v->entities[i];
        Vector2 sp = tile_scr(e->x, e->y);
        int sz = TILE_SIZE * e->size;
        if (e->size > 1) sp.y -= TILE_SIZE * (e->size - 1);

        if (e->entity_type == ENTITY_PLAYER) {
            int cx = (int)sp.x + sz/2, cy = (int)sp.y + sz/2;
            DrawCircle(cx, cy, (float)(sz/2-1), COL_PLAYER);
            DrawCircleLines(cx, cy, (float)(sz/2-1), WHITE);

            /* Prayer icon sprite overhead */
            Texture2D* tex = NULL;
            if (e->prayer == PRAYER_PROTECT_MELEE && v->pray_melee_tex.id) tex = &v->pray_melee_tex;
            else if (e->prayer == PRAYER_PROTECT_RANGE && v->pray_range_tex.id) tex = &v->pray_range_tex;
            else if (e->prayer == PRAYER_PROTECT_MAGIC && v->pray_magic_tex.id) tex = &v->pray_magic_tex;

            if (tex) {
                int iw = tex->width, ih = tex->height;
                DrawTexture(*tex, cx - iw/2, cy - sz/2 - ih - 2, WHITE);
            } else {
                const char* pn = NULL; Color pc = COL_TEXT_CYAN;
                if (e->prayer == PRAYER_PROTECT_MELEE) { pn="Melee"; pc=COL_TEXT_RED; }
                else if (e->prayer == PRAYER_PROTECT_RANGE) { pn="Range"; pc=COL_TEXT_GREEN; }
                else if (e->prayer == PRAYER_PROTECT_MAGIC) { pn="Mage"; pc=COL_TEXT_CYAN; }
                if (pn) { int tw=MeasureText(pn,10); text_s(pn,cx-tw/2,cy-sz/2-14,10,pc); }
            }
        } else {
            Color c = npc_color(e->npc_type);
            if (e->is_dead) c.a = 60;
            DrawRectangleRounded((Rectangle){sp.x+1,sp.y+1,sz-2,sz-2}, 0.3f, 4, c);

            if (!e->is_dead) {
                const char* name = npc_name(e->npc_type);
                int tw = MeasureText(name, 10);
                text_s(name, (int)sp.x+sz/2-tw/2, (int)sp.y-26, 10, COL_TEXT_YELLOW);
            }

            if (e->jad_telegraph == JAD_TELEGRAPH_MAGIC_WINDUP)
                text_s("MAGE!", (int)sp.x+sz/2-16, (int)sp.y-14, 12, COL_TEXT_CYAN);
            else if (e->jad_telegraph == JAD_TELEGRAPH_RANGED_WINDUP)
                text_s("RANGE!", (int)sp.x+sz/2-18, (int)sp.y-14, 12, COL_TEXT_GREEN);
        }

        /* HP bar */
        if (e->max_hp > 0 && !e->is_dead) {
            int bw = sz+4, bh = 5, bx = (int)sp.x-2, by = (int)sp.y-8;
            float f = (float)e->current_hp / (float)e->max_hp;
            DrawRectangle(bx,by,bw,bh,COL_HP_RED);
            DrawRectangle(bx,by,(int)(bw*f),bh,COL_HP_GREEN);
            DrawRectangleLines(bx,by,bw,bh,COL_GUI_BORDER);
        }
    }
}

/* ======================================================================== */
/* Hit splats                                                                */
/* ======================================================================== */

static void update_splats(ViewerState* v) {
    for (int i = 0; i < v->entity_count; i++) {
        FcRenderEntity* e = &v->entities[i];
        if (e->damage_taken_this_tick > 0 && v->num_splats < FC_MAX_RENDER_ENTITIES*4) {
            SplatAnim* s = &v->splats[v->num_splats++];
            s->active=1; s->damage=e->damage_taken_this_tick/10; s->ticks_remaining=12; s->y_offset=0; s->entity_idx=i;
        }
    }
    int w=0;
    for (int i=0;i<v->num_splats;i++) {
        SplatAnim* s=&v->splats[i]; if (!s->active) continue;
        s->ticks_remaining--; s->y_offset-=0.8f;
        if (s->ticks_remaining<=0) { s->active=0; continue; }
        if (w!=i) v->splats[w]=*s; w++;
    }
    v->num_splats=w;
}

static void draw_splats(ViewerState* v) {
    for (int i=0;i<v->num_splats;i++) {
        SplatAnim* s=&v->splats[i]; if (!s->active||s->entity_idx>=v->entity_count) continue;
        FcRenderEntity* e=&v->entities[s->entity_idx];
        Vector2 sp=tile_scr(e->x,e->y); int sz=TILE_SIZE*e->size;
        if (e->size>1) sp.y-=TILE_SIZE*(e->size-1);
        int sx=(int)sp.x+sz/2, sy=(int)sp.y+sz/2+(int)s->y_offset;
        DrawCircle(sx,sy,10,(s->damage>0)?COL_SPLAT_RED:COL_SPLAT_BLUE);
        DrawCircleLines(sx,sy,10,BLACK);
        char d[16]; snprintf(d,sizeof(d),"%d",s->damage);
        int tw=MeasureText(d,12); text_s(d,sx-tw/2,sy-6,12,COL_TEXT_WHITE);
    }
}

/* ======================================================================== */
/* GUI Panel                                                                 */
/* ======================================================================== */

static void draw_panel(ViewerState* v) {
    int px=FC_ARENA_WIDTH*TILE_SIZE, py=HEADER_HEIGHT, pw=PANEL_WIDTH;
    DrawRectangle(px,py,pw,WINDOW_H-py,COL_BG_DARK);
    DrawLine(px,py,px,WINDOW_H,COL_GUI_BORDER);
    FcPlayer* p = &v->state.player;
    int x=px+8; char buf[128]; int tw;

    /* --- HP / Prayer bars at top --- */
    int bar_y = py + 6;
    float hf=(p->max_hp>0)?(float)p->current_hp/(float)p->max_hp:0;
    DrawRectangle(x,bar_y,pw-16,12,COL_HP_RED); DrawRectangle(x,bar_y,(int)((pw-16)*hf),12,COL_HP_GREEN);
    snprintf(buf,128,"HP %d/%d",p->current_hp/10,p->max_hp/10);
    tw=MeasureText(buf,10); text_s(buf,x+(pw-16)/2-tw/2,bar_y+1,10,COL_TEXT_WHITE);
    bar_y += 16;

    float pf=(p->max_prayer>0)?(float)p->current_prayer/(float)p->max_prayer:0;
    DrawRectangle(x,bar_y,pw-16,12,CLITERAL(Color){40,40,80,255});
    DrawRectangle(x,bar_y,(int)((pw-16)*pf),12,COL_PRAYER_BLUE);
    snprintf(buf,128,"Pray %d/%d",p->current_prayer/10,p->max_prayer/10);
    tw=MeasureText(buf,10); text_s(buf,x+(pw-16)/2-tw/2,bar_y+1,10,COL_TEXT_WHITE);
    bar_y += 16;

    /* Wave info compact */
    snprintf(buf,128,"Wave %d/%d  NPCs:%d  Kills:%d",v->state.current_wave,FC_NUM_WAVES,
             v->state.npcs_remaining,v->state.total_npcs_killed);
    text_s(buf,x,bar_y,10,COL_TEXT_YELLOW);
    bar_y += 16;

    /* --- Tab bar --- */
    int tab_y = bar_y;
    int tab_w = (pw - 16) / 3;
    const char* tab_names[] = {"Inventory", "Prayer", "Combat"};
    Vector2 mouse = GetMousePosition();

    for (int t = 0; t < 3; t++) {
        int tx = x + t * tab_w;
        Color bg = (t == v->active_tab) ? CLITERAL(Color){100,90,70,255} : CLITERAL(Color){50,44,35,255};
        DrawRectangle(tx, tab_y, tab_w-2, 20, bg);
        DrawRectangleLines(tx, tab_y, tab_w-2, 20, COL_GUI_BORDER);

        /* Draw tab icon sprite if available */
        Texture2D* icon = NULL;
        if (t==0 && v->tab_inv_tex.id) icon = &v->tab_inv_tex;
        else if (t==1 && v->tab_pray_tex.id) icon = &v->tab_pray_tex;
        else if (t==2 && v->tab_combat_tex.id) icon = &v->tab_combat_tex;

        if (icon) {
            float scale = 16.0f / (float)(icon->height > 0 ? icon->height : 1);
            DrawTextureEx(*icon, (Vector2){(float)tx+2, (float)tab_y+2}, 0, scale, WHITE);
            int label_x = tx + 20;
            text_s(tab_names[t], label_x, tab_y+5, 9, COL_TEXT_WHITE);
        } else {
            tw = MeasureText(tab_names[t], 10);
            text_s(tab_names[t], tx + (tab_w-2)/2 - tw/2, tab_y+5, 10, COL_TEXT_WHITE);
        }

        /* Click detection */
        if (IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
            Rectangle r = {(float)tx, (float)tab_y, (float)(tab_w-2), 20};
            if (CheckCollisionPointRec(mouse, r)) v->active_tab = t;
        }
    }
    int content_y = tab_y + 24;

    /* --- Tab content --- */
    if (v->active_tab == 0) {
        /* INVENTORY TAB */
        /* Shark slots */
        int slot_x = x, slot_y = content_y;
        int slot_w = 40, slot_h = 36, cols = 4, gap = 6;

        for (int i = 0; i < FC_MAX_SHARKS; i++) {
            int col = i % cols;
            int row = i / cols;
            int sx = slot_x + col * (slot_w + gap);
            int sy = slot_y + row * (slot_h + gap);

            int has_item = (i < p->sharks_remaining);
            DrawRectangle(sx, sy, slot_w, slot_h, COL_BG_SLOT);
            DrawRectangleLines(sx, sy, slot_w, slot_h, COL_GUI_BORDER);
            if (has_item) {
                /* Shark icon (stylized) */
                DrawRectangle(sx+4, sy+8, 32, 18, CLITERAL(Color){180,140,100,255});
                DrawRectangle(sx+8, sy+10, 24, 14, CLITERAL(Color){220,170,120,255});
                text_s("S", sx+16, sy+10, 14, COL_TEXT_WHITE);
                /* Click to eat */
                if (IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
                    Rectangle r = {(float)sx,(float)sy,(float)slot_w,(float)slot_h};
                    if (CheckCollisionPointRec(mouse, r)) v->pending_eat_action = FC_EAT_SHARK;
                }
            }
        }

        /* Prayer potion slots (below sharks) */
        int pot_start_y = slot_y + (FC_MAX_SHARKS / cols + 1) * (slot_h + gap);
        int max_pots = 8;  /* 8 potions × 4 doses */
        int pots_remaining = (p->prayer_doses_remaining + 3) / 4;  /* ceil to pots */
        for (int i = 0; i < max_pots; i++) {
            int col = i % cols;
            int row = i / cols;
            int sx = slot_x + col * (slot_w + gap);
            int sy = pot_start_y + row * (slot_h + gap);

            int has_item = (i < pots_remaining);
            DrawRectangle(sx, sy, slot_w, slot_h, COL_BG_SLOT);
            DrawRectangleLines(sx, sy, slot_w, slot_h, COL_GUI_BORDER);
            if (has_item) {
                DrawRectangle(sx+12, sy+4, 16, 28, CLITERAL(Color){50,180,220,255});
                DrawRectangle(sx+10, sy+2, 20, 8, CLITERAL(Color){80,60,40,255});
                int doses_in_pot = (i < pots_remaining - 1) ? 4 :
                    ((p->prayer_doses_remaining % 4 == 0) ? 4 : (p->prayer_doses_remaining % 4));
                snprintf(buf, 128, "%d", doses_in_pot);
                text_s(buf, sx+16, sy+16, 10, COL_TEXT_WHITE);
                if (IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
                    Rectangle r = {(float)sx,(float)sy,(float)slot_w,(float)slot_h};
                    if (CheckCollisionPointRec(mouse, r)) v->pending_drink_action = FC_DRINK_PRAYER_POT;
                }
            }
        }

    } else if (v->active_tab == 1) {
        /* PRAYER TAB */
        /* 3 protection prayers in a row */
        int pray_x = x + 10, pray_y = content_y + 10;
        int icon_sz = 40;

        struct { Texture2D* on; Texture2D* off; const char* label; int prayer_val; int fc_action; } prayers[] = {
            { &v->pray_magic_tex, &v->pray_magic_off_tex, "Magic", PRAYER_PROTECT_MAGIC, FC_PRAYER_MAGIC },
            { &v->pray_range_tex, &v->pray_range_off_tex, "Range", PRAYER_PROTECT_RANGE, FC_PRAYER_RANGE },
            { &v->pray_melee_tex, &v->pray_melee_off_tex, "Melee", PRAYER_PROTECT_MELEE, FC_PRAYER_MELEE },
        };

        for (int i = 0; i < 3; i++) {
            int ix = pray_x + i * (icon_sz + 15);
            int iy = pray_y;
            int is_active = (p->prayer == prayers[i].prayer_val);

            /* Background highlight if active */
            if (is_active) {
                DrawRectangle(ix-2, iy-2, icon_sz+4, icon_sz+4, CLITERAL(Color){200,200,100,80});
            }
            DrawRectangle(ix, iy, icon_sz, icon_sz, COL_BG_SLOT);
            DrawRectangleLines(ix, iy, icon_sz, icon_sz, is_active ? COL_TEXT_YELLOW : COL_GUI_BORDER);

            /* Draw prayer icon sprite */
            Texture2D* tex = is_active ? prayers[i].on : prayers[i].off;
            if (tex && tex->id) {
                float scale = (float)(icon_sz - 4) / (float)(tex->height > 0 ? tex->height : 1);
                DrawTextureEx(*tex, (Vector2){(float)(ix+2), (float)(iy+2)}, 0, scale, WHITE);
            } else {
                text_s(prayers[i].label, ix+4, iy+14, 12, is_active ? COL_TEXT_YELLOW : COL_TEXT_WHITE);
            }

            /* Label below */
            tw = MeasureText(prayers[i].label, 10);
            text_s(prayers[i].label, ix + icon_sz/2 - tw/2, iy + icon_sz + 4, 10,
                   is_active ? COL_TEXT_YELLOW : COL_TEXT_WHITE);

            /* Click to toggle */
            if (IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
                Rectangle r = {(float)ix,(float)iy,(float)icon_sz,(float)icon_sz};
                if (CheckCollisionPointRec(mouse, r)) {
                    if (is_active)
                        v->pending_prayer_action = FC_PRAYER_OFF;
                    else
                        v->pending_prayer_action = prayers[i].fc_action;
                }
            }
        }

        /* Current prayer status */
        int status_y = pray_y + icon_sz + 24;
        const char* pn = "None";
        if (p->prayer==PRAYER_PROTECT_MELEE) pn = "Protect from Melee";
        else if (p->prayer==PRAYER_PROTECT_RANGE) pn = "Protect from Missiles";
        else if (p->prayer==PRAYER_PROTECT_MAGIC) pn = "Protect from Magic";
        snprintf(buf, 128, "Active: %s", pn);
        text_s(buf, x, status_y, 11, COL_TEXT_WHITE);

    } else {
        /* COMBAT TAB */
        int cy = content_y + 4;
        snprintf(buf,128,"Dealt: %d",p->total_damage_dealt/10);
        text_s(buf,x,cy,11,COL_TEXT_WHITE); cy+=14;
        snprintf(buf,128,"Taken: %d",p->total_damage_taken/10);
        text_s(buf,x,cy,11,COL_TEXT_WHITE); cy+=14;
        snprintf(buf,128,"Food: %d/%d",p->total_food_eaten,FC_MAX_SHARKS);
        text_s(buf,x,cy,11,COL_TEXT_WHITE); cy+=14;
        snprintf(buf,128,"Potions: %d/%d",p->total_potions_used,FC_MAX_PRAYER_DOSES/4);
        text_s(buf,x,cy,11,COL_TEXT_WHITE); cy+=14;
        snprintf(buf,128,"Pos: (%d,%d)",p->x,p->y);
        text_s(buf,x,cy,11,COL_TEXT_WHITE); cy+=20;

        /* Timers */
        snprintf(buf,128,"Atk: %d  Food: %d  Pot: %d",p->attack_timer,p->food_timer,p->potion_timer);
        text_s(buf,x,cy,10,CLITERAL(Color){130,130,140,255});
    }

    /* Status bar at bottom */
    snprintf(buf,128,"%s %dtps Tick:%d %s",v->paused?"PAUSED":"RUN",v->ticks_per_sec,v->state.tick,
             v->use_3d_models?"[3D]":"[2D]");
    text_s(buf,x,WINDOW_H-22,10,v->paused?COL_TEXT_RED:COL_TEXT_GREEN);
}

/* ======================================================================== */
/* Header + debug + controls                                                 */
/* ======================================================================== */

static void draw_header(ViewerState* v) {
    DrawRectangle(0,0,WINDOW_W,HEADER_HEIGHT,COL_HEADER_BG);
    char buf[128];
    snprintf(buf,128,"Fight Caves — Wave %d/%d",v->state.current_wave,FC_NUM_WAVES);
    text_s(buf,10,4,16,COL_TEXT_YELLOW);
    snprintf(buf,128,"Ep:%d Seed:%u Rot:%d",v->episode_count,v->seed,v->state.rotation_id);
    text_s(buf,10,22,10,CLITERAL(Color){130,130,140,255});
    FcPlayer* p=&v->state.player;
    snprintf(buf,128,"HP %d/%d  Pray %d/%d",p->current_hp/10,p->max_hp/10,p->current_prayer/10,p->max_prayer/10);
    int tw=MeasureText(buf,14); text_s(buf,WINDOW_W-tw-10,8,14,COL_TEXT_WHITE);
}

static void draw_debug(ViewerState* v) {
    if (!v->show_debug) return;
    int x=10, y=HEADER_HEIGHT+6; char buf[128];
    DrawRectangle(5,y-2,280,60,CLITERAL(Color){0,0,0,160});
    snprintf(buf,128,"Hash:0x%08x Actions:[%d %d %d %d %d]",v->last_hash,
             v->actions[0],v->actions[1],v->actions[2],v->actions[3],v->actions[4]);
    text_s(buf,x,y,10,CLITERAL(Color){180,180,190,255}); y+=14;
    snprintf(buf,128,"Terminal:%d DMG:%d/%d Models:%d",v->state.terminal,
             v->state.damage_dealt_this_tick/10,v->state.damage_taken_this_tick/10,
             v->model_cache?v->model_cache->count:0);
    text_s(buf,x,y,10,CLITERAL(Color){180,180,190,255}); y+=14;
    snprintf(buf,128,"Timers: atk=%d food=%d pot=%d",v->state.player.attack_timer,
             v->state.player.food_timer,v->state.player.potion_timer);
    text_s(buf,x,y,10,CLITERAL(Color){180,180,190,255});
}

/* ======================================================================== */
/* Reset                                                                     */
/* ======================================================================== */

static void reset_episode(ViewerState* v) {
    v->seed = (uint32_t)GetRandomValue(1,999999);
    fc_reset(&v->state, v->seed);
    fc_fill_render_entities(&v->state, v->entities, &v->entity_count);
    v->last_hash = fc_state_hash(&v->state);
    v->episode_count++;
    memset(v->actions, 0, sizeof(v->actions));
    v->num_splats = 0;
}

/* ======================================================================== */
/* Main                                                                      */
/* ======================================================================== */

int main(void) {
    SetConfigFlags(FLAG_WINDOW_RESIZABLE | FLAG_MSAA_4X_HINT);
    InitWindow(WINDOW_W, WINDOW_H, "Fight Caves RL — Viewer");
    SetTargetFPS(60);

    ViewerState v;
    memset(&v, 0, sizeof(v));
    fc_init(&v.state);
    v.paused = 1;
    v.ticks_per_sec = 5;
    v.use_3d_models = 1;

    /* Camera2D (for 2D mode) */
    v.camera2d.offset = (Vector2){(float)(FC_ARENA_WIDTH*TILE_SIZE)/2.0f,
                                   (float)(HEADER_HEIGHT+FC_ARENA_HEIGHT*TILE_SIZE/2.0f)};
    v.camera2d.target = v.camera2d.offset;
    v.camera2d.zoom = 1.0f;

    /* Camera3D */
    v.cam_yaw = 0.0f;
    v.cam_pitch = 0.8f;
    v.cam_dist = 40.0f;
    v.camera3d.up = (Vector3){0, 1, 0};
    v.camera3d.fovy = 45.0f;
    v.camera3d.projection = CAMERA_PERSPECTIVE;

    /* Load NPC models */
    v.model_cache = fc_model_cache_load("demo-env/assets/fight_caves_npcs.models");
    if (!v.model_cache)
        v.model_cache = fc_model_cache_load("../demo-env/assets/fight_caves_npcs.models");

    /* Load terrain mesh */
    v.terrain = fc_terrain_load("demo-env/assets/fight_caves.terrain");
    if (!v.terrain.loaded)
        v.terrain = fc_terrain_load("../demo-env/assets/fight_caves.terrain");

    /* Load sprites */
    {
        const char* dirs[] = {"demo-env/assets/sprites/", "../demo-env/assets/sprites/"};
        char path[256];

        #define LOAD_TEX(var, name) \
            for (int d=0; d<2 && !(var).id; d++) { snprintf(path,256,"%s%s",dirs[d],name); var=LoadTexture(path); }

        LOAD_TEX(v.pray_melee_tex, "protect_melee_on.png");
        LOAD_TEX(v.pray_range_tex, "protect_missiles_on.png");
        LOAD_TEX(v.pray_magic_tex, "protect_magic_on.png");
        LOAD_TEX(v.pray_melee_off_tex, "protect_melee_off.png");
        LOAD_TEX(v.pray_range_off_tex, "protect_missiles_off.png");
        LOAD_TEX(v.pray_magic_off_tex, "protect_magic_off.png");
        LOAD_TEX(v.tab_inv_tex, "tab_inventory.png");
        LOAD_TEX(v.tab_pray_tex, "tab_prayer.png");
        LOAD_TEX(v.tab_combat_tex, "tab_combat.png");

        #undef LOAD_TEX

        v.sprites_loaded = (v.pray_melee_tex.id && v.pray_range_tex.id && v.pray_magic_tex.id);
        fprintf(stderr, "Sprites: prayer=%s tabs=%s terrain=%s\n",
                v.sprites_loaded ? "yes" : "no",
                v.tab_inv_tex.id ? "yes" : "no",
                v.terrain.loaded ? "yes" : "no");
    }

    reset_episode(&v);

    while (!WindowShouldClose()) {
        /* Input */
        if (IsKeyPressed(KEY_Q)) break;
        if (IsKeyPressed(KEY_SPACE)) v.paused = !v.paused;
        if (IsKeyPressed(KEY_RIGHT)) v.step_once = 1;
        if (IsKeyPressed(KEY_UP) && v.ticks_per_sec < MAX_TICKS_PER_SEC) v.ticks_per_sec++;
        if (IsKeyPressed(KEY_DOWN) && v.ticks_per_sec > MIN_TICKS_PER_SEC) v.ticks_per_sec--;
        if (IsKeyPressed(KEY_R)) reset_episode(&v);
        if (IsKeyPressed(KEY_D)) v.show_debug = !v.show_debug;
        if (IsKeyPressed(KEY_T)) v.use_3d_models = !v.use_3d_models;

        /* Camera controls */
        float wheel = GetMouseWheelMove();
        if (v.use_3d_models) {
            /* 3D orbit camera */
            if (IsMouseButtonDown(MOUSE_BUTTON_RIGHT)) {
                Vector2 delta = GetMouseDelta();
                v.cam_yaw += delta.x * 0.005f;
                v.cam_pitch -= delta.y * 0.005f;
                if (v.cam_pitch < 0.1f) v.cam_pitch = 0.1f;
                if (v.cam_pitch > 1.4f) v.cam_pitch = 1.4f;
            }
            if (wheel != 0.0f) {
                v.cam_dist *= (wheel > 0) ? (1.0f/1.15f) : 1.15f;
                if (v.cam_dist < 5.0f) v.cam_dist = 5.0f;
                if (v.cam_dist > 200.0f) v.cam_dist = 200.0f;
            }
        } else {
            /* 2D camera */
            if (wheel != 0.0f) {
                v.camera2d.zoom *= (wheel > 0) ? 1.15f : (1.0f/1.15f);
                if (v.camera2d.zoom < 0.3f) v.camera2d.zoom = 0.3f;
                if (v.camera2d.zoom > 5.0f) v.camera2d.zoom = 5.0f;
            }
            if (IsMouseButtonDown(MOUSE_BUTTON_MIDDLE)) {
                Vector2 delta = GetMouseDelta();
                v.camera2d.target.x -= delta.x / v.camera2d.zoom;
                v.camera2d.target.y -= delta.y / v.camera2d.zoom;
            }
            if (v.entity_count > 0) {
                Vector2 pp = tile_scr(v.entities[0].x, v.entities[0].y);
                pp.x += TILE_SIZE/2.0f; pp.y += TILE_SIZE/2.0f;
                v.camera2d.target.x += (pp.x - v.camera2d.target.x) * 0.08f;
                v.camera2d.target.y += (pp.y - v.camera2d.target.y) * 0.08f;
            }
        }

        /* Tick */
        int should_tick = 0;
        if (!v.paused) {
            v.tick_accumulator += GetFrameTime() * (float)v.ticks_per_sec;
            if (v.tick_accumulator >= 1.0f) { v.tick_accumulator -= 1.0f; should_tick = 1; }
        }
        if (v.step_once) { should_tick = 1; v.step_once = 0; }

        if (should_tick && v.state.terminal == TERMINAL_NONE) {
            /* Random actions for demo mode */
            for (int h=0; h<FC_NUM_ACTION_HEADS; h++)
                v.actions[h] = GetRandomValue(0, FC_ACTION_DIMS[h]-1);
            if (GetRandomValue(0,2)==0) v.actions[0] = 0;

            /* Apply UI-driven actions (override random) */
            if (v.pending_prayer_action) {
                v.actions[2] = v.pending_prayer_action;
                v.pending_prayer_action = 0;
            }
            if (v.pending_eat_action) {
                v.actions[3] = v.pending_eat_action;
                v.pending_eat_action = 0;
            }
            if (v.pending_drink_action) {
                v.actions[4] = v.pending_drink_action;
                v.pending_drink_action = 0;
            }

            fc_step(&v.state, v.actions);
            fc_fill_render_entities(&v.state, v.entities, &v.entity_count);
            v.last_hash = fc_state_hash(&v.state);
            update_splats(&v);
            if (v.state.terminal != TERMINAL_NONE) v.paused = 1;
        }

        /* Render */
        BeginDrawing();
        ClearBackground(COL_BORDER);

        if (v.use_3d_models && v.model_cache) {
            draw_entities_3d(&v);
        } else {
            BeginMode2D(v.camera2d);
            draw_arena(&v);
            draw_entities_2d(&v);
            draw_splats(&v);
            EndMode2D();
        }

        draw_header(&v);
        draw_panel(&v);
        draw_debug(&v);
        DrawText("Space:pause Right:step Up/Down:speed R:reset D:debug T:2D/3D Q:quit Scroll:zoom RightDrag:orbit",
                 10, WINDOW_H-12, 9, CLITERAL(Color){80,80,90,255});

        EndDrawing();
    }

    /* Cleanup */
    if (v.model_cache) fc_model_cache_free(v.model_cache);
    fc_terrain_free(&v.terrain);
    if (v.pray_melee_tex.id) UnloadTexture(v.pray_melee_tex);
    if (v.pray_range_tex.id) UnloadTexture(v.pray_range_tex);
    if (v.pray_magic_tex.id) UnloadTexture(v.pray_magic_tex);
    if (v.pray_melee_off_tex.id) UnloadTexture(v.pray_melee_off_tex);
    if (v.pray_range_off_tex.id) UnloadTexture(v.pray_range_off_tex);
    if (v.pray_magic_off_tex.id) UnloadTexture(v.pray_magic_off_tex);
    if (v.tab_inv_tex.id) UnloadTexture(v.tab_inv_tex);
    if (v.tab_pray_tex.id) UnloadTexture(v.tab_pray_tex);
    if (v.tab_combat_tex.id) UnloadTexture(v.tab_combat_tex);
    CloseWindow();
    return 0;
}
