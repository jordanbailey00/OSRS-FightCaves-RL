#!/bin/bash
# Train Fight Caves RL agent with wandb logging.
# Usage: ./train.sh [--no-wandb]

SRC_DIR="$(cd "$(dirname "$0")" && pwd)"
PUFFER_DIR="$(cd "$SRC_DIR/../pufferlib_4" && pwd)"

# Sync config to where PufferLib reads it
cp "$SRC_DIR/config/fight_caves.ini" "$PUFFER_DIR/config/fight_caves.ini"
echo "[train.sh] Synced config to pufferlib"

# Ensure runtime dirs exist
mkdir -p "$PUFFER_DIR/checkpoints" "$PUFFER_DIR/logs" "$PUFFER_DIR/wandb"

# Collision asset path
export FC_COLLISION_PATH="$SRC_DIR/training-env/assets/fightcaves.collision"

# CUDA + CuDNN
export PATH=/usr/local/cuda/bin:$PATH
CUDNN_LIB="$(python3 -c "import nvidia.cudnn; import os; print(os.path.join(os.path.dirname(nvidia.cudnn.__file__), 'lib'))" 2>/dev/null)"
if [ -n "$CUDNN_LIB" ]; then
    export LD_LIBRARY_PATH="$CUDNN_LIB:$LD_LIBRARY_PATH"
fi

# W&B
export WANDB_DIR="$PUFFER_DIR/wandb"
export PUFFERLIB_DIR="$PUFFER_DIR"

cd "$PUFFER_DIR"
source "$SRC_DIR/.venv/bin/activate"

WANDB_FLAG="--wandb"
if [ "$1" = "--no-wandb" ]; then
    WANDB_FLAG=""
fi

python -m pufferlib.pufferl train fight_caves $WANDB_FLAG
