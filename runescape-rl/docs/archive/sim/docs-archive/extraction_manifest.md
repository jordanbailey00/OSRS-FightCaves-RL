# Fight Caves Headless Extraction Manifest (Step 1)

## Documentation Status

- Status: archive candidate under `pivot_documentation_triage.md`.
- Current authority: `pivot_plan.md` and `pivot_implementation_plan.md`.
- Retention reason: kept temporarily for historical extraction-boundary context; do not treat it as active pivot planning guidance.

## 1) Scope and Intent

This manifest defines the initial keep/cut boundary for extracting a deterministic Fight Caves-only runtime from Void.

Status:
- Step 1 manifest artifacts are created in this iteration.
- This is an extraction baseline, not the final pruned runtime.
- Any item listed as `candidate` still requires runtime validation before deletion.

Primary objective:
- Preserve 1:1 behavior for Fight Caves while removing unrelated runtime and data loading.

## 2) Source Evidence Used

Code authority:
- `game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt`
- `game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCaveWaves.kt`
- `game/src/main/kotlin/content/area/karamja/tzhaar_city/TzTokJad.kt`
- `game/src/main/kotlin/content/area/karamja/tzhaar_city/TzHaarHealers.kt`
- `game/src/main/kotlin/GameTick.kt`
- `game/src/main/kotlin/Main.kt`
- `engine/src/main/kotlin/world/gregs/voidps/engine/EngineModules.kt`
- `engine/src/main/kotlin/world/gregs/voidps/engine/data/ConfigFiles.kt`
- `engine/src/main/kotlin/world/gregs/voidps/engine/map/instance/Instances.kt`
- `engine/src/main/kotlin/world/gregs/voidps/engine/map/zone/DynamicZones.kt`

Data authority:
- `data/minigame/tzhaar_fight_cave/*`
- `data/area/karamja/tzhaar_city/tzhaar_city.objs.toml`
- `data/area/karamja/tzhaar_city/tzhaar_city.items.toml`
- `data/area/karamja/tzhaar_city/tzhaar_city.npcs.toml`
- `data/skill/range/*.items.toml` (loadout + ammo)
- `data/skill/fishing/fish.items.toml` (shark)
- `data/skill/herblore/potion.items.toml` (prayer potion dose chain)
- `data/skill/prayer/prayers.toml`
- `data/skill/prayer/prayer.varps.toml`
- `data/skill/prayer/prayer.varbits.toml`

## 3) Required Runtime Closure (Keep)

### 3.1 Gradle Modules

Required for headless runtime:
- `game`
- `engine`
- `cache`
- `types`
- `buffer`
- `config`

Oracle-only (keep for parity harness path):
- `network`

Physically pruned in Step 13:
- `tools`
- `database`

No conditional DB module support remains in the pruned repository.

### 3.2 Tick and World Pipeline

Required stage order basis (from `GameTick.kt`):
1. `PlayerResetTask`
2. `NPCResetTask`
3. `BotManager` (candidate to disable in headless)
4. `Hunting` (candidate to disable in headless)
5. `GrandExchange` (candidate to disable in headless)
6. `ConnectionQueue` (disable in headless)
7. `NPCs`
8. `FloorItems` (disabled in headless)
9. `InstructionTask`
10. `World`
11. `NPCTask`
12. `PlayerTask`
13. `FloorItemTracking`
14. `GameObjects.timers`
15. `DynamicZones`
16. `ZoneBatchUpdates`
17. `CharacterUpdateTask`
18. `SaveQueue` (candidate to disable in headless training)
19. `SaveLogs` (disable in headless training)

### 3.3 Code Paths (Initial Keep Set)

Fight Caves domain:
- `game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt`
- `game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCaveWaves.kt`
- `game/src/main/kotlin/content/area/karamja/tzhaar_city/TzTokJad.kt`
- `game/src/main/kotlin/content/area/karamja/tzhaar_city/TzHaarHealers.kt`

Instance + region handling:
- `game/src/main/kotlin/content/quest/Cutscene.kt`
- `game/src/main/kotlin/content/entity/world/RegionLoading.kt`
- `engine/src/main/kotlin/world/gregs/voidps/engine/map/instance/Instances.kt`
- `engine/src/main/kotlin/world/gregs/voidps/engine/map/zone/DynamicZones.kt`

