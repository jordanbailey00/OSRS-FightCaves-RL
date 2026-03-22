from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
import csv
import json
from pathlib import Path
from time import perf_counter
from typing import Any

import pufferlib.vector

from fight_caves_rl.defaults import DEFAULT_TRAIN_BENCHMARK_CONFIG_PATH
from fight_caves_rl.policies.registry import build_policy_from_config
from fight_caves_rl.puffer.factory import (
    build_puffer_train_config,
    load_smoke_train_config,
    make_vecenv,
    resolve_train_env_backend,
)
from fight_caves_rl.puffer.trainer import ConfigurablePuffeRL
from fight_caves_rl.utils.paths import repo_root, runtime_subdir


class _NullLogger:
    def __init__(self) -> None:
        self.run_id = "hotpath"
        self.records: list[Any] = []
        self.artifact_records: list[Any] = []
        self.effective_tags: tuple[str, ...] = ()

    def log(self, *args: Any, **kwargs: Any) -> None:
        return None

    def close(self, *args: Any, **kwargs: Any) -> None:
        return None

    def build_artifact_record(self, *args: Any, **kwargs: Any) -> object:
        raise RuntimeError("Hot-path profiling does not build artifacts.")

    def update_config(self, *args: Any, **kwargs: Any) -> None:
        return None

    def log_artifact(self, *args: Any, **kwargs: Any) -> None:
        return None

    def finish(self) -> None:
        return None


@dataclass(frozen=True)
class IterationStats:
    count: int
    min_seconds: float
    median_seconds: float
    mean_seconds: float
    p95_seconds: float
    p99_seconds: float
    max_seconds: float


def _timestamp_id() -> str:
    return datetime.now(UTC).strftime("%Y%m%dT%H%M%SZ")


def _summarize(values: list[float]) -> IterationStats:
    if not values:
        return IterationStats(
            count=0,
            min_seconds=0.0,
            median_seconds=0.0,
            mean_seconds=0.0,
            p95_seconds=0.0,
            p99_seconds=0.0,
            max_seconds=0.0,
        )
    ordered = sorted(float(value) for value in values)
    idx95 = min(len(ordered) - 1, max(0, int(round(0.95 * (len(ordered) - 1)))))
    idx99 = min(len(ordered) - 1, max(0, int(round(0.99 * (len(ordered) - 1)))))
    return IterationStats(
        count=len(ordered),
        min_seconds=float(ordered[0]),
        median_seconds=float(_median(ordered)),
        mean_seconds=float(sum(ordered) / len(ordered)),
        p95_seconds=float(ordered[idx95]),
        p99_seconds=float(ordered[idx99]),
        max_seconds=float(ordered[-1]),
    )


def _median(values: list[float]) -> float:
    if not values:
        return 0.0
    midpoint = len(values) // 2
    if len(values) % 2 == 1:
        return float(values[midpoint])
    return float((values[midpoint - 1] + values[midpoint]) / 2.0)


def _bucket_seconds(snapshot: dict[str, dict[str, float | int]], bucket: str) -> float:
    return float(snapshot.get(bucket, {}).get("seconds", 0.0))


def _write_hotspot_csv(
    path: Path,
    *,
    hotspots: list[dict[str, Any]],
) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=("rank", "stage", "seconds", "share_percent", "source"),
        )
        writer.writeheader()
        for row in hotspots:
            writer.writerow(row)


