# Fight Caves RL -- Project History

Consolidated from individual doc files on 2026-04-03. Reference material for future agents.

---

## 1. Project Architecture Decisions

### C-First Pivot (2026-03-24)
- Pivoted from Kotlin/JVM to pure C. Kotlin history preserved in `archive/kotlin-final` branch and `runescape-rl-nested-backup.bundle`.
- Nested `.git` from Kotlin era removed; parent repo (`claude/`, branch `v2Claude`) now fully owns `runescape-rl/`.

### Three-Component Architecture
1. **C simulation backend** (`training-env/`) -- deterministic headless Fight Caves sim. All 63 waves, 8 NPC types, combat, prayer, food/potions.
2. **Python RL binding** (`RL/`) -- ctypes wrapper, vectorized env, training pipeline.
3. **C/Raylib debug viewer** (`demo-env/`) -- 3D visual viewer rendering sim state with OSRS cache assets.

### Single Mechanics Owner Principle
All Fight Caves gameplay logic lives in `fc_core` (static lib). Both the headless training kernel and the viewer are thin shells over this shared backend. The viewer NEVER invents gameplay behavior -- it only sends actions, reads state, draws state.

### Hybrid Offline-Export + Runtime-Sim Architecture
- **Offline**: Python scripts decode OSRS cache into binary assets (TERR, SCNE, MDL2, PNG).
- **Runtime**: C sim (`fc_core`) for gameplay; Raylib for rendering.
- Rationale: cache decoding is complex (Python wins), runtime sim must be fast (C wins), rendering needs GPU (Raylib/OpenGL wins). The reference codebase (Void RSPS) is an oracle for correctness, not a codebase to fork.

### Key Reference Sources
- **PufferLib OSRS PvP** (`pufferlib/ocean/osrs_pvp/`): flat state structs, XORshift32 RNG, multi-head action space, encounter vtable, binding.c, Raylib rewind viewer.
- **PufferLib Zulrah** (`pufferlib/ocean/osrs_zulrah/`): phase/rotation state machine, data-driven phase table.
- **Kotlin archive** (`archive/kotlin-final`): 63-wave spawn tables, NPC stats, Jad telegraph, observation layout, reward features.
- **RSMod** (rev 233, at `rsmod/`): excellent reference for b237 cache format, combat formulas, tick ordering, collision flags, prayer drain. Better than Void RSPS for generic mechanics (modern OSRS, cleaner code). Does NOT have FC-specific content.
- **Void RSPS** (rev 634): authoritative for FC encounter scripting, Jad telegraph, healer behavior, wave definitions.

---

## 2. Contracts (Frozen)

### Observation: 178 floats
- `policy_obs[126]`: player state (20) + per-NPC state (8 slots x 12 features = 96) + wave/meta (10)
- `reward_features[16]`: emitted by C, NOT policy input. Python applies configurable shaping weights.
- `action_mask[36]`: 5 heads: MOVE(17) + ATTACK(9) + PRAYER(5) + EAT(3) + DRINK(2)

### Action: 5 multi-head
| Head | Dim | Notes |
|------|-----|-------|
| MOVE | 17 | 0=idle, 1-8=walk, 9-16=run |
| ATTACK | 9 | 0=none, 1-8=visible NPC by slot |
| PRAYER | 5 | 0=no change, 1=off, 2=prot magic, 3=prot missiles, 4=prot melee |
| EAT | 3 | 0=none, 1=shark, 2=combo |
| DRINK | 2 | 0=none, 1=prayer potion |

### Determinism
- XORshift32 single RNG state seeded at reset. Episode = (seed, action_sequence) fully reproducible.
- `fc_state_hash()` = FNV-1a over explicit field values (padding-independent).
- Golden traces: PR2 seed 777 hash `0xf7c9fc45`, PR5 seed 55555 hash `0x8baf02d4`, PR6 seed 66666 hash `0x7c0d19cd`.

---

## 3. Combat System Verification

### Accuracy Formula (verified against Void RSPS Hit.kt AND RSMod)
```
if attRoll > defRoll:
  hitChance = 1 - (defRoll + 2) / (2 * (attRoll + 1))
else:
  hitChance = attRoll / (2 * (defRoll + 1))
```
RSMod uses integer basis points (0-10000) -- same formula, different representation.

