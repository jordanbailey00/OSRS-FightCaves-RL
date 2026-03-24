from __future__ import annotations

import copy
import json
from pathlib import Path

import numpy as np
import pytest

from fight_caves_rl.contracts.reward_feature_schema import REWARD_FEATURE_INDEX
from fight_caves_rl.envs.puffer_encoding import encode_observation_for_policy
from fight_caves_rl.native_runtime import (
    NativeEpisodeConfig,
    NativeRuntimeBuildConfig,
    NativeSlotDebugState,
    NativeTile,
    NativeVisibleNpcConfig,
    load_native_runtime,
)
from fight_caves_rl.replay.trace_packs import resolve_trace_pack


def test_native_snapshot_flat_observation_matches_policy_contract_for_configured_state(
    tmp_path: Path,
):
    state = NativeSlotDebugState(
        tile=NativeTile(12, 34, 0),
        hitpoints_current=512,
        prayer_current=17,
        run_energy=2500,
        running=False,
        protect_from_magic=True,
        protect_from_missiles=False,
        protect_from_melee=True,
        attack_locked=True,
        food_locked=False,
        drink_locked=True,
        combo_locked=False,
        busy_locked=True,
        sharks=7,
        prayer_potion_dose_count=12,
        ammo=321,
    )
    targets = [
        NativeVisibleNpcConfig(
            npc_index=40,
            id="tz_kih",
            tile=NativeTile(5, 5, 0),
            hitpoints_current=90,
            hitpoints_max=100,
            under_attack=True,
        ),
        NativeVisibleNpcConfig(
            npc_index=41,
            id="tztok_jad",
            tile=NativeTile(6, 7, 0),
            hitpoints_current=2500,
            hitpoints_max=2500,
            jad_telegraph_state=2,
        ),
    ]

    with load_native_runtime(
        build_config=NativeRuntimeBuildConfig(output_dir=tmp_path / "build", force_rebuild=True)
    ) as runtime:
        reset = runtime.reset_batch([NativeEpisodeConfig(seed=41_001, start_wave=1)])
        runtime.configure_slot_state(0, state)
        runtime.configure_slot_visible_targets(0, targets)
        snapshot = runtime.snapshot_slots(slot_indices=[0])

    expected_observation = copy.deepcopy(reset.observations[0])
    expected_observation["player"]["tile"] = {"x": 12, "y": 34, "level": 0}
    expected_observation["player"]["hitpoints_current"] = 512
    expected_observation["player"]["prayer_current"] = 17
    expected_observation["player"]["run_energy"] = 2500
    expected_observation["player"]["run_energy_percent"] = 25
    expected_observation["player"]["running"] = False
    expected_observation["player"]["protection_prayers"] = {
        "protect_from_magic": True,
        "protect_from_missiles": False,
        "protect_from_melee": True,
    }
    expected_observation["player"]["lockouts"] = {
        "attack_locked": True,
        "food_locked": False,
        "drink_locked": True,
        "combo_locked": False,
        "busy_locked": True,
    }
    expected_observation["player"]["consumables"] = {
        "shark_count": 7,
        "prayer_potion_dose_count": 12,
        "ammo_id": "adamant_bolts",
        "ammo_count": 321,
    }
    expected_observation["wave"]["remaining"] = 2
    expected_observation["npcs"] = [
        {
            "visible_index": 0,
            "npc_index": 40,
            "id": "tz_kih",
            "tile": {"x": 5, "y": 5, "level": 0},
            "hitpoints_current": 90,
            "hitpoints_max": 100,
            "hidden": False,
            "dead": False,
            "under_attack": True,
            "jad_telegraph_state": 0,
        },
        {
            "visible_index": 1,
            "npc_index": 41,
            "id": "tztok_jad",
            "tile": {"x": 6, "y": 7, "level": 0},
            "hitpoints_current": 2500,
            "hitpoints_max": 2500,
            "hidden": False,
            "dead": False,
            "under_attack": False,
            "jad_telegraph_state": 2,
        },
    ]

    np.testing.assert_allclose(
        snapshot.flat_observations[0],
        encode_observation_for_policy(expected_observation),
    )
    np.testing.assert_array_equal(snapshot.reward_features, np.zeros((1, 16), dtype=np.float32))


