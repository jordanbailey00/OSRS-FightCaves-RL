from __future__ import annotations

import json
import subprocess
from pathlib import Path

from fight_caves_rl.native_runtime import (
    NativeViewerBuildConfig,
    build_native_debug_viewer,
    native_viewer_filename,
)
from fight_caves_rl.replay.native_viewer_replay import (
    build_native_viewer_replay_from_replay_pack,
    build_native_viewer_replay_from_trace_pack,
    write_native_viewer_replay,
)


def test_native_debug_viewer_builds_and_exports_generic_scene_json(tmp_path: Path):
    build_dir = tmp_path / "native-viewer-build"
    viewer_path = build_native_debug_viewer(
        NativeViewerBuildConfig(output_dir=build_dir, force_rebuild=True)
    )

    assert viewer_path.name == native_viewer_filename()
    assert viewer_path.is_file()
    assert (build_dir / "fight_caves_header.png").is_file()
    assert (build_dir / "fight_caves_scene_slice_v1.json").is_file()
    assert (build_dir / "fight_caves_export_manifest_v1.json").is_file()
    assert (build_dir / "sprites" / "pray_magic_127_0.png").is_file()

    result = subprocess.run(
        [
            str(viewer_path),
            "--dump-scene-json",
            "--start-wave",
            "63",
            "--seed",
            "63063",
        ],
        cwd=str(build_dir),
        text=True,
        capture_output=True,
        check=False,
    )
    assert result.returncode == 0, result.stderr
    payload = json.loads(result.stdout)
    assert payload["schema_id"] == "fc_native_debug_viewer_scene_v1"
    assert payload["trace_id"] is None
    assert payload["scene"]["rect_count"] >= 6
    assert payload["assets"]["asset_id"] == "fight_caves_scene_slice_v1"
    assert payload["assets"]["terrain_tile_count"] >= 5000
    assert payload["assets"]["hud_sprite_count"] >= 20
    assert payload["assets"]["map_archive_accessible"] == 1
    assert payload["assets"]["object_archive_accessible"] == 0
    assert payload["snapshot"]["wave"] == 63
    assert payload["snapshot"]["player"]["hitpoints_current"] == 700
    assert payload["snapshot"]["inventory"]["ammo"] == 1000
    assert payload["viewer"]["last_action"]["present"] == 0


def test_native_debug_viewer_can_export_trace_replay_scene_json(tmp_path: Path):
    build_dir = tmp_path / "native-viewer-build"
    viewer_path = build_native_debug_viewer(
        NativeViewerBuildConfig(output_dir=build_dir, force_rebuild=True)
    )

    result = subprocess.run(
        [
            str(viewer_path),
            "--dump-scene-json",
            "--trace",
            "jad_healer",
            "--step-count",
            "1",
        ],
        cwd=str(build_dir),
        text=True,
        capture_output=True,
        check=False,
    )
    assert result.returncode == 0, result.stderr
    payload = json.loads(result.stdout)
    assert payload["trace_id"] == "jad_healer"
    assert payload["snapshot"]["steps"] == 1
    assert payload["snapshot"]["wave"] == 63
    assert payload["snapshot"]["jad_hit_resolve_outcome"] == "pending"
    assert payload["viewer"]["last_action"]["present"] == 1
    assert payload["viewer"]["last_action"]["action_label"] == "wait"
    assert payload["snapshot"]["terminal_label"] in {
        "none",
        "player_death",
        "cave_complete",
        "tick_cap",
        "invalid_state",
    }


def test_native_debug_viewer_can_apply_scripted_state_actions(tmp_path: Path):
    build_dir = tmp_path / "native-viewer-build"
    viewer_path = build_native_debug_viewer(
        NativeViewerBuildConfig(output_dir=build_dir, force_rebuild=True)
    )

    initial = subprocess.run(
        [
            str(viewer_path),
            "--dump-scene-json",
            "--start-wave",
            "63",
            "--seed",
            "63063",
        ],
        cwd=str(build_dir),
        text=True,
        capture_output=True,
        check=False,
    )
    assert initial.returncode == 0, initial.stderr
    initial_payload = json.loads(initial.stdout)

    result = subprocess.run(
        [
            str(viewer_path),
            "--dump-scene-json",
            "--start-wave",
            "63",
            "--seed",
            "63063",
            "--apply-action",
            "prayer:magic",
            "--apply-action",
            "eat",
            "--apply-action",
            "drink",
            "--apply-action",
            "run",
        ],
        cwd=str(build_dir),
        text=True,
        capture_output=True,
        check=False,
    )
    assert result.returncode == 0, result.stderr
    payload = json.loads(result.stdout)
    assert payload["snapshot"]["player"]["protect_from_magic"] == 1
    assert (
        payload["snapshot"]["inventory"]["sharks"]
        == initial_payload["snapshot"]["inventory"]["sharks"] - 1
    )
    assert (
        payload["snapshot"]["inventory"]["prayer_potion_dose_count"]
        == initial_payload["snapshot"]["inventory"]["prayer_potion_dose_count"] - 4
    )
    assert (
        payload["snapshot"]["player"]["running"]
        != initial_payload["snapshot"]["player"]["running"]
    )
    assert payload["viewer"]["last_action"]["action_label"] == "toggle_run"
    assert payload["viewer"]["last_action"]["accepted"] == 1


