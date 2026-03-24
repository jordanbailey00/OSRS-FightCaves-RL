"""
Fight Caves PufferLib environment — ctypes bridge to libfc_capi.so.

No environment semantics in Python. Reward features are transported in the
obs buffer but separated from policy input.

All contract constants are read from the C library at import time.
"""

import ctypes
import os
import time
import numpy as np

# ======================================================================== #
# Load shared library                                                       #
# ======================================================================== #

_HERE = os.path.dirname(os.path.abspath(__file__))
_ROOT = os.path.dirname(os.path.dirname(_HERE))
_LIB_PATH = os.path.join(_ROOT, "build", "training-env", "libfc_capi.so")

if not os.path.exists(_LIB_PATH):
    raise ImportError(
        f"libfc_capi.so not found at {_LIB_PATH}. "
        "Run: cmake -B build -DCMAKE_BUILD_TYPE=Release && cmake --build build"
    )

_lib = ctypes.CDLL(_LIB_PATH)

# ======================================================================== #
# Read contract constants from C (no magic numbers in Python)               #
# ======================================================================== #

def _cint(name):
    fn = getattr(_lib, name)
    fn.restype = ctypes.c_int
    fn.argtypes = []
    return fn()

FC_OBS_SIZE = _cint("fc_capi_obs_size")
FC_POLICY_OBS_SIZE = _cint("fc_capi_policy_obs_size")
FC_REWARD_FEATURES = _cint("fc_capi_reward_features")
FC_ACTION_MASK_SIZE = _cint("fc_capi_action_mask_size")
FC_NUM_ACTION_HEADS = _cint("fc_capi_num_action_heads")
FC_ACTION_DIMS = [
    _cint("fc_capi_move_dim"),
    _cint("fc_capi_attack_dim"),
    _cint("fc_capi_prayer_dim"),
    _cint("fc_capi_eat_dim"),
    _cint("fc_capi_drink_dim"),
]

# Verify consistency
assert FC_OBS_SIZE == FC_POLICY_OBS_SIZE + FC_REWARD_FEATURES + FC_ACTION_MASK_SIZE
assert FC_NUM_ACTION_HEADS == len(FC_ACTION_DIMS)
assert sum(FC_ACTION_DIMS) == FC_ACTION_MASK_SIZE

# ======================================================================== #
# C function signatures                                                     #
# ======================================================================== #

_EnvPtr = ctypes.c_void_p

_lib.fc_capi_create.restype = _EnvPtr
_lib.fc_capi_create.argtypes = []

_lib.fc_capi_destroy.restype = None
_lib.fc_capi_destroy.argtypes = [_EnvPtr]

_lib.fc_capi_reset.restype = None
_lib.fc_capi_reset.argtypes = [_EnvPtr, ctypes.c_uint]

_lib.fc_capi_step.restype = ctypes.c_int
_lib.fc_capi_step.argtypes = [_EnvPtr, ctypes.POINTER(ctypes.c_int)]

_lib.fc_capi_get_obs.restype = ctypes.POINTER(ctypes.c_float)
_lib.fc_capi_get_obs.argtypes = [_EnvPtr]

_lib.fc_capi_get_reward.restype = ctypes.c_float
_lib.fc_capi_get_reward.argtypes = [_EnvPtr]

_lib.fc_capi_get_terminal.restype = ctypes.c_int
_lib.fc_capi_get_terminal.argtypes = [_EnvPtr]

# ======================================================================== #
# Vectorized environment                                                    #
# ======================================================================== #

class FightCavesVecEnv:
    """Vectorized Fight Caves environment using ctypes C backend.

    Obs buffer layout per env: [policy_obs (126)] [reward_features (16)] [action_mask (36)]
    Policy receives only policy_obs. Reward features are for shaping/logging.
    """

    def __init__(self, num_envs=1, seed=42):
        self.num_envs = num_envs
        self._envs = []
        for i in range(num_envs):
            env = _lib.fc_capi_create()
            _lib.fc_capi_reset(env, seed + i)
            self._envs.append(env)

        # Allocate numpy arrays for batch interface
        self.observations = np.zeros((num_envs, FC_OBS_SIZE), dtype=np.float32)
        self.rewards = np.zeros(num_envs, dtype=np.float32)
        self.terminals = np.zeros(num_envs, dtype=np.int32)

        # Read initial observations
        self._read_all_obs()

    def _read_obs(self, env_idx):
        """Copy obs from C env into numpy array."""
        obs_ptr = _lib.fc_capi_get_obs(self._envs[env_idx])
        ctypes.memmove(
            self.observations[env_idx:env_idx+1].ctypes.data,
            obs_ptr,
            FC_OBS_SIZE * 4,  # float32 = 4 bytes
        )

    def _read_all_obs(self):
        for i in range(self.num_envs):
            self._read_obs(i)

    def reset(self, seed=None):
        for i in range(self.num_envs):
            s = (seed + i) if seed is not None else (42 + i)
            _lib.fc_capi_reset(self._envs[i], s)
        self._read_all_obs()
        self.rewards[:] = 0.0
        self.terminals[:] = 0
        return self.observations

    def step(self, actions):
        """Step all environments.

        Args:
            actions: int32 array of shape (num_envs, FC_NUM_ACTION_HEADS)

        Returns:
            observations, rewards, terminals (numpy arrays)
        """
        actions = np.asarray(actions, dtype=np.int32).reshape(self.num_envs, FC_NUM_ACTION_HEADS)

        for i in range(self.num_envs):
            act_arr = (ctypes.c_int * FC_NUM_ACTION_HEADS)(*actions[i])
            self.terminals[i] = _lib.fc_capi_step(self._envs[i], act_arr)
            self.rewards[i] = _lib.fc_capi_get_reward(self._envs[i])
            self._read_obs(i)

        return self.observations, self.rewards, self.terminals

    def close(self):
        for env in self._envs:
            _lib.fc_capi_destroy(env)
        self._envs = []

    @staticmethod
    def split_obs(obs):
        """Split observation buffer into (policy_obs, reward_features, action_mask)."""
        policy_obs = obs[..., :FC_POLICY_OBS_SIZE]
        reward_features = obs[..., FC_POLICY_OBS_SIZE:FC_POLICY_OBS_SIZE + FC_REWARD_FEATURES]
        action_mask = obs[..., FC_POLICY_OBS_SIZE + FC_REWARD_FEATURES:]
        return policy_obs, reward_features, action_mask


