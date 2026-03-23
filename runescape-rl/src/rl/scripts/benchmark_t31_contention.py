#!/usr/bin/env python3
"""T3.1 — Post-boundary cache-contention confirmation.

Runs repeated Mode 6 (trainer ceiling, fake env) and Mode 7 subprocess
(real env, subprocess backend) measurements to determine whether the B wave
materially reduced the integrated-path train-phase inflation.

Key metric: mode7_train_seconds / mode6_train_seconds (the train-phase inflation ratio).

Pre-B reference (post-T2.1, embedded):
  Mode 6 train: ~15.21s    Mode 7 train: ~60.21s    Inflation: ~3.96x
B1.1 spot-check (subprocess):
  Mode 7 train: ~20.63s    Inflation vs Mode 6: ~1.36x
"""
from __future__ import annotations

import json
import statistics
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path
from time import perf_counter, perf_counter_ns
from typing import Any

import numpy as np


@dataclass(frozen=True)
class SingleRunResult:
    mode: str
    backend: str
    run_index: int
    sps: float
    evaluate_seconds: float
    train_seconds: float
    elapsed_seconds: float
    global_step: int

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class T31Report:
    created_at: str
    num_runs: int
    total_timesteps: int
    env_count: int
    mode6_runs: list[dict[str, Any]]
    mode7_subprocess_runs: list[dict[str, Any]]
    mode6_median_train_s: float
    mode6_median_eval_s: float
    mode6_median_sps: float
    mode7_median_train_s: float
    mode7_median_eval_s: float
    mode7_median_sps: float
    train_phase_inflation: float
    eval_phase_inflation: float
    pre_b_reference: dict[str, Any]

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def run_mode6_single(
    config_path: str | Path,
    env_count: int,
    total_timesteps: int,
    run_index: int,
) -> SingleRunResult:
    """Run one Mode 6 (trainer ceiling) measurement."""
    from fight_caves_rl.benchmarks.train_ceiling_bench import (
        _FakeVecEnv,
        _NullLogger,
        _clamp_train_config_for_benchmark,
    )
    from fight_caves_rl.policies.mlp import MultiDiscreteMLPPolicy
    from fight_caves_rl.puffer.factory import build_puffer_train_config, load_smoke_train_config
    from fight_caves_rl.puffer.trainer import ConfigurablePuffeRL

    config = load_smoke_train_config(config_path)
    config["num_envs"] = env_count
    config.setdefault("logging", {})["dashboard"] = False
    _clamp_train_config_for_benchmark(config, total_timesteps=total_timesteps)

    vecenv = _FakeVecEnv(env_count)
    policy = MultiDiscreteMLPPolicy.from_spaces(
        vecenv.single_observation_space,
        vecenv.single_action_space,
        hidden_size=int(config["policy"]["hidden_size"]),
    )
    train_config = build_puffer_train_config(
        config,
        data_dir=Path(f"/tmp/fc_t31_mode6_run{run_index}"),
        total_timesteps=total_timesteps,
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
        instrumentation_enabled=False,
    )

    evaluate_seconds = 0.0
    train_seconds = 0.0
    started = perf_counter()

    while trainer.global_step < total_timesteps:
        t0 = perf_counter()
        trainer.evaluate()
        evaluate_seconds += perf_counter() - t0

        t0 = perf_counter()
        trainer.train()
        train_seconds += perf_counter() - t0

    global_step = int(trainer.global_step)
    elapsed = perf_counter() - started
    trainer.close()

    return SingleRunResult(
        mode="mode6",
        backend="fake",
        run_index=run_index,
        sps=global_step / elapsed if elapsed > 0 else 0.0,
        evaluate_seconds=evaluate_seconds,
        train_seconds=train_seconds,
        elapsed_seconds=elapsed,
        global_step=global_step,
    )


