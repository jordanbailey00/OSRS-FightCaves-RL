# Fight Caves Headed Env Boot/Build Write-Up

This document explains exactly how the current headed Fight Caves demo is built, launched, and run in the normalized workspace.

It is intentionally explicit so another coding agent can trace the path without guessing.

Current source revisions at the time this report was written:

- `rsps` repo HEAD: `65f04890d368d2108cf1a3c16364369d3943e2ca`
- `rsps/void-client` repo HEAD: `228561a143dbf58717805dc65e03b832ca420b95`

## 1. Canonical path map

Older workspace names that may still appear in historical notes:

- old RSPS root: `/home/jordan/code/RSPS`
- old RL root: `/home/jordan/code/RL`
- old headless env root: `/home/jordan/code/fight-caves-RL`
- old lite demo root: `/home/jordan/code/fight-caves-demo-lite`

Current canonical roots:

- active headed server/runtime owner: `/home/jordan/code/runescape-rl/rsps`
- active headed client source: `/home/jordan/code/runescape-rl/rsps/void-client`
- headless sim owner: `/home/jordan/code/runescape-rl/training-env/sim`
- Python RL/control owner: `/home/jordan/code/runescape-rl/training-env/rl`
- frozen fallback/reference demo module: `/home/jordan/code/runescape-rl/demo-env`

Important ownership note:

- the real headed Fight Caves path is owned by `rsps/`
- `demo-env/` is not the active headed runtime path

## 2. Quick commands

### 2.1 Start the Fight Caves demo server in WSL

```bash
source /home/jordan/code/.workspace-env.sh
cd /home/jordan/code/runescape-rl/rsps
./gradlew --no-daemon :game:runFightCavesDemo
```

### 2.2 Launch the headed client from WSL into native Windows Java

```bash
cd /home/jordan/code/runescape-rl/rsps
./scripts/run_fight_caves_demo_client.sh
```

### 2.3 Launch with explicit credentials

```bash
cd /home/jordan/code/runescape-rl/rsps
./scripts/run_fight_caves_demo_client.sh --username jordantest01 --password pass123
```

### 2.4 Show the real client window during bootstrap instead of the dedicated loading window

```bash
cd /home/jordan/code/runescape-rl/rsps
./scripts/run_fight_caves_demo_client.sh --show-bootstrap
```

### 2.5 Print the resolved launch command without starting the client

```bash
cd /home/jordan/code/runescape-rl/rsps
./scripts/run_fight_caves_demo_client.sh --dry-run
```

### 2.6 Close the headed client from WSL

```bash
cd /home/jordan/code/runescape-rl/rsps
./scripts/close_fight_caves_demo_client.sh
```

## 3. Preconditions and required tools

### 3.1 Workspace env bootstrap

WSL entrypoint:

- `/home/jordan/code/.workspace-env.sh`

That file immediately delegates to:

- `/home/jordan/code/runescape-rl/scripts/workspace-env.sh`

What it sets:

- `WORKSPACE_ROOT=/home/jordan/code/runescape-rl`
- `FIGHT_CAVES_RUNTIME_ROOT=/home/jordan/code/runescape-rl-runtime` unless overridden
- `JAVA_HOME=/home/jordan/code/runescape-rl-runtime/tool-state/workspace-tools/jdk-21`
- `GRADLE_USER_HOME=/home/jordan/code/runescape-rl-runtime/cache/gradle`
- `PIP_CACHE_DIR=/home/jordan/code/runescape-rl-runtime/cache/pip`
- `UV_CACHE_DIR=/home/jordan/code/runescape-rl-runtime/cache/uv`
- `UV_PYTHON_INSTALL_DIR=/home/jordan/code/runescape-rl-runtime/cache/python`
- `XDG_CACHE_HOME=/home/jordan/code/runescape-rl-runtime/cache/general`
- `XDG_CONFIG_HOME=/home/jordan/code/runescape-rl-runtime/tool-state/agent/config`
- `TMPDIR=/home/jordan/code/runescape-rl-runtime/tmp/run`

This means:

