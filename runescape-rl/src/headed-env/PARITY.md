# demo-env PARITY

## Parity status

`demo-env` is no longer the active parity authority.

## Required compatibility expectations

- If selected as fallback, it should remain operationally compatible with its frozen intended behavior.
- It should not be treated as the canonical parity target for train-to-headed transfer decisions.

## Active parity ownership

- Active transfer-critical parity and headed trust ownership are in:
  - `training-env` (headless side)
  - `rsps` (headed side)

## Risk note

- Divergence from newer RSPS-headed behavior is acceptable for this module as long as it remains clearly labeled fallback/reference.
