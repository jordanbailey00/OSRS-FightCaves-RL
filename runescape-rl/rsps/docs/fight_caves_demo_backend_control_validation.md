# Fight Caves Demo Backend-Control Validation

This note records the first live PR 8.1 backend-control smoke against the trusted RSPS-backed headed Fight Caves demo.

## Validation run

- Date: 2026-03-12
- Account: `fcdemo01`
- Runtime topology:
  - RSPS demo server in WSL/Linux
  - convenience headed client on Windows
  - working machine-specific path: `172.25.183.199:43594`
  - `127.0.0.1:43594` still does not work for this stock/convenience client path on this machine

## Primary artifacts

- Smoke summary:
  - `/home/jordan/code/RL/artifacts/headed_backend_smoke/fcdemo01-20260312T195724064404.json`
- Session log with backend action events:
  - `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/session_logs/fcdemo01-2026-03-12T19-57-06.416.jsonl`
- Starter-state manifest for the same session:
  - `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/starter_state_manifests/fcdemo01-2026-03-12T19-57-06.416_reset_001.json`
- Per-request headed result artifacts:
  - `/home/jordan/code/RSPS/data/fight_caves_demo/backend_control/results/fcdemo01-walk_to_tile-20260312T195724565801.json`
  - `/home/jordan/code/RSPS/data/fight_caves_demo/backend_control/results/fcdemo01-toggle_protection_prayer-20260312T195725817726.json`
  - `/home/jordan/code/RSPS/data/fight_caves_demo/backend_control/results/fcdemo01-attack_visible_npc-20260312T195727069606.json`
  - `/home/jordan/code/RSPS/data/fight_caves_demo/backend_control/results/fcdemo01-eat_shark-20260312T195728071361.json`
  - `/home/jordan/code/RSPS/data/fight_caves_demo/backend_control/results/fcdemo01-drink_prayer_potion-20260312T195729323399.json`

## Result summary

The backend-control smoke passed for the first headed slice of PR 8.1.

Confirmed:

- backend-issued `move` applied
- backend-issued prayer toggle applied
- backend-issued `attack_visible_npc` applied
- backend-issued `eat_shark` applied
- backend-issued `drink_prayer_potion` applied
- visible-target ordering was explicit in both the per-request result artifacts and the session log
- processed ticks advanced one request at a time:
  - `100, 101, 102, 103, 104, 105, 106, 107, 108, 109`
- the smoke artifact recorded forward-only cadence validation with `tick_deltas=[1,1,1,1,1,1,1,1,1]`

Not yet covered by this validation note:

- replay of recorded/shared traces
- live checkpoint inference

Those later slices were completed in PR 8.1 and are recorded in:

- [fight_caves_demo_headed_replay_validation.md](./fight_caves_demo_headed_replay_validation.md)
- [fight_caves_demo_live_checkpoint_validation.md](./fight_caves_demo_live_checkpoint_validation.md)
