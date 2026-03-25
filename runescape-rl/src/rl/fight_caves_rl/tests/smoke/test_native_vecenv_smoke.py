from __future__ import annotations

from pathlib import Path

import yaml

from fight_caves_rl.native_runtime import ENV_BACKEND_NATIVE_PREVIEW
from fight_caves_rl.tests.smoke._helpers import load_json, run_script


def test_native_vecenv_reset_step_smoke(tmp_path: Path):
    config_path = _write_native_smoke_config(tmp_path / "smoke_native_v2.yaml")
    output = tmp_path / "native_vecenv_reset_step.json"
    result = run_script(
        "run_vecenv_smoke.py",
        "--config",
        str(config_path),
        "--mode",
        "reset-step",
        "--output",
        str(output),
    )
    if result.returncode != 0:
        raise AssertionError(
            f"Native vecenv reset/step smoke failed.\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}"
        )

    payload = load_json(output)
    assert payload["mode"] == "reset-step"
    assert payload["backend"] == "embedded"
    assert int(payload["env_count"]) == 4
    assert payload["initial_observation_shape"] == [4, 134]
    assert payload["topology"]["env_backend"] == ENV_BACKEND_NATIVE_PREVIEW
    assert payload["topology"]["transport_mode"] == "embedded_native_ctypes"
    assert payload["topology"]["worker_count"] == 1


def test_native_vecenv_subprocess_shared_memory_smoke(tmp_path: Path):
    config_path = _write_native_smoke_config(tmp_path / "smoke_native_v2.yaml")
    output = tmp_path / "native_vecenv_subprocess.json"
    result = run_script(
        "run_vecenv_smoke.py",
        "--config",
        str(config_path),
        "--mode",
        "long-run",
        "--backend",
        "subprocess",
        "--output",
        str(output),
    )
    if result.returncode != 0:
        raise AssertionError(
            f"Native subprocess vecenv smoke failed.\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}"
        )

    payload = load_json(output)
    assert payload["mode"] == "long-run"
    assert payload["backend"] == "subprocess"
    assert int(payload["env_count"]) == 4
    assert payload["topology"]["env_backend"] == ENV_BACKEND_NATIVE_PREVIEW
    assert payload["topology"]["transport_mode"] == "shared_memory_v1"
    assert payload["topology"]["worker_count"] == 2
    assert payload["topology"]["worker_env_counts"] == [2, 2]
    assert payload["last_reward_shape"] == [4]


def _write_native_smoke_config(path: Path) -> Path:
    payload = yaml.safe_load(
        (Path(__file__).resolve().parents[3] / "configs" / "train" / "smoke_fast_v2.yaml").read_text(
            encoding="utf-8"
        )
    )
    payload["config_id"] = "smoke_native_v2"
    payload["env"]["env_backend"] = ENV_BACKEND_NATIVE_PREVIEW
    path.write_text(yaml.safe_dump(payload, sort_keys=False), encoding="utf-8")
    return path