Combat core:
- `game/src/main/kotlin/content/entity/combat/**`
- `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/mode/combat/**`

Movement + collision + pathing:
- `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/mode/move/**`
- `engine/src/main/kotlin/world/gregs/voidps/engine/map/collision/**`
- `org.rsmod.game.pathfinder.*` via `EngineModules.kt`

Player action dependencies:
- `game/src/main/kotlin/content/skill/prayer/**`
- `game/src/main/kotlin/content/skill/constitution/Eating.kt`
- `game/src/main/kotlin/content/skill/constitution/drink/Potions.kt`
- `game/src/main/kotlin/content/skill/ranged/Ranged.kt`
- `game/src/main/kotlin/content/skill/ranged/Ammo.kt`
- `game/src/main/kotlin/content/entity/player/effect/energy/Energy.kt`
- `game/src/main/kotlin/content/entity/player/effect/energy/Running.kt`
- `game/src/main/kotlin/content/entity/proj/ShootProjectile.kt`
- `game/src/main/kotlin/content/entity/player/inv/item/ItemExtensions.kt`

### 3.4 Script Class Allowlist (Step 4 Locked)

Keep these script classes active in headless:
- `content.area.karamja.tzhaar_city.TzhaarFightCave`
- `content.area.karamja.tzhaar_city.TzTokJad`
- `content.area.karamja.tzhaar_city.TzHaarHealers`
- `content.entity.Movement`
- `content.entity.world.RegionLoading`
- `content.entity.combat.Combat`
- `content.entity.combat.hit.CombatHitsplats`
- `content.entity.npc.combat.Attack`
- `content.entity.death.CharacterDeath`
- `content.entity.death.NPCDeath`
- `content.entity.death.PlayerDeath`
- `content.entity.player.Login`
- `content.skill.ranged.Ranged`
- `content.skill.prayer.Prayers`
- `content.skill.prayer.active.PrayerDrain`
- `content.skill.prayer.list.PrayerToggle`
- `content.skill.constitution.Eating`
- `content.skill.constitution.drink.Potions`
- `content.entity.player.effect.energy.Energy`

Oracle-only support scripts (not required for headless episode stepping):
- `content.area.karamja.tzhaar_city.TzHaarFightCaveGuard`
- `content.area.karamja.tzhaar_city.TzHaarBanker`
- `content.area.karamja.tzhaar_city.TzHaarShops`
- `content.area.karamja.tzhaar_city.TzHaarMej`
- `content.area.karamja.tzhaar_city.TzHaar`

### 3.5 Data Allowlist (Initial)

Fight Caves primary set:
- `data/minigame/tzhaar_fight_cave/tzhaar_fight_cave_waves.toml`
- `data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.npcs.toml`
- `data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.combat.toml`
- `data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.areas.toml`
- `data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.vars.toml`
- `data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.varbits.toml`
- `data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.ifaces.toml`
- `data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.anims.toml`
- `data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.gfx.toml`
- `data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.sounds.toml`
- `data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.jingles.toml`
- `data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.scripts.toml`

Entrance/reward linkage:
- `data/area/karamja/tzhaar_city/tzhaar_city.objs.toml`
- `data/area/karamja/tzhaar_city/tzhaar_city.items.toml`
- `data/area/karamja/tzhaar_city/tzhaar_city.npcs.toml`
- `data/area/karamja/tzhaar_city/tzhaar_city.npc-spawns.toml` (oracle-mode support)