### NPC Stats (from Void 634 cache, corrected sizes)

| Type | Size | CL | HP | AtkSpd | MaxHit | AtkStyle |
|------|------|-----|-----|--------|--------|----------|
| Tz-Kih | 1 | 22 | 100 | 4 | 40 | melee |
| Tz-Kek | 2 | 45 | 200 | 4 | 70 | melee |
| Tz-Kek(sm) | 1 | 22 | 100 | 4 | 40 | melee |
| Tok-Xil | **3** | 90 | 400 | 4 | 130/140 | melee+range |
| Yt-MejKot | **4** | 180 | 800 | 4 | 250 | melee+heal |
| Ket-Zek | **5** | 360 | 1600 | 4 | 540/490 | melee+magic |
| TzTok-Jad | **5** | 702 | 2500 | 8 | 970/950 | multi |
| Yt-HurKot | 1 | 108 | 600 | 4 | 140 | melee+heal |

**SIZE CORRECTIONS**: Tok-Xil=3 (not 2), Yt-MejKot=4 (not 2), Ket-Zek=5 (not 3), Jad=5 (not 3). Critical for pathfinding/safespots.

### Projectile Timing
- `CLIENT_TICKS.toTicks(n) = n / 30` (floor division)
- Tok-Xil ranged: default travel (5 * distance client ticks), hit = `toTicks(travel) + 1`
- Ket-Zek magic: travel = `8 + distance * 8`, hit = `toTicks(travel) + 1`
- Jad magic: travel = `distance * 8`, hit = `toTicks(travel) + 1`
- Jad ranged: no projectile, hit delay = `toTicks(120) + 1 = 5` game ticks
- **Jad timeline**: Tick 0 = attack start, Tick 3 = hit() called, Tick 6 = damage resolves. Prayer checked at RESOLVE time (tick 6), not fire time.

### Prayer Drain (counter-based, verified against RSMod)
```
each tick:
  drainCounter += sum(active prayer drain values)  // protect prayers: drain=12 each
  resistance = 60 + (prayerBonus * 2)
  while drainCounter > resistance:
    drain 1 prayer point
    drainCounter -= resistance
```
Protection prayer blocks 100% of matching attack style in PvM.

### Consumables
- **Shark**: heals 200 HP (flat), 3-tick food delay
- **Prayer potion**: restores `7 + floor(prayerLevel * 0.25)` = 17 points (at prayer 43), 2-tick drink delay
- Food and potions use SEPARATE delay clocks -- can eat + drink on same tick
- 8 potions = 32 doses total, 20 sharks

### Hit Resolution (RSMod two-mode system)
- `queueHit`: prayer reduction applied at QUEUE time, damage capped to current HP (enables tick-eating)
- `queueImpactHit`: prayer reduction applied at IMPACT time, no damage cap
- Jad uses impact-time checking (correct). Regular NPC melee may use queue-time (needs verification).

### Player Fixed Loadout
- Stats: Atk 1, Str 1, Def 70, HP 70, Ranged 70, Prayer 43, Magic 1
- Equipment: Coif, Rune Crossbow, Black D'hide body/chaps/vambraces, Snakeskin Boots, Adamant Bolts (1000)

---

## 4. Tick Loop Ordering (verified against Void RSPS GameTick.kt AND RSMod GameCycle.kt)

**Critical**: NPCs process BEFORE players each tick.

Simplified C ordering:
1. Clear per-tick event flags
2. Process player action (from RL or human input)
3. NPC AI (hunt, target, attack decisions)
4. NPC movement (one step per tick)
5. Player movement (one step, or two if running)
6. Resolve pending hits landing this tick
7. Deaths / splits / despawns
8. Prayer drain tick
9. HP regen (every 100 ticks)
10. Wave clear check -> advance wave
11. Terminal condition check
12. Emit state / fill render entities

---

## 5. Wave System

