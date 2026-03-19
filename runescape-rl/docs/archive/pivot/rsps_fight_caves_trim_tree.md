# RSPS Fight Caves Trim Tree

## Keep as-is for first pass

- `RSPS/engine`
- `RSPS/network`
- `RSPS/cache`
- `RSPS/config`
- `RSPS/types`
- `RSPS/buffer`
- Fight Caves content under `RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/`
- region loading and instance support
- inventory/equipment/prayer/combat/movement/update pipelines
- `RSPS/data/minigame/tzhaar_fight_cave/*`
- shared item/NPC/interface/var/animation/graphic definitions needed by the client/UI/runtime

## Trim by alternate bootstrap/profile

- bot loading from `RSPS/game/src/main/kotlin/GameModules.kt`
- bot tick stage from `RSPS/game/src/main/kotlin/GameTick.kt`
- Grand Exchange loading and tick stage
- farming, charter ships, fairy rings, books, other unrelated module loads from `GameModules.kt`
- unrelated content script loads where they are not needed for login/gameframe/Fight Caves play
- broader world traversal after login

## Gate rather than delete at first

- login/auth flow
- file-server/JS5 path
- gameframe tabs that are not directly used during Fight Caves
- shared data/config tables
- save/load path

## Add for demo mode

- Fight Caves episode initializer that enforces the canonical starter state
- demo-specific spawn/login routing into the cave
- demo-specific world gating
- starter-state validation manifest/logging
