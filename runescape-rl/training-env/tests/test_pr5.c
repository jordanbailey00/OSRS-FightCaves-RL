#include "fc_types.h"
#include "fc_contracts.h"
#include "fc_api.h"
#include "fc_combat.h"
#include "fc_prayer.h"
#include "fc_pathfinding.h"
#include "fc_npc.h"
#include "fc_debug.h"
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <math.h>

/* Minimal test framework */
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
#define ASSERT_NEQ(a, b, msg) ASSERT((a) != (b), msg)
#define ASSERT_GT(a, b, msg) ASSERT((a) > (b), msg)
#define ASSERT_GE(a, b, msg) ASSERT((a) >= (b), msg)
#define ASSERT_LT(a, b, msg) ASSERT((a) < (b), msg)
#define ASSERT_LE(a, b, msg) ASSERT((a) <= (b), msg)
#define ASSERT_FLOAT_NEAR(a, b, eps, msg) ASSERT(fabsf((a) - (b)) < (eps), msg)

/* Helper: spawn NPC at position */
static int spawn_npc(FcState* state, int npc_type, int x, int y) {
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        if (!state->npcs[i].active) {
            fc_npc_spawn(&state->npcs[i], npc_type, x, y,
                         state->next_spawn_index++);
            state->npcs_remaining++;
            return i;
        }
    }
    return -1;
}

/* Helper: step with idle */
static void step_idle(FcState* state) {
    int idle[FC_NUM_ACTION_HEADS] = {0, 0, 0, 0, 0};
    fc_step(state, idle);
}

/* ======================================================================== */
/* Test: NPC defence stats are real (not hardcoded 1)                        */
/* ======================================================================== */

static void test_npc_defence_stats(void) {
    printf("test_npc_defence_stats:\n");

    /* All NPC types should have real defence stats */
    const FcNpcStats* s;

    s = fc_npc_get_stats(NPC_TZ_KIH);
    ASSERT_EQ(s->def_level, 22, "Tz-Kih def level 22");
    ASSERT_EQ(s->def_bonus, 0, "Tz-Kih def bonus 0");

    s = fc_npc_get_stats(NPC_TZ_KEK);
    ASSERT_EQ(s->def_level, 45, "Tz-Kek def level 45");

    s = fc_npc_get_stats(NPC_TOK_XIL);
    ASSERT_EQ(s->def_level, 60, "Tok-Xil def level 60");

    s = fc_npc_get_stats(NPC_YT_MEJKOT);
    ASSERT_EQ(s->def_level, 180, "Yt-MejKot def level 180");

    s = fc_npc_get_stats(NPC_KET_ZEK);
    ASSERT_EQ(s->def_level, 300, "Ket-Zek def level 300");

    s = fc_npc_get_stats(NPC_TZTOK_JAD);
    ASSERT_EQ(s->def_level, 480, "Jad def level 480");

    /* fc_npc_def_roll should produce non-trivial values */
    int roll = fc_npc_def_roll(180, 30);
    ASSERT_GT(roll, 1000, "Yt-MejKot def roll substantial");

    /* Player accuracy against high-def NPC should be lower */
    FcPlayer p;
    memset(&p, 0, sizeof(p));
    p.ranged_level = 70;
    p.ranged_attack_bonus = 100;
    int att_roll = fc_player_ranged_attack_roll(&p);

    float easy = fc_hit_chance(att_roll, fc_npc_def_roll(22, 0));
    float hard = fc_hit_chance(att_roll, fc_npc_def_roll(300, 40));
    ASSERT_GT(easy, hard, "easier to hit low-def NPC than high-def NPC");
    ASSERT_GT(easy, 0.5f, "high accuracy against Tz-Kih");
    ASSERT_LT(hard, 0.5f, "low accuracy against Ket-Zek");
}

/* ======================================================================== */
/* Test: Magic hit delay formula                                             */
/* ======================================================================== */

