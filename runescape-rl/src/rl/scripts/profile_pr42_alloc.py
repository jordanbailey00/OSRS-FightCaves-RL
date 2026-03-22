#!/usr/bin/env python3
"""Allocation and GC profiling for forensic investigation PR 4.2.

Runs three profiling passes:
  Pass 1: Env-only stepping with JFR allocation + GC events
  Pass 2: Trainer ceiling with JFR GC events (mode 6 equivalent)
  Pass 3: Full training with JFR GC events (mode 7 equivalent) for
          comparison against mode 6 to investigate train-phase inflation

Uses JFR ObjectAllocationSample, GarbageCollection, GCHeapSummary events.
"""
from __future__ import annotations

import json
import os
import subprocess
from collections import Counter
from datetime import UTC, datetime
from pathlib import Path
from time import perf_counter_ns

import numpy as np


def _jcmd() -> str:
    return str(Path(os.environ.get("JAVA_HOME", "")) / "bin" / "jcmd")


def _jfr_cmd() -> str:
    return str(Path(os.environ.get("JAVA_HOME", "")) / "bin" / "jfr")


def _start_jfr(name: str, jfr_path: Path, extra_settings: str = "") -> str:
    pid = os.getpid()
    cmd = [
        _jcmd(), str(pid), "JFR.start",
        f"filename={jfr_path.resolve()}",
        f"name={name}",
        "settings=profile",
        "jdk.ObjectAllocationSample#enabled=true",
        "jdk.ObjectAllocationSample#throttle=1000/s",
        "jdk.ObjectAllocationInNewTLAB#enabled=true",
        "jdk.ObjectAllocationOutsideTLAB#enabled=true",
        "jdk.GarbageCollection#enabled=true",
        "jdk.GCPhasePause#enabled=true",
        "jdk.GCHeapSummary#enabled=true",
        "jdk.YoungGenerationCollection#enabled=true",
        "jdk.OldGenerationCollection#enabled=true",
        "jdk.ExecutionSample#period=2ms",
        "maxsize=512m",
    ]
    if extra_settings:
        cmd.append(extra_settings)
    r = subprocess.run(cmd, capture_output=True, text=True)
    return r.stdout.strip()


def _stop_jfr(name: str) -> str:
    pid = os.getpid()
    r = subprocess.run(
        [_jcmd(), str(pid), "JFR.stop", f"name={name}"],
        capture_output=True, text=True,
    )
    return r.stdout.strip()


def profile_env_allocations(
    env_count: int = 64,
    rounds: int = 4096,
    warmup_rounds: int = 64,
    output_dir: Path = Path("results"),
) -> dict:
    """Pass 1: Profile JVM allocations during env-only stepping."""
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
            account_name_prefix="alloc_env",
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

    jfr_path = output_dir / "alloc_env_stepping.jfr"

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

        # Start JFR
        print(f"Starting JFR allocation profiling (env-only, {rounds} rounds)...")
        print(_start_jfr("alloc_env", jfr_path))

        wall_start = perf_counter_ns()
        for _ in range(rounds):
            vecenv.send(zero_action)
            vecenv.recv()
        wall_nanos = perf_counter_ns() - wall_start

        print(_stop_jfr("alloc_env"))

        total_steps = env_count * rounds
        sps = total_steps * 1e9 / max(1, wall_nanos)

        # Parse JFR
        alloc_analysis = _parse_jfr_allocations(jfr_path)
        gc_analysis = _parse_jfr_gc(jfr_path)

        result = {
            "profile": "env_allocations",
            "created_at": datetime.now(UTC).isoformat(),
            "env_count": env_count,
            "rounds": rounds,
            "total_steps": total_steps,
            "wall_nanos": wall_nanos,
            "sps": sps,
            "jfr_file": str(jfr_path),
            "allocations": alloc_analysis,
            "gc": gc_analysis,
        }

        print("=" * 90)
        print("ENV-ONLY ALLOCATION PROFILE")
        print("=" * 90)
        print(f"SPS: {sps:,.0f}  Wall: {wall_nanos / 1e6:.1f}ms  Steps: {total_steps}")
        print()
        _print_alloc_summary(alloc_analysis)
        print()
        _print_gc_summary(gc_analysis)

        return result
    finally:
        vecenv.close()


