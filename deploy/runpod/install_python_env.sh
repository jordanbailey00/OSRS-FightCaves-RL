#!/usr/bin/env bash
set -euo pipefail

VENV_ROOT="${RUNPOD_VENV_ROOT:-/opt/runescape-rl-venv}"
REQ_FILE="${RUNPOD_REQUIREMENTS_FILE:-/opt/runpod/requirements.txt}"

python3 -m venv "${VENV_ROOT}"
source "${VENV_ROOT}/bin/activate"

python -m pip install --upgrade pip setuptools wheel
python -m pip install --index-url https://download.pytorch.org/whl/cu128 'torch==2.11.0'
python -m pip install --no-cache-dir -r "${REQ_FILE}"

# The cuDNN wheels ship versioned shared objects (for example libcudnn.so.9)
# without the unversioned linker names expected by training-env/build.sh.
# Add local symlinks inside the venv so -lcudnn resolves during backend builds.
python - <<'PY'
import os
from pathlib import Path

import nvidia.cudnn

libdir = Path(nvidia.cudnn.__path__[0]) / "lib"
created = []
for path in sorted(libdir.glob("libcudnn*.so.*")):
    stem = path.name.split(".so.", 1)[0]
    link = libdir / f"{stem}.so"
    if not link.exists():
        link.symlink_to(path.name)
        created.append((link.name, path.name))

for link_name, target_name in created:
    print(f"linked {link_name} -> {target_name}")
PY

python - <<'PY'
import torch
import numpy
import pybind11

print("torch", torch.__version__)
print("torch.cuda", torch.version.cuda)
print("numpy", numpy.__version__)
print("pybind11", pybind11.__version__)
PY
