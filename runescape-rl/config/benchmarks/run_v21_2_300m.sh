#!/bin/bash
set -euo pipefail

SRC_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
CFG="$SRC_DIR/config/benchmarks/fight_caves_v21_2_300m.ini"

echo "[benchmark] Using config: $CFG"
grep -n '^total_timesteps' "$CFG"

cd "$SRC_DIR"
CONFIG_PATH="$CFG" FORCE_BACKEND_REBUILD=1 bash train.sh "$@"
