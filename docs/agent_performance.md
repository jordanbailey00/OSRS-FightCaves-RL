# Agent Performance

## Current Snapshot

- Status date: `2026-03-24`
- Current agent-performance source of truth step: `TRAIN2`
- Baseline run:
  - train config: `/tmp/train2/train2_native_mlp64_behavior_v1.yaml`
  - checkpoint: `/tmp/train2/data/fc-rl-train-1774402467-0e951ac9a73b.pt`
  - train backend: `native_preview`
  - learner device: `cuda`
  - total timesteps: `1,048,576`
- Evaluation set:
  - `bootstrap_smoke`
  - `parity_reference_v0`
  - total evaluated seeds: `5`
  - eval step cap: `2048`
- Current authoritative behavior baseline:
  - best wave reached: `1`
  - typical wave reached: `1`
  - mean wave reached: `1.0`
  - cave completions: `0 / 5`
  - player deaths: `0 / 5`
  - stalled/no terminal within eval cap: `5 / 5`
  - dominant action pattern: repeated `attack_visible_npc`
- PR8b status: `blocked`

## Current Status

- The current native stack is usable for real training/debugging work:
  - a real checkpoint can be trained,
  - the checkpoint can be evaluated on the native backend,
  - replay packs can be exported,
  - the shared-backend native viewer can inspect the resulting behavior.
- TRAIN2 improved the specific TRAIN1 failure mode:
  - the `eat_shark` collapse is gone,
  - sharks and prayer potions now remain untouched through the eval horizon.
- The current agent behavior is still not useful yet:
  - it never clears wave 1 on the supported eval seeds,
  - it now collapses into `attack_visible_npc` from the reset state,
  - those attacks are rejected at `tick=0` because no visible targets are present in the reset-state policy observation,
  - the episode then never advances into productive combat behavior.

## Progression Summary

- Bootstrap pack:
  - seed `11001`: wave `1`, remaining `1`, terminal `none`, steps `2048`
  - seed `22002`: wave `1`, remaining `1`, terminal `none`, steps `2048`
- Parity-reference pack:
  - seed `11001`: wave `1`, remaining `1`, terminal `none`, steps `2048`
  - seed `33003`: wave `1`, remaining `1`, terminal `none`, steps `2048`
  - seed `44004`: wave `1`, remaining `1`, terminal `none`, steps `2048`

## Behavioral Strengths

- The trained checkpoint is stable enough to run deterministically through the native eval path and viewer tooling.
- The policy does not crash the backend, trigger invalid-state terminals, or depend on legacy JVM execution paths.
- The agent remains alive through the inspected early steps, which means the current stack is good enough to debug policy behavior directly.
- TRAIN2 shows that narrow behavior interventions do change the learned dominant action pattern, so the stack is responsive enough for behavior-focused iteration.

## Behavioral Failures

- The policy now collapses to action id `2` (`attack_visible_npc`) from the reset state on every evaluated seed.
- The reset-state replay shows `visible_target_count = 0`, so the attack is rejected with `invalid_target_index` before any world progression occurs.
- The player position stays fixed on the dais; there is no meaningful movement or successful attack behavior.
- The episode remains at `tick = 0` and `steps = 0` in viewer inspection even after many replay frames, so the policy is stuck before the productive combat loop begins.
- Wave progression remains completely flat despite the removal of the consumable-spam behavior.

## Viewer And Replay Inspection Notes

- Replay packs inspected:
  - `/home/joe/projects/runescape-rl/codex2/runescape-rl-runtime/artifacts/training-env/rl/eval/train2_eval_native_bootstrap/fc-rl-eval-1774402531-eca49ed21db9/replay_pack.json`
  - `/home/joe/projects/runescape-rl/codex2/runescape-rl-runtime/artifacts/training-env/rl/eval/train2_eval_native_parity_reference/fc-rl-eval-1774402531-91511677930b/replay_pack.json`
