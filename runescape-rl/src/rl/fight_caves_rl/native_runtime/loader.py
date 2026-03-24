from __future__ import annotations

import ctypes
import json
from pathlib import Path
from typing import Any

import numpy as np

from fight_caves_rl.envs.puffer_encoding import POLICY_NPC_ID_TO_INDEX
from fight_caves_rl.native_runtime.build import NativeRuntimeBuildConfig, build_native_runtime
from fight_caves_rl.native_runtime.constants import (
    ENV_BACKEND_NATIVE_PREVIEW,
    NATIVE_CORE_TRACE_IDS,
    NATIVE_RUNTIME_ABI_VERSION,
)
from fight_caves_rl.native_runtime.reset import (
    NativeEpisodeConfig,
    NativeResetBatchResult,
    NativeResetSlotState,
)
from fight_caves_rl.native_runtime.step import (
    JAD_HIT_RESOLVE_OUTCOME_BY_CODE,
    NativeSlotDebugState,
    NativeStepBatchResult,
    NativeVisibleNpcConfig,
)


class _NativeEpisodeConfigC(ctypes.Structure):
    _fields_ = [
        ("seed", ctypes.c_int64),
        ("start_wave", ctypes.c_int),
        ("ammo", ctypes.c_int),
        ("prayer_potions", ctypes.c_int),
        ("sharks", ctypes.c_int),
    ]


class _NativeSlotDebugStateC(ctypes.Structure):
    _fields_ = [
        ("tile_x", ctypes.c_int),
        ("tile_y", ctypes.c_int),
        ("tile_level", ctypes.c_int),
        ("hitpoints_current", ctypes.c_int),
        ("prayer_current", ctypes.c_int),
        ("run_energy", ctypes.c_int),
        ("running", ctypes.c_int),
        ("protect_from_magic", ctypes.c_int),
        ("protect_from_missiles", ctypes.c_int),
        ("protect_from_melee", ctypes.c_int),
        ("attack_locked", ctypes.c_int),
        ("food_locked", ctypes.c_int),
        ("drink_locked", ctypes.c_int),
        ("combo_locked", ctypes.c_int),
        ("busy_locked", ctypes.c_int),
        ("sharks", ctypes.c_int),
        ("prayer_potion_dose_count", ctypes.c_int),
        ("ammo", ctypes.c_int),
    ]


class _NativeVisibleTargetC(ctypes.Structure):
    _fields_ = [
        ("listed", ctypes.c_int),
        ("currently_visible", ctypes.c_int),
        ("npc_index", ctypes.c_int),
        ("id_code", ctypes.c_int),
        ("tile_x", ctypes.c_int),
        ("tile_y", ctypes.c_int),
        ("tile_level", ctypes.c_int),
        ("hitpoints_current", ctypes.c_int),
        ("hitpoints_max", ctypes.c_int),
        ("hidden", ctypes.c_int),
        ("dead", ctypes.c_int),
        ("under_attack", ctypes.c_int),
        ("jad_telegraph_state", ctypes.c_int),
    ]


class NativeRuntimeError(RuntimeError):
    pass