- 63 waves, 15 spawn rotations. Rotation chosen randomly at wave 1, used for entire episode.
- 5 spawn areas: NORTH_WEST, SOUTH_WEST, SOUTH, SOUTH_EAST, CENTER (7x7 or 8x8 tile ranges)
- Player start: tile (2400, 5088), relative (32, 32) from FC base (2368, 5056)
- Tz-Kek counts as 2 in remaining counter (for split spawns)
- Wave clear = counter reaches 0, next wave starts immediately (no delay)
- Jad healers: 4 Yt-HurKot spawn at cardinal offsets when Jad HP < 50% (1250). Respawn only if Jad healed back above 50% first.
- Max concurrent NPCs: 6 (wave 58)

---

## 6. NPC Special Behaviors

- **Tz-Kih**: melee, drains 1 prayer point on hit impact (flat, unscaled)
- **Tz-Kek**: melee, splits into 2 small Tz-Kek on death at same tile
- **Tok-Xil**: dual mode -- melee at range 1, ranged at range 14
- **Yt-MejKot**: melee + heals nearby NPCs with HP < 50% of max, 100 HP per heal, ~8-tile radius
- **Ket-Zek**: magic at range 14 + melee at range 1
- **Jad**: 3-state telegraph (IDLE -> MAGIC_WINDUP/RANGED_WINDUP -> fire), 3-tick windup, 8-tick attack speed, RNG style selection
- **Yt-HurKot**: follows Jad, heals 50 HP every 4 ticks when within 5 tiles. Player attack permanently distracts (stops healing, reverts to melee AI).

### NPC AI
- Aggro mode: aggressive_intolerant, hunt range 64 tiles, re-checks every 4 ticks
- Walk: 1 tile/tick (NPCs never run in FC)
- Step: try diagonal, then horizontal, then vertical, then stop
- NPCs CAN walk through each other (no NPC-NPC collision)
- Pathfinding: RSMod A*, size-aware, recalculates when steps empty or target moved

---

## 7. Porting Findings (Void RSPS -> C Backend)

### Cache Format
- OSRS binary cache at `reference/legacy-headless-env/data/cache/` (183MB .dat2 + 37 index files)
- b237 modern OSRS from openrs2.org -- NOT the Void 634 cache for rendering
- `.dat` -> `.dat2` adapter needed (same sector-chain format, different filename)
- b237 opcode fix: opcodes 6/7 read int32 model IDs (not uint16). Reference: RuneLite commit c2e1eb3.
- VoidPS cache uses non-standard underlay/overlay floor definition opcodes (opcode 2 in underlays that standard OSRS lacks)

### What NOT to Port from Reference
- WSL<->Windows launcher mechanics
- JDK8 Windows client build
- Swing autologin/bootstrap window
- JS5/file-server protocol (we read cache directly)
- Login/ISAAC cipher (no network split)
- Jagex cache sync to Windows paths

### Terrain/Scene Assembly
- Region 9551 = base Region(37,79). 4 regions: (37,79), (37,80), (38,80), (39,80)
- 1 tile = 128 OSRS model units (`OSRS_SCALE = 1/128`)
- Client uses 512 subunits per tile for height interpolation
- Terrain height = purely visual (8-bit cache byte). Does NOT affect gameplay collision.
- Height formula: `h * -8 / 128 = h * -1/16` (NOT `h * -1/128` which makes terrain nearly flat)
- Object shapes (25 types): 0-3=walls (edge-aligned), 10-11=centrepiece (centre-aligned), 22=ground decor
- Object rotation: 4 values (0-3 = 0/90/180/270 degrees). Rotation swaps sizeX/sizeY.

### Object Export Pipeline Fixes (discovered during viewer development)
1. **Object definition parser missing opcodes 21/22** (contouredGround/delayShading as zero-byte flags). Caused 95.7% of objects silently discarded (11,598 of 12,113). After fix: 205 -> 4,382 instances.
2. **Terrain parser bug**: reading 2 bytes for overlay data instead of 1.
3. **Object recoloring (opcode 40)**: must apply per-face HSV color replacement. Object 9383 (ceiling element) was gray-green without it.
4. **Object 9383 is a ceiling element** (Y=7.49 tiles above terrain), NOT a floor tile as initially assumed.

