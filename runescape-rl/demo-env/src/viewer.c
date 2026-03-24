/*
 * viewer.c — Minimal Raylib viewer shell for Fight Caves.
 *
 * Renders fc_core state via fc_fill_render_entities. No gameplay logic.
 * Placeholder art: colored shapes for player/NPCs, gray grid for tiles.
 *
 * Controls:
 *   Space     — pause/resume
 *   Right     — single-step (while paused)
 *   Up/Down   — increase/decrease tick speed
 *   R         — reset episode
 *   H         — toggle HUD overlay
 *   D         — toggle debug overlay (state hash, action heads)
 *   Q / Esc   — quit
 *   Scroll    — zoom in/out
 */

#include "raylib.h"
#include "fc_types.h"
#include "fc_contracts.h"
#include "fc_api.h"
#include "fc_npc.h"
#include <stdio.h>
#include <string.h>

/* ======================================================================== */
/* Configuration                                                             */
/* ======================================================================== */

#define WINDOW_W     1280
#define WINDOW_H      800
#define TILE_SIZE_DEFAULT 12
#define MAX_TICKS_PER_SEC 30
#define MIN_TICKS_PER_SEC  1

/* ======================================================================== */
/* Viewer state                                                              */
/* ======================================================================== */

typedef struct {
    FcState state;
    FcRenderEntity entities[FC_MAX_RENDER_ENTITIES];
    int entity_count;

    /* Simulation control */
    int paused;
    int step_once;
    int ticks_per_sec;
    float tick_accumulator;

    /* Camera */
    float zoom;
    float cam_x, cam_y;  /* world offset in pixels */

    /* Display toggles */
    int show_hud;
    int show_debug;

    /* Current actions (random or idle while viewing) */
    int actions[FC_NUM_ACTION_HEADS];

    /* Episode tracking */
    uint32_t seed;
    int episode_count;
    uint32_t last_hash;
} ViewerState;

/* ======================================================================== */
/* Colors for NPC types                                                      */
/* ======================================================================== */

static Color npc_color(int npc_type) {
    switch (npc_type) {
        case NPC_TZ_KIH:    return (Color){220, 60, 60, 255};    /* red */
        case NPC_TZ_KEK:    return (Color){180, 80, 80, 255};    /* dark red */
        case NPC_TZ_KEK_SM: return (Color){200, 100, 100, 255};  /* lighter red */
        case NPC_TOK_XIL:   return (Color){60, 180, 60, 255};    /* green */
        case NPC_YT_MEJKOT: return (Color){60, 160, 220, 255};   /* cyan */
        case NPC_KET_ZEK:   return (Color){160, 60, 220, 255};   /* purple */
        case NPC_TZTOK_JAD: return (Color){255, 140, 0, 255};    /* orange */
        case NPC_YT_HURKOT: return (Color){255, 200, 60, 255};   /* yellow */
        default:            return GRAY;
    }
}

/* ======================================================================== */
/* World-to-screen projection                                                */
/* ======================================================================== */

static int world_to_screen_x(ViewerState* v, float wx) {
    return (int)((wx * TILE_SIZE_DEFAULT * v->zoom) + v->cam_x);
}

static int world_to_screen_y(ViewerState* v, float wy) {
    /* Y is inverted: world Y increases upward, screen Y increases downward */
    return WINDOW_H - (int)((wy * TILE_SIZE_DEFAULT * v->zoom) + v->cam_y);
}

static int tile_screen_size(ViewerState* v) {
    return (int)(TILE_SIZE_DEFAULT * v->zoom);
}

/* ======================================================================== */
/* Draw arena grid                                                           */
/* ======================================================================== */

static void draw_arena(ViewerState* v) {
    int ts = tile_screen_size(v);

    for (int x = 0; x < FC_ARENA_WIDTH; x++) {
        for (int y = 0; y < FC_ARENA_HEIGHT; y++) {
            int sx = world_to_screen_x(v, (float)x);
            int sy = world_to_screen_y(v, (float)(y + 1));  /* +1 because screen Y inverted */

            if (sx > WINDOW_W || sy > WINDOW_H || sx + ts < 0 || sy + ts < 0) continue;

            Color c = v->state.walkable[x][y]
                ? (Color){40, 40, 45, 255}      /* walkable: dark gray */
                : (Color){25, 25, 28, 255};      /* obstacle: darker */
            DrawRectangle(sx, sy, ts, ts, c);
            DrawRectangleLines(sx, sy, ts, ts, (Color){55, 55, 60, 255});
        }
    }
}

