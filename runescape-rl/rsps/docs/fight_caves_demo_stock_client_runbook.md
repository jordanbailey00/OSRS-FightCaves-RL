# Fight Caves Demo Stock Client Runbook

This runbook is the first-pass bring-up path for PR 7A.5.

Use it to launch:

- the RSPS Fight Caves demo profile in WSL/Linux
- the headed `void-client` path on native Windows

The original 7A.5 milestone used the stock release jar unchanged.

After 7B.1, repeated local bring-up now defaults to a lightly modified convenience jar built from the checked-out `void-client` source tree. That convenience jar exists only to remove repetitive local setup friction while still showing a dedicated demo loading window that matches the native loading screen style:

- no manual `Auto setup`
- no manual username/password entry
- no manual setup/login actions on the default convenience path
- visible loading window and moving loading bar during bootstrap without exposing the login screen
- same client protocol/render/gameframe ownership as the stock client

Use the stock release jar only when you explicitly want the fallback path.

## 1. Preconditions

1. The RSPS cache must exist at `/home/jordan/code/RSPS/data/cache/`.

   See [fight_caves_demo_preflight.md](./fight_caves_demo_preflight.md).

2. The RSPS server must be run from WSL/Linux with the workspace JDK loaded:

```bash
source /home/jordan/code/.workspace-env.sh
```

3. The headed client must run on native Windows with a local JDK/JRE available.

## 2. Preferred client artifact

For repeated local Fight Caves demo bring-up, the preferred artifact is:

- `/home/jordan/code/.artifacts/void-client/void-client-fight-caves-demo.jar`

This jar is built locally from the checked-out `void-client` source plus `libs/clientlibs.jar` by:

```bash
cd /home/jordan/code/RSPS
./scripts/build_fight_caves_demo_client.sh
```

Why this exists:

- the stock jar on this machine could spend minutes on the first-run update/setup flow
- username/password autofill against the stock jar still depended on the client reaching the login screen first
- the convenience jar accepts `--username` and `--password` directly and works with launcher-side cache/prefs seeding to remove manual setup/login actions

