Runpod image/bootstrap files for Fight Caves training.

Files:
- `Dockerfile`: custom CUDA devel image with Python/torch/build deps and SSH
- `requirements.txt`: Python runtime deps baked into the image
- `entrypoint.sh`: starts `sshd` and installs the injected public key
- `bootstrap_from_volume.sh`: restores the training workspace from the mounted volume
- `verify_gpu_stack.sh`: checks CUDA, nvcc, and torch GPU visibility

Expected pod flow:
1. Attach the network volume at `/runpod-volume`
2. SSH into the pod
3. Run `runpod-bootstrap`
4. `cd /workspace/codex3/runescape-rl`
5. Run `runpod-verify`
6. Start training with the warm checkpoint printed by the bootstrap script