def profile_trainer_gc(
    env_count: int = 64,
    total_timesteps: int = 16384,
    config_path: str | Path = "configs/benchmark/fast_train_v2_mlp64.yaml",
    output_dir: Path = Path("results"),
) -> dict:
    """Pass 2: Profile GC during trainer ceiling (mode 6, fake env)."""
    from fight_caves_rl.benchmarks.train_ceiling_bench import run_train_ceiling_benchmark

    jfr_path = output_dir / "gc_trainer_ceiling.jfr"

    print(f"Starting JFR GC profiling (trainer ceiling, {total_timesteps} timesteps)...")
    print(_start_jfr("gc_trainer", jfr_path))

    wall_start = perf_counter_ns()
    report = run_train_ceiling_benchmark(
        config_path,
        env_counts_override=(env_count,),
        total_timesteps_override=total_timesteps,
    )
    wall_nanos = perf_counter_ns() - wall_start

    print(_stop_jfr("gc_trainer"))

    m = report.measurements[0]
    gc_analysis = _parse_jfr_gc(jfr_path)

    result = {
        "profile": "trainer_ceiling_gc",
        "created_at": datetime.now(UTC).isoformat(),
        "env_count": env_count,
        "total_timesteps": total_timesteps,
        "sps": m.env_steps_per_second,
        "elapsed_seconds": m.elapsed_seconds,
        "evaluate_seconds": m.evaluate_seconds,
        "train_seconds": m.train_seconds,
        "wall_nanos": wall_nanos,
        "jfr_file": str(jfr_path),
        "gc": gc_analysis,
    }

    print("=" * 90)
    print("TRAINER CEILING GC PROFILE (mode 6)")
    print("=" * 90)
    print(f"SPS: {m.env_steps_per_second:.0f}  Evaluate: {m.evaluate_seconds:.1f}s  Train: {m.train_seconds:.1f}s")
    print()
    _print_gc_summary(gc_analysis)

    return result


def profile_training_gc(
    env_count: int = 64,
    total_timesteps: int = 16384,
    config_path: str | Path = "configs/benchmark/fast_train_v2_mlp64.yaml",
    output_dir: Path = Path("results"),
) -> dict:
    """Pass 3: Profile GC during full training (mode 7, real env).

    Inlines the mode 7 training logic to wrap with JFR recording.
    """
    from pathlib import Path as _Path
    from time import perf_counter
    from typing import Any

    import yaml

    from fight_caves_rl.bridge.contracts import HeadlessBootstrapConfig
    from fight_caves_rl.envs_fast.fast_reward_adapter import FastRewardAdapter
    from fight_caves_rl.envs_fast.fast_vector_env import (
        FastKernelVecEnv,
        FastKernelVecEnvConfig,
    )
    from fight_caves_rl.policies.mlp import MultiDiscreteMLPPolicy
    from fight_caves_rl.puffer.factory import build_puffer_train_config
    from fight_caves_rl.puffer.trainer import ConfigurablePuffeRL

    config = yaml.safe_load(_Path(config_path).read_text(encoding="utf-8"))
    env_config = config.get("env", {})
    reward_config_id = str(config["reward_config"])
    reward_adapter = FastRewardAdapter.from_config_id(reward_config_id)

    vecenv = FastKernelVecEnv(
        FastKernelVecEnvConfig(
            env_count=env_count,
            account_name_prefix="alloc_train",
            start_wave=int(env_config.get("start_wave", 1)),
            ammo=int(env_config.get("ammo", 1000)),
            prayer_potions=int(env_config.get("prayer_potions", 8)),
            sharks=int(env_config.get("sharks", 20)),
            tick_cap=int(env_config.get("tick_cap", 20_000)),
            info_payload_mode="minimal",
            instrumentation_enabled=False,
            bootstrap=HeadlessBootstrapConfig(start_world=False),
        ),
        reward_adapter=reward_adapter,
    )

    # Configure training
    config["num_envs"] = env_count
    config["train"]["total_timesteps"] = total_timesteps
    config.setdefault("logging", {})["dashboard"] = False
    effective_batch = max(1, min(total_timesteps, int(config["train"]["batch_size"])))
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
            raise RuntimeError("Not supported")
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
        data_dir=_Path("/tmp/fc_pr42_bench"),
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

    jfr_path = output_dir / "gc_full_training.jfr"

    print(f"Starting JFR GC profiling (full training mode 7, {total_timesteps} timesteps)...")
    print(_start_jfr("gc_training", jfr_path))

    evaluate_seconds = 0.0
    train_seconds = 0.0
    wall_start = perf_counter_ns()
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

    wall_nanos = perf_counter_ns() - wall_start
    print(_stop_jfr("gc_training"))

    elapsed_s = wall_nanos / 1e9
    sps = global_step / max(0.001, elapsed_s)
    gc_analysis = _parse_jfr_gc(jfr_path)
    alloc_analysis = _parse_jfr_allocations(jfr_path)

    result = {
        "profile": "full_training_gc",
        "created_at": datetime.now(UTC).isoformat(),
        "env_count": env_count,
        "total_timesteps": total_timesteps,
        "global_step": global_step,
        "sps": sps,
        "elapsed_seconds": elapsed_s,
        "evaluate_seconds": evaluate_seconds,
        "train_seconds": train_seconds,
        "wall_nanos": wall_nanos,
        "jfr_file": str(jfr_path),
        "gc": gc_analysis,
        "allocations": alloc_analysis,
    }

    print("=" * 90)
    print("FULL TRAINING GC PROFILE (mode 7)")
    print("=" * 90)
    print(f"SPS: {sps:.0f}  Elapsed: {elapsed_s:.1f}s  "
          f"Evaluate: {evaluate_seconds:.1f}s  Train: {train_seconds:.1f}s")
    print()
    _print_gc_summary(gc_analysis)
    print()
    _print_alloc_summary(alloc_analysis)

    return result


