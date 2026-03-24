#include "fc_types.h"
#include "fc_contracts.h"
#include "fc_api.h"
#include "fc_debug.h"
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <math.h>

/* ======================================================================== */
/* Minimal test framework                                                    */
/* ======================================================================== */

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
#define ASSERT_FLOAT_EQ(a, b, msg) ASSERT(fabsf((a) - (b)) < 1e-6f, msg)

/* ======================================================================== */
/* Test: Contract constant consistency                                       */
/* ======================================================================== */

static void test_contract_constants(void) {
    printf("test_contract_constants:\n");

    /* Policy obs + reward features = total obs */
    ASSERT_EQ(FC_POLICY_OBS_SIZE + FC_REWARD_FEATURES, FC_TOTAL_OBS,
              "FC_TOTAL_OBS = policy_obs + reward_features");

    /* Total obs + mask = full buffer */
    ASSERT_EQ(FC_TOTAL_OBS + FC_ACTION_MASK_SIZE, FC_OBS_SIZE,
              "FC_OBS_SIZE = total_obs + mask");

    /* Policy obs = player + NPC + meta */
    ASSERT_EQ(FC_OBS_PLAYER_SIZE + FC_OBS_NPC_TOTAL + FC_OBS_META_SIZE, FC_POLICY_OBS_SIZE,
              "policy_obs = player + npc + meta");

    /* NPC total = stride * slots */
    ASSERT_EQ(FC_OBS_NPC_STRIDE * FC_OBS_NPC_SLOTS, FC_OBS_NPC_TOTAL,
              "npc_total = stride * slots");

    /* Mask size = sum of head dims */
    ASSERT_EQ(FC_MOVE_DIM + FC_ATTACK_DIM + FC_PRAYER_DIM + FC_EAT_DIM + FC_DRINK_DIM,
              FC_ACTION_MASK_SIZE, "mask_size = sum of head dims");

    /* Action head count */
    ASSERT_EQ(FC_NUM_ACTION_HEADS, 5, "5 action heads");

    /* NPC slots matches visible cap */
    ASSERT_EQ(FC_OBS_NPC_SLOTS, FC_VISIBLE_NPCS, "NPC obs slots = visible cap");

    /* Reward features count */
    ASSERT_EQ(FC_REWARD_FEATURES, 16, "16 reward features");

    /* Concrete sizes */
    ASSERT_EQ(FC_POLICY_OBS_SIZE, 126, "policy obs = 126 floats");
    ASSERT_EQ(FC_TOTAL_OBS, 142, "total obs = 142 floats");
    ASSERT_EQ(FC_ACTION_MASK_SIZE, 36, "mask = 36 floats");
    ASSERT_EQ(FC_OBS_SIZE, 178, "full buffer = 178 floats");

    /* Observation region boundaries don't overlap */
    ASSERT_EQ(FC_OBS_NPC_START, FC_OBS_PLAYER_SIZE, "NPC starts after player");
    ASSERT_EQ(FC_OBS_META_START, FC_OBS_NPC_START + FC_OBS_NPC_TOTAL, "meta starts after NPC");
    ASSERT_EQ(FC_REWARD_START, FC_POLICY_OBS_SIZE, "reward starts after policy obs");

    /* Mask region boundaries */
    ASSERT_EQ(FC_MASK_ATTACK_START, FC_MOVE_DIM, "attack mask after move mask");
    ASSERT_EQ(FC_MASK_PRAYER_START, FC_MASK_ATTACK_START + FC_ATTACK_DIM, "prayer mask after attack");
    ASSERT_EQ(FC_MASK_EAT_START, FC_MASK_PRAYER_START + FC_PRAYER_DIM, "eat mask after prayer");
    ASSERT_EQ(FC_MASK_DRINK_START, FC_MASK_EAT_START + FC_EAT_DIM, "drink mask after eat");
}

/* ======================================================================== */
/* Test: State init and reset                                                */
/* ======================================================================== */

