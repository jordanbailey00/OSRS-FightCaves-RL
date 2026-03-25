#include "fc_types.h"
#include "fc_contracts.h"
#include "fc_api.h"
#include "fc_combat.h"
#include "fc_npc.h"
#include "fc_wave.h"
#include "fc_debug.h"
#include <stdio.h>
#include <string.h>
#include <math.h>

static int tests_run = 0;
static int tests_passed = 0;

#define ASSERT(cond, msg) do { \
    tests_run++; \
    if (!(cond)) { \
        printf("  FAIL: %s (line %d)\n", msg, __LINE__); \
    } else { \
        tests_passed++; \
    } \
} while(0)

#define ASSERT_EQ(a, b, msg) ASSERT((a) == (b), msg)
#define ASSERT_GT(a, b, msg) ASSERT((a) > (b), msg)
#define ASSERT_GE(a, b, msg) ASSERT((a) >= (b), msg)
#define ASSERT_LE(a, b, msg) ASSERT((a) <= (b), msg)

static void step_idle(FcState* state) {
    int idle[5] = {0,0,0,0,0};
    fc_step(state, idle);
}

/* Kill all active NPCs instantly by zeroing HP and resolving */
static void kill_all_npcs(FcState* state) {
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        if (state->npcs[i].active && !state->npcs[i].is_dead) {
            state->npcs[i].current_hp = 0;
            state->npcs[i].is_dead = 1;
            state->npcs[i].active = 0;
            state->npcs_remaining--;
            state->total_npcs_killed++;
            state->npcs_killed_this_tick++;
        }
    }
}

/* ======================================================================== */
/* Test: Wave table data correctness                                         */
/* ======================================================================== */

static void test_wave_table(void) {
    printf("test_wave_table:\n");

    /* Wave 1: 1 Tz-Kih */
    const FcWaveEntry* w1 = fc_wave_get(1);
    ASSERT_EQ(w1->num_spawns, 1, "wave 1 has 1 NPC");
    ASSERT_EQ(w1->npc_types[0], NPC_TZ_KIH, "wave 1 is Tz-Kih");

    /* Wave 3: 1 Tz-Kek */
    const FcWaveEntry* w3 = fc_wave_get(3);
    ASSERT_EQ(w3->num_spawns, 1, "wave 3 has 1 NPC");
    ASSERT_EQ(w3->npc_types[0], NPC_TZ_KEK, "wave 3 is Tz-Kek");

    /* Wave 7: 1 Tok-Xil */
    const FcWaveEntry* w7 = fc_wave_get(7);
    ASSERT_EQ(w7->num_spawns, 1, "wave 7 has 1 NPC");
    ASSERT_EQ(w7->npc_types[0], NPC_TOK_XIL, "wave 7 is Tok-Xil");

    /* Wave 15: 1 Yt-MejKot */
    const FcWaveEntry* w15 = fc_wave_get(15);
    ASSERT_EQ(w15->num_spawns, 1, "wave 15 has 1 NPC");
    ASSERT_EQ(w15->npc_types[0], NPC_YT_MEJKOT, "wave 15 is Yt-MejKot");

    /* Wave 31: 1 Ket-Zek */
    const FcWaveEntry* w31 = fc_wave_get(31);
    ASSERT_EQ(w31->num_spawns, 1, "wave 31 has 1 NPC");
    ASSERT_EQ(w31->npc_types[0], NPC_KET_ZEK, "wave 31 is Ket-Zek");

    /* Wave 63: TzTok-Jad */
    const FcWaveEntry* w63 = fc_wave_get(63);
    ASSERT_EQ(w63->num_spawns, 1, "wave 63 has 1 NPC");
    ASSERT_EQ(w63->npc_types[0], NPC_TZTOK_JAD, "wave 63 is Jad");

    /* Wave 58: 6 NPCs (max density) */
    const FcWaveEntry* w58 = fc_wave_get(58);
    ASSERT_EQ(w58->num_spawns, 6, "wave 58 has 6 NPCs");

    /* Spawn rotation: wave 1, rotation 0 → SPAWN_CENTER */
    int dir = fc_wave_spawn_dir(1, 0, 0);
    ASSERT_EQ(dir, SPAWN_CENTER, "wave 1 rot 0 → center");

    /* Spawn position mapping */
    int x, y;
    fc_spawn_position(SPAWN_SOUTH, &x, &y);
    ASSERT_EQ(x, 32, "south spawn x=32");
    ASSERT_EQ(y, 5, "south spawn y=5");

    fc_spawn_position(SPAWN_NORTH_WEST, &x, &y);
    ASSERT_EQ(x, 8, "NW spawn x=8");
    ASSERT_EQ(y, 55, "NW spawn y=55");
}

