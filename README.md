# Fight Caves RL

Training a reinforcement learning agent to complete the Old School RuneScape Fight Caves — a 63-wave PvM gauntlet ending in a boss fight against TzTok-Jad.

**Goals:**
- Build a deterministic, high-performance Fight Caves simulation in C
- Train a PPO agent from scratch to clear all 63 waves and defeat Jad
- Achieve this without human demonstrations or hardcoded strategies

**Current results (v28.4 — best config to date):**
- Agent consistently reaches wave 60-62+ from cold start
- Learned safespotting, prayer switching, kiting, and resource management
- **Jad kill rate: 20-50% (peak 38.6% at 3.26B steps)** — actively optimizing via reward sweeps

![Agent Demo](runescape-rl/assets/demo.gif)

---

## Getting Started

### Requirements

- Linux (tested on Ubuntu 24.04)
- Python 3.12+
- NVIDIA GPU with CUDA 12.8+ and cuDNN 9+
- CMake 3.20+
- GCC/G++
- Raylib 5.5 (vendored in `demo-env/raylib/`)

### Clone & Setup

```bash
git clone https://github.com/jordanbailey00/OSRS-FightCaves-RL.git
cd OSRS-FightCaves-RL/runescape-rl

# Create virtual environment
python3 -m venv .venv
source .venv/bin/activate

# Install dependencies
pip install torch numpy wandb pybind11 rich rich-argparse
```

### Play (Viewer)

Build and launch the interactive Raylib viewer:

```bash
cmake -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build -j$(nproc)
./build/demo-env/fc_viewer
```

Controls:
- Arrow keys / WASD — move player
- 1/2/3 — toggle protect melee/range/magic
- Right-click drag — orbit camera
- Scroll — zoom
- O — toggle debug overlay
- Q/Esc — quit

<!-- TODO: Screenshot of the playable viewer with HUD, prayer icons, and NPC health bars -->

### Train

Run a full training session (cold start, ~40 min for 5B steps on RTX 5070 Ti):

```bash
FORCE_BACKEND_REBUILD=1 bash train.sh
```

`train.sh` handles venv activation, config sync, backend compilation, and launch. Training logs to W&B by default.

Key flags:
```bash
bash train.sh --no-wandb                    # disable W&B logging
bash train.sh sweep                          # run hyperparameter sweep
LOAD_MODEL_PATH=/path/to/checkpoint.bin bash train.sh  # warm-start
```

### Evaluate a Checkpoint

Watch a trained policy play in the viewer:

```bash
source .venv/bin/activate
python3 demo-env/eval_viewer.py --ckpt /path/to/checkpoint.bin
```

---

## Commands

| Command | Description |
|---------|-------------|
| `bash train.sh` | Train with current config (cold start) |
| `bash train.sh sweep` | Run Protein hyperparameter sweep |
| `bash train.sh --no-wandb` | Train without W&B logging |
| `LOAD_MODEL_PATH=<path> bash train.sh` | Warm-start from checkpoint |
| `./build/demo-env/fc_viewer` | Launch playable viewer |
| `python3 demo-env/eval_viewer.py --ckpt <path>` | Replay trained policy |
| `bash analyze_run.sh <run_id>` | Quick W&B run analysis |

---

## Architecture

<!-- TODO: Simple block diagram showing fc-core -> training-env -> PufferLib and fc-core -> demo-env -> Raylib -->

```
runescape-rl/
├── fc-core/           Deterministic C game simulation
│   ├── include/       Headers (types, contracts, combat, reward, API)
│   └── src/           Implementation (tick, state, combat, NPC, wave, prayer)
├── training-env/      PufferLib 4.0 adapter (binding.c, fight_caves.h)
├── demo-env/          Raylib 3D viewer + eval tooling
│   ├── src/           Viewer, debug overlay, asset loaders
│   └── assets/        Collision map, prayer/item sprites
├── config/            Training config (fight_caves.ini)
└── docs/              Run history, RL config reference, design doc
```

**Backend (`fc-core/`):** Pure C, zero allocations per tick. Deterministic game simulation — combat, pathfinding, prayer, waves, NPC AI. Both the viewer and trainer call into the same `fc_step()` function.

**Training (`training-env/`):** PufferLib 4.0 wrapper. Compiles the C backend into a shared library with CUDA-accelerated vectorized environments. 4096 parallel agents, ~2M steps/sec on a single GPU.

**Viewer (`demo-env/`):** Raylib-based 3D viewer for human play and policy replay. Debug overlay shows reward breakdown, NPC stats, prayer state, threat context.

**Training stack:** PPO with MinGRU policy (2-layer, 256 hidden, ~439K params). W&B integration for logging and sweep analysis.

---

## RL Config

Current live config (v25.9):

| Category | Key params |
|----------|-----------|
| **Combat rewards** | `w_damage_dealt=0.7`, `w_npc_kill=3.5`, `w_wave_clear=15.0`, `w_jad_kill=150.0` |
| **Survival penalties** | `w_damage_taken=-0.6` (squared, 70x amplified), `w_player_death=-20.0` |
| **Prayer shaping** | `w_correct_danger_prayer=0.25`, `w_correct_jad_prayer=2.0`, `shape_wrong_prayer_penalty=-1.25` |
| **Positioning** | `shape_npc_melee_penalty=-1.0`, `shape_safespot_attack_reward=1.5`, `shape_kiting_reward=1.0` (band 2-10) |
| **Resource management** | `shape_food_full_waste_penalty=-6.5`, `shape_pot_full_waste_penalty=-6.5` |
| **PPO** | `lr=3e-4`, `gamma=0.999`, `ent_coef=0.01`, `horizon=256`, `minibatch=4096` |
| **Vector** | `4096 agents`, `2 buffers` |

Full config reference: [`docs/rl_config.md`](docs/rl_config.md)

---

## Results

<!-- TODO: W&B chart showing wave_reached progression over training steps for v25.9 -->

### Best Cold Start: v25.9 (`8td831nt`)

| Metric | Value |
|--------|-------|
| wave_reached (final avg) | 58.67 |
| max_wave | 63 |
| reached_wave_63 | 2.3% |
| jad_kill_rate (peak) | 0.42% |
| prayer_uptime_magic | 52.1% |
| attack_when_ready_rate | 93.4% |
| training time | 43 min (5B steps) |
| throughput | 1.84M SPS |

<!-- TODO: Screenshot of eval viewer showing agent at wave 60+ with debug overlay -->

### Key Milestones

- **v21.2** — First cold start to reach wave 60 (range-7 weapon, no LOS)
- **v22.1** — First Jad kills (1.5%, warm-started), introduced TBow combat model
- **v25.4** — Best warm-start run (44.5% wave-63 access, 0.13% Jad kills)
- **v25.9** — First cold start under TBow+LOS to reach wave 60+ and kill Jad

<!-- TODO: W&B comparison chart of v25.8 (failed cold start, wave 30) vs v25.9 (successful, wave 58) -->

### Active: Reward Sweep

Running a 200-run Protein sweep over 10 reward parameters to find optimal config for Jad conversion. See [`docs/run_history.md`](docs/run_history.md) for full experiment history.

<!-- TODO: Screenshot of W&B sweep dashboard showing parameter importance -->

---

## Project Status

Active research. The agent reaches wave 60+ consistently but Jad kills remain rare (<1%). Current focus is reward optimization via sweeps and improving prayer switching behavior.

Full experiment history with configs, results, and analysis: [`docs/run_history.md`](docs/run_history.md)

---

## License

MIT License. See [LICENSE](../LICENSE) for details.