def test_native_generic_reward_features_cover_pr6_surface(tmp_path: Path):
    with load_native_runtime(
        build_config=NativeRuntimeBuildConfig(output_dir=tmp_path / "build", force_rebuild=True)
    ) as runtime:
        runtime.reset_batch(
            [
                NativeEpisodeConfig(seed=50_001, start_wave=1),
                NativeEpisodeConfig(seed=50_002, start_wave=1),
                NativeEpisodeConfig(seed=50_003, start_wave=1),
                NativeEpisodeConfig(seed=50_004, start_wave=1),
                NativeEpisodeConfig(seed=50_005, start_wave=63),
                NativeEpisodeConfig(seed=50_006, start_wave=1),
            ]
        )
        runtime.configure_slot_visible_targets(
            0,
            [NativeVisibleNpcConfig(npc_index=1, id="tz_kih", tile=NativeTile(1, 1, 0))],
        )
        runtime.configure_slot_visible_targets(
            1,
            [NativeVisibleNpcConfig(npc_index=2, id="tz_kih", tile=NativeTile(1, 1, 0))],
        )
        runtime.configure_slot_state(2, NativeSlotDebugState(hitpoints_current=500, sharks=5))
        runtime.configure_slot_visible_targets(
            2,
            [NativeVisibleNpcConfig(npc_index=3, id="tz_kih", tile=NativeTile(1, 1, 0))],
        )
        runtime.configure_slot_state(3, NativeSlotDebugState(prayer_current=20, prayer_potion_dose_count=12))
        runtime.configure_slot_visible_targets(
            3,
            [NativeVisibleNpcConfig(npc_index=4, id="tz_kih", tile=NativeTile(1, 1, 0))],
        )
        runtime.configure_slot_visible_targets(
            4,
            [
                NativeVisibleNpcConfig(
                    npc_index=5,
                    id="tztok_jad",
                    tile=NativeTile(1, 1, 0),
                    hitpoints_current=100,
                    hitpoints_max=100,
                )
            ],
        )
        runtime.configure_slot_visible_targets(
            5,
            [NativeVisibleNpcConfig(npc_index=6, id="tz_kih", tile=NativeTile(1, 1, 0))],
        )

        result = runtime.step_batch(
            [
                0, 0, 0, 0,
                1, 9, 9, 0,
                4, 0, 0, 0,
                5, 0, 0, 0,
                2, 0, 0, 0,
                2, 9, 0, 0,
            ]
        )

    expected = np.zeros((6, 16), dtype=np.float32)

    expected[0, REWARD_FEATURE_INDEX["damage_taken"]] = 25.0
    expected[0, REWARD_FEATURE_INDEX["idle_penalty_flag"]] = 1.0
    expected[0, REWARD_FEATURE_INDEX["tick_penalty_base"]] = 1.0

    expected[1, REWARD_FEATURE_INDEX["damage_taken"]] = 25.0
    expected[1, REWARD_FEATURE_INDEX["movement_progress"]] = 1.0
    expected[1, REWARD_FEATURE_INDEX["tick_penalty_base"]] = 1.0

    expected[2, REWARD_FEATURE_INDEX["food_used"]] = 1.0
    expected[2, REWARD_FEATURE_INDEX["tick_penalty_base"]] = 1.0

    expected[3, REWARD_FEATURE_INDEX["damage_taken"]] = 25.0
    expected[3, REWARD_FEATURE_INDEX["prayer_potion_used"]] = 4.0
    expected[3, REWARD_FEATURE_INDEX["tick_penalty_base"]] = 1.0

    expected[4, REWARD_FEATURE_INDEX["damage_dealt"]] = 100.0
    expected[4, REWARD_FEATURE_INDEX["npc_kill"]] = 1.0
    expected[4, REWARD_FEATURE_INDEX["jad_damage_dealt"]] = 100.0
    expected[4, REWARD_FEATURE_INDEX["jad_kill"]] = 1.0
    expected[4, REWARD_FEATURE_INDEX["cave_complete"]] = 1.0
    expected[4, REWARD_FEATURE_INDEX["tick_penalty_base"]] = 1.0

    expected[5, REWARD_FEATURE_INDEX["invalid_action"]] = 1.0
    expected[5, REWARD_FEATURE_INDEX["tick_penalty_base"]] = 1.0

    np.testing.assert_allclose(result.reward_features, expected)


@pytest.mark.parametrize(
    ("trace_id", "fixture_name", "use_trace_pack"),
    [
        ("single_wave", "oracle_single_wave.json", True),
        ("full_run", "oracle_full_run.json", False),
        ("jad_prayer_protected", "oracle_jad_prayer_protected.json", False),
        ("jad_prayer_too_late", "oracle_jad_prayer_too_late.json", False),
        ("jad_healer", "oracle_jad_healer.json", False),
        ("tzkek_split", "oracle_tzkek_split.json", False),
    ],
)
def test_native_trace_reward_features_match_frozen_semantics(
    tmp_path: Path,
    trace_id: str,
    fixture_name: str,
    use_trace_pack: bool,
):
    fixture = _load_parity_fixture(fixture_name)
    expected_records = fixture["payload"]["records"]

    with load_native_runtime(
        build_config=NativeRuntimeBuildConfig(output_dir=tmp_path / "build", force_rebuild=True)
    ) as runtime:
        runtime.reset_batch([NativeEpisodeConfig(seed=11_001, start_wave=1)])
        runtime.load_core_trace(0, trace_id)
        if use_trace_pack:
            trace_pack = resolve_trace_pack("parity_single_wave_v0")
            results = [runtime.step_batch(_pack_trace_action(step.action)) for step in trace_pack.steps]
        else:
            results = [
                runtime.step_batch(_pack_action_name(record["action_name"]))
                for record in expected_records[1:]
            ]

    observed = np.asarray([result.reward_features[0] for result in results], dtype=np.float32)
    expected = np.asarray(
        [
            _expected_reward_row(before_record, after_record)
            for before_record, after_record in zip(expected_records[:-1], expected_records[1:])
        ],
        dtype=np.float32,
    )
    np.testing.assert_allclose(observed, expected)


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
    if action_name == "attack_visible_npc":
        return [2, 0, 0, 0]
    raise ValueError(f"Unsupported PR6 fixture action name {action_name!r}")


