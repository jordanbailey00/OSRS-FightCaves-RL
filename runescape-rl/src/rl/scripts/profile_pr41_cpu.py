#!/usr/bin/env python3
"""CPU profiling for forensic investigation PR 4.1.

Runs two profiling passes:
  Pass 1: Trainer ceiling (mode 6) — PufferLib + MLP64 with fake env
  Pass 2: Env-only stepping — FastKernelVecEnv send/recv loop

Uses cProfile for method-level attribution with cumulative times and call counts.
"""
from __future__ import annotations

import cProfile
import json
import pstats
import io
from datetime import UTC, datetime
from pathlib import Path
from time import perf_counter_ns

import numpy as np


def profile_trainer_ceiling(
    env_count: int = 64,
    total_timesteps: int = 16384,
    config_path: str | Path = "configs/benchmark/fast_train_v2_mlp64.yaml",
    output_dir: Path = Path("results"),
) -> dict:
    """Profile the PufferLib trainer loop with a fake env (mode 6 equivalent)."""
    from fight_caves_rl.benchmarks.train_ceiling_bench import run_train_ceiling_benchmark

    profiler = cProfile.Profile()
    profiler.enable()
    report = run_train_ceiling_benchmark(
        config_path,
        env_counts_override=(env_count,),
        total_timesteps_override=total_timesteps,
    )
    profiler.disable()

    m = report.measurements[0]

    # Dump raw stats
    stats_path = output_dir / "profile_trainer_ceiling.prof"
    profiler.dump_stats(str(stats_path))

    # Generate text summary
    stream = io.StringIO()
    ps = pstats.Stats(profiler, stream=stream)
    ps.sort_stats("cumulative")
    ps.print_stats(80)
    cumulative_text = stream.getvalue()

    stream2 = io.StringIO()
    ps2 = pstats.Stats(profiler, stream=stream2)
    ps2.sort_stats("tottime")
    ps2.print_stats(80)
    tottime_text = stream2.getvalue()

    # Extract top methods
    top_by_cumulative = _extract_top_methods(profiler, sort_key="cumulative", limit=40)
    top_by_tottime = _extract_top_methods(profiler, sort_key="tottime", limit=40)

    result = {
        "profile": "trainer_ceiling",
        "created_at": datetime.now(UTC).isoformat(),
        "env_count": env_count,
        "total_timesteps": total_timesteps,
        "sps": m.env_steps_per_second,
        "elapsed_seconds": m.elapsed_seconds,
        "evaluate_seconds": m.evaluate_seconds,
        "train_seconds": m.train_seconds,
        "top_by_cumulative": top_by_cumulative,
        "top_by_tottime": top_by_tottime,
        "stats_file": str(stats_path),
    }

    print("=" * 90)
    print("TRAINER CEILING CPU PROFILE (mode 6 equivalent)")
    print("=" * 90)
    print(f"SPS: {m.env_steps_per_second:.0f}  Elapsed: {m.elapsed_seconds:.1f}s")
    print(f"Evaluate: {m.evaluate_seconds:.1f}s  Train: {m.train_seconds:.1f}s")
    print()
    print("--- Top 30 by cumulative time ---")
    print(cumulative_text[:4000])
    print()
    print("--- Top 30 by self time ---")
    print(tottime_text[:4000])

    return result


