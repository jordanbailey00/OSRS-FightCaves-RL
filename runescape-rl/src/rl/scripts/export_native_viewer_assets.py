from __future__ import annotations

import argparse

from fight_caves_rl.native_runtime.viewer_assets import (
    ensure_native_viewer_asset_bundle,
    generated_viewer_bundle_path,
    generated_viewer_manifest_path,
    generated_viewer_sprites_root,
)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Export the native-owned Fight Caves viewer asset bundle from validated legacy cache inputs."
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Rebuild the generated asset bundle even if the cached fingerprint matches.",
    )
    args = parser.parse_args()

    bundle_path = ensure_native_viewer_asset_bundle(force_rebuild=bool(args.force))
    print(bundle_path)
    print(generated_viewer_manifest_path())
    print(generated_viewer_sprites_root())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
