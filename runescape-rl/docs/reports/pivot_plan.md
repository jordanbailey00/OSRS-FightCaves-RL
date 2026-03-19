# Fight Caves RL — Pivot Plan

## 0) Executive decision

Do **not** restart from zero.

Proceed with a **two-environment architecture plus one oracle/reference module**:

1. **V1 Oracle / Reference Module**  
   Keep the current simulator-backed RSPS + headless correctness stack as the correctness oracle, parity reference, and source of authoritative Fight Caves mechanics behavior.

2. **V1.5 RSPS-backed Headed Demo / Replay Environment (`fight-caves-demo-rsps`, working name)**  
   Build the primary headed demo path by trimming and reusing the existing `RSPS` server/runtime/content plus stock `void-client`. This path must be Fight Caves-only, direct-to-cave after login, server-enforced canonical starter state, and visually/functionally close to the in-game RSPS/OSRS experience. The current `fight-caves-demo-lite` module is frozen as fallback/reference only.

3. **V2 Fast Headless Training Environment**  
   Build a new lightweight training kernel focused only on Fight Caves mechanics, flat observations, batched stepping, and PufferLib-native training throughput.

This recommendation follows directly from the current workspace shape: the RL repo already contains PufferLib integration, reward configs, replay/export, benchmarks, and broad test coverage, while the simulator repo already contains the headed runtime, headless bootstrap, action adapter, observation builders, and parity/performance suites. The current training flow runs through a correctness-first bridge into the Kotlin headless runtime rather than a minimal high-throughput kernel. fileciteturn15file0 fileciteturn15file2 fileciteturn15file3 fileciteturn16file0

