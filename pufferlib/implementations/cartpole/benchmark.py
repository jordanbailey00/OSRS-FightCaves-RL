#!/usr/bin/env python3
"""Benchmark: CartPole — Stock PufferLib vs RS-style architecture.

CartPole is the simplest/fastest Ocean env: obs=(4,) float32, action=Discrete(2).
This serves as the infrastructure overhead baseline — any SPS difference here
is purely from the sampling/entropy method, not env complexity.

Run: python -m implementations.cartpole.benchmark [--device cuda|cpu] [--timesteps N]
"""
from __future__ import annotations

import argparse
import sys
import os

# Ensure pufferlib root is on path
PUFFERLIB_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if PUFFERLIB_ROOT not in sys.path:
    sys.path.insert(0, PUFFERLIB_ROOT)

import pufferlib.pytorch
from pufferlib.ocean.cartpole.cartpole import Cartpole

from implementations.shared.benchmark_engine import (
    BenchmarkConfig,
    run_env_only_benchmark,
    run_training_benchmark,
    print_comparison,
)
from implementations.shared.gumbel_sample import sample_logits_gumbel
from implementations.cartpole.policy import CartPoleRSPolicy


# Stock policy: matches pufferlib.ocean.torch.Policy (Default) for CartPole
# Uses the same architecture as RS to isolate sampling/entropy effect
def make_rs_policy(vecenv):
    return CartPoleRSPolicy(vecenv.driver_env)


def make_stock_policy(vecenv):
    """Stock policy: same architecture, just used with stock sampling."""
    return CartPoleRSPolicy(vecenv.driver_env)


ENV_KWARGS = {
    "cart_mass": 1.0,
    "pole_mass": 0.1,
    "pole_length": 0.5,
    "gravity": 9.8,
    "force_mag": 10.0,
    "dt": 0.02,
}


def main():
    parser = argparse.ArgumentParser(description="CartPole benchmark: stock vs RS-style")
    parser.add_argument("--device", default="cuda", choices=["cuda", "cpu"])
    parser.add_argument("--num-envs", type=int, default=4096)
    parser.add_argument("--timesteps", type=int, default=200_000)
    parser.add_argument("--duration", type=float, default=0, help="Time-based benchmark (seconds)")
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
        gamma=0.95,
        minibatch_size=min(32768, args.num_envs * 16),
        prio_alpha=0.0,
    )

    results = []

    # 1. Env-only SPS
    if not args.skip_env_only:
        print(f"[CartPole] Benchmarking env-only SPS ({args.num_envs} envs, {args.env_only_duration}s)...")
        env_result = run_env_only_benchmark(
            Cartpole, ENV_KWARGS, num_envs=args.num_envs, duration_s=args.env_only_duration
        )
        results.append(env_result)
        print(f"  Env-only SPS: {env_result.sps:,.0f}")

    # 2. Stock training (multinomial sampling)
    print(f"\n[CartPole] Benchmarking STOCK training ({config.device})...")
    stock_result = run_training_benchmark(
        Cartpole, ENV_KWARGS, make_stock_policy, config,
        sample_fn=pufferlib.pytorch.sample_logits,
        label="stock_multinomial",
    )
    results.append(stock_result)
    print(f"  Stock SPS: {stock_result.sps:,.0f}")

    # 3. RS-style training (Gumbel-max + fused entropy)
    print(f"\n[CartPole] Benchmarking RS-STYLE training ({config.device})...")
    rs_result = run_training_benchmark(
        Cartpole, ENV_KWARGS, make_rs_policy, config,
        sample_fn=sample_logits_gumbel,
        label="rs_gumbel_fused",
    )
    results.append(rs_result)
    print(f"  RS-style SPS: {rs_result.sps:,.0f}")

    # Comparison
    print_comparison(results)

    return results


if __name__ == "__main__":
    main()