/* ======================================================================== */
/* Draw entities                                                             */
/* ======================================================================== */

static void draw_entities(ViewerState* v) {
    int ts = tile_screen_size(v);

    for (int i = 0; i < v->entity_count; i++) {
        FcRenderEntity* e = &v->entities[i];
        int sx = world_to_screen_x(v, (float)e->x);
        int sy = world_to_screen_y(v, (float)(e->y + e->size));
        int sz = ts * e->size;

        if (e->entity_type == ENTITY_PLAYER) {
            /* Player: blue filled circle */
            int cx = sx + sz / 2;
            int cy = sy + sz / 2;
            DrawCircle(cx, cy, (float)(sz / 2 - 1), (Color){40, 120, 255, 255});
            DrawCircleLines(cx, cy, (float)(sz / 2 - 1), WHITE);

            /* Prayer indicator */
            if (e->prayer == PRAYER_PROTECT_MELEE) {
                DrawText("M", cx - 4, cy - 24, 12, YELLOW);
            } else if (e->prayer == PRAYER_PROTECT_RANGE) {
                DrawText("R", cx - 4, cy - 24, 12, GREEN);
            } else if (e->prayer == PRAYER_PROTECT_MAGIC) {
                DrawText("P", cx - 4, cy - 24, 12, SKYBLUE);
            }
        } else {
            /* NPC: colored rectangle */
            Color c = npc_color(e->npc_type);
            if (e->is_dead) {
                c.a = 80;  /* ghost for dead */
            }
            DrawRectangle(sx + 1, sy + 1, sz - 2, sz - 2, c);
            DrawRectangleLines(sx, sy, sz, sz, WHITE);

            /* Jad telegraph indicator */
            if (e->jad_telegraph == JAD_TELEGRAPH_MAGIC_WINDUP) {
                DrawText("MAG", sx, sy - 14, 10, SKYBLUE);
            } else if (e->jad_telegraph == JAD_TELEGRAPH_RANGED_WINDUP) {
                DrawText("RNG", sx, sy - 14, 10, GREEN);
            }
        }

        /* HP bar (above entity) */
        if (e->max_hp > 0 && !e->is_dead) {
            int bar_w = sz;
            int bar_h = 4;
            int bar_x = sx;
            int bar_y = sy - 8;
            float hp_frac = (float)e->current_hp / (float)e->max_hp;
            DrawRectangle(bar_x, bar_y, bar_w, bar_h, RED);
            DrawRectangle(bar_x, bar_y, (int)(bar_w * hp_frac), bar_h, GREEN);
        }

        /* Hitsplat (damage this tick) */
        if (e->damage_taken_this_tick > 0) {
            char dmg[16];
            snprintf(dmg, sizeof(dmg), "%d", e->damage_taken_this_tick / 10);
            DrawText(dmg, sx + sz/2 - 6, sy + sz/2 - 6, 14, RED);
        }
    }
}

/* ======================================================================== */
/* Draw HUD                                                                  */
/* ======================================================================== */

