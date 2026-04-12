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