def run_mode7_subprocess_single(
    config_path: str | Path,
    env_count: int,
    total_timesteps: int,
    run_index: int,
) -> SingleRunResult:
    """Run one Mode 7 (real env, subprocess backend) measurement."""
    import json as _json

    from fight_caves_rl.benchmarks.train_ceiling_bench import (
        _NullLogger,
        _clamp_train_config_for_benchmark,
    )
    from fight_caves_rl.policies.registry import build_policy_from_config
    from fight_caves_rl.puffer.factory import build_puffer_train_config, load_smoke_train_config, make_vecenv
    from fight_caves_rl.puffer.trainer import ConfigurablePuffeRL

    config = load_smoke_train_config(config_path)
    config["num_envs"] = env_count
    config.setdefault("logging", {})["dashboard"] = False
    _clamp_train_config_for_benchmark(config, total_timesteps=total_timesteps)

    vecenv = make_vecenv(config, backend="subprocess")

    policy = build_policy_from_config(
        config,
        vecenv.single_observation_space,
        vecenv.single_action_space,
    )
    train_config = build_puffer_train_config(
        config,
        data_dir=Path(f"/tmp/fc_t31_mode7_run{run_index}"),
        total_timesteps=total_timesteps,
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
        instrumentation_enabled=False,
    )

    evaluate_seconds = 0.0
    train_seconds = 0.0
    started = perf_counter()

    try:
        while trainer.global_step < total_timesteps:
            t0 = perf_counter()
            trainer.evaluate()
            evaluate_seconds += perf_counter() - t0

            t0 = perf_counter()
            trainer.train()
            train_seconds += perf_counter() - t0

        global_step = int(trainer.global_step)
    finally:
        trainer.close()

    elapsed = perf_counter() - started

    return SingleRunResult(
        mode="mode7",
        backend="subprocess",
        run_index=run_index,
        sps=global_step / elapsed if elapsed > 0 else 0.0,
        evaluate_seconds=evaluate_seconds,
        train_seconds=train_seconds,
        elapsed_seconds=elapsed,
        global_step=global_step,
    )


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser(
        description="T3.1 — Post-boundary cache-contention confirmation."
    )
    parser.add_argument("--num-runs", type=int, default=5)
    parser.add_argument("--env-count", type=int, default=64)
    parser.add_argument("--train-timesteps", type=int, default=16384)
    parser.add_argument(
        "--config",
        type=Path,
        default=Path("configs/benchmark/fast_train_v2_mlp64.yaml"),
    )
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    num_runs = args.num_runs
    env_count = args.env_count
    total_timesteps = args.train_timesteps
    config_path = args.config

    print(f"T3.1 — Post-boundary cache-contention confirmation")
    print(f"  Runs: {num_runs}, Envs: {env_count}, Timesteps: {total_timesteps}")
    print()

    # ---- Mode 6 runs ----
    print(f"=== Mode 6 (trainer ceiling, fake env) — {num_runs} runs ===")
    mode6_runs: list[SingleRunResult] = []
    for i in range(num_runs):
        result = run_mode6_single(config_path, env_count, total_timesteps, i)
        mode6_runs.append(result)
        print(
            f"  Run {i}: SPS={result.sps:.1f}, "
            f"eval={result.evaluate_seconds:.2f}s, "
            f"train={result.train_seconds:.2f}s"
        )
    print()

    # ---- Mode 7 subprocess runs ----
    print(f"=== Mode 7 (real env, subprocess backend) — {num_runs} runs ===")
    mode7_runs: list[SingleRunResult] = []
    for i in range(num_runs):
        result = run_mode7_subprocess_single(config_path, env_count, total_timesteps, i)
        mode7_runs.append(result)
        print(
            f"  Run {i}: SPS={result.sps:.1f}, "
            f"eval={result.evaluate_seconds:.2f}s, "
            f"train={result.train_seconds:.2f}s"
        )
    print()

    # ---- Analysis ----
    m6_train = sorted(r.train_seconds for r in mode6_runs)
    m6_eval = sorted(r.evaluate_seconds for r in mode6_runs)
    m6_sps = sorted(r.sps for r in mode6_runs)
    m7_train = sorted(r.train_seconds for r in mode7_runs)
    m7_eval = sorted(r.evaluate_seconds for r in mode7_runs)
    m7_sps = sorted(r.sps for r in mode7_runs)

    m6_train_med = statistics.median(m6_train)
    m6_eval_med = statistics.median(m6_eval)
    m6_sps_med = statistics.median(m6_sps)
    m7_train_med = statistics.median(m7_train)
    m7_eval_med = statistics.median(m7_eval)
    m7_sps_med = statistics.median(m7_sps)

    train_inflation = m7_train_med / m6_train_med if m6_train_med > 0 else float("inf")
    eval_inflation = m7_eval_med / m6_eval_med if m6_eval_med > 0 else float("inf")

    pre_b_reference = {
        "note": "Post-T2.1, embedded backend, before B wave",
        "mode6_train_s": 15.21,
        "mode6_eval_s": 12.0,
        "mode6_sps": 607.9,
        "mode7_embedded_train_s": 60.21,
        "mode7_embedded_eval_s": 18.49,
        "mode7_embedded_sps": 208.2,
        "train_inflation": 3.96,
        "b11_spot_check_mode7_subprocess_train_s": 20.63,
        "b11_spot_check_train_inflation": 1.36,
        "original_pre_t_mode6_train_s": 20.08,
        "original_pre_t_mode7_train_s": 39.68,
        "original_pre_t_train_inflation": 1.98,
    }

    report = T31Report(
        created_at=datetime.now(UTC).isoformat(),
        num_runs=num_runs,
        total_timesteps=total_timesteps,
        env_count=env_count,
        mode6_runs=[r.to_dict() for r in mode6_runs],
        mode7_subprocess_runs=[r.to_dict() for r in mode7_runs],
        mode6_median_train_s=m6_train_med,
        mode6_median_eval_s=m6_eval_med,
        mode6_median_sps=m6_sps_med,
        mode7_median_train_s=m7_train_med,
        mode7_median_eval_s=m7_eval_med,
        mode7_median_sps=m7_sps_med,
        train_phase_inflation=train_inflation,
        eval_phase_inflation=eval_inflation,
        pre_b_reference=pre_b_reference,
    )

    # ---- Print summary ----
    print("=" * 70)
    print("T3.1 RESULTS — Post-boundary cache-contention confirmation")
    print("=" * 70)
    print()
    print(f"Mode 6 (trainer ceiling, fake env) — median of {num_runs} runs:")
    print(f"  Train: {m6_train_med:.2f}s  Eval: {m6_eval_med:.2f}s  SPS: {m6_sps_med:.1f}")
    print(f"  Range: train [{m6_train[0]:.2f}s – {m6_train[-1]:.2f}s]")
    print()
    print(f"Mode 7 (real env, subprocess) — median of {num_runs} runs:")
    print(f"  Train: {m7_train_med:.2f}s  Eval: {m7_eval_med:.2f}s  SPS: {m7_sps_med:.1f}")
    print(f"  Range: train [{m7_train[0]:.2f}s – {m7_train[-1]:.2f}s]")
    print()
    print(f"Train-phase inflation (mode7/mode6): {train_inflation:.2f}x")
    print(f"Eval-phase inflation (mode7/mode6):  {eval_inflation:.2f}x")
    print()
    print("Pre-B comparison:")
    print(f"  Original (pre-T, embedded):    train inflation = 1.98x")
    print(f"  Post-T2.1 (embedded):          train inflation = 3.96x")
    print(f"  B1.1 spot-check (subprocess):  train inflation = 1.36x")
    print(f"  T3.1 authoritative (subproc):  train inflation = {train_inflation:.2f}x")
    print()
    if train_inflation <= 1.10:
        print("VERDICT: Cache contention ELIMINATED (<= 10% inflation).")
        print("Subprocess separation fully addressed the train-phase inflation.")
    elif train_inflation <= 1.25:
        print("VERDICT: Cache contention GREATLY REDUCED (10-25% residual).")
        print("Subprocess separation materially reduced the train-phase inflation.")
        print("Residual may warrant investigation but is not a primary bottleneck.")
    elif train_inflation <= 1.50:
        print("VERDICT: Cache contention PARTIALLY REDUCED (25-50% residual).")
        print("Subprocess separation helped but significant residual remains.")
    else:
        print("VERDICT: Cache contention NOT RESOLVED (>50% inflation persists).")
        print("Further investigation needed.")
    print("=" * 70)

    # ---- Write results ----
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(report.to_dict(), indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    print(f"\nResults written to: {args.output}")


if __name__ == "__main__":
    main()
