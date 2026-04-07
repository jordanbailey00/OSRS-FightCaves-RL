# Serverless / GPU Pod Setup Plan

## Goal

Create a clean cloud-training workflow for Fight Caves so an ephemeral GPU pod can:

1. clone the required code from GitHub,
2. install the required system and Python dependencies,
3. fetch the required warm-start checkpoint,
4. build the local PufferLib backend,
5. launch the staged training command,
6. write checkpoints/logs/W&B data without any manual file copying from the local machine.

This document is a **planning document only**. It does **not** assume the setup already exists.

---

## Current Reality

The current local workflow is not yet packaged for a clean pod bootstrap.

### Important constraints

1. The top-level repo at `codex3` does **not** currently track `pufferlib_4`.
   - Root `.gitignore` contains:
     - `pufferlib_4/`
   - That means cloning the top-level repo alone is **not sufficient** for training.

2. `pufferlib_4` is currently a **separate nested git repo** with required local modifications.
   - Current local branch: `4.0`
   - Current modified files include:
     - `build.sh`
     - `pufferlib/pufferl.py`
     - `pufferlib/sweep.py`
     - `config/fight_caves.ini`
     - `assets/fightcaves.collision`
     - `ocean/fight_caves/**`

3. The live Fight Caves training path depends on **both**:
   - `runescape-rl/...`
   - `pufferlib_4/...`

4. The staged `v18.3` sweep launch path is:
   - [sweep_v18_3.sh](/home/joe/projects/runescape-rl/codex3/runescape-rl/sweep_v18_3.sh)
   - which in turn calls:
     - [train.sh](/home/joe/projects/runescape-rl/codex3/runescape-rl/train.sh)
     - `python -m pufferlib.pufferl sweep fight_caves`

5. The warm-start checkpoint is **not** in git.
   - Current default `v18.3` warm-start checkpoint:
     - `pufferlib_4/checkpoints/fight_caves/xm6i52ta/0000005977931776.bin`
   - A pod cannot run `v18.3` unless this file is hosted somewhere it can download from.

6. The backend build is not just Python.
   - It builds a compiled local backend via `pufferlib_4/build.sh`.
   - That path requires:
     - Python headers
     - `pybind11`
     - `numpy`
     - C/C++ compiler
     - OpenMP-capable toolchain
     - CUDA toolkit with `nvcc`
     - cuDNN / CUDA libs available either from the system or Python packages

---

## Required Code Inventory

This is the minimum code that must exist on the pod for **headless training**.

## Required from `runescape-rl`

These files are part of the training launch and local Fight Caves training env:

- `runescape-rl/train.sh`
- `runescape-rl/sweep_v18_3.sh`
- `runescape-rl/config/fight_caves.ini`
- `runescape-rl/training-env/**`
  - includes:
    - `binding.c`
    - `fight_caves.h`
    - `fight_caves.c`
    - `src/**`
    - `assets/fightcaves.collision`

Useful but optional:

- `runescape-rl/analyze_run.sh`
- `runescape-rl/docs/rl_config.md`
- `runescape-rl/train_v18_3_control.sh`
- `runescape-rl/config/fight_caves_v18_3_control.ini`

Not needed for cloud training:

- `runescape-rl/demo-env/**`
- `runescape-rl/eval_viewer.py`
- `runescape-rl/docs/**` except for operator reference
- local build artifacts under `runescape-rl/build/`

## Required from `pufferlib_4`

These files are required because the trainer imports PufferLib and builds the local backend:

- `pufferlib_4/pyproject.toml`
- `pufferlib_4/build.sh`
- `pufferlib_4/pufferlib/**`
  - especially:
    - `pufferlib/pufferl.py`
    - `pufferlib/sweep.py`
    - `pufferlib/torch_pufferl.py`
    - `pufferlib/models.py`
- `pufferlib_4/src/**`
  - CUDA / CPU backend bindings
- `pufferlib_4/vendor/**`
- `pufferlib_4/config/fight_caves.ini`
- `pufferlib_4/assets/fightcaves.collision`
- `pufferlib_4/ocean/fight_caves/**`
  - includes the local Fight Caves env that `pufferlib_4/build.sh` expects

Useful but optional:

- `pufferlib_4/README.md`
- `pufferlib_4/LICENSE`

Not needed for cloud training:

- checkpoints
- logs
- wandb cache
- tests
- examples
- other env configs
- compiled outputs:
  - `build/`
  - `pufferlib/_C*.so`
  - downloaded `raylib-*`

---

## Branch Strategy

## Preferred immediate path: two-repo cloud bootstrap

This is the lowest-risk option because it does **not** require restructuring the local codebase first.

### Repo / branch plan

1. Push a cloud branch for `runescape-rl`
   - branch name: `v2codex_runpod`
   - contents:
     - only the Fight Caves training-relevant code
     - demo/viewer code may be omitted on that branch

2. Push a cloud branch for `pufferlib_4`
   - branch name: `v2codex_runpod`
   - contents:
     - all local PufferLib changes needed for Fight Caves
     - especially the `v18.3` sweep fix and the local `ocean/fight_caves` env

