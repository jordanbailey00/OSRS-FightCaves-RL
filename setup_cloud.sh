#!/bin/bash
set -e

# ============================================================
# Cloud GPU Setup Script — RunPod / Lambda / Vast.ai
# Run once after SSH into a fresh instance:
#   git clone git@github.com:jordanbailey00/runescape-rl.git && cd runescape-rl && git checkout v2Claude && bash setup_cloud.sh
# ============================================================

echo "=== Fight Caves RL — Cloud GPU Setup ==="

REPO_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_DIR"

# 1. System dependencies (most cloud images have these, but just in case)
echo "[1/5] Checking system dependencies..."
if ! command -v gcc &>/dev/null || ! command -v g++ &>/dev/null; then
    echo "  Installing build tools..."
    apt-get update -qq && apt-get install -y -qq build-essential python3-dev 2>/dev/null || \
    sudo apt-get update -qq && sudo apt-get install -y -qq build-essential python3-dev 2>/dev/null || \
    echo "  Warning: could not install build tools. Ensure gcc/g++ are available."
fi

# 2. Python venv
echo "[2/5] Setting up Python venv..."
if [ ! -d "runescape-rl/.venv" ]; then
    python3 -m venv runescape-rl/.venv
fi
source runescape-rl/.venv/bin/activate

# 3. Python packages
echo "[3/5] Installing Python packages..."
pip install --quiet --upgrade pip
pip install --quiet torch numpy pybind11 wandb rich rich-argparse

# 4. Build training backend
echo "[4/5] Building training backend..."
cd runescape-rl
export FC_COLLISION_PATH="$(pwd)/fc-core/assets/fightcaves.collision"
bash training-env/build.sh --cpu 2>&1 | tail -3
cd ..

# 5. Verify
echo "[5/5] Verifying..."
python3 -c "
import sys
sys.path.insert(0, 'pufferlib_4')
import pufferlib
print(f'  pufferlib: OK')
import torch
print(f'  torch: {torch.__version__}')
print(f'  CUDA: {torch.cuda.is_available()} ({torch.cuda.get_device_name(0) if torch.cuda.is_available() else \"CPU\"})')
"

# 6. W&B auto-login
echo "[6/6] Configuring W&B..."
WANDB_API_KEY="${WANDB_API_KEY:-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}"
python3 -c "import wandb; wandb.login(key='$WANDB_API_KEY', relogin=True)" 2>/dev/null && echo "  W&B: logged in" || echo "  W&B: login failed (training will still work without logging)"

echo ""
echo "=== Setup complete ==="
echo "To start training:"
echo "  cd runescape-rl && bash train.sh"
