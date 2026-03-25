from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
from typing import Any

import torch

import pufferlib
import pufferlib.pufferl
from fight_caves_rl.policies.registry import build_policy_from_config
from fight_caves_rl.puffer.factory import build_puffer_train_config, load_smoke_train_config, make_vecenv
from fight_caves_rl.puffer.trainer import ConfigurablePuffeRL


class _NullLogger:
    def __init__(self) -> None:
        self.run_id = "inspect_learner_path"
        self.records: list[object] = []
        self.artifact_records: list[object] = []
        self.effective_tags: tuple[str, ...] = ()

    def log(self, *args: Any, **kwargs: Any) -> None:
        return None

    def close(self, *args: Any, **kwargs: Any) -> None:
        return None

    def build_artifact_record(self, *args: Any, **kwargs: Any) -> object:
        raise RuntimeError("inspect_learner_path does not build artifacts")

    def update_config(self, *args: Any, **kwargs: Any) -> None:
        return None

    def log_artifact(self, *args: Any, **kwargs: Any) -> None:
        return None

    def finish(self) -> None:
        return None


def _tensor_device(value: object) -> str | None:
    if torch.is_tensor(value):
        return str(value.device)
    return None


def _buffer_devices(trainer: ConfigurablePuffeRL) -> dict[str, str]:
    names = (
        "observations",
        "actions",
        "values",
        "logprobs",
        "rewards",
        "terminals",
        "truncations",
        "ratio",
        "ep_lengths",
        "ep_indices",
    )
    payload: dict[str, str] = {}
    for name in names:
        value = getattr(trainer, name, None)
        device = _tensor_device(value)
        if device is not None:
            payload[name] = device
    return payload


def _optimizer_state_devices(trainer: ConfigurablePuffeRL) -> list[str]:
    devices: set[str] = set()
    for state in trainer.optimizer.state.values():
        for value in state.values():
            device = _tensor_device(value)
            if device is not None:
                devices.add(device)
    return sorted(devices)


def _optimizer_state_summary(trainer: ConfigurablePuffeRL) -> dict[str, list[str]]:
    summary: dict[str, set[str]] = {}
    for state in trainer.optimizer.state.values():
        for key, value in state.items():
            device = _tensor_device(value)
            if device is None:
                continue
            summary.setdefault(device, set()).add(str(key))
    return {device: sorted(keys) for device, keys in sorted(summary.items())}


def main() -> None:
    parser = argparse.ArgumentParser(description="Inspect the CUDA learner path for native_preview training.")
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--env-count", type=int, default=None)
    parser.add_argument("--total-timesteps", type=int, default=None)
    args = parser.parse_args()

    config = load_smoke_train_config(args.config)
    if args.env_count is not None:
        config["num_envs"] = int(args.env_count)
    if args.total_timesteps is not None:
        config["train"]["total_timesteps"] = int(args.total_timesteps)
    config.setdefault("logging", {})["dashboard"] = False

    payload: dict[str, Any] = {
        "torch": {
            "version": torch.__version__,
            "version_cuda": torch.version.cuda,
            "cuda_is_available": bool(torch.cuda.is_available()),
            "cuda_device_count": int(torch.cuda.device_count()),
            "cuda_device_name_0": (
                torch.cuda.get_device_name(0) if torch.cuda.is_available() else None
            ),
        },
        "resolved_train_device": str(config["train"]["device"]),
        "pufferlib": {
            "module_path": str(Path(pufferlib.__file__).resolve()),
            "native_extension_importable": bool(importlib.util.find_spec("pufferlib._C")),
            "advantage_cuda_enabled": bool(pufferlib.pufferl.ADVANTAGE_CUDA),
            "nvcc_path": shutil.which("nvcc"),
            "torch_op_available": bool(
                hasattr(torch.ops, "pufferlib")
                and hasattr(torch.ops.pufferlib, "compute_puff_advantage")
            ),
        },
    }

    vecenv = None
    trainer = None
    original_advantage = pufferlib.pufferl.compute_puff_advantage
    advantage_probe: dict[str, Any] = {}
    train_error: str | None = None

    def _wrapped_advantage(values, rewards, terminals, ratio, advantages, *extra):
        advantage_probe["input_devices"] = {
            "values": str(values.device),
            "rewards": str(rewards.device),
            "terminals": str(terminals.device),
            "ratio": str(ratio.device),
            "advantages": str(advantages.device),
        }
        advantage_probe["advantage_cuda_enabled"] = bool(pufferlib.pufferl.ADVANTAGE_CUDA)
        result = original_advantage(values, rewards, terminals, ratio, advantages, *extra)
        advantage_probe["output_device"] = str(result.device)
        return result

    pufferlib.pufferl.compute_puff_advantage = _wrapped_advantage
    try:
        with tempfile.TemporaryDirectory(prefix="inspect_learner_path_") as tmpdir:
            vecenv = make_vecenv(config, backend="subprocess")
            policy = build_policy_from_config(
                config,
                vecenv.single_observation_space,
                vecenv.single_action_space,
            )
            payload["policy_param_device_before_trainer"] = str(next(policy.parameters()).device)
            train_config = build_puffer_train_config(
                config,
                data_dir=Path(tmpdir),
                total_timesteps=int(config["train"]["total_timesteps"]),
            )
            policy = policy.to(train_config["device"])
            trainer = ConfigurablePuffeRL(
                train_config,
                vecenv,
                policy,
                _NullLogger(),
                dashboard_enabled=False,
                checkpointing_enabled=False,
                profiling_enabled=False,
                utilization_enabled=False,
                logging_enabled=False,
                instrumentation_enabled=True,
            )
            payload["vecenv_topology"] = (
                dict(vecenv.topology_snapshot()) if hasattr(vecenv, "topology_snapshot") else {}
            )
            payload["trainer"] = {
                "config_device": str(trainer.config["device"]),
                "policy_param_device": str(next(trainer.policy.parameters()).device),
                "buffer_devices": _buffer_devices(trainer),
                "optimizer_state_devices_before_train": _optimizer_state_devices(trainer),
                "optimizer_state_summary_before_train": _optimizer_state_summary(trainer),
            }

            try:
                trainer.evaluate()
                trainer.train()
            except Exception as exc:  # pragma: no cover - surfaced in payload
                train_error = f"{type(exc).__name__}: {exc}"

            payload["trainer"].update(
                {
                    "global_step_after_probe": int(getattr(trainer, "global_step", 0)),
                    "optimizer_state_devices_after_train": _optimizer_state_devices(trainer),
                    "optimizer_state_summary_after_train": _optimizer_state_summary(trainer),
                    "instrumentation": trainer.instrumentation_snapshot(),
                    "losses": dict(getattr(trainer, "losses", {})),
                }
            )
    finally:
        pufferlib.pufferl.compute_puff_advantage = original_advantage
        if trainer is not None:
            try:
                trainer.close()
            except Exception:
                pass
        elif vecenv is not None:
            try:
                vecenv.close()
            except Exception:
                pass

    payload["advantage_probe"] = advantage_probe
    payload["train_error"] = train_error
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
