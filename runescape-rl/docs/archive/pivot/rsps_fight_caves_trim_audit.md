# RSPS Fight Caves Trim Audit

## Scope

This audit answers whether a new near-in-game headed Fight Caves demo can be built by trimming and reusing the existing RSPS runtime. It is planning-only and based on static inspection of:

- `/home/jordan/code/RSPS/game/src/main/kotlin/Main.kt`
- `/home/jordan/code/RSPS/game/src/main/kotlin/GameTick.kt`
- `/home/jordan/code/RSPS/engine/src/main/kotlin/world/gregs/voidps/engine/event/AuditLog.kt`
- `/home/jordan/code/RSPS/engine/src/main/kotlin/world/gregs/voidps/engine/data/AccountManager.kt`
- `/home/jordan/code/RSPS/engine/src/main/kotlin/world/gregs/voidps/engine/client/PlayerAccountLoader.kt`
- `/home/jordan/code/RSPS/game/src/main/kotlin/content/entity/world/RegionLoading.kt`
- `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt`
- `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCaveWaves.kt`
- `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzTokJad.kt`
- `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/JadTelegraph.kt`
- `/home/jordan/code/RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzHaarHealers.kt`
- `/home/jordan/code/RSPS/game/src/main/kotlin/content/quest/Cutscene.kt`
- `/home/jordan/code/RSPS/game/src/main/kotlin/content/entity/player/modal/GameFrame.kt`
- `/home/jordan/code/RSPS/game/src/main/kotlin/content/skill/melee/CombatStyles.kt`
- `/home/jordan/code/RSPS/game/src/main/kotlin/content/skill/prayer/list/PrayerList.kt`
- `/home/jordan/code/RSPS/game/src/main/kotlin/GameModules.kt`
- `/home/jordan/code/RSPS/game/src/main/resources/game.properties`
- `/home/jordan/code/RSPS/network/src/main/kotlin/world/gregs/voidps/network/login/Protocol.kt`
- `/home/jordan/code/RSPS/game/src/test/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCaveTest.kt`
- `/home/jordan/code/pivot_plan.md`
- `/home/jordan/code/pivot_implementation_plan.md`
- `/home/jordan/code/RL/RLspec.md`

## Direct answers

1. **Is RSPS alone sufficient for a near-in-game headed demo?**  
   No. RSPS owns the server/runtime, mechanics, login/session bootstrap, packets, instances, and Fight Caves content, but not the near-in-game renderer/UI/assets path. The headed presentation comes from the external `void-client`.

2. **What role does `void-client` play?**  
   It is the actual 634 desktop client: login screen, gameframe/widgets, scene rendering, cache asset loading, entity rendering, packet decoding, hitsplats/projectiles, and camera/minimap.

3. **Can stock `void-client` likely be reused for a first pass?**  
   Yes, for a first pass. The client already defaults to `127.0.0.1:43594` and `skipLobby = true`. If the server spawns the player directly into a Fight Caves-only episode, stock client reuse is likely enough. A light loader/client tweak is only needed if "auto-login with no login screen/manual credential entry" becomes a requirement.

4. **Where is the cleanest seam to force the canonical episode-start state?**  
   Server-side, in Fight Caves content/runtime, not in generic login. The cleanest seam is a new guarded episode initializer invoked from:
   - `TzhaarFightCave.objectOperate("Enter", "cave_entrance_fight_cave")`
   - `TzhaarFightCave.playerSpawn` reconnect/restart path

5. **What systems absolutely cannot be removed without breaking Fight Caves play?**  
   Login + JS5/file-server, region loading, dynamic zones/instances, player and NPC update encoding, inventory/equipment, prayers, combat, ranged ammo/projectiles, movement/pathing, hitsplats/graphics, variables/interfaces, and Fight Caves content/data.

6. **What systems are likely safe to disable/remove for a Fight Caves-only runtime?**  
   Database storage, tools, bots, Grand Exchange, fairy rings, charter ships, farming, most unrelated skilling, unrelated area content/spawns, shops, quests, minigames, and broader world traversal. These should be removed by a demo-specific bootstrap/profile, not by deleting shared engine primitives.

7. **Can this stay WSL/Linux-canonical and still get the headed GUI on Windows?**  
   Yes. Recommended: keep RSPS server/runtime and docs Linux-canonical in WSL, and run `void-client` on native Windows JDK against the WSL-hosted server. WSLg is possible but is a secondary path, not the lowest-friction default.

