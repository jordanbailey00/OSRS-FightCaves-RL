from __future__ import annotations

import argparse
from pathlib import Path

from fight_caves_rl.native_runtime import NativeRuntimeBuildConfig, build_native_runtime


def main() -> int:
    parser = argparse.ArgumentParser(description="Build the PR2 native runtime descriptor library.")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=None,
        help="Optional output directory for the built shared library.",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Rebuild even if the current build fingerprint matches.",
    )
    args = parser.parse_args()
    library_path = build_native_runtime(
        NativeRuntimeBuildConfig(
            output_dir=args.output_dir,
            force_rebuild=bool(args.force),
        )
    )
    print(library_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
