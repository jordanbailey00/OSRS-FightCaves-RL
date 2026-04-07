/*
 * fc_debug_overlay.h — Phase 9c debug tooling & overlays.
 *
 * Separate from viewer.c to isolate debug visualization from core viewer code.
 * All functions are read-only — they never modify FcState or ViewerState.
 *
 * Two entry points called from viewer.c:
 *   debug_overlay_3d()  — called inside BeginMode3D/EndMode3D (tiles, rays, ranges)
 *   debug_overlay_2d()  — called after EndMode3D (text panels, obs display)
 *
 * Toggle with 'O' key (debug overlay master toggle).
 * Sub-toggles cycle with Shift+O or number keys in debug mode.
 */

#ifndef FC_DEBUG_OVERLAY_H
#define FC_DEBUG_OVERLAY_H

#include "raylib.h"
#include "rlgl.h"
#include "fc_types.h"
#include "fc_contracts.h"
#include "fc_api.h"
#include "fc_pathfinding.h"
#include "fc_combat.h"
#include "fc_npc.h"
#include <stdio.h>
#include <string.h>
#include <math.h>
#include <stdlib.h>
#include <stdarg.h>

/* ======================================================================== */
/* Event log ring buffer                                                    */
/* ======================================================================== */

#define DBG_LOG_MAX_ENTRIES  128
#define DBG_LOG_MAX_MSG      80

typedef struct {
    char entries[DBG_LOG_MAX_ENTRIES][DBG_LOG_MAX_MSG];
    int tick[DBG_LOG_MAX_ENTRIES];      /* game tick when event occurred */
    Color color[DBG_LOG_MAX_ENTRIES];   /* display color per entry */
    int head;                           /* next write position */
    int count;                          /* total entries (capped at MAX) */
    int scroll_offset;                  /* for scrollable display */
} DbgEventLog;

static DbgEventLog g_dbg_log = {0};

static void dbg_log_clear(void) {
    g_dbg_log.head = 0;
    g_dbg_log.count = 0;
    g_dbg_log.scroll_offset = 0;
}

static void dbg_log_event(int game_tick, Color c, const char* fmt, ...) {
    int idx = g_dbg_log.head;
    g_dbg_log.tick[idx] = game_tick;
    g_dbg_log.color[idx] = c;

    va_list args;
    va_start(args, fmt);
    vsnprintf(g_dbg_log.entries[idx], DBG_LOG_MAX_MSG, fmt, args);
    va_end(args);

    g_dbg_log.head = (g_dbg_log.head + 1) % DBG_LOG_MAX_ENTRIES;
    if (g_dbg_log.count < DBG_LOG_MAX_ENTRIES) g_dbg_log.count++;
}

/* Call once per tick to auto-generate events from state changes.
 * Reads current state and emits relevant log entries. */
static void dbg_log_tick(const FcState* state) {
    int t = state->tick;
    const FcPlayer* p = &state->player;
    static const char* npc_names[] = {"?","Tz-Kih","Tz-Kek","Kek-Sm","Tok-Xil",
        "MejKot","Ket-Zek","Jad","HurKot"};
    static const char* style_str[] = {"none","melee","ranged","magic"};

    /* Player took damage */
    if (p->damage_taken_this_tick > 0) {
        dbg_log_event(t, CLITERAL(Color){255,120,120,255},
                      "Player took %d damage", p->damage_taken_this_tick / 10);
    }

    /* Player ate food */
    if (p->food_eaten_this_tick) {
        dbg_log_event(t, CLITERAL(Color){180,255,180,255},
                      "Ate shark (+20 HP) [%d left]", p->sharks_remaining);
    }

    /* Player drank potion */
    if (p->potion_used_this_tick) {
        dbg_log_event(t, CLITERAL(Color){120,180,255,255},
                      "Drank ppot (+17 pray) [%d doses left]", p->prayer_doses_remaining);
    }

    /* Player switched prayer */
    if (p->prayer_changed_this_tick) {
        static const char* pray_names[] = {"OFF","Prot Melee","Prot Range","Prot Magic"};
        dbg_log_event(t, CLITERAL(Color){255,255,100,255},
                      "Prayer -> %s", pray_names[p->prayer]);
    }

    /* Player hit landed */
    if (p->hit_landed_this_tick && p->attack_target_idx >= 0) {
        const FcNpc* tgt = &state->npcs[p->attack_target_idx];
        const char* nm = (tgt->npc_type > 0 && tgt->npc_type < 9) ? npc_names[tgt->npc_type] : "?";
        dbg_log_event(t, CLITERAL(Color){200,200,200,255},
                      "Attacked %s [%d]", nm, p->attack_target_idx);
    }

    /* NPC events */
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        const FcNpc* n = &state->npcs[i];

        /* NPC damage taken */
        if (n->damage_taken_this_tick > 0) {
            const char* nm = (n->npc_type > 0 && n->npc_type < 9) ? npc_names[n->npc_type] : "?";
            dbg_log_event(t, CLITERAL(Color){255,200,100,255},
                          "%s[%d] took %d dmg (%d HP left)",
                          nm, i, n->damage_taken_this_tick / 10, n->current_hp / 10);
        }

        /* NPC died */
        if (n->died_this_tick) {
            const char* nm = (n->npc_type > 0 && n->npc_type < 9) ? npc_names[n->npc_type] : "?";
            dbg_log_event(t, CLITERAL(Color){255,80,80,255},
                          "%s[%d] KILLED", nm, i);
        }

        /* Jad telegraph — only log on state change */
        if (n->npc_type == NPC_TZTOK_JAD && n->active && !n->is_dead) {
            static int prev_jad_telegraph = JAD_TELEGRAPH_IDLE;
            if (n->jad_telegraph != prev_jad_telegraph) {
                if (n->jad_telegraph == JAD_TELEGRAPH_MAGIC_WINDUP) {
                    int correct = (p->prayer == PRAYER_PROTECT_MAGIC);
                    dbg_log_event(t, correct ? CLITERAL(Color){100,255,100,255} : CLITERAL(Color){255,50,50,255},
                                  "Jad: MAGIC telegraph — prayer %s",
                                  correct ? "CORRECT" : "WRONG!");
                } else if (n->jad_telegraph == JAD_TELEGRAPH_RANGED_WINDUP) {
                    int correct = (p->prayer == PRAYER_PROTECT_RANGE);
                    dbg_log_event(t, correct ? CLITERAL(Color){100,255,100,255} : CLITERAL(Color){255,50,50,255},
                                  "Jad: RANGED telegraph — prayer %s",
                                  correct ? "CORRECT" : "WRONG!");
                }
                prev_jad_telegraph = n->jad_telegraph;
            }
        }
    }

    /* Movement — log new route destination (walk-to-tile or click-to-move) */
    {
        static int prev_route_dest_x = -1, prev_route_dest_y = -1;
        if (p->route_len > 0 && p->route_idx < p->route_len) {
            int dx = p->route_x[p->route_len - 1];
            int dy = p->route_y[p->route_len - 1];
            if (dx != prev_route_dest_x || dy != prev_route_dest_y) {
                dbg_log_event(t, CLITERAL(Color){180,220,255,255},
                              "Move to tile (%d,%d) [%d steps]", dx, dy, p->route_len - p->route_idx);
                prev_route_dest_x = dx;
                prev_route_dest_y = dy;
            }
        } else {
            prev_route_dest_x = -1;
            prev_route_dest_y = -1;
        }
    }

    /* Wave events */
    if (state->wave_just_cleared) {
        dbg_log_event(t, CLITERAL(Color){100,255,255,255},
                      "Wave %d CLEARED — advancing", state->current_wave - 1);
    }

    /* Jad killed */
    if (state->jad_killed) {
        dbg_log_event(t, CLITERAL(Color){255,215,0,255}, "*** JAD DEFEATED ***");
    }

    /* Player death */
    if (state->terminal == TERMINAL_PLAYER_DEATH && p->current_hp <= 0) {
        dbg_log_event(t, CLITERAL(Color){255,0,0,255}, "*** PLAYER DIED ***");
    }

    /* Cave complete */
    if (state->terminal == TERMINAL_CAVE_COMPLETE) {
        dbg_log_event(t, CLITERAL(Color){0,255,0,255}, "*** CAVE COMPLETE — FIRE CAPE ***");
    }
}

