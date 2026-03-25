"""Export Fight Caves terrain from OSRS cache to a binary .terrain file.

Handles VoidPS-specific floor definition opcodes that the PufferLib
terrain exporter doesn't support.

Output format: TERR binary compatible with PufferLib osrs_pvp_terrain.h.

Usage:
    python3 demo-env/scripts/export_fc_terrain.py
"""

import gzip
import io
import math
import struct
import sys
from dataclasses import dataclass, field
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent
ROOT_DIR = SCRIPTS_DIR.parent.parent
PUFFERLIB_SCRIPTS = ROOT_DIR.parent / "pufferlib" / "pufferlib" / "ocean" / "osrs_pvp" / "scripts"
sys.path.insert(0, str(SCRIPTS_DIR))
sys.path.insert(0, str(PUFFERLIB_SCRIPTS))

from dat2_cache_reader import Dat2CacheReader

CACHE_PATH = ROOT_DIR / "reference" / "legacy-headless-env" / "data" / "cache"
ASSETS_DIR = ROOT_DIR / "demo-env" / "assets"

# FC regions
FC_REGIONS = [(37, 79), (37, 80), (38, 80), (39, 80)]
FC_BASE_X = 37 * 64  # 2368
FC_BASE_Y = 79 * 64  # 5056

# OSRS constants
REGION_SIZE = 64
HEIGHT_LEVELS = 4


# ======================================================================== #
# Floor definitions (VoidPS opcode format)                                   #
# ======================================================================== #

@dataclass
class FloorDef:
    floor_id: int = 0
    rgb: int = 0
    texture: int = -1
    scale: int = 512
    hide_underlay: bool = True
    secondary_rgb: int = -1
    hue: int = 0
    saturation: int = 0
    lightness: int = 0
    color: int = 0


def _rgb_to_hsl(rgb: int, flo: FloorDef):
    r = ((rgb >> 16) & 0xFF) / 256.0
    g = ((rgb >> 8) & 0xFF) / 256.0
    b = (rgb & 0xFF) / 256.0
    mn = min(r, g, b)
    mx = max(r, g, b)
    light = (mn + mx) / 2.0
    sat = 0.0
    hue = 0.0
    if mn != mx:
        d = mx - mn
        sat = d / (2.0 - mn - mx) if light >= 0.5 else d / (mn + mx)
        if mx == r:
            hue = (g - b) / d + (6.0 if g < b else 0.0)
        elif mx == g:
            hue = (b - r) / d + 2.0
        else:
            hue = (r - g) / d + 4.0
        hue /= 6.0
    flo.hue = int(hue * 256.0)
    flo.saturation = int(sat * 256.0)
    flo.lightness = int(light * 256.0)
    if flo.saturation < 0:
        flo.saturation = 0
    elif flo.saturation > 255:
        flo.saturation = 255
    if flo.lightness < 0:
        flo.lightness = 0
    elif flo.lightness > 255:
        flo.lightness = 255
    if light > 0.5:
        flo.color = int((1.0 - light) * sat * 512.0)
    else:
        flo.color = int(light * sat * 512.0)
    if flo.color < 1:
        flo.color = 1
    flo.hue = int(hue * flo.color)
    half = flo.color >> 1
    flo.lightness = int(light * 256.0)


def decode_underlay(data: bytes, fid: int) -> FloorDef:
    flo = FloorDef(floor_id=fid)
    buf = io.BytesIO(data)
    while True:
        op = buf.read(1)
        if not op or op[0] == 0:
            break
        opc = op[0]
        if opc == 1:
            flo.rgb = (buf.read(1)[0] << 16) | (buf.read(1)[0] << 8) | buf.read(1)[0]
        elif opc == 2:
            flo.texture = struct.unpack(">H", buf.read(2))[0]
            if flo.texture == 65535:
                flo.texture = -1
        elif opc == 3:
            flo.scale = struct.unpack(">H", buf.read(2))[0] << 2
        elif opc == 4:
            pass  # blockShadow = false
        elif opc == 5:
            pass  # flag
        else:
            break
    _rgb_to_hsl(flo.rgb, flo)
    return flo


