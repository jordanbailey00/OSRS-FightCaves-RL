from __future__ import annotations

import csv
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import textwrap
import tomllib
import zlib

from fight_caves_rl.native_runtime.osrs_cache_dat2 import Dat2CacheReader, djb2
from fight_caves_rl.utils.paths import legacy_headless_env_root, legacy_rsps_root, workspace_root


FIGHT_CAVES_CACHE_REGIONS: tuple[tuple[int, int], ...] = (
    (37, 79),
    (37, 80),
    (38, 80),
    (39, 80),
)

VIEWER_ASSET_BUNDLE_ID = "fight_caves_scene_slice_v1"
VIEWER_CACHE_INPUT_RELATIVE = Path("reference/legacy-headless-env/data/cache")
VIEWER_EMPTY_RSPS_CACHE_RELATIVE = Path("reference/legacy-rsps/data/cache")
VIEWER_XTEA_TEST_INPUTS: tuple[Path, ...] = (
    Path("reference/legacy-rsps/tools/src/test/resources/xteas/xteas.json"),
    Path("reference/legacy-rsps/tools/src/test/resources/xteas/xteas-default.json"),
)
FIGHT_CAVES_OBJECT_ARCHIVE_REGIONS: tuple[int, ...] = (
    9551,
    9552,
)

SPRITE_EXPORTS: tuple[tuple[str, int], ...] = (
    ("pray_magic", 127),
    ("pray_missiles", 128),
    ("pray_melee", 129),
    ("panel_side_background", 1031),
    ("panel_tabs_bottom", 1032),
    ("panel_tabs_top", 1036),
    ("click_cross_move_0", 515),
    ("click_cross_move_1", 516),
    ("click_cross_move_2", 517),
    ("click_cross_move_3", 518),
    ("click_cross_attack_0", 519),
    ("click_cross_attack_1", 520),
    ("click_cross_attack_2", 521),
    ("click_cross_attack_3", 522),
    ("wave_digit_0", 4056),
    ("wave_digit_1", 4057),
    ("wave_digit_2", 4058),
    ("wave_digit_3", 4059),
    ("wave_digit_4", 4060),
    ("wave_digit_5", 4061),
    ("wave_digit_6", 4062),
    ("inventory_slot", 170),
    ("inventory_slot_frame_a", 821),
    ("inventory_slot_frame_b", 822),
)

FIGHT_CAVES_ITEM_EXPORTS: tuple[tuple[str, int], ...] = (
    ("vial", 229),
    ("shark", 385),
    ("prayer_potion_4", 2434),
    ("coif", 1169),
    ("black_dragonhide_vambraces", 2491),
    ("black_dragonhide_chaps", 2497),
    ("black_dragonhide_body", 2503),
    ("snakeskin_boots", 6328),
    ("adamant_bolts", 9143),
    ("rune_crossbow", 9185),
)

FIGHT_CAVES_NPC_EXPORTS: tuple[tuple[str, int], ...] = (
    ("tz_kih", 2734),
    ("tz_kih_spawn_point", 2735),
    ("tz_kek", 2736),
    ("tz_kek_spawn_point", 2737),
    ("tz_kek_spawn", 2738),
    ("tok_xil", 2739),
    ("tok_xil_spawn_point", 2740),
    ("yt_mej_kot", 2741),
    ("yt_mej_kot_spawn_point", 2742),
    ("ket_zek", 2743),
    ("ket_zek_spawn_point", 2744),
    ("tztok_jad", 2745),
    ("yt_hur_kot", 2746),
)

FIGHT_CAVES_INTERFACE_EXPORTS: tuple[int, ...] = (
    316,
    149,
    271,
    387,
    541,
    749,
)

FIGHT_CAVES_OBJECT_EXPORTS: tuple[tuple[str, int], ...] = (
    ("lava_forge", 9390),
    ("cave_entrance_fight_cave", 9356),
    ("cave_exit_fight_cave", 9357),
    ("cave_entrance_tzhaar_city", 31284),
    ("cave_entrance_library", 31292),
    ("cave_exit_tzhaar_city", 9359),
)


def generated_viewer_assets_root() -> Path:
    return workspace_root() / "demo-env" / "assets" / "generated"


def generated_viewer_bundle_path() -> Path:
    return generated_viewer_assets_root() / "fight_caves_scene_slice_v1.json"


def generated_viewer_manifest_path() -> Path:
    return generated_viewer_assets_root() / "fight_caves_export_manifest_v1.json"


def generated_viewer_terrain_bundle_path() -> Path:
    return generated_viewer_assets_root() / "fight_caves_terrain_slice_v1.terr"


def generated_viewer_npc_models_bundle_path() -> Path:
    return generated_viewer_assets_root() / "fight_caves_npc_models_v1.mdl2"


def generated_viewer_sprites_root() -> Path:
    return generated_viewer_assets_root() / "sprites"


def generated_viewer_models_root() -> Path:
    return generated_viewer_assets_root() / "models"


def validated_viewer_cache_input_path() -> Path:
    return (legacy_headless_env_root() / "data" / "cache").resolve()


def empty_rsps_cache_input_path() -> Path:
    return (legacy_rsps_root() / "data" / "cache").resolve()


def _fight_caves_demo_save_path() -> Path:
    return legacy_rsps_root() / "data" / "fight_caves_demo" / "saves" / "fcdemo01.toml"


def _fight_caves_region_world_bounds() -> tuple[int, int, int, int]:
    region_min_x = min(region_x for region_x, _region_y in FIGHT_CAVES_CACHE_REGIONS)
    region_max_x = max(region_x for region_x, _region_y in FIGHT_CAVES_CACHE_REGIONS)
    region_min_y = min(region_y for _region_x, region_y in FIGHT_CAVES_CACHE_REGIONS)
    region_max_y = max(region_y for _region_x, region_y in FIGHT_CAVES_CACHE_REGIONS)
    min_x = region_min_x * 64
    max_x = region_max_x * 64 + 63
    min_y = region_min_y * 64
    max_y = region_max_y * 64 + 63
    return min_x, max_x, min_y, max_y


def _ocean_osrs_pvp_scripts_root() -> Path:
    return workspace_root().parent.parent / "pufferlib" / "pufferlib" / "ocean" / "osrs_pvp" / "scripts"


