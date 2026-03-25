"""Export Fight Caves UI sprites from OSRS cache to PNG files.

Extracts prayer icons, hitsplat sprites, and other UI elements.
Uses PufferLib's sprite decoder with our Dat2CacheReader adapter.

Usage:
    python3 demo-env/scripts/export_fc_sprites.py
"""

import sys
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent
ROOT_DIR = SCRIPTS_DIR.parent.parent
PUFFERLIB_SCRIPTS = ROOT_DIR.parent / "pufferlib" / "pufferlib" / "ocean" / "osrs_pvp" / "scripts"
sys.path.insert(0, str(SCRIPTS_DIR))
sys.path.insert(0, str(PUFFERLIB_SCRIPTS))

from dat2_cache_reader import Dat2CacheReader
from export_sprites_modern import decode_sprites, save_sprite_png

CACHE_PATH = ROOT_DIR / "reference" / "legacy-headless-env" / "data" / "cache"
ASSETS_DIR = ROOT_DIR / "demo-env" / "assets" / "sprites"

# Sprite IDs to export (from osrs_pvp_gui.h sprite source comments)
PRAYER_ICONS = {
    # Protection prayers (the ones we use in Fight Caves)
    # Enabled state sprite IDs
    128: "protect_magic_on",
    129: "protect_missiles_on",
    130: "protect_melee_on",
    # Disabled state
    148: "protect_magic_off",
    149: "protect_missiles_off",
    150: "protect_melee_off",
}

HITSPLAT_SPRITES = {
    # Hitsplat background sprites (if they exist at these IDs)
    # OSRS hitsplat sprites are in a different index/format
}

# Additional useful sprites
UI_SPRITES = {
    # Tab icons
    168: "tab_inventory",
    779: "tab_prayer",
    780: "tab_combat",
    # Equipment slot backgrounds
    156: "slot_head",
    159: "slot_weapon",
    161: "slot_body",
    163: "slot_legs",
    # Special attack bar
    657: "special_bar",
}


def main():
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)

    print(f"Reading cache from {CACHE_PATH}")
    reader = Dat2CacheReader(CACHE_PATH)

    all_sprites = {}
    all_sprites.update(PRAYER_ICONS)
    all_sprites.update(UI_SPRITES)

    exported = 0
    failed = 0
    for sprite_id, name in sorted(all_sprites.items()):
        try:
            data = reader.read_container(8, sprite_id)
            if data is None:
                print(f"  {name} (sprite {sprite_id}): not in cache")
                failed += 1
                continue

            frames = decode_sprites(sprite_id, data)
            if not frames:
                print(f"  {name} (sprite {sprite_id}): decode returned 0 frames")
                failed += 1
                continue

            for i, frame in enumerate(frames):
                suffix = f"_{i}" if len(frames) > 1 else ""
                out_path = ASSETS_DIR / f"{name}{suffix}.png"
                save_sprite_png(frame, out_path)
                print(f"  {name}{suffix}: {frame.width}x{frame.height} → {out_path.name}")
                exported += 1

        except Exception as e:
            print(f"  {name} (sprite {sprite_id}): error: {e}")
            failed += 1

    print(f"\nDone: {exported} sprites exported, {failed} failed")


if __name__ == "__main__":
    main()
