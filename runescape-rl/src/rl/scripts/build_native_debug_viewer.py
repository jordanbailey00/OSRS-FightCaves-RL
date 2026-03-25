from __future__ import annotations

import argparse
from pathlib import Path

from fight_caves_rl.native_runtime import NativeViewerBuildConfig, build_native_debug_viewer


def main() -> int:
    parser = argparse.ArgumentParser(description="Build the DV1 native debug viewer executable.")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=None,
        help="Optional output directory for the built viewer executable.",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Rebuild even if the current build fingerprint matches.",
    )
    args = parser.parse_args()
    viewer_path = build_native_debug_viewer(
        NativeViewerBuildConfig(
            output_dir=args.output_dir,
            force_rebuild=bool(args.force),
        )
    )
    print(viewer_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
