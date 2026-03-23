from __future__ import annotations

import argparse
import sys

from fight_caves_rl.fixtures.golden_fixtures import fixture_drift, generate_all_fixtures


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate or verify the checked-in PR1 golden fixtures.")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    if args.check:
        drift = fixture_drift()
        if drift:
            for message in drift:
                print(message, file=sys.stderr)
            raise SystemExit(1)
        return

    for path in generate_all_fixtures():
        print(path)


if __name__ == "__main__":
    main()