def _parse_jfr_allocations(jfr_path: Path) -> dict:
    """Parse JFR allocation events."""
    # Parse ObjectAllocationSample events
    result = subprocess.run(
        [_jfr_cmd(), "print", "--events",
         "jdk.ObjectAllocationSample,jdk.ObjectAllocationInNewTLAB,jdk.ObjectAllocationOutsideTLAB",
         str(jfr_path)],
        capture_output=True, text=True,
    )

    if result.returncode != 0:
        return {"error": result.stderr, "raw_length": 0}

    text = result.stdout
    events = text.split("jdk.ObjectAllocation")

    type_counts: Counter[str] = Counter()
    type_bytes: Counter[str] = Counter()
    stack_alloc: Counter[str] = Counter()
    phase_alloc: Counter[str] = Counter()
    total_events = 0
    total_bytes = 0

    phase_markers = {
        "observeFlatBatch": "observe_flat",
        "observeFightCaveFlat": "observe_flat",
        "HeadlessFlatObservationBuilder": "observe_flat",
        "packFlatObservationBatch": "observe_flat",
        "captureRewardSnapshot": "projection",
        "FastTerminalStateEvaluator": "projection",
        "FastRewardFeatureWriter": "projection",
        "applyActionsBatch": "apply_actions",
        "applyFightCaveAction": "apply_actions",
        "HeadlessActionAdapter": "apply_actions",
        "GameLoop.tick": "tick",
        "NPCTask": "tick",
        "PlayerTask": "tick",
        "PlayerResetTask": "tick",
        "NPCResetTask": "tick",
        "fightCavePrayerPotionDoseCount": "prayer_potion_count",
    }

    import re
    for event in events[1:]:
        total_events += 1

        # Extract object class
        class_match = re.search(r'objectClass\s*=\s*(\S+)', event)
        if class_match:
            cls = class_match.group(1)
            type_counts[cls] += 1

        # Extract weight/size
        weight_match = re.search(r'weight\s*=\s*(\d+)', event)
        if weight_match:
            w = int(weight_match.group(1))
            total_bytes += w
            if class_match:
                type_bytes[cls] += w

        # Extract top-of-stack for allocation site
        stack_lines = []
        for line in event.split("\n"):
            stripped = line.strip()
            if "line:" in stripped and ("at " in stripped or "." in stripped):
                clean = stripped.lstrip("at ").strip()
                if clean and not clean.startswith("startTime"):
                    stack_lines.append(clean)

        if stack_lines:
            # First non-JDK frame
            alloc_site = stack_lines[0]
            for frame in stack_lines:
                if not any(x in frame for x in ["java.", "kotlin.", "it.unimi.", "jdk.", "sun."]):
                    alloc_site = frame
                    break
            m = re.match(r'([A-Za-z0-9_$.]+\.[A-Za-z0-9_$<>]+)\(', alloc_site)
            if m:
                stack_alloc[m.group(1)] += 1
            else:
                stack_alloc[alloc_site[:80]] += 1

        # Phase attribution
        full_stack = " ".join(stack_lines)
        attributed = False
        for marker, phase in phase_markers.items():
            if marker in full_stack:
                phase_alloc[phase] += 1
                attributed = True
                break
        if not attributed:
            phase_alloc["other"] += 1

    return {
        "total_events": total_events,
        "total_bytes": total_bytes,
        "top_types_by_count": [
            {"type": t, "count": c, "pct": round(100.0 * c / max(1, total_events), 1)}
            for t, c in type_counts.most_common(20)
        ],
        "top_types_by_bytes": [
            {"type": t, "bytes": b, "pct": round(100.0 * b / max(1, total_bytes), 1)}
            for t, b in type_bytes.most_common(20)
        ],
        "top_alloc_sites": [
            {"site": s, "count": c, "pct": round(100.0 * c / max(1, total_events), 1)}
            for s, c in stack_alloc.most_common(30)
        ],
        "phase_attribution": [
            {"phase": p, "count": c, "pct": round(100.0 * c / max(1, total_events), 1)}
            for p, c in phase_alloc.most_common()
        ],
    }


