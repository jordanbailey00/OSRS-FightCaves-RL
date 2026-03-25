from __future__ import annotations

from collections.abc import Callable, Mapping, Sequence
from dataclasses import dataclass, field
from typing import Any

import numpy as np
import pufferlib
import pufferlib.vector

from fight_caves_rl.bridge.contracts import HeadlessBootstrapConfig
from fight_caves_rl.contracts.mechanics_contract import FIGHT_CAVES_V2_MECHANICS_CONTRACT
from fight_caves_rl.contracts.reward_feature_schema import REWARD_FEATURE_SCHEMA
from fight_caves_rl.contracts.terminal_codes import TerminalCode, TERMINAL_CODE_SCHEMA
from fight_caves_rl.envs.shared_memory_transport import (
    INFO_PAYLOAD_MODE_MINIMAL,
    INFO_PAYLOAD_MODES,
)
from fight_caves_rl.envs_fast.fast_policy_encoding import pack_joint_actions
from fight_caves_rl.envs_fast.fast_reward_adapter import FastRewardAdapter
from fight_caves_rl.envs_fast.fast_spaces import (
    FAST_OBSERVATION_FEATURE_COUNT,
    build_fast_action_space,
    build_fast_observation_space,
)
from fight_caves_rl.native_runtime.loader import load_native_runtime
from fight_caves_rl.native_runtime.reset import NativeEpisodeConfig, NativeResetBatchResult
from fight_caves_rl.native_runtime.step import NativeStepBatchResult

ResetOptionsProvider = Callable[[int, int], Mapping[str, object] | None]

_TERMINAL_REASON_BY_CODE = {
    int(TerminalCode.NONE): None,
    int(TerminalCode.PLAYER_DEATH): "player_death",
    int(TerminalCode.CAVE_COMPLETE): "cave_complete",
    int(TerminalCode.TICK_CAP): "max_tick_cap",
    int(TerminalCode.INVALID_STATE): "invalid_state",
    int(TerminalCode.ORACLE_ABORT): "oracle_abort",
}


@dataclass(frozen=True)
class NativeKernelVecEnvConfig:
    env_count: int
    account_name_prefix: str = "rl_native_vecenv"
    start_wave: int = 1
    ammo: int = 1000
    prayer_potions: int = 8
    sharks: int = 20
    tick_cap: int = 20_000
    include_future_leakage: bool = False
    info_payload_mode: str = INFO_PAYLOAD_MODE_MINIMAL
    instrumentation_enabled: bool = False
    bootstrap: HeadlessBootstrapConfig = field(default_factory=HeadlessBootstrapConfig)
    reset_options_provider: ResetOptionsProvider | None = None


