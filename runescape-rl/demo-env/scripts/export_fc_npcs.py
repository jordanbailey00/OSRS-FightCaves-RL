"""Export Fight Caves NPC models from OSRS cache to MDL2 binary.

Reads the .dat2/.idx* cache, decodes NPC definitions to get model IDs,
then extracts and decodes 3D models into the PufferLib MDL2 binary format.

Output: fight_caves_npcs.models + fc_npc_models.h

Usage:
    python3 demo-env/scripts/export_fc_npcs.py
"""

import io
import struct
import sys
from pathlib import Path

# Setup imports
SCRIPTS_DIR = Path(__file__).parent
ROOT_DIR = SCRIPTS_DIR.parent.parent
PUFFERLIB_SCRIPTS = ROOT_DIR / "pufferlib" / "pufferlib" / "ocean" / "osrs_pvp" / "scripts"
sys.path.insert(0, str(SCRIPTS_DIR))
sys.path.insert(0, str(PUFFERLIB_SCRIPTS))

from dat2_cache_reader import Dat2CacheReader
from export_models import (
    decode_model,
    write_models_binary,
    ModelData,
)

CACHE_PATH = ROOT_DIR / "reference" / "legacy-headless-env" / "data" / "cache"
ASSETS_DIR = ROOT_DIR / "demo-env" / "assets"

# FC NPC definitions
FC_NPCS = [
    # (npc_id, name, c_enum)
    (2734, "Tz-Kih", "NPC_TZ_KIH"),
    (2736, "Tz-Kek", "NPC_TZ_KEK"),
    (2738, "Tz-Kek-Sm", "NPC_TZ_KEK_SM"),
    (2739, "Tok-Xil", "NPC_TOK_XIL"),
    (2741, "Yt-MejKot", "NPC_YT_MEJKOT"),
    (2743, "Ket-Zek", "NPC_KET_ZEK"),
    (2745, "TzTok-Jad", "NPC_TZTOK_JAD"),
    (2746, "Yt-HurKot", "NPC_YT_HURKOT"),
]

# Animation IDs from tzhaar_fight_cave.anims.toml
FC_ANIMS = {
    2734: {"idle": 9231, "attack": 9232, "death": 9230},
    2736: {"idle": 9235, "attack": 9233, "death": 9234},
    2738: {"idle": 9235, "attack": 9233, "death": 9234},  # same as Tz-Kek
    2739: {"idle": 9242, "attack": 9245, "death": 9239},
    2741: {"idle": 9248, "attack": 9246, "death": 9247},
    2743: {"idle": 9268, "attack": 9265, "death": 9269},
    2745: {"idle": 9278, "attack": 9277, "death": 9279},
    2746: {"idle": 9253, "attack": 9252, "death": 9257},
}


def read_u8(buf):
    return buf.read(1)[0]


def read_u16(buf):
    return struct.unpack(">H", buf.read(2))[0]


def read_i16(buf):
    return struct.unpack(">h", buf.read(2))[0]


def read_string(buf):
    s = b""
    while True:
        c = buf.read(1)
        if c == b"\x00" or not c:
            break
        s += c
    return s.decode("ascii", errors="replace")


def decode_npc_model_ids(data: bytes) -> list[int]:
    """Extract model IDs from VoidPS NPC definition binary."""
    buf = io.BytesIO(data)
    while True:
        b = buf.read(1)
        if not b:
            return []
        opcode = b[0]
        if opcode == 0:
            return []
        if opcode == 1:
            count = read_u8(buf)
            return [read_u16(buf) for _ in range(count)]
        # Skip other opcodes
        if opcode == 2:
            read_string(buf)
        elif opcode == 12:
            buf.read(1)
        elif 30 <= opcode <= 34:
            read_string(buf)
        elif opcode in (40, 41, 121):
            buf.read(read_u8(buf) * 4)
        elif opcode == 42:
            buf.read(read_u8(buf))
        elif opcode in (93, 99, 107, 109, 111, 141, 143, 158, 159, 162):
            pass
        elif opcode == 95:
            buf.read(2)
        elif opcode in (97, 98, 102, 103, 114, 122, 123, 137, 138, 139, 142):
            buf.read(2)
        elif opcode in (100, 101, 125, 128, 140, 163, 165, 168):
            buf.read(1)
        elif opcode in (106, 118):
            buf.read(2)
            buf.read(2)
            if opcode == 118:
                buf.read(2)
            count = read_u8(buf)
            buf.read((count + 1) * 2)
        elif opcode in (113, 155, 164):
            buf.read(4)
        elif opcode == 119:
            buf.read(1)
        elif opcode == 127:
            buf.read(2)
        elif opcode == 134:
            buf.read(9)
        elif opcode in (135, 136):
            buf.read(3)
        elif 150 <= opcode <= 154:
            read_string(buf)
        elif opcode == 249:
            count = read_u8(buf)
            for _ in range(count):
                is_string = read_u8(buf)
                buf.read(3)
                if is_string:
                    read_string(buf)
                else:
                    buf.read(4)
        elif opcode == 60:
            buf.read(read_u8(buf) * 2)
        elif opcode == 160:
            buf.read(read_u8(buf) * 2)
        else:
            return []
    return []