/* ======================================================================== */
/* Test: Wave 1 auto-spawn on reset                                          */
/* ======================================================================== */

static void test_wave1_reset(void) {
    printf("test_wave1_reset:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    ASSERT_EQ(state.current_wave, 1, "wave 1 on reset");
    ASSERT_EQ(state.npcs_remaining, 1, "1 NPC from wave 1");

    /* Find the spawned Tz-Kih */
    int found = 0;
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        if (state.npcs[i].active && state.npcs[i].npc_type == NPC_TZ_KIH) {
            found = 1;
            break;
        }
    }
    ASSERT(found, "wave 1 Tz-Kih spawned on reset");
}

/* ======================================================================== */
/* Test: Wave progression — kill wave, next wave spawns                      */
/* ======================================================================== */

static void test_wave_progression(void) {
    printf("test_wave_progression:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    ASSERT_EQ(state.current_wave, 1, "start at wave 1");

    /* Kill all NPCs and step to trigger wave advance */
    kill_all_npcs(&state);
    step_idle(&state);  /* check_terminal → wave_check_advance */

    ASSERT_EQ(state.current_wave, 2, "advanced to wave 2");
    ASSERT_EQ(state.npcs_remaining, 2, "wave 2 has 2 NPCs");

    /* Kill wave 2 and advance */
    kill_all_npcs(&state);
    step_idle(&state);

    ASSERT_EQ(state.current_wave, 3, "advanced to wave 3");
    /* Wave 3 is Tz-Kek */
    int found_kek = 0;
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        if (state.npcs[i].active && state.npcs[i].npc_type == NPC_TZ_KEK) {
            found_kek = 1;
            break;
        }
    }
    ASSERT(found_kek, "wave 3 spawns Tz-Kek");
}

/* ======================================================================== */
/* Test: Tz-Kek split doesn't break wave-clear                              */
/* ======================================================================== */

static void test_tz_kek_split_wave_clear(void) {
    printf("test_tz_kek_split_wave_clear:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    /* Advance to wave 3 (1 Tz-Kek) */
    kill_all_npcs(&state);
    step_idle(&state);  /* → wave 2 */
    kill_all_npcs(&state);
    step_idle(&state);  /* → wave 3 */

    ASSERT_EQ(state.current_wave, 3, "at wave 3");

    /* Find the Tz-Kek and kill it (will split) */
    int kek_idx = -1;
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        if (state.npcs[i].active && state.npcs[i].npc_type == NPC_TZ_KEK) {
            kek_idx = i;
            break;
        }
    }
    ASSERT_GE(kek_idx, 0, "found Tz-Kek");

    /* Kill the Tz-Kek directly */
    state.npcs[kek_idx].current_hp = 0;
    state.npcs[kek_idx].is_dead = 1;
    state.npcs[kek_idx].active = 0;
    state.npcs_remaining--;
    state.total_npcs_killed++;

    /* Trigger split */
    fc_npc_tz_kek_split(&state, state.npcs[kek_idx].x, state.npcs[kek_idx].y);

    /* Wave should NOT be cleared — splits are alive */
    ASSERT_EQ(state.npcs_remaining, 2, "2 splits alive");
    ASSERT_EQ(state.current_wave, 3, "still on wave 3 (splits alive)");

    /* Now kill the splits */
    kill_all_npcs(&state);
    step_idle(&state);

    /* NOW wave should advance */
    ASSERT_EQ(state.current_wave, 4, "advanced to wave 4 after killing splits");
}

/* ======================================================================== */
/* Test: Reach wave 63 (Jad) via fast-kill                                   */
/* ======================================================================== */

static void test_reach_jad(void) {
    printf("test_reach_jad:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    /* Fast-forward to wave 63 by killing each wave instantly */
    for (int w = 1; w < 63; w++) {
        kill_all_npcs(&state);
        step_idle(&state);
    }

    ASSERT_EQ(state.current_wave, 63, "reached wave 63");

    /* Jad should be spawned */
    int jad_found = 0;
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        if (state.npcs[i].active && state.npcs[i].npc_type == NPC_TZTOK_JAD) {
            jad_found = 1;
            ASSERT_EQ(state.npcs[i].max_hp, 2500, "Jad has 250 HP");
            ASSERT_EQ(state.npcs[i].size, 3, "Jad is size 3");
            break;
        }
    }
    ASSERT(jad_found, "Jad spawned on wave 63");
}

/* ======================================================================== */
/* Test: Jad healer auto-spawn at HP threshold                               */
/* ======================================================================== */

