from __future__ import annotations

from dataclasses import dataclass, field
from multiprocessing.connection import Connection
import multiprocessing
from time import perf_counter
import statistics
import traceback
from typing import Any, Mapping

import numpy as np
import pufferlib
import pufferlib.vector

from fight_caves_rl.benchmarks.instrumentation import BucketInstrumentation
from fight_caves_rl.bridge.contracts import HeadlessBootstrapConfig
from fight_caves_rl.bridge.errors import BridgeError
from fight_caves_rl.curriculum.registry import build_curriculum
from fight_caves_rl.envs.puffer_encoding import (
    build_policy_action_space,
    build_policy_observation_space,
)
from fight_caves_rl.envs.shared_memory_transport import (
    INFO_PAYLOAD_MODE_FULL,
    INFO_PAYLOAD_MODE_MINIMAL,
    INFO_PAYLOAD_MODES,
    PIPE_PICKLE_TRANSPORT_MODE,
    SHARED_MEMORY_TRANSPORT_MODE,
    SUBPROCESS_TRANSPORT_MODES,
    SharedMemoryTransportParent,
    SharedMemoryTransportWorker,
)
from fight_caves_rl.envs.vector_env import HeadlessBatchVecEnv, HeadlessBatchVecEnvConfig
from fight_caves_rl.envs_fast import FastKernelVecEnv, FastKernelVecEnvConfig, FastRewardAdapter
from fight_caves_rl.envs_fast.fast_spaces import (
    build_fast_action_space,
    build_fast_observation_space,
)
from fight_caves_rl.rewards.registry import resolve_reward_fn


@dataclass(frozen=True)
class SubprocessHeadlessBatchVecEnvConfig:
    env_count: int
    reward_config_id: str
    curriculum_config_id: str
    env_backend: str = "v1_bridge"
    transport_mode: str = PIPE_PICKLE_TRANSPORT_MODE
    worker_count: int = 1
    account_name_prefix: str = "rl_vecenv"
    start_wave: int = 1
    ammo: int = 1000
    prayer_potions: int = 8
    sharks: int = 20
    tick_cap: int = 20_000
    include_future_leakage: bool = False
    info_payload_mode: str = INFO_PAYLOAD_MODE_MINIMAL
    instrumentation_enabled: bool = False
    bootstrap: HeadlessBootstrapConfig = field(default_factory=HeadlessBootstrapConfig)


@dataclass(frozen=True)
class _WorkerError:
    error_type: str
    message: str
    traceback_text: str


@dataclass(frozen=True)
class _WorkerHandle:
    index: int
    conn: Connection
    process: multiprocessing.Process
    env_slice: slice
    transport_parent: SharedMemoryTransportParent | None


