# Fight Caves Demo Backend-Control Smoke

This note covers the first Phase 8 backend-control smoke path for the trusted RSPS-backed headed demo.

It exists to prove that the headed Fight Caves session can be driven from backend-issued actions without UI clicking before any replay or live checkpoint inference work begins.

## Scope

The smoke path uses:

- RSPS demo-only backend action inbox/results under `/home/jordan/code/RSPS/data/fight_caves_demo/backend_control/`
- the shared `headless_action_v1` action schema from the RL repo
- the live headed Fight Caves session that was already validated in Phase 7

The smoke step is intentionally narrower than replay or live inference. It proves:

- backend-issued `move`
- backend-issued `attack`
- backend-issued `eat shark`
- backend-issued `drink prayer potion`
- backend-issued prayer toggle
- stable headed visible-target ordering evidence
- headed action timing evidence via per-request processed ticks and result artifacts

## Preconditions

1. Start the RSPS demo server in WSL/Linux:

```bash
source /home/jordan/code/.workspace-env.sh
cd /home/jordan/code/RSPS
./gradlew --no-daemon :game:runFightCavesDemo
```

2. Start the convenience headed client and wait until the player is already in Fight Caves:

```bash
cd /home/jordan/code/RSPS
./scripts/run_fight_caves_demo_client.sh
```

Current machine-specific note:

- the working headed path is the WSL IP path
- `127.0.0.1` does not work for the stock/convenience client path on this machine

3. Once the headed client is in-game, run the backend smoke from the RL repo:

```bash
cd /home/jordan/code/RL
python3 scripts/run_headed_backend_smoke.py --account fcdemo01
```

## Artifacts

Runtime transport artifacts:

- requests: `/home/jordan/code/RSPS/data/fight_caves_demo/backend_control/inbox/`
- results: `/home/jordan/code/RSPS/data/fight_caves_demo/backend_control/results/`

Smoke summary artifact:

- `/home/jordan/code/RL/artifacts/headed_backend_smoke/<account>-<timestamp>.json`

Session-log evidence:

- `/home/jordan/code/RSPS/data/fight_caves_demo/artifacts/session_logs/<session>.jsonl`
- look for `backend_action_processed`

Latest live validation note:

- [fight_caves_demo_backend_control_validation.md](./fight_caves_demo_backend_control_validation.md)

## Review points

When reviewing the smoke artifact, confirm:

- all five required backend-driven actions report `action_applied=true`
- the attack request uses a visible target index from the headed visible-target list
- processed ticks move forward one request at a time
- rejected or deferred behavior, if any, is captured explicitly in the result payload

This smoke path was the first gate inside PR 8.1.

Replay of recorded traces and live checkpoint inference were completed later in the same PR. See:

- [fight_caves_demo_headed_replay_validation.md](./fight_caves_demo_headed_replay_validation.md)
- [fight_caves_demo_live_checkpoint_validation.md](./fight_caves_demo_live_checkpoint_validation.md)
