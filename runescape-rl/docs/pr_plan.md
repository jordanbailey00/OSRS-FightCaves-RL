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
- **Asset sources**: PufferLib PvP render pipeline (cache export scripts, C header arrays). Placeholder-art-first strategy — viewer never blocked on art assets.

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

**Status**: Accepted. Raylib viewer implemented as single `demo-env/src/viewer.c` (~400 lines).

**Delivered**:
- `demo-env/src/viewer.c` — Combined render+HUD+controls in one file. Top-down tile grid, placeholder art (blue rect=player, type-colored rects=NPCs), HP bars above entities, prayer indicators, Jad telegraph display, hitsplat queue (4 slots per entity), HUD overlay (HP, prayer, wave, tick, food/potion counts, active prayer icon).
- Controls: Space (pause/resume), Right (step once), Up/Down (speed ±), R (reset), H (toggle HUD), D (toggle debug overlay), Q (quit), scroll wheel (zoom).
- Debug overlay: per-tick state hash, action head values.
- Auto-step mode and manual step mode.
- Renderer uses `fc_fill_render_entities` exclusively — no direct state struct access.

**Deviation from plan**: Merged viewer_main.c + viewer_render.c + viewer_hud.c into single viewer.c. Appropriate for current scope; can be split if it grows beyond ~600 lines.

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

### PR 5: All NPC Types — AI and Special Mechanics

**What**: Extend NPC AI beyond Tz-Kih. Stats for all 9 types are already in `fc_npc.c` (NPC_STATS table from PR 2). This PR adds type-specific behavior and real NPC defence stats.

- Add real NPC defence stats to stat table (replace hardcoded def_roll=1 in player attack)
- Tz-Kek: split on death (spawn 2 NPC_TZ_KEK_SM at death position)
- Tok-Xil: ranged attack AI (maintain distance, ranged projectile delay)
- Yt-MejKot: melee + heal nearby NPCs (heal timer, heal amount)
- Ket-Zek: magic attack AI (long range, high damage)
- TzTok-Jad: dual attack style (magic/ranged), prayer telegraph (Idle→MagicWindup→RangedWindup, 3-tick queue), max hits 970/950 tenths
- Yt-HurKot: Jad healers, spawn when Jad reaches low HP, heal Jad if not distracted by player attack

Tests:
- Unit: each NPC type attacks correctly, moves correctly, dies correctly
- Unit: Tz-Kek death spawns 2 adds
- Unit: Jad telegraph → prayer check → damage resolution
- Unit: Yt-MejKot healing
- Unit: Yt-HurKot Jad healing
- Determinism: multi-NPC combat deterministic

**Acceptance**: All 9 NPC types have correct AI. Jad prayer telegraph works. Tz-Kek splits on death. Yt-MejKot heals. Yt-HurKot heals Jad. Real NPC defence stats used. Golden trace still passes (or updated if semantics intentionally changed).

---

### PR 6: Wave System (All 63 Waves + Jad)

**What**: Wave table, spawn rotations, wave progression, and full Fight Caves episode.

- `src/fc_wave.c` — 63-wave spawn table (NPC composition per wave). 15 spawn rotation variants per wave (spawn directions). Wave transition logic (all NPCs dead → next wave). Jad spawns on wave 63 with healer mechanic.
- Full episode: reset → wave 1 → ... → wave 63 → Jad → terminal (win or death).

Replay recording enabled: `fc_headless` writes (seed, actions[]) binary file during episodes.

Tests:
- Unit: wave table correctness (wave N spawns expected NPC types and counts)
- Integration: full 63-wave episode with scripted actions (verify all waves trigger, Jad spawns correctly)
- Determinism: full episode deterministic across runs
- Replay: record episode, replay from file, verify per-tick state hash equality

**Acceptance**: Complete Fight Caves simulation works headlessly. 63 waves + Jad. Deterministic. Replayable.

---

### PR 7: Batched Headless Kernel and Benchmarks

**What**: Multi-environment batched stepping for high-throughput training.

- `src/fc_batch_runner.c` — Step N environments in lockstep. Flat batched observation/action/reward arrays.
- `src/fc_headless_kernel.c` — Single-env headless step loop (reset, step, pack obs, write reward, check done, auto-reset).
- Benchmarks: SPS for 1, 16, 64, 256, 1024 environments.

Tests:
- Batched reset/step produces correct shapes
- Each environment in batch is independent (different seeds → different episodes)
- SPS benchmarks recorded as baseline

**Acceptance**: Batched headless runner at target throughput. SPS baselined.

---

## Phase 3: Training Pipeline

### PR 8: Training Pipeline

**What**: End-to-end training with W&B logging.

- `RL/src/trainer.py` — PufferLib training loop
- `RL/src/policy.py` — MLP policy with multi-head action output (one softmax per head, masked logits)
- `RL/configs/train_base.yaml` — Hyperparameters
- `RL/scripts/train.py` — Entry point
- W&B: SPS, reward, wave reached, episode length, per-head entropy