class SubprocessHeadlessBatchVecEnv:
    reset = pufferlib.vector.reset
    step = pufferlib.vector.step

    def __init__(self, config: SubprocessHeadlessBatchVecEnvConfig) -> None:
        if int(config.env_count) <= 0:
            raise ValueError(f"env_count must be > 0, got {config.env_count}.")
        self.config = config
        self.driver_env = self
        self.agents_per_batch = int(config.env_count)
        self.num_agents = self.agents_per_batch
        self.env_backend = str(config.env_backend)
        if self.env_backend == "v2_fast":
            self.single_observation_space = build_fast_observation_space()
            self.single_action_space = build_fast_action_space()
        else:
            self.single_observation_space = build_policy_observation_space()
            self.single_action_space = build_policy_action_space()
        self.transport_mode = str(config.transport_mode)
        if self.transport_mode not in SUBPROCESS_TRANSPORT_MODES:
            raise ValueError(
                "Unsupported subprocess transport mode: "
                f"{self.transport_mode!r}. Expected one of {SUBPROCESS_TRANSPORT_MODES!r}."
            )
        requested_worker_count = int(config.worker_count)
        if requested_worker_count <= 0:
            raise ValueError(f"worker_count must be > 0, got {config.worker_count}.")
        self.worker_env_counts = _partition_worker_env_counts(
            env_count=self.agents_per_batch,
            worker_count=min(requested_worker_count, self.agents_per_batch),
        )
        self.info_payload_mode = str(config.info_payload_mode)
        if self.info_payload_mode not in INFO_PAYLOAD_MODES:
            raise ValueError(
                "Unsupported subprocess info payload mode: "
                f"{self.info_payload_mode!r}. Expected one of {INFO_PAYLOAD_MODES!r}."
            )
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
        self.agent_ids = np.arange(self.num_agents)
        self.initialized = False
        self.flag = pufferlib.vector.RESET
        self.infos: list[dict[str, Any]] = []
        self._minimal_infos = tuple({} for _ in range(self.num_agents))
        self._closed = False
        self._instrumentation = (
            BucketInstrumentation() if bool(config.instrumentation_enabled) else None
        )
        self._ctx = multiprocessing.get_context("spawn")
        self._workers: list[_WorkerHandle] = []
        self._worker_local_minimal_infos: dict[int, tuple[dict[str, Any], ...]] = {}
        self._worker_recv_wait_seconds: dict[int, list[float]] = {}
        self._recv_round_total_wait_seconds: list[float] = []
        self._recv_round_max_wait_seconds: list[float] = []
        self._recv_round_min_wait_seconds: list[float] = []
        self._recv_round_wait_span_seconds: list[float] = []
        observation_dim = int(self.single_observation_space.shape[0])
        action_dim = len(self.single_action_space.nvec)
        self._observations = np.zeros(
            (self.num_agents, observation_dim),
            dtype=self.single_observation_space.dtype,
        )
        self._rewards = np.zeros(self.num_agents, dtype=np.float32)
        self._terminals = np.zeros(self.num_agents, dtype=np.bool_)
        self._truncations = np.zeros(self.num_agents, dtype=np.bool_)
        self._teacher_actions = np.zeros(self.num_agents, dtype=np.int32)
        self._masks = np.ones(self.num_agents, dtype=np.bool_)

        start = 0
        for worker_index, worker_env_count in enumerate(self.worker_env_counts):
            env_slice = slice(start, start + int(worker_env_count))
            start = env_slice.stop
            transport_parent: SharedMemoryTransportParent | None = None
            if self.transport_mode == SHARED_MEMORY_TRANSPORT_MODE:
                transport_parent = SharedMemoryTransportParent(
                    env_count=int(worker_env_count),
                    action_dim=action_dim,
                    observation_dim=observation_dim,
                )
            parent_conn, child_conn = self._ctx.Pipe()
            process = self._ctx.Process(
                target=_subprocess_vecenv_worker,
                args=(
                    child_conn,
                    _config_to_payload(
                        config,
                        worker_index=worker_index,
                        worker_env_count=int(worker_env_count),
                        transport_parent=transport_parent,
                    ),
                ),
                daemon=True,
            )
            process.start()
            child_conn.close()
            self._workers.append(
                _WorkerHandle(
                    index=worker_index,
                    conn=parent_conn,
                    process=process,
                    env_slice=env_slice,
                    transport_parent=transport_parent,
                )
            )
            self._worker_local_minimal_infos[worker_index] = tuple(
                {} for _ in range(int(worker_env_count))
            )
            self._worker_recv_wait_seconds[worker_index] = []

    @property
    def num_envs(self) -> int:
        return self.agents_per_batch

    @property
    def episode_counts(self) -> np.ndarray:
        counts = np.zeros(self.num_agents, dtype=np.int64)
        for worker in self._workers:
            payload = self._request_payload(worker, "episode_counts", None)
            counts[worker.env_slice] = np.asarray(payload["episode_counts"], dtype=np.int64)
        return counts

    def topology_snapshot(self) -> dict[str, Any]:
        return {
            "backend": "subprocess",
            "env_backend": self.env_backend,
            "transport_mode": self.transport_mode,
            "worker_count": len(self._workers),
            "worker_env_counts": [int(value) for value in self.worker_env_counts],
            "info_payload_mode": self.info_payload_mode,
        }

    def async_reset(self, seed: int | None = None) -> None:
        stage_started = perf_counter()
        self.flag = pufferlib.vector.RECV
        for worker in self._workers:
            worker_started = perf_counter()
            self._send_command(
                worker,
                "reset",
                {"seed": None if seed is None else int(seed)},
            )
            self._record_instrumentation(
                "subprocess_parent_async_reset_send_command",
                perf_counter() - worker_started,
            )
        self._record_instrumentation(
            "subprocess_parent_async_reset_total",
            perf_counter() - stage_started,
        )

    def send(self, actions: np.ndarray) -> None:
        stage_started = perf_counter()
        if not actions.flags.contiguous:
            contiguous_started = perf_counter()
            actions = np.ascontiguousarray(actions)
            self._record_instrumentation(
                "subprocess_parent_send_make_contiguous",
                perf_counter() - contiguous_started,
            )
        precheck_started = perf_counter()
        checked = pufferlib.vector.send_precheck(self, actions)
        self._record_instrumentation(
            "subprocess_parent_send_precheck",
            perf_counter() - precheck_started,
        )
        for worker in self._workers:
            worker_started = perf_counter()
            action_slice_started = perf_counter()
            local_actions = np.asarray(checked[worker.env_slice])
            self._record_instrumentation(
                "subprocess_parent_send_action_slice",
                perf_counter() - action_slice_started,
            )
            if worker.transport_parent is not None:
                write_actions_started = perf_counter()
                worker.transport_parent.write_actions(local_actions)
                self._record_instrumentation(
                    "subprocess_parent_send_transport_write_actions",
                    perf_counter() - write_actions_started,
                )
                send_command_started = perf_counter()
                self._send_command(worker, "step", {})
                self._record_instrumentation(
                    "subprocess_parent_send_command",
                    perf_counter() - send_command_started,
                )
                self._record_instrumentation(
                    "subprocess_parent_send_worker_total",
                    perf_counter() - worker_started,
                )
                continue
            payload_copy_started = perf_counter()
            payload_actions = np.array(local_actions, copy=True)
            self._record_instrumentation(
                "subprocess_parent_send_pipe_action_copy",
                perf_counter() - payload_copy_started,
            )
            send_command_started = perf_counter()
            self._send_command(worker, "step", {"actions": payload_actions})
            self._record_instrumentation(
                "subprocess_parent_send_command",
                perf_counter() - send_command_started,
            )
            self._record_instrumentation(
                "subprocess_parent_send_worker_total",
                perf_counter() - worker_started,
            )
        self._record_instrumentation("subprocess_parent_send_total", perf_counter() - stage_started)

    def recv(self):
        stage_started = perf_counter()
        pufferlib.vector.recv_precheck(self)
        self._record_instrumentation(
            "subprocess_parent_recv_precheck",
            perf_counter() - stage_started,
        )
        infos: list[dict[str, Any]] = list(self._minimal_infos)
        round_waits: list[float] = []
        for worker in self._workers:
            wait_started = perf_counter()
            raw_payload = self._recv_payload(worker)
            wait_elapsed = perf_counter() - wait_started
            round_waits.append(wait_elapsed)
            self._worker_recv_wait_seconds.setdefault(worker.index, []).append(wait_elapsed)
            self._record_instrumentation("subprocess_parent_recv_wait", wait_elapsed)

            materialize_started = perf_counter()
            payload = self._materialize_worker_payload(worker, raw_payload)
            self._record_instrumentation(
                "subprocess_parent_recv_materialize",
                perf_counter() - materialize_started,
            )

            apply_started = perf_counter()
            self._apply_worker_transition(worker.env_slice, payload)
            self._record_instrumentation(
                "subprocess_parent_recv_apply_transition",
                perf_counter() - apply_started,
            )
            infos[worker.env_slice] = list(payload["infos"])
        if round_waits:
            round_total = float(sum(round_waits))
            round_max = float(max(round_waits))
            round_min = float(min(round_waits))
            self._recv_round_total_wait_seconds.append(round_total)
            self._recv_round_max_wait_seconds.append(round_max)
            self._recv_round_min_wait_seconds.append(round_min)
            self._recv_round_wait_span_seconds.append(round_max - round_min)
            self._record_instrumentation("subprocess_parent_recv_wait_round_total", round_total)
            self._record_instrumentation("subprocess_parent_recv_wait_round_max", round_max)
            self._record_instrumentation("subprocess_parent_recv_wait_round_min", round_min)
            self._record_instrumentation("subprocess_parent_recv_wait_round_span", round_max - round_min)
        self.infos = infos
        self._record_instrumentation("subprocess_parent_recv_total", perf_counter() - stage_started)
        return (
            self._observations,
            self._rewards,
            self._terminals,
            self._truncations,
            self._teacher_actions,
            self.infos,
            self.agent_ids,
            self._masks,
        )

    def notify(self) -> None:
        return None

    def close(self) -> None:
        if self._closed:
            return
        try:
            for worker in self._workers:
                if worker.process.is_alive():
                    self._send_command(worker, "close", None)
            for worker in self._workers:
                if worker.process.is_alive() and worker.conn.poll(2.0):
                    self._recv_payload(worker)
        except Exception:
            pass
        finally:
            for worker in self._workers:
                if worker.process.is_alive():
                    worker.process.terminate()
                worker.process.join(timeout=2.0)
                worker.conn.close()
                if worker.transport_parent is not None:
                    worker.transport_parent.close(unlink=True)
            self._closed = True

    def instrumentation_snapshot(self) -> dict[str, dict[str, float | int]]:
        snapshots: list[dict[str, dict[str, float | int]]] = []
        for worker in self._workers:
            payload = self._request_payload(worker, "instrumentation_snapshot", None)
            snapshots.append(dict(payload.get("instrumentation", {})))
        merged = _merge_instrumentation_snapshots(snapshots)
        if self._instrumentation is not None:
            merged = _merge_instrumentation_snapshots([merged, self._instrumentation.snapshot()])
        return merged

    def instrumentation_detailed_snapshot(self) -> dict[str, Any]:
        worker_details: list[dict[str, Any]] = []
        vecenv_snapshots: list[dict[str, dict[str, float | int]]] = []
        worker_loop_snapshots: list[dict[str, dict[str, float | int]]] = []
        for worker in self._workers:
            payload = self._request_payload(worker, "instrumentation_detail", None)
            vecenv_instrumentation = dict(payload.get("vecenv_instrumentation", {}))
            worker_loop_instrumentation = dict(payload.get("worker_loop_instrumentation", {}))
            vecenv_snapshots.append(vecenv_instrumentation)
            worker_loop_snapshots.append(worker_loop_instrumentation)
            worker_details.append(
                {
                    "worker_index": int(worker.index),
                    "env_count": int(worker.env_slice.stop - worker.env_slice.start),
                    "vecenv_instrumentation": vecenv_instrumentation,
                    "worker_loop_instrumentation": worker_loop_instrumentation,
                    "step_total_seconds_stats": dict(payload.get("step_total_seconds_stats", {})),
                    "recv_wait_seconds_stats": _summarize_distribution(
                        self._worker_recv_wait_seconds.get(worker.index, [])
                    ),
                }
            )
        parent_snapshot = self._instrumentation.snapshot() if self._instrumentation is not None else {}
        merged_vecenv = _merge_instrumentation_snapshots(vecenv_snapshots)
        merged_worker_loop = _merge_instrumentation_snapshots(worker_loop_snapshots)
        return {
            "parent_instrumentation": parent_snapshot,
            "worker_vecenv_instrumentation": merged_vecenv,
            "worker_loop_instrumentation": merged_worker_loop,
            "worker_details": worker_details,
            "recv_wait_round_total_stats": _summarize_distribution(self._recv_round_total_wait_seconds),
            "recv_wait_round_max_stats": _summarize_distribution(self._recv_round_max_wait_seconds),
            "recv_wait_round_min_stats": _summarize_distribution(self._recv_round_min_wait_seconds),
            "recv_wait_round_span_stats": _summarize_distribution(self._recv_round_wait_span_seconds),
        }

    def reset_instrumentation(self) -> None:
        if self._instrumentation is not None:
            self._instrumentation.clear()
        for worker_index in list(self._worker_recv_wait_seconds):
            self._worker_recv_wait_seconds[worker_index].clear()
        self._recv_round_total_wait_seconds.clear()
        self._recv_round_max_wait_seconds.clear()
        self._recv_round_min_wait_seconds.clear()
        self._recv_round_wait_span_seconds.clear()
        for worker in self._workers:
            self._request_payload(worker, "reset_instrumentation", None)

    def _record_instrumentation(self, bucket: str, seconds: float) -> None:
        if self._instrumentation is None:
            return
        self._instrumentation.record(bucket, seconds)

    def _send_command(
        self,
        worker: _WorkerHandle,
        command: str,
        payload: Mapping[str, Any] | None,
    ) -> None:
        if not worker.process.is_alive():
            raise BridgeError(
                "Subprocess vecenv worker is not running. "
                f"worker_index={worker.index}. The previous command likely crashed the worker."
            )
        worker.conn.send((command, dict(payload or {})))

    def _recv_payload(self, worker: _WorkerHandle) -> dict[str, Any]:
        try:
            status, payload = worker.conn.recv()
        except EOFError as exc:
            raise BridgeError(
                "Subprocess vecenv worker exited unexpectedly while waiting for a response. "
                f"worker_index={worker.index}."
            ) from exc
        if status == "ok":
            return payload
        error = _WorkerError(**payload)
        raise BridgeError(
            "Subprocess vecenv worker failed with "
            f"{error.error_type}: {error.message}\n"
            f"worker_index={worker.index}\n"
            f"{error.traceback_text}"
        )

    def _request_payload(
        self,
        worker: _WorkerHandle,
        command: str,
        payload: Mapping[str, Any] | None,
    ) -> dict[str, Any]:
        self._send_command(worker, command, payload)
        return self._recv_payload(worker)

    def _materialize_worker_payload(
        self,
        worker: _WorkerHandle,
        payload: dict[str, Any],
    ) -> dict[str, Any]:
        if payload.get("transport_mode") == SHARED_MEMORY_TRANSPORT_MODE or "buffer_index" in payload:
            if worker.transport_parent is None:
                raise BridgeError(
                    "Shared-memory subprocess vecenv response arrived without a parent transport."
                )
            payload = worker.transport_parent.materialize_transition(payload)
        elif payload.get("info_payload_mode") == INFO_PAYLOAD_MODE_MINIMAL and "infos" not in payload:
            payload = dict(payload)
            payload["infos"] = list(self._worker_local_minimal_infos[worker.index])
        return payload

    def _apply_worker_transition(
        self,
        env_slice: slice,
        payload: dict[str, Any],
    ) -> None:
        np.copyto(self._observations[env_slice], np.asarray(payload["observations"], dtype=np.float32))
        np.copyto(self._rewards[env_slice], np.asarray(payload["rewards"], dtype=np.float32))
        np.copyto(self._terminals[env_slice], np.asarray(payload["terminals"], dtype=np.bool_))
        np.copyto(self._truncations[env_slice], np.asarray(payload["truncations"], dtype=np.bool_))
        np.copyto(
            self._teacher_actions[env_slice],
            np.asarray(payload["teacher_actions"], dtype=np.int32),
            casting="unsafe",
        )
        np.copyto(self._masks[env_slice], np.asarray(payload["masks"], dtype=np.bool_))


