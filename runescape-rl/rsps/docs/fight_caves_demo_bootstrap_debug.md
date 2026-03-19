# Fight Caves Demo Bootstrap Debug

This note records the bounded PR 7B.2 investigation into headed bootstrap/login friction on the RSPS-backed Fight Caves demo path.

## Current convenience-path decision

- The RSPS/client protocol still requires a real login handshake.
- The visible setup/login screens are not strictly required on the Fight Caves demo convenience path.
- The local convenience jar now uses direct auto-login without manual input, while showing a dedicated loading window with a moving bar during bootstrap and keeping the real client hidden until in-game.

This keeps mechanics and starter-state ownership on the RSPS side while removing the user-facing setup/login friction from repeated demo bring-up.

## What is strictly required vs convenience baggage

Strictly required:

- client bootstrap
- cache availability
- JS5/update handshake
- real login handshake against the RSPS server
- world/region initialization

Not strictly required on the local convenience path:

- showing the `Auto setup` screen
- manual username/password entry
- requiring any manual interaction at the setup/login stages

## Current default convenience path

From WSL:

```bash
cd /home/jordan/code/RSPS
./scripts/run_fight_caves_demo_client.sh
```

Current behavior:

- resolves the working WSL IP automatically
- syncs the RSPS cache into the Windows client cache
- seeds Windows preferences to skip `Auto setup`
- uses the convenience client jar by default
- uses the stable disposable demo account `fcdemo01` / `pass123` if no credentials are provided
- keeps the real client hidden while a dedicated loading window stays visible during bootstrap
- auto-enters the Fight Caves demo path after bootstrap without manual input

Use `--show-bootstrap` only if you explicitly want to expose the real client window during bootstrap for debugging.

## Measured timing breakdown

Bounded measurement from the current zero-touch convenience run:

- launcher command: `./scripts/run_fight_caves_demo_client.sh`
- client stdout log: `/home/jordan/code/RSPS/data/fight_caves_demo/client_logs/client-20260312T183005-stdout.log`
- session log: `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/session_logs/fcdemo01-2026-03-12T18-30-36.010.jsonl`

Observed timings:

- cache sync: `67ms`
- client bootstrap to login-ready (`client_state=3 login_stage=0`): `12695ms`
- login submit to post-login ready state (`client_state=11 login_stage=0`): `16269ms`
- total hidden bootstrap to reveal: `28964ms`

## Diagnosis

The remaining startup cost is no longer dominated by setup friction or manual login.

What is already minimized:

- no manual `Auto setup`
- no manual credential entry
- visible moving loading bar during client bootstrap
- warm cache sync is negligible on this machine

What still dominates:

- legacy client bootstrap before login-ready
- post-submit world/client initialization before the first ready-to-reveal state

So the residual bottleneck is now inside legacy client startup and world init, not launcher-side convenience plumbing.

## Machine-specific runtime note

- WSL IP `172.25.183.199:43594` works on this machine
- `127.0.0.1:43594` does not work for the stock-client path on this machine