/* ======================================================================== */
/* Debug overlay state — add these fields to ViewerState                    */
/* ======================================================================== */

/* Overlay mode flags (bitfield) */
#define DBG_COLLISION    (1 << 0)   /* walkable/blocked tile coloring */
#define DBG_LOS          (1 << 1)   /* line-of-sight rays to NPCs */
#define DBG_PATH         (1 << 2)   /* current player route highlighted */
#define DBG_RANGE        (1 << 3)   /* attack range circle around player */
#define DBG_ENTITY_INFO  (1 << 4)   /* detailed entity state on hover/click */
#define DBG_OBS          (1 << 5)   /* RL observation overlay */
#define DBG_MASK         (1 << 6)   /* action mask display */
#define DBG_REWARD       (1 << 7)   /* reward feature display */
#define DBG_ALL          (0xFF)

/* Colors */
#define DBG_COL_WALK     CLITERAL(Color){  30, 180,  30, 40 }
#define DBG_COL_BLOCK    CLITERAL(Color){ 200,  30,  30, 60 }
#define DBG_COL_LOS_OK   CLITERAL(Color){  30, 255,  30, 200 }
#define DBG_COL_LOS_FAIL CLITERAL(Color){ 255,  30,  30, 200 }
#define DBG_COL_PATH     CLITERAL(Color){ 255, 255,   0, 160 }
#define DBG_COL_RANGE    CLITERAL(Color){ 100, 200, 255, 120 }
#define DBG_COL_PANEL    CLITERAL(Color){   0,   0,   0, 200 }
#define DBG_COL_LABEL    CLITERAL(Color){ 200, 200, 200, 255 }
#define DBG_COL_VALUE    CLITERAL(Color){ 255, 255, 100, 255 }
#define DBG_COL_GOOD     CLITERAL(Color){ 100, 255, 100, 255 }
#define DBG_COL_BAD      CLITERAL(Color){ 255, 100, 100, 255 }
#define DBG_COL_DIM      CLITERAL(Color){ 120, 120, 120, 255 }

/* ======================================================================== */
/* A. Collision / LOS / Path / Range overlays (3D)                          */
/* ======================================================================== */

/* Draw walkable/blocked tile overlay */
static void dbg_draw_collision(const FcState* state) {
    for (int tx = 0; tx < FC_ARENA_WIDTH; tx++) {
        for (int ty = 0; ty < FC_ARENA_HEIGHT; ty++) {
            Color c = state->walkable[tx][ty] ? DBG_COL_WALK : DBG_COL_BLOCK;
            DrawCube((Vector3){tx + 0.5f, 0.02f, -(ty + 0.5f)}, 0.9f, 0.02f, 0.9f, c);
        }
    }
}

/* Master 3D overlay — called inside BeginMode3D/EndMode3D.
 * Only collision tiles use 3D (they work with depth test disabled). */
static void debug_overlay_3d(const FcState* state, int dbg_flags) {
    rlDisableDepthTest();
    if (dbg_flags & DBG_COLLISION) dbg_draw_collision(state);
    rlEnableDepthTest();
}

/* ======================================================================== */
/* 2D screen-space overlays (LOS, path, range) — drawn after EndMode3D     */
/* Uses GetWorldToScreen() projection so nothing can occlude them.          */
/* ======================================================================== */

static Vector2 dbg_tile_to_screen(int tx, int ty, Camera3D cam) {
    Vector3 w = { tx + 0.5f, 0.5f, -(ty + 0.5f) };
    return GetWorldToScreen(w, cam);
}

/* LOS rays — 2D projected lines from player to NPCs */
static void dbg_draw_los_2d(const FcState* state, Camera3D cam) {
    const FcPlayer* p = &state->player;
    Vector2 ps = dbg_tile_to_screen(p->x, p->y, cam);

    for (int i = 0; i < FC_MAX_NPCS; i++) {
        const FcNpc* n = &state->npcs[i];
        if (!n->active || n->is_dead) continue;

        int ncx = n->x + n->size / 2;
        int ncy = n->y + n->size / 2;
        Vector2 ns = dbg_tile_to_screen(ncx, ncy, cam);

        int has_los = fc_has_line_of_sight(p->x, p->y, n->x, n->y, state->walkable);
        Color c = has_los ? DBG_COL_LOS_OK : DBG_COL_LOS_FAIL;
        DrawLineEx(ps, ns, 3.0f, c);
        DrawCircleV(ns, 6.0f, c);
    }
}

/* Path visualization — 2D projected dots and lines along route */
static void dbg_draw_path_2d(const FcState* state, Camera3D cam) {
    const FcPlayer* p = &state->player;
    if (p->route_idx >= p->route_len) return;

    Vector2 prev = {0};
    for (int i = p->route_idx; i < p->route_len; i++) {
        Vector2 s = dbg_tile_to_screen(p->route_x[i], p->route_y[i], cam);
        DrawCircleV(s, 4.0f, DBG_COL_PATH);
        if (i > p->route_idx) {
            DrawLineEx(prev, s, 2.0f, DBG_COL_PATH);
        }
        prev = s;
    }

    /* Destination marker — larger circle */
    if (p->route_len > 0) {
        Vector2 dest = dbg_tile_to_screen(
            p->route_x[p->route_len - 1], p->route_y[p->route_len - 1], cam);
        DrawCircleV(dest, 8.0f, DBG_COL_PATH);
        DrawCircleLinesV(dest, 12.0f, DBG_COL_PATH);
    }
}

