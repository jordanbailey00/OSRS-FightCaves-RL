from __future__ import annotations

from dataclasses import asdict
import hashlib
import json
from pathlib import Path
from typing import Any

from fight_caves_rl.contracts.reward_feature_schema import (
    REWARD_FEATURE_DEFINITIONS,
    REWARD_FEATURE_SCHEMA,
)
from fight_caves_rl.contracts.terminal_codes import (
    TERMINAL_CODE_DEFINITIONS,
    TERMINAL_CODE_SCHEMA,
)
from fight_caves_rl.envs.schema import (
    FIGHT_CAVE_EPISODE_START_CONTRACT,
    HEADLESS_ACTION_DEFINITIONS,
    HEADLESS_ACTION_REJECT_REASONS,
    HEADLESS_ACTION_SCHEMA,
    HEADLESS_PROTECTION_PRAYER_IDS,
    HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA,
)


FROZEN_CONTRACTS_DIR = Path(__file__).resolve().parent / "frozen"


def export_action_schema_artifact() -> dict[str, Any]:
    return _with_integrity(
        {
            "artifact_type": "action_schema",
            "contract": asdict(HEADLESS_ACTION_SCHEMA),
            "compatibility_rules": {
                "action_ids": "append_only",
                "parameter_names": "append_only_per_action",
                "reject_reasons": "append_only",
            },
            "packed_fast_schema": {
                "word_count": 4,
                "dtype": "int32",
            },
            "protection_prayer_ids": list(HEADLESS_PROTECTION_PRAYER_IDS),
            "reject_reasons": list(HEADLESS_ACTION_REJECT_REASONS),
            "actions": [asdict(action) for action in HEADLESS_ACTION_DEFINITIONS],
        }
    )


def export_flat_observation_schema_artifact() -> dict[str, Any]:
    return _with_integrity(
        {
            "artifact_type": "flat_observation_schema",
            "contract": asdict(HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA.identity),
            "dtype": HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA.dtype,
            "feature_count": HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA.feature_count,
            "max_visible_npcs": HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA.max_visible_npcs,
            "base_field_count": HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA.base_field_count,
            "npc_slot_field_count": HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA.npc_slot_field_count,
            "shape_policy": "replace_on_shape_or_semantic_change",
        }
    )


def export_reward_feature_schema_artifact() -> dict[str, Any]:
    return _with_integrity(
        {
            "artifact_type": "reward_feature_schema",
            "contract": asdict(REWARD_FEATURE_SCHEMA),
            "compatibility_rules": {
                "features": "append_only",
            },
            "feature_count": len(REWARD_FEATURE_DEFINITIONS),
            "features": [asdict(feature) for feature in REWARD_FEATURE_DEFINITIONS],
        }
    )


def export_terminal_code_schema_artifact() -> dict[str, Any]:
    return _with_integrity(
        {
            "artifact_type": "terminal_code_schema",
            "contract": asdict(TERMINAL_CODE_SCHEMA),
            "compatibility_rules": {
                "codes": "append_only",
            },
            "codes": [
                {
                    "code": int(definition.code),
                    "name": definition.code.name,
                    "description": definition.description,
                }
                for definition in TERMINAL_CODE_DEFINITIONS
            ],
        }
    )


def export_episode_start_contract_artifact() -> dict[str, Any]:
    return _with_integrity(
        {
            "artifact_type": "episode_start_contract",
            "contract": asdict(FIGHT_CAVE_EPISODE_START_CONTRACT.identity),
            "seed_key": FIGHT_CAVE_EPISODE_START_CONTRACT.seed_key,
            "start_wave": {
                "min": FIGHT_CAVE_EPISODE_START_CONTRACT.start_wave_min,
                "max": FIGHT_CAVE_EPISODE_START_CONTRACT.start_wave_max,
                "default": FIGHT_CAVE_EPISODE_START_CONTRACT.default_start_wave,
            },
            "defaults": {
                "ammo": FIGHT_CAVE_EPISODE_START_CONTRACT.default_ammo,
                "prayer_potions": FIGHT_CAVE_EPISODE_START_CONTRACT.default_prayer_potions,
                "sharks": FIGHT_CAVE_EPISODE_START_CONTRACT.default_sharks,
                "run_energy_percent": FIGHT_CAVE_EPISODE_START_CONTRACT.run_energy_percent,
                "run_toggle_on": FIGHT_CAVE_EPISODE_START_CONTRACT.run_toggle_on,
                "xp_gain_blocked": FIGHT_CAVE_EPISODE_START_CONTRACT.xp_gain_blocked,
            },
            "fixed_levels": [
                {"skill": name, "level": level}
                for name, level in FIGHT_CAVE_EPISODE_START_CONTRACT.fixed_levels
            ],
            "reset_clocks": list(FIGHT_CAVE_EPISODE_START_CONTRACT.reset_clocks),
            "reset_variables": list(FIGHT_CAVE_EPISODE_START_CONTRACT.reset_variables),
            "equipment": list(FIGHT_CAVE_EPISODE_START_CONTRACT.equipment),
            "inventory_item_ids": list(FIGHT_CAVE_EPISODE_START_CONTRACT.inventory_item_ids),
            "start_wave_invocation": FIGHT_CAVE_EPISODE_START_CONTRACT.start_wave_invocation,
        }
    )