8. **What is the smallest realistic first implementation milestone?**  
   A trimmed RSPS demo mode that accepts stock `void-client` login, immediately forces the canonical starter state, instantiates the Fight Caves, starts wave 1, blocks broader world access, and logs enough state to compare against the headless contract.

## Runtime and bootstrap ownership

| Concern | Primary ownership | Evidence | Trim implication |
|---|---|---|---|
| Server startup entrypoint | `RSPS/game/src/main/kotlin/Main.kt` | `main()` loads settings/cache/content, starts `GameServer`, `LoginServer`, `World.start(...)`, and `GameLoop(...)`. | A Fight Caves-only mode should branch here or through a demo-specific app entrypoint/profile. |
| Tick loop and stage order | `RSPS/game/src/main/kotlin/GameTick.kt`, `RSPS/engine/src/main/kotlin/world/gregs/voidps/engine/GameLoop.kt` | Tick stages include connection queue, instruction handling, world, NPC/player tasks, dynamic zones, update tasks, plus optional systems like bots, hunting, Grand Exchange. | Keep connection/instruction/world/update stages. Create a demo-specific tick list that omits unrelated stages such as bots and Grand Exchange. |
| Login/session bootstrap | `RSPS/network/.../LoginServer.kt`, `RSPS/engine/.../PlayerAccountLoader.kt`, `RSPS/engine/.../AccountManager.kt` | Login creates a `Client`, loads/creates account, calls `accounts.setup(...)`, `client.login(...)`, then `accounts.spawn(...)`. | Keep the login path. Simplify credentials/account creation if desired, but do not remove the protocol path if using stock client. |
| Player spawn initialization | `RSPS/engine/.../AccountManager.kt` | `setup()` wires interfaces, variables, inventories, levels, body, viewport, collision. `spawn()` invokes region load callback, opens gameframe, then `Spawn.player(player)`. | Keep as generic plumbing. Do not encode Fight Caves-specific starter state here. |
| Region and instance loading | `RSPS/game/src/main/kotlin/content/entity/world/RegionLoading.kt`, `RSPS/game/src/main/kotlin/content/quest/Cutscene.kt`, `RSPS/engine/.../DynamicZones.kt`, `RSPS/engine/.../Instances.kt` | Dynamic and static region packets are sent here; Fight Caves uses `smallInstance(...)` and dynamic region copies. | Must keep. This is part of the minimal headed path. |
| Audit logging | `RSPS/engine/src/main/kotlin/world/gregs/voidps/engine/event/AuditLog.kt` | Login, disconnect, and Fight Caves start/end already log to audit output. | Keep for debuggability; it is already aligned with the demo goal. |

## Fight Caves content ownership

| Responsibility | Primary file(s) | Notes |
|---|---|---|
| Fight Caves entry/exit | `RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt` | `objectOperate("Enter", "cave_entrance_fight_cave")` creates the instance, teleports the player in, and calls `startWave(...)`. Exit confirmation also lives here. |
| Wave progression | `RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt` | `npcDespawn(...)` decrements `fight_cave_remaining` and advances wave/end state. `startWave(...)` opens the cave interface, sets wave vars, chooses rotation, and spawns NPCs. |
| Wave data/rotations | `RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCaveWaves.kt` and `RSPS/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave_waves.toml` | Fight Caves NPC composition and spawn rotations are loaded from data. |
| Jad attacks and telegraph state | `RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzTokJad.kt`, `RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/JadTelegraph.kt` | Jad attack delay, client-visible telegraph state, prayer sampling, and resolved hit metadata are here. |
| Healers | `RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzHaarHealers.kt` | Healer targeting and Jad heal-over-time logic are separate and must remain. |
| Split Keks | `RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt` | `npcCombatDamage` and `npcDeath` handlers implement split behavior. |
| Restart after reconnect | `RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt` | `playerSpawn` recreates the instance and restarts the saved wave. |
| Rewards/leave/reset | `RSPS/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt` | `leave(...)`, exit handling, cooldown, and reward payout live here. |
| Regression coverage | `RSPS/game/src/test/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCaveTest.kt` | Existing tests cover completion, Jad healers, logout, reconnect, death, and teleport-out behavior. |

