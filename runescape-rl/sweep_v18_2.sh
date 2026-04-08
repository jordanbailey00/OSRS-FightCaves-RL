#!/bin/bash
# Narrow continuation sweep for Fight Caves v18.2.
#
# Default behavior:
# - warm-start from a strong v18.1 checkpoint
# - run a narrow sweep over LR / clip / entropy / tiny late-wave curriculum pct
# - keep reward shaping and obs/backend fixed to the v18/v18.1 recipe
#
# Optional overrides:
#   LOAD_MODEL_PATH=/path/to/checkpoint.bin bash sweep_v18_2.sh
#   SWEEP_GPUS=4 TRAIN_GPUS=1 bash sweep_v18_2.sh
#   WANDB_GROUP=my_group TAG=my_tag bash sweep_v18_2.sh
#   bash sweep_v18_2.sh --no-wandb

SRC_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SRC_DIR/.." && pwd)"
PUFFER_DIR="${PUFFER_DIR:-$ROOT_DIR/pufferlib_4}"
TRAINING_BUILD_SH="$SRC_DIR/training-env/build.sh"

if [ ! -d "$PUFFER_DIR" ]; then
    echo "Error: PufferLib not found at $PUFFER_DIR"
    exit 1
fi

if [ ! -f "$TRAINING_BUILD_SH" ]; then
    echo "Error: local training backend build script not found at $TRAINING_BUILD_SH"
    exit 1
fi

DEFAULT_LOAD_MODEL_PATH="$PUFFER_DIR/checkpoints/fight_caves/xm6i52ta/0000002203058176.bin"
LOAD_MODEL_PATH="${LOAD_MODEL_PATH:-$DEFAULT_LOAD_MODEL_PATH}"
SWEEP_GPUS="${SWEEP_GPUS:-1}"
TRAIN_GPUS="${TRAIN_GPUS:-1}"
WANDB_GROUP="${WANDB_GROUP:-v18_2_sweep}"
TAG="${TAG:-v18.2}"

if [ ! -f "$LOAD_MODEL_PATH" ]; then
    echo "Error: warm-start checkpoint not found at $LOAD_MODEL_PATH"
    exit 1
fi

# Sync config to where PufferLib reads it
cp "$SRC_DIR/config/fight_caves.ini" "$PUFFER_DIR/config/fight_caves.ini"
echo "[sweep_v18_2.sh] Synced config to $PUFFER_DIR/config/fight_caves.ini"

mkdir -p \
    "$PUFFER_DIR/checkpoints/fight_caves" \
    "$PUFFER_DIR/logs/fight_caves" \
    "$PUFFER_DIR/wandb"

cd "$PUFFER_DIR"
source "$SRC_DIR/.venv/bin/activate"
export PUFFERLIB_DIR="$PUFFER_DIR"
export PATH=/usr/local/cuda/bin:$PATH
export FC_COLLISION_PATH="$SRC_DIR/fc-core/assets/fightcaves.collision"
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
elif ! python -c "import importlib.util, sys; spec=importlib.util.spec_from_file_location('pufferlib._C', sys.argv[1]); mod=importlib.util.module_from_spec(spec); spec.loader.exec_module(mod); ok=(getattr(mod, 'env_name', None) == 'fight_caves' and hasattr(mod, 'create_pufferl')); raise SystemExit(0 if ok else 1)" "$BACKEND_SO"; then
    BACKEND_REBUILD_REASON="backend missing compiled trainer API"
elif find "$SRC_DIR/fc-core" "$SRC_DIR/training-env" "$PUFFER_DIR/src/vecenv.h" -type f -newer "$BACKEND_SO" -print -quit | grep -q .; then
    BACKEND_REBUILD_REASON="backend sources newer than compiled extension"
elif [ "${FORCE_BACKEND_REBUILD:-0}" = "1" ]; then
    BACKEND_REBUILD_REASON="FORCE_BACKEND_REBUILD=1"
fi

if [ -n "$BACKEND_REBUILD_REASON" ]; then
    echo "[sweep_v18_2.sh] Rebuilding fight_caves backend: $BACKEND_REBUILD_REASON"
    bash "$TRAINING_BUILD_SH"
fi

WANDB_FLAG="--wandb"
EXTRA_ARGS=()
for arg in "$@"; do
    if [ "$arg" = "--no-wandb" ]; then
        WANDB_FLAG=""
    else
        EXTRA_ARGS+=("$arg")
    fi
done

CMD=(python -m pufferlib.pufferl sweep fight_caves
    --load-model-path "$LOAD_MODEL_PATH"
    --train.gpus "$TRAIN_GPUS"
    --sweep.gpus "$SWEEP_GPUS"
    --wandb-group "$WANDB_GROUP"
    --tag "$TAG")

if [ -n "$WANDB_FLAG" ]; then
    CMD+=("$WANDB_FLAG")
fi

if [ "${#EXTRA_ARGS[@]}" -gt 0 ]; then
    CMD+=("${EXTRA_ARGS[@]}")
fi

echo "[sweep_v18_2.sh] Warm-start checkpoint: $LOAD_MODEL_PATH"
echo "[sweep_v18_2.sh] Sweep GPUs: $SWEEP_GPUS | Train GPUs per trial: $TRAIN_GPUS"
echo "[sweep_v18_2.sh] W&B group: $WANDB_GROUP | tag: $TAG"

"${CMD[@]}"