- the RSPS server is built/launched with the workspace JDK 21 in WSL
- Gradle caches and temp state are externalized into the runtime root

### 3.2 RSPS cache prerequisite

The RSPS server expects:

- `/home/jordan/code/runescape-rl/rsps/data/cache`

In this workspace that path is a symlink to the headless sim cache:

- `/home/jordan/code/runescape-rl/training-env/sim/data/cache`

Current preflight note:

- `/home/jordan/code/runescape-rl/rsps/docs/fight_caves_demo_preflight.md`

Minimum sanity check:

```bash
test -f /home/jordan/code/runescape-rl/rsps/data/cache/main_file_cache.dat2 && echo OK
```

### 3.3 Windows-side tools required

The headed client launch path uses Windows tools from WSL via `powershell.exe`.

WSL launcher scripts:

- `/home/jordan/code/runescape-rl/rsps/scripts/run_fight_caves_demo_client.sh`
- `/home/jordan/code/runescape-rl/rsps/scripts/build_fight_caves_demo_client.sh`
- `/home/jordan/code/runescape-rl/rsps/scripts/close_fight_caves_demo_client.sh`

Windows PowerShell scripts they delegate to:

- `/home/jordan/code/runescape-rl/rsps/scripts/launch_fight_caves_demo_client.ps1`
- `/home/jordan/code/runescape-rl/rsps/scripts/build_fight_caves_demo_client.ps1`
- `/home/jordan/code/runescape-rl/rsps/scripts/close_fight_caves_demo_client.ps1`

Required Windows toolchain split:

- Windows JDK 8 `java.exe` or `javaw.exe` to run the client
- Windows JDK 8 `javac.exe` and `jar.exe` to build the convenience jar

Why JDK 8 matters:

- the checked-out `void-client` source uses legacy APIs that do not compile cleanly under the workspace JDK 21 path
- the convenience client build is therefore intentionally done on native Windows JDK 8

## 4. End-to-end runtime/control flow

## 4.1 Human headed bring-up flow

1. Source the workspace env in WSL:
   - `/home/jordan/code/.workspace-env.sh`
2. Run the RSPS demo Gradle task:
   - `./gradlew --no-daemon :game:runFightCavesDemo`
3. The RSPS server starts the Fight Caves demo profile and opens port `43594`.
4. From WSL, run:
   - `./scripts/run_fight_caves_demo_client.sh`
5. If the convenience jar is missing, the launcher builds it first from the checked-out `void-client` source.
6. The launcher resolves the current WSL IP unless `--address` was supplied.
7. The launcher syncs RSPS cache files into the Windows Jagex cache location.
8. The launcher seeds Windows Jagex preference files.
9. The launcher starts the client jar with `--address`, `--port`, and usually `--username` / `--password`.
10. The client starts hidden by default when autologin is enabled and shows the dedicated bootstrap loading window instead.
11. The client auto-submits credentials and waits until the in-game state is reached.
12. The real client frame is revealed only after the client has advanced to an in-game-ready state.
13. On server-side login/spawn, the Fight Caves demo profile forces a fresh canonical episode reset and immediately starts the wave inside a fresh instance.

## 4.2 Optional backend-controlled headed path after the client is already up

This is not required for a human headed bring-up, but it is the layer used later for replay/live checkpoint control.

Relevant files:

- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveDemoBackendControl.kt`
- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveBackendActionAdapter.kt`
- `/home/jordan/code/runescape-rl/training-env/rl/scripts/run_demo_backend.py`
- `/home/jordan/code/runescape-rl/training-env/rl/scripts/run_headed_checkpoint_inference.py`

That layer writes request files to:

- `/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/backend_control/inbox`

and reads results from:

- `/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/backend_control/results`

## 5. Exact server build and boot path

### 5.1 Gradle wrapper and task

WSL command:

```bash
cd /home/jordan/code/runescape-rl/rsps
./gradlew --no-daemon :game:runFightCavesDemo
```

Key files involved:

- Gradle wrapper: `/home/jordan/code/runescape-rl/rsps/gradlew`
- task definition: `/home/jordan/code/runescape-rl/rsps/game/build.gradle.kts`