static void test_state_init_reset(void) {
    printf("test_state_init_reset:\n");

    FcState state;
    fc_init(&state);

    /* After init, everything should be zero */
    ASSERT_EQ(state.tick, 0, "tick is 0 after init");
    ASSERT_EQ(state.terminal, TERMINAL_NONE, "not terminal after init");
    ASSERT_EQ(state.player.current_hp, 0, "player HP is 0 after init (not yet reset)");

    /* Reset with a seed */
    fc_reset(&state, 42);

    ASSERT_EQ(state.tick, 0, "tick is 0 after reset");
    ASSERT_EQ(state.terminal, TERMINAL_NONE, "not terminal after reset");
    ASSERT_EQ(state.player.current_hp, FC_PLAYER_MAX_HP, "player HP is max after reset");
    ASSERT_EQ(state.player.max_hp, FC_PLAYER_MAX_HP, "player max HP correct");
    ASSERT_EQ(state.player.current_prayer, FC_PLAYER_MAX_PRAYER, "prayer is max after reset");
    ASSERT_EQ(state.player.sharks_remaining, FC_MAX_SHARKS, "sharks correct after reset");
    ASSERT_EQ(state.player.prayer_doses_remaining, FC_MAX_PRAYER_DOSES, "doses correct");
    ASSERT_EQ(state.player.prayer, PRAYER_NONE, "prayer off after reset");
    ASSERT_EQ(state.current_wave, 1, "wave 1 after reset");
    ASSERT_EQ(state.rng_seed, 42, "seed stored");
    ASSERT(state.rng_state != 0, "RNG state nonzero");

    /* Player position is center */
    ASSERT_EQ(state.player.x, FC_ARENA_WIDTH / 2, "player x at center");
    ASSERT_EQ(state.player.y, FC_ARENA_HEIGHT / 2, "player y at center");

    /* Arena borders are non-walkable */
    ASSERT_EQ(state.walkable[0][0], 0, "corner not walkable");
    ASSERT_EQ(state.walkable[0][FC_ARENA_HEIGHT/2], 0, "left border not walkable");
    ASSERT_EQ(state.walkable[FC_ARENA_WIDTH/2][FC_ARENA_HEIGHT/2], 1, "center walkable");

    /* Rotation is in valid range */
    ASSERT(state.rotation_id >= 0 && state.rotation_id < FC_NUM_ROTATIONS,
           "rotation in valid range");
}

/* ======================================================================== */
/* Test: Reset is deterministic (same seed → same state)                     */
/* ======================================================================== */

static void test_reset_determinism(void) {
    printf("test_reset_determinism:\n");

    FcState a, b;
    fc_init(&a);
    fc_init(&b);

    fc_reset(&a, 12345);
    fc_reset(&b, 12345);

    ASSERT_EQ(a.rotation_id, b.rotation_id, "same seed → same rotation");
    ASSERT_EQ(a.rng_state, b.rng_state, "same seed → same RNG state");
    ASSERT_EQ(fc_state_hash(&a), fc_state_hash(&b), "same seed → same hash");

    /* Different seed → different state */
    fc_reset(&b, 99999);
    ASSERT_NEQ(fc_state_hash(&a), fc_state_hash(&b), "different seed → different hash");
}

/* ======================================================================== */
/* Test: RNG determinism                                                     */
/* ======================================================================== */

static void test_rng_determinism(void) {
    printf("test_rng_determinism:\n");

    FcState a, b;
    fc_init(&a);
    fc_init(&b);

    fc_rng_seed(&a, 777);
    fc_rng_seed(&b, 777);

    /* Same seed → same sequence */
    for (int i = 0; i < 100; i++) {
        uint32_t va = fc_rng_next(&a);
        uint32_t vb = fc_rng_next(&b);
        if (va != vb) {
            ASSERT(0, "RNG sequence diverged");
            return;
        }
    }
    ASSERT(1, "RNG sequence matches for 100 values");

    /* Verify range functions */
    fc_rng_seed(&a, 42);
    int saw_different = 0;
    for (int i = 0; i < 100; i++) {
        int v = fc_rng_int(&a, 10);
        ASSERT(v >= 0 && v < 10, "rng_int in range");
        if (v != 0) saw_different = 1;
    }
    ASSERT(saw_different, "rng_int produces variety");

    fc_rng_seed(&a, 42);
    for (int i = 0; i < 100; i++) {
        float f = fc_rng_float(&a);
        ASSERT(f >= 0.0f && f < 1.0f, "rng_float in [0,1)");
    }

    /* Seed 0 doesn't produce stuck RNG */
    fc_rng_seed(&a, 0);
    uint32_t v1 = fc_rng_next(&a);
    uint32_t v2 = fc_rng_next(&a);
    ASSERT_NEQ(v1, v2, "seed 0 doesn't produce stuck RNG");
}

