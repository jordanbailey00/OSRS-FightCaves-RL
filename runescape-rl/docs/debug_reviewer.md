# Viewer Debug Log — Consolidated

This file is the single debug log for all viewer parity investigation.

The **active viewer rebuild plan** is defined in `docs/pr_plan.md` under "Active Viewer Rebuild Plan (PR9a-R0 through PR9a-R7)".

The **active planning foundation** is the oracle instrumentation pass (2026-03-31), documented in this file under "Oracle Instrumentation Matrix" and "Instrumentation Findings".

---

## HISTORICAL — Prior Viewer Experiments (2026-03-30)

> **STATUS: SUPERSEDED.** Steps 1–10 below document the first-pass viewer that was built before the oracle instrumentation pass. This viewer accumulated stale asset paths, dual render modes, and unverifiable visual state. It was archived as `viewer_archived_20260331.c` and replaced by the clean shell rebuild (PR9a-R1). These steps are preserved as historical record only. They are NOT the active implementation path.

---

## Step 1: Terrain Height + Object Alignment (2026-03-30)

**Fixed**: Terrain height formula (`cache_byte / 16`), crop bounds (63→64), terrain-height sampling for objects, removed Y+0.3 offset hack. Height confirmed against void-client source.

## Step 2: Scene Construction Correctness (2026-03-30)

**Fixed**: Critical terrain parser bug — `parse_terrain_data()` was reading 2 bytes for overlay data instead of 1. Implemented RS2 sub-tile tessellation (15 path types). Added wall-shape edge anchoring (shapes 0-3,9). Found that lava crack pattern requires UV textures (not vertex coloring).

## Step 3: Object Parser Opcode Fix (2026-03-30)

**Fixed**: `parse_object_def()` was missing opcodes 21 (`contouredGround`) and 22 (`delayShading`) from its zero-byte flag list. Source of truth: `ObjectDecoder.kt` line 39. This caused 95.7% of scene objects to be silently discarded (11,598 of 12,113 placements). After fix: 205 → 4,382 instances, 53K → 240K triangles.

## Step 4: Oracle-Driven Reconciliation (2026-03-30)

### Kotlin Oracle Files Used

| File | Role |
|------|------|
| `reference/legacy-rsps/tools/.../model_decoder/model-mesh_decoder.kt` | Model decode: vertices, faces, colors, textures, UVs |
| `reference/legacy-headless-env/cache/definition/decoder/ObjectDecoderFull.kt` | Object def: model refs, recolor, retexture, brightness, contrast |
| `reference/legacy-headless-env/cache/definition/Recolourable.kt` | Recolor/retexture read: opcode 40 (originalColours/modifiedColours), opcode 41 (originalTextureColours/modifiedTextureColours) |
| `reference/legacy-headless-env/cache/config/decoder/UnderlayDecoder.kt` | Terrain underlay: rgb, texture ID, scale |
| `reference/legacy-headless-env/cache/config/decoder/OverlayDecoder.kt` | Terrain overlay: rgb, texture ID, blend |
| `reference/legacy-headless-env/cache/definition/decoder/SpriteDecoder.kt` | Sprite/texture decode from cache index 9 |
| `reference/legacy-headless-env/cache/definition/data/IndexedSprite.kt` | Sprite data: indexed-color raster + palette |

### Asset-by-Asset Comparison

#### Object 9383 (centrepiece, 2,462 placements — HIGHEST COUNT)

Model 834: 4 verts, 2 faces, 1 tex space. A simple flat quad at Y=-959, spanning 1 tile.

| Property | Kotlin Oracle | Our Exporter | Gap |
|----------|-------------|-------------|-----|
| Geometry | 4 verts, 2 faces | 4 verts, 2 faces | Match |
| Base color | HSV 6689 → RGB(99,83,55) gray-green | HSV 6689 → RGB(99,83,55) | Match |
| **Recolor** | HSV 6689→2570, RGB(99,83,55)→(42,28,24) dark brown | **NOT APPLIED** | **CRITICAL** |
| **Retexture** | TexID 70→418 | **NOT APPLIED** | Missing |
| Brightness | 15 | Ignored | Missing |
| Contrast | 50 | Ignored | Missing |

**Impact**: 2,462 floor tiles render as gray-green (99,83,55) instead of dark brown (42,28,24). This is THE primary cause of the gray arena floor in the viewer.

#### Object 31286 (cave entrance, large cave wall formation)

Model 34349: 180 verts, 341 faces, 8 tex spaces. All color-only.

| Property | Kotlin Oracle | Our Exporter | Gap |
|----------|-------------|-------------|-----|
| Geometry | 180v, 341f | 180v, 341f | Match |
| Colors | 16 unique, all very dark (0-13 RGB range) | Same | Match |
| Recolor | None | N/A | N/A |
| Brightness | 15 | Ignored | Minor (already very dark) |
| Contrast | 50 | Ignored | Minor |
| AABB | X[-768,768] Y[-959,4] Z[-512,512] | Match | Match |

**Impact**: Cave walls have correct dark colors already. Brightness/contrast would refine but not dramatically change appearance.

#### Object 9370 (bone pile ground decor, 1,634 placements)

Model 9264: 39 verts, 40 faces, 1 tex space. All color-only. Has alpha.

| Property | Kotlin Oracle | Our Exporter | Gap |
|----------|-------------|-------------|-----|
| Geometry | 39v, 40f | 39v, 40f | Match |
| Colors | 3 unique: RGB(132,66,19), (150,75,21), (171,112,24) | Same | Match |
| Recolor | None | N/A | N/A |
| Alpha | Has per-face alpha | **Ignored** | Missing |
| Brightness | 32 | Ignored | Moderate |
| Contrast | 60 | Ignored | Moderate |

**Impact**: Bone piles have correct warm brown/orange base colors. Missing alpha means some semi-transparent faces render opaque. Missing brightness (32) would darken them somewhat.

#### Object 9341 (wall segment, 811 placements)

Model 9269: 75 verts, 149 faces, 4 tex spaces. All color-only.

| Property | Kotlin Oracle | Our Exporter | Gap |
|----------|-------------|-------------|-----|
| Geometry | 75v, 149f | 75v, 149f | Match |
| Colors | 7 unique, dark browns: RGB(42,28,24) to (64,43,36) | Same | Match |
| Recolor | None | N/A | N/A |
| Brightness | 15 | Ignored | Minor |

**Impact**: Wall segments already have correct dark brown colors. Minor refinement from brightness/contrast.

#### Terrain Floor (Underlay 72 — FC rock floor)

| Property | Kotlin Oracle | Our Exporter | Gap |
|----------|-------------|-------------|-----|
| RGB | 0x030303 (near-black) | Overridden to 0x1A0D08 | Approximate |
| **Texture** | ID 521 from cache index 9 | **Not decoded** | **CRITICAL** |
| Scale | 1024 | Not used | Missing |

**Impact**: The lava crack floor pattern is a bitmap texture (ID 521). Without decoding and UV-mapping it, the floor is just a flat color. This is the second-highest visual gap after object recoloring.

### Root Cause Hierarchy

1. **Object Recoloring (opcode 40) — HIGHEST IMPACT, EASY FIX**
   - Object 9383 alone: 2,462 floor tiles wrong color (gray-green vs dark brown)
   - Fix: For each face, if `faceColor == originalColour`, replace with `modifiedColour`
   - Complexity: ~20 lines of Python in `expand_model` or `transform_model`

2. **Terrain UV Textures — HIGHEST VISUAL IMPACT, COMPLEX**
   - Floor pattern (lava cracks) is texture 521 from cache index 9
   - Requires: sprite decode, UV generation, GPU texture upload, shader

3. **Object Brightness/Contrast — MODERATE IMPACT, EASY**
   - Most FC objects have brightness=15-32, contrast=50-60
   - Applied as post-HSV color adjustment per the Kotlin `ObjectDefinitionFull`

4. **Face Alpha — LOW IMPACT**
   - Some bone pile faces have per-face alpha (semi-transparency)
   - Requires alpha blending in the scene model

### Recommendation: Salvage Current Exporter with Targeted Fixes

The Kotlin oracle comparison shows our Python exporter's **geometry decode is correct** — vertex counts, face counts, face topology, and model-space AABBs all match. The gaps are in **material handling**, not geometry.

Do NOT replace the exporter with Kotlin tooling — the geometry pipeline is correct. Only the material layer needs fixing.

---

## Step 5: Opcode 40 Recolor Implementation (2026-03-30)

### What changed

1. `export_fc_objects.py`: `parse_object_def()` now returns `ObjectDef` dataclass containing models, recolors (opcode 40 pairs), retextures (opcode 41 pairs), brightness, and contrast.
2. `export_fc_objects.py`: Added `apply_recolors()` — for each face, if `faceColor == originalColour`, replace with `modifiedColour`. Applied per-object before `expand_model()`.
3. `export_fc_objects.py`: Models are now cached per (object_id, model_id) pair, so different objects sharing the same base model get their own recolor applied.

### What was NOT applied (and why)

**Brightness/contrast (opcodes 29/39) — DEFERRED.** Initial implementation applied brightness as a raw HSV value offset. This produced washed-out colors because brightness/contrast are **lighting parameters** in the RS2 engine that interact with directional lighting. Without that lighting engine, applying them as direct color adjustments is incorrect. They are stored in `ObjectDef` but not applied to vertex colors.

### Recolor verification

Object 9383 recolor confirmed working:
- Base model 834 face color: HSV 6689 → RGB(99,83,55) gray-green
- After recolor (HSV 6689→2570): RGB(42,28,24) very dark brown
- 34 objects have explicit recolors, 1,357 placed instances affected

### Critical correction: Object 9383 is NOT a floor tile

**Model 834 is a cave CEILING element**, not a floor tile. All 4 vertices sit at model Y=-959, which after flip+scale = **Y=7.49 tiles ABOVE terrain**. It's a flat quad 4×4 tiles spanning the cave ceiling.

This means the recolor is correct (ceiling goes from gray-green to dark brown) but its visual impact is negligible — the ceiling is above the camera viewport at most viewing angles.

### What actually dominates the scene visually

| Layer | Faces | Y position | Colors | Status |
|-------|-------|-----------|--------|--------|
| Terrain mesh | ~16K | Y≈0 (floor) | Dark brown (0x1A0D08 override) | Needs texture 521 |
| Bone piles (9370-9373) | **~237K** | Y≈0.1-0.2 | RGB(132-171, 66-112, 19-24) warm brown | No recolor needed (base colors correct) |
| Cave walls (9341-9350) | ~15K | Y=0-7.5 | Very dark (0-64 RGB range) | Correct |
| Ceiling tiles (9383) | ~1.2K | **Y≈7.5** (above viewport) | Recolored to dark brown | Fixed but invisible |

