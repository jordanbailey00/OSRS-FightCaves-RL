#!/bin/bash
# Train Fight Caves RL agent with wandb logging.
# Usage: ./train.sh [--no-wandb]
# Optional:
#   LOAD_MODEL_PATH=/path/to/checkpoint.bin ./train.sh
#   LOAD_MODEL_PATH=latest ./train.sh

SRC_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SRC_DIR/.." && pwd)"
PUFFER_DIR="${PUFFER_DIR:-$ROOT_DIR/pufferlib_4}"
CONFIG_PATH="${CONFIG_PATH:-$SRC_DIR/config/fight_caves.ini}"

if [ ! -d "$PUFFER_DIR" ]; then
    echo "Error: PufferLib not found at $PUFFER_DIR"
    exit 1
fi

if [ ! -f "$CONFIG_PATH" ]; then
    echo "Error: config not found at $CONFIG_PATH"
    exit 1
fi

# Sync config to where PufferLib reads it
cp "$CONFIG_PATH" "$PUFFER_DIR/config/fight_caves.ini"
echo "[train.sh] Synced config from $CONFIG_PATH to $PUFFER_DIR/config/fight_caves.ini"

mkdir -p \
    "$PUFFER_DIR/checkpoints/fight_caves" \
    "$PUFFER_DIR/logs/fight_caves" \
    "$PUFFER_DIR/wandb"

cd "$PUFFER_DIR"
source "$SRC_DIR/.venv/bin/activate"
export PUFFERLIB_DIR="$PUFFER_DIR"
export PATH=/usr/local/cuda/bin:$PATH
export FC_COLLISION_PATH="$SRC_DIR/training-env/assets/fightcaves.collision"
export WANDB_DIR="$PUFFER_DIR/wandb"
export WANDB_CACHE_DIR="$PUFFER_DIR/wandb/.cache"
export WANDB_CONFIG_DIR="$PUFFER_DIR/wandb/.config"
export WANDB_DATA_DIR="$PUFFER_DIR/wandb/.data"
CUDNN_LIB="$(python -c "import nvidia.cudnn, os; print(os.path.join(nvidia.cudnn.__path__[0], 'lib'))" 2>/dev/null || true)"
if [ -n "$CUDNN_LIB" ]; then
    export LD_LIBRARY_PATH="$CUDNN_LIB${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
fi
export CC="${CC:-gcc}"
export CXX="${CXX:-g++}"

EXT_SUFFIX="$(python -c "import sysconfig; print(sysconfig.get_config_var('EXT_SUFFIX') or '')")"
BACKEND_SO="$PUFFER_DIR/pufferlib/_C${EXT_SUFFIX}"
BACKEND_REBUILD_REASON=""
if [ ! -f "$BACKEND_SO" ]; then
    BACKEND_REBUILD_REASON="missing backend"
elif find "$SRC_DIR/training-env" "$PUFFER_DIR/src/vecenv.h" -type f -newer "$BACKEND_SO" -print -quit | grep -q .; then
    BACKEND_REBUILD_REASON="backend sources newer than compiled extension"
elif [ "${FORCE_BACKEND_REBUILD:-0}" = "1" ]; then
    BACKEND_REBUILD_REASON="FORCE_BACKEND_REBUILD=1"
fi

if [ -n "$BACKEND_REBUILD_REASON" ]; then
    echo "[train.sh] Rebuilding fight_caves backend: $BACKEND_REBUILD_REASON"
    bash "$PUFFER_DIR/build.sh" fight_caves
fi

WANDB_FLAG="--wandb"
if [ "$1" = "--no-wandb" ]; then
    WANDB_FLAG=""
fi

CMD=(python -m pufferlib.pufferl train fight_caves)
if [ -n "$WANDB_FLAG" ]; then
    CMD+=("$WANDB_FLAG")
fi
if [ -n "${LOAD_MODEL_PATH:-}" ]; then
    echo "[train.sh] Using warm-start checkpoint: $LOAD_MODEL_PATH"
    CMD+=(--load-model-path "$LOAD_MODEL_PATH")
fi

"${CMD[@]}"
