#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/workspace-env.sh"

export SOURCE_ROOT="$(cd "$script_dir/.." && pwd)"
export RL_REPO="$SOURCE_ROOT/src/rl"
export FIGHT_CAVES_RL_REPO="$SOURCE_ROOT/src/headless-env"
export RSPS_REPO="$SOURCE_ROOT/src/rsps"
export WANDB_DIR="$FIGHT_CAVES_RUNTIME_ROOT/artifacts/training-env/rl/wandb"
export WANDB_DATA_DIR="$FIGHT_CAVES_RUNTIME_ROOT/artifacts/training-env/rl/wandb-data"
export WANDB_CACHE_DIR="$FIGHT_CAVES_RUNTIME_ROOT/artifacts/training-env/rl/wandb-cache"

mkdir -p \
  "$FIGHT_CAVES_RUNTIME_ROOT/artifacts/training-env/rl" \
  "$FIGHT_CAVES_RUNTIME_ROOT/builds" \
  "$FIGHT_CAVES_RUNTIME_ROOT/checkpoints" \
  "$FIGHT_CAVES_RUNTIME_ROOT/eval" \
  "$FIGHT_CAVES_RUNTIME_ROOT/logs" \
  "$FIGHT_CAVES_RUNTIME_ROOT/replays" \
  "$FIGHT_CAVES_RUNTIME_ROOT/reports" \
  "$FIGHT_CAVES_RUNTIME_ROOT/tmp"

echo "Bootstrap ready"
echo "SOURCE_ROOT=$SOURCE_ROOT"
echo "RUNTIME_ROOT=$FIGHT_CAVES_RUNTIME_ROOT"
