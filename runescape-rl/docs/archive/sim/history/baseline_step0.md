# Baseline Report - Step 0

Date: 2026-03-04 22:01:53 -05:00
Branch: main
Commit: 71b6ad3fd
Status: COMPLETE

## Scope

Executed Step 0 from FCplan.md:
1. Stabilize compile blockers without gameplay behavior changes.
2. Run baseline test targets.
3. Record environment prerequisites and baseline test outcomes.

## Stabilization Changes

1. Compile fix in `game/src/main/kotlin/content/entity/world/RegionLoading.kt`
- Moved.playerMoved.addFirst(::checkReload) -> Moved.playerMoved.add(0, ::checkReload)
- Reason: playerMoved is an ObjectArrayList; addFirst is unavailable.
- Semantics preserved: insertion at index 0 keeps "run first" ordering intent.

## Environment Prerequisites (Confirmed)

1. Java 21 runtime required.
- Local portable JDK used: .tools/jdk-21.0.10+7
- Gradle executed with JAVA_HOME=.tools/jdk-21.0.10+7

2. Void modified cache required in data/cache/.
- Downloaded from official Mega folder linked in README.md.
- File used: 2025-06-12-void-634-cache.7z
- Extracted into data/cache/.

## Baseline Test Commands and Results

1. ./gradlew :game:test --tests "content.area.karamja.tzhaar_city.TzhaarFightCaveTest"
- Result: PASSED

2. ./gradlew :game:test --tests "content.entity.combat.*"
- Result: PASSED

3. ./gradlew :game:test --tests "content.skill.prayer.*"
- Result: PASSED

4. ./gradlew :game:test --tests "content.skill.constitution.*"
- Result: PASSED

## Notes

1. Without Java 21 and the modified cache, baseline tests fail in non-representative ways.
2. Step 0 is now complete; extraction work can proceed to Step 1.


