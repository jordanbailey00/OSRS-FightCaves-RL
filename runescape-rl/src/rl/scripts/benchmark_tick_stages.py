#!/usr/bin/env python3
"""Entry point for the per-tick-stage timing benchmark (forensic investigation PR 3.1).

Runs the v2 fast kernel environment and extracts per-stage timing data
from GameLoop instrumentation.

Usage:
    cd training-env/rl
    python scripts/benchmark_tick_stages.py \\
        --config configs/benchmark/sps_decomposition_v0.yaml \\
        --output results/tick_stages.json

    # With env-count sweep:
    for n in 4 16 64; do
        python scripts/benchmark_tick_stages.py \\
            --env-count $n --rounds 512 \\
            --output results/tick_stages_${n}env.json
    done
"""
from fight_caves_rl.benchmarks.tick_stage_bench import main

if __name__ == "__main__":
    main()
