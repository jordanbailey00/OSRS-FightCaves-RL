#!/bin/bash
set -e

# ============================================================
# Cloud GPU Setup — RunPod / Lambda / Vast.ai
# One command after clone:
#   bash setup_cloud.sh
# Then:
#   cd runescape-rl && bash train.sh
# ============================================================

echo "=== Fight Caves RL — Cloud GPU Setup ==="

REPO_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_DIR"

# 1. System deps
echo "[1/6] System dependencies..."
if ! command -v gcc &>/dev/null; then
    apt-get update -qq && apt-get install -y -qq build-essential python3-dev 2>/dev/null || \
    sudo apt-get update -qq && sudo apt-get install -y -qq build-essential python3-dev 2>/dev/null || true
fi

# 2. Venv
echo "[2/6] Python venv..."
if [ ! -d "runescape-rl/.venv" ]; then
    python3 -m venv runescape-rl/.venv
fi
source runescape-rl/.venv/bin/activate

# 3. ALL pip deps (torch, pybind11, wandb, rich, CuDNN)
echo "[3/6] Installing Python packages (torch, cudnn, wandb, etc)..."
pip install --quiet --upgrade pip
pip install --quiet torch numpy pybind11 wandb rich rich-argparse
pip install --quiet nvidia-cudnn-cu12 2>/dev/null || pip install --quiet nvidia-cudnn-cu11 2>/dev/null || echo "  Warning: could not install CuDNN pip package"

# 4. Set LD_LIBRARY_PATH for CuDNN so CUDA build finds libcudnn
CUDNN_LIB="$(python3 -c "import nvidia.cudnn, os; print(os.path.join(nvidia.cudnn.__path__[0], 'lib'))" 2>/dev/null || true)"
if [ -n "$CUDNN_LIB" ] && [ -d "$CUDNN_LIB" ]; then
    export LD_LIBRARY_PATH="$CUDNN_LIB${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
    # Also create symlink so linker finds -lcudnn
    if [ ! -f /usr/lib/x86_64-linux-gnu/libcudnn.so ] && ls "$CUDNN_LIB"/libcudnn.so* &>/dev/null; then
        ln -sf "$CUDNN_LIB"/libcudnn.so* /usr/lib/x86_64-linux-gnu/ 2>/dev/null || \
        sudo ln -sf "$CUDNN_LIB"/libcudnn.so* /usr/lib/x86_64-linux-gnu/ 2>/dev/null || true
    fi
    echo "  CuDNN: $CUDNN_LIB"
else
    echo "  CuDNN: not found (will fall back to CPU build)"
fi

# 5. Build training backend (try CUDA first, fall back to CPU)
echo "[5/6] Building training backend..."
cd runescape-rl
export FC_COLLISION_PATH="$(pwd)/fc-core/assets/fightcaves.collision"
export CC="${CC:-gcc}"
export CXX="${CXX:-g++}"
bash training-env/build.sh 2>&1 | tail -3 || {
    echo "  CUDA build failed, trying CPU..."
    bash training-env/build.sh --cpu 2>&1 | tail -3
}
cd ..

# 6. W&B
echo "[6/6] Configuring W&B..."
WANDB_API_KEY="${WANDB_API_KEY:-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}"
python3 -c "import wandb; wandb.login(key='$WANDB_API_KEY', relogin=True)" 2>/dev/null && echo "  W&B: logged in" || echo "  W&B: login failed"

# 7. Verify
echo ""
echo "=== Verification ==="
python3 -c "
import sys
sys.path.insert(0, 'pufferlib_4')
import torch
print(f'  torch: {torch.__version__}')
print(f'  CUDA: {torch.cuda.is_available()} ({torch.cuda.get_device_name(0) if torch.cuda.is_available() else \"CPU\"})')
try:
    from pufferlib import _C
    print(f'  _C backend: OK (has create_pufferl: {hasattr(_C, \"create_pufferl\")})')
except ImportError as e:
    print(f'  _C backend: FAILED ({e})')
"

echo ""
echo "=== Setup complete. Start training: ==="
echo "  cd runescape-rl && bash train.sh"