3. Clone both repos side-by-side on the pod:
   - `/workspace/runescape-rl`
   - `/workspace/pufferlib_4`

Why this is the recommended first version:

- it matches the current local filesystem assumptions
- `train.sh` already expects `../pufferlib_4`
- no forced vendoring or repo surgery required
- easier to validate before building a more minimal single-repo flow

## Future ideal path: single training-only repo or submodule layout

This is cleaner long term, but it is **not** free.

Options:

1. Track `pufferlib_4` in the top-level repo
   - remove `pufferlib_4/` from root `.gitignore`
   - vendor the required subset

2. Convert `pufferlib_4` into a proper Git submodule
   - top-level repo tracks exact commit
   - pod bootstrap becomes:
     - `git clone --recurse-submodules ...`

3. Create a dedicated training-only monorepo
   - only Fight Caves training code
   - no demo-env
   - no local historical clutter

This is the best eventual shape, but the immediate cloud path should use the two-repo model first.

---

## What Must Be Committed Before Cloud Training

## In `runescape-rl` branch

Must be committed and pushed:

- current `v18.3` launch scripts
- current `v18.3` config
- `train.sh` changes that support alternate config paths
- training env source
- collision asset

Should **not** be committed:

- `.venv/`
- `build/`
- replay gifs / frames
- local logs
- local checkpoints

## In `pufferlib_4` branch

Must be committed and pushed:

- `pufferlib/pufferl.py`
- `pufferlib/sweep.py`
- `build.sh`
- `ocean/fight_caves/**`
- `config/fight_caves.ini` if it is used by runtime
- `assets/fightcaves.collision`

Should **not** be committed:

- `build/`
- `pufferlib/_C*.so`
- `logs/`
- `checkpoints/`
- `wandb/`
- downloaded `raylib-*`

## Key warning

A `runescape-rl` branch by itself is **not enough** right now because:

- the top-level repo ignores `pufferlib_4`
- the live training path imports and builds from `pufferlib_4`

So before cloud training, both code sources must be pushed somewhere accessible to the pod.

---

## External Artifacts That Must Be Hosted Separately

Code alone is not enough.

## Required external artifact

The warm-start checkpoint file must be downloadable by the pod:

- current `v18.3` default:
  - `xm6i52ta/0000005977931776.bin`

Recommended hosting options:

1. **W&B artifact** — preferred
   - versioned
   - fits the existing experiment workflow
   - accessible from the pod after `wandb login`

2. GitHub Release asset
   - simple, but less experiment-native

3. Cloud object storage (S3 / R2 / GCS)
   - good if multiple pods will reuse checkpoints

Do **not** rely on manually copying checkpoint files into pods for the repeatable workflow.

## Other required secrets / auth

- W&B API key
- GitHub auth if the repos or branches are private

Optional:

- Hugging Face / object-store credentials if checkpoints are hosted there instead of W&B

---

## Pod Requirements

The target pod must provide or be able to install:

### System requirements

- Linux x86_64
- NVIDIA GPU visible via `nvidia-smi`
- enough disk for:
  - two repos
  - Python venv
  - backend build artifacts
  - checkpoints
  - logs / W&B cache

### Required tools

- `git`
- `curl`
- `bash`
- `python3`
- `python3-venv`
- `gcc`
- `g++`
- `clang` or a valid C compiler
- `make` / `build-essential`
- `pkg-config` (optional but useful)

### Required GPU build dependency

- **CUDA toolkit with `nvcc`**

This is critical. The current backend build requires `nvcc`.

The pod bootstrap must verify:

```bash
command -v nvcc
nvidia-smi
```

If `nvcc` is missing:

- the pod image is not sufficient for the current training build path
- either:
  - use a CUDA **development** image/template, or
  - install the CUDA toolkit before build, or
  - build a custom pod template with `nvcc` preinstalled

### Python packages required

Current PufferLib project dependencies:

- `setuptools`
- `numpy`
- `torch>=2.9`
- `rich`
- `rich_argparse`
- `gpytorch`
- `scikit-learn`
- `wandb`
- `pybind11`

Additional operational package:

- `wheel`

---

## Recommended Pod Filesystem Layout

Use this exact shape so the current scripts work with minimal overrides:

```text
/workspace/
  runescape-rl/
  pufferlib_4/
```

Why:

- `runescape-rl/train.sh` defaults `PUFFER_DIR` to `../pufferlib_4`
- `sweep_v18_3.sh` assumes the same layout

---

## Recommended Bootstrap Flow

## Step 1: clone both branches

Example target layout:

```bash
cd /workspace
git clone --branch v2codex_runpod <RUNESCAPE_RL_GIT_URL> runescape-rl
git clone --branch v2codex_runpod <PUFFERLIB4_GIT_URL> pufferlib_4
```

## Step 2: install system packages

Exact package list may vary by base image, but the minimum target command should look like:

```bash
apt-get update
apt-get install -y git curl build-essential gcc g++ clang python3-venv pkg-config
```

