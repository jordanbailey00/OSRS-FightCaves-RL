from __future__ import annotations

from pathlib import Path

import yaml

from fight_caves_rl.native_runtime import ENV_BACKEND_NATIVE_PREVIEW
from fight_caves_rl.tests.smoke._helpers import load_json, offline_wandb_env, run_script


def test_native_train_smoke(tmp_path: Path):
    config_path = _write_native_train_config(tmp_path / "smoke_native_v2.yaml")
    summary_path = tmp_path / "train_native_summary.json"
    data_dir = tmp_path / "train_native_artifacts"
    result = run_script(
        "train.py",
        "--config",
        str(config_path),
        "--total-timesteps",
        "16",
        "--data-dir",
        str(data_dir),
        "--output",
        str(summary_path),
        env=offline_wandb_env(tmp_path, tags="smoke,train,native_preview"),
        timeout_seconds=240.0,
    )
    if result.returncode != 0:
        raise AssertionError(
            f"Native train smoke failed.\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}"
        )

    summary = load_json(summary_path)
    assert summary["transport_mode"] == "native_preview_subprocess_shared_memory_v1"
    assert int(summary["global_step"]) >= 16
    assert summary["vecenv_topology"]["env_backend"] == ENV_BACKEND_NATIVE_PREVIEW
    assert summary["vecenv_topology"]["transport_mode"] == "shared_memory_v1"
    assert summary["vecenv_topology"]["worker_count"] == 2


def test_native_eval_loop_smoke(tmp_path: Path):
    train_config_path = _write_native_train_config(tmp_path / "smoke_native_v2.yaml")
    train_summary_path = tmp_path / "native_train_summary.json"
    eval_config_path = _write_native_eval_config(tmp_path / "parity_native_v2.yaml")
    eval_path = tmp_path / "native_eval_summary.json"
    train_result = run_script(
        "train.py",
        "--config",
        str(train_config_path),
        "--total-timesteps",
        "16",
        "--data-dir",
        str(tmp_path / "native_train_artifacts"),
        "--output",
        str(train_summary_path),
        env=offline_wandb_env(tmp_path, tags="smoke,eval-train,native-preview"),
        timeout_seconds=240.0,
    )
    if train_result.returncode != 0:
        raise AssertionError(
            f"Native train smoke failed.\nstdout:\n{train_result.stdout}\nstderr:\n{train_result.stderr}"
        )

    checkpoint_path = str(load_json(train_summary_path)["checkpoint_path"])
    eval_result = run_script(
        "eval.py",
        "--checkpoint",
        checkpoint_path,
        "--config",
        str(eval_config_path),
        "--max-steps",
        "16",
        "--output",
        str(eval_path),
        env=offline_wandb_env(tmp_path, tags="smoke,eval,native-preview"),
        timeout_seconds=240.0,
    )
    if eval_result.returncode != 0:
        raise AssertionError(
            f"Native eval smoke failed.\nstdout:\n{eval_result.stdout}\nstderr:\n{eval_result.stderr}"
        )

    summary = load_json(eval_path)
    assert summary["config_id"] == "parity_native_v2"
    assert summary["reward_config_id"] == "reward_sparse_v2"
    assert summary["runtime_reward_config_id"] == "reward_sparse_v0"
    assert len(summary["per_seed"]) == 2
    assert summary["summary_digest"]


def _write_native_train_config(path: Path) -> Path:
    payload = yaml.safe_load(
        (Path(__file__).resolve().parents[3] / "configs" / "train" / "smoke_fast_v2.yaml").read_text(
            encoding="utf-8"
        )
    )
    payload["config_id"] = "smoke_native_v2"
    payload["env"]["env_backend"] = ENV_BACKEND_NATIVE_PREVIEW
    payload["train"]["device"] = "cpu"
    path.write_text(yaml.safe_dump(payload, sort_keys=False), encoding="utf-8")
    return path


def _write_native_eval_config(path: Path) -> Path:
    payload = yaml.safe_load(
        (Path(__file__).resolve().parents[3] / "configs" / "eval" / "parity_fast_v2.yaml").read_text(
            encoding="utf-8"
        )
    )
    payload["config_id"] = "parity_native_v2"
    payload["env"] = {"env_backend": ENV_BACKEND_NATIVE_PREVIEW}
    path.write_text(yaml.safe_dump(payload, sort_keys=False), encoding="utf-8")
    return path