static void test_jad_healer_spawn(void) {
    printf("test_jad_healer_spawn:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    /* Fast-forward to wave 63 */
    for (int w = 1; w < 63; w++) {
        kill_all_npcs(&state);
        step_idle(&state);
    }
    ASSERT_EQ(state.current_wave, 63, "at wave 63");

    /* Find Jad and reduce HP below threshold */
    int jad_idx = -1;
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        if (state.npcs[i].active && state.npcs[i].npc_type == NPC_TZTOK_JAD) {
            jad_idx = i;
            break;
        }
    }
    ASSERT_GE(jad_idx, 0, "found Jad");

    /* Reduce to below 50% HP */
    state.npcs[jad_idx].current_hp = 1000;  /* 40% of 2500 */

    /* Step — healers should spawn */
    step_idle(&state);

    ASSERT_EQ(state.jad_healers_spawned, 1, "healer spawn flag set");

    int healer_count = 0;
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        if (state.npcs[i].active && state.npcs[i].npc_type == NPC_YT_HURKOT) {
            healer_count++;
        }
    }
    ASSERT_EQ(healer_count, FC_JAD_NUM_HEALERS, "4 Yt-HurKot spawned");
}

/* ======================================================================== */
/* Test: Cave completion                                                     */
/* ======================================================================== */

