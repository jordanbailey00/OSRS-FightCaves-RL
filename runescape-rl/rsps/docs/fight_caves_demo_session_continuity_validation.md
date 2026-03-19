# Fight Caves Demo Session Continuity Validation

This note records the bounded PR 7B.2 investigation into headed death/recycle continuity on the RSPS-backed Fight Caves demo path.

## Problem

Observed live failure before the fix:

- player dies in the Fight Caves demo
- headed client shows `connection lost`
- client falls back to the login screen

This is a real blocker before Phase 8 because the headed demo loop should stay inside the same session and recycle back into Fight Caves.

## Root cause

The problem was a server-side reset timing race, not an intended logout path.

Before the fix:

- the demo profile used a fixed delay before resetting after death
- the stock death queue could still be active while the demo recycle tried to rebuild the Fight Caves episode
- that made the recycle sensitive to race conditions between death handling, teleports, instance rebuild, and client region refresh

## Fix

The demo death recycle now waits until the stock death queue is actually clear before resetting the episode.

Changed files:

- `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt`
- `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveDemoObservability.kt`
- `/home/jordan/code/RSPS/game/src/test/kotlin/content/area/karamja/tzhaar_city/FightCaveEpisodeInitializerTest.kt`

What changed:

- removed the blind fixed death-reset delay
- added polling until `player.dead` is false and the `death` queue is no longer active
- recorded a `death_reset_ready` session-log event before the recycle happens
- aborts the recycle if the session is already actually disconnected

## Validation

Targeted Fight Caves validation run:

```bash
source /home/jordan/code/.workspace-env.sh
cd /home/jordan/code/RSPS
./gradlew --no-daemon :game:test --tests content.area.karamja.tzhaar_city.FightCaveEpisodeInitializerTest --tests content.area.karamja.tzhaar_city.TzhaarFightCaveTest
```

Result:

- passed

Relevant coverage:

- `FightCaveEpisodeInitializerTest > demo profile death stays in the fight cave loop()`
- `FightCaveEpisodeInitializerTest > demo profile leave recycles into a fresh fight cave episode()`
- `FightCaveEpisodeInitializerTest > demo profile teleporting out of the cave resets the episode and blocks world access()`
- `TzhaarFightCaveTest > Complete all waves to earn fire cape()`

The death test now explicitly checks for the new continuity signal in the session log:

- `"event":"death_reset_ready"`
- `"death_queue_active":false`
- `"disconnected":false`

## Current status

The bounded server/runtime fix is in place and the targeted regression suites are green.

What still remains before PR 7B.2 can close:

- one fresh live headed death/recycle rerun on the current convenience path, to confirm the earlier `connection lost` failure is gone in true headed/manual play as well
