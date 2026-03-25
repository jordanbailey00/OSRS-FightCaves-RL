from __future__ import annotations

from typing import Any, Mapping

import gymnasium as gym
import numpy as np

from fight_caves_rl.contracts.terminal_codes import TerminalCode
from fight_caves_rl.curriculum.registry import build_curriculum
from fight_caves_rl.envs.observation_views import reconstruct_raw_observation_from_flat
from fight_caves_rl.envs.puffer_encoding import build_policy_action_space, build_policy_observation_space
from fight_caves_rl.envs_fast.fast_policy_encoding import pack_action_from_policy
from fight_caves_rl.envs_fast.fast_reward_adapter import FastRewardAdapter
from fight_caves_rl.native_runtime.loader import load_native_runtime
from fight_caves_rl.native_runtime.reset import NativeEpisodeConfig

_TERMINAL_REASON_BY_CODE = {
    int(TerminalCode.NONE): None,
    int(TerminalCode.PLAYER_DEATH): "player_death",
    int(TerminalCode.CAVE_COMPLETE): "cave_complete",
    int(TerminalCode.TICK_CAP): "max_tick_cap",
    int(TerminalCode.INVALID_STATE): "invalid_state",
    int(TerminalCode.ORACLE_ABORT): "oracle_abort",
}


class NativeFightCavesGymEnv(gym.Env[np.ndarray, np.ndarray]):
    metadata = {"render_modes": []}

    def __init__(
        self,
        *,
        env_config: Mapping[str, Any] | None = None,
        reward_config_id: str = "reward_sparse_v2",
        curriculum_config_id: str = "curriculum_disabled_v0",
        env_index: int = 0,
    ) -> None:
        super().__init__()
        env_settings = dict(env_config or {})
        self._reward_adapter = FastRewardAdapter.from_config_id(reward_config_id)
        self._reward_adapter.validate_supported()
        self._curriculum = build_curriculum(curriculum_config_id)
        self._env_settings = env_settings
        self._env_index = int(env_index)
        self._episodes_started = 0
        self._runtime = load_native_runtime()
        self.observation_space = build_policy_observation_space()
        self.action_space = build_policy_action_space()
        self.last_raw_observation: dict[str, Any] | None = None
        self.last_reset_info: dict[str, Any] | None = None
        self.last_step_info: dict[str, Any] | None = None
        self._episode_return = 0.0
        self._episode_length = 0
        self._prev_observation: np.ndarray | None = None

    def reset(
        self,
        *,
        seed: int | None = None,
        options: dict[str, Any] | None = None,
    ) -> tuple[np.ndarray, dict[str, float]]:
        super().reset(seed=seed)
        reset_options = dict(
            self._curriculum.reset_overrides(
                slot_index=self._env_index,
                episode_index=self._episodes_started,
            )
        )
        if options:
            reset_options.update(dict(options))
        config = NativeEpisodeConfig(
            seed=int(seed if seed is not None else reset_options.get("seed", self._episodes_started)),
            start_wave=int(reset_options.get("start_wave", self._env_settings.get("start_wave", 1))),
            ammo=int(reset_options.get("ammo", self._env_settings.get("ammo", 1000))),
            prayer_potions=int(
                reset_options.get("prayer_potions", self._env_settings.get("prayer_potions", 8))
            ),
            sharks=int(reset_options.get("sharks", self._env_settings.get("sharks", 20))),
        )
        result = self._runtime.reset_batch([config])
        observation = np.asarray(result.flat_observations[0], dtype=np.float32)
        raw_observation = reconstruct_raw_observation_from_flat(observation)
        self._episodes_started += 1
        self._episode_return = 0.0
        self._episode_length = 0
        self._prev_observation = np.array(observation, copy=True)
        self.last_raw_observation = raw_observation
        self.last_step_info = None
        self.last_reset_info = {
            "episode_state": dict(result.episode_states[0]),
        }
        return observation, {
            "episode_seed": float(raw_observation["episode_seed"]),
            "wave": float(raw_observation["wave"]["wave"]),
            "remaining": float(raw_observation["wave"]["remaining"]),
        }

    def step(
        self,
        action: np.ndarray | list[int] | tuple[int, ...],
    ) -> tuple[np.ndarray, float, bool, bool, dict[str, float]]:
        result = self._runtime.step_batch(pack_action_from_policy(action), slot_indices=[0])
        observation = np.asarray(result.flat_observations[0], dtype=np.float32)
        reward = float(self._reward_adapter.weight_batch(result.reward_features)[0])
        if self._prev_observation is not None and self._reward_adapter.has_proximity_shaping:
            reward += float(
                self._reward_adapter.proximity_shaping_batch(
                    self._prev_observation.reshape(1, -1),
                    observation.reshape(1, -1),
                )[0]
            )
        self._prev_observation = np.array(observation, copy=True)
        terminated = bool(result.terminated[0])
        truncated = bool(result.truncated[0])
        terminal_code = int(result.terminal_codes[0])
        raw_observation = reconstruct_raw_observation_from_flat(observation)
        self.last_raw_observation = raw_observation
        self._episode_return += reward
        self._episode_length += 1
        self.last_step_info = {
            "terminal_reason": _TERMINAL_REASON_BY_CODE.get(terminal_code),
            "episode_steps": int(result.episode_steps[0]),
            "action_result": {
                "action_applied": bool(result.action_accepted[0]),
                "rejection_reason": None if int(result.rejection_codes[0]) == 0 else "rejected",
            },
            "visible_targets": raw_observation["npcs"],
        }
        info = {
            "action_id": float(int(result.action_ids[0])),
            "visible_target_count": float(len(raw_observation["npcs"])),
            "wave": float(raw_observation["wave"]["wave"]),
            "remaining": float(raw_observation["wave"]["remaining"]),
            "reward": float(reward),
            "action_applied": float(int(bool(result.action_accepted[0]))),
            "rejection_reason_code": float(int(result.rejection_codes[0])),
            "terminal_reason_code": float(terminal_code),
            "episode_steps": float(int(result.episode_steps[0])),
            "episode_return": float(self._episode_return),
            "episode_length": float(self._episode_length),
            "terminated": float(int(terminated)),
            "truncated": float(int(truncated)),
        }
        return observation, reward, terminated, truncated, info

    def close(self) -> None:
        self._runtime.close()
