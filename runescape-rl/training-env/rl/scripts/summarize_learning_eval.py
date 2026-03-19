from __future__ import annotations

import argparse
from datetime import UTC, datetime
import json
from pathlib import Path
import statistics
from typing import Any


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Summarize replay-eval artifacts into a fixed learning-metrics payload "
            "for Phase 9.3/Phase 10 tracking."
        )
    )
    parser.add_argument("--eval-summary", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    eval_summary = load_json(args.eval_summary)
    replay_pack = load_json(Path(str(eval_summary["replay_pack_path"])))
    per_seed = list(eval_summary.get("per_seed", []))
    episodes = list(replay_pack.get("episodes", []))

    if len(per_seed) != len(episodes):
        raise ValueError(
            "Mismatch between eval summary per-seed rows and replay-pack episodes: "
            f"{len(per_seed)} != {len(episodes)}"
        )

    steps_taken = [int(item["steps_taken"]) for item in per_seed]
    terminated = [bool(item["terminated"]) for item in per_seed]
    truncated = [bool(item["truncated"]) for item in per_seed]
    terminal_reasons = [item.get("terminal_reason") for item in per_seed]
    final_waves = [
        int(item["final_semantic_observation"]["wave"]["wave"])
        for item in per_seed
    ]
    max_wave_seen = [max_wave_for_episode(episode) for episode in episodes]

    payload = {
        "schema_id": "learning_eval_summary_v1",
        "created_at": datetime.now(UTC).isoformat(),
        "source_eval_summary": str(args.eval_summary.resolve()),
        "source_replay_pack": str(Path(str(eval_summary["replay_pack_path"])).resolve()),
        "seed_pack": str(eval_summary["seed_pack"]),
        "seed_pack_version": int(eval_summary["seed_pack_version"]),
        "summary_digest": str(eval_summary["summary_digest"]),
        "episode_count": len(per_seed),
        "metrics": {
            "mean_steps": safe_mean(steps_taken),
            "median_steps": safe_median(steps_taken),
            "terminated_rate": safe_mean(terminated),
            "truncated_rate": safe_mean(truncated),
            "player_death_rate": safe_rate(terminal_reasons, "player_death"),
            "cave_complete_rate": safe_rate(terminal_reasons, "cave_complete"),
            "mean_final_wave": safe_mean(final_waves),
            "max_final_wave": max(final_waves) if final_waves else 0,
            "mean_max_wave_seen": safe_mean(max_wave_seen),
            "max_wave_seen": max(max_wave_seen) if max_wave_seen else 0,
        },
        "per_seed": [
            {
                "seed": int(seed_row["seed"]),
                "steps_taken": int(seed_row["steps_taken"]),
                "terminal_reason": seed_row.get("terminal_reason"),
                "terminated": bool(seed_row["terminated"]),
                "truncated": bool(seed_row["truncated"]),
                "final_wave": int(seed_row["final_semantic_observation"]["wave"]["wave"]),
                "max_wave_seen": int(max_wave_seen[index]),
                "trajectory_digest": str(seed_row["trajectory_digest"]),
            }
            for index, seed_row in enumerate(per_seed)
        ],
    }

    args.output.resolve().parent.mkdir(parents=True, exist_ok=True)
    args.output.resolve().write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(f"Wrote learning eval summary: {args.output.resolve()}")


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def safe_mean(values: list[Any]) -> float:
    if not values:
        return 0.0
    numeric = [float(value) for value in values]
    return statistics.fmean(numeric)


def safe_median(values: list[Any]) -> float:
    if not values:
        return 0.0
    numeric = [float(value) for value in values]
    return float(statistics.median(numeric))


def safe_rate(values: list[Any], target: str) -> float:
    if not values:
        return 0.0
    return sum(1 for value in values if value == target) / float(len(values))


def max_wave_for_episode(episode: dict[str, Any]) -> int:
    waves: list[int] = []
    for step in episode.get("steps", []):
        semantic = dict(step.get("semantic_observation", {}))
        wave_payload = dict(semantic.get("wave", {}))
        if "wave" in wave_payload:
            waves.append(int(wave_payload["wave"]))
    final_semantic = dict(episode.get("final_semantic_observation", {}))
    final_wave_payload = dict(final_semantic.get("wave", {}))
    if "wave" in final_wave_payload:
        waves.append(int(final_wave_payload["wave"]))
    return max(waves) if waves else 0


if __name__ == "__main__":
    main()
