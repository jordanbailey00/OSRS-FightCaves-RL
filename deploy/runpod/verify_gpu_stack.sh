#!/usr/bin/env bash
set -euo pipefail

echo "== nvidia-smi =="
nvidia-smi

echo
echo "== nvcc =="
nvcc --version | tail -n 3

echo
echo "== python / torch =="
python - <<'PY'
import torch
import numpy
import pybind11

print("torch", torch.__version__)
print("torch.cuda", torch.version.cuda)
print("torch.cuda.is_available", torch.cuda.is_available())
if torch.cuda.is_available():
    print("gpu_count", torch.cuda.device_count())
    print("gpu_name", torch.cuda.get_device_name(0))
print("numpy", numpy.__version__)
print("pybind11", pybind11.__version__)
PY

echo
echo "== cuDNN linker target =="
python - <<'PY'
from pathlib import Path
import ctypes

import nvidia.cudnn

libdir = Path(nvidia.cudnn.__path__[0]) / "lib"
unversioned = libdir / "libcudnn.so"
versioned = sorted(libdir.glob("libcudnn.so.*"))
target = unversioned if unversioned.exists() else (versioned[0] if versioned else None)

print("cudnn_libdir", libdir)
print("has_unversioned", unversioned.exists())
if versioned:
    print("versioned_target", versioned[0].name)
if target is None:
    raise SystemExit("No cuDNN shared library found in runtime")

ctypes.CDLL(str(target))
print("cudnn_load_ok", target)
PY