def decode_overlay(data: bytes, fid: int) -> FloorDef:
    flo = FloorDef(floor_id=fid)
    buf = io.BytesIO(data)
    while True:
        op = buf.read(1)
        if not op or op[0] == 0:
            break
        opc = op[0]
        if opc == 1:
            flo.rgb = (buf.read(1)[0] << 16) | (buf.read(1)[0] << 8) | buf.read(1)[0]
        elif opc == 2:
            flo.texture = buf.read(1)[0]
        elif opc == 3:
            flo.texture = struct.unpack(">H", buf.read(2))[0]
            if flo.texture == 65535:
                flo.texture = -1
        elif opc == 5:
            flo.hide_underlay = False
        elif opc == 7:
            flo.secondary_rgb = (buf.read(1)[0] << 16) | (buf.read(1)[0] << 8) | buf.read(1)[0]
        elif opc == 8:
            pass  # anInt961
        elif opc == 9:
            flo.scale = struct.unpack(">H", buf.read(2))[0] << 2
        elif opc == 10:
            pass  # blockShadow
        elif opc == 11:
            buf.read(1)  # anInt3633
        elif opc == 12:
            pass  # underlayOverrides
        elif opc == 13:
            buf.read(3)  # waterColour
        elif opc == 14:
            buf.read(1)  # waterScale
        elif opc == 16:
            buf.read(1)  # waterIntensity
        else:
            break
    if flo.secondary_rgb != -1:
        _rgb_to_hsl(flo.secondary_rgb, flo)
    _rgb_to_hsl(flo.rgb, flo)
    return flo


# ======================================================================== #
# Terrain parsing (per-tile heightmap, underlay, overlay, shape)             #
# ======================================================================== #

@dataclass
class TileData:
    height: int = 0
    underlay_id: int = 0
    overlay_id: int = 0
    shape: int = 0
    settings: int = 0


def parse_terrain_data(data: bytes) -> list[list[list[TileData]]]:
    """Parse OSRS terrain binary into [plane][x][y] tile array."""
    buf = io.BytesIO(data)
    tiles = [[[TileData() for _ in range(REGION_SIZE)]
              for _ in range(REGION_SIZE)]
             for _ in range(HEIGHT_LEVELS)]

    for plane in range(HEIGHT_LEVELS):
        for x in range(REGION_SIZE):
            for y in range(REGION_SIZE):
                tile = tiles[plane][x][y]
                while True:
                    b = buf.read(1)
                    if not b:
                        break
                    v = b[0]
                    if v == 0:
                        break
                    elif v == 1:
                        h = buf.read(1)[0]
                        tile.height = h
                        break
                    elif v <= 49:
                        tile.overlay_id = struct.unpack(">h", buf.read(2))[0]
                        tile.shape = (v - 2) // 4
                        # rotation = (v - 2) & 3
                    elif v <= 81:
                        tile.settings = v - 49
                    else:
                        tile.underlay_id = v - 81
    return tiles


# ======================================================================== #
# Terrain mesh generation                                                    #
# ======================================================================== #

JAGEX_NOISE_TABLE = None

def _init_noise():
    global JAGEX_NOISE_TABLE
    if JAGEX_NOISE_TABLE is not None:
        return
    JAGEX_NOISE_TABLE = [0.0] * 512
    import random
    rng = random.Random(0)
    for i in range(512):
        JAGEX_NOISE_TABLE[i] = rng.random() * 2.0 - 1.0


def _noise(x, y):
    _init_noise()
    X = int(x) & 255
    Y = int(y) & 255
    return JAGEX_NOISE_TABLE[X + Y * 16 & 511]


def _calc_height(abs_x, abs_y):
    """Calculate tile height from OSRS procedural noise (MapRegion.java)."""
    n1 = _noise(abs_x + 45365, abs_y + 91923) * 4 - 2
    n2 = _noise(abs_x * 2 + 10294, abs_y * 2 + 37821) * 2 - 1
    n3 = _noise(abs_x * 4 + 34567, abs_y * 4 + 12345) * 1 - 0.5
    return int(n1 + n2 + n3) * -8


