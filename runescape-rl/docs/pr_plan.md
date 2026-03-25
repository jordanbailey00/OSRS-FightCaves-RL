# Fight Caves RL — PR Plan

## Strategy

Early vertical slice to reduce waterfall risk. Get a minimal deterministic combat loop running in C, bound to Python, and visible in the viewer as fast as possible — then expand to the full Fight Caves roster, all 63 waves, and richer modes.

## Maintenance Rule

This doc is updated after every PR with: status, delivered scope, acceptance evidence, deviations, downstream implications, and the exact next PR. No important implementation decision may live only in chat.

## Reference Intake (pre-PR 1)

Before locking any contracts, these references were reviewed:

- **PufferLib OSRS PvP** (`pufferlib/pufferlib/ocean/osrs_pvp/`): Adopted flat state structs, XORshift32 RNG, multi-head action space with masking, encounter vtable API, binding.c pattern, Raylib rewind viewer, human input → same action interface.
- **PufferLib Zulrah** (`pufferlib/pufferlib/ocean/osrs_zulrah/`): Adopted phase/rotation state machine for wave progression, data-driven action sequences, shared combat math.
- **Kotlin archive** (`archive/kotlin-final`): Extracted 63-wave spawn tables (15 rotations each), 6 NPC types with stats/behaviors, Jad prayer telegraph mechanics, 134-float observation layout, 7-action space, 16 reward features, fixed equipment loadout.
- **Asset sources**: PufferLib PvP render pipeline (cache export scripts, C header arrays). OSRS binary cache available locally at `reference/legacy-headless-env/data/cache/` for asset extraction.

Findings are folded into the PR specs below. No separate reference doc.

---

## Phase 1: Vertical Slice (Contracts → Minimal Combat → Binding → Viewer)

### PR 1: Core State, Contracts, and Determinism Infrastructure — COMPLETE

**Status**: Accepted. 368 test assertions passing.

**Delivered**:
- `include/fc_types.h` — FcState, FcPlayer, FcNpc, FcPendingHit, FcRenderEntity, all enums. Flat fields, no nested pointers.
- `include/fc_contracts.h` — 178-float transport buffer (126 policy_obs + 16 reward_features + 36 action_mask). 5 action heads (MOVE 17, ATTACK 9, PRAYER 5, EAT 3, DRINK 2). NPC slot ordering: distance ascending, spawn_index tiebreak, 8-slot cap. Reward features separable from policy obs. Single source of truth.
- `include/fc_api.h` — `fc_init`, `fc_reset(seed)`, `fc_step(actions)`, `fc_destroy`, `fc_write_obs`, `fc_write_mask`, `fc_write_reward_features`, `fc_is_terminal`, `fc_terminal_code`, `fc_state_hash`, `fc_fill_render_entities`, RNG functions.
- `include/fc_debug.h` — Debug log (compile-time `FC_DEBUG` toggle, zero cost when off), `FcActionTrace` for seed+action replay.
- `src/fc_state.c`, `src/fc_rng.c` (XORshift32), `src/fc_hash.c` (FNV-1a over explicit fields, padding-independent), `src/fc_debug.c`.

**Key decisions**: Reward features emitted by C but NOT default policy input. State hash hashes explicit field values, not raw struct bytes. NPC slot overflow: 9th+ NPC simulated but invisible to policy.

---

### PR 2: Minimal Combat Slice (Player vs 1 Tz-Kih) — COMPLETE

**Status**: Accepted. 64 test assertions passing (432 total). Golden trace canary: seed 777, hash `0x89d019cd`.

**Delivered**:
- `src/fc_tick.c` — Full tick loop: clear flags → process actions (move, attack, prayer, eat, drink) → decrement timers → prayer drain → NPC AI → resolve pending hits → check terminal → tick++.
- `src/fc_combat.c` — OSRS accuracy formula, NPC melee attack roll/max hit, player ranged attack, PvM protection prayer (correct prayer blocks 100% — not PvP 60%), pending hit queue with tick delay, hit resolution for both player and NPC targets.
- `src/fc_prayer.c` — 3 protection prayers, per-tick drain based on prayer bonus, prayer potion restore.
- `src/fc_pathfinding.c` — Greedy diagonal-first walk (1 step) / run (2 steps), NPC step-toward.
- `src/fc_npc.c` — NPC stat table pre-populated for all 9 types. Spawn, AI tick (aggro, move, attack). Tz-Kih fully functional with prayer drain on hit.
- Player actions integrated into tick: directional movement, ranged attack on visible NPC slot, eat/drink with cooldown timers.
- Observation/mask/reward writers updated for live combat state.

**Deviations from original plan**:
- `fc_player.c` and `fc_obs.c`/`fc_reward.c` not created as separate files — player action processing lives in `fc_tick.c`, obs/reward in `fc_state.c`. Keeps the file count manageable while the scope is small.
- NPC stat table covers all 9 types (not just Tz-Kih), but only Tz-Kih AI is fully tested. Other types have stats; AI extensions in PR 5.
- Player ranged attack uses hardcoded NPC defence roll of 1. PR 5 must add real NPC defence stats.

**Outstanding for later PRs**:
- Real NPC defence stats (PR 5)
- Tz-Kek split-on-death, Yt-MejKot healing, Jad telegraph, healer distraction (PR 5)
- Wave table and wave transitions (PR 6)

---

### PR 3: Python Binding and Smoke Tests — COMPLETE

**Status**: Accepted. All smoke/vectorized/SPS tests passing. SPS baseline: 335,800 (64 envs).

**Delivered**:
- `include/fc_capi.h` + `src/fc_capi.c` — Plain C shared library API (`libfc_capi.so`). create/destroy/reset/step/getters, batch_step, contract constant exports (obs_size, policy_obs_size, reward_features, mask_size, head dims). No Python.h dependency.
- `RL/src/fc_env.py` — ctypes wrapper: `FightCavesVecEnv` with N independent C env contexts, `split_obs()` for (policy_obs, reward_features, action_mask) separation. Constants read from C at import time.
- Smoke test: 4 envs × 100 steps — shapes, ranges, mask values verified.
- Vectorized test: 16 envs with different seeds diverge correctly (different player HP after 50 ticks).
- SPS benchmark: **335,800 SPS** (64 envs, 26,235 ticks, 5.0s). This is the Python↔C ctypes round-trip baseline.

