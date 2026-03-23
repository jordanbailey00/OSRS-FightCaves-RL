from __future__ import annotations

import json
from pathlib import Path

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
from fight_caves_rl.replay.trace_packs import resolve_trace_pack


def test_native_single_wave_core_trace_matches_frozen_parity_fields(tmp_path: Path):
    fixture = _load_parity_fixture("oracle_single_wave.json")
    trace_pack = resolve_trace_pack("parity_single_wave_v0")

    with load_native_runtime(
        build_config=NativeRuntimeBuildConfig(output_dir=tmp_path / "build", force_rebuild=True)
    ) as runtime:
        runtime.reset_batch([NativeEpisodeConfig(seed=11_001, start_wave=1)])
        runtime.load_core_trace(0, "single_wave")
        observed = [
            _project_step_record(
                runtime.step_batch(_pack_action(step.action)),
            )
            for step in trace_pack.steps
        ]

    assert observed == [_project_expected_record(record) for record in fixture["payload"]["records"][1:]]


def test_native_full_run_core_trace_matches_frozen_parity_fields(tmp_path: Path):
    fixture = _load_parity_fixture("oracle_full_run.json")
    expected_records = fixture["payload"]["records"][1:]

    with load_native_runtime(
        build_config=NativeRuntimeBuildConfig(output_dir=tmp_path / "build", force_rebuild=True)
    ) as runtime:
        runtime.reset_batch([NativeEpisodeConfig(seed=11_001, start_wave=1)])
        runtime.load_core_trace(0, "full_run")
        observed = [
            _project_step_record(runtime.step_batch([0, 0, 0, 0]))
            for _ in expected_records
        ]

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


def _pack_action(action) -> list[int]:
    packed = [int(action.action_id), 0, 0, 0]
    if action.action_id == 1 and action.tile is not None:
        packed[1] = int(action.tile.x)
        packed[2] = int(action.tile.y)
        packed[3] = int(action.tile.level)
    elif action.action_id == 2 and action.visible_npc_index is not None:
        packed[1] = int(action.visible_npc_index)
    elif action.action_id == 3 and action.prayer is not None:
        packed[1] = {
            "protect_from_magic": 0,
            "protect_from_missiles": 1,
            "protect_from_melee": 2,
        }[str(action.prayer)]
    return packed


def _project_step_record(result) -> dict[str, object]:
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
        "run_enabled": bool(record["run_enabled"]),
        "visible_npc_type": [str(value) for value in record["visible_npc_type"]],
        "visible_npc_hitpoints": [int(value) for value in record["visible_npc_hitpoints"]],
        "visible_npc_alive": [bool(value) for value in record["visible_npc_alive"]],
        "visible_target_order": [int(value) for value in record["visible_target_order"]],
    }