def _subprocess_vecenv_worker(conn: Connection, payload: dict[str, Any]) -> None:
    vecenv: HeadlessBatchVecEnv | FastKernelVecEnv | None = None
    transport_worker: SharedMemoryTransportWorker | None = None
    worker_instrumentation = BucketInstrumentation()
    step_total_seconds: list[float] = []
    try:
        vecenv = _build_worker_vecenv(payload)
        if payload["transport_mode"] == SHARED_MEMORY_TRANSPORT_MODE:
            transport_worker = SharedMemoryTransportWorker.attach(payload["transport"])
        while True:
            recv_command_started = perf_counter()
            command, data = conn.recv()
            worker_instrumentation.record("worker_conn_recv_wait", perf_counter() - recv_command_started)
            if command == "reset":
                reset_started = perf_counter()
                vecenv_reset_started = perf_counter()
                vecenv.async_reset(seed=data.get("seed"))
                worker_instrumentation.record(
                    "worker_vecenv_async_reset",
                    perf_counter() - vecenv_reset_started,
                )
                vecenv_recv_started = perf_counter()
                transition = vecenv.recv()
                worker_instrumentation.record(
                    "worker_vecenv_recv_after_reset",
                    perf_counter() - vecenv_recv_started,
                )
                serialize_started = perf_counter()
                serialized = _serialize_transition(transition, transport_worker=transport_worker)
                worker_instrumentation.record(
                    "worker_serialize_transition_after_reset",
                    perf_counter() - serialize_started,
                )
                conn_send_started = perf_counter()
                conn.send(("ok", serialized))
                worker_instrumentation.record("worker_conn_send_after_reset", perf_counter() - conn_send_started)
                worker_instrumentation.record("worker_reset_total", perf_counter() - reset_started)
                continue
            if command == "step":
                step_started = perf_counter()
                if transport_worker is None:
                    receive_action_started = perf_counter()
                    actions = np.asarray(data["actions"])
                    worker_instrumentation.record(
                        "worker_receive_pipe_actions",
                        perf_counter() - receive_action_started,
                    )
                else:
                    receive_action_started = perf_counter()
                    actions = transport_worker.read_actions()
                    worker_instrumentation.record(
                        "worker_receive_shared_memory_actions",
                        perf_counter() - receive_action_started,
                    )
                vecenv_send_started = perf_counter()
                vecenv.send(actions)
                worker_instrumentation.record("worker_vecenv_send", perf_counter() - vecenv_send_started)
                vecenv_recv_started = perf_counter()
                transition = vecenv.recv()
                worker_instrumentation.record("worker_vecenv_recv_after_step", perf_counter() - vecenv_recv_started)
                serialize_started = perf_counter()
                serialized = _serialize_transition(transition, transport_worker=transport_worker)
                worker_instrumentation.record(
                    "worker_serialize_transition_after_step",
                    perf_counter() - serialize_started,
                )
                conn_send_started = perf_counter()
                conn.send(("ok", serialized))
                worker_instrumentation.record("worker_conn_send_after_step", perf_counter() - conn_send_started)
                step_elapsed = perf_counter() - step_started
                worker_instrumentation.record("worker_step_total", step_elapsed)
                step_total_seconds.append(step_elapsed)
                continue
            if command == "close":
                vecenv.close()
                conn.send(("ok", {"closed": True}))
                return
            if command == "instrumentation_snapshot":
                conn.send(("ok", {"instrumentation": vecenv.instrumentation_snapshot()}))
                continue
            if command == "instrumentation_detail":
                conn.send(
                    (
                        "ok",
                        {
                            "vecenv_instrumentation": vecenv.instrumentation_snapshot(),
                            "worker_loop_instrumentation": worker_instrumentation.snapshot(),
                            "step_total_seconds_stats": _summarize_distribution(step_total_seconds),
                        },
                    )
                )
                continue
            if command == "reset_instrumentation":
                vecenv.reset_instrumentation()
                worker_instrumentation.clear()
                step_total_seconds.clear()
                conn.send(("ok", {"reset": True}))
                continue
            if command == "episode_counts":
                conn.send(("ok", {"episode_counts": np.asarray(vecenv.episode_counts, dtype=np.int64)}))
                continue
            raise ValueError(f"Unsupported subprocess vecenv command: {command!r}")
    except BaseException as exc:
        try:
            conn.send(
                (
                    "error",
                    {
                        "error_type": type(exc).__name__,
                        "message": str(exc),
                        "traceback_text": traceback.format_exc(),
                    },
                )
            )
        except Exception:
            pass
        raise
    finally:
        if transport_worker is not None:
            try:
                transport_worker.close()
            except Exception:
                pass
        if vecenv is not None:
            try:
                vecenv.close()
            except Exception:
                pass
        conn.close()