def _pack_trace_action(action) -> list[int]:
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


def _expected_reward_row(before_record: dict[str, object], after_record: dict[str, object]) -> np.ndarray:
    row = np.zeros((16,), dtype=np.float32)
    before_total_npc_hitpoints = sum(int(value) for value in before_record["visible_npc_hitpoints"])
    after_total_npc_hitpoints = sum(int(value) for value in after_record["visible_npc_hitpoints"])
    before_alive_count = sum(1 for value in before_record["visible_npc_alive"] if value)
    after_alive_count = sum(1 for value in after_record["visible_npc_alive"] if value)
    before_jad_hitpoints, before_jad_alive = _jad_snapshot(before_record)
    after_jad_hitpoints, after_jad_alive = _jad_snapshot(after_record)

    wave_transitioned = int(after_record["wave_id"]) > int(before_record["wave_id"]) and int(before_record["wave_id"]) > 0
    if wave_transitioned:
        row[REWARD_FEATURE_INDEX["damage_dealt"]] = float(before_total_npc_hitpoints)
        row[REWARD_FEATURE_INDEX["npc_kill"]] = float(before_alive_count)
        row[REWARD_FEATURE_INDEX["wave_clear"]] = float(
            int(after_record["wave_id"]) - int(before_record["wave_id"])
        )
    else:
        row[REWARD_FEATURE_INDEX["damage_dealt"]] = float(
            max(before_total_npc_hitpoints - after_total_npc_hitpoints, 0)
        )
        row[REWARD_FEATURE_INDEX["npc_kill"]] = float(max(before_alive_count - after_alive_count, 0))
        row[REWARD_FEATURE_INDEX["wave_clear"]] = (
            1.0
            if int(before_record["remaining_npcs"]) > 0 and int(after_record["remaining_npcs"]) == 0
            else 0.0
        )

    row[REWARD_FEATURE_INDEX["damage_taken"]] = float(
        max(int(before_record["player_hitpoints"]) - int(after_record["player_hitpoints"]), 0)
    )
    row[REWARD_FEATURE_INDEX["jad_damage_dealt"]] = float(
        max(before_jad_hitpoints - after_jad_hitpoints, 0)
    )
    row[REWARD_FEATURE_INDEX["jad_kill"]] = 1.0 if before_jad_alive and not after_jad_alive else 0.0
    row[REWARD_FEATURE_INDEX["player_death"]] = (
        1.0 if int(after_record["terminal_code"]) == 1 else 0.0
    )
    row[REWARD_FEATURE_INDEX["cave_complete"]] = (
        1.0 if int(after_record["terminal_code"]) == 2 else 0.0
    )
    row[REWARD_FEATURE_INDEX["food_used"]] = float(
        max(int(before_record["inventory_sharks"]) - int(after_record["inventory_sharks"]), 0)
    )
    row[REWARD_FEATURE_INDEX["prayer_potion_used"]] = float(
        max(
            int(before_record["inventory_prayer_potions"])
            - int(after_record["inventory_prayer_potions"]),
            0,
        )
    )
    outcome = str(after_record["jad_hit_resolve_outcome"])
    if outcome == "protected":
        row[REWARD_FEATURE_INDEX["correct_jad_prayer_on_resolve"]] = 1.0
    elif outcome == "hit":
        row[REWARD_FEATURE_INDEX["wrong_jad_prayer_on_resolve"]] = 1.0
    row[REWARD_FEATURE_INDEX["invalid_action"]] = 0.0 if bool(after_record["action_accepted"]) else 1.0
    row[REWARD_FEATURE_INDEX["movement_progress"]] = (
        1.0 if str(after_record["action_name"]) == "walk_to_tile" else 0.0
    )
    row[REWARD_FEATURE_INDEX["idle_penalty_flag"]] = (
        1.0 if str(after_record["action_name"]) == "wait" else 0.0
    )
    row[REWARD_FEATURE_INDEX["tick_penalty_base"]] = 1.0
    return row


def _jad_snapshot(record: dict[str, object]) -> tuple[int, bool]:
    jad_hitpoints = 0
    jad_alive = False
    for npc_type, hitpoints, alive in zip(
        record["visible_npc_type"],
        record["visible_npc_hitpoints"],
        record["visible_npc_alive"],
    ):
        if str(npc_type) != "tztok_jad":
            continue
        jad_hitpoints = max(jad_hitpoints, int(hitpoints))
        jad_alive = jad_alive or bool(alive)
    return jad_hitpoints, jad_alive
