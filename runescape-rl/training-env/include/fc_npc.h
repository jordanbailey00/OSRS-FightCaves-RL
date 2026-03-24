#ifndef FC_NPC_H
#define FC_NPC_H

#include "fc_types.h"

/* NPC stat table entry (one per NPC_TYPE) */
typedef struct {
    int max_hp;
    int attack_style;
    int attack_speed;   /* ticks between attacks */
    int attack_range;   /* 1 for melee, >1 for ranged/magic */
    int max_hit;        /* max damage per hit */
    int att_level;
    int att_bonus;
    int str_level;
    int str_bonus;
    int size;           /* tile footprint */
    int movement_speed; /* 1=walk, 2=run */
    int prayer_drain;   /* prayer drain on hit (Tz-Kih) */
} FcNpcStats;

/* Get stats for a given NPC type */
const FcNpcStats* fc_npc_get_stats(int npc_type);

/* Initialize an NPC slot from type and spawn position */
void fc_npc_spawn(FcNpc* npc, int npc_type, int x, int y, int spawn_index);

/* Run NPC AI for one tick: movement + attack decision */
void fc_npc_tick(FcState* state, int npc_idx);

#endif /* FC_NPC_H */
