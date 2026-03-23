from pathlib import Path

import yaml

from fight_caves_rl.defaults import (
    DEFAULT_DEMO_BACKEND,
    DEFAULT_ENV_BENCHMARK_CONFIG_PATH,
    DEFAULT_REPLAY_BACKEND,
    DEFAULT_TRAIN_BENCHMARK_CONFIG_PATH,
    DEFAULT_TRAIN_CONFIG_PATH,
    DEFAULT_TRAIN_ENV_BACKEND,
    PHASE10_SELECTED_TRAIN_BENCHMARK_CONFIG_PATH,
    PHASE10_SELECTED_TRAIN_CONFIG_PATH,
    DEMO_BACKEND_FIGHT_CAVES_DEMO_LITE,
    DEMO_BACKEND_ORACLE_V1,
    DEMO_BACKEND_RSPS_HEADED,
    backend_selection_registry,
)
from fight_caves_rl.utils.paths import repo_root, workspace_root


def test_training_defaults_point_to_v2_fast_configs():
    assert DEFAULT_TRAIN_ENV_BACKEND == "v2_fast"
    assert (repo_root() / DEFAULT_TRAIN_CONFIG_PATH).is_file()
    assert (repo_root() / DEFAULT_ENV_BENCHMARK_CONFIG_PATH).is_file()
    assert (repo_root() / DEFAULT_TRAIN_BENCHMARK_CONFIG_PATH).is_file()
    assert (repo_root() / PHASE10_SELECTED_TRAIN_CONFIG_PATH).is_file()
    assert (repo_root() / PHASE10_SELECTED_TRAIN_BENCHMARK_CONFIG_PATH).is_file()


def test_phase10_selected_configs_preserve_v2_fast_training_contract():
    root = repo_root()
    for relative_path in (
        str(PHASE10_SELECTED_TRAIN_CONFIG_PATH),
        str(PHASE10_SELECTED_TRAIN_BENCHMARK_CONFIG_PATH),
    ):
        payload = yaml.safe_load((root / relative_path).read_text(encoding="utf-8"))
        assert payload["env"]["env_backend"] == "v2_fast"
        assert payload["env"]["info_payload_mode"] == "minimal"
        assert payload["env"]["subprocess_transport_mode"] == "shared_memory_v1"
        assert payload["policy"]["id"] == "mlp_v0"
        assert payload["policy"]["hidden_size"] == 64
        assert payload["reward_config"] == "reward_shaped_v2"
        assert payload["curriculum_config"] == "curriculum_wave_progression_v2"
        assert payload["train"]["use_rnn"] is False


def test_demo_backend_defaults_point_to_rsps_headed_with_explicit_fallbacks():
    registry = backend_selection_registry()

    assert DEFAULT_DEMO_BACKEND == DEMO_BACKEND_RSPS_HEADED
    assert DEFAULT_REPLAY_BACKEND == DEMO_BACKEND_RSPS_HEADED
    assert set(registry) == {
        DEMO_BACKEND_RSPS_HEADED,
        DEMO_BACKEND_FIGHT_CAVES_DEMO_LITE,
        DEMO_BACKEND_ORACLE_V1,
    }
    assert Path(registry[DEMO_BACKEND_RSPS_HEADED].entrypoint).is_file()
    assert Path(registry[DEMO_BACKEND_ORACLE_V1].entrypoint).is_file()
    assert "fight_caves_demo_lite" in registry[DEMO_BACKEND_FIGHT_CAVES_DEMO_LITE].entrypoint


def test_preserved_v1_config_fallbacks_pin_v1_bridge_backend():
    root = repo_root()
    for relative_path in (
        "configs/train/smoke_ppo_v0.yaml",
        "configs/train/train_baseline_v0.yaml",
        "configs/benchmark/vecenv_256env_v0.yaml",
        "configs/benchmark/train_1024env_v0.yaml",
    ):
        payload = yaml.safe_load((root / relative_path).read_text(encoding="utf-8"))
        assert payload["env"]["env_backend"] == "v1_bridge"
