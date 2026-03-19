# Fight Caves Demo Login Differential Debug

This note records the bounded login-completion differential-debugging pass for PR 7A.5 and the server-side fix pass that followed it.

It answers two questions:

- was the stock-client `Your account has been disabled` result demo-specific?
- what exact server-side defect caused it?

## Runtime used

- stock client artifact:
  - `/home/jordan/code/.artifacts/void-client/void-client-1.0.1.jar`
- Windows Java:
  - `C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot\bin\java.exe`
- working network path:
  - `172.25.183.199:43594`

Compared server paths:

- full RSPS:
  - `/home/jordan/code/RSPS`
  - `./gradlew --no-daemon :game:run`
- Fight Caves demo profile:
  - `/home/jordan/code/RSPS`
  - `./gradlew --no-daemon :game:runFightCavesDemo`

## Initial baseline result

Full normal RSPS login did **not** succeed before the fix.

The disabled-account result was therefore broader than the Fight Caves demo profile.

## Shared pre-fix failure trace

Both full RSPS and the Fight Caves demo profile produced the same server-side sequence:

1. `login_validate ... response=SUCCESS(2)`
2. `login_load ... created_new=true`
3. `Player <username> loaded and queued for login.`
4. `login_connect_setup`
5. `login_connect_queue_wait`
6. no `login_connect_queue_ready`
7. no `login_connect_client_login_sent`
8. no `login_connect_spawn_complete`
9. later `login_validate ... response=ACCOUNT_DISABLED(4)`

Disk-state checks after the failed runs showed:

- no new save file under `/home/jordan/code/RSPS/data/saves/`
- no new save file under `/home/jordan/code/RSPS/data/fight_caves_demo/saves/`

## Exact root cause

The login failure came from two server-side defects interacting:

1. newly created first-login accounts were not persisted before the client could retry validation
2. an unrelated game-loop stage exception could stop the queued-login transition from ever reaching `ConnectionQueue.run()`

The concrete failure shape was:

- `PlayerAccountLoader.load(...)` created a new account in memory
- `AccountManager.setup(...)` added that new account to `AccountDefinitions`
- `PasswordManager.validate(...)` could then see:
  - `exists(username) == false`
  - `password(username) != null`
- that combination maps to `ACCOUNT_DISABLED(4)`
- meanwhile, the live `JadTelegraph` exception from generic NPC combat could kill the game loop before the waiting login reached `login_connect_queue_ready`

That is why the client first appeared to be queued for login, then later received a false disabled-account response instead of entering the game.

## Server-side fix applied

The narrowed fix pass changed only the shared RSPS server-side login/runtime path:

1. `PlayerAccountLoader.load(...)` now persists newly created accounts immediately, before waiting on the login queue
2. `GameLoop` now isolates per-stage exceptions so an unrelated stage failure does not stop later stages or future queue processing

Relevant code:

- `/home/jordan/code/RSPS/engine/src/main/kotlin/world/gregs/voidps/engine/client/PlayerAccountLoader.kt`
- `/home/jordan/code/RSPS/engine/src/main/kotlin/world/gregs/voidps/engine/GameLoop.kt`

Supporting tests:

- `/home/jordan/code/RSPS/engine/src/test/kotlin/world/gregs/voidps/engine/client/PlayerAccountLoaderTest.kt`
- `/home/jordan/code/RSPS/engine/src/test/kotlin/world/gregs/voidps/engine/GameLoopStageExceptionTest.kt`

## Post-fix differential result

### Full RSPS baseline

Post-fix, the full normal RSPS path succeeded for first-login account `base7501`:

- `login_validate ... response=SUCCESS(2)`
- `login_persist_new_account`
- `login_connect_queue_ready`
- `login_connect_client_login_sent`
- `login_connect_spawn_complete`

The stock client entered logged-in flow and reached the character-selection/game-entry interface.

Artifacts:

- `/home/jordan/code/.artifacts/void-client/headed-validation/postfix/full-rsps-server-postfix.log`
- `/home/jordan/code/.artifacts/void-client/headed-validation/postfix/client-full-postfix-04-after-enter.png`

### Fight Caves demo profile

Post-fix, the Fight Caves demo profile also succeeded for first-login account `demo7502`:

- `login_validate ... response=SUCCESS(2)`
- `login_persist_new_account`
- `login_connect_queue_ready`
- `login_connect_client_login_sent`
- `login_connect_spawn_complete username=demo7502 tile=Tile(6509, 125, 0)`

The stock client entered the game and landed directly in the Fight Caves runtime.

Artifacts:

- `/home/jordan/code/.artifacts/void-client/headed-validation/postfix/rsps-demo-server-postfix.log`
- `/home/jordan/code/.artifacts/void-client/headed-validation/postfix/client-demo-postfix-03-after-login.png`

## First exact divergence point

Before the fix:

- there was no demo-specific login divergence
- both paths diverged from the intended success path immediately after `login_connect_queue_wait`

After the fix:

- that shared divergence is gone
- both paths now reach the queued-login completion and client login-send path

## Smallest fix that worked

The smallest effective fix was the server-side combination of:

- immediate persistence for newly created accounts
- stage-level exception isolation in the game loop

No client modification was required.

## Scope note

This note covers the resolved disabled-account/login-completion defect only. Later Fight Caves mechanics issues were handled separately in subsequent Phase 7 work.
