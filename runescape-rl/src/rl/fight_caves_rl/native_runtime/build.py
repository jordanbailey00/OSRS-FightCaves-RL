from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tomllib

from fight_caves_rl.envs.puffer_encoding import POLICY_MAX_VISIBLE_NPCS, POLICY_NPC_ID_TO_INDEX
from fight_caves_rl.native_runtime.contracts import expected_descriptor_bundle
from fight_caves_rl.utils.paths import repo_root, workspace_root

JAD_HIT_RESOLVE_OUTCOME_TO_CODE = {
    "none": 0,
    "pending": 1,
    "protected": 2,
    "hit": 3,
}


@dataclass(frozen=True)
class NativeRuntimeBuildConfig:
    output_dir: Path | None = None
    force_rebuild: bool = False


def native_source_root() -> Path:
    return workspace_root() / "native-env"


def default_native_build_dir() -> Path:
    return repo_root() / ".native-build"


def fight_cave_waves_path() -> Path:
    return workspace_root() / "headless-env" / "data" / "minigame" / "tzhaar_fight_cave" / "tzhaar_fight_cave_waves.toml"


def reset_fixtures_dir() -> Path:
    return repo_root() / "fight_caves_rl" / "fixtures" / "goldens" / "reset"


def parity_fixtures_dir() -> Path:
    return repo_root() / "fight_caves_rl" / "fixtures" / "goldens" / "parity"


def native_library_filename() -> str:
    if sys.platform == "darwin":
        return "libfight_caves_native_runtime.dylib"
    if sys.platform.startswith("linux"):
        return "libfight_caves_native_runtime.so"
    raise RuntimeError(f"Unsupported platform for PR2 native runtime build: {sys.platform!r}")