Smoke test: 10K steps, verify reward increases from random baseline.

**Acceptance**: Training launches. Metrics in W&B. Learning signal exists.

---

## Phase 4: Viewer Expansion

### PR 9: Viewer — Full Fight Caves Rendering

**What**: Extend the minimal viewer (PR 4) to render all NPC types, all waves, combat events.

- NPC type-specific colors/shapes (or placeholder sprites)
- Hit splats (damage numbers with animation, PvP pattern: 4 slots per entity)
- Prayer icons (active prayer indicator on player and HUD)
- Wave transition display
- Jad telegraph visual (attack style indicator)
- Inventory/consumable display in HUD
- Kill count, wave counter

**Acceptance**: Full Fight Caves visually renders in the viewer. All NPC types distinguishable. Combat events visible.

---

### PR 10: Viewer — Human Playable Mode

**What**: Click-based input through the canonical action interface.

- `demo-env/src/viewer_input.c` — Mouse/keyboard → action buffer
- Click tile → MOVE head value (pathfinding via fc_core)
- Click NPC → ATTACK head value
- Click prayer icon → PRAYER head value
- Click inventory slot → EAT/DRINK head value
- Keyboard shortcuts: F1-F3 for prayers, 1-2 for eat/drink
- All inputs populate the same action buffer the RL agent uses. No fc_core calls bypass the action interface.

**Acceptance**: Human can play Fight Caves through the viewer. All actions go through canonical interface.

---

### PR 11: Viewer — Policy Playback Mode

**What**: Load trained checkpoint, watch agent play.

- PyTorch checkpoint loading → inference
- Viewer steps fc_core → packs obs → policy → actions → fc_core
- Configurable speed (real-time, fast, slow)
- Overlay: action head values, action probabilities, value estimate per tick

Requires PR 8 (trained checkpoint exists).

**Acceptance**: Can watch a trained agent play Fight Caves in the viewer.

---

### PR 12: Viewer — Replay/Debug Mode

**What**: Full replay and debug inspection.

- Load replay file (seed + action_sequence)
- Playback via deterministic re-execution through fc_core
- Rewind via snapshot history (PvP pattern: snapshot full state per tick, ~5KB × 2000 ticks)
- Step forward, step backward, pause, resume
- Configurable speed (slowmo 0.1x → fast-forward 10x)
- Debug overlays (individually toggleable):
  - Pathfinding: BFS expansion visualization
  - NPC aggro: range circles, current target lines
  - Reward: per-tick reward feature breakdown
  - Safe spots: tiles unreachable by current NPCs
  - State inspector: hover/click entity → show all internal fields
  - Combat log: scrolling hit events
  - State hash: per-tick hash display for determinism verification

**Acceptance**: Record training episode, load in viewer, step through with full debug inspection.

---

## Phase 5: Evaluation and Metrics

### PR 13: Evaluation Pipeline and Living Docs

**What**: Systematic evaluation and performance tracking.

- `RL/scripts/eval.py` — Run N episodes, collect statistics
- `docs/training_performance.md` — SPS, throughput, resource usage (living doc)
- `docs/agent_performance.md` — Wave distribution, Jad kills, death analysis (living doc)
- Automated eval integration with W&B
- Baseline comparison tooling

**Acceptance**: Eval pipeline runs. Performance docs populated. Integrates with W&B.

---

## Sequencing Rules

```
Phase 1 — Vertical Slice:
  PR1 (contracts) → PR2 (minimal combat) → PR3 (Python binding) → PR4 (minimal viewer)

Phase 2 — Full Fight Caves:
  PR2 → PR5 (all NPCs) → PR6 (63 waves) → PR7 (batched kernel)

Phase 3 — Training:
  PR3 + PR7 → PR8 (training pipeline)

Phase 4 — Viewer Expansion:
  PR4 + PR6 → PR9 (full rendering)
  PR9 → PR10 (human play)
  PR8 + PR9 → PR11 (policy playback)
  PR9 → PR12 (replay/debug)

Phase 5 — Evaluation:
  PR8 → PR13 (eval pipeline)
```

Dependencies:
1. PR 1 before everything (contracts are foundational)
2. PR 2 before PR 3 (Python can't bind to empty C)
3. PR 2 before PR 4 (viewer needs mechanics to render)
4. PR 2 before PR 5 (NPC expansion builds on combat slice)
5. PR 5 before PR 6 (waves need NPC types)
6. PR 3 + PR 7 before PR 8 (training needs binding + batched kernel)
7. PR 6 before PR 9 (full viewer needs all waves)
8. PR 8 before PR 11 (policy playback needs a checkpoint)

Parallelism opportunities:
- PR 4 (viewer) and PR 5 (NPCs) can proceed in parallel after PR 2
- PR 3 (binding) can proceed in parallel with PR 4/PR 5
- PR 9–12 (viewer expansion) can proceed in parallel with PR 8 (training) and PR 13 (eval)
