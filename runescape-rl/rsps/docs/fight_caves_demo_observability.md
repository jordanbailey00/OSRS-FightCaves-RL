# Fight Caves Demo Observability

This document defines the headed-demo observability artifacts added in PR 7A.6.

These artifacts are demo-profile only.

They exist to make starter-state verification, session debugging, and manual headed validation concrete without requiring a client fork.

## Artifact Root

Default demo artifact root:

- `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/`

Configured by:

- `fightCave.demo.artifacts.path`

Current default profile setting lives in:

- `/home/jordan/code/RSPS/game/src/main/resources/fight-caves-demo.properties`

## Artifact Layout

### Structured session logs

Path pattern:

- `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/session_logs/<session-id>.jsonl`

Purpose:

- append-only structured headed session log for one demo login session

Current event types:

- `session_started`
- `entry_requested`
- `leave_requested`
- `world_access_violation`
- `starter_state_manifest_written`
- `episode_reset`

## Starter-state manifests

Path pattern:

- `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/starter_state_manifests/<session-id>_reset_<nnn>.json`

Purpose:

- exact server-owned snapshot of the canonical demo starter state written after each Fight Caves demo reset

Contents include:

- account and session id
- reset cause
- seed / wave / rotation / instance id
- player tile
- run energy and run toggle
- prayer flags
- equipment
- inventory
- blocked skill state
- artifact path references

## Validation checklists

Path pattern:

- `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/validation_checklists/<session-id>.md`

Purpose:

- per-session headed validation checklist combining:
  - auto-observed items
  - manual follow-up items
  - artifact references
  - event counters

Auto-observed items currently track:

- session log creation
- demo entry logging
- starter-state manifest creation
- leave/reset request logging
- world-access violation logging

## Existing audit TSV logs

Path:

- `/home/jordan/code/RSPS/data/fight_caves_demo/logs/`

Purpose:

- existing `AuditLog` TSV output remains enabled alongside the new demo-specific artifacts

Use this for:

- coarse audit trail
- correlating structured demo files back to broader RSPS server events

## Emission Seams

Current demo observability hooks are attached only to existing demo seams:

- demo-profile login bootstrap
- Fight Caves demo entry via object enter
- Fight Caves demo spawn bootstrap
- demo leave/recycle
- demo death reset
- demo world-access violation on cave exit / teleport-out
- post-reset canonical starter-state enforcement

## Scope Note

These artifacts exist to support the later headed trust gate with concrete evidence, not to replace it.
