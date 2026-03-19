# Fight Caves Demo Stock Client Validation

This note records the native-Windows stock-client validation work for PR 7A.5, including the initial failure and the post-fix rerun that resolved login completion.

## Runtime used

- RSPS server:
  - WSL/Linux
  - `/home/jordan/code/RSPS`
- Stock client artifact:
  - `/home/jordan/code/.artifacts/void-client/void-client-1.0.1.jar`
- Windows Java used for the client:
  - `C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot\bin\java.exe`

## Connection-path result on this machine

### `127.0.0.1`

Result:

- failed

Evidence:

- native Windows `Test-NetConnection 127.0.0.1 -Port 43594` failed
- the stock client stalled on the update screen and emitted `error_game_js5connect`

Relevant artifacts:

- `/home/jordan/code/.artifacts/void-client/headed-validation/client-127-before-login.png`
- `/home/jordan/code/.artifacts/void-client/headed-validation/client-127-stdout.log`

### WSL IP fallback

WSL IP used:

- `172.25.183.199`

Result:

- worked for the stock headed path

Evidence:

- native Windows `Test-NetConnection 172.25.183.199 -Port 43594` succeeded
- the client established a live socket to the RSPS server
- the client advanced through:
  - update sync
  - first-run auto-setup prompt
  - existing-account login screen
  - real in-game entry after the server-side login fix pass

## Initial result before the server-side fix

Observed result:

- both full RSPS and the Fight Caves demo profile reached login submit
- both then failed with:
  - `Your account has been disabled. Check your message centre for details.`

Relevant artifacts:

- `/home/jordan/code/.artifacts/void-client/headed-validation/client-wslip-login-result-10s.png`
- `/home/jordan/code/.artifacts/void-client/headed-validation/client-full-login-result-20s.png`
- `/home/jordan/code/RSPS/docs/fight_caves_demo_login_differential_debug.md`

That initial result is now superseded by the post-fix rerun below.

## Post-fix rerun result

The narrowed server-side fix pass changed the outcome on both the full normal RSPS profile and the Fight Caves demo profile.

### Full normal RSPS profile

Server path:

- `./gradlew --no-daemon :game:run`

Submitted credentials:

- username: `base7501`
- password: `pass123`

Observed result:

- first-login account creation succeeded
- the newly created account persisted immediately to `/home/jordan/code/RSPS/data/saves/base7501.toml`
- the login advanced through:
  - `login_connect_queue_ready`
  - `login_connect_client_login_sent`
  - `login_connect_spawn_complete`
- the stock client entered logged-in flow and reached the real character-selection/game-entry interface instead of the disabled-account dialog

Relevant artifacts:

- `/home/jordan/code/.artifacts/void-client/headed-validation/postfix/full-rsps-server-postfix.log`
- `/home/jordan/code/.artifacts/void-client/headed-validation/postfix/client-full-postfix-02-filled.png`
- `/home/jordan/code/.artifacts/void-client/headed-validation/postfix/client-full-postfix-04-after-enter.png`

### Fight Caves demo profile

Server path:

- `./gradlew --no-daemon :game:runFightCavesDemo`

Submitted credentials:

- username: `demo7502`
- password: `pass123`

Observed result:

- first-login account creation succeeded
- the newly created account persisted immediately to `/home/jordan/code/RSPS/data/fight_caves_demo/saves/demo7502.toml`
- the login advanced through:
  - `login_connect_queue_ready`
  - `login_connect_client_login_sent`
  - `login_connect_spawn_complete`
- the stock client entered the game and landed directly in the Fight Caves runtime at `Tile(6509, 125, 0)`

Relevant artifacts:

- `/home/jordan/code/.artifacts/void-client/headed-validation/postfix/rsps-demo-server-postfix.log`
- `/home/jordan/code/.artifacts/void-client/headed-validation/postfix/client-demo-postfix-02-filled.png`
- `/home/jordan/code/.artifacts/void-client/headed-validation/postfix/client-demo-postfix-03-after-login.png`

## Headed UI and gameframe result

Confirmed working on the stock client after the fix:

- real in-game world scene rendering in the Fight Caves
- minimap and chat/gameframe rendering
- combat tab interaction
- prayer tab interaction
- spellbook tab interaction

Relevant artifacts:

- `/home/jordan/code/.artifacts/void-client/headed-validation/postfix/client-demo-postfix-03-after-login.png`
- `/home/jordan/code/.artifacts/void-client/headed-validation/postfix/client-demo-postfix-04-inventory-tab.png`
- `/home/jordan/code/.artifacts/void-client/headed-validation/postfix/client-demo-postfix-05-combat-tab.png`
- `/home/jordan/code/.artifacts/void-client/headed-validation/postfix/client-demo-postfix-07-inventory-maybe.png`

Note:

- the tab captures prove the stock gameframe is live and interactive
- this is sufficient to close the stock-client bring-up and in-game-entry blocker for PR 7A.5
- richer headed playability validation remains a later manual trust-gate concern, not a remaining 7A.5 login blocker

## Current PR 7A.5 disposition

PR 7A.5 is ready to close.

Reason:

- the stock client can log into the full normal RSPS profile
- the stock client can log into the Fight Caves demo profile
- the false `ACCOUNT_DISABLED(4)` first-login path is resolved
- the headed client reaches real in-game entry
- the demo profile lands directly in the intended Fight Caves flow with the stock gameframe

## Scope note

This note is the stock-client bring-up and in-game-entry record for PR 7A.5. Later Fight Caves mechanics cleanup and headed trust validation belong to subsequent Phase 7 work.
