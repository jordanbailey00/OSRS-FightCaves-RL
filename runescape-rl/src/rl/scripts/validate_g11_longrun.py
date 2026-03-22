#!/usr/bin/env python3
"""G1.1 — Long-run GPU-mainline training validation.

Validates that the optimized GPU training pipeline runs stably
and produces meaningful learning signal over sustained training.
"""
from __future__ import annotations

import json
import math
import sys
from collections import defaultdict
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path
from time import perf_counter
from typing import Any

import numpy as np
import torch


@dataclass
class EpisodeRecord:
    env_index: int
    length: int
    return_sum: float
    terminal_code: int


@dataclass
class CheckpointSnapshot:
    global_step: int
    elapsed_seconds: float
    sps: float
    episodes_completed: int
    mean_episode_length: float
    mean_episode_return: float
    max_episode_length: int
    losses: dict[str, float]
    stable: bool


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser(description="G1.1 — Long-run GPU training validation.")
    parser.add_argument("--total-timesteps", type=int, default=262144)
    parser.add_argument("--env-count", type=int, default=64)
    parser.add_argument("--config", type=Path, default=Path("configs/train/train_fast_v2_mlp64.yaml"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--log-interval", type=int, default=16384,
                        help="Log a checkpoint snapshot every N steps")
    args = parser.parse_args()

    from fight_caves_rl.benchmarks.train_ceiling_bench import _clamp_train_config_for_benchmark
    from fight_caves_rl.policies.registry import build_policy_from_config
    from fight_caves_rl.puffer.factory import build_puffer_train_config, load_smoke_train_config, make_vecenv
    from fight_caves_rl.puffer.trainer import ConfigurablePuffeRL

    class _NullLogger:
        def __init__(self):
            self.run_id = "g1_1_longrun"
            self.records, self.artifact_records, self.effective_tags = [], [], ()
        def log(self, *a, **k): pass
        def close(self, *a, **k): pass
        def build_artifact_record(self, *a, **k): raise RuntimeError("N/A")
        def update_config(self, *a, **k): pass
        def log_artifact(self, *a, **k): pass
        def finish(self): pass

    total_timesteps = args.total_timesteps
    config = load_smoke_train_config(args.config)
    config["num_envs"] = args.env_count
    config["train"]["total_timesteps"] = total_timesteps
    config.setdefault("logging", {})["dashboard"] = False
    # Clamp batch params to be valid for the total timesteps
    effective_batch = max(1, min(total_timesteps, int(config["train"]["batch_size"])))
    config["train"]["batch_size"] = effective_batch
    config["train"]["minibatch_size"] = max(1, min(int(config["train"]["minibatch_size"]), effective_batch))
    config["train"]["max_minibatch_size"] = max(int(config["train"]["minibatch_size"]), min(int(config["train"]["max_minibatch_size"]), effective_batch))
    config["train"]["bptt_horizon"] = max(1, min(int(config["train"]["bptt_horizon"]), effective_batch))
    config["train"]["checkpoint_interval"] = 1_000_000  # No checkpointing during validation

    device = config["train"]["device"]
    print(f"G1.1 — Long-run GPU-mainline training validation")
    print(f"  Config: {args.config}")
    print(f"  Device: {device}")
    print(f"  Envs: {args.env_count}, Timesteps: {total_timesteps:,}")
    print(f"  Log interval: {args.log_interval:,}")
    if torch.cuda.is_available():
        print(f"  GPU: {torch.cuda.get_device_properties(0).name}")
        torch.cuda.reset_peak_memory_stats()
    print()

    # Build env + trainer
    vecenv = make_vecenv(config, backend="subprocess")
    policy = build_policy_from_config(config, vecenv.single_observation_space, vecenv.single_action_space)
    if device == "cuda":
        policy = policy.to("cuda")

    train_config = build_puffer_train_config(config, data_dir=Path("/tmp/fc_g11_longrun"), total_timesteps=total_timesteps)
    trainer = ConfigurablePuffeRL(
        train_config, vecenv, policy, _NullLogger(),
        dashboard_enabled=False, checkpointing_enabled=False,
        profiling_enabled=False, utilization_enabled=False,
        logging_enabled=True, instrumentation_enabled=False,
    )

    # Tracking
    snapshots: list[CheckpointSnapshot] = []
    all_episode_lengths: list[int] = []
    all_episode_returns: list[float] = []
    errors: list[str] = []
    stable = True
    last_log_step = 0
    started = perf_counter()

    print(f"{'Step':>10s}  {'SPS':>8s}  {'Episodes':>8s}  {'MeanLen':>8s}  {'MeanRet':>10s}  {'MaxLen':>8s}  {'Stable':>7s}")
    print("-" * 75)

    try:
        while trainer.global_step < total_timesteps:
            trainer.evaluate()
            trainer.train()

            # Collect episode stats from trainer.stats
            ep_returns = trainer.stats.get("episode_return", [])
            ep_lengths = trainer.stats.get("episode_length", [])
            if ep_returns:
                all_episode_returns.extend(ep_returns)
            if ep_lengths:
                all_episode_lengths.extend(int(x) for x in ep_lengths)

            # Check for NaN/Inf in losses
            for k, v in trainer.losses.items():
                if isinstance(v, float) and (math.isnan(v) or math.isinf(v)):
                    errors.append(f"step={trainer.global_step}: {k}={v}")
                    stable = False

            # Periodic logging
            current_step = int(trainer.global_step)
            if current_step - last_log_step >= args.log_interval:
                elapsed = perf_counter() - started
                sps = current_step / elapsed if elapsed > 0 else 0
                n_eps = len(all_episode_lengths)
                mean_len = np.mean(all_episode_lengths) if all_episode_lengths else 0
                mean_ret = np.mean(all_episode_returns) if all_episode_returns else 0
                max_len = max(all_episode_lengths) if all_episode_lengths else 0

                snap = CheckpointSnapshot(
                    global_step=current_step,
                    elapsed_seconds=elapsed,
                    sps=sps,
                    episodes_completed=n_eps,
                    mean_episode_length=float(mean_len),
                    mean_episode_return=float(mean_ret),
                    max_episode_length=max_len,
                    losses=dict(trainer.losses),
                    stable=stable,
                )
                snapshots.append(snap)
                print(f"{current_step:>10,d}  {sps:>8.0f}  {n_eps:>8d}  {mean_len:>8.1f}  {mean_ret:>10.2f}  {max_len:>8d}  {'yes' if stable else 'NO':>7s}")
                last_log_step = current_step

    except Exception as exc:
        errors.append(f"CRASH at step {trainer.global_step}: {type(exc).__name__}: {exc}")
        stable = False
        print(f"\nERROR: {exc}")

    finally:
        final_step = int(trainer.global_step)
        elapsed = perf_counter() - started
        try:
            trainer.close()
        except Exception:
            pass

    # Final summary
    sps = final_step / elapsed if elapsed > 0 else 0
    vram = torch.cuda.max_memory_allocated() / 1024**2 if device == "cuda" else 0
    n_eps = len(all_episode_lengths)

    print(f"\n{'=' * 75}")
    print(f"G1.1 RESULTS — Long-run GPU training validation")
    print(f"{'=' * 75}")
    print(f"Total steps: {final_step:,} / {total_timesteps:,}")
    print(f"Elapsed: {elapsed:.1f}s")
    print(f"SPS: {sps:.0f}")
    print(f"Episodes completed: {n_eps}")
    if all_episode_lengths:
        print(f"Episode length: mean={np.mean(all_episode_lengths):.1f}, median={np.median(all_episode_lengths):.0f}, max={max(all_episode_lengths)}, min={min(all_episode_lengths)}")
    if all_episode_returns:
        print(f"Episode return: mean={np.mean(all_episode_returns):.2f}, max={max(all_episode_returns):.2f}, min={min(all_episode_returns):.2f}")
    if device == "cuda":
        print(f"VRAM peak: {vram:.0f} MB")
    print(f"Stable: {'YES' if stable else 'NO'}")
    if errors:
        print(f"Errors ({len(errors)}):")
        for e in errors[:10]:
            print(f"  {e}")
    print(f"{'=' * 75}")

    # Write results
    report = {
        "created_at": datetime.now(UTC).isoformat(),
        "config_path": str(args.config),
        "device": device,
        "total_timesteps_target": total_timesteps,
        "total_timesteps_actual": final_step,
        "env_count": args.env_count,
        "elapsed_seconds": elapsed,
        "sps": sps,
        "vram_peak_mb": vram,
        "stable": stable,
        "episodes_completed": n_eps,
        "episode_lengths": all_episode_lengths,
        "episode_returns": [float(x) for x in all_episode_returns],
        "errors": errors,
        "snapshots": [asdict(s) for s in snapshots],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    print(f"\nResults written to: {args.output}")


if __name__ == "__main__":
    main()