def hsl_to_rgb(hue, sat, light):
    """Convert HSL (each 0-255) to RGB packed int."""
    h = hue / 256.0
    s = sat / 256.0
    li = light / 256.0
    if s == 0:
        v = int(li * 255)
        return (v << 16) | (v << 8) | v

    def hue2rgb(p, q, t):
        if t < 0: t += 1
        if t > 1: t -= 1
        if t < 1/6: return p + (q - p) * 6 * t
        if t < 1/2: return q
        if t < 2/3: return p + (q - p) * (2/3 - t) * 6
        return p

    q = li * (1 + s) if li < 0.5 else li + s - li * s
    p = 2 * li - q
    r = int(hue2rgb(p, q, h + 1/3) * 255)
    g = int(hue2rgb(p, q, h) * 255)
    b = int(hue2rgb(p, q, h - 1/3) * 255)
    return (min(255, max(0, r)) << 16) | (min(255, max(0, g)) << 8) | min(255, max(0, b))


def build_terrain_mesh(reader, underlays, overlays):
    """Build vertex-colored terrain mesh from FC map regions."""
    # Collect all tile data across regions
    total_w = 4 * REGION_SIZE  # 256 tiles wide (4 regions max)
    total_h = 2 * REGION_SIZE  # 128 tiles tall (2 regions)

    # Use FC_BASE as origin
    verts = []
    colors = []

    for rx, ry in FC_REGIONS:
        m_hash = _djb2(f"m{rx}_{ry}")
        manifest5 = reader.read_index_manifest(5)
        hash_to_gid = {v: k for k, v in manifest5.group_name_hashes.items()}
        m_gid = hash_to_gid.get(m_hash)
        if m_gid is None:
            continue

        raw = reader._read_raw_sectors(5, m_gid)
        if raw is None:
            continue

        # Decompress
        comp_type = raw[0]
        comp_len = struct.unpack(">I", raw[1:5])[0]
        if comp_type == 0:
            terrain_data = raw[5:5+comp_len]
        else:
            payload = raw[9:9+comp_len]
            terrain_data = gzip.decompress(payload)

        tiles = parse_terrain_data(terrain_data)
        base_x = rx * REGION_SIZE
        base_y = ry * REGION_SIZE

        # Generate mesh quads for plane 0
        for lx in range(REGION_SIZE - 1):
            for ly in range(REGION_SIZE - 1):
                wx = base_x + lx - FC_BASE_X
                wy = base_y + ly - FC_BASE_Y

                # Crop to fc_core arena bounds (0..63 x 0..63)
                if wx < 0 or wx >= 63 or wy < 0 or wy >= 63:
                    continue

                tile = tiles[0][lx][ly]

                # Get color — use Fight Caves specific mapping
                # Known FC floor types from cache analysis:
                #   underlay 72 (idx 71): rgb=#030303 (black rock walls)
                #   underlay 118 (idx 117): rgb=#752222 (dark red lava rock)
                #   overlay 28466: FC lava crack overlay (orange-red glow)
                #   overlay 10802: FC secondary overlay (darker lava)
                #   no floor: void/border → black
                rgb = 0x000000  # default: void black
                if tile.overlay_id > 0:
                    ov = overlays.get(tile.overlay_id - 1)
                    if ov and ov.rgb != 0:
                        rgb = ov.rgb
                    elif tile.overlay_id == 28466:
                        # FC primary overlay: lava cracks — orange-red glow
                        rgb = 0xC03010
                    elif tile.overlay_id == 10802:
                        # FC secondary overlay: darker lava border
                        rgb = 0x401008
                    else:
                        rgb = 0x501510  # generic dark red fallback for unknown FC overlays
                elif tile.underlay_id > 0:
                    ul = underlays.get(tile.underlay_id - 1)
                    if ul and ul.rgb != 0:
                        rgb = ul.rgb

                r = (rgb >> 16) & 0xFF
                g = (rgb >> 8) & 0xFF
                b = rgb & 0xFF

                # Height
                h0 = tile.height * -1.0 / 128.0 if tile.height else _calc_height(base_x + lx, base_y + ly) / 128.0
                h1 = tiles[0][lx+1][ly].height * -1.0 / 128.0 if tiles[0][lx+1][ly].height else _calc_height(base_x+lx+1, base_y+ly) / 128.0
                h2 = tiles[0][lx][ly+1].height * -1.0 / 128.0 if tiles[0][lx][ly+1].height else _calc_height(base_x+lx, base_y+ly+1) / 128.0
                h3 = tiles[0][lx+1][ly+1].height * -1.0 / 128.0 if tiles[0][lx+1][ly+1].height else _calc_height(base_x+lx+1, base_y+ly+1) / 128.0

                fx, fy = float(wx), float(wy)

                # Two triangles per quad
                for tri in [(fx, h0, fy, fx+1, h1, fy, fx, h2, fy+1),
                            (fx+1, h1, fy, fx+1, h3, fy+1, fx, h2, fy+1)]:
                    for vi in range(0, 9, 3):
                        verts.extend([tri[vi], tri[vi+1], tri[vi+2]])
                        colors.extend([r, g, b, 255])

    return verts, colors


