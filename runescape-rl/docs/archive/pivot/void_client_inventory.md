# void-client Inventory

## Scope

This inventory covers `/home/jordan/code/RSPS/void-client` as cloned for the RSPS-backed headed demo audit.

Repo state inspected:

- remote: `https://github.com/GregHib/void-client`
- branch: `main`
- commit: `228561a143dbf58717805dc65e03b832ca420b95`

## Top-level map

| Path | Type | Purpose |
|---|---|---|
| `README.md` | File | Minimal repo description: "Deobfuscated 634 2010-12-14 client". |
| `libs/clientlibs.jar` | File | Supporting library jar used by the client sources. |
| `client/resources/icon-*.png` | Files | Window icons used by the desktop loader. |
| `client/src/*.java` | 756 Java files | Full deobfuscated 634 desktop client source tree. |

Local repo notes:

- no Gradle wrapper
- no Maven build
- no Ant build
- no local run script
- no checked-in packaged `client.jar`

Practical implication: the repo is source + support jar, not a self-contained modern build project.

## Entrypoints and boot path

### Primary entrypoint

`/home/jordan/code/RSPS/void-client/client/src/Loader.java`

Key findings:

- Desktop Swing/AWT launcher.
- CLI args:
  - `-ip` / `--address`
  - `-p` / `--port`
  - `-d` / `--debug`
  - `-t` / `--trace`
- Defaults:
  - `address = "127.0.0.1"`
  - `port = 43594`
  - `skipLobby = true`
- Creates a `JFrame`, injects applet parameters, then starts the client applet.
- Provides both login-server and file-server RSA moduli.

### Secondary entrypoint

`/home/jordan/code/RSPS/void-client/client/src/client.java`

Key findings:

- Also contains `main(...)`, but it expects full applet-style parameters and is less convenient than `Loader`.
- `init()` reads applet parameters such as `worldid`, `lobbyid`, `lobbyaddress`, `demoaddress`, `modewhere`, `modewhat`, `lang`, `worldflags`, and membership flags.

### Login/direct-boot support

Relevant files:

- `/home/jordan/code/RSPS/void-client/client/src/Class151.java`
- `/home/jordan/code/RSPS/void-client/client/src/Class14_Sub4.java`
- `/home/jordan/code/RSPS/void-client/client/src/Class82.java`

Key findings:

- `Loader.skipLobby = true` is wired into the login state flow.
- A `directlogin` developer console command exists in `Class82.java`.
- This makes direct game login feasible without using the lobby path.

### Boot sequence summary

1. `Loader.main(...)` parses CLI flags and creates the frame.
2. `Loader.setParms()` injects the expected applet parameters.
3. `client.init()` consumes world/lobby/demo/game flags and client config.
4. `Class297` prepares OS/cache/filesystem services and socket creation.
5. `client.method101(...)` performs the JS5/file-server handshake.
6. `Class164.method1278(...)` runs staged cache/interface/resource loading.
7. Once in-game, `Class348_Sub24.method2991(...)` becomes the main network/UI update pump.

## Connection, login, and protocol assumptions

### Connection ownership

| Concern | Primary file(s) | Notes |
|---|---|---|
| Socket creation | `/home/jordan/code/RSPS/void-client/client/src/Class297.java` | Opens sockets to the configured host/port and prints `Connect:` when `Loader.debug` is enabled. |
| World/lobby address setup | `/home/jordan/code/RSPS/void-client/client/src/Loader.java`, `/home/jordan/code/RSPS/void-client/client/src/client.java` | World and lobby addresses/ids are parameter-driven. |
| Login RSA | `/home/jordan/code/RSPS/void-client/client/src/Loader.java`, `/home/jordan/code/RSPS/void-client/client/src/Class348_Sub31.java` | Client expects RSA-backed login handshake. |
| File-server RSA | `/home/jordan/code/RSPS/void-client/client/src/Loader.java`, packet classes referencing file-server RSA | Confirms JS5/file-server coupling, not just game-socket coupling. |

### What the server must provide

The client is not a thin renderer. It expects a normal RS2-era server protocol surface:

- login handshake
- JS5/file-server response path
- map/region and dynamic-region packets
- interface open/close/update packets
- container/inventory updates
- varp/varbit/config updates
- player and NPC update packets
- graphics and projectile packets
- chat/status packets, even if some are mostly inert in a trimmed demo

Evidence:

- `Class348_Sub24.method2991(...)` applies ongoing interface and state updates.
- `Class348_Sub41.method3157(...)` handles region shifts and re-bases players/NPCs/projectiles.
- `Player.java` decodes appearance/equipment/name/head-icon and model state from packets.

