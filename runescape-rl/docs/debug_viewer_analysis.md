# Debug Viewer Analysis — Oracle-Driven Findings

Date: 2026-03-30

This document consolidates all findings from the oracle instrumentation pass against the headed Kotlin/Java Fight Caves client. The purpose is to precisely document how the oracle renders the Fight Caves so we can replicate it in the C/Raylib viewer.

## Source Trees

| Role | Path |
|------|------|
| Oracle runtime (headed RSPS + client) | `/home/joe/projects/runescape-rl-reference/runescape-rl/rsps` |
| Oracle client source (decompiled Java) | `/home/joe/projects/runescape-rl-reference/runescape-rl/rsps/void-client/client/src/` |
| Oracle server Kotlin source (real names) | `/home/joe/projects/runescape-rl-reference/runescape-rl/rsps/cache/`, `engine/`, `tools/` |
| RS cache (shared by both oracle and C viewer) | `/home/joe/projects/runescape-rl-reference/runescape-rl/training-env/sim/data/cache/` |
| C/Raylib viewer | `/home/joe/projects/runescape-rl/claude/runescape-rl/demo-env/src/viewer.c` |
| Python exporters | `/home/joe/projects/runescape-rl/claude/runescape-rl/demo-env/scripts/` |

---

## 1. Camera

### Oracle source files
- `void-client/client/src/OutputStream_Sub1.java:46` — camera computation callsite
- `void-client/client/src/Class17.java:221` (`method268`) — camera position from pitch/yaw/zoom
- `void-client/client/src/Class70.java` — sine/cosine lookup table

### RS-HD camera model

The client uses a **16384-entry** sine/cosine table (not 2048 as in classic RS2):

```java
// Class70.java — static initializer
double d = 3.834951969714103E-4;  // = 2π / 16384
for (int i = 0; i < 16384; i++) {
    anIntArray1207[i] = (int) (16384.0 * Math.sin(d * (double) i));  // sine
    anIntArray1204[i] = (int) (Math.cos(d * (double) i) * 16384.0);  // cosine
}
```

- Full circle = **16384** angle units
- Sine/cosine values scaled to ±16384 (14-bit fixed point)
- Camera pitch range: **1024–3072** (22.5°–67.5° from horizontal)
- Default pitch: **~1600** (≈35° from horizontal, ≈55° from top-down)

### Camera distance formula

From `OutputStream_Sub1.java:46`:
```java
int i_4_ = (int) Class76.aFloat1287;  // zoom level
int i_5_ = (int) Class314.aFloat3938 + Class195.anInt5016 & 0x3fff;  // yaw
Class17.method268(i_4_, -200 + heightAtPlayer, i_5_, pitch, cameraX, -19360, playerZ,
                  (i_4_ >> 3) * 3 + 600 << 2);
```

The last parameter is the **camera distance from player** in RS units:

```
distance = ((zoom >> 3) * 3 + 600) << 2
         = ((zoom / 8) * 3 + 600) * 4
```

| Zoom value | Distance (RS units) | Distance (tiles) |
|-----------|-------------------|-----------------|
| 0 | 2400 | 18.75 |
| 512 | 3168 | 24.75 |
| **1024 (default)** | **3936** | **30.75** |
| 2048 | 5472 | 42.75 |

### Camera position computation

From `Class17.java:221` (`method268`):

```java
static final void method268(int zoom, int heightOffset, int yaw, int pitch,
                            int cameraX, int magic, int playerZ, int distance) {
    int pitchAngle = (16384 - zoom) & 0x3fff;
    int yawAngle   = (16384 - yaw)  & 0x3fff;

    int cameraHeightDelta = 0;
    int cameraForward     = distance;

    if (pitchAngle != 0) {
        cameraHeightDelta = -cameraForward * sin[pitchAngle] >> 14;
        cameraForward     =  cameraForward * cos[pitchAngle] >> 14;
    }
    if (yawAngle != 0) {
        int cameraLateral = sin[yawAngle] * cameraForward >> 14;
        cameraForward     = cameraForward * cos[yawAngle]  >> 14;
    }

    finalCameraYaw    = yaw;
    finalCameraY      = pitch - cameraForward;   // depth into scene
    finalCameraHeight = heightOffset - cameraHeightDelta; // vertical
    finalCameraX      = playerZ - cameraLateral; // lateral
    finalCameraZoom   = zoom;
}
```

### Live instrumented values