### Player Appearance
- Server encodes 12 body parts: Head, Cape, Amulet, Weapon, Chest, Shield, Arms, Legs, Hair, Hands, Feet, Beard
- Equipment model = `itemIndex | 0x8000`, body style = `look + 0x100`
- Composite model assembly from cache is substantially more work than single NPC model extraction

---

## 8. Debug Viewer Development

### Camera Calibration (oracle-verified)
- RS-HD sine table: 16384 entries (not 2048 as RS2 classic), scale +/-16384
- Camera distance formula: `((zoom >> 3) * 3 + 600) << 2`. At zoom=1024: 3936 RS units = 30.75 tiles.
- **Default pitch ~1600/16384 = 35 degrees from horizontal** (cam_pitch = 0.6 rad). Our initial 1.1 rad (63 degrees) was far too steep.
- Viewer final: cam_pitch=0.6, cam_dist=28, fovy=32 degrees

### Alpha Blending for Bone Piles
- 2,336 bone pile instances, 101K triangles, 75-80% of faces semi-transparent (RS alpha 110=57% opacity, 170=33%)
- Raylib default `DrawMesh` does NOT call `glEnable(GL_BLEND)` -- all faces render opaque regardless of vertex alpha
- Fix: two-pass rendering (opaque geometry first with depth writes, then semi-transparent with depth mask disabled + blend enabled)
- Oracle confirmed: RS client uses same opaque-first, blend-second approach
- Blend mode: `GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA` (Raylib default matches oracle mode 1)
- Even with correct alpha, bone pile density at arena center (3-5 overlapping per tile) approaches 90-100% combined opacity. The oracle looks different because the lava crack TEXTURE provides visual contrast.

### Terrain Floor Progression
1. Kotlin `FightCavesTerrainExporter` baked PNG accepted as floor source (256x256, 4px/tile, correct server-side colour pipeline)
2. Structured per-tile binary (`fc_floor_structured.bin`) exported but produced wrong colours from raw definition RGB
3. Texture 521 UV mapping approach investigated but deferred -- requires cache index 9 packed texture decode
4. Lava-crack texture is what makes the oracle floor visually distinctive but definition colours are sufficient for current needs

### Terrain Lighting Model (from oracle TileLevel.kt)
- Light direction: (-200, -220, -200) = upper-right-front
- Base brightness: 75518 >> 9 = 147
- Per-tile intensity from height gradient: `dx = h[x+1] - h[x-1]`, `dy = h[y+1] - h[y-1]`
- `distance = sqrt(dx*dx + 262144 + dy*dy)`, then dot product with light direction

### Viewer Rebuild (PR9a series)
- First-pass viewer (Steps 1-8) accumulated stale paths, dual render modes. Archived as `viewer_archived_20260331.c`.
- Oracle instrumentation pass (PR9a-R0) established trustworthy planning foundation: 9-phase matrix, 63 probes.
- Clean shell reset (PR9a-R1): single render path, oracle-calibrated camera, no stale code.
- Floor layer (PR9a-R2a): Kotlin bake PNG on 64x64 quad. R2b (lava cracks) skipped.
- Current state: walls/cliffs from b237 cache (3,867 objects, 26 unique models, 424K triangles), NPC placeholder cubes, player cylinder, full UI panel.

---

## 9. Performance

### Training SPS (PR7 batched C path)

| Envs | Total SPS | Per-Env SPS |
|------|-----------|-------------|
| 1 | 167,956 | 167,956 |
| 16 | 1,714,272 | 107,142 |
| 64 | **3,353,676** | 52,401 |
| 256 | 4,351,384 | 16,998 |
| 1024 | **4,617,173** | 4,509 |

- PR3 -> PR7 improvement at 64 envs: 335,800 -> 3,353,676 (+899%)
- Bottleneck at high env counts: L1/L2 cache pressure (~2KB per FcEnvCtx, 1024 envs = 2MB+ working set)
- Optimal training range: 64-256 envs
- No trained agent exists yet. Training blocked until viewer completion.

### Python Binding
- Used ctypes + shared library (not PufferLib env_binding.h) because python3-dev not installed
- `FcBatchCtx` in C: contiguous array of N `FcEnvCtx`, single `fc_capi_batch_step_flat` call steps all envs
- Zero per-env Python loop in hot path

