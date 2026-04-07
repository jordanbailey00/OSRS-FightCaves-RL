#!/bin/bash
# Local-only v18.3 sanity-control run.
# Plain train, not sweep. Uses the invalid-action fix with the gentle v18.3
# continuation recipe for 500M timesteps.

set -euo pipefail

SRC_DIR="$(cd "$(dirname "$0")" && pwd)"
DEFAULT_CKPT="$SRC_DIR/../pufferlib_4/checkpoints/fight_caves/xm6i52ta/0000005977931776.bin"

export CONFIG_PATH="${CONFIG_PATH:-$SRC_DIR/config/fight_caves_v18_3_control.ini}"
export LOAD_MODEL_PATH="${LOAD_MODEL_PATH:-$DEFAULT_CKPT}"

bash "$SRC_DIR/train.sh" "$@"