**Deviation from plan**:
- Used **ctypes + shared library** instead of PufferLib `env_binding.h` C extension. Reason: python3-dev (Python.h) not installed and no sudo access. The `binding.c` pattern was prototyped but could not compile.
- PufferLib `env_binding.h` integration is deferred as an optional future optimization. The ctypes path is functionally equivalent with slightly higher per-step Python overhead.
- No separate `RL/tests/test_smoke.py` — tests are in `fc_env.py` directly (run via `python3 RL/src/fc_env.py`).

**Downstream implications**:
- PR 8 (training pipeline) will use `FightCavesVecEnv` directly or adapt it to PufferLib's interface. The ctypes path works but the per-env Python loop may become a bottleneck at high env counts. If so, the batch_step C function or PufferLib binding.c path can be added.
- SPS is expected to improve significantly when the per-env ctypes loop is replaced with a batched C call (fc_capi_batch_step already exists but is not yet wired into the Python vectorized env).

---

### PR 4: Minimal Viewer Shell — COMPLETE

**Status**: Accepted. Raylib viewer implemented as single `demo-env/src/viewer.c` (~435 lines). Builds and renders fc_core state with placeholder art.

**Delivered**:
- `demo-env/src/viewer.c` — Combined render+HUD+controls in one file.
- Top-down tile grid: walkable tiles = dark gray, obstacles = darker, grid lines. Arena grid rendered via `state.walkable[][]` directly (only non-simulation field the viewer reads).
- Entity rendering via `fc_fill_render_entities` exclusively — no direct FcState struct access for simulation data.
- Player: blue filled circle with white outline. Prayer indicator text (M/R/P) above head.
- NPCs: type-colored rectangles with white outline. Per-type color mapping for all 8 NPC types. Jad telegraph indicator text (MAG/RNG). Dead NPCs rendered translucent (alpha=80).
- HP bars above all living entities (green fill on red background).
- Hitsplat: damage number rendered in red at entity center when `damage_taken_this_tick > 0`.
- HUD overlay (toggle H): HP, prayer, active prayer name, sharks/doses, ammo, wave/kills/remaining, tick/episode, position, timers, cumulative damage, pause/speed indicator.
- Debug overlay (toggle D): state hash hex, seed/rotation, action head values array, terminal/damage-per-tick, zoom level.
- Controls help bar at bottom of screen.
- Controls: Space (pause/resume), Right (single-step while paused), Up/Down (tick speed 1–30 tps), R (reset episode with random seed), H (toggle HUD), D (toggle debug), Q/Esc (quit), scroll wheel (zoom 0.3x–5.0x), middle-mouse drag (camera pan).
- Demo mode: random actions each tick (with occasional forced idle). Auto-pause on terminal.
- `demo-env/CMakeLists.txt`: links fc_core + Raylib 5.5 pre-built static lib + X11 + OpenGL.

**Acceptance evidence**: Builds with cmake, window opens, renders tile grid with player circle and NPC rectangle, entities move and fight under random actions, HP bars update, HUD displays all stats, debug overlay shows state hash, zoom/pan work, pause/step/reset work, auto-pauses on terminal.

**Deviation from plan**: Merged viewer_main.c + viewer_render.c + viewer_hud.c into single viewer.c. Appropriate for current scope; can be split if it grows beyond ~600 lines.

**Downstream implications**:
- **PR 9 (full rendering)**: viewer.c already has per-type NPC colors for all 8 types. PR 9 will add wave transition display, improved hitsplat animation, and possibly sprites. The single-file structure may need splitting if PR 9 pushes past ~600 lines.
- **PR 10 (human play)**: viewer currently uses random actions in demo mode. Human input will replace the random action block in the main loop with keyboard/mouse → action buffer translation. The viewer's control architecture (input → actions[] → fc_step) is already compatible.
- **PR 12 (replay/debug)**: viewer currently has forward-only stepping. Replay requires file I/O loading and snapshot rewind. The debug overlay already shows state hash (useful for determinism verification). Rewind will need per-tick state snapshots stored alongside the main loop.
- **Viewer reads `state.walkable[][]` directly** for arena rendering (not via fill_render_entities). This is acceptable because walkability is a static arena property, not simulation state. If the arena becomes dynamic, this should be moved to the render entity interface.

---

### Checkpoint: Git Ownership Normalization — COMPLETE

**Status**: Accepted. Parent repo (`claude/`) now fully owns `runescape-rl/`.

**Problem**: `runescape-rl/` contained a nested `.git` from the Kotlin era (on branch `vClaude`, 32 commits, remote `jordanbailey00/runescape-rl`). Parent repo on `c-rewrite` branch was tracking the directory as a plain directory but could not commit Kotlin deletions or C additions correctly.

**Actions taken**:
1. Inspected nested repo: branches `master` + `vClaude`, 32 commits, no submodule config.
2. Exported full backup bundle: `runescape-rl-nested-backup.bundle` (8.2 MB) saved at `/home/joe/projects/runescape-rl/` (outside the parent repo). Verified with `git bundle verify` — complete history, 4 refs.
3. Removed `runescape-rl/.git` from the parent repo worktree.
4. Staged all changes in parent repo: 11,559 Kotlin file deletions + C rewrite additions (training-env/, demo-env/, RL/, CMakeLists.txt, README.md, .gitignore).
5. Created checkpoint commit on `c-rewrite` branch: PR1–PR4 C rewrite baseline.

**Recovery**: To restore the nested repo history: `git clone /home/joe/projects/runescape-rl/runescape-rl-nested-backup.bundle runescape-rl-history`.

---

## Phase 2: Full Fight Caves

### PR 5: All NPC Types — AI and Special Mechanics — COMPLETE