def export_all_contract_artifacts() -> dict[str, dict[str, Any]]:
    return {
        "action_schema.v1.json": export_action_schema_artifact(),
        "flat_observation_schema.v1.json": export_flat_observation_schema_artifact(),
        "reward_feature_schema.v1.json": export_reward_feature_schema_artifact(),
        "terminal_code_schema.v1.json": export_terminal_code_schema_artifact(),
        "episode_start_contract.v1.json": export_episode_start_contract_artifact(),
    }


def write_contract_artifacts(output_dir: Path = FROZEN_CONTRACTS_DIR) -> list[Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    for filename, payload in export_all_contract_artifacts().items():
        path = output_dir / filename
        path.write_text(_format_json(payload), encoding="utf-8")
        written.append(path)
    return written


def contract_artifact_drift(output_dir: Path = FROZEN_CONTRACTS_DIR) -> list[str]:
    errors: list[str] = []
    for filename, expected in export_all_contract_artifacts().items():
        path = output_dir / filename
        if not path.is_file():
            errors.append(f"Missing frozen contract artifact: {path}")
            continue
        current = json.loads(path.read_text(encoding="utf-8"))
        normalized_expected = json.loads(json.dumps(expected))
        if current != normalized_expected:
            errors.append(
                _describe_contract_drift(
                    filename,
                    current=current,
                    expected=normalized_expected,
                )
            )
    return errors


def _describe_contract_drift(
    filename: str,
    *,
    current: dict[str, Any],
    expected: dict[str, Any],
) -> str:
    artifact_type = str(expected.get("artifact_type", filename))
    if artifact_type == "action_schema":
        return _append_only_message(
            filename=filename,
            item_name="action ids",
            current=current.get("actions", []),
            expected=expected.get("actions", []),
        )
    if artifact_type == "reward_feature_schema":
        return _append_only_message(
            filename=filename,
            item_name="reward features",
            current=current.get("features", []),
            expected=expected.get("features", []),
        )
    if artifact_type == "terminal_code_schema":
        return _append_only_message(
            filename=filename,
            item_name="terminal codes",
            current=current.get("codes", []),
            expected=expected.get("codes", []),
        )
    return (
        f"Frozen contract artifact drifted for {filename}. "
        "This contract is not append-only; update the live contract intentionally, "
        "then regenerate the frozen artifact and version metadata together."
    )


def _append_only_message(
    *,
    filename: str,
    item_name: str,
    current: list[Any],
    expected: list[Any],
) -> str:
    prefix_matches = current[: len(expected)] == expected
    if prefix_matches and len(current) >= len(expected):
        return (
            f"Frozen contract artifact drifted for {filename}. "
            f"The {item_name} contract is append-only, so existing entries must stay byte-for-byte "
            "stable and any new entries must be appended after the frozen prefix. Regenerate the "
            "artifact only after intentionally extending the schema."
        )
    return (
        f"Frozen contract artifact drifted for {filename}. "
        f"The {item_name} contract is append-only and the existing frozen prefix was modified."
    )


def _with_integrity(payload: dict[str, Any]) -> dict[str, Any]:
    checksum = _checksum(payload)
    return {
        **payload,
        "integrity": {
            "algorithm": "sha256",
            "checksum": checksum,
        },
    }


def _checksum(payload: dict[str, Any]) -> str:
    return hashlib.sha256(_canonical_json_bytes(payload)).hexdigest()


def _canonical_json_bytes(payload: Any) -> bytes:
    return json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8")


def _format_json(payload: dict[str, Any]) -> str:
    return json.dumps(payload, indent=2, sort_keys=True) + "\n"