# ======================================================================== #
# Tests                                                                      #
# ======================================================================== #

def test_smoke(num_envs=4, num_steps=100):
    """Basic smoke test: create, reset, step, check shapes."""
    env = FightCavesVecEnv(num_envs=num_envs, seed=42)
    obs = env.reset()

    assert obs.shape == (num_envs, FC_OBS_SIZE), f"obs shape: {obs.shape}"
    assert env.rewards.shape == (num_envs,)
    assert env.terminals.shape == (num_envs,)

    # Check obs is normalized [0, 1]
    assert obs.min() >= -0.01, f"obs min: {obs.min()}"
    assert obs.max() <= 1.01, f"obs max: {obs.max()}"

    # Split obs
    policy_obs, reward_features, action_mask = FightCavesVecEnv.split_obs(obs)
    assert policy_obs.shape == (num_envs, FC_POLICY_OBS_SIZE)
    assert reward_features.shape == (num_envs, FC_REWARD_FEATURES)
    assert action_mask.shape == (num_envs, FC_ACTION_MASK_SIZE)

    # Mask values are 0 or 1
    unique_mask = set(np.unique(action_mask).tolist())
    assert unique_mask.issubset({0.0, 1.0}), f"mask values: {unique_mask}"

    # Step with random actions
    episodes_completed = 0
    for _ in range(num_steps):
        actions = np.zeros((num_envs, FC_NUM_ACTION_HEADS), dtype=np.int32)
        for h in range(FC_NUM_ACTION_HEADS):
            actions[:, h] = np.random.randint(0, FC_ACTION_DIMS[h], size=num_envs)
        obs, rewards, terminals = env.step(actions)

        assert obs.shape == (num_envs, FC_OBS_SIZE)
        assert rewards.shape == (num_envs,)
        episodes_completed += terminals.sum()

    env.close()
    print(f"Smoke test passed: {num_envs} envs, {num_steps} steps, {episodes_completed} episodes completed")
    return True


def test_vectorized(num_envs=16, num_steps=50):
    """Test that vectorized envs are independent (different seeds → different states)."""
    env = FightCavesVecEnv(num_envs=num_envs, seed=100)
    obs = env.reset(seed=100)

    # Different envs should have different initial observations (different rotations)
    # At minimum, env[0] and env[1] should differ due to seed offset
    idle = np.zeros((num_envs, FC_NUM_ACTION_HEADS), dtype=np.int32)
    for _ in range(num_steps):
        obs, _, _ = env.step(idle)

    # Check that envs diverged (different RNG seeds → different NPC behavior)
    # Compare player HP across envs — with different seeds, NPC attacks land differently
    player_hp = obs[:, 0]  # FC_OBS_PLAYER_HP is index 0
    unique_hp = len(set(player_hp.tolist()))
    assert unique_hp > 1, "vectorized envs should diverge with different seeds"

    env.close()
    print(f"Vectorized test passed: {num_envs} envs diverged correctly")
    return True


def test_sps(num_envs=64, duration=5.0):
    """Measure steps per second (Python↔C round-trip)."""
    env = FightCavesVecEnv(num_envs=num_envs, seed=42)
    env.reset()

    # Pre-generate random actions
    cache_size = 1024
    action_cache = np.zeros((cache_size, num_envs, FC_NUM_ACTION_HEADS), dtype=np.int32)
    for i in range(cache_size):
        for h in range(FC_NUM_ACTION_HEADS):
            action_cache[i, :, h] = np.random.randint(0, FC_ACTION_DIMS[h], size=num_envs)

    tick = 0
    start = time.time()
    while time.time() - start < duration:
        env.step(action_cache[tick % cache_size])
        tick += 1
    elapsed = time.time() - start

    sps = num_envs * tick / elapsed
    print(f"SPS: {sps:,.0f} ({num_envs} envs, {tick} ticks, {elapsed:.1f}s)")

    env.close()
    return sps


def test_constants():
    """Verify contract constants match expected values from fc_contracts.h."""
    assert FC_OBS_SIZE == 178, f"FC_OBS_SIZE={FC_OBS_SIZE}"
    assert FC_POLICY_OBS_SIZE == 126, f"FC_POLICY_OBS_SIZE={FC_POLICY_OBS_SIZE}"
    assert FC_REWARD_FEATURES == 16, f"FC_REWARD_FEATURES={FC_REWARD_FEATURES}"
    assert FC_ACTION_MASK_SIZE == 36, f"FC_ACTION_MASK_SIZE={FC_ACTION_MASK_SIZE}"
    assert FC_NUM_ACTION_HEADS == 5, f"FC_NUM_ACTION_HEADS={FC_NUM_ACTION_HEADS}"
    assert FC_ACTION_DIMS == [17, 9, 5, 3, 2], f"FC_ACTION_DIMS={FC_ACTION_DIMS}"
    print("Constants test passed: all match fc_contracts.h")
    return True


if __name__ == "__main__":
    test_constants()
    test_smoke()
    test_vectorized()
    test_sps()
