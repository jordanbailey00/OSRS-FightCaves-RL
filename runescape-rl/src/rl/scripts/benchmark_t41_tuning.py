#!/usr/bin/env python3
"""T4.1 — Subprocess/runtime tuning experiments.

Tests tuning axes that may reduce the remaining 1.73× train-phase inflation:
  1. Worker count: 1, 2, 4 (baseline), 8
  2. JVM heap: -Xmx128m, -Xmx64m (via FC_RL_JVM_OPTS)
  3. JVM GC: -XX:+UseSerialGC (no background GC threads)
  4. Combined best settings

Each configuration runs Mode 7 subprocess N times and reports median train/eval/SPS.
Mode 6 baseline is run once at the start for reference.
"""
from __future__ import annotations

import json
import os
import statistics
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path
from time import perf_counter
from typing import Any


@dataclass(frozen=True)
class RunResult:
    sps: float
    evaluate_seconds: float
    train_seconds: float
    elapsed_seconds: float
    global_step: int

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class ExperimentResult:
    label: str
    worker_count: int
    jvm_opts: str
    runs: list[dict[str, Any]]
    median_train_s: float
    median_eval_s: float
    median_sps: float
    train_range: tuple[float, float]

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def _run_mode6(config_path: str | Path, env_count: int, total_timesteps: int) -> RunResult:
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
        vecenv.single_observation_space, vecenv.single_action_space,
        hidden_size=int(config["policy"]["hidden_size"]),
    )
    train_config = build_puffer_train_config(
        config, data_dir=Path("/tmp/fc_t41_mode6"), total_timesteps=total_timesteps,
    )
    trainer = ConfigurablePuffeRL(
        train_config, vecenv, policy, _NullLogger(),
        dashboard_enabled=False, checkpointing_enabled=False,
        profiling_enabled=False, utilization_enabled=False,
        logging_enabled=False, instrumentation_enabled=False,
    )
    eval_s = train_s = 0.0
    started = perf_counter()
    while trainer.global_step < total_timesteps:
        t0 = perf_counter(); trainer.evaluate(); eval_s += perf_counter() - t0
        t0 = perf_counter(); trainer.train(); train_s += perf_counter() - t0
    gs = int(trainer.global_step)
    elapsed = perf_counter() - started
    trainer.close()
    return RunResult(gs / elapsed if elapsed > 0 else 0, eval_s, train_s, elapsed, gs)


def _run_mode7(
    config_path: str | Path,
    env_count: int,
    total_timesteps: int,
    worker_count: int,
    jvm_opts: str,
    run_index: int,
) -> RunResult:
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
    config["env"]["subprocess_worker_count"] = worker_count
    config.setdefault("logging", {})["dashboard"] = False
    _clamp_train_config_for_benchmark(config, total_timesteps=total_timesteps)

    old_opts = os.environ.get("FC_RL_JVM_OPTS", "")
    if jvm_opts:
        os.environ["FC_RL_JVM_OPTS"] = jvm_opts
    elif "FC_RL_JVM_OPTS" in os.environ:
        del os.environ["FC_RL_JVM_OPTS"]

    try:
        vecenv = make_vecenv(config, backend="subprocess")
    finally:
        if old_opts:
            os.environ["FC_RL_JVM_OPTS"] = old_opts
        elif "FC_RL_JVM_OPTS" in os.environ:
            del os.environ["FC_RL_JVM_OPTS"]

    policy = build_policy_from_config(
        config, vecenv.single_observation_space, vecenv.single_action_space,
    )
    train_config = build_puffer_train_config(
        config, data_dir=Path(f"/tmp/fc_t41_m7_{run_index}"), total_timesteps=total_timesteps,
    )
    trainer = ConfigurablePuffeRL(
        train_config, vecenv, policy, _NullLogger(),
        dashboard_enabled=False, checkpointing_enabled=False,
        profiling_enabled=False, utilization_enabled=False,
        logging_enabled=False, instrumentation_enabled=False,
    )
    eval_s = train_s = 0.0
    started = perf_counter()
    try:
        while trainer.global_step < total_timesteps:
            t0 = perf_counter(); trainer.evaluate(); eval_s += perf_counter() - t0
            t0 = perf_counter(); trainer.train(); train_s += perf_counter() - t0
        gs = int(trainer.global_step)
    finally:
        trainer.close()
    elapsed = perf_counter() - started
    return RunResult(gs / elapsed if elapsed > 0 else 0, eval_s, train_s, elapsed, gs)


