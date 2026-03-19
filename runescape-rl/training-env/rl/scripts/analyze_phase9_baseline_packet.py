from __future__ import annotations

import argparse
from collections import defaultdict
from datetime import UTC, datetime
import json
from pathlib import Path
import statistics
from typing import Any


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Analyze a Phase 9.1 baseline packet and emit a ranked Phase 9.2 "
            "performance decomposition artifact."
        )
    )
    parser.add_argument("--packet", type=Path, required=True)
    parser.add_argument("--ceiling-report", type=Path, default=None)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    packet = load_json(args.packet)
    packet_dir = args.packet.resolve().parent
    raw_env_dir = packet_dir / "raw" / "env"
    raw_train_dir = packet_dir / "raw" / "train"
    if not raw_env_dir.is_dir() or not raw_train_dir.is_dir():
        raise FileNotFoundError(
            f"Expected raw benchmark directories under {packet_dir}: "
            f"{raw_env_dir} and {raw_train_dir}"
        )

    env_rows = [load_json(path) for path in sorted(raw_env_dir.glob("*.json"))]
    train_payloads = [load_json(path) for path in sorted(raw_train_dir.glob("*.json"))]
    train_rows = []
    for payload in train_payloads:
        measurements = payload.get("measurements", [])
        for measurement in measurements:
            train_rows.append(measurement)

    disabled_rows = [row for row in train_rows if str(row.get("logging_mode")) == "disabled"]
    stage_summary_by_env = build_runner_stage_summary(disabled_rows)
    trainer_bucket_rankings = {
        "env_16": build_trainer_bucket_ranking(disabled_rows, env_count=16),
        "env_64": build_trainer_bucket_ranking(disabled_rows, env_count=64),
        "env_16_64_aggregate": build_trainer_bucket_ranking(disabled_rows, env_count=None, include_envs={16, 64}),
    }
    env_hot_path_ranking = {
        "env_64": build_env_hot_path_ranking(env_rows, env_count=64),
    }

    env_peak_median = float(packet["summary"]["env_only_peak_median_sps"])
    train_peak_median = float(packet["summary"]["training_peak_median_sps_disabled"])
    train_to_env_ratio = 0.0 if env_peak_median <= 0 else train_peak_median / env_peak_median

    ceiling_payload = None
    if args.ceiling_report is not None:
        ceiling_payload = load_json(args.ceiling_report)

    artifact = {
        "schema_id": "post_pivot_phase9_decomposition_v1",
        "created_at": datetime.now(UTC).isoformat(),
        "source_phase9_packet": str(args.packet.resolve()),
        "headline": {
            "env_only_peak_median_sps": env_peak_median,
            "training_peak_median_sps_disabled": train_peak_median,
            "training_to_env_peak_ratio": train_to_env_ratio,
            "env_to_training_slowdown_factor": (0.0 if train_peak_median <= 0 else env_peak_median / train_peak_median),
        },
        "runner_stage_summary_disabled_by_env_count": stage_summary_by_env,
        "trainer_bucket_rankings_disabled": trainer_bucket_rankings,
        "env_hot_path_rankings": env_hot_path_ranking,
        "ceiling_report": (
            None
            if ceiling_payload is None
            else {
                "path": str(args.ceiling_report.resolve()),
                "metric_contract_id": ceiling_payload.get("metric_contract_id"),
                "config_id": ceiling_payload.get("config_id"),
                "measurements": [
                    {
                        "env_count": int(entry["env_count"]),
                        "diagnostic_env_steps_per_second": float(entry["diagnostic_env_steps_per_second"]),
                        "evaluate_seconds": float(entry["evaluate_seconds"]),
                        "train_seconds": float(entry["train_seconds"]),
                        "final_evaluate_seconds": float(entry["final_evaluate_seconds"]),
                    }
                    for entry in ceiling_payload.get("measurements", [])
                ],
            }
        ),
    }

    args.output.resolve().parent.mkdir(parents=True, exist_ok=True)
    args.output.resolve().write_text(
        json.dumps(artifact, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(f"Wrote Phase 9.2 decomposition artifact: {args.output.resolve()}")


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def build_runner_stage_summary(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    by_env: dict[int, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        by_env[int(row["env_count"])].append(row)

    summary: list[dict[str, Any]] = []
    for env_count in sorted(by_env):
        stage_values: dict[str, list[float]] = defaultdict(list)
        for row in by_env[env_count]:
            stage_seconds = dict(row.get("runner_stage_seconds", {}))
            for key, value in stage_seconds.items():
                stage_values[str(key)].append(float(value))

        stage_means = {
            stage: statistics.fmean(values) for stage, values in stage_values.items() if values
        }
        total = sum(stage_means.values())
        ranked = sorted(stage_means.items(), key=lambda item: item[1], reverse=True)
        summary.append(
            {
                "env_count": env_count,
                "sample_count": len(by_env[env_count]),
                "mean_total_stage_seconds": total,
                "stage_means_ranked": [
                    {
                        "stage": stage,
                        "mean_seconds": mean_seconds,
                        "share_of_total_percent": (0.0 if total <= 0 else (mean_seconds / total) * 100.0),
                    }
                    for stage, mean_seconds in ranked
                ],
            }
        )
    return summary


def build_trainer_bucket_ranking(
    rows: list[dict[str, Any]],
    *,
    env_count: int | None,
    include_envs: set[int] | None = None,
) -> dict[str, Any]:
    selected: list[dict[str, Any]] = []
    for row in rows:
        row_env = int(row["env_count"])
        if env_count is not None and row_env != env_count:
            continue
        if include_envs is not None and row_env not in include_envs:
            continue
        selected.append(row)

    bucket_values: dict[str, list[float]] = defaultdict(list)
    for row in selected:
        buckets = dict(row.get("trainer_bucket_totals", {}))
        for key, value in buckets.items():
            bucket_values[str(key)].append(float(value["seconds"]))

    ranked = sorted(
        ((statistics.fmean(values), bucket) for bucket, values in bucket_values.items() if values),
        reverse=True,
    )
    total = sum(mean_seconds for mean_seconds, _ in ranked)
    return {
        "sample_count": len(selected),
        "ranked_means": [
            {
                "bucket": bucket,
                "mean_seconds": mean_seconds,
                "share_of_total_percent": (0.0 if total <= 0 else (mean_seconds / total) * 100.0),
            }
            for mean_seconds, bucket in ranked
        ],
    }


def build_env_hot_path_ranking(rows: list[dict[str, Any]], *, env_count: int) -> dict[str, Any]:
    selected = [row for row in rows if int(row["env_count"]) == env_count]
    bucket_values: dict[str, list[float]] = defaultdict(list)
    for row in selected:
        buckets = dict(row.get("hot_path_bucket_totals", {}))
        for key, value in buckets.items():
            seconds = float(value["seconds"]) if isinstance(value, dict) else float(value)
            bucket_values[str(key)].append(seconds)

    ranked = sorted(
        ((statistics.fmean(values), bucket) for bucket, values in bucket_values.items() if values),
        reverse=True,
    )
    total = sum(mean_seconds for mean_seconds, _ in ranked)
    return {
        "sample_count": len(selected),
        "ranked_means": [
            {
                "bucket": bucket,
                "mean_seconds": mean_seconds,
                "share_of_total_percent": (0.0 if total <= 0 else (mean_seconds / total) * 100.0),
            }
            for mean_seconds, bucket in ranked
        ],
    }


if __name__ == "__main__":
    main()