def main():
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)

    print(f"Reading cache from {CACHE_PATH}")
    reader = Dat2CacheReader(CACHE_PATH)

    # Step 1: Get NPC model IDs from cache
    print("\nDecoding NPC definitions...")
    npc_model_map = {}
    for npc_id, name, c_enum in FC_NPCS:
        gid = npc_id // 128
        fid = npc_id % 128
        files = reader.read_group(18, gid)
        model_ids = decode_npc_model_ids(files[fid])
        if model_ids:
            npc_model_map[npc_id] = model_ids[0]  # primary model
            print(f"  {name} (NPC {npc_id}): model_id={model_ids[0]}")
        else:
            print(f"  {name} (NPC {npc_id}): NO MODEL IDs FOUND")

    # Step 2: Extract and decode 3D models
    print("\nExtracting 3D models...")
    model_list: list[ModelData] = []
    for npc_id, name, c_enum in FC_NPCS:
        mid = npc_model_map.get(npc_id)
        if mid is None:
            continue
        raw = reader.read_container(7, mid)
        if raw is None:
            print(f"  {name}: model {mid} not in cache")
            continue
        try:
            model = decode_model(mid, raw)
            if model is not None:
                model_list.append(model)
                print(f"  {name}: model {mid}, {model.face_count} faces, {model.vertex_count} verts")
            else:
                print(f"  {name}: model {mid} decode returned None")
        except Exception as e:
            print(f"  {name}: model {mid} decode error: {e}")

    # Step 3: Write MDL2 binary
    output_path = ASSETS_DIR / "fight_caves_npcs.models"
    print(f"\nWriting MDL2 binary to {output_path}")
    write_models_binary(output_path, model_list)
    print(f"  Wrote {output_path.stat().st_size} bytes, {len(model_list)} models")

    # Step 4: Write C header
    header_path = ASSETS_DIR / "fc_npc_models.h"
    print(f"\nWriting C header to {header_path}")
    with open(header_path, "w") as f:
        f.write("/* Auto-generated Fight Caves NPC model/animation mappings. */\n")
        f.write("/* Source: OSRS cache + tzhaar_fight_cave.anims.toml */\n\n")
        f.write("#ifndef FC_NPC_MODELS_H\n#define FC_NPC_MODELS_H\n\n")
        f.write("#include <stdint.h>\n\n")
        f.write("typedef struct {\n")
        f.write("    uint16_t npc_id;\n")
        f.write("    uint32_t model_id;\n")
        f.write("    uint32_t idle_anim;\n")
        f.write("    uint32_t attack_anim;\n")
        f.write("    uint32_t death_anim;\n")
        f.write("    const char* name;\n")
        f.write("} FcNpcModelMapping;\n\n")
        f.write(f"static const FcNpcModelMapping FC_NPC_MODEL_MAP[] = {{\n")
        for npc_id, name, c_enum in FC_NPCS:
            mid = npc_model_map.get(npc_id, 0)
            anims = FC_ANIMS.get(npc_id, {})
            idle = anims.get("idle", 65535)
            attack = anims.get("attack", 65535)
            death = anims.get("death", 65535)
            f.write(f"    {{{npc_id}, {mid}, {idle}, {attack}, {death}, \"{name}\"}},\n")
        f.write("};\n\n")
        f.write(f"#define FC_NPC_MODEL_COUNT {len(FC_NPCS)}\n\n")
        f.write("#endif /* FC_NPC_MODELS_H */\n")
    print(f"  Written")

    print("\nDone!")


if __name__ == "__main__":
    main()
