#!/usr/bin/env python3
"""Benchmark: Breakout — Stock PufferLib vs RS-style architecture.

Breakout is a medium-complexity Ocean env: obs=(118,) float32, action=Discrete(3).
The larger observation vector (vs CartPole's 4-dim) tests encoder throughput.
Episode lengths vary significantly, which tests rollout buffer management.

Run: python -m implementations.breakout.benchmark [--device cuda|cpu] [--timesteps N]
"""
from __future__ import annotations

import argparse
import sys
import os

PUFFERLIB_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if PUFFERLIB_ROOT not in sys.path:
    sys.path.insert(0, PUFFERLIB_ROOT)

import pufferlib.pytorch
from pufferlib.ocean.breakout.breakout import Breakout

from implementations.shared.benchmark_engine import (
    BenchmarkConfig,
    run_env_only_benchmark,
    run_training_benchmark,
    print_comparison,
)
from implementations.shared.gumbel_sample import sample_logits_gumbel
from implementations.breakout.policy import BreakoutRSPolicy


def make_policy(vecenv):
    return BreakoutRSPolicy(vecenv.driver_env)


ENV_KWARGS = {
    "frameskip": 4,
    "width": 576,
    "height": 330,
    "brick_rows": 6,
    "brick_cols": 18,
}


def main():
    parser = argparse.ArgumentParser(description="Breakout benchmark: stock vs RS-style")
    parser.add_argument("--device", default="cuda", choices=["cuda", "cpu"])
    parser.add_argument("--num-envs", type=int, default=4096)
    parser.add_argument("--timesteps", type=int, default=200_000)
    parser.add_argument("--duration", type=float, default=0)
    parser.add_argument("--env-only-duration", type=float, default=10.0)
    parser.add_argument("--skip-env-only", action="store_true")
    args = parser.parse_args()

    config = BenchmarkConfig(
        num_envs=args.num_envs,
        bptt_horizon=16,
        total_timesteps=args.timesteps,
        benchmark_duration_s=args.duration,
        device=args.device,
        learning_rate=0.05,
        gamma=0.97,
        minibatch_size=min(32768, args.num_envs * 16),
        prio_alpha=0.0,
    )

    results = []

    if not args.skip_env_only:
        print(f"[Breakout] Benchmarking env-only SPS ({args.num_envs} envs, {args.env_only_duration}s)...")
        env_result = run_env_only_benchmark(
            Breakout, ENV_KWARGS, num_envs=args.num_envs, duration_s=args.env_only_duration
        )
        results.append(env_result)
        print(f"  Env-only SPS: {env_result.sps:,.0f}")

    print(f"\n[Breakout] Benchmarking STOCK training ({config.device})...")
    stock_result = run_training_benchmark(
        Breakout, ENV_KWARGS, make_policy, config,
        sample_fn=pufferlib.pytorch.sample_logits,
        label="stock_multinomial",
    )
    results.append(stock_result)
    print(f"  Stock SPS: {stock_result.sps:,.0f}")

    print(f"\n[Breakout] Benchmarking RS-STYLE training ({config.device})...")
    rs_result = run_training_benchmark(
        Breakout, ENV_KWARGS, make_policy, config,
        sample_fn=sample_logits_gumbel,
        label="rs_gumbel_fused",
    )
    results.append(rs_result)
    print(f"  RS-style SPS: {rs_result.sps:,.0f}")

    print_comparison(results)
    return results


if __name__ == "__main__":
    main()
