#!/usr/bin/env python3
"""Run all Ocean env benchmarks and produce a comparison summary.

Usage:
    cd /home/joe/projects/runescape-rl/claude/pufferlib
    python -m implementations.run_all_benchmarks [--device cuda|cpu] [--timesteps N]
"""
from __future__ import annotations

import argparse
import sys
import os
import traceback

PUFFERLIB_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if PUFFERLIB_ROOT not in sys.path:
    sys.path.insert(0, PUFFERLIB_ROOT)

from implementations.shared.benchmark_engine import BenchmarkResult, print_comparison


def main():
    parser = argparse.ArgumentParser(description="Run all Ocean env benchmarks")
    parser.add_argument("--device", default="cuda", choices=["cuda", "cpu"])
    parser.add_argument("--timesteps", type=int, default=200_000)
    parser.add_argument("--skip-env-only", action="store_true")
    args = parser.parse_args()

    # Override sys.argv so per-env benchmarks see our args
    base_argv = ["benchmark", f"--device={args.device}", f"--timesteps={args.timesteps}"]
    if args.skip_env_only:
        base_argv.append("--skip-env-only")

    all_results: dict[str, list[BenchmarkResult]] = {}

    envs = [
        ("CartPole", "implementations.cartpole.benchmark"),
        ("Breakout", "implementations.breakout.benchmark"),
        ("Snake", "implementations.snake.benchmark"),
        ("Boids", "implementations.boids.benchmark"),
    ]

    for env_name, module_path in envs:
        print(f"\n{'='*80}")
        print(f"  {env_name}")
        print(f"{'='*80}")
        saved_argv = sys.argv
        sys.argv = base_argv[:]
        try:
            mod = __import__(module_path, fromlist=["main"])
            results = mod.main()
            all_results[env_name] = results
        except Exception:
            print(f"  ERROR running {env_name}:")
            traceback.print_exc()
            all_results[env_name] = []
        finally:
            sys.argv = saved_argv

    # Summary
    print("\n" + "=" * 80)
    print("  CROSS-ENV COMPARISON SUMMARY")
    print("=" * 80)
    print(f"\n{'Env':<12} {'Env-Only SPS':>14} {'Stock SPS':>14} {'RS-Style SPS':>14} {'RS/Stock':>10}")
    print("-" * 64)
    for env_name, results in all_results.items():
        env_sps = "-"
        stock_sps = "-"
        rs_sps = "-"
        ratio = "-"
        for r in results:
            if r.label == "env_only":
                env_sps = f"{r.sps:,.0f}"
            elif r.label == "stock_multinomial":
                stock_sps = f"{r.sps:,.0f}"
            elif r.label == "rs_gumbel_fused":
                rs_sps = f"{r.sps:,.0f}"
        stock_r = next((r for r in results if r.label == "stock_multinomial"), None)
        rs_r = next((r for r in results if r.label == "rs_gumbel_fused"), None)
        if stock_r and rs_r and stock_r.sps > 0:
            ratio = f"{rs_r.sps / stock_r.sps:.3f}x"
        print(f"{env_name:<12} {env_sps:>14} {stock_sps:>14} {rs_sps:>14} {ratio:>10}")
    print()


if __name__ == "__main__":
    main()
