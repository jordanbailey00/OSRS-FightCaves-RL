from __future__ import annotations

import json
from pathlib import Path

import pytest

from fight_caves_rl.envs.observation_views import (
    observation_consumable_value,
    observation_player_hitpoints_current,
    observation_remaining,
    observation_tick,
    observation_wave,
    reconstruct_raw_observation_from_flat,
)
from fight_caves_rl.native_runtime import (
    NativeEpisodeConfig,
    NativeRuntimeBuildConfig,
    load_native_runtime,
)


@pytest.mark.parametrize(
    ("trace_id", "fixture_name"),
    [
        ("jad_telegraph", "oracle_jad_telegraph.json"),
        ("jad_prayer_protected", "oracle_jad_prayer_protected.json"),
        ("jad_prayer_too_late", "oracle_jad_prayer_too_late.json"),
        ("jad_healer", "oracle_jad_healer.json"),
        ("tzkek_split", "oracle_tzkek_split.json"),
    ],
)
def test_native_special_trace_matches_frozen_parity_artifact(
    tmp_path: Path,
    trace_id: str,
    fixture_name: str,
):
    fixture = _load_parity_fixture(fixture_name)
    expected_records = fixture["payload"]["records"]

    with load_native_runtime(
        build_config=NativeRuntimeBuildConfig(output_dir=tmp_path / "build", force_rebuild=True)
    ) as runtime:
        runtime.reset_batch([NativeEpisodeConfig(seed=11_001, start_wave=1)])
        runtime.load_core_trace(0, trace_id)
        observed = [_project_result_record(runtime.snapshot_slots(slot_indices=[0]))]
        for record in expected_records[1:]:
            observed.append(_project_result_record(runtime.step_batch(_pack_action_name(record["action_name"]))))

    assert observed == [_project_expected_record(record) for record in expected_records]


def _load_parity_fixture(name: str) -> dict[str, object]:
    path = (
        Path(__file__).resolve().parents[2]
        / "fixtures"
        / "goldens"
        / "parity"
        / name
    )
    return json.loads(path.read_text(encoding="utf-8"))


def _pack_action_name(action_name: str) -> list[int]:
    if action_name == "wait":
        return [0, 0, 0, 0]
    if action_name == "toggle_protection_prayer":
        return [3, 0, 0, 0]
    raise ValueError(f"Unsupported PR5b action name {action_name!r}")


def _project_result_record(result) -> dict[str, object]:
    row = result.flat_observations[0]
    raw = reconstruct_raw_observation_from_flat(row)
    npcs = list(raw["npcs"])
    return {
        "action_accepted": bool(result.action_accepted[0]),
        "rejection_code": int(result.rejection_codes[0]),
        "terminal_code": int(result.terminal_codes[0]),
        "tick_index": observation_tick(row),
        "wave_id": observation_wave(row),
        "remaining_npcs": observation_remaining(row),
        "player_hitpoints": observation_player_hitpoints_current(row),
        "player_prayer_points": int(raw["player"]["prayer_current"]),
        "inventory_ammo": observation_consumable_value(row, "ammo_count"),
        "inventory_prayer_potions": observation_consumable_value(row, "prayer_potion_dose_count"),
        "inventory_sharks": observation_consumable_value(row, "shark_count"),
        "jad_telegraph_state": int(result.jad_telegraph_states[0]),
        "jad_hit_resolve_outcome": str(result.jad_hit_resolve_outcomes[0]),
        "run_enabled": bool(raw["player"]["running"]),
        "visible_npc_type": [str(npc["id"]) for npc in npcs],
        "visible_npc_hitpoints": [int(npc["hitpoints_current"]) for npc in npcs],
        "visible_npc_alive": [not bool(npc["dead"]) for npc in npcs],
        "visible_target_order": [int(npc["npc_index"]) for npc in npcs],
    }


def _project_expected_record(record: dict[str, object]) -> dict[str, object]:
    return {
        "action_accepted": bool(record["action_accepted"]),
        "rejection_code": int(record["rejection_code"]),
        "terminal_code": int(record["terminal_code"]),
        "tick_index": int(record["tick_index"]),
        "wave_id": int(record["wave_id"]),
        "remaining_npcs": int(record["remaining_npcs"]),
        "player_hitpoints": int(record["player_hitpoints"]),
        "player_prayer_points": int(record["player_prayer_points"]),
        "inventory_ammo": int(record["inventory_ammo"]),
        "inventory_prayer_potions": int(record["inventory_prayer_potions"]),
        "inventory_sharks": int(record["inventory_sharks"]),
        "jad_telegraph_state": int(record["jad_telegraph_state"]),
        "jad_hit_resolve_outcome": str(record["jad_hit_resolve_outcome"]),
        "run_enabled": bool(record["run_enabled"]),
        "visible_npc_type": [str(value) for value in record["visible_npc_type"]],
        "visible_npc_hitpoints": [int(value) for value in record["visible_npc_hitpoints"]],
        "visible_npc_alive": [bool(value) for value in record["visible_npc_alive"]],
        "visible_target_order": [int(value) for value in record["visible_target_order"]],
    }
