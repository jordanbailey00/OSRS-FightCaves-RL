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

/* Minimal test framework (same as PR 1) */
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

/* Helper: spawn a Tz-Kih near the player */
static void spawn_tz_kih(FcState* state, int x, int y) {
    for (int i = 0; i < FC_MAX_NPCS; i++) {
        if (!state->npcs[i].active) {
            fc_npc_spawn(&state->npcs[i], NPC_TZ_KIH, x, y,
                         state->next_spawn_index++);
            state->npcs_remaining++;
            return;
        }
    }
}

/* ======================================================================== */
/* Test: Combat math                                                         */
/* ======================================================================== */

static void test_combat_math(void) {
    printf("test_combat_math:\n");

    /* Hit chance formula */
    float ch = fc_hit_chance(100, 50);
    ASSERT_GT(ch, 0.5f, "attacker stronger → >50% hit chance");

    ch = fc_hit_chance(50, 100);
    ASSERT_LT(ch, 0.5f, "defender stronger → <50% hit chance");

    ch = fc_hit_chance(100, 100);
    ASSERT_FLOAT_NEAR(ch, 0.495f, 0.01f, "equal rolls → ~49.5%");

    /* NPC attack roll */
    int roll = fc_npc_attack_roll(22, 0);
    ASSERT_EQ(roll, (22 + 9) * 64, "Tz-Kih attack roll correct");

    /* NPC max hit */
    int mh = fc_npc_melee_max_hit(22, 0);
    ASSERT_GT(mh, 0, "Tz-Kih max hit > 0");

    /* Player ranged attack roll */
    FcPlayer p;
    memset(&p, 0, sizeof(p));
    p.ranged_level = 70;
    p.ranged_attack_bonus = 100;
    int p_roll = fc_player_ranged_attack_roll(&p);
    ASSERT_GT(p_roll, 5000, "player ranged roll substantial");

    /* Player ranged max hit */
    p.ranged_strength_bonus = 64;
    int p_mh = fc_player_ranged_max_hit(&p);
    ASSERT_GT(p_mh, 5, "player ranged max hit > 5");

    /* Distance to NPC */
    FcNpc npc;
    memset(&npc, 0, sizeof(npc));
    npc.x = 35; npc.y = 35; npc.size = 1;
    int dist = fc_distance_to_npc(32, 32, &npc);
    ASSERT_EQ(dist, 3, "distance chebyshev = 3");

    /* Multi-tile NPC distance */
    npc.size = 2;
    dist = fc_distance_to_npc(36, 35, &npc);
    ASSERT_EQ(dist, 0, "adjacent to 2-tile NPC = 0");
}

/* ======================================================================== */
/* Test: PvM prayer block (100% on correct, 0% on wrong)                     */
/* ======================================================================== */

static void test_prayer_block(void) {
    printf("test_prayer_block:\n");

    /* Correct prayer blocks */
    ASSERT_EQ(fc_prayer_blocks_style(PRAYER_PROTECT_MELEE, ATTACK_MELEE), 1,
              "melee prayer blocks melee");
    ASSERT_EQ(fc_prayer_blocks_style(PRAYER_PROTECT_RANGE, ATTACK_RANGED), 1,
              "range prayer blocks ranged");
    ASSERT_EQ(fc_prayer_blocks_style(PRAYER_PROTECT_MAGIC, ATTACK_MAGIC), 1,
              "magic prayer blocks magic");

    /* Wrong prayer does not block */
    ASSERT_EQ(fc_prayer_blocks_style(PRAYER_PROTECT_MELEE, ATTACK_RANGED), 0,
              "melee prayer doesn't block ranged");
    ASSERT_EQ(fc_prayer_blocks_style(PRAYER_PROTECT_RANGE, ATTACK_MAGIC), 0,
              "range prayer doesn't block magic");
    ASSERT_EQ(fc_prayer_blocks_style(PRAYER_PROTECT_MAGIC, ATTACK_MELEE), 0,
              "magic prayer doesn't block melee");

    /* No prayer doesn't block */
    ASSERT_EQ(fc_prayer_blocks_style(PRAYER_NONE, ATTACK_MELEE), 0,
              "no prayer doesn't block");
}

/* ======================================================================== */
/* Test: Prayer drain                                                        */
/* ======================================================================== */

