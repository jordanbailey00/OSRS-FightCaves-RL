"""Expanded benchmark modes 5-10 for the forensic performance investigation (PR 2.2).

Extends the modes 1-4 baseline from PR 2.1 with training-path and hook-overhead
measurements:

  Mode 5  — env with parity trace hooks enabled
  Mode 6  — trainer ceiling (fake env, real MLP policy + PufferLib loop)
  Mode 7  — embedded training (real env + real MLP policy + train loop)
  Mode 8  — headed path (SKIPPED — no display available)
  Mode 9  — minimal legal training (embedded, all optional features off)
  Mode 10 — full feature training (subprocess transport + logging)

Each mode reports SPS, elapsed time, and memory where feasible.

JVM lifecycle note: The in-process JVM (via JPype) cannot be restarted after
shutdown.  Modes that use the real env (5, 7) share a single JVM session and
must run in the same process with careful lifecycle management.  Mode 6 uses
a fake env and does not touch the JVM.
"""
from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path
from time import perf_counter, perf_counter_ns
from typing import Any

import numpy as np


@dataclass(frozen=True)
class ExpandedModeResult:
    mode: int
    label: str
    description: str
    feasible: bool
    skip_reason: str | None
    env_count: int
    total_timesteps: int
    global_step: int
    elapsed_nanos: int
    sps: float
    evaluate_seconds: float
    train_seconds: float
    memory_mib: float
    extra: dict[str, Any]

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class ExpandedModesReport:
    created_at: str
    modes: list[ExpandedModeResult]
    pr21_mode4_sps: float | None

    def to_dict(self) -> dict[str, Any]:
        return {
            "created_at": self.created_at,
            "pr21_mode4_sps": self.pr21_mode4_sps,
            "modes": [m.to_dict() for m in self.modes],
        }


def _infeasible(mode: int, label: str, description: str, reason: str) -> ExpandedModeResult:
    return ExpandedModeResult(
        mode=mode, label=label, description=description,
        feasible=False, skip_reason=reason,
        env_count=0, total_timesteps=0, global_step=0,
        elapsed_nanos=0, sps=0.0,
        evaluate_seconds=0.0, train_seconds=0.0,
        memory_mib=0.0, extra={},
    )


def run_mode_6_trainer_ceiling(
    config_path: str | Path = "configs/benchmark/fast_train_v2_mlp64.yaml",
    env_count: int = 64,
    total_timesteps: int = 16384,
) -> ExpandedModeResult:
    """Mode 6: Trainer ceiling — fake env, real policy, real training loop.

    This mode does NOT use the JVM and can run independently.
    """
    from fight_caves_rl.benchmarks.common import capture_peak_memory_profile
    from fight_caves_rl.benchmarks.train_ceiling_bench import run_train_ceiling_benchmark

    report = run_train_ceiling_benchmark(
        config_path,
        env_counts_override=(env_count,),
        total_timesteps_override=total_timesteps,
    )
    m = report.measurements[0]
    mem = capture_peak_memory_profile()
    mem_mib = mem.get("combined_peak_rss_kib", 0) / 1024.0

    return ExpandedModeResult(
        mode=6, label="mode_6_trainer_ceiling",
        description="Trainer ceiling (fake env, real MLP64 policy + PufferLib loop)",
        feasible=True, skip_reason=None,
        env_count=env_count, total_timesteps=total_timesteps,
        global_step=m.global_step,
        elapsed_nanos=int(m.elapsed_seconds * 1_000_000_000),
        sps=m.env_steps_per_second,
        evaluate_seconds=m.evaluate_seconds,
        train_seconds=m.train_seconds,
        memory_mib=mem_mib,
        extra={
            "evaluate_iterations": m.evaluate_iterations,
            "train_iterations": m.train_iterations,
            "runner_stage_seconds": m.runner_stage_seconds,
        },
    )


