# E2E Acceptance - Fight Caves Headless 1:1 Parity

## Documentation Status

- Status: archive candidate under `pivot_documentation_triage.md`.
- Current authority: `pivot_plan.md` and `pivot_implementation_plan.md`.
- Retention reason: kept temporarily for historical extraction-era acceptance context; do not treat it as the active pivot acceptance gate.

Run this suite only after all `FCplan.md` steps are complete.

This is the final gate for acceptance.

---

## 0) Global Preconditions

All must be true before running E2E:
1. `FCspec.md` is current and approved.
2. `FCplan.md` steps 0-13 are marked complete.
3. Headless and oracle runtimes build successfully.
4. Required deterministic trace pack exists.
5. CI and local environment use identical code revision.

Required trace pack (minimum):
1. `trace_idle_500.json` (WAIT only)
2. `trace_movement_edges.json` (boundary/pathing stress)
3. `trace_consume_lockouts.json` (eat/drink timing)
4. `trace_prayer_switches.json` (protection toggles)
5. `trace_full_run_seeded.json` (full run action trace)
6. `trace_jad_healer_trigger.json` (Jad threshold and healer behavior)
7. `trace_tzkek_split.json` (split behavior focus)

---

## 1) Mandatory Command Sequence

Run in order:
1. `./gradlew clean`
2. `./gradlew :game:test`
3. `./gradlew :game:test --tests "*Headless*"`
4. `./gradlew :game:test --tests "*Parity*"`
5. `./gradlew :game:test --tests "content.area.karamja.tzhaar_city.TzhaarFightCaveTest"`
6. `./gradlew :game:headlessDistZip`
7. `./gradlew :game:e2eTest`

If any command fails, stop and resolve before proceeding.

---

## 2) E2E Test Suites

## E2E-01 Build and Startup Integrity

Objective:
- Verify both oracle and headless modes boot and tick.

Procedure:
1. Start oracle runtime in test mode.
2. Start headless runtime in deterministic mode.
3. Execute 100 ticks in each with no actions.

Assertions:
1. No startup exceptions.
2. No missing definition/script errors.
3. Tick loop advances and exits cleanly.

Artifacts:
1. startup logs for oracle and headless.
2. tick summary logs.

---

## E2E-02 Deterministic Replay Identity (Headless internal)

Objective:
- Verify headless deterministic replay contract.

Procedure:
1. For each trace in trace pack, run headless replay twice with same seed.
2. Compare per-tick snapshots.

Assertions:
1. Snapshot streams are byte-for-byte identical.
2. Terminal outcomes are identical.
3. RNG call counters are identical (if enabled).

Artifacts:
1. replay output hash files.
2. per-trace pass/fail report.

---

## E2E-03 Oracle vs Headless Tick Parity

Objective:
- Verify 1:1 behavior against oracle.

Procedure:
1. For each trace and seed pair, run oracle and headless side-by-side.
2. Compare snapshots tick-by-tick with fail-fast diff.

Assertions:
1. No divergence for mandatory scenarios.
2. Matching player state, wave state, NPC state, combat outcomes, and termination state.

Artifacts:
1. parity report JSON.
2. first-diff dump files (must be empty on pass).

---

## E2E-04 Full Fight Caves Completion Parity

Objective:
- Validate full 63-wave end-to-end parity.

Procedure:
1. Run seeded full-run trace through oracle and headless.
2. Validate wave progression events and final reward outcome.

Assertions:
1. All 63 waves progress in matching order.
2. Jad wave behavior matches.
3. Completion state and rewards match expected logic.

Artifacts:
1. wave event logs.
2. final state summary for both runtimes.

---

## E2E-05 Special Mechanics Parity

Objective:
- Validate Fight Caves-specific mechanics.

Procedure:
1. Run `trace_tzkek_split.json` scenario.
2. Run `trace_jad_healer_trigger.json` scenario.
3. Run prayer-drain scenario for Tz-Kih.

Assertions:
1. Tz-Kek split spawns match in count/timing.
2. Jad healers spawn at correct threshold and heal behavior matches.
3. Prayer drain behavior matches.

Artifacts:
1. mechanic-specific event logs.
2. snapshot comparisons for trigger windows.

---

## E2E-06 Movement, Pathing, and Clipping Parity

Objective:
- Validate safespot-critical movement parity.

Procedure:
1. Execute `trace_movement_edges.json` on both runtimes.
2. Include diagonal corner and blocked-tile scenarios.
3. Include NPC-follow and under/step-out behavior scenarios.

Assertions:
1. Step acceptance/rejection matches.
2. Path destinations and queue behavior match.
3. LOS-sensitive behavior matches where applicable.

Artifacts:
1. movement decision logs.
2. collision/path debug dumps.

---

## E2E-07 Combat Timing and Consumption Lockout Parity

Objective:
- Verify attack cadence and consume timing parity.

Procedure:
1. Execute trace with repeated attack/eat/drink alternation.
2. Include ammo depletion edge case and prayer toggles.

Assertions:
1. Attack delays and hit landing ticks match.
2. Eat/drink lockout rejections match.
3. Protection prayer impact behavior matches.
4. Ammo behavior (consumption/blocking at zero) matches.

Artifacts:
1. action result timelines.
2. per-tick combat event logs.

---

## E2E-08 Episode Reset and Termination Contract

Objective:
- Validate correct reset and terminal handling.

Procedure:
1. Run multiple resets with fixed seeds.
2. Validate death termination, completion termination, and max-tick termination.

Assertions:
1. Reset state equals contract every run.
2. Terminal reason is correct and deterministic.
3. Post-terminal cleanup does not leak prior episode state.

Artifacts:
1. reset snapshot diffs.
2. termination reason report.

---

## E2E-09 Runtime Pruning Verification

Objective:
- Confirm excluded systems are not active in headless runtime.

Procedure:
1. Inspect loaded services/modules/scripts in headless process.
2. Verify excluded subsystems are not initialized.

Assertions:
1. No network/login server running.
2. No unrelated content systems loaded beyond allowlist.
3. Headless runtime still passes parity suites.

Artifacts:
1. startup module inventory.
2. script registry dump.

---

## E2E-10 Throughput and Stability (Post-Parity)

Objective:
- Validate production readiness for RL stepping.

Procedure:
1. Run long soak test (minimum 100k ticks equivalent workload).
2. Run throughput benchmark with fixed action generation.

Assertions:
1. No crashes, deadlocks, or memory growth anomalies.
2. Throughput meets project target.
3. Post-benchmark parity spot-check still passes.

Artifacts:
1. benchmark report.
2. memory/timing profile snapshots.

---

## 3) Pass/Fail Rules

Release pass requires all:
1. All E2E suites pass.
2. No unresolved parity divergence.
3. No flaky test behavior in two consecutive full suite runs.
4. Artifacts generated and archived.

Automatic fail conditions:
1. Any deterministic replay mismatch under same seed + trace.
2. Any oracle/headless divergence in mandatory scenarios.
3. Missing required artifacts.

---

## 4) Final Sign-Off Checklist

1. `FCspec.md` acceptance criteria confirmed.
2. `FCplan.md` all step exit criteria confirmed.
3. E2E suite pass report approved.
4. Release candidate commit tagged.
5. Future RL work unblocked.