def _load_ocean_script_module(module_name: str):
    scripts_root = _ocean_osrs_pvp_scripts_root()
    module_path = scripts_root / f"{module_name}.py"
    if not module_path.is_file():
        raise RuntimeError(f"Missing local Ocean reference script: {module_path}")
    if str(scripts_root) not in sys.path:
        sys.path.insert(0, str(scripts_root))
    spec = importlib.util.spec_from_file_location(f"fc_ocean_{module_name}", module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Could not load Ocean reference script: {module_path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _load_fight_caves_demo_save() -> tuple[str, dict[str, object]]:
    path = _fight_caves_demo_save_path()
    text = path.read_text(encoding="utf-8")
    return text, tomllib.loads(text)


def _viewer_xtea_candidate_payloads() -> list[dict[str, object]]:
    payloads: list[dict[str, object]] = []
    repo_workspace_root = workspace_root().parent.parent
    for relative_path in VIEWER_XTEA_TEST_INPUTS:
        path = repo_workspace_root / relative_path
        present = path.is_file()
        contains_fight_caves_regions = False
        regions: list[int] = []
        if present:
            try:
                raw = json.loads(path.read_text(encoding="utf-8"))
                if isinstance(raw, list):
                    for entry in raw:
                        if not isinstance(entry, dict):
                            continue
                        region_id = entry.get("mapsquare", entry.get("id"))
                        if isinstance(region_id, int):
                            regions.append(region_id)
                contains_fight_caves_regions = all(
                    region_id in regions for region_id in FIGHT_CAVES_OBJECT_ARCHIVE_REGIONS
                )
            except json.JSONDecodeError:
                contains_fight_caves_regions = False
        payloads.append(
            {
                "path": str(relative_path),
                "present": present,
                "contains_fight_caves_regions": contains_fight_caves_regions,
                "region_count": len(regions),
            }
        )
    return payloads


def _demo_loadout_specs() -> list[dict[str, object]]:
    _text, payload = _load_fight_caves_demo_save()
    inventories = payload.get("inventories", {})
    worn_equipment = inventories.get("worn_equipment", []) if isinstance(inventories, dict) else []
    item_lookup = {label: item_id for label, item_id in FIGHT_CAVES_ITEM_EXPORTS}
    specs: list[dict[str, object]] = []
    for slot_index, entry in enumerate(worn_equipment):
        if not isinstance(entry, dict):
            continue
        label = entry.get("id")
        if not isinstance(label, str):
            continue
        item_id = item_lookup.get(label)
        if item_id is None:
            continue
        specs.append(
            {
                "slot_index": slot_index,
                "label": label,
                "item_id": item_id,
            }
        )
    return specs


def ensure_native_viewer_asset_bundle(*, force_rebuild: bool = False) -> Path:
    generated_root = generated_viewer_assets_root()
    bundle_path = generated_viewer_bundle_path()
    manifest_path = generated_viewer_manifest_path()
    terrain_bundle_path = generated_viewer_terrain_bundle_path()
    npc_models_bundle_path = generated_viewer_npc_models_bundle_path()
    sprites_root = generated_viewer_sprites_root()
    models_root = generated_viewer_models_root()
    generated_root.mkdir(parents=True, exist_ok=True)
    sprites_root.mkdir(parents=True, exist_ok=True)
    models_root.mkdir(parents=True, exist_ok=True)

    cache_path = validated_viewer_cache_input_path()
    _validate_cache_input(cache_path)
    fingerprint = _asset_export_fingerprint(cache_path)

    if (
        not force_rebuild
        and bundle_path.is_file()
        and manifest_path.is_file()
        and terrain_bundle_path.is_file()
        and npc_models_bundle_path.is_file()
        and _manifest_matches(manifest_path, fingerprint)
    ):
        return bundle_path

    with tempfile.TemporaryDirectory(prefix="fc-viewer-assets-") as temp_dir_text:
        temp_dir = Path(temp_dir_text)
        raw_dir = temp_dir / "raw"
        raw_dir.mkdir(parents=True, exist_ok=True)
        _run_java_exporter(cache_path=cache_path, output_dir=raw_dir)
        dat2_export = _export_dat2_scene_assets(cache_path=cache_path, raw_dir=raw_dir)
        bundle = _assemble_bundle(raw_dir, dat2_export)
        _write_generated_assets(bundle, raw_dir, generated_root)

    manifest_path.write_text(
        json.dumps(
            {
                "fingerprint": fingerprint,
                "bundle_path": str(bundle_path),
                "terrain_bundle_path": str(terrain_bundle_path),
                "npc_models_bundle_path": str(npc_models_bundle_path),
                "sprites_root": str(sprites_root),
                "models_root": str(models_root),
                "cache_input": str(cache_path),
            },
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )
    return bundle_path


def load_native_viewer_asset_bundle() -> dict[str, object]:
    bundle_path = ensure_native_viewer_asset_bundle()
    return json.loads(bundle_path.read_text(encoding="utf-8"))


def _manifest_matches(manifest_path: Path, fingerprint: str) -> bool:
    payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    return str(payload.get("fingerprint", "")) == fingerprint


def _validate_cache_input(cache_path: Path) -> None:
    if not cache_path.is_dir():
        raise RuntimeError(
            "DV4b cache input is missing. "
            f"Expected directory {cache_path}."
        )
    main_dat = cache_path / "main_file_cache.dat2"
    if not main_dat.is_file():
        raise RuntimeError(
            "DV4b cache input is incomplete. "
            f"Expected file {main_dat}."
        )


def _asset_export_fingerprint(cache_path: Path) -> str:
    hasher = hashlib.sha256()
    for path in (
        Path(__file__),
        Path(__file__).with_name("osrs_cache_dat2.py"),
        legacy_headless_env_root() / "config" / "headless_manifest.toml",
        legacy_rsps_root() / "data" / "minigame" / "tzhaar_fight_cave" / "tzhaar_fight_cave.areas.toml",
        legacy_rsps_root() / "data" / "minigame" / "tzhaar_fight_cave" / "tzhaar_fight_cave.ifaces.toml",
        legacy_rsps_root() / "data" / "area" / "karamja" / "tzhaar_city" / "tzhaar_city.objs.toml",
        legacy_rsps_root() / "data" / "entity" / "player" / "inventory" / "inventory.ifaces.toml",
        legacy_rsps_root() / "data" / "entity" / "player" / "inventory" / "inventory_side.ifaces.toml",
        legacy_rsps_root() / "data" / "entity" / "player" / "modal" / "icon.items.toml",
        legacy_rsps_root() / "data" / "skill" / "range" / "crossbow.items.toml",
        legacy_rsps_root() / "data" / "skill" / "range" / "bolt.items.toml",
        legacy_rsps_root() / "data" / "skill" / "range" / "ranged_armour.items.toml",
        legacy_rsps_root() / "data" / "minigame" / "tai_bwo_wannai_cleanup" / "tai_bwo_wannai_cleanup.items.toml",
        legacy_rsps_root() / "data" / "skill" / "herblore" / "potion.items.toml",
        legacy_rsps_root() / "data" / "skill" / "herblore" / "herblore.items.toml",
        legacy_rsps_root() / "tools" / "src" / "main" / "kotlin" / "world" / "gregs" / "voidps" / "tools" / "cache" / "Xteas.kt",
        legacy_rsps_root() / "data" / "fight_caves_demo" / "saves" / "fcdemo01.toml",
        legacy_headless_env_root() / "assets" / "fight-caves-header.png",
        _ocean_osrs_pvp_scripts_root() / "export_terrain.py",
        _ocean_osrs_pvp_scripts_root() / "export_models.py",
        _ocean_osrs_pvp_scripts_root() / "export_sprites_modern.py",
    ):
        hasher.update(path.read_bytes())
    for relative_path in VIEWER_XTEA_TEST_INPUTS:
        path = workspace_root().parent.parent / relative_path
        if path.is_file():
            hasher.update(path.read_bytes())
    main_dat = cache_path / "main_file_cache.dat2"
    hasher.update(str(main_dat.stat().st_size).encode("utf-8"))
    hasher.update(str(int(main_dat.stat().st_mtime)).encode("utf-8"))
    return hasher.hexdigest()


def _run_java_exporter(*, cache_path: Path, output_dir: Path) -> None:
    java_source = output_dir / "FightCavesAssetExporter.java"
    java_source.write_text(_java_exporter_source(), encoding="utf-8")
    classpath_entries = [str(output_dir), *_exporter_classpath_entries()]
    classpath = ":".join(classpath_entries)

    compile_result = subprocess.run(
        ["javac", "-cp", classpath, str(java_source)],
        text=True,
        capture_output=True,
        check=False,
    )
    if compile_result.returncode != 0:
        raise RuntimeError(
            "DV4b Java exporter compilation failed.\n"
            f"stdout:\n{compile_result.stdout}\n"
            f"stderr:\n{compile_result.stderr}"
        )

    run_result = subprocess.run(
        [
            "java",
            "-cp",
            classpath,
            "FightCavesAssetExporter",
            str(cache_path),
            str(output_dir),
        ],
        text=True,
        capture_output=True,
        check=False,
    )
    if run_result.returncode != 0:
        raise RuntimeError(
            "DV4b Java exporter run failed.\n"
            f"stdout:\n{run_result.stdout}\n"
            f"stderr:\n{run_result.stderr}"
        )


def _exporter_classpath_entries() -> list[str]:
    entries = [
        legacy_headless_env_root() / "game" / "build" / "libs" / "fight-caves-headless.jar",
        legacy_headless_env_root() / "cache" / "build" / "libs" / "cache-dev.jar",
        legacy_headless_env_root() / "buffer" / "build" / "libs" / "buffer-dev.jar",
        legacy_headless_env_root() / "types" / "build" / "libs" / "types-dev.jar",
        _find_gradle_jar(".gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/*/*/kotlin-stdlib-*.jar"),
        _find_gradle_jar(".gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-common/*/*/kotlin-stdlib-common-*.jar"),
        _find_gradle_jar(".gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk7/*/*/kotlin-stdlib-jdk7-*.jar"),
        _find_gradle_jar(".gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk8/*/*/kotlin-stdlib-jdk8-*.jar"),
        _find_gradle_jar(".gradle/caches/modules-2/files-2.1/it.unimi.dsi/fastutil/*/*/fastutil-*.jar"),
        _find_gradle_jar(".gradle/caches/modules-2/files-2.1/com.github.jponge/lzma-java/*/*/lzma-java-*.jar"),
        _find_gradle_jar(".gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/*/*/slf4j-api-*.jar"),
        _find_gradle_jar(".gradle/caches/modules-2/files-2.1/ch.qos.logback/logback-core/*/*/logback-core-*.jar"),
        _find_gradle_jar(".gradle/caches/modules-2/files-2.1/ch.qos.logback/logback-classic/*/*/logback-classic-*.jar"),
    ]
    return [str(path) for path in entries]


def _find_gradle_jar(pattern: str) -> Path:
    matches = sorted(Path.home().glob(pattern))
    if not matches:
        raise RuntimeError(f"Missing Gradle dependency jar for DV4b exporter pattern: {pattern}")
    return matches[-1]


def _export_dat2_scene_assets(*, cache_path: Path, raw_dir: Path) -> dict[str, object]:
    reader = Dat2CacheReader(cache_path)
    terrain_module = _load_ocean_script_module("export_terrain")
    model_module = _load_ocean_script_module("export_models")
    sprite_module = _load_ocean_script_module("export_sprites_modern")

    terrain_summary = _export_dat2_terrain_bundle(reader, terrain_module, raw_dir)
    sprite_summary = _export_dat2_sprite_subset(reader, sprite_module, raw_dir)
    npc_summary = _export_dat2_npc_model_bundle(reader, model_module, raw_dir)

    export_summary = {
        "terrain_bundle": terrain_summary,
        "sprite_bundle": sprite_summary,
        "npc_model_bundle": npc_summary,
    }
    (raw_dir / "dv4c_export_summary.json").write_text(
        json.dumps(export_summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return export_summary


def _export_dat2_terrain_bundle(reader: Dat2CacheReader, terrain_module, raw_dir: Path) -> dict[str, object]:
    underlays, overlays = _decode_floor_definitions_dat2(reader, terrain_module)
    texture_colors = terrain_module.load_texture_average_colors_modern(reader)
    map_groups = terrain_module.find_map_groups(reader)
    parsed_regions: dict[tuple[int, int], object] = {}

    for region_x, region_y in FIGHT_CAVES_CACHE_REGIONS:
        mapsquare = (region_x << 8) | region_y
        terrain_group, _object_group = map_groups.get(mapsquare, (None, None))
        if terrain_group is None:
            raise RuntimeError(
                f"DV4c terrain group is missing for Fight Caves region ({region_x},{region_y})."
            )
        terrain_data = reader.read_container(5, terrain_group)
        if terrain_data is None:
            raise RuntimeError(
                "DV4c terrain export could not read cache index 5 group "
                f"{terrain_group} for region ({region_x},{region_y})."
            )
        parsed_regions[(region_x, region_y)] = terrain_module.parse_terrain_full(
            terrain_data,
            region_x * 64,
            region_y * 64,
        )

    terrain_module.stitch_region_edges(parsed_regions)
    vertices, colors = terrain_module.build_terrain_mesh(
        parsed_regions,
        underlays,
        overlays,
        tex_colors=texture_colors,
    )
    heightmap = terrain_module.build_heightmap(parsed_regions)
    output_path = raw_dir / generated_viewer_terrain_bundle_path().name
    terrain_module.write_terrain_binary(output_path, vertices, colors, parsed_regions, heightmap)
    return {
        "filename": output_path.name,
        "vertex_count": len(vertices) // 3,
        "region_count": len(parsed_regions),
        "regions": [f"{region_x}_{region_y}" for region_x, region_y in FIGHT_CAVES_CACHE_REGIONS],
        "floor_definition_index": 2,
        "terrain_index": 5,
    }


def _export_dat2_sprite_subset(reader: Dat2CacheReader, sprite_module, raw_dir: Path) -> dict[str, object]:
    sprites_root = raw_dir / "sprites"
    sprites_root.mkdir(parents=True, exist_ok=True)

    exported: list[dict[str, object]] = []
    for name, sprite_id in SPRITE_EXPORTS:
        sprite_data = reader.read_container(8, sprite_id)
        if sprite_data is None:
            raise RuntimeError(
                f"DV4c sprite export could not read cache index 8 group {sprite_id} ({name})."
            )
        frames = sprite_module.decode_sprites(sprite_id, sprite_data)
        if not frames:
            raise RuntimeError(
                f"DV4c sprite export decoded no frames for cache index 8 group {sprite_id} ({name})."
            )
        filename = f"{name}_{sprite_id}_0.png"
        _write_sprite_frame_png(frames[0], sprites_root / filename)
        exported.append(
            {
                "name": name,
                "sprite_id": sprite_id,
                "filename": filename,
                "width": int(frames[0].width),
                "height": int(frames[0].height),
            }
        )

    return {
        "count": len(exported),
        "sprite_index": 8,
        "sprites": exported,
    }


def _read_string(buf: io.BytesIO) -> str:
    parts: list[str] = []
    while True:
        data = buf.read(1)
        if not data or data[0] == 0:
            break
        parts.append(chr(data[0]))
    return "".join(parts)


def _write_sprite_frame_png(frame, output_path: Path) -> None:
    width = int(frame.width)
    height = int(frame.height)
    raw = bytearray()
    for row in range(height):
        raw.append(0)
        for column in range(width):
            argb = int(frame.pixels[row * width + column])
            raw.extend(
                (
                    (argb >> 16) & 0xFF,
                    (argb >> 8) & 0xFF,
                    argb & 0xFF,
                    (argb >> 24) & 0xFF,
                )
            )

    def chunk(tag: bytes, payload: bytes) -> bytes:
        return (
            struct.pack(">I", len(payload))
            + tag
            + payload
            + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)
        )

    png = bytearray(b"\x89PNG\r\n\x1a\n")
    png.extend(chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)))
    png.extend(chunk(b"IDAT", zlib.compress(bytes(raw), level=9)))
    png.extend(chunk(b"IEND", b""))
    output_path.write_bytes(bytes(png))


def _read_u16_be(buf: io.BytesIO) -> int:
    data = buf.read(2)
    return int.from_bytes(data, "big", signed=False) if len(data) == 2 else 0


def _read_u24_be(buf: io.BytesIO) -> int:
    data = buf.read(3)
    return int.from_bytes(data, "big", signed=False) if len(data) == 3 else 0


def _decode_floor_definitions_dat2(reader: Dat2CacheReader, terrain_module):
    underlays: dict[int, object] = {}
    overlays: dict[int, object] = {}

    underlay_files = reader.read_group(2, 1)
    for definition_id, data in underlay_files.items():
        floor = terrain_module.FloorDef(floor_id=definition_id)
        buf = io.BytesIO(data)
        while True:
            opcode_data = buf.read(1)
            if not opcode_data or opcode_data[0] == 0:
                break
            opcode = opcode_data[0]
            if opcode == 1:
                floor.rgb = _read_u24_be(buf)
            elif opcode == 2:
                texture = _read_u16_be(buf)
                floor.texture = -1 if texture == 65535 else texture
            elif opcode == 3:
                _read_u16_be(buf)
            elif opcode == 4 or opcode == 5:
                continue
            else:
                raise RuntimeError(f"DV4c encountered unsupported underlay opcode {opcode} for floor {definition_id}.")
        if floor.secondary_rgb != -1:
            terrain_module._rgb_to_hsl(floor.secondary_rgb, floor)
        terrain_module._rgb_to_hsl(floor.rgb, floor)
        underlays[definition_id] = floor

    overlay_files = reader.read_group(2, 4)
    for definition_id, data in overlay_files.items():
        floor = terrain_module.FloorDef(floor_id=definition_id)
        buf = io.BytesIO(data)
        while True:
            opcode_data = buf.read(1)
            if not opcode_data or opcode_data[0] == 0:
                break
            opcode = opcode_data[0]
            if opcode == 1:
                floor.rgb = _read_u24_be(buf)
            elif opcode == 2:
                floor.texture = buf.read(1)[0]
            elif opcode == 3:
                texture = _read_u16_be(buf)
                floor.texture = -1 if texture == 65535 else texture
            elif opcode == 5:
                floor.hide_underlay = False
            elif opcode == 7:
                floor.secondary_rgb = _read_u24_be(buf)
            elif opcode == 8:
                continue
            elif opcode == 9:
                _read_u16_be(buf)
            elif opcode == 10:
                continue
            elif opcode == 11:
                buf.read(1)
            elif opcode == 12:
                continue
            elif opcode == 13:
                _read_u24_be(buf)
            elif opcode == 14:
                buf.read(1)
            elif opcode == 16:
                buf.read(1)
            else:
                raise RuntimeError(f"DV4c encountered unsupported overlay opcode {opcode} for floor {definition_id}.")
        if floor.secondary_rgb != -1:
            terrain_module._rgb_to_hsl(floor.secondary_rgb, floor)
            floor.secondary_hue = floor.hue
            floor.secondary_saturation = floor.saturation
            floor.secondary_lightness = floor.lightness
        terrain_module._rgb_to_hsl(floor.rgb, floor)
        overlays[definition_id] = floor

    return underlays, overlays


def _read_sharded_index_entry(reader: Dat2CacheReader, index_id: int, entry_id: int) -> bytes | None:
    group_id = entry_id >> 7
    file_id = entry_id & 0x7F
    try:
        files = reader.read_group(index_id, group_id)
    except (FileNotFoundError, KeyError):
        return None
    return files.get(file_id)


def _parse_modern_npc_def(npc_id: int, data: bytes) -> dict[str, object]:
    buf = io.BytesIO(data)
    result: dict[str, object] = {
        "npc_id": npc_id,
        "name": "",
        "model_ids": [],
        "size": 1,
        "width_scale": 128,
        "height_scale": 128,
    }
    while True:
        opcode_data = buf.read(1)
        if not opcode_data:
            break
        opcode = opcode_data[0]
        if opcode == 0:
            break
        if opcode == 1:
            count = buf.read(1)[0]
            result["model_ids"] = [int.from_bytes(buf.read(2), "big", signed=False) for _ in range(count)]
        elif opcode == 2:
            result["name"] = _read_string(buf)
        elif opcode == 12:
            result["size"] = buf.read(1)[0]
        elif opcode in (13, 14, 15, 16, 18, 95, 97, 98, 102, 103, 114, 122, 123, 127, 137, 138, 139, 142):
            buf.read(2)
        elif opcode == 17:
            buf.read(8)
        elif 30 <= opcode <= 34:
            _read_string(buf)
        elif opcode in (40, 41):
            count = buf.read(1)[0]
            buf.read(count * 4)
        elif opcode == 42:
            count = buf.read(1)[0]
            buf.read(count)
        elif opcode == 60:
            count = buf.read(1)[0]
            buf.read(count * 2)
        elif opcode == 93 or opcode == 99 or opcode == 107 or opcode == 109 or opcode == 111 or opcode == 141 or opcode == 143 or opcode == 158 or opcode == 159 or opcode == 162:
            continue
        elif opcode == 97:
            result["width_scale"] = int.from_bytes(buf.read(2), "big", signed=False)
        elif opcode == 98:
            result["height_scale"] = int.from_bytes(buf.read(2), "big", signed=False)
        elif opcode in (100, 101, 119, 125, 128, 140, 163, 165, 168):
            buf.read(1)
        elif opcode == 106 or opcode == 118:
            buf.read(2)
            buf.read(2)
            extra = 1 if opcode == 118 else 0
            if opcode == 118:
                buf.read(2)
            count = buf.read(1)[0]
            buf.read((count + 2 + extra) * 2)
        elif opcode == 115:
            buf.read(4)
        elif opcode == 113 or opcode == 155 or opcode == 164:
            buf.read(4)
        elif opcode == 116:
            buf.read(2)
        elif opcode == 117:
            buf.read(8)
        elif opcode == 121:
            translation_count = buf.read(1)[0]
            for _ in range(translation_count):
                buf.read(1)
                buf.read(3)
        elif opcode == 134:
            buf.read(2)
            buf.read(2)
            buf.read(2)
            buf.read(2)
            buf.read(1)
        elif opcode == 135 or opcode == 136:
            buf.read(1)
            buf.read(2)
        elif opcode == 160:
            count = buf.read(1)[0]
            buf.read(count * 2)
        elif opcode == 249:
            param_count = buf.read(1)[0]
            for _ in range(param_count):
                is_string = buf.read(1)[0]
                buf.read(3)
                if is_string:
                    _read_string(buf)
                else:
                    buf.read(4)
        else:
            raise RuntimeError(
                f"DV4c encountered unsupported NPC opcode {opcode} while parsing npc {npc_id}."
            )
    return result


def _export_dat2_npc_model_bundle(reader: Dat2CacheReader, model_module, raw_dir: Path) -> dict[str, object]:
    output_path = raw_dir / generated_viewer_npc_models_bundle_path().name
    decoded_models: list[object] = []
    npc_entries: list[dict[str, object]] = []

    for label, npc_id in FIGHT_CAVES_NPC_EXPORTS:
        npc_data = _read_sharded_index_entry(reader, 18, npc_id)
        if npc_data is None:
            raise RuntimeError(
                f"DV4c NPC export could not read cache index 18 group {npc_id} ({label})."
            )
        npc_def = _parse_modern_npc_def(npc_id, npc_data)
        model_ids = list(npc_def.get("model_ids", []))
        if not model_ids:
            raise RuntimeError(
                f"DV4c NPC export found no model IDs for cache index 18 group {npc_id} ({label})."
            )
        model_id = int(model_ids[0])
        model_data = reader.read_container(7, model_id)
        if model_data is None:
            raise RuntimeError(
                f"DV4c NPC export could not read cache index 7 group {model_id} for npc {label}."
            )
        decoded_model = model_module.decode_model(model_id, model_data)
        if decoded_model is None:
            raise RuntimeError(
                f"DV4c NPC export could not decode model {model_id} for npc {label}."
            )
        decoded_models.append(decoded_model)
        npc_entries.append(
            {
                "label": label,
                "npc_id": npc_id,
                "model_id": model_id,
                "width_scale": int(npc_def.get("width_scale", 128)),
                "height_scale": int(npc_def.get("height_scale", 128)),
                "size": int(npc_def.get("size", 1)),
            }
        )

    model_module.write_models_binary(output_path, decoded_models)
    return {
        "filename": output_path.name,
        "count": len(npc_entries),
        "npc_definition_index": 18,
        "model_index": 7,
        "npcs": npc_entries,
    }


def _assemble_bundle(raw_dir: Path, dat2_export: dict[str, object]) -> dict[str, object]:
    metadata = json.loads((raw_dir / "metadata.json").read_text(encoding="utf-8"))
    terrain_tiles = _load_terrain_tiles(raw_dir / "terrain_tiles.csv")
    palette = _build_palette(terrain_tiles)
    terrain_summary = dat2_export["terrain_bundle"]
    sprite_summary = dat2_export["sprite_bundle"]
    npc_model_summary = dat2_export["npc_model_bundle"]
    world_min_x, world_max_x, world_min_y, world_max_y = _fight_caves_region_world_bounds()

    fight_caves_ifaces = _load_legacy_semantic_table(
        legacy_rsps_root() / "data" / "minigame" / "tzhaar_fight_cave" / "tzhaar_fight_cave.ifaces.toml"
    )
    inventory_ifaces = _load_legacy_semantic_table(
        legacy_rsps_root() / "data" / "entity" / "player" / "inventory" / "inventory.ifaces.toml"
    )
    inventory_side_ifaces = _load_legacy_semantic_table(
        legacy_rsps_root() / "data" / "entity" / "player" / "inventory" / "inventory_side.ifaces.toml"
    )
    tzhaar_city_objects = _load_legacy_semantic_table(
        legacy_rsps_root() / "data" / "area" / "karamja" / "tzhaar_city" / "tzhaar_city.objs.toml"
    )
    icon_items = tomllib.loads(
        (legacy_rsps_root() / "data" / "entity" / "player" / "modal" / "icon.items.toml").read_text(encoding="utf-8")
    )
    demo_save_text, demo_save = _load_fight_caves_demo_save()
    xtea_candidates = _viewer_xtea_candidate_payloads()
    model_exports = list(metadata.get("model_exports", []))
    item_export_count = sum(
        1 for export in model_exports
        if str(export.get("owner_kind", "")).startswith("item_")
    )
    npc_export_count = sum(
        1 for export in model_exports
        if str(export.get("owner_kind", "")) == "npc"
    )
    player_export_count = sum(
        1 for export in model_exports
        if str(export.get("owner_kind", "")).startswith("player_")
    )
    object_export_count = sum(
        1 for export in model_exports
        if str(export.get("owner_kind", "")) == "object"
    )
    player_items = {
        str(entry.get("label")): entry
        for entry in metadata.get("items", [])
        if isinstance(entry, dict) and entry.get("label") is not None
    }
    player_appearance = {
        "source_path": "reference/legacy-rsps/data/fight_caves_demo/saves/fcdemo01.toml",
        "male": bool(demo_save.get("male", True)),
        "looks": list(demo_save.get("looks", [])),
        "colours": list(demo_save.get("colours", [])),
        "tile": dict(demo_save.get("tile", {})) if isinstance(demo_save.get("tile"), dict) else {},
        "worn_equipment": [
            {
                **spec,
                "name": str(player_items.get(str(spec["label"]), {}).get("name", spec["label"])),
                "item_present_in_export": str(spec["label"]) in player_items,
            }
            for spec in _demo_loadout_specs()
        ],
        "equipment_model_mode": "demo_save_and_cache_definition_recipe",
    }
    blocked_categories: list[str] = []
    blocked_notes: list[str] = []
    if not bool(metadata["cache_validation"]["object_archive_accessible"]):
        blocked_categories.append("fight_caves_map_object_archive_props")
        blocked_notes.append(str(metadata["cache_validation"]["object_archive_blocker"]))
    blocked_categories.append("inventory_item_icons_from_item_models")
    blocked_notes.append(
        "Fight Caves item icon rasterization is still not committed in this checkout. "
        "ARCH-VIEW2 exports real item model inputs and icon camera recipes, but not final raster item icons yet."
    )

    bundle = {
        "asset_id": VIEWER_ASSET_BUNDLE_ID,
        "description": (
            "Legacy-asset-derived Fight Caves scene slice and render-input bundle for the native viewer. "
            "Terrain comes from cache-derived TERR export, Fight Caves NPC visuals come from cache-derived MDL2 export, "
            "and HUD sprites come from cache-derived PNG export plus RSPS semantic tables."
        ),
        "source_refs": [
            str(VIEWER_CACHE_INPUT_RELATIVE),
            "reference/legacy-headless-env/config/headless_manifest.toml",
            "reference/legacy-rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.areas.toml",
            "reference/legacy-rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.ifaces.toml",
            "reference/legacy-rsps/data/area/karamja/tzhaar_city/tzhaar_city.objs.toml",
            "reference/legacy-rsps/data/entity/player/inventory/inventory.ifaces.toml",
            "reference/legacy-rsps/data/entity/player/inventory/inventory_side.ifaces.toml",
            "reference/legacy-rsps/data/entity/player/modal/icon.items.toml",
            "reference/legacy-rsps/data/fight_caves_demo/saves/fcdemo01.toml",
            *[str(path) for path in VIEWER_XTEA_TEST_INPUTS],
        ],
        "cache_validation": {
            "preferred_input": str(VIEWER_CACHE_INPUT_RELATIVE),
            "preferred_input_valid": True,
            "rsps_cache_input": str(VIEWER_EMPTY_RSPS_CACHE_RELATIVE),
            "rsps_cache_empty": _directory_is_effectively_empty(empty_rsps_cache_input_path()),
            "required_object_archive_regions": list(FIGHT_CAVES_OBJECT_ARCHIVE_REGIONS),
            "xtea_candidates": xtea_candidates,
            "map_archive_names": list(metadata["cache_validation"]["map_archive_names"]),
            "map_archive_accessible": bool(metadata["cache_validation"]["map_archive_accessible"]),
            "object_archive_names": list(metadata["cache_validation"]["object_archive_names"]),
            "object_archive_accessible": bool(metadata["cache_validation"]["object_archive_accessible"]),
            "object_archive_blocker": metadata["cache_validation"]["object_archive_blocker"],
        },
        "world_to_runtime_transform": {
            "world_origin_x": 2396,
            "world_origin_y": 5088,
            "runtime_x": "world_x - 2396",
            "runtime_y": "world_y - 5088",
        },
        "terrain": {
            "tile_count": len(terrain_tiles),
            "region_count": int(terrain_summary["region_count"]),
            "regions": list(terrain_summary["regions"]),
            "terrain_bundle_filename": str(terrain_summary["filename"]),
            "vertex_count": int(terrain_summary["vertex_count"]),
            "floor_definition_index": int(terrain_summary["floor_definition_index"]),
            "terrain_index": int(terrain_summary["terrain_index"]),
            "world_bounds": {
                "min_x": world_min_x,
                "max_x": world_max_x,
                "min_y": world_min_y,
                "max_y": world_max_y,
            },
            "tiles": terrain_tiles,
            "palette": palette,
        },
        "interfaces": {
            "fight_caves_overlay": {
                "semantic": fight_caves_ifaces,
                "cache": _find_interface(metadata["interfaces"], 316),
            },
            "inventory_tab": {
                "semantic": inventory_ifaces,
                "cache": _find_interface(metadata["interfaces"], 149),
            },
            "inventory_side": {
                "semantic": inventory_side_ifaces,
                "cache": _find_interface(metadata["interfaces"], 387),
            },
        },
        "sprites": sprite_summary["sprites"],
        "hud_sprite_files": _hud_sprite_file_map(sprite_summary["sprites"]),
        "items": metadata["items"],
        "npcs": metadata["npcs"],
        "npc_model_bundle": npc_model_summary,
        "objects": metadata.get("objects", []),
        "object_semantics": tzhaar_city_objects,
        "identity_kits": metadata.get("identity_kits", []),
        "player_default_appearance": player_appearance,
        "model_exports": model_exports,
        "render_export_status": {
            "terrain_bundle": {
                "status": "exported",
                "vertex_count": int(terrain_summary["vertex_count"]),
                "region_count": int(terrain_summary["region_count"]),
            },
            "sprite_bundle": {
                "status": "exported",
                "sprite_count": int(sprite_summary["count"]),
            },
            "npc_model_bundle": {
                "status": "exported",
                "exported_model_count": int(npc_model_summary["count"]),
            },
            "object_archive_props": {
                "status": "blocked" if not bool(metadata["cache_validation"]["object_archive_accessible"]) else "exported",
                "required_regions": list(FIGHT_CAVES_OBJECT_ARCHIVE_REGIONS),
                "exported_model_file_count": object_export_count,
            },
            "item_render_inputs": {
                "status": "exported",
                "exported_model_file_count": item_export_count,
            },
            "npc_render_inputs": {
                "status": "exported",
                "exported_model_file_count": npc_export_count,
            },
            "player_render_inputs": {
                "status": "exported",
                "exported_model_file_count": player_export_count,
            },
            "item_icon_rasterization": {
                "status": "blocked",
                "reason": "no committed offline item-icon rasterizer in this checkout",
            },
        },
        "blocked_categories": blocked_categories,
        "blocked_notes": blocked_notes,
        "fight_caves_demo_inventory_source": demo_save_text.strip().splitlines()[-2:],
        "icon_items_excerpt": {key: icon_items[key] for key in ("prayer_icon", "number_zero", "number_one", "number_two") if key in icon_items},
    }
    return bundle


def _directory_is_effectively_empty(path: Path) -> bool:
    if not path.exists():
        return True
    return not any(path.iterdir())


def _load_legacy_semantic_table(path: Path) -> dict[str, object]:
    text = path.read_text(encoding="utf-8")
    current_root = ""
    sections: list[dict[str, object]] = []

    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("[") and line.endswith("]"):
            section_name = line[1:-1].strip()
            if section_name.startswith("."):
                if not current_root:
                    raise RuntimeError(
                        f"Legacy semantic table {path} uses dotted sections before a root section."
                    )
                section_name = f"{current_root}{section_name}"
            else:
                current_root = section_name
            sections.append({"name": section_name})
            continue
        if "=" not in line or not sections:
            continue
        key, value = line.split("=", 1)
        parsed_value: object = value.strip()
        key = key.strip()
        if parsed_value.startswith('"') and parsed_value.endswith('"'):
            parsed_value = parsed_value[1:-1]
        else:
            try:
                parsed_value = int(parsed_value)
            except ValueError:
                pass
        sections[-1][key] = parsed_value

    return {
        "source_path": str(path.relative_to(legacy_rsps_root().parent)),
        "source_text": text,
        "sections": sections,
    }


def _find_interface(interfaces: list[dict[str, object]], interface_id: int) -> dict[str, object] | None:
    for interface in interfaces:
        if int(interface["interface_id"]) == interface_id:
            return interface
    return None


def _hud_sprite_file_map(sprites: list[dict[str, object]]) -> dict[str, str]:
    mapping: dict[str, str] = {}
    for sprite in sprites:
        mapping[str(sprite["name"])] = str(sprite["filename"])
    return mapping


def _load_terrain_tiles(csv_path: Path) -> list[dict[str, object]]:
    tiles: list[dict[str, object]] = []
    with csv_path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            kind = _classify_tile_kind(int(row["runtime_x"]), int(row["runtime_y"]), int(row["r"]), int(row["g"]), int(row["b"]))
            tiles.append(
                {
                    "runtime_x": int(row["runtime_x"]),
                    "runtime_y": int(row["runtime_y"]),
                    "world_x": int(row["world_x"]),
                    "world_y": int(row["world_y"]),
                    "underlay_id": int(row["underlay_id"]),
                    "overlay_id": int(row["overlay_id"]),
                    "settings": int(row["settings"]),
                    "color": [int(row["r"]), int(row["g"]), int(row["b"]), 255],
                    "kind": kind,
                }
            )
    return tiles


def _classify_tile_kind(runtime_x: int, runtime_y: int, red: int, green: int, blue: int) -> str:
    if red == 0 and green == 0 and blue == 0:
        return "void"
    if red >= 70 and red > green and green >= blue:
        return "lava"
    if runtime_x <= -22 or runtime_x >= 22 or runtime_y <= -20 or runtime_y >= 76:
        if red >= green:
            return "lava"
    return "floor"


def _build_palette(tiles: list[dict[str, object]]) -> dict[str, list[int]]:
    floor_color = _average_color(tile["color"] for tile in tiles if tile["kind"] == "floor")
    lava_color = _average_color(tile["color"] for tile in tiles if tile["kind"] == "lava")
    if floor_color is None:
        floor_color = [68, 54, 46, 255]
    if lava_color is None:
        lava_color = [186, 80, 28, 255]

    def darken(color: list[int], amount: float) -> list[int]:
        return [
            max(0, min(255, int(round(color[0] * amount)))),
            max(0, min(255, int(round(color[1] * amount)))),
            max(0, min(255, int(round(color[2] * amount)))),
            255,
        ]

    def blend(a: list[int], b: list[int], amount: float) -> list[int]:
        return [
            int(round(a[0] + (b[0] - a[0]) * amount)),
            int(round(a[1] + (b[1] - a[1]) * amount)),
            int(round(a[2] + (b[2] - a[2]) * amount)),
            255,
        ]

    return {
        "lava_base": darken(lava_color, 0.82),
        "lava_mid": lava_color,
        "lava_hot": blend(lava_color, [255, 196, 72, 255], 0.45),
        "lava_ring": blend(lava_color, [255, 216, 122, 255], 0.55),
        "lava_ring_dim": darken(lava_color, 0.58),
        "basalt_floor": floor_color,
        "basalt_floor_highlight": blend(floor_color, [140, 124, 112, 255], 0.28),
        "basalt_wall": darken(floor_color, 0.74),
        "basalt_wall_edge": blend(darken(floor_color, 0.82), [170, 156, 144, 255], 0.22),
        "dais": blend(floor_color, [126, 112, 95, 255], 0.32),
        "dais_glow": blend(lava_color, [255, 196, 72, 255], 0.35),
        "ember": blend(lava_color, [255, 236, 160, 255], 0.32),
        "player_base": [176, 205, 214, 255],
        "player_accent": [54, 112, 168, 255],
        "npc_jad": [128, 72, 36, 255],
        "npc_jad_telegraph": [230, 118, 41, 255],
        "npc_healer": [132, 194, 102, 255],
        "npc_ranger": [183, 154, 78, 255],
        "npc_mage": [96, 139, 218, 255],
        "npc_melee": [170, 90, 74, 255],
        "hover": [255, 228, 120, 255],
        "selection": [111, 214, 255, 255],
        "spawn_pad": blend(lava_color, [194, 148, 82, 255], 0.35),
    }


def _average_color(colors: object) -> list[int] | None:
    total_r = 0
    total_g = 0
    total_b = 0
    count = 0
    for color in colors:
        red, green, blue, _alpha = color
        total_r += red
        total_g += green
        total_b += blue
        count += 1
    if count == 0:
        return None
    return [
        int(round(total_r / count)),
        int(round(total_g / count)),
        int(round(total_b / count)),
        255,
    ]


def _write_generated_assets(bundle: dict[str, object], raw_dir: Path, generated_root: Path) -> None:
    bundle_path = generated_viewer_bundle_path()
    bundle_path.write_text(json.dumps(bundle, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    terrain_bundle_path = generated_viewer_terrain_bundle_path()
    shutil.copy2(raw_dir / terrain_bundle_path.name, terrain_bundle_path)

    npc_models_bundle_path = generated_viewer_npc_models_bundle_path()
    shutil.copy2(raw_dir / npc_models_bundle_path.name, npc_models_bundle_path)

    sprites_root = generated_viewer_sprites_root()
    if sprites_root.exists():
        shutil.rmtree(sprites_root)
    shutil.copytree(raw_dir / "sprites", sprites_root)

    models_root = generated_viewer_models_root()
    if models_root.exists():
        shutil.rmtree(models_root)
    raw_models_root = raw_dir / "models"
    if raw_models_root.is_dir():
        shutil.copytree(raw_models_root, models_root)
    else:
        models_root.mkdir(parents=True, exist_ok=True)


def _java_exporter_source() -> str:
    demo_save = _load_fight_caves_demo_save()[1]
    world_min_x, world_max_x, world_min_y, world_max_y = _fight_caves_region_world_bounds()
    player_look_ids = tuple(int(value) for value in demo_save.get("looks", []))
    player_loadout_specs = _demo_loadout_specs()
    sprite_entries = ",\n".join(
        f"        new SpriteSpec({json.dumps(name)}, {sprite_id})"
        for name, sprite_id in SPRITE_EXPORTS
    )
    region_entries = ",\n".join(
        f"        new RegionSpec({region_x}, {region_y})"
        for region_x, region_y in FIGHT_CAVES_CACHE_REGIONS
    )
    item_entries = ",\n".join(
        f"        new IdLabel({json.dumps(name)}, {item_id})"
        for name, item_id in FIGHT_CAVES_ITEM_EXPORTS
    )
    npc_entries = ",\n".join(
        f"        new IdLabel({json.dumps(name)}, {npc_id})"
        for name, npc_id in FIGHT_CAVES_NPC_EXPORTS
    )
    object_entries = ",\n".join(
        f"        new IdLabel({json.dumps(name)}, {object_id})"
        for name, object_id in FIGHT_CAVES_OBJECT_EXPORTS
    )
    interface_entries = ", ".join(str(interface_id) for interface_id in FIGHT_CAVES_INTERFACE_EXPORTS)
    player_look_entries = ", ".join(str(value) for value in player_look_ids) or "0"
    player_loadout_entries = ",\n".join(
        f"        new PlayerLoadoutSpec({int(spec['slot_index'])}, {json.dumps(str(spec['label']))}, {int(spec['item_id'])})"
        for spec in player_loadout_specs
    ) or '        new PlayerLoadoutSpec(0, "", -1)'
    return textwrap.dedent(
        f"""
        import sim.cache.Cache;
        import sim.cache.FileCache;
        import sim.cache.Index;
        import sim.cache.config.data.IdentityKitDefinition;
        import sim.cache.config.data.OverlayDefinition;
        import sim.cache.config.data.UnderlayDefinition;
        import sim.cache.config.decoder.IdentityKitDecoder;
        import sim.cache.config.decoder.OverlayDecoder;
        import sim.cache.config.decoder.UnderlayDecoder;
        import sim.cache.definition.data.IndexedSprite;
        import sim.cache.definition.data.InterfaceComponentDefinitionFull;
        import sim.cache.definition.data.InterfaceDefinitionFull;
        import sim.cache.definition.data.ItemDefinitionFull;
        import sim.cache.definition.data.NPCDefinitionFull;
        import sim.cache.definition.data.ObjectDefinitionFull;
        import sim.cache.definition.data.SpriteDefinition;
        import sim.cache.definition.decoder.InterfaceDecoderFull;
        import sim.cache.definition.decoder.ItemDecoderFull;
        import sim.cache.definition.decoder.NPCDecoderFull;
        import sim.cache.definition.decoder.ObjectDecoderFull;
        import sim.cache.definition.decoder.SpriteDecoder;

        import java.io.BufferedWriter;
        import java.io.IOException;
        import java.nio.charset.StandardCharsets;
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.util.ArrayList;
        import java.util.LinkedHashSet;
        import java.util.List;
        import java.util.Set;
        import javax.imageio.ImageIO;

        public final class FightCavesAssetExporter {{
            private static final int WORLD_MIN_X = {world_min_x};
            private static final int WORLD_MAX_X = {world_max_x};
            private static final int WORLD_MIN_Y = {world_min_y};
            private static final int WORLD_MAX_Y = {world_max_y};
            private static final int RUNTIME_ORIGIN_X = 2396;
            private static final int RUNTIME_ORIGIN_Y = 5088;
            private static final RegionSpec[] REGIONS = new RegionSpec[] {{
        {region_entries}
            }};
            private static final int[] INTERFACES = new int[]{{ {interface_entries} }};
            private static final SpriteSpec[] SPRITES = new SpriteSpec[] {{
        {sprite_entries}
            }};
            private static final IdLabel[] ITEMS = new IdLabel[] {{
        {item_entries}
            }};
            private static final IdLabel[] NPCS = new IdLabel[] {{
        {npc_entries}
            }};
            private static final IdLabel[] OBJECTS = new IdLabel[] {{
        {object_entries}
            }};
            private static final int[] PLAYER_LOOK_KIT_IDS = new int[]{{ {player_look_entries} }};
            private static final PlayerLoadoutSpec[] PLAYER_LOADOUT = new PlayerLoadoutSpec[] {{
        {player_loadout_entries}
            }};

            private record RegionSpec(int x, int y) {{}}
            private record SpriteSpec(String name, int id) {{}}
            private record IdLabel(String name, int id) {{}}
            private record PlayerLoadoutSpec(int slotIndex, String label, int id) {{}}
            private record ExportedSprite(String name, int id, String filename, int width, int height) {{}}
            private record ExportedModel(String ownerKind, String ownerLabel, int ownerId, String usage, int modelId, String filename, int byteLength) {{}}

            public static void main(String[] args) throws Exception {{
                if (args.length != 2) {{
                    throw new IllegalArgumentException("expected <cache-path> <output-dir>");
                }}
                Cache cache = FileCache.Companion.invoke(args[0], null, null, null);
                Path out = Path.of(args[1]);
                Files.createDirectories(out);
                Files.createDirectories(out.resolve("sprites"));

                UnderlayDefinition[] underlays = new UnderlayDecoder().load(cache);
                OverlayDefinition[] overlays = new OverlayDecoder().load(cache);
                CacheProbe probe = writeTerrainCsv(cache, underlays, overlays, out.resolve("terrain_tiles.csv"));

                InterfaceDefinitionFull[] interfaces = new InterfaceDecoderFull().load(cache);
                ItemDefinitionFull[] items = new ItemDecoderFull().load(cache);
                NPCDefinitionFull[] npcs = new NPCDecoderFull().load(cache);
                ObjectDefinitionFull[] objects = new ObjectDecoderFull(true, false).load(cache);
                IdentityKitDefinition[] identityKits = new IdentityKitDecoder().load(cache);
                SpriteDefinition[] sprites = new SpriteDecoder().load(cache);

                List<ExportedSprite> exportedSprites = exportSprites(sprites, out.resolve("sprites"));
                List<ExportedModel> exportedModels = exportModels(
                    cache,
                    items,
                    npcs,
                    objects,
                    identityKits,
                    out.resolve("models")
                );
                writeMetadataJson(
                    out.resolve("metadata.json"),
                    interfaces,
                    items,
                    npcs,
                    objects,
                    identityKits,
                    exportedSprites,
                    exportedModels,
                    probe
                );
            }}

            private static CacheProbe writeTerrainCsv(
                Cache cache,
                UnderlayDefinition[] underlays,
                OverlayDefinition[] overlays,
                Path output
            ) throws IOException {{
                List<String> rows = new ArrayList<>();
                List<String> mapArchiveNames = new ArrayList<>();
                List<String> objectArchiveNames = new ArrayList<>();
                List<String> objectArchiveBlockers = new ArrayList<>();
                rows.add("runtime_x,runtime_y,world_x,world_y,underlay_id,overlay_id,settings,r,g,b");
                for (RegionSpec region : REGIONS) {{
                    String mapArchiveName = "m" + region.x() + "_" + region.y();
                    String objectArchiveName = "l" + region.x() + "_" + region.y();
                    byte[] mapArchive = cache.data(Index.MAPS, mapArchiveName, null);
                    byte[] objectArchive = cache.data(Index.MAPS, objectArchiveName, null);
                    int position = 0;
                    mapArchiveNames.add(mapArchiveName);
                    objectArchiveNames.add(objectArchiveName);
                    if (mapArchive == null) {{
                        throw new IllegalStateException("Fight Caves map archive " + mapArchiveName + " was not readable from cache input.");
                    }}
                    if (objectArchive == null) {{
                        objectArchiveBlockers.add("Fight Caves object archive " + objectArchiveName + " is not readable from the validated headless cache without region xteas.");
                    }}
                    for (int level = 0; level < 4; level++) {{
                        for (int localX = 0; localX < 64; localX++) {{
                            for (int localY = 0; localY < 64; localY++) {{
                                int overlayId = 0;
                                int settings = 0;
                                int underlayId = 0;
                                while (position < mapArchive.length) {{
                                    int config = mapArchive[position++] & 0xff;
                                    if (config == 0) {{
                                        break;
                                    }} else if (config == 1) {{
                                        position++;
                                        break;
                                    }} else if (config <= 49) {{
                                        overlayId = mapArchive[position++] & 0xff;
                                    }} else if (config <= 81) {{
                                        settings = config - 49;
                                    }} else {{
                                        underlayId = config - 81;
                                    }}
                                }}
                                if (level != 0) {{
                                    continue;
                                }}
                                int worldX = region.x() * 64 + localX;
                                int worldY = region.y() * 64 + localY;
                                if (worldX < WORLD_MIN_X || worldX > WORLD_MAX_X || worldY < WORLD_MIN_Y || worldY > WORLD_MAX_Y) {{
                                    continue;
                                }}
                                int rgb = resolveTileRgb(underlays, overlays, underlayId, overlayId);
                                int runtimeX = worldX - RUNTIME_ORIGIN_X;
                                int runtimeY = worldY - RUNTIME_ORIGIN_Y;
                                rows.add(
                                    runtimeX + "," + runtimeY + "," + worldX + "," + worldY + "," + underlayId + "," + overlayId + "," + settings + ","
                                        + ((rgb >> 16) & 0xff) + "," + ((rgb >> 8) & 0xff) + "," + (rgb & 0xff)
                                );
                            }}
                        }}
                    }}
                }}
                Files.write(output, rows, StandardCharsets.UTF_8);
                return new CacheProbe(
                    mapArchiveNames,
                    objectArchiveNames,
                    objectArchiveBlockers.isEmpty(),
                    objectArchiveBlockers.isEmpty() ? "none" : String.join(" ", objectArchiveBlockers)
                );
            }}

            private static int resolveTileRgb(
                UnderlayDefinition[] underlays,
                OverlayDefinition[] overlays,
                int underlayId,
                int overlayId
            ) {{
                int rgb = 0;
                if (overlayId > 0 && overlayId < overlays.length) {{
                    OverlayDefinition overlay = overlays[overlayId];
                    if (overlay != null) {{
                        rgb = overlay.getBlendColour() != -1 ? overlay.getBlendColour() : overlay.getColour();
                        if ((rgb == 0 || rgb == -1) && overlay.getWaterColour() != 0) {{
                            rgb = overlay.getWaterColour();
                        }}
                    }}
                }}
                if ((rgb == 0 || rgb == -1) && underlayId > 0 && underlayId < underlays.length) {{
                    UnderlayDefinition underlay = underlays[underlayId];
                    if (underlay != null) {{
                        rgb = underlay.getColour();
                    }}
                }}
                if (rgb < 0) {{
                    rgb &= 0xFFFFFF;
                }}
                return rgb & 0xFFFFFF;
            }}

            private static List<ExportedSprite> exportSprites(SpriteDefinition[] sprites, Path outputDir) throws IOException {{
                List<ExportedSprite> exported = new ArrayList<>();
                for (SpriteSpec spec : SPRITES) {{
                    if (spec.id() < 0 || spec.id() >= sprites.length) {{
                        continue;
                    }}
                    SpriteDefinition definition = sprites[spec.id()];
                    if (definition == null || definition.getSprites() == null || definition.getSprites().length == 0) {{
                        continue;
                    }}
                    IndexedSprite sprite = definition.getSprites()[0];
                    String filename = spec.name() + "_" + spec.id() + "_0.png";
                    ImageIO.write(sprite.toBufferedImage(), "png", outputDir.resolve(filename).toFile());
                    exported.add(new ExportedSprite(spec.name(), spec.id(), filename, sprite.getWidth(), sprite.getHeight()));
                }}
                return exported;
            }}

            private static List<ExportedModel> exportModels(
                Cache cache,
                ItemDefinitionFull[] items,
                NPCDefinitionFull[] npcs,
                ObjectDefinitionFull[] objects,
                IdentityKitDefinition[] identityKits,
                Path outputDir
            ) throws IOException {{
                Files.createDirectories(outputDir);
                List<ExportedModel> exported = new ArrayList<>();
                Set<String> seen = new LinkedHashSet<>();

                for (IdLabel entry : ITEMS) {{
                    ItemDefinitionFull item = entry.id() >= 0 && entry.id() < items.length ? items[entry.id()] : null;
                    if (item == null) {{
                        continue;
                    }}
                    exportModel(cache, outputDir, exported, seen, "item_inventory", entry.name(), entry.id(), "inventory_model", item.getModelId());
                    for (int modelId : compactModelIds(new int[]{{item.getPrimaryMaleModel(), item.getSecondaryMaleModel(), item.getTertiaryMaleModel()}})) {{
                        exportModel(cache, outputDir, exported, seen, "item_male_wield", entry.name(), entry.id(), "male_wield_model", modelId);
                    }}
                    for (int modelId : compactModelIds(new int[]{{item.getPrimaryFemaleModel(), item.getSecondaryFemaleModel(), item.getTertiaryFemaleModel()}})) {{
                        exportModel(cache, outputDir, exported, seen, "item_female_wield", entry.name(), entry.id(), "female_wield_model", modelId);
                    }}
                }}

                for (IdLabel entry : NPCS) {{
                    NPCDefinitionFull npc = entry.id() >= 0 && entry.id() < npcs.length ? npcs[entry.id()] : null;
                    if (npc == null || npc.getModelIds() == null) {{
                        continue;
                    }}
                    for (int modelId : compactModelIds(npc.getModelIds())) {{
                        exportModel(cache, outputDir, exported, seen, "npc", entry.name(), entry.id(), "npc_model", modelId);
                    }}
                }}

                for (IdLabel entry : OBJECTS) {{
                    ObjectDefinitionFull object = entry.id() >= 0 && entry.id() < objects.length ? objects[entry.id()] : null;
                    if (object == null) {{
                        continue;
                    }}
                    for (int modelId : flattenObjectModelIds(object.getModelIds())) {{
                        exportModel(cache, outputDir, exported, seen, "object", entry.name(), entry.id(), "object_model", modelId);
                    }}
                }}

                for (int kitId : PLAYER_LOOK_KIT_IDS) {{
                    IdentityKitDefinition kit = kitId >= 0 && kitId < identityKits.length ? identityKits[kitId] : null;
                    if (kit == null || kit.getModelIds() == null) {{
                        continue;
                    }}
                    for (int modelId : compactModelIds(kit.getModelIds())) {{
                        exportModel(cache, outputDir, exported, seen, "player_identity_kit", "kit_" + kitId, kitId, "identity_model", modelId);
                    }}
                }}

                for (PlayerLoadoutSpec spec : PLAYER_LOADOUT) {{
                    ItemDefinitionFull item = spec.id() >= 0 && spec.id() < items.length ? items[spec.id()] : null;
                    if (item == null) {{
                        continue;
                    }}
                    for (int modelId : compactModelIds(new int[]{{item.getPrimaryMaleModel(), item.getSecondaryMaleModel(), item.getTertiaryMaleModel()}})) {{
                        exportModel(cache, outputDir, exported, seen, "player_worn_item", spec.label(), spec.id(), "male_wield_model", modelId);
                    }}
                }}

                return exported;
            }}

            private static void exportModel(
                Cache cache,
                Path outputDir,
                List<ExportedModel> exported,
                Set<String> seen,
                String ownerKind,
                String ownerLabel,
                int ownerId,
                String usage,
                int modelId
            ) throws IOException {{
                if (modelId < 0) {{
                    return;
                }}
                String key = ownerKind + ":" + ownerId + ":" + usage + ":" + modelId;
                if (!seen.add(key)) {{
                    return;
                }}
                byte[] model = cache.data(Index.MODELS, modelId, 0, null);
                if (model == null) {{
                    return;
                }}
                String filename = sanitize(ownerKind + "_" + ownerLabel + "_" + ownerId + "_" + usage + "_" + modelId + ".bin");
                Files.write(outputDir.resolve(filename), model);
                exported.add(new ExportedModel(ownerKind, ownerLabel, ownerId, usage, modelId, filename, model.length));
            }}

            private static int[] compactModelIds(int[] values) {{
                LinkedHashSet<Integer> ids = new LinkedHashSet<>();
                for (int value : values) {{
                    if (value >= 0) {{
                        ids.add(value);
                    }}
                }}
                int[] compact = new int[ids.size()];
                int index = 0;
                for (Integer value : ids) {{
                    compact[index++] = value;
                }}
                return compact;
            }}

            private static int[] flattenObjectModelIds(int[][] values) {{
                if (values == null) {{
                    return new int[0];
                }}
                LinkedHashSet<Integer> ids = new LinkedHashSet<>();
                for (int[] group : values) {{
                    if (group == null) {{
                        continue;
                    }}
                    for (int value : group) {{
                        if (value >= 0) {{
                            ids.add(value);
                        }}
                    }}
                }}
                int[] compact = new int[ids.size()];
                int index = 0;
                for (Integer value : ids) {{
                    compact[index++] = value;
                }}
                return compact;
            }}

            private static String sanitize(String value) {{
                return value.replaceAll("[^A-Za-z0-9._-]", "_");
            }}

            private static void writeMetadataJson(
                Path output,
                InterfaceDefinitionFull[] interfaces,
                ItemDefinitionFull[] items,
                NPCDefinitionFull[] npcs,
                ObjectDefinitionFull[] objects,
                IdentityKitDefinition[] identityKits,
                List<ExportedSprite> sprites,
                List<ExportedModel> modelExports,
                CacheProbe probe
            ) throws IOException {{
                StringBuilder json = new StringBuilder();
                json.append("{{\\n");
                json.append("  \\"cache_validation\\": {{\\n");
                json.append("    \\"map_archive_accessible\\": true,\\n");
                json.append("    \\"map_archive_names\\": ").append(renderStringArray(probe.mapArchiveNames())).append(",\\n");
                json.append("    \\"object_archive_accessible\\": ").append(probe.objectArchiveAccessible()).append(",\\n");
                json.append("    \\"object_archive_names\\": ").append(renderStringArray(probe.objectArchiveNames())).append(",\\n");
                json.append("    \\"object_archive_blocker\\": \\"");
                json.append(escape(probe.objectArchiveBlocker()));
                json.append("\\"\\n");
                json.append("  }},\\n");

                json.append("  \\"interfaces\\": [\\n");
                for (int i = 0; i < INTERFACES.length; i++) {{
                    int interfaceId = INTERFACES[i];
                    InterfaceDefinitionFull definition = interfaceId >= 0 && interfaceId < interfaces.length ? interfaces[interfaceId] : null;
                    json.append("    {{\\"interface_id\\": ").append(interfaceId).append(", \\"components\\": [");
                    if (definition != null && definition.getComponents() != null) {{
                        boolean first = true;
                        for (InterfaceComponentDefinitionFull component : definition.getComponents()) {{
                            if (component == null) {{
                                continue;
                            }}
                            if (component.getDefaultImage() == -1 && component.getText().isEmpty() && component.getName().isEmpty() && component.getContentType() == 0) {{
                                continue;
                            }}
                            if (!first) {{
                                json.append(", ");
                            }}
                            first = false;
                            json.append("{{");
                            json.append("\\"component_id\\": ").append(component.getId() & 0xffff).append(", ");
                            json.append("\\"type\\": ").append(component.getType()).append(", ");
                            json.append("\\"default_image\\": ").append(component.getDefaultImage()).append(", ");
                            json.append("\\"content_type\\": ").append(component.getContentType()).append(", ");
                            json.append("\\"text\\": \\"").append(escape(component.getText())).append("\\", ");
                            json.append("\\"name\\": \\"").append(escape(component.getName())).append("\\"");
                            json.append("}}");
                        }}
                    }}
                    json.append("]}}");
                    json.append(i + 1 == INTERFACES.length ? "\\n" : ",\\n");
                }}
                json.append("  ],\\n");

                json.append("  \\"items\\": [\\n");
                for (int i = 0; i < ITEMS.length; i++) {{
                    IdLabel entry = ITEMS[i];
                    ItemDefinitionFull item = entry.id() >= 0 && entry.id() < items.length ? items[entry.id()] : null;
                    json.append("    {{");
                    json.append("\\"label\\": \\"").append(entry.name()).append("\\", ");
                    json.append("\\"item_id\\": ").append(entry.id()).append(", ");
                    if (item != null) {{
                        json.append("\\"name\\": \\"").append(escape(item.getName())).append("\\", ");
                        json.append("\\"model_id\\": ").append(item.getModelId()).append(", ");
                        json.append("\\"sprite_scale\\": ").append(item.getSpriteScale()).append(", ");
                        json.append("\\"sprite_pitch\\": ").append(item.getSpritePitch()).append(", ");
                        json.append("\\"sprite_roll\\": ").append(item.getSpriteCameraRoll()).append(", ");
                        json.append("\\"sprite_translate_x\\": ").append(item.getSpriteTranslateX()).append(", ");
                        json.append("\\"sprite_translate_y\\": ").append(item.getSpriteTranslateY()).append(", ");
                        json.append("\\"sprite_yaw\\": ").append(item.getSpriteCameraYaw()).append(", ");
                        json.append("\\"male_wield_model_ids\\": ").append(renderIntArray(compactModelIds(new int[]{{item.getPrimaryMaleModel(), item.getSecondaryMaleModel(), item.getTertiaryMaleModel()}}))).append(", ");
                        json.append("\\"female_wield_model_ids\\": ").append(renderIntArray(compactModelIds(new int[]{{item.getPrimaryFemaleModel(), item.getSecondaryFemaleModel(), item.getTertiaryFemaleModel()}}))).append(", ");
                        json.append("\\"original_colours\\": ").append(renderShortArray(item.getOriginalColours())).append(", ");
                        json.append("\\"modified_colours\\": ").append(renderShortArray(item.getModifiedColours())).append(", ");
                        json.append("\\"original_textures\\": ").append(renderShortArray(item.getOriginalTextureColours())).append(", ");
                        json.append("\\"modified_textures\\": ").append(renderShortArray(item.getModifiedTextureColours()));
                    }} else {{
                        json.append("\\"name\\": \\"missing\\", \\"model_id\\": -1, \\"sprite_scale\\": 0, \\"sprite_pitch\\": 0, \\"sprite_roll\\": 0, \\"sprite_translate_x\\": 0, \\"sprite_translate_y\\": 0, \\"sprite_yaw\\": 0, \\"male_wield_model_ids\\": [], \\"female_wield_model_ids\\": [], \\"original_colours\\": [], \\"modified_colours\\": [], \\"original_textures\\": [], \\"modified_textures\\": []");
                    }}
                    json.append("}}");
                    json.append(i + 1 == ITEMS.length ? "\\n" : ",\\n");
                }}
                json.append("  ],\\n");

                json.append("  \\"npcs\\": [\\n");
                for (int i = 0; i < NPCS.length; i++) {{
                    IdLabel entry = NPCS[i];
                    NPCDefinitionFull npc = entry.id() >= 0 && entry.id() < npcs.length ? npcs[entry.id()] : null;
                    json.append("    {{");
                    json.append("\\"label\\": \\"").append(entry.name()).append("\\", ");
                    json.append("\\"npc_id\\": ").append(entry.id()).append(", ");
                    if (npc != null) {{
                        json.append("\\"name\\": \\"").append(escape(npc.getName())).append("\\", ");
                        json.append("\\"size\\": ").append(npc.getSize()).append(", ");
                        json.append("\\"model_ids\\": ").append(renderIntArray(compactModelIds(npc.getModelIds()))).append(", ");
                        json.append("\\"combat\\": ").append(npc.getCombat()).append(", ");
                        json.append("\\"scale_xy\\": ").append(npc.getScaleXY()).append(", ");
                        json.append("\\"scale_z\\": ").append(npc.getScaleZ()).append(", ");
                        json.append("\\"head_icon\\": ").append(npc.getHeadIcon()).append(", ");
                        json.append("\\"hitbar_sprite\\": ").append(npc.getHitbarSprite()).append(", ");
                        json.append("\\"sprite_id\\": ").append(npc.getSpriteId()).append(", ");
                        json.append("\\"map_function\\": ").append(npc.getMapFunction()).append(", ");
                        json.append("\\"render_emote\\": ").append(npc.getRenderEmote()).append(", ");
                        json.append("\\"original_colours\\": ").append(renderShortArray(npc.getOriginalColours())).append(", ");
                        json.append("\\"modified_colours\\": ").append(renderShortArray(npc.getModifiedColours())).append(", ");
                        json.append("\\"original_textures\\": ").append(renderShortArray(npc.getOriginalTextureColours())).append(", ");
                        json.append("\\"modified_textures\\": ").append(renderShortArray(npc.getModifiedTextureColours()));
                    }} else {{
                        json.append("\\"name\\": \\"missing\\", \\"size\\": 1, \\"model_ids\\": [], \\"combat\\": -1, \\"scale_xy\\": 128, \\"scale_z\\": 128, \\"head_icon\\": -1, \\"hitbar_sprite\\": -1, \\"sprite_id\\": -1, \\"map_function\\": -1, \\"render_emote\\": -1, \\"original_colours\\": [], \\"modified_colours\\": [], \\"original_textures\\": [], \\"modified_textures\\": []");
                    }}
                    json.append("}}");
                    json.append(i + 1 == NPCS.length ? "\\n" : ",\\n");
                }}
                json.append("  ],\\n");

                json.append("  \\"objects\\": [\\n");
                for (int i = 0; i < OBJECTS.length; i++) {{
                    IdLabel entry = OBJECTS[i];
                    ObjectDefinitionFull object = entry.id() >= 0 && entry.id() < objects.length ? objects[entry.id()] : null;
                    json.append("    {{");
                    json.append("\\"label\\": \\"").append(entry.name()).append("\\", ");
                    json.append("\\"object_id\\": ").append(entry.id()).append(", ");
                    if (object != null) {{
                        json.append("\\"name\\": \\"").append(escape(object.getName())).append("\\", ");
                        json.append("\\"size_x\\": ").append(object.getSizeX()).append(", ");
                        json.append("\\"size_y\\": ").append(object.getSizeY()).append(", ");
                        json.append("\\"model_ids\\": ").append(renderIntArray(flattenObjectModelIds(object.getModelIds()))).append(", ");
                        json.append("\\"model_size_x\\": ").append(object.getModelSizeX()).append(", ");
                        json.append("\\"model_size_y\\": ").append(object.getModelSizeY()).append(", ");
                        json.append("\\"model_size_z\\": ").append(object.getModelSizeZ()).append(", ");
                        json.append("\\"offset_x\\": ").append(object.getOffsetX()).append(", ");
                        json.append("\\"offset_y\\": ").append(object.getOffsetY()).append(", ");
                        json.append("\\"offset_z\\": ").append(object.getOffsetZ()).append(", ");
                        json.append("\\"mapscene\\": ").append(object.getMapscene()).append(", ");
                        json.append("\\"interactive\\": ").append(object.getInteractive()).append(", ");
                        json.append("\\"original_colours\\": ").append(renderShortArray(object.getOriginalColours())).append(", ");
                        json.append("\\"modified_colours\\": ").append(renderShortArray(object.getModifiedColours())).append(", ");
                        json.append("\\"original_textures\\": ").append(renderShortArray(object.getOriginalTextureColours())).append(", ");
                        json.append("\\"modified_textures\\": ").append(renderShortArray(object.getModifiedTextureColours()));
                    }} else {{
                        json.append("\\"name\\": \\"missing\\", \\"size_x\\": 1, \\"size_y\\": 1, \\"model_ids\\": [], \\"model_size_x\\": 128, \\"model_size_y\\": 128, \\"model_size_z\\": 128, \\"offset_x\\": 0, \\"offset_y\\": 0, \\"offset_z\\": 0, \\"mapscene\\": -1, \\"interactive\\": -1, \\"original_colours\\": [], \\"modified_colours\\": [], \\"original_textures\\": [], \\"modified_textures\\": []");
                    }}
                    json.append("}}");
                    json.append(i + 1 == OBJECTS.length ? "\\n" : ",\\n");
                }}
                json.append("  ],\\n");

                json.append("  \\"identity_kits\\": [\\n");
                for (int i = 0; i < PLAYER_LOOK_KIT_IDS.length; i++) {{
                    int kitId = PLAYER_LOOK_KIT_IDS[i];
                    IdentityKitDefinition kit = kitId >= 0 && kitId < identityKits.length ? identityKits[kitId] : null;
                    json.append("    {{");
                    json.append("\\"kit_id\\": ").append(kitId).append(", ");
                    if (kit != null) {{
                        json.append("\\"body_part_id\\": ").append(kit.getBodyPartId()).append(", ");
                        json.append("\\"model_ids\\": ").append(renderIntArray(compactModelIds(kit.getModelIds()))).append(", ");
                        json.append("\\"head_models\\": ").append(renderIntArray(compactModelIds(kit.getHeadModels()))).append(", ");
                        json.append("\\"original_colours\\": ").append(renderShortArray(kit.getOriginalColours())).append(", ");
                        json.append("\\"modified_colours\\": ").append(renderShortArray(kit.getModifiedColours())).append(", ");
                        json.append("\\"original_textures\\": ").append(renderShortArray(kit.getOriginalTextureColours())).append(", ");
                        json.append("\\"modified_textures\\": ").append(renderShortArray(kit.getModifiedTextureColours()));
                    }} else {{
                        json.append("\\"body_part_id\\": -1, \\"model_ids\\": [], \\"head_models\\": [], \\"original_colours\\": [], \\"modified_colours\\": [], \\"original_textures\\": [], \\"modified_textures\\": []");
                    }}
                    json.append("}}");
                    json.append(i + 1 == PLAYER_LOOK_KIT_IDS.length ? "\\n" : ",\\n");
                }}
                json.append("  ],\\n");

                json.append("  \\"model_exports\\": [\\n");
                for (int i = 0; i < modelExports.size(); i++) {{
                    ExportedModel model = modelExports.get(i);
                    json.append("    {{");
                    json.append("\\"owner_kind\\": \\"").append(escape(model.ownerKind())).append("\\", ");
                    json.append("\\"owner_label\\": \\"").append(escape(model.ownerLabel())).append("\\", ");
                    json.append("\\"owner_id\\": ").append(model.ownerId()).append(", ");
                    json.append("\\"usage\\": \\"").append(escape(model.usage())).append("\\", ");
                    json.append("\\"model_id\\": ").append(model.modelId()).append(", ");
                    json.append("\\"filename\\": \\"models/").append(escape(model.filename())).append("\\", ");
                    json.append("\\"byte_length\\": ").append(model.byteLength());
                    json.append("}}");
                    json.append(i + 1 == modelExports.size() ? "\\n" : ",\\n");
                }}
                json.append("  ],\\n");

                json.append("  \\"sprites\\": [\\n");
                for (int i = 0; i < sprites.size(); i++) {{
                    ExportedSprite sprite = sprites.get(i);
                    json.append("    {{");
                    json.append("\\"name\\": \\"").append(sprite.name()).append("\\", ");
                    json.append("\\"sprite_id\\": ").append(sprite.id()).append(", ");
                    json.append("\\"filename\\": \\"sprites/").append(sprite.filename()).append("\\", ");
                    json.append("\\"width\\": ").append(sprite.width()).append(", ");
                    json.append("\\"height\\": ").append(sprite.height());
                    json.append("}}");
                    json.append(i + 1 == sprites.size() ? "\\n" : ",\\n");
                }}
                json.append("  ]\\n");
                json.append("}}\\n");
                Files.writeString(output, json.toString(), StandardCharsets.UTF_8);
            }}

            private record CacheProbe(
                List<String> mapArchiveNames,
                List<String> objectArchiveNames,
                boolean objectArchiveAccessible,
                String objectArchiveBlocker
            ) {{}}

            private static String renderStringArray(List<String> values) {{
                StringBuilder json = new StringBuilder("[");
                for (int index = 0; index < values.size(); index++) {{
                    if (index > 0) {{
                        json.append(", ");
                    }}
                    json.append("\\"").append(escape(values.get(index))).append("\\"");
                }}
                json.append("]");
                return json.toString();
            }}

            private static String renderIntArray(int[] values) {{
                if (values == null || values.length == 0) {{
                    return "[]";
                }}
                StringBuilder json = new StringBuilder("[");
                for (int index = 0; index < values.length; index++) {{
                    if (index > 0) {{
                        json.append(", ");
                    }}
                    json.append(values[index]);
                }}
                json.append("]");
                return json.toString();
            }}

            private static String renderShortArray(short[] values) {{
                if (values == null || values.length == 0) {{
                    return "[]";
                }}
                StringBuilder json = new StringBuilder("[");
                for (int index = 0; index < values.length; index++) {{
                    if (index > 0) {{
                        json.append(", ");
                    }}
                    json.append((int) values[index]);
                }}
                json.append("]");
                return json.toString();
            }}

            private static String escape(String value) {{
                return value
                    .replace("\\\\", "\\\\\\\\")
                    .replace("\\"", "\\\\\\"")
                    .replace("\\n", "\\\\n");
            }}
        }}
        """
    ).strip() + "\n"