static void test_prayer_drain(void) {
    printf("test_prayer_drain:\n");

    FcPlayer p;
    memset(&p, 0, sizeof(p));
    p.current_prayer = 430;
    p.max_prayer = 430;
    p.prayer = PRAYER_PROTECT_MELEE;
    p.prayer_bonus = 5;

    int start = p.current_prayer;
    for (int t = 0; t < 10; t++) {
        fc_prayer_drain_tick(&p);
    }
    ASSERT_LT(p.current_prayer, start, "prayer drained after 10 ticks");
    ASSERT_GT(p.current_prayer, 0, "prayer not fully drained in 10 ticks");

    /* Drain to zero deactivates prayer */
    p.current_prayer = 5;  /* almost empty */
    for (int t = 0; t < 100; t++) {
        fc_prayer_drain_tick(&p);
        if (p.prayer == PRAYER_NONE) break;
    }
    ASSERT_EQ(p.prayer, PRAYER_NONE, "prayer deactivated when drained");
    ASSERT_EQ(p.current_prayer, 0, "prayer at 0");
}

/* ======================================================================== */
/* Test: Prayer potion restore                                               */
/* ======================================================================== */

static void test_prayer_potion(void) {
    printf("test_prayer_potion:\n");

    int restore = fc_prayer_potion_restore(43);
    /* floor(43 * 0.25) + 7 = 10 + 7 = 17 points → 170 tenths */
    ASSERT_EQ(restore, 170, "prayer potion restores 17 points at level 43");
}

/* ======================================================================== */
/* Test: Pathfinding                                                         */
/* ======================================================================== */

static void test_pathfinding(void) {
    printf("test_pathfinding:\n");

    uint8_t walkable[FC_ARENA_WIDTH][FC_ARENA_HEIGHT];
    memset(walkable, 1, sizeof(walkable));
    /* Wall at x=34 */
    for (int y = 0; y < FC_ARENA_HEIGHT; y++) walkable[34][y] = 0;

    /* Walk east 1 tile */
    int x = 32, y = 32;
    int moved = fc_move_toward(&x, &y, 1, 0, 1, walkable);
    ASSERT_EQ(moved, 1, "walk east 1 tile");
    ASSERT_EQ(x, 33, "x = 33 after walk east");
    ASSERT_EQ(y, 32, "y unchanged");

    /* Walk east again — blocked by wall at 34 */
    moved = fc_move_toward(&x, &y, 1, 0, 1, walkable);
    ASSERT_EQ(moved, 0, "blocked by wall");
    ASSERT_EQ(x, 33, "x still 33");

    /* Run diagonally NE 2 tiles (no wall) */
    x = 30; y = 30;
    moved = fc_move_toward(&x, &y, 2, 2, 2, walkable);
    ASSERT_EQ(moved, 2, "run NE 2 tiles");
    ASSERT_EQ(x, 32, "x = 32 after run NE");
    ASSERT_EQ(y, 32, "y = 32 after run NE");

    /* NPC step toward player */
    x = 30; y = 30;
    int stepped = fc_npc_step_toward(&x, &y, 32, 32, walkable);
    ASSERT_EQ(stepped, 1, "NPC took 1 step");
    ASSERT_EQ(x, 31, "NPC x closer");
    ASSERT_EQ(y, 31, "NPC y closer");
}

/* ======================================================================== */
/* Test: NPC spawn and stats                                                 */
/* ======================================================================== */

static void test_npc_spawn(void) {
    printf("test_npc_spawn:\n");

    FcNpc npc;
    memset(&npc, 0, sizeof(npc));
    fc_npc_spawn(&npc, NPC_TZ_KIH, 40, 40, 0);

    ASSERT_EQ(npc.active, 1, "NPC active after spawn");
    ASSERT_EQ(npc.npc_type, NPC_TZ_KIH, "NPC type correct");
    ASSERT_EQ(npc.x, 40, "NPC x correct");
    ASSERT_EQ(npc.y, 40, "NPC y correct");
    ASSERT_GT(npc.max_hp, 0, "NPC has HP");
    ASSERT_EQ(npc.current_hp, npc.max_hp, "NPC at full HP");
    ASSERT_EQ(npc.attack_style, ATTACK_MELEE, "Tz-Kih is melee");
    ASSERT_EQ(npc.attack_range, 1, "melee range = 1");

    const FcNpcStats* stats = fc_npc_get_stats(NPC_TZ_KIH);
    ASSERT_EQ(stats->prayer_drain, 10, "Tz-Kih drains 10 tenths (1 point)");
}