- Native viewer replay checks:
  - frame `1` on bootstrap seed `11001` shows `attack_visible_npc` rejected with `invalid_target_index`, `visible_target_count = 0`, `tick = 0`, and no NPCs present in the reset-state snapshot.
  - frame `25` on bootstrap seed `11001` still shows `tick = 0`, `steps = 0`, full HP, full inventory, and the last action still `attack_visible_npc`.
  - frame `25` on parity seed `44004` shows the same failure pattern on a different supported seed.
- Aggregate replay evidence:
  - all `5 / 5` evaluated episodes emitted the same dominant packed action prefix `[2, 0, 0, 0]` for all `2048` recorded steps.

## Limiting-Factor Assessment

- Most likely current limiter: `action space / reset-step sequencing plus policy instability`
  - the policy now prefers the semantically right action family (`attack_visible_npc`), but it applies it at the wrong time from a no-target reset state.
  - the current learner still collapses to a single deterministic action-id head value instead of learning the simple `wait -> attack` sequence that yields high reward on wave 1.
- Secondary possibility: `reward design`
  - TRAIN2’s reward changes were enough to remove consumable spam, but they were not enough to guide the policy into the productive two-step sequence.
- Less likely primary limiters for this baseline:
  - `observation quality`: the reset-state observation is consistent with the viewer and replay outputs; the problem is what the policy does with it.
  - `mechanics understanding`: the policy still does not reach meaningful combat interaction.
  - `throughput`: the current throughput class is still not high enough for PR8b, but it is already sufficient to produce and inspect real failing checkpoints.

## Performance History / Change Log

### 2026-03-24 — TRAIN1

- Benchmark and training shape:
  - native CUDA training path
  - `train_fast_v2_mlp64`-derived config
  - `1,048,576` timesteps
  - eval on `bootstrap_smoke` and `parity_reference_v0`
- Previous behavior baseline:
  - none; first real checkpoint baseline
- New behavior baseline:
  - best wave: `1`
  - typical wave: `1`
  - cave completions: `0 / 5`
  - dominant behavior: repeated `eat_shark`
- Explanation:
  - the full native training/eval/viewer stack is now active and usable,
  - but the current trained policy is behaviorally collapsed and not yet solving the first wave.

### 2026-03-24 — TRAIN2

- Benchmark and training shape:
  - native CUDA training path
  - same `train_fast_v2_mlp64`-derived config shape as TRAIN1
  - same `1,048,576` timesteps
  - eval on `bootstrap_smoke` and `parity_reference_v0`
- Previous behavior baseline:
  - best wave: `1`
  - dominant behavior: repeated `eat_shark`
- New behavior baseline:
  - best wave: `1`
  - dominant behavior: repeated `attack_visible_npc`
  - cave completions: `0 / 5`
  - player deaths: `0 / 5`
- Explanation:
  - the specific consumable-spam collapse improved,
  - but it was replaced by an invalid attack loop at the reset state,
  - so progression remains flat even though the failure mode is now more diagnostically useful.

## Current Recommendation

- Do not resume the narrow performance track as the immediate next move.
- Training behavior work is temporarily paused while the native viewer is brought up to the required manual-debug and demo-readability standard.
- The immediate implementation priority is the viewer-completion sequence, with DV4c now complete and DV4d next before DV5.
- DV4d, DV5, and DV6 remain the viewer-completion sequence before training behavior work resumes.
- When training work resumes, the next behavior-focused pass should still target reset-step sequencing rather than consumable spam:
  - inspect whether the policy should be guided or masked away from `attack_visible_npc` while `visible_target_count == 0`,
  - audit the reset-state observation and curriculum assumptions around the first productive action,
  - verify whether the correct early behavior is explicitly learnable as `wait -> attack`,
  - rerun training after that reset-step behavior intervention and compare against both TRAIN1 and TRAIN2.
