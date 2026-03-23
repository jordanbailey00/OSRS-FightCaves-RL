#!/usr/bin/env python3
"""G1.3 — Reward / training-dynamics baseline.

Runs sustained monitored training with production tick_cap and episode
instrumentation. Reports episode return/length/wave trends and loss dynamics.
"""
from __future__ import annotations

import json
import math
import statistics
from collections import defaultdict
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path
from time import perf_counter
from typing import Any

import numpy as np
import torch


@dataclass
class WindowSnapshot:
    window_index: int
    global_step: int
    elapsed_seconds: float
    sps: float
    episodes_in_window: int
    cumulative_episodes: int
    mean_return: float
    mean_length: float
    max_wave: int
    losses: dict[str, float]
    stable: bool


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser(description="G1.3 — Training-dynamics baseline.")
    parser.add_argument("--total-timesteps", type=int, default=4_194_304)
    parser.add_argument("--env-count", type=int, default=64)
    parser.add_argument("--tick-cap", type=int, default=20000)
    parser.add_argument("--config", type=Path, default=Path("configs/train/train_fast_v2_mlp64.yaml"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--window-size", type=int, default=524_288,
                        help="Steps per reporting window")
    args = parser.parse_args()

    from fight_caves_rl.policies.registry import build_policy_from_config
    from fight_caves_rl.puffer.factory import build_puffer_train_config, load_smoke_train_config, make_vecenv
    from fight_caves_rl.puffer.trainer import ConfigurablePuffeRL

    class _NullLogger:
        def __init__(self):
            self.run_id = "g1_3_baseline"
            self.records, self.artifact_records, self.effective_tags = [], [], ()
        def log(self, *a, **k): pass
        def close(self, *a, **k): pass
        def build_artifact_record(self, *a, **k): raise RuntimeError("N/A")
        def update_config(self, *a, **k): pass
        def log_artifact(self, *a, **k): pass
        def finish(self): pass

    total_ts = args.total_timesteps
    config = load_smoke_train_config(args.config)
    config["num_envs"] = args.env_count
    config["env"]["tick_cap"] = args.tick_cap
    config["train"]["total_timesteps"] = total_ts
    effective_batch = max(1, min(total_ts, int(config["train"]["batch_size"])))
    config["train"]["batch_size"] = effective_batch
    config["train"]["minibatch_size"] = max(1, min(int(config["train"]["minibatch_size"]), effective_batch))
    config["train"]["max_minibatch_size"] = max(int(config["train"]["minibatch_size"]), min(int(config["train"]["max_minibatch_size"]), effective_batch))
    config["train"]["bptt_horizon"] = max(1, min(int(config["train"]["bptt_horizon"]), effective_batch))
    config["train"]["checkpoint_interval"] = 1_000_000
    config.setdefault("logging", {})["dashboard"] = False

    device = config["train"]["device"]
    print(f"G1.3 — Training-dynamics baseline")
    print(f"  Config: {args.config}")
    print(f"  Device: {device}, Envs: {args.env_count}, Tick cap: {args.tick_cap}")
    print(f"  Total timesteps: {total_ts:,}, Window: {args.window_size:,}")
    if torch.cuda.is_available():
        print(f"  GPU: {torch.cuda.get_device_properties(0).name}")
        torch.cuda.reset_peak_memory_stats()
    print()

    vecenv = make_vecenv(config, backend="subprocess")
    policy = build_policy_from_config(config, vecenv.single_observation_space, vecenv.single_action_space)
    if device == "cuda":
        policy = policy.to("cuda")

    train_config = build_puffer_train_config(config, data_dir=Path("/tmp/fc_g13_baseline"), total_timesteps=total_ts)
    trainer = ConfigurablePuffeRL(
        train_config, vecenv, policy, _NullLogger(),
        dashboard_enabled=False, checkpointing_enabled=False,
        profiling_enabled=False, utilization_enabled=False,
        logging_enabled=True, instrumentation_enabled=False,
    )

    # Tracking
    snapshots: list[WindowSnapshot] = []
    all_returns: list[float] = []
    all_lengths: list[int] = []
    all_waves: list[int] = []
    window_returns: list[float] = []
    window_lengths: list[int] = []
    window_waves: list[int] = []
    errors: list[str] = []
    stable = True
    window_idx = 0
    last_window_step = 0
    started = perf_counter()

    header = f"{'Window':>7s}  {'Step':>12s}  {'SPS':>8s}  {'WinEps':>7s}  {'TotEps':>7s}  {'MeanRet':>10s}  {'MeanLen':>8s}  {'MaxWave':>7s}  {'Stable':>6s}"
    print(header)
    print("-" * len(header))

    try:
        while trainer.global_step < total_ts:
            trainer.evaluate()
            trainer.train()

            # Collect episode stats
            for k, v in trainer.stats.items():
                if k == "episode_return":
                    window_returns.extend(float(x) for x in v)
                    all_returns.extend(float(x) for x in v)
                elif k == "episode_length":
                    window_lengths.extend(int(x) for x in v)
                    all_lengths.extend(int(x) for x in v)
                elif k == "wave_reached":
                    window_waves.extend(int(x) for x in v)
                    all_waves.extend(int(x) for x in v)

            # Check losses
            for k, v in trainer.losses.items():
                if isinstance(v, float) and (math.isnan(v) or math.isinf(v)):
                    errors.append(f"step={trainer.global_step}: {k}={v}")
                    stable = False

            # Window reporting
            current_step = int(trainer.global_step)
            if current_step - last_window_step >= args.window_size:
                elapsed = perf_counter() - started
                sps = current_step / elapsed if elapsed > 0 else 0
                n_win = len(window_returns)
                n_tot = len(all_returns)
                mr = np.mean(window_returns) if window_returns else 0
                ml = np.mean(window_lengths) if window_lengths else 0
                mw = max(window_waves) if window_waves else 0

                snap = WindowSnapshot(
                    window_index=window_idx,
                    global_step=current_step,
                    elapsed_seconds=elapsed,
                    sps=sps,
                    episodes_in_window=n_win,
                    cumulative_episodes=n_tot,
                    mean_return=float(mr),
                    mean_length=float(ml),
                    max_wave=mw,
                    losses=dict(trainer.losses),
                    stable=stable,
                )
                snapshots.append(snap)
                print(f"{window_idx:>7d}  {current_step:>12,d}  {sps:>8.0f}  {n_win:>7d}  {n_tot:>7d}  {mr:>10.4f}  {ml:>8.0f}  {mw:>7d}  {'ok' if stable else 'NO':>6s}")

                window_returns.clear()
                window_lengths.clear()
                window_waves.clear()
                window_idx += 1
                last_window_step = current_step

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

    sps = final_step / elapsed if elapsed > 0 else 0
    vram = torch.cuda.max_memory_allocated() / 1024**2 if device == "cuda" else 0

    print(f"\n{'=' * 80}")
    print(f"G1.3 RESULTS — Training-dynamics baseline")
    print(f"{'=' * 80}")
    print(f"Total steps: {final_step:,} / {total_ts:,}")
    print(f"Elapsed: {elapsed:.1f}s, SPS: {sps:.0f}")
    print(f"Total episodes: {len(all_returns)}")
    if all_returns:
        print(f"Episode return: mean={np.mean(all_returns):.4f}, min={min(all_returns):.4f}, max={max(all_returns):.4f}")
    if all_lengths:
        print(f"Episode length: mean={np.mean(all_lengths):.0f}, min={min(all_lengths)}, max={max(all_lengths)}")
    if all_waves:
        print(f"Wave reached: mean={np.mean(all_waves):.1f}, max={max(all_waves)}")
    if device == "cuda":
        print(f"VRAM peak: {vram:.0f} MB")
    print(f"Stable: {'YES' if stable else 'NO'}")
    if errors:
        print(f"Errors: {errors[:5]}")

    # Trend analysis
    if len(snapshots) >= 2:
        first_q = snapshots[:len(snapshots)//4]
        last_q = snapshots[-len(snapshots)//4:]
        if first_q and last_q:
            early_ret = np.mean([s.mean_return for s in first_q if s.episodes_in_window > 0] or [0])
            late_ret = np.mean([s.mean_return for s in last_q if s.episodes_in_window > 0] or [0])
            early_len = np.mean([s.mean_length for s in first_q if s.episodes_in_window > 0] or [0])
            late_len = np.mean([s.mean_length for s in last_q if s.episodes_in_window > 0] or [0])
            print(f"\nTrend (first 25% vs last 25% of windows):")
            print(f"  Return: {early_ret:.4f} → {late_ret:.4f}")
            print(f"  Length: {early_len:.0f} → {late_len:.0f}")
    print(f"{'=' * 80}")

    report = {
        "created_at": datetime.now(UTC).isoformat(),
        "config_path": str(args.config),
        "device": device,
        "tick_cap": args.tick_cap,
        "total_timesteps_target": total_ts,
        "total_timesteps_actual": final_step,
        "env_count": args.env_count,
        "elapsed_seconds": elapsed,
        "sps": sps,
        "vram_peak_mb": vram,
        "stable": stable,
        "total_episodes": len(all_returns),
        "all_returns": all_returns,
        "all_lengths": all_lengths,
        "all_waves": all_waves,
        "errors": errors,
        "snapshots": [asdict(s) for s in snapshots],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    print(f"\nResults written to: {args.output}")


if __name__ == "__main__":
    main()
