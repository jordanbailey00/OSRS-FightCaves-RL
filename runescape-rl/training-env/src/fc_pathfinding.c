#include "fc_pathfinding.h"

/*
 * fc_pathfinding.c — Grid-based movement on the Fight Caves arena.
 *
 * Uses the greedy diagonal-first approach from PufferLib OSRS PvP.
 * Walk = 1 step, Run = up to 2 steps.
 */

static int tile_walkable(int x, int y,
                         const uint8_t walkable[FC_ARENA_WIDTH][FC_ARENA_HEIGHT]) {
    if (x < 0 || x >= FC_ARENA_WIDTH || y < 0 || y >= FC_ARENA_HEIGHT) return 0;
    return walkable[x][y];
}

int fc_move_toward(int* x, int* y, int dx, int dy, int max_steps,
                   const uint8_t walkable[FC_ARENA_WIDTH][FC_ARENA_HEIGHT]) {
    int tx = *x + dx;
    int ty = *y + dy;
    int steps = 0;

    for (int step = 0; step < max_steps; step++) {
        if (*x == tx && *y == ty) break;

        /* Compute direction toward target */
        int sx = 0, sy = 0;
        if (tx > *x) sx = 1; else if (tx < *x) sx = -1;
        if (ty > *y) sy = 1; else if (ty < *y) sy = -1;

        /* Try diagonal first, then x-only, then y-only */
        if (sx != 0 && sy != 0 && tile_walkable(*x + sx, *y + sy, walkable)) {
            *x += sx; *y += sy; steps++;
        } else if (sx != 0 && tile_walkable(*x + sx, *y, walkable)) {
            *x += sx; steps++;
        } else if (sy != 0 && tile_walkable(*x, *y + sy, walkable)) {
            *y += sy; steps++;
        } else {
            break;  /* blocked */
        }
    }
    return steps;
}

int fc_npc_step_toward(int* x, int* y, int target_x, int target_y,
                       const uint8_t walkable[FC_ARENA_WIDTH][FC_ARENA_HEIGHT]) {
    int dx = 0, dy = 0;
    if (target_x > *x) dx = 1; else if (target_x < *x) dx = -1;
    if (target_y > *y) dy = 1; else if (target_y < *y) dy = -1;

    if (dx == 0 && dy == 0) return 0;  /* already there */

    /* Try diagonal, then x-only, then y-only */
    if (dx != 0 && dy != 0 && tile_walkable(*x + dx, *y + dy, walkable)) {
        *x += dx; *y += dy; return 1;
    }
    if (dx != 0 && tile_walkable(*x + dx, *y, walkable)) {
        *x += dx; return 1;
    }
    if (dy != 0 && tile_walkable(*x, *y + dy, walkable)) {
        *y += dy; return 1;
    }
    return 0;
}
