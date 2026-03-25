# Agent Performance — Living Doc

Last updated: 2026-03-25 | Branch: v2Claude | Post-PR8a

## Current Agent State

**No trained agent exists yet.** Training is blocked until viewer completion (PR8a-c + PR9 + PR10). Training pipeline is PR 11 in the current plan. This doc will be populated when training begins.

## Metrics to Track

Once training is active, this doc will track:

| Metric | Description |
|--------|-------------|
| Wave reached (mean/max/distribution) | How far the agent gets in Fight Caves |
| Jad kills | Fraction of episodes that defeat Jad |
| Jad prayer accuracy | Correct prayer switch rate during Jad telegraph |
| Episode length (ticks) | Mean/median/max ticks per episode |
| Death wave distribution | Which waves the agent dies on most often |
| DPS efficiency | Damage dealt per tick vs theoretical max |
| Resource efficiency | Sharks/doses used vs available, conservation rate |
| Cave completion rate | Fraction of episodes clearing all 63 waves |

## Baseline Comparisons

| Baseline | Description | Expected Performance |
|----------|-------------|---------------------|
| Random policy | Uniform random actions (masked) | Wave 1-3, immediate death |
| Idle policy | All-zeros actions every tick | Wave 1, death from NPC melee |
| Prayer-only scripted | Correct prayer switching, no attack/move | Survives longer vs melee NPCs, dies to resource depletion |

Baselines will be measured when PR 8 training pipeline is operational.

## Regression Log

No regressions recorded (no agent exists yet).

## History

| Date | Checkpoint | Change | Impact |
|------|------------|--------|--------|
| — | — | No training runs yet | — |