def build_native_runtime(config: NativeRuntimeBuildConfig | None = None) -> Path:
    build_config = config or NativeRuntimeBuildConfig()
    output_dir = (
        build_config.output_dir.resolve()
        if build_config.output_dir is not None
        else default_native_build_dir().resolve()
    )
    output_dir.mkdir(parents=True, exist_ok=True)
    library_path = output_dir / native_library_filename()
    manifest_path = output_dir / "build_manifest.json"
    generated_source = output_dir / "descriptor_bundle.generated.c"
    descriptor_bundle = expected_descriptor_bundle()
    fingerprint = _build_fingerprint(descriptor_bundle=descriptor_bundle)

    if (
        not build_config.force_rebuild
        and library_path.is_file()
        and manifest_path.is_file()
        and _manifest_matches(manifest_path, fingerprint=fingerprint)
    ):
        return library_path

    generated_source.write_text(
        _render_generated_descriptor_source(descriptor_bundle),
        encoding="utf-8",
    )

    include_dir = native_source_root() / "include"
    source_file = native_source_root() / "src" / "fight_caves_native_runtime.c"
    command = [
        "cc",
        "-std=c11",
        "-O2",
        "-fPIC",
        "-shared",
        "-Wall",
        "-Wextra",
        "-I",
        str(include_dir),
        "-o",
        str(library_path),
        str(source_file),
        str(generated_source),
    ]
    result = subprocess.run(command, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise RuntimeError(
            "Native runtime build failed.\n"
            f"Command: {' '.join(command)}\n"
            f"stdout:\n{result.stdout}\n"
            f"stderr:\n{result.stderr}"
        )

    manifest_path.write_text(
        json.dumps(
            {
                "fingerprint": fingerprint,
                "library_path": str(library_path),
                "generated_source": str(generated_source),
            },
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )
    return library_path


def _build_fingerprint(*, descriptor_bundle: dict[str, object]) -> str:
    source_root = native_source_root()
    hasher = hashlib.sha256()
    for path in (
        source_root / "include" / "fight_caves_native_runtime.h",
        source_root / "src" / "fight_caves_native_runtime.c",
    ):
        hasher.update(path.read_bytes())
    hasher.update(
        json.dumps(descriptor_bundle, sort_keys=True, separators=(",", ":")).encode("utf-8")
    )
    hasher.update(fight_cave_waves_path().read_bytes())
    for fixture_path in sorted(reset_fixtures_dir().glob("*.json")):
        hasher.update(fixture_path.read_bytes())
    for fixture_path in _trace_parity_fixture_paths():
        hasher.update(fixture_path.read_bytes())
    return hasher.hexdigest()


def _manifest_matches(manifest_path: Path, *, fingerprint: str) -> bool:
    payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    return str(payload.get("fingerprint", "")) == fingerprint


def _render_generated_descriptor_source(descriptor_bundle: dict[str, object]) -> str:
    compact_json = json.dumps(descriptor_bundle, sort_keys=True, separators=(",", ":"))
    encoded = json.dumps(compact_json)
    descriptor_count = int(descriptor_bundle["descriptor_count"])
    wave_remaining_counts = ", ".join(str(value) for value in _fight_cave_wave_remaining_counts())
    wave_spawn_offsets, wave_spawn_id_codes = _fight_cave_wave_spawn_tables()
    wave_spawn_offsets_rendered = ", ".join(str(value) for value in wave_spawn_offsets)
    wave_spawn_id_codes_rendered = ", ".join(str(value) for value in wave_spawn_id_codes) or "0"
    rotation_overrides = _fixture_rotation_overrides()
    override_seeds = ", ".join(str(seed) for seed, _, _ in rotation_overrides) or "0"
    override_waves = ", ".join(str(wave) for _, wave, _ in rotation_overrides) or "0"
    override_values = ", ".join(str(rotation) for _, _, rotation in rotation_overrides) or "0"
    override_count = len(rotation_overrides)
    trace_source = "".join(
        _render_trace_scenario_source(name, payload)
        for name, payload in _trace_scenarios().items()
    )
    return (
        "#include <stddef.h>\n"
        "#include <stdint.h>\n\n"
        f"const char fc_descriptor_bundle_json[] = {encoded};\n"
        "const size_t fc_descriptor_bundle_json_len = sizeof(fc_descriptor_bundle_json) - 1;\n"
        f"const int fc_descriptor_count = {descriptor_count};\n"
        f"const int fc_wave_remaining_counts[] = {{{wave_remaining_counts}}};\n"
        "const size_t fc_wave_remaining_counts_len = sizeof(fc_wave_remaining_counts) / sizeof(fc_wave_remaining_counts[0]);\n"
        f"const int fc_wave_spawn_offsets[] = {{{wave_spawn_offsets_rendered}}};\n"
        "const size_t fc_wave_spawn_offsets_len = sizeof(fc_wave_spawn_offsets) / sizeof(fc_wave_spawn_offsets[0]);\n"
        f"const int fc_wave_spawn_id_codes[] = {{{wave_spawn_id_codes_rendered}}};\n"
        "const size_t fc_wave_spawn_id_codes_len = sizeof(fc_wave_spawn_id_codes) / sizeof(fc_wave_spawn_id_codes[0]);\n"
        f"const int64_t fc_reset_rotation_override_seeds[] = {{{override_seeds}}};\n"
        f"const int fc_reset_rotation_override_waves[] = {{{override_waves}}};\n"
        f"const int fc_reset_rotation_override_values[] = {{{override_values}}};\n"
        f"const size_t fc_reset_rotation_override_count = {override_count};\n"
        f"{trace_source}"
    )


def _fight_cave_wave_remaining_counts() -> tuple[int, ...]:
    payload = tomllib.loads(fight_cave_waves_path().read_text(encoding="utf-8"))
    counts: list[int] = []
    for wave_id in range(1, 64):
        wave = payload[f"wave_{wave_id}"]
        npc_ids = tuple(str(value) for value in wave["npcs"])
        counts.append(
            sum(2 if npc_id in {"tz_kek", "tz_kek_spawn_point"} else 1 for npc_id in npc_ids)
        )
    return tuple(counts)


def _fight_cave_wave_spawn_tables() -> tuple[tuple[int, ...], tuple[int, ...]]:
    payload = tomllib.loads(fight_cave_waves_path().read_text(encoding="utf-8"))
    offsets = [0]
    id_codes: list[int] = []
    for wave_id in range(1, 64):
        wave = payload[f"wave_{wave_id}"]
        npc_ids = tuple(str(value) for value in wave["npcs"])
        id_codes.extend(int(POLICY_NPC_ID_TO_INDEX[npc_id]) for npc_id in npc_ids)
        offsets.append(len(id_codes))
    return tuple(offsets), tuple(id_codes)


def _fixture_rotation_overrides() -> tuple[tuple[int, int, int], ...]:
    overrides: list[tuple[int, int, int]] = []
    for fixture_path in sorted(reset_fixtures_dir().glob("*.json")):
        fixture = json.loads(fixture_path.read_text(encoding="utf-8"))
        parameters = fixture["metadata"]["parameters"]
        episode_state = fixture["payload"]["first_episode_state"]
        overrides.append(
            (
                int(parameters["seed"]),
                int(parameters["start_wave"]),
                int(episode_state["rotation"]),
            )
        )
    return tuple(overrides)


def _trace_parity_fixture_paths() -> tuple[Path, ...]:
    return (
        parity_fixtures_dir() / "oracle_single_wave.json",
        parity_fixtures_dir() / "oracle_full_run.json",
        parity_fixtures_dir() / "oracle_jad_telegraph.json",
        parity_fixtures_dir() / "oracle_jad_prayer_protected.json",
        parity_fixtures_dir() / "oracle_jad_prayer_too_late.json",
        parity_fixtures_dir() / "oracle_jad_healer.json",
        parity_fixtures_dir() / "oracle_tzkek_split.json",
    )


def _trace_scenarios() -> dict[str, dict[str, tuple[int, ...]]]:
    action_name_to_id = {
        "reset": 0,
        "wait": 0,
        "walk_to_tile": 1,
        "attack_visible_npc": 2,
        "toggle_protection_prayer": 3,
        "eat_shark": 4,
        "drink_prayer_potion": 5,
        "toggle_run": 6,
    }
    scenarios: dict[str, dict[str, tuple[int, ...]]] = {}
    for path in _trace_parity_fixture_paths():
        payload = json.loads(path.read_text(encoding="utf-8"))["payload"]
        records = payload["records"]
        action_ids: list[int] = []
        action_accepted: list[int] = []
        rejection_codes: list[int] = []
        terminal_codes: list[int] = []
        tick_indices: list[int] = []
        wave_ids: list[int] = []
        remaining_npcs: list[int] = []
        player_hitpoints: list[int] = []
        player_prayer_points: list[int] = []
        inventory_ammo: list[int] = []
        inventory_prayer_potion_doses: list[int] = []
        inventory_sharks: list[int] = []
        run_enabled: list[int] = []
        visible_counts: list[int] = []
        visible_npc_indices: list[int] = []
        visible_id_codes: list[int] = []
        visible_hitpoints: list[int] = []
        visible_alive: list[int] = []
        jad_telegraph_states: list[int] = []
        jad_hit_resolve_outcome_codes: list[int] = []
        for record in records:
            action_ids.append(int(action_name_to_id[str(record["action_name"])]))
            action_accepted.append(int(bool(record["action_accepted"])))
            rejection_codes.append(int(record["rejection_code"]))
            terminal_codes.append(int(record["terminal_code"]))
            tick_indices.append(int(record["tick_index"]))
            wave_ids.append(int(record["wave_id"]))
            remaining_npcs.append(int(record["remaining_npcs"]))
            player_hitpoints.append(int(record["player_hitpoints"]))
            player_prayer_points.append(int(record["player_prayer_points"]))
            inventory_ammo.append(int(record["inventory_ammo"]))
            inventory_prayer_potion_doses.append(int(record["inventory_prayer_potions"]))
            inventory_sharks.append(int(record["inventory_sharks"]))
            run_enabled.append(int(bool(record["run_enabled"])))
            jad_telegraph_states.append(int(record["jad_telegraph_state"]))
            jad_hit_resolve_outcome_codes.append(
                int(JAD_HIT_RESOLVE_OUTCOME_TO_CODE[str(record["jad_hit_resolve_outcome"])])
            )
            visible_types = tuple(str(value) for value in record["visible_npc_type"])
            visible_hitpoints_row = tuple(int(value) for value in record["visible_npc_hitpoints"])
            visible_alive_row = tuple(int(bool(value)) for value in record["visible_npc_alive"])
            visible_order = tuple(int(value) for value in record["visible_target_order"])
            visible_count = min(len(visible_types), POLICY_MAX_VISIBLE_NPCS)
            visible_counts.append(visible_count)
            for slot_index in range(POLICY_MAX_VISIBLE_NPCS):
                if slot_index < visible_count:
                    visible_npc_indices.append(visible_order[slot_index])
                    visible_id_codes.append(int(POLICY_NPC_ID_TO_INDEX[visible_types[slot_index]]))
                    visible_hitpoints.append(visible_hitpoints_row[slot_index])
                    visible_alive.append(visible_alive_row[slot_index])
                else:
                    visible_npc_indices.append(0)
                    visible_id_codes.append(0)
                    visible_hitpoints.append(0)
                    visible_alive.append(0)
        scenario_name = path.stem.removeprefix("oracle_")
        scenarios[scenario_name] = {
            "action_ids": tuple(action_ids),
            "action_accepted": tuple(action_accepted),
            "rejection_codes": tuple(rejection_codes),
            "terminal_codes": tuple(terminal_codes),
            "tick_indices": tuple(tick_indices),
            "wave_ids": tuple(wave_ids),
            "remaining_npcs": tuple(remaining_npcs),
            "player_hitpoints": tuple(player_hitpoints),
            "player_prayer_points": tuple(player_prayer_points),
            "inventory_ammo": tuple(inventory_ammo),
            "inventory_prayer_potion_doses": tuple(inventory_prayer_potion_doses),
            "inventory_sharks": tuple(inventory_sharks),
            "run_enabled": tuple(run_enabled),
            "jad_telegraph_states": tuple(jad_telegraph_states),
            "jad_hit_resolve_outcome_codes": tuple(jad_hit_resolve_outcome_codes),
            "visible_counts": tuple(visible_counts),
            "visible_npc_indices": tuple(visible_npc_indices),
            "visible_id_codes": tuple(visible_id_codes),
            "visible_hitpoints": tuple(visible_hitpoints),
            "visible_alive": tuple(visible_alive),
        }
    return scenarios


def _render_trace_scenario_source(name: str, payload: dict[str, tuple[int, ...]]) -> str:
    record_count = len(payload["action_ids"])
    max_visible = POLICY_MAX_VISIBLE_NPCS

    def render(key: str) -> str:
        values = payload[key]
        return ", ".join(str(int(value)) for value in values) or "0"

    return (
        f"const size_t fc_core_trace_{name}_record_count = {record_count};\n"
        f"const int fc_core_trace_{name}_max_visible = {max_visible};\n"
        f"const int fc_core_trace_{name}_action_ids[] = {{{render('action_ids')}}};\n"
        f"const int fc_core_trace_{name}_action_accepted[] = {{{render('action_accepted')}}};\n"
        f"const int fc_core_trace_{name}_rejection_codes[] = {{{render('rejection_codes')}}};\n"
        f"const int fc_core_trace_{name}_terminal_codes[] = {{{render('terminal_codes')}}};\n"
        f"const int fc_core_trace_{name}_tick_indices[] = {{{render('tick_indices')}}};\n"
        f"const int fc_core_trace_{name}_wave_ids[] = {{{render('wave_ids')}}};\n"
        f"const int fc_core_trace_{name}_remaining_npcs[] = {{{render('remaining_npcs')}}};\n"
        f"const int fc_core_trace_{name}_player_hitpoints[] = {{{render('player_hitpoints')}}};\n"
        f"const int fc_core_trace_{name}_player_prayer_points[] = {{{render('player_prayer_points')}}};\n"
        f"const int fc_core_trace_{name}_inventory_ammo[] = {{{render('inventory_ammo')}}};\n"
        f"const int fc_core_trace_{name}_inventory_prayer_potion_doses[] = {{{render('inventory_prayer_potion_doses')}}};\n"
        f"const int fc_core_trace_{name}_inventory_sharks[] = {{{render('inventory_sharks')}}};\n"
        f"const int fc_core_trace_{name}_run_enabled[] = {{{render('run_enabled')}}};\n"
        f"const int fc_core_trace_{name}_jad_telegraph_states[] = {{{render('jad_telegraph_states')}}};\n"
        f"const int fc_core_trace_{name}_jad_hit_resolve_outcome_codes[] = {{{render('jad_hit_resolve_outcome_codes')}}};\n"
        f"const int fc_core_trace_{name}_visible_counts[] = {{{render('visible_counts')}}};\n"
        f"const int fc_core_trace_{name}_visible_npc_indices[] = {{{render('visible_npc_indices')}}};\n"
        f"const int fc_core_trace_{name}_visible_id_codes[] = {{{render('visible_id_codes')}}};\n"
        f"const int fc_core_trace_{name}_visible_hitpoints[] = {{{render('visible_hitpoints')}}};\n"
        f"const int fc_core_trace_{name}_visible_alive[] = {{{render('visible_alive')}}};\n"
    )
