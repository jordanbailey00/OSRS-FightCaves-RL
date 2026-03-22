"""Per-tick-stage timing benchmark for the forensic performance investigation (PR 3.1).

Runs the v2 fast kernel environment and extracts per-stage timing data
from the GameLoop instrumentation added in PR 3.1.

The 12 headless tick stages are:
  1. PlayerResetTask    2. NPCResetTask     3. NPCs
  4. InstructionTask    5. World            6. NPCTask
  7. PlayerTask         8. FloorItemTracking 9. GameObjects.timers
  10. DynamicZones      11. ZoneBatchUpdates 12. CharacterUpdateTask

This benchmark reports:
  - Per-stage total nanos, mean nanos/tick, % of tick time
  - Comparison of tick-stage sum vs. total tickNanos
  - Ranking by runtime share
"""
from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path
from time import perf_counter_ns
from typing import Any

import numpy as np

from fight_caves_rl.envs_fast.fast_vector_env import HEADLESS_TICK_STAGE_NAMES


@dataclass(frozen=True)
class StageTimingResult:
    index: int
    name: str
    total_nanos: int
    mean_nanos_per_tick: float
    pct_of_tick: float
    pct_of_kernel: float
    calls: int

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class TickStageReport:
    created_at: str
    env_count: int
    rounds: int
    warmup_rounds: int
    total_ticks: int
    tick_total_nanos: int
    kernel_total_nanos: int
    observe_flat_nanos: int
    projection_nanos: int
    apply_actions_nanos: int
    wall_total_nanos: int
    stages: list[StageTimingResult]
    stages_sum_nanos: int
    tick_vs_stages_delta_pct: float

    def to_dict(self) -> dict[str, Any]:
        d = asdict(self)
        d["stages"] = [s.to_dict() for s in self.stages]
        return d