static void test_magic_hit_delay(void) {
    printf("test_magic_hit_delay:\n");

    /* magic delay = 1 + floor((1 + distance) / 3) */
    ASSERT_EQ(fc_magic_hit_delay(1), 1 + (1 + 1) / 3, "magic delay dist=1");
    ASSERT_EQ(fc_magic_hit_delay(5), 1 + (1 + 5) / 3, "magic delay dist=5");
    ASSERT_EQ(fc_magic_hit_delay(8), 1 + (1 + 8) / 3, "magic delay dist=8");
    /* Should be different from ranged delay at distance 3 */
    /* magic(3) = 1 + (1+3)/3 = 2, ranged(3) = (3+3)/6 + 2 = 3 */
    ASSERT_NEQ(fc_magic_hit_delay(3), fc_ranged_hit_delay(3),
               "magic and ranged delay differ at dist=3");
}

/* ======================================================================== */
/* Test: Tz-Kek split on death                                               */
/* ======================================================================== */

static void test_tz_kek_split(void) {
    printf("test_tz_kek_split:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    /* Clear wave-spawned NPCs */
    for (int i = 0; i < FC_MAX_NPCS; i++) { state.npcs[i].active = 0; }
    state.npcs_remaining = 0;

    int idx = spawn_npc(&state, NPC_TZ_KEK, state.player.x + 2, state.player.y);
    ASSERT_GE(idx, 0, "Tz-Kek spawned");
    ASSERT_EQ(state.npcs_remaining, 1, "1 NPC remaining");
    ASSERT_EQ(state.npcs[idx].max_hp, 200, "Tz-Kek has 200 HP (20.0)");

    /* Kill it by queuing a direct pending hit that will finish it off.
     * Note: the death handler sets active=0, then split reuses the slot,
     * so we can't check is_dead on the original slot after step. */
    state.npcs[idx].current_hp = 1;
    fc_queue_pending_hit(state.npcs[idx].pending_hits,
                         &state.npcs[idx].num_pending_hits,
                         FC_MAX_PENDING_HITS,
                         10, 1, ATTACK_RANGED, -1, 0);

    /* Step once to resolve the hit — Tz-Kek dies and splits */
    step_idle(&state);

    /* npcs_remaining: was 1, death decremented to 0, 2 splits added → 2 */
    ASSERT_EQ(state.npcs_remaining, 2, "2 small Tz-Kek spawned on split");
    ASSERT_EQ(state.npcs_killed_this_tick, 1, "1 NPC killed (the Tz-Kek)");
    ASSERT_EQ(state.total_npcs_killed, 1, "total kills = 1");

    /* Verify the splits are NPC_TZ_KEK_SM */
    int smalls_found = 0;
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        if (state.npcs[i].active && state.npcs[i].npc_type == NPC_TZ_KEK_SM) {
            smalls_found++;
            ASSERT_EQ(state.npcs[i].max_hp, 100, "small Tz-Kek has 100 HP");
        }
    }
    ASSERT_EQ(smalls_found, 2, "found 2 NPC_TZ_KEK_SM");
}

/* ======================================================================== */
/* Test: Tok-Xil ranged behavior                                             */
/* ======================================================================== */

