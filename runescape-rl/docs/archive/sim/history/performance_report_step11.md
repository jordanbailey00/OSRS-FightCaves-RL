# Performance Report - Step 11

Generated from `docs/performance_benchmark.log` and Step 11 verification test runs.

## Historical Note

This document remains the historical Step 11 report.

For active optimization work, the newer standalone Phase 0 entrypoints should now be treated as the current source for sim-side benchmark artifacts:

```bash
source /home/jordan/code/.workspace-env.sh
cd /home/jordan/code/fight-caves-RL
./gradlew --no-daemon :game:headlessPerformanceReport
./gradlew --no-daemon :game:headlessPerformanceProfile
```

Current-host WSL standalone values from `docs/performance_benchmark.json`:

- single-slot throughput: `30509.78` ticks/s
- batched headless throughput (`16 envs`): `473574.60` env steps/s
- soak throughput: `42621.76` ticks/s

These standalone Phase 0 values supersede the older Step 11 `8891.93` ticks/s artifact for current-host capacity estimation. The Step 11 content below is preserved because it still records the original extraction-phase validation context.

## Scope

This report covers Step 11 requirements:
- performance benchmark (`HeadlessStepRateBenchmarkTest`)
- long-run soak stability (`HeadlessLongRunStabilityTest`)
- batch stepping parity check (`HeadlessBatchSteppingParityTest`)
- full Step 9 parity matrix rerun after optimization changes

## Environment

- OS: Windows
- Runtime: Java 21 (`Eclipse Adoptium jdk-21.0.10.7-hotspot`)
- Gradle command context: `:game:test` targeted test execution

## Benchmark Results

Source artifact: `docs/performance_benchmark.log`

- `generated_at_utc=2026-03-06T01:54:55.9547411Z`
- Throughput benchmark:
  - `steps=10000`
  - `ticks_advanced=10000`
  - `elapsed_nanos=1124615400`
  - `ticks_per_second=8891.93`
- Soak benchmark:
  - `steps=20000`
  - `ticks_advanced=20000`
  - `elapsed_nanos=2177215500`
  - `ticks_per_second=9186.05`
  - `final_tick=30000`
  - `final_wave=1`
  - `final_remaining=0`
  - `final_hitpoints=700`
  - `final_prayer=43`

## Validation Commands and Outcomes

Executed with `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`:

```powershell
./gradlew :game:test `
  --tests "HeadlessStepRateBenchmarkTest" `
  --tests "HeadlessLongRunStabilityTest" `
  --tests "HeadlessBatchSteppingParityTest" `
  --tests "HeadlessPerformanceReportGenerationTest" `
  --tests "ParityHarnessSingleWaveTraceTest" `
  --tests "ParityHarnessFullRunTraceTest" `
  --tests "ParityHarnessJadHealerScenarioTest" `
  --tests "ParityHarnessTzKekSplitScenarioTest" `
  --no-daemon
```

Per-suite outcomes from `game/build/test-results/test/TEST-*.xml`:
- `HeadlessStepRateBenchmarkTest`: passed
- `HeadlessLongRunStabilityTest`: passed
- `HeadlessBatchSteppingParityTest`: passed
- `HeadlessPerformanceReportGenerationTest`: passed
- `ParityHarnessSingleWaveTraceTest`: passed
- `ParityHarnessFullRunTraceTest`: passed
- `ParityHarnessJadHealerScenarioTest`: passed
- `ParityHarnessTzKekSplitScenarioTest`: passed

## Assessment

- Step 11 throughput gate is satisfied (`ticks_per_second` well above `>100` threshold).
- Soak stability gate is satisfied (bounded final observation and sustained throughput).
- Post-optimization parity gate is satisfied (full Step 9 matrix rerun passed).
- Step 11 exit criteria are met.