def run_tick_stage_benchmark(
    *,
    env_count: int = 64,
    rounds: int = 512,
    warmup_rounds: int = 16,
    config_path: str | Path = "configs/benchmark/sps_decomposition_v0.yaml",
) -> TickStageReport:
    import yaml

    from fight_caves_rl.bridge.contracts import HeadlessBootstrapConfig
    from fight_caves_rl.envs_fast.fast_reward_adapter import FastRewardAdapter
    from fight_caves_rl.envs_fast.fast_vector_env import (
        FastKernelVecEnv,
        FastKernelVecEnvConfig,
    )

    config = yaml.safe_load(Path(config_path).read_text(encoding="utf-8")) or {}

    reward_adapter = FastRewardAdapter.from_config_id("reward_sparse_v2")
    vecenv = FastKernelVecEnv(
        FastKernelVecEnvConfig(
            env_count=env_count,
            account_name_prefix="tick_stage_bench",
            start_wave=int(config.get("start_wave", 1)),
            ammo=int(config.get("ammo", 1000)),
            prayer_potions=int(config.get("prayer_potions", 8)),
            sharks=int(config.get("sharks", 20)),
            tick_cap=int(config.get("tick_cap", 20_000)),
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

        # Reset
        vecenv.async_reset(70_000)
        vecenv.recv()

        # Warmup
        for _ in range(warmup_rounds):
            vecenv.send(zero_action)
            vecenv.recv()
        vecenv.reset_instrumentation()

        # Measurement
        wall_start = perf_counter_ns()
        for _ in range(rounds):
            vecenv.send(zero_action)
            vecenv.recv()
        wall_total = perf_counter_ns() - wall_start

        # Extract instrumentation
        buckets = vecenv.instrumentation_snapshot()

        def _bucket_nanos(name: str) -> int:
            entry = buckets.get(name, {})
            return int(float(entry.get("seconds", 0.0)) * 1_000_000_000)

        def _bucket_calls(name: str) -> int:
            entry = buckets.get(name, {})
            return int(entry.get("calls", 0))

        tick_total = _bucket_nanos("fast_kernel_tick")
        obs_total = _bucket_nanos("fast_kernel_observe_flat")
        proj_total = _bucket_nanos("fast_kernel_projection")
        apply_total = _bucket_nanos("fast_kernel_apply_actions")
        kernel_total = _bucket_nanos("fast_kernel_total")
        tick_calls = max(1, _bucket_calls("fast_kernel_tick"))

        # Per-stage data
        stage_results: list[StageTimingResult] = []
        stages_sum = 0
        for i, name in enumerate(HEADLESS_TICK_STAGE_NAMES):
            bucket_name = f"fast_kernel_tick_stage_{name}"
            stage_nanos = _bucket_nanos(bucket_name)
            stage_calls = max(1, _bucket_calls(bucket_name))
            stages_sum += stage_nanos
            stage_results.append(
                StageTimingResult(
                    index=i + 1,
                    name=name,
                    total_nanos=stage_nanos,
                    mean_nanos_per_tick=stage_nanos / tick_calls,
                    pct_of_tick=stage_nanos * 100.0 / max(1, tick_total),
                    pct_of_kernel=stage_nanos * 100.0 / max(1, kernel_total),
                    calls=stage_calls,
                )
            )

        # Sort by runtime share descending for the report
        stage_results.sort(key=lambda s: s.total_nanos, reverse=True)

        delta_pct = (
            (stages_sum - tick_total) * 100.0 / max(1, tick_total)
            if tick_total > 0
            else 0.0
        )

        return TickStageReport(
            created_at=datetime.now(UTC).isoformat(),
            env_count=env_count,
            rounds=rounds,
            warmup_rounds=warmup_rounds,
            total_ticks=tick_calls,
            tick_total_nanos=tick_total,
            kernel_total_nanos=kernel_total,
            observe_flat_nanos=obs_total,
            projection_nanos=proj_total,
            apply_actions_nanos=apply_total,
            wall_total_nanos=wall_total,
            stages=stage_results,
            stages_sum_nanos=stages_sum,
            tick_vs_stages_delta_pct=delta_pct,
        )
    finally:
        vecenv.close()


def format_tick_stage_report(report: TickStageReport) -> str:
    lines = [
        "=" * 90,
        "PER-TICK-STAGE TIMING REPORT (PR 3.1)",
        "=" * 90,
        f"Env count: {report.env_count}   Rounds: {report.rounds}   "
        f"Warmup: {report.warmup_rounds}   Total ticks: {report.total_ticks}",
        "",
        "--- Kernel Phase Breakdown ---",
        f"  tick (all stages):  {report.tick_total_nanos / 1e6:>10,.1f} ms  "
        f"({report.tick_total_nanos * 100.0 / max(1, report.kernel_total_nanos):>5.1f}% of kernel)",
        f"  observe_flat:       {report.observe_flat_nanos / 1e6:>10,.1f} ms  "
        f"({report.observe_flat_nanos * 100.0 / max(1, report.kernel_total_nanos):>5.1f}% of kernel)",
        f"  projection:         {report.projection_nanos / 1e6:>10,.1f} ms  "
        f"({report.projection_nanos * 100.0 / max(1, report.kernel_total_nanos):>5.1f}% of kernel)",
        f"  apply_actions:      {report.apply_actions_nanos / 1e6:>10,.1f} ms  "
        f"({report.apply_actions_nanos * 100.0 / max(1, report.kernel_total_nanos):>5.1f}% of kernel)",
        f"  kernel total:       {report.kernel_total_nanos / 1e6:>10,.1f} ms",
        f"  wall total:         {report.wall_total_nanos / 1e6:>10,.1f} ms",
        "",
        "--- Per-Tick-Stage Breakdown (ranked by runtime share) ---",
        f"{'#':>3}  {'Stage':30s}  {'Total (ms)':>12s}  {'Mean/tick (ns)':>16s}  "
        f"{'% of tick':>10s}  {'% of kernel':>12s}",
        "-" * 90,
    ]
    for s in report.stages:
        lines.append(
            f"  {s.index:>2}  {s.name:30s}  {s.total_nanos / 1e6:>12,.2f}  "
            f"{s.mean_nanos_per_tick:>16,.0f}  "
            f"{s.pct_of_tick:>10.1f}%  {s.pct_of_kernel:>11.1f}%"
        )
    lines.append("-" * 90)
    lines.append(
        f"      {'SUM':30s}  {report.stages_sum_nanos / 1e6:>12,.2f}  "
        f"{'':>16s}  {'100.0':>10s}%  "
        f"{report.stages_sum_nanos * 100.0 / max(1, report.kernel_total_nanos):>11.1f}%"
    )
    lines.append("")
    lines.append(
        f"  tick total vs stages sum delta: {report.tick_vs_stages_delta_pct:+.1f}%"
        f"  (measurement overhead from per-stage timing)"
    )
    lines.append("=" * 90)
    return "\n".join(lines)


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser(
        description="Per-tick-stage timing benchmark (forensic investigation PR 3.1)."
    )
    parser.add_argument("--env-count", type=int, default=64)
    parser.add_argument("--rounds", type=int, default=512)
    parser.add_argument("--warmup-rounds", type=int, default=16)
    parser.add_argument(
        "--config",
        type=Path,
        default=Path("configs/benchmark/sps_decomposition_v0.yaml"),
    )
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    report = run_tick_stage_benchmark(
        env_count=args.env_count,
        rounds=args.rounds,
        warmup_rounds=args.warmup_rounds,
        config_path=args.config,
    )

    print(format_tick_stage_report(report))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(report.to_dict(), indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    print(f"\nResults written to: {args.output}")


if __name__ == "__main__":
    main()