def profile_env_stepping(
    env_count: int = 64,
    rounds: int = 512,
    warmup_rounds: int = 16,
    output_dir: Path = Path("results"),
) -> dict:
    """Profile the env-only step loop (mode 4 equivalent)."""
    from fight_caves_rl.bridge.contracts import HeadlessBootstrapConfig
    from fight_caves_rl.envs_fast.fast_reward_adapter import FastRewardAdapter
    from fight_caves_rl.envs_fast.fast_vector_env import (
        FastKernelVecEnv,
        FastKernelVecEnvConfig,
    )

    reward_adapter = FastRewardAdapter.from_config_id("reward_sparse_v2")
    vecenv = FastKernelVecEnv(
        FastKernelVecEnvConfig(
            env_count=env_count,
            account_name_prefix="profile_env",
            start_wave=1,
            ammo=1000,
            prayer_potions=8,
            sharks=20,
            tick_cap=20_000,
            info_payload_mode="minimal",
            instrumentation_enabled=True,
            bootstrap=HeadlessBootstrapConfig(start_world=False),
        ),
        reward_adapter=reward_adapter,
    )

    try:
        zero_action = np.zeros(
            (env_count, len(vecenv.single_action_space.nvec)),
            dtype=np.int32,
        )
        vecenv.async_reset(70_000)
        vecenv.recv()

        # Warmup (not profiled)
        for _ in range(warmup_rounds):
            vecenv.send(zero_action)
            vecenv.recv()

        # Profile steady-state stepping
        profiler = cProfile.Profile()
        wall_start = perf_counter_ns()
        profiler.enable()
        for _ in range(rounds):
            vecenv.send(zero_action)
            vecenv.recv()
        profiler.disable()
        wall_nanos = perf_counter_ns() - wall_start

        total_steps = env_count * rounds
        sps = total_steps * 1e9 / max(1, wall_nanos)

        # Dump raw stats
        stats_path = output_dir / "profile_env_stepping.prof"
        profiler.dump_stats(str(stats_path))

        # Generate text summary
        stream = io.StringIO()
        ps = pstats.Stats(profiler, stream=stream)
        ps.sort_stats("cumulative")
        ps.print_stats(80)
        cumulative_text = stream.getvalue()

        stream2 = io.StringIO()
        ps2 = pstats.Stats(profiler, stream=stream2)
        ps2.sort_stats("tottime")
        ps2.print_stats(80)
        tottime_text = stream2.getvalue()

        top_by_cumulative = _extract_top_methods(profiler, sort_key="cumulative", limit=40)
        top_by_tottime = _extract_top_methods(profiler, sort_key="tottime", limit=40)

        # Get instrumentation
        buckets = vecenv.instrumentation_snapshot()

        result = {
            "profile": "env_stepping",
            "created_at": datetime.now(UTC).isoformat(),
            "env_count": env_count,
            "rounds": rounds,
            "total_steps": total_steps,
            "wall_nanos": wall_nanos,
            "sps": sps,
            "top_by_cumulative": top_by_cumulative,
            "top_by_tottime": top_by_tottime,
            "stats_file": str(stats_path),
        }

        print("=" * 90)
        print("ENV-ONLY STEPPING CPU PROFILE (mode 4 equivalent)")
        print("=" * 90)
        print(f"SPS: {sps:,.0f}  Wall: {wall_nanos/1e6:.1f}ms  Steps: {total_steps}")
        print()
        print("--- Top 30 by cumulative time ---")
        print(cumulative_text[:4000])
        print()
        print("--- Top 30 by self time ---")
        print(tottime_text[:4000])

        return result
    finally:
        vecenv.close()


