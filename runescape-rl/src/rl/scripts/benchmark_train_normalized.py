from __future__ import annotations

import argparse
import json
from pathlib import Path
from time import perf_counter
from typing import Any

import torch

import pufferlib.pufferl
from fight_caves_rl.policies.registry import build_policy_from_config
from fight_caves_rl.puffer.factory import build_puffer_train_config, load_smoke_train_config, make_vecenv
from fight_caves_rl.puffer.trainer import ConfigurablePuffeRL


class _NullLogger:
    def __init__(self) -> None:
        self.run_id = "normalized_train_bench"
        self.records: list[object] = []
        self.artifact_records: list[object] = []
        self.effective_tags: tuple[str, ...] = ()

    def log(self, *args: Any, **kwargs: Any) -> None:
        return None

    def close(self, *args: Any, **kwargs: Any) -> None:
        return None

    def build_artifact_record(self, *args: Any, **kwargs: Any) -> object:
        raise RuntimeError("normalized benchmark does not build artifacts")

    def update_config(self, *args: Any, **kwargs: Any) -> None:
        return None

    def log_artifact(self, *args: Any, **kwargs: Any) -> None:
        return None

    def finish(self) -> None:
        return None


def _bucket_seconds(snapshot: dict[str, dict[str, float | int]], name: str) -> float:
    bucket = dict(snapshot.get(name, {}))
    return float(bucket.get("seconds", 0.0))


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Run a normalized steady-state train benchmark on the native/v2 fast training path."
    )
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--env-count", type=int, default=None)
    parser.add_argument("--measured-timesteps", type=int, default=16_384)
    parser.add_argument("--warmup-iterations", type=int, default=4)
    parser.add_argument("--backend", choices=("subprocess", "embedded"), default="subprocess")
    parser.add_argument("--enable-tf32", action="store_true")
    args = parser.parse_args()

    config = load_smoke_train_config(args.config)
    if args.env_count is not None:
        config["num_envs"] = int(args.env_count)
    config.setdefault("logging", {})["dashboard"] = False
    config["train"]["checkpoint_interval"] = 1_000_000

    measured_timesteps = int(args.measured_timesteps)
    if measured_timesteps <= 0:
        raise ValueError("measured-timesteps must be > 0")

    startup: dict[str, float] = {}
    vecenv = None
    trainer = None
    final_global_step = 0
    try:
        stage_started = perf_counter()
        vecenv = make_vecenv(config, backend=str(args.backend), instrumentation_enabled=True)
        startup["vecenv_build_seconds"] = perf_counter() - stage_started

        stage_started = perf_counter()
        policy = build_policy_from_config(
            config,
            vecenv.single_observation_space,
            vecenv.single_action_space,
        )
        startup["policy_build_seconds"] = perf_counter() - stage_started

        train_config = build_puffer_train_config(
            config,
            data_dir=Path("/tmp/fc_train_normalized_bench"),
            total_timesteps=measured_timesteps,
        )
        if bool(args.enable_tf32) and str(train_config["device"]) == "cuda" and torch.cuda.is_available():
            torch.set_float32_matmul_precision("high")
            torch.backends.cuda.matmul.allow_tf32 = True
            torch.backends.cudnn.allow_tf32 = True
        policy = policy.to(train_config["device"])

        stage_started = perf_counter()
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
        startup["trainer_init_seconds"] = perf_counter() - stage_started

        warmup_rollout_seconds = 0.0
        warmup_train_seconds = 0.0
        for _ in range(max(0, int(args.warmup_iterations))):
            stage_started = perf_counter()
            trainer.evaluate()
            warmup_rollout_seconds += perf_counter() - stage_started

            stage_started = perf_counter()
            trainer.train()
            warmup_train_seconds += perf_counter() - stage_started

        trainer.reset_instrumentation()

        measured_step_start = int(trainer.global_step)
        measured_step_target = measured_step_start + measured_timesteps
        rollout_seconds = 0.0
        train_seconds = 0.0
        rollout_iterations = 0
        train_iterations = 0
        measured_started = perf_counter()

        while trainer.global_step < measured_step_target:
            stage_started = perf_counter()
            trainer.evaluate()
            rollout_seconds += perf_counter() - stage_started
            rollout_iterations += 1

            stage_started = perf_counter()
            trainer.train()
            train_seconds += perf_counter() - stage_started
            train_iterations += 1

        measured_wall_seconds = perf_counter() - measured_started
        final_global_step = int(trainer.global_step)
        measured_global_steps = final_global_step - measured_step_start
        trainer_snapshot = trainer.instrumentation_snapshot()
        vecenv_topology = (
            dict(vecenv.topology_snapshot()) if hasattr(vecenv, "topology_snapshot") else {}
        )
        close_started = perf_counter()
        trainer.close()
        startup["close_seconds"] = perf_counter() - close_started
    finally:
        if vecenv is not None:
            try:
                vecenv.close()
            except Exception:
                pass

    steps = int(measured_global_steps)
    steady_state_train_sps = 0.0 if train_seconds <= 0.0 else float(steps) / float(train_seconds)
    steady_state_end_to_end_sps = (
        0.0 if measured_wall_seconds <= 0.0 else float(steps) / float(measured_wall_seconds)
    )
    sync_stall_seconds = (
        _bucket_seconds(trainer_snapshot, "eval_rollout_index_extract")
        + _bucket_seconds(trainer_snapshot, "train_loss_metric_items")
        + _bucket_seconds(trainer_snapshot, "train_done_cleanup_explained_variance")
    )
    host_device_transfer_seconds = (
        _bucket_seconds(trainer_snapshot, "eval_tensor_copy")
        + _bucket_seconds(trainer_snapshot, "eval_action_to_numpy")
    )
    train_done_cleanup_seconds = _bucket_seconds(trainer_snapshot, "train_done_cleanup")

    payload = {
        "schema_id": "train_benchmark_normalized_v1",
        "config_path": str(args.config),
        "config_id": str(config["config_id"]),
        "backend": str(args.backend),
        "env_count": int(config["num_envs"]),
        "measured_timesteps": int(measured_timesteps),
        "warmup_iterations": int(args.warmup_iterations),
        "startup_seconds": startup,
        "warmup_seconds": {
            "rollout_seconds": float(warmup_rollout_seconds),
            "train_seconds": float(warmup_train_seconds),
        },
        "steady_state": {
            "measured_global_steps": steps,
            "rollout_seconds": float(rollout_seconds),
            "train_seconds": float(train_seconds),
            "wall_seconds": float(measured_wall_seconds),
            "rollout_iterations": int(rollout_iterations),
            "train_iterations": int(train_iterations),
            "training_sps": float(steady_state_train_sps),
            "end_to_end_sps": float(steady_state_end_to_end_sps),
        },
        "learner_path": {
            "train_device": str(train_config["device"]),
            "torch_version": torch.__version__,
            "torch_cuda_version": torch.version.cuda,
            "cuda_available": bool(torch.cuda.is_available()),
            "cuda_device_name_0": (
                torch.cuda.get_device_name(0) if torch.cuda.is_available() else None
            ),
            "advantage_cuda": bool(pufferlib.pufferl.ADVANTAGE_CUDA),
            "float32_matmul_precision": torch.get_float32_matmul_precision(),
            "allow_tf32_matmul": bool(torch.backends.cuda.matmul.allow_tf32),
            "allow_tf32_cudnn": bool(torch.backends.cudnn.allow_tf32),
            "cudnn_benchmark": bool(torch.backends.cudnn.benchmark),
            "tf32_requested": bool(args.enable_tf32),
        },
        "batch_shape": {
            "num_envs": int(config["num_envs"]),
            "batch_size": int(train_config["batch_size"]),
            "minibatch_size": int(train_config["minibatch_size"]),
            "max_minibatch_size": int(train_config["max_minibatch_size"]),
            "bptt_horizon": int(train_config["bptt_horizon"]),
            "total_minibatches": int(getattr(trainer, "total_minibatches", 0)),
            "segments": int(getattr(trainer, "segments", 0)),
            "minibatch_segments": int(getattr(trainer, "minibatch_segments", 0)),
        },
        "vecenv_topology": vecenv_topology,
        "trainer_bucket_totals": trainer_snapshot,
        "time_breakdown": {
            "rollout_collection_seconds": _bucket_seconds(trainer_snapshot, "eval_total"),
            "rollout_env_wait_seconds": _bucket_seconds(trainer_snapshot, "eval_env_recv"),
            "rollout_write_seconds": _bucket_seconds(trainer_snapshot, "eval_rollout_write"),
            "advantage_gae_seconds": _bucket_seconds(trainer_snapshot, "train_advantage"),
            "policy_forward_seconds": (
                _bucket_seconds(trainer_snapshot, "eval_policy_forward")
                + _bucket_seconds(trainer_snapshot, "train_policy_forward")
            ),
            "loss_compute_seconds": _bucket_seconds(trainer_snapshot, "train_loss_compute"),
            "backward_seconds": _bucket_seconds(trainer_snapshot, "train_backward"),
            "optimizer_step_seconds": _bucket_seconds(trainer_snapshot, "train_optimizer_step"),
            "train_done_cleanup_seconds": train_done_cleanup_seconds,
            "host_device_transfer_seconds": host_device_transfer_seconds,
            "synchronization_stall_seconds": sync_stall_seconds,
            "known_cpu_fallback_seconds": (
                _bucket_seconds(trainer_snapshot, "train_advantage")
                if not bool(pufferlib.pufferl.ADVANTAGE_CUDA)
                else 0.0
            ),
        },
        "synchronization_sources": {
            "eval_rollout_index_extract_seconds": _bucket_seconds(
                trainer_snapshot,
                "eval_rollout_index_extract",
            ),
            "train_loss_metric_items_seconds": _bucket_seconds(
                trainer_snapshot,
                "train_loss_metric_items",
            ),
            "train_done_cleanup_explained_variance_seconds": _bucket_seconds(
                trainer_snapshot,
                "train_done_cleanup_explained_variance",
            ),
        },
        "train_done_cleanup_breakdown": {
            "explained_variance_seconds": _bucket_seconds(
                trainer_snapshot,
                "train_done_cleanup_explained_variance",
            ),
            "log_gate_seconds": _bucket_seconds(
                trainer_snapshot,
                "train_done_cleanup_log_gate",
            ),
            "mean_and_log_seconds": _bucket_seconds(
                trainer_snapshot,
                "train_done_cleanup_mean_and_log",
            ),
            "loss_assign_seconds": _bucket_seconds(
                trainer_snapshot,
                "train_done_cleanup_loss_assign",
            ),
            "dashboard_seconds": _bucket_seconds(
                trainer_snapshot,
                "train_done_cleanup_dashboard",
            ),
            "reset_state_seconds": _bucket_seconds(
                trainer_snapshot,
                "train_done_cleanup_reset_state",
            ),
            "profile_clear_seconds": _bucket_seconds(
                trainer_snapshot,
                "train_done_cleanup_profile_clear",
            ),
        },
        "loss_compute_breakdown": {
            "loss_tensor_math_seconds": _bucket_seconds(
                trainer_snapshot,
                "train_loss_tensor_math",
            ),
            "loss_metric_items_seconds": _bucket_seconds(
                trainer_snapshot,
                "train_loss_metric_items",
            ),
        },
        "cpu_fallback_paths": {
            "pufferlib_advantage_cuda": bool(pufferlib.pufferl.ADVANTAGE_CUDA),
            "pufferlib_advantage_cpu_fallback_active": not bool(pufferlib.pufferl.ADVANTAGE_CUDA),
        },
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