The task is registered in:

- `/home/jordan/code/runescape-rl/rsps/game/build.gradle.kts`

Behavior of that task:

- task name: `runFightCavesDemo`
- type: `JavaExec`
- main class: `FightCavesDemoMain`
- working directory: the RSPS root project directory

### 5.2 Main class chain

The demo task starts:

- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/FightCavesDemoMain.kt`

That file calls:

- `Main.start(GameProfiles.fightCavesDemo)`

The main server bootstrap lives at:

- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/Main.kt`

The profile definitions live at:

- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/GameProfiles.kt`

### 5.3 Game profile selection

`GameProfiles.fightCavesDemo` defines:

- profile id: `fight-caves-demo`
- property files:
  - `game.properties`
  - `fight-caves-demo.properties`
- after-preload hook:
  - `FightCavesDemoBootstrap::install`

Profile override file:

- `/home/jordan/code/runescape-rl/rsps/game/src/main/resources/fight-caves-demo.properties`

Important demo overrides there:

- `server.name=Void Fight Caves Demo`
- `development.accountCreation=true`
- `world.start.creation=false`
- `storage.autoSave.minutes=0`
- `storage.players.path=./data/fight_caves_demo/saves/`
- `storage.players.logs=./data/fight_caves_demo/logs/`
- `storage.players.errors=./data/fight_caves_demo/errors/`
- `fightCave.demo.artifacts.path=./data/fight_caves_demo/artifacts/`
- `bots.count=0`
- `fightCave.demo.enabled=true`
- `fightCave.demo.skipBotTick=true`
- `fightCave.demo.ammo=1000`
- `fightCave.demo.prayerPotions=8`
- `fightCave.demo.sharks=20`
- `fightCave.demo.backendControl.enabled=true`
- `fightCave.demo.backendControl.root=./data/fight_caves_demo/backend_control/`

### 5.4 What `Main.start(...)` actually does

Code:

- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/Main.kt`

The effective server bootstrap sequence is:

1. `settings(profile)` loads the property files for the selected profile.
2. `Cache.load(settings)` loads the RSPS cache from `rsps/data/cache`.
3. `GameServer.load(cache, settings)` builds the file/game server.
4. `server.start(Settings["network.port"].toInt())` starts the network listener.
5. `configFiles.update()` updates content/config files.
6. `preload(cache, configFiles)` loads definitions/content and starts Koin modules.
7. `profile.afterPreload()` installs the Fight Caves demo bootstrap.
8. `LoginServer.load(...)` creates the login server.
9. `getTickStages()` builds the game loop stage list.
10. `World.start(configFiles)` starts the world.
11. `GameLoop(stages).start(scope)` starts ticking.

### 5.5 Koin module and tick-stage hooks relevant to the demo

Koin module file:

- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/GameModules.kt`

Relevant bindings:

- `FightCaveBackendActionAdapter`
- `FightCaveDemoBackendControl`

Tick stage list:

- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/GameTick.kt`

Relevant demo behavior there:

- `fightCaveDemoBackendControl` is inserted as a normal tick stage
- bot ticking is skipped when `fightCave.demo.skipBotTick=true`
- logs are periodically flushed through `SaveLogs`

## 6. Exact Fight Caves direct-entry seam

### 6.1 Demo login marker hook

Code:

- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/GameProfiles.kt`

`FightCavesDemoBootstrap.install()` wraps the account manager load callback and marks the player with:

- `fight_cave_demo_profile = true`
- `fight_cave_demo_entry_pending = true`

It also starts demo observability with:

- `FightCaveDemoObservability.beginSession(player)`

### 6.2 Fight Caves content owner

Primary content script:

- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt`

Companion Fight Caves files that still matter to behavior:

- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCaveWaves.kt`
- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzTokJad.kt`
- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/JadTelegraph.kt`
- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzHaarHealers.kt`

### 6.3 The actual direct-to-cave methods

The active server-side reset code lives at:

- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveEpisodeInitializer.kt`

The main reset entry from the Fight Caves script is:

- `TzhaarFightCave.resetDemoEpisode(player, cause)`

That method:

1. Builds a `FightCaveEpisodeConfig`
2. Calls `episodeInitializer.reset(player, config)`
3. Records starter-state artifacts/logs through `FightCaveDemoObservability.recordReset(...)`

### 6.4 What the episode initializer actually enforces

`FightCaveEpisodeInitializer.reset(...)` does all of the following server-side:

1. sets the deterministic/random seed for the episode
2. clears transient combat/timer/queue/watch/anim/gfx state
3. clears prior Fight Caves state
4. clears prior instance state and destroys NPCs in the previous instance
5. forces the normal prayer book:
   - `PrayerConfigs.PRAYERS = PrayerConfigs.BOOK_PRAYERS`
6. clears active prayers, curses, quick-prayer state, and drain counters
7. resets skills/resources to the canonical starter contract
8. blocks XP gain
9. restores run energy to max and enables running
10. enables auto-retaliate
11. overwrites equipment with the canonical ranged loadout
12. overwrites inventory with sharks and prayer potions
13. creates a fresh small instance of the Fight Caves region
14. teleports the player into the cave centre inside that instance
15. calls `fightCave.startWave(player, config.startWave, start = false)`
16. schedules a short follow-up aggression pass so spawned NPCs immediately attack the player

### 6.5 When the reset is triggered

Primary hooks in `TzhaarFightCave.kt`:

- `playerSpawn { ... resetDemoEpisode(this, cause = "player_spawn") }`
- `objectOperate("Enter", "cave_entrance_fight_cave") { ... resetDemoEpisode(this, cause = "object_enter") }`
- exit handling:
  - `scheduleDemoEpisodeReset(this, cause = "exit_object")`
- world-leak handling:
  - `scheduleDemoEpisodeReset(this, cause = "area_exit")`
- death handling:
  - `scheduleDemoDeathReset(this)`
  - which waits for death queue completion and then calls `resetDemoEpisode(this, cause = "player_death")`
- completion/recycle handling:
  - `Player.leave(...)` calls `scheduleDemoEpisodeReset(this, cause = "cave_complete")` in the demo profile

This is why the demo loops back into a fresh Fight Caves episode instead of leaving the player in the broader world.

## 7. Exact client build path

### 7.1 WSL build entrypoint

Script:

- `/home/jordan/code/runescape-rl/rsps/scripts/build_fight_caves_demo_client.sh`

Default output:

- `/home/jordan/code/runescape-rl/.artifacts/void-client/void-client-fight-caves-demo.jar`

That shell script only delegates into PowerShell and passes explicit source/resource/lib/output paths.

### 7.2 Windows PowerShell build script

Script:

- `/home/jordan/code/runescape-rl/rsps/scripts/build_fight_caves_demo_client.ps1`

Inputs:

- source dir:
  - `/home/jordan/code/runescape-rl/rsps/void-client/client/src`
- resource dir:
  - `/home/jordan/code/runescape-rl/rsps/void-client/client/resources`
- dependency jar:
  - `/home/jordan/code/runescape-rl/rsps/void-client/libs/clientlibs.jar`
- output jar:
  - `/home/jordan/code/runescape-rl/.artifacts/void-client/void-client-fight-caves-demo.jar`

What that script actually does:

1. resolves `javac.exe` from a Windows JDK 8 installation
2. resolves `jar.exe` from a Windows JDK 8 installation
3. creates a temp build root in:
   - `%TEMP%\\fight-caves-demo-client-build`
4. writes a `sources.txt` listing all `*.java` files under `client/src`
5. writes a manifest file with:
   - `Main-Class: Loader`
6. compiles all Java sources with classpath:
   - `clientlibs.jar`
   - `client/resources`
7. copies resource files into the classes directory
8. extracts `clientlibs.jar` into the classes directory
9. repacks everything into the output jar

### 7.3 Current client artifact state

Current local convenience jar path:

- `/home/jordan/code/runescape-rl/.artifacts/void-client/void-client-fight-caves-demo.jar`

The WSL launcher also knows about a stock fallback jar path:

- `/home/jordan/code/runescape-rl/.artifacts/void-client/void-client-1.0.1.jar`

But the active workspace currently has only:

- `void-client-fight-caves-demo.jar`

The stock fallback path is a launcher option, not the primary local artifact currently present.

## 8. Exact client launch path

### 8.1 WSL launch script

Script:

- `/home/jordan/code/runescape-rl/rsps/scripts/run_fight_caves_demo_client.sh`

Important behavior in that script:

1. resolves the RSPS root
2. chooses the default jar:
   - `/home/jordan/code/runescape-rl/.artifacts/void-client/void-client-fight-caves-demo.jar`
3. resolves the WSL IP using:
   - `hostname -I | awk '{print $1}'`
4. auto-builds the convenience jar if it is missing
5. defaults the demo account to:
   - username: `fcdemo01`
   - password: `pass123`
6. writes client stdout/stderr logs to:
   - `/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/client_logs`
7. calls the PowerShell launcher with explicit arguments

### 8.2 Windows PowerShell launcher

Script:

- `/home/jordan/code/runescape-rl/rsps/scripts/launch_fight_caves_demo_client.ps1`

That script does all of the following:

1. resolves a Windows Java runtime executable
2. optionally syncs RSPS cache files from:
   - `/home/jordan/code/runescape-rl/rsps/data/cache`
   into:
   - `C:\\.jagex_cache_32\\runescape`
3. seeds:
   - `%USERPROFILE%\\jagex_runescape_preferences.dat`
   - `%USERPROFILE%\\jagex_runescape_preferences2.dat`
4. launches:
   - `java -jar <jar> --address <ip> --port 43594`
5. when using the convenience jar plus credentials, also passes:
   - `--username <name>`
   - `--password <value>`
   - optionally `--show-during-bootstrap`
6. redirects stdout/stderr to the log paths provided by the WSL shell script
7. emits a JSON summary of the launch details to stdout

### 8.3 Client-side direct-login/bootstrap code

Main launcher class:

- `/home/jordan/code/runescape-rl/rsps/void-client/client/src/Loader.java`

Key behavior there:

- defaults:
  - `address = "127.0.0.1"`
  - `port = 43594`
  - `skipLobby = true`
- parses:
  - `--address`
  - `--port`
  - `--username`
  - `--password`
  - `--show-during-bootstrap`
  - `--hide-until-ingame`
- when autologin creds are present and no explicit visibility override is given:
  - `hideUntilInGame = true`
- configures:
  - `FightCavesDemoAutoLogin.configure(...)`
- creates the real Swing frame and optionally shows the dedicated bootstrap window instead of the real client frame

Client autologin helper:

- `/home/jordan/code/runescape-rl/rsps/void-client/client/src/FightCavesDemoAutoLogin.java`

What it does:

1. starts a background thread after client init
2. watches legacy client state fields:
   - `Class240.anInt4674`
   - `Class225.anInt2955`
   - `Class367_Sub2.anInt7297`
3. updates the dedicated bootstrap window with those states
4. waits until the login UI is ready
5. submits credentials through:
   - `Class253.method1922(password, 0, username, true)`
6. waits until client state indicates in-game readiness
7. reveals the real frame only after that point

Dedicated bootstrap/loading window:

- `/home/jordan/code/runescape-rl/rsps/void-client/client/src/FightCavesDemoBootstrapWindow.java`

What it does:

- opens a Swing frame titled `Client`
- draws a black loading screen with the native-style red bar
- animates progress locally while also reacting to real client/login state
- shows messages such as:
  - `Checking for updates`
  - `Preparing login`
  - `Logging in`
  - `Loading Fight Caves`

## 9. Runtime outputs and artifact locations

### 9.1 Server-side demo profile data

Under:

- `/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo`

Important subpaths:

- saves:
  - `/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/saves`
- logs:
  - `/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/logs`
- errors:
  - `/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/errors`
- starter-state/session artifacts:
  - `/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/artifacts`
- backend control inbox/results:
  - `/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/backend_control`
- client stdout/stderr logs:
  - `/home/jordan/code/runescape-rl/rsps/data/fight_caves_demo/client_logs`

### 9.2 Client build artifact

Under:

- `/home/jordan/code/runescape-rl/.artifacts/void-client`

Primary file:

- `/home/jordan/code/runescape-rl/.artifacts/void-client/void-client-fight-caves-demo.jar`

### 9.3 Windows client runtime state

Outside the repo on Windows:

- cache mirror:
  - `C:\\.jagex_cache_32\\runescape`
- preference files:
  - `%USERPROFILE%\\jagex_runescape_preferences.dat`
  - `%USERPROFILE%\\jagex_runescape_preferences2.dat`

## 10. Exact files another coding agent should inspect first

### 10.1 Server boot and profile

- `/home/jordan/code/runescape-rl/rsps/game/build.gradle.kts`
- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/FightCavesDemoMain.kt`
- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/Main.kt`
- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/GameProfiles.kt`
- `/home/jordan/code/runescape-rl/rsps/game/src/main/resources/fight-caves-demo.properties`