/* ======================================================================== */
/* Test: State hash — determinism verification                               */
/* ======================================================================== */

static void test_state_hash_determinism(void) {
    printf("test_state_hash_determinism:\n");

    FcState a, b;
    fc_init(&a);
    fc_init(&b);

    int actions[FC_NUM_ACTION_HEADS] = {0, 0, 0, 0, 0};

    fc_reset(&a, 42);
    fc_reset(&b, 42);

    /* Run 10 steps with same actions, verify per-tick hash equality */
    for (int t = 0; t < 10; t++) {
        fc_step(&a, actions);
        fc_step(&b, actions);

        uint32_t ha = fc_state_hash(&a);
        uint32_t hb = fc_state_hash(&b);
        if (ha != hb) {
            printf("    Hash diverged at tick %d: 0x%08x vs 0x%08x\n", t+1, ha, hb);
            ASSERT(0, "per-tick hash equality");
            return;
        }
    }
    ASSERT(1, "per-tick hashes match for 10 steps");

    /* Verify hash changes across ticks (not a constant) */
    fc_reset(&a, 100);
    uint32_t h0 = fc_state_hash(&a);
    fc_step(&a, actions);
    uint32_t h1 = fc_state_hash(&a);
    ASSERT_NEQ(h0, h1, "hash changes after step (tick advances)");
}

/* ======================================================================== */
/* Test: State hash — padding independence (false drift detection)            */
/* ======================================================================== */

static void test_state_hash_padding_independence(void) {
    printf("test_state_hash_padding_independence:\n");

    /*
     * Verify that fc_state_hash does NOT depend on padding bytes.
     * Allocate two states with different garbage in padding, reset both
     * with the same seed, and confirm identical hashes.
     */
    FcState* a = (FcState*)malloc(sizeof(FcState));
    FcState* b = (FcState*)malloc(sizeof(FcState));

    /* Fill both with different garbage patterns */
    memset(a, 0xAA, sizeof(FcState));
    memset(b, 0x55, sizeof(FcState));

    /* Reset both with same seed — this memsets to 0 then initializes fields */
    fc_reset(a, 42);
    fc_reset(b, 42);

    uint32_t ha = fc_state_hash(a);
    uint32_t hb = fc_state_hash(b);
    ASSERT_EQ(ha, hb, "hash independent of prior memory contents (padding zeroed by reset)");

    /* Additional check: manually corrupt a padding byte (if any exists between fields).
     * We verify this by checking that two states with identical field values
     * but allocated from different memory produce the same hash.
     * Since fc_reset memsets to 0 first, this is already tested above.
     * The real guarantee is that fc_state_hash reads explicit fields, not raw bytes. */

    free(a);
    free(b);
}

/* ======================================================================== */
/* Test: Observation output                                                  */
/* ======================================================================== */