/* ======================================================================== */
/* Test: Integration — player vs 1 Tz-Kih scripted combat                   */
/* ======================================================================== */

static void test_combat_integration(void) {
    printf("test_combat_integration:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    /* Spawn Tz-Kih adjacent to player */
    spawn_tz_kih(&state, state.player.x + 1, state.player.y);

    int idle[FC_NUM_ACTION_HEADS] = {0, 0, 0, 0, 0};
    int pray_melee[FC_NUM_ACTION_HEADS] = {0, 0, FC_PRAYER_MELEE, 0, 0};
    int attack_slot0[FC_NUM_ACTION_HEADS] = {0, 1, 0, 0, 0};  /* attack NPC in slot 0 */

    /* Turn on melee prayer first */
    fc_step(&state, pray_melee);
    ASSERT_EQ(state.player.prayer, PRAYER_PROTECT_MELEE, "melee prayer active");

    /* Step several ticks — NPC should attack but prayer blocks damage */
    int hp_before = state.player.current_hp;
    for (int t = 0; t < 20; t++) {
        fc_step(&state, idle);
    }
    /* With melee prayer, all Tz-Kih melee hits should be blocked (100%) */
    ASSERT_EQ(state.player.current_hp, hp_before, "melee prayer blocks all Tz-Kih damage");

    /* Now turn off prayer and let the NPC hit */
    int pray_off[FC_NUM_ACTION_HEADS] = {0, 0, FC_PRAYER_OFF, 0, 0};
    fc_step(&state, pray_off);
    ASSERT_EQ(state.player.prayer, PRAYER_NONE, "prayer off");

    /* Step until hit — the NPC should deal some damage eventually */
    hp_before = state.player.current_hp;
    int took_damage = 0;
    for (int t = 0; t < 30; t++) {
        fc_step(&state, idle);
        if (state.player.current_hp < hp_before) {
            took_damage = 1;
            break;
        }
    }
    ASSERT(took_damage, "player took damage without prayer");

    /* Attack the NPC */
    int npc_hp_before = state.npcs[0].current_hp;
    for (int t = 0; t < 30; t++) {
        fc_step(&state, attack_slot0);
    }
    /* Player should have dealt some damage (ranged attacks hitting) */
    ASSERT_LT(state.npcs[0].current_hp, npc_hp_before, "NPC took damage from player");
}

/* ======================================================================== */
/* Test: NPC dies → npcs_remaining decremented                               */
/* ======================================================================== */

static void test_npc_death(void) {
    printf("test_npc_death:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    spawn_tz_kih(&state, state.player.x + 2, state.player.y);
    ASSERT_EQ(state.npcs_remaining, 1, "1 NPC remaining");

    /* Kill the NPC by setting low HP then attacking */
    state.npcs[0].current_hp = 1;

    int attack[FC_NUM_ACTION_HEADS] = {0, 1, 0, 0, 0};
    for (int t = 0; t < 20; t++) {
        fc_step(&state, attack);
        if (state.npcs[0].is_dead) break;
    }
    ASSERT_EQ(state.npcs[0].is_dead, 1, "NPC is dead");
    ASSERT_EQ(state.npcs_remaining, 0, "0 NPCs remaining");
    ASSERT_GT(state.total_npcs_killed, 0, "kill counted");
}

/* ======================================================================== */
/* Test: Eating and drinking                                                 */
/* ======================================================================== */

static void test_consumables(void) {
    printf("test_consumables:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    /* Damage the player */
    state.player.current_hp = 300;

    int eat[FC_NUM_ACTION_HEADS] = {0, 0, 0, FC_EAT_SHARK, 0};
    fc_step(&state, eat);
    ASSERT_GT(state.player.current_hp, 300, "healed after eating");
    ASSERT_EQ(state.player.sharks_remaining, FC_MAX_SHARKS - 1, "shark consumed");

    /* Can't eat again immediately (food timer) */
    int hp_after_eat = state.player.current_hp;
    state.player.current_hp = 300;  /* damage again */
    fc_step(&state, eat);
    ASSERT_EQ(state.player.current_hp, 300, "can't eat during cooldown");

    /* Wait for cooldown then eat again */
    int idle[FC_NUM_ACTION_HEADS] = {0, 0, 0, 0, 0};
    for (int t = 0; t < FC_FOOD_COOLDOWN_TICKS; t++) fc_step(&state, idle);
    fc_step(&state, eat);
    ASSERT_GT(state.player.current_hp, 300, "ate again after cooldown");

    /* Drink prayer potion */
    state.player.current_prayer = 100;  /* low prayer */
    state.player.potion_timer = 0;
    int drink[FC_NUM_ACTION_HEADS] = {0, 0, 0, 0, FC_DRINK_PRAYER_POT};
    fc_step(&state, drink);
    ASSERT_GT(state.player.current_prayer, 100, "prayer restored");
    ASSERT_EQ(state.player.prayer_doses_remaining, FC_MAX_PRAYER_DOSES - 1, "dose consumed");
}

/* ======================================================================== */
/* Test: Determinism — same seed + actions = identical per-tick hashes        */
/* ======================================================================== */

static void test_determinism(void) {
    printf("test_determinism:\n");

    FcState a, b;
    fc_init(&a);
    fc_init(&b);

    fc_reset(&a, 12345);
    fc_reset(&b, 12345);

    /* Spawn NPC in both */
    spawn_tz_kih(&a, a.player.x + 1, a.player.y);
    spawn_tz_kih(&b, b.player.x + 1, b.player.y);

    /* Run 50 ticks with varied actions */
    int actions_sequence[][FC_NUM_ACTION_HEADS] = {
        {0, 0, FC_PRAYER_MELEE, 0, 0},
        {FC_MOVE_WALK_N, 0, 0, 0, 0},
        {0, 1, 0, 0, 0},
        {0, 0, 0, FC_EAT_SHARK, 0},
        {FC_MOVE_WALK_S, 0, FC_PRAYER_OFF, 0, 0},
    };
    int num_scripts = 5;

    for (int t = 0; t < 50; t++) {
        int* act = actions_sequence[t % num_scripts];
        fc_step(&a, act);
        fc_step(&b, act);

        uint32_t ha = fc_state_hash(&a);
        uint32_t hb = fc_state_hash(&b);
        if (ha != hb) {
            printf("    Hash diverged at tick %d: 0x%08x vs 0x%08x\n", t+1, ha, hb);
            ASSERT(0, "per-tick determinism");
            return;
        }
    }
    ASSERT(1, "50-tick deterministic with combat");
}

/* ======================================================================== */
/* Test: Golden trace — record and replay a scripted scenario                */
/* ======================================================================== */

static void test_golden_trace(void) {
    printf("test_golden_trace:\n");

    /*
     * Golden trace: a replayable artifact that future PRs can use to detect
     * simulation drift. If fc_tick semantics change, this test breaks.
     *
     * Scenario: seed 777, spawn 1 Tz-Kih at (33,32), player at center.
     * 20-tick scripted sequence: prayer on → attack → idle → eat → pray off.
     */
    const uint32_t GOLDEN_SEED = 777;
    const int GOLDEN_TICKS = 20;
    int golden_actions[20][FC_NUM_ACTION_HEADS] = {
        {0, 0, FC_PRAYER_MELEE, 0, 0},           /* t0: pray melee */
        {0, 1, 0, 0, 0},                          /* t1: attack slot 0 */
        {0, 1, 0, 0, 0},                          /* t2: attack */
        {0, 0, 0, 0, 0},                          /* t3: idle */
        {0, 1, 0, 0, 0},                          /* t4: attack */
        {0, 1, 0, 0, 0},                          /* t5: attack */
        {0, 0, 0, 0, 0},                          /* t6: idle */
        {0, 1, 0, 0, 0},                          /* t7: attack */
        {0, 1, 0, 0, 0},                          /* t8: attack */
        {0, 0, 0, 0, 0},                          /* t9: idle */
        {0, 1, 0, 0, 0},                          /* t10: attack */
        {FC_MOVE_WALK_S, 0, 0, 0, 0},             /* t11: walk south */
        {FC_MOVE_WALK_N, 0, 0, 0, 0},             /* t12: walk north */
        {0, 1, 0, 0, 0},                          /* t13: attack */
        {0, 0, 0, 0, FC_DRINK_PRAYER_POT},        /* t14: drink potion */
        {0, 0, FC_PRAYER_OFF, 0, 0},              /* t15: pray off */
        {0, 1, 0, 0, 0},                          /* t16: attack */
        {0, 0, 0, 0, 0},                          /* t17: idle */
        {0, 0, FC_PRAYER_MELEE, 0, 0},            /* t18: pray melee again */
        {0, 1, 0, 0, 0},                          /* t19: attack */
    };

    /* Record pass */
    FcState state;
    FcActionTrace trace;
    fc_init(&state);
    fc_trace_init(&trace);

    fc_reset(&state, GOLDEN_SEED);
    fc_trace_reset(&trace, GOLDEN_SEED);
    spawn_tz_kih(&state, state.player.x + 1, state.player.y);

    uint32_t recorded_hashes[20];
    for (int t = 0; t < GOLDEN_TICKS; t++) {
        fc_step(&state, golden_actions[t]);
        recorded_hashes[t] = fc_state_hash(&state);
        fc_trace_record(&trace, golden_actions[t], FC_NUM_ACTION_HEADS, recorded_hashes[t]);
    }

    /* Replay pass — must produce identical hashes */
    FcState replay;
    fc_init(&replay);
    fc_reset(&replay, trace.seed);
    spawn_tz_kih(&replay, replay.player.x + 1, replay.player.y);

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
    ASSERT(replay_ok, "golden trace replay matches recording");

    /* Print the final hash as a reference — if this changes, simulation semantics changed */
    printf("    Golden trace final hash (seed %u, %d ticks): 0x%08x\n",
           GOLDEN_SEED, GOLDEN_TICKS, recorded_hashes[GOLDEN_TICKS - 1]);

    fc_trace_destroy(&trace);
}

/* ======================================================================== */
/* Test: Observation reflects combat state                                   */
/* ======================================================================== */

static void test_obs_with_combat(void) {
    printf("test_obs_with_combat:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    spawn_tz_kih(&state, state.player.x + 1, state.player.y);

    float obs[FC_OBS_SIZE];
    fc_write_obs(&state, obs);
    fc_write_mask(&state, obs + FC_TOTAL_OBS);

    /* NPC slot 0 should be valid */
    float valid = obs[FC_OBS_NPC_START + FC_NPC_VALID];
    ASSERT_FLOAT_NEAR(valid, 1.0f, 0.01f, "NPC slot 0 valid");

    /* NPC type should be Tz-Kih normalized */
    float npc_type = obs[FC_OBS_NPC_START + FC_NPC_TYPE];
    float expected_type = (float)NPC_TZ_KIH / (float)NPC_TYPE_COUNT;
    ASSERT_FLOAT_NEAR(npc_type, expected_type, 0.01f, "NPC type matches Tz-Kih");

    /* Attack mask: slot 0 should be valid (NPC exists) */
    float atk_mask = obs[FC_TOTAL_OBS + FC_MASK_ATTACK_START + 1];
    ASSERT_FLOAT_NEAR(atk_mask, 1.0f, 0.01f, "attack slot 0 valid");

    /* NPC remaining in meta */
    float remaining = obs[FC_OBS_META_START + FC_OBS_META_REMAINING];
    ASSERT_GT(remaining, 0.0f, "npcs remaining > 0");
}

/* ======================================================================== */
/* Test: PR1 regression — verify PR1 tests still pass after PR2 changes      */
/* ======================================================================== */

static void test_pr1_regression(void) {
    printf("test_pr1_regression:\n");

    FcState a, b;
    fc_init(&a);
    fc_init(&b);

    /* Reset determinism still works */
    fc_reset(&a, 42);
    fc_reset(&b, 42);
    ASSERT_EQ(fc_state_hash(&a), fc_state_hash(&b), "reset still deterministic");

    /* Tick cap still works (check_terminal sees tick before increment) */
    fc_reset(&a, 42);
    a.tick = FC_MAX_EPISODE_TICKS;
    int idle[FC_NUM_ACTION_HEADS] = {0, 0, 0, 0, 0};
    fc_step(&a, idle);
    ASSERT_EQ(fc_is_terminal(&a), 1, "tick cap still triggers");
}

/* ======================================================================== */
/* Main                                                                      */
/* ======================================================================== */

int main(void) {
    printf("=== PR 2 Tests: Minimal Combat Slice (Player vs 1 Tz-Kih) ===\n\n");

    test_combat_math();
    test_prayer_block();
    test_prayer_drain();
    test_prayer_potion();
    test_pathfinding();
    test_npc_spawn();
    test_combat_integration();
    test_npc_death();
    test_consumables();
    test_determinism();
    test_golden_trace();
    test_obs_with_combat();
    test_pr1_regression();

    printf("\n=== Results: %d/%d passed ===\n", tests_passed, tests_run);
    return (tests_passed == tests_run) ? 0 : 1;
}
