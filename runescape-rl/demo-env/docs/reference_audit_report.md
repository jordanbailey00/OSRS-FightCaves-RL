# Reference Root Audit Report — Porting Oracle for demo-env

Date: 2026-03-30

## 1. Porting Map: Ownership Boundaries

### Backend (Server) — Gameplay/Runtime Truth
| System | Reference Owner | demo-env Equivalent |
|--------|----------------|---------------------|
| Episode reset / starter state | `FightCaveEpisodeInitializer.kt` | `fc_api.h` reset function |
| Wave progression (63 waves) | `TzhaarFightCave.kt` + `tzhaar_fight_cave_waves.toml` | `fc_core.c` wave logic |
| NPC spawn placement (5 areas × 15 rotations) | `TzhaarFightCave.startWave()` | `fc_core.c` spawn logic |
| Instance creation (128×128 tile copy) | `Instances.kt` + `DynamicZones.kt` | Not needed — demo-env is single-instance |
| Movement / collision / pathing | `Movement.kt` + `Collisions.kt` | `fc_core.c` tile movement |
| Combat (accuracy, damage, prayer) | `Attack.kt`, `Damage.kt`, `Prayer.kt` | `fc_core.c` combat |
| Jad telegraph / healers | `TzTokJad.kt`, `JadTelegraph.kt`, `TzHaarHealers.kt` | `fc_core.c` Jad logic |
| Inventory / equipment / consumables | `Inventories.kt`, `Equipment.kt`, `Eating.kt` | `fc_core.c` item use |
| Backend control (action injection) | `FightCaveDemoBackendControl.kt` | `fc_api.h` action interface |

### Frontend (Client) — Scene/Render/UI
| System | Reference Owner | demo-env Equivalent |
|--------|----------------|---------------------|
| Terrain mesh / heightmap rendering | `void-client Class237.java` (decompiled) | `export_fc_terrain.py` → `fc_terrain_loader.h` |
| Loc/object model decode & placement | `void-client Class213.java` (decompiled) | `export_fc_objects.py` → `fc_object_loader.h` |
| 3D model decode (vertices, faces, colors) | `void-client Class64.java` (decompiled) | `mesh_decoder.py` |
| Player composite model (12 body parts) | `void-client Player.java` decode + `AppearanceEncoder.kt` | **Missing** — cylinder placeholder |
| NPC model rendering | `void-client` model cache lookup | `fc_model_loader.h` (pre-exported) |
| Gameframe / tabs / prayer / inventory UI | `void-client Interface*.java` + server packets | **Partial** — 2D sprite overlay |
| Minimap | `void-client Class348_Sub5_Sub1` | **Missing** |
| Hitsplats / health bars / head icons | `void-client Class286_Sub5.java` sprite resolution | **Missing** |

### Cache/Asset Loading
| System | Reference Path | demo-env Path |
|--------|---------------|---------------|
| Cache format | `.dat2 + .idx*` (Displee/Jagex) | Same cache via `dat2_cache_reader.py` |
| Map tiles (terrain) | Index 5: `m{X}_{Y}` | `export_fc_terrain.py` reads index 5 |
| Loc/objects (placements) | Index 5: `l{X}_{Y}` | `export_fc_objects.py` reads index 5 |
| Object definitions | Index 16 (opcode 1/5 for models) | `export_fc_objects.py` reads index 16 |
| Model meshes | Index 7 | `mesh_decoder.py` reads index 7 |
| NPC definitions | Index 18 | `fc_model_loader.h` reads pre-exported |
| Sprites | Index 8 | PNG export pipeline |
| Floor defs (underlay/overlay) | Index 2 | `export_fc_terrain.py` decodes |

