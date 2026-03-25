from __future__ import annotations

import csv
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import tomllib

from fight_caves_rl.utils.paths import legacy_headless_env_root, legacy_rsps_root, workspace_root


FIGHT_CAVES_REGION_WORLD_MIN_X = 2396 - 24
FIGHT_CAVES_REGION_WORLD_MAX_X = 2396 + 24
FIGHT_CAVES_REGION_WORLD_MIN_Y = 5088 - 24
FIGHT_CAVES_REGION_WORLD_MAX_Y = 5088 + 80
FIGHT_CAVES_CACHE_REGIONS: tuple[tuple[int, int], ...] = (
    (37, 79),
    (37, 80),
)

VIEWER_ASSET_BUNDLE_ID = "fight_caves_scene_slice_v1"
VIEWER_CACHE_INPUT_RELATIVE = Path("reference/legacy-headless-env/data/cache")
VIEWER_EMPTY_RSPS_CACHE_RELATIVE = Path("reference/legacy-rsps/data/cache")

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
    ("shark", 385),
    ("prayer_potion_4", 2434),
    ("adamant_bolts", 9143),
    ("rune_crossbow", 9185),
)

FIGHT_CAVES_NPC_EXPORTS: tuple[tuple[str, int], ...] = (
    ("tz_kih", 2734),
    ("tz_kih_spawn", 2735),
    ("tz_kek", 2736),
    ("tz_kek_spawn", 2738),
    ("yt_mejkot", 2741),
    ("ket_zek", 2743),
    ("tztok_jad", 2745),
)

FIGHT_CAVES_INTERFACE_EXPORTS: tuple[int, ...] = (
    316,
    149,
    271,
    387,
    541,
    749,
)


def generated_viewer_assets_root() -> Path:
    return workspace_root() / "demo-env" / "assets" / "generated"


def generated_viewer_bundle_path() -> Path:
    return generated_viewer_assets_root() / "fight_caves_scene_slice_v1.json"


def generated_viewer_manifest_path() -> Path:
    return generated_viewer_assets_root() / "fight_caves_export_manifest_v1.json"


def generated_viewer_sprites_root() -> Path:
    return generated_viewer_assets_root() / "sprites"


def validated_viewer_cache_input_path() -> Path:
    return (legacy_headless_env_root() / "data" / "cache").resolve()


def empty_rsps_cache_input_path() -> Path:
    return (legacy_rsps_root() / "data" / "cache").resolve()