## Canonical starter-state seam

The required episode-start contract is documented in `/home/jordan/code/RL/RLspec.md` section `7.1.1 Episode-start state contract`:

- Rune crossbow
- Adamant bolts, default ammo `1000`
- Coif, black d'hide body/chaps/vambs, snakeskin boots
- 8x 4-dose prayer potion
- 20x shark
- Fixed skills and resources
- Full HP, full prayer
- Run energy 100%, run mode ON
- No XP gain

### Recommended ownership

The canonical starter state should be enforced by a new server-side Fight Caves episode initializer, called from the Fight Caves runtime path itself.

Recommended call sites:

1. `TzhaarFightCave.objectOperate("Enter", "cave_entrance_fight_cave")`
2. `TzhaarFightCave.playerSpawn` when restoring a demo episode or reconnecting into the cave

Recommended responsibilities of that initializer:

- clear and repopulate inventory and equipment
- force ammo type/count
- set or clamp skills and current resources
- set Constitution/HP and prayer to full
- force run energy and run toggle
- set spawn tile/start wave/rotation constants
- clear or block XP gain for the episode
- clear unrelated transient state from persisted saves

### Why this is the cleanest seam

- It is already the real ownership point for Fight Caves instance creation and wave start.
- It avoids polluting generic account/login bootstrap with Fight Caves-only semantics.
- It can be re-used from reconnect/restart flows.
- It lines up with the user-visible contract: entering the demo means entering a new canonical episode.

### Seams that are less suitable

- `AccountManager.setup(...)`: too low-level; should remain generic player wiring.
- `AccountManager.create(...)`: only affects brand-new accounts and would not protect against persisted drift.
- Generic `playerSpawn` or introduction logic: too broad and currently tied to world/home/new-player behavior.

### No-XP-gain seam

Evidence:

- `RSPS/game/src/main/kotlin/content/entity/player/combat/CombatExperience.kt` grants combat XP.
- `RSPS/engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/skill/exp/Experience.kt` supports blocked skills via `blocked`.

Practical recommendation:

- block all skills for demo episodes at episode start, or gate combat XP scripts behind a demo-mode check
- keep the no-XP rule server-side so the headed demo and headless parity checks can use the same contract

## Systems that must stay for real Fight Caves play

### Core runtime and protocol

- `RSPS/game`, `RSPS/engine`, `RSPS/network`, `RSPS/cache`, `RSPS/config`, `RSPS/types`, `RSPS/buffer`
- login protocol and JS5/file-server path
- region loading and dynamic region packets
- player and NPC update tasks
- variable/interface/container packet encoders

### Fight Caves mechanics

- `TzhaarFightCave.kt`
- `TzhaarFightCaveWaves.kt`
- `TzTokJad.kt`
- `JadTelegraph.kt`
- `TzHaarHealers.kt`
- associated `RSPS/data/minigame/tzhaar_fight_cave/*`

### Combat, movement, and resources

- ranged combat and ammo handling
- prayer activation and drain
- eating and potion consumption
- player movement, run energy, pathing
- NPC aggression/chasing/following
- projectiles, graphics, hitsplats, and overhead updates
- inventory and equipment state containers

### UI-facing server dependencies

| UI surface | Key server files | Notes |
|---|---|---|
| Gameframe/tab open state | `RSPS/game/src/main/kotlin/content/entity/player/modal/GameFrame.kt`, `RSPS/engine/src/main/kotlin/world/gregs/voidps/engine/client/ui/Interfaces.kt` | The RSPS server opens the gameframe and tab interfaces. |
| Combat tab | `RSPS/game/src/main/kotlin/content/skill/melee/CombatStyles.kt` | Sends attack style, special energy, and auto-retaliate vars; unlocks interface components. |
| Prayer tab/orb | `RSPS/game/src/main/kotlin/content/skill/prayer/list/PrayerList.kt` | Sends prayer config vars and unlocks prayer widgets. |
| Inventory/equipment | `RSPS/engine/src/main/kotlin/world/gregs/voidps/engine/inv/Inventories.kt`, `RSPS/network/.../ContainerEncoders.kt` | Inventory/equipment widget state is packet-driven. |
| Region/scene load | `RSPS/game/src/main/kotlin/content/entity/world/RegionLoading.kt` | Required for map/instance visibility. |
| Visual updates | `RSPS/network/src/main/kotlin/world/gregs/voidps/network/login/Protocol.kt` | Fight Caves play depends on `PLAYER_UPDATING`, `NPC_UPDATING`, `PROJECTILE_ADD`, `GRAPHIC_AREA`, `INTERFACE_*`, `CLIENT_VARP/VARBIT`, and region packets. |

