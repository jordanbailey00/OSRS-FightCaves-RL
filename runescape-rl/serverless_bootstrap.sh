#!/bin/bash
set -euo pipefail

log() {
    echo "[serverless_bootstrap] $*"
}

die() {
    echo "[serverless_bootstrap] ERROR: $*" >&2
    exit 1
}

SRC_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SRC_DIR/.." && pwd)"
PUFFER_DIR="${PUFFER_DIR:-$REPO_ROOT/pufferlib_4}"
VENV_DIR="${VENV_DIR:-$SRC_DIR/.venv}"

DEFAULT_CKPT_DIR="$PUFFER_DIR/checkpoints/fight_caves/xm6i52ta"
DEFAULT_CKPT_PATH="$DEFAULT_CKPT_DIR/0000005977931776.bin"

APT_PACKAGES=(
    git
    curl
    ca-certificates
    build-essential
    gcc
    g++
    clang
    python3-venv
    pkg-config
)

PIP_PACKAGES=(
    numpy
    rich
    rich_argparse
    gpytorch
    scikit-learn
    wandb
    pybind11
)

run_as_root() {
    if [ "$(id -u)" -eq 0 ]; then
        "$@"
    elif command -v sudo >/dev/null 2>&1; then
        sudo "$@"
    else
        die "Need root or sudo to install system packages"
    fi
}

validate_repo() {
    [ -d "$SRC_DIR" ] || die "runescape-rl directory not found at $SRC_DIR"
    [ -d "$PUFFER_DIR" ] || die "pufferlib_4 directory not found at $PUFFER_DIR"
    [ -f "$PUFFER_DIR/pyproject.toml" ] || die "Missing $PUFFER_DIR/pyproject.toml"
    [ -f "$SRC_DIR/train.sh" ] || die "Missing $SRC_DIR/train.sh"
    [ -f "$SRC_DIR/sweep_v18_3.sh" ] || die "Missing $SRC_DIR/sweep_v18_3.sh"
    [ -f "$SRC_DIR/training-env/assets/fightcaves.collision" ] || die "Missing Fight Caves collision asset"
}

install_system_packages() {
    if [ "${SKIP_APT:-0}" = "1" ]; then
        log "Skipping apt install (SKIP_APT=1)"
        return
    fi

    if ! command -v apt-get >/dev/null 2>&1; then
        log "apt-get not found; skipping system package install"
        return
    fi

    log "Installing system packages"
    run_as_root apt-get update
    run_as_root env DEBIAN_FRONTEND=noninteractive apt-get install -y "${APT_PACKAGES[@]}"
}

setup_python() {
    command -v python3 >/dev/null 2>&1 || die "python3 not found"

    if [ ! -d "$VENV_DIR" ]; then
        log "Creating virtualenv at $VENV_DIR"
        python3 -m venv "$VENV_DIR" --system-site-packages
    fi

    # shellcheck disable=SC1091
    source "$VENV_DIR/bin/activate"

    python -m pip install --upgrade pip setuptools wheel
    python -c "import torch" >/dev/null 2>&1 || die "PyTorch is not available. Use a PyTorch/CUDA pod image."
    python -m pip install "${PIP_PACKAGES[@]}"
    python -m pip install -e "$PUFFER_DIR" --no-deps
}

setup_wandb() {
    if [ "${SKIP_WANDB:-0}" = "1" ]; then
        log "Skipping W&B login (SKIP_WANDB=1)"
        return
    fi

    if [ -z "${WANDB_API_KEY:-}" ]; then
        log "WANDB_API_KEY not set; skipping W&B login"
        return
    fi

    # shellcheck disable=SC1091
    source "$VENV_DIR/bin/activate"

    log "Logging into W&B"
    wandb login --relogin "$WANDB_API_KEY"
}

fetch_warmstart() {
    mkdir -p "$DEFAULT_CKPT_DIR"

    if [ -f "$DEFAULT_CKPT_PATH" ]; then
        log "Warm-start checkpoint already present"
        return
    fi

    if [ -z "${WARMSTART_URL:-}" ]; then
        log "Warm-start checkpoint missing; set WARMSTART_URL to download it automatically"
        return
    fi

    log "Downloading warm-start checkpoint"
    curl -fL "$WARMSTART_URL" -o "$DEFAULT_CKPT_PATH"
}

build_backend() {
    if [ "${SKIP_BACKEND_BUILD:-0}" = "1" ]; then
        log "Skipping backend build (SKIP_BACKEND_BUILD=1)"
        return
    fi

    # shellcheck disable=SC1091
    source "$VENV_DIR/bin/activate"

    command -v nvidia-smi >/dev/null 2>&1 || die "nvidia-smi not found"
    command -v nvcc >/dev/null 2>&1 || die "nvcc not found. Use a pod image with CUDA toolkit installed."

    export PATH="/usr/local/cuda/bin:$PATH"
    export FC_COLLISION_PATH="$SRC_DIR/training-env/assets/fightcaves.collision"
    export CC="${CC:-gcc}"
    export CXX="${CXX:-g++}"

    EXT_SUFFIX="$(python -c "import sysconfig; print(sysconfig.get_config_var('EXT_SUFFIX') or '')")"
    BACKEND_SO="$PUFFER_DIR/pufferlib/_C${EXT_SUFFIX}"

    if [ -f "$BACKEND_SO" ] && [ "${FORCE_BACKEND_BUILD:-0}" != "1" ]; then
        log "Backend already built at $BACKEND_SO"
        return
    fi

    log "Building fight_caves backend"
    bash "$PUFFER_DIR/build.sh" fight_caves
    [ -f "$BACKEND_SO" ] || die "Expected backend not found after build: $BACKEND_SO"
}

prepare_runtime_dirs() {
    mkdir -p \
        "$PUFFER_DIR/checkpoints/fight_caves" \
        "$PUFFER_DIR/logs/fight_caves" \
        "$PUFFER_DIR/wandb"
}

print_next_steps() {
    log "Done"
    echo
    echo "Next:"
    echo "  cd $SRC_DIR"
    if [ -f "$DEFAULT_CKPT_PATH" ]; then
        echo "  bash sweep_v18_3.sh"
    else
        echo "  WARMSTART_URL=<checkpoint-url> bash serverless_bootstrap.sh"
        echo "  bash sweep_v18_3.sh"
    fi
}

validate_repo
install_system_packages
prepare_runtime_dirs
setup_python
setup_wandb
fetch_warmstart
build_backend
print_next_steps