static void test_cave_complete(void) {
    printf("test_cave_complete:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    /* Fast-forward through all 63 waves */
    for (int w = 1; w <= 63; w++) {
        kill_all_npcs(&state);
        step_idle(&state);
    }

    /* Should be terminal: cave complete */
    ASSERT_EQ(state.terminal, TERMINAL_CAVE_COMPLETE, "cave complete terminal");
    ASSERT_EQ(state.wave_just_cleared, 1, "wave cleared flag");
}

/* ======================================================================== */
/* Test: Different rotations produce different spawn positions               */
/* ======================================================================== */

static void test_rotation_variety(void) {
    printf("test_rotation_variety:\n");

    /* Two seeds should get different rotations (probabilistic but very likely) */
    FcState a, b;
    fc_init(&a);
    fc_init(&b);
    fc_reset(&a, 1);
    fc_reset(&b, 99999);

    /* With different seeds, rotations should differ (15 options) */
    /* Not guaranteed but extremely likely */
    int same_rotation = (a.rotation_id == b.rotation_id);
    /* Just check that rotations are in valid range */
    ASSERT_GE(a.rotation_id, 0, "rotation A >= 0");
    ASSERT_LE(a.rotation_id, 14, "rotation A <= 14");
    ASSERT_GE(b.rotation_id, 0, "rotation B >= 0");
    ASSERT_LE(b.rotation_id, 14, "rotation B <= 14");

    /* Check that different rotations for wave 5 (3 NPCs) produce different dirs */
    int dir_r0 = fc_wave_spawn_dir(5, 0, 0);
    int dir_r6 = fc_wave_spawn_dir(5, 6, 0);
    /* These should be different (from TOML data, rot 1 and rot 7 differ) */
    /* rot 1 = north_west, rot 7 = south_west for wave 5 NPC 0 */
    ASSERT_EQ(fc_wave_spawn_dir(5, 0, 0), SPAWN_NORTH_WEST, "wave 5 rot 0 npc 0 = NW");
    ASSERT_EQ(fc_wave_spawn_dir(5, 6, 0), SPAWN_SOUTH_WEST, "wave 5 rot 6 npc 0 = SW");
}

/* ======================================================================== */
/* Test: Multi-wave determinism                                              */
/* ======================================================================== */

static void test_multi_wave_determinism(void) {
    printf("test_multi_wave_determinism:\n");

    FcState a, b;
    fc_init(&a);
    fc_init(&b);
    fc_reset(&a, 77777);
    fc_reset(&b, 77777);

    /* Run 10 waves with scripted fast-kill + some ticks between */
    for (int w = 0; w < 10; w++) {
        /* Run a few ticks of combat */
        for (int t = 0; t < 5; t++) {
            int act[5] = {0, 1, 0, 0, 0};
            fc_step(&a, act);
            fc_step(&b, act);
        }
        /* Kill all NPCs */
        kill_all_npcs(&a);
        kill_all_npcs(&b);
        step_idle(&a);
        step_idle(&b);
    }

    uint32_t ha = fc_state_hash(&a);
    uint32_t hb = fc_state_hash(&b);
    ASSERT_EQ(ha, hb, "multi-wave deterministic");
    ASSERT_EQ(a.current_wave, b.current_wave, "same wave");
}

/* ======================================================================== */
/* Test: PR6 golden trace                                                    */
/* ======================================================================== */

static void test_pr6_golden_trace(void) {
    printf("test_pr6_golden_trace:\n");

    const uint32_t SEED = 66666;

    FcState state;
    FcActionTrace trace;
    fc_init(&state);
    fc_trace_init(&trace);
    fc_reset(&state, SEED);
    fc_trace_reset(&trace, SEED);

    /* Run through waves 1-5 with scripted kill-and-fight pattern */
    int ticks = 0;
    for (int w = 0; w < 5; w++) {
        /* 3 ticks of combat */
        for (int t = 0; t < 3; t++) {
            int act[5] = {0, 1, FC_PRAYER_MELEE, 0, 0};
            fc_step(&state, act);
            uint32_t h = fc_state_hash(&state);
            fc_trace_record(&trace, act, FC_NUM_ACTION_HEADS, h);
            ticks++;
        }
        /* Kill wave */
        kill_all_npcs(&state);
        int idle[5] = {0,0,0,0,0};
        fc_step(&state, idle);
        uint32_t h = fc_state_hash(&state);
        fc_trace_record(&trace, idle, FC_NUM_ACTION_HEADS, h);
        ticks++;
    }

    /* Replay */
    FcState replay;
    fc_init(&replay);
    fc_reset(&replay, trace.seed);

    int replay_ok = 1;
    int tick_idx = 0;
    for (int w = 0; w < 5; w++) {
        for (int t = 0; t < 3; t++) {
            fc_step(&replay, &trace.actions[tick_idx * FC_NUM_ACTION_HEADS]);
            uint32_t h = fc_state_hash(&replay);
            if (h != trace.hashes[tick_idx]) {
                printf("    Diverged at tick %d: 0x%08x vs 0x%08x\n",
                       tick_idx, h, trace.hashes[tick_idx]);
                replay_ok = 0;
                break;
            }
            tick_idx++;
        }
        if (!replay_ok) break;
        kill_all_npcs(&replay);
        int idle[5] = {0,0,0,0,0};
        fc_step(&replay, &trace.actions[tick_idx * FC_NUM_ACTION_HEADS]);
        uint32_t h = fc_state_hash(&replay);
        if (h != trace.hashes[tick_idx]) {
            printf("    Diverged at wave-advance tick %d\n", tick_idx);
            replay_ok = 0;
        }
        tick_idx++;
    }
    ASSERT(replay_ok, "PR6 golden trace replay matches");

    printf("    PR6 golden trace: seed %u, %d ticks, final hash 0x%08x\n",
           SEED, ticks, trace.hashes[ticks - 1]);

    fc_trace_destroy(&trace);
}

/* ======================================================================== */
/* Test: NPC count per wave for mid/late waves                               */
/* ======================================================================== */

static void test_late_wave_npcs(void) {
    printf("test_late_wave_npcs:\n");

    /* Wave 12: 4 NPCs (Tz-Kih, Tz-Kih, Tz-Kek, Tok-Xil) */
    const FcWaveEntry* w12 = fc_wave_get(12);
    ASSERT_EQ(w12->num_spawns, 4, "wave 12 has 4 NPCs");
    ASSERT_EQ(w12->npc_types[3], NPC_TOK_XIL, "wave 12 NPC 3 is Tok-Xil");

    /* Wave 43: 5 NPCs (Tz-Kih×2, Tz-Kek, Tok-Xil, Ket-Zek) */
    const FcWaveEntry* w43 = fc_wave_get(43);
    ASSERT_EQ(w43->num_spawns, 5, "wave 43 has 5 NPCs");
    ASSERT_EQ(w43->npc_types[4], NPC_KET_ZEK, "wave 43 last is Ket-Zek");

    /* Wave 62: 2 NPCs (Ket-Zek × 2) */
    const FcWaveEntry* w62 = fc_wave_get(62);
    ASSERT_EQ(w62->num_spawns, 2, "wave 62 has 2 NPCs");
    ASSERT_EQ(w62->npc_types[0], NPC_KET_ZEK, "wave 62 first is Ket-Zek");
    ASSERT_EQ(w62->npc_types[1], NPC_KET_ZEK, "wave 62 second is Ket-Zek");
}

/* ======================================================================== */
/* Main                                                                      */
/* ======================================================================== */

int main(void) {
    printf("=== PR 6 Tests: Wave System (63 Waves + Jad) ===\n\n");

    test_wave_table();
    test_wave1_reset();
    test_wave_progression();
    test_tz_kek_split_wave_clear();
    test_reach_jad();
    test_jad_healer_spawn();
    test_cave_complete();
    test_rotation_variety();
    test_late_wave_npcs();
    test_multi_wave_determinism();
    test_pr6_golden_trace();

    printf("\n=== Results: %d/%d passed ===\n", tests_passed, tests_run);
    return (tests_passed == tests_run) ? 0 : 1;
}