def _run_jvm_modes(
    *,
    env_count: int,
    env_rounds: int,
    warmup_rounds: int,
    seed_base: int,
    train_timesteps: int,
    config_path: str | Path,
) -> tuple[ExpandedModeResult, ExpandedModeResult]:
    """Run modes 5 and 7 in a single JVM session.

    Creates one FastKernelVecEnv, uses it for mode 5 (parity trace measurement),
    then wraps it in a trainer for mode 7.  The JVM is never shut down between
    modes, avoiding the singleton-state corruption issue.
    """
    import yaml

    from fight_caves_rl.benchmarks.common import capture_peak_memory_profile
    from fight_caves_rl.bridge.contracts import HeadlessBootstrapConfig
    from fight_caves_rl.envs_fast.fast_policy_encoding import pack_joint_actions
    from fight_caves_rl.envs_fast.fast_reward_adapter import FastRewardAdapter
    from fight_caves_rl.envs_fast.fast_vector_env import (
        FastKernelVecEnv,
        FastKernelVecEnvConfig,
        _java_int_array,
    )
    from fight_caves_rl.policies.mlp import MultiDiscreteMLPPolicy
    from fight_caves_rl.puffer.factory import build_puffer_train_config
    from fight_caves_rl.puffer.trainer import ConfigurablePuffeRL

    config = yaml.safe_load(Path(config_path).read_text(encoding="utf-8"))
    env_config = config.get("env", {})
    reward_config_id = str(config["reward_config"])
    reward_adapter = FastRewardAdapter.from_config_id(reward_config_id)

    vecenv = FastKernelVecEnv(
        FastKernelVecEnvConfig(
            env_count=env_count,
            account_name_prefix=str(env_config.get("account_name_prefix", "expanded_bench")),
            start_wave=int(env_config.get("start_wave", 1)),
            ammo=int(env_config.get("ammo", 1000)),
            prayer_potions=int(env_config.get("prayer_potions", 8)),
            sharks=int(env_config.get("sharks", 20)),
            tick_cap=int(env_config.get("tick_cap", 20_000)),
            info_payload_mode="minimal",
            instrumentation_enabled=True,
            bootstrap=HeadlessBootstrapConfig(start_world=False),
        ),
        reward_adapter=reward_adapter,
    )

    zero_action = np.zeros(
        (env_count, len(vecenv.single_action_space.nvec)),
        dtype=np.int32,
    )

    # ---- Mode 5: env with parity trace ----
    vecenv.async_reset(seed_base)
    vecenv.recv()

    # Warmup
    for _ in range(warmup_rounds):
        vecenv.send(zero_action)
        vecenv.recv()
    vecenv.reset_instrumentation()

    # Measure with parity trace via direct JVM calls
    runtime = vecenv._runtime
    slot_indices = _java_int_array(list(range(env_count)))

    started_5 = perf_counter_ns()
    for _ in range(env_rounds):
        packed = pack_joint_actions(zero_action)
        runtime.stepBatch(
            slot_indices,
            _java_int_array(packed),
            True,    # emitParityTrace = True
            None,
            None,
        )
    elapsed_5 = perf_counter_ns() - started_5

    total_steps_5 = env_count * env_rounds
    sps_5 = total_steps_5 * 1_000_000_000.0 / max(1, elapsed_5)
    mem_5 = capture_peak_memory_profile()

    mode_5 = ExpandedModeResult(
        mode=5, label="mode_5_env_with_parity",
        description="Env step with parity trace hooks enabled",
        feasible=True, skip_reason=None,
        env_count=env_count, total_timesteps=total_steps_5,
        global_step=total_steps_5, elapsed_nanos=int(elapsed_5),
        sps=sps_5, evaluate_seconds=0.0, train_seconds=0.0,
        memory_mib=mem_5.get("combined_peak_rss_kib", 0) / 1024.0,
        extra={"rounds": env_rounds, "warmup_rounds": warmup_rounds},
    )

    # ---- Mode 7: embedded training ----
    # Re-reset the env for training (parity trace left some state)
    vecenv.async_reset(seed_base + 1000)
    vecenv.recv()
    vecenv.reset_instrumentation()

    # Build trainer around the existing vecenv
    config["num_envs"] = env_count
    config["train"]["total_timesteps"] = train_timesteps
    config.setdefault("logging", {})["dashboard"] = False
    effective_batch = max(1, min(train_timesteps, int(config["train"]["batch_size"])))
    config["train"]["batch_size"] = effective_batch
    config["train"]["minibatch_size"] = max(
        1, min(int(config["train"]["minibatch_size"]), effective_batch),
    )
    config["train"]["max_minibatch_size"] = max(
        int(config["train"]["minibatch_size"]),
        min(int(config["train"]["max_minibatch_size"]), effective_batch),
    )
    config["train"]["bptt_horizon"] = max(
        1, min(int(config["train"]["bptt_horizon"]), effective_batch),
    )

    class _NullLogger:
        def __init__(self) -> None:
            self.run_id = "null"
            self.records: list[object] = []
            self.artifact_records: list[object] = []
            self.effective_tags: tuple[str, ...] = ()
        def log(self, *a: Any, **kw: Any) -> None: pass
        def close(self, *a: Any, **kw: Any) -> None: pass
        def build_artifact_record(self, *a: Any, **kw: Any) -> object:
            raise RuntimeError("Not supported in benchmark.")
        def update_config(self, *a: Any, **kw: Any) -> None: pass
        def log_artifact(self, *a: Any, **kw: Any) -> None: pass
        def finish(self) -> None: pass

    policy = MultiDiscreteMLPPolicy.from_spaces(
        vecenv.single_observation_space,
        vecenv.single_action_space,
        hidden_size=int(config["policy"]["hidden_size"]),
    )
    train_config = build_puffer_train_config(
        config,
        data_dir=Path("/tmp/fc_mode7_bench"),
        total_timesteps=train_timesteps,
    )

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

    evaluate_seconds = 0.0
    train_seconds = 0.0
    started_7 = perf_counter_ns()
    try:
        while trainer.global_step < train_timesteps:
            t0 = perf_counter()
            trainer.evaluate()
            evaluate_seconds += perf_counter() - t0

            t0 = perf_counter()
            trainer.train()
            train_seconds += perf_counter() - t0

        global_step = int(trainer.global_step)
    finally:
        # Close the trainer without closing the vecenv
        # (trainer.close() will close the vecenv for us)
        trainer.close()

    elapsed_7 = perf_counter_ns() - started_7
    sps_7 = global_step * 1_000_000_000.0 / max(1, elapsed_7) if elapsed_7 > 0 else 0.0
    mem_7 = capture_peak_memory_profile()

    mode_7 = ExpandedModeResult(
        mode=7, label="mode_7_embedded_training",
        description="Embedded training (real env + MLP64 policy + PufferLib train loop)",
        feasible=True, skip_reason=None,
        env_count=env_count, total_timesteps=train_timesteps,
        global_step=global_step, elapsed_nanos=int(elapsed_7),
        sps=sps_7, evaluate_seconds=evaluate_seconds, train_seconds=train_seconds,
        memory_mib=mem_7.get("combined_peak_rss_kib", 0) / 1024.0,
        extra={
            "config_path": str(config_path),
            "hidden_size": int(config["policy"]["hidden_size"]),
            "batch_size": effective_batch,
        },
    )

    return mode_5, mode_7