def _build_worker_vecenv(payload: dict[str, Any]) -> HeadlessBatchVecEnv:
    curriculum = build_curriculum(str(payload["curriculum_config_id"]))
    env_backend = str(payload.get("env_backend", "v1_bridge"))
    if env_backend == "v2_fast":
        return FastKernelVecEnv(
            FastKernelVecEnvConfig(
                env_count=int(payload["env_count"]),
                account_name_prefix=str(payload["account_name_prefix"]),
                start_wave=int(payload["start_wave"]),
                ammo=int(payload["ammo"]),
                prayer_potions=int(payload["prayer_potions"]),
                sharks=int(payload["sharks"]),
                tick_cap=int(payload["tick_cap"]),
                include_future_leakage=bool(payload["include_future_leakage"]),
                info_payload_mode=str(payload["info_payload_mode"]),
                instrumentation_enabled=bool(payload["instrumentation_enabled"]),
                bootstrap=HeadlessBootstrapConfig(
                    load_content_scripts=bool(payload["bootstrap"]["load_content_scripts"]),
                    start_world=bool(payload["bootstrap"]["start_world"]),
                    install_shutdown_hook=bool(payload["bootstrap"]["install_shutdown_hook"]),
                    settings_overrides=dict(payload["bootstrap"]["settings_overrides"]),
                ),
                reset_options_provider=curriculum.reset_overrides,
            ),
            reward_adapter=FastRewardAdapter.from_config_id(str(payload["reward_config_id"])),
        )
    return HeadlessBatchVecEnv(
        HeadlessBatchVecEnvConfig(
            env_count=int(payload["env_count"]),
            account_name_prefix=str(payload["account_name_prefix"]),
            start_wave=int(payload["start_wave"]),
            ammo=int(payload["ammo"]),
            prayer_potions=int(payload["prayer_potions"]),
            sharks=int(payload["sharks"]),
            tick_cap=int(payload["tick_cap"]),
            include_future_leakage=bool(payload["include_future_leakage"]),
            info_payload_mode=str(payload["info_payload_mode"]),
            instrumentation_enabled=bool(payload["instrumentation_enabled"]),
            bootstrap=HeadlessBootstrapConfig(
                load_content_scripts=bool(payload["bootstrap"]["load_content_scripts"]),
                start_world=bool(payload["bootstrap"]["start_world"]),
                install_shutdown_hook=bool(payload["bootstrap"]["install_shutdown_hook"]),
                settings_overrides=dict(payload["bootstrap"]["settings_overrides"]),
            ),
            reset_options_provider=curriculum.reset_overrides,
        ),
        reward_fn=resolve_reward_fn(str(payload["reward_config_id"])),
    )