PufferLib 3.0 is optimized around native `PufferEnv` environments that operate in-place on shared buffers, and its docs explicitly call out async `send/recv`, multiple environments per worker, shared memory, shared flags, zero-copy batching, and native/C environments as the main path to very high SPS. The docs also note that pure Python becomes a bottleneck as environments grow more complex, while the native `PufferEnv` format is designed so observations, actions, rewards, and terminals are initialized from shared buffers and all operations happen in-place. ([puffer.ai](https://puffer.ai/docs.html?utm_source=chatgpt.com))

---

## 0.1) Closure status

The pivot implementation described in this document is now complete through PR 8.2.

Current active end state:

- default RL training backend: `v2_fast`
- default headed demo/replay backend: trusted RSPS-backed headed path
- preserved fallbacks remain selectable:
  - `fight-caves-demo-lite` as frozen fallback/reference
  - V1 oracle/reference/debug replay path
  - explicit V1 config-based training and benchmark fallbacks

This document remains the high-level architecture/source-of-truth plan. The execution record and final closure summary live in:

- `/home/jordan/code/pivot_implementation_plan.md`
- `/home/jordan/code/pivot_closure_report.md`

---

## 1) Success criteria

This pivot is complete only when all of the following are true:

- A policy can train end-to-end to complete Fight Caves and earn a fire cape in the **headless V2 trainer**.
- The trained policy can run in the **RSPS-backed headed demo path**, using stock `void-client` first, with the player routed directly into a Fight Caves-only run that is visually and functionally close to the in-game experience.
- The headless training path achieves **at least 500,000 training steps/sec** as a measured training throughput target.
- The training environment, the RSPS-backed headed demo path, and the oracle/reference environment satisfy **mechanics parity**, meaning the same action schema, tick semantics, combat/prayer loop, inventory rules, wave progression, and terminal outcomes at the contract boundary.
- PufferLib remains the trainer/runtime backbone at minimum. The RL repo already has a dedicated PufferLib integration layer, training configs, benchmark scripts, and trainer-facing wrappers, so the pivot should reuse that foundation rather than replace it. fileciteturn16file0

### Performance gates

Use two separate performance gates:

- **Env-only SPS gate:** target **≥ 1,000,000 env steps/sec** on the benchmark machine.
- **End-to-end training SPS gate:** target **≥ 500,000 steps/sec** during actual training.

This split is important because the final trainer includes policy inference, rollout storage, optimization, and logging overhead beyond raw environment stepping. PufferLib’s docs distinguish between environment simulation performance and full trainer throughput, and they show that fast native env design is the prerequisite for very high train-time throughput. ([puffer.ai](https://puffer.ai/docs.html?utm_source=chatgpt.com))

---

## 2) Current-state diagnosis

Your current architecture already has the right *pieces*, but the training hot path is too heavy.

The inventory shows the current training path as:

- `RL/scripts/train.py`
- `RL/fight_caves_rl/puffer/trainer.py`
- `RL/fight_caves_rl/puffer/factory.py`
- `RL/fight_caves_rl/envs/vector_env.py`
- `RL/fight_caves_rl/bridge/batch_client.py`
- `RL/fight_caves_rl/bridge/debug_client.py`
- `fight-caves-RL/game/src/main/kotlin/HeadlessMain.kt` and related simulator APIs. fileciteturn15file2

The current execution flow also shows:

- structured and flat observation builders in Kotlin
- Python bridge conversion of JVM observations to Python objects / NumPy arrays
- Python-side observation mapping and Puffer encoding
- Python-side reward and terminal inference after the simulator step
- a bridge package explicitly described as a **correctness-first headless sim integration**. fileciteturn15file0 fileciteturn16file0

That is why the current stack feels large and slow relative to NMMO3.

NMMO3 is much closer to:

- thin Python wrapper
- native env state
- native batched step
- native observation packing
- native reward reduction

while your current stack is closer to:

- Python vec env
- Python batch bridge
- JPype runtime bridge
- headless Kotlin runtime
- Python-side terminal/reward/info logic. fileciteturn15file3 fileciteturn15file8 fileciteturn15file9 fileciteturn15file4 fileciteturn15file5

---

## 3) Pivot principle

Define parity as:

**mechanics parity between the fast trainer and the RSPS-backed headed demo/oracle environment, not full engine/runtime parity.**

That means the trainer must preserve:

- tick cadence
- action meanings
- action rejection rules
- attack timings
- prayer timings
- Jad telegraph semantics
- consumable rules
- movement/run rules
- wave progression
- episode reset contract
- terminal outcomes

It does **not** need to preserve:

- full general `GameLoop` runtime architecture
- full script loading model
- broad engine modularity
- rich structured observation objects
- debug/info payload richness in the hot path
- full headed runtime internals inside the trainer

This is the key unlock. It lets the training env become small enough to align with PufferLib’s high-throughput model. ([puffer.ai](https://puffer.ai/docs.html?utm_source=chatgpt.com))

---

## 4) Target architecture

## 4.1 Environment split

### A. Oracle / Reference Module (keep)
Purpose:
- parity oracle
- authoritative mechanics reference
- seed/action trace generation
- debugging and correctness checks
- manual headed validation when mechanics disputes arise

Implementation:
- keep current simulator-backed headed/headless stack
- keep `HeadlessMain.kt`
- keep `HeadlessActionAdapter.kt`
- keep `HeadlessObservationBuilder.kt`
- keep the current replay/eval/export flow
- keep the current parity and determinism suites
- keep full RSPS/runtime behavior available as the source of truth for Fight Caves mechanics. fileciteturn15file2 fileciteturn15file3 fileciteturn16file0

### B. Headed Demo / Replay Environment (new)
Purpose:
- primary visual demo environment
- primary replay environment
- immediate wave-1 Fight Caves load for manual testing
- lightweight headed validation target that feels like playing Fight Caves in RSPS without broader world/server access

Implementation:
- new RSPS-backed headed demo path, working name **`fight-caves-demo-rsps`**
- built by trimming and reusing `RSPS` server/runtime/content plus stock `void-client`
- keeps login, JS5/file-server, region loading, interfaces, containers, vars, graphics, and the normal client protocol intact
- routes the player directly into the Fight Caves runtime after login and enforces the canonical starter state server-side
- only exposes the systems required to play Fight Caves end-to-end
- blocks broader world traversal and unrelated content through gating before deep pruning
- becomes the primary manual validation path first, and only later becomes the default demo/replay backend if it proves trustworthy
- leaves `fight-caves-demo-lite` frozen as fallback/reference rather than deleting it

### C. Fast Training Environment (new)
Purpose:
- maximize SPS
- minimize per-step overhead
- provide fixed flat observations
- consume packed actions
- return batched obs/reward/terminal outputs
- satisfy mechanics parity with the RSPS-backed headed demo path and the oracle/reference module

Implementation:
- new **Fight Caves Fast Kernel**
- batched API
- flat contiguous buffers
- no structured observation objects in the hot path
- no per-slot Python normalization/bridge object construction in the hot path
- direct PufferLib-native vector env wrapper

---

## 4.2 Architectural decision

### Decision
Build the first V2 fast trainer in **Kotlin/JVM**, not C, because:
- your current mechanics already live there
- action rules and episode initialization already live there
- it is the fastest salvage path
- it avoids duplicating combat logic across languages immediately
- it keeps parity easier to validate

If Kotlin/JVM V2 still misses the performance gate after batch API + flat buffer work, then add a **Stage 2 native kernel** in C/Cython/Rust later.
The V2 fast-env API, buffer contracts, action schema, terminal codes, and reward-feature outputs must be designed so the Kotlin/JVM kernel can be replaced by a future native/C kernel without changing the RL-facing interface.

This is the correct salvage path:
- **first** shrink runtime shape and bridge overhead
- **then** decide whether a full native kernel is required

---

## 5) What stays, what changes, what leaves the hot path

## 5.1 Keep as canonical/oracle/debug

Keep these as the authoritative oracle/reference path:

- `fight-caves-RL/game/src/main/kotlin/HeadlessMain.kt`
- `fight-caves-RL/game/src/main/kotlin/HeadlessActionAdapter.kt`
- `fight-caves-RL/game/src/main/kotlin/HeadlessObservationBuilder.kt`
- `fight-caves-RL/game/src/main/kotlin/HeadlessFlatObservationBuilder.kt`
- `RL/fight_caves_rl/replay/*`
- `RL/fight_caves_rl/tests/determinism/*`
- `RL/fight_caves_rl/tests/parity/*`
- `RL/fight_caves_rl/tests/integration/*`
- full headed runtime entrypoint(s) and existing replay/eval flows used for oracle/reference validation. fileciteturn15file2 fileciteturn15file3 fileciteturn15file7 fileciteturn16file0

## 5.2 Keep and update

Keep these subsystems, but update them for the pivot:

- `RL/fight_caves_rl/puffer/factory.py`
- `RL/fight_caves_rl/puffer/trainer.py`
- `RL/fight_caves_rl/rewards/registry.py`
- `RL/fight_caves_rl/curriculum/*`
- `RL/fight_caves_rl/policies/*`
- benchmark scripts under `RL/scripts/benchmark_*.py`
- training/eval/reward configs under `RL/configs/*`
- performance suites under `RL/fight_caves_rl/tests/performance/*`. fileciteturn15file2 fileciteturn16file0

## 5.3 Remove from the training hot path, but do not delete

These should stop being part of the primary training path:

- `RL/fight_caves_rl/bridge/debug_client.py`
- `RL/fight_caves_rl/bridge/batch_client.py`
- `RL/fight_caves_rl/envs/correctness_env.py`
- structured observation conversion and validation in the train path
- rich per-step info payload construction in the train path
- per-slot JPype action construction in the train path
- post-step Python terminal inference in the train path. fileciteturn15file0 fileciteturn15file8 fileciteturn15file9

These stay for:
- oracle checks
- debugging
- acceptance tests
- demo/replay support

## 5.4 New code to add

### In `fight-caves-RL`
Add a new package for the fast trainer kernel, for example:

```text id="6tf88e"
fight-caves-RL/game/src/main/kotlin/headless/fast/
    FastFightCavesKernel.kt
    FastBatchApi.kt
    FastEpisodeInitializer.kt
    FastActionCodec.kt
    FastActionSchema.kt
    FastFlatObsWriter.kt
    FastStepMetrics.kt
    FastTerminalState.kt
    FastParityTrace.kt
    FastWaveLogic.kt
    FastCombatLoop.kt
    FastNpcState.kt
    FastPlayerState.kt
    FastJadTelegraph.kt
```

### In `RSPS`
Add a demo-specific headed path inside `RSPS`, for example:

```text id="8ydj0u"
RSPS/game/src/main/kotlin/demo/fight_caves/
    FightCavesDemoMain.kt
    FightCavesDemoBootstrap.kt
    FightCavesDemoModules.kt
    FightCavesDemoTickStages.kt
    FightCavesEpisodeInitializer.kt
    FightCavesWorldGate.kt
    FightCavesDemoSessionLog.kt
    FightCavesStarterStateManifest.kt
```

### In `RSPS/void-client` (optional later only if justified)
Keep stock client first. Only add convenience work after the server/runtime path is already usable, for example:

```text
RSPS/void-client/client/src/Loader.java
RSPS/void-client/client/src/...
```

### In `RL`
Add a new training env path, for example:

```text id="96d2fx"
RL/fight_caves_rl/envs_fast/
    __init__.py
    fast_vector_env.py
    fast_spaces.py
    fast_policy_encoding.py
    fast_reward_adapter.py
    fast_benchmark.py
    fast_trace_adapter.py

RL/fight_caves_rl/contracts/
    __init__.py
    mechanics_contract.py
    parity_trace_schema.py
    terminal_codes.py
    reward_feature_schema.py
```

### New configs

```text id="wcj1f0"
RL/configs/train/train_fast_v2.yaml
RL/configs/train/smoke_fast_v2.yaml
RL/configs/reward/reward_sparse_v2.yaml
RL/configs/reward/reward_shaped_v2.yaml
RL/configs/curriculum/curriculum_wave_progression_v2.yaml
RL/configs/benchmark/fast_env_v2.yaml
RL/configs/benchmark/fast_train_v2.yaml
RL/configs/eval/parity_fast_v2.yaml
```

---

## 6) V2 fast training environment design

## 6.1 Kernel responsibilities

The new fast kernel must own all training-critical mechanics:

- episode reset / start wave
- player state
- NPC state
- wave spawning / progression
- combat timing
- prayer timing
- Jad telegraph timing
- movement and run state
- consumable usage
- inventory/resource counters
- hit resolution
- terminal detection
- reward feature emission
- flat observation writing

It must **not** depend on:
- full structured observation objects
- the general headed runtime UI state
- per-slot JVM object wrappers
- Python-side semantic interpretation to finish the step

## 6.2 Batch API shape

The V2 kernel API should be batch-first.

### Required calls

- `createKernel(config)`
- `resetBatch(slot_ids, reset_options, out_obs, out_terminals, out_metrics)`
- `stepBatch(actions, out_obs, out_rewards_or_metrics, out_terminals, out_infos)`
- `close()`

### Immediate output contracts

Each `stepBatch` must return, for **all active slots**:

- observation buffer
- terminal/truncation flags
- terminal code
- reward feature vector or final scalar reward
- minimal episode counters
- minimal rejection/action status code

No per-slot Python callbacks.
No structured dict construction.
No post-step semantic reconstruction.

## 6.3 Buffer format

All train-path data must be contiguous fixed-shape arrays:

- observations: `float32` or `uint8` fixed-width rows
- actions: small integer arrays
- rewards: `float32`
- terminals: `bool` or `uint8`
- truncations: `bool` or `uint8`
- terminal codes: `uint8`
- reward feature matrix: `float32[k]` per env slot if using weighted reward composition in Python

This aligns with PufferLib’s native `PufferEnv` format, where operations happen in-place on shared buffers and vectorization relies on shared-memory slices rather than object-heavy APIs. ([puffer.ai](https://puffer.ai/docs.html?utm_source=chatgpt.com))

---

## 7) Observation design

## 7.1 Training observation principles

The V2 observation must be:

- fixed width
- flat
- contiguous
- cheap to write
- stable across resets
- stable across training/demo parity checks
- free of future leakage
- directly usable by the policy

PufferLib’s docs explicitly emphasize flattened observation handling and native env buffers as the fast path. ([puffer.ai](https://puffer.ai/docs.html?utm_source=chatgpt.com))

## 7.2 Observation groups

Use a single flat vector composed of:

### A. Self state
- current hp ratio
- current prayer ratio
- run energy ratio
- ammo ratio
- sharks remaining ratio
- prayer potions remaining ratio
- position (normalized)
- moving flag
- running flag
- attack cooldown ticks normalized
- eat cooldown ticks normalized
- potion cooldown ticks normalized
- prayer toggle lockout ticks normalized
- under_attack flag
- current wave normalized
- remaining NPC count normalized
- Jad present flag
- Jad alive flag

### B. Prayer/combat state
- protect magic active
- protect ranged active
- protect melee active
- current attack target visible-index or none
- in_combat flag
- safe_spot state / exposure state if used by mechanics
- last damage taken normalized
- last damage dealt normalized

### C. Jad telegraph state
- telegraph phase one-hot: none / magic windup / ranged windup
- ticks since telegraph onset normalized
- ticks until hit resolve normalized if this is part of the mechanics contract in both trainer and demo

### D. Wave/global state
- wave id
- spawn group index or stage index
- total NPCs alive normalized
- healers alive normalized
- Jad hp ratio when present
- time in episode normalized
- ticks remaining until cap normalized

### E. Visible NPC table
Use a fixed-size table of the nearest or canonical visible NPC targets, sorted by a stable rule shared with demo/oracle. For each NPC row:
- present flag
- npc type id / one-hot bucket
- hp ratio
- x offset normalized
- y offset normalized
- distance normalized
- targeting player flag
- attack style / telegraph state
- ticks-to-next-hit normalized if available from mechanics
- alive flag

### F. Optional map/local geometry
Only include if needed. Prefer compact geometry or safe-spot flags over a large pixel/grid representation for the first fast trainer.

## 7.3 What not to include
Do not include in V2 train observations:
- structured Python dict views
- verbose debug metadata
- UI-only fields
- text-like fields
- fields that only exist for replay/export
- future leakage fields in the training default path

---

## 8) Action design

## 8.1 Action schema rule

Keep the **append-only action schema concept**, but stop decoding it into rich Python objects inside the training hot path.

The trainer should send a compact integer action representation directly to the fast kernel.

## 8.2 Recommended action format

Use a small **MultiDiscrete** action space because PufferLib’s defaults support discrete and multi-discrete spaces directly. ([puffer.ai](https://puffer.ai/docs.html?utm_source=chatgpt.com))

Recommended action vector:

- `intent_opcode`
- `target_index`
- `aux_param_1`
- `aux_param_2`

Where:

### `intent_opcode`
Encodes one-intent-per-tick actions such as:
- noop
- move_n
- move_s
- move_e
- move_w
- move_ne
- move_nw
- move_se
- move_sw
- attack_target
- toggle_protect_magic
- toggle_protect_ranged
- eat_shark
- drink_prayer_potion
- toggle_run

### `target_index`
Used for targetable actions, especially visible NPC target selection.

### `aux_param_*`
Reserved for future mechanics without breaking the schema.

## 8.3 Decoder location
The action decoder must live **inside the fast kernel**, not in Python.

Python should only:
- define the space
- write the raw action integers
- call the batch step

---

## 9) Reward design

## 9.1 Reward architecture

Keep reward configuration in the RL repo, but move reward-relevant **event extraction** into the fast kernel.

### Kernel responsibility
For each step, the kernel emits a small `reward_features` vector per env slot.

### Python responsibility
Python applies config-driven weights to that vector:
- cheap vectorized dot product
- easy reward iteration
- no need to infer reward semantics from a structured observation post-step

This preserves flexibility without keeping the expensive current reward path.

## 9.2 Required reward outputs from the kernel

Per env slot per step, emit:

- `damage_dealt`
- `damage_taken`
- `npc_kill`
- `wave_clear`
- `jad_damage_dealt`
- `jad_kill`
- `player_death`
- `cave_complete`
- `food_used`
- `prayer_potion_used`
- `correct_jad_prayer_on_resolve`
- `wrong_jad_prayer_on_resolve`
- `invalid_action`
- `movement_progress`
- `idle_penalty_flag`
- `tick_penalty_base`

## 9.3 Reward configs to support

### `reward_sparse_v2`
Use for evaluation and canary training:
- `+1.0` cave complete
- `-1.0` player death
- optionally `0.0` otherwise

### `reward_shaped_v2`
Use for main training:
- positive:
  - damage dealt
  - npc kills
  - wave clears
  - Jad damage
  - cave completion
  - correct Jad prayer at hit resolution
  - movement/progress only if truly useful
- negative:
  - damage taken
  - wrong Jad prayer at hit resolution
  - invalid actions
  - overuse of resources if desired
  - small per-tick penalty
  - player death

### Reward rule
Do **not** reward merely toggling prayer. Reward only the mechanically correct outcome at the relevant timing boundary.

## 9.4 Existing reward system to reuse
The current repo already has a reward registry plus sparse and shaped reward configs, and the shaped reward currently layers dense damage, consumable-use, and step terms on top of sparse reward. Keep that conceptual split, but move the expensive event extraction closer to the simulator/kernel. fileciteturn16file0

---

## 10) Terminal and episode design

The fast kernel must directly emit:

- `terminated`
- `truncated`
- `terminal_code`

### Terminal codes
Use a fixed enum such as:
- `NONE`
- `PLAYER_DEATH`
- `CAVE_COMPLETE`
- `TICK_CAP`
- `INVALID_STATE`
- `ORACLE_ABORT`

### Episode reset inputs
Each reset must support:
- seed
- start wave
- ammo
- sharks
- prayer potions
- tick cap
- optional curriculum stage overrides

The current stack already centralizes reset/start-wave behavior in the simulator and env config layer, so preserve that concept in V2. fileciteturn15file0 fileciteturn15file2

---

## 11) PufferLib 3.0 alignment

## 11.1 What the V2 env must do

The V2 trainer env must be implemented as a **native `PufferEnv`-style vector environment** with:

- in-place buffer writes
- flat spaces
- minimal per-step Python work
- `send/recv` support for async vectorization
- compatibility with multiple envs per worker
- compatibility with shared memory slices
- no object-heavy step path

That is the exact style PufferLib 3.0 documents for fast environments. ([puffer.ai](https://puffer.ai/docs.html?utm_source=chatgpt.com))

## 11.2 Trainer/backend guidance

### First target
- implement the fast env itself first
- benchmark it in serial / single-process mode
- only then layer multiprocessing / async vectorization

### Training target
Use:
- `PuffeRL`
- async `send/recv`
- multiple envs per worker
- shared memory backend
- minimal logging inside rollouts

PufferLib’s docs explicitly say async `send/recv` is required for the EnvPool-like fast path, and they list multiple envs per worker, shared memory, shared flags, and zero-copy batching as the main optimizations. ([puffer.ai](https://puffer.ai/docs.html?utm_source=chatgpt.com))

## 11.3 Policy guidance

Use a **recurrent policy**.

Fight Caves is partially observable and timing-heavy, and PufferLib already supports standard PyTorch policies plus LSTM integration, with an optimized rollout/training split using `LSTMCell` during rollouts and `LSTM` during training. ([puffer.ai](https://puffer.ai/docs.html?utm_source=chatgpt.com))

Recommended starting policy:
- shared MLP encoder
- 1 recurrent layer
- hidden size 128–256
- policy head for MultiDiscrete action space
- value head
- no image encoder in V2

## 11.4 Observation/action space handling

Use:
- flat `Box` observation space
- `MultiDiscrete` action space

Avoid structured-space emulation in the hot path. PufferLib’s docs explain that flattening and handling structure only at the emulation/model boundary keeps vectorization and storage simpler and faster. ([puffer.ai](https://puffer.ai/docs.html?utm_source=chatgpt.com))

---

## 12) Training plan

## 12.1 Algorithm
Use **PuffeRL PPO + recurrent policy** as the default baseline.

## 12.2 Training tracks

Run three tracks:

### Track A — smoke
Purpose:
- compile/run sanity
- determinism smoke
- no crash / no NaN / reset correctness

### Track B — shaped training
Purpose:
- teach combat loop
- teach resource usage
- teach Jad telegraph/prayer response
- maximize early learning speed

### Track C — sparse eval
Purpose:
- measure true task success
- ensure no reward hacking
- determine actual fire cape completion rate

## 12.3 Curriculum
The repo already has disabled and wave-progression curricula. Keep that pattern and introduce `wave_progression_v2` as the default training curriculum. fileciteturn16file0

Recommended curriculum stages:
- stage 1: early waves only
- stage 2: mid waves
- stage 3: late waves no Jad
- stage 4: Jad-focused episodes
- stage 5: full caves
- stage 6: mixed full caves + Jad starts

## 12.4 Evaluation
Keep:
- deterministic eval
- replay eval
- parity canaries
- seed-pack based evaluation

The current repo already has replay export and parity/determinism test coverage; reuse it as the evaluation spine rather than replacing it. fileciteturn15file2 fileciteturn16file0

---

## 13) Demo / headed environment plan

## 13.1 Immediate answer
Do **not** continue `fight-caves-demo-lite` as the primary Phase 7 path.

Freeze `fight-caves-demo-lite` as:
- fallback/reference
- historical evidence of the earlier lite-demo direction
- a retained source of PR 7.1 validation artifacts and implementation ideas

Build the new primary headed path as an **RSPS-backed Fight Caves-only demo**, working name **`fight-caves-demo-rsps`**, by trimming and reusing:
- `RSPS` server/runtime/content
- stock `void-client`

This headed path must:
- keep the real client protocol surface intact
- route the player directly into Fight Caves after login
- enforce the canonical starter state server-side in the Fight Caves runtime path
- remain Fight Caves-only
- look and feel functionally close to the in-game RSPS/OSRS experience
- preserve debuggability and manual validation visibility

Historical note: earlier pre-replan drafts treated `fight-caves-demo-lite` as the default headed demo target. That direction is now superseded. The active primary headed target is the RSPS-backed path, and `fight-caves-demo-lite` is frozen fallback/reference only.

## 13.2 Source-of-truth split
The RSPS-backed headed path uses this ownership split:

- `RSPS` owns headed mechanics/runtime behavior, Fight Caves boot flow, and server-side starter-state enforcement
- the headless Fight Caves path remains the RL training path and the contract reference for the training environment
- `void-client` owns headed UI, gameframe, rendering, assets, and cache-backed presentation
- `RL` remains the PufferLib/training/orchestration owner
- `fight-caves-demo-lite` remains frozen fallback/reference and is no longer the primary headed target

## 13.3 First-pass scope
The first pass should prefer reuse and gating over deep deletion:

- keep login and JS5/file-server compatibility
- keep region loading, dynamic zones, instances, interfaces, containers, vars, player/NPC updates, graphics, and projectiles
- keep the stock client first, with no assumed client fork
- gate broader world traversal and unrelated content rather than aggressively pruning cache/data/interfaces
- make the headed path trustworthy for manual testing before adding later demo/replay polish

## 13.4 Required demo contract
The trained policy must eventually be runnable against the headed path using the same:
- action schema
- tick cadence
- reset contract
- visible target ordering rule
- telegraph semantics
- terminal conditions
- canonical preset player-state contract

## 13.5 Caution note
This is a real detour from the previous lite-demo continuation.

- it delays the old `fight-caves-demo-lite` Phase 7/8 path intentionally
- it does not erase earlier lite-demo work
- it does replace the old assumption that lite-demo is the default headed endpoint

---

## 14) Parity contract

## 14.1 What parity means
Parity must be checked at the level of **mechanics traces**, not internal engine implementation.

## 14.2 Required parity-trace fields
For the same seed and same action sequence, compare V2 against the oracle/reference module and the RSPS-backed headed demo path on:

- tick index
- action accepted/rejected
- rejection code
- player hp
- player prayer
- run state
- inventory counts
- wave id
- remaining NPCs
- visible NPC target list/order
- each visible NPC type / hp / alive flag
- Jad telegraph state
- Jad hit resolve outcome
- damage dealt/taken
- terminal code

## 14.3 Acceptance rule
V2 is accepted only when these traces match within the defined mechanics contract for the parity scenarios.

The existing repo already contains parity canaries, replay equivalence checks, and bridge batch step parity tests. Extend those instead of inventing a new parity framework. fileciteturn16file0

---

## 15) Immediate no-regret actions

Do these before the full V2 kernel is complete.

### 15.1 Strip V1 training path
For the current bridge path:
- force flat observations only
- disable structured observation validation in training
- force minimal info payload mode
- disable rich debug fields during training
- verify no future leakage on train path

This will not solve the architectural issue, but it will give a cleaner baseline.

### 15.2 Add precise timing breakdowns
Instrument the current training step into:
- Python action decode
- JPype action application
- runtime tick
- flat observation retrieval
- terminal inference
- reward evaluation
- info assembly

The repo already has benchmark scripts and performance tests; extend those with segment timing so the pivot has hard evidence. fileciteturn15file2 fileciteturn16file0

### 15.3 Add batch bridge APIs
Before V2 fully exists, add:
- `applyActionsBatch(...)`
- `observeFlatBatch(...)`

This is an interim optimization and also a stepping stone toward the V2 kernel.

---

## 16) Implementation phases

## Phase 0 — decision freeze and contract freeze
Deliverables:
- write this pivot into repo docs
- freeze the mechanics parity definition
- freeze the V2 action schema
- freeze the V2 terminal code enum
- freeze reward feature schema

## Phase 1 — baseline measurement
Deliverables:
- current V1 benchmark breakdowns
- current env-only SPS
- current train SPS
- current per-stage timing profile
- current memory profile

Use existing benchmark scripts and performance tests as the starting point. fileciteturn15file2 fileciteturn16file0

## Phase 2 — V1 trimming
Deliverables:
- flat-only training mode
- minimal info mode default for benchmarks
- structured observation path removed from training hot path
- batch action/observe bridge APIs
- revised benchmark numbers

## Phase 3 — V2 fast kernel MVP
Deliverables:
- new fast kernel package in `fight-caves-RL`
- batch reset/step APIs
- fixed flat observation writer
- packed action decode
- terminal code emission
- reward feature emission
- env-only benchmark

Acceptance:
- env-only SPS materially above V1
- deterministic smoke works
- first parity traces available

## Phase 4 — PufferLib V2 wrapper
Deliverables:
- `fast_vector_env.py`
- `fast_spaces.py`
- `fast_policy_encoding.py`
- factory switch for `env_backend = v1_bridge | v2_fast`
- smoke training config for V2

Acceptance:
- PuffeRL can run the new env
- serial and async paths work
- reset/step semantics stable

## Phase 5 — training and rewards
Deliverables:
- `reward_sparse_v2.yaml`
- `reward_shaped_v2.yaml`
- `curriculum_wave_progression_v2.yaml`
- recurrent policy baseline
- train/eval configs

Acceptance:
- no NaNs
- episodes complete/reset correctly
- shaped learning signal is present
- sparse eval pipeline runs

## Phase 6 — parity hardening
Deliverables:
- parity trace schema
- parity canaries for action acceptance, prayer timing, Jad timing, wave progression, terminal codes
- V1 vs V2 equivalence suites

Acceptance:
- defined parity scenarios pass
- demo-oracle transfer confidence is high enough to proceed

## Phase 7 — RSPS-backed headed demo program
Phase 7 is now the RSPS-backed headed demo program.

Its deliverables are:
- formally freeze `fight-caves-demo-lite` as fallback/reference
- add an RSPS demo-specific bootstrap/profile that keeps login, JS5/file-server, and stock client protocol intact
- add a server-side Fight Caves episode initializer that enforces the canonical starter state from `RL/RLspec.md`
- gate broader world traversal and unrelated content while preserving required generic plumbing
- bring up the headed path with stock `void-client` on native Windows against the WSL-hosted RSPS runtime
- add starter-state manifests, session logs, entry/reset/leave events, and manual validation checklists
- prove direct entry, canonical starter state, no broader world access, playable combat/prayer/inventory loop, wave progression, and restart/death/completion handling

Phase 7 explicitly does **not** assume:
- a custom client fork in the first milestone
- continuation of the old `fight-caves-demo-lite` PR 7.2 replay/live inference plan
- automatic default-switchover at the end of the first bring-up

Historical note:
- old lite-demo PR 7.1 work remains useful fallback/reference evidence
- old lite-demo PR 7.2 continuation is superseded by the RSPS-backed plan

Acceptance:
- a manual user can connect with stock `void-client` and enter the constrained Fight Caves path
- the canonical starter state is enforced server-side and validated against `RL/RLspec.md`
- broader world access is blocked
- the headed path is sufficiently playable and observable to be trusted for manual demo/testing

## Phase 8 — trust gate and selective switchover
Phase 8 only happens after the RSPS-backed headed path is stable enough to trust.

Phase 8 deliverables are:
- decide whether the RSPS-backed headed path is trustworthy enough to become the default demo/replay backend
- switch defaults only if the Phase 7 trust gate passes
- keep `fight-caves-demo-lite` available as fallback/reference until the new path is proven
- leave V1 available as oracle/reference/debug backend
- update docs/configs/tests only after the new headed path is validated

---

## 17) Codebase revision plan

## In `RL`
### Keep
- `fight_caves_rl/puffer/*`
- `fight_caves_rl/rewards/*`
- `fight_caves_rl/curriculum/*`
- `fight_caves_rl/policies/*`
- `fight_caves_rl/replay/*`
- `configs/*`
- tests, especially determinism/parity/performance
- benchmark scripts. fileciteturn16file0

### Update
- `fight_caves_rl/puffer/factory.py`
- `fight_caves_rl/puffer/trainer.py`
- env benchmark scripts
- train/eval configs
- reward configs
- replay/eval runner to support `v2_fast`
- docs describing training topology

### De-scope from train hot path
- `fight_caves_rl/bridge/*`
- `fight_caves_rl/envs/correctness_env.py`
- structured observation mapping/validation path
- rich info payload path

### Add
- `fight_caves_rl/envs_fast/*`
- `fight_caves_rl/contracts/*`
- new configs and tests

## In `fight-caves-RL`
### Keep
- `HeadlessMain.kt`
- headed runtime entrypoint(s)
- oracle observation/action builders
- current episode initializer
- existing JUnit parity/performance suites. fileciteturn15file2 fileciteturn15file3

### Update
- add a fast kernel package
- add batch fast APIs
- add parity trace emission
- optionally add shared mechanics helpers usable by both oracle and fast kernel

### Do not require for V2
- full script bootstrapping
- general runtime subsystems not needed by the mechanics kernel
- rich structured observation objects in the hot path

## In `RSPS`
### Add
- demo-specific bootstrap/profile for the headed Fight Caves path
- server-side Fight Caves episode initializer
- Fight Caves-only world gating layered on top of intact login/protocol/runtime plumbing
- starter-state manifests and headed session logs
- manual validation checklist and audit-oriented instrumentation

### Keep aligned with the shared mechanics contract
- action schema
- tick cadence
- terminal codes
- visible-target ordering rule
- Jad telegraph semantics
- reset/start-wave contract

### Do not require in the first pass
- aggressive cache/data/interface pruning
- a Windows-only server/runtime path
- a custom client fork

## In `fight-caves-demo-lite`
### Freeze
- keep the module in the repo as fallback/reference
- retain old PR 7.1 validation artifacts and implementation history
- allow only reference maintenance or emergency compatibility fixes if later required

### Do not continue
- old PR 7.2 replay/live inference continuation
- primary headed-demo feature growth
- any attempt to make lite-demo the default headed backend again without a new planning decision

---

## 18) Dependencies and tooling

## Required
- Python training stack already present in the RL repo: PufferLib, Torch, NumPy, YAML/config tooling, benchmark/test infrastructure. fileciteturn16file0
- Kotlin/Gradle simulator stack already present in `fight-caves-RL`. fileciteturn16file0
- the existing `RSPS` runtime plus stock `void-client` for the headed demo path
- PufferLib 3.x native `PufferEnv` path and `PuffeRL`. ([puffer.ai](https://puffer.ai/docs.html?utm_source=chatgpt.com))

## Keep only for oracle/interim
- JPype bridge

## Add for profiling
- Python profiler for train path
- JVM profiler for fast kernel path
- benchmark scripts that separate env-only and train SPS

The repo already has benchmark scripts and performance tests; extend those rather than creating one-off measurement code. fileciteturn15file2 fileciteturn16file0

### Development Environment Assumption

All implementation, tooling, dependency installation, scripts, benchmarks, profiling, and runtime commands for this pivot must assume the Codex agent is operating in a WSL Linux environment.

Requirements:
- Use Linux/WSL paths, shells, and commands
- Do not assume Windows-native path semantics or PowerShell
- Keep Python environments, Gradle/JDK tooling, and project dependencies installable and runnable from WSL
- Treat WSL as the canonical development and execution environment for agent-driven work unless explicitly stated otherwise
- Prefer repositories and virtual environments located on the Linux filesystem, not under `/mnt/c/...`

---

## 19) Risks and mitigations

### Risk 1
V2 Kotlin kernel still misses 500k training SPS.

**Mitigation:**  
Treat Kotlin V2 as the first salvage step. If batch APIs + flat buffers + reduced runtime shape still miss the gate, port only the fast kernel to a native extension while keeping the same action/obs/terminal contract.

### Risk 2
V2 diverges from the RSPS-backed headed demo path or oracle/reference behavior.

**Mitigation:**  
Make parity trace comparison a blocking gate before trainer switchover, and keep the headed path pinned to the same mechanics contract and canonical starter-state rules.

### Risk 3
The RSPS-backed trim introduces hidden client/server coupling breakage or expands into an uncontrolled general-RSPS fork.

**Mitigation:**  
Keep stock `void-client` first, preserve login/JS5/protocol/runtime plumbing, prefer gating over deep pruning, and keep the headed scope constrained to Fight Caves-only bring-up and validation.

### Risk 4
Reward hacking in shaped training.

**Mitigation:**  
Always run sparse eval and replay-based validation alongside shaped training.

### Risk 5
The codebase becomes even larger.

**Mitigation:**  
Do not replace everything. Reassign roles:
- V1 = oracle/reference
- V1.5 = RSPS-backed headed demo path
- `fight-caves-demo-lite` = frozen fallback/reference
- V2 = trainer
- shared contracts = parity spine

That is cleaner than trying to make one stack do everything.

---

## 20) Explicit instructions for the coding agent

1. **Do not delete the current V1 bridge/oracle/reference stack.**
2. **Do not continue optimizing V1 as if it will become the final 500k SPS trainer.**
3. **Freeze `fight-caves-demo-lite` as fallback/reference and do not continue the old PR 7.2 path.**
4. **Implement the primary headed demo path inside `RSPS` by trimming/reusing RSPS plus stock `void-client` first.**
5. **Implement a new V2 fast training path beside V1.**
6. **Define parity as mechanics parity, not full runtime parity.**
7. **Move training hot-path semantics into the fast kernel:**
   - action decode
   - flat observation write
   - terminal detection
   - reward feature emission
8. **Keep Python’s train-path work minimal:**
   - write actions
   - call batch step
   - receive buffers
   - vectorized reward weighting if needed
9. **Align the V2 env with PufferLib’s native fast path:**
   - in-place buffers
   - flat spaces
   - async `send/recv`
   - multiple envs per worker
   - no structured data in rollout storage. ([puffer.ai](https://puffer.ai/docs.html?utm_source=chatgpt.com))
9. **Use the existing replay, parity, determinism, and benchmark infrastructure as the validation spine.** fileciteturn15file2 fileciteturn16file0
10. **Keep the RSPS-backed headed demo Fight Caves-only and prefer gating over aggressive first-pass pruning.**
11. **Do not make the visual/demo environment a blocker for V2 training throughput.**
12. **Make 500k training SPS a hard measured gate, not an aspiration.**
13. **Assume WSL is the canonical agent environment.**
    All scripts, paths, tooling setup, benchmarks, and runtime commands must be authored for WSL/Linux unless an exception is explicitly documented.
---

## 21) Bottom line

The path forward is:

- **salvage** the current system
- **reassign its role**
- **build a smaller V2 trainer beside it**
- **build the primary headed demo path on top of `RSPS` plus stock `void-client`**
- **keep `fight-caves-demo-lite` frozen as fallback/reference**
- **use the current simulator as oracle/reference**
- **enforce mechanics parity through trace tests**
- **align the new trainer with PufferLib’s native fast-path design**

That gives you the best chance of satisfying all four high-level goals at once:
- train Fire Cape successfully
- preserve demoability through a near-in-game RSPS-backed headed path
- preserve enough parity for transfer
- actually hit the throughput target with PufferLib in the loop. fileciteturn15file2 fileciteturn16file0 ([puffer.ai](https://puffer.ai/docs.html?utm_source=chatgpt.com))

If you want, I can turn this into a **copy-paste coding-agent prompt** that instructs the agent to implement Phase 0 and Phase 1 first.