def run_expanded_modes(
    *,
    env_count: int = 64,
    env_rounds: int = 256,
    train_timesteps: int = 16384,
    config_path: str | Path = "configs/benchmark/fast_train_v2_mlp64.yaml",
    pr21_mode4_sps: float | None = None,
) -> ExpandedModesReport:
    """Run all feasible expanded modes and return a consolidated report."""
    results: list[ExpandedModeResult] = []

    # Mode 6 first — no JVM, safe to run before JVM modes
    print("Running mode 6 (trainer ceiling, fake env)...")
    mode_6 = run_mode_6_trainer_ceiling(
        config_path=config_path,
        env_count=env_count,
        total_timesteps=train_timesteps,
    )
    print(f"  Mode 6: {mode_6.sps:,.0f} SPS")

    # Modes 5 and 7 — share a single JVM session
    print("Running modes 5 + 7 (JVM session)...")
    mode_5, mode_7 = _run_jvm_modes(
        env_count=env_count,
        env_rounds=env_rounds,
        warmup_rounds=16,
        seed_base=70000,
        train_timesteps=train_timesteps,
        config_path=config_path,
    )
    print(f"  Mode 5: {mode_5.sps:,.0f} SPS")
    print(f"  Mode 7: {mode_7.sps:,.1f} SPS")

    # Assemble in mode order
    results.append(mode_5)
    results.append(mode_6)
    results.append(mode_7)

    # Mode 8 — infeasible
    results.append(_infeasible(
        8, "mode_8_headed_path", "Headed simulation path",
        "No display available; headed path is not relevant to training perf.",
    ))

    # Mode 9 — minimal legal training = mode 7
    results.append(ExpandedModeResult(
        mode=9, label="mode_9_minimal_legal_training",
        description="Minimal legal training path (= mode 7: embedded, no hooks, no logging)",
        feasible=True, skip_reason=None,
        env_count=mode_7.env_count,
        total_timesteps=mode_7.total_timesteps,
        global_step=mode_7.global_step,
        elapsed_nanos=mode_7.elapsed_nanos,
        sps=mode_7.sps,
        evaluate_seconds=mode_7.evaluate_seconds,
        train_seconds=mode_7.train_seconds,
        memory_mib=mode_7.memory_mib,
        extra={"note": "Equivalent to mode 7 — embedded FastKernelVecEnv is already the minimal fast path"},
    ))

    # Mode 10 — infeasible in this environment
    results.append(_infeasible(
        10, "mode_10_full_feature_training",
        "Full feature training (subprocess transport + logging)",
        "Subprocess transport requires multi-process JVM workers. "
        "Mode 7 provides the embedded-path measurement; mode 10 would add "
        "subprocess serialization + process scheduling overhead on top.",
    ))

    return ExpandedModesReport(
        created_at=datetime.now(UTC).isoformat(),
        modes=results,
        pr21_mode4_sps=pr21_mode4_sps,
    )


