# training-env ARCHITECTURE

## Internal structure

- `training-env/sim/`
  - Kotlin/Gradle multi-module headless sim runtime.
  - Owns simulation tick/reset/action/observation mechanics surface for headless path.
- `training-env/rl/`
  - Python package and scripts for training, eval, replay, benchmark, and backend selection.
- `training-env/docs/`, `training-env/analysis/`, `training-env/fixtures/`
  - module-level support docs, analysis helpers, and small fixtures.

## Runtime/control flow

## Train path

1. Entry: `training-env/rl/scripts/train.py`.
2. Config/backend resolution: `training-env/rl` selects default `v2_fast` env backend.
3. Vecenv construction: RL factory builds vecenv (subprocess or embedded mode).
4. Control loop: trainer sends batched actions and receives batched transitions.
5. Output: checkpoints/manifests/train artifacts are written to runtime root.

## Eval/replay path

1. Entry: `training-env/rl/scripts/replay_eval.py` (or `run_demo_backend.py` when selecting replay mode).
2. Input: checkpoint + eval config + seed pack.
3. Runtime: RL drives sim transitions deterministically for evaluation.
4. Output: `eval_summary`, replay pack/index, and run manifests in runtime root.

## Benchmark/profiling path

1. Env benchmark entry: `training-env/rl/scripts/benchmark_env.py`.
2. Train benchmark entry: `training-env/rl/scripts/benchmark_train.py`.
3. Packet/decomposition:
   - `run_phase9_baseline_packet.py`
   - `analyze_phase9_baseline_packet.py`
4. Hot-path attribution:
   - `profile_hotpath_attribution.py`
5. Output: benchmark/profiling JSON/CSV and reports in runtime root.

## How RL calls into sim

- RL never executes game semantics directly; it calls sim through vecenv boundaries.
- In subprocess mode:
  - parent RL process issues `send(actions)`/`recv()` calls
  - worker processes host sim vecenv instances and return transitions
  - optional shared-memory transport reduces copy overhead on the boundary
- In embedded mode:
  - RL process hosts vecenv directly and calls sim runtime in-process
- In both modes, the control contract is batched transitions:
  - `observations, rewards, terminals, truncations, infos, masks` plus action metadata as configured

## Control boundaries

- Sim mechanics live in `sim`; RL orchestrates around it.
- RL can batch/transport/log but should not silently alter sim semantics.
- Output routing is centralized through runtime-root path helpers and workspace bootstrap scripts.

## Relationship to sibling modules

- `rsps/` is the active headed runtime target for demo/replay.
- `demo-env/` remains fallback/reference only.
