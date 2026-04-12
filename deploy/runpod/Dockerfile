FROM nvidia/cuda:12.8.1-devel-ubuntu24.04

ENV DEBIAN_FRONTEND=noninteractive
ENV CUDA_HOME=/usr/local/cuda
ENV PATH=/opt/runescape-rl-venv/bin:/usr/local/cuda/bin:${PATH}
ENV LD_LIBRARY_PATH=/usr/local/cuda/lib64:${LD_LIBRARY_PATH}
ENV VIRTUAL_ENV=/opt/runescape-rl-venv
ENV PIP_NO_CACHE_DIR=1

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    ca-certificates \
    curl \
    libgl1 \
    libice6 \
    libsm6 \
    libx11-6 \
    libxcursor1 \
    libxext6 \
    libxfixes3 \
    libxi6 \
    libxinerama1 \
    libxrandr2 \
    openssh-server \
    pkg-config \
    python3 \
    python3-dev \
    python3-pip \
    python3-venv \
    && ln -sf /usr/bin/python3 /usr/local/bin/python && \
    rm -rf /var/lib/apt/lists/* /var/cache/apt/archives/*

RUN python3 -m venv "${VIRTUAL_ENV}"

COPY requirements.txt /tmp/runpod-requirements.txt

RUN python -m pip install --upgrade pip setuptools wheel && \
    python -m pip install --index-url https://download.pytorch.org/whl/cu128 'torch==2.11.0' && \
    python -m pip install --no-cache-dir -r /tmp/runpod-requirements.txt && \
    python - <<'PY'
import torch
import numpy
import pybind11
print("torch", torch.__version__)
print("torch_cuda", torch.version.cuda)
print("numpy", numpy.__version__)
print("pybind11", pybind11.__version__)
PY

RUN mkdir -p /var/run/sshd /root/.ssh /workspace /runpod-volume && \
    chmod 700 /root/.ssh && \
    ssh-keygen -A && \
    printf '%s\n' \
      'PermitRootLogin yes' \
      'PasswordAuthentication no' \
      'PubkeyAuthentication yes' \
      'UsePAM no' \
      'X11Forwarding no' \
      'ClientAliveInterval 120' \
      'ClientAliveCountMax 5' \
      >> /etc/ssh/sshd_config

COPY entrypoint.sh /usr/local/bin/runpod-entrypoint
COPY bootstrap_from_volume.sh /usr/local/bin/runpod-bootstrap
COPY verify_gpu_stack.sh /usr/local/bin/runpod-verify

RUN chmod +x \
    /usr/local/bin/runpod-entrypoint \
    /usr/local/bin/runpod-bootstrap \
    /usr/local/bin/runpod-verify

WORKDIR /workspace
EXPOSE 22

ENTRYPOINT ["/usr/local/bin/runpod-entrypoint"]
CMD ["sleep", "infinity"]