The stock fallback remains available from the [void-client releases page](https://github.com/GregHib/void-client/releases) or from:

- `/home/jordan/code/.artifacts/void-client/void-client-1.0.1.jar`

Use the checked-out `/home/jordan/code/RSPS/void-client/` repo as:

- source/reference for ownership and later client work
- the source for the local convenience build
- a fallback IntelliJ artifact-build path if needed

Do not treat the checked-out source tree as a signal that broader client feature work has started. The local jar is still just a launch convenience layer.

Current workspace note:

- a direct `javac` build of `/home/jordan/code/RSPS/void-client/client/src/` under the workspace JDK 21 fails on legacy/internal API references
- examples observed during verification include `sun.net.www.protocol.http.AuthenticationInfo`, `Component.getPeer()`, and `JSObject.getWindow(...)`

That is why the local convenience build uses Windows JDK 8 rather than the workspace JDK 21 toolchain.

## 3. Start the RSPS demo profile

From WSL/Linux:

```bash
source /home/jordan/code/.workspace-env.sh
cd /home/jordan/code/RSPS
./gradlew --no-daemon :game:runFightCavesDemo
```

Expected startup signals:

- profile `fight-caves-demo`
- listening on port `43594`
- login server online
- game world online

The stock client default matches this port.

## 4. Launch the stock client on Windows

Preferred first pass:

```bat
java -jar client.jar --address 127.0.0.1 --port 43594
```

If localhost forwarding from Windows to WSL does not work on the local machine, use the current WSL IP instead:

```bash
hostname -I
```

Then launch:

```bat
java -jar client.jar --address <wsl-ip> --port 43594
```

The checked-out stock client boot path already defaults to:

- `Loader.main(...)`
- `skipLobby = true`
- `127.0.0.1:43594`

So no first-pass client code change should be required.

## 4A. 7B.1 convenience launcher

After PR 7A.7, the workspace includes a repo-owned convenience launcher for repeated headed demo bring-up.

From WSL/Linux:

```bash
cd /home/jordan/code/RSPS
./scripts/run_fight_caves_demo_client.sh
```

What it does:

- auto-builds `/home/jordan/code/.artifacts/void-client/void-client-fight-caves-demo.jar` if it is missing
- resolves the current WSL IP automatically
- syncs the RSPS cache into the Windows client cache at `C:\.jagex_cache_32\runescape`
- seeds the Windows `jagex_runescape_preferences*.dat` files to avoid the blocking `Auto setup` step
- launches the Windows client against the correct address/port for this machine
- writes client stdout/stderr logs to `RSPS/data/fight_caves_demo/client_logs/`

Optional login autofill:

```bash
cd /home/jordan/code/RSPS
./scripts/run_fight_caves_demo_client.sh --username jordantest01 --password pass123
```

Notes:

- when no credentials are provided, the default convenience path uses the stable disposable demo account `fcdemo01` / `pass123`
- when using the default convenience jar, username/password are passed directly into the client auto-login helper
- by default the convenience path keeps the real client hidden until in-game and shows a dedicated loading window with a moving bar during bootstrap
- use `--show-bootstrap` only if you explicitly want to expose the real client window during bootstrap for debugging
- `--stock-jar` falls back to the unmodified release jar and retains the older Windows `SendKeys` autofill path
- the launcher does not change mechanics or server-side ownership
- warm reruns now avoid most cache copies and no longer sit on multi-minute update waits on this machine, but the current measured zero-touch bring-up is still about 29 seconds on this machine rather than only a few seconds total

To close the client from WSL:

```bash
cd /home/jordan/code/RSPS
./scripts/close_fight_caves_demo_client.sh
```

## 5. Login expectations

Login with any username and password.

The Fight Caves demo profile currently keeps:

- local account creation enabled
- generic character creation disabled
- demo-profile entry markers enabled on login

Expected first-pass result after login:

- the player is routed into the canonical Fight Caves demo episode
- broader world traversal is not part of the loop
- inventory, prayers, combat tab, scene rendering, and gameframe come from stock `void-client`

## 6. Fallback if a local client jar must be built

If a prebuilt `client.jar` is not available locally, use the IntelliJ artifact-build path documented in:

- [../wiki/Client-Building.md](../wiki/Client-Building.md)

That fallback is still a stock-client path.

It is not a justification to modify client code in PR 7A.5.

## 7. Known limits of this path

The headed trust gate is already closed.

This launcher path is still limited to:

- launch convenience
- cache/prefs seeding
- zero-touch login
- machine-local runtime ergonomics

It is not yet a guarantee of "only a few seconds total" from process start to in-game on every cold boot.

For the current measured breakdown, see:

- [fight_caves_demo_bootstrap_debug.md](./fight_caves_demo_bootstrap_debug.md)

## 8. Current validation note

The first native-Windows validation result for this runbook is recorded in:

- [fight_caves_demo_stock_client_validation.md](./fight_caves_demo_stock_client_validation.md)
- [fight_caves_demo_observability.md](./fight_caves_demo_observability.md)

Current machine-specific result:

- `127.0.0.1` failed
- WSL IP fallback is the working path on this machine
- the default convenience launcher now reaches in-game without manual `Auto setup` or manual credential entry
- warm reruns skip most cache copies
- current measured bring-up is materially faster than the earlier multi-minute path, but still not yet only a few seconds total on this machine
- session-continuity follow-up is tracked in [fight_caves_demo_session_continuity_validation.md](./fight_caves_demo_session_continuity_validation.md)
- Phase 8 backend-control smoke usage is documented in [fight_caves_demo_backend_control_smoke.md](./fight_caves_demo_backend_control_smoke.md)
- the first live backend-control validation result is recorded in [fight_caves_demo_backend_control_validation.md](./fight_caves_demo_backend_control_validation.md)
- the first live replay-first validation result is recorded in [fight_caves_demo_headed_replay_validation.md](./fight_caves_demo_headed_replay_validation.md)
- the first live checkpoint-inference validation result is recorded in [fight_caves_demo_live_checkpoint_validation.md](./fight_caves_demo_live_checkpoint_validation.md)
