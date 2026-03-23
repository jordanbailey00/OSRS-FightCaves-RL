from __future__ import annotations

import argparse
import json
from pathlib import Path

from fight_caves_rl.replay.mechanics_parity import collect_named_parity_scenario_trace


def main() -> None:
    parser = argparse.ArgumentParser(description="Collect a named oracle/headless parity scenario trace as JSON.")
    parser.add_argument("--mode", choices=("oracle", "headless"), required=True)
    parser.add_argument("--scenario-id", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    payload = collect_named_parity_scenario_trace(args.mode, args.scenario_id)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
