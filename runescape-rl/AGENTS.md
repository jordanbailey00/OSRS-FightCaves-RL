# AGENTS.md

Repository-level instructions for coding/documentation agents working in `/home/jordan/code/runescape-rl`.

## Behavior rules

- Prefer small, explicit, reviewable changes.
- Do not guess when validated evidence exists in canonical docs or artifacts.
- Keep source root clean; route generated outputs to runtime root.

## Repository working rules

- Source root: `/home/jordan/code/runescape-rl`
- Runtime root: `/home/jordan/code/runescape-rl-runtime`
- Do not write generated runtime artifacts into source directories.
- Use `/scripts/bootstrap.sh` and `/scripts/validate_smoke.sh` as baseline environment/smoke entrypoints.

## Canonical documentation rules

Canonical permanent docs are:

- `/README.md`
- `/AGENTS.md`
- `/docs/active-reference/plan.md`
- `/docs/active-reference/performance.md`
- `/docs/active-reference/changelog.md`
- `/docs/active-reference/testing.md`
- `/docs/source-of-truth/architecture.md`
- `/docs/source-of-truth/contracts.md`
- `/docs/source-of-truth/project_tree.md`
- Module canonical docs:
  - `/rsps/README.md`, `/rsps/SPEC.md`, `/rsps/ARCHITECTURE.md`, `/rsps/PARITY.md`
  - `/demo-env/README.md`, `/demo-env/SPEC.md`, `/demo-env/ARCHITECTURE.md`, `/demo-env/PARITY.md`
  - `/training-env/README.md`, `/training-env/SPEC.md`, `/training-env/ARCHITECTURE.md`, `/training-env/PARITY.md`

## Report vs truth rules

- Reports, audits, one-off analyses, and profiling packets go under `/docs/reports/`.
- Reports are non-canonical unless their conclusions are merged into canonical docs.
- If project truth changes, update canonical docs directly instead of creating new permanent docs.

## Source-of-truth reset rule

- There must be one active plan source, one active performance source, one active testing source, and one active architecture/contracts source.
- Do not leave overlapping docs claiming active authority.
- Superseded or historical docs must be archived or explicitly treated as reports.
