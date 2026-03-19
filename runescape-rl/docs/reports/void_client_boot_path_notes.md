# void-client Boot Path Notes

## Observed boot path

1. `Loader.main(...)`
   - parses `--address`, `--port`, `--debug`, `--trace`
   - defaults to `127.0.0.1:43594`
   - sets `skipLobby = true`
2. `Loader.setParms()`
   - injects applet parameters such as `worldid`, `lobbyid`, `lobbyaddress`, `game`, `lang`, `worldflags`
3. `Loader.startClient()`
   - creates `client`
   - calls `init()` and `start()`
4. `client.init()`
   - reads the applet parameters into client globals
5. `Class297`
   - sets up OS/cache/filesystem services
   - opens cache files and later sockets
6. `client.method101(...)`
   - performs JS5/file-server handshake
7. `Class164.method1278(...)`
   - loads archives/resources/interfaces/loginscreen/lobbyscreen/huffman/world map
8. `Class348_Sub24.method2991(...)`
   - main in-game network + UI update pump

## Direct-login note

The client already has two useful conveniences:

- `Loader.skipLobby = true`
- `directlogin` console command in `Class82.java`

This is why stock-client reuse is plausible for the first milestone even before a custom loader tweak.

## First-pass implication

For the first milestone, do not treat the client boot path as the main trim target. The easier path is:

- keep client boot mostly stock
- keep login protocol intact
- use RSPS demo mode to place the player directly into the canonical Fight Caves episode after login
