# Fight Caves Windows Headed Deep Dive

Date: 2026-03-30

Scope: This document explains, in concrete code-level terms, how the current playable headed Fight Caves demo is built, booted, loaded, rendered, and driven on this workspace's Windows + WSL topology. It is intended as a reverse-engineering guide for a future C/raylib port.

This is not the old `demo-env` path. The real headed Fight Caves path lives in [`/home/jordan/code/runescape-rl/rsps`](/home/jordan/code/runescape-rl/rsps). [`/home/jordan/code/runescape-rl/demo-env`](/home/jordan/code/runescape-rl/demo-env) is frozen fallback/reference only.

## 1. Runtime Topology

### 1.1 Process split

The playable Fight Caves headed demo is a split-runtime system:

- WSL/Linux runs the RSPS server/runtime.
- Native Windows runs the headed `void-client` Java client.
- The server and client talk over the normal RS2-era game protocol on port `43594`.

The current live headed path uses:

- source root: [`/home/jordan/code/runescape-rl`](/home/jordan/code/runescape-rl)
- runtime root: [`/home/jordan/code/runescape-rl-runtime`](/home/jordan/code/runescape-rl-runtime)
- RSPS module: [`/home/jordan/code/runescape-rl/rsps`](/home/jordan/code/runescape-rl/rsps)
- shared RSPS cache payload: [`/home/jordan/code/runescape-rl/training-env/sim/data/cache`](/home/jordan/code/runescape-rl/training-env/sim/data/cache)
- RSPS-facing cache symlink: [`/home/jordan/code/runescape-rl/rsps/data/cache`](/home/jordan/code/runescape-rl/rsps/data/cache)

### 1.2 Commands used in this environment

Boot the server in WSL:

```bash
source /home/jordan/code/.workspace-env.sh
cd /home/jordan/code/runescape-rl/rsps
./gradlew --no-daemon :game:runFightCavesDemo
```

Boot the headed client from WSL, which delegates to Windows PowerShell + Windows Java:

```bash
cd /home/jordan/code/runescape-rl/rsps
./scripts/run_fight_caves_demo_client.sh
```

The launcher defaults to:

- server port: `43594`
- account: `fcdemo01`
- password: `pass123`
- direct autologin enabled
- hide real client frame until in-game enabled

### 1.3 Working transport path on this machine

The client does not use `127.0.0.1` here. The working path on this machine is the WSL IP:

- server address used in the live run: `172.25.183.199`
- port: `43594`

Evidence from the live client log:

- [`/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/client_logs/client-20260330T174101-stdout.log`](/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/client_logs/client-20260330T174101-stdout.log)

Key lines:

- `FIGHT_CAVES_DEMO_AUTOLOGIN_ENABLED address=172.25.183.199 port=43594`
- `FIGHT_CAVES_DEMO_FRAME_REVEAL reason=ready_state_11 elapsed_ms=28570`
- `FIGHT_CAVES_DEMO_READY elapsed_ms=28570 login_roundtrip_ms=16065`

## 2. Exact Build And Boot Chain

## 2.1 Server boot chain

The server-side headed Fight Caves profile is registered in:

- [`/home/jordan/code/runescape-rl/rsps/game/build.gradle.kts`](/home/jordan/code/runescape-rl/rsps/game/build.gradle.kts)

Key task:

- `runFightCavesDemo`

That task launches:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/FightCavesDemoMain.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/FightCavesDemoMain.kt)

That entrypoint does exactly one thing:

- `Main.start(GameProfiles.fightCavesDemo)`

The profile is defined in:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/GameProfiles.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/GameProfiles.kt)

The `fightCavesDemo` profile layers:

- `game.properties`
- `fight-caves-demo.properties`

and then runs:

- `FightCavesDemoBootstrap.install()`