static void draw_hud(ViewerState* v) {
    if (!v->show_hud) return;

    FcPlayer* p = &v->state.player;
    int y = 10;
    int x = 10;
    int gap = 18;

    DrawRectangle(5, 5, 250, 220, (Color){0, 0, 0, 180});

    char buf[128];

    snprintf(buf, sizeof(buf), "HP: %d/%d", p->current_hp / 10, p->max_hp / 10);
    DrawText(buf, x, y, 16, (p->current_hp < p->max_hp / 3) ? RED : GREEN);
    y += gap;

    snprintf(buf, sizeof(buf), "Prayer: %d/%d", p->current_prayer / 10, p->max_prayer / 10);
    DrawText(buf, x, y, 16, (p->current_prayer < p->max_prayer / 4) ? RED : SKYBLUE);
    y += gap;

    const char* pray_name = "OFF";
    if (p->prayer == PRAYER_PROTECT_MELEE) pray_name = "Melee";
    else if (p->prayer == PRAYER_PROTECT_RANGE) pray_name = "Ranged";
    else if (p->prayer == PRAYER_PROTECT_MAGIC) pray_name = "Magic";
    snprintf(buf, sizeof(buf), "Active: %s", pray_name);
    DrawText(buf, x, y, 16, YELLOW);
    y += gap;

    snprintf(buf, sizeof(buf), "Sharks: %d  Doses: %d", p->sharks_remaining, p->prayer_doses_remaining);
    DrawText(buf, x, y, 16, WHITE);
    y += gap;

    snprintf(buf, sizeof(buf), "Ammo: %d", p->ammo_count);
    DrawText(buf, x, y, 16, WHITE);
    y += gap;

    snprintf(buf, sizeof(buf), "Wave: %d  Kills: %d  NPCs: %d",
             v->state.current_wave, v->state.total_npcs_killed, v->state.npcs_remaining);
    DrawText(buf, x, y, 16, GOLD);
    y += gap;

    snprintf(buf, sizeof(buf), "Tick: %d  Episode: %d", v->state.tick, v->episode_count);
    DrawText(buf, x, y, 16, LIGHTGRAY);
    y += gap;

    snprintf(buf, sizeof(buf), "Pos: (%d, %d)", p->x, p->y);
    DrawText(buf, x, y, 16, LIGHTGRAY);
    y += gap;

    /* Timers */
    snprintf(buf, sizeof(buf), "Atk:%d  Food:%d  Pot:%d",
             p->attack_timer, p->food_timer, p->potion_timer);
    DrawText(buf, x, y, 14, DARKGRAY);
    y += gap;

    /* Damage stats */
    snprintf(buf, sizeof(buf), "Dealt: %d  Taken: %d",
             p->total_damage_dealt / 10, p->total_damage_taken / 10);
    DrawText(buf, x, y, 14, DARKGRAY);
    y += gap;

    /* Speed indicator */
    snprintf(buf, sizeof(buf), "%s  %d tps",
             v->paused ? "PAUSED" : "RUNNING", v->ticks_per_sec);
    DrawText(buf, x, y, 14, v->paused ? RED : GREEN);
}

/* ======================================================================== */
/* Draw debug overlay                                                        */
/* ======================================================================== */

static void draw_debug(ViewerState* v) {
    if (!v->show_debug) return;

    int x = WINDOW_W - 280;
    int y = 10;
    int gap = 16;

    DrawRectangle(x - 5, 5, 275, 120, (Color){0, 0, 0, 180});

    char buf[128];

    snprintf(buf, sizeof(buf), "State hash: 0x%08x", v->last_hash);
    DrawText(buf, x, y, 14, LIGHTGRAY);
    y += gap;

    snprintf(buf, sizeof(buf), "Seed: %u  Rotation: %d", v->seed, v->state.rotation_id);
    DrawText(buf, x, y, 14, LIGHTGRAY);
    y += gap;

    snprintf(buf, sizeof(buf), "Actions: [%d %d %d %d %d]",
             v->actions[0], v->actions[1], v->actions[2], v->actions[3], v->actions[4]);
    DrawText(buf, x, y, 14, LIGHTGRAY);
    y += gap;

    snprintf(buf, sizeof(buf), "Terminal: %d  DMG/tick: %d/%d",
             v->state.terminal,
             v->state.damage_dealt_this_tick / 10,
             v->state.damage_taken_this_tick / 10);
    DrawText(buf, x, y, 14, LIGHTGRAY);
    y += gap;

    snprintf(buf, sizeof(buf), "Zoom: %.1fx", v->zoom);
    DrawText(buf, x, y, 14, LIGHTGRAY);
}

/* ======================================================================== */
/* Draw controls help                                                        */
/* ======================================================================== */

static void draw_controls(void) {
    int y = WINDOW_H - 24;
    DrawText("Space:pause  Right:step  Up/Down:speed  R:reset  H:hud  D:debug  Q:quit  Scroll:zoom",
             10, y, 12, (Color){100, 100, 100, 255});
}

/* ======================================================================== */
/* Reset episode                                                             */
/* ======================================================================== */

static void reset_episode(ViewerState* v) {
    v->seed = (uint32_t)GetRandomValue(1, 999999);
    fc_reset(&v->state, v->seed);

    /* Spawn Tz-Kih for combat slice */
    fc_npc_spawn(&v->state.npcs[0], NPC_TZ_KIH,
                 v->state.player.x + 3, v->state.player.y,
                 v->state.next_spawn_index++);
    v->state.npcs_remaining = 1;

    fc_fill_render_entities(&v->state, v->entities, &v->entity_count);
    v->last_hash = fc_state_hash(&v->state);
    v->episode_count++;
    memset(v->actions, 0, sizeof(v->actions));
}