def _djb2(name):
    h = 0
    for c in name:
        h = ((h << 5) - h + ord(c)) & 0xFFFFFFFF
    return h if h < 0x80000000 else h - 0x100000000


def main():
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)

    print(f"Reading cache from {CACHE_PATH}")
    reader = Dat2CacheReader(CACHE_PATH)

    # Decode floor definitions
    print("Decoding floor definitions (VoidPS format)...")
    underlays = {}
    try:
        ul_files = reader.read_group(2, 1)  # config index, group 1 = underlays
        for fid, data in ul_files.items():
            underlays[fid] = decode_underlay(data, fid)
        print(f"  {len(underlays)} underlays decoded")
    except Exception as e:
        print(f"  Underlay decode error: {e}")

    overlays = {}
    try:
        ov_files = reader.read_group(2, 4)  # config index, group 4 = overlays
        for fid, data in ov_files.items():
            overlays[fid] = decode_overlay(data, fid)
        print(f"  {len(overlays)} overlays decoded")
    except Exception as e:
        print(f"  Overlay decode error: {e}")

    # Build terrain mesh
    print("Building terrain mesh...")
    verts, colors = build_terrain_mesh(reader, underlays, overlays)
    vert_count = len(verts) // 3
    print(f"  {vert_count} vertices, {vert_count // 3} triangles")

    if vert_count == 0:
        print("ERROR: No terrain vertices generated!")
        return

    # Write TERR binary
    output_path = ASSETS_DIR / "fight_caves.terrain"
    print(f"Writing terrain to {output_path}")
    with open(output_path, "wb") as f:
        f.write(struct.pack("<I", 0x54455252))  # "TERR" magic
        f.write(struct.pack("<I", vert_count))
        f.write(struct.pack("<I", len(FC_REGIONS)))
        f.write(struct.pack("<i", 0))  # min_world_x (relative)
        f.write(struct.pack("<i", 0))  # min_world_y (relative)
        # Vertices
        for v in verts:
            f.write(struct.pack("<f", v))
        # Colors
        for c in colors:
            f.write(struct.pack("B", c))

    print(f"  Wrote {output_path.stat().st_size} bytes")

    # Print some floor def samples
    print("\nSample underlays:")
    for fid in sorted(underlays.keys())[:5]:
        u = underlays[fid]
        print(f"  {fid}: rgb=0x{u.rgb:06x} tex={u.texture} scale={u.scale}")
    print("Sample overlays:")
    for fid in sorted(overlays.keys())[:5]:
        o = overlays[fid]
        print(f"  {fid}: rgb=0x{o.rgb:06x} tex={o.texture} scale={o.scale}")


if __name__ == "__main__":
    main()
