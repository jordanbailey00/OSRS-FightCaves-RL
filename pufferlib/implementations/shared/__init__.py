# Shared helpers for the Ocean env comparison experiment.
#
# DOCUMENTED EXCEPTION: This shared/ directory contains two modules used by all
# env implementations:
#   - gumbel_sample.py : Gumbel-max sampling + fused entropy (from runescape-rl)
#   - benchmark_engine.py : Standalone PPO training loop for benchmarking
#
# These are shared because they are the core of the RS-style architecture being
# tested, and duplicating them per-env would add maintenance burden with no
# scientific benefit. Each env's benchmark.py imports from here.
