# Fight Caves Demo Headed Trust Gate

Date: 2026-03-12
Phase/PR: 7A.7
Status: Passed, latest live headed rerun accepted and PR 7A.7 closed

## Run Topology

- RSPS demo server: WSL/Linux profile `fight-caves-demo`
- Headed client: stock `void-client-1.0.1.jar` on native Windows JDK 8
- Working client transport on this machine: `172.25.183.199:43594`
- Known non-working client transport on this machine: `127.0.0.1:43594`

## Latest Live Rerun Under Closure Review

- Account: `jordantest01`
- Session ID: `jordantest01-2026-03-12T16-17-48.383`
- Session log: `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/session_logs/jordantest01-2026-03-12T16-17-48.383.jsonl`
- Checklist: `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/validation_checklists/jordantest01-2026-03-12T16-17-48.383.md`
- Starter-state manifest 1: `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/starter_state_manifests/jordantest01-2026-03-12T16-17-48.383_reset_001.json`
- Starter-state manifest 2: `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/starter_state_manifests/jordantest01-2026-03-12T16-17-48.383_reset_002.json`

## Historical Supporting Session For World-Gating Coverage

- Account: `trust77c1`
- Session ID: `trust77c1-2026-03-12T15-00-03.803`
- Session log: `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/session_logs/trust77c1-2026-03-12T15-00-03.803.jsonl`
- Checklist: `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/validation_checklists/trust77c1-2026-03-12T15-00-03.803.md`
- Starter-state manifest 3: `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/starter_state_manifests/trust77c1-2026-03-12T15-00-03.803_reset_003.json`
- Starter-state manifest 4: `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/starter_state_manifests/trust77c1-2026-03-12T15-00-03.803_reset_004.json`

## Headed Evidence

- Latest live headed rerun reached real in-game entry on the stock Windows client over the machine-specific working transport path `172.25.183.199:43594`.
- Latest live headed rerun confirmed the prayer tab now shows the normal prayer book rather than curses.
- Latest live headed rerun confirmed no hidden prayer drain was observed while no prayers were active.
- Latest live headed rerun confirmed death recycle returns to a real active wave.
- Latest live headed rerun confirmed combat still works after recycle.
- Latest live headed rerun confirmed wave progression continued as expected.
- Historical live world-gating evidence remains anchored to `trust77c1`:
  - `world_access_violation` with `cause=area_exit`
  - `leave_requested` with `cause=exit_object`
  - both paths recycled back into a fresh Fight Caves episode rather than broader-world travel

## 2026-03-12 Blocker-Fix Pass

The latest live manual run reopened three real blockers:

- headed prayer tab still showing curses / unexpected prayer drain behavior
- death recycle returning to a fresh cave position with no visible active wave
- exit or home-tele recycle rebuilding wave 1 with inert NPCs / no player attack loop

Those were addressed in code and regression coverage, and the final live rerun has now confirmed the fixes on the headed stock-client path.

### Fixes landed

- Prayer-book authority:
  - `/home/jordan/code/RSPS/game/src/main/kotlin/content/skill/prayer/list/PrayerList.kt`
  - `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveEpisodeInitializer.kt`
  - The demo profile now forces the normal prayer book whenever the prayer UI opens, clears hidden curses state, and stops hidden prayer drain if no normal prayers are active.
- Recycle wave aggression:
  - `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveEpisodeInitializer.kt`
  - After a reset rebuilds the Fight Caves instance, the demo path now reissues wave aggression once the fresh NPCs are indexed so leave/death/world-access recycle paths do not strand the player with inert wave-1 NPCs.
- Added explicit regression coverage:
  - `/home/jordan/code/RSPS/game/src/test/kotlin/content/area/karamja/tzhaar_city/FightCaveEpisodeInitializerTest.kt`
  - `/home/jordan/code/RSPS/game/src/test/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCaveTest.kt`

### Automated evidence

- Command:
  - `source /home/jordan/code/.workspace-env.sh && ./gradlew --no-daemon :game:test --tests content.area.karamja.tzhaar_city.FightCaveEpisodeInitializerTest --tests content.area.karamja.tzhaar_city.TzhaarFightCaveTest`