class NativeKernelVecEnv:
    reset = pufferlib.vector.reset
    step = pufferlib.vector.step

    def __init__(
        self,
        config: NativeKernelVecEnvConfig,
        *,
        reward_adapter: FastRewardAdapter,
    ) -> None:
        if int(config.env_count) <= 0:
            raise ValueError(f"env_count must be > 0, got {config.env_count}.")
        if str(config.info_payload_mode) not in INFO_PAYLOAD_MODES:
            raise ValueError(
                f"Unsupported info_payload_mode: {config.info_payload_mode!r}. "
                f"Expected one of {INFO_PAYLOAD_MODES!r}."
            )
        if str(config.info_payload_mode) != INFO_PAYLOAD_MODE_MINIMAL:
            raise ValueError(
                "The native preview vecenv only supports info_payload_mode='minimal'. "
                "It must not reconstruct structured semantics in Python."
            )
        if bool(config.include_future_leakage):
            raise ValueError("native preview vecenv does not support future-leakage observations.")
        reward_adapter.validate_supported()

        self.config = config
        self._reward_adapter = reward_adapter
        self._runtime = load_native_runtime()
        self._descriptor = self._runtime.descriptor_bundle()
        self._reward_feature_count = int(self._descriptor["summary"]["reward_feature_count"])
        self._observation_feature_count = int(
            self._descriptor["summary"]["flat_observation_feature_count"]
        )
        self._validate_descriptor()

        self.driver_env = self
        self.agents_per_batch = int(config.env_count)
        self.num_agents = self.agents_per_batch
        self.single_observation_space = build_fast_observation_space()
        self.single_action_space = build_fast_action_space()
        self.action_space = pufferlib.spaces.joint_space(
            self.single_action_space,
            self.agents_per_batch,
        )
        self.observation_space = pufferlib.spaces.joint_space(
            self.single_observation_space,
            self.agents_per_batch,
        )
        self.emulated = {
            "observation_dtype": self.single_observation_space.dtype,
            "emulated_observation_dtype": self.single_observation_space.dtype,
        }
        pufferlib.set_buffers(self)
        self.agent_ids = np.arange(self.num_agents)
        self.initialized = False
        self.flag = pufferlib.vector.RESET
        self.infos: list[dict[str, Any]] = []
        self._seed_base: int | None = None
        self._episodes_started = np.zeros(self.num_agents, dtype=np.int64)
        self._episode_returns = np.zeros(self.num_agents, dtype=np.float32)
        self._episode_lengths = np.zeros(self.num_agents, dtype=np.int32)
        self._minimal_infos = tuple({} for _ in range(self.num_agents))
        self._all_slot_indices = tuple(range(self.num_agents))
        self._prev_observations: np.ndarray | None = (
            np.zeros((self.num_agents, self._observation_feature_count), dtype=np.float32)
            if reward_adapter.has_proximity_shaping
            else None
        )

    @property
    def num_envs(self) -> int:
        return self.agents_per_batch

    @property
    def episode_counts(self) -> np.ndarray:
        return self._episodes_started.copy()

    def topology_snapshot(self) -> dict[str, Any]:
        return {
            "backend": "embedded",
            "env_backend": "native_preview",
            "transport_mode": "embedded_native_ctypes",
            "worker_count": 1,
            "worker_env_counts": [int(self.num_agents)],
            "info_payload_mode": str(self.config.info_payload_mode),
        }

    def instrumentation_snapshot(self) -> dict[str, dict[str, float | int]]:
        return {}

    def reset_instrumentation(self) -> None:
        return None

    def async_reset(self, seed: int | None = None) -> None:
        self.flag = pufferlib.vector.RECV
        self._seed_base = None if seed is None else int(seed)
        self._episodes_started.fill(0)
        self._episode_returns.fill(0.0)
        self._episode_lengths.fill(0)
        response = self._reset_slots(self._all_slot_indices)
        self._apply_reset_response(response)
        self._record_episode_starts(self._all_slot_indices)
        self.infos = list(self._minimal_infos)

    def send(self, actions: np.ndarray) -> None:
        if not actions.flags.contiguous:
            actions = np.ascontiguousarray(actions)
        actions = pufferlib.vector.send_precheck(self, actions)

        done_mask = self.terminals | self.truncations
        if not done_mask.any():
            step_response = self._step_slots(self._all_slot_indices, actions)
            self._apply_step_response(step_response, joint_actions=actions)
            self.infos = self._minimal_infos
            return

        done_indices = tuple(int(i) for i in np.flatnonzero(done_mask))
        self.infos = self._build_episode_end_infos(done_indices)
        reset_response = self._reset_slots(done_indices)
        self._apply_reset_response(reset_response)
        self._record_episode_starts(done_indices)
        active_indices = tuple(i for i in range(self.num_agents) if i not in done_indices)
        if active_indices:
            step_response = self._step_slots(active_indices, actions[np.asarray(active_indices)])
            self._apply_step_response(step_response, joint_actions=actions)

    def recv(self):
        pufferlib.vector.recv_precheck(self)
        return (
            self.observations,
            self.rewards,
            self.terminals,
            self.truncations,
            self.teacher_actions,
            self.infos,
            self.agent_ids,
            self.masks,
        )

    def notify(self) -> None:
        return None

    def close(self) -> None:
        self._runtime.close()

    def _validate_descriptor(self) -> None:
        summary = self._descriptor["summary"]
        contracts = self._descriptor["contracts"]
        terminal_contract = contracts["terminal_code_schema"]["contract"]
        if summary["action_schema_id"] != FIGHT_CAVES_V2_MECHANICS_CONTRACT.action_schema_id:
            raise RuntimeError("native action schema id drifted from the frozen mechanics contract")
        if int(summary["action_schema_version"]) != int(
            FIGHT_CAVES_V2_MECHANICS_CONTRACT.action_schema_version
        ):
            raise RuntimeError(
                "native action schema version drifted from the frozen mechanics contract"
            )
        if summary["reward_feature_schema_id"] != REWARD_FEATURE_SCHEMA.contract_id:
            raise RuntimeError("native reward feature schema id drifted from the frozen contract")
        if int(summary["reward_feature_schema_version"]) != int(
            REWARD_FEATURE_SCHEMA.version
        ):
            raise RuntimeError(
                "native reward feature schema version drifted from the frozen contract"
            )
        if self._observation_feature_count != FAST_OBSERVATION_FEATURE_COUNT:
            raise RuntimeError("native flat observation count drifted from the RL fast space")
        if terminal_contract["contract_id"] != TERMINAL_CODE_SCHEMA.contract_id:
            raise RuntimeError("native terminal schema id drifted from the frozen contract")
        if int(terminal_contract["version"]) != int(TERMINAL_CODE_SCHEMA.version):
            raise RuntimeError("native terminal schema version drifted from the frozen contract")

    def _reset_slots(self, slot_indices: Sequence[int]) -> NativeResetBatchResult:
        return self._runtime.reset_batch(self._episode_configs(slot_indices))

    def _step_slots(self, slot_indices: Sequence[int], actions: np.ndarray) -> NativeStepBatchResult:
        packed_actions = pack_joint_actions(actions)
        return self._runtime.step_batch(
            packed_actions,
            slot_indices=np.asarray(slot_indices, dtype=np.int32),
        )

    def _episode_configs(self, slot_indices: Sequence[int]) -> list[NativeEpisodeConfig]:
        provider = self.config.reset_options_provider
        seeds = self._allocate_seeds(slot_indices)
        configs: list[NativeEpisodeConfig] = []
        for position, slot_index in enumerate(slot_indices):
            options: dict[str, object] = {}
            if provider is not None:
                provided = provider(
                    slot_index=int(slot_index),
                    episode_index=int(self._episodes_started[int(slot_index)]),
                )
                if provided is not None:
                    options = dict(provided)
            seed = (
                int(seeds[position])
                if seeds is not None
                else int(
                    options.get(
                        "seed",
                        int(slot_index)
                        + int(self._episodes_started[int(slot_index)]) * self.num_agents,
                    )
                )
            )
            configs.append(
                NativeEpisodeConfig(
                    seed=seed,
                    start_wave=int(options.get("start_wave", self.config.start_wave)),
                    ammo=int(options.get("ammo", self.config.ammo)),
                    prayer_potions=int(options.get("prayer_potions", self.config.prayer_potions)),
                    sharks=int(options.get("sharks", self.config.sharks)),
                )
            )
        return configs

    def _allocate_seeds(self, slot_indices: Sequence[int]) -> list[int] | None:
        if self._seed_base is None:
            return None
        stride = self.num_agents
        return [
            int(self._seed_base) + int(slot_index) + int(self._episodes_started[int(slot_index)]) * stride
            for slot_index in slot_indices
        ]

    def _build_episode_end_infos(self, done_indices: Sequence[int]) -> tuple[dict[str, Any], ...]:
        infos: list[dict[str, Any]] = list(self._minimal_infos)
        for i in done_indices:
            terminal_code = int(self._last_terminal_codes[i]) if hasattr(self, "_last_terminal_codes") else 0
            info: dict[str, Any] = {
                "episode_return": float(self._episode_returns[i]),
                "episode_length": int(self._episode_lengths[i]),
                "terminal_reason": _TERMINAL_REASON_BY_CODE.get(terminal_code),
            }
            if self.observations.shape[1] > 26:
                info["wave_reached"] = int(self.observations[i, 26])
            infos[i] = info
        return tuple(infos)

    def _record_episode_starts(self, slot_indices: Sequence[int]) -> None:
        for slot_index in slot_indices:
            slot = int(slot_index)
            self._episodes_started[slot] += 1
            self._episode_returns[slot] = 0.0
            self._episode_lengths[slot] = 0

    def _apply_reset_response(self, response: NativeResetBatchResult) -> None:
        slot_indices = np.asarray(response.slot_indices, dtype=np.int32)
        observations = np.asarray(response.flat_observations, dtype=np.float32).reshape(
            len(slot_indices),
            self._observation_feature_count,
        )
        self.observations[slot_indices] = observations
        self.rewards[slot_indices] = 0.0
        self.terminals[slot_indices] = False
        self.truncations[slot_indices] = False
        self.teacher_actions[slot_indices] = 0
        self.masks[slot_indices] = True
        if self._prev_observations is not None:
            self._prev_observations[slot_indices] = observations
        self._last_terminal_codes = np.zeros(self.num_agents, dtype=np.int32)

    def _apply_step_response(
        self,
        response: NativeStepBatchResult,
        *,
        joint_actions: np.ndarray,
    ) -> None:
        slot_indices = np.asarray(response.slot_indices, dtype=np.int32)
        observations = np.asarray(response.flat_observations, dtype=np.float32).reshape(
            len(slot_indices),
            self._observation_feature_count,
        )
        reward_features = np.asarray(response.reward_features, dtype=np.float32).reshape(
            len(slot_indices),
            self._reward_feature_count,
        )
        rewards = self._reward_adapter.weight_batch(reward_features)
        if self._prev_observations is not None:
            rewards += self._reward_adapter.proximity_shaping_batch(
                self._prev_observations[slot_indices],
                observations,
            )
        self.observations[slot_indices] = observations
        self.rewards[slot_indices] = rewards
        self.terminals[slot_indices] = np.asarray(response.terminated, dtype=np.bool_)
        self.truncations[slot_indices] = np.asarray(response.truncated, dtype=np.bool_)
        self.masks[slot_indices] = True
        self.actions[slot_indices] = joint_actions[slot_indices]
        self._episode_returns[slot_indices] += rewards
        self._episode_lengths[slot_indices] += 1
        if self._prev_observations is not None:
            self._prev_observations[slot_indices] = observations
        if not hasattr(self, "_last_terminal_codes"):
            self._last_terminal_codes = np.zeros(self.num_agents, dtype=np.int32)
        self._last_terminal_codes[slot_indices] = np.asarray(response.terminal_codes, dtype=np.int32)