/* Attack range ring — 2D projected Chebyshev boundary */
static void dbg_draw_range_2d(const FcState* state, Camera3D cam) {
    const FcPlayer* p = &state->player;
    int range = 7;

    for (int dx = -range; dx <= range; dx++) {
        for (int dy = -range; dy <= range; dy++) {
            int dist = (abs(dx) > abs(dy)) ? abs(dx) : abs(dy);
            if (dist == range) {
                int tx = p->x + dx;
                int ty = p->y + dy;
                if (tx >= 0 && tx < FC_ARENA_WIDTH && ty >= 0 && ty < FC_ARENA_HEIGHT) {
                    Vector2 s = dbg_tile_to_screen(tx, ty, cam);
                    DrawCircleV(s, 3.0f, DBG_COL_RANGE);
                }
            }
        }
    }
}

/* Master 2D overlays for LOS/path/range — called after EndMode3D */
static void debug_overlay_screen(const FcState* state, Camera3D cam, int dbg_flags) {
    if (dbg_flags & DBG_LOS)   dbg_draw_los_2d(state, cam);
    if (dbg_flags & DBG_PATH)  dbg_draw_path_2d(state, cam);
    if (dbg_flags & DBG_RANGE) dbg_draw_range_2d(state, cam);
}

/* ======================================================================== */
/* B. Entity state inspection (2D)                                          */
/* ======================================================================== */

static void dbg_draw_entity_info(const FcState* state, Camera3D camera) {
    char buf[256];
    int panel_x = 10;
    int panel_y = 160;
    int pw = 280, lh = 13;

    const FcPlayer* p = &state->player;

    /* Player info panel */
    int ph = lh * 12 + 10;
    DrawRectangle(panel_x, panel_y, pw, ph, DBG_COL_PANEL);
    int y = panel_y + 4;

    DrawText("PLAYER STATE", panel_x + 4, y, 11, DBG_COL_VALUE); y += lh + 2;
    snprintf(buf, sizeof(buf), "Pos: (%d, %d)  Facing: %.0f", p->x, p->y, p->facing_angle);
    DrawText(buf, panel_x + 4, y, 10, DBG_COL_LABEL); y += lh;
    snprintf(buf, sizeof(buf), "HP: %d/%d  Prayer: %d/%d",
             p->current_hp/10, p->max_hp/10, p->current_prayer/10, p->max_prayer/10);
    DrawText(buf, panel_x + 4, y, 10, DBG_COL_LABEL); y += lh;
    snprintf(buf, sizeof(buf), "Timers: atk=%d food=%d pot=%d combo=%d",
             p->attack_timer, p->food_timer, p->potion_timer, p->combo_timer);
    DrawText(buf, panel_x + 4, y, 10, DBG_COL_LABEL); y += lh;

    static const char* pray_str[] = { "OFF", "Melee", "Range", "Magic" };
    snprintf(buf, sizeof(buf), "Prayer: %s  Drain ctr: %d  Bonus: +%d",
             pray_str[p->prayer], p->prayer_drain_counter, p->prayer_bonus);
    DrawText(buf, panel_x + 4, y, 10, DBG_COL_LABEL); y += lh;

    snprintf(buf, sizeof(buf), "Sharks: %d  Doses: %d  Ammo: %d",
             p->sharks_remaining, p->prayer_doses_remaining, p->ammo_count);
    DrawText(buf, panel_x + 4, y, 10, DBG_COL_LABEL); y += lh;

    snprintf(buf, sizeof(buf), "Target: %d  Approach: %d  Route: %d/%d",
             p->attack_target_idx, p->approach_target, p->route_idx, p->route_len);
    DrawText(buf, panel_x + 4, y, 10, DBG_COL_LABEL); y += lh;

    snprintf(buf, sizeof(buf), "Pending hits: %d  Running: %d",
             p->num_pending_hits, p->is_running);
    DrawText(buf, panel_x + 4, y, 10, DBG_COL_LABEL); y += lh;

    snprintf(buf, sizeof(buf), "This tick: dmg_taken=%d hit_landed=%d food=%d pot=%d pray=%d",
             p->damage_taken_this_tick, p->hit_landed_this_tick,
             p->food_eaten_this_tick, p->potion_used_this_tick, p->prayer_changed_this_tick);
    DrawText(buf, panel_x + 4, y, 10, DBG_COL_DIM); y += lh;

    snprintf(buf, sizeof(buf), "Totals: dmg_dealt=%d dmg_taken=%d food=%d pots=%d",
             p->total_damage_dealt, p->total_damage_taken,
             p->total_food_eaten, p->total_potions_used);
    DrawText(buf, panel_x + 4, y, 10, DBG_COL_DIM); y += lh;

    /* NPC info panels (compact, for each active NPC) */
    y += 6;
    int npc_panel_y = y;
    static const char* npc_names[] = {"?","Tz-Kih","Tz-Kek","Kek-Sm","Tok-Xil",
        "MejKot","Ket-Zek","Jad","HurKot"};
    static const char* style_str[] = {"none","melee","ranged","magic"};

    for (int i = 0; i < FC_MAX_NPCS; i++) {
        const FcNpc* n = &state->npcs[i];
        if (!n->active) continue;

        int nph = lh * 4 + 8;
        DrawRectangle(panel_x, npc_panel_y, pw, nph, DBG_COL_PANEL);
        int ny = npc_panel_y + 4;

        const char* name = (n->npc_type > 0 && n->npc_type < 9) ? npc_names[n->npc_type] : "?";
        Color hdr_col = n->is_dead ? DBG_COL_BAD : DBG_COL_VALUE;
        snprintf(buf, sizeof(buf), "NPC[%d] %s (lv%d) %s  spawn:%d",
                 i, name, n->npc_type, n->is_dead ? "DEAD" : "", n->spawn_index);
        DrawText(buf, panel_x + 4, ny, 10, hdr_col); ny += lh;

        snprintf(buf, sizeof(buf), "Pos:(%d,%d) sz=%d  HP:%d/%d  Style:%s",
                 n->x, n->y, n->size, n->current_hp/10, n->max_hp/10,
                 style_str[n->attack_style]);
        DrawText(buf, panel_x + 4, ny, 10, DBG_COL_LABEL); ny += lh;

        snprintf(buf, sizeof(buf), "AtkTmr:%d/%d  Range:%d  MaxHit:%d  Aggro:%d",
                 n->attack_timer, n->attack_speed, n->attack_range,
                 n->max_hit/10, n->aggro_target);
        DrawText(buf, panel_x + 4, ny, 10, DBG_COL_LABEL); ny += lh;

        if (n->npc_type == NPC_TZTOK_JAD) {
            static const char* tele_str[] = {"idle","MAGIC","RANGED"};
            snprintf(buf, sizeof(buf), "Jad: telegraph=%s next=%s",
                     tele_str[n->jad_telegraph], style_str[n->jad_next_style]);
            DrawText(buf, panel_x + 4, ny, 10, DBG_COL_BAD);
        } else if (n->npc_type == NPC_YT_HURKOT) {
            snprintf(buf, sizeof(buf), "Healer: distracted=%d heal_target=%d",
                     n->healer_distracted, n->heal_target_idx);
            DrawText(buf, panel_x + 4, ny, 10, DBG_COL_LABEL);
        } else {
            snprintf(buf, sizeof(buf), "Pending hits:%d  death_timer:%d",
                     n->num_pending_hits, n->death_timer);
            DrawText(buf, panel_x + 4, ny, 10, DBG_COL_DIM);
        }

        npc_panel_y += nph + 2;
        if (npc_panel_y > 900) break;  /* don't overflow screen */
    }
}

