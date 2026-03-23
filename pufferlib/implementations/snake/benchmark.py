#!/usr/bin/env python3
"""Benchmark: Snake — Stock PufferLib vs RS-style architecture.

Snake is a multi-agent Ocean env: obs=(11,11) int8, action=Discrete(4).
Default: 16 envs * 256 agents = 4096 total agents.
Tests batch processing at scale — the large agent count exercises the
rollout buffer and sampling path with high throughput.

Run: python -m implementations.snake.benchmark [--device cuda|cpu] [--timesteps N]
"""
from __future__ import annotations

import argparse
import sys
import os

PUFFERLIB_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if PUFFERLIB_ROOT not in sys.path:
    sys.path.insert(0, PUFFERLIB_ROOT)

import pufferlib.pytorch
from pufferlib.ocean.snake.snake import Snake

from implementations.shared.benchmark_engine import (
    BenchmarkConfig,
    run_env_only_benchmark,
    run_training_benchmark,
    print_comparison,
)
from implementations.shared.gumbel_sample import sample_logits_gumbel
from implementations.snake.policy import SnakeRSPolicy


def make_policy(vecenv):
    return SnakeRSPolicy(vecenv.driver_env)


ENV_KWARGS = {
    "width": 640,
    "height": 360,
    "num_agents": 256,
    "num_food": 4096,
    "vision": 5,
    "leave_corpse_on_death": True,
    "reward_food": 0.1,
    "reward_corpse": 0.1,
    "reward_death": -1.0,
}


def main():
    parser = argparse.ArgumentParser(description="Snake benchmark: stock vs RS-style")
    parser.add_argument("--device", default="cuda", choices=["cuda", "cpu"])
    parser.add_argument("--num-envs", type=int, default=16, help="Number of env instances (each has 256 agents)")
    parser.add_argument("--timesteps", type=int, default=200_000)
    parser.add_argument("--duration", type=float, default=0)
    parser.add_argument("--env-only-duration", type=float, default=10.0)
    parser.add_argument("--skip-env-only", action="store_true")
    args = parser.parse_args()

    total_agents = args.num_envs * ENV_KWARGS["num_agents"]
    config = BenchmarkConfig(
        num_envs=args.num_envs,
        bptt_horizon=16,
        total_timesteps=args.timesteps,
        benchmark_duration_s=args.duration,
        device=args.device,
        learning_rate=0.03,
        gamma=0.99,
        minibatch_size=min(32768, total_agents * 16),
        prio_alpha=0.0,
    )

    results = []

    if not args.skip_env_only:
        print(f"[Snake] Benchmarking env-only SPS ({args.num_envs} envs, {total_agents} agents, {args.env_only_duration}s)...")
        env_result = run_env_only_benchmark(
            Snake, ENV_KWARGS, num_envs=args.num_envs, duration_s=args.env_only_duration
        )
        results.append(env_result)
        print(f"  Env-only SPS: {env_result.sps:,.0f}")

    print(f"\n[Snake] Benchmarking STOCK training ({config.device})...")
    stock_result = run_training_benchmark(
        Snake, ENV_KWARGS, make_policy, config,
        sample_fn=pufferlib.pytorch.sample_logits,
        label="stock_multinomial",
    )
    results.append(stock_result)
    print(f"  Stock SPS: {stock_result.sps:,.0f}")

    print(f"\n[Snake] Benchmarking RS-STYLE training ({config.device})...")
    rs_result = run_training_benchmark(
        Snake, ENV_KWARGS, make_policy, config,
        sample_fn=sample_logits_gumbel,
        label="rs_gumbel_fused",
    )
    results.append(rs_result)
    print(f"  RS-style SPS: {rs_result.sps:,.0f}")

    print_comparison(results)
    return results


if __name__ == "__main__":
    main()
