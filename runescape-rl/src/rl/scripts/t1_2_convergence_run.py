#!/usr/bin/env python3
"""T1.2 convergence validation — single-method training run.

Usage:
    python scripts/t1_2_convergence_run.py --method gumbel --output results/t1_2_gumbel.json
    python scripts/t1_2_convergence_run.py --method multinomial --output results/t1_2_multinomial.json
"""
from __future__ import annotations
import argparse
import json
from collections import defaultdict
from pathlib import Path
from time import perf_counter

import numpy as np
import torch
import yaml

TOTAL_STEPS = 65536
SEED = 33021
ENV_COUNT = 64


def run(method: str, output: str) -> None:
    from fight_caves_rl.puffer.factory import build_puffer_train_config, make_vecenv
    from fight_caves_rl.policies.mlp import MultiDiscreteMLPPolicy
    from fight_caves_rl.puffer.trainer import ConfigurablePuffeRL

    # If multinomial, monkey-patch the trainer to use original sampling
    if method == "multinomial":
        import pufferlib.pytorch
        import fight_caves_rl.puffer.trainer as trainer_module
        trainer_module.sample_logits_gumbel = pufferlib.pytorch.sample_logits
        print(f"Patched trainer to use original multinomial sampling")

    config_path = "configs/benchmark/fast_train_v2_mlp64.yaml"
    config = yaml.safe_load(Path(config_path).read_text(encoding="utf-8"))
    config["num_envs"] = ENV_COUNT
    config["train"]["total_timesteps"] = TOTAL_STEPS
    config["train"]["seed"] = SEED
    config.setdefault("logging", {})["dashboard"] = False

    torch.manual_seed(SEED)
    np.random.seed(SEED)

    vecenv = make_vecenv(config, instrumentation_enabled=False)
    policy = MultiDiscreteMLPPolicy.from_spaces(
        vecenv.single_observation_space,
        vecenv.single_action_space,
        hidden_size=int(config["policy"]["hidden_size"]),
    )
    train_config = build_puffer_train_config(
        config, data_dir=Path(f"/tmp/t1_2_{method}"), total_timesteps=TOTAL_STEPS,
    )

    class _NullLogger:
        run_id = "null"
        records = []
        artifact_records = []
        effective_tags = ()
        def log(self, *a, **k): pass
        def close(self, *a, **k): pass
        def build_artifact_record(self, *a, **k): raise RuntimeError
        def update_config(self, *a, **k): pass
        def log_artifact(self, *a, **k): pass
        def finish(self): pass

    trainer = ConfigurablePuffeRL(
        train_config, vecenv, policy, _NullLogger(),
        dashboard_enabled=False, checkpointing_enabled=False,
        profiling_enabled=False, utilization_enabled=False,
        logging_enabled=False, instrumentation_enabled=False,
    )

    returns_log = []
    started = perf_counter()
    while trainer.global_step < TOTAL_STEPS:
        stats = trainer.evaluate()
        trainer.train()
        if "episode_return" in stats and stats["episode_return"]:
            mean_ret = float(np.mean(stats["episode_return"]))
            returns_log.append({"step": int(trainer.global_step), "mean_return": mean_ret,
                                "n_episodes": len(stats["episode_return"])})
            print(f"  step={trainer.global_step:>6}  mean_return={mean_ret:.2f}  "
                  f"episodes={len(stats['episode_return'])}")

    elapsed = perf_counter() - started
    final_step = int(trainer.global_step)
    trainer.close()

    any_nan = any(np.isnan(r["mean_return"]) or np.isinf(r["mean_return"]) for r in returns_log)

    result = {
        "method": method,
        "total_steps": final_step,
        "elapsed_seconds": elapsed,
        "sps": final_step / elapsed if elapsed > 0 else 0,
        "returns": returns_log,
        "any_nan_inf": any_nan,
        "n_return_samples": len(returns_log),
    }
    Path(output).parent.mkdir(parents=True, exist_ok=True)
    with open(output, "w") as f:
        json.dump(result, f, indent=2)
    print(f"\n{method}: {final_step} steps in {elapsed:.1f}s = {result['sps']:.1f} SPS")
    print(f"Return samples: {len(returns_log)}, NaN/Inf: {any_nan}")
    print(f"Written to {output}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--method", choices=["gumbel", "multinomial"], required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    run(args.method, args.output)