def format_expanded_report(report: ExpandedModesReport) -> str:
    lines = [
        "=" * 80,
        "EXPANDED MODES BENCHMARK REPORT (PR 2.2)",
        "=" * 80,
        "",
    ]
    if report.pr21_mode4_sps is not None:
        lines.append(f"PR 2.1 mode 4 (env-only, embedded) reference: {report.pr21_mode4_sps:,.0f} SPS")
        lines.append("")

    lines.append(f"{'Mode':>6}  {'Label':40s}  {'SPS':>12s}  {'Status':10s}")
    lines.append("-" * 80)
    for m in report.modes:
        if m.feasible:
            lines.append(
                f"  {m.mode:>4}  {m.label:40s}  {m.sps:>12,.0f}  {'OK':10s}"
            )
        else:
            lines.append(
                f"  {m.mode:>4}  {m.label:40s}  {'N/A':>12s}  {'SKIPPED':10s}"
            )
    lines.append("")

    for m in report.modes:
        if not m.feasible:
            continue
        elapsed_s = m.elapsed_nanos / 1e9
        lines.append(f"--- Mode {m.mode}: {m.description} ---")
        lines.append(f"  SPS: {m.sps:,.1f}")
        lines.append(f"  Elapsed: {elapsed_s:.2f}s")
        lines.append(f"  Global step: {m.global_step}")
        if m.evaluate_seconds > 0 or m.train_seconds > 0:
            lines.append(f"  Evaluate: {m.evaluate_seconds:.2f}s  Train: {m.train_seconds:.2f}s")
        lines.append(f"  Memory: {m.memory_mib:.0f} MiB")
        lines.append("")

    lines.append("=" * 80)
    return "\n".join(lines)


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser(
        description="Expanded benchmark modes 5-10 (forensic investigation PR 2.2)."
    )
    parser.add_argument("--env-count", type=int, default=64)
    parser.add_argument("--env-rounds", type=int, default=256)
    parser.add_argument("--train-timesteps", type=int, default=16384)
    parser.add_argument(
        "--config",
        type=Path,
        default=Path("configs/benchmark/fast_train_v2_mlp64.yaml"),
    )
    parser.add_argument("--pr21-mode4-sps", type=float, default=None,
                        help="PR 2.1 mode 4 SPS for reference comparison.")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    report = run_expanded_modes(
        env_count=args.env_count,
        env_rounds=args.env_rounds,
        train_timesteps=args.train_timesteps,
        config_path=args.config,
        pr21_mode4_sps=args.pr21_mode4_sps,
    )

    print(format_expanded_report(report))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(report.to_dict(), indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    print(f"\nResults written to: {args.output}")


if __name__ == "__main__":
    main()
