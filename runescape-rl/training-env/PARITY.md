# training-env PARITY

## Parity purpose

`training-env` must maintain transfer-critical compatibility between:

- headless training/eval behavior
- headed RSPS-backed demo behavior

## Required parity areas

- target ordering and target index mapping
- prayer behavior and toggle semantics
- consumable behavior (shark/prayer potion use)
- movement/path timing behavior
- reset/starter-state behavior
- wave progression and completion behavior
- Jad behavior when reached

## Contract expectations

- Shared action semantics must remain stable across headless and headed backend-control paths.
- Observation/action schema changes require explicit contract updates.
- Parity validation should be evidence-backed via replay/live headed artifacts, not assumed.

## Known risk to track

- Throughput work must not introduce semantic drift in action/observation meaning.
- Any mismatch must be documented and assigned rather than hidden in wrapper logic.