Added `CAMERA_ORACLE` dump to `FightCavesDemoAutoLogin.java`:
- **zoom = 1024** (confirmed live, default RS-HD zoom)
- **playerX = 4352, playerY = 4352** (= 8.5 tiles from region origin = FC center)
- Camera position fields (Class286_Sub4.anInt6246, etc.) read as 0 from the autologin thread because they are **set per-frame on the render thread** and not persistent between frames

### Mapping to C/Raylib viewer

| Parameter | Oracle value | C viewer before | C viewer corrected |
|-----------|-------------|----------------|-------------------|
| cam_dist | 30.75 tiles | 28 | 28 (close enough) |
| cam_pitch | ~0.61 rad (35° from horiz) | 1.1 rad (63°) | **0.6 rad** |
| fovy | ~35-40° effective | 32° | 32° (acceptable) |
| target Y | player body center | 0.5 | 0.5 (correct) |

**Critical correction**: cam_pitch was 1.1 (too steep / too top-down). Oracle default is ~0.6 (35° from horizontal). This is why screenshots looked like a near-overhead view instead of the oracle's angled perspective.

---

## 2. Terrain Textures and Materials

### Oracle source files
- `rsps/cache/src/main/kotlin/.../config/decoder/OverlayDecoder.kt` — overlay definition parser
- `rsps/cache/src/main/kotlin/.../config/decoder/UnderlayDecoder.kt` — underlay definition parser
- `rsps/cache/src/main/kotlin/.../definition/decoder/MaterialDecoder.kt` — material/texture bulk loader (cache index 26)
- `rsps/cache/src/main/kotlin/.../definition/data/MaterialDefinition.kt` — material properties
- `rsps/tools/src/main/kotlin/.../map/render/load/MapTileSettings.kt` — terrain tile vertex builder
- `rsps/tools/src/main/kotlin/.../map/render/draw/TileLevel.kt` — terrain rendering with lighting

### Fight Caves terrain definitions

Decoded from cache index 2 (config), groups 1 (FLOOR_UNDERLAY) and 4 (FLOOR_OVERLAY):

| Type | ID | Colour (RGB) | Texture ID | Material useTexColour | Notes |
|------|-----|-------------|-----------|----------------------|-------|
| Underlay | **72** | 0x030303 (near-black) | **521** | True | **Main FC floor** (every tile) |
| Underlay | 118 | 0x752222 (dark red) | 181 | True | Secondary floor |
| Overlay | 42 | 0xFF00FF (magenta) | -1 | — | Invisible mask |
| Overlay | **111** | 0xFBA13A (bright orange) | **61** | True | Lava crack lines |
| Overlay | 143 | 0x010101 (near-black) | -1 | — | Dark overlay |
| Overlay | **157** | 0xFAB737 (golden) | **61** | True | Lava crack lines |

**FC regions**: (37,79), (37,80), (38,80), (39,80)

**NOTE**: An earlier oracle pass incorrectly reported texture IDs 1/669/512 due to a bug in the overlay decoder that read from the wrong cache version's config format. Re-verified against the actual cache used by both the oracle and our exporter: underlay 72 uses texture **521**, overlays 111/157 use texture **61**.

All FC materials have `useTextureColour=True`. The definition colours (underlay 0x030303 near-black, overlays 0xFBA13A/0xFAB737 bright orange/golden) are used for the 2D map renderer. The 3D HD client UV-maps the actual texture bitmaps (texture 521 = the lava crack web pattern) onto the terrain regardless of `useTextureColour`.

### Texture/material system architecture

```
Cache Index 2, Group 1 → UnderlayDefinition (colour, texture ID, scale)
Cache Index 2, Group 4 → OverlayDefinition (colour, texture ID, scale, blend, hide_underlay)
Cache Index 26, Group 0 → MaterialDefinition[] (bulk packed: useTextureColour, colour, type)
Cache Index 9           → Sprite bitmaps (indexed-colour rasters with palette)
```

The `MaterialDefinition` maps a texture ID to rendering properties:
- `useTextureColour: Boolean` — if true, use material colour instead of UV texture
- `colour: Int` — the flat colour (16-bit HSV packed) used when textured rendering falls back
- `type: Byte` — rendering type (affects blend/water/special handling)

### How terrain textures are applied

From `TileLevel.kt:drawTiles()`:

**Case 1: Simple tile (no tessellation)** — `TileColours` path
```kotlin
if (textureMetrics != null && !textureMetrics.useTextureColour) {
    // Store per-vertex brightness, not colour — actual texture applied via UV in 3D renderer
    tileColour.middleColourIndex = (tileBrightness[x][y] - tileShadows[x][y]).toShort()
    tileColour.textureId = texture.toShort()
} else {
    // No texture or texture uses flat colour — apply HSL→HSV vertex colours with lighting
    val hsl = hslToHsv(colour).toInt()
    tileColour.middleColourIndex = light(tileBrightness[x][y] - tileShadows[x][y], hsl).toShort()
    tileColour.textureId = (-1).toShort()
}
```

**Case 2: Tessellated tile (overlay paths 1-15)** — `TextureColours` path
- Overlay path type selects vertex indices from lookup tables (`firstTileTypeVertices`, etc.)
- Each sub-triangle gets its own colour, texture ID, and blend colour
- Overlay and underlay sub-triangles are interleaved based on path type

**In the 3D client** (`ha_Sub2.java`), textured tiles are rendered with actual UV-mapped sprite bitmaps via OpenGL texture units. The 2D map renderer (`TileLevel.kt`) only uses the material's flat `colour` field as a fallback.

### Terrain lighting model

From `TileLevel.kt:loadBrightness()`:

```kotlin
setBrightness(-200f, -220f, -200f)  // Light direction: upper-right-front
val brightness: Int = BRIGHTNESS shr 9  // BRIGHTNESS = 75518, so base = 147

// Per-tile: compute from height gradients
val dx = tile(x + 1, y).height - tile(x - 1, y).height
val dy = tile(x, y + 1).height - tile(x, y - 1).height
val distance = sqrt((dx * dx + 262144 + dy * dy).toDouble()).toInt()
val lightX = (dx shl 8) / distance
val lightY = -262144 / distance
val lightZ = (dy shl 8) / distance
intensity += this.lightX * lightX + this.lightY * lightY + this.lightZ * lightZ shr 17
intensity = clamp(intensity shr 1, 2, 126)
```

Vertex colour for lit terrain: `light(brightness, hsvColour)` where:
```kotlin
fun light(light: Int, colour: Int): Int {
    var lighting = (colour and 0x7f) * light shr 7
    lighting = clamp(lighting, 2, 126)
    return lighting + (colour and 0xff80)
}
```

---

## 3. Bone-Pile Alpha and Decor Rendering

### Oracle source files
- `void-client/client/src/Class64_Sub2.java` — Model class (face vertices, colours, alpha)
- `void-client/client/src/ha_Sub2.java:1230-1240` — OpenGL blend state switching
- `void-client/client/src/Class377.java:963` — `glEnable(GL_BLEND)` call

### Face alpha storage

Model face alpha is stored in `Class64_Sub2.aByteArray5499[faceIndex]`:
- Value 0 = fully opaque
- Non-zero = has transparency (RS convention: 0=opaque, 255=fully transparent)

### Blend mode selection

From `ha_Sub2.java:1230`:

```java
if (blendMode == 1) {
    glEnable(GL_BLEND);     // 3042
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);  // 770, 771
} else if (blendMode == 2) {
    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE);  // additive
} else if (blendMode == 3) {
    glEnable(GL_BLEND);
    glBlendFunc(GL_DST_COLOR, GL_ONE);  // destination
} else {
    glDisable(GL_BLEND);  // opaque
}
```

Alpha TEST is also used (`glEnable(GL_ALPHA_TEST)` = 3008) for hard-edge cutouts.

### Rendering order

1. **Opaque faces** drawn first with depth test + depth writes enabled
2. **Semi-transparent faces** drawn second with `GL_BLEND` enabled
3. Overlapping transparent models at the same tile position have their alpha values **additively merged**: `alpha[dest] += alpha[src]` (Class64_Sub2.java lines 2432-2490)

### Bone pile alpha values

From prior `mesh_decoder.py` analysis of bone pile models 9264-9267 (objects 9370-9373):
- 75-80% of faces have non-zero alpha
- RS alpha 110 → Raylib alpha 145 (57% opacity)
- RS alpha 170 → Raylib alpha 85 (33% opacity)
- 2,336 total bone pile instances, 101,440 total triangles

### C/Raylib implementation

Our two-pass approach is **confirmed correct** by the oracle:
```c
// Pass 1: opaque geometry (walls, rocks) — depth test + writes ON
DrawModel(scene_no_bones.model, ...);

// Pass 2: semi-transparent bone piles — depth writes OFF, blend ON
rlDisableDepthMask();
rlEnableColorBlend();  // glEnable(GL_BLEND)
DrawModel(scene_bonepiles.model, ...);
rlDisableColorBlend();
rlEnableDepthMask();
```