class NativeRuntimeHandle:
    def __init__(self, *, library_path: Path, library: ctypes.CDLL, handle: int) -> None:
        self.library_path = library_path
        self._library = library
        self._handle = handle
        self._closed = False
        self._descriptor_bundle_cache: dict[str, Any] | None = None

    @property
    def abi_version(self) -> int:
        return int(self._library.fc_native_abi_version())

    @property
    def runtime_version(self) -> str:
        return _decode_c_string(self._library.fc_native_runtime_version_string())

    @property
    def descriptor_count(self) -> int:
        self._require_open()
        count = int(self._library.fc_native_runtime_descriptor_count(self._handle))
        if count < 0:
            raise NativeRuntimeError(_last_error(self._library))
        return count

    @property
    def closed(self) -> bool:
        return self._closed

    def descriptor_bundle(self) -> dict[str, Any]:
        self._require_open()
        if self._descriptor_bundle_cache is None:
            payload = self._library.fc_native_runtime_descriptor_bundle_json(self._handle)
            if not payload:
                raise NativeRuntimeError(_last_error(self._library))
            self._descriptor_bundle_cache = json.loads(_decode_c_string(payload))
        return self._descriptor_bundle_cache

    def reset_batch(
        self,
        configs: list[NativeEpisodeConfig] | tuple[NativeEpisodeConfig, ...],
    ) -> NativeResetBatchResult:
        self._require_open()
        config_array_type = _NativeEpisodeConfigC * len(configs)
        c_configs = config_array_type(
            *[
                _NativeEpisodeConfigC(
                    seed=int(config.seed),
                    start_wave=int(config.start_wave),
                    ammo=int(config.ammo),
                    prayer_potions=int(config.prayer_potions),
                    sharks=int(config.sharks),
                )
                for config in configs
            ]
        )
        if (
            self._library.fc_native_runtime_reset_batch(
                self._handle,
                c_configs,
                len(configs),
            )
            != 1
        ):
            raise NativeRuntimeError(_last_error(self._library))

        env_count = int(self._library.fc_native_runtime_last_reset_env_count(self._handle))
        summary = self.descriptor_bundle()["summary"]
        observation_feature_count = int(summary["flat_observation_feature_count"])
        reward_feature_count = int(summary["reward_feature_count"])
        slot_indices = np.arange(env_count, dtype=np.int32)

        flat_observations = _copy_pointer_array(
            self._library.fc_native_runtime_last_reset_flat_observations(self._handle),
            shape=(env_count, observation_feature_count),
            dtype=np.float32,
        )
        reward_features = _copy_pointer_array(
            self._library.fc_native_runtime_last_reset_reward_features(self._handle),
            shape=(env_count, reward_feature_count),
            dtype=np.float32,
        )
        terminal_codes = _copy_pointer_array(
            self._library.fc_native_runtime_last_reset_terminal_codes(self._handle),
            shape=(env_count,),
            dtype=np.int32,
        )
        terminated = _copy_pointer_array(
            self._library.fc_native_runtime_last_reset_terminated(self._handle),
            shape=(env_count,),
            dtype=np.int32,
        ).astype(bool, copy=False)
        truncated = _copy_pointer_array(
            self._library.fc_native_runtime_last_reset_truncated(self._handle),
            shape=(env_count,),
            dtype=np.int32,
        ).astype(bool, copy=False)
        episode_ticks = _copy_pointer_array(
            self._library.fc_native_runtime_last_reset_episode_ticks(self._handle),
            shape=(env_count,),
            dtype=np.int32,
        )
        episode_steps = _copy_pointer_array(
            self._library.fc_native_runtime_last_reset_episode_steps(self._handle),
            shape=(env_count,),
            dtype=np.int32,
        )

        seeds = _copy_pointer_array(
            self._library.fc_native_runtime_last_reset_episode_seeds(self._handle),
            shape=(env_count,),
            dtype=np.int64,
        )
        waves = _copy_pointer_array(
            self._library.fc_native_runtime_last_reset_waves(self._handle),
            shape=(env_count,),
            dtype=np.int32,
        )
        rotations = _copy_pointer_array(
            self._library.fc_native_runtime_last_reset_rotations(self._handle),
            shape=(env_count,),
            dtype=np.int32,
        )
        remaining = _copy_pointer_array(
            self._library.fc_native_runtime_last_reset_remaining(self._handle),
            shape=(env_count,),
            dtype=np.int32,
        )
        ammo = _copy_pointer_array(
            self._library.fc_native_runtime_last_reset_ammo(self._handle),
            shape=(env_count,),
            dtype=np.int32,
        )
        prayer_doses = _copy_pointer_array(
            self._library.fc_native_runtime_last_reset_prayer_potion_doses(self._handle),
            shape=(env_count,),
            dtype=np.int32,
        )
        sharks = _copy_pointer_array(
            self._library.fc_native_runtime_last_reset_sharks(self._handle),
            shape=(env_count,),
            dtype=np.int32,
        )

        slot_states = tuple(
            NativeResetSlotState(
                seed=int(seeds[index]),
                wave=int(waves[index]),
                rotation=int(rotations[index]),
                remaining=int(remaining[index]),
                ammo=int(ammo[index]),
                prayer_potion_dose_count=int(prayer_doses[index]),
                sharks=int(sharks[index]),
            )
            for index in range(env_count)
        )
        observations = tuple(slot_state.to_observation() for slot_state in slot_states)
        episode_states = tuple(slot_state.to_episode_state() for slot_state in slot_states)
        return NativeResetBatchResult(
            env_count=env_count,
            slot_indices=slot_indices,
            flat_observations=flat_observations,
            reward_features=reward_features,
            terminal_codes=terminal_codes,
            terminated=terminated,
            truncated=truncated,
            episode_ticks=episode_ticks,
            episode_steps=episode_steps,
            episode_states=episode_states,
            observations=observations,
        )

    def configure_slot_state(self, slot_index: int, state: NativeSlotDebugState) -> None:
        self._require_open()
        c_state = _NativeSlotDebugStateC(
            tile_x=int(state.tile.x),
            tile_y=int(state.tile.y),
            tile_level=int(state.tile.level),
            hitpoints_current=int(state.hitpoints_current),
            prayer_current=int(state.prayer_current),
            run_energy=int(state.run_energy),
            running=int(bool(state.running)),
            protect_from_magic=int(bool(state.protect_from_magic)),
            protect_from_missiles=int(bool(state.protect_from_missiles)),
            protect_from_melee=int(bool(state.protect_from_melee)),
            attack_locked=int(bool(state.attack_locked)),
            food_locked=int(bool(state.food_locked)),
            drink_locked=int(bool(state.drink_locked)),
            combo_locked=int(bool(state.combo_locked)),
            busy_locked=int(bool(state.busy_locked)),
            sharks=int(state.sharks),
            prayer_potion_dose_count=int(state.prayer_potion_dose_count),
            ammo=int(state.ammo),
        )
        if self._library.fc_native_runtime_debug_configure_slot_state(
            self._handle,
            int(slot_index),
            ctypes.byref(c_state),
        ) != 1:
            raise NativeRuntimeError(_last_error(self._library))

    def configure_slot_visible_targets(
        self,
        slot_index: int,
        targets: list[NativeVisibleNpcConfig] | tuple[NativeVisibleNpcConfig, ...],
    ) -> None:
        self._require_open()
        target_array_type = _NativeVisibleTargetC * len(targets)
        c_targets = target_array_type(
            *[
                _NativeVisibleTargetC(
                    listed=int(bool(target.listed)),
                    currently_visible=int(bool(target.currently_visible)),
                    npc_index=int(target.npc_index),
                    id_code=int(POLICY_NPC_ID_TO_INDEX[target.id]),
                    tile_x=int(target.tile.x),
                    tile_y=int(target.tile.y),
                    tile_level=int(target.tile.level),
                    hitpoints_current=int(target.hitpoints_current),
                    hitpoints_max=int(target.hitpoints_max),
                    hidden=int(bool(target.hidden)),
                    dead=int(bool(target.dead)),
                    under_attack=int(bool(target.under_attack)),
                    jad_telegraph_state=int(target.jad_telegraph_state),
                )
                for target in targets
            ]
        )
        if self._library.fc_native_runtime_debug_set_slot_visible_targets(
            self._handle,
            int(slot_index),
            c_targets if len(targets) > 0 else None,
            len(targets),
        ) != 1:
            raise NativeRuntimeError(_last_error(self._library))

    def configure_slot_control(
        self,
        slot_index: int,
        *,
        tick_cap: int,
        force_invalid_state: bool = False,
    ) -> None:
        self._require_open()
        if self._library.fc_native_runtime_debug_set_slot_control(
            self._handle,
            int(slot_index),
            int(tick_cap),
            int(bool(force_invalid_state)),
        ) != 1:
            raise NativeRuntimeError(_last_error(self._library))

    def load_core_trace(
        self,
        slot_index: int,
        trace_id: str,
    ) -> None:
        self._require_open()
        try:
            native_trace_id = int(NATIVE_CORE_TRACE_IDS[trace_id])
        except KeyError as exc:
            raise ValueError(
                f"Unsupported native core trace id {trace_id!r}. "
                f"Expected one of {sorted(NATIVE_CORE_TRACE_IDS)!r}."
            ) from exc
        if self._library.fc_native_runtime_debug_load_core_trace(
            self._handle,
            int(slot_index),
            native_trace_id,
        ) != 1:
            raise NativeRuntimeError(_last_error(self._library))

    def snapshot_slots(
        self,
        *,
        slot_indices: np.ndarray | list[int] | tuple[int, ...] | None = None,
    ) -> NativeStepBatchResult:
        self._require_open()
        if slot_indices is None:
            env_count = int(self._library.fc_native_runtime_last_reset_env_count(self._handle))
            if env_count < 0:
                raise NativeRuntimeError(
                    "native runtime snapshot requires a reset or explicit slot_indices first"
                )
            slot_index_array = np.arange(env_count, dtype=np.int32)
        else:
            slot_index_array = np.asarray(slot_indices, dtype=np.int32).reshape(-1)
            env_count = int(slot_index_array.size)
        if (
            self._library.fc_native_runtime_debug_snapshot_slots(
                self._handle,
                slot_index_array.ctypes.data_as(ctypes.POINTER(ctypes.c_int)) if env_count > 0 else None,
                env_count,
            )
            != 1
        ):
            raise NativeRuntimeError(_last_error(self._library))
        return self._read_last_step_batch_result()

    def step_batch(
        self,
        packed_actions: np.ndarray | list[int] | tuple[int, ...],
        *,
        slot_indices: np.ndarray | list[int] | tuple[int, ...] | None = None,
    ) -> NativeStepBatchResult:
        self._require_open()
        action_array = np.asarray(packed_actions, dtype=np.int32).reshape(-1)
        if action_array.size % 4 != 0:
            raise ValueError(
                f"Packed native action batch length must be a multiple of 4, got {action_array.size}."
            )
        env_count = int(action_array.size // 4)
        slot_index_array: np.ndarray | None
        if slot_indices is None:
            slot_index_array = None
            slot_index_pointer = None
        else:
            slot_index_array = np.asarray(slot_indices, dtype=np.int32).reshape(-1)
            if slot_index_array.shape != (env_count,):
                raise ValueError(
                    f"slot_indices shape must be {(env_count,)}, got {slot_index_array.shape}."
                )
            slot_index_pointer = slot_index_array.ctypes.data_as(ctypes.POINTER(ctypes.c_int))

        if (
            self._library.fc_native_runtime_step_batch(
                self._handle,
                slot_index_pointer,
                action_array.ctypes.data_as(ctypes.POINTER(ctypes.c_int32)),
                env_count,
            )
            != 1
        ):
            raise NativeRuntimeError(_last_error(self._library))
        return self._read_last_step_batch_result()

    def _read_last_step_batch_result(self) -> NativeStepBatchResult:
        self._require_open()

        actual_env_count = int(self._library.fc_native_runtime_last_step_env_count(self._handle))
        summary = self.descriptor_bundle()["summary"]
        observation_feature_count = int(summary["flat_observation_feature_count"])
        reward_feature_count = int(summary["reward_feature_count"])
        jad_hit_resolve_outcome_codes = _copy_pointer_array(
            self._library.fc_native_runtime_last_step_jad_hit_resolve_outcome_codes(self._handle),
            shape=(actual_env_count,),
            dtype=np.int32,
        )
        return NativeStepBatchResult(
            env_count=actual_env_count,
            slot_indices=_copy_pointer_array(
                self._library.fc_native_runtime_last_step_slot_indices(self._handle),
                shape=(actual_env_count,),
                dtype=np.int32,
            ),
            action_ids=_copy_pointer_array(
                self._library.fc_native_runtime_last_step_action_ids(self._handle),
                shape=(actual_env_count,),
                dtype=np.int32,
            ),
            action_accepted=_copy_pointer_array(
                self._library.fc_native_runtime_last_step_action_accepted(self._handle),
                shape=(actual_env_count,),
                dtype=np.int32,
            ).astype(bool, copy=False),
            rejection_codes=_copy_pointer_array(
                self._library.fc_native_runtime_last_step_rejection_codes(self._handle),
                shape=(actual_env_count,),
                dtype=np.int32,
            ),
            resolved_target_npc_indices=_copy_pointer_array(
                self._library.fc_native_runtime_last_step_resolved_target_npc_indices(self._handle),
                shape=(actual_env_count,),
                dtype=np.int32,
            ),
            jad_telegraph_states=_copy_pointer_array(
                self._library.fc_native_runtime_last_step_jad_telegraph_states(self._handle),
                shape=(actual_env_count,),
                dtype=np.int32,
            ),
            jad_hit_resolve_outcome_codes=jad_hit_resolve_outcome_codes,
            jad_hit_resolve_outcomes=tuple(
                JAD_HIT_RESOLVE_OUTCOME_BY_CODE[int(code)] for code in jad_hit_resolve_outcome_codes
            ),
            flat_observations=_copy_pointer_array(
                self._library.fc_native_runtime_last_step_flat_observations(self._handle),
                shape=(actual_env_count, observation_feature_count),
                dtype=np.float32,
            ),
            reward_features=_copy_pointer_array(
                self._library.fc_native_runtime_last_step_reward_features(self._handle),
                shape=(actual_env_count, reward_feature_count),
                dtype=np.float32,
            ),
            terminal_codes=_copy_pointer_array(
                self._library.fc_native_runtime_last_step_terminal_codes(self._handle),
                shape=(actual_env_count,),
                dtype=np.int32,
            ),
            terminated=_copy_pointer_array(
                self._library.fc_native_runtime_last_step_terminated(self._handle),
                shape=(actual_env_count,),
                dtype=np.int32,
            ).astype(bool, copy=False),
            truncated=_copy_pointer_array(
                self._library.fc_native_runtime_last_step_truncated(self._handle),
                shape=(actual_env_count,),
                dtype=np.int32,
            ).astype(bool, copy=False),
            episode_ticks=_copy_pointer_array(
                self._library.fc_native_runtime_last_step_episode_ticks(self._handle),
                shape=(actual_env_count,),
                dtype=np.int32,
            ),
            episode_steps=_copy_pointer_array(
                self._library.fc_native_runtime_last_step_episode_steps(self._handle),
                shape=(actual_env_count,),
                dtype=np.int32,
            ),
        )

    def close(self) -> None:
        if self._closed:
            return
        self._library.fc_native_runtime_close(self._handle)
        self._closed = True

    def __enter__(self) -> NativeRuntimeHandle:
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.close()

    def _require_open(self) -> None:
        if self._closed:
            raise NativeRuntimeError("native runtime handle is closed")


def load_native_runtime(
    *,
    library_path: Path | None = None,
    build_config: NativeRuntimeBuildConfig | None = None,
) -> NativeRuntimeHandle:
    resolved_library_path = (
        library_path.resolve()
        if library_path is not None
        else build_native_runtime(build_config)
    )
    library = ctypes.CDLL(str(resolved_library_path))
    _configure_api(library)
    abi_version = int(library.fc_native_abi_version())
    if abi_version != NATIVE_RUNTIME_ABI_VERSION:
        raise NativeRuntimeError(
            f"Unsupported native ABI version {abi_version}; expected {NATIVE_RUNTIME_ABI_VERSION}."
        )
    handle = library.fc_native_runtime_open()
    if not handle:
        raise NativeRuntimeError(_last_error(library))
    return NativeRuntimeHandle(
        library_path=resolved_library_path,
        library=library,
        handle=handle,
    )


def load_native_runtime_from_config(
    config: dict[str, Any],
    *,
    build_config: NativeRuntimeBuildConfig | None = None,
) -> NativeRuntimeHandle:
    env_backend = str(dict(config.get("env", {})).get("env_backend", ""))
    if env_backend != ENV_BACKEND_NATIVE_PREVIEW:
        raise ValueError(
            "Native runtime loading requires env.env_backend='native_preview'. "
            "Standard vecenv/train cutover remains out of scope before PR7."
        )
    return load_native_runtime(build_config=build_config)


def _configure_api(library: ctypes.CDLL) -> None:
    library.fc_native_abi_version.argtypes = []
    library.fc_native_abi_version.restype = ctypes.c_int
    library.fc_native_runtime_version_string.argtypes = []
    library.fc_native_runtime_version_string.restype = ctypes.c_char_p
    library.fc_native_runtime_open.argtypes = []
    library.fc_native_runtime_open.restype = ctypes.c_void_p
    library.fc_native_runtime_close.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_close.restype = None
    library.fc_native_runtime_last_error.argtypes = []
    library.fc_native_runtime_last_error.restype = ctypes.c_char_p
    library.fc_native_runtime_descriptor_count.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_descriptor_count.restype = ctypes.c_int
    library.fc_native_runtime_descriptor_bundle_json.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_descriptor_bundle_json.restype = ctypes.c_char_p
    library.fc_native_runtime_descriptor_bundle_json_size.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_descriptor_bundle_json_size.restype = ctypes.c_size_t
    library.fc_native_runtime_reset_batch.argtypes = [
        ctypes.c_void_p,
        ctypes.POINTER(_NativeEpisodeConfigC),
        ctypes.c_int,
    ]
    library.fc_native_runtime_reset_batch.restype = ctypes.c_int
    library.fc_native_runtime_last_reset_env_count.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_reset_env_count.restype = ctypes.c_int
    library.fc_native_runtime_last_reset_episode_seeds.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_reset_episode_seeds.restype = ctypes.POINTER(ctypes.c_int64)
    library.fc_native_runtime_last_reset_waves.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_reset_waves.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_reset_rotations.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_reset_rotations.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_reset_remaining.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_reset_remaining.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_reset_ammo.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_reset_ammo.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_reset_prayer_potion_doses.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_reset_prayer_potion_doses.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_reset_sharks.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_reset_sharks.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_reset_flat_observations.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_reset_flat_observations.restype = ctypes.POINTER(ctypes.c_float)
    library.fc_native_runtime_last_reset_reward_features.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_reset_reward_features.restype = ctypes.POINTER(ctypes.c_float)
    library.fc_native_runtime_last_reset_terminal_codes.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_reset_terminal_codes.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_reset_terminated.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_reset_terminated.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_reset_truncated.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_reset_truncated.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_reset_episode_ticks.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_reset_episode_ticks.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_reset_episode_steps.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_reset_episode_steps.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_debug_configure_slot_state.argtypes = [
        ctypes.c_void_p,
        ctypes.c_int,
        ctypes.POINTER(_NativeSlotDebugStateC),
    ]
    library.fc_native_runtime_debug_configure_slot_state.restype = ctypes.c_int
    library.fc_native_runtime_debug_set_slot_visible_targets.argtypes = [
        ctypes.c_void_p,
        ctypes.c_int,
        ctypes.POINTER(_NativeVisibleTargetC),
        ctypes.c_int,
    ]
    library.fc_native_runtime_debug_set_slot_visible_targets.restype = ctypes.c_int
    library.fc_native_runtime_debug_set_slot_control.argtypes = [
        ctypes.c_void_p,
        ctypes.c_int,
        ctypes.c_int,
        ctypes.c_int,
    ]
    library.fc_native_runtime_debug_set_slot_control.restype = ctypes.c_int
    library.fc_native_runtime_debug_load_core_trace.argtypes = [
        ctypes.c_void_p,
        ctypes.c_int,
        ctypes.c_int,
    ]
    library.fc_native_runtime_debug_load_core_trace.restype = ctypes.c_int
    library.fc_native_runtime_debug_snapshot_slots.argtypes = [
        ctypes.c_void_p,
        ctypes.POINTER(ctypes.c_int),
        ctypes.c_int,
    ]
    library.fc_native_runtime_debug_snapshot_slots.restype = ctypes.c_int
    library.fc_native_runtime_step_batch.argtypes = [
        ctypes.c_void_p,
        ctypes.POINTER(ctypes.c_int),
        ctypes.POINTER(ctypes.c_int32),
        ctypes.c_int,
    ]
    library.fc_native_runtime_step_batch.restype = ctypes.c_int
    library.fc_native_runtime_last_step_env_count.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_step_env_count.restype = ctypes.c_int
    library.fc_native_runtime_last_step_slot_indices.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_step_slot_indices.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_step_action_ids.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_step_action_ids.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_step_action_accepted.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_step_action_accepted.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_step_rejection_codes.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_step_rejection_codes.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_step_resolved_target_npc_indices.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_step_resolved_target_npc_indices.restype = ctypes.POINTER(
        ctypes.c_int
    )
    library.fc_native_runtime_last_step_jad_telegraph_states.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_step_jad_telegraph_states.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_step_jad_hit_resolve_outcome_codes.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_step_jad_hit_resolve_outcome_codes.restype = ctypes.POINTER(
        ctypes.c_int
    )
    library.fc_native_runtime_last_step_flat_observations.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_step_flat_observations.restype = ctypes.POINTER(ctypes.c_float)
    library.fc_native_runtime_last_step_reward_features.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_step_reward_features.restype = ctypes.POINTER(ctypes.c_float)
    library.fc_native_runtime_last_step_terminal_codes.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_step_terminal_codes.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_step_terminated.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_step_terminated.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_step_truncated.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_step_truncated.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_step_episode_ticks.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_step_episode_ticks.restype = ctypes.POINTER(ctypes.c_int)
    library.fc_native_runtime_last_step_episode_steps.argtypes = [ctypes.c_void_p]
    library.fc_native_runtime_last_step_episode_steps.restype = ctypes.POINTER(ctypes.c_int)


def _last_error(library: ctypes.CDLL) -> str:
    payload = library.fc_native_runtime_last_error()
    if not payload:
        return "native runtime reported an unknown error"
    return _decode_c_string(payload)


def _decode_c_string(payload: bytes | None) -> str:
    if payload is None:
        return ""
    return payload.decode("utf-8")


def _copy_pointer_array(
    pointer: ctypes._Pointer[ctypes._SimpleCData],
    *,
    shape: tuple[int, ...],
    dtype: np.dtype[Any] | type[np.generic],
) -> np.ndarray:
    total = int(np.prod(shape, dtype=np.int64))
    if total == 0:
        return np.zeros(shape, dtype=dtype)
    if not pointer:
        raise NativeRuntimeError("native runtime returned a null data pointer")
    array = np.ctypeslib.as_array(pointer, shape=(total,))
    return np.asarray(array, dtype=dtype).copy().reshape(shape)