## Rendering and scene ownership

| Surface | Primary file(s) | Notes |
|---|---|---|
| World/scene and camera runtime | Distributed across the deobfuscated client, including scene and entity classes | This is a full game client, not a replay shell. |
| Player rendering/appearance decode | `/home/jordan/code/RSPS/void-client/client/src/Player.java` | Decodes appearance, equipment, names, icons, animation state, and movement paths. |
| NPC rendering/update | `/home/jordan/code/RSPS/void-client/client/src/Npc.java` and related update classes | NPC rendering and movement are client-owned and packet-driven. |
| Region shift / map rebase | `/home/jordan/code/RSPS/void-client/client/src/Class348_Sub41.java` | Re-bases all entities and scene state when map region changes. |
| Projectiles / area graphics / hitsplats | Packet and renderer classes referenced from `Class348_Sub24.java` and related classes | Confirms the real client visual path is already present. |

Audit conclusion:

- Near-in-game scene presentation is already here.
- Recreating inventory/prayer/combat visuals outside this client would be unnecessary duplicate work.

## UI and widget ownership

The client owns the actual widget rendering and mutation. The server drives it by packets, vars, and interface ids.

Primary evidence:

- `/home/jordan/code/RSPS/void-client/client/src/Class348_Sub24.java`
- `/home/jordan/code/RSPS/void-client/client/src/Class46.java`

Observed widget/state responsibilities in `Class348_Sub24.method2991(...)`:

- interface text updates
- color changes
- model swaps/animations
- component visibility
- orientation/position-like mutations
- varp/string-driven widget state

Practical implication:

- inventory tab, prayer tab, combat interface, equipment panel, minimap, and standard gameframe widgets should be reused from stock client
- the RSPS server must continue sending the normal interface/container/varp packet stream for those widgets to behave correctly

## Asset and cache loading

### Cache path

Relevant files:

- `/home/jordan/code/RSPS/void-client/client/src/Class297.java`
- `/home/jordan/code/RSPS/void-client/client/src/Class201.java`
- `/home/jordan/code/RSPS/void-client/client/src/Class164.java`

Key findings:

- The client opens:
  - `random.dat`
  - `main_file_cache.dat2`
  - `main_file_cache.idx255`
  - `main_file_cache.idx0...`
- Cache directories are searched under locations such as:
  - `c:/rscache/`
  - `/rscache/`
  - `c:/windows/`
  - `c:/`
  - user home
  - `/tmp/`
  - `.jagex_cache_*`
  - `.file_store_*`

### Loaded archives/resources

Evidence from `Class164.java` and related loading text/constants:

- `loginscreen`
- `lobbyscreen`
- `huffman`
- interfaces
- interface scripts
- world map
- sprites/models/definitions via the usual cache archive loaders

Practical implication:

- first pass should reuse the full asset/cache path
- a Fight Caves-only client trim should not start by trying to carve out custom widget/model/sprite loading

## Build and run status

What is directly confirmed locally:

- the repo contains source and `libs/clientlibs.jar`
- `Loader.java` is the obvious run entrypoint
- there is no local build wrapper

What is not yet confirmed:

- exact compile invocation for this checkout
- whether the checked-in source tree still compiles cleanly without additional setup

Because this was an audit-only task, the client was not modified and no implementation changes were made.

## Trim feasibility

### Stock client with minimal/no modification

**Likely viable for first pass.**

Why:

- defaults already target localhost-style RSPS bring-up
- skip-lobby support already exists
- the client already owns the right UI/asset/rendering path
- most of the desired "trim" can happen server-side by forcing a Fight Caves-only bootstrap after login

What this first pass would still include:

- normal login screen or direct-login console flow
- full standard gameframe, even if many tabs are inert
- full cache asset path

### Lightly modified client

**Likely useful, but not required for the first pass.**

Best reasons to modify later:

- auto-fill or auto-submit login
- hide unused tabs/panels for a cleaner Fight Caves-only presentation
- preconfigure address/port/world id more explicitly

### Substantial custom client trim

**Not recommended as the first milestone.**

Why not:

- the client and server are tightly coupled through cache, protocol, region loading, interfaces, and vars
- the stock client already solves the "looks/feels like real RSPS/OSRS" problem
- a deep custom trim would create a second frontend system before the RSPS-backed path is proven

## Audit conclusion

The `void-client` repo is sufficient to provide the near-in-game headed presentation required for the new demo path, but only when paired with a real RSPS-compatible server runtime. The right first-pass strategy is to keep the client mostly stock and concentrate trimming work inside the RSPS server/bootstrap path.