/* ======================================================================== */
/* C. RL observation / action mask / reward overlay (2D)                    */
/* ======================================================================== */

static void dbg_draw_obs(const FcState* state, int dbg_flags) {
    float obs[FC_OBS_SIZE];
    fc_write_obs(state, obs);

    int rx = 300;
    int ry = 160;
    int lh = 12;
    char buf[128];

    if (dbg_flags & DBG_OBS) {
        int pw = 260, ph = lh * 12 + 10;
        DrawRectangle(rx, ry, pw, ph, DBG_COL_PANEL);
        int y = ry + 4;
        DrawText("OBSERVATION (policy)", rx + 4, y, 11, DBG_COL_VALUE); y += lh + 2;

        snprintf(buf, sizeof(buf), "HP:%.2f Pray:%.2f X:%.2f Y:%.2f",
                 obs[0], obs[1], obs[2], obs[3]);
        DrawText(buf, rx + 4, y, 9, DBG_COL_LABEL); y += lh;
        snprintf(buf, sizeof(buf), "AtkTmr:%.2f FoodTmr:%.2f PotTmr:%.2f",
                 obs[4], obs[5], obs[6]);
        DrawText(buf, rx + 4, y, 9, DBG_COL_LABEL); y += lh;
        snprintf(buf, sizeof(buf), "PrayMel:%.0f PrayRng:%.0f PrayMag:%.0f",
                 obs[10], obs[11], obs[12]);
        DrawText(buf, rx + 4, y, 9, DBG_COL_LABEL); y += lh;
        snprintf(buf, sizeof(buf), "Sharks:%.2f Doses:%.2f Ammo:%.2f",
                 obs[13], obs[14], obs[15]);
        DrawText(buf, rx + 4, y, 9, DBG_COL_LABEL); y += lh;

        /* NPC slots summary */
        y += 4;
        DrawText("NPC slots:", rx + 4, y, 9, DBG_COL_DIM); y += lh;
        for (int s = 0; s < FC_OBS_NPC_SLOTS; s++) {
            int base = FC_OBS_NPC_START + s * FC_OBS_NPC_STRIDE;
            if (obs[base + FC_NPC_VALID] < 0.5f) continue;
            snprintf(buf, sizeof(buf), " [%d] type:%.1f hp:%.2f dist:%.2f style:%.1f tele:%.1f",
                     s, obs[base+1], obs[base+4], obs[base+5], obs[base+6], obs[base+10]);
            DrawText(buf, rx + 4, y, 8, DBG_COL_LABEL); y += lh - 1;
        }

        /* Meta */
        y += 4;
        int mbase = FC_OBS_META_START;
        snprintf(buf, sizeof(buf), "Wave:%.2f Rot:%.2f Remain:%.2f Tick:%.4f",
                 obs[mbase], obs[mbase+1], obs[mbase+2], obs[mbase+3]);
        DrawText(buf, rx + 4, y, 9, DBG_COL_LABEL);

        ry += ph + 4;
    }

    if (dbg_flags & DBG_MASK) {
        float mask[FC_ACTION_MASK_SIZE];
        fc_write_mask(state, mask);

        int pw = 260, ph = lh * 10 + 10;
        DrawRectangle(rx, ry, pw, ph, DBG_COL_PANEL);
        int y = ry + 4;
        DrawText("ACTION MASK", rx + 4, y, 11, DBG_COL_VALUE); y += lh + 2;

        /* Move mask */
        snprintf(buf, sizeof(buf), "MOVE: ");
        int len = (int)strlen(buf);
        for (int m = 0; m < FC_MOVE_DIM && len < 120; m++) {
            buf[len++] = mask[FC_MASK_MOVE_START + m] > 0.5f ? '1' : '0';
        }
        buf[len] = '\0';
        DrawText(buf, rx + 4, y, 8, DBG_COL_LABEL); y += lh;

        /* Attack mask */
        snprintf(buf, sizeof(buf), "ATK:  ");
        len = (int)strlen(buf);
        for (int m = 0; m < FC_ATTACK_DIM && len < 120; m++) {
            buf[len++] = mask[FC_MASK_ATTACK_START + m] > 0.5f ? '1' : '0';
        }
        buf[len] = '\0';
        DrawText(buf, rx + 4, y, 8, DBG_COL_LABEL); y += lh;

        /* Prayer mask */
        snprintf(buf, sizeof(buf), "PRAY: ");
        len = (int)strlen(buf);
        for (int m = 0; m < FC_PRAYER_DIM && len < 120; m++) {
            buf[len++] = mask[FC_MASK_PRAYER_START + m] > 0.5f ? '1' : '0';
        }
        buf[len] = '\0';
        DrawText(buf, rx + 4, y, 8, DBG_COL_LABEL); y += lh;

        /* Eat/Drink mask */
        snprintf(buf, sizeof(buf), "EAT:%c%c%c DRINK:%c%c",
                 mask[FC_MASK_EAT_START+0]>0.5f?'1':'0',
                 mask[FC_MASK_EAT_START+1]>0.5f?'1':'0',
                 mask[FC_MASK_EAT_START+2]>0.5f?'1':'0',
                 mask[FC_MASK_DRINK_START+0]>0.5f?'1':'0',
                 mask[FC_MASK_DRINK_START+1]>0.5f?'1':'0');
        DrawText(buf, rx + 4, y, 8, DBG_COL_LABEL); y += lh;

        /* Target X/Y mask summary (just count valid) */
        int valid_x = 0, valid_y = 0;
        for (int m = 0; m < FC_MOVE_TARGET_X_DIM; m++)
            if (mask[FC_MASK_TARGET_X_START + m] > 0.5f) valid_x++;
        for (int m = 0; m < FC_MOVE_TARGET_Y_DIM; m++)
            if (mask[FC_MASK_TARGET_Y_START + m] > 0.5f) valid_y++;
        snprintf(buf, sizeof(buf), "TARGET_X: %d/65 valid  TARGET_Y: %d/65 valid",
                 valid_x, valid_y);
        DrawText(buf, rx + 4, y, 8, DBG_COL_LABEL);

        ry += ph + 4;
    }

    if (dbg_flags & DBG_REWARD) {
        float* rwd = obs + FC_REWARD_START;
        int pw = 260, ph = lh * 10 + 10;
        DrawRectangle(rx, ry, pw, ph, DBG_COL_PANEL);
        int y = ry + 4;
        DrawText("REWARD FEATURES", rx + 4, y, 11, DBG_COL_VALUE); y += lh + 2;

        static const char* rwd_names[] = {
            "dmg_dealt","dmg_taken","npc_kill","wave_clear",
            "jad_dmg","jad_kill","death","cave_done",
            "food","ppot","jad_pray_ok","jad_pray_bad",
            "invalid","movement","idle","tick_pen"
        };

        for (int r = 0; r < FC_REWARD_FEATURES; r++) {
            Color c = (rwd[r] > 0.001f) ? DBG_COL_GOOD : DBG_COL_DIM;
            if (r == FC_RWD_DAMAGE_TAKEN || r == FC_RWD_PLAYER_DEATH ||
                r == FC_RWD_WRONG_JAD_PRAY || r == FC_RWD_INVALID_ACTION) {
                c = (rwd[r] > 0.001f) ? DBG_COL_BAD : DBG_COL_DIM;
            }
            snprintf(buf, sizeof(buf), "%-12s %.4f", rwd_names[r], rwd[r]);
            DrawText(buf, rx + 4, y, 8, c); y += lh - 2;
        }
    }
}

