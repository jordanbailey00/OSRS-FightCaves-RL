#ifndef FC_PATHFINDING_H
#define FC_PATHFINDING_H

#include "fc_types.h"

/* Try to move entity from (x,y) toward target offset (dx,dy).
 * Walk (max 1 step) or run (max 2 steps). Diagonal-first fallback.
 * Returns number of tiles actually moved (0, 1, or 2).
 * Updates *x, *y in place. */
int fc_move_toward(int* x, int* y, int dx, int dy, int max_steps,
                   const uint8_t walkable[FC_ARENA_WIDTH][FC_ARENA_HEIGHT]);

/* Greedy step: move NPC one tile closer to (target_x, target_y).
 * Diagonal-first, then cardinal fallback.
 * Returns 1 if moved, 0 if blocked. */
int fc_npc_step_toward(int* x, int* y, int target_x, int target_y,
                       const uint8_t walkable[FC_ARENA_WIDTH][FC_ARENA_HEIGHT]);

#endif /* FC_PATHFINDING_H */