---

## 10. Key Technical Gotchas

1. **NPC sizes were wrong** in original plan. Tok-Xil=3, Yt-MejKot=4, Ket-Zek=5, Jad=5. Critical for pathfinding.
2. **Potion delay is 2 ticks**, not 3. Food is 3 ticks. Separate clocks.
3. **Prayer drain is counter-based** (accumulate -> threshold -> drain 1), not a simple per-tick rate.
4. **Camera pitch 1.1 rad was wrong**. Oracle default is 0.6 rad (35 degrees). Initial screenshots looked top-down.
5. **Object definition parser missing opcodes 21/22** caused 95.7% of scene objects to be silently discarded.
6. **Raylib DrawMesh does NOT enable GL_BLEND** by default. Vertex alpha values have no effect without explicit two-pass rendering.
7. **Object 9383 is a ceiling** (Y=7.5 tiles up), not a floor tile. Recoloring it was correct but visually irrelevant.
8. **Terrain height formula `h * -1/128` is wrong**. Correct: `h * -1/16` (8x larger). Makes difference between flat and visible relief.
9. **Terrain crop at 63 was off-by-one**. FC arena is 64 tiles wide (0..63 inclusive).
10. **VoidPS cache uses non-standard floor opcodes** that PufferLib decoders don't handle. Requires custom decoder extension.
11. **b237 model opcodes 6/7 read int32** (not uint16 as in older revisions). Missing this causes model decode failures.
12. **NPCs can walk through each other** in Fight Caves (no NPC-NPC collision). Only terrain/object collision applies.
13. **Jad prayer is checked at RESOLVE time** (tick 6 from attack start), not at fire time.
14. **Tz-Kek counts as 2** in wave remaining counter (for its split spawns).
15. **Healer respawn** requires Jad to be healed back above 50% first before re-triggering.

---

## 11. PR Delivery Log

| PR | Scope | Key Metric | Status |
|----|-------|------------|--------|
| PR1 | Core state, contracts, determinism | 368 assertions | Complete |
| PR2 | Minimal combat (player vs Tz-Kih) | 432 assertions total | Complete |
| PR3 | Python binding + smoke tests | 335,800 SPS (64 envs) | Complete |
| PR4 | Minimal Raylib viewer shell | ~435 lines | Complete |
| PR5 | All 8 NPC types, AI, specials | 504 assertions total | Complete |
| PR6 | Wave system (63 waves + Jad) | 560 assertions total | Complete |
| PR7 | Batched headless kernel | 3.35M SPS (64 envs) | Complete |
| PR8a | Asset source audit | OSRS cache found | Complete |
| PR8b | Asset export pipeline | 8 NPC models, 14 sprites | Complete |
| PR8c | C/Raylib asset integration | MDL2 loader, prayer sprites | Complete |
| PR9a-R0 | Oracle instrumentation | 63 probes, 9 phases | Complete |
| PR9a-R1 | Clean viewer shell reset | 355 lines, single render path | Complete |
| PR9a-R2a | Floor geometry | Kotlin bake PNG on quad | Complete |
| PR9a-R3+ | Walls, decor, NPCs, UI | In progress | Active |

---

## 12. Headed Oracle Reference

The Void RSPS headed client can be run on this machine for visual comparison.

**Server** (terminal 1):
```bash
source /home/joe/projects/runescape-rl-reference/runescape-rl/scripts/workspace-env.sh
cd /home/joe/projects/runescape-rl-reference/runescape-rl/rsps
./gradlew --no-daemon :game:runFightCavesDemo
```

**Client** (terminal 2):
```bash
java -jar /home/joe/projects/runescape-rl-reference/runescape-rl/.artifacts/void-client/void-client-fight-caves-demo.jar \
  --address 127.0.0.1 --port 43594 --username fcdemo01 --password pass123 --show-during-bootstrap
```

**Environment fixes applied**: restored cache symlink, JDK symlink, created `.temp/` dir, compiled client with `javac --release 8` on JDK 21, removed dead `AuthenticationInfo` import, populated `~/.jagex_cache_32/runescape/`.
