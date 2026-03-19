# RSPSplan.md

## Documentation Status

- Status: superseded for active execution under `pivot_documentation_triage.md`.
- Current authority: `RSPSspec.md`, `pivot_plan.md`, and `pivot_implementation_plan.md`.
- Retention reason: kept for historical RSPS module context; do not use as the active workspace execution plan.

## Scope and Source of Truth

This plan treats `RSPSspec.md` as the source of truth for the `RSPS` module's role in the workspace.

This is a **minimum viable oracle/reference plan**, not a new gameplay or infrastructure roadmap.

## Current Role

`RSPS` exists in this workspace to provide:
- headed/oracle behavior
- manual validation in the full runtime
- parity dispute resolution
- rare mechanic debugging outside the RL hot path

## Work Chunks

### 1. Document the oracle boundary

Required outcomes:
- module purpose is explicit
- hot-path exclusions are explicit
- dependency direction is explicit

### 2. Document validation responsibilities

Required outcomes:
- headed/manual validation role is explicit
- parity/oracle usage is explicit
- expected interaction points with `fight-caves-RL` and `RL` are explicit

### 3. Keep future work constrained

Required outcomes:
- future RSPS work only expands documentation or oracle/debug workflows unless a separate implementation change is explicitly approved
- RL/runtime semantics remain owned by `fight-caves-RL` and `RL`, not by this plan

## Acceptance Criteria

This plan is satisfied when:
- `RSPSspec.md` and `RSPSplan.md` are no longer placeholders
- the workspace can point to `RSPS` as a documented oracle/reference module without inventing extra scope
- the docs make clear that `RSPS` is outside the RL training hot path
