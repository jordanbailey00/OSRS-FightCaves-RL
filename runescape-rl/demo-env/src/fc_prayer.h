#ifndef FC_PRAYER_H
#define FC_PRAYER_H

#include "fc_types.h"

/* Drain prayer points based on active prayer and bonus. Called each tick. */
void fc_prayer_drain_tick(FcPlayer* p);

/* Apply a prayer action (from FC_PRAYER_* constants in fc_contracts.h) */
void fc_prayer_apply_action(FcPlayer* p, int prayer_action);

/* Prayer potion restore amount in tenths (level-dependent) */
int fc_prayer_potion_restore(int prayer_level);

#endif /* FC_PRAYER_H */