def ensure_native_viewer_asset_bundle(*, force_rebuild: bool = False) -> Path:
    generated_root = generated_viewer_assets_root()
    bundle_path = generated_viewer_bundle_path()
    manifest_path = generated_viewer_manifest_path()
    sprites_root = generated_viewer_sprites_root()
    generated_root.mkdir(parents=True, exist_ok=True)
    sprites_root.mkdir(parents=True, exist_ok=True)

    cache_path = validated_viewer_cache_input_path()
    _validate_cache_input(cache_path)
    fingerprint = _asset_export_fingerprint(cache_path)

    if (
        not force_rebuild
        and bundle_path.is_file()
        and manifest_path.is_file()
        and _manifest_matches(manifest_path, fingerprint)
    ):
        return bundle_path

    with tempfile.TemporaryDirectory(prefix="fc-viewer-assets-") as temp_dir_text:
        temp_dir = Path(temp_dir_text)
        raw_dir = temp_dir / "raw"
        raw_dir.mkdir(parents=True, exist_ok=True)
        _run_java_exporter(cache_path=cache_path, output_dir=raw_dir)
        bundle = _assemble_bundle(raw_dir)
        _write_generated_assets(bundle, raw_dir, generated_root)

    manifest_path.write_text(
        json.dumps(
            {
                "fingerprint": fingerprint,
                "bundle_path": str(bundle_path),
                "sprites_root": str(sprites_root),
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
        legacy_rsps_root() / "data" / "minigame" / "tzhaar_fight_cave" / "tzhaar_fight_cave.areas.toml",
        legacy_rsps_root() / "data" / "minigame" / "tzhaar_fight_cave" / "tzhaar_fight_cave.ifaces.toml",
        legacy_rsps_root() / "data" / "entity" / "player" / "inventory" / "inventory.ifaces.toml",
        legacy_rsps_root() / "data" / "entity" / "player" / "inventory" / "inventory_side.ifaces.toml",
        legacy_rsps_root() / "data" / "entity" / "player" / "modal" / "icon.items.toml",
        legacy_rsps_root() / "data" / "fight_caves_demo" / "saves" / "fcdemo01.toml",
        legacy_headless_env_root() / "assets" / "fight-caves-header.png",
    ):
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


def _assemble_bundle(raw_dir: Path) -> dict[str, object]:
    metadata = json.loads((raw_dir / "metadata.json").read_text(encoding="utf-8"))
    terrain_tiles = _load_terrain_tiles(raw_dir / "terrain_tiles.csv")
    palette = _build_palette(terrain_tiles)

    fight_caves_ifaces = _load_legacy_semantic_table(
        legacy_rsps_root() / "data" / "minigame" / "tzhaar_fight_cave" / "tzhaar_fight_cave.ifaces.toml"
    )
    inventory_ifaces = _load_legacy_semantic_table(
        legacy_rsps_root() / "data" / "entity" / "player" / "inventory" / "inventory.ifaces.toml"
    )
    inventory_side_ifaces = _load_legacy_semantic_table(
        legacy_rsps_root() / "data" / "entity" / "player" / "inventory" / "inventory_side.ifaces.toml"
    )
    icon_items = tomllib.loads(
        (legacy_rsps_root() / "data" / "entity" / "player" / "modal" / "icon.items.toml").read_text(encoding="utf-8")
    )
    demo_save_text = (legacy_rsps_root() / "data" / "fight_caves_demo" / "saves" / "fcdemo01.toml").read_text(
        encoding="utf-8"
    )

    bundle = {
        "asset_id": VIEWER_ASSET_BUNDLE_ID,
        "description": (
            "First legacy-asset-derived Fight Caves scene slice for the native viewer. "
            "Terrain and HUD sprites come from the validated headless cache plus RSPS semantic tables."
        ),
        "source_refs": [
            str(VIEWER_CACHE_INPUT_RELATIVE),
            "reference/legacy-rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.areas.toml",
            "reference/legacy-rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.ifaces.toml",
            "reference/legacy-rsps/data/entity/player/inventory/inventory.ifaces.toml",
            "reference/legacy-rsps/data/entity/player/inventory/inventory_side.ifaces.toml",
            "reference/legacy-rsps/data/entity/player/modal/icon.items.toml",
            "reference/legacy-rsps/data/fight_caves_demo/saves/fcdemo01.toml",
        ],
        "cache_validation": {
            "preferred_input": str(VIEWER_CACHE_INPUT_RELATIVE),
            "preferred_input_valid": True,
            "rsps_cache_input": str(VIEWER_EMPTY_RSPS_CACHE_RELATIVE),
            "rsps_cache_empty": _directory_is_effectively_empty(empty_rsps_cache_input_path()),
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
            "regions": [f"{region_x}_{region_y}" for region_x, region_y in FIGHT_CAVES_CACHE_REGIONS],
            "world_bounds": {
                "min_x": FIGHT_CAVES_REGION_WORLD_MIN_X,
                "max_x": FIGHT_CAVES_REGION_WORLD_MAX_X,
                "min_y": FIGHT_CAVES_REGION_WORLD_MIN_Y,
                "max_y": FIGHT_CAVES_REGION_WORLD_MAX_Y,
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
        "sprites": metadata["sprites"],
        "hud_sprite_files": _hud_sprite_file_map(metadata["sprites"]),
        "items": metadata["items"],
        "npcs": metadata["npcs"],
        "blocked_categories": [
            "fight_caves_map_object_archive_props",
            "inventory_item_icons_from_item_models",
            "npc_model_renderables",
            "player_model_renderables",
        ],
        "blocked_notes": [
            metadata["cache_validation"]["object_archive_blocker"],
            "The validated cache exposes real item and NPC model metadata, but this checkout does not include a committed offline item- or model-render pipeline for those visuals.",
            "DV4b therefore exports real terrain, real HUD sprites, and real entity metadata while leaving richer item and model art explicit for the follow-on viewer passes.",
        ],
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

    sprites_root = generated_viewer_sprites_root()
    if sprites_root.exists():
        shutil.rmtree(sprites_root)
    shutil.copytree(raw_dir / "sprites", sprites_root)


def _java_exporter_source() -> str:
    sprite_entries = ",\n".join(
        f'        new SpriteSpec("{name}", {sprite_id})'
        for name, sprite_id in SPRITE_EXPORTS
    )
    region_entries = ",\n".join(
        f"        new RegionSpec({region_x}, {region_y})"
        for region_x, region_y in FIGHT_CAVES_CACHE_REGIONS
    )
    item_entries = ",\n".join(
        f'        new IdLabel("{name}", {item_id})'
        for name, item_id in FIGHT_CAVES_ITEM_EXPORTS
    )
    npc_entries = ",\n".join(
        f'        new IdLabel("{name}", {npc_id})'
        for name, npc_id in FIGHT_CAVES_NPC_EXPORTS
    )
    interface_entries = ", ".join(str(interface_id) for interface_id in FIGHT_CAVES_INTERFACE_EXPORTS)
    return textwrap.dedent(
        f"""
        import sim.cache.Cache;
        import sim.cache.FileCache;
        import sim.cache.Index;
        import sim.cache.config.data.OverlayDefinition;
        import sim.cache.config.data.UnderlayDefinition;
        import sim.cache.config.decoder.OverlayDecoder;
        import sim.cache.config.decoder.UnderlayDecoder;
        import sim.cache.definition.data.IndexedSprite;
        import sim.cache.definition.data.InterfaceComponentDefinitionFull;
        import sim.cache.definition.data.InterfaceDefinitionFull;
        import sim.cache.definition.data.ItemDefinitionFull;
        import sim.cache.definition.data.NPCDefinitionFull;
        import sim.cache.definition.data.SpriteDefinition;
        import sim.cache.definition.decoder.InterfaceDecoderFull;
        import sim.cache.definition.decoder.ItemDecoderFull;
        import sim.cache.definition.decoder.NPCDecoderFull;
        import sim.cache.definition.decoder.SpriteDecoder;

        import java.io.BufferedWriter;
        import java.io.IOException;
        import java.nio.charset.StandardCharsets;
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.util.ArrayList;
        import java.util.List;
        import javax.imageio.ImageIO;

        public final class FightCavesAssetExporter {{
            private static final int WORLD_MIN_X = {FIGHT_CAVES_REGION_WORLD_MIN_X};
            private static final int WORLD_MAX_X = {FIGHT_CAVES_REGION_WORLD_MAX_X};
            private static final int WORLD_MIN_Y = {FIGHT_CAVES_REGION_WORLD_MIN_Y};
            private static final int WORLD_MAX_Y = {FIGHT_CAVES_REGION_WORLD_MAX_Y};
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

            private record RegionSpec(int x, int y) {{}}
            private record SpriteSpec(String name, int id) {{}}
            private record IdLabel(String name, int id) {{}}
            private record ExportedSprite(String name, int id, String filename, int width, int height) {{}}

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
                SpriteDefinition[] sprites = new SpriteDecoder().load(cache);

                List<ExportedSprite> exportedSprites = exportSprites(sprites, out.resolve("sprites"));
                writeMetadataJson(
                    out.resolve("metadata.json"),
                    interfaces,
                    items,
                    npcs,
                    exportedSprites,
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

            private static void writeMetadataJson(
                Path output,
                InterfaceDefinitionFull[] interfaces,
                ItemDefinitionFull[] items,
                NPCDefinitionFull[] npcs,
                List<ExportedSprite> sprites,
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
                        json.append("\\"sprite_yaw\\": ").append(item.getSpriteCameraYaw());
                    }} else {{
                        json.append("\\"name\\": \\"missing\\", \\"model_id\\": -1, \\"sprite_scale\\": 0, \\"sprite_pitch\\": 0, \\"sprite_yaw\\": 0");
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
                        int modelCount = npc.getModelIds() == null ? 0 : npc.getModelIds().length;
                        json.append("\\"name\\": \\"").append(escape(npc.getName())).append("\\", ");
                        json.append("\\"size\\": ").append(npc.getSize()).append(", ");
                        json.append("\\"model_count\\": ").append(modelCount).append(", ");
                        json.append("\\"sprite_id\\": ").append(npc.getSpriteId()).append(", ");
                        json.append("\\"map_function\\": ").append(npc.getMapFunction());
                    }} else {{
                        json.append("\\"name\\": \\"missing\\", \\"size\\": 1, \\"model_count\\": 0, \\"sprite_id\\": -1, \\"map_function\\": -1");
                    }}
                    json.append("}}");
                    json.append(i + 1 == NPCS.length ? "\\n" : ",\\n");
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

            private static String escape(String value) {{
                return value
                    .replace("\\\\", "\\\\\\\\")
                    .replace("\\"", "\\\\\\"")
                    .replace("\\n", "\\\\n");
            }}
        }}
        """
    ).strip() + "\n"
