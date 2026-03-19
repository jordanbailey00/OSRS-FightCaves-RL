# Pivot Closure Report

## Status

The pivot is complete.

Completed boundary:

- Phase 0 through Phase 8 are complete
- PR 0.1 through PR 8.2 are complete

This report is the concise end-state summary for operators and future reviewers.

## Final architecture

The finished workspace uses a three-path role split:

- V1 oracle/reference/debug path:
  - current simulator-backed headed/headless stack
  - kept for parity, replay/reference, and mechanics debugging
- RSPS-backed headed demo/replay path:
  - `RSPS` runtime plus `void-client`
  - trusted headed demo path and current default demo/replay backend
- V2 fast headless training path:
  - `v2_fast`
  - current default RL training backend

Source-of-truth split:

- `fight-caves-RL` owns oracle/headless semantics and mechanics-parity reference behavior
- `RSPS` owns headed mechanics/runtime behavior for the trusted headed demo path
- `void-client` owns headed UI/render/assets/gameframe
- `RL` owns PufferLib training, evaluation, backend selection, replay tooling, and analytics

## Final defaults

- training default:
  - `v2_fast`
  - entrypoint: `/home/jordan/code/RL/scripts/train.py`
  - default config: `/home/jordan/code/RL/configs/train/smoke_fast_v2.yaml`
- headed demo/replay default:
  - `rsps_headed`
  - selector: `/home/jordan/code/RL/scripts/run_demo_backend.py`

## Preserved fallback paths

The default switch did not remove older paths.

Preserved explicit fallbacks:

- frozen lite-demo headed fallback/reference:
  - selector: `python scripts/run_demo_backend.py --backend fight_caves_demo_lite --mode launch_reference`
- V1 oracle/reference/debug replay:
  - selector: `python scripts/run_demo_backend.py --backend oracle_v1 --mode replay`
  - underlying entrypoint: `/home/jordan/code/RL/scripts/replay_eval.py`
- V1 config-based training and benchmark fallbacks:
  - `/home/jordan/code/RL/configs/train/smoke_ppo_v0.yaml`
  - `/home/jordan/code/RL/configs/train/train_baseline_v0.yaml`
  - `/home/jordan/code/RL/configs/benchmark/vecenv_256env_v0.yaml`
  - `/home/jordan/code/RL/configs/benchmark/train_1024env_v0.yaml`

Those preserved V1 config fallbacks pin `env_backend = v1_bridge` explicitly.

## Why the default switch was earned

The default headed switch happened only after the RSPS-backed path proved:

- headed trust gate closure in Phase 7
- backend-control smoke in Phase 8.1
- replay-first validation in Phase 8.1
- live checkpoint inference in Phase 8.1

Key evidence:

- headed backend smoke:
  - `/home/jordan/code/RSPS/docs/fight_caves_demo_backend_control_validation.md`
- headed replay validation:
  - `/home/jordan/code/RSPS/docs/fight_caves_demo_headed_replay_validation.md`
- headed live checkpoint validation:
  - `/home/jordan/code/RSPS/docs/fight_caves_demo_live_checkpoint_validation.md`
- default/fallback selection reference:
  - `/home/jordan/code/RL/docs/default_backend_selection.md`

## Non-blocking caveats

These do not block pivot completion, but future operators should know them:

- current machine-specific headed client note:
  - the working headed client path is the WSL IP path
  - `127.0.0.1:43594` does not work for the stock/convenience client path on this machine
- the convenience headed client path is zero-touch and trusted, but still not universally “only a few seconds total” on cold bootstrap
- the full RL acceptance gate currently still encounters unrelated pre-existing unit failures in the `PrototypeProductionTrainer` test surface; that did not block the pivot default-switch criteria
- broad multi-wave live headed stress beyond the bounded Phase 8.1 evidence was not claimed as part of pivot completion

## Operator quickstart

Default RL training:

```bash
source /home/jordan/code/.workspace-env.sh
cd /home/jordan/code/RL
uv run python scripts/train.py --output /tmp/fc_train.json
```

Default headed replay:

```bash
source /home/jordan/code/.workspace-env.sh
cd /home/jordan/code/RL
uv run python scripts/run_demo_backend.py
```

Default headed live checkpoint demo:

```bash
source /home/jordan/code/.workspace-env.sh
cd /home/jordan/code/RL
uv run python scripts/run_demo_backend.py --mode live_inference --checkpoint /path/to/checkpoint.pt
```

Intentional preserved fallbacks:

```bash
source /home/jordan/code/.workspace-env.sh
cd /home/jordan/code/RL
uv run python scripts/run_demo_backend.py --backend oracle_v1 --mode replay -- --checkpoint /path/to/checkpoint.pt --output /tmp/fc_eval.json
uv run python scripts/run_demo_backend.py --backend fight_caves_demo_lite --mode launch_reference
uv run python scripts/train.py --config configs/train/smoke_ppo_v0.yaml --output /tmp/fc_train_v1.json
```

Key runbooks and validation notes:

- `/home/jordan/code/RSPS/docs/fight_caves_demo_stock_client_runbook.md`
- `/home/jordan/code/RL/docs/default_backend_selection.md`
- `/home/jordan/code/RSPS/docs/fight_caves_demo_backend_control_validation.md`
- `/home/jordan/code/RSPS/docs/fight_caves_demo_headed_replay_validation.md`
- `/home/jordan/code/RSPS/docs/fight_caves_demo_live_checkpoint_validation.md`
