#include "fc_api.h"

/*
 * fc_prayer.c — Prayer activation, drain, and potion restore.
 *
 * OSRS prayer drain:
 *   drain_counter accumulates each tick based on active prayer cost.
 *   When counter >= (60 + 2 * prayer_bonus), subtract 1 prayer point (in tenths: 10).
 *   Protection prayers cost 12 drain per tick.
 *
 * Prayer potion restore:
 *   floor(prayer_level * 0.25) + 7 points per dose.
 *   For level 43: floor(10.75) + 7 = 17 points → 170 in tenths.
 */

#define PRAYER_OVERHEAD_DRAIN_RATE 12

void fc_prayer_drain_tick(FcPlayer* p) {
    if (p->prayer == PRAYER_NONE) return;
    if (p->current_prayer <= 0) {
        /* Auto-deactivate if prayer points depleted */
        p->prayer = PRAYER_NONE;
        return;
    }

    /* Drain 1 point per tick for overhead prayers.
     * Simplified from the accumulator model — at prayer bonus 5,
     * OSRS drains ~1 point every ~5.8 ticks. We use a simpler model:
     * drain_rate / (60 + 2 * prayer_bonus) points per tick, accumulated. */
    int resistance = 60 + 2 * p->prayer_bonus;
    /* We track drain in the attack_timer's unused bits — actually let's just
     * do a direct per-tick drain in tenths. At bonus 5, resistance = 70.
     * 12/70 ≈ 0.171 points/tick. In tenths: ~1.71/tick.
     * Simpler: accumulate drain_rate, when >= resistance, subtract 10 (1 point in tenths). */

    /* We'll use a static drain counter approach. Since we don't have a dedicated
     * field, we'll drain directly: subtract (drain_rate * 10 / resistance) tenths per tick,
     * but that loses precision. Better: track via a counter on the state.
     *
     * For simplicity and accuracy: drain 10 tenths (1 point) every
     * ceil(resistance / drain_rate) ticks = ceil(70/12) = 6 ticks at bonus 5.
     * That's ~7.2 points per 43 ticks, fairly close to OSRS.
     *
     * Implementation: use a modular tick counter. */

    /* Actually, just drain a fixed amount per tick in tenths for now.
     * OSRS at prayer bonus 5: ~1 point per 5.83 ticks.
     * In tenths: 10 / 5.83 ≈ 1.71 tenths per tick.
     * Round to integer: drain 2 tenths per tick (slightly fast, close enough). */
    int drain = (PRAYER_OVERHEAD_DRAIN_RATE * 10 + resistance - 1) / resistance;
    /* drain = ceil(120 / 70) = ceil(1.71) = 2 tenths per tick at bonus 5 */
    p->current_prayer -= drain;
    if (p->current_prayer <= 0) {
        p->current_prayer = 0;
        p->prayer = PRAYER_NONE;
    }
}

void fc_prayer_apply_action(FcPlayer* p, int prayer_action) {
    switch (prayer_action) {
        case 0: /* FC_PRAYER_NO_CHANGE */ break;
        case 1: /* FC_PRAYER_OFF */
            p->prayer = PRAYER_NONE;
            break;
        case 2: /* FC_PRAYER_MAGIC */
            p->prayer = (p->current_prayer > 0) ? PRAYER_PROTECT_MAGIC : PRAYER_NONE;
            break;
        case 3: /* FC_PRAYER_RANGE */
            p->prayer = (p->current_prayer > 0) ? PRAYER_PROTECT_RANGE : PRAYER_NONE;
            break;
        case 4: /* FC_PRAYER_MELEE */
            p->prayer = (p->current_prayer > 0) ? PRAYER_PROTECT_MELEE : PRAYER_NONE;
            break;
    }
}

int fc_prayer_potion_restore(int prayer_level) {
    /* floor(level * 0.25) + 7 points → in tenths */
    return (prayer_level / 4 + 7) * 10;
}
