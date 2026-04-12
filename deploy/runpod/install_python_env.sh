#!/usr/bin/env bash
set -euo pipefail

VENV_ROOT="${RUNPOD_VENV_ROOT:-/opt/runescape-rl-venv}"
REQ_FILE="${RUNPOD_REQUIREMENTS_FILE:-/opt/runpod/requirements.txt}"

python3 -m venv "${VENV_ROOT}"
source "${VENV_ROOT}/bin/activate"

python -m pip install --upgrade pip setuptools wheel
python -m pip install --index-url https://download.pytorch.org/whl/cu128 'torch==2.11.0'
python -m pip install --no-cache-dir -r "${REQ_FILE}"

python - <<'PY'
import torch
import numpy
import pybind11

print("torch", torch.__version__)
print("torch.cuda", torch.version.cuda)
print("numpy", numpy.__version__)
print("pybind11", pybind11.__version__)
PY
