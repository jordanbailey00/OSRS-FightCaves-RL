# RSPS + void-client Integration Plan

## 1. Recommended architecture

### Recommendation

Use **stock `void-client` against a trimmed Fight Caves-only RSPS runtime** for the first implementation milestone.

### Why this is the best first move

- RSPS already owns the real Fight Caves mechanics and instance flow.
- `void-client` already owns the real headed UI/assets/rendering path.
- The lowest-risk trim is server-side:
  - direct login to a demo account
  - immediate canonical starter-state enforcement
  - immediate Fight Caves instance bootstrap
  - world access gated off
- This preserves the ability to validate against the existing RSPS Fight Caves scripts instead of inventing a second mechanics path.

### When a client modification becomes justified

Move to a lightly modified client only if the stock client becomes a usability blocker because of:

- manual login friction
- the need to hide unused tabs/panels
- the need for a stronger "straight into cave" presentation

That should be a second-step convenience change, not the first architecture decision.

## 2. Recommended module/path name

### Recommended product/path name

`fight-caves-demo-rsps`

### Recommended code placement strategy

Do **not** create another standalone workspace-root demo engine beside `fight-caves-demo-lite`.

Instead:

- treat `fight-caves-demo-rsps` as the new headed demo path name
- implement the server/runtime side inside `RSPS`, using:
  - a demo-specific entrypoint/profile
  - a trimmed content/module bootstrap
  - a canonical Fight Caves episode initializer
- keep the external client checkout at:
  - `/home/jordan/code/RSPS/void-client`

Reason:

- the true runtime owner is RSPS
- the true client owner is `void-client`
- a brand new parallel workspace module would mostly duplicate wiring instead of simplifying it

## 3. Source-of-truth split

| Concern | Owner | Notes |
|---|---|---|
| Core Fight Caves mechanics | RSPS server/runtime | Reuse existing Fight Caves, Jad, healer, wave, movement, and combat logic. |
| Canonical starter-state contract | Headless contract in `/home/jordan/code/RL/RLspec.md`, enforced server-side in RSPS demo mode | Headless remains the contract authority; RSPS demo mode must conform to it. |
| Headed runtime boot | RSPS demo-specific entry/profile | Login, spawn, region load, instance creation, and world gating live here. |
| Headless parity contract | Headless Fight Caves environment | The headed path must be validated against the headless contract, not the other way around. |
| Assets/UI/widgets/gameframe | `void-client` | Use stock client UI/assets first. |
| Replay/demo path | New RSPS-backed headed path `fight-caves-demo-rsps` | Separate from frozen `fight-caves-demo-lite`. |
| Training path | Headless environment + RL repo + PufferLib | No change: training remains headless. |

## 4. Minimal viable trimmed RSPS/Void demo

The smallest worthwhile milestone is:

1. Start trimmed RSPS demo mode.
2. Connect with stock `void-client`.
3. Login with any username/password using existing account-creation behavior or demo account provisioning.
4. Immediately force the canonical episode-start state on the server.
5. Immediately spawn the player into the Fight Caves instance and start wave 1.
6. Open the normal gameframe with working combat, inventory, prayer, movement, hitsplats, and projectiles.
7. Block broader world access and non-Fight-Caves traversal/content.
8. Emit audit/starter-state logs so headed state can be compared to the headless contract.

This slice is enough to prove:

- real headed client fidelity
- real UI/widget path
- real RSPS Fight Caves mechanics reuse
- real starter-state enforcement seam
- real client/server compatibility

## 5. Runtime environment recommendation

### Recommended runtime

**Hybrid setup: WSL/Linux RSPS server + native Windows `void-client`.**

### Why

- The workspace and RL/headless docs already declare Linux/WSL as canonical.
- RSPS server/runtime and future training/parity work fit that model cleanly.
- `void-client` is an old Java Swing/AWT desktop application and is likely simpler to run on native Windows JDK than through WSLg.
- This keeps server-side implementation Linux-canonical while still delivering the headed GUI on the Windows desktop.

### Secondary option

WSLg for the client is plausible, but it should be treated as a fallback/dev convenience path, not the recommended default.

## 6. Key risks and unknowns

- **Client/server coupling risk:** stock client expects normal JS5/login/region/interface/container packet behavior; trimming too aggressively on the RSPS side will break the client.
- **Bootstrap complexity risk:** "login straight into Fight Caves" is easy conceptually, but it must be layered onto the existing account/session/region-load path without creating reconnection or persistence bugs.
- **Cache/build friction risk:** the local `void-client` repo has no checked-in build system, only source plus `clientlibs.jar`.
- **Broad data-pruning risk:** items, NPCs, interfaces, vars, animations, and graphics are cross-referenced heavily; deleting data is riskier than selective loading/gating.
- **Starter-state drift risk:** persisted saves can leak unrelated state unless demo mode overrides inventory/equipment/resources on every episode start.
- **UI trimming risk:** the stock gameframe opens many standard tabs. Deep UI cleanup may require more client work than the first pass should take on.
- **Parity drift risk:** if the RSPS demo adds custom convenience logic outside the shared contract, it can drift away from headless semantics.
- **Testing risk:** the first milestone is highly integration-heavy and depends on both server and client behavior, not just server unit tests.

## 7. What Phase 7 should become

### Recommendation

Phase 7 should be re-scoped from "`fight-caves-demo-lite` implementation" to **RSPS-backed headed demo bring-up**.

Suggested shape:

- **Phase 7A:** Freeze `fight-caves-demo-lite`, stand up `fight-caves-demo-rsps` planning/bootstrap path, add demo-specific RSPS entry/profile, keep stock `void-client`.
- **Phase 7B:** Enforce canonical starter state and Fight Caves-only world gating on the RSPS side.
- **Phase 7C:** Add optional client convenience changes only if stock-client bring-up is too awkward.

### Freeze recommendation

`fight-caves-demo-lite` should be formally frozen as:

- fallback reference
- prior headed validation artifact source
- parity/debug comparison target during the RSPS-backed bring-up

### PR structure recommendation

Do not continue the current PR 7.2+ line as if `fight-caves-demo-lite` were still the primary headed target.

Instead:

- branch Phase 7 into a new RSPS-backed headed-demo track
- keep old `fight-caves-demo-lite` PR history as frozen reference
- treat this as a real plan detour that delays the rest of the pivot

### Delay note

This detour **does delay the rest of the pivot plan**. That should be made explicit in the next planning update, because the headed demo path is changing architecture, repo ownership, and validation approach.

## Final recommendation

The correct first pass is not a custom new client and not more work on `fight-caves-demo-lite`. It is:

- freeze `fight-caves-demo-lite`
- create a new RSPS-backed headed path named `fight-caves-demo-rsps`
- keep the first client pass stock
- do the real trimming and starter-state enforcement inside RSPS
- keep headless as the authoritative contract and training path