**Status**: Accepted. 72 test assertions passing (504 total with PR1+PR2). PR5 golden trace: seed 55555, 30 ticks, hash `0x8baf02d4`.

**Delivered**:
- `FcNpcStats` extended with `def_level`, `def_bonus`, `heal_amount`, `heal_interval`, `jad_ranged_max_hit`. Replaces old `str_level`/`str_bonus` (were unused).
- Real NPC defence stats for all 8 types. Player ranged attack now calls `fc_npc_def_roll(def_level, def_bonus)` instead of hardcoded `1`. Accuracy varies meaningfully: >80% vs Tz-Kih (def 22), <30% vs Ket-Zek (def 300).
- `fc_magic_hit_delay(distance)` added: `1 + floor((1+dist)/3)`.
- **Tz-Kek split-on-death**: `fc_npc_tz_kek_split()` spawns 2 `NPC_TZ_KEK_SM` at death position. Triggered in `fc_resolve_npc_pending_hits` on death. Dead Tz-Kek's slot is reused by a split spawn (active=0 → new spawn).
- **Tok-Xil ranged**: Uses generic attack with `ATTACK_RANGED`, range 6, projectile delay via `fc_ranged_hit_delay`. Protect range blocks 100%.
- **Yt-MejKot healing**: `yt_mejkot_heal_tick()` heals nearby NPCs (Chebyshev distance ≤3) for 10 HP every 8 ticks. Does not self-heal. Caps at max_hp. Heals in addition to normal melee attack behavior.
- **Ket-Zek magic**: Uses generic attack with `ATTACK_MAGIC`, range 8, delay via `fc_magic_hit_delay`. Protect magic blocks 100%.
- **Jad telegraph**: 3-state machine (IDLE → MAGIC_WINDUP/RANGED_WINDUP → fire). Attack style chosen by RNG on each cycle. Wind-up holds for 3 ticks, visible in obs as `FC_NPC_JAD_TELEGRAPH`. Hit queued with style-appropriate delay. Magic max 970, ranged max 950 tenths.
- **Yt-HurKot**: Walks toward Jad and heals 10 HP every 4 ticks (if adjacent). Player attack sets `healer_distracted=1`, which permanently stops Jad-healing behavior. Distracted healers revert to normal melee AI.
- `npc_generic_attack()` factored out for melee/ranged/magic NPC types (non-Jad).
- `fc_npc_tick()` now dispatches by type: Jad uses telegraph state machine, Yt-HurKot prioritizes healing, Yt-MejKot heals then attacks, others use generic.

**Deviations from plan**:
- NPC stat table struct changed: removed unused `str_level`/`str_bonus`, added `def_level`/`def_bonus`/`heal_amount`/`heal_interval`/`jad_ranged_max_hit`. This changed the golden trace hash from `0x89d019cd` to `0xf7c9fc45` for the PR2 seed-777 scenario (self-consistent, just different absolute value).
- Yt-HurKot healer spawning during Jad wave is NOT implemented in this PR — that's a wave-system feature (PR 6). This PR only implements the healer's AI behavior assuming it's already spawned.

**Tests added** (`test_pr5.c`, 72 assertions):
- `test_npc_defence_stats`: All NPC types have real def levels. fc_npc_def_roll produces non-trivial values. Player accuracy varies correctly by target defence.
- `test_magic_hit_delay`: Formula correctness, distinct from ranged delay.
- `test_tz_kek_split`: Death triggers 2 NPC_TZ_KEK_SM spawns. npcs_remaining updated. Split NPCs have correct stats.
- `test_tok_xil_ranged`: Ranged attack from distance, projectile delay, protect range blocks.
- `test_yt_mejkot_heal`: Heals nearby damaged NPC, caps at max_hp.
- `test_ket_zek_magic`: Magic attack from distance, protect magic blocks.
- `test_jad_telegraph`: Enters windup state, correct prayer blocks 100%.
- `test_jad_wrong_prayer`: Wrong prayer → damage.
- `test_yt_hurkot_heals_jad`: Healer walks to Jad and heals.
- `test_yt_hurkot_distraction`: Player attack distracts healer, stops Jad healing.
- `test_npc_attack_styles`: All 8 types have correct attack style/size/flags.
- `test_jad_stats`: Jad max hits, range, defence correct.
- `test_multi_npc_determinism`: 100 ticks with mixed NPC types, same seed = same hashes.
- `test_pr5_golden_trace`: Record/replay 30-tick multi-type combat. Self-consistent.
- `test_pr2_regression`: PR2 seed-777 trace still self-consistent.

**Downstream implications**:
- **PR 6 (waves)**: All NPC types are now functional. Wave system can spawn any type and combat works. Yt-HurKot auto-spawn at Jad low HP must be implemented in PR 6's wave logic (not fc_npc.c). Tz-Kek split is automatic on death — wave system must account for split NPCs in wave-clear detection.
- **PR 8 (training)**: Real NPC defence makes player accuracy vary significantly by target. This affects reward shaping — damage_dealt reward feature now correlates with target difficulty. May need to normalize reward by target type or use the existing per-tick normalization.
- **Golden trace**: PR2 hash changed from `0x89d019cd` to `0xf7c9fc45` due to stat table restructure. New baseline going forward.
- **No contract changes**: FC_OBS_SIZE, FC_POLICY_OBS_SIZE, FC_ACTION_MASK_SIZE, FC_NUM_ACTION_HEADS all unchanged. No Python-side changes needed.

---

### PR 6: Wave System (All 63 Waves + Jad) — COMPLETE

**Status**: Accepted. 57 test assertions passing (560 total with PR1+PR2+PR5). PR6 golden trace: seed 66666, 20 ticks, hash `0x7c0d19cd`.

