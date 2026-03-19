# Fight Caves Demo RSPS Spec

## A. Purpose

This document is the source-of-truth spec for the new RSPS-backed headed Fight Caves demo path.

This path exists to provide:

- a headed demo environment that is visually and functionally close to the in-game RSPS/OSRS Fight Caves experience
- a manual validation and debugging target that uses the real client UI/assets/rendering path instead of recreating them
- a headed endpoint the RL agent can demo in after training in the headless environment

This path is different from `fight-caves-demo-lite` in one critical way:

- `fight-caves-demo-lite` was a lightweight custom headed module path
- this new path is built by trimming and reusing the existing `RSPS` runtime plus stock `void-client`

`fight-caves-demo-lite` is now frozen as fallback/reference only:

- keep it in the repo
- do not delete it
- do not continue the old PR 7.2 replay/live inference continuation as the primary headed plan

## B. Source-of-truth split

The new headed path uses this ownership split:

| Concern | Owner | Notes |
|---|---|---|
| Headed mechanics/runtime | `RSPS` | Reuse the real server/runtime, Fight Caves content, and packet-driven UI support. |
| Headless training runtime | headless Fight Caves environment | Remains the RL training environment. |
| Canonical starter-state contract | `/home/jordan/code/RL/RLspec.md` | Contract authority lives in the headless spec; headed RSPS must conform to it. |
| Starter-state enforcement in the headed path | `RSPS` server-side Fight Caves runtime | Must be enforced server-side, not by generic login and not by the client. |
| Client UI/assets/render/gameframe | `void-client` | Use stock client first. |
| Replay/demo path | RSPS-backed headed path | This is the new primary headed path once it proves trustworthy. |
| PufferLib/training/orchestration | `RL` repo | No change. |

`fight-caves-demo-lite` does not own the headed path anymore. It remains a frozen fallback/reference module and historical artifact source.

## C. Runtime topology

### First-pass topology

- `RSPS` server/runtime runs in WSL/Linux
- stock `void-client` runs on native Windows JDK
- the client connects to the RSPS server over the normal RS2-era protocol surface

### First-pass stock-client artifact

The preferred first-pass runtime artifact is the prebuilt stock `client.jar` from the `void-client` release path.

The checked-out `/home/jordan/code/RSPS/void-client/` repo is still important, but for this milestone it is primarily:

- source/reference for ownership and boot-path inspection
- a fallback IntelliJ artifact-build path if a local jar is needed

It is not the default CLI build path for the first milestone.

Current workspace verification found that a direct JDK 21 CLI compile of the checked-out client source is not clean because the source still references older/internal Java APIs. That does not block stock-client reuse, but it does mean the first bring-up path should standardize on the release jar rather than a new local source-build workflow.

### Why this topology is preferred

- WSL/Linux remains the canonical runtime environment for agent-driven work and for the headless/training side
- the RSPS server/runtime already fits the Linux-canonical workflow
- `void-client` is an old Java desktop client and is likely lower-friction on native Windows than through WSLg
- this preserves Linux-canonical server ownership without forcing a Windows-only server/runtime plan

### Connection assumptions to preserve

The first pass keeps the stock client assumptions intact:

- login
- JS5/file-server
- region and dynamic-region updates
- interfaces
- containers
- vars
- player/NPC updates
- graphics/projectiles

This is why the first pass prefers server-side trimming/gating over a custom client fork.

## D. Canonical starter-state ownership

The canonical episode-start state is defined in:

- `/home/jordan/code/RL/RLspec.md`
- section `7.1.1 Episode-start state contract`

The headed path must enforce that state server-side inside the Fight Caves runtime path.

### Required enforcement seams

The cleanest server-side enforcement seams are:

- `TzhaarFightCave.objectOperate("Enter", "cave_entrance_fight_cave")`
- `TzhaarFightCave.playerSpawn`

### State that must be enforced server-side

- equipment
- ammo type and count
- inventory contents and potion doses
- skills and current resource state
- HP / Constitution
- prayer
- run energy
- run toggle
- no XP gain
- spawn/position
- wave/start constants

### Enforcement rules

- do not rely on generic login bootstrap for canonical starter state
- do not rely on client-side substitutions
- clear or overwrite persisted-save drift on every headed episode start
- document ownership for each field in implementation notes and manifests

## E. Fight Caves-only scope

### What is blocked

The first pass must prevent:

- broader world traversal
- unrelated content access
- using the headed path as a general RSPS runtime

### What must still remain alive

The following generic systems still need to remain active because the stock client and real Fight Caves play depend on them:

- login and JS5/file-server
- region loading / dynamic zones / instances
- player/NPC update pipeline
- inventory/equipment
- prayers
- ranged combat/ammo/projectiles
- movement/pathing
- hitsplats/graphics
- interface/container/var packets
- Fight Caves data/config path

### Why gating is preferred over deep pruning first

Broad pruning of cache/data/interfaces is risky because of client/server coupling and implicit cross-references.

First pass should therefore prefer:

- demo-specific bootstrap/profile
- server-side gating
- selective module loading

and should avoid:

- broad asset deletion
- aggressive interface pruning
- custom client trimming before the stock-client path works

## F. Validation requirements

The RSPS-backed headed path is not trustworthy until it proves all of the following:

- starter-state parity against `/home/jordan/code/RL/RLspec.md`
- direct entry into Fight Caves after login
- no broader world access
- playable combat/prayer/inventory loop
- wave progression
- restart/death/completion handling
- sufficient logs/manifests for debugging

### Required validation artifacts

- starter-state manifest
- session logs
- Fight Caves entry/reset/leave events
- world-access violation and gating logs
- manual validation checklist

### Current artifact locations

- starter-state manifests:
  - `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/starter_state_manifests/`
- structured session logs:
  - `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/session_logs/`
- validation checklists:
  - `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/validation_checklists/`
- existing audit TSV logs:
  - `/home/jordan/code/RSPS/data/fight_caves_demo/logs/`

Artifact layout details are documented in:

- `/home/jordan/code/RSPS/docs/fight_caves_demo_observability.md`

### Trust-gate rule

Do not switch the default demo/replay backend to this path until the headed trust gate passes.

That means:

- no automatic Phase 8 switchover assumption
- no replay/default-target assumption just because the server boots

## G. Risk register

### 1. Client/server coupling

Risk:
- trimming RSPS too aggressively breaks stock `void-client` expectations

Mitigation:
- keep login/JS5/protocol/runtime plumbing intact
- prefer gating over deep pruning

### 2. `void-client` build/run friction

Risk:
- the local `void-client` repo has source plus support jar, but no checked-in modern build wrapper

Mitigation:
- use stock client first
- treat client edits as optional later work
- keep initial proof focused on server/runtime reuse
- use the release `client.jar` as the first-pass runtime artifact and keep the local checkout as source/reference or IntelliJ artifact-build fallback

### 3. Cache/data/interface pruning risk

Risk:
- deleting "unrelated" definitions may break shared client/runtime dependencies

Mitigation:
- do not front-load deep data pruning
- trim by bootstrap/profile and gating first

### 4. Persisted-save leakage

Risk:
- saved player state leaks into headed demo episodes and breaks the canonical contract

Mitigation:
- make the server-side Fight Caves episode initializer authoritative
- overwrite starter-state fields on every episode start

### 5. Parity drift risk

Risk:
- the RSPS-backed headed path drifts from the headless RL contract

Mitigation:
- tie starter-state ownership to `RLspec.md`
- validate via manifests, logs, and parity/trust gates before any default-switchover