def _serialize_transition(
    transition: tuple[Any, ...],
    *,
    transport_worker: SharedMemoryTransportWorker | None,
) -> dict[str, Any]:
    if transport_worker is not None:
        return transport_worker.publish_transition(transition)
    observations, rewards, terminals, truncations, teacher_actions, infos, agent_ids, masks = transition
    info_payload_mode = (
        INFO_PAYLOAD_MODE_MINIMAL
        if all(not info for info in infos)
        else INFO_PAYLOAD_MODE_FULL
    )
    return {
        "observations": np.array(observations, copy=True),
        "rewards": np.array(rewards, copy=True),
        "terminals": np.array(terminals, copy=True),
        "truncations": np.array(truncations, copy=True),
        "teacher_actions": np.array(teacher_actions, copy=True),
        "agent_ids": np.array(agent_ids, copy=True),
        "masks": np.array(masks, copy=True),
        "info_payload_mode": info_payload_mode,
        **({} if info_payload_mode == INFO_PAYLOAD_MODE_MINIMAL else {"infos": list(infos)}),
    }


def _config_to_payload(
    config: SubprocessHeadlessBatchVecEnvConfig,
    *,
    worker_index: int,
    worker_env_count: int,
    transport_parent: SharedMemoryTransportParent | None,
) -> dict[str, Any]:
    account_name_prefix = str(config.account_name_prefix)
    if int(config.worker_count) > 1:
        account_name_prefix = f"{account_name_prefix}_worker{int(worker_index)}"
    payload: dict[str, Any] = {
        "env_count": int(worker_env_count),
        "reward_config_id": str(config.reward_config_id),
        "curriculum_config_id": str(config.curriculum_config_id),
        "env_backend": str(config.env_backend),
        "transport_mode": str(config.transport_mode),
        "account_name_prefix": account_name_prefix,
        "start_wave": int(config.start_wave),
        "ammo": int(config.ammo),
        "prayer_potions": int(config.prayer_potions),
        "sharks": int(config.sharks),
        "tick_cap": int(config.tick_cap),
        "include_future_leakage": bool(config.include_future_leakage),
        "info_payload_mode": str(config.info_payload_mode),
        "instrumentation_enabled": bool(config.instrumentation_enabled),
        "bootstrap": {
            "load_content_scripts": bool(config.bootstrap.load_content_scripts),
            "start_world": bool(config.bootstrap.start_world),
            "install_shutdown_hook": bool(config.bootstrap.install_shutdown_hook),
            "settings_overrides": dict(config.bootstrap.settings_overrides),
        },
    }
    if str(config.transport_mode) == SHARED_MEMORY_TRANSPORT_MODE:
        if transport_parent is None:
            raise ValueError(
                "Shared-memory subprocess transport requires a parent-side shared-memory transport."
            )
        payload["transport"] = transport_parent.spec().to_payload()
    return payload


