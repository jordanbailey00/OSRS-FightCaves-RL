from __future__ import annotations

import argparse
import json
from pathlib import Path

from fight_caves_rl.fixtures.reset_repro import collect_reset_repro


def main() -> None:
    parser = argparse.ArgumentParser(description="Collect semantic reset reproducibility payloads as JSON.")
    parser.add_argument("--seed", type=int, required=True)
    parser.add_argument("--start-wave", type=int, default=1)
    parser.add_argument("--ammo", type=int, default=1000)
    parser.add_argument("--prayer-potions", type=int, default=8)
    parser.add_argument("--sharks", type=int, default=20)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    payload = collect_reset_repro(
        seed=args.seed,
        start_wave=args.start_wave,
        ammo=args.ammo,
        prayer_potions=args.prayer_potions,
        sharks=args.sharks,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
if __name__ == "__main__":
    main()