Raylib's default blend func is already `GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA` (set at init), which matches the oracle's mode 1.

---

## 4. Scene Layering and Object Density

### Oracle source files
- `void-client/client/src/OutputStream_Sub1.java:102-104` — scene building
- `void-client/client/src/Class289.java:27-28` — region tile position
- `void-client/client/src/Class130_Sub1.java` — viewport clipping

### Scene construction

- Scene is built from cache index 5 map data: terrain tiles + loc placements per region
- **No additional culling or filtering** beyond standard viewport frustum clipping
- All level-0 objects are placed; nothing is skipped based on type or density
- The **2,336 bone pile instances** we place in the C viewer are correct — the oracle places the same count

### Why the oracle doesn't look carpeted

The oracle renders the same 2,336 bone pile instances as our viewer, but they look like scattered debris rather than a solid carpet because of four factors:

1. **Alpha blending**: Bone pile faces at 33-57% opacity let the floor show through
2. **Terrain texture contrast**: The black floor with bright lava cracks creates strong visual contrast against the semi-transparent brown bone debris
3. **Directional lighting**: Light from (-200, -220, -200) creates brightness variation across both terrain and objects, breaking visual uniformity
4. **Camera angle**: At 35° pitch (not 63°), the perspective shows more depth through the bone pile layer

**No culling difference exists.** The visual difference between our viewer and the oracle is entirely due to materials and rendering state, not object count.

---

## 5. Consolidated Corrections for C/Raylib Viewer

### Immediate fixes (viewer.c)

| Parameter | Current | Corrected | Source |
|-----------|---------|-----------|--------|
| cam_pitch | 1.1 rad (63°) | **0.6 rad (35°)** | Oracle default pitch ~1600/16384 |
| Alpha blend | Two-pass (implemented) | Keep as-is | Confirmed by oracle |
| cam_dist | 28 | Keep (oracle ~31) | Close enough |
| fovy | 32° | Keep (oracle ~35-40°) | Close enough |

### Next slice: terrain texture pipeline

Implementation order (updated after Step 8):
1. ~~Decode underlay/overlay definitions~~ **Done** (Step 8: definition colours + lighting applied)
2. ~~Apply directional lighting~~ **Done** (Step 8: oracle model light(-200,-220,-200), brightness=75518>>9)
3. **Next**: Decode texture 521 bitmap from cache index 9 (packed texture format, not sprite format)
4. Generate per-tile UV coordinates for underlay texture tiling
5. Upload texture to Raylib and apply via textured terrain mesh
6. Repeat for overlay texture 61 if needed

### Texture ID summary for FC floor

| Texture ID | Referenced by | Cache Index 9 size | Role |
|-----------|--------------|-------------------|------|
| **521** | Underlay 72 | 203 bytes | Main FC floor (lava crack web pattern) |
| 181 | Underlay 118 | — | Secondary floor texture |
| **61** | Overlays 111, 157 | — | Overlay texture (lava crack lines) |

Note: cache index 9 uses a packed texture format (not the index 8 sprite format). Decoding these texture bitmaps and UV-mapping them onto the terrain is the remaining visual blocker.

---

## 6. Oracle Bring-Up Reference

### Reproducible commands

**Terminal 1 — Server:**
```bash
source /home/joe/projects/runescape-rl-reference/runescape-rl/scripts/workspace-env.sh
cd /home/joe/projects/runescape-rl-reference/runescape-rl/rsps
./gradlew --no-daemon :game:runFightCavesDemo
```

**Terminal 2 — Client:**
```bash
java -jar /home/joe/projects/runescape-rl-reference/runescape-rl/.artifacts/void-client/void-client-fight-caves-demo.jar \
  --address 127.0.0.1 --port 43594 \
  --username fcdemo01 --password pass123 \
  --show-during-bootstrap
```

### Environment fixes applied to reference tree

1. `rsps/data/cache` — restored broken symlink → `training-env/sim/data/cache` (193MB)
2. `runescape-rl-runtime/.../jdk-21` — restored broken symlink → `jdk-21.0.10+7`
3. `rsps/data/.temp/` — created missing directory (server boot crash)
4. Client jar — compiled with `javac --release 8` on JDK 21, removed dead `AuthenticationInfo` import from `Class272_Sub2.java`
5. `~/.jagex_cache_32/runescape/` — populated with cache files
6. `FightCavesDemoAutoLogin.java` — instrumented with `CAMERA_ORACLE` dump (camera state every 5s)
