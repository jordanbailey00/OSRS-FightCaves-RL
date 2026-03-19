# Sim Profiler Report

Date: 2026-03-09

Repo and SHA:
- fight-caves-RL: `2365506bd3ea5cce515c571f39c24e72a38acc67`

Runtime used in this audit:
- JVM: Temurin `21.0.10+7`
- host topology: WSL2 on AMD Ryzen 5 5600G, 6 cores / 12 threads
- primary profiler attempted: Java Flight Recorder (`jfr`)
- direct attach tool attempted: `jcmd`

This report records what was and was not measurable about the JVM/headless runtime in this audit pass.

## Goal

Determine whether the JVM/headless sim itself is already the dominant bottleneck, or whether the outer RL stack is slower.

## Phase 0 Standalone Harness Outcome

The repo now has clean standalone measurement entrypoints that do not rely on the JUnit test worker path:

```bash
source /home/jordan/code/.workspace-env.sh
cd /home/jordan/code/fight-caves-RL
./gradlew --no-daemon :game:headlessPerformanceReport
./gradlew --no-daemon :game:headlessPerformanceProfile
```

Artifacts produced by those entrypoints:

- report log:
  - `/home/jordan/code/fight-caves-RL/history/performance_benchmark.log`
- report json:
  - `/home/jordan/code/fight-caves-RL/history/performance_benchmark.json`
- clean profile artifacts:
  - `/home/jordan/code/fight-caves-RL/game/build/reports/headless-performance/headless_performance_profile.log`
  - `/home/jordan/code/fight-caves-RL/game/build/reports/headless-performance/headless_performance_profile.json`
  - `/home/jordan/code/fight-caves-RL/game/build/reports/headless-performance/headless_performance_profile.jfr`

Current-host WSL standalone report values:

- single-slot throughput benchmark:
  - `30509.78` ticks/s
- batched headless benchmark (`16` envs, `4000` tick rounds):
  - `473574.60` env steps/s
  - `29598.41` tick rounds/s
- soak benchmark:
  - `42621.76` ticks/s
- per-worker ceiling estimate from the standalone report:
  - `workers_needed_for_100k = 1` on this host-class measurement

Interpretation:

- this standalone harness materially supersedes the older Step 11 `8.9k` artifact for current-host performance analysis
- the clean standalone numbers make `100k+` look plausible from the sim-ceiling perspective
- the remaining measured collapse is therefore much more clearly in the RL outer stack

## What Was Measured

### 1. Existing Pure-JVM Benchmark Artifact

Source:
- `/home/jordan/code/fight-caves-RL/history/performance_report_step11.md`

Existing current-repo artifact values:
- throughput benchmark: `8891.93` ticks/s
- soak benchmark: `9186.05` ticks/s

Important limitation:
- this artifact is real repo evidence, but it was generated in a Windows-native environment, not on the WSL host used for the rest of this audit packet
- it should now be treated as historical context, not the active per-worker ceiling estimate

### 2. Current-Host JUnit Sanity Run

Command:

```bash
source /home/jordan/code/.workspace-env.sh
cd /home/jordan/code/fight-caves-RL
./gradlew --no-daemon :game:test --tests '*HeadlessStepRateBenchmarkTest'
```

Outcome:
- passed on the current WSL host
- the test does not emit numeric throughput itself; it only enforces a minimum gate
- it remains useful as a regression gate, not as the preferred Phase 0 measurement path

### 3. Clean Standalone JFR Capture

Command:

```bash
source /home/jordan/code/.workspace-env.sh
cd /home/jordan/code/fight-caves-RL
./gradlew --no-daemon :game:headlessPerformanceProfile
```

Outcome:
- produced a clean standalone JFR:
  - `/home/jordan/code/fight-caves-RL/game/build/reports/headless-performance/headless_performance_profile.jfr`
- produced matching standalone report log/json sidecars
- `jfr summary` succeeded cleanly on the generated file
- the profile duration was about `3 s`
- unlike the older JUnit capture, the sample stream now includes headless runtime frames

Direct evidence from the clean standalone JFR:
- `jdk.ExecutionSample`: `101`
- `jdk.ObjectAllocationSample`: `384`
- `jdk.GarbageCollection`: `4`
- `jfr print --events jdk.ExecutionSample ... | rg 'Headless|GameLoop'` now hits:
  - `HeadlessRuntime.tick(int)`
  - `HeadlessBatchSteppingKt.runFightCaveBatch(...)`
  - `HeadlessObservationBuilder.build(...)`
  - `HeadlessActionAdapter.visibleNpcTargets(...)`
  - `sim.engine.GameLoop.tick(...)`
- `jfr print --events jdk.ObjectAllocationSample ... | rg 'Headless|LinkedHashMap|ArrayList'` now hits:
  - `HeadlessActionAdapter.applied(...)`
  - `HeadlessPerformanceReportGenerator.runSingleSlotBenchmark(...)`
  - `HeadlessPerformanceReportGenerator.runSoakBenchmark(...)`
  - repeated `LinkedHashMap` and `ArrayList` allocation sites on the hot path