/* ======================================================================== */
/* Side panel debug tabs (drawn inside the side panel area)                 */
/* ======================================================================== */

/* Draw debug info as a tabbed panel section.
 * px = panel X, x = content X, by = Y start position, pw = panel width.
 * dbg_tab: 0=player, 1=obs, 2=mask, 3=reward.
 * Returns end Y position. */
static int dbg_draw_panel_tabs(const FcState* state, int px, int x, int by,
                                int pw, int dbg_tab) {
    char buf[256];
    int lh = 12;

    /* Tab buttons */
    DrawLine(px+4, by, px+pw-4, by, CLITERAL(Color){42,36,28,255}); by += 2;
    static const char* dtab_labels[] = { "Player", "Obs", "Mask", "Reward", "Log" };
    int num_dtabs = 5;
    int dtab_w = (pw - 12) / num_dtabs;
    int dtab_h = 16;
    for (int t = 0; t < num_dtabs; t++) {
        int tx = px + 4 + t * dtab_w;
        int selected = (t == dbg_tab);
        Color bg = selected ? CLITERAL(Color){82,73,61,255} : CLITERAL(Color){42,36,28,255};
        DrawRectangle(tx, by, dtab_w, dtab_h, bg);
        Color tc = selected ? DBG_COL_VALUE : DBG_COL_DIM;
        int tw = MeasureText(dtab_labels[t], 8);
        DrawText(dtab_labels[t], tx + (dtab_w - tw) / 2, by + 4, 8, tc);
        if (selected) DrawLine(tx+2, by+dtab_h-1, tx+dtab_w-2, by+dtab_h-1, DBG_COL_VALUE);
    }
    by += dtab_h + 3;

    /* Tab content */
    if (dbg_tab == 0) {
        /* Player state */
        const FcPlayer* p = &state->player;
        static const char* pray_str[] = { "OFF", "Melee", "Range", "Magic" };

        snprintf(buf, sizeof(buf), "Pos:(%d,%d) Face:%.0f", p->x, p->y, p->facing_angle);
        DrawText(buf, x, by, 8, DBG_COL_LABEL); by += lh;
        snprintf(buf, sizeof(buf), "HP:%d/%d Pray:%d/%d",
                 p->current_hp/10, p->max_hp/10, p->current_prayer/10, p->max_prayer/10);
        DrawText(buf, x, by, 8, DBG_COL_LABEL); by += lh;
        snprintf(buf, sizeof(buf), "Tmr atk:%d fd:%d pot:%d",
                 p->attack_timer, p->food_timer, p->potion_timer);
        DrawText(buf, x, by, 8, DBG_COL_LABEL); by += lh;
        snprintf(buf, sizeof(buf), "Prayer:%s Drain:%d Bonus:+%d",
                 pray_str[p->prayer], p->prayer_drain_counter, p->prayer_bonus);
        DrawText(buf, x, by, 8, DBG_COL_LABEL); by += lh;
        snprintf(buf, sizeof(buf), "Shark:%d Dose:%d Ammo:%d",
                 p->sharks_remaining, p->prayer_doses_remaining, p->ammo_count);
        DrawText(buf, x, by, 8, DBG_COL_LABEL); by += lh;
        snprintf(buf, sizeof(buf), "Tgt:%d Appr:%d Route:%d/%d",
                 p->attack_target_idx, p->approach_target, p->route_idx, p->route_len);
        DrawText(buf, x, by, 8, DBG_COL_LABEL); by += lh;
        snprintf(buf, sizeof(buf), "Hits:%d Run:%d Regen:%d",
                 p->num_pending_hits, p->is_running, p->hp_regen_counter);
        DrawText(buf, x, by, 8, DBG_COL_LABEL); by += lh;

        /* Active NPCs compact list */
        by += 2;
        static const char* npc_names[] = {"?","Kih","Kek","KSm","Xil","Mej","Zek","Jad","Hur"};
        for (int i = 0; i < FC_MAX_NPCS; i++) {
            const FcNpc* n = &state->npcs[i];
            if (!n->active) continue;
            const char* nm = (n->npc_type>0 && n->npc_type<9) ? npc_names[n->npc_type] : "?";
            snprintf(buf, sizeof(buf), "[%d]%s hp:%d atk:%d/%d",
                     i, nm, n->current_hp/10, n->attack_timer, n->attack_speed);
            Color c = n->is_dead ? DBG_COL_BAD : DBG_COL_DIM;
            DrawText(buf, x, by, 7, c); by += lh - 2;
        }

    } else if (dbg_tab == 1) {
        /* Observation values */
        float obs[FC_OBS_SIZE];
        fc_write_obs(state, obs);

        snprintf(buf, sizeof(buf), "HP:%.2f Pray:%.2f", obs[0], obs[1]);
        DrawText(buf, x, by, 8, DBG_COL_LABEL); by += lh;
        snprintf(buf, sizeof(buf), "X:%.2f Y:%.2f", obs[2], obs[3]);
        DrawText(buf, x, by, 8, DBG_COL_LABEL); by += lh;
        snprintf(buf, sizeof(buf), "Atk:%.2f Fd:%.2f Pot:%.2f",
                 obs[4], obs[5], obs[6]);
        DrawText(buf, x, by, 8, DBG_COL_LABEL); by += lh;
        snprintf(buf, sizeof(buf), "Mel:%.0f Rng:%.0f Mag:%.0f",
                 obs[10], obs[11], obs[12]);
        DrawText(buf, x, by, 8, DBG_COL_LABEL); by += lh;
        snprintf(buf, sizeof(buf), "Shrk:%.2f Dose:%.2f Ammo:%.2f",
                 obs[13], obs[14], obs[15]);
        DrawText(buf, x, by, 8, DBG_COL_LABEL); by += lh;

        by += 2;
        DrawText("NPC slots:", x, by, 7, DBG_COL_DIM); by += lh - 1;
        for (int s = 0; s < FC_OBS_NPC_SLOTS; s++) {
            int base = FC_OBS_NPC_START + s * FC_OBS_NPC_STRIDE;
            if (obs[base + FC_NPC_VALID] < 0.5f) continue;
            snprintf(buf, sizeof(buf), "[%d] t:%.1f hp:%.2f d:%.2f",
                     s, obs[base+1], obs[base+4], obs[base+5]);
            DrawText(buf, x, by, 7, DBG_COL_LABEL); by += lh - 2;
        }

    } else if (dbg_tab == 2) {
        /* Action mask */
        float mask[FC_ACTION_MASK_SIZE];
        fc_write_mask(state, mask);

        DrawText("MOVE:", x, by, 8, DBG_COL_DIM);
        snprintf(buf, sizeof(buf), " ");
        int len = 1;
        for (int m = 0; m < FC_MOVE_DIM && len < 120; m++)
            buf[len++] = mask[FC_MASK_MOVE_START + m] > 0.5f ? '1' : '0';
        buf[len] = '\0';
        DrawText(buf, x + 30, by, 8, DBG_COL_LABEL); by += lh;

        DrawText("ATK:", x, by, 8, DBG_COL_DIM);
        len = 1; buf[0] = ' ';
        for (int m = 0; m < FC_ATTACK_DIM && len < 120; m++)
            buf[len++] = mask[FC_MASK_ATTACK_START + m] > 0.5f ? '1' : '0';
        buf[len] = '\0';
        DrawText(buf, x + 30, by, 8, DBG_COL_LABEL); by += lh;

        DrawText("PRAY:", x, by, 8, DBG_COL_DIM);
        len = 1; buf[0] = ' ';
        for (int m = 0; m < FC_PRAYER_DIM && len < 120; m++)
            buf[len++] = mask[FC_MASK_PRAYER_START + m] > 0.5f ? '1' : '0';
        buf[len] = '\0';
        DrawText(buf, x + 30, by, 8, DBG_COL_LABEL); by += lh;

        snprintf(buf, sizeof(buf), "EAT:%c%c%c  DRINK:%c%c",
                 mask[FC_MASK_EAT_START+0]>0.5f?'1':'0',
                 mask[FC_MASK_EAT_START+1]>0.5f?'1':'0',
                 mask[FC_MASK_EAT_START+2]>0.5f?'1':'0',
                 mask[FC_MASK_DRINK_START+0]>0.5f?'1':'0',
                 mask[FC_MASK_DRINK_START+1]>0.5f?'1':'0');
        DrawText(buf, x, by, 8, DBG_COL_LABEL); by += lh;

        int vx = 0, vy = 0;
        for (int m = 0; m < FC_MOVE_TARGET_X_DIM; m++)
            if (mask[FC_MASK_TARGET_X_START + m] > 0.5f) vx++;
        for (int m = 0; m < FC_MOVE_TARGET_Y_DIM; m++)
            if (mask[FC_MASK_TARGET_Y_START + m] > 0.5f) vy++;
        snprintf(buf, sizeof(buf), "TGT_X:%d/65 TGT_Y:%d/65", vx, vy);
        DrawText(buf, x, by, 8, DBG_COL_LABEL); by += lh;

    } else if (dbg_tab == 3) {
        /* Computed reward signals — mirrors fight_caves.h:fc_puffer_compute_reward().
         * Shows the actual reward contribution per signal, not just raw features.
         *
         * UPDATE THESE WEIGHTS when config/fight_caves.ini changes.
         * Set a weight to 0 to move a signal to the "inactive" section. */
        #define RW_DMG_DEALT       2.0f    /* active */
        #define RW_DMG_TAKEN      -0.75f   /* active (quadratic x70) */
        #define RW_NPC_KILL        5.0f    /* active */
        #define RW_WAVE_CLEAR     10.0f    /* active (x wave_num) */
        #define RW_JAD_DAMAGE      2.0f    /* active */
        #define RW_JAD_KILL       50.0f    /* active */
        #define RW_PLAYER_DEATH  -20.0f    /* active */
        #define RW_CAVE_COMPLETE 100.0f    /* active */
        #define RW_CORRECT_PRAY    1.0f    /* active (from config w_correct_danger_prayer) */
        #define RW_WRONG_PRAY_PEN -1.0f    /* active (hardcoded, wrong prayer on hit) */
        #define RW_NPC_PRAY_BONUS  2.0f    /* active (Ket-Zek/Tok-Xil/MejKot specific) */
        #define RW_MELEE_RANGE_PEN -0.5f   /* active (dangerous NPC at melee range) */
        #define RW_PRAY_FLICK      0.5f    /* active (prayer on without drain) */
        #define RW_COMBAT_IDLE    -0.5f    /* active (hardcoded, after 2 idle ticks) */
        #define RW_TICK_PENALTY   -0.001f  /* active */
        #define RW_INVALID_ACT    -0.1f    /* inactive (signal never fires) */
        #define RW_JAD_PRAY_OK     0.0f    /* inactive */
        #define RW_JAD_PRAY_BAD    0.0f    /* inactive */
        #define RW_WRONG_PRAY      0.0f    /* inactive */
        #define RW_MOVEMENT        0.0f    /* inactive */
        #define RW_IDLE            0.0f    /* inactive */

        float obs_buf[FC_OBS_SIZE];
        fc_write_obs(state, obs_buf);
        float* rwd = obs_buf + FC_REWARD_START;
        const FcPlayer* p = &state->player;

        /* Combat idle counter (static, persists across frames) */
        static int ticks_since_attack = 0;
        if (rwd[FC_RWD_DAMAGE_DEALT] > 0.0f) ticks_since_attack = 0;
        else if (state->npcs_remaining > 0) ticks_since_attack++;
        else ticks_since_attack = 0;

        /* Compute each active signal's reward contribution this tick */
        /* 1. damage_dealt — linear */
        float r_dmg_dealt = rwd[FC_RWD_DAMAGE_DEALT] * RW_DMG_DEALT;
        /* 2. damage_taken — quadratic x70 */
        float dmg_frac = rwd[FC_RWD_DAMAGE_TAKEN];
        float r_dmg_taken = dmg_frac * dmg_frac * 70.0f * RW_DMG_TAKEN;
        /* 3. npc_kill */
        float r_npc_kill = rwd[FC_RWD_NPC_KILL] * RW_NPC_KILL;
        /* 4. wave_clear — scaled by wave number */
        float r_wave_clear = 0.0f;
        if (rwd[FC_RWD_WAVE_CLEAR] > 0.0f) {
            int cleared = state->current_wave - 1;
            if (cleared < 1) cleared = 1;
            r_wave_clear = RW_WAVE_CLEAR * (float)cleared;
        }
        /* 5. jad_damage */
        float r_jad_dmg = rwd[FC_RWD_JAD_DAMAGE] * RW_JAD_DAMAGE;
        /* 6. jad_kill */
        float r_jad_kill = rwd[FC_RWD_JAD_KILL] * RW_JAD_KILL;
        /* 7. player_death */
        float r_death = rwd[FC_RWD_PLAYER_DEATH] * RW_PLAYER_DEATH;
        /* 8. cave_complete */
        float r_cave = rwd[FC_RWD_CAVE_COMPLETE] * RW_CAVE_COMPLETE;
        /* 9. food waste — penalty only, scaled by waste fraction */
        float r_food = 0.0f;
        if (rwd[FC_RWD_FOOD_USED] > 0.0f) {
            int pre_hp = state->pre_eat_hp;
            if (pre_hp >= p->max_hp) r_food = -5.0f;
            else { int w = 200 - (p->max_hp - pre_hp); r_food = (w > 0) ? -(float)w/200.0f : 0.0f; }
        }
        /* 10. pot waste — penalty only, scaled by waste fraction */
        float r_ppot = 0.0f;
        if (rwd[FC_RWD_PRAYER_POT_USED] > 0.0f) {
            int pre_pr = state->pre_drink_prayer;
            if (pre_pr >= p->max_prayer) r_ppot = -5.0f;
            else { int w = 170 - (p->max_prayer - pre_pr); r_ppot = (w > 0) ? -(float)w/170.0f : 0.0f; }
        }
        /* 11. correct/wrong prayer */
        float r_pray = 0.0f;
        float r_wrong_pray_pen = 0.0f;
        if (p->hit_style_this_tick != ATTACK_NONE && p->prayer != PRAYER_NONE) {
            if (fc_prayer_blocks_style(p->prayer, p->hit_style_this_tick))
                r_pray = RW_CORRECT_PRAY;
            else
                r_wrong_pray_pen = RW_WRONG_PRAY_PEN;
        }
        /* 12. NPC-specific prayer bonus */
        float r_npc_pray = 0.0f;
        if (p->hit_style_this_tick != ATTACK_NONE && p->prayer != PRAYER_NONE &&
            fc_prayer_blocks_style(p->prayer, p->hit_style_this_tick) &&
            p->attack_target_idx >= 0) {
            int src = p->hit_source_npc_type;
            if ((src == NPC_KET_ZEK && p->prayer == PRAYER_PROTECT_MAGIC) ||
                (src == NPC_TOK_XIL && p->prayer == PRAYER_PROTECT_RANGE) ||
                (src == NPC_YT_MEJKOT && p->prayer == PRAYER_PROTECT_MELEE))
                r_npc_pray = RW_NPC_PRAY_BONUS;
        }
        /* 13. dangerous NPC in melee range penalty */
        float r_melee_range = 0.0f;
        for (int ni = 0; ni < FC_MAX_NPCS; ni++) {
            const FcNpc* tn = &state->npcs[ni];
            if (!tn->active || tn->is_dead) continue;
            if (tn->npc_type == NPC_KET_ZEK || tn->npc_type == NPC_TOK_XIL ||
                tn->npc_type == NPC_YT_MEJKOT) {
                if (fc_distance_to_npc(p->x, p->y, tn) <= 1) r_melee_range += RW_MELEE_RANGE_PEN;
            }
        }
        /* 14. prayer flick reward */
        float r_flick = 0.0f;
        if (p->prayer != PRAYER_NONE && p->prayer_changed_this_tick) {
            int res = 60 + 2 * p->prayer_bonus;
            if (p->prayer_drain_counter + 12 <= res) r_flick = RW_PRAY_FLICK;
        }
        /* 15. combat_idle */
        float r_idle_combat = (ticks_since_attack >= 2 && state->npcs_remaining > 0)
            ? RW_COMBAT_IDLE : 0.0f;
        /* 16. tick_penalty */
        float r_tick = RW_TICK_PENALTY;
        /* 17. kiting reward — +1.0 when dealing damage from range 5-7 */
        float r_kite = 0.0f;
        if (rwd[FC_RWD_DAMAGE_DEALT] > 0.0f && p->attack_target_idx >= 0) {
            const FcNpc* kt = &state->npcs[p->attack_target_idx];
            if (kt->active && !kt->is_dead) {
                int kd = fc_distance_to_npc(p->x, p->y, kt);
                if (kd >= 5 && kd <= 7) r_kite = 1.0f;
            }
        }
        /* 18. unnecessary prayer penalty — -0.2 when praying with no threat */
        float r_pray_waste = 0.0f;
        if (p->prayer != PRAYER_NONE) {
            int threatened = 0;
            for (int ni = 0; ni < FC_MAX_NPCS; ni++) {
                const FcNpc* tn = &state->npcs[ni];
                if (!tn->active || tn->is_dead) continue;
                int td = fc_distance_to_npc(p->x, p->y, tn);
                if (td <= tn->attack_range) { threatened = 1; break; }
            }
            if (!threatened) {
                for (int hi = 0; hi < p->num_pending_hits; hi++)
                    if (p->pending_hits[hi].active) { threatened = 1; break; }
            }
            if (!threatened) r_pray_waste = -0.2f;
        }

        /* Inactive signals — raw feature values only */
        float r_invalid  = rwd[FC_RWD_INVALID_ACTION]      * RW_INVALID_ACT;
        float r_jad_ok   = rwd[FC_RWD_CORRECT_JAD_PRAY]    * RW_JAD_PRAY_OK;
        float r_jad_bad  = rwd[FC_RWD_WRONG_JAD_PRAY]      * RW_JAD_PRAY_BAD;
        float r_wrong_pr = rwd[FC_RWD_WRONG_DANGER_PRAY]   * RW_WRONG_PRAY;
        float r_move     = rwd[FC_RWD_MOVEMENT]             * RW_MOVEMENT;
        float r_idle     = rwd[FC_RWD_IDLE]                 * RW_IDLE;

        float total = r_dmg_dealt + r_dmg_taken + r_npc_kill + r_wave_clear
            + r_jad_dmg + r_jad_kill + r_death + r_cave + r_food + r_ppot
            + r_pray + r_wrong_pray_pen + r_npc_pray + r_melee_range + r_flick
            + r_kite + r_pray_waste + r_idle_combat + r_tick;

        /* Draw active signals */
        int sh = lh - 3;
        DrawText("-- ACTIVE --", x, by, 8, DBG_COL_VALUE); by += sh + 2;

        struct { const char* name; float val; int is_penalty; } active[] = {
            {"dmg_dealt",   r_dmg_dealt,      0},
            {"dmg_taken",   r_dmg_taken,      1},
            {"npc_kill",    r_npc_kill,       0},
            {"wave_clear",  r_wave_clear,     0},
            {"jad_dmg",     r_jad_dmg,        0},
            {"jad_kill",    r_jad_kill,       0},
            {"death",       r_death,          1},
            {"cave_done",   r_cave,           0},
            {"food_waste",  r_food,           1},
            {"pot_waste",   r_ppot,           1},
            {"pray_ok",     r_pray,           0},
            {"pray_wrong",  r_wrong_pray_pen, 1},
            {"npc_pray",    r_npc_pray,       0},
            {"melee_rng",   r_melee_range,    1},
            {"pray_flick",  r_flick,          0},
            {"kite",        r_kite,           0},
            {"pray_waste",  r_pray_waste,     1},
            {"combat_idle", r_idle_combat,    1},
            {"tick_pen",    r_tick,           1},
        };
        for (int i = 0; i < 19; i++) {
            Color c = DBG_COL_DIM;
            float v = active[i].val;
            if (v > 0.0001f)  c = DBG_COL_GOOD;
            if (v < -0.0001f) c = DBG_COL_BAD;
            snprintf(buf, sizeof(buf), "%-11s %+.4f", active[i].name, v);
            DrawText(buf, x, by, 8, c); by += sh;
        }

        /* Total */
        by += 2;
        Color tc = (total > 0.0001f) ? DBG_COL_GOOD : (total < -0.0001f) ? DBG_COL_BAD : DBG_COL_DIM;
        snprintf(buf, sizeof(buf), "TOTAL       %+.4f", total);
        DrawText(buf, x, by, 8, tc); by += sh + 4;

        /* Draw inactive signals */
        DrawText("-- INACTIVE --", x, by, 8, DBG_COL_DIM); by += sh + 2;
        struct { const char* name; float raw; } inactive[] = {
            {"invalid",    rwd[FC_RWD_INVALID_ACTION]},
            {"jad_ok",     rwd[FC_RWD_CORRECT_JAD_PRAY]},
            {"jad_bad",    rwd[FC_RWD_WRONG_JAD_PRAY]},
            {"wrong_pray", rwd[FC_RWD_WRONG_DANGER_PRAY]},
            {"movement",   rwd[FC_RWD_MOVEMENT]},
            {"idle",       rwd[FC_RWD_IDLE]},
        };
        for (int i = 0; i < 6; i++) {
            snprintf(buf, sizeof(buf), "%-11s  %.4f", inactive[i].name, inactive[i].raw);
            DrawText(buf, x, by, 8, DBG_COL_DIM); by += sh;
        }
    } else if (dbg_tab == 4) {
        /* Event log — scrollable with scrollbar, most recent at top */
        int max_visible = 20;
        int total = g_dbg_log.count;
        int entry_h = lh - 2;
        int log_h = max_visible * entry_h;
        int scrollbar_w = 10;
        int content_w = pw - 16 - scrollbar_w;

        if (total == 0) {
            DrawText("No events yet", x, by, 8, DBG_COL_DIM);
            by += lh;
        } else {
            int max_scroll = total - max_visible;
            if (max_scroll < 0) max_scroll = 0;

            /* Clamp scroll */
            if (g_dbg_log.scroll_offset > max_scroll)
                g_dbg_log.scroll_offset = max_scroll;
            if (g_dbg_log.scroll_offset < 0)
                g_dbg_log.scroll_offset = 0;

            /* Mouse wheel scroll */
            float wheel = GetMouseWheelMove();
            if (wheel != 0.0f) {
                Vector2 mp = GetMousePosition();
                if (mp.x >= px) {
                    g_dbg_log.scroll_offset -= (int)wheel * 3;
                    if (g_dbg_log.scroll_offset < 0) g_dbg_log.scroll_offset = 0;
                    if (g_dbg_log.scroll_offset > max_scroll) g_dbg_log.scroll_offset = max_scroll;
                }
            }

            /* Scrollbar track */
            int sb_x = px + pw - scrollbar_w - 4;
            int sb_y = by;
            DrawRectangle(sb_x, sb_y, scrollbar_w, log_h, CLITERAL(Color){30,26,20,255});

            /* Scrollbar thumb */
            if (total > max_visible) {
                float thumb_frac = (float)max_visible / (float)total;
                int thumb_h = (int)(log_h * thumb_frac);
                if (thumb_h < 12) thumb_h = 12;
                float scroll_frac = (max_scroll > 0) ? (float)g_dbg_log.scroll_offset / (float)max_scroll : 0;
                int thumb_y = sb_y + (int)((log_h - thumb_h) * scroll_frac);
                DrawRectangle(sb_x, thumb_y, scrollbar_w, thumb_h, CLITERAL(Color){120,110,90,255});

                /* Drag scrollbar with mouse */
                if (IsMouseButtonDown(MOUSE_BUTTON_LEFT)) {
                    Vector2 mp = GetMousePosition();
                    if (mp.x >= sb_x && mp.x < sb_x + scrollbar_w &&
                        mp.y >= sb_y && mp.y < sb_y + log_h) {
                        float click_frac = (mp.y - sb_y) / (float)log_h;
                        g_dbg_log.scroll_offset = (int)(click_frac * (float)(max_scroll + 1));
                        if (g_dbg_log.scroll_offset > max_scroll) g_dbg_log.scroll_offset = max_scroll;
                    }
                }
            }

            /* Draw entries */
            int drawn = 0;
            for (int i = g_dbg_log.scroll_offset; i < total && drawn < max_visible; i++) {
                int idx = (g_dbg_log.head - 1 - i + DBG_LOG_MAX_ENTRIES * 2) % DBG_LOG_MAX_ENTRIES;
                snprintf(buf, sizeof(buf), "t%d %s", g_dbg_log.tick[idx], g_dbg_log.entries[idx]);
                /* Truncate to fit content width */
                DrawText(buf, x, by, 7, g_dbg_log.color[idx]);
                by += entry_h;
                drawn++;
            }

            /* Pad remaining space if fewer entries than max_visible */
            by += (max_visible - drawn) * entry_h;
        }
    }

    return by;
}

#endif /* FC_DEBUG_OVERLAY_H */