def profile_jvm_jfr(
    env_count: int = 64,
    rounds: int = 1024,
    warmup_rounds: int = 32,
    output_dir: Path = Path("results"),
) -> dict:
    """Profile JVM-side hotspots using Java Flight Recorder.

    Attaches JFR to the in-process JVM (started by JPype) during steady-state
    env stepping to capture method-level CPU samples within observe_flat,
    projection, tick, and other JVM phases.
    """
    import os
    import subprocess

    from fight_caves_rl.bridge.contracts import HeadlessBootstrapConfig
    from fight_caves_rl.envs_fast.fast_reward_adapter import FastRewardAdapter
    from fight_caves_rl.envs_fast.fast_vector_env import (
        FastKernelVecEnv,
        FastKernelVecEnvConfig,
    )

    reward_adapter = FastRewardAdapter.from_config_id("reward_sparse_v2")
    vecenv = FastKernelVecEnv(
        FastKernelVecEnvConfig(
            env_count=env_count,
            account_name_prefix="profile_jfr",
            start_wave=1,
            ammo=1000,
            prayer_potions=8,
            sharks=20,
            tick_cap=20_000,
            info_payload_mode="minimal",
            instrumentation_enabled=False,
            bootstrap=HeadlessBootstrapConfig(start_world=False),
        ),
        reward_adapter=reward_adapter,
    )

    jfr_path = output_dir / "jvm_profile.jfr"
    pid = os.getpid()
    jcmd = Path(os.environ.get("JAVA_HOME", "")) / "bin" / "jcmd"
    if not jcmd.exists():
        jcmd = Path("jcmd")  # fallback to PATH

    try:
        zero_action = np.zeros(
            (env_count, len(vecenv.single_action_space.nvec)),
            dtype=np.int32,
        )
        vecenv.async_reset(70_000)
        vecenv.recv()

        # Warmup
        for _ in range(warmup_rounds):
            vecenv.send(zero_action)
            vecenv.recv()

        # Start JFR recording
        print(f"Starting JFR recording on PID {pid}...")
        start_cmd = [
            str(jcmd), str(pid),
            "JFR.start",
            f"filename={jfr_path.resolve()}",
            "name=pr41",
            "settings=profile",
            "maxsize=256m",
        ]
        start_result = subprocess.run(start_cmd, capture_output=True, text=True)
        if start_result.returncode != 0:
            print(f"JFR start failed: {start_result.stderr}")
            return {"error": start_result.stderr}
        print(f"JFR started: {start_result.stdout.strip()}")

        # Run stepping loop (longer than cProfile pass for better sampling)
        wall_start = perf_counter_ns()
        for _ in range(rounds):
            vecenv.send(zero_action)
            vecenv.recv()
        wall_nanos = perf_counter_ns() - wall_start

        # Stop JFR recording
        print("Stopping JFR recording...")
        stop_cmd = [
            str(jcmd), str(pid),
            "JFR.stop",
            "name=pr41",
        ]
        stop_result = subprocess.run(stop_cmd, capture_output=True, text=True)
        print(f"JFR stopped: {stop_result.stdout.strip()}")

        total_steps = env_count * rounds
        sps = total_steps * 1e9 / max(1, wall_nanos)

        # Parse JFR execution samples
        jfr_text = _parse_jfr_hotspots(jfr_path)

        result = {
            "profile": "jvm_jfr",
            "created_at": datetime.now(UTC).isoformat(),
            "env_count": env_count,
            "rounds": rounds,
            "total_steps": total_steps,
            "wall_nanos": wall_nanos,
            "sps": sps,
            "jfr_file": str(jfr_path),
            "jfr_hotspots": jfr_text,
        }

        print("=" * 90)
        print("JVM FLIGHT RECORDER PROFILE (env-only stepping)")
        print("=" * 90)
        print(f"SPS: {sps:,.0f}  Wall: {wall_nanos/1e6:.1f}ms  Steps: {total_steps}")
        print()
        print(jfr_text[:6000])

        return result
    finally:
        vecenv.close()


def _parse_jfr_hotspots(jfr_path: Path) -> str:
    """Parse JFR file and extract CPU hotspot methods."""
    import os
    import subprocess

    jfr_cmd = Path(os.environ.get("JAVA_HOME", "")) / "bin" / "jfr"
    if not jfr_cmd.exists():
        jfr_cmd = Path("jfr")

    # Get execution sample events (CPU profiling)
    result = subprocess.run(
        [str(jfr_cmd), "print", "--events", "jdk.ExecutionSample", str(jfr_path)],
        capture_output=True,
        text=True,
    )

    if result.returncode != 0:
        # Try summary instead
        summary = subprocess.run(
            [str(jfr_cmd), "summary", str(jfr_path)],
            capture_output=True,
            text=True,
        )
        return f"JFR print failed: {result.stderr}\n\nSummary:\n{summary.stdout}"

    # Count method occurrences from stack traces
    from collections import Counter
    method_counts: Counter[str] = Counter()
    lines = result.stdout.split("\n")
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("at ") or (stripped and "." in stripped and "(" in stripped):
            # Stack frame
            method = stripped.lstrip("at ").strip()
            method_counts[method] += 1

    if not method_counts:
        return f"No execution samples found. Raw output ({len(result.stdout)} chars):\n{result.stdout[:2000]}"

    # Format top methods
    total_samples = sum(method_counts.values())
    output_lines = [
        f"JFR Execution Samples: {total_samples} total stack frames",
        f"Unique methods: {len(method_counts)}",
        "",
        "--- Top 50 JVM methods by sample count ---",
        f"{'Samples':>8}  {'%':>6}  Method",
        "-" * 80,
    ]
    for method, count in method_counts.most_common(50):
        pct = 100.0 * count / total_samples
        output_lines.append(f"{count:>8}  {pct:>5.1f}%  {method}")

    return "\n".join(output_lines)


