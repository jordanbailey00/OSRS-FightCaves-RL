# rsps PARITY

## Parity role

`rsps` is the active headed side of train-to-demo parity/trust validation.

## Required parity expectations

- Shared action semantics between headless and headed backend-control paths must stay aligned.
- Target ordering/index semantics must remain stable enough for checkpoint transfer.
- Prayer/consumables/movement/reset/wave progression behavior must remain transfer-compatible.

## Headed trust boundary

- Replay and live checkpoint inference must run through backend-issued control, not manual UI-only interaction.
- Trust decisions require artifact-backed headed evidence (logs/manifests/checklists/reports).

## Known compatibility notes

- Current machine-specific stock-client connectivity path: WSL IP works; `127.0.0.1` does not.
- Fallback paths (`demo-env`, V1 reference flows) remain selectable and should not be removed implicitly.