static void test_observation_output(void) {
    printf("test_observation_output:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    float obs[FC_OBS_SIZE];
    memset(obs, 0xFF, sizeof(obs));  /* poison first */

    fc_write_obs(&state, obs);
    fc_write_mask(&state, obs + FC_TOTAL_OBS);

    /* Player HP should be 1.0 (full health) */
    ASSERT_FLOAT_EQ(obs[FC_OBS_PLAYER_HP], 1.0f, "player HP = 1.0 at full health");

    /* Player prayer should be 1.0 (full prayer) */
    ASSERT_FLOAT_EQ(obs[FC_OBS_PLAYER_PRAYER], 1.0f, "player prayer = 1.0 at full");

    /* Player position should be normalized */
    float px = obs[FC_OBS_PLAYER_X];
    float py = obs[FC_OBS_PLAYER_Y];
    ASSERT(px > 0.0f && px < 1.0f, "player x normalized in (0,1)");
    ASSERT(py > 0.0f && py < 1.0f, "player y normalized in (0,1)");

    /* Prayer flags should all be 0 (no prayer active) */
    ASSERT_FLOAT_EQ(obs[FC_OBS_PLAYER_PRAY_MEL], 0.0f, "no melee prayer");
    ASSERT_FLOAT_EQ(obs[FC_OBS_PLAYER_PRAY_RNG], 0.0f, "no ranged prayer");
    ASSERT_FLOAT_EQ(obs[FC_OBS_PLAYER_PRAY_MAG], 0.0f, "no magic prayer");

    /* Sharks normalized */
    ASSERT_FLOAT_EQ(obs[FC_OBS_PLAYER_SHARKS], 1.0f, "sharks = 1.0 at full");

    /* All NPC slots should be empty (no NPCs spawned yet) */
    for (int slot = 0; slot < FC_OBS_NPC_SLOTS; slot++) {
        float valid = obs[FC_OBS_NPC_START + slot * FC_OBS_NPC_STRIDE + FC_NPC_VALID];
        ASSERT_FLOAT_EQ(valid, 0.0f, "NPC slot empty (no spawns)");
    }

    /* Wave meta */
    float wave = obs[FC_OBS_META_START + FC_OBS_META_WAVE];
    ASSERT(wave > 0.0f, "wave > 0 (wave 1)");

    /* Reward features: tick penalty always 1.0 */
    float tick_penalty = obs[FC_REWARD_START + FC_RWD_TICK_PENALTY];
    ASSERT_FLOAT_EQ(tick_penalty, 1.0f, "tick penalty = 1.0");

    /* All obs values should be in [0, 1] range */
    int all_normalized = 1;
    for (int i = 0; i < FC_TOTAL_OBS; i++) {
        if (obs[i] < -0.01f || obs[i] > 1.01f) {
            printf("    obs[%d] = %f out of range\n", i, obs[i]);
            all_normalized = 0;
        }
    }
    ASSERT(all_normalized, "all obs values in [0,1]");

    /* Mask: idle always valid */
    float mask_idle = obs[FC_TOTAL_OBS + FC_MASK_MOVE_START + FC_MOVE_IDLE];
    ASSERT_FLOAT_EQ(mask_idle, 1.0f, "idle move always valid");

    /* Mask: attack none always valid */
    float mask_atk_none = obs[FC_TOTAL_OBS + FC_MASK_ATTACK_START + FC_ATTACK_NONE];
    ASSERT_FLOAT_EQ(mask_atk_none, 1.0f, "attack none always valid");

    /* Mask: all NPC attack slots should be masked (no NPCs) */
    for (int slot = 0; slot < FC_VISIBLE_NPCS; slot++) {
        float m = obs[FC_TOTAL_OBS + FC_MASK_ATTACK_START + 1 + slot];
        ASSERT_FLOAT_EQ(m, 0.0f, "NPC attack slot masked (no NPCs)");
    }

    /* Mask values should be 0 or 1 */
    int mask_binary = 1;
    for (int i = 0; i < FC_ACTION_MASK_SIZE; i++) {
        float v = obs[FC_TOTAL_OBS + i];
        if (v != 0.0f && v != 1.0f) {
            printf("    mask[%d] = %f (not 0 or 1)\n", i, v);
            mask_binary = 0;
        }
    }
    ASSERT(mask_binary, "all mask values are 0 or 1");
}

/* ======================================================================== */
/* Test: Reward features are separable from policy obs                       */
/* ======================================================================== */

static void test_reward_separation(void) {
    printf("test_reward_separation:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    float obs[FC_OBS_SIZE];
    fc_write_obs(&state, obs);

    /* Verify reward features start at the right offset */
    float rwd[FC_REWARD_FEATURES];
    fc_write_reward_features(&state, rwd);

    for (int i = 0; i < FC_REWARD_FEATURES; i++) {
        ASSERT_FLOAT_EQ(obs[FC_REWARD_START + i], rwd[i],
                        "reward in obs buffer matches standalone write");
    }

    /* Verify policy obs does NOT include reward features */
    /* (Policy obs is obs[0..FC_POLICY_OBS_SIZE-1], reward is obs[FC_POLICY_OBS_SIZE..]) */
    ASSERT(FC_REWARD_START == FC_POLICY_OBS_SIZE,
           "reward starts exactly where policy obs ends");
}

/* ======================================================================== */
/* Test: NPC slot ordering (deterministic, distance-based)                   */
/* ======================================================================== */

static void test_npc_slot_ordering(void) {
    printf("test_npc_slot_ordering:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    /* Manually place some NPCs at known positions */
    state.player.x = 32;
    state.player.y = 32;

    /* NPC 0: far away */
    state.npcs[0].active = 1;
    state.npcs[0].npc_type = NPC_TZ_KIH;
    state.npcs[0].x = 50;
    state.npcs[0].y = 50;
    state.npcs[0].current_hp = 100;
    state.npcs[0].max_hp = 100;
    state.npcs[0].spawn_index = 0;
    state.npcs[0].size = 1;

    /* NPC 1: close */
    state.npcs[1].active = 1;
    state.npcs[1].npc_type = NPC_TOK_XIL;
    state.npcs[1].x = 33;
    state.npcs[1].y = 33;
    state.npcs[1].current_hp = 200;
    state.npcs[1].max_hp = 200;
    state.npcs[1].spawn_index = 1;
    state.npcs[1].size = 1;

    /* NPC 2: same distance as NPC 1 but higher spawn index */
    state.npcs[2].active = 1;
    state.npcs[2].npc_type = NPC_TZ_KEK;
    state.npcs[2].x = 31;
    state.npcs[2].y = 31;
    state.npcs[2].current_hp = 150;
    state.npcs[2].max_hp = 150;
    state.npcs[2].spawn_index = 2;
    state.npcs[2].size = 1;

    float obs[FC_OBS_SIZE];
    fc_write_obs(&state, obs);

    /* Slot 0 should be NPC 1 (closest, spawn_index=1, distance=1) */
    float slot0_type = obs[FC_OBS_NPC_START + 0 * FC_OBS_NPC_STRIDE + FC_NPC_TYPE];
    float slot0_valid = obs[FC_OBS_NPC_START + 0 * FC_OBS_NPC_STRIDE + FC_NPC_VALID];
    ASSERT_FLOAT_EQ(slot0_valid, 1.0f, "slot 0 valid");

    /* Slot 1 should be NPC 2 (same distance=1, but spawn_index=2 > 1) */
    float slot1_type = obs[FC_OBS_NPC_START + 1 * FC_OBS_NPC_STRIDE + FC_NPC_TYPE];
    float slot1_valid = obs[FC_OBS_NPC_START + 1 * FC_OBS_NPC_STRIDE + FC_NPC_VALID];
    ASSERT_FLOAT_EQ(slot1_valid, 1.0f, "slot 1 valid");

    /* Slot 2 should be NPC 0 (farthest, distance=18) */
    float slot2_type = obs[FC_OBS_NPC_START + 2 * FC_OBS_NPC_STRIDE + FC_NPC_TYPE];
    float slot2_valid = obs[FC_OBS_NPC_START + 2 * FC_OBS_NPC_STRIDE + FC_NPC_VALID];
    ASSERT_FLOAT_EQ(slot2_valid, 1.0f, "slot 2 valid");

    /* Verify ordering: slot0.distance <= slot1.distance <= slot2.distance */
    float d0 = obs[FC_OBS_NPC_START + 0 * FC_OBS_NPC_STRIDE + FC_NPC_DISTANCE];
    float d1 = obs[FC_OBS_NPC_START + 1 * FC_OBS_NPC_STRIDE + FC_NPC_DISTANCE];
    float d2 = obs[FC_OBS_NPC_START + 2 * FC_OBS_NPC_STRIDE + FC_NPC_DISTANCE];
    ASSERT(d0 <= d1, "slot 0 closer or equal to slot 1");
    ASSERT(d1 <= d2, "slot 1 closer or equal to slot 2");

    /* Distance tie: slot0 and slot1 have same distance, slot0 has lower spawn_index */
    /* NPC 1 (spawn_index=1) should be slot 0, NPC 2 (spawn_index=2) should be slot 1 */
    float s0_type_norm = (float)NPC_TOK_XIL / (float)NPC_TYPE_COUNT;
    float s1_type_norm = (float)NPC_TZ_KEK / (float)NPC_TYPE_COUNT;
    ASSERT_FLOAT_EQ(slot0_type, s0_type_norm, "slot 0 is NPC_TOK_XIL (closer spawn_index)");
    ASSERT_FLOAT_EQ(slot1_type, s1_type_norm, "slot 1 is NPC_TZ_KEK (farther spawn_index)");

    /* Slots 3-7 should be empty */
    for (int slot = 3; slot < FC_OBS_NPC_SLOTS; slot++) {
        float v = obs[FC_OBS_NPC_START + slot * FC_OBS_NPC_STRIDE + FC_NPC_VALID];
        ASSERT_FLOAT_EQ(v, 0.0f, "overflow slot empty");
    }
}

/* ======================================================================== */
/* Test: NPC slot overflow (more than 8 active NPCs)                         */
/* ======================================================================== */

static void test_npc_slot_overflow(void) {
    printf("test_npc_slot_overflow:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    state.player.x = 32;
    state.player.y = 32;

    /* Spawn 12 NPCs at increasing distances */
    for (int i = 0; i < 12; i++) {
        state.npcs[i].active = 1;
        state.npcs[i].npc_type = NPC_TZ_KIH;
        state.npcs[i].x = 32 + i + 1;
        state.npcs[i].y = 32;
        state.npcs[i].current_hp = 100;
        state.npcs[i].max_hp = 100;
        state.npcs[i].spawn_index = i;
        state.npcs[i].size = 1;
    }

    float obs[FC_OBS_SIZE];
    fc_write_obs(&state, obs);

    /* All 8 visible slots should be valid */
    for (int slot = 0; slot < FC_VISIBLE_NPCS; slot++) {
        float v = obs[FC_OBS_NPC_START + slot * FC_OBS_NPC_STRIDE + FC_NPC_VALID];
        ASSERT_FLOAT_EQ(v, 1.0f, "visible slot valid");
    }

    /* The 8 closest NPCs should be in slots (distances 1-8) */
    float d7 = obs[FC_OBS_NPC_START + 7 * FC_OBS_NPC_STRIDE + FC_NPC_DISTANCE];
    /* NPC at distance 8 → 8/64 = 0.125 */
    ASSERT(d7 <= 0.13f, "slot 7 is 8th closest NPC");

    /* Verify strictly increasing distance across slots */
    for (int slot = 1; slot < FC_VISIBLE_NPCS; slot++) {
        float dp = obs[FC_OBS_NPC_START + (slot-1) * FC_OBS_NPC_STRIDE + FC_NPC_DISTANCE];
        float dc = obs[FC_OBS_NPC_START + slot * FC_OBS_NPC_STRIDE + FC_NPC_DISTANCE];
        ASSERT(dp <= dc, "distances non-decreasing");
    }
}

/* ======================================================================== */
/* Test: Render entities                                                     */
/* ======================================================================== */

static void test_render_entities(void) {
    printf("test_render_entities:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    /* Add one NPC */
    state.npcs[0].active = 1;
    state.npcs[0].npc_type = NPC_TZ_KIH;
    state.npcs[0].x = 40;
    state.npcs[0].y = 40;
    state.npcs[0].current_hp = 100;
    state.npcs[0].max_hp = 100;
    state.npcs[0].size = 1;

    FcRenderEntity entities[FC_MAX_RENDER_ENTITIES];
    int count = 0;
    fc_fill_render_entities(&state, entities, &count);

    ASSERT_EQ(count, 2, "1 player + 1 NPC = 2 entities");
    ASSERT_EQ(entities[0].entity_type, ENTITY_PLAYER, "entity 0 is player");
    ASSERT_EQ(entities[0].x, state.player.x, "player x correct");
    ASSERT_EQ(entities[1].entity_type, ENTITY_NPC, "entity 1 is NPC");
    ASSERT_EQ(entities[1].npc_type, NPC_TZ_KIH, "NPC type correct");
    ASSERT_EQ(entities[1].x, 40, "NPC x correct");
}

/* ======================================================================== */
/* Test: Action trace (record and verify)                                    */
/* ======================================================================== */

static void test_action_trace(void) {
    printf("test_action_trace:\n");

    FcActionTrace trace;
    fc_trace_init(&trace);
    fc_trace_reset(&trace, 42);

    ASSERT_EQ(trace.seed, 42, "trace seed stored");
    ASSERT_EQ(trace.num_ticks, 0, "trace starts empty");

    /* Record actions from a real run so hashes match */
    FcState rec;
    fc_init(&rec);
    fc_reset(&rec, 42);

    int actions1[FC_NUM_ACTION_HEADS] = {1, 0, 0, 0, 0};
    int actions2[FC_NUM_ACTION_HEADS] = {5, 0, 2, 0, 0};

    fc_step(&rec, actions1);
    fc_trace_record(&trace, actions1, FC_NUM_ACTION_HEADS, fc_state_hash(&rec));

    fc_step(&rec, actions2);
    fc_trace_record(&trace, actions2, FC_NUM_ACTION_HEADS, fc_state_hash(&rec));

    ASSERT_EQ(trace.num_ticks, 2, "2 ticks recorded");
    ASSERT_EQ(trace.actions[0 * FC_NUM_ACTION_HEADS + 0], 1, "tick 0 head 0 correct");
    ASSERT_EQ(trace.actions[1 * FC_NUM_ACTION_HEADS + 0], 5, "tick 1 head 0 correct");

    /* Verify replay: reset state, replay actions, check hashes match */
    FcState state;
    fc_init(&state);
    fc_reset(&state, trace.seed);

    for (int t = 0; t < trace.num_ticks; t++) {
        fc_step(&state, &trace.actions[t * FC_NUM_ACTION_HEADS]);
        uint32_t h = fc_state_hash(&state);
        ASSERT_EQ(h, trace.hashes[t], "replay hash matches recorded hash");
    }

    fc_trace_destroy(&trace);
}

/* ======================================================================== */
/* Test: Tick cap terminal                                                   */
/* ======================================================================== */

static void test_tick_cap_terminal(void) {
    printf("test_tick_cap_terminal:\n");

    FcState state;
    fc_init(&state);
    fc_reset(&state, 42);

    int actions[FC_NUM_ACTION_HEADS] = {0, 0, 0, 0, 0};

    ASSERT_EQ(fc_is_terminal(&state), 0, "not terminal at start");

    /* Step to tick cap. check_terminal runs before tick++, so terminal
     * triggers when tick == FC_MAX_EPISODE_TICKS at check time. That means
     * we need tick to reach MAX_EPISODE_TICKS before check. Since tick++
     * happens after check, we set tick to MAX-1 and step once (check sees MAX-1,
     * not terminal, then tick++ → MAX), then step again (check sees MAX → terminal). */
    state.tick = FC_MAX_EPISODE_TICKS - 1;
    fc_step(&state, actions);
    ASSERT_EQ(fc_is_terminal(&state), 0, "not terminal one before cap");

    fc_step(&state, actions);
    ASSERT_EQ(fc_is_terminal(&state), 1, "terminal at tick cap");
    ASSERT_EQ(fc_terminal_code(&state), TERMINAL_TICK_CAP, "terminal code is TICK_CAP");
}

/* ======================================================================== */
/* Test: Move direction tables are consistent                                */
/* ======================================================================== */

static void test_move_direction_tables(void) {
    printf("test_move_direction_tables:\n");

    /* Idle has zero offset */
    ASSERT_EQ(FC_MOVE_DX[0], 0, "idle dx = 0");
    ASSERT_EQ(FC_MOVE_DY[0], 0, "idle dy = 0");

    /* Walk directions (1-8) have max offset of 1 */
    for (int i = 1; i <= 8; i++) {
        int adx = (FC_MOVE_DX[i] < 0) ? -FC_MOVE_DX[i] : FC_MOVE_DX[i];
        int ady = (FC_MOVE_DY[i] < 0) ? -FC_MOVE_DY[i] : FC_MOVE_DY[i];
        ASSERT(adx <= 1 && ady <= 1, "walk offset <= 1");
        ASSERT(adx + ady > 0, "walk offset nonzero");
    }

    /* Run directions (9-16) have max offset of 2 */
    for (int i = 9; i <= 16; i++) {
        int adx = (FC_MOVE_DX[i] < 0) ? -FC_MOVE_DX[i] : FC_MOVE_DX[i];
        int ady = (FC_MOVE_DY[i] < 0) ? -FC_MOVE_DY[i] : FC_MOVE_DY[i];
        int chebyshev = (adx > ady) ? adx : ady;
        ASSERT_EQ(chebyshev, 2, "run offset chebyshev = 2");
    }

    /* Dimensions match */
    ASSERT_EQ(FC_MOVE_DIM, 17, "MOVE_DIM = 17");
}

/* ======================================================================== */
/* Main                                                                      */
/* ======================================================================== */

int main(void) {
    printf("=== PR 1 Tests: Core State, Contracts, Determinism ===\n\n");

    test_contract_constants();
    test_state_init_reset();
    test_reset_determinism();
    test_rng_determinism();
    test_state_hash_determinism();
    test_state_hash_padding_independence();
    test_observation_output();
    test_reward_separation();
    test_npc_slot_ordering();
    test_npc_slot_overflow();
    test_render_entities();
    test_action_trace();
    test_tick_cap_terminal();
    test_move_direction_tables();

    printf("\n=== Results: %d/%d passed ===\n", tests_passed, tests_run);
    return (tests_passed == tests_run) ? 0 : 1;
}