def _parse_jfr_gc(jfr_path: Path) -> dict:
    """Parse JFR GC events."""
    # GarbageCollection events
    gc_result = subprocess.run(
        [_jfr_cmd(), "print", "--events", "jdk.GarbageCollection", str(jfr_path)],
        capture_output=True, text=True,
    )

    # GCPhasePause events
    pause_result = subprocess.run(
        [_jfr_cmd(), "print", "--events", "jdk.GCPhasePause", str(jfr_path)],
        capture_output=True, text=True,
    )

    # GCHeapSummary events
    heap_result = subprocess.run(
        [_jfr_cmd(), "print", "--events", "jdk.GCHeapSummary", str(jfr_path)],
        capture_output=True, text=True,
    )

    import re

    # Parse GC events
    gc_events = gc_result.stdout.split("jdk.GarbageCollection {")
    gc_count = len(gc_events) - 1
    gc_causes: Counter[str] = Counter()
    gc_durations_ms: list[float] = []

    for event in gc_events[1:]:
        cause_match = re.search(r'cause\s*=\s*"([^"]*)"', event)
        if cause_match:
            gc_causes[cause_match.group(1)] += 1
        dur_match = re.search(r'duration\s*=\s*(\S+)', event)
        if dur_match:
            dur_str = dur_match.group(1)
            # Parse duration (e.g., "12.3 ms", "PT0.012S")
            ms_match = re.search(r'([\d.]+)\s*ms', dur_str)
            if ms_match:
                gc_durations_ms.append(float(ms_match.group(1)))
            else:
                s_match = re.search(r'([\d.]+)\s*s', dur_str)
                if s_match:
                    gc_durations_ms.append(float(s_match.group(1)) * 1000)

    # Parse pause events
    pause_events = pause_result.stdout.split("jdk.GCPhasePause {")
    pause_count = len(pause_events) - 1
    pause_durations_ms: list[float] = []

    for event in pause_events[1:]:
        dur_match = re.search(r'duration\s*=\s*(\S+)', event)
        if dur_match:
            dur_str = dur_match.group(1)
            ms_match = re.search(r'([\d.]+)\s*ms', dur_str)
            if ms_match:
                pause_durations_ms.append(float(ms_match.group(1)))
            else:
                s_match = re.search(r'([\d.]+)\s*s', dur_str)
                if s_match:
                    pause_durations_ms.append(float(s_match.group(1)) * 1000)
                else:
                    us_match = re.search(r'([\d.]+)\s*us', dur_str)
                    if us_match:
                        pause_durations_ms.append(float(us_match.group(1)) / 1000)

    # Parse heap summaries
    heap_events = heap_result.stdout.split("jdk.GCHeapSummary {")
    heap_used_bytes: list[int] = []

    for event in heap_events[1:]:
        used_match = re.search(r'heapUsed\s*=\s*(\d+)', event)
        if used_match:
            heap_used_bytes.append(int(used_match.group(1)))

    return {
        "gc_count": gc_count,
        "gc_causes": dict(gc_causes.most_common()),
        "gc_total_pause_ms": round(sum(gc_durations_ms), 2) if gc_durations_ms else 0,
        "gc_avg_pause_ms": round(sum(gc_durations_ms) / max(1, len(gc_durations_ms)), 3) if gc_durations_ms else 0,
        "gc_max_pause_ms": round(max(gc_durations_ms), 3) if gc_durations_ms else 0,
        "gc_durations_ms": sorted(gc_durations_ms, reverse=True)[:20],
        "pause_event_count": pause_count,
        "pause_total_ms": round(sum(pause_durations_ms), 2) if pause_durations_ms else 0,
        "pause_avg_ms": round(sum(pause_durations_ms) / max(1, len(pause_durations_ms)), 3) if pause_durations_ms else 0,
        "pause_max_ms": round(max(pause_durations_ms), 3) if pause_durations_ms else 0,
        "heap_used_min_mb": round(min(heap_used_bytes) / 1e6, 1) if heap_used_bytes else 0,
        "heap_used_max_mb": round(max(heap_used_bytes) / 1e6, 1) if heap_used_bytes else 0,
        "heap_samples": len(heap_used_bytes),
    }