Interpretation:
- this capture is clean enough to use for function-level optimization targeting
- startup and cache loading still appear in the profile, but headless hot-path frames are now visible and attributable
### 4. Earlier JFR Attempts And Tooling Limits

Attempt A:
- embedded-JVM bridge benchmark with:

```bash
JAVA_TOOL_OPTIONS='-XX:StartFlightRecording=filename=/tmp/fc_perf_audit/sim_bridge64.jfr,settings=profile,dumponexit=true'
```

Outcome:
- recording startup message appeared
- dump failed on exit and produced a zero-byte file

Attempt B:
- Gradle JUnit run with:

```bash
JAVA_TOOL_OPTIONS='-XX:StartFlightRecording=filename=/tmp/fc_perf_audit/sim_step_test.jfr,settings=profile,dumponexit=true'
./gradlew --no-daemon :game:test --tests '*HeadlessStepRateBenchmarkTest'
```

Outcome:
- produced a non-zero JFR file: `/tmp/fc_perf_audit/sim_step_test.jfr`
- `jfr summary` succeeded
- `jfr print` pretty-printer hit internal errors on some frames in this JDK build
- JSON export succeeded after the worker exited

Benchmark scenario and sample duration:
- scenario: `HeadlessStepRateBenchmarkTest`
- resulting JFR duration: `319 s`
- extra JVM flags used: `-XX:StartFlightRecording=filename=/tmp/fc_perf_audit/sim_step_test.jfr,settings=profile,dumponexit=true`

Result quality:
- CPU and allocation samples were dominated by Gradle worker / JUnit / JaCoCo harness activity
- extracted CPU sample set contained zero headless-symbol hits for:
  - `HeadlessObservationBuilder`
  - `HeadlessActionAdapter`
  - `FightCave`
  - `GameTick`
  - `GameLoop`
- extracted allocation sample set also contained zero headless-symbol hits

JFR summary highlights:
- duration: `319 s`
- `jdk.ExecutionSample`: `521`
- `jdk.ObjectAllocationSample`: `4723`
- `jdk.GarbageCollection`: `27`

Allocation and GC summary from the contaminated capture:
- top allocation objects:
  - `[B`: `2514`
  - `java/lang/String`: `180`
  - `[Ljava/lang/Object;`: `126`
  - `java/lang/ThreadLocal$ThreadLocalMap$Entry`: `125`
  - `java/util/ArrayList`: `77`
- GC counts:
  - `G1New`: `19`
  - `G1Old`: `8`

Interpretation:
- this older JFR file is not clean enough to attribute CPU or allocation cost to the headless fight-caves runtime itself
- it remains useful as evidence for why the standalone Phase 0 profiler path was necessary

### 5. Attach Tooling Result

Command:

```bash
source /home/jordan/code/.workspace-env.sh
jcmd -l
```

Outcome:
- `jcmd` sees normal Java processes such as Gradle workers
- it does not expose the embedded JPype JVM inside the RL Python benchmark as an attachable JVM target

Interpretation:
- direct attach-based JFR on the embedded bridge runtime is not available with the current setup

## Evidence-Based Conclusions

Facts:
- There is now a clean standalone current-host report at about `30.5k` single-slot ticks/s and about `473.6k` batched env steps/s at `16` envs.
- The current WSL host can pass the step-rate JUnit benchmark.
- Clean JVM-only hot-function sampling is now available through `:game:headlessPerformanceProfile`.

What this does and does not prove:
- It does not prove that the full end-to-end stack can reach `100000+` today.
- It does show that the standalone sim ceiling is far higher than the older Step 11 artifact suggested.
- It now does provide attributable current-host JVM-side targets for the next optimization pass.

## What Remains Uncertain

- native-Linux standalone sim numbers on the approved source-of-truth host class
- native-Linux clean standalone JFR on that same host class

## Best Supported Next Sim-Side Targets

These are now profiler-supported targets rather than pure hypotheses:
- `HeadlessObservationBuilder.build(...)`
- `HeadlessActionAdapter.visibleNpcTargets(...)`
- `HeadlessActionAdapter.applied(...)`
- repeated `LinkedHashMap` / ordered-object construction in action and observation paths
- repeated `ArrayList` growth / iteration on hot paths

Reason:
- the outer Python profile still shows observation conversion is the dominant RL-side cost
- the clean standalone JFR now also shows actionable headless-side observation/action allocation sites rather than only harness noise

## Audit Position

The direct sim profiler work is now actionable enough for optimization planning:
- the standalone harness gives a credible per-worker ceiling estimate
- the clean JFR path provides attributable CPU and allocation samples on headless runtime frames
- the native-Linux source-of-truth rerun has now completed, so profiler infrastructure is no longer a Phase 0 blocker
