from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from fight_caves_rl.contracts.frozen_artifacts import FROZEN_CONTRACTS_DIR
from fight_caves_rl.native_runtime.constants import (
    NATIVE_RUNTIME_ABI_VERSION,
    NATIVE_RUNTIME_ID,
    NATIVE_RUNTIME_MODE,
    NATIVE_RUNTIME_VERSION,
)

_CONTRACT_FILENAMES = {
    "action_schema": "action_schema.v1.json",
    "flat_observation_schema": "flat_observation_schema.v1.json",
    "reward_feature_schema": "reward_feature_schema.v1.json",
    "terminal_code_schema": "terminal_code_schema.v1.json",
    "episode_start_contract": "episode_start_contract.v1.json",
}


def load_frozen_contract_payloads(
    contracts_dir: Path = FROZEN_CONTRACTS_DIR,
) -> dict[str, dict[str, Any]]:
    payloads: dict[str, dict[str, Any]] = {}
    for key, filename in _CONTRACT_FILENAMES.items():
        path = contracts_dir / filename
        payloads[key] = json.loads(path.read_text(encoding="utf-8"))
    return payloads


def expected_descriptor_bundle(
    contracts_dir: Path = FROZEN_CONTRACTS_DIR,
) -> dict[str, Any]:
    contracts = load_frozen_contract_payloads(contracts_dir)
    action_schema = contracts["action_schema"]
    flat_schema = contracts["flat_observation_schema"]
    reward_schema = contracts["reward_feature_schema"]
    terminal_schema = contracts["terminal_code_schema"]
    episode_contract = contracts["episode_start_contract"]
    return {
        "native_runtime_id": NATIVE_RUNTIME_ID,
        "native_runtime_version": NATIVE_RUNTIME_VERSION,
        "native_runtime_mode": NATIVE_RUNTIME_MODE,
        "abi_version": NATIVE_RUNTIME_ABI_VERSION,
        "descriptor_count": len(contracts),
        "summary": {
            "action_schema_id": action_schema["contract"]["contract_id"],
            "action_schema_version": action_schema["contract"]["version"],
            "action_count": len(action_schema["actions"]),
            "flat_observation_schema_id": flat_schema["contract"]["contract_id"],
            "flat_observation_schema_version": flat_schema["contract"]["version"],
            "flat_observation_feature_count": flat_schema["feature_count"],
            "flat_observation_max_visible_npcs": flat_schema["max_visible_npcs"],
            "reward_feature_schema_id": reward_schema["contract"]["contract_id"],
            "reward_feature_schema_version": reward_schema["contract"]["version"],
            "reward_feature_count": reward_schema["feature_count"],
            "terminal_code_schema_id": terminal_schema["contract"]["contract_id"],
            "terminal_code_schema_version": terminal_schema["contract"]["version"],
            "terminal_code_count": len(terminal_schema["codes"]),
            "episode_start_contract_id": episode_contract["contract"]["contract_id"],
            "episode_start_contract_version": episode_contract["contract"]["version"],
        },
        "contracts": contracts,
    }