## Step 3: create the Python environment

```bash
cd /workspace/runescape-rl
python3 -m venv .venv --system-site-packages
source .venv/bin/activate
python -m pip install --upgrade pip setuptools wheel
python -m pip install numpy rich rich_argparse gpytorch scikit-learn wandb pybind11
python -m pip install -e /workspace/pufferlib_4 --no-deps
```

Notes:

- `--system-site-packages` is useful if the pod template already ships with PyTorch.
- `--no-deps` avoids accidentally replacing the pod’s CUDA/PyTorch stack.

## Step 4: authenticate W&B

```bash
source /workspace/runescape-rl/.venv/bin/activate
wandb login
```

## Step 5: download the warm-start checkpoint

The bootstrap must fetch:

- `/workspace/pufferlib_4/checkpoints/fight_caves/xm6i52ta/0000005977931776.bin`

The exact command depends on where the checkpoint is hosted.

Example placeholder:

```bash
mkdir -p /workspace/pufferlib_4/checkpoints/fight_caves/xm6i52ta
<DOWNLOAD_COMMAND> /workspace/pufferlib_4/checkpoints/fight_caves/xm6i52ta/0000005977931776.bin
```

## Step 6: validate prerequisites

Before launching a run:

```bash
command -v nvcc
nvidia-smi
test -f /workspace/pufferlib_4/checkpoints/fight_caves/xm6i52ta/0000005977931776.bin
```

## Step 7: launch the run

For `v18.3`:

```bash
cd /workspace/runescape-rl
bash sweep_v18_3.sh
```

---

## One-Command Bootstrap Target

This is the workflow we want, even if we do not implement it yet.

The pod terminal should eventually receive something conceptually like:

```bash
bash -lc '
set -euo pipefail
cd /workspace
git clone --branch v2codex_runpod <RUNESCAPE_RL_GIT_URL> runescape-rl
git clone --branch v2codex_runpod <PUFFERLIB4_GIT_URL> pufferlib_4
apt-get update
apt-get install -y git curl build-essential gcc g++ clang python3-venv pkg-config
cd /workspace/runescape-rl
python3 -m venv .venv --system-site-packages
source .venv/bin/activate
python -m pip install --upgrade pip setuptools wheel
python -m pip install numpy rich rich_argparse gpytorch scikit-learn wandb pybind11
python -m pip install -e /workspace/pufferlib_4 --no-deps
wandb login < /path/to/noninteractive_token_input_or_env_setup
mkdir -p /workspace/pufferlib_4/checkpoints/fight_caves/xm6i52ta
<DOWNLOAD_CHECKPOINT_COMMAND>
bash sweep_v18_3.sh
'
```

This should eventually become a real script committed in the serverless branch.

---

## Pre-Push Checklist

Before building the cloud branches:

1. Confirm `runescape-rl` branch contains the intended training launch code.
2. Confirm `pufferlib_4` branch contains:
   - the `v18.3` sweep fix
   - the current Fight Caves env
3. Confirm `.gitignore` does **not** accidentally omit required source files for the chosen repo strategy.
4. Confirm the warm-start checkpoint is hosted remotely.
5. Confirm the pod image has `nvcc`, or choose/build one that does.
6. Confirm W&B auth method for non-interactive pod setup.

---

## Open Decisions

These are the remaining design choices before the cloud path is truly clean.

### 1. Two repos vs one repo

Immediate recommendation:

- use **two repos / two branches**

Reason:

- it matches the current code reality
- no structural refactor required first

### 2. Checkpoint hosting

Preferred recommendation:

- host the warm-start checkpoint as a **W&B artifact**

Reason:

- versioned
- experiment-native
- easy to map to runs

### 3. Pod template

Preferred recommendation:

- use a pod image/template with **CUDA toolkit + nvcc already present**

Reason:

- avoids spending pod time installing CUDA
- reduces bootstrap failure modes

### 4. Whether to prune demo-env immediately

Recommendation:

- yes for the cloud branch, but only after validating that all training-only build paths still work without it

Reason:

- demo-env is not needed for training
- but do not accidentally remove anything the build scripts still reference indirectly

---

## Recommended Immediate Next Steps

1. Create a GitHub branch `v2codex_runpod` for `runescape-rl`.
2. Create a GitHub branch `v2codex_runpod` for `pufferlib_4`.
3. Decide and implement checkpoint hosting.
4. Choose a pod template with `nvcc`.
5. Convert the bootstrap flow above into a committed shell script in the cloud branch.
6. Validate on one pod before pruning further.

---

## Bottom Line

A clean cloud workflow is feasible now, but it is **not** yet “clone one branch and run” unless we first solve two packaging problems:

1. `pufferlib_4` must be pushed and cloned as part of the workflow.
2. the warm-start checkpoint must be hosted remotely.

The lowest-risk path is:

- two GitHub branches,
- side-by-side clone on the pod,
- scripted environment/bootstrap,
- remote checkpoint download,
- then `bash sweep_v18_3.sh`.
