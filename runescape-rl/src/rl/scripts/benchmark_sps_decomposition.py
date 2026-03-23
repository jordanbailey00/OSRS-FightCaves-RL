#!/usr/bin/env python3
"""Entry point for the SPS decomposition benchmark (forensic investigation PR 2.1).

Runs the v2 fast kernel environment in embedded mode and decomposes SPS
into four modes:

  Mode 1 — core simulation only (JVM tick)
  Mode 2 — simulation + reward/done (tick + projection)
  Mode 3 — simulation + observation (tick + observe_flat)
  Mode 4 — full public env step (wall clock)

Usage:
    cd training-env/rl
    python scripts/benchmark_sps_decomposition.py \\
        --config configs/benchmark/sps_decomposition_v0.yaml \\
        --output results/sps_decomposition.json

    # Override env count or rounds:
    python scripts/benchmark_sps_decomposition.py \\
        --config configs/benchmark/sps_decomposition_v0.yaml \\
        --env-count 16 --rounds 512 \\
        --output results/sps_decomposition_16env.json
"""
from fight_caves_rl.benchmarks.sps_decomposition_bench import main

if __name__ == "__main__":
    main()