The bone piles (237K faces) carpet the floor with warm brown tones that photograph as gray at distance. In the reference, these are subtle decorations on a BLACK FLOOR with BRIGHT ORANGE LAVA CRACKS. The terrain texture (521) is what makes the floor look like Fight Caves.

### Screenshot assessment

Same-angle screenshot after recolor: no visible improvement. The scene is still dominated by the gray-brown bone pile carpet. The recolored ceiling tiles are above the viewport. The terrain underneath is hidden.

### Clear next blocker

**Terrain texture 521 (lava crack floor) is THE remaining visual blocker.** The recolor fix is correct and applied, but the dominant visual gap is the terrain material, not object colors. The bone pile base colors are warm brown (not gray per se), but without the contrasting black+orange lava floor texture underneath, they blend into a uniform gray-brown mass.

---

## Step 6: Scene Decomposition and Layer Isolation (2026-03-30)

### Decomposition method

Added layer toggle (L key) and auto-screenshot capture to the viewer. 4 scenes exported:
1. `debug_walls_only.scene` — shapes 0-4,9 only (547 instances, 56K tris)
2. `debug_ground_decor_only.scene` — shape 22 only (2,854 instances, 132K tris)
3. `debug_no_bonepiles.scene` — full scene minus objects 9370-9373 (2,046 instances, 138K tris)
4. `debug_bonepiles_only.scene` — objects 9370-9373 only (2,336 instances, 101K tris)

### Decomposition screenshots (saved as fc_layer_0_full.png through fc_layer_3_terrain.png)

**Layer 3 — Terrain only (no objects):**
Dark brown floor with tessellated orange overlay patches at the cave rim. The arena reads as a large, open dark cave floor. Player cylinder small at center. This layer ALONE looks structurally similar to the reference's floor composition — dark floor, player at center, elevated rim at edges.

**Layer 1 — Terrain + walls only:**
Cave wall formations (dark angular shapes) frame the arena boundary. Golden/orange streaks from overlay tessellation. The cave structure is visible and the arena reads as enclosed space with proper boundary geometry.

**Layer 2 — Terrain + everything except bone piles:**
Walls + cave debris decorations (31177-31195) + centrepieces + formations. Additional small golden/white decor items visible at the rim. Floor remains mostly dark. The scene reads as a decorated cave with a clear, open floor.

**Layer 0 — Full scene (with bone piles):**
Bone piles carpet the ENTIRE floor with a dense warm orange-brown mass (237K faces). The dark terrain underneath is completely hidden. The scene reads as cluttered and dense — this is the "squished" effect.

### Key finding: bone pile opacity is the primary composition problem

**Bone pile face alpha audit:**
- Object 9370 (model 9264): 32 of 40 faces have alpha (RS alpha 110→Raylib 145, or 170→85)
- Object 9371 (model 9265): 40 of 52 faces have alpha
- Object 9372 (model 9266): 32 of 42 faces have alpha
- Object 9373 (model 9267): 32 of 40 faces have alpha

**75-80% of bone pile faces are supposed to be SEMI-TRANSPARENT** (RS alpha 110 = 57% opacity, RS alpha 170 = 33% opacity). We implemented face alpha reading in `mesh_decoder.py` (this slice), but Raylib's default model shader does **NOT perform alpha blending** — all faces render fully opaque regardless of vertex alpha values.

In the RS2 reference, these bone piles appear as subtle, see-through debris scattered across the lava floor. In our viewer, they're an opaque orange-brown carpet that hides everything underneath.

### Face alpha implementation status

**Done:**
- `mesh_decoder.py`: `ModelMesh.face_alpha` field added
- `decode_model()`: reads `face_alpha_ptr` data when `alpha_flag == 1`
- `expand_model()`: converts RS alpha (0=opaque, 255=transparent) to Raylib alpha (255=opaque, 0=transparent) and writes per-vertex alpha

**NOT done:**
- Raylib model rendering does not enable alpha blending by default. Need to call `rlEnableBlend()` or use a custom shader with alpha test/blend before `DrawModel` for scene objects. This is a C-side viewer change, not a Python exporter change.

### Camera/scale pixel comparison

| Metric | C Viewer | Reference (headed) | Ratio |
|--------|---------|--------------------|----|
| Image size | 1244x962 | 1438x1027 | 0.87x / 0.94x |
| 3D viewport width | 1024px | ~1050px | 0.98x |
| Player size | 18x34 px | ~40x60 px | 0.45x / 0.57x |
| Player center X (%) | 62% | ~50% | Offset right |
| Player center Y (%) | 50% | ~55% | Close |

The player appears about **half the pixel size** in our viewer vs the reference. This is a camera distance/FOV difference, not a world scale issue. Our camera is further away (cam_dist=40 vs RS2's closer view) and/or wider FOV (45° vs RS2's ~30°).

### Determination: root causes are a COMBINATION

1. **Bone pile opacity (CRITICAL, CO-EQUAL with terrain texture):** 237K faces rendering opaque when 75-80% should be semi-transparent. This creates the dense carpet effect that hides the terrain and makes the arena feel cramped. Fix: enable alpha blending in Raylib scene model rendering.

2. **Terrain texture 521 (CRITICAL, CO-EQUAL):** The terrain-only view already reads as a dark cave floor, but the lava crack texture would make it match the reference. With alpha-blended bone piles, the texture would show through the semi-transparent debris.

3. **Camera distance/FOV (SECONDARY):** Player appears ~0.5x pixel size vs reference. Adjusting cam_dist from 40→20 and FOV from 45°→30° would produce closer framing. But this is a user-adjustable parameter, not a hard bug.

---

## Step 7: Alpha Blending + Oracle Camera Calibration (2026-03-30)

### Alpha blending implementation

**Problem**: Raylib's `DrawMesh` path does NOT call `glEnable(GL_BLEND)`. Vertex color alpha values (exported correctly by `mesh_decoder.py`) have no effect — all faces render fully opaque.

**Fix** (viewer.c `draw_scene()`): Two-pass rendering for scene mode 0 (full scene):

```c
/* Pass 1: opaque scene (walls, rocks — no bone piles) */
if (v->scene_no_bones.loaded)
    DrawModel(v->scene_no_bones.model, (Vector3){0,0,0}, 1.0f, WHITE);

/* Pass 2: semi-transparent bone piles */
if (v->scene_bonepiles.loaded) {
    rlDisableDepthMask();   /* don't write depth — transparent pass */
    rlEnableColorBlend();   /* glEnable(GL_BLEND) */
    DrawModel(v->scene_bonepiles.model, (Vector3){0,0,0}, 1.0f, WHITE);
    rlDisableColorBlend();
    rlEnableDepthMask();
}
```

Why two passes: opaque geometry (walls, rocks) must write to the depth buffer first. Bone piles render without depth writes so overlapping semi-transparent faces all contribute rather than occluding each other.

Scenes loaded: `debug_no_bonepiles.scene` (opaque, 138K tris) + `debug_bonepiles_only.scene` (alpha, 101K tris).

Screenshot path also fixed: `ExportImage(LoadImageFromScreen(), path)` instead of `TakeScreenshot()` which ignores directory components.

### Camera calibration

Default camera adjusted to match oracle (headed RS2 RSPS) framing:

| Parameter     | Before | After  | Oracle (est.) |
|---------------|--------|--------|---------------|
| cam_dist      | 40     | 28     | ~25-30        |
| fovy          | 45°    | 32°    | ~30°          |
| cam_pitch     | 0.8 (46°) | ~~1.1 (63°)~~ → 0.6 (35°) | ~35° (oracle) |
| target Y      | 0.0 (ground) | 0.5 (body center) | body center |

### Before/after measurements

| Metric                | Before (Step 6) | After (Step 7)  | Oracle         |
|-----------------------|-----------------|-----------------|----------------|
| Player size (px)      | 18×34           | 38×66           | ~40×60         |
| Player center X%      | 62%             | 64%             | ~50%           |
| Player center Y%      | 50%             | 51%             | ~55%           |
| Alpha blending        | OFF             | ON              | ON             |
| Terrain texture 521   | missing         | missing         | present        |

Player center X% remains offset (64% vs 50%) because the 220px UI panel on the right shifts the 3D viewport. The player IS centered within the 3D viewport (~50% of viewport width). Oracle likely includes no side panel or has a different layout.

### Alpha verification

Same-camera comparison (blend ON vs blend OFF, same cam_dist=28/fovy=32/pitch=1.1):

- 51.9% of center-floor pixels changed color when blend was enabled
- 13.2% got darker (terrain showing through semi-transparent bones)
- Darkest pixel in bone pile area: RGB(26,13,8) luminance=47 (terrain color)
- Average RGB shift: +28R, +22G, +4B (lighter overall because depth mask off allows overlapping faces to all render, adding brightness through accumulation)

### Proportional comparison

At the new camera: 1 tile ≈ 63px at viewport center (derived from player cylinder: 0.6 tiles wide = 38px → 63 px/tile).

| Ratio                           | Value  | Assessment        |
|---------------------------------|--------|-------------------|
| Player height (1.2 tiles)       | 66px   | Correct scale     |
| Player width (0.6 tiles)        | 38px   | Correct scale     |
| Bone pile element height (~0.2t)| ~11px  | Correct (floor-level debris) |
| Player / bone pile height       | 5.9×   | Plausible — player should tower over floor debris |
| Player / cave wall height       | 0.16×  | Consistent — walls are ~7.5 tiles tall |
| Floor crack width               | N/A    | Requires texture 521 |

No residual scale/parity mismatch detected. Player proportions match oracle after camera calibration.

### Visual assessment

**Before** (Step 6): Opaque bone pile carpet hides terrain completely. Wide FOV makes player appear tiny. Cave walls visible at edges.

**After** (Step 7): Alpha blending reveals dark terrain through semi-transparent bone pile faces. Steeper/closer camera matches oracle framing. Player appears at correct relative size.

**Oracle** (jad-kill.jpg): BLACK floor with bright red/orange LAVA CRACK web pattern. Bone pile debris visible but not dominant — floor pattern is the primary visual element. Cave walls at edges.

### Determination

**Ready for texture 521 next.** No residual scale/parity mismatch remains. The scene composition is now structurally correct (player at right scale, bone piles semi-transparent, correct camera angle). The dominant remaining gap is the terrain material: the oracle's floor is a dramatic black+red lava crack pattern (texture 521 from cache index 9), whereas our floor is flat dark brown vertex color.

The bone piles still appear dense because 2,336 instances with 101K semi-transparent faces is a LOT of geometry. With the oracle's black+red lava floor underneath, the bone pile debris would read as scattered decoration over a contrasting floor rather than a uniform orange-brown carpet. The alpha blending is necessary but not sufficient — terrain texture 521 is the remaining critical gap.

---

## Headed Oracle Bring-Up (2026-03-30)

### Environment adjustments made

The reference tree at `/home/joe/projects/runescape-rl-reference/` was copied from a WSL/Windows workspace. Two broken symlinks (became zero-byte files during copy) were restored:

