#!/bin/bash
set -e

# ============================================================
# Cloud GPU Setup — ONE COMMAND, ZERO MANUAL STEPS
#
# Usage after clone:
#   bash setup_cloud.sh
# Then:
#   cd runescape-rl && bash train.sh
# ============================================================

REPO_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_DIR"

echo "=== [1/6] System dependencies ==="
apt-get update -qq 2>/dev/null && apt-get install -y -qq build-essential python3-dev python3-venv 2>/dev/null || true

echo "=== [2/6] CuDNN linker symlink ==="
# The system has libcudnn.so.9 but the linker needs libcudnn.so
# This is the #1 cause of CUDA build failures on cloud instances
if [ -f /usr/lib/x86_64-linux-gnu/libcudnn.so.9 ] && [ ! -f /usr/lib/x86_64-linux-gnu/libcudnn.so ]; then
    ln -sf /usr/lib/x86_64-linux-gnu/libcudnn.so.9 /usr/lib/x86_64-linux-gnu/libcudnn.so
    echo "  Created /usr/lib/x86_64-linux-gnu/libcudnn.so -> libcudnn.so.9"
elif [ -f /usr/lib/x86_64-linux-gnu/libcudnn.so ]; then
    echo "  libcudnn.so already exists"
else
    # Try to find it elsewhere
    CUDNN_SO=$(find /usr -name "libcudnn.so.*" -not -name "*.so.*.*" 2>/dev/null | head -1)
    if [ -n "$CUDNN_SO" ]; then
        CUDNN_DIR=$(dirname "$CUDNN_SO")
        ln -sf "$CUDNN_SO" "$CUDNN_DIR/libcudnn.so"
        echo "  Created $CUDNN_DIR/libcudnn.so -> $(basename $CUDNN_SO)"
    else
        echo "  WARNING: No libcudnn found. CUDA build will fail, CPU fallback will be used."
    fi
fi

# Also symlink nccl if missing (another common cloud issue)
if [ -f /usr/lib/x86_64-linux-gnu/libnccl.so.2 ] && [ ! -f /usr/lib/x86_64-linux-gnu/libnccl.so ]; then
    ln -sf /usr/lib/x86_64-linux-gnu/libnccl.so.2 /usr/lib/x86_64-linux-gnu/libnccl.so
    echo "  Created libnccl.so symlink"
fi

echo "=== [3/6] Python venv ==="
if [ ! -d "runescape-rl/.venv" ]; then
    python3 -m venv runescape-rl/.venv
fi
source runescape-rl/.venv/bin/activate

echo "=== [4/6] Python packages ==="
pip install --quiet --upgrade pip
pip install --quiet torch numpy pybind11 wandb rich rich-argparse
pip install --quiet nvidia-cudnn-cu12 2>/dev/null || true

# If pip cudnn installed, symlink those too
PIP_CUDNN_LIB="$(python3 -c "import nvidia.cudnn,os;print(os.path.join(nvidia.cudnn.__path__[0],'lib'))" 2>/dev/null || true)"
if [ -n "$PIP_CUDNN_LIB" ] && [ -d "$PIP_CUDNN_LIB" ] && [ ! -f /usr/lib/x86_64-linux-gnu/libcudnn.so ]; then
    for f in "$PIP_CUDNN_LIB"/libcudnn*.so*; do
        ln -sf "$f" /usr/lib/x86_64-linux-gnu/ 2>/dev/null || true
    done
    echo "  Symlinked pip CuDNN to /usr/lib/"
fi

echo "=== [5/6] W&B login ==="
WANDB_KEY="${WANDB_API_KEY:-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}"
python3 -c "import wandb; wandb.login(key='$WANDB_KEY', relogin=True)" 2>/dev/null && echo "  Logged in" || echo "  Failed (training works without it)"

echo "=== [6/6] Build training backend ==="
cd runescape-rl
export FC_COLLISION_PATH="$(pwd)/fc-core/assets/fightcaves.collision"
export CC="${CC:-gcc}"
export CXX="${CXX:-g++}"
bash training-env/build.sh 2>&1 | tail -3 || {
    echo "  CUDA build failed, trying CPU..."
    bash training-env/build.sh --cpu 2>&1 | tail -3
}

echo ""
echo "=== Verification ==="
source .venv/bin/activate 2>/dev/null || source "$REPO_DIR/runescape-rl/.venv/bin/activate"
python3 -c "
import torch
print(f'  torch {torch.__version__}')
print(f'  CUDA: {torch.cuda.is_available()} ({torch.cuda.get_device_name(0) if torch.cuda.is_available() else \"CPU\"})')
import sys; sys.path.insert(0,'$REPO_DIR/pufferlib_4')
from pufferlib import _C
gpu = hasattr(_C, 'create_pufferl')
print(f'  Backend: {\"GPU (create_pufferl)\" if gpu else \"CPU only (no create_pufferl - CUDA build failed)\"}')
if not gpu:
    print('  !! Training will NOT work without GPU backend !!')
    print('  !! Check that libcudnn.so symlink exists and re-run setup !!')
"

echo ""
echo "=== DONE. To train: ==="
echo "  cd runescape-rl && bash train.sh"