**Delivered**:
- `include/fc_wave.h` — Wave system API: `fc_wave_get`, `fc_wave_spawn_dir`, `fc_wave_spawn`, `fc_wave_check_advance`, `fc_spawn_position`. Jad healer threshold constant `FC_JAD_HEALER_THRESHOLD_FRAC` (50% HP), `FC_JAD_NUM_HEALERS` (4).
- `src/fc_wave.c` (~1250 lines) — Complete 63-wave spawn table sourced from Kotlin archive TOML (`tzhaar_fight_cave_waves.toml`). 15 spawn rotations per wave (spawn direction per NPC per rotation). Wave → arena coordinate mapping for 5 spawn directions (SOUTH, SOUTH_WEST, NORTH_WEST, SOUTH_EAST, CENTER). `fc_wave_spawn` allocates NPCs into free slots. `fc_wave_check_advance` handles wave-clear → next-wave transition + TERMINAL_CAVE_COMPLETE.
- `FcWaveEntry` simplified: `int npc_types[6]` + `int num_spawns` (replaced `FcWaveSpawn` sub-struct).
- `FcState.jad_healers_spawned` flag added to prevent re-spawning healers.
- `fc_reset` now auto-spawns wave 1 NPCs via `fc_wave_spawn(state, 1)`.
- `check_terminal` in `fc_tick.c` now calls `fc_wave_check_advance` for wave-clear detection and `check_jad_healers` for Yt-HurKot auto-spawn at Jad HP < 50%.
- Jad healer spawn: 4 Yt-HurKot spawned at cardinal offsets from Jad position when HP drops below threshold on wave 63.
- Wave-clear correctly handles Tz-Kek splits: split spawns increment `npcs_remaining`, so wave doesn't clear until splits are also killed.
- Spawn rotations: `rotation_id` selected at `fc_reset` via RNG, used for all waves in the episode.
- Full episode path: reset → wave 1 spawn → combat → wave-clear → wave 2 spawn → ... → wave 63 (Jad) → Jad HP < 50% → 4 healers spawn → kill all → TERMINAL_CAVE_COMPLETE.

**Deviations from plan**:
- Persistent replay file I/O NOT added (deferred to PR 7/12 as reconciled in docs patch). Determinism verified via in-memory `FcActionTrace` record/replay.
- Wave table generated from TOML via Python script, embedded as C `static const` arrays (not loaded at runtime).
- PR2 test suite adjusted from 64 to 63 assertions: `test_npc_death` simplified (removed `npcs_remaining==0` assertion which is now unreliable due to wave auto-advance).

**Tests added** (`test_pr6.c`, 57 assertions):
- `test_wave_table`: Wave composition for key waves (1,3,7,15,31,63). Spawn rotation dir lookup. Spawn position coordinate mapping. Wave 58 has 6 NPCs (max density).
- `test_wave1_reset`: Reset auto-spawns wave 1 Tz-Kih.
- `test_wave_progression`: Kill wave → advances to next wave with correct NPC types.
- `test_tz_kek_split_wave_clear`: Tz-Kek death triggers split, wave doesn't clear until splits killed.
- `test_reach_jad`: Fast-kill through 62 waves, verify wave 63 spawns Jad with correct stats.
- `test_jad_healer_spawn`: Reduce Jad HP below 50%, verify 4 Yt-HurKot spawn, flag set.
- `test_cave_complete`: Kill all 63 waves → TERMINAL_CAVE_COMPLETE.
- `test_rotation_variety`: Rotation IDs in valid range, different rotations produce different spawn dirs.
- `test_late_wave_npcs`: Verify NPC composition for waves 12, 43, 62.
- `test_multi_wave_determinism`: 10-wave run with same seed produces identical state hashes.
- `test_pr6_golden_trace`: Record/replay 5-wave scripted combat. Self-consistent.

