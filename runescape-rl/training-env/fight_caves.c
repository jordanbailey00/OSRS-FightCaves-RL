/*
 * fight_caves.c — Standalone entry point for testing without PufferLib.
 *
 * Compiled with: ./build.sh --local (debug) or ./build.sh --fast (optimized)
 * Runs N episodes with random actions and prints stats.
 */

#include "fight_caves.h"
#include <stdio.h>
#include <time.h>

int main(void) {
    FightCaves env = {0};
    env.num_agents = 1;
    env.observations = (float*)calloc(FC_PUFFER_OBS_SIZE, sizeof(float));
    env.actions = (float*)calloc(FC_PUFFER_NUM_ATNS, sizeof(float));
    env.rewards = (float*)calloc(1, sizeof(float));
    env.terminals = (float*)calloc(1, sizeof(float));

    /* Default reward weights */
    env.w_damage_dealt = 0.5f;
    env.w_attack_attempt = 0.0f;
    env.w_damage_taken = -0.5f;
    env.w_npc_kill = 3.0f;
    env.w_wave_clear = 10.0f;
    env.w_jad_damage = 2.0f;
    env.w_jad_kill = 50.0f;
    env.w_player_death = -20.0f;
    env.w_cave_complete = 100.0f;
    env.w_food_used = -0.05f;
    env.w_prayer_pot_used = -0.05f;
    env.w_correct_jad_prayer = 5.0f;
    env.w_wrong_jad_prayer = -10.0f;
    env.w_invalid_action = -0.1f;
    env.w_movement = 0.0f;
    env.w_idle = -0.01f;
    env.w_tick_penalty = -0.005f;

    fc_init(&env.state);

    srand((unsigned)time(NULL));
    int episodes = 100;
    int total_ticks = 0;
    float total_reward = 0;
    int max_wave = 0;

    printf("Running %d episodes with random actions...\n", episodes);
    clock_t start = clock();

    for (int ep = 0; ep < episodes; ep++) {
        c_reset(&env);
        int ep_ticks = 0;
        while (!env.terminals[0] && ep_ticks < 30000) {
            for (int h = 0; h < FC_PUFFER_NUM_ATNS; h++)
                env.actions[h] = (float)(rand() % 17);
            env.actions[0] = (rand() % 3 == 0) ? (float)(rand() % 17) : 0.0f;
            env.actions[1] = (rand() % 5 == 0) ? (float)(rand() % 9) : 0.0f;
            env.actions[2] = (rand() % 10 == 0) ? (float)(rand() % 5) : 0.0f;
            env.actions[3] = (rand() % 8 == 0) ? (float)(rand() % 3) : 0.0f;
            env.actions[4] = (rand() % 8 == 0) ? (float)(rand() % 2) : 0.0f;
            c_step(&env);
            ep_ticks++;
        }
        total_ticks += ep_ticks;
        total_reward += env.ep_return;
        if (env.state.current_wave > max_wave) max_wave = env.state.current_wave;
    }

    clock_t end = clock();
    double elapsed = (double)(end - start) / CLOCKS_PER_SEC;

    printf("Results:\n");
    printf("  Episodes:    %d\n", episodes);
    printf("  Total ticks: %d\n", total_ticks);
    printf("  SPS:         %.0f steps/sec\n", total_ticks / elapsed);
    printf("  Avg reward:  %.2f\n", total_reward / episodes);
    printf("  Max wave:    %d\n", max_wave);
    printf("  Time:        %.2fs\n", elapsed);
    printf("  Log: score=%.1f ep_return=%.1f wave=%.1f n=%.0f\n",
           env.log.score, env.log.episode_return, env.log.wave_reached, env.log.n);

    c_close(&env);
    free(env.observations);
    free(env.actions);
    free(env.rewards);
    free(env.terminals);
    return 0;
}