def _extract_top_methods(profiler: cProfile.Profile, sort_key: str, limit: int) -> list[dict]:
    """Extract top methods from cProfile stats as structured data."""
    stats = pstats.Stats(profiler)
    stats.sort_stats(sort_key)

    results = []
    for (filename, lineno, funcname), (cc, nc, tt, ct, callers) in sorted(
        stats.stats.items(),
        key=lambda x: x[1][3] if sort_key == "cumulative" else x[1][2],
        reverse=True,
    )[:limit]:
        # Simplify filename
        short_file = filename
        for prefix in ("fight_caves_rl/", "pufferlib/", ".venv/lib/python3.12/site-packages/"):
            idx = short_file.find(prefix)
            if idx >= 0:
                short_file = short_file[idx:]
                break
        if len(short_file) > 80:
            short_file = "..." + short_file[-77:]

        results.append({
            "function": funcname,
            "file": short_file,
            "line": lineno,
            "calls": nc,
            "tottime": round(tt, 6),
            "cumtime": round(ct, 6),
            "percall_tot": round(tt / max(1, nc), 9),
            "percall_cum": round(ct / max(1, nc), 9),
        })
    return results


def main():
    import argparse

    parser = argparse.ArgumentParser(description="CPU profiling for PR 4.1")
    parser.add_argument("--env-count", type=int, default=64)
    parser.add_argument("--train-timesteps", type=int, default=16384)
    parser.add_argument("--env-rounds", type=int, default=512)
    parser.add_argument("--output-dir", type=Path, default=Path("results"))
    parser.add_argument(
        "--config",
        type=Path,
        default=Path("configs/benchmark/fast_train_v2_mlp64.yaml"),
    )
    parser.add_argument("--skip-trainer", action="store_true", help="Skip trainer profiling")
    parser.add_argument("--skip-env", action="store_true", help="Skip env profiling")
    parser.add_argument("--skip-jfr", action="store_true", help="Skip JFR profiling")
    args = parser.parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)
    all_results = {}

    if not args.skip_trainer:
        print("\n>>> PASS 1: Trainer ceiling profiling <<<\n")
        all_results["trainer_ceiling"] = profile_trainer_ceiling(
            env_count=args.env_count,
            total_timesteps=args.train_timesteps,
            config_path=args.config,
            output_dir=args.output_dir,
        )

    if not args.skip_env:
        print("\n>>> PASS 2: Env-only stepping profiling <<<\n")
        all_results["env_stepping"] = profile_env_stepping(
            env_count=args.env_count,
            rounds=args.env_rounds,
            output_dir=args.output_dir,
        )

    if not args.skip_jfr:
        print("\n>>> PASS 3: JVM Flight Recorder profiling <<<\n")
        all_results["jvm_jfr"] = profile_jvm_jfr(
            env_count=args.env_count,
            rounds=args.env_rounds * 2,  # longer run for better sampling
            output_dir=args.output_dir,
        )

    # Save combined results
    output_path = args.output_dir / "cpu_profile_pr41.json"
    output_path.write_text(
        json.dumps(all_results, indent=2, sort_keys=False, default=str) + "\n",
        encoding="utf-8",
    )
    print(f"\nCombined results written to: {output_path}")


if __name__ == "__main__":
    main()