def test_native_debug_viewer_can_apply_scripted_move_and_attack_actions(tmp_path: Path):
    build_dir = tmp_path / "native-viewer-build"
    viewer_path = build_native_debug_viewer(
        NativeViewerBuildConfig(output_dir=build_dir, force_rebuild=True)
    )

    initial = subprocess.run(
        [
            str(viewer_path),
            "--dump-scene-json",
            "--start-wave",
            "63",
            "--seed",
            "63063",
        ],
        cwd=str(build_dir),
        text=True,
        capture_output=True,
        check=False,
    )
    assert initial.returncode == 0, initial.stderr
    initial_payload = json.loads(initial.stdout)
    next_tile_x = initial_payload["snapshot"]["player"]["tile_x"] + 1
    next_tile_y = initial_payload["snapshot"]["player"]["tile_y"]
    tile_level = initial_payload["snapshot"]["player"]["tile_level"]

    moved = subprocess.run(
        [
            str(viewer_path),
            "--dump-scene-json",
            "--start-wave",
            "63",
            "--seed",
            "63063",
            "--apply-action",
            f"move:{next_tile_x}:{next_tile_y}:{tile_level}",
        ],
        cwd=str(build_dir),
        text=True,
        capture_output=True,
        check=False,
    )
    assert moved.returncode == 0, moved.stderr
    moved_payload = json.loads(moved.stdout)
    assert moved_payload["snapshot"]["player"]["tile_x"] == next_tile_x
    assert moved_payload["viewer"]["last_action"]["action_label"] == "walk_to_tile"
    assert moved_payload["viewer"]["last_action"]["accepted"] == 1

    attacked = subprocess.run(
        [
            str(viewer_path),
            "--dump-scene-json",
            "--trace",
            "jad_healer",
            "--step-count",
            "1",
            "--apply-action",
            "attack-visible:0",
        ],
        cwd=str(build_dir),
        text=True,
        capture_output=True,
        check=False,
    )
    assert attacked.returncode == 0, attacked.stderr
    attack_payload = json.loads(attacked.stdout)
    assert attack_payload["viewer"]["last_action"]["action_label"] == "attack_visible_npc"
    assert attack_payload["viewer"]["last_action"]["accepted"] == 1
    assert attack_payload["snapshot"]["steps"] == 2
    assert attack_payload["snapshot"]["jad_hit_resolve_outcome"] == "pending"


def test_native_debug_viewer_can_load_trace_pack_replay_files(tmp_path: Path):
    build_dir = tmp_path / "native-viewer-build"
    viewer_path = build_native_debug_viewer(
        NativeViewerBuildConfig(output_dir=build_dir, force_rebuild=True)
    )
    replay_path = write_native_viewer_replay(
        tmp_path / "jad_healer.viewer_replay",
        build_native_viewer_replay_from_trace_pack("parity_jad_healer_v0"),
    )

    result = subprocess.run(
        [
            str(viewer_path),
            "--dump-scene-json",
            "--replay-file",
            str(replay_path),
            "--replay-frame",
            "2",
        ],
        cwd=str(build_dir),
        text=True,
        capture_output=True,
        check=False,
    )
    assert result.returncode == 0, result.stderr
    payload = json.loads(result.stdout)
    assert payload["trace_id"] is None
    assert payload["replay"]["loaded"] == 1
    assert payload["replay"]["source_kind"] == "trace_pack"
    assert payload["replay"]["source_ref"] == "parity_jad_healer_v0"
    assert payload["replay"]["frame_index"] == 2
    assert payload["replay"]["frame_count"] >= 3
    assert payload["snapshot"]["wave"] == 63
    assert payload["snapshot"]["tick"] == 2
    assert payload["viewer"]["last_action"]["present"] == 1
    assert payload["viewer"]["last_action"]["action_label"] == "wait"
    assert payload["snapshot"]["visible_target_count"] >= 1


def test_native_debug_viewer_can_load_replay_pack_exports(tmp_path: Path):
    build_dir = tmp_path / "native-viewer-build"
    viewer_path = build_native_debug_viewer(
        NativeViewerBuildConfig(output_dir=build_dir, force_rebuild=True)
    )
    replay_pack_path = tmp_path / "minimal_replay_pack.json"
    replay_pack_path.write_text(
        json.dumps(
            {
                "episodes": [
                    {
                        "seed": 63063,
                        "semantic_episode_state": {"wave": 63},
                        "episode_reset_summary": {
                            "wave": 63,
                            "ammo": 1000,
                            "prayer_potions": 8,
                            "sharks": 20,
                        },
                        "steps": [
                            {"action": [0, 0, 0, 0, 7, 8]},
                            {"action": [2, 0, 0, 0, 9, 10]},
                        ],
                    }
                ]
            },
            indent=2,
            sort_keys=True,
        ),
        encoding="utf-8",
    )
    replay_path = write_native_viewer_replay(
        tmp_path / "minimal.viewer_replay",
        build_native_viewer_replay_from_replay_pack(replay_pack_path),
    )

    result = subprocess.run(
        [
            str(viewer_path),
            "--dump-scene-json",
            "--replay-file",
            str(replay_path),
            "--replay-frame",
            "2",
        ],
        cwd=str(build_dir),
        text=True,
        capture_output=True,
        check=False,
    )
    assert result.returncode == 0, result.stderr
    payload = json.loads(result.stdout)
    assert payload["replay"]["loaded"] == 1
    assert payload["replay"]["source_kind"] == "replay_pack"
    assert payload["replay"]["source_ref"] == "minimal_replay_pack_episode_0"
    assert payload["replay"]["frame_index"] == 2
    assert payload["replay"]["frame_count"] == 3
    assert payload["viewer"]["last_action"]["present"] == 1
    assert payload["viewer"]["last_action"]["action_label"] == "attack_visible_npc"
    assert payload["viewer"]["last_action"]["accepted"] == 1
    assert payload["viewer"]["last_action"]["resolved_target_npc_index"] == 1
