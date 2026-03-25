from __future__ import annotations

import argparse
from pathlib import Path

from fight_caves_rl.replay.native_viewer_replay import (
    build_native_viewer_replay_from_replay_pack,
    build_native_viewer_replay_from_trace_pack,
    write_native_viewer_replay,
)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Export a backend-driven native viewer replay file from a trace pack or replay pack."
    )
    source_group = parser.add_mutually_exclusive_group(required=True)
    source_group.add_argument("--trace-pack", type=str, default=None)
    source_group.add_argument("--replay-pack", type=Path, default=None)
    parser.add_argument("--episode-index", type=int, default=0)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    if args.trace_pack is not None:
        payload = build_native_viewer_replay_from_trace_pack(args.trace_pack)
    else:
        payload = build_native_viewer_replay_from_replay_pack(
            args.replay_pack,
            episode_index=int(args.episode_index),
        )
    output = write_native_viewer_replay(args.output, payload)
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
