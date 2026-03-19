# Post-Pivot Plan

## Purpose

The pivot is finished. The workspace now has:

- a default V2 fast headless training path
- a trusted RSPS-backed headed demo/replay path
- preserved fallback/reference paths for lite-demo and V1 oracle/debug use

The new problem space is no longer architecture convergence. It is system proof:

- what is the real current performance of the default V2 path
- does the agent actually learn useful Fight Caves behavior
- do trained checkpoints transfer credibly into the headed RSPS-backed path
- what should be optimized, trimmed, or refactored only after the winning path is proven

This roadmap is intentionally evidence-first. It does not reopen the pivot, and it does not start with cleanup.

## Post-Pivot Source-Of-Truth Split

The pivot-established ownership split remains active:

- `fight-caves-RL`
  - oracle/reference mechanics
  - parity reference behavior
  - headless runtime semantics
- `RSPS`
  - trusted headed runtime mechanics for the headed demo path
  - headed reset/state/runtime behavior
- `void-client`
  - headed UI, rendering, assets, and gameframe
- `RL`
  - PufferLib training
  - backend selection
  - evaluation and replay tooling
  - analytics, checkpoints, and benchmarks

Post-pivot work should preserve that split unless a future evidence-backed decision explicitly changes it.

## Post-Pivot Principles

- do not start with cleanup
- do not start with broad refactors
- do not optimize before measurement truth exists
- do not assume learning works just because the pivot completed
- do not assume train-to-demo transfer works just because Phase 8 integration completed
- use explicit evidence gates for performance, learning, and transfer
- keep fallback/reference paths until the default system is actually proven
- separate performance proof, learning proof, transfer proof, and cleanup

## Canonical Measurement And Proof Questions

The post-pivot roadmap must answer these in order:

1. What does `~500,000 SPS` mean operationally on the default training path?
2. What benchmark profile and methodology are canonical?
3. What counts as proving that the agent is actually learning?
4. What counts as proving that train-to-demo transfer works?
5. When is cleanup/refactor allowed to begin?
6. What fallback/reference paths must remain preserved until proof is complete?

## Canonical Benchmark Starting Point

Initial post-pivot measurement should start from the current default V2 benchmark surfaces:

- env-only benchmark profile:
  - `/home/jordan/code/RL/configs/benchmark/fast_env_v2.yaml`
- end-to-end training benchmark profile:
  - `/home/jordan/code/RL/configs/benchmark/fast_train_v2.yaml`

The methodology must stay source-of-truth oriented:

- run on the approved Linux/WSL source-of-truth environment first
- keep env-only and end-to-end training throughput separate
- record vecenv topology, runner stage seconds, trainer bucket totals, and memory
- use repeatable benchmark packets rather than one-off console claims

Operational interpretation:

- env-only goal remains `>= 1,000,000 env steps/sec`
- training goal remains `>= 500,000 production training env-steps/sec`
- the training goal is not satisfied by env-only numbers or replay throughput

## Phase Structure

## Phase 9 - Training Readiness And Benchmark Truth

Purpose:

- establish the real current performance of the default V2 path
- define the canonical benchmark methodology
- verify the default training config, reward path, curriculum path, and logging/manifest surfaces are actually ready for serious runs

Why first:

- without benchmark truth, performance work is blind
- without readiness checks, learning results are hard to interpret

Phase 9 exit:

- current SPS is measured honestly
- the gap to `~500,000 SPS` is quantified
- dominant bottlenecks are ranked
- training readiness metrics/artifacts are frozen

## Phase 10 - Learning Proof

Purpose:

- prove that the agent can learn meaningful Fight Caves behavior on the default V2 path
- move from smoke training to milestone-driven learning evidence

Why second:

- the system should prove learning before demo polish, cleanup, or large optimization bets
- training efficacy is the real question after architecture convergence

Phase 10 exit:

- a fixed evaluation ladder exists
- checkpoint progression is measurable
- meaningful learning is proven beyond random or degenerate survival
- a promotable checkpoint exists for headed demo proof

## Phase 11 - End-To-End Headed Demo Proof

Purpose:

- prove train -> checkpoint -> headed RSPS-backed demo works credibly
- verify the semantics that matter to user trust and policy transfer

Why after learning proof:

- a headed demo of a non-learning checkpoint is not a useful system proof
- transfer validation should use a checkpoint with credible headless behavior

Phase 11 exit:

- a real trained checkpoint drives the headed demo credibly
- transfer-critical semantics are validated
- headed behavior is explainable through artifacts/logs rather than guesswork

## Phase 12 - Performance Hardening

Purpose:

- improve throughput only after measurement and learning evidence exist
- fix ranked bottlenecks in priority order

Why after benchmark truth and learning proof:

- optimizing before learning proof risks tuning the wrong system
- optimizing before transfer proof risks breaking what was actually trustworthy

Phase 12 exit:

- meaningful bottleneck fixes are implemented with before/after evidence
- the gap to the `~500,000 SPS` goal is reduced or a deeper escalation decision is justified by data

## Phase 13 - Cleanup / Prune / Refactor

Purpose:

- simplify the workspace only after the winning path is proven
- clean up dead docs/scripts/configs and archive stale paths carefully

Why last:

- cleanup before proof risks deleting useful fallback/debug/reference paths
- refactor before proof risks obscuring the real system bottlenecks

Phase 13 exit:

- dead files and stale paths are audited
- safe removals/archives are executed deliberately
- refactors are limited to proven winning-path pain points

## Learning Milestone Ladder

The roadmap should treat learning as a ladder, not a binary:

- early-wave proof
  - agent survives and progresses beyond trivial opening behavior
- later-wave proof
  - agent reaches materially later waves consistently enough to show non-random learning
- Jad reach proof
  - agent reaches Jad in evaluation with repeatable evidence
- completion proof
  - agent earns at least one fire cape on the fixed evaluation surface, then improves repeatability

These are milestone layers, not a license to skip earlier evidence.

## Transfer-Critical Parity Scope

Post-pivot parity work should stay targeted.

The semantics that matter most for train-to-demo trust are:

- visible-target ordering and target resolution
- prayer book and prayer toggles
- consumable use and lockouts
- movement and path timing
- episode reset and canonical starter-state enforcement
- wave progression and recycle behavior
- Jad behavior when the policy reaches Jad-relevant situations

Broad abstract parity work that does not help transfer trust should stay out of the critical path.

## Preservation Rule Before Cleanup

These should remain preserved until the default post-pivot system is genuinely proven:

- `fight-caves-demo-lite`
- V1 oracle/reference/debug replay path
- explicit V1 config-based training/benchmark fallbacks
- the current headed validation artifacts and runbooks

Cleanup or removal work must not begin until the benchmark truth, learning proof, and headed transfer proof gates are all satisfied.

## What This Roadmap Is Not

This roadmap is not:

- a new pivot
- a cleanup-first plan
- a broad refactor-first plan
- a claim that the system is already fast enough
- a claim that the system already learns well enough
- a claim that the headed demo is already proven for real trained behavior

It is the roadmap for proving those things.