## Must-keep vs can-disable matrix

| Category | Disposition | Concrete notes |
|---|---|---|
| `engine` | **Must keep** | Core entity/runtime/state systems, queues, timers, collisions, instances, variables, and inventory plumbing are foundational. |
| `game` | **Must keep, but trim** | Keep Fight Caves, combat, movement, prayers, inventory, region loading, and gameframe support. Create a demo-specific bootstrap rather than loading all game content. |
| `network` | **Must keep** | Required for login, JS5, map load, interface updates, inventory packets, player/NPC/projectile updates. |
| `cache` | **Must keep** | Needed for definitions, maps, interfaces, animations, graphics, items, and models consumed by server and client. |
| `data` | **Must keep large parts; risky to prune aggressively** | Fight Caves-only runtime still depends on shared item/NPC/interface/var/graphic data. Broad data deletion is risky because cross-references are implicit. Prefer a selective load/profile over destructive pruning. |
| `tools` | **Can likely disable** | No evidence that tools are required for runtime play. |
| Persistence/database | **Database can likely disable** | `GameModules.kt` already defaults to file storage. First pass should stay on file storage or a lightweight synthetic/ephemeral storage path. |
| Login/auth flow | **Must keep, but can simplify** | Stock client expects real login and file-server paths. Credentials can be relaxed via existing account-creation behavior. |
| Dev/admin commands | **Can likely disable in demo mode** | Useful during audit/bring-up, but not required for the final demo path. Admin wave select in `TzhaarFightCave.kt` should remain non-default. |
| Bots | **Can likely disable** | `GameModules.kt` and `GameTick.kt` currently load/tick bot systems; demo mode can omit them. |
| Grand Exchange | **Can likely disable** | Explicitly loaded/ticked in `GameModules.kt` and `GameTick.kt`; unrelated to Fight Caves. |
| World map traversal/content outside Fight Caves | **Can likely disable or gate** | Force login spawn into the cave, suppress unrelated teleports/objects, and prevent leaving the instance except through the demo reset path. |
| Unrelated skilling | **Can likely disable** | Only keep consumables/equipment behaviors required inside Fight Caves. |
| Shops/quests/minigames unrelated to Fight Caves | **Can likely disable** | Remove from the demo-specific bootstrap/content profile. |
| Broad interface/gameframe surface | **Unknown / risky to remove partially** | The client gameframe expects many standard tabs/widgets. Leave unused tabs inert before attempting deep UI pruning. |
| Broad data/config pruning | **Unknown / risky to remove** | Interface, varp, item, NPC, animation, and graphic definitions are highly cross-referenced. |

## Practical RSPS trim seams

### Strong trim seam: alternate bootstrap/profile

Best place to trim unrelated systems:

- a demo-specific app entrypoint or profile layered on top of `Main.kt`
- a demo-specific Koin module instead of the current `gameModule(files)` that loads bots, GE, fairy rings, charter ships, farming, and other unrelated systems
- a demo-specific tick stage list derived from `getTickStages()` with only the stages needed for networked Fight Caves play

### Weak trim seam: deleting engine primitives

Avoid treating these as trim targets:

- instances/dynamic zones
- collision/pathing
- region loading
- player or NPC update encoders
- container/interface/variable encoding

These are not "extra RSPS world systems"; they are part of the minimal real-client contract.

## Recommended first-pass RSPS target

The minimal viable trimmed RSPS runtime should:

- keep stock login + JS5/file-server compatibility
- load only the data/config needed for a normal Fight Caves run plus the shared client-facing definitions it depends on
- spawn the player straight into a canonical Fight Caves episode after login
- use `TzhaarFightCave` as the mechanics owner instead of rewriting cave logic
- block exit into the broader world
- preserve audit logging and add a starter-state manifest/check path

That is the smallest slice that still looks and behaves like the real RSPS/Void game path instead of a second custom demo engine.