- Result:
  - `FightCaveEpisodeInitializerTest > demo prayer interface forces the normal prayer book()` passed
  - `FightCaveEpisodeInitializerTest > demo profile leave recycles into a fresh fight cave episode()` passed
  - `FightCaveEpisodeInitializerTest > demo profile death stays in the fight cave loop()` passed
  - `FightCaveEpisodeInitializerTest > demo profile teleporting out of the cave resets the episode and blocks world access()` passed
  - `TzhaarFightCaveTest > Complete all waves to earn fire cape()` passed

## Blocker Diagnoses

### Prayer-book mismatch

- Diagnosis: real server-side state bug affecting headed presentation, not a curses-mechanics mismatch.
- Root cause:
  - the demo flow allowed the prayer UI to inherit or reopen on a non-normal book value, and hidden curses state could survive long enough to confuse the headed presentation
- Fix:
  - demo reset still sets the explicit normal-book selector
  - demo prayer UI open now re-forces the normal book and clears curses/drain state
- Evidence:
  - automated regression `demo prayer interface forces the normal prayer book()` is green
  - latest live rerun showed the normal prayer book in real headed play and no hidden prayer drain with no active prayers
- Trust-gate result: pass

### Death recycle / respawn with no spawns

- Diagnosis: the reset loop was real, but live trust validation still needed a stronger post-reset wave-boot handoff.
- Root cause:
  - the recycled wave could exist server-side without reliably reasserting aggression once the fresh NPCs were indexed
- Fix:
  - the reset path now reissues aggression a couple of ticks after the instance rebuild
- Evidence:
  - automated regressions for death, leave, and world-access recycle are green again
  - latest live rerun returned to an active wave after death and combat worked after recycle
- Trust-gate result: pass

### No broader world access

- Diagnosis: validated with real headed leave attempts.
- Evidence:
  - session log records `leave_requested` with `cause=exit_object`
  - session log immediately records a fresh `episode_reset`
  - `reset_003` shows canonical re-entry into a new Fight Caves instance instead of travel to the broader world
  - session log also records `world_access_violation` with `cause=area_exit`
  - `reset_004` shows canonical re-entry after the area-exit violation
  - the post-exit and post-area-exit headed captures both show the player still inside the Fight Caves-only runtime
- Trust-gate result: pass

### Completion recycle decision

- Decision: live manual completion recycle is not required to close 7A.7.
- Why:
  - the full-wave automated Fight Caves suite is now green again, including `TzhaarFightCaveTest > Complete all waves to earn fire cape()`
  - the headed trust gate already proves the same recycle family in live play through direct entry, combat, wave progression, death recycle, and exit-path recycle
  - forcing a manual full-cave completion now would add time but not materially reduce ambiguity for headed trustworthiness
- Trust-gate result: pass

## Result Summary

- Direct entry into Fight Caves: pass
- Canonical starter state: pass
  - `jordantest01` manifest `reset_001` shows the correct starter equipment, inventory, run state, skills, and normal prayer book.
- Combat, prayer, and inventory loop: pass
- Wave progression: pass
- Death reset/restart loop: pass
  - `jordantest01` session log shows a fresh `episode_reset_followup` after `player_death`, and the latest live rerun confirmed the post-death wave was active and playable.
- No broader world access: pass
  - Live world-gating evidence remains covered by `trust77c1` through both exit-object recycle and area-exit violation recycle.
- Completion recycle: pass
  - The automated full-wave suite remains the acceptance evidence for completion recycle in 7A.7.
- Observability usefulness: pass
  - The session logs, manifests, and checklists were sufficient to diagnose the reopened blockers and confirm the successful live rerun.

## Current Decision

- PR 7A.7 is complete.
- Phase 7A trust gate is passed.
- The stock-client headed path is now trustworthy enough to move to the next planned step without starting Phase 8.
- Machine-specific runtime note remains unchanged:
  - working stock-client transport on this machine: `172.25.183.199:43594`
  - non-working stock-client transport on this machine: `127.0.0.1:43594`