def _print_alloc_summary(alloc: dict) -> None:
    print(f"Total allocation events: {alloc['total_events']}")
    print(f"Total bytes sampled: {alloc['total_bytes']:,}")
    print()

    print("--- Phase attribution ---")
    for item in alloc["phase_attribution"]:
        print(f"  {item['count']:>6} ({item['pct']:>5.1f}%)  {item['phase']}")

    print()
    print("--- Top allocated types by count ---")
    for item in alloc["top_types_by_count"][:15]:
        print(f"  {item['count']:>6} ({item['pct']:>5.1f}%)  {item['type']}")

    print()
    print("--- Top allocated types by bytes ---")
    for item in alloc["top_types_by_bytes"][:15]:
        mb = item["bytes"] / 1e6
        print(f"  {mb:>8.1f} MB ({item['pct']:>5.1f}%)  {item['type']}")

    print()
    print("--- Top allocation sites (first app frame) ---")
    for item in alloc["top_alloc_sites"][:20]:
        print(f"  {item['count']:>6} ({item['pct']:>5.1f}%)  {item['site']}")


def _print_gc_summary(gc: dict) -> None:
    print(f"GC events: {gc['gc_count']}")
    print(f"GC causes: {gc['gc_causes']}")
    print(f"GC total pause: {gc['gc_total_pause_ms']:.1f}ms  "
          f"avg: {gc['gc_avg_pause_ms']:.3f}ms  max: {gc['gc_max_pause_ms']:.3f}ms")
    print(f"Pause events: {gc['pause_event_count']}  "
          f"total: {gc['pause_total_ms']:.1f}ms  max: {gc['pause_max_ms']:.3f}ms")
    print(f"Heap used: {gc['heap_used_min_mb']:.0f}–{gc['heap_used_max_mb']:.0f} MB "
          f"({gc['heap_samples']} samples)")
    if gc["gc_durations_ms"]:
        print(f"Top GC pauses (ms): {gc['gc_durations_ms'][:10]}")


def main():
    import argparse

    parser = argparse.ArgumentParser(description="Allocation/GC profiling for PR 4.2")
    parser.add_argument("--env-count", type=int, default=64)
    parser.add_argument("--env-rounds", type=int, default=4096)
    parser.add_argument("--train-timesteps", type=int, default=16384)
    parser.add_argument("--output-dir", type=Path, default=Path("results"))
    parser.add_argument(
        "--config",
        type=Path,
        default=Path("configs/benchmark/fast_train_v2_mlp64.yaml"),
    )
    parser.add_argument("--skip-env", action="store_true")
    parser.add_argument("--skip-trainer", action="store_true")
    parser.add_argument("--skip-training", action="store_true")
    args = parser.parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)
    all_results = {}

    if not args.skip_env:
        print("\n>>> PASS 1: Env-only allocation profiling <<<\n")
        all_results["env_allocations"] = profile_env_allocations(
            env_count=args.env_count,
            rounds=args.env_rounds,
            output_dir=args.output_dir,
        )

    if not args.skip_trainer:
        print("\n>>> PASS 2: Trainer ceiling GC profiling <<<\n")
        all_results["trainer_gc"] = profile_trainer_gc(
            env_count=args.env_count,
            total_timesteps=args.train_timesteps,
            config_path=args.config,
            output_dir=args.output_dir,
        )

    if not args.skip_training:
        print("\n>>> PASS 3: Full training GC profiling <<<\n")
        all_results["training_gc"] = profile_training_gc(
            env_count=args.env_count,
            total_timesteps=args.train_timesteps,
            config_path=args.config,
            output_dir=args.output_dir,
        )

    output_path = args.output_dir / "alloc_profile_pr42.json"
    output_path.write_text(
        json.dumps(all_results, indent=2, sort_keys=False, default=str) + "\n",
        encoding="utf-8",
    )
    print(f"\nCombined results written to: {output_path}")


if __name__ == "__main__":
    main()
