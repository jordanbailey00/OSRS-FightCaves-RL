# Active Changelog (Last 14 Days)

Rolling summary of important changes and decisions.  
Window: 2026-03-05 through 2026-03-19.

## 2026-03-19

- Phase 9.5 completed with a bounded trainer/policy compute-cost sweep.
- Phase 10 default config selected explicitly:
  - benchmark: `fast_train_v2_mlp64`
  - train: `train_fast_v2_mlp64`
- Rejected bounded candidates:
  - `fast_train_v2_lstm64`
  - `fast_train_v2_mlp128`
- Selected `mlp64` candidate improved bounded train benchmark throughput and post-selection attribution throughput on the same subprocess V2 packet family.
- Benchmark/manifest SHA capture now degrades to `unknown` instead of failing when the workspace is detached from `.git`, which allowed PR 9.5 artifacts to be produced from the current source tree.

## 2026-03-12

- RSPS-backed headed demo path selected as primary; lite-demo path frozen as fallback/reference.
- Phase 7/8 plan rewritten around RSPS + stock `void-client`, server-side starter-state enforcement, and trust-gate sequencing.
- RSPS demo profile bootstrap, starter-state initializer, and Fight Caves-only gating slices landed.

## 2026-03-12 to 2026-03-14

- Stock client bring-up completed on the trusted topology (WSL-hosted server + native Windows client).
- First-login false `ACCOUNT_DISABLED(4)` path fixed in shared RSPS login/account path.
- Observability/manifests/checklists added for headed demo sessions.
- Non-Jad telegraph regression fixed before headed trust gate closure.
- PR 7A.7 trust gate closed after live rerun confirmed prayer/combat/reset/wave behavior.

## 2026-03-14

- PR 7B.1/7B.2 convenience and continuity improvements landed:
  - direct auto-entry flow
  - stable death/recycle continuity
  - dedicated loading window/progress visibility
  - preserved stock-jar fallback path

## 2026-03-14 to 2026-03-15

- Phase 8.1 completed in sequence:
  - backend-control smoke
  - replay-first integration
  - live checkpoint inference
- Phase 8.2 completed:
  - default training backend kept/switched to `v2_fast`
  - default demo/replay backend switched to trusted RSPS-headed path
  - fallbacks preserved and selectable

## 2026-03-15

- Pivot closure finalized.
- Post-pivot roadmap and implementation plan created (Phase 9+ structure).

## 2026-03-16

- Phase 9 completed:
  - baseline packet (PR 9.1)
  - decomposition (PR 9.2)
  - training-readiness contract freeze (PR 9.3)
- Workspace source/runtime root normalization completed under `/home/jordan/code`.
- Post-flattening hot-path attribution packet completed with function/stage-level evidence:
  - dominant slowdown in trainer model compute
  - transport/obs/action path not primary blockers
