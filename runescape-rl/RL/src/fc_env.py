"""
Fight Caves PufferLib environment — ctypes bridge to libfc_capi.so.

No environment semantics in Python. Reward features are transported in the
obs buffer but separated from policy input.

All contract constants are read from the C library at import time.

PR7: Uses batched C path (fc_capi_batch_step_flat) instead of per-env Python loop.
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
# C function signatures — batch interface (PR7)                              #
# ======================================================================== #

_BatchPtr = ctypes.c_void_p
_FloatPtr = ctypes.POINTER(ctypes.c_float)
_IntPtr = ctypes.POINTER(ctypes.c_int)

_lib.fc_capi_batch_create.restype = _BatchPtr
_lib.fc_capi_batch_create.argtypes = [ctypes.c_int]

_lib.fc_capi_batch_destroy.restype = None
_lib.fc_capi_batch_destroy.argtypes = [_BatchPtr]

_lib.fc_capi_batch_reset.restype = None
_lib.fc_capi_batch_reset.argtypes = [_BatchPtr, ctypes.c_uint]

_lib.fc_capi_batch_step_flat.restype = None
_lib.fc_capi_batch_step_flat.argtypes = [_BatchPtr, _IntPtr, _FloatPtr, _FloatPtr, _IntPtr]

_lib.fc_capi_batch_get_obs.restype = None
_lib.fc_capi_batch_get_obs.argtypes = [_BatchPtr, _FloatPtr]

# ======================================================================== #
# Vectorized environment (batched C path)                                    #
# ======================================================================== #

class FightCavesVecEnv:
    """Vectorized Fight Caves environment using batched C backend.

    Obs buffer layout per env: [policy_obs (126)] [reward_features (16)] [action_mask (36)]
    Policy receives only policy_obs. Reward features are for shaping/logging.

    PR7: Single C call per step (fc_capi_batch_step_flat) replaces per-env Python loop.
    """

    def __init__(self, num_envs=1, seed=42):
        self.num_envs = num_envs
        self._batch = _lib.fc_capi_batch_create(num_envs)

        # Allocate contiguous numpy arrays for zero-copy C interop
        self.observations = np.zeros((num_envs, FC_OBS_SIZE), dtype=np.float32)
        self.rewards = np.zeros(num_envs, dtype=np.float32)
        self.terminals = np.zeros(num_envs, dtype=np.int32)

        # Reset and read initial obs
        _lib.fc_capi_batch_reset(self._batch, seed)
        _lib.fc_capi_batch_get_obs(
            self._batch,
            self.observations.ctypes.data_as(_FloatPtr),
        )

    def reset(self, seed=None):
        s = seed if seed is not None else 42
        _lib.fc_capi_batch_reset(self._batch, s)
        _lib.fc_capi_batch_get_obs(
            self._batch,
            self.observations.ctypes.data_as(_FloatPtr),
        )
        self.rewards[:] = 0.0
        self.terminals[:] = 0
        return self.observations

    def step(self, actions):
        """Step all environments in one C call.

        Args:
            actions: int32 array of shape (num_envs, FC_NUM_ACTION_HEADS)

        Returns:
            observations, rewards, terminals (numpy arrays)
        """
        actions = np.ascontiguousarray(actions, dtype=np.int32).reshape(
            self.num_envs, FC_NUM_ACTION_HEADS
        )

        _lib.fc_capi_batch_step_flat(
            self._batch,
            actions.ctypes.data_as(_IntPtr),
            self.observations.ctypes.data_as(_FloatPtr),
            self.rewards.ctypes.data_as(ctypes.POINTER(ctypes.c_float)),
            self.terminals.ctypes.data_as(_IntPtr),
        )

        return self.observations, self.rewards, self.terminals

    def close(self):
        if self._batch:
            _lib.fc_capi_batch_destroy(self._batch)
            self._batch = None

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

    idle = np.zeros((num_envs, FC_NUM_ACTION_HEADS), dtype=np.int32)
    for _ in range(num_steps):
        obs, _, _ = env.step(idle)

    # Check that envs diverged
    player_hp = obs[:, 0]
    unique_hp = len(set(player_hp.tolist()))
    assert unique_hp > 1, "vectorized envs should diverge with different seeds"

    env.close()
    print(f"Vectorized test passed: {num_envs} envs diverged correctly")
    return True


def test_batch_independence(num_envs=8):
    """Test that batch envs produce identical results to individual envs."""
    seed = 777

    # Batch path
    batch_env = FightCavesVecEnv(num_envs=num_envs, seed=seed)
    batch_env.reset(seed=seed)
    actions = np.zeros((num_envs, FC_NUM_ACTION_HEADS), dtype=np.int32)
    actions[:, 1] = 1  # attack slot 0
    for _ in range(20):
        batch_obs, batch_rwd, batch_term = batch_env.step(actions)

    # Single env path (using batch of 1 for each)
    for i in range(num_envs):
        single = FightCavesVecEnv(num_envs=1, seed=seed + i)
        single.reset(seed=seed + i)
        act1 = np.zeros((1, FC_NUM_ACTION_HEADS), dtype=np.int32)
        act1[:, 1] = 1
        for _ in range(20):
            s_obs, s_rwd, s_term = single.step(act1)
        # Compare
        assert np.allclose(batch_obs[i], s_obs[0], atol=1e-6), \
            f"env {i} obs mismatch"
        single.close()

    batch_env.close()
    print(f"Batch independence test passed: {num_envs} envs match individual")
    return True


def test_sps(num_envs=64, duration=5.0):
    """Measure steps per second (batched C path)."""
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


def benchmark_scaling(duration=5.0):
    """Benchmark SPS across env counts."""
    print("\n=== SPS Scaling Benchmark ===")
    results = {}
    for n in [1, 16, 64, 256, 1024]:
        sps = test_sps(num_envs=n, duration=duration)
        results[n] = sps
        per_env = sps / n
        print(f"  {n:>5d} envs: {sps:>12,.0f} SPS  ({per_env:>8,.0f} per-env)")
    return results


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
    test_batch_independence()
    print()
    benchmark_scaling()
