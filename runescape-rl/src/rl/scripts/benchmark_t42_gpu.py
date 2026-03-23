#!/usr/bin/env python3
"""T4.2 — GPU feasibility benchmark.

Compares Mode 6 (trainer ceiling) and Mode 7 (subprocess training)
on CPU vs GPU to determine whether GPU eliminates the remaining
~1.5-1.7× train-phase inflation.

Reports: SPS, train time, eval time, VRAM usage, stability.
"""
from __future__ import annotations

import json
import statistics
import sys
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path
from time import perf_counter
from typing import Any

import torch


@dataclass(frozen=True)
class RunResult:
    mode: str
    device: str
    run_index: int
    sps: float
    evaluate_seconds: float
    train_seconds: float
    elapsed_seconds: float
    global_step: int
    vram_peak_mb: float
    stable: bool
    notes: str

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def _run_mode6(
    config_path: str | Path, env_count: int, total_timesteps: int,
    device: str, run_index: int,
) -> RunResult:
    from fight_caves_rl.benchmarks.train_ceiling_bench import (
        _FakeVecEnv, _NullLogger, _clamp_train_config_for_benchmark,
    )
    from fight_caves_rl.policies.mlp import MultiDiscreteMLPPolicy
    from fight_caves_rl.puffer.factory import build_puffer_train_config, load_smoke_train_config
    from fight_caves_rl.puffer.trainer import ConfigurablePuffeRL

    config = load_smoke_train_config(config_path)
    config["num_envs"] = env_count
    config["train"]["device"] = device
    config.setdefault("logging", {})["dashboard"] = False
    _clamp_train_config_for_benchmark(config, total_timesteps=total_timesteps)

    if device == "cuda":
        torch.cuda.reset_peak_memory_stats()

    vecenv = _FakeVecEnv(env_count)
    policy = MultiDiscreteMLPPolicy.from_spaces(
        vecenv.single_observation_space, vecenv.single_action_space,
        hidden_size=int(config["policy"]["hidden_size"]),
    )
    if device == "cuda":
        policy = policy.to("cuda")

    train_config = build_puffer_train_config(
        config, data_dir=Path(f"/tmp/fc_t42_m6_{device}_{run_index}"),
        total_timesteps=total_timesteps,
    )
    trainer = ConfigurablePuffeRL(
        train_config, vecenv, policy, _NullLogger(),
        dashboard_enabled=False, checkpointing_enabled=False,
        profiling_enabled=False, utilization_enabled=False,
        logging_enabled=False, instrumentation_enabled=False,
    )

    eval_s = train_s = 0.0
    stable = True
    started = perf_counter()
    try:
        while trainer.global_step < total_timesteps:
            t0 = perf_counter(); trainer.evaluate(); eval_s += perf_counter() - t0
            t0 = perf_counter(); trainer.train(); train_s += perf_counter() - t0
        gs = int(trainer.global_step)
    except Exception as exc:
        stable = False
        gs = int(trainer.global_step)
        print(f"  ERROR: {exc}")
    finally:
        trainer.close()

    elapsed = perf_counter() - started
    vram = torch.cuda.max_memory_allocated() / 1024**2 if device == "cuda" else 0.0

    # Check for NaN/Inf in losses
    losses = getattr(trainer, "losses", {})
    for k, v in losses.items():
        if isinstance(v, float) and (v != v or abs(v) == float("inf")):
            stable = False
            break

    return RunResult(
        mode="mode6", device=device, run_index=run_index,
        sps=gs / elapsed if elapsed > 0 else 0,
        evaluate_seconds=eval_s, train_seconds=train_s,
        elapsed_seconds=elapsed, global_step=gs,
        vram_peak_mb=vram, stable=stable, notes="",
    )