/* ======================================================================== */
/* Main                                                                      */
/* ======================================================================== */

int main(void) {
    SetConfigFlags(FLAG_WINDOW_RESIZABLE);
    InitWindow(WINDOW_W, WINDOW_H, "Fight Caves RL — Viewer");
    SetTargetFPS(60);

    ViewerState v;
    memset(&v, 0, sizeof(v));
    fc_init(&v.state);
    v.paused = 1;
    v.ticks_per_sec = 5;
    v.zoom = 1.5f;
    v.show_hud = 1;
    v.show_debug = 1;

    /* Center camera on player */
    v.cam_x = WINDOW_W / 2.0f - (FC_ARENA_WIDTH / 2) * TILE_SIZE_DEFAULT * v.zoom;
    v.cam_y = WINDOW_H / 2.0f - (FC_ARENA_HEIGHT / 2) * TILE_SIZE_DEFAULT * v.zoom;

    reset_episode(&v);

    while (!WindowShouldClose()) {
        /* ---- Input ---- */
        if (IsKeyPressed(KEY_Q)) break;
        if (IsKeyPressed(KEY_SPACE)) v.paused = !v.paused;
        if (IsKeyPressed(KEY_RIGHT)) v.step_once = 1;
        if (IsKeyPressed(KEY_UP)) {
            v.ticks_per_sec++;
            if (v.ticks_per_sec > MAX_TICKS_PER_SEC) v.ticks_per_sec = MAX_TICKS_PER_SEC;
        }
        if (IsKeyPressed(KEY_DOWN)) {
            v.ticks_per_sec--;
            if (v.ticks_per_sec < MIN_TICKS_PER_SEC) v.ticks_per_sec = MIN_TICKS_PER_SEC;
        }
        if (IsKeyPressed(KEY_R)) reset_episode(&v);
        if (IsKeyPressed(KEY_H)) v.show_hud = !v.show_hud;
        if (IsKeyPressed(KEY_D)) v.show_debug = !v.show_debug;

        /* Zoom */
        float wheel = GetMouseWheelMove();
        if (wheel != 0.0f) {
            v.zoom += wheel * 0.2f;
            if (v.zoom < 0.3f) v.zoom = 0.3f;
            if (v.zoom > 5.0f) v.zoom = 5.0f;
        }

        /* Camera pan (middle mouse or arrow keys while holding shift) */
        if (IsMouseButtonDown(MOUSE_BUTTON_MIDDLE)) {
            Vector2 delta = GetMouseDelta();
            v.cam_x += delta.x;
            v.cam_y -= delta.y;
        }

        /* ---- Simulation tick ---- */
        int should_tick = 0;
        if (!v.paused) {
            v.tick_accumulator += GetFrameTime() * (float)v.ticks_per_sec;
            if (v.tick_accumulator >= 1.0f) {
                v.tick_accumulator -= 1.0f;
                should_tick = 1;
            }
        }
        if (v.step_once) {
            should_tick = 1;
            v.step_once = 0;
        }

        if (should_tick && v.state.terminal == TERMINAL_NONE) {
            /* Random actions for demo (PR 10 adds human input, PR 11 adds policy) */
            for (int h = 0; h < FC_NUM_ACTION_HEADS; h++) {
                v.actions[h] = GetRandomValue(0, FC_ACTION_DIMS[h] - 1);
            }
            /* Force idle sometimes for readability */
            if (GetRandomValue(0, 2) == 0) v.actions[0] = 0;

            fc_step(&v.state, v.actions);
            fc_fill_render_entities(&v.state, v.entities, &v.entity_count);
            v.last_hash = fc_state_hash(&v.state);

            /* Auto-reset on terminal */
            if (v.state.terminal != TERMINAL_NONE) {
                /* Pause to show death/completion state */
                v.paused = 1;
            }
        }

        /* ---- Render ---- */
        BeginDrawing();
        ClearBackground((Color){20, 20, 25, 255});

        draw_arena(&v);
        draw_entities(&v);
        draw_hud(&v);
        draw_debug(&v);
        draw_controls();

        EndDrawing();
    }

    CloseWindow();
    return 0;
}
