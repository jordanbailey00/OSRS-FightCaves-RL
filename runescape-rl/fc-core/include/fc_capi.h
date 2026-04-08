#ifndef FC_CAPI_H
#define FC_CAPI_H

/*
 * fc_capi.h — Plain C API for shared library loading (ctypes/cffi).
 * No Python.h dependency.
 */

typedef struct FcEnvCtx FcEnvCtx;

/* Constants */
int fc_capi_obs_size(void);
int fc_capi_policy_obs_size(void);
int fc_capi_reward_features(void);
int fc_capi_action_mask_size(void);
int fc_capi_num_action_heads(void);
int fc_capi_move_dim(void);
int fc_capi_attack_dim(void);
int fc_capi_prayer_dim(void);
int fc_capi_eat_dim(void);
int fc_capi_drink_dim(void);
int fc_capi_move_target_x_dim(void);
int fc_capi_move_target_y_dim(void);

/* Lifecycle */
FcEnvCtx* fc_capi_create(void);
void fc_capi_destroy(FcEnvCtx* ctx);
void fc_capi_reset(FcEnvCtx* ctx, unsigned int seed);
int fc_capi_step(FcEnvCtx* ctx, const int actions[]);

/* Getters */
const float* fc_capi_get_obs(const FcEnvCtx* ctx);
float fc_capi_get_reward(const FcEnvCtx* ctx);
int fc_capi_get_terminal(const FcEnvCtx* ctx);

/* Batch */
void fc_capi_batch_step(FcEnvCtx** envs, int num_envs,
                        const int* all_actions,
                        float* all_obs,
                        float* all_rewards,
                        int* all_terminals);

#endif /* FC_CAPI_H */