Loadout dependencies:
- `data/skill/range/crossbow.items.toml`
- `data/skill/range/bolt.items.toml`
- `data/skill/range/ranged_armour.items.toml`
- `data/skill/range/ammo_groups.toml`
- `data/minigame/tai_bwo_wannai_cleanup/tai_bwo_wannai_cleanup.items.toml`
- `data/skill/fishing/fish.items.toml`
- `data/skill/herblore/potion.items.toml`
- `data/entity/player/inventory/inventory.invs.toml` (inventory capacity/slots)
- `data/entity/player/equipment/worn_equipment.invs.toml` (equipment slot container)
Combat/prayer/player defs needed by retained scripts:
- `data/entity/player/combat/weapon_animations.toml`
- `data/entity/player/combat/combat_styles/weapon_styles.toml`
- `data/entity/player/combat/combat_styles/combat_style.varps.toml`
- `data/entity/player/combat/weapon.anims.toml`
- `data/entity/player/combat/combat.sounds.toml`
- `data/skill/prayer/prayers.toml`
- `data/skill/prayer/prayer.varps.toml`
- `data/skill/prayer/prayer.varbits.toml`
- `data/skill/prayer/prayer.sounds.toml`
- `data/entity/player/modal/toplevel/gameframe.varps.toml`
- `data/entity/player/human.anims.toml`
- `data/entity/player/player.sounds.toml`
- `data/entity/npc/hunt_modes.toml`
- `data/client/categories.toml`

### 3.6 Required IDs

Fight Caves NPC IDs:
- `tz_kih`
- `tz_kih_spawn_point`
- `tz_kek`
- `tz_kek_spawn_point`
- `tz_kek_spawn`
- `tok_xil`
- `tok_xil_spawn_point`
- `yt_mej_kot`
- `yt_mej_kot_spawn_point`
- `ket_zek`
- `ket_zek_spawn_point`
- `tztok_jad`
- `yt_hur_kot`

Loadout items:
- `rune_crossbow`
- `adamant_bolts`
- `coif`
- `black_dragonhide_body`
- `black_dragonhide_chaps`
- `black_dragonhide_vambraces`
- `snakeskin_boots`
- `shark`
- `prayer_potion_4`

Rewards and cave objects:
- `tokkul`
- `fire_cape`
- `cave_entrance_fight_cave`
- `cave_exit_fight_cave`

### 3.7 Cache and Map Scope

Required source region:
- `9551` (Fight Caves source region copied into dynamic instance)

Oracle entry/support region:
- `9808` (TzHaar city surface/entrance region)

Dynamic instances:
- Use `Instances.small()` pool from `x=100..252`, `y<82` region space.
- Preserve `smallInstance(region, levels=3)` semantics.

## 4) Exclusion Set (Do Not Load in Headless)

Runtime systems to exclude:
- Network/login stack (`GameServer`, `LoginServer`, packet decode loops)
- Social systems (chat/friends/clan/trade)
- Grand Exchange processing
- Bot spawning/behavior
- Account autosave/log persistence for training loops
- Non-Fight-Caves minigames/quests/skills not in closure

Data directories to exclude by default:
- `data/activity/**`
- `data/social/**`
- `data/quest/**`
- `data/minigame/**` except `tzhaar_fight_cave` and explicitly allowlisted item source files
- `data/area/**` except explicitly allowlisted files

Code packages to exclude by default:
- `game/src/main/kotlin/content/social/**`
- `game/src/main/kotlin/content/quest/**` except `content/quest/Cutscene.kt`
- `game/src/main/kotlin/content/minigame/**` except Fight Caves-required assets/scripts

## 5) Static Validation Rules to Implement Next

Required automated checks (Step 1 tests in plan):
1. `HeadlessManifestContainsFightCaveClosureTest`
   - Assert all required script classes, data files, and IDs resolve.
2. `HeadlessManifestRejectsExcludedSystemsTest`
   - Assert excluded script/data namespaces are not loaded in headless mode.

Additional checks to add with Step 2-4:
- Startup fail-fast if a required file in allowlist is missing.
- Startup fail-fast if headless loads non-allowlisted config file.
- Script registry diff check (oracle vs headless) for explicit allowlist enforcement.

## 6) Notes on Confidence

High confidence (direct evidence):
- Fight Caves script + wave/combat/healer/Jad closure.
- 63-wave and 15-rotation data source.
- Required loadout item source files and reward object/item references.

Inference-based (must be validated by tests):
- Minimal shared definition set needed once headless module wiring bypasses full `dirs.txt` scanning.
- Whether additional animation/sound/interface files are needed for strict parity beyond no-op visual calls.