1. **Cache symlink**: `rsps/data/cache` → `training-env/sim/data/cache` (193MB RS cache)
2. **JDK symlink**: `runescape-rl-runtime/tool-state/workspace-tools/jdk-21` → `jdk-21.0.10+7`

One missing directory created: `rsps/data/.temp/` (needed by `ConfigFiles.update()` at server boot).

Fight Caves demo data directories created under `rsps/data/fight_caves_demo/` (saves, logs, errors, artifacts, backend_control).

### Client build adaptation

The original client required Windows JDK 8 for build. Adapted for Linux JDK 21:

1. Compiled all 758 Java source files with `javac --release 8` (JDK 21 cross-compiling to Java 8 target)
2. Removed `sun.net.www.protocol.http.AuthenticationInfo` import from `Class272_Sub2.java` (internal JDK API removed post-JDK 8, used only for HTTP proxy auth — dead code path for local connections)
3. Built convenience jar: `.artifacts/void-client/void-client-fight-caves-demo.jar`

Jagex cache files copied to `~/.jagex_cache_32/runescape/` (Linux equivalent of `C:\.jagex_cache_32\runescape\`).

### Verified working — oracle screenshot captured

Server ticked through 90+ game ticks, client connected in 3.2 seconds, reached `client_state=11` (in-game), `FIGHT_CAVES_DEMO_READY` confirmed. Screenshot saved to `/home/joe/Pictures/Screenshots/fc_oracle_client.png`.

### Reproducible commands

**Terminal 1 — Start the server:**

```bash
source /home/joe/projects/runescape-rl-reference/runescape-rl/scripts/workspace-env.sh
cd /home/joe/projects/runescape-rl-reference/runescape-rl/rsps
./gradlew --no-daemon :game:runFightCavesDemo
```

Wait for `Listening for requests on port 43594...` in the output.

**Terminal 2 — Launch the client:**

```bash
java -jar /home/joe/projects/runescape-rl-reference/runescape-rl/.artifacts/void-client/void-client-fight-caves-demo.jar \
  --address 127.0.0.1 \
  --port 43594 \
  --username fcdemo01 \
  --password pass123 \
  --show-during-bootstrap
```

Wait for `FIGHT_CAVES_DEMO_READY` in stdout.

**Shutdown:**

Kill the client process (Ctrl+C or close window), then kill the server (Ctrl+C in terminal 1).

---

## Oracle Instrumentation Pass (2026-03-30)

Ran the headed Kotlin/Java client as a live measurement oracle. Instrumented `FightCavesDemoAutoLogin.java` to dump camera state at runtime. Traced rendering pipeline through decompiled client source (`void-client/client/src/`) cross-referenced with server-side Kotlin code (proper variable names).

### 1. Camera Oracle

**Source files used:**
- `OutputStream_Sub1.java:46` — camera position computation callsite
- `Class17.java:221` (method268) — camera position from pitch/yaw/zoom
- `Class70.java` — sine/cosine table (16384 entries = full circle, scale 16384)

**RS-HD camera model:**
- Sine table: 16384 entries, `sin[angle] = 16384 * Math.sin(angle * 2π/16384)`
- Full circle = 16384 angle units (not 2048 as in RS2 classic)
- Camera pitch range: 1024–3072 (22.5°–67.5° from horizontal)
- Default pitch: ~1600 (~35° from horizontal, ~55° from vertical)

**Camera distance formula** (from `OutputStream_Sub1.java:46` → `Class17.method268`):
```
zoomDerived = ((zoom >> 3) * 3 + 600) << 2
```
At default zoom=1024: `zoomDerived = ((128)*3 + 600)*4 = (384+600)*4 = 3936 RS units = 30.75 tiles`

**Camera position** (from `Class17.method268`):
```
pitchAngle = (16384 - zoom) & 0x3fff  // NOT the pitch itself
yawAngle = (16384 - yaw) & 0x3fff
cameraZ = pitch - (cos(pitchAngle) * zoomDerived >> 14)
cameraY = heightOffset + (sin(pitchAngle) * zoomDerived >> 14)
cameraX = playerZ - (sin(yawAngle) * cos(pitchAngle) * zoomDerived >> 14)
```

**Live instrumented values (from CAMERA_ORACLE dump):**
- zoom=1024 (default RS-HD zoom, confirmed live)
- playerX=4352, playerY=4352 (= 8.5 tiles from region origin, = FC center area)
- Camera position variables read as 0 from autologin thread (set per-frame on render thread, not persistent)

**Mapping to C/Raylib viewer:**
- RS distance 3936 units / 128 units-per-tile ≈ **30.75 tiles** — our cam_dist=28 is close
- RS pitch ~1600/16384 * 360° ≈ **35° from horizontal** → cam_pitch ≈ 0.61 rad — our 1.1 rad (63°) is too steep
- RS FOV: the client uses a fixed-function projection with no explicit FOV field; the "zoom" is the distance, not FOV. The effective FOV is determined by viewport size and the 3D projection constant. From the oracle screenshot, the visible extent suggests ~35-40° effective VFOV.

**Recommended C/Raylib corrections:**
- cam_pitch: **1.1 → 0.6** (35° above horizontal, matching RS default)
- cam_dist: 28 is reasonable (oracle is ~31 tiles)
- fovy: 32° is reasonable (oracle ~35-40° effective)
- target Y: 0.5 is acceptable

### 2. Terrain Texture/Material Oracle

**Source files used:**
- `OverlayDecoder.kt` — overlay definition parser (opcode 2/3=texture, 1=colour, 9=scale)
- `UnderlayDecoder.kt` — underlay definition parser
- `MaterialDecoder.kt` — material/texture definition bulk loader (cache index 26)
- `MaterialDefinition.kt` — material properties (colour, useTextureColour, type)
- `MapTileSettings.kt` — terrain tile vertex setup with overlay/underlay blending
- `TileLevel.kt` — terrain lighting, brightness, and textured/coloured tile rendering

**Fight Caves terrain definitions (decoded from cache):**

| Definition | Colour | Texture ID | Scale | Notes |
|-----------|--------|-----------|-------|-------|
| **Underlay 72** | 0x557799 | **1** | 512 | Main FC floor (NOT texture 521) |
| **Underlay 118** | 0x38422F | **96** | 512 | Secondary floor |
| **Overlay 42** | 0xFF00FF (magenta=invisible) | -1 | 512 | No texture, hide_underlay=true |
| **Overlay 111** | 0x60769A | **669** | 1024 | Textured overlay |
| **Overlay 143** | 0xB79767 | **512** | 1024 | Textured, hide_underlay=false |
| **Overlay 157** | 0x4182AF | -1 | 512 | Colour only |

**~~Stale correction (superseded):~~** ~~An earlier oracle pass incorrectly claimed FC used textures 1/669/512 due to a decoder bug.~~ Re-verified: FC underlay 72 DOES use texture 521, overlays 111/157 use texture 61. See Step 8 for corrected values.

**How terrain textures are applied (from TileLevel.kt:drawTiles):**
1. If the tile has a `textureId != -1` and `useTextureColour == false`:
   - The texture's `colour` field from `MaterialDefinition` provides the base colour
   - Per-vertex brightness from directional lighting is applied via `light(brightness, textureColour)`
   - The texture bitmap is NOT UV-mapped onto terrain in the 2D map renderer
2. In the 3D client (ha_Sub2.java), textured terrain tiles use actual UV-mapped textures via OpenGL texture units

**Lighting model (from TileLevel.kt:loadBrightness):**
- Light direction: `(-200, -220, -200)` (sun from upper-right)
- Brightness constant: `BRIGHTNESS = 75518` (75518 >> 9 = 147 base intensity)
- Per-tile intensity from height gradient: `dx = height[x+1] - height[x-1]`, `dy = height[y+1] - height[y-1]`
- Shadow map: `tileShadows[x][y]` subtracted from brightness
- Final vertex brightness clamped to [2, 126]

### 3. Bone-Pile Alpha / Decor Rendering Oracle

**Source files used:**
- `Class64_Sub2.java` — Model class, `aByteArray5499` = face alpha array
- `ha_Sub2.java:1230-1240` — OpenGL blend state switching
- `Class377.java:963` — glEnable(GL_BLEND) call

**Alpha rendering pipeline:**
1. Face alpha values stored in `aByteArray5499[faceIndex]` (0 = opaque, non-zero = has alpha)
2. During face sorting/merging (lines 2420-2490), faces with `alpha != 0` are identified and separated
3. Blend mode selection (ha_Sub2.java:1230):
   - Mode 0: `glDisable(GL_BLEND)` — opaque faces
   - Mode 1: `glEnable(GL_BLEND) + glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)` — standard alpha blend
   - Mode 2: additive blend (`GL_ONE, GL_ONE`)
   - Mode 3: destination blend (`GL_DST_COLOR, GL_ONE`)
4. Alpha TEST also used: `glEnable(GL_ALPHA_TEST)` (3008) — hard cutoff for alpha < threshold

**Bone pile rendering order:**
- Opaque faces drawn FIRST with depth writes enabled
- Semi-transparent faces drawn SECOND with GL_BLEND enabled
- The face merge system (lines 2432-2490) accumulates alpha from overlapping models: `is_498_[i_525_] += i_523_` — additive alpha merge for overlapping transparent geometry at the same tile position

**Confirmation:** Our two-pass approach (opaque pass → blend pass with depth mask disabled) is the correct strategy. The RS client does the same: opaque first, then blended.

### 4. Scene-Layering Oracle

**Source files used:**
- `OutputStream_Sub1.java:102-104` — scene building call with region loading
- `Class289.java:27-28` — region tile position computation
- `Class130_Sub1.java` — scene graph viewport clipping

**Scene building:**
- Scene is built from map data (index 5) exactly as we do: terrain tiles + loc placements per region
- No additional culling/filtering beyond standard RS2 viewport frustum culling
- All level-0 objects are placed; no objects are skipped based on type or density
- The 2,336 bone pile instances we place ARE correct — the headed client places the same count

**Why the headed client doesn't look carpeted:**
1. **Alpha blending**: bone pile faces at 33-57% opacity let the floor show through
2. **Terrain texture**: the contrasting black+coloured lava crack pattern makes the debris read as scattered decoration, not a uniform carpet
3. **Lighting**: directional lighting (from -200,-220,-200) adds depth variation to both terrain and bone piles, breaking visual uniformity
4. **No culling difference**: the same number of objects are rendered; the visual difference is entirely material/blend, not object count

### Oracle Verdict

**Current diagnosis confirmed with one correction:**

1. **Alpha blending** — confirmed necessary, our two-pass approach is correct
2. **Camera pitch** — **CORRECTED**: pitch 1.1 rad (63°) was too steep. Oracle default ≈ 0.6 rad (35° from horizontal). Fixed in Step 8.
3. **Terrain textures** — Verified from actual cache: underlay 72 uses texture 521, overlays 111/157 use texture 61. All materials have `useTextureColour=True`. Definition colours are: underlay 72 = 0x030303 (near-black), overlay 111 = 0xFBA13A (bright orange), overlay 157 = 0xFAB737 (golden). The lava crack web pattern visible in the oracle is the texture 521 BITMAP tiled across the floor — not just the definition colour.
4. **Scene layering** — no culling difference; same object count. Visual difference is entirely material/blend.

---

## Step 8: Camera Pitch Fix + Terrain Material Colours + Lighting (2026-03-30)

### Changes made

**Camera** (viewer.c):
- cam_pitch: 1.1 → **0.6** (oracle-calibrated 35° from horizontal)
- cam_dist, fovy, target Y unchanged (28, 32°, 0.5 — already close to oracle)
- Added `--mode N` CLI argument for starting in specific scene modes

**Terrain exporter** (export_fc_terrain.py):
- Added `decode_materials()` — decodes MaterialDefinition bulk data from cache index 26
- Replaced hardcoded colour approximations with definition colours from cache
- Added `_calc_brightness()` — oracle directional lighting model from TileLevel.kt: light(-200,-220,-200), base brightness 75518>>9=147, height-gradient-based per-tile intensity
- Added `_light_rgb()` — applies brightness scaling to RGB colours

**Oracle-verified terrain definitions (actual cache):**

| Definition | Colour | Texture | Material useTexColour |
|-----------|--------|---------|----------------------|
| Underlay 72 | 0x030303 (near-black) | 521 | True |
| Underlay 118 | 0x752222 (dark red) | 181 | True |
| Overlay 111 | 0xFBA13A (bright orange) | 61 | True |
| Overlay 157 | 0xFAB737 (golden) | 61 | True |
| Overlay 42 | 0xFF00FF (invisible) | -1 | — |
| Overlay 143 | 0x010101 (near-black) | -1 | — |

### Runtime verification (auto-screenshot provenance pass)

Added debug overlay (enabled by default) showing: camera values, scene mode, all loaded asset vertex counts, alpha rendering status, and terrain vertex colour samples. Added auto-screenshot at frames 60/90/120 cycling through modes 0/3/2 via ExportImage (framebuffer capture, not affected by window overlap).

**Screenshots (from single binary, single run):**
- `fc_auto_mode3_terrain.png`: **NEAR-BLACK FLOOR CONFIRMED.** Terrain-only shows dark floor (RGB 3,3,3) with red cave walls. The terrain asset IS correct.
- `fc_auto_mode2_nobones.png`: Opaque walls/rocks over dark floor. No orange carpet — confirms bone piles are the sole orange source.
- `fc_auto_mode0_full.png`: Full scene with alpha — still appears overwhelmingly orange because 304,320 bone pile vertices at 33-57% opacity per face stack to near-opaque when overlapping across 2,336 instances.

**Debug overlay confirms:**
- `CAM pitch=0.60 dist=28.0 fovy=32`
- `Terrain:OK(24558v)`, `NoBones:OK(414873v)`, `Bones:OK(304320v)`
- `Alpha: TWO-PASS ACTIVE`
- `Terrain[0]:RGB(8,8,8) [mid]:RGB(3,3,3)` — terrain vertices are near-black

### Honest assessment

The terrain IS near-black. The camera IS at 0.6 rad. Alpha blending IS active. But the **visual result in mode 0 still looks like an orange carpet** because:

1. **Bone pile density is extreme**: 2,336 instances × ~43 faces each × 33-57% opacity per face. At the center of the arena, 3-5 bone pile models overlap per tile, and their combined opacity approaches 90-100% — the terrain is mathematically invisible even with blending.
2. **The oracle's floor contrast comes from the lava crack TEXTURE**, not from the terrain definition colour. In the oracle, the floor is a dramatic black-with-bright-red-lines pattern that visually competes with the semi-transparent bone debris. Our flat near-black floor provides no visual contrast — it just darkens the bone piles slightly.

### Determination

**Next blocker is still texture 521 bitmap decode + UV terrain pipeline.** The terrain colours and alpha are technically correct but the visual parity gap remains because:
- Oracle: bright red/orange lava cracks on black floor SHOW THROUGH semi-transparent bone debris → the lava pattern is the dominant visual element
- Ours: flat near-black floor shows through as slight darkening → bone debris remains the dominant visual element

The lava crack texture pattern is what breaks the "orange carpet" appearance in the oracle. Without it, alpha blending alone cannot create the visual contrast needed.

### Step 9: Oracle-Baked Terrain Texture Pipeline (2026-03-30)

**Approach change**: Instead of reverse-engineering the cache index 9 texture format, wrote a Kotlin exporter (`FightCavesTerrainExporter.kt`) in the Void RSPS `tools` module that uses the same cache library, overlay/underlay/material decoders, and rendering pipeline as the headed client.

**What was built:**
- `rsps/tools/.../FightCavesTerrainExporter.kt` — standalone Gradle task that renders FC terrain via `RegionManager` + `MapTileSettings` + `TileLevel`
- Gradle task `exportFcTerrain` in `tools/build.gradle.kts`
- Outputs: `fc_terrain_baked.png` (768x512, 4px/tile) and `fc_terrain_baked_hires.png` (1536x1024, 8px/tile)

**C viewer changes:**
- `fc_terrain_loader.h`: Added UV coordinate generation + `fc_terrain_apply_texture()` that loads the baked PNG
- `viewer.c`: Loads `fc_terrain_baked.png` after terrain mesh, applies as diffuse texture
- Debug overlay now shows `Terrain: TEXTURED (baked 768x512)`

**Result:**
The baked terrain texture IS applied and renders correctly (confirmed via auto-screenshot, debug overlay). However, the visual output uses the same **flat material colours** as the 2D map renderer — dark floor with orange edges. The lava crack web pattern visible in the oracle requires the actual **texture bitmap** from cache index 9, which the 2D renderer does not use.

**Cache index 9 texture format investigation:**
- Index 9 entries are NOT sprites (they use a different packed format from index 8)
- Each entry contains a count byte followed by references, but these are NOT simple sprite IDs
- The first "sprite" referenced by texture 521 (sprite ID 8) is actually a crossbow icon, not lava
- The RS-HD texture compositing format is undocumented in the Void codebase

### Step 10: Oracle Screenshot as Baked Terrain Texture (2026-03-30)

**Approach**: Rather than reverse-engineering the undocumented cache index 9 texture format, used the working oracle client directly:

1. Modified client zoom limits: min=256, max=16384 (was 1024-3072), default=6000, 6x scroll speed, disabled server camera reset
2. User zoomed out in the oracle client and captured the lava crack floor pattern
3. Cropped and resized the screenshot to 512x512 PNG
4. Applied as tiled terrain texture in the C viewer (UV = tile position / 8, wrap mode = REPEAT)
5. Set terrain vertex colours to white (255,255,255) so texture renders at full brightness instead of being multiplied by near-black vertex colours

**Files changed:**
- `fc_terrain_loader.h`: UV tiling at 8 tiles per repeat, SetTextureWrap(REPEAT), vertex colour override to white
- `fc_terrain_baked.png`: 512x512 lava crack pattern from oracle screenshot
- Client `Class239_Sub2.java`, `Class76.java`, `Class318_Sub1_Sub1.java`, `Canvas_Sub1.java`: zoom uncap for screenshot capture

**Result (verified via auto-screenshot):**
- Terrain-only: **Black floor with orange lava crack web pattern** — matches oracle
- No-bonepiles: Lava floor with cave walls — structurally matches oracle composition
- Full scene: Bone piles over lava floor — the lava cracks are visible through the semi-transparent gaps between bone fragments

**Next blocker:** Player composite model (replacing blue cylinder with actual player model). The terrain is now visually correct.

---

## END OF HISTORICAL SECTION

> Everything above this line documents the superseded first-pass viewer (Steps 1–10, 2026-03-30). Everything below documents the current rebuild path.

---

## Viewer Reset (2026-03-31) — PR9a-R1

The debug viewer code was reset and rebuilt from scratch. The previous implementation (981 lines) had accumulated stale asset paths, contradictory render modes, and unverifiable visual state. Archived as `viewer_archived_20260331.c`.

### Slice 1: Clean Shell

New viewer (265 lines) — single render path, no asset loading, no terrain/scene/model dependencies.

**What's included:**
- Game loop: fc_init → fc_reset → fc_step with random demo actions
- Pause/resume (Space), single-step (Right), speed control (Up/Down), reset (R)
- Camera: oracle-calibrated defaults (pitch=0.6, dist=28, fovy=32), right-drag orbit, scroll zoom
- Header: wave/episode/HP/prayer display
- Panel: HP/Pray bars, wave info, inventory summary, prayer status, terminal state
- Debug overlay (D key): camera values, hash, entity count, render status
- 3D viewport: grid placeholder, player cylinder, NPC cubes, HP bars
- Controls help bar at bottom

**What's intentionally NOT included:**
- No terrain mesh or texture
- No scene objects (walls, rocks, bone piles)
- No NPC 3D models
- No sprite loading
- No screenshot system
- No decomposition modes (L key)

**Screenshot:** `fc_viewer_shell_v1.png` — clean dark grid with blue player cylinder, readable UI, no visual corruption.

---

## Oracle Instrumentation Matrix (2026-03-31) — PR9a-R0

Disciplined phase-by-phase instrumentation plan for the headed Kotlin/Java Fight Caves oracle at `/home/joe/projects/runescape-rl-reference/runescape-rl/rsps/`. This matrix must be completed before any viewer rebuild work begins.

Oracle source of truth:
- Server: Kotlin RSPS at `rsps/` (engine, cache, game, types modules)
- Client: Decompiled Java void-client at `rsps/void-client/client/src/` (758 obfuscated .java files)
- Cache: OSRS binary cache at `training-env/sim/data/cache/` (193MB, linked via `rsps/data/cache`)

### Phase 1 — Bootstrap / Bring-Up

| Probe | File(s) | Question | Output |
|-------|---------|----------|--------|
| Server main entry | `game/.../FightCavesDemoMain.kt` → `Main.kt` | What is the exact boot sequence from Gradle task to tick loop? | Ordered call chain with timing |
| Profile loading | `GameProfiles.kt`, `fight-caves-demo.properties` | What demo overrides are active? Which are one-time vs recurring? | Property list with lifecycle |
| Cache load | `Cache.kt`, `CacheLoader.kt`, `FileCache.kt` | How is the cache opened, what indices are loaded, in what order? | Index list + load order |
| Preload chain | `Main.kt` → `preload()`, `GameModules.kt` | What definitions/configs are loaded before the world starts? | Definition type list |
| Demo bootstrap hook | `GameProfiles.kt` → `FightCavesDemoBootstrap.install()` | What exactly does the demo profile inject into the login path? | Hook description |
| Client boot | `Loader.java` → `FightCavesDemoAutoLogin.java` | Client-side: what state machine gets the player from jar launch to in-game? | State sequence |
| Instance creation | `FightCaveEpisodeInitializer.kt` → `resetFightCaveInstance()` | What code path creates the FC instance on first login? | Call chain |

### Phase 2 — Cache / Resource Resolution

| Probe | File(s) | Question | Output |
|-------|---------|----------|--------|
| Cache index inventory | `Cache.kt`, `Index.kt` | Which cache indices exist and what do they contain? | Index → content type map |
| FC terrain resources | `MapTileDecoder.kt`, `MapDefinition.kt` | Which cache archive(s) hold the FC region terrain tiles? Archive naming? | Archive name, group, file IDs |
| FC object resources | `MapObjectDecoder.kt`, `MapObject.kt` | Which cache archive(s) hold the FC region objects? | Archive name, object IDs |
| Underlay/overlay defs | `UnderlayDecoder.kt`, `OverlayDecoder.kt` | Which underlay/overlay IDs are used in FC region? What are their RGB/texture/scale? | ID → property table |
| Material/texture defs | `MaterialDecoder.kt`, `MaterialDefinition.kt` | Which material IDs are referenced by FC underlays/overlays? | Material ID → property table |
| Object defs | `ObjectDecoderFull.kt` | Which object definition IDs are placed in FC? Models, recolors, sizes? | Object ID → model/property table |
| NPC defs | `NPCDecoderFull.kt`, `tzhaar_fight_cave.npcs.toml` | Which NPC IDs appear in FC? Model IDs, sizes, anim IDs? | NPC ID → model/anim table |
| Sprite/texture assets | `SpriteDecoder.kt` | Which sprite IDs are used for FC terrain textures (e.g., texture 521)? | Sprite ID list |
| Player equipment models | `ItemDecoderFull.kt`, `FightCaveEpisodeInitializer.kt` | What equipment model IDs does the canonical FC loadout use? | Item → model ID map |

### Phase 3 — Region / Scene Construction

| Probe | File(s) | Question | Output |
|-------|---------|----------|--------|
| Region ID and bounds | `TzhaarFightCave.kt`, `Region.kt` | Region 9551 = (37,79). Tile bounds? | Exact tile rectangle |
| Instance allocation | `Instances.kt` | How is a small instance (128x128) allocated? What's the offset? | Allocation algorithm |
| Zone copy mechanism | `DynamicZones.kt` | How are source zones copied into the instance? | Zone iteration order |
| Tile decode loop | `MapTileDecoder.kt` | How is the 64x64x4 tile array populated from cache bytes? | Decode algorithm |
| Object decode loop | `MapObjectDecoder.kt` | How are objects decoded and placed? Bridge filtering? | Decode algorithm |
| Object → collision | `GameObjectCollisionAdd.kt`, `ObjectShape.kt` | How do placed objects modify the collision map? Shape dispatch? | Shape → collision flag map |
| Collision decode | `CollisionDecoder.kt` | How are tile-level collision flags set from settings byte? | Flag assignment logic |
| Scene population order | `MapObjectsDecoder.kt` | What is the order: tiles first, then objects? Or interleaved? | Ordered steps |

### Phase 4 — Terrain Material / Render Construction

| Probe | File(s) | Question | Output |
|-------|---------|----------|--------|
| Underlay color pipeline | `UnderlayDecoder.kt`, `UnderlayDefinition.kt` | RGB → HSL conversion for FC underlay ID 72? | Exact color values |
| Overlay color pipeline | `OverlayDecoder.kt`, `OverlayDefinition.kt` | HSL conversion for FC overlay IDs? Different algorithm? | Exact color values + algorithm diff |
| Texture resolution | `MaterialDefinition.kt` | FC underlay 72 has texture 521. What does material 521 look like? | Material properties |
| Client tile mesh build | `Class237.java`, `Class237_Sub1.java` | How does the client build per-tile geometry from underlay/overlay data? | Mesh construction algorithm |
| Height map population | `Class237.java` (`method1678/1679`) | How are tile heights populated? What units? | Height algorithm + units |
| Sub-tile tessellation | Client `method1685/1697` | How are overlay paths (15 types) tessellated into sub-tile triangles? | Path → triangle map |
| Lighting/brightness | Client shading code | What per-tile or per-vertex lighting is applied? | Lighting formula |
| Texture UV generation | Client texture code | How are terrain texture UVs computed? Per-tile or per-vertex? | UV algorithm |

### Phase 5 — Player / NPC Visual Construction

| Probe | File(s) | Question | Output |
|-------|---------|----------|--------|
| Player appearance packet | Network encoder files | How does the server send player appearance to the client? | Packet structure |
| Player model composite | Client model code | How does the client assemble equipment models into a player composite? | Assembly algorithm |
| NPC model selection | `NPCDefinitions.kt`, NPC toml | How is NPC type → model ID resolved? Multiple models per NPC? | Model ID lookup chain |
| NPC size/height | `tzhaar_fight_cave.npcs.toml` | What are the size (tiles) and height (units) for each FC NPC? | NPC → size/height table |
| Animation selection | `tzhaar_fight_cave.anims.toml` | What animation IDs are used for each NPC action (idle, attack, death)? | NPC → anim ID table |
| Model transforms | Client model rendering | How are models positioned, rotated, scaled in world space? | Transform pipeline |
| Recolor/retexture | `ObjectDecoderFull.kt`, client model code | How are opcode 40/41 recolors applied to models? | Recolor algorithm |

### Phase 6 — UI / Sprite Construction

| Probe | File(s) | Question | Output |
|-------|---------|----------|--------|
| Font rendering | Client `FontDecoder.kt`, client font code | How is text rendered? Bitmap font from cache? | Font system description |
| Prayer icons | Sprite cache entries | What sprite IDs are the prayer icons? | Sprite ID list |
| Item sprites | Sprite cache entries | What sprite IDs for sharks, prayer pots, crossbow bolts? | Sprite ID list |
| HP/prayer bars | Client UI code | How are HP/prayer bars rendered? Sprites or runtime drawing? | Rendering method |
| Combat overlay | Client hitsplat code | How are hitsplats rendered? Sprite IDs? Animation? | Hitsplat system |
| Minimap | Client minimap code | Is there a minimap? How rendered? | Minimap description |
| Chat/interface panels | Client interface code | What UI panels are visible during FC? | Panel inventory |

### Phase 7 — Frame Render Path

| Probe | File(s) | Question | Output |
|-------|---------|----------|--------|
| Camera update | `Canvas_Sub1.java` lines 23-51 | Per-frame camera position computation? | Camera update formula |
| Scene submission order | `Canvas_Sub1.java` `method123()` | What is the exact draw order? | Ordered draw call list |
| Terrain draw | `Class203.method1479()` | How are terrain tiles submitted? Per-tile or batched? | Submission method |
| Object draw | Client scene object rendering | How are static scene objects drawn? Sorted? | Draw order/sort |
| Entity draw | Client entity rendering | How are player/NPC models drawn relative to terrain? | Entity draw order |
| Transparency handling | Client blend/depth code | How are semi-transparent objects (bone piles, water) handled? | Blend state management |
| UI draw | Client 2D overlay | When is 2D UI drawn relative to 3D scene? | UI draw timing |
| State changes | Client GL state | What OpenGL state changes happen per frame? | State change list |

### Phase 8 — Runtime Update Loop

| Probe | File(s) | Question | Output |
|-------|---------|----------|--------|
| Server tick stages | `GameTick.kt` | What stages run each server tick? In what order? | Stage list |
| Client frame rate | Client timing code | Client frame rate? Decoupled from server tick? | FPS / tick rate |
| Scene static vs dynamic | All scene code | What scene data is loaded once vs recomputed per frame/tick? | Static/dynamic table |
| NPC position updates | Network NPC update | How are NPC positions sent to client each tick? | Update protocol |
| Player position updates | Network player update | How are player movements sent? | Update protocol |
| Animation updates | Network anim code | How are animation state changes sent? | Update protocol |
| Projectile/GFX updates | Network GFX code | How are projectiles/graphics created and updated? | Update protocol |

### Phase 9 — Arena Footprint / Map Trace

| Probe | File(s) | Question | Output |
|-------|---------|----------|--------|
| Region tile bounds | `TzhaarFightCave.kt`, `Region.kt` | Exact min/max tile X/Y for region 9551? | Tile rectangle |
| Active arena rectangle | `tzhaar_fight_cave.areas.toml` | What is the multi-combat area vs spawn areas vs center? | Area rectangles |
| Per-tile walkability | Collision data from cache | Which tiles are walkable, blocked, void? | Occupancy grid |
| Per-tile underlay/overlay | `MapTileDecoder.kt` output | What underlay/overlay ID is on each tile? | Tile material grid |
| Per-tile height | `MapTileDecoder.kt` output | What height value per tile? | Height grid |
| Static wall objects | `MapObjectDecoder.kt` output | What wall/boundary objects and where? | Object placement list |
| Boundary bounding box | Object positions | Aggregate bounding box of visible cave boundary? | Bounding box |
| Tile-to-world scale | `Tile.kt`, client rendering | How do tile coords map to world/render coords? | Scale formula |
| Origin/center convention | `TzhaarFightCave.kt` | Centre tile (2400,5088). What is the origin convention? | Origin spec |
| Instance vs source footprint | `Instances.kt`, `DynamicZones.kt` | Does the instance area differ from the source region area? | Comparison |

---

## Instrumentation Findings (2026-03-31)

### Phase 1 — Bootstrap / Bring-Up Findings

**Boot sequence** (exact order, all one-time unless noted):

1. `FightCavesDemoMain.main()` → `Main.start(GameProfiles.fightCavesDemo)`
2. `settings(profile)` — loads `game.properties` then `fight-caves-demo.properties`, merges env vars
3. `Cache.load(settings)` — opens `cache.dat2` + `cache.idx255`, returns Cache object
4. `GameServer.load(cache, settings)` → `server.start(43594)` — network listen begins
5. `configFiles.update()` — collects config files from disk
6. `startKoin { modules(engineModule, gameModule, cache) }` — DI init, all definition decoders created
7. `engineLoad(configFiles)` — loads engine definitions
8. `ContentLoader().load()` — loads all game content scripts
9. `profile.afterPreload()` → `FightCavesDemoBootstrap.install()`:
   - Wraps `AccountManager.loadCallback` to mark every login with `fight_cave_demo_profile=true`
   - Sets `fight_cave_demo_entry_pending=true` on login
10. `LoginServer.load(...)` — creates login server
11. `World.start(configFiles)` — initializes world entity system
12. `GameLoop(getTickStages()).start(scope)` — starts 600ms tick loop

**Tick stages** (RECURRING, 20 stages per 600ms tick):
1. PlayerResetTask, 2. NPCResetTask, 3. [skip BotManager], 4. Hunting, 5. GrandExchange, 6. ConnectionQueue, 7. NPCs, 8. FloorItems, 9. **FightCaveDemoBackendControl**, 10. InstructionTask, 11. World, 12. NPCTask, 13. PlayerTask, 14. FloorItems, 15. GameObjects.timers, 16. DynamicZones, 17. ZoneBatchUpdates, 18. CharacterUpdateTask (PlayerUpdate + NPCUpdate), 19. AccountSave, 20. SaveLogs

**Per-login (RECURRING)**: `markDemoLogin()` → sets flags → `FightCaveDemoObservability.beginSession()`

**Per-episode (RECURRING)**: `FightCaveEpisodeInitializer.reset()`:
1. `setRandom(Random(seed))` — deterministic RNG
2. `resetTransientState()` — clear queues/timers/combat
3. `resetFightCaveState()` — destroy previous instance + NPCs, clear FC vars
4. `resetPlayerStatsAndResources()` — fixed levels: Def=70, HP=700, Range=70, Prayer=43
5. `resetPlayerLoadout()` — coif, rune crossbow, black d'hide, snakeskin boots, 1000 adamant bolts, 8 ppots, 20 sharks
6. `resetFightCaveInstance()`:
   - `player.smallInstance(Region(9551), levels=3)` — allocate 2×2 region instance
   - Copy zones from source region via `DynamicZones.copy()`
   - Teleport player to Tile(2400,5088) + instance offset
   - `fightCave.startWave(player, wave, start=false)`
   - Queue 2-tick delayed aggression pass

**Key one-time vs recurring**:
- One-time: steps 1–12 (boot through tick loop start)
- Recurring per tick: 20 tick stages
- Recurring per login: demo flag marking
- Recurring per episode: full state/instance/loadout reset

### Phase 2 — Cache / Resource Resolution Findings

**Cache index inventory** (35 indices, FC uses 9):

| Index | Name | FC Usage |
|-------|------|----------|
| 2 | CONFIGS | Underlay (archive 1), Overlay (archive 4) |
| 3 | INTERFACES | Interface 316 (wave counter overlay) |
| 4 | SOUND_EFFECTS | NPC sounds 291–1184 |
| 5 | MAPS | Region terrain (m37_79, l37_79, etc.) |
| 7 | MODELS | NPC/object 3D models |
| 18 | NPCS | NPC definitions 2734–2746 |
| 20 | ANIMATIONS | Anim IDs 9230–9300 |
| 21 | GRAPHICS | Projectile/GFX IDs 444, 1616, 1622–1627 |
| 26 | TEXTURE_DEFINITIONS | Material/texture properties |

**FC NPC roster** (from `tzhaar_fight_cave.npcs.toml`):

| NPC | ID | HP | Attack Style | Size |
|-----|----|----|-------------|------|
| Tz-Kih | 2734 | 100 | Melee (drains prayer) | 1 |
| Tz-Kek | 2736 | 200 | Melee (splits on death) | 1 |
| Tz-Kek small | 2738 | 100 | Melee | 1 |
| Tok-Xil | 2739 | 400 | Melee + Ranged(14) | 1 |
| Yt-MejKot | 2741 | 800 | Melee + heals nearby NPCs | 1 |
| Ket-Zek | 2743 | 1600 | Melee + Magic(14), height=64 | 1 |
| TzTok-Jad | 2745 | 2500 | Melee + Range + Magic, height=512 | 1 |
| Yt-HurKot | 2746 | 600 | Melee, heals Jad | 1 |

**FC animations** (from `tzhaar_fight_cave.anims.toml`): IDs 9230–9300. Key: Jad magic=9300, Jad range=9276, Jad melee=9277.

**FC projectiles/GFX** (from `tzhaar_fight_cave.gfx.toml`): Tok-Xil projectile=1616, Ket-Zek travel=1623, Jad travel=1627, Jad range impact=451.

**Map archives**: Terrain tiles from `m{x}_{y}` archives, objects from `l{x}_{y}` archives in Index 5. FC regions: (37,79), (37,80), (38,80), (39,80).

**Underlay/overlay definitions**: Loaded from Config archive 1 (underlay) and 4 (overlay). Each has RGB colour, texture ID, scale. Two DIFFERENT RGB→HSL algorithms (underlay vs overlay — see Phase 4).

### Phase 3 — Region / Scene Construction Findings

**Exact load order** (from `MapDefinitions.loadCache()`):

1. **Tiles first**: `loadSettings(cache, regionX, regionY, settings)` → `MapTileDecoder.loadTiles()` populates 16384-byte settings array (4 levels × 64×64 tiles)
2. **Collision second**: `collisions.decode(settings, regionX<<6, regionY<<6)` → reads settings byte per tile: BLOCKED_TILE=0x1, BRIDGE_TILE=0x2, ROOF_TILE=0x4
3. **Objects third**: `decoder.decode(cache, settings, regionX, regionY, xteas)` → reads `l{x}_{y}` archive, delta-compressed object IDs + positions, extracts shape (bits 2-7) and rotation (bits 0-1). Skips bridge objects.

**Collision comes from BOTH tiles AND objects**:
- Tile collision: BLOCKED_TILE flag in settings byte → `CollisionFlag.FLOOR`
- Object collision: `ObjectDefinitionFull.solid` (0=none, 1=half, 2=full) + shape-based wall/corner collision modification via `GameObjectCollisionAdd`

**Object shape dispatch** (25 shapes):
- 0: WALL_STRAIGHT, 1: WALL_DIAGONAL_CORNER, 2: WALL_CORNER, 3: WALL_SQUARE_CORNER
- 4-8: Wall decorations (various offset types)
- 9: WALL_DIAGONAL
- 10: CENTRE_PIECE_STRAIGHT, 11: CENTRE_PIECE_DIAGONAL
- 12-21: Roof types
- 22: GROUND_DECOR
- 23-24: Alt corner types

**Rotated zone copy** (for instancing): `MapObjectsRotatedDecoder` applies rotation transform to object position within 8×8 zone using `rotateX/rotateY` functions. Object rotation is additive: `objRotation = (rotation + zoneRotation) & 0x3`.

### Phase 4 — Terrain Material / Render Construction Findings

**Two different RGB→HSL algorithms**:

**Underlay** (`UnderlayDecoder.computeHsl`): Standard RGB→HSL with separate output fields (hue, saturation, lightness, chroma). Outputs 0-255 range for each. Chroma depends on lightness threshold (0.5).

**Overlay** (`OverlayDecoder.shiftRGBColours`): Packed into single int: `(l2>>1) + ((s2>>5<<7) + (h2&0xff>>2<<10))`. Has **dynamic saturation reduction** based on lightness:
- l2 > 243: s2 >>= 4 (÷16)
- l2 > 217: s2 >>= 3 (÷8)
- l2 > 192: s2 >>= 2 (÷4)
- l2 > 179: s2 >>= 1 (÷2)
- Magenta (0xFF00FF) returns -1

**FC terrain exporter tool found**: `tools/.../FightCavesTerrainExporter.kt`
- Loads 4 FC regions: (37,79), (37,80), (38,80), (39,80)
- Creates 3×3 RegionManager per region (for neighbor blending)
- Renders to baked PNG at 4px/tile (256px per region), upscales to 8px/tile
- Pipeline: MapDecoder → OverlayDecoder → UnderlayDecoder → MaterialDecoder → RegionManager → MapTileSettings → renderRegion()

**Client tile mesh** (Class237/Class237_Sub1):
- `anIntArrayArrayArray3122[plane][x][y]` = height map, -960 units between planes
- `aByteArrayArrayArray3113` = overlay IDs, `aByteArrayArrayArray3123` = underlay IDs
- `method1685()` = opaque terrain render, `method1697()` = transparent terrain render
- `method1680()` = depth/blend state setup

**ObjectDefinitionFull rendering fields**:
- `modelIds: Array<IntArray>?` — model IDs per type
- `brightness: Int`, `contrast: Int` — lighting parameters
- `originalColours/modifiedColours: ShortArray?` — opcode 40 recolor
- `originalTextureColours/modifiedTextureColours: ShortArray?` — opcode 41 retexture
- `sizeX/sizeY: Int` — tile footprint
- `offsetX/Y/Z: Int` — model position offset
- `solid: Int` (0/1/2), `block: Int` (PROJECTILE=0x8 | ROUTE=0x10)
- `mapscene: Int` — minimap icon override

### Phase 5 — Player / NPC Visual Construction Findings

**NPC visual pipeline** (NPCDefinitionFull):
- `modelIds: IntArray?` — composed model mesh IDs from cache Index 7
- `renderEmote: Int` — idle animation ID
- `scaleXY/scaleZ: Int` — scale factors (128 = 100%)
- `originalColours/modifiedColours` — recolouring pairs
- `headIcon: Int` — overhead prayer icon sprite
- `hitbarSprite: Int` — HP bar sprite
- `lightModifier/shadowModifier: Int` — lighting adjustments
- `height: Int` — render height (Jad=512, Ket-Zek=64)

**Player visual pipeline** (Appearance + AppearanceEncoder):
- 12 equipment slots (head, cape, amulet, weapon, chest, shield, legs, hands, feet, ring, ammo, reserved)
- 5 colour slots (hair, facial, torso, legs, feet)
- `headIcon: Int` — active prayer overhead icon
- `emote: Int` — render animation (idle=1426)
- `transform: Int` — NPC transform override (-1 = normal player)
- Equipment model IDs come from item definitions in cache Index 19
- Encoded per tick via `AppearanceEncoder.encode()` — sends appearance packet with: transform flag, equipment slots, colours, combat level, display name, flags

**For C viewer simplification**: Player model = composite of equipment item models. NPC model = NPC definition modelIds. Both get recolour/retexture applied. Animations are optional for static viewer. Scale/height transforms are multiplicative (128 = 1.0x).

### Phase 6 — UI / Sprite Construction Findings

**Sprite system**: Cache Index 8. Each SpriteDefinition contains array of IndexedSprite: offsetX/Y, width/height, palette (256 ARGB), raster (palette indices), optional alpha. Palette colour 0 = transparent.

**Font system**: Cache Index 13. FontDefinition has glyph widths, kerning adjustments, vertical spacing. Text rendered as bitmap glyphs.

**HitSplat system**: HitSplatDefinition has font ID, text colour, icon sprite, left/middle/right digit sprites, offset X/Y, duration (70 ticks = 2.1s), fade. Marks: Melee=0, Range=1, Magic=2, Regular=3, Missed=8, Healed=9. Critical = mark+10.

**FC interface**: Interface 316 (game_screen_overlay) with 2 components: background sprite (id=1) and wave_number text (id=2).

**Prayer overhead icons**: Protection prayers (group 6): protect_from_melee (level 43), protect_from_missiles (level 40), protect_from_magic (level 37). Each displays a sprite over the player head via `appearance.headIcon`.

**Minimal UI subset for C viewer**:
- HP/prayer bars (runtime drawing, no sprites needed)
- Wave counter text (runtime drawing)
- Prayer overhead icon (3 protection sprites from cache, or procedural indicators)
- Hitsplat damage numbers (bitmap font or procedural text)
- NPC names/combat levels (bitmap font or procedural text)

### Phase 7 — Frame Render Path Findings

**Exact frame order** (from Canvas_Sub1.method123()):

1. **Map data load** — load underlay/overlay/object data from cache
2. **GPU init** — `Class348_Sub42_Sub2.method3171()` — allocate GPU resources, set up 4-level terrain mesh
3. **Terrain mesh build** — `new Class237_Sub1(4, width, height, false)` — 4 planes
4. **Opaque terrain render** — `method1685()` — base terrain with depth test, per-object lighting
5. **Static object render** — `method1090()/method944()` — walls, rocks, non-transparent objects
6. **Depth/blend state setup** — `method1680()` — prepare for transparency pass
7. **Transparent object render** — `method1697(false, ...)` — semi-transparent geometry (bone piles)
8. **Overlay/water render** — `method1685()` + `method1697(true, ...)` — water/overlays with alpha blending
9. **Tile-by-tile iteration** — `Class203.method1479()` — 4 planes × width × height tiles, cached in hashmap
10. **UI/2D overlay** — follows 3D pipeline

**State changes**: Opaque pass writes depth. Transparent pass reads depth but does not write (`rlDisableDepthMask`). Overlay pass enables alpha blending. This matches our viewer's two-pass approach (opaque scene → alpha bone piles).

**Camera**: Fixed pitch range 22.5°–67.5°. Default pitch ~35° from horizontal. Zoom formula: `zoomDerived = ((zoom>>3)*3 + 600) << 2`. At zoom=1024: 3936 RS units = 30.75 tiles distance. Tile composite key: `y | (z << 14) | (plane << 28)`.

### Phase 8 — Runtime Update Loop Findings

**Tick rate**: 600ms (from `GameLoop.ENGINE_DELAY = 600L`)

**Client frame rate**: Decoupled from server tick. Client renders at display refresh rate. 3D scene geometry is STATIC — only entity positions/animations update per server tick.

**Static (loaded once)**:
- Terrain mesh (4D array [level][x][y])
- Tile height values
- Underlay/overlay data arrays
- Collision map flags
- Static object placement
- **Sent once per region change via `mapRegion()` or `dynamicMapRegion()`**

**Dynamic (per tick)**:
- Player position, movement, animation, appearance — `PlayerUpdateTask` every 600ms
- NPC position, movement, animation — `NPCUpdateTask` every 600ms
- Object state changes (doors, levers) — `ZoneBatchUpdates` per tick
- Floor items spawn/despawn — `ZoneBatchUpdates` per tick
- Hitsplats, projectiles, GFX — per-event via character update flags

**Region reload triggers**:
1. Player crosses zone boundary AND outside viewport radius
2. Dynamic zone boundary crossed
3. Initial login
- Sends XTEA-encrypted region data with zone coordinates

**For C viewer**: Terrain/objects are STATIC after load — build once, render every frame. Only entity positions, HP, animations, hitsplats change per tick. This is the key simplification: bake terrain + objects offline, animate entities at runtime.

### Phase 9 — Arena Footprint / Map Trace Findings

**A. Arena bounds trace**

| Property | Value |
|----------|-------|
| Region ID | 9551 |
| Region coords | (x=37, y=79) |
| Min tile X | 2368 |
| Max tile X | 2431 |
| Min tile Y | 5056 |
| Max tile Y | 5119 |
| Width | 64 tiles |
| Height | 64 tiles |
| Planes | 0, 1, 2 (3 levels) |
| Centre tile | (2400, 5088) |

**B. Spawn area definitions** (from `tzhaar_fight_cave.areas.toml`):

| Area | X range | Y range | Size | Purpose |
|------|---------|---------|------|---------|
| multi_area | [2368,2559] | [5056,5183] | 192×128 | Multi-combat zone (larger than single region) |
| north_west | [2378,2385] | [5102,5109] | 8×8 | NPC spawn zone |
| south_west | [2379,2385] | [5070,5076] | 7×7 | NPC spawn zone |
| south | [2402,2408] | [5070,5076] | 7×7 | NPC spawn zone |
| south_east | [2416,2422] | [5080,5086] | 7×7 | NPC spawn zone |
| none (center) | [2397,2403] | [5085,5091] | 7×7 | Center spawn (Jad) |

**C. Instance vs source footprint**:
- Source region: 64×64 tiles (Region 9551)
- Instance allocation: `Instances.small()` = SMALL_SIZE=3 regions = 192×192 tiles maximum
- Actual copy: `DynamicZones.copy(from=Region(9551), to=instanceRegion, levels=3)` copies only the source 64×64 zones
- The instance has 192×192 tile space but only 64×64 is populated — rest is void

**D. Tile-to-world scale**:
- At the Tile abstraction: 1 tile = 1 coordinate unit
- RS2 physics convention: 128 world units per tile (for sub-tile positioning)
- In the client renderer: tile coordinates are direct array indices, world position derived from `(tileX * 128, height, tileY * 128)` in render units
- Height between planes: -960 render units

**E. Height data**: `MapTile.height` field (8 bits, 0-255). Actual height = `height * -8` in render units (from MapTileDecoder). Plane offsets add -960 per level.

**F. Arena rebuild contract**

The rebuilt C viewer must use:
- **Arena tile dimensions**: 64×64 tiles (Region 9551, one region)
- **Arena center**: tile (2400, 5088), which is local tile (32, 32) within the region
- **Tile/world scale**: 1 tile = 1.0 world unit in the viewer (or 128 RS units if sub-tile precision needed)
- **Floor footprint**: all tiles within region (2368,5056)→(2431,5119) that have underlay/overlay data
- **Static boundary**: cave wall objects define the visible boundary; aggregate bounding box fits within the 64×64 region
- **Height**: use `MapTile.height * -8 / 128.0` tiles for height-per-tile (normalized to tile units)
- **Planes**: render plane 0 only for floor; plane 0 objects for walls/decor
- **Instance offset is irrelevant**: viewer renders the source region directly, no instance allocation needed
- **The active playable arena is SMALLER than 64×64** — the walkable interior is bounded by cave walls. Exact walkable tile count requires collision data dump. The viewer should render the full 64×64 region and let the wall geometry define the visual boundary.

---

## Minimum Data and Runtime Behavior the Rebuilt C Viewer Must Reproduce

### Floor
- 64×64 tile terrain mesh from Region 9551 (plane 0)
- Per-tile underlay colour (UnderlayDefinition.colour + HSL conversion)
- Per-tile overlay colour + path type (15 tessellation patterns) + rotation
- Per-tile height from MapTile.height
- Terrain texture (lava crack pattern). **Primary path**: use the Kotlin `FightCavesTerrainExporter` tool to bake a correct terrain PNG from the server-side decode pipeline. **Historical fallback** (not active): texture 521 bitmap decode from cache index 9 + UV mapping in the C viewer — this was the prior approach but is superseded by the baked PNG path.
- Sub-tile triangle tessellation matching RS2 path types 0-14

### Walls / Static Cave Geometry
- All objects from `l37_79` cache archive placed on plane 0
- Shape-based placement: walls (shapes 0-3,9), centre pieces (10-11), ground decor (22)
- Object recolouring (opcode 40: originalColours → modifiedColours per face)
- Object model geometry from cache Index 7
- Object position offsets from ObjectDefinitionFull.offsetX/Y/Z
- Rotation (0-3 quarter turns) applied to both position and model orientation

### Decor / Clutter
- Bone pile objects (9370-9373): ground decor (shape 22), 75-80% semi-transparent faces
- Alpha blending: two-pass render (opaque first, then transparent without depth writes)
- Bone pile base colours are warm brown — correct without recoloring

### Player
- Position from fc_fill_render_entities (tile coordinates → world position)
- Minimum: coloured shape (cylinder/capsule) at correct scale (0.6 tiles wide, 1.2 tiles tall)
- Stretch goal: composite model from equipment item models (rune crossbow, black d'hide set)
- Prayer overhead indicator (Melee/Range/Magic text or sprite)
- HP bar above head

### NPCs
- Position, type, HP from fc_fill_render_entities
- Minimum: type-coloured shapes at correct relative scale (Jad is height=512 vs normal height~128)
- Stretch goal: NPC models from cache Index 7 via NPCDefinitionFull.modelIds
- Per-type visual distinction (at minimum: size + colour)
- Jad telegraph indicator (MAGIC/RANGE text)
- HP bars above heads

### UI
- HP bar (green on red), prayer bar (blue), wave counter
- Inventory summary (sharks, prayer pots, bolts counts)
- Active prayer name
- Hitsplat damage numbers (procedural text or bitmap font)
- Controls help text
- Debug overlay (state hash, camera values, tick count)

---

## What Can Be Baked/Exported Offline vs What Must Remain Runtime Logic

### Baked Offline (export once from cache, load as static assets)

| Asset | Export Source | Format | Notes |
|-------|-------------|--------|-------|
| Terrain mesh | MapTileDecoder + region m37_79 | .terrain binary (vertices, indices, colours, UVs) | 64×64 tile grid, pre-tessellated with path types |
| Terrain texture | Sprite cache Index 8, texture 521 | PNG | Lava crack bitmap, UV-mapped to terrain |
| Terrain baked image | FightCavesTerrainExporter tool | PNG (256×256 or 512×512) | Fallback: baked colour per tile, no UV needed |
| Scene objects | MapObjectDecoder + region l37_79 | .scene binary (instances with model refs) | Object ID, position, rotation, shape per placement |
| Object models | Cache Index 7, model IDs from object defs | .models binary (vertices, faces, colours) | Pre-recoloured using opcode 40/41 data |
| NPC models | Cache Index 7, model IDs from NPC defs | .models binary | Pre-recoloured, one model set per NPC type |
| Sprites | Cache Index 8 | PNG files | Prayer icons, item icons, hitsplat graphics |
| Collision map | CollisionDecoder output for region | Binary grid (64×64 bytes) | 1=walkable, 0=blocked per tile |
| Arena occupancy mask | Derived from collision + underlay presence | CSV or binary grid | For viewer floor rendering bounds |

### Must Remain Runtime Logic (computed per tick/frame in the C viewer)

| Data | Reason | Source |
|------|--------|--------|
| Entity positions | Change every tick via fc_step | fc_fill_render_entities |
| Entity HP / alive state | Combat changes every tick | fc_fill_render_entities |
| Active prayer | Player action each tick | FcState |
| Hitsplat display | Damage events per tick | FcRenderEntity.damage_taken_this_tick |
| Jad telegraph | Changes per Jad attack cycle | FcRenderEntity.jad_telegraph |
| Wave counter | Advances on wave clear | FcState.wave |
| Inventory counts | Eat/drink actions | FcState.sharks/prayer_doses/ammo |
| Camera position | User input (orbit, zoom, pan) | Viewer runtime |
| Animation frame | Per-frame interpolation | Optional — can use static poses |
| Alpha blending state | Per-frame render pass ordering | Viewer runtime (two-pass: opaque → transparent) |

### Key Insight
The terrain, objects, and models are **100% static after load**. The server never modifies terrain or static objects during a Fight Caves episode. All dynamic state comes from entity positions, combat, and player actions — which are already provided by the C sim via `fc_fill_render_entities`. The viewer rebuild should load baked assets once and only update entity state per tick.

---

## PR9a-R2: Floor-Source Decision (2026-03-31)

Evaluated floor-source options in ranked order per the active rebuild plan.

### Option A — Kotlin FightCavesTerrainExporter — FOOTPRINT ONLY (reclassified)

**Attempted**: Ran the Kotlin `FightCavesTerrainExporter` tool via `./gradlew --no-daemon :tools:exportFcTerrain` on the oracle JDK 21 at `/home/joe/projects/runescape-rl-reference/`.

**Result**: Successfully produced `fc_terrain_region_37_79.png` — a 256×256 pixel RGBA PNG of Region 9551 at 4px/tile. This is exactly 64×64 tiles. The tool uses the server-side decode pipeline: MapDecoder → OverlayDecoder → UnderlayDecoder → MaterialDecoder → RegionManager → MapTileSettings → renderRegion(). The image contains floor-only terrain data (no walls, no decor, no entities, no UI).

**Artifact**: `demo-env/assets/fc_floor_region_37_79.png` (7,944 bytes, 256×256 RGBA)

**Mapping**:
- 256px / 64 tiles = 4.0 px/tile
- Image pixel (0,0) = top-left = tile (0,63) in RS2 coordinates (north-west corner)
- In the viewer: X=[0,64], Z=[0,64] on the Y=0 plane
- UV flip: image V=0 (top) maps to Z=64 (north), V=1 (bottom) maps to Z=0 (south)

**Reclassification (2026-03-31, after oracle screenshot comparison)**:

The Option A artifact is reclassified as a **footprint/alignment artifact only**, NOT a viable final floor source. Evidence from the fixed comparison screenshot pack:

- **Footprint/alignment: TENTATIVELY VALIDATED.** The overall cave outline, volcanic rim positions, interior shape, and player-centre alignment all match the oracle aerial view (160441.png). The 64×64 tile coverage appears correct.
- **Visual/material parity: FAILING.** The oracle floor (160519.png) is dominated by bright orange LAVA CRACK web lines over a near-black base (texture 521 bitmap, UV-tiled). The Option A artifact shows only blurred definition-colour patches (4px/tile map-view colours). The dominant visual element of the oracle floor is entirely absent.

The Kotlin `FightCavesTerrainExporter.renderRegion()` renders map-view definition colours (underlay/overlay HSL→RGB), NOT the in-game texture-mapped appearance. The exporter is fundamentally the wrong tool for producing visual parity — it produces a colour map, not a texture-rendered floor.

**Current artifact role**: Footprint/alignment validation layer only. Retained as a reference underlay but does not satisfy R2 visual acceptance.

### Fixed comparison screenshot pack (2026-03-31)

The following screenshots are the fixed R2 evaluation baseline:

| Screenshot | Source | View | Content |
|-----------|--------|------|---------|
| `160519.png` | Oracle | Gameplay ~35° | Lava cracks, no grid |
| `160511.png` | Oracle | Gameplay ~35° | Lava cracks, with green tile grid |
| `160441.png` | Oracle | Aerial top-down | Full arena, no grid |
| `160434.png` | Oracle | Aerial top-down | Full arena, with green/red grid |
| `Screenshot from 2026-03-31 16-13-50.png` | C/Raylib | Near-top-down | Current floor with grid |

### Floor-source decision ladder — final status

- **Option A (Kotlin orthographic colour bake)**: **ACCEPTED as R2 floor**. Produces correct arena shape with warm dark cave palette and smooth blending through the full Kotlin colour pipeline (MapDecoder → OverlayDecoder → UnderlayDecoder → MaterialDecoder → RegionManager → MapTileSettings → Gouraud render). 256×256 PNG at 4px/tile = exact 64×64 footprint. User confirmed acceptable visual.
- **Option A-refined**: Exhausted. Kotlin renderer is flat-colour Gouraud, hardcoded 4px/tile, no texture bitmap access.
- **Option B (structured per-tile binary)**: Exported successfully (`fc_floor_structured.bin`, 64×64, per-tile height/underlay/overlay/colour data). However, the vertex-coloured mesh built from raw definition RGB produced incorrect colours (blue/gray instead of the warm palette the Kotlin renderer produces through its full HSL blending pipeline). Retained as a data artifact, not the active render path.
- **Option C (oracle screenshot crop)**: Rejected. Perspective-distorted screenshots are not valid render assets.

### R2a — floor geometry, footprint, mapping — COMPLETE

Acceptance verified:
1. Floor renders: Option A texture on flat quad, correct cave shape and palette
2. 64×64 footprint: 256px / 4px/tile = 64 tiles per axis
3. Player at (32,32): camera follows player on the floor
4. Scale 1:1: quad = 64.0 world units = FC_ARENA_WIDTH
5. Grid aligns: G key draws integer tile boundaries
6. Shape matches oracle: user confirmed via screenshot comparison
7. Camera presets: 1=aerial, 2=gameplay, 3=close
8. Footprint verification: printed to stderr at startup

Floor is flat at Y=0 (no height variation). Per-tile height data available in `fc_floor_structured.bin` if needed later.

### R2b — floor material / visual parity — SKIPPED

The oracle floor uses texture 521 (lava-crack bitmap, procedurally generated by HD client). The Kotlin bake produces definition colours only. User accepted the Option A visual as sufficient. R2b deferred — not blocking R3.

### Implementation (current)

- `viewer.c`: Loads `fc_floor_region_37_79.png` as Raylib texture on a flat 4-vertex quad (0,0,0) to (64,0,64).
- UV orientation: V flipped (image top = north = Z=64, image bottom = south = Z=0).
- Grid overlay (G key) for footprint verification.
- Fixed camera presets: 1=aerial, 2=gameplay, 3=close.
- Debug overlay shows floor source.
- Footprint verification printed to stderr at startup.

### Additional data artifacts (retained, not active render path)

- `fc_floor_structured.bin`: 64×64 binary grid (16 bytes/tile) — height, underlayId, overlayId, overlayPath, overlayRotation, settings, blocked, RGB colours, computedHeight. Useful for future structured floor work.
- `fc_floor_colours.png`: 64×64 pixel raw-colour image from definition colours. Diagnostic only.
- `fc_floor_heightmap.txt`: Human-readable 64×64 height grid. Diagnostic only.

---

## R3 Stopping Point (2026-04-01)

### Current viewer state

Reverted to the working R2 floor + Kotlin wall objects state (viewer_rollback_r3.c). The floor texture renders correctly with the correct orientation. The wall objects render but are visually broken — individual disconnected model shapes instead of continuous terraced cliff terrain.

### Root cause of wall rendering failure

Multiple attempts to render the cave wall terrain in C/Raylib have failed because the approach was fundamentally wrong. The individual 3D object models from the cache (shapes 0-3, 9) are NOT how the oracle client renders cave walls. The oracle client's scene builder (`s_Sub1.method3978`) takes those models and **stitches them into a continuous terrain surface** using height interpolation, per-tile tessellation, and Gouraud colour blending. Simply placing the raw model meshes at tile positions produces disconnected angular shapes with inside-out faces — not the smooth terraced cliff surface the oracle shows.

Attempts that failed:
1. Python `export_fc_objects.py` SCNE export — correct data but visually corrupted when rendered
2. Kotlin `FightCavesWallExporter` using the provided meshdecoder.kt algorithm — same visual result
3. Heightmap-only terrain mesh from structured binary — correct shape but wrong colours, lost wall detail
4. Client height grid dump from `anIntArrayArrayArray3122` — gives correct terrain shape but no wall geometry

### Next step: Java/Kotlin-side asset construction + export

**The agreed approach going forward**: Let the Java/Kotlin oracle architecture handle ALL mesh decoding, construction, and stitching — then export the fully constructed assets to be loaded directly by the C/Raylib viewer. This means:

1. **The oracle client (or Kotlin tools) builds the complete scene mesh** — terrain surface with wall geometry stitched in, using the exact same code that produces the correct visual in the oracle screenshots.

2. **Export the constructed mesh** to a simple format (OBJ or SCNE binary) with vertex positions + vertex colours already computed.

3. **The C/Raylib viewer just loads and renders** the pre-built mesh. No cache decoding, no model parsing, no scene construction on the C side.

This applies to all static assets:
- **Floor terrain** — heightmapped surface with colours
- **Wall/cliff terrain** — the elevated rim with stitched wall object geometry
- **Decor/clutter** — bone piles and ground decorations
- **NPC models** — pre-decoded with recolouring applied
- **Player model** — pre-composed from equipment models

### Where to resume

The scene dump hook is installed in `FightCavesSceneDumper.java` (auto-triggers on first render frame). It successfully exports the height grid from the running client. The next investigation needs to:

1. Find the `s_Sub1` renderer instances within `Class147.aClass357ArrayArrayArray2029` (the scene graph)
2. Access the `Class236` tile mesh arrays (`aClass236ArrayArray8222`) which contain the FINAL stitched terrain with wall geometry
3. Export those Class236 tile meshes (vertices + triangle indices + HSV colours) as a combined SCNE binary
4. Load that in the C/Raylib viewer

Key files for resuming:
- `current_fightcaves_demo/void-client/client/src/FightCavesSceneDumper.java` — the dump hook
- `current_fightcaves_demo/void-client/client/src/s_Sub1.java` — scene builder with Class236 arrays
- `current_fightcaves_demo/void-client/client/src/Class236.java` — tile mesh data structure
- `current_fightcaves_demo/void-client/client/src/Class147.java` — scene graph root
- `current_fightcaves_demo/void-client/client/src/OutputStream_Sub1.java` — render hook location
- Rollback point: `demo-env/src/viewer_rollback_r3.c`

### Oracle demo commands (for reference)

Server:
```bash
cd /home/joe/projects/runescape-rl-reference/current_fightcaves_demo && ./gradlew --no-daemon :game:runFightCavesDemo
```

Client:
```bash
java -jar /home/joe/projects/runescape-rl-reference/current_fightcaves_demo/.artifacts/void-client/void-client-fight-caves-demo.jar --address 127.0.0.1 --port 43594 --username fcdemo01 --password pass123
```

C/Raylib viewer:
```bash
cd /home/joe/projects/runescape-rl/claude/runescape-rl/build && ./demo-env/fc_viewer
```
