from __future__ import annotations

import argparse
import sys

from fight_caves_rl.contracts.frozen_artifacts import contract_artifact_drift, write_contract_artifacts


def main() -> None:
    parser = argparse.ArgumentParser(description="Export or verify the frozen PR1 contract artifacts.")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    if args.check:
        drift = contract_artifact_drift()
        if drift:
            for message in drift:
                print(message, file=sys.stderr)
            raise SystemExit(1)
        return

    for path in write_contract_artifacts():
        print(path)


if __name__ == "__main__":
    main()
