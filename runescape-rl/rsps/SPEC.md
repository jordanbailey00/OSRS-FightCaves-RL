# rsps SPEC

## Purpose

Provide the trusted headed RSPS-backed Fight Caves runtime and demo path.

## Required behavior

- Support headed Fight Caves demo profile bring-up.
- Enforce canonical starter-state/reset behavior in server/runtime path.
- Preserve stock-client protocol surfaces required for headed play.
- Support backend-controlled headed actions for replay/live inference flows.
- Emit headed observability artifacts used by trust-gate validation.

## Inputs

- RSPS runtime config/profile selection.
- headed client connection and login flow.
- backend-issued actions for headed replay/live inference where applicable.

## Outputs

- headed in-game Fight Caves runtime behavior.
- session logs/manifests/checklists and validation artifacts.

## Invariants

- Headed mechanics ownership remains server/runtime-owned.
- No client-side semantics substitution for starter-state contract.
- Fight Caves-only gating stays in place for the headed demo profile.