**Downstream implications**:
- **PR 7 (batched kernel)**: Full episode simulation now works end-to-end. Batched kernel can run complete Fight Caves episodes. Replay file I/O can be added here.
- **PR 8 (training)**: Environment is now feature-complete for training. All 63 waves + Jad + healers + all NPC types. The Python side needs no changes — `FightCavesVecEnv` already handles auto-reset on terminal.
- **PR 9 (viewer)**: Viewer already updated in PR6 to use the wave system (`reset_episode` no longer hardcodes a Tz-Kih). PR 9 adds wave transition display, improved hitsplats, and possibly sprites.
- **Contract**: `FcWaveEntry` struct changed (removed `FcWaveSpawn` sub-struct, replaced with flat `int npc_types[]`). No Python-visible contract changes (FC_OBS_SIZE etc. unchanged).
- **Golden trace baseline**: PR2 hash `0xf7c9fc45` still valid (wave system doesn't affect single-NPC combat). PR5 hash `0x8baf02d4` still valid. PR6 hash `0x7c0d19cd`.

**Exact next PR**: PR 7 — Batched Headless Kernel and Benchmarks.

---

### PR 7: Batched Headless Kernel and Benchmarks — COMPLETE

**Status**: Accepted. **3,353,676 SPS @ 64 envs** (up from 335,800 — **10x improvement**). Peak 4,617,173 SPS @ 1024 envs. All Python + C tests passing (560 C assertions + Python smoke/vectorized/batch-independence tests).

**Delivered**:
- `fc_capi.c` extended with `FcBatchCtx`: contiguous array of N `FcEnvCtx` structs allocated in one `calloc`. Functions: `fc_capi_batch_create`, `fc_capi_batch_destroy`, `fc_capi_batch_reset`, `fc_capi_batch_step_flat`, `fc_capi_batch_get_obs`.
- `fc_capi_batch_step_flat`: takes flat numpy-compatible arrays (int32 actions, float32 obs/rewards, int32 terminals). One C call steps all envs and copies results — eliminates per-env ctypes overhead.
- `fc_capi_reset` cleaned: removed stale manual Tz-Kih spawn (wave system handles it since PR6).
- `RL/src/fc_env.py` rewritten to use batched C path: `FightCavesVecEnv.__init__` creates `FcBatchCtx`, `step()` calls `fc_capi_batch_step_flat` with flat numpy arrays. Zero per-env Python loop in the hot path.
- `test_batch_independence`: verifies batch envs produce identical results to individually-run single envs (same seed → same obs after 20 steps).
- `benchmark_scaling`: measures SPS at 1, 16, 64, 256, 1024 envs.
- `docs/training_performance.md` updated with full benchmark table and regression analysis.

**SPS Benchmark Results**:

| Envs | Total SPS | Per-Env SPS |
|------|-----------|-------------|
| 1 | 167,956 | 167,956 |
| 16 | 1,714,272 | 107,142 |
| 64 | 3,353,676 | 52,401 |
| 256 | 4,351,384 | 16,998 |
| 1024 | 4,617,173 | 4,509 |

**PR3→PR7 comparison @ 64 envs**: 335,800 → 3,353,676 (+899%).

**Deviations from plan**:
- No separate `fc_batch_runner.c` or `fc_headless_kernel.c` files. The batch logic lives in `fc_capi.c` alongside the single-env API. `FcBatchCtx` wraps a contiguous `FcEnvCtx[]` array — simple and sufficient.
- Replay file I/O not added (remains deferred to PR 12 as stated in docs patch).

**Downstream implications**:
- **PR 8 (training)**: Environment is now both feature-complete (63 waves) and fast (3.3M+ SPS @ 64 envs). `FightCavesVecEnv` API is unchanged (same `step()` / `reset()` / `split_obs()` interface). Training pipeline can use it directly.
- **Scaling**: Per-env SPS drops at high env counts (cache pressure). For training, 64-256 envs is the sweet spot. Threading or SIMD could push further but are not needed for initial training.

**Exact next PR**: PR 8 — Viewer Asset Pipeline + Full Fight Caves Rendering.

---

## Phase 3: Viewer Completion (pre-training requirement)

Viewer must look and play similar to actual RuneScape Fight Caves before training begins. Asset-backed rendering, human-playable input, and debug usability come first.

### PR 8: Viewer Asset Pipeline + Full Fight Caves Rendering — REOPENED

**Status**: Reopened. Initial pass delivered OSRS-style color palette, GUI panel, hit splats, but arena/entities/UI are still procedural primitives. Procedural placeholders do not meet the required viewer quality bar. PR8 is split into PR8a/PR8b/PR8c.

**Reference audit findings** (folded into implementation):
- **PufferLib OSRS PvP viewer** (`osrs_pvp_render.h`, 3714 lines): Adopted 2D Camera2D rendering pattern, OSRS color palette (`osrs_pvp_gui.h`), hit splat animation system (4 slots, float-up-and-fade), right-side GUI panel layout, text shadow rendering (`DrawText` at +1,+1 black, then color on top), camera zoom (×1.15 per scroll), camera follow with smooth lerp (0.08 factor).
- **PufferLib GUI system** (`osrs_pvp_gui.h`, 1700 lines): Adopted panel color constants (GUI_BG_DARK, GUI_BG_SLOT, etc.), inventory slot visual style, HP bar green-on-red rendering, prayer display style. Simplified from 7-tab system to single-panel with sections.
- **PufferLib Inferno data** (`data/npc_models.h`): Contains NPC model/anim IDs for TzHaar-variant NPCs (Jal-* prefix). Fight Caves NPCs (Tz-* prefix) were exported in PR8b from the local OSRS cache.
- **Kotlin demo-env**: Wave data reused (PR6). Viewer layout was text-only terminal, no visual patterns adopted.

**Delivered**:
- `demo-env/src/viewer.c` (~640 lines): Complete rewrite from placeholder grid to Fight Caves presentation.
- **Arena**: Procedural Fight Caves terrain — sandy brown checkerboard floor (TzHaar cave palette), animated lava border around walkable area, dark rock walls for obstacles. Visually reads as a cave arena, not a grid.
- **Entities**: NPC type-specific colors with TzHaar-inspired palette (rusty orange Tz-Kih, brown Tz-Kek, green Tok-Xil, blue-teal Yt-MejKot, purple Ket-Zek, gold Jad, yellow Yt-HurKot). Rounded rectangles for NPCs. Blue circle for player. NPC names displayed above in OSRS yellow text.
- **Jad telegraph**: "MAGE!" / "RANGE!" text above Jad in appropriate prayer color.
- **Prayer overhead**: Player's active prayer displayed above head (Melee/Range/Mage in red/green/cyan).
- **HP bars**: OSRS-style green-on-red bars with dark border above all living entities.
- **Hitsplats**: OSRS-style red circle (damage) or blue circle (block) with white damage number, float-up animation, 12-tick duration.
- **GUI panel** (right side, 220px): HP bar (green/red), Prayer bar (blue), active prayer name, inventory section (shark/prayer pot/bolt counts with colored slot icons), wave info, combat stats, speed/pause indicator. OSRS GUI color palette from PufferLib.
- **Header bar**: Wave counter, episode/seed/rotation info, HP/prayer quick display.
- **Camera**: Raylib Camera2D with zoom (scroll, 0.3x–5.0x), pan (middle-mouse drag), smooth follow on player (lerp 0.08).
- **Controls**: Space/Right/Up/Down/R/D/Q/Scroll/Mid-drag (unchanged functionality, H key removed — HUD is always-on in panel).
- **Text rendering**: OSRS shadow text throughout (black at +1,+1, color on top).

**Remaining placeholders** (documented):
- **Entity shapes are colored primitives** (circles/rounded rects) in the PR8 initial pass. PR8b exported actual 3D NPC models and prayer sprites from the local OSRS cache. PR8c will integrate these into the viewer.
- **Arena terrain is procedural** (Raylib drawing with OSRS-inspired colors), not cache-exported `.terrain` binary. Same reason: no cache for `export_terrain.py`.
- **No sprite assets** for prayer icons, inventory items, or equipment. These are rendered as colored rectangles with text labels. Upgrading requires running `export_sprites.py` against the cache.
- **No projectile flight visualization** (arrows, magic projectiles). Hit splats show damage but no in-flight projectile rendering.
- **No death animation** — dead NPCs simply disappear (active=0).

**Downstream implications**:
- **PR 9 (human play)**: Viewer now has proper entity/tile rendering with camera. Click-to-move needs screen→world coordinate conversion via Camera2D inverse transform. Click-to-attack needs entity hit detection (check if click position overlaps entity screen bounds).
- **PR 11 (training)**: No training-side changes needed. Viewer is a separate process.
- **Asset upgrade path**: If OSRS cache becomes available, run PufferLib export scripts to generate `.terrain`/`.models`/`.sprites` binaries, then integrate PufferLib `osrs_pvp_models.h` loader and `osrs_pvp_terrain.h` loader. The viewer architecture supports this — just swap procedural drawing for model rendering.

**Exact next PR**: PR 8a — RSPS/Kotlin Fight Caves Render + Asset Audit.

---

### PR 8a: RSPS/Kotlin Fight Caves Render + Asset Audit — COMPLETE

**Status**: Accepted. All asset source locations identified. OSRS binary cache found on disk. Export plan defined.

**Key finding**: A full OSRS binary cache exists at `runescape-rl/reference/legacy-headless-env/data/cache/` (183MB `.dat2` + 37 index files). This is a modern OSRS format cache readable by the PufferLib export scripts (with a `.dat` → `.dat2` adapter). This unblocks real asset extraction.

**Source locations identified**:

| Asset Class | Source | Key Data |
|-------------|--------|----------|
| **Arena/terrain** | OSRS cache regions 37-39,79-80 | World coords (2368,5056)→(2559,5183). Spawn areas defined in `tzhaar_fight_cave.areas.toml` |
| **NPC definitions** | `tzhaar_fight_cave.npcs.toml` | IDs: Tz-Kih=2734, Tz-Kek=2736, Tok-Xil=2739, Yt-MejKot=2741, Ket-Zek=2743, Jad=2745, Yt-HurKot=2746. Stats, size, height all defined |
| **NPC models** | OSRS cache index 7 (models) | Model IDs decoded from NPCDefinitionFull.modelIds via cache index 2 group 9. Animation IDs: 9230-9300 range (from `tzhaar_fight_cave.anims.toml`) |
| **NPC animations** | OSRS cache index 0 (skeletons/frames) | Tz-Kih attack=9232, Jad magic=9300, Jad range=9276, etc. |
| **GFX/projectiles** | `tzhaar_fight_cave.gfx.toml` | Heal=444, Ket-Zek=1622-1624, Tok-Xil=1616, Jad=1625-1627/451 |
| **Player equipment** | `FastFightCavesShared.kt` | Fixed loadout: rune crossbow, black d'hide, snakeskin boots, adamant bolts |
| **Sprites** | OSRS cache index 8 (sprites) | Prayer icons (IDs from `osrs_pvp_gui.h`), item sprites, hitsplat sprites, UI elements |
| **Wave data** | `tzhaar_fight_cave_waves.toml` | Already used in PR6 |

**Export plan**:

| Asset | Export Tool | Output Format | Destination |
|-------|-----------|---------------|-------------|
| FC terrain mesh | `export_terrain.py --cache CACHE --regions "37,79 37,80 38,79 38,80 39,79 39,80"` | `.terrain` binary (TERR) | `demo-env/assets/fight_caves.terrain` |
| FC NPC models | `export_models.py --cache CACHE` (adapted for NPC model IDs) | `.models` binary (MDL2) | `demo-env/assets/fight_caves_npcs.models` |
| FC NPC animations | `export_animations.py --cache CACHE` (FC anim IDs) | `.anims` binary (ANIM) | `demo-env/assets/fight_caves_npcs.anims` |
| FC objects (walls) | `export_objects.py --cache CACHE` (FC region objects) | `.objects` binary | `demo-env/assets/fight_caves.objects` |
| Prayer/item sprites | `export_sprites.py --cache CACHE` | PNG files | `demo-env/assets/sprites/` |
| NPC model ID table | Python script to decode NPCDefinitionFull from cache | C header | `demo-env/assets/fc_npc_models.h` |

**Blockers**:
- PufferLib `CacheReader` expects `.dat` (317 format). Our cache is `.dat2` (modern OSRS). Same sector-chain format but different filename. **Fix**: symlink `main_file_cache.dat` → `main_file_cache.dat2`, or patch `CacheReader.__init__` to try `.dat2` fallback. Verified the data format is compatible — successfully read reference tables from `.dat2` using the same sector logic.
- Map regions may be XTEA-encrypted. Fight Caves is an instanced area — may or may not need keys. Need to test. If encrypted, keys are publicly available from OpenRS2 archives.
- NPC model IDs are not in the TOML data — they're in the binary cache NPCDefinition entries. Need to decode NPC defs from cache index 2 to get model IDs for each FC NPC ID.

**Gap list**:
- **Immediately exportable**: Terrain mesh, object mesh, sprites (once `.dat2` adapter works)
- **Requires NPC def decode first**: NPC models + animations (need model IDs from cache)
- **Requires xtea testing**: Map terrain (may be encrypted for instanced regions)
- **Approximation needed**: If FC region terrain is encrypted and xteas unavailable, generate approximate terrain from walkability data + OSRS color palette (lava, rock, sand). This is a documented fallback, not a first choice.

---

### PR 8b: Fight Caves Asset Export Pipeline — COMPLETE

**Status**: Accepted. NPC models (8 NPCs, 686KB MDL2 binary) and UI sprites (14 PNGs) exported from OSRS cache. Terrain export blocked by VoidPS cache format incompatibility.

**Delivered**:
- `demo-env/scripts/dat2_cache_reader.py`: Adapter that reads standard OSRS `.dat2/.idx*` cache files via the PufferLib `ModernCacheReader` API (read_container, read_index_manifest, read_group, read_config_entry). Bridges the format gap.
- `demo-env/scripts/export_fc_npcs.py`: Decodes VoidPS NPC definitions (opcode stream) from cache index 18 to extract model IDs, then reads 3D models from cache index 7, and writes PufferLib MDL2 binary format. Generates `fc_npc_models.h` C header with model/animation mappings.
- `demo-env/scripts/export_fc_sprites.py`: Extracts prayer icons (on/off states for protect melee/missiles/magic), equipment slot backgrounds, tab icons, and special bar from cache index 8 using PufferLib sprite decoder.
- `demo-env/assets/fight_caves_npcs.models`: MDL2 binary with 8 FC NPC 3D models (Tz-Kih=34252, Tz-Kek=34251, Tz-Kek-Sm=34250, Tok-Xil=34133, Yt-MejKot=34135, Ket-Zek=34137, TzTok-Jad=34131, Yt-HurKot=34136).
- `demo-env/assets/fc_npc_models.h`: C header mapping NPC IDs to model IDs + animation IDs.
- `demo-env/assets/sprites/`: 14 PNG files (6 prayer icons, 4 equipment slots, 3 tab icons, 1 special bar).

**Terrain blocker**: VoidPS cache uses non-standard underlay/overlay floor definition opcodes (opcode 2 in underlay entries, which standard OSRS doesn't have). PufferLib `_decode_underlay_entry` only handles opcode 1 (RGB) and 0 (terminator). Fix options: (a) extend decoder for VoidPS opcodes, (b) use procedural terrain with OSRS color palette as documented fallback. Terrain export is deferred to PR8c where it can be addressed alongside integration.

**Export commands** (reproducible):
```bash
# NPC models + C header
python3 demo-env/scripts/export_fc_npcs.py

# UI sprites
python3 demo-env/scripts/export_fc_sprites.py
```
Both scripts require the OSRS cache at `runescape-rl/reference/legacy-headless-env/data/cache/` and the `.dat` → `.dat2` symlink.

**Exact next PR**: PR 8c — C/Raylib Integration of Exported Fight Caves Assets.

---

### PR 8c: C/Raylib Integration of Exported Fight Caves Assets — COMPLETE

**Status**: Accepted. NPC 3D models rendered via MDL2 loader. Prayer icon sprites rendered as Raylib textures. 3D/2D mode toggle. All 560 C tests passing.

**Delivered**:
- `demo-env/src/fc_model_loader.h`: MDL2 binary loader adapted from PufferLib `osrs_pvp_models.h`. Loads `fight_caves_npcs.models`, creates Raylib Mesh+Model per NPC, provides `fc_model_cache_get(cache, model_id)` lookup.
- `demo-env/src/viewer.c` rewritten (~550 lines): dual rendering mode (T key toggles):
  - **3D mode** (default): Camera3D with orbital controls (right-drag orbit, scroll zoom). NPC 3D models from cache-exported MDL2 binary rendered via `DrawModelEx`. Player as blue cylinder. Ground plane as colored cubes matching TzHaar cave palette. 3D HP bars as billboard cubes.
  - **2D mode**: Camera2D with zoom/pan/follow (unchanged from PR8 initial pass). Rounded rect NPCs with type colors, prayer icon sprites above player, hit splat animations.
- NPC type → model ID mapping: static array `NPC_TYPE_TO_MODEL_ID[NPC_TYPE_COUNT]` maps fc_core NPC type enum to OSRS cache model IDs. Model lookup via `fc_model_cache_get`.
- Prayer icon sprites: `protect_melee_on.png`, `protect_missiles_on.png`, `protect_magic_on.png` loaded as Raylib textures. Rendered above player head (2D mode) and in GUI prayer section. Falls back to text labels if sprites fail to load.
- GUI panel: real prayer icon sprites next to prayer name in active prayer section.
- Asset path resolution: tries `demo-env/assets/` then `../demo-env/assets/` for portability (build dir vs project root).

**Remaining documented fallbacks**:
- **Terrain**: Procedural (sandy brown checkerboard + lava border + rock walls). Blocker: VoidPS cache uses non-standard floor definition opcodes that PufferLib's `_decode_underlay_entry` doesn't handle. Requires extending the decoder for VoidPS-specific opcodes (opcode 2 in underlays). The procedural terrain is visually acceptable (TzHaar cave palette) but not cache-derived.
- **Player model**: Rendered as blue cylinder (3D) or blue circle (2D). Exporting a player composite model requires assembling body parts + equipment models from cache, which is substantially more work than NPC single-model extraction.
- **Item sprites**: Inventory slots use colored rectangles, not cache-exported item sprites. The sprite exporter can extract these but they weren't prioritized over NPC models.
- **Hitsplats in 3D mode**: Only in 2D mode currently. 3D mode shows HP bars but not floating damage numbers.
- **No animations**: Models are static (idle pose). Integrating the animation system requires the PufferLib `osrs_pvp_anim.h` runtime + exported `.anims` binary.

**Exact next PR**: PR 9a — Viewer Parity (terrain, player, tabbed UI, item sprites).

---

### PR 9a: Viewer Parity — Terrain, Player Composite, Tabbed UI, Item Sprites — IN PROGRESS

**Delivered so far**:
- `demo-env/scripts/export_fc_terrain.py`: Terrain exporter with VoidPS floor definition opcode handling (underlays: opcodes 1-5, overlays: opcodes 1-14). Successfully decoded 159 underlays + 235 overlays from cache.
- `demo-env/assets/fight_caves.terrain`: Exported TERR binary (95,256 vertices, 31,752 triangles, 1.5MB). 4 FC map regions (37,79), (37,80), (38,80), (39,80).
- `demo-env/src/fc_terrain_loader.h`: TERR binary loader, creates Raylib Mesh+Model.
- Terrain mesh integrated into 3D viewer mode — `DrawModel` replaces per-tile cubes when terrain is loaded.
- **Tabbed UI panel**: 3 tabs (Inventory, Prayer, Combat) with tab switching and exported tab icon sprites.
- **Inventory tab**: Shark slots (20) and prayer potion slots (8) with colored icons, dose counters. Clicking a shark slot queues `FC_EAT_SHARK`. Clicking a potion slot queues `FC_DRINK_PRAYER_POT`. Actions routed through canonical action heads.
- **Prayer tab**: 3 protection prayers with exported on/off icon sprites, active highlight, clickable to toggle. Click queues the appropriate `FC_PRAYER_*` action through canonical interface.
- **Combat tab**: Damage dealt/taken, food/potion usage, position, cooldown timers.
- Off-state prayer sprites (`protect_*_off.png`) loaded alongside on-state versions.

**Not yet done**:
- Player model (still blue cylinder). Requires either PufferLib PlayerComposite assembly or a pre-composed fixed-loadout model export. Documented blocker.
- Item sprites for inventory (using colored rectangles with letter labels, not cache-rendered item sprites). Requires ExportItemSprites.java (needs JVM) or pre-rendering from 3D item models.

---

### PR 9a: Viewer Parity — Terrain, Player Composite, Tabbed UI, Item Sprites

Blocked by: PR 8c (asset pipeline must exist).

**What**: Close the remaining viewer gaps so the viewer looks and plays like Fight Caves.

- Fix VoidPS terrain/floor-definition blocker OR use RSPS/Kotlin export path for pre-baked FC terrain mesh. Procedural terrain not accepted as final.
- Replace player cylinder with actual player representation using the fixed FC loadout (rune crossbow, black d'hide). Pre-composed model or assembled composite.
- Build RS-like tabbed panel (inventory, prayer, combat/stats tabs) using exported tab/slot sprites.
- Export and display real item sprites for sharks, prayer potions, adamant bolts in inventory slots.
- Make inventory items clickable (routes to EAT/DRINK action heads).
- Make prayer icons clickable (routes to PRAYER action head).
- Tab switching must work.

**Acceptance**:
- Viewer no longer uses procedural terrain as the primary arena presentation.
- Player is no longer a blue cylinder/circle.
- RS-like tabbed UI exists and is functional (tabs switch, items visible as sprites).
- Sharks/prayer pots/bolts visible as inventory items with real sprites.
- Prayer and inventory interactions work through the UI (routed to canonical actions).
- Screenshot is materially closer to actual RuneScape Fight Caves play/debug.

---

### PR 9b: Human-Playable Controls via Canonical Actions

Blocked by: PR 9a (viewer parity must exist first).

**What**: Full human-playable input through the canonical action interface.

- Click tile → MOVE head value (pathfind from player to clicked tile, enqueue one direction per tick)
- Click NPC → ATTACK head value (target NPC in slot by screen position)
- Click prayer icon in UI panel → PRAYER head value (already wired in PR9a)
- Click inventory item in UI panel → EAT/DRINK head value (already wired in PR9a)
- Keyboard shortcuts: F1-F3 for prayers, 1-2 for eat/drink
- Real-time tick rate (~0.6s/tick OSRS standard, or configurable).

**Acceptance**:
- Human can play Fight Caves through the viewer from wave 1 through Jad.
- Click-to-move works on the arena tiles.
- Click-to-attack works on visible NPCs.
- All actions go through the canonical multi-head action interface. No bypasses.

---

### PR 10: Animations, Projectiles, Hitsplats, Replay/Debug

**What**: Visual polish and debug completion.

- Model animations (idle, attack, death) via PufferLib anim system.
- Projectile flight visualization.
- 3D hitsplats (billboard damage numbers).
- Replay file I/O, rewind via snapshot history.
- Debug overlays (aggro, reward, safe spots, state inspector, combat log).

**Acceptance**: Models animate. Projectiles visible. Replay/rewind functional. Debug overlays toggleable.

---

## Phase 4: Training Pipeline (blocked until PR9a + PR9b accepted)

### PR 11: Training Pipeline

**What**: End-to-end training with W&B logging.

- `RL/src/trainer.py` — PufferLib training loop
- `RL/src/policy.py` — MLP policy with multi-head action output (one softmax per head, masked logits)
- `RL/configs/train_base.yaml` — Hyperparameters
- `RL/scripts/train.py` — Entry point
- W&B: SPS, reward, wave reached, episode length, per-head entropy

Smoke test: 10K steps, verify reward increases from random baseline.

**Acceptance**: Training launches. Metrics in W&B. Learning signal exists.

---

### PR 12: Viewer — Policy Playback Mode

**What**: Load trained checkpoint, watch agent play.

- PyTorch checkpoint loading → inference
- Viewer steps fc_core → packs obs → policy → actions → fc_core
- Configurable speed (real-time, fast, slow)
- Overlay: action head values, action probabilities, value estimate per tick

Requires PR 11 (trained checkpoint exists).

**Acceptance**: Can watch a trained agent play Fight Caves in the viewer.

---

## Phase 5: Evaluation and Metrics

### PR 13: Evaluation Pipeline and Living Docs

**What**: Systematic evaluation and performance tracking.

- `RL/scripts/eval.py` — Run N episodes, collect statistics
- Automated eval integration with W&B
- Baseline comparison tooling

**Acceptance**: Eval pipeline runs. Performance docs populated. Integrates with W&B.

---

## Sequencing Rules

```
Phase 1 — Vertical Slice (COMPLETE):
  PR1 (contracts) → PR2 (combat) → PR3 (binding) → PR4 (minimal viewer)

Phase 2 — Full Fight Caves (COMPLETE):
  PR5 (all NPCs) → PR6 (63 waves) → PR7 (batched kernel)

Phase 3 — Viewer Completion (current):
  PR8a/b/c (done) → PR9a (terrain+player+UI) → PR9b (human play) → PR10 (anim+replay+debug)

Phase 4 — Training (blocked until PR9a + PR9b accepted):
  PR7 + PR9b → PR11 (training pipeline)
  PR11 → PR12 (policy playback)

Phase 5 — Evaluation:
  PR11 → PR13 (eval pipeline)
```

Dependencies:
1. PR 8c before PR 9a (asset pipeline must exist)
2. PR 9a before PR 9b (human play needs terrain+UI+player)
3. PR 9a + PR 9b before PR 11 (training blocked until viewer parity accepted)
4. PR 11 before PR 12 (policy playback needs trained checkpoint)
5. PR 11 before PR 13 (eval needs training pipeline)
