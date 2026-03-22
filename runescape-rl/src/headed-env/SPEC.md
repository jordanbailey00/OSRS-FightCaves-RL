# demo-env SPEC

## Purpose

Preserve the lite-demo path as a fallback/reference module after RSPS-backed headed path became primary.

## Required behavior

- Remain runnable as fallback/reference when explicitly selected.
- Preserve existing historical validation context and starter-state/logging references.
- Avoid becoming the primary headed implementation path unless a future approved decision changes direction.

## Inputs

- explicit operator selection of fallback path
- existing lite-demo configs/assets/source already in this module

## Outputs

- fallback demo run behavior when intentionally launched
- reference artifacts/logs when used

## Invariants

- Module remains frozen/superseded for primary roadmap purposes.
- New primary headed work should not be implemented here.
- Active headed trust claims are owned by `rsps`, not `demo-env`.