### Fight Caves-Specific vs Generic Runtime
| FC-Specific | Generic Runtime |
|-------------|-----------------|
| Wave table (63 waves × 15 rotations) | Tile-based movement + collision |
| 5 spawn areas (NW, SE, SW, S, centre) | NPC AI / aggression |
| Jad telegraph / prayer-check timing | Combat accuracy/damage formula |
| Tz-Kek split on death | Inventory / equipment containers |
| Yt-HurKot heal at Jad half-HP | Prayer drain + protection |
| Canonical loadout (crossbow, d'hide, sharks×20, ppots×8) | Consumable effects |
| Demo recycle (death/leave/complete → fresh episode) | Entity update / interpolation |

---

## 2. What to Port vs Ignore

### 2a. Windows/Bootstrap-Only — NOT Worth Porting
- `run_fight_caves_demo_client.sh` / `.ps1` — WSL↔Windows launcher mechanics
- `build_fight_caves_demo_client.ps1` — JDK8 Windows client JAR build
- `FightCavesDemoAutoLogin.java` — client state polling for auto-login
- `FightCavesDemoBootstrapWindow.java` — Swing loading bar
- `Loader.java` — JFrame bootstrap, CLI arg parsing
- Jagex cache sync to `C:\.jagex_cache_32\` — Windows cache seeding
- JS5/file-server protocol (`FileServer.kt`) — we read cache directly
- Login/ISAAC cipher protocol — we have no network split

### 2b. Core Runtime/Rendering Truths That demo-env MUST Mirror
1. **Terrain height = purely visual** — 8-bit value from cache, used only for mesh vertex Y positions. Does NOT affect gameplay collision (which is tile-grid-based).
2. **Height unit = raw cache byte (0-255)** — Greg's TileLevel uses 512 subunits per tile for interpolation. The height→world transform is: `vertex_Y = tile.height` (scaled into the 512-subunit grid, then projected).
3. **Tile = atomic gameplay unit** — 1 tile = 1 movement step. Region = 64×64 tiles. Zone = 8×8 tiles.
4. **Object shapes (25 types)** determine both collision footprint and model placement offset. Shapes 0-3 are walls (edge-aligned), 4-8 are wall decorations, 9 is diagonal wall, 10-11 are centrepiece objects, 12-21 are roofs, 22 is ground decor.
5. **Object sizeX/sizeY** from definition opcodes 14/15 (default 1×1). Rotation swaps X/Y.
6. **Model coordinates** are in OSRS units: 128 units = 1 tile (so `OSRS_SCALE = 1/128` is correct).
7. **Player appearance = 12 body parts** encoded server-side. Each slot is either equipment model (`id | 0x8000`) or body style (`look + 0x100`).
8. **NPC models** are resolved by NPC definition → model ID array → cache index 7.
9. **Fight Caves region = 9551** = Region(37, 79) base. The 4 regions are (37,79), (37,80), (38,80), (39,80).
10. **Centre tile = (2400, 5088)** in absolute OSRS coords. Relative to FC_BASE (2368, 5056) = tile (32, 32).
11. **Instance offset is a Delta** — server copies original region data into allocated empty region space. demo-env doesn't need instances since it IS the only world.

---

## 3. Concrete Answers from the Reference

### How is the Fight Caves scene actually assembled?
1. Server creates a **small instance** (128×128 tile copy) of region 9551 via `DynamicZones.copy()`.
2. Server teleports player to **centre (2400, 5088)** inside instance coordinates.
3. **Client** loads the terrain (`m37_79`, `m37_80`, etc.) and loc data (`l37_79`, etc.) from cache via JS5.
4. Client decodes heightmaps, builds terrain mesh with underlay/overlay colors, and places loc/object models at their tile positions.
5. Server sends NPC spawn packets per wave; client resolves NPC model IDs from definitions and renders them.

### Terrain, region data, and loc/object loading?
- **Terrain**: Cache index 5, `m{X}_{Y}` archives. Raw bytes decoded as: opcode 0 = end-of-tile, opcode 1 = height (next byte), opcodes 2-49 = overlay, 50-81 = settings, 82+ = underlay.
- **Height per tile**: Single unsigned byte (0-255). Represents visual elevation only.
- **Objects**: Cache index 5, `l{X}_{Y}` archives. Smart-encoded object ID deltas, packed tile positions, shape (bits 2-7 of attr byte), rotation (bits 0-1).
- **Object definitions**: Cache index 16. Opcodes 1/5 give model type + model ID arrays. Opcodes 14/15 give sizeX/sizeY.

### World scale, tile scale, object placement, rotation, scene footprint?
- **1 tile = 128 OSRS model units** (confirmed: `OSRS_SCALE = 1/128`).
- **Client internally uses 512 subunits per tile** for height interpolation and sub-tile positioning.
- **Object rotation**: 4 values (0-3 = 0°/90°/180°/270° around Y axis). Rotation=1: `(vx,vz)→(vz,-vx)`.
- **Scene footprint**: 4 regions = ~128×128 tiles. Active arena ~64×64 tiles centred around (2400, 5088).

### Player appearance/equipment visual assembly?
- Server encodes 12 body parts via `AppearanceEncoder.kt`: Head, Cape, Amulet, Weapon, Chest, Shield, Arms, Legs, Hair, Hands, Feet, Beard.
- Each part: either `equipItemIndex | 0x8000` (equipment model) or `bodyLook + 0x100` (default body).
- 5 body colours encoded (reverse order in packet).
- Client decodes via `Player.method2452()` and assembles composite model from cache.
- **Demo loadout**: coif, rune crossbow, black d'hide body/chaps/vambraces, snakeskin boots, adamant bolts.

### UI/gameframe ownership?
- **Server-owned state**: HP value, prayer points, inventory contents, equipment, active prayers, combat level, wave number.
- **Server-driven UI**: Interface open/close packets, container update packets, variable update packets.
- **Client-rendered**: Gameframe layout, prayer tab icons, inventory grid, minimap, hitsplats, health bars, head icons, all sprites.
- **Fight Caves overlay**: Opened by `TzhaarFightCave.startWave()` via interface packet.

---

## 4. Current demo-env Mismatch List

### 4a. CRITICAL — Wrong Terrain Heights
**Current**: `height * -1.0 / 128.0` — divides raw cache byte by 128, negated.
**Problem**: This creates heights of ~-2.0 for height=255, making the terrain nearly flat (heights range ±0.5 tiles). The reference shows height bytes represent meaningful elevation. The `-1/128` formula treats height as if it were in OSRS model units, but cache height bytes are NOT in the same coordinate space as model vertices.
**Evidence**: Greg's `TileLevel.averageHeight()` uses the raw height values directly in a 512-subunit interpolation grid. The constant 262144 (= 512²) in lighting normal calculation confirms height values are scaled relative to the 512-subunit tile grid.
**Fix needed**: Use height values appropriately scaled. Each height unit represents `1/512` of a tile's horizontal extent in the elevation dimension — or more practically, `height * -8` gives OSRS-unit heights (matching the procedural fallback `_calc_height` which returns `int(n1+n2+n3) * -8`), then divide by 128 for tile-scale → `height * -8 / 128 = height * -1/16`.

### 4b. CRITICAL — Terrain Only Covers 63×63 Tiles (Off-by-One)
**Current**: `if wx < 0 or wx >= 63` crops to 0..62, but FC arena is 64 tiles wide.
**Also**: Only plane 0 exported. This is correct for FC ground level.

### 4c. MAJOR — Object Height Not Terrain-Aligned
**Current**: Objects rendered at Y=0, terrain at Y+0.3 offset. Objects float or sink relative to terrain.
**Reference truth**: Objects sit on the terrain surface at their tile position. The object's model Y=0 should correspond to the terrain height at that tile.
**Fix needed**: Sample terrain height at each object's tile position and apply as Y offset.

### 4d. MAJOR — No Shape-Specific Object Placement
**Current**: All objects placed at tile centre (tile_wx + 0.5, tile_wy + 0.5).
**Reference truth**: Shapes 0-3 (walls) are edge-aligned, not centre-aligned. Shape 10-11 (centrepiece) IS centre-aligned. Shape 22 (ground decor) is centre-aligned. Wall shapes need sub-tile offsets based on rotation.
**Impact**: Walls appear shifted by half a tile from their correct positions.

### 4e. MAJOR — No Multi-Tile Object Support
**Current**: All objects treated as 1×1. No sizeX/sizeY from object definitions.
**Reference truth**: Some FC objects span multiple tiles. Rotation swaps sizeX/sizeY. Collision footprint and visual placement depend on these dimensions.

### 4f. MAJOR — Player is a Cylinder
**Current**: No player model — rendered as a coloured cylinder.
**Reference truth**: 12-part body composite model with equipment overrides. The canonical demo loadout models exist in cache.

### 4g. MODERATE — Terrain Colors are Hardcoded Approximations
**Current**: ~5 hardcoded RGB values for known underlay/overlay IDs.
**Reference truth**: Greg's `ColourPalette` + `MapTileSettings` uses a proper HSL→palette→brightness pipeline with 5×5 sampling/blending. The real terrain has smooth colour gradients, not flat per-tile blocks.

### 4h. MODERATE — No Terrain Height Interpolation
**Current**: Each tile quad has 4 corner heights, but corners from adjacent regions don't blend.
**Reference truth**: `TileLevel.averageHeight()` bilinearly interpolates heights across tile boundaries using the 512-subunit grid.

### 4i. MINOR — Terrain Missing Overlay Path Types
**Current**: All tiles are simple 2-triangle quads.
**Reference truth**: MapConstants defines 15 different tile path types (`firstTileTypeVertices`, etc.) with varying triangle tessellations. Overlay paths create diagonal splits, corner fills, etc. for lava crack detail geometry.

### 4j. MINOR — 5 Model Decode Failures (Old Format)
**Current**: `decode_model()` returns None for models without 0xFF 0xFF sentinel.
**Impact**: Minor — affects only a handful of objects in the scene.

---

## 5. Implementation Plan (Prioritized for Playable Debug Visualizer)

### Priority A: World Scale + Scene Assembly Correctness
1. **Fix terrain height formula**: Change from `h * -1.0 / 128.0` to `h * -1.0 / 16.0` (or equivalently `h * -8 / 128`). This makes heights 8× larger, creating visible terrain relief.
2. **Fix terrain coverage**: Change crop bounds to `0..63` (inclusive both edges), export full 64×64 tile grid.
3. **Align object Y with terrain**: For each object placement, sample the terrain height at its tile position and add as vertical offset.
4. **Remove terrain Y+0.3 hack**: Once heights are correct, the offset shouldn't be needed.

### Priority B: Terrain Construction/Material Correctness
5. **Implement underlay colour blending**: Port Greg's 5×5 sampling window from `MapTileSettings.loadUnderlays()` for smooth colour gradients instead of per-tile flat colours.
6. **Add overlay path tessellation**: Use the 15 tile-type vertex tables from `MapConstants` to create proper sub-tile geometry for lava crack paths.
7. **Add terrain lighting/normals from reference**: Use Greg's brightness calculation (dx/dy height deltas, 262144 constant, directional light at -200,-220,-200).

### Priority C: Loc/Object Scene Geometry Correctness
8. **Add shape-specific placement offsets**: Walls (shapes 0-3) → edge-aligned with rotation. Centrepieces (10-11) → centre-aligned. Ground decor (22) → centre, Y=terrain.
9. **Read sizeX/sizeY from object definitions**: Parse opcodes 14/15. Apply size for multi-tile object rendering and rotation swap.
10. **Object-terrain height alignment**: Place object model base at the terrain height of its anchor tile.

### Priority D: Player Representation
11. **Export canonical loadout model**: Decode the 12 body-part models for the demo equipment set (coif, crossbow, d'hide, etc.) from cache, composite them offline.
12. **Render player composite model**: Replace cylinder with the pre-composited mesh.
13. **Add facing direction**: Rotate player model based on movement direction / target facing.

### Priority E: Inventory/Prayer/Combat UI
14. **Prayer tab rendering**: Show active/available prayers with correct icons from cache sprites.
15. **Inventory grid**: Render 28-slot inventory with item sprites.
16. **HP/Prayer orbs**: Render current HP and prayer point values.
17. **Hitsplats**: Show damage numbers on hit.
18. **Wave counter**: Display current wave number (reference: FC overlay interface).

### Priority F: Human-Playable Input
19. **Click-to-move**: Raycast 3D terrain → tile coordinate → walk action.
20. **Click-to-attack**: Raycast to NPC model → attack action.
21. **Prayer tab clicks**: Toggle protection prayers.
22. **Inventory clicks**: Use shark / prayer potion.
23. **Right-click context menus**: Optional, for parity with reference.

---

## 6. Architecture Recommendation: How demo-env Should Mirror Reference

### Recommendation: **Hybrid — Offline Export + Runtime Sim**

demo-env should NOT mirror the reference's code structure directly. The reasons:

1. **The reference is a split client-server over RS2 protocol.** Porting the protocol is unnecessary overhead. demo-env is single-process.
2. **The reference client is decompiled Java with obfuscated names.** It's an oracle for understanding *what* happens, not *how* to structure C code.
3. **The reference server is Kotlin/Koin/coroutines.** The idioms don't translate to C.

Instead:

**Offline export pipeline** (Python scripts reading cache → binary assets):
- Terrain mesh (TERR) — already exists, needs height/colour fixes
- Object mesh (SCNE) — already exists, needs placement fixes
- NPC models (MDL2) — already exists
- Player model — needs new composite export
- UI sprites (PNG) — already exists
- Wave table — export from TOML to C-friendly format

**Runtime simulation** (C, compiled into viewer):
- `fc_core.c` — tick-based gameplay sim (movement, combat, waves, prayer, inventory)
- `viewer.c` — Raylib 3D rendering of sim state
- `fc_api.h` — action interface for both human input and RL agent

This hybrid is correct because:
- **Cache decoding is complex** and Python is the right tool (we already have it working)
- **Runtime sim must be fast** for RL training (C is the right language)
- **Rendering needs GPU** (Raylib/OpenGL in C is the right approach)
- **The export pipeline is already 80% built** — it just needs the fixes identified above

The reference root's value is as an **oracle for correctness**, not as a codebase to fork.

---

## 7. Top Reference Files/Functions

### Fight Caves startup/reset
| File | Key function | What it reveals |
|------|-------------|-----------------|
| `rsps/game/.../TzhaarFightCave.kt` | `resetDemoEpisode()`, `startWave()` | Wave init, spawn placement, 5 areas |
| `rsps/game/.../FightCaveEpisodeInitializer.kt` | `reset()` | Full starter state: skills, equipment, inventory, prayer book |
| `rsps/game/.../GameProfiles.kt` | `FightCavesDemoBootstrap.install()` | Demo login hook |

### Wave progression
| File | Key function | What it reveals |
|------|-------------|-----------------|
| `rsps/game/.../TzhaarFightCaveWaves.kt` | `npcs()`, `spawns()` | Wave→NPC mapping, 15 rotations |
| `rsps/data/minigame/.../tzhaar_fight_cave_waves.toml` | — | All 63 waves × 15 rotations |
| `rsps/data/minigame/.../tzhaar_fight_cave.npcs.toml` | — | NPC stats (HP, levels, height) |

### Terrain/region loading
| File | Key function | What it reveals |
|------|-------------|-----------------|
| `rsps/cache/.../MapTileDecoder.kt` | tile opcode parsing | Height byte = opcode 1 payload |
| `rsps/tools/.../MapDecoder.kt` | `loadTiles()` | Raw cache byte → tile properties |
| `rsps/tools/.../TileLevel.kt` | `averageHeight()`, `loadBrightness()` | 512-subunit interpolation, lighting formula |
| `rsps/tools/.../MapTileSettings.kt` | `loadUnderlays()` | 5×5 colour blending |
| `rsps/tools/.../MapConstants.kt` | tile type vertices | 15 overlay path tessellations |

### Loc/object loading
| File | Key function | What it reveals |
|------|-------------|-----------------|
| `rsps/engine/.../ObjectShape.kt` | 25 shape constants | Wall vs centrepiece vs ground-decor placement |
| `rsps/cache/.../ObjectDefinition.kt` | sizeX, sizeY fields | Multi-tile footprint |
| `rsps/cache/.../ObjectDecoderFull.kt` | opcodes 14, 15, 65-67, 70-72 | Size, model scale, model offset |
| `rsps/engine/.../GameObjectCollision.kt` | `modifyObject()` | How rotation swaps sizeX/sizeY |

### Player appearance assembly
| File | Key function | What it reveals |
|------|-------------|-----------------|
| `rsps/network/.../AppearanceEncoder.kt` | `encode()` | 12 body parts, 5 colours, equipment model encoding |
| `rsps/engine/.../BodyParts.kt` | `update()` | Equipment → model ID resolution (`item | 0x8000` vs `look + 0x100`) |
| `rsps/engine/.../EquipSlot.kt` | slot indices | Hat=0, Cape=1, ... Ammo=13 |

### UI/interface/container rendering
| File | Key function | What it reveals |
|------|-------------|-----------------|
| `rsps/engine/.../Interfaces.kt` | `open()`, `gameFrame` | Gameframe selection (fixed vs resizable) |
| `rsps/network/.../InterfaceEncoders.kt` | packet encoders | How UI state is transmitted |
| `rsps/network/.../ContainerEncoders.kt` | `sendInventoryItems()` | Inventory/equipment sync |
| `void-client/client/src/Class286_Sub5.java` | `method2159()` | Sprite archive IDs for all UI elements |

---

## 8. Next Implementation Slice Recommendation

**Slice: "Terrain Height + Object Alignment Fix"**

Scope: Fix the two most visually impactful problems with minimal code change.

Files to modify:
1. `export_fc_terrain.py` line 357-360 — change height formula from `* -1.0 / 128.0` to `* -8.0 / 128.0`
2. `export_fc_terrain.py` line 315 — change `>= 63` to `>= 64`
3. `export_fc_objects.py` — add terrain height sampling to `transform_model()` so objects sit on the ground
4. `viewer.c` — remove the `+0.3` Y offset hack for terrain

Expected visual improvement:
- Terrain will have visible height variation (currently near-flat)
- Objects (walls, rocks) will sit on the terrain surface instead of floating
- Scene will look 3D rather than a flat board

This is the highest-value slice because it fixes the "flat wrong floor" and "floating objects" problems simultaneously, requires only ~20 lines of changes across 3 files, and doesn't require any new systems.