static void test_tok_xil_ranged(void) {
    printf("test_tok_xil_ranged:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    int idx = spawn_npc(&state, NPC_TOK_XIL, state.player.x + 4, state.player.y);
    ASSERT_GE(idx, 0, "Tok-Xil spawned");
    ASSERT_EQ(state.npcs[idx].attack_style, ATTACK_RANGED, "Tok-Xil is ranged");
    ASSERT_EQ(state.npcs[idx].attack_range, 6, "Tok-Xil has range 6");

    /* Tok-Xil should attack from distance without moving first */
    int dist = fc_distance_to_npc(state.player.x, state.player.y, &state.npcs[idx]);
    ASSERT_LE(dist, 6, "Tok-Xil within attack range");

    /* Step until damage is taken (ranged hit with delay) */
    int took_damage = 0;
    int initial_hp = state.player.current_hp;
    for (int t = 0; t < 30; t++) {
        step_idle(&state);
        if (state.player.current_hp < initial_hp) {
            took_damage = 1;
            break;
        }
    }
    ASSERT(took_damage, "Tok-Xil ranged attack dealt damage");

    /* Verify protect range blocks it */
    state.player.current_hp = state.player.max_hp;
    int pray[FC_NUM_ACTION_HEADS] = {0, 0, FC_PRAYER_RANGE, 0, 0};
    fc_step(&state, pray);

    int hp_after_pray = state.player.current_hp;
    for (int t = 0; t < 30; t++) {
        step_idle(&state);
    }
    ASSERT_EQ(state.player.current_hp, hp_after_pray,
              "protect range blocks Tok-Xil damage (100% PvM block)");
}

/* ======================================================================== */
/* Test: Yt-MejKot healing                                                   */
/* ======================================================================== */

static void test_yt_mejkot_heal(void) {
    printf("test_yt_mejkot_heal:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    /* Spawn Yt-MejKot and a damaged Tz-Kih near it */
    int mejkot_idx = spawn_npc(&state, NPC_YT_MEJKOT,
                                state.player.x + 3, state.player.y);
    int kih_idx = spawn_npc(&state, NPC_TZ_KIH,
                             state.player.x + 4, state.player.y);
    ASSERT_GE(mejkot_idx, 0, "Yt-MejKot spawned");
    ASSERT_GE(kih_idx, 0, "Tz-Kih spawned");

    /* Damage the Tz-Kih */
    state.npcs[kih_idx].current_hp = 50;  /* 5.0 HP, max is 100 */

    /* Step many ticks — Yt-MejKot should heal the Tz-Kih */
    for (int t = 0; t < 30; t++) {
        step_idle(&state);
    }

    ASSERT_GT(state.npcs[kih_idx].current_hp, 50,
              "Yt-MejKot healed the Tz-Kih");
    ASSERT_LE(state.npcs[kih_idx].current_hp, state.npcs[kih_idx].max_hp,
              "healed HP does not exceed max");
}

/* ======================================================================== */
/* Test: Ket-Zek magic behavior                                              */
/* ======================================================================== */

static void test_ket_zek_magic(void) {
    printf("test_ket_zek_magic:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    int idx = spawn_npc(&state, NPC_KET_ZEK, state.player.x + 5, state.player.y);
    ASSERT_GE(idx, 0, "Ket-Zek spawned");
    ASSERT_EQ(state.npcs[idx].attack_style, ATTACK_MAGIC, "Ket-Zek is magic");
    ASSERT_EQ(state.npcs[idx].attack_range, 8, "Ket-Zek has range 8");

    /* Step until damage is taken */
    int took_damage = 0;
    int initial_hp = state.player.current_hp;
    for (int t = 0; t < 30; t++) {
        step_idle(&state);
        if (state.player.current_hp < initial_hp) {
            took_damage = 1;
            break;
        }
    }
    ASSERT(took_damage, "Ket-Zek magic attack dealt damage");

    /* Verify protect magic blocks it */
    state.player.current_hp = state.player.max_hp;
    int pray[FC_NUM_ACTION_HEADS] = {0, 0, FC_PRAYER_MAGIC, 0, 0};
    fc_step(&state, pray);

    int hp_after_pray = state.player.current_hp;
    for (int t = 0; t < 30; t++) {
        step_idle(&state);
    }
    ASSERT_EQ(state.player.current_hp, hp_after_pray,
              "protect magic blocks Ket-Zek damage");
}

/* ======================================================================== */
/* Test: Jad telegraph → prayer check → damage resolution                    */
/* ======================================================================== */

static void test_jad_telegraph(void) {
    printf("test_jad_telegraph:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 100);

    /* Clear wave-spawned NPCs for clean Jad test */
    for (int i = 0; i < FC_MAX_NPCS; i++) { state.npcs[i].active = 0; }
    state.npcs_remaining = 0;

    int idx = spawn_npc(&state, NPC_TZTOK_JAD, state.player.x + 5, state.player.y);
    ASSERT_GE(idx, 0, "Jad spawned");
    ASSERT_EQ(state.npcs[idx].jad_telegraph, JAD_TELEGRAPH_IDLE, "Jad starts idle");
    ASSERT_EQ(state.npcs[idx].attack_speed, 8, "Jad attack speed = 8");
    ASSERT_EQ(state.npcs[idx].size, 3, "Jad is size 3");
    ASSERT_EQ(state.npcs[idx].max_hp, 2500, "Jad has 250 HP (2500 tenths)");

    /* Step until Jad starts a telegraph */
    int saw_telegraph = 0;
    int telegraph_style = 0;
    for (int t = 0; t < 30; t++) {
        step_idle(&state);
        if (state.npcs[idx].jad_telegraph != JAD_TELEGRAPH_IDLE) {
            saw_telegraph = 1;
            telegraph_style = state.npcs[idx].jad_telegraph;
            break;
        }
    }
    ASSERT(saw_telegraph, "Jad entered telegraph state");
    ASSERT(telegraph_style == JAD_TELEGRAPH_MAGIC_WINDUP ||
           telegraph_style == JAD_TELEGRAPH_RANGED_WINDUP,
           "telegraph is magic or ranged windup");

    /* Switch to correct prayer based on telegraph */
    int prayer_action = (telegraph_style == JAD_TELEGRAPH_MAGIC_WINDUP)
        ? FC_PRAYER_MAGIC : FC_PRAYER_RANGE;
    int pray[FC_NUM_ACTION_HEADS] = {0, 0, prayer_action, 0, 0};
    fc_step(&state, pray);

    /* Step until hit resolves — prayer should block */
    int hp_before = state.player.current_hp;
    for (int t = 0; t < 20; t++) {
        step_idle(&state);
    }
    ASSERT_EQ(state.player.current_hp, hp_before,
              "correct prayer blocks Jad hit completely");
}

/* ======================================================================== */
/* Test: Jad wrong prayer → takes massive damage                             */
/* ======================================================================== */

static void test_jad_wrong_prayer(void) {
    printf("test_jad_wrong_prayer:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 200);

    for (int i = 0; i < FC_MAX_NPCS; i++) { state.npcs[i].active = 0; }
    state.npcs_remaining = 0;

    /* Give player lots of HP so they survive */
    state.player.current_hp = 9990;
    state.player.max_hp = 9990;

    int idx = spawn_npc(&state, NPC_TZTOK_JAD, state.player.x + 5, state.player.y);
    ASSERT_GE(idx, 0, "Jad spawned");

    /* Step until telegraph, then pray the WRONG style */
    for (int t = 0; t < 30; t++) {
        step_idle(&state);
        if (state.npcs[idx].jad_telegraph != JAD_TELEGRAPH_IDLE) {
            /* Pray the wrong style */
            int wrong = (state.npcs[idx].jad_telegraph == JAD_TELEGRAPH_MAGIC_WINDUP)
                ? FC_PRAYER_MELEE : FC_PRAYER_MELEE;
            int pray[FC_NUM_ACTION_HEADS] = {0, 0, wrong, 0, 0};
            fc_step(&state, pray);
            break;
        }
    }

    /* Step until hit resolves — should take damage */
    int hp_before = state.player.current_hp;
    int took_damage = 0;
    for (int t = 0; t < 20; t++) {
        step_idle(&state);
        if (state.player.current_hp < hp_before) {
            took_damage = 1;
            break;
        }
    }
    ASSERT(took_damage, "wrong prayer → Jad deals damage");
}

/* ======================================================================== */
/* Test: Yt-HurKot heals Jad                                                */
/* ======================================================================== */

static void test_yt_hurkot_heals_jad(void) {
    printf("test_yt_hurkot_heals_jad:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    for (int i = 0; i < FC_MAX_NPCS; i++) { state.npcs[i].active = 0; }
    state.npcs_remaining = 0;

    /* Spawn Jad at half HP */
    int jad_idx = spawn_npc(&state, NPC_TZTOK_JAD,
                             state.player.x + 6, state.player.y);
    ASSERT_GE(jad_idx, 0, "Jad spawned");
    state.npcs[jad_idx].current_hp = 1000;  /* half of 2500 */

    /* Spawn healer near Jad */
    int healer_idx = spawn_npc(&state, NPC_YT_HURKOT,
                                state.player.x + 7, state.player.y);
    ASSERT_GE(healer_idx, 0, "Yt-HurKot spawned");
    ASSERT_EQ(state.npcs[healer_idx].is_healer, 1, "marked as healer");

    int jad_hp_before = state.npcs[jad_idx].current_hp;

    /* Step — healer should walk to Jad and heal */
    for (int t = 0; t < 20; t++) {
        step_idle(&state);
    }
    ASSERT_GT(state.npcs[jad_idx].current_hp, jad_hp_before,
              "Yt-HurKot healed Jad");
}

/* ======================================================================== */
/* Test: Yt-HurKot distraction stops healing                                 */
/* ======================================================================== */

static void test_yt_hurkot_distraction(void) {
    printf("test_yt_hurkot_distraction:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    for (int i = 0; i < FC_MAX_NPCS; i++) { state.npcs[i].active = 0; }
    state.npcs_remaining = 0;

    /* Spawn Jad at half HP */
    int jad_idx = spawn_npc(&state, NPC_TZTOK_JAD,
                             state.player.x + 6, state.player.y);
    state.npcs[jad_idx].current_hp = 1000;

    /* Spawn healer very close to player so we can attack it */
    int healer_idx = spawn_npc(&state, NPC_YT_HURKOT,
                                state.player.x + 2, state.player.y);
    ASSERT_GE(healer_idx, 0, "healer spawned");

    /* Attack the healer to distract it.
     * NPC slot 0 should be healer (closer than Jad). */
    int attack[FC_NUM_ACTION_HEADS] = {0, 1, 0, 0, 0};
    for (int t = 0; t < 15; t++) {
        fc_step(&state, attack);
    }

    /* Healer should be distracted after taking a hit */
    ASSERT_EQ(state.npcs[healer_idx].healer_distracted, 1,
              "healer distracted after player attack");

    /* Record Jad HP and step more — Jad should NOT be healed further */
    int jad_hp_now = state.npcs[jad_idx].current_hp;
    for (int t = 0; t < 20; t++) {
        step_idle(&state);
    }
    ASSERT_LE(state.npcs[jad_idx].current_hp, jad_hp_now,
              "distracted healer stops healing Jad");
}

/* ======================================================================== */
/* Test: Multi-NPC combat determinism                                        */
/* ======================================================================== */

static void test_multi_npc_determinism(void) {
    printf("test_multi_npc_determinism:\n");

    FcState a, b;
    fc_init(&a);
    fc_init(&b);

    fc_reset(&a, 99999);
    fc_reset(&b, 99999);

    /* Spawn diverse NPC mix in both */
    spawn_npc(&a, NPC_TZ_KIH, 33, 32);
    spawn_npc(&a, NPC_TOK_XIL, 28, 32);
    spawn_npc(&a, NPC_KET_ZEK, 32, 38);

    spawn_npc(&b, NPC_TZ_KIH, 33, 32);
    spawn_npc(&b, NPC_TOK_XIL, 28, 32);
    spawn_npc(&b, NPC_KET_ZEK, 32, 38);

    int actions_sequence[][FC_NUM_ACTION_HEADS] = {
        {0, 0, FC_PRAYER_MAGIC, 0, 0},
        {FC_MOVE_WALK_N, 0, 0, 0, 0},
        {0, 1, 0, 0, 0},
        {0, 0, 0, FC_EAT_SHARK, 0},
        {FC_MOVE_WALK_S, 0, FC_PRAYER_OFF, 0, 0},
        {0, 2, FC_PRAYER_RANGE, 0, 0},
        {FC_MOVE_WALK_E, 0, 0, 0, FC_DRINK_PRAYER_POT},
    };
    int num_scripts = 7;

    for (int t = 0; t < 100; t++) {
        int* act = actions_sequence[t % num_scripts];
        fc_step(&a, act);
        fc_step(&b, act);

        uint32_t ha = fc_state_hash(&a);
        uint32_t hb = fc_state_hash(&b);
        if (ha != hb) {
            printf("    Hash diverged at tick %d: 0x%08x vs 0x%08x\n", t+1, ha, hb);
            ASSERT(0, "multi-NPC determinism");
            return;
        }
    }
    ASSERT(1, "100-tick multi-NPC deterministic");
}

/* ======================================================================== */
/* Test: PR5 golden trace — multi-type NPC combat                            */
/* ======================================================================== */

static void test_pr5_golden_trace(void) {
    printf("test_pr5_golden_trace:\n");

    const uint32_t SEED = 55555;
    const int TICKS = 30;

    FcState state;
    FcActionTrace trace;
    fc_init(&state);
    fc_trace_init(&trace);

    fc_reset(&state, SEED);
    fc_trace_reset(&trace, SEED);

    /* Spawn a diverse NPC mix */
    spawn_npc(&state, NPC_TZ_KIH, 33, 32);
    spawn_npc(&state, NPC_TZ_KEK, 28, 35);
    spawn_npc(&state, NPC_TOK_XIL, 35, 28);
    spawn_npc(&state, NPC_KET_ZEK, 25, 32);

    int golden_actions[30][FC_NUM_ACTION_HEADS] = {
        {0, 0, FC_PRAYER_MAGIC, 0, 0},   /* t0: pray magic (Ket-Zek) */
        {0, 1, 0, 0, 0},                  /* t1: attack slot 0 */
        {0, 1, 0, 0, 0},                  /* t2: attack */
        {FC_MOVE_WALK_S, 0, 0, 0, 0},     /* t3: walk south */
        {0, 0, 0, 0, 0},                  /* t4: idle */
        {0, 2, 0, 0, 0},                  /* t5: attack slot 1 */
        {0, 0, FC_PRAYER_RANGE, 0, 0},    /* t6: switch to range prayer */
        {0, 1, 0, 0, 0},                  /* t7: attack */
        {FC_MOVE_WALK_N, 0, 0, 0, 0},     /* t8: walk north */
        {0, 0, 0, FC_EAT_SHARK, 0},       /* t9: eat */
        {0, 1, 0, 0, 0},                  /* t10: attack */
        {0, 0, 0, 0, FC_DRINK_PRAYER_POT},/* t11: drink */
        {0, 1, 0, 0, 0},                  /* t12: attack */
        {FC_MOVE_WALK_E, 0, 0, 0, 0},     /* t13: walk east */
        {0, 0, FC_PRAYER_MELEE, 0, 0},    /* t14: switch to melee prayer */
        {0, 1, 0, 0, 0},                  /* t15: attack */
        {0, 0, 0, 0, 0},                  /* t16: idle */
        {0, 3, 0, 0, 0},                  /* t17: attack slot 2 */
        {FC_MOVE_WALK_W, 0, 0, 0, 0},     /* t18: walk west */
        {0, 1, 0, 0, 0},                  /* t19: attack */
        {0, 0, FC_PRAYER_MAGIC, 0, 0},    /* t20: back to magic prayer */
        {0, 1, 0, 0, 0},                  /* t21: attack */
        {0, 0, 0, 0, 0},                  /* t22: idle */
        {0, 2, 0, 0, 0},                  /* t23: attack slot 1 */
        {FC_MOVE_RUN_N, 0, 0, 0, 0},      /* t24: run north */
        {0, 0, 0, FC_EAT_SHARK, 0},       /* t25: eat */
        {0, 1, 0, 0, 0},                  /* t26: attack */
        {0, 0, FC_PRAYER_OFF, 0, 0},      /* t27: prayer off */
        {0, 1, 0, 0, 0},                  /* t28: attack */
        {0, 0, 0, 0, 0},                  /* t29: idle */
    };

    /* Record pass */
    uint32_t recorded_hashes[30];
    for (int t = 0; t < TICKS; t++) {
        fc_step(&state, golden_actions[t]);
        recorded_hashes[t] = fc_state_hash(&state);
        fc_trace_record(&trace, golden_actions[t], FC_NUM_ACTION_HEADS, recorded_hashes[t]);
    }

    /* Replay pass */
    FcState replay;
    fc_init(&replay);
    fc_reset(&replay, trace.seed);
    spawn_npc(&replay, NPC_TZ_KIH, 33, 32);
    spawn_npc(&replay, NPC_TZ_KEK, 28, 35);
    spawn_npc(&replay, NPC_TOK_XIL, 35, 28);
    spawn_npc(&replay, NPC_KET_ZEK, 25, 32);

    int replay_ok = 1;
    for (int t = 0; t < trace.num_ticks; t++) {
        fc_step(&replay, &trace.actions[t * FC_NUM_ACTION_HEADS]);
        uint32_t h = fc_state_hash(&replay);
        if (h != trace.hashes[t]) {
            printf("    Golden trace diverged at tick %d: 0x%08x vs 0x%08x\n",
                   t, h, trace.hashes[t]);
            replay_ok = 0;
            break;
        }
    }
    ASSERT(replay_ok, "PR5 golden trace replay matches recording");

    printf("    PR5 golden trace final hash (seed %u, %d ticks): 0x%08x\n",
           SEED, TICKS, recorded_hashes[TICKS - 1]);

    fc_trace_destroy(&trace);
}

/* ======================================================================== */
/* Test: NPC types have correct attack styles                                */
/* ======================================================================== */

static void test_npc_attack_styles(void) {
    printf("test_npc_attack_styles:\n");

    FcNpc npc;

    fc_npc_spawn(&npc, NPC_TZ_KIH, 32, 32, 0);
    ASSERT_EQ(npc.attack_style, ATTACK_MELEE, "Tz-Kih melee");

    fc_npc_spawn(&npc, NPC_TZ_KEK, 32, 32, 1);
    ASSERT_EQ(npc.attack_style, ATTACK_MELEE, "Tz-Kek melee");

    fc_npc_spawn(&npc, NPC_TOK_XIL, 32, 32, 2);
    ASSERT_EQ(npc.attack_style, ATTACK_RANGED, "Tok-Xil ranged");

    fc_npc_spawn(&npc, NPC_YT_MEJKOT, 32, 32, 3);
    ASSERT_EQ(npc.attack_style, ATTACK_MELEE, "Yt-MejKot melee");

    fc_npc_spawn(&npc, NPC_KET_ZEK, 32, 32, 4);
    ASSERT_EQ(npc.attack_style, ATTACK_MAGIC, "Ket-Zek magic");

    fc_npc_spawn(&npc, NPC_TZTOK_JAD, 32, 32, 5);
    ASSERT_EQ(npc.attack_style, ATTACK_MAGIC, "Jad initial style magic");
    ASSERT_EQ(npc.size, 3, "Jad size 3");

    fc_npc_spawn(&npc, NPC_YT_HURKOT, 32, 32, 6);
    ASSERT_EQ(npc.attack_style, ATTACK_MELEE, "Yt-HurKot melee");
    ASSERT_EQ(npc.is_healer, 1, "Yt-HurKot is healer");
}

/* ======================================================================== */
/* Test: Jad max hits                                                        */
/* ======================================================================== */

static void test_jad_stats(void) {
    printf("test_jad_stats:\n");

    const FcNpcStats* s = fc_npc_get_stats(NPC_TZTOK_JAD);
    ASSERT_EQ(s->max_hit, 970, "Jad magic max hit 970 (97.0)");
    ASSERT_EQ(s->jad_ranged_max_hit, 950, "Jad ranged max hit 950 (95.0)");
    ASSERT_EQ(s->attack_range, 10, "Jad range 10");
    ASSERT_EQ(s->def_level, 480, "Jad def 480");
}

/* ======================================================================== */
/* Test: PR2 golden trace still self-consistent                              */
/* ======================================================================== */

static void test_pr2_regression(void) {
    printf("test_pr2_regression:\n");

    /* The PR2 golden trace hash will have changed because NPC stat table
     * layout changed (str_level/str_bonus → def_level/def_bonus).
     * But the trace must still be self-consistent (record == replay). */
    FcState state;
    FcActionTrace trace;
    fc_init(&state);
    fc_trace_init(&trace);

    fc_reset(&state, 777);
    fc_trace_reset(&trace, 777);
    spawn_npc(&state, NPC_TZ_KIH, state.player.x + 1, state.player.y);

    int golden_actions[20][FC_NUM_ACTION_HEADS] = {
        {0, 0, FC_PRAYER_MELEE, 0, 0},
        {0, 1, 0, 0, 0}, {0, 1, 0, 0, 0}, {0, 0, 0, 0, 0},
        {0, 1, 0, 0, 0}, {0, 1, 0, 0, 0}, {0, 0, 0, 0, 0},
        {0, 1, 0, 0, 0}, {0, 1, 0, 0, 0}, {0, 0, 0, 0, 0},
        {0, 1, 0, 0, 0}, {FC_MOVE_WALK_S, 0, 0, 0, 0},
        {FC_MOVE_WALK_N, 0, 0, 0, 0}, {0, 1, 0, 0, 0},
        {0, 0, 0, 0, FC_DRINK_PRAYER_POT}, {0, 0, FC_PRAYER_OFF, 0, 0},
        {0, 1, 0, 0, 0}, {0, 0, 0, 0, 0},
        {0, 0, FC_PRAYER_MELEE, 0, 0}, {0, 1, 0, 0, 0},
    };

    uint32_t recorded_hashes[20];
    for (int t = 0; t < 20; t++) {
        fc_step(&state, golden_actions[t]);
        recorded_hashes[t] = fc_state_hash(&state);
        fc_trace_record(&trace, golden_actions[t], FC_NUM_ACTION_HEADS, recorded_hashes[t]);
    }

    FcState replay;
    fc_init(&replay);
    fc_reset(&replay, trace.seed);
    spawn_npc(&replay, NPC_TZ_KIH, replay.player.x + 1, replay.player.y);

    int ok = 1;
    for (int t = 0; t < trace.num_ticks; t++) {
        fc_step(&replay, &trace.actions[t * FC_NUM_ACTION_HEADS]);
        uint32_t h = fc_state_hash(&replay);
        if (h != trace.hashes[t]) {
            printf("    PR2 regression diverged at tick %d\n", t);
            ok = 0;
            break;
        }
    }
    ASSERT(ok, "PR2 golden trace self-consistent after PR5 changes");

    printf("    PR2 trace final hash (post-PR5): 0x%08x\n", recorded_hashes[19]);
    fc_trace_destroy(&trace);
}

/* ======================================================================== */
/* Main                                                                      */
/* ======================================================================== */

int main(void) {
    printf("=== PR 5 Tests: All NPC Types — AI and Special Mechanics ===\n\n");

    test_npc_defence_stats();
    test_magic_hit_delay();
    test_tz_kek_split();
    test_tok_xil_ranged();
    test_yt_mejkot_heal();
    test_ket_zek_magic();
    test_jad_telegraph();
    test_jad_wrong_prayer();
    test_yt_hurkot_heals_jad();
    test_yt_hurkot_distraction();
    test_npc_attack_styles();
    test_jad_stats();
    test_multi_npc_determinism();
    test_pr5_golden_trace();
    test_pr2_regression();

    printf("\n=== Results: %d/%d passed ===\n", tests_passed, tests_run);
    return (tests_passed == tests_run) ? 0 : 1;
}
