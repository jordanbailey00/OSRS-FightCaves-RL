#!/usr/bin/env python3
"""Benchmark: Boids — Stock PufferLib vs RS-style architecture.

Boids is the most architecturally relevant comparison env:
  obs=(num_boids*4,) float32, action=MultiDiscrete([5,5]).

This is the only selected env with MultiDiscrete actions, matching
runescape-rl's action space style. The RS-style policy uses separate
action heads per dimension, which is the exact architecture runescape-rl uses.

This benchmark answers: "Does the MultiDiscrete action head architecture
from runescape-rl introduce overhead on a known-fast env?"

Run: python -m implementations.boids.benchmark [--device cuda|cpu] [--timesteps N]
"""
from __future__ import annotations

import argparse
import sys
import os

PUFFERLIB_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if PUFFERLIB_ROOT not in sys.path:
    sys.path.insert(0, PUFFERLIB_ROOT)

import pufferlib.pytorch
from pufferlib.ocean.boids.boids import Boids

from implementations.shared.benchmark_engine import (
    BenchmarkConfig,
    run_env_only_benchmark,
    run_training_benchmark,
    print_comparison,
)
from implementations.shared.gumbel_sample import sample_logits_gumbel
from implementations.boids.policy import BoidsRSPolicy


def make_policy(vecenv):
    return BoidsRSPolicy(vecenv.driver_env)


ENV_KWARGS = {
    "num_boids": 64,
    "margin_turn_factor": 0.0,
    "centering_factor": 0.0,
    "avoid_factor": 1.0,
    "matching_factor": 1.0,
}


def main():
    parser = argparse.ArgumentParser(description="Boids benchmark: stock vs RS-style")
    parser.add_argument("--device", default="cuda", choices=["cuda", "cpu"])
    parser.add_argument("--num-envs", type=int, default=64, help="Number of env instances (each has 64 boids)")
    parser.add_argument("--timesteps", type=int, default=200_000)
    parser.add_argument("--duration", type=float, default=0)
    parser.add_argument("--env-only-duration", type=float, default=10.0)
    parser.add_argument("--skip-env-only", action="store_true")
    args = parser.parse_args()

    total_agents = args.num_envs * ENV_KWARGS["num_boids"]
    config = BenchmarkConfig(
        num_envs=args.num_envs,
        bptt_horizon=16,
        total_timesteps=args.timesteps,
        benchmark_duration_s=args.duration,
        device=args.device,
        learning_rate=0.025,
        gamma=0.95,
        minibatch_size=min(16384, total_agents * 16),
        prio_alpha=0.0,
    )

    results = []

    if not args.skip_env_only:
        print(f"[Boids] Benchmarking env-only SPS ({args.num_envs} envs, {total_agents} agents, {args.env_only_duration}s)...")
        env_result = run_env_only_benchmark(
            Boids, ENV_KWARGS, num_envs=args.num_envs, duration_s=args.env_only_duration
        )
        results.append(env_result)
        print(f"  Env-only SPS: {env_result.sps:,.0f}")

    print(f"\n[Boids] Benchmarking STOCK training ({config.device})...")
    stock_result = run_training_benchmark(
        Boids, ENV_KWARGS, make_policy, config,
        sample_fn=pufferlib.pytorch.sample_logits,
        label="stock_multinomial",
    )
    results.append(stock_result)
    print(f"  Stock SPS: {stock_result.sps:,.0f}")

    print(f"\n[Boids] Benchmarking RS-STYLE training ({config.device})...")
    rs_result = run_training_benchmark(
        Boids, ENV_KWARGS, make_policy, config,
        sample_fn=sample_logits_gumbel,
        label="rs_gumbel_fused",
    )
    results.append(rs_result)
    print(f"  RS-style SPS: {rs_result.sps:,.0f}")

    print_comparison(results)
    return results


if __name__ == "__main__":
    main()