def _partition_worker_env_counts(*, env_count: int, worker_count: int) -> tuple[int, ...]:
    if env_count <= 0:
        raise ValueError(f"env_count must be > 0, got {env_count}.")
    if worker_count <= 0:
        raise ValueError(f"worker_count must be > 0, got {worker_count}.")
    base, remainder = divmod(int(env_count), int(worker_count))
    counts = [
        base + (1 if worker_index < remainder else 0)
        for worker_index in range(int(worker_count))
    ]
    return tuple(int(count) for count in counts if count > 0)


def _merge_instrumentation_snapshots(
    snapshots: list[dict[str, dict[str, float | int]]],
) -> dict[str, dict[str, float | int]]:
    merged: dict[str, dict[str, float | int]] = {}
    for snapshot in snapshots:
        for bucket, values in snapshot.items():
            current = merged.setdefault(bucket, {"seconds": 0.0, "calls": 0})
            current["seconds"] = float(current["seconds"]) + float(values.get("seconds", 0.0))
            current["calls"] = int(current["calls"]) + int(values.get("calls", 0))
    return merged


def _summarize_distribution(values: list[float]) -> dict[str, float | int]:
    if not values:
        return {
            "count": 0,
            "min": 0.0,
            "median": 0.0,
            "mean": 0.0,
            "p95": 0.0,
            "p99": 0.0,
            "max": 0.0,
        }
    sorted_values = sorted(float(value) for value in values)
    return {
        "count": len(sorted_values),
        "min": float(sorted_values[0]),
        "median": float(statistics.median(sorted_values)),
        "mean": float(statistics.fmean(sorted_values)),
        "p95": float(np.percentile(sorted_values, 95)),
        "p99": float(np.percentile(sorted_values, 99)),
        "max": float(sorted_values[-1]),
    }
