from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
import json
import statistics
import subprocess
import sys
from pathlib import Path
from typing import Any

from fight_caves_rl.benchmarks.common import build_benchmark_context
from fight_caves_rl.defaults import (
    DEFAULT_ENV_BENCHMARK_CONFIG_PATH,
    DEFAULT_TRAIN_BENCHMARK_CONFIG_PATH,
)
from fight_caves_rl.puffer.factory import load_smoke_train_config
from fight_caves_rl.utils.paths import repo_root, runtime_subdir

ENV_SPS_TARGET = 1_000_000.0
TRAINING_SPS_TARGET = 500_000.0


@dataclass(frozen=True)
class Phase9RunRecord:
    run_kind: str
    env_count: int
    repeat: int
    output_path: str
    command: tuple[str, ...]

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Run the Phase 9.1 post-pivot baseline packet for the default V2 path "
            "and compute current SPS gaps."
        )
    )
    parser.add_argument(
        "--env-config",
        type=Path,
        default=DEFAULT_ENV_BENCHMARK_CONFIG_PATH,
    )
    parser.add_argument(
        "--train-config",
        type=Path,
        default=DEFAULT_TRAIN_BENCHMARK_CONFIG_PATH,
    )
    parser.add_argument("--env-counts", type=str, default="4,16,64")
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--env-rounds", type=int, default=None)
    parser.add_argument("--train-total-timesteps", type=int, default=None)
    parser.add_argument("--train-logging-modes", type=str, default="disabled,standard")
    parser.add_argument("--train-runner-mode", type=str, default="smoke_subprocess_v1")
    parser.add_argument("--output-dir", type=Path, default=None)
    parser.add_argument("--output", type=Path, default=None)
    args = parser.parse_args()

    env_counts = parse_env_counts(args.env_counts)
    if args.repeats <= 0:
        raise ValueError("--repeats must be > 0.")

    root = repo_root()
    output_dir = (
        args.output_dir.resolve()
        if args.output_dir is not None
        else runtime_subdir(
            "artifacts",
            "benchmark",
            f"phase9_pr91_baseline_{timestamp_id()}",
        )
    )
    output_dir.mkdir(parents=True, exist_ok=True)
    raw_dir = output_dir / "raw"
    env_dir = raw_dir / "env"
    train_dir = raw_dir / "train"
    env_dir.mkdir(parents=True, exist_ok=True)
    train_dir.mkdir(parents=True, exist_ok=True)

    env_config = (root / args.env_config).resolve() if not args.env_config.is_absolute() else args.env_config.resolve()
    train_config = (root / args.train_config).resolve() if not args.train_config.is_absolute() else args.train_config.resolve()
    if not env_config.is_file():
        raise FileNotFoundError(f"Env benchmark config not found: {env_config}")
    if not train_config.is_file():
        raise FileNotFoundError(f"Train benchmark config not found: {train_config}")

    env_rows: list[dict[str, Any]] = []
    train_rows: list[dict[str, Any]] = []
    run_records: list[Phase9RunRecord] = []

    for env_count in env_counts:
        for repeat in range(1, args.repeats + 1):
            env_output = env_dir / f"env_count_{env_count:03d}_rep_{repeat:02d}.json"
            env_command = [
                sys.executable,
                str(root / "scripts" / "benchmark_env.py"),
                "--config",
                str(env_config),
                "--mode",
                "vecenv",
                "--env-count",
                str(env_count),
            ]
            if args.env_rounds is not None:
                env_command.extend(("--rounds", str(args.env_rounds)))
            env_command.extend(("--output", str(env_output)))
            run_command(env_command, cwd=root)
            env_payload = json.loads(env_output.read_text(encoding="utf-8"))
            env_rows.append(
                {
                    "env_count": env_count,
                    "repeat": repeat,
                    "output_path": str(env_output),
                    "env_steps_per_second": float(env_payload["env_steps_per_second"]),
                    "tick_rounds_per_second": float(env_payload["tick_rounds_per_second"]),
                    "runner_stage_seconds": dict(env_payload.get("runner_stage_seconds", {})),
                    "hot_path_bucket_totals": dict(env_payload.get("hot_path_bucket_totals", {})),
                    "memory_profile": dict(env_payload.get("memory_profile", {})),
                    "vecenv_topology": dict(env_payload.get("vecenv_topology", {})),
                }
            )
            run_records.append(
                Phase9RunRecord(
                    run_kind="env",
                    env_count=env_count,
                    repeat=repeat,
                    output_path=str(env_output),
                    command=tuple(env_command),
                )
            )

            train_output = train_dir / f"train_count_{env_count:03d}_rep_{repeat:02d}.json"
            train_command = [
                sys.executable,
                str(root / "scripts" / "benchmark_train.py"),
                "--config",
                str(train_config),
                "--env-count",
                str(env_count),
                "--logging-modes",
                str(args.train_logging_modes),
                "--runner-mode",
                str(args.train_runner_mode),
                "--output",
                str(train_output),
            ]
            if args.train_total_timesteps is not None:
                train_command.extend(("--total-timesteps", str(args.train_total_timesteps)))
            run_command(train_command, cwd=root)
            train_payload = json.loads(train_output.read_text(encoding="utf-8"))
            for measurement in train_payload["measurements"]:
                train_rows.append(
                    {
                        "env_count": env_count,
                        "repeat": repeat,
                        "logging_mode": str(measurement["logging_mode"]),
                        "output_path": str(train_output),
                        "production_env_steps_per_second": float(
                            measurement["production_env_steps_per_second"]
                        ),
                        "wall_clock_env_steps_per_second": float(
                            measurement["wall_clock_env_steps_per_second"]
                        ),
                        "runner_stage_seconds": dict(measurement.get("runner_stage_seconds", {})),
                        "trainer_bucket_totals": dict(measurement.get("trainer_bucket_totals", {})),
                        "memory_profile": dict(measurement.get("memory_profile", {})),
                        "vecenv_topology": dict(measurement.get("vecenv_topology", {})),
                    }
                )
            run_records.append(
                Phase9RunRecord(
                    run_kind="train",
                    env_count=env_count,
                    repeat=repeat,
                    output_path=str(train_output),
                    command=tuple(train_command),
                )
            )

    env_summary = summarize_env_rows(env_rows)
    train_summary = summarize_train_rows(train_rows)

    env_peak_median = max(
        (row["median_env_steps_per_second"] for row in env_summary),
        default=0.0,
    )
    train_peak_median = max(
        (
            row["median_production_env_steps_per_second"]
            for row in train_summary
            if row["logging_mode"] == "disabled"
        ),
        default=0.0,
    )

    env_gap = build_gap_summary(env_peak_median, ENV_SPS_TARGET)
    train_gap = build_gap_summary(train_peak_median, TRAINING_SPS_TARGET)

    benchmark_context = build_context_from_train_config(train_config)

    packet = {
        "schema_id": "post_pivot_phase9_baseline_packet_v1",
        "created_at": datetime.now(UTC).isoformat(),
        "phase_id": "phase9",
        "pr_id": "pr9_1",
        "methodology": {
            "env_benchmark_config": str(env_config),
            "train_benchmark_config": str(train_config),
            "env_mode": "vecenv",
            "env_counts": env_counts,
            "repeats": int(args.repeats),
            "env_rounds_override": args.env_rounds,
            "train_total_timesteps_override": args.train_total_timesteps,
            "train_logging_modes": [mode.strip() for mode in args.train_logging_modes.split(",") if mode.strip()],
            "train_runner_mode": str(args.train_runner_mode),
        },
        "targets": {
            "env_only_sps_target": ENV_SPS_TARGET,
            "training_sps_target": TRAINING_SPS_TARGET,
        },
        "summary": {
            "env_only_peak_median_sps": env_peak_median,
            "training_peak_median_sps_disabled": train_peak_median,
            "env_gap": env_gap,
            "training_gap": train_gap,
        },
        "env_summary_by_count": env_summary,
        "train_summary_by_count_and_mode": train_summary,
        "run_records": [record.to_dict() for record in run_records],
        "context": benchmark_context,
    }

    output = (
        args.output.resolve()
        if args.output is not None
        else output_dir / "phase9_pr91_baseline_packet.json"
    )
    output.write_text(json.dumps(packet, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Wrote Phase 9 baseline packet: {output}")


def parse_env_counts(raw: str) -> list[int]:
    values = [token.strip() for token in raw.split(",") if token.strip()]
    if not values:
        raise ValueError("--env-counts must contain at least one integer.")
    parsed = [int(token) for token in values]
    if any(value <= 0 for value in parsed):
        raise ValueError("--env-counts values must be > 0.")
    return parsed


def run_command(command: list[str], *, cwd: Path) -> None:
    result = subprocess.run(
        command,
        cwd=str(cwd),
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(
            "Command failed.\n"
            f"command: {' '.join(command)}\n"
            f"returncode: {result.returncode}\n"
            f"stdout:\n{result.stdout}\n"
            f"stderr:\n{result.stderr}"
        )


def summarize_env_rows(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    grouped: dict[int, list[float]] = {}
    for row in rows:
        grouped.setdefault(int(row["env_count"]), []).append(float(row["env_steps_per_second"]))
    summary: list[dict[str, Any]] = []
    for env_count in sorted(grouped):
        samples = grouped[env_count]
        summary.append(
            {
                "env_count": env_count,
                "sample_count": len(samples),
                "min_env_steps_per_second": min(samples),
                "max_env_steps_per_second": max(samples),
                "mean_env_steps_per_second": statistics.fmean(samples),
                "median_env_steps_per_second": statistics.median(samples),
            }
        )
    return summary


def summarize_train_rows(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    grouped: dict[tuple[int, str], list[float]] = {}
    for row in rows:
        key = (int(row["env_count"]), str(row["logging_mode"]))
        grouped.setdefault(key, []).append(float(row["production_env_steps_per_second"]))
    summary: list[dict[str, Any]] = []
    for env_count, logging_mode in sorted(grouped):
        samples = grouped[(env_count, logging_mode)]
        summary.append(
            {
                "env_count": env_count,
                "logging_mode": logging_mode,
                "sample_count": len(samples),
                "min_production_env_steps_per_second": min(samples),
                "max_production_env_steps_per_second": max(samples),
                "mean_production_env_steps_per_second": statistics.fmean(samples),
                "median_production_env_steps_per_second": statistics.median(samples),
            }
        )
    return summary


def build_gap_summary(measured: float, target: float) -> dict[str, Any]:
    ratio = 0.0 if target <= 0 else measured / target
    return {
        "measured_sps": measured,
        "target_sps": target,
        "absolute_gap_sps": target - measured,
        "measured_to_target_ratio": ratio,
        "meets_target": measured >= target,
    }


def build_context_from_train_config(train_config: Path) -> dict[str, Any]:
    config = load_smoke_train_config(train_config)
    context = build_benchmark_context(
        env_count=int(config["num_envs"]),
        logging_mode="phase9_baseline_packet",
        replay_mode="disabled",
        dashboard_mode="disabled",
        reward_config_id=str(config["reward_config"]),
        curriculum_config_id=str(config["curriculum_config"]),
    )
    return context.to_dict()


def timestamp_id() -> str:
    return datetime.now(UTC).strftime("%Y%m%dT%H%M%SZ")


if __name__ == "__main__":
    main()