def _run_mode7(
    config_path: str | Path, env_count: int, total_timesteps: int,
    device: str, run_index: int,
) -> RunResult:
    import os

    from fight_caves_rl.benchmarks.train_ceiling_bench import (
        _NullLogger, _clamp_train_config_for_benchmark,
    )
    from fight_caves_rl.policies.registry import build_policy_from_config
    from fight_caves_rl.puffer.factory import (
        build_puffer_train_config, load_smoke_train_config, make_vecenv,
    )
    from fight_caves_rl.puffer.trainer import ConfigurablePuffeRL

    config = load_smoke_train_config(config_path)
    config["num_envs"] = env_count
    config["train"]["device"] = device
    config.setdefault("logging", {})["dashboard"] = False
    _clamp_train_config_for_benchmark(config, total_timesteps=total_timesteps)

    if device == "cuda":
        torch.cuda.reset_peak_memory_stats()

    vecenv = make_vecenv(config, backend="subprocess")

    policy = build_policy_from_config(
        config, vecenv.single_observation_space, vecenv.single_action_space,
    )
    if device == "cuda":
        policy = policy.to("cuda")

    train_config = build_puffer_train_config(
        config, data_dir=Path(f"/tmp/fc_t42_m7_{device}_{run_index}"),
        total_timesteps=total_timesteps,
    )
    trainer = ConfigurablePuffeRL(
        train_config, vecenv, policy, _NullLogger(),
        dashboard_enabled=False, checkpointing_enabled=False,
        profiling_enabled=False, utilization_enabled=False,
        logging_enabled=False, instrumentation_enabled=False,
    )

    eval_s = train_s = 0.0
    stable = True
    started = perf_counter()
    try:
        while trainer.global_step < total_timesteps:
            t0 = perf_counter(); trainer.evaluate(); eval_s += perf_counter() - t0
            t0 = perf_counter(); trainer.train(); train_s += perf_counter() - t0
        gs = int(trainer.global_step)
    except Exception as exc:
        stable = False
        gs = int(trainer.global_step)
        print(f"  ERROR: {exc}")
    finally:
        trainer.close()

    elapsed = perf_counter() - started
    vram = torch.cuda.max_memory_allocated() / 1024**2 if device == "cuda" else 0.0

    losses = getattr(trainer, "losses", {})
    for k, v in losses.items():
        if isinstance(v, float) and (v != v or abs(v) == float("inf")):
            stable = False
            break

    return RunResult(
        mode="mode7", device=device, run_index=run_index,
        sps=gs / elapsed if elapsed > 0 else 0,
        evaluate_seconds=eval_s, train_seconds=train_s,
        elapsed_seconds=elapsed, global_step=gs,
        vram_peak_mb=vram, stable=stable, notes="",
    )