def _write_worker_csv(path: Path, worker_details: list[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=(
                "worker_index",
                "env_count",
                "step_count",
                "step_mean_seconds",
                "step_p95_seconds",
                "step_p99_seconds",
                "step_max_seconds",
                "recv_wait_mean_seconds",
                "recv_wait_p95_seconds",
                "recv_wait_p99_seconds",
                "recv_wait_max_seconds",
            ),
        )
        writer.writeheader()
        for worker in worker_details:
            step_stats = dict(worker.get("step_total_seconds_stats", {}))
            recv_stats = dict(worker.get("recv_wait_seconds_stats", {}))
            writer.writerow(
                {
                    "worker_index": int(worker["worker_index"]),
                    "env_count": int(worker["env_count"]),
                    "step_count": int(step_stats.get("count", 0)),
                    "step_mean_seconds": float(step_stats.get("mean", 0.0)),
                    "step_p95_seconds": float(step_stats.get("p95", 0.0)),
                    "step_p99_seconds": float(step_stats.get("p99", 0.0)),
                    "step_max_seconds": float(step_stats.get("max", 0.0)),
                    "recv_wait_mean_seconds": float(recv_stats.get("mean", 0.0)),
                    "recv_wait_p95_seconds": float(recv_stats.get("p95", 0.0)),
                    "recv_wait_p99_seconds": float(recv_stats.get("p99", 0.0)),
                    "recv_wait_max_seconds": float(recv_stats.get("max", 0.0)),
                }
            )


def _top_hotspots(
    *,
    trainer_snapshot: dict[str, dict[str, float | int]],
    env_detail: dict[str, Any],
    run_wall_seconds: float,
    limit: int = 12,
) -> list[dict[str, Any]]:
    parent = dict(env_detail.get("parent_instrumentation", {}))
    worker_vecenv = dict(env_detail.get("worker_vecenv_instrumentation", {}))
    worker_loop = dict(env_detail.get("worker_loop_instrumentation", {}))
    rows: list[tuple[str, float, str]] = []

    trainer_focus_buckets = (
        "eval_env_recv",
        "eval_policy_forward",
        "eval_tensor_copy",
        "eval_rollout_write",
        "eval_env_send",
        "train_policy_forward",
        "train_backward",
        "train_optimizer_step",
        "train_minibatch_prepare",
        "train_loss_compute",
    )
    for bucket in trainer_focus_buckets:
        seconds = _bucket_seconds(trainer_snapshot, bucket)
        if seconds > 0.0:
            rows.append((bucket, seconds, "trainer"))

    env_focus_buckets = (
        "fast_kernel_apply_actions",
        "fast_kernel_tick",
        "fast_kernel_observe_flat",
        "fast_kernel_projection",
        "fast_vecenv_step_batch_call",
        "fast_vecenv_action_pack",
        "fast_vecenv_apply_step_buffers",
    )
    for bucket in env_focus_buckets:
        seconds = _bucket_seconds(worker_vecenv, bucket)
        if seconds > 0.0:
            rows.append((bucket, seconds, "env_worker"))

    boundary_focus_buckets = (
        "subprocess_parent_recv_wait",
        "subprocess_parent_send_command",
        "subprocess_parent_recv_materialize",
        "subprocess_parent_recv_apply_transition",
        "worker_receive_shared_memory_actions",
        "worker_receive_pipe_actions",
        "worker_serialize_transition_after_step",
        "worker_conn_send_after_step",
    )
    for bucket in boundary_focus_buckets:
        seconds = _bucket_seconds(parent, bucket) + _bucket_seconds(worker_loop, bucket)
        if seconds > 0.0:
            rows.append((bucket, seconds, "transport_boundary"))

    ordered = sorted(rows, key=lambda item: item[1], reverse=True)[: int(limit)]
    hotspots: list[dict[str, Any]] = []
    for rank, (stage, seconds, source) in enumerate(ordered, start=1):
        share_percent = 0.0 if run_wall_seconds <= 0.0 else (seconds / run_wall_seconds) * 100.0
        hotspots.append(
            {
                "rank": rank,
                "stage": stage,
                "seconds": float(seconds),
                "share_percent": float(share_percent),
                "source": source,
            }
        )
    return hotspots


def _drain_pending_vecenv_transition(vecenv: Any) -> bool:
    flag = getattr(vecenv, "flag", None)
    if flag != pufferlib.vector.RECV:
        return False
    try:
        vecenv.recv()
    except Exception:
        return False
    return True


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Profile trainer/env/transport hot-path timing for the default V2 training path."
    )
    parser.add_argument("--config", type=Path, default=DEFAULT_TRAIN_BENCHMARK_CONFIG_PATH)
    parser.add_argument("--total-timesteps", type=int, default=8_192)
    parser.add_argument("--env-count", type=int, default=None)
    parser.add_argument("--subprocess-worker-count", type=int, default=None)
    parser.add_argument("--backend", choices=("subprocess", "embedded"), default="subprocess")
    parser.add_argument("--warmup-iterations", type=int, default=0)
    parser.add_argument("--output-dir", type=Path, default=None)
    args = parser.parse_args()

    root = repo_root()
    config_path = (
        args.config.resolve()
        if args.config.is_absolute()
        else (root / args.config).resolve()
    )
    config = load_smoke_train_config(config_path)
    if args.env_count is not None:
        config["num_envs"] = int(args.env_count)
    if args.subprocess_worker_count is not None:
        config.setdefault("env", {})["subprocess_worker_count"] = int(args.subprocess_worker_count)

    output_dir = (
        args.output_dir.resolve()
        if args.output_dir is not None
        else runtime_subdir("reports", "perf", f"hotpath_attribution_{_timestamp_id()}")
    )
    output_dir.mkdir(parents=True, exist_ok=True)

    vecenv_build_started = perf_counter()
    vecenv = make_vecenv(config, backend=str(args.backend), instrumentation_enabled=True)
    vecenv_build_seconds = perf_counter() - vecenv_build_started
    vecenv_topology = (
        dict(vecenv.topology_snapshot()) if hasattr(vecenv, "topology_snapshot") else {"backend": str(args.backend)}
    )

    policy_build_started = perf_counter()
    policy = build_policy_from_config(
        config,
        vecenv.single_observation_space,
        vecenv.single_action_space,
    )
    policy_build_seconds = perf_counter() - policy_build_started

    train_config = build_puffer_train_config(
        config,
        data_dir=output_dir / "train_data",
        total_timesteps=int(args.total_timesteps),
    )

    trainer_init_started = perf_counter()
    trainer = ConfigurablePuffeRL(
        train_config,
        vecenv,
        policy,
        _NullLogger(),
        dashboard_enabled=False,
        checkpointing_enabled=False,
        profiling_enabled=False,
        utilization_enabled=False,
        logging_enabled=False,
        instrumentation_enabled=True,
    )
    trainer_init_seconds = perf_counter() - trainer_init_started

    warmup_eval_seconds = 0.0
    warmup_train_seconds = 0.0
    run_started = perf_counter()
    evaluate_iteration_seconds: list[float] = []
    train_iteration_seconds: list[float] = []
    global_step_before_measure = int(trainer.global_step)

    trainer_snapshot: dict[str, dict[str, float | int]] = {}
    env_snapshot: dict[str, dict[str, float | int]] = {}
    env_detail: dict[str, Any] = {"worker_details": []}
    drained_pending_transition = False
    try:
        for _ in range(max(0, int(args.warmup_iterations))):
            step_started = perf_counter()
            trainer.evaluate()
            warmup_eval_seconds += perf_counter() - step_started
            step_started = perf_counter()
            trainer.train()
            warmup_train_seconds += perf_counter() - step_started

        trainer.reset_instrumentation()
        global_step_before_measure = int(trainer.global_step)
        run_started = perf_counter()

        while trainer.global_step < int(train_config["total_timesteps"]):
            step_started = perf_counter()
            trainer.evaluate()
            evaluate_iteration_seconds.append(perf_counter() - step_started)

            step_started = perf_counter()
            trainer.train()
            train_iteration_seconds.append(perf_counter() - step_started)

        final_eval_started = perf_counter()
        trainer.evaluate()
        final_eval_seconds = perf_counter() - final_eval_started

        # Trainer evaluate/send loop leaves one pending env response by design.
        # Drain it before issuing worker control commands for instrumentation.
        drained_pending_transition = _drain_pending_vecenv_transition(vecenv)
        trainer_snapshot = trainer.instrumentation_snapshot()
        env_snapshot = vecenv.instrumentation_snapshot() if hasattr(vecenv, "instrumentation_snapshot") else {}
        env_detail = (
            vecenv.instrumentation_detailed_snapshot()
            if hasattr(vecenv, "instrumentation_detailed_snapshot")
            else {"worker_details": []}
        )
    finally:
        try:
            trainer.close()
        finally:
            vecenv.close()

    run_wall_seconds = perf_counter() - run_started
    measured_global_steps = int(trainer.global_step) - int(global_step_before_measure)
    measured_sps = (
        0.0 if run_wall_seconds <= 0.0 else float(measured_global_steps) / float(run_wall_seconds)
    )

    evaluate_stats = _summarize(evaluate_iteration_seconds)
    train_stats = _summarize(train_iteration_seconds)

    trainer_wait_env = _bucket_seconds(trainer_snapshot, "eval_env_recv")
    trainer_send_env = _bucket_seconds(trainer_snapshot, "eval_env_send")
    trainer_policy_forward = (
        _bucket_seconds(trainer_snapshot, "eval_policy_forward")
        + _bucket_seconds(trainer_snapshot, "train_policy_forward")
    )
    trainer_update_compute = (
        _bucket_seconds(trainer_snapshot, "train_backward")
        + _bucket_seconds(trainer_snapshot, "train_optimizer_step")
        + _bucket_seconds(trainer_snapshot, "train_loss_compute")
        + _bucket_seconds(trainer_snapshot, "train_minibatch_prepare")
    )

    worker_vecenv = dict(env_detail.get("worker_vecenv_instrumentation", {}))
    parent_instrumentation = dict(env_detail.get("parent_instrumentation", {}))
    worker_loop_instrumentation = dict(env_detail.get("worker_loop_instrumentation", {}))

    hotspots = _top_hotspots(
        trainer_snapshot=trainer_snapshot,
        env_detail=env_detail,
        run_wall_seconds=run_wall_seconds,
    )

    payload = {
        "schema_id": "perf_hotpath_attribution_v1",
        "created_at": datetime.now(UTC).isoformat(),
        "setup": {
            "config_path": str(config_path),
            "config_id": str(config.get("config_id")),
            "env_backend": resolve_train_env_backend(config),
            "vecenv_backend": str(args.backend),
            "num_envs": int(config["num_envs"]),
            "subprocess_worker_count": int(config.get("env", {}).get("subprocess_worker_count", 1)),
            "transport_mode": str(config.get("env", {}).get("subprocess_transport_mode", "pipe_pickle_v1")),
            "info_payload_mode": str(config.get("env", {}).get("info_payload_mode", "minimal")),
            "policy_id": str(config.get("policy", {}).get("id")),
            "use_rnn": bool(config.get("train", {}).get("use_rnn", False)),
            "total_timesteps": int(train_config["total_timesteps"]),
            "warmup_iterations": int(args.warmup_iterations),
            "instrumentation_enabled": True,
            "vecenv_topology": vecenv_topology,
            "drained_pending_transition_before_snapshot": bool(drained_pending_transition),
        },
        "runner_stage_seconds": {
            "vecenv_build_seconds": float(vecenv_build_seconds),
            "policy_build_seconds": float(policy_build_seconds),
            "trainer_init_seconds": float(trainer_init_seconds),
            "warmup_eval_seconds": float(warmup_eval_seconds),
            "warmup_train_seconds": float(warmup_train_seconds),
            "measured_run_wall_seconds": float(run_wall_seconds),
            "final_eval_seconds": float(final_eval_seconds),
        },
        "throughput": {
            "measured_global_steps": int(measured_global_steps),
            "measured_env_steps_per_second": float(measured_sps),
        },
        "iteration_timing": {
            "evaluate": asdict(evaluate_stats),
            "train": asdict(train_stats),
        },
        "trainer_bucket_totals": trainer_snapshot,
        "env_hot_path_bucket_totals": env_snapshot,
        "env_hot_path_detailed": env_detail,
        "derived_splits": {
            "trainer_wait_for_env_seconds": float(trainer_wait_env),
            "trainer_send_actions_seconds": float(trainer_send_env),
            "trainer_policy_forward_seconds": float(trainer_policy_forward),
            "trainer_update_compute_seconds": float(trainer_update_compute),
            "trainer_non_wait_seconds": float(max(0.0, run_wall_seconds - trainer_wait_env)),
            "transport_parent_recv_wait_seconds": float(
                _bucket_seconds(parent_instrumentation, "subprocess_parent_recv_wait")
            ),
            "transport_parent_send_command_seconds": float(
                _bucket_seconds(parent_instrumentation, "subprocess_parent_send_command")
            ),
            "transport_worker_send_seconds": float(
                _bucket_seconds(worker_loop_instrumentation, "worker_conn_send_after_step")
            ),
            "transport_worker_serialize_seconds": float(
                _bucket_seconds(worker_loop_instrumentation, "worker_serialize_transition_after_step")
            ),
            "env_fast_kernel_apply_actions_seconds": float(
                _bucket_seconds(worker_vecenv, "fast_kernel_apply_actions")
            ),
            "env_fast_kernel_tick_seconds": float(_bucket_seconds(worker_vecenv, "fast_kernel_tick")),
            "env_fast_kernel_observe_flat_seconds": float(
                _bucket_seconds(worker_vecenv, "fast_kernel_observe_flat")
            ),
            "env_fast_kernel_projection_seconds": float(
                _bucket_seconds(worker_vecenv, "fast_kernel_projection")
            ),
            "env_fast_kernel_total_seconds": float(_bucket_seconds(worker_vecenv, "fast_kernel_total")),
            "env_action_pack_seconds": float(_bucket_seconds(worker_vecenv, "fast_vecenv_action_pack")),
            "env_apply_step_buffers_seconds": float(
                _bucket_seconds(worker_vecenv, "fast_vecenv_apply_step_buffers")
            ),
            "env_step_batch_call_seconds": float(
                _bucket_seconds(worker_vecenv, "fast_vecenv_step_batch_call")
            ),
        },
        "hotspots_ranked": hotspots,
    }

    json_path = output_dir / "perf_hotpath_attribution.json"
    json_path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    hotspots_csv_path = output_dir / "hotspots_ranked.csv"
    _write_hotspot_csv(hotspots_csv_path, hotspots=hotspots)

    worker_csv_path = output_dir / "worker_skew_stats.csv"
    _write_worker_csv(worker_csv_path, list(payload["env_hot_path_detailed"].get("worker_details", [])))

    print(f"Wrote hot-path attribution JSON: {json_path}")
    print(f"Wrote ranked hotspots CSV: {hotspots_csv_path}")
    print(f"Wrote worker skew CSV: {worker_csv_path}")


if __name__ == "__main__":
    main()