The demo profile overrides live in:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/resources/fight-caves-demo.properties`](/home/jordan/code/runescape-rl/rsps/game/src/main/resources/fight-caves-demo.properties)

Important properties:

- `server.name=Void Fight Caves Demo`
- `development.accountCreation=true`
- `world.start.creation=false`
- `storage.players.path=./data/fight_caves_demo/saves/`
- `storage.players.logs=./data/fight_caves_demo/logs/`
- `fightCave.demo.artifacts.path=./data/fight_caves_demo/artifacts/`
- `fightCave.demo.enabled=true`
- `fightCave.demo.skipBotTick=true`
- `fightCave.demo.backendControl.enabled=true`
- `fightCave.demo.backendControl.root=./data/fight_caves_demo/backend_control/`

The generic server boot path is:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/Main.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/Main.kt)

`Main.start(...)` performs:

1. property load via `Settings.load(...)`
2. cache load via `Cache.load(settings)`
3. JS5/file-server boot via `GameServer.load(cache, settings)` and `server.start(...)`
4. content/config preload via Koin + config/data definition loaders
5. demo post-preload install via `profile.afterPreload()`
6. login server creation via `LoginServer.load(...)`
7. world start via `World.start(configFiles)`
8. tick-loop start via `GameLoop(getTickStages())`

### 2.2 Demo profile login hook

The demo bootstrap layer is installed by:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/GameProfiles.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/GameProfiles.kt)

It wraps:

- `AccountManager.loadCallback`

For every login on this profile it marks:

- `fight_cave_demo_profile = true`
- `fight_cave_demo_entry_pending = true`

and starts demo observability:

- `FightCaveDemoObservability.beginSession(player)`

This is the first point where a normal RSPS login is diverted into a Fight Caves-only headed runtime.

### 2.3 Client build chain

The client launcher entrypoint is:

- [`/home/jordan/code/runescape-rl/rsps/scripts/run_fight_caves_demo_client.sh`](/home/jordan/code/runescape-rl/rsps/scripts/run_fight_caves_demo_client.sh)

This shell script:

1. resolves the WSL IP via `hostname -I`
2. defaults to port `43594`
3. chooses the patched convenience jar:
   - [`/home/jordan/code/runescape-rl/.artifacts/void-client/void-client-fight-caves-demo.jar`](/home/jordan/code/runescape-rl/.artifacts/void-client/void-client-fight-caves-demo.jar)
4. auto-builds that jar if missing
5. defaults credentials to `fcdemo01` / `pass123`
6. delegates final launch to a Windows PowerShell script

If the patched jar is missing, it is built by:

- [`/home/jordan/code/runescape-rl/rsps/scripts/build_fight_caves_demo_client.sh`](/home/jordan/code/runescape-rl/rsps/scripts/build_fight_caves_demo_client.sh)

which delegates to:

- [`/home/jordan/code/runescape-rl/rsps/scripts/build_fight_caves_demo_client.ps1`](/home/jordan/code/runescape-rl/rsps/scripts/build_fight_caves_demo_client.ps1)

The PowerShell build script:

1. resolves Windows `javac.exe` and `jar.exe` from a JDK 8 installation
2. compiles all Java files in:
   - [`/home/jordan/code/runescape-rl/rsps/void-client/client/src`](/home/jordan/code/runescape-rl/rsps/void-client/client/src)
3. copies all resources from:
   - [`/home/jordan/code/runescape-rl/rsps/void-client/client/resources`](/home/jordan/code/runescape-rl/rsps/void-client/client/resources)
4. extracts:
   - [`/home/jordan/code/runescape-rl/rsps/void-client/libs/clientlibs.jar`](/home/jordan/code/runescape-rl/rsps/void-client/libs/clientlibs.jar)
5. builds a jar whose manifest `Main-Class` is `Loader`

This means the headed client is a locally compiled Java 8 applet wrapper plus the original decompiled client code and library payload.

### 2.4 Client launch chain

Final Windows launch is done by:

- [`/home/jordan/code/runescape-rl/rsps/scripts/launch_fight_caves_demo_client.ps1`](/home/jordan/code/runescape-rl/rsps/scripts/launch_fight_caves_demo_client.ps1)

That script:

1. resolves a Windows Java 8 runtime
2. syncs `main_file_cache*` files from the WSL cache into:
   - `C:\.jagex_cache_32\runescape`
3. seeds:
   - `%USERPROFILE%\jagex_runescape_preferences.dat`
4. launches:
   - `java.exe -jar <jar> --address <wsl-ip> --port 43594 --username ... --password ...`
5. redirects stdout/stderr into:
   - [`/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/client_logs`](/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/client_logs)

Because [`/home/jordan/code/runescape-rl/rsps/data/cache`](/home/jordan/code/runescape-rl/rsps/data/cache) is a symlink to [`/home/jordan/code/runescape-rl/training-env/sim/data/cache`](/home/jordan/code/runescape-rl/training-env/sim/data/cache), the client ends up consuming the same core cache payload that the headless/training side is anchored to.

## 3. Frontend: Rendering, Loading, UI, Assets, Scene

## 3.1 Frontend ownership split

The frontend is not rendered by Swing itself. The frontend has two layers:

1. Swing wrapper/bootstrap layer
2. actual decompiled RS2/RSPS client applet and renderer

The Swing wrapper is responsible for:

- the outer native window
- icon loading
- the temporary loading bar window
- hiding/revealing the real client frame
- autologin state monitoring

The real scene, gameframe, terrain, entities, overlays, and tab interfaces are rendered by the decompiled `void-client`, not by Swing.

### 3.2 Loader and outer frame

Main client entrypoint:

- [`/home/jordan/code/runescape-rl/rsps/void-client/client/src/Loader.java`](/home/jordan/code/runescape-rl/rsps/void-client/client/src/Loader.java)

`Loader` does the following:

1. parses CLI args:
   - `--address`
   - `--port`
   - `--username`
   - `--password`
   - `--show-during-bootstrap`
   - `--hide-until-ingame`
2. sets:
   - `skipLobby = true`
3. seeds client parameters such as:
   - `worldid`
   - `lobbyaddress`
   - `cachesubdirid`
   - `worldflags`
4. creates Swing `JFrame("Client")`
5. loads icon PNGs from resources:
   - `icon-16.png`
   - `icon-32.png`
   - `icon-64.png`
   - `icon-128.png`
   - `icon-256.png`
6. creates the real game client applet:
   - `client var_client = new client();`
7. calls:
   - `var_client.init();`
   - `var_client.start();`

The applet is embedded into the Swing frame via:

- `aJPanel3.add(this);`

That is the point where the actual decompiled RSPS client starts its own bootstrap, JS5, and render loop.

### 3.3 Custom loading window and autologin

Autologin controller:

- [`/home/jordan/code/runescape-rl/rsps/void-client/client/src/FightCavesDemoAutoLogin.java`](/home/jordan/code/runescape-rl/rsps/void-client/client/src/FightCavesDemoAutoLogin.java)

Bootstrap window:

- [`/home/jordan/code/runescape-rl/rsps/void-client/client/src/FightCavesDemoBootstrapWindow.java`](/home/jordan/code/runescape-rl/rsps/void-client/client/src/FightCavesDemoBootstrapWindow.java)

How it works:

1. If username/password are provided and visibility is not explicitly overridden, `Loader` sets:
   - `hideUntilInGame = true`
2. `Loader` shows a separate Swing-only loading window.
3. A daemon thread polls decompiled client state integers:
   - `Class240.anInt4674`
   - `Class225.anInt2955`
   - `Class367_Sub2.anInt7297`
4. When the client is at login-ready state:
   - `clientState == 3`
   - `loginStage == 0`
   - `modalState == 0`
   it submits credentials via:
   - `Class253.method1922(password, 0, username, true);`
5. The loading bar text is inferred from state:
   - `Checking for updates`
   - `Preparing login`
   - `Logging in`
   - `Loading Fight Caves`
6. The real client frame is only revealed when in-game states are reached:
   - client state `7`, `10`, or `11`
   - login stage `0`

This is why the current headed path feels like "boot straight into the cave" even though the underlying client still performs the normal client bootstrap and login protocol.

### 3.4 File-store/cache loading

The client still expects the legacy cache/file-store layout.

Important classes:

- [`/home/jordan/code/runescape-rl/rsps/void-client/client/src/Class201.java`](/home/jordan/code/runescape-rl/rsps/void-client/client/src/Class201.java)
- [`/home/jordan/code/runescape-rl/rsps/void-client/client/src/Class297.java`](/home/jordan/code/runescape-rl/rsps/void-client/client/src/Class297.java)
- [`/home/jordan/code/runescape-rl/rsps/void-client/client/src/client.java`](/home/jordan/code/runescape-rl/rsps/void-client/client/src/client.java)

Operationally:

1. The launcher copies `main_file_cache*` sectors into:
   - `C:\.jagex_cache_32\runescape`
2. The client opens that local cache store on Windows.
3. The client then talks to the server JS5/file-server for verification, index/prefetch, and region/interface/model archive access.

This is why the headed render path is near-authentic: it uses the real cache archives, not reconstructed assets.

### 3.5 JS5/file-server asset flow

Server-side JS5 owner:

- [`/home/jordan/code/runescape-rl/rsps/network/src/main/kotlin/world/gregs/voidps/network/FileServer.kt`](/home/jordan/code/runescape-rl/rsps/network/src/main/kotlin/world/gregs/voidps/network/FileServer.kt)

The file-server:

1. checks client revision
2. sends prefetch keys
3. acknowledges JS5 session handshake
4. serves cache archive requests

The client uses this to load:

- interface definitions
- sprites
- map squares
- landscape data
- model/animation data
- graphics/projectile definitions

This protocol surface is required for:

- terrain
- NPC/player models
- prayer icons
- hitmarks
- map/minimap art
- Fight Caves interface overlay

### 3.6 Region and terrain loading

The client region loader is rooted in:

- [`/home/jordan/code/runescape-rl/rsps/void-client/client/src/Class101.java`](/home/jordan/code/runescape-rl/rsps/void-client/client/src/Class101.java)

`Class101.method893(...)`:

1. computes the current region around the local player
2. requests the corresponding archive ids:
   - `mX_Y`
   - `lX_Y`
   - `nX_Y`
   - `umX_Y`
   - `ulX_Y`
3. hands off to relocation/reset logic:
   - `Class348_Sub41.method3157(...)`

This is the actual terrain/map-square path that makes the Fight Caves arena floor, rock formations, walls, and cave layout appear on screen. The server side only places the player inside a dynamic instance; the client still has to load the correct map and landscape archives for the copied region.

### 3.7 Local player creation and world entry

The local player is created from incoming login/region packets in:

- [`/home/jordan/code/runescape-rl/rsps/void-client/client/src/Class239_Sub5.java`](/home/jordan/code/runescape-rl/rsps/void-client/client/src/Class239_Sub5.java)

`Class239_Sub5.method1741(...)`:

1. creates the local `Player` object
2. decodes packed 30-bit position and plane
3. initializes the local player tile
4. installs saved appearance block if present
5. seeds the local and nearby-player tracking arrays

This is the moment the client has enough state to begin rendering the player in the scene.

### 3.8 Player appearance, equipment, head icons, and gear visuals

Server-side appearance encoder:

- [`/home/jordan/code/runescape-rl/rsps/network/src/main/kotlin/world/gregs/voidps/network/login/protocol/visual/encode/player/AppearanceEncoder.kt`](/home/jordan/code/runescape-rl/rsps/network/src/main/kotlin/world/gregs/voidps/network/login/protocol/visual/encode/player/AppearanceEncoder.kt)

Client-side appearance decoder:

- [`/home/jordan/code/runescape-rl/rsps/void-client/client/src/Player.java`](/home/jordan/code/runescape-rl/rsps/void-client/client/src/Player.java)

The server encoder writes:

- display name
- combat level
- summoning/skill display level
- body colours
- skull icon
- head icon
- title
- transform id if any
- the 12 body/equipment slots

The client decoder `Player.method2452(...)` reconstructs:

- body/equipment model parts
- body colours
- display name
- combat/skill level display
- title/head/skull metadata
- render identity and model refresh triggers

This is why the player appears wearing:

- coif
- rune crossbow
- black d'hide body
- black d'hide chaps
- black d'hide vambraces
- snakeskin boots
- adamant bolts

Those items are not hardcoded in the client. The server sets the equipment container and appearance payload; the client decodes them and resolves the corresponding models from cache.

### 3.9 NPC rendering

NPC scene entities are represented client-side by decompiled scene/model classes, notably:

- [`/home/jordan/code/runescape-rl/rsps/void-client/client/src/Class318_Sub1_Sub5_Sub2.java`](/home/jordan/code/runescape-rl/rsps/void-client/client/src/Class318_Sub1_Sub5_Sub2.java)

Those entities build render models using cache-backed definitions and the engine renderer abstraction:

- `ha`
- `ha_Sub2`
- `Class101`

The server does not send literal meshes. It sends:

- NPC spawn/update packets
- animation ids
- gfx ids
- projectile ids
- hit data

The client maps those numeric ids to cached art and plays the correct models and animations for `tz_kih`, `tz_kek`, `tok_xil`, `ket_zek`, `tztok_jad`, and healer minions.

### 3.10 UI sprites, icons, hitsplats, prayer icons, minimap art

The client sprite archive binding for many UI overlays is visible in:

- [`/home/jordan/code/runescape-rl/rsps/void-client/client/src/Class286_Sub5.java`](/home/jordan/code/runescape-rl/rsps/void-client/client/src/Class286_Sub5.java)

`Class286_Sub5.method2159(...)` resolves sprite archive ids for:

- `hitmarks`
- `hitbar_default`
- `timerbar_default`
- `headicons_pk`
- `headicons_prayer`
- `hint_headicons`
- `hint_mapmarkers`
- `mapflag`
- `cross`
- `mapdots`
- `scrollbar`
- `name_icons`
- `floorshadows`
- `compass`
- `otherlevel`
- `hint_mapedge`

This is the asset path for:

- prayer icons above heads
- hitsplats
- health bars
- minimap dots/flag/crosshair
- many gameframe overlays

### 3.11 Gameframe, prayer tab, inventory tab, combat tab

The generic gameframe is opened server-side in:

- [`/home/jordan/code/runescape-rl/rsps/engine/src/main/kotlin/world/gregs/voidps/engine/data/AccountManager.kt`](/home/jordan/code/runescape-rl/rsps/engine/src/main/kotlin/world/gregs/voidps/engine/data/AccountManager.kt)

Specifically:

- `player.open(player.interfaces.gameFrame)`

Gameframe/interface ownership:

- [`/home/jordan/code/runescape-rl/rsps/engine/src/main/kotlin/world/gregs/voidps/engine/client/ui/Interfaces.kt`](/home/jordan/code/runescape-rl/rsps/engine/src/main/kotlin/world/gregs/voidps/engine/client/ui/Interfaces.kt)

This chooses between:

- fixed `toplevel`
- resizable `toplevel_full`

and emits the interface packets that instruct the client to display the proper root frame and child panes.

Generic packet encoders for this UI path:

- [`/home/jordan/code/runescape-rl/rsps/network/src/main/kotlin/world/gregs/voidps/network/login/protocol/encode/InterfaceEncoders.kt`](/home/jordan/code/runescape-rl/rsps/network/src/main/kotlin/world/gregs/voidps/network/login/protocol/encode/InterfaceEncoders.kt)
- [`/home/jordan/code/runescape-rl/rsps/network/src/main/kotlin/world/gregs/voidps/network/login/protocol/encode/ContainerEncoders.kt`](/home/jordan/code/runescape-rl/rsps/network/src/main/kotlin/world/gregs/voidps/network/login/protocol/encode/ContainerEncoders.kt)

These packets drive:

- opening/closing interfaces
- interface text
- interface sprites
- item widgets
- inventory/equipment containers
- interface visibility
- component settings

The Fight Caves overlay itself comes from:

- [`/home/jordan/code/runescape-rl/rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.ifaces.toml`](/home/jordan/code/runescape-rl/rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.ifaces.toml)

and is opened by:

- `TzhaarFightCave.startWave(...)`

Prayer-book state is normalized server-side by:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/skill/prayer/list/PrayerList.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/skill/prayer/list/PrayerList.kt)

It forces demo accounts onto the normal prayer book and resends:

- `prayers`
- `active_prayers`
- `active_curses`
- quick-prayer state vars

Inventory/equipment container updates flow through:

- [`/home/jordan/code/runescape-rl/rsps/engine/src/main/kotlin/world/gregs/voidps/engine/inv/Inventories.kt`](/home/jordan/code/runescape-rl/rsps/engine/src/main/kotlin/world/gregs/voidps/engine/inv/Inventories.kt)

and then:

- `sendInventoryItems(...)`

### 3.12 Input path

Normal human play uses the client’s standard input path:

- mouse clicks and keyboard input are interpreted by the decompiled client
- the client converts them into normal RS2 input/opcode packets
- server decoders and instruction handlers turn them into walk, interface, item, prayer, and combat actions

This document does not fully map every UI input opcode, but the playable path depends on that unchanged stock-client input handling rather than on any custom synthetic UI-click layer.

## 4. Backend: Server Runtime, State, Mechanics

## 4.1 Network contract

The headed demo remains a real client/server game.

Server network owners:

- [`/home/jordan/code/runescape-rl/rsps/network/src/main/kotlin/world/gregs/voidps/network/GameServer.kt`](/home/jordan/code/runescape-rl/rsps/network/src/main/kotlin/world/gregs/voidps/network/GameServer.kt)
- [`/home/jordan/code/runescape-rl/rsps/network/src/main/kotlin/world/gregs/voidps/network/LoginServer.kt`](/home/jordan/code/runescape-rl/rsps/network/src/main/kotlin/world/gregs/voidps/network/LoginServer.kt)
- [`/home/jordan/code/runescape-rl/rsps/network/src/main/kotlin/world/gregs/voidps/network/FileServer.kt`](/home/jordan/code/runescape-rl/rsps/network/src/main/kotlin/world/gregs/voidps/network/FileServer.kt)

The client connects with request opcodes that split into:

- login connection
- JS5/file-server connection

The login server performs:

1. revision check
2. RSA block handling
3. XTEA session decode
4. password validation
5. client ISAAC cipher setup
6. account load
7. transition to in-game packet loop

This is the standard live protocol surface the client expects. The headed demo works because the RSPS runtime still speaks the normal protocol.

### 4.2 Account load and gameframe spawn

Account ownership:

- [`/home/jordan/code/runescape-rl/rsps/engine/src/main/kotlin/world/gregs/voidps/engine/data/AccountManager.kt`](/home/jordan/code/runescape-rl/rsps/engine/src/main/kotlin/world/gregs/voidps/engine/data/AccountManager.kt)

Important steps:

- `create(...)` creates a new local account if allowed
- `setup(...)` attaches:
  - interfaces
  - variables
  - inventories
  - appearance/body
  - viewport
  - collision
- `spawn(...)`:
  - invokes the demo bootstrap `loadCallback`
  - opens the gameframe
  - spawns the player into the world
  - triggers area-enter handling

This is why the client can immediately show a normal gameframe and world scene after login instead of a custom one-off UI.

### 4.3 Tick order

Tick pipeline:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/GameTick.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/GameTick.kt)

Important stage order:

1. player reset
2. NPC reset
3. connection/input queue
4. NPC logic
5. floor items
6. demo backend-control stage
7. decoded instruction execution
8. world logic
9. NPC combat/task phase
10. player task phase
11. timers and dynamic zones
12. zone batch updates
13. player and NPC update packet construction
14. save queue and logs

This matters because the frontend only becomes "playable" if:

- movement instructions are applied before update encode
- NPC combat runs before update encode
- wave resets happen before the scene update packet is built
- interface/container/variable updates are emitted before the next client frame

### 4.4 Fight Caves runtime owner

The central Fight Caves owner is:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt)

It owns:

- entry object behavior
- exit object behavior
- instance entry/exit
- wave start
- wave completion
- Jad completion / fire cape reward path
- death/reset handling
- demo-specific recycle/gating behavior

### 4.5 Canonical demo episode reset

The demo starter-state and fresh-instance seam is:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveEpisodeInitializer.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveEpisodeInitializer.kt)

The reset sequence is:

1. clear queues/timers/steps/watch/anim/gfx/attack state
2. clear old instance and NPCs
3. clear Fight Caves vars
4. suppress unrelated content noise and task popups
5. force normal prayer book
6. clear active curses/quick-prayer vars/drain state
7. overwrite skills and block XP gain
8. restore run energy and set `running = true`
9. reset equipment
10. reset inventory
11. create a fresh small instance copied from the Fight Caves region
12. teleport to the cave centre inside that instance
13. start the requested wave immediately
14. after a short follow-up delay, force NPC aggression back onto the player

This is the server-side seam that makes the headed demo deterministic and fresh each time.

### 4.6 Live starter-state proof from this run

Manifest:

- [`/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/artifacts/starter_state_manifests/fcdemo01-2026-03-30T17-41-31.478_reset_001.json`](/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/artifacts/starter_state_manifests/fcdemo01-2026-03-30T17-41-31.478_reset_001.json)

Confirmed live values:

- prayer book: `normal`
- active prayers: empty
- active curses: empty
- wave: `1`
- rotation: `14`
- instance id: `25857`
- tile: `(6496, 96, 0)`
- run energy: `10000`
- running: `true`
- equipment:
  - coif
  - rune crossbow
  - black d'hide body
  - black d'hide chaps
  - black d'hide vambraces
  - snakeskin boots
  - adamant bolts x1000
- inventory:
  - 8 prayer potion(4)
  - 20 sharks
- blocked skill XP gain

### 4.7 Starting, ending, leaving, restarting instance

Entry path:

- `objectOperate("Enter", "cave_entrance_fight_cave")`

Demo behavior:

- records entry
- calls `resetDemoEpisode(...)`

Exit path:

- `objectOperate("Enter", "cave_exit_fight_cave")`

Demo behavior:

- records leave
- schedules reset instead of returning player to the broader world

World gating:

- `exited("tzhaar_fight_cave_multi_area")`

Demo behavior:

- logs world-access violation
- resets player back into Fight Caves

Death path:

- `playerDeath { ... }`

Demo behavior:

- suppresses normal drop/exit path
- teleports to the cave centre in instance space
- schedules demo death reset into a fresh episode

Completion path:

- `leave(wave, defeatedJad = true)`

Demo behavior:

- records recycle
- schedules reset instead of ending the headed loop

So for the demo path, start, end, death, and leave all converge on the same concept:

- fresh re-entry into a clean Fight Caves episode

## 4.8 Wave progression and spawn placement

Wave data owner:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCaveWaves.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCaveWaves.kt)
- backed by:
  - [`/home/jordan/code/runescape-rl/rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave_waves.toml`](/home/jordan/code/runescape-rl/rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave_waves.toml)

`startWave(...)`:

1. opens the Fight Caves overlay
2. sets `fight_cave_wave`
3. picks rotation `1..15` on wave 1
4. writes `fight_cave_remaining`
5. gets NPC ids for that wave
6. gets region-direction spawn slots for the current rotation
7. offsets them into the player’s dynamic instance
8. picks collision-safe tiles inside those sub-areas
9. spawns NPCs and immediately instructs them to attack

NPC death progression:

- when `fight_cave_remaining` reaches zero:
  - if wave < 63, start next wave
  - if Jad is defeated on wave 63, exit/recycle

### 4.9 Movement and pathing

Movement owner:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/entity/Movement.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/entity/Movement.kt)

Movement instruction handling:

1. incoming walk instruction clears modal/UI noise
2. clears watch target and weak queues
3. resolves border cases if present
4. otherwise calls `player.walkTo(target)`

The system also manages collision occupancy for:

- players
- NPCs

This matters for Fight Caves kiting, line-of-sight, melee reach, and spawn placement.

### 4.10 Combat resolution

Generic NPC combat owner:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/entity/npc/combat/Attack.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/entity/npc/combat/Attack.kt)

Generic damage owner:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/entity/combat/hit/Damage.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/entity/combat/hit/Damage.kt)

The attack flow is:

1. pick a combat definition for the NPC
2. choose a valid attack based on conditions and range
3. play source anim/gfx/sound
4. optionally path into range
5. spawn projectile(s)
6. roll hit(s) against the target
7. schedule delayed impacts if ranged/magic/projectile-based
8. apply impact animations, gfx, sounds, drains, poison, freeze, messages

`Damage.roll(...)` and `Damage.modify(...)` calculate:

- accuracy success
- base max hit
- min hit
- stance bonus
- prayer effective level bonus
- void modifiers
- spell modifiers
- slayer modifiers
- weapon damage modifiers
- equipment damage modifiers
- special attack modifiers
- prayer damage modifiers
- target damage caps/reductions

This is the core server-side combat simulation for all headed Fight Caves attacks.

### 4.11 Prayer behavior

Prayer system owner:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/skill/prayer/Prayer.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/skill/prayer/Prayer.kt)

Key behavior:

- effective level bonuses feed into combat formulas
- protection prayers can zero or reduce damage
- deflect/protection logic is checked by attack type
- active prayers are stored in transmitted player vars

The demo-specific normal-prayer enforcement is done in two layers:

1. episode initializer forces the prayer book and clears curses
2. `PrayerList.normalisePrayerBook()` keeps the UI and state consistent when the prayer tab/orb opens

That two-layer approach is what fixed the earlier "curses instead of prayers" headed issue.

### 4.12 Eating and drinking

Consumable owner:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/skill/constitution/Eating.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/skill/constitution/Eating.kt)

The eat/drink path:

1. validates the item as consumable
2. checks the relevant clock:
   - `food_delay`
   - `drink_delay`
   - `combo_delay`
3. removes or replaces the item in inventory
4. plays `eat_drink` animation and sound
5. applies the item-specific restore effect
6. restores Constitution by the item’s heal range

This is why sharks and prayer potions behave like real consumables rather than custom scripted shortcuts.

### 4.13 Equipment, stats, and combat impact

Equipment influence owner:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/entity/player/equip/Equipment.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/entity/player/equip/Equipment.kt)

Combat style and weapon behavior owners:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/skill/melee/CombatStyles.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/skill/melee/CombatStyles.kt)
- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/skill/melee/weapon/Weapon.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/skill/melee/weapon/Weapon.kt)
- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/skill/ranged/Ammo.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/skill/ranged/Ammo.kt)

These systems determine:

- attack type and style
- attack speed
- attack range
- strength bonus
- ranged ammo requirements
- bolt effects
- shield reductions
- void bonuses
- defensive bonuses

For the demo loadout, the main relevant impacts are:

- rune crossbow drives ranged attack profile
- adamant bolts provide the projectile/ammo identity
- d'hide gear contributes ranged/defensive bonuses
- fixed Defence/Ranged/Prayer levels shape accuracy, survivability, and prayer usability

## 5. Fight Caves NPC Mechanics

Core data files:

- NPC definitions:
  - [`/home/jordan/code/runescape-rl/rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.npcs.toml`](/home/jordan/code/runescape-rl/rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.npcs.toml)
- combat definitions:
  - [`/home/jordan/code/runescape-rl/rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.combat.toml`](/home/jordan/code/runescape-rl/rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave.combat.toml)
- waves/rotations:
  - [`/home/jordan/code/runescape-rl/rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave_waves.toml`](/home/jordan/code/runescape-rl/rsps/data/minigame/tzhaar_fight_cave/tzhaar_fight_cave_waves.toml)

Script overrides:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzTokJad.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzTokJad.kt)
- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/JadTelegraph.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/JadTelegraph.kt)
- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzHaarHealers.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzHaarHealers.kt)

### 5.1 Tz-Kih

- id `2734`
- HP `100`
- aggressive hunt mode
- melee attack speed `4`
- max melee hit `40`
- impact drains `1` Prayer

### 5.2 Tz-Kek

- id `2736`
- HP `200`
- melee max hit `70`
- on death, splits into two `tz_kek_spawn`

Split handling is scripted in:

- `npcDeath("tz_kek,tz_kek_spawn_point") { spawn("tz_kek_spawn", ...) }`

### 5.3 Tz-Kek spawn

- id `2738`
- HP `100`
- melee max hit `40`

### 5.4 Tok-Xil

- id `2739`
- HP `400`
- melee max hit `130`
- ranged max hit `140`
- ranged projectile:
  - `tok_xil_shoot`

### 5.5 Yt-MejKot

- id `2741`
- HP `800`
- melee max hit `250`
- special `heal` attack if nearby monsters are weakened

Heal behavior is scripted in:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzHaarHealers.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzHaarHealers.kt)

### 5.6 Ket-Zek

- id `2743`
- HP `1600`
- melee max hit `540`
- magic max hit `490`
- magic uses:
  - attack anim/gfx
  - projectile travel gfx
  - impact gfx

### 5.7 TzTok-Jad

- id `2745`
- HP `2500`
- attack speed `8`
- melee max hit `970`
- range and magic handled partly by generic combat data and partly by explicit script overrides

Data file defines Jad range/magic windups and impact visuals, but the actual hit resolution for range/magic is overridden in:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzTokJad.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzTokJad.kt)

Jad telegraph state is tracked in:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/JadTelegraph.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/JadTelegraph.kt)

It records:

- telegraph state
- committed attack style
- attack sequence
- hit resolve tick
- sampled protection prayer at check time
- resolved damage

This is one of the clearest places where the runtime already contains a useful high-level combat intent model that can be mirrored in a C port.

### 5.8 Yt-HurKot

- id `2746`
- HP `600`
- melee max hit `140`
- healer minion for Jad

At Jad half HP, healer behavior is triggered by:

- `npcLevelChanged(Skill.Constitution, "tztok_jad") { ... }`

and healer ticking is controlled by:

- `npcTimerStart("yt_hur_kot_heal") { 4 }`
- `npcTimerTick("yt_hur_kot_heal") { ... }`

If within 5 tiles of Jad, healers:

- animate
- play gfx
- restore Jad HP
- emit area sound

## 6. Observability And Live Evidence

Current session log:

- [`/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/artifacts/session_logs/fcdemo01-2026-03-30T17-41-31.478.jsonl`](/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/artifacts/session_logs/fcdemo01-2026-03-30T17-41-31.478.jsonl)

This shows:

1. session started
2. entry requested on player spawn
3. starter-state manifest written
4. episode reset
5. follow-up reset
6. wave 1 NPC present after follow-up

The follow-up event confirms the reset did not just teleport the player into an empty instance. It booted a real wave:

- spawned NPC count `1`
- NPC id `tz_kih`

Current client stderr:

- [`/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/client_logs/client-20260330T174101-stderr.log`](/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/client_logs/client-20260330T174101-stderr.log)

It contains a non-fatal `ImageFormatException` from the Windows Java client, followed by a render-path stack trace. The client still reached ready state and remained live, so this is currently a render/startup caveat rather than a headed boot blocker.

## 7. Optional Backend-Control Layer

Human headed play does not require backend control, but the headed path also supports action injection for replay and checkpoint inference.

Files:

- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveDemoBackendControl.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveDemoBackendControl.kt)
- [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveBackendActionAdapter.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveBackendActionAdapter.kt)

This layer:

- polls a backend-control inbox under:
  - [`/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/backend_control`](/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/backend_control)
- maps shared action ids to real headed runtime actions:
  - wait
  - walk_to_tile
  - attack_visible_npc
  - toggle_protection_prayer
  - eat_shark
  - drink_prayer_potion
  - toggle_run

This is important for a port because it cleanly separates:

- raw frontend rendering/input
- gameplay simulation
- machine-control boundary

## 8. What Must Be Reimplemented For A C/raylib Port

If the goal is "build the same playable Fight Caves experience in C/raylib", the current Java system is doing four distinct jobs.

### 8.1 You must reimplement the frontend host and renderer

The current client provides:

- window creation
- loading-screen UX
- terrain/map loading
- model rendering
- animation playback
- projectiles and graphics
- hitsplats and health bars
- overhead icons and head icons
- minimap and gameframe
- prayer/combat/inventory tabs
- client-side entity interpolation and scene drawing

### 8.2 You must reimplement the protocol or replace it with a new local contract

Today the frontend is driven by:

- login protocol
- JS5/file-server
- interface/container/var packets
- entity update packets
- graphics/projectile packets
- region/dynamic-region packets

If the port remains networked, those surfaces need equivalents.

If the port becomes single-process/local, then the same information still has to flow across an internal API boundary.

### 8.3 You must reimplement server-authoritative gameplay

The backend today owns:

- starter-state reset
- dynamic instance creation
- wave progression
- movement/pathing
- collision
- combat accuracy/damage
- prayer protection
- inventory/equipment changes
- consumable effects
- NPC AI/attack selection
- Jad telegraph and delayed resolution
- healer logic
- death/leave/reset/recycle flow

These are not cosmetic. They are the actual Fight Caves game.

### 8.4 You must preserve the split between generic systems and Fight Caves-specific scripts

The current codebase is not one monolith. It is layered:

- generic runtime systems:
  - login
  - cache
  - world
  - movement
  - combat
  - interfaces
  - variables
  - inventory/equipment
- Fight Caves-specific content:
  - wave tables
  - spawn rules
  - Jad overrides
  - healer logic
  - demo reset/recycle/gating

That separation is worth preserving in a port. Otherwise the port will turn into a hard-to-maintain special-case program instead of a clean Fight Caves runtime.

## 9. Recommended Reading Order For A Porting Agent

Read these first, in order:

1. [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/FightCavesDemoMain.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/FightCavesDemoMain.kt)
2. [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/GameProfiles.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/GameProfiles.kt)
3. [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/Main.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/Main.kt)
4. [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/GameTick.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/GameTick.kt)
5. [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt)
6. [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveEpisodeInitializer.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveEpisodeInitializer.kt)
7. [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzTokJad.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzTokJad.kt)
8. [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/JadTelegraph.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/JadTelegraph.kt)
9. [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzHaarHealers.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzHaarHealers.kt)
10. [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/entity/npc/combat/Attack.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/entity/npc/combat/Attack.kt)
11. [`/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/entity/combat/hit/Damage.kt`](/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/entity/combat/hit/Damage.kt)
12. [`/home/jordan/code/runescape-rl/rsps/scripts/run_fight_caves_demo_client.sh`](/home/jordan/code/runescape-rl/rsps/scripts/run_fight_caves_demo_client.sh)
13. [`/home/jordan/code/runescape-rl/rsps/scripts/launch_fight_caves_demo_client.ps1`](/home/jordan/code/runescape-rl/rsps/scripts/launch_fight_caves_demo_client.ps1)
14. [`/home/jordan/code/runescape-rl/rsps/void-client/client/src/Loader.java`](/home/jordan/code/runescape-rl/rsps/void-client/client/src/Loader.java)
15. [`/home/jordan/code/runescape-rl/rsps/void-client/client/src/FightCavesDemoAutoLogin.java`](/home/jordan/code/runescape-rl/rsps/void-client/client/src/FightCavesDemoAutoLogin.java)
16. [`/home/jordan/code/runescape-rl/rsps/void-client/client/src/client.java`](/home/jordan/code/runescape-rl/rsps/void-client/client/src/client.java)
17. [`/home/jordan/code/runescape-rl/rsps/void-client/client/src/Player.java`](/home/jordan/code/runescape-rl/rsps/void-client/client/src/Player.java)
18. [`/home/jordan/code/runescape-rl/rsps/void-client/client/src/Class101.java`](/home/jordan/code/runescape-rl/rsps/void-client/client/src/Class101.java)
19. [`/home/jordan/code/runescape-rl/rsps/void-client/client/src/Class286_Sub5.java`](/home/jordan/code/runescape-rl/rsps/void-client/client/src/Class286_Sub5.java)

## 10. Bottom Line

The current playable Fight Caves headed demo is real because it reuses the original RSPS split:

- the server owns world state, combat, instances, waves, and gameplay truth
- the stock-derived client owns terrain, models, assets, UI, icons, rendering, and input
- the bridge between them is still the normal JS5 + login + entity/interface/container/variable protocol

The demo-specific work did not fake the cave. It did three focused things:

1. booted the server into a Fight Caves-only profile
2. forced a deterministic fresh starter-state inside the Fight Caves runtime
3. added a Windows convenience launcher so the stock client comes up quickly and lands directly in the cave

That is why the result is a true playable Fight Caves experience instead of a UI mock or simplified training visualizer.
