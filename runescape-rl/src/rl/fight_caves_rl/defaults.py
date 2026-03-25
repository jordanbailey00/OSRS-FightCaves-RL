from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from fight_caves_rl.utils.paths import legacy_headless_env_root, repo_root

DEFAULT_TRAIN_CONFIG_PATH = Path("configs/train/smoke_fast_v2.yaml")
DEFAULT_VECENV_SMOKE_CONFIG_PATH = DEFAULT_TRAIN_CONFIG_PATH
DEFAULT_ENV_BENCHMARK_CONFIG_PATH = Path("configs/benchmark/fast_env_v2.yaml")
DEFAULT_TRAIN_BENCHMARK_CONFIG_PATH = Path("configs/benchmark/fast_train_v2.yaml")
DEFAULT_TRAIN_CEILING_CONFIG_PATH = Path("configs/benchmark/fast_train_v2.yaml")
PHASE10_SELECTED_TRAIN_CONFIG_PATH = Path("configs/train/train_fast_v2_mlp64.yaml")
PHASE10_SELECTED_TRAIN_BENCHMARK_CONFIG_PATH = Path("configs/benchmark/fast_train_v2_mlp64.yaml")

ORACLE_REPLAY_CONFIG_PATH = Path("configs/eval/replay_eval_v0.yaml")
FAST_EVAL_CONFIG_PATH = Path("configs/eval/parity_fast_v2.yaml")

DEFAULT_TRAIN_ENV_BACKEND = "v2_fast"

DEMO_BACKEND_RSPS_HEADED = "rsps_headed"
DEMO_BACKEND_FIGHT_CAVES_DEMO_LITE = "fight_caves_demo_lite"
DEMO_BACKEND_ORACLE_V1 = "oracle_v1"

DEFAULT_DEMO_BACKEND = DEMO_BACKEND_RSPS_HEADED
DEFAULT_REPLAY_BACKEND = DEMO_BACKEND_RSPS_HEADED


@dataclass(frozen=True)
class BackendSelection:
    backend_id: str
    role: str
    default_mode: str
    entrypoint: str
    selection_hint: str


def backend_selection_registry() -> dict[str, BackendSelection]:
    rl_scripts = repo_root() / "scripts"
    return {
        DEMO_BACKEND_RSPS_HEADED: BackendSelection(
            backend_id=DEMO_BACKEND_RSPS_HEADED,
            role="default headed demo/replay backend",
            default_mode="replay",
            entrypoint=str(rl_scripts / "run_headed_trace_replay.py"),
            selection_hint=(
                "Use runescape-rl/src/rl/scripts/run_demo_backend.py with the default backend "
                "or pass --backend rsps_headed."
            ),
        ),
        DEMO_BACKEND_FIGHT_CAVES_DEMO_LITE: BackendSelection(
            backend_id=DEMO_BACKEND_FIGHT_CAVES_DEMO_LITE,
            role="frozen headed fallback/reference path",
            default_mode="launch_reference",
            entrypoint=f"{legacy_headless_env_root()} ::fight-caves-demo-lite:run",
            selection_hint=(
                "Use runescape-rl/src/rl/scripts/run_demo_backend.py --backend fight_caves_demo_lite "
                "--mode launch_reference."
            ),
        ),
        DEMO_BACKEND_ORACLE_V1: BackendSelection(
            backend_id=DEMO_BACKEND_ORACLE_V1,
            role="V1 oracle/reference/debug replay path",
            default_mode="replay",
            entrypoint=str(rl_scripts / "replay_eval.py"),
            selection_hint=(
                "Use runescape-rl/src/rl/scripts/run_demo_backend.py --backend oracle_v1 --mode replay."
            ),
        ),
    }