### 10.2 Fight Caves demo routing and reset

- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/TzhaarFightCave.kt`
- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveEpisodeInitializer.kt`
- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveDemoObservability.kt`
- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/content/area/karamja/tzhaar_city/FightCaveDemoBackendControl.kt`
- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/GameModules.kt`
- `/home/jordan/code/runescape-rl/rsps/game/src/main/kotlin/GameTick.kt`

### 10.3 Client build and launch

- `/home/jordan/code/runescape-rl/rsps/scripts/build_fight_caves_demo_client.sh`
- `/home/jordan/code/runescape-rl/rsps/scripts/build_fight_caves_demo_client.ps1`
- `/home/jordan/code/runescape-rl/rsps/scripts/run_fight_caves_demo_client.sh`
- `/home/jordan/code/runescape-rl/rsps/scripts/launch_fight_caves_demo_client.ps1`
- `/home/jordan/code/runescape-rl/rsps/scripts/close_fight_caves_demo_client.sh`
- `/home/jordan/code/runescape-rl/rsps/scripts/close_fight_caves_demo_client.ps1`

### 10.4 Client source files that were changed for the convenience path

- `/home/jordan/code/runescape-rl/rsps/void-client/client/src/Loader.java`
- `/home/jordan/code/runescape-rl/rsps/void-client/client/src/FightCavesDemoAutoLogin.java`
- `/home/jordan/code/runescape-rl/rsps/void-client/client/src/FightCavesDemoBootstrapWindow.java`

### 10.5 Optional RL -> headed control

- `/home/jordan/code/runescape-rl/training-env/rl/scripts/run_demo_backend.py`
- `/home/jordan/code/runescape-rl/training-env/rl/scripts/run_headed_checkpoint_inference.py`

## 11. Machine-specific notes that matter

- On this machine, the headed client path has historically needed the WSL IP rather than `127.0.0.1`.
- The convenience launcher automatically resolves the WSL IP unless overridden.
- The headed path depends on the RSPS cache symlink being correct:
  - `/home/jordan/code/runescape-rl/rsps/data/cache -> /home/jordan/code/runescape-rl/training-env/sim/data/cache`
- The primary headed client artifact is the convenience jar, not the frozen lite demo module.
- `demo-env/` is preserved as fallback/reference only and does not own the current real headed Fight Caves boot path.

## 12. Minimal reproducible operator sequence

If another coding agent wants the shortest reproducible headed bring-up path, use this sequence:

```bash
source /home/jordan/code/.workspace-env.sh
test -f /home/jordan/code/runescape-rl/rsps/data/cache/main_file_cache.dat2
cd /home/jordan/code/runescape-rl/rsps
./gradlew --no-daemon :game:runFightCavesDemo
```

In a second WSL terminal:

```bash
cd /home/jordan/code/runescape-rl/rsps
./scripts/run_fight_caves_demo_client.sh
```

That is the current real headed Fight Caves demo path.
