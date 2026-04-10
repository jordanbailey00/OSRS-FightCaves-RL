#include "fc_api.h"

/*
 * XORshift32 RNG — single state, deterministic, seeded at reset.
 * All randomness in the simulation flows through this RNG.
 * Adopted from PufferLib OSRS PvP (osrs_pvp_types.h).
 */

void fc_rng_seed(FcState* state, uint32_t seed) {
    /* XORshift32 cannot have state 0 — if seed is 0, use a fixed nonzero value */
    state->rng_state = (seed != 0) ? seed : 0x12345678u;
    state->rng_seed = seed;
}

uint32_t fc_rng_next(FcState* state) {
    uint32_t x = state->rng_state;
    x ^= x << 13;
    x ^= x >> 17;
    x ^= x << 5;
    state->rng_state = x;
    return x;
}

int fc_rng_int(FcState* state, int max) {
    if (max <= 0) return 0;
    /* Lemire's fast range reduction — replaces IDIV (10-40 cycles) with
     * MUL + shift (3-4 cycles). Bias < 1/2^32, negligible for game rolls. */
    return (int)(((uint64_t)fc_rng_next(state) * (uint64_t)max) >> 32);
}

float fc_rng_float(FcState* state) {
    return (float)(fc_rng_next(state) & 0x00FFFFFFu) / (float)0x01000000u;
}