def _median(values: list[float]) -> float:
    return statistics.median(values) if values else 0.0


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser(description="T4.2 — GPU feasibility benchmark.")
    parser.add_argument("--num-runs", type=int, default=3)
    parser.add_argument("--env-count", type=int, default=64)
    parser.add_argument("--train-timesteps", type=int, default=16384)
    parser.add_argument("--config", type=Path, default=Path("configs/benchmark/fast_train_v2_mlp64.yaml"))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    n, ec, ts, cp = args.num_runs, args.env_count, args.train_timesteps, args.config

    # GPU info
    gpu_name = torch.cuda.get_device_properties(0).name if torch.cuda.is_available() else "N/A"
    gpu_vram_gb = torch.cuda.get_device_properties(0).total_memory / 1024**3 if torch.cuda.is_available() else 0
    print(f"T4.2 — GPU feasibility benchmark")
    print(f"  GPU: {gpu_name} ({gpu_vram_gb:.1f} GB)")
    print(f"  PyTorch: {torch.__version__}, CUDA: {torch.version.cuda}")
    print(f"  Runs: {n}, Envs: {ec}, Timesteps: {ts}\n")

    all_runs: dict[str, list[RunResult]] = {
        "mode6_cpu": [], "mode6_gpu": [], "mode7_cpu": [], "mode7_gpu": [],
    }

    # Mode 6 CPU
    print("=== Mode 6 CPU ===")
    for i in range(n):
        r = _run_mode6(cp, ec, ts, "cpu", i)
        all_runs["mode6_cpu"].append(r)
        print(f"  Run {i}: SPS={r.sps:.1f}, eval={r.evaluate_seconds:.2f}s, train={r.train_seconds:.2f}s")

    # Mode 6 GPU
    print("\n=== Mode 6 GPU ===")
    for i in range(n):
        r = _run_mode6(cp, ec, ts, "cuda", i)
        all_runs["mode6_gpu"].append(r)
        print(f"  Run {i}: SPS={r.sps:.1f}, eval={r.evaluate_seconds:.2f}s, train={r.train_seconds:.2f}s, VRAM={r.vram_peak_mb:.0f}MB, stable={r.stable}")

    # Mode 7 CPU
    print("\n=== Mode 7 CPU (subprocess) ===")
    for i in range(n):
        r = _run_mode7(cp, ec, ts, "cpu", i)
        all_runs["mode7_cpu"].append(r)
        print(f"  Run {i}: SPS={r.sps:.1f}, eval={r.evaluate_seconds:.2f}s, train={r.train_seconds:.2f}s")

    # Mode 7 GPU
    print("\n=== Mode 7 GPU (subprocess) ===")
    for i in range(n):
        r = _run_mode7(cp, ec, ts, "cuda", i)
        all_runs["mode7_gpu"].append(r)
        print(f"  Run {i}: SPS={r.sps:.1f}, eval={r.evaluate_seconds:.2f}s, train={r.train_seconds:.2f}s, VRAM={r.vram_peak_mb:.0f}MB, stable={r.stable}")

    # Analysis
    def stats(runs: list[RunResult]) -> dict[str, float]:
        return {
            "median_sps": _median([r.sps for r in runs]),
            "median_train_s": _median([r.train_seconds for r in runs]),
            "median_eval_s": _median([r.evaluate_seconds for r in runs]),
            "max_vram_mb": max((r.vram_peak_mb for r in runs), default=0),
            "all_stable": all(r.stable for r in runs),
        }

    s = {k: stats(v) for k, v in all_runs.items()}

    m6_cpu_train = s["mode6_cpu"]["median_train_s"]
    m6_gpu_train = s["mode6_gpu"]["median_train_s"]
    m7_cpu_train = s["mode7_cpu"]["median_train_s"]
    m7_gpu_train = s["mode7_gpu"]["median_train_s"]

    cpu_inflation = m7_cpu_train / m6_cpu_train if m6_cpu_train > 0 else 0
    gpu_inflation = m7_gpu_train / m6_gpu_train if m6_gpu_train > 0 else 0

    print("\n" + "=" * 80)
    print("T4.2 RESULTS")
    print("=" * 80)
    print(f"\nGPU: {gpu_name} ({gpu_vram_gb:.1f} GB), CUDA {torch.version.cuda}")
    print(f"\n{'Config':<25s}  {'Med SPS':>10s}  {'Med Train':>10s}  {'Med Eval':>10s}  {'VRAM':>8s}  {'Stable':>7s}")
    print("-" * 80)
    for label, key in [("Mode 6 CPU", "mode6_cpu"), ("Mode 6 GPU", "mode6_gpu"),
                        ("Mode 7 CPU", "mode7_cpu"), ("Mode 7 GPU", "mode7_gpu")]:
        st = s[key]
        vram_str = f"{st['max_vram_mb']:.0f}MB" if st['max_vram_mb'] > 0 else "N/A"
        print(f"{label:<25s}  {st['median_sps']:>10.1f}  {st['median_train_s']:>10.2f}s  {st['median_eval_s']:>10.2f}s  {vram_str:>8s}  {'yes' if st['all_stable'] else 'NO':>7s}")

    print(f"\nTrain-phase inflation:")
    print(f"  CPU: Mode 7 / Mode 6 = {m7_cpu_train:.2f} / {m6_cpu_train:.2f} = {cpu_inflation:.2f}x")
    print(f"  GPU: Mode 7 / Mode 6 = {m7_gpu_train:.2f} / {m6_gpu_train:.2f} = {gpu_inflation:.2f}x")
    print(f"\nMode 6 speedup (GPU vs CPU): {s['mode6_cpu']['median_sps'] / s['mode6_gpu']['median_sps']:.2f}x" if s["mode6_gpu"]["median_sps"] > 0 else "")
    print(f"Mode 7 speedup (GPU vs CPU): {s['mode7_gpu']['median_sps'] / s['mode7_cpu']['median_sps']:.2f}x" if s["mode7_cpu"]["median_sps"] > 0 else "")
    print("=" * 80)

    # Write results
    report = {
        "created_at": datetime.now(UTC).isoformat(),
        "gpu": {"name": gpu_name, "vram_gb": gpu_vram_gb, "cuda": torch.version.cuda, "torch": torch.__version__},
        "config": {"num_runs": n, "env_count": ec, "total_timesteps": ts},
        "runs": {k: [r.to_dict() for r in v] for k, v in all_runs.items()},
        "summary": s,
        "inflation": {"cpu": cpu_inflation, "gpu": gpu_inflation},
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    print(f"\nResults written to: {args.output}")


if __name__ == "__main__":
    main()
