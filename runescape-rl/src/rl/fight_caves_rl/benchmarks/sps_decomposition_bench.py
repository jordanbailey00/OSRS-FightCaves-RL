"""SPS decomposition benchmark for the forensic performance investigation.

Runs the v2 fast kernel environment in embedded mode with per-round
phase decomposition.  Produces modes 1-4 from a single run:

  Mode 1 — core simulation only          (JVM tickNanos)
  Mode 2 — simulation + reward/done      (tickNanos + projectionNanos)
  Mode 3 — simulation + observation       (tickNanos + observeFlatNanos)
  Mode 4 — full public env step           (total wall clock including Python)

Collects per-round timings so p50/p95/p99 latencies can be reported.
"""
from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from datetime import UTC, datetime
from pathlib import Path
from time import perf_counter_ns
from typing import Any, Sequence

import numpy as np


@dataclass(frozen=True)
class SPSDecompositionConfig:
    config_id: str
    env_count: int
    rounds: int
    warmup_rounds: int
    start_wave: int
    tick_cap: int
    ammo: int
    prayer_potions: int
    sharks: int
    seed_base: int
    action_id: int


@dataclass(frozen=True)
class LatencyStats:
    mean_ns: float
    p50_ns: float
    p95_ns: float
    p99_ns: float
    min_ns: float
    max_ns: float
    std_ns: float

    def to_dict(self) -> dict[str, float]:
        return asdict(self)


@dataclass(frozen=True)
class ModeResult:
    label: str
    description: str
    total_nanos: int
    env_steps: int
    sps: float
    mean_step_nanos: float
    latency: LatencyStats

    def to_dict(self) -> dict[str, Any]:
        d = asdict(self)
        d["latency"] = self.latency.to_dict()
        return d


@dataclass(frozen=True)
class PythonOverheadBreakdown:
    action_pack_nanos: int
    jpype_call_nanos: int
    buffer_apply_nanos: int
    done_reset_nanos: int
    total_send_nanos: int
    recv_nanos: int

    def to_dict(self) -> dict[str, int]:
        return asdict(self)


@dataclass(frozen=True)
class SPSDecompositionReport:
    created_at: str
    config: SPSDecompositionConfig
    env_count: int
    rounds: int
    warmup_rounds: int
    mode_1_core_sim: ModeResult
    mode_2_sim_reward: ModeResult
    mode_3_sim_obs: ModeResult
    mode_4_full_env_step: ModeResult
    jvm_kernel_totals: dict[str, float]
    python_overhead: PythonOverheadBreakdown
    jvm_pct_of_total: float
    python_pct_of_total: float
    memory_profile: dict[str, int]

    def to_dict(self) -> dict[str, Any]:
        d: dict[str, Any] = {
            "created_at": self.created_at,
            "config": asdict(self.config),
            "env_count": self.env_count,
            "rounds": self.rounds,
            "warmup_rounds": self.warmup_rounds,
            "mode_1_core_sim": self.mode_1_core_sim.to_dict(),
            "mode_2_sim_reward": self.mode_2_sim_reward.to_dict(),
            "mode_3_sim_obs": self.mode_3_sim_obs.to_dict(),
            "mode_4_full_env_step": self.mode_4_full_env_step.to_dict(),
            "jvm_kernel_totals": dict(self.jvm_kernel_totals),
            "python_overhead": self.python_overhead.to_dict(),
            "jvm_pct_of_total": self.jvm_pct_of_total,
            "python_pct_of_total": self.python_pct_of_total,
            "memory_profile": dict(self.memory_profile),
        }
        return d


def load_sps_decomposition_config(path: str | Path) -> SPSDecompositionConfig:
    import yaml

    payload = yaml.safe_load(Path(path).read_text(encoding="utf-8")) or {}
    return SPSDecompositionConfig(
        config_id=str(payload.get("config_id", "sps_decomposition")),
        env_count=int(payload.get("env_count", 64)),
        rounds=int(payload.get("rounds", 256)),
        warmup_rounds=int(payload.get("warmup_rounds", 16)),
        start_wave=int(payload.get("start_wave", 1)),
        tick_cap=int(payload.get("tick_cap", 20_000)),
        ammo=int(payload.get("ammo", 1000)),
        prayer_potions=int(payload.get("prayer_potions", 8)),
        sharks=int(payload.get("sharks", 20)),
        seed_base=int(payload.get("seed_base", 70_000)),
        action_id=int(payload.get("action_id", 0)),
    )