def run_experiment(
    label: str,
    config_path: str | Path,
    env_count: int,
    total_timesteps: int,
    worker_count: int,
    jvm_opts: str,
    num_runs: int,
) -> ExperimentResult:
    print(f"\n--- {label} (workers={worker_count}, jvm_opts='{jvm_opts}') ---")
    runs: list[RunResult] = []
    for i in range(num_runs):
        r = _run_mode7(config_path, env_count, total_timesteps, worker_count, jvm_opts, i)
        runs.append(r)
        print(f"  Run {i}: SPS={r.sps:.1f}, eval={r.evaluate_seconds:.2f}s, train={r.train_seconds:.2f}s")

    trains = sorted(r.train_seconds for r in runs)
    evals = sorted(r.evaluate_seconds for r in runs)
    spss = sorted(r.sps for r in runs)

    return ExperimentResult(
        label=label,
        worker_count=worker_count,
        jvm_opts=jvm_opts,
        runs=[r.to_dict() for r in runs],
        median_train_s=statistics.median(trains),
        median_eval_s=statistics.median(evals),
        median_sps=statistics.median(spss),
        train_range=(trains[0], trains[-1]),
    )


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser(description="T4.1 — Subprocess tuning experiments.")
    parser.add_argument("--num-runs", type=int, default=3)
    parser.add_argument("--env-count", type=int, default=64)
    parser.add_argument("--train-timesteps", type=int, default=16384)
    parser.add_argument("--config", type=Path, default=Path("configs/benchmark/fast_train_v2_mlp64.yaml"))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    n = args.num_runs
    ec = args.env_count
    ts = args.train_timesteps
    cp = args.config

    print("T4.1 — Subprocess/runtime tuning experiments")
    print(f"  Runs per config: {n}, Envs: {ec}, Timesteps: {ts}\n")

    # Mode 6 baseline
    print("=== Mode 6 baseline ===")
    m6 = _run_mode6(cp, ec, ts)
    print(f"  Mode 6: SPS={m6.sps:.1f}, eval={m6.evaluate_seconds:.2f}s, train={m6.train_seconds:.2f}s")

    experiments: list[ExperimentResult] = []

    # Experiment 1: Worker count sweep
    for wc in [1, 2, 4, 8]:
        experiments.append(run_experiment(
            f"workers={wc}", cp, ec, ts, wc, "", n,
        ))

    # Experiment 2: JVM heap reduction (128m only; 64m OOMs)
    experiments.append(run_experiment(
        "workers=4, -Xmx128m", cp, ec, ts, 4, "-Xmx128m", n,
    ))
    experiments.append(run_experiment(
        "workers=1, -Xmx128m", cp, ec, ts, 1, "-Xmx128m", n,
    ))

    # Experiment 3: Serial GC (no background GC threads)
    experiments.append(run_experiment(
        "workers=4, SerialGC", cp, ec, ts, 4, "-XX:+UseSerialGC", n,
    ))
    experiments.append(run_experiment(
        "workers=1, SerialGC", cp, ec, ts, 1, "-XX:+UseSerialGC", n,
    ))

    # Experiment 4: Combined best — 1 worker + small heap + serial GC
    experiments.append(run_experiment(
        "workers=1, -Xmx128m, SerialGC", cp, ec, ts, 1,
        "-Xmx128m -XX:+UseSerialGC", n,
    ))

    # Summary
    print("\n" + "=" * 80)
    print("T4.1 SUMMARY")
    print("=" * 80)
    print(f"\nMode 6 baseline: train={m6.train_seconds:.2f}s, eval={m6.evaluate_seconds:.2f}s, SPS={m6.sps:.1f}")
    print(f"\n{'Config':<40s}  {'Med Train':>10s}  {'Med Eval':>10s}  {'Med SPS':>10s}  {'Inflation':>10s}")
    print("-" * 90)
    for e in experiments:
        inflation = e.median_train_s / m6.train_seconds if m6.train_seconds > 0 else 0
        print(f"{e.label:<40s}  {e.median_train_s:>10.2f}s  {e.median_eval_s:>10.2f}s  {e.median_sps:>10.1f}  {inflation:>10.2f}x")
    print("=" * 80)

    # Write results
    report = {
        "created_at": datetime.now(UTC).isoformat(),
        "num_runs_per_experiment": n,
        "total_timesteps": ts,
        "env_count": ec,
        "mode6_baseline": m6.to_dict(),
        "experiments": [e.to_dict() for e in experiments],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    print(f"\nResults written to: {args.output}")


if __name__ == "__main__":
    main()