def run_sps_decomposition(
    config: SPSDecompositionConfig,
    *,
    rounds_override: int | None = None,
    env_count_override: int | None = None,
) -> SPSDecompositionReport:
    from fight_caves_rl.benchmarks.common import capture_peak_memory_profile
    from fight_caves_rl.bridge.contracts import HeadlessBootstrapConfig
    from fight_caves_rl.envs_fast.fast_reward_adapter import FastRewardAdapter
    from fight_caves_rl.envs_fast.fast_vector_env import (
        FastKernelVecEnv,
        FastKernelVecEnvConfig,
    )

    env_count = int(env_count_override if env_count_override is not None else config.env_count)
    rounds = int(rounds_override if rounds_override is not None else config.rounds)

    reward_adapter = FastRewardAdapter.from_config_id("reward_sparse_v2")
    vecenv = FastKernelVecEnv(
        FastKernelVecEnvConfig(
            env_count=env_count,
            account_name_prefix="sps_decomp_bench",
            start_wave=config.start_wave,
            ammo=config.ammo,
            prayer_potions=config.prayer_potions,
            sharks=config.sharks,
            tick_cap=config.tick_cap,
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
        vecenv.async_reset(config.seed_base)
        vecenv.recv()

        # Warmup
        for _ in range(config.warmup_rounds):
            vecenv.send(zero_action)
            vecenv.recv()
        vecenv.reset_instrumentation()

        # Per-round collection arrays
        round_wall_nanos = np.empty(rounds, dtype=np.int64)
        round_tick_nanos = np.empty(rounds, dtype=np.int64)
        round_obs_nanos = np.empty(rounds, dtype=np.int64)
        round_proj_nanos = np.empty(rounds, dtype=np.int64)
        round_apply_nanos = np.empty(rounds, dtype=np.int64)

        for i in range(rounds):
            wall_start = perf_counter_ns()
            vecenv.send(zero_action)
            vecenv.recv()
            round_wall_nanos[i] = perf_counter_ns() - wall_start

        # Extract instrumentation totals
        buckets = vecenv.instrumentation_snapshot()
        memory_profile = capture_peak_memory_profile()

        # JVM kernel phase totals (seconds → nanos)
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

        action_pack_total = _bucket_nanos("fast_vecenv_action_pack")
        step_batch_call_total = _bucket_nanos("fast_vecenv_step_batch_call")
        apply_buffers_total = _bucket_nanos("fast_vecenv_apply_step_buffers")
        send_total = _bucket_nanos("fast_vecenv_send_total")
        recv_total = _bucket_nanos("fast_vecenv_recv_total")
        reset_batch_total = _bucket_nanos("fast_vecenv_reset_batch_call")

        wall_total = int(round_wall_nanos.sum())
        total_env_steps = env_count * rounds

        # Derive per-round phase nanos from totals (evenly distributed approximation)
        # This is an approximation — true per-round would require JVM-side per-call recording
        calls = max(1, _bucket_calls("fast_kernel_tick"))
        tick_per_round = tick_total // calls if calls > 0 else 0
        obs_per_round = obs_total // calls if calls > 0 else 0
        proj_per_round = proj_total // calls if calls > 0 else 0

        # Mode 1: core sim only = tick time only
        mode_1_per_round = np.full(rounds, tick_per_round, dtype=np.int64)
        mode_1_total = tick_total

        # Mode 2: sim + reward/done = tick + projection
        mode_2_per_round = np.full(rounds, tick_per_round + proj_per_round, dtype=np.int64)
        mode_2_total = tick_total + proj_total

        # Mode 3: sim + observation = tick + observe
        mode_3_per_round = np.full(rounds, tick_per_round + obs_per_round, dtype=np.int64)
        mode_3_total = tick_total + obs_total

        # Mode 4: full env step = wall clock
        mode_4_total = wall_total

        def _make_mode(
            label: str,
            description: str,
            total_nanos: int,
            per_round_nanos: np.ndarray,
        ) -> ModeResult:
            sps = total_env_steps * 1_000_000_000.0 / max(1, total_nanos)
            mean_step_ns = total_nanos / max(1, total_env_steps)
            # Per-round nanos represent env_count steps each
            per_step_nanos = per_round_nanos / max(1, env_count)
            sorted_ns = np.sort(per_step_nanos)
            return ModeResult(
                label=label,
                description=description,
                total_nanos=int(total_nanos),
                env_steps=int(total_env_steps),
                sps=float(sps),
                mean_step_nanos=float(mean_step_ns),
                latency=LatencyStats(
                    mean_ns=float(np.mean(sorted_ns)),
                    p50_ns=float(np.percentile(sorted_ns, 50)),
                    p95_ns=float(np.percentile(sorted_ns, 95)),
                    p99_ns=float(np.percentile(sorted_ns, 99)),
                    min_ns=float(sorted_ns[0]),
                    max_ns=float(sorted_ns[-1]),
                    std_ns=float(np.std(sorted_ns)),
                ),
            )

        mode_1 = _make_mode(
            "mode_1_core_sim",
            "JVM tick only (12 headless stages)",
            mode_1_total,
            mode_1_per_round,
        )
        mode_2 = _make_mode(
            "mode_2_sim_reward",
            "JVM tick + reward/terminal projection",
            mode_2_total,
            mode_2_per_round,
        )
        mode_3 = _make_mode(
            "mode_3_sim_obs",
            "JVM tick + flat observation build",
            mode_3_total,
            mode_3_per_round,
        )
        mode_4 = _make_mode(
            "mode_4_full_env_step",
            "Full env step (JVM + Python + JPype crossing)",
            mode_4_total,
            round_wall_nanos,
        )

        done_reset_nanos = reset_batch_total
        python_overhead_nanos = max(0, wall_total - kernel_total - done_reset_nanos)
        jvm_pct = kernel_total * 100.0 / max(1, wall_total)
        python_pct = 100.0 - jvm_pct

        return SPSDecompositionReport(
            created_at=datetime.now(UTC).isoformat(),
            config=config,
            env_count=env_count,
            rounds=rounds,
            warmup_rounds=config.warmup_rounds,
            mode_1_core_sim=mode_1,
            mode_2_sim_reward=mode_2,
            mode_3_sim_obs=mode_3,
            mode_4_full_env_step=mode_4,
            jvm_kernel_totals={
                "tick_nanos": tick_total,
                "observe_flat_nanos": obs_total,
                "projection_nanos": proj_total,
                "apply_actions_nanos": apply_total,
                "kernel_total_nanos": kernel_total,
                "tick_pct": tick_total * 100.0 / max(1, kernel_total),
                "observe_flat_pct": obs_total * 100.0 / max(1, kernel_total),
                "projection_pct": proj_total * 100.0 / max(1, kernel_total),
                "apply_actions_pct": apply_total * 100.0 / max(1, kernel_total),
            },
            python_overhead=PythonOverheadBreakdown(
                action_pack_nanos=action_pack_total,
                jpype_call_nanos=step_batch_call_total,
                buffer_apply_nanos=apply_buffers_total,
                done_reset_nanos=done_reset_nanos,
                total_send_nanos=send_total,
                recv_nanos=recv_total,
            ),
            jvm_pct_of_total=float(jvm_pct),
            python_pct_of_total=float(python_pct),
            memory_profile=memory_profile,
        )
    finally:
        vecenv.close()


def format_report_summary(report: SPSDecompositionReport) -> str:
    lines = [
        "=" * 72,
        "SPS DECOMPOSITION REPORT",
        "=" * 72,
        f"Env count: {report.env_count}   Rounds: {report.rounds}   "
        f"Warmup: {report.warmup_rounds}",
        "",
        "--- Mode Results ---",
    ]
    for mode in (
        report.mode_1_core_sim,
        report.mode_2_sim_reward,
        report.mode_3_sim_obs,
        report.mode_4_full_env_step,
    ):
        lines.append(
            f"  {mode.label:30s}  {mode.sps:>12,.1f} SPS  "
            f"mean={mode.mean_step_nanos / 1000:>8,.1f}us  "
            f"p50={mode.latency.p50_ns / 1000:>8,.1f}us  "
            f"p95={mode.latency.p95_ns / 1000:>8,.1f}us  "
            f"p99={mode.latency.p99_ns / 1000:>8,.1f}us"
        )
    lines.append("")
    lines.append("--- JVM Kernel Phase Breakdown ---")
    kt = report.jvm_kernel_totals
    lines.append(f"  tick (12 stages):    {kt['tick_pct']:>6.1f}%  ({kt['tick_nanos'] / 1e6:>10,.1f} ms)")
    lines.append(f"  observe_flat:        {kt['observe_flat_pct']:>6.1f}%  ({kt['observe_flat_nanos'] / 1e6:>10,.1f} ms)")
    lines.append(f"  projection:          {kt['projection_pct']:>6.1f}%  ({kt['projection_nanos'] / 1e6:>10,.1f} ms)")
    lines.append(f"  apply_actions:       {kt['apply_actions_pct']:>6.1f}%  ({kt['apply_actions_nanos'] / 1e6:>10,.1f} ms)")
    lines.append(f"  kernel total:                  ({kt['kernel_total_nanos'] / 1e6:>10,.1f} ms)")
    lines.append("")
    lines.append("--- Wall Clock Attribution ---")
    lines.append(f"  JVM kernel:  {report.jvm_pct_of_total:>6.1f}% of wall time")
    lines.append(f"  Python+JPype:{report.python_pct_of_total:>6.1f}% of wall time")
    lines.append("")
    po = report.python_overhead
    lines.append("--- Python Overhead Detail ---")
    lines.append(f"  action_pack:    {po.action_pack_nanos / 1e6:>10,.1f} ms")
    lines.append(f"  jpype_call:     {po.jpype_call_nanos / 1e6:>10,.1f} ms")
    lines.append(f"  buffer_apply:   {po.buffer_apply_nanos / 1e6:>10,.1f} ms")
    lines.append(f"  done_reset:     {po.done_reset_nanos / 1e6:>10,.1f} ms")
    lines.append(f"  total_send:     {po.total_send_nanos / 1e6:>10,.1f} ms")
    lines.append(f"  recv:           {po.recv_nanos / 1e6:>10,.1f} ms")
    lines.append("")
    mp = report.memory_profile
    lines.append(f"Memory: process={mp.get('process_peak_rss_kib', 0) / 1024:.0f} MiB  "
                 f"children={mp.get('children_peak_rss_kib', 0) / 1024:.0f} MiB  "
                 f"combined={mp.get('combined_peak_rss_kib', 0) / 1024:.0f} MiB")
    lines.append("=" * 72)
    return "\n".join(lines)


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser(
        description="SPS decomposition benchmark (forensic investigation PR 2.1)."
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=Path("configs/benchmark/sps_decomposition_v0.yaml"),
        help="Benchmark config YAML.",
    )
    parser.add_argument("--rounds", type=int, default=None, help="Override rounds.")
    parser.add_argument("--env-count", type=int, default=None, help="Override env count.")
    parser.add_argument("--output", type=Path, required=True, help="JSON output path.")
    args = parser.parse_args()

    config = load_sps_decomposition_config(args.config)
    report = run_sps_decomposition(
        config,
        rounds_override=args.rounds,
        env_count_override=args.env_count,
    )
    print(format_report_summary(report))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(report.to_dict(), indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    print(f"\nResults written to: {args.output}")


if __name__ == "__main__":
    main()
